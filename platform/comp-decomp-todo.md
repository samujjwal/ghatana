Below is the **file-by-file implementation task list** from the audit at commit `0aa8aa9161103146ad52e1029aa9b0805e2f95be`.

This is **not** a release-readiness plan. Do **not** run release-readiness checks, release evidence generation, release freshness checks, product promotion workflows, or evidence bundle scripts. The repo itself already marks this pass as implementation hardening and says not to run release-readiness/evidence workflows. 

---

# Pass 1 — Canonical product truth, module truth, and stale-status cleanup

Root cause: Data Cloud has competing truth sources. The canonical registry marks Data Cloud as `blocked`, runtime surface status as `degraded`, and release-readiness execution as `not-executed`, while other docs/scripts still use release-blocking or production-ready language. 

## `config/canonical-product-registry.json`

**What to change:**

Update the `data-cloud` entry to become the canonical source for:

* plane status: `active`, `preview`, `degraded`, `target-only`, `blocked`
* surface status: `user-ready`, `operator-preview`, `internal-preview`, `disabled`, `unavailable`, `degraded`
* module status
* ownership classification
* product completeness blockers
* no-release-readiness-execution flag

**Where:**

Inside the `"data-cloud"` object, especially:

* `productCompletenessStatus`
* `runtimeSurfaceStatus`
* `releaseReadinessExecutionStatus`
* `lifecycleReadiness.reasonCodes`
* `lifecycleReadiness.nextRequiredWork`
* `lifecycleReadiness.blockerGateAdapterMatrix`

The current registry already contains the right blockers: runtime-truth UI drift, connector runtime missing, audio-video incomplete, AEP lifecycle incomplete, Context Plane target-only, and optional execution capability. Keep and make these the generated source of truth. 

**Exact task:**

Add normalized structures like:

```json
"planes": {
  "data": { "status": "active" },
  "event": { "status": "active" },
  "context": { "status": "target-only" },
  "intelligence": { "status": "active" },
  "governance": { "status": "active" },
  "action": {
    "status": "partial",
    "semanticReadiness": "partial",
    "blockers": [
      "patternspec-compiler-incomplete",
      "eventcloud-spi-incomplete",
      "learning-to-recommendation-incomplete",
      "replay-safe-agent-execution-incomplete"
    ]
  },
  "operations": { "status": "active" }
}
```

Do **not** add evidence-generation tasks.

---

## `config/generated/settings-gradle-includes.kts`

**What to change:**

Do not edit manually. It is generated from `canonical-product-registry.json`. The file itself says it is auto-generated and should not be manually edited. 

**Exact task:**

After registry changes, regenerate this file only through the generator.

**Where:**

Data Cloud module section currently includes active Data Cloud modules and Action Plane modules. 

**Do not:**

Do not manually add/remove Gradle includes here.

---

## `scripts/generate-product-registry-artifacts.mjs`

**What to change:**

Update the generator so it emits:

* Gradle includes
* product/module status summaries
* plane/surface readiness summaries
* Data Cloud hardening status
* no-release-readiness-execution marker

**Where:**

Generator logic that reads `config/canonical-product-registry.json` and writes generated settings/artifacts.

**Exact task:**

Make generated outputs distinguish:

* implementation-hardening tasks
* release-readiness execution tasks
* evidence-generation tasks

For this pass, generated summaries must explicitly say:

```text
releaseReadinessExecutionStatus = not-executed
doNotRunReleaseReadinessChecks = true
doNotGenerateReleaseEvidence = true
```

---

## `scripts/list-data-cloud-active-modules.mjs`

**What to change:**

This script currently classifies modules as `release-blocking`, and comments say the script is used by release policy consumers while also saying not to execute release workflows during implementation hardening. 

**Where:**

* `releaseBlockingModules`
* `actionPlaneModules`
* `classifyDataCloudModule`
* reason strings returned by classification

**Exact task:**

Rename or split classification concepts:

* `implementationCriticalModules`
* `activeHardeningModules`
* `releaseBlockingModules` only for future release workflows

For this iteration, return reasons such as:

```js
{
  category: "implementation-critical",
  reason: "Active Data Cloud module; include in implementation hardening and targeted tests. Do not run release-readiness/evidence workflows."
}
```

**Do not:**

Do not trigger release readiness from this script.

---

## `products/data-cloud/README.md`

**What to change:**

Preserve the current statement that this iteration is not release-readiness execution. 

**Where:**

* `Current Implementation Truth`
* `Module Classification and Promotion`
* `Shared Platform Review`

**Exact task:**

Update README to say:

* Data Cloud product is coherent but blocked for production completeness.
* Action Plane is active but AEP semantic lifecycle is partial.
* Context Plane is target-only.
* Audio-video is metadata/control-plane integration until first-class lifecycle is implemented.
* Release-readiness/evidence execution is explicitly out of scope for this implementation pass.

---

## `products/data-cloud/DEVELOPER_MANUAL.md`

**What to change:**

The manual correctly says Data Cloud owns storage/query/events/governance/context APIs/agent memory/SDK/UI and does not own AEP semantic internals. 

**Where:**

* `What Data Cloud Owns`
* `Work on plugins`
* `Work on UI features`
* local workflow instructions

**Exact task:**

Add a new section:

```md
## Implementation Hardening Scope

Do not run release-readiness checks or evidence-generation scripts in this pass.
Use targeted unit/integration/component/API tests only.
```

Also add a boundary note:

```md
AEP semantic lifecycle completion must happen through Action Plane contracts, not by moving AEP semantics into Data/Event/Governance planes.
```

