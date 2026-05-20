## Executive verdict

**Can this commit ship? No.**

The target commit `8ea134a6ce713e9b7ed084ec07a4dc09ff2daffb` is a merge commit whose visible diff only touches `products/yappc/CHANGELOG.md`, but I audited the **Data Cloud / Action Plane repository snapshot** at that commit, not just the diff. 

```text
Production ready: No
Feature complete: No
World-class quality: No
Product coherence: Partial
Shared library health: Partial
Can ship under deterministic rules: No
Blocking P0 count: 3
Blocking P1 count: 5
Biggest blocker: Contract/runtime/security metadata drift around canonical Action Plane routes
Biggest architectural risk: Runtime Truth, route metadata, OpenAPI, UI posture, and security classification are not one consistent source of truth
Biggest product risk: Customer-facing surfaces can appear live while runtime/storage/governance behavior is partial or fallback-backed
Biggest testing risk: CI has important gates, but E2E smoke, backup drill, and some security/SBOM checks are advisory or continue-on-error
Confidence: Medium-high for static audit; I did not execute Gradle/Node test commands
```

---

## Audit progress ledger

| Area                              |     Coverage | Status                                                 | Confidence |
| --------------------------------- | -----------: | ------------------------------------------------------ | ---------: |
| Commit identity                   |      Checked | Complete                                               |       High |
| Canonical Data Cloud intent       |      Checked | Complete                                               |       High |
| Plane architecture/docs           |      Checked | Complete                                               |       High |
| Gradle module map                 |      Checked | Complete                                               |       High |
| Contracts                         |      Checked | Partial                                                |     Medium |
| Runtime route wiring              |      Checked | Partial/Broken                                         |       High |
| Runtime Truth                     |      Checked | Partial                                                |     Medium |
| Security/policy/audit enforcement |      Checked | Broken for canonical Action Plane route classification |       High |
| Event envelope                    |      Checked | Partial/Broken round-trip                              |       High |
| Context Plane                     |      Checked | Partial / in-memory runtime path                       |       High |
| UI Runtime Truth integration      |      Checked | Partial/drift                                          |     Medium |
| CI/release gates                  |      Checked | Partial                                                |       High |
| Full test execution               | Not executed | Unknown                                                |        N/A |

---

## What is strong

The product intent is now much clearer than earlier states. The README states Data Cloud is the AI-native operational data fabric and that AEP is only the runtime implementation behind the Action Plane, not a separate customer-facing product boundary. It also defines the plane model, outcome-first navigation, Runtime Truth endpoint, and canonical contracts. 

The canonical plane architecture is also explicit: Data Cloud is organized around stable planes, AEP must not be positioned as a separate product, and dependency rules forbid Data/Event/Context/Governance from depending on Action internals. 

The Gradle registry shows Data Cloud has been substantially reorganized into plane-based modules, including `planes:data`, `planes:event`, `planes:governance`, `planes:intelligence`, `planes:action:*`, `delivery:*`, `contracts`, `integration-tests`, and extensions. 

CI is materially stronger than a basic build. It includes backend compile, frontend type-check, unit tests, architecture tests, contract drift detection, route metadata generation check, tenant isolation audit, connector validation, agent governance validation, SDK generation, reuse scorecard, Helm/k8s render validation, and release-gate aggregation.  

---

# P0 findings

## P0-1 — Canonical Action Plane route security classification is inconsistent and can under-enforce policy

