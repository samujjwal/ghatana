# Aura Long-Horizon Task Execution Matrix

Version: 1.0
Date: March 2026
Coverage: Weeks 25-104
Primary Schedule Source: `Aura_Full_Product_Implementation_Plan_104_Weeks.md`

## Purpose

This document extends Aura's implementation-ready task decomposition beyond the first 24 weeks.

Use this document together with:

- `Aura_Task_Execution_Matrix.md` for Weeks 1-24
- `Aura_Shared_Platform_Integration_Spec.md` for AEP/Data Cloud/shared platform implementation rules
- `Aura_Test_Plan_and_Traceability.md` for test-first execution rules

If a long-horizon item is being pulled into active delivery, it must have a usable row here before
implementation starts.

---

## How To Use

1. Start from the matching week in `Aura_Full_Product_Implementation_Plan_104_Weeks.md`.
2. Pull the corresponding row here into sprint planning.
3. Add implementation tickets from `What`, `How`, `Where`, and `Validation`.
4. Update detailed test suites before coding if the feature introduces new behavior, scopes, enums, or integrations.

---

## Weeks 25-39

| Week | What | How | Where | Validation |
| ---- | ---- | --- | ----- | ---------- |
| 25 | Scale the invite beta with controlled cohort expansion and live support readiness | admit staged cohorts, verify rollback playbook, and monitor trust/outcome dashboards continuously | onboarding flows, feature flags, GTM ops, shared/o11y, support runbooks | invite beta reaches target cohort size with stable dashboards and no unresolved P0 trust defects |
| 26 | Close immediate beta regressions and stabilize the operating baseline | triage defects from real usage, ship highest-severity fixes, and update regression coverage | affected `apps/*`, `domains/*`, docs, and tests | top validated beta regressions are fixed and protected by automated tests |
| 27 | Expand ingestion coverage and freshness for priority beauty categories | add sources, improve refresh scheduling, and tighten freshness suppression/reranking rules | `apps/core-worker`, `domains/catalog`, `shared/data-cloud`, shared/o11y | freshness and coverage improve on named priority categories |
| 28 | Improve compare, shortlist quality, and tradeoff visibility | refine compare scoring, explanation copy, and shortlist persistence based on beta behavior | `apps/web`, `apps/api`, `domains/explainability`, `domains/recommendation` | compare completion and shortlist quality improve without trust regressions |
| 29 | Finalize premium value hypotheses that preserve core trust surfaces | define premium candidates around workflow depth rather than gating trust/safety essentials | product analytics, `docs/Aura_GTM_Strategy.md`, `docs/Aura_Consumer_Value_Operating_Model.md` | premium proposal excludes core trust, safety, export, and consent controls |
| 30 | Add community contribution data model and moderation queue foundations | introduce contribution entities, review status, moderation actions, and audit trail support | `apps/api` (`community`, `governance`), `data/prisma`, internal admin UI | moderated contribution flow exists with auditable state changes |
| 31 | Ship creator integration links, cards, and attribution plumbing | generate creator-aware link surfaces and track downstream quality by source | `apps/web`, `apps/api`, `packages/ui`, analytics paths | creator traffic is attributable through profile completion, trust, and outcome metrics |
| 32 | Implement verified-review architecture and reviewer flags | model reviewer verification state, content weighting hooks, and governance controls | `apps/api`, `data/prisma`, `domains/community`, shared/security | verified review state is enforceable and auditable end to end |
| 33 | Build twin-user signal research pipeline and offline evaluation | derive similarity candidates offline, define privacy limits, and measure quality before exposure | `ml/training`, `ml/evaluation`, `domains/community`, `docs/Aura_AI_ML_Data_Operating_Model.md` | offline twin-signal report exists with privacy and fairness review |
| 34 | Run public-launch hardening for scale, failover, burst, and rollback | execute resilience drills, restore checks, and launch gating using shared platform dependencies | shared/o11y, shared/data-cloud, shared/aep, runbooks, test suites | public-launch hardening checklist passes with drill evidence |
| 35 | Public launch the beauty-first product | remove launch gate restrictions only after quality, trust, and support criteria are green | production configs, GTM ops, support, shared/o11y | public launch occurs without unresolved P0 trust, privacy, or resilience blockers |
| 36 | Operate press/editorial support and creator-loop monitoring | evaluate incoming acquisition quality and support issues daily after launch | GTM ops, analytics, support playbooks | traffic quality and trust metrics remain within launch guardrails |
| 37 | Enable community review contributions in controlled beta | admit limited contributors, apply moderation SLAs, and measure safety/comprehension risk | `apps/web`, `apps/api`, internal moderation UI, shared/security | community beta is operational, moderated, and reversible |
| 38 | Build model v2 feature set from real outcome and review data | convert validated post-launch signals into candidate ranking/shade/review features | `ml/feature-pipelines`, `ml/training`, `domains/recommendation` | model v2 feature set is documented and reproducible |
| 39 | Run model v2 shadow and canary rollout with fairness review | compare v2 against champion using shadow, then small canary, with rollback path active | shared ai registry/inference services, `ml/evaluation`, shared/o11y | model v2 passes outcome, fairness, and rollback gates before wider rollout |

