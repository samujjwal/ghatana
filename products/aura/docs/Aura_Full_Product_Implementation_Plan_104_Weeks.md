# Aura Full Product Implementation Plan (104 Weeks)

Version: 1.0
Date: March 2026

## Purpose

This document lays out a week-by-week plan to implement the full Aura product described across the
current Aura documentation set, covering:

- Year 1 beauty intelligence platform
- community and brand analytics foundations
- Year 2 lifestyle expansion into wardrobe, assistant, routine automation, and scaled community intelligence

This plan assumes Aura follows the authoritative product, trust, consent, and architecture rules in
`Aura_Master_Platform_Specification.md`.

For weeks 1-24, use `Aura_Task_Execution_Matrix.md` as the task-level implementation guide. For
weeks 25-104, use `Aura_Long_Horizon_Task_Execution_Matrix.md`. For any AEP/Data Cloud/shared
platform work, also use `Aura_Shared_Platform_Integration_Spec.md`. This document is the
sequencing view, not the only implementation artifact.

---

## Planning Assumptions

### Scope Assumption

The "entire product" means the currently documented Aura roadmap through Month 24, not just the MVP.

### Team Assumption

The current Month 1-6 staffing shape is not enough to deliver the full documented product without
unrealistic compression. This plan assumes phased team growth:

| Period | Assumed Core Team |
| ------ | ----------------- |
| Weeks 1-24 | 1 PM, 1 designer, 3 full-stack engineers, 1 ML engineer, 1 QA/shared automation |
| Weeks 25-52 | add 1 mobile/frontend engineer, 1 platform/backend engineer, 1 data/analytics engineer, part-time SRE/DevOps |
| Weeks 53-104 | add 1 cross-category product engineer, 1 assistant/ML engineer, 1 partner analytics engineer, 1 trust/community operations lead |

### Delivery Assumption

Aura remains a modular-monolith-first system with three early deployables:

- `apps/api`
- `apps/core-worker`
- `apps/ml-inference`

New services are allowed only when scaling, runtime isolation, compliance, or team ownership justify them.

Shared-platform rule: Aura owns product logic and domain behavior, while AEP owns cross-process
event communication, Data Cloud owns managed data handling and lifecycle, and shared security/o11y
capabilities remain outside Aura-specific infrastructure.

---

## Mandatory Reuse Policy

Aura should reuse existing Ghatana assets before creating local equivalents.

### Direct Reuse Targets

| Reuse Target | Use In Aura | Why |
| ------------ | ----------- | --- |
| `@ghatana/ui` | web and mobile-adjacent shared UI primitives | accessible component baseline already exists |
| `@ghatana/tokens` | design tokens, spacing, typography, colors, transitions | avoid parallel design-token system |
| `@ghatana/theme` | shared theme and brand preset handling | avoids local theming fork |
| `@ghatana/utils` | formatting, class merge, responsive and accessibility helpers | avoids utility duplication |
| `@ghatana/api` | client transport, retries, middleware | avoids building another fetch wrapper |
| `@ghatana/realtime` | SSE/WebSocket helpers for notifications or live state | avoids local realtime abstraction |
| `@ghatana/accessibility-audit` | CI and release accessibility gates | gives reusable automated audit path |
| `platform/contracts` | shared schema/contract generation patterns | avoid contract drift and ad hoc codegen |
| `products/aep/platform` | event communication, routing, replay, and fan-out boundary | avoid direct Event Cloud or broker integration in Aura |
| `products/data-cloud/platform` | managed data plane for datasets, lifecycle, lineage, restore, and retention | avoid Aura-owned storage orchestration |
| `products/data-cloud/spi` | extension seam for Aura-specific Data Cloud plugins or adapters | keep specialized data behavior inside shared data plane |
| `platform/java/ingestion` | ingestion worker patterns and helpers | avoid custom ingestion framework |
| `platform/java/governance` | consent/audit/policy enforcement support | aligns Aura trust rules with shared governance |
| `platform/java/observability` | metrics, tracing, health, dashboards wiring | avoid bespoke observability stack glue |
| `platform/java/security` | auth/security integration building blocks | reuse shared security patterns |
| `platform/java/testing` | integration fixtures, testcontainers, async test helpers | stronger shared test foundation |
| `platform/java/ai-integration` | model-serving, evaluation, promotion, registry patterns | reuse model lifecycle foundations |
| `shared-services/auth-service` + `shared-services/auth-gateway` | centralized auth/session capabilities | avoid product-specific auth service |
| `shared-services/ai-inference-service` | shared inference boundary where suitable | avoid duplicating model-serving scaffolding |
| `shared-services/ai-registry` | model registry, champion/challenger integration | avoid custom model registry |

