# Tutorputor Autonomous Content Generation - Detailed Implementation Plan

**Date:** 2026-04-12  
**Based on:** AUTONOMOUS_CONTENT_GENERATION_ROADMAP.md  
**Guidelines:** .github/copilot-instructions.md  
**Status:** Draft

---

## Executive Summary

This document provides a granular, task-level implementation plan for achieving the autonomous content generation vision outlined in the roadmap. Each task includes:
- Clear deliverables
- File locations affected
- Dependencies
- Verification criteria
- Estimation (story points)

**Core Principles Applied:**
- Reuse before creating (platform abstractions preferred)
- Type safety at implementation time (no `any`, strict TypeScript)
- Tests included with every change
- Follow existing module patterns
- Explicit contracts over implicit assumptions

---

## Phase 0: Stabilize The Current Pipeline

**Goal:** Make the existing architecture reliable before adding sophistication.  
**Duration:** 30 days  
**Priority:** P0 - Blocker for all other work

### Task 0.1: Audit and Document Current Contract State

**Deliverable:** Contract alignment audit report  
**Files to Analyze:**
- `products/tutorputor/contracts/proto/content_generation.proto`
- `products/tutorputor/libs/tutorputor-ai/src/agents/main/proto/content_generation.proto`
- `products/tutorputor/services/tutorputor-content-generation/src/main/proto/content_generation.proto`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/RealContentGenerationClient.ts`

**Acceptance Criteria:**
- [ ] Document all message definitions in each proto file
- [ ] Identify field-level differences between contracts
- [ ] Map which services consume which contracts
- [ ] Identify unused/deprecated proto files
- [ ] Produce decision matrix for contract consolidation

**Story Points:** 3  
**Dependencies:** None  
**Owner:** Platform Engineer

---

### Task 0.2: Define Authoritative Contract Location

**Deliverable:** Selected canonical contract path with rationale document  
**Decision Options:**
1. `products/tutorputor/contracts/proto/` (centralized)
2. `products/tutorputor/libs/tutorputor-ai/src/agents/main/proto/` (AI-centric)
3. New location following platform pattern

**Acceptance Criteria:**
- [ ] Document selection criteria (consumer proximity, versioning needs, build integration)
- [ ] Define contract versioning strategy
- [ ] Update build scripts to generate from canonical location only
- [ ] Document migration path for existing consumers

**Story Points:** 2  
**Dependencies:** Task 0.1  
**Owner:** Platform Engineer + Architecture Review

---

### Task 0.3: Merge Proto Contract Definitions

**Deliverable:** Unified `content_generation.proto` with all required RPCs  
**Required RPCs:**
- `GenerateClaims` with `content_needs` output
- `AnalyzeContentNeeds` (explicit planning step)
- `GenerateExamples` with manifest output
- `GenerateAnimation` (currently missing)
- `GenerateSimulation` with existing strong contract
- `ValidateArtifact` for validation mesh integration

**Acceptance Criteria:**
- [ ] Single proto file contains all RPC definitions
- [ ] `ContentNeeds` message includes:
  - `example_count`, `example_types`
  - `animation_required`, `animation_type_hints`
  - `simulation_required`, `simulation_domain`
  - `misconception_coverage_required`
- [ ] All message fields have explicit types (no `google.protobuf.Any` without justification)
- [ ] Proto compiles without warnings
- [ ] Backward compatibility strategy documented (if applicable)

**Files Affected:**
- `products/tutorputor/contracts/proto/content_generation.proto` (canonical location TBD from Task 0.2)

**Story Points:** 5  
**Dependencies:** Task 0.2  
**Owner:** Backend Engineer (gRPC/Java)

---

### Task 0.4: Update RealContentGenerationClient

**Deliverable:** Fixed proto loading and package resolution  
**Current Issue:** `RealContentGenerationClient` loads from incorrect path (`tutorputor-ai-agents`)

**Acceptance Criteria:**
- [ ] Update proto file path to canonical location
- [ ] Fix package name resolution
- [ ] Add explicit error handling for proto loading failures
- [ ] Add startup validation that proto files exist and are readable
- [ ] Type all client methods with explicit input/output types (no `any`)
- [ ] Add unit tests for client initialization
- [ ] Add integration tests for each RPC method

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/RealContentGenerationClient.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/__tests__/RealContentGenerationClient.test.ts` (new)

**Implementation Notes:**
```typescript
// Must follow TypeScript standards from copilot-instructions.md
interface GenerateClaimsRequest {
  topic: string;
  domain: string;
  gradeBand: string;
  curriculumContext?: CurriculumContext;
}

interface GenerateClaimsResponse {
  claims: LearningClaim[];
  contentNeeds: ContentNeeds[];
}

// All methods must have explicit return types
async function generateClaims(
  request: GenerateClaimsRequest
): Promise<Result<GenerateClaimsResponse, ContentGenerationError>>
```

**Story Points:** 5  
**Dependencies:** Task 0.3  
**Owner:** TypeScript Engineer

---

### Task 0.5: Fix ClaimGenerationProcessor ContentNeeds Handling

**Deliverable:** Deterministic content needs planning after claim generation  
**Current Issue:** Worker assumes `content_needs` populated, but `GenerateClaims` doesn't reliably fill it

**Acceptance Criteria:**
- [ ] Add explicit `AnalyzeContentNeeds` step if `GenerateClaims` returns empty content needs
- [ ] Insert workflow state transition: `CLAIMS_GENERATED` -> `ANALYZING_CONTENT_NEEDS`
- [ ] Implement `AnalyzeContentNeedsProcessor` with:
  - Input: `LearningClaim[]`, topic context
  - Output: `ContentNeeds` per claim
- [ ] Add validation that content needs are complete before fan-out
- [ ] Add comprehensive unit tests for processor logic
- [ ] Add integration tests for full workflow

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ClaimGenerationProcessor.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/AnalyzeContentNeedsProcessor.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/workers/content/types/workflow-states.ts` (may need update)

**Story Points:** 5  
**Dependencies:** Task 0.4  
**Owner:** Backend Engineer (TypeScript/Node)

---

### Task 0.6: Implement Contract Tests

**Deliverable:** Contract test suite verifying worker-service compatibility  
**Test Strategy:**
- Consumer-driven contract tests
- Pact or equivalent if available, else manual contract tests

**Acceptance Criteria:**
- [ ] Test that worker expectations match service responses
- [ ] Test all RPC methods with realistic payloads
- [ ] Test error handling paths
- [ ] Test edge cases (empty claims, missing content needs, malformed responses)
- [ ] CI pipeline runs contract tests on PR
- [ ] Tests fail build on contract violation

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/__tests__/contract.test.ts` (new)
- `products/tutorputor/services/tutorputor-content-generation/src/test/contract/` (if this service is active)

**Story Points:** 5  
**Dependencies:** Task 0.5  
**Owner:** QA Engineer + Backend Engineer

---

### Task 0.7: Deprecate or Remove Duplicate Content Generation Service

**Deliverable:** Clean separation of active vs deprecated paths  
**Decision Required:** Is `services/tutorputor-content-generation` still active?

**Acceptance Criteria:**
- [ ] Audit current usage of `services/tutorputor-content-generation`
- [ ] If deprecated:
  - Add deprecation markers to all public APIs
  - Remove from build/deployment pipelines
  - Archive with README explaining migration
