# Product Artifact Contract

**Version:** 1.0.0
**Status:** Implementation-Ready
**Last Updated:** 2026-05-12

## Purpose

This contract defines the schema and lifecycle of artifacts produced and consumed by Kernel lifecycle phases. Artifacts are the tangible outputs of build, package, release, and deploy phases that are tracked, validated, and promoted across environments.

## Design Principles

1. **Explicit artifact contracts**: All artifacts must be declared and validated.
2. **Fingerprinting**: All artifacts are fingerprinted (SHA256) for reproducibility.
3. **Immutable artifacts**: Once produced, artifacts are never modified.
4. **Traceability**: Artifacts trace back to source commit, phase, and environment.
5. **Fail closed**: Missing or invalid artifacts fail the consuming phase.

## Artifact Types

### Build Artifacts

#### jvm-service

**Description:** Compiled Java service as a JAR file.

**Produced by:** build phase (via GradleJavaServiceAdapter)

**Consumed by:** package phase

**Schema:**
```json
{
  "surface": "backend-api",
  "type": "jvm-service",
  "path": "products/digital-marketing/dm-api/build/libs/dm-api.jar",
  "fingerprint": "sha256:abc123...",
  "producedBy": "gradle-java-service",
  "metadata": {
    "gradleModule": ":products:digital-marketing:dm-api",
    "javaVersion": "21",
    "mainClass": "com.ghatana.digitalmarketing.DmosApiServer"
  }
}
```

**Validation:**
- File exists
- SHA256 fingerprint matches
- Valid JAR format
- Main class declared

---

#### static-web-bundle

**Description:** Built React/web application as static files.

**Produced by:** build phase (via PnpmViteReactAdapter)

**Consumed by:** package phase

**Schema:**
```json
{
  "surface": "web",
  "type": "static-web-bundle",
  "path": "products/digital-marketing/ui/dist",
  "fingerprint": "sha256:def456...",
  "producedBy": "pnpm-vite-react",
  "metadata": {
    "packagePath": "products/digital-marketing/ui/package.json",
    "bundleSize": 1024000,
    "entryPoints": ["index.html"]
  }
}
```

**Validation:**
- Directory exists
- index.html exists
- SHA256 fingerprint of key files matches
- Bundle size within limits

---

#### test-report

**Description:** Test execution results (JUnit XML, JSON, etc.).

**Produced by:** test phase

**Consumed by:** build phase (for conformance gates)

**Schema:**
```json
{
  "surface": "backend-api",
  "type": "test-report",
  "path": "products/digital-marketing/dm-api/build/reports/tests/test",
  "fingerprint": "sha256:ghi789...",
  "producedBy": "gradle-java-service",
  "metadata": {
    "format": "junit-xml",
    "tests": 150,
    "failures": 0,
    "skipped": 2,
    "durationMs": 45000
  }
}
```

**Validation:**
- File exists
- Valid format (JUnit XML, JSON)
- Test counts match expectations

---

#### coverage-report

**Description:** Code coverage results (Jacoco, LCOV, etc.).

**Produced by:** test phase

**Consumed by:** build phase (for coverage gates)

**Schema:**
```json
{
  "surface": "backend-api",
  "type": "coverage-report",
  "path": "products/digital-marketing/dm-api/build/reports/jacoco/test",
  "fingerprint": "sha256:jkl012...",
  "producedBy": "gradle-java-service",
  "metadata": {
    "format": "jacoco-xml",
    "lineCoverage": 0.85,
    "branchCoverage": 0.78,
    "instructionCoverage": 0.82
  }
}
```

**Validation:**
- File exists
- Valid format
- Coverage meets thresholds

---

### Package Artifacts

#### container-image

**Description:** Docker/OCI container image.

**Produced by:** package phase (via DockerBuildxAdapter)

**Consumed by:** deploy phase

**Schema:**
```json
{
  "surface": "backend-api",
  "type": "container-image",
  "image": "ghatana/digital-marketing-api:1.0.0",
  "digest": "sha256:abc123...",
  "producedBy": "docker-buildx",
  "metadata": {
    "dockerfile": "config/docker/templates/product-java-service.Dockerfile.template",
    "baseImage": "eclipse-temurin:21-jre-jammy",
    "platforms": ["linux/amd64", "linux/arm64"],
    "sizeBytes": 256000000
  }
}
```

**Validation:**
- Image exists in registry
- Digest matches
- Platform(s) match target
- Size within limits

---

#### static-web-image

**Description:** Nginx container serving static web bundle.