### Reference Implementations

These should be studied before Aura builds parallel patterns:

| Reference | Use As A Pattern For |
| --------- | -------------------- |
| `@ghatana/tutorputor-db` | Prisma package structure, migration packaging, test helpers |
| `@ghatana/tutorputor-ui-shared` | app-level shared providers and UI utility composition |
| `products/dcmaar/libs/typescript/connectors` | connector/integration packaging patterns |
| `products/audio-video/libs/common` | Java resilience, security, and observability utility composition |

### Reuse Rules

1. Reuse shared platform packages and shared products first.
2. If a direct shared package is not suitable, check existing product libraries for a reusable pattern.
3. Only build local Aura-specific code when the shared option does not fit the domain or would force harmful coupling.
4. Every new local wrapper must document why a shared alternative was rejected.
5. Aura must not create product-local event infrastructure or managed data infrastructure when AEP or Data Cloud already provide the boundary.

---

## Delivery Workstreams

Aura implementation should run across six coordinated workstreams:

1. Consumer experience: onboarding, feed, detail, compare, assistant, mobile
2. Catalog and knowledge: products, ingredients, shades, style, sources, provenance
3. Recommendation and AI: rules, ranking, confidence, training, evaluation, rollout
4. Trust and governance: consent, export, deletion, safety triage, moderation, audit
5. Platform and operations: CI/CD, observability, scaling, failover, release management
6. Revenue and growth: creator loops, affiliate instrumentation, premium, brand analytics

---

## Week-by-Week Plan

## Weeks 1-13: Foundation and Internal Alpha

| Week | Focus | Reuse First | Exit Gate |
| ---- | ----- | ----------- | --------- |
| 1 | Freeze MVP scope, target journeys, success metrics, and reuse audit | `@ghatana/ui`, `@ghatana/tokens`, `products/aep/platform`, `products/data-cloud/platform`, shared auth and AI services | Scope, exclusions, and reuse decisions approved |
| 2 | Stand up monorepo layout, Gitea Actions pipelines, baseline lint/test/build jobs | workspace tooling, `platform/java/testing`, `@ghatana/accessibility-audit` | CI runs baseline quality gates on Aura workspace |
| 3 | Wire auth skeleton, session handling, and sensitive-action re-auth path | `shared-services/auth-service`, `shared-services/auth-gateway`, `platform/java/security` | Login and protected routes work in local/staging |
| 4 | Finalize Prisma schema, shared domain types, and contract-first persistence model | `platform/contracts`, `products/data-cloud/platform`, `products/data-cloud/spi`, `@ghatana/tutorputor-db` as package pattern | Schema, API, and event semantics align |
| 5 | Build profile onboarding skeleton, declared-profile UX, and origin labeling | `@ghatana/ui`, `@ghatana/theme`, `@ghatana/utils` | Onboarding persists declared profile fields |
| 6 | Implement consent-center foundation and export/delete flow skeleton | `platform/java/governance`, `products/data-cloud/platform`, shared auth, `@ghatana/ui` dialogs/forms | Consent records and self-serve privacy flows persist |
| 7 | Stand up ingestion foundations for top beauty categories and provenance storage | `platform/java/ingestion`, `products/aep/platform`, `products/data-cloud/spi`, connector patterns from DCMAAR | Source ingest writes canonical product shells plus provenance |
| 8 | Add canonical product, brand, ingredient, and source entities | `products/data-cloud/platform`, `products/data-cloud/spi`, product schema patterns from Tutorputor | Product detail data can be queried consistently |
| 9 | Build ingredient analyzer v1 and hard safety exclusions | shared governance and Java domain modules, no local rule engine fork | Allergen and key ingredient conflicts are enforced |
| 10 | Build basic feed API and feed-card UI with reason and trust placeholders | `@ghatana/api`, `@ghatana/ui`, `@ghatana/tokens` | Feed works for cold-start and declared-profile users |
| 11 | Implement product detail, save/bookmark flows, and saved-items shelf | `@ghatana/ui`, `@ghatana/utils`, shared API wrapper | Save state persists across feed/detail surfaces |
| 12 | Build shade ontology v1, nearest-match scoring, and confidence gating | `platform/java/ai-integration` patterns, no custom serving stack yet | Supported foundation shade matching works with abstention behavior |
| 13 | Add compare, feedback capture, outcome event path, and internal alpha gate review | `products/aep/platform`, `products/data-cloud/platform`, `@ghatana/accessibility-audit` | Internal alpha launches with outcome instrumentation live |

