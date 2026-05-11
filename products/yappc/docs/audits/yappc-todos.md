Below is the implementation plan for YAPPC based on commit `cc8f279c3eb11d15b6e6817499e621dd8b7cb3a9`. Important context: that commit itself is a changelog-only automation commit, so this plan targets the **full codebase snapshot at that ref**, not the one-file diff. 

# YAPPC Production-Grade Implementation Plan

## 0. Current-state read

The latest YAPPC direction is clearer than before:

YAPPC now explicitly treats **Data Cloud+AEP as one merged platform product** consumed only through typed contracts. YAPPC owns the app-creator lifecycle, workspace/project UX, canvas/page/generation/review surfaces, while Data Cloud+AEP owns intelligence execution, retrieval, memory, telemetry, policy, evidence, analytics, and evaluation. 

The route manifest is now a rich governance source of truth with route method/path, auth, owner, boundary, operationId, scopes, audit event type, and privacy classification, and it is intended to drive OpenAPI parity and generated route authorization. 

The backend authorization registry has also moved toward generated manifest usage through `GeneratedRouteRegistry`, route pattern matching, public-route handling, and normalized scope extraction from path, query, and headers. 

The frontend API client is partly migrated toward an OpenAPI-generated adapter model, but it still contains manual helpers, backward-compatible DTOs, manual fetch wrappers, and partial generated-client delegation. 

The generation service has improved with generation run persistence, review status updates, provenance metadata, degraded-AI fallback handling, rollback safety checks, and deterministic fallback generation, but it still has TODO/default-context seams and placeholder-like diff/content handling that must be productionized. 

---

# Phase 1 — Lock the canonical spine

## Goal

Prevent every YAPPC area from creating its own lifecycle, scope, route, artifact, UI, or platform-integration model.

## Work items

### YP-001 — Make canonical docs enforceable, not advisory

**What to do**

Update and enforce:

```text
products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md
products/yappc/docs/guides/terminology-glossary.md
products/yappc/docs/api/route-manifest.yaml
products/yappc/docs/api/openapi.yaml
products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md
```

**Implementation details**

Create a canonical tracker with these sections:

```text
00-canonical-spine
01-access-scope-governance
02-api-contracts-generated-client
03-dashboard-lifecycle-cockpit
04-canvas-authoring
05-page-builder-registry
06-artifact-import-preview
07-generation-diff-review-rollback
08-scaffold-packs-templates
09-data-cloud-aep-contracts
10-observability-operations
11-security-privacy
12-testing-quality-gates
13-docs-repo-cleanup
```

Each tracker item must include:

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

**Done when**

No implementation TODO exists only in chat/audit docs. Every TODO maps to code path, test path, and acceptance gate.

---

### YP-002 — Enforce Product / Project / App terminology

**What to do**

Use the canonical distinction everywhere:

| Term    | Use only for                                  |
| ------- | --------------------------------------------- |
| Project | persisted workspace-scoped delivery container |
| Product | real-world business/customer outcome          |
| App     | runnable/generated/deployed software          |

**Where**

```text
products/yappc/frontend/web/src/**/*
products/yappc/frontend/libs/**/*
products/yappc/core/**/*
products/yappc/docs/**/*
products/yappc/docs/api/openapi.yaml
```

**Implementation details**

Add a lightweight text/AST check:

```text
Project = API IDs, dashboard cards, lifecycle routing, access control
Product = business outcome copy
App = generated/runtime output
```

**Done when**

No UI/API/schema uses Product as a synonym for Project, and no generated runtime output is called Project.

---

# Phase 2 — Route, API, access, and scope hardening

This is the highest-priority phase because it affects every area.

## YP-010 — Make `route-manifest.yaml` the single route source of truth

**What to do**

Finish the route-manifest → OpenAPI → generated client → backend authorization chain.

**Where**

```text
products/yappc/docs/api/route-manifest.yaml
products/yappc/docs/api/openapi.yaml
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/generated/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java
products/yappc/frontend/clients/generated/api/*
products/yappc/frontend/web/src/lib/api/client.ts
```

**Implementation details**

For every route:

