# =============================================================================
# Global Load Balancer Module
# =============================================================================
#
# Provides multi-region traffic routing for Data-Cloud using two mechanisms:
#
#   1. AWS Global Accelerator — anycast static IPs, TCP-layer failover < 30 s,
#      works independently of DNS TTL. Recommended for latency-sensitive paths.
#
#   2. Route 53 latency-based routing with health checks — DNS-based active-active
#      read distribution; falls back automatically to the surviving region when
#      health checks fail. Recommended for REST API consumers.
#
# Topology supported:
#   • Active-Active:  Both regions serve live traffic; Route53 routes by latency.
#   • Active-Passive: Primary region serves all traffic; passive region receives
#                     traffic only when primary health check fails.
#
# Usage:
#
#   module "global_lb" {
#     source = "./modules/global-load-balancer"
#
#     name_prefix      = "ghatana-data-cloud-production"
#     primary_region   = "us-east-1"
#     secondary_region = "us-west-2"
#     topology         = "active-passive"        # or "active-active"
#
#     primary_alb_dns_name   = "<primary-ALB-DNS>"
#     primary_alb_zone_id    = "<primary-ALB-hosted-zone-id>"
#     secondary_alb_dns_name = "<secondary-ALB-DNS>"
#     secondary_alb_zone_id  = "<secondary-ALB-hosted-zone-id>"
#
#     route53_zone_id = aws_route53_zone.public.zone_id
#     api_fqdn        = "api.data-cloud.ghatana.io"
#
#     tags = local.common_tags
#   }

# ---------------------------------------------------------------------------
# Provider aliases
# ---------------------------------------------------------------------------
terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 5.40"
      configuration_aliases = [aws.primary, aws.secondary]
    }
  }
}

# ---------------------------------------------------------------------------
# Variables
# ---------------------------------------------------------------------------

variable "name_prefix" {
  description = "Common resource name prefix."
  type        = string
}

variable "primary_region" {
  description = "Primary AWS region (e.g. us-east-1)."
  type        = string
}

variable "secondary_region" {
  description = "Secondary AWS region for DR / read-scale (e.g. us-west-2)."
  type        = string
}

variable "topology" {
  description = "Routing topology: 'active-active' (latency-based) or 'active-passive' (failover)."
  type        = string
  default     = "active-passive"
  validation {
    condition     = contains(["active-active", "active-passive"], var.topology)
    error_message = "topology must be 'active-active' or 'active-passive'."
  }
}

variable "primary_alb_dns_name" {
  description = "DNS name of the Application Load Balancer in the primary region."
  type        = string
}

variable "primary_alb_zone_id" {
  description = "Hosted zone ID of the primary ALB (needed for Route 53 alias records)."
  type        = string
}

variable "secondary_alb_dns_name" {
  description = "DNS name of the Application Load Balancer in the secondary region."
  type        = string
}

variable "secondary_alb_zone_id" {
  description = "Hosted zone ID of the secondary ALB."
  type        = string
}

variable "route53_zone_id" {
  description = "Route 53 public hosted zone ID where DNS records will be created."
  type        = string
}

variable "api_fqdn" {
  description = "Fully-qualified domain name for the Data-Cloud API (e.g. api.data-cloud.ghatana.io)."
  type        = string
}

variable "health_check_path" {
  description = "HTTP path used for Route53 and Global Accelerator health checks."
  type        = string
  default     = "/health"
}

variable "health_check_port" {
  description = "Port used for health checks."
  type        = number
  default     = 8082
}

variable "enable_global_accelerator" {
  description = "Create an AWS Global Accelerator in addition to Route53 routing."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}

# ---------------------------------------------------------------------------
# Route 53 Health Checks
#
# One health check per region.  Route53 uses these to decide whether to include
# a region's record in DNS responses (latency-based) or to trigger failover.
# ---------------------------------------------------------------------------
resource "aws_route53_health_check" "primary" {
  fqdn              = var.primary_alb_dns_name
  port              = var.health_check_port
  type              = "HTTP"
  resource_path     = var.health_check_path
  failure_threshold = 3
  request_interval  = 10  # seconds — "fast" health check for quicker failover

  tags = merge(var.tags, {
    Name   = "${var.name_prefix}-hc-primary"
    Region = var.primary_region
  })
}

