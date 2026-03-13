# Aura Consumer Value Operating Model

Version: 1.0
Date: March 2026

## Purpose

This document explains how Aura must deliver visible, repeatable value to consumers.

It complements the governing decisions in `Aura_Master_Platform_Specification.md` and the product
intent in `Aura_PRD_v1.md`. If this document conflicts with the Master spec, the Master spec wins.

The core rule is simple: **Aura only earns trust when it helps users make better decisions faster,
with fewer bad outcomes, and with clearer reasons than the alternatives.**

---

## 1. Consumer Value Thesis

Aura creates consumer value only if it does all four of these consistently:

1. Reduces research time and decision fatigue.
2. Reduces wrong-fit, unsafe, or regretted purchases.
3. Explains the decision in user language, with evidence.
4. Learns from outcomes without forcing users to surrender unnecessary data.

### Value Promise by Core Problem

| User Problem | Consumer Value Promise | Product Behavior Required | Proof Metric |
| ------------ | ---------------------- | ------------------------- | ------------ |
| Shade matching uncertainty | "Help me avoid buying the wrong shade." | Match by undertone and depth, show confidence, surface closest alternatives, abstain when data is weak | Shade-miss rate |
| Ingredient confusion | "Tell me if this is likely safe for me." | Detect allergens, risky actives, and incomplete ingredient lists; separate hard exclusions from soft warnings | Adverse reaction rate, safety precision |
| Fragmented discovery | "Save me time across tabs, creators, and retailer pages." | Produce a confident shortlist fast, compare similar products, summarize tradeoffs | Median time-to-decision |
| Generic recommendations | "Do not recommend for a generic average user." | Personalize by declared and learned preferences, but keep them visible and editable | Helpfulness rate, shortlist acceptance |
| Trust deficit | "Show me why I should believe this." | Attach reasons, evidence, freshness, source provenance, and confidence | Explanation helpfulness, trust feedback |

---

## 2. Consumer Jobs To Be Done

Aura's first responsibility is not to impress users with AI. It is to help them finish high-anxiety
shopping jobs with less effort and lower risk.

| Consumer Job | Primary User | What Success Feels Like | Aura Must Optimize For |
| ------------ | ------------ | ----------------------- | ---------------------- |
| Find a safe option | Sensitive-skin shopper | "I know what to avoid and what is likely okay." | Safety clarity, false-negative avoidance |
| Find the right shade | Complexion shopper | "I am confident enough to buy without guessing." | Shade accuracy, confidence gating |
| Narrow the field quickly | Research-heavy shopper | "I got from dozens of options to a shortlist fast." | Time-to-decision, compare flow quality |
| Stay within budget and values | Budget- and ethics-conscious shopper | "The options fit my constraints without hidden compromises." | Filter fidelity, price fit, ethical filter correctness |
| Learn from past outcomes | Repeat shopper | "Aura remembers what worked and what failed." | Outcome memory, fewer repeated mistakes |
| Recover from a bad outcome | User with regret, return, or reaction | "Aura helped me understand what happened and what to try next." | Triage speed, recovery UX, safer alternatives |

---

## 3. Value Delivery Loops

### 3.1 First-Session Value Loop

Aura must prove value before asking for optional data.

1. Collect only the minimum first-party inputs needed for the decision at hand.
2. Generate a shortlist or a clear exclusion result within the same session.
3. Show user-facing reasons and confidence on the first result page.
4. Ask for optional detail only when it will materially improve the answer.
5. End the session with a confident shortlist, save, compare decision, or explicit abstention.

**Exit criterion:** The user reaches a confident shortlist or a clear "not enough confidence yet"
state in less time than they would using retailer pages, creators, and community threads alone.

### 3.2 Decision-Support Loop

Aura must help users choose, not just browse.

1. Turn a large catalog into a small candidate set.
2. Explain why each candidate is included.
3. Explain why strong alternatives were excluded or ranked lower.
4. Highlight tradeoffs such as price, finish, ingredient concerns, or confidence.
5. Preserve neutrality between similar options unless evidence clearly supports one.

### 3.3 Outcome and Recovery Loop

Aura must keep helping after the purchase.

1. Prompt for post-use outcomes at the right moment, not immediately after the click.
2. Capture shade mismatch, adverse reaction, regret, return, and keep/not-keep.
3. Route severe safety signals to review within the defined SLA.
4. Update user-specific rules and global learning only after validation appropriate to the signal.
5. Offer safer or better-fit alternatives when a negative outcome is reported.

### 3.4 Repeat-Value Loop

Aura becomes sticky only when repeat sessions are smarter than first sessions.

1. Remember explicit profile data and outcomes.
2. Use behavioral learning to improve ordering, not to override hard safety constraints.
3. Show users what Aura has learned and let them correct it.
4. Reuse previous comparisons, saved items, and successful purchases to shorten future journeys.

---

## 4. Required Experience by Scenario

