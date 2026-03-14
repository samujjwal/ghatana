# Aura Test Cases 02: Recommendation, AI, and Knowledge Models

Version: 1.0
Date: March 13, 2026

## Scope

This suite covers:

- candidate generation and rules filtering
- feature construction and ranking
- explanation and confidence generation
- Personal Intelligence Engine behavior
- shade ontology, ingredient graph, style taxonomy, and product similarity
- community intelligence and agent orchestration
- fairness and training-signal integrity

---

## A. Candidate Generation and Rules Filtering

### AURA-AIK-001 Category filter returns only supported category candidates
Level: Unit
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_Master_Platform_Specification.md`
Preconditions: Mixed-category catalog fixture.
Steps:
1. Request recommendations for one category.
2. Inspect candidate set before ranking.
Expected:
- All candidates belong to requested category or valid subcategory.
- No cross-category leakage occurs.

### AURA-AIK-002 Availability filter excludes products with no in-stock sources
Level: Integration
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_AI_Agent_Architecture.md`
Preconditions: Candidate set includes in-stock and out-of-stock source records.
Steps:
1. Run candidate generation.
2. Inspect pre-ranking set.
Expected:
- Out-of-stock-only products are excluded.
- Mixed-availability products retain only valid merchant sources.

### AURA-AIK-003 Hard allergen exclusion removes unsafe candidates
Level: Unit
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_Ingredient_Knowledge_Graph.md`
Preconditions: User with declared allergen and matching product ingredient.
Steps:
1. Run rules filtering.
Expected:
- Candidate is excluded.
- `ALLERGEN_ALERT` reason is retained for warnings/history.

### AURA-AIK-004 Ethical filter violation is excluded silently from ranking set
Level: Unit
Priority: P1
Source Docs: `Aura_Recommendation_Algorithms.md`
Preconditions: User requires cruelty-free or vegan; candidate violates it.
Steps:
1. Run rules filtering.
Expected:
- Violating candidate is removed from rankable set.
- No false-positive `ETHICAL_MATCH` appears.

### AURA-AIK-005 Over-budget product is soft-excluded rather than hard-excluded
Level: Unit
Priority: P1
Source Docs: `Aura_Recommendation_Algorithms.md`
Preconditions: Candidate exceeds spending preference.
Steps:
1. Run rules filtering and ranking.
2. Request over-budget items when explicitly asked.
Expected:
- Candidate is deprioritized by default.
- Candidate can be surfaced with over-budget indication when user asks.

### AURA-AIK-006 Exact owned product is suppressed into already-own shelf
Level: Integration
Priority: P1
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_Knowledge_Graph.md`
Preconditions: User owns exact product.
Steps:
1. Request recommendations in same category.
Expected:
- Exact owned product is absent from default recommendation list.
- Ownership is still available through dedicated owned-state logic.

### AURA-AIK-007 Duplicate active ingredient produces soft penalty, not false exclusion
Level: Unit
Priority: P1
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_Ingredient_Knowledge_Graph.md`
Preconditions: User owns product with overlapping active ingredient.
Steps:
1. Rank candidate with duplicate active.
Expected:
- Candidate is not hard-excluded unless independently unsafe.
- `DUPLICATE_ACTIVE` reason is attached.

### AURA-AIK-008 Candidate generation target range is respected
Level: Integration
Priority: P2
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_AI_Agent_Architecture.md`
Preconditions: Large supported catalog.
Steps:
1. Request recommendations for broad query.
2. Inspect candidate list count.
Expected:
- Candidate set remains within documented pre-ranking size target or justified limit.

---

## B. Feature Construction and Ranking

### AURA-AIK-009 Compatibility score combines shade and ingredient safety branches correctly
Level: Unit
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_AI_Engine_Design.md`
Preconditions: One fully compatible candidate, one shade-only candidate, one safety-only candidate.
Steps:
1. Compute feature set for each candidate.
Expected:
- Compatibility score reflects both shade and safety inputs.
- Missing one dimension lowers score predictably.

### AURA-AIK-010 Price fit score is highest near user preference midpoint
Level: Unit
Priority: P1
Source Docs: `Aura_Recommendation_Algorithms.md`
Preconditions: Candidates at low, midpoint, and high edge of preference band.
Steps:
1. Compute `price_fit_score`.
Expected:
- Midpoint-adjacent product scores highest.
- Distance from midpoint reduces score monotonically.

### AURA-AIK-011 Source trust score drops when ingredient or shade metadata is incomplete
Level: Unit
Priority: P1
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_AI_Engine_Design.md`
Preconditions: Candidates with full and partial metadata.
Steps:
1. Build features.
Expected:
- Partial ingredient or shade metadata lowers source trust.
- Low-trust candidates affect confidence or trust flags downstream.