1. Route exists in `route-manifest.yaml`.
2. Route exists in `openapi.yaml`.
3. Route has `operationId`.
4. Route has auth mode.
5. Route has scopes.
6. Route has owner.
7. Route has boundary: `YAPPC` or `DATA_CLOUD_AEP`.
8. Route has audit event type.
9. Route has privacy classification.
10. Generated TS client includes it.
11. Backend authorization registry loads it.
12. Contract test verifies parity.

**Important fix**

Validate route manifest naming and generated registry server keys. In the current `RouteAuthorizationRegistry`, route registration loads `getRoutesForServer("yappc-services")`, but `getAuthorizedScopesForRoute` later searches `getRoutesForServer("yappc-lifecycle")`. That must be corrected to the same canonical server key or a shared lookup by method/path across servers. 

**Tests**

```text
RouteManifestSchemaTest
RouteManifestOpenApiParityTest
RouteManifestGeneratedRegistryTest
RouteAuthorizationRegistryTest
FrontendGeneratedClientParityTest
```

**Done when**

Build fails if any route is missing from one layer.

---

## YP-011 — Normalize scope passing end-to-end

**Problem**

Authorization extracts scope from path, query, and headers, but intentionally does not parse request body at authorization time. That means project-scoped POST routes must always provide `workspaceId` and `projectId` through path/query/header before body parsing. 

**What to do**

Define one canonical request-scope strategy:

```text
Preferred:
path params for resource ID where possible
query params for workspace/project context on existing flat routes
headers only for cross-cutting context, not hidden business scope
body scope only validated after authorization, never primary auth input
```

**Where**

```text
products/yappc/frontend/web/src/lib/api/client.ts
products/yappc/frontend/web/src/services/phase/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/*
products/yappc/docs/api/openapi.yaml
```

**Implementation details**

Update all typed API methods so project-scoped calls include:

```text
workspaceId
projectId
actorId when mutation/review
phase when lifecycle operation
artifactId when artifact operation
```

For flat endpoints like:

```text
POST /api/v1/yappc/intent/capture
POST /api/v1/yappc/shape/derive
POST /api/v1/yappc/generate
```

either:

1. Add query/context params consistently, or
2. Move to scoped routes like:

```text
POST /api/v1/projects/{projectId}/lifecycle/intent/capture
POST /api/v1/projects/{projectId}/lifecycle/shape/derive
POST /api/v1/projects/{projectId}/generation/runs
```

Preferred long-term: scoped project routes.

**Tests**

Create matrix tests:

```text
OWNER can read/write
ADMIN can read/write
DEVELOPER can read/write allowed project operations
VIEWER can read only
included project read-only cannot mutate
missing workspaceId fails with precise error
missing projectId fails with precise error
wrong workspaceId denied
wrong tenant denied
```

**Done when**

No route returns generic “Access denied” for a predictable missing-scope case. It returns actionable, precise errors.

---

## YP-012 — Finish generated OpenAPI client migration

**Problem**

`client.ts` delegates some auth/workspace/project calls to generated services, but still has manual fetch helpers, backward-compatible DTOs, and manually typed areas. 

**What to do**

Migrate endpoint groups one by one:

1. Auth
2. Workspaces
3. Projects
4. Dashboard actions
5. Lifecycle phases
6. Phase packet
7. Artifacts
8. Generation
9. Preview
10. Import/review/residual islands
11. Data Cloud+AEP platform contracts

**Where**

```text
products/yappc/frontend/web/src/lib/api/client.ts
products/yappc/frontend/clients/generated/api/*
products/yappc/docs/api/openapi.yaml
products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts
```

**Implementation rules**

Do not delete the adapter in one big bang. Use this sequence:

```text
generated client method exists
adapter delegates to generated client
callers use adapter
tests prove parity
remove duplicate local DTOs
only then remove old manual helper for that domain
```

**Done when**

`client.ts` becomes a thin compatibility facade, not the source of contract truth.

---

# Phase 3 — Dashboard and lifecycle cockpit

## YP-020 — Convert lifecycle cockpit to packet-driven rendering

**Current basis**

