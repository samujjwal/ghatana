# DMOS audit execution — commit `4b9fff1bf67dd3b75ca393eb6b40a1a7014f8a9b`

I verified the target commit and audited the Digital Marketing product against the tailored prompt using the repository state and canonical product docs available through the GitHub connector. I did **not** run local Gradle/pnpm/Playwright tests, so build/test pass claims are treated as **documented but not independently executed**. The target commit is present as `refactor 42`. 

## 1. Production readiness verdict

**Production ready: No**

**Confidence: Medium-high** for architectural/readiness findings; **medium** for test/build status because tests were inspected from code/docs/workflows but not executed.

**Direction of travel: Significantly improving, but still incomplete.**

DMOS has moved from loose scaffolding toward a coherent product: canonical docs exist, stable UI routes are identified, backend context/auth hardening exists, route/action permission concepts are present, and production bootstrap checks exist. The README explicitly describes DMOS as an AI-native, governance-first product and identifies stable vs boundary surfaces, while also admitting several partial areas: integration tests partial, UI E2E pending/partial, Google Ads partial, and privacy/security partial. 

**Main blockers**

The biggest issue is not one missing page. It is that the canonical product promise is much broader than the implemented stable product. The canonical vision says DMOS should run a governed growth lifecycle from objective → strategy → proposal/SOW/contract → onboarding → research → assets → approval → execution → lead capture/CRM → analytics/attribution → optimization → renewal, while the stable code surface is mainly dashboard, approvals, AI action log, campaigns, strategy, and budget. 

**Highest-risk area**

The highest-risk area is **execution trust**: campaign launch, connector execution, analytics, consent/privacy, and reporting must be proven end-to-end through durable workflows, real data, idempotency, rollback/kill switches, audit, and tests. Current code/docs show meaningful progress, but not enough evidence for production-grade, revenue-impacting marketing execution.

---

# 2. Root architectural blockers

## P0-1 — Product promise and implemented stable surface are not yet aligned

**Why it matters:** The canonical vision defines DMOS as a full governed growth execution OS, not just campaign CRUD plus dashboards. The stable route manifest only covers dashboard, approvals, AI actions, campaigns, strategy, and budget as stable surfaces; funnel analytics, attribution, ROI/ROAS, self-marketing, market research, advanced channels, localization, agency, and AI optimization are boundary routes. 

**Root cause:** Scope expansion is captured in canonical docs, but product runtime is still split between stable MVP surfaces and feature-gated boundary pages.

**Affected surfaces:** Full growth lifecycle, analytics, attribution, self-marketing, agency operations, customer lifecycle, sales/proposal/contract loop, lead/CRM loop.

**Target pattern:** Keep the first production pass intentionally narrow, but make the MVP loop truly complete: intake → audit/research → strategy → budget → content → approval → Google Search execution/export → lead capture → reporting → next-best action.

**Required fix:** Declare one executable MVP path as the production path and mark everything else as boundary with explicit unavailable states, backend gates, docs, and tests.

**Required tests:** One real-backend E2E for the declared MVP loop, plus tests proving boundary routes cannot masquerade as production-ready.

**Cleanup implication:** Remove “production-ready” language from boundary surfaces unless backed by real APIs, persistence, and E2E proof.

---

## P0-2 — Campaign execution architecture is still short of durable governed execution

**Why it matters:** Canonical architecture requires governed event-driven execution: recommendation → policy/compliance check → approval gate → durable command → connector/service execution → event publication → analytics/learning feedback. It also requires workflow persistence, outbox/inbox, retry, DLQ, idempotency, rollback, and kill switches. 

**Root cause:** Campaign APIs and lifecycle operations exist, but production-grade execution semantics are not fully proven across durable command execution, Google Ads connector runtime, kill switch, rollback, and analytics feedback.

**Evidence:** README marks Google Ads connector as partial despite adapter and workflow progress; it specifically notes remaining event-loop blocking mitigation and command/workflow wiring concerns. 

