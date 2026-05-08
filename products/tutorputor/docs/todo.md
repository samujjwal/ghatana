## Automated Content Generation

### TODO 1

* **Priority**: Critical
* **Area**: Automated Content Generation / Backend
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/GenerationRequestJobProcessor.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/execution-service.ts`
* **What to do**: Pass `job.data.tenantId` into every `recordJobResult(...)` and `recordBatchResults(...)` call from `GenerationRequestJobProcessor`; update method callers and type signatures so tenant ID is mandatory at compile time.
* **Why**: The execution service requires tenant scoping, but the generation-request worker calls it without `tenantId`, creating a runtime failure and breaking tenant isolation for generated content jobs.
* **Verification / Tests**: Add worker integration tests for completed, failed, and dependency-blocked generation jobs proving `tenantId` is required, persisted, and rejected when mismatched.

### TODO 2

* **Priority**: Critical
* **Area**: Automated Content Generation / Content Safety
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/GenerationRequestJobProcessor.ts`, `executeEvaluation(...)`; `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/planner-service.ts`
* **What to do**: Remove unconditional `autoPublish: true` from generation evaluation and disable `AUTO_PUBLISH` review paths until semantic validation, golden datasets, and human-review bypass criteria are production-proven behind an explicit feature flag.
* **Why**: The product’s current state explicitly says fully autonomous content generation is not production-ready without human review; auto-publishing generated learning content can ship incorrect, unsafe, or pedagogically weak material.
* **Verification / Tests**: Add E2E tests where low-risk, medium-risk, and high-risk generated content all route through the correct review workflow unless `AUTONOMOUS_CONTENT_AUTO_PUBLISH_ENABLED=true` and quality gates pass.

### TODO 3

* **Priority**: Critical
* **Area**: Automated Content Generation / API Contracts / Security
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/routes.ts`, `POST /generation/requests/:requestId/results`
* **What to do**: Replace admin-role result submission with worker-authenticated submission using worker JWT, mTLS, or signed job callback; validate `jobId`, `status`, `durationMs`, `outputAssetId`, `outputData`, and diagnostics with a strict schema and reject missing or unknown fields.
* **Why**: The current route accepts broad records, defaults non-`failed` statuses to completed, allows empty job IDs, and is protected only by content/admin roles instead of a worker trust boundary.
* **Verification / Tests**: Add contract tests for malformed result payloads, missing `jobId`, invalid status, wrong tenant, unauthorized user token, valid worker token, and replayed result submission.

### TODO 4

* **Priority**: High
* **Area**: Automated Content Generation / Data Integrity
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/planner-service.ts`, `planRequest(...)`
* **What to do**: Move the `DRAFT → PLANNING → PLANNED` transition and job creation into a single transaction, or add explicit rollback/failure state handling if planning fails after setting `PLANNING`.
* **Why**: A failure after the initial status update can leave generation requests permanently stuck in `PLANNING`.
* **Verification / Tests**: Add failure-injection tests for planning errors before job creation, during job creation, and during request update; verify status becomes `DRAFT` or `FAILED_PLANNING` with an audit event.

### TODO 5

* **Priority**: High
* **Area**: Automated Content Generation / Simulations
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/GenerationRequestJobProcessor.ts`, `executeSimulation(...)`; `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/planner-service.ts`
* **What to do**: Generate simulations using the canonical SimKit contract: deterministic seed, parameter bounds, telemetry events, state snapshots, accessibility metadata, export config, failure states, and claim/evidence linkage.
* **Why**: Current simulation generation sends only claim text, grade, domain, `PARAMETER_EXPLORATION`, and `MEDIUM` complexity; it does not enforce simulation-first learning evidence requirements.
* **Verification / Tests**: Add golden tests for generated Math, Physics, Biology/Medicine, Technology, and Business simulations verifying seed determinism, keyboard accessibility, telemetry schema, and assessment embed-readiness.

### TODO 6

* **Priority**: High
* **Area**: Automated Content Generation / Assessment
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/GenerationRequestJobProcessor.ts`, `executeAssessment(...)`
* **What to do**: Replace the `example_to_assessment` adapter with a real assessment generator that creates prediction, manipulation, explanation, and constructed-response items with confidence-based marking, rubrics, answer keys, distractor rationales, and claim/evidence mappings.
* **Why**: Current assessment generation adapts worked examples into assessment items and does not satisfy TutorPutor’s CBM, simulation-based, evidence-based mastery model.
* **Verification / Tests**: Add generated-assessment contract tests verifying every generated assessment has CBM fields, scoring policy, rubric, claim IDs, evidence IDs, and at least one simulation-based item for simulation-first modules.

