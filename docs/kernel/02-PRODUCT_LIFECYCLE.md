# Kernel Product Lifecycle

## Overview

The Kernel Product Lifecycle defines a standard lifecycle for all ProductUnits: dev → validate → test → build → package → deploy → verify → promote → rollback.

This is the **Kernel Product Lifecycle**, not the YAPPC Creator Lifecycle. YAPPC is a consumer of Kernel lifecycle contracts for visibility and control-plane operations, not the lifecycle execution engine itself.

## Lifecycle Phases

## Maturity

| Phase | Current maturity |
|-------|------------------|
| plan | Pilot executable for Digital Marketing |
| validate/test/build | Pilot executable for Digital Marketing |
| package/deploy/verify | Pilot path; requires manifest completeness and required output validation |
| promote/rollback | Target/partial |

### dev
- **Purpose**: Start development environment for a product
- **Operations**: Start dev servers, watch for changes
- **Surfaces**: All surfaces in parallel
- **Owner**: Surface adapters

### validate
- **Purpose**: Validate product structure, contracts, and conformance without building
- **Operations**: Registry validation, manifest validation, lifecycle contract validation
- **Gates**: registry-validation, manifest-validation, lifecycle-contract-validation
- **Owner**: Surface adapters

### test
- **Purpose**: Run tests for a product
- **Operations**: Unit tests, integration tests, e2e tests
- **Surfaces**: All surfaces with tests
- **Owner**: Surface adapters

### build
- **Purpose**: Build a product
- **Operations**: Compile, bundle, create artifacts
- **Surfaces**: Sequential with dependencies
- **Owner**: Surface adapters

### package
- **Purpose**: Package a product for deployment
- **Operations**: Create deployment artifacts, Docker images
- **Gates**: artifact-validation, security-scan
- **Owner**: Package adapters

### deploy
- **Purpose**: Deploy a product to an environment
- **Operations**: Apply deployment configuration, health checks
- **Environments**: local, dev, staging, prod
- **Gates**: deployment-gates, health-checks
- **Owner**: Deployment adapters

### verify
- **Purpose**: Verify a deployment is healthy
- **Operations**: Health checks, smoke tests, metrics validation
- **Gates**: health-check-gates, observability-gates
- **Owner**: Deployment adapters

### promote
- **Purpose**: Promote a product between environments
- **Operations**: Artifact promotion, configuration updates
- **Gates**: promotion-policies, approval-gates
- **Owner**: Release providers

### rollback
- **Purpose**: Rollback a product to a previous version
- **Operations**: Rollback execution, verification
- **Gates**: rollback-gates
- **Owner**: Deployment adapters

## Lifecycle Profiles

### standard-web-api-product
- Surfaces: backend-api, web
- Dev: Parallel
- Build: Sequential (backend-api → web)
- Suitable for: Most web applications with backend APIs

### mobile-plus-api-product
- Surfaces: backend-api, web, mobile-ios, mobile-android
- Dev: Parallel
- Build: Sequential with mobile builds
- Suitable for: Mobile applications with backend

### backend-only-java-service
- Surfaces: backend-api, worker
- Dev: Parallel
- Build: Sequential
- Suitable for: Backend-only services

### platform-provider-product
- Surfaces: backend-api, web, operator
- Dev: Parallel
- Build: Sequential
- Suitable for: Platform products that provide capabilities to others

## Configuration

Products declare their lifecycle configuration in `kernel-product.yaml`:

```yaml
lifecycleProfile: standard-web-api-product
surfaces:
  backend-api:
    adapter: gradle-java-service
    module: :products:product-name:api
  web:
    adapter: pnpm-vite-react
    module: products/product-name/ui
phases:
  dev:
    mode: parallel
  build:
    mode: sequential
```

## Required Phase Truth

Each lifecycle phase produces and consumes specific truth files to ensure reproducibility, auditability, and observability:

- **lifecycle-plan.json**: The planned lifecycle execution, including phases, gates, and dependencies
- **lifecycle-result.json**: The execution result for each phase with status, duration, and outputs
- **gate-result-manifest.json**: Detailed results for each gate evaluation including pass/fail status and evidence
- **artifact-manifest.json**: Complete inventory of artifacts produced during build and package phases
- **deployment-manifest.json**: Deployment configuration and applied changes for each environment
- **verify-health-report.json**: Health check results and metrics from verify phase
- **lifecycle-health-snapshot.json**: Aggregated health status across all lifecycle phases and gates
