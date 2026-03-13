# Aura Master Platform Specification

Version: 2.0
Date: March 2026

This document is the **comprehensive single-source-of-truth** for the Aura platform. It integrates vision, architecture, data model, AI systems, API design, engineering plan, and roadmap. Individual specialized documents provide deeper detail on specific topics; this document provides the authoritative synthesis.

---

# 1. Product Vision

Aura is an AI-powered decision-support platform that helps users determine which beauty, fashion, and wellness products are best suited for them personally.

Instead of generic discovery, Aura analyzes:

- structured product metadata and ingredient data
- community sentiment and peer signals
- user profile attributes and behavioral history
- contextual signals (season, mood, lifestyle events)
- AI-driven compatibility inference

to deliver personalized recommendations with clear, verifiable explanations.

**Core question Aura answers:** _"What is right for me right now?"_

---

# 2. Core Product Pillars

| #   | Pillar                                  | Description                                                                                              |
| --- | --------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1   | **Personal Profile System (You Index)** | Unified user intelligence model: declared, inferred, behavioral, and imported attributes                 |
| 2   | **Product Intelligence Engine**         | Canonical product catalog, ingredient ontology, shade ontology, style taxonomy, product similarity model |
| 3   | **Unified Content Feed**                | Personalized discovery and inspiration surface with recommendation cards                                 |
| 4   | **Explainable Recommendation Engine**   | Ranked, reasoned, confidence-scored suggestions with human-readable explanations and reason codes        |
| 5   | **Community Intelligence Layer**        | Aggregated and analyzed reviews, sentiment signals, twin-user discovery, verified community insights     |

---

# 3. System Architecture

Aura uses a **7-layer platform architecture**. Each layer has a clear responsibility boundary and communicates with adjacent layers through defined APIs or events.

## Layer Overview

```
┌──────────────────────────────────────────────────────────┐
│  7. Observability, Governance & Learning                  │
├──────────────────────────────────────────────────────────┤
│  6. Experience Delivery (Web, Mobile, AI Assistant)       │
├──────────────────────────────────────────────────────────┤
│  5. Agent Orchestration                                   │
├──────────────────────────────────────────────────────────┤
│  4. Decision & Recommendation                             │
├──────────────────────────────────────────────────────────┤
│  3. Personal Intelligence (You Index, Context)            │
├──────────────────────────────────────────────────────────┤
│  2. Canonical Knowledge (Products, Ingredients, Shades)   │
├──────────────────────────────────────────────────────────┤
│  1. Source & Ingestion                                    │
└──────────────────────────────────────────────────────────┘
```

## Layer Descriptions

### Layer 1 — Source & Ingestion

Collects and normalizes raw inputs. Responsibilities: source connection and scheduling, scraping and API retrieval, parsing, normalization, deduplication, entity resolution, change detection, freshness scoring.

Sources: retailer and affiliate feeds, brand product pages, ingredient databases, product reviews, community discussions, optional user-linked receipts, optional wellness integrations.

Components: Source Adapters, Crawl Scheduler, Parsing Workers, Enrichment Workers, Deduplication Service, Catalog Merge Service.

### Layer 2 — Canonical Knowledge

Converts normalized inputs into durable, structured platform intelligence.

Stores: Product Catalog, Ingredient Graph, Shade Ontology, Style Taxonomy, Review and Community Corpus, Source Provenance Store.

Storage: PostgreSQL (canonical entities), pgvector (semantic retrieval), object storage (raw snapshots), Redis (hot paths).

### Layer 3 — Personal Intelligence

Maintains user-specific intelligence models.

Modules: You Index Service (declared, inferred, imported attributes), Context Engine (season, weather, mood, event, optional bio signals), Ownership and History Service, Preference Learning Service.

Design principles: user-editable inferred data, clear separation of declared vs inferred vs imported, privacy-aware optional integrations, reversible and auditable inference history.

### Layer 4 — Decision & Recommendation

Transforms knowledge and profile into ranked, explained recommendations.

Pipeline: Candidate Generation → Rules Filtering → Feature Construction → Ranking → Explanation Generation → Confidence Estimation.

Ranking formula (current v1):

```
Final Score = 0.45 × compatibility + 0.20 × sentiment + 0.20 × popularity + 0.15 × price_fit
```

Each recommendation output includes: ranked item, score, confidence level, reason codes, evidence links, and trust/safety flags.

### Layer 5 — Agent Orchestration

Coordinates specialized AI agents through shared context and events.

Agents: Discovery Agent, Ingredient Safety Agent, Shade Matching Agent, Community Intelligence Agent, Commerce Agent, Explanation Agent.

Orchestration flow: User Intent → Intent Interpreter → Discovery Agent → Compatibility Agent → Safety Agent → Ranking Engine → Explanation Agent → Result.

