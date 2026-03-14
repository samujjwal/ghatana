module "data_cloud" {
  source = "../../"

  # ----- Globals -----
  environment    = "staging"
  region         = var.region
  replica_region = var.replica_region

  # ----- VPC -----
  vpc_cidr              = var.vpc_cidr
  availability_zones    = var.availability_zones
  single_nat_gateway    = true   # cost optimised for staging

  # ----- EKS -----
  eks_cluster_version = "1.29"
  eks_node_groups     = var.eks_node_groups

  # ----- RDS -----
  rds_instance_class    = "db.t4g.medium"
  rds_multi_az          = false
  rds_deletion_protection = false
  rds_storage_gb        = 50
  rds_backup_days       = 3

  # ----- MSK -----
  msk_instance_type     = "kafka.t3.small"
  msk_broker_count      = 2
  msk_storage_gb        = 100

  # ----- ElastiCache -----
  elasticache_node_type     = "cache.t4g.medium"
  elasticache_replicas      = 1

  # ----- OpenSearch -----
  opensearch_instance_type  = "t3.small.search"
  opensearch_instance_count = 1
  opensearch_volume_size_gb = 50
  opensearch_dedicated_master_enabled = false
  opensearch_dedicated_master_type    = ""
  opensearch_dedicated_master_count   = 0

  # ----- S3 -----
  enable_cross_region_replication = false

  # ----- ClickHouse -----
  clickhouse_node_count   = 1
  clickhouse_instance_type = "r6i.large"
  clickhouse_volume_size_gb = 200
  clickhouse_ami_id        = var.clickhouse_ami_id
  clickhouse_key_name      = var.clickhouse_key_name
}
