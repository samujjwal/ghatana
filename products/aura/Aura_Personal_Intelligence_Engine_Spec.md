# Aura Personal Intelligence Engine Specification

## Overview

The Aura Personal Intelligence Engine (PIE) is the core AI subsystem that turns raw product data,
community signals, and user context into a living, personalized intelligence model.

The engine answers: **"What is right for this specific user at this specific moment?"**

The PIE integrates four major systems:

1. The You Index — personal profile model
2. Context Engine — dynamic situational state
3. Decision Reasoning Engine — explainable recommendation pipeline
4. Personalization Learning Loop — continuous improvement from feedback

---

# 1. The You Index (Personal Profile Model)

The You Index is Aura's unified representation of the user. It is not a static form — it is a
living model that grows with user behavior and can always be viewed and corrected by the user.

## Data Origin Categories

| Origin | Description | Editability |
|--------|-------------|------------|
| `DECLARED` | Explicitly entered by the user | Editable at any time |
| `INFERRED` | Derived from behavioral signals or AI analysis | Visible to user; overridable |
| `IMPORTED` | Pulled from third-party integrations (requires explicit per-scope consent) | User can revoke integration to remove |

## Profile Domains

**Beauty Profile**
- Skin type (dry, oily, combination, normal, sensitive, acne-prone)
- Undertone (cool, warm, neutral, olive)
- Skin tone (canonical depth 1–10 from Shade Color Ontology)
- Skin concerns (hyperpigmentation, redness, fine lines, dehydration, etc.)
- Allergies and sensitivities (INCI ingredient names or classes)

**Style Profile**
- Style archetype (primary + secondary, from Style Archetype Taxonomy)
- Color palette preferences
- Body shape (for fashion Phase 3+)

**Lifestyle Profile**
- Wellness goals
- Ethical preferences (cruelty-free, vegan, sustainable packaging, etc.)
- Spending preference range

**Behavioral Profile** *(inferred)*
- Saved items and categories
- Dismissed items and characteristics (negative preference signals)
- Viewed products and dwell time
- Optional: purchase history (imported with consent)

---

# 2. Context Engine

The Context Engine adjusts recommendations based on **momentary signals** — signals that are
relevant right now but differ from static user preferences.

## Context Signal Categories

**Time context**
- Season (spring / summer / autumn / winter)
- Time of day (morning routine vs. evening routine)
- Upcoming event type (wedding, outdoor festival, beach vacation, etc.)

**Emotional / lifestyle context**
- Mood input (optional, user-declared)
- Lifestyle signal (high-stress week, travel)

**Bio context** *(optional integrations, explicit opt-in required)*
- Sleep quality signals (from wearable)
- Hormonal cycle stage (user-declared or wearable integration)
- Hydration / activity level

**Wardrobe context** *(Phase 3)*
- Owned wardrobe items
- Recently worn outfits

## Context Adjustment Behavior

Context signals apply a **multiplicative adjustment** to the recommendation score rather than replacing
base compatibility scores. This ensures that base compatibility (shade, ingredient safety) is never
overridden by context — context can boost or penalize, but cannot promote an unsafe product.

---

# 3. Decision Reasoning Engine

The reasoning engine produces explainable, structured recommendations by executing the full
recommendation pipeline against the user's You Index and current context.

## Pipeline

| Step | Description |
|------|-------------|
| 1. Candidate Generation | Narrow the product catalog to relevant candidates using category, price, and availability filters |
| 2. Compatibility Filtering | Apply allergen exclusions and hard ethical filters |
| 3. Feature Construction | Build compatibility, sentiment, popularity, context, and freshness features |
| 4. Context Weighting | Apply context multipliers to feature scores |
| 5. Ranking | Score candidates using the ranking formula or ML ranker |
| 6. Explanation Generation | Attach reason codes and produce human-readable explanation |
| 7. Confidence Estimation | Evaluate profile completeness and source quality to generate confidence level |

## Ranking Formula

