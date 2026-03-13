# Aura Data Architecture

## Overview

The Aura data architecture is organized into four primary intelligence domains. Each domain has clear ownership, storage patterns, and access patterns. This document defines the logical data model; the physical Prisma schema is in [Aura_Database_Schema_Prisma.md](Aura_Database_Schema_Prisma.md).

---

## Core Data Domains

### 1. Product Intelligence

Canonical representation of all products, brands, shades, and ingredients ingested from external sources.

**Key entities:**

| Entity        | Key Fields                                                                                                        |
| ------------- | ----------------------------------------------------------------------------------------------------------------- |
| Product       | `product_id`, `brand_id`, `name`, `category`, `subcategory`, `price_min`, `price_max`, `currency`, `external_ref` |
| Brand         | `brand_id`, `name`, `metadata`                                                                                    |
| ProductShade  | `shade_id`, `product_id`, `name`, `undertone`, `finish`, `hex_code`, `canonical_depth`                            |
| ProductSource | `source_id`, `product_id`, `source_type`, `source_url`, `fetched_at`, `freshness_score`                           |

**Example product record:**

```json
{
  "product_id": "prd_001",
  "brand": "Example Brand",
  "category": "foundation",
  "subcategory": "liquid",
  "shades": [
    {
      "name": "Warm Beige",
      "undertone": "warm",
      "finish": "satin",
      "canonical_depth": 5
    }
  ],
  "ingredients": ["water", "glycerin", "niacinamide", "titanium dioxide"],
  "price_min": 38,
  "price_max": 42,
  "currency": "USD"
}
```

---

### 2. Ingredient Ontology

Structured knowledge about cosmetic ingredients: their functions, safety classifications, and relationships to skin types and concerns.

**Key entities:**

| Entity            | Key Fields                                                                                  |
| ----------------- | ------------------------------------------------------------------------------------------- |
| Ingredient        | `ingredient_id`, `inci_name`, `common_name`, `functions`, `risk_flags`, `regulatory_status` |
| ProductIngredient | `product_id`, `ingredient_id`, `position`, `concentration`                                  |

**Ingredient attributes:**

- `inci_name`: canonical INCI identifier (e.g., "Niacinamidum")
- `common_name`: human-readable alias (e.g., "Niacinamide", "Vitamin B3")
- `functions`: roles in formulation (e.g., ["humectant", "skin-conditioning", "brightening"])
- `risk_flags`: structured safety flags (e.g., ["allergen:fragrance", "comedogenic:moderate"])

**Example ingredient record:**

```json
{
  "ingredient_id": "ing_042",
  "inci_name": "Niacinamidum",
  "common_name": "Niacinamide",
  "functions": ["humectant", "brightening", "pore-minimizing"],
  "risk_flags": [],
  "compatible_skin_types": ["dry", "oily", "combination", "sensitive"]
}
```

---

### 3. User Profile Intelligence

Structured representation of each user's attributes, behavioral signals, and consent state.

**Profile data is categorized by origin:**

| Origin     | Description                                                     | Editability                                          |
| ---------- | --------------------------------------------------------------- | ---------------------------------------------------- |
| `DECLARED` | Explicitly entered by user during onboarding or profile editing | User-editable at any time                            |
| `INFERRED` | Derived from behavioral signals by Aura                         | User-visible and overridable                         |
| `IMPORTED` | Pulled from third-party integrations (e.g., wellness apps)      | Requires explicit per-scope consent; user can revoke |

**Key user profile fields:**

| Field                 | Origin              | Example                   |
| --------------------- | ------------------- | ------------------------- |
| `skin_type`           | Declared            | "dry"                     |
| `undertone`           | Declared            | "warm"                    |
| `skin_tone`           | Declared            | "medium"                  |
| `allergies`           | Declared            | ["fragrance", "lanolin"]  |
| `ethical_preferences` | Declared            | ["cruelty_free", "vegan"] |
| `style_archetype`     | Declared + Inferred | "Minimalist"              |
| `spending_preference` | Inferred / declared | { "min": 15, "max": 60 }  |

**Example user profile:**

```json
{
  "user_id": "usr_101",
  "skin_type": "dry",
  "undertone": "warm",
  "skin_tone": "medium",
  "allergies": ["fragrance"],
  "ethical_preferences": ["cruelty_free"],
  "style_archetype": "Minimalist"
}
```

**Consent records:** Every optional data integration (wellness app, receipt import) requires an explicit, scoped, revocable consent record stored alongside the user profile.

---

### 4. Recommendation Intelligence

Tracks every recommendation generated, the reasoning behind it, user feedback, and model version used.

**Key entities:**

| Entity               | Key Fields                                                                                         |
| -------------------- | -------------------------------------------------------------------------------------------------- |
| Recommendation       | `recommendation_id`, `user_id`, `product_id`, `score`, `confidence`, `model_version`, `created_at` |
| RecommendationReason | `recommendation_id`, `reason_code`, `weight`, `details`                                            |
| FeedbackEvent        | `user_id`, `product_id`, `recommendation_id`, `event_type`, `value`, `created_at`                  |

**Feedback event types:** `CLICK`, `SAVE`, `DISMISS`, `PURCHASE`, `RATING`, `HELPFUL`, `NOT_HELPFUL`

**Example recommendation record:**

```json
{
  "recommendation_id": "rec_202",
  "user_id": "usr_101",
  "product_id": "prd_001",
  "score": 0.91,
  "confidence": 0.84,
  "model_version": "ranker-v3",
  "reason_codes": ["SHADE_MATCH", "INGREDIENT_SAFE", "COMMUNITY_MATCH"],
  "explanation": "Matches your warm undertone. Fragrance-free and highly rated by users with dry skin."
}
```

---

## Storage Technology Map

| Data                                                 | Storage                        | Rationale                                    |
| ---------------------------------------------------- | ------------------------------ | -------------------------------------------- |
| Canonical product, user, and recommendation entities | PostgreSQL                     | Transactional integrity, relational querying |
| Vector embeddings (products, ingredients)            | pgvector                       | Semantic similarity search in-database       |
| Raw ingestion payloads                               | Object storage (S3-compatible) | Cost-efficient, append-only                  |
| Hot recommendation paths, session context            | Redis                          | Sub-millisecond read latency                 |
| ML training data snapshots                           | Object storage                 | Versioned dataset management                 |
| Audit log                                            | PostgreSQL (append-only table) | Queryable compliance records                 |

---

## Data Governance Principles

1. **Privacy by design:** Collect only what is needed. Infer only with consent. Retain only as long as necessary.
2. **Consent as a first-class record:** No personal data is processed without a valid, in-scope consent record.
3. **Data minimization:** Analytics streams receive tokenized user IDs, never raw PII.
4. **Auditability:** All profile changes and recommendation events are timestamped and immutable.
5. **User rights:** Self-serve account deletion triggers a full data purge workflow across all stores.
