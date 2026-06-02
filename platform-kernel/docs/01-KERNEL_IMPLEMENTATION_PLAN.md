## Why the score is stuck around 2.5–2.7

The issue is not lack of features. The issue is that several “canonical” systems exist **beside** fallback, legacy, compatibility, or partial runtime paths. That keeps the score low because every new feature inherits ambiguity.

The next iteration should **not** tackle everything. It should only attack dimensions below 3:

| Dimension                            | Current problem                                                                                                 | Next-score lever                                               |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| Runtime truth / UI route correctness | Backend `/surfaces`, static UI route registry, capability aliases, hardcoded nav, and duplicate routes coexist. | Collapse to one route/surface truth.                           |
| Security / authorization             | Permission derivation exists, but route permissions are incomplete and media has TODO permission checks.        | Central route policy envelope.                                 |
| AEP / Action Plane                   | Placement is settled, but semantic lifecycle is partial.                                                        | Implement missing semantic lifecycle root contracts.           |
| Agent runtime                        | Memory read/write checks exist, but search delegates tenant safety downstream.                                  | Enforce scoped search policy at source.                        |
| Audio-video                          | Media lifecycle exists, but permission and processor/job lifecycle are incomplete.                              | First-class secured media workflow.                            |
| Connectors                           | Handler exists, but production runtime path is degraded/fallback-heavy.                                         | One real connector journey end-to-end.                         |
| UI/UX cognitive load                 | Simplified IA exists, but duplicate routes and preview/fallback logic still leak complexity.                    | Remove duplicate/legacy paths and make runtime truth drive UX. |

Release-readiness/evidence execution remains out of scope. The repo itself says this iteration excludes release-readiness/evidence workflows and says not to run release-readiness check tasks or evidence generation.   

## Imperative implementation note

**Do not duplicate logic, code, files, registries, route definitions, permissions, schemas, surface metadata, UI components, i18n keys, mocks, fallback clients, or compatibility handlers. Fix the canonical owner and delete or convert all competing paths into generated aliases, redirects, or tests. Do not create “temporary” parallel implementations. Do not add release-readiness execution or evidence-generation work in this pass.**

---

# Next iteration goal

Target a jump from **2.7 → 3.6+** by fixing root causes only.

The next pass should be these five workstreams, in this order:

1. Runtime Truth + route/nav consolidation.
2. Backend permission/policy envelope.
3. Connector production path.
4. Media/audio-video first-class workflow.
5. AEP/agent semantic safety.

---

# File-by-file task list

## Workstream 1 — Runtime Truth, route, navigation, and UI gating consolidation

### `products/data-cloud/delivery/ui/src/routes.tsx`

**Problem:** Routes are duplicated. `pipelines` is defined once as the primary route tree and then again in the compatibility section with the same paths.  

**Tasks:**

1. Delete duplicate compatibility route entries for:

   * `pipelines`
   * `pipelines/new`
   * `pipelines/:id`

2. Keep compatibility only where the legacy path is actually different:

   * `workflows` → `/pipelines`
   * `workflows/new` → `/pipelines/new`
   * `workflows/:id` → `/pipelines/:id`

3. Convert all compatibility routes to `<Navigate />` redirects unless they truly need to preserve route params.

4. Remove direct rendering of page components from compatibility blocks.

5. Add `allowPreview` and `allowPreviewFor` explicitly for operator-preview routes that are meant to render:

   * `/alerts`
   * `/media/artifacts`
   * `/memory`
   * `/entities`
   * `/fabric`
   * `/agents`
   * `/plugins`
   * `/connectors`, only if backend surface marks it preview and audience-allowed.

6. Ensure target-only routes, especially `/context`, never pass preview flags.

**Acceptance criteria:**

* No duplicate route path exists.
* Compatibility paths redirect only.
* Target-only surfaces render disabled state only.
* Preview surfaces render only when backend and route explicitly allow preview.

---

### `products/data-cloud/delivery/ui/src/components/security/RuntimeCapabilityRouteGate.tsx`

**Problem:** Preview gating is internally contradictory. The gate calculates `previewAllowed`, but then calls `isSurfaceAvailable(signal)` without passing preview options. `isSurfaceAvailable` returns false for preview unless `allowPreview` is passed.  

**Tasks:**

1. Replace:

