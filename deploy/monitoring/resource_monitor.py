#!/usr/bin/env python3
"""Read-only Linux metrics; explicit --once enables state writes and Discord delivery."""
import argparse
import copy
import fcntl
import json
import math
import os
import re
import sys
import tempfile
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib import error, parse, request


@dataclass(frozen=True)
class Config:
    environment: str = "dev"
    host_label: str = "giut-dev-ec2"
    cpu_high: float = 85
    cpu_recover: float = 75
    memory_high: float = 90
    memory_recover: float = 80
    trigger_samples: int = 3
    recovery_samples: int = 2
    interval_seconds: int = 60
    max_sample_gap_seconds: int = 180
    oom_cooldown_seconds: int = 900

    def __post_init__(self):
        for label in (self.environment, self.host_label):
            if not isinstance(label, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", label):
                raise ValueError("environment/host_label must be short ASCII labels")
        for high, low in ((self.cpu_high, self.cpu_recover), (self.memory_high, self.memory_recover)):
            if (type(high) not in (int, float) or type(low) not in (int, float)
                    or not 0 <= low < high <= 100):
                raise ValueError("thresholds must satisfy 0 <= recovery < high <= 100")
        for value in (self.trigger_samples, self.recovery_samples, self.interval_seconds,
                      self.max_sample_gap_seconds, self.oom_cooldown_seconds):
            if type(value) is not int or not 1 <= value <= 86400:
                raise ValueError("sample counts and seconds must be positive bounded integers")
        if self.interval_seconds < 30 or self.max_sample_gap_seconds < self.interval_seconds * 2:
            raise ValueError("interval must be >= 30s and maximum gap >= two intervals")


@dataclass(frozen=True)
class Snapshot:
    timestamp: float
    boot_id: str
    cpu_total: int
    cpu_idle: int
    memory_percent: float
    oom_kills: int


def read_snapshot(proc=Path("/proc"), now=None):
    values = proc.joinpath("stat").read_text().splitlines()[0].split()
    if values[0] != "cpu" or len(values) < 9:
        raise ValueError("aggregate /proc/stat CPU fields unavailable")
    ticks = [int(value) for value in values[1:9]]  # guest is already counted in user/nice.
    if any(value < 0 for value in ticks):
        raise ValueError("negative CPU counters")
    mem = {}
    for line in proc.joinpath("meminfo").read_text().splitlines():
        key, value = line.split(":", 1)
        if key in ("MemTotal", "MemAvailable"):
            fields = value.split()
            if len(fields) != 2 or fields[1] != "kB":
                raise ValueError("unexpected memory unit")
            mem[key] = int(fields[0])
    total, available = mem["MemTotal"], mem["MemAvailable"]
    if total <= 0 or not 0 <= available <= total:
        raise ValueError("invalid memory counters")
    vmstat = dict(line.split() for line in proc.joinpath("vmstat").read_text().splitlines())
    oom = int(vmstat["oom_kill"])
    boot = proc.joinpath("sys/kernel/random/boot_id").read_text().strip()
    if not boot or oom < 0:
        raise ValueError("boot ID or OOM counter unavailable")
    return Snapshot(time.time() if now is None else now, boot, sum(ticks), ticks[3] + ticks[4],
                    100 * (total - available) / total, oom)


def cpu_percent(previous, sample):
    if previous is None or previous["boot_id"] != sample.boot_id:
        return None
    total = sample.cpu_total - previous["cpu_total"]
    idle = sample.cpu_idle - previous["cpu_idle"]
    if total <= 0 or not 0 <= idle <= total:
        return None
    return 100 * (total - idle) / total


def new_metric():
    return {"alarm": False, "notified": False, "high": 0, "low": 0,
            "transition_value": None, "transition_at": None}


def advance(previous, sample, config):
    state = copy.deepcopy(previous) if previous is not None else {
        "version": 1, "metrics": {"CPU": new_metric(), "RAM": new_metric()},
        "oom_pending": 0, "oom_sent_at": 0, "next_attempt_at": 0, "failures": 0,
    }
    gap = sample.timestamp - previous["sample"]["timestamp"] if previous else None
    same_boot = previous is not None and previous["sample"]["boot_id"] == sample.boot_id
    # Manual rapid invocations must not masquerade as minute-spaced observations.
    if same_boot and gap is not None and 0 <= gap < config.interval_seconds * 0.75:
        return state
    consecutive = same_boot and gap is not None and 0 < gap <= config.max_sample_gap_seconds
    cpu = cpu_percent(previous["sample"], sample) if consecutive else None
    if not consecutive:
        for metric in state["metrics"].values():
            metric["high"] = metric["low"] = 0
    if previous is not None and (not same_boot or gap <= 0):
        # Reset network retry timestamps after reboot/backward wall-clock changes.
        state["next_attempt_at"] = state["oom_sent_at"] = 0
    if same_boot:
        state["oom_pending"] += max(0, sample.oom_kills - previous["sample"]["oom_kills"])
    for name, value, high, low in (
            ("CPU", cpu, config.cpu_high, config.cpu_recover),
            ("RAM", sample.memory_percent, config.memory_high, config.memory_recover)):
        metric = state["metrics"][name]
        metric["value"] = value
        if value is None:
            metric["high"] = metric["low"] = 0
            continue
        metric["high"] = min(config.trigger_samples, metric["high"] + 1) if value >= high else 0
        metric["low"] = min(config.recovery_samples, metric["low"] + 1) if value <= low else 0
        next_alarm = metric["alarm"]
        if metric["high"] >= config.trigger_samples:
            next_alarm = True
        elif metric["low"] >= config.recovery_samples:
            next_alarm = False
        if next_alarm != metric["alarm"]:
            metric["alarm"] = next_alarm
            metric["transition_value"] = value
            metric["transition_at"] = sample.timestamp
    state["sample"] = dict(timestamp=sample.timestamp, boot_id=sample.boot_id,
                           cpu_total=sample.cpu_total, cpu_idle=sample.cpu_idle,
                           oom_kills=sample.oom_kills)
    return state


def pending_events(state, now, config):
    if now < state["next_attempt_at"]:
        return []
    events = []
    for name, metric in state["metrics"].items():
        if metric["value"] is not None and metric["alarm"] != metric["notified"]:
            events.append({"kind": name, "alarm": metric["alarm"], "value": metric["transition_value"],
                           "observed_at": metric["transition_at"]})
    if state["oom_pending"] and (not state["oom_sent_at"]
            or now - state["oom_sent_at"] >= config.oom_cooldown_seconds):
        events.append({"kind": "OOM", "count": state["oom_pending"]})
    return events


def format_message(events, config, now, synthetic=False):
    stamp = datetime.fromtimestamp(now, timezone.utc).isoformat()
    lines = [("🧪 기웃 자원 알림 테스트 — 실제 장애 아님" if synthetic else "📊 기웃 서버 자원 알림"),
             "환경: " + config.environment, "대상: " + config.host_label, "전송 시각(UTC): " + stamp]
    for event in events:
        if event["kind"] == "OOM":
            lines.append("🚨 Linux OOM 강제 종료 감지: " + str(event["count"]) + "건"
                         " (호스트 전체, 종료 대상은 커널 로그 확인 필요)")
        else:
            name = event["kind"]
            high = config.cpu_high if name == "CPU" else config.memory_high
            low = config.cpu_recover if name == "CPU" else config.memory_recover
            if event["alarm"]:
                lines.append("🚨 " + name + " 과부하 판정 값: " + format(event["value"], ".1f")
                             + "% / 기준 " + str(high) + "% 이상 "
                             + str(config.trigger_samples) + "회 연속")
            else:
                lines.append("✅ " + name + " 정상 범위 복귀 판정 값: " + format(event["value"], ".1f")
                             + "% / 기준 " + str(low) + "% 이하 "
                             + str(config.recovery_samples) + "회 연속")
            observed = datetime.fromtimestamp(event.get("observed_at", now), timezone.utc).isoformat()
            lines.append("판정 시각(UTC): " + observed)
    return "\n".join(lines)


class DeliveryError(Exception):
    def __init__(self, retry_after=0):
        super().__init__("Discord delivery failed; URL and response body redacted")
        self.retry_after = retry_after


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise DeliveryError()


def validate_webhook(url):
    parsed = parse.urlsplit(url)
    if (parsed.scheme != "https" or parsed.hostname != "discord.com"
            or parsed.netloc != "discord.com" or parsed.query or parsed.fragment
            or not re.fullmatch(r"/api/webhooks/[0-9]+/[A-Za-z0-9_.-]+", parsed.path)):
        raise ValueError("use a standard https://discord.com/api/webhooks/... URL")


def post_discord(url, content, opener=None):
    validate_webhook(url)
    payload = json.dumps({"content": content, "allowed_mentions": {"parse": []}},
                         ensure_ascii=False).encode("utf-8")
    req = request.Request(url + "?wait=true", data=payload, method="POST",
                          headers={"Content-Type": "application/json", "User-Agent": "GiutResourceMonitor/1.0"})
    if opener is None:
        opener = request.build_opener(request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(req, timeout=5) as response:
            if response.status != 200:
                raise DeliveryError()
    except error.HTTPError as exc:
        retry_after = 0
        if exc.code == 429:
            try:
                retry_after = min(86400, max(0, float(exc.headers.get("Retry-After", "0"))))
            except (TypeError, ValueError):
                pass
        exc.close()
        raise DeliveryError(retry_after) from None
    except (error.URLError, TimeoutError, OSError):
        raise DeliveryError() from None


def acknowledge(state, events, now):
    for event in events:
        if event["kind"] == "OOM":
            state["oom_pending"] -= event["count"]
            state["oom_sent_at"] = now
        else:
            state["metrics"][event["kind"]]["notified"] = event["alarm"]
    state["next_attempt_at"] = state["failures"] = 0


def save_state(path, state):
    # Atomic replace protects persisted alert state from partial writes on process death.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", dir=path.parent, prefix=".state-", delete=False) as out:
            temporary = Path(out.name)
            json.dump(state, out, allow_nan=False)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def load_state(path):
    if not path.exists():
        return None
    state = json.loads(path.read_text())
    if (not isinstance(state, dict) or not isinstance(state.get("metrics"), dict)
            or not isinstance(state.get("sample"), dict)):
        raise ValueError("invalid monitor state structure")
    if state.get("version") != 1 or set(state.get("metrics", {})) != {"CPU", "RAM"}:
        raise ValueError("invalid monitor state; preserve the file and investigate")
    for key in ("oom_pending", "oom_sent_at", "next_attempt_at", "failures"):
        if type(state.get(key)) not in (int, float) or not math.isfinite(state[key]) or state[key] < 0:
            raise ValueError("invalid persisted counters")
    for key in ("timestamp", "cpu_total", "cpu_idle", "oom_kills"):
        value = state["sample"][key]
        if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
            raise ValueError("invalid persisted sample")
    if not isinstance(state["sample"]["boot_id"], str) or not state["sample"]["boot_id"]:
        raise ValueError("invalid persisted boot ID")
    for metric in state["metrics"].values():
        if not isinstance(metric, dict):
            raise ValueError("invalid persisted metric structure")
        if type(metric["alarm"]) is not bool or type(metric["notified"]) is not bool:
            raise ValueError("invalid persisted alarm")
        if any(type(metric[k]) is not int or metric[k] < 0 for k in ("high", "low")):
            raise ValueError("invalid persisted streak")
        if metric["value"] is not None and not (
                type(metric["value"]) in (int, float) and 0 <= metric["value"] <= 100):
            raise ValueError("invalid persisted metric")
        if metric["alarm"] != metric["notified"] and (
                type(metric.get("transition_value")) not in (int, float)
                or not 0 <= metric["transition_value"] <= 100
                or type(metric.get("transition_at")) not in (int, float)
                or not math.isfinite(metric["transition_at"]) or metric["transition_at"] < 0):
            raise ValueError("invalid pending transition")
    return state


def run_once(config, state_path, webhook, proc=Path("/proc"), now=None, sender=post_discord):
    state_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with state_path.with_suffix(".lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return "another monitor invocation is running"
        sample = read_snapshot(proc, now)
        state = advance(load_state(state_path), sample, config)
        save_state(state_path, state)  # Persist pending events BEFORE attempting external I/O.
        events = pending_events(state, sample.timestamp, config)
        if not events:
            return "sample recorded; no notification due"
        try:
            sender(webhook, format_message(events, config, sample.timestamp))
        except DeliveryError as exc:
            state["failures"] = min(10, state["failures"] + 1)
            delay = max(min(900, config.interval_seconds * 2 ** (state["failures"] - 1)), exc.retry_after)
            state["next_attempt_at"] = sample.timestamp + delay
            save_state(state_path, state)
            return "delivery failed; pending notification retained for retry"
        acknowledge(state, events, sample.timestamp)
        save_state(state_path, state)
        return "notification delivered"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("config.example.json"))
    parser.add_argument("--state", type=Path, default=Path("/var/lib/giut-resource-monitor/state.json"))
    parser.add_argument("--webhook-file", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--preview", action="store_true", help="synthetic output only (the default)")
    mode.add_argument("--check", action="store_true", help="Linux read-only metric check; no state or network")
    mode.add_argument("--test-webhook", action="store_true", help="send one labeled synthetic test; no state")
    mode.add_argument("--once", action="store_true", help="sample, persist and send due notifications")
    args = parser.parse_args(argv)
    try:
        config = Config(**json.loads(args.config.read_text()))
        if args.check:
            first = read_snapshot()
            time.sleep(1)
            sample = read_snapshot()
            print(json.dumps({"cpu_percent": cpu_percent(vars(first), sample),
                              "memory_percent": sample.memory_percent,
                              "oom_kill_total": sample.oom_kills}, ensure_ascii=False))
        elif args.once or args.test_webhook:
            if args.webhook_file is None:
                raise ValueError("--webhook-file is required for delivery")
            webhook = args.webhook_file.read_text().strip()
            validate_webhook(webhook)
            if args.test_webhook:
                post_discord(webhook, format_message([{"kind": "CPU", "alarm": True, "value": 91.2},
                    {"kind": "RAM", "alarm": True, "value": 93.4}, {"kind": "OOM", "count": 1}],
                    config, time.time(), synthetic=True))
                print("synthetic test delivered; monitor thresholds and state were not changed")
            else:
                result = run_once(config, args.state, webhook)
                print(result)
                if result.startswith("delivery failed"):
                    return 1
        else:
            print(format_message([{"kind": "CPU", "alarm": True, "value": 91.2},
                {"kind": "RAM", "alarm": True, "value": 93.4}, {"kind": "OOM", "count": 1},
                {"kind": "CPU", "alarm": False, "value": 65.0}], config, time.time(), synthetic=True))
            print("\nPREVIEW ONLY: no metrics read, state written or network request made.")
        return 0
    except (OSError, ValueError, TypeError, KeyError, IndexError, DeliveryError) as exc:
        # Never print exception text: HTTP errors and file paths may contain secrets.
        print("Monitor failed (" + type(exc).__name__
              + "); check configuration, Linux /proc, credential and state permissions. Details redacted.",
              file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