**Affected surfaces:** Campaign launch, Google Ads execution, connector health, campaign rollback, budget enforcement, analytics/reporting.

**Target pattern:** Campaign launch should create a durable execution command with idempotency, preflight, approval state, connector kill switch, retry/DLQ, external ID mapping, rollback plan, and auditable result.

**Required fix:** Make launch/connector execution a first-class durable workflow, not just a servlet/service mutation.

**Required tests:** Workflow replay, duplicate idempotency, connector timeout, rate limit, DLQ, kill switch, rollback, missing approval, missing conversion tracking, budget exceeded.

**Cleanup implication:** Remove or feature-flag any launch path that updates internal state without proving external execution safety.

---

## P0-3 — Analytics, attribution, and ROI/ROAS are boundary surfaces, not production reporting

**Why it matters:** DMOS success depends on measurable outcomes: funnel progression, ROI/ROAS, attribution, cost per lead/acquisition, and optimization. Canonical vision explicitly requires real source data, freshness, and confidence labels for analytics/reporting. 

**Root cause:** Reporting/analytics pages are route-manifested as boundary lifecycle routes, while the dashboard still mixes API-derived values with UI-side aggregation and derived presentation.

**Evidence:** The route manifest marks funnel analytics, attribution, ROI/ROAS, AI optimization, self-marketing, market research, advanced channels, localization, and agency as `lifecycle: 'boundary'`. 

**Affected surfaces:** Dashboard KPI cards, budget tracking, funnel analytics, attribution, ROI/ROAS, self-marketing reporting.

**Target pattern:** Canonical analytics runtime with source events, freshness, attribution model, dashboard/report/export parity, and confidence labels.

**Required fix:** Introduce a server-side analytics/reporting contract for MVP metrics and make dashboard consume that contract instead of deriving business-critical metrics ad hoc.

**Required tests:** Metric formula tests, dashboard/report/export parity, stale data tests, empty/partial states, attribution model tests, ROI/ROAS tests.

**Cleanup implication:** Boundary analytics pages should be unavailable or clearly marked until backed by real data.

---

## P0-4 — Privacy, consent, PII, and DSAR are not yet first-class enough for marketing data

**Why it matters:** Digital marketing handles contacts, leads, customer data, audience segments, consent, unsubscribe/suppression, and potentially sensitive customer behavior.

**Root cause:** The README explicitly states PII model and token hardening are pending: hashed identifiers, encrypted PII, DSAR, PII redaction, and key management are still listed as pending implementation. 

**Affected surfaces:** Contact/lead capture, audience targeting, email/SMS follow-up, CRM-lite, exports, AI prompts/logs, analytics, support access.

**Target pattern:** Consent and suppression should be enforced server-side before any audience sync, email/SMS follow-up, CRM export, AI use, or report/export.

**Required fix:** Implement canonical contact/consent/suppression/privacy services and force all lead/audience/content/AI/export paths through them.

**Required tests:** Consent capture/revocation, suppression enforcement, purpose mismatch, DSAR export/delete/anonymize, AI/action-log redaction, token/secret log redaction.

**Cleanup implication:** Remove any “lead/customer/audience” production claim unless it goes through the privacy model.

---

## P0-5 — Authorization is improved, but route/action/capability governance is duplicated

**Why it matters:** Authorization must be backend-enforced and consistent across UI route metadata, API routes, action permissions, capabilities, and docs.

**Evidence:** `DmosHttpContextFactory` derives identity server-side in production, requires tenant context, enforces idempotency for writes, and checks capability/action when provided.  Backend action-role mapping exists in `DmosActionPermissionRegistry`, but it manually mirrors route manifest actions. 

**Root cause:** UI route manifest, backend action registry, capability resolver, API docs, and README are separate sources that must stay synchronized manually.

**Affected surfaces:** All routes/actions, approvals, campaign operations, budget/strategy actions, boundary pages.

**Target pattern:** One generated product capability/action registry that emits both TypeScript and Java artifacts plus OpenAPI metadata.

