module "data_cloud" {
  source = "../../"

  # ----- Globals -----
  environment    = "staging"
  aws_region     = var.region
  replica_region = var.replica_region

  # ----- VPC -----
  vpc_cidr              = var.vpc_cidr
  availability_zones    = var.availability_zones
  single_nat_gateway    = true   # cost optimised for staging

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
  postgres_instance_class        = "db.t4g.medium"
  postgres_multi_az              = false
  postgres_deletion_protection   = false
  postgres_allocated_storage_gb  = 50
  postgres_backup_retention_days = 3

  # ----- MSK -----
  msk_broker_instance_type = "kafka.t3.small"
  msk_broker_count         = 2
  msk_broker_storage_gb    = 100

  # ----- ElastiCache -----
  redis_node_type       = "cache.t4g.medium"
  redis_num_cache_nodes = 1

  # ----- OpenSearch -----
  opensearch_instance_type  = "t3.small.search"
  opensearch_instance_count = 1
  opensearch_volume_size_gb = 50
  opensearch_dedicated_master_enabled = false
  opensearch_dedicated_master_type    = ""
  opensearch_dedicated_master_count   = 0

  # ----- S3 -----
  s3_enable_cross_region_replication = false

  # ----- ClickHouse -----
  clickhouse_node_count   = 1
  clickhouse_instance_type = "r6i.large"
  clickhouse_volume_size_gb = 200
  clickhouse_ami_id        = var.clickhouse_ami_id
  clickhouse_key_name      = var.clickhouse_key_name
}
