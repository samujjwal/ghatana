Below is the **file-by-file task backlog** for `samujjwal/ghatana` at commit `9058b7747f6056cbb9800376801c1946466d7529`.

Scope rule: **do not add release-evidence tasks, do not generate release evidence, and do not run release-readiness/readiness checks in this iteration.** This list focuses only on implementation, correctness, feature completeness, UI/UX, security/privacy, i18n/a11y, observability, and targeted tests.

---

# A. Product truth, module boundaries, and canonical architecture

## A1. `products/data-cloud/README.md`

**What to change**

* Keep the core statement that Data Cloud is one product and Action Plane is integrated inside Data Cloud.
* Clarify that this iteration is **not** about release-readiness execution.
* Remove or quarantine implementation-plan language that instructs engineers to regenerate evidence or execute release-readiness checks during normal hardening.
* Add a short “Current implementation truth” section:

  * Data Plane: active.
  * Event Plane: active.
  * Intelligence Plane: active.
  * Governance Plane: active.
  * Action Plane: active but still has AEP naming/test-boundary debt.
  * Context Plane: target plane, but not currently present as an active Gradle module unless added in this pass.
  * Audio-video: external shared service with partial Data Cloud metadata integration, not yet full first-class modality.

**Where**

* Update around current canonical description and module map lines that describe Data Cloud and Action Plane. The README already defines Data Cloud as a governed operational data fabric and says Action Plane formerly AEP is integrated under `products/data-cloud/planes/action`.
* Update the “Module Classification and Promotion” area to say **skip release-readiness/evidence execution for this hardening iteration**.

**Tests**

* No release-readiness check.
* Add/update documentation consistency test only if an existing doc-lint test already exists and can be run in isolation.

---

## A2. `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`

**What to change**

* Update “Current-To-Target Module Map” so it matches actual generated Gradle includes.
* Add explicit status for `planes/context`:

  * Either create it as an active module, or mark it as target-only and prevent UI/runtime from treating it as fully available.
* Remove remaining ambiguity between “AEP product” and “Action Plane inside Data Cloud.”
* Keep dependency rules:

  * Data/Event/Context/Governance/Intelligence must not depend on Action internals.
  * Action may depend on public SPI/contracts only.
  * Delivery composition may wire planes together.

**Where**

* Plane model and target layout sections.
* Shared platform review rules and migration sequence.

**Tests**

* Add/update architecture boundary test entries only.
* Do **not** run production-readiness or evidence checks.

---

## A3. `config/canonical-product-registry.json`

**What to change**

* If Context Plane is implemented now, add the canonical Data Cloud module entry for:

  * `:products:data-cloud:planes:context`
* If not implementing Context Plane now, add product-shape metadata that marks Context Plane as `planned` or `target-only`, not `active`.
* Ensure audio-video module paths match actual modules:

  * `products:audio-video:modules:speech:stt-service`
  * `products:audio-video:modules:speech:tts-service`
  * `products:audio-video:modules:vision:vision-service`
  * `products:audio-video:modules:intelligence:multimodal-service`
  * infrastructure modules currently present in generated settings.

**Where**

* Product registry source that generates `config/generated/settings-gradle-includes.kts`, which currently includes Data Cloud and audio-video modules.

**Tests**

* Run only the product-registry generation test or snapshot test if available.
* Do **not** run release-readiness checks.

---

## A4. `config/generated/settings-gradle-includes.kts`

**What to change**

* Do not hand-edit if generated.
* Regenerate only from `config/canonical-product-registry.json`.
* Expected result:

  * Add `:products:data-cloud:planes:context` only if Context Plane is implemented.
  * Keep Action Plane modules under Data Cloud.
  * Keep audio-video module list consistent with actual module docs and build files.

**Where**

* Data Cloud section currently includes Data/Event/Operations/Intelligence/Governance/Action modules but no Context Plane module.
* Audio-video section includes active modules that differ from docs claiming `apps/*`, `core/*`, and `infrastructure/*` paths.

**Tests**

* Targeted generation drift test only.
* Do **not** run readiness checks.

---

## A5. `scripts/list-data-cloud-active-modules.mjs`

**What to change**

* Add Context Plane module classification only if the module is implemented.
* Keep Action Plane modules classified as release-blocking in the script, but do not add any task requiring release-readiness execution in this iteration.
* Remove misleading advisory/release wording from comments if it causes developers to execute release checks during hardening.
* Add validation for “target-only plane must not appear in active modules.”

**Where**

* `releaseBlockingModules` and `actionPlaneModules` sets.

**Tests**

* Add unit test for:

  * active Data Cloud module classification,
  * missing Context Plane behavior,
  * invalid unclassified Data Cloud module.
* No release-readiness run.

---

# B. Action Plane / AEP naming, test exclusions, and boundary cleanup

## B1. `products/data-cloud/planes/action/server/build.gradle.kts`

**What to change**

* Replace module comments saying “canonical server surface for the AEP product” with “Data Cloud Action Plane server surface.”
* Keep compatibility OpenAPI sync if still needed, but ensure comments say:

  * `action-plane.yaml` is canonical.
  * `aep.yaml` is compatibility-only until retired.
* Do not add release-readiness execution.
* Keep `verifyOpenApiSync` as a contract sync task, not a readiness task.

**Where**

* Top module comment currently says AEP server/product.
* OpenAPI sync section currently says canonical source is `products/data-cloud/contracts/openapi/aep.yaml`; update wording to prefer `action-plane.yaml` where possible.

**Tests**

* Existing server unit/contract tests only.
* Do not run release-readiness.

---

## B2. `products/data-cloud/planes/action/engine/build.gradle.kts`

**What to change**

* Replace “Core AEP execution engine” wording with “Data Cloud Action Plane execution engine.”
* Review dependencies on broad platform modules:

  * `platform:contracts`
  * `platform:java:agent-core`
  * `platform:java:messaging`
* Ensure engine depends only on public contracts/SPI and not delivery/runtime implementation internals.
* Add or update ArchUnit boundary test if missing.

**Where**

* Top comment and dependency block.

**Tests**

* Targeted engine boundary test.
* Existing engine unit tests.
* No readiness checks.

---

## B3. `products/data-cloud/planes/action/orchestrator/build.gradle.kts`

**What to change**

* Remove or fix legacy test exclusions:

  * `AepDiModulesTest.java`
  * `AgentMemoryPlaneClientMasteryTest.java`
