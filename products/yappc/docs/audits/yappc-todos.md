# YAPPC Production-Grade Implementation Plan

**Document status:** Consolidated canonical implementation plan  
**Intended location in repo:** `products/yappc/docs/audits/YAPPC_PRODUCTION_GRADE_IMPLEMENTATION_PLAN.md`  
**Scope:** YAPPC product in `samujjwal/ghatana`, with clear integration boundary to the merged **Data Cloud+AEP** platform product  
**Purpose:** Provide one complete, gap-free implementation plan to harden YAPPC area-by-area without fragmenting architecture, contracts, UX, APIs, tests, security, governance, or documentation.

---

## 1. Executive Summary

YAPPC should be implemented and hardened as a production-grade **app-creator and product-development lifecycle product**. Work should be tackled independently by area, but all areas must remain coherent through one shared spine:

- canonical terminology
- mounted lifecycle
- workspace/project/artifact scope
- typed API contracts
- route manifest and OpenAPI parity
- authorization and governance
- audit and observability
- design-system consistency
- generated artifact provenance
- preview trust model
- production-grade tests
- documentation cleanup

The important product-boundary clarification is:

> **AEP and Data Cloud are no longer separate product boundaries. They are merged into one Data Cloud+AEP platform product.**

Therefore, the plan must no longer say:

- “AEP product and Data Cloud product”
- “connect to AEP first, then Data Cloud”
- “AEP owns X, Data Cloud owns Y” as separate external products

Instead, use:

> **YAPPC integrates with the merged Data Cloud+AEP platform product for intelligence execution, retrieval, memory, telemetry, evidence, policy, analytics, and evaluation.**

Internally, Data Cloud+AEP may still have subdomains such as agent runtime, memory, search, embeddings, telemetry, evidence, and policy. Externally, YAPPC should treat it as **one platform product exposed through stable typed contracts**.

---

## 2. Product Boundary

## 2.1 YAPPC Product Boundary

YAPPC is the **user-facing app-creator and product-development lifecycle product**.

YAPPC owns:

- workspace management
- project management
- user/project/workspace context
- project lifecycle cockpit
- Prompt → Plan → Confirm → Generate → Preview → Run → Observe → Learn → Evolve UX
- requirements/spec authoring
- canvas-based authoring
- page builder and component authoring
- generated artifact review UX
- approvals and governance UI
- project dashboard
- lifecycle activity feed
- audit and traceability views
- preview/export/review flows
- human decision workflows
- user-facing status, confidence, evidence, and policy explanations

YAPPC must not own:

- generic agent runtime internals
- generic tool runtime internals
- memory engine internals
- search/indexing infrastructure internals
- embeddings infrastructure internals
- telemetry lake internals
- analytics computation engine internals
- model evaluation flywheel internals
- generic policy engine internals outside YAPPC governance consumption
- cross-product data platform internals

YAPPC consumes those capabilities through typed contracts exposed by the merged Data Cloud+AEP platform product.

---

## 2.2 Data Cloud+AEP Product Boundary

Data Cloud+AEP is the **merged intelligence, agent execution, data, retrieval, memory, telemetry, evidence, policy, and evaluation platform product**.

Data Cloud+AEP owns:

- agent registry
- agent orchestration
- task routing
- tool registry
- tool execution
- multi-agent delegation
- working memory
- episodic memory
- semantic memory
- execution traces
- safety and policy checks for agent/tool runs
- raw and curated data storage
- event store
- evidence store
- search indexes
- vector embeddings
- retrieval and RAG
- telemetry and analytics pipelines
- cost and usage history
- model evaluation datasets
- feedback/evaluation loops
- drift detection
- historical project/evidence retrieval
- cross-product intelligence platform capabilities

Internal Data Cloud+AEP subdomains:

```text
Data Cloud+AEP
├── Agent Runtime
├── Agent Registry
├── Tool Runtime
├── Tool Registry
├── Orchestration
├── Memory
├── Search / Retrieval
├── Embeddings
├── Event Store
├── Evidence Store
├── Telemetry / Analytics
├── Cost / Usage
├── Evaluation / Feedback
├── Drift Detection
├── Policy / Guardrails
└── Execution Trace
```

External product boundary:

```text
YAPPC  ──typed contracts──>  Data Cloud+AEP
```

YAPPC should never import or directly depend on internal Data Cloud+AEP runtime, memory, retrieval, analytics, or agent modules.

---

## 2.3 Shared Contracts Boundary

Shared contracts/packages may own:

- domain-neutral DTOs
- API contracts
- event schemas
- policy schemas
- observability conventions
- design tokens
- shared UI primitives
- generated client types
- plugin contracts
- cross-product security abstractions

Shared packages must remain product-neutral. If a type has YAPPC lifecycle semantics, it belongs under YAPPC. If a type has platform intelligence/data semantics, it belongs under Data Cloud+AEP or shared platform contracts.

---

## 3. Canonical Wording

## 3.1 Preferred Wording

Use this wording consistently:

```text
YAPPC integrates with the merged Data Cloud+AEP platform product for intelligence execution, retrieval, memory, telemetry, evidence, policy, analytics, and evaluation.
```

```text
YAPPC owns the app-creator lifecycle experience and project governance. Data Cloud+AEP owns the intelligence/data platform capabilities that power YAPPC behind stable contracts.
```

```text
AEP and Data Cloud are no longer treated as separate product boundaries. They are internal capability domains within the merged Data Cloud+AEP product.
```

```text
YAPPC should consume Data Cloud+AEP through typed contracts, not through direct imports of internal runtime, memory, search, analytics, or telemetry modules.
```

## 3.2 Avoid This Wording

Do not use:

```text
AEP product and Data Cloud product
```

```text
AEP owns X, Data Cloud owns Y
```

```text
Connect YAPPC to AEP first, then connect to Data Cloud
```

```text
Data layer is separate from AEP
```

Use instead:

```text
Connect YAPPC to the Data Cloud+AEP platform contract.
```

```text
Within Data Cloud+AEP, the agent-runtime subdomain handles orchestration and the data/retrieval subdomain handles search, embeddings, telemetry, evidence, and analytics.
```

---

## 4. Canonical Product Model

YAPPC must preserve the following domain distinctions.

## 4.1 Project

