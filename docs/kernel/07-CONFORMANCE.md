# Conformance

## Overview

Kernel conformance ensures products adhere to platform contracts, lifecycle requirements, and governance policies.

## Conformance Checks

### Product Registry Conformance

- **Check**: Product registry schema validation
- **Script**: `scripts/check-product-registry-contracts.mjs`
- **Schema**: `config/canonical-product-registry-schema.json`
- **Authoritative Source**: `config/canonical-product-registry.json`

### Lifecycle Conformance

- **Check**: Product lifecycle contract validation
- **Script**: `scripts/check-product-lifecycle-contracts.mjs`
- **Validates**: kernel-product.yaml, lifecycle.local.yaml, runtime-profile.yaml

### Toolchain Adapter Conformance

- **Check**: Toolchain adapter contract validation
- **Script**: `scripts/check-toolchain-adapter-contracts.mjs`
- **Validates**: Adapter implementations match interface

### Artifact Conformance

- **Check**: Product artifact contract validation
- **Script**: `scripts/check-product-artifact-contracts.mjs`
- **Validates**: Artifact manifests match declared artifacts

### Deployment Conformance

- **Check**: Product deployment contract validation
- **Script**: `scripts/check-product-deployment-contracts.mjs`
- **Validates**: Deployment manifests match deployment targets

### Plugin Conformance

- **Check**: Plugin registry and binding validation
- **Script**: `scripts/check-plugin-contracts.mjs` (to be added)
- **Validates**: Plugin bindings match plugin registry

## Conformance Fixtures

Products declare expected conformance fixtures in `conformance/` directory:

- `lifecycle-fixtures.json` - Expected lifecycle steps and adapters
- `artifact-fixtures.json` - Expected artifacts by surface
- `deployment-fixtures.json` - Expected deployment steps and adapters

## Running Conformance Checks

```bash
# Run all conformance checks
pnpm check:kernel-platform-lifecycle

# Run individual checks
node scripts/check-product-lifecycle-contracts.mjs
node scripts/check-toolchain-adapter-contracts.mjs
node scripts/check-product-artifact-contracts.mjs
node scripts/check-product-deployment-contracts.mjs
```

## Conformance Failures

Conformance failures are explicit and actionable:

- Missing required fields
- Invalid schema
- Mismatched fixtures
- Unregistered plugins
- Missing lifecycle bindings

Products with conformance failures cannot proceed through lifecycle gates.