* If old tests are invalid, replace them with current-boundary tests instead of excluding them.
* Remove duplicate `testImplementation(libs.bundles.testing.core)` entry.
* Confirm orchestrator dependencies use Action Plane contracts/SPI and do not leak Data Plane internals beyond intended public APIs.

**Where**

* Test exclusions section.
* Dependency block.

**Tests**

* Add/update:

  * orchestrator lifecycle test,
  * agent dispatch wiring test,
  * pipeline execution queue test,
  * Data Cloud public SPI dependency boundary test.

---

## B4. `products/data-cloud/planes/action/agent-runtime/build.gradle.kts`

**What to change**

* Replace “AEP Agent Runtime” wording with “Data Cloud Action Plane Agent Runtime.”
* Remove temporary test exclusions or replace excluded tests with current implementations:

  * `RegistryAndFactoryTest.java`
  * `GovernedMemoryPlaneMasteryTest.java`
  * `GaaMasteryLifecycleE2ETest.java`
  * `GovernedAgentDispatcherMasteryTest.java`
  * `AgentExecutionSecurityIntegrationTest.java`
  * `AgentPackageLoaderTest.java`
  * `MemoryWritePolicyMasteryTest.java`
  * `StructuredContextInjectorMasteryTest.java`
* Keep only justified exclusions with issue and removal date if absolutely unavoidable, but target should be zero exclusions for production readiness.

**Where**

* Top module description and `tasks.compileTestJava` exclusion block.

**Tests**

* Re-enable or recreate tests in the same module under:

  * `products/data-cloud/planes/action/agent-runtime/src/test/java/**`
* Add focused tests for:

  * governed dispatcher,
  * memory write policy,
  * structured context injection,
  * agent package loading,
  * security integration.

---

## B5. `products/data-cloud/contracts/openapi/action-plane.yaml`

**What to change**

* Ensure canonical Action Plane routes use `/api/v1/action/*`.
* Ensure pipeline, agent, memory, autonomy, plugin, learning, review, run/execution, and pattern routes are explicitly represented.
* Remove root-level legacy paths from the canonical Action Plane spec.
* Keep backward compatibility only in compatibility registry or `aep.yaml`.

**Where**

* Action contract paths and schemas.

**Tests**

* OpenAPI route sync test only.
* Do not run readiness checks.

---

## B6. `products/data-cloud/contracts/openapi/aep.yaml`

**What to change**

* Mark as compatibility contract only.
* Keep equivalent to Action Plane only where compatibility is still required.
* Do not introduce new canonical routes here.
* Add deprecation note if missing.

**Where**

* Contract header and route comments.

**Tests**

* Contract equivalence test with `action-plane.yaml`, not readiness.

---

## B7. `products/data-cloud/contracts/openapi/route-compatibility-registry.yaml`

**What to change**

* Move all legacy root Action/AEP paths here.
* Ensure compatibility aliases are feature-flagged or explicitly deprecated.
* Add removal criteria for aliases, but no release-readiness task.

**Tests**

* Router compatibility test only.

---

# C. Media/audio-video: make it real, secure, and reachable

## C1. `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java`

**What to change**

* Replace direct `X-Tenant-ID` tenant extraction with canonical `RequestContextResolver` / `HttpHandlerSupport`.
* Do not accept tenant identity from raw headers in production.
* Fix nested route dispatch:

  * Current code extracts everything after `/api/v1/media/artifacts/` as `artifactId`, then rejects values containing `/`, so `/transcribe` and `/analyze` are unreachable.
* Parse subroutes before validating artifact ID.
* Add handlers for:

  * `POST /api/v1/media/artifacts/:artifactId/transcribe`
  * `POST /api/v1/media/artifacts/:artifactId/analyze`
  * `GET /api/v1/media/artifacts/:artifactId/jobs/:jobId`
  * `GET /api/v1/media/artifacts/:artifactId/results`
* Replace synthetic job IDs with persisted job records.
* Enforce role/permission checks:

  * create artifact,
  * view artifact,
  * delete artifact,
  * trigger processing,
  * read result.
* Redact `storageUri`, metadata, consent, and retention fields unless caller has required permission.
* Add audit event for create/delete/transcribe/analyze/result read.

**Where**

* Tenant extraction block.
* Nested route handling block.
* `triggerTranscription` and `triggerVisionAnalysis`.
* `toResponse`.

**Tests**

* `MediaArtifactControllerTenantSecurityTest`
* `MediaArtifactControllerRouteDispatchTest`
* `MediaArtifactControllerRedactionTest`
* `MediaArtifactControllerProcessingJobTest`

---

## C2. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`

**What to change**

* Register explicit media processing routes:

  * `POST /api/v1/media/artifacts/:artifactId/transcribe`
  * `POST /api/v1/media/artifacts/:artifactId/analyze`
  * `GET /api/v1/media/artifacts/:artifactId/jobs/:jobId`
  * `GET /api/v1/media/artifacts/:artifactId/results`
* Do not rely on the generic `mediaArtifactController::handle` route for nested subresources.
* Make route group count dynamic or accurate; current `log.info("... {}", 29)` is hardcoded.

**Where**

* `withMediaArtifactRoutes`.
* `build()` logging.

**Tests**

* `DataCloudRouterBuilderMediaRoutesTest`
* OpenAPI-router sync test update.

---

## C3. `products/data-cloud/contracts/openapi/data-cloud.yaml`

**What to change**

* Add/align media artifact contracts:

  * `MediaArtifact`
  * `CreateMediaArtifactRequest`
  * `MediaProcessingJob`
  * `MediaProcessingResult`
  * `MediaArtifactListResponse`
* Add media paths:

  * `POST /api/v1/media/artifacts`
  * `GET /api/v1/media/artifacts`
  * `GET /api/v1/media/artifacts/{artifactId}`
  * `DELETE /api/v1/media/artifacts/{artifactId}`
  * `POST /api/v1/media/artifacts/{artifactId}/transcribe`
  * `POST /api/v1/media/artifacts/{artifactId}/analyze`
  * `GET /api/v1/media/artifacts/{artifactId}/jobs/{jobId}`
  * `GET /api/v1/media/artifacts/{artifactId}/results`
* Add security metadata:

  * read media,
  * manage media,
  * process media,
  * read media result.
* Mark `storageUri` as sensitive/redactable.

**Where**

* Add under existing path/schema sections; OpenAPI currently documents broad product surfaces and security requirements.

**Tests**

* Data Cloud OpenAPI-router sync test.
* Media API contract test.

---

## C4. `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactRecord.java`

**What to change**

