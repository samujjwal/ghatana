## API Contracts / Backend / Content Generation

### TODO 1

* **Priority**: Critical
* **Area**: API Contracts / Content Generation
* **Where**: `products/tutorputor/contracts/proto/content_generation.proto`, `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/RealContentGenerationClient.ts`
* **What to do**: Fix the TypeScript gRPC client request serialization to match the authoritative proto exactly: `GenerateClaimsRequest.context` must be a nested `RequestContext`, request metadata must go into `context_params`, `GenerateExamplesRequest` must send `example_types` instead of `types`, and `GenerateAnimationRequest` must include `domain`, `grade_level`, and `context`.
* **Why**: The proto declares `RequestContext context`, `context_params`, `example_types`, and animation `domain`/`grade_level`, but the client sends flat request fields and mismatched names, which can drop tenant/request context and break automated content fan-out.  
* **Verification / Tests**: Add proto-encoded request contract tests that invoke an in-process fake `ContentGenerationService` and assert received protobuf payloads for `GenerateClaims`, `AnalyzeContentNeeds`, `GenerateExamples`, `GenerateSimulation`, `GenerateAnimation`, and `ValidateContent`.

### TODO 2

* **Priority**: Critical
* **Area**: Frontend / API Contracts
* **Where**: `products/tutorputor/apps/tutorputor-web/src/api/tutorputorClient.ts`, `products/tutorputor/services/tutorputor-platform/src/plugins/business-modules.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/learning/routes.ts`
* **What to do**: Align learner app API paths with actual platform route prefixes. Fix dashboard, enrollment, pathway, assessment, attempt-submit, gamification, marketplace, credentials, and analytics calls so the client and backend agree on one canonical URL per route.
* **Why**: The web client calls paths such as `/api/v1/learning/dashboard`, `/api/v1/enrollments`, `/api/v1/pathways/*`, and `/api/v1/gamification/*`, while the platform mounts the learning module under `/api/v1/learning` and the learning routes themselves include paths like `/learning/dashboard`, `/enrollments`, `/pathways/*`, `/assessments/:id/attempt`, and `/attempts/:id/submit`; engagement/gamification is mounted under `/api/v1/engagement/gamification`.   
* **Verification / Tests**: Add generated-client contract tests and Playwright/API smoke tests for dashboard load, enroll, update progress, generate pathway, start assessment attempt, submit attempt, fetch gamification progress, fetch credentials, and marketplace listing.

### TODO 3

* **Priority**: Critical
* **Area**: API Contracts / Architecture
* **Where**: `products/tutorputor/api/tutorputor-api.openapi.yaml`, `products/tutorputor/docs/architecture/API_ROUTE_OWNERS.json`, `products/tutorputor/services/tutorputor-platform/src/modules/**/routes.ts`, `products/tutorputor/apps/tutorputor-web/src/api/tutorputorClient.ts`
* **What to do**: Rebuild the OpenAPI contract and `API_ROUTE_OWNERS.json` from the actual platform routes, then generate or validate typed clients from that source. Every public route must have backend owner, contract owner, typed client owner, and test owner.
* **Why**: The product spec says every public OpenAPI route must be mapped to a backend owner, contract owner, typed/generated client, and test owner, but the current route-owner file maps only a few routes and does not cover most web/admin/API surfaces.   
* **Verification / Tests**: Add a CI gate that compares registered Fastify routes against OpenAPI paths and `API_ROUTE_OWNERS.json`, failing if any public route is missing, stale, or has no test owner.

### TODO 4

