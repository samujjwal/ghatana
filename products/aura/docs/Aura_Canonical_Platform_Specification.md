# Aura Platform — Executive Summary & Document Index

Version: 2.1
Date: March 2026

**Purpose**: Executive entry point and navigation guide for Aura platform artifacts.

**⚠️ IMPORTANT**: This document is **non-authoritative** and should never be used to resolve policy or implementation conflicts. For all authoritative specifications, see `Aura_Master_Platform_Specification.md`.

**📚 For standardized terminology**, see `Aura_Glossary.md` - the authoritative glossary for all Aura platform terms.

---

## Quick Reference

| Topic | Authoritative Document | Section |
|-------|------------------------|---------|
| Product Vision & Scope | `Aura_Master_Platform_Specification.md` | §1-2 |
| Consumer Value Delivery | `Aura_Consumer_Value_Operating_Model.md` | Complete |
| Task Execution Detail | `Aura_Task_Execution_Matrix.md` | Complete |
| System Architecture | `Aura_Master_Platform_Specification.md` | §3 |
| Technical Stack | `Aura_Technical_Stack_Blueprint.md` | Complete |
| Data Architecture | `Aura_Data_Architecture.md` | Complete |
| AI/ML and Data Operating Model | `Aura_AI_ML_Data_Operating_Model.md` | Complete |
| API Contracts | `Aura_API_Contracts.md` | Complete |
| AI Engine Design | `Aura_AI_Engine_Design.md` | Complete |
| Database Schema | `Aura_Database_Schema_Prisma.md` | Complete |
| UI/UX Blueprint | `Aura_UI_UX_Blueprint.md` | Complete |
| **Standardized Terminology** | `Aura_Glossary.md` | Complete |

---

## 1. Product Vision (Summary)

Aura is an AI-powered decision-support platform for beauty, fashion, and lifestyle choices that answers: **"What is right for me right now?"**

Instead of browsing fragmented sources, Aura analyzes product intelligence, ingredient compatibility, community sentiment, personal profile attributes, and contextual signals — then delivers personalized recommendations with clear, verifiable explanations.

---

## 2. Core Platform Pillars (Summary)

| #   | Pillar                                  | Description                                                                  |
| --- | --------------------------------------- | ---------------------------------------------------------------------------- |
| 1   | **Personal Profile System (You Index)** | Unified user intelligence: declared, inferred, behavioral, and imported data |
| 2   | **Product Intelligence Engine**         | Canonical product catalog, ingredient graph, shade ontology, style taxonomy  |
| 3   | **Unified Content Feed**                | Personalized discovery and inspiration surface                               |
| 4   | **Explainable Recommendation Engine**   | Ranked, reasoned, confidence-scored suggestions with reason codes            |
| 5   | **Community Intelligence Layer**        | Aggregated reviews, sentiment signals, twin-user discovery                   |

*For detailed specifications, see `Aura_Master_Platform_Specification.md` §2*

---

## 3. Architecture Overview (Summary)

Aura uses a **7-layer platform architecture**:

| Layer                                   | Responsibility                                                                                           |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1. Source & Ingestion                   | Collect and normalize product data, reviews, community feeds, optional wearable and receipt integrations |
| 2. Canonical Knowledge                  | Product catalog, ingredient graph, shade ontology, style taxonomy, source provenance                     |
| 3. Personal Intelligence                | You Index, context engine, ownership history, preference learning                                        |
| 4. Decision & Recommendation            | Candidate generation, rules filtering, feature construction, ranking, explanation generation             |
| 5. Agent Orchestration                  | Specialized AI agents coordinated through shared context and events                                      |
| 6. Experience Delivery                  | Web, mobile, AI assistant, brand analytics                                                               |
| 7. Observability, Governance & Learning | Consent management, audit, model monitoring, continuous learning                                         |

*For detailed architecture, see `Aura_Master_Platform_Specification.md` §3*

---

## 4. Technology Summary (Summary)

| Layer       | Technology                 | Purpose                                                                                     |
| ----------- | -------------------------- | ------------------------------------------------------------------------------------------- |
| Frontend    | React Router v7, Tailwind   | Web/mobile UI with SSR, data fetching, streaming                                           |
| User API    | Node.js + Fastify + Prisma  | Profile management, preferences, feed delivery, real-time, CRUD                              |
| Core Domain | Java 21 + ActiveJ          | Recommendation engine, ingestion workers, ranking pipeline, high-throughput event processing |
| ML Inference| Python + FastAPI           | Model serving, feature computation, batch pipelines                                          |