* Add fields:

  * `status`
  * `processingState`
  * `retentionUntil`
  * `consentStatus`
  * `redactionPolicy`
  * `createdBy`
  * `updatedAt`
* Keep tenant ID immutable.
* Add helper for safe/redacted response projection.

**Tests**

* Record serialization test.
* Redaction projection test.

---

## C5. `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactRepository.java`

**What to change**

* Add methods:

  * `findJobsByArtifact(String artifactId, String tenantId)`
  * `findResultsByArtifact(String artifactId, String tenantId)`
  * `saveJob(MediaProcessingJobRecord job)`
  * `updateJobStatus(...)`
  * `saveResult(MediaProcessingResultRecord result)`
* Ensure all methods require tenant ID.
* Ensure delete either soft-deletes or emits retention-aware deletion event.

**Tests**

* Repository tenant isolation test.
* Job lifecycle persistence test.

---

## C6. `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaArtifactEventEmitter.java`

**What to change**

* Emit typed events for:

  * `media.artifact.created`
  * `media.artifact.deleted`
  * `media.transcription.requested`
  * `media.vision-analysis.requested`
  * `media.processing.completed`
  * `media.processing.failed`
* Include:

  * tenant ID,
  * artifact ID,
  * job ID,
  * correlation ID,
  * request ID,
  * agent ID,
  * media type,
  * consent status,
  * retention metadata.
* Do not include raw storage URI in event payload unless encrypted/redacted.

**Tests**

* Event payload shape test.
* Sensitive-field redaction test.

---

## C7. Add `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaProcessingJobRecord.java`

**What to add**

* Immutable record for:

  * `jobId`
  * `tenantId`
  * `artifactId`
  * `jobType`
  * `status`
  * `requestedBy`
  * `requestedAt`
  * `startedAt`
  * `completedAt`
  * `errorCode`
  * `errorMessage`
  * `correlationId`
  * `requestId`
  * `resultRef`

**Tests**

* Serialization and state transition test.

---

## C8. Add `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/memory/media/MediaProcessingResultRecord.java`

**What to add**

* Immutable result record for:

  * transcription result,
  * vision labels,
  * confidence,
  * model/provider,
  * processing metadata,
  * redaction marker,
  * created timestamp.

**Tests**

* Serialization and redaction test.

---

## C9. `products/data-cloud/planes/action/agent-catalog/capabilities/audio-video-capabilities.yaml`

**What to change**

* Align capabilities with actual Data Cloud media routes and audio-video service routes.
* Add capability entries for:

  * register media artifact,
  * request transcription,
  * request vision analysis,
  * read media processing status,
  * read media processing result.
* Add permission requirements and tenant-scope metadata.
* Mark unsupported modalities as unavailable if not implemented.

**Where**

* File identified in search results as existing audio-video capability catalog.

**Tests**

* Agent catalog capability validation test.

---

## C10. `products/audio-video/docs/capability-map.md`

**What to change**

* Correct module paths to match actual generated settings.
* Do not claim `apps/*`, `core/*`, or `infrastructure/*` modules exist if they do not.
* Replace “all capabilities implemented” with precise state:

  * STT service module present,
  * TTS service module present,
  * Vision service module present,
  * Multimodal service module present,
  * Data Cloud integration partial until media job lifecycle is wired.
* Remove evidence-generation wording from this iteration.

**Where**

* Current map claims all capabilities are implemented with modules/routes/tests/gates.
* Actual generated module paths differ.

**Tests**

* Documentation/module path consistency test only.

---

## C11. `products/audio-video/modules/speech/stt-service/build.gradle.kts`

**What to change**

* Ensure main class `com.ghatana.stt.grpc.SttGrpcServer` exists and exposes a service contract compatible with Data Cloud media job requests.
* Add Data Cloud event consumer or bridge client dependency only through public contract/SPI, not direct launcher internals.
* Add integration test for Data Cloud `media.transcription.requested` event to STT job handling.

**Where**

* Current build file defines `SttGrpcServer` as main class and has a smoke test for it.

**Tests**

* Existing `smokeTestMainClass`.
* Add targeted STT event bridge test.
* No readiness checks.

---

## C12. `products/audio-video/modules/speech/tts-service/build.gradle.kts`

**What to change**

* Align with same security/observability/contract conventions as STT.
* Ensure TTS is not falsely exposed through Data Cloud media artifact flows unless a Data Cloud route exists.
* Add integration contract for future TTS if in scope.

**Tests**

* Main-class smoke test.
* Contract test.

---

## C13. `products/audio-video/modules/vision/vision-service/build.gradle.kts`

**What to change**

* Ensure vision service accepts Data Cloud media artifact processing jobs through public bridge/SPI.
* Add object-detection/analysis status response contract.
* Add result schema compatible with Data Cloud `MediaProcessingResultRecord`.

**Tests**

* Vision processing integration test.
* Result contract test.

---

## C14. `products/audio-video/modules/intelligence/multimodal-service/build.gradle.kts`

**What to change**

* Align multimodal job input/output with Data Cloud media artifact and Action Plane agent capabilities.
* Add tenant/correlation/request ID propagation.
* Ensure no raw media content is logged.

**Tests**

* Multimodal job lifecycle test.
* Observability propagation test.

---

# D. Security, authorization, tenant isolation, and audit

## D1. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/security/RequestContextResolver.java`

**What to change**

* Keep production behavior rejecting `X-Tenant-ID` and `tenantId` query parameters. This is already directionally correct.
* Add explicit support for permission/scope extraction if not already present in `Principal`.
* Add helper result for:

  * authenticated user,
  * tenant,
  * workspace,
  * project,
  * roles,
  * permissions,
  * support access,
  * correlation ID,
  * trace ID.
* Validate workspace/project IDs using canonical patterns, not only length.
* Emit structured audit event for rejected tenant spoofing attempts.

**Tests**

* Existing tenant resolution tests.
* Add:

  * production rejects `X-Tenant-ID`,
  * production rejects `tenantId`,
  * local allows fallback,
  * support access requires proper role,
  * workspace/project ID validation.

---

## D2. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java`

**What to change**

* Add a common method:

  * `requireRequestContext(HttpRequest request)`
  * `requirePermission(HttpRequest request, String permission)`
  * `requireAnyPermission(HttpRequest request, Set<String> permissions)`
* Deprecate direct tenant-only helpers in new handlers.
* Prevent new production handlers from using `resolvePrincipalId` header values for authorization.
* Add response helpers for:

  * unauthorized,
  * forbidden,
  * policy denied,
  * degraded/unavailable surface,
  * redacted response.