resource "aws_route53_health_check" "secondary" {
  fqdn              = var.secondary_alb_dns_name
  port              = var.health_check_port
  type              = "HTTP"
  resource_path     = var.health_check_path
  failure_threshold = 3
  request_interval  = 10

  tags = merge(var.tags, {
    Name   = "${var.name_prefix}-hc-secondary"
    Region = var.secondary_region
  })
}

# ---------------------------------------------------------------------------
# Route 53 Records — active-passive (failover) topology
# ---------------------------------------------------------------------------
resource "aws_route53_record" "primary_failover" {
  count   = var.topology == "active-passive" ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.api_fqdn
  type    = "A"

  failover_routing_policy {
    type = "PRIMARY"
  }

  set_identifier  = "primary-${var.primary_region}"
  health_check_id = aws_route53_health_check.primary.id

  alias {
    name                   = var.primary_alb_dns_name
    zone_id                = var.primary_alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "secondary_failover" {
  count   = var.topology == "active-passive" ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.api_fqdn
  type    = "A"

  failover_routing_policy {
    type = "SECONDARY"
  }

  set_identifier  = "secondary-${var.secondary_region}"
  health_check_id = aws_route53_health_check.secondary.id

  alias {
    name                   = var.secondary_alb_dns_name
    zone_id                = var.secondary_alb_zone_id
    evaluate_target_health = true
  }
}

# ---------------------------------------------------------------------------
# Route 53 Records — active-active (latency-based) topology
# ---------------------------------------------------------------------------
resource "aws_route53_record" "primary_latency" {
  count   = var.topology == "active-active" ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.api_fqdn
  type    = "A"

  latency_routing_policy {
    region = var.primary_region
  }

  set_identifier  = "primary-${var.primary_region}"
  health_check_id = aws_route53_health_check.primary.id

  alias {
    name                   = var.primary_alb_dns_name
    zone_id                = var.primary_alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "secondary_latency" {
  count   = var.topology == "active-active" ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.api_fqdn
  type    = "A"

  latency_routing_policy {
    region = var.secondary_region
  }

  set_identifier  = "secondary-${var.secondary_region}"
  health_check_id = aws_route53_health_check.secondary.id

  alias {
    name                   = var.secondary_alb_dns_name
    zone_id                = var.secondary_alb_zone_id
    evaluate_target_health = true
  }
}

# ---------------------------------------------------------------------------
# AWS Global Accelerator (optional)
#
# Provides:
#   • Two static anycast IPs — no DNS TTL wait during failover
#   • TCP-layer health checks with 10-second failover (vs 60s+ for DNS)
#   • AWS backbone routing from edge locations to the nearest healthy ALB
# ---------------------------------------------------------------------------
resource "aws_globalaccelerator_accelerator" "main" {
  count   = var.enable_global_accelerator ? 1 : 0
  name    = "${var.name_prefix}-ga"
  ip_address_type = "IPV4"
  enabled = true

  attributes {
    flow_logs_enabled   = true
    flow_logs_s3_bucket = aws_s3_bucket.ga_flow_logs[0].bucket
    flow_logs_s3_prefix = "flow-logs/"
  }

  tags = var.tags
}

resource "aws_s3_bucket" "ga_flow_logs" {
  count  = var.enable_global_accelerator ? 1 : 0
  # Use primary provider for flow logs bucket (Global Accelerator is global)
  bucket = "${var.name_prefix}-ga-flow-logs"
  tags   = merge(var.tags, { Name = "${var.name_prefix}-ga-flow-logs" })
}

resource "aws_s3_bucket_public_access_block" "ga_flow_logs" {
  count                   = var.enable_global_accelerator ? 1 : 0
  bucket                  = aws_s3_bucket.ga_flow_logs[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_globalaccelerator_listener" "http_api" {
  count           = var.enable_global_accelerator ? 1 : 0
  accelerator_arn = aws_globalaccelerator_accelerator.main[0].id
  client_affinity = "SOURCE_IP"  # Sticky sessions per source IP

  port_range {
    from_port = var.health_check_port
    to_port   = var.health_check_port
  }
}

resource "aws_globalaccelerator_endpoint_group" "primary" {
  count                         = var.enable_global_accelerator ? 1 : 0
  listener_arn                  = aws_globalaccelerator_listener.http_api[0].id
  endpoint_group_region         = var.primary_region
  traffic_dial_percentage       = var.topology == "active-passive" ? 100 : 50
  health_check_path             = var.health_check_path
  health_check_port             = var.health_check_port
  health_check_protocol         = "HTTP"
  health_check_interval_seconds = 10
  threshold_count               = 3

  endpoint_configuration {
    endpoint_id                    = var.primary_alb_dns_name
    weight                         = 128
    client_ip_preservation_enabled = true
  }
}

resource "aws_globalaccelerator_endpoint_group" "secondary" {
  count                         = var.enable_global_accelerator ? 1 : 0
  listener_arn                  = aws_globalaccelerator_listener.http_api[0].id
  endpoint_group_region         = var.secondary_region
  # In active-passive, dial percentage is 0; traffic only flows here on failover.
  traffic_dial_percentage       = var.topology == "active-passive" ? 0 : 50
  health_check_path             = var.health_check_path
  health_check_port             = var.health_check_port
  health_check_protocol         = "HTTP"
  health_check_interval_seconds = 10
  threshold_count               = 3

  endpoint_configuration {
    endpoint_id                    = var.secondary_alb_dns_name
    weight                         = 128
    client_ip_preservation_enabled = true
  }
}

# ---------------------------------------------------------------------------
# CloudWatch Alarm — alert when both regions are unhealthy
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_metric_alarm" "all_regions_unhealthy" {
  alarm_name          = "${var.name_prefix}-all-regions-unhealthy"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  threshold           = 1
  alarm_description   = "CRITICAL: Data-Cloud API is unreachable in ALL regions — immediate on-call response required."
  treat_missing_data  = "breaching"

  # Alarm when the sum of healthy health checks drops below 1 (both regions down).
  metric_query {
    id          = "primary_hc"
    return_data = false
    metric {
      metric_name = "HealthCheckStatus"
      namespace   = "AWS/Route53"
      period      = 60
      stat        = "Minimum"
      dimensions  = { HealthCheckId = aws_route53_health_check.primary.id }
    }
  }

  metric_query {
    id          = "secondary_hc"
    return_data = false
    metric {
      metric_name = "HealthCheckStatus"
      namespace   = "AWS/Route53"
      period      = 60
      stat        = "Minimum"
      dimensions  = { HealthCheckId = aws_route53_health_check.secondary.id }
    }
  }

  metric_query {
    id          = "total_healthy"
    expression  = "primary_hc + secondary_hc"
    label       = "TotalHealthyRegions"
    return_data = true
  }

  tags = var.tags
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "primary_health_check_id" {
  description = "Route53 health check ID for the primary region."
  value       = aws_route53_health_check.primary.id
}

output "secondary_health_check_id" {
  description = "Route53 health check ID for the secondary region."
  value       = aws_route53_health_check.secondary.id
}

output "global_accelerator_dns_name" {
  description = "DNS name of the Global Accelerator (null when disabled)."
  value       = var.enable_global_accelerator ? aws_globalaccelerator_accelerator.main[0].dns_name : null
}

output "global_accelerator_ips" {
  description = "Static anycast IPs of the Global Accelerator (null when disabled)."
  value       = var.enable_global_accelerator ? aws_globalaccelerator_accelerator.main[0].ip_sets[0].ip_addresses : null
}

output "api_fqdn" {
  description = "The fully-qualified domain name clients should use."
  value       = var.api_fqdn
}
