# Aura 24-Month Product Strategy

## Strategic Intent

Build a defensible, personalized beauty intelligence platform in Year 1. Expand into broader lifestyle intelligence and community-driven growth in Year 2.

---

## Year 1 — Beauty Intelligence Platform

**Theme:** Earn user trust through accuracy, transparency, and a clearly superior shade- and ingredient-aware recommendation experience.

### Key Objectives

1. Build and ship the recommendation engine with ingredient safety and shade matching
2. Acquire and retain early users through creator partnerships and community presence
3. Validate affiliate monetization and establish baseline conversion metrics
4. Stand up the data flywheel: feedback → model learning → better recommendations

### Quarterly Milestones

#### Q1 — Foundation & MVP

- Monorepo, Gitea Actions CI/CD, and environments established
- Modular-monolith boundaries defined (`api`, `core-worker`, `ml-inference`) with explicit service-extraction criteria
- Core domain model and Prisma schema finalized
- Auth, user onboarding, and profile flow shipped
- Product catalog ingestion pipeline operational (MVP coverage of top 5 beauty categories)
- Ingredient analyzer v1 shipping with allergen detection
- Shade match v1 operational for foundations
- Internal alpha with 50 users

#### Q2 — AI Personalization & Beta Launch

- Recommendation feed operational with explainable reason codes
- AI skin analysis (selfie-based undertone inference) in closed beta
- Review sentiment pipeline ingesting community data
- Open beta launch: invite-based, ~500 users
- Affiliate link instrumentation and first revenue measurements
- Experimentation framework operational (A/B testing for ranking weights)
- 30-day retention benchmark established

#### Q3 — Community Features & Growth

- Community review contributions enabled (Phase 4 foundations)
- Twin-user discovery signals in personalization model
- Creator partnership program activated (10+ creator integrations)
- Public launch
- Press and editorial outreach
- Product comparison feature shipped
- First premium subscription offering tested

#### Q4 — Brand Analytics Dashboard

- Brand analytics dashboard in closed beta (3 brand partners)
- Anonymized aggregate insights: ingredient performance by skin type, shade satisfaction distribution, sentiment trends
- Recommendation quality review process established
- Model v2 training cycle complete (sufficient interaction data available)
- 24-month strategy retrospective and Year 2 planning

### Year 1 Success Metrics

| Metric                              | Target by Month 12                                                  |
| ----------------------------------- | ------------------------------------------------------------------- |
| Median time-to-decision             | ≤ 8 minutes for supported beauty journeys                           |
| Shade-miss rate                     | ≤ 12% for Aura-assisted supported complexion outcomes               |
| Recommendation-attributed return reduction | ≥ 20% vs. self-directed baseline once measurement volume is valid |
| Adverse reaction report triage SLA  | 100% reviewed within 24 hours, with rule updates for severe signals |
| Explanation helpfulness             | ≥ 4.0 / 5.0                                                         |

---

## Year 2 — Lifestyle Intelligence Expansion

**Theme:** Expand beyond beauty into fashion, wellness, and routine automation. Deepen community and begin building the twin-user network.

### Key Objectives

1. Launch wardrobe intelligence (fashion product recommendations)
2. Ship advanced AI assistant capable of multi-turn recommendation conversations
3. Expand community features: verified reviews, shared routines, twin discovery at scale
4. Grow brand analytics revenue to meaningful contribution

### New Capabilities

#### Wardrobe Intelligence

- Wardrobe catalog: users can inventory owned items
- Style archetype-aware product recommendations
- Seasonal and occasion-based styling suggestions
- Cross-category intelligence: complement beauty routine to wardrobe style

#### Advanced AI Assistant

- Multi-turn conversational recommendations ("Ask Aura")
- Context-aware queries: "I have a garden party on Saturday, what glow products would work with my complexion?"
- Proactive notifications: restock alerts, new dupe detected for owned product, trending ingredient in user's concern category

#### Routine Automation

- "Build my routine" flow — AI-generated morning and evening routine recommendation
- Routine analysis: surface conflicts, redundancies, or gaps in an existing routine
- Routine sharing: publish and discover community routines

#### Community Intelligence at Scale

- Verified reviewer program (identity-verified reviews carry higher weight)
- Skin type cohort community pages
- Public profile sharing (opt-in)

### Year 2 Success Metrics

| Metric                                | Target by Month 24                                     |
| ------------------------------------- | ------------------------------------------------------ |
| Cross-category time-to-decision       | Maintains or improves Year 1 benchmark                 |
| Wardrobe / routine recommendation regret rate | Lower than self-directed baseline in supported flows |
| AI assistant task completion rate     | Majority of supported multi-turn queries end in a confident shortlist |
| Brand analytics revenue               | Meaningful ARR contribution                            |
| Premium subscriber outcome satisfaction | Premium users report measurable value vs. free baseline |

---

## Long-Term Vision

Aura becomes a **trusted personal advisor** for lifestyle and commerce decisions. The platform's value increases as the user's profile deepens, the community grows, and the AI's understanding of individual needs becomes more precise.

Long-term, Aura is not a product discovery app — it is the intelligence layer that users consult before any lifestyle purchase and that understands them well enough to make proactive, trustworthy suggestions before they even think to ask.