* **Priority**: Critical
* **Area**: Simulations / Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/SimulationGenerationProcessor.ts`, `products/tutorputor/contracts/proto/content_generation.proto`, `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
* **What to do**: Fix simulation manifest persistence to use proto fields correctly, especially `manifest.title` instead of `manifest.name`; validate the manifest against the simulation schema before persistence; populate `ClaimSimulation.goal` and `successCriteria` from validated manifest success rules instead of `{}` and generic fallback text.
* **Why**: The proto defines `SimulationManifest.title`, but the processor persists `response.manifest.name`; the processor also stores raw manifests without enforcing seeds, parameter bounds, telemetry profile, accessibility metadata, or success criteria, weakening the simulation-first learning model.  
* **Verification / Tests**: Add unit and integration tests for simulation generation using representative math, physics, biology/medicine, engineering, economics, and CS manifests; assert DB rows contain title, domain, validated manifest, goal, non-empty success criteria, and claim linkage.

### TODO 5

* **Priority**: Critical
* **Area**: Offline / Frontend / Data Integrity
* **Where**: `products/tutorputor/apps/tutorputor-web/src/sw.ts`, offline queue/storage hooks in `products/tutorputor/apps/tutorputor-web/src/**`
* **What to do**: Replace the current offline mutation placeholder with a durable IndexedDB-backed queue that stores request body, method, headers required for replay, idempotency key, conflict policy version, created timestamp, and replay status; implement actual background replay and conflict handling for progress, simulation captures, assessment attempts, AI disabled/offline states, and telemetry batches.
* **Why**: The service worker returns `202 queued` for failed mutations but only posts URL/method/timestamp to clients and does not persist or replay the mutation body; `syncPendingMutations()` only emits a client message, so offline progress and assessment writes can be silently lost. 
* **Verification / Tests**: Add Playwright offline tests that disable network, complete a lesson, capture a simulation, submit an assessment draft, record telemetry, restore network, and verify backend state reconciles exactly once.

## Automated Content Generation / Evidence / AI

### TODO 6