### AURA-AIK-012 Baseline ranking formula matches documented weights
Level: Unit
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_Master_Platform_Specification.md`
Preconditions: Deterministic feature vector fixture.
Steps:
1. Calculate expected score manually.
2. Compare to implementation output.
Expected:
- Score equals `0.45 compatibility + 0.20 sentiment + 0.20 popularity + 0.15 price_fit`.

### AURA-AIK-013 Diversity reranking prevents brand domination
Level: Unit
Priority: P1
Source Docs: `Aura_Recommendation_Algorithms.md`
Preconditions: Top 10 initial rank list dominated by one brand.
Steps:
1. Run diversity reranker.
Expected:
- Final top set contains at least documented diversity spread.
- Reordering does not promote unsafe candidates.

### AURA-AIK-014 Novelty boost does not outrank materially better known-safe candidate
Level: Unit
Priority: P1
Source Docs: `Aura_Recommendation_Algorithms.md`
Preconditions: One familiar candidate with stronger compatibility, one novel weaker candidate.
Steps:
1. Apply novelty boost.
Expected:
- Novelty never overwhelms meaningful compatibility and safety advantages.

### AURA-AIK-015 Safety promotion breaks ties in favor of safer products
Level: Unit
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`
Preconditions: Two near-equal candidates, only one has both `INGREDIENT_SAFE` and `SHADE_MATCH`.
Steps:
1. Rank candidates.
Expected:
- Safer candidate ranks above unsafe or weaker-trust alternative.

### AURA-AIK-016 Ranking never surfaces allergen-excluded item after downstream reranking
Level: Integration
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_AI_Agent_Architecture.md`
Preconditions: One excluded allergen candidate, multiple safe candidates.
Steps:
1. Run full pipeline including post-ranking adjustments.
Expected:
- Excluded item never re-enters final list.

---

## C. Explanation and Confidence

### AURA-AIK-017 Reason codes are grounded in actual pipeline evidence only
Level: Unit
Priority: P0
Source Docs: `Aura_AI_Engine_Design.md`, `Aura_Recommendation_Algorithms.md`
Preconditions: Candidate with limited reasons.
Steps:
1. Generate explanation payload.
Expected:
- Only computed reason codes appear.
- No hallucinated ingredient, shade, or community claims are present.

### AURA-AIK-018 Explanation evidence references match structured source facts
Level: Contract
Priority: P0
Source Docs: `Aura_API_Contracts.md`, `Aura_AI_Engine_Design.md`
Preconditions: Recommendation with evidence payload.
Steps:
1. Compare explanation text to evidence list.
Expected:
- Evidence items are sufficient to justify explanation claims.
- Missing evidence causes case failure.

### AURA-AIK-019 Low-confidence recommendation includes `LOW_CONFIDENCE` trust flag
Level: Unit
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`
Preconditions: Low completeness or partial metadata fixture.
Steps:
1. Generate recommendation.
Expected:
- Confidence score is low.
- Low-confidence trust flag is attached.

### AURA-AIK-020 Low shade confidence is distinct from generic low confidence
Level: Unit
Priority: P1
Source Docs: `Aura_Shade_Color_Ontology.md`, `Aura_API_Contracts.md`
Preconditions: Incomplete undertone metadata but otherwise high-quality product.
Steps:
1. Generate shade suggestion.
Expected:
- `LOW_SHADE_CONFIDENCE` appears.
- System does not incorrectly downgrade unrelated safety or sentiment signals.

### AURA-AIK-021 Community caution attaches cautionary trust flag without fabricating safety claims
Level: Integration
Priority: P1
Source Docs: `Aura_AI_Agent_Architecture.md`, `Aura_AI_Engine_Design.md`
Preconditions: Review corpus contains repeated cautionary theme but no hard allergen violation.
Steps:
1. Run community enrichment and explanation generation.
Expected:
- Caution appears as community trust flag or `COMMUNITY_FLAG`.
- System does not convert community caution into unsupported medical exclusion.

