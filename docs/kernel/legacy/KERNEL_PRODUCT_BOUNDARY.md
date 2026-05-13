# Kernel Product Boundary

This document is the canonical boundary contract between Kernel-owned platform capabilities and product-owned domain systems.

## Ownership

| Concern | Owner | Source of truth |
| --- | --- | --- |
| Product registration, kind, status, build ownership, CI inclusion | Kernel | `config/canonical-product-registry.json` |
| Workspace/package shape generated from product registration | Kernel | `scripts/generate-product-registry-artifacts.mjs` |
| Kernel capability vocabulary and policy namespaces | Kernel | `config/kernel-product-capability-registry.json` |
| Product manifest values and domain-pack declarations | Product | `products/*/domain-pack-manifest.yaml`, product manifest JSON |
| Product route declarations and page elements | Product | Product route contract files and generated route manifests |
| Shared shell, route entitlement shape, role evaluator, UI primitives | Kernel | `platform/typescript/product-shell`, `platform/typescript/design-system` |
| Product-specific pages, workflows, policies, personas, cards | Product | `products/*` |

## Boundary Rules

Kernel code must stay product-neutral. Platform modules must not import from `products/**`, and products must consume Kernel through public package exports or declared bridge/adapters.

Products may declare product-specific vocabulary only through registered namespaces. Shared policy actions such as `read`, `write`, `delete`, `export`, and `download` remain canonical; product actions and resources must be product-scoped.

Registry entries must distinguish `business-product`, `platform-provider`, `shared-service`, `domain-pack`, and `demo/example`. CI and conformance gates must filter by `kind`, status, and the relevant conformance flags rather than by hardcoded product lists.

## Generated Artifacts

The canonical registry generates or validates:

- `config/product-shape.json`
- `pnpm-workspace.yaml`
- root product task scripts
- product CI matrix expectations
- generated capability and audit docs

Run `node scripts/generate-product-registry-artifacts.mjs` after registry changes, then run the product registration and CI checks listed in `PRODUCT_CONFORMANCE_SPEC.md`.

## Related Docs

- `docs/kernel/KERNEL_CONSUMPTION_GUIDE.md`
- `docs/kernel/PRODUCT_MANIFEST_SPEC.md`
- `docs/kernel/PRODUCT_CONFORMANCE_SPEC.md`
- `docs/kernel/PRODUCT_LIFECYCLE_CONTRACT.md` - lifecycle phase contracts
- `docs/kernel/PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md` - toolchain adapter specification
- `docs/kernel/PRODUCT_ARTIFACT_CONTRACT.md` - artifact contracts
- `docs/kernel/PRODUCT_ENVIRONMENT_CONTRACT.md` - environment contracts
- `docs/kernel/PRODUCT_DEPLOYMENT_CONTRACT.md` - deployment contracts
- `docs/kernel/PRODUCT_RELEASE_PROMOTION_CONTRACT.md` - release and promotion contracts
- `docs/generated/CAPABILITY_MATRIX.md`
