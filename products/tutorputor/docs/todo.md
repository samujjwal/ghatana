## Vision Alignment / Data Model

### TODO 1

* **Priority**: High
* **Area**: Vision Alignment / Data Integrity
* **Where**: `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma` `ModuleDomain`; domain filters/selectors in web/admin apps; generators and seed data using module domains 
* **What to do**: Expand TutorPutor’s canonical domain taxonomy beyond `MATH`, `SCIENCE`, and `TECH` to include the product’s target domains such as engineering, medicine/health, business/management, economics, computer science/technology, and interdisciplinary tracks. Update Prisma schema, migrations, generated clients, API DTOs, CMS selectors, learner filters, generation planner domain logic, seed data, and tests.
* **Why**: Current schema cannot represent the full TutorPutor vision and will force incorrect mappings, hardcoded exceptions, or duplicate domain concepts.
* **Verification / Tests**: Add migration tests, API contract tests, CMS module creation tests, learner module filtering tests, and generation-planner tests for every supported domain.

### TODO 2

* **Priority**: Critical
* **Area**: API Contracts / Backend / Frontend
* **Where**: `products/tutorputor/api/tutorputor-api.openapi.yaml`; `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/routes.ts`; frontend clients under `products/tutorputor/apps/tutorputor-web/src/api/**`  
* **What to do**: Reconcile the OpenAPI contract with actual backend routes. The OpenAPI file documents `/content/generate`, `/content/generate/{contentId}`, `/libraries`, and `/learning-paths/{userId}`, but the implemented generation control plane exposes `/generation/requests/**`. Either implement the documented contract as a facade or update OpenAPI to the real generation-request lifecycle, then regenerate typed clients.
* **Why**: API consumers and frontend code cannot rely on the contract if documented endpoints, backend route owners, response shapes, and actual routes diverge.
* **Verification / Tests**: Add OpenAPI-to-route contract tests, generated-client compile tests, and API E2E tests for content generation, request planning, execution, streaming, cancellation, and result recording.

### TODO 3

* **Priority**: High
* **Area**: Simplification / Content Generation / API Contracts
* **Where**: `products/tutorputor/api/tutorputor-api.openapi.yaml`; `products/tutorputor/services/tutorputor-platform/src/clients/ai-client.ts`; `products/tutorputor/services/tutorputor-platform/src/modules/ai/routes.ts`; `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/**`; `products/tutorputor/services/tutorputor-content-generation/**`   
* **What to do**: Consolidate the multiple content-generation entry points into one canonical architecture: OpenAPI/typed client → backend generation request lifecycle → queue/worker → Java content-generation runtime → validation/review → CMS intake. Remove or clearly demote overlapping ad hoc paths such as AI `/generate-concept`, AI `/generate-simulation`, gRPC direct helper methods, and OpenAPI `/content/generate` unless they delegate to the canonical flow.
* **Why**: Multiple generation paths will produce inconsistent provenance, validation, review, telemetry, cost tracking, and content quality behavior.
* **Verification / Tests**: Add architecture boundary tests, route ownership tests, and E2E tests proving all content-generation UI actions use the canonical path.

---

## Automated Content Generation

### TODO 4

