# Tutorputor Autonomous Content Generation Roadmap

Date: 2026-04-11

Scope: Tutorputor vision docs, architecture docs, product code, content-generation services, content studio workflows, knowledge-base services, simulation stack, and relevant external standards/research.

## Executive Summary

Tutorputor already has the foundations of a strong autonomous content platform. The strongest parts are:

- a claim/evidence/task authoring spine in `LearningExperience`, `LearningClaim`, `LearningEvidence`, and `ExperienceTask`
- claim-linked modality tables in `ClaimExample`, `ClaimSimulation`, and `ClaimAnimation`
- a canonical asset and manifest system in `ContentAsset`, `ContentBlock`, `ContentAssetRevision`, and `ArtifactManifest`
- a substantially more mature cross-domain simulation stack in `libs/tutorputor-simulation`
- quality-loop, evaluation, review, regeneration, and publish-gating services in the platform

The main blocker is not the absence of data models. The blocker is fragmentation:

- there are multiple content-generation architectures in the repo, and they do not fully agree on contracts
- the worker pipeline expects richer outputs than the active generation services reliably produce
- examples, animations, and evidence are much less structured than simulations
- factual validation is still mostly heuristic or LLM-based, not claim-atom and source grounded
- several docs and UI surfaces describe a more complete system than the code currently provides

The fastest path to "minimal human intervention with high accuracy" is not a greenfield rebuild. It is to converge on a single pattern:

1. plan claims and modality needs
2. ground each claim in authoritative evidence
3. generate typed manifests for examples, animations, and simulations
4. validate with deterministic, retrieval-grounded, and pedagogical checks
5. materialize into canonical assets
6. route only low-confidence or novel cases to humans

The central recommendation is simple:

- make `LearningClaim + LearningEvidence + contentNeeds + ArtifactManifest` the canonical generation contract
- make the simulation subsystem the pattern for all other modalities
- make every published factual statement traceable to evidence or to an explicit domain rule

## How This Review Was Done

Reviewed docs:

- `products/tutorputor/README.md`
- `products/tutorputor/docs/architecture/specs/PRODUCT_SPEC.md`
- `products/tutorputor/docs/architecture/CURRENT_STATE.md`
- `products/tutorputor/docs/architecture/specs/EVIDENCE_BASED_CONTENT.md`
- `products/tutorputor/docs/COMPREHENSIVE_CONTENT_GENERATION_PLAN.md`
- simulation and content-studio docs under `products/tutorputor/docs`

Reviewed code areas:

- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/*`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/*`
- `products/tutorputor/contracts/v1/*`
- `products/tutorputor/contracts/proto/*`
- `products/tutorputor/libs/tutorputor-ai/*`
- `products/tutorputor/libs/content-studio-agents/*`
- `products/tutorputor/libs/tutorputor-simulation/*`
- `products/tutorputor/apps/tutorputor-admin/*`
- `products/tutorputor/apps/content-explorer/*`

Reviewed external standards and research:

- 1EdTech CASE, QTI, LTI, Common Cartridge, Caliper, and AI rubric material
- H5P semantics and declarative content authoring
- PhET simulation design principles
- RAG, Self-RAG, FActScore, exemplar-based expository generation, and cognitive-load research on worked examples and animations

## What Is Real In Code Today

### 1. The strongest Tutorputor foundation is already relational and claim-centric

The Prisma schema contains a solid evidence-based authoring spine:

- `LearningExperience`
- `LearningClaim`
- `LearningEvidence`
- `ExperienceTask`

This is already closer to the desired future than many of the older docs imply. The schema also includes:

- `ClaimExample`
- `ClaimSimulation`
- `ClaimAnimation`

This is a strong conceptual choice. It means Tutorputor already thinks in terms of:

- atomic learning claims
- evidence linked to claims
- multiple pedagogical modalities attached to claims

That is exactly the right abstraction for autonomous content generation.

### 2. Tutorputor already has a canonical asset and manifest layer

The `ContentAsset`, `ContentBlock`, `ContentAssetRevision`, and `ArtifactManifest` models are the best long-term substrate in the product.

This is important because `ArtifactManifest` already gives Tutorputor a place to store:

- typed generated artifacts
- schema version
- claim linkage
- validation status
- generation provenance

This is the natural home for data-driven examples, animations, simulations, and assessments.

### 3. The simulation stack is materially ahead of the rest of the system

The simulation subsystem is the most mature content modality in the product:

- strong contracts in `contracts/v1/simulation/types.ts`
- runtime kernels across multiple domains
- authoring service, prompt packs, validators, and template libraries
- claim-linking and manifest authoring routes

This stack already behaves like a domain engine rather than a one-shot text generator. That is the design pattern the other modalities should copy.

### 4. Content Studio and worker orchestration are real

The platform content-studio service and worker queue are not fake. The product really does have:

- experience creation
- queued claim generation
- follow-up example/simulation/animation generation
- validation and regeneration hooks
- publish/review workflow

The architecture in `services/tutorputor-platform/src/modules/content/studio/service.ts` and `src/workers/content/*` is the most important production path.

### 5. Quality loops and regeneration already exist

The platform has:

- `EvaluationService`
- `GenerationQualityLoopService`
- review services
- regeneration candidate services
- publish services

This means Tutorputor is already designed for closed-loop quality improvement. The missing piece is not workflow plumbing. The missing piece is stronger grounding and stronger artifact contracts.

## Where Current Reality Breaks Down

### 1. There are multiple incompatible content-generation contracts

There are at least three competing content-generation contract families:

- the platform worker's expectations in `RealContentGenerationClient`
- the richer claim-oriented proto in `libs/tutorputor-ai/src/agents/main/proto/content_generation.proto`
- the separate proto in `services/tutorputor-content-generation/src/main/proto/content_generation.proto`

These are not aligned.

Examples:

- the platform worker expects claim-level `content_needs`
- the richer proto supports `content_needs`, but the generation service does not reliably populate them during `GenerateClaims`
- the separate proto includes `GenerateAnimation`, but it does not match the richer claim-centric contract used by the worker logic
- `RealContentGenerationClient` tries to load proto files from `tutorputor-ai-agents`, which is no longer the real implementation path

This is the most urgent technical issue in the whole stack. Until there is one authoritative contract, content autonomy will be brittle.

### 2. Claim fan-out depends on data that is not consistently generated

The worker pipeline assumes:

- `generate-claims` returns claims
- each claim includes `content_needs`
- the worker can fan out to `generate-examples`, `generate-simulation`, and `generate-animation`

But the active Java gRPC service currently separates:

- `GenerateClaims`
- `AnalyzeContentNeeds`

and the `GenerateClaims` implementation does not reliably fill the `content_needs` field expected by the worker.

Result:

- the architecture is correct in principle
- the runtime behavior is likely incomplete or inconsistent in practice

### 3. Evidence exists as a concept, but not yet as a robust provenance graph

`LearningEvidence` is a good start, but it currently stores:

- `claimRef`
- `type`
- `description`
- `observables`

What it does not yet capture well enough is:

- authoritative source identity
- citation spans or source excerpts
- support strength
- contradiction status
- retrieval timestamp
- evidence freshness
- machine-checkable provenance

For highly autonomous generation, this gap matters more than almost any prompt improvement.

### 4. Knowledge-base validation is promising but still partly heuristic and partly placeholder

The knowledge-base service has a useful architecture:

- external source adapters for Wikipedia, OpenStax, and Khan Academy
- fact verification route
- curriculum lookup route
- content validation route

But the implementation is still mixed:

- assertion extraction is heuristic
- fact verification mostly counts relevant sources
- local knowledge-base search is mocked
- generated examples are placeholder text
- standards lookup is currently mock-backed

This is enough for a prototype quality loop, but not enough for high-confidence autonomous publishing.

### 5. Examples and animations are under-modeled compared to simulations

Examples:

- `ClaimExample` supports multiple examples per claim, which is good
- but the content payload is still open-ended and lightweight
- current generation is prompt-driven and weakly governed

Animations:

- `ClaimAnimation` stores open-ended `config`
- there is a deterministic template-based animation integration service
- selection is mainly keyword and heuristic based
- the system currently enforces one animation per claim

Simulations:

- have richer contracts, better validators, and stronger authoring workflows

This asymmetry is the clearest signal about where the product should go next.

### 6. Validation is mostly structural, not atomic-factual

Current validation is valuable but limited:

- structure and completeness checks
- safety heuristics
- accessibility heuristics
- manifest presence and shape checks
- review and regeneration routing

What is still weak:

- atomic factual precision
- evidence coverage per factual statement
- contradiction detection across sources
- modality-specific pedagogical quality
- domain invariant validation across examples and animations

### 7. UI and docs drift from backend truth

There is visible drift between:

- aspirational docs
- current backend implementation
- actual front-end authoring surfaces

Examples:

- `COMPREHENSIVE_CONTENT_GENERATION_PLAN.md` is stale in places
- `apps/content-explorer` exists but is incomplete
- admin surfaces mix real `content-studio` APIs with older route assumptions
- some screens are still presentation-heavy or mock-heavy

This does not mean the backend is weak. It means the current product surface can mislead prioritization if taken at face value.

## Deep Analysis By Feature

### A. Claims

Current state:

- claims are first-class relational objects
- claims have `claimRef`, `text`, `bloomLevel`, `orderIndex`
- claims already have a `contentNeeds` JSON field

Strengths:

- right granularity
- good basis for task and modality planning
- easy to benchmark, version, and evaluate

Weaknesses:

- claim generation and claim-planning are not reliably separated in the active execution path
- claims are not consistently grounded in authoritative evidence before downstream generation
- claim quality is still mostly prompt quality plus structural validation

Recommendation:

- treat claim generation as a planning task, not a publishing task
- make every claim pass through a claim-planner that produces:
  - normalized claim text
  - pedagogical intent
  - evidence needs
  - modality needs
  - validation rules

### B. Evidence

Current state:

- evidence is represented in the schema
- there is a knowledge-base validation service
- there are routes for fact verification and curriculum alignment

Strengths:

- the product already thinks in terms of evidence, not just explanations
- OpenStax and Khan Academy are directionally better sources than free-form web search

Weaknesses:

- evidence is not yet the source of truth for content generation
- provenance is too weak
- there is no stable evidence bundle per claim
- no robust freshness or contradiction handling

Recommendation:

- make evidence generation and evidence verification explicit pipeline stages
- build a claim-level evidence bundle before example/animation/simulation generation

### C. Examples

Current state:

- examples can be multiple per claim
- examples are stored relationally
- there is a worker processor and generation path

Strengths:

- one-to-many relationship is correct
- examples are naturally claim-linked

Weaknesses:

- example payloads are too loose
- no shared taxonomy for example families, transfer level, misconception coverage, or scaffolding
- no explicit evidence references inside example structures

Recommendation:

- examples should be generated as typed `WorkedExampleManifest` artifacts with:
  - claim and evidence references
  - setup/givens
  - reasoning steps
  - misconception checkpoints
  - transfer variants
  - grade-band adaptations
  - scoring and validation hints

### D. Animations

Current state:

- claim-linked table exists
- animation integration service exists
- template fallback exists

Strengths:

- there is already a useful deterministic fallback path
- the platform can store and retrieve claim-linked animation configs

Weaknesses:

- animation generation is much less mature than simulation generation
- one-animation-per-claim is too restrictive for scale
- configs are not strongly typed enough
- animations are not systematically grounded in claim evidence

Recommendation:

- define `AnimationManifest` as a declarative scene-and-timeline format with:
  - claim and evidence references
  - scene graph
  - segments
  - cueing markers
  - narration hooks
  - interaction controls
  - accessibility metadata
  - validators for pacing, cueing, and coverage

### E. Simulations

Current state:

- strongest subsystem in Tutorputor
- cross-domain universal schema
- runtime kernels
- authoring routes
- template library and prompt packs

Strengths:

- domain engine approach
- reusable abstractions
- better validation and template governance
- realistic path to scale

Weaknesses:

- not fully integrated into the same canonical claim/evidence plan used by the broader content pipeline
- current claim-simulation relationship allows only one simulation per claim

Recommendation:

- use the simulation subsystem as the template for all other modalities
- separate:
  - pedagogical plan
  - domain runtime model
  - presentation manifest

### F. Validation, Review, and Regeneration

Current state:

- strong workflow support
- multiple review states
- regeneration candidate creation
- publish gating

Strengths:

- the platform already supports autonomy with guardrails
- evaluation and regeneration can become very powerful once grounded metrics are added

Weaknesses:

- evaluation signals are too lightweight
- no atomic fact precision score
- no evidence coverage score
- no modality-specific pedagogy scorecards

Recommendation:

- evolve validation from a single layer into a validation mesh

## The Target Architecture

Tutorputor should move to a single canonical pipeline:

`topic/concept -> claim planner -> evidence bundle -> modality planners -> manifest generators -> validators -> materialization -> review/publish`

### Canonical generation stages

#### 1. Claim planner

Input:

- topic
- domain
- grade band
- curriculum context
- available templates and simulation kernels

Output:

- normalized claims
- claim order and dependencies
- pedagogical intent
- `contentNeeds` plan per claim

#### 2. Evidence bundle builder

Input:

- one claim
- domain context
- target grade band

Output:

- evidence bundle with:
  - source metadata
  - excerpts or structured facts
  - support strength
  - freshness
  - contradiction notes
  - confidence

#### 3. Modality planners

One planner per modality, but with a common envelope:

- example plan
- animation plan
- simulation plan

Each should answer:

- is this modality required
- why is it required
- which evidence supports it
- which misconceptions it addresses
- what grade adaptation it needs
- what validators should apply

#### 4. Manifest generators

The LLM should produce manifests, not final presentation prose as the primary artifact.

Primary output types:

- `WorkedExampleManifest`
- `AnimationManifest`
- `SimulationManifest`
- `AssessmentManifest`

Human-readable content should be derived from manifests where possible.

#### 5. Validation mesh

Every artifact should pass:

- contract/schema validation
- domain-rule validation
- evidence coverage validation
- factual precision validation
- pedagogy and accessibility validation
- runtime validation for executable artifacts

#### 6. Materialization

Materialize validated artifacts into:

- `ContentAsset`
- `ContentBlock`
- `ArtifactManifest`
- `ContentAssetRevision`

#### 7. Review and publish

Humans should review:

- low-confidence claims
- conflicting evidence cases
- novel domain patterns
- policy-sensitive or high-risk content

Everything else should be eligible for automated publish or expert-review routing.

## The Data-Driven Representation Tutorputor Needs

The repo already contains most of the right storage primitives. The missing piece is a shared manifest envelope used consistently across modalities.

### Shared manifest envelope

Every generated modality artifact should carry:

- `schemaVersion`
- `manifestType`
- `claimRef`
- `evidenceRefs`
- `objectiveRefs`
- `domain`
- `gradeBand`
- `pedagogicalIntent`
- `prerequisites`
- `misconceptions`
- `provenance`
- `validators`
- `telemetryProfile`

This can live inside existing `ArtifactManifest.manifest` and be validated by JSON Schema plus domain validators.

### Example manifest shape

Example manifests should include:

- example family: real-world, analogy, worked solution, counterexample, case study
- learner goal
- givens
- reasoning steps
- explanation steps
- misconception checkpoints
- transfer prompts
- adaptation rules by grade band
- evidence references
- scoring hints for automated evaluation

### Animation manifest shape

Animation manifests should include:

- scene graph
- entities and labels
- segment boundaries
- cueing rules
- pacing metadata
- accessibility metadata
- optional narration script hooks
- learner controls
- evidence and claim mapping

### Simulation manifest shape

Simulation manifests should include:

- domain entities
- parameters
- invariants
- runtime kernel selection
- scenario presets
- success criteria
- explanation hooks
- evidence and claim mapping
- telemetry schema

### Why this matters

Once examples, animations, and simulations share a manifest envelope:

- generation becomes more consistent
- validation becomes reusable
- editors can be auto-generated from schema
- telemetry becomes comparable across modalities
- domain scaling stops depending on prompt craftsmanship alone

This is the key to low-human-intervention content production.

## Industry Standards And Research Guidance

### Standards and product patterns

#### CASE

[1EdTech CASE](https://www.1edtech.org/standards/case) is the right reference model for standards and competency alignment. CASE provides machine-readable frameworks, unique identifiers, and structured associations across standards, competencies, rubrics, and related learning artifacts.

Implication for Tutorputor:

- claims, tasks, examples, assessments, and simulations should all be taggable to objective identifiers
- Tutorputor's `curriculumAlignment` should evolve toward CASE-compatible objective references

#### QTI

[1EdTech QTI](https://www.1edtech.org/standards/qti/index) exists for interoperable assessment content and results exchange.

Implication for Tutorputor:

- assessments should remain typed manifests and should be exportable toward QTI-aligned representations where useful

#### LTI and Common Cartridge

[1EdTech LTI](https://www.1edtech.org/standards/lti) and [Common Cartridge](https://www.1edtech.org/standards/cc) are useful for packaging and platform interoperability.

Implication for Tutorputor:

- keep Tutorputor's internal representation richer than these standards
- use them as export and integration layers, not as the core authoring model

#### Caliper and xAPI

[1EdTech Caliper](https://www.1edtech.org/standards/caliper) is useful for standardized learning telemetry. xAPI is also relevant as a broader activity-stream pattern for learning events.

Implication for Tutorputor:

- define a common telemetry profile for examples, animations, and simulations
- record comparable learner interactions and outcomes across modalities

#### H5P semantics

[H5P semantics](https://h5p.org/semantics) is a strong reference for declarative, schema-driven authoring. H5P uses semantics definitions to describe accepted input values and generate editors.

Implication for Tutorputor:

- manifest schemas should drive editor generation and validation
- authoring UI should be schema-backed, not page-specific form code

#### PhET

[PhET](https://phet.colorado.edu/en/about) is a strong reference for research-based interactive simulation design. Their principles include making the invisible visible, using multiple representations, guiding attention, and making simulations reusable across educational situations.

Implication for Tutorputor:

- simulations and animations should not just be "cool visuals"
- they should expose causal structure, multiple representations, and guided exploration

#### AI governance

[1EdTech's Generative AI Data Rubric](https://www.1edtech.org/standards/ai-rubric) emphasizes transparency, disclosure, data-source quality, bias controls, and ownership concerns.

Implication for Tutorputor:

- every generated artifact should carry provenance metadata
- review policies should distinguish internal generation, third-party generation, retrieved evidence, and human edits

### Research takeaways

#### Retrieval-augmented generation

[Lewis et al., 2020](https://arxiv.org/abs/2005.11401) showed that retrieval-augmented generation improves factuality and provenance over parametric-only generation.

Implication:

- Tutorputor should retrieve authoritative domain evidence before claim and explanation generation

#### Self-RAG

[Asai et al., 2023](https://arxiv.org/abs/2310.11511) showed that adaptive retrieval plus self-reflection improves factuality and citation accuracy.

Implication:

- Tutorputor should add a critique-and-revise loop instead of relying on one-shot generation

#### FActScore

[Min et al., 2023](https://arxiv.org/abs/2305.14251) proposed atomic-fact scoring against reliable knowledge sources.

Implication:

- Tutorputor should validate generated claims and long-form explanations at the atomic fact level, not only at the whole-document level

#### Exemplar-based expository generation

[Liu and Chang, ACL 2025](https://aclanthology.org/2025.acl-long.1250/) showed that plan-then-adapt generation with exemplars improves factual, coherent expository text generation.

Implication:

- Tutorputor should maintain high-quality exemplar libraries by domain and use them during claim explanation and example generation

#### NL to DSL generation

[Bassamzadeh and Methani, 2024](https://arxiv.org/abs/2408.08335) argues that planning in code or DSLs is more reliable than planning in free natural language because it supports deterministic checks.

Implication:

- manifest generation should be the first-class output, not a secondary export

#### Worked examples and instructional animations

[van Gog, Paas, and Sweller, 2010](https://link.springer.com/article/10.1007/s10648-010-9145-4) summarizes a large body of research showing:

- worked examples are especially effective for novice learners
- animations often need cueing and segmentation to avoid overload
- high guidance helps when complexity is high

Implication:

- Tutorputor should encode cueing, segmentation, and scaffolding directly into manifests and validators

## Concrete Roadmap

## Phase 0: Stabilize The Current Pipeline

Goal: make the existing architecture reliable before adding sophistication.

Actions:

- choose one authoritative gRPC/proto contract under `products/tutorputor/contracts/proto`
- merge the currently split strengths:
  - claim-level `content_needs`
  - explicit `GenerateAnimation`
  - validation RPCs
- update `RealContentGenerationClient` to load that authoritative contract and real package names
- insert `AnalyzeContentNeeds` as an explicit step after `GenerateClaims` if `GenerateClaims` does not itself return populated `content_needs`
- add contract tests between worker expectations and service outputs
- mark the placeholder `services/tutorputor-content-generation` path as deprecated if it is not the real production service
- make animation generation either fully supported end-to-end or explicitly disabled until supported

Files most likely affected:

- `products/tutorputor/contracts/proto/content_generation.proto`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/RealContentGenerationClient.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ClaimGenerationProcessor.ts`
- `products/tutorputor/libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/contentstudio/grpc/ContentGenerationServiceImpl.java`

Success criteria:

- claim generation always yields a valid modality plan
- fan-out jobs are triggered deterministically
- proto loading and package resolution are no longer ambiguous

## Phase 1: Build A Real Evidence Layer

Goal: make claims publishable only when grounded.

Actions:

- extend `LearningEvidence` or add a linked evidence-source record to capture:
  - source type
  - source URL
  - source title
  - excerpt or structured fact
  - support kind
  - credibility
  - retrievedAt
  - freshness and verification state
- replace mock local-knowledge and mock standards paths with real adapters
- persist claim-level evidence bundles before modality generation
- add contradiction and freshness checks
- define domain-specific evidence policies:
  - OpenStax and peer-reviewed sources for STEM
  - vetted curriculum and standards mappings for K-12 alignment

Files most likely affected:

- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/service.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts`
- `products/tutorputor/contracts/v1/*`

Success criteria:

- every claim has evidence coverage
- evidence provenance is queryable
- review can explain why a claim is believed

## Phase 2: Move Examples And Animations To Manifest-First Generation

Goal: make examples and animations as structured as simulations.

Actions:

- define JSON schemas for `WorkedExampleManifest` and `AnimationManifest`
- make generators output manifests first
- route manifests through `ArtifactManifest` validation before materialization
- teach the asset materializer to generate `ContentBlock`s from manifests consistently
- add manifest validators for:
  - claim coverage
  - evidence linkage
  - pacing and cueing
  - misconception coverage
  - grade adaptation

Files most likely affected:

- `products/tutorputor/contracts/v1/artifact-manifests.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/asset/materialization-service.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/manifest-validator.ts`
- example and animation worker processors

Success criteria:

- examples and animations are generated from typed contracts
- admin editors can be schema-backed
- human review focuses on pedagogical quality, not fixing malformed structures

## Phase 3: Converge Modalities Around Shared Planning And Variants

Goal: support one engine across domains and modalities.

Actions:

- define a canonical `contentNeeds` JSON schema in contracts
- make every claim store a modality plan with shared fields
- support variants for simulations and animations instead of enforcing a single artifact forever
- add primary-vs-variant semantics rather than one-row-only semantics
- unify telemetry and success criteria across examples, animations, and simulations

Likely schema evolution:

- add `variantKey` and `isPrimary` to claim-linked modality tables
- relax uniqueness where variants are needed

Success criteria:

- same planner can reason across all modalities
- multiple variants can be tested or adapted per grade band
- AB testing becomes natural instead of custom

## Phase 4: Add Strong Evaluation And Safe Autonomy

Goal: reduce human work without reducing trust.

Actions:

- add atomic fact extraction and FActScore-style evaluation
- benchmark per domain and grade band
- build modality-specific scorecards
- auto-regenerate on low evidence coverage, low factual precision, or poor learner outcomes
- use review queues for novel claims, weak evidence, policy-sensitive domains, and repeated failures

Files most likely affected:

- `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/evaluation-service.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/review/quality-loop-service.ts`
- regeneration and analytics modules

Success criteria:

- auto-publish rate rises only alongside measured trust metrics
- regression tests catch factual and pedagogical drift

## Suggested 30 / 60 / 90 Day Plan

### First 30 days

- unify the proto contract
- fix worker-service contract drift
- make claim-to-content-needs planning deterministic
- add operational telemetry on generation fan-out

### By 60 days

- persist evidence bundles
- wire evidence into publish gating
- move worked examples and animations to manifest-first generation
- schema-drive the admin editor for at least one modality

### By 90 days

- benchmark two pilot domains end to end
- support multi-variant examples and simulations
- add atomic factual scoring and evidence coverage scoring
- enable low-risk auto-publish for validated content

Recommended pilot domains:

- physics
- algebra

Reason:

- strongest combination of existing simulation assets, clear invariants, and strong external source availability

## What Tutorputor Should Not Do

- do not rely on a single LLM prompt as the source of truth
- do not make UI components the canonical content model
- do not publish factual content that cannot be traced to evidence or domain rules
- do not treat animations as only visual decoration
- do not keep duplicate proto contracts and duplicate generation services indefinitely

## Final Recommendation

Tutorputor does not need a brand-new content-generation architecture. It needs convergence.

The best path is:

- keep the current claim/evidence/task spine
- make evidence first-class and source-grounded
- make modality planning explicit
- make manifests the primary generation output
- copy the simulation subsystem's rigor into examples and animations
- use validation and quality loops to reserve human attention for exceptions, not routine authoring

If this is done well, Tutorputor can realistically move toward:

- lower human authoring effort
- higher factual trust
- stronger reuse across domains
- a single engine for examples, animations, and simulations
- much more reliable content production at scale

## External Sources

- [1EdTech CASE](https://www.1edtech.org/standards/case)
- [1EdTech QTI](https://www.1edtech.org/standards/qti/index)
- [1EdTech LTI](https://www.1edtech.org/standards/lti)
- [1EdTech Common Cartridge](https://www.1edtech.org/standards/cc)
- [1EdTech Caliper](https://www.1edtech.org/standards/caliper)
- [1EdTech Generative AI Data Rubric](https://www.1edtech.org/standards/ai-rubric)
- [H5P Semantics Definition](https://h5p.org/semantics)
- [PhET About Page](https://phet.colorado.edu/en/about)
- [Lewis et al., Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks](https://arxiv.org/abs/2005.11401)
- [Asai et al., Self-RAG](https://arxiv.org/abs/2310.11511)
- [Min et al., FActScore](https://arxiv.org/abs/2305.14251)
- [Liu and Chang, Writing Like the Best: Exemplar-Based Expository Text Generation](https://aclanthology.org/2025.acl-long.1250/)
- [Bassamzadeh and Methani, Plan with Code](https://arxiv.org/abs/2408.08335)
- [van Gog, Paas, and Sweller, Cognitive Load Theory: Advances in Research on Worked Examples, Animations, and Cognitive Load Measurement](https://link.springer.com/article/10.1007/s10648-010-9145-4)