- [ ] If still active:
  - Migrate to unified proto contract
  - Document why separate service exists
- [ ] Update all documentation referencing this service

**Files Affected:**
- `products/tutorputor/services/tutorputor-content-generation/` (potential archive)
- `products/tutorputor/docs/` (documentation updates)

**Story Points:** 3  
**Dependencies:** Task 0.6  
**Owner:** Platform Engineer + DevOps

---

### Task 0.8: Animation Generation - Enable or Explicitly Disable

**Deliverable:** Clear operational state for animation generation  
**Current State:** Partially implemented but not end-to-end

**Acceptance Criteria:**

**Option A - Enable:**
- [ ] Complete `GenerateAnimation` RPC implementation
- [ ] Wire through worker pipeline
- [ ] Add basic validation
- [ ] Add tests

**Option B - Disable (Recommended for Phase 0):**
- [ ] Add feature flag `ENABLE_ANIMATION_GENERATION` (default false)
- [ ] Skip animation jobs when flag disabled
- [ ] Log clear message: "Animation generation disabled in Phase 0, enable flag for Phase 2"
- [ ] Document in operational runbook

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/AnimationGenerationProcessor.ts`
- `products/tutorputor/services/tutorputor-platform/src/config/feature-flags.ts` (update or create)

**Story Points:** 2  
**Dependencies:** Task 0.7  
**Owner:** Backend Engineer

---

### Phase 0 Success Criteria Summary

1. **Contract Unification:**
   - Single authoritative proto file
   - All RPCs defined (GenerateClaims, AnalyzeContentNeeds, GenerateExamples, GenerateAnimation, GenerateSimulation, ValidateArtifact)
   - Proto compiles without warnings

2. **Worker Reliability:**
   - `RealContentGenerationClient` loads from correct path
   - `ClaimGenerationProcessor` always produces valid content needs
   - Fan-out jobs trigger deterministically

3. **Test Coverage:**
   - Contract tests pass
   - Unit tests for all processors
   - Integration tests for workflow

4. **Operational Clarity:**
   - Deprecated services clearly marked
   - Animation generation explicitly enabled or disabled
   - No ambiguous proto loading

---

## Phase 1: Build A Real Evidence Layer

**Goal:** Make claims publishable only when grounded.  
**Duration:** 30 days (days 30-60)  
**Priority:** P0 - Core value proposition

### Task 1.1: Extend LearningEvidence Schema

**Deliverable:** Enhanced `LearningEvidence` model with provenance fields  
**Current Schema:** Basic `claimRef`, `type`, `description`, `observables`

**Required Additions:**
```prisma
model LearningEvidence {
  id                  String   @id @default(uuid())
  claimRef            String
  
  // Source identity
  sourceType          EvidenceSourceType
  sourceUrl           String?
  sourceTitle         String
  sourcePublisher     String?
  sourcePublicationDate DateTime?
  
  // Content
  excerpt             String?  // Direct quote or excerpt
  structuredFact      Json?    // Machine-parseable fact representation
  
  // Provenance
  supportKind         SupportKind  // SUPPORTS, CONTRADICTS, NEUTRAL
  credibilityScore    Float?   // 0.0 - 1.0
  retrievedAt         DateTime
  freshnessStatus     FreshnessStatus
  
  // Verification
  verificationState   VerificationState
  contradictionNotes  String?
  
  // Relations
  claim               LearningClaim @relation(fields: [claimRef], references: [claimRef])
  
  // Audit
  createdAt           DateTime @default(now())
  updatedAt           DateTime @updatedAt
  
  @@index([claimRef])
  @@index([sourceType])
  @@index([freshnessStatus])
}

enum EvidenceSourceType {
  OPENSTAX
  KHAN_ACADEMY
  WIKIPEDIA
  PEER_REVIEWED_JOURNAL
  TEXTBOOK
  CURRICULUM_STANDARD
  DOMAIN_EXPERT
  SIMULATION_RESULT
  CALCULATION
}

enum SupportKind {
  SUPPORTS
  CONTRADICTS
  NEUTRAL
  PARTIALLY_SUPPORTS
}

enum FreshnessStatus {
  CURRENT
  STALE
  EXPIRED
  UNKNOWN
}

enum VerificationState {
  UNVERIFIED
  VERIFIED
  DISPUTED
  FAILED_VERIFICATION
}
```

**Acceptance Criteria:**
- [ ] Migration script created and tested
- [ ] Prisma schema updated with all new fields
- [ ] Enums defined with proper documentation
- [ ] Indexes added for query performance
- [ ] Backward compatibility: existing evidence records migrate with defaults
- [ ] Migration tested on staging database

**Files Affected:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/libs/tutorputor-core/prisma/migrations/` (new migration)

**Story Points:** 5  
**Dependencies:** Phase 0 complete  
**Owner:** Backend Engineer (Database/Prisma)

---

### Task 1.2: Create Evidence Bundle Aggregate

**Deliverable:** `EvidenceBundle` domain model for claim-level evidence collection  
**Pattern:** Aggregate root containing multiple `LearningEvidence` records

**Acceptance Criteria:**
- [ ] Define `EvidenceBundle` interface/type:
  ```typescript
  interface EvidenceBundle {
    bundleId: string;
    claimRef: string;
    domain: string;
    gradeBand: string;
    evidences: LearningEvidence[];
    bundleConfidence: number; // Aggregated from individual evidences
    coverageScore: number; // % of claim aspects covered by evidence
    contradictionDetected: boolean;
    freshnessOverall: FreshnessStatus;
    generatedAt: Date;
  }
  ```
- [ ] Implement `EvidenceBundleBuilder` service:
  - `addEvidence(e: LearningEvidence): EvidenceBundleBuilder`
  - `calculateCoverage(): number`
  - `detectContradictions(): ContradictionReport`
  - `build(): EvidenceBundle`
- [ ] Add comprehensive unit tests
- [ ] Follow existing service patterns in `knowledge-base` module

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/evidence-bundle.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/__tests__/evidence-bundle.test.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 1.1  
**Owner:** Backend Engineer (TypeScript)

---

### Task 1.3: Implement Evidence Retrieval Adapters

**Deliverable:** Real adapter implementations for external sources  
**Current State:** Mock implementations exist  
**Sources to Implement:**
1. **OpenStax** - Textbook content API
2. **Khan Academy** - Content API
3. **Wikipedia** - REST API (with quality scoring)

**Acceptance Criteria:**
- [ ] `OpenStaxAdapter` implements `EvidenceSourceAdapter`:
  - `search(query: string, domain: string): Promise<SearchResult[]>`
  - `retrieveContent(url: string): Promise<RetrievedContent>`
  - `getSourceType(): EvidenceSourceType.OPENSTAX`
- [ ] `KhanAcademyAdapter` with same interface
- [ ] `WikipediaAdapter` with quality filtering:
  - Filter by page quality ratings
  - Prefer featured/good articles
  - Track citation density
- [ ] All adapters handle errors gracefully (no silent failures)
- [ ] Rate limiting implemented per source
- [ ] Caching layer for retrieved content
- [ ] Unit tests with mocked external APIs
- [ ] Integration tests with real APIs (optional, behind feature flag)

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/adapters/openstax-adapter.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/adapters/khan-academy-adapter.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/adapters/wikipedia-adapter.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/adapters/evidence-source-adapter.ts` (interface)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/adapters/__tests__/*` (tests)

