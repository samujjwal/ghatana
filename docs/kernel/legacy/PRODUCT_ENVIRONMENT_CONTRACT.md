# Product Environment Contract

**Version:** 1.0.0
**Status:** Implementation-Ready
**Last Updated:** 2026-05-12

## Purpose

This contract defines the schema and behavior of environments (local, dev, staging, prod) where products are deployed, verified, promoted, and operated. Environments model deployment targets, secrets providers, config providers, approval requirements, and environment-specific gates.

## Design Principles

1. **Explicit environment modeling**: Environments are first-class concepts with defined contracts.
2. **Environment-specific policies**: Each environment has its own gates, approvals, and observability profiles.
3. **Fail closed**: Missing environment config or invalid environment binding fails the phase.
4. **Progressive strictness**: Environments progress from permissive (local) to strict (prod).
5. **Immutable environment definitions**: Environment configs are versioned and audited.

## Environment Schema

### Base Environment Schema

```json
{
  "schemaVersion": "1.0.0",
  "id": "local",
  "displayName": "Local Development",
  "deploymentTarget": "compose-local",
  "secretsProvider": "local-env",
  "configProvider": "local-files",
  "approvalRequired": false,
  "requiredGates": [
    "registry-validation",
    "artifact-validation",
    "environment-validation",
    "health-check"
  ],
  "observabilityProfile": "local-standard",
  "rollbackPolicy": "immediate",
  "promotionPolicy": "none"
}
```

### Environment Fields

- **schemaVersion**: Schema version (currently "1.0.0")
- **id**: Unique environment identifier (local, dev, staging, prod)
- **displayName**: Human-readable name
- **deploymentTarget**: Which deployment adapter to use (compose-local, kubernetes, etc.)
- **secretsProvider**: How secrets are provided (local-env, external-secret-store, etc.)
- **configProvider**: How configuration is provided (local-files, environment-config-service, etc.)
- **approvalRequired**: Whether manual approval is required for deployment
- **requiredGates**: Gates that must pass before deployment
- **observabilityProfile**: Which observability profile to use
- **rollbackPolicy**: Rollback strategy (immediate, manual, disabled)
- **promotionPolicy**: Promotion rules (none, linear, approval-required)

## Standard Environments

### local

**Purpose:** Local development environment on developer machines.

**Deployment Target:** compose-local

**Characteristics:**
- No approval required
- Minimal gates
- Immediate rollback
- Local secrets via .env files
- Local config files
- Basic observability

**Config:** `config/environments/local.json`

```json
{
  "schemaVersion": "1.0.0",
  "id": "local",
  "displayName": "Local Development",
  "deploymentTarget": "compose-local",
  "secretsProvider": "local-env",
  "configProvider": "local-files",
  "approvalRequired": false,
  "requiredGates": [
    "registry-validation",
    "artifact-validation",
    "environment-validation",
    "health-check"
  ],
  "observabilityProfile": "local-standard",
  "rollbackPolicy": "immediate",
  "promotionPolicy": "none"
}
```

---

### dev

**Purpose:** Shared development environment for team testing.

**Deployment Target:** kubernetes or compose-shared

**Characteristics:**
- No approval required
- Standard gates
- Manual rollback
- External secret store
- Environment config service
- Standard observability

**Config:** `config/environments/dev.json`

```json
{
  "schemaVersion": "1.0.0",
  "id": "dev",
  "displayName": "Development",
  "deploymentTarget": "kubernetes",
  "secretsProvider": "external-secret-store",
  "configProvider": "environment-config-service",
  "approvalRequired": false,
  "requiredGates": [
    "registry-validation",
    "artifact-validation",
    "environment-validation",
    "security-scan",
    "health-check",
    "observability-check"
  ],
  "observabilityProfile": "dev-standard",
  "rollbackPolicy": "manual",
  "promotionPolicy": "linear"
}
```

---

### staging

**Purpose:** Pre-production environment for final testing and validation.

**Deployment Target:** kubernetes

**Characteristics:**
- Approval required
- Strict gates
- Manual rollback with plan
- External secret store
- Environment config service
- Production-like observability

**Config:** `config/environments/staging.json`