* Keep request/correlation ID propagation.

**Where**

* Existing helper already resolves correlation ID, trace context, tenant context, and error responses.

**Tests**

* `HttpHandlerSupportAuthorizationTest`
* `HttpHandlerSupportErrorEnvelopeTest`
* `HttpHandlerSupportRedactionTest`

---

## D3. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java`

**What to change**

* Ensure all governance mutation handlers call centralized request context and permission helpers.
* Add policy decision result to responses for simulation/toggle/delete/purge/redact operations.
* Add audit event for:

  * policy create/update/delete/toggle,
  * retention classify,
  * purge,
  * redaction.

**Where**

* Router registers governance routes for retention, purge, redaction, policy CRUD, and simulation.

**Tests**

* Governance permission matrix test.
* Governance audit emission test.
* Policy simulation no-mutation test.

---

## D4. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SettingsHandler.java`

**What to change**

* Ensure API key create/rotate/revoke and security settings changes require admin permission.
* Require approval workflow for sensitive settings changes.
* Redact API key material in list/get responses.
* Add audit event for all sensitive changes.

**Where**

* Settings routes include API keys, security settings, profile/preferences, notification preferences, and approval workflow.

**Tests**

* Settings permission test.
* API key redaction test.
* Approval workflow test.

---

## D5. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandler.java`

**What to change**

* Ensure connector credentials are never returned.
* Require permissions for:

  * register connector,
  * update connector,
  * delete connector,
  * rotate credentials,
  * trigger sync,
  * read schema/health.
* Add audit event for connector lifecycle operations.
* Ensure handler returns canonical response shape used by generated UI contracts.

**Where**

* Router exposes connector lifecycle routes under `/api/v1/connectors` and compatibility `/data-fabric/connectors`.

**Tests**

* `DataSourceRegistryHandlerSecurityTest`
* `ConnectorLifecycleTest`
* `ConnectorCredentialRedactionTest`

---

## D6. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AiAssistHandler.java`

**What to change**

* Make production-mode fail-closed wiring mandatory when deployment profile is production/staging/sovereign.
* Require `TenantQuotaService` in production if AI routes are enabled.
* Require `IdempotencyStore` in production for mutating AI operations.
* Require `DataCloudClient` or audit service for AI action persistence in production.
* Do not allow heuristic fallback in production when `CompletionService` is missing.
* Confirm every AI route records audit/action telemetry.
* Ensure prompts never include raw PII fields; enforce schema/metadata-only policy.

**Where**

* Existing fail-closed production support and optional quota/idempotency/client wiring.
* Example route logic for entity suggest and schema infer.

**Tests**

* `AiAssistHandlerProductionFailClosedTest`
* `AiAssistHandlerQuotaRequiredTest`
* `AiAssistHandlerIdempotencyTest`
* `AiAssistHandlerAuditPersistenceTest`
* `AiAssistHandlerPiiPromptPolicyTest`

---

# E. Backend API and route correctness

## E1. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`

**What to change**

* Keep canonical Action routes under `/api/v1/action/*`.
* Ensure root legacy routes are only registered when `LEGACY_ACTION_ROUTES` is enabled.
* Remove canonical duplicate routes that still use root paths:

  * `/api/v1/pipelines/draft`
  * `/api/v1/pipelines/:pipelineId/optimise-hint`
  * any other Action-owned root route.
* Add explicit storage profile routes if UI feature remains:

  * `GET /api/v1/storage-profiles`
  * `POST /api/v1/storage-profiles`
  * `GET /api/v1/storage-profiles/:profileId`
  * `PUT /api/v1/storage-profiles/:profileId`
  * `DELETE /api/v1/storage-profiles/:profileId`
  * `POST /api/v1/storage-profiles/:profileId/set-default`
  * `GET /api/v1/storage-profiles/:profileId/metrics`
* If `/data-fabric/profiles` compatibility aliases remain, mark them as compatibility-only and route them to the same handler.
* Ensure every registered route appears in OpenAPI.

**Where**

* Pipeline/action routes.
* AI assist routes with mixed canonical and root paths.
* Connector compatibility aliases.

**Tests**

* Router/OpenAPI sync.
* Legacy route feature-flag test.
* Storage profile route test.
* Action canonical route test.

---

## E2. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/PipelineCheckpointHandler.java`

**What to change**

* Enforce canonical Action Plane permissions.
* Validate pipeline payload against contract schema, not free-form map.
* Persist created/updated/deleted audit events.
* Return stable errors for invalid DAGs, missing nodes, duplicate node IDs, invalid edges.
* Add optimistic concurrency/version support if pipeline updates can race.

**Where**

* Router maps this handler to Action pipeline CRUD routes.

**Tests**

* Pipeline CRUD contract test.
* Invalid DAG test.
* Permission test.
* Audit test.

---

## E3. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`

**What to change**

* Ensure execute/cancel/retry/rollback/checkpoint/restore are consistent under `/api/v1/action`.
* Ensure execution responses include:

  * request ID,
  * tenant ID,
  * execution ID,
  * status,
  * started/completed timestamps,
  * node statuses,
  * failure reason,
  * retryability.
* Add durable state transition validation.
* Add idempotency for execute/retry/cancel/rollback.

**Where**

* Router registers pipeline and execution routes.
* OpenAPI defines workflow execution response/details/log schemas.

**Tests**

* Workflow execution lifecycle test.
* Execution logs test.
* Cancel/retry/rollback permission test.
* Idempotency test.

---

## E4. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SurfaceRegistryHandler.java`

**What to change**

* Ensure `/api/v1/surfaces` is the only canonical runtime truth endpoint.
* Remove any UI/client dependency on `/api/v1/capabilities`.
* Ensure response includes:

  * owner plane,
  * status,
  * required dependencies,
  * dependency probes,
  * runtime profile,
  * limitations,
  * actions allowed.
* Add media/audio-video surfaces and Context Plane surface truth.
* Hide target-only planes from default navigation unless status is available/degraded with clear reason.

**Where**

* Router exposes `/api/v1/surfaces` and `/api/v1/surfaces/schema`.
* OpenAPI defines `SurfaceRecord`.

**Tests**

* Surface schema test.
* Unavailable/degraded surface test.
* UI route visibility contract test.

---

# F. Context Plane and RAG/product memory ownership

## F1. Add `products/data-cloud/planes/context/build.gradle.kts` if Context Plane is implemented now

