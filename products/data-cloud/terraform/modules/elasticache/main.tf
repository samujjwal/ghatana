variable "cluster_id" { type = string }
variable "node_type" { type = string }
variable "engine_version" { type = string }
variable "num_cache_nodes" { type = number }
variable "automatic_failover" { type = bool }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group" { type = string }
variable "tags" { type = map(string) }

# ---------------------------------------------------------------------------
# Security Group
# ---------------------------------------------------------------------------
resource "aws_security_group" "redis" {
  name        = "${var.cluster_id}-sg"
  description = "Allow Redis access from EKS nodes."
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "Redis from EKS nodes"
  }

  tags = merge(var.tags, { Name = "${var.cluster_id}-sg" })
}

# ---------------------------------------------------------------------------
# Subnet Group
# ---------------------------------------------------------------------------
resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.cluster_id}-subnet-group"
  subnet_ids = var.subnet_ids
  tags       = var.tags
}

# ---------------------------------------------------------------------------
# Parameter Group (Redis 7)
# ---------------------------------------------------------------------------
resource "aws_elasticache_parameter_group" "this" {
  name   = "${var.cluster_id}-redis7"
  family = "redis7"
  description = "data-cloud Redis 7 parameter group"

  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }

  parameter {
    name  = "activerehashing"
    value = "yes"
  }

  parameter {
    name  = "lazyfree-lazy-eviction"
    value = "yes"
  }

  tags = var.tags
}

# ---------------------------------------------------------------------------
# Auth Token (stored in Secrets Manager)
# ---------------------------------------------------------------------------
resource "random_password" "redis_auth" {
  length  = 32
  special = false  # ElastiCache auth token can't contain certain specials
}

resource "aws_secretsmanager_secret" "redis_auth" {
  name                    = "${var.cluster_id}/auth-token"
  description             = "Redis AUTH token for ${var.cluster_id}"
  recovery_window_in_days = 7
  tags                    = var.tags
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id     = aws_secretsmanager_secret.redis_auth.id
  secret_string = random_password.redis_auth.result
}

# ---------------------------------------------------------------------------
# ElastiCache Replication Group
# ---------------------------------------------------------------------------
resource "aws_elasticache_replication_group" "this" {
  replication_group_id = var.cluster_id
  description          = "data-cloud Redis hot-tier cache"

  node_type               = var.node_type
  num_cache_clusters      = var.num_cache_nodes
  parameter_group_name    = aws_elasticache_parameter_group.this.name
  subnet_group_name       = aws_elasticache_subnet_group.this.name
  security_group_ids      = [aws_security_group.redis.id]
  engine_version          = var.engine_version
  port                    = 6379

  automatic_failover_enabled  = var.automatic_failover
  multi_az_enabled            = var.automatic_failover
  at_rest_encryption_enabled  = true
  transit_encryption_enabled  = true
  auth_token                  = random_password.redis_auth.result

  auto_minor_version_upgrade = true
  maintenance_window         = "sun:04:00-sun:05:00"
  snapshot_window            = "03:00-04:00"
  snapshot_retention_limit   = 7

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis_slow.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis_engine.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "engine-log"
  }

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "redis_slow" {
  name              = "/aws/elasticache/${var.cluster_id}/slow-log"
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_cloudwatch_log_group" "redis_engine" {
  name              = "/aws/elasticache/${var.cluster_id}/engine-log"
  retention_in_days = 14
  tags              = var.tags
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "primary_endpoint" {
  value = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "reader_endpoint" {
  value = aws_elasticache_replication_group.this.reader_endpoint_address
}

output "port" {
  value = aws_elasticache_replication_group.this.port
}

output "auth_secret_arn" {
  value     = aws_secretsmanager_secret.redis_auth.arn
  sensitive = true
}
