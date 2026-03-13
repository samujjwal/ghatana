# Aura System Architecture Blueprint

## Architecture Overview

Aura follows a **7-layer platform architecture**. Each layer has a clear responsibility boundary and communicates with adjacent layers through defined APIs or event streams. This document provides an engineering-level blueprint; for detailed specifications of the intelligence layers, see [Aura_Intelligence_Platform_Architecture.md](Aura_Intelligence_Platform_Architecture.md).

---

## Layer 1 — Source & Ingestion

**Responsibility:** Collect, parse, normalize, and route raw external data into the platform.

Sources:

- ecommerce catalogs and affiliate product feeds
- brand product pages (scraped or API-supplied)
- ingredient databases (INCI, proprietary enrichment)
- product reviews (retailer sites, community forums)
- community discussions
- optional: user-linked email receipts, wellness app integrations

Services:

- **Source Adapters** — connector per data source type
- **Crawl Scheduler** — manages scheduling, rate limits, and retry logic
- **Parsing Workers** — extract structured data from HTML, JSON, or CSV
- **Enrichment Workers** — resolve ingredients, map shades, score freshness
- **Deduplication Service** — entity resolution across sources
- **Catalog Merge Service** — merges new data into the canonical product record

---

## Layer 2 — Canonical Knowledge

**Responsibility:** Store durable, structured platform intelligence derived from ingestion.

Stores:

- **Product Catalog** — canonical product records with normalized metadata
- **Ingredient Graph** — ingredient relationships, functions, risk flags, compatibility mappings
- **Shade Ontology** — cross-brand shade, tone, undertone, finish, coverage mappings
- **Style Taxonomy** — archetype classifications and color category mappings
- **Review & Community Corpus** — structured reviews with sentiment scores and vector embeddings
- **Source Provenance Store** — data origin, freshness score, confidence level per field

Technology:

- PostgreSQL for canonical entities and transactional data
- pgvector for semantic similarity search
- Object storage for raw ingestion payloads and snapshots
- Redis for hot recommendation paths

---

## Layer 3 — Personal Intelligence

**Responsibility:** Maintain a live, user-specific intelligence model (You Index).

Modules:

- **You Index Service** — aggregates declared, inferred, behavioral, and imported profile attributes
- **Context Engine** — evaluates momentary signals (season, weather, event type, mood, optional bio data)
- **Ownership & History Service** — tracks items viewed, saved, purchased, dismissed, or already owned
- **Preference Learning Service** — derives long-term preference vectors from behavioral history

Design principles:

- Users can view and edit inferred attributes
- Clear separation of declared vs inferred vs imported fields
- Optional integrations are privacy-gated with explicit scoped consent
- All inference decisions are auditable and reversible

---

## Layer 4 — Decision & Recommendation

**Responsibility:** Transform knowledge and user context into ranked, explained recommendations.

Pipeline:

1. Candidate Generation — coarse product set from catalog based on query and profile
2. Rules Filtering — hard exclusions (allergens, price bounds, ethical filters, availability)
3. Feature Construction — compatibility, sentiment, popularity, price fit, trend signals
4. Ranking — hybrid deterministic scoring + ML ranking model + diversity reranking
5. Explanation Generation — structured reason codes and human-readable text
6. Confidence Estimation — based on profile completeness, source quality, model certainty

Output per recommendation:

```json
{
  "productId": "prd_001",
  "score": 0.91,
  "confidence": 0.84,
  "reasonCodes": ["SHADE_MATCH", "INGREDIENT_SAFE", "COMMUNITY_MATCH"],
  "explanation": "Matches your warm undertone, fragrance-free, and highly rated by users with dry skin.",
  "trustFlags": []
}
```

---

## Layer 5 — Agent Orchestration

**Responsibility:** Coordinate specialized AI agents for complex, multi-step requests.

Agents: Discovery Agent, Ingredient Safety Agent, Shade Matching Agent, Community Intelligence Agent, Commerce Agent, Explanation Agent.

Coordination: event-driven, with structured intermediate outputs and traceable reasoning chain. Falls back to deterministic paths when model confidence is low.

---

## Layer 6 — Experience Delivery

**Responsibility:** Serve platform intelligence to users across all channels.

Channels:

- Web app (React Router v7 + React)
- Mobile app (React Native)
- "Ask Aura" AI assistant interface
- Brand analytics dashboard (Phase 4)

Core experiences: personalized feed, product intelligence panel, compare view, profile dashboard, privacy and consent center.

API surface: GraphQL for client queries and mutations; REST/gRPC for internal service-to-service communication.

---

## Layer 7 — Observability, Governance & Learning

**Responsibility:** Ensure trust, compliance, and continuous system improvement.

Capabilities:

- **Consent Management** — per-scope, revocable consents tracked in the database; optional integrations and high-sensitivity processing validate consent before execution
- **Audit Logging** — immutable log of all sensitive profile and preference changes
- **Recommendation Quality Monitoring** — CTR, save rate, dismiss rate, conversion lift, explanation helpfulness scores
- **Safety Outcome Monitoring** — shade-miss, adverse reaction, return, and regret signals routed for review and model updates
- **Feature & Score Drift Detection** — alerting when distribution shift exceeds defined thresholds
- **Fairness Monitoring** — recommendation quality checks across skin-tone and demographic cohorts
- **Model Lifecycle Management** — champion/challenger evaluation, canary releases, rollback
- **Privacy Controls** — data minimization, retention policies, data export, and right-to-deletion workflows

Observability stack: Micrometer, OpenTelemetry, Prometheus, Grafana.

---

## Cross-Cutting Concerns

### Privacy by Design

- Core service data is processed to operate Aura for the authenticated user
- Explicit scoped consent is required before optional imports or high-sensitivity enrichment
- Data minimization: collect only what is needed for stated purposes
- PII routing: sensitive fields never enter analytics streams without tokenization

### Security

- Authentication: JWT-based, issued by auth service
- Authorization: per-user, per-scope
- Input validation at all API boundaries
- Secrets managed via environment or secrets manager (never hardcoded)

### Observability

- All services emit structured logs, traces, and metrics
- Distributed tracing via OpenTelemetry across all service calls
- SLO targets defined per experience tier

### Scalability Strategy

- Start as a modular monolith if team size warrants
- Extract ingestion, recommendation engine, and community analysis into independent services as load grows
- Keep explainability and consent as first-class services from day one — do not bury them in application logic