**What to add**

* New Java module for:

  * lineage,
  * provenance,
  * semantic context,
  * freshness,
  * memory/RAG grounding,
  * retrieval policies.
* Dependencies allowed:

  * `planes:shared-spi`
  * Data/Event/Governance public contracts only.
* Must not depend on Action implementation internals.

**Tests**

* Context module boundary test.
* Context service unit tests.

---

## F2. Add `products/data-cloud/planes/context/src/main/java/com/ghatana/datacloud/context/ContextRecord.java`

**What to add**

* Canonical context record:

  * tenant ID,
  * collection/entity scope,
  * source,
  * provenance,
  * freshness timestamp,
  * trust score,
  * semantic tags,
  * policy classification.

**Tests**

* Serialization test.
* Validation test.

---

## F3. Add `products/data-cloud/planes/context/src/main/java/com/ghatana/datacloud/context/ContextService.java`

**What to add**

* Public service interface for:

  * get context,
  * put context,
  * delete context,
  * snapshot,
  * collection context,
  * RAG grounding check,
  * lineage/trust lookup.

**Tests**

* Context service contract test.

---

## F4. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/ContextLayerHandler.java`

**What to change**

* Move business logic into Context Plane service if Context Plane is implemented.
* Handler should only:

  * resolve request context,
  * enforce permission,
  * call Context Plane service,
  * map response.
* Add redaction for sensitive context values.

**Where**

* Router exposes context routes.

**Tests**

* Context handler permission test.
* Context redaction test.

---

## F5. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/CollectionContextHandler.java`

**What to change**

* Move lineage/trust business logic into Context Plane service.
* Ensure RAG policy checks are governed by Governance Plane policy service.
* Include provenance and freshness in response.

**Where**

* Router exposes collection context, lineage trust, and RAG policy check routes.

**Tests**

* RAG policy check test.
* Collection lineage/trust test.

---

# G. UI contract, endpoint alignment, and simple product UX

## G1. `products/data-cloud/delivery/ui/ARCHITECTURE.md`

**What to change**

* Replace “current implementation uses ad hoc response types” with the implemented generated-client approach after Group G is complete.
* Keep explicit rule: UI services must use generated/validated clients.
* Add rule that UI must use Runtime Truth `/api/v1/surfaces` for feature availability.
* Add rule that default nav must remain outcome-first:

  * Home,
  * Data,
  * Events,
  * Query,
  * Pipelines,
  * Trust,
  * Operations.
* Add “Do not run readiness checks in this implementation iteration.”

**Where**

* DC-UI-004 note currently states generated API types are missing and current services use ad hoc response types.

**Tests**

* Docs-only consistency test if available.

---

## G2. `products/data-cloud/delivery/ui/package.json`

**What to change**

* Change `"test"` so it does **not** run `test:readiness` by default.
* Add:

  * `"test:focused": "vitest run"`
  * `"test:contracts": "vitest run tests/contract"`
  * keep `"test:readiness"` but do not use it in this iteration.
* Keep `generate:api-types` and `check:api-types`.
* Add script for targeted data-fabric tests if useful:

  * `"test:data-fabric": "vitest run src/features/data-fabric"`

**Where**

* Current `"test"` points to `"pnpm run test:readiness"`.

**Tests**

* No test needed for package script change.
* Do not run `test:readiness`.

---

## G3. `products/data-cloud/delivery/ui/scripts/generate-api-types.ts`

**What to change**

* Generate types from:

  * `products/data-cloud/contracts/openapi/data-cloud.yaml`
  * `products/data-cloud/contracts/openapi/action-plane.yaml`
* Output generated types into a stable generated folder:

  * `products/data-cloud/delivery/ui/src/generated/api/data-cloud.ts`
  * `products/data-cloud/delivery/ui/src/generated/api/action-plane.ts`
* Fail generation on invalid OpenAPI.
* Do not generate release evidence.

**Tests**

* `check-api-types.ts`.
* Targeted type-check only.

---

## G4. `products/data-cloud/delivery/ui/scripts/check-api-types.ts`

**What to change**

* Compare generated UI types against OpenAPI source.
* Fail only on type drift.
* Do not invoke readiness/evidence/maturity scripts.

**Tests**

* Script unit test if present.
* No readiness check.

---

## G5. Add `products/data-cloud/delivery/ui/src/generated/api/data-cloud.ts`

**What to add**

* Generated OpenAPI TypeScript types.
* Mark as generated.
* No manual edits.

**Tests**

* Type-check.

---

## G6. Add `products/data-cloud/delivery/ui/src/generated/api/action-plane.ts`

**What to add**

* Generated Action Plane OpenAPI TypeScript types.
* Mark as generated.
* No manual edits.

**Tests**

* Type-check.

---

## G7. `products/data-cloud/delivery/ui/src/contracts/schemas.ts`

**What to change**

* Replace hand-maintained contract-like types with generated OpenAPI-derived types where possible.
* Keep Zod schemas only for runtime validation at UI boundaries.
* Ensure connector, storage profile, media artifact, surface, pipeline, execution, and governance schemas match backend.

**Where**

* Data-fabric API service imports this file for connector/storage schemas.

**Tests**

* Schema parse tests.
* Generated type compatibility tests.

---

## G8. `products/data-cloud/delivery/ui/src/lib/api/client.ts`

**What to change**

* Add canonical request ID propagation.
* Do not send `X-Tenant-ID` in production mode.
* Handle backend error envelope consistently.
* Handle 401, 403, 404, 409, 429, 503.
* Add response parsing hooks for generated types.
* Add degraded/unavailable surface response handling.

**Tests**

* API client error envelope test.
* Request ID propagation test.
* Production tenant header absence test.

---

## G9. `products/data-cloud/delivery/ui/src/features/data-fabric/services/api.ts`

**What to change**

* Replace `/data-fabric/connectors` calls with `/api/v1/connectors`.
* Replace `/data-fabric/profiles` calls with canonical `/api/v1/storage-profiles` or whatever backend canonical route is implemented.
* Remove manual type mapping that conflicts with backend contracts.
* Fix suspicious mapping:

  * `clickhouse` should not map to `StorageType.DATABRICKS`.
  * `in-memory` should not map to `StorageType.HDFS`.
* Use generated OpenAPI types and Zod validation at boundaries.
* Handle 503 runtime truth response when connectors/storage profiles are disabled.

**Where**

* Current service calls `/data-fabric/profiles` and `/data-fabric/connectors`.
* Current type mapping maps `clickhouse` to `DATABRICKS` and `in-memory` to `HDFS`.