```ts
const allowed =
  isSurfaceAvailable(signal) && (!isPreviewStatus || previewAllowed);
```

with logic that passes preview options into `isSurfaceAvailable`.

2. Make preview availability depend on:

   * `allowPreview === true`
   * backend `signal.status === "PREVIEW"`
   * backend `signal.audience` matching `allowPreviewFor`, or `allowPreviewFor === "admin"`

3. Add explicit denial for:

   * `signal.targetOnly === true`
   * `signal.readinessClass === "target-only"`

4. Remove fallback permissiveness when `signal` is missing.

5. Add unit tests for:

   * LIVE allowed
   * DEGRADED allowed with banner
   * PREVIEW denied by default
   * PREVIEW allowed for matching audience
   * PREVIEW denied for wrong audience
   * TARGET-ONLY always denied
   * missing surface denied

**Acceptance criteria:**

* Preview behavior works as intended.
* Target-only cannot render real content.
* Missing runtime truth does not render optional surfaces.

---

### `products/data-cloud/delivery/ui/src/api/surfaces.service.ts`

**Problem:** The service still keeps capability compatibility types and `fetchCapabilityRegistry`, even though `/api/v1/capabilities` is removed and `/surfaces` is canonical. 

**Tasks:**

1. Remove runtime usage of:

   * `CapabilityStatus`
   * `CapabilitySignal`
   * `CapabilityRegistrySnapshot`
   * `fetchCapabilityRegistry`
   * `getCapabilitySignal`

2. Keep backward-compatible types only inside tests if truly needed.

3. Change `getSurfaceSignal` aliasing so aliases are generated from backend surface metadata or generated route manifest, not hardcoded in `SURFACE_ALIASES`.

4. Move hardcoded alias table out of runtime code into a generated compatibility map.

5. Sort surfaces by backend `sortOrder` first, then label.

6. Ensure `label`, `description`, and fallback strings prefer `labelKey` / `descriptionKey`.

**Acceptance criteria:**

* Runtime code consumes only `SurfaceSignal`.
* Capability compatibility is not used by application code.
* Surface aliases are generated, not manually maintained.

---

### `products/data-cloud/delivery/ui/src/layouts/DefaultLayout.tsx`

**Problem:** Navigation claims to be backend-driven but still filters through hardcoded `corePaths` and `managePaths`. 

**Tasks:**

1. Delete hardcoded path sets:

   * `corePaths`
   * `managePaths`
   * `_hiddenPaths`

2. Build navigation sections from backend fields:

   * `routeGroup`
   * `primaryNavigation`
   * `contextualNavigation`
   * `sortOrder`
   * `discoverable`
   * `minimumShellRole`

3. Sort within each group by `sortOrder`.

4. Render only surfaces whose backend runtime state is:

   * LIVE
   * DEGRADED
   * allowed PREVIEW for the active product view mode

5. Hide target-only and disabled surfaces from navigation.

6. Replace product view mode labels/descriptions with i18n keys if missing.

7. Do not expose raw shell role controls outside development. The file already hides the raw role switcher in production; keep that and do not add another switcher. 

**Acceptance criteria:**

* Backend surface registry controls navigation.
* No hardcoded primary nav path list remains.
* Changing backend `discoverable`, `routeGroup`, or `sortOrder` changes navigation without code edits.

---

### `products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts`

**Problem:** Static registry still acts as route truth for role guards and compatibility, even though comments say it is fallback-only. 

**Tasks:**

1. Rename this file to make ownership explicit, for example:

   * `GeneratedRouteSurfaceFallback.ts`
   * or `StaticRouteSurfaceFallback.ts`

2. Remove it from runtime authorization decisions.

3. Use it only for:

   * tests
   * generated fallback display during backend boot failure
   * route manifest drift tests

4. Delete duplicated metadata that backend `/surfaces` already owns:

   * label
   * lifecycle
   * preview audience
   * discoverability
   * capabilities

5. Generate fallback content from the same source that produces backend surface metadata.

6. Remove backward-compatible exports:

   * `RouteCapabilitySchema`
   * `RouteCapability`
   * `RouteCapabilityRegistrySchema`
   * `RouteCapabilityRegistry`
   * `canonicalRouteRegistry`
   * `getDiscoverableRoutes`
   * `getRouteByPath`
   * `getActiveRoutes`
   * `getRoutesByLifecycle`

