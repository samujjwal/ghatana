# TutorPutor Content Intelligence Backlog

> Status: execution-ready backlog derived from `CONTENT-GEN-EXPLORE.md` and `CONTENT_SYSTEMS_RECONSTRUCTION.md`
> Scope date: 2026-03-25
> Goal: convert the target architecture into deliverable backlog items with explicit outcomes and acceptance criteria

## 1. Backlog Conventions

This backlog assumes the following execution model:

- Fastify in `services/tutorputor-platform` remains the canonical public API and control plane.
- Java 21 and ActiveJ services handle long-running, high-throughput, or compute-heavy processing.
- Prisma remains the canonical transactional persistence layer during migration.
- BullMQ remains the intake and dispatch backbone until canonical orchestration contracts are fully stabilized.

Delivery labels:

- `P0` stabilize current production path
- `P1` establish canonical content model
- `P2` deliver hybrid discovery
- `P3` deliver governed generation
- `P4` close the telemetry and regeneration loop
- `P5` remove transitional legacy shapes

Suggested owner labels:

- `Platform-TS`
- `Platform-Java`
- `Admin-UI`
- `Learner-UI`
- `Data`
- `Infra`

## 2. Epic Overview

### Epic A: Stabilize Current Surfaces

Goal: remove known correctness, typing, and route drift issues before major architectural change.

### Epic B: Canonical Content Asset Foundation

Goal: establish a single forward content model and migrate core content reads and writes onto it.

### Epic C: Hybrid Discovery

Goal: replace lexical-only discovery with canonical asset search, semantic indexing, and recommendation primitives.

### Epic D: Governed Generation

Goal: shift generation from loosely governed queue work to planner-based, evaluated, manifest-first pipelines.

### Epic E: Feedback And Regeneration

Goal: wire learner and authoring signals back into ranking, evaluation, and regeneration.

### Epic F: Legacy Removal And Cutover

Goal: remove transitional APIs, duplicate root aggregates, and low-value artifact paths after cutover.

## 3. Backlog Items

## P0.1 Canonical Explorer Route Cleanup

### Goal

Make learner discovery route behavior correct and canonical before adding richer search capabilities.

### Scope

- Keep one canonical learner explorer entry point.
- Remove or redirect placeholder `/content` and `/content/explore` behavior to the canonical search surface.
- Ensure all search result navigation uses slug-based module routing.
- Remove dead lazy-route divergence.

### Primary Areas

- `products/tutorputor/apps/tutorputor-web`
- `products/tutorputor/services/tutorputor-platform/src/modules/search`

### Dependencies

- None

### Acceptance Criteria

- Search results navigate to the correct learner detail route using canonical slugs.
- No learner-facing placeholder explorer page remains in the active route graph.
- Route tests cover canonical explorer behavior and alias handling.
- Search results page, search hooks, and router configuration are consistent.

### Necessary Details

- Preserve current public functionality while removing route ambiguity.
- This item must finish before recommendation rails or hybrid discovery ship.

## P0.2 Content Studio Type And Status Convergence

### Goal

Eliminate drift between contracts, route handlers, and persistence states in Content Studio.

### Scope

- Remove remaining `any`-style placeholders from studio routes and service boundaries.
- Align experience workflow states across contracts, UI mapping, and service logic.
- Ensure admin status rendering matches real persisted states.

### Primary Areas

- `products/tutorputor/services/tutorputor-platform/src/modules/content/studio`
- `products/tutorputor/contracts/v1`
- `products/tutorputor/apps/tutorputor-admin`

### Dependencies

- None

### Acceptance Criteria

- Studio route inputs and service interfaces use explicit types.
- Contract status values match the real persisted lifecycle.
- Admin UI does not expose phantom states.
- Type-check failures introduced by status drift are removed.

### Necessary Details

- This is a correctness and maintainability item, not a feature item.
- No new backward-compatibility statuses should be introduced.

## P0.3 Authoring Lifecycle Event Instrumentation

### Goal

Make authored-content lifecycle events observable and queryable.

### Scope

- Persist `ExperienceEvent` or canonical successor events for create, update, validate, publish, artifact link, review decision, and archive flows.
- Surface recent lifecycle events through admin analytics endpoints.
- Standardize event metadata shapes.

### Primary Areas

- `products/tutorputor/services/tutorputor-platform/src/modules/content/studio`
- Prisma schema and analytics query paths
- `products/tutorputor/apps/tutorputor-admin`