### TODO 7

* **Priority**: High
* **Area**: Automated Content Generation / Animations / Accessibility
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/GenerationRequestJobProcessor.ts`, `executeAnimation(...)`
* **What to do**: Extend animation generation requests to include pedagogical purpose, linked claim/evidence, transcript/caption requirements, reduced-motion fallback, visual description, duration bounds, and accessibility metadata.
* **Why**: Current animation generation hardcodes `TWO_D` and `45` seconds without proving instructional value or accessibility readiness.
* **Verification / Tests**: Add animation generation tests verifying captions, transcript, reduced-motion alternative, linked claim/evidence, and accessibility metadata are required before materialization.

### TODO 8

* **Priority**: High
* **Area**: Content & CMS / Simplification
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/routes.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/**`
* **What to do**: Remove or feature-flag deprecated queue-driven Content Studio generation endpoints and route all new content generation through the unified `/generation/requests` lifecycle.
* **Why**: The service marks queue-driven generation as deprecated but still exposes generation methods, creating duplicate workflows and inconsistent governance.
* **Verification / Tests**: Add route-level tests proving deprecated generation endpoints return `410 Gone` or are hidden unless a migration flag is enabled; verify admin UI uses only the unified generation-request API.

### TODO 9

* **Priority**: High
* **Area**: Automated Content Generation / Workers
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/index.ts`
* **What to do**: Treat unknown BullMQ job names as failed jobs by throwing an error, recording diagnostics, and routing them to the dead-letter queue instead of logging a warning and allowing completion flow to continue.
* **Why**: Unknown generation jobs can currently be marked completed by the worker wrapper even though no processor executed.
* **Verification / Tests**: Add worker tests for unknown job names proving they fail, are retried according to policy, and are moved to DLQ after max attempts.

### TODO 10

* **Priority**: High
* **Area**: DevOps / Workers / Reliability
* **Where**: `products/tutorputor/services/tutorputor-platform/src/plugins/workers.ts`, `products/tutorputor/services/tutorputor-platform/src/startup/content-worker-init.ts`
* **What to do**: Store the `ContentWorkerController` returned by `initializeContentWorker(...)` and call `worker.close()` during Fastify `onClose`; validate Redis URL before constructing `new URL(redisUrl)` and fail with a typed startup error.
* **Why**: The worker controller is discarded, so BullMQ worker, queue, DLQ, and Redis resources may not shut down cleanly; empty Redis URLs fail late and are treated as degraded mode.
* **Verification / Tests**: Add shutdown tests proving worker, queue, DLQ, Redis, and Prisma ownership are closed correctly; add startup config tests for missing/invalid `REDIS_URL`.

### TODO 11

* **Priority**: High
* **Area**: Automated Content Generation / Data Integrity
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/execution-service.ts`
* **What to do**: Pass `tenantId` into `maybeCompleteRequest(...)` and tenant-scope all request completion, status update, and snapshot publishing queries.
* **Why**: `maybeCompleteRequest(...)` and related publishing logic fetch/update by request ID only, violating the product rule that generated content and job state must remain tenant-scoped.
* **Verification / Tests**: Add multi-tenant generation tests with two tenants and same-shaped generation data proving no request, job, snapshot, or event crosses tenant boundaries.

