# =============================================================================
# DR Environment — Secondary Region Infrastructure + Cross-Region Wiring
# =============================================================================
#
# This environment provisions:
#   1. A full secondary-region stack (VPC, EKS, RDS, MSK, ElastiCache,
#      OpenSearch, S3) in us-west-2 at reduced capacity.
#   2. Cross-region replication (RDS replica, MSK Replicator, OpenSearch
#      snapshots, ClickHouse remote_servers config).
#   3. Global load balancer (Route53 health checks + failover records +
#      Global Accelerator) pointing to both regions.
#
# Topology: Active-Passive
#   • us-east-1 (primary) serves 100% of traffic under normal conditions.
#   • us-west-2 (DR) receives traffic only when the primary health check fails.
#   • RTO target: < 15 minutes (DNS propagation + EKS pod startup).
#   • RPO target: < 5 minutes (RDS replica lag + Kafka replicator lag).
#
# Prerequisites:
#   - The production environment (environments/production/) must already exist.
#   - The primary RDS, MSK and OpenSearch ARNs must be provided as variables.
#   - The Route53 hosted zone for the API FQDN must exist.
#   - AMI IDs are region-specific; update clickhouse_ami_id for us-west-2.
#
# Apply:
#   cd environments/dr
#   terraform init -backend-config=backend.tf
#   terraform plan -var-file=terraform.tfvars
#   terraform apply -var-file=terraform.tfvars

# ---------------------------------------------------------------------------
# Providers — secondary region is us-west-2
# ---------------------------------------------------------------------------
provider "aws" {
  alias  = "primary"
  region = "us-east-1"
}

provider "aws" {
  alias  = "secondary"
  region = var.secondary_region

  default_tags {
    tags = {
      Project     = "ghatana-data-cloud"
      Environment = "dr"
      ManagedBy   = "terraform"
      Owner       = "platform-team"
      PrimaryRegion = "us-east-1"
    }
  }
}

# Use secondary as the default provider so all resources land in us-west-2.
provider "aws" {
  region = var.secondary_region

  default_tags {
    tags = {
      Project     = "ghatana-data-cloud"
      Environment = "dr"
      ManagedBy   = "terraform"
      Owner       = "platform-team"
    }
  }
}