A **Project** is the persisted workspace-scoped delivery container.

Use Project for:

- API identifiers
- dashboard cards
- access control
- workspace scoping
- lifecycle routing
- artifact ownership
- activity feeds
- audit records
- phase cockpit

Examples:

```text
/api/projects/{projectId}
[p/:projectId/:phase]
ProjectDashboardAction
PhaseProjectSnapshot
```

## 4.2 Product

A **Product** is the real-world customer or business outcome being shaped by a project.

Use Product for:

- intent descriptions
- strategy copy
- customer/business outcome language
- roadmap language
- retrospective language

Do not use Product as a synonym for Project.

## 4.3 App

An **App** is the runnable software produced, generated, deployed, previewed, or observed from a project.

Use App for:

- generated runtime output
- deployed software
- preview/runtime status
- generated app artifact bundles

Do not use App as a synonym for Project.

---

## 5. Canonical Lifecycle Model

YAPPC mounted lifecycle:

```text
1. Intent
2. Shape
3. Validate
4. Generate
5. Run
6. Observe
7. Learn
8. Evolve
```

Canonical mounted routes:

```text
/p/:projectId/intent
/p/:projectId/shape
/p/:projectId/validate
/p/:projectId/generate
/p/:projectId/run
/p/:projectId/observe
/p/:projectId/learn
/p/:projectId/evolve
```

Every phase cockpit must be driven by the same canonical lifecycle packet.

## 5.1 Lifecycle Packet Inputs

A phase cockpit should receive:

- project snapshot
- workspace context
- actor context
- current phase
- readiness preview
- blockers
- required artifacts
- completed artifacts
- activity feed
- dashboard action classification
- capability model
- governance state
- platform run status, if applicable
- evidence/retrieval summary, if applicable
- preview/generation/runtime health signals

## 5.2 Phase Addition Rule

When adding or changing a phase:

1. Update canonical phase service.
2. Update route copy and cockpit configuration.
3. Update OpenAPI schemas.
4. Update dashboard routing.
5. Update typed API client.
6. Update phase tests.
7. Update lifecycle packet contract.
8. Update implementation tracker.
9. Verify old aliases are handled only through compatibility adapters.

---

## 6. Canonical Artifact Model

An **Artifact** is a governed lifecycle output that can be reviewed, persisted, traced, imported, generated, or deployed.

Artifact families:

- lifecycle artifacts
- generated artifacts
- imported artifacts
- page artifacts
- evidence artifacts
- review artifacts
- preview artifacts
- run artifacts

Artifact rules:

- Every artifact must have explicit tenant/workspace/project scope.
- Generated artifacts must include provenance.
- Imported artifacts must include trust classification.
- Page artifacts must include operation history.
- Reviewable artifacts must include review status.
- High-impact artifact mutations must create audit records.
- Artifacts used by Data Cloud+AEP must include indexing/evidence metadata through contract fields, not hidden coupling.

---

## 7. Canonical Builder Model

YAPPC page builder has three nested concepts:

```text
Canvas node
  → Page document
      → Builder document
```

## 7.1 Canvas Node

A canvas node is a spatial graph node in the project canvas.

It owns:

- node id
- spatial position
- parent/child relationship
- drill-down metadata
- linked artifact reference
- visual type
- layout state

## 7.2 Page Document

A page document is the persisted page artifact envelope.

It owns:

- artifact id
- page document id
- project id
- workspace id
- governance metadata
- preview trust
- sync state
- operation log
- conflict state
- review state
- builder document payload

## 7.3 Builder Document

A builder document is the registry-backed UI-builder model.

It owns:

- component instances
- slots
- props
- styles
- data bindings
- registry versions
- migrations
- serialization format

## 7.4 Builder Acceptance Criteria

- Saving page builder edits persists operation log.
- Reload restores exact builder document.
- Component insertion validates registry contract.
- Invalid components become residual/unavailable states, not crashes.
- Conflict and overwrite flows require reason and audit.
- Preview trust is explicit for every previewable element.
- Builder serialization is covered by tests.

---

## 8. Preview Trust Model

Preview trust is a security and policy boundary, not only a visual preference.

Trust levels:

```text
trusted-local
trusted-controlled
semi-trusted
untrusted
```

Preview rules:

- Components and imported artifacts must declare preview trust metadata.
- Restricted or privacy-sensitive data requires explicit acknowledgement.
- Untrusted artifacts must not execute directly.
- Semi-trusted artifacts require isolation or review.
- Preview policy blocks must be visible to users.
- Preview runtime errors must flow into Observe.
- Preview sessions must be scoped to tenant, workspace, project, artifact, and actor.

---

## 9. Governance Trace Model

Every high-impact action must produce a governance trace.

Required trace dimensions:

- tenant id
- workspace id
- project id
- artifact id where relevant
- actor id
- phase
- operation
- outcome
- timestamp
- changed node ids or file paths where relevant
- reason for review/apply/reject/rollback/overwrite
- data classification
- preview trust
- policy decision where relevant
- Data Cloud+AEP run/trace id where relevant
- evidence ids where relevant

Primary trace surfaces:

- product audit events
- page document operation logs
- generated artifact provenance
- diff region provenance
- dashboard action execution audit
- platform execution trace references
- review decision records

---

## 10. Implementation Tracker

Create or update:

```text
products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md
```

Tracker sections:

```text
00-canonical-spine
01-workspace-project-access
02-lifecycle-cockpit
03-dashboard-ux-shell
04-api-contracts
05-canvas-authoring
06-page-builder-registry
07-artifact-import-preview
08-generation-diff-review
09-scaffold-packs
10-data-cloud-aep-platform-integration
11-observability-operations
12-security-privacy-governance
13-tests-quality-gates
14-docs-cleanup
```

Every tracker item must include:

```text
ID
Area
Problem
Production-grade fix
Files/paths
Contracts affected
Tests required
Acceptance criteria
Status
Owner/notes
```

Tracker rules:

- No TODO exists only in audit output or chat.
- No duplicate TODOs with different names.
- Correctness issues outrank cleanup.
- Security/scope issues outrank UI polish.
- Dead-code cleanup is part of done, not future work.
- Each item links to code/docs/tests.

---

## 11. Implementation Area Map

