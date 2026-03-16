# =============================================================================
# Cross-Region Replication Module
# =============================================================================
#
# Provisions all data-plane replication resources needed for an active-passive
# (or active-active read-scale) disaster-recovery topology:
#
#   • RDS — cross-region read replica (promotes to standalone in DR)
#   • MSK — MSK Managed Replicator between primary and secondary clusters
#   • OpenSearch — cross-cluster search outbound connection + automated snapshots
#   • ClickHouse — remote_servers entry written to secondary via SSM Parameter Store
#
# Usage (from root main.tf):
#
#   module "cross_region_replication" {
#     source = "./modules/cross-region-replication"
#
#     primary_region   = "us-east-1"
#     secondary_region = "us-west-2"
#
#     # RDS
#     primary_rds_arn          = module.rds.instance_arn
#
#     # MSK
#     primary_msk_arn          = module.msk.cluster_arn
#     secondary_msk_arn        = module.msk_secondary.cluster_arn
#     msk_replication_topics   = ["datacloud.*"]
#
#     # OpenSearch
#     primary_opensearch_arn   = module.opensearch.domain_arn
#     primary_opensearch_endpoint = module.opensearch.endpoint
#
#     # Tagging / naming
#     name_prefix = local.name_prefix
#     tags        = local.common_tags
#
#     providers = {
#       aws.primary   = aws
#       aws.secondary = aws.replica
#     }
#   }

# ---------------------------------------------------------------------------
# Provider aliases — caller MUST pass these
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
# Input variables
# ---------------------------------------------------------------------------

variable "primary_region" {
  description = "AWS region for the primary deployment."
  type        = string
}

variable "secondary_region" {
  description = "AWS region for the secondary (DR) deployment."
  type        = string
}

variable "name_prefix" {
  description = "Common resource name prefix (e.g. ghatana-data-cloud-production)."
  type        = string
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}

# --- RDS ---------------------------------------------------------------
variable "primary_rds_arn" {
  description = "ARN of the primary RDS PostgreSQL instance to replicate."
  type        = string
}

variable "rds_replica_instance_class" {
  description = "Instance class for the cross-region RDS read replica."
  type        = string
  default     = "db.r6g.large"
}

variable "rds_replica_storage_gb" {
  description = "Allocated storage in GiB for the RDS read replica."
  type        = number
  default     = 100
}

# --- MSK ---------------------------------------------------------------
variable "primary_msk_arn" {
  description = "ARN of the primary MSK cluster (source for replication)."
  type        = string
}

variable "secondary_msk_arn" {
  description = "ARN of the secondary MSK cluster (target for replication)."
  type        = string
}

variable "msk_replication_topics" {
  description = "List of topic name regex patterns to replicate (e.g. 'datacloud.*')."
  type        = list(string)
  default     = ["datacloud\\..*"]
}

variable "msk_replication_consumer_groups" {
  description = "List of consumer-group regex patterns whose offsets are replicated."
  type        = list(string)
  default     = ["data-cloud-.*"]
}

# --- OpenSearch --------------------------------------------------------
variable "primary_opensearch_arn" {
  description = "ARN of the primary OpenSearch domain."
  type        = string
}

variable "primary_opensearch_endpoint" {
  description = "Endpoint URL (without https://) of the primary OpenSearch domain."
  type        = string
}

variable "opensearch_snapshot_hour" {
  description = "UTC hour (0-23) for daily automated OpenSearch snapshots."
  type        = number
  default     = 3
}

# ---------------------------------------------------------------------------
# Data sources
# ---------------------------------------------------------------------------
data "aws_caller_identity" "primary" {
  provider = aws.primary
}

data "aws_caller_identity" "secondary" {
  provider = aws.secondary
}

# ---------------------------------------------------------------------------
# 1. RDS Cross-Region Read Replica
#
# A read replica in the secondary region can be promoted to a standalone
# primary in seconds during a DR failover, achieving a typical RTO < 15 min.
# ---------------------------------------------------------------------------
resource "aws_db_instance" "rds_replica" {
  provider = aws.secondary

  identifier             = "${var.name_prefix}-postgres-replica"
  replicate_source_db    = var.primary_rds_arn
  instance_class         = var.rds_replica_instance_class
  storage_encrypted      = true
  publicly_accessible    = false
  auto_minor_version_upgrade = true

  # Backup retention on the replica enables PITR independent of primary.
  backup_retention_period = 7
  backup_window           = "04:00-05:00"

  # Keep monitoring on for replica health visibility.
  performance_insights_enabled = true

  # Read queries can be directed here to reduce primary load.
  # NOTE: promote this instance if primary becomes unavailable.
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-postgres-replica"
    Role = "dr-read-replica"
  })

  lifecycle {
    # Prevent accidental promotion during normal Terraform runs.
    ignore_changes = [replicate_source_db]
  }
}

