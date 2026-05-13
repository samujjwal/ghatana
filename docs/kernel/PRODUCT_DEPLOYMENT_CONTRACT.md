# Product Deployment Contract

This document defines deployment contract enforcement for lifecycle-enabled products.

## Source Of Truth

For enabled products, deployment contract is driven by `kernel-product.yaml` `deployment.local`.

Required fields:

- `target`
- `composeFile`
- `envExampleFile`
- `healthChecks`

Optional but validated when present:

- `envFile` (warning if missing locally)

## Registry Alignment

`node scripts/check-product-deployment-contracts.mjs` enforces that:

- Enabled products declare deployment targets in `config/canonical-product-registry.json`.
- `deployment.local.target` exists in registry target list and in `config/deployment-targets.json`.
- Enabled products support `local` environment in registry metadata.

## Health Check Coverage

`deployment.local.healthChecks` must include all declared surfaces from `kernel-product.yaml`.

Example:

```yaml
deployment:
  local:
    target: compose-local
    composeFile: products/digital-marketing/deploy/local.compose.yaml
    envFile: products/digital-marketing/deploy/local.env
    envExampleFile: products/digital-marketing/deploy/local.env.example
    healthChecks:
      backend-api:
        type: http
        url: http://localhost:${DMOS_API_PORT:-8080}/health
      web:
        type: http
        url: http://localhost:${DMOS_WEB_PORT:-5173}/
```

## Exclusion Policy

Excluded products (`yappc`, `data-cloud`) are outside lifecycle deployment enforcement and must not be treated as lifecycle-enabled products.