### TODO 12

* **Priority**: Medium
* **Area**: Automated Content Generation / Reuse
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/planner-service.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/execution-service.ts`
* **What to do**: Consolidate duplicated `mapRequest(...)`, `mapJob(...)`, enum mapping, date conversion, and JSON coercion logic into a shared generation mapper module.
* **Why**: Duplicate mappers increase risk of inconsistent API shapes, lifecycle state bugs, and hard-to-debug generation status rendering.
* **Verification / Tests**: Add mapper unit tests covering all `GenerationRequestStatus`, `GenerationJobStatus`, review paths, missing optional dates, and diagnostics.

---

## AI Tutor / Adaptive Engine / AI Governance

### TODO 13

* **Priority**: Critical
* **Area**: AI / Learning Model
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/index.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/ai/OllamaAIProxyService.ts`
* **What to do**: Inject `app.prisma` into `OllamaAIProxyService` and implement tenant-scoped RAG retrieval from modules, content blocks, learning claims, misconceptions, simulation state, and recent attempts.
* **Why**: The AI module constructs `OllamaAIProxyService` without Prisma, so module and claim grounding cannot load; the tutor falls back to IDs instead of real learning context.
* **Verification / Tests**: Add AI tutor integration tests proving the prompt includes tenant-scoped module title, content block excerpts, claims, current simulation state, recent attempts, and misconceptions.

### TODO 14

* **Priority**: Critical
* **Area**: AI / Security & Privacy
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/OllamaAIProxyService.ts`
* **What to do**: Add tenant filters to module/content/claim retrieval and stop querying claims by ID alone; load claims through their experience/module tenant relationship.
* **Why**: Once Prisma is injected, current context-fetching methods can leak cross-tenant content because `contentAsset.findFirst({ id })` and `learningClaim.findMany({ id in claimIds })` are not tenant-scoped.
* **Verification / Tests**: Add cross-tenant AI tutor tests where a user from tenant A requests tenant B module/claim IDs and receives a 403 or no context.

### TODO 15

* **Priority**: High
* **Area**: AI / Production Quality
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/OllamaAIProxyService.ts`, `handleTutorQuery(...)`
* **What to do**: Stop swallowing Ollama/service failures as normal tutor answers; return typed service-unavailable errors, mark safety/governance status correctly, and surface fallback UI guidance through the client.
* **Why**: Current failure handling returns “I’m sorry...” with `safety.blocked=false`, which hides AI outages and makes route-level audit treat failed tutoring as successful output.
* **Verification / Tests**: Add tests for Ollama timeout, TLS failure, malformed response, and provider outage verifying 503 response, audit failure event, and no fake successful tutor answer.

### TODO 16

* **Priority**: High
* **Area**: AI / Assessment
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/OllamaAIProxyService.ts`, `generateQuestionsFromContent(...)`; `products/tutorputor/services/tutorputor-platform/src/modules/ai/routes.ts`, `POST /generate-questions`
* **What to do**: Fetch actual module/content text, learning objectives, and misconceptions before generating questions; remove the prompt that only passes `moduleId` and `tenantId`; stop returning an empty array on generation failure.
* **Why**: “Generate questions from content” currently does not use content, so generated assessments are ungrounded and unverifiable.
* **Verification / Tests**: Add tests with a seeded module and content blocks proving generated questions reference the supplied content and fail with a typed error when no content exists.

### TODO 17

* **Priority**: High
* **Area**: AI / Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/OllamaAIProxyService.ts`, `generateLearningUnitDraft(...)`
* **What to do**: Remove generic static fallback learning-unit drafts on AI failure; persist failed generation state with diagnostics and require retry or human-authored draft.
* **Why**: Static fallback sections create fake production content that can enter authoring workflows without genuine AI generation or instructional validation.
* **Verification / Tests**: Add AI failure tests verifying no fallback learning unit is persisted and the author receives a recoverable generation error.

### TODO 18

