# Aura Product Similarity Model

## Objective

Identify similar products across brands to power dupe detection, alternative recommendations, "compare products" features, and supply-chain resilience (recommend alternatives when a product is out of stock or discontinued).

---

## Similarity Dimensions

Products are compared across five dimensions:

| Dimension                         | Weight | Description                                                                                                                 |
| --------------------------------- | ------ | --------------------------------------------------------------------------------------------------------------------------- |
| **Ingredient Overlap**            | 0.40   | Jaccard similarity of active ingredient sets; weighted by ingredient position (higher-position ingredients contribute more) |
| **Shade Similarity**              | 0.20   | Canonical shade distance: undertone alignment + depth proximity (0–1 scale); 0 for non-shade-bearing categories             |
| **Community Sentiment Alignment** | 0.20   | Cosine similarity between sentiment score distributions across skin type cohorts                                            |
| **Price Proximity**               | 0.10   | Inverse of normalized price delta within category                                                                           |
| **Category & Subcategory Match**  | 0.10   | Exact match bonus; subcategory partial credit                                                                               |

**Similarity Score formula:**

```
similarity = 0.40 × ingredient_similarity
           + 0.20 × shade_similarity
           + 0.20 × sentiment_similarity
           + 0.10 × price_similarity
           + 0.10 × category_match
```

Scores range 0.0–1.0. Products with `similarity ≥ 0.75` are considered strong dupes. Products with `0.50 ≤ similarity < 0.75` are "alternatives."

---

## Ingredient Similarity Calculation

Ingredient similarity uses a **weighted Jaccard index** that respects ingredient ordering:

```
ingredient_similarity = Σ(weight_i × match_i) / Σ(weight_i × (match_i + non_match_i))
```

Where `weight_i` decreases logarithmically with ingredient list position (first ingredient is heaviest). This ensures that key actives matter more than low-concentration additives.

---

## Use Cases

| Use Case                                | Details                                                                                         |
| --------------------------------------- | ----------------------------------------------------------------------------------------------- |
| **Dupe detection**                      | Surface budget alternatives to premium products with similarity ≥ 0.75                          |
| **Alternative recommendations**         | When a preferred product is discontinued or out of stock, suggest similar in-stock alternatives |
| **Compare products**                    | Drive the "compare" feature with structured similarity dimensions                               |
| **"You already own something similar"** | Generate `OWNS_SIMILAR` reason code to prevent redundant purchases                              |
| **Catalog deduplication**               | Flag potential duplicate product records created from different sources                         |

---

## Model Maintenance

- Similarity scores are computed offline as a batch job when new products are ingested or existing products are enriched.
- The similarity graph is stored as indexed pairwise records in PostgreSQL for fast retrieval.
- Shade similarity is skipped (set to 0) for non-shade-bearing categories (serum, toner, sunscreen, etc.).
- Weights are tunable via configuration. A/B tested against user engagement on the "compare" and "alternatives" surfaces.
- Community feedback ("this isn't really a dupe") is collected and fed back into the weight calibration pipeline.