The canonical model says each phase route is mounted at `/p/:projectId/:phase`, and phase cockpit data should use project snapshot, activity, readiness preview, dashboard classification, and typed `yappcApi` methods. 

**What to do**

Create a single canonical `PhaseCockpitPacket`.

**Contract**

```ts
type PhaseCockpitPacket = {
  tenantId: string;
  workspaceId: string;
  projectId: string;
  actorId: string;
  phase: 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'learn' | 'evolve';
  project: PhaseProjectSnapshot;
  readiness: PhaseReadiness;
  blockers: PhaseBlocker[];
  requiredArtifacts: ArtifactRequirement[];
  completedArtifacts: ArtifactSummary[];
  activity: PhaseActivityEvent[];
  governance: GovernanceRecord[];
  capabilities: ResourceCapabilities;
  platform?: PlatformRunSummary;
  degraded?: DegradedState;
};
```

**Where**

```text
products/yappc/frontend/web/src/services/phase/*
products/yappc/frontend/web/src/routes/app/project/_phaseCockpit.tsx
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PhasePacketController.java
products/yappc/docs/api/openapi.yaml
```

**Implementation details**

* Replace route-local data stitching with packet builder.
* Keep phase-specific display config separate from phase data.
* All phase actions must call typed API client methods.
* All action buttons must be derived from backend capabilities.

**Done when**

Every lifecycle phase renders from the same packet shape and passes role/scope tests.

---

## YP-021 — Productionize phase-by-phase behavior

Implement each phase independently, but with the same contract and UX rules.

| Phase    | Production-grade behavior                                                       |
| -------- | ------------------------------------------------------------------------------- |
| Intent   | capture intent, normalize, show ambiguity, request user review only when needed |
| Shape    | derive domain/app shape, show assumptions, link to artifacts/evidence           |
| Validate | show blockers, policy results, evidence, completeness, testability              |
| Generate | show generation plan, artifacts, diffs, provenance, review gates                |
| Run      | preview/deploy/run status, rollback/promote gates                               |
| Observe  | runtime signals, errors, preview blocks, user activity, platform health         |
| Learn    | approved learning, feedback, memory-write proposal, no silent mutation          |
| Evolve   | governed improvement proposal, impact, policy decision, approval path           |

**Done when**

Each phase has:

```text
route test
loader test
action test
capability test
error/degraded state test
audit event test
OpenAPI contract test
```

---

## YP-022 — Simplify dashboard into action cockpit

**What to do**

Build dashboard around three user questions:

```text
What is blocked?
What needs review?
What is safe to continue?
```

**Where**

```text
products/yappc/frontend/web/src/routes/dashboard.tsx
products/yappc/frontend/web/src/routes/app/project/_shell.tsx
products/yappc/frontend/web/src/services/phase/NextActionRankingService.ts
products/yappc/frontend/web/src/lib/api/client.ts
```

**Implementation details**

Dashboard layout:

```text
Top: workspace/project context, health, dominant next action
Section 1: blocked work
Section 2: review required
Section 3: safe to continue
Section 4: recent activity/governance
Section 5: platform/preview/generation health
```

**Rules**

* One dominant CTA per project.
* Secondary actions hidden behind “More”.
* Degraded/fallback data must be labeled.
* No fake counts.
* No client-only authorization decisions for mutations.

**Done when**

A user can land on the dashboard and know the next safe action in under 5 seconds.

---

# Phase 4 — Canvas, page builder, registry, and preview

## YP-030 — Stabilize the canvas/document model

**What to do**

Make canvas persistence deterministic and governed.

**Where**

```text
products/yappc/frontend/libs/yappc-canvas/*
products/yappc/frontend/libs/yappc-diagram/*
products/yappc/frontend/libs/yappc-sketch/*
products/yappc/frontend/web/src/components/canvas/*
products/yappc/docs/api/openapi.yaml
```

**Canonical model**

```text
Canvas node
  -> optional linked artifact
  -> optional page document
      -> builder document
```

**Implementation tasks**

1. Define `CanvasDocument` schema.
2. Define node ID, parent ID, linked artifact ID.
3. Add save/load API contract.
4. Add operation metadata for mutations.
5. Add validation before persistence.
6. Add drill-down/deep-link tests.
7. Add large-canvas performance tests.