**Story Points:** 8  
**Dependencies:** Task 1.2  
**Owner:** Backend Engineer

---

### Task 1.4: Build Evidence Bundle Generator Service

**Deliverable:** Service that generates complete evidence bundles for claims  
**Flow:**
1. Extract key assertions from claim
2. Query multiple evidence sources
3. Score and rank evidence
4. Detect contradictions
5. Package into EvidenceBundle

**Acceptance Criteria:**
- [ ] Implement `EvidenceBundleGenerator`:
  ```typescript
  interface EvidenceBundleGenerator {
    generateForClaim(
      claim: LearningClaim,
      options: GenerationOptions
    ): Promise<EvidenceBundle>;
  }
  ```
- [ ] Assertion extraction (heuristic + optional LLM enhancement)
- [ ] Parallel source querying with Promise.all
- [ ] Evidence ranking algorithm (credibility * relevance * freshness)
- [ ] Contradiction detection (basic string similarity + semantic check)
- [ ] Configurable minimum evidence threshold
- [ ] Telemetry: source hit rates, average bundle size, generation latency
- [ ] Comprehensive unit tests
- [ ] Integration tests with real sources

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/evidence-bundle-generator.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/__tests__/evidence-bundle-generator.test.ts` (new)

**Story Points:** 8  
**Dependencies:** Task 1.3  
**Owner:** Backend Engineer (AI integration)

---

### Task 1.5: Add Evidence Bundle Persistence

**Deliverable:** Database layer for evidence bundles  
**Note:** Reuse existing `LearningEvidence` table, add bundle metadata

**Acceptance Criteria:**
- [ ] Add `EvidenceBundleMetadata` table:
  ```prisma
  model EvidenceBundleMetadata {
    id              String   @id @default(uuid())
    claimRef        String   @unique
    bundleConfidence Float
    coverageScore   Float
    contradictionDetected Boolean
    freshnessOverall String
    evidenceCount   Int
    generatedAt     DateTime
    
    claim           LearningClaim @relation(fields: [claimRef], references: [claimRef])
    
    @@index([claimRef])
    @@index([bundleConfidence])
  }
  ```
- [ ] Implement `EvidenceBundleRepository`:
  - `save(bundle: EvidenceBundle): Promise<void>`
  - `load(claimRef: string): Promise<EvidenceBundle | null>`
  - `findByDomain(domain: string): Promise<EvidenceBundle[]>`
- [ ] Migration script
- [ ] Repository tests

**Files Affected:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma` (update)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/evidence-bundle-repository.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 1.4  
**Owner:** Backend Engineer (Database)

---

### Task 1.6: Implement Freshness and Contradiction Checks

**Deliverable:** Background jobs for evidence health monitoring  
**Freshness Check:**
- Re-query sources for updated content
- Flag stale evidence
- Trigger re-generation if freshness drops below threshold

**Contradiction Detection:**
- Periodic re-scan for new contradictory evidence
- Alert on high-confidence contradictions

**Acceptance Criteria:**
- [ ] `EvidenceFreshnessChecker` service:
  - Runs daily via cron/scheduler
  - Re-queries sources for each evidence
  - Updates `freshnessStatus`
  - Emits events for stale evidence
- [ ] `ContradictionScanner` service:
  - Periodic scan across bundles
  - Semantic similarity for contradiction detection
  - Emits alerts for new contradictions