### AURA-AIK-022 Confidence factors degrade independently
Level: Unit
Priority: P1
Source Docs: `Aura_Personal_Intelligence_Engine_Spec.md`
Preconditions: Multiple fixtures with one missing factor each.
Steps:
1. Remove profile completeness, then ingredient completeness, then community volume, then behavioral history.
Expected:
- Each missing factor lowers confidence for documented reason.
- Confidence rationale remains explainable.

---

## D. Personal Intelligence Engine and Learning

### AURA-AIK-023 Context multipliers never override an unsafe product into final results
Level: Unit
Priority: P0
Source Docs: `Aura_Personal_Intelligence_Engine_Spec.md`
Preconditions: Unsafe candidate and strong contextual match.
Steps:
1. Apply context weighting.
Expected:
- Unsafe candidate remains excluded or penalized.
- Context can only adjust safe base candidates.

### AURA-AIK-024 Session-level dismissals take effect before weekly retraining
Level: Integration
Priority: P1
Source Docs: `Aura_Personal_Intelligence_Engine_Spec.md`
Preconditions: User repeatedly dismisses same product pattern.
Steps:
1. Dismiss products in a session.
2. Request recommendations again before retraining.
Expected:
- Near-term recommendations reflect updated negative preference immediately.

### AURA-AIK-025 Purchase is weighted stronger than click or save
Level: Unit
Priority: P1
Source Docs: `Aura_Personal_Intelligence_Engine_Spec.md`, `Aura_AI_Engine_Design.md`
Preconditions: Equal candidates with different interaction histories.
Steps:
1. Update preference model with click-only, save-only, and purchase histories.
Expected:
- Purchase has strongest positive effect.
- Click-only remains weaker than save.

### AURA-AIK-026 Post-use adverse reaction down-weights similar items
Level: Integration
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_Recommendation_Algorithms.md`, `Aura_AI_Model_Training_Pipeline.md`
Preconditions: User submits adverse reaction outcome on recommended item.
Steps:
1. Record outcome.
2. Request similar-category recommendations.
Expected:
- Similar risky items are deprioritized or flagged.
- Safety rules and training signals are updated.

### AURA-AIK-027 Shade feedback updates future complexion recommendations
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Engine_Design.md`, `Aura_Shade_Color_Ontology.md`
Preconditions: User reports "too light" on prior recommendation.
Steps:
1. Record shade feedback.
2. Request new complexion recommendations.
Expected:
- Future matches shift toward deeper shades when appropriate.
- Previously bad match does not repeat.

### AURA-AIK-028 New user strategy branches correctly by available data
Level: Unit
Priority: P0
Source Docs: `Aura_AI_Engine_Design.md`
Preconditions: Fixtures for no profile, skin type only, full declared profile, growing behavior.
Steps:
1. Run cold-start strategy for each fixture.
Expected:
- Each fixture follows documented branch.
- Confidence messaging matches state.

---

## E. Knowledge Graphs and Ontologies

### AURA-AIK-029 Ingredient graph resolves `contains -> allergen flag -> exclusion` path
Level: Unit
Priority: P0
Source Docs: `Aura_Ingredient_Knowledge_Graph.md`
Preconditions: Product contains known fragrance allergen and user is fragrance-sensitive.
Steps:
1. Traverse graph.
Expected:
- Match is found through correct path.
- Alert reason names specific ingredient or class.

### AURA-AIK-030 Ingredient conflict graph catches routine-conflict pairings
Level: Unit
Priority: P1
Source Docs: `Aura_Ingredient_Knowledge_Graph.md`
Preconditions: Owned serum conflicts with candidate ingredient.
Steps:
1. Check `conflicts_with` relation.
Expected:
- Conflict is surfaced as routine or duplicate-active caution.

### AURA-AIK-031 Shade scoring exact-match branch yields maximum score
Level: Unit
Priority: P0
Source Docs: `Aura_Shade_Color_Ontology.md`
Preconditions: User and candidate shade share exact depth and undertone.
Steps:
1. Calculate shade score.
Expected:
- Score equals documented maximum for exact match branch.