## 11.1 Area 00 — Canonical Spine

Purpose:

Create the shared foundation that prevents every feature area from inventing its own model.

Owns:

- terminology
- lifecycle phases
- artifact model
- page/builder/canvas model
- preview trust
- governance trace
- implementation tracker
- documentation structure

Key files:

```text
products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md
products/yappc/docs/guides/terminology-glossary.md
products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md
products/yappc/docs/api/openapi.yaml
products/yappc/docs/api/route-manifest.yaml
```

Tasks:

1. Update product boundary wording to Data Cloud+AEP.
2. Ensure Project/Product/App terms are defined and enforced.
3. Ensure mounted lifecycle phases are canonical.
4. Define artifact/page/builder/canvas relationships.
5. Define preview trust levels.
6. Define governance trace dimensions.
7. Create tracker structure.
8. Add architecture fitness rules for forbidden dependencies.

Acceptance criteria:

- Docs no longer imply AEP and Data Cloud are separate products.
- All canonical terms have one definition.
- All code references to lifecycle phases use canonical phase service.
- Build/test/lint can detect obvious boundary violations.

---

## 11.2 Area 01 — Workspace, Project, Access, and Scope

Purpose:

Make project access powerful, flexible, predictable, and scope-safe.

Owns:

- workspace membership
- project ownership/inclusion
- tenant/workspace/project access
- role/capability resolution
- frontend capability rendering
- backend authorization enforcement

Key files:

```text
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationFilter.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcAuthorizationService.java
products/yappc/frontend/libs/yappc-auth/src/*
products/yappc/frontend/web/src/services/workspace/accessControl*
products/yappc/frontend/web/src/lib/api/client.ts
```

Tasks:

1. Implement route pattern matching for parameterized routes.
2. Require tenant/workspace/project/artifact scope by route type.
3. Normalize scope extraction from headers, query params, path params, and request bodies.
4. Return deterministic access errors.
5. Define canonical frontend capabilities.
6. Load capabilities from backend read model.
7. Remove scattered role checks.
8. Add owner/admin/developer/viewer test matrix.
9. Ensure included/read-only projects are handled consistently.

Acceptance criteria:

- Unknown protected routes are denied by default.
- Missing workspace/project scope returns precise error.
- UI does not show invalid primary actions.
- Backend remains source of enforcement.
- Every mutation has tenant/workspace/project/actor context.
- Tests cover allowed and denied paths.

---

## 11.3 Area 02 — Lifecycle Cockpit

Purpose:

Make each phase independently production-grade while keeping all phases coherent.

Owns:

- phase routes
- phase packet
- blockers
- readiness
- required artifacts
- suggested next actions
- phase primary/secondary actions
- phase-specific governance
- phase activity/evidence

Key files:

```text
products/yappc/frontend/web/src/services/phase/*
products/yappc/frontend/web/src/routes/app/project/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PhasePacketController.java
products/yappc/docs/api/openapi.yaml
```

Tasks:

1. Define canonical `PhaseCockpitPacket`.
2. Move phase UI to packet-driven rendering.
3. Implement phase-specific action contracts.
4. Add capability gating for every phase action.
5. Add blockers and readiness display.
6. Add evidence and governance display.
7. Add Data Cloud+AEP run status references where platform actions are triggered.
8. Add per-phase tests.

Required phase coverage:

```text
Intent
Shape
Validate
Generate
Run
Observe
Learn
Evolve
```

Acceptance criteria per phase:

- Route renders.
- Packet loads.
- Invalid scope shows clear error.
- Primary action uses typed API.
- Success updates activity/audit.
- Failure shows specific reason.
- Viewer sees read-only state.
- Developer/Admin/Owner permissions differ correctly.
- Platform run/evidence/policy references are displayed when relevant.

---

## 11.4 Area 03 — Dashboard and UX Shell

Purpose:

Make YAPPC simple, information-rich, and low-cognitive-load.

Owns:

- dashboard
- project cards
- workspace switcher
- action grouping
- project shell
- navigation
- empty/error/degraded states
- visual consistency

Key files:

```text
products/yappc/frontend/web/src/routes/dashboard.tsx
products/yappc/frontend/web/src/routes/app/project/_shell.tsx
products/yappc/frontend/web/src/components/ui/*
products/yappc/frontend/libs/yappc-ui/*
products/yappc/frontend/libs/yappc-product-theme/*
```

Recommended layout:

```text
Top: Workspace/project context + health + primary next action
Row 1: Blocked / Review Required / Safe to Continue
Row 2: Active projects with lifecycle status
Row 3: Recent activity and governance events
Row 4: System signals: preview/runtime/generation/platform health
```

Tasks:

1. Backend-derive dashboard actions.
2. Group actions into blocked/review/safe.
3. Show one dominant action per project.
4. Add reason labels.
5. Add degraded state when fallback/client-derived data exists.
6. Normalize colors, typography, spacing, icons, and buttons.
7. Add empty states with guided action.
8. Add accessibility checks.

Acceptance criteria:

- User can identify next action in under 5 seconds.
- No card has multiple competing CTAs.
- All status labels map to backend state or explicit degraded state.
- No hardcoded fake counts.
- Viewer/read-only projects do not expose mutation actions.
- Dashboard action opens correct phase cockpit.

---

## 11.5 Area 04 — API Contracts

Purpose:

Eliminate frontend/backend drift and make contracts enforceable.

Owns:

- OpenAPI
- route manifest
- typed/generated client
- API parity tests
- REST/GraphQL ownership rules
- compatibility prefixes

Key files:

```text
products/yappc/docs/api/openapi.yaml
products/yappc/docs/api/route-manifest.yaml
products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts
products/yappc/frontend/web/src/lib/api/client.ts
products/yappc/frontend/libs/yappc-core/src/api/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/*
```

Tasks:

1. Make route manifest the route source of truth.
2. Normalize route prefixes.
3. Ensure backend routes appear in OpenAPI.
4. Ensure frontend-used routes appear in OpenAPI.
5. Generate TypeScript client/types from OpenAPI.
6. Replace hand-coded DTOs endpoint-by-endpoint.
7. Block raw fetch outside API infrastructure.
8. Add contract tests for Data Cloud+AEP integration DTOs.

Acceptance criteria:

