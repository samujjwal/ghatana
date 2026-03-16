# Aura Master Platform Specification

Version: 2.2
Date: March 2026

## Document Authority & Navigation

**This document is the single authoritative source of truth** for Aura platform scope, product vision, technical architecture, consent policy, canonical contracts, and success metrics.

**For executive overview and quick navigation**, see `Aura_Canonical_Platform_Specification.md` - a non-authoritative summary that points to detailed sections in this document.

**For standardized terminology**, see `Aura_Glossary.md` - the authoritative glossary for all Aura platform terms.

**Implementation Rule**: Teams should implement from this document only. If any specialized document conflicts with this specification, this document wins until the downstream doc is synchronized.

---

# 0. Document Governance

- **Authority:** This document is the only source of truth for product scope, trust policy, consent boundaries, canonical contracts, and success metrics.
- **Role of specialized documents:** Specialized docs may elaborate on implementation detail, but if any conflict exists, this document wins until the downstream doc is synchronized.
- **Role of the canonical index:** `Aura_Canonical_Platform_Specification.md` is an executive summary and navigation document only. It is not an authority for policy or contracts.
- **Implementation rule:** Teams should not implement from a downstream contradiction. Reconcile the conflict here first, then propagate the update outward.

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

Detailed execution guidance lives in:

- `Aura_Consumer_Value_Operating_Model.md` for how Aura proves value to consumers session by session
- `Aura_AI_ML_Data_Operating_Model.md` for how Aura manages data quality, model risk, and learning loops
- `Aura_Shared_Platform_Integration_Spec.md` for how Aura integrates AEP, Data Cloud, and shared platform services
- `Aura_Task_Execution_Matrix.md` for task-level what/how/where/validation detail across active delivery work
- `Aura_Long_Horizon_Task_Execution_Matrix.md` for task-level execution detail across Weeks 25-104
- `Aura_Full_Product_Implementation_Plan_104_Weeks.md` for the long-horizon full-product sequencing and reuse-first implementation plan

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

## Shared Platform Boundary Rules

- **AEP is the only event communication path:** Aura publishes and consumes cross-process events through `products/aep/platform` and its contracts. Aura code must not integrate directly with Event Cloud or raw broker infrastructure.
- **Data Cloud is the authoritative data-management plane:** Durable data handling, storage lifecycle, lineage, retention, export assembly, and plugin-managed data access run through `products/data-cloud/platform` or an approved `products/data-cloud/spi` plugin. Aura may define logical schemas and dataset contracts, but not bypass Data Cloud for managed data paths.
- **Security, auth, audit, and observability are shared capabilities:** Aura integrates shared auth, security, governance, and o11y services/modules. Aura should not build product-local replacements for those cross-cutting concerns.

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

Components: Source Adapters, Crawl Scheduler, Parsing Workers, Enrichment Workers, Deduplication Service, Catalog Merge Service, Data Cloud dataset/plugin writers, AEP publishers.

### Layer 2 — Canonical Knowledge

Converts normalized inputs into durable, structured platform intelligence.

Stores: Product Catalog, Ingredient Graph, Shade Ontology, Style Taxonomy, Review and Community Corpus, Source Provenance Store.

Storage: Data Cloud-managed relational, vector, cache, and object-storage plugins for canonical entities, semantic retrieval, raw snapshots, and hot paths.

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

Principles: structured intermediate outputs, AEP-mediated coordination, traceable reasoning chain, deterministic fallback paths.

### Layer 6 — Experience Delivery

Serves results to users across channels.

Channels: web app, mobile app, AI assistant interface, future brand analytics dashboard.

Core experiences: personalized feed, product detail intelligence panel, compare view, "Ask Aura" assistant, profile dashboard, privacy and consent center.

### Layer 7 — Observability, Governance & Learning

Cross-cutting concerns ensuring trust, compliance, and continuous improvement.

Capabilities: consent management (per-scope, revocable), audit logging (all sensitive mutations), recommendation quality monitoring, feature and score drift detection, fairness checks across skin-tone cohorts, model lifecycle management, privacy controls, and post-purchase/post-use safety outcome triage, implemented through shared governance, security, and observability capabilities.

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
- Track consent per optional integration and per high-sensitivity processing category. Core Aura service data required to operate the product is processed as part of the service experience; optional imports and high-sensitivity enrichments require explicit, scoped, revocable consent.
- Store INCI ingredient names as the canonical identifier; common names as aliases.

## Database Technology

