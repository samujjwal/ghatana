# Data Cloud Unified Detailed Architecture

**Product:** Data Cloud  
**Action Plane runtime:** AEP under `products/data-cloud/planes/action`  
**Public contracts:** `products/data-cloud/contracts`  
**Purpose:** Define detailed architecture, module boundaries, runtime planes, contracts, deployment topology, data/event/agent flows, governance, observability, and architecture rules for the unified Data Cloud product.

---

## 1. Architecture Goals

The unified architecture must satisfy these goals:

```text
1. One top-level product boundary: products/data-cloud.
2. Data Cloud is organized by planes, not surface areas.
3. AEP is the runtime implementation behind the Action Plane, not a separate product.
4. Data, Event, Context, Governance, and Intelligence planes remain independent of Action Plane implementation internals.
5. The Action Plane consumes Data Cloud public contracts/SPI/event/context/governance APIs.
6. Public contracts are centralized at products/data-cloud/contracts.
7. Product consumers use SDK/API/contracts instead of internal modules.
8. Runtime truth is exposed through a Runtime Truth Registry.
9. Production profiles fail closed for missing security, policy, audit, durability, and required runtime dependencies.
10. UI, SDK, docs, and tests are aligned with runtime truth.
11. Architecture remains modular enough to avoid a god product.
```

---

## 2. Target Repository Architecture

```mermaid
flowchart TB
    subgraph Root["products/data-cloud"]
        Docs["docs<br/>Product, architecture, operations"]
        Contracts["contracts<br/>Public OpenAPI / Proto / Schemas"]
        Delivery["delivery<br/>API, launcher, SDK, UI"]
        Extensions["extensions<br/>Plugins, connectors, agent catalog"]
        Deploy["deploy<br/>Helm, K8s, Terraform"]
        IntegrationTests["integration-tests"]

        subgraph Planes["planes"]
            Data["data"]
            Event["event"]
            Context["context"]
            Intelligence["intelligence"]
            Governance["governance"]
            Action["action<br/>AEP runtime implementation"]
            Operations["operations"]
        end
    end

    Delivery --> Contracts
    Delivery --> Planes
    Extensions --> Contracts
    Extensions --> Planes
    IntegrationTests --> Delivery
    IntegrationTests --> Planes

    Action --> Data
    Action --> Event
    Action --> Context
    Action --> Governance
    Action --> Operations

    Data -. forbidden .-> Action
    Event -. forbidden .-> Action
    Context -. forbidden .-> Action
    Governance -. forbidden .-> Action
```

---

## 3. Architecture Planes

### 3.1 Plane model

```mermaid
flowchart TB
    subgraph Experience["Experience Plane"]
        UI["Unified UI"]
        SDK["SDKs"]
        CLI["CLI / Scripts"]
        Docs["Docs"]
    end

    subgraph Contract["Contract Plane"]
        OpenAPI["OpenAPI"]
        Proto["Proto"]
        Schemas["JSON/Event Schemas"]
        RuntimeTruthSchema["Runtime Truth Schema"]
    end

    subgraph RuntimeTruth["Runtime Truth Plane"]
        RuntimeTruthRegistry["Runtime Truth Registry"]
        Health["Health/Readiness"]
        DependencyProbes["Dependency Probes"]
        Degradation["Degradation State"]
    end

    subgraph DataPlane["Data Plane"]
        Entities["Entities"]
        Events["Events"]
        EventStore["Event Store"]
        Query["Query"]
        Analytics["Analytics"]
    end

    subgraph ContextPlane["Context Plane"]
        Lineage["Lineage"]
        Freshness["Freshness"]
        Provenance["Provenance"]
        Memory["Agent Memory"]
        RAG["RAG/Context Retrieval"]
    end

    subgraph ActionPlane["Action Plane"]
        Patterns["Patterns"]
        Pipelines["Pipelines"]
        Agents["Agents"]
        Runs["Runs"]
        HITL["HITL"]
        Learning["Learning"]
    end

    subgraph GovernancePlane["Governance Plane"]
        Tenant["Tenant Isolation"]
        Authz["Authorization"]
        Policy["Policy"]
        Audit["Audit"]
        Retention["Retention/Redaction"]
    end

    subgraph OpsPlane["Operations Plane"]
        Metrics["Metrics"]
        Traces["Traces"]
        Logs["Logs"]
        Alerts["Alerts"]
        Backups["Backup/Restore"]
    end

    Experience --> Contract
    Experience --> RuntimeTruth
    Contract --> DataPlane
    Contract --> ActionPlane
    RuntimeTruth --> Experience
    DataPlane --> ContextPlane
    DataPlane --> GovernancePlane
    ActionPlane --> DataPlane
    ActionPlane --> GovernancePlane
    DataPlane --> OpsPlane
    ActionPlane --> OpsPlane
    GovernancePlane --> OpsPlane
```