---

## `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`

**What to change:**

This file is the canonical plane architecture and already marks Context as target-only, Action Plane modules as preview/degraded/active, and UI/API as degraded. 

**Where:**

* `Current-To-Target Module Map`
* `Dependency Rules`
* `Shared Platform Review Rules`

**Exact task:**

Update the table to align with `canonical-product-registry.json` and remove any language that implies production readiness before executable lifecycle is complete.

Add explicit statuses:

* `context`: target-only, not user-visible
* `action/engine`: active implementation, semantic readiness partial
* `action/orchestrator`: active implementation, replay-safe lifecycle partial
* `action/agent-runtime`: active implementation, replay-safe execution partial
* `delivery/ui`: degraded until runtime-truth UI drift is fixed
* `media`: partial external modality integration

---

## `products/data-cloud/docs/architecture/ACTION_PLANE_MODULE_INVENTORY.md`

**What to change:**

Keep this as the canonical Action Plane inventory, but generate it from the registry.

**Where:**

This file already states the correct root truth: placement is complete, but semantic/product readiness is partial and still requires PatternSpec compiler, EventCloud SPI, pattern lifecycle, learning-to-recommendation, and replay-safe agent execution. 

**Exact task:**

Add per-module status columns:

* placement status
* semantic readiness
* runtime readiness
* API readiness
* UI readiness
* test coverage status

Do not call modules production-ready unless lifecycle proof exists.

---

## `products/data-cloud/planes/action/MODULE_INVENTORY.md`

**What to change:**

This file conflicts with the canonical architecture because it claims all release-blocking modules are production-ready.  It also says it is current as of an older commit, not the audited commit. 

**Exact task:**

Either delete this file or replace it with a pointer:

```md
# Moved

The canonical Action Plane module inventory is:
products/data-cloud/docs/architecture/ACTION_PLANE_MODULE_INVENTORY.md

This file must not contain independent readiness claims.
```

**Preferred:** delete the stale file after updating references.

---

# Pass 2 — Security and authorization trust-boundary fix

Root cause: backend permissions can be accepted from a request header. `RequestContextResolver` reads `X-Permissions`, validates only string format, and merges those permissions with role-derived permissions.  

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/security/RequestContextResolver.java`

**What to change:**

Remove user-controlled permission injection.

**Where:**

* `extractPermissions`
* permission merge in `resolve`
* `ADMIN_PERMISSIONS`
* `OPERATOR_PERMISSIONS`
* `VIEWER_PERMISSIONS`
* `hasPermission`
* `hasAnyPermission`
* `hasAllPermissions`

**Exact task:**

1. Delete or disable `extractPermissions(HttpRequest request)`.
2. Remove `X-Permissions` as a trusted authorization source.
3. Derive permissions only from:

   * authenticated principal roles/claims,
   * API-key identity,
   * server-side policy engine,
   * signed support delegation token.
4. Add canonical permissions required by existing handlers:

   * `connector:read`
   * `connector:register`
   * `connector:update`
   * `connector:delete`
   * `connector:sync`
   * `connector:rotate-credentials`
   * `action:pipeline:read`
   * `action:pipeline:create`
   * `action:pipeline:update`
   * `action:pipeline:delete`
   * `action:pipeline:execute`
   * `action:pipeline:cancel`
   * `media:artifact:read`
   * `media:artifact:create`
   * `media:artifact:process`
   * `media:artifact:delete`
5. Return `403` if a required permission is absent.

---

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java`

**What to change:**

Keep the strong fail-closed model, but align route metadata permissions with the new canonical permission registry.

**Where:**

* `serveAsPrincipal`
* `requiredAccess`
* `hasRequiredAccess`
* policy evaluation
* audit emission

The filter already fails closed for missing route metadata in production-like profiles.  It also evaluates policy before serving critical routes. 

**Exact task:**

1. Ensure `AccessLevel` is only coarse-grained.
2. Add fine-grained permission checks through canonical permission mapping.
3. Ensure policy engine sees:

   * tenant ID,
   * principal,
   * roles,
   * canonical permissions,
   * route ID,
   * operation ID,
   * sensitivity.
4. Add audit details for denied permission:

   * `requiredPermission`
   * `routeId`
   * `principal`
   * `tenantId`
   * `requestId`

---

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java`

**What to change:**

This is the authoritative route security metadata registry. 

**Where:**

All `route(...)` entries.

**Exact task:**

1. Add canonical permission IDs to route metadata.
2. Stop relying only on `AccessLevel`.
3. Ensure Action Plane routes map to Action Plane permissions.
4. Ensure media routes map to media permissions.
5. Ensure connector routes map to connector permissions.
6. Ensure route metadata is generated or checked against router registrations.

---

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java`

**What to change:**

Centralize permission checking.

**Where:**

Methods such as `requirePermission`, request context resolution, principal resolution.

**Exact task:**

1. Make `requirePermission` use the new canonical permission registry.
2. Ensure it never trusts request headers for permissions.
3. Return structured errors:

   * `PERMISSION_DENIED`
   * `AUTHENTICATION_REQUIRED`
   * `TENANT_REQUIRED`
   * `SUPPORT_DELEGATION_REQUIRED`
4. Add request ID and route ID to every error body.

