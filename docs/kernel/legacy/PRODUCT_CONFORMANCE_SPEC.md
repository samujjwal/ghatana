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

## Lifecycle Conformance

Products with lifecycle enabled must pass additional conformance checks:

- `pnpm check:product-lifecycle-contracts` - validates lifecycle profile and phase configuration
- `pnpm check:toolchain-adapter-contracts` - validates adapter declarations and compatibility
- `pnpm check:product-artifact-contracts` - validates artifact declarations and fingerprints
- `pnpm check:product-environment-contracts` - validates environment bindings and deployment targets
- `pnpm check:product-deployment-contracts` - validates deployment manifests and health checks

See [PRODUCT_LIFECYCLE_CONTRACT.md](PRODUCT_LIFECYCLE_CONTRACT.md) for lifecycle phase contracts.

## Related Docs

- [PRODUCT_LIFECYCLE_CONTRACT.md](PRODUCT_LIFECYCLE_CONTRACT.md) - lifecycle phase contracts
- [PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md](PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md) - toolchain adapter specification
- [PRODUCT_ARTIFACT_CONTRACT.md](PRODUCT_ARTIFACT_CONTRACT.md) - artifact contracts
- [PRODUCT_MANIFEST_SPEC.md](PRODUCT_MANIFEST_SPEC.md) - manifest schema