*For complete technology stack, see `Aura_Technical_Stack_Blueprint.md`*

---

## 5. Document Navigation

### Product & Strategy
- `Aura_PRD_v1.md` - Product Requirements Document
- `Aura_Consumer_Value_Operating_Model.md` - How Aura delivers concrete value and trust to consumers
- `Aura_Task_Execution_Matrix.md` - Task-level what/how/where/validation guide for active delivery
- `Aura_24_Month_Strategy.md` - Long-term product strategy
- `Aura_Product_Roadmap_Epics.md` - Engineering epic breakdown
- `Aura_GTM_Strategy.md` - Go-to-market strategy
- `Aura_Defensibility_Strategy.md` - Competitive moats

### Architecture & Technical
- `Aura_System_Architecture.md` - Detailed system architecture
- `Aura_Intelligence_Platform_Architecture.md` - AI platform design
- `Aura_Data_Architecture.md` - Data model and domains
- `Aura_Database_Schema_Prisma.md` - Complete database schema
- `Aura_Event_Architecture.md` - Event-driven design
- `Aura_C4_Architecture_Diagrams.md` - Visual architecture diagrams

### AI & Intelligence
- `Aura_AI_ML_Data_Operating_Model.md` - Data quality, model risk, label strategy, and learning loops
- `Aura_AI_Engine_Design.md` - AI model architecture
- `Aura_AI_Agent_Architecture.md` - Specialized AI agents
- `Aura_AI_Model_Training_Pipeline.md` - ML training pipeline
- `Aura_Personal_Intelligence_Engine_Spec.md` - Personal intelligence system
- `Aura_Recommendation_Algorithms.md` - Recommendation algorithms

### Knowledge & Ontology
- `Aura_Knowledge_Graph.md` - Knowledge graph design
- `Aura_Ingredient_Knowledge_Graph.md` - Ingredient relationships
- `Aura_Shade_Color_Ontology.md` - Shade matching system
- `Aura_Style_Archetype_Taxonomy.md` - Style classification
- `Aura_Product_Similarity_Model.md` - Product similarity

### Implementation & Operations
- `Aura_Monorepo_Structure.md` - Code organization
- `Aura_Task_Execution_Matrix.md` - Task execution map for active delivery work
- `Aura_Engineering_Sprint_Plan_6_Months.md` - Development timeline
- `Aura_UI_UX_Blueprint.md` - Design system
- `Aura_API_Contracts.md` - API specifications
- `Aura_Technical_Stack_Blueprint.md` - Technology choices

### Business & Market
- `Aura_Competitive_Landscape.md` - Market analysis
- `Aura_Canonical_Platform_Specification.md` - This document

---

## 6. Implementation Guidance

1. **Always start with `Aura_Master_Platform_Specification.md`** for authoritative requirements
2. **Use specialized documents** for detailed implementation guidance
3. **Check cross-references** in each document for related specifications
4. **Report conflicts** between documents to maintain authority hierarchy

---

**Status**: ✅ CURRENT - Executive summary only  
**Authority**: ❌ NON-AUTHORITATIVE - Reference master specification  
**Last Updated**: March 2026

| Layer                                                     | Technology                            |
| --------------------------------------------------------- | ------------------------------------- |
| User API (CRUD, preferences, real-time)                   | Node.js + Fastify + Prisma            |
| Core Domain (recommendations, ranking, ingestion workers) | Java 21 + ActiveJ                     |
| Frontend                                                  | React Router v7 + Tailwind CSS        |
| Mobile                                                    | React Native                          |
| Databases                                                 | PostgreSQL, Redis, pgvector           |
| ML Inference                                              | Python + FastAPI inference service    |
| Observability                                             | Micrometer, OpenTelemetry, Prometheus |
| Infrastructure                                            | Docker, Kubernetes                    |

---

## 5. Roadmap Summary

| Phase                     | Focus                                                                           | Timeline   |
| ------------------------- | ------------------------------------------------------------------------------- | ---------- |
| Phase 1 — Curated Me      | Beauty MVP: ingestion, shade matching, ingredient analysis, recommendation feed | Months 1–3 |
| Phase 2 — Aura Insights   | AI personalization: skin analysis, sentiment engine, explainability             | Months 4–6 |
| Phase 3 — Bio Sync        | Context-aware: wearables, routine builder, mood-based styling                   | Months 7–9 |
| Phase 4 — Aura Collective | Community: twin networks, verified reviews, shared routines                     | Months 10+ |