terraform {
  required_version = ">= 1.7.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

# ---------------------------------------------------------------------------
# Variables
# ---------------------------------------------------------------------------
variable "secondary_region" {
  description = "AWS region for the DR / secondary deployment."
  type        = string
  default     = "us-west-2"
}

variable "secondary_vpc_cidr" {
  description = "VPC CIDR for the secondary region. Must not overlap with primary (10.0.0.0/16)."
  type        = string
  default     = "10.1.0.0/16"
}

variable "secondary_availability_zones" {
  type    = list(string)
  default = ["us-west-2a", "us-west-2b", "us-west-2c"]
}

# --- Cross-region wiring (outputs from the production stack) ---------------
variable "primary_rds_arn" {
  description = "ARN of the primary RDS instance (from environments/production outputs)."
  type        = string
}

variable "primary_msk_arn" {
  description = "ARN of the primary MSK cluster."
  type        = string
}

variable "primary_opensearch_arn" {
  description = "ARN of the primary OpenSearch domain."
  type        = string
}

variable "primary_opensearch_endpoint" {
  description = "Endpoint of the primary OpenSearch domain."
  type        = string
}

variable "primary_alb_dns_name" {
  description = "DNS name of the primary region ALB."
  type        = string
}

variable "primary_alb_zone_id" {
  description = "Hosted zone ID of the primary ALB."
  type        = string
}

# --- Route53 / Global routing -----------------------------------------------
variable "route53_zone_id" {
  description = "Route53 public hosted zone ID for the API FQDN."
  type        = string
}

variable "api_fqdn" {
  description = "API FQDN (e.g. api.data-cloud.ghatana.io)."
  type        = string
  default     = "api.data-cloud.ghatana.io"
}

# --- DR-region ClickHouse AMI -----------------------------------------------
variable "clickhouse_ami_id" {
  description = "Ubuntu 22.04 LTS AMI ID for us-west-2 (update each quarter)."
  type        = string
  default     = "ami-0735c191cf914754d"  # Ubuntu 22.04 LTS us-west-2 — update quarterly
}

variable "clickhouse_key_name" {
  description = "EC2 key pair name in us-west-2 for ClickHouse SSH access."
  type        = string
}

# ---------------------------------------------------------------------------
# Locals
# ---------------------------------------------------------------------------
locals {
  name_prefix = "ghatana-data-cloud-dr"

  common_tags = {
    Project       = "ghatana-data-cloud"
    Environment   = "dr"
    ManagedBy     = "terraform"
    Owner         = "platform-team"
    PrimaryRegion = "us-east-1"
  }

  # Private subnets — non-overlapping with primary (10.0.x.x)
  private_subnet_cidrs  = ["10.1.0.0/19",  "10.1.32.0/19",  "10.1.64.0/19"]
  public_subnet_cidrs   = ["10.1.96.0/22", "10.1.100.0/22", "10.1.104.0/22"]
  database_subnet_cidrs = ["10.1.108.0/22","10.1.112.0/22", "10.1.116.0/22"]
}

# ---------------------------------------------------------------------------
# Secondary Region Infrastructure (reduced DR capacity)
# ---------------------------------------------------------------------------

module "vpc_dr" {
  source = "../../modules/vpc"

  name                  = local.name_prefix
  vpc_cidr              = var.secondary_vpc_cidr
  availability_zones    = var.secondary_availability_zones
  private_subnet_cidrs  = local.private_subnet_cidrs
  public_subnet_cidrs   = local.public_subnet_cidrs
  database_subnet_cidrs = local.database_subnet_cidrs
  enable_nat_gateway    = true
  single_nat_gateway    = true   # Cost optimisation — single NAT in DR
  cluster_name          = "${local.name_prefix}-eks"
  tags                  = local.common_tags
}

module "eks_dr" {
  source = "../../modules/eks"

  cluster_name       = "${local.name_prefix}-eks"
  kubernetes_version = "1.29"
  vpc_id             = module.vpc_dr.vpc_id
  subnet_ids         = module.vpc_dr.private_subnet_ids
  cluster_log_types  = ["api", "audit", "authenticator"]
  cluster_addons = {
    vpc-cni            = "v1.16.4-eksbuild.2"
    coredns            = "v1.11.1-eksbuild.4"
    kube-proxy         = "v1.29.0-eksbuild.1"
    aws-ebs-csi-driver = "v1.28.0-eksbuild.1"
  }
  # DR runs at minimum capacity — scales up during failover
  node_groups = {
    general = {
      instance_types = ["m6i.large"]    # Smaller than production
      min_size       = 1
      max_size       = 10              # Can scale up to match prod during DR
      desired_size   = 2
      disk_size_gb   = 50
      labels         = { role = "general", environment = "dr" }
      taints         = []
    }
  }
  tags = local.common_tags

  depends_on = [module.vpc_dr]
}

module "msk_dr" {
  source = "../../modules/msk"

  cluster_name         = "${local.name_prefix}-kafka"
  kafka_version        = "3.6.0"
  broker_instance_type = "kafka.m5.large"
  broker_count         = 3
  broker_storage_gb    = 500
  sasl_scram_enabled   = true
  vpc_id               = module.vpc_dr.vpc_id
  subnet_ids           = module.vpc_dr.private_subnet_ids
  allowed_security_group = module.eks_dr.node_security_group_id
  tags                 = local.common_tags

  depends_on = [module.vpc_dr]
}

module "elasticache_dr" {
  source = "../../modules/elasticache"

  cluster_id             = "${local.name_prefix}-redis"
  engine_version         = "7.1"
  node_type              = "cache.t4g.small"
  num_cache_nodes        = 1  # Minimal in DR; scale up after failover
  automatic_failover     = false
  vpc_id                 = module.vpc_dr.vpc_id
  subnet_ids             = module.vpc_dr.private_subnet_ids
  allowed_security_group = module.eks_dr.node_security_group_id
  tags                   = local.common_tags

  depends_on = [module.vpc_dr]
}

module "opensearch_dr" {
  source = "../../modules/opensearch"

  domain_name                         = "${local.name_prefix}-search"
  engine_version                      = "OpenSearch_2.11"
  instance_type                       = "m6g.large.search"
  instance_count                      = 2   # Reduced in DR
  volume_size_gb                      = 150
  dedicated_master_enabled            = false  # Simplified in DR
  dedicated_master_type               = "m6g.large.search"
  dedicated_master_count              = 0
  vpc_id                              = module.vpc_dr.vpc_id
  subnet_ids                          = slice(module.vpc_dr.private_subnet_ids, 0, 2)
  allowed_security_group              = module.eks_dr.node_security_group_id
  tags                                = local.common_tags

  depends_on = [module.vpc_dr]
}

module "s3_dr" {
  source = "../../modules/s3"

  name_prefix                     = local.name_prefix
  region                          = var.secondary_region
  replica_region                  = "us-east-1"   # Reversed: secondary replicates back to primary
  vpc_id                          = module.vpc_dr.vpc_id
  eks_node_role_arn               = module.eks_dr.node_role_arn
  enable_cross_region_replication = false  # No further replication from DR

  tags = local.common_tags

  depends_on = [module.vpc_dr]

  providers = {
    aws.replica = aws.primary
  }
}

module "clickhouse_dr" {
  source = "../../modules/clickhouse"

  name_prefix       = local.name_prefix
  node_count        = 1   # Single node in DR; shard config replicated from primary
  instance_type     = "r6i.xlarge"
  volume_size_gb    = 300
  volume_iops       = 3000
  clickhouse_version = "24.3"
  ami_id            = var.clickhouse_ami_id
  key_name          = var.clickhouse_key_name
  vpc_id            = module.vpc_dr.vpc_id
  subnet_ids        = module.vpc_dr.database_subnet_ids
  allowed_security_group = module.eks_dr.node_security_group_id
  tags              = local.common_tags

  depends_on = [module.vpc_dr]
}

# ---------------------------------------------------------------------------
# Cross-Region Replication (primary → secondary)
# ---------------------------------------------------------------------------
module "cross_region_replication" {
  source = "../../modules/cross-region-replication"

  primary_region   = "us-east-1"
  secondary_region = var.secondary_region
  name_prefix      = "ghatana-data-cloud-production"  # Matches the primary stack

  # RDS — cross-region read replica in us-west-2
  primary_rds_arn          = var.primary_rds_arn
  rds_replica_instance_class = "db.r6g.large"

  # MSK — managed replicator between primary and DR Kafka clusters
  primary_msk_arn  = var.primary_msk_arn
  secondary_msk_arn = module.msk_dr.cluster_arn
  msk_replication_topics = ["datacloud\\..*"]
  msk_replication_consumer_groups = ["data-cloud-.*"]

  primary_msk_subnet_ids          = []  # Sourced from production state — set via CI/CD
  primary_msk_security_group_ids  = []  # Sourced from production state — set via CI/CD
  secondary_msk_subnet_ids        = module.vpc_dr.private_subnet_ids
  secondary_msk_security_group_ids = [module.eks_dr.node_security_group_id]

  # OpenSearch — cross-region snapshots to DR S3 bucket
  primary_opensearch_arn      = var.primary_opensearch_arn
  primary_opensearch_endpoint = var.primary_opensearch_endpoint

  # ClickHouse — remote_servers config stored in SSM
  clickhouse_primary_hosts   = []  # Sourced from production state — set via CI/CD
  clickhouse_secondary_hosts = module.clickhouse_dr.node_private_ips

  tags = local.common_tags

  providers = {
    aws.primary   = aws.primary
    aws.secondary = aws
  }

  depends_on = [module.msk_dr]
}

# ---------------------------------------------------------------------------
# Global Load Balancer (Route53 failover + Global Accelerator)
# ---------------------------------------------------------------------------
module "global_lb" {
  source = "../../modules/global-load-balancer"

  name_prefix      = "ghatana-data-cloud-production"
  primary_region   = "us-east-1"
  secondary_region = var.secondary_region
  topology         = "active-passive"

  primary_alb_dns_name   = var.primary_alb_dns_name
  primary_alb_zone_id    = var.primary_alb_zone_id
  secondary_alb_dns_name = module.eks_dr.ingress_alb_dns_name  # EKS ingress in DR
  secondary_alb_zone_id  = module.eks_dr.ingress_alb_zone_id

  route53_zone_id           = var.route53_zone_id
  api_fqdn                  = var.api_fqdn
  health_check_path         = "/health"
  health_check_port         = 8082
  enable_global_accelerator = true

  tags = local.common_tags

  providers = {
    aws.primary   = aws.primary
    aws.secondary = aws
  }

  depends_on = [module.eks_dr]
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "dr_eks_cluster_endpoint" {
  description = "Kubernetes API endpoint of the DR EKS cluster."
  value       = module.eks_dr.cluster_endpoint
}

output "dr_msk_cluster_arn" {
  description = "ARN of the DR MSK cluster (replication target)."
  value       = module.msk_dr.cluster_arn
}

output "dr_opensearch_endpoint" {
  description = "Endpoint of the DR OpenSearch domain."
  value       = module.opensearch_dr.endpoint
}

output "dr_rds_replica_endpoint" {
  description = "Endpoint of the cross-region RDS read replica (promote during failover)."
  value       = module.cross_region_replication.rds_replica_endpoint
}

output "global_accelerator_ips" {
  description = "Static anycast IPs of the Global Accelerator."
  value       = module.global_lb.global_accelerator_ips
}

output "api_fqdn" {
  description = "API FQDN routed via Route53 failover."
  value       = module.global_lb.api_fqdn
}
