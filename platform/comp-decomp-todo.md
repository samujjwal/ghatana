Below is the **more critical, expanded, file-by-file next-iteration task list** for `samujjwal/ghatana` at commit `e65044bc452b69a02209f92db2d002e7c534dcd1`.

The reason the score is stuck around **2.5–2.6** is not lack of more routes, docs, scripts, or evidence. The root cause is that Data Cloud has many partially implemented systems, but they are not yet collapsed into **one canonical runtime truth, one lifecycle model, one permission/policy/audit model, one extension model, and one simple UI mental model**. The next iteration should be a consolidation leap, not another additive pass.

**Imperative rule for the implementation pass:**
Do **not** duplicate any logic, code, registry, route, component, DTO, schema, permission map, feature flag, test fixture, or file. Before adding anything, first locate the canonical owner and extend it. If two files already express the same concept, merge into one owner and delete or downgrade the duplicate. No compatibility layer unless the current code demonstrably needs it for an existing caller.

Also: **do not run or plan release-readiness checks, release evidence generation, release bundles, product promotion, or evidence freshness workflows in this iteration.** The repo itself says this pass is not release-readiness execution and explicitly excludes release-readiness/evidence workflows. 

---

# Why the score is stuck

Data Cloud now has a good product shape: it is documented as one governed operational data fabric with Action Plane/AEP integrated under `products/data-cloud/planes/action`.  But the current status is still partial: Action Plane semantic lifecycle is incomplete, Context Plane is target-only, and audio-video is partial integration rather than a full durable modality. 

The next iteration must fix these root causes:

1. **Runtime truth is not the only source of truth.** Backend `/api/v1/surfaces`, UI `RouteSurfaceRegistry`, `RoleProtectedRoute`, and `RuntimeCapabilityRouteGate` all make separate availability decisions.
2. **Action Plane/AEP has routes and modules but not complete executable semantics.** PatternSpec compiler, EventCloud SPI, pattern lifecycle, learning-to-recommendation, and replay-safe agent execution are still explicitly incomplete. 
3. **Media/audio-video has useful lifecycle pieces, but not a full durable Data Cloud modality.** The controller has consent, retention, processing state, job IDs, transcript/frame index responses, and operation recording, but event emitter and operation recorder are optional and processor execution is not yet a first-class durable job runtime. 
4. **Security and governance are fragmented.** Several handlers check permissions locally instead of through one canonical policy/audit middleware.
5. **UI still has cognitive-load leaks.** It exposes many direct surfaces while also trying to hide advanced concepts through preview gates.
6. **Shared libraries still contain Data Cloud/AEP semantic drift.** The canonical architecture already calls this out and lists modules that must be split or moved. 

---

# Target outcome for next iteration

A realistic leapfrog target is:

| Area                       | Current | Next target |
| -------------------------- | ------: | ----------: |
| Overall score              |    ~2.6 | **3.6–3.9** |
| Runtime truth/UI gating    |     2.5 |     **4.0** |
| Action Plane/AEP lifecycle |     2.4 |     **3.5** |
| Media/audio-video          | 1.8–2.2 |     **3.5** |
| Security/audit/policy      |     3.0 |     **4.0** |
| UI simplicity              |     2.8 |     **3.8** |
| Shared-library boundary    |     2.6 |     **3.6** |

This leap only happens if the next pass removes duplicated truth and partial lifecycle semantics. Adding more isolated routes/pages will keep the score stuck.

---

# Workstream 1 — Runtime Truth as the only executable product contract

## Goal

Make `/api/v1/surfaces` the single backend-owned contract for UI navigation, route access, capability availability, disabled states, dependency status, preview audience, owner plane, and user action availability.

The backend already has `SurfaceRegistryHandler` and `SurfaceRecord`, and `SurfaceRecord` enforces that `LIVE` surfaces must have dependency probes.   But the UI still has its own local registry and preview lifecycle rules.

## File-by-file tasks

