# Aura Platform — Executive Summary & Document Index

Version: 2.0
Date: March 2026

This document is the **entry point** for all Aura platform artifacts. It provides an executive overview and directs readers to the authoritative specialized document for each topic. For exhaustive detail, consult the Master Platform Specification and the individual referenced documents.

---

## 1. Product Vision

Aura is an AI-powered decision-support platform for beauty, fashion, and lifestyle choices.

Instead of browsing fragmented sources, Aura analyzes product intelligence, ingredient compatibility, community sentiment, personal profile attributes, and contextual signals — then delivers personalized recommendations with clear, verifiable explanations.

**Core question Aura answers:** _"What is right for me right now?"_

---

## 2. Core Platform Pillars

| #   | Pillar                                  | Description                                                                  |
| --- | --------------------------------------- | ---------------------------------------------------------------------------- |
| 1   | **Personal Profile System (You Index)** | Unified user intelligence: declared, inferred, behavioral, and imported data |
| 2   | **Product Intelligence Engine**         | Canonical product catalog, ingredient graph, shade ontology, style taxonomy  |
| 3   | **Unified Content Feed**                | Personalized discovery and inspiration surface                               |
| 4   | **Explainable Recommendation Engine**   | Ranked, reasoned, confidence-scored suggestions with reason codes            |
| 5   | **Community Intelligence Layer**        | Aggregated reviews, sentiment signals, twin-user discovery                   |

---

## 3. Architecture Overview

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

---

## 4. Technology Summary

| Layer                                                     | Technology                            |
| --------------------------------------------------------- | ------------------------------------- |
| User API (CRUD, preferences, real-time)                   | Node.js + Fastify + Prisma            |
| Core Domain (recommendations, ranking, ingestion workers) | Java 21 + ActiveJ                     |
| Frontend                                                  | React Router v7 + Tailwind CSS        |
| Mobile                                                    | React Native                          |
| Databases                                                 | PostgreSQL, Redis, pgvector           |
| ML Inference                                              | Python microservices                  |
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

- [Aura_Technical_Stack_Blueprint.md](Aura_Technical_Stack_Blueprint.md) — Technology decisions and rationale
- [Aura_Engineering_Sprint_Plan_6_Months.md](Aura_Engineering_Sprint_Plan_6_Months.md) — Sprint-level execution plan

### UX

- [Aura_UI_UX_Blueprint.md](Aura_UI_UX_Blueprint.md) — Core screens and design principles
  • community insights

# ===================================================== 12. Monetization

Revenue streams:

• affiliate commerce
• premium subscription
• brand analytics

# ===================================================== 13. Competitive Positioning

Aura combines advantages of:

Retail platforms
Ingredient analysis tools
Social discovery platforms
AI assistants

Key differentiation:

• cross-brand intelligence
• deep personalization
• explainable recommendations

# ===================================================== 14. Defensibility Strategy

Long-term advantages:

• proprietary product intelligence data
• personalization advantage
• community network effects
• brand analytics platform
• AI learning flywheel

# ===================================================== 15. Strategic Outcome

Aura evolves from a discovery app into a trusted AI advisor for lifestyle decisions.

It becomes the intelligence layer between consumers and commerce.