* **Priority**: High
* **Area**: Automated Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ExampleGenerationProcessor.ts`, `products/tutorputor/contracts/v1/**`, `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
* **What to do**: Replace loose example JSON persistence with a typed `WorkedExampleManifest` stored through the canonical artifact/manifest layer. Include `schemaVersion`, `claimRef`, `evidenceRefs`, `objectiveRefs`, `domain`, `gradeBand`, `pedagogicalIntent`, `misconceptions`, `provenance`, validators, and telemetry profile.
* **Why**: The roadmap identifies examples as under-modeled compared with simulations, and the current processor stores an open-ended payload with no evidence refs or provenance.  
* **Verification / Tests**: Add schema tests for `WorkedExampleManifest`, golden example fixtures by domain/grade, and worker tests proving generated examples fail persistence if evidence/provenance/schema fields are missing.

### TODO 7

* **Priority**: High
* **Area**: Content Generation / Data Integrity
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ExampleGenerationProcessor.ts`
* **What to do**: Remove `claimExample.deleteMany({ where: { experienceId, claimRef } })` from regeneration flow. Replace it with versioned artifact revisioning that preserves previous generated examples, review decisions, provenance, and learner-performance history.
* **Why**: Deleting all examples for a claim before creating regenerated examples destroys auditability and makes it impossible to compare generated versions or explain why content changed. 
* **Verification / Tests**: Add regeneration tests proving previous examples remain queryable, new examples are marked as the active revision, and review/audit history survives regeneration.

### TODO 8

* **Priority**: High
* **Area**: Evidence / Content Validation
* **Where**: `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`, `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/**`, `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/**`
* **What to do**: Implement claim-level evidence bundles as first-class persisted objects with source type, source URL, title, excerpt/span, support kind, credibility score, contradiction status, retrievedAt, freshness status, verification state, and linkage to each generated claim/artifact.
* **Why**: The roadmap states evidence exists conceptually but does not yet provide robust provenance, freshness, contradiction handling, or machine-checkable source support needed for autonomous content generation. 
* **Verification / Tests**: Add publish-gate tests that reject generated factual claims without verified evidence bundles and add knowledge-base integration tests for supported, contradicted, stale, and unverified evidence cases.

### TODO 9

* **Priority**: High
* **Area**: Content Generation / AI Governance
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ClaimGenerationProcessor.ts`, `products/tutorputor/services/tutorputor-platform/src/config/feature-flags.ts`
* **What to do**: Replace silent default content-needs fallback with an explicit planner failure state and review routing. If `AnalyzeContentNeeds` fails or returns incomplete modality needs, mark the claim/job as degraded, route to human review, and persist diagnostics instead of silently applying generic defaults.
* **Why**: The processor currently falls back to generic examples/simulation needs when analysis fails, which can produce plausible but pedagogically wrong artifacts for autonomous generation. 
* **Verification / Tests**: Add tests where `AnalyzeContentNeeds` fails, returns empty needs, or times out; assert the job is not auto-published and review queue/risk diagnostics are created.

### TODO 10

* **Priority**: High
* **Area**: Domain Model / Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/RealContentGenerationClient.ts`, `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ClaimGenerationProcessor.ts`, `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
* **What to do**: Align domain normalization and default modality planning with the full TutorPutor domain set: `MATH`, `SCIENCE`, `TECH`, `ENGINEERING`, `MEDICINE`, `HEALTH`, `BUSINESS`, `MANAGEMENT`, `ECONOMICS`, `COMPUTER_SCIENCE`, and `INTERDISCIPLINARY`.
* **Why**: Prisma supports the broader product domain model, but the gRPC client collapses several domains to `TECH`, and claim-default content needs only handle a subset of STEM domains.   
* **Verification / Tests**: Add parameterized tests for every `ModuleDomain` proving domain is preserved through API request, gRPC request, claim planning, content-needs planning, and artifact generation.

### TODO 11

* **Priority**: High
* **Area**: Animation / Automated Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/AnimationGenerationProcessor.ts`, `products/tutorputor/contracts/proto/content_generation.proto`, `products/tutorputor/contracts/v1/**`
* **What to do**: Make animation generation manifest-first with a validated `AnimationManifest` that includes claim/evidence/objective refs, scene graph, timeline segments, cueing rules, narration hooks, pacing metadata, learner controls, reduced-motion behavior, captions/text alternatives, provenance, and validators.
* **Why**: The current proto and processor persist basic scenes/cues/raw data, while the roadmap explicitly identifies animations as under-modeled compared with simulations and needing typed manifests, cueing, segmentation, and evidence linkage.   
* **Verification / Tests**: Add animation manifest schema tests, accessibility tests for reduced motion/captions, and worker tests proving invalid animation manifests cannot be persisted.

### TODO 12

* **Priority**: High
* **Area**: Tests / Content Generation
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/__tests__/contract.test.ts`
* **What to do**: Replace weak mocked “integration” tests with real contract tests that compile the proto, start a local in-process gRPC server, assert exact request/response field names, and validate all worker request types against the canonical proto.
* **Why**: The existing test checks for method/message names and uses stubbed clients; it also contains request shapes that do not match the actual TypeScript interfaces, so it would not catch current client/proto drift. 
* **Verification / Tests**: Add failing tests for the current mismatches (`context` vs `RequestContext`, `types` vs `example_types`, missing animation domain/grade), then fix the client until tests pass.

## Learning Model / Assessment / Telemetry

### TODO 13

* **Priority**: High
* **Area**: Assessment / CBM / Micro-Viva
* **Where**: `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`, `products/tutorputor/services/tutorputor-platform/src/modules/learning/assessment-service.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/learning/routes.ts`, `products/tutorputor/apps/tutorputor-web/src/pages/AssessmentDetailPage.tsx`
* **What to do**: Make Confidence-Based Marking first-class in assessment attempts: require confidence per answer, persist item-level confidence, compute CBM score, Brier score, calibration index, and viva-trigger signals, and expose them to learner/instructor dashboards.
* **Why**: The product spec defines CBM and viva as core differentiators, but visible assessment persistence is generic JSON responses/feedback and the submit API does not require confidence.   
* **Verification / Tests**: Add API tests blocking submission without confidence, golden CBM scoring tests, Brier/calibration tests, and Playwright tests for learner answer review with confidence/correctness reflection.

### TODO 14

* **Priority**: High
* **Area**: Telemetry / Analytics
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/learning/routes.ts`, `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`, `products/tutorputor/contracts/v1/**`, `products/tutorputor/apps/tutorputor-web/src/sw.ts`
* **What to do**: Replace untyped learning-event ingestion with versioned JSON schemas for `sim.start`, `sim.control.change`, `sim.snapshot`, `sim.capture`, `assess.answer`, `assist.hint`, offline sync events, and AI tutor events. Enforce no direct PII in event payloads and validate payloads server-side.
* **Why**: The learning event route accepts a generic object with a production schema-refinement comment, and the schema stores `eventType String` plus `payload Json`, which is too weak for evidence-based mastery and privacy-safe analytics.  
* **Verification / Tests**: Add schema validation tests for every telemetry event type, privacy tests rejecting PII fields, and dashboard reconciliation tests comparing raw events to analytics aggregates.

### TODO 15

* **Priority**: High
* **Area**: Learning Model / Frontend
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/DashboardPage.tsx`, `products/tutorputor/services/tutorputor-platform/src/modules/learning/service.ts`, `products/tutorputor/services/tutorputor-platform/src/modules/learning/analytics-service.ts`
* **What to do**: Rework the learner dashboard around actionable mastery evidence: show claim text, weakest claim, evidence events, confidence calibration, simulation attempts needing review, remediation actions, and next best simulation/lesson based on real backend data.
* **Why**: The dashboard has an “Actionable Learner State” panel, but it only displays claim IDs and generic counts; the “Try Simulation” section renders recommended modules rather than actual simulation recommendations tied to claim evidence. 
* **Verification / Tests**: Add component tests and API fixtures showing dashboard states for no evidence, developing mastery, misconception remediation, overdue spaced repetition, viva required, and offline pending sync.

### TODO 16

* **Priority**: Medium
* **Area**: Learning Model / Pathways
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/learning/pathways-service.ts`, `products/tutorputor/apps/tutorputor-web/src/pages/PathwaysPage.tsx`, `products/tutorputor/apps/tutorputor-web/src/api/tutorputorClient.ts`
* **What to do**: Align pathway APIs and UI with the adaptive learning model: diagnostic → learner profile → prerequisite graph → pathway → mastery updates → remediation → next best lesson. Remove or rename client methods that imply non-existent `/v1/pathways/recommend` and `/v1/pathways/enroll` routes unless the backend implements them.
* **Why**: The learner client exposes pathway methods that do not match the backend route set, and TutorPutor’s value proposition depends on adaptive, knowledge-gap-aware progression rather than static path calls.   
* **Verification / Tests**: Add E2E tests for onboarding, diagnostic, pathway generation, module completion, mastery update, remediation recommendation, and pathway advance.

## Frontend / App / UX / Access

### TODO 17

* **Priority**: Critical
* **Area**: Security & Privacy / Frontend
* **Where**: `products/tutorputor/apps/tutorputor-web/src/contexts/AuthContext.tsx`, `products/tutorputor/apps/tutorputor-web/src/api/tutorputorClient.ts`, `products/tutorputor/apps/tutorputor-admin/src/hooks/useAuth.ts`
* **What to do**: Define one canonical tenant/auth context source. Either derive tenant from validated JWT/user session on every request or persist it through one shared, validated auth utility; remove conflicting localStorage behavior and hardcoded key duplication.
* **Why**: The web AuthContext comment says tenantId is derived from JWT and no longer stored in localStorage, yet initialization writes `TENANT_ID_KEY`; the API client still requires `localStorage.getItem("tenant_id")`, creating stale-tenant and tenant-leak risk.  
* **Verification / Tests**: Add auth tests for login, refresh, logout, tenant switch, stale localStorage tenant, missing tenant, and cross-tenant API call prevention.

### TODO 18

* **Priority**: Critical
* **Area**: Security & Privacy / Admin App
* **Where**: `products/tutorputor/apps/tutorputor-admin/src/App.tsx`, `products/tutorputor/apps/tutorputor-admin/src/hooks/useAuth.ts`, `products/tutorputor/apps/tutorputor-admin/src/main.tsx`
* **What to do**: Remove broad admin development bypass from app runtime and move it behind an explicit local-only test fixture or mock server profile. Stop suppressing `Failed` and `404` console errors globally in development.
* **Why**: The admin shell allows unauthenticated/non-admin access in development unless `?requireAuth` is present, `useAuth` can install a seeded admin `dev-token`, and `main.tsx` suppresses error output containing `Failed` or `404`, hiding integration failures during audit and QA.   
* **Verification / Tests**: Add admin route tests for unauthenticated, learner, teacher, admin, and superadmin users; add CI checks that fail if dev bypass is enabled in non-local builds.

### TODO 19

* **Priority**: High
* **Area**: Frontend / Simulations
* **Where**: `products/tutorputor/apps/tutorputor-web/src/router/routes.tsx`, `products/tutorputor/apps/tutorputor-web/src/pages/SimulationStudio*`
* **What to do**: Bring `SimulationStudio` into normal TypeScript compilation or hide it behind a feature flag until it is typed and production-safe. Remove the `@ts-ignore` route import.
* **Why**: The learner route map exposes `/simulations/studio/:id?` while explicitly ignoring TypeScript because the page is experimental, which conflicts with TutorPutor’s simulation-first production quality bar. 
* **Verification / Tests**: Add typecheck coverage, route smoke tests, accessibility tests, and Playwright tests for simulation creation/open/edit flows.

### TODO 20

* **Priority**: High
* **Area**: Admin App / Content & CMS
* **Where**: `products/tutorputor/apps/tutorputor-admin/src/pages/AuthoringPage*`, `products/tutorputor/apps/tutorputor-admin/src/**/UnifiedContentStudio*`, `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/**`
* **What to do**: Complete the pending UnifiedContentStudio refactor into smaller schema-backed authoring components: claim planner, evidence bundle editor, worked example editor, simulation manifest editor, animation manifest editor, assessment editor, validation panel, and publish workflow panel.
* **Why**: The current state document flags UnifiedContentStudio bloat and mock/test references as the remaining high-priority admin issue, while the roadmap requires manifest-first schema-backed authoring for low-human-intervention content generation.  
* **Verification / Tests**: Add per-component tests plus an authoring E2E flow: create topic → generate claims → attach evidence → generate example/simulation/animation → validate → review → publish → preview learner view.

### TODO 21

* **Priority**: Medium
* **Area**: UI/UX / Simplification
* **Where**: `products/tutorputor/apps/tutorputor-admin/src/App.tsx`
* **What to do**: Remove duplicate `settings/marketplace` route definition and audit all legacy redirects to keep only routes that are still required for existing deep links.
* **Why**: The admin router defines `settings/marketplace` twice and carries a large legacy redirect surface, increasing route ambiguity and maintenance cost. 
* **Verification / Tests**: Add route-table tests that assert no duplicate paths and verify each retained legacy route redirects to the intended canonical page.

### TODO 22

* **Priority**: Medium
* **Area**: Frontend / UI/UX / Simplification
* **Where**: `products/tutorputor/apps/tutorputor-web/src/pages/Home.tsx`, `products/tutorputor/apps/tutorputor-web/src/router/routes.tsx`
* **What to do**: Remove the unused placeholder `Home.tsx` or replace it with a real product landing/dashboard entry if `/home` is intended to exist. Keep route behavior consistent with `/dashboard`.
* **Why**: `/home` redirects to `/dashboard`, but a placeholder Home component remains with inline styling and generic copy, creating dead/obsolete UI surface.  
* **Verification / Tests**: Add route coverage confirming `/`, `/home`, and `/dashboard` resolve consistently and no unused placeholder page is bundled.

## Mobile / Offline / Deferred Features

### TODO 23

* **Priority**: High
* **Area**: Mobile / Offline
* **Where**: `products/tutorputor/apps/tutorputor-mobile/**`, `products/tutorputor/apps/tutorputor-mobile/src/config.ts`, `products/tutorputor/README.md`
* **What to do**: Keep mobile explicitly non-production behind release flags until learner app shell, navigation, offline queue, sync conflict handling, auth, telemetry replay, and app-store deployment readiness are completed. Make mobile API URL defaults environment-specific and avoid defaulting production API in local/dev builds.
* **Why**: The README says mobile is in development and not production-ready, while mobile config defaults to a production API URL and enables offline/background sync by default.  
* **Verification / Tests**: Add mobile CI checks for required env config, offline sync unit tests, navigation smoke tests, auth tests, and a release gate that blocks production build if mobile parity flags are false.

### TODO 24

* **Priority**: Medium
* **Area**: VR/WebXR / Product Boundary
* **Where**: `products/tutorputor/docs/architecture/VR_WEBXR_ROADMAP_DECISION.md`, `products/tutorputor/services/tutorputor-vr/**`, `products/tutorputor/apps/**`, `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
* **What to do**: Ensure all VR/WebXR services, schema models, docs, UI links, usage metrics, and deployment scripts are consistently marked deferred or feature-flagged, with no production-facing learner/admin entry points until explicitly reactivated.
* **Why**: The README and current-state docs say VR/WebXR is deferred/not ready, while the product spec and schema still describe VR service/model surfaces.   
* **Verification / Tests**: Add route and deployment tests proving VR/WebXR endpoints/UI are hidden by default and cannot be accessed without the explicit feature flag.

## Platform / Observability / Production Readiness

### TODO 25

* **Priority**: High
* **Area**: Backend / API Error Handling
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/learning/routes.ts`, all `products/tutorputor/services/tutorputor-platform/src/modules/**/routes.ts`, `products/tutorputor/api/tutorputor-api.openapi.yaml`
* **What to do**: Standardize all route errors to the canonical error envelope documented in OpenAPI: `{ error: { code, message, details }, traceId, timestamp, statusCode }`. Replace ad hoc `{ message }` responses in learning/pathway/assessment routes.
* **Why**: OpenAPI documents a canonical envelope, but learning routes return plain `{ message }` in multiple error paths.  
* **Verification / Tests**: Add route error-envelope tests for 400, 401, 403, 404, 409, 429, and 500 responses across learning, content, AI, integration, engagement, credentials, and admin modules.

### TODO 26

* **Priority**: High
* **Area**: Content Validation / Knowledge Base
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/**`, `products/tutorputor/services/tutorputor-platform/src/modules/content-quality-monitoring/**`, `products/tutorputor/services/tutorputor-platform/src/modules/content-evaluation/**`
* **What to do**: Move content validation from heuristic/source-count validation toward atomic factual validation: extract atomic claims, retrieve authoritative evidence, score support/contradiction, persist evidence coverage, and block auto-publish below threshold.
* **Why**: Current state says autonomous content generation is not ready without human-in-the-loop, semantic validation is heuristic, and vector/source-grounded validation remains a gap.  
* **Verification / Tests**: Add golden datasets for algebra and physics with correct, partially correct, contradicted, unsupported, and stale claims; assert validation scores and publish decisions.