### 3.2 Plane ownership

| Plane | Owner | Responsibilities |
|---|---|---|
| Experience | Data Cloud UI/SDK | Unified console, generated clients, operator surfaces |
| Contract | Data Cloud contracts | Public OpenAPI/proto/schema truth |
| Runtime Truth | Data Cloud planes + Action Plane probes | Plane state, surface state, dependency state, degraded behavior |
| Data Plane | Data Cloud core | Entity/event/query/analytics persistence |
| Context Plane | Data Cloud core | Lineage, freshness, provenance, memory, RAG |
| Action Plane | AEP runtime implementation | Pipelines, agents, patterns, runs, HITL, learning |
| Governance Plane | Data Cloud core + Action Plane evidence emitters | Tenant isolation, policy, audit, compliance evidence |
| Operations | Shared platform + product runtime | Metrics, traces, logs, alerts, backup/restore |

---

## 4. Module Architecture

The canonical module map lives in `docs/architecture/PLANE_ARCHITECTURE.md`. This section summarizes the target organization.

### 4.1 Plane modules

| Target Path | Plane | Purpose |
|---|---|---|
| `planes/shared-spi/` | Contract | Stable plane SPI and plugin contracts |
| `planes/data/entity/` | Data | Entity model, schema, and storage contracts |
| `planes/event/core/` | Event | Event primitives and event log contracts |
| `planes/event/store/` | Event | Event store providers |
| `planes/context/` | Context | Lineage, freshness, provenance, memory, and RAG |
| `planes/intelligence/analytics/` | Intelligence | Query, reports, recommendations, and analytics |
| `planes/governance/core/` | Governance | Policy, privacy, retention, redaction, and audit support |
| `planes/action/*` | Action | Pipelines, patterns, agents, reviews, runs, learning, and AEP runtime implementation |
| `planes/operations/*` | Operations | Configuration, health, runtime truth, alerts, and diagnostics |

### 4.2 Delivery and extension modules

| Target Path | Purpose |
|---|---|
| `delivery/api/` | API handlers and route adapters |
| `delivery/launcher/` | Process entry point and transport handlers |
| `delivery/runtime-composition/` | Runtime composition across planes |
| `delivery/sdk/` | Generated clients from contracts |
| `delivery/ui/` | Product UI |
| `extensions/plugins/` | Plugin implementations |
| `extensions/connectors/` | Source/sink connector implementations |
| `extensions/agent-catalog/` | Agent metadata and read-only catalog surfaces |
| `extensions/kernel-bridge/` | Kernel integration bridge |
| `kernel-bridge` | Kernel integration | Platform kernel |

---

## 5. Dependency Architecture

### 5.1 Allowed dependency graph