| File                                                                                                                           | What to change                                                                                                                                                                                                                                                                                                                                                                            |
| ------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/SurfaceRecord.java`                   | Add fields required by UI so it no longer needs a parallel route registry: `path`, `label`, `labelKey`, `description`, `descriptionKey`, `iconName`, `minimumShellRole`, `discoverable`, `lifecycle`, `previewAudience`, `routeGroup`, `sortOrder`, `primaryNavigation`, `contextualNavigation`, `fallbackReason`, and `recommendedAction`. Preserve existing dependency probe invariant. |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SurfaceRegistryHandler.java` | Return the enriched `SurfaceRecord` contract from `/api/v1/surfaces`. Do not require the UI to infer lifecycle, audience, or labels from a local TypeScript registry. Keep `/api/v1/surfaces/schema` aligned with the enriched contract.                                                                                                                                                  |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/SurfaceSchemaGenerator.java`          | Update schema generation for the enriched surface fields. Add enum validation for statuses/lifecycle/audience. Ensure schema and response shape match exactly.                                                                                                                                                                                                                            |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java`             | Build one canonical surface list from actual wired dependencies. Do not hand-maintain capability state separately from handler/plugin/dependency wiring.                                                                                                                                                                                                                                  |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`          | Stop encoding route lifecycle and preview semantics in comments and scattered route registration. Route registration should reference surface IDs from the canonical surface contract.                                                                                                                                                                                                    |
| `products/data-cloud/delivery/ui/src/api/surfaces.service.ts`                                                                  | Make this the only UI data source for runtime surface availability, route metadata, and dependency limitations. Remove any transform that loses backend fields.                                                                                                                                                                                                                           |
| `products/data-cloud/delivery/ui/src/lib/capabilities/RuntimeCapabilityService.ts`                                             | Convert from legacy “capability enabled” map to a typed runtime-surface store. It should expose `getSurfaceByPath`, `getSurfaceByAlias`, `getNavigationSurfaces`, and `getActionAvailability`.                                                                                                                                                                                            |
| `products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts`                                                      | Downgrade this from executable registry to fallback-only static metadata. Remove local lifecycle decisions. Do not let this file decide whether routes are active, preview, target-only, or disabled.                                                                                                                                                                                     |
| `products/data-cloud/delivery/ui/src/components/security/RuntimeCapabilityRouteGate.tsx`                                       | Make it consume only backend `SurfaceSignal`/`SurfaceRecord` status and audience. It should render loading, disabled, unavailable, degraded, preview, and misconfigured states consistently.                                                                                                                                                                                              |
| `products/data-cloud/delivery/ui/src/components/security/RoleProtectedRoute.tsx`                                               | Remove preview lifecycle enforcement from this component. It should only handle shell disclosure role, not runtime capability lifecycle. Currently `operator-preview` requires `allowPreviewAs`, which can block a route before `RuntimeCapabilityRouteGate` evaluates backend truth.                                                                                                     |
| `products/data-cloud/delivery/ui/src/routes.tsx`                                                                               | Replace per-route duplicated aliases/preview settings with `surfaceId` references. Every gated route should resolve availability through backend runtime truth.                                                                                                                                                                                                                           |
| `products/data-cloud/delivery/ui/src/layouts/DefaultLayout.tsx`                                                                | Build sidebar navigation from backend surfaces, not the local registry plus hardcoded `corePaths`/`managePaths`. Current nav is registry-derived but still filtered locally.                                                                                                                                                                                                              |
| `products/data-cloud/delivery/ui/src/components/common/GlobalSearch.tsx`                                                       | Use backend-discoverable surfaces for search destinations. Do not search target-only, disabled, or unavailable surfaces unless explicitly in admin diagnostics mode.                                                                                                                                                                                                                      |
| `products/data-cloud/delivery/ui/src/pages/DisabledSurfacePage.tsx`                                                            | Render backend-provided owner plane, dependencies, probes, limitations, next action, and runtime profile. Do not invent UI-only disabled explanations.                                                                                                                                                                                                                                    |
| `products/data-cloud/delivery/ui/src/pages/RuntimeTruthPage.tsx`                                                               | Render the same `/api/v1/surfaces` data used by nav/gates. Do not use a separate diagnostic model.                                                                                                                                                                                                                                                                                        |

## Acceptance criteria

* `/api/v1/surfaces` is the only executable source of truth for UI visibility and capability state.
* Operator-preview routes no longer fail before backend runtime truth is checked.
* Context, media, agents, plugins, and fabric show consistent enabled/disabled/degraded explanations.
* No duplicate route lifecycle registry remains active in UI.

---

# Workstream 2 — Action Plane/AEP executable semantic lifecycle

## Goal

Stop treating Action Plane readiness as route/API presence. Implement the missing semantic core: PatternSpec compilation, EventCloud SPI, pattern lifecycle, learning-to-recommendation, and replay-safe agent execution.

The module inventory explicitly says no Action Plane module should be considered production-ready without executable PatternSpec compiler, EventCloud SPI, pattern lifecycle, learning-to-recommendation loop, and replay-safe agent execution with side-effect controls. 

## File-by-file tasks

| File / directory                                                                                                                   | What to change                                                                                                                                                                                                                                           |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/planes/action/operator-contracts/src/main/java/**`                                                            | Add canonical `PatternSpec`, `PatternSpecVersion`, `PatternInput`, `PatternOutput`, `PatternWindow`, `PatternCondition`, `PatternAction`, `PatternGovernance`, and `PatternCompilationResult` types. Do not create a second PatternSpec model elsewhere. |
| `products/data-cloud/planes/action/operator-contracts/src/main/java/**/EventOperatorCapability*.java`                              | Ensure EventOperatorCapability declares deterministic inputs, side-effect policy, idempotency scope, replay mode, approval requirement, and observability metadata.                                                                                      |
| `products/data-cloud/planes/action/engine/src/main/java/**`                                                                        | Implement the PatternSpec compiler and evaluator. Compile PatternSpec into executable detector plans. Do not hardcode special-case detectors in handlers.                                                                                                |
| `products/data-cloud/planes/action/engine/src/main/java/**`                                                                        | Add pattern lifecycle states: draft, validating, active, paused, superseded, retired, failed, archived.                                                                                                                                                  |
| `products/data-cloud/planes/action/engine/src/main/java/**`                                                                        | Add learning feedback ingestion into the pattern lifecycle. Feedback should produce recommendations, not mutate production detectors directly.                                                                                                           |
| `products/data-cloud/planes/action/event-bridge/src/main/java/**`                                                                  | Implement EventCloud SPI bridge using Data Cloud Event Plane public SPI only. Data/Event/Governance planes must not import Action internals.                                                                                                             |
| `products/data-cloud/planes/action/orchestrator/src/main/java/**`                                                                  | Implement replay-safe action execution lifecycle. All orchestration must carry tenant, correlation, causation, idempotency key, replay mode, and policy context.                                                                                         |
| `products/data-cloud/planes/action/agent-runtime/src/main/java/**`                                                                 | Implement replay-safe agent execution with side-effect controls. Agents must separate planning, review, approval, execution, and compensation.                                                                                                           |
| `products/data-cloud/planes/action/registry/src/main/java/**`                                                                      | Store pattern, agent, and action definitions as metadata only. Do not embed runtime execution semantics into registry persistence.                                                                                                                       |
| `products/data-cloud/planes/action/security/src/main/java/**`                                                                      | Add policy decision contracts for pattern activation, agent execution, tool invocation, rollback, and autonomous action.                                                                                                                                 |
| `products/data-cloud/planes/action/compliance/src/main/java/**`                                                                    | Persist review, approval, rejection, rollback, and learning evidence as Data Cloud evidence, while keeping AEP semantics in Action Plane contracts.                                                                                                      |
| `products/data-cloud/planes/action/observability/src/main/java/**`                                                                 | Add trace/span/event models for pattern evaluation, agent plan, approval, execution, retry, rollback, and learning recommendation.                                                                                                                       |
| `products/data-cloud/contracts/openapi/action-plane.yaml`                                                                          | Add/align endpoints for pattern specs, pattern compilation, pattern lifecycle, action runs, approvals, replay, learning recommendations, and execution traces.                                                                                           |
| `products/data-cloud/contracts/openapi/aep.yaml`                                                                                   | Keep compatibility only. It must not become the canonical home for new PatternSpec/EventCloud semantics; the Data Cloud AEP boundary doc says compatibility contracts must not become canonical.                                                         |
| `products/data-cloud/contracts/openapi/data-cloud.yaml`                                                                            | Expose only Data Cloud-owned persistence/governance/action-surface contracts. Do not duplicate full AEP semantic models here if `action-plane.yaml` owns them.                                                                                           |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`   | Replace generic workflow execution with canonical Action Run lifecycle when executing pipelines. It already delegates to `WorkflowExecutionCapability` and returns 503 when absent; make that capability backed by the canonical Action Plane runtime.   |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/plugins/WorkflowExecutionCapability.java` | Add idempotency, replay, policy, approval, and trace fields to the capability contract.                                                                                                                                                                  |
| `products/data-cloud/delivery/ui/src/pages/WorkflowsPage.tsx`                                                                      | Rename product semantics from generic workflow to pipeline/action execution where appropriate. UI should show run state, last run, failure reason, retryability, approval state, and rollback availability.                                              |
| `products/data-cloud/delivery/ui/src/pages/WorkflowDesigner.tsx`                                                                   | Do not allow a user to create executable flows that bypass PatternSpec/action-run validation.                                                                                                                                                            |
| `products/data-cloud/delivery/ui/src/pages/AgentPluginManagerPage.tsx`                                                             | Show agents as governed capabilities with policy, tools, approval requirement, and runtime state, not as loose plugins.                                                                                                                                  |