**Acceptance criteria:**

* Runtime app cannot make route availability decisions from static metadata.
* Fallback registry is generated and test-only/runtime-fallback-only.

---

### `products/data-cloud/delivery/ui/src/components/security/RoleProtectedRoute.tsx`

**Problem:** Route role protection resolves from static route registry, not live backend surface truth.  

**Tasks:**

1. Stop importing `getRouteSurfaceByPath` from static registry.

2. Resolve required role from backend surface registry or generated route manifest.

3. If backend surface truth is loading:

   * block protected route rendering
   * show safe loading state

4. If backend surface truth is unavailable:

   * fail closed for non-primary routes
   * allow only home/data routes if product policy permits

5. Keep note that shell role is UI disclosure only; backend authorization remains mandatory.

6. Add tests for:

   * role insufficient
   * role sufficient
   * backend unavailable
   * route not found
   * target-only route

**Acceptance criteria:**

* UI role disclosure is consistent with backend surface truth.
* Static registry no longer gates routes.

---

### `products/data-cloud/delivery/ui/src/pages/DisabledSurfacePage.tsx`

**Tasks:**

1. Make disabled state content fully backend-driven:

   * `fallbackReason`
   * `recommendedAction`
   * `requiredDependencies`
   * `dependencyProbes`
   * `runtimeProfile`
   * `ownerPlane`

2. Replace raw strings with i18n keys.

3. Add specific visual treatments for:

   * unavailable
   * disabled
   * misconfigured
   * target-only
   * preview-not-allowed

4. Ensure target-only messaging does not sound like a user-actionable error.

**Acceptance criteria:**

* Disabled state is informative but not noisy.
* No raw user-visible strings.

---

## Workstream 2 — Backend route policy and authorization consolidation

### `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/security/RequestContextResolver.java`

**Problem:** Permission derivation is too narrow. `DataSourceRegistryHandler` requires `connector:register`, but the role-derived permission sets do not include connector permissions.  

**Tasks:**

1. Add canonical connector permissions:

   * `connector:read`
   * `connector:register`
   * `connector:update`
   * `connector:delete`
   * `connector:test`
   * `connector:sync`
   * `connector:rotate-credentials`
   * `connector:link-dataset`

2. Add canonical media permissions:

   * `media:artifact:create`
   * `media:artifact:read`
   * `media:artifact:delete`
   * `media:artifact:update-consent`
   * `media:artifact:process`
   * `media:artifact:retry`
   * `media:artifact:read-result`

3. Add canonical action permissions:

   * `action:pipeline:read`
   * `action:pipeline:write`
   * `action:pipeline:execute`
   * `action:agent:read`
   * `action:agent:execute`
   * `action:pattern:read`
   * `action:pattern:write`
   * `action:pattern:activate`
   * `action:review:approve`

4. Map permissions by role:

   * ADMIN gets all product admin/configure/connector/media/action permissions.
   * OPERATOR gets read, execute, process, sync, retry, but not destructive/admin permissions.
   * VIEWER gets read-only surface/data/media metadata where safe.

5. Do not read permissions from headers in production. Keep existing production rejection behavior. 

6. Add tests proving ADMIN can register connector and OPERATOR cannot rotate credentials unless explicitly allowed.

**Acceptance criteria:**

* Every route-required permission can be derived from canonical roles.
* No handler requires a permission that no role can ever get.

---

### `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java`

**Problem:** Request context resolution exists, but route-level policy is still manually called by handlers. 

**Tasks:**

1. Add helper methods:

   * `requirePermission(HttpRequest request, String permission)`
   * `requireAnyPermission(HttpRequest request, Set<String> permissions)`
   * `requireAllPermissions(HttpRequest request, Set<String> permissions)`

2. Ensure each helper:

   * resolves `RequestContext`
   * returns consistent `401/403`
   * includes request ID/correlation ID
   * records denied audit event hook if audit service is configured

3. Replace direct handler-level permission boilerplate with this helper.

4. Add typed error envelope for permission failures.

**Acceptance criteria:**

* Permission checks have one implementation.
* Handler code does not manually reconstruct auth behavior.

---

### `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`

**Problem:** Routes are registered directly with handlers. There is no visible centralized route policy wrapper, and release-readiness routes exist even though this pass must not execute those workflows.  

**Tasks:**