- Build fails when backend route is missing from OpenAPI.
- Build fails when frontend invokes route missing from OpenAPI.
- Build fails when route lacks authorization metadata.
- Generated client compiles.
- No incompatible duplicate DTOs.
- GraphQL-owned domains are not called through REST client.

---

## 11.6 Area 05 — Canvas Authoring

Purpose:

Make canvas a reliable production authoring surface, not an isolated visual demo.

Owns:

- infinite canvas
- semantic zoom
- drill-down
- node model
- canvas persistence
- spatial relationships
- diagrams
- sketch/whiteboard layer
- canvas-to-artifact links

Key files:

```text
products/yappc/frontend/libs/yappc-canvas/*
products/yappc/frontend/libs/yappc-diagram/*
products/yappc/frontend/libs/yappc-sketch/*
products/yappc/frontend/web/src/components/canvas/*
products/yappc/frontend/web/src/routes/app/project/*
```

Tasks:

1. Define canonical canvas document schema.
2. Define canvas node IDs and linked artifact IDs.
3. Add validation before persistence.
4. Persist canvas changes with operation metadata.
5. Implement semantic zoom and drill-down tests.
6. Integrate diagram and sketch layers through stable adapters.
7. Ensure canvas does not directly own page-builder internals.
8. Add performance tests for large canvases.

Acceptance criteria:

- Canvas save/load is deterministic.
- Linked artifacts remain valid after reload.
- Large canvas does not become unusable.
- Drill-down routes are stable and deep-linkable.
- Sketch/diagram/page nodes use consistent selection and operation patterns.
- Canvas mutations are audited if they affect governed artifacts.

---

## 11.7 Area 06 — Page Builder and Registry

Purpose:

Make page creation, editing, serialization, migration, and preview production-safe.

Owns:

- page document
- builder document
- component registry
- palette
- drag/drop
- property inspector
- style editor
- registry migrations
- page operation log
- page review decisions

Key files:

```text
products/yappc/frontend/libs/yappc-page-builder/*
products/yappc/frontend/libs/yappc-artifact-compiler/*
products/yappc/frontend/web/src/components/page-builder/*
products/yappc/docs/api/openapi.yaml
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/*
```

Tasks:

1. Define page document envelope.
2. Define builder document schema.
3. Define component registry contract.
4. Implement registry validation.
5. Implement component alias migrations.
6. Add operation log for save/load/import/reload/conflict/overwrite.
7. Implement review decision endpoint for page artifacts.
8. Add serialization/deserialization tests.

Acceptance criteria:

- Page builder does not save invalid registry components.
- Reload restores exact builder state.
- Registry version mismatch runs migration or blocks safely.
- Operation log can be exported.
- Conflict/overwrite requires reason.
- Page edits preserve governance trace.

---

## 11.8 Area 07 — Artifact Import and Preview

Purpose:

Make source import, decompile, residual islands, and preview safe and governed.

Owns:

- import-source flow
- import job status
- source validation
- decompile
- component mapping
- residual islands
- registry candidates
- preview sessions
- preview trust enforcement

Key files:

```text
products/yappc/docs/api/openapi.yaml
products/yappc/docs/api/route-manifest.yaml
products/yappc/frontend/web/src/lib/api/client.ts
products/yappc/frontend/libs/yappc-artifact-compiler/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PreviewSessionApiController.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PreviewSecurityPolicy.java
```

Tasks:

1. Implement governed import-source request.
2. Return import job id and status.
3. Validate untrusted input before mutation.
4. Block direct mutation until validation passes.
5. Map source components to registry contracts.
6. Store residual islands for unmapped areas.
7. Add review flow for residual islands.
8. Add registry candidate generation with approval.
9. Enforce preview sessions and trust policy.
10. Surface preview blocks in Observe.

Acceptance criteria:

- Import cannot mutate canvas/page docs until validation passes.
- Residual islands are visible and actionable.
- Untrusted preview never executes directly.
- Preview session requires tenant/workspace/project/artifact/user scope.
- Preview security fails closed in production.
- All import/review/preview decisions are audited.

---

## 11.9 Area 08 — Generation, Diff, Review, Rollback

Purpose:

Make generated output trustworthy, traceable, reviewable, and reversible.

Owns:

- generation plan
- generated artifact proposals
- structured diffs
- provenance
- review decisions
- apply/reject/rollback
- degraded AI fallback state
- generated artifact storage

Key files:

```text
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/generate/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/GenerationRunRepository.java
products/yappc/frontend/web/src/lib/api/client.ts
products/yappc/frontend/web/src/routes/app/project/*
products/yappc/docs/api/openapi.yaml
```

Tasks:

1. Introduce `GenerationPlan`.
2. Store generated content, not only content references.
3. Add file-level provenance.
4. Add diff-region provenance.
5. Replace simple diff with structured diff.
6. Add review decisions:
   - apply
   - reject
   - rollback
   - request changes
7. Mark AI fallback output as degraded.
8. Prevent auto-apply of degraded output.
9. Link generation to Data Cloud+AEP run/trace/evidence where applicable.
10. Add rollback idempotency tests.

Acceptance criteria:

- No generated artifact lacks provenance.
- AI failure does not produce fake success.
- Degraded output requires review.
- Apply/reject/rollback are idempotent.
- Rollback restores previous state.
- Diff view shows ownership and source evidence.
- Every review decision is audited.

---

## 11.10 Area 09 — Scaffold, Packs, Templates, Dependencies

Purpose:

Make scaffold engine reusable, deterministic, and separate from lifecycle orchestration.

Owns:

- pack discovery
- pack metadata
- template rendering
- project creation
- add feature
- update/preview update
- dependency analysis
- dependency conflicts
- generated file manifest

Key files:

```text
products/yappc/core/scaffold/api/*
products/yappc/core/scaffold/docs/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/generate/*
products/yappc/docs/api/route-manifest.yaml
products/yappc/docs/api/openapi.yaml
```

Tasks:

1. Define canonical pack metadata contract.
2. Validate pack structure.
3. Validate template variables.
4. Add generated file manifest.
5. Implement dry-run and preview-update as first-class flows.
6. Add dependency conflict gate.
7. Ensure lifecycle generation invokes scaffold through service boundary.
8. Add HTTP/gRPC/API parity tests where supported.