**Tests**

* Data-fabric API service test.
* Endpoint path test.
* Storage type mapping test.
* Runtime unavailable/degraded test.

---

## G10. `products/data-cloud/delivery/ui/src/features/data-fabric/types/index.ts`

**What to change**

* Align enums with backend OpenAPI.
* Remove UI-only status values that backend never returns.
* Add explicit degraded/unavailable states if Runtime Truth can return them.
* Add redaction-aware fields for credentials and storage URI.

**Tests**

* Type compatibility test against generated OpenAPI types.

---

## G11. `products/data-cloud/delivery/ui/src/features/data-fabric/stores/storage-profile.store.ts`

**What to change**

* Add explicit states:

  * idle,
  * loading,
  * loaded,
  * empty,
  * error,
  * unauthorized,
  * unavailable,
  * degraded.
* Remove silent failures.
* Store backend request ID on errors.
* Do not keep secrets in state.

**Tests**

* Store reducer/action tests.

---

## G12. `products/data-cloud/delivery/ui/src/features/data-fabric/stores/connector.store.ts`

**What to change**

* Add same state model as storage profiles.
* Add sync job state:

  * queued,
  * running,
  * succeeded,
  * failed,
  * canceled.
* Store connector health and schema state separately.
* Do not store credentials after submit.

**Tests**

* Connector store tests.
* Sync lifecycle state tests.

---

## G13. `products/data-cloud/delivery/ui/src/features/data-fabric/components/StorageProfilesPage.tsx`

**What to change**

* Use common page layout.
* Use generated contract-backed service.
* Add empty/loading/error/unauthorized/unavailable states.
* Add progressive disclosure for advanced storage settings.
* Make destructive delete require confirmation.
* Use i18n keys for all user-visible text.

**Tests**

* Page render test.
* Empty/error/unauthorized tests.
* Delete confirmation test.
* i18n raw-string test.

---

## G14. `products/data-cloud/delivery/ui/src/features/data-fabric/components/StorageProfilesList.tsx`

**What to change**

* Use common table/list component.
* Add accessible row actions.
* Add sorting/filtering if supported by backend, otherwise hide unsupported controls.
* Add redaction for sensitive config fields.
* Add keyboard navigation.

**Tests**

* Table action test.
* Keyboard accessibility test.
* Sensitive field redaction test.

---

## G15. `products/data-cloud/delivery/ui/src/features/data-fabric/components/DataConnectorsPage.tsx`

**What to change**

* Use canonical `/api/v1/connectors` service.
* Add connect/test/sync/enable/disable flows.
* Show runtime unavailable state when feature disabled.
* Show connector health and last sync status.
* Add i18n keys.

**Tests**

* Connector lifecycle page test.
* Feature disabled test.
* i18n test.

---

## G16. `products/data-cloud/delivery/ui/src/features/data-fabric/components/DataConnectorsList.tsx`

**What to change**

* Use common table/list component.
* Add row click to details.
* Add row actions:

  * test,
  * sync,
  * enable,
  * disable,
  * rotate credentials,
  * delete.
* Gate actions by permission/capability.
* Add accessible labels and keyboard support.

**Tests**

* Action visibility test.
* Permission gating test.
* Keyboard test.

---

## G17. Add `products/data-cloud/delivery/ui/src/features/media/services/api.ts`

**What to add**

* Contract-backed media service:

  * create artifact,
  * list artifacts,
  * get artifact,
  * delete artifact,
  * request transcription,
  * request vision analysis,
  * get job status,
  * get results.
* Use generated OpenAPI types.
* Handle redacted fields and 403/503.

**Tests**

* Media API service test.

---

## G18. Add `products/data-cloud/delivery/ui/src/features/media/types/index.ts`

**What to add**

* UI-safe media artifact types.
* Job state enum.
* Result state.
* Redaction marker type.
* Consent status type.

**Tests**

* Type compatibility test.

---

## G19. Add `products/data-cloud/delivery/ui/src/features/media/stores/media.store.ts`

**What to add**

* Media artifact state.
* Processing job state.
* Result state.
* Error/request ID state.
* Feature unavailable/degraded state.

**Tests**

* Media store lifecycle test.

---

## G20. Add `products/data-cloud/delivery/ui/src/features/media/components/MediaArtifactsPage.tsx`

**What to add**

* Low-cognitive-load media artifact page.
* Default list with search/filter.
* Actions:

  * register artifact,
  * request transcription,
  * request analysis,
  * view status,
  * view result,
  * delete.
* Hide advanced metadata behind disclosure.
* Show consent/retention warnings.

**Tests**

* Page flow test.
* Consent warning test.
* Processing status test.

---

## G21. Add `products/data-cloud/delivery/ui/src/features/media/components/MediaArtifactDetails.tsx`

**What to add**

* Details panel/page for artifact metadata, processing jobs, and results.
* Redact sensitive fields unless allowed.
* Show lineage/event links when available.

**Tests**

* Details render test.
* Redaction test.

---

## G22. `products/data-cloud/delivery/ui/src/routes/*` or route manifest source

**What to change**

* Add route for media only if backend media job flow is implemented.
* Ensure default nav remains outcome-first.
* Hide role-disclosed surfaces unless capabilities allow them:

  * Context,
  * Insights,
  * Reviews,
  * Patterns,
  * Agents,
  * Learning,
  * Connectors,
  * Plugins,
  * Settings,
  * Contracts.
* Use `/api/v1/surfaces` to decide whether route is visible, degraded, or unavailable.

**Tests**

* Route truth test.
* Surface-based nav visibility test.
* Unauthorized route test.

---

# H. Data contracts, connectors, storage profiles, and Data Plane completeness

## H1. `products/data-cloud/contracts/openapi/data-cloud.yaml`

**What to change**

* Add/align canonical storage profile schemas:

  * `StorageProfile`
  * `CreateStorageProfileRequest`
  * `UpdateStorageProfileRequest`
  * `StorageProfileMetrics`
* Add/align connector schemas:

  * `Connector`
  * `CreateConnectorRequest`
  * `UpdateConnectorRequest`
  * `ConnectorHealth`
  * `ConnectorSyncStatus`
  * `ConnectorSchemaPreview`
