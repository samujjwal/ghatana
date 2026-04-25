# AEP UI/UX Audit and Remediation Blueprint

Date: 2026-04-24  
Product: `products/aep`  
Audit target: AEP control plane UI and UX-affecting implementation  
Primary evidence: source code, current docs, live browser inspection, targeted Vitest, Playwright E2E/a11y runs, product-truth verification, TypeScript check.

## 1. Executive Summary

AEP currently has the structure of a serious operator control plane, but the user experience is not production-ready. The product is meaningfully broader than a prototype: it includes operate, build, learn, govern, catalog, review, memory, marketplace, workflow template, and cost surfaces. However, the current implementation has several critical gaps where the UI appears complete while the operational behavior is incomplete, misleading, or broken.

The most severe finding is that the live app renders unstyled. `products/aep/ui/src/main.tsx` does not import any CSS, and `rg --files products/aep/ui/src | rg 'css$|scss$|less$'` found no stylesheet in `src`. In the in-app browser at `http://127.0.0.1:3001/`, `/login` and `/operate` rendered as raw HTML with default typography and oversized SVGs. This invalidates much of the visual design quality, perceived polish, and accessibility confidence.

Completeness is also materially incomplete. Core flows exist but often do not close correctly: pipeline edit links route back to the list instead of opening the builder, workflow template instantiation opens the same broken edit route, agent detail deep links are generated but immediately redirected away, run cancellation and agent deregistration lack governed confirmation/reason/audit flows, and there is no unified operation center for async work such as runs, reflection, template instantiation, publish, cancellation, and governance checks.

Simplicity is mixed. The navigation has improved into outcome groups - Operate, Build, Learn, Govern, Catalog - but many flows still expose implementation concepts directly. Pipeline building remains drag/drop/manual-first rather than intent-first. Learning and memory are taxonomy-driven instead of question-driven. Governance is centralized on a dashboard rather than being embedded at risky action points. AI appears as visible panels and "AI" labels more than as quiet reduction of user work.

Correctness has multiple high-risk failures. `getEditPipelineUrl()` returns `/build/pipelines?id=...`, but the app route `/build/pipelines` renders the list page, not the builder. `PipelineBuilderPage` saves and validates without passing the active tenant, so save/validate can default to the wrong tenant while run uses the selected tenant. Memory Explorer accepts an agent selector, but the Episodes tab ignores the selected agent. The monitoring dashboard can show `Checked Invalid Date` and zero KPIs while data is loading or unavailable, which creates false operational certainty.

Consistency is not under control. Route helpers claim to be the canonical route source, but helpers and `App.tsx` disagree. The design system is only partly adopted: `DESIGN_SYSTEM_ADOPTION.md` reports only 2 design-system imports and about 64 raw form/control elements still needing migration. Icons mix manual SVGs, Unicode symbols, and emojis. Error retry patterns range from `refetch()` to `window.location.reload()`. Confirmation patterns range from custom modals to browser `confirm()` and `prompt()`.

Trust, privacy, security, and observability are present as concepts but not yet mature as pervasive UX qualities. Platform sign-in is now the visible default, but login copy still centers JWT language, the SSO callback did not reliably update authenticated state in live browser testing, and E2E tests still expect the legacy JWT paste and `localStorage` storage model. Audit logging falls back to storing audit events in `localStorage`, including sensitive audit metadata. Governance panels expose some evidence, but many sensitive actions still bypass explicit risk, impact, or audit visibility.

Overall production-readiness: Critical risk. AEP has many valuable surfaces and backend-adjacent concepts, but the current front-end experience has release-blocking issues in styling, route correctness, auth flow correctness, tenant correctness, action safety, async visibility, and design-system consistency.

## 2. Deep Audit Scorecard

| Area | Rating | Evidence and rationale |
|---|---:|---|
| Completeness | Critical | Major screens exist, but core flows are incomplete: edit pipeline, open instantiated template, agent deep link, async job center, onboarding, sensitive action recovery, and role-specific experiences are missing or partial. |
| Simplicity | High | Outcome navigation helps, but builder, governance, learning, marketplace, and memory still expose too many concepts and manual controls. Several workflows require users to infer state from scattered surfaces. |
| Correctness | Critical | Broken route helpers, tenant mismatch in save/validate, stale auth tests, invalid date display, misleading zero metrics, unhandled callback state, and memory selector mismatch directly misrepresent system state or user intent. |
| Consistency | Critical | Route source-of-truth drift, mixed components, mixed icons, mixed retry patterns, mixed confirmation patterns, and inconsistent state handling indicate systemic standards drift. |
| Information architecture | High | Top-level IA is reasonable, but Learn/Episodes redirects into Build/Patterns, Agent detail route is absent, marketplace is disconnected from registry/use, and governance is too isolated from risk points. |
| Navigation | High | Main nav is understandable, but breadcrumbs and command palette are disabled by default, deep links are broken for important entities, and some links go to broad pages instead of contextual destinations. |
| Workflow simplicity | High | Most flows are present but not streamlined. Pipeline authoring is manual-first, template instantiation is incomplete, monitoring triage is metrics-first, and review/governance workflows require manual interpretation. |
| Visual design | Critical | Live app renders unstyled because no CSS is imported. Tailwind/design-system classes do not apply, producing raw HTML and giant SVGs. |
| Interaction quality | High | Tables, drawers, forms, and dialogs exist, but several high-risk actions are one-click, use browser prompts, or lack focus management and recovery. |
| Accessibility | High | Playwright a11y suite passed 33 tests, but it seeds auth through stale `localStorage` and cannot validate the intended authenticated state reliably. Drag/drop builder lacks complete keyboard alternatives. |
| AI/ML embedded experience | High | AI suggestions exist, but they are not consistently embedded as defaults, prioritization, triage, or governance-aware automation. Confidence, fallback, override, and review triggers are not standardized. |
| Observability/transparency UX | High | SSE status, durability banner, run state, audit, and governance exist, but visibility is coarse, sometimes misleading, and not unified across async operations. |
| Privacy/security UX | High | Platform sign-in is an improvement, but copy/tests/storage are inconsistent, audit fallback uses `localStorage`, sensitive actions are not uniformly guarded, and role boundaries are unclear. |
| Responsive behavior | High | Some pages use responsive grids, but live styling is absent, builder side panels can overload small screens, and drag/drop canvas is not mobile-appropriate as a primary authoring model. |
| Perceived performance | Medium | Skeletons and loading states exist in places, but global lazy fallback is generic, async status is fragmented, and stale or unavailable data can look like successful empty data. |
| Cognitive load | High | Users must understand tenants, runs, reviews, agents, memories, patterns, templates, governance, SSE, durability, and AI suggestions with limited progressive disclosure. |
| Overall product usability | Critical | The product cannot be considered production-ready until styling, route correctness, tenant correctness, auth flow, and sensitive action safeguards are fixed. |

Severity scale used in this report:
- Critical: release-blocking or materially misleading/broken.
- High: severe workflow, trust, accessibility, correctness, or operational risk.
- Medium: meaningful usability, consistency, or completeness defect.
- Low: polish, wording, or local consistency improvement.

## 3. Complete Surface-by-Surface Audit

### 3.1 Application Shell, Styling, and Layout

Purpose: Provide the persistent product frame, navigation, theme, global error handling, auth boundary, and route outlet.

Completeness assessment: Critical gap. The shell exists through `App`, `PageShell`, `NavBar`, `ProtectedRoute`, `TenantSelector`, and `SseStatus`, but the visual system is not loaded. `main.tsx` imports React, `ErrorBoundary`, `ThemeProvider`, and `App`, but no CSS. No CSS/SCSS/LESS file exists under `src`.

Simplicity assessment: The nav groups are understandable and aligned to user outcomes. However, status and tenant controls sit in the nav without enough role/scope explanation, and secondary route discovery is weak because breadcrumbs and command palette are off by default.

Correctness assessment: Critical. Live browser showed unstyled screens. This means the UI does not match the intended hierarchy, spacing, contrast, responsive layout, or component state representation.

Consistency assessment: Critical. Design-system usage is partial. `DESIGN_SYSTEM_ADOPTION.md` states there are only 2 design-system imports and about 64 raw controls needing migration.

Issues found:
- No stylesheet import in `main.tsx`.
- No local stylesheet found in `src`.
- Manual SVG icon paths and Unicode logo/iconography in nav and toolbar.
- ThemeProvider is mounted, but token adoption is incomplete.
- Global fallback is generic `Loading...`, not product-specific or state-specific.

Recommendations:
- Add a single app stylesheet entry and verify Tailwind/design-system CSS loads in dev and build.
- Add a visual smoke test that fails when computed styles indicate raw browser defaults.
- Move nav icons, buttons, selects, inputs, tags, dialogs, skeletons, and toasts to shared components.
- Centralize app shell status patterns: tenant, session, SSE, durability, and environment.

Automation opportunities:
- Add CI screenshot or computed-style guard for login and `/operate`.
- Add design-system lint checks for raw control usage in high-risk surfaces.

Trust/security considerations:
- Shell should make tenant and session scope unmistakable without noisy technical detail.

### 3.2 Authentication and Login

Purpose: Gate AEP console access and establish a platform-issued AEP session.

