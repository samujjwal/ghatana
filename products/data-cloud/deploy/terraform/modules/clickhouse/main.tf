variable "name_prefix" { type = string }
variable "node_count" { type = number }
variable "instance_type" { type = string }
variable "volume_size_gb" { type = number }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group" { type = string }
variable "ami_id" { type = string; description = "Ubuntu 22.04 LTS x86_64 AMI ID for the target region." }
variable "key_name" { type = string; description = "EC2 key pair name for SSH access (ops use)." }
variable "backup_bucket_id" { type = string }
variable "backup_bucket_arn" { type = string }
variable "tags" { type = map(string) }

resource "random_password" "clickhouse_default_user" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+?"
}

resource "aws_secretsmanager_secret" "clickhouse" {
  name                    = "${var.name_prefix}/clickhouse/admin"
  description             = "ClickHouse default user credentials."
  recovery_window_in_days = 7
  tags                    = var.tags
}

resource "aws_secretsmanager_secret_version" "clickhouse" {
  secret_id     = aws_secretsmanager_secret.clickhouse.id
  secret_string = jsonencode({
    username = "datacloud_admin"
    password = random_password.clickhouse_default_user.result
    port     = 8123
  })
}

# ---------------------------------------------------------------------------
# Security Group
# ---------------------------------------------------------------------------
resource "aws_security_group" "clickhouse" {
  name        = "${var.name_prefix}-clickhouse-sg"
  description = "ClickHouse: HTTP (8123) and native (9000) from EKS nodes."
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 8123
    to_port         = 8123
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "ClickHouse HTTP interface from EKS nodes"
  }

  ingress {
    from_port       = 9000
    to_port         = 9000
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "ClickHouse native interface from EKS nodes"
  }

  ingress {
    from_port   = 9009
    to_port     = 9009
    protocol    = "tcp"
    self        = true
    description = "ClickHouse inter-node replication"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-clickhouse-sg" })
}

# ---------------------------------------------------------------------------
# IAM Instance Profile (for clickhouse-backup to write to S3)
# ---------------------------------------------------------------------------
resource "aws_iam_role" "clickhouse_instance" {
  name = "${var.name_prefix}-clickhouse-ec2"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.clickhouse_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "clickhouse_s3_backup" {
  name = "s3-backup-access"
  role = aws_iam_role.clickhouse_instance.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListMultipartUploadParts", "s3:AbortMultipartUpload"]
        Resource = "${var.backup_bucket_arn}/clickhouse/*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = var.backup_bucket_arn
      },
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = aws_secretsmanager_secret.clickhouse.arn
      }
    ]
  })
}

resource "aws_iam_instance_profile" "clickhouse" {
  name = "${var.name_prefix}-clickhouse"
  role = aws_iam_role.clickhouse_instance.name
  tags = var.tags
}

# ---------------------------------------------------------------------------
# EC2 Instances
# ---------------------------------------------------------------------------
locals {
  clickhouse_user_data = base64encode(templatefile("${path.module}/user_data.sh.tpl", {
    clickhouse_version = "24.3.*"
    admin_user         = "datacloud_admin"
    admin_password     = random_password.clickhouse_default_user.result
    backup_bucket      = var.backup_bucket_id
    secret_arn         = aws_secretsmanager_secret.clickhouse.arn
  }))
}

resource "aws_instance" "clickhouse" {
  count                       = var.node_count
  ami                         = var.ami_id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_ids[count.index % length(var.subnet_ids)]
  vpc_security_group_ids      = [aws_security_group.clickhouse.id]
  iam_instance_profile        = aws_iam_instance_profile.clickhouse.name
  key_name                    = var.key_name
  user_data_base64            = local.clickhouse_user_data
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"  # IMDSv2 only
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 50
    encrypted             = true
    delete_on_termination = true
    tags                  = merge(var.tags, { Name = "${var.name_prefix}-clickhouse-${count.index}-root" })
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-clickhouse-${count.index}"
    Role = "clickhouse"
  })

  lifecycle {
    ignore_changes = [ami]  # prevent drift from AMI updates; use managed patching
  }
}

resource "aws_ebs_volume" "clickhouse_data" {
  count             = var.node_count
  availability_zone = aws_instance.clickhouse[count.index].availability_zone
  type              = "gp3"
  size              = var.volume_size_gb
  iops              = 6000
  throughput        = 500
  encrypted         = true

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-clickhouse-${count.index}-data"
  })
}

resource "aws_volume_attachment" "clickhouse_data" {
  count       = var.node_count
  device_name = "/dev/xvdf"
  volume_id   = aws_ebs_volume.clickhouse_data[count.index].id
  instance_id = aws_instance.clickhouse[count.index].id
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "private_ips" {
  value = aws_instance.clickhouse[*].private_ip
}

output "instance_ids" {
  value = aws_instance.clickhouse[*].id
}

output "secret_arn" {
  value = aws_secretsmanager_secret.clickhouse.arn
}
