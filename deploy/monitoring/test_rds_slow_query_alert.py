import io
import json
import os
import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
from urllib.error import URLError

import build_rds_slow_query_stack as stack
import rds_slow_query_alert as alert


TOPIC = "arn:aws:sns:ap-northeast-2:123456789012:test-topic"
ALARM = "arn:aws:cloudwatch:ap-northeast-2:123456789012:alarm:test-slowquery"
SECRET = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:test-abc123"
WEBHOOK = "https://discord.com/api/webhooks/123456789/FAKE_TOKEN"
ENV = {"ENVIRONMENT": "dev", "EXPECTED_TOPIC_ARN": TOPIC,
       "EXPECTED_ALARM_ARN": ALARM, "WEBHOOK_SECRET_ARN": SECRET}


def event(state="ALARM", old="OK", **extra):
    alarm = dict(AlarmArn=ALARM, NewStateValue=state, OldStateValue=old,
                 StateChangeTime="2026-10-23T09:00:00.000+0000",
                 NewStateReason="secret SQL wjdanr@example.invalid @everyone",
                 AlarmDescription="private SQL", **extra)
    return {"Records": [{"EventSource": "aws:sns", "Sns": {
        "TopicArn": TOPIC, "Message": json.dumps(alarm)}}]}


class HandlerTest(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, ENV, clear=True)
        self.env.start()
        self.addCleanup(self.env.stop)
        self.client = patch.object(alert, "aws_client")
        self.aws = self.client.start()
        self.addCleanup(self.client.stop)
        self.aws.return_value.get_secret_value.return_value = {
            "SecretString": json.dumps({"webhook_url": WEBHOOK})}
        self.delivery = patch.object(alert, "send")
        self.send = self.delivery.start()
        self.addCleanup(self.delivery.stop)

    def test_alarm_summary_only(self):
        self.assertEqual({"status": "sent"}, alert.handler(event(), None))
        payload, url = self.send.call_args.args
        self.assertIn("느린 쿼리 감지", payload["content"])
        self.assertIn("5분", payload["content"])
        self.assertIn("2026-10-23T09:00:00+00:00", payload["content"])
        for secret in ("private SQL", "wjdanr", "@everyone", WEBHOOK, "secret SQL"):
            self.assertNotIn(secret, payload["content"])
        self.assertEqual({"parse": []}, payload["allowed_mentions"])
        self.assertEqual(WEBHOOK + "?wait=true", url)
        self.aws.assert_called_once_with("secretsmanager")
        self.aws.return_value.get_secret_value.assert_called_once_with(SecretId=SECRET)

    def test_recovery_is_not_health_claim(self):
        alert.handler(event("OK", "ALARM"), None)
        text = self.send.call_args.args[0]["content"]
        self.assertIn("경보 해제", text)
        self.assertIn("보장하지는 않습니다", text)

    def test_synthetic_state_change_is_labeled_without_forwarding_reason(self):
        value = event()
        body = json.loads(value["Records"][0]["Sns"]["Message"])
        body["NewStateReason"] = "GIUT_SYNTHETIC_TEST"
        value["Records"][0]["Sns"]["Message"] = json.dumps(body)
        alert.handler(value, None)
        self.assertIn("[연결 테스트]", self.send.call_args.args[0]["content"])

    def test_initial_ok_and_insufficient_are_ignored_without_secret_access(self):
        for value in (event("OK", "INSUFFICIENT_DATA"), event("INSUFFICIENT_DATA")):
            self.assertEqual({"status": "ignored"}, alert.handler(value, None))
        self.aws.assert_not_called()
        self.send.assert_not_called()

    def test_reject_untrusted_topic_alarm_source_and_batch(self):
        cases = []
        value = event()
        value["Records"][0]["Sns"]["TopicArn"] = TOPIC + "-other"
        cases.append(value)
        value = event()
        body = json.loads(value["Records"][0]["Sns"]["Message"])
        body["AlarmArn"] = ALARM + "-other"
        value["Records"][0]["Sns"]["Message"] = json.dumps(body)
        cases.append(value)
        value = event()
        value["Records"][0]["EventSource"] = "aws:s3"
        cases += [value, {"Records": []}, {"Records": event()["Records"] * 2}]
        for value in cases:
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                alert.handler(value, None)
        self.aws.assert_not_called()
        self.send.assert_not_called()

    def test_invalid_messages_and_timestamps(self):
        for body in ("bad", "x" * 32769, "null", "[]"):
            value = event()
            value["Records"][0]["Sns"]["Message"] = body
            with self.assertRaises(RuntimeError):
                alert.handler(value, None)
        value = event()
        body = json.loads(value["Records"][0]["Sns"]["Message"])
        body["StateChangeTime"] = "2026-10-23T09:00:00"
        value["Records"][0]["Sns"]["Message"] = json.dumps(body)
        with self.assertRaises(RuntimeError):
            alert.handler(value, None)
        self.send.assert_not_called()

    def test_bad_labels_and_missing_env_fail_closed(self):
        for changes in ({"ENVIRONMENT": "@everyone"}, {"EXPECTED_TOPIC_ARN": ""}):
            with patch.dict(os.environ, changes), self.assertRaises(RuntimeError):
                alert.handler(event(), None)
        self.send.assert_not_called()

    def test_http_secret_and_aws_failures_are_redacted_and_retryable(self):
        for exc in (URLError(WEBHOOK), TimeoutError("private SQL"),
                    RuntimeError("webhook secret " + WEBHOOK)):
            self.send.side_effect = exc
            with self.assertRaises(RuntimeError) as caught:
                alert.handler(event(), None)
            self.assertNotIn("FAKE_TOKEN", str(caught.exception))
            self.assertNotIn("private SQL", str(caught.exception))
            self.assertTrue(caught.exception.__suppress_context__)
        self.send.side_effect = None
        self.aws.side_effect = RuntimeError(WEBHOOK)
        with self.assertRaises(RuntimeError) as caught:
            alert.handler(event(), None)
        self.assertNotIn(WEBHOOK, str(caught.exception))

    def test_only_standard_discord_urls(self):
        for url in ("http://discord.com/api/webhooks/123/a",
                    "https://evil.invalid/api/webhooks/123/a",
                    "https://discord.com.evil.invalid/api/webhooks/123/a",
                    "https://discord.com@evil.invalid/api/webhooks/123/a",
                    "https://discord.com:443/api/webhooks/123/a",
                    WEBHOOK + "?x=1", WEBHOOK + "#token", "file:///etc/passwd"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                alert.webhook_url(json.dumps({"webhook_url": url}))

    def test_invalid_secret_fails_without_delivery(self):
        for secret in ("not-json", "{}", '{"webhook_url": null}',
                       '{"webhook_url": "https://evil.invalid"}'):
            self.aws.return_value.get_secret_value.return_value = {"SecretString": secret}
            with self.assertRaises(RuntimeError):
                alert.handler(event(), None)
        self.send.assert_not_called()


class TransportTest(unittest.TestCase):
    def test_post_is_bounded_and_does_not_read_response_body(self):
        response = MagicMock()
        response.status = 200
        response.__enter__.return_value = response
        with patch.object(alert.request, "build_opener") as build:
            build.return_value.open.return_value = response
            alert.send({"content": "테스트", "allowed_mentions": {"parse": []}},
                       WEBHOOK + "?wait=true")
            req = build.return_value.open.call_args.args[0]
            self.assertEqual("POST", req.method)
            self.assertEqual("테스트", json.loads(req.data)["content"])
            self.assertEqual(4, build.return_value.open.call_args.kwargs["timeout"])
            self.assertEqual({}, build.call_args.args[0].proxies)
            self.assertIsInstance(build.call_args.args[1], alert.NoRedirect)
            response.read.assert_not_called()

    def test_non_success_responses_raise(self):
        for code in (301, 400, 429, 500):
            response = MagicMock()
            response.status = code
            response.__enter__.return_value = response
            with patch.object(alert.request, "build_opener") as build:
                build.return_value.open.return_value = response
                with self.assertRaises(RuntimeError):
                    alert.send({}, WEBHOOK)

    def test_redirect_is_denied(self):
        self.assertIsNone(alert.NoRedirect().redirect_request(
            None, None, 302, "", {}, "https://evil.invalid"))


class StackTest(unittest.TestCase):
    def setUp(self):
        self.template = stack.build_template()
        self.resources = self.template["Resources"]

    def test_embeds_exact_handler_and_stays_within_console_upload_limit(self):
        expected = Path(alert.__file__).read_text()
        props = self.resources["DeliveryFunction"]["Properties"]
        self.assertEqual(expected, props["Code"]["ZipFile"])
        self.assertEqual("index.handler", props["Handler"])
        compile(expected, "index.py", "exec")
        self.assertLess(len(json.dumps(self.template).encode()), 51200)
        self.assertNotIn("VpcConfig", props)
        self.assertNotIn("FAKE_TOKEN", json.dumps(self.template))

    def test_no_rds_change_or_secret_value_in_template(self):
        self.assertFalse(any(v["Type"].startswith("AWS::RDS::")
                             for v in self.resources.values()))
        self.assertNotIn("webhook_url", self.template["Parameters"])
        policies = self.resources["DeliveryRole"]["Properties"]["Policies"][0]
        statements = policies["PolicyDocument"]["Statement"]
        for statement in statements:
            self.assertNotEqual("*", statement["Resource"])
            self.assertNotEqual("*", statement["Action"])
        self.assertEqual(["logs:CreateLogStream", "logs:PutLogEvents"], statements[0]["Action"])
        self.assertEqual("secretsmanager:GetSecretValue", statements[1]["Action"])
        self.assertEqual("sqs:SendMessage", statements[2]["Action"])

    def test_alarm_window_metric_and_filter_semantics(self):
        metric = self.resources["SlowQueryMetric"]["Properties"]
        for sample, expected in [
            ("# Query_time: 1.023456  Lock_time: 0.0\nSELECT 1;", True),
            ("# Time: sample\n# User@Host: redacted\n# Query_time: 2.0", True),
            ("#   Query_time:  1.5", True),
            ("# Time: sample", False), ("SELECT 1", False)]:
            self.assertEqual(expected, bool(re.search(stack.FILTER_PATTERN.strip("%"), sample)))
        self.assertEqual(stack.FILTER_PATTERN, metric["FilterPattern"])
        alarm = self.resources["SlowQueryAlarm"]["Properties"]
        self.assertEqual((300, 1, 1, "Sum", "notBreaching"),
                         (alarm["Period"], alarm["EvaluationPeriods"], alarm["Threshold"],
                          alarm["Statistic"], alarm["TreatMissingData"]))
        self.assertEqual(metric["MetricTransformations"][0]["MetricName"], alarm["MetricName"])
        self.assertEqual(alarm["AlarmActions"], alarm["OKActions"])

    def test_subscription_ready_before_alarm_and_failure_destination(self):
        self.assertIn("AlarmSubscription", self.resources["SlowQueryAlarm"]["DependsOn"])
        self.assertIn("AsyncDelivery", self.resources["AlarmSubscription"]["DependsOn"])
        props = self.resources["AsyncDelivery"]["Properties"]
        self.assertEqual(2, props["MaximumRetryAttempts"])
        self.assertEqual({"Fn::GetAtt": ["DeliveryFailures", "Arn"]},
                         props["DestinationConfig"]["OnFailure"]["Destination"])
        self.assertEqual(604800, self.resources["DeliveryFailures"]["Properties"]["MessageRetentionPeriod"])
        permission = self.resources["SnsInvokePermission"]["Properties"]
        self.assertEqual({"Ref": "AlarmTopic"}, permission["SourceArn"])
        self.assertEqual({"Ref": "AWS::AccountId"}, permission["SourceAccount"])

    def test_local_builder_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "stack.json"
            with patch("sys.argv", ["builder", "--output", str(output)]), patch("sys.stdout", io.StringIO()):
                stack.main()
                self.assertEqual(self.template, json.loads(output.read_text()))
                with self.assertRaises(FileExistsError):
                    stack.main()


if __name__ == "__main__":
    unittest.main()