## Weeks 14-26: Beta, Personalization, and ML Foundations

| Week | Focus | Reuse First | Exit Gate |
| ---- | ----- | ----------- | --------- |
| 14 | Run internal alpha, triage top defects, and close trust-blocking issues | shared test and observability tooling | Alpha blockers triaged and assigned |
| 15 | Harden dashboards, traces, error budgets, and operational alerts | `platform/java/observability`, shared monitoring stack | SLO dashboards exist for feed and recommendations |
| 16 | Add review ingestion pipeline and normalized review storage | `platform/java/ingestion`, `products/data-cloud/spi`, DCMAAR connector patterns | Review corpus available with provenance |
| 17 | Implement sentiment and caution extraction pipeline | `platform/java/ai-integration`, shared inference service where suitable | Review-derived sentiment signals land in catalog/recommendation path |
| 18 | Improve profile editor, inferred attribute visibility, and override UX | `@ghatana/ui`, `@ghatana/theme`, `@ghatana/utils` | Users can inspect and correct inferred data |
| 19 | Add assistant v1, guided prompts, and search-based discovery | `shared-services/ai-inference-service`, `@ghatana/api`, `@ghatana/realtime` if needed | Supported assistant queries resolve into safe recommendation flows |
| 20 | Add outcome-reporting UX to detail and saved-item surfaces | `@ghatana/ui`, shared event contracts | Users can submit structured post-use outcomes |
| 21 | Implement experiment assignment and exposure logging | `@ghatana/api`, `products/aep/platform`, platform observability, shared config patterns | Stable experiments run with auditable exposure data |
| 22 | Build recommendation training snapshot pipeline, challenger evaluation loop, and selfie-inference pilot path | `platform/java/ai-integration`, `shared-services/ai-registry`, consented ML capture flow | Training snapshot flow runs end to end and the selfie pilot stays opt-in and confidence-gated |
| 23 | Add affiliate instrumentation and outcome-aware funnel reporting | `@ghatana/api`, shared analytics patterns | Affiliate links are disclosed and tracked without rank bias |
| 24 | Complete privacy and trust UX pass, accessibility pass, and invite-beta hardening | `@ghatana/accessibility-audit`, `@ghatana/ui` | Trust-critical surfaces pass review and audit |
| 25 | Scale the invite beta: cohort expansion, support runbooks, rollback drills, and release checklist enforcement | shared observability and auth services | Invite beta runs with stable support and rollback readiness |
| 26 | Measure invite-beta retention/trust/outcome baselines and close immediate regressions | existing dashboards and alerting stack | Beta is live with stable dashboards, support loop, and early baselines |

## Weeks 27-39: Public Launch and Community Foundations