### Dependencies

- `P0.2`

### Acceptance Criteria

- Every major authoring state transition writes an event record.
- Admin analytics can display recent authoring activity per experience.
- Event payloads are stable enough to support future telemetry and regeneration logic.
- Tests cover event writes for core authoring actions.

### Necessary Details

- Reuse existing event concepts where possible.
- This item is required before closed-loop authoring analytics can be trusted.

## P0.4 Java Heavy-Processing Boundary Definition

### Goal

Make the TypeScript control plane and Java execution plane explicit and implementation-ready.

### Scope

- Define the internal gRPC contract between `tutorputor-platform` and `tutorputor-content-generation`.
- Identify which job families remain in BullMQ and which execution steps move to Java.
- Define request, response, progress, and failure payloads.
- Define observability and correlation-id propagation across the boundary.

### Primary Areas

- `products/tutorputor/services/tutorputor-platform`
- `products/tutorputor/services/tutorputor-content-generation`
- `products/tutorputor/libs/content-studio-agents`

### Dependencies

- None

### Acceptance Criteria

- There is a documented and versioned internal contract for heavy processing.
- Fastify responsibilities and Java responsibilities are unambiguous.
- Correlation, retry, and idempotency expectations are specified.
- The Java services are defined as internal processing components, not a second public API.

### Necessary Details

- Java services must follow repo standards: Java 21, ActiveJ async model, no blocking the event loop, `EventloopTestBase` for async tests.

## P1.1 Canonical ContentAsset Schema

### Goal

Introduce the canonical root aggregate for discoverable and generated content.

### Scope

- Add canonical asset tables such as `ContentAsset`, `ContentAssetRevision`, `ContentBlock`, and `ArtifactManifest`.
- Add quality, review, and indexing state fields needed by discovery and generation.
- Define revisioning semantics.

### Primary Areas

- Prisma schema
- shared contracts
- migration scripts

### Dependencies

- `P0.2`

### Acceptance Criteria

- Canonical asset schema exists and supports typed blocks plus manifest attachments.
- Revision records can represent current `Module`, `LearningExperience`, and manifest-backed artifacts.
- The schema supports discoverability, review state, quality score, and semantic index status.

### Necessary Details

- `Module` and `LearningExperience` are migration inputs, not permanent peer roots.
- Avoid re-encoding the same drift into the new schema.

## P1.2 Asset Backfill Pipeline

### Goal

Populate canonical assets from current production content without preserving dual-root architecture permanently.

### Scope

- Backfill `Module`, `LearningExperience`, `SimulationManifest`, and linked artifacts into canonical assets and revisions.
- Produce mapping records for cutover verification.
- Flag content that cannot be migrated cleanly.

### Primary Areas

- Data migration tooling
- Prisma backfill jobs
- validation scripts

### Dependencies

- `P1.1`

### Acceptance Criteria

- Existing content roots are backfilled into canonical assets.
- Migration reports identify failures, duplicates, and low-quality legacy content.
- Backfilled assets preserve referential links to simulations, animations, claims, and tasks where required.

### Necessary Details

- Temporary dual reads are acceptable during migration.
- Permanent dual writes are not.

## P1.3 Asset-Based Read APIs

### Goal

Move control-plane reads to the canonical content model.

### Scope

- Introduce asset-based Fastify read APIs for search results, asset detail, related assets, and authoring detail.
- Update admin and learner clients to consume asset-based reads.

### Primary Areas

- `services/tutorputor-platform`
- `apps/tutorputor-web`
- `apps/tutorputor-admin`

### Dependencies

- `P1.1`
- `P1.2`

### Acceptance Criteria

- Learner discovery reads can be served from canonical assets.
- Admin authoring reads can resolve canonical asset state and current revision metadata.
- UI consumers do not require direct knowledge of both legacy root models.

### Necessary Details

- Do not expose a permanent `/api/v2` family.
- Keep public routes under canonical `/api/v1` ownership.

## P1.4 Manifest Contract Unification

### Goal

Make examples, simulations, animations, and assessments structurally consistent and machine-validatable.

### Scope

- Define canonical manifest contracts for worked examples, simulations, animations, and assessments.
- Align existing simulation manifest behavior with canonical artifact storage.
- Add manifest validation rules and storage conventions.

### Primary Areas