```mermaid
flowchart BT
    Shared["platform/java + platform-kernel + shared-services"]
    Contracts["products/data-cloud/contracts"]
    DataCore["Data Cloud Core"]
    Context["Context/Agent Registry"]
    AEPContracts["products/data-cloud/planes/action/operator-contracts"]
    AEPImpl["products/data-cloud/planes/action/* implementation"]
    Launcher["products/data-cloud/delivery/launcher or distribution"]
    UI["products/data-cloud/delivery/ui"]
    SDK["products/data-cloud/delivery/sdk"]
    Consumers["Other Products"]

    DataCore --> Shared
    Context --> DataCore
    Context --> Shared

    AEPContracts --> Shared
    AEPImpl --> AEPContracts
    AEPImpl --> Contracts
    AEPImpl --> DataCore
    AEPImpl --> Context
    AEPImpl --> Shared

    Launcher --> DataCore
    Launcher --> AEPImpl
    SDK --> Contracts
    UI --> SDK
    Consumers --> SDK
    Consumers --> Contracts
```

### 5.2 Compile-time rules

```text
Rule DC-ARCH-001:
  No Data Cloud core module may depend on :products:data-cloud:planes:action:* implementation modules.

Rule DC-ARCH-002:
  AEP engine must not depend on AEP server, launcher, UI, or gateway.

Rule DC-ARCH-003:
  AEP may depend on Data Cloud SPI/contracts/event APIs.

Rule DC-ARCH-004:
  Public consumers must depend on SDK/contracts, not AEP server/engine internals.

Rule DC-ARCH-005:
  Product-level contracts must not depend on implementation modules.

Rule DC-ARCH-006:
  Only launcher/distribution/integration-test modules may compose Data Cloud core and AEP implementation modules.
```

---

## 6. Contract Architecture

### 6.1 Contract source-of-truth

```text
products/data-cloud/contracts/openapi/data-cloud.yaml
  Core Data Cloud REST contract

products/data-cloud/contracts/openapi/action-plane.yaml
  Action Plane REST contract

products/data-cloud/contracts/openapi/data-cloud-platform.yaml
  Unified product REST contract, generated/assembled from core + AEP where possible

products/data-cloud/contracts/proto/data-cloud/
  Core gRPC/proto contracts

products/data-cloud/contracts/proto/aep/
  Action Plane gRPC/proto contracts

products/data-cloud/contracts/schemas/
  Event, entity, pipeline, agent, surface, audit, and governance schemas
```

### 6.2 Contract synchronization

```mermaid
flowchart LR
    ProductContracts["products/data-cloud/contracts"]
    ServerResources["Runtime server resources"]
    SDKGeneration["SDK generation"]
    APIValidation["API route validation"]
    PlatformRegistry["platform/contracts/openapi where still needed"]

    ProductContracts --> ServerResources
    ProductContracts --> SDKGeneration
    ProductContracts --> APIValidation
    ProductContracts --> PlatformRegistry

    ServerResources --> Runtime["Runtime OpenAPI served to clients"]
    SDKGeneration --> SDK["Data Cloud SDK"]
    APIValidation --> CI["CI Gates"]
```

### 6.3 Drift prevention

Required checks:

```text
- OpenAPI validates.
- Runtime route inventory matches OpenAPI.
- AEP server resource spec matches product-level AEP spec.
- SDK generation uses product-level specs.
- No stale `products/data-cloud/planes/action/contracts` path remains.
```

---

## 7. Runtime Architecture

### 7.1 Unified runtime topology

