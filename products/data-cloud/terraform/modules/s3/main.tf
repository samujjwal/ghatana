variable "name_prefix" { type = string }
variable "region" { type = string }
variable "replica_region" { type = string }
variable "vpc_id" { type = string }
variable "tags" { type = map(string) }
variable "enable_cross_region_replication" { type = bool; default = false }
variable "eks_node_role_arn" { type = string }

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# ---------------------------------------------------------------------------
# Blob Bucket
# ---------------------------------------------------------------------------
resource "aws_s3_bucket" "blob" {
  bucket        = "${var.name_prefix}-blobs-${data.aws_caller_identity.current.account_id}"
  force_destroy = false
  tags          = merge(var.tags, { Name = "${var.name_prefix}-blobs" })
}

resource "aws_s3_bucket_versioning" "blob" {
  bucket = aws_s3_bucket.blob.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "blob" {
  bucket = aws_s3_bucket.blob.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "blob" {
  bucket                  = aws_s3_bucket.blob.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "blob" {
  bucket = aws_s3_bucket.blob.id

  rule {
    id     = "tiered-storage"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    expiration {
      days = 730
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

resource "aws_s3_bucket_policy" "blob_deny_non_tls" {
  bucket = aws_s3_bucket.blob.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyNonTLS"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource  = ["${aws_s3_bucket.blob.arn}", "${aws_s3_bucket.blob.arn}/*"]
        Condition = { Bool = { "aws:SecureTransport" = "false" } }
      },
      {
        Sid    = "AllowIRSARole"
        Effect = "Allow"
        Principal = { AWS = aws_iam_role.s3_irsa.arn }
        Action    = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
        Resource  = ["${aws_s3_bucket.blob.arn}", "${aws_s3_bucket.blob.arn}/*"]
      }
    ]
  })
}

# ---------------------------------------------------------------------------
# Backup Bucket
# ---------------------------------------------------------------------------
resource "aws_s3_bucket" "backup" {
  bucket        = "${var.name_prefix}-backups-${data.aws_caller_identity.current.account_id}"
  force_destroy = false
  tags          = merge(var.tags, { Name = "${var.name_prefix}-backups" })
}

resource "aws_s3_bucket_versioning" "backup" {
  bucket = aws_s3_bucket.backup.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "backup" {
  bucket                  = aws_s3_bucket.backup.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    id     = "backup-retention"
    status = "Enabled"

    transition {
      days          = 7
      storage_class = "GLACIER"
    }

    expiration {
      days = 90
    }
  }
}

# ---------------------------------------------------------------------------
# Cross-Region Replication (conditional)
# ---------------------------------------------------------------------------
resource "aws_s3_bucket_replication_configuration" "blob_crr" {
  count  = var.enable_cross_region_replication ? 1 : 0
  bucket = aws_s3_bucket.blob.id
  role   = aws_iam_role.s3_replication[0].arn

  rule {
    id     = "crr-all"
    status = "Enabled"

    destination {
      bucket        = aws_s3_bucket.blob_replica[0].arn
      storage_class = "STANDARD_IA"
    }
  }

  depends_on = [aws_s3_bucket_versioning.blob]
}

resource "aws_s3_bucket" "blob_replica" {
  count    = var.enable_cross_region_replication ? 1 : 0
  bucket   = "${var.name_prefix}-blobs-replica-${data.aws_caller_identity.current.account_id}"
  provider = aws.replica
  tags     = merge(var.tags, { Name = "${var.name_prefix}-blobs-replica" })
}

resource "aws_s3_bucket_versioning" "blob_replica" {
  count    = var.enable_cross_region_replication ? 1 : 0
  bucket   = aws_s3_bucket.blob_replica[0].id
  provider = aws.replica
  versioning_configuration { status = "Enabled" }
}

resource "aws_iam_role" "s3_replication" {
  count = var.enable_cross_region_replication ? 1 : 0
  name  = "${var.name_prefix}-s3-replication"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "s3.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  inline_policy {
    name = "replication"
    policy = jsonencode({
      Version = "2012-10-17"
      Statement = [
        {
          Effect   = "Allow"
          Action   = ["s3:GetReplicationConfiguration", "s3:ListBucket"]
          Resource = aws_s3_bucket.blob.arn
        },
        {
          Effect   = "Allow"
          Action   = ["s3:GetObjectVersionForReplication", "s3:GetObjectVersionAcl", "s3:GetObjectVersionTagging"]
          Resource = "${aws_s3_bucket.blob.arn}/*"
        },
        {
          Effect   = "Allow"
          Action   = ["s3:ReplicateObject", "s3:ReplicateDelete", "s3:ReplicateTags"]
          Resource = "${aws_s3_bucket.blob_replica[0].arn}/*"
        }
      ]
    })
  }

  tags = var.tags
}

# ---------------------------------------------------------------------------
# IRSA Role for Data-Cloud pods
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "s3_irsa_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.eks_node_role_arn]
    }
  }
}

resource "aws_iam_role" "s3_irsa" {
  name               = "${var.name_prefix}-s3-irsa"
  assume_role_policy = data.aws_iam_policy_document.s3_irsa_trust.json
  tags               = var.tags
}

resource "aws_iam_role_policy" "s3_irsa" {
  name = "s3-access"
  role = aws_iam_role.s3_irsa.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.blob.arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = aws_s3_bucket.blob.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:ListBucket"]
        Resource = ["${aws_s3_bucket.backup.arn}", "${aws_s3_bucket.backup.arn}/*"]
      }
    ]
  })
}

# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------
output "blob_bucket_id"    { value = aws_s3_bucket.blob.id }
output "blob_bucket_arn"   { value = aws_s3_bucket.blob.arn }
output "backup_bucket_id"  { value = aws_s3_bucket.backup.id }
output "backup_bucket_arn" { value = aws_s3_bucket.backup.arn }
output "irsa_role_arn"     { value = aws_iam_role.s3_irsa.arn }
