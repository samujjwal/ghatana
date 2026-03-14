# Aura Task Execution Matrix

Version: 1.0
Date: March 2026
Primary Schedule Source: `Aura_Engineering_Sprint_Plan_6_Months.md`

For the longer-horizon sequence beyond the first 24 weeks, use
`Aura_Long_Horizon_Task_Execution_Matrix.md` together with
`Aura_Full_Product_Implementation_Plan_104_Weeks.md`.

## Purpose

This document tightens Aura's execution docs by defining, for each scheduled task:

- **What** must exist when the task is done
- **How** the task should be implemented
- **Where** the work belongs in the product, platform, data, and repo structure
- **Validation** required before the task is considered complete

If a task is active but does not have a usable row here, it is not ready for implementation.

---

## How To Use

1. Start with the schedule in `Aura_Engineering_Sprint_Plan_6_Months.md`.
2. Find the matching task ID here.
3. If the task touches AEP, Data Cloud, or shared auth/o11y, use `Aura_Shared_Platform_Integration_Spec.md`.
4. Implement against the listed `What`, `How`, and `Where`.
5. Pull the matching validation work into the test backlog before coding.
6. If the task changes, update this matrix and the test traceability doc before implementation continues.

---

## Where Notation

| Surface | Meaning |
| ------- | ------- |
| `apps/web` | Consumer-facing web flows, UI state, route handlers |
| `apps/mobile` | Mobile surfaces where parity is required |
| `apps/api` | Fastify BFF, GraphQL/REST client-facing API, user-facing modular monolith |
| `apps/core-worker` | Java worker host for ingestion, enrichment, ranking, async jobs |
| `apps/ml-inference` | Python inference boundary when a separate ML runtime is needed |
| `domains/*` | Core business modules and rules (`profile`, `catalog`, `recommendation`, `explainability`, `community`, `governance`) |
| `packages/*` | Shared contracts, UI, domain types, event schemas, utilities |
| `data/prisma` | Canonical logical schema and migrations; managed persistence still runs through Data Cloud |
| `ml/*` | Training, evaluation, feature generation, offline analysis |
| `docs/*` | Governing and implementation docs that must stay synchronized |
| `shared/aep` | Shared event communication boundary for publication, subscription, replay, and DLQ handling |
| `shared/data-cloud` | Shared data-management plane for datasets, plugins, storage lifecycle, exports, backups, and lineage |
| `shared/security` | Shared auth, security, governance, audit, and sensitive-action integration points |
| `shared/o11y` | Shared dashboards, traces, alerts, error budgets, and quality reporting |

---

## Phase 1 — Curated Me

