# TutorPutor Content Explore And Generation

> Status: revised against the live TutorPutor codebase on 2026-03-25
> Scope: content discovery, structured content authoring, generation, evaluation, telemetry, and regeneration
> Intent: define a forward-only architecture that matches the active technology stack and raises the quality bar for intelligent content

## 1. Review Of Current State

TutorPutor already has real content infrastructure, but it is uneven.

What is real today:

- The active runtime for these flows is TypeScript, not Java.
- The main backend is `products/tutorputor/services/tutorputor-platform`, built on Node.js, Fastify, Prisma, Redis, BullMQ, and gRPC-assisted background generation.
- The authoring surface is `products/tutorputor/apps/tutorputor-admin`, built with React, Vite, TanStack Query, and Jotai.
- The learner surface is `products/tutorputor/apps/tutorputor-web`, also React, Vite, TanStack Query, and Jotai.
- Simulation generation and validation already exist through `@tutorputor/simulation` and `/api/sim-author/*`.
- Content Studio is the strongest subsystem. It has real CRUD, claim and task management, publish validation, review flow, analytics reads, and queue-backed claim generation.
- Explorer is the weakest subsystem. Search is mostly lexical and heuristic. Semantic retrieval, recommendation ranking, and a coherent explorer boundary are not implemented yet.
- Generation is real but under-governed. The queue, worker, processors, and persistence exist, but evaluation rigor, retrieval quality, and content governance are not yet strong enough.

What is wrong with the previous version of this document:

- It described the target as if TutorPutor were already organized around separate greenfield services instead of the current Fastify modular monolith.
- It introduced a generic `/api/v2/*` surface that does not match the current route strategy or the need to converge on one canonical API shape.
- It was too abstract about the real aggregates already in the system: `Module`, `LearningExperience`, `LearningClaim`, `ExperienceTask`, `SimulationManifest`, `ClaimSimulation`, `ClaimAnimation`, `ExperienceAnalytics`, and the BullMQ worker flow.
- It allowed the old split between learner-facing `Module` content and authoring-facing `LearningExperience` to linger conceptually instead of making a hard forward move to one canonical content model.
- It talked about semantic search, recommendations, and feedback loops without enough grounding in the current code paths that need to be replaced or consolidated.

This revision fixes that.

## 2. Architectural Decisions

### 2.1 Stack Decision

The content explore and generation system will continue on the active TutorPutor stack:

- Control plane and canonical APIs: Fastify in `tutorputor-platform`
- Persistence and request state: Prisma with PostgreSQL
- Async intake and dispatch: Redis and BullMQ
- Heavy generation execution: Java 21 services over gRPC, centered on `services/tutorputor-content-generation`
- Java async model for heavy services: ActiveJ event loop and Promise-based execution
- Learner UI: React 19, Vite, TanStack Query, Jotai
- Admin UI: React 19, Vite, TanStack Query, Jotai
- Simulation engine: `@tutorputor/simulation`
- Shared contracts and types: `@tutorputor/contracts` and `@tutorputor/core`

This is a hybrid system by design.

- Fastify owns synchronous request handling, tenant-aware APIs, auth, persistence orchestration, and user-facing status reporting.
- Java owns long-running, CPU-heavy, memory-heavy, or high-throughput execution such as batch generation, semantic indexing, recommendation recomputation, quality evaluation, and regeneration pipelines.
- Java services must follow the repo standard: Java 21, ActiveJ, no blocking on the event loop, and `Promise.ofBlocking(...)` for blocking IO.

### 2.2 Fix-Forward Only

This architecture is explicitly fix-forward.

- We do not keep a long-lived split between `Module` and `LearningExperience` as separate top-level content concepts.
- We do not add a second explorer stack beside the current search routes.
- We do not add a shadow `/api/v2/*` system while keeping old routes as permanent peers.
- We do not keep dual-write behavior longer than a cutover window.
- We do not preserve low-value content shapes for compatibility if they block a better structured model.

Short migration windows are acceptable. Permanent backward-compatibility layers are not.

### 2.3 Product Quality Decision

TutorPutor content must optimize for educational value, not output volume.

The system should prefer:

- structured content over freeform blobs
- high-value explainers over generic prose
- evidence-backed claims over broad topics
- manifest-driven simulations and animations over one-off payloads
- data-driven worked examples over decorative examples
- publish gating over low-quality auto-publish
- recommendation quality over recommendation quantity

## 3. North-Star Model

TutorPutor should behave as one closed content intelligence loop:

```text
Authoring and generation
    -> structured assets and manifests
    -> indexing and ranking
    -> learner discovery and interaction
    -> telemetry and evaluation
    -> regeneration and ranking improvement
```

The loop matters because TutorPutor is not just a CMS and not just a search engine. It is a learning-content system whose discovery quality depends on the quality and structure of the assets it generates and curates.

## 4. Canonical Content Model

### 4.1 Canonical Root Aggregate

TutorPutor should move to one canonical aggregate: `ContentAsset`.

`ContentAsset` replaces the long-term split between `Module` and `LearningExperience` as separate top-level concepts.

Core asset types:

- `MODULE`
- `EXPLAINER`
- `EXAMPLE_SET`
- `SIMULATION`
- `ANIMATION`
- `ASSESSMENT`
- `PATHWAY`
- `REFERENCE_PACK`

Each asset is discoverable, reviewable, indexable, and quality-scored.

### 4.2 Asset Composition

Each `ContentAsset` contains a typed ordered composition.

Block types:

- `TEXT_EXPLAINER`
- `WORKED_EXAMPLE_SET`
- `DATA_TABLE`
- `VISUAL_SEQUENCE`
- `SIMULATION_ENTRY`
- `ANIMATION_ENTRY`
- `QUESTION_SET`
- `TASK`
- `REFLECTION`
- `HINT`
- `TUTOR_PROMPT`
- `EVIDENCE_CAPTURE`

This is the core shift away from generic JSON-heavy content blobs.

### 4.3 Pedagogy Model

The evidence-centered structure already present in TutorPutor should stay and become stricter.

- `LearningClaim`: what the learner should know or do
- `LearningEvidence`: what proves the claim
- `LearningTask`: what the learner performs to generate evidence

Every publishable teaching asset must be traceable through claim, evidence, and task alignment.

### 4.4 Artifact Model

Artifacts are first-class and manifest-driven.

Artifact types:

- `EXAMPLE_MANIFEST`
- `SIMULATION_MANIFEST`
- `ANIMATION_MANIFEST`
- `ASSESSMENT_MANIFEST`
- `RUBRIC_MANIFEST`
- `REFERENCE_CONTEXT`
- `TUTOR_CONFIGURATION`

The rule is simple: examples, animations, and simulations must be machine-validated structured artifacts, not only generated prose.

### 4.5 Manifest Strategy

#### Example Manifest

Worked examples should carry:

- learning objective
- problem statement
- givens
- solution steps
- explanation per step
- misconception notes
- difficulty
- data payloads or tables when relevant
- visualization hints

#### Simulation Manifest

Simulation manifests should remain aligned with the existing simulation engine and include:

- domain
- variables and parameter schema
- scenes or states
- learner interactions
- success criteria
- telemetry hooks
- safety or constraint rules

#### Animation Manifest

Animation manifests should include:

- target concept
- scene timeline
- transitions
- annotations
- synchronized narration or captions
- source data bindings
- accessibility metadata

Manifest-first content is non-negotiable if TutorPutor wants high-value reusable learning material.

## 5. Repo-Aligned Target Architecture

The system should evolve inside the existing TutorPutor platform, not beside it.

### 5.1 Backend Consolidation

Create a new slice under:

```text
products/tutorputor/services/tutorputor-platform/src/modules/content-intelligence/
```

This slice should absorb and replace scattered ownership now spread across:

- `modules/search`
- `modules/content/studio`
- `modules/simulation`
- `modules/animation-runtime`
- `modules/content-needs`
- `modules/auto-revision`
- parts of `modules/learning/pathways-service`

Target structure:

```text
content-intelligence/
  common/
  assets/
  discovery/
  recommendations/
  generation/
  orchestration/
  artifacts/
  evaluation/
  telemetry/
  regeneration/
```

This TypeScript module is the control plane. It should not become the long-running execution plane.

### 5.2 Java Heavy Processing Plane

Long-running and heavy processing should be implemented in Java, not in Fastify route handlers and not in ad hoc Node worker logic.

