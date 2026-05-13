# Kernel Consumption Guide

This guide defines how products consume Kernel capabilities without moving product behavior into the platform.

## Product Onboarding Flow

1. Add or update the product entry in `config/canonical-product-registry.json`.
2. Declare product-owned manifest values in the product manifest.
3. Run `node scripts/generate-product-registry-artifacts.mjs`.
4. Use `pnpm product <productId> <task> [surface]` for product-scoped build, test, and dev tasks.
5. Use `node scripts/resolve-affected-products.mjs --json <changed-files>` or `pnpm resolve:affected-products` for CI impact analysis.

The registry is the editable infrastructure source of truth. Generated workspace, product-shape, and script artifacts must not become separate product metadata authorities.

## Frontend Consumption

Product UIs should consume:

- `@ghatana/product-shell` for shell layout, route access evaluation, entitlement hydration, and shell chrome primitives.
- `@ghatana/design-system` for async states and dashboard composition primitives.
- Product-local route contracts for route metadata, with React element construction kept in UI boundary files.

Products own page components, route labels, personas, tiers, cards, and domain actions. Kernel owns the shape and shared evaluators.

## Backend Consumption

Products should declare Kernel capability usage in their manifests and expose adapters or bridge contracts only where product behavior crosses the platform boundary.

Bridge and conformance declarations belong in registry/manifest metadata. Compliance scripts must load those declarations instead of hardcoding product names.

## Generated Evidence

Use `pnpm generate:capability-matrix` to refresh product capability consumption evidence and `pnpm check:capability-matrix` to verify the generated matrix is current.

## Lifecycle Consumption

For lifecycle phase orchestration (dev, validate, test, build, package, deploy, verify, promote, rollback), products should:

1. Declare a `lifecycleProfile` in the product registry entry
2. Provide `kernel-product.yaml` with surface-to-adapter mappings
3. Use `kernel product <phase> <productId>` commands instead of direct tool invocation

See [PRODUCT_LIFECYCLE_CONTRACT.md](PRODUCT_LIFECYCLE_CONTRACT.md) for the full lifecycle contract.

## Related Docs

- [PRODUCT_LIFECYCLE_CONTRACT.md](PRODUCT_LIFECYCLE_CONTRACT.md) - lifecycle phase contracts
- [PRODUCT_MANIFEST_SPEC.md](PRODUCT_MANIFEST_SPEC.md) - manifest schema