## Weeks 40-52

| Week | What | How | Where | Validation |
| ---- | ---- | --- | ----- | ---------- |
| 40 | Design brand analytics schema and partner API boundary | define privacy-safe aggregates, cohort suppression rules, and partner-facing contract shapes | `docs/Aura_API_Contracts.md`, `docs/Aura_Data_Architecture.md`, `platform/contracts` | analytics contract and privacy thresholds are approved |
| 41 | Implement brand analytics backend aggregations | build derived datasets and suppression logic on Data Cloud-managed analytics paths | `apps/core-worker`, `shared/data-cloud`, analytics datasets, governance logic | aggregate pipeline runs with suppression and traceability |
| 42 | Build closed-beta brand analytics dashboard UI | create internal partner views using shared charting and access control patterns | `apps/web` or partner UI surface, `@ghatana/ui`, `@ghatana/charts` | internal partner dashboard works end to end |
| 43 | Validate low-volume cohort blocking and partner export paths | test privacy thresholds, export contracts, and failure handling before partner access | analytics exports, governance policies, shared/security | privacy review passes and blocked cohorts are enforced |
| 44 | Run premium packaging experiments tied to measurable value | test packaging around deeper comparison, assistant depth, or routine workflows | growth experiments, GTM docs, analytics, `apps/web` paywall surfaces | premium experiment results are tied to value, not arbitrary gating |
| 45 | Mature community warning moderation and safety triage loops | add review escalation, warning clustering, and response workflows for risky content | moderation UI, governance queues, safety review artifacts | high-risk community signals are reviewed before surfacing broadly |
| 46 | Ship creator tools v1 for embedded ingredient-check and shade-match cards | provide embeddable cards and attribution with disclosure-safe output | `packages/ui`, `apps/api`, creator integration assets | creator embed tools work with attribution and trust labeling |
| 47 | Institutionalize recommendation quality reviews and model governance | create monthly review ritual, owners, artifacts, and escalation rules | product review artifacts, `ml/evaluation`, shared/o11y | governance cadence exists with recorded decisions and follow-ups |
| 48 | Complete model v2 rollout and retire obsolete champion paths | finish promotion only after rollout evidence clears trust and quality gates | shared ai registry, inference services, configs | v2 is live with rollback confidence and obsolete paths are retired safely |
| 49 | Onboard first brand partners into closed beta analytics | provision partner access, contracts, dashboards, and support workflows | partner onboarding ops, shared/security, analytics UI | first partner cohort can use analytics safely and supportably |
| 50 | Run Year 1 resilience quarter for backup, restore, chaos, and recovery | execute recovery drills against Data Cloud, AEP, and core Aura flows | shared/data-cloud, shared/aep, shared/o11y, recovery suite | recovery objectives are demonstrated in practice |
| 51 | Run Year 1 metrics retrospective and trust-gap closure planning | compare year-one metrics against roadmap targets and assign remediation owners | PRD, GTM docs, dashboards, leadership review artifacts | KPI status and trust gaps are documented with owners and due dates |
| 52 | Freeze Year 2 scope, staffing, and reuse plan | re-baseline roadmap and confirm reuse-first strategy before category expansion | strategy docs, long-horizon matrix, hiring/staffing plan | Year 2 plan is approved with clear sequencing and ownership |