Primary Java execution targets:

- `products/tutorputor/services/tutorputor-content-generation`
- `products/tutorputor/libs/content-studio-agents`

Primary responsibilities for the Java plane:

- large-batch claim, example, simulation, animation, and assessment generation
- semantic chunking and embedding generation
- recommendation graph recomputation
- evaluation, policy checks, and governed publish scoring
- drift analysis and regeneration candidate scoring
- heavy export or packaging workflows for animation and simulation artifacts
- retrieval pipelines that combine knowledge sources, embeddings, and policy checks

Java implementation rules:

- Use ActiveJ for async execution and service lifecycle.
- Keep gRPC as the internal service boundary from the TypeScript platform.
- Avoid blocking the event loop. Wrap blocking model calls, file IO, and external fetches with `Promise.ofBlocking(...)`.
- Reuse platform libraries for observability, governance, AI integration, and testing.

### 5.3 Frontend Alignment

Learner app:

- Keep one canonical discovery surface.
- `SearchResultsPage` becomes the real explorer entry point.
- Remove the conceptual split between `/search`, `/content`, and `/content/explore` once migration is done.
- Add recommendation rails, related assets, prerequisite rails, and next-step panels to the actual learner explorer, not to placeholder pages.

Admin app:

- Keep Content Studio as the authoring home.
- Move generation planning, review, analytics, and regeneration into the authoring workflow instead of creating a disconnected admin subsystem.
- Keep simulation and animation tooling embedded, but backed by stricter manifest contracts.

### 5.4 Worker Alignment

The existing BullMQ worker remains the correct backbone for background job intake and dispatch.

It should be upgraded into a thin orchestration layer, not kept as the place where heavy generation logic lives.

Required job families:

- `plan-generation`
- `generate-claims`
- `generate-examples`
- `generate-simulation`
- `generate-animation`
- `generate-assessment`
- `evaluate-asset`
- `publish-asset`
- `reindex-asset`
- `regenerate-asset`

Dispatch rules:

- Fastify persists the request, enqueues the job, and reports status.
- BullMQ dispatches work and tracks retry, dedupe, and DLQ state.
- Java services perform the heavy execution and return typed artifacts, scores, and diagnostics.

The worker must remain idempotent, typed, retry-safe, and DLQ-backed.

## 6. API Direction

The canonical route family should converge under `/api/v1`.

Do not add a permanent `/api/v2` parallel surface.

### 6.1 Discovery APIs

```text
GET /api/v1/assets/search
GET /api/v1/assets/autocomplete
GET /api/v1/assets/:assetId
GET /api/v1/assets/:assetId/related
GET /api/v1/assets/:assetId/next-steps
GET /api/v1/recommendations/for-learner
GET /api/v1/recommendations/for-goal
```

### 6.2 Generation APIs

```text
POST /api/v1/generation/requests
GET /api/v1/generation/requests/:requestId
POST /api/v1/generation/requests/:requestId/review/approve
POST /api/v1/generation/requests/:requestId/review/reject
POST /api/v1/generation/requests/:requestId/regenerate
```

### 6.3 Artifact Manifest APIs

```text
POST /api/v1/manifests/examples
POST /api/v1/manifests/simulations
POST /api/v1/manifests/animations
PUT /api/v1/manifests/simulations/:manifestId
PUT /api/v1/manifests/animations/:manifestId
```

### 6.4 Evaluation And Telemetry APIs

```text
GET /api/v1/evaluation/assets/:assetId
POST /api/v1/telemetry/explorer/events
POST /api/v1/telemetry/generation/events
POST /api/v1/telemetry/ranking-feedback
POST /api/v1/assets/:assetId/reindex
```

### 6.5 Internal Processing Boundary

Public APIs remain on Fastify.

- Admin and learner apps call Fastify only.
- Fastify persists request state and invokes internal gRPC Java services for heavy processing.
- Java services are internal platform components, not a second public API surface.

### 6.6 Route Migration Rule

`/api/content-studio/*` and `/api/sim-author/*` are transitional surfaces only.

After the new canonical APIs land and the admin and learner apps migrate, the old surfaces should be removed rather than preserved indefinitely.

## 7. Search And Recommendation Architecture

### 7.1 Current Problem

