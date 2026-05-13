# Product Release and Promotion Contract

**Version:** 1.0.0
**Status:** Implementation-Ready
**Last Updated:** 2026-05-12

## Purpose

This contract defines the schema and behavior of product release and promotion across environments. Release creates versioned artifacts, while promotion moves releases between environments (e.g., staging → prod) with gates, approvals, and rollback plans.

## Design Principles

1. **Release is versioned**: Every release has a semantic version and immutable artifacts.
2. **Promotion is gated**: Promotion requires passing all environment-specific gates.
3. **Rollback is planned**: Rollback plans are generated before promotion.
4. **Approval is explicit**: Non-local promotion requires explicit approval.
5. **Fail closed**: Missing artifacts, failed gates, or missing approval fail promotion.

## Release Schema

### Release Plan

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "version": "1.0.0",
  "releaseId": "release-20260512-143000",
  "packageArtifactManifest": ".kernel/out/products/digital-marketing/package/20260512-143000/artifact-manifest.json",
  "sourceRef": "main",
  "plannedAt": "2026-05-12T14:30:00Z",
  "releaseNotes": "Initial production release",
  "artifacts": [
    {
      "surface": "backend-api",
      "type": "container-image",
      "image": "ghatana/digital-marketing-api:1.0.0",
      "digest": "sha256:abc123...",
      "buildArtifact": "products/digital-marketing/dm-api/build/libs/dm-api.jar"
    },
    {
      "surface": "web",
      "type": "static-web-image",
      "image": "ghatana/digital-marketing-web:1.0.0",
      "digest": "sha256:def456...",
      "buildArtifact": "products/digital-marketing/ui/dist"
    }
  ],
  "versioning": {
    "strategy": "semantic",
    "major": 1,
    "minor": 0,
    "patch": 0,
    "preRelease": null,
    "buildMetadata": null
  }
}
```

### Release Manifest

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "version": "1.0.0",
  "releaseId": "release-20260512-143000",
  "packageArtifactManifest": ".kernel/out/products/digital-marketing/package/20260512-143000/artifact-manifest.json",
  "releasedAt": "2026-05-12T14:30:00Z",
  "sourceRef": "main",
  "releaseNotes": "Initial production release",
  "artifacts": [
    {
      "surface": "backend-api",
      "type": "container-image",
      "image": "ghatana/digital-marketing-api:1.0.0",
      "digest": "sha256:abc123..."
    },
    {
      "surface": "web",
      "type": "static-web-image",
      "image": "ghatana/digital-marketing-web:1.0.0",
      "digest": "sha256:def456..."
    }
  ],
  "signatures": [
    {
      "artifact": "ghatana/digital-marketing-api:1.0.0",
      "signature": "RSA-SHA256:...",
      "signedAt": "2026-05-12T14:31:00Z",
      "signedBy": "kernel-release-signer"
    }
  ],
  "metadata": {
    "gitCommit": "abc123def456",
    "gitBranch": "main",
    "buildId": "build-20260512-143000",
    "packageId": "package-20260512-143000"
  }
}
```

## Promotion Schema