| Week | Focus | Reuse First | Exit Gate |
| ---- | ----- | ----------- | --------- |
| 27 | Triage beta feedback and ship highest-value fixes | existing Aura task matrix and shared regression gates | Top beta issues fixed with regression coverage |
| 28 | Expand ingestion coverage and freshness for priority beauty categories | `platform/java/ingestion`, `products/data-cloud/spi`, shared connectors | Freshness and catalog breadth improve measurably |
| 29 | Improve compare, shortlist quality, and premium feature hypothesis list | `@ghatana/ui`, `@ghatana/api` | Compare and shortlist conversion improves |
| 30 | Add community contribution data model and moderation queue foundations | `platform/java/governance`, shared admin UI patterns | Moderation-capable contribution path exists |
| 31 | Ship creator integration links/cards and attribution plumbing | `@ghatana/api`, `@ghatana/ui` shareable components | Creator traffic is attributable end to end |
| 32 | Implement verified-review architecture, reviewer flags, and audit support | shared auth/governance services | Verified review state is modeled and enforceable |
| 33 | Build twin-user signal research pipeline and offline similarity evaluation | `platform/java/ai-integration`, shared registry/eval patterns | Twin-signal offline evaluation report exists |
| 34 | Public-launch hardening: scale, failover, burst, rollback, and support drills | shared monitoring and testing modules | Public-launch hardening checklist passes |
| 35 | Public launch of beauty-first product | existing launch stack | Public launch occurs without unresolved P0 trust or safety blockers |
| 36 | Run press/editorial support and creator-loop monitoring week | analytics and GTM tooling already in place | Traffic quality and trust metrics remain healthy |
| 37 | Enable community review contributions in controlled beta | moderation and governance foundations | Community beta users can contribute safely |
| 38 | Build model v2 feature set from real outcome and review data | `platform/java/ai-integration`, shared AI registry | Model v2 candidate reaches offline readiness |
| 39 | Run model v2 shadow/canary rollout and fairness review | `shared-services/ai-registry`, shared inference service | Model v2 passes rollout and fairness gates |

## Weeks 40-52: Brand Analytics, Premium Validation, and Year 1 Scale

| Week | Focus | Reuse First | Exit Gate |
| ---- | ----- | ----------- | --------- |
| 40 | Design brand analytics schema, cohort privacy rules, and partner API boundary | `platform/contracts`, `products/data-cloud/platform`, governance modules | Brand analytics contract and privacy thresholds approved |
| 41 | Implement brand analytics backend aggregations and cohort suppression rules | `products/data-cloud/platform`, `products/data-cloud/spi`, `platform/java/governance` | Privacy-safe aggregate pipeline runs |
| 42 | Build brand analytics dashboard closed-beta UI | `@ghatana/ui`, `@ghatana/charts` if suitable, `@ghatana/theme` | Internal partner dashboard usable end to end |
| 43 | Validate low-volume cohort blocking, privacy review, and partner export paths | shared governance/audit modules | Brand analytics passes privacy and legal review |
| 44 | Run premium packaging experiments tied to real value, not arbitrary gating | existing analytics and UI stack | Premium hypotheses tied to measurable value |
| 45 | Mature community warning moderation and safety triage loops | governance and moderation tooling | High-risk community signals are reviewed before surfacing |
| 46 | Ship creator tools v1 for embedded ingredient-check and shade-match cards | `@ghatana/ui`, shared API client, reusable card components | Creator embed tools function with attribution and disclosure |
| 47 | Institutionalize recommendation quality reviews and monthly model governance | shared registry, evaluation, observability tooling | Review cadence and artifact set established |
| 48 | Complete model v2 rollout and retire obsolete champion paths | shared AI registry and inference tooling | Model v2 is live with rollback confidence |
| 49 | Onboard first brand partners into closed beta analytics | existing analytics dashboard and partner API boundary | Brand beta partners can use dashboards safely |
| 50 | Year 1 resilience quarter: backup/restore, chaos, and recovery rehearsals | shared infra monitoring, platform testing | Recovery objectives demonstrated in practice |
| 51 | Run Year 1 metrics retrospective and close outcome/trust gaps | existing dashboards and review loops | Year 1 KPI status documented with owners |
| 52 | Freeze Year 2 scope, staffing, and reuse plan before expansion work | all current strategy docs and reuse inventory | Year 2 execution plan approved |

## Weeks 53-65: Wardrobe Intelligence Foundations