## Weeks 53-65

| Week | What | How | Where | Validation |
| ---- | ---- | --- | ----- | ---------- |
| 53 | Freeze wardrobe-intelligence scope, taxonomy, and reuse audit | define supported fashion categories, user journeys, and shared capability reuse before coding | `docs/Aura_Style_Archetype_Taxonomy.md`, strategy docs, reuse inventory | fashion scope and success metrics are approved |
| 54 | Add fashion catalog ingestion sources and quality rules | implement pilot fashion connectors, normalization, and source-quality scoring | `apps/core-worker`, `domains/catalog`, `shared/data-cloud`, connector patterns | pilot fashion ingest works with provenance and freshness metadata |
| 55 | Extend data model for wardrobe ownership and cross-category entities | add ownership, style, and apparel-specific entities without breaking beauty domains | `data/prisma`, `domains/catalog`, shared/data-cloud | wardrobe entities and ownership model are stable and queryable |
| 56 | Refine style-archetype mapping for fashion use cases | improve style elicitation, mapping confidence, and override behavior | `domains/profile`, `domains/recommendation`, `apps/web` style flows | style profile supports fashion recommendations with visible confidence |
| 57 | Build wardrobe inventory UX for owned items and preferences | create add/edit/remove flows for wardrobe items and relevant preferences | `apps/web`, `apps/mobile` if needed, `apps/api`, `packages/ui` | users can manage wardrobe inventory safely and clearly |
| 58 | Implement fashion candidate generation and baseline ranking | extend retrieval, hard filters, and deterministic ranking to fashion products | `domains/recommendation`, `domains/catalog`, `apps/api` | fashion candidate sets and baseline ranking work for supported journeys |
| 59 | Add seasonal, occasion, and outfit-context inputs | persist context signals for fashion decision support without over-collection | `domains/profile`, `apps/web`, `apps/api` | context inputs are stored, editable, and used in supported ranking paths |
| 60 | Extend explainability to cross-category beauty-plus-fashion reasoning | show why cross-category suggestions fit without mixing unsupported claims | `domains/explainability`, `packages/ui`, `apps/web` compare/detail surfaces | cross-category reasons are readable, evidence-backed, and safe |
| 61 | Add assistant intents for outfit and occasion requests | extend assistant orchestration with bounded fashion-specific intents | `apps/api` assistant, `docs/Aura_AI_Agent_Architecture.md`, shared AI services | assistant handles supported fashion intents with fallback behavior |
| 62 | Add wardrobe compare, save, and limited sharing flows | extend compare/save/share-light flows to wardrobe and fashion recommendations | `apps/web`, `apps/api`, `packages/ui`, privacy controls | wardrobe compare and save work without violating privacy rules |
| 63 | Instrument wardrobe outcome and regret tracking | capture keep/return/regret signals for fashion recommendations | `packages/event-contracts`, `apps/api`, `shared/aep`, `shared/data-cloud` | wardrobe outcomes are linked to recommendation and usable for learning |
| 64 | Run internal wardrobe beta and trust review | test wardrobe flows internally with trust, quality, and support checks | feature flags, shared/o11y, support playbooks | internal wardrobe beta passes trust and quality review |
| 65 | Prepare open beta for wardrobe intelligence | finalize cohorts, launch checklist, regression gates, and support plan | GTM ops, regression suites, runbooks | wardrobe beta readiness is approved with rollback path |

## Weeks 66-78