Completeness assessment: Incomplete. Platform sign-in is the visible primary path, but copy, tests, callback behavior, and endpoint assumptions are inconsistent.

Simplicity assessment: The default visible action is simple: "Sign in with Platform." However, the page headline still says "Enter the AEP control plane with a platform-issued JWT", and explanatory copy still talks about bearer tokens and AEP session tokens. This forces users into implementation detail before they can enter the product.

Correctness assessment: Critical. Live callback testing with `/auth/callback?token=fake-audit-token&session=fake-audit-session&redirect=/operate` did not reliably update auth state; it initially returned/stayed at login until reload. E2E login test expects the legacy "JWT access token" textarea, but the textarea is hidden unless `LEGACY_JWT_PASTE` is enabled.

Consistency assessment: High. Auth code stores in `sessionStorage`, but Playwright tests still assert `localStorage`. Docs still mention localStorage as a completed-remediation item in older language.

Evidence:
- `LoginPage.tsx` lines 72-78 center JWT/bearer-session wording.
- `LoginPage.tsx` lines 105-115 exposes platform sign-in as primary.
- `login.spec.ts` lines 21-29 expects visible "JWT access token" and `localStorage`.
- Login E2E failed: timeout waiting for `getByLabel('JWT access token')`.

Recommendations:
- Rewrite login around platform identity, tenant/session safety, and recovery only.
- Make legacy token paste a clearly secondary break-glass path with separate test coverage.
- Update E2E tests to cover platform redirect/callback and sessionStorage.
- Make `SsoCallbackPage` update auth context directly or force a safe reload after storage mutation.
- Add session expiry warning and recovery without data loss.

### 3.3 Operate - Monitoring Dashboard

Purpose: Provide operational overview of AEP runs, reviews, alerts, durability, live state, and triage.

Completeness assessment: Partially complete. The dashboard has KPIs, run table, durability banner, cost/alert links, SSE status, and run details. It lacks a unified incident/attention model, durable job history, scoped retry, and complete async/recovery representation.

Simplicity assessment: Medium-high burden. The dashboard starts with broad KPIs and banners, but operators need "what needs attention now" first. AI suggestions and durability warnings compete with basic operational status.

Correctness assessment: Critical. Live `/operate` showed `Checked Invalid Date` in the durability banner. KPI cards showed zeros while data was still loading or unavailable, which can imply a healthy empty system.

Consistency assessment: Medium. Some errors use inline retry, some routes use full reload. Run actions and review actions differ in safety and explanation.

Issues found:
- Durability checked time can render as invalid date.
- Metrics derived from currently loaded arrays can misrepresent backend truth.
- Run cancellation lacks confirmation, reason, impact preview, audit evidence, and undo/recovery.
- Bulk cancellation relies on `Promise.all` and does not represent partial failure well.
- AI suggestion treatment is row-level but still visually branded as AI rather than embedded triage.

Recommendations:
- Replace KPI-first layout with attention-first operational queue: blocked, failed, needs review, risky, stale, and recently changed.
- Use explicit loading/unavailable/degraded states instead of zero as default.
- Add cancel confirmation requiring reason and showing downstream impact.
- Create a background operations center for run actions, retries, cancellations, reflection, template instantiation, and publish.
- Deep-link cost alerts and run issues to exact filtered contexts.

### 3.4 Operate - Run Detail

Purpose: Explain one run end to end: current state, event lineage, agent decisions, policies, logs, errors, cancellation, and recovery.

Completeness assessment: Partial. Detail route exists, but feature-flagged tabs can appear as unavailable content, and failure/recovery states are not differentiated enough.

Simplicity assessment: The tab model is familiar, but users still need to assemble cause/effect from raw sections. A summary-first incident explanation is missing.

Correctness assessment: High. Error state collapses many failures into "Run not found", conflating 404, 403, network, tenant mismatch, and backend degraded states.

Consistency assessment: Medium. Cancel behavior and error handling differ from review and pipeline delete flows.

Recommendations:
- Add a run summary panel: "What happened", "Why", "What changed", "Recommended next action".
- Separate not found, access denied, tenant mismatch, backend unavailable, and timeout.
- Add cancel/retry/resume actions with confirmation, reason, audit preview, and resulting operation status.

### 3.5 Operate - HITL Review Queue

Purpose: Let humans review risky items, approve/reject with rationale, and preserve governance evidence.

Completeness assessment: Better than many surfaces. Queue, detail panel, approve note, required reject reason, and policy diff exist. Missing focus trap, focus restore, semantic diff, history, batch review governance, and richer decision provenance.

Simplicity assessment: Review queue is more direct than governance dashboard, but raw object diffs and policy payloads still require expert interpretation.

Correctness assessment: Medium. Reject reason is required in detail flow, but run table rejection uses browser `prompt`, creating inconsistency and weaker validation.

Consistency assessment: High. Review interactions are not consistent across review page and run table.

Recommendations:
- Use one shared ReviewDecisionDialog for all approve/reject actions.
- Replace raw object diff with semantic changed/added/removed summary.
- Add keyboard/focus modal compliance.
- Show model/system recommendation confidence only where it changes human effort.

### 3.6 Operate - Cost Dashboard

Purpose: Show cost health, alerts, per-pipeline spend, and optimization opportunities.

Completeness assessment: Partial. Cost data and alert surfaces exist, but alert ownership, acknowledgment, resolution, follow-up, and deep links are incomplete.

Simplicity assessment: Moderate. Cost views are understandable but still report-heavy rather than action-first.

Correctness assessment: Medium-high. Broad links to `/build/pipelines` and `/operate` lose context. Per-pipeline breakdown rows do not deep link to the affected pipeline/run.

Consistency assessment: Medium. Error recovery uses full page reload rather than scoped refetch in some cases.

Recommendations:
- Make alerts actionable: owner, impacted pipeline/run, severity, recommended action, ack/snooze/resolve.
- Deep-link every alert and row to filtered context.
- Add cost anomaly explanation and confidence thresholds.

### 3.7 Build - Pipeline List

Purpose: Browse, search, create, edit, delete, and operate pipelines.

Completeness assessment: Critical gap in edit. The list exists, but edit routing is broken because `getEditPipelineUrl(id)` returns `/build/pipelines?id=...`, and `/build/pipelines` renders `PipelineListPage`.

Simplicity assessment: The list is straightforward, but actions do not complete reliably.

Correctness assessment: Critical. Users who choose edit can remain on or return to the list route rather than entering the builder.

Consistency assessment: High. Query key patterns differ from workflow template invalidation.

Recommendations:
- Create an explicit route `/build/pipelines/:pipelineId/edit` or make `/build/pipelines?id=` render the builder.
- Add route contract tests asserting every helper resolves to the intended page.
- Add edit E2E from list row to builder loaded with the pipeline ID.
- Add delete impact preview with active runs, schedules, dependent templates, and audit log.

### 3.8 Build - Pipeline Builder

Purpose: Author, validate, save, export, and run AEP pipelines.

Completeness assessment: Partial. React Flow builder, palette, property panel, AI assistant, validation, save, export, undo/redo, run now, and dirty guard exist. Missing or incomplete: route for editing existing pipelines, tenant-correct save/validate, keyboard authoring, deployment lifecycle, validation gating, run result navigation, mobile authoring, and action history completeness.

Simplicity assessment: High burden. This is still a manual drag/drop builder. The ideal flow should be intent/template first, with AI/system drafting structure and asking the user only for uncertain values.

Correctness assessment: Critical. `handleSave` calls `savePipeline(spec)` and `handleValidate` calls `validatePipeline(spec)` without active `tenantId`, while `handleRunNow` passes `tenantId`. This creates tenant mismatch risk.

Consistency assessment: High. Toolbar uses Unicode/manual controls, palette uses emoji labels, property panel uses raw forms, and delete/undo behavior is not fully consistent.

Evidence:
- `PipelineBuilderPage.tsx` lines 213-214 calls `savePipeline(spec)`.
- `PipelineBuilderPage.tsx` lines 231-232 calls `validatePipeline(spec)`.
- `PipelineBuilderPage.tsx` lines 294-298 runs by `pipeline.id` and `tenantId`.
- `routes.ts` lines 61-64 generates edit URL under list route.

Recommendations:
- Fix route and tenant handling immediately.
- Gate Run Now behind saved, valid, tenant-confirmed pipeline state.
- After Run Now, navigate or link to run detail and create an operation record.
- Add keyboard-first authoring: add stage menu, command palette, connection list, reorder, delete with undo.
- Replace default-open AI assistant with contextual suggestions embedded in empty canvas, validation errors, and property fields.

### 3.9 Build - Pattern Studio and Learning Episodes

Purpose: Manage reusable patterns, view learning episodes, and trigger reflection/learning workflows.

Completeness assessment: Partial. Pattern lists, creation, deletion, and reflection trigger exist. Learning route redirects into pattern studio by query. Missing: validated YAML, dry run, deletion impact, reflection job progress, learning result visibility, and durable history.

Simplicity assessment: High burden. The user must understand patterns, episodes, reflection, YAML, and tabs. The product should instead answer "What did the system learn?" and "What should I apply?"

