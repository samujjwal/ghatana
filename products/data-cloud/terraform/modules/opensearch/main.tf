variable "domain_name" { type = string }
variable "engine_version" { type = string }
variable "instance_type" { type = string }
variable "instance_count" { type = number }
variable "volume_size_gb" { type = number }
variable "dedicated_master_enabled" { type = bool }
variable "dedicated_master_type" { type = string }
variable "dedicated_master_count" { type = number }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group" { type = string }
variable "eks_node_role_arn" { type = string }
variable "tags" { type = map(string) }

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# ---------------------------------------------------------------------------
# Security Group
# ---------------------------------------------------------------------------
resource "aws_security_group" "opensearch" {
  name        = "${var.domain_name}-sg"
  description = "Allow OpenSearch HTTPS access from EKS nodes."
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "OpenSearch HTTPS from EKS nodes"
  }

  ingress {
    from_port       = 9200
    to_port         = 9200
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "OpenSearch REST from EKS nodes"
  }

  tags = merge(var.tags, { Name = "${var.domain_name}-sg" })
}

# ---------------------------------------------------------------------------
# Service-linked role (must exist before first OpenSearch domain in account)
# ---------------------------------------------------------------------------
resource "aws_iam_service_linked_role" "opensearch" {
  aws_service_name = "opensearchservice.amazonaws.com"
  # This will fail if the role already exists — use lifecycle to ignore that.
  lifecycle {
    ignore_changes = [aws_service_name]
  }
}

# ---------------------------------------------------------------------------
# Access Policy — allow traffic from EKS node role only
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "opensearch_access" {
  statement {
    sid     = "AllowEKSNodeAccess"
    effect  = "Allow"
    actions = ["es:*"]

    principals {
      type        = "AWS"
      identifiers = [var.eks_node_role_arn]
    }

    resources = ["arn:aws:es:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:domain/${var.domain_name}/*"]
  }
}

# ---------------------------------------------------------------------------
# OpenSearch Domain
# ---------------------------------------------------------------------------
resource "aws_opensearch_domain" "this" {
  domain_name    = var.domain_name
  engine_version = var.engine_version

  cluster_config {
    instance_type          = var.instance_type
    instance_count         = var.instance_count
    zone_awareness_enabled = var.instance_count > 1

    dynamic "zone_awareness_config" {
      for_each = var.instance_count > 1 ? [1] : []
      content {
        availability_zone_count = min(var.instance_count, 3)
      }
    }

    dedicated_master_enabled = var.dedicated_master_enabled
    dedicated_master_type    = var.dedicated_master_enabled ? var.dedicated_master_type : null
    dedicated_master_count   = var.dedicated_master_enabled ? var.dedicated_master_count : null
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.volume_size_gb
    iops        = 3000
    throughput  = 250
  }

  vpc_options {
    subnet_ids         = slice(var.subnet_ids, 0, min(var.instance_count, 3))
    security_group_ids = [aws_security_group.opensearch.id]
  }

  encrypt_at_rest {
    enabled = true
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https                   = true
    tls_security_policy             = "Policy-Min-TLS-1-2-2019-07"
    custom_endpoint_enabled         = false
  }

  advanced_security_options {
    enabled                        = true
    anonymous_auth_enabled         = false
    internal_user_database_enabled = false

    master_user_options {
      master_user_arn = var.eks_node_role_arn
    }
  }

  auto_tune_options {
    desired_state       = "ENABLED"
    rollback_on_disable = "NO_ROLLBACK"
  }

  log_publishing_options {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.opensearch_index_slow.arn
    log_type                 = "INDEX_SLOW_LOGS"
  }

  log_publishing_options {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.opensearch_search_slow.arn
    log_type                 = "SEARCH_SLOW_LOGS"
  }

  log_publishing_options {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.opensearch_error.arn
    log_type                 = "ES_APPLICATION_LOGS"
  }

  access_policies = data.aws_iam_policy_document.opensearch_access.json

  tags = var.tags

  depends_on = [aws_iam_service_linked_role.opensearch]
}

resource "aws_cloudwatch_log_group" "opensearch_index_slow" {
  name              = "/aws/opensearch/${var.domain_name}/index-slow"
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_cloudwatch_log_group" "opensearch_search_slow" {
  name              = "/aws/opensearch/${var.domain_name}/search-slow"
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_cloudwatch_log_group" "opensearch_error" {
  name              = "/aws/opensearch/${var.domain_name}/error"
  retention_in_days = 30
  tags              = var.tags
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "endpoint" {
  value = "https://${aws_opensearch_domain.this.endpoint}"
}

output "kibana_endpoint" {
  value = "https://${aws_opensearch_domain.this.dashboard_endpoint}"
}

output "domain_arn" {
  value = aws_opensearch_domain.this.arn
}
