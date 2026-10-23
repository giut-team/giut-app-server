import contextlib
import configparser
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
from urllib import error

spec = importlib.util.spec_from_file_location("resource_monitor", Path(__file__).with_name("resource_monitor.py"))
m = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = m
spec.loader.exec_module(m)

# Synthetic, invalid-as-a-real-secret test fixture; no request ever leaves the process.
WEBHOOK = "https://discord.com/api/webhooks/123/fake-test-token"


class MonitorTest(unittest.TestCase):
    def setUp(self):
        self.config = m.Config()
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.state_path = self.root / "state.json"
        self.state = None
        self.now = 1000
        self.total = 10000
        self.idle = 1000
        self.oom = 12
        self.boot = "test-boot"

    def sample(self, cpu=20, memory=50, seconds=60):
        self.now += seconds
        self.total += 1000
        self.idle += int(1000 * (100 - cpu) / 100)
        value = m.Snapshot(self.now, self.boot, self.total, self.idle, memory, self.oom)
        self.state = m.advance(self.state, value, self.config)
        return value

    def events(self):
        return m.pending_events(self.state, self.now, self.config)

    def establish_alarm(self):
        self.sample()
        for _ in range(3):
            self.sample(cpu=90, memory=95)
        return self.events()

    def proc_fixture(self, available=200, oom=14):
        proc = self.root / "proc"
        (proc / "sys/kernel/random").mkdir(parents=True, exist_ok=True)
        (proc / "stat").write_text("cpu 100 20 30 800 50 10 15 5 9999 9999\ncpu0 1 1 1 1 1 1 1 1\n")
        (proc / "meminfo").write_text("MemTotal: 1000 kB\nMemFree: 1 kB\nMemAvailable: "
                                     + str(available) + " kB\nCached: 200 kB\n")
        (proc / "vmstat").write_text("pgfault 42\noom_kill " + str(oom) + "\n")
        (proc / "sys/kernel/random/boot_id").write_text(self.boot + "\n")
        return proc

    def test_config_rejects_unsafe_labels_thresholds_and_timing(self):
        for override in ({"host_label": "@everyone"}, {"environment": "dev\nsecret"},
                         {"cpu_high": 75, "cpu_recover": 80}, {"memory_high": 101},
                         {"trigger_samples": 0}, {"trigger_samples": True},
                         {"interval_seconds": 1}, {"max_sample_gap_seconds": 60},
                         {"cpu_high": float("nan")}):
            with self.subTest(override=override), self.assertRaises((ValueError, TypeError)):
                m.Config(**override)

    def test_reads_host_metrics_using_available_memory_without_double_counting_guest(self):
        value = m.read_snapshot(self.proc_fixture(), now=1200)
        self.assertEqual(value.cpu_total, 1030)
        self.assertEqual(value.cpu_idle, 850)
        self.assertEqual(value.memory_percent, 80)
        self.assertEqual(value.oom_kills, 14)

    def test_missing_or_invalid_metrics_fail_instead_of_assuming_healthy(self):
        for available in (-1, 1001):
            with self.subTest(available=available), self.assertRaises(ValueError):
                m.read_snapshot(self.proc_fixture(available=available))
        proc = self.proc_fixture()
        (proc / "vmstat").write_text("pgfault 42\n")
        with self.assertRaises(KeyError):
            m.read_snapshot(proc)

    def test_cpu_uses_counter_delta_and_handles_reboot_and_reset(self):
        self.sample()
        previous = self.state["sample"].copy()
        sample = self.sample(cpu=90)
        self.assertEqual(m.cpu_percent(previous, sample), 90)
        self.assertIsNone(m.cpu_percent(previous, m.Snapshot(1200, "new", 1, 1, 50, 0)))
        self.assertIsNone(m.cpu_percent(previous, m.Snapshot(1200, self.boot, 1, 1, 50, 0)))

    def test_first_install_baselines_existing_oom_and_cpu(self):
        self.sample(cpu=99, memory=95)
        self.assertIsNone(self.state["metrics"]["CPU"]["value"])
        self.assertEqual(self.state["oom_pending"], 0)
        self.assertEqual(self.events(), [])

    def test_requires_three_high_samples_and_sends_only_once_per_incident(self):
        self.sample()
        for _ in range(2):
            self.sample(cpu=90, memory=95)
            self.assertEqual(self.events(), [])
        self.sample(cpu=90, memory=95)
        events = self.events()
        self.assertEqual({e["kind"] for e in events}, {"CPU", "RAM"})
        m.acknowledge(self.state, events, self.now)
        for _ in range(10):
            self.sample(cpu=99, memory=99)
        self.assertEqual(self.events(), [])
        self.assertEqual(self.state["metrics"]["CPU"]["high"], 3)

    def test_two_low_samples_send_recovery_and_new_incident_can_alert_again(self):
        events = self.establish_alarm()
        m.acknowledge(self.state, events, self.now)
        self.sample(cpu=70, memory=75)
        self.assertEqual(self.events(), [])
        self.sample(cpu=70, memory=75)
        recovery = self.events()
        self.assertEqual(len(recovery), 2)
        self.assertTrue(all(not event["alarm"] for event in recovery))
        m.acknowledge(self.state, recovery, self.now)
        for _ in range(3):
            self.sample(cpu=90, memory=95)
        self.assertEqual(len(self.events()), 2)

    def test_hysteresis_band_does_not_send_a_false_recovery(self):
        events = self.establish_alarm()
        m.acknowledge(self.state, events, self.now)
        for _ in range(5):
            self.sample(cpu=80, memory=85)
        self.assertEqual(self.events(), [])
        self.assertTrue(self.state["metrics"]["CPU"]["alarm"])

    def test_rapid_invocations_do_not_count_as_consecutive_minute_samples(self):
        self.sample()
        for _ in range(3):
            self.sample(cpu=90, memory=95, seconds=1)
        self.assertEqual(self.events(), [])
        self.assertEqual(self.state["metrics"]["CPU"]["high"], 0)

    def test_large_gap_breaks_streak_but_does_not_bypass_oom_cooldown(self):
        self.sample()
        self.oom += 1
        self.sample(cpu=90, memory=95)
        m.acknowledge(self.state, self.events(), self.now)
        self.sample(cpu=90, memory=95)
        self.oom += 2
        self.sample(cpu=90, memory=95, seconds=240)
        self.assertEqual(self.events(), [])
        self.assertEqual(self.state["oom_pending"], 2)
        self.assertIsNone(self.state["metrics"]["CPU"]["value"])

    def test_oom_increments_are_batched_and_survive_reboot_without_replaying_boot_totals(self):
        self.sample()
        self.oom += 2
        self.sample()
        first = self.events()
        self.assertEqual(first, [{"kind": "OOM", "count": 2}])
        m.acknowledge(self.state, first, self.now)
        self.oom += 3
        self.sample()
        self.assertEqual(self.events(), [])
        for _ in range(14):
            self.sample()
        self.assertEqual(self.events(), [{"kind": "OOM", "count": 3}])
        self.boot = "second-boot"
        self.oom = 7  # Pre-existing current-boot total is baselined, not added.
        self.sample()
        self.assertEqual(self.state["oom_pending"], 3)

    def test_alarm_that_recovers_before_first_delivery_does_not_send_stale_warning(self):
        self.establish_alarm()  # Do not acknowledge a notification that was never sent.
        self.sample(cpu=20, memory=50)
        self.sample(cpu=20, memory=50)
        self.assertEqual(self.events(), [])

    def test_delayed_alert_keeps_qualifying_reading_and_observation_time_not_current_low_value(self):
        events = self.establish_alarm()
        alarm_time = self.now
        self.sample(cpu=20, memory=50)  # One low reading is not confirmed recovery.
        delayed = self.events()
        self.assertEqual(delayed[0]["value"], events[0]["value"])
        self.assertEqual(delayed[0]["observed_at"], alarm_time)
        content = m.format_message(delayed, self.config, self.now)
        self.assertIn("판정 값: 90.0%", content)
        self.assertNotIn("판정 값: 20.0%", content)

    def test_state_round_trip_preserves_notification_and_corruption_is_not_reset(self):
        events = self.establish_alarm()
        m.acknowledge(self.state, events, self.now)
        m.save_state(self.state_path, self.state)
        self.assertEqual(m.load_state(self.state_path), self.state)
        self.assertEqual(self.state_path.stat().st_mode & 0o777, 0o600)
        self.state_path.write_text("{not-json")
        with self.assertRaises(ValueError):
            m.load_state(self.state_path)
        self.assertEqual(self.state_path.read_text(), "{not-json")

    def test_malformed_state_structures_fail_closed_without_replacing_them(self):
        self.sample()
        valid = self.state.copy()
        for state in ([], {"version": 1}, dict(valid, metrics=[]), dict(valid, sample=None)):
            with self.subTest(state=state):
                text = json.dumps(state)
                self.state_path.write_text(text)
                with self.assertRaises(ValueError):
                    m.load_state(self.state_path)
                self.assertEqual(self.state_path.read_text(), text)

    def test_run_once_persists_before_send_and_retains_pending_until_retry_success(self):
        self.sample()
        m.save_state(self.state_path, self.state)
        proc = self.proc_fixture(oom=self.oom + 1)
        observations = []

        def fail(url, content):
            observations.append(m.load_state(self.state_path)["oom_pending"])
            raise m.DeliveryError(retry_after=120)

        result = m.run_once(self.config, self.state_path, WEBHOOK, proc, self.now + 60, fail)
        self.assertIn("delivery failed", result)
        self.assertEqual(observations, [1])
        saved = m.load_state(self.state_path)
        self.assertEqual(saved["oom_pending"], 1)
        self.assertEqual(saved["next_attempt_at"], self.now + 180)
        self.assertNotIn(WEBHOOK, self.state_path.read_text())
        sender = MagicMock()
        m.run_once(self.config, self.state_path, WEBHOOK, proc, self.now + 120, sender)
        sender.assert_not_called()
        m.run_once(self.config, self.state_path, WEBHOOK, proc, self.now + 180, sender)
        sender.assert_called_once()
        self.assertEqual(m.load_state(self.state_path)["oom_pending"], 0)

    def test_concurrent_invocation_cannot_replace_state_or_send(self):
        with self.state_path.with_suffix(".lock").open("a") as lock:
            m.fcntl.flock(lock, m.fcntl.LOCK_EX | m.fcntl.LOCK_NB)
            sender = MagicMock()
            result = m.run_once(self.config, self.state_path, WEBHOOK, self.root / "missing", sender=sender)
        self.assertIn("another monitor", result)
        sender.assert_not_called()
        self.assertFalse(self.state_path.exists())

    def test_delivery_is_explicit_https_no_redirects_and_no_mentions(self):
        opener = MagicMock()
        opener.open.return_value.__enter__.return_value.status = 200
        m.post_discord(WEBHOOK, "example @everyone", opener)
        req = opener.open.call_args.args[0]
        self.assertTrue(req.full_url.endswith("?wait=true"))
        self.assertEqual(json.loads(req.data)["allowed_mentions"], {"parse": []})
        self.assertEqual(opener.open.call_args.kwargs["timeout"], 5)
        for url in ("http://discord.com/api/webhooks/123/token", "https://evil.example/api/webhooks/123/token",
                    WEBHOOK + "?thread_id=1", "https://discord.com.evil.example/api/webhooks/1/x"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                m.post_discord(url, "test", opener)
        with self.assertRaises(m.DeliveryError):
            m.NoRedirect().redirect_request(None, None, 302, "", {}, "https://evil.example")

    def test_rate_limit_and_http_errors_are_sanitized_and_not_acknowledged(self):
        opener = MagicMock()
        opener.open.side_effect = error.HTTPError(
            WEBHOOK, 429, "secret", {"Retry-After": "120"}, io.BytesIO(b"private response"))
        with self.assertRaises(m.DeliveryError) as caught:
            m.post_discord(WEBHOOK, "test", opener)
        self.assertEqual(caught.exception.retry_after, 120)
        self.assertNotIn(WEBHOOK, str(caught.exception))
        self.assertNotIn("secret", str(caught.exception))

    def test_network_and_non_success_statuses_do_not_report_delivery_success(self):
        for status in (204, 302, 500):
            opener = MagicMock()
            opener.open.return_value.__enter__.return_value.status = status
            with self.subTest(status=status), self.assertRaises(m.DeliveryError):
                m.post_discord(WEBHOOK, "test", opener)
        opener = MagicMock()
        opener.open.side_effect = error.URLError("private URL or proxy details")
        with self.assertRaises(m.DeliveryError) as caught:
            m.post_discord(WEBHOOK, "test", opener)
        self.assertNotIn("proxy", str(caught.exception))

    def test_backward_clock_and_reboot_reset_streak_without_inventing_oom_events(self):
        self.sample()
        self.sample(cpu=99, memory=95)
        self.sample(cpu=99, memory=95, seconds=-120)
        self.assertEqual(self.events(), [])
        self.assertIsNone(self.state["metrics"]["CPU"]["value"])
        self.boot = "new-boot"
        self.oom = 100
        self.sample(cpu=99, memory=95)
        self.assertEqual(self.state["oom_pending"], 0)
        self.assertEqual(self.events(), [])

    def test_test_webhook_is_explicitly_labeled_and_does_not_change_monitor_state(self):
        credential = self.root / "webhook"
        credential.write_text(WEBHOOK)
        with patch.object(m, "post_discord") as sender, patch.object(m, "read_snapshot") as reader:
            with contextlib.redirect_stdout(io.StringIO()):
                result = m.main(["--test-webhook", "--webhook-file", str(credential),
                                 "--state", str(self.state_path)])
        self.assertEqual(result, 0)
        self.assertIn("실제 장애 아님", sender.call_args.args[1])
        reader.assert_not_called()
        self.assertFalse(self.state_path.exists())

    def test_default_preview_never_reads_host_writes_state_or_sends(self):
        with patch.object(m, "read_snapshot") as reader, patch.object(m, "post_discord") as sender:
            with contextlib.redirect_stdout(io.StringIO()) as out:
                result = m.main(["--state", str(self.state_path)])
        self.assertEqual(result, 0)
        self.assertIn("PREVIEW ONLY", out.getvalue())
        self.assertIn("실제 장애 아님", out.getvalue())
        reader.assert_not_called()
        sender.assert_not_called()
        self.assertFalse(self.state_path.exists())

    def test_missing_credential_and_invalid_config_fail_without_secret_output(self):
        credential = self.root / "webhook"
        credential.write_text("https://evil.example/private-secret")
        for args in (["--once"], ["--test-webhook", "--webhook-file", str(credential)]):
            with self.subTest(args=args), contextlib.redirect_stderr(io.StringIO()) as out:
                self.assertEqual(m.main(args), 1)
                self.assertNotIn("private-secret", out.getvalue())

    def test_systemd_files_keep_runtime_unprivileged_and_poll_interval_consistent(self):
        directory = Path(__file__).parent
        service = configparser.ConfigParser(interpolation=None)
        service.read(directory / "giut-resource-monitor.service")
        self.assertEqual(service["Service"]["DynamicUser"], "yes")
        self.assertEqual(service["Service"]["NoNewPrivileges"], "yes")
        self.assertEqual(service["Service"]["ProtectSystem"], "strict")
        self.assertIn("LoadCredential", service["Service"])
        self.assertIn("--once", service["Service"]["ExecStart"])
        self.assertIn("${CREDENTIALS_DIRECTORY}/discord-webhook", service["Service"]["ExecStart"])
        timer = configparser.ConfigParser(interpolation=None)
        timer.read(directory / "giut-resource-monitor.timer")
        config = m.Config(**json.loads((directory / "config.example.json").read_text()))
        self.assertEqual(timer["Timer"]["OnUnitActiveSec"], str(config.interval_seconds) + "s")

    def test_proc_fixture_end_to_end_warns_suppresses_and_recovers_without_network(self):
        proc = self.proc_fixture(available=50)
        cpu_user, cpu_idle = 100, 900
        messages = []
        sender = lambda url, content: messages.append(content)
        for index in range(5):
            (proc / "stat").write_text("cpu " + str(cpu_user) + " 0 0 " + str(cpu_idle) + " 0 0 0 0\n")
            m.run_once(self.config, self.state_path, WEBHOOK, proc, 1000 + index * 60, sender)
            cpu_user += 900
            cpu_idle += 100
        self.assertEqual(len(messages), 2)  # RAM's third point, then CPU's third interval.
        self.assertTrue(any("CPU 과부하" in value for value in messages))
        self.assertTrue(any("RAM 과부하" in value for value in messages))
        for index in range(2):
            # Start from the last actually recorded counters (before the loop's final increment).
            if index == 0:
                cpu_user -= 900
                cpu_idle -= 100
            cpu_user += 200
            cpu_idle += 800
            (proc / "stat").write_text("cpu " + str(cpu_user) + " 0 0 " + str(cpu_idle) + " 0 0 0 0\n")
            (proc / "meminfo").write_text("MemTotal: 1000 kB\nMemAvailable: 500 kB\n")
            m.run_once(self.config, self.state_path, WEBHOOK, proc, 1300 + index * 60, sender)
        self.assertEqual(len(messages), 3)
        self.assertIn("CPU 정상 범위 복귀", messages[-1])
        self.assertIn("RAM 정상 범위 복귀", messages[-1])


if __name__ == "__main__":
    unittest.main()
