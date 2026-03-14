region         = "us-east-1"
replica_region = "us-west-2"

vpc_cidr            = "10.0.0.0/16"
availability_zones  = ["us-east-1a", "us-east-1b"]

eks_node_groups = {
  general = {
    instance_types = ["t3.large"]
    desired_size   = 2
    min_size       = 1
    max_size       = 4
    disk_size      = 50
    taints         = []
    labels         = { workload = "general" }
  }
}

clickhouse_ami_id  = "ami-0c7217cdde317cfec"  # Ubuntu 22.04 LTS us-east-1 — update per region
clickhouse_key_name = "ghatana-staging-ops"