**Required fix:** Generate route/action/capability/role metadata from a canonical manifest and validate parity in CI.

**Required tests:** Unknown action fails closed, every UI action has backend rule, every backend action has route/API coverage or documented internal-only status, disabled capability returns locked state.

**Cleanup implication:** Remove hand-maintained duplicate role/action maps once generated artifacts exist.

---

## P0-6 — AI-native behavior is visible, but not yet governed as a complete agent/runtime system

**Why it matters:** Canonical architecture says product-level agents must define capabilities, scopes, confidence model, evidence requirements, tool allowlist, budget authority, rollback, evaluation suite, prompt/model version, memory policy, audit, and telemetry. 

**Root cause:** Strategy and budget flows expose provenance and AI action logging, but the full agent contract system is not yet evident across content, market research, optimization, sales/proposal, contract, analytics, and customer success.

**Affected surfaces:** Strategy, budget, content generation, market research, AI optimization, next-best action, proposal/SOW/contract, self-marketing.

**Target pattern:** Every AI-assisted workflow should produce a traceable candidate output with evidence, policy validation, approval decision, model/prompt version, and audit entry.

**Required fix:** Implement a product-level AI action contract and enforce it across every AI-generated artifact.

**Required tests:** Prompt/version persistence, evidence presence, unsafe claim blocking, approval required for high risk, no private data leakage, AI log redaction, model provenance visible in UI.

**Cleanup implication:** Remove/flag any AI-generated output that cannot explain model, inputs, evidence, policy checks, and approval status.

---

## P1-7 — UI/UX is improving, but not yet “low cognitive load with full visibility”

**Why it matters:** Canonical product principles require low cognitive load and dashboards that show the next useful decision, not every possible control. 

**Root cause:** The dashboard aggregates many widgets, but the product still lacks a clear “next best step” workbench that guides the MVP loop from intake to measurable outcome.

**Affected surfaces:** Dashboard, campaigns, strategy, budget, approvals, AI action log.

**Target pattern:** One command-center flow: “what needs attention now,” “what can launch,” “what is blocked,” “what changed,” “what result happened,” and “what should I do next.”

**Required fix:** Add a canonical workspace readiness model and render dashboard sections from that model.

**Required tests:** Empty workspace, partially configured workspace, blocked launch, pending approval, failed connector, stale analytics, unauthorized user, keyboard navigation.

**Cleanup implication:** Remove static or weak dashboard cards that do not map to a user decision.

---

## P1-8 — Tests and release gates exist, but are not sufficient proof of production readiness

**Why it matters:** Canonical testing requires cross-layer proof across UI, API, backend, domain, persistence, connector, audit, and telemetry; it explicitly rejects fake green tests and tests that mock away the behavior under review. 

**Root cause:** README marks integration tests partial and UI E2E pending/partial. Some E2E fixtures route mocked API responses, which is acceptable for UI tests but insufficient as release evidence for real backend behavior.

**Affected surfaces:** Critical flows: login, dashboard, campaign lifecycle, strategy approval, budget approval, content generation, launch, connector-disabled/enabled, AI log, unauthorized user.

**Target pattern:** Separate test tiers: UI mocked tests for states, real-backend E2E for critical flows, API integration with Postgres, connector chaos tests, privacy/security tests.

**Required fix:** Make real-backend stable route E2E mandatory before production release, not conditional or optional.

**Required tests:** All canonical E2E journeys in `04-TESTING.md`, plus privacy, consent, DSAR, analytics, connector, and workflow replay tests.

**Cleanup implication:** Delete or archive stale audit/TODO docs once release gates encode them.

---

## P1-9 — Documentation is better, but canonical/non-canonical docs still create audit noise

**Why it matters:** Repeated audits keep rediscovering stale expectations when old prompts, audit docs, wrappers, and feature-freeze docs coexist with canonical docs.

**Root cause:** The canonical set exists under `docs/canonical`, but older entrypoint docs, API contract docs, prompts, freeze policy, audit baselines, and README readiness claims still overlap.

