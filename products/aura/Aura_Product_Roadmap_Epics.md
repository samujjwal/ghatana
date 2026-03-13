# Aura Product Roadmap & Engineering Epic Breakdown

## Phase 1 — Curated Me (Months 1–3)

**Goal:** Ship a compelling beauty intelligence MVP that demonstrates the core Aura value proposition: personalized, explainable product recommendations for beauty.

### Engineering Epics

| Epic                      | Description                                                                                     |
| ------------------------- | ----------------------------------------------------------------------------------------------- |
| Product Ingestion System  | Source adapters, crawl scheduler, parsing and enrichment workers, deduplication service         |
| User Onboarding & Profile | Profile onboarding questionnaire, You Index v1 (declared data), profile editing                 |
| Ingredient Analyzer       | Ingredient safety engine, allergen detection, ingredient conflict detection                     |
| Shade Matching v1         | Canonical shade ontology, cross-brand shade mapping, shade compatibility scoring                |
| Recommendation Feed       | Candidate generation, rules filtering, baseline ranking, recommendation cards with reason codes |
| Bookmarking System        | Save/unsave product flows, saved items shelf                                                    |
| Consent Center v1         | Consent record management, per-scope consent UI                                                 |

### Phase 1 Success Metrics

| Metric                          | Target            |
| ------------------------------- | ----------------- |
| Profile completion rate         | ≥ 60%             |
| Recommendation CTR              | ≥ 12%             |
| Saves per active user per week  | ≥ 3               |
| Ingredient safety flag accuracy | ≥ 95% (QA sample) |
| Internal alpha NPS              | > 50              |

---

## Phase 2 — Aura Insights (Months 4–6)

**Goal:** Improve personalization quality and launch the first ML-powered recommendation improvements alongside explainable AI features.

### Engineering Epics

| Epic                                 | Description                                                                              |
| ------------------------------------ | ---------------------------------------------------------------------------------------- |
| AI Skin Analysis                     | Selfie-based undertone inference (opt-in); inferred attribute written to You Index       |
| Review Sentiment Engine              | Ingest and score community reviews; profile-filtered sentiment per product               |
| Product Similarity Engine            | Ingredient-based dupe detection, alternative recommendation generation                   |
| Explainable Recommendation Interface | Enhanced reason cards, confidence display, evidence links, explanation feedback          |
| ML Ranking v1                        | Gradient boosted ranker trained on interaction data; A/B tested against baseline formula |
| Experimentation Framework            | A/B test infrastructure for ranking weights, UI variants, and feature flags              |
| Compare Products Screen              | Side-by-side product comparison with per-dimension scoring                               |

### Phase 2 Success Metrics

| Metric                             | Target                                  |
| ---------------------------------- | --------------------------------------- |
| Recommendation engagement rate     | ≥ 15% improvement over Phase 1 baseline |
| Conversion (recommendation → save) | ≥ 20%                                   |
| Explanation helpfulness rating     | ≥ 4.0 / 5.0                             |
| 30-day user retention              | ≥ 35%                                   |
| ML ranker NDCG@10                  | Improvement vs. rule-based baseline     |

---

## Phase 3 — Bio Sync (Months 7–9)

**Goal:** Deliver context-aware personalization through optional wellness and lifestyle integrations.

### Engineering Epics

| Epic                         | Description                                                                               |
| ---------------------------- | ----------------------------------------------------------------------------------------- |
| Wearable Health Integrations | Opt-in connections: Apple Health, sleep trackers; context signals feed the Context Engine |
| Mood-Based Styling           | Mood input UI; context-aware recommendation adjustments                                   |
| Routine Builder              | AI-generated morning/evening routine recommendations; conflict and redundancy detection   |
| Advanced Context Engine      | Seasonal, weather, event context signals in recommendation pipeline                       |

### Phase 3 Success Metrics

| Metric                                  | Target                              |
| --------------------------------------- | ----------------------------------- |
| Integration connections per active user | ≥ 0.5 (optional; tracks adoption)   |
| Routine builder adoption                | ≥ 20% of MAU                        |
| Context-adjusted recommendation CTR     | ≥ 5% lift over non-context baseline |
| 60-day retention                        | ≥ 30%                               |

---

## Phase 4 — Aura Collective (Months 10+)

**Goal:** Activate community intelligence at scale and launch brand analytics as a B2B revenue stream.

### Engineering Epics

| Epic                      | Description                                                                                                              |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Twin Network Discovery    | Identify and surface "users like you" based on profile similarity; weight their reviews and saves more heavily           |
| Verified Review System    | Identity-verified reviewer program; verified flag on reviews; elevated weight in sentiment scoring                       |
| Shared Routines           | Users can publish and discover routines; community curation                                                              |
| Brand Analytics Dashboard | Anonymized aggregate insights for brand partners: shade satisfaction, ingredient performance, sentiment trends by cohort |
| Creator Tools             | Aura embed links for creator content; shareable "ingredient check" and "shade match" cards                               |

### Phase 4 Success Metrics

| Metric                                    | Target                                                       |
| ----------------------------------------- | ------------------------------------------------------------ |
| Community contributions per MAU per month | ≥ 1 (review, save, or routine share)                         |
| Peer recommendation usage                 | ≥ 30% of MAU clicking "loved by users like you"              |
| Brand analytics ARR                       | Meaningful revenue contribution milestone                    |
| Verified review coverage                  | ≥ 20% of actioned products have at least one verified review |