Correctness assessment: Medium-high. Tab state handling is inconsistent because helper `handleSetMainTab` can update URL but tab buttons call state directly. Reflection trigger has no visible closure.

Consistency assessment: High. Learn/Episodes is represented as a nav destination but implemented as a redirect to Build/Patterns query state.

Recommendations:
- Give Learn its own coherent surface or rename nav to match where users land.
- Convert reflection to an async operation with progress, result, audit, and retry.
- Add pattern deletion confirmation with dependency/impact summary.
- Validate YAML before save and provide a safe preview/dry run.

### 3.10 Learn - Memory Explorer

Purpose: Let users inspect agent episodes, semantic facts, and learned policies.

Completeness assessment: Incomplete. Tabs exist, but agent scoping is inconsistent.

Simplicity assessment: Medium-high burden. The UI asks users to select an agent, then shows some data scoped and some unscoped. The mental model is not clear enough.

Correctness assessment: Critical. `EpisodesTab({ agentId })` ignores `agentId` and calls `useAllEpisodes(50)`, so selecting an agent does not filter episodes.

Consistency assessment: High. Facts are agent-scoped, policies are tenant-level by design, episodes appear scoped in the UI but are not scoped in implementation.

Evidence:
- `MemoryExplorerPage.tsx` lines 108-110 accepts `agentId` but ignores it.
- Lines 143-145 explain policies are tenant-level, but the selector remains global across the surface.

Recommendations:
- Scope episodes by selected agent or remove/disable the selector for episodes.
- Make selector scope explicit per tab.
- Add summary-first memory: key learned facts, recent changes, confidence, source episodes, and governance state.

### 3.11 Govern - Governance Dashboard

Purpose: Show compliance, policies, tenancy, audit log, privacy/security controls, and governance evidence.

Completeness assessment: Partial. Governance sections exist and are enabled by default. Missing: actionable policy workflows, links to HITL, evidence drilldown, tenant controls, audit failure states in some panels, and governance cues embedded in build/review/marketplace.

Simplicity assessment: Medium. A single governance dashboard is useful for administrators, but users need governance signals at the point of risky action.

Correctness assessment: High. Feature flag comments claim all flags default false for safety, but governance, NLQ, AI suggestions, and anomaly detection default on. Some panels return null on missing/error data, hiding trust failures.

Consistency assessment: High. Feature flag model and implementation contradict each other.

Evidence:
- `feature-flags.ts` lines 12-13 says all flags default false.
- Lines 21-35 enable compliance, tenancy, audit, NLQ, AI suggestions, and anomaly detection by default unless explicitly false.

Recommendations:
- Align feature flag policy and code. Use explicit default policy per class: safety-critical, experimental, admin-only, generally available.
- Add governance action links to affected runs, pipelines, agents, and review items.
- Use shared error/empty/loading components in all governance panels.
- Embed governance cues upstream in builder, marketplace publish, deregister, delete, and run actions.

### 3.12 Catalog - Agent Registry

Purpose: Discover registered agents, inspect capabilities/status, and manage lifecycle.

Completeness assessment: Partial. Search, cards/list, detail drawer, and deregister exist. Missing: deep-linked details, pagination/server filters, registration flow, discovery flow, ownership, data access, dependency graph, and governed deregistration.

Simplicity assessment: Medium. Registry is understandable, but CTAs are misleading: "Auto-discover services" routes to workflow catalog rather than an actual discovery operation.

Correctness assessment: High. `getAgentDetailUrl(id)` returns `/catalog/agents/:id`, but `App.tsx` redirects that path back to `/catalog/agents`.

Consistency assessment: High. The helper says an agent detail page exists, but route implementation disagrees.

Recommendations:
- Add real routeable agent detail or remove helper/deep links.
- Confirm deregister with impact, dependencies, active runs, and audit record.
- Add server-side filtering/pagination if registry can grow.
- Create a real registration/discovery workflow with progress and result states.

### 3.13 Catalog - Agent Marketplace

Purpose: Publish and discover reusable agents, reviews, tags, and marketplace metadata.

Completeness assessment: Incomplete for governed use. Publish form requires only name. Description/tags are warnings. No approval flow, data access declaration, owner, versioning, install/use action, moderation, or registry connection.

Simplicity assessment: The current form is deceptively simple. It hides important risk and governance information that users need before publishing reusable agents.

Correctness assessment: High. Optional reviewer/title/comment flows allow weak marketplace evidence. No duplicate/self-review/moderation checks are visible.

Consistency assessment: Medium. Marketplace trust patterns do not match governance patterns.

Recommendations:
- Add publish wizard with required owner, purpose, version, capability scope, data access, risk level, and review path.
- Add install/register/use handoff into Agent Registry.
- Add review moderation and reviewer identity rules.
- Show governance/compliance badges only when backed by evidence links.

### 3.14 Catalog - Workflow Templates

Purpose: Browse reusable workflow templates and instantiate pipelines from them.

Completeness assessment: Partial. Template list, search, instantiate, and success modal exist. Missing: parameter form, preview, version/owner/governance evidence, impact summary, deep link to created builder, correct cache invalidation, and async operation tracking.

Simplicity assessment: Search and cards are simple, but the "Open in builder" path is broken.

Correctness assessment: Critical. Success modal calls `navigate(getEditPipelineUrl(id))`, which routes to `/build/pipelines?id=...`; App renders the pipeline list there. Cache invalidation uses `['pipelines', tenantId]`, while pipeline list uses another key shape, so list refresh can be stale.

Evidence:
- `WorkflowCatalogPage.tsx` lines 160-162 invalidates `['pipelines', tenantId]`.
- Lines 286-291 navigate to `getEditPipelineUrl(id)`.
- `routes.ts` lines 61-64 returns list route with query.

Recommendations:
- Fix edit route and cache key immediately.
- Add template preview and required parameters before instantiate.
- Create an operation status for instantiation.
- Show created pipeline in list and builder reliably after success.

### 3.15 Tenant Selector and Tenant Context

Purpose: Let users view/switch active tenant.

Completeness assessment: Partial. Tenant selector and recent tenants exist. Missing: role/permission clarity, environment clarity, risk confirmation for all tenant switches, and contextual invalidation/reload behavior.

Simplicity assessment: Basic selector is simple, but tenant switching is high-risk in an operator control plane. The UI should make tenant context unmistakable and avoid accidental cross-tenant operations.

Correctness assessment: High. Some API calls do not pass tenant explicitly, creating mismatch risk. Recent tenant IDs are stored in localStorage, which can leak tenant identifiers on shared machines.

Consistency assessment: Medium. Tenant ID appears in API queries, URL-neutral app state, local storage recents, and controls, but no single state contract is obvious.

Recommendations:
- Add a visible tenant scope confirmation for high-risk actions.
- Use tenant-aware route/query invalidation standards.
- Review tenant identifier storage and retention.
- Create route/API tests that assert active tenant is passed for all tenant-scoped mutations.

### 3.16 SSE / Live Status

Purpose: Communicate real-time connection health.

Completeness assessment: Incomplete. It shows only Connecting, Live, Offline. It does not show stale data, reconnecting, auth failure, tenant mismatch, last event time, retry backoff, degraded fallback, or whether current page uses live data.

Simplicity assessment: The compact status is simple, but too coarse to support operator trust.

Correctness assessment: Critical implementation risk. `SseStatus` returns null before `useEffect` when route is not relevant, violating React hook ordering when relevance changes.

Evidence:
- `SseStatus.tsx` lines 29-34 returns before `useEffect`.
- Lines 36-48 then defines the effect.

Recommendations:
- Always call hooks; make effect no-op when route is irrelevant.
- Expand status model: live, connecting, reconnecting, stale, unavailable, unauthorized, tenant mismatch.
- Show last successful event and scoped recovery action in a tooltip or details popover.

### 3.17 Accessibility and Inclusive UX

Purpose: Ensure keyboard, screen reader, focus, contrast, scaling, and motion-safe use.

Completeness assessment: Partial. Automated a11y suite covers 11 routes and passed 33 checks, but its authenticated setup is stale and insufficient. Drag/drop builder lacks complete non-pointer authoring.

Simplicity assessment: Some pages have conventional tables/forms. Builder, dialogs, and raw object diffs create major accessibility burden.

Correctness assessment: High. Tests can pass while not reaching intended authenticated states because they seed `localStorage`, whereas auth now uses `sessionStorage`.

Evidence:
- `a11y.spec.ts` lines 38-42 seeds `localStorage`.
- `a11y.spec.ts` passed 33/33 in the current run.

Recommendations:
- Update a11y test auth to current session model and assert expected page heading before axe.
- Add focus trap/focus restore tests for dialogs/drawers.
- Add keyboard builder workflow tests: add stage, configure field, connect, validate, save.
- Add reduced motion and text scaling checks.

### 3.18 Frontend Architecture and UX-Affecting Implementation

Purpose: Assess implementation patterns that shape UX reliability.

Completeness assessment: Partial. There are route helpers, API modules, query hooks, feature flags, and tests, but they do not yet enforce product truth.

Simplicity assessment: Implementation complexity leaks into UX through route drift, auth drift, feature flag drift, tenant drift, and query key drift.