- **Data Cloud relational plugin**: canonical entities, transactional data, recommendation records
- **Data Cloud vector plugin**: product and ingredient embeddings for semantic retrieval
- **Data Cloud cache plugin**: hot recommendation paths, session context, rate limiting
- **Data Cloud object-storage plugin**: raw ingestion payloads, ML training snapshots, audit archives

Aura may reference PostgreSQL, pgvector, Redis, or S3-compatible storage as underlying Data Cloud implementations, but Aura-owned code should integrate through Data Cloud-managed interfaces and plugins.

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
5. **Evaluation** — offline: NDCG, MAP, precision@k; outcomes: time-to-decision, shade-miss rate, adverse reaction rate, return reduction; trust: explanation helpfulness, low-confidence disclosure quality
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

Used for all user-facing data access. Supports flexible queries across products, profiles, recommendations, outcomes, and community data.

Core first-party service mutations do **not** require optional-integration consent. Consent validation is required for optional imports and high-sensitivity processing only.

## Internal Service APIs: REST / gRPC

Used for service-to-service communication where latency and clear boundaries matter.

## Auth Pattern

- Authentication: JWT-based, issued by auth service
- Authorization: scoped per-user, per-tenant
- Core service mutations: allowed for authenticated users within normal product permissions
- Optional imports and high-sensitivity processing: require valid, in-scope consent record
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
| `aura.profile`        | ProfileUpdated, ProfileAttributeOverridden, ConsentGranted, ConsentRevoked, DataExportRequested |
| `aura.recommendation` | RecommendationGenerated, RecommendationServed, RecommendationOutcomeCaptured |
| `aura.feedback`       | FeedbackCaptured (view, click, save, dismiss, purchase, helpful, not_helpful, rating) |
| `aura.governance`     | AuditEvent, ModelDeployed, DriftDetected, FairnessAlertTriggered |

All events are immutable, versioned, and include `occurredAt` timestamps. Consumers must be idempotent. Cross-process publication and subscription happen through AEP only.

See [Aura_Event_Architecture.md](Aura_Event_Architecture.md) for canonical event schemas.

---

# 11. Technology Stack

## Hybrid Backend Architecture

Aura follows a hybrid backend strategy with clear seam between user-facing CRUD and core domain logic:

| Feature Type     | Technology | Framework             | Use Case                                             |
| ---------------- | ---------- | --------------------- | ---------------------------------------------------- |
| **User API**     | Node.js    | Fastify + Prisma      | Profile, preferences, CRUD, real-time feed           |
| **Core Domain**  | Java 21    | ActiveJ               | Recommendations, ingestion workers, ranking pipeline |
| **ML Inference** | Python     | FastAPI inference service | Model serving, feature pipelines, evaluation hooks |

## Full Stack

| Layer            | Technology                            |
| ---------------- | ------------------------------------- |
| Frontend         | React Router v7 + Tailwind CSS        |
| Mobile           | React Native                          |
| User API         | Node.js + Fastify + Prisma            |
| Core Domain      | Java 21 + ActiveJ                     |
| GraphQL Gateway  | Node.js + Fastify                     |
| Event Plane      | AEP                                   |
| Data Plane       | Data Cloud + approved Data Cloud plugins |
| Relational Store | Data Cloud-managed PostgreSQL plugin  |
| Vector Search    | Data Cloud-managed pgvector plugin    |
| Cache            | Data Cloud-managed Redis plugin       |
| Object Storage   | Data Cloud-managed S3-compatible plugin |
| ML Model Serving | Python + FastAPI                      |
| ML Training      | PyTorch, scikit-learn                 |
| Security/Auth    | Shared auth service, security modules |
| Observability    | Shared o11y platform via Micrometer, OpenTelemetry, Prometheus |
| Infrastructure   | Docker, Kubernetes                    |
| CI/CD            | Gitea Actions                         |

Aura keeps the early-stage deployment surface intentionally small: `api`, `core-worker`, and
`ml-inference`. Product domains stay modular through explicit internal boundaries and shared
contracts, not through day-one service sprawl. Aura owns product behavior, while AEP, Data Cloud,
shared security, and shared o11y own the underlying event, data, and cross-cutting platform planes.

See [Aura_Technical_Stack_Blueprint.md](Aura_Technical_Stack_Blueprint.md) for decisions and rationale.

---

# 12. Monorepo Structure

