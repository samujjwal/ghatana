# Aura AI/ML and Data Operating Model

Version: 1.0
Date: March 2026

## Purpose

This document defines how Aura should use data and AI/ML to improve consumer outcomes without
over-engineering the stack or under-engineering trust, safety, and data quality.

It complements:

- `Aura_Master_Platform_Specification.md` for governing product and platform decisions
- `Aura_Data_Architecture.md` for logical data domains
- `Aura_AI_Model_Training_Pipeline.md` for training lifecycle details

If there is a conflict, the Master spec wins.

The central rule is: **models are useful only when they reduce consumer mistakes and uncertainty.
If they cannot do that reliably, Aura should fall back, ask for more input, or abstain.**

---

## 1. Operating Principles

1. **Data quality before model complexity:** Better canonical data usually beats a more complex model on noisy data.
2. **Rules first for high-risk decisions:** Ingredient safety and consent enforcement must never depend only on probabilistic output.
3. **Hybrid recommendation stack:** Use deterministic retrieval and filtering first, then ML reranking where labeled signal exists.
4. **Confidence-gated behavior:** Low confidence changes the UX and the decision path; it is not just a score hidden in the logs.
5. **Human review where harm is plausible:** Severe safety signals, ontology conflicts, and repeated model failures must enter review queues.
6. **Smallest viable runtime surface:** Keep a small number of deployables and avoid a service-per-model architecture until evidence justifies it.
7. **Deletion and consent must propagate:** Future training data snapshots must honor revocations and deletion requests.

---

## 2. Data Source Hierarchy

Aura should not treat all data as equally trustworthy.

| Tier | Source Type | Examples | Allowed Use | Limits |
| ---- | ----------- | -------- | ----------- | ------ |
| `T0` | Authoritative structured data | brand feeds, retailer catalog, vetted ingredient DBs, expert labels | safety rules, canonical entities, hard constraints | still validate for freshness and completeness |
| `T1` | High-trust derived data | normalized outcomes, QA-reviewed mappings, validated community aggregates | ranking features, confidence, cohort insights | requires provenance and reviewable lineage |
| `T2` | Lower-trust unstructured data | reviews, forums, creator content, social chatter | warning mining, sentiment, hypothesis generation | cannot override hard safety rules by itself |
| `T3` | User-specific optional imports | receipts, purchase history imports, selfie analysis, bio/wearable data | personalization and context enrichment | requires scoped consent and clear user value |

**Policy:** High-risk conclusions should lean on `T0` and validated `T1` evidence. `T2` should enrich, not govern.

---

## 3. Core Data Products

Aura should manage a small number of explicit data products, each with ownership and quality gates.

| Data Product | Contents | Primary Owner | Main Uses |
| ------------ | -------- | ------------- | --------- |
| Canonical Catalog | products, brands, shades, ingredient lists, source provenance | catalog/data ops | retrieval, compare, safety, shade |
| User Intelligence Snapshot | declared, inferred, imported, behavioral, consented attributes | profile/product | personalization, confidence |
| Recommendation Ledger | recommendation outputs, evidence, confidence, model version, profile snapshot | recommendation/product | auditability, learning, replay |
| Outcome Dataset | shade outcomes, adverse reactions, returns, keep/not-keep, regret | product + safety ops | ranking labels, rule updates, quality measurement |
| Golden Evaluation Sets | curated examples for shade, safety, ranking, sentiment, trust | ML + domain experts | offline evaluation and regressions |

---

## 4. Data Quality Service Levels

