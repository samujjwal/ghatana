# Aura AI Model Training Pipeline

## Objectives

Train and continuously improve models for:

- Shade matching (undertone and depth compatibility)
- Ingredient compatibility and allergen risk scoring
- Recommendation ranking (personalized scoring)
- Review sentiment classification (benefit extraction, warning detection)

---

## Pipeline Stages

### 1. Data Collection

| Source                   | Data Type    | Notes                                       |
| ------------------------ | ------------ | ------------------------------------------- |
| Product catalog metadata | Structured   | Ingredients, shades, categories, price      |
| User interactions        | Behavioral   | Clicks, saves, dismissals, purchases        |
| Community review corpora | Unstructured | Ratings, titles, body text                  |
| Product outcomes         | Labeled      | Shade feedback, adverse reactions, returns, keep/not-keep where available |
| Expert annotation        | Labeled      | Ingredient function and risk flags          |

### 2. Data Validation

- Schema validation and null handling
- Duplicate and near-duplicate detection
- Label integrity checks (e.g., verify shade-undertone mapping consistency)
- Distribution drift checks vs. previous training snapshot
- PII audit — ensure no user-identifiable data in model training sets

### 3. Feature Engineering

| Feature Group                | Description                                                                |
| ---------------------------- | -------------------------------------------------------------------------- |
| Product embeddings           | Dense vector representations of product name, description, and ingredients |
| User preference vectors      | Aggregated behavioral and declared profile signals                         |
| Ingredient conflict features | Hard-conflict flags and soft-risk scores by ingredient pair and profile    |
| Shade compatibility features | Undertone match score, depth delta, finish preference alignment            |
| Price sensitivity features   | Price fit score relative to declared and inferred spending preference      |
| Source trust features        | Data freshness, source reliability, completeness score                     |
| Community sentiment features | Sentiment score, skin-type-stratified rating, warning signal presence      |

### 4. Training

| Model                    | Approach                                | Training Signal                                                  |
| ------------------------ | --------------------------------------- | ---------------------------------------------------------------- |
| Shade Matcher            | Embeddings + cosine similarity          | Shade satisfaction feedback, expert shade mapping labels         |
| Ingredient Safety Scorer | Rule augmented classifier               | Expert labels, community warning signals, dermatology references |
| Recommendation Ranker    | Gradient-boosted trees → deep retrieval | Save rate, CTR, conversion lift, explicit "helpful" ratings      |
| Sentiment Classifier     | Fine-tuned transformer (BERT-class)     | Human-labeled review segments                                    |

Training approach:

- Start with baseline deterministic rules + gradient boosted ranking
- Introduce deep retrieval models (two-tower) when labeled data volume warrants
- Maintain a deterministic fallback path for cold-start and low-confidence cases

### 5. Evaluation

**Offline Ranking Metrics**

| Metric      | Target |
| ----------- | ------ |
| NDCG@10     | ≥ 0.75 |
| MAP@10      | ≥ 0.70 |
| Precision@5 | ≥ 0.65 |

**Business Metrics** _(measured via A/B test or shadow evaluation)_

| Metric                       | Target                                              |
| ---------------------------- | --------------------------------------------------- |
| Median time-to-decision      | Improvement vs. previous model version              |
| Shade-miss rate              | No regression; target improvement vs. baseline      |
| Adverse reaction rate        | No regression; target improvement vs. baseline      |
| Return reduction             | Positive directional improvement                    |
| Explanation helpfulness rate | ≥ 60% of users who open explanation rate it helpful |

**Fairness Checks**

All models must pass fairness evaluation before deployment:

| Cohort                                   | Check                                                                |
| ---------------------------------------- | -------------------------------------------------------------------- |
| Skin tone depth (light vs. deep)         | NDCG gap < 5% between depth cohorts                                  |
| Skin type (oily vs. dry vs. sensitive)   | No cohort systematically under-served by ranking                     |
| Ethical preference (vegan, cruelty-free) | Preference filters apply correctly at all confidence levels          |
| Price band                               | Low-price-preference users receive equivalent recommendation quality |

### 6. Deployment

| Stage                 | Description                                                                     |
| --------------------- | ------------------------------------------------------------------------------- |
| Model registry        | All trained models versioned, tagged with training data snapshot hash           |
| Champion / challenger | New model serves a traffic slice alongside the current champion                 |
| Shadow evaluation     | New model scores in parallel with no user-visible effect for initial validation |
| Canary release        | Gradual rollout: 1% → 5% → 20% → 100%, with automated rollback on regression    |

### 7. Monitoring

| Signal                   | Alert Condition                                                    |
| ------------------------ | ------------------------------------------------------------------ |
| Feature drift            | Input distribution shifts ≥ 15% vs. training baseline              |
| Score distribution drift | Recommendation score distribution shifts ≥ 10%                     |
| Recommendation diversity | Category or brand concentration increases beyond defined threshold |
| Fairness drift           | Cohort NDCG gap widens beyond tolerance (see Evaluation above)     |
| Explanation quality      | Helpfulness rate drops below 50% for 2 consecutive days            |

---

## Retraining Cadence

| Model                    | Retraining Trigger                                                        |
| ------------------------ | ------------------------------------------------------------------------- |
| Recommendation Ranker    | Weekly batch retraining; drift-triggered emergency retraining             |
| Sentiment Classifier     | Monthly batch retraining + manual review when new warning patterns emerge |
| Shade Matcher            | Quarterly retraining + triggered by new brand catalog ingestion           |
| Ingredient Safety Scorer | Triggered by expert label updates or new ingredient database releases     |