Correctness assessment: Critical. Product truth verification passed 41 checks with 0 issues but missed live CSS failure, route helper drift, test drift, and broken edit handoffs.

Consistency assessment: Critical. Shared abstractions exist but are not consistently used.

Verification evidence:
- `pnpm --filter @aep/ui run verify:truth`: passed, 41 checks, 0 issues.
- `pnpm --filter @aep/ui exec tsc --noEmit`: passed.
- Targeted Vitest: 8 files, 89 tests passed, command exited non-clean due unhandled `TypeError: Body is unusable: Body has already been read` from auth bootstrap/http-client.
- `e2e/login.spec.ts`: failed due stale JWT textarea expectation.
- `e2e/a11y.spec.ts`: passed 33 tests but uses stale localStorage auth setup.

Recommendations:
- Add route-helper contract tests that mount the router and assert target page.
- Add CSS presence and visual smoke tests.
- Add tenant-scoped mutation tests.
- Fix auth bootstrap double-read/unhandled error.
- Update E2E auth utilities to match current platform/session model.

## 4. Complete End-to-End Flow Review

### 4.1 First Entry and Authentication

User goal: Enter the AEP console securely and land on the requested page.

Entry point: Protected route, `/login`, platform SSO, `/auth/callback`.

Current steps:
1. User requests a protected route.
2. Protected route sends user to login.
3. Login presents platform sign-in.
4. Callback stores token/session from URL query.
5. Auth provider should recognize session and return to target.

Decision points: platform sign-in vs legacy token if enabled; target route after login.

Manual effort: Low in visible path, but implementation terms appear in copy.

Failure points:
- Callback state did not reliably update without reload in live browser testing.
- E2E coverage still asserts legacy JWT field and localStorage.
- No session expiry warning/recovery path.

Completeness gaps:
- No explicit expired-session flow.
- No identity/role display beyond session active/token.
- No safe recovery if callback fails.

Ideal future journey:
User clicks "Sign in with Platform", returns to the requested page, sees clear tenant/session context, and receives non-destructive reauth prompts before expiry.

### 4.2 Monitor and Triage Runs

User goal: Know what needs attention and resolve it safely.

Entry point: `/operate`.

Current steps:
1. View durability banner, KPIs, run table, alerts.
2. Filter/search runs.
3. Open run detail or act from table.
4. Cancel, approve, reject, or inspect AI suggestions.

Decision points: Which issue matters, which run to open, whether to trust KPIs, whether to act immediately.

Manual effort: Medium-high. Users must interpret several widgets.

Failure points:
- Invalid date in durability banner.
- Loading/unavailable data can appear as zero metrics.
- Cancel lacks reason/confirmation/audit.
- Reject may use browser prompt in table.

Ideal future journey:
Dashboard opens with an attention queue. System groups related failures, suggests cause and next action, shows confidence, and provides safe guided actions with audit and recovery.

### 4.3 Review HITL Items

User goal: Approve or reject risky work with enough context and evidence.

Entry point: `/operate/reviews` or review action from run table.

Current steps:
1. Select review item.
2. Read details, policy diff, confidence.
3. Approve with optional note or reject with required reason.

Decision points: Trust recommendation, inspect evidence, choose decision.

Manual effort: Medium. Raw diffs and technical payloads require interpretation.

Failure points:
- Dialog focus model incomplete.
- Table prompt path differs from review detail path.
- No batch decision governance.

Ideal future journey:
Review queue ranks by risk, summarizes why human input is needed, shows semantic diff and policy evidence, and applies decisions through one shared governed dialog.

### 4.4 Create Pipeline Manually

User goal: Create a valid pipeline and run it.

Entry point: `/build/pipelines/new`.

Current steps:
1. Drag stage from palette.
2. Connect/configure in property panel.
3. Validate.
4. Save.
5. Run Now.

Decision points: Which stage to add, how to configure, whether warnings matter, whether pipeline is ready.

Manual effort: High.

Failure points:
- Drag/drop lacks complete keyboard alternative.
- Save/validate omit active tenant.
- Run Now can run unsaved/invalid pipeline.
- No automatic navigation to run detail.

Ideal future journey:
User states intent or selects a template. System drafts a pipeline, prefills likely fields, highlights uncertain/risky parts, validates continuously, and creates a run with full traceability after confirmation.

### 4.5 Edit Existing Pipeline

User goal: Open a pipeline, modify it, validate, save, and understand impact.

Entry point: pipeline list row edit, workflow instantiate success, or direct URL.

Current steps:
1. Click Edit.
2. App navigates to `/build/pipelines?id=...`.
3. Router renders pipeline list, not builder.

Failure points:
- Core edit flow is broken.
- Builder has code for `pipelineId` query loading, but route does not mount builder for that URL.

Ideal future journey:
Every edit entry point opens `/build/pipelines/:id/edit`, loads the selected pipeline, shows current deployed/dirty/validation state, warns about active dependencies, and preserves recovery.

### 4.6 Instantiate Workflow Template

User goal: Turn a reusable template into a real pipeline.

Entry point: `/catalog/workflows`.

Current steps:
1. Search/browse template.
2. Click instantiate.
3. Success modal appears.
4. Click Go to list or Open in builder.

Failure points:
- Open in builder uses broken edit URL.
- Cache invalidation likely does not refresh pipeline list due query key mismatch.
- No parameter form, version preview, owner, governance, or operation tracking.

Ideal future journey:
User previews template, confirms parameters and governance impact, instantiation appears as a background operation, and success deep-links to the new builder route.

### 4.7 Learn From Patterns and Memory

User goal: Understand what agents learned and convert useful learnings into safer behavior.

Entry point: `/build/patterns`, `/learn/episodes`, `/learn/memory`.

Current steps:
1. Choose patterns/learning/memory surfaces.
2. Browse episodes, facts, policies.
3. Trigger reflection or create/delete patterns.

Failure points:
- `/learn/episodes` redirects into Build/Patterns.
- Memory episode tab ignores selected agent.
- Reflection has no visible job progress/result.
- Pattern deletion is not guarded.

Ideal future journey:
Learn is a summary-first surface: "new findings", "candidate policies", "repeated failures", "needs review", with raw events available on demand.

### 4.8 Govern and Audit

User goal: Understand compliance posture, policy state, tenant safety, and audit evidence.

Entry point: `/govern`.

Current steps:
1. View compliance, tenancy, audit, and policy sections.
2. Inspect reports/logs.
3. Manually navigate to other pages for action.

Failure points:
- Some panels hide errors by returning null.
- Policies are not actionable from governance.
- Governance cues are missing from the risky surfaces where decisions occur.

Ideal future journey:
Governance dashboard gives posture and evidence, while action-point governance appears in builder, review, marketplace, registry, cancellation, deletion, and publish flows.

### 4.9 Discover, Publish, and Manage Agents

User goal: Find, inspect, publish, register, use, or remove agents safely.

Entry point: `/catalog/agents`, `/catalog/marketplace`.

Current steps:
1. Browse registry or marketplace.
2. Open registry detail drawer.
3. Publish marketplace listing or submit review.
4. Deregister directly.

Failure points:
- Agent detail route cannot deep link.
- Marketplace publish is under-governed.
- No install/use handoff.
- Deregister lacks confirmation/impact/audit.

Ideal future journey:
Users can move from marketplace discovery to governed install/registration/use. Registry details are deep-linked, role-aware, and show dependencies, permissions, owner, health, and audit.

## 5. Comprehensive Findings Catalog