**Done when**

Canvas save/load round-trips exactly, and governed canvas mutations create trace/audit metadata.

---

## YP-031 — Productionize page builder and component registry

**What to do**

Make page building registry-backed, migratable, and safe.

**Where**

```text
products/yappc/frontend/libs/yappc-page-builder/*
products/yappc/frontend/libs/yappc-artifact-compiler/*
products/yappc/frontend/web/src/components/page-builder/*
products/yappc/docs/api/openapi.yaml
```

**Implementation tasks**

1. Define page document envelope.
2. Define builder document schema.
3. Define component registry contract.
4. Add component validation before insert/save.
5. Add registry versioning.
6. Add migration for old aliases.
7. Add residual/unavailable state for unknown components.
8. Add page operation log.
9. Add conflict/overwrite/review flows.

**Done when**

Invalid or unknown imported components do not crash the builder. They become governed residual/unavailable states.

---

## YP-032 — Harden preview trust and import-source flows

**What to do**

Treat preview as a policy boundary.

**Where**

```text
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PreviewSessionApiController.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PreviewSecurityPolicy.java
products/yappc/frontend/web/src/lib/api/client.ts
products/yappc/frontend/libs/yappc-artifact-compiler/*
products/yappc/docs/api/openapi.yaml
```

**Implementation tasks**

1. Require preview session for controlled/untrusted previews.
2. Enforce tenant/workspace/project/artifact/user scope.
3. Block untrusted direct execution.
4. Require acknowledgement for sensitive data.
5. Surface preview policy blocks in Observe.
6. Add import-source validation before page/canvas mutation.
7. Store residual islands for unmapped imports.
8. Add residual review and registry candidate approval.

**Done when**

No imported source can mutate canvas/page state before validation and trust classification.

---

# Phase 5 — Generation, diff, review, and rollback

## YP-040 — Fix generation context and provenance

**Problem**

Generation currently uses defaults such as `"default-project"` and `"default-workspace"` when metadata is absent, and still has TODOs for loading actual intent. 

**What to do**

Require explicit generation context.

**Contract**

```ts
type GenerationContext = {
  tenantId: string;
  workspaceId: string;
  projectId: string;
  actorId: string;
  phase: 'generate';
  sourceArtifactIds: string[];
  canvasNodeIds?: string[];
  intentId?: string;
  shapeId: string;
  correlationId: string;
};
```

**Where**

```text
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/generate/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/GenerationRunRepository.java
products/yappc/docs/api/openapi.yaml
products/yappc/frontend/web/src/lib/api/client.ts
```

**Done when**

Generation fails fast if project/workspace/actor/source context is missing. No production path uses default project/workspace IDs.

---

## YP-041 — Store generated content, not only content references

**Problem**

The service builds artifacts with `contentRef` and `sizeBytes`, but real content persistence and diff loading must be explicit. 

**What to do**

Add artifact content storage abstraction.

```java
interface ArtifactContentStore {
  Promise<StoredContentRef> put(ArtifactContent content, ArtifactContext context);
  Promise<ArtifactContent> get(String contentRef, ArtifactContext context);
}
```

**Implementation tasks**

1. Store content hash.
2. Store content size.
3. Store MIME/language.
4. Store provenance.
5. Store source evidence IDs.
6. Store generated-by run ID.
7. Add content retrieval authorization.

**Done when**

Diff, preview, review, apply, and rollback can load actual content by governed content reference.

---

## YP-042 — Replace placeholder diff with real structured diff

**Problem**

`computeLineDiff` currently marks content as modified without loading actual content and notes that a production implementation should use a proper diff algorithm. 

**What to do**

Use a real line diff algorithm and store regions.

**Diff model**

```ts
type DiffRegion = {
  id: string;
  filePath: string;
  type: 'addition' | 'deletion' | 'modification';
  oldStartLine: number;
  oldEndLine: number;
  newStartLine: number;
  newEndLine: number;
  originalContent?: string;
  newContent?: string;
  owner: 'system' | 'user' | 'ai';
  provenance: GenerateArtifactProvenance;
};
```