### TODO 27

* **Priority**: Medium
* **Area**: Payments / Infrastructure
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/payments/**`, tax-rate cache/storage code, `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
* **What to do**: Replace placeholder tax-rate caching with database-backed cache entries keyed by jurisdiction/product/tax category/effective date, including TTL, source metadata, and refresh/audit behavior.
* **Why**: The current-state doc identifies Stripe Tax integration as partial and explicitly says the caching framework is still placeholder and needs database-backed cache. 
* **Verification / Tests**: Add tests for cache hit, cache miss, stale refresh, Stripe API failure fallback, audit entry creation, and tenant isolation.

### TODO 28

* **Priority**: Medium
* **Area**: DevOps / Observability
* **Where**: `products/tutorputor/services/tutorputor-platform/src/workers/content/**`, `products/tutorputor/services/tutorputor-platform/src/monitoring/**`, `products/tutorputor/ci/**`
* **What to do**: Add end-to-end observability for content-generation jobs: correlation ID, generation request ID, job ID, claimRef, model, prompt hash, token/cost, latency, validation score, review path, retry count, and final artifact IDs across logs, metrics, traces, and SSE updates.
* **Why**: The platform has tracing and worker telemetry, but automated content generation must be auditable across planning, generation, validation, persistence, review, and publish to support safe autonomy.  
* **Verification / Tests**: Add integration tests that execute a generation request and assert trace/log/metric/SSE records contain the same correlation IDs and final artifact IDs.

## Code Quality / Reuse / Simplification

### TODO 29

* **Priority**: High
* **Area**: Simplification / Frontend API
* **Where**: `products/tutorputor/apps/tutorputor-web/src/api/tutorputorClient.ts`, `products/tutorputor/apps/tutorputor-admin/src/services/**`, shared API utilities
* **What to do**: Consolidate duplicated API clients and header logic into one generated/shared TutorPutor API client. Remove the parallel `TutorPutorApiClient` and `tutorputorClient` patterns unless there is a documented boundary.
* **Why**: The web app has two separate fetch clients with different base paths, duplicated tenant/header logic, and inconsistent error handling; one checks `response.ok`, the simplified one does not. 
* **Verification / Tests**: Add shared-client tests for auth headers, tenant headers, correlation IDs, canonical errors, JSON parsing, non-JSON errors, and retry/no-retry behavior.

### TODO 30

* **Priority**: Medium
* **Area**: Data Model / Simplification
* **Where**: `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`, social/forum/study-group models, learning-event models
* **What to do**: Replace JSON stored as `String` and untyped event payloads with typed JSON columns or normalized tables where data is queried, filtered, moderated, or validated. Prioritize `StudyGroup.subjects`, `StudyGroup.modules`, forum attachments/categories, learning events, and assessment responses.
* **Why**: The schema uses stringified JSON for several social/collaboration fields and generic JSON for learning/assessment payloads, which weakens validation, querying, moderation, and analytics correctness. 
* **Verification / Tests**: Add migration tests, query/filter tests, moderation tests, telemetry validation tests, and backward data conversion tests.

### TODO 31

* **Priority**: Medium
* **Area**: Tests / Product Coverage
* **Where**: `products/tutorputor/services/tutorputor-platform/src/modules/**/__tests__`, `products/tutorputor/apps/tutorputor-web/tests/**`, `products/tutorputor/apps/tutorputor-admin/tests/**`
* **What to do**: Add missing dedicated tests for social learning, VR/deferred gating, auto-revision, content quality monitoring, content generation benchmark, compliance evidence, data residency, plugin marketplace, and analytics consolidation modules.
* **Why**: The product spec notes many model/service areas have corresponding code but no dedicated test files, and the current product has broad module registration across many platform domains.  
* **Verification / Tests**: Add module-level route/service tests plus one product E2E smoke covering learner, teacher, admin, content author, and institution admin flows.

### TODO 32

* **Priority**: Medium
* **Area**: Documentation / Reality Alignment
* **Where**: `products/tutorputor/README.md`, `products/tutorputor/docs/architecture/CURRENT_STATE.md`, `products/tutorputor/docs/architecture/specs/PRODUCT_SPEC.md`, `products/tutorputor/docs/architecture/specs/AUTONOMOUS_CONTENT_GENERATION_ROADMAP.md`
* **What to do**: Reconcile documentation after fixes so product status, route ownership, mobile readiness, VR/WebXR status, autonomous content-generation readiness, content validation limitations, and known production gates are stated consistently.
* **Why**: Current docs correctly identify several gaps, but the product still has drift between aspirational spec, current-state notes, route contracts, app clients, and implementation.    
* **Verification / Tests**: Add a docs consistency checklist to CI that verifies referenced files exist, route-owner paths match OpenAPI/backend routes, and deferred/not-ready features are not documented as production-ready.