Principles: structured intermediate outputs, event-driven coordination, traceable reasoning chain, deterministic fallback paths.

### Layer 6 — Experience Delivery

Serves results to users across channels.

Channels: web app, mobile app, AI assistant interface, future brand analytics dashboard.

Core experiences: personalized feed, product detail intelligence panel, compare view, "Ask Aura" assistant, profile dashboard, privacy and consent center.

### Layer 7 — Observability, Governance & Learning

Cross-cutting concerns ensuring trust, compliance, and continuous improvement.

Capabilities: consent management (per-scope, revocable), audit logging (all sensitive mutations), recommendation quality monitoring, feature and score drift detection, fairness checks across skin-tone cohorts, model lifecycle management, privacy controls.

---

# 4. Data Architecture

## Core Data Domains

| Domain                | Contents                                                              |
| --------------------- | --------------------------------------------------------------------- |
| Product Intelligence  | Products, brands, shades, ingredients, sources, prices                |
| Ingredient Ontology   | Ingredient functions, risk flags, skin compatibility, conflict graph  |
| User Profile          | Declared, inferred, imported attributes; behavioral history; consents |
| Recommendation Events | Recommendation records, reason codes, scores, confidence              |
| Community Corpus      | Reviews, sentiment scores, author hashes, community signals           |
| Feedback Events       | Clicks, saves, dismissals, purchases, explicit ratings                |

## Key Modeling Principles

- Separate declared, inferred, and imported user data with `ProfileDataOrigin` enum.
- Normalize products, ingredients, shades, brands, and sources — avoid duplication.
- Preserve recommendation auditability: every recommendation stores model version, score, and reason codes.
- Track consent per scope and per data category. Never process user data without explicit, scoped, revocable consent.
- Store INCI ingredient names as the canonical identifier; common names as aliases.

## Database Technology

- **PostgreSQL**: canonical entities, transactional data, recommendation records
- **pgvector**: product and ingredient embeddings for semantic retrieval
- **Redis**: hot recommendation paths, session context, rate limiting
- **Object Storage**: raw ingestion payloads, ML training snapshots, audit archives

See [Aura_Database_Schema_Prisma.md](Aura_Database_Schema_Prisma.md) for the authoritative Prisma schema.

---

# 5. Recommendation Engine

## Pipeline

1. **Candidate Generation** — narrows the product universe based on query, category, and coarse profile filters
2. **Rules Filtering** — applies hard constraints: allergen exclusions, price bounds, ethical filters, availability, ownership similarity suppression
3. **Feature Construction** — derives: shade compatibility, ingredient safety score, community sentiment alignment, price fit, recency/trend signals, profile similarity
4. **Ranking** — hybrid approach: deterministic scoring rules + ML ranking model + diversity reranking
5. **Explanation Generation** — produces user-facing explanations and structured reason codes
6. **Confidence Estimation** — generates confidence signals based on profile completeness, source quality, model certainty

## Scoring Formula (v1 baseline)

```
Final Score = 0.45 × compatibility_score
            + 0.20 × sentiment_score
            + 0.20 × popularity_score
            + 0.15 × price_fit_score
```

Weights are tunable via the experimentation framework and will evolve with ML training data.

## Recommendation Reason Codes

| Code               | Meaning                                                  |
| ------------------ | -------------------------------------------------------- |
| `SHADE_MATCH`      | Compatible with user's skin tone and undertone           |
| `INGREDIENT_SAFE`  | No flagged ingredients for user's sensitivities          |
| `ALLERGEN_ALERT`   | Contains a user-declared allergen (exclusion flag)       |
| `DUPLICATE_ACTIVE` | Redundant active ingredient with an owned product        |
| `OWNS_SIMILAR`     | User already owns a similar product                      |
| `PRICE_FIT`        | Within user's spending preference range                  |
| `ETHICAL_MATCH`    | Meets user's ethical filters (vegan, cruelty-free, etc.) |
| `COMMUNITY_MATCH`  | Highly rated by users with similar profiles              |

See [Aura_Recommendation_Algorithms.md](Aura_Recommendation_Algorithms.md) for detail.

---

# 6. AI Model Training Pipeline

## Stages

1. **Data Collection** — product catalog metadata, user interactions, community review corpora, product outcomes
2. **Data Validation** — schema validation, null handling, drift checks, label integrity
3. **Feature Engineering** — product embeddings, user preference vectors, ingredient conflict features, price sensitivity features, source trust features
4. **Model Training** — baseline: rules + gradient boosted ranking; later: deep retrieval and ranking if warranted
5. **Evaluation** — offline: NDCG, MAP, precision@k; business: CTR, save rate, conversion lift; trust: explanation helpfulness, dismiss/hide rate
6. **Deployment** — model registry → champion/challenger rollout → shadow evaluation → canary release
7. **Monitoring** — feature drift, score distribution drift, recommendation diversity, fairness checks across demographics