### Sprint 1

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S01-T01` | Freeze MVP scope, supported categories, core user journeys, and success metrics | Reconcile PRD, Master spec, consumer value doc, and out-of-scope boundaries into one approved shape | `docs/Aura_PRD_v1.md`, `docs/Aura_Master_Platform_Specification.md`, `docs/Aura_Consumer_Value_Operating_Model.md`, `docs/Aura_Canonical_Platform_Specification.md` | No conflicting scope statements remain; affected test rows are traceable |
| `S01-T02` | Establish monorepo, Gitea Actions CI/CD, and baseline environments | Apply the approved repo layout, workspace tooling, build/test workflow shells, and environment conventions | `.gitea/workflows`, `apps/*`, `domains/*`, `packages/*`, `data/*`, `ml/*`, `infra/*` | CI executes baseline checks and the repo layout matches the monorepo doc |
| `S01-T03` | Lock modular-monolith boundaries and service extraction criteria | Define `api`, `core-worker`, and `ml-inference` as early deployables and keep domain seams explicit | `docs/Aura_Master_Platform_Specification.md`, `docs/Aura_System_Architecture.md`, `docs/Aura_Technical_Stack_Blueprint.md`, `docs/Aura_Monorepo_Structure.md`, `docs/Aura_C4_Architecture_Diagrams.md` | No feature task is ambiguous about its deployable and domain home |
| `S01-T04` | Define the core domain model and Prisma schema | Translate data, API, and event contracts into canonical entities, enums, and persistence rules | `data/prisma`, `packages/domain-types`, `shared/data-cloud`, `docs/Aura_Data_Architecture.md`, `docs/Aura_Database_Schema_Prisma.md` | Schema, contract, and event semantics align for products, profiles, recommendations, consents, and outcomes |
| `S01-T05` | Deliver auth and profile onboarding skeleton | Implement account/session handling plus the first declared-profile onboarding path with origin metadata | `apps/api` (`profile`, `governance`), `apps/web` onboarding routes, `packages/graphql-schema`, `data/prisma`, `shared/security` | User can sign up, log in, save initial profile fields, and see declared origin labeling |

### Sprint 2

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S02-T01` | Build catalog ingestion MVP for priority beauty categories | Implement source adapters, parsing, normalization, merge, provenance capture, and freshness scheduling | `apps/core-worker` (`catalog`), `domains/catalog`, `data/prisma`, `shared/data-cloud`, `shared/aep` | Priority catalog rows ingest with source provenance and freshness metadata |
| `S02-T02` | Add canonical product, brand, and ingredient entities | Extend schemas and merge logic so products, brands, ingredients, and relationships are queryable and deduped | `data/prisma`, `packages/domain-types`, `domains/catalog`, `docs/Aura_Knowledge_Graph.md` | Canonical entities resolve cleanly across sources and support product detail queries |
| `S02-T03` | Create a basic feed API | Expose baseline discovery/feed query with pagination, category filters, and deterministic fallback ranking | `apps/api` (`recommendation`, `catalog`), `packages/graphql-schema`, `docs/Aura_API_Contracts.md` | Feed API returns valid contract for empty, new-user, and seeded-user cases |
| `S02-T04` | Create wireframes and design system foundation | Define core layouts, accessibility baselines, tokens, and reusable primitives for onboarding, feed, and detail views | `apps/web`, `packages/ui`, `packages/design-tokens`, `docs/Aura_UI_UX_Blueprint.md` | Wireframes cover key MVP journeys and shared primitives are reused across surfaces |

### Sprint 3

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S03-T01` | Implement the product detail page | Build a product intelligence surface with canonical facts, trust markers, saved state, and recommendation context | `apps/web` product routes, `apps/api` (`catalog`, `recommendation`), `packages/ui` | Feed-to-detail flow works and surfaces evidence, provenance, and save state |
| `S03-T02` | Build save/bookmark flows | Add save/unsave mutations, persistence, and a saved-items shelf that can later inform learning | `apps/api` (`profile`, `recommendation`), `data/prisma`, `apps/web` saved-items UI | Save state persists across sessions and is tied to the relevant product and user |
| `S03-T03` | Add consent center v1 | Deliver per-scope optional consent UI, revoke flows, audit records, and enforcement hooks | `apps/web` privacy routes, `apps/api` (`governance`), `data/prisma`, `packages/graphql-schema`, `shared/security` | Grant, deny, and revoke states are visible and actually control optional processing |
| `S03-T04` | Add data export request flow | Provide self-serve export request, export assembly job, secure delivery, and status visibility | `apps/web` privacy routes, `apps/api` (`governance`), `apps/core-worker` export job, `shared/data-cloud`, `shared/security` | Export contains required user domains and enforces auth and scope rules |
| `S03-T05` | Add ingestion jobs and admin diagnostics | Expose job visibility, stale-source diagnostics, retries, and operator-friendly failure context | `apps/core-worker`, `shared/o11y`, `shared/aep`, `docs/Aura_Event_Architecture.md` | Failed or stale jobs are visible, retryable, and observable without raw log digging |

### Sprint 4

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S04-T01` | Implement ingredient analyzer v1 | Parse ingredient lists, resolve canonical ingredient entities, and produce hard exclusions and soft warnings | `domains/catalog`, `domains/recommendation`, `apps/api`, `docs/Aura_Ingredient_Knowledge_Graph.md` | Known allergens and ingredient conflicts are correctly flagged with missing-data handling |
| `S04-T02` | Add rules engine for allergen and ethical filtering | Introduce a deterministic filter layer for hard constraints before ranking | `domains/recommendation`, `apps/api`, `apps/core-worker`, `packages/domain-types` | Excluded products never rank ahead of safe/eligible products for the same user context |
| `S04-T03` | Build recommendation explanation payload shape | Standardize reason codes, evidence, confidence, and trust flags in API and event outputs | `packages/graphql-schema`, `packages/event-contracts`, `domains/explainability`, `shared/aep`, `docs/Aura_API_Contracts.md` | Clients receive a stable explanation contract for feed, detail, and compare surfaces |
| `S04-T04` | Add unit and integration tests | Translate current feature branches and contracts into automated tests before ML complexity grows | `products/aura/docs/testing`, app test suites, `.gitea/workflows` | Branch, contract, and consent paths touched so far are automated in CI |

### Sprint 5

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S05-T01` | Implement shade-matching v1 | Build canonical shade anchors, nearest-match scoring, and low-confidence abstention behavior | `domains/catalog`, `domains/recommendation`, `data/prisma`, `docs/Aura_Shade_Color_Ontology.md` | Supported complexion products return best-fit shade, alternates, and confidence status |
| `S05-T02` | Add recommendation query API | Expose per-user recommendation requests with filters, reasons, evidence, and confidence | `apps/api` (`recommendation`), `packages/graphql-schema`, `docs/Aura_API_Contracts.md` | Query supports supported filters and returns auditable recommendation payloads |
| `S05-T03` | Build personalized recommendation cards in UI | Deliver feed cards with reasons, trust labels, save/dismiss controls, and error/empty/loading states | `apps/web` feed routes, `packages/ui`, `domains/explainability` | Cards render correctly across supported states and preserve trust surfaces |
| `S05-T04` | Instrument analytics events | Emit canonical recommendation, view, and interaction events with idempotent server-side publication | `packages/event-contracts`, `apps/web`, `apps/api`, `shared/aep`, `shared/o11y`, `docs/Aura_Event_Architecture.md` | Event payloads include recommendation linkage, required fields, and no duplicated emissions |

### Sprint 6

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S06-T01` | Improve ranking heuristics | Tune deterministic ranking weights and ordering behavior using outcome-aware offline review | `domains/recommendation`, `apps/api`, `ml/evaluation`, `docs/Aura_Recommendation_Algorithms.md` | Offline evaluation improves without regressions on safety and trust checks |
| `S06-T02` | Add compare products screen | Create side-by-side comparison with normalized facts, recommendation context, and tradeoff summary | `apps/web` compare routes, `apps/api` compare query, `packages/ui` | Users can compare supported products and see meaningful differences clearly |
| `S06-T03` | Add feedback capture for view, click, save, and dismiss | Persist shallow interaction feedback tied to recommendation and user context | `apps/web`, `apps/api`, `data/prisma`, `packages/event-contracts`, `shared/data-cloud`, `shared/aep` | Feedback events are linked to recommendation IDs and deduplicated correctly |
| `S06-T04` | Add post-use outcome event contract for shade mismatch, reaction, and return | Extend structured outcome capture through persistence and learning pipelines | `packages/event-contracts`, `apps/api`, `apps/core-worker`, `shared/data-cloud`, `shared/aep`, `docs/Aura_Event_Architecture.md` | Outcome data flows from UI to API to DB to events with severity and provenance |
| `S06-T05` | Run the internal alpha | Execute a controlled internal release with trust, outcome, and support instrumentation live | feature flags, `shared/o11y`, `docs/Aura_GTM_Strategy.md`, `docs/Aura_24_Month_Strategy.md` | Alpha gate checklist is complete and all critical issues are triaged with owners |

---

## Phase 2 — Aura Insights

### Sprint 7

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S07-T01` | Add review ingestion and sentiment pipeline | Ingest review corpora, derive structured sentiment and warning signals, and preserve provenance | `apps/core-worker` (`community`), `domains/community`, `ml/training`, `data/prisma`, `shared/data-cloud`, `shared/aep` | Product-level review aggregates are available with cohort-aware provenance |
| `S07-T02` | Expand product enrichment jobs | Improve normalization for ingredients, shades, taxonomy, and freshness-sensitive product facts | `apps/core-worker` (`catalog`), `domains/catalog`, `shared/data-cloud`, `data/prisma` | Coverage and completeness improve for priority categories without merge regressions |
| `S07-T03` | Improve profile editing and inferred-attribute display | Make declared/inferred/imported attributes visible, editable, and origin-labeled | `apps/web` profile routes, `apps/api` (`profile`), `packages/ui` | Users can inspect and override inferred data with origin and history visibility |
| `S07-T04` | Harden observability and error budgets | Define SLOs, alerts, dashboards, and error-budget policy for ingestion and recommendation paths | `shared/o11y`, `.gitea/workflows`, `docs/Aura_System_Architecture.md` | Latency, freshness, and error dashboards exist with actionable alert thresholds |

### Sprint 8

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S08-T01` | Add assistant query flow | Deliver conversational intent-to-recommendation flow with safe structured orchestration | `apps/api` (`assistant`), `apps/web` assistant UI, `docs/Aura_AI_Agent_Architecture.md` | Supported assistant intents resolve into recommendation flows with fallback behavior |
| `S08-T02` | Implement search and guided recommendation prompts | Add searchable discovery and prompt templates that accelerate common user questions | `apps/api` (`catalog`, `recommendation`), `apps/web` search UI, `shared/data-cloud` vector path where relevant | Search and guided prompts return relevant, explainable candidate sets |
| `S08-T03` | Refine explanations and confidence display | Improve user wording, evidence expansion, confidence states, and low-confidence handling | `domains/explainability`, `apps/web`, `packages/ui`, `docs/Aura_Consumer_Value_Operating_Model.md` | High- and low-confidence outputs are distinguishable and readable in UX |
| `S08-T04` | Add user-facing outcome reporting in product detail and saved items | Make negative-outcome capture visible at the moments users naturally need it | `apps/web` detail and saved routes, `apps/api` (`governance`, `recommendation`), `packages/event-contracts` | Users can file structured outcome reports from both surfaces and link them to the product decision |
| `S08-T05` | Complete invite beta prep | Finalize cohort selection, support process, privacy review, trust checks, and launch checklist for the external invite cohort | feature flags, support playbooks, `docs/Aura_GTM_Strategy.md`, `docs/Aura_24_Month_Strategy.md` | Invite beta gate criteria are approved and launch instrumentation is live |

### Sprint 9

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S09-T01` | Launch the invite beta | Release to invite-only external users with support, instrumentation, and trust reviews active | `apps/web`, feature flags, onboarding flows, `shared/o11y`, GTM ops | Invite beta users onboard successfully and live dashboards/support channels are operating |
| `S09-T02` | Add experimentation framework | Create deterministic experiment assignment, exposure logging, and analysis-ready configuration | `apps/api`, `apps/web`, `packages/domain-types`, `docs/Aura_Technical_Stack_Blueprint.md` | Experiments are cohort-stable and exposures are logged for analysis and rollback |
| `S09-T03` | Tune ranking using interaction data | Use early signals to tune ranking while respecting outcome-first and fairness guardrails | `ml/evaluation`, `ml/training`, `domains/recommendation`, `apps/api` config | Tested variants do not regress outcome or trust metrics on monitored cohorts |
| `S09-T04` | Add moderation tools for review/community ingestion | Deliver internal moderation queues, actions, and auditability for user and imported community content | internal admin UI, `apps/api` (`community`, `governance`), `data/prisma` | Moderators can review, suppress, and escalate content with a complete audit trail |

### Sprint 10

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S10-T01` | Add affiliate link instrumentation | Generate and track disclosed affiliate links after ranking without affecting recommendation order | `apps/api` (`commerce`), `apps/web` feed and detail surfaces, `docs/Aura_GTM_Strategy.md` | Affiliate disclosure is visible and ranking remains unchanged with or without link generation |
| `S10-T02` | Create conversion funnel dashboards | Join recommendation, click, purchase, and outcome data into cohort-aware funnel reporting | analytics views, `shared/o11y`, `shared/data-cloud`, `apps/api` exports, `data/prisma` derived reporting | Funnel metrics are available by cohort, trust state, and entry surface |
| `S10-T03` | Run recommendation quality and safety outcome reviews | Establish a recurring review ritual for returns, reactions, confidence failures, and cohort drift | `ml/evaluation`, safety queue reporting, product review artifacts, `docs/Aura_AI_ML_Data_Operating_Model.md` | Review artifacts exist and actions are tracked into backlog or rule/model updates |
| `S10-T04` | Perform a privacy and trust UX pass | Audit disclosures, controls, low-confidence handling, and consent/trust copy against the trust contract | `apps/web`, `packages/ui`, `docs/Aura_UI_UX_Blueprint.md`, `docs/Aura_Consumer_Value_Operating_Model.md` | Critical trust surfaces pass content, accessibility, and control checks |
| `S10-T05` | Pilot selfie-based undertone inference behind explicit scoped consent | Ship an opt-in pilot for selfie-based undertone inference with separate consent, low-confidence fallback, and no requirement for core value paths | `apps/web` consented capture flow, `apps/api` (`profile`, `governance`), `apps/ml-inference`, `shared/security`, `shared/data-cloud`, `docs/Aura_AI_Engine_Design.md` | Opt-in users can use the pilot safely, consent is enforced, low-confidence cases abstain, and non-consenting users see no degraded core flow |

### Sprint 11

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S11-T01` | Optimize ingestion coverage and freshness | Raise source coverage and reduce stale-data exposure in priority categories | `apps/core-worker`, `domains/catalog`, `shared/o11y` freshness dashboards, `shared/data-cloud` | Coverage and freshness improve measurably on priority categories |
| `S11-T02` | Improve recommendation latency | Reduce p95 response time without dropping explanation, evidence, or safety guarantees | `apps/api`, `domains/recommendation`, `shared/data-cloud` cache and index paths, `data/prisma` indexes | Recommendation latency meets target and trust payloads remain intact |
| `S11-T03` | Add backlog items from beta learning | Ship the highest-impact fixes discovered during beta and connect each one to regression coverage | affected `apps/*`, `domains/*`, docs, and tests based on issue location | Top validated beta issues are closed with automated regression protection |
| `S11-T04` | Start premium packaging analysis | Define premium candidates that do not compromise core trust and safety value | product analytics, `docs/Aura_GTM_Strategy.md`, `docs/Aura_24_Month_Strategy.md`, `docs/Aura_Consumer_Value_Operating_Model.md` | Premium proposal keeps core trust/safety flows outside the paywall |

### Sprint 12

| Task ID | What | How | Where | Validation |
| ------- | ---- | --- | ----- | ---------- |
| `S12-T01` | Complete production hardening | Finalize security, rollback, dependency, incident, and support readiness for launch | `infra/*`, `.gitea/workflows`, `shared/security`, `shared/o11y`, runbooks, configs | Launch hardening checklist and incident drill results are accepted |
| `S12-T02` | Run QA sweep and regression automation | Automate the P0/P1 regression set across critical product, privacy, and recommendation flows | app test suites, `products/aura/docs/testing`, `.gitea/workflows` | Release candidate gates enforce automated regression coverage |
| `S12-T03` | Run launch readiness review | Perform the final go/no-go review across product, trust, privacy, performance, and support | PRD, GTM docs, strategy docs, dashboards, launch checklist artifacts | All blockers are closed or explicitly waived by named owners |
| `S12-T04` | Plan Phase 2 feature follow-through | Convert validated learning into next-phase backlog with implementation and test detail before coding | `docs/Aura_Product_Roadmap_Epics.md`, `docs/Aura_24_Month_Strategy.md`, this matrix, test plan | New active tasks are added to this matrix and traced to tests before implementation starts |

---

## Roadmap Decomposition Rule

For any roadmap epic that enters active planning after Sprint 12:

1. Add the epic's delivery tasks to this matrix before coding begins.
2. Define `What`, `How`, `Where`, and `Validation` for each new task.
3. Update the test traceability matrix and detailed test suites before implementation.
4. Do not begin execution from an epic title alone.