### AURA-AIK-032 Shade scoring adjacent-undertone branch is lower than exact but above opposite
Level: Unit
Priority: P1
Source Docs: `Aura_Shade_Color_Ontology.md`
Preconditions: Exact, adjacent, and opposite undertone fixtures.
Steps:
1. Calculate scores.
Expected:
- Exact > adjacent > opposite.
- Relative order matches documented scoring assumptions.

### AURA-AIK-033 Unmapped shade with insufficient metadata is excluded from shade-match scoring
Level: Unit
Priority: P0
Source Docs: `Aura_Shade_Color_Ontology.md`
Preconditions: Shade record marked low confidence.
Steps:
1. Attempt shade scoring.
Expected:
- Shade is excluded from standard scoring.
- Low-confidence trust path is used instead.

### AURA-AIK-034 Style archetype blend influences aesthetic tie-breakers only
Level: Unit
Priority: P2
Source Docs: `Aura_Style_Archetype_Taxonomy.md`
Preconditions: Two candidates equal on safety and utility, different on style alignment.
Steps:
1. Rank with archetype blend present.
Expected:
- Style preference influences tie-break or low-stakes ordering.
- Style never overrides safety or hard constraints.

### AURA-AIK-035 Product similarity thresholds separate strong dupes from alternatives
Level: Unit
Priority: P1
Source Docs: `Aura_Product_Similarity_Model.md`
Preconditions: Candidate pairs with scores above 0.75, between 0.50 and 0.75, and below 0.50.
Steps:
1. Run similarity classification.
Expected:
- Pairs map to strong dupe, alternative, or unrelated buckets correctly.

### AURA-AIK-036 Non-shade categories skip shade similarity contribution
Level: Unit
Priority: P1
Source Docs: `Aura_Product_Similarity_Model.md`
Preconditions: Serum or toner pair.
Steps:
1. Compute similarity.
Expected:
- Shade similarity contributes zero.
- Remaining dimensions still score correctly.

---

## F. Community Intelligence and Agent Orchestration

### AURA-AIK-037 Community sentiment weighting prefers similar-profile reviewers
Level: Integration
Priority: P1
Source Docs: `Aura_AI_Agent_Architecture.md`, `Aura_Recommendation_Algorithms.md`
Preconditions: Mixed review corpus with conflicting cohort sentiment.
Steps:
1. Score same product for dry-skin user and oily-skin user.
Expected:
- Sentiment feature changes according to matching cohort.

### AURA-AIK-038 Community themes surface both positive and cautionary signals
Level: Integration
Priority: P1
Source Docs: `Aura_AI_Agent_Architecture.md`
Preconditions: Review corpus contains repeated praise and repeated caution.
Steps:
1. Run community enrichment.
Expected:
- Both positive themes and cautionary themes are returned.

### AURA-AIK-039 Commerce agent flags stale price data
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Agent_Architecture.md`, `Aura_API_Contracts.md`
Preconditions: Product source older than freshness threshold.
Steps:
1. Run commerce enrichment.
Expected:
- Stale price trust flag appears.
- Merchant options remain visible only if contract allows.

### AURA-AIK-040 Explanation agent fails closed when required reason inputs are missing
Level: Unit
Priority: P0
Source Docs: `Aura_AI_Agent_Architecture.md`, `Aura_AI_Engine_Design.md`
Preconditions: Upstream reason list empty or malformed.
Steps:
1. Invoke explanation composition.
Expected:
- Explanation agent returns safe fallback or error.
- No fabricated narrative is emitted.

### AURA-AIK-041 Parallel-safe agents produce deterministic merged result
Level: Integration
Priority: P1
Source Docs: `Aura_AI_Agent_Architecture.md`
Preconditions: Discovery complete; safety, shade, and community agents run in parallel.
Steps:
1. Run orchestration multiple times with identical inputs.
Expected:
- Merge result is deterministic.
- No race changes final ranking unexpectedly.

### AURA-AIK-042 Agent timeout degrades gracefully without breaking final response
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Agent_Architecture.md`
Preconditions: One non-critical agent deliberately delayed beyond timeout.
Steps:
1. Execute orchestration.
Expected:
- Final response still returns from best available signals.
- Missing agent contribution is reflected via trust or confidence degradation.

---

## G. Fairness and Training Readiness