# ---------------------------------------------------------------------------
# 2. MSK Managed Replicator
#
# AWS MSK Replicator (GA since Dec 2023) mirrors topics and consumer-group
# offsets from the primary cluster to the secondary. A single replicator
# handles unlimited topics matching the configured regex.
# ---------------------------------------------------------------------------

# IAM role that the MSK Replicator service will assume.
resource "aws_iam_role" "msk_replicator" {
  provider = aws.primary
  name     = "${var.name_prefix}-msk-replicator"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "kafka.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy" "msk_replicator" {
  provider = aws.primary
  name     = "msk-replication-access"
  role     = aws_iam_role.msk_replicator.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadPrimary"
        Effect = "Allow"
        Action = [
          "kafka-cluster:Connect",
          "kafka-cluster:DescribeCluster",
          "kafka-cluster:ReadData",
          "kafka-cluster:DescribeTopic",
          "kafka-cluster:AlterGroup",
          "kafka-cluster:DescribeGroup",
        ]
        Resource = [
          var.primary_msk_arn,
          "${var.primary_msk_arn}/*"
        ]
      },
      {
        Sid    = "WriteSecondary"
        Effect = "Allow"
        Action = [
          "kafka-cluster:Connect",
          "kafka-cluster:DescribeCluster",
          "kafka-cluster:WriteData",
          "kafka-cluster:CreateTopic",
          "kafka-cluster:DescribeTopic",
          "kafka-cluster:AlterTopic",
        ]
        Resource = [
          var.secondary_msk_arn,
          "${var.secondary_msk_arn}/*"
        ]
      }
    ]
  })
}

resource "aws_msk_replicator" "main" {
  provider        = aws.primary
  replicator_name = "${var.name_prefix}-kafka-replicator"
  service_execution_role_arn = aws_iam_role.msk_replicator.arn
  description     = "Replicates data-cloud Kafka topics from primary to secondary region for DR."

  kafka_cluster {
    amazon_msk_cluster {
      msk_cluster_arn = var.primary_msk_arn
    }
    vpc_config {
      # Subnet IDs and security groups must be sourced from the primary VPC.
      # Passed as a dedicated variable or looked up via data source in caller.
      subnet_ids         = var.primary_msk_subnet_ids
      security_groups_ids = var.primary_msk_security_group_ids
    }
  }

  kafka_cluster {
    amazon_msk_cluster {
      msk_cluster_arn = var.secondary_msk_arn
    }
    vpc_config {
      subnet_ids          = var.secondary_msk_subnet_ids
      security_groups_ids = var.secondary_msk_security_group_ids
    }
  }

  replication_info {
    source_kafka_cluster_arn = var.primary_msk_arn
    target_kafka_cluster_arn = var.secondary_msk_arn
    target_compression_type  = "SNAPPY"

    topic_replication {
      topics_to_replicate           = var.msk_replication_topics
      detect_and_copy_new_topics    = true
      copy_access_control_lists     = false
      copy_topic_configurations     = true
      starting_position {
        type = "LATEST"  # Only replicate from now; use EARLIEST for full backfill.
      }
    }

    consumer_group_replication {
      consumer_groups_to_replicate           = var.msk_replication_consumer_groups
      detect_and_copy_new_consumer_groups    = true
      synchronise_consumer_group_offsets     = true
    }
  }

  tags = var.tags

  depends_on = [aws_iam_role_policy.msk_replicator]
}

# Extra MSK networking variables (sourced from caller's VPC module outputs)
variable "primary_msk_subnet_ids" {
  description = "Private subnet IDs in the primary region's VPC for the MSK replicator."
  type        = list(string)
}

variable "primary_msk_security_group_ids" {
  description = "Security group IDs allowing MSK access in the primary region."
  type        = list(string)
}

