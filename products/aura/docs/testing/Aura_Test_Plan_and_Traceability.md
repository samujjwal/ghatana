# Aura Test Plan and Traceability Matrix

Version: 1.0
Date: March 13, 2026
Primary Source of Truth: `../Aura_Master_Platform_Specification.md`

## Purpose

This document turns the Aura documentation set into a test-driven-development guide.

It has four jobs:

1. Define how Aura test cases are organized and named.
2. Map every Aura source document to one or more test suites.
3. Define the minimum environments, personas, and data fixtures needed to execute the cases.
4. Provide a repeatable coverage model so new implementation work can add tests before code.

## How To Use This Suite

1. Start every feature by locating the relevant source document row in the traceability matrix.
2. If the work is scheduled, find the matching task row in `../Aura_Task_Execution_Matrix.md` before implementation.
3. For Weeks 25-104 work, also use `../Aura_Long_Horizon_Task_Execution_Matrix.md`.
4. Copy the matching test cases into the implementation backlog before writing code.
5. Implement unit and contract tests first, then integration tests, then end-to-end flows.
6. Treat every newly introduced branch, enum value, event type, consent scope, and error mode as a trigger to extend the suite.
7. When a source doc changes, update this matrix first, then update the detailed test documents.

## Test Levels

| Level | Meaning | Typical Aura Scope |
| ----- | ------- | ------------------ |
| Unit | Smallest executable rule or branch | ranking formula, shade score math, filter predicates, enum serialization |
| Contract | API/schema/event shape verification | GraphQL fields, REST payloads, Prisma models, event schemas |
| Integration | Cross-component behavior | gateway to services, service to DB, ingestion to enrichment, event consumer behavior |
| E2E | User-visible journey across system boundaries | onboarding, feed, compare, assistant, consent center, export/delete |
| Non-Functional | Quality attributes | latency, accessibility, observability, fairness, security, resilience |
| Validation Experiment | Market and business hypothesis test | GTM loops, trust comprehension, creator traffic quality, premium packaging readiness |

## Case ID Scheme

| Prefix | Domain |
| ------ | ------ |
| `AURA-PUX` | Product, UX, privacy, accessibility |
| `AURA-AIK` | AI, recommendation, ontology, knowledge models |
| `AURA-ADE` | API, data, database, events |
| `AURA-OPS` | Platform architecture, performance, security, observability, resilience |
| `AURA-BIZ` | Strategy, GTM, monetization, validation experiments |

Case numbers should be stable. If a case is retired, do not reuse its ID.

## Required Test Environments

| Environment | Use | Minimum Requirements |
| ----------- | --- | -------------------- |
| Local Dev | TDD loop for unit/contract tests | seeded DB or Data Cloud test plugin, stubbed product catalog, fake auth, local AEP substitute |
| CI | mandatory regression gate | deterministic seeds, snapshot fixtures, schema contract validation, accessibility scan |
| Integration/Staging | service and workflow validation | Data Cloud-managed datasets/plugins, AEP, auth, security, observability, seeded partner/catalog data |
| Pre-Launch Shadow | production-like safety checks | shadow recommendation scoring, canary support, drift dashboards, audit log verification |
| Performance/Chaos | scale, failover, soak, and recovery validation | load generator, traffic profiles, fault injection, restore environment, AEP and Data Cloud observability |

## Required Core Personas and Fixtures

| Fixture ID | Persona / Data Shape | Primary Uses |
| ---------- | -------------------- | ------------ |
| `USR-NEW-001` | New user, no profile, no optional consent | cold start, onboarding, confidence fallback |
| `USR-BEAUTY-002` | Declared skin type, undertone, allergies, ethical prefs | standard recommendation flows |
| `USR-SAFE-003` | Fragrance allergy, sensitive skin, adverse reaction history | ingredient safety, exclusion branches |
| `USR-SHADE-004` | Complete complexion profile with supported foundation history | shade matching, shade feedback |
| `USR-CONTEXT-005` | Complete profile plus optional bio/context consent | context engine, high-sensitivity consent |
| `USR-COMMUNITY-006` | Community contribution and public profile consent enabled | community flows, verified review handling |
| `USR-LOWBUDGET-007` | Strong spending ceiling and ethical filters | price fit, silent exclusion, compare |
| `USR-EXPORT-008` | Mature account with recommendations, feedback, outcomes | data export and deletion |
| `CAT-FOUNDATION-001` | Supported complexion catalog with shade metadata | shade ranking, compare, returns |
| `CAT-SKINCARE-002` | Full ingredient lists and conflict pairs | safety model, routine conflict, dupes |
| `SRC-STALE-003` | Product source records with stale price/freshness | trust flags, commerce behavior |
| `REV-MIXED-004` | Review corpus with cohort-specific sentiment split | community intelligence, fairness |