* Add canonical paths:

  * `/api/v1/storage-profiles`
  * `/api/v1/storage-profiles/{profileId}`
  * `/api/v1/storage-profiles/{profileId}/set-default`
  * `/api/v1/storage-profiles/{profileId}/metrics`
  * `/api/v1/connectors`
  * `/api/v1/connectors/{connectionId}`
  * `/api/v1/connectors/{connectionId}/test`
  * `/api/v1/connectors/{connectionId}/enable`
  * `/api/v1/connectors/{connectionId}/disable`
  * `/api/v1/connectors/{connectionId}/rotate-credentials`
  * `/api/v1/connectors/{connectionId}/health`
  * `/api/v1/connectors/{connectionId}/schema`
  * `/api/v1/connectors/{connectionId}/sync`
  * `/api/v1/connectors/{connectionId}/sync/status`
* Mark credential fields write-only.
* Add response examples for unavailable/degraded connector feature.

**Where**

* Existing DataSource contract and connector registration request exist but need canonical endpoint alignment.

**Tests**

* OpenAPI schema test.
* UI generated type test.
* Router sync test.

---

## H2. Add or update `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/StorageProfileHandler.java`

**What to change/add**

* If file exists, update; otherwise create.
* Implement:

  * list profiles,
  * create profile,
  * get profile,
  * update profile,
  * delete profile,
  * set default,
  * get metrics.
* Enforce request context and permissions.
* Redact sensitive config.
* Validate storage type, encryption, compression, provider config.
* Emit audit events for create/update/delete/default changes.

**Tests**

* `StorageProfileHandlerTest`
* `StorageProfileHandlerSecurityTest`
* `StorageProfileHandlerRedactionTest`

---

## H3. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandler.java`

**What to change**

* Align response shape with OpenAPI.
* Make connector sync return durable job ID and status endpoint.
* Ensure `/statistics` compatibility maps to canonical `/sync/status`.
* Remove secrets from all GET responses.
* Add feature-unavailable response shape consistent with Runtime Truth.

**Where**

* Router currently returns runtime truth 503 when connector handler is unavailable or feature-disabled.

**Tests**

* Connector API contract test.
* Sync job status test.
* Feature disabled test.

---

## H4. `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandlerTest.java`

**What to add/update**

* Assert canonical `/api/v1/connectors` response shape.
* Assert credentials are not returned.
* Assert feature-disabled returns runtime truth payload.
* Assert sync lifecycle returns job ID/status.

---

## H5. `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/ConnectorLifecycleTest.java`

**What to add/update**

* End-to-end handler-level flow:

  * register connector,
  * get connector,
  * test connector,
  * enable,
  * trigger sync,
  * read sync status,
  * disable,
  * rotate credentials,
  * delete.
* Tenant isolation for every step.

---

# I. Runtime Truth, observability, and async lifecycle

## I1. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SurfaceRegistryHandler.java`

**What to change**

* Add surfaces for:

  * Context Plane,
  * Media artifacts,
  * Audio-video STT,
  * Audio-video Vision,
  * Audio-video Multimodal,
  * Connectors,
  * Storage profiles,
  * Agent runtime,
  * Action execution,
  * AI assist.
* Each surface must include:

  * owner plane,
  * state,
  * status,
  * required dependencies,
  * dependency probes,
  * runtime profile,
  * limitations,
  * actions allowed.
* Do not expose release-readiness surfaces as normal product UX in this iteration.

**Tests**

* Surface registry unit test.
* Surface schema test.
* UI nav contract test.

---

## I2. `products/data-cloud/contracts/openapi/data-cloud.yaml`

**What to change**

* Expand `SurfaceRecord` if needed:

  * add `featureFlag`,
  * add `visibility`,
  * add `roleRequirements`,
  * add `reasonCode`,
  * add `recommendedAction`.
* Ensure generated UI can distinguish:

  * unavailable,
  * degraded,
  * disabled,
  * preview,
  * unauthorized.

**Where**

* Existing `SurfaceRecord` schema.

**Tests**

* Surface schema generation test.
* UI surface parsing test.

---

## I3. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RequestTraceSupport.java`

**What to change**

* Ensure every response includes request/correlation ID.
* Ensure async job creation stores request/correlation ID.
* Ensure SSE/WebSocket messages include correlation or event IDs.

**Tests**

* Request trace propagation test.

---

## I4. `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`

**What to change**

* Standardize execution log entry structure.
* Include node-level status and duration.
* Include failure reason and retryability.
* Ensure logs endpoint cannot leak cross-tenant data.

**Where**

* OpenAPI already includes execution detail and logs schemas.

**Tests**

* Execution logs tenant isolation test.
* Failure-state observability test.

---

## I5. `monitoring/prometheus/rules/data-cloud.yml`

**What to change**

* Add alerts for:

  * media job failures,
  * connector sync failures,
  * Action execution failure rate,
  * AI provider unavailable,
  * tenant auth rejection spikes,
  * surface dependency unavailable.
* Do not add release-readiness alerts.

**Tests**

* YAML lint if available.
* No readiness checks.

---

## I6. `monitoring/grafana/dashboards/data-cloud-platform.json`

**What to change**

* Add panels for:

  * surface state,
  * connector sync status,
  * media processing jobs,
  * Action executions,
  * AI assist fail-closed/fallback,
  * tenant auth rejection.
* Avoid evidence/release panels in this pass.

**Tests**

* Dashboard JSON validation only.

---

# J. Tests and quality gates without readiness execution

## J1. `products/data-cloud/delivery/launcher/build.gradle.kts`

**What to change**

* Raise Jacoco threshold from `0.00` after adding focused tests.
* Set staged threshold initially to realistic value for touched launcher handlers.
* Change `spotbugs.ignoreFailures = true` to false only after fixing current SpotBugs blockers in touched areas.
* Keep `checkDataCloudOpenApiSync` because it is contract drift, not release-readiness.
* Do not wire in readiness scripts.

**Where**

* Jacoco threshold currently `0.00`.
* SpotBugs currently ignores failures.
* OpenAPI-router sync task exists and is useful.

**Tests**

* Targeted launcher tests only.

---

## J2. `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilderTest.java`

**What to add/update**

* Assert media nested routes exist.
* Assert storage profile routes exist if implemented.
* Assert legacy Action root routes are disabled by default.
* Assert canonical `/api/v1/action/*` routes are always present.

---

## J3. `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupportTenantResolutionTest.java`

**What to add/update**

* Add coverage for new helper methods:

  * `requireRequestContext`,
  * `requirePermission`,
  * policy-denied response.
* Confirm no handler needs raw `X-Tenant-ID` after migration.

---

## J4. Add `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/MediaArtifactControllerTest.java`

**What to test**

