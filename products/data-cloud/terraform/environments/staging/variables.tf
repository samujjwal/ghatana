variable "region" {
  type        = string
  description = "Primary AWS region."
}

variable "replica_region" {
  type        = string
  description = "Replica AWS region (used for S3 CRR)."
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR for the VPC."
}

variable "availability_zones" {
  type        = list(string)
  description = "List of AZs to deploy into."
}

variable "eks_node_groups" {
  type = map(object({
    instance_types = list(string)
    desired_size   = number
    min_size       = number
    max_size       = number
    disk_size      = number
    taints         = list(object({ key = string, value = string, effect = string }))
    labels         = map(string)
  }))
}

variable "clickhouse_ami_id" {
  type        = string
  description = "Ubuntu 22.04 LTS AMI ID for the deployment region."
}

variable "clickhouse_key_name" {
  type        = string
  description = "EC2 key pair name for SSH access."
}
