# Deployment and Environments

See [14-build-tooling-cicd-architecture.md](14-build-tooling-cicd-architecture.md), [15-security-architecture.md](15-security-architecture.md), and [`diagrams/deployment.mmd`](diagrams/deployment.mmd).

## Environment Matrix

| Environment | Packaging / Runtime | Evidence | Notes |
|---|---|---|---|
| Local dev | Gradle modules, Vite UI, optional Docker | `README.md`, `Dockerfile`, `ui/package.json` | supports standalone HTTP/gRPC plus local UI |
| Test / CI | Gradle tests, Testcontainers, Vitest/Playwright, security scans | module build files, `.github/workflows/security-scanning.yml` | product-local CI emphasis is security, not full deploy |
| Kubernetes | Helm chart and raw manifests | `helm/data-cloud/**`, `k8s/**` | probes, autoscaling, network policy, external secrets |
| Staging / Production AWS | Terraform-managed AWS infra plus Helm install | `terraform/README.md` | private subnets, EKS, RDS, MSK, Redis, OpenSearch, ClickHouse, S3 |

## Implemented

- Dockerfile packages the standalone launcher runtime.
- Helm chart configures service, ingress, HPA, PDB, probes, network policy, observability, and secret integration.
- Raw Kubernetes deployment manifest provides a second deployment path.
- Terraform docs define a private AWS topology with EKS, PostgreSQL, Redis, Kafka, OpenSearch, ClickHouse, and S3.

## Inferred

- The intended production story is:
  1. Terraform provisions infra.
  2. Secrets sync into Kubernetes.
  3. Helm deploys the application.
  4. Argo CD may manage release state via `k8s/argocd-application.yaml`.

## Missing

- I did not find an end-to-end product-local release workflow automating image build/tag/publish/promote.
- Rollback procedures are implied by K8s rolling updates and Helm, but not documented as an explicit product-local sequence.

## Runtime Packaging Map

| Artifact | Source | Consumer |
|---|---|---|
| `launcher` jar | Gradle build | Docker image |
| Docker image | `products/data-cloud/Dockerfile` | local Docker, Kubernetes |
| Helm release | `helm/data-cloud` | staging/production clusters |
| K8s YAML | `products/data-cloud/k8s` | direct cluster apply or GitOps |
| Terraform outputs | `terraform/environments/*` | Helm values injection |

## Config and Secret Injection

### Implemented

- Non-sensitive env values come from ConfigMap or Helm values.
- Sensitive values come from Kubernetes Secret or External Secrets Operator.
- Terraform docs recommend AWS Secrets Manager as the secret source of truth.

### Missing

- No single environment contract enumerates required env vars by feature flag and transport mode.

## Deployment Findings

| Finding | Evidence | Impact |
|---|---|---|
| Deployment assets are strong and production-oriented | Docker, Helm, K8s, Terraform | good infra maturity |
| Packaging story may not match build reality | Dockerfile vs `launcher` Gradle config | deployment risk |
| Product docs describe more automation than product-local workflows currently show | infra docs vs `.github/workflows` contents | operator expectation mismatch |