**Affected surfaces:** Product planning, QA, release review, audit prompts, implementation sequencing.

**Target pattern:** One canonical documentation set with all other docs either linked as current operational docs or archived.

**Required fix:** Keep `docs/canonical/00-11` as the primary source. Convert root docs to thin pointers or delete/merge them. Move prompts/audits to an archive outside canonical decision-making.

**Required tests:** Documentation lint/link check, canonical-doc matrix check, stale-audit scan.

**Cleanup implication:** Mandatory cleanup phase below.

---

# 3. Digital Marketing completeness matrix

| Surface                         | Route Registry | Scope/Auth | Domain Model | AI Workflow | Campaign/Execution Runtime | Analytics Runtime | Privacy/Consent | Tests | Cleanup |                                                 Status |
| ------------------------------- | -------------: | ---------: | -----------: | ----------: | -------------------------: | ----------------: | --------------: | ----: | ------: | -----------------------------------------------------: |
| Dashboard / command center      |              ✅ |         🟡 |           🟡 |          🟡 |                         🟡 |                🟡 |              🟡 |    🟡 |      🟡 |                                             🟡 Partial |
| Campaign lifecycle              |              ✅ |         🟡 |           🟡 |           ⚫ |                         🟡 |                🟡 |              🟡 |    🟡 |      🟡 |                                             🟡 Partial |
| Strategy generation             |              ✅ |         🟡 |           🟡 |          🟡 |                          ⚫ |                 ⚫ |              🟡 |    🟡 |      🟡 |                                             🟡 Partial |
| Budget recommendation           |              ✅ |         🟡 |           🟡 |          🟡 |                         🟡 |                🟡 |               ⚫ |    🟡 |      🟡 |                                             🟡 Partial |
| Approval workflow               |              ✅ |         🟡 |           🟡 |          🟡 |                         🟡 |                 ⚫ |              🟡 |    🟡 |      🟡 |                                      🟡 Strong partial |
| AI action log                   |              ✅ |         🟡 |           🟡 |          🟡 |                          ⚫ |                 ⚫ |              🟡 |    🟡 |      🟡 |                                             🟡 Partial |
| Content generation              |             🟡 |         🟡 |           🟡 |          🟡 |                          ⚫ |                 ⚫ |              🟡 |    🟡 |      🟡 | 🟡 Backend/API visible, UI maturity not fully verified |
| Website audit / market research |             🟡 |         🟡 |           🟡 |          🟡 |                          ⚫ |                🟡 |              🟡 |    🟡 |      🟡 |                                    🟡 Boundary/partial |
| Google Ads connector            |              ⚫ |         🟡 |           🟡 |           ⚫ |                         🟡 |                🟡 |              🟡 |    🟡 |      🟡 |                                             🟡 Partial |
| Funnel analytics                |              ✅ |         🟡 |           🔴 |           ⚫ |                          ⚫ |                🔴 |              🟡 |    🔴 |      🟡 |                          🔴 Missing production runtime |
| Attribution                     |              ✅ |         🟡 |           🔴 |           ⚫ |                          ⚫ |                🔴 |              🟡 |    🔴 |      🟡 |                          🔴 Missing production runtime |
| ROI/ROAS                        |              ✅ |         🟡 |           🔴 |           ⚫ |                          ⚫ |                🔴 |              🟡 |    🔴 |      🟡 |                          🔴 Missing production runtime |
| Self-marketing funnel           |              ✅ |         🟡 |           🔴 |          🟡 |                         🔴 |                🔴 |              🟡 |    🔴 |      🟡 |                                       🔴 Boundary only |
| Agency operations               |              ✅ |         🟡 |           🔴 |           ⚫ |                          ⚫ |                🔴 |              🔴 |    🔴 |      🟡 |                                       🔴 Boundary only |
| Consent/suppression/DSAR        |             🔴 |         🟡 |           🔴 |          🟡 |                         🟡 |                🟡 |              🔴 |    🔴 |      🟡 |                                  🔴 Production blocker |
| i18n/a11y                       |             🟡 |          ⚫ |            ⚫ |           ⚫ |                          ⚫ |                 ⚫ |               ⚫ |    🟡 |      🟡 |                                             🟡 Partial |

