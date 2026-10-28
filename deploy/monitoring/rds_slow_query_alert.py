"""CloudWatch alarm -> SNS -> Discord. Never read RDS logs or forward event text."""
import json
import os
import re
from datetime import datetime, timezone
from urllib import parse, request


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_clients = {}


def aws_client(service):
    # Built once per execution environment and reused across warm invocations,
    # so a warm call does not pay a fresh connection/TLS handshake every time.
    # boto3 is supplied by the Lambda Python runtime; offline tests replace this boundary.
    if service not in _clients:
        import boto3
        from botocore.config import Config
        _clients[service] = boto3.client(service, config=Config(
            connect_timeout=2, read_timeout=3, retries={"total_max_attempts": 2}))
    return _clients[service]


def log_group_console_url(region, log_group_name):
    """Deep link to the log group's page; no time range, no log content fetched here."""
    encoded = parse.quote(parse.quote(log_group_name, safe=""), safe="").replace("%", "$")
    return "https://{0}.console.aws.amazon.com/cloudwatch/home?region={0}#logsV2:log-groups/log-group/{1}".format(
        region, encoded)


def notification(record, environment, topic_arn, alarm_arn, log_group_name=None, region=None):
    """Allowlisted fields only; SNS reason/description/SQL are deliberately ignored."""
    if record.get("EventSource") != "aws:sns":
        raise ValueError("unexpected event source")
    sns = record["Sns"]
    if sns.get("TopicArn") != topic_arn:
        raise ValueError("unexpected topic")
    message = sns["Message"]
    if not isinstance(message, str) or len(message) > 32768:
        raise ValueError("invalid alarm message")
    alarm = json.loads(message)
    if alarm.get("AlarmArn") != alarm_arn:
        raise ValueError("unexpected alarm")
    state = alarm.get("NewStateValue")
    if state not in ("ALARM", "OK"):
        return None
    # Initial INSUFFICIENT_DATA -> OK is not a recovery.
    if state == "OK" and alarm.get("OldStateValue") != "ALARM":
        return None
    raw_time = alarm["StateChangeTime"].replace("Z", "+00:00")
    # SNS uses +0000; Python 3.9 offline verification requires the colon form.
    if re.search(r"[+-][0-9]{4}$", raw_time):
        raw_time = raw_time[:-2] + ":" + raw_time[-2:]
    timestamp = datetime.fromisoformat(raw_time)
    if timestamp.tzinfo is None:
        raise ValueError("timezone required")
    timestamp = timestamp.astimezone(timezone.utc).isoformat()
    if state == "ALARM":
        title = "🐢 기웃 RDS 느린 쿼리 감지"
        detail = "5분 평가 구간에서 느린 쿼리 로그 이벤트가 감지됐습니다."
    else:
        title = "✅ 기웃 RDS 느린 쿼리 경보 해제"
        detail = ("경보가 OK로 전환됐습니다. 새 로그가 없거나 기준 미만이라는 뜻이며, "
                  "DB 전체 정상·수집 정상까지 보장하지는 않습니다.")
    if alarm.get("NewStateReason") == "GIUT_SYNTHETIC_TEST":
        title = "🧪 [연결 테스트] " + title
    log_line = "\nCloudWatch의 RDS slowquery 로그와 수집 상태를 확인하세요."
    if log_group_name and region:
        log_line += ("\n로그 그룹: " + log_group_name
                     + "\n바로가기: " + log_group_console_url(region, log_group_name))
    return {
        "content": (title + "\n환경: " + environment + "\n경보 시각(UTC): " + timestamp
                    + "\n" + detail + log_line
                    + "\n원문 SQL·사용자 정보는 이 알림에 포함하지 않습니다."),
        "allowed_mentions": {"parse": []},
    }


def webhook_url(secret):
    value = json.loads(secret)["webhook_url"]
    if not isinstance(value, str):
        raise ValueError("invalid webhook")
    url = parse.urlsplit(value)
    if (url.scheme != "https" or url.netloc != "discord.com"
            or url.query or url.fragment
            or not re.fullmatch(r"/api/webhooks/[0-9]+/[A-Za-z0-9_.-]+", url.path)):
        raise ValueError("invalid webhook")
    # Ask Discord for a confirmation response, rather than fire-and-forget.
    return value + "?wait=true"


def send(payload, url):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = request.Request(url, data=body, method="POST", headers={
        "Content-Type": "application/json", "User-Agent": "GiutRdsSlowQueryAlert/1.0"})
    opener = request.build_opener(request.ProxyHandler({}), NoRedirect())
    with opener.open(req, timeout=4) as response:
        if not 200 <= response.status < 300:
            raise RuntimeError("Discord rejected notification")
        # Do not read/log Discord's response body or webhook URL.


def handler(event, context):
    try:
        environment = os.environ["ENVIRONMENT"]
        if not re.fullmatch(r"[A-Za-z0-9_.-]{1,32}", environment):
            raise ValueError("invalid environment")
        records = event["Records"]
        # SNS invokes Lambda with one message. Reject batches to avoid partial replay.
        if not isinstance(records, list) or len(records) != 1:
            raise ValueError("expected one SNS record")
        # Optional: absent on a stack not yet updated with this env var, or malformed -> no link, not a failure.
        log_group_name = os.environ.get("SLOW_LOG_GROUP_NAME", "")
        if not re.fullmatch(r"/aws/rds/instance/[A-Za-z0-9-]+/slowquery", log_group_name):
            log_group_name = None
        payload = notification(records[0], environment, os.environ["EXPECTED_TOPIC_ARN"],
                               os.environ["EXPECTED_ALARM_ARN"],
                               log_group_name, os.environ.get("AWS_REGION"))
        if payload is None:
            return {"status": "ignored"}
        secret = aws_client("secretsmanager").get_secret_value(
            SecretId=os.environ["WEBHOOK_SECRET_ARN"])["SecretString"]
        send(payload, webhook_url(secret))
        return {"status": "sent"}
    except Exception:
        # AWS/Lambda records exception strings. Never propagate event/secret/HTTP errors.
        # Failing (not returning 200) lets async retries and the failure destination work.
        raise RuntimeError("RDS alarm delivery failed; check configuration and AWS metrics") from None