```json
{
  "schemaVersion": "1.0.0",
  "id": "staging",
  "displayName": "Staging",
  "deploymentTarget": "kubernetes",
  "secretsProvider": "external-secret-store",
  "configProvider": "environment-config-service",
  "approvalRequired": true,
  "requiredGates": [
    "registry-validation",
    "artifact-validation",
    "environment-validation",
    "security",
    "privacy",
    "license-policy",
    "conformance",
    "e2e",
    "performance",
    "rollback-plan",
    "approval"
  ],
  "observabilityProfile": "staging-standard",
  "rollbackPolicy": "manual-with-plan",
  "promotionPolicy": "approval-required"
}
```

---

### prod

**Purpose:** Production environment for live traffic.

**Deployment Target:** kubernetes

**Characteristics:**
- Approval required
- Strictest gates
- Manual rollback with plan
- External secret store with rotation
- Environment config service with versioning
- Full observability with alerts

**Config:** `config/environments/prod.json`

```json
{
  "schemaVersion": "1.0.0",
  "id": "prod",
  "displayName": "Production",
  "deploymentTarget": "kubernetes",
  "secretsProvider": "external-secret-store-with-rotation",
  "configProvider": "environment-config-service-versioned",
  "approvalRequired": true,
  "requiredGates": [
    "registry-validation",
    "artifact-validation",
    "security",
    "privacy",
    "license-policy",
    "conformance",
    "e2e",
    "performance",
    "rollback-plan",
    "approval",
    "change-management"
  ],
  "observabilityProfile": "prod-standard",
  "rollbackPolicy": "manual-with-plan",
  "promotionPolicy": "approval-required"
}
```

## Deployment Targets

Deployment targets are registered in `config/deployment/deployment-targets.json`:

```json
{
  "version": "1.0.0",
  "targets": {
    "compose-local": {
      "kind": "local",
      "adapter": "compose-local",
      "supportedEnvironments": ["local"],
      "requires": ["deployment.local.composeFile", "deployment.local.envFile"]
    },
    "kubernetes": {
      "kind": "cluster",
      "adapter": "kubernetes",
      "supportedEnvironments": ["dev", "staging", "prod"],
      "requires": ["deployment.kubernetes.namespace", "deployment.kubernetes.helmChart"]
    }
  }
}
```

## Secrets Providers

### local-env

**Description:** Secrets from local .env files.

**Implementation:** Read from `products/<id>/deploy/local.env.example` or user-provided .env

**Use Cases:** Local development

**Configuration:**
```json
{
  "provider": "local-env",
  "envFile": "products/digital-marketing/deploy/local.env"
}
```

---

### external-secret-store

**Description:** Secrets from external secret manager (AWS Secrets Manager, HashiCorp Vault, etc.).

**Implementation:** Adapter to secret manager API

**Use Cases:** dev, staging, prod

**Configuration:**
```json
{
  "provider": "external-secret-store",
  "backend": "aws-secrets-manager",
  "prefix": "/ghatana/digital-marketing/"
}
```

---

### external-secret-store-with-rotation

**Description:** Secrets from external secret manager with automatic rotation.

**Implementation:** External secret manager + rotation hooks

**Use Cases:** prod

**Configuration:**
```json
{
  "provider": "external-secret-store-with-rotation",
  "backend": "aws-secrets-manager",
  "prefix": "/ghatana/digital-marketing/",
  "rotationPolicy": {
    "intervalDays": 90,
    "notifyBeforeDays": 7
  }
}
```

## Config Providers

### local-files

**Description:** Configuration from local YAML/JSON files.

**Implementation:** Read from `products/<id>/runtime/runtime-profile.yaml`

**Use Cases:** Local development

**Configuration:**
```json
{
  "provider": "local-files",
  "configPath": "products/digital-marketing/runtime/runtime-profile.yaml"
}
```

---

### environment-config-service

**Description:** Configuration from centralized config service.

**Implementation:** Adapter to config service API

**Use Cases:** dev, staging

**Configuration:**
```json
{
  "provider": "environment-config-service",
  "serviceUrl": "https://config.service.internal",
  "prefix": "/products/digital-marketing/"
}
```

---

### environment-config-service-versioned

**Description:** Configuration from centralized config service with versioning.

**Implementation:** Config service + version tracking

**Use Cases:** prod

**Configuration:**
```json
{
  "provider": "environment-config-service-versioned",
  "serviceUrl": "https://config.service.internal",
  "prefix": "/products/digital-marketing/",
  "versioning": {
    "strategy": "semantic",
    "trackVersions": true
  }
}
```

## Observability Profiles

Observability profiles are defined in `config/observability/product-lifecycle-observability.json`:

