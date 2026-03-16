# =============================================================================
# DR Environment — Concrete Variable Values
# =============================================================================
# DO NOT commit actual ARNs or sensitive values.  Use AWS Secrets Manager or
# GitHub Actions secrets and pass via:
#   -var="primary_rds_arn=$PRIMARY_RDS_ARN"
# for pipeline runs.
#
# The values below are safe-to-commit defaults / placeholders.
# Sensitive fields (primary_rds_arn, primary_msk_arn, primary_opensearch_arn)
# MUST be supplied at plan/apply time via environment variables or the CI/CD
# pipeline — they are never stored in version control.
# =============================================================================

# --- Regions & Networking ---------------------------------------------------
secondary_region = "us-west-2"
secondary_vpc_cidr = "10.1.0.0/16"
secondary_availability_zones = ["us-west-2a", "us-west-2b", "us-west-2c"]

# --- Primary Region References (OVERRIDE in CI/CD) --------------------------
# These are sourced from `terraform output` in environments/production/ and
# passed as TF_VAR_* environment variables in the CI pipeline.
#
# Example pipeline step:
#   cd environments/production
#   export TF_VAR_primary_rds_arn=$(terraform output -raw rds_instance_arn)
#   export TF_VAR_primary_msk_arn=$(terraform output -raw msk_cluster_arn)
#   export TF_VAR_primary_opensearch_arn=$(terraform output -raw opensearch_domain_arn)
#   export TF_VAR_primary_opensearch_endpoint=$(terraform output -raw opensearch_endpoint)
#   export TF_VAR_primary_alb_dns_name=$(terraform output -raw alb_dns_name)
#   export TF_VAR_primary_alb_zone_id=$(terraform output -raw alb_zone_id)
#   cd ../dr
#   terraform apply -var-file=terraform.tfvars

primary_alb_dns_name = "REPLACE_WITH_PRIMARY_ALB_DNS"   # from production TF output
primary_alb_zone_id  = "REPLACE_WITH_PRIMARY_ALB_ZONE"  # from production TF output

# --- Route53 ----------------------------------------------------------------
route53_zone_id = "REPLACE_WITH_ROUTE53_ZONE_ID"  # e.g. Z01234567ABCDEFGHIJKL
api_fqdn        = "api.data-cloud.ghatana.io"

# --- ClickHouse AMI (us-west-2) ---------------------------------------------
# Ubuntu 22.04 LTS — verify quarterly:
# aws ec2 describe-images --region us-west-2 --owners 099720109477 \
#   --filters "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
#   --query 'sort_by(Images,&CreationDate)[-1].{ID:ImageId,Name:Name}'
clickhouse_ami_id   = "ami-0735c191cf914754d"
clickhouse_key_name = "ghatana-dr-clickhouse"