1. Introduce a route registration helper that takes:

   * HTTP method
   * path
   * surface ID
   * permission
   * sensitivity
   * handler

2. Use this helper for all Data Cloud product routes.

3. Attach route metadata to runtime surface schema.

4. Gate release-readiness routes behind an explicit non-default feature flag and make them non-discoverable.

5. Do not add any task to run release-readiness endpoints.

6. Replace the hardcoded log line `route groups = 29` with actual route group count. 

7. Remove `/data-fabric/*` connector aliases from primary registration path. Keep them only as redirect/compatibility if required. 

**Acceptance criteria:**

* Every route has surface + permission metadata.
* Route policy is centralized.
* Release-readiness routes are inert for this iteration.

---

### `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`

**Problem:** Media controller explicitly has TODO permission checks across multiple endpoints.  

**Tasks:**

1. Remove permission TODO comments after central policy wrapper is implemented.

2. Add route metadata requirements:

   * `POST /api/v1/media/artifacts` → `media:artifact:create`
   * `GET /api/v1/media/artifacts` → `media:artifact:read`
   * `GET /api/v1/media/artifacts/{id}` → `media:artifact:read`
   * `DELETE /api/v1/media/artifacts/{id}` → `media:artifact:delete`
   * `PATCH /api/v1/media/artifacts/{id}` → `media:artifact:update-consent`
   * `POST /transcribe` → `media:artifact:process`
   * `POST /analyze` → `media:artifact:process`
   * `POST /index-multimodal` → `media:artifact:process`
   * `POST /retry` → `media:artifact:retry`
   * `GET /jobs`, `/transcript`, `/frame-index` → `media:artifact:read-result`

3. Keep controller thin; do not put authorization business logic inside the controller.

4. Return typed error envelopes consistently.

**Acceptance criteria:**

* No media endpoint can be invoked without backend permission.
* Controller remains HTTP parsing/delegation only.

---

## Workstream 3 — Connector production path

### `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandler.java`

**Problem:** Connector handler has good structure but still allows degraded operation when runtime is unavailable. That is truthful, but production readiness requires at least one real runtime-backed path.  

**Tasks:**

1. Use central `HttpHandlerSupport.requirePermission` for every operation:

   * list
   * register
   * update
   * delete
   * test
   * enable
   * disable
   * rotate credentials
   * health
   * schema
   * sync
   * sync status
   * capabilities
   * dataset link

2. Enforce connector runtime required in staging/production/sovereign for:

   * test
   * schema
   * sync
   * health
   * capabilities

3. Keep metadata-only operations available only where safe:

   * list
   * get
   * disabled runtime status

4. Persist structured connection fields:

   * `connectionId`
   * `type`
   * `state`
   * `health`
   * `schemaSnapshotId`
   * `lastSyncStartedAt`
   * `lastSyncCompletedAt`
   * `lastSyncStatus`
   * `lastError`
   * `datasetLinks`
   * `credentialVersion`
   * `credentialRotatedAt`

5. Redact credentials from every response. Existing constants `CREDENTIALS_KEY` and `SECRET_REFERENCE_KEY` should be used consistently. 

6. Ensure `handleDatasetLink` creates durable Data Cloud dataset linkage, not just connector metadata.

7. Ensure idempotency is used for mutating operations:

   * register
   * update
   * delete
   * enable
   * disable
   * rotate
   * sync
   * dataset-link

8. Emit operation records and audit events for every mutation.

**Acceptance criteria:**

* One connector can complete: register → test → schema → sync → dataset link → health/status.
* Production profiles do not pretend connector runtime is live when it is unavailable.
* Credentials never leak.

---

### `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandlerTest.java`

**Tasks:**

1. Add test: ADMIN can register connector.
2. Add test: OPERATOR cannot rotate credentials without permission.
3. Add test: VIEWER can list only if policy allows read.
4. Add test: production profile rejects runtime operations when `fabric == null`.
5. Add test: local profile returns truthful degraded response when `fabric == null`.
6. Add test: credentials are redacted on list/get.
7. Add test: sync requires idempotency when configured.
8. Add test: dataset-link persists linkage.

**Acceptance criteria:**

* Connector behavior is deterministic by profile, role, and runtime availability.

---

### `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/ConnectorLifecycleTest.java`

**Tasks:**

1. Add full lifecycle test:

   * register
   * get
   * test
   * schema
   * sync
   * sync status
   * dataset-link
   * health
   * disable
   * delete

