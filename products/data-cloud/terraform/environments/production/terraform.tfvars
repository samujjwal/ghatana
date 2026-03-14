region         = "us-east-1"
replica_region = "us-west-2"

vpc_cidr           = "10.0.0.0/16"
availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]

eks_node_groups = {
  general = {
    instance_types = ["m6i.xlarge"]
    desired_size   = 3
    min_size       = 2
    max_size       = 10
    disk_size      = 100
    taints         = []
    labels         = { workload = "general" }
  }
  memory-optimized = {
    instance_types = ["r6i.2xlarge"]
    desired_size   = 2
    min_size       = 1
    max_size       = 6
    disk_size      = 100
    taints         = [{ key = "workload", value = "memory-intensive", effect = "NO_SCHEDULE" }]
    labels         = { workload = "memory-optimized" }
  }
}

clickhouse_ami_id   = "ami-0c7217cdde317cfec"  # Ubuntu 22.04 LTS us-east-1 — update per region
clickhouse_key_name = "ghatana-production-ops"