- shared contracts
- platform validation
- Java heavy-processing services

### Dependencies

- `P1.1`
- `P0.4`

### Acceptance Criteria

- Each artifact class has an explicit manifest schema.
- Validation failures are actionable and block publish when required.
- Manifest-backed artifacts can be persisted and re-read without lossy transformation.

### Necessary Details

- Generated prose alone is not a sufficient artifact format for these content classes.

## P2.1 Semantic Chunking And Embedding Pipeline

### Goal

Add the semantic substrate required for hybrid discovery and retrieval-augmented generation.

### Scope

- Add `SemanticChunk` and `EmbeddingVector` or equivalent storage.
- Build chunk extraction from canonical assets and revisions.
- Generate embeddings through Java heavy-processing services.
- Trigger reindex on publish and regenerate flows.

### Primary Areas

- Prisma schema
- Java content-generation services
- Fastify orchestration hooks

### Dependencies

- `P1.1`
- `P1.3`
- `P0.4`

### Acceptance Criteria

- Published assets generate semantic chunks.
- Embeddings are created and stored through Java processing.
- Reindex can be triggered deterministically from control-plane state changes.
- Discovery and generation retrieval can query the same indexed corpus.

### Necessary Details

- Heavy embedding work belongs in Java services, not in Fastify route handlers.

## P2.2 Hybrid Search Ranking

### Goal

Upgrade search from lexical heuristics to hybrid ranking.

### Scope

- Combine lexical score, semantic similarity, asset quality, and learner-fit inputs.
- Return explanation metadata for ranking.
- Add support for simulation-first and prerequisite-aware discovery.

### Primary Areas

- Fastify discovery module
- Java retrieval services
- learner search UI

### Dependencies

- `P2.1`

### Acceptance Criteria

- Search can combine lexical and semantic signals.
- Result payloads include ranking explanation fields.
- Results support canonical asset types beyond plain modules.
- Learner search tests cover hybrid result rendering and navigation.

### Necessary Details

- Lexical search remains as fallback behavior, not as the target ranking model.

## P2.3 Related Assets And Next-Step APIs

### Goal

Expose navigable learning relationships directly in discovery.

### Scope

- Add related assets, prerequisite suggestions, and next-step recommendation endpoints.
- Populate recommendation edges from rule-based bootstraps first, then learned signals.

### Primary Areas

- Fastify discovery and recommendation APIs
- Java recommendation graph recompute jobs
- learner explorer UI

### Dependencies

- `P1.3`
- `P2.1`

### Acceptance Criteria

- Asset detail can show related content and next-step suggestions.
- Recommendation edges are persisted and queryable.
- Learner UI can render related and next-step rails from canonical APIs.

### Necessary Details

- This item establishes the base recommendation graph, not full personalization maturity.

## P2.4 Explorer UI Consolidation

### Goal

Align the learner explorer with the canonical discovery model.

### Scope

- Refactor learner search and explorer components around canonical assets.
- Add result-type rendering for explainers, simulations, example sets, and assessments.
- Add rails for related content and next-step guidance.

### Primary Areas

- `products/tutorputor/apps/tutorputor-web`

### Dependencies

- `P2.2`
- `P2.3`

### Acceptance Criteria

- The explorer UI uses one canonical discovery flow.
- Result cards support multiple asset classes.
- Related and next-step rails are rendered from live API data.
- Placeholder explorer UX debt is removed.

### Necessary Details

- Avoid reintroducing split explorer pages or duplicate search surfaces.

## P3.1 Generation Planner And Request Model

### Goal

Make generation explicit, typed, and governable before content execution begins.

### Scope

- Add canonical `GenerationRequest` and `GenerationJob` models.
- Implement planning logic that determines asset graph, artifact needs, risk, and review path.
- Persist planning output before generation fan-out.

### Primary Areas

- Fastify generation control plane
- Prisma schema
- Java planning assist where needed

### Dependencies

- `P1.1`
- `P0.4`

### Acceptance Criteria

- Every generation run starts from a persisted generation request.
- Planned outputs and risk metadata are queryable.
- Review routing can depend on planner output.

### Necessary Details

- Planner output must be typed enough to drive downstream Java execution deterministically.

## P3.2 Java Batch Generation Execution

### Goal

Move heavy generation execution into Java services using the repo-standard async model.

### Scope