## Acceptance criteria

* Action Plane has one executable lifecycle model.
* AEP semantics are not duplicated in Data Plane, Event Plane, UI, or shared libraries.
* Pipeline execution, pattern detection, agent execution, learning feedback, and rollback all use the same run model.

---

# Workstream 3 — Media/audio-video as a first-class durable Data Cloud modality

## Goal

Turn media from “metadata plus trigger routes” into a governed Data Cloud data modality with durable processing jobs, consent, retention, transcripts, frame indexes, lineage, and operations visibility.

The controller already has consent and retention checks, processing states, job IDs, transcript/frame-index responses, operation recording, and event emission hooks. But event emitter and operation recorder are nullable, and transcription/vision job IDs use timestamp strings instead of a durable job runtime.  

## File-by-file tasks

| File / directory                                                                                                          | What to change                                                                                                                                                                                                                                                                                                      |
| ------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`        | Split into thin controller + service. Controller should only parse request, resolve principal, call service, and return response. Move lifecycle rules, consent checks, retention checks, job creation, event emission, and operation recording to a canonical service.                                             |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`        | Remove local role-to-permission mapping. `hasPermissionFromRoles` duplicates permission logic and should be replaced by canonical permission/policy service.                                                                                                                                                        |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`        | Make event emission and operation recording mandatory for production-like profiles. If the emitter/recorder is absent, surface must be `MISCONFIGURED`, not silently degraded.                                                                                                                                      |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactRecord.java`              | Confirm it is the single canonical record for media artifact metadata. Add or normalize fields: `contentClass`, `privacyClass`, `consentStatus`, `retentionPolicy`, `retentionUntil`, `processingState`, `storageProvider`, `lineageRef`, `policyContext`, `redactionState`, `checksum`, `sizeBytes`, `durationMs`. |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaProcessingJob.java`               | Make this the canonical durable media job model. Add `queuedAt`, `startedAt`, `completedAt`, `attempt`, `maxAttempts`, `processorId`, `processorVersion`, `inputArtifactId`, `outputArtifactIds`, `traceId`, `requestId`, `failureCode`, `failureReason`, `retryable`, `cancelledBy`, `cancelledAt`.                |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactRepository.java`          | Add atomic methods: `createJob`, `transitionJobState`, `attachTranscript`, `attachFrameIndex`, `markFailed`, `markCancelled`, `markRetentionExpired`, `findJobsByState`. Do not scatter state transitions in controller code.                                                                                       |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/DataCloudMediaArtifactRepository.java` | Implement all repository methods durably through Data Cloud entity/event storage. No in-memory-only production path.                                                                                                                                                                                                |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactEventEmitter.java`        | Emit canonical events for `registered`, `consent-updated`, `processing-requested`, `processing-started`, `transcript-created`, `frame-index-created`, `processing-failed`, `retry-requested`, `retention-expired`, `deleted`.                                                                                       |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/media/MediaArtifactService.java`                    | Create this only if no existing service owner exists. It should own all media lifecycle transitions. If a service already exists, extend it instead.                                                                                                                                                                |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/media/MediaProcessorPort.java`                      | Create/extend a canonical port for audio/video/image processors. It should return durable job result objects, not raw handler-specific maps.                                                                                                                                                                        |
| `products/audio-video/modules/speech/stt-service/**`                                                                      | Implement adapter to `MediaProcessorPort` for transcription. Do not call speech service directly from controller.                                                                                                                                                                                                   |
| `products/audio-video/modules/vision/vision-service/**`                                                                   | Implement adapter to `MediaProcessorPort` for frame/object/scene analysis.                                                                                                                                                                                                                                          |
| `products/audio-video/modules/intelligence/multimodal-service/**`                                                         | Implement adapter for multimodal extraction and summary, but keep it behind media capability runtime truth until complete.                                                                                                                                                                                          |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`     | Add missing media subroutes already handled in controller but not registered, including jobs, transcript, frame index, retry, and consent PATCH. Current router only registers create/list/get/delete/transcribe/analyze.                                                                                           |
| `products/data-cloud/delivery/ui/src/pages/MediaArtifactPage.tsx`                                                         | Show media artifacts with processing state, consent, retention, last job, transcript/frame-index availability, retry eligibility, and clear disabled/degraded messaging.                                                                                                                                            |
| `products/data-cloud/delivery/ui/src/api/media.service.ts`                                                                | Use generated OpenAPI types. Do not define ad hoc media DTOs.                                                                                                                                                                                                                                                       |
| `products/data-cloud/contracts/openapi/data-cloud.yaml`                                                                   | Expand media schema and endpoints to match durable lifecycle. The contract already lists `media` as artifact metadata and processing, but it must describe actual job lifecycle.                                                                                                                                    |

## Acceptance criteria

* Media processing is durable, auditable, retryable, cancellable, and visible.
* Audio/video/image processors are pluggable through a canonical port.
* No direct controller-owned lifecycle logic remains.
* Media capability state in `/api/v1/surfaces` accurately reflects processor, storage, event, consent, and audit dependencies.

---

# Workstream 4 — Unified security, permission, policy, audit, and tenant enforcement

## Goal

Replace local permission checks and nullable audit behavior with one canonical enforcement path.

The OpenAPI already says production tenant identity must be derived from auth and security-sensitive routes need audit/policy controls.  But handlers still perform some local checks and optional audit/operation recording.

## File-by-file tasks

| File / directory                                                                                                                   | What to change                                                                                                                                                                                                                                     |
| ---------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java`             | Ensure this is the only place that resolves principal, tenant, roles, request ID, correlation ID, and auth mode for HTTP requests.                                                                                                                 |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java`         | Add canonical methods: `requireTenant`, `requirePermission`, `requirePolicyDecision`, `requireAuditContext`, `resolveOperationContext`, `forbiddenWithPolicyReason`.                                                                               |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java`                 | Wire policy engine, audit service, idempotency store, transaction manager, and operation recorder as profile-aware required dependencies. Production-like profiles should fail closed when required safety dependencies are absent.                |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java` | Move startup safety validation into one profile capability validator. Current startup has auth validation and API key enforcement; extend this pattern to audit, policy, idempotency, transaction, event store, and media processor dependencies.  |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`                 | Remove local permission role mapping and use `HttpHandlerSupport`/policy enforcement.                                                                                                                                                              |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`   | Make idempotency mandatory for mutating action routes in production-like profiles. It currently proceeds when `idempotencyStore == null`.                                                                                                          |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java`       | Ensure retention, purge, redaction, policy create/update/delete/toggle all use the same policy/audit context.                                                                                                                                      |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/PluginInstallHandler.java`       | Enforce plugin install/enable/disable/upgrade through policy, audit, sandbox, and idempotency.                                                                                                                                                     |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AiAssistHandler.java`            | Add prompt/tool safety policy and audit event for every AI suggestion/application/feedback route.                                                                                                                                                  |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AgentCatalogHandler.java`        | Enforce agent descriptor read vs execution/action permissions separately.                                                                                                                                                                          |
| `products/data-cloud/planes/action/security/src/main/java/**`                                                                      | Own canonical action permissions and policy decisions. Do not duplicate Action Plane permissions in UI or handler-local maps.                                                                                                                      |
| `products/data-cloud/planes/governance/core/src/main/java/**`                                                                      | Own governance policy primitives and reusable decisions.                                                                                                                                                                                           |
| `products/data-cloud/contracts/openapi/data-cloud.yaml`                                                                            | Add security/audit/policy response schemas consistently: `ForbiddenPolicyResponse`, `AuditRequiredResponse`, `TenantMismatchResponse`, `BreakGlassRequiredResponse`.                                                                               |

## Acceptance criteria

* No handler has its own role-to-permission switch.
* No sensitive mutation succeeds without tenant, policy, idempotency, audit, and correlation context.
* Missing required safety dependency marks surface as `MISCONFIGURED` and blocks production startup.

---

# Workstream 5 — Data Plane, Event Plane, and core runtime correctness

## Goal

Strengthen the actual backbone: entity storage, event log, query, tailing, idempotency, durability, and transaction behavior.

## File-by-file tasks

| File                                                                                                                            | What to change                                                                                                                                                                                                       |
| ------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/DataCloud.java`                           | Fix `DataCloudConfig` compact constructor bug: it currently forces `allowInvalidLocalEventsForTests = false`, overriding the constructor input and making `forTesting()` misleading.                                 |
| `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/DataCloud.java`                           | Fix `tailEvents` async behavior. Do not call `subscriptionPromise.getResult()` immediately; return a subscription abstraction that handles pending subscription, failure, cancellation, and callback errors safely.  |
| `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/DataCloud.java`                           | Replace generic `System.getLogger` close-failure logging with the existing SLF4J logger for consistent observability.                                                                                                |
| `products/data-cloud/planes/data/entity/src/main/java/com/ghatana/datacloud/entity/**`                                          | Ensure entity write lifecycle is consistently: validate schema → enforce policy → idempotency → transaction → save → append event → lineage/audit. No direct save path should bypass this.                           |
| `products/data-cloud/planes/data/entity/src/main/java/com/ghatana/datacloud/entity/importexport/EntityImportExportService.java` | Ensure import/export applies tenant, policy, redaction, audit, idempotency, and operation recording.                                                                                                                 |
| `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/spi/EntityStore.java`                                | Make query/filter/sort/pagination contracts explicit enough for H2, external stores, and UI tables to behave consistently.                                                                                           |
| `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/spi/EventLogStore.java`                              | Add explicit tail subscription states and error callback semantics.                                                                                                                                                  |
| `products/data-cloud/planes/event/store/src/main/java/**`                                                                       | Ensure H2/event store implementations follow the same offset, ordering, time-range, and tail semantics as the SPI.                                                                                                   |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/EventStoreHealthProbe.java`                 | Extend health probe to check append/read/tail readiness where safe.                                                                                                                                                  |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/JdbcDatabaseHealthProbe.java`               | Ensure DB health maps into runtime truth dependency probes, not just health endpoints.                                                                                                                               |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/transaction/DataCloudTransactionManager.java`        | Use this for multi-step writes across critical entity/media/action operations. Do not leave transaction support optional for production-like profiles.                                                               |
| `products/data-cloud/storage/H2WriteIdempotencyStore.java` / `H2EntityWriteIdempotencyStore.java`                               | Ensure idempotency scopes are not entity-only; use operation-specific scope for action, media, plugin, governance, export, and import operations.                                                                    |

## Acceptance criteria

* Core storage/event paths are reliable and deterministic.
* Event tailing cannot silently fail or expose inconsistent state.
* Mutating workflows have one transaction/idempotency story.

---

# Workstream 6 — UI simplification and 0 cognitive load

## Goal

Make Data Cloud simple to use. Default user mental model should be: **Home, Data, Events, Query, Pipelines, Trust, Operations**. Advanced surfaces should appear through contextual drilldown only when runtime truth says they are available.

The docs already define this default navigation.  The UI route file also states a simplified IA intent. 

## File-by-file tasks

| File / directory                                                       | What to change                                                                                                                                                                                                                    |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/delivery/ui/src/layouts/DefaultLayout.tsx`        | Remove local hardcoded nav filtering and use backend surface metadata. Keep default nav lightweight.                                                                                                                              |
| `products/data-cloud/delivery/ui/src/layouts/DefaultLayout.tsx`        | Replace raw visible text `Data Cloud` with i18n key.                                                                                                                                                                              |
| `products/data-cloud/delivery/ui/src/routes.tsx`                       | Remove user-visible direct routes from default discoverability unless backend surfaces expose them. Keep direct deep links guarded but not discoverable.                                                                          |
| `products/data-cloud/delivery/ui/src/pages/IntelligentHub.tsx`         | Make this a real control tower: health/runtime truth, recent operations, next actions, degraded surfaces, data quality, and action runs. No decorative cards.                                                                     |
| `products/data-cloud/delivery/ui/src/pages/DataPage.tsx`               | Consolidate collections, entities, lineage, quality, context, and connectors into one progressive Data workspace. Avoid separate mental models for “entities,” “fabric,” and “context” unless runtime truth makes them available. |
| `products/data-cloud/delivery/ui/src/pages/DataExplorer.tsx`           | Use shared table/list/detail components. Row click should open details; filters/sort/search should be consistent.                                                                                                                 |
| `products/data-cloud/delivery/ui/src/pages/EventExplorerPage.tsx`      | Connect event explorer to replay, pattern linkage, correlation/causation, and action run links. Do not show raw event stream without useful drilldown.                                                                            |
| `products/data-cloud/delivery/ui/src/pages/WorkflowsPage.tsx`          | Rename visible model from “workflows” to product-appropriate “Pipelines” and show run state, owner, trigger, last run, health, approval/retry state.                                                                              |
| `products/data-cloud/delivery/ui/src/pages/TrustCenter.tsx`            | Consolidate policy, audit, privacy, retention, redaction, and compliance into a single low-cognitive-load Trust Center.                                                                                                           |
| `products/data-cloud/delivery/ui/src/pages/OperationsConsolePage.tsx`  | Show runtime truth, dependency probes, active jobs, error queues, audit posture, trace posture, and degraded capabilities.                                                                                                        |
| `products/data-cloud/delivery/ui/src/pages/MediaArtifactPage.tsx`      | Make media simple: artifact list, consent, retention, processing state, transcript/frame index, retry/cancel. Avoid exposing implementation details.                                                                              |
| `products/data-cloud/delivery/ui/src/pages/AgentPluginManagerPage.tsx` | Split agent catalog from plugin catalog in the UI. Agents are governed operators; plugins are extension packages. Do not conflate them.                                                                                           |
| `products/data-cloud/delivery/ui/src/pages/PluginsPage.tsx`            | Show lifecycle, version, sandbox, validation, permissions, owner, and compatibility.                                                                                                                                              |
| `products/data-cloud/delivery/ui/src/components/common/**`             | Create or consolidate common `DataTable`, `ActionToolbar`, `StatusPill`, `RuntimeStateBanner`, `EmptyState`, `ErrorState`, `UnauthorizedState`, `DegradedState`, `ConfirmDialog`. Do not create feature-local duplicates.         |
| `products/data-cloud/delivery/ui/src/features/data-fabric/**`          | Remove hardcoded/demo metrics and use backend surfaces/data only.                                                                                                                                                                 |
| `products/data-cloud/delivery/ui/src/lib/i18n/**`                      | Ensure all visible strings are translated. No raw text in pages, layouts, tables, buttons, cards, dialogs, or errors.                                                                                                             |

## Acceptance criteria

* Default UI is outcome-first and not plane-first.
* Advanced concepts appear only as contextual drilldowns.
* Same table/action/status/error patterns across all surfaces.
* No raw visible strings in touched UI files.

---

# Workstream 7 — Context Plane decision: promote or keep unavailable

## Goal

Stop showing Context as half-real. It is currently target-only in architecture, but routes and UI exist.

The architecture says `planes/context/` is target-only and not active as a Gradle module.  The router still exposes context endpoints. 

## File-by-file tasks

| File / directory                                                                                                                 | What to change                                                                                                                                   |
| -------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `products/data-cloud/planes/context/`                                                                                            | Decide: either promote to active module or keep it target-only. Do not keep ambiguous placeholder behavior.                                      |
| `config/generated/settings-gradle-includes.kts`                                                                                  | If promoted, include the context module through canonical product registry generation. Do not edit generated file manually.                      |
| `config/canonical-product-registry.json`                                                                                         | Add Context Plane module only if implementation is production-critical now.                                                                      |
| `scripts/list-data-cloud-active-modules.mjs`                                                                                     | Add context to implementation-critical modules only if promoted. Otherwise keep explicit target-only status.                                     |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/ContextLayerHandler.java`      | If not promoted, remove route registration or return runtime-truth-driven unavailable response. If promoted, use durable storage and governance. |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/CollectionContextHandler.java` | Same: no fake/partial context behavior.                                                                                                          |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`            | Register context routes only when Context Plane surface is active or intentionally preview with dependency probes.                               |
| `products/data-cloud/delivery/ui/src/pages/ContextExplorerPage.tsx`                                                              | If target-only, render disabled page only. If promoted, connect to real context API.                                                             |
| `products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts`                                                        | Remove local target-only enforcement after Workstream 1; backend runtime truth owns this.                                                        |

## Acceptance criteria

* Context is either truly implemented or clearly unavailable.
* No UI/API suggests Context is live when module truth says target-only.

---

# Workstream 8 — Plugin and extension lifecycle consolidation

## Goal

Make plugins pluggable, safe, validated, observable, and versioned. Do not treat plugin routes as just CRUD endpoints.

## File-by-file tasks

| File / directory                                                                                                                     | What to change                                                                                                                                                |
| ------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/extensions/plugins/src/main/java/**`                                                                            | Define canonical plugin descriptor, config schema, permission requirements, dependency list, lifecycle hooks, version compatibility, and sandbox requirement. |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/plugins/DataCloudRuntimePluginManager.java` | Make this the only runtime plugin manager. Remove duplicate plugin discovery/execution logic elsewhere.                                                       |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/PluginInstallHandler.java`         | Route all install/enable/disable/upgrade/validate/conformance through the runtime plugin manager and policy/audit middleware.                                 |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`                | Keep only canonical plugin namespace. Avoid legacy plugin routes except behind explicit feature flag.                                                         |
| `products/data-cloud/contracts/openapi/data-cloud.yaml`                                                                              | Add plugin schemas: descriptor, config schema, validation result, conformance result, sandbox status, compatibility result, lifecycle event.                  |
| `products/data-cloud/delivery/ui/src/pages/PluginsPage.tsx`                                                                          | Show plugin state, validation state, sandbox state, permissions, version compatibility, and owner plane.                                                      |
| `products/data-cloud/delivery/ui/src/pages/PluginDetailsPage.tsx`                                                                    | Add safe enable/disable/upgrade flow with confirmation, policy denial display, and audit operation ID.                                                        |
| `products/data-cloud/planes/shared-spi/src/main/java/**`                                                                             | Keep only generic plugin SPI here. Move Data Cloud-specific plugin semantics into `products/data-cloud/extensions/plugins`.                                   |
| `products/data-cloud/extensions/kernel-bridge/**`                                                                                    | Ensure bridge is extension-only and does not leak launcher/server internals.                                                                                  |

## Acceptance criteria

* One plugin manager.
* One plugin descriptor.
* One plugin lifecycle.
* Plugins cannot bypass policy, audit, sandbox, or runtime truth.

---

# Workstream 9 — Agent runtime and agent catalog separation

## Goal

Stop mixing agent metadata, plugin management, memory, and execution semantics. Agents should be governed operators with capabilities, policies, memory scopes, and run histories.

## File-by-file tasks

| File / directory                                                                                                            | What to change                                                                                                           |
| --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `products/data-cloud/extensions/agent-catalog/src/main/java/**`                                                             | Own read-only/metadata catalog: agent descriptor, owner, capabilities, tools, required policies, memory scopes, version. |
| `products/data-cloud/extensions/agent-registry/src/main/java/**`                                                            | Own tenant-specific agent registration and enablement, not runtime execution.                                            |
| `products/data-cloud/planes/action/agent-runtime/src/main/java/**`                                                          | Own executable agent run lifecycle: plan, review, approve, execute, observe, retry, rollback, compensate.                |
| `products/data-cloud/planes/action/operator-contracts/src/main/java/**`                                                     | Define `AgentCapability`, `ToolCapability`, `AgentExecutionPolicy`, `AgentMemoryScope`, `HumanApprovalRequirement`.      |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AgentCatalogHandler.java` | Keep catalog read paths only. Do not execute agents from catalog handler.                                                |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/MemoryPlaneHandler.java`  | Ensure memory writes/read/search are governed by agent ID, tenant, retention, policy, and audit context.                 |
| `products/data-cloud/delivery/ui/src/pages/AgentPluginManagerPage.tsx`                                                      | Rename/split into agent catalog/agent runtime UI. Do not present agents as plugins.                                      |
| `products/data-cloud/delivery/ui/src/pages/MemoryPlaneViewerPage.tsx`                                                       | Show memory as governed data with retention/policy/search context, not as raw records.                                   |
| `products/data-cloud/contracts/openapi/action-plane.yaml`                                                                   | Add agent run lifecycle endpoints and schemas.                                                                           |
| `products/data-cloud/contracts/openapi/data-cloud.yaml`                                                                     | Keep Data Cloud persistence/catalog endpoints separate from execution semantics.                                         |

## Acceptance criteria

* Agent catalog ≠ plugin management ≠ runtime execution.
* Agent actions require policy/audit/approval where needed.
* Memory is tenant-scoped, retention-aware, and auditable.

---

# Workstream 10 — Shared library boundary cleanup

## Goal

Remove Data Cloud/AEP product semantics from generic shared libraries. Keep shared libraries only when genuinely reusable by unrelated products.

The canonical architecture already gives the rule: keep modules in platform only if they are generic and used by multiple unrelated products; move or split when they mainly describe Data Cloud planes or Action Plane semantics. 

## File-by-file tasks

| File / directory                                  | What to change                                                                                                                                                        |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `platform/java/agent-core/**`                     | Keep only generic agent interfaces. Move Action Plane runtime behavior, dispatch, review, EventOperatorCapability semantics to `products/data-cloud/planes/action/*`. |
| `platform/java/workflow/**`                       | Keep generic workflow primitives only if reused outside Data Cloud/AEP. Move action-run/pipeline execution semantics to Action Plane.                                 |
| `platform/java/messaging/**`                      | Keep generic messaging abstractions. Move Data Cloud storage-plane event routing to `planes/event`; move AEP event semantics to `planes/action`.                      |
| `platform/java/ai-integration/**`                 | Keep provider abstractions only. Move query assist, schema inference, data recommendations, and action suggestions to `planes/intelligence`.                          |
| `platform/java/data-governance/**`                | Keep generic policy primitives only. Move retention, redaction, provenance, and evidence implementations into `planes/governance`.                                    |
| `platform/contracts/**`                           | Remove Data Cloud/Action Plane OpenAPI/schemas if duplicated. Canonical contracts belong under `products/data-cloud/contracts`.                                       |
| `products/data-cloud/planes/shared-spi/**`        | Keep stable SPI only. Do not put product workflow or AEP semantic behavior here.                                                                                      |
| `scripts/check-action-plane-boundaries.mjs`       | Extend to catch semantic leakage from Data/Event/Governance/Context into Action internals and from shared platform into Data Cloud product specifics.                 |
| `scripts/check-dependency-boundaries.mjs`         | Add Data Cloud plane-specific dependency direction assertions.                                                                                                        |
| `eslint-rules/ghatana-architecture-rules.test.js` | Add frontend boundary checks: UI components cannot import app services/stores; generated clients required for API calls.                                              |

## Acceptance criteria

* Product semantics live in product planes.
* Shared libraries stay generic.
* Boundary checks prevent regression.

---

# Workstream 11 — Observability and operations lifecycle

## Goal

Every async job, action run, media processing operation, plugin operation, governance mutation, and data import/export must be visible, traceable, auditable, and recoverable.

## File-by-file tasks

| File / directory                                                                                                              | What to change                                                                                                                                                                                 |
| ----------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/operations/OperationRecord.java`                   | Ensure operation record supports operation type, resource type/id, status, trace ID, request ID, correlation ID, actor, tenant, retryability, failure reason, policy decision, audit event ID. |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/operations/OperationRecorder.java`                 | Make operation recording mandatory for production-like profiles.                                                                                                                               |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/operations/InMemoryOperationRecorder.java`         | Restrict to local/test profiles.                                                                                                                                                               |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/OperationsJobHandler.java`  | Back the operations job center from canonical operation records. Do not use feature-local job lists as the only source.                                                                        |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AlertingHandler.java`       | Connect alert remediation actions to operation records and action-run lifecycle.                                                                                                               |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/application/observability/TraceExportService.java` | Ensure all HTTP/action/media/plugin/governance operations can export trace events or explicitly report trace-disabled state in runtime truth.                                                  |
| `products/data-cloud/planes/action/observability/src/main/java/**`                                                            | Add action-specific trace spans for pattern eval, agent plan, review, execute, retry, rollback, recommendation.                                                                                |
| `products/data-cloud/delivery/ui/src/pages/OperationsJobCenterPage.tsx`                                                       | Show canonical operation timeline with filters by type/status/resource/tenant/correlation.                                                                                                     |
| `products/data-cloud/delivery/ui/src/pages/OperationsConsolePage.tsx`                                                         | Show dependency probes, degraded surfaces, failed jobs, retryable jobs, audit posture, trace posture.                                                                                          |
| `monitoring/prometheus/rules/data-cloud.yml`                                                                                  | Add alerts based on canonical operation failure, media queue backlog, action run failures, event tail errors, audit sink unavailable, policy engine unavailable.                               |
| `monitoring/grafana/dashboards/data-cloud-platform.json`                                                                      | Align panels to canonical operation/runtime truth fields.                                                                                                                                      |
| `monitoring/grafana/dashboards/aep-data-cloud-boundary.json`                                                                  | Show Action Plane/AEP boundary health: EventCloud bridge, pattern compiler, agent runtime, learning recommendations, replay queue.                                                             |

## Acceptance criteria

* Operators can trace every important operation.
* UI and dashboards reflect the same runtime truth and operation records.
* No silent audit/trace/operation skipping in production-like profiles.

---

# Workstream 12 — OpenAPI, generated clients, and DTO cleanup

## Goal

Make backend contracts the source of frontend types. No ad hoc DTOs, duplicated schemas, or UI-only response assumptions.

The UI architecture explicitly requires generated/validated clients from OpenAPI and says UI services must not use ad hoc response types. 

## File-by-file tasks

| File / directory                                                                                      | What to change                                                                                                                       |
| ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `products/data-cloud/contracts/openapi/data-cloud.yaml`                                               | Align all implemented routes with actual router registrations. Add missing media job/transcript/frame-index/retry/consent endpoints. |
| `products/data-cloud/contracts/openapi/action-plane.yaml`                                             | Own Action Plane/AEP execution, pattern, learning, agent, replay, approval schemas.                                                  |
| `products/data-cloud/contracts/openapi/aep.yaml`                                                      | Keep compatibility only; do not add new canonical semantics here.                                                                    |
| `products/data-cloud/delivery/ui/src/contracts/schemas.ts`                                            | Ensure generated types are complete and consumed by services.                                                                        |
| `products/data-cloud/delivery/ui/src/api/*.service.ts`                                                | Remove hand-written response types that duplicate OpenAPI. Use generated types.                                                      |
| `products/data-cloud/delivery/ui/src/features/**/types.ts`                                            | Keep only UI view models. Do not duplicate API DTOs.                                                                                 |
| `products/data-cloud/delivery/ui/src/__tests__/api/**`                                                | Update tests to assert generated client shapes, not locally invented mocks.                                                          |
| `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/dto/**`                     | Remove DTOs that duplicate OpenAPI schema inconsistently. If Java DTOs remain, they must be contract-aligned and tested.             |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/**` | Return canonical DTO/envelope shapes consistently. Do not hand-build inconsistent maps where typed DTOs exist.                       |

## Acceptance criteria

* UI compiles only against generated/validated API types.
* Router, OpenAPI, backend DTOs, and UI services agree.
* No duplicate DTO definitions for the same concept.

---

# Workstream 13 — Data quality, lineage, catalog, and governance product completeness

## Goal

Make Data Cloud feel like a governed data product, not just entity/event CRUD.

## File-by-file tasks

| File / directory                                                                                                                | What to change                                                                                                                                  |
| ------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/planes/data/entity/src/main/java/**`                                                                       | Add canonical collection metadata: owner, schema, quality profile, retention policy, lineage state, freshness, classification, allowed actions. |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java`       | Ensure collection list/detail returns enough metadata for Data UI to avoid extra fake cards.                                                    |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityValidationHandler.java` | Connect validation to data quality score and governance policy.                                                                                 |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/LineageHandler.java`          | Ensure lineage is generated from real events/imports/transforms/media/action runs, not isolated plugin data.                                    |
| `products/data-cloud/plugins/lineage/**`                                                                                        | Make lineage plugin consume canonical events and operation records.                                                                             |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataProductHandler.java`      | Connect data products to collection metadata, schema compatibility, SLA, governance, and runtime truth.                                         |
| `products/data-cloud/delivery/ui/src/pages/DataPage.tsx`                                                                        | Show collection health, schema, records, quality, lineage, policy, recent operations in one low-cognitive-load workspace.                       |
| `products/data-cloud/delivery/ui/src/pages/DataQualityTrustPage.tsx`                                                            | Use backend data quality trust scores and link to source collection/lineage/policy failures.                                                    |
| `products/data-cloud/delivery/ui/src/pages/TenantGovernancePage.tsx`                                                            | Show tenant-level governance posture from canonical governance APIs.                                                                            |
| `products/data-cloud/contracts/openapi/data-cloud.yaml`                                                                         | Ensure collection metadata, quality, lineage, data products, and governance schemas are linked.                                                 |

## Acceptance criteria

* Data tab is the primary product workspace.
* Users can understand data health, lineage, governance, and actions without navigating many disconnected pages.

---

# Workstream 14 — Query, analytics, reports, and AI assist correctness

## Goal

Make query/analytics/reporting useful and governed, while keeping AI assist optional, safe, and non-noisy.

## File-by-file tasks

| File / directory                                                                                                              | What to change                                                                                                                |
| ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AnalyticsHandler.java`      | Ensure queries include tenant, policy, redaction, cost estimate, execution plan, timeout, cancellation, and operation record. |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/analytics/AnalyticsQueryEngine.java`               | Add consistent execution state and failure reason.                                                                            |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/analytics/report/ReportService.java`               | Make reports runtime-driven and contract-backed. No static/demo report values.                                                |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/FederatedQueryHandler.java` | Ensure federated query is disabled/degraded through runtime truth when Trino/analytics dependency is absent.                  |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/StorageCostHandler.java`    | Connect estimates to actual storage profiles and query plan.                                                                  |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AiAssistHandler.java`       | Gate AI assist by policy, privacy, LLM availability, and runtime truth. No ungoverned AI suggestions.                         |
| `products/data-cloud/planes/intelligence/analytics/src/main/java/**`                                                          | Own analytics domain logic. Do not keep analytics behavior in HTTP handlers.                                                  |
| `products/data-cloud/planes/intelligence/feature-ingest/src/main/java/**`                                                     | Clarify preview status and runtime truth dependency.                                                                          |
| `products/data-cloud/delivery/ui/src/pages/SqlWorkspacePage.tsx`                                                              | Show query plan, cost, policy/redaction status, result state, cancellation, and saved queries.                                |
| `products/data-cloud/delivery/ui/src/pages/InsightsPage.tsx`                                                                  | Keep insights outcome-based. Do not show raw AI/ML implementation concepts by default.                                        |
| `products/data-cloud/delivery/ui/src/components/ai/AiAssistant.tsx`                                                           | Make AI assistant contextual, safe, and explainable. It should not be a generic chatbot over product internals.               |

## Acceptance criteria

* Query/report/AI outputs are policy-aware, traceable, and useful.
* AI assist degrades gracefully and never implies unavailable capabilities are live.

---

# Workstream 15 — Build/module classification and non-release implementation verification

## Goal

Keep implementation verification targeted and deterministic, without release-readiness/evidence execution.

## File-by-file tasks

| File                                           | What to change                                                                                                                                                                                                                                  |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `scripts/list-data-cloud-active-modules.mjs`   | Keep implementation-critical classification. Remove or further quarantine legacy naming such as `releaseBlockingModules` if it causes confusion. The script already says it is not an instruction to run release-readiness/evidence workflows.  |
| `products/data-cloud/README.md`                | Keep the explicit instruction that this iteration excludes release readiness/evidence execution.                                                                                                                                                |
| `products/data-cloud/delivery/ui/package.json` | Do not use `test:readiness` in this iteration. Keep targeted scripts only for changed areas. Existing package has a `test:readiness` script; do not invoke it for this work.                                                                    |
| `.github/workflows/data-cloud-ci.yml`          | Do not modify to add release evidence. Only adjust if needed for compile/test of implementation-critical modules.                                                                                                                               |
| `.github/workflows/data-cloud-release.yml`     | Do not touch in this iteration unless removing accidental coupling to implementation checks.                                                                                                                                                    |
| `.gitea/workflows/data-cloud-release.yml`      | Same: no release workflow work.                                                                                                                                                                                                                 |
| `scripts/check-data-cloud-*.mjs`               | Do not add new release/evidence scripts. Add only implementation boundary/contract checks if needed.                                                                                                                                            |
| `.kernel/evidence/**`                          | Do not update, generate, or rely on evidence artifacts in this iteration.                                                                                                                                                                       |
| `products/data-cloud/TEST_MANUAL.md`           | Update only if needed to describe focused implementation verification commands; explicitly exclude readiness checks.                                                                                                                            |
| `products/data-cloud/FOCUSED_TESTS_REPORT.md`  | Do not treat as source of truth for current implementation unless regenerated later in a separate evidence phase.                                                                                                                               |

## Acceptance criteria

* Implementation pass can run targeted compile/unit/integration/UI tests.
* No release-readiness or evidence-generation work is mixed into this iteration.

---

# Workstream 16 — Minimal targeted tests to prevent regression

## Goal

Add tests only where they verify changed implementation root causes. Do not create release evidence.

## File-by-file tasks

| File / directory                                                                                | What to add/update                                                                                 |
| ----------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `products/data-cloud/delivery/ui/src/__tests__/routes/runtimeTruthNavigation.test.tsx`          | Assert nav comes from backend surfaces and respects status/audience/discoverability.               |
| `products/data-cloud/delivery/ui/src/__tests__/routes/RuntimeCapabilityRouteGate.test.tsx`      | Assert LIVE/DEGRADED/PREVIEW/MISCONFIGURED/UNAVAILABLE behavior.                                   |
| `products/data-cloud/delivery/ui/src/__tests__/routes/RoleProtectedRoute.test.tsx`              | Assert shell role does not duplicate runtime preview logic.                                        |
| `products/data-cloud/delivery/ui/src/__tests__/pages/DisabledSurfacePage.test.tsx`              | Assert dependency probes, owner plane, limitations, and next action render.                        |
| `products/data-cloud/delivery/ui/src/__tests__/api/surfaces.service.test.ts`                    | Assert enriched surface schema maps correctly.                                                     |
| `products/data-cloud/delivery/ui/src/__tests__/pages/MediaArtifactPage.test.tsx`                | Assert media lifecycle states, consent, retention, retry, transcript/frame-index display.          |
| `products/data-cloud/delivery/ui/src/__tests__/i18n/noRawText.test.tsx`                         | Add focused raw visible text guard for touched Data Cloud UI files.                                |
| `products/data-cloud/delivery/launcher/src/test/java/**/SurfaceRegistryHandlerTest.java`        | Assert typed enriched `/api/v1/surfaces` response and tenant/auth requirements.                    |
| `products/data-cloud/delivery/launcher/src/test/java/**/WorkflowExecutionHandlerTest.java`      | Assert idempotency, missing execution capability, trace/correlation, operation recording.          |
| `products/data-cloud/delivery/runtime-composition/src/test/java/**/DataCloudConfigTest.java`    | Assert `allowInvalidLocalEventsForTests` is preserved.                                             |
| `products/data-cloud/delivery/runtime-composition/src/test/java/**/DataCloudEventTailTest.java` | Assert async tail subscription lifecycle, cancellation, failure behavior.                          |
| `products/data-cloud/delivery/api/src/test/java/**/MediaArtifactControllerTest.java`            | After refactor, assert controller delegates to service and enforces canonical policy path.         |
| `products/data-cloud/delivery/api/src/test/java/**/MediaArtifactServiceTest.java`               | Assert media create/consent/process/retry/delete lifecycle.                                        |
| `products/data-cloud/planes/action/**/src/test/java/**`                                         | Add PatternSpec compiler, pattern lifecycle, learning recommendation, replay-safe execution tests. |
| `products/data-cloud/planes/action/event-bridge/src/test/java/**`                               | Assert EventCloud SPI uses public Data Cloud Event Plane contracts only.                           |
| `products/data-cloud/planes/action/agent-runtime/src/test/java/**`                              | Assert agent side-effect policy, approval, replay, retry, rollback.                                |
| `scripts/__tests__/check-action-plane-boundaries.test.mjs`                                      | Extend to catch new boundary rules.                                                                |
| `scripts/__tests__/check-dependency-boundaries.test.mjs`                                        | Add Data Cloud plane/shared-library duplication/boundary cases.                                    |

## Acceptance criteria

* Tests validate root-cause fixes.
* No broad release-readiness scripts.
* No evidence-generation tasks.

---

# Priority order for the next implementation iteration

## P0 — Must do first to leapfrog

1. Runtime truth contract consolidation.
2. UI route/nav/gate consolidation.
3. Security/policy/audit enforcement unification.
4. Action Plane executable lifecycle foundation.
5. Media/audio-video durable lifecycle foundation.
6. Core runtime bug fixes: `DataCloudConfig` flag and async event tail.

## P1 — Do immediately after P0

1. Context Plane promote-or-disable decision.
2. Plugin lifecycle consolidation.
3. Agent catalog/runtime/memory separation.
4. OpenAPI/generated-client cleanup.
5. Shared-library boundary cleanup.

## P2 — Hardening

1. Data quality/lineage/governance product completeness.
2. Query/analytics/reports/AI assist correctness.
3. Operations/observability dashboards and alerts.
4. UI visual/interaction consistency.
5. Focused deterministic tests.

---

# “Do not do” list for next iteration

Do **not**:

* Add new top-level product pages without runtime truth.
* Add more demo cards or static metrics.
* Add another route registry.
* Add another permission map.
* Add another plugin manager.
* Add another media job model.
* Add another Action/AEP run model.
* Add another generated-client bypass.
* Add local UI DTOs duplicating OpenAPI.
* Add compatibility endpoints unless current callers prove they need them.
* Run release-readiness checks.
* Generate release evidence.
* Touch `.kernel/evidence/**` as part of this implementation pass.
* Treat docs or evidence artifacts as proof that features work.
* Patch symptoms in UI while backend capability truth remains fragmented.

---

# The shortest path to leapfrogging the score

The next pass should be implemented in this order:

1. **Make `/api/v1/surfaces` the product truth.**
2. **Make UI consume that truth everywhere.**
3. **Make security/policy/audit/idempotency mandatory by profile.**
4. **Make Action Plane executable through one lifecycle.**
5. **Make media/audio-video durable through one lifecycle.**
6. **Delete/merge duplicate registries, permissions, DTOs, route metadata, and lifecycle logic.**
7. **Add focused tests only for those changed root causes.**

That is the path from ~2.6 to the high 3s. More pages, more scripts, or more evidence will not move the score meaningfully until these root causes are fixed.