| Week | What | How | Where | Validation |
| ---- | ---- | --- | ----- | ---------- |
| 66 | Define multi-turn assistant state model and success metrics | formalize task states, memory rules, and bounded tool-routing expectations | `docs/Aura_AI_Agent_Architecture.md`, assistant domain docs | assistant state model is approved and measurable |
| 67 | Implement multi-turn conversation orchestration and tool routing | add structured state handling, tool selection, and safe fallback paths | `apps/api` assistant module, shared AI services, packages contracts | assistant can maintain bounded multi-turn context safely |
| 68 | Add conversation memory and evaluation harness | store safe carry-forward context and evaluate task completion systematically | assistant module, `ml/evaluation`, shared/data-cloud | assistant memory behavior is measurable and policy-compliant |
| 69 | Build optional wellness and wearable ingestion foundation | add explicitly scoped optional integrations with strict consent gates | `apps/core-worker`, `domains/profile`, shared/security, shared/data-cloud | optional bio/wellness signals ingest only with valid scope |
| 70 | Extend routine data model for products, conflicts, gaps, and substitutions | model routine steps, product roles, and conflict relationships | `data/prisma`, `domains/catalog`, `domains/recommendation` | routine model supports build, analyze, and explain scenarios |
| 71 | Implement routine conflict detection and duplicate-active logic | add deterministic checks for conflict classes and redundant actives | `domains/recommendation`, `domains/catalog`, `apps/api` | routine analyzer catches documented conflict classes correctly |
| 72 | Build routine-builder UX and routine recommendation API | deliver create/edit/analyze routine flows with explainability | `apps/web`, `apps/api`, `packages/ui`, `domains/explainability` | routine builder works end to end for supported product classes |
| 73 | Add proactive notifications for restocks, dupes, and new matches | add opt-in notification logic without spamming or violating trust rules | `apps/api`, `@ghatana/realtime`, shared/o11y, user prefs | notification system supports trusted, opt-in alerts only |
| 74 | Run advanced assistant internal beta | evaluate multi-turn assistant on task completion, trust, and fallback quality | internal beta program, shared AI infra, evaluation harness | assistant beta meets task-completion and trust thresholds |
| 75 | Run routine and wellness optional-enrichment beta | validate usefulness and consent comprehension before broader rollout | feature flags, governance flows, analytics, support | enrichment beta is useful, consent-safe, and reversible |
| 76 | Tune cross-category recommendation and assistant quality | use real interactions and outcomes to improve cross-category performance | `ml/training`, `ml/evaluation`, `domains/recommendation` | cross-category outcomes improve without fairness or trust regressions |
| 77 | Validate premium packaging for assistant and routine value | test monetization only around value-added depth and convenience | growth experiments, pricing tests, analytics | premium candidates are supported by measurable user value |
| 78 | Harden assistant and routine features for broader launch | complete accessibility, resilience, support, and rollback gating | regression suites, shared/o11y, runbooks, feature flags | release checklist passes for assistant and routine features |

## Weeks 79-91

| Week | What | How | Where | Validation |
| ---- | ---- | --- | ----- | ---------- |
| 79 | Build verified-reviewer identity and approval workflow | implement reviewer verification, approval criteria, and audit workflow | shared/security, governance services, moderation UI | verified reviewer program is operable and auditable |
| 80 | Roll out verified-review weighting in recommendation signals | integrate reviewer trust weights without overpowering broader quality signals | `domains/community`, `domains/recommendation`, `ml/evaluation` | verified weighting improves signal quality without unfair distortions |
| 81 | Add opt-in public profile sharing and visibility controls | create safe profile visibility model with explicit user control and previews | `apps/web`, `apps/api`, privacy controls, shared/security | public profile controls are explicit, revocable, and safe |
| 82 | Build cohort community pages for supported segments | create moderated aggregate views for skin type, style, and routine cohorts | `apps/web`, `apps/api`, `packages/ui`, moderation flows | cohort pages are useful, moderated, and privacy-safe |
| 83 | Implement twin-user offline similarity model and trust review | move twin-user research into candidate model evaluation with privacy guardrails | `ml/training`, `ml/evaluation`, privacy review artifacts | twin-user offline quality and privacy review pass |
| 84 | Run privacy and perception study for twin-user framing | test user understanding and discomfort before visible rollout | user research, trust review, GTM/research artifacts | framing does not feel invasive to target cohorts |
| 85 | Ship twin-user signals in shadow mode only | compute and log twin-user signals without user-visible impact | `domains/community`, experiment framework, shared/o11y | shadow metrics exist without affecting live ranking |
| 86 | Launch shared-routine public beta with moderation controls | expose public routine sharing to controlled users with moderation and visibility rules | `apps/web`, `apps/api`, moderation UI, shared/security | shared routine beta is operational and moderated |
| 87 | Scale moderation tooling and review queues for community growth | improve reviewer productivity, queue routing, and SLA observability | moderation tooling, governance queues, shared/o11y | moderation SLA holds under increased community volume |
| 88 | Launch twin-user controlled beta | expose twin-user features only behind explicit safeguards and experiment control | feature flags, experiment framework, privacy controls | controlled beta runs with guardrails and rollback ready |
| 89 | Build network-effect and trust dashboards | create dashboards for community quality, twin-loop value, and trust perception | analytics datasets, shared/o11y, GTM/product review artifacts | community and twin-network metrics are measurable and reviewable |
| 90 | Prepare verified reviews, cohort pages, and twin-user launch | run final privacy, moderation, resilience, and communications readiness review | launch checklist, runbooks, moderation ops, GTM ops | launch blockers are closed or explicitly waived |
| 91 | Public launch scaled community intelligence features | release mature community features with live moderation and safety support | production configs, moderation ops, support, shared/o11y | community-scale features launch without trust regressions |

