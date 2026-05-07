variable "cluster_name" { type = string }
variable "kafka_version" { type = string }
variable "broker_instance_type" { type = string }
variable "broker_count" { type = number }
variable "broker_storage_gb" { type = number }
variable "sasl_scram_enabled" { type = bool }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group" { type = string }
variable "tags" { type = map(string) }

# ---------------------------------------------------------------------------
# Security Group
# ---------------------------------------------------------------------------
resource "aws_security_group" "msk" {
  name        = "${var.cluster_name}-sg"
  description = "Allow Kafka access from EKS nodes only."
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 9096  # SASL/SCRAM TLS
    to_port         = 9096
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "Kafka SASL/SCRAM TLS from EKS nodes"
  }

  ingress {
    from_port       = 9092  # PLAINTEXT (internal only, disabled in production)
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "Kafka plaintext from EKS nodes (intra-cluster only)"
  }

  ingress {
    from_port       = 2181  # ZooKeeper
    to_port         = 2181
    protocol        = "tcp"
    security_groups = [var.allowed_security_group]
    description     = "ZooKeeper from EKS nodes"
  }

  tags = merge(var.tags, { Name = "${var.cluster_name}-sg" })
}

# ---------------------------------------------------------------------------
# CloudWatch Log Group
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/${var.cluster_name}"
  retention_in_days = 30
  tags              = var.tags
}

# ---------------------------------------------------------------------------
# MSK Configuration (Kafka broker properties)
# ---------------------------------------------------------------------------
resource "aws_msk_configuration" "this" {
  name              = "${var.cluster_name}-config"
  kafka_versions    = [var.kafka_version]
  description       = "data-cloud Kafka broker configuration"

  server_properties = <<-EOF
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=4
    num.io.threads=8
    num.network.threads=5
    socket.request.max.bytes=104857600
    log.retention.hours=168
    log.segment.bytes=1073741824
    log.retention.check.interval.ms=300000
    message.max.bytes=10485760
    replica.lag.time.max.ms=30000
    group.initial.rebalance.delay.ms=3000
    transaction.state.log.min.isr=2
    transaction.state.log.replication.factor=3
  EOF
}

# ---------------------------------------------------------------------------
# MSK Cluster
# ---------------------------------------------------------------------------
resource "aws_msk_cluster" "this" {
  cluster_name           = var.cluster_name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = slice(var.subnet_ids, 0, var.broker_count)
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.broker_storage_gb
        provisioned_throughput {
          enabled           = true
          volume_throughput = 250
        }
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  client_authentication {
    sasl {
      scram = var.sasl_scram_enabled
    }
    unauthenticated = false
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  open_monitoring {
    prometheus {
      jmx_exporter  { enabled_in_broker = true }
      node_exporter { enabled_in_broker = true }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  enhanced_monitoring = "PER_BROKER"
  tags                = var.tags
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "bootstrap_brokers_sasl_scram" {
  value     = aws_msk_cluster.this.bootstrap_brokers_sasl_scram
  sensitive = true
}

output "zookeeper_connect_string" {
  value = aws_msk_cluster.this.zookeeper_connect_string
}

output "cluster_arn" {
  value = aws_msk_cluster.this.arn
}
