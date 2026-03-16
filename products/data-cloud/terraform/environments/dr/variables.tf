# =============================================================================
# DR Environment — Variable Declarations
# =============================================================================
# All variables declared here match exactly what is sourced in main.tf.

variable "secondary_region" {
  description = "AWS region for the DR / secondary deployment."
  type        = string
  default     = "us-west-2"
}

variable "secondary_vpc_cidr" {
  description = "VPC CIDR for DR region — must not overlap with primary (10.0.0.0/16)."
  type        = string
  default     = "10.1.0.0/16"
}

variable "secondary_availability_zones" {
  description = "Availability zones available in the DR region."
  type        = list(string)
  default     = ["us-west-2a", "us-west-2b", "us-west-2c"]
}

# ---------------------------------------------------------------------------
# Primary-region references (sourced from production apply outputs / CI pipeline)
# ---------------------------------------------------------------------------

variable "primary_rds_arn" {
  description = "ARN of the primary RDS instance in us-east-1."
  type        = string
  sensitive   = true
}

variable "primary_msk_arn" {
  description = "ARN of the primary MSK cluster in us-east-1."
  type        = string
  sensitive   = true
}

variable "primary_opensearch_arn" {
  description = "ARN of the primary OpenSearch domain in us-east-1."
  type        = string
  sensitive   = true
}

variable "primary_opensearch_endpoint" {
  description = "VPC endpoint URL of the primary OpenSearch domain in us-east-1."
  type        = string
}

variable "primary_alb_dns_name" {
  description = "DNS name of the primary region ALB or Ingress controller."
  type        = string
}

variable "primary_alb_zone_id" {
  description = "Route53 alias hosted-zone ID of the primary ALB."
  type        = string
}

# ---------------------------------------------------------------------------
# Route53 / global load balancing
# ---------------------------------------------------------------------------

variable "route53_zone_id" {
  description = "ID of the Route53 public hosted zone that owns the api_fqdn."
  type        = string
}

variable "api_fqdn" {
  description = "Fully-qualified API domain name managed by Route53 (e.g. api.data-cloud.ghatana.io)."
  type        = string
  default     = "api.data-cloud.ghatana.io"
}

# ---------------------------------------------------------------------------
# ClickHouse
# ---------------------------------------------------------------------------

variable "clickhouse_ami_id" {
  description = "Ubuntu 22.04 LTS AMI ID in us-west-2. Update quarterly via scripts/update_amis.sh."
  type        = string
  # Default below valid as of 2024-Q1; verify at:
  # aws ec2 describe-images --region us-west-2 --owners 099720109477 \
  #   --filters "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
  #   --query 'sort_by(Images,&CreationDate)[-1].ImageId'
  default = "ami-0735c191cf914754d"
}

variable "clickhouse_key_name" {
  description = "Name of the EC2 SSH key pair in us-west-2 to attach to ClickHouse nodes."
  type        = string
}