| Week | Focus | Reuse First | Exit Gate |
| ---- | ----- | ----------- | --------- |
| 53 | Freeze wardrobe-intelligence scope, taxonomy, and reuse audit | `@ghatana/ui`, `platform/java/*`, style taxonomy docs | Fashion scope and success metrics approved |
| 54 | Add fashion catalog ingestion sources and source-quality rules | `platform/java/ingestion`, `products/data-cloud/spi`, shared connector patterns | Fashion catalog ingest working for pilot sources |
| 55 | Extend data model for wardrobe ownership, style attributes, and cross-category entities | `platform/contracts`, `products/data-cloud/platform`, `products/data-cloud/spi` | Wardrobe entities and ownership model finalized |
| 56 | Refine style-archetype mapping for fashion recommendation use | existing style taxonomy and shared UI quiz patterns | Style profile supports fashion use cases |
| 57 | Build wardrobe inventory UX for owned items and preferences | `@ghatana/ui`, `@ghatana/api`, Tutorputor shared provider patterns if useful | Users can create and manage wardrobe inventory |
| 58 | Implement fashion candidate generation and baseline ranking | existing recommendation domain and AI pipeline | Fashion candidate set and baseline scores work |
| 59 | Add seasonal, occasion, and outfit-context inputs to the profile/context engine | existing context engine and shared utils | Context-aware fashion inputs are persisted and queryable |
| 60 | Extend explainability to cross-category beauty-plus-fashion reasoning | existing explainability module and UI components | Cross-category reasons render clearly |
| 61 | Add assistant intents for outfit and occasion requests | shared inference service, assistant orchestration stack | Assistant handles supported fashion intents safely |
| 62 | Add wardrobe compare/save/share-light flows | `@ghatana/ui`, `@ghatana/realtime` only if needed | Compare and save work for fashion inventory and recs |
| 63 | Instrument wardrobe outcome and regret tracking | existing event contracts, `products/aep/platform`, and analytics stack | Wardrobe outcome telemetry available |
| 64 | Run internal wardrobe beta and trust review | existing QA, observability, and support playbooks | Internal beta passes trust and quality review |
| 65 | Prepare open beta for wardrobe intelligence | existing GTM and release infrastructure | Wardrobe beta readiness approved |

## Weeks 66-78: Advanced Assistant and Routine Automation

| Week | Focus | Reuse First | Exit Gate |
| ---- | ----- | ----------- | --------- |
| 66 | Define multi-turn assistant state model, task taxonomy, and success metrics | `shared-services/ai-inference-service`, `platform/java/ai-integration` | Assistant state and task model approved |
| 67 | Implement multi-turn conversation orchestration and tool routing | shared AI inference and registry services | Assistant can maintain structured multi-turn context |
| 68 | Add conversation memory, safe context carry-forward, and evaluation harness | `platform/java/ai-integration`, shared testing patterns | Assistant memory behaves within trust and privacy rules |
| 69 | Build optional wellness/wearable ingestion foundation with scoped consent | shared auth/governance services, connector patterns | Optional bio/wellness signals ingest only with valid scope |
| 70 | Extend routine data model for products, order, conflicts, gaps, and substitutions | `platform/contracts`, `products/data-cloud/platform`, `products/data-cloud/spi` | Routine model supports build/analyze/share scenarios |
| 71 | Implement routine conflict detection and duplicate-active logic | existing ingredient graph and rules engine | Routine analyzer catches documented conflict classes |
| 72 | Build "build my routine" UX and routine recommendation API | `@ghatana/ui`, `@ghatana/api`, recommendation domain reuse | Routine builder works end to end |
| 73 | Add proactive notifications for restocks, dupes, and relevant new matches | `@ghatana/realtime`, shared observability/queue patterns | Notification system supports opt-in trusted alerts |
| 74 | Run advanced assistant internal beta | shared AI infra and observability | Assistant beta meets task-completion and trust thresholds |
| 75 | Run routine and wellness optional-enrichment beta | governance and analytics stacks | Optional enrichment beta remains consent-safe and useful |
| 76 | Tune cross-category recommendation and assistant quality using real interactions | existing ML training and evaluation stack | Cross-category outcomes trend in the right direction |
| 77 | Validate premium packaging for assistant and routine value | existing monetization instrumentation | Premium candidates tied to measurable user value |
| 78 | Harden assistant/routine features for broader launch | shared accessibility, testing, and observability tooling | Release checklist passes for assistant/routine features |

## Weeks 79-91: Community Intelligence at Scale and Twin Network

