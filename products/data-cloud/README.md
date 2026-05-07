# Data Cloud

Data Cloud is Ghatana's AI-native operational data fabric. It unifies trusted operational data, durable events, governed context, intelligence, policy, and action in one product.

The product is organized by planes, not capability areas. AEP is the current runtime implementation behind the Action Plane; it is not a separate customer-facing product boundary.

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
| `planes/action/*` | Action Plane runtime implementation powered by AEP |
| `delivery/api/`, `delivery/launcher/`, `delivery/runtime-composition/`, `delivery/sdk/`, `delivery/ui/` | Delivery and Experience surfaces |
| `extensions/connectors/`, `extensions/plugins/`, `extensions/agent-catalog/`, `extensions/agent-registry/`, `extensions/kernel-bridge/` | Extension modules |
| `deploy/helm/`, `deploy/k8s/`, `deploy/terraform/` | Deployment assets |

## Runtime Truth

Runtime truth reports whether planes and surfaces are live, degraded, disabled, preview, unavailable, or misconfigured.

Target surface:

```text
GET /api/v1/surfaces
```

Compatibility endpoint:

```text
GET /api/v1/capabilities
```

The compatibility endpoint should remain only until callers migrate to Runtime Truth Registry naming.

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
Data/Event/Context/Governance/Intelligence planes must not import Action Plane implementation internals.
Action Plane may consume public contracts/SPI from Data, Event, Context, Governance, and Operations.
Delivery runtime composition may compose all planes.
Contracts must not depend on runtime implementation modules.
UI must use generated clients and frontend adapters, not backend internals.
```

## Shared Platform Review

Shared platform modules should remain shared only when they are genuinely cross-product infrastructure. If a platform module mainly exists because Data Cloud and AEP were previously separate products, migrate or split the Data Cloud/Action Plane-specific behavior into `products/data-cloud`.
