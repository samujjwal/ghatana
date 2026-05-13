# Product Developer Guide

## Overview

This guide is for product developers who want to build products on the Kernel platform.

## Quick Start

1. Register your product in the canonical registry
2. Create your product structure
3. Configure lifecycle profile
4. Run conformance checks
5. Build and deploy

## Product Registration

Add your product to `config/canonical-product-registry.json`. The schema is defined in `config/canonical-product-registry-schema.json`.

```json
{
  "registry": {
    "your-product": {
      "id": "your-product",
      "name": "Your Product",
      "kind": "business-product",
      "lifecycleProfile": "standard-web-api-product",
      "lifecycle": {
        "enabled": true,
        "configPath": "products/your-product/lifecycle.local.yaml"
      },
      "surfaces": [
        {
          "type": "backend-api",
          "implementationStatus": "implemented",
          "adapter": "gradle-java-service",
          "module": ":products:your-product:api"
        },
        {
          "type": "web",
          "implementationStatus": "implemented",
          "adapter": "pnpm-vite-react",
          "packagePath": "products/your-product/ui"
        }
      ]
    }
  }
}
```

## Product Structure

Create the following files:

```
products/your-product/
├── kernel-product.yaml
├── lifecycle.local.yaml
├── runtime/
│   └── runtime-profile.yaml
├── deploy/
│   └── health-checks.json
└── conformance/
    ├── lifecycle-fixtures.json
    ├── artifact-fixtures.json
    └── deployment-fixtures.json
```

## Lifecycle Configuration

Create `kernel-product.yaml`:

```yaml
lifecycleProfile: standard-web-api-product
surfaces:
  backend-api:
    adapter: gradle-java-service
    module: :products:your-product:api
  web:
    adapter: pnpm-vite-react
    module: products/your-product/ui
phases:
  dev:
    mode: parallel
  build:
    mode: sequential
```

Create `lifecycle.local.yaml`:

```yaml
environment: local
surfaces:
  backend-api:
    port: 8080
    env:
      DATABASE_URL: jdbc:postgresql://localhost:5432/your-product
  web:
    port: 3000
    env:
      API_URL: http://localhost:8080
deployment:
  adapter: compose-local
  composeFile: deploy/local.compose.yaml
```

## Runtime Profile

Create `runtime/runtime-profile.yaml`:

```yaml
surfaces:
  backend-api:
    jvmOptions:
      -Xmx1g
      -Xms512m
    env:
      LOG_LEVEL: info
  web:
    env:
      NODE_ENV: production
```

## Health Checks

Create `deploy/health-checks.json`:

```json
{
  "surfaces": {
    "backend-api": {
      "type": "http",
      "path": "/health",
      "port": 8080
    },
    "web": {
      "type": "http",
      "path": "/",
      "port": 3000
    }
  }
}
```

## Conformance Fixtures

Create `conformance/lifecycle-fixtures.json`:

```json
{
  "expectedSteps": {
    "dev": ["start-dev-server"],
    "build": ["compile", "test", "package"]
  }
}
```

Create `conformance/artifact-fixtures.json`:

```json
{
  "expectedArtifacts": {
    "backend-api": ["jar"],
    "web": ["static-web-bundle"]
  }
}
```

Create `conformance/deployment-fixtures.json`:

```json
{
  "expectedSteps": {
    "deploy": ["apply-deployment"]
  }
}
```

## Running Commands

After registration, run the generator to create derived artifacts:

```bash
node scripts/generate-product-registry-artifacts.mjs
```

Use the lifecycle commands:

```bash
# Plan a phase
pnpm kernel plan your-product build

# Run dev
pnpm dev:your-product

# Build
pnpm build:your-product

# Test
pnpm test:your-product

# Deploy locally
pnpm deploy:local:your-product
```

## Conformance Checks

Run conformance checks:

```bash
pnpm check:kernel-platform-lifecycle
```

## CI/CD

The generator automatically creates CI matrix entries. Your product will be included in CI pipelines based on your registry entry.
