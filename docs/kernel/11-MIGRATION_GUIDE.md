# Migration Guide

## Overview

This guide helps you migrate existing products to the Kernel lifecycle platform.

## Migration Prerequisites

- Product registered in canonical product registry
- Lifecycle profile selected
- Toolchain adapters available
- Runtime profile configured
- Health checks defined
- Conformance fixtures created

## Migration Steps

### 1. Register Product

Add your product to `config/canonical-product-registry.json`:

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

### 2. Create Product Structure

Create the required files:

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

### 3. Configure Lifecycle

Create `kernel-product.yaml` with your lifecycle profile and surfaces.

### 4. Configure Local Environment

Create `lifecycle.local.yaml` with your local environment configuration.

### 5. Configure Runtime Profile

Create `runtime/runtime-profile.yaml` with your runtime configuration.

### 6. Define Health Checks

Create `deploy/health-checks.json` with your health check definitions.

### 7. Create Conformance Fixtures

Create conformance fixtures to validate lifecycle behavior.

### 8. Run Generator

Generate derived artifacts:

```bash
node scripts/generate-product-registry-artifacts.mjs
```

### 9. Run Conformance Checks

Validate your migration:

```bash
pnpm check:kernel-platform-lifecycle
```

### 10. Test Lifecycle Commands

Test the lifecycle commands:

```bash
pnpm dev:your-product
pnpm build:your-product
pnpm test:your-product
pnpm deploy:local:your-product
```

## Migration Order

Migrate products in this order:

## Product Readiness Matrix

This matrix tracks the lifecycle migration readiness of each Ghatana product. Update this table as products progress through migration stages.

| Product | Lifecycle Status | Profile | Surfaces | Package Adapter | Deploy Adapter | Health Checks | Notes |
|---------|-----------------|---------|----------|-----------------|----------------|---------------|-------|
| **Digital Marketing** | ✅ `enabled` | `standard-web-api-product` | `backend-api`, `web` | `docker-buildx` | `compose-local` | `/health/ready`, `/health/live` | Pilot product — fully configured |
| **PHR** | 🔶 `partial` | `standard-web-api-product` | `backend-api`, `web` | — | — | — | Needs `kernel-product.yaml`, deploy config |
| **Finance** | 🔶 `partial` | `standard-web-api-product` | `backend-api`, `web` | — | — | — | Needs `kernel-product.yaml`, deploy config |
| **FlashIt** | 🔶 `partial` | `standard-web-api-product` | `backend-api`, `web` | — | — | — | Needs `kernel-product.yaml`, deploy config |
| **Aura / AEP** | 🔶 `partial` | — | `backend-api` | — | — | — | Non-standard profile; needs assessment |
| **DCMAAR** | 🔶 `partial` | — | `backend-api` | — | — | — | Data platform; needs custom profile |
| **Data Cloud** | 🔶 `partial` | — | `backend-api` | — | — | — | Shared data service; needs assessment |
| **YAPPC** | 🔶 `partial` | — | `backend-api` | — | — | — | Multi-module platform; needs custom profile |

### Readiness Legend

| Status | Meaning |
|--------|---------|
| ✅ `enabled` | Fully configured and lifecycle-managed; CI/CD enforces all phases |
| 🔶 `partial` | Registered in registry; `kernel-product.yaml` partially complete or missing |
| 📋 `planned` | Registered; migration not yet started |
| ❌ `disabled` | Excluded from lifecycle management (see `config/kernel-lifecycle-exclusions.json`) |

### Next Steps per Product

1. **PHR** — Create `products/phr/kernel-product.yaml`, add Dockerfiles, configure `compose-local` for local deploy
2. **Finance** — Create `products/finance/kernel-product.yaml`, add Dockerfiles, configure `compose-local` for local deploy
3. **FlashIt** — Create `products/flashit/kernel-product.yaml`, add Dockerfiles, configure `compose-local` for local deploy
4. **Aura/AEP** — Define a custom lifecycle profile; document multi-surface orchestration
5. **DCMAAR, Data Cloud** — Assess data platform requirements; may need a `data-platform-product` lifecycle profile
6. **YAPPC** — Assess multi-module toolchain requirements; may need a `multi-module-platform` lifecycle profile

1. digital-marketing
2. phr
3. flashit
4. finance
5. data-cloud
6. yappc
7. tutorputor
8. audio-video
9. dcmaar
10. security-gateway

## Common Issues

### Adapter Not Found
- Ensure the adapter is registered in `config/toolchain-adapter-registry.json`
- Check that the adapter package is installed

### Conformance Failures
- Review conformance fixture definitions
- Ensure all required files are created
- Check that lifecycle configuration matches registry entry

### Build Failures
- Verify toolchain adapter configuration
- Check that build commands work outside of Kernel
- Review build logs for specific errors

### Deployment Failures
- Verify deployment target configuration
- Check health check definitions
- Ensure deployment adapter is available

## Rollback

If migration fails, you can rollback by:
1. Removing lifecycle configuration from registry entry
2. Removing product-specific lifecycle files
3. Regenerating derived artifacts
4. Using existing build/deploy scripts