Current search is lexical plus heuristics. That is acceptable as a fallback, not as the target discovery model.

### 7.2 Required Search Modes

- lexical search
- hybrid lexical plus semantic search
- simulation-first search
- pathway-constrained search
- prerequisite-aware search
- learner-goal exploration

### 7.3 Ranking Inputs

Ranking should combine:

- lexical relevance
- semantic similarity
- asset quality score
- learner level fit
- prerequisite fit
- pathway fit
- engagement quality
- completion and mastery outcomes
- freshness
- diversity
- simulation or animation usefulness when the query implies it

### 7.4 Result Contract

A search result should return more than title and snippet.

Minimum result payload:

- asset identity and slug
- asset type
- quality score
- difficulty and grade band
- explanation of why it ranked
- available manifests or artifacts
- related assets
- next-step suggestions

### 7.5 Recommendation Scope

TutorPutor needs a real recommendation engine, not dashboard placeholders.

Recommendation edge types:

- `PREREQUISITE`
- `SIMILAR`
- `NEXT_BEST`
- `GOAL_ALIGNED`
- `REMEDIATION`
- `OFTEN_USED_AFTER`

## 8. Generation Architecture

Generation is split into a control plane and an execution plane.

- Control plane: Fastify routes, Prisma persistence, BullMQ dispatch, review state, and user-visible status
- Execution plane: Java services for heavy retrieval, generation, evaluation, indexing, and regeneration

### 8.1 Generation Stages

#### Stage 1: Plan

- interpret prompt or author intent
- select asset type and asset graph
- determine required artifacts
- estimate cost, risk, and review path

Owner: Fastify control plane, with optional Java planning assist for large compound requests.

#### Stage 2: Retrieve

- fetch reference assets
- fetch pathway context
- fetch standards and curriculum rules
- fetch prior high-performing examples
- fetch learner-outcome and drift signals when regenerating

Owner: Java execution plane.

#### Stage 3: Generate

- claims
- explainers
- worked example manifests
- simulation manifests
- animation manifests
- assessment manifests
- tutor configuration

Owner: Java execution plane.

#### Stage 4: Evaluate

- claim and evidence coherence
- task usefulness
- structural completeness
- pedagogical quality
- factual support
- reading-level fit
- accessibility
- safety
- simulation and animation manifest validity

Owner: Java execution plane, with persisted results returned to Fastify.

#### Stage 5: Review

- auto-approve only for low-risk, high-score assets
- route everything else to human review

Owner: Fastify control plane and admin UI.

#### Stage 6: Publish And Index

- persist asset revision
- persist artifact manifests
- compute semantic chunks
- update embeddings
- refresh recommendation edges

Owner split:

- Fastify persists publication state.
- Java performs chunking, embeddings, and recommendation recomputation.

### 8.2 Quality Gates

Low-value content must not publish.

Examples of rejection reasons:

- generic explanation with no worked structure
- claim without evidence-producing task
- simulation manifest that fails validation
- animation manifest with no timeline or accessibility metadata
- unsupported factual assertions
- duplicated material that adds no instructional value

## 9. Data Model And Schema Direction

### 9.1 New Canonical Tables

- `ContentAsset`
- `ContentAssetRevision`
- `ContentBlock`
- `ArtifactManifest`
- `GenerationRequest`
- `GenerationJob`
- `EvaluationResult`
- `SemanticChunk`
- `EmbeddingVector`
- `ExplorerEvent`
- `RecommendationEdge`
- `RegenerationCandidate`

### 9.2 Migration Rule

`Module` and `LearningExperience` are migration inputs, not permanent peer roots.

Migration sequence:

1. Add canonical asset tables.
2. Backfill existing modules, experiences, simulations, and linked artifacts into assets and revisions.
3. Switch reads and writes in platform, admin, and web to the canonical model.
4. Remove duplicate legacy write paths.
5. Drop superseded tables or freeze them briefly for data verification, then remove them.

There should be no permanent dual-model architecture.

### 9.3 Semantic Storage

TutorPutor should add semantic indexing explicitly.

- `SemanticChunk` stores extracted search and retrieval units.
- `EmbeddingVector` stores model-specific embeddings.
- Every published or regenerated asset triggers reindexing.
- Every search and generation retrieval path uses the same indexed asset corpus.