Acceptance criteria:

- Pack validation catches missing templates/variables/dependencies.
- Dry run shows file changes before mutation.
- Generated files trace to pack/template/version.
- Dependency conflicts block unsafe generation.
- Scaffold routes align with route manifest and OpenAPI.

---

## 11.11 Area 10 — Data Cloud+AEP Platform Integration

Purpose:

Integrate YAPPC with the merged platform product without leaking platform internals into YAPPC.

Owns:

- YAPPC-to-platform contracts
- platform run requests
- execution status
- trace viewer references
- evidence search
- memory query/write hooks where appropriate
- telemetry event emission
- policy evaluation
- degraded/platform-unavailable states

YAPPC must consume Data Cloud+AEP through typed contracts only.

## 11.11.1 Required Platform Contracts

### Agent/intelligence execution

```text
POST /platform/intelligence/runs
GET  /platform/intelligence/runs/{runId}
GET  /platform/intelligence/runs/{runId}/trace
POST /platform/intelligence/runs/{runId}/cancel
```

YAPPC uses this to:

- submit lifecycle task
- inspect orchestration status
- show execution progress
- display trace reference
- cancel long-running work

### Evidence/retrieval

```text
POST /platform/evidence/search
GET  /platform/evidence/artifacts/{artifactId}
POST /platform/evidence/index
```

YAPPC uses this to:

- retrieve relevant project evidence
- show evidence in Validate/Generate/Review
- index approved artifacts
- support traceability

### Memory

```text
POST /platform/memory/query
POST /platform/memory/write
GET  /platform/memory/summary
```

YAPPC uses this to:

- retrieve project-scoped memory summaries
- write approved lifecycle knowledge
- avoid owning memory retention/summarization internals

### Telemetry and analytics

```text
POST /platform/telemetry/events
GET  /platform/analytics/project-summary
GET  /platform/analytics/lifecycle-health
GET  /platform/analytics/model-quality
```

YAPPC uses this to:

- emit lifecycle events
- display health summaries
- show model quality or confidence summaries
- support feedback loops

### Policy and guardrails

```text
POST /platform/policy/evaluate
GET  /platform/policy/decisions/{decisionId}
```

YAPPC uses this to:

- evaluate high-impact operations
- show block/review/approve decisions
- attach policy decisions to audit records

## 11.11.2 Required Platform Request Context

Every YAPPC → Data Cloud+AEP request must include:

```text
tenantId
workspaceId
projectId
actorId
phase
operation
dataClassification
requestedAt
correlationId
```

Where applicable:

```text
artifactId
canvasNodeId
generationRunId
previewSessionId
approvalRequestId
sourceEvidenceIds
```

## 11.11.3 Required Platform Response Metadata

Every Data Cloud+AEP response used by YAPPC must include:

```text
status
confidence
confidenceReason
traceId
evidenceIds
policyDecisionId
degraded
degradedReason
createdAt
completedAt
```

Where applicable:

```text
runId
memoryRecordIds
searchResultIds
modelQualitySummary
costSummary
```

Acceptance criteria:

- YAPPC does not import internal platform runtime/data modules.
- Data Cloud+AEP is treated as one product boundary.
- Platform unavailable state is shown clearly.
- Platform-generated suggestions are not auto-applied.
- Every platform result used for user action has trace/evidence/policy metadata.
- Contract tests cover request/response DTOs.

---

## 11.12 Area 11 — Observability and Operations

Purpose:

Make YAPPC diagnosable and operable in production.

Owns:

- health
- readiness
- metrics
- logs
- audit event emission
- frontend error reporting
- operational dashboards
- alerting docs
- runbooks

Key files:

```text
products/yappc/docs/operations/*
products/yappc/prometheus.yappc.yml
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/common/ServiceObservability.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/JdbcAuditLogger.java
products/yappc/frontend/web/src/lib/api/client.ts
```

Tasks:

1. Standardize metric names and tags.
2. Add tenant/workspace/project/phase/action/outcome tags.
3. Add frontend error reporting with data classification.
4. Add readiness checks:
   - database
   - artifact store
   - preview runtime
   - scaffold packs
   - Data Cloud+AEP platform connectivity
5. Add startup guards:
   - production DB configured
   - preview secret configured
   - unsafe dev flags disabled
   - migrations applied
6. Add operational runbooks.

Acceptance criteria:

- Every lifecycle mutation has audit and metrics.
- Every import/generation/preview/review action has traceability.
- Readiness explains dependency-specific failures.
- Production startup fails fast on unsafe config.
- Logs do not leak secrets or restricted payloads.

---

## 11.13 Area 12 — Security, Privacy, Governance

Purpose:

Make security and governance native, pervasive, and enforced.

Owns:

- authentication
- authorization
- tenant isolation
- workspace/project/artifact isolation
- privacy classification
- preview trust
- audit immutability
- approval rules
- policy decisions
- vulnerability management

Key files:

```text
products/yappc/docs/security/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/*
products/yappc/frontend/libs/yappc-auth/src/*
products/yappc/frontend/web/src/services/workspace/accessControl*
```

Tasks:

1. Enforce tenant/workspace/project/artifact isolation.
2. Add privacy classification to sensitive routes and artifacts.
3. Enforce preview trust levels.
4. Add approval requirement for high-impact actions.
5. Attach policy decision ids to high-impact operations.
6. Ensure audit trail is append-only.
7. Add penetration/security checklist.
8. Add tests for common access-denied causes.

Acceptance criteria:

- No mutation bypasses authorization.
- No preview bypasses trust policy.
- No generated artifact applies without required governance.
- No tenant/workspace leakage in search/evidence/platform calls.
- Security tests cover wrong tenant, wrong workspace, missing scope, stale token, missing permission.

---

## 11.14 Area 13 — Tests and Quality Gates

Purpose:

Make correctness measurable and stop repeated regressions.

Owns:

- unit tests
- component tests
- API contract tests
- backend integration tests
- E2E tests
- accessibility tests
- architecture fitness tests
- performance/resilience tests

Required test pyramid:

```text
Unit tests
  ↓
Component/service tests
  ↓
API contract tests
  ↓
Integration tests
  ↓
E2E journey tests
  ↓
Performance/resilience tests where needed
```