## Weeks 92-104

| Week | What | How | Where | Validation |
| ---- | ---- | --- | ----- | ---------- |
| 92 | Define paid brand analytics packaging, SLAs, and onboarding | finalize packaging, service levels, privacy commitments, and partner workflow | strategy docs, partner contracts, analytics product docs | revenue-ready analytics package is approved |
| 93 | Automate partner reporting and scheduled exports | generate recurring partner reports through contract-safe export flows | `apps/core-worker`, `shared/data-cloud`, partner analytics datasets | partner reporting works without manual spreadsheet dependency |
| 94 | Extend analytics across beauty, wardrobe, and routine cohorts | broaden partner analytics only where anonymity and utility remain acceptable | analytics datasets, governance rules, partner API | cross-category partner insights are privacy-safe and useful |
| 95 | Harden cross-category recommendation and compare experiences | refine latency, explainability, and correctness across mixed-category journeys | `apps/api`, `domains/recommendation`, `domains/explainability`, `apps/web` | cross-category flows meet trust and latency targets |
| 96 | Optimize advanced assistant task-completion quality and fallback handling | improve tool routing, fallback behavior, and evaluation based on production usage | assistant module, shared AI services, `ml/evaluation` | multi-turn assistant meets task-completion gate |
| 97 | Instrument premium subscriber satisfaction and support loop | measure premium outcomes, support burden, and value realization against free baseline | analytics, support tooling, GTM/product review | premium value is measurable and supportable |
| 98 | Refine creator, community, and partner growth loops with quality dashboards | manage growth loops using outcome and trust metrics rather than top-line volume | analytics dashboards, GTM ops, partner review artifacts | growth loops are governed by quality metrics |
| 99 | Run workspace-wide reuse audit and dependency rationalization | review local wrappers, shared dependencies, and upgrade posture across Aura | repo audit, platform library inventory, architecture review | Aura dependency graph is rationalized and documented |
| 100 | Execute full disaster-recovery, chaos, and failover quarter for mature product | run mature-product resilience drills across AEP, Data Cloud, and key user journeys | recovery suite, shared/o11y, shared/aep, shared/data-cloud | mature product passes resilience drills against target objectives |
| 101 | Complete full model-governance audit | verify dataset cards, model cards, fairness artifacts, rollback plans, and deletion policy | ML governance artifacts, product review, shared AI registry | all active model families have production-ready governance artifacts |
| 102 | Close Month 24 KPI gaps on trust, regret, assistant completion, and analytics revenue | execute focused remediation on any KPI still below committed target | leadership review backlog, product and GTM ops, ML evaluation | KPI gaps are closed or have approved remediation plans |
| 103 | Run full-product readiness review across product, trust, ops, partner analytics, and community | perform integrated go/no-go review for the Month 24 product state | all governing docs, test suites, runbooks, dashboards | full product reaches Month 24 release readiness |
| 104 | Complete 24-month retrospective and next-horizon roadmap | document lessons, extraction decisions, and the next planning horizon with owners | strategy docs, architecture review, roadmap artifacts | retrospective and next-horizon plan are approved and actionable |