See [Aura_AI_Model_Training_Pipeline.md](Aura_AI_Model_Training_Pipeline.md) for detail.

---

# 7. Personal Intelligence Engine

The Personal Intelligence Engine (PIE) is the AI subsystem that turns raw data and signals into a living user model.

## You Index — Profile Domains

| Domain     | Attributes                                                 |
| ---------- | ---------------------------------------------------------- |
| Beauty     | skin type, undertone, skin tone, skin concerns, allergies  |
| Style      | body shape, style archetype, color preferences             |
| Lifestyle  | wellness goals, ethical preferences, spending preferences  |
| Behavioral | saved items, viewed products, purchase history, dismissals |

## Context Engine Signals

- Time context: season, weather, time of day, event type
- Emotional context: mood input, lifestyle signals
- Bio context (optional): sleep signals, cycle stage, hydration indicators

## Reasoning Engine Output

Every recommendation response from PIE includes:

```
Recommended because:
  - [compatibility reason, e.g., "matches warm undertone"]
  - [safety reason, e.g., "fragrance-free, matches your allergy profile"]
  - [community reason, e.g., "highly rated by users with dry skin"]
```

See [Aura_Personal_Intelligence_Engine_Spec.md](Aura_Personal_Intelligence_Engine_Spec.md) for detail.

---

# 8. AI Agent Architecture

Aura supports specialized agents that coordinate through shared events and context.

## Core Agents

| Agent                        | Responsibility                                                  |
| ---------------------------- | --------------------------------------------------------------- |
| Discovery Agent              | Narrows product universe based on user intent                   |
| Ingredient Safety Agent      | Checks ingredient compatibility against user profile            |
| Shade Matching Agent         | Matches skin tone and undertone to product shades across brands |
| Community Intelligence Agent | Aggregates and analyzes community discussions and sentiment     |
| Commerce Agent               | Finds purchase options, compares prices, validates availability |
| Explanation Agent            | Composes human-readable recommendation rationale                |

## Orchestration Flow

```
User Request
  → Intent Interpreter
  → Discovery Agent (candidates)
  → Ingredient Safety Agent (filter)
  → Shade Matching Agent (compatibility scoring)
  → Community Intelligence Agent (sentiment enrichment)
  → Ranking Engine
  → Explanation Agent
  → Final Response
```

See [Aura_AI_Agent_Architecture.md](Aura_AI_Agent_Architecture.md) for detail.

---

# 9. API Design

## Public Client API: GraphQL

Used for all user-facing data access. Supports flexible queries across products, profiles, recommendations, and community data.

Core mutations require **consent validation** before processing any personal data.

## Internal Service APIs: REST / gRPC

Used for service-to-service communication where latency and clear boundaries matter.

## Auth Pattern

- Authentication: JWT-based, issued by auth service
- Authorization: scoped per-user, per-tenant
- Sensitive mutations: require valid, in-scope consent record
- API keys: for brand analytics and partner APIs

See [Aura_API_Contracts.md](Aura_API_Contracts.md) for GraphQL schema and REST endpoint definitions.

---

# 10. Event Architecture

Aura uses an event-driven architecture for asynchronous scaling, service decoupling, and model learning.

## Canonical Event Topics

| Topic                 | Events                                                    |
| --------------------- | --------------------------------------------------------- |
| `aura.ingestion`      | ProductDiscovered, ProductUpdated, SourceFetched          |
| `aura.catalog`        | ProductEnriched, BrandMerged, IngredientResolved          |
| `aura.profile`        | ProfileUpdated, ConsentChanged, PreferenceInferred        |
| `aura.recommendation` | RecommendationGenerated, RecommendationServed             |
| `aura.feedback`       | FeedbackCaptured (click, save, dismiss, purchase, rating) |
| `aura.governance`     | AuditEvent, ModelDeployed, DriftDetected                  |

All events are immutable, versioned, and include `occurredAt` timestamps. Consumers must be idempotent.

See [Aura_Event_Architecture.md](Aura_Event_Architecture.md) for canonical event schemas.

---

# 11. Technology Stack

## Hybrid Backend Architecture

Aura follows a hybrid backend strategy with clear seam between user-facing CRUD and core domain logic:

| Feature Type     | Technology | Framework             | Use Case                                             |
| ---------------- | ---------- | --------------------- | ---------------------------------------------------- |
| **User API**     | Node.js    | Fastify + Prisma      | Profile, preferences, CRUD, real-time feed           |
| **Core Domain**  | Java 21    | ActiveJ               | Recommendations, ingestion workers, ranking pipeline |
| **ML Inference** | Python     | FastAPI microservices | Model serving, feature pipelines                     |

## Full Stack