* **Priority**: Critical
* **Area**: Content Generation / Simulations
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/planner-service.ts` `STANDARD_ARTIFACT_SET`, `VISUAL_DOMAINS`, `determinePlannedAssets` 
* **What to do**: Make simulation and visual/animation artifacts first-class for all TutorPutor learning modules unless an explicit validated exception is recorded. Include mathematics, technology/CS, medicine, business, economics, and interdisciplinary domains, not only physics/chemistry/biology/geometry/astronomy/engineering.
* **Why**: The product requires visual-first and simulation-first learning. Current planner behavior omits simulations and animations for many core domains.
* **Verification / Tests**: Add planner golden tests for every supported domain verifying required artifact graph includes claim, explainer, worked example, simulation, animation/visualization, assessment, and evaluation or an explicit exception reason.

### TODO 5

* **Priority**: Critical
* **Area**: Content Generation / Simulations
* **Where**: `products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/generators/SimulationGenerator.java` 
* **What to do**: Replace the generic simulation generator that emits static `seed = 42`, `parameterBounds.input = [0,10]`, and `expectedOutputs.baseline = 5` with domain-specific SimKit manifest generation. Require claim reference, deterministic seed, parameter semantics, bounds, model/engine type, expected outputs, telemetry events, accessibility metadata, keyboard controls, reduced-motion behavior, export configuration, and scoring hooks.
* **Why**: Current output is structurally present but not pedagogically, scientifically, or operationally usable as a learning simulation.
* **Verification / Tests**: Add golden manifest tests for math graphing, physics motion, CS logic/scheduling, biology/medicine vitals, and business/economics supply-demand simulations. Add schema validation tests that reject manifests without seed, parameter bounds, expected outputs, telemetry, and accessibility metadata.

### TODO 6

* **Priority**: High
* **Area**: Content Generation / Animations
* **Where**: `products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/generators/AnimationGenerator.java` 
* **What to do**: Replace the `Start/Middle/End` animation placeholder with a typed animation/visualization schema that includes learning claim linkage, domain-specific frames or state transitions, narration/caption metadata, reduced-motion fallback, visual validation criteria, timing, and accessibility alternatives.
* **Why**: Decorative keyframes do not support TutorPutor’s visual-first pedagogy or allow correctness validation.
* **Verification / Tests**: Add animation schema tests, accessibility metadata tests, and golden fixtures for at least math graph transformation, physics vector motion, biology process flow, and business causal-loop animation.

### TODO 7

* **Priority**: Critical
* **Area**: Content Generation / Assessment
* **Where**: `products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/generators/AssessmentGenerator.java`; Prisma `AssessmentItem` / `AssessmentAttempt` models  
* **What to do**: Replace generic assessment generation with evidence-centered assessment generation. Each item must link to learning claim/evidence/task, difficulty, rubric or answer key, distractor rationales, confidence-based marking metadata, feedback, misconception mapping, and at least one simulation-based item per module.
* **Why**: Current generated items only ask learners to identify a claim with placeholder distractors and cannot measure mastery, transfer, confidence, or process evidence.
* **Verification / Tests**: Add golden generated-assessment tests validating claim coverage, distractor quality, CBM metadata, simulation item presence, scoring rules, and rubric completeness.

### TODO 8

* **Priority**: Critical
* **Area**: Content Generation / Validation
* **Where**: `products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/validation/GeneratedContentValidationGate.java` 
* **What to do**: Extend validation beyond structural non-empty checks. Add deterministic checks for math/science correctness where possible, source/evidence grounding, hallucination risk, reading level, curriculum alignment, accessibility, localization readiness, age appropriateness, distractor quality, simulation determinism, parameter validity, animation usefulness, and assessment-to-objective coverage.
* **Why**: Current validation can pass content that is present but wrong, inaccessible, shallow, unsafe, or pedagogically useless.
* **Verification / Tests**: Add failing fixtures for factually wrong claims, ungrounded evidence, inaccessible simulation, invalid distractors, missing captions, weak assessment coverage, and wrong expected simulation outputs.

### TODO 9

* **Priority**: High
* **Area**: Content Generation / Simplification
* **Where**: `products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/generators/ComprehensiveContentGenerator.java` 
* **What to do**: Replace ignored `Object llmGateway`, `Object embeddingService`, and `Object qualityValidator` constructor parameters with typed ports and actually use them for generation, retrieval/grounding, semantic validation, quality scoring, and provenance capture. Record real generation duration instead of returning `0L`.
* **Why**: Ignored dependencies create false architecture confidence and prevent observable, grounded, quality-controlled generation.
* **Verification / Tests**: Add constructor type tests, orchestration tests with typed fake ports, quality-validator invocation tests, provenance tests, and generation-duration assertions.

### TODO 10

* **Priority**: Critical
* **Area**: Content Generation / Production Readiness
* **Where**: `products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/MockContentGenerator.java`; `ContentGenerationLauncher.java`; deployment environment configuration  
* **What to do**: Ensure `MockContentGenerator` cannot be selected in production paths, and make production startup fail if no explicit LLM provider/model configuration is supplied. Keep mock generation available only through test fixtures or explicitly named local-dev profiles.
* **Why**: The launcher currently tolerates missing provider configuration by falling back to local defaults, while the mock generator can produce convincing but fake content if accidentally wired.
* **Verification / Tests**: Add startup configuration tests for `NODE_ENV=production`, deployment smoke tests, and CI checks that fail when mock/default provider wiring is present in production profiles.

---

## Generation Execution / Queueing

### TODO 11

* **Priority**: Critical
* **Area**: Backend / Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/execution-service.ts`; `queue-dispatcher.ts`; `routes.ts`   
* **What to do**: Dispatch newly unblocked dependent generation jobs after each successful job result. After a dependency job completes, call the dispatcher for the same tenant/request and enqueue dependent jobs such as simulation, animation, and evaluation. Use `collectDependencyFailureResults` to fail dependent jobs when dependencies fail.
* **Why**: Current execution starts only initially ready jobs; jobs waiting on claim/explainer dependencies can remain pending forever.
* **Verification / Tests**: Add integration tests for a dependency chain: claim completes → simulation/animation enqueue → assessment completes → evaluation enqueue → request completes.