- [ ] Both services emit structured logs
- [ ] Both services expose metrics
- [ ] Unit tests for check logic
- [ ] Integration tests for full flow

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/jobs/evidence-freshness-checker.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/jobs/contradiction-scanner.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/jobs/__tests__/*` (tests)

**Story Points:** 5  
**Dependencies:** Task 1.5  
**Owner:** Backend Engineer

---

### Task 1.7: Define Domain-Specific Evidence Policies

**Deliverable:** Policy configuration system for evidence requirements  
**Policies by Domain:**
- **STEM (Physics, Algebra):**
  - Minimum 2 peer-reviewed or OpenStax sources
  - Khan Academy acceptable for worked examples
  - Wikipedia only for definitions, not derivations
- **K-12 Alignment:**
  - Curriculum standards must be mapped
  - Grade-appropriate vocabulary verification

**Acceptance Criteria:**
- [ ] `EvidencePolicy` interface:
  ```typescript
  interface EvidencePolicy {
    domain: string;
    gradeBand?: string;
    minimumEvidenceCount: number;
    requiredSourceTypes: EvidenceSourceType[];
    minimumCredibilityScore: number;
    freshnessRequirement: FreshnessStatus;
    allowWikipedia: boolean;
    curriculumAlignmentRequired: boolean;
  }
  ```
- [ ] Policy configuration YAML/JSON:
  - `config/evidence-policies/stem.yaml`
  - `config/evidence-policies/k12.yaml`
- [ ] `EvidencePolicyEvaluator`:
  - `evaluate(bundle: EvidenceBundle, policy: EvidencePolicy): PolicyEvaluationResult`
- [ ] Default policies for physics and algebra (pilot domains)
- [ ] Tests for policy evaluation

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/policies/evidence-policy.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/config/evidence-policies/` (new directory)
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/policies/__tests__/evidence-policy.test.ts` (new)

**Story Points:** 3  
**Dependencies:** Task 1.5  
**Owner:** Domain Expert + Backend Engineer

---

### Task 1.8: Wire Evidence into Content Studio Service

**Deliverable:** Integration of evidence generation into content workflow  
**Integration Points:**
1. After claim generation, trigger evidence bundle generation
2. Before publish gating, verify evidence policy compliance
3. Store evidence bundle with claim

**Acceptance Criteria:**
- [ ] Update `ContentStudioService.generateExperience()`:
  - Add evidence generation step after claim generation
  - Store bundle metadata with claims
- [ ] Update publish gating logic:
  - Check evidence policy compliance
  - Route to human review if insufficient evidence
  - Auto-publish only if evidence requirements met
- [ ] Add telemetry: evidence coverage per claim, policy pass rate
- [ ] Update workflow states: `GENERATING_EVIDENCE`, `EVIDENCE_REVIEW`
- [ ] Integration tests for full workflow

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/publish/gating-service.ts` (update or create)

**Story Points:** 5  
**Dependencies:** Task 1.6, Task 1.7  
**Owner:** Backend Engineer

---

### Phase 1 Success Criteria Summary

1. **Schema Evolution:**
   - `LearningEvidence` has full provenance fields
   - `EvidenceBundleMetadata` table exists
   - All migrations applied cleanly

2. **Evidence Generation:**
   - Real adapters for OpenStax, Khan Academy, Wikipedia
   - Evidence bundles generated for all claims
   - Coverage score calculated

3. **Quality Assurance:**
   - Freshness checks run daily
   - Contradiction detection active
   - Policy enforcement at publish gate

4. **Operational:**
   - Domain-specific policies configured
   - Telemetry on evidence coverage
   - Clear routing to human review when needed

---

## Phase 2: Move Examples And Animations To Manifest-First Generation

**Goal:** Make examples and animations as structured as simulations.  
**Duration:** 30 days (days 60-90)  
**Priority:** P1 - Structural improvement

### Task 2.1: Define WorkedExampleManifest Schema

**Deliverable:** JSON Schema and TypeScript types for worked examples  
**Based on:** Research on worked examples (van Gog, Paas, Sweller)  
**Schema Structure:**
```typescript
interface WorkedExampleManifest {
  schemaVersion: '1.0.0';
  manifestType: 'WorkedExample';
  
  // Provenance
  claimRef: string;
  evidenceRefs: string[];
  objectiveRefs?: string[];
  
  // Context
  domain: string;
  gradeBand: string;
  pedagogicalIntent: string;
  
  // Example family classification
  exampleFamily: 'real-world' | 'analogy' | 'worked-solution' | 'counterexample' | 'case-study';
  
  // Core content
  learnerGoal: string;
  givens: Given[];
  reasoningSteps: ReasoningStep[];
  explanationSteps: ExplanationStep[];
  
  // Pedagogical scaffolding
  misconceptionCheckpoints: MisconceptionCheckpoint[];
  transferPrompts: TransferPrompt[];
  adaptationRules: GradeAdaptationRule[];
  
  // Quality
  difficultyEstimate: number;
  estimatedTimeMinutes: number;
  prerequisites: string[];
  
  // Scoring hints
  evaluationHints: EvaluationHints;
}

interface ReasoningStep {
  stepNumber: number;
  description: string;
  visualAidRef?: string;
  checkpoint: boolean; // Pause for learner reflection?
}

interface MisconceptionCheckpoint {
  commonError: string;
  warningSign: string;
  correctiveGuidance: string;
}
```

**Acceptance Criteria:**
- [ ] JSON Schema file created and validated
- [ ] TypeScript types with strict typing (no `any`)
- [ ] Zod validation schema for runtime checking
- [ ] Schema documentation with examples
- [ ] Versioning strategy defined

**Files Affected:**
- `products/tutorputor/contracts/v1/artifact-manifests/worked-example-manifest.ts` (new)
- `products/tutorputor/contracts/v1/artifact-manifests/schemas/worked-example-schema.json` (new)
- `products/tutorputor/contracts/v1/artifact-manifests/index.ts` (update)

**Story Points:** 5  
**Dependencies:** Phase 1 complete  
**Owner:** Backend Engineer (TypeScript)

---

### Task 2.2: Define AnimationManifest Schema

**Deliverable:** JSON Schema and TypeScript types for declarative animations  
**Schema Structure:**
```typescript
interface AnimationManifest {
  schemaVersion: '1.0.0';
  manifestType: 'Animation';
  
  // Provenance
  claimRef: string;
  evidenceRefs: string[];
  
  // Context
  domain: string;
  gradeBand: string;
  pedagogicalIntent: string;
  
  // Scene structure
  sceneGraph: SceneNode[];
  entities: AnimatedEntity[];
  
  // Timeline
  segments: AnimationSegment[];
  totalDurationSeconds: number;
  
  // Cueing and pacing
  cueingRules: CueingRule[];
  pacingMetadata: PacingMetadata;
  
  // Accessibility
  narrationScript?: NarrationScript;
  subtitles?: SubtitleCue[];
  learnerControls: LearnerControl[];
  
  // Pedagogical
  claimMapping: ClaimSegmentMapping[];
}

interface AnimationSegment {
  segmentId: string;
  startTime: number;
  endTime: number;
  description: string;
  conceptsIllustrated: string[];
  pausePoints: number[]; // Times where learner can pause
}

interface CueingRule {
  trigger: 'time' | 'action' | 'completion';
  condition: string;
  effect: 'highlight' | 'focus' | 'label' | 'fade';
  target: string;
}
```

**Acceptance Criteria:**
- [ ] JSON Schema file created and validated
- [ ] TypeScript types with strict typing
- [ ] Zod validation schema
- [ ] Schema documentation with examples
- [ ] Integration with existing animation integration service

**Files Affected:**
- `products/tutorputor/contracts/v1/artifact-manifests/animation-manifest.ts` (new)
- `products/tutorputor/contracts/v1/artifact-manifests/schemas/animation-schema.json` (new)

**Story Points:** 5  
**Dependencies:** Task 2.1  
**Owner:** Backend Engineer (TypeScript)

---

### Task 2.3: Update ClaimExample Model

**Deliverable:** Enhanced `ClaimExample` to store manifest reference  
**Current:** Open-ended content payload
**New:** Manifest-first with materialized content

**Acceptance Criteria:**
- [ ] Add fields to `ClaimExample`:
  ```prisma
  model ClaimExample {
    id              String   @id @default(uuid())
    claimRef        String
    
    // Manifest reference (source of truth)
    manifestId      String   // References ArtifactManifest
    manifestVersion String
    
    // Materialized content (derived from manifest)
    title           String
    content         Json     // Rendered content
    
    // Example family classification
    exampleFamily   String
    difficultyLevel Int
    
    // Quality
    validationStatus String
    
    claim           LearningClaim @relation(fields: [claimRef], references: [claimRef])
    
    @@index([claimRef])
    @@index([exampleFamily])
  }
  ```
- [ ] Migration script
- [ ] Backward compatibility for existing examples
- [ ] Update relations to `ArtifactManifest`

**Files Affected:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/libs/tutorputor-core/prisma/migrations/` (new migration)

**Story Points:** 3  
**Dependencies:** Task 2.1  
**Owner:** Backend Engineer (Database)

---

### Task 2.4: Update ClaimAnimation Model

**Deliverable:** Enhanced `ClaimAnimation` for manifest-first approach  
**Acceptance Criteria:**
- [ ] Add fields to `ClaimAnimation`:
  ```prisma
  model ClaimAnimation {
    id              String   @id @default(uuid())
    claimRef        String
    
    // Manifest reference
    manifestId      String
    manifestVersion String
    
    // Allow multiple animations per claim (remove unique constraint)
    variantKey      String   @default('primary')
    isPrimary       Boolean  @default(true)
    
    // Rendered configuration
    config          Json     // Derived from manifest
    
    // Quality
    validationStatus String
    
    claim           LearningClaim @relation(fields: [claimRef], references: [claimRef])
    
    @@unique([claimRef, variantKey])  // Changed from @@unique([claimRef])
    @@index([claimRef])
    @@index([isPrimary])
  }
  ```
- [ ] Migration script
- [ ] Remove one-animation-per-claim restriction
- [ ] Update relations to `ArtifactManifest`

**Files Affected:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/libs/tutorputor-core/prisma/migrations/` (new migration)

**Story Points:** 3  
**Dependencies:** Task 2.2  
**Owner:** Backend Engineer (Database)

---

### Task 2.5: Create Manifest Validator Service

**Deliverable:** Validation service for all artifact manifests  
**Validation Levels:**
1. Schema validation (JSON Schema)
2. Domain-rule validation
3. Evidence coverage validation
4. Pedagogical validation

**Acceptance Criteria:**
- [ ] `ManifestValidator` interface:
  ```typescript
  interface ManifestValidator {
    validate(manifest: unknown): Promise<ValidationResult>;
  }
  ```
- [ ] `SchemaValidator` - JSON Schema validation
- [ ] `EvidenceCoverageValidator` - Check evidenceRefs exist and are valid
- [ ] `PedagogicalValidator` - Check pacing, cueing, misconception coverage
- [ ] `CompositeValidator` - Runs all validators and aggregates results
- [ ] Validation result includes:
  - Pass/fail status
  - List of violations with severity
  - Suggested fixes
- [ ] Unit tests for each validator
- [ ] Integration tests for composite validation

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/manifest-validator.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/validators/schema-validator.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/validators/evidence-coverage-validator.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/validators/pedagogical-validator.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/validators/__tests__/*` (tests)

**Story Points:** 8  
**Dependencies:** Task 2.1, Task 2.2  
**Owner:** Backend Engineer

---

### Task 2.6: Implement Worked Example Generator

**Deliverable:** Generator that outputs `WorkedExampleManifest`  
**Approach:** LLM generates manifest, then materializer renders content  

**Acceptance Criteria:**
- [ ] `WorkedExampleGenerator`:
  ```typescript
  interface WorkedExampleGenerator {
    generate(
      claim: LearningClaim,
      evidenceBundle: EvidenceBundle,
      contentNeeds: ContentNeeds,
      options: GenerationOptions
    ): Promise<WorkedExampleManifest>;
  }
  ```
- [ ] Prompt engineering for manifest generation:
  - Use evidence bundle as context
  - Generate structured manifest, not free text
  - Include misconception checkpoints
- [ ] Integration with LLM gateway (reuse `platform:java:ai-integration` patterns)
- [ ] Output validation via ManifestValidator
- [ ] Retry logic for validation failures
- [ ] Telemetry: generation latency, validation pass rate
- [ ] Unit tests with mocked LLM
- [ ] Integration tests with real LLM (optional)

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/worked-example-generator.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/prompts/worked-example-prompts.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/__tests__/worked-example-generator.test.ts` (new)

**Story Points:** 8  
**Dependencies:** Task 2.5  
**Owner:** AI Engineer + Backend Engineer

---

### Task 2.7: Implement Animation Manifest Generator

**Deliverable:** Generator that outputs `AnimationManifest`  
**Acceptance Criteria:**
- [ ] `AnimationManifestGenerator` with same interface pattern
- [ ] Prompt engineering for declarative animation
- [ ] Scene graph generation
- [ ] Cueing rules generation
- [ ] Integration with LLM gateway
- [ ] Output validation
- [ ] Retry logic
- [ ] Telemetry
- [ ] Unit tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/animation-manifest-generator.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/prompts/animation-prompts.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/__tests__/animation-manifest-generator.test.ts` (new)

**Story Points:** 8  
**Dependencies:** Task 2.5  
**Owner:** AI Engineer + Backend Engineer

---

### Task 2.8: Update Asset Materialization Service

**Deliverable:** Enhanced materializer for manifest-to-asset conversion  
**Current:** Direct generation to ContentAsset  
**New:** Manifest -> Validation -> Materialization

**Acceptance Criteria:**
- [ ] Update `AssetMaterializationService`:
  ```typescript
  interface AssetMaterializationService {
    materializeExample(manifest: WorkedExampleManifest): Promise<ContentAsset>;
    materializeAnimation(manifest: AnimationManifest): Promise<ContentAsset>;
    materializeSimulation(manifest: SimulationManifest): Promise<ContentAsset>;
  }
  ```
- [ ] Implement `materializeExample`:
  - Convert manifest to renderable content
  - Store in `ContentBlock`
  - Create `ArtifactManifest` record
- [ ] Implement `materializeAnimation`:
  - Convert manifest to animation config
  - Store in `ClaimAnimation`
  - Create `ArtifactManifest` record
- [ ] Reuse existing simulation materialization
- [ ] Consistent error handling across all modalities
- [ ] Transactional materialization (all-or-nothing)
- [ ] Unit tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/asset/materialization-service.ts` (update)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/asset/__tests__/materialization-service.test.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 2.6, Task 2.7  
**Owner:** Backend Engineer

---

### Task 2.9: Update Worker Processors for Manifest-First Flow

**Deliverable:** Updated `ExampleGenerationProcessor` and `AnimationGenerationProcessor`  
**New Flow:**
1. Load claim and evidence bundle
2. Generate manifest
3. Validate manifest
4. Materialize to asset
5. Store with manifest reference

**Acceptance Criteria:**
- [ ] Update `ExampleGenerationProcessor`:
  - Call `WorkedExampleGenerator`
  - Validate output
  - Call `materializeExample`
  - Update `ClaimExample` with manifest reference
- [ ] Update `AnimationGenerationProcessor`:
  - Call `AnimationManifestGenerator`
  - Validate output
  - Call `materializeAnimation`
  - Update `ClaimAnimation` with manifest reference
- [ ] Consistent error handling and retry logic
- [ ] Comprehensive logging
- [ ] Unit tests for processors
- [ ] Integration tests for end-to-end flow

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ExampleGenerationProcessor.ts` (update)
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/AnimationGenerationProcessor.ts` (update)
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/__tests__/example-generation.test.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/__tests__/animation-generation.test.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 2.8  
**Owner:** Backend Engineer

---

### Task 2.10: Schema-Driven Admin Editor (Example Modality)

**Deliverable:** Schema-backed editor for worked examples  
**Approach:** Generate form UI from `WorkedExampleManifest` schema  

**Acceptance Criteria:**
- [ ] Create JSON Schema form generator:
  - Input: JSON Schema
  - Output: React form components
- [ ] Build `WorkedExampleEditor`:
  - Load manifest from `ArtifactManifest`
  - Render form fields from schema
  - Real-time validation
  - Save updates back to manifest
- [ ] Integrate with existing admin app
- [ ] Type-safe implementation (no `any`)
- [ ] Unit tests for form components
- [ ] E2E tests for editor flow

**Files Affected:**
- `products/tutorputor/apps/tutorputor-admin/src/components/schema-form/` (new directory)
- `products/tutorputor/apps/tutorputor-admin/src/components/editors/WorkedExampleEditor.tsx` (new)
- `products/tutorputor/apps/tutorputor-admin/src/components/editors/__tests__/WorkedExampleEditor.test.tsx` (new)

**Story Points:** 8  
**Dependencies:** Task 2.9  
**Owner:** Frontend Engineer (React/TypeScript)

---

### Phase 2 Success Criteria Summary

1. **Manifest Standards:**
   - `WorkedExampleManifest` and `AnimationManifest` schemas defined
   - TypeScript types and Zod validation
   - Documentation with examples

2. **Generation Pipeline:**
   - Manifest-first generation for examples and animations
   - Validation before materialization
   - Consistent materialization to `ContentAsset`

3. **Database Evolution:**
   - `ClaimExample` and `ClaimAnimation` reference manifests
   - Multiple animations per claim supported

4. **Editor Experience:**
   - Schema-driven editor for worked examples
   - Real-time validation
   - Human review focuses on quality, not structure

---

## Phase 3: Converge Modalities Around Shared Planning And Variants

**Goal:** Support one engine across domains and modalities.  
**Duration:** 30 days (days 90-120)  
**Priority:** P1 - Scalability improvement

### Task 3.1: Define Canonical ContentNeeds JSON Schema

**Deliverable:** Unified schema for modality planning  
**Schema Structure:**
```typescript
interface ContentNeeds {
  schemaVersion: '1.0.0';
  claimRef: string;
  
  // Modality requirements
  examples: ExampleNeeds;
  animations: AnimationNeeds;
  simulations: SimulationNeeds;
  assessments: AssessmentNeeds;
  
  // Shared planning
  pedagogicalIntent: string;
  misconceptionCoverage: string[];
  gradeAdaptation: GradeAdaptationPlan;
  
  // Quality gates
  requiredValidation: ValidationRequirement[];
  autoPublishEligible: boolean;
}

interface ExampleNeeds {
  required: boolean;
  count: number;
  families: ExampleFamily[]; // 'real-world', 'analogy', etc.
  difficultyRange: [number, number];
}

interface AnimationNeeds {
  required: boolean;
  count: number; // Support multiple animations
  typeHints: string[];
  pacingPreference: 'slow' | 'medium' | 'fast';
}
```

**Acceptance Criteria:**
- [ ] JSON Schema defined with all modality needs
- [ ] TypeScript types with strict typing
- [ ] Zod validation schema
- [ ] Migration path from existing `contentNeeds` JSON field
- [ ] Documentation

**Files Affected:**
- `products/tutorputor/contracts/v1/content-needs.ts` (new)
- `products/tutorputor/contracts/v1/schemas/content-needs-schema.json` (new)

**Story Points:** 3  
**Dependencies:** Phase 2 complete  
**Owner:** Backend Engineer (TypeScript)

---

### Task 3.2: Update LearningClaim ContentNeeds Storage

**Deliverable:** Enhanced `LearningClaim` with structured modality plan  
**Acceptance Criteria:**
- [ ] Update `LearningClaim` model:
  ```prisma
  model LearningClaim {
    // ... existing fields ...
    
    // Replace JSON contentNeeds with structured relation
    modalityPlanId  String?
    modalityPlan    ModalityPlan? @relation(fields: [modalityPlanId], references: [id])
    
    // Keep JSON as fallback/cache
    contentNeedsJson Json?
  }
  
  model ModalityPlan {
    id              String   @id @default(uuid())
    claimRef        String   @unique
    
    // Structured content needs
    examplesRequired Boolean
    examplesCount   Int
    examplesFamilies String[]
    
    animationsRequired Boolean
    animationsCount Int
    animationTypeHints String[]
    
    simulationsRequired Boolean
    simulationsCount Int
    simulationDomains String[]
    
    // Shared planning
    pedagogicalIntent String
    misconceptionCoverage String[]
    
    // JSON fallback for extensibility
    rawPlan         Json
    
    createdAt       DateTime @default(now())
    updatedAt       DateTime @updatedAt
    
    claim           LearningClaim?
  }
  ```
- [ ] Migration script
- [ ] Update all code that reads/writes `contentNeeds`
- [ ] Tests

**Files Affected:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/libs/tutorputor-core/prisma/migrations/` (new migration)
- `products/tutorputor/services/tutorputor-platform/src/` (updates for contentNeeds usage)

**Story Points:** 5  
**Dependencies:** Task 3.1  
**Owner:** Backend Engineer (Database)

---

### Task 3.3: Implement Modality Planner Service

**Deliverable:** Unified planner for all modalities  
**Acceptance Criteria:**
- [ ] `ModalityPlanner` interface:
  ```typescript
  interface ModalityPlanner {
    planForClaim(
      claim: LearningClaim,
      evidenceBundle: EvidenceBundle,
      domain: string,
      gradeBand: string
    ): Promise<ModalityPlan>;
  }
  ```
- [ ] Implementation considers:
  - Claim type (definition, procedure, concept)
  - Evidence bundle content
  - Domain patterns (e.g., physics needs simulations)
  - Grade appropriateness
- [ ] Configurable heuristics
- [ ] Telemetry: plan confidence, coverage
- [ ] Unit tests
- [ ] Integration tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/planning/modality-planner.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/planning/__tests__/modality-planner.test.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 3.2  
**Owner:** AI Engineer + Backend Engineer

---

### Task 3.4: Add Variant Support to ClaimSimulation

**Deliverable:** Multiple simulations per claim  
**Current:** One simulation per claim
**New:** Primary + variants

**Acceptance Criteria:**
- [ ] Update `ClaimSimulation` model:
  ```prisma
  model ClaimSimulation {
    id              String   @id @default(uuid())
    claimRef        String
    
    // Variant support
    variantKey      String   @default('primary')
    isPrimary       Boolean  @default(true)
    
    // Manifest reference (align with Phase 2 pattern)
    manifestId      String
    manifestVersion String
    
    // Domain and kernel
    domain          String
    kernelId        String
    
    // Quality
    validationStatus String
    
    claim           LearningClaim @relation(fields: [claimRef], references: [claimRef])
    
    @@unique([claimRef, variantKey])
    @@index([claimRef])
    @@index([isPrimary])
    @@index([domain])
  }
  ```
- [ ] Migration script
- [ ] Update simulation generation to support variants
- [ ] Update UI to show variant selector
- [ ] Tests

**Files Affected:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/libs/tutorputor-core/prisma/migrations/` (new migration)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/simulation-generator.ts` (update)

**Story Points:** 5  
**Dependencies:** Task 3.3  
**Owner:** Backend Engineer

---

### Task 3.5: Unify Telemetry Across Modalities

**Deliverable:** Common telemetry schema for examples, animations, simulations  
**Acceptance Criteria:**
- [ ] Define `ModalityTelemetry` interface:
  ```typescript
  interface ModalityTelemetry {
    modalityType: 'example' | 'animation' | 'simulation';
    artifactId: string;
    
    // Engagement
    viewCount: number;
    completionRate: number;
    averageTimeSpentSeconds: number;
    
    // Learning outcomes
    prePostScoreDelta: number;
    misconceptionCorrectionRate: number;
    
    // Quality signals
    learnerRating: number;
    errorReports: number;
    
    // Comparison
    comparedToVariant?: string;
    abTestGroup?: 'a' | 'b';
  }
  ```
- [ ] Implement telemetry collection:
  - `ExampleTelemetryCollector`
  - `AnimationTelemetryCollector`
  - `SimulationTelemetryCollector`
- [ ] Unified analytics dashboard query interface
- [ ] Tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/telemetry/modality-telemetry.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/telemetry/collectors/` (new directory)

**Story Points:** 5  
**Dependencies:** Task 3.4  
**Owner:** Backend Engineer + Data Engineer

---

### Task 3.6: Implement A/B Testing Framework for Variants

**Deliverable:** Built-in A/B testing for modality variants  
**Acceptance Criteria:**
- [ ] `VariantExperiment` model:
  ```prisma
  model VariantExperiment {
    id              String   @id @default(uuid())
    claimRef        String
    modalityType    String   // 'example', 'animation', 'simulation'
    
    // Variants
    variantAId      String
    variantBId      String
    
    // Experiment config
    startDate       DateTime
    endDate         DateTime?
    targetSampleSize Int
    
    // Results
    status          String   // 'running', 'completed', 'cancelled'
    winnerVariantId String?
    
    @@index([claimRef])
    @@index([status])
  }
  ```
- [ ] `ExperimentService`:
  - `createExperiment(claimRef, modalityType, variantA, variantB): Experiment`
  - `assignVariant(experimentId, learnerId): 'a' | 'b'`
  - `recordOutcome(experimentId, variant, outcome): void`
  - `analyzeResults(experimentId): AnalysisResult`
- [ ] Integration with telemetry collectors
- [ ] Tests

**Files Affected:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma` (update)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/experiments/experiment-service.ts` (new)

**Story Points:** 8  
**Dependencies:** Task 3.5  
**Owner:** Backend Engineer

---

### Phase 3 Success Criteria Summary

1. **Unified Planning:**
   - `ContentNeeds` canonical schema
   - `ModalityPlanner` service
   - Structured modality plans stored with claims

2. **Variant Support:**
   - Multiple examples per claim
   - Multiple animations per claim
   - Multiple simulations per claim

3. **Unified Telemetry:**
   - Common telemetry schema across modalities
   - Cross-modality comparison possible

4. **Experimentation:**
   - A/B testing framework
   - Automatic winner selection
   - Integrated with telemetry

---

## Phase 4: Add Strong Evaluation And Safe Autonomy

**Goal:** Reduce human work without reducing trust.  
**Duration:** 30 days (days 120-150)  
**Priority:** P2 - Quality assurance

### Task 4.1: Implement Atomic Fact Extraction

**Deliverable:** Service to extract verifiable facts from content  
**Pattern:** FActScore methodology  

**Acceptance Criteria:**
- [ ] `AtomicFactExtractor`:
  ```typescript
  interface AtomicFactExtractor {
    extract(text: string): Promise<AtomicFact[]>;
  }
  
  interface AtomicFact {
    factId: string;
    statement: string;
    sourceSpan: [number, number]; // Character positions in original
    entities: string[];
    claimRef?: string;
  }
  ```
- [ ] LLM-based extraction with structured output
- [ ] Validation that extracted facts are atomic (single verifiable statement)
- [ ] Unit tests
- [ ] Integration tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/atomic-fact-extractor.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/__tests__/atomic-fact-extractor.test.ts` (new)

**Story Points:** 5  
**Dependencies:** Phase 3 complete  
**Owner:** AI Engineer

---

### Task 4.2: Implement FActScore Evaluation

**Deliverable:** FActScore-style fact verification  
**Approach:**
1. Extract atomic facts from generated content
2. Verify each fact against evidence bundle
3. Calculate precision score

**Acceptance Criteria:**
- [ ] `FActScoreEvaluator`:
  ```typescript
  interface FActScoreEvaluator {
    evaluate(
      content: string,
      evidenceBundle: EvidenceBundle
    ): Promise<FActScoreResult>;
  }
  
  interface FActScoreResult {
    precision: number; // % of facts supported by evidence
    numFacts: number;
    supportedFacts: AtomicFact[];
    unsupportedFacts: AtomicFact[];
    contradictingFacts: AtomicFact[];
  }
  ```
- [ ] Fact verification logic:
  - Semantic similarity to evidence
  - LLM-based verification for edge cases
  - Contradiction detection
- [ ] Thresholds for pass/fail
- [ ] Telemetry
- [ ] Unit tests
- [ ] Integration tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/factscore-evaluator.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/__tests__/factscore-evaluator.test.ts` (new)

**Story Points:** 8  
**Dependencies:** Task 4.1  
**Owner:** AI Engineer + Backend Engineer

---

### Task 4.3: Implement Evidence Coverage Scoring

**Deliverable:** Score for how well evidence covers a claim  
**Acceptance Criteria:**
- [ ] `EvidenceCoverageScorer`:
  ```typescript
  interface EvidenceCoverageScorer {
    score(
      claim: LearningClaim,
      evidenceBundle: EvidenceBundle
    ): Promise<CoverageScore>;
  }
  
  interface CoverageScore {
    overallScore: number; // 0.0 - 1.0
    aspectCoverage: Map<string, number>; // Per-aspect coverage
    gaps: string[]; // List of uncovered aspects
  }
  ```
- [ ] Aspect extraction from claim (key concepts, terms, relationships)
- [ ] Coverage calculation per aspect
- [ ] Gap identification
- [ ] Thresholds for sufficient coverage
- [ ] Tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/evidence-coverage-scorer.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 4.2  
**Owner:** Backend Engineer

---

### Task 4.4: Implement Modality-Specific Scorecards

**Deliverable:** Evaluation scorecards for each modality  
**Scorecard Structure:**
```typescript
interface ModalityScorecard {
  modalityType: 'example' | 'animation' | 'simulation';
  artifactId: string;
  
  // Factual quality
  factPrecision: number;
  evidenceCoverage: number;
  
  // Pedagogical quality
  clarityScore: number;
  engagementPrediction: number;
  difficultyAccuracy: number;
  
  // Modality-specific
  exampleScore?: WorkedExampleScore;
  animationScore?: AnimationScore;
  simulationScore?: SimulationScore;
  
  // Overall
  overallScore: number;
  passesThreshold: boolean;
  
  // Recommendations
  improvementAreas: string[];
}
```

**Acceptance Criteria:**
- [ ] Define scorecard schemas for each modality
- [ ] Implement `ExampleScorecardEvaluator`:
  - Check worked example structure
  - Validate misconception coverage
  - Score transfer prompts
- [ ] Implement `AnimationScorecardEvaluator`:
  - Check pacing
  - Validate cueing
  - Score accessibility
- [ ] Implement `SimulationScorecardEvaluator`:
  - Check invariants
  - Validate scenario coverage
  - Score interactivity
- [ ] Composite scoring algorithm
- [ ] Tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/scorecards/` (new directory)
- Scorecard types and evaluators for each modality

**Story Points:** 8  
**Dependencies:** Task 4.3  
**Owner:** Backend Engineer + Pedagogy Expert

---

### Task 4.5: Implement Auto-Regeneration Triggers

**Deliverable:** Automatic regeneration on quality failures  
**Triggers:**
- FActScore below threshold
- Evidence coverage below threshold
- Poor learner outcomes
- New contradictory evidence

**Acceptance Criteria:**
- [ ] `AutoRegenerationService`:
  ```typescript
  interface AutoRegenerationService {
    checkAndTrigger(artifactId: string): Promise<RegenerationDecision>;
    triggerRegeneration(artifactId: string, reason: string): Promise<void>;
  }
  ```
- [ ] Quality threshold configuration
- [ ] Regeneration candidate creation
- [ ] Backoff strategy (exponential backoff for repeated failures)
- [ ] Max regeneration attempts limit
- [ ] Human escalation on repeated failures
- [ ] Telemetry
- [ ] Tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/regeneration/auto-regeneration-service.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/regeneration/__tests__/auto-regeneration-service.test.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 4.4  
**Owner:** Backend Engineer

---

### Task 4.6: Implement Smart Review Queues

**Deliverable:** Intelligent routing to human review  
**Review Categories:**
- Novel claims (first occurrence in domain)
- Weak evidence (low coverage score)
- Contradictions detected
- Policy-sensitive domains
- Repeated generation failures

**Acceptance Criteria:**
- [ ] `ReviewQueueService`:
  ```typescript
  interface ReviewQueueService {
    routeForReview(artifactId: string, reason: ReviewReason): Promise<void>;
    getQueue(queueType: ReviewQueueType): Promise<ReviewItem[]>;
    assignReviewer(itemId: string, reviewerId: string): Promise<void>;
    recordDecision(itemId: string, decision: ReviewDecision): Promise<void>;
  }
  ```
- [ ] Review reason classification
- [ ] Queue prioritization algorithm
- [ ] Reviewer assignment logic
- [ ] Decision tracking (approve, reject, request changes)
- [ ] Analytics on review patterns
- [ ] Tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/review/review-queue-service.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/review/__tests__/review-queue-service.test.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 4.5  
**Owner:** Backend Engineer

---

### Task 4.7: Implement Benchmarking Framework

**Deliverable:** Domain and grade band benchmarking  
**Pilot Domains:** Physics, Algebra

**Acceptance Criteria:**
- [ ] `BenchmarkService`:
  ```typescript
  interface BenchmarkService {
    runBenchmark(
      domain: string,
      gradeBand: string,
      testCases: TestCase[]
    ): Promise<BenchmarkResult>;
  }
  
  interface BenchmarkResult {
    domain: string;
    gradeBand: string;
    overallPassRate: number;
    factPrecisionAvg: number;
    evidenceCoverageAvg: number;
    generationLatencyP95: number;
    costPerClaim: number;
  }
  ```
- [ ] Test case library for physics and algebra
- [ ] Automated benchmark execution
- [ ] Regression detection
- [ ] Benchmark report generation
- [ ] Historical tracking
- [ ] Tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/benchmarking/benchmark-service.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/benchmarking/test-cases/` (new directory)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/benchmarking/__tests__/benchmark-service.test.ts` (new)

**Story Points:** 8  
**Dependencies:** Task 4.6  
**Owner:** QA Engineer + Domain Expert

---

### Task 4.8: Implement Safe Auto-Publish Gating

**Deliverable:** Auto-publish with guardrails  
**Criteria for Auto-Publish:**
- FActScore >= 0.95
- Evidence coverage >= 0.90
- Modality scorecard passes
- Not novel domain pattern
- Not policy-sensitive
- No contradictions

**Acceptance Criteria:**
- [ ] `AutoPublishGatingService`:
  ```typescript
  interface AutoPublishGatingService {
    evaluateForAutoPublish(artifactId: string): Promise<AutoPublishDecision>;
  }
  
  interface AutoPublishDecision {
    canAutoPublish: boolean;
    reasons: string[]; // If false, why
    confidence: number;
  }
  ```
- [ ] Configurable criteria per domain
- [ ] Override capability for admins
- [ ] Audit log of auto-publish decisions
- [ ] Telemetry: auto-publish rate, override rate
- [ ] Tests

**Files Affected:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/publish/auto-publish-gating.ts` (new)
- `products/tutorputor/services/tutorputor-platform/src/modules/content/publish/__tests__/auto-publish-gating.test.ts` (new)

**Story Points:** 5  
**Dependencies:** Task 4.7  
**Owner:** Backend Engineer

---

### Phase 4 Success Criteria Summary

1. **Atomic Evaluation:**
   - FActScore evaluation operational
   - Evidence coverage scoring
   - Fact extraction from generated content

2. **Scorecards:**
   - Modality-specific quality scorecards
   - Pass/fail thresholds defined
   - Improvement recommendations

3. **Autonomy:**
   - Auto-regeneration on quality failures
   - Smart review queue routing
   - Safe auto-publish with guardrails

4. **Benchmarking:**
   - Physics and algebra benchmarks established
   - Regression detection active
   - Performance tracking

5. **Trust Metrics:**
   - Auto-publish rate tracked alongside quality
   - Human review efficiency metrics
   - Continuous improvement loops

---

## Summary: Task Rollup by Phase

| Phase | Tasks | Total Story Points | Duration | Key Deliverable |
|-------|-------|-------------------|----------|-----------------|
| 0 | 8 tasks | 30 SP | 30 days | Stable, unified pipeline |
| 1 | 8 tasks | 44 SP | 30 days | Real evidence layer |
| 2 | 10 tasks | 50 SP | 30 days | Manifest-first generation |
| 3 | 6 tasks | 31 SP | 30 days | Unified modality planning |
| 4 | 8 tasks | 41 SP | 30 days | Safe autonomous publishing |
| **Total** | **40 tasks** | **196 SP** | **150 days** | **Full autonomous content platform** |

---

## Critical Path Dependencies

```
Phase 0 (Stabilize)
├── Task 0.1 → 0.2 → 0.3 → 0.4 → 0.5 → 0.6 → 0.7 → 0.8
│
Phase 1 (Evidence)
├── Task 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6 → 1.7 → 1.8
│
Phase 2 (Manifest-First)
├── Task 2.1/2.2 (parallel)
│   ├── Task 2.3/2.4 (parallel)
│   ├── Task 2.5
│   ├── Task 2.6/2.7 (parallel)
│   ├── Task 2.8
│   ├── Task 2.9
│   └── Task 2.10
│
Phase 3 (Convergence)
├── Task 3.1 → 3.2 → 3.3 → 3.4 → 3.5 → 3.6
│
Phase 4 (Autonomy)
├── Task 4.1 → 4.2 → 4.3 → 4.4 → 4.5 → 4.6 → 4.7 → 4.8
```

---

## Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Proto contract changes break existing consumers | High | Comprehensive contract tests (Task 0.6), backward compatibility strategy |
| Evidence source APIs change | Medium | Adapter pattern with versioned interfaces, circuit breakers |
| LLM output quality inconsistent | High | Structured output schemas, validation layers, retry logic |
| Database migrations fail | High | Test migrations on staging, rollback scripts, idempotent migrations |
| Performance degradation | Medium | Telemetry at each phase, performance budgets, load testing |
| Team knowledge gaps | Medium | Pair programming, documentation, code review focus on standards |

---

## Definition of Done (Per Task)

Per the copilot-instructions.md guidelines, each task is complete when:

1. **Code Quality:**
   - Follows existing conventions of touched module
   - TypeScript: strict typing, no `any`, explicit return types
   - Java: documentation tags (`@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern`)
   - Zero warnings from lint/static checks

2. **Testing:**
   - Unit tests for business logic
   - Integration tests for boundaries
   - Contract tests for API changes
   - All tests pass

3. **Documentation:**
   - Code comments where non-obvious
   - Schema documentation
   - API documentation for public interfaces

4. **Observability:**
   - Structured logs for important flows
   - Metrics for critical paths
   - Error handling that surfaces, not swallows

5. **Database:**
   - Migrations tested
   - Rollback scripts prepared
   - Indexes for query performance

---

## Appendix: File Inventory

### New Files (by Phase)

**Phase 0:**
- `products/tutorputor/contracts/proto/content_generation.proto` (consolidated)
- `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/__tests__/RealContentGenerationClient.test.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/AnalyzeContentNeedsProcessor.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/__tests__/contract.test.ts`

**Phase 1:**
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/evidence-bundle.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/__tests__/evidence-bundle.test.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/adapters/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/evidence-bundle-generator.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/evidence-bundle-repository.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/jobs/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/knowledge-base/policies/*`

**Phase 2:**
- `products/tutorputor/contracts/v1/artifact-manifests/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/manifest-validator.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/validators/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/*`
- `products/tutorputor/apps/tutorputor-admin/src/components/schema-form/*`
- `products/tutorputor/apps/tutorputor-admin/src/components/editors/*`

**Phase 3:**
- `products/tutorputor/contracts/v1/content-needs.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/planning/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/telemetry/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/experiments/*`

**Phase 4:**
- `products/tutorputor/services/tutorputor-platform/src/modules/content/evaluation/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/regeneration/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/review/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/benchmarking/*`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/publish/*`

### Modified Files (by Phase)

**Phase 0:**
- `products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/RealContentGenerationClient.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ClaimGenerationProcessor.ts`

**Phase 1:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts`

**Phase 2:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/asset/materialization-service.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ExampleGenerationProcessor.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/processors/AnimationGenerationProcessor.ts`

**Phase 3:**
- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/simulation-generator.ts`

---

*End of Implementation Plan*
