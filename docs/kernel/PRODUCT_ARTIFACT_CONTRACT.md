# Product Artifact Contract

This document defines the enforced artifact contract for lifecycle-enabled products.

## Source Of Truth

For enabled products, artifact expectations are read from `kernel-product.yaml`:

- `surfaces.<surface>.expectedOutputs.<phase>`
- `artifacts.<phase>.<surface>`

Registry-only artifact declarations are not authoritative for enabled lifecycle execution.

## Validation Rules

`node scripts/check-product-artifact-contracts.mjs` enforces:

- Enabled product has a valid `lifecycleConfigPath`.
- `kernel-product.yaml` declares `artifacts`.
- Every `artifacts.<phase>.<surface>` entry maps to a declared surface.
- `type` and `packaging` are valid.
- `required` is boolean when present.
- Required non-container artifacts must define `paths`.
- Surfaces should define `expectedOutputs` (warning when missing).

## Adapter Output Enforcement

Adapter execution validates outputs against `expectedOutputs` for the current phase.

- `gradle-java-service` validates configured outputs, including wildcard paths.
- `pnpm-vite-react` validates configured output paths directly.
- Missing required outputs result in execution failure.

## Artifact Types

Supported artifact types are controlled by script validation and include:

- `jvm-service`, `jvm-library`, `node-service`, `static-web-bundle`, `container-image`, `mobile-bundle`, `sdk-package`, `domain-pack`, `test-report`, `coverage-report`.