2. Use one fake but real in-process `DataFabricConnector`, not mocks that bypass behavior.

3. Assert operation records are created.

4. Assert audit events are created.

**Acceptance criteria:**

* Connector journey is verified once, end-to-end, without release-readiness execution.

---

## Workstream 4 — Audio-video / media first-class workflow

### `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactService.java`

**Problem:** Service has consent/lifecycle/event/operation structure, but processing triggers only update state and emit requested events. There is no complete durable processing job lifecycle here.   

**Tasks:**

1. Create durable job before setting processing state:

   * job type: transcription / vision / multimodal / retry
   * status: queued / processing / succeeded / failed / cancelled
   * requestedBy
   * requestedAt
   * correlationId
   * input parameters

2. Return the real persisted `jobId`, not timestamp-generated controller IDs.

3. Move job ID generation into service/repository, not controller.

4. Enforce retention policy before:

   * processing
   * retry
   * transcript retrieval
   * frame-index retrieval
   * multimodal index retrieval

5. On failure:

   * update media artifact `lastError`
   * update processing state to FAILED
   * record operation
   * emit failure event

6. Add processor SPI call:

   * `MediaProcessorGateway.requestTranscription`
   * `requestVisionAnalysis`
   * `requestMultimodalIndexing`

7. If processor gateway is unavailable in production profile, fail closed with truthful 503.

8. Keep event emission mandatory in production profiles. Existing constructor validation should remain. 

**Acceptance criteria:**

* Media workflow has durable jobs, real job IDs, status transitions, and result references.
* Processing cannot happen without consent and retention validity.

---

### `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`

**Tasks:**

1. Stop generating job IDs in controller:

   * `retry-${artifactId}-${System.currentTimeMillis()}`
   * `transcription-${artifactId}-${System.currentTimeMillis()}`
   * `vision-${artifactId}-${System.currentTimeMillis()}`
   * `multimodal-${artifactId}-${System.currentTimeMillis()}`

2. Return service-created job response.

3. Replace `Map<String, String>` payload parsing with typed request records:

   * `TranscriptionRequest`
   * `VisionAnalysisRequest`
   * `MultimodalIndexingRequest`
   * `ConsentUpdateRequest`

4. Validate enums:

   * language code
   * analysis type
   * index type
   * consent status

5. Do not add authorization inside controller; rely on centralized route policy.

**Acceptance criteria:**

* Controller is thin and deterministic.
* Job IDs are durable and traceable.

---

### `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactRepository.java`

**Tasks:**

1. Add repository methods if missing:

   * `createJob`
   * `updateJobStatus`
   * `findJob`
   * `findJobsByArtifact`
   * `saveTranscript`
   * `saveFrameIndex`
   * `saveMultimodalIndex`
   * `linkJobResult`
   * `markProcessingFailed`

2. Ensure every method is tenant-scoped.

3. Ensure artifact ID and tenant ID are always both required.

4. Add optimistic concurrency/versioning for job state transitions.

**Acceptance criteria:**

* Job lifecycle is durable and tenant-isolated.

---

### `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/JpaMediaArtifactRepository.java`

**Tasks:**

1. Fix naming/import conflict if present: class and imported JPA repository both named `JpaMediaArtifactRepository` in the commit diff. The diff showed an invalid Java alias-style import, which must not exist in real code. 

2. Rename one side clearly:

   * `JpaMediaArtifactRecordRepository`
   * or `SpringMediaArtifactJpaRepository`

3. Implement durable job methods from `MediaArtifactRepository`.

4. Do not store lifecycle state only inside generic metadata if a typed entity field exists or should exist.

5. Add tenant-scoped query methods for jobs, transcripts, frame indexes, and multimodal indexes.

**Acceptance criteria:**

* Repository compiles cleanly.
* Media state is queryable and not hidden in arbitrary metadata.

---

### `products/data-cloud/delivery/api/src/test/java/com/ghatana/datacloud/memory/media/MediaArtifactServiceTest.java`

**Tasks:**

1. Add create artifact tests:

   * audio requires consent
   * video requires consent
   * non-media can be consent-not-required

2. Add process tests:

   * consent denied blocks
   * consent pending blocks
   * retention invalid blocks
   * granted consent creates durable job
   * missing processor in production returns failure

