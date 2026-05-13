# Product Deployment Contract

**Version:** 1.0.0
**Status:** Implementation-Ready
**Last Updated:** 2026-05-12

## Purpose

This contract defines the schema and behavior of product deployment to environments. Deployment includes planning, execution, health checks, verification, and rollback. Kernel orchestrates deployment through deployment adapters while products declare deployment intent through their manifests.

## Design Principles

1. **Deployment as a first-class phase**: Deployment is not an afterthought but a modeled lifecycle phase.
2. **Artifact-driven deployment**: Deployment consumes validated artifacts from package/release phases.
3. **Health checks are mandatory**: All deployments must pass health checks before being considered successful.
4. **Rollback is always planned**: Rollback plans are generated before non-local deployments.
5. **Fail closed**: Missing artifacts, invalid environment config, or failed health checks fail deployment.

## Deployment Schema

### Deployment Plan

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "environment": "staging",
  "deploymentId": "deploy-20260512-143000",
  "releaseArtifactManifest": ".kernel/out/products/digital-marketing/release/20260512-143000/artifact-manifest.json",
  "deploymentTarget": "kubernetes",
  "plannedAt": "2026-05-12T14:30:00Z",
  "services": [
    {
      "name": "digital-marketing-api",
      "surface": "backend-api",
      "image": "ghatana/digital-marketing-api:1.0.0",
      "digest": "sha256:abc123...",
      "replicas": 3,
      "namespace": "digital-marketing-staging",
      "resources": {
        "cpu": "2000m",
        "memory": "2Gi"
      },
      "ports": [
        {
          "containerPort": 8080,
          "servicePort": 80,
          "protocol": "tcp"
        }
      ],
      "env": {
        "DMOS_API_PORT": "8080",
        "DATABASE_URL": "${secrets.database.url}"
      },
      "healthCheck": {
        "type": "http",
        "path": "/health",
        "port": 8080,
        "initialDelaySeconds": 30,
        "periodSeconds": 10,
        "timeoutSeconds": 5,
        "successThreshold": 3,
        "failureThreshold": 3
      }
    },
    {
      "name": "digital-marketing-web",
      "surface": "web",
      "image": "ghatana/digital-marketing-web:1.0.0",
      "digest": "sha256:def456...",
      "replicas": 3,
      "namespace": "digital-marketing-staging",
      "resources": {
        "cpu": "500m",
        "memory": "512Mi"
      },
      "ports": [
        {
          "containerPort": 80,
          "servicePort": 443,
          "protocol": "tcp"
        }
      ],
      "healthCheck": {
        "type": "http",
        "path": "/",
        "port": 80,
        "initialDelaySeconds": 10,
        "periodSeconds": 10,
        "timeoutSeconds": 5,
        "successThreshold": 2,
        "failureThreshold": 3
      }
    }
  ],
  "rollbackPlan": {
    "strategy": "previous-artifact",
    "previousDeploymentId": "deploy-20260511-120000",
    "previousImages": {
      "backend-api": "ghatana/digital-marketing-api:0.9.0",
      "web": "ghatana/digital-marketing-web:0.9.0"
    },
    "rollbackSteps": [
      {
        "service": "digital-marketing-api",
        "action": "rollback-image",
        "toImage": "ghatana/digital-marketing-api:0.9.0"
      },
      {
        "service": "digital-marketing-web",
        "action": "rollback-image",
        "toImage": "ghatana/digital-marketing-web:0.9.0"
      },
      {
        "action": "verify-health",
        "timeoutSeconds": 300
      }
    ]
  }
}
```

### Deployment Result

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "environment": "staging",
  "deploymentId": "deploy-20260512-143000",
  "status": "succeeded",
  "startedAt": "2026-05-12T14:30:00Z",
  "completedAt": "2026-05-12T14:35:00Z",
  "durationMs": 300000,
  "services": [
    {
      "name": "digital-marketing-api",
      "status": "deployed",
      "deployedAt": "2026-05-12T14:32:00Z",
      "healthStatus": "healthy",
      "healthCheckedAt": "2026-05-12T14:34:00Z"
    },
    {
      "name": "digital-marketing-web",
      "status": "deployed",
      "deployedAt": "2026-05-12T14:31:00Z",
      "healthStatus": "healthy",
      "healthCheckedAt": "2026-05-12T14:33:00Z"
    }
  ],
  "healthChecks": [
    {
      "service": "digital-marketing-api",
      "type": "http",
      "url": "http://digital-marketing-api.staging.internal/health",
      "status": "pass",
      "responseTimeMs": 45,
      "statusCode": 200
    },
    {
      "service": "digital-marketing-web",
      "type": "http",
      "url": "https://digital-marketing-web.staging.internal/",
      "status": "pass",
      "responseTimeMs": 20,
      "statusCode": 200
    }
  ],
  "failure": null
}
```

