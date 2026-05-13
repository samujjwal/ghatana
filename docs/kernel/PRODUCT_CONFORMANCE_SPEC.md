# Product Conformance Spec

Product conformance proves that products consume Kernel contracts through declared, typed, and generated interfaces.

## Required Checks

Run these checks after registry, manifest, shell, route, or conformance changes:

- `pnpm check:product-workspace-registration`
- `pnpm check:product-ci-matrices`
- `pnpm check:product-manifest-contracts`
- `pnpm check:product-package-metadata`
- `pnpm check:kernel-boundaries`
- `pnpm check:observability-conformance`
- `pnpm check:bridge-compliance`
- `pnpm check:affected-products`
- `pnpm check:capability-matrix`

Use product-local typecheck and test commands for touched products, for example `pnpm --dir products/phr/apps/web exec tsc --noEmit`.

## Conformance Evidence

Conformance evidence must be executable or schema-backed where possible. Token-only checks are temporary compatibility gates and should be replaced with typed loaders, manifest validation, generated artifacts, or behavior-level contract tests.

Current generated evidence includes:

- `docs/generated/CAPABILITY_MATRIX.md`
- `docs/kernel/PRODUCT_KERNEL_AUDIT_PROGRESS.md`
- `config/observability/product-observability-flows.json`
- registry-declared bridge adapter/test evidence

## Failure Policy

Conformance scripts must fail closed. Missing registry entries, missing manifests, invalid namespaces, stale generated artifacts, or unknown product kinds must produce explicit violations rather than silently skipping products.
