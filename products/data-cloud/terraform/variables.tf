# ---------------------------------------------------------------------------
# Global
# ---------------------------------------------------------------------------
variable "aws_region" {
  description = "Primary AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

variable "replica_region" {
  description = "Secondary AWS region for S3 cross-region replication."
  type        = string
  default     = "us-west-2"
}

variable "environment" {
  description = "Deployment environment: staging | production."
  type        = string
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be 'staging' or 'production'."
  }
}

variable "owner" {
  description = "Team or person owning this deployment (used for tagging)."
  type        = string
  default     = "platform-team"
}

# ---------------------------------------------------------------------------
# VPC
# ---------------------------------------------------------------------------
variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of AZs (≥3 for HA)."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (one per AZ). Hosts EKS nodes, MSK, ElastiCache."
  type        = list(string)
  default     = ["10.0.0.0/19", "10.0.32.0/19", "10.0.64.0/19"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (one per AZ). Hosts NAT gateways and load balancers."
  type        = list(string)
  default     = ["10.0.96.0/22", "10.0.100.0/22", "10.0.104.0/22"]
}

variable "database_subnet_cidrs" {
  description = "CIDR blocks for isolated database subnets (one per AZ). Hosts RDS and ClickHouse."
  type        = list(string)
  default     = ["10.0.108.0/22", "10.0.112.0/22", "10.0.116.0/22"]
}

variable "enable_nat_gateway" {
  description = "Whether to create NAT gateways in every AZ (set false for cost savings in non-prod)."
  type        = bool
  default     = true
}

variable "single_nat_gateway" {
  description = "Use a single shared NAT gateway instead of one per AZ (reduces cost in staging)."
  type        = bool
  default     = false
}

# ---------------------------------------------------------------------------
# EKS
# ---------------------------------------------------------------------------
variable "cluster_name" {
  description = "EKS cluster name."
  type        = string
  default     = "ghatana-data-cloud"
}

variable "kubernetes_version" {
  description = "Kubernetes version for the EKS cluster."
  type        = string
  default     = "1.29"
}

variable "node_groups" {
  description = "Map of EKS managed node group configurations."
  type = map(object({
    instance_types = list(string)
    min_size       = number
    max_size       = number
    desired_size   = number
    disk_size_gb   = number
    labels         = map(string)
    taints         = list(object({ key = string, value = string, effect = string }))
  }))
  default = {
    general = {
      instance_types = ["m6i.xlarge"]
      min_size       = 2
      max_size       = 10
      desired_size   = 3
      disk_size_gb   = 50
      labels         = { role = "general" }
      taints         = []
    }
    memory_optimized = {
      instance_types = ["r6i.2xlarge"]
      min_size       = 0
      max_size       = 5
      desired_size   = 0
      disk_size_gb   = 100
      labels         = { role = "memory-intensive" }
      taints = [{
        key    = "dedicated"
        value  = "memory"
        effect = "NO_SCHEDULE"
      }]
    }
  }
}

variable "cluster_log_types" {
  description = "EKS control-plane log types to enable."
  type        = list(string)
  default     = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
}

variable "cluster_addons" {
  description = "EKS managed addons to install."
  type        = map(string)
  default = {
    vpc-cni            = "v1.16.4-eksbuild.2"
    coredns            = "v1.11.1-eksbuild.4"
    kube-proxy         = "v1.29.0-eksbuild.1"
    aws-ebs-csi-driver = "v1.28.0-eksbuild.1"
  }
}

# ---------------------------------------------------------------------------
# RDS PostgreSQL
# ---------------------------------------------------------------------------
variable "postgres_instance_class" {
  description = "RDS instance class for PostgreSQL."
  type        = string
  default     = "db.t4g.medium"
}

variable "postgres_engine_version" {
  description = "PostgreSQL engine version."
  type        = string
  default     = "16.2"
}

variable "postgres_allocated_storage_gb" {
  description = "Initial allocated storage in GiB."
  type        = number
  default     = 100
}