| Scenario | Core Consumer Need | Minimum Data Required | What Aura Must Do | What Aura Must Not Do | If Confidence Is Low |
| -------- | ------------------ | --------------------- | ----------------- | --------------------- | -------------------- |
| Foundation shade match | Avoid wrong shade | supported product shade data, skin tone, undertone or equivalent anchor | Show best-fit shades, nearest alternates, confidence, and why | Pretend confidence is high when shade anchors are sparse | Ask for more profile detail or abstain from single-best recommendation |
| Ingredient safety check | Avoid irritation or conflict | ingredient list, declared allergies/sensitivities, safety rules | Flag hard conflicts, explain soft warnings, show missing-data alerts | Present incomplete INCI data as safe | Show `LOW_CONFIDENCE` or `INCOMPLETE_INGREDIENT_DATA` and suppress strong positive recommendation |
| Browse feed | Save time and surface relevant options | category context and baseline profile | Personalize ranking and explain top reasons | Optimize only for clicks or novelty | Fall back to high-trust deterministic ranking |
| Product compare | Reduce decision fatigue | two or more products with normalized attributes | Present difference summary in plain language | Force users to infer differences from raw specs | Collapse to fact-based comparison without preference weighting |
| Budget-constrained shopping | Avoid overspending | price bounds, category | Respect ceiling as a hard or soft constraint per user setting | Upsell through ranking bias | Show best-fit within budget first |
| Negative outcome report | Recover safely and learn | recommendation reference plus outcome details | Triage, acknowledge uncertainty, update profile/rules, suggest safer options | Treat severe signals as normal engagement feedback | Escalate to review and reduce automation confidence |

---

## 5. Trust Contract With Consumers

Aura should operate under a visible trust contract:

1. **Explain the why:** Every recommendation must have human-readable reasons.
2. **Explain the evidence:** Users should see the product facts, source quality, or peer signal behind the reason.
3. **Separate facts from inference:** Declared, inferred, and imported data must remain distinguishable.
4. **Be honest about uncertainty:** Low-confidence answers should ask for more input, present a smaller claim, or abstain.
5. **Protect safety first:** Hard safety rules outrank engagement, monetization, and novelty.
6. **Protect neutrality:** Affiliate economics happen after ranking and never decide the ranking.
7. **Give users control:** Users can edit profile assumptions, revoke optional scopes, export data, and delete data.

---

## 6. Consumer Operations Model

Product value depends on operations, not just screens and models.

### Review Queues

| Queue | Trigger | Owner | SLA |
| ----- | ------- | ----- | --- |
| Safety outcome queue | adverse reaction or severe irritant signal | safety/governance reviewer | 24 hours |
| Shade mismatch queue | repeated mismatch on supported categories or products | catalog/shade reviewer | 3 business days |
| Data conflict queue | source disagreement on ingredients, shade, or availability | catalog operations | 3 business days |
| Trust complaint queue | user reports misleading explanation or hidden commercial influence | trust/product owner | 2 business days |

### Support Taxonomy

All negative consumer outcomes should map into a small, learnable taxonomy:

- wrong shade
- unsafe reaction
- incompatible finish or texture
- misleading review consensus
- bad price/value fit
- product not as described
- confidence overstated
- data missing or outdated

This taxonomy should drive product fixes, model updates, and support reporting.

---

## 7. Measurement Framework

### Activation and Decision Quality

| Metric | Why It Matters |
| ------ | -------------- |
| Median time-to-decision | Measures whether Aura truly reduces research burden |
| Shortlist acceptance rate | Indicates whether top results are usable, not just visible |
| Compare-to-save conversion | Tests whether comparison helps users choose confidently |

### Safety and Outcome Quality

| Metric | Why It Matters |
| ------ | -------------- |
| Shade-miss rate | Direct measure of complexion-value delivery |
| Adverse reaction report rate | Direct measure of safety quality |
| Recommendation-attributed return reduction | Direct measure of reduced regret and wrong purchases |
| Repeat negative outcome rate | Detects whether Aura is learning from mistakes |

### Trust and Relationship Quality

| Metric | Why It Matters |
| ------ | -------------- |
| Explanation helpfulness | Measures whether reasons actually help consumers |
| Low-confidence disclosure rate | Ensures uncertainty is surfaced, not hidden |
| Trust feedback / complaint rate | Catches breakdowns in perceived honesty or neutrality |
| Optional-consent uptake after core value | Tests whether users trust Aura enough to share more only after value is proven |

---

## 8. What To Build First

To maximize early consumer value, Aura should prioritize:

1. Ingredient safety checks with clear hard exclusions.
2. Supported-category shade matching with abstention when anchors are weak.
3. Fast shortlist and compare experiences.
4. Outcome capture and recovery flows.
5. Evidence-backed explanations and confidence.

Aura should delay or carefully gate:

1. Optional integrations that do not improve the first-session answer.
2. High-sensitivity enrichment that cannot clearly improve user outcomes.
3. Broad lifestyle expansion before beauty outcomes are measurably strong.
4. Community features that add noise before trust systems are ready.

---

## 9. Feature Checklist Before Shipping

Every consumer-facing feature should answer "yes" to these:

1. What user problem gets easier or safer because of this feature?
2. What decision becomes faster?
3. What negative outcome becomes less likely?
4. What evidence will the user see?
5. What happens when Aura is uncertain?
6. What outcome signals does the feature capture afterward?
7. How can the user correct Aura if it is wrong?

If a feature cannot answer these clearly, it is not ready to ship.