* Create artifact.
* List artifacts.
* Get artifact.
* Delete artifact.
* Reject missing consent for audio/video.
* Do not expose raw sensitive metadata unless allowed.

---

## J5. Add `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/MediaArtifactControllerTenantSecurityTest.java`

**What to test**

* Production rejects raw tenant header.
* Authenticated principal tenant is used.
* Cross-tenant artifact access is denied.
* Support access requires support/admin role and reason.

---

## J6. Add `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/MediaArtifactProcessingJobTest.java`

**What to test**

* Transcription request creates durable job.
* Vision analysis request creates durable job.
* Job status transitions.
* Failed job includes reason.
* Result is tenant-isolated and redacted.

---

## J7. Add `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/StorageProfileHandlerTest.java`

**What to test**

* CRUD.
* Set default.
* Metrics.
* Validation.
* Redaction.
* Permission denial.

---

## J8. Add `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/ActionRouteNamespaceTest.java`

**What to test**

* Canonical Action routes under `/api/v1/action/*`.
* Legacy routes disabled unless feature flag enabled.
* No new Action-owned route added at root.

---

## J9. Add `products/data-cloud/delivery/ui/src/features/data-fabric/services/api.test.ts`

**What to test**

* Uses canonical `/api/v1/connectors`.
* Uses canonical `/api/v1/storage-profiles`.
* Correct storage type mapping.
* Handles unavailable/degraded response.
* Does not send credentials after create/update.

---

## J10. Add `products/data-cloud/delivery/ui/src/features/media/services/api.test.ts`

**What to test**

* Create/list/get/delete artifact.
* Request transcription.
* Request analysis.
* Read job status.
* Read results.
* Handles 403/503/redacted fields.

---

## J11. Add `products/data-cloud/delivery/ui/src/features/media/components/MediaArtifactsPage.test.tsx`

**What to test**

* Loading/empty/error/unauthorized/degraded states.
* Register artifact.
* Trigger processing.
* View job status.
* View result.
* Permission-gated actions.

---

## J12. Add `products/data-cloud/delivery/ui/src/__tests__/routes/surfaceDrivenNavigation.test.tsx`

**What to test**

* Default nav only shows outcome-first surfaces.
* Context/Agents/Plugins/Settings are role/capability disclosed.
* Disabled/unavailable surfaces are hidden or explained.
* No release-readiness/evidence surfaces appear in normal product nav.

---

# K. Audio-video service alignment

## K1. `products/audio-video/docs/capability-map.md`

**What to change**

* Replace overstated “Implemented” status with actual states.
* Use active module paths from generated settings.
* Add Data Cloud integration status:

  * metadata registration: partial,
  * processing request: pending after media route fix,
  * durable job lifecycle: required,
  * result retrieval: required,
  * privacy/retention enforcement: required.
* Remove “evidence” as an implementation target for this pass.

**Where**

* Current capability matrix says all capabilities are implemented.

---

## K2. `products/audio-video/libs/common/src/test/java/com/ghatana/audio/video/common/proto/ProtoCompatibilityTest.java`

**What to change**

* Add compatibility assertions for Data Cloud media job request/result contracts if proto bridge is used.
* Ensure STT/TTS/Vision/Multimodal proto types include tenant ID, artifact ID, job ID, correlation ID, and consent/retention metadata.

**Where**

* Existing proto compatibility test file found in search results.

---

## K3. `products/audio-video/modules/infrastructure/messaging`

**What to change**

* Add consumer/producer contract for Data Cloud media processing events.
* Ensure no direct dependency on Data Cloud launcher internals.
* Use public event/SPI contracts.

**Tests**

* Media event bridge contract test.

---

## K4. `products/audio-video/modules/infrastructure/security`

**What to change**

* Enforce tenant and permission metadata from Data Cloud job request.
* Add consent/retention validation for audio/video processing.
* Reject job without consent where required.

**Tests**

* Media consent enforcement test.
* Tenant isolation test.

---

## K5. `products/audio-video/modules/intelligence/multimodal-service`

**What to change**

* Accept Data Cloud media processing jobs.
* Emit processing result references, not raw sensitive media content.
* Include trace/request IDs in logs and metrics.

**Tests**

* Multimodal Data Cloud job integration test.

---

# L. Files explicitly out of scope for this iteration

Do **not** create tasks to execute or regenerate these now:

* `.kernel/evidence/**`
* `release-evidence/**`
* `products/data-cloud/lifecycle/readiness-evidence.yaml`
* `scripts/validate-data-cloud-release-evidence.mjs`
* `scripts/check-product-release-readiness.mjs`
* `scripts/check-data-cloud-maturity-proof.mjs`
* `products/data-cloud/scripts/verify-production-readiness.sh`
* `products/data-cloud/scripts/generate-route-inventory.mjs` unless needed for route docs only
* any `*release*`, `*readiness*`, `*evidence*`, or `*maturity-proof*` script as an execution target

Keep those files unchanged unless a future iteration explicitly focuses on release readiness.

---

# Minimal grouped verification plan

Use only targeted checks after each task group.

## Backend/API targeted checks

```bash
./gradlew :products:data-cloud:delivery:launcher:test --tests '*MediaArtifact*'
./gradlew :products:data-cloud:delivery:launcher:test --tests '*StorageProfile*'
./gradlew :products:data-cloud:delivery:launcher:test --tests '*DataSourceRegistry*'
./gradlew :products:data-cloud:delivery:launcher:test --tests '*ActionRouteNamespace*'
./gradlew :products:data-cloud:delivery:launcher:checkDataCloudOpenApiSync
```

## Action Plane targeted checks

```bash
./gradlew :products:data-cloud:planes:action:engine:test
./gradlew :products:data-cloud:planes:action:orchestrator:test
./gradlew :products:data-cloud:planes:action:agent-runtime:test
./gradlew :products:data-cloud:planes:action:server:test
```

## UI targeted checks

```bash
pnpm --filter @data-cloud/ui type-check
pnpm --filter @data-cloud/ui vitest run src/features/data-fabric
pnpm --filter @data-cloud/ui vitest run src/features/media
pnpm --filter @data-cloud/ui vitest run src/__tests__/routes/surfaceDrivenNavigation.test.tsx
```

Do **not** run:

```bash
pnpm --filter @data-cloud/ui test
pnpm --filter @data-cloud/ui test:readiness
pnpm check:data-cloud-maturity-proof
pnpm check:product-release-readiness
pnpm validate:data-cloud-release-evidence
```