---

## 6. Document Index

### Strategy & Vision

- [Aura_PRD_v1.md](Aura_PRD_v1.md) — Product requirements, user problems, success criteria
- [Aura_Consumer_Value_Operating_Model.md](Aura_Consumer_Value_Operating_Model.md) — Consumer value loops, trust contract, outcome-first delivery
- [Aura_24_Month_Strategy.md](Aura_24_Month_Strategy.md) — 24-month objectives and milestones
- [Aura_Product_Roadmap_Epics.md](Aura_Product_Roadmap_Epics.md) — Engineering epics by phase
- [Aura_GTM_Strategy.md](Aura_GTM_Strategy.md) — Go-to-market channels and growth loops
- [Aura_Defensibility_Strategy.md](Aura_Defensibility_Strategy.md) — Competitive moats
- [Aura_Competitive_Landscape.md](Aura_Competitive_Landscape.md) — Competitor analysis and positioning

### Architecture

- [Aura_Master_Platform_Specification.md](Aura_Master_Platform_Specification.md) — **Comprehensive platform spec (start here for deep dives)**
- [Aura_System_Architecture.md](Aura_System_Architecture.md) — Layered architecture blueprint
- [Aura_Intelligence_Platform_Architecture.md](Aura_Intelligence_Platform_Architecture.md) — Detailed intelligence layer design
- [Aura_C4_Architecture_Diagrams.md](Aura_C4_Architecture_Diagrams.md) — System context, container, component, and deployment diagrams
- [Aura_Monorepo_Structure.md](Aura_Monorepo_Structure.md) — Repository layout

### AI & Intelligence

- [Aura_AI_ML_Data_Operating_Model.md](Aura_AI_ML_Data_Operating_Model.md) — Data tiers, label strategy, model controls, review operations
- [Aura_Personal_Intelligence_Engine_Spec.md](Aura_Personal_Intelligence_Engine_Spec.md) — You Index, context engine, reasoning engine
- [Aura_Recommendation_Algorithms.md](Aura_Recommendation_Algorithms.md) — Scoring formulas, ranking pipeline
- [Aura_AI_Engine_Design.md](Aura_AI_Engine_Design.md) — AI models for shade matching, ingredient safety, ranking
- [Aura_AI_Model_Training_Pipeline.md](Aura_AI_Model_Training_Pipeline.md) — Model training, evaluation, deployment
- [Aura_AI_Agent_Architecture.md](Aura_AI_Agent_Architecture.md) — Specialized agents and orchestration

### Knowledge Models

- [Aura_Knowledge_Graph.md](Aura_Knowledge_Graph.md) — Core entity relationships
- [Aura_Ingredient_Knowledge_Graph.md](Aura_Ingredient_Knowledge_Graph.md) — Ingredient safety and compatibility graph
- [Aura_Shade_Color_Ontology.md](Aura_Shade_Color_Ontology.md) — Cross-brand shade standardization
- [Aura_Style_Archetype_Taxonomy.md](Aura_Style_Archetype_Taxonomy.md) — Fashion style classification
- [Aura_Product_Similarity_Model.md](Aura_Product_Similarity_Model.md) — Dupe detection and alternative recommendations

### Data & APIs

- [Aura_Data_Architecture.md](Aura_Data_Architecture.md) — Domain data model overview
- [Aura_Database_Schema_Prisma.md](Aura_Database_Schema_Prisma.md) — Complete Prisma/PostgreSQL schema
- [Aura_API_Contracts.md](Aura_API_Contracts.md) — GraphQL and REST API definitions
- [Aura_Event_Architecture.md](Aura_Event_Architecture.md) — Event streams, topics, and processing

### Engineering

- [Aura_Task_Execution_Matrix.md](Aura_Task_Execution_Matrix.md) — Sprint task execution map with what/how/where/validation
- [Aura_Technical_Stack_Blueprint.md](Aura_Technical_Stack_Blueprint.md) — Technology decisions and rationale
- [Aura_Engineering_Sprint_Plan_6_Months.md](Aura_Engineering_Sprint_Plan_6_Months.md) — Sprint-level execution plan

### UX

- [Aura_UI_UX_Blueprint.md](Aura_UI_UX_Blueprint.md) — Core screens and design principles

---

## 7. Governance Note

- Use [Aura_Master_Platform_Specification.md](Aura_Master_Platform_Specification.md) when defining scope, privacy boundaries, API/event/schema semantics, or success metrics.
- Use specialized documents for detail after the Master spec has already established the governing decision.