| Week | Focus | Reuse First | Exit Gate |
| ---- | ----- | ----------- | --------- |
| 79 | Build verified-reviewer identity and approval workflow | shared auth/governance services | Verified reviewer program is operable and auditable |
| 80 | Roll out verified-review weighting in recommendation signals | existing community and ranking pipelines | Verified weighting works without unfair distortions |
| 81 | Add opt-in public profile sharing and visibility controls | shared auth/governance, `@ghatana/ui` privacy components | Public profile control model is explicit and safe |
| 82 | Build cohort community pages for skin type/style/routine segments | `@ghatana/ui`, shared API patterns | Cohort pages render useful, moderated aggregate views |
| 83 | Implement twin-user offline similarity model and trust review | shared AI integration and evaluation stack | Twin-user offline quality and privacy review passes |
| 84 | Run privacy and perception study for twin-user framing | research + trust tooling already in place | Similar-user feature does not feel invasive |
| 85 | Ship twin-user signals in shadow mode only | shared AI registry / experiment stack | Shadow metrics available without user-visible effect |
| 86 | Launch shared-routine public beta with moderation and privacy controls | community/governance reuse, `@ghatana/ui` | Shared routine beta is operational and moderated |
| 87 | Scale moderation tooling and review queues for community growth | governance modules and shared admin patterns | Moderation SLA remains stable at higher volume |
| 88 | Launch twin-user controlled beta | experiment framework, AI registry, privacy controls | Controlled beta runs with explicit safeguards |
| 89 | Build network-effect and trust measurement dashboards | existing analytics and charts stack | Community and twin-loop metrics are measurable |
| 90 | Public-launch prep for verified reviews, cohort pages, and twin signals | existing launch/hardening playbooks | Launch blockers resolved |
| 91 | Public launch of scaled community intelligence features | existing platform and GTM stack | Community-scale features launch without trust regressions |

## Weeks 92-104: Brand Analytics Revenue, Cross-Category Hardening, and Month 24 Closure

| Week | Focus | Reuse First | Exit Gate |
| ---- | ----- | ----------- | --------- |
| 92 | Define paid brand analytics packaging, SLAs, and partner onboarding workflow | existing analytics stack, governance modules | Revenue-ready analytics package approved |
| 93 | Automate partner reporting, scheduled exports, and contract-safe dashboards | `platform/contracts`, `products/data-cloud/platform`, shared auth/governance patterns | Partner reporting works without manual spreadsheet dependency |
| 94 | Extend analytics across beauty, wardrobe, and routine cohorts where privacy-safe | existing aggregation and suppression rules | Cross-category partner insights are privacy-safe and usable |
| 95 | Harden cross-category recommendation and compare experiences | existing recommendation, explainability, and UI foundations | Cross-category flows meet trust and latency targets |
| 96 | Optimize advanced assistant task-completion quality and fallback handling | shared AI inference/registry and evaluation stack | Multi-turn assistant meets task-completion gate |
| 97 | Instrument premium subscriber outcome satisfaction and support loop | existing analytics and support tooling | Premium value is measurable against free baseline |
| 98 | Refine creator, community, and partner growth loops with outcome-aware dashboards | current GTM, analytics, and attribution systems | Growth loops are managed by quality metrics, not volume only |
| 99 | Run workspace-wide reuse audit and dependency-upgrade program for Aura | all shared platform packages and services | Aura dependency graph is rationalized and documented |
| 100 | Execute full disaster-recovery, chaos, and failover quarter for the mature product | shared infra monitoring, platform testing, recovery suite | Mature product passes resilience drills |
| 101 | Complete full model-governance audit: cards, datasets, fairness, rollback, deletion policy | shared AI registry, evaluation, governance tooling | All active model families have production artifacts and audit trail |
| 102 | Close Month 24 KPI gaps on trust, regret, assistant completion, and analytics revenue | existing dashboards and review loops | KPI remediation plan or closure documented |
| 103 | Run full-product readiness review across product, trust, operations, partner analytics, and community | all governing docs and hardening suites | Full product reaches Month 24 release readiness |
| 104 | Complete 24-month retrospective and produce next-horizon roadmap and extraction decisions | all strategy, task, and review artifacts | Month 24 retrospective and next-plan approved |

---

## Weekly Operating Cadence

Every week in this plan should include:

1. Reuse review before local implementation starts.
2. Test backlog update before code starts.
3. Product/trust/ops review for any new user-facing or high-risk behavior.
4. Dashboard and regression review before release.

If a week cannot satisfy those four checks, the work should not be marked complete.
