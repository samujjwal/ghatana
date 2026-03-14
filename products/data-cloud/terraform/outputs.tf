# ---------------------------------------------------------------------------
# Networking
# ---------------------------------------------------------------------------
output "vpc_id" {
  description = "ID of the VPC."
  value       = module.vpc.vpc_id
}

output "private_subnet_ids" {
  description = "IDs of the private subnets (EKS nodes, MSK, ElastiCache)."
  value       = module.vpc.private_subnet_ids
}

output "database_subnet_ids" {
  description = "IDs of the isolated database subnets (RDS, ClickHouse)."
  value       = module.vpc.database_subnet_ids
}

output "public_subnet_ids" {
  description = "IDs of the public subnets (NAT gateways, ALB)."
  value       = module.vpc.public_subnet_ids
}

# ---------------------------------------------------------------------------
# EKS
# ---------------------------------------------------------------------------
output "eks_cluster_name" {
  description = "EKS cluster name."
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS API server endpoint."
  value       = module.eks.cluster_endpoint
}

output "eks_cluster_ca_certificate" {
  description = "Base64-encoded cluster CA certificate."
  value       = module.eks.cluster_ca_certificate
  sensitive   = true
}

output "eks_oidc_provider_arn" {
  description = "ARN of the EKS OIDC provider (used for IRSA trust policies)."
  value       = module.eks.oidc_provider_arn
}

output "eks_node_role_arn" {
  description = "ARN of the IAM role attached to EKS managed node groups."
  value       = module.eks.node_role_arn
}

# ---------------------------------------------------------------------------
# RDS PostgreSQL
# ---------------------------------------------------------------------------
output "postgres_endpoint" {
  description = "RDS PostgreSQL endpoint (hostname:port)."
  value       = module.rds.endpoint
}

output "postgres_port" {
  description = "RDS PostgreSQL port."
  value       = module.rds.port
}

output "postgres_db_name" {
  description = "Initial database name."
  value       = module.rds.db_name
}

output "postgres_secret_arn" {
  description = "ARN of the Secrets Manager secret holding PostgreSQL credentials."
  value       = module.rds.secret_arn
  sensitive   = true
}

# ---------------------------------------------------------------------------
# MSK (Kafka)
# ---------------------------------------------------------------------------
output "msk_bootstrap_brokers" {
  description = "MSK bootstrap broker connection string (SASL/SCRAM over TLS)."
  value       = module.msk.bootstrap_brokers_sasl_scram
  sensitive   = true
}

output "msk_zookeeper_connect" {
  description = "MSK ZooKeeper connection string (for topic administration)."
  value       = module.msk.zookeeper_connect_string
}

# ---------------------------------------------------------------------------
# ElastiCache Redis
# ---------------------------------------------------------------------------
output "redis_primary_endpoint" {
  description = "ElastiCache Redis primary endpoint (for write operations)."
  value       = module.elasticache.primary_endpoint
}

output "redis_reader_endpoint" {
  description = "ElastiCache Redis reader endpoint (for read-replica operations)."
  value       = module.elasticache.reader_endpoint
}

output "redis_port" {
  description = "Redis port."
  value       = module.elasticache.port
}

# ---------------------------------------------------------------------------
# OpenSearch
# ---------------------------------------------------------------------------
output "opensearch_endpoint" {
  description = "OpenSearch domain HTTPS endpoint."
  value       = module.opensearch.endpoint
}

output "opensearch_kibana_endpoint" {
  description = "OpenSearch Dashboards endpoint."
  value       = module.opensearch.kibana_endpoint
}

output "opensearch_domain_arn" {
  description = "ARN of the OpenSearch domain."
  value       = module.opensearch.domain_arn
}

# ---------------------------------------------------------------------------
# S3
# ---------------------------------------------------------------------------
output "s3_blob_bucket_id" {
  description = "S3 blob-storage bucket name."
  value       = module.s3.blob_bucket_id
}

output "s3_blob_bucket_arn" {
  description = "S3 blob-storage bucket ARN."
  value       = module.s3.blob_bucket_arn
}

output "s3_backup_bucket_id" {
  description = "S3 backup bucket name."
  value       = module.s3.backup_bucket_id
}

output "s3_irsa_role_arn" {
  description = "ARN of the IAM role for IRSA S3 access (mounted by the data-cloud pod)."
  value       = module.s3.irsa_role_arn
}

# ---------------------------------------------------------------------------
# ClickHouse
# ---------------------------------------------------------------------------
output "clickhouse_private_ips" {
  description = "Private IP addresses of the ClickHouse EC2 nodes."
  value       = module.clickhouse.private_ips
}

output "clickhouse_http_endpoint" {
  description = "Primary ClickHouse HTTP interface endpoint (http://ip:8123)."
  value       = module.clickhouse.http_endpoint
}

# ---------------------------------------------------------------------------
# Helm / App config helpers
# ---------------------------------------------------------------------------
output "helm_values_snippet" {
  description = "Ready-to-paste Helm values fragment for the data-cloud chart."
  value = templatefile("${path.module}/templates/helm-values.tpl", {
    postgres_host        = module.rds.endpoint
    kafka_brokers        = module.msk.bootstrap_brokers_sasl_scram
    redis_host           = module.elasticache.primary_endpoint
    redis_port           = module.elasticache.port
    opensearch_host      = module.opensearch.endpoint
    clickhouse_host      = module.clickhouse.http_endpoint
    s3_blob_bucket       = module.s3.blob_bucket_id
    s3_backup_bucket     = module.s3.backup_bucket_id
    aws_region           = var.aws_region
    s3_irsa_role_arn     = module.s3.irsa_role_arn
  })
  sensitive = true
}