### TODO 12

* **Priority**: Critical
* **Area**: Backend / Data Integrity / Security
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/routes.ts` `/generation/requests/:requestId/results`; `execution-service.ts` `recordJobResult` / `recordBatchResults`  
* **What to do**: Add tenant-scoped and worker-authenticated result recording. Require worker/service identity, tenantId, generationRequestId, generationJobId, idempotency key, and request/job consistency checks before updating a job. Wrap batch result processing and request counter updates in transactions.
* **Why**: Current result recording updates by `jobId` / `requestId` without tenant ownership validation or idempotency, allowing duplicate increments and cross-tenant/job spoofing risks.
* **Verification / Tests**: Add cross-tenant spoof tests, duplicate callback tests, partial batch failure tests, worker-auth rejection tests, and transaction rollback tests.

### TODO 13

* **Priority**: High
* **Area**: Backend / Queueing / Production Readiness
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/queue/content-generation-queue.ts`; `queue-dispatcher.ts`  
* **What to do**: Separate `QUEUED` and `RUNNING` job states. Mark jobs queued after BullMQ enqueue, then mark running only from the worker when execution starts. Also hard-fail queue-disabled mode outside test/local-dev profiles.
* **Why**: Jobs are currently marked `RUNNING` immediately after enqueue, and a disabled/no-op queue can make jobs appear active even though no worker will execute them.
* **Verification / Tests**: Add queue lifecycle tests, worker-start status transition tests, and production environment tests that reject `CONTENT_QUEUE_DISABLED=true`.

### TODO 14

* **Priority**: Medium
* **Area**: Backend / Streaming / Observability
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/routes.ts` SSE routes 
* **What to do**: Make generation SSE streams robust and useful. Add guarded JSON parsing for Redis messages, malformed-message logging, tenant/request authorization, and real job notification subscriptions for `/generation/notifications/stream` instead of only connect/ping events.
* **Why**: Current streaming can break on malformed Redis payloads and the notification endpoint does not deliver actual generation status updates.
* **Verification / Tests**: Add SSE tests for snapshot, job result, malformed Redis message, heartbeat, close cleanup, tenant isolation, and notification delivery.

---

## AI Tutor / AI Governance

### TODO 15

* **Priority**: Critical
* **Area**: AI / Frontend / Learning Model
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/AITutorPage.tsx`; `products/tutorputor/apps/tutorputor-web/src/api/aiTutorGrounding.ts`  
* **What to do**: Replace hardcoded tutor grounding values such as `current-module`, `current-claim`, and `ai-tutor-current-session` with actual active module ID, content block ID, claim IDs, current simulation state, recent attempts, confidence values, misconception IDs, and allowed help mode from learner context.
* **Why**: Tutor responses cannot be truly grounded or adaptive when the frontend sends placeholder context.
* **Verification / Tests**: Add AI tutor E2E tests from a module page with an active simulation and recent assessment attempts, verifying request payload contains real module, claim, simulation, and attempt data.

### TODO 16

