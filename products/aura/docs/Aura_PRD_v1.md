# Aura — Product Requirements Document

Version: 1.1
Date: March 12, 2026

---

## Executive Summary

Aura is an AI-powered decision-support platform helping users determine which beauty, fashion, and wellness products are best suited for them personally.

Instead of generic discovery, Aura analyzes product metadata, community sentiment, user profile attributes, contextual signals, and AI inference — delivering personalized recommendations with clear, verifiable explanations.

This PRD defines product intent and user outcomes. Platform-wide policy, contract, and consent decisions are governed by `Aura_Master_Platform_Specification.md`.

---

## Product Vision

Aura becomes the **intelligence layer for lifestyle decisions**. It sits between users and commerce platforms, aggregating product data and generating personalized, explainable insights — so users never have to guess whether a product is right for them.

---

## Target Users

**Primary:** Women 18–34, beauty-focused digital shoppers who research products before buying and face uncertainty about shade matching, ingredient safety, or which brand to trust.

**Secondary:** Style-conscious ecommerce shoppers across beauty, fashion, and accessories.

**Future:** Health-aware consumers integrating wellness signals (wearables, routines, bio data).

---

## Core User Problems

| #   | Problem                        | User Pain                                                                                                                         |
| --- | ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **Shade matching uncertainty** | Foundation and correction products look different on screen vs. skin. Buying the wrong shade is costly and frustrating.           |
| 2   | **Ingredient confusion**       | Users with allergies, sensitivities, or active ingredient concerns cannot easily assess whether a product is safe for their skin. |
| 3   | **Fragmented discovery**       | Product research spans retailer sites, Reddit threads, YouTube reviews, and TikTok — requiring significant time and judgment.     |
| 4   | **Generic recommendations**    | Retailer and influencer recommendations are rarely personalized. What works for someone else may not work for this user.          |
| 5   | **Trust deficit in reviews**   | Paid partnerships and incentivized reviews make it hard to assess authenticity. Users distrust blanket star ratings.              |

---

## Product Principles

1. **Personalization First** — every recommendation reflects the individual user, not a generic persona.
2. **Explainability** — every recommendation comes with a clear, specific reason. No black-box scores.
3. **Transparency** — users see what data Aura uses to advise them and can correct or remove it.
4. **Privacy Control** — users own their data. All integrations require explicit, revocable consent.
5. **Trust Before Monetization** — commercial placements are labeled. Affiliate relationships are disclosed.

## Consumer Value Delivery Model

Aura should be judged by whether it helps consumers finish high-anxiety shopping jobs better than
retailer pages, creators, and community threads alone.

That means every major user flow must:

1. Reduce time-to-decision.
2. Reduce wrong-fit, unsafe, or regretted purchases.
3. Show clear reasons and evidence.
4. Capture post-use outcomes so the system learns from real results.

Detailed execution guidance lives in `Aura_Consumer_Value_Operating_Model.md`.

---

## Core Platform Pillars

| #   | Pillar                                  | Description                                                                                  |
| --- | --------------------------------------- | -------------------------------------------------------------------------------------------- |
| 1   | **Personal Profile System (You Index)** | Unified user intelligence model with declared, inferred, behavioral, and imported attributes |
| 2   | **Product Intelligence Engine**         | Canonical product knowledge, ingredient ontology, shade ontology, product similarity         |
| 3   | **Unified Content Feed**                | Personalized discovery surface with recommendation cards                                     |
| 4   | **Explainable Recommendation Engine**   | Ranked, reasoned, confidence-scored product suggestions with human-readable explanations     |
| 5   | **Community Intelligence Layer**        | Aggregated reviews, sentiment signals, verified community insights, twin-user discovery      |

---

## MVP Scope

Phase 1 focuses exclusively on **beauty**:

- Skincare product recommendations
- Foundation and correction shade matching (by skin tone and undertone)
- Ingredient analysis and allergen flagging
- Explainable recommendation cards in the feed
- User profile onboarding (skin type, undertone, allergies, ethical preferences)
- Product bookmarking/saving
- Basic consent center

**Explicitly out of scope for MVP:**

- Fashion and apparel
- Wearable integrations
- Community review contributions
- Brand analytics dashboard

---

## Functional Requirements

### Profile System

- FR-1: Users can declare skin type, undertone, skin tone, allergies, and ethical preferences during onboarding.
- FR-2: Aura infers additional profile attributes from behavioral signals (viewed, saved, dismissed products).
- FR-3: Users can view and override any inferred attribute.
- FR-4: Profile data is separated into declared, inferred, and imported categories with clear visual distinction.

### Product Intelligence

- FR-5: Product catalog includes brand, category, ingredients (INCI name), shades (with undertone and finish), and price range.
- FR-6: Aura maps product shades to its canonical shade ontology to enable cross-brand shade matching.
- FR-7: Ingredient safety analysis flags allergens, duplicate actives, and known irritants against the user's profile.