- Execute claim, example, simulation, animation, and assessment generation in Java.
- Use ActiveJ for async flows and lifecycle management.
- Return typed manifests, scores, diagnostics, and failure details over gRPC.

### Primary Areas

- `products/tutorputor/services/tutorputor-content-generation`
- `products/tutorputor/libs/content-studio-agents`

### Dependencies

- `P0.4`
- `P3.1`
- `P1.4`

### Acceptance Criteria

- Heavy generation jobs no longer depend on ad hoc Node-side generation logic.
- Java services expose typed outputs for all required artifact classes.
- Async Java tests use `EventloopTestBase`.
- Blocking external calls are wrapped with `Promise.ofBlocking(...)` where applicable.

### Necessary Details

- This is the core runtime shift for heavy processing.
- Fastify remains the request owner and status/reporting layer.

## P3.3 Evaluation And Guardrail Scorecards

### Goal

Ensure generated content is evaluated before publish and carries durable quality metadata.

### Scope

- Add evaluation result persistence and scorecards.
- Evaluate claim-evidence-task coherence, structural completeness, safety, accessibility, and manifest validity.
- Return publish recommendation and risk level.

### Primary Areas

- Java evaluation services
- Fastify review and publish flow
- admin review UI

### Dependencies

- `P3.2`

### Acceptance Criteria

- Every generated asset revision has an evaluation record.
- Review and publish flows can consume scorecards directly.
- Low-quality or invalid assets are blocked from auto-publish.

### Necessary Details

- Evaluation depth must move earlier in the pipeline, not only at final publish.

## P3.4 Admin Review And Regeneration Console

### Goal

Give authors and reviewers a first-class control surface for governed generation.

### Scope

- Add review views for generation requests, evaluation results, manifests, and regeneration actions.
- Surface risk, score, provenance, and review decision history.

### Primary Areas

- `products/tutorputor/apps/tutorputor-admin`
- Fastify generation APIs

### Dependencies

- `P3.1`
- `P3.3`

### Acceptance Criteria

- Reviewers can inspect generated outputs and scorecards in one place.
- Approve, reject, and regenerate actions are available on live request state.
- Review decisions are persisted and auditable.

### Necessary Details

- Keep this inside the authoring workflow, not in a detached admin subsystem.

## P4.1 Explorer Telemetry And Ranking Feedback

### Goal

Capture discovery behavior needed to improve ranking and regeneration.

### Scope

- Track impressions, clicks, reformulations, starts, completions, and next-step selections.
- Persist explorer telemetry in canonical event shapes.
- Add ranking feedback intake.

### Primary Areas

- Fastify telemetry APIs
- learner UI instrumentation
- analytics storage

### Dependencies

- `P2.4`

### Acceptance Criteria

- Discovery interactions are recorded for live explorer usage.
- Ranking feedback can be joined to assets and queries.
- Telemetry is available for later regeneration and recommendation jobs.

### Necessary Details

- Use stable event contracts that survive future ranking changes.

## P4.2 Outcome-Aware Recommendation Recompute

### Goal

Use learning outcomes and engagement quality to improve recommendation quality.

### Scope

- Recompute recommendation edges using learner outcomes, pathway context, and interaction quality.
- Run heavy recomputation in Java.

### Primary Areas

- Java recommendation services
- Fastify recommendation serving layer

### Dependencies

- `P2.3`
- `P4.1`

### Acceptance Criteria

- Recommendation recompute jobs can run independently of request-serving paths.
- Recommendation edges incorporate outcome-aware signals.
- Fastify can serve updated recommendation state without embedding heavy compute.

### Necessary Details

- This item should keep rule-based bootstraps available until enough telemetry exists.

## P4.3 Regeneration Candidate Engine

### Goal

Automatically identify content that should be regenerated, repaired, or demoted.

### Scope

- Detect regeneration triggers from poor discovery performance, poor learning outcomes, misconception patterns, stale curriculum, or safety concerns.
- Persist `RegenerationCandidate` records with reasons and priority.

### Primary Areas

- Java drift and regeneration services
- Fastify admin APIs
- admin UI queue views

### Dependencies

- `P4.1`
- `P3.3`

### Acceptance Criteria

- The system can create regeneration candidates automatically.
- Each candidate includes trigger reason, severity, and target asset.
- Admin UI can review and act on candidate queues.

### Necessary Details

- Regeneration is a governed workflow, not blind auto-replacement.

## P4.4 Closed-Loop Publish And Reindex Flow