### Promotion Plan

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "promotionId": "promote-20260512-150000",
  "fromEnvironment": "staging",
  "toEnvironment": "prod",
  "releaseArtifactManifest": ".kernel/out/products/digital-marketing/release/20260512-143000/artifact-manifest.json",
  "releaseVersion": "1.0.0",
  "plannedAt": "2026-05-12T15:00:00Z",
  "gates": [
    {
      "name": "artifact-validation",
      "status": "pending"
    },
    {
      "name": "environment-validation",
      "status": "pending"
    },
    {
      "name": "security",
      "status": "pending"
    },
    {
      "name": "privacy",
      "status": "pending"
    },
    {
      "name": "license-policy",
      "status": "pending"
    },
    {
      "name": "conformance",
      "status": "pending"
    },
    {
      "name": "e2e",
      "status": "pending"
    },
    {
      "name": "performance",
      "status": "pending"
    },
    {
      "name": "rollback-plan",
      "status": "pending"
    },
    {
      "name": "approval",
      "status": "pending"
    }
  ],
  "rollbackPlan": {
    "strategy": "previous-artifact",
    "fromDeploymentId": "deploy-20260512-140000",
    "fromEnvironment": "staging",
    "previousDeploymentId": "deploy-20260510-120000",
    "previousEnvironment": "prod",
    "previousReleaseVersion": "0.9.0",
    "previousImages": {
      "backend-api": "ghatana/digital-marketing-api:0.9.0",
      "web": "ghatana/digital-marketing-web:0.9.0"
    },
    "rollbackSteps": [
      {
        "service": "digital-marketing-api",
        "action": "rollback-image",
        "toImage": "ghatana/digital-marketing-api:0.9.0",
        "namespace": "digital-marketing-prod"
      },
      {
        "service": "digital-marketing-web",
        "action": "rollback-image",
        "toImage": "ghatana/digital-marketing-web:0.9.0",
        "namespace": "digital-marketing-prod"
      },
      {
        "action": "verify-health",
        "timeoutSeconds": 300
      }
    ]
  }
}
```

### Promotion Result

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "promotionId": "promote-20260512-150000",
  "fromEnvironment": "staging",
  "toEnvironment": "prod",
  "releaseVersion": "1.0.0",
  "status": "succeeded",
  "startedAt": "2026-05-12T15:00:00Z",
  "completedAt": "2026-05-12T15:10:00Z",
  "durationMs": 600000,
  "gates": [
    {
      "name": "artifact-validation",
      "status": "passed",
      "checkedAt": "2026-05-12T15:00:30Z"
    },
    {
      "name": "environment-validation",
      "status": "passed",
      "checkedAt": "2026-05-12T15:00:45Z"
    },
    {
      "name": "security",
      "status": "passed",
      "checkedAt": "2026-05-12T15:01:00Z"
    },
    {
      "name": "privacy",
      "status": "passed",
      "checkedAt": "2026-05-12T15:01:30Z"
    },
    {
      "name": "license-policy",
      "status": "passed",
      "checkedAt": "2026-05-12T15:02:00Z"
    },
    {
      "name": "conformance",
      "status": "passed",
      "checkedAt": "2026-05-12T15:03:00Z"
    },
    {
      "name": "e2e",
      "status": "passed",
      "checkedAt": "2026-05-12T15:05:00Z"
    },
    {
      "name": "performance",
      "status": "passed",
      "checkedAt": "2026-05-12T15:06:00Z"
    },
    {
      "name": "rollback-plan",
      "status": "passed",
      "checkedAt": "2026-05-12T15:07:00Z"
    },
    {
      "name": "approval",
      "status": "passed",
      "checkedAt": "2026-05-12T15:08:00Z",
      "approvedBy": "john.doe@example.com"
    }
  ],
  "deployment": {
    "deploymentId": "deploy-20260512-150500",
    "deployedAt": "2026-05-12T15:05:00Z",
    "status": "succeeded"
  },
  "failure": null
}
```