**Produced by:** package phase (via DockerBuildxAdapter)

**Consumed by:** deploy phase

**Schema:**
```json
{
  "surface": "web",
  "type": "static-web-image",
  "image": "ghatana/digital-marketing-web:1.0.0",
  "digest": "sha256:def456...",
  "producedBy": "docker-buildx",
  "metadata": {
    "dockerfile": "config/docker/templates/product-node-web.Dockerfile.template",
    "baseImage": "nginx:alpine",
    "bundlePath": "/usr/share/nginx/html",
    "sizeBytes": 64000000
  }
}
```

**Validation:**
- Image exists in registry
- Digest matches
- Bundle path correct
- Size within limits

---

### Deployment Artifacts

#### deployment-manifest

**Description:** Manifest of deployed services and their configuration.

**Produced by:** deploy phase

**Consumed by:** verify, rollback phases

**Schema:**
```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "environment": "local",
  "deploymentId": "deploy-20260512-143000",
  "sourceRef": "main",
  "releaseArtifactManifest": ".kernel/out/products/digital-marketing/release/20260512-143000/artifact-manifest.json",
  "deployedAt": "2026-05-12T14:30:00Z",
  "services": [
    {
      "name": "digital-marketing-api",
      "surface": "backend-api",
      "image": "ghatana/digital-marketing-api:1.0.0",
      "digest": "sha256:abc123...",
      "replicas": 1,
      "ports": [
        {
          "containerPort": 8080,
          "hostPort": 8080,
          "protocol": "tcp"
        }
      ],
      "healthCheck": {
        "type": "http",
        "path": "/health",
        "port": 8080,
        "intervalSeconds": 30
      }
    },
    {
      "name": "digital-marketing-web",
      "surface": "web",
      "image": "ghatana/digital-marketing-web:1.0.0",
      "digest": "sha256:def456...",
      "replicas": 1,
      "ports": [
        {
          "containerPort": 80,
          "hostPort": 5173,
          "protocol": "tcp"
        }
      ],
      "healthCheck": {
        "type": "http",
        "path": "/",
        "port": 80,
        "intervalSeconds": 30
      }
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

**Validation:**
- All services deployed
- Health checks passing
- Rollback plan exists
- Image digests match release manifest

---

#### health-check-report

**Description:** Results of health checks performed after deployment.

**Produced by:** deploy/verify phases

**Consumed by:** promotion gates

**Schema:**
```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "environment": "local",
  "deploymentId": "deploy-20260512-143000",
  "checkedAt": "2026-05-12T14:32:00Z",
  "overallStatus": "healthy",
  "services": [
    {
      "name": "digital-marketing-api",
      "surface": "backend-api",
      "status": "healthy",
      "checks": [
        {
          "name": "http-health",
          "type": "http",
          "url": "http://localhost:8080/health",
          "status": "pass",
          "responseTimeMs": 45,
          "statusCode": 200
        }
      ]
    },
    {
      "name": "digital-marketing-web",
      "surface": "web",
      "status": "healthy",
      "checks": [
        {
          "name": "http-health",
          "type": "http",
          "url": "http://localhost:5173/",
          "status": "pass",
          "responseTimeMs": 20,
          "statusCode": 200
        }
      ]
    }
  ]
}
```

**Validation:**
- All checks passing
- Response times within SLA
- Status codes correct

---

### Release Artifacts

#### release-manifest

**Description:** Manifest of a release with versioning and metadata.

**Produced by:** release phase

**Consumed by:** deploy, promote phases

**Schema:**
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
      "signedAt": "2026-05-12T14:31:00Z"
    }
  ]
}
```

**Validation:**
- Version format valid
- All artifacts from package manifest
- Signatures valid (if required)

---

## Artifact Manifest Schema

### Build Artifact Manifest