3. Add retry tests:

   * only failed jobs can retry
   * retry clears last error
   * retry creates new durable job

4. Add event/operation tests:

   * create emits created event
   * process emits processing requested
   * failure emits failed
   * blocked operation records BLOCKED

**Acceptance criteria:**

* Media lifecycle is verified at service level.

---

## Workstream 5 — AEP / Action Plane semantic lifecycle

### `products/data-cloud/planes/action/operator-contracts`

**Problem:** Action Plane inventory says `operator-contracts` semantic readiness is partial because PatternSpec compiler is incomplete. 

**Tasks:**

1. Add canonical `PatternSpec` model if missing.

2. Add canonical validation result model.

3. Add compiler output model:

   * normalized pattern graph
   * event inputs
   * temporal constraints
   * spatial constraints if supported
   * actions
   * side-effect policy
   * replay policy
   * governance policy references

4. Add no duplicate `PatternSpec` models in other modules.

5. Remove or mark legacy AEP pattern models as adapters only.

**Acceptance criteria:**

* One canonical PatternSpec contract exists.
* Compiler target is explicit and typed.

---

### `products/data-cloud/planes/action/engine`

**Problem:** Engine is active but semantic readiness is partial, especially learning-to-recommendation. 

**Tasks:**

1. Implement PatternSpec compiler.

2. Implement validation phases:

   * syntax
   * semantic
   * event source availability
   * policy compatibility
   * replay safety
   * side-effect safety

3. Implement pattern lifecycle:

   * draft
   * validated
   * active
   * paused
   * learning
   * recommended
   * retired

4. Implement learning-to-recommendation output:

   * recommendation ID
   * source pattern
   * evidence summary
   * confidence
   * risk
   * required approval
   * rollback plan

5. Ensure recommendations do not auto-activate.

**Acceptance criteria:**

* Pattern lifecycle is executable.
* Learning produces reviewable recommendations, not hidden behavior.

---

### `products/data-cloud/planes/action/event-bridge`

**Problem:** Event bridge is degraded because EventCloud SPI is incomplete. 

**Tasks:**

1. Define EventCloud SPI between Data/Event Plane and Action Plane.

2. Support:

   * append
   * consume
   * replay
   * checkpoint
   * correlate
   * trace propagation
   * tenant scope
   * schema version

3. Add replay-safe event consumption mode.

4. Ensure Action Plane never imports Data Plane internals directly.

**Acceptance criteria:**

* Pattern engine consumes events through SPI.
* Replay and checkpointing are deterministic.

---

### `products/data-cloud/planes/action/orchestrator`

**Problem:** Replay-safe lifecycle is partial. 

**Tasks:**

1. Add execution lifecycle:

   * planned
   * policy-evaluated
   * waiting-for-approval
   * running
   * succeeded
   * failed
   * cancelled
   * compensating
   * replayed

2. Add idempotency key per execution.

3. Add side-effect ledger.

4. Add compensation hooks.

5. Add replay mode that suppresses side effects unless explicitly approved.

**Acceptance criteria:**

* Replaying an execution cannot trigger external side effects by default.

---

### `products/data-cloud/planes/action/agent-runtime`

**Problem:** Agent runtime is active but replay-safe execution is partial. 

**Tasks:**

1. Add `AgentExecutionRecord` as actual implemented code if only documented.

2. Add `AgentReplayPlanner` as actual implemented code if only documented.

3. Persist:

   * prompt snapshot
   * model snapshot
   * tool calls
   * retrieval context
   * policy decisions
   * output
   * redactions
   * approvals
   * side-effect decisions

4. Fail closed when redaction pattern loading fails, preserving documented behavior. 

5. Add approval requirement for risky tools.

**Acceptance criteria:**

* Agent run replay is deterministic and auditable.

---

### `products/data-cloud/planes/action/observability`

**Problem:** Action observability is degraded/partial. 

**Tasks:**

1. Emit structured events for:

   * pattern validation
   * pattern activation
   * pattern recommendation
   * agent execution
   * approval required
   * approval granted/denied
   * side-effect suppressed
   * replay execution

2. Add correlation IDs through all Action Plane flows.

3. Feed operations job center with Action Plane executions.

**Acceptance criteria:**

* Operators can debug pattern/agent/pipeline behavior without reading logs only.

---

## Workstream 6 — Agent memory security