```mermaid
flowchart TB
    Client["Client / SDK / UI"]
    Gateway["Data Cloud API Gateway"]
    Cap["Runtime Truth Registry"]

    subgraph Core["Data Cloud Core Runtime"]
        EntitySvc["Entity Service"]
        EventSvc["Event Service"]
        QuerySvc["Query/Analytics Service"]
        ContextSvc["Context/Memory/RAG Service"]
        GovernanceSvc["Governance/Audit Service"]
    end

    subgraph AEP["AEP Runtime"]
        PatternSvc["Pattern Service"]
        PipelineSvc["Pipeline Service"]
        AgentSvc["Agent Service"]
        RunSvc["Run Ledger"]
        HITLSvc["HITL Service"]
        LearningSvc["Learning Service"]
    end

    Storage["Storage Backends / Plugins"]
    O11y["Metrics / Traces / Logs / Audit"]

    Client --> Gateway
    Gateway --> Cap
    Gateway --> EntitySvc
    Gateway --> EventSvc
    Gateway --> QuerySvc
    Gateway --> ContextSvc
    Gateway --> GovernanceSvc
    Gateway --> PatternSvc
    Gateway --> PipelineSvc
    Gateway --> AgentSvc
    Gateway --> HITLSvc

    AEP --> EventSvc
    AEP --> ContextSvc
    AEP --> GovernanceSvc
    Core --> Storage
    AEP --> Storage
    Core --> O11y
    AEP --> O11y
```

### 7.2 Runtime modes

| Mode | Data Cloud | AEP | Intended use |
|---|---|---|---|
| Local | Embedded/in-memory | Embedded allowed | Developer workflow |
| Sovereign | Embedded durable/file-backed | Embedded or local sidecar | Air-gapped/single-binary |
| Standalone | Single Data Cloud server | AEP same deployment or separate process | Small production / validation |
| Enterprise | Durable providers + auth/policy/audit | Separate scalable AEP service | Production |
| Test | Deterministic fixtures | Deterministic fixtures | CI/integration tests |

### 7.3 AEP production dependency rule

```text
If AEP_PROFILE=production:
  require durable Data Cloud connection/event store
  require policy/audit dependencies when governance endpoints are active
  fail closed if required dependencies are absent
```

---

## 8. Data Architecture

### 8.1 Entity/event model

```mermaid
classDiagram
    class Tenant {
      +tenantId
      +region
      +policyProfile
    }

    class Collection {
      +collectionName
      +schema
      +retentionPolicy
      +classification
    }

    class Entity {
      +entityId
      +tenantId
      +collection
      +payload
      +version
      +createdAt
      +updatedAt
    }

    class EntityVersion {
      +version
      +entityId
      +diff
      +actor
      +timestamp
    }

    class Event {
      +eventId
      +tenantId
      +eventType
      +subject
      +payload
      +correlationId
      +timestamp
    }

    Tenant "1" --> "*" Collection
    Collection "1" --> "*" Entity
    Entity "1" --> "*" EntityVersion
    Entity "1" --> "*" Event
```

### 8.2 Required event envelope

```text
eventId
tenantId
eventType
subjectType
subjectId
source
payload
schemaVersion
correlationId
causationId
actor
classification
policyContext
timestamp
provenance
traceContext
```

### 8.3 AEP run model

```mermaid
classDiagram
    class PipelineDefinition {
      +pipelineId
      +tenantId
      +name
      +version
      +state
      +operators
      +triggers
    }

    class PatternDefinition {
      +patternId
      +tenantId
      +type
      +criteria
      +thresholds
      +confidencePolicy
    }

    class AgentDescriptor {
      +agentId
      +tenantId
      +surfaces
      +version
      +runtimeConfig
    }

    class Run {
      +runId
      +tenantId
      +pipelineId
      +status
      +startedAt
      +completedAt
      +evidence
    }

    class ReviewItem {
      +reviewId
      +tenantId
      +runId
      +confidence
      +status
      +decision
    }

    PipelineDefinition "1" --> "*" Run
    PatternDefinition "1" --> "*" Run
    AgentDescriptor "1" --> "*" Run
    Run "0..*" --> "*" ReviewItem
```

---

## 9. Event-to-AEP Flow