---

# 4. File-level gaps necessary for migration

| Area                     | Path                                                               | Gap                                                                                                                                          | Required fix                                                                                               | Required tests                                                             |
| ------------------------ | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Canonical product scope  | `products/digital-marketing/docs/canonical/00-VISION.md`           | Vision is strong and broad; implementation only covers a narrow stable subset.                                                               | Add explicit “production MVP contract” section mapping each implemented route/API to readiness.            | Canonical-doc status check.                                                |
| README status            | `products/digital-marketing/README.md`                             | README mixes “production-ready” route language with partial statuses for UI E2E, Google Ads, privacy/security, and integration tests.        | Replace readiness table with `Ready / Partial / Boundary / Not verified` evidence-based statuses.          | Docs lint and status matrix check.                                         |
| Route/action duplication | `ui/src/routeManifest.tsx`, `DmosActionPermissionRegistry.java`    | Backend action registry manually mirrors UI route/action metadata.                                                                           | Generate Java/TS route-action-capability registry from one manifest.                                       | Route/action parity tests.                                                 |
| Auth context             | `DmosHttpContextFactory.java`                                      | Strong production identity path exists, but non-production anonymous fallback remains; capability resolution uses path substring heuristics. | Keep fallback test/local-only; replace path heuristics with generated route metadata.                      | Prod-mode spoofing, missing auth, unknown capability, action denied.       |
| Analytics/dashboard      | `DashboardPage.tsx` and dashboard widgets                          | Dashboard still derives some metrics in UI; confidence/freshness must come from backend analytics/provenance.                                | Add `DashboardSummary` API contract with metric source/freshness/confidence.                               | Dashboard/report parity and stale-data tests.                              |
| Campaign lifecycle       | `CampaignsPage.tsx`, `CampaignService`, `DmosCampaignServlet`      | Campaign operations exist, but durable workflow/connector/rollback/kill-switch completeness is not fully proven.                             | Route all launch/external execution through durable workflow/outbox.                                       | Launch, pause, complete, archive, rollback, idempotency, DLQ, kill switch. |
| Budget domain            | `BudgetRecommendationService.java`                                 | Service validates numeric inputs but not full financial/marketing assumptions or analytics linkage.                                          | Persist assumptions, source strategy, model provenance, approval gates, and downstream budget enforcement. | Budget formula, approval, over-budget launch block.                        |
| API contracts            | `docs/API_CONTRACT.md`, `docs/canonical/02-API_CONTRACTS.md`       | API docs appear split and risk drift around auth headers/error envelope.                                                                     | Keep only canonical OpenAPI/contract source and generate docs from implementation.                         | OpenAPI route parity, error envelope parity, generated client build.       |
| Production bootstrap     | `ProductionBootstrapValidator.java`                                | Bootstrap validation is valuable, but kernel/plugin validation should prove actual plugin readiness, not only adapter presence.              | Add plugin health/capability probes for audit, approval, consent, feature flags, telemetry, notifications. | Production bootstrap failure tests per missing plugin.                     |
| Release gates            | `.github/workflows/dmos-release-gate.yml`                          | Release gates exist, but real-backend E2E must be mandatory for production release.                                                          | Make production branch protection require real-backend stable E2E with configured env.                     | Branch protection/runbook validation.                                      |
| Audit/prompt docs        | `products/digital-marketing/new-product-prompt.md`, old audit docs | These are not canonical and can confuse future audits.                                                                                       | Move to `docs/archive/audits/` or delete if superseded.                                                    | Stale-doc scan.                                                            |

---

# 5. Prioritized implementation sequence

## 1. Define the production MVP contract