```text
aura/
  .gitea/
    workflows/            -- Gitea Actions CI/CD pipelines
  apps/
    web/                  -- React Router v7 web application (with Vite or Rsbuild build tool)
    mobile/               -- React Native app
    api/                  -- Fastify GraphQL / REST BFF and modular monolith for user-facing flows
    core-worker/          -- Java/ActiveJ host for ingestion, enrichment, ranking, and async jobs
    ml-inference/         -- Python/FastAPI inference boundary for model serving
  domains/
    profile/              -- You Index rules, overrides, visibility, and consent-aware profile logic
    catalog/              -- Product, ingredient, shade, and source normalization rules
    recommendation/       -- Candidate generation, scoring, confidence, and ranking logic
    explainability/       -- Reason codes, evidence assembly, trust copy, transparency rules
    community/            -- Review analysis, sentiment, and twin-user signals
    governance/           -- Consent policy, audit, moderation, and safety rules
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
```

`domains/` are hard module boundaries, not day-one deployment units. Extract a domain into its own
service only when runtime isolation, sustained load, compliance needs, or team ownership create a
clear operational benefit.

---

# 13. Product Roadmap

| Phase   | Name            | Timeline   | Focus              | Key Deliverables                                                                         |
| ------- | --------------- | ---------- | ------------------ | ---------------------------------------------------------------------------------------- |
| Phase 1 | Curated Me      | Months 1–3 | Beauty MVP         | product ingestion, shade matching, ingredient analyzer, recommendation feed, bookmarking |
| Phase 2 | Aura Insights   | Months 4–6 | AI personalization | AI skin analysis, review sentiment, explainable recommendations, product comparison      |
| Phase 3 | Bio Sync        | Months 7–9 | Context-aware      | wearable integrations, routine builder, mood-based styling                               |
| Phase 4 | Aura Collective | Months 10+ | Community          | twin-user discovery, verified reviews, shared routines, brand analytics                  |

### Launch Milestones

| Milestone | Target Timing | Scope |
| --------- | ------------- | ----- |
| Internal alpha | Month 3 | internal or employee-like cohort, ~50 users, trust and instrumentation validation |
| Invite beta | Month 5 launch with Month 5-6 ramp | external invite-only cohort, up to ~500 users, trust and outcome measurement |
| Public launch | Month 8 | broad beauty-first public availability after hardening and launch review |

Invite beta requires privacy review completion and counsel-approved consent/notice language. Public
launch is blocked on unresolved privacy, compliance, or consent-policy issues.

### Phase 1 Success Metrics

- Median time-to-decision for supported beauty tasks ≤ 10 minutes
- Shade-miss rate for Aura-assisted supported foundation purchases ≤ 15%
- Ingredient safety false-negative rate < 1% in QA-reviewed safety audits
- Recommendation-attributed adverse reaction reports triaged within 24 hours
- Recommendation-attributed return / "not keeping it" rate at least 15% below self-directed baseline once enough data exists

### Phase 2 Success Metrics

- Median time-to-decision improves by ≥ 20% vs. Phase 1 baseline
- Recommendation-attributed return reduction sustained across skincare and complexion categories
- Shade-miss rate improves by ≥ 20% vs. Phase 1 baseline for supported categories
- Adverse reaction report rate remains below defined safety threshold per 1,000 recommendation-attributed uses
- Explanation helpfulness rating ≥ 4.0/5 with evidence payload coverage on all recommendation surfaces

---

# 14. Monetization

| Stream               | Description                                                                                            | Timeline |
| -------------------- | ------------------------------------------------------------------------------------------------------ | -------- |
| Affiliate Commerce   | Commission on purchases made through Aura links                                                        | Phase 2  |
| Premium Subscription | Advanced features: enhanced AI analysis, unlimited comparisons, routine automation                     | Phase 3+ |
| Brand Analytics      | Anonymized insights dashboard for brands: ingredient performance, shade satisfaction, sentiment trends | Phase 4  |

---

# 15. Defensibility Strategy

1. **Data Moat** — proprietary dataset of ingredient compatibility, shade match outcomes, and user satisfaction by profile attributes
2. **Personalization Advantage** — recommendation quality compounds as user data grows
3. **Network Effects** — community participation (reviews, routines, twin discovery) increases platform value for all users
4. **Brand Intelligence Layer** — B2B revenue from anonymized analytics creates a second data flywheel
5. **AI Learning Flywheel** — user interactions → feedback signals → model updates → better recommendations → better outcomes and stronger trust

---

# 16. Strategic Outcome

Aura becomes the **trusted intelligence layer** between users and commerce platforms. Instead of visiting multiple sites, users rely on Aura to answer "What is right for me?" across beauty, fashion, and wellness — creating a defensible, compounding platform with strong personalization flywheel effects.