* **Priority**: High
* **Area**: AI / Security & Privacy
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/routes.ts`; `governance.ts`; `AIAuditService` 
* **What to do**: Use actual consent and safety-filter outcomes in AI governance metadata instead of setting `consentState: "granted"` and `safetyFilterResult: "passed"` before model execution. Redact or minimize AI audit payloads so raw learner questions, simulation state, attempts, and misconceptions are not stored unnecessarily.
* **Why**: AI governance/audit logs must reflect actual policy decisions and protect learner privacy, especially for minors and educational records.
* **Verification / Tests**: Add tests for missing AI consent, blocked unsafe prompt, PII redaction, audit payload minimization, and safety-filter provenance.

### TODO 17

* **Priority**: High
* **Area**: AI / Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/ai/AIContentGenerationService.ts`; AI routes `/generate-concept` and `/generate-simulation`  
* **What to do**: Replace tutor-query-based concept/simulation generation and regex JSON extraction with a dedicated structured generation client that enforces JSON schema validation, model/prompt provenance, review status, grounding sources, quality score, and failure on invalid/missing required fields.
* **Why**: Current parsing defaults missing values and can accept malformed or shallow AI output as generated content.
* **Verification / Tests**: Add invalid JSON tests, missing required field tests, provenance tests, schema-contract tests, and AI-generation failure tests.

### TODO 18

* **Priority**: High
* **Area**: AI / Assessment
* **Where**: `products/tutorputor/services/tutorputor-platform/src/clients/ai-client.ts` `gradeResponse` 
* **What to do**: Replace hardcoded single-response grading IDs and synthetic confidence with a real grading contract that passes assessmentId, itemId, userId, rubric, model answer, learner response, context, confidence selection, and expected max points. Return rubric-level scores, model/version metadata, confidence score, human-review flag, and calibration inputs.
* **Why**: Current grading loses assessment identity, ignores key rubric/context fields, and fabricates confidence, making grading non-auditable and unsuitable for mastery decisions.
* **Verification / Tests**: Add AI grading contract tests, rubric-score tests, human-review threshold tests, and audit/provenance tests.

### TODO 19

* **Priority**: High
* **Area**: AI / Security
* **Where**: `products/tutorputor/services/tutorputor-platform/src/clients/ai-client.ts`; `products/tutorputor/services/tutorputor-platform/src/modules/ai/index.ts`  
* **What to do**: Require TLS/mTLS or approved service-mesh credentials for AI gRPC calls and AI proxy calls in production. Keep insecure local channels only behind explicit local-dev configuration.
* **Why**: AI calls may include learner context and educational records; insecure service-to-service transport is not acceptable for production.
* **Verification / Tests**: Add production config tests rejecting insecure gRPC/proxy settings and integration tests for secure-channel configuration.

---

## Assessment / Evaluation

### TODO 20

