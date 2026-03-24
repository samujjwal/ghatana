# ADR-DC-001 — Data-Cloud Module Ownership & Domain Boundaries

| Metadata     | Value                                   |
|--------------|-----------------------------------------|
| **Status**   | Accepted                                |
| **Date**     | 2026-01-19                              |
| **Deciders** | Data-Cloud Team, Platform Architecture  |
| **Supersedes** | _none_                                |

---

## 1. Context

`products/data-cloud` has grown to encompass multiple sub-modules with distinct capability domains.
As the surface area expanded (entity CRUD, analytics, AI assist, voice gateway, data lifecycle /
governance), ambiguity arose about which team owns each module, what its public contract is, and
which other modules are allowed to depend on it.

This ADR formalises the ownership matrix and enforces strict downward dependency flow:

```
products → libs → contracts
```

No circular dependences.  No product ↔ product imports except through `libs/*` or `contracts/*`.

---

## 2. Decision

### 2.1 Module Ownership Matrix

| Gradle Module | Capability Domain | Team Owner | Package Root | Primary Public Contract |
|---|---|---|---|---|
| `:products:data-cloud:platform` | Core domain types & service interfaces | Data-Cloud Platform | `com.ghatana.datacloud` | `DataCloudService`, `EntityStore`, `QueryEngine` |
| `:products:data-cloud:spi` | Storage provider SPI | Data-Cloud Platform | `com.ghatana.datacloud.spi` | `StorageProvider`, `IndexProvider` |
| `:products:data-cloud:launcher` | HTTP server wiring & handler routing | Data-Cloud Runtime | `com.ghatana.datacloud.launcher` | `DataCloudHttpServer` |
| `:products:data-cloud:agent-registry` | Agent/operator registry | Data-Cloud AI | `com.ghatana.datacloud.agents` | `AgentRegistry`, `AgentDefinition` |
| `:products:data-cloud:feature-store-ingest` | ML feature ingestion pipeline | Data-Cloud AI | `com.ghatana.datacloud.features` | `FeatureIngestService` |
| `:products:data-cloud:sdk` | External client SDK | Data-Cloud SDK | `com.ghatana.datacloud.sdk` | `DataCloudClient` |
| `:products:data-cloud:ui` | React/Tailwind UI components | Data-Cloud Frontend | `@ghatana/data-cloud-ui` | Published npm package |

### 2.2 Handler Domain Allocation within `:launcher`

| Handler Class | Capability Domain | Routes Owned | Depends On |
|---|---|---|---|
| `EntityHandler` | Entity CRUD + search + export + anomalies + batch + SSE | `/api/v1/entities/**` | `DataCloudService`, `EntityStore` |
| `AnalyticsHandler` | Query, aggregation, reports, AI suggest | `/api/v1/analytics/**`, `/api/v1/reports/**` | `QueryEngine`, `CompletionService` |
| `PipelineHandler` | Pipeline management + AI optimise hint | `/api/v1/pipelines/**` | `PipelineService`, `CompletionService` |
| `BrainHandler` | Brain status + AI explanation | `/api/v1/brain/**` | `BrainService`, `CompletionService` |
| `AiAssistHandler` | Cross-domain AI assist (DC-E3) | `/api/v1/ai/**` | `CompletionService` |
| `VoiceGatewayHandler` | NLU voice intent resolution (DC-E4) | `/api/v1/voice/**` | `CompletionService`, `AuditService` |
| `DataLifecycleHandler` | Retention, purge, PII redaction, compliance (DC-E5) | `/api/v1/governance/**` | `AuditService` |
| `ModelRegistryHandler` | AI model registration + promotion (DC-11) | `/api/v1/models/**` | `ModelRegistry` |
| `FeatureHandler` | Feature vector ingestion + retrieval (DC-11) | `/api/v1/features/**` | `FeatureIngestService` |
| `IngestHandler` | Data ingestion + event publish | `/api/v1/ingest/**` | `EntityStore`, `EventBus` |
| `EventHandler` | SSE event tailing (CDC) | `/api/v1/events/**` | `EventTailService` |

### 2.3 Allowed Dependency Edges

```
launcher   ──imports──▶  platform  ──imports──▶  spi
launcher   ──imports──▶  libs:http-server
launcher   ──imports──▶  libs:ai-integration    (via AiAssistHandler, VoiceGatewayHandler)
launcher   ──imports──▶  platform:java:audit    (via DataLifecycleHandler, VoiceGatewayHandler)
launcher   ──imports──▶  platform:java:governance
sdk        ──imports──▶  contracts/openapi
ui         ──imports──▶  sdk  (via REST)
agent-registry ──imports──▶ platform
feature-store-ingest ──imports──▶ platform
```

**Forbidden edges:**

| Source Module | Forbidden Import | Reason |
|---|---|---|
| `platform` | Any `launcher.*` class | Circular |
| `sdk` | Any `launcher.*` class | Layering violation |
| `launcher` | `feature-store-ingest` internal impl | Use `platform` API only |
| Any product | `products/virtual-org/**` directly | Must go through `libs/*` |

### 2.4 OpenAPI Contract Governance

- **Single source of truth**: `products/data-cloud/docs/openapi.yaml`
- **Drift detection**: `products/data-cloud/scripts/check-openapi-drift.sh` (runs in CI via `boundary-check.yml`)
- **Policy**: A PR that adds/removes routes in `DataCloudHttpServer.java` **MUST** update `openapi.yaml` atomically in the same commit.  CI will fail otherwise.
- **Ownership**: The Data-Cloud Platform team reviews all changes to `openapi.yaml` via CODEOWNERS.

---

## 3. Consequences

### Positive
- Clear escalation path: each module has a single owning team.
- Forbidden edges are machine-enforceable via `platform-boundary-check.gradle` and ArchUnit tests.
- OpenAPI drift is caught in CI, preventing documentation rot.

### Negative / Trade-offs
- `launcher` must not reach into `feature-store-ingest` internals; integrators must add a service interface in `platform` if needed.
- New handler domains require an ADR update (adds process overhead, but prevents invisible scope creep).

---

## 4. Compliance

All modules MUST maintain an `OWNER.md` at their root.  The boundary-check CI step validates this for every
new sub-module detected under `products/data-cloud/`.

```
products/data-cloud/<module>/OWNER.md
  Required fields: team, slack-channel, oncall-rotation, tech-lead, last-reviewed
```

---

## 5. Review Schedule

This ADR is reviewed at the start of each platform quarter.  Changes to the ownership matrix require
sign-off from the current tech lead listed in `products/data-cloud/OWNER.md`.
