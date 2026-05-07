# data-cloud — Terraform Infrastructure

Production-grade Terraform module for deploying the `data-cloud` platform on AWS.
All infrastructure is provisioned in private subnets; no resources are publicly exposed.

---

## Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│  VPC (10.0.0.0/16)                                                 │
│                                                                    │
│  ┌─────────────────────────────┐  ┌─────────────────────────────┐  │
│  │  Private Subnets (EKS)     │  │  Database Subnets           │  │
│  │  ┌───────┐  ┌───────────┐  │  │  ┌──────┐  ┌────────────┐  │  │
│  │  │  EKS  │  │ClickHouse │  │  │  │  RDS │  │ElastiCache │  │  │
│  │  │  Pods │  │  EC2 ×3   │  │  │  │  PG16│  │  Redis 7   │  │  │
│  │  └───────┘  └───────────┘  │  │  └──────┘  └────────────┘  │  │
│  └─────────────────────────────┘  │  ┌──────┐  ┌────────────┐  │  │
│                                   │  │  MSK │  │OpenSearch  │  │  │
│  ┌─────────────────────────────┐  │  │Kafka │  │  2.11      │  │  │
│  │  Public Subnets (NAT GW)   │  │  └──────┘  └────────────┘  │  │
│  └─────────────────────────────┘  └─────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
                        │
              S3 Gateway Endpoint
                        │
              ┌─────────────────┐
              │  S3 Buckets     │
              │  blob / backup  │
              └─────────────────┘
```

---

## Module Map

| Module | Purpose | Key Resources |
| :--- | :--- | :--- |
| `modules/vpc` | Network foundation | VPC, subnets (public/private/db), NAT GW, IGW, VPC endpoints |
| `modules/eks` | Kubernetes control plane | EKS 1.29, OIDC IRSA, KMS-encrypted secrets, managed node groups |
| `modules/rds` | Relational state store | PostgreSQL 16, gp3 Multi-AZ, Secrets Manager, Enhanced Monitoring |
| `modules/msk` | Event streaming | MSK Kafka 3.6, SASL/SCRAM TLS, Prometheus metrics |
| `modules/elasticache` | Hot-tier cache | Redis 7 replication group, auth token, TLS, allkeys-lru |
| `modules/opensearch` | Full-text search | OpenSearch 2.11, VPC, fine-grained access control, dedicated masters |
| `modules/s3` | Object/blob storage | Blob bucket + backup bucket, CRR, IRSA role, lifecycle |
| `modules/clickhouse` | Analytical OLAP store | EC2 r6i.2xlarge × N, 500 GB gp3 EBS, automated S3 backup |

---

## Getting Started

### Prerequisites

- Terraform ≥ 1.7
- AWS CLI v2 configured with sufficient IAM permissions
- An S3 bucket `ghatana-terraform-state` and DynamoDB table `ghatana-terraform-locks` for remote state (create once per account using the bootstrap below)

### Bootstrap remote state (one-time)

```bash
aws s3api create-bucket \
  --bucket ghatana-terraform-state \
  --region us-east-1

aws s3api put-bucket-versioning \
  --bucket ghatana-terraform-state \
  --versioning-configuration Status=Enabled

aws dynamodb create-table \
  --table-name ghatana-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

### Deploy staging

```bash
cd terraform/environments/staging

# Edit terraform.tfvars with your actual values (AMI ID, key pair name, etc.)
terraform init
terraform plan -out=staging.plan
terraform apply staging.plan
```

### Deploy production

```bash
cd terraform/environments/production

terraform init
terraform plan -out=production.plan
terraform apply production.plan
```

---

## Helm Values Integration

After a successful apply, generate the Helm values overlay:

```bash
cd terraform/environments/production
terraform output -raw helm_values_snippet > /tmp/tf-values.yaml
helm upgrade --install data-cloud ./helm/data-cloud \
  --namespace data-cloud \
  -f /tmp/tf-values.yaml
```

This injects all infrastructure endpoints (Kafka bootstrap, RDS host, OpenSearch endpoint, S3 bucket, ClickHouse IPs, Redis endpoint) directly into the Helm release.

---

## Kubernetes Secrets Pre-seeding

The Terraform modules store all credentials in AWS Secrets Manager. Before deploying the Helm chart, sync them into Kubernetes using the External Secrets Operator or the provided script:

```bash
scripts/deployment/seed-k8s-secrets.sh \
  --cluster ghatana-data-cloud-production \
  --region us-east-1
```

---

## Environment Differences

| Setting | Staging | Production |
| :--- | :--- | :--- |
| NAT Gateways | 1 (cost-optimised) | 1 per AZ (HA) |
| RDS | `db.t4g.medium`, no Multi-AZ | `db.r6g.xlarge`, Multi-AZ |
| MSK | `kafka.t3.small`, 2 brokers | `kafka.m5.large`, 3 brokers |
| Redis | `cache.t4g.medium`, 1 replica | `cache.r7g.large`, 2 replicas |
| OpenSearch | `t3.small.search` × 1 | `r6g.large.search` × 3 + 3 dedicated masters |
| ClickHouse | 1 node, 200 GB | 3 nodes, 500 GB |
| S3 CRR | Disabled | Enabled (us-west-2 replica) |
| RDS deletion protection | Disabled | **Enabled** |

---

## Security Notes

- All data at rest is encrypted (KMS for EKS secrets, SSE-KMS for S3, RDS Encrypted, ElastiCache at-rest, OpenSearch encrypt-at-rest).
- All data in transit is TLS-encrypted (MSK SASL/TLS, Redis TLS, OpenSearch HTTPS with TLS 1.2+, RDS SSL).
- EC2 instances use IMDSv2 only (`http_tokens = "required"`).
- S3 buckets deny non-TLS `s3:*` via bucket policy.
- No resources are deployed in public subnets except NAT Gateway elastic IPs.
- EKS pod permissions use IRSA (IAM Roles for Service Accounts) — no static credentials.