```json
{
  "version": "1.0.0",
  "profiles": {
    "local-standard": {
      "logging": {
        "level": "debug",
        "format": "console"
      },
      "metrics": {
        "enabled": false
      },
      "tracing": {
        "enabled": false
      }
    },
    "dev-standard": {
      "logging": {
        "level": "info",
        "format": "json"
      },
      "metrics": {
        "enabled": true,
        "endpoint": "http://prometheus:9090/metrics"
      },
      "tracing": {
        "enabled": true,
        "endpoint": "http://jaeger:14268/api/traces",
        "sampleRate": 0.1
      }
    },
    "staging-standard": {
      "logging": {
        "level": "info",
        "format": "json"
      },
      "metrics": {
        "enabled": true,
        "endpoint": "http://prometheus:9090/metrics"
      },
      "tracing": {
        "enabled": true,
        "endpoint": "http://jaeger:14268/api/traces",
        "sampleRate": 0.5
      }
    },
    "prod-standard": {
      "logging": {
        "level": "warn",
        "format": "json"
      },
      "metrics": {
        "enabled": true,
        "endpoint": "http://prometheus:9090/metrics"
      },
      "tracing": {
        "enabled": true,
        "endpoint": "http://jaeger:14268/api/traces",
        "sampleRate": 0.01
      },
      "alerts": {
        "enabled": true,
        "channels": ["pagerduty", "slack"]
      }
    }
  }
}
```

## Rollback Policies

### immediate

**Description:** Rollback can be triggered immediately without approval.

**Use Cases:** local

**Behavior:** `kernel product rollback <product> --env local` executes immediately

---

### manual

**Description:** Rollback requires manual trigger but no additional approval.

**Use Cases:** dev

**Behavior:** `kernel product rollback <product> --env dev` executes immediately after manual trigger

---

### manual-with-plan

**Description:** Rollback requires manual trigger and pre-approved rollback plan.

**Use Cases:** staging, prod

**Behavior:** Rollback plan must be generated and approved before deployment; rollback executes after manual trigger

---

### disabled

**Description:** Rollback is disabled (not recommended).

**Use Cases:** None (dangerous)

---

## Promotion Policies

### none

**Description:** No promotion from this environment.

**Use Cases:** local, prod

**Behavior:** Cannot promote from local or to prod without explicit policy override

---

### linear

**Description:** Linear promotion path (local → dev → staging → prod).

**Use Cases:** dev, staging

**Behavior:** Can only promote to next environment in sequence

---

### approval-required

**Description:** Promotion requires approval.

**Use Cases:** staging → prod

**Behavior:** Promotion requires explicit approval before execution

---

## Environment Validation

Before deployment to an environment, Kernel validates:

1. **Environment exists:** Environment config is present and valid
2. **Deployment target exists:** Target is registered and supports the environment
3. **Secrets provider exists:** Provider is registered and accessible
4. **Config provider exists:** Provider is registered and accessible
5. **Product supports environment:** Product manifest declares the environment
6. **Required gates can be satisfied:** All required gates are available
7. **Rollback plan exists:** For non-local environments, rollback plan is generated

Validation failures fail the deployment phase (fail-closed).

## Environment-Specific Product Config

Products declare environment-specific overrides in their lifecycle config:

```yaml
# products/digital-marketing/kernel-product.yaml
productId: digital-marketing
lifecycleProfile: standard-web-api-product

environments:
  local:
    replicas: 1
    resources:
      cpu: "500m"
      memory: "512Mi"
  dev:
    replicas: 2
    resources:
      cpu: "1000m"
      memory: "1Gi"
  staging:
    replicas: 3
    resources:
      cpu: "2000m"
      memory: "2Gi"
  prod:
    replicas: 5
    resources:
      cpu: "4000m"
      memory: "4Gi"
```

## Environment Binding

Phases that require environment binding must specify the environment:

```bash
kernel product deploy digital-marketing --env staging
kernel product verify digital-marketing --env staging
kernel product promote digital-marketing --from staging --to prod
kernel product rollback digital-marketing --env prod
```

Kernel validates:
- Environment exists
- Environment config is valid
- Product supports the environment
- User has permissions for the environment

## Related Contracts

- [Product Lifecycle Contract](PRODUCT_LIFECYCLE_CONTRACT.md)
- [Product Artifact Contract](PRODUCT_ARTIFACT_CONTRACT.md)
- [Product Deployment Contract](PRODUCT_DEPLOYMENT_CONTRACT.md)
- [Product Release Promotion Contract](PRODUCT_RELEASE_PROMOTION_CONTRACT.md)