### Deployment Manifest

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "environment": "staging",
  "deploymentId": "deploy-20260512-143000",
  "sourceRef": "main",
  "releaseArtifactManifest": ".kernel/out/products/digital-marketing/release/20260512-143000/artifact-manifest.json",
  "deployedAt": "2026-05-12T14:35:00Z",
  "deploymentTarget": "kubernetes",
  "services": [
    {
      "name": "digital-marketing-api",
      "surface": "backend-api",
      "image": "ghatana/digital-marketing-api:1.0.0",
      "digest": "sha256:abc123...",
      "replicas": 3,
      "namespace": "digital-marketing-staging"
    },
    {
      "name": "digital-marketing-web",
      "surface": "web",
      "image": "ghatana/digital-marketing-web:1.0.0",
      "digest": "sha256:def456...",
      "replicas": 3,
      "namespace": "digital-marketing-staging"
    }
  ],
  "rollbackPlan": {
    "strategy": "previous-artifact",
    "previousDeploymentId": "deploy-20260511-120000",
    "previousImages": {
      "backend-api": "ghatana/digital-marketing-api:0.9.0",
      "web": "ghatana/digital-marketing-web:0.9.0"
    }
  }
}
```

## Deployment Adapters

### ComposeDeploymentAdapter

**Purpose:** Deploy services using Docker Compose.

**Supported Environments:** local

**Required Config:**
- `composeFile`: Path to docker-compose.yml
- `envFile`: Path to .env file
- `services`: Services to deploy

**Deployment Steps:**
1. Validate compose file syntax
2. Check for port conflicts
3. Load environment variables from .env
4. Execute `docker compose up -d`
5. Wait for services to start
6. Run health checks
7. Emit deployment manifest

**Rollback Steps:**
1. Execute `docker compose down`
2. Load previous compose file
3. Execute `docker compose up -d` with previous images
4. Run health checks
5. Emit rollback manifest

---

### KubernetesDeploymentAdapter

**Purpose:** Deploy services to Kubernetes.

**Supported Environments:** dev, staging, prod

**Required Config:**
- `namespace`: Kubernetes namespace
- `helmChart`: Path to Helm chart
- `valuesFile`: Path to values file

**Deployment Steps:**
1. Validate namespace exists
2. Validate Helm chart syntax
3. Generate values file from deployment plan
4. Execute `helm upgrade --install`
5. Wait for rollout to complete
6. Run health checks
7. Emit deployment manifest

**Rollback Steps:**
1. Execute `helm rollback`
2. Wait for rollback to complete
3. Run health checks
4. Emit rollback manifest

---

### HelmDeploymentAdapter

**Purpose:** Deploy services using Helm charts.

**Supported Environments:** dev, staging, prod

**Required Config:**
- `namespace`: Kubernetes namespace
- `releaseName`: Helm release name
- `chartPath`: Path to chart
- `valuesFile`: Path to values file

**Deployment Steps:**
1. Validate namespace exists
2. Validate chart exists
3. Generate values file from deployment plan
4. Execute `helm upgrade --install`
5. Wait for resources to be ready
6. Run health checks
7. Emit deployment manifest

**Rollback Steps:**
1. Execute `helm rollback <release>`
2. Wait for rollback to complete
3. Run health checks
4. Emit rollback manifest

---

### TerraformDeploymentAdapter

**Purpose:** Deploy infrastructure using Terraform.

**Supported Environments:** staging, prod

**Required Config:**
- `workspace`: Terraform workspace
- `configPath`: Path to Terraform config
- `variablesFile`: Path to variables file

**Deployment Steps:**
1. Select Terraform workspace
2. Validate Terraform config
3. Generate variables file from deployment plan
4. Execute `terraform plan`
5. Execute `terraform apply`
6. Wait for resources to be ready
7. Run health checks
8. Emit deployment manifest

**Rollback Steps:**
1. Execute `terraform plan` with previous state
2. Execute `terraform apply` with previous state
3. Wait for resources to be ready
4. Run health checks
5. Emit rollback manifest

---

## Health Checks

### HTTP Health Check

```json
{
  "type": "http",
  "path": "/health",
  "port": 8080,
  "scheme": "http",
  "initialDelaySeconds": 30,
  "periodSeconds": 10,
  "timeoutSeconds": 5,
  "successThreshold": 3,
  "failureThreshold": 3,
  "expectedStatusCode": 200,
  "expectedBody?": "healthy"
}
```

**Validation:**
- Endpoint responds with expected status code
- Response body matches expected pattern (if specified)
- Response time within threshold

---

### TCP Health Check

```json
{
  "type": "tcp",
  "port": 8080,
  "initialDelaySeconds": 10,
  "periodSeconds": 10,
  "timeoutSeconds": 5,
  "successThreshold": 2,
  "failureThreshold": 3
}
```

**Validation:**
- Port accepts TCP connection
- Connection established within timeout

---

### Command Health Check

```json
{
  "type": "command",
  "command": ["/bin/sh", "-c", "pgrep -f java"],
  "initialDelaySeconds": 10,
  "periodSeconds": 30,
  "timeoutSeconds": 10,
  "successThreshold": 1,
  "failureThreshold": 3
}
```

**Validation:**
- Command exits with code 0
- Command completes within timeout

---

## Health Check Profiles

Health check profiles are defined in `config/deployment/health-check-profiles.json`:

```json
{
  "version": "1.0.0",
  "profiles": {
    "standard-http": {
      "type": "http",
      "path": "/health",
      "initialDelaySeconds": 30,
      "periodSeconds": 10,
      "timeoutSeconds": 5,
      "successThreshold": 3,
      "failureThreshold": 3
    },
    "quick-http": {
      "type": "http",
      "path": "/health",
      "initialDelaySeconds": 5,
      "periodSeconds": 5,
      "timeoutSeconds": 3,
      "successThreshold": 1,
      "failureThreshold": 3
    },
    "strict-http": {
      "type": "http",
      "path": "/health",
      "initialDelaySeconds": 60,
      "periodSeconds": 10,
      "timeoutSeconds": 5,
      "successThreshold": 5,
      "failureThreshold": 2
    }
  }
}
```

## Rollback Strategies

### previous-artifact

**Description:** Rollback to the previous artifact/image.

**Use Cases:** Most deployments

**Behavior:**
- Track previous deployment artifacts
- On rollback, redeploy previous artifacts
- Verify health after rollback

---

### blue-green

**Description:** Maintain two environments (blue and green), switch traffic between them.

**Use Cases:** Zero-downtime deployments

**Behavior:**
- Deploy new version to green environment
- Verify health on green
- Switch traffic from blue to green
- Keep blue as rollback target

---

### canary

**Description:** Gradually roll out new version to subset of traffic.

**Use Cases:** Risky deployments with gradual rollout

**Behavior:**
- Deploy new version alongside old
- Route small percentage of traffic to new version
- Monitor metrics and health
- Gradually increase traffic percentage
- Roll back by reverting traffic to old version

---

## Deployment Verification

After deployment, verification includes:

1. **Health checks:** All services pass health checks
2. **Smoke tests:** Critical user journeys work
3. **Metrics check:** Error rates, latency within SLA
4. **Log check:** No error spikes in logs
5. **Dependency check:** Downstream services accessible

Verification failures trigger rollback if rollback policy allows.

## Deployment Gates

Deployment gates are defined in environment config:

**Local:**
- registry-validation
- artifact-validation
- environment-validation
- health-check

**Dev:**
- registry-validation
- artifact-validation
- environment-validation
- security-scan
- health-check
- observability-check

**Staging:**
- registry-validation
- artifact-validation
- environment-validation
- security
- privacy
- license-policy
- conformance
- e2e
- performance
- rollback-plan
- approval

**Prod:**
- registry-validation
- artifact-validation
- security
- privacy
- license-policy
- conformance
- e2e
- performance
- rollback-plan
- approval
- change-management

## Deployment CLI

```bash
# Deploy to environment
kernel product deploy digital-marketing --env staging

# Deploy with specific artifact
kernel product deploy digital-marketing --env staging --artifact .kernel/out/products/digital-marketing/release/20260512-143000/artifact-manifest.json

# Dry-run deployment
kernel product deploy digital-marketing --env staging --dry-run

# Verify deployment
kernel product verify digital-marketing --env staging

# Rollback deployment
kernel product rollback digital-marketing --env staging

# Rollback to specific artifact
kernel product rollback digital-marketing --env staging --to deploy-20260511-120000
```

## Deployment Output

Deployment writes to:
```
.kernel/out/products/<productId>/deploy/<environment>/<timestamp>/
  deployment-plan.json
  deployment-result.json
  deployment-manifest.json
  health-check-report.json
  logs/
```

## Related Contracts

- [Product Lifecycle Contract](PRODUCT_LIFECYCLE_CONTRACT.md)
- [Product Artifact Contract](PRODUCT_ARTIFACT_CONTRACT.md)
- [Product Environment Contract](PRODUCT_ENVIRONMENT_CONTRACT.md)
- [Product Release Promotion Contract](PRODUCT_RELEASE_PROMOTION_CONTRACT.md)