* **Priority**: High
* **Area**: AI / API Contracts
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/index.ts`, `products/tutorputor/services/tutorputor-platform/src/plugins/business-modules.ts`
* **What to do**: Register AI health and cache routes relative to the module prefix instead of hardcoding `/api/v1/ai/health` and `/api/v1/ai/cache/stats` inside a plugin already mounted at `/api/v1/ai`.
* **Why**: Hardcoded full paths inside a prefixed Fastify plugin can produce duplicate paths such as `/api/v1/ai/api/v1/ai/health`.
* **Verification / Tests**: Add route registration tests that list all AI routes and verify exactly `/api/v1/ai/health`, `/api/v1/ai/cache/stats`, `/api/v1/ai/tutor/query`, `/api/v1/ai/generate-questions`, `/api/v1/ai/generate-concept`, and `/api/v1/ai/generate-simulation`.

### TODO 19

* **Priority**: Medium
* **Area**: AI Governance / Privacy
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/routes.ts`, AI audit payload generation for `/tutor/query`
* **What to do**: Redact and size-limit AI audit payloads, especially `question`, `currentSimulationState`, `recentAttempts`, misconceptions, and response payloads; store hashes or references for large/sensitive fields.
* **Why**: AI audit logging can capture learner-sensitive context and high-volume simulation state directly.
* **Verification / Tests**: Add audit tests proving PII-like fields, oversized simulation payloads, and learner free-text are redacted/truncated according to policy.

---

## Knowledge Base / Validation / Content Quality

### TODO 20

* **Priority**: Critical
* **Area**: Content Generation / Validation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/service.ts`
* **What to do**: Remove synthetic and mock production paths in `searchLocalKnowledgeBase(...)`, `searchExternalSources(...)`, `generateExamples(...)`, `searchMathStandards(...)`, and `searchScienceStandards(...)`; replace them with real governed knowledge records or explicit “insufficient evidence” failures.
* **Why**: The validator can currently fabricate definitions, examples, and standards, which undermines automated content correctness.
* **Verification / Tests**: Add validation tests proving missing evidence returns `FAIL` or `REVIEW_REQUIRED`, never synthetic facts or mock standards.

### TODO 21

* **Priority**: Critical
* **Area**: Simulations / Validation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/independent-validator-service.ts`, `mapContentType(...)`
* **What to do**: Replace the mapping of `simulation` and `animation` to `explanation` with dedicated validators for simulation manifest schema, deterministic execution, parameter bounds, telemetry config, accessibility metadata, scientific correctness, and animation storyboard/caption correctness.
* **Why**: Simulations and animations are core learning artifacts and cannot be validated as text explanations.
* **Verification / Tests**: Add validator tests for invalid simulation schema, missing seed, missing telemetry, inaccessible controls, invalid physics/math outputs, and animation without transcript/reduced-motion fallback.

### TODO 22