| Layer            | Technology                            |
| ---------------- | ------------------------------------- |
| Frontend         | React Router v7 + Tailwind CSS        |
| Mobile           | React Native                          |
| User API         | Node.js + Fastify + Prisma            |
| Core Domain      | Java 21 + ActiveJ                     |
| GraphQL Gateway  | Node.js + Fastify                     |
| Primary DB       | PostgreSQL                            |
| Vector Search    | pgvector (PostgreSQL extension)       |
| Cache            | Redis                                 |
| Object Storage   | S3-compatible                         |
| ML Model Serving | Python + FastAPI                      |
| ML Training      | PyTorch, scikit-learn                 |
| Observability    | Micrometer, OpenTelemetry, Prometheus |
| Infrastructure   | Docker, Kubernetes                    |
| CI/CD            | GitHub Actions                        |

See [Aura_Technical_Stack_Blueprint.md](Aura_Technical_Stack_Blueprint.md) for decisions and rationale.

---

# 12. Monorepo Structure

```text
aura/
  apps/
    web/                  -- React Router v7 web application (with Vite or Rsbuild build tool)
    mobile/               -- React Native app
    api-gateway/          -- Fastify GraphQL / REST gateway
    ingestion-worker/     -- Java/ActiveJ ingestion pipeline
    recommendation-worker/ -- Java/ActiveJ recommendation engine
  services/
    profile-service/      -- User profile, You Index (Fastify + Prisma)
    catalog-service/      -- Product catalog management (Java/ActiveJ)
    recommendation-service/ -- Core ranking and explanation (Java/ActiveJ)
    explainability-service/
    community-intelligence-service/
    governance-service/   -- Consent, audit, moderation
  packages/
    ui/                   -- Shared UI component library
    design-tokens/
    graphql-schema/       -- Shared GraphQL type definitions
    api-client/
    domain-types/         -- Shared TypeScript domain types
    event-contracts/      -- Canonical event schema definitions
    shared-utils/
  data/
    prisma/               -- Prisma schema and migrations
    seed/
  ml/
    notebooks/
    training/
    evaluation/
    feature-pipelines/
  docs/
    prd/
    architecture/
    api/
    runbooks/
  infra/
    docker/
    kubernetes/
    terraform/
    github-actions/
```

---

# 13. Product Roadmap

| Phase   | Name            | Timeline   | Focus              | Key Deliverables                                                                         |
| ------- | --------------- | ---------- | ------------------ | ---------------------------------------------------------------------------------------- |
| Phase 1 | Curated Me      | Months 1–3 | Beauty MVP         | product ingestion, shade matching, ingredient analyzer, recommendation feed, bookmarking |
| Phase 2 | Aura Insights   | Months 4–6 | AI personalization | AI skin analysis, review sentiment, explainable recommendations, product comparison      |
| Phase 3 | Bio Sync        | Months 7–9 | Context-aware      | wearable integrations, routine builder, mood-based styling                               |
| Phase 4 | Aura Collective | Months 10+ | Community          | twin-user discovery, verified reviews, shared routines, brand analytics                  |

### Phase 1 Success Metrics

- Daily Active Users target
- Saved items per active user ≥ 3
- Recommendation CTR ≥ 12%
- Profile completion rate ≥ 60%

### Phase 2 Success Metrics

- Recommendation engagement rate improvement ≥ 15% over Phase 1 baseline
- Explanation helpfulness rating ≥ 4.0/5
- Conversion rate from recommendation to save ≥ 20%

---

# 14. Monetization

| Stream               | Description                                                                                            | Timeline |
| -------------------- | ------------------------------------------------------------------------------------------------------ | -------- |
| Affiliate Commerce   | Commission on purchases made through Aura links                                                        | Phase 1  |
| Premium Subscription | Advanced features: enhanced AI analysis, unlimited comparisons, routine automation                     | Phase 2  |
| Brand Analytics      | Anonymized insights dashboard for brands: ingredient performance, shade satisfaction, sentiment trends | Phase 4  |

---

# 15. Defensibility Strategy

1. **Data Moat** — proprietary dataset of ingredient compatibility, shade match outcomes, and user satisfaction by profile attributes
2. **Personalization Advantage** — recommendation quality compounds as user data grows
3. **Network Effects** — community participation (reviews, routines, twin discovery) increases platform value for all users
4. **Brand Intelligence Layer** — B2B revenue from anonymized analytics creates a second data flywheel
5. **AI Learning Flywheel** — user interactions → feedback signals → model updates → better recommendations → more engagement

---

# 16. Strategic Outcome

Aura becomes the **trusted intelligence layer** between users and commerce platforms. Instead of visiting multiple sites, users rely on Aura to answer "What is right for me?" across beauty, fashion, and wellness — creating a defensible, compounding platform with strong personalization flywheel effects.