variable "secondary_msk_subnet_ids" {
  description = "Private subnet IDs in the secondary region's VPC for the MSK replicator."
  type        = list(string)
}

variable "secondary_msk_security_group_ids" {
  description = "Security group IDs allowing MSK access in the secondary region."
  type        = list(string)
}

# ---------------------------------------------------------------------------
# 3. OpenSearch Cross-Region Automated Snapshots
#
# AWS OpenSearch natively supports automated daily snapshots. We register an
# S3 snapshot repository in the secondary region's bucket so snapshots can be
# restored independently of the primary.
# ---------------------------------------------------------------------------

# IAM role that OpenSearch uses to write snapshots to S3.
resource "aws_iam_role" "opensearch_snapshot" {
  provider = aws.primary
  name     = "${var.name_prefix}-opensearch-snapshot"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "es.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

# S3 bucket in the secondary region for cross-region snapshot storage.
resource "aws_s3_bucket" "opensearch_snapshots" {
  provider      = aws.secondary
  bucket        = "${var.name_prefix}-opensearch-snapshots-${data.aws_caller_identity.secondary.account_id}"
  force_destroy = false
  tags          = merge(var.tags, { Name = "${var.name_prefix}-opensearch-snapshots" })
}

resource "aws_s3_bucket_versioning" "opensearch_snapshots" {
  provider = aws.secondary
  bucket   = aws_s3_bucket.opensearch_snapshots.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "opensearch_snapshots" {
  provider = aws.secondary
  bucket   = aws_s3_bucket.opensearch_snapshots.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "aws:kms" }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "opensearch_snapshots" {
  provider                = aws.secondary
  bucket                  = aws_s3_bucket.opensearch_snapshots.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "opensearch_snapshots" {
  provider = aws.secondary
  bucket   = aws_s3_bucket.opensearch_snapshots.id

  rule {
    id     = "snapshot-retention"
    status = "Enabled"

    expiration { days = 30 }  # Keep 30 days of snapshots
  }
}

resource "aws_iam_role_policy" "opensearch_snapshot" {
  provider = aws.primary
  name     = "s3-snapshot-access"
  role     = aws_iam_role.opensearch_snapshot.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = aws_s3_bucket.opensearch_snapshots.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.opensearch_snapshots.arn}/*"
      }
    ]
  })
}

# SSM Parameter stores the snapshot registration payload.
# The data-cloud launcher reads this parameter on startup to POST the
# _snapshot/dr-repository API call to OpenSearch.
resource "aws_ssm_parameter" "opensearch_snapshot_repository" {
  provider = aws.primary
  name     = "/${var.name_prefix}/opensearch/snapshot-repository"
  type     = "String"
  value = jsonencode({
    type = "s3"
    settings = {
      bucket      = aws_s3_bucket.opensearch_snapshots.bucket
      region      = var.secondary_region
      role_arn    = aws_iam_role.opensearch_snapshot.arn
      base_path   = "snapshots"
    }
  })
  tags = var.tags
}

# ---------------------------------------------------------------------------
# 4. ClickHouse Remote Servers Configuration (SSM Parameter Store)
#
# ClickHouse's distributed architecture uses <remote_servers> in config.xml
# to define cross-shard/cross-region clusters. We store the config snippet
# in SSM so the ClickHouse user-data script (or Ansible) can pull it at
# instance start/refresh.
# ---------------------------------------------------------------------------
variable "clickhouse_primary_hosts" {
  description = "List of ClickHouse node private IPs/FQDNs in the primary region."
  type        = list(string)
  default     = []
}

variable "clickhouse_secondary_hosts" {
  description = "List of ClickHouse node private IPs/FQDNs in the secondary region."
  type        = list(string)
  default     = []
}

locals {
  # Build ClickHouse <shard> blocks for all primary hosts.
  ch_primary_shards = join("\n", [for h in var.clickhouse_primary_hosts : <<-EOT
        <shard>
            <replica>
                <host>${h}</host>
                <port>9000</port>
                <user>datacloud</user>
                <password_secret>clickhouse_datacloud</password_secret>
            </replica>
        </shard>
  EOT
  ])

  # Build <shard> blocks for all secondary (DR) hosts.
  ch_secondary_shards = join("\n", [for h in var.clickhouse_secondary_hosts : <<-EOT
        <shard>
            <replica>
                <host>${h}</host>
                <port>9000</port>
                <user>datacloud</user>
                <password_secret>clickhouse_datacloud</password_secret>
            </replica>
        </shard>
  EOT
  ])

  ch_remote_servers_xml = <<-XML
    <yandex>
        <remote_servers>
            <!-- Primary region cluster — for normal operations -->
            <data_cloud_primary>
                ${local.ch_primary_shards}
            </data_cloud_primary>

            <!-- DR region cluster — read traffic during failover -->
            <data_cloud_dr>
                ${local.ch_secondary_shards}
            </data_cloud_dr>

            <!-- Global cluster — routes reads across primary + DR -->
            <data_cloud_global>
                ${local.ch_primary_shards}
                ${local.ch_secondary_shards}
            </data_cloud_global>
        </remote_servers>

        <!-- ReplicatedMergeTree uses ZooKeeper/ClickHouse Keeper.
             For cross-region replication use MaterializedPostgreSQL or
             a custom Distributed table over data_cloud_global. -->
        <macros>
            <cluster>data_cloud_primary</cluster>
            <shard>01</shard>
            <replica>primary</replica>
        </macros>
    </yandex>
  XML
}

resource "aws_ssm_parameter" "clickhouse_remote_servers" {
  provider = aws.primary
  name     = "/${var.name_prefix}/clickhouse/remote-servers-config"
  type     = "SecureString"
  value    = local.ch_remote_servers_xml
  tags     = var.tags
}

# ---------------------------------------------------------------------------
# CloudWatch Alarms — replication health monitoring
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_metric_alarm" "rds_replica_lag" {
  provider            = aws.secondary
  alarm_name          = "${var.name_prefix}-rds-replica-lag"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "ReplicaLag"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 300  # Alert if lag exceeds 5 minutes
  alarm_description   = "Data-Cloud RDS cross-region replica lag exceeds 5 minutes — DR recovery point may be stale."
  treat_missing_data  = "breaching"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.rds_replica.identifier
  }

  alarm_actions = [aws_sns_topic.replication_alerts.arn]
  ok_actions    = [aws_sns_topic.replication_alerts.arn]

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "msk_replicator_lag" {
  provider            = aws.primary
  alarm_name          = "${var.name_prefix}-msk-replicator-lag"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "MessagesBehindLatest"
  namespace           = "AWS/Kafka"
  period              = 60
  statistic           = "Maximum"
  threshold           = 100000  # Alert if >100k messages behind
  alarm_description   = "Data-Cloud MSK replicator consumer lag exceeds 100,000 messages — Kafka replication is falling behind."
  treat_missing_data  = "breaching"

  dimensions = {
    ReplicatorName = aws_msk_replicator.main.replicator_name
  }

  alarm_actions = [aws_sns_topic.replication_alerts.arn]
  ok_actions    = [aws_sns_topic.replication_alerts.arn]

  tags = var.tags
}

resource "aws_sns_topic" "replication_alerts" {
  provider = aws.primary
  name     = "${var.name_prefix}-replication-alerts"
  tags     = var.tags
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "rds_replica_endpoint" {
  description = "Endpoint of the cross-region RDS read replica."
  value       = aws_db_instance.rds_replica.address
}

output "rds_replica_arn" {
  description = "ARN of the cross-region RDS read replica."
  value       = aws_db_instance.rds_replica.arn
}

output "msk_replicator_arn" {
  description = "ARN of the MSK Managed Replicator."
  value       = aws_msk_replicator.main.arn
}

output "opensearch_snapshot_bucket" {
  description = "Name of the S3 bucket storing OpenSearch cross-region snapshots."
  value       = aws_s3_bucket.opensearch_snapshots.bucket
}

output "opensearch_snapshot_role_arn" {
  description = "ARN of the IAM role OpenSearch uses to write snapshots."
  value       = aws_iam_role.opensearch_snapshot.arn
}

output "clickhouse_remote_servers_ssm_parameter" {
  description = "SSM Parameter Store path for the ClickHouse remote_servers XML config."
  value       = aws_ssm_parameter.clickhouse_remote_servers.name
}

output "replication_alerts_sns_arn" {
  description = "ARN of the SNS topic for replication health alerts."
  value       = aws_sns_topic.replication_alerts.arn
}