variable "postgres_max_allocated_storage_gb" {
  description = "Maximum storage autoscaling ceiling in GiB."
  type        = number
  default     = 1000
}

variable "postgres_multi_az" {
  description = "Enable Multi-AZ standby for RDS."
  type        = bool
  default     = true
}

variable "postgres_backup_retention_days" {
  description = "Number of days to retain automated RDS backups."
  type        = number
  default     = 14
}

variable "postgres_deletion_protection" {
  description = "Prevent accidental deletion of the RDS instance."
  type        = bool
  default     = true
}

variable "postgres_db_name" {
  description = "Initial database name."
  type        = string
  default     = "datacloud"
}

# ---------------------------------------------------------------------------
# MSK (Kafka)
# ---------------------------------------------------------------------------
variable "msk_kafka_version" {
  description = "Apache Kafka version for MSK."
  type        = string
  default     = "3.6.0"
}

variable "msk_broker_instance_type" {
  description = "MSK broker instance type."
  type        = string
  default     = "kafka.m5.large"
}

variable "msk_broker_count" {
  description = "Number of MSK broker nodes (must be a multiple of AZ count)."
  type        = number
  default     = 3
}

variable "msk_broker_storage_gb" {
  description = "Storage per MSK broker in GiB."
  type        = number
  default     = 200
}

variable "msk_sasl_scram_enabled" {
  description = "Enable SASL/SCRAM authentication for MSK."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# ElastiCache Redis
# ---------------------------------------------------------------------------
variable "redis_node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.small"
}

variable "redis_engine_version" {
  description = "Redis engine version."
  type        = string
  default     = "7.1"
}

variable "redis_num_cache_nodes" {
  description = "Number of nodes in the Redis cluster (1 for single, ≥2 for cluster mode)."
  type        = number
  default     = 2
}

