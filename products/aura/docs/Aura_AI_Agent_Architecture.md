# Aura AI Agent Architecture

## Overview

Aura supports a set of **specialized AI agents** that collaborate to answer complex, multi-step user requests. Agents operate on the Aura knowledge graph, product catalog, and user intelligence model. They communicate through structured events and intermediate outputs — not raw LLM messages.

Agents are coordinated by an **Agent Orchestrator** that interprets user intent, routes sub-tasks to the appropriate agents, and assembles the final recommendation or answer.

---

## Core Agents

### 1. Discovery Agent

**Responsibility:** Identify a relevant candidate set of products from the catalog based on user intent and coarse profile filters.

Inputs:

- User intent (natural language query or structured filter)
- User profile summary (category, price range, ethical filters)
- Current platform catalog snapshot

Outputs:

- Ranked candidate list (product IDs + initial relevance scores)

Behavior:

- Uses Data Cloud-managed vector similarity search for semantic product retrieval
- Applies hard filters: category, price range, availability
- Returns top-N candidates (e.g., 50–200) for downstream agents to refine

---

### 2. Ingredient Safety Agent

**Responsibility:** Evaluate each candidate product's ingredient list against the user's safety profile.

Inputs:

- Candidate products with ingredient lists (INCI names)
- User allergies, sensitivities, and declared ingredient concerns (from You Index)

Outputs:

- Per-product safety assessment:
  - `SAFE`: no flagged ingredients
  - `ALLERGEN_ALERT`: contains a user-declared allergen → excluded from recommendations
  - `DUPLICATE_ACTIVE`: redundant active with an owned product → flagged, soft-exclusion
  - `IRRITANT_RISK`: ingredient known to cause issues for user's skin type → flagged with confidence level
- Reason codes attached to each product for use in explanations

Behavior:

- Queries the Ingredient Knowledge Graph for each candidate
- Applies user's declared sensitivity rules with certainty thresholds
- Conservative by default: flags rather than silently excludes uncertain cases

---

### 3. Shade Matching Agent

**Responsibility:** Score shade compatibility between candidate products and the user's skin tone and undertone profile.

Inputs:

- Candidate products with shade metadata (from Shade Ontology)
- User skin tone, undertone, finish preference (from You Index)

Outputs:

- Shade compatibility score per candidate (0.0–1.0)
- Matched shade ID and human-readable match description (e.g., "Warm Beige — close match")

Behavior:

- Maps product shades to the canonical Shade Ontology
- Scores compatibility using tone depth distance + undertone alignment
- Highlights the best-matching shade within a multi-shade product
- Confidence is lower for products with no undertone metadata — flagged accordingly

---

### 4. Community Intelligence Agent

**Responsibility:** Enrich candidate products with aggregated community sentiment and peer signals.

Inputs:

- Candidate product IDs
- User profile attributes relevant to community matching (skin type, concerns)

Outputs:

- Sentiment score per product (from review corpus)
- Community match score: proportion of positive reviews from users with similar profiles
- Key community themes (e.g., "loved by oily skin users", "reported pilling under makeup")

Behavior:

- Queries the Review & Community Corpus
- Applies profile-filtered sentiment: weights reviews from users with similar skin type / concerns more heavily
- Surfaces both positive signals and cautionary themes (surfaced as `trustFlags`)

---

### 5. Commerce Agent

**Responsibility:** Find purchase options, compare prices, and validate product availability.

Inputs:

- Candidate product IDs

Outputs:

- Available merchants per product
- Current prices with links
- Price delta vs. user's declared spending preference
- Affiliate link (where available)

Behavior:

- Queries product source records for merchant and pricing data
- Checks freshness of pricing data (stale data > 24 hours is flagged)
- Generates affiliate links where the merchant relationship exists
- Affiliate placement is always labeled — never influences recommendation ranking

---

### 6. Explanation Agent

**Responsibility:** Compose human-readable, trust-building explanations for each recommendation.

Inputs:

- Final ranked product with score components
- Reason codes from all upstream agents
- User profile context

Outputs:

- Primary explanation (1–2 sentences, user-facing)
- Structured reason list (reason codes + weights)
- Trust flags (allergen alert, low-confidence shade match, stale pricing, etc.)

Behavior:

- Produces natural language explanations from structured reason codes (template + LLM hybrid)
- Never fabricates reasons — all claims are grounded in structured signals
- Confidence level is surfaced when the system has limited data about a user or product

---

## Agent Orchestration

### Orchestration Flow

```
User Request (query or context)
  │
  ▼
Intent Interpreter
  │  (structured intent: category, constraints, goal)
  ▼
Discovery Agent ─────────────────────────────────────────┐
  │ (candidate product list, ~50–200 items)              │
  ▼                                                      │
Ingredient Safety Agent ─── (filter allergens, flag risks) │
  │                                                      │
  ▼                                                      │
Shade Matching Agent ─────── (score shade compatibility) │
  │                                                      │
  ▼                                                      │
Community Intelligence Agent ─ (enrich with sentiment)   │
  │                                                      │
  ▼                                                      │
Commerce Agent ──────────────── (pricing, availability)  │
  │                                                      │
  ▼                                                      │
Ranking Engine ──────── (final weighted score)           │
  │                                                      │
  ▼                                                      │
Explanation Agent ─────── (natural language rationale)   │
  │                                                      │
  ▼                                                      │
Final Recommendation Response ◄──────────────────────────┘
```

### Orchestration Principles

1. **Structured intermediate outputs** — each agent emits a typed, validated payload. No raw LLM text is passed between agents.
2. **Event-driven coordination** — agents publish to internal event topics; the orchestrator subscribes and routes.
3. **Traceable reasoning chain** — every recommendation captures which agent produced each reason code, for auditability.
4. **Deterministic fallback** — when an agent has insufficient data or low confidence, it returns a structured `LOW_CONFIDENCE` signal. The orchestrator falls back to deterministic rules rather than hallucinating.
5. **Parallel execution where safe** — Shade Matching, Ingredient Safety, and Community Intelligence agents can run in parallel after Discovery completes.
6. **Timeout and graceful degradation** — each agent has a timeout budget. Slow or failed agents return partial results; the orchestrator assembles the best available response.

---

## Future Agent Extensions

| Agent                       | Purpose                                                         | Phase   |
| --------------------------- | --------------------------------------------------------------- | ------- |
| Wardrobe Intelligence Agent | Assess compatibility of new item with owned wardrobe            | Phase 3 |
| Routine Analysis Agent      | Identify gaps and redundancies in user's skincare routine       | Phase 3 |
| Trend Intelligence Agent    | Surface rising ingredients, shades, or styles in community data | Phase 4 |
| Autonomous Commerce Agent   | Proactively notify user of restocks, sales, or new dupes        | Phase 4 |