```
score = 0.45 × compatibility_score
      + 0.20 × community_sentiment_score
      + 0.20 × popularity_score
      + 0.15 × price_fit_score
```

Context multipliers are applied after the base score is calculated.

## Example Reasoning Output

```
Recommended because:
  ✓ Shade 'Warm Honey' matches your warm undertone (score: 0.92)
  ✓ No flagged ingredients for your sensitivity profile
  ✓ Highly rated by users with dry skin (sentiment: 0.88, 412 reviews)
  ✓ Priced within your preference range ($38)
Confidence: High — full profile data available
```

---

# 4. Personalization Learning Loop

Aura continuously improves recommendations for each user as behavioral signals accumulate.

## Learning Signals

| Signal | Weight | Interpretation |
|--------|--------|---------------|
| Purchase | Very high | User committed to this product — strong positive signal |
| Save | High | Strong intent to consider or buy |
| "Helpful" rating | High | User confirms the recommendation was on-target |
| Click | Medium | Interest, but not commitment |
| "Not helpful" rating | High | Recommendation missed the mark for stated reason |
| Dismiss | Medium-high | User explicitly rejected this product |

## Learning Cycle

```
User interaction
  → FeedbackCaptured event emitted
  → Feedback Processor aggregates signals
  → User preference vectors updated (near real-time for obvious signals)
  → Model retraining batch (weekly) incorporates new labeled examples
  → Improved recommendations served on next session
```

## Near-Term Preference Updates

Session-level signals (saves, dismissals from the current session) update the user's preference
state before the next weekly model training cycle. A single strong negative signal (repeatedly
dismiss a product type) takes effect immediately, ensuring Aura stops surfacing unwanted content
right away.

---

# 5. Confidence & Trust Signals

Every recommendation from the PIE includes a structured confidence assessment.

## Confidence Factors

| Factor | Effect on Confidence |
|--------|---------------------|
| Profile completeness (skin type, undertone, allergies all declared) | Full profile → high confidence |
| Ingredient data completeness for the product | Incomplete ingredient list → lower confidence |
| Shade metadata availability (undertone, depth) | No undertone metadata → lower shade confidence |
| Community sample size | < 20 relevant reviews → lower sentiment confidence |
| Behavioral history volume | Fewer than 5 interactions → lower preference confidence |

## User-Visible Confidence Signals

- **High confidence** — shown as a standard recommendation card
- **Medium confidence** — shown with "Based on your declared skin type — add more details for better recommendations" prompt
- **Low confidence** — shown with "Showing popular picks — complete your profile for personalized recommendations"

---

# 6. Future Extensions

| Extension | Phase | Description |
|-----------|-------|-------------|
| Wardrobe intelligence | Phase 3 | PIE integrates owned wardrobe items into outfit compatibility and coordination recommendations |
| Routine automation | Phase 3 | PIE analyzes declared/saved products to generate morning and evening routine sequences with step-by-step ordering |
| Predictive skincare recommendations | Phase 3 | PIE uses seasonal signals and optional bio context to proactively suggest skincare regimen adjustments before problems appear |
| Autonomous shopping agents | Phase 4 | PIE-powered agents monitor for restocks, price drops, and new dupes on the user's behalf and surface alerts |
| Twin-user personalization network | Phase 4 | PIE uses profile similarity scores to identify "users like you" and weight their behavioral signals more heavily in ranking |
| Longitudinal skin tracking | Phase 4 | PIE allows users to log skin changes over time and uses this to adjust its model of how their skin responds to products |
| Multi-profile household mode | Phase 4 | PIE maintains separate You Indexes for multiple household members under a single account |

---

# Strategic Outcome

The Personal Intelligence Engine transforms Aura from a product discovery tool into a
**trusted AI advisor** for lifestyle decisions. As the You Index deepens and behavioral data
accumulates, recommendations become more precise, explanations become more personalized, and the
user's trust in Aura's judgment increases — creating a self-reinforcing personalization flywheel
that makes Aura more valuable the longer it is used.