### `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/security/MemorySecurityManager.java`

**Problem:** `canSearch` returns boolean only; it cannot enforce scoped semantic/vector search policy. 

**Tasks:**

1. Replace or extend `canSearch` with:

```java
MemorySearchPolicy authorizeSearch(SearchRequest request, String tenantId, String agentId)
```

2. Policy must include:

   * tenant filter
   * allowed agent scopes
   * allowed memory tiers
   * allowed classifications
   * redaction requirements
   * retention constraints
   * max result limit

3. Keep old `canSearch` only as deprecated adapter if required, but do not use it in runtime.

**Acceptance criteria:**

* Search authorization returns enforceable scope, not just yes/no.

---

### `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/security/TenantIsolatingMemorySecurityManager.java`

**Problem:** `canSearch` always returns true and delegates tenant filtering to query layer. 

**Tasks:**

1. Implement scoped search policy.
2. Deny search if tenant ID is blank/invalid.
3. Deny search if agent is not authorized for shared memory.
4. Include tenant filter in policy.
5. Include classification restrictions.
6. Log denied search attempts without leaking raw tenant or memory details.

**Acceptance criteria:**

* Semantic search cannot accidentally cross tenants if downstream retriever misses a filter.

---

### `products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/memory/security/TenantIsolatingMemorySecurityManagerTest.java`

**Tasks:**

1. Add test: read allows same tenant.
2. Add test: read denies other tenant.
3. Add test: write denies other tenant.
4. Add test: search returns tenant-scoped policy.
5. Add test: blank tenant denied.
6. Add test: unauthorized shared memory denied.
7. Add test: PII/PHI memory excluded unless permission/classification allows.

**Acceptance criteria:**

* Agent memory authorization is tested at the source.

---

## Workstream 7 — Shared-library and dependency boundary cleanup

### `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`

**Problem:** Docs already identify boundary drift in shared platform modules. 

**Tasks:**

1. Convert “initial candidates” into a concrete migration matrix:

   * keep
   * split
   * move to Data Cloud
   * move to Action Plane
   * leave as SPI only

2. Do not create new shared abstractions unless three unrelated products need them.

3. Add explicit rule: product behavior must not live in platform modules.

**Acceptance criteria:**

* Engineers know where each low-score boundary fix belongs.

---

### `platform/java/agent-core`

**Tasks:**

1. Keep only generic agent contracts.
2. Move Action Plane runtime behavior out if any remains.
3. Do not include EventOperatorCapability runtime behavior here.
4. Add boundary test preventing Data Cloud Action runtime from leaking into platform core.

---

### `platform/java/workflow`

**Tasks:**

1. Keep generic workflow primitives only.
2. Move Data Cloud pipeline semantics into `products/data-cloud/planes/action`.
3. Move persistence metadata into Data Cloud if product-specific.

---

### `platform/java/messaging`

**Tasks:**

1. Keep generic messaging primitives only.
2. Move storage-plane event routing into `products/data-cloud/planes/event`.
3. Move Action Plane event semantics into `products/data-cloud/planes/action`.

---

### `platform/java/ai-integration`

**Tasks:**

1. Keep provider abstractions only.
2. Move query assist, schema inference, recommendations, action suggestions into Data Cloud Intelligence/Action planes.

---

### `platform/java/data-governance`

**Tasks:**

1. Keep generic policy primitives only.
2. Move retention, redaction, provenance, and evidence implementations into Data Cloud Governance Plane if product-specific.

---

### `platform/contracts`

**Tasks:**

1. Remove Data Cloud and Action Plane OpenAPI/schema ownership from platform contracts.
2. Keep Data Cloud contracts under `products/data-cloud/contracts`.
3. Keep `platform/contracts/src/test/resources/data-cloud-openapi.yaml` only as test fixture if still needed; otherwise delete or regenerate from canonical contract.

---

## Workstream 8 — UI low-cognitive-load cleanup

### `products/data-cloud/delivery/ui/src/pages/IntelligentHub.tsx`

**Tasks:**

1. Show only user-ready or degraded core surfaces by default.
2. Hide target-only/internal/operator-preview surfaces unless user is in operator/admin mode.
3. Replace plane/internal language with outcome language:

   * “Data”
   * “Events”
   * “Pipelines”
   * “Trust”
   * “Operations”
