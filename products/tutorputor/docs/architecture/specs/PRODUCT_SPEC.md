# TutorPutor — Reverse-Engineered Product Specification

> **Version:** 1.1.0
> **Generated:** 2026-03-03 (v1.1 — Comprehensive review pass)
> **Source of Truth:** Codebase at `products/tutorputor/`
> **Method:** Systematic reverse-engineering from source code, contracts, tests, and docs

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Architecture](#2-system-architecture)
3. [Domain Model](#3-domain-model)
4. [Functional Requirements](#4-functional-requirements)
5. [Simulation Engine Requirements](#5-simulation-engine-requirements)
6. [Learning & Pedagogy Requirements](#6-learning--pedagogy-requirements)
7. [AI Integration Requirements](#7-ai-integration-requirements)
8. [Non-Functional Requirements](#8-non-functional-requirements)
9. [Extensibility & Plugin Model](#9-extensibility--plugin-model)
10. [Traceability Matrix](#10-traceability-matrix)
11. [Gaps & Risks](#11-gaps--risks)
12. [Coverage Score](#12-coverage-score)

---

## 1. Executive Summary

### What TutorPutor Is

TutorPutor is a **simulation-first AI learning platform** that teaches STEAM concepts through interactive experiments rather than passive content consumption. Learners form hypotheses, manipulate simulation parameters, observe outcomes, and build genuine understanding through the scientific method. AI agents assist in content generation, adaptive tutoring, and assessment—but the simulation experience is primary.

### Core Differentiator

| Traditional LMS | TutorPutor |
|:---|:---|
| Content delivery (videos, text) | Simulation-first experimentation |
| Quiz-based assessment | Evidence-based mastery with CBM (Confidence-Based Marking) |
| Fixed learning path | Adaptive, knowledge-gap-aware progression |
| Passive consumption | Predict → Experiment → Explain → Construct cycle |
| No integrity verification | Viva Engine detects overconfidence/cheating patterns |

### Target Users

- **K-12 Students** – Foundational STEAM concepts with scaffolded simulations
- **Higher Education** – Advanced topics (PK/PD pharmacology, system dynamics, circuit design)
- **Self-Learners** – Independent study with adaptive pacing
- **Educators / Content Authors** – CMS for authoring Learning Units with AI assistance
- **Institutions** – LTI 1.3 integration, multi-tenant, analytics dashboards

### Deployment Modes

| Mode | Evidence |
|:---|:---|
| **Web** (primary) | `apps/tutorputor-web` — React 19 + Vite + Tailwind |
| **Admin** | `apps/tutorputor-admin` — CMS/Content Studio |
| **Student** | `apps/tutorputor-student` — Dedicated student portal |
| **Mobile** | `apps/tutorputor-mobile` — React Native (planned) |
| **Offline** | `docs/offline-mode-spec.md` — IndexedDB + ServiceWorker (spec complete) |
| **VR/AR** | `services/tutorputor-vr` — standalone VR simulation runtime (scaffold) |

---

## 2. System Architecture

### 2.1 C4 Level 1 — System Context

```
┌──────────────┐     ┌───────────────────────────────────────────────┐
│   Learner    │────▶│              TutorPutor Platform              │
│  (Browser)   │◀────│  Simulations │ AI Tutoring │ Assessments      │
└──────────────┘     └──────────────┬───────────┬───────────────────┘
                                    │           │
┌──────────────┐     ┌──────────────▼──┐  ┌─────▼─────────┐
│   Educator   │────▶│  Content Studio │  │  AI Providers  │
│  (Browser)   │◀────│  (Admin App)    │  │  (OpenAI/Ollama│
└──────────────┘     └─────────────────┘  └───────────────┘
                                    │
┌──────────────┐     ┌──────────────▼──┐
│  LMS (ext.)  │────▶│  LTI Gateway    │
│  Canvas etc. │◀────│  (1.3 OIDC)     │
└──────────────┘     └─────────────────┘
```

**External Systems:**
- AI Providers (OpenAI, Ollama, multi-provider with fallback)
- LMS platforms via LTI 1.3 (Canvas, Blackboard, Moodle)
- Stripe (payments/subscriptions)
- xAPI / Caliper LRS (learning record ingestion)

### 2.2 C4 Level 2 — Container Diagram

**Architecture:** The platform follows a **Consolidated Modular Monolith** pattern. The `tutorputor-platform` service is a single Fastify process that registers 19 domain modules, each with their own routes, services, and models. Simulation services remain separate for compute isolation.

| Container | Technology | Purpose | Evidence |
|:---|:---|:---|:---|
| **Web App** | React 19, Vite, Tailwind, Jotai, TanStack Query | Student-facing simulation & learning UI | `apps/tutorputor-web/` |
| **Admin App** | React 19, Vite, TipTap, TanStack Query | Content Studio / CMS for educators | `apps/tutorputor-admin/` |
| **API Gateway** | Fastify | Thin gateway delegating to consolidated platform | `apps/api-gateway/` |
| **Platform Service (Monolith)** | Node.js, Fastify, Prisma, Redis, Sentry | **19 modules**: content, learning, collaboration, user, engagement, integration, tenant, search, audit, compliance, credentials, payments, auto-revision, content-needs, knowledge-base, monitoring, auth, ai, animation-runtime | `services/tutorputor-platform/` |
| **AI Agents Service** | Java 21, ActiveJ, gRPC | Content generation & learner interaction agents (GAA) | `services/tutorputor-ai-agents/` |
| **Sim Author** | Node.js | AI-powered manifest generation from NL prompts | `services/tutorputor-sim-author/` |
| **Sim NL** | Node.js | Natural language intent parsing & manifest refinement | `services/tutorputor-sim-nl/` |
| **Sim Runtime** | Node.js + WASM + Redis | Simulation execution with session state in Redis | `services/tutorputor-sim-runtime/` |
| **Sim SDK** | TypeScript | SDK for building custom domain kernels | `services/tutorputor-sim-sdk/` |
| **Kernel Registry** | Node.js, Hono | Plugin marketplace for simulation kernels | `services/tutorputor-kernel-registry/` |
| **Domain Loader** | Node.js | Loads domain content (JSON concept graphs) at startup | `services/tutorputor-domain-loader/` |
| **Database** | SQLite (dev) / PostgreSQL (prod), Prisma ORM | **106 models, 61 enums** — 2,792 lines of schema | `services/tutorputor-db/` |
| **VR Service** | Node.js | VR/AR simulation runtime with 8 DB models | `services/tutorputor-vr/` |
| **Content Studio Agents** | Java 21, ActiveJ | GAA agents for content generation & learner interaction | `libs/content-studio-agents/` |
| **Content Worker** | Node.js | Background content processing pipeline | `services/tutorputor-platform/src/workers/` |

### 2.2.1 Platform Modules (Consolidated Monolith)

The platform service (`server.ts`) registers the following modules, each prefixed under `/api/`:

| Module | Prefix | Sub-modules | Purpose | Evidence |
|:---|:---|:---|:---|:---|
| **content** | `/api/content` | cms, studio, animation-integration | Content CRUD, CMS, content studio authoring | `modules/content/` |
| **learning** | `/api/learning` | analytics-service, assessment-service, pathways-service | Learning events, assessments, pathways, analytics | `modules/learning/` |
| **collaboration** | `/api/collaboration` | threads, shared notes | Discussion Q&A, shared note editing | `modules/collaboration/` |
| **user** | `/api/user` | teacher, admin | Teacher classrooms, institution admin dashboards | `modules/user/` |
| **engagement** | `/api/engagement` | gamification, social, credentials | Points/badges/leaderboards, study groups, forums, peer tutoring, chat | `modules/engagement/` |
| **integration** | `/api/integration` | lti, marketplace, billing | LTI 1.3 OIDC, marketplace listings, checkout/purchases | `modules/integration/` |
| **tenant** | `/api/tenant` | config, domain packs | Multi-tenant config, domain pack management, quota enforcement | `modules/tenant/` |
| **search** | `/api/search` | full-text search | Content discovery with faceted filtering | `modules/search/` |
| **audit** | `/api/audit` | audit log | Immutable audit trail with actor/resource/action tracking | `modules/audit/` |
| **compliance** | `/api/compliance` | export, deletion, consent | GDPR/CCPA data export, right-to-deletion, consent management | `modules/compliance/` |
| **credentials** | `/api/credentials` | badge management | Achievement rules, simulation-based credentials | `modules/credentials/` |
| **payments** | `/api/payments` | Stripe subscriptions | Subscription plans (FREE→ENTERPRISE), webhook handling | `modules/payments/` |
| **auto-revision** | `/api/auto-revision` | drift detection, A/B testing | Content quality drift monitoring, auto-regeneration, A/B experiments | `modules/auto-revision/` |
| **content-needs** | `/api/content-needs` | claim analysis | Bloom-level-aware content needs analysis for claims | `modules/content-needs/` |
| **knowledge-base** | `/api/knowledge-base` | fact-checking, curriculum standards | Fact verification, concept search, curriculum alignment | `modules/knowledge-base/` |
| **monitoring** | `/api/monitoring` | health checks | Production health check system with 30s polling | `modules/monitoring/` |
| **auth** | — | JWT, RBAC | JWT authentication, role-based access control | `modules/auth/` |
| **ai** | `/api/ai` | AI proxy, content generation | Ollama/OpenAI proxy, content generation service | `modules/ai/` |
| **animation-runtime** | — | animation service | Animation execution and integration | `modules/animation-runtime/` |

### 2.3 C4 Level 3 — Component Breakdown

#### Simulation Engine (Core)

| Component | Role | Evidence |
|:---|:---|:---|
| `PhysicsKernel` | Euler integration solver for rigid bodies, springs, vectors | `sim-runtime/physics-kernel.ts` |
| `DiscreteKernel` | Algorithm visualization — sort, graph traversal, data structures | `sim-runtime/discrete-kernel.ts` |
| `SystemDynamicsKernel` | Stock-flow models, Euler/RK4 integration | `sim-runtime/system-dynamics-kernel.ts` |
| `ChemistryKernel` | Molecular operations, valence validation | `sim-runtime/chemistry-kernel.ts` |
| `BaseKernel` (SDK) | Abstract base for custom domain kernels | `sim-sdk/base-kernel.ts` |
| `IntentParser` | NL → structured intent classification (regex + patterns) | `sim-nl/intent-parser.ts` |
| `RefinementEngine` | Apply intents to modify manifests with undo/redo | `sim-nl/refinement-engine.ts` |
| `SimAuthorService` | AI manifest generation with prompt packs per domain | `sim-author/service.ts` |
| `PromptPacks` | Domain-specific few-shot prompts (6 domains) | `sim-author/prompt-packs.ts` |

#### Learning Engine (Libs)

| Component | Role | Evidence |
|:---|:---|:---|
| `ClaimMasteryCalculator` | CBM scoring, parameter targeting, explanation scoring | `learning-engine/analytics/ClaimMastery.ts` |
| `VivaEngine` | Overconfidence/cheating detection → oral verification queue | `learning-engine/analytics/VivaEngine.ts` |
| `LearningUnitValidator` | 9-gate publishing validation for Learning Units | `learning-engine/validation/LearningUnitValidator.ts` |
| `SimulationAdapter` | Maps simulation manifests to LearningPath steps | `learning-path/simulation-adapter.ts` |

#### Learning Kernel (Plugin Architecture)

| Component | Role | Evidence |
|:---|:---|:---|
| `PluginRegistry` | Central registry with lifecycle management | `learning-kernel/registry/PluginRegistry.ts` |
| `PipelineRunner` | Priority-ordered plugin execution pipeline | `learning-kernel/pipeline/PipelineRunner.ts` |
| `CBMProcessor` | Confidence-Based Marking evidence processor | `learning-kernel/plugins/CBMProcessor.ts` |
| `XAPIIngestor` | xAPI statement ingestion plugin | `learning-kernel/plugins/XAPIIngestor.ts` |
| `LearningUnitValidator` | Plugin-based LU validation | `learning-kernel/plugins/LearningUnitValidator.ts` |
| `ContentStudioValidator` | Content Studio validation plugin | `learning-kernel/plugins/ContentStudioValidator.ts` |

#### AI Agents (Java / GAA Framework)

| Component | Role | Evidence |
|:---|:---|:---|
| `ContentGenerationAgent` | PERCEIVE→REASON→ACT→CAPTURE→REFLECT lifecycle for content generation | `content-studio-agents/ContentGenerationAgent.java` |
| `LearnerInteractionAgent` | Real-time adaptive tutoring with emotional state detection | `content-studio-agents/LearnerInteractionAgent.java` |
| `ContentQualityValidator` | Fact-checking and curriculum alignment validation | `content-studio-agents/ContentQualityValidator.java` |
| `KnowledgeBaseService` | Domain knowledge for fact-checking | `content-studio-agents/KnowledgeBaseService.java` |

#### Frontend (Rendering)

| Component | Role | Evidence |
|:---|:---|:---|
| `SimulationPlayer` | Play/pause, step, seek, speed, keyboard shortcuts | `web/SimulationPlayer.tsx` |
| `EnhancedSimulationCanvas` | Canvas rendering with pan/zoom, hit-testing, annotations | `web/EnhancedSimulationCanvas.tsx` |
| `Physics3DRenderer` | 3D physics rendering via React Three Fiber | `sim-renderer/Physics3DRenderer.tsx` |
| `PhysicsRenderer` | 2D physics rendering (rigid bodies, springs, vectors, particles) | `sim-renderer/PhysicsRenderer.ts` |
| `DiscreteRenderer` | Node/edge/pointer rendering for algorithm visualization | `sim-renderer/DiscreteRenderer.ts` |
| `useSimulation` Hook | Jotai-based state management with undo/redo history | `physics-simulation/hooks/useSimulation.ts` |

---

## 3. Domain Model

### 3.1 Core Entities

#### SimulationManifest

| Field | Type | Constraint | Evidence |
|:---|:---|:---|:---|
| `id` | `SimulationId` (branded string) | Required, unique | `contracts/v1/simulation/types.ts` |
| `version` | `string` | SemVer format | `contracts/v1/simulation/types.ts` |
| `title` | `string` | Required, non-empty | Manifest schema |
| `description` | `string` | Optional | Manifest schema |
| `domain` | `SimulationDomain` | One of 8 domains | `contracts/v1/simulation/types.ts` |
| `canvas` | `{ width, height, backgroundColor }` | Required | Manifest schema |
| `playback` | `{ defaultSpeed, allowSpeedChange }` | Required | Manifest schema |
| `initialEntities` | `SimEntity[]` | At least 1 | Manifest schema |
| `steps` | `SimulationStep[]` | At least 1 | Manifest schema |
| `domainMetadata` | Domain-specific config | Varies by domain | Manifest schema |

**Lifecycle:** Draft → Validated → Published → Archived
**Relationships:** Contains entities and steps; Referenced by LearningUnit tasks; Cached with hash-based keys
**Supported Domains:** `CS_DISCRETE`, `PHYSICS`, `ECONOMICS`, `CHEMISTRY`, `BIOLOGY`, `MEDICINE`, `ENGINEERING`, `MATHEMATICS`

#### LearningUnit

| Field | Type | Constraint | Evidence |
|:---|:---|:---|:---|
| `intent` | `Intent` | Required (problem + motivation) | `contracts/v1/learning-unit.ts` |
| `claims` | `Claim[]` | ≥1; IDs match `C\d+` pattern | `contracts/v1/learning-unit.ts` |
| `evidence` | `Evidence[]` | ≥1; every claim must have evidence | `contracts/v1/learning-unit.ts` |
| `tasks` | `Task[]` | ≥1 (prediction/simulation/explanation/construction) | `contracts/v1/learning-unit.ts` |
| `artifacts` | `Artifact[]` | ≥1 (simulation, explainer_video, etc.) | `contracts/v1/learning-unit.ts` |
| `telemetry` | `TelemetryConfig` | ≥3 events required | `contracts/v1/learning-unit.ts` |
| `assessment` | `AssessmentConfig` | Required (model + scoring) | `contracts/v1/learning-unit.ts` |
| `credential` | `CredentialConfig` | Optional; skillTags recommended | `contracts/v1/learning-unit.ts` |
| `level` | `string` | Optional (e.g., "university") | `contracts/v1/learning-unit.ts` |

**Lifecycle:** Draft → Review → Published → Archived
**Publishing Gates:** 9 mandatory gates (hasIntent, hasMinimumClaims, allClaimsHaveEvidence, hasMinimumTasks, predictionsRequireConfidence, explainersAreNotTerminal, hasMinimumTelemetry, hasAssessmentConfig, hasMinimumArtifacts)

#### Claim

| Field | Type | Constraint | Evidence |
|:---|:---|:---|:---|
| `id` | `string` | Pattern: `C\d+` (C1, C2, ...) | LearningUnitValidator |
| `text` | `string` | ≥10 chars; should contain action verbs | LearningUnitValidator |
| `bloom` | `BloomLevel` | Required (remember→create) | `contracts/v1/learning-unit.ts` |
| `prerequisites` | `string[]` | Format: `LU_id.claim_id` | `contracts/v1/learning-unit.ts` |

**Action Verbs (validated):** predict, explain, construct, compare, derive, analyze, evaluate, create, apply, demonstrate, identify, describe, calculate, design, implement

#### SimEntity (Entity Hierarchy)

**Base:** `SimEntityBase` — id, type, label, x, y, z, width, height, rotation, scale, opacity, color, visible, layer, metadata

| Domain Entity | Type Field | Unique Fields | Evidence |
|:---|:---|:---|:---|
| `DiscreteNodeEntity` | `"node"` | value, highlighted, visited, comparing, sorted, shape | `contracts/v1/simulation/types.ts` |
| `DiscreteEdgeEntity` | `"edge"` | sourceId, targetId, directed, weight | `contracts/v1/simulation/types.ts` |
| `DiscretePointerEntity` | `"pointer"` | targetId, pointerLabel, style | `contracts/v1/simulation/types.ts` |
| `PhysicsBodyEntity` | `"rigidBody"` | mass, velocityX/Y, accelerationX/Y, friction, restitution, fixed | `contracts/v1/simulation/types.ts` |
| `PhysicsSpringEntity` | `"spring"` | anchorId, attachId, stiffness, damping, restLength | `contracts/v1/simulation/types.ts` |
| `PhysicsVectorEntity` | `"vector"` | attachId, magnitude, angle, vectorType | `contracts/v1/simulation/types.ts` |
| `PhysicsParticleEntity` | `"particle"` | velocityX/Y, lifetime, age | `contracts/v1/simulation/types.ts` |
| `EconStockEntity` | `"stock"` | value, minValue, maxValue, units | `contracts/v1/simulation/types.ts` |
| `EconFlowEntity` | `"flow"` | sourceId, targetId, rate, equation, delay | `contracts/v1/simulation/types.ts` |
| `EconAgentEntity` | `"agent"` | state, agentType, behavior | `contracts/v1/simulation/types.ts` |
| `ChemAtomEntity` | `"atom"` | element, charge, isotope, hybridization | `contracts/v1/simulation/types.ts` |
| `ChemBondEntity` | `"bond"` | atom1Id, atom2Id, bondOrder | `contracts/v1/simulation/types.ts` |

#### Module (Database)

| Field | Type | Constraint | Evidence |
|:---|:---|:---|:---|
| `id` | `String` | CUID, primary key | `schema.prisma` |
| `tenantId` | `String` | Multi-tenant isolation | `schema.prisma` |
| `slug` | `String` | Unique per tenant | `schema.prisma` |
| `title` | `String` | Required | `schema.prisma` |
| `domain` | `ModuleDomain` | MATH, SCIENCE, TECH | `schema.prisma` |
| `difficulty` | `ModuleDifficulty` | INTRO, INTERMEDIATE, ADVANCED | `schema.prisma` |
| `status` | `ModuleStatus` | DRAFT → PUBLISHED → ARCHIVED | `schema.prisma` |

**Relationships:** Tags, LearningObjectives, ContentBlocks, Prerequisites, Enrollments, Revisions, Assessments, SimulationTemplates, SimulationManifests, MarketplaceListings, LearningExperience

#### Assessment

Types: `QUIZ`, `PROJECT`, `SIMULATION`
Status: `DRAFT → PUBLISHED → ARCHIVED`
Attempt Status: `IN_PROGRESS → SUBMITTED → GRADED → EXPIRED`

Modes (for simulation assessments):
- **Prediction** — Learner predicts outcome before running simulation; requires confidence level
- **Manipulation** — Learner manipulates parameters to achieve target condition
- **Explanation** — Learner explains simulation behavior using rubric-graded free text

### 3.2 Database Model Inventory (106 Models, 61 Enums)

The spec v1.0 documented ~10 models. The full schema (2,792 lines) contains **106 models across 10 domain areas**:

| Domain | Models | Key Entities |
|:---|:---|:---|
| **Core Content** (12) | Module, ModuleTag, ModuleLearningObjective, ModuleContentBlock, ModulePrerequisite, ModuleRevision, DomainConcept, ConceptPrerequisite, ConceptModuleMapping, DomainAuthor, DomainAuthorConcept, Asset | Content creation, domain graph, authorship |
| **Enrollment & Learning** (5) | Enrollment, LearningEvent, LearningPath, LearningPathNode, LearningPathEnrollment | Student enrollment, event sourcing, adaptive paths |
| **Assessment** (4) | Assessment, AssessmentObjective, AssessmentItem, AssessmentAttempt, AssessmentDraft | Quizzes, rubrics, attempts, drafts |
| **Simulation** (6) | SimulationManifest, SimulationTemplate, SimulationDefinition, SimulationManifestVersion, SimulationManifestExtension, SimulationLinkAudit | Manifest versioning, template governance |
| **Social Learning** (18) | StudyGroup, StudyGroupMember, StudyGroupJoinRequest, StudyGroupInvite, StudySession, SessionRsvp, Forum, ForumTopic, ForumPost, PostReaction, TutorProfile, TutoringRequest, TutoringSession, TutoringReview, ChatRoom, ChatMessage, ChatReadReceipt, SocialActivity | Study groups, forums, peer tutoring, real-time chat |
| **Collaboration** (5) | Thread, Post, HelpRequest, SharedNote, NoteComment, CollaborativeWhiteboard | Q&A threads, shared notes, whiteboards |
| **VR/AR** (8) | VRLab, VRScene, VRInteractable, VRLabObjective, VRSession, VRMultiplayerSession, VRAsset, VRAnalyticsEvent | Full VR lab system with multiplayer |
| **Commerce** (3) | MarketplaceListing, CheckoutSession, Purchase | Marketplace, payments |
| **Tenant & Identity** (7) | Tenant, TenantSettings, User, IdentityProvider, SsoUserLink, SocialNotification, NotificationPreference | Multi-tenancy, SSO, notifications |
| **Compliance & Audit** (4) | DataExportRequest, DataDeletionRequest, DeletionVerification, AuditLog | GDPR/CCPA, audit trail |
| **Learning Experience** (14) | LearningExperience, LearningUnit, LearningClaim, LearningEvidence, ExperienceTask, ValidationRecord, AIGenerationLog, ExperienceRevision, ExperienceEvent, ClaimExample, ClaimSimulation, ClaimAnimation, ReviewQueue, ReviewDecision | Full experience authoring pipeline |
| **Auto-Revision** (8) | ExperienceAnalytics, ExperienceAutoRefinement, ABExperiment, DriftSignal, RegenerationInsight, AutoRevisionConfig, AutoRevisionMetrics, VisualizationDefinition, VisualizationSnapshot, ContentExample | Content quality monitoring, A/B testing |
| **Other** (4) | AIPromptCache, ValidationRecordExtended, SimulationManifestVersion, ClassroomMember | Caching, extended validation |

⚠️ **Gap: Many of these models (especially Social Learning, VR, Auto-Revision) have corresponding service code but no dedicated test files.**

---

## 4. Functional Requirements

### FR-SIM: Simulation Engine

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-001 | Manifest-Driven Simulation | System SHALL execute simulations from declarative JSON manifests (USP format) containing entities, steps, and domain metadata | `sim-runtime/physics-kernel.ts` — `run()` method |
| TPUT-FR-002 | Multi-Domain Kernels | System SHALL support 8 simulation domains: CS_DISCRETE, PHYSICS, ECONOMICS, CHEMISTRY, BIOLOGY, MEDICINE, ENGINEERING, MATHEMATICS | `contracts/v1/simulation/types.ts` — `SimulationDomain` type |
| TPUT-FR-003 | Physics Simulation | Physics kernel SHALL support rigid bodies (mass, velocity, friction, restitution), springs (Hooke's law + damping), vectors (velocity/acceleration/force), gravity, and Euler integration at configurable timestep | `sim-runtime/physics-kernel.ts` — `PhysicsKernel` class |
| TPUT-FR-004 | Deterministic Execution | Simulation kernels SHALL produce identical keyframes for identical manifests and seeds | `physics-kernel.ts` — `runDeterministic()` returns same as `run()` for deterministic engine |
| TPUT-FR-005 | Keyframe Generation | Each simulation step SHALL produce a `SimKeyframe` containing stepIndex, timestamp, and full entity state snapshot | `physics-kernel.ts` — step/run methods |
| TPUT-FR-006 | State Serialization | Kernel state SHALL be serializable to JSON and restorable via `serialize()`/`deserialize()` | `physics-kernel.ts` — `serialize()` and `deserialize()` methods |
| TPUT-FR-007 | Simulation Actions | System SHALL process step actions: SET_INITIAL_VELOCITY, APPLY_FORCE (impulse or continuous), CONNECT_SPRING, RELEASE, SET_GRAVITY, CREATE_ENTITY, REMOVE_ENTITY | `physics-kernel.ts` — `applyPhysicsAction()` |
| TPUT-FR-008 | Spring Physics | Spring forces SHALL follow Hooke's law (F = -kx) with configurable stiffness, damping, and rest length | `physics-kernel.ts` — `applySpringForces()` |
| TPUT-FR-009 | Ground Collision | Physics kernel SHALL implement simplified ground collision at y=500 with restitution bounce and friction deceleration | `physics-kernel.ts` — ground check in `step()` |
| TPUT-FR-010 | Vector Entity Tracking | Vector entities SHALL auto-update magnitude/angle based on attached body's velocity or acceleration | `physics-kernel.ts` — `updateVectorEntities()` |

### FR-NL: Natural Language Interface

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-011 | NL Intent Parsing | System SHALL classify natural language commands into 15 intent types for simulation modification | `sim-nl/intent-parser.ts` — regex patterns |
| TPUT-FR-012 | Manifest Refinement | System SHALL apply parsed intents to modify existing manifests (add/remove/modify entities and steps) with undo/redo | `sim-nl/refinement-engine.ts` — `apply()` with undo stack |
| TPUT-FR-013 | AI Manifest Generation | System SHALL generate simulation manifests from NL descriptions using domain-specific prompt packs with few-shot examples | `sim-author/service.ts` — `createSimulationAuthorService()` |
| TPUT-FR-014 | Multi-Provider AI | AI service SHALL support multiple providers with automatic fallback and retry logic (configurable max retries) | `sim-author/service.ts` — `callAI()` with retry |
| TPUT-FR-015 | Rate Limiting | AI calls SHALL be rate-limited by requests/minute and tokens/minute per configuration | `sim-author/service.ts` — `checkRateLimit()` |
| TPUT-FR-016 | Confidence Scoring | Generated manifests SHALL include confidence score; low confidence SHALL trigger `needsReview` flag | `contracts/v1/simulation/types.ts` — `GenerateManifestResult` |
| TPUT-FR-017 | Parameter Suggestions | System SHALL suggest simulation parameters for a given domain and context | `contracts/v1/simulation/types.ts` — `SuggestParametersRequest/Result` |

### FR-LRN: Learning Engine

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-020 | CBM Scoring | System SHALL calculate Confidence-Based Marking scores using a scoring matrix: correct+high=3, correct+medium=2, correct+low=1, incorrect+high=-6, incorrect+medium=-2, incorrect+low=0 | `learning-kernel/plugins/CBMProcessor.ts` — `DEFAULT_CBM_CONFIG` |
| TPUT-FR-021 | Calibration Index | System SHALL compute calibration index (average confidence - accuracy delta) per session; values > 0.3 SHALL trigger viva | `CBMProcessor.ts` — `updateAggregates()` |
| TPUT-FR-022 | Brier Score | System SHALL calculate Brier score (mean squared error between confidence and outcome) for probabilistic accuracy measurement | `CBMProcessor.ts` — `calculateAggregate()` |
| TPUT-FR-023 | Claim Mastery | System SHALL compute weighted mastery scores from prediction_vs_outcome (0.3), parameter_targeting (0.5), explanation_quality (0.2), construction_artifact (0.4) evidence types | `learning-engine/analytics/ClaimMastery.ts` — `EVIDENCE_WEIGHTS` |
| TPUT-FR-024 | Parameter Targeting Scoring | System SHALL score parameter_targeting based on goal achievement, attempt count (penalty per attempt), and RMSE relative to tolerance | `ClaimMastery.ts` — `calculateParameterTargetingScore()` |
| TPUT-FR-025 | Viva Detection | System SHALL identify learners needing oral verification based on: overconfident wrong (≥2 consecutive), speed anomaly (<P10), pattern mismatch (correct but random exploration) | `learning-engine/analytics/VivaEngine.ts` — `evaluateTriggers()` |
| TPUT-FR-026 | Viva Priority Queue | Viva candidates SHALL be prioritized: CRITICAL (overconfident wrong), HIGH (speed anomaly), MEDIUM (pattern mismatch), LOW (explanation avoidance) | `VivaEngine.ts` — `PRIORITY` constants |
| TPUT-FR-027 | LU Publishing Gates | Learning Units SHALL pass 9 validation gates before publishing: hasIntent, hasMinimumClaims, allClaimsHaveEvidence, hasMinimumTasks, predictionsRequireConfidence, explainersAreNotTerminal, hasMinimumTelemetry, hasAssessmentConfig, hasMinimumArtifacts | `validation/LearningUnitValidator.ts` — `PUBLISH_GATES` |
| TPUT-FR-028 | Prediction Confidence Required | All prediction tasks within a Learning Unit SHALL require confidence selection (low/medium/high) | `LearningUnitValidator.ts` — `checkTasks()` |
| TPUT-FR-029 | Explainers Must Scaffold | Explainer video artifacts SHALL NOT be terminal content; they MUST scaffold at least one task | `LearningUnitValidator.ts` — `checkArtifacts()` |

### FR-ASM: Assessment

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-030 | Simulation Assessment Items | System SHALL support 3 assessment modes: prediction, manipulation, and explanation with domain-specific grading | `libs/assessments/simulation-item.ts` |
| TPUT-FR-031 | Grading Strategies | System SHALL support grading methods: `kernel_replay` (re-run simulation with tolerances), `state_comparison`, `rubric` (multi-criteria scoring) | `simulation-item.ts` — `SimulationGradingStrategy` |
| TPUT-FR-032 | Partial Credit | Assessment grading SHALL support partial credit by default | `simulation-item.ts` — `partialCredit: true` |
| TPUT-FR-033 | Hint System | Simulation assessment items SHALL support tiered hints with unique IDs and escalation | `simulation-item.ts` — `withHints()` |
| TPUT-FR-034 | Item Inference | System SHALL infer assessment items from simulation manifests including entity focus and parameter constraints | `simulation-item.ts` — `inferSimulationItemFromManifest()` |
| TPUT-FR-035 | CBM Scoring Integration | Assessment responses SHALL be scored using CBM+ matrix with accuracy, confidence, and Brier metrics aggregated per session | `CBMProcessor.ts` — full pipeline |

### FR-AI: AI Agents

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-040 | GAA Lifecycle | AI agents SHALL follow 5-phase lifecycle: PERCEIVE → REASON → ACT → CAPTURE → REFLECT | `ContentGenerationAgent.java`, `LearnerInteractionAgent.java` |
| TPUT-FR-041 | Content Generation | ContentGenerationAgent SHALL generate content with quality validation, curriculum alignment checking, and fact-checking against knowledge base | `ContentGenerationAgent.java` — `act()` method |
| TPUT-FR-042 | Learner Context Enrichment | Agents SHALL enrich requests with learner preferences, difficulty adjustment, and knowledge gap detection | `ContentGenerationAgent.java` — `enrichWithLearnerContext()` |
| TPUT-FR-043 | Episode Capture | Every agent interaction SHALL produce an Episode stored in memory with full context, tags, and reward signal | `ContentGenerationAgent.java` — `capture()` |
| TPUT-FR-044 | Semantic Fact Storage | High-quality outputs (qualityScore ≥ 0.8) SHALL be stored as semantic facts (triples) for future reference | `ContentGenerationAgent.java` — `capture()` conditional fact storage |
| TPUT-FR-045 | Reflection | Agents SHALL analyze recent episodes; quality ≥ 0.85 triggers pattern extraction; quality < 0.6 triggers failure analysis | `ContentGenerationAgent.java` — `reflect()` |
| TPUT-FR-046 | Policy Learning | Successful patterns SHALL be stored as Policies (situation → action with confidence) for future prompt optimization | `ContentGenerationAgent.java` — `extractSuccessfulPatterns()` |
| TPUT-FR-047 | Adaptive Tutoring | LearnerInteractionAgent SHALL track emotional state (struggling, rushed, frustrated, confident, neutral) from timing and attempt data | `LearnerInteractionAgent.java` — `detectEmotionalState()` |
| TPUT-FR-048 | Mastery-Based Progression | Tutoring agent SHALL track mastery threshold (0.85) with minimum 3 attempts before advancing | `LearnerInteractionAgent.java` — `MASTERY_THRESHOLD`, `MIN_ATTEMPTS_FOR_MASTERY` |

### FR-CMS: Content Management

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-050 | Module CRUD | Platform SHALL support creating, reading, updating, and deleting learning modules with version tracking | `schema.prisma` — Module model |
| TPUT-FR-051 | Content Blocks | Modules SHALL contain typed content blocks: text, rich_text, interactive_visualization, video, image, simulation, vr_simulation, example, exercise, assessment_item_ref, ai_tutor_prompt | `contracts/v1/types.ts` — `ContentBlockType` |
| TPUT-FR-052 | Simulation Block | Content blocks SHALL reference simulations by manifest ID or embed inline manifests (for drafts) | `contracts/v1/types.ts` — `SimulationBlockPayload` |
| TPUT-FR-053 | Multi-Tenant Isolation | All data SHALL be scoped by tenantId; unique constraints include `[tenantId, slug]` | `schema.prisma` — `@@unique([tenantId, slug])` |
| TPUT-FR-054 | Content Generation via gRPC | System SHALL support gRPC services for: GenerateClaims, AnalyzeContentNeeds, GenerateExamples, GenerateSimulation, GenerateAnimation | `tutorputor_content_generation.proto` |
| TPUT-FR-055 | AI Learning Path Generation | System SHALL generate personalized learning paths via gRPC including nodes with type, prerequisites, and estimated time | `ai_learning.proto` — `GenerateLearningPath` |

### FR-INT: Integration

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-060 | LTI 1.3 Launch | System SHALL support LTI 1.3 OIDC launch flow with JWT verification | `platform/modules/integration/lti/routes.ts` |
| TPUT-FR-061 | JWKS Endpoint | System SHALL expose a JWKS endpoint for LTI verification | `lti/routes.ts` — `GET /jwks` |
| TPUT-FR-062 | Stripe Payments | System SHALL support Stripe subscriptions with plans: FREE, STARTER, PROFESSIONAL, INSTITUTION, ENTERPRISE | `platform/modules/payments/service.ts` — `PLAN_PRICES` |
| TPUT-FR-063 | xAPI Ingestion | System SHALL ingest xAPI statements via the XAPIIngestor plugin and normalize to EvidenceEvents | `learning-kernel/plugins/XAPIIngestor.ts` |
| TPUT-FR-064 | Marketplace | System SHALL support a module marketplace with listings, visibility control (PUBLIC/PRIVATE), and creator attribution | `schema.prisma` — `MarketplaceListing` model |

### FR-SOC: Social Learning (⚠️ NOT IN v1.0 SPEC)

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-070 | Study Groups | System SHALL support study groups with membership, join requests, invitations, and configurable visibility (PUBLIC/PRIVATE) | `modules/engagement/social/study-groups.ts`, `schema.prisma` — 4 related models |
| TPUT-FR-071 | Discussion Forums | System SHALL support scoped forums (GLOBAL/STUDY_GROUP/CLASSROOM/MODULE) with topics, threaded posts, reactions, polls, and moderation | `modules/engagement/social/forums.ts`, `schema.prisma` — Forum, ForumTopic, ForumPost, PostReaction |
| TPUT-FR-072 | Peer Tutoring | System SHALL support tutor profiles, tutoring requests, sessions, and reviews with rating system | `modules/engagement/social/peer-tutoring.ts`, `schema.prisma` — TutorProfile, TutoringRequest, TutoringSession, TutoringReview |
| TPUT-FR-073 | Real-Time Chat | System SHALL support chat rooms with messages and read receipts; chat rooms are linked to study groups | `modules/engagement/social/chat.ts`, `schema.prisma` — ChatRoom, ChatMessage, ChatReadReceipt |
| TPUT-FR-074 | Shared Notes | System SHALL support collaborative shared notes with versioning, editing permissions (view/comment/edit), and comments | `modules/collaboration/service.ts`, `schema.prisma` — SharedNote, NoteComment |
| TPUT-FR-075 | Collaborative Whiteboards | System SHALL support collaborative whiteboards for real-time visual collaboration | `schema.prisma` — CollaborativeWhiteboard |
| TPUT-FR-076 | Social Activity Feed | System SHALL track social activity events across the platform | `schema.prisma` — SocialActivity |
| TPUT-FR-077 | Study Sessions | Study groups SHALL support scheduled study sessions with RSVP (ATTENDING/MAYBE/NOT_ATTENDING) | `schema.prisma` — StudySession, SessionRsvp |

### FR-GAM: Gamification (⚠️ NOT IN v1.0 SPEC)

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-080 | Points System | System SHALL award points for activities (module_complete, assessment, streak, daily_login, bonus) with per-user tracking | `modules/engagement/gamification/service.ts` — `AwardPointsInput` |
| TPUT-FR-081 | Badge System | System SHALL support badge creation with criteria types: modules_completed, streak_days, points_earned, assessment_score, custom | `modules/engagement/gamification/service.ts` — `BadgeCriteria` |
| TPUT-FR-082 | Leaderboard | System SHALL support time-scoped leaderboards (daily/weekly/monthly/allTime), optionally scoped to module | `modules/engagement/gamification/service.ts` — `LeaderboardOptions` |
| TPUT-FR-083 | Level Progression | System SHALL calculate user levels from XP thresholds: [0, 100, 250, 500, 1000, 2000, 4000, 8000, 16000, 32000] | `modules/engagement/gamification/service.ts` — `LEVEL_XP` |
| TPUT-FR-084 | Simulation Achievements | System SHALL evaluate simulation-based achievements and issue credentials | `modules/credentials/rules/simulation-achievement-rules.ts` |

### FR-GOV: Governance & Compliance (⚠️ NOT IN v1.0 SPEC)

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-090 | Audit Logging | System SHALL maintain immutable audit logs with actor, action, resource type/ID, IP address, and user agent | `modules/audit/service.ts` — `AuditLogEntry` |
| TPUT-FR-091 | Audit Query | System SHALL support filtered, paginated, date-ranged queries of audit logs with summary statistics | `modules/audit/service.ts` — `AuditLogQuery`, `AuditLogSummary` |
| TPUT-FR-092 | GDPR Data Export | System SHALL support user data export requests (GDPR Art. 20) with async processing and download links | `modules/compliance/service.ts` — `requestUserExport()` |
| TPUT-FR-093 | Right to Deletion | System SHALL support data deletion requests (GDPR Art. 17) with verification | `schema.prisma` — DataDeletionRequest, DeletionVerification |
| TPUT-FR-094 | Consent Management | System SHALL track consent records for AI-processed student data | `modules/compliance/types.ts` — `ConsentRecord` |
| TPUT-FR-095 | Data Retention Policy | System SHALL support configurable data retention policies per data category | `modules/compliance/types.ts` — `DataRetentionPolicy` |
| TPUT-FR-096 | Compliance Reporting | System SHALL generate compliance reports on demand | `modules/compliance/service.ts` — `ComplianceReport` |

### FR-TNT: Tenant Management (⚠️ NOT IN v1.0 SPEC)

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-100 | Tenant Configuration | System SHALL support per-tenant settings: registration policy, email verification, default roles, max classroom size, enabled features | `modules/tenant/service.ts` — `TenantSettings` |
| TPUT-FR-101 | Simulation Quotas | System SHALL enforce per-tenant simulation quotas: max concurrent sessions and monthly run limits | `modules/tenant/service.ts` — `simulationQuotas` |
| TPUT-FR-102 | Domain Pack Management | System SHALL support tenant-scoped Domain Packs containing curated simulation collections with visibility (private/institution/public) | `modules/tenant/service.ts` — `DomainPack` |

### FR-ARV: Auto-Revision Pipeline (⚠️ NOT IN v1.0 SPEC)

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-110 | Drift Detection | System SHALL detect content quality drift via signals: engagement_drop, high_abort_rate, low_completion, low_mastery, negative_feedback | `modules/auto-revision/service.ts` — `DriftSignal` |
| TPUT-FR-111 | Regeneration Queue | System SHALL queue content for AI-assisted regeneration with priority and estimated impact scoring | `modules/auto-revision/service.ts` — `RegenerationCandidate` |
| TPUT-FR-112 | A/B Testing | System SHALL support A/B experiments on content variants with sample size tracking, statistical significance testing (p-value, effect size), and automatic winner promotion | `modules/auto-revision/service.ts` — `ABExperiment` |
| TPUT-FR-113 | Content Needs Analysis | System SHALL analyze claim-level content needs based on Bloom level and domain context, suggesting examples, simulations, and animations | `modules/content-needs/service.ts` — `ContentNeedsAnalyzer` |
| TPUT-FR-114 | Knowledge Base Fact-Checking | System SHALL verify factual claims against domain knowledge base with confidence scoring, source attribution, and risk assessment | `modules/knowledge-base/service.ts` — `FactCheckResult` |
| TPUT-FR-115 | Curriculum Standard Alignment | System SHALL validate content against curriculum standards (e.g., CCSS) | `modules/knowledge-base/service.ts` — `CurriculumStandard` |

### FR-USR: User Management (⚠️ NOT IN v1.0 SPEC)

| ID | Title | Description | Evidence |
|:---|:---|:---|:---|
| TPUT-FR-120 | Teacher Dashboard | Teachers SHALL have a dashboard with classroom overview, student progress, and recent activity | `modules/user/teacher/service.ts` — `getTeacherDashboard()` |
| TPUT-FR-121 | Classroom Management | Teachers SHALL create classrooms, manage rosters, assign modules, and track per-classroom progress | `modules/user/teacher/service.ts` |
| TPUT-FR-122 | Institution Admin | Institution admins SHALL have tenant-level summaries, user management with pagination, usage metrics, and content administration | `modules/user/admin/service.ts` — `InstitutionAdminServiceImpl` |
| TPUT-FR-123 | Student Risk Indicators | Analytics SHALL compute student risk indicators with risk factors and levels | `modules/learning/analytics-service.ts` — `StudentRiskIndicator` |
| TPUT-FR-124 | Learning Pathways | System SHALL support curated learning pathways with ordered nodes, prerequisites, and enrollment tracking | `modules/learning/pathways-service.ts`, `schema.prisma` — LearningPath, LearningPathNode, LearningPathEnrollment |
| TPUT-FR-125 | Notifications | System SHALL support social notifications with per-user notification preferences | `schema.prisma` — SocialNotification, NotificationPreference |

---

## 5. Simulation Engine Requirements

### 5.1 Determinism

| Requirement | Value | Evidence |
|:---|:---|:---|
| Seed-based reproducibility | `runDeterministic(request, seed)` available | `physics-kernel.ts` |
| Current implementation | Deterministic by default (Euler integration, no random) | `physics-kernel.ts` — `runDeterministic` delegates to `run()` |
| ⚠️ Gap | Stochastic simulations (e.g., Monte Carlo, agent-based) not yet supported | No random seed integration observed |

### 5.2 Performance Targets

| Metric | Target | Evidence |
|:---|:---|:---|
| Max simulation steps | 200+ (tested) | `docs/tutorputor-day-by-day-impl-plan-simulation.md` — Day 8 perf smoke |
| Timestep | Configurable; default 1/60s (60 Hz) | `physics-kernel.ts` — `worldConfig.timeStep` |
| Physics iterations | 8 velocity, 3 position | `physics-kernel.ts` — `PhysicsWorldConfig` |
| Cache warming | LRU cache with TTL and hit rate tracking | `sim-runtime/physics-perf.ts` — `PhysicsCacheWarmer` |
| Worker offload | Worker pool for heavy computations | `sim-runtime/physics-perf.ts` — `PhysicsWorkerOffload` |
| Adaptive sampling | Downsampling of insignificant keyframes | `sim-runtime/physics-perf.ts` — `adaptiveDownsample()` |

### 5.3 Entity Limits

| Domain | Max Entities (observed) | Evidence |
|:---|:---|:---|
| CS_DISCRETE | Not explicitly limited; seed manifests use 5-10 nodes | Seed manifests |
| PHYSICS | Not explicitly limited; demo uses <10 bodies | Seed manifests |
| ECONOMICS | Stock-flow models with agents | Seed manifests |

### 5.4 Serialization Format

- **Format:** JSON
- **Schema:** `SimulationManifest` — full manifest including entities, steps, domainMetadata
- **State:** `PhysicsKernel.serialize()` → JSON with bodies, springs, entities, step index, time, config
- **Caching:** Hash-based cache keys with Redis/in-memory

### 5.5 Undo/Redo

| Capability | Implementation | Evidence |
|:---|:---|:---|
| Manifest undo | History stack with max 50 entries | `sim-nl/refinement-engine.ts` — `undoStack` |
| Entity state undo | Jotai history atoms with timestamps | `physics-simulation/state/atoms.ts` — `SimulationHistoryEntry` |

---

## 6. Learning & Pedagogy Requirements

### 6.1 Mastery Model

**Type:** Evidence-weighted CBM (Confidence-Based Marking)

**Scoring Matrix (CBM+):**

| | High Confidence | Medium Confidence | Low Confidence |
|:---|:---|:---|:---|
| **Correct** | +3 | +2 | +1 |
| **Incorrect** | -6 | -2 | 0 |

Evidence from: `learning-kernel/plugins/CBMProcessor.ts` — `DEFAULT_CBM_CONFIG`

**Note:** The more lenient matrix in `ClaimMastery.ts` uses -2 for incorrect+high (vs -6 in CBMProcessor). The CBMProcessor is the canonical implementation.

### 6.2 Evidence Types & Weights

| Evidence Type | Weight | Description | Evidence |
|:---|:---|:---|:---|
| `prediction_vs_outcome` | 0.3 | Did the learner predict correctly? With what confidence? | `ClaimMastery.ts` |
| `parameter_targeting` | 0.5 | Did the learner achieve the goal state by manipulating parameters? | `ClaimMastery.ts` |
| `explanation_quality` | 0.2 | Rubric-scored explanation quality | `ClaimMastery.ts` |
| `construction_artifact` | 0.4 | Quality of constructed artifacts | `ClaimMastery.ts` |

### 6.3 Adaptive Feedback Logic

The `LearnerInteractionAgent` implements adaptive feedback:

1. **Emotional state detection** from timing + attempt data
2. **Performance scoring** = base (0.5) + correctness (0.3) + first-attempt bonus (0.1) + no-hints bonus (0.1)
3. **Mastery threshold** = 0.85 with minimum 3 attempts
4. **Difficulty adjustment** based on learner history (planned, currently returns default)
5. **Knowledge gap detection** from interaction patterns (planned, currently returns empty)

### 6.4 Experiment-Driven Learning Cycle

```
1. PREDICT  → Learner forms hypothesis with confidence level
2. SIMULATE → Learner manipulates parameters, observes outcome
3. COMPARE  → System compares prediction to actual outcome
4. EXPLAIN  → Learner articulates understanding
5. REFLECT  → System identifies mastery, gaps, and next steps
```

Evidence: `contracts/v1/learning-unit.ts` — task types map directly to this cycle: `prediction`, `simulation`, `explanation`, `construction`

### 6.5 Knowledge Graph Behavior

**Concept Graph** is stored as JSON files per domain:
- `content/domains/physics.json` — 10+ concepts with prerequisite chains
- `content/domains/biology.json` — hierarchical concept tree

Each concept includes:
- `prerequisites[]` — Required prior concepts (e.g., `phy_F_1` → `phy_F_2`)
- `simulation_metadata` — Type, interactivity, purpose, time estimate
- `pedagogical_metadata` — Learning objectives, competencies, scaffolding, accessibility
- `cross_domain_links` — Cross-domain relationships
- `learning_object_metadata` — Author, version, status, audience

### 6.6 Prerequisite Enforcement

- Claims have `prerequisites?: string[]` (format: `LU_id.claim_id`)
- Learning path steps have `SimulationPrerequisite` with `type: "required" | "recommended"` and `minScore`
- LU Validator warns if university-level content lacks prerequisite claims

### 6.7 Hint Escalation Strategy

Assessment items support tiered hints via `SimulationHint[]` with:
- Unique `hintId` per level
- Sequential escalation (validated: no duplicate IDs)
- Hint level tracked per learner interaction for emotional state detection (≥3 hints → "frustrated")

### 6.8 Misconception Detection

- LU `Intent.targetMisconceptions` field explicitly records targeted misconceptions
- Overconfidence detection via Viva Engine (overconfident wrong pattern)
- CBM calibration index reveals systematic over/under-confidence

---

## 7. AI Integration Requirements

### 7.1 When AI Is Invoked

| Trigger | Agent | Evidence |
|:---|:---|:---|
| Content creation request | `ContentGenerationAgent` | `ContentGenerationAgent.java` — `perceive()` |
| Learner submits answer/asks hint | `LearnerInteractionAgent` | `LearnerInteractionAgent.java` — `perceive()` |
| Manifest generation from NL | `SimAuthorService` | `sim-author/service.ts` — `callAI()` |
| Manifest refinement from NL | `RefinementEngine` + AI fallback | `sim-nl/refinement-engine.ts` |
| Learning path generation | `AiLearningService` (gRPC) | `ai_learning.proto` |
| Assessment grading | `AiLearningService` (gRPC) | `ai_learning.proto` — `GradeAssessment` |
| Assessment item generation | `AiLearningService` (gRPC) | `ai_learning.proto` — `GenerateAssessmentItems` |
| Remediation suggestion | `AiLearningService` (gRPC) | `ai_learning.proto` — `SuggestRemediation` |

### 7.2 What AI Can / Cannot Modify

| AI Can | AI Cannot |
|:---|:---|
| Generate manifests | Bypass validation gates |
| Suggest parameters | Override administrator-set constraints |
| Adjust difficulty for learner | Access raw student PII |
| Generate hints and explanations | Self-approve low-confidence content (`needsReview` flag) |
| Create assessment items | Modify grading rubrics without human review |

### 7.3 Memory Retention Model (GAA Framework)

| Memory Type | Implementation | Evidence |
|:---|:---|:---|
| **Episodic** | Episodes with input, output, context, tags, reward | `Episode.builder()` in agents |
| **Semantic** | Facts as subject-predicate-object triples with confidence | `Fact.builder()` in `capture()` |
| **Procedural** | Policies as situation→action patterns with confidence | `Policy.builder()` in `extractSuccessfulPatterns()` |
| **Preference** | Learner preferences (content type, learning style) | `Preference` updates in `reflect()` |

Storage: `context.getMemoryStore()` — Event-sourced via GAA platform `agent-memory` module

### 7.4 Cost Control

| Control | Implementation | Evidence |
|:---|:---|:---|
| Rate Limiting | Requests/minute + tokens/minute tracking | `sim-author/service.ts` — `checkRateLimit()` |
| Retry Budget | Configurable `maxRetries` (default: 3) | `sim-author/service.ts` |
| Provider Fallback | Multi-provider support with automatic failover | `sim-author/ai-providers.ts` |
| Cache | Redis/in-memory caching to avoid duplicate AI calls | `sim-author/service.ts` — `cacheEnabled` |

### 7.5 Safety Guardrails

| Guardrail | Implementation | Evidence |
|:---|:---|:---|
| Content Quality Validation | `ContentQualityValidator` in act phase | `ContentGenerationAgent.java` — `act()` |
| Curriculum Alignment Check | `KnowledgeBaseService.checkCurriculumAlignment()` | `ContentGenerationAgent.java` — `act()` |
| Age-Appropriateness | Checked in quality validation | `ContentGenerationAgent.java` — documented in Javadoc |
| Schema Validation | Manifest validation before storage | `sim-author/validation.ts` |
| `needsReview` Flag | Low-confidence outputs flagged for human review | `contracts/v1/simulation/types.ts` |
| Failure Analysis | Quality < 0.6 triggers failure pattern identification and human flagging | `ContentGenerationAgent.java` — `identifyFailurePatterns()` |

### 7.6 Prompt Injection Mitigation

- System prompts are statically defined in prompt packs (not user-editable)
- User input goes only into `userPrompt` field, separated from system instructions
- Schema validation rejects malformed outputs
- ⚠️ **Gap:** No explicit prompt injection scanning or input sanitization layer observed

---

## 8. Non-Functional Requirements

### 8.1 Performance

| Metric | Requirement | Evidence |
|:---|:---|:---|
| Simulation step execution | < 16ms per frame (60 Hz) | `physics-kernel.ts` — Euler integration per frame |
| Manifest generation (AI) | Within SLA (not specified numerically) | Implementation plan — Day 19 acceptance |
| Keyframe caching | LRU with TTL; hit rate tracked | `physics-perf.ts` — `PhysicsCacheWarmer` |
| Render performance | PixiJS/Canvas 2D + optional R3F for 3D | `sim-renderer/`, `Physics3DRenderer.tsx` |
| Worker offload | Thread pool for compute-heavy simulations | `physics-perf.ts` — `PhysicsWorkerOffload` |

### 8.2 Scalability

| Dimension | Design | Evidence |
|:---|:---|:---|
| Multi-tenant | `tenantId` on all DB models; tenant-scoped indexes | `schema.prisma` — `@@index([tenantId, ...])` |
| Concurrent classrooms | Module + Enrollment model supports multiple cohorts | `schema.prisma` — Enrollment model |
| Offline mode | IndexedDB + ServiceWorker + mutation queue | `docs/offline-mode-spec.md` |
| Plugin extensibility | Kernel Registry for 3rd-party simulation kernels | `tutorputor-kernel-registry/` |

### 8.3 Reliability

| Requirement | Implementation | Evidence |
|:---|:---|:---|
| State serialization | `serialize()`/`deserialize()` on all kernels | `physics-kernel.ts` |
| Undo/redo | History stacks (manifests and entity state) | `refinement-engine.ts`, `atoms.ts` |
| Version tracking | `ModuleRevision` model; manifest `version` field | `schema.prisma` |
| Caching | Hash-based cache with TTL; warming for hot manifests | `physics-perf.ts` |
| Retry logic | Configurable retries for AI calls | `sim-author/service.ts` |

### 8.4 Security

| Requirement | Implementation | Evidence |
|:---|:---|:---|
| Multi-tenant isolation | `tenantId` scoping on all queries | `schema.prisma` |
| Authentication | JWT via `@fastify/jwt`; LTI 1.3 OIDC | `server.ts`, `lti/routes.ts` |
| Role-based access | `UserRole: student, teacher, admin, creator`; `requireRole()` helper | `contracts/v1/types.ts`, `core/http/requestContext.ts` |
| Helmet CSP | Content Security Policy with strict directives | `server.ts` — `@fastify/helmet` |
| CORS | Configurable origin with credentials support | `server.ts` — `@fastify/cors` |
| Stripe webhook security | Stripe SDK with API key management | `payments/service.ts` |
| Tenant-aware rate limiting | Redis-backed rate limits keyed by `{tenantId}:{userId}` | `core/middleware/rate-limit.ts` |
| Sentry PII filtering | Authorization headers and cookies stripped from error reports | `core/observability/error-tracking.ts` — `beforeSend` filter |
| SSO/Identity providers | Multi-IdP support with SSO user linking | `schema.prisma` — IdentityProvider, SsoUserLink |
| ⚠️ Gap | No explicit prompt injection scanning layer | NL input → AI without sanitization |
| ⚠️ Gap | JWT secret defaults to `"change-me-in-production"` | `server.ts` — hardcoded fallback |

### 8.5 Observability

| Dimension | Implementation | Evidence |
|:---|:---|:---|
| **Error Tracking** | Sentry with profiling, traces, unhandled rejection capture | `core/observability/error-tracking.ts` — `@sentry/node` + `@sentry/profiling-node` |
| **Metrics** | Prometheus-style metrics endpoint; `context.recordMetric()` in agents | `core/observability/metrics.ts`, agents |
| **Health Checks** | Production health check service with 30s polling, DB/Redis probes | `modules/monitoring/health-check.ts` — `HealthCheckService` |
| Simulation Analytics | Play, Pause, Seek, Speed Change, Complete, Error events | `contracts/v1/simulation/types.ts` — `SimulationAnalyticsEvent` |
| Real-time Event Streaming | Redis Streams (`learning:events:{tenantId}`) + Pub/Sub | `modules/learning/analytics-service.ts` — `redis.xadd()` + `redis.publish()` |
| Plugin metrics | Per-plugin `durationMs` in processing results | `contracts/v1/plugin-interfaces.ts` — `ProcessingResult` |
| Agent metrics | Quality trends, calibration indexes, failure rates | `ContentGenerationAgent.java` — `reflect()` |
| WebSocket real-time | Platform-level WebSocket client with auto-reconnect, heartbeat | `platform/typescript/realtime/` — `WebSocketClient`, `useWebSocket` hook |
| Tracing | OpenTelemetry referenced in architecture; Sentry traces active | `error-tracking.ts` — `tracesSampleRate` |
| ⚠️ Gap | OTEL span propagation not fully wired across services | Only Sentry tracing observed |

---

## 9. Extensibility & Plugin Model

### 9.1 Domain Module Contract

New STEAM domains are added via:

1. **Schema extension** — Add domain entity types + actions to `contracts/v1/simulation/types.ts`
2. **Kernel implementation** — Implement `SimKernelService` interface (via `BaseKernel` from SDK)
3. **Prompt pack** — Add domain-specific few-shot examples to `sim-author/prompt-packs.ts`
4. **Renderer** — Implement domain-specific entity renderers in `sim-renderer/`
5. **Content seeding** — Add concept graph JSON to `content/domains/`
6. **Kernel registration** — Register kernel in registry service

Evidence: The 6 implemented domains (CS_DISCRETE, PHYSICS, ECONOMICS, CHEMISTRY, BIOLOGY, MEDICINE) all follow this pattern.

### 9.2 Plugin Model (Learning Kernel)

**Plugin Types:**
- `evidence_processor` — Processes learner evidence (CBM, BKT, IRT)
- `ingestor` — Normalizes external data (xAPI, Caliper)
- `authoring_tool` — CMS authoring extensions
- `asset_provider` — Content delivery plugins
- `notifier` — Notification plugins

**Plugin Contract:**
```typescript
interface Plugin {
  readonly metadata: PluginMetadata; // id, name, version, type, priority
  initialize?(): Promise<void>;
  shutdown?(): Promise<void>;
}
```

**Execution:** Pipeline runner executes plugins in priority order (highest first) with shared `ProcessingContext` (Blackboard pattern). Plugins can halt the pipeline via `context.halt`.

**Marketplace:** Kernel Registry service at `/api/v1/plugins` supports plugin submission with:
- Policy validation (version compatibility, prohibited dependencies scan)
- Status lifecycle: pending → approved → rejected → deprecated
- Metadata: downloads, rating, versions
- Plugin types: kernel, promptPack, visualizer

Evidence: `learning-kernel/registry/PluginRegistry.ts`, `tutorputor-kernel-registry/routes/plugins.ts`

### 9.3 Simulation Kernel SDK

The `BaseKernel` abstract class provides:
- `KernelState<TEntity>` — Generic state snapshot
- `KernelAnalytics` — Execution metrics (step timings, peak memory)
- `KernelHooks<TConfig>` — Lifecycle hooks for custom initialization

Custom kernels implement `SimKernelService`:
- `canExecute(manifest)` — Domain matching
- `run(request)` → `SimulationRunResult` with keyframes
- `runDeterministic(request, seed)` — Seeded execution
- `step()` — Single step execution
- `serialize()`/`deserialize()` — State persistence
- `getAnalytics()` — Runtime metrics

Evidence: `sim-sdk/base-kernel.ts`

### 9.4 Versioning Strategy

| Level | Strategy | Evidence |
|:---|:---|:---|
| Manifest | SemVer in `version` field | `SimulationManifest.version` |
| Module | Integer version + revision history | `schema.prisma` — `version Int`, `ModuleRevision` model |
| Kernel | SemVer in plugin metadata | `PluginMetadata.version` |
| Template | `parentVersionId` for lineage tracking | `SimulationTemplate.parentVersionId` |
| Content | `learning_object_metadata.version` | `content/domains/*.json` |

### 9.5 Backward Compatibility

- Manifest schema uses optional fields for domain-specific extensions
- Template governance tracks parent versions
- Plugin registry supports multiple versions per plugin
- ⚠️ **Gap:** No explicit schema migration strategy for manifest format changes

---

## 10. Traceability Matrix

| Requirement ID | Evidence (Path) | Test Evidence | Notes |
|:---|:---|:---|:---|
| TPUT-FR-001 | `services/tutorputor-sim-runtime/src/physics-kernel.ts` | E2E `e2e/simulation-playback.spec.ts` | ✅ Complete |
| TPUT-FR-002 | `contracts/v1/simulation/types.ts` | Contract tests referenced in impl plan | ✅ 8 domains defined |
| TPUT-FR-003 | `services/tutorputor-sim-runtime/src/physics-kernel.ts` | Unit tests referenced in Day 10 | ✅ Euler integration |
| TPUT-FR-004 | `physics-kernel.ts` — `runDeterministic()` | Referenced in Day 10 acceptance | ✅ Delegates to deterministic `run()` |
| TPUT-FR-005 | `physics-kernel.ts` — `step()` produces keyframes | Day 3 acceptance criteria | ✅ |
| TPUT-FR-006 | `physics-kernel.ts` — `serialize()`/`deserialize()` | — | ✅ JSON format |
| TPUT-FR-007 | `physics-kernel.ts` — `applyPhysicsAction()` | Unit tests | ✅ 7 action types |
| TPUT-FR-008 | `physics-kernel.ts` — `applySpringForces()` | — | ✅ Hooke's law |
| TPUT-FR-011 | `services/tutorputor-sim-nl/src/intent-parser.ts` | — | ✅ 15 intent types |
| TPUT-FR-012 | `services/tutorputor-sim-nl/src/refinement-engine.ts` | — | ✅ Undo/redo stack |
| TPUT-FR-013 | `services/tutorputor-sim-author/src/service.ts` | Day 2 acceptance | ✅ 6 domain prompt packs |
| TPUT-FR-020 | `libs/learning-kernel/src/plugins/CBMProcessor.ts` | — | ✅ Full CBM+ implementation |
| TPUT-FR-023 | `libs/learning-engine/src/analytics/ClaimMastery.ts` | — | ✅ Weighted evidence scoring |
| TPUT-FR-025 | `libs/learning-engine/src/analytics/VivaEngine.ts` | — | ✅ 3 detection patterns |
| TPUT-FR-027 | `libs/learning-engine/src/validation/LearningUnitValidator.ts` | — | ✅ 9 gates |
| TPUT-FR-030 | `libs/assessments/src/simulation-item.ts` | — | ✅ 3 modes + builder |
| TPUT-FR-040 | `libs/content-studio-agents/src/main/java/.../ContentGenerationAgent.java` | Compile tests | ✅ 5-phase lifecycle |
| TPUT-FR-047 | `libs/content-studio-agents/src/main/java/.../LearnerInteractionAgent.java` | — | ✅ Emotional state detection |
| TPUT-FR-050 | `services/tutorputor-db/prisma/schema.prisma` | — | ✅ Full Prisma schema |
| TPUT-FR-060 | `services/tutorputor-platform/src/modules/integration/lti/routes.ts` | — | ✅ JWT launch + JWKS |
| TPUT-FR-062 | `services/tutorputor-platform/src/modules/payments/service.ts` | — | ✅ Stripe integration |
| TPUT-FR-070 | `services/tutorputor-platform/src/modules/engagement/social/study-groups.ts` | — | ✅ Implementation exists; ❌ No tests |
| TPUT-FR-071 | `services/tutorputor-platform/src/modules/engagement/social/forums.ts` | — | ✅ Implementation exists; ❌ No tests |
| TPUT-FR-072 | `services/tutorputor-platform/src/modules/engagement/social/peer-tutoring.ts` | — | ✅ Implementation exists; ❌ No tests |
| TPUT-FR-073 | `services/tutorputor-platform/src/modules/engagement/social/chat.ts` | — | ✅ Implementation exists; ❌ No tests |
| TPUT-FR-080 | `services/tutorputor-platform/src/modules/engagement/gamification/service.ts` | — | ✅ Points/badges/leaderboards; ❌ No tests |
| TPUT-FR-090 | `services/tutorputor-platform/src/modules/audit/service.ts` | — | ✅ Full audit logging; ❌ No tests |
| TPUT-FR-092 | `services/tutorputor-platform/src/modules/compliance/service.ts` | — | ⚠️ Partial — request not persisted; ❌ No tests |
| TPUT-FR-100 | `services/tutorputor-platform/src/modules/tenant/service.ts` | — | ✅ Tenant config + quotas; ❌ No tests |
| TPUT-FR-110 | `services/tutorputor-platform/src/modules/auto-revision/service.ts` | — | ✅ Drift + A/B; ❌ No tests |
| TPUT-FR-114 | `services/tutorputor-platform/src/modules/knowledge-base/service.ts` | `knowledge-base/__tests__/service.test.ts` | ✅ Fact-checking with tests |
| TPUT-FR-120 | `services/tutorputor-platform/src/modules/user/teacher/service.ts` | — | ✅ Teacher dashboard; ❌ No tests |
| TPUT-FR-123 | `services/tutorputor-platform/src/modules/learning/analytics-service.ts` | — | ✅ Risk indicators; ❌ No tests |

---

## 11. Gaps & Risks

### 11.1 Pedagogical Risks

| Risk | Severity | Details |
|:---|:---|:---|
| **Inconsistent CBM scoring matrices** | High | `ClaimMastery.ts` uses -2 for incorrect+high while `CBMProcessor.ts` uses -6. Two divergent scoring paths produce incompatible mastery signals. Need canonical authority and deprecation of the lenient matrix. |
| **BKT/IRT not implemented** | Medium | Plugin interfaces exist (`EvidenceProcessor`) but no BKT (Bayesian Knowledge Tracing) or IRT (Item Response Theory) plugins found in code. Referenced in kernel example but not shipped. This limits adaptive precision. |
| **Learning velocity not tracked** | Medium | `LearnerInteractionAgent` references "analyzing learning velocity" in reflect phase but implementation returns `Promise.complete()` early. Without velocity, pacing adaptation is impossible. |
| **Prerequisite enforcement is advisory** | Medium | Prerequisites exist in schema but no runtime enforcement prevents skipping ahead. A student can access advanced topics without completing foundational claims. |
| **Gamification may undermine intrinsic motivation** | Low | Full points/badges/leaderboard system exists but no A/B testing of gamification's impact on deep learning outcomes vs. surface engagement observed. |

### 11.2 Simulation Stability Risks

| Risk | Severity | Details |
|:---|:---|:---|
| **No collision detection** | High | Physics kernel only checks ground plane (y≥500). No body-to-body collision detection, broadphase, or resolution. Simulations with interacting objects produce incorrect results. |
| **Euler instability** | High | Euler integration can diverge with large timesteps or stiff springs. No RK4/Verlet integrator for physics (RK4 exists only for economics system dynamics). This is dangerous for educational accuracy. |
| **Rapier WASM not integrated** | Medium | Architecture plans reference Rapier WASM upgrade. Current physics is custom Euler. The planned upgrade is needed to address both collision detection and numerical stability. |
| **No entity count limits** | Low | No explicit maximum entity count per simulation. Could cause browser tab crashes with large manifests. |
| **Simulation session TTL** | Low | Session timeout is 30 min (`SESSION_TIMEOUT_MS`). No graceful session migration or resumption after timeout. |

### 11.3 AI Agent Risks

| Risk | Severity | Details |
|:---|:---|:---|
| **No prompt injection scanning** | Critical | User NL input goes directly to AI providers without sanitization layer. System/user prompt separation exists but no explicit injection detection (e.g., instruction-following attacks). |
| **Learner enrichment stubbed** | High | `loadLearnerPreferences()`, `adjustDifficultyForLearner()`, `detectKnowledgeGaps()` all return default values. The entire adaptive personalization chain is non-functional. |
| **Cost monitoring is per-service** | Medium | Rate limiting exists in sim-author but no cross-service cost aggregation, budget enforcement, or alerting. A tenant could exhaust AI budget without warning. |
| **Reflection requires 5+ episodes** | Low | Pattern extraction only fires after 5 domain episodes. Cold-start agents have no policy optimization, meaning first interactions for new domains are unoptimized. |
| **AI content generation lacks rollback** | Medium | `ContentGenerationAgent` generates content and stores it, but `AIGenerationLog` is append-only with no automated rollback mechanism for quality regressions. |

### 11.4 Scalability Risks

| Risk | Severity | Details |
|:---|:---|:---|
| **SQLite in dev, PostgreSQL unverified** | High | Prisma configured for SQLite. Production PostgreSQL path not verified in codebase. Migration strategy between engines not documented. |
| **In-memory caching** | Medium | `PhysicsCacheWarmer` uses in-memory LRU. Redis is used by sim-runtime for session state and by analytics for event streaming, but kernel-level caching is not Redis-backed. |
| **No horizontal scaling plan** | Medium | Platform service is a monolith with 19 modules. No queue-based worker architecture for simulation execution. Content workers exist but are co-located. |
| **106 models in single Prisma schema** | Medium | Extremely large schema risks slow migrations, complex dependency graphs, and developer cognitive overload. No domain decomposition or schema splitting strategy. |
| **Redis as single point of failure** | Medium | Redis is used for: rate limiting, session state, event streaming, pub/sub. No Redis Sentinel/Cluster configuration observed. |

### 11.5 Data Governance Gaps

| Gap | Severity | Details |
|:---|:---|:---|
| **Compliance module is partially implemented** | High | GDPR export exists but `requestUserExport()` stores request in memory (not persisted). `DataRequest` model comment says "not in current schema". The `findById()` method returns `null` always. The `update()` method for credential revocation returns `null` with a warning. |
| **No PII field classification** | High | Student data stored in 106 models without explicit PII tags, FERPA/COPPA field-level annotations, or data sensitivity classification. |
| **Data retention policy exists in types only** | Medium | `DataRetentionPolicy` type is defined in compliance module but no enforcement or scheduled cleanup observed. Learning events and episodes accumulate without TTL. |
| **Consent management defined but not enforced** | Medium | `ConsentRecord` type exists but no middleware checks consent status before AI-processing student data. |
| **Audit log has no tamper protection** | Low | AuditLog is a regular Prisma model. No append-only guarantees, checksumming, or external log shipping for tamper evidence. |

### 11.6 Social Feature Risks

| Gap | Severity | Details |
|:---|:---|:---|
| **Content moderation minimal** | High | Forums have `requireModeration` flag but no AI/ML content moderation pipeline for detecting harmful content in student-generated posts, chat messages, or shared notes. |
| **Chat has no end-to-end encryption** | Medium | ChatMessage stored in plaintext. For minors (K-12), this raises COPPA concerns. |
| **Peer tutoring trust/safety** | Medium | TutoringSession connects students with peer tutors but no background verification, session monitoring, or inappropriate behavior detection. |
| **Study group size unbounded** | Low | `maxMembers` defaults to 50 but no validation on extremely large values. |

### 11.7 VR Subsystem Status

| Gap | Severity | Details |
|:---|:---|:---|
| **VR has schema but limited runtime** | Medium | 8 comprehensive database models (VRLab, VRScene, VRInteractable, VRLabObjective, VRSession, VRMultiplayerSession, VRAsset, VRAnalyticsEvent) exist suggesting a designed system, but `services/tutorputor-vr/` contains minimal runtime implementation. Schema work suggests intention but execution is incomplete. |

### 11.8 Test Coverage Gaps

| Gap | Severity | Details |
|:---|:---|:---|
| **73 test files total** (55 TS + 18 Java) | — | Test inventory across the entire product |
| **Learning engine analytics has no tests** | High | `ClaimMastery.ts`, `VivaEngine.ts` — no test files found. These are critical scoring algorithms affecting student grades. |
| **CBMProcessor has no tests** | High | The canonical CBM scoring plugin has no regression tests despite impacting all assessment scoring. |
| **Social modules have no tests** | High | Study groups, forums, peer tutoring, chat — 18 database models with zero test files. |
| **Compliance module has no tests** | High | GDPR data export/deletion code is untested. Risk of data leaks in production. |
| **Auto-revision pipeline untested** | Medium | A/B experiment statistical analysis and drift detection have no regression tests. |
| **6 E2E Playwright tests exist** | — | `simulation-playback.spec.ts`, `simulation-tutor.spec.ts`, `cross-domain-simulation.spec.ts`, `cms-authoring.spec.ts`, `simulation-assessment.spec.ts`, `instructor-simulation-analytics.spec.ts` |
| **Platform service has some integration tests** | — | `pathways-service.test.ts`, `assessment-service.test.ts`, `routes.test.ts`, `knowledge-base/service.test.ts`, `ai/routes.test.ts`, `ai/OllamaAIProxyService.test.ts` |
| **Offline mode is spec-only** | Low | `offline-mode-spec.md` exists but no implementation |
| **Mobile app is scaffold** | Low | `tutorputor-mobile/` exists with minimal content |

---

## 12. Coverage Score

### Requirement Coverage Model

| Dimension | Score | Notes |
|:---|:---|:---|
| **Simulation Engine — Evidence** | 95% | 6 domain kernels implemented, USP protocol complete, NL interface working |
| **Simulation Engine — Contracts** | 95% | Comprehensive TypeScript contracts with branded IDs, 8 domains typed |
| **Simulation Engine — Tests** | 60% | E2E tests exist; unit tests referenced; sim-author has spec tests |
| **Learning Engine — Evidence** | 85% | CBM, Viva, Mastery, Validation all implemented |
| **Learning Engine — Contracts** | 90% | LearningUnit, Claims, Evidence, Tasks fully typed |
| **Learning Engine — Tests** | 20% | No dedicated test files for CBM, Viva, or ClaimMastery analytics |
| **AI Agents — Evidence** | 75% | Two GAA agents implemented; enrichment methods stubbed; no injection protection |
| **AI Agents — Contracts** | 85% | gRPC protos + Java interfaces complete |
| **AI Agents — Tests** | 40% | Compilation verified; behavioral tests unclear; some AI proxy tests exist |
| **Assessment — Evidence** | 80% | 3 modes, builder pattern, grading strategies, CBM integration |
| **Assessment — Contracts** | 85% | Full assessment types in contracts |
| **Assessment — Tests** | 35% | `simulation-item.spec.ts` exists; no scoring regression tests |
| **Plugin System — Evidence** | 90% | Registry, Pipeline, CBM plugin, XAPI ingestor all implemented |
| **Plugin System — Contracts** | 95% | Full plugin interface hierarchy |
| **Plugin System — Tests** | 40% | Registry has test potential but low observed coverage |
| **Social Learning — Evidence** | 70% | Study groups, forums, chat, peer tutoring all have service/route implementations |
| **Social Learning — Contracts** | 75% | Types exist; 18 DB models defined |
| **Social Learning — Tests** | 0% | No test files found for any social module |
| **Gamification — Evidence** | 80% | Points, badges, leaderboards, levels, achievements implemented |
| **Gamification — Tests** | 0% | No test files found |
| **Auto-Revision — Evidence** | 75% | Drift detection, regeneration queue, A/B testing implemented |
| **Auto-Revision — Tests** | 0% | No test files found |
| **Compliance/Governance — Evidence** | 50% | Types defined; export partially implemented; deletion exists in schema; audit log works |
| **Compliance/Governance — Tests** | 0% | No test files found |
| **Tenant Management — Evidence** | 80% | Config, quotas, domain packs implemented with routes |
| **Tenant Management — Tests** | 0% | No test files found |
| **Frontend — Evidence** | 85% | Player, Canvas, 2D/3D renderers, CMS, state management, AI tutor, dashboard |
| **Frontend — Tests** | 55% | 6 E2E Playwright specs + component tests (ConceptEditor, DashboardPage, AITutorPage, etc.) |
| **NFR — Performance** | 70% | Cache warming, worker offload, adaptive sampling implemented |
| **NFR — Security** | 65% | Helmet, CORS, JWT, rate limiting, Sentry PII filtering; prompt injection gap |
| **NFR — Observability** | 70% | Sentry error tracking + profiling; Redis event streaming; health checks; metrics endpoint |
| **Database Schema** | 90% | 106 models, 61 enums covering all product domains comprehensively |

### Overall Scores

| Metric | Score |
|:---|:---|
| **Evidence Completeness** | **78%** — Core simulation, learning, AI, and social systems implemented; compliance/enrichment stubbed |
| **Contract Completeness** | **88%** — TypeScript + Protobuf contracts are thorough; social types less formal |
| **Test Coverage** | **28%** — 73 test files across 106+ models and 19 modules; critical scoring algorithms untested |
| **NFR Linkage** | **68%** — Security and observability significantly better than v1.0 assessed; compliance gaps remain |
| **Architectural Fidelity** | **85%** — Simulation-first philosophy preserved; hybrid backend implemented; monolith coherent |
| **Feature Completeness** | **72%** — Social, gamification, VR, offline, and mobile are incomplete or scaffolded |

### **Final Completeness Score: 65%**

**v1.0 scored 72% but missed 11+ modules and ~60% of the database schema.** The corrected score is **65%**, lower because:

1. **Social learning** (study groups, forums, chat, peer tutoring) is extensive but entirely untested
2. **Compliance module** is partially implemented — the data export pipeline doesn't persist requests
3. **Learner personalization** (the core differentiator) has stubbed enrichment methods
4. **No prompt injection protection** on a platform serving K-12 students
5. **Physics engine** has no collision detection and uses numerically unstable Euler integration
6. **73 test files** for a system with 106 DB models, 19 platform modules, and 6 simulation kernels is inadequate

**What's strong:**
- Simulation engine architecture (USP manifest protocol, domain kernels, NL interface)
- Contract/type system (comprehensive branded types, full entity hierarchy)
- Learning pedagogy model (CBM+, Viva Engine, 9-gate publishing, claim mastery weights)
- GAA agent lifecycle (5-phase with memory, reflection, policy learning)
- Database schema design (well-normalized, multi-tenant, comprehensive relationships)
- Observability foundation (Sentry, Redis streams, health checks)

---

> **Document generated by reverse-engineering from source code.**
> **Code is authoritative. Gaps marked with ⚠️ are genuine absences, not assumptions.**

### Revision History

| Version | Date | Changes |
|:---|:---|:---|
| 1.0.0 | 2026-03-02 | Initial reverse-engineering — core simulation, learning, AI, and assessment |
| 1.1.0 | 2026-03-03 | **Major review pass**: Added 11 undocumented platform modules (social, gamification, compliance, audit, tenant, auto-revision, content-needs, knowledge-base, teacher/admin, monitoring). Expanded DB inventory from ~10 to 106 models. Added 55+ new functional requirements (FR-070→FR-125). Corrected architecture from "microservices" to "consolidated modular monolith". Updated security/observability with Sentry, Redis streams, rate limiting, WebSocket. Expanded gaps from 11 to 30+ with severity re-assessment. Corrected completeness score from 72% to 65%. |
