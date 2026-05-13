# Product Lifecycle

## Overview

The Kernel Product Lifecycle Platform defines a standard lifecycle for all products: dev → validate → test → build → package → deploy → verify → promote → rollback.

## Lifecycle Phases

### dev
- **Purpose**: Start development environment for a product
- **Operations**: Start dev servers, watch for changes
- **Surfaces**: All surfaces in parallel

### validate
- **Purpose**: Validate product structure, contracts, and conformance without building
- **Operations**: Registry validation, manifest validation, lifecycle contract validation
- **Gates**: registry-validation, manifest-validation, lifecycle-contract-validation

### test
- **Purpose**: Run tests for a product
- **Operations**: Unit tests, integration tests, e2e tests
- **Surfaces**: All surfaces with tests

### build
- **Purpose**: Build a product
- **Operations**: Compile, bundle, create artifacts
- **Surfaces**: Sequential with dependencies

### package
- **Purpose**: Package a product for deployment
- **Operations**: Create deployment artifacts, Docker images
- **Gates**: artifact-validation, security-scan

### deploy
- **Purpose**: Deploy a product to an environment
- **Operations**: Apply deployment configuration, health checks
- **Environments**: local, dev, staging, prod
- **Gates**: deployment-gates, health-checks

### verify
- **Purpose**: Verify a deployment is healthy
- **Operations**: Health checks, smoke tests, metrics validation
- **Gates**: health-check-gates, observability-gates

### promote
- **Purpose**: Promote a product between environments
- **Operations**: Artifact promotion, configuration updates
- **Gates**: promotion-policies, approval-gates

### rollback
- **Purpose**: Rollback a product to a previous version
- **Operations**: Rollback execution, verification
- **Gates**: rollback-gates

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