## 10. Telemetry, Evaluation, And Regeneration

### 10.1 Telemetry Requirements

TutorPutor should track:

- search impressions
- clicks
- query reformulations
- asset starts
- completions
- mastery outcomes
- simulation interaction traces
- animation engagement
- assessment performance
- authoring review decisions
- regeneration reasons

### 10.2 Evaluation Requirements

Every asset revision should carry:

- provenance
- prompt and config hash
- source context references
- model metadata
- quality score breakdown
- risk level
- review status
- publish eligibility
- semantic index status

### 10.3 Regeneration Triggers

Regeneration should trigger from:

- poor discovery performance
- poor learning outcomes
- repeated misconceptions
- simulation failure patterns
- stale curriculum alignment
- safety or quality concerns

Regeneration is not only an author action. It is a controlled system behavior.

## 11. Delivery Plan

### Phase 0: Stabilize The Current Surfaces

- keep the active Fastify monolith and current apps
- finish the cleanup already underway in search and studio typing
- remove dead explorer aliases and route drift
- keep search navigation slug-correct and canonical
- make authoring analytics and lifecycle eventing complete
- verify the existing Java content-generation service is the designated heavy-processing target instead of duplicating that logic in Node

### Phase 1: Introduce Canonical Assets

- add canonical asset tables and revision tables
- backfill `Module`, `LearningExperience`, `SimulationManifest`, and linked artifacts
- move admin and learner reads to asset-based APIs
- align TypeScript platform contracts with the Java gRPC payloads used for heavy processing

### Phase 2: Hybrid Discovery

- add chunking and embedding pipeline
- implement hybrid ranking
- add related assets, prerequisite suggestions, and next-step APIs
- run chunking, embeddings, and graph recomputation in Java services

### Phase 3: Governed Generation

- add generation planner
- convert examples, simulations, and animations to manifest-first outputs
- enforce evaluation gates before publish
- move batch generation and evaluation execution into Java ActiveJ services

### Phase 4: Feedback Loop

- add explorer telemetry and ranking feedback
- connect learner outcomes to recommendation scoring
- create regeneration candidates automatically
- run large-scale regeneration analysis and recommendation recompute in Java

### Phase 5: Remove Legacy Shapes

- remove old route families that were only transitional
- remove dual root content concepts
- remove low-value artifact formats that bypass manifests

## 12. Testing Strategy

### 12.1 Platform Tests

- Vitest unit tests for ranking, planning, evaluators, and manifest validation
- integration tests for request to queue to worker to persistence flow
- integration tests for publish to index to discover flow

### 12.2 Frontend Tests

- React Testing Library tests for learner explorer behavior
- React Testing Library tests for authoring generation and review flows
- route tests for canonical explorer navigation only

### 12.3 End-To-End Tests

- author requests generation
- system produces structured asset and manifests
- evaluation and review complete
- asset becomes discoverable
- learner uses asset and linked simulation
- telemetry records outcome
- regeneration candidate is created when needed

## 13. Definition Of Done

This architecture is not done until all of the following are true:

- discovery uses one canonical learner explorer surface
- content has one canonical root aggregate
- examples, animations, and simulations are manifest-driven
- generation is planner-based and evaluation-gated
- search is hybrid, not lexical-only
- recommendations are real and learner-context aware
- telemetry closes the loop into ranking and regeneration
- old compatibility layers are removed after cutover

## 14. Final Position

TutorPutor should become a structured content intelligence platform built on a deliberate hybrid stack: Fastify, Prisma, Redis, BullMQ, React, the existing simulation engine, and Java 21 plus ActiveJ for long-running heavy processing.

The right move is not to preserve the current split-brain model with more wrappers. The right move is to converge hard on canonical assets, manifest-driven educational materials, hybrid discovery, governed generation, and feedback-driven regeneration, while pushing heavy processing into Java services and keeping Fastify as the canonical request and orchestration layer.

## 15. Execution Backlog

Execution-ready backlog items derived from this document are tracked in `CONTENT_INTELLIGENCE_BACKLOG.md`.

That backlog defines:

- phased backlog items
- goals per item
- acceptance criteria
- implementation scope and dependencies
- Fastify versus Java ownership boundaries

That is the shortest path to high-quality intelligent content exploration and generation.