**Done when**

Generated diff view can render real additions/deletions/modifications with provenance.

---

## YP-043 — Review/apply/reject/rollback as idempotent state machine

**What to do**

Make generation review a state machine, not loose status updates.

**States**

```text
GENERATING
GENERATED
REVIEW_PENDING
APPROVED
APPLIED
REJECTED
ROLLBACK_REQUESTED
ROLLED_BACK
FAILED
```

**Rules**

* Apply requires approved or review-pending with actor permission.
* Reject requires reason.
* Rollback requires reason, actor, previous applied state, and safety checks.
* User edits preserve provenance.
* Degraded AI output cannot auto-apply.

**Tests**

```text
apply once succeeds
apply twice is idempotent
reject after apply denied
rollback after apply succeeds
rollback twice idempotent
rollback without reason denied
viewer cannot apply/reject/rollback
degraded output requires review
```

**Done when**

Review decisions are deterministic, auditable, and safe to retry.

---

# Phase 6 — Scaffold, packs, templates, and dependency correctness

## YP-050 — Make scaffold a deterministic service boundary

**What to do**

Keep scaffold engine reusable and separate from lifecycle orchestration.

**Where**

```text
products/yappc/core/scaffold/api/*
products/yappc/core/scaffold/docs/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/generate/*
products/yappc/docs/api/route-manifest.yaml
products/yappc/docs/api/openapi.yaml
```

**Implementation tasks**

1. Define canonical pack metadata.
2. Validate pack structure.
3. Validate required variables.
4. Validate template syntax.
5. Add dry-run result.
6. Add generated file manifest.
7. Add dependency conflict analysis.
8. Add update preview before mutation.
9. Add pack/template/version provenance to generated artifacts.

**Done when**

Every scaffold mutation can be previewed, validated, traced, and rolled back.

---

# Phase 7 — Data Cloud+AEP typed platform integration

## YP-060 — Create YAPPC platform contract client

**Current direction**

YAPPC must consume Data Cloud+AEP through typed contracts only, and must not import platform internals.  The platform mapping doc also defines YAPPC as product/workspace/project-facing lifecycle layer and Data Cloud+AEP as merged intelligence/data platform. 

**What to do**

Add a dedicated platform integration package:

```text
products/yappc/frontend/libs/yappc-platform-contracts/*
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/platform/*
```

**Required contracts**

```text
Agent/intelligence execution
Evidence/retrieval
Memory summary/write proposal
Telemetry/analytics
Policy/guardrails
Execution trace references
```

**Request context**

Every platform call must include:

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

**Response metadata**

Every platform response used in YAPPC UI must include:

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

**Done when**

YAPPC has no direct imports from Data Cloud+AEP internals, only generated/typed contract clients.

---

# Phase 8 — Observability, operations, security, privacy

## YP-070 — Standardize audit, metrics, and readiness

**Where**

```text
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/common/ServiceObservability.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/JdbcAuditLogger.java
products/yappc/docs/operations/*
products/yappc/prometheus.yappc.yml
```

**Implementation tasks**

1. Standardize metric names.
2. Add tags: tenant, workspace, project, phase, operation, outcome.
3. Add readiness checks:

   * database
   * artifact content store
   * preview runtime
   * scaffold packs
   * generated route registry
   * Data Cloud+AEP connectivity
4. Add startup guards:

   * production DB required
   * preview secret required
   * route manifest loaded
   * OpenAPI parity verified
   * migrations applied
5. Add runbooks.

**Done when**

Any lifecycle failure can be traced from UI action → API route → audit event → metric/log → artifact/platform trace.

---

## YP-071 — Enforce privacy classification

**What to do**

Use the route manifest privacy classification in runtime behavior.

**Implementation details**

* `PUBLIC`: no sensitive payloads.
* `INTERNAL`: workspace/project metadata.
* `CONFIDENTIAL`: generated artifacts, source imports, requirements, evidence.
* `RESTRICTED`: rollback, promotion, policy decisions, sensitive previews.

**Done when**

Telemetry and logs redact or avoid restricted payloads by default.

---