```mermaid
sequenceDiagram
    participant Source as Source System
    participant DC as Data Cloud Event API
    participant Store as Event Store
    participant Cap as Runtime Truth Registry
    participant AEP as AEP Pattern/Pipeline Engine
    participant Run as Run Ledger
    participant Gov as Governance/Audit
    participant UI as Operator UI

    Source->>DC: Append event
    DC->>Gov: Check tenant/policy/classification
    DC->>Store: Persist event
    DC->>Cap: Resolve Action Plane state
    alt AEP active
        Store-->>AEP: Event available
        AEP->>AEP: Match patterns / trigger pipelines
        AEP->>Run: Create/update run
        AEP->>Gov: Emit execution evidence
        AEP-->>UI: SSE/runtime update
    else AEP unavailable/degraded
        DC->>Gov: Record skipped/degraded processing
        DC-->>UI: Surface degraded
    end
```

---

## 10. HITL and Learning Architecture

### 10.1 HITL flow

```mermaid
flowchart TB
    AgentAction["Agent/Pipeline Action"]
    Confidence["Confidence + Risk Evaluation"]
    Policy["Policy Decision"]
    AutoApply["Auto Apply"]
    Review["Create HITL Review Item"]
    Queue["Review Queue"]
    Approve["Approve"]
    Reject["Reject"]
    Escalate["Escalate"]
    Learn["Learning Pipeline"]
    Audit["Audit Evidence"]

    AgentAction --> Confidence
    Confidence --> Policy
    Policy -->|low risk / high confidence| AutoApply
    Policy -->|review required| Review
    Review --> Queue
    Queue --> Approve
    Queue --> Reject
    Queue --> Escalate
    Approve --> Learn
    Reject --> Learn
    Escalate --> Queue
    AutoApply --> Audit
    Review --> Audit
    Approve --> Audit
    Reject --> Audit
```

### 10.2 Learning loop

```mermaid
flowchart LR
    Episodes["Episodes"]
    Reflection["Reflection / Grouping"]
    Evaluation["Evaluation Gate"]
    Candidate["Policy Candidate"]
    HITL["Human Review"]
    Promotion["Policy Promotion"]
    Procedural["Procedural Memory"]
    Audit["Audit"]

    Episodes --> Reflection
    Reflection --> Evaluation
    Evaluation --> Candidate
    Candidate --> HITL
    HITL -->|approved| Promotion
    HITL -->|rejected| Audit
    Promotion --> Procedural
    Promotion --> Audit
```

---

## 11. Governance Architecture

### 11.1 Required metadata

Every meaningful operation should carry:

```text
tenant
actor
source
classification
policy decision
provenance
audit event
trace id
Surface state
freshness
```

### 11.2 Policy enforcement path

```mermaid
flowchart TB
    Request["Request"]
    Auth["Authentication"]
    Tenant["Tenant Resolution"]
    Authz["Authorization"]
    Policy["Policy Engine"]
    Classification["Data Classification"]
    Execution["Data/AEP Execution"]
    Audit["Audit Event"]
    Response["Response"]

    Request --> Auth
    Auth --> Tenant
    Tenant --> Authz
    Authz --> Policy
    Policy --> Classification
    Classification --> Execution
    Execution --> Audit
    Audit --> Response
```

### 11.3 Fail-closed paths

Production must fail closed for:

```text
- missing tenant context
- missing authentication for protected routes
- missing authorization
- missing policy engine for governed action
- missing audit writer for sensitive mutation
- missing durable event store when durability is advertised
- missing redaction policy for sensitive export
- unavailable AEP dependency when AEP is required
- surface advertised as live without passing probes
```

---

## 12. Runtime Truth Registry Architecture

### 12.1 Surface states

```text
LIVE
DEGRADED
DISABLED
PREVIEW
UNAVAILABLE
MISCONFIGURED
```

### 12.2 Surface record

```json
{
  "surface": "aep.hitl.review",
  "state": "LIVE",
  "owner": "data-cloud/aep",
  "dependencies": ["event-store", "audit", "policy-engine"],
  "lastCheckedAt": "2026-05-05T00:00:00Z",
  "evidence": {
    "healthProbe": "passed",
    "contract": "products/data-cloud/contracts/openapi/action-plane.yaml",
    "tests": ["AepHttpServerHitlTest"]
  },
  "limitations": []
}
```

