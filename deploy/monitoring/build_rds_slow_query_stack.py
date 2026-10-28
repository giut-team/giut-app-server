#!/usr/bin/env python3
"""Generate a standalone CloudFormation JSON file locally; never calls AWS."""
import argparse
import json
from pathlib import Path


FILTER_PATTERN = "%#[ ]+Query_time:[ ]+[0-9]+%"


def ref(name):
    return {"Ref": name}


def sub(value):
    return {"Fn::Sub": value}


def arn(name):
    return {"Fn::GetAtt": [name, "Arn"]}


def resource(kind, properties, **extra):
    return {"Type": "AWS::" + kind, "Properties": properties, **extra}


def build_template():
    alarm_name = sub("${AWS::StackName}-slowquery")
    alarm_arn = sub("arn:${AWS::Partition}:cloudwatch:${AWS::Region}:${AWS::AccountId}:alarm:${AWS::StackName}-slowquery")
    metric_name = sub("${AWS::StackName}-SlowLogEvents")
    source = Path(__file__).with_name("rds_slow_query_alert.py").read_text()
    return {
        "AWSTemplateFormatVersion": "2010-09-09",
        "Description": "Giut RDS slowquery alarm to Discord (no RDS changes, no raw SQL forwarding)",
        "Parameters": {
            "Environment": {"Type": "String", "Default": "dev",
                            "AllowedPattern": "[A-Za-z0-9_.-]{1,32}"},
            "SlowLogGroupName": {"Type": "String",
                                "AllowedPattern": "/aws/rds/instance/[A-Za-z0-9-]+/slowquery",
                                "Description": "Existing Standard-class RDS MySQL slowquery log group"},
            "WebhookSecretArn": {"Type": "String",
                                 "AllowedPattern": "arn:aws:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+",
                                 "Description": "Same-region secret JSON with webhook_url; use aws/secretsmanager key"},
        },
        "Resources": {
            "AlarmTopic": resource("SNS::Topic", {}),
            "AlarmTopicPolicy": resource("SNS::TopicPolicy", {
                "Topics": [ref("AlarmTopic")],
                "PolicyDocument": {"Version": "2012-10-17", "Statement": [{
                    "Effect": "Allow", "Principal": {"Service": "cloudwatch.amazonaws.com"},
                    "Action": "sns:Publish", "Resource": ref("AlarmTopic"),
                    "Condition": {"StringEquals": {"aws:SourceAccount": ref("AWS::AccountId")},
                                  "ArnEquals": {"aws:SourceArn": alarm_arn}}}]} }),
            "DeliveryFailures": resource("SQS::Queue", {
                "MessageRetentionPeriod": 604800, "SqsManagedSseEnabled": True}),
            "LambdaLogGroup": resource("Logs::LogGroup", {
                "LogGroupName": sub("/aws/lambda/${AWS::StackName}-discord"),
                "RetentionInDays": 14}),
            "DeliveryRole": resource("IAM::Role", {
                "AssumeRolePolicyDocument": {"Version": "2012-10-17", "Statement": [{
                    "Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"},
                    "Action": "sts:AssumeRole"}]},
                "Policies": [{"PolicyName": "AlarmDeliveryOnly",
                              "PolicyDocument": {"Version": "2012-10-17", "Statement": [
                                  {"Effect": "Allow", "Action": ["logs:CreateLogStream", "logs:PutLogEvents"],
                                   "Resource": arn("LambdaLogGroup")},
                                  {"Effect": "Allow", "Action": "secretsmanager:GetSecretValue",
                                   "Resource": ref("WebhookSecretArn")},
                                  {"Effect": "Allow", "Action": "sqs:SendMessage",
                                   "Resource": arn("DeliveryFailures")},
                              ]}}]}),
            "DeliveryFunction": resource("Lambda::Function", {
                "FunctionName": sub("${AWS::StackName}-discord"),
                "Runtime": "python3.12", "Handler": "index.handler",
                "Role": arn("DeliveryRole"), "Timeout": 20, "MemorySize": 128,
                "Code": {"ZipFile": source},
                "Environment": {"Variables": {
                    "ENVIRONMENT": ref("Environment"),
                    "WEBHOOK_SECRET_ARN": ref("WebhookSecretArn"),
                    "EXPECTED_TOPIC_ARN": ref("AlarmTopic"),
                    "EXPECTED_ALARM_ARN": alarm_arn,
                    "SLOW_LOG_GROUP_NAME": ref("SlowLogGroupName"),
                }}}, DependsOn=["LambdaLogGroup"]),
            "AsyncDelivery": resource("Lambda::EventInvokeConfig", {
                "FunctionName": ref("DeliveryFunction"), "Qualifier": "$LATEST",
                "MaximumRetryAttempts": 2, "MaximumEventAgeInSeconds": 3600,
                "DestinationConfig": {"OnFailure": {"Destination": arn("DeliveryFailures")}}}),
            "SnsInvokePermission": resource("Lambda::Permission", {
                "Action": "lambda:InvokeFunction", "FunctionName": ref("DeliveryFunction"),
                "Principal": "sns.amazonaws.com", "SourceArn": ref("AlarmTopic"),
                "SourceAccount": ref("AWS::AccountId")}),
            "AlarmSubscription": resource("SNS::Subscription", {
                "TopicArn": ref("AlarmTopic"), "Protocol": "lambda",
                "Endpoint": arn("DeliveryFunction")},
                DependsOn=["SnsInvokePermission", "AsyncDelivery"]),
            "SlowQueryMetric": resource("Logs::MetricFilter", {
                "LogGroupName": ref("SlowLogGroupName"), "FilterPattern": FILTER_PATTERN,
                "MetricTransformations": [{
                    "MetricNamespace": "Giut/RDS", "MetricName": metric_name,
                    "MetricValue": "1", "DefaultValue": 0, "Unit": "Count",
                }]}),
            "SlowQueryAlarm": resource("CloudWatch::Alarm", {
                "AlarmName": alarm_name,
                "AlarmDescription": "Slowquery log events in 5min; absence is not proof of DB health",
                "Namespace": "Giut/RDS", "MetricName": metric_name,
                "Statistic": "Sum", "Period": 300, "EvaluationPeriods": 1,
                "DatapointsToAlarm": 1, "Threshold": 1,
                "ComparisonOperator": "GreaterThanOrEqualToThreshold",
                "TreatMissingData": "notBreaching",
                "AlarmActions": [ref("AlarmTopic")], "OKActions": [ref("AlarmTopic")],
            }, DependsOn=["SlowQueryMetric", "AlarmSubscription", "AlarmTopicPolicy"]),
        },
        "Outputs": {
            "AlarmName": {"Value": ref("SlowQueryAlarm")},
            "AlarmArn": {"Value": arn("SlowQueryAlarm")},
            "FunctionName": {"Value": ref("DeliveryFunction")},
            "FailureQueueUrl": {"Value": ref("DeliveryFailures")},
            "SlowLogGroup": {"Value": ref("SlowLogGroupName")},
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    # Generated artifact, not a credential/config file. Never overwrite existing files.
    with args.output.open("x") as output:
        json.dump(build_template(), output, ensure_ascii=False, indent=2)
        output.write("\n")
    print("CloudFormation template generated locally; nothing was deployed.")


if __name__ == "__main__":
    main()