* **Priority**: Critical
* **Area**: Assessment / Frontend / Backend
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/AssessmentDetailPage.tsx`; backend assessment APIs; `Assessment`, `AssessmentItem`, `AssessmentAttempt` Prisma models  
* **What to do**: Replace placeholder assessment fetch/start/submit behavior with real API-backed assessment attempts. Load assessment items from backend, create persisted attempts, submit responses, grade server-side, store feedback, and render results.
* **Why**: Current page returns empty placeholder assessments, creates client-only fake attempts, submits nothing, and navigates to results without grading.
* **Verification / Tests**: Add API E2E tests for start/submit/grade attempts and Playwright tests for learner completing multiple-choice and short-answer assessments.

### TODO 21

* **Priority**: Critical
* **Area**: Assessment / Data Model / Frontend
* **Where**: `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma` `AssessmentAttempt`; `AssessmentDetailPage.tsx`; backend grading services  
* **What to do**: Implement confidence-based marking end-to-end. Add per-item confidence to attempt responses, scoring multipliers/penalties, calibration gain, Brier score or equivalent, required confidence UI, backend scoring, dashboard reporting, and review feedback.
* **Why**: TutorPutor’s evaluation model requires confidence calibration, but current schema and assessment UI do not capture or score confidence.
* **Verification / Tests**: Add unit tests for CBM scoring rules, API tests rejecting submissions without confidence, and UI tests showing confidence/correctness review.

### TODO 22

* **Priority**: High
* **Area**: Assessment / Simulations / Learning Evidence
* **Where**: `AssessmentItem` / `AssessmentAttempt` schema; generated assessment outputs; `AssessmentDetailPage.tsx`; simulation renderer/runtime   
* **What to do**: Add simulation-based assessment item support. Assessment items must be able to reference a simulation manifest, seed, target state, required learner action, captured state, process features, and grading tolerance.
* **Why**: Assessment currently measures static answers only and cannot capture process evidence or simulation mastery.
* **Verification / Tests**: Add simulation-assessment fixtures, seeded state replay tests, grading tolerance tests, and UI tests for completing a simulation-based item.

### TODO 23

* **Priority**: High
* **Area**: Assessment / Micro-Viva
* **Where**: Prisma schema; instructor dashboard; assessment service; calendar/scheduling integration; recording metadata storage 
* **What to do**: Implement micro-viva/oral-defense workflow with random and anomaly-triggered selection, scheduling, rubric, recording URL, remediation on failure, and instructor queue.
* **Why**: The new evaluation model requires oral verification for integrity and transfer, but current schema/UI does not support viva scheduling or rubrics.
* **Verification / Tests**: Add selection algorithm tests, scheduling tests, rubric submission tests, recording metadata tests, and instructor-dashboard E2E tests.

---

## Learner App / Module Delivery

### TODO 24

* **Priority**: Critical
* **Area**: Frontend / Learning Model / Simulations
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/ModulePage.tsx`; CMS block types; module API contracts 
* **What to do**: Replace the local limited `ContentBlock` renderer with a canonical block-renderer registry that supports all published block types: text, video, quiz, interactive, exercise, simulation, animation, assessment, AI tutor prompt, exportable chart, and embedded evidence/task blocks.
* **Why**: Current learner module UI renders only `text` and `exercise`; all other block types fall back to “not yet rendered,” so generated/CMS content cannot be consumed correctly.
* **Verification / Tests**: Add renderer unit tests and Playwright tests for a module containing every supported block type.

### TODO 25

* **Priority**: Critical
* **Area**: Learning Model / Analytics / Frontend
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/ModulePage.tsx`; `Enrollment`; `LearningEvent`; progress update APIs  
* **What to do**: Replace manual “Mark Step Completed” progress increments with evidence-based progress. Progress must be derived from completed content blocks, simulation captures, assessment evidence, time-on-task telemetry, and mastery thresholds.
* **Why**: Current progress increases by `+10` and `+60 seconds` per click, which rewards clicks instead of learning.
* **Verification / Tests**: Add progress computation tests, telemetry ingestion tests, and E2E tests proving progress changes only after required learning evidence is produced.

### TODO 26

* **Priority**: High
* **Area**: Frontend / Content Provenance
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/ModulePage.tsx`; module API response; content validation/review storage 
* **What to do**: Persist and surface accurate content provenance: generatedBy, modelId/modelVersion, promptVersion, validatorVersion, validatedAt, review status, reviewer role, source/evidence summary, and last validation result. Do not infer AI generation only from missing `authorId`.
* **Why**: Current UI can imply AI-generated content was reviewed by an automated validation pipeline without receiving validator metadata.
* **Verification / Tests**: Add API contract tests and UI tests for AI-generated, human-authored, human-reviewed, rejected, and stale-validation modules.

---

## CMS / Authoring

### TODO 27

