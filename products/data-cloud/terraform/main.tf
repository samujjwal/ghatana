# ---------------------------------------------------------------------------
# Data sources
# ---------------------------------------------------------------------------
data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition

  name_prefix = "ghatana-data-cloud-${var.environment}"

  common_tags = {
    Project     = "ghatana-data-cloud"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# ---------------------------------------------------------------------------
# Networking
# ---------------------------------------------------------------------------
module "vpc" {
  source = "./modules/vpc"

  name                  = local.name_prefix
  vpc_cidr              = var.vpc_cidr
  availability_zones    = var.availability_zones
  private_subnet_cidrs  = var.private_subnet_cidrs
  public_subnet_cidrs   = var.public_subnet_cidrs
  database_subnet_cidrs = var.database_subnet_cidrs
  enable_nat_gateway    = var.enable_nat_gateway
  single_nat_gateway    = var.single_nat_gateway
  cluster_name          = var.cluster_name
  tags                  = local.common_tags
}

# ---------------------------------------------------------------------------
# EKS
# ---------------------------------------------------------------------------
module "eks" {
  source = "./modules/eks"

  cluster_name       = "${local.name_prefix}-eks"
  kubernetes_version = var.kubernetes_version
  vpc_id             = module.vpc.vpc_id
  subnet_ids         = module.vpc.private_subnet_ids
  cluster_log_types  = var.cluster_log_types
  cluster_addons     = var.cluster_addons
  node_groups        = var.node_groups
  tags               = local.common_tags

  depends_on = [module.vpc]
}

# ---------------------------------------------------------------------------
# RDS PostgreSQL
# ---------------------------------------------------------------------------
module "rds" {
  source = "./modules/rds"

  identifier              = "${local.name_prefix}-postgres"
  engine_version          = var.postgres_engine_version
  instance_class          = var.postgres_instance_class
  allocated_storage_gb    = var.postgres_allocated_storage_gb
  max_allocated_storage_gb = var.postgres_max_allocated_storage_gb
  multi_az                = var.postgres_multi_az
  backup_retention_days   = var.postgres_backup_retention_days
  deletion_protection     = var.postgres_deletion_protection
  db_name                 = var.postgres_db_name
  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = module.vpc.database_subnet_ids
  allowed_security_group  = module.eks.node_security_group_id
  tags                    = local.common_tags

  depends_on = [module.vpc]
}

# ---------------------------------------------------------------------------
# MSK (Kafka)
# ---------------------------------------------------------------------------
module "msk" {
  source = "./modules/msk"

  cluster_name         = "${local.name_prefix}-kafka"
  kafka_version        = var.msk_kafka_version
  broker_instance_type = var.msk_broker_instance_type
  broker_count         = var.msk_broker_count
  broker_storage_gb    = var.msk_broker_storage_gb
  sasl_scram_enabled   = var.msk_sasl_scram_enabled
  vpc_id               = module.vpc.vpc_id
  subnet_ids           = module.vpc.private_subnet_ids
  allowed_security_group = module.eks.node_security_group_id
  tags                 = local.common_tags

  depends_on = [module.vpc]
}

# ---------------------------------------------------------------------------
# ElastiCache Redis
# ---------------------------------------------------------------------------
module "elasticache" {
  source = "./modules/elasticache"

  cluster_id             = "${local.name_prefix}-redis"
  node_type              = var.redis_node_type
  engine_version         = var.redis_engine_version
  num_cache_nodes        = var.redis_num_cache_nodes
  automatic_failover     = var.redis_automatic_failover
  vpc_id                 = module.vpc.vpc_id
  subnet_ids             = module.vpc.private_subnet_ids
  allowed_security_group = module.eks.node_security_group_id
  tags                   = local.common_tags

  depends_on = [module.vpc]
}

# ---------------------------------------------------------------------------
# OpenSearch
# ---------------------------------------------------------------------------
module "opensearch" {
  source = "./modules/opensearch"

  domain_name                    = "${local.name_prefix}-search"
  engine_version                 = var.opensearch_engine_version
  instance_type                  = var.opensearch_instance_type
  instance_count                 = var.opensearch_instance_count
  volume_size_gb                 = var.opensearch_volume_size_gb
  dedicated_master_enabled       = var.opensearch_dedicated_master_enabled
  dedicated_master_type          = var.opensearch_dedicated_master_type
  dedicated_master_count         = var.opensearch_dedicated_master_count
  vpc_id                         = module.vpc.vpc_id
  subnet_ids                     = module.vpc.private_subnet_ids
  allowed_security_group         = module.eks.node_security_group_id
  eks_node_role_arn              = module.eks.node_role_arn
  tags                           = local.common_tags

  depends_on = [module.vpc]
}

# ---------------------------------------------------------------------------
# S3 (blob storage + backups)
# ---------------------------------------------------------------------------
module "s3" {
  source = "./modules/s3"

  environment                      = var.environment
  blob_bucket_prefix               = var.s3_blob_bucket_name_prefix
  backup_bucket_prefix             = var.s3_backup_bucket_name_prefix
  enable_cross_region_replication  = var.s3_enable_cross_region_replication
  replica_region                   = var.replica_region
  blob_lifecycle_transition_days   = var.s3_blob_lifecycle_transition_days
  blob_glacier_days                = var.s3_blob_glacier_days
  account_id                       = local.account_id
  eks_oidc_provider_arn            = module.eks.oidc_provider_arn
  eks_oidc_provider_url            = module.eks.oidc_provider_url
  tags                             = local.common_tags

  providers = {
    aws         = aws
    aws.replica = aws.replica
  }

  depends_on = [module.eks]
}

# ---------------------------------------------------------------------------
# ClickHouse (self-managed on EC2)
# ---------------------------------------------------------------------------
module "clickhouse" {
  source = "./modules/clickhouse"

  name                   = "${local.name_prefix}-clickhouse"
  instance_type          = var.clickhouse_instance_type
  node_count             = var.clickhouse_node_count
  volume_size_gb         = var.clickhouse_volume_size_gb
  volume_iops            = var.clickhouse_volume_iops
  clickhouse_version     = var.clickhouse_version
  vpc_id                 = module.vpc.vpc_id
  subnet_ids             = module.vpc.database_subnet_ids
  allowed_security_group = module.eks.node_security_group_id
  backup_bucket_id       = module.s3.backup_bucket_id
  tags                   = local.common_tags

  depends_on = [module.vpc, module.s3]
}
