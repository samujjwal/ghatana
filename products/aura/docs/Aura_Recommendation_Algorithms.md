# Aura Recommendation Algorithms Specification

## Overview

Aura uses a **hybrid recommendation architecture** combining rules-based filtering, machine learning ranking, and deterministic post-processing. The pipeline is designed for explainability, safety, and gradual improvement as data accumulates.

---

## Recommendation Pipeline

### Step 1 — Candidate Generation

Generates a relevant product subset from the full catalog using coarse, fast filters:

- **Category filter:** Only products in the relevant category or subcategory
- **Hard exclusions:** Allergen-flagged ingredients, user-declared ethical filters, price bounds
- **Availability filter:** Products with at least one in-stock source record
- **Semantic retrieval:** Optional—vector similarity search against user preference embedding (Phase 2+)

Target candidate set size: 50–200 products (before rules filtering).

---

### Step 2 — Rules Filtering

Applies non-negotiable hard constraints. Filtered products are excluded from ranking and may surface as warnings.

| Rule                                                           | Action                                                  |
| -------------------------------------------------------------- | ------------------------------------------------------- |
| Contains allergen from user's declared allergen list           | `ALLERGEN_ALERT` → excluded from recommendations        |
| Ethical filter violated (e.g., not cruelty-free when required) | Excluded silently                                       |
| Price outside user's declared range                            | Soft exclusion (surfaced as "over budget" if requested) |
| Ingredient conflicts with an owned product's key active        | `DUPLICATE_ACTIVE` flag → deprioritized, not excluded   |
| User already owns this exact product                           | Excluded by default; shown in "Already Own" shelf       |

---

### Step 3 — Feature Construction

Builds per-candidate decision features:

| Feature               | Description                                                                                              |
| --------------------- | -------------------------------------------------------------------------------------------------------- |
| `compatibility_score` | Weighted combination of shade match and ingredient compatibility                                         |
| `sentiment_score`     | Community sentiment score, profile-filtered (weighted toward reviews from users with similar attributes) |
| `popularity_score`    | Normalized save/click rate within category and skin type cohort                                          |
| `price_fit_score`     | Proximity to user's spending preference midpoint (1.0 = exact match, decays with distance)               |
| `recency_score`       | Boost for newly ingested or recently updated products (trend signal)                                     |
| `source_trust_score`  | Confidence in the product data quality based on ingestion source reliability                             |

---

### Step 4 — Ranking

**Baseline ranking formula (v1):**

```
final_score = 0.45 × compatibility_score
            + 0.20 × sentiment_score
            + 0.20 × popularity_score
            + 0.15 × price_fit_score
```

**Phase 2+ ML Ranker:** Gradient boosted model trained on labeled interaction data (saves, purchases, explicit ratings). Replaces the formula with a learned model while using the same features. The formula continues to serve as a deterministic fallback and explainability reference.

**Post-Ranking Adjustments:**

| Adjustment              | Description                                                                                                            |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **Diversity reranking** | Ensures the returned list covers multiple brands and shade ranges (avoid recommending 10 products from the same brand) |
| **Novelty boost**       | Slight uplift for products the user has never seen before, preventing feed staleness                                   |
| **Safety promotion**    | Products with `INGREDIENT_SAFE` and `SHADE_MATCH` reason codes rank above otherwise equal products                     |

---

### Step 5 — Explanation Generation

Every recommendation is accompanied by:

1. **Structured reason codes** — machine-readable, used for filtering and UI logic
2. **Human-readable explanation** — 1–2 sentences generated from reason codes via template + LLM hybrid
3. **Evidence references** — specific ingredient names, shade names, or community stats cited

**Example explanation output:**

```json
{
  "score": 0.91,
  "confidence": 0.84,
  "reasonCodes": ["SHADE_MATCH", "INGREDIENT_SAFE", "COMMUNITY_MATCH"],
  "explanation": "Matches your warm undertone in Shade 'Golden Beige'. Fragrance-free and highly rated by users with dry skin.",
  "trustFlags": []
}
```

---

### Step 6 — Confidence Estimation

A confidence score (0.0–1.0) is generated alongside every recommendation score:

| Factor                      | Impact on Confidence                                        |
| --------------------------- | ----------------------------------------------------------- |
| Profile completeness        | Low completeness → lower confidence                         |
| Ingredient data quality     | Missing or partial ingredient list → lower confidence       |
| Shade metadata availability | No undertone metadata → lower shade confidence              |
| Community sample size       | Fewer than 20 relevant reviews → lower sentiment confidence |
| Model certainty             | High variance in ML model predictions → lower confidence    |

Low-confidence recommendations surface a `LOW_CONFIDENCE` trust flag to the user.

---

## Personalization Signals

| Signal           | Source                       | Usage                                                                    |
| ---------------- | ---------------------------- | ------------------------------------------------------------------------ |
| Skin type        | Declared / inferred          | Weights community reviews from same skin type more heavily               |
| Undertone        | Declared / inferred          | Drives shade compatibility scoring                                       |
| Allergies        | Declared                     | Hard exclusion via allergen rules                                        |
| Saved items      | Behavioral                   | Trains preference learning model; similarity to saved items boosts score |
| Dismissed items  | Behavioral                   | Trains negative preference model; penalizes similar products             |
| Purchases        | Behavioral (optional import) | Highest-weight positive signal for learning                              |
| Explicit ratings | Behavioral                   | Strongest per-item signal                                                |
| Post-use outcomes | Behavioral                  | Shade miss, adverse reaction, and return reports update ranking and safety rules |

---

## Fairness & Quality Monitoring

| Metric                     | Description                                                        | Target                                                     |
| -------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------------- |
| Median time-to-decision    | Time from recommendation surface entry to confident shortlist/save | Improve over baseline per cohort                           |
| Shade-miss rate            | Share of supported shade outcomes reported as mismatch             | Improve over baseline per cohort                           |
| Adverse reaction rate      | Reported irritation or reaction for kept recommended items         | Below defined safety threshold per cohort                  |
| Return / regret rate       | Share of recommendation-attributed purchases later returned/regretted | Lower than self-directed baseline per cohort            |
| Explanation helpfulness    | User rating of "was this explanation helpful?"                     | ≥ 4.0 / 5.0                                                |
| Coverage across skin tones | Ensures all skin tone depth levels receive quality recommendations | No tone group below 80% of best-performing group's metrics |
| Brand diversity            | Ensures no single brand dominates recommendation output            | ≥ 3 unique brands in top-10 results                        |

---

## Evaluation Metrics

### Offline Metrics (Pre-Deployment)

| Metric      | Description                                           |
| ----------- | ----------------------------------------------------- |
| NDCG@10     | Normalized Discounted Cumulative Gain at rank 10      |
| MAP@10      | Mean Average Precision at rank 10                     |
| Precision@5 | Proportion of top-5 recommendations that are relevant |

### Online Metrics (Post-Deployment)

| Metric                | Rationale                                                             |
| --------------------- | --------------------------------------------------------------------- |
| Median time-to-decision | Measures whether Aura is reducing research effort                   |
| Shade-miss rate         | Measures whether supported complexion recommendations are working    |
| Adverse reaction rate   | Measures whether safety logic is actually protecting users          |
| Return reduction rate   | Tracks whether Aura's product fit predictions reduce purchase returns |
| Satisfaction feedback   | Explicit "helpful / not helpful" ratings                             |