| ID | Title | Severity | Category | Affected area | Dimension | Evidence | Impact | Likely root cause | Recommended fix |
|---|---|---:|---|---|---|---|---|---|---|
| AEP-UX-001 | App renders unstyled because CSS is not imported | Critical | Visual/system | Entire UI | Correctness, consistency | `main.tsx` has no CSS import; no stylesheet in `src`; live browser showed raw HTML and oversized SVGs | Product appears broken; visual hierarchy, responsive layout, and a11y confidence collapse | Styling entry omitted during migration | Add app CSS entry, verify build includes styles, add visual smoke guard |
| AEP-UX-002 | Product-truth verification misses user-facing breakages | Critical | Test/quality | Verification | Correctness | `verify:truth` passed 41 checks with 0 issues while route, CSS, auth, and tenant defects exist | False confidence in release readiness | Checks are metadata/static and too shallow | Add route, style, auth, tenant, and live render assertions |
| AEP-UX-003 | Pipeline edit links route to list instead of builder | Critical | Routing | Pipeline list/builder | Completeness, correctness | `getEditPipelineUrl` returns `/build/pipelines?id=...`; App mounts list on `/build/pipelines` | Users cannot reliably edit existing pipelines | Route-helper drift | Add explicit edit route or mount builder for query route; test every helper |
| AEP-UX-004 | Workflow template "Open in builder" follows broken edit route | Critical | Workflow | Workflow catalog | Completeness, correctness | Success modal calls `navigate(getEditPipelineUrl(id))` | Template instantiation does not close with expected edit flow | Shared broken helper | Fix helper and add E2E from instantiate to loaded builder |
| AEP-UX-005 | Agent detail helper points to route that redirects away | High | Routing | Agent registry | Completeness, correctness | `getAgentDetailUrl` returns `/catalog/agents/:id`; App redirects that path to `/catalog/agents` | Deep links and entity sharing are impossible | Detail drawer and route model not reconciled | Add real detail route or remove helper and deep-link affordances |
| AEP-UX-006 | Feature flag safety policy contradicts implementation | High | Governance | Feature flags | Correctness, trust | Comment says all default false; several governance/AI flags default true | Experimental/trust features can appear without explicit rollout policy | Comment/code drift | Define flag classes and enforce defaults through tests |
| AEP-UX-007 | SSO callback does not reliably update auth state | Critical | Auth | Login/callback | Completeness, correctness | Live callback required reload/returned to login before reaching `/operate` | Users can fail to enter product after sign-in | Storage mutation not synchronized with AuthProvider state | Update auth context on callback or reload intentionally after safe storage |
| AEP-UX-008 | Login E2E is stale against current sign-in flow | High | Test/quality | Login | Correctness | Playwright failed waiting for `JWT access token`; field hidden by default | CI either fails or tests wrong behavior | Auth migration incomplete in tests | Rewrite login E2E for platform sign-in and legacy recovery separately |
| AEP-UX-009 | A11y test auth setup uses old localStorage model | High | Accessibility/test | E2E a11y | Correctness | `a11y.spec.ts` seeds `localStorage`; app uses sessionStorage/http-client | Passing a11y can audit wrong screens/state | Test utility drift | Seed current auth model and assert page identity before axe |
| AEP-UX-010 | Auth bootstrap/http-client can double-read response body | High | Reliability | Auth/http client | Correctness | Vitest command exited non-clean with `Body is unusable: Body has already been read` | Hidden runtime error risk during session bootstrap | Error parsing/retry path reads body twice | Normalize response parsing and add regression test |
| AEP-UX-011 | Login copy centers JWT despite platform SSO primary path | Medium | Content | Login | Simplicity, correctness | H1 says platform-issued JWT; visible CTA is platform sign-in | Users see implementation/security detail unnecessarily | Copy not updated after auth migration | Rewrite to identity/session language; move JWT to break-glass |
| AEP-UX-012 | Monitoring durability banner can show `Invalid Date` | High | Observability | `/operate` | Correctness | Live browser showed `Checked Invalid Date` | Operators cannot trust health timestamp | Missing/null timestamp formatting | Guard timestamp and show "not checked" or degraded |
| AEP-UX-013 | KPIs can show zero while data is loading/unavailable | High | Observability | `/operate` | Correctness | Live `/operate` showed zero cards with loading runs | False operational health | Derived empty arrays treated as truth | Separate loading, empty, unavailable, degraded, zero |
| AEP-UX-014 | Run cancellation lacks governed confirmation | High | Safety | Run table/detail | Trust, correctness | Cancel actions directly trigger mutation | Accidental destructive operational action | No shared sensitive-action pattern | Require reason, impact preview, audit, operation status |
| AEP-UX-015 | Rejection flow uses browser prompt in run table | Medium | Interaction | Run table | Consistency, accessibility | `prompt('Enter rejection reason:')` pattern observed in source | Poor a11y, weak validation, inconsistent governance | One-off action implementation | Use shared ReviewDecisionDialog |
| AEP-UX-016 | Run detail error says "Run not found" for broad errors | Medium | Error handling | Run detail | Correctness | Error state conflates failures | Users cannot recover correctly | Generic catch-all copy | Distinguish 404/403/network/tenant/degraded |
| AEP-UX-017 | Builder save/validate omit active tenant | Critical | Tenant correctness | Pipeline builder | Correctness, trust | Save/validate call APIs without `tenantId`; run passes it | Cross-tenant save/validate mismatch risk | API wrapper default tenant leakage | Pass tenant for all scoped mutations; add tests |
| AEP-UX-018 | Run Now is not gated by saved and valid state | High | Workflow | Pipeline builder | Correctness | Run Now directly runs `pipeline.id` | Users can run stale/invalid/unsaved definitions | Missing lifecycle state machine | Gate run behind saved+valid; show exact blocking reasons |
| AEP-UX-019 | Builder is drag/drop-first without complete keyboard path | High | Accessibility | Pipeline builder | Completeness | Palette/canvas are pointer-centric | Keyboard and assistive tech users blocked | React Flow implementation lacks alternate workflow | Add add-stage menu, command palette, connection editor |
| AEP-UX-020 | Builder mobile model is incomplete | Medium | Responsive | Pipeline builder | Completeness | Side panels/canvas heavy layout | Small-screen users face occlusion and overload | Desktop-first canvas | Add mobile authoring mode or declare desktop-only with useful fallback |
| AEP-UX-021 | Pipeline deletion lacks operational impact preview | High | Safety | Pipeline list | Trust, correctness | Delete exists but no active-run/dependency evidence | Users can break running/dependent workflows | Missing dependency model in UI | Show active runs, templates, agents, audit, typed confirm |
| AEP-UX-022 | Pattern deletion lacks confirmation/impact/audit | Medium | Safety | Pattern studio | Trust | Delete mutation direct | Learned behavior may be removed accidentally | One-off destructive action | Use shared sensitive action flow |
| AEP-UX-023 | Pattern YAML lacks validation/dry run | Medium | Validation | Pattern studio | Completeness, correctness | Create supports config editor without robust visible validation | Invalid patterns can be created or misunderstood | Raw config-first UI | Add schema validation, preview, dry run |
| AEP-UX-024 | Reflection trigger has no job progress/result closure | High | Async UX | Pattern studio | Completeness | Trigger mutation has no durable operation view | Users cannot know what happened | No background operation center | Add operation record, progress, result, retry, audit |
| AEP-UX-025 | Memory Explorer agent selector does not filter episodes | Critical | Data correctness | Memory Explorer | Correctness | `EpisodesTab` ignores `agentId` | Users see wrong agent history | Hook mismatch | Scope episodes or remove selector for that tab |
| AEP-UX-026 | Tenant switching/storage lacks full trust treatment | High | Tenant/privacy | Tenant selector | Trust, correctness | Recent tenant IDs stored locally; some API calls omit tenant | Tenant leakage and cross-tenant action risk | No tenant contract enforcement | Add tenant-scoped mutation tests and retention policy |
| AEP-UX-027 | Agent deregister lacks confirmation/impact/audit | High | Safety | Agent registry | Trust, correctness | Direct deregister action | May disrupt workflows/runs | Missing shared sensitive action | Impact preview, reason, audit, rollback guidance |
| AEP-UX-028 | Marketplace publish is under-governed | High | Governance | Agent marketplace | Completeness, trust | Name-only required publish | Unsafe reusable agents can be advertised | Marketplace treated as simple CRUD | Require owner, scope, data access, review, version, approval |
| AEP-UX-029 | Marketplace reviews lack moderation rules | Medium | Trust | Agent marketplace | Correctness | Optional reviewer/title/comment and no visible guardrails | Low-quality or misleading trust signals | Missing review model | Add identity, duplicate/self-review, moderation, evidence |
| AEP-UX-030 | Marketplace discovery does not connect to install/use | High | Workflow | Marketplace/registry | Completeness | No clear install/register/use handoff | Discovery does not complete user job | Marketplace isolated from registry | Add governed install/register/use flow |
| AEP-UX-031 | Governance tenancy/audit panels can hide failure | High | Trust | Governance | Correctness | Some panel error states return null/no data | Trust surfaces can disappear silently | Incomplete state coverage | Standardize error/loading/empty/degraded states |
| AEP-UX-032 | Governance policies are not actionable | Medium | Workflow | Governance | Completeness | Policies shown without linked actions | Admins cannot resolve from context | Dashboard-only governance | Link to HITL, affected pipelines/runs/agents |
| AEP-UX-033 | Cost alerts link to broad pages | Medium | Navigation | Cost dashboard | Simplicity, correctness | Alerts route to broad `/operate` or `/build/pipelines` | Users lose context and effort increases | Missing filtered URLs/entity links | Deep-link to affected run/pipeline/cost item |
| AEP-UX-034 | Cost alerts lack ownership and resolution lifecycle | Medium | Workflow | Cost dashboard | Completeness | No ack/snooze/resolve/owner model | Operational follow-up is manual | Alerts are report-only | Add alert workflow and history |
| AEP-UX-035 | Retry patterns are inconsistent | Medium | Interaction | Multiple pages | Consistency | Some use `refetch`, some `window.location.reload()` | Users lose context unnecessarily | No shared error component standard | Centralize retry/error boundary patterns |
| AEP-UX-036 | SSE status violates React hook ordering | Critical | Reliability | Shell | Correctness | Conditional return before `useEffect` | Runtime hook errors possible when route relevance changes | Early return before hooks | Move condition inside effect/render after hooks |
| AEP-UX-037 | SSE status is too coarse for operator trust | Medium | Observability | Shell | Completeness | Only Connecting/Live/Offline | Users cannot distinguish stale/auth/tenant/retry states | Minimal status model | Add detailed live-state model |
| AEP-UX-038 | Audit fallback stores sensitive audit events locally | High | Privacy/security | Audit service | Trust | `audit_backup_*` stored in localStorage | Sensitive operational metadata can persist client-side | Convenience fallback | Encrypt/minimize or move to secure durable retry queue |
| AEP-UX-039 | Implementation plan overstates completion | High | Product process | Docs/current app | Correctness | Plan says all P0-P2 complete; current audit finds critical blockers | Roadmap/trust decisions may be wrong | Docs not verified against live UX | Update docs with current evidence and blockers |
| AEP-UX-040 | Design system adoption is incomplete | High | Design system | Entire UI | Consistency | Adoption doc reports about 64 raw controls needing migration | Visual and behavioral drift | Partial migration | Migrate by shared component priority |
| AEP-UX-041 | No onboarding/setup/readiness flow | Medium | Completeness | First use | Completeness | No setup journey found | New tenant/user cannot understand prerequisites | Operator-assumed product | Add readiness checklist and guided setup |
| AEP-UX-042 | Role-specific cockpit is missing | High | Permissions | Whole app | Completeness, trust | All authenticated users see broad surfaces | Users may see actions beyond role context | RBAC not embedded in IA | Role-aware nav, actions, empty states, explanations |
| AEP-UX-043 | Builder AI assistant is not embedded enough | Medium | AI/ML UX | Builder | Simplicity | Assistant is a visible panel rather than quiet defaults | Adds mode and cognitive load | Add-on AI pattern | Embed suggestions in fields, validation, templates |
| AEP-UX-044 | AI assistance lacks standardized confidence/override model | High | AI/ML trust | Multiple | Correctness, trust | AI suggestions exist but governance not standardized | Users cannot judge automation safely | No AI UX contract | Define confidence, fallback, explanation, override, audit |
| AEP-UX-045 | HITL dialog lacks full modal accessibility | Medium | Accessibility | Review queue | Completeness | Aside/dialog pattern lacks focus trap/restore evidence | Keyboard users can lose context | Custom dialog | Shared accessible dialog component |
| AEP-UX-046 | Compliance signals can overstate trust without evidence links | Medium | Trust | Governance/marketplace | Correctness | Badges/statuses not always tied to evidence | False confidence | Status-only trust UX | Link every trust claim to source/evidence/audit |
| AEP-UX-047 | Agent registry claims pagination/filtering but fetches all | Medium | Scalability | Agent registry | Correctness | Hook comment says paginated/filterable; implementation client-filters | Large registries degrade and counts mislead | API/UI mismatch | Server pagination/filter contract |
| AEP-UX-048 | Generated API contracts are not consistently used | Medium | Architecture | API clients | Consistency | Handwritten clients coexist with generated contracts | Contract drift and validation inconsistency | Incomplete adoption | Generate typed clients for user-facing APIs |
| AEP-UX-049 | Browser title is too narrow | Low | Polish | App metadata | Consistency | Title is "AEP Pipeline Builder" while product is broader | Weak product orientation | Earlier builder-only scope | Rename title to AEP Control Plane |
| AEP-UX-050 | No unified operation/job center | High | Observability | Cross-product | Completeness | Runs, reflection, templates, publish, cancel lack shared status | Users cannot track background work | Async status fragmented | Add operation center with lifecycle, retry, history, audit |