Required E2E journeys:

1. Owner creates workspace/project and enters Intent.
2. Developer advances Shape to Validate.
3. Viewer opens project read-only and cannot mutate.
4. Dashboard action opens correct phase cockpit.
5. Generate produces review-required diff.
6. User rejects generated output.
7. User applies generated output.
8. User rolls back generation.
9. User imports source and reviews residual islands.
10. Untrusted preview is blocked.
11. Data Cloud+AEP platform run is shown with status/trace/evidence.
12. Platform unavailable state is shown without crashing.
13. Policy block is shown and prevents high-impact action.
14. Included/read-only project denies mutation but remains inspectable.

Quality rules:

- No trivial assertions.
- No test theater.
- No tests that only verify mocks.
- No production path depends on test fixtures.
- Contract tests must fail on route/schema drift.
- E2E must validate actual UI action and API behavior.

Acceptance criteria:

- Every touched boundary has meaningful tests.
- Coverage includes success, failure, denied, degraded, and empty states.
- Architecture fitness blocks forbidden dependencies.
- CI runs targeted test suites for touched areas.

---

## 11.15 Area 14 — Documentation and Cleanup

Purpose:

Keep docs and repo structure clean so future audits do not keep rediscovering stale issues.

Owns:

- canonical docs
- archived docs
- duplicate docs cleanup
- dead code cleanup
- invalid import cleanup
- temporary file cleanup
- implementation tracker maintenance

Canonical docs to keep active:

```text
products/yappc/docs/00-vision.md
products/yappc/docs/01-product-requirements.md
products/yappc/docs/02-architecture.md
products/yappc/docs/03-design.md
products/yappc/docs/04-api-contracts.md
products/yappc/docs/05-platform-mapping-yappc-data-cloud-aep.md
products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md
products/yappc/docs/api/openapi.yaml
products/yappc/docs/api/route-manifest.yaml
products/yappc/docs/security/*
products/yappc/docs/operations/*
products/yappc/docs/testing/*
products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md
```

Docs to archive/remove:

- old audit reports
- generated session reports
- duplicate roadmap files
- outdated implementation logs
- stale migration notes
- obsolete TODO docs
- docs that still describe AEP and Data Cloud as separate products

Code cleanup tasks:

- remove unused files
- remove invalid imports
- remove dead compatibility code after migration
- remove placeholder production logic
- remove duplicated DTOs
- remove route-local fetches
- remove deprecated package names
- remove old generated docs not referenced by canonical docs

Acceptance criteria:

- Active docs are small, canonical, and linked.
- Archive docs are clearly not active references.
- Tracker is the only active TODO source.
- No code comments point to obsolete docs.
- No stale docs describe the old AEP/Data Cloud split as current.

---

## 12. Execution Roadmap

## Sprint 1 — Boundary Cleanup and Canonical Spine

Goals:

- fix product boundary wording
- establish implementation tracker
- enforce canonical terminology
- prepare contract/route work

Tasks:

1. Rename platform mapping doc to reflect merged Data Cloud+AEP.
2. Replace old split wording across active docs.
3. Update canonical models with merged platform boundary.
4. Create implementation tracker.
5. Add forbidden dependency rules:
   - YAPPC cannot import internal Data Cloud+AEP runtime/data modules.
6. Add canonical lifecycle/terminology checks.
7. Inventory all YAPPC API routes and frontend calls.

Exit criteria:

- No active doc says AEP and Data Cloud are separate external products.
- Tracker is created.
- Product boundaries are clear.
- Initial route/API inventory exists.

---

## Sprint 2 — API, Route, and Access Spine

Goals:

- make API contract enforcement real
- fix access/scope inconsistency
- prevent route drift

Tasks:

1. Normalize route manifest.
2. Normalize OpenAPI.
3. Add route manifest ↔ OpenAPI test.
4. Add backend route ↔ authorization registry test.
5. Add frontend client ↔ OpenAPI test.
6. Implement route pattern matching in authorization registry.
7. Normalize scope extraction.
8. Add owner/admin/developer/viewer access matrix tests.
9. Start generated API client migration.

Exit criteria:

- Build fails on route/contract/auth drift.
- Missing scope errors are deterministic.
- UI and backend capability models are aligned.
- No new raw fetch is allowed.

---

## Sprint 3 — Dashboard and Lifecycle Cockpit

Goals:

- make YAPPC usable from dashboard to phase cockpit
- drive phases from canonical packet

Tasks:

1. Define `PhaseCockpitPacket`.
2. Implement backend packet endpoint.
3. Migrate phase services to packet.
4. Harden dashboard action groups.
5. Add capability reasons to dashboard and phase actions.
6. Add activity/governance/evidence sections.
7. Add E2E for dashboard → cockpit.
8. Add read-only and denied-state tests.

Exit criteria:

- Every phase route renders from canonical packet.
- Dashboard action opens correct phase.
- User sees clear action/reason/status.
- Viewer cannot mutate but can inspect.

---

## Sprint 4 — Canvas and Page Builder Foundation

Goals:

- make authoring surfaces coherent and persistable

Tasks:

1. Canonicalize canvas document schema.
2. Canonicalize page document envelope.
3. Canonicalize builder document schema.
4. Add registry validation.
5. Add component migration rules.
6. Add operation log.
7. Add save/load/conflict tests.
8. Add large-canvas performance test.

Exit criteria:

- Canvas/page/builder state persists and reloads correctly.
- Invalid registry components are handled safely.
- Page operations are auditable.
- Builder changes preserve governance metadata.

---

## Sprint 5 — Import and Preview Trust

Goals:

- make source import and preview governed and safe

Tasks:

1. Implement governed import-source contract.
2. Add import job status.
3. Add source validation.
4. Add residual island review.
5. Add registry candidate approval.
6. Harden preview session contract.
7. Enforce trust levels.
8. Add untrusted/semi-trusted preview tests.

Exit criteria:

- Untrusted import cannot mutate artifacts.
- Residual islands are visible.
- Preview trust blocks execution when required.
- Preview events flow into Observe/audit.

---

## Sprint 6 — Generation, Diff, Review, Rollback

Goals:

- make generated output trustworthy and reversible

Tasks:

