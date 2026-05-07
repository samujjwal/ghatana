# Data Cloud Plane Architecture

**Status:** Canonical target architecture  
**Scope:** Product positioning, module hierarchy, runtime boundaries, and migration rules  
**Supersedes:** Capability-area language in older Data Cloud and AEP planning docs

Data Cloud is one product organized as interoperable planes. A plane is a stable product and architecture boundary with a clear purpose, ownership model, public surfaces, and implementation modules.

Do not use "capability area" as the product hierarchy. Use:

```text
Planes = product architecture
Surfaces = user/API entry points
Modules = implementation
Runtime truth = live/degraded/unavailable state of planes and surfaces
AEP = implementation behind the Action Plane
```

## Positioning

Data Cloud is an AI-native operational data fabric where trusted data, durable events, governed context, intelligence, policy, and action run together.

It should not be positioned as "Data Cloud plus AEP." AEP is not a separate customer-facing product boundary inside this repository. AEP is the runtime implementation that powers the Action Plane.

```text
Data Cloud product
  Action Plane
    AEP runtime implementation
```

## Plane Model

| Plane | Purpose | Primary Surfaces | Implementation Boundary |
| --- | --- | --- | --- |
| Experience Plane | Product UI, generated clients, docs, scripts, and operator workflows | Home, shell, SDKs, docs | `delivery/ui`, `delivery/sdk`, docs |
| Contract Plane | Stable public API, event, schema, and SDK contracts | OpenAPI, schemas, SDK generation | `contracts` |
| Runtime Truth Plane | Live/degraded/unavailable state of planes, surfaces, dependencies, and runtime profiles | health, readiness, surface state, dependency probes | runtime truth registry and probes |
| Data Plane | Operational entities, collections, schema, storage, quality, history, and queryable records | Data, entities, collection APIs | `planes/data` |
| Event Plane | Durable event log, replay, streaming, offsets, and event-backed integration | Events, replay, streams | `planes/event` |
| Context Plane | Lineage, provenance, freshness, semantic context, memory, RAG, and retrieval grounding | Context details, lineage, memory, RAG | `planes/context` |
| Intelligence Plane | Schema inference, query assistance, recommendations, anomaly detection, classification, summarization, and ML substrate | Query assist, insights, recommendations | `planes/intelligence` |
| Governance Plane | Tenant isolation, authorization, policy, privacy, retention, redaction, legal hold, audit, and compliance evidence | Trust, audit, policy, privacy actions | `planes/governance` |
| Action Plane | Pipelines, patterns, agents, runs, human review, learning loops, workflow execution, rollback, and governed automation | Pipelines, agents, reviews, runs, learning | `planes/action` powered by AEP runtime |
| Operations Plane | Metrics, traces, logs, alerts, backup, restore, compaction, readiness, and operational diagnostics | Operations, alerts, health | `planes/operations` |

## Navigation Model

The UI should be outcome-first. It can be backed by planes, but users should not need to understand implementation placement.

Default navigation:

```text
Home
Data
Events
Query
Pipelines
Trust
Operations
```

Contextual or role-disclosed surfaces:

```text
Context
Insights
Reviews
Patterns
Agents
Learning
Connectors
Plugins
Settings
Contracts
```

Navigation-to-plane mapping:

| Navigation Surface | Primary Plane | Secondary Planes |
| --- | --- | --- |
| Home | Experience | Runtime Truth, Operations |
| Data | Data | Context, Governance, Event |
| Events | Event | Operations, Action |
| Query | Data | Intelligence, Context, Governance |
| Pipelines | Action | Event, Governance, Operations |
| Trust | Governance | Context, Event, Action |
| Operations | Operations | Runtime Truth |
| Insights | Intelligence | Operations, Action |
| Agents | Action | Context, Governance |
| Reviews | Action | Governance |
| Connectors | Event | Data, Operations |
| Plugins | Action | Event, Data, Operations |

## Target Repository Layout

```text
products/data-cloud/
├─ docs/
│  ├─ product/
│  ├─ architecture/
│  ├─ api/
│  ├─ operations/
│  ├─ migration/
│  └─ audits/
├─ contracts/
│  ├─ openapi/
│  │  ├─ data-cloud.yaml
│  │  └─ action-plane.yaml
│  └─ schemas/
├─ planes/
│  ├─ data/
│  ├─ event/
│  ├─ context/
│  ├─ intelligence/
│  ├─ governance/
│  ├─ action/
│  └─ operations/
├─ delivery/
│  ├─ api/
│  ├─ launcher/
│  ├─ sdk/
│  └─ ui/
├─ extensions/
│  ├─ connectors/
│  ├─ plugins/
│  ├─ agent-catalog/
│  └─ feature-store-ingest/
├─ deploy/
│  ├─ helm/
│  ├─ k8s/
│  └─ terraform/
└─ integration-tests/
```

## Current-To-Target Module Map