variable "redis_automatic_failover" {
  description = "Enable automatic failover (requires num_cache_nodes ≥2)."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# OpenSearch
# ---------------------------------------------------------------------------
variable "opensearch_engine_version" {
  description = "OpenSearch engine version."
  type        = string
  default     = "OpenSearch_2.11"
}

variable "opensearch_instance_type" {
  description = "OpenSearch data node instance type."
  type        = string
  default     = "m6g.large.search"
}

variable "opensearch_instance_count" {
  description = "Number of OpenSearch data nodes."
  type        = number
  default     = 3
}

variable "opensearch_volume_size_gb" {
  description = "EBS volume size per OpenSearch node in GiB."
  type        = number
  default     = 100
}

variable "opensearch_dedicated_master_enabled" {
  description = "Enable dedicated master nodes."
  type        = bool
  default     = true
}

variable "opensearch_dedicated_master_type" {
  description = "Instance type for dedicated OpenSearch master nodes."
  type        = string
  default     = "m6g.large.search"
}

variable "opensearch_dedicated_master_count" {
  description = "Number of dedicated master nodes (3 or 5)."
  type        = number
  default     = 3
}

# ---------------------------------------------------------------------------
# S3
# ---------------------------------------------------------------------------
variable "s3_blob_bucket_name_prefix" {
  description = "Prefix for the blob-storage S3 bucket name (suffixed with environment + account ID)."
  type        = string
  default     = "ghatana-data-cloud-blobs"
}

variable "s3_backup_bucket_name_prefix" {
  description = "Prefix for the backup S3 bucket name."
  type        = string
  default     = "ghatana-data-cloud-backups"
}

variable "s3_enable_cross_region_replication" {
  description = "Enable S3 cross-region replication for disaster recovery."
  type        = bool
  default     = true
}

variable "s3_blob_lifecycle_transition_days" {
  description = "Days before transitioning blob objects to S3-IA."
  type        = number
  default     = 30
}

variable "s3_blob_glacier_days" {
  description = "Days before transitioning blob objects to Glacier."
  type        = number
  default     = 90
}

# ---------------------------------------------------------------------------
# ClickHouse (self-managed on EC2)
# ---------------------------------------------------------------------------
variable "clickhouse_instance_type" {
  description = "EC2 instance type for ClickHouse nodes."
  type        = string
  default     = "r6i.2xlarge"
}

variable "clickhouse_node_count" {
  description = "Number of ClickHouse nodes. Use 1 for staging, ≥2 for production (manual sharding)."
  type        = number
  default     = 2
}

variable "clickhouse_volume_size_gb" {
  description = "EBS gp3 volume size per ClickHouse node in GiB."
  type        = number
  default     = 500
}

variable "clickhouse_volume_iops" {
  description = "Provisioned IOPS for ClickHouse EBS gp3 volumes."
  type        = number
  default     = 3000
}

variable "clickhouse_version" {
  description = "ClickHouse server version to install via apt."
  type        = string
  default     = "24.3"
}

# ---------------------------------------------------------------------------
# Secrets (Vault path prefixes — consumed by ESO SecretStore)
# ---------------------------------------------------------------------------
variable "vault_address" {
  description = "HashiCorp Vault address (used by ESO SecretStore and Terraform Vault provider)."
  type        = string
  default     = "https://vault.internal.ghatana.io"
}

variable "vault_kv_path" {
  description = "Vault KV v2 mount path prefix for data-cloud secrets."
  type        = string
  default     = "data-cloud"
}

# ---------------------------------------------------------------------------
# Cross-Region Replication (secondary region resources — supplied by CI/CD
# after the DR environment is applied; safe defaults keep primary-only apply
# from failing when DR has not yet been provisioned)
# ---------------------------------------------------------------------------

variable "cross_region_secondary_msk_arn" {
  description = "ARN of the MSK cluster in the replica region (DR environment)."
  type        = string
  default     = ""
}

variable "cross_region_msk_topics" {
  description = "List of Kafka topic regex patterns to replicate to DR."
  type        = list(string)
  default     = ["datacloud\\..*"]
}

variable "cross_region_msk_consumer_groups" {
  description = "List of consumer group ID regex patterns to sync offsets for."
  type        = list(string)
  default     = ["data-cloud-.*"]
}

variable "cross_region_secondary_msk_subnet_ids" {
  description = "List of subnet IDs in the replica region for MSK replicator networking."
  type        = list(string)
  default     = []
}

variable "cross_region_secondary_msk_security_group_ids" {
  description = "Security group IDs in the replica region used by the MSK replicator."
  type        = list(string)
  default     = []
}

variable "cross_region_clickhouse_secondary_hosts" {
  description = "Private IP addresses of ClickHouse nodes in the DR region."
  type        = list(string)
  default     = []
}

variable "cross_region_secondary_alb_dns_name" {
  description = "DNS name of the DR region Application Load Balancer."
  type        = string
  default     = ""
}

variable "cross_region_secondary_alb_zone_id" {
  description = "Hosted zone ID of the DR region Application Load Balancer."
  type        = string
  default     = ""
}

# ---------------------------------------------------------------------------
# Global Load Balancer / Route53
# ---------------------------------------------------------------------------

variable "route53_zone_id" {
  description = "Route53 hosted zone ID. When empty the global-lb module is skipped."
  type        = string
  default     = ""
}

variable "api_fqdn" {
  description = "Fully-qualified API domain name (e.g. api.data-cloud.ghatana.io)."
  type        = string
  default     = "api.data-cloud.ghatana.io"
}

variable "load_balancer_topology" {
  description = "Multi-region routing topology: 'active-passive' (failover) or 'active-active' (latency-based)."
  type        = string
  default     = "active-passive"

  validation {
    condition     = contains(["active-passive", "active-active"], var.load_balancer_topology)
    error_message = "load_balancer_topology must be 'active-passive' or 'active-active'."
  }
}

variable "enable_global_accelerator" {
  description = "Deploy AWS Global Accelerator for static anycast IP routing."
  type        = bool
  default     = false
}