### 12.3 Consumers

```text
- UI navigation
- action enablement
- SDK feature flags
- docs badges
- automation planning
- support diagnostics
- deployment validation
```

---

## 13. Observability Architecture

### 13.1 Telemetry taxonomy

```text
Data Cloud telemetry:
  entity.created
  entity.updated
  event.appended
  query.executed
  governance.policy.evaluated
  audit.recorded
  surface.state.changed

AEP telemetry:
  aep.event.received
  aep.pattern.detected
  aep.pipeline.started
  aep.pipeline.completed
  aep.pipeline.failed
  aep.hitl.created
  aep.hitl.approved
  aep.hitl.rejected
  aep.learning.consolidated
  aep.policy.promoted
```

### 13.2 Trace flow

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant DC as Data Cloud Core
    participant AEP
    participant Store
    participant Audit
    participant Metrics

    Client->>API: Request with correlation id
    API->>DC: Start trace span
    DC->>Store: Persist/query
    DC->>AEP: Trigger AEP if applicable
    AEP->>AEP: Pipeline spans
    AEP->>Audit: Execution evidence
    DC->>Metrics: Emit metrics
    AEP->>Metrics: Emit AEP metrics
    API-->>Client: Response with trace id
```

---

## 14. Deployment Architecture

### 14.1 Local profile

```mermaid
flowchart TB
    Dev["Developer"]
    Process["Single JVM / Local Stack"]
    DC["Embedded Data Cloud"]
    AEP["Embedded/Local AEP"]
    UI["Local UI"]

    Dev --> UI
    UI --> Process
    Process --> DC
    Process --> AEP
```

### 14.2 Enterprise profile

```mermaid
flowchart TB
    LB["Load Balancer / Gateway"]
    DCAPI["Data Cloud API Service"]
    AEPAPI["AEP Service"]
    Postgres["PostgreSQL"]
    Kafka["Kafka/Event Store"]
    Redis["Redis"]
    ClickHouse["ClickHouse"]
    ObjectStore["S3/Ceph"]
    OTel["OpenTelemetry"]
    Prom["Prometheus"]
    Audit["Audit Store"]

    LB --> DCAPI
    LB --> AEPAPI
    DCAPI --> Postgres
    DCAPI --> Kafka
    DCAPI --> Redis
    DCAPI --> ClickHouse
    DCAPI --> ObjectStore
    AEPAPI --> DCAPI
    AEPAPI --> Kafka
    AEPAPI --> Audit
    DCAPI --> OTel
    AEPAPI --> OTel
    OTel --> Prom
```

---

## 15. Validation Matrix

| Area | Validation |
|---|---|
| Gradle modules | No `:products:data-cloud:planes:action:*` after merge |
| Paths | No active `products/data-cloud/planes/action` after merge |
| Contracts | Product-level OpenAPI validates |
| Route sync | Runtime routes match OpenAPI |
| Data Cloud core boundary | No dependency on AEP impl |
| AEP engine boundary | No dependency on server/launcher/UI |
| Production profile | Fail closed for missing durable/policy/audit deps |
| Runtime Truth Registry | UI/SDK/docs read runtime truth |
| SDK | Generates from product-level contracts |
| UI | AEP pages runtime-truth-gated |
| Integration | Event -> AEP -> audit -> UI flow passes |

---

## 16. Architecture Acceptance Criteria

```text
- Data Cloud remains the only top-level product folder.
- AEP lives under products/data-cloud/planes/action.
- Public contracts live under products/data-cloud/contracts.
- Data Cloud core has no AEP implementation dependency.
- AEP consumes Data Cloud contracts/SPI/event APIs.
- Launcher/distribution composes core + AEP.
- Runtime Truth Registry controls runtime truth.
- All critical paths emit audit and telemetry.
- Production profiles fail closed for trust-critical dependencies.
- SDK, UI, docs, and tests match product-level contracts.
```
