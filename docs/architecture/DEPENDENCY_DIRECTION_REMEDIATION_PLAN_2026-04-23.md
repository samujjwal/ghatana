# Dependency Direction Remediation Plan

**Date:** 2026-04-23
**Source of truth:** `./gradlew validateDependencyDirection --console=plain --no-configuration-cache`
**Status:** Architecture gate implemented; 21 runtime violations remain

## What Is Already Complete

The root architecture gate is no longer a placeholder.

The following checks now execute and fail on violations:
- `validateNoCircularDependencies`
- `validateModuleBoundaries`
- `validateDependencyDirection`
- `validateArchitecture` wired into root `check`

Current validated state:
- Project dependency cycles: `0`
- Platform or shared-services depending on product runtime modules: `0`
- Remaining runtime dependency-direction violations: `21`

## Remediation Principle

Do not expand the allowlist to absorb product internals.

Only these dependency shapes are acceptable long term:
- product -> platform
- product -> platform-kernel
- product -> platform-plugins
- product -> shared-services
- product -> contract-only shared surfaces with explicit ownership

Any product module that is consumed by other products must be one of:
- moved to `platform/*` if it is product-agnostic
- moved to `shared-services/*` if it is an owned shared runtime
- reduced to a narrow contract/SPI surface with explicit documentation and ownership

## Violation Clusters

### Cluster 1: Data Cloud shared surface still leaks product implementation

Violations:
- `:products:data-cloud:planes:shared-spi -> :products:data-cloud:planes:data:entity`
- `:products:data-cloud:planes:action:* -> :products:data-cloud:extensions:agent-registry`
- `:products:data-cloud:planes:action:server -> :products:data-cloud:delivery:runtime-composition`
- `:products:yappc:core:knowledge-graph -> :products:data-cloud:delivery:runtime-composition`
- `:products:yappc:core:knowledge-graph -> :products:data-cloud:extensions:plugins`

Assessment:
- `:products:data-cloud:planes:shared-spi` is intended to be the shared contract surface.
- It currently depends on `platform-entity` because SPI interfaces reference `EntityInterface` and `QuerySpecInterface` from product-owned code.
- `agent-registry`, `platform-launcher`, and `platform-plugins` are runtime implementation modules and should not be consumed cross-product.

Required long-term action:
1. Move or extract contract-only entity/query types out of `platform-entity` into a shared contract surface.
2. Replace cross-product consumption of `agent-registry` and `platform-launcher` with a stable client or contract package.
3. Keep `platform-launcher` and `platform-plugins` product-private unless explicitly rehomed.

Recommended implementation order:
1. Break `spi -> platform-entity`
2. Extract `agent-registry` client/contracts
3. Remove `platform-launcher` cross-product consumption

### Cluster 2: AEP runtime surfaces are being consumed as shared platform capabilities

Violations:
- `:products:virtual-org:* -> :products:data-cloud:planes:action:agent-runtime`
- `:products:virtual-org:modules:integration -> :products:data-cloud:planes:action:engine`
- `:products:yappc:core:agents:* -> :products:data-cloud:planes:action:agent-runtime`
- `:products:yappc:core:agents:runtime -> :products:data-cloud:planes:action:engine`
- `:products:yappc:core:services-lifecycle -> :products:data-cloud:planes:action:agent-runtime`
- `:products:yappc:core:services-lifecycle -> :products:data-cloud:planes:action:engine`
- `:products:yappc:core:services-lifecycle -> :products:data-cloud:planes:action:orchestrator`
- `:products:yappc:core:yappc-infrastructure -> :products:data-cloud:planes:action:agent-runtime`
- `:products:yappc:core:yappc-infrastructure -> :products:data-cloud:planes:action:engine`
- `:products:yappc:core:yappc-infrastructure -> :products:data-cloud:planes:action:registry`

Assessment:
- These dependencies indicate AEP runtime has become a de facto shared platform layer.
- If those APIs are truly reusable, they belong in `platform/java/*` or a dedicated shared service.
- If they are AEP-specific, downstream products need adapter ports instead of direct runtime coupling.

Required long-term action:
1. Identify which AEP types are reusable contracts versus AEP runtime internals.
2. Extract reusable contracts to platform/shared modules.
3. Replace direct product-to-AEP runtime dependencies with adapters against those extracted contracts.

Recommended implementation order:
1. Extract operator/runtime contracts consumed by YAPPC and Virtual Org
2. Replace direct `aep-engine` and `aep-registry` dependencies
3. Reassess whether `aep-agent-runtime` belongs under platform agent-core

### Cluster 3: Direct product-to-product coupling outside approved shared surfaces

Violations:
- `:products:software-org:engine:boot -> :products:virtual-org:modules:framework`
- `:products:software-org:engine:modules:domain-model -> :products:virtual-org:modules:framework`

Assessment:
- This is direct product-to-product coupling with no contract boundary.
- These dependencies should move to platform or shared-services, or be inverted through an owner-specific integration module.

Required long-term action:
1. Identify the concrete types imported from Virtual Org framework.
2. Extract them into a shared contract/module if they are truly reused.
3. Otherwise move the integration code under a single owning product boundary.

## Allowed Shared Product APIs

The current gate intentionally allows only these product-owned shared surfaces:
- `:products:data-cloud:planes:shared-spi`
- `:products:data-cloud:planes:action:operator-contracts`

This allowlist is temporary and must remain small.

Any new entry must satisfy all of the following:
- contract-only surface
- no runtime implementation logic
- explicit owner
- explicit migration plan toward platform or shared-services if reuse grows

## Execution Order

1. Break the `data-cloud:spi` dependency on `platform-entity`
2. Extract AEP and Data Cloud shared contracts used by other products
3. Remove remaining cross-product runtime dependencies product by product
4. Shrink the shared-product API allowlist
5. Make `validateArchitecture` green in normal CI runs

## Validation Command

```bash
./gradlew validateArchitecture --console=plain --no-configuration-cache
```

The architecture work for this slice is complete only when that command passes without allowlisting additional product internals.