Produced by the build phase at `.kernel/out/products/<productId>/build/<timestamp>/artifact-manifest.json`:

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "phase": "build",
  "sourceRef": "main",
  "buildId": "build-20260512-143000",
  "builtAt": "2026-05-12T14:30:00Z",
  "artifacts": [
    {
      "surface": "backend-api",
      "type": "jvm-service",
      "path": "products/digital-marketing/dm-api/build/libs/dm-api.jar",
      "fingerprint": "sha256:abc123...",
      "producedBy": "gradle-java-service",
      "metadata": {
        "gradleModule": ":products:digital-marketing:dm-api",
        "javaVersion": "21"
      }
    },
    {
      "surface": "web",
      "type": "static-web-bundle",
      "path": "products/digital-marketing/ui/dist",
      "fingerprint": "sha256:def456...",
      "producedBy": "pnpm-vite-react",
      "metadata": {
        "packagePath": "products/digital-marketing/ui/package.json",
        "bundleSize": 1024000
      }
    }
  ],
  "testResults": [
    {
      "surface": "backend-api",
      "type": "test-report",
      "path": "products/digital-marketing/dm-api/build/reports/tests/test",
      "metadata": {
        "tests": 150,
        "failures": 0,
        "durationMs": 45000
      }
    }
  ],
  "coverageResults": [
    {
      "surface": "backend-api",
      "type": "coverage-report",
      "path": "products/digital-marketing/dm-api/build/reports/jacoco/test",
      "metadata": {
        "lineCoverage": 0.85
      }
    }
  ]
}
```

### Package Artifact Manifest

Produced by the package phase at `.kernel/out/products/<productId>/package/<timestamp>/artifact-manifest.json`:

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "phase": "package",
  "buildArtifactManifest": ".kernel/out/products/digital-marketing/build/20260512-143000/artifact-manifest.json",
  "packageId": "package-20260512-143500",
  "packagedAt": "2026-05-12T14:35:00Z",
  "artifacts": [
    {
      "surface": "backend-api",
      "type": "container-image",
      "image": "ghatana/digital-marketing-api:1.0.0",
      "digest": "sha256:abc123...",
      "producedBy": "docker-buildx",
      "metadata": {
        "dockerfile": "config/docker/templates/product-java-service.Dockerfile.template",
        "sizeBytes": 256000000
      }
    },
    {
      "surface": "web",
      "type": "static-web-image",
      "image": "ghatana/digital-marketing-web:1.0.0",
      "digest": "sha256:def456...",
      "producedBy": "docker-buildx",
      "metadata": {
        "dockerfile": "config/docker/templates/product-node-web.Dockerfile.template",
        "sizeBytes": 64000000
      }
    }
  ]
}
```

## Artifact Fingerprinting

All artifacts are fingerprinted using SHA256:

```typescript
async function fingerprintArtifact(path: string): Promise<string> {
  const hash = crypto.createHash('sha256');
  const stream = fs.createReadStream(path);
  for await (const chunk of stream) {
    hash.update(chunk);
  }
  return `sha256:${hash.digest('hex')}`;
}
```

For directories (e.g., static web bundles):
- Fingerprint is computed over the concatenation of all file contents
- File list is sorted for deterministic ordering
- Excludes node_modules, .git, and other build artifacts

## Artifact Storage

Artifacts are stored in:
- **Local builds:** `.kernel/out/products/<productId>/<phase>/<timestamp>/`
- **Remote storage:** Configurable (e.g., S3, GCS, Artifactory) for production

Storage configuration is per-environment:
```json
{
  "local": {
    "storage": "local",
    "path": ".kernel/out"
  },
  "prod": {
    "storage": "s3",
    "bucket": "ghatana-artifacts",
    "prefix": "products/"
  }
}
```

## Artifact Validation

Artifacts are validated at:
1. **Production time:** Adapter validates outputs after execution
2. **Consumption time:** Consuming phase validates artifacts before use
3. **Promotion time:** Promotion gates validate artifact integrity

Validation checks:
- File/image exists
- Fingerprint matches
- Format valid (JAR, container image, etc.)
- Metadata complete
- Size within limits

## Artifact Promotion

Artifacts are promoted across environments:
- **build → package:** Build artifacts consumed by package phase
- **package → release:** Package artifacts versioned in release
- **release → deploy:** Release artifacts deployed to environment
- **deploy → promote:** Deployment artifacts promoted to next environment

Promotion requires:
- Artifact manifest from previous phase
- All gates passed
- Rollback plan generated (for non-local environments)

## Artifact Retention

Retention policy per environment:
- **local:** 7 days
- **dev:** 30 days
- **staging:** 90 days
- **prod:** 1 year (or per compliance requirements)

Retention is configurable per product.

## Related Contracts

- [Product Lifecycle Contract](PRODUCT_LIFECYCLE_CONTRACT.md)
- [Product Toolchain Adapter Spec](PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md)
- [Product Environment Contract](PRODUCT_ENVIRONMENT_CONTRACT.md)
- [Product Deployment Contract](PRODUCT_DEPLOYMENT_CONTRACT.md)
- [Product Release Promotion Contract](PRODUCT_RELEASE_PROMOTION_CONTRACT.md)