**Goal:** Freeze one complete production loop.

**Expected outcome:** A single documented path: workspace setup/intake → audit/research → strategy → budget → content → approval → Google Search launch/export → lead capture → report → next-best action.

**Acceptance criteria:** Every step has UI route, API, backend service, persistence, authorization, audit, telemetry, and tests.

---

## 2. Generate canonical route/action/capability registry

**Goal:** Remove manual duplication between UI route manifest, backend action registry, capability resolver, API docs, and tests.

**Expected outcome:** One product manifest generates TS route data, Java action registry, OpenAPI security metadata, and test fixtures.

**Acceptance criteria:** No manually duplicated role/action map remains.

---

## 3. Harden auth, scope, tenant, workspace, and support access

**Goal:** Make backend enforcement authoritative everywhere.

**Expected outcome:** Production identity always derived server-side; all reads/writes require tenant/workspace/principal/session; cross-tenant and cross-workspace tests pass.

**Acceptance criteria:** Spoofed `X-Roles`, `X-Permissions`, `X-Principal-ID` cannot grant access in production.

---

## 4. Convert campaign launch into durable workflow execution

**Goal:** Make campaign execution trustworthy.

**Expected outcome:** Launch creates durable command/outbox record; preflight, approval, connector, retry, DLQ, kill switch, rollback, audit, telemetry all participate.

**Acceptance criteria:** Duplicate launch cannot double-execute external connector; failed connector enters retry/DLQ; rollback/kill switch are operator-visible.

---

## 5. Implement canonical analytics/reporting runtime

**Goal:** Make dashboard and reports agree.

**Expected outcome:** Backend computes dashboard summary, funnel, attribution, ROI/ROAS, budget pacing, freshness, and confidence.

**Acceptance criteria:** Dashboard values equal report/export values for the same filters.

---

## 6. Complete privacy, consent, suppression, and DSAR

**Goal:** Make marketing data safe.

**Expected outcome:** Contact/lead/audience/AI/export paths enforce consent, suppression, purpose, redaction, retention, and DSAR behavior.

**Acceptance criteria:** No email/SMS/audience sync/export/AI prompt can use contact data without valid consent/purpose.

---

## 7. Govern AI workflows end to end

**Goal:** Make AI native but trustworthy.

**Expected outcome:** Every AI output has model/prompt version, evidence, assumptions, confidence, policy checks, approval requirement, and AI action log.

**Acceptance criteria:** No generated strategy/content/budget/recommendation can be approved or executed without provenance.

---

## 8. Tighten UI/UX into a command center

**Goal:** Reduce cognitive load while preserving visibility.

**Expected outcome:** Dashboard shows next decision, blockers, readiness, risks, stale data, and actions.

**Acceptance criteria:** Empty, partial, ready, blocked, failed, stale, unauthorized, and degraded states are tested.

---

## 9. Make production release gates mandatory

**Goal:** Stop relying on documented readiness.

**Expected outcome:** CI proves Gradle checks, pnpm type/lint/build, API contract parity, Postgres integration, real-backend E2E, accessibility, security/privacy, and connector chaos.

**Acceptance criteria:** Production deployment is impossible unless all required gates pass.

---

## 10. Repository cleanup and doc consolidation

**Goal:** Stop repeated audits from stale docs and old prompts.

**Expected outcome:** Canonical docs are minimal, complete, and current; stale audit prompts and old TODOs are archived/deleted.

**Acceptance criteria:** Repo has one canonical source set and a doc matrix CI check.

---

# 6. Regression and release gates before production readiness

Minimum required gates:

1. Login/auth context: dev vs prod, missing auth, spoofed headers, session derivation.
2. Dashboard traversal from `/workspaces/:workspaceId/dashboard`.
3. Role/capability/access matrix for viewer, brand-manager, marketing-director, exec-sponsor, admin.
4. Tenant/workspace/client isolation across all APIs and reports.
5. Campaign lifecycle golden path: create → launch blocked until ready → approve → launch → pause → complete → archive → duplicate → rollback where supported.
6. Durable workflow replay: restart, retry, DLQ, idempotency, causation/correlation IDs.
7. Google Ads connector: OAuth missing/expired, success, external validation error, transient failure, rate limit, timeout, kill switch, rollback.
8. Strategy workflow: generate → provenance → submit → approve/reject.
9. Budget workflow: generate → assumptions → submit → approve → enforce spend caps.
10. Content workflow: generate → validate → version → approve.
11. Consent/privacy: consent capture/revoke, suppression, DSAR export/delete/anonymize, PII redaction.
12. Analytics/reporting: funnel, attribution, ROI/ROAS, CPL/CPA/CAC, conversion rate, budget pacing, freshness.
13. Dashboard/report/export parity.
14. AI safety: model/prompt version, evidence, unsafe claim block, approval gate, AI log redaction.
15. i18n: dates, numbers, currency, percentages, timezone, translation readiness.
16. Accessibility: keyboard, focus, modal/table/chart semantics, screen-reader labels, error announcements.
17. Mock/stub guard: no production dependency on test fixtures, local mocks, deterministic adapters, fake analytics.
18. Cleanup validation: no dead routes, stale docs, duplicate registries, duplicate report runtimes.

---

# Repository Cleanup Plan

| Priority | Classification     | Path                                                                                           | Reason                                                              | Evidence                                                                               | Safe Fix                                                   | Tests/Validation                     |
| -------: | ------------------ | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------ |
|       P0 | Merge              | `products/digital-marketing/docs/API_CONTRACT.md` → `docs/canonical/02-API_CONTRACTS.md`       | API contract sources risk drift.                                    | README/API docs/auth behavior overlap.                                                 | Keep canonical OpenAPI/contract only; generate human docs. | OpenAPI parity test.                 |
|       P0 | Replace            | `ui/src/routeManifest.tsx` + `DmosActionPermissionRegistry.java`                               | Duplicated action/role/capability metadata.                         | Both contain role/action maps.                                                         | Generate both from one manifest.                           | Route/action parity CI.              |
|       P0 | Replace            | `DmosHttpContextFactory.resolveRequiredCapability`                                             | Path substring capability detection is brittle.                     | Capability resolver is code-based path matching.                                       | Use generated route metadata.                              | Unknown/missing capability tests.    |
|       P0 | Keep, but harden   | `ProductionBootstrapValidator.java`                                                            | Important guard, but must prove plugin readiness deeply.            | Validator pattern exists but plugin validation needs stronger probes.                  | Add required plugin health checks.                         | Production bootstrap negative tests. |
|       P1 | Merge/archive      | `products/digital-marketing/docs/00-VISION.md`, `01-ARCHITECTURE.md`, `04-TESTING.md` wrappers | Thin wrappers duplicate canonical doc navigation.                   | Canonical docs exist under `docs/canonical`.                                           | Replace wrappers with pointers or remove if redundant.     | Link/doc matrix test.                |
|       P1 | Archive            | `products/digital-marketing/new-product-prompt.md`                                             | Prompt artifact, not canonical product source.                      | It is an audit/review prompt, not product spec.                                        | Move to `docs/archive/prompts/` or delete.                 | Stale prompt scan.                   |
|       P1 | Update             | `products/digital-marketing/README.md`                                                         | Readiness table overstates some areas relative to partial statuses. | README marks some dimensions ready while also listing E2E/privacy/Google Ads partial.  | Make evidence-based status table.                          | README status lint.                  |
|       P1 | Replace            | Dashboard metric derivation                                                                    | Dashboard should consume canonical backend summary.                 | Dashboard hooks/aggregation visible in inspected UI snippets.                          | Add `DashboardSummary` API.                                | Dashboard/report parity tests.       |
|       P1 | Delete/Merge       | Old audit/TODO docs under product audit paths                                                  | Repeated audits use stale TODOs.                                    | Current prompt forbids prior audits as source of truth.                                | Move to dated archive or delete.                           | Stale audit scan.                    |
|       P2 | Keep, document why | Boundary pages                                                                                 | Useful route placeholders if explicit.                              | Route manifest marks them `boundary`.                                                  | Add visible locked/unavailable state and backend gate.     | Feature-off UI/API tests.            |