1. Add generation plan.
2. Store generated content and provenance.
3. Replace simple diff with structured diff.
4. Add apply/reject/rollback/request-change.
5. Mark fallback/degraded outputs.
6. Prevent auto-apply of degraded output.
7. Link platform trace/evidence to generation.
8. Add rollback/idempotency tests.

Exit criteria:

- Generated artifacts have provenance.
- Review decisions are auditable.
- Rollback works.
- AI/platform failure is degraded, not fake success.

---

## Sprint 7 — Data Cloud+AEP Platform Integration

Goals:

- connect YAPPC to merged platform product through contracts only

Tasks:

1. Define platform execution DTOs.
2. Define evidence/search DTOs.
3. Define memory DTOs.
4. Define telemetry/analytics DTOs.
5. Define policy/guardrail DTOs.
6. Add generated/typed client for platform contracts.
7. Add phase cockpit platform run status.
8. Add evidence viewer.
9. Add policy decision display.
10. Add unavailable/degraded handling.

Exit criteria:

- YAPPC never imports platform internals.
- Platform responses include trace/evidence/policy/degraded metadata.
- YAPPC renders platform status clearly.
- Contract tests cover platform integration.

---

## Sprint 8 — Scaffold/Packs Integration

Goals:

- make scaffold engine deterministic and governed

Tasks:

1. Validate pack metadata.
2. Validate template variables.
3. Add generated file manifest.
4. Add dry-run/preview-update flow.
5. Add dependency conflict gate.
6. Connect lifecycle generation to scaffold boundary.
7. Add scaffold route/OpenAPI/client parity tests.

Exit criteria:

- Unsafe scaffold changes are blocked.
- Dry-run is available.
- Generated files trace to pack/template/version.
- Dependency conflicts are visible.

---

## Sprint 9 — Observability, Security, and Production Readiness

Goals:

- make the product operable and secure

Tasks:

1. Add readiness checks.
2. Add production startup guards.
3. Standardize metrics tags.
4. Standardize audit fields.
5. Add frontend error reporting.
6. Add runbooks.
7. Add security regression tests.
8. Add privacy classification tests.

Exit criteria:

- Production fails fast on unsafe config.
- Readiness identifies dependency failures.
- Audit and metrics cover all high-impact actions.
- Security tests cover scope/permission failures.

---

## Sprint 10 — Final Cleanup and Acceptance

Goals:

- remove drift, stale docs, dead code, and duplicate logic

Tasks:

1. Consolidate active docs.
2. Archive stale audit/session docs.
3. Remove old split wording.
4. Remove invalid imports.
5. Remove dead compatibility code.
6. Remove duplicated DTOs.
7. Run full E2E suite.
8. Run architecture fitness checks.
9. Update implementation tracker statuses.
10. Produce final production readiness report.

Exit criteria:

- Active docs are canonical and minimal.
- No stale AEP/Data Cloud split remains.
- No known duplicate DTO route logic remains.
- Full acceptance checklist passes.

---

## 13. Cross-Cutting Acceptance Gates

Every area must pass these gates before closing.

## 13.1 Contract Gate

- OpenAPI updated.
- Route manifest updated.
- Typed client updated/generated.
- Backend route registered.
- Authorization metadata added.
- Contract test added.

## 13.2 Scope Gate

- tenant id explicit.
- workspace id explicit.
- project id explicit.
- artifact id explicit where applicable.
- actor id explicit.
- phase explicit where applicable.

## 13.3 UX Gate

- UI uses shared components.
- UI has clear loading/empty/error/degraded states.
- Primary action is obvious.
- No hidden critical state.
- No cognitive overload.
- Accessibility is tested.

## 13.4 Security/Governance Gate

- authorization enforced.
- privacy classification added.
- audit event emitted.
- preview trust applied where relevant.
- policy decision attached where relevant.
- approval required for high-impact action.

## 13.5 Observability Gate

- success metric emitted.
- failure metric emitted.
- degraded metric emitted where applicable.
- logs include correlation id.
- logs do not leak secrets.
- audit event includes scope and actor.

## 13.6 Testing Gate

- unit tests added.
- contract tests added.
- integration tests added.
- E2E tests added where user journey changes.
- denied/failure/degraded states tested.
- no test theater.

## 13.7 Cleanup Gate

- duplicate code removed.
- stale docs updated or archived.
- invalid imports removed.
- temporary files removed.
- tracker updated.
- no new TODO without tracker item.

---

## 14. Required Architecture Fitness Rules

Add automated checks for:

1. YAPPC cannot import internal Data Cloud+AEP runtime modules.
2. YAPPC frontend cannot use raw fetch outside API infrastructure.
3. Web app UI imports must go through approved app/component barrel.
4. Backend protected routes must appear in authorization registry.
5. Backend routes must appear in route manifest.
6. Route manifest routes must appear in OpenAPI.
7. Frontend API client routes must appear in OpenAPI.
8. Lifecycle phase aliases must only be normalized in canonical phase service.
9. Generated artifact DTOs must include provenance.
10. Previewable artifacts must include preview trust metadata.
11. Production code cannot reference test mocks/stubs.
12. Active docs cannot contain old “AEP and Data Cloud as separate product” wording.

---

## 15. Required YAPPC ↔ Data Cloud+AEP Integration Events

YAPPC emits:

```text
workspace.created
workspace.updated
project.created
project.updated
project.lifecycle_phase_changed
requirement.submitted
requirement.approved
requirement.rejected
artifact.created
artifact.updated
artifact.imported
artifact.reviewed
generation.requested
generation.reviewed
generation.applied
generation.rejected
generation.rolled_back
preview.session_created
preview.blocked
run.requested
run.promoted
run.rolled_back
policy.review_required
policy.blocked
user.feedback_submitted
```

Data Cloud+AEP returns or emits references for:

```text
platform.run.started
platform.run.completed
platform.run.failed
platform.run.cancelled
platform.trace.created
platform.evidence.indexed
platform.evidence.retrieved
platform.memory.updated
platform.policy.evaluated
platform.recommendation.generated
platform.telemetry.processed
platform.evaluation.completed
platform.drift.detected
```

YAPPC may display these events, but should not own platform-internal processing.

---

## 16. Required Data Models / DTO Families

## 16.1 YAPPC-owned DTOs

