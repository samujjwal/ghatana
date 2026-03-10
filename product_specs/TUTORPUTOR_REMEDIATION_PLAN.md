# TUTORPUTOR REMEDIATION & IMPLEMENTATION PLAN

> **Status**: DRAFT
> **Target Version**: 2.1.0
> **Based on**: Reverse Engineered Spec v2.0.0

This document outlines the critical path to stabilize the TutorPutor platform, address architectural gaps, and bring all modules to a passing state.

---

# 1. Critical "Fix-First" Strategy (Phase 1)

The following modules are currently in `FAIL` state and block the core "Concept-through-Experiment" loop. They must be fixed in this order:

## 1.1 Foundation Layer

| Module                        | Current State | Remediation Action                                                                                                  | Difficulty |
| :---------------------------- | :------------ | :------------------------------------------------------------------------------------------------------------------ | :--------- |
| `libs/tutorputor-contracts`   | **PASS**      | maintain strict schema versioning.                                                                                  | Low        |
| `services/tutorputor-sim-sdk` | **FAIL**      | Fix type errors in `base-kernel.ts` and `manifest-schema.ts`. Ensure Zod schemas align with the latest `contracts`. | Low        |
| `libs/learning-kernel`        | **FAIL**      | Fix `PipelineRunner` tests. Essential for evidence processing.                                                      | Medium     |

## 1.2 Core Runtime

| Module                            | Prioritized Issues                                                                                                         |
| :-------------------------------- | :------------------------------------------------------------------------------------------------------------------------- |
| `services/tutorputor-sim-runtime` | Connect `SimKernelService` (SDK) with `ioredis` for state persistence. Fix build errors related to missing contract types. |
| `libs/physics-simulation`         | Validate physics engine determinism. Add unit tests for `step()` execution.                                                |

## 1.3 Data Layer

| Module                   | Prioritized Issues                                                                                                                                                            |
| :----------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `services/tutorputor-db` | **CRITICAL**: Fix migration failures. The schema (`vocab`, `users`) likely has drift. <br> **Action**: Run `prisma migrate resolve` or reset dev DB to match `schema.prisma`. |

---

# 2. AI Service Implementation (Phase 2)

The current `AiLearningServiceImpl` is a stub. We need to implement the real **GenAI Orchestrator**.

## 2.1 Architecture Update

Use the **Ghatana AI Gateway** (shared service) instead of direct LLM calls.

**Implementation Steps:**

1.  **Define Prompts**: created `services/tutorputor-ai-agents/src/main/resources/prompts/learning-path-generator.st` (StringTemplate).
2.  **Java Implementation**:
    - Inject `AiGatewayClient`.
    - Implement `generateLearningPath` to call Gateway with `temperature=0.7`.
    - Parse JSON response into `AiLearningProto` objects.
3.  **Prompt Engineering**:
    - _System_: "You are an expert curriculum designer for K-12 STEAM."
    - _Output_: Enforce strict JSON schema matching the Proto definition.

---

# 3. Frontend & Simulation Authoring (Phase 3)

The `NLAuthorPanel` exists but needs integration with the real AI backend.

## 3.1 Tasks

1.  **Canvas Testing**: Add Playwright tests for `SimulationAuthoringWorkspace`. Ensure dragging an entity updates the Manifest JSON.
2.  **Service Integration**: Connect React `NLAuthorPanel` -> `BFF (Platform)` -> `gRPC (AI Agents)`.
3.  **Visual Regression**: Snapshot tests for `VisualizerPlugin` rendering (ensure simulated balls look like balls).

---

# 4. Architecture Drift & Tech Debt

## 4.1 Identified Gaps

- **Queue Reliability**: `TUTORPUTOR_FLOW_MAP` notes that queue integration tests are missing.
  - _Fix_: Add integration tests for `BullMQ` producers/consumers in `tutorputor-platform`.
- **Legacy References**: Scripts check for legacy utils.
  - _Fix_: Strict enforcement of `libs/*` usage (no `shared/*`).
- **Event Bus**: Ensure `EvidenceEvents` are published to the global Event Bus (Kafka/RabbitMQ) for analytics, not just local processing.

## 4.2 Database Schema Refactoring

The `schema.prisma` is too large (2700+ lines).

- **Action**: Split into `prisma/schema` sub-files if supported, or strictly organize by domain comments (User, Content, Sim, Social).
- **Indexes**: Review indexes on `EvidenceEvent` table for write performance.

---

# 5. Execution Roadmap

## Week 1: Stabilization

- [ ] Fix `tutorputor-sim-sdk` build.
- [ ] Fix `tutorputor-db` migrations.
- [ ] Get `tutorputor-sim-runtime` building.

## Week 2: AI & Logic

- [ ] Implement `AiLearningServiceImpl` (Real Logic).
- [ ] Fix `learning-kernel` pipeline tests.
- [ ] Deploy `tutorputor-ai-agents`.

## Week 3: UI & Integration

- [ ] Wire up `NLAuthorPanel` to Backend.
- [ ] End-to-End Test: "Create Sim -> AI Generate -> Play Sim".

---

# 6. Immediate Next Action

**Recommendation**: Start with **1.1 Foundation Layer**. If `tutorputor-sim-sdk` is broken, nothing else works.

**Command**:
`cd services/tutorputor-sim-sdk && pnpm build`
