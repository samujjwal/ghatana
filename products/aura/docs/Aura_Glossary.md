# Aura Documentation Glossary

**Purpose**: Standardized terminology and definitions used across all Aura platform documentation.

**Version**: 1.0  
**Date**: March 2026  
**Authority**: Referenced by all Aura documents for consistent terminology

---

## Core Product Terms

| Term | Definition | Used In |
|-------|-------------|----------|
| **Aura Platform** | AI-powered decision-support platform for beauty, fashion, and lifestyle choices | All documents |
| **You Index** | Unified user intelligence model combining declared, inferred, and behavioral attributes | Master Spec, PIE, Data Architecture |
| **Core Question** | "What is right for me right now?" - the fundamental question Aura answers | Master Spec, PRD, GTM |
| **Explainable AI** | Every recommendation includes clear, verifiable reason codes and evidence | AI Engine, Recommendation Algorithms |
| **Personal Intelligence Engine (PIE)** | Core AI subsystem that turns data into personalized intelligence | PIE Spec, Intelligence Architecture |

---

## User & Profile Terms

| Term | Definition | Canonical Source |
|-------|-------------|------------------|
| **Primary User** | Women 18-34, beauty-focused digital shoppers who research products before purchasing | PRD, GTM Strategy |
| **Declared Data** | Explicitly entered by user (skin type, allergies, preferences) | Data Architecture, PIE |
| **Inferred Data** | Derived from behavioral signals or AI analysis (opt-in) | Data Architecture, PIE |
| **Imported Data** | Pulled from third-party integrations (requires explicit consent) | Data Architecture, PIE |
| **Skin Tone Depth** | 1-10 canonical scale for product matching (not medical classification) | Shade Ontology, PIE |
| **Undertone** | Cool, Warm, Neutral, Olive classification for shade matching | Shade Ontology, PIE |

---

## Technical Architecture Terms

| Term | Definition | Referenced In |
|-------|-------------|---------------|
| **7-Layer Architecture** | Source & Ingestion → Canonical Knowledge → Personal Intelligence → Decision & Recommendation → Agent Orchestration → Experience Delivery → Observability | System Architecture, Master Spec |
| **Hybrid Backend** | Node.js/Fastify for user API + Java 21/ActiveJ for core domain + Python/FastAPI for ML | Technical Stack, System Architecture |
| **Event-Driven** | Asynchronous communication between layers using immutable events | Event Architecture, Intelligence Platform |
| **Agent Orchestration** | Coordination of specialized AI agents through shared context and events | AI Agent Architecture, Intelligence Platform |
| **Canonical Knowledge** | Durable, structured platform intelligence (products, ingredients, shades) | Data Architecture, Knowledge Graph |

---

## AI & ML Terms

| Term | Definition | Context |
|-------|-------------|---------|
| **Shade Matching Model** | Scores compatibility between product shade and user's skin tone/undertone | AI Engine, Shade Ontology |
| **Ingredient Compatibility Model** | Assesses whether product formulation is compatible with user's skin profile | AI Engine, Ingredient Graph |
| **Recommendation Pipeline** | Candidate generation → rules filtering → feature construction → ranking → explanation | Recommendation Algorithms, AI Engine |
| **Graceful Degradation** | Deterministic rules provide fallbacks when ML models have insufficient data | AI Engine, Recommendation Algorithms |
| **Twin-User Discovery** | Finding users with similar attributes for community insights | Knowledge Graph, PIE |

---

## Data & Knowledge Terms

| Term | Definition | Source |
|-------|-------------|--------|
| **Ingredient Knowledge Graph** | Models relationships between ingredients, skin types, concerns, and risks | Ingredient Graph |
| **Shade Ontology** | Universal mapping layer for cross-brand shade normalization | Shade Ontology |
| **Style Archetype Taxonomy** | Classification of aesthetic preferences (Minimalist, Classic, Bohemian, etc.) | Style Taxonomy |
| **Product Similarity Model** | Identifies dupes and alternatives across brands | Product Similarity |
| **Vector Embeddings** | Dense representations for semantic similarity search | Data Architecture, AI Training |

---

## Business & Market Terms

| Term | Definition | Used In |
|-------|-------------|----------|
| **Beauty-First Entry** | Market entry strategy focusing on skincare and foundation shade matching | GTM Strategy, 24-Month Plan |
| **Creator-Led Acquisition** | Primary distribution channel through TikTok/YouTube beauty creators | GTM Strategy, Defensibility |
| **Data Moat** | Proprietary datasets unavailable to single competitor | Defensibility Strategy |
| **Personalization Advantage** | Recommendation accuracy improves as user data grows | Defensibility Strategy |
| **Brand Analytics Revenue** | B2B revenue from anonymized aggregate insights | GTM Strategy, 24-Month Plan |

---

## Success Metrics & KPIs

| Term | Definition | Target |
|-------|-------------|--------|
| **Time-to-Decision** | Median time for user to make product choice | ≤ 8 minutes (Year 1) |
| **Shade-Miss Rate** | Percentage of wrong shade purchases | ≤ 12% (Year 1) |
| **Return Reduction** | Decrease in returns vs self-directed baseline | ≥ 20% (Year 1) |
| **Explanation Helpfulness** | User rating of recommendation reasons | ≥ 4.0/5.0 |
| **30-Day Retention** | User retention after one month | ≥ 25% before paid acquisition |

---

## Consent & Privacy Terms

| Term | Definition | Context |
|-------|-------------|---------|
| **Core Service Data** | Account data, declared profile, on-platform interactions | Data Architecture |
| **Optional Enrichment** | Imported history, public profile, partner data | Data Architecture |
| **High-Sensitivity** | Wearable/bio signals, selfie analysis, regulated data | Data Architecture |
| **Scoped Consent** | Per-feature explicit consent for optional integrations | Data Architecture |
| **Data Export Request** | User right to export their data | Data Architecture |

---

## Implementation Terms

| Term | Definition | Reference |
|-------|-------------|-----------|
| **Monorepo** | Single repository containing all apps, services, packages | Monorepo Structure |
| **Feature Flag** | Toggle for gradual feature rollout | Engineering Plan |
| **A/B Testing** | Controlled experiments for ranking weights and UI variants | Engineering Plan |
| **CI/CD Pipeline** | Automated build, test, deployment workflow | Engineering Plan |
| **Service Mesh** | Inter-service communication and observability | Technical Stack |

---

## Document Usage Guidelines

1. **Always use terms as defined here** in new documentation
2. **Reference this glossary** when introducing new terminology
3. **Update this glossary** when adding new platform concepts
4. **Maintain consistency** across all Aura documentation

---

**Status**: ✅ CURRENT - Active glossary  
**Authority**: ✅ AUTHORITATIVE - Terminology standard  
**Last Updated**: March 2026
