# Data Cloud

Data Cloud is Ghatana's governed operational data fabric. It unifies trusted operational data, metadata, schemas, durable storage-plane events, governed context, intelligence substrate, policy evidence, and pluggable persistence.

The product is organized by planes, not capability areas. Data Cloud is the single customer-facing product. The Action Plane runtime (formerly AEP) is integrated within Data Cloud under `products/data-cloud/planes/action`, providing governed automation, pipelines, patterns, agents, reviews, runs, and learning.

## Canonical Docs

| Document | Purpose |
| --- | --- |
| `docs/architecture/PLANE_ARCHITECTURE.md` | Canonical plane architecture, target module layout, dependency rules, and migration sequence |
| `docs/product/01_data_cloud_unified_vision_market_positioning.md` | Product vision and positioning |
| `docs/product/02_data_cloud_unified_detailed_architecture.md` | Detailed architecture and runtime model |
| `docs/product/03_data_cloud_unified_high_level_design.md` | Navigation, UX, API, and migration design |
| `docs/README.md` | Documentation index |
| `contracts/README.md` | Contract Plane ownership and validation |

## Plane Model

```text
Experience Plane
Contract Plane
Runtime Truth Plane
Data Plane
Event Plane
Context Plane
Intelligence Plane
Governance Plane
Action Plane
Operations Plane
```

Default product navigation should remain outcome-first:

```text
Home
Data
Events
Query
Pipelines
Trust
Operations
```

Role-disclosed or contextual surfaces include Context, Insights, Reviews, Patterns, Agents, Learning, Connectors, Plugins, Settings, and Contracts.

## Current Module Map

The active repository layout is plane-based.

| Current Path | Target Plane / Area |
| --- | --- |
| `planes/shared-spi/` | shared plane SPI |
| `planes/data/entity/` | Data Plane |
| `planes/event/core/`, `planes/event/store/` | Event Plane |
| `planes/context/` | Context Plane placeholder and ownership boundary |
| `planes/intelligence/analytics/`, `planes/intelligence/feature-ingest/` | Intelligence Plane |
| `planes/governance/core/` | Governance Plane |
| `planes/operations/config/` | Operations Plane |
| `planes/action/*` | Action Plane runtime implementation providing governed automation, pipelines, patterns, agents, reviews, runs, and learning |
| `delivery/api/`, `delivery/launcher/`, `delivery/runtime-composition/`, `delivery/sdk/`, `delivery/ui/` | Delivery and Experience surfaces |
| `extensions/connectors/`, `extensions/plugins/`, `extensions/agent-catalog/`, `extensions/agent-registry/`, `extensions/kernel-bridge/` | Extension modules |
| `deploy/helm/`, `deploy/k8s/`, `deploy/terraform/` | Deployment assets |

## Runtime Truth

Runtime truth reports whether planes and surfaces are live, degraded, disabled, preview, unavailable, or misconfigured.

Target surface:

```text
GET /api/v1/surfaces
```

DC-P1.12: Compatibility endpoint /api/v1/capabilities has been removed; all callers should use canonical /api/v1/surfaces.

## Contracts

Canonical contracts live in:

```text
products/data-cloud/contracts/openapi/data-cloud.yaml
products/data-cloud/contracts/openapi/action-plane.yaml
```

Compatibility contract:

```text
products/data-cloud/contracts/openapi/aep.yaml
```

`aep.yaml` and `action-plane.yaml` must remain equivalent until the AEP-named contract is retired.

## Local Development

Run the backend in the local profile:

```bash
DATACLOUD_PROFILE=local \
DATACLOUD_HTTP_ENABLED=true \
./gradlew :products:data-cloud:delivery:launcher:runLauncher
```

Run backend and UI together:

```bash
bash products/data-cloud/scripts/run-local-stack.sh
```

Default HTTP port:

```text
8082
```

## Architecture Rules

```text
Data/Event/Context/Governance/Intelligence planes must not import AEP or Action Plane implementation internals.
AEP-related integration may consume public contracts/SPI from Data, Event, Context, Governance, and Operations.
Delivery runtime composition may compose all planes.
Contracts must not depend on runtime implementation modules.
UI must use generated clients and frontend adapters, not backend internals.
```

## Module Classification and Promotion

Data Cloud modules are classified as either **release-blocking** or **advisory**:

- **Release-blocking modules**: Must pass all checks before any release. These are production-critical surfaces.
- **Advisory modules**: Compiled and tested in CI but not blocking releases. Used only for explicitly non-production surfaces.

### Promotion Process

When an advisory module becomes a production surface, it must be promoted to release-blocking status:

1. **Update module classification**: Edit `scripts/list-data-cloud-active-modules.mjs` to move the module from `advisoryModules` to `releaseBlockingModules`.
2. **Verify release-blocking checks**: Ensure the module has comprehensive tests, linting, and security checks configured.
3. **Update CI workflows**: The release workflow automatically includes all release-blocking modules in strict checks.
4. **Document the change**: Add a note to the implementation tracker or changelog explaining why the module was promoted.
5. **Regenerate evidence**: Run `pnpm check:data-cloud-active-module-evidence` to update the evidence file.

### Current Advisory Modules

There are currently no active advisory modules in Data Cloud. API contract and integration suites are release-blocking and must pass for release gates.

Data Cloud UI readiness is also release-blocking for route/runtime/OpenAPI parity:

- `check:route-manifest` (backend registry/runtime parity + generated artifact drift)
- `docs:routes:check` (route truth matrix drift)
- `check:api-types` (OpenAPI-generated UI types drift)
- Focused parity tests (`routeTruthMatrix`, `routeInventory`, `routeSurfaceClassification`, `analyticsOpenApiAlignment`, `openApiDrivenMocks`)

See `scripts/list-data-cloud-active-modules.mjs` for the authoritative source of truth.

## Shared Platform Review

Shared platform modules should remain shared only when they are genuinely cross-product infrastructure. If a module mainly exists to bridge Data-Cloud storage and AEP adaptive event processing, keep the stable SPI boundary explicit and avoid moving AEP-owned semantics into Data-Cloud.