## Coverage Rules

| Rule | Requirement |
| ---- | ----------- |
| Branch coverage | Every decision branch described in docs must have at least one positive and one negative case where feasible. |
| Enum coverage | Every enum or code value must have serialization, persistence, API, and event coverage. |
| Consent coverage | Every optional/high-sensitivity scope must have grant, revoke, deny, expired, and unauthorized cases. |
| Outcome coverage | Every supported post-use outcome must flow through UX, API, persistence, analytics, and learning. |
| Trust coverage | Confidence, evidence, affiliate labels, and trust flags must be asserted in all relevant user journeys. |
| Fairness coverage | Any cohort-sensitive logic must be validated across skin tone depth, skin type, and price preference segments. |
| Scale coverage | Critical user and worker paths must have burst, sustained load, and backlog-recovery cases. |
| Recovery coverage | Critical stores and dependencies must have outage, replay, backup/restore, and rollback cases. |
| Reuse regression coverage | Shared Ghatana libraries and services used by Aura must trigger impacted contract and regression tests when they change. |
| Shared platform boundary coverage | Async paths must use AEP, managed data paths must use Data Cloud or approved plugins, and shared security/o11y integration must be regression-tested. |

## Source Document to Test Suite Matrix

| Source Document | Primary Test Suites | Coverage Notes |
| --------------- | ------------------- | -------------- |
| `../Aura_Master_Platform_Specification.md` | `PUX`, `AIK`, `ADE`, `OPS`, `BIZ` | Governing doc; every suite traces back here |
| `../Aura_Canonical_Platform_Specification.md` | `BIZ`, `OPS` | Navigation and governance consistency only |
| `../Aura_Glossary.md` | `PUX`, `AIK`, `ADE`, `OPS`, `BIZ` | Terminology and naming consistency |
| `../Aura_PRD_v1.md` | `PUX`, `BIZ` | User problems, functional requirements, KPIs |
| `../Aura_Consumer_Value_Operating_Model.md` | `PUX`, `BIZ`, `AIK` | Consumer value loops, trust contract, recovery flows, outcome-first delivery |
| `../Aura_Task_Execution_Matrix.md` | `OPS`, `BIZ`, `PUX`, `AIK`, `ADE` | Task-level execution detail, implementation location, and validation expectations |
| `../Aura_Long_Horizon_Task_Execution_Matrix.md` | `OPS`, `BIZ`, `PUX`, `AIK`, `ADE` | Weeks 25-104 execution detail, implementation location, and validation expectations |
| `../Aura_Full_Product_Implementation_Plan_104_Weeks.md` | `OPS`, `BIZ` | Long-horizon sequencing, reuse-first planning, staffing, and release gating |
| `../Aura_Shared_Platform_Integration_Spec.md` | `ADE`, `OPS` | AEP/Data Cloud transaction boundaries, topic/dataset registration, outbox, auth, telemetry |
| `../Aura_UI_UX_Blueprint.md` | `PUX` | Screens, accessibility, trust surfaces, outcome capture UI |
| `../Aura_System_Architecture.md` | `OPS`, `ADE` | layer behavior, privacy boundary, AEP/Data Cloud/shared platform boundaries, observability |
| `../Aura_Intelligence_Platform_Architecture.md` | `AIK`, `OPS`, `ADE` | retrieval, online/offline paths, governance |
| `../Aura_Technical_Stack_Blueprint.md` | `OPS` | deployment, tooling, infrastructure assumptions |
| `../Aura_Monorepo_Structure.md` | `OPS` | build/test workspace assumptions |
| `../Aura_C4_Architecture_Diagrams.md` | `OPS`, `ADE` | container and deployment path validation |
| `../Aura_Data_Architecture.md` | `ADE`, `OPS` | service data boundary, model coverage |
| `../Aura_Database_Schema_Prisma.md` | `ADE` | persistence and schema contract cases |
| `../Aura_API_Contracts.md` | `ADE`, `PUX` | GraphQL/REST contract coverage |
| `../Aura_Event_Architecture.md` | `ADE`, `OPS` | AEP publication, versioning, replay, DLQ |
| `../Aura_Personal_Intelligence_Engine_Spec.md` | `AIK`, `PUX` | profile learning, context, confidence |
| `../Aura_Recommendation_Algorithms.md` | `AIK` | candidate gen, rules, ranking, fairness |
| `../Aura_AI_Engine_Design.md` | `AIK` | model behavior, explainability, cold start |
| `../Aura_AI_ML_Data_Operating_Model.md` | `AIK`, `ADE`, `OPS` | source tiers, label strategy, human review, runtime controls |
| `../Aura_AI_Model_Training_Pipeline.md` | `AIK`, `OPS` | training data, evaluation, deployment, monitoring |
| `../Aura_AI_Agent_Architecture.md` | `AIK`, `ADE`, `OPS` | orchestration, timeouts, structured outputs |
| `../Aura_Knowledge_Graph.md` | `AIK`, `ADE` | graph traversal and entity relation cases |
| `../Aura_Ingredient_Knowledge_Graph.md` | `AIK` | ingredient safety and conflict reasoning |
| `../Aura_Shade_Color_Ontology.md` | `AIK` | shade scoring, thresholds, confidence |
| `../Aura_Style_Archetype_Taxonomy.md` | `AIK`, `PUX` | style quiz and blend behavior |
| `../Aura_Product_Similarity_Model.md` | `AIK` | dupes, alternatives, similarity thresholds |
| `../Aura_Product_Roadmap_Epics.md` | `BIZ`, `OPS` | phase gates and milestone readiness |
| `../Aura_Engineering_Sprint_Plan_6_Months.md` | `OPS`, `BIZ` | delivery readiness and regression gating |
| `../Aura_24_Month_Strategy.md` | `BIZ` | long-range validation metrics and phase gates |
| `../Aura_GTM_Strategy.md` | `BIZ`, `PUX` | launch experiments, trust comprehension, creator acquisition quality |
| `../Aura_Competitive_Landscape.md` | `BIZ` | competitive differentiation validation |
| `../Aura_Defensibility_Strategy.md` | `BIZ` | moat-building instrumentation and behavior tests |