## 6. Completeness Gap Inventory

Missing screens:
- Routeable agent detail page.
- Pipeline edit route that mounts the builder.
- Unified operation/job center.
- First-use onboarding and tenant readiness checklist.
- Session expiry/re-auth recovery screen.
- Dedicated learn summary surface, if Learn remains a top-level nav item.
- Marketplace install/register/use handoff.
- Governance evidence drilldown for badges, policies, and compliance claims.

Missing steps:
- Validate parameters before workflow template instantiation.
- Confirm tenant scope before high-risk mutations.
- Show impact before cancel, delete, deregister, publish, and policy changes.
- Navigate to run detail after Run Now.
- Resolve or acknowledge cost alerts.
- Review or approve marketplace publish.
- Confirm reflection job outcome.

Missing transitions:
- Login callback to authenticated context without reload ambiguity.
- Template success to loaded builder.
- Pipeline list edit to loaded builder.
- Marketplace listing to registry/use.
- Governance issue to affected run/pipeline/agent/review.
- Async action to durable progress/history.

Missing states:
- Styled UI baseline.
- Loading vs unavailable vs zero data.
- Invalid/missing durability timestamp.
- SSE stale/reconnecting/unauthorized/tenant mismatch.
- Empty but configured vs empty because backend unavailable.
- Partial bulk action failure.
- Agent selector unscoped state.
- Feature disabled but route visible state.

Missing validations:
- Tenant-scoped save/validate API calls.
- Pipeline run gating on saved and valid state.
- Pattern YAML schema validation.
- Marketplace publish data access/owner/version/risk validation.
- Template parameters before instantiate.
- Review self/duplicate/moderation checks.
- Required reason for all reject/cancel/destructive actions.

Missing guidance:
- What the user should do first after login.
- Why governance status matters at action points.
- What AI suggestions mean, confidence, and override.
- What a failed or stale SSE connection changes.
- What reflection produced and how to apply it.
- How memory facts relate to source episodes and policy outcomes.

Missing error/recovery handling:
- Auth callback failure recovery.
- Scoped refetch instead of full page reload.
- Detailed run detail errors.
- Governance panel error states.
- Audit log persistence failure UX.
- Template instantiation partial failure.
- Cancellation partial failure.

Missing supporting workflows:
- Role-aware navigation/action availability.
- Operation history and audit handoff.
- Alert acknowledgment/resolution.
- Agent lifecycle registration, discovery, install, deprecate.
- Pipeline deployment/versioning/rollback.
- Pattern dependency review and rollback.

Missing operational visibility:
- Last successful data sync per surface.
- Background job status for reflection/template/publish/cancel.
- Source evidence for compliance/trust statuses.
- State transition history for runs, reviews, policies, and agents.

Missing accessibility behavior:
- Keyboard builder authoring.
- Focus trap/restore for dialogs/drawers.
- Accessible non-drag palette alternative.
- Test coverage that reaches intended authenticated pages.
- Text scaling and reduced motion validation.

Missing trust/privacy/security surfaces:
- Session expiry and reauth.
- Audit failure visibility.
- Tenant scope confirmation and retention explanation.
- Sensitive action confirmation with reason and audit.
- Marketplace publish governance and data access disclosure.

Missing automation:
- Intent-first pipeline drafting.
- Smart parameter prefill for templates.
- Alert clustering and prioritization.
- Review queue risk ranking.
- Cost anomaly explanation.
- Memory episode summarization and policy candidate grouping.

## 7. Comprehensive Simplification Plan

Remove:
- JWT-centered login headline from the default path.
- Broken agent detail route helper or the redirect that makes it false.
- Browser `prompt()`/`confirm()` in governed flows.
- Raw object diffs as primary review content.
- Empty disabled tabs where a feature is not available unless they teach something useful.
- Broad links that lose context.

Merge:
- Review decision patterns across run table and HITL detail.
- Error/retry/loading/empty/degraded states into shared components.
- Tenant-scoped mutation handling into shared API helpers.
- Async status across runs, reflection, template instantiation, publish, cancellation, and audit retry.
- Governance cues into action surfaces rather than requiring a separate governance trip.

Hide by default:
- Raw JSON/YAML and event payloads behind "advanced" or "view source".
- AI mechanics unless confidence, reason, or governance requires visibility.
- SSE implementation details unless live state is degraded.
- Legacy JWT paste behind break-glass recovery.

Automate:
- Draft pipeline from intent/template.
- Prefill stage configs and validation rules.
- Rank runs/reviews by risk and actionability.
- Cluster repeated failures and memory episodes.
- Suggest cost remediation with confidence and expected impact.
- Route governance issues to owners.

Prefill:
- Tenant from current context in all mutations.
- Pipeline name/description from template or intent.
- Review reason suggestions based on policy failures.
- Marketplace metadata from agent manifest.
- Alert owner from pipeline/agent ownership.

Make contextual:
- Governance cues at delete, deregister, publish, run, approve, reject, and template instantiate.
- Audit evidence near the action outcome.
- AI suggestions in fields, rows, and validation messages.
- Live status details only where live data matters.

Move to advanced/admin mode:
- Raw audit filters.
- Policy payload details.
- YAML config editors.
- SSE diagnostics.
- Experimental AI/NLQ controls.

Create reusable shared patterns:
- `SensitiveActionDialog`.
- `ReviewDecisionDialog`.
- `AsyncOperationToast/Panel`.
- `EntityDeepLink`.
- `TenantScopedMutation`.
- `EvidenceBadge`.
- `ConfidenceExplanation`.
- `PageState` for loading/error/empty/degraded.

## 8. Correctness Review Register