* **Priority**: Critical
* **Area**: Content & CMS / Validation
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/CMSModuleEditorPage.tsx`; `useModuleEditor.ts`; backend `/cms/modules/**` publish route  
* **What to do**: Add mandatory pre-publish quality gates for modules: metadata completeness, objectives, claims/evidence/tasks, required simulation block, assessment coverage, captions/transcripts, accessibility notes, validator pass status, provenance, and review workflow state. Block publish until all required gates pass.
* **Why**: Current publish flow is a confirmation dialog and mutation call, allowing incomplete or invalid learning content to be published.
* **Verification / Tests**: Add CMS publish rejection tests for missing objectives, missing simulation, invalid simulation schema, missing assessment, failed validator, and missing accessibility metadata.

### TODO 28

* **Priority**: Medium
* **Area**: Content & CMS / Frontend
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/cms/useModuleEditor.ts` 
* **What to do**: Replace editing lookup via `/cms/modules?status=DRAFT` and local `find(id)` with a direct `/cms/modules/:moduleId` API. Support editing allowed published/archived states through explicit permissions and versioning.
* **Why**: Fetching only draft lists is fragile, inefficient, and prevents correct editing/version handling for non-draft modules.
* **Verification / Tests**: Add API tests for direct module fetch, UI tests for draft edit, published revision edit, permission denial, and not-found behavior.

### TODO 29

* **Priority**: Medium
* **Area**: Content & CMS / Frontend
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/cms/useModuleEditor.ts` template initialization effect 
* **What to do**: Add `templateData`, `isEditing`, and `templateId` to the draft-initialization effect dependencies and make template initialization idempotent.
* **Why**: Current effect depends only on `existingModule`, so template-based draft initialization can fail when template data arrives asynchronously.
* **Verification / Tests**: Add React Testing Library tests for creating a module from a template and editing an existing module without template overwrite.

### TODO 30

* **Priority**: Critical
* **Area**: Simulations / Content & CMS / Accessibility
* **Where**: `products/tutorputor/apps/tutorputor-web/src/components/cms/SimulationBlockEditor.tsx`; generated simulation schemas; module block contracts 
* **What to do**: Replace arbitrary JSON editing with a typed simulation manifest schema and real validation. Require claimRef, seed, parameter bounds, expected outputs, telemetry config, accessibility notes, keyboard controls, export settings, scoring hooks, and error/failure states. Add a real preview renderer that executes the manifest instead of showing a placeholder.
* **Why**: Current editor can save invalid simulation JSON and its preview does not validate runtime behavior.
* **Verification / Tests**: Add Zod/schema tests, invalid JSON rejection tests, simulation preview tests, keyboard accessibility tests, and CMS-to-module render E2E tests.

### TODO 31

* **Priority**: Medium
* **Area**: UI/UX / Accessibility / Simplification
* **Where**: `products/tutorputor/apps/tutorputor-web/src/components/cms/SimulationBlockEditor.tsx`; `CMSModuleEditorPage.tsx` modal usage  
* **What to do**: Replace raw modal/buttons/inputs with shared accessible `@ghatana/ui` / TutorPutor UI primitives, add focus trap, escape-to-close, ARIA labels, keyboard navigation, and consistent error messages.
* **Why**: CMS authoring UI is a high-risk workflow and must be accessible, consistent, and low-cognitive-load.
* **Verification / Tests**: Add axe-core tests, keyboard-only workflow tests, focus restoration tests, and visual regression snapshots.

### TODO 32

* **Priority**: High
* **Area**: Content & CMS / Type Safety
* **Where**: `CMSModuleEditorPage.tsx`; `useModuleEditor.ts`; `products/tutorputor/apps/tutorputor-web/src/pages/cms/types.ts`; shared contracts  
* **What to do**: Remove `any` casts from CMS module editor and define discriminated union types for all content block payloads. Share these types between Prisma/API/frontend through the TutorPutor contract package.
* **Why**: Untyped CMS payloads allow invalid content to enter the product and defer failures to learner runtime.
* **Verification / Tests**: Add TypeScript compile tests, block payload schema tests, and client/server contract tests.

---

## Data Contracts / Telemetry

### TODO 33

* **Priority**: Critical
* **Area**: Data Integrity / API Contracts
* **Where**: Prisma `ModuleContentBlock` model; CMS block APIs; learner block renderer; generation outputs 
* **What to do**: Replace `blockType String` + generic `payload Json` with a versioned, validated block contract. Use discriminated payload schemas for text, video, quiz, exercise, simulation, animation, AI prompt, assessment, and evidence blocks.
* **Why**: Unvalidated JSON blocks allow CMS/generator/frontend to disagree silently and cause learner-facing rendering failures.
* **Verification / Tests**: Add schema validation tests, migration tests, invalid block rejection tests, and block renderer compatibility tests.

### TODO 34

* **Priority**: High
* **Area**: Telemetry / Analytics / Privacy
* **Where**: Prisma `LearningEvent`; simulation runtime; assessment runtime; analytics dashboards 
* **What to do**: Replace generic `eventType String` + `payload Json` learning events with versioned telemetry contracts including `sessionId`, `runId`, `attemptId`, `contentBlockId`, `schemaVersion`, `source`, consent state, and PII-free payload guarantees.
* **Why**: Current event shape is too generic to reliably support simulation evidence, CBM, dashboard metrics, privacy deletion, or replay/debugging.
* **Verification / Tests**: Add telemetry schema tests for `sim.start`, `sim.control.change`, `sim.snapshot`, `sim.capture`, `assess.answer`, `assist.hint`, privacy redaction tests, and dashboard reconciliation tests.

### TODO 35

* **Priority**: High
* **Area**: API Contracts / Error Handling
* **Where**: `products/tutorputor/api/tutorputor-api.openapi.yaml`; `generation/routes.ts`; `ai/routes.ts`   
* **What to do**: Normalize all backend error responses to the documented canonical envelope with `error.code`, `error.message`, `details`, `traceId`, `timestamp`, and `statusCode`.
* **Why**: Current routes often return simple `{ error: ... }`, which breaks consistent frontend handling and observability.
* **Verification / Tests**: Add contract tests for 400/401/403/404/422/429/500 responses across generation, AI, CMS, assessment, and learning routes.

---

## Mobile / Offline / Deferred Features

### TODO 36

* **Priority**: Medium
* **Area**: Frontend / Product Readiness
* **Where**: `products/tutorputor/README.md`; `products/tutorputor/apps/tutorputor-mobile/**`; production navigation/marketing/routes 
* **What to do**: Add an explicit production feature gate for mobile learner flows until mobile reaches parity. Document and enforce which mobile routes/screens are non-production, and prevent production UI from implying mobile is ready.
* **Why**: README says mobile is not production-ready, but product surfaces must not expose incomplete learner channels.
* **Verification / Tests**: Add route visibility tests, feature-flag tests, and mobile parity checklist tests in CI.

### TODO 37

* **Priority**: High
* **Area**: Offline / Learning Model / Reliability
* **Where**: `products/tutorputor/README.md`; `products/tutorputor/apps/tutorputor-web/src/sw.ts`; offline sync scripts and progress/assessment/simulation APIs 
* **What to do**: Complete offline behavior beyond partial service-worker caching. Support offline module consumption, simulation state capture, assessment attempt persistence, queued progress mutations, conflict resolution, consent-aware telemetry sync, and retry visibility.
* **Why**: TutorPutor’s low-bandwidth/offline promise requires reliable learning-state sync, not just asset caching.
* **Verification / Tests**: Add offline Playwright tests for module progress, simulation capture, assessment submission, reconnect sync, conflict resolution, and privacy deletion after sync.

### TODO 38

* **Priority**: Medium
* **Area**: Frontend / Product Readiness / Simplification
* **Where**: `products/tutorputor/README.md`; `products/tutorputor/apps/tutorputor-web/src/pages/vr/VrLabsPage.tsx`; `products/tutorputor/apps/tutorputor-web/src/pages/vr/VrSessionPage.tsx` 
* **What to do**: Hide, remove, or clearly feature-flag VR/WebXR pages because README states VR/WebXR is deferred indefinitely. Replace any visible VR routes with clear “deferred/not available” product copy or remove from navigation.
* **Why**: Deferred premium/immersive features should not appear as production-ready learner functionality.
* **Verification / Tests**: Add route/nav tests ensuring VR pages are hidden unless an explicit experimental flag is enabled.

---

## Billing / Marketplace

### TODO 39

* **Priority**: Medium
* **Area**: Backend / Data Model / Product Alignment
* **Where**: Prisma billing models `StripeCustomer`, `Subscription`, `PaymentMethod`, `Invoice`, `Transaction`, `WebhookEvent`; marketplace checkout code and docs 
* **What to do**: Decide and enforce the canonical billing architecture. If Stripe is the target, update product docs and contracts to remove PSP-agnostic/Kill Bill expectations. If PSP-agnostic billing is still desired, abstract Stripe-specific models behind generic billing/customer/subscription/payment provider entities.
* **Why**: Current schema directly embeds Stripe concepts, which conflicts with a portable marketplace/institutional billing direction.
* **Verification / Tests**: Add billing contract tests, provider abstraction tests or documentation consistency tests, and migration tests if schema is generalized.

---

## Security / Privacy / Compliance

### TODO 40

* **Priority**: High
* **Area**: Security & Privacy / AI
* **Where**: AI audit logging in `products/tutorputor/services/tutorputor-platform/src/modules/ai/routes.ts`; `AIAuditService`; Prisma audit models 
* **What to do**: Implement AI audit data minimization and retention policy. Store hashes, redacted text, classification labels, and references instead of full raw learner prompts/responses where possible. Add retention/deletion behavior tied to privacy center and consent revocation.
* **Why**: AI tutor and generation routes may process sensitive learner data and educational records.
* **Verification / Tests**: Add privacy deletion tests, consent revocation tests, audit redaction tests, and retention policy tests.

### TODO 41

* **Priority**: High
* **Area**: Security / Backend
* **Where**: `generation/routes.ts`; `ai/routes.ts`; request-context middleware; route guards  
* **What to do**: Standardize role and service authorization across TutorPutor APIs. Ensure generation result callback routes are service-authenticated, AI routes are role-appropriate, learner routes cannot call admin-only generation endpoints, and all decisions are audited.
* **Why**: Current admin guards are present in some routes, but worker callbacks and AI generation flows need explicit service identity and consistent enforcement.
* **Verification / Tests**: Add RBAC matrix tests for learner, teacher, content_creator, admin, superadmin, and worker/service identities.

---

## Tests / CI / End-to-End Verification

### TODO 42

* **Priority**: Critical
* **Area**: Tests / CI / Product Readiness
* **Where**: `products/tutorputor/apps/**`; `products/tutorputor/services/**`; `products/tutorputor/libs/**`; `products/tutorputor/scripts/**`; CI workflows 
* **What to do**: Add one canonical critical-path E2E suite: author creates generation request → plan → execute → worker records results → validation/review → CMS publish → learner opens module → runs simulation → completes assessment with confidence → dashboard updates → teacher reviews evidence.
* **Why**: Current implementation has many separate surfaces, but TutorPutor readiness depends on the full learning/content-generation lifecycle working together.
* **Verification / Tests**: Add Playwright + API E2E suite with seeded test data and CI gate that fails on any broken step.

### TODO 43

* **Priority**: High
* **Area**: Tests / Content Generation
* **Where**: Java generators and validation gate; Node planner/execution services; frontend CMS and module renderer   
* **What to do**: Add golden-data tests for generated claims, evidence, examples, simulations, animations, assessments, quality reports, module blocks, and learner rendering.
* **Why**: Component tests alone cannot prove generated learning content is correct, usable, accessible, and renderable.
* **Verification / Tests**: Add golden fixtures for at least five domains and compare generated output against schema, quality, accessibility, and rendering expectations.

### TODO 44

* **Priority**: High
* **Area**: Tests / Contracts / Simplification
* **Where**: OpenAPI, Prisma schema, TypeScript contracts, Java protobuf contracts, frontend API clients, backend routes   
* **What to do**: Add cross-contract validation that compares OpenAPI paths, backend route registration, generated clients, Prisma models, and gRPC/protobuf payloads for TutorPutor.
* **Why**: Contract drift is already visible between documented and implemented content-generation APIs.
* **Verification / Tests**: Add CI job that fails when an OpenAPI route lacks backend implementation, a frontend client calls an undocumented route, or a persisted payload does not match the shared schema.

### TODO 45

* **Priority**: Medium
* **Area**: DevOps / Observability
* **Where**: generation execution services, AI routes, Java content generation launcher, Prometheus/Grafana configuration, CI scripts   
* **What to do**: Add end-to-end observability for content generation and AI learning flows: correlation ID propagation, generation request/job metrics, model latency, validation failures, queue lag, worker failures, learner telemetry ingest failures, and dashboard alerts.
* **Why**: TutorPutor depends on automated content/AI/simulation pipelines, so failures must be observable before learners or authors experience silent breakage.
* **Verification / Tests**: Add observability gate tests, metrics endpoint tests, trace propagation tests, alert-rule tests, and failure-injection tests for AI/generation/queue failures.