```text
Workspace
WorkspaceMember
Project
ProjectCapability
ProjectDashboardAction
PhaseCockpitPacket
LifecyclePhase
Artifact
PageArtifact
PageDocument
BuilderDocument
CanvasDocument
GenerationPlan
GeneratedArtifactProposal
GeneratedArtifactDiff
GenerationReviewDecision
PreviewSession
AuditEvent
ApprovalRequest
```

## 16.2 Data Cloud+AEP contract DTOs

```text
PlatformRunRequest
PlatformRunStatus
PlatformRunTrace
PlatformEvidenceSearchRequest
PlatformEvidenceSearchResult
PlatformMemoryQueryRequest
PlatformMemorySummary
PlatformTelemetryEvent
PlatformAnalyticsSummary
PlatformPolicyEvaluationRequest
PlatformPolicyDecision
PlatformRecommendation
```

## 16.3 Shared DTOs

```text
TenantScope
WorkspaceScope
ProjectScope
ActorScope
ArtifactScope
DataClassification
PreviewTrustLevel
CorrelationContext
ErrorResponse
ProblemDetails
Pagination
Sort
Filter
```

---

## 17. Required UI States

Every major UI surface must support:

```text
loading
loaded
empty
error
access denied
read-only
blocked
review required
safe to continue
degraded
platform unavailable
policy blocked
approval pending
conflict
out of sync
stale data
```

No UI surface should collapse all failure states into a generic error.

---

## 18. Required Error Taxonomy

Use precise error types:

```text
MISSING_TENANT_SCOPE
MISSING_WORKSPACE_SCOPE
MISSING_PROJECT_SCOPE
MISSING_ARTIFACT_SCOPE
ACCESS_DENIED
TENANT_MISMATCH
WORKSPACE_MISMATCH
PROJECT_NOT_FOUND
ARTIFACT_NOT_FOUND
ROUTE_NOT_REGISTERED
CONTRACT_MISMATCH
VALIDATION_FAILED
POLICY_BLOCKED
APPROVAL_REQUIRED
PREVIEW_TRUST_BLOCKED
GENERATION_DEGRADED
PLATFORM_UNAVAILABLE
PLATFORM_TIMEOUT
IMPORT_VALIDATION_FAILED
REGISTRY_COMPONENT_UNSUPPORTED
CONFLICT_DETECTED
ROLLBACK_UNAVAILABLE
```

Frontend should map these to clear user-facing messages and suggested next actions.

---

## 19. Final Production Acceptance Checklist

YAPPC is production-grade when all of the following are true:

### Product Boundary

- YAPPC product boundary is clear.
- Data Cloud+AEP is treated as one merged platform product.
- YAPPC consumes Data Cloud+AEP through typed contracts only.
- No active docs describe AEP and Data Cloud as separate external products.

### Contracts

- Every route is in route manifest.
- Every route is in OpenAPI.
- Every frontend REST call uses typed/generated client.
- Every protected backend route is in authorization registry.
- Contract tests fail on drift.

### Scope and Access

- Every mutation includes tenant/workspace/project/actor scope.
- Artifact mutations include artifact scope.
- Access denial is deterministic and explainable.
- Owner/Admin/Developer/Viewer flows are tested.

### Lifecycle

- Every phase renders from canonical packet.
- Every phase has blockers/readiness/actions/governance/activity.
- Dashboard actions route to correct phase.
- Phase actions update audit/activity.

### UI/UX

- Dashboard has no cognitive overload.
- Every page has loading/empty/error/degraded/read-only states.
- Visual design is consistent.
- Accessibility checks pass.
- No fake/demo data in production paths.

### Canvas/Page Builder

- Canvas state persists and reloads.
- Page documents preserve builder state.
- Registry validation is enforced.
- Invalid components become governed residual states.
- Operation logs are recorded.

### Import/Preview

- Source import is governed.
- Residual islands are reviewable.
- Preview trust is enforced.
- Untrusted preview cannot execute.
- Preview policy blocks are visible.

### Generation

- Generated artifacts have provenance.
- Diff regions have provenance.
- Apply/reject/rollback are implemented and tested.
- AI/platform fallback is marked degraded.
- Degraded output cannot auto-apply.

### Data Cloud+AEP Integration

- Platform runs include trace/evidence/policy references.
- Evidence retrieval is tenant/workspace/project scoped.
- Platform unavailable/degraded states are handled.
- Platform recommendations are never silently applied.
- Platform contracts are tested.

### Observability

- Metrics exist for success/failure/degraded states.
- Audit records exist for high-impact actions.
- Readiness checks identify dependency failures.
- Production startup guards are enforced.
- Logs avoid secrets and restricted data.

### Tests

- Unit tests cover local logic.
- Contract tests cover APIs and DTOs.
- Integration tests cover backend behavior.
- E2E tests cover critical user journeys.
- Security tests cover access/scope failures.
- No test theater.

### Cleanup

- Implementation tracker is current.
- Old audit/session docs are archived.
- Dead code is removed.
- Duplicate DTOs are removed.
- Invalid imports are removed.
- Stale split wording is removed.

---

## 20. Suggested Final Repo Changes

Recommended final doc/file changes:

```text
ADD:
products/yappc/docs/audits/YAPPC_PRODUCTION_GRADE_IMPLEMENTATION_PLAN.md
products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md

RENAME OR REPLACE:
products/yappc/docs/05-platform-mapping-yappc-aep.md
→ products/yappc/docs/05-platform-mapping-yappc-data-cloud-aep.md

UPDATE:
products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md
products/yappc/docs/api/openapi.yaml
products/yappc/docs/api/route-manifest.yaml
products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts
products/yappc/frontend/web/src/lib/api/client.ts
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationFilter.java
products/yappc/frontend/web/src/services/phase/*
products/yappc/frontend/web/src/routes/dashboard.tsx
products/yappc/frontend/web/src/routes/app/project/*
```

---

## 21. Implementation Principle

The implementation should not try to “add more features” first. It should make the current product architecture correct, coherent, secure, tested, and production-grade.

The north star:

> Build YAPPC as a simple, powerful, AI-assisted app-creator lifecycle product where the user sees clear actions, evidence, confidence, governance, and outcomes — while Data Cloud+AEP provides the merged intelligence/data platform behind stable contracts.

