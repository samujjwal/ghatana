# Aura AI Intelligence Engine Design

## Overview

The Aura Intelligence Engine powers personalized product recommendations. It consists of three specialized AI models trained on product, ingredient, community, and behavioral data — plus an explainability system that ensures every recommendation can be justified in plain language.

The engine is designed for **graceful degradation**: as training data grows, model quality improves. At launch, deterministic rules provide high-confidence fallbacks when ML models have insufficient data.

---

## AI Model Architecture

### Model 1 — Shade Matching Model

**Purpose:** Score compatibility between a product shade and a user's skin tone and undertone.

**Inputs:**

- User skin tone depth (1–10 canonical scale)
- User undertone (cool / warm / neutral / olive)
- Product shade metadata: canonical depth, undertone, finish (from Shade Ontology)

**Output:** Shade compatibility score (0.0–1.0) per shade variant.

**Approach:**

- Phase 1: Deterministic scoring using the Shade Ontology distance formula (undertone alignment + depth proximity)
- Phase 2: Selfie-based undertone inference (computer vision model, opt-in) to improve accuracy for users who haven't declared undertone

**Training signals (Phase 2+):** User feedback on shade matches (shade correct / shade too light / shade too dark, etc. — captured via product rating flow).

---

### Model 2 — Ingredient Compatibility Model

**Purpose:** Assess whether a product's ingredient formulation is compatible with a user's skin profile and declared sensitivities.

**Inputs:**

- Product ingredient list (INCI names, ordered by concentration position)
- User allergen list (declared)
- User skin type and concerns
- Ingredient Knowledge Graph relationships (compatible_with, irritating_for, conflicts_with)

**Output:** Per-product safety assessment:

- `SAFE`: no flagged ingredients
- `ALLERGEN_ALERT`: allergen present → hard exclusion
- `IRRITANT_RISK`: ingredient flagged as potentially irritating for user's skin type → soft flag with confidence level
- `DUPLICATE_ACTIVE`: redundant active ingredient with owned product → soft flag

**Approach:**

- Phase 1: Rule-based traversal of the Ingredient Knowledge Graph — deterministic, highly accurate when graph data is complete
- Phase 2+: Confidence adjustment based on community feedback patterns (e.g., fragrance-sensitized users who still tolerate low-% limonene — nuance captured from outcome data)

---

### Model 3 — Recommendation Ranking Model

**Purpose:** Produce a final ranked list of candidate products for a user, combining compatibility, community, popularity, and price signals.

**Inputs:**

| Feature Group         | Features                                                             |
| --------------------- | -------------------------------------------------------------------- |
| Profile compatibility | Shade compatibility score, ingredient safety score                   |
| Community signals     | Profile-filtered sentiment score, community match rate, review count |
| Behavioral popularity | Save rate, click rate, conversion rate (within skin type cohort)     |
| User preference fit   | Price fit score, style archetype alignment, ethical filter match     |
| Recency               | Product freshness score, trend signal                                |
| Source quality        | Ingredient data completeness, shade metadata confidence              |

**Output:** Score (0.0–1.0) per candidate product; used with diversity and safety post-processing.

**Approach:**

- Phase 1: Deterministic weighted formula:
  ```
  score = 0.45 × compatibility + 0.20 × sentiment + 0.20 × popularity + 0.15 × price_fit
  ```
- Phase 2+: Gradient boosted ranking model (LightGBM or XGBoost) trained on labeled interaction data (saves, purchases, explicit ratings). Champion/challenger deployment — old formula remains as fallback.

**Training signals:** Clicks, saves, dismissals, purchases, explicit "helpful / not helpful" ratings. Higher-weight preference: purchase > save > dismiss.

---

## Explainability System

Every recommendation includes structured reason codes and a human-readable explanation. The explainability system is not bolted on — it is a first-class output of the recommendation pipeline.

**Reason code generation:** Each step of the pipeline attaches reason codes as it processes candidates.

| Agent/Step                     | Reason Codes Generated                                                   |
| ------------------------------ | ------------------------------------------------------------------------ |
| Ingredient Compatibility Model | `INGREDIENT_SAFE`, `ALLERGEN_ALERT`, `DUPLICATE_ACTIVE`, `IRRITANT_RISK` |
| Shade Matching Model           | `SHADE_MATCH` (with shade name)                                          |
| Community Intelligence         | `COMMUNITY_MATCH`, `COMMUNITY_FLAG`                                      |
| Rules Filter                   | `ETHICAL_MATCH`, `PRICE_FIT`                                             |
| Similarity Model               | `OWNS_SIMILAR`                                                           |

**Explanation composition:** Reason codes are converted to natural language via a template-first approach (deterministic, reliable) with an optional LLM polishing step for more conversational phrasing. LLM output is constrained to the structured reason set — no hallucination of reasons not in the pipeline output.

**Example explanation:**

> "We recommended this because it matches your warm undertone in Shade 'Golden Beige', contains no ingredients from your sensitivity list, and is highly rated by users with dry skin."

---

## Feedback Loop

User actions feed the learning pipeline to continuously improve model quality.

| Signal               | Weight          | Usage                                                                 |
| -------------------- | --------------- | --------------------------------------------------------------------- |
| Purchase             | Very high       | Strongest intent signal; used to train ranker on long-term preference |
| Save                 | High            | Strong positive signal; near-purchase intent                          |
| Click                | Medium          | Interest signal; click-without-save is weak positive                  |
| Dismiss              | High (negative) | Strong negative signal for this product type/characteristic           |
| "Not helpful" rating | High            | Direct feedback on recommendation quality                             |
| "Helpful" rating     | High            | Reinforces reasoning patterns                                         |

**Learning loop:** Signals accumulate → labeled dataset grows → model retrained on schedule (weekly batch) → new model evaluated offline → champion/challenger rollout → if champion improves NDCG, it becomes the new production model.

---

## Cold-Start Strategy

For new users with minimal profile data:

| Profile State              | Strategy                                                                                           |
| -------------------------- | -------------------------------------------------------------------------------------------------- |
| No profile data            | Show category-popular products with high community sentiment; no personalization claimed           |
| Skin type declared only    | Apply skin-type-compatible ingredient safety filtering; sort by community match for that skin type |
| Full declared profile      | Full personalization pipeline with declared data; confidence level reflects data completeness      |
| Behavioral history growing | Inferred preferences begin supplementing declared data after 5+ interactions                       |

Every recommendation clearly signals its confidence level to the user. Low-confidence recommendations show "Based on limited profile data — fill in your profile for better recommendations."

---

## Privacy in the AI Engine

- No raw user PII enters the ML training pipeline. Training data uses pseudonymized user IDs.
- User feedback is aggregated before being included in batch training jobs.
- Selfie analysis for undertone inference is processed entirely on-device or in an isolated inference service — no selfie images are stored.
- Users can opt out of contributing their interactions to model training without losing access to personalized recommendations.