## Detailed Test Documents

| File | Scope |
| ---- | ----- |
| `Aura_Test_Cases_01_Product_UX_Privacy.md` | onboarding, feed, product detail, compare, assistant, saved items, privacy center, accessibility |
| `Aura_Test_Cases_02_Recommendation_AI_Knowledge.md` | ranking, confidence, explainability, knowledge graph, ontology, similarity, agent flows |
| `Aura_Test_Cases_03_API_Data_Events.md` | GraphQL, REST, database, schema, enums, events, export/delete, consent contracts |
| `Aura_Test_Cases_04_Platform_Architecture_Operations.md` | ingestion, performance, resilience, shared observability, deployment, security, fairness ops |
| `Aura_Test_Cases_05_Strategy_GTM_Business_Validation.md` | user-problem validation, launch gates, growth loops, monetization, brand analytics, community trust |
| `Aura_Test_Cases_06_Performance_Chaos_Recovery_Reuse.md` | burst/load/soak, AEP/Data Cloud dependency failure, backup/restore, concurrency integrity, shared-library regression |

## Definition of Done For A Feature

1. All linked unit and contract cases are implemented and passing in CI.
2. All linked integration cases are implemented or explicitly deferred with owner and reason.
3. At least one E2E journey exists for the user-facing path.
4. Required non-functional checks for the feature class are automated or scheduled.
5. Any new metric or event has analytics validation and dashboard coverage.
6. Any new consented behavior has explicit deny, revoke, and audit-path tests.
7. Any new async or durable-data path is validated against the AEP/Data Cloud shared-platform boundary.

## Definition of Done For A Release Candidate

1. No failing `P0` or `P1` test cases.
2. All source documents touched by the release have traceable test coverage.
3. Recommendation quality, safety outcome, and trust dashboards are green for the candidate build.
4. Data export and account deletion flows have been re-run against the candidate.
5. Canaries or shadow scoring have no unexplained fairness, drift, or latency regressions.