| Correctness issue | Severity | Evidence | Required correction |
|---|---:|---|---|
| Styling absent in live app | Critical | No CSS import; live unstyled app | Add stylesheet and visual guard |
| Pipeline edit route incorrect | Critical | Helper/list route mismatch | Add real edit route and tests |
| Template open builder incorrect | Critical | Uses broken edit helper | Fix route and E2E |
| Agent detail URL incorrect | High | Helper returns redirected route | Add detail route or remove helper |
| Builder save/validate wrong tenant risk | Critical | APIs called without `tenantId` | Pass tenant and test |
| Memory agent selector ignored for episodes | Critical | `agentId` ignored | Scope query or alter UI |
| Auth callback state unreliable | Critical | Live callback required reload | Synchronize auth context |
| Login test asserts removed default UI | High | Playwright failure | Update tests |
| A11y tests seed wrong storage | High | localStorage seeding | Use sessionStorage/current auth |
| Invalid durability timestamp | High | Live `Checked Invalid Date` | Guard null/invalid timestamp |
| Zero metrics can mean loading/unavailable | High | Live zero cards while loading | State-specific metrics |
| Feature flag defaults contradict policy | High | Comment vs defaults | Align policy and code |
| SSE component violates hook order | Critical | Early return before hook | Refactor hook placement |
| Run detail errors conflated | Medium | Generic "not found" | Typed error states |
| Workflow catalog invalidates wrong query | Medium | Query key mismatch | Standardize query keys |
| Http response body double-read | High | Vitest unhandled error | Single parse path |
| Broad cost links lose context | Medium | Non-deep links | Entity-filtered routes |
| Audit fallback local storage | High | `audit_backup_*` | Secure retry model |

## 9. Consistency Review Register

| Drift type | Severity | Examples | Standard needed |
|---|---:|---|---|
| Route drift | Critical | Edit helper, agent detail helper, Learn redirect | Router contract tests and generated routes |
| Component drift | High | Raw buttons/inputs/selects, design-system partial use | Shared DS components by default |
| Icon drift | Medium | Manual SVG, Unicode, emoji | Lucide/design-system icon standard |
| Confirmation drift | High | Custom modal, prompt, confirm, direct actions | Shared sensitive-action flow |
| Error drift | Medium | `refetch()` vs `window.location.reload()` | Shared error component |
| Loading drift | Medium | Skeletons, empty states, zero cards | Shared PageState model |
| Terminology drift | Medium | JWT, platform sign-in, session token | Identity/session language standard |
| Trust drift | High | Governance dashboard vs action-point silence | EvidenceBadge and risk prompt standard |
| AI drift | High | AI panel, row suggestion, enabled flags | AI confidence/explanation standard |
| Tenant drift | Critical | Some APIs pass tenant, others default | TenantScopedMutation abstraction |
| Test drift | High | localStorage vs sessionStorage; legacy login | Shared E2E auth helper |
| Accessibility drift | High | A11y tests pass but builder incomplete | Interaction-level a11y standards |

## 10. Comprehensive AI/ML Embedding Plan

| Opportunity | Function | User value | Mode | Confidence and fallback | Governance/override | Priority |
|---|---|---|---|---|---|---:|
| Intent-first pipeline drafting | Convert user goal/template into draft pipeline | Removes manual drag/drop setup | Assist, then automate low-risk fields | High confidence autofill; low confidence ask | User reviews uncertain stages; audit accepted changes | Critical |
| Smart stage configuration | Prefill connectors, validation, enrichment | Fewer fields and errors | Assist | Show confidence per field; fallback empty | User override always available | High |
| Run triage summarization | Explain failures, likely cause, next action | Faster operations | Assist | Confidence shown; fallback raw logs | Human acts on suggestions | High |
| Review queue prioritization | Rank HITL by risk/SLA/repetition | Better focus | Assist | Low confidence keeps chronological backup | Reviewers can resort/filter | High |
| Policy diff summarization | Convert raw diff into semantic risk | Safer approvals | Assist | Always show source diff behind details | Human approval required | High |
| Cost anomaly detection | Detect unusual spend and likely drivers | Prevent overruns | Assist/monitor | Confidence and expected impact | Ack/snooze/resolve with audit | Medium |
| Template parameter inference | Fill instantiate parameters from context | Faster template use | Assist | Ask for missing/low-confidence values | User confirms before create | High |
| Memory clustering | Cluster episodes/facts into themes | Reduces raw event scanning | Assist | Show source episodes and confidence | Promote to policy only with review | Medium |
| Policy candidate generation | Suggest procedural policy from repeated events | Safer learning loop | Assist | Human review required | Full audit and rollback | Medium |
| Marketplace trust scoring | Summarize quality, usage, risk, review evidence | Safer agent reuse | Assist | No score without evidence | Explain factors and allow admin override | Medium |
| Tenant-risk warnings | Detect cross-tenant/context mismatch | Prevent mistakes | Guardrail | Hard block on high-risk mismatch | Admin override with reason | Critical |
| Async failure routing | Route failed operations to owner | Less manual follow-up | Automate low-risk notification | Fallback generic queue | Owner can reassign | Medium |

AI/ML principles for AEP:
- AI should reduce manual setup, triage, and interpretation. It should not create extra panels unless necessary.
- Confidence must be attached to actions, not decorative badges.
- High-risk automation requires human review, reason capture, and audit.
- Every accepted AI suggestion should be explainable and reversible where possible.
- Privacy-sensitive inputs must be minimized, scoped to tenant, and governed by data handling policy.

## 11. Trust / Transparency / Privacy / Security UX Review

Operational visibility:
- Add a unified operation center for all background work.
- Show last updated/checked timestamps only when valid.
- Represent loading, stale, degraded, unavailable, empty, and zero separately.
- Provide traceable state transitions for runs, reviews, policies, agents, and templates.

Auditability:
- Every sensitive action should create an audit record with actor, tenant, resource, reason, before/after, and result.
- Audit fallback should not persist sensitive metadata in localStorage without encryption/minimization and user/admin visibility.
- Trust badges must link to evidence.

Permission clarity:
- Navigation and actions should reflect role and permission.
- Disabled/restricted actions should explain required permission without revealing sensitive data.
- Tenant switching should clarify scope, permission, and consequences.

Sensitive actions:
- Cancel run, delete pipeline, delete pattern, deregister agent, publish agent, approve/reject policy, instantiate template with risky permissions, and bulk operations require a shared confirmation model.
- High-risk actions need impact preview, reason, tenant confirmation, and operation result.

Privacy controls:
- Review local storage use for tenant IDs, audit backups, and tokens.
- Keep session tokens in session storage or safer platform-managed session mechanisms.
- Show data access declarations for marketplace agents.

Role-based transparency:
- Operators need attention, recovery, and run evidence.
- Builders need validation, governance warnings, and deployment state.
- Admins need audit, compliance, permissions, and tenant safety.
- Reviewers need risk summaries, diffs, and decision history.

Safe defaults:
- Platform sign-in by default.
- Legacy token paste disabled by default and framed as recovery.
- Experimental AI/automation hidden or guarded until confidence/governance model is complete.
- Destructive actions require confirmation and reason.

Exception visibility:
- Partial failures should list succeeded, failed, retryable, and unknown items.
- Async failures should persist until acknowledged/resolved.
- Backend unavailable should never look like "zero data".

## 12. Design System / Reuse Review

Current state:
- ThemeProvider and ErrorBoundary are mounted.
- Design-system adoption document reports only 2 design-system imports and about 64 raw controls needing migration.
- Live app styling is absent, so any visual-system promise is currently not true in the browser.

Inconsistent components:
- Buttons, inputs, selects, dialogs, badges, tooltips, tables, skeletons, error states, and tabs vary across pages.
- Some pages use local core components while others use raw elements or design-system imports.

Behavior drift:
- Retry varies between `refetch()` and reload.
- Confirmation varies between custom modal, browser prompt, browser confirm, and direct mutation.
- Entity links vary between helper-generated routes, hardcoded routes, broad routes, and redirects.

Naming drift:
- Login uses JWT/platform/session wording simultaneously.
- Learn/Episodes lands in Build/Patterns.
- Agent detail exists in helpers but not routes.

Missing shared abstractions:
- Route contract source.
- Tenant-scoped API mutation wrapper.
- Sensitive action dialog.
- Review decision dialog.
- Async operation center.
- Shared page state.
- Evidence/trust badge.
- AI confidence/explanation component.

Token/style inconsistency:
- Tailwind-like class usage exists but is not loaded.
- Manual inline styles and color classes appear in builder controls.
- Emoji and Unicode icons replace standardized icons.

Standardization plan:
1. Restore global styling and verify visually.
2. Migrate shell, login, operate, builder, governance, and catalog controls to shared components.
3. Add lint/checks for raw controls in app pages.
4. Centralize route generation and test every navigation path.
5. Centralize sensitive-action, page-state, and async-operation patterns.

## 13. Prioritized Remediation Roadmap

### Immediate: release blockers

| Item | Priority | Effort | Impact | Dependencies | Owner type | Rationale |
|---|---:|---:|---:|---|---|---|
| Restore CSS/design-system styling | Critical | Medium | Critical | App styling setup | Frontend/design system | Live UI is visually broken |
| Fix pipeline edit route | Critical | Small-medium | Critical | Router/helpers | Frontend | Core build workflow broken |
| Fix template open-builder route and query invalidation | Critical | Small | High | Edit route, query keys | Frontend | Template flow does not close correctly |
| Pass tenant to builder save/validate | Critical | Small | Critical | API helper contract | Frontend/backend | Prevent cross-tenant mismatch |
| Fix SSO callback auth state and tests | Critical | Medium | Critical | Auth context | Frontend/platform | Entry flow unreliable |
| Fix SSE hook ordering | Critical | Small | High | Component refactor | Frontend | Potential runtime correctness issue |
| Update E2E auth and a11y auth helpers | High | Medium | High | Auth fixes | Frontend/QA | Current tests give false confidence |