* **Priority**: High
* **Area**: Content Generation / Semantic Validation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/service.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/**`, `products/tutorputor/services/tutorputor-platform/src/modules/golden-dataset/**`
* **What to do**: Replace heuristic scoring with vector-backed semantic validation and golden-dataset comparisons for factual accuracy, contradiction detection, grade fit, pedagogical quality, simulation correctness, and assessment answer-key correctness.
* **Why**: Current validation uses simple assertion extraction, passed-check counts, and weak heuristics; it cannot safely support autonomous content generation.
* **Verification / Tests**: Add domain golden datasets for math, physics, biology/medicine, CS, and business; compare generated content against expected claims, explanations, simulations, and assessment keys.

### TODO 23

* **Priority**: High
* **Area**: Content Generation / External Evidence
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/service.ts`
* **What to do**: Move Wikipedia, OpenStax, and Khan Academy lookups behind a source-governance adapter with allowlists, timeouts, caching, source versioning, citation persistence, and offline failure handling.
* **Why**: Live external fetches during validation make results non-deterministic and do not persist source provenance consistently.
* **Verification / Tests**: Add deterministic tests with recorded fixtures and verify source metadata, retrieval timestamp, credibility score, and citation URL are persisted.

---

## Content Studio / CMS

### TODO 24

* **Priority**: High
* **Area**: Content & CMS / Data Integrity
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/routes.ts`, `PATCH /experiences/:experienceId/tasks/:taskId`, `DELETE /experiences/:experienceId/tasks/:taskId`
* **What to do**: Replace blank claim ID calls (`updateTask(experienceId, "", taskId, ...)`, `deleteTask(experienceId, "", taskId)`) with claim-scoped routes or service-level verification that the task belongs to the target experience and claim.
* **Why**: Passing an empty claim ID weakens task ownership validation and can update/delete the wrong task if IDs are reused or checks are incomplete.
* **Verification / Tests**: Add tests for updating/deleting a task under the correct claim, wrong claim, wrong experience, and wrong tenant.

### TODO 25

* **Priority**: High
* **Area**: Content & CMS / API Contracts
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/routes.ts`
* **What to do**: Remove broad `.passthrough()` from Content Studio create/update schemas and define exact request schemas for experience metadata, grade range, module ID, status, tasks, claims, generation requests, and automation rules.
* **Why**: Passthrough schemas allow undocumented fields to enter service logic and persistence paths.
* **Verification / Tests**: Add schema tests rejecting unknown fields and contract tests proving frontend request payloads match the documented API.

### TODO 26

* **Priority**: Medium
* **Area**: Content & CMS / Automated Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/routes.ts`, `buildGlossaryTerms(...)`
* **What to do**: Replace the hardcoded glossary helper with a real glossary generation/retrieval path using the generation-request pipeline, domain concept graph, or knowledge-base service.
* **Why**: The current helper explicitly states it is temporary and returns generic domain terms, which is insufficient for production authoring.
* **Verification / Tests**: Add tests verifying glossary terms are grounded in domain concepts and grade level, not hardcoded defaults.

### TODO 27

* **Priority**: High
* **Area**: Content & CMS / Publishing
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts`
* **What to do**: Replace silent defaults in mapping and normalization functions (`taskTypeFromRow`, `evidenceTypeFromRow`, `bloomFromInput`, `normalizeGradeRange`) with explicit validation failures for unknown persisted values.
* **Why**: Defaulting invalid task/evidence/Bloom/grade values hides corrupted content and can publish incorrect learning experiences.
* **Verification / Tests**: Add corrupted-data tests proving invalid Bloom level, task type, evidence type, and grade range block validation and publishing.

### TODO 28

* **Priority**: High
* **Area**: Frontend / UI/UX / Content & CMS
* **Where**: `products/tutorputor/apps/tutorputor-admin/**`, especially `UnifiedContentStudio` and Content Studio routes/components
* **What to do**: Refactor the Content Studio UI into smaller workflow-driven screens: Plan, Generate, Validate, Review, Preview, Publish, Analytics; remove mock/test references and reduce component bloat.
* **Why**: The repo’s current state identifies Content Studio bloat and mock/test references, which increases cognitive load for educators and makes content quality workflows harder to audit.
* **Verification / Tests**: Add Playwright tests for authoring a learning experience end-to-end and component tests for each workflow step with realistic data.

---

## TutorPutor App / Offline / Mobile

### TODO 29

* **Priority**: High
* **Area**: Frontend / Offline / Privacy
* **Where**: `products/tutorputor/apps/tutorputor-web/src/sw.ts`
* **What to do**: Remove `/api/v1/ai/tutor/query` from offline mutation queuing or add an explicit AI-offline policy that stores only a safe disabled-state marker, not learner questions or AI prompts.
* **Why**: AI tutor questions may contain sensitive learner data and should not be replayed blindly as offline mutations.
* **Verification / Tests**: Add service-worker tests verifying AI tutor requests fail gracefully offline or store only approved redacted metadata.

### TODO 30

* **Priority**: High
* **Area**: Frontend / Offline / Data Integrity
* **Where**: `products/tutorputor/apps/tutorputor-web/src/sw.ts`, `products/tutorputor/apps/tutorputor-web/src/offline/offlineSyncIndexedDB.ts`
* **What to do**: Replace `btoa(body).slice(0, 16)` idempotency keys with a stable SHA-256 hash including tenant, user, entity type, entity ID, logical operation, and client mutation ID.
* **Why**: Current idempotency keys can collide, leak body-derived data shape, and are not tied to tenant/user/entity semantics.
* **Verification / Tests**: Add collision tests, duplicate replay tests, cross-tenant replay tests, and conflict-resolution tests for progress, simulation capture, assessment attempts, and telemetry batches.

### TODO 31

* **Priority**: High
* **Area**: Mobile / Offline / Product Readiness
* **Where**: `products/tutorputor/apps/tutorputor-mobile/**`, `products/tutorputor/apps/tutorputor-mobile/src/config.ts`
* **What to do**: Keep mobile hidden behind a non-production feature flag until the learner shell, route parity, auth/session handling, offline sync, conflict resolution, and app-store deployment checklist are complete.
* **Why**: Product docs mark mobile as in development, not production-ready, while configuration defaults to a production API and enables offline/background sync by default.
* **Verification / Tests**: Add mobile smoke tests for login, module download, offline lesson progress, simulation capture, assessment attempt, sync, conflict handling, and disabled-production-channel behavior.

### TODO 32

* **Priority**: Medium
* **Area**: Frontend / Offline / UX
* **Where**: `products/tutorputor/apps/tutorputor-web/src/sw.ts`
* **What to do**: Add explicit conflict UI and replay status handling for queued mutations instead of only posting `SYNC_COMPLETED` and `SYNC_FAILED` messages.
* **Why**: Learners need to know whether offline progress, assessment attempts, and simulation captures actually reconciled or require action.
* **Verification / Tests**: Add E2E tests for offline progress success, replay conflict, retry exceeded, and learner-visible recovery action.

---

## API Contracts / Routing / Platform Coherence

### TODO 33

* **Priority**: Critical
* **Area**: API Contracts / Backend
* **Where**: `products/tutorputor/docs/architecture/API_ROUTE_OWNERS.json`, `products/tutorputor/api/tutorputor-api.openapi.yaml`, `products/tutorputor/services/tutorputor-platform/src/modules/**/routes.ts`
* **What to do**: Regenerate and validate `API_ROUTE_OWNERS.json` from actual Fastify route registration; remove malformed route keys containing `${prefix}` and duplicated path segments such as `/content/generation/generation/...`, `/content/studio${prefix}/...`, and `/engagement/engagement/...`.
* **Why**: Route ownership metadata is supposed to be the canonical contract map, but it currently contains generated placeholder strings and duplicated prefixes.
* **Verification / Tests**: Add a CI route-ownership test that boots the Fastify app, enumerates routes, compares them to OpenAPI and `API_ROUTE_OWNERS.json`, and fails on missing, extra, malformed, or duplicate routes.

### TODO 34

* **Priority**: High
* **Area**: API Contracts / Error Handling
* **Where**: `products/tutorputor/services/tutorputor-platform/src/core/middleware/standard-error-response.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/**/routes.ts`
* **What to do**: Replace manual `{ error: ... }` responses across module routes with the canonical error envelope and typed error helpers.
* **Why**: Routes currently bypass the standard error middleware with inconsistent response shapes, making frontend and contract handling unreliable.
* **Verification / Tests**: Add contract tests for representative 400, 401, 403, 404, 409, 422, 429, and 500 responses across content, generation, AI, learning, integration, and compliance routes.

### TODO 35

* **Priority**: High
* **Area**: API Contracts / Simplification
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/index.ts`, `registerContentStudioRoutes(...)`
* **What to do**: Remove the one-release legacy alias `/api/content-studio/*` after migration, or enforce an explicit deprecation feature flag with telemetry and a removal date.
* **Why**: Legacy and canonical Content Studio routes duplicate behavior and complicate contracts, permissions, clients, tests, and audit logs.
* **Verification / Tests**: Add route tests proving only canonical `/api/v1/content-studio/*` is exposed in production mode.

