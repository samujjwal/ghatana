# Aura Product Roadmap & Engineering Epic Breakdown

Detailed `what`, `how`, `where`, and validation guidance for active delivery tasks lives in
`Aura_Task_Execution_Matrix.md` and `Aura_Long_Horizon_Task_Execution_Matrix.md`. No epic should
enter implementation until it is decomposed there.

## Phase 1 — Curated Me (Months 1–3)

**Goal:** Ship a compelling beauty intelligence MVP that demonstrates the core Aura value proposition: personalized, explainable product recommendations for beauty.

**Execution detail:** See `Aura_Task_Execution_Matrix.md` rows `S01-T01` through `S06-T05`.

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

| Metric                          | Target                                                               |
| ------------------------------- | -------------------------------------------------------------------- |
| Median time-to-decision         | ≤ 10 minutes for supported beauty journeys                           |
| Shade-miss rate                 | ≤ 15% of Aura-assisted supported complexion outcomes                 |
| Ingredient safety flag accuracy | ≥ 95% precision (QA sample)                                          |
| Adverse reaction triage SLA     | 100% of recommendation-attributed reports reviewed within 24 hours   |
| Return / regret reduction       | ≥ 15% lower than self-directed baseline once outcome volume is valid |

---

## Phase 2 — Aura Insights (Months 4–6)

**Goal:** Improve personalization quality, pilot opt-in AI skin analysis safely, and launch the invite beta with ML-assisted improvements.

**Execution detail:** See `Aura_Task_Execution_Matrix.md` rows `S07-T01` through `S12-T04`.

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

| Metric                             | Target                                       |
| ---------------------------------- | -------------------------------------------- |
| Median time-to-decision            | ≥ 20% improvement over Phase 1 baseline      |
| Recommendation-attributed returns  | Sustained reduction across skincare/makeup   |
| Explanation helpfulness rating     | ≥ 4.0 / 5.0                                  |
| Shade-miss rate                    | Improvement vs. Phase 1 baseline             |
| ML ranker NDCG@10                  | Improvement vs. rule-based baseline          |

---

## Phase 3 — Bio Sync (Months 7–9)

**Goal:** Deliver context-aware personalization through optional wellness and lifestyle integrations.

**Execution rule:** Decompose each Phase 3 epic into task rows in
`Aura_Long_Horizon_Task_Execution_Matrix.md` before implementation begins.

### Engineering Epics

| Epic                         | Description                                                                               |
| ---------------------------- | ----------------------------------------------------------------------------------------- |
| Wearable Health Integrations | Opt-in connections: Apple Health, sleep trackers; context signals feed the Context Engine |
| Mood-Based Styling           | Mood input UI; context-aware recommendation adjustments                                   |
| Routine Builder              | AI-generated morning/evening routine recommendations; conflict and redundancy detection   |
| Advanced Context Engine      | Seasonal, weather, event context signals in recommendation pipeline                       |

### Phase 3 Success Metrics

| Metric                                  | Target                                                |
| --------------------------------------- | ----------------------------------------------------- |
| Context-adjusted time-to-decision       | ≥ 10% faster than non-context baseline                |
| Routine conflict detection precision    | ≥ 95% on QA-reviewed routine checks                   |
| Prevented duplicate-active recommendations | Improvement vs. Phase 2 baseline                    |
| Recommendation-attributed return reduction | Maintained after optional context enrichment       |

---

## Phase 4 — Aura Collective (Months 10+)

**Goal:** Activate community intelligence at scale and launch brand analytics as a B2B revenue stream.

**Execution rule:** Decompose each Phase 4 epic into task rows in
`Aura_Long_Horizon_Task_Execution_Matrix.md` before implementation begins.

### Engineering Epics

| Epic                      | Description                                                                                                              |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Twin Network Discovery    | Identify and surface "users like you" based on profile similarity; weight their reviews and saves more heavily           |
| Verified Review System    | Identity-verified reviewer program; verified flag on reviews; elevated weight in sentiment scoring                       |
| Shared Routines           | Users can publish and discover routines; community curation                                                              |
| Brand Analytics Dashboard | Anonymized aggregate insights for brand partners: shade satisfaction, ingredient performance, sentiment trends by cohort |
| Creator Tools             | Aura embed links for creator content; shareable "ingredient check" and "shade match" cards                               |

### Phase 4 Success Metrics

| Metric                                    | Target                                                          |
| ----------------------------------------- | --------------------------------------------------------------- |
| Verified review outcome usefulness        | Majority of users rate outcome-backed peer signals as helpful    |
| Community warning precision               | High-confidence warnings validated in moderation / QA sampling   |
| Recommendation-attributed return reduction | Improved further with peer-signal enrichment                    |
| Verified review coverage                  | ≥ 20% of actioned products have at least one verified review    |