### Short-term: operational safety and correctness

| Item | Priority | Effort | Impact | Dependencies | Owner type | Rationale |
|---|---:|---:|---:|---|---|---|
| Add shared sensitive-action dialog | High | Medium | High | Design system | Design/frontend | Standardizes cancel/delete/deregister/reject |
| Add operation center | High | High | High | Backend event/job APIs | Product/frontend/backend | Completes async workflows |
| Separate zero/loading/unavailable/degraded states | High | Medium | High | PageState component | Frontend | Stops false operational state |
| Fix Memory Explorer agent scoping | Critical | Small-medium | High | API hook | Frontend/backend | Prevents wrong data interpretation |
| Add route contract tests | High | Medium | High | Router helpers | Frontend/QA | Prevents navigation drift |
| Fix auth bootstrap body parsing | High | Small | Medium | HTTP client | Frontend | Removes hidden runtime/test error |
| Add run cancellation reason/audit | High | Medium | High | Audit API | Frontend/backend | Sensitive operation safety |

### Medium-term: simplification and embedded governance

| Item | Priority | Effort | Impact | Dependencies | Owner type | Rationale |
|---|---:|---:|---:|---|---|---|
| Build intent-first pipeline creation | High | High | High | ML/API/template model | Product/design/frontend/ML | Largest cognitive-load reduction |
| Add semantic policy diff and review explanations | High | Medium | High | Policy metadata | Design/frontend/backend | Improves review correctness |
| Add marketplace publish governance | High | High | High | Registry/governance APIs | Product/frontend/backend | Makes marketplace trustworthy |
| Add agent detail route and lifecycle workflow | High | Medium | High | Registry APIs | Frontend/backend | Completes catalog experience |
| Embed governance cues in builder/review/marketplace | High | Medium | High | EvidenceBadge | Product/design/frontend | Trust at action points |
| Add cost alert lifecycle | Medium | Medium | Medium | Cost API | Product/frontend/backend | Converts reports into workflow |

### Long-term: product maturity

| Item | Priority | Effort | Impact | Dependencies | Owner type | Rationale |
|---|---:|---:|---:|---|---|---|
| Role-specific cockpits | High | High | High | RBAC model | Product/design/frontend/platform | Reduces noise and risk |
| AI-native operations guidance | High | High | High | ML service/eval | ML/product/frontend | Moves from data display to decision support |
| Learning-to-policy workflow | Medium | High | High | Memory/policy APIs | ML/backend/frontend | Completes AEP learning loop |
| Full design-system migration | High | High | High | DS components | Design system/frontend | Durable consistency |
| Secure audit retry mechanism | High | Medium-high | High | Backend queue/platform | Security/backend/frontend | Removes localStorage audit risk |
| Responsive/mobile-specific builder strategy | Medium | High | Medium | Product decision | Design/frontend | Avoids poor canvas experience on small screens |

## 14. Final Ideal UX Vision

AEP should feel like a calm, trustworthy operator control plane that quietly handles complexity. A user lands in a styled, clean shell with unmistakable tenant, role, session, and live-state context. The first screen answers: what needs attention, what is running, what changed, and what action is safest next.

The system should do most setup work automatically. Pipeline creation should start from intent or a trusted template. AEP drafts the pipeline, prefills likely configuration, validates continuously, and asks the user only about uncertain or risky decisions. Manual canvas editing should remain available, but not be the default mental model.

Completeness is achieved through closed workflows: every create/edit/run/review/delete/publish/instantiate action has a beginning, confirmation when needed, progress, result, audit trail, and recovery. No operation should disappear into a toast-only outcome.

Cognitive load is reduced by prioritizing attention queues, summaries, and contextual controls over raw dashboards. Advanced details remain available but hidden behind progressive disclosure. Users should not need to understand SSE, reflection jobs, policy payloads, or backend session mechanics unless they are diagnosing a problem.

Correctness is ensured through route contracts, tenant-scoped API wrappers, typed state machines for lifecycle flows, and tests that verify the actual live pages. Loading, empty, degraded, stale, failed, and successful states must be visibly distinct.

Consistency is maintained by shared components and standards: one route model, one page-state model, one sensitive-action model, one review-decision model, one async-operation model, one AI confidence/explanation model, and one trust-evidence pattern.

Trust is maintained by showing the right evidence at the right moment. Governance appears at action points, not just in a dashboard. Every sensitive operation captures reason and result. Audit and compliance claims link to evidence. Privacy-sensitive data stays out of unsafe client storage.

AI/ML remains embedded and mostly invisible. Users experience fewer fields, smarter defaults, better triage, clearer summaries, and safer recommendations. AI becomes visible only when confidence, review, override, or audit context matters.

## 15. Executive Summary Lists

### Top 10 critical issues
1. App renders unstyled because CSS is not imported.
2. Pipeline edit route is broken.
3. Workflow template "Open in builder" follows broken edit route.
4. Builder save/validate omit active tenant.
5. SSO callback does not reliably update auth state.
6. Memory Explorer agent selector does not filter episodes.
7. SSE status violates React hook ordering.
8. Product-truth verification misses major UX failures.
9. Monitoring can show zero/invalid state as if it were truthful.
10. Sensitive operational actions lack governed confirmation/audit.

### Top 10 completeness gaps
1. No real pipeline edit route.
2. No routeable agent detail page.
3. No unified operation/job center.
4. No first-use onboarding/readiness.
5. No session expiry/re-auth recovery.
6. No complete keyboard pipeline authoring.
7. No marketplace install/use workflow.
8. No reflection job result/progress.
9. No cost alert lifecycle.
10. No role-specific cockpit.

### Top 10 simplification opportunities
1. Move from manual builder-first to intent/template-first pipeline creation.
2. Replace dashboard-first monitoring with attention queue.
3. Hide raw JSON/YAML/payload details by default.
4. Merge all review decisions into one shared flow.
5. Merge all sensitive actions into one shared pattern.
6. Use contextual governance cues instead of centralized-only governance.
7. Turn AI panels into field/row/action suggestions.
8. Deep-link alerts and costs to exact context.
9. Replace broad nav redirects with coherent route destinations.
10. Make Learn summary-first rather than taxonomy-first.

### Top 10 correctness issues
1. Missing CSS import.
2. `getEditPipelineUrl` route mismatch.
3. Workflow template open-builder route mismatch.
4. `getAgentDetailUrl` points to redirected route.
5. Builder save/validate tenant mismatch.
6. Memory episodes ignore selected agent.
7. Feature flag comments contradict defaults.
8. Auth tests assert old storage/login behavior.
9. Durability timestamp can render invalid date.
10. SSE hook order violation.

### Top 10 consistency issues
1. Route helpers and router disagree.
2. Design-system adoption is partial.
3. Raw controls are widespread.
4. Iconography mixes manual SVG, Unicode, and emoji.
5. Confirmation patterns vary.
6. Error retry patterns vary.
7. AI guidance patterns vary.
8. Trust evidence patterns vary.
9. Tenant handling patterns vary.
10. E2E auth helpers conflict with current auth implementation.

### Top 10 AI/ML opportunities
1. Intent-first pipeline drafting.
2. Smart stage configuration prefill.
3. Run failure summarization and next-best action.
4. HITL risk prioritization.
5. Semantic policy diff summaries.
6. Cost anomaly detection and remediation.
7. Template parameter inference.
8. Memory episode clustering.
9. Policy candidate generation.
10. Marketplace trust/risk summary.

### Top 10 trust/visibility/privacy/security improvements
1. Add sensitive-action confirmation with reason and audit.
2. Add unified operation history/status.
3. Remove audit-event localStorage fallback or secure/minimize it.
4. Add session expiry and reauth recovery.
5. Make tenant scope unmistakable and enforced.
6. Link compliance/trust badges to evidence.
7. Add marketplace publish governance and data access disclosure.
8. Distinguish stale/unavailable/loading/zero data.
9. Add role-aware navigation and action permissions.
10. Replace false green tests with route/auth/style/tenant checks.

## Appendix: Verification Notes

Commands and observed outcomes:

| Check | Outcome |
|---|---|
| `pnpm --filter @aep/ui run verify:truth` | Passed: 41 checks, 0 issues. Audit found this check is insufficient. |
| `pnpm --filter @aep/ui exec tsc --noEmit` | Passed. |
| Targeted Vitest across auth/pages/hooks/security/privacy | 89 tests passed, but command exited non-clean due unhandled `Body is unusable: Body has already been read`. |
| `pnpm --filter @aep/ui exec playwright test e2e/login.spec.ts --project=chromium --reporter=line` | Failed waiting for `JWT access token`, reflecting stale test expectations. |
| `pnpm --filter @aep/ui exec playwright test e2e/a11y.spec.ts --project=chromium --reporter=line` | Passed 33 tests, but auth seeding uses stale `localStorage`. |
| Live browser `/login` and `/operate` | Rendered unstyled; `/operate` showed `Checked Invalid Date`, zero metrics/loading runs, and raw/oversized SVG visuals. |
