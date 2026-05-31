# Data Cloud Plane Architecture

**Status:** Canonical target architecture  
**Scope:** Product positioning, module hierarchy, runtime boundaries, and migration rules  
**Supersedes:** Capability-area language in older Data Cloud planning docs

Data Cloud is one product organized as interoperable planes. A plane is a stable product and architecture boundary with a clear purpose, ownership model, public surfaces, and implementation modules.

Do not use "capability area" as the product hierarchy. Use:

```text
Planes = product architecture
Surfaces = user/API entry points
Modules = implementation
Runtime truth = live/degraded/unavailable state of planes and surfaces
Action Plane = Data Cloud's governed automation runtime (formerly AEP)
```

## Positioning

Data Cloud is a governed operational data fabric where trusted data, durable storage-plane events, governed context, intelligence substrate, policy evidence, and pluggable persistence run together.

The Action Plane (formerly AEP) is integrated within Data Cloud as the governed automation runtime, providing event-driven agent orchestration, pattern detection, pipeline execution, HITL review, and learning loops.

```text
Data Cloud product
  governed data/storage substrate
  public contracts and stable SPI
  Action Plane runtime (governed automation)
    Event-driven agent orchestration
    Pattern detection and learning
    Pipeline execution
    HITL review workflows
    Runtime observability
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
| Action Plane | Governed automation runtime providing event-driven agent orchestration, pattern detection, pipeline execution, HITL review, learning loops, and runtime observability | Pipelines, agents, reviews, runs, learning metadata | `planes/action` |
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
| Plugins | Data | Event, Operations |

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

Status values: **active** means implemented as an active Gradle/runtime module, **degraded** means present but not complete enough to advertise as fully live, **preview** means user/API surface exists behind runtime truth, and **target-only** means architecture-owned but not active.

| Current Path | Target Path | Plane | Status | Notes |
| --- | --- | --- | --- | --- |
| `planes/shared-spi/` | `planes/shared-spi/` | Contract | active | Stable plane SPI and plugin contracts |
| `planes/data/entity/` | `planes/data/entity/` | Data | active | Entity model, schema, storage contracts |
| `planes/event/core/` | `planes/event/core/` | Event | active | Event primitives and event log contracts |
| `planes/event/store/` | `planes/event/store/` | Event | active | Event store providers |
| `planes/context/` | `planes/context/` | Context | target-only | Not currently active as Gradle module |
| `planes/intelligence/analytics/` | `planes/intelligence/analytics/` | Intelligence | active | Query, reports, analytics |
| `planes/intelligence/feature-ingest/` | `planes/intelligence/feature-ingest/` | Intelligence | preview | Feature ingestion and ML substrate |
| `planes/governance/core/` | `planes/governance/core/` | Governance | active | Governance and policy support |
| `planes/operations/config/` | `planes/operations/config/` | Operations | active | Runtime configuration model |
| `planes/action/operator-contracts/` | `planes/action/operator-contracts/` | Action | active (semantic readiness partial) | Operator contracts defined; PatternSpec compiler, EventCloud SPI, and executable lifecycle still incomplete |
| `planes/action/central-runtime/` | `planes/action/central-runtime/` | Action | active (semantic readiness partial) | Central runtime infrastructure active; replay-safe execution partial |
| `planes/action/engine/` | `planes/action/engine/` | Action | active (semantic readiness partial) | Pattern detection active; PatternSpec compiler, learning-to-recommendation incomplete |
| `planes/action/orchestrator/` | `planes/action/orchestrator/` | Action | active (replay-safe lifecycle partial) | Orchestration active; replay-safe lifecycle and idempotent execution incomplete |
| `planes/action/agent-runtime/` | `planes/action/agent-runtime/` | Action | active (replay-safe execution partial) | Agent execution active; replay-safe execution, side-effect controls incomplete |
| `planes/action/event-bridge/` | `planes/action/event-bridge/` | Action/Event | degraded | Event bridge between Data Cloud Event Plane and Action Plane |
| `planes/action/server/` | `planes/action/server/` | Action/Delivery | preview | Action Plane HTTP server and API handlers |
| `planes/action/registry/` | `planes/action/registry/` | Action | preview | Agent and pattern registry |
| `planes/action/analytics/` | `planes/action/analytics/` | Action | degraded | Action Plane analytics and metrics |
| `planes/action/security/` | `planes/action/security/` | Action | active | Action Plane security and authorization |
| `planes/action/api/` | `planes/action/api/` | Action | preview | Action Plane API contracts |
| `planes/action/scaling/` | `planes/action/scaling/` | Action | preview | Action Plane scaling and capacity management |
| `planes/action/observability/` | `planes/action/observability/` | Action | degraded | Action Plane observability and tracing |
| `planes/action/identity/` | `planes/action/identity/` | Action | active | Action Plane identity and authentication |
| `planes/action/compliance/` | `planes/action/compliance/` | Action | active | Action Plane compliance and audit |
| `planes/action/kernel-bridge/` | `planes/action/kernel-bridge/` | Action | degraded | Compatibility bridge remains until canonical extension bridge fully owns kernel integration |
| `delivery/api/` | `delivery/api/` | Delivery | degraded | Data Cloud API handlers and route adapters; media and operations job lifecycle still hardening |
| `delivery/runtime-composition/` | `delivery/runtime-composition/` | Delivery | active | Runtime composition across planes |
| `delivery/launcher/` | `delivery/launcher/` | Delivery | degraded | Process entry point and transport handlers; connector runtime wiring still hardening |
| `delivery/sdk/` | `delivery/sdk/` | Experience/Contract | degraded | Generated clients |
| `delivery/ui/` | `delivery/ui/` | Experience | degraded | Product UI consuming runtime truth |
| `extensions/plugins/` | `extensions/plugins/` | Extensions | preview | Plugin implementations |
| `extensions/agent-registry/` | `extensions/agent-registry/` | Extensions | preview | Data Cloud-backed agent registry extension and read model |
| `extensions/agent-catalog/` | `extensions/agent-catalog/` | Extensions | preview | Agent metadata catalog |
| `extensions/kernel-bridge/` | `extensions/kernel-bridge/` | Extensions | degraded | Kernel integration bridge |
| `products/audio-video/` | `products/audio-video/` | Media (external) | partial | Audio-video external shared service; partial Data Cloud metadata integration, not yet full first-class modality with durable job lifecycle |
| `deploy/helm/`, `deploy/k8s/`, `deploy/terraform/` | `deploy/*` | Operations | target-only | Deployment assets |

**Context Plane Status:** target-only, not user-visible. The `planes/context/` directory exists as a placeholder and ownership boundary but is not an active Gradle module. UI/runtime should not treat it as fully available until promoted from target-only status.

## Dependency Rules

```text
delivery/runtime-composition may compose Data-Cloud planes.
Action Plane runtime may depend on public contracts/SPI from data, event, context, governance, and operations.
planes/data, planes/event, planes/context, and planes/governance must not depend on planes/action implementation internals.
delivery/ui must depend on generated clients and frontend adapters, not backend internals.
extensions must depend on contracts/SPI, not launcher internals.
contracts must not depend on implementation modules.
```

Allowed direction:

```text
Experience -> Contract -> Planes
Action Plane runtime -> Data/Event/Context/Governance public contracts
Planes -> Operations telemetry contracts
Delivery composition -> all planes
```

Forbidden direction:

```text
Data/Event/Context/Governance -> Action Plane implementation internals
Contracts -> runtime implementation
SDK -> launcher or server internals
UI -> backend implementation modules
```

## Shared Platform Review Rules

Some platform modules became shared because Data-Cloud and AEP boundaries drifted. Re-evaluate them during migration.

Keep in platform when:

```text
The abstraction is used by three or more unrelated products.
The module contains generic primitives with no Data Cloud plane semantics.
The module is infrastructure rather than product behavior.
```

Move or split into Data Cloud when:

```text
The module exists mainly for Data Cloud or Action Plane functionality.
The types describe Data Cloud planes or Action Plane semantics.
The implementation is product behavior, not cross-product infrastructure.
```

Initial candidates:

| Platform Module | Recommendation |
| --- | --- |
| `platform:java:agent-core` | Keep minimal generic interfaces in platform; move Action Plane-specific runtime, dispatch, review, and EventOperatorCapability semantics to `planes/action`. Data-Cloud may keep persistence models only. |
| `platform:java:workflow` | Keep generic workflow primitives if other products use them; keep Data-Cloud persistence metadata separate from Action Plane runtime semantics. |
| `platform:java:messaging` | Keep generic messaging abstractions in platform; move Data-Cloud storage-plane event routing into `planes/event`; keep Action Plane event semantics in `planes/action`. |
| `platform:java:ai-integration` | Keep provider abstractions in platform; move query assist, schema inference, recommendations, and action suggestions into `planes/intelligence`. |
| `platform:java:data-governance` | Keep generic policy primitives if reused; move retention, redaction, provenance, and evidence implementations into `planes/governance`. |
| `platform:contracts` | Move Data Cloud and Action Plane OpenAPI/schemas into `products/data-cloud/contracts`. |

## Migration Sequence

1. Align docs and terminology around planes, surfaces, and runtime truth.
2. Normalize contracts under `products/data-cloud/contracts`.
3. Move docs into `docs/product`, `docs/architecture`, `docs/api`, `docs/operations`, `docs/migration`, and `docs/audits`.
4. Move low-risk deployment assets into `deploy`.
5. Move current Data Cloud core modules into `planes/*` without package renames.
6. Ensure Action Plane runtime uses public contracts/SPI from Data Cloud planes.
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