| Quality Area | Minimum Rule | If Rule Fails |
| ------------ | ------------ | ------------- |
| Ingredient completeness | Do not show strong safety-positive claims for products with incomplete or unparseable INCI data | lower confidence, show missing-data flag, suppress strong recommendation |
| Shade normalization | Supported complexion items need canonical depth and undertone mapping before shade-match claims | allow browsing, suppress strong shade-match positioning |
| Price and availability freshness | Commerce-facing data should meet freshness targets defined per source | label stale data, lower trust score, rerank or suppress buy CTA |
| Provenance coverage | Every recommendation reason must be traceable to facts, learned signals, or validated aggregates | do not surface unsupported reason |
| Outcome linkage | Post-use outcomes must connect back to the recommendation, user context, and product version when possible | treat as weaker signal and hold out from automated global updates |

---

## 5. Label Strategy

Aura's most valuable labels are delayed outcome labels, not just clicks.

| Label Type | How It Is Collected | Delay | Review Path | Primary Use |
| ---------- | ------------------- | ----- | ----------- | ----------- |
| Shade satisfaction / mismatch | explicit post-use prompt, return reason, support report | days to weeks | shade reviewer for repeated failures | shade model and catalog mapping |
| Adverse reaction | explicit report with severity and timing | hours to days | safety review queue | safety rules, risk scoring, suppression |
| Keep / not keep / return | explicit prompt, linked order or user report | days to weeks | product ops for anomalies | ranking outcome quality |
| Helpful / not helpful | inline explanation feedback | immediate | sampled QA review | explanation quality and ranking |
| Save / shortlist / compare | behavioral interaction | immediate | no manual review by default | intent and preference learning |
| Review warning labels | human-labeled review segments | batch | annotation QA | sentiment and warning extraction |

**Rule:** Behavioral labels are useful but insufficient. Outcome labels should be treated as the
highest-value supervision for consumer-facing ranking quality.

---

## 6. Model Strategy by Capability

| Capability | Baseline Approach | ML Upgrade Path | Abstain / Fallback Rule |
| ---------- | ----------------- | --------------- | ----------------------- |
| Ingredient safety | deterministic rules + knowledge graph + expert overrides | classifier assists warning discovery and soft-risk scoring | if ingredient data is incomplete, do not claim product is safe |
| Shade matching | ontology anchors + nearest-neighbor similarity + expert mappings | embedding model after enough validated shade outcomes | if anchor quality is weak, ask for more info or avoid single-best recommendation |
| Recommendation ranking | deterministic candidate generation + rules + weighted scoring | gradient-boosted ranker first, deep retrieval later | if profile or data completeness is weak, fall back to deterministic ranking |
| Review understanding | keyword/rule seed set + curated taxonomy | transformer classifier with human QA | do not use raw sentiment alone to justify safety claims |
| Preference learning | explicit profile + recent outcomes + saves/dismissals | lightweight personalized rank adjustments | behavioral learning cannot override hard exclusions |

---

## 7. Solving the Hard AI/ML and Data Problems

### 7.1 Cold Start

**Problem:** New users and new products have little or no interaction data.

**Approach:**

1. Start with declared profile fields and deterministic filters.
2. Use high-trust catalog features before collaborative or deep-personalization features.
3. Ask only the questions that materially improve the immediate answer.
4. Use curated popular-safe defaults rather than fake personalization.
5. Keep a deterministic fallback path for unsupported or sparse cases.

### 7.2 Sparse and Delayed Labels

**Problem:** The most valuable labels arrive after purchase or use.

**Approach:**

1. Design outcome prompts into the product, not as an afterthought.
2. Link outcomes back to the recommendation and product version.
3. Weight post-use labels more heavily than shallow engagement labels.
4. Use cohort-level learning only after minimum support thresholds are met.
5. Keep exploration controlled so Aura can learn without causing avoidable harm.

### 7.3 Noisy and Conflicting Source Data

**Problem:** Ingredient lists, shade names, and product metadata often disagree across sources.

**Approach:**

1. Preserve raw source snapshots and provenance.
2. Compute source trust and completeness scores.
3. Resolve deterministic conflicts through source ranking and review queues.
4. Never let lower-trust community or marketing copy override authoritative safety facts.
5. Lower confidence when normalization fails.