# Phase 9 — Testing and quality gates

## YP-080 — Required test suites

Create these as mandatory gates:

```text
1. Route manifest schema test
2. Route manifest ↔ OpenAPI parity test
3. OpenAPI ↔ generated client test
4. Frontend client endpoint coverage test
5. Backend route authorization test
6. Scope extraction matrix test
7. Phase cockpit packet contract test
8. Dashboard action routing test
9. Canvas save/load round-trip test
10. Page builder serialization/migration test
11. Import-source preview trust test
12. Generation diff/review/rollback state-machine test
13. Data Cloud+AEP contract DTO test
14. E2E dashboard → phase → generate → review → run/observe flow
```

The repo already has OpenAPI contract tests that check required paths, endpoint-method matrix, frontend client coverage, telemetry/audit schemas, security schemes, provenance schemas, lifecycle enum aliases, and public operation wording. Build on that instead of creating parallel checks. 

**Done when**

Every touched area has:

```text
unit tests
contract tests
integration tests where backend/API involved
component tests where UI involved
e2e tests for user journey
no placeholder assertions
```

---

# Phase 10 — Docs and repo cleanup

## YP-090 — Consolidate active docs

**Keep active docs small and canonical**

```text
products/yappc/docs/00-vision.md
products/yappc/docs/01-architecture.md
products/yappc/docs/02-domain-model.md
products/yappc/docs/03-api-contracts.md
products/yappc/docs/04-ui-ux.md
products/yappc/docs/05-platform-mapping-yappc-data-cloud-aep.md
products/yappc/docs/06-security-governance.md
products/yappc/docs/07-observability-operations.md
products/yappc/docs/08-testing-quality.md
products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md
```

**Move/remove**

* Move old audits to `docs/archive/`.
* Remove duplicate reports once tracker captures active tasks.
* Remove stale path references.
* Remove old AEP/Data Cloud split wording.
* Remove generated-session docs from active docs.

**Done when**

A contributor can find the source of truth without reading old audit reports.

---

# Recommended execution order

## Sprint 1 — Contract and access foundation

1. YP-001 canonical tracker.
2. YP-010 route manifest source of truth.
3. YP-011 scope normalization.
4. YP-012 generated client migration plan.
5. Route/auth/scope tests.

## Sprint 2 — Dashboard and lifecycle cockpit

1. YP-020 phase packet.
2. YP-021 phase behavior for Intent/Shape/Validate.
3. YP-022 dashboard action cockpit.
4. Role/tier/workspace matrix tests.

## Sprint 3 — Generate/review core

1. YP-040 explicit generation context.
2. YP-041 artifact content store.
3. YP-042 real structured diff.
4. YP-043 review state machine.
5. Generate → diff → apply/reject/rollback tests.

## Sprint 4 — Canvas/page/import/preview

1. YP-030 canvas document schema.
2. YP-031 builder registry validation.
3. YP-032 preview trust/import-source.
4. Page builder and import e2e tests.

## Sprint 5 — Scaffold and platform integration

1. YP-050 scaffold pack/template determinism.
2. YP-060 Data Cloud+AEP typed platform client.
3. Evidence/policy/trace display in Validate/Generate/Review.
4. Platform degraded-state tests.

## Sprint 6 — Operations, cleanup, hardening

1. YP-070 readiness/metrics/audit.
2. YP-071 privacy classification enforcement.
3. YP-080 full quality gates.
4. YP-090 docs/repo cleanup.

---

# Non-negotiable acceptance bar

YAPPC is production-grade only when:

1. Every route is in route manifest, OpenAPI, generated client, and authorization registry.
2. Every mutation is scoped by tenant/workspace/project/actor.
3. Every lifecycle phase uses the same packet model.
4. Every generated artifact has content, provenance, diff, review, and rollback path.
5. Every preview/import path has trust classification.
6. Data Cloud+AEP is consumed only through typed contracts.
7. UI never exposes unauthorized actions.
8. No production code path uses fake/default project/workspace/tenant IDs.
9. No generated/fallback AI output is silently treated as trusted.
10. Tests cover contracts, access matrix, lifecycle flows, and user journeys.
