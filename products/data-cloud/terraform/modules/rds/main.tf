variable "identifier" { type = string }
variable "engine_version" { type = string }
variable "instance_class" { type = string }
variable "allocated_storage_gb" { type = number }
variable "max_allocated_storage_gb" { type = number }
variable "multi_az" { type = bool }
variable "backup_retention_days" { type = number }
variable "deletion_protection" { type = bool }
variable "db_name" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group" { type = string }
variable "tags" { type = map(string) }

# ---------------------------------------------------------------------------
# Security Group
# ---------------------------------------------------------------------------
resource "aws_security_group" "rds" {
  name        = "${var.identifier}-sg"
  description = "Allow PostgreSQL access from EKS nodes only."
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "PostgreSQL from EKS nodes"
  }

  tags = merge(var.tags, { Name = "${var.identifier}-sg" })
}

# ---------------------------------------------------------------------------
# Subnet Group
# ---------------------------------------------------------------------------
resource "aws_db_subnet_group" "this" {
  name       = "${var.identifier}-subnet-group"
  subnet_ids = var.subnet_ids
  tags       = merge(var.tags, { Name = "${var.identifier}-subnet-group" })
}

# ---------------------------------------------------------------------------
# Parameter Group (PostgreSQL 16 tuning)
# ---------------------------------------------------------------------------
resource "aws_db_parameter_group" "this" {
  name        = "${var.identifier}-pg16"
  family      = "postgres${split(".", var.engine_version)[0]}"
  description = "Custom PostgreSQL parameter group for data-cloud."

  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory/32768}"  # 1/4 of RAM in 8 kB pages
  }

  parameter {
    name  = "effective_cache_size"
    value = "{DBInstanceClassMemory/16384}"  # 3/4 of RAM
  }

  parameter {
    name  = "max_connections"
    value = "500"
  }

  parameter {
    name  = "wal_level"
    value = "replica"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"  # log queries slower than 1 s
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  tags = var.tags
}

# ---------------------------------------------------------------------------
# Master credentials in Secrets Manager
# ---------------------------------------------------------------------------
resource "random_password" "rds" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "rds" {
  name                    = "${var.identifier}/credentials"
  description             = "RDS PostgreSQL master credentials for ${var.identifier}"
  recovery_window_in_days = 7
  tags                    = var.tags
}

resource "aws_secretsmanager_secret_version" "rds" {
  secret_id = aws_secretsmanager_secret.rds.id
  secret_string = jsonencode({
    username = "datacloud_admin"
    password = random_password.rds.result
    engine   = "postgres"
    host     = aws_db_instance.this.address
    port     = aws_db_instance.this.port
    dbname   = var.db_name
  })
}

# ---------------------------------------------------------------------------
# RDS Instance
# ---------------------------------------------------------------------------
resource "aws_db_instance" "this" {
  identifier              = var.identifier
  engine                  = "postgres"
  engine_version          = var.engine_version
  instance_class          = var.instance_class
  allocated_storage       = var.allocated_storage_gb
  max_allocated_storage   = var.max_allocated_storage_gb
  storage_type            = "gp3"
  storage_encrypted       = true

  db_name  = var.db_name
  username = "datacloud_admin"
  password = random_password.rds.result

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.this.name

  backup_retention_period   = var.backup_retention_days
  backup_window             = "02:00-03:00"
  maintenance_window        = "Mon:03:00-Mon:04:00"
  copy_tags_to_snapshot     = true
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.identifier}-final-snapshot"
  delete_automated_backups  = false

  performance_insights_enabled          = true
  performance_insights_retention_period = 14
  monitoring_interval                   = 60
  monitoring_role_arn                   = aws_iam_role.rds_enhanced_monitoring.arn
  enabled_cloudwatch_logs_exports       = ["postgresql", "upgrade"]

  auto_minor_version_upgrade = true
  publicly_accessible        = false
  apply_immediately          = false

  tags = var.tags
}

# ---------------------------------------------------------------------------
# Enhanced Monitoring IAM Role
# ---------------------------------------------------------------------------
resource "aws_iam_role" "rds_enhanced_monitoring" {
  name = "${var.identifier}-enhanced-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "rds_enhanced_monitoring" {
  role       = aws_iam_role.rds_enhanced_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "endpoint" { value = aws_db_instance.this.address }
output "port" { value = aws_db_instance.this.port }
output "db_name" { value = aws_db_instance.this.db_name }
output "secret_arn" {
  value     = aws_secretsmanager_secret.rds.arn
  sensitive = true
}