### Promotion Manifest

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "promotionId": "promote-20260512-150000",
  "fromEnvironment": "staging",
  "toEnvironment": "prod",
  "releaseVersion": "1.0.0",
  "promotedAt": "2026-05-12T15:10:00Z",
  "releaseArtifactManifest": ".kernel/out/products/digital-marketing/release/20260512-143000/artifact-manifest.json",
  "deploymentManifest": ".kernel/out/products/digital-marketing/deploy/prod/20260512-150500/deployment-manifest.json",
  "gates": [
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
  "rollbackPlan": {
    "strategy": "previous-artifact",
    "previousDeploymentId": "deploy-20260510-120000",
    "previousReleaseVersion": "0.9.0"
  }
}
```

## Versioning Strategies

### Semantic Versioning

**Pattern:** MAJOR.MINOR.PATCH

**Rules:**
- MAJOR: Incompatible API changes
- MINOR: Backwards-compatible functionality additions
- PATCH: Backwards-compatible bug fixes

**Example:** 1.0.0, 1.1.0, 1.1.1, 2.0.0

**Configuration:**
```json
{
  "versioning": {
    "strategy": "semantic",
    "autoIncrement": "patch"
  }
}
```

---

### Calendar Versioning

**Pattern:** YYYY.MM.DD

**Rules:**
- Year.Month.Day
- Multiple releases per day use build metadata

**Example:** 2026.05.12, 2026.05.12+1, 2026.06.01

**Configuration:**
```json
{
  "versioning": {
    "strategy": "calendar",
    "timezone": "UTC"
  }
}
```

---

### Sequential Versioning

**Pattern:** Incrementing integer

**Rules:**
- Start at 1
- Increment by 1 for each release

**Example:** 1, 2, 3, 4

**Configuration:**
```json
{
  "versioning": {
    "strategy": "sequential",
    "startAt": 1
  }
}
```

---

## Promotion Gates

### artifact-validation

**Purpose:** Validate release artifacts exist and are intact.

**Checks:**
- All artifacts from release manifest exist
- Image digests match
- Artifact fingerprints match

**Failure:** Fail promotion

---

### environment-validation

**Purpose:** Validate target environment is configured and accessible.

**Checks:**
- Environment config exists
- Deployment target is accessible
- Secrets provider is accessible
- Config provider is accessible

**Failure:** Fail promotion

---

### security

**Purpose:** Validate security requirements are met.

**Checks:**
- No known vulnerabilities in dependencies
- No secrets in artifacts
- Security scans pass
- Container images are from trusted sources

**Failure:** Fail promotion

---

### privacy

**Purpose:** Validate privacy requirements are met.

**Checks:**
- Data classification is correct
- PII handling is compliant
- Privacy policies are enforced
- Audit trails are enabled

**Failure:** Fail promotion

---

### license-policy

**Purpose:** Validate license compliance.

**Checks:**
- All dependencies have allowed licenses
- License headers are present
- License attribution is complete

**Failure:** Fail promotion

---

### conformance

**Purpose:** Validate product conformance to platform standards.

**Checks:**
- Product boundary rules pass
- Kernel purity rules pass
- Platform conformance checks pass

**Failure:** Fail promotion

---

### e2e

**Purpose:** Validate end-to-end functionality.

**Checks:**
- Critical user journeys work
- Integration points work
- Data flows work

**Failure:** Fail promotion

---

### performance

**Purpose:** Validate performance requirements are met.

**Checks:**
- Response times within SLA
- Throughput meets targets
- Resource usage within limits
- No performance regressions

**Failure:** Fail promotion

---

### rollback-plan

**Purpose:** Validate rollback plan exists and is executable.

**Checks:**
- Previous deployment exists
- Previous artifacts are accessible
- Rollback steps are valid
- Rollback can be executed within SLA

**Failure:** Fail promotion

---

### approval

**Purpose:** Require explicit human approval.

**Checks:**
- Approval granted by authorized user
- Approval reason documented
- Approval timestamp recorded

**Failure:** Fail promotion

---

## Promotion Policies

Promotion policies are defined in `config/deployment/promotion-policies.json`:

```json
{
  "version": "1.0.0",
  "policies": {
    "linear": {
      "description": "Linear promotion path through environments",
      "path": ["local", "dev", "staging", "prod"],
      "allowSkip": false,
      "requireApprovalFrom": ["staging"]
    },
    "flexible": {
      "description": "Flexible promotion with skips allowed",
      "path": ["local", "dev", "staging", "prod"],
      "allowSkip": true,
      "requireApprovalFrom": ["staging"]
    },
    "direct-to-prod": {
      "description": "Direct promotion to production (emergency only)",
      "path": ["local", "prod"],
      "allowSkip": true,
      "requireApprovalFrom": ["prod"],
      "requireJustification": true
    }
  }
}
```

## Rollback Plans

### previous-artifact

**Description:** Rollback to the previous artifact/image.

**Plan:**
1. Identify previous deployment in target environment
2. Extract previous artifact/image references
3. Generate rollback steps to redeploy previous artifacts
4. Validate rollback steps are executable
5. Store rollback plan with promotion

**Execution:**
1. Execute rollback steps
2. Verify health after rollback
3. Emit rollback manifest

---

### blue-green

**Description:** Rollback by switching traffic back to previous environment.

**Plan:**
1. Maintain blue and green environments
2. Deploy new version to green
3. Switch traffic from blue to green
4. Keep blue as rollback target

**Execution:**
1. Switch traffic back to blue
2. Verify health after switch
3. Emit rollback manifest

---

### canary

**Description:** Rollback by reverting traffic percentage.

**Plan:**
1. Gradually increase traffic to new version
2. Monitor metrics and health
3. Keep old version as rollback target

**Execution:**
1. Revert traffic to old version
2. Verify health after revert
3. Emit rollback manifest

---

## Release CLI

```bash
# Create release
kernel product release digital-marketing --version 1.0.0

# Create release with auto-version
kernel product release digital-marketing --auto-version

# Create release with release notes
kernel product release digital-marketing --version 1.0.0 --notes "Initial production release"

# Promote to environment
kernel product promote digital-marketing --from staging --to prod

# Promote with specific release
kernel product promote digital-marketing --from staging --to prod --release 1.0.0

# Dry-run promotion
kernel product promote digital-marketing --from staging --to prod --dry-run

# Rollback promotion
kernel product rollback digital-marketing --env prod
```

## Release Output

Release writes to:
```
.kernel/out/products/<productId>/release/<timestamp>/
  release-plan.json
  release-result.json
  release-manifest.json
  signatures/
  logs/
```

Promotion writes to:
```
.kernel/out/products/<productId>/promote/<from>-<to>/<timestamp>/
  promotion-plan.json
  promotion-result.json
  promotion-manifest.json
  rollback-plan.json
  logs/
```

## Related Contracts

- [Product Lifecycle Contract](PRODUCT_LIFECYCLE_CONTRACT.md)
- [Product Artifact Contract](PRODUCT_ARTIFACT_CONTRACT.md)
- [Product Environment Contract](PRODUCT_ENVIRONMENT_CONTRACT.md)
- [Product Deployment Contract](PRODUCT_DEPLOYMENT_CONTRACT.md)
