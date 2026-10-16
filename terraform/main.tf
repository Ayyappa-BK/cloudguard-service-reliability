terraform {
  required_version = ">= 1.6, < 2.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
}
provider "aws" { region = var.region }
variable "region" { default = "us-east-1" }
variable "cluster_name" { type = string }
variable "oidc_provider_arn" { type = string }
variable "oidc_issuer" { type = string }
resource "aws_ecr_repository" "service" {
  name                 = "cloudguard"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration { encryption_type = "AES256" }
}
resource "aws_cloudwatch_log_group" "service" {
  name              = "/cloudguard/service"
  retention_in_days = 14
}
resource "aws_sns_topic" "alerts" { name = "cloudguard-alerts" }
resource "aws_cloudwatch_metric_alarm" "errors" {
  alarm_name          = "cloudguard-availability-fast-burn"
  namespace           = "CloudGuard"
  metric_name         = "ErrorRate"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  threshold           = 1.44
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "breaching"
  dimensions          = { Service = "cloudguard" }
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
resource "aws_cloudwatch_dashboard" "service" {
  dashboard_name = "cloudguard"
  dashboard_body = jsonencode({ widgets = [{ type = "metric", width = 12, height = 6, properties = {
    region = var.region, title = "Error rate percent", metrics = [["CloudGuard", "ErrorRate", "Service", "cloudguard"]], period = 60, stat = "Average"
  } }] })
}
resource "aws_iam_role" "collector" {
  name               = "cloudguard-collector"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Federated = var.oidc_provider_arn }, Action = "sts:AssumeRoleWithWebIdentity", Condition = { StringEquals = { "${trimprefix(var.oidc_issuer, "https://")}:sub" = "system:serviceaccount:default:cloudguard-collector", "${trimprefix(var.oidc_issuer, "https://")}:aud" = "sts.amazonaws.com" } } }] })
}
resource "aws_iam_role_policy" "collector" {
  role = aws_iam_role.collector.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["cloudwatch:PutMetricData"], Resource = "*", Condition = { StringEquals = { "cloudwatch:namespace" = "CloudGuard" } } }
  ] })
}
output "repository_url" { value = aws_ecr_repository.service.repository_url }
output "collector_role_arn" { value = aws_iam_role.collector.arn }
output "alarm_topic_arn" { value = aws_sns_topic.alerts.arn }