### Goal

Ensure publish, reindex, recommendation recompute, and telemetry linkage form one coherent loop.

### Scope

- Trigger semantic reindex and recommendation refresh on publish and regenerate.
- Persist index status and recompute status on canonical assets.
- Surface operational status in admin tools.

### Primary Areas

- Fastify publish flow
- Java indexing and recompute services
- admin operational views

### Dependencies

- `P2.1`
- `P4.2`

### Acceptance Criteria

- Publish can trigger downstream indexing and recommendation refresh reliably.
- Asset status includes semantic index state.
- Operators can diagnose stuck or failed downstream processing.

### Necessary Details

- This item is necessary for trustworthy discoverability after generation.

## P5.1 Legacy API Cutover

### Goal

Remove transitional public APIs once canonical asset and generation routes are live.

### Scope

- Deprecate and remove route families that were only transitional, including legacy explorer aliases and superseded generation or studio surfaces.
- Update clients and docs to canonical APIs only.

### Primary Areas

- Fastify routes
- admin and learner clients
- docs

### Dependencies

- `P1.3`
- `P3.4`
- `P2.4`

### Acceptance Criteria

- No client depends on superseded transitional routes.
- Public docs point to canonical APIs only.
- Removed routes are either gone or intentionally short-lived redirects during cutover.

### Necessary Details

- Do not preserve a permanent `/api/v2` public shadow.

## P5.2 Legacy Root Model Removal

### Goal

Complete the forward-only model shift by removing permanent dependence on duplicate root aggregates.

### Scope

- Stop treating `Module` and `LearningExperience` as permanent peer roots.
- Remove duplicate writes and stale read paths.
- Freeze or archive legacy structures only as migration artifacts, then remove them.

### Primary Areas

- Prisma schema
- platform service
- admin and learner data access

### Dependencies

- `P1.2`
- `P1.3`
- `P5.1`

### Acceptance Criteria

- Canonical assets are the source of truth for discoverable and generated content.
- No production write path depends on maintaining dual root aggregates.
- Legacy tables or fields are removed or formally retired.

### Necessary Details

- This is the real completion point of fix-forward convergence.

## P5.3 Low-Value Artifact Path Removal

### Goal

Eliminate artifact generation paths that bypass manifests or governance.

### Scope

- Remove generation and publish paths that output unstructured low-value artifacts where manifest-driven equivalents exist.
- Enforce manifest validation before publish for example, simulation, animation, and assessment classes.

### Primary Areas

- Fastify publish and artifact APIs
- Java generation services
- admin review UI

### Dependencies

- `P1.4`
- `P3.2`
- `P3.3`

### Acceptance Criteria

- Artifact classes with canonical manifests cannot bypass validation.
- Unstructured legacy artifact flows are removed or disabled.
- Publish paths reject low-value outputs that do not meet manifest and evaluation requirements.

### Necessary Details

- This item protects the educational quality bar.

## 4. Cross-Cutting Acceptance Gates

These gates apply across all backlog items:

- Public APIs stay under the canonical Fastify control plane.
- Heavy processing moves into Java services, not into route handlers.
- Async Java tests use `EventloopTestBase`.
- Long-running Java operations avoid blocking the event loop.
- Manifest-driven content remains the standard for examples, simulations, animations, and assessments.
- No permanent backward-compatibility layer is introduced for duplicate root models or public route families.

## 5. Recommended Execution Order

1. `P0.1`
2. `P0.2`
3. `P0.3`
4. `P0.4`
5. `P1.1`
6. `P1.2`
7. `P1.3`
8. `P1.4`
9. `P2.1`
10. `P2.2`
11. `P2.3`
12. `P2.4`
13. `P3.1`
14. `P3.2`
15. `P3.3`
16. `P3.4`
17. `P4.1`
18. `P4.2`
19. `P4.3`
20. `P4.4`
21. `P5.1`
22. `P5.2`
23. `P5.3`

## 6. Done Condition For The Backlog

The backlog is complete only when:

- discovery uses one canonical explorer surface
- content uses one canonical root aggregate
- examples, simulations, animations, and assessments are manifest-driven
- heavy generation, indexing, and evaluation run in Java services
- Fastify remains the canonical public API and control plane
- recommendations are live and outcome-aware
- telemetry drives regeneration and ranking improvement
- transitional routes and duplicate legacy roots are removed