---

## Learning Model / Assessment / Analytics

### TODO 36

* **Priority**: High
* **Area**: Learning Model / Assessment
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/learning/**`, `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/**`, `products/tutorputor/libs/**`
* **What to do**: Enforce claim/evidence/task linkage from generated content through runtime assessment and mastery updates; block publishing when claims lack evidence, tasks lack scoring, or simulations lack process evidence.
* **Why**: TutorPutor’s differentiator is evidence-based mastery, not content completion; generation and learning flows must preserve traceability.
* **Verification / Tests**: Add end-to-end tests from generated learning experience → learner simulation task → CBM answer → mastery update → instructor dashboard.

### TODO 37

* **Priority**: High
* **Area**: Analytics / Instructor Dashboard
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/analytics-consolidation/**`, `products/tutorputor/services/tutorputor-platform/src/modules/content-quality-monitoring/**`, `products/tutorputor/apps/tutorputor-admin/**`
* **What to do**: Replace heuristic-only learner/content risk and quality signals with auditable metric definitions tied to raw learning events, assessment attempts, simulation traces, CBM calibration, and review decisions.
* **Why**: Current state identifies heuristic-only gap detection and surface-level quality monitoring; dashboards must show explainable learning evidence.
* **Verification / Tests**: Add metric reconciliation tests comparing dashboard values to seeded raw events and attempts.

### TODO 38

* **Priority**: Medium
* **Area**: Learning Model / Classroom
* **Where**: `products/tutorputor/libs/tutorputor-core/**`, learner profile schema/models, `products/tutorputor/services/tutorputor-platform/src/modules/user/**`, `products/tutorputor/services/tutorputor-platform/src/modules/learning/**`
* **What to do**: Add classroom/cohort binding to learner profiles or enrollment membership and apply it to teacher dashboards, assignments, filters, analytics, and exports.
* **Why**: Current product state notes classroom filtering is not implemented, limiting instructor workflows and institutional readiness.
* **Verification / Tests**: Add teacher dashboard tests with two classrooms proving teachers only see assigned learners, assignments, attempts, and risk flags.

---

## Security / Privacy / Compliance

### TODO 39

* **Priority**: High
* **Area**: Security & Privacy / AI Governance
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/routes.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/audit/**`, `products/tutorputor/services/tutorputor-platform/src/modules/compliance/**`
* **What to do**: Correct AI audit governance metadata so failed calls are not automatically recorded as `consentState: "missing"` and blocked calls are not conflated with provider failures.
* **Why**: Current audit metadata can misrepresent the cause of AI failures, weakening compliance evidence.
* **Verification / Tests**: Add audit tests for missing consent, safety-blocked content, rate limit, provider timeout, and successful request.

### TODO 40

* **Priority**: High
* **Area**: Security & Privacy / Child Safety
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/auth/**`, `products/tutorputor/services/tutorputor-platform/src/modules/user/**`, `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/**`, `products/tutorputor/apps/tutorputor-web/**`
* **What to do**: Verify and enforce under-13 parental consent across learner registration, AI tutor usage, telemetry capture, social/forum features, notifications, and data export/delete flows.
* **Why**: TutorPutor targets K-12 learners and must enforce COPPA/FERPA-style consent before sensitive learning, AI, social, and telemetry processing.
* **Verification / Tests**: Add E2E tests for under-13 registration blocked before consent, consent approval, consent revocation, AI disabled state, telemetry disabled state, and social feature restrictions.

### TODO 41

* **Priority**: Medium
* **Area**: Compliance / Data Retention
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/compliance/**`, `products/tutorputor/services/tutorputor-platform/src/modules/content/telemetry/**`, `products/tutorputor/services/tutorputor-platform/src/modules/learning/**`
* **What to do**: Implement retention policies for AI interactions, simulation telemetry, assessment attempts, learning events, content-generation prompts, audit logs, and review records.
* **Why**: Product requirements include privacy and data subject rights, but production readiness requires explicit retention windows and purge/anonymization jobs.
* **Verification / Tests**: Add retention-job tests and compliance export/delete tests proving data is retained, anonymized, or deleted according to policy.

---

## DevOps / Observability / CI

### TODO 42

* **Priority**: High
* **Area**: Observability / Metrics
* **Where**: `products/tutorputor/services/tutorputor-platform/src/plugins/core.ts`
* **What to do**: Replace `request.routerPath ?? "unknown"` metric labeling with a Fastify-supported route identifier and normalize labels to avoid high-cardinality paths.
* **Why**: Current HTTP metrics can collapse routes into `unknown` or produce unreliable observability for production SLOs.
* **Verification / Tests**: Add metrics tests proving requests to `/health`, `/api/v1/learning/dashboard`, `/api/v1/ai/tutor/query`, and `/generation/requests` produce expected route labels.

### TODO 43

* **Priority**: High
* **Area**: Tests / CI
* **Where**: `products/tutorputor/**`, `products/tutorputor/.gitea/workflows/tutorputor-ci.yml`, package test scripts
* **What to do**: Remove “known pre-existing failures” from acceptable verification status; make CI fail on all unit, integration, API, Playwright, accessibility, and contract test failures, or explicitly quarantine tests with owner and expiry.
* **Why**: Conditional production readiness cannot rely on partially passing test suites for a learning platform with AI, assessments, privacy, and child-safety requirements.
* **Verification / Tests**: Add CI quality gate that reports zero unowned failures and blocks merge/release when any non-quarantined test fails.

### TODO 44

* **Priority**: Medium
* **Area**: DevOps / Notifications
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/routes.ts`, `GET /generation/notifications/stream`; `products/tutorputor/services/tutorputor-platform/src/modules/notifications/**`
* **What to do**: Replace heartbeat-only notification streams with actual generation job status events or hide the route until it emits real notifications.
* **Why**: A stream that only sends `connected` and pings gives the admin UI a false sense of real-time generation monitoring.
* **Verification / Tests**: Add SSE tests proving job queued, started, progress, completed, failed, and DLQ events are delivered to authorized users.

---

## Product Boundary / Deferred Features

### TODO 45

* **Priority**: High
* **Area**: Product Readiness / VR-WebXR
* **Where**: `products/tutorputor/docs/architecture/VR_WEBXR_ROADMAP_DECISION.md`, `products/tutorputor/services/tutorputor-vr/**`, VR-related schema/routes/UI references
* **What to do**: Ensure all VR/WebXR service, schema, route, and UI entry points are clearly marked deferred and hidden from production learner/admin flows unless a feature flag enables experimental access.
* **Why**: Product docs state VR/WebXR is deferred indefinitely; visible scaffolds can confuse scope, tests, and production readiness.
* **Verification / Tests**: Add production-mode route/UI tests proving no VR/WebXR learner or admin entry point is visible unless the explicit experimental flag is enabled.

### TODO 46

* **Priority**: Medium
* **Area**: Product Architecture / Documentation
* **Where**: `products/tutorputor/README.md`, `products/tutorputor/docs/architecture/README.md`, `products/tutorputor/docs/architecture/CURRENT_STATE.md`, `products/tutorputor/docs/architecture/specs/PRODUCT_SPEC.md`
* **What to do**: Reconcile architecture terminology across docs: consolidated modular monolith vs microservices, mobile status, VR status, content generation readiness, supported runtime topology, and canonical API ownership.
* **Why**: Current documents contain mixed architecture language and changing readiness claims that can mislead implementation and audits.
* **Verification / Tests**: Add a documentation consistency checklist and CI doc-lint rule for canonical architecture terms and deprecated-surface warnings.
