# Aura Platform — Executive Summary & Document Index

Version: 2.2
Date: March 2026

**Purpose**: Executive entry point and navigation guide for Aura platform artifacts.

**Important**: This document is **non-authoritative** and should never be used to resolve policy,
contract, or implementation conflicts. Use `Aura_Master_Platform_Specification.md` for all
governing decisions.

**Terminology source**: `Aura_Glossary.md`

---

## Quick Reference

| Topic | Primary Document | Notes |
| ----- | ---------------- | ----- |
| Product vision and authority | `Aura_Master_Platform_Specification.md` | authoritative source of truth |
| Consumer value delivery | `Aura_Consumer_Value_Operating_Model.md` | value loops and trust contract |
| Shared platform integration | `Aura_Shared_Platform_Integration_Spec.md` | AEP, Data Cloud, security, o11y integration rules |
| Active delivery tasks | `Aura_Task_Execution_Matrix.md` | Weeks 1-24 task detail |
| Long-horizon delivery tasks | `Aura_Long_Horizon_Task_Execution_Matrix.md` | Weeks 25-104 task detail |
| Full implementation sequencing | `Aura_Full_Product_Implementation_Plan_104_Weeks.md` | week-by-week sequencing view |
| Technical stack | `Aura_Technical_Stack_Blueprint.md` | runtime and tooling decisions |
| Data architecture | `Aura_Data_Architecture.md` | logical data model and service boundary |
| API contracts | `Aura_API_Contracts.md` | GraphQL, REST, and auth model |
| Event contracts | `Aura_Event_Architecture.md` | AEP topics, schemas, replay, DLQ |
| Testing | `testing/Aura_Test_Plan_and_Traceability.md` | test-first execution rules |

---

## Product Summary

Aura is an AI-powered decision-support platform for beauty, fashion, and wellness choices. It
answers the question: **"What is right for me right now?"**

Aura's current strategic posture is:

- beauty-first trust and decision quality in Year 1
- category expansion only after outcome and trust proof
- shared-platform-first implementation through AEP, Data Cloud, shared auth/security, and shared o11y
- modular-monolith-first delivery with only `api`, `core-worker`, and `ml-inference` as early deployables

---

## Shared Platform Summary

- AEP is the only Aura cross-process event communication boundary.
- Data Cloud is the managed data plane for Aura datasets, exports, retention, restore, and lineage.
- Shared auth, security, audit, and observability are platform capabilities, not Aura-local infrastructure.
- Aura owns product logic, data semantics, recommendation behavior, and trust policy.

For implementation detail, see `Aura_Shared_Platform_Integration_Spec.md`.

---

## Technology Summary

| Layer | Technology | Purpose |
| ----- | ---------- | ------- |
| Frontend | React 19 + React Router v7 + Tailwind CSS | web UI, SSR, streaming, data loading |
| Mobile | React Native | mobile experience |
| User API | Node.js + Fastify + Prisma | client-facing GraphQL/REST and BFF flows |
| Core Domain | Java 21 + ActiveJ | ingestion, ranking, asynchronous domain work |
| Event Plane | AEP | cross-process async communication, replay, DLQ |
| Data Plane | Data Cloud | managed persistence, retention, export, restore |
| ML Runtime | Python + FastAPI | inference and ML-specific runtime |
| CI/CD | Gitea Actions | build, test, release automation |

---

## Launch Milestones

| Milestone | Target Timing | Scope |
| --------- | ------------- | ----- |
| Internal alpha | Month 3 | internal or employee-like cohort, ~50 users |
| Invite beta | Month 5 launch with Month 5-6 ramp | invite-only external cohort, up to ~500 users |
| Public launch | Month 8 | beauty-first public launch after hardening |

---

## Roadmap Summary

| Phase | Focus | Timeline |
| ----- | ----- | -------- |
| Phase 1 — Curated Me | beauty MVP: ingestion, shade matching, ingredient analysis, recommendation feed | Months 1-3 |
| Phase 2 — Aura Insights | explainability, sentiment, experimentation, AI-assisted personalization | Months 4-6 |
| Phase 3 — Bio Sync | context-aware wellness and routine intelligence | Months 7-9 |
| Phase 4 — Aura Collective | community, verified reviews, twin-user features, brand analytics | Months 10+ |

---

## Document Navigation

### Strategy and Product

- `Aura_Master_Platform_Specification.md`
- `Aura_PRD_v1.md`
- `Aura_Consumer_Value_Operating_Model.md`
- `Aura_24_Month_Strategy.md`
- `Aura_Product_Roadmap_Epics.md`
- `Aura_GTM_Strategy.md`
- `Aura_Defensibility_Strategy.md`
- `Aura_Competitive_Landscape.md`

### Architecture and Execution

- `Aura_System_Architecture.md`
- `Aura_Intelligence_Platform_Architecture.md`
- `Aura_Shared_Platform_Integration_Spec.md`
- `Aura_C4_Architecture_Diagrams.md`
- `Aura_Monorepo_Structure.md`
- `Aura_Technical_Stack_Blueprint.md`
- `Aura_Task_Execution_Matrix.md`
- `Aura_Long_Horizon_Task_Execution_Matrix.md`
- `Aura_Full_Product_Implementation_Plan_104_Weeks.md`
- `Aura_Engineering_Sprint_Plan_6_Months.md`

### Data, AI, and Knowledge

- `Aura_Data_Architecture.md`
- `Aura_Database_Schema_Prisma.md`
- `Aura_API_Contracts.md`
- `Aura_Event_Architecture.md`
- `Aura_AI_ML_Data_Operating_Model.md`
- `Aura_AI_Model_Training_Pipeline.md`
- `Aura_AI_Engine_Design.md`
- `Aura_AI_Agent_Architecture.md`
- `Aura_Personal_Intelligence_Engine_Spec.md`
- `Aura_Recommendation_Algorithms.md`
- `Aura_Knowledge_Graph.md`
- `Aura_Ingredient_Knowledge_Graph.md`
- `Aura_Shade_Color_Ontology.md`
- `Aura_Style_Archetype_Taxonomy.md`
- `Aura_Product_Similarity_Model.md`

### UX and Testing

- `Aura_UI_UX_Blueprint.md`
- `testing/Aura_Test_Plan_and_Traceability.md`
- `testing/Aura_Test_Cases_01_Product_UX_Privacy.md`
- `testing/Aura_Test_Cases_02_Recommendation_AI_Knowledge.md`
- `testing/Aura_Test_Cases_03_API_Data_Events.md`
- `testing/Aura_Test_Cases_04_Platform_Architecture_Operations.md`
- `testing/Aura_Test_Cases_05_Strategy_GTM_Business_Validation.md`
- `testing/Aura_Test_Cases_06_Performance_Chaos_Recovery_Reuse.md`

---

## Governance Note

1. Start with `Aura_Master_Platform_Specification.md` for any scope, consent, contract, or KPI decision.
2. Use `Aura_Shared_Platform_Integration_Spec.md` before implementing any AEP or Data Cloud dependency.
3. Use the task execution matrices before coding scheduled work.
4. Update the test plan and detailed cases before implementing new behavior.

---

**Status**: CURRENT
**Authority**: NON-AUTHORITATIVE
**Last Updated**: March 2026