### AURA-AIK-043 Ranking quality gap across skin tone cohorts stays within tolerance
Level: Non-Functional
Priority: P0
Source Docs: `Aura_Recommendation_Algorithms.md`, `Aura_AI_Model_Training_Pipeline.md`
Preconditions: Evaluation dataset stratified by skin tone depth.
Steps:
1. Run offline metrics by cohort.
Expected:
- Cohort gap stays within documented threshold.

### AURA-AIK-044 Low-price-preference users are not systematically pushed over budget
Level: Non-Functional
Priority: P1
Source Docs: `Aura_AI_Model_Training_Pipeline.md`, `Aura_Recommendation_Algorithms.md`
Preconditions: Users segmented by spending preference.
Steps:
1. Run ranking evaluation by price band.
Expected:
- Low-budget cohort receives comparable recommendation quality.
- Over-budget leakage remains within defined tolerance.

### AURA-AIK-045 Training datasets include post-use outcomes without PII leakage
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Model_Training_Pipeline.md`, `Aura_AI_Engine_Design.md`
Preconditions: Exportable training snapshot build.
Steps:
1. Build training set from interactions and outcomes.
2. Inspect for identifiers and label integrity.
Expected:
- Outcome labels are present.
- No raw PII enters training snapshot.

### AURA-AIK-046 Champion/challenger rollout blocks regressions on safety outcomes
Level: Non-Functional
Priority: P0
Source Docs: `Aura_AI_Model_Training_Pipeline.md`
Preconditions: New model candidate available.
Steps:
1. Run challenger against current champion.
2. Compare time-to-decision, shade-miss, adverse reaction, and return outcomes.
Expected:
- Rollout is blocked on meaningful safety or trust regressions even if CTR rises.

### AURA-AIK-047 Selfie undertone inference abstains below confidence threshold
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Engine_Design.md`, `Aura_Task_Execution_Matrix.md`
Preconditions: Opt-in selfie input that produces borderline or poor-quality inference signals.
Steps:
1. Run the undertone inference path on low-quality or ambiguous input.
2. Request complexion recommendations after inference attempt.
Expected:
- Low-confidence inference abstains rather than forcing an undertone value.
- Recommendation flow falls back to declared data or low-confidence shade logic.
- User-facing output does not overclaim certainty.

### AURA-AIK-048 Selfie-derived undertone never overrides a declared undertone automatically
Level: Unit
Priority: P0
Source Docs: `Aura_AI_Engine_Design.md`, `Aura_PRD_v1.md`
Preconditions: User has a declared undertone and separately completes the opt-in selfie pilot.
Steps:
1. Merge declared profile state with selfie-derived inference result.
Expected:
- Declared undertone remains authoritative by default.
- Selfie-derived result is stored, if at all, as inferred evidence or ignored according to merge rules.
- Recommendation ranking consumes the declared value unless the user explicitly changes it.

### AURA-AIK-049 Selfie-pilot rollout evaluation reports quality across skin tone cohorts before expansion
Level: Integration
Priority: P1
Source Docs: `Aura_AI_ML_Data_Operating_Model.md`, `Aura_AI_Model_Training_Pipeline.md`, `Aura_24_Month_Strategy.md`
Preconditions: Pilot evaluation dataset with opt-in participants across multiple tone-depth cohorts.
Steps:
1. Run the pre-expansion evaluation bundle for the selfie pilot.
2. Inspect quality and fairness outputs by cohort.
Expected:
- Evaluation artifacts include cohort-sliced quality metrics rather than one aggregate score only.
- Material quality gaps block expansion or require mitigation tracking.
- Rollout decision is tied to documented evaluation evidence.

### AURA-AIK-050 Training and evaluation datasets exclude raw selfie images and retain derived labels only where allowed
Level: Integration
Priority: P0
Source Docs: `Aura_AI_Engine_Design.md`, `Aura_AI_ML_Data_Operating_Model.md`, `Aura_Data_Architecture.md`
Preconditions: Opt-in selfie pilot has generated inference outputs and downstream training artifacts.
Steps:
1. Inspect the training snapshot and evaluation dataset produced from selfie-pilot activity.
Expected:
- Raw selfie images are absent from training and evaluation datasets.
- Only approved derived attributes or labels appear where policy allows.
- Dataset lineage shows the consented source and processing boundary clearly.