| Current Path | Target Path | Plane | Notes |
| --- | --- | --- | --- |
| `spi/` | `planes/shared-spi/` | Contract | Stable plane SPI and plugin contracts |
| `platform-entity/` | `planes/data/entity/` | Data | Entity model, schema, storage contracts |
| `platform-event/` | `planes/event/core/` | Event | Event primitives and event log contracts |
| `platform-event-store/` | `planes/event/store/` | Event | Event store providers |
| `platform-analytics/` | `planes/intelligence/analytics/` | Intelligence | Query, reports, analytics |
| `platform-governance/` | `planes/governance/core/` | Governance | Governance and policy support |
| `platform-config/` | `planes/operations/config/` | Operations | Runtime configuration model |
| `aep/operator-contracts/` | `planes/action/operator-contracts/` | Action | Internal action operator contracts |
| `aep/engine/` | `planes/action/engine/` | Action | Pattern, pipeline, and action execution engine |
| `aep/orchestrator/` | `planes/action/orchestrator/` | Action | Workflow and agentic orchestration |
| `aep/agent-runtime/` | `planes/action/agent-runtime/` | Action | Agent execution runtime |
| `aep/event-cloud-bridge/` | `planes/action/event-bridge/` | Action/Event | Bridge from Event Plane to Action Plane |
| `aep/server/` | `planes/action/server/` | Action/Delivery | AEP runtime server kept with Action Plane implementation during this migration |
| `platform-api/` | `delivery/api/` | Delivery | Data Cloud API handlers and route adapters |
| `platform-launcher/` | `delivery/runtime-composition/` | Delivery | Runtime composition across planes |
| `launcher/` | `delivery/launcher/` | Delivery | Process entry point and transport handlers |
| `sdk/` | `delivery/sdk/` | Experience/Contract | Generated clients |
| `ui/` | `delivery/ui/` | Experience | Product UI |
| `platform-plugins/` | `extensions/plugins/` | Extensions | Plugin implementations |
| `agent-registry/` | `extensions/agent-registry/` | Extensions | Data Cloud-backed agent registry extension and read model |
| `agent-catalog/` | `extensions/agent-catalog/` | Extensions | Agent metadata catalog |
| `feature-store-ingest/` | `planes/intelligence/feature-ingest/` | Intelligence | Feature ingestion and ML substrate |
| `kernel-bridge/` | `extensions/kernel-bridge/` | Extensions | Kernel integration bridge |
| `helm/`, `k8s/`, `terraform/` | `deploy/*` | Operations | Deployment assets |

## Dependency Rules

```text
delivery/runtime-composition may compose all planes.
planes/action may depend on public contracts/SPI from data, event, context, governance, and operations.
planes/data, planes/event, planes/context, and planes/governance must not depend on planes/action internals.
delivery/ui must depend on generated clients and frontend adapters, not backend internals.
extensions must depend on contracts/SPI, not launcher internals.
contracts must not depend on implementation modules.
```

Allowed direction:

```text
Experience -> Contract -> Planes
Action -> Data/Event/Context/Governance public contracts
Planes -> Operations telemetry contracts
Delivery composition -> all planes
```

Forbidden direction:

```text
Data/Event/Context/Governance -> Action implementation
Contracts -> runtime implementation
SDK -> launcher or server internals
UI -> backend implementation modules
```

## Shared Platform Review Rules

Some platform modules became shared because Data Cloud and AEP were separate products. Re-evaluate them during migration.

Keep in platform when:

```text
The abstraction is used by three or more unrelated products.
The module contains generic primitives with no Data Cloud plane semantics.
The module is infrastructure rather than product behavior.
```

Move or split into Data Cloud when:

```text
The module exists mainly for Data Cloud plus AEP integration.
The types describe Data Cloud planes or Action Plane semantics.
The implementation is product behavior, not cross-product infrastructure.
```

Initial candidates:

| Platform Module | Recommendation |
| --- | --- |
| `platform:java:agent-core` | Keep minimal generic interfaces in platform; move action-specific runtime, memory, dispatch, and review types into `planes/action`. |
| `platform:java:workflow` | Move Data Cloud pipeline semantics into `planes/action`; keep only generic workflow primitives if other products use them. |
| `platform:java:messaging` | Keep generic messaging abstractions in platform; move Data Cloud event routing and Action Plane bridges into `planes/event` and `planes/action/event-bridge`. |
| `platform:java:ai-integration` | Keep provider abstractions in platform; move query assist, schema inference, recommendations, and action suggestions into `planes/intelligence`. |
| `platform:java:data-governance` | Keep generic policy primitives if reused; move retention, redaction, provenance, and evidence implementations into `planes/governance`. |
| `platform:contracts` | Move Data Cloud and Action Plane OpenAPI/schemas into `products/data-cloud/contracts`. |

## Migration Sequence

1. Align docs and terminology around planes, surfaces, and runtime truth.
2. Normalize contracts under `products/data-cloud/contracts`.
3. Move docs into `docs/product`, `docs/architecture`, `docs/api`, `docs/operations`, `docs/migration`, and `docs/audits`.
4. Move low-risk deployment assets into `deploy`.
5. Move current Data Cloud core modules into `planes/*` without package renames.
6. Move AEP modules into `planes/action` without package renames.
7. Move delivery modules into `delivery/*`.
8. Move plugin/catalog/bridge modules into `extensions/*`.
9. Add architecture checks for forbidden dependencies.
10. Rename Java packages after Gradle/module paths stabilize.

## Naming Rules

Use these names in product and architecture docs:

```text
Action Plane
Runtime Truth Registry
plane state
surface state
runtime dependency
degraded surface
```

Avoid these names in product and architecture docs:

```text
capability area
AEP capability
capability model
capability truth
capability registry
```

The word "capability" may remain temporarily in code and compatibility APIs until the Runtime Truth Registry migration is complete.