**Area:** Security / route metadata / Action Plane
**Status:** Broken
**Files:**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/EndpointSensitivity.java
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteActionAccessRegistry.java
```

**Evidence**

The router correctly registers many canonical Action Plane routes under `/api/v1/action/*`, including pipelines, memory, learning, executions, autonomy, agents, and plugins.  

However, `EndpointSensitivity` still contains several legacy critical/action patterns such as `/api/v1/pipelines/`, `/api/v1/memory/`, `/api/v1/plugins/{id}/enable`, `/api/v1/autonomy/level`, and `/api/v1/learning/review/{id}/approve`, while canonical `/api/v1/action/*` is broadly classified as `SENSITIVE`, not necessarily `CRITICAL`. 

The generated route access registry contains canonical `/api/v1/action/*` entries, but `EndpointSensitivity.classify()` can return `SENSITIVE` from the `/api/v1/action/` prefix before the fallback registry is used for escalation.  

**Why it matters**

Critical mutations such as plugin enable/disable/upgrade, autonomy level changes, execution rollback/retry/cancel, and Action Plane lifecycle operations must be policy-checked and audit-enforced deterministically. A route cannot be safe if the router, generated registry, and sensitivity classifier disagree.

**Required implementation action**

Replace prefix-first sensitivity logic with a generated, contract-backed route metadata registry that is authoritative for:

```text
method
canonical path
sensitivity
required access
requires auth
requires tenant
requires policy
requires blocking audit
idempotency requirement
runtime truth surface
legacy status
```

Then make `EndpointSensitivity` a thin adapter over that registry.

**Tests required**

Add table-driven tests for every canonical `/api/v1/action/*` mutation proving:

```text
expected sensitivity
required access
policy engine invoked
audit emitted
audit blocks in production for CRITICAL
legacy aliases disabled by default
```

---

## P0-2 — Event envelope append preserves fields, but query/get responses do not round-trip the canonical envelope

**Area:** Event Plane / Action Plane handoff / audit evidence
**Status:** Broken
**Files:**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EventHandler.java
products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/DataCloudClient.java
products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/EventEnvelopeGoldenTest.java
```

**Evidence**

The event append path now builds a richer canonical event with actor, classification, policy context, provenance, trace context, correlation ID, causation ID, headers, timestamp, tenant, and workspace metadata. 

The `DataCloudClient.Event` model supports those envelope fields. 

But `handleQueryEvents()` and `handleGetEventByOffset()` return only:

```text
offset
type
payload
timestamp
```

They do not expose actor, classification, policy context, provenance, trace context, correlation ID, causation ID, event ID, workspace ID, or headers. 

The golden test claims full lifecycle coverage, but the visible assertions focus on append/persist behavior, not the HTTP query/get response round-trip. 

**Why it matters**

The audit requirement demands proof that all envelope fields survive append → persist → query → replay/tail → Action Plane handoff. Current query/get APIs do not prove or expose that. This breaks provenance, audit evidence, replay correctness, and operator trust.

**Required implementation action**

Return a canonical event envelope DTO from all event read paths:

```text
offset
eventId
type
tenantId
workspaceId
subject
subjectType
actor
classification
policyContext
provenance
traceContext
correlationId
causationId
payload
timestamp
headers
```

Do not flatten away envelope metadata.

**Tests required**

Add end-to-end tests for:

```text
append event with full envelope
query events returns full envelope
get by offset returns full envelope
tail/replay returns full envelope
Action Plane bridge receives full envelope
```

---

## P0-3 — OpenAPI/runtime/UI posture is not a single deterministic route truth

**Area:** Contract Plane / Runtime Truth / UI gating
**Status:** Broken
**Files:**

```text
products/data-cloud/contracts/openapi/data-cloud.yaml
products/data-cloud/contracts/openapi/action-plane.yaml
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java
products/data-cloud/delivery/ui/src/lib/routing/RuntimeTruthPosture.generated.ts
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteActionAccessRegistry.java
```

**Evidence**

The contracts README says `data-cloud.yaml` is canonical, `aep.yaml` is compatibility, and `action-plane.yaml` is the target Action Plane contract, but it also says to “create this during the contract rename migration” even though the file exists. That is stale documentation in the Contract Plane. 

`data-cloud.yaml` describes strict production tenant identity, deprecated tenant hints, policy requirements, sensitive-route audit, and many public surfaces. 

`action-plane.yaml` describes the Action Plane and AEP compatibility, but the fetched path section still exposes `/api/v1/events` for event processing while runtime has moved many Action Plane routes to `/api/v1/action/*`. 

The generated UI Runtime Truth posture still contains legacy `/api/v1/pipelines` paths and marks many entries as `INTERNAL`, while the runtime router registers canonical `/api/v1/action/pipelines`.  

**Why it matters**

A production system cannot have separate truths for contract, runtime router, security metadata, and UI feature gating. This creates false readiness, incorrect UI behavior, and security gaps.

**Required implementation action**

Create one canonical route manifest generated from OpenAPI plus runtime route declarations, then generate all of these from it:

```text
OpenAPI route list
ActiveJ route registration parity check
RouteActionAccessRegistry
EndpointSensitivity / policy metadata
RuntimeTruthPosture.generated.ts
SDK route metadata
UI navigation/action gating
docs route matrix
```

Fail CI if any one of those drifts.

---

# P1 findings

## P1-1 — Context Plane uses in-memory state for a customer-facing runtime surface

**Area:** Context Plane
**Status:** Partial / production-risk
**File:**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/ContextLayerHandler.java
```

**Evidence**

The handler exposes `/api/v1/context` and related snapshot/delete APIs, but it is backed by tenant-scoped `ConcurrentHashMap` structures and explicitly says the context layer is in-memory runtime metadata. 

**Required implementation action**

Move production context storage behind a durable `ContextStore` SPI:

```text
ContextStore
JdbcContextStore
InMemoryContextStore only for local/test
ContextVersionStore
ContextAuditEmitter
ContextRetentionPolicy
```

Fail startup in production if Context Plane is enabled without a durable store.

---

## P1-2 — Runtime has many local/fallback modes; production validation exists but fallback behavior remains broadly embedded

**Area:** Runtime profile / production hardening
**Status:** Partial
**Files:**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java
```

**Evidence**

The server still contains optional dependencies and fallback comments for no-op metrics, heuristic AI assist, in-memory settings, in-memory idempotency, missing transaction manager, discarded traces, and skipped audit in some modes.  

The good news is that production validation now blocks many of these in production/staging/sovereign profiles, including missing auth, audit, policy, durable event store, durable entity store, idempotency, metrics, trace export, transaction manager, completion service, and durable settings. 

**Required implementation action**

Make fallback mode explicit and typed:

```text
RuntimeProfile.LOCAL
RuntimeProfile.TEST
RuntimeProfile.EMBEDDED
RuntimeProfile.STAGING
RuntimeProfile.PRODUCTION
RuntimeProfile.SOVEREIGN
```

Then require every fallback to declare:

```text
allowed profiles
runtime truth status
UI visibility impact
audit message
startup validation rule
test coverage
```

---

## P1-3 — UI uses Runtime Truth, but generated posture metadata is stale and too permissive

**Area:** UI / Runtime Truth / route gating
**Status:** Partial
**Files:**

```text
products/data-cloud/delivery/ui/src/api/surfaces.service.ts
products/data-cloud/delivery/ui/src/lib/routing/RuntimeTruthPosture.generated.ts
products/data-cloud/delivery/ui/src/lib/api/client.ts
```

**Evidence**

The UI correctly calls canonical `/surfaces` and removed `/capabilities` fallback. 

The default API client base URL is `/api/v1`, which explains why `apiClient.get("/surfaces")` maps to `/api/v1/surfaces`. 

But generated posture metadata still includes legacy `/api/v1/pipelines` paths and broad `INTERNAL` sensitivity. 

**Required implementation action**

Regenerate UI route posture from the same canonical manifest used by backend security. Do not allow UI posture to classify canonical mutating routes independently.

---

## P1-4 — CI is strong but still not a hard production-release gate

**Area:** CI / release readiness
**Status:** Partial
**Files:**

```text
.github/workflows/data-cloud-ci.yml
```

**Evidence**

CI includes many useful checks, including contract drift detection, route metadata generation, tenant isolation audit, connector validation, agent governance validation, reuse scorecard, SDK generation, Helm/k8s rendering, and a release gate. 

However, smoke E2E and backup drill are advisory, and dependency vulnerability check, SBOM generation, and SonarQube analysis are configured with `continue-on-error: true`. 

**Required implementation action**

Split CI into two profiles:

```text
PR gate: fast, deterministic, blocks merge
Release gate: full production readiness, blocks release
```

Make release blocking for:

```text
E2E smoke against real deployment
backup/restore drill
dependency vulnerability threshold
SBOM generation
security scan
production profile startup
event envelope round-trip
route metadata parity
runtime truth parity
```

---

## P1-5 — Shared-library boundary is improved but still too broad to certify as healthy

**Area:** Shared libraries / dependency hygiene
**Status:** Partial
**Files:**

```text
settings.gradle.kts
products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md
```

**Evidence**

The root settings include many shared platform modules: workflow, AI integration, governance, agent-core, runtime, audit, messaging, data-governance, identity, contracts, kernel modules, and platform plugins. 

The architecture document already recognizes this risk and says some platform modules became shared only because Data Cloud and AEP were previously separate; it gives move/split guidance for agent-core, workflow, messaging, AI integration, data-governance, and contracts. 

**Required implementation action**

Enforce shared-library decisions with ArchUnit/Gradle checks:

```text
Keep shared only if used by 3+ unrelated products or truly generic
Move Data Cloud plane semantics into products/data-cloud
Block Data Cloud/AEP terms inside platform modules unless explicitly allowed
Block circular or product-to-platform semantic leakage
```

---

# Scorecard

| Area                     | Score |
| ------------------------ | ----: |
| Vision alignment         |   4/5 |
| Feature completeness     |   2/5 |
| Runtime correctness      |   2/5 |
| Contract correctness     |   2/5 |
| UI/API coherence         |   2/5 |
| Security/privacy         |   2/5 |
| Tenant isolation         |   3/5 |
| Governance/audit         |   3/5 |
| Event/action correctness |   2/5 |
| Shared library reuse     |   3/5 |
| Dependency hygiene       |   3/5 |
| Observability            |   3/5 |
| Testing depth            |   3/5 |
| CI/release confidence    |   3/5 |
| Docs truthfulness        |   3/5 |
| Operational readiness    |   2/5 |

---

# Feature completeness matrix

| Plane / Surface     | Status  | Required action                                                                                                       |
| ------------------- | ------- | --------------------------------------------------------------------------------------------------------------------- |
| Experience Plane    | Partial | Regenerate UI route posture from canonical route manifest; remove stale legacy route metadata.                        |
| Contract Plane      | Broken  | Align `data-cloud.yaml`, `action-plane.yaml`, runtime routes, SDKs, and generated metadata.                           |
| Runtime Truth Plane | Partial | Make `/api/v1/surfaces` the only source of UI/runtime gating and include route-level policy/audit/idempotency status. |
| Data Plane          | Partial | Keep validating durable entity storage, tenant safety, idempotency, outbox, and audit under production profile.       |
| Event Plane         | Broken  | Return full canonical envelope on query/get/tail/replay, not only append.                                             |
| Context Plane       | Partial | Replace in-memory production context with durable `ContextStore`.                                                     |
| Intelligence Plane  | Partial | Keep heuristic/local modes disabled in production and expose model/AI readiness through Runtime Truth.                |
| Governance Plane    | Partial | Fix canonical Action Plane critical route policy classification.                                                      |
| Action Plane        | Broken  | Make `/api/v1/action/*` canonical across OpenAPI, router, metadata, tests, SDK, and UI.                               |
| Operations Plane    | Partial | Make smoke, backup, security, SBOM, and production startup checks hard release gates.                                 |
| Shared libraries    | Partial | Enforce move/split/keep decisions with architecture tests.                                                            |

---

# File-by-file implementation plan

## 1. `EndpointSensitivity.java`

Replace hardcoded legacy route-prefix logic with generated metadata lookup.

Implement:

```text
RouteSecurityMetadata metadata = RouteSecurityRegistry.lookup(method, path)
return metadata.sensitivity()
```

Delete or deprecate:

```text
CRITICAL_ROUTE_ACTIONS
SENSITIVE_ROUTE_ACTIONS
DELETE_CRITICAL_PREFIXES for legacy-only paths
manual normalizePath logic
```

Acceptance criteria:

```text
Every canonical /api/v1/action/* route has exact sensitivity.
CRITICAL routes invoke policy engine.
Blocking audit applies in production.
No route falls through to INTERNAL unless explicitly declared.
```

---

## 2. `RouteActionAccessRegistry.java`

Regenerate from the canonical route manifest, not from stale OpenAPI fragments alone.

Add fields:

```text
sensitivity
requiresPolicy
requiresBlockingAudit
requiresIdempotency
runtimeTruthSurface
legacyStatus
```

Acceptance criteria:

```text
Registry has no route that is absent from runtime unless explicitly legacy/deprecated.
Registry has no runtime route missing from metadata.
Generated file fails CI if stale.
```

---

## 3. `DataCloudRouterBuilder.java`

Keep canonical `/api/v1/action/*` routes. Remove remaining un-gated legacy Action Plane routes such as AI pipeline draft paths under `/api/v1/pipelines/*`, or move them under `/api/v1/action/pipelines/*`.

Acceptance criteria:

```text
No Action Plane route is exposed outside /api/v1/action/* unless marked legacy and feature-flagged.
Legacy routes default disabled.
OpenAPI and SDK match runtime.
```

---

## 4. `action-plane.yaml`

Update all canonical Action Plane paths to `/api/v1/action/*`.

Mark AEP compatibility paths explicitly as deprecated, compatibility-only, and non-canonical.

Acceptance criteria:

```text
action-plane.yaml matches runtime router.
aep.yaml is compatibility-only.
data-cloud.yaml references Action Plane consistently.
```

---

## 5. `RuntimeTruthPosture.generated.ts`

Regenerate from backend route metadata.

Acceptance criteria:

```text
No stale /api/v1/pipelines canonical entries.
Canonical /api/v1/action/* entries exist.
Sensitivity matches backend.
Required capabilities include auth, tenant, policy, audit, idempotency where applicable.
```

---

## 6. `EventHandler.java`

Return canonical envelope DTOs from:

```text
handleQueryEvents
handleGetEventByOffset
tail/replay APIs
```

Acceptance criteria:

```text
append response includes canonical identifiers
query response includes all envelope fields
get-by-offset response includes all envelope fields
round-trip tests prove no field loss
```

---

## 7. `EventEnvelopeGoldenTest.java`

Extend from append-only verification to full lifecycle verification:

```text
append
query
get by offset
tail/replay
Action Plane bridge consumption
```

Acceptance criteria:

```text
Every canonical field is asserted after each step.
Test fails if any field is dropped.
```

---

## 8. `ContextLayerHandler.java`

Introduce durable context storage SPI.

Replace:

```text
ConcurrentHashMap tenantContexts
ConcurrentHashMap tenantVersions
ConcurrentHashMap tenantCreatedAt
```

With:

```text
ContextStore
ContextVersionStore
ContextAuditStore
```

Acceptance criteria:

```text
Production profile fails without durable ContextStore.
Local/test may use InMemoryContextStore only with Runtime Truth warning.
```

---

## 9. `.github/workflows/data-cloud-ci.yml`

Promote production-release checks from advisory to blocking in release workflows.

Change:

```text
smoke-e2e --warn-only
backup-drill --warn-only
dependencyCheckAnalyze continue-on-error
cyclonedxBom continue-on-error
SonarQube continue-on-error
```

Into release-blocking gates for release/main protected paths.

Acceptance criteria:

```text
PR remains fast.
Release gate blocks on E2E smoke, backup/restore, SBOM, security threshold, production startup, and route/event/runtime-truth parity.
```

---

# Minimum blocking fixes before shipment

1. Fix canonical Action Plane route sensitivity and policy enforcement.
2. Make route metadata, OpenAPI, runtime router, UI posture, and SDK generation use one canonical route manifest.
3. Fix event query/get/tail/replay to preserve the full canonical event envelope.
4. Replace production Context Plane in-memory storage with durable storage or mark the surface unavailable in production.
5. Make production release gates block on real E2E smoke, security/SBOM, backup/restore, and production startup validation.

Until those are done, this commit should be treated as **architecturally improved but not production-shippable**.
