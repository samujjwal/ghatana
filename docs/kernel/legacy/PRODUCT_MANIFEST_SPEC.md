# Product Manifest Spec

Product manifests are Kernel-shaped, product-owned declarations. The canonical envelope is independent of YAML or JSON serialization.

## Required Envelope

Every manifest-backed product must declare:

| Field | Purpose |
| --- | --- |
| `schemaVersion` | Manifest contract version. Current value is `1.0.0`. |
| `id` | Must match the canonical registry key. |
| `product` | Product identity when present; must match the registry key. |
| `version` | Product manifest version. |
| `kernelCapabilitiesConsumed` | Kernel capabilities used by the product. |
| `policyActions` | Canonical or product-namespaced actions. |
| `policyResources` | Canonical or product-namespaced resources. |
| `pluginsConsumed` | Kernel/platform plugins consumed by the product. |
| `bridgesConsumed` | Product bridge/adapters consumed by the product. |
| `domainPacksProvided` | Product-owned domain packs registered with Kernel. |
| `uiSurfaces` | Implemented UI surfaces, not planned surfaces. |
| `runtimeServices` | Runtime services or adapters that provide capability evidence. |
| `dataSensitivity` | Data classification used by conformance and governance checks. |

Product-specific extension data belongs under `productExtensions`. Do not add unversioned top-level product-only fields unless the manifest schema has been updated.

## Lifecycle Configuration

Products with lifecycle enabled should declare lifecycle configuration in a separate `kernel-product.yaml` file (not in the product manifest). This keeps lifecycle concerns separate from capability consumption.

The `kernel-product.yaml` file declares:

| Field | Purpose |
| --- | --- |
| `productId` | Product identifier (must match registry) |
| `lifecycleProfile` | Which lifecycle profile to use (e.g., `standard-web-api-product`) |
| `surfaces` | Surface definitions with adapter mappings |
| `phases` | Phase-specific overrides for surfaces and execution mode |

Example:
```yaml
productId: digital-marketing
lifecycleProfile: standard-web-api-product

surfaces:
  backend-api:
    adapter: gradle-java-service
    gradleModule: :products:digital-marketing:dm-api
    devTask: runDmosApiServer
    buildTask: build
  web:
    adapter: pnpm-vite-react
    packagePath: products/digital-marketing/ui/package.json

phases:
  dev:
    defaultSurfaces: [backend-api, web]
    mode: parallel
```

The product registry entry should reference this file via `lifecycleConfigPath`.

See [PRODUCT_LIFECYCLE_CONTRACT.md](PRODUCT_LIFECYCLE_CONTRACT.md) for the full lifecycle contract and available profiles.

## Registry Coupling

If `conformance.manifest` is `true`, the registry entry must also declare non-null `manifestPath`, `manifestFormat`, and `buildFile`.

Products without a manifest must explicitly set `conformance.manifest` to `false` and carry an exemption reason in registry metadata.

## Policy Vocabulary

Shared actions may use canonical names. Product-specific actions and resources must use a registered namespace such as `finance:settle`, `digital-marketing:launch`, or `flashit:moments`.

Wildcard policy resources are not allowed as authorization vocabulary. Model hierarchy explicitly with product-scoped resource names.