### Recommendation Engine

- FR-8: Recommendations are generated per-user using the personal profile and current context.
- FR-9: Every recommendation includes at least one structured reason code and a human-readable explanation.
- FR-10: Allergen-flagged products are excluded from recommendations by default, with override option.
- FR-11: Users can filter recommendations by price range, ethical filters, and category.
- FR-12: Users can compare two or more products side by side.

### Feedback & Learning

- FR-13: The platform captures view, click, save, dismiss, and purchase feedback signals.
- FR-14: Explicit user ratings (helpful / not helpful) are supported for recommendations.
- FR-15: Users can report post-purchase and post-use outcomes including shade too light / too dark, adverse reaction / irritation, and return / did not keep.
- FR-16: Feedback and outcome signals are used to improve future recommendation ranking and safety rules for that user and the broader system.

### Privacy & Consent

- FR-17: Core first-party service data required to operate Aura may be processed as part of the service. This includes account data, declared profile fields, on-platform saves/dismissals/clicks, recommendation requests/responses, and safety outcome reports.
- FR-18: Explicit consent is required before any optional integration or high-sensitivity enrichment is activated. This includes purchase-history import, email/receipt parsing, wearable or bio signals, selfie-based analysis, and public profile or community sharing.
- FR-19: Consent is per-scope, explicit, revocable at any time, and visible in a dedicated consent center.
- FR-20: Users can export their profile, interaction history, and recommendation history, and can delete their account and associated data via self-serve flows.
- FR-21: Affiliate link placement is clearly labeled on all recommendation surfaces.

---

## Non-Functional Requirements

| Category          | Requirement                                                                       |
| ----------------- | --------------------------------------------------------------------------------- |
| **Latency**       | Recommendation API p95 response < 300ms                                           |
| **Availability**  | 99.9% uptime for recommendation and feed APIs                                     |
| **Privacy**       | GDPR and CCPA compliant from day one                                              |
| **Security**      | Authentication via JWT; optional integrations and high-sensitivity processing require valid, scoped consent |
| **Accessibility** | WCAG 2.1 AA compliance on web; equivalent on mobile                               |
| **Observability** | All services emit structured logs, traces, and metrics                            |

---

## Success Metrics

### Phase 1 (MVP) KPIs

| Metric                                         | Target                                                               |
| ---------------------------------------------- | -------------------------------------------------------------------- |
| Median time-to-decision for supported journeys | ≤ 10 minutes from query / feed entry to confident shortlist or save |
| Shade-miss rate                                | ≤ 15% of Aura-assisted supported complexion outcomes                 |
| Ingredient safety flag accuracy                | ≥ 95% precision on QA-reviewed sample                               |
| Adverse reaction report triage SLA             | 100% of recommendation-attributed reports reviewed within 24 hours   |
| Recommendation-attributed return reduction     | ≥ 15% lower than self-directed baseline once outcome volume is valid |
| Recommendation explanation helpfulness         | ≥ 4.0 / 5.0 (user rating)                                            |

### Phase 2 KPIs

| Metric                                         | Target                                         |
| ---------------------------------------------- | ---------------------------------------------- |
| Median time-to-decision                        | ≥ 20% improvement over Phase 1 baseline        |
| Shade-miss rate                                | ≥ 20% improvement over Phase 1 baseline        |
| Recommendation-attributed return reduction     | Sustained across skincare and complexion flows |
| Adverse reaction reports per 1,000 kept items  | Below defined safety threshold                 |
| Explanation helpfulness with evidence coverage | ≥ 4.0 / 5.0 with 100% evidence-backed reasons  |

---

## Monetization

| Stream               | Description                                                                                          | Phase   |
| -------------------- | ---------------------------------------------------------------------------------------------------- | ------- |
| Affiliate Commerce   | Commission on purchases made through Aura links                                                      | Phase 2 |
| Premium Subscription | Advanced analysis, unlimited comparisons, routine automation                                         | Phase 3+ |
| Brand Analytics      | Anonymized insights for beauty brands (ingredient performance, shade satisfaction, sentiment trends) | Phase 4 |

---

## Constraints & Risks

| Risk                       | Mitigation                                                                             |
| -------------------------- | -------------------------------------------------------------------------------------- |
| Ingredient data quality    | Partner with established ingredient databases; build validation pipeline               |
| Shade mapping accuracy     | Build canonical shade ontology with human-review step; show confidence levels          |
| Cold-start for new users   | Offer guided onboarding questionnaire; show popular items until profile is established |
| Affiliate trust concerns   | Clearly label all commercial placements; never let placement fees influence ranking    |
| Privacy regulation changes | Separate core service processing from optional/high-sensitivity consent scopes and review with counsel before launch |
