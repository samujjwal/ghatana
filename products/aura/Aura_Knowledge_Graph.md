# Aura Knowledge Graph & Ontology Design

## Purpose

The Aura Knowledge Graph connects products, ingredients, users, and community insights into a unified semantic model. It enables the recommendation engine to reason across entities — not just match keywords — and surfaces relationships that individual products or reviews cannot express alone.

---

## Core Entities

| Entity             | Description                                                                       |
| ------------------ | --------------------------------------------------------------------------------- |
| **Product**        | A canonical product record: brand, category, name, ingredients, shades, pricing   |
| **Brand**          | A product manufacturer or vendor                                                  |
| **Ingredient**     | A cosmetic or formulation ingredient identified by INCI name                      |
| **ProductShade**   | A named color/finish variant of a product, mapped to the canonical shade ontology |
| **User**           | A registered Aura user                                                            |
| **UserProfile**    | The You Index: aggregated declared, inferred, and behavioral attributes           |
| **Review**         | A community or retailer review, with sentiment score and metadata                 |
| **Routine**        | A user-defined sequence of products applied in a specific order                   |
| **Recommendation** | A scored, explained suggestion linking a user to a product at a point in time     |

---

## Core Relationships

| Subject      | Relationship          | Object          | Notes                                          |
| ------------ | --------------------- | --------------- | ---------------------------------------------- |
| Product      | `contains`            | Ingredient      | Position-ordered; concentration when available |
| Product      | `belongs_to`          | Brand           | Many products → one brand                      |
| Product      | `has_shade`           | ProductShade    | One product → many shades                      |
| Product      | `similar_to`          | Product         | From Product Similarity Model                  |
| Ingredient   | `conflicts_with`      | Ingredient      | e.g., AHA + Retinol same routine               |
| Ingredient   | `compatible_with`     | SkinType        | Suitable pairings                              |
| Ingredient   | `treats`              | SkinConcern     | e.g., Niacinamide treats uneven tone           |
| Ingredient   | `causes_reaction_for` | SkinType        | Risk relationship                              |
| Ingredient   | `belongs_to_class`    | IngredientClass | e.g., AHA, BHA, Emollient, Humectant           |
| ProductShade | `maps_to`             | CanonicalShade  | Cross-brand shade normalization                |
| User         | `has_profile`         | UserProfile     | One-to-one                                     |
| User         | `purchased`           | Product         | Behavioral signal                              |
| User         | `saved`               | Product         | Behavioral signal                              |
| User         | `allergic_to`         | Ingredient      | Declared sensitivity                           |
| User         | `similar_to`          | User            | For community matching (twin-user discovery)   |
| Review       | `about`               | Product         | Source product reference                       |
| Routine      | `includes`            | Product         | Ordered product sequence                       |

---

## Example Graph Traversal

**Query: Find moisturizers that are safe for a user with dry skin and fragrance allergy.**

```
User → allergic_to → Ingredient("Fragrance")

Product(category=moisturizer)
  → contains → Ingredient
  → Ingredient NOT IN [user allergens]
  → Ingredient compatible_with → SkinType("Dry")

Result: filtered + scored moisturizers safe for this user
```

**Query: Why is this product recommended?**

```
Product → contains → Hyaluronic Acid
Hyaluronic Acid → compatible_with → SkinType("Dry")
User → has_profile → skinType: "Dry"
→ Reason: "Contains Hyaluronic Acid, highly compatible with dry skin"
```

---

## Ontology Extension Points

The knowledge graph is designed to grow incrementally:

- **Phase 1:** Product, Brand, Ingredient, ProductShade, User, Recommendation
- **Phase 2:** Review, IngredientClass, SkinConcern relationships
- **Phase 3:** Routine, wardrobe items, wellness signals
- **Phase 4:** Twin-user relationships, community cluster entities

---

## Storage

The knowledge graph is stored relationally in PostgreSQL (normalized entity tables) with vector embeddings in pgvector for semantic similarity retrieval. Ingredient and product relationships are query-time joins rather than a separate graph database — this keeps the stack simple at early scale. A dedicated graph database (e.g., Neo4j) can be evaluated if traversal complexity warrants it in later phases.
