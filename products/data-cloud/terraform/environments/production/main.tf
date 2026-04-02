module "data_cloud" {
  source = "../../"

  # ----- Globals -----
  environment    = "production"
  aws_region     = var.region
  replica_region = var.replica_region

  # ----- VPC -----
  vpc_cidr              = var.vpc_cidr
  availability_zones    = var.availability_zones
  single_nat_gateway    = false  # per-AZ for HA

  # ----- EKS -----
  kubernetes_version = "1.29"
  node_groups = {
    for name, group in var.eks_node_groups : name => {
      instance_types = group.instance_types
      desired_size   = group.desired_size
      min_size       = group.min_size
      max_size       = group.max_size
      disk_size_gb   = group.disk_size
      taints         = group.taints
      labels         = group.labels
    }
  }

  # ----- RDS -----
  postgres_instance_class        = "db.r6g.xlarge"
  postgres_multi_az              = true
  postgres_deletion_protection   = true
  postgres_allocated_storage_gb  = 500
  postgres_backup_retention_days = 14

  # ----- MSK -----
  msk_broker_instance_type = "kafka.m5.large"
  msk_broker_count         = 3
  msk_broker_storage_gb    = 1000

  # ----- ElastiCache -----
  redis_node_type       = "cache.r7g.large"
  redis_num_cache_nodes = 2

  # ----- OpenSearch -----
  opensearch_instance_type            = "r6g.large.search"
  opensearch_instance_count           = 3
  opensearch_volume_size_gb           = 300
  opensearch_dedicated_master_enabled = true
  opensearch_dedicated_master_type    = "m6g.large.search"
  opensearch_dedicated_master_count   = 3

  # ----- S3 -----
  s3_enable_cross_region_replication = true

  # ----- ClickHouse -----
  clickhouse_node_count     = 3
  clickhouse_instance_type  = "r6i.2xlarge"
  clickhouse_volume_size_gb = 500
  clickhouse_ami_id         = var.clickhouse_ami_id
  clickhouse_key_name       = var.clickhouse_key_name
}