## Canonical docs matrix

| Doc                                           | Keep | Merge | Archive | Delete | Notes                                                     |
| --------------------------------------------- | ---: | ----: | ------: | -----: | --------------------------------------------------------- |
| `docs/canonical/00-VISION.md`                 |    ✅ |       |         |        | Primary product vision.                                   |
| `docs/canonical/01-ARCHITECTURE.md`           |    ✅ |       |         |        | Primary architecture.                                     |
| `docs/canonical/02-API_CONTRACTS.md`          |    ✅ |     ✅ |         |        | Merge older `API_CONTRACT.md` into this.                  |
| `docs/canonical/03-UX_WORKFLOWS.md`           |    ✅ |       |         |        | Must include stable/boundary route states.                |
| `docs/canonical/04-TESTING.md`                |    ✅ |       |         |        | Primary testing standard.                                 |
| `docs/canonical/05-OPERATIONS.md`             |    ✅ |       |         |        | Must link runbooks.                                       |
| `docs/canonical/06-IMPLEMENTATION_PLAN.md`    |    ✅ |       |         |        | Keep as phased plan, not audit TODO dump.                 |
| `docs/canonical/07-MARKET_AND_POSITIONING.md` |    ✅ |       |         |        | Keep for product strategy.                                |
| `docs/canonical/08-PRODUCT_REQUIREMENTS.md`   |    ✅ |       |         |        | Should drive feature inventory.                           |
| `docs/canonical/09-FEATURE_CATALOG.md`        |    ✅ |       |         |        | Add production/boundary/test status per feature.          |
| `docs/canonical/10-DESIGN.md`                 |    ✅ |       |         |        | Keep UI/service/AI/governance design.                     |
| `docs/canonical/11-DATA_MODEL.md`             |    ✅ |       |         |        | Must include consent/contact/analytics model.             |
| `docs/API_CONTRACT.md`                        |      |     ✅ |         |        | Merge into canonical API contract.                        |
| `docs/00-VISION.md` etc wrappers              |      |     ✅ |       ✅ |        | Thin pointers only, or archive/delete.                    |
| `new-product-prompt.md`                       |      |       |       ✅ |        | Prompt artifact, not product truth.                       |
| Old audits/TODOs                              |      |       |       ✅ |      ✅ | Keep only if explicitly referenced as historical archive. |

## Final cleanup checklist

* [ ] Legacy audit prompts archived or deleted.
* [ ] Canonical docs consolidated under `docs/canonical/00-11`.
* [ ] README readiness table corrected to evidence-based statuses.
* [ ] API contract docs merged into one canonical source.
* [ ] UI/backend action-capability metadata generated from one manifest.
* [ ] Boundary pages clearly feature-gated with backend/data pending status.
* [ ] Dashboard metrics moved behind canonical backend summary/report contract.
* [ ] Production stubs/fake/demo paths removed or hard-fail behind non-prod guards.
* [ ] Privacy/PII/consent docs tied to implementation and tests.
* [ ] Release gates made mandatory for production branch protection.
* [ ] No duplicate report/analytics runtime remains.
* [ ] No product-specific logic leaks into Kernel/platform modules.
* [ ] Build/lint/typecheck/unit/integration/E2E/security/a11y gates pass before readiness claim.

## Final decision

DMOS at `4b9fff1bf67dd3b75ca393eb6b40a1a7014f8a9b` is **not production-ready**, but it is **meaningfully improving** toward the intended architecture.

The next step should not be another broad audit. It should be a focused hardening cycle around one complete production MVP loop, with mandatory real-backend release gates and cleanup of stale docs/prompts so future audits measure real progress instead of rediscovering old scope drift.
