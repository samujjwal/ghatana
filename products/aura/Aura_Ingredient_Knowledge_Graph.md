# Aura Ingredient Knowledge Graph

## Purpose

The Ingredient Knowledge Graph models the relationships between cosmetic ingredients, skin types, skin concerns, and product formulations. It is the foundation of Aura's ingredient safety analysis, allergen detection, and "why this product is safe for you" explanations.

---

## Core Entities

| Entity              | Key Attributes                                                                                                  |
| ------------------- | --------------------------------------------------------------------------------------------------------------- |
| **Ingredient**      | INCI name (canonical), common name, CAS number, functions, risk flags, EU/US regulatory status                  |
| **IngredientClass** | Category grouping: AHA, BHA, Retinoid, Vitamin C, Emollient, Humectant, Occlusiv, Fragrance, Preservative, etc. |
| **SkinType**        | Dry, Oily, Combination, Normal, Sensitive, Acne-Prone                                                           |
| **SkinConcern**     | Hyperpigmentation, Acne, Fine Lines, Dehydration, Redness, Uneven Texture, Clogged Pores                        |
| **RiskFlag**        | Allergen, Irritant, Comedogenic, Endocrine Disruptor, UV Sensitizer                                             |
| **Product**         | Reference to the canonical product entity                                                                       |

---

## Core Relationships

| Relationship                                      | Description                                         | Example                                           |
| ------------------------------------------------- | --------------------------------------------------- | ------------------------------------------------- |
| `Ingredient → treats → SkinConcern`               | Ingredient has evidence of improving a concern      | Niacinamide → treats → Hyperpigmentation          |
| `Ingredient → compatible_with → SkinType`         | Ingredient is well-tolerated by a skin type         | Hyaluronic Acid → compatible_with → Dry           |
| `Ingredient → irritating_for → SkinType`          | Ingredient is frequently irritating for a skin type | High-% AHAs → irritating_for → Sensitive          |
| `Ingredient → conflicts_with → Ingredient`        | Two ingredients should not be used together         | AHA/BHA → conflicts_with → Retinol (same routine) |
| `Ingredient → synergizes_with → Ingredient`       | Two ingredients enhance each other's effect         | Vitamin C → synergizes_with → Vitamin E           |
| `Ingredient → belongs_to_class → IngredientClass` | Class membership for filtering                      | Glycolic Acid → belongs_to_class → AHA            |
| `Ingredient → has_risk_flag → RiskFlag`           | Known safety concern                                | Methylisothiazolinone → has_risk_flag → Allergen  |
| `Product → contains → Ingredient`                 | Product formulation membership                      | Foundation X → contains → Titanium Dioxide        |

---

## Example Graph Queries

### Query: Is this product safe for a user with fragrance sensitivity?

```
Product → contains → [Ingredient list]
For each Ingredient:
  Ingredient → has_risk_flag → Allergen   (type: Fragrance)
  User → allergic_to → FragranceIngredients?
→ If any match: ALLERGEN_ALERT
→ Reason: "Contains [ingredient name], a fragrance allergen you've flagged"
```

### Query: What ingredients are beneficial for dry skin?

```
Ingredient → compatible_with → SkinType("Dry")
Ingredient → treats → SkinConcern (Dehydration | Fine Lines)
→ Return: Hyaluronic Acid, Glycerin, Shea Butter, Ceramides, Squalane...
```

### Query: Does this moisturizer conflict with the user's serum?

```
OwnedSerum → contains → [IngredientA, IngredientB]
NewProduct → contains → [IngredientC, IngredientD]
Check: IngredientA → conflicts_with → any(IngredientC, D)
→ If conflict: DUPLICATE_ACTIVE or routine conflict flag
```

---

## Data Sources

| Source                        | Type                  | Notes                                   |
| ----------------------------- | --------------------- | --------------------------------------- |
| INCI database                 | Structured            | Primary canonical ingredient identifier |
| EWG Skin Deep                 | Safety ratings        | Risk flag sourcing                      |
| CosDNA                        | Community safety data | Supplementary risk signals              |
| EU Cosmetics Regulation Annex | Regulatory            | Prohibited and restricted substances    |
| Scientific literature         | Research              | Functions and synergy data (curated)    |

---

## Graph Maintenance

- Ingredient records are upserted during product ingestion. New ingredients trigger enrichment via the Enrichment Worker.
- Risk flags and compatibility relationships are bootstrapped from reference databases and maintained by the platform team.
- High-confidence community signals (large sample, consistent pattern) can strengthen or add compatibility/irritation relationships over time, with human review.
- All ingredient knowledge is versioned. Recommendation reason codes cite the version of the ingredient knowledge used.