### 7.4 Feedback Loop Bias

**Problem:** If Aura learns only from clicks and top-ranked items, it can reinforce its own blind spots.

**Approach:**

1. Optimize against outcome and trust metrics, not only engagement.
2. Use exploration budgets carefully for low-risk recommendation surfaces.
3. Audit performance by skin tone depth, skin type, price sensitivity, and other relevant cohorts.
4. Require fairness checks before deployment and after rollout.
5. Review repeated failures by cohort as product defects, not just model metrics.

### 7.5 Catalog Churn and Drift

**Problem:** Product lines, prices, availability, and consumer preferences change quickly.

**Approach:**

1. Run freshness checks on catalog and commerce data.
2. Trigger shade and ranking review when catalog deltas exceed thresholds.
3. Detect feature drift and score drift continuously.
4. Keep model retraining tied to drift and label availability, not only calendar cadence.

### 7.6 Privacy, Consent, and Deletion

**Problem:** Personalization data changes over time, and user rights must remain enforceable.

**Approach:**

1. Keep optional imports and high-sensitivity enrichments in separate, scoped datasets.
2. Exclude revoked or deleted user data from future training snapshots.
3. Retire affected models on normal cadence unless legal or safety obligations require accelerated retraining.
4. Keep recommendation ledgers and dataset hashes so training lineage remains auditable.
5. Minimize PII in analytics and training data by design.

---

## 8. Human-in-the-Loop System

| Review Queue | Typical Trigger | Action |
| ------------ | --------------- | ------ |
| Safety review | adverse reaction reports, dangerous ingredient conflict, severe warning cluster | validate signal, update rules, suppress unsafe items if needed |
| Shade review | repeated mismatch or anchor inconsistency | review ontology mapping, brand shade anchors, fallback behavior |
| Data conflict review | catalog disagreement, missing fields, stale commerce data | resolve source precedence, correct canonical record |
| Model QA review | fairness alert, confidence overstatement, explanation mismatch | hold rollout, patch logic, adjust thresholds |

Human review should feed both product fixes and future training data, not operate as an isolated support process.

---

## 9. Release Gates and Runtime Controls

No model or major data pipeline change should ship without:

1. Passing offline evaluation on golden datasets.
2. Passing fairness thresholds on defined cohorts.
3. Passing shadow or challenger validation against the current champion.
4. Showing no regression on median time-to-decision, shade-miss rate, adverse reaction rate, or return reduction.
5. Producing reasons and evidence that still satisfy the user-facing explanation contract.
6. Having a rollback path and deterministic fallback ready.

Runtime controls should include:

- low-confidence trust flags
- unsupported-category fallbacks
- hard safety suppression
- stale-data suppression or reranking
- audit logging for recommendation outputs and model versions

---

## 10. Build Sequence Without Over-Engineering

### Phase 1

- PostgreSQL + pgvector + object storage snapshots
- deterministic candidate generation and rules
- weighted ranking plus lightweight ML where signal exists
- manual review queues for high-risk failures
- no feature store, no separate vector database, no service-per-model topology

### Phase 2

Introduce only when evidence supports it:

- deeper personalized reranking
- controlled event-bus fan-out
- additional model-serving isolation for materially different workloads
- more automated annotation and evaluation tooling

### Phase 3

Adopt only when scale or organizational complexity requires it:

- feature store
- dedicated vector infrastructure
- multiple model-serving deployables
- specialized training orchestration beyond snapshot-based pipelines

---

## 11. Required Artifacts for Every Model Family

Each model family should have:

1. A dataset card describing sources, filters, cohort coverage, and exclusions.
2. A model card describing intended use, failure modes, fairness checks, and confidence behavior.
3. A reproducible training snapshot hash.
4. An evaluation report tied to consumer outcome metrics.
5. A rollback and fallback plan.

If these artifacts do not exist, the model is not production-ready.