---

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`

**What to change:**

Pipeline/execution handlers use fine-grained permissions such as `action:pipeline:read`. 

**Where:**

Every handler method:

* `handleGetExecution`
* `handleExecutePipeline`
* `handleCancelPipelineExecution`
* `handleRetryExecution`
* `handleRollbackExecution`
* `handleCheckpointExecution`
* `handleRestoreExecution`
* pipeline execution log/list methods

**Exact task:**

Ensure each action has the canonical permission:

* read/list/logs → `action:pipeline:read`
* execute/retry → `action:pipeline:execute`
* cancel → `action:pipeline:cancel`
* rollback/restore → `action:pipeline:admin`
* checkpoint → `action:pipeline:checkpoint`

Also ensure operation audit records include the permission used.

---

## `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`

**What to change:**

Media authorization currently checks simple roles like `viewer`, `editor`, `admin`, `processor`. 

**Where:**

* `handle`
* create/list/get/delete/transcribe/analyze authorization branches

**Exact task:**

Replace local role checks with canonical permissions:

* create → `media:artifact:create`
* list/get → `media:artifact:read`
* transcribe/analyze → `media:artifact:process`
* delete → `media:artifact:delete`

Keep tenant resolution from authenticated principal only.

---

## Test files to add/update for Pass 2

### `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/security/RequestContextResolverTest.java`

Add tests:

* `X-Permissions` does not grant permissions.
* roles expand only through server-side mapping.
* unsupported permission returns false.
* production rejects spoofed tenant header/query.
* support delegation requires signed/server-side context.

### `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilterTest.java`

Add tests:

* missing route metadata fails closed in production.
* denied permission emits audit.
* critical route without policy engine fails closed.
* audit-only mode does not apply to production hardening tests unless explicitly configured.

### `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandlerSecurityTest.java`

Add tests:

* viewer can read execution.
* operator can execute/retry.
* operator cannot rollback/restore without admin permission.
* injected `X-Permissions` cannot bypass role policy.

### `products/data-cloud/delivery/api/src/test/java/com/ghatana/datacloud/api/controller/MediaArtifactControllerSecurityTest.java`

Add tests:

* viewer cannot create/process/delete.
* processor can process but not delete.
* admin can delete.
* injected permission header does not bypass authorization.

---

# Pass 3 — Event, replay, checkpoint, and envelope correctness

Root cause: event replay/checkpoint APIs are not production-grade. `checkpoint` currently defaults to a no-op returning `true`, and `replayEvents` uses `toOffset` only to compute a limit. 

## `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/DataCloudClient.java`

**What to change:**

Make replay/checkpoint semantics explicit and non-silent.

**Where:**

* `checkpoint`
* `replayEvents`
* `EventQuery`
* `TailRequest`
* `Offset`

**Exact task:**

1. Replace default `checkpoint` no-op with one of:

   * abstract required method, or
   * default `Promise.ofException(new UnsupportedOperationException(...))`.
2. Add `ReplayRequest` record:

   * `fromOffset`
   * `toOffset`
   * `eventTypes`
   * `replayMode`
   * `idempotencyKey`
   * `consumerGroup`
3. Replace `replayEvents(String tenantId, long fromOffset, long toOffset)` implementation with real bounded filtering.
4. Define inclusive/exclusive offset semantics in JavaDoc.
5. Add checkpoint read API:

   * `readCheckpoint(tenantId, stream, consumerGroup)`
6. Add checkpoint commit API with idempotency:

   * `commitCheckpoint(tenantId, stream, consumerGroup, offset, idempotencyKey)`

---

## `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/DataCloud.java`

**What to change:**

`appendEvent` validates event envelopes only outside `LOCAL`. 

**Where:**

* `DefaultDataCloudClient.appendEvent`
* `queryEvents`
* `tailEvents`
* `InMemoryEventLogStore`
* `InMemoryEntityStore`

**Exact task:**

1. Validate event envelope in all profiles by default.
2. Add explicit config flag:

   * `allowInvalidLocalEventsForTests`
3. Do not silently bypass validation in local mode.
4. Implement bounded replay using actual offset range.
5. Make `tailEvents.cancel()` unregister the listener from `InMemoryEventLogStore`, not just ignore callbacks.
6. Add checkpoint persistence in in-memory profile for tests.
7. Ensure query sorting by timestamp/offset is stable.

---

## `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/spi/EventLogStore.java`

**What to change:**

Add durable checkpoint and replay primitives.

**Exact task:**

Add SPI methods:

```java
Promise<Checkpoint> readCheckpoint(TenantContext tenant, String stream, String consumerGroup);
Promise<Checkpoint> commitCheckpoint(TenantContext tenant, String stream, String consumerGroup, Offset offset, String idempotencyKey);
Promise<List<EventEntry>> replay(TenantContext tenant, ReplaySpec spec);
Promise<Void> unsubscribe(TenantContext tenant, SubscriptionId subscriptionId);
```

---

## `products/data-cloud/planes/event/store/**`

**What to change:**

Durable store providers must implement the checkpoint/replay contract.

**Exact task:**

For each event store implementation:

* add checkpoint table/collection if durable,
* implement bounded replay,
* enforce tenant isolation,
* enforce idempotent checkpoint commits,
* add tests for replay and checkpoint recovery.

---

## Tests for Pass 3

### `products/data-cloud/planes/shared-spi/src/test/java/com/ghatana/datacloud/DataCloudClientReplayTest.java`

Add tests for:

* replay respects `fromOffset`.
* replay respects `toOffset`.
* replay filters event types.
* replay requires valid tenant.
* checkpoint commit/read works.
* no-op checkpoint is impossible.

### `products/data-cloud/delivery/runtime-composition/src/test/java/com/ghatana/datacloud/DataCloudEventEnvelopeValidationTest.java`

Add tests for:

* local profile validates required source by default.
* explicit test override allows invalid event only in test config.
* source/correlation/trace headers round-trip.

---

# Pass 4 — AEP semantic lifecycle completion

Root cause: AEP placement is complete, but semantic lifecycle is explicitly partial. The Action Plane inventory says completion still requires unified operator model, PatternSpec compiler, EventCloud SPI, pattern lifecycle, learning-to-recommendation loop, and replay-safe agent execution. 

## `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/agent/capability/EventOperatorCapability.java`

**What to change:**

This is currently a thin interface. 

**Exact task:**

Add or connect canonical operator lifecycle contracts:

* validate operator spec
* compile operator spec
* explain operator plan
* declare side effects
* declare replay behavior
* declare required policies
* declare observability requirements

---

## `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/operator/contract/**`

**What to change:**

Make PatternSpec/operator contracts first-class.

**Exact task:**

Add or harden:

* `PatternSpec`
* `PatternSpecCompiler`
* `PatternValidationResult`
* `PatternRuntimePlan`
* `PatternLifecycleState`
* `PatternMatchResult`
* `PatternExplainability`
* `LearningFeedback`
* `RecommendationCandidate`

---

## `products/data-cloud/planes/action/engine/src/main/java/**`

**What to change:**

Implement the event-pattern execution lifecycle.

**Exact task:**

Add engine services:

* compile PatternSpec
* register active patterns
* consume Data Cloud Event Plane records
* detect matches
* create pattern instances
* emit explainability evidence
* support replay mode
* support tenant isolation
* support confidence and uncertainty metadata

---

## `products/data-cloud/planes/action/registry/src/main/java/**`

**What to change:**

Make pattern/agent registry versioned and lifecycle-aware.

**Exact task:**

Persist:

* pattern definition
* version
* lifecycle status
* owner
* tenant
* validation result
* activation timestamp
* retirement timestamp
* rollback version
* learning feedback references

---

## `products/data-cloud/planes/action/orchestrator/src/main/java/**`

**What to change:**

Connect pattern decisions to governed action execution.

**Exact task:**

Add orchestration path:

```text
event → pattern match → policy check → review if required → agent/pipeline execution → operation record → learning feedback
```

Add replay-safe behavior:

* idempotency key
* replay mode
* compensation strategy
* no side effects during dry-run replay

---

## `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/registry/AgentEventOperatorCapabilityAdapter.java`

**What to change:**

The adapter has useful policies and metrics, but validation only checks operator kind and compile emits a minimal runtime plan. 

**Where:**

* `validate`
* `compile`
* `process`
* `mapAgentResult`
* `buildEvidence`
* `requireSideEffectControls`
* `requireDurableMemoryStore`

**Exact task:**

1. Validate:

   * input schema,
   * output schema,
   * side-effect policy,
   * replay policy,
   * human review policy,
   * observability policy,
   * tenant context,
   * trace/correlation requirements.
2. Compile a real runtime plan with:

   * operator ID,
   * input/output schemas,
   * side-effect profile,
   * replay mode,
   * policy requirements,
   * required tools,
   * memory requirements.
3. Make replay mode explicit in `AgentContext`.
4. Emit structured evidence for every outcome.
5. Add fail-closed behavior for missing trace/correlation/tenant.

The adapter already requires durable memory unless explicitly allowed as no-op. Keep that behavior. 

---

## `products/data-cloud/planes/action/event-bridge/src/main/java/**`

**What to change:**

Make EventCloud persistence bridge explicit and replay-safe.

**Exact task:**

* Consume only public Event Plane SPI.
* Do not import Data/Event/Governance implementation internals.
* Persist EventCloud records behind AEP-owned SPI.
* Include tenant, offset, correlation, causation, policy context, trace.
* Add replay and backfill mode.

---

## Tests for Pass 4

### `products/data-cloud/planes/action/operator-contracts/src/test/java/**`

Add tests:

* PatternSpec validation.
* PatternSpec compile failure messages.
* operator lifecycle states.
* schema compatibility.
* side-effect declarations.

### `products/data-cloud/planes/action/engine/src/test/java/**`

Add tests:

* event stream → pattern match.
* no match.
* replay match.
* late event handling.
* uncertainty/confidence propagation.

### `products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/registry/AgentEventOperatorCapabilityAdapterTest.java`

Update tests for:

* compile produces complete runtime plan.
* missing policy fails validation.
* side-effecting agent requires human-review/idempotency/audit.
* replay dry-run does not perform side effects.

### `products/data-cloud/integration-tests/src/test/java/**`

Add one vertical slice test:

```text
append Data Cloud event
→ AEP pattern detects match
→ review generated
→ approve review
→ agent action executes
→ operation record visible
→ replay does not duplicate side effect
```

---

# Pass 5 — Runtime truth and UI capability disclosure

Root cause: UI has runtime gating, but preview surfaces are still too easily reachable. `RuntimeCapabilityRouteGate` treats `LIVE`, `DEGRADED`, and `PREVIEW` as available, and routes can opt into `allowPreview`.  

## `products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts`

**What to change:**

This file is the UI route surface registry. It currently defines active/preview/boundary/discoverable metadata. 

**Where:**

* `RouteLifecycleSchema`
* `RouteSurfaceSchema`
* `canonicalRouteSurfaceRegistry`
* `getDiscoverableRouteSurfaces`

**Exact task:**

1. Add new lifecycle values:

   * `user-ready`
   * `operator-preview`
   * `internal-preview`
   * `target-only`
   * `disabled`
2. Mark:

   * `/context` as `target-only` until Context Plane is active.
   * `/memory` as `operator-preview`.
   * `/agents` as `operator-preview`.
   * `/media/artifacts` as `operator-preview` until first-class lifecycle exists.
   * `/fabric` as `operator-preview`.
   * `/plugins` as `operator-preview` unless plugin lifecycle is complete.
3. Keep default discoverable nav minimal:

   * `/`
   * `/data`
   * `/events`
   * `/query`
   * `/pipelines`
   * `/trust`
   * `/operations`
4. Do not show target-only routes in navigation/search.

---

## `products/data-cloud/delivery/ui/src/components/security/RuntimeCapabilityRouteGate.tsx`

**What to change:**

Do not treat preview as usable by default.

**Where:**

* `RuntimeCapabilityRouteGateProps`
* `allowPreview`
* `allowed`
* `RuntimePostureBanner`

**Exact task:**

1. Change default `allowPreview` to `false`.
2. Add `previewAudience` support:

   * `internal`
   * `operator`
   * `admin`
3. Require both:

   * backend surface status allows preview,
   * UI route registry allows that preview audience.
4. Replace hardcoded strings:

   * `"Preview"`
   * `"Degraded"`
   * `"Checking surface availability..."`
     with i18n keys.

---

## `products/data-cloud/delivery/ui/src/api/surfaces.service.ts`

**What to change:**

The canonical surface status taxonomy exists. 

**Where:**

* `SurfaceStatus`
* `SurfaceSignal`
* `normalizeSurfaceStatus`
* `SURFACE_ALIASES`
* `isSurfaceAvailable`

**Exact task:**

1. Add `audience` to `SurfaceSignal`.
2. Add `readinessClass`:

   * `user-ready`
   * `operator-preview`
   * `internal-preview`
   * `target-only`
3. Change `isSurfaceAvailable` so `PREVIEW` is not globally available.
4. Normalize backend `targetOnly`, `audience`, and `limitations`.
5. Add aliases for Pattern/AEP/Learning once Action Plane lifecycle is implemented.

---

## `products/data-cloud/delivery/ui/src/routes.tsx`

**What to change:**

Routes currently pass `allowPreview` for many surfaces, including media, events, memory, entities, context, fabric, agents, settings, plugins, and connectors.   

**Exact task:**

1. Remove blanket `allowPreview`.
2. Replace with explicit audience-aware preview:

   * `allowPreviewFor="operator"`
   * `allowPreviewFor="admin"`
3. Remove `/context` preview rendering until Context Plane is active.
4. Keep compatibility routes but ensure they do not appear in navigation/search.
5. Hide release-truth route completely in this pass.

---

## `products/data-cloud/delivery/ui/src/components/security/RoleProtectedRoute.tsx`

**What to change:**

This file correctly states shell role is only UI disclosure, not backend authorization. 

**Exact task:**

1. Keep that separation.
2. Add route lifecycle check from `RouteSurfaceRegistry`.
3. If route is `target-only`, render disabled surface even if role matches.
4. Do not allow shell role to imply backend capability.

---

## Tests for Pass 5

### `products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.test.ts`

Add tests:

* target-only routes are never discoverable.
* preview routes require explicit preview audience.
* default nav contains only the intended outcome-first routes.

### `products/data-cloud/delivery/ui/src/components/security/RuntimeCapabilityRouteGate.test.tsx`

Add tests:

* PREVIEW does not render by default.
* operator-preview renders only for operator/admin.
* target-only always renders disabled state.
* loading state does not flash optional surface.

### `products/data-cloud/delivery/ui/src/routes.test.tsx`

Add tests:

* `/context` blocked while Context Plane target-only.
* `/media/artifacts` preview gated.
* `/plugins` preview gated.
* compatibility aliases redirect without increasing nav items.

---

# Pass 6 — Audio-video first-class modality

Root cause: Data Cloud currently treats audio-video as partial metadata integration. The registry says audio-video modality is partial and must become durable MediaArtifact job lifecycle, Transcript, FrameIndex, consent, retention, and processing workflow. 

## `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`

**What to change:**

The controller currently registers media metadata and returns blocked/service-unavailable if runtime is not configured. 

**Where:**

* `createArtifact`
* `listArtifacts`
* `triggerTranscription`
* `triggerVisionAnalysis`
* `deleteArtifact`

**Exact task:**

1. Add durable lifecycle states:

   * `REGISTERED`
   * `CONSENT_PENDING`
   * `CONSENT_DENIED`
   * `QUEUED`
   * `PROCESSING`
   * `TRANSCRIBED`
   * `ANALYZED`
   * `INDEXED`
   * `FAILED`
   * `RETAINED`
   * `DELETED`
2. Enforce consent before processing.
3. Enforce retention policy before delete/process/export.
4. Create operation record for every lifecycle transition.
5. Return job IDs for transcribe/analyze.
6. Add endpoints:

   * `GET /api/v1/media/artifacts/:artifactId/jobs`
   * `GET /api/v1/media/artifacts/:artifactId/transcript`
   * `GET /api/v1/media/artifacts/:artifactId/frame-index`
   * `POST /api/v1/media/artifacts/:artifactId/retry`
7. Emit Data Cloud events for every transition.

---

## `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactRecord.java`

**What to change:**

Add first-class fields instead of storing lifecycle-critical data only in metadata.

**Exact task:**

Add fields:

* `status`
* `consentStatus`
* `retentionPolicy`
* `retentionUntil`
* `processingJobId`
* `transcriptId`
* `frameIndexId`
* `lastError`
* `createdBy`
* `updatedBy`
* `deletedAt`

---

## `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactRepository.java`

**What to change:**

Support lifecycle and job queries.

**Exact task:**

Add methods:

* `updateStatus`
* `findJobs`
* `saveTranscript`
* `findTranscript`
* `saveFrameIndex`
* `findFrameIndex`
* `markDeleted`
* `findByRetentionDue`
* `findByConsentStatus`

---

## `products/audio-video/modules/intelligence/multimodal-service/**`

**What to change:**

Connect multimodal processing to Data Cloud media lifecycle.

**Exact task:**

Expose provider interface or adapter consumed by Data Cloud:

* submit media processing job
* get job status
* fetch transcript
* fetch frame index
* fetch extracted events
* fetch embeddings/index metadata

---

## `products/audio-video/modules/speech/stt-service/**`

**What to change:**

Make STT integration job-based and traceable.

**Exact task:**

* Accept Data Cloud artifact ID.
* Return job ID.
* Emit job state transitions.
* Return transcript with confidence, language, timestamps, speaker labels where available.

---

## `products/audio-video/modules/vision/vision-service/**`

**What to change:**

Make frame/object/scene analysis job-based and traceable.

**Exact task:**

* Accept Data Cloud artifact ID.
* Return job ID.
* Emit frame index result.
* Include confidence, timestamp ranges, extracted labels/events.

---

## `products/data-cloud/delivery/ui/src/pages/MediaArtifactPage.tsx`

**What to change:**

This page currently contains raw i18n keys as visible strings and a modal form. 

**Exact task:**

1. Use `useTranslation`.
2. Replace raw text keys with `t(...)`.
3. Add status timeline.
4. Add job panel.
5. Add transcript panel.
6. Add frame index panel.
7. Disable process buttons unless consent is granted.
8. Show retention warnings.
9. Replace raw toast keys with translated messages. Current toast calls use raw keys. 

---

## `products/data-cloud/delivery/ui/src/features/media/services/api.ts`

**What to change:**

The API service already supports create/list/get/delete/transcribe/analyze. 

**Exact task:**

Add methods:

* `getJobs(artifactId)`
* `getTranscript(artifactId)`
* `getFrameIndex(artifactId)`
* `retryJob(artifactId, jobId)`
* `updateConsent(artifactId, consentStatus)`
* `applyRetentionPolicy(artifactId, policy)`

---

## Tests for Pass 6

### `products/data-cloud/delivery/api/src/test/java/com/ghatana/datacloud/api/controller/MediaArtifactControllerTest.java`

Add tests:

* consent required for audio/video.
* denied consent blocks processing.
* retention policy blocks delete/process as required.
* transcription creates job.
* vision analysis creates job.
* job failure returns structured error.

### `products/data-cloud/delivery/ui/src/pages/MediaArtifactPage.test.tsx`

Add tests:

* no raw i18n keys visible.
* processing disabled until consent granted.
* transcript/frame tabs render when available.
* failed job shows retry action.

---

# Pass 7 — Connector production path

Root cause: Data Cloud registry says connector runtime is incomplete and needs provider, runtime truth states, dataset linkage, health, schema, sync, and credential rotation. 

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandler.java`

**What to change:**

This handler stores connector metadata and delegates operations to `DataFabricConnector` if available; otherwise it returns degraded/truthful responses. 

**Where:**

* `handleListConnections`
* `handleRegisterConnection`
* test/sync/schema/health/rotate/delete methods
* `isConnectorRuntimeRequired`
* idempotency helpers

**Exact task:**

1. Replace tenant error message `"X-Tenant-Id header is required"` with authenticated tenant context language. Current message conflicts with production tenant rules. 
2. Ensure connector runtime is mandatory in staging/production/sovereign.
3. Add dataset linkage:

   * connector ID → dataset ID
   * sync job → dataset version
   * schema snapshot → contract version
4. Redact credentials in all responses.
5. Add credential rotation operation record.
6. Add health status model:

   * `UNKNOWN`
   * `HEALTHY`
   * `DEGRADED`
   * `FAILED`
   * `CREDENTIALS_EXPIRED`
7. Add sync status model:

   * `IDLE`
   * `QUEUED`
   * `RUNNING`
   * `FAILED`
   * `COMPLETED`
8. Require idempotency for mutating operations.

---

## `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/fabric/DataFabricConnector.java`

**What to change:**

Make connector runtime contract production-ready.

**Exact task:**

Add methods:

* `testConnection`
* `inferSchema`
* `sync`
* `getSyncStatus`
* `getHealth`
* `rotateCredentials`
* `linkDataset`
* `redactConfig`

---

## `products/data-cloud/contracts/openapi/data-cloud.yaml`

**What to change:**

Add or align connector schemas and endpoints.

**Exact task:**

Add schemas:

* `Connector`
* `ConnectorCreateRequest`
* `ConnectorHealth`
* `ConnectorSchemaSnapshot`
* `ConnectorSyncRequest`
* `ConnectorSyncStatus`
* `CredentialRotationRequest`
* `DatasetLink`

Do not add release evidence endpoints or release-readiness schemas.

---

## Tests for Pass 7

### `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandlerTest.java`

Add tests:

* no credentials returned.
* runtime unavailable returns 503 in production-like profiles.
* local profile can save pending connector truthfully.
* schema inference stores snapshot.
* sync links dataset.
* credential rotation records operation.

### `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/ConnectorLifecycleTest.java`

Update to cover full lifecycle:

```text
register → test → infer schema → link dataset → sync → health → rotate credentials → disable/delete
```

---

# Pass 8 — Plugin/extensibility hardening

## `products/data-cloud/extensions/plugins/**`

**What to change:**

Make plugins lifecycle-driven, not just route-driven.

**Exact task:**

Add canonical plugin contract:

* `PluginDescriptor`
* `PluginVersion`
* `PluginConfigSchema`
* `PluginCapability`
* `PluginLifecycleState`
* `PluginPolicyRequirements`
* `PluginRuntimeIsolation`
* `PluginHealth`
* `PluginAuditPolicy`

---

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`

**What to change:**

The router centrally wires many domains. 

**Exact task:**

1. Keep current routes working.
2. Introduce plane-owned route registrar interfaces:

   * `DataPlaneRouteRegistrar`
   * `EventPlaneRouteRegistrar`
   * `ActionPlaneRouteRegistrar`
   * `GovernancePlaneRouteRegistrar`
   * `OperationsPlaneRouteRegistrar`
   * `MediaRouteRegistrar`
   * `PluginRouteRegistrar`
3. Move route groups out incrementally.
4. Generate route metadata into `RouteSecurityRegistry`.

---

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java`

**What to change:**

Make route security metadata generated from route registrars.

**Exact task:**

Add generated route IDs and permission IDs for plugin routes:

* `plugin:read`
* `plugin:install`
* `plugin:enable`
* `plugin:disable`
* `plugin:upgrade`
* `plugin:validate`
* `plugin:sandbox`

---

## Tests for Pass 8

### `products/data-cloud/extensions/plugins/src/test/java/**`

Add tests:

* plugin descriptor validation.
* config schema validation.
* lifecycle transitions.
* policy requirements.
* sandbox/isolation requirements.

### `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilderTest.java`

Update tests:

* route registrars register expected routes.
* every route has security metadata.
* plugin routes have canonical permissions.

---

# Pass 9 — Observability and operations trace model

## `products/data-cloud/planes/action/observability/**`

**What to change:**

Create one trace model for Data Cloud + AEP + agents + media.

**Exact task:**

Add canonical IDs:

* `requestId`
* `tenantId`
* `principalId`
* `operationId`
* `eventOffset`
* `pipelineExecutionId`
* `patternInstanceId`
* `agentInvocationId`
* `mediaArtifactId`
* `mediaJobId`
* `correlationId`
* `traceId`

---

## `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`

**What to change:**

Execution cancellation already records operation and metrics. 

**Exact task:**

Apply the same structured operation record model to:

* execute
* retry
* rollback
* checkpoint
* restore
* cancel
* logs
* list executions

Each response should include:

* `requestId`
* `operationId`
* `tenantId`
* `pipelineId`
* `executionId`
* `traceId`
* `correlationId`
* `status`
* `retryable`
* `failureReason`

---

## `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`

**Exact task:**

Add operation/trace IDs to all media responses:

* create artifact
* transcribe
* analyze
* retry
* delete
* consent update
* retention update

---

## `products/data-cloud/delivery/ui/src/pages/OperationsJobCenterPage.tsx`

**What to change:**

Expose cross-plane operation timeline.

**Exact task:**

Add filters:

* operation type
* status
* plane
* tenant
* correlation ID
* media job ID
* pipeline execution ID
* agent invocation ID

---

## Tests for Pass 9

Add tests:

* operation IDs appear on mutating responses.
* failed action produces traceable error response.
* media processing job appears in operations.
* agent denial appears in operations timeline.

---

# Pass 10 — Shared library boundary cleanup

The architecture explicitly says shared platform modules should remain shared only when genuinely cross-product, and Data Cloud/AEP semantics should move or stay in Data Cloud/Action Plane as appropriate. 

## `platform/java/agent-core/**`

**What to change:**

Keep only generic agent contracts.

**Exact task:**

Move or avoid adding:

* AEP operator semantics
* EventCloud semantics
* Data Cloud persistence semantics
* review/runtime dispatch semantics

Those belong under `products/data-cloud/planes/action/**` or AEP-owned contracts.

---

## `platform/java/workflow/**`

**What to change:**

Keep generic workflow primitives only.

**Exact task:**

Move Data Cloud pipeline metadata and Action Plane runtime behavior into:

* `products/data-cloud/planes/action/orchestrator`
* `products/data-cloud/planes/action/central-runtime`

---

## `platform/java/messaging/**`

**What to change:**

Keep generic messaging abstractions only.

**Exact task:**

Move Data Cloud storage-plane event routing into:

* `products/data-cloud/planes/event/core`
* `products/data-cloud/planes/event/store`

Move Action Plane event semantics into:

* `products/data-cloud/planes/action/operator-contracts`
* `products/data-cloud/planes/action/engine`

---

## `platform/java/ai-integration/**`

**What to change:**

Keep provider abstractions only.

**Exact task:**

Move product-specific AI behavior into:

* query assist → `products/data-cloud/planes/intelligence/analytics`
* schema inference → `products/data-cloud/planes/intelligence/analytics`
* action suggestions → `products/data-cloud/planes/action/*`

---

## `platform/java/data-governance/**`

**What to change:**

Keep generic policy primitives only.

**Exact task:**

Move Data Cloud-specific retention/redaction/provenance/audit evidence into:

* `products/data-cloud/planes/governance/core`

---

## Tests for Pass 10

### `scripts/check-action-plane-boundaries.mjs`

Update to enforce:

* Data/Event/Governance do not import Action internals.
* Platform modules do not import Data Cloud product semantics.
* Contracts do not import runtime implementation.
* UI does not import backend internals.

### `scripts/__tests__/check-action-plane-boundaries.test.mjs`

Add fixture cases for every forbidden dependency.

---

# Pass 11 — UI i18n, a11y, and raw-string cleanup

## `products/data-cloud/delivery/ui/src/pages/MediaArtifactPage.tsx`

**What to change:**

Raw translation keys are rendered as visible text. 

**Exact task:**

Replace raw strings with `t(...)`:

* `mediaArtifacts.registerArtifact`
* `mediaArtifacts.registerDescription`
* `mediaArtifactDetails.agentId`
* `mediaArtifactDetails.mediaType`
* `mediaArtifactDetails.storageUri`
* `mediaArtifactDetails.durationMs`
* `mediaArtifactDetails.consentStatus`
* `mediaArtifactDetails.checksum`
* `mediaArtifactDetails.retentionPolicy`
* `mediaArtifactDetails.retentionUntil`
* `common.cancel`
* `mediaArtifacts.registering`

Also add:

* dialog title ID
* `aria-labelledby`
* focus trap
* Escape key close
* initial focus
* error summary

---

## `products/data-cloud/delivery/ui/src/components/security/RuntimeCapabilityRouteGate.tsx`

**What to change:**

Hardcoded UI strings remain. 

**Exact task:**

Replace:

* `"Preview"`
* `"Degraded"`
* `"Checking surface availability..."`

with i18n keys.

---

## `products/data-cloud/delivery/ui/src/pages/DisabledSurfacePage.tsx`

**Exact task:**

Ensure disabled/degraded/unavailable/target-only states have:

* translated title,
* translated description,
* clear next action,
* keyboard focus,
* accessible status role.

---

## Translation files

Likely locations:

* `products/data-cloud/delivery/ui/src/i18n/**`
* `products/data-cloud/delivery/ui/src/locales/**`
* or current translation resource files used by `react-i18next`

**Exact task:**

Add all keys introduced above.

---

## Tests for Pass 11

Add/update:

* `MediaArtifactPage.test.tsx`
* `RuntimeCapabilityRouteGate.test.tsx`
* `DisabledSurfacePage.test.tsx`

Assertions:

* no raw i18n keys visible.
* dialog has accessible name.
* preview/degraded banners are translated.
* disabled state is announced correctly.

---

# Pass 12 — OpenAPI and contract alignment

## `products/data-cloud/contracts/openapi/data-cloud.yaml`

**What to change:**

Update contracts for:

* connector lifecycle,
* media lifecycle,
* checkpoint/replay,
* operation trace model,
* runtime truth readiness classes.

**Exact task:**

Add/modify schemas:

* `ReplayRequest`
* `ReplayResponse`
* `Checkpoint`
* `CheckpointCommitRequest`
* `SurfaceReadinessClass`
* `ConnectorHealth`
* `ConnectorSyncStatus`
* `MediaArtifact`
* `MediaProcessingJob`
* `Transcript`
* `FrameIndex`
* `OperationTrace`

---

## `products/data-cloud/contracts/openapi/action-plane.yaml`

**What to change:**

Add AEP semantic lifecycle contracts.

**Exact task:**

Add schemas/endpoints for:

* PatternSpec validate
* PatternSpec compile
* pattern activate/deactivate
* pattern match explain
* learning review
* recommendation candidate
* replay-safe agent invocation

---

## `products/data-cloud/contracts/openapi/aep.yaml`

**What to change:**

Keep compatibility only.

**Exact task:**

Ensure it mirrors canonical `action-plane.yaml` only where compatibility is required. Do not make `aep.yaml` the new canonical source.

---

## `products/data-cloud/delivery/sdk/**`

**What to change:**

Regenerate SDKs from updated OpenAPI after contract changes.

**Exact task:**

Update generated Java/TypeScript/Python clients only through the SDK generation flow.

Do not run release readiness.

---

# Pass 13 — Focused tests only, no evidence-generation runs

## Do update/add targeted tests

Add or update tests in:

* `products/data-cloud/delivery/launcher/src/test/java/**`
* `products/data-cloud/delivery/api/src/test/java/**`
* `products/data-cloud/planes/shared-spi/src/test/java/**`
* `products/data-cloud/planes/action/**/src/test/java/**`
* `products/data-cloud/delivery/ui/src/**/*.test.tsx`
* `products/data-cloud/integration-tests/src/test/java/**`

## Do not run or add tasks around these scripts in this iteration

Do **not** execute or plan execution for:

* `scripts/validate-data-cloud-release-evidence.mjs`
* `scripts/check-data-cloud-release-runtime-profile.mjs`
* `scripts/check-data-cloud-operations-readiness.mjs`
* `scripts/check-data-cloud-maturity-proof.mjs`
* release evidence bundle scripts
* release freshness scripts
* product promotion workflows

The task plan may update static code/tests, but it must not include release-readiness execution.

---

# Minimal verification passes after implementation

Use only targeted hardening verification, grouped to minimize repetition:

1. **Security pass:** resolver/filter/handler permission tests.
2. **Event pass:** replay/checkpoint/event envelope tests.
3. **AEP pass:** PatternSpec/operator/agent lifecycle tests.
4. **Media pass:** media API + UI tests.
5. **Connector pass:** connector lifecycle tests.
6. **UI pass:** runtime truth route gating + i18n/a11y tests.
7. **Boundary pass:** dependency-boundary tests.

Do **not** add release-readiness/evidence verification to these passes.
