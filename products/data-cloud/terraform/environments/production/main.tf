module "data_cloud" {
  source = "../../"

  # ----- Globals -----
  environment    = "production"
  region         = var.region
  replica_region = var.replica_region

  # ----- VPC -----
  vpc_cidr              = var.vpc_cidr
  availability_zones    = var.availability_zones
  single_nat_gateway    = false  # per-AZ for HA

  # ----- EKS -----
  eks_cluster_version = "1.29"
  eks_node_groups     = var.eks_node_groups

  # ----- RDS -----
  rds_instance_class      = "db.r6g.xlarge"
  rds_multi_az            = true
  rds_deletion_protection = true
  rds_storage_gb          = 500
  rds_backup_days         = 14

  # ----- MSK -----
  msk_instance_type = "kafka.m5.large"
  msk_broker_count  = 3
  msk_storage_gb    = 1000

  # ----- ElastiCache -----
  elasticache_node_type = "cache.r7g.large"
  elasticache_replicas  = 2

  # ----- OpenSearch -----
  opensearch_instance_type            = "r6g.large.search"
  opensearch_instance_count           = 3
  opensearch_volume_size_gb           = 300
  opensearch_dedicated_master_enabled = true
  opensearch_dedicated_master_type    = "m6g.large.search"
  opensearch_dedicated_master_count   = 3

  # ----- S3 -----
  enable_cross_region_replication = true

  # ----- ClickHouse -----
  clickhouse_node_count     = 3
  clickhouse_instance_type  = "r6i.2xlarge"
  clickhouse_volume_size_gb = 500
  clickhouse_ami_id         = var.clickhouse_ami_id
  clickhouse_key_name       = var.clickhouse_key_name
}