4. Remove release/evidence wording from normal product dashboard.
5. Add clear degraded-state CTA from backend `recommendedAction`.

---

### `products/data-cloud/delivery/ui/src/pages/DataPage.tsx`

**Tasks:**

1. Make Data page the single entry for:

   * collections
   * entities
   * datasets
   * quality
   * lineage
   * connectors, as contextual action if available
2. Do not send users to `/entities`, `/fabric`, `/context` as primary journeys.
3. Use common table/list component.
4. Ensure empty/loading/error/unauthorized states are consistent.

---

### `products/data-cloud/delivery/ui/src/pages/MediaArtifactPage.tsx`

**Tasks:**

1. Render lifecycle state from backend:

   * registered
   * consent pending
   * queued
   * processing
   * completed
   * failed
   * deleted

2. Hide process actions when permission is missing.

3. Show consent action only when permission allows.

4. Show transcript/frame-index actions only when result exists.

5. Show job timeline from operations job API.

6. Do not show fake job IDs.

---

### `products/data-cloud/delivery/ui/src/features/data-fabric/components/DataConnectorsPage.tsx`

**Tasks:**

1. Use canonical `/api/v1/connectors` only.
2. Remove dependency on `/data-fabric/connectors` compatibility route.
3. Show runtime-unavailable state when backend returns 503 truth.
4. Disable sync/test/schema buttons when connector runtime unavailable.
5. Show credential-rotation state without exposing secrets.
6. Add dataset-link workflow only after connector has schema.

---

### `products/data-cloud/delivery/ui/src/pages/AgentPluginManagerPage.tsx`

**Tasks:**

1. Separate “Agent Catalog” from “Plugin Manager” if both are shown.
2. Use Action Plane runtime truth for agent availability.
3. Hide execution actions unless `action:agent:execute` is available.
4. Show replay/approval status for agent actions once backend supports it.
5. Do not expose raw AEP terminology to normal users.

---

### `products/data-cloud/delivery/ui/src/pages/EventExplorerPage.tsx`

**Tasks:**

1. Rename user-facing copy from “AEP event stream” to “Event stream” unless operator diagnostics mode.
2. Add replay availability state from backend.
3. Add disabled state if EventCloud SPI is incomplete.
4. Do not imply pattern lifecycle is complete until Action Plane reports it.

---

### `products/data-cloud/delivery/ui/src/i18n/*`

**Tasks:**

1. Add missing keys for every route/surface label, disabled reason, recommended action, and page title.
2. Remove raw text from:

   * route descriptions
   * disabled surface fallbacks
   * preview/degraded banners
   * media states
   * connector runtime states
3. Add test that fails on missing route/surface i18n keys.

---

# Minimal verification plan for this pass only

Run targeted implementation tests only. Do **not** run release-readiness or evidence workflows.

Suggested targeted checks after implementation:

```bash
# UI route/runtime truth only
cd products/data-cloud/delivery/ui
pnpm type-check
pnpm test -- src/__tests__/routes src/__tests__/api src/__tests__/pages

# Backend route/security/media/connector only
./gradlew \
  :products:data-cloud:delivery:launcher:test \
  :products:data-cloud:delivery:api:test \
  :products:data-cloud:planes:action:agent-runtime:test \
  :products:data-cloud:planes:action:engine:test \
  :products:data-cloud:planes:action:event-bridge:test
```

Do **not** run:

```bash
pnpm test:readiness
products/data-cloud/scripts/verify-production-readiness.sh
/api/v1/release-readiness
release evidence generation
product promotion workflows
.kernel/evidence generation or freshness workflows
```

The UI package currently defines `test:readiness`, but this pass must not use it. 

---

# Leapfrog acceptance bar

The next iteration should not be considered successful unless all of these are true:

1. No duplicate route paths remain.
2. Runtime app uses `/api/v1/surfaces` as the only surface truth.
3. Preview gating works correctly and target-only never renders real pages.
4. Backend permission derivation includes every permission required by active route handlers.
5. Media endpoints have centralized backend authorization.
6. Connector ADMIN journey works end-to-end with one real connector provider.
7. Media artifact journey works end-to-end with durable jobs and real job IDs.
8. Agent memory search returns an enforceable scoped policy.
9. AEP has a real PatternSpec compiler and replay-safe execution path started.
10. No release-readiness/evidence execution is added or run.
