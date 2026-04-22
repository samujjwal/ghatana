# AEP UI/UX Audit and Remediation Blueprint

Date: 2026-04-22
Product audited: `products/aep`
Audit scope: AEP only
Primary UI audited: `products/aep/ui`

## Methodology and Evidence Base

This audit is grounded in the current AEP implementation, not in roadmap or remediation claims alone. Evidence sources reviewed:

- Runtime shell and routing: `products/aep/ui/src/App.tsx`, `ui/src/main.tsx`
- Navigation and shared shell: `ui/src/components/shared/NavBar.tsx`, `TenantSelector.tsx`, `SseStatus.tsx`, `components/core/FuzzyFinder.tsx`, `components/security/ProtectedRoute.tsx`
- Core pages:
  - `pages/LoginPage.tsx`
  - `MonitoringDashboardPage.tsx`
  - `HitlReviewPage.tsx`
  - `RunDetailPage.tsx`
  - `PipelineListPage.tsx`
  - `PipelineBuilderPage.tsx`
  - `PatternStudioPage.tsx`
  - `LearningPage.tsx`
  - `MemoryExplorerPage.tsx`
  - `GovernancePage.tsx`
  - `AgentRegistryPage.tsx`
  - `AgentDetailPage.tsx`
  - `WorkflowCatalogPage.tsx`
  - `AgentMarketplacePage.tsx`
  - `CostDashboardPage.tsx`
- API and client layers: `ui/src/api/aep.api.ts`, `ui/src/api/pipeline.api.ts`, `ui/src/api/sse.ts`, `ui/src/lib/http-client.ts`, `ui/src/lib/api-client.ts`, `ui/src/lib/feature-flags.ts`
- Hooks and runtime behavior: `ui/src/hooks/usePipelineRuns.ts`, `ui/src/hooks/useHitlQueue.ts`
- Prior docs/audits:
  - `products/aep/docs/AUDIT_REPORT_2026-04-18.md`
  - `products/aep/docs/REMEDIATION_TRACKER.md`
  - `products/aep/docs-generated/03-cross-alignment-analysis/01-code-vs-docs-alignment.md`
- Test evidence:
  - `ui/src/__tests__/AepNewPages.test.tsx`
  - `ui/src/__tests__/AepPhaseThreePages.test.tsx`
  - `ui/src/__tests__/auth-flow.test.tsx`
  - `ui/src/__tests__/PipelineBuilderPage.test.tsx`
  - `ui/e2e/login.spec.ts`
  - `ui/e2e/a11y.spec.ts`

Verification performed during this audit:

- `pnpm --filter @aep/ui exec vitest run src/__tests__/AepNewPages.test.tsx src/__tests__/AepPhaseThreePages.test.tsx src/__tests__/auth-flow.test.tsx src/__tests__/PipelineBuilderPage.test.tsx`
  - Result: `4` test files passed, `62` tests passed
- `pnpm --filter @aep/ui exec playwright test e2e/login.spec.ts --project=chromium --reporter=line`
  - Result: passed
- `pnpm --filter @aep/ui exec playwright test e2e/a11y.spec.ts --project=chromium --reporter=line`
  - Result: `20` failed, `13` passed
  - Repeated issue: serious `color-contrast` failures across monitoring, HITL, pipelines, pattern studio, learning, memory, governance, agent registry, and workflow catalog

Important audit assumption:

- The Java backend was not available during browser-based accessibility runs, so many pages rendered frontend-only empty/error shell states. That does not invalidate the UX findings. It actually surfaced how the protected shell, tenant widget, SSE indicator, and page chrome behave under degraded conditions.

## 1. Executive Summary

Overall UI/UX health: `High risk, but materially more coherent than Data Cloud`

AEP has a clearer outcome-oriented top-level information architecture than Data Cloud. The shell is simpler, the main page set is more focused, and several operational surfaces are honest about disabled or unavailable capabilities. The strongest parts of the product are:

- the high-level operate/build/learn/govern/catalog grouping
- the HITL review flow
- the governance posture framing
- the memory/learning concept model
- overall consistency of page-shell composition

The weakest systemic issues are not visual clutter or page sprawl alone. They are:

- route/action mismatches that break key handoffs
- duplicated or overlapping surfaces that reintroduce fragmentation
- dead or partially enforced feature-flag and capability abstractions
- security and onboarding friction from manual JWT pasting and localStorage token storage
- repeated accessibility contrast failures across the shell
- documentation and remediation claims that overstate implementation convergence

The most important UX problem in the current AEP UI is that it looks more complete than its workflow handoffs really are. The router was simplified into outcome-based paths, but some page-level actions still navigate to legacy paths that the router no longer supports properly. That creates silent task failure in the middle of otherwise polished interfaces.

The most serious examples:

- `PipelineListPage` still navigates to `/pipelines` and `/pipelines?id=...`, while canonical routes are `/build/pipelines` and `/build/pipelines/new`
- `WorkflowCatalogPage` instantiates a template and navigates to `/pipelines/${pipelineId}`, but the router has no matching route, so the user is pushed into a fallback path instead of editing the new pipeline
- `PatternStudioPage` contains its own learning sub-flows while a separate `LearningPage` still exists, reintroducing conceptual duplication
- `AgentRegistryPage` and `AgentDetailPage` coexist even though remediation docs claim consolidation is complete

The biggest trust and safety problems are:

- pasting bearer JWTs into the UI as the primary login flow
- bearer and session tokens stored in `localStorage`
- RBAC and AI suggestion fetches bypassing the shared authenticated client
- documentation claiming remediation is complete where runtime still shows gaps

The biggest accessibility problem is systemic contrast debt in the shared shell. The Chromium a11y pass failed across most protected surfaces, with repeated serious contrast violations centered around the tenant indicator, shell text, and several active/inactive tab/button states.

Production-readiness of the product experience: `Not ready for broad external exposure without route/action reconciliation, auth UX hardening, and accessibility remediation`

## 2. Full UX Scorecard

Severity interpretation:

- `Critical`: current state can block work, mislead users, or materially erode trust
- `High`: major UX, reliability, or accessibility weakness
- `Medium`: bounded issue with meaningful impact
- `Low`: acceptable but improvable

| Area | Rating | Justification |
| --- | --- | --- |
| Information architecture | Medium | Outcome-first grouping is strong, but overlapping learning/catalog/build surfaces still create duplication. |
| Navigation | High | Top-level nav is clean, but page-level actions still point to old routes or unsupported handoffs. |
| Workflow simplicity | High | AEP reduces shell complexity well, but core creation and instantiation flows still require manual interpretation or break mid-flow. |
| Visual design | Medium | Cleaner and more consistent than many internal products, but accessibility contrast failures are widespread. |
| Interaction quality | High | Page-level affordances are polished, but several key CTAs route to the wrong place or lose task context. |
| Accessibility | Critical | Chromium a11y audit failed 20 tests, mostly serious color-contrast defects across core protected pages. |
| AI/ML embedded experience | High | AI concepts exist, but they are unevenly embedded and sometimes present as separate panels or unused components rather than workflow-native assistance. |
| Observability/transparency UX | Medium | Monitoring and governance expose useful truth, but shell-wide SSE and degraded backend behavior generate noisy, weakly contextual signals. |
| Privacy/security UX | Critical | Manual JWT paste and localStorage token storage are high-friction and high-risk patterns for a modern control plane. |
| Consistency/reuse | Medium | Page shells are consistent, but duplicate API clients, unused components, and dead feature flags show architecture drift. |
| Responsive behavior | Medium | Most pages appear structurally adaptable, but dense split-panel views and React Flow constraints suggest narrower-screen brittleness. |
| Perceived performance | Medium | Shell loads quickly, but many pages immediately issue background requests and SSE subscriptions regardless of user intent. |
| Cognitive load | High | Better than Data Cloud, but users still need to understand route aliases, duplicate learning surfaces, disabled capabilities, and infrastructure states. |
| Overall usability | High | Strong product direction, but important task paths remain unreliable enough to prevent broad confidence. |

## 3. Complete Surface-by-Surface Audit

### 3.1 Shell, Routing, Navigation, Tenant Context, and Finder

Purpose:

- Provide one operator-facing control plane for AEP across operate/build/learn/govern/catalog.

Current quality:

- Strong conceptual structure.
- Undermined by implementation drift.

Evidence and issues:

- Canonical routes in `ui/src/App.tsx` are well organized:
  - `/operate`
  - `/operate/costs`
  - `/operate/reviews`
  - `/operate/runs/:runId`
  - `/build/pipelines`
  - `/build/pipelines/new`
  - `/build/patterns`
  - `/learn/episodes`
  - `/learn/memory`
  - `/govern`
  - `/catalog/agents`
  - `/catalog/agents/:agentId`
  - `/catalog/marketplace`
  - `/catalog/workflows`
- `PipelineListPage.tsx:78-85` still navigates to `/pipelines` and `/pipelines?id=...`
- `WorkflowCatalogPage.tsx:147-149` navigates to `/pipelines/${result.pipelineId}`, which is not routed in `App.tsx`
- Wildcard route in `App.tsx` redirects unknown paths to `/operate`, so the broken template-instantiation handoff does not fail loudly; it silently dumps the user somewhere else
- `App.tsx` always renders `<Breadcrumbs />` and `<FuzzyFinder />`, even though `ui/src/lib/feature-flags.ts` defines `BREADCRUMBS` and `COMMAND_PALETTE` flags
- `FuzzyFinder.tsx` still contains `DEFAULT_FINDER_ITEMS` using old hash-style routes and pages like `#/settings`, which are not part of the current AEP router
- `TenantSelector.tsx` exposes tenant switching as a raw editable string in the global shell
- `SseStatus.tsx` subscribes on every protected page, regardless of whether the current task needs real-time updates

Why it matters:

- A clean shell loses its value if page-level actions do not respect the route model.
- Hidden fallback redirects create trust erosion because the user experiences “something happened” rather than a truthful error.
- Editable tenant IDs in the global shell are efficient for internal operators but risky and high-cognitive-load for broader audiences.

Recommendations:

- Make all page-level navigation derive from canonical route helpers.
- Fail explicitly when a route target cannot be resolved instead of silently redirecting to `/operate`.
- Gate Breadcrumbs and FuzzyFinder with actual feature flags or remove those flags.
- Replace free-form tenant editing with a validated tenant switcher if this UI is ever exposed beyond trusted internal operators.
- Only subscribe to SSE in surfaces that actually use live data.

Automation opportunities:

- Route helper generation from a typed route registry
- context-preserving deep links for instantiated templates and edit flows
- recent/frequent tenant switching with safe validation

Trust/privacy/security considerations:

- Tenant switching is powerful and dangerous; the UX should make current tenant scope unmistakable and hard to change accidentally.

### 3.2 Login

Purpose:

- Authenticate into the AEP control plane.

Current quality:

- Clear copy and visually polished.
- Operationally high-friction and security-heavy.

Evidence and issues:

- `LoginPage.tsx` requires users to paste a platform-issued JWT into a textarea
- `AuthContext.tsx` stores bearer and session tokens in browser state backed by `localStorage` through `http-client.ts`
- `http-client.ts` stores `aep-token` and `aep-session` in `localStorage`
- Playwright login flow passed, so the current mechanism works technically

Why it matters:

- This is not a modern, low-cognitive-load sign-in flow.
- Pasting long-lived tokens into the UI increases both user burden and security exposure.
- LocalStorage token persistence raises XSS and device-sharing risk.

Recommendations:

- Replace token-paste auth with redirect-based platform SSO or a trusted identity handoff.
- Move session persistence away from `localStorage`.
- Make session status, expiry, and sign-out behavior more explicit.

Automation opportunities:

- Automatic session bootstrap from platform context
- silent renewal or short-lived secure session establishment

Trust/privacy/security considerations:

- This is the single largest trust and security UX weakness in the current AEP UI.

### 3.3 Monitoring Dashboard

Purpose:

- Run status, metrics, alerting, and operational action.

Current quality:

- Useful layout and sensible KPI grouping.
- Too dependent on shell-level patterns and backend availability.

Evidence and issues:

- `MonitoringDashboardPage.tsx` combines durability banner, KPI cards, chart, runs/metrics tabs, bulk actions, and AI suggestion panel
- `AiSuggestionsPanel` is always inserted in the Runs tab when AI suggestions are enabled
- Browser a11y run reported repeated serious contrast failures on `/operate`
- Playwright console also surfaced proxy errors and shell churn when backend services were unavailable

Why it matters:

- The page is directionally good, but it still makes users think about infrastructure state, consent, AI suggestions, SSE, and run operations together.
- Bulk operations are feature-flagged off by default, but the surrounding list and selection logic still increase surface complexity.

Recommendations:

- Keep the page focused on run triage first, metrics second.
- Make AI suggestions contextual to a run or metric row instead of a separate banner panel.
- Standardize degraded-backend empty/error states so the page reads as intentionally limited rather than partially broken.

### 3.4 HITL Review Queue

Purpose:

- Human-in-the-loop decision review and approval.

Current quality:

- One of the strongest flows in AEP.

Evidence and issues:

- `HitlReviewPage.tsx` gives a clean list-detail-review model
- `useHitlQueue.ts` supports SSE updates and optional smart prioritization
- The page passed keyboard-entry tests, but failed Chromium a11y checks due to serious shell-level color contrast
- Smart prioritization exists only behind a feature flag and is not strongly explained in the page UI

Why it matters:

- This is a good operational flow, but its sophistication is under-explained and hidden behind implementation detail.

Recommendations:

- Surface urgency rationale when smart prioritization is enabled.
- Keep the review queue as the reference pattern for focused, decision-heavy AEP workflows.

### 3.5 Run Detail

Purpose:

- Unified view of run overview, lineage, decisions, and policies.

Current quality:

- Strong structure.
- Too dependent on feature-flagged evidence tabs.

Evidence and issues:

- `RunDetailPage.tsx` organizes details well, but lineage, decisions, and policies each degrade into boundary panels when feature flags are disabled
- `RunDetailPage.tsx` is explicit that it will not render mock data when features are disabled, which is good
- The page depends on multiple advanced capability feeds that may or may not be present

Why it matters:

- The page is honest, but it still asks operators to understand which evidence categories are globally disabled rather than simply showing what is available.

Recommendations:

- Condense missing-evidence tabs into a single capability status strip rather than three almost-empty conceptual tabs.
- Preserve the current “no fake evidence” posture.

### 3.6 Pipeline List

Purpose:

- Browse, create, edit, and delete pipelines.

Current quality:

- Clean list management page.
- Contains one of the most important route breakages in the product.

Evidence and issues:

- `PipelineListPage.tsx:78-85` uses `/pipelines` legacy navigation for both new and edit actions
- There is no canonical edit route in `App.tsx`
- Search uses a good `TextField`, but the page remains list-centric without strong workflow state prioritization
- Chromium a11y run failed on serious contrast

Why it matters:

- The page looks fully operational, but “Edit” does not preserve task context under the current router.

Recommendations:

- Add a canonical edit path or builder state model that accepts pipeline IDs.
- Replace legacy hardcoded paths immediately.
- Distinguish clearly between draft creation, editing an existing pipeline, and viewing execution status.

### 3.7 Pipeline Builder

Purpose:

- Build and run pipelines visually.

Current quality:

- Visually promising and central to the product.
- Still too manual and underpowered relative to AEP’s product claims.

Evidence and issues:

- `PipelineBuilderPage.tsx` offers save, validate, export, undo/redo, run-now, and new
- It does not load an existing pipeline by ID or query param
- `pipeline.api.ts` includes `getPipeline`, but no current page path or builder loader uses it
- “Run now” posts a synthetic `pipeline.test-run` event to `/api/v1/events`
- Browser run emitted React Flow warnings: parent container needs width and height to render graph
- Chromium a11y run failed on serious contrast

Why it matters:

- A pipeline builder without a first-class edit/load model is incomplete as a real production workspace.
- “Run now” using synthetic events is acceptable for internal validation, but should not be mistaken for a polished production workflow.

Recommendations:

- Add canonical edit/open behavior for existing pipelines.
- Add guided setup and AI-assisted stage suggestions directly into the builder rather than relying on roadmap claims.
- Resolve React Flow container warnings before treating this as robust production UX.

Automation opportunities:

- natural-language draft creation
- stage recommendation
- configuration prefill
- topology validation
- impact/risk explanation

### 3.8 Pattern Studio

Purpose:

- Create, manage, and review patterns and learning/policy artifacts.

Current quality:

- Rich page with useful controls.
- Conceptually overloaded.

Evidence and issues:

- `PatternStudioPage.tsx` includes pattern management plus a nested “learning” mode with episodes and policies
- `LearningPage.tsx` still exists as a separate dedicated route for episodes and policies
- `REMEDIATION_TRACKER.md` claims “Add tab navigation to PatternStudio + Learning” is complete, but the product still ships both a standalone `LearningPage` and an embedded learning mode in `PatternStudioPage`
- Chromium a11y run failed on serious contrast, including tab/button states

Why it matters:

- Users now have two places to do overlapping learning/policy work.
- This undermines the otherwise clean outcome-first IA.

Recommendations:

- Choose one owner for learning and policy review:
  - either keep `LearningPage` as the canonical route and remove learning content from Pattern Studio
  - or fully collapse Learning into Pattern Studio and remove `/learn/episodes`

### 3.9 Learning

Purpose:

- Browse episodes and review/promote policies.

Current quality:

- Focused and clear.
- Duplicative.

Evidence and issues:

- `LearningPage.tsx` and `PatternStudioPage.tsx` both expose episodes/policies/reflection concepts
- Chromium a11y run failed on serious contrast

Why it matters:

- This is one of the main IA duplication points in the product.

Recommendations:

- Resolve the duplication with Pattern Studio.

### 3.10 Memory Explorer

Purpose:

- Inspect episodic, semantic, and procedural memory.

Current quality:

- Strong read model.
- Still somewhat infrastructure-forward.

Evidence and issues:

- Good tab structure and agent scoping
- Policies are tenant-level only while episodes/facts can be agent-scoped
- Chromium a11y run failed on serious contrast, including tab/button states

Why it matters:

- Conceptual model is good, but still asks users to understand memory taxonomy rather than intent-based questions like “what did this agent learn?” or “why did behavior change?”

Recommendations:

- Add higher-level summaries above raw memory type tabs.

### 3.11 Governance

Purpose:

- Policies, compliance, tenancy, audit.

Current quality:

- Strong framing and one of the most honest pages in the product.

Evidence and issues:

- Good progressive disclosure for SOC2 detail
- Multiple sections depend on feature flags and boundary panels
- Chromium a11y run failed on serious contrast

Why it matters:

- The structure is good enough to become the model for future trust surfaces, but color and shell contrast issues reduce accessibility quality.

Recommendations:

- Preserve the explicit “no placeholder data” posture.
- Pull governance cues upstream into build, review, and marketplace flows.

### 3.12 Agent Registry

Purpose:

- Browse registered agents and inspect details.

Current quality:

- Good list-detail behavior and useful empty state.

Evidence and issues:

- `AgentRegistryPage.tsx` includes an inline detail panel
- `App.tsx` still routes `/catalog/agents/:agentId` to a separate `AgentDetailPage`
- `REMEDIATION_TRACKER.md` claims “Consolidate AgentRegistry + AgentDetail pages” is complete
- Chromium a11y run failed on serious contrast

Why it matters:

- The product now supports both inline detail and dedicated detail route, increasing conceptual branching again.

Recommendations:

- Either fully commit to inline detail and remove dedicated route, or commit to dedicated route and simplify the registry panel.

### 3.13 Agent Detail

Purpose:

- Dedicated deep view of one agent.

Current quality:

- Informative, but redundant with the registry’s inline detail experience.

Evidence and issues:

- `AgentDetailPage.tsx` explicitly shows discovery-only agents as non-executable manifest placeholders
- `AgentDetailPage.tsx` still navigates back via `/agents`, relying on backward-compat redirect

Why it matters:

- The route is not wrong, but it reinforces unresolved duplication with the registry.

### 3.14 Workflow Catalog

Purpose:

- Browse workflow templates and instantiate them into pipelines.

Current quality:

- Good card-based browse surface.
- Contains the most severe broken CTA in the product.

Evidence and issues:

- `WorkflowCatalogPage.tsx:147-149` navigates to `/pipelines/${result.pipelineId}`
- `App.tsx` has no `/pipelines/:id` route
- wildcard routing sends unmatched paths to `/operate`
- Chromium a11y run failed on serious contrast

Why it matters:

- This breaks a headline “instantiate template” workflow at the moment of success.
- Users can believe the template was not created, because the UI abandons their context.

Recommendations:

- Add a canonical post-instantiation destination immediately.
- This should be treated as a `P0` UX defect.

### 3.15 Agent Marketplace

Purpose:

- Publish, inspect, and review reusable agent packs.

Current quality:

- Rich surface, but broad for the current maturity level.

Evidence and issues:

- Publishing, ratings, tenant review history, and detail pane all coexist in one page
- Marketplace publishing remains tenant-scoped until broader governance exists, which the page correctly notes
- Review inputs rely on placeholder-only fields for some content

Why it matters:

- The page is ambitious and useful, but it pushes a lot of concepts into one place:
  - publish
  - discover
  - provenance
  - ratings
  - review writing

Recommendations:

- Consider separating “publish my agent” from “review marketplace listings.”
- Add clearer guardrails around trust level, safety review, and tenant scope.

### 3.16 Cost Dashboard

Purpose:

- Operating spend and cost concentration visibility.

Current quality:

- Clear and useful.

Issues:

- Lacks next-best-action framing.
- Shows alerting and concentration, but no direct remediation or optimization handoff.

Recommendations:

- Connect cost insights back to pipeline, run, and agent actions.

## 4. Complete End-to-End Flow Review

### 4.1 Sign in and enter the control plane

User goal:

- Get into AEP and start work quickly and safely.

Current steps:

1. Open the app.
2. Paste a JWT manually.
3. Submit.
4. UI requests AEP session token.
5. User lands in the originally requested page.

Current quality:

- Works technically.
- High friction and poor security ergonomics.

Failure points:

- malformed token
- session bootstrap failure
- bearer token persistence risk

Ideal future state:

- Platform SSO or trusted redirect, then lightweight AEP session issuance with no token paste.

### 4.2 Monitor runs and respond to issues

User goal:

- See what is running, failing, or degraded.

Current steps:

1. Open `/operate`
2. Review KPIs, durability banner, runs table
3. Optionally act on AI suggestions or cancel runs
4. Open run detail

Strengths:

- Good hierarchy
- durable shell
- run detail exists

Weaknesses:

- AI suggestions are a separate noisy block
- shell-level SSE and tenant status always present
- accessibility contrast issues reduce scanability

### 4.3 Review HITL items

User goal:

- Approve or reject uncertain decisions.

Current steps:

1. Open `/operate/reviews`
2. Pick queue item
3. Review policy diff
4. Approve with note or reject with reason

Strengths:

- clear and focused
- reasoned review model
- good separation of list and detail

Weaknesses:

- urgency explanation is underexposed

### 4.4 Create a new pipeline

User goal:

- Build a useful pipeline quickly.

Current steps:

1. Open `/build/pipelines/new`
2. Add stages manually
3. Configure manually
4. Validate
5. Save
6. Optionally trigger synthetic run

Strengths:

- mature builder tools

Weaknesses:

- very manual
- no first-class AI/NL guidance in current page
- no strong “start from intent” path

Ideal future state:

- user starts from intent or template, system drafts pipeline, user reviews and edits only the uncertain parts.

### 4.5 Edit an existing pipeline

User goal:

- Reopen and modify a pipeline.

Current steps:

1. Open pipeline list
2. Click Edit
3. Page navigates using legacy route
4. Current builder does not load pipeline by ID

Severity:

- Critical

Ideal future state:

- dedicated builder load/open behavior with canonical route or query state.

### 4.6 Instantiate a workflow template

User goal:

- Start from a curated template and continue editing.

Current steps:

1. Open `/catalog/workflows`
2. Click Instantiate
3. Backend creates pipeline
4. UI navigates to unsupported `/pipelines/:id`
5. Wildcard redirect likely dumps user to `/operate`

Severity:

- Critical

Ideal future state:

- instantiate, then open the created pipeline in the builder with clear success confirmation.

### 4.7 Design patterns and review learning

User goal:

- Create or refine patterns and review learned policies.

Current state:

- Split across `PatternStudioPage` and `LearningPage`, while also partially combined

Severity:

- High duplication/cognitive load

### 4.8 Browse memory and inspect agent behavior

User goal:

- Understand what an agent learned and why.

Current quality:

- Structurally good, but still taxonomy-first rather than question-first.

### 4.9 Govern tenant behavior and audit state

User goal:

- Check policies, compliance, tenancy, and audit.

Current quality:

- Strong

Weaknesses:

- still somewhat siloed from the workflows where policy matters most.

## 5. Comprehensive Findings Catalog

### AEP-F001

- Title: Workflow template instantiation routes users to a non-existent path
- Severity: Critical
- Category: Routing / Workflow handoff
- Affected surface: Workflow Catalog → Pipeline Builder
- Evidence: `ui/src/pages/WorkflowCatalogPage.tsx:147-149`; `ui/src/App.tsx` has no `/pipelines/:id` route
- Why it matters: one of the primary product promises breaks after a successful action
- User impact: disorientation and loss of trust
- Business impact: failed template adoption and support burden
- Root cause: page action still uses legacy route semantics after router redesign
- Recommended fix: add canonical open/edit route and use it everywhere
- Expected benefit: restores one of the core AEP builder flows

### AEP-F002

- Title: Pipeline list edit/new actions still use legacy `/pipelines` paths
- Severity: Critical
- Category: Routing / Workflow handoff
- Affected surface: Pipeline List
- Evidence: `ui/src/pages/PipelineListPage.tsx:78-85`
- Why it matters: edit intent is not mapped to a current builder path
- User impact: wrong destination and lost pipeline context
- Business impact: users cannot reliably maintain existing pipelines
- Root cause: page actions were not updated with canonical outcome routes
- Recommended fix: replace with typed route helpers and implement existing-pipeline load
- Expected benefit: reliable edit flow

### AEP-F003

- Title: Pipeline Builder has no first-class open/edit path for existing pipelines
- Severity: Critical
- Category: Workflow completeness
- Affected surface: Pipeline Builder
- Evidence: `pipeline.api.ts` exposes `getPipeline`, but `PipelineBuilderPage.tsx` does not load by ID and the router has no edit route
- Why it matters: a builder without a stable reopen/edit model is incomplete
- User impact: users can create but not confidently resume/edit
- Business impact: weakens trust in the primary build experience
- Root cause: creation flow advanced faster than lifecycle flow
- Recommended fix: add canonical edit route and loader behavior
- Expected benefit: true production pipeline authoring

### AEP-F004

- Title: Pattern Studio and Learning duplicate each other’s responsibilities
- Severity: High
- Category: Information architecture
- Affected surface: Pattern Studio, Learning
- Evidence: `PatternStudioPage.tsx` includes learning episodes and policies; `LearningPage.tsx` also does
- Why it matters: duplicates concepts and destinations
- User impact: users must remember where to go for learning work
- Business impact: harder future maintenance and onboarding
- Root cause: partial consolidation
- Recommended fix: choose one canonical learning owner
- Expected benefit: lower cognitive load

### AEP-F005

- Title: Agent Registry inline detail and dedicated Agent Detail route coexist despite claimed consolidation
- Severity: High
- Category: IA / Consistency
- Affected surface: Agent Registry, Agent Detail
- Evidence: `AgentRegistryPage.tsx`, `AgentDetailPage.tsx`, `App.tsx`, and `docs/REMEDIATION_TRACKER.md`
- Why it matters: duplicates navigation patterns and undermines remediation truth
- User impact: inconsistent browse-to-detail behavior
- Business impact: maintenance and docs drift
- Root cause: consolidation work only partially applied
- Recommended fix: remove one detail model
- Expected benefit: more coherent catalog flow

### AEP-F006

- Title: Manual JWT paste is the primary login UX
- Severity: Critical
- Category: Security UX / Onboarding
- Affected surface: Login
- Evidence: `ui/src/pages/LoginPage.tsx`
- Why it matters: high-friction and unsafe compared with modern platform-auth patterns
- User impact: slow onboarding, user anxiety, token handling burden
- Business impact: security posture and enterprise trust risk
- Root cause: platform identity integration not surfaced in-product
- Recommended fix: move to platform SSO or trusted handoff
- Expected benefit: safer, simpler sign-in

### AEP-F007

- Title: Auth and session tokens are stored in localStorage
- Severity: Critical
- Category: Security UX / Frontend architecture
- Affected surface: whole product
- Evidence: `ui/src/lib/http-client.ts`
- Why it matters: raises XSS exposure and persistence risk
- User impact: indirect but serious
- Business impact: avoidable security debt
- Root cause: convenience-based persistence
- Recommended fix: move to safer session storage model or server-managed session cookies
- Expected benefit: stronger auth posture

### AEP-F008

- Title: RBAC checks bypass the shared authenticated API client
- Severity: High
- Category: Security / Consistency
- Affected surface: action gating
- Evidence: `ui/src/components/security/RBACGuard.tsx` uses raw `fetch`
- Why it matters: permission checks can drift from auth/session behavior used elsewhere
- User impact: actions may disappear incorrectly or fail inconsistently
- Business impact: weaker trust in security controls
- Root cause: guard implemented outside the central client layer
- Recommended fix: route all permission checks through shared client/auth plumbing
- Expected benefit: consistent access control behavior

### AEP-F009

- Title: AI suggestions panel bypasses the shared authenticated API client
- Severity: High
- Category: AI UX / Frontend architecture
- Affected surface: Monitoring AI suggestions
- Evidence: `ui/src/components/monitoring/AiSuggestionsPanel.tsx`
- Why it matters: AI suggestions can fail differently from the rest of the app
- User impact: inconsistent loading, auth, and error behavior
- Business impact: harder observability and governance
- Root cause: one-off feature implementation
- Recommended fix: move to shared API client and shared AI assistance abstraction
- Expected benefit: consistent assistive UX

### AEP-F010

- Title: Shell feature flags for breadcrumbs and command palette are defined but not honored
- Severity: Medium
- Category: Implementation / UX governance
- Affected surface: Page shell
- Evidence: `ui/src/lib/feature-flags.ts`; `ui/src/App.tsx` always renders `Breadcrumbs` and `FuzzyFinder`
- Why it matters: config truth is unreliable
- User impact: inconsistent deployment expectations
- Business impact: release-control drift
- Root cause: feature-flag layer not integrated into rendering logic
- Recommended fix: remove dead flags or actually enforce them
- Expected benefit: cleaner deployment control

### AEP-F011

- Title: Global SSE status is always present even when the page does not need real-time behavior
- Severity: Medium
- Category: Observability UX / Cognitive load
- Affected surface: protected shell
- Evidence: `SseStatus.tsx` rendered in `NavBar`; protected pages subscribe via shell
- Why it matters: observability signal is ambient rather than contextual
- User impact: more noise and more infrastructure-thinking
- Business impact: less signal-to-noise
- Root cause: shell-level status chosen over page-level relevance
- Recommended fix: make SSE visibility contextual or collapse it into a diagnostics panel
- Expected benefit: calmer operator experience

### AEP-F012

- Title: Tenant switching is implemented as a raw editable string in the global nav
- Severity: High
- Category: Tenancy UX / Safety
- Affected surface: shell
- Evidence: `ui/src/components/shared/TenantSelector.tsx`
- Why it matters: tenant context is powerful and easy to mistype or switch accidentally
- User impact: cross-tenant confusion risk
- Business impact: safety and trust concerns
- Root cause: internal-operator-first design left unrefined
- Recommended fix: use validated chooser with recent tenants and explicit confirmation for risky switches
- Expected benefit: safer multi-tenant operation

### AEP-F013

- Title: Accessibility contrast debt is systemic across the protected shell
- Severity: Critical
- Category: Accessibility
- Affected surface: monitoring, HITL, pipelines, pattern studio, learning, memory, governance, agent registry, workflow catalog
- Evidence: Chromium Playwright a11y run failed `20` tests, repeatedly on `color-contrast`
- Why it matters: this is not one page; it is a shell-wide token/color issue
- User impact: reduced readability and compliance failures
- Business impact: broad a11y non-compliance risk
- Root cause: gray/indigo token combinations slightly under WCAG thresholds
- Recommended fix: adjust shell text, tenant widget, and selected/inactive button tokens systematically
- Expected benefit: broad accessibility improvement with one design-system pass

### AEP-F014

- Title: Contrast failures repeatedly involve the tenant indicator and tab/button label states
- Severity: High
- Category: Accessibility / Design system
- Affected surface: shell and tab bars
- Evidence: Playwright failure output repeatedly names `.tracking-wide`, `.text-gray-700`, and several active/inactive button label selectors
- Why it matters: recurring pattern indicates token-level problem, not isolated page markup
- User impact: hard-to-scan interface
- Business impact: repeated accessibility regressions
- Root cause: design tokens tuned for aesthetics over threshold margin
- Recommended fix: raise contrast for subtle text and selected tab states
- Expected benefit: one fix improves many pages

### AEP-F015

- Title: React Flow emits parent-container size warnings during browser verification
- Severity: Medium
- Category: Frontend reliability / Perceived quality
- Affected surface: Pipeline Builder
- Evidence: Chromium run emitted React Flow warning that parent container needs width and height
- Why it matters: builder rendering is fragile in some contexts
- User impact: risk of blank or unstable builder canvas
- Business impact: reduced confidence in flagship builder
- Root cause: layout assumptions not universally satisfied
- Recommended fix: harden container sizing contract and test builder in more viewport/layout scenarios
- Expected benefit: more reliable canvas behavior

### AEP-F016

- Title: AEP exposes rich AI/NLQ/voice components that are not actually embedded into primary flows
- Severity: High
- Category: AI/ML UX
- Affected surface: build and query-like workflows
- Evidence: `NLQInput.tsx`, `VoiceInput.tsx`, `VoiceCommandBar.tsx` exist, but no primary pages use them
- Why it matters: product claims and capability scaffolding exceed actual embedded workflow value
- User impact: AI remains a latent component library rather than a task reducer
- Business impact: slower realization of AEP’s automation value proposition
- Root cause: component work advanced ahead of workflow integration
- Recommended fix: embed AI assistance into pipeline creation and operational triage, or remove dormant claims
- Expected benefit: better alignment between UX and AI strategy

### AEP-F017

- Title: Monitoring AI suggestions are a separate panel rather than workflow-native guidance
- Severity: Medium
- Category: AI/ML UX
- Affected surface: Monitoring
- Evidence: `MonitoringDashboardPage.tsx` inserts `AiSuggestionsPanel` above the runs table
- Why it matters: suggestions feel bolted on instead of embedded into action moments
- User impact: more scanning and interpretation burden
- Business impact: lower adoption of AI assistance
- Root cause: AI introduced as a feature block
- Recommended fix: attach suggestions to runs, pipelines, or alerts directly
- Expected benefit: lower cognitive load

### AEP-F018

- Title: Marketplace publishing and marketplace reviewing are crowded into one surface
- Severity: Medium
- Category: Information architecture
- Affected surface: Agent Marketplace
- Evidence: `AgentMarketplacePage.tsx`
- Why it matters: publishing, browsing, provenance, and review writing all compete on one page
- User impact: heavier scan burden
- Business impact: harder onboarding into marketplace flow
- Root cause: one-page MVP accumulated multiple responsibilities
- Recommended fix: separate publish and review modes or simplify by progressive disclosure
- Expected benefit: calmer marketplace UX

### AEP-F019

- Title: Remediation tracker materially overstates convergence and completion
- Severity: High
- Category: Product truth / Documentation
- Affected surface: docs, planning, QA confidence
- Evidence: `products/aep/docs/REMEDIATION_TRACKER.md` claims all P0-P3 tasks complete, including route/page consolidation tasks contradicted by current UI
- Why it matters: internal truth mismatch encourages false confidence
- User impact: bugs survive because teams believe they are closed
- Business impact: planning drift and audit inaccuracy
- Root cause: tracker was not reconciled against runtime after subsequent UI changes
- Recommended fix: convert remediation tracker to evidence-linked status with runtime verification references
- Expected benefit: more trustworthy planning artifacts

### AEP-F020

- Title: Code-vs-docs misalignment remains a live risk despite earlier alignment analysis
- Severity: Medium
- Category: Documentation governance
- Affected surface: roadmap, audits, internal expectations
- Evidence: `docs-generated/03-cross-alignment-analysis/01-code-vs-docs-alignment.md` already identified drift; current runtime still shows mismatches
- Why it matters: the organization knows drift exists, but reconciliation remains incomplete
- User impact: indirect but real through shipped inconsistencies
- Business impact: repeated re-auditing and planning waste
- Recommended fix: formalize product truth generation from runtime metadata and tested flows
- Expected benefit: fewer contradictory artifacts

### AEP-F021

- Title: A dedicated type-safe API client exists but is effectively unused in the runtime app
- Severity: Medium
- Category: Frontend architecture / Reuse
- Affected surface: API integration layer
- Evidence: `ui/src/lib/api-client.ts` exists, but current runtime paths mainly use `http-client.ts` wrappers or raw fetch
- Why it matters: multiple API abstraction patterns increase drift
- User impact: indirect via inconsistency and maintenance complexity
- Business impact: harder standardization
- Root cause: migration or experimentation not finalized
- Recommended fix: converge onto one client stack
- Expected benefit: cleaner frontend architecture

### AEP-F022

- Title: Workflow template success state is not visible or reassuring
- Severity: High
- Category: Interaction design
- Affected surface: Workflow Catalog
- Evidence: instantiate mutation directly navigates without confirmation and currently targets wrong route
- Why it matters: successful creation lacks reliable acknowledgment
- User impact: uncertainty about whether the pipeline was created
- Business impact: reduced trust in template workflows
- Recommended fix: show created pipeline confirmation with explicit “Open builder” CTA
- Expected benefit: safer and clearer handoff

### AEP-F023

- Title: Pipeline creation remains heavily manual despite remediation claims around AI suggestions and prefill
- Severity: High
- Category: Workflow simplification / AI strategy
- Affected surface: Pipeline Builder
- Evidence: `PipelineBuilderPage.tsx` shows no first-class AI/NL start flow; remediation tracker claims stage suggestions, NL pipeline creation, and 80% auto-config are complete
- Why it matters: real UX still asks users to do most of the work
- User impact: high effort for a supposedly agentic product
- Business impact: lower throughput and perceived product differentiation
- Root cause: backend/platform capability claims not carried into the shipped UI flow
- Recommended fix: embed intent capture and configuration prefill into the builder entry experience
- Expected benefit: major simplicity gain

### AEP-F024

- Title: AEP login and shell assume highly trusted internal-user behavior
- Severity: High
- Category: Product strategy / Trust
- Affected surface: Login, tenant switching, shell diagnostics
- Evidence: JWT paste login, raw tenant editing, always-on SSE state
- Why it matters: internal-operator assumptions are leaking into the product UX model
- User impact: steep learning curve and fragile safety posture
- Business impact: poor readiness for broader audiences
- Recommended fix: define whether AEP is internal-operator-only or evolving into a broader product, then align shell accordingly
- Expected benefit: more coherent product direction

### AEP-F025

- Title: Browser a11y suite succeeds on keyboard entry but fails the visual token system
- Severity: Medium
- Category: Accessibility
- Affected surface: shell-wide
- Evidence: a11y run produced many passing keyboard tests but failing contrast tests
- Why it matters: interaction semantics are better than visual accessibility, which is good news but still incomplete
- User impact: visible usability debt remains
- Business impact: lower remediation cost if approached at token level
- Recommended fix: prioritize color/token fixes before deeper structural refactors
- Expected benefit: high accessibility return on effort

## 6. Comprehensive Simplification Plan

### Remove

- Remove broken legacy navigation calls from page code
- Remove duplicate learning access points or duplicate agent detail access pattern
- Remove dead feature flags or dead default finder items
- Remove persistent shell observability noise where it is not task-relevant

### Merge

- Merge route definitions and page-level navigation helpers
- Merge Pattern Studio and Learning into one canonical learning architecture
- Merge Agent Registry and Agent Detail into one canonical browse/detail model
- Merge raw fetch-based auxiliary features into the shared authenticated API client

### Hide by default

- Hide low-level tenant switching behind an explicit admin/operator affordance if needed
- Hide AI explanation or confidence surfaces until they materially support the current task
- Hide shell diagnostics that do not change user action

### Automate

- pipeline draft generation from user intent
- stage recommendation and connector prefill
- post-template instantiation handoff and pipeline opening
- urgency explanation in HITL
- cost-to-remediation linking

### Prefill / preconfigure

- common pipeline templates
- stage configuration defaults
- marketplace publish metadata
- review-note scaffolds

### Move to advanced mode

- raw procedural memory browsing
- low-level tenant and infrastructure controls
- full SOC2 control lists
- detailed runtime durability diagnostics

### Standardize as shared patterns

- route helper generation
- tab bars and selected/inactive states
- shell token/contrast system
- empty/error/degraded states
- permission-check behavior

## 7. Comprehensive AI/ML Embedding Plan

### Pipeline creation

- Function: intent-to-pipeline draft
- User value: reduces builder effort
- Mode: assist first, never auto-deploy
- Confidence threshold: only auto-compose when high confidence; otherwise draft with uncertainty markers
- Fallback: template picker plus manual builder
- Review triggers: external connectors, destructive actions, policy-sensitive stages
- Override model: user edits any suggested stage
- Visibility model: one concise rationale block, not a separate AI page
- Privacy/security: ensure generated stages respect tenant policy and connector permissions
- Priority: Immediate

### Pipeline editing

- Function: explain impact of edits, suggest missing stages, suggest retries or fallbacks
- User value: safer edits
- Mode: assist
- Priority: Short-term

### Monitoring

- Function: summarize likely root cause and next best action
- User value: faster incident triage
- Mode: assist
- Visibility model: attach to run or pipeline rows, not banner-first
- Priority: Immediate

### HITL

- Function: urgency ranking, similar-case retrieval, summary of proposed policy change
- User value: faster and better human review
- Mode: assist with strict human approval
- Priority: Short-term

### Learning and memory

- Function: summarize behavior shifts, cluster repeated episodes, propose policy candidates
- User value: less raw-log reading
- Mode: assist
- Priority: Short-term

### Governance

- Function: predict risky policy drift or concentration of failures
- User value: preventative control
- Mode: assist, never silently enforce
- Priority: Medium-term

### Marketplace

- Function: trust scoring, compatibility scoring, review summarization
- User value: safer reuse
- Mode: assist
- Priority: Medium-term

## 8. Comprehensive Trust / Transparency / Privacy / Security UX Review

### Current gaps

- login requires token paste
- tokens stored in localStorage
- tenant switching is too raw
- permission checks bypass central auth client
- route failures are not always explicit
- documentation overstates completion

### Current strengths

- governance surfaces avoid fake evidence
- run detail avoids synthetic lineage/decision/policy data when features are off
- HITL requires reasoned human intervention
- the shell exposes tenancy and runtime state plainly

### Future-state trust posture

- secure platform sign-in with explicit tenant scope
- safe session handling and expiry
- action-point trust cues instead of ambient control-plane anxiety
- capability truth that never overpromises
- one status model for live, degraded, disabled, and unavailable capability

## 9. Design System / Consistency / Reuse Review

### Strengths

- most pages share a recognizable shell and card/table language
- `@ghatana/design-system` usage is better than in several other products

### Inconsistencies

- shell feature flags defined but not applied
- duplicate API client abstractions
- raw fetch usage in security and AI surfaces
- duplicated learning and agent-detail information architecture
- old default finder items still present

### Systemic reuse opportunities

- route helpers and route registry
- tab/button contrast tokens
- page success/handoff messaging
- auth-aware fetch wrappers for every capability

## 10. Prioritized Remediation Roadmap

### Immediate

1. Fix template-instantiation destination and pipeline edit/open routing
- Priority: P0
- Effort: Medium
- Impact: Very high
- Dependencies: route design decision
- Owner type: frontend, product

2. Replace page-level legacy `/pipelines` navigation
- Priority: P0
- Effort: Low
- Impact: High
- Owner type: frontend

3. Address systemic color-contrast failures in the shell
- Priority: P0
- Effort: Medium
- Impact: Very high
- Owner type: design-system, frontend

4. Remove or strictly scope JWT-paste login for any audience beyond trusted internal operators
- Priority: P0
- Effort: High
- Impact: Very high
- Owner type: product, identity, frontend, security

### Short-term

1. Choose one canonical learning surface
- Priority: P1
- Effort: Medium
- Impact: High
- Owner type: product, design, frontend

2. Choose one canonical agent detail model
- Priority: P1
- Effort: Medium
- Impact: Medium
- Owner type: product, frontend

3. Move RBAC and AI fetches onto the shared authenticated client
- Priority: P1
- Effort: Medium
- Impact: High
- Owner type: frontend, platform

4. Gate Breadcrumbs/FuzzyFinder with real flags or remove dead flags
- Priority: P1
- Effort: Low
- Impact: Medium
- Owner type: frontend

5. Add explicit success and “Open in builder” handoff after template instantiation
- Priority: P1
- Effort: Low
- Impact: High
- Owner type: frontend, design

### Medium-term

1. Integrate AI assistance into pipeline creation instead of keeping it as latent componentry
- Priority: P2
- Effort: High
- Impact: High
- Owner type: product, frontend, ML

2. Replace raw tenant editing with safer validated switching
- Priority: P2
- Effort: Medium
- Impact: High
- Owner type: product, frontend

3. Contextualize SSE and observability signals
- Priority: P2
- Effort: Medium
- Impact: Medium
- Owner type: frontend, product

4. Reconcile remediation tracker against runtime truth
- Priority: P2
- Effort: Medium
- Impact: High
- Owner type: product, engineering management, docs

### Long-term

1. Move to secure platform-auth handoff and safer session model
- Priority: P3
- Effort: High
- Impact: Very high
- Owner type: identity, security, frontend, platform

2. Deliver a true intent-first pipeline authoring flow
- Priority: P3
- Effort: High
- Impact: Very high
- Owner type: product, frontend, ML

3. Build trustworthy AI-native operational guidance across monitoring, HITL, and governance
- Priority: P3
- Effort: High
- Impact: High
- Owner type: ML, product, frontend

## 11. Final Ideal UX Vision

AEP should feel like a serious operator control plane that quietly reduces work instead of asking users to understand more systems than necessary.

Users should arrive through secure platform sign-in, land in a clearly scoped tenant context, and immediately see the work that matters: active runs, reviews waiting, risky cost concentration, or policy concerns. When they create or edit pipelines, the system should draft most of the structure, explain the uncertain parts, and let the user confirm only the parts that deserve judgment. Template instantiation should feel reliable and obvious, not fragile.

Trust should come from consistency:

- every action goes where it says it will
- every capability is either live, clearly disabled, or intentionally hidden
- no page claims completion that the runtime does not support
- governance and review remain embedded where risk exists

AI/ML should be mostly invisible. The user should experience it as:

- fewer manual fields
- better defaults
- better triage ordering
- clearer next actions
- faster summaries of memory, patterns, and risk

The UI should preserve AEP’s strongest current quality: its outcome-first structure. But it should remove the remaining contradictions between shell, page actions, docs, and real runtime behavior.

## 12. Executive Summary Lists

### Top 10 Critical Issues

1. Workflow template instantiation routes to a non-existent path.
2. Pipeline list edit/new actions still use legacy routes.
3. Pipeline Builder lacks a first-class open/edit model.
4. Login depends on pasting a JWT into the UI.
5. Auth/session tokens are stored in localStorage.
6. Chromium a11y audit failed 20 tests across core protected pages.
7. Shell-wide color contrast is below WCAG thresholds in repeated places.
8. Pattern Studio and Learning duplicate each other.
9. Agent Registry and Agent Detail duplicate each other despite documentation claiming consolidation.
10. Remediation tracker materially overstates completion and convergence.

### Top 10 Simplification Opportunities

1. Replace all page-level navigation with canonical route helpers.
2. Unify Pattern Studio and Learning.
3. Unify Agent Registry and Agent Detail.
4. Add one canonical builder open/edit path.
5. Replace token-paste login with platform sign-in.
6. Make AI guidance row-level and contextual, not banner-level.
7. Reduce ambient shell observability noise.
8. Turn tenant switching into a safer validated interaction.
9. Remove dead flags and dead finder defaults.
10. Standardize shell contrast tokens.

### Top 10 AI/ML Opportunities

1. Intent-to-pipeline draft generation
2. Stage suggestion and config prefill
3. Monitoring next-best-action suggestions
4. HITL urgency explanation and similar-case retrieval
5. Learning episode clustering and summary
6. Policy proposal quality/risk explanation
7. Marketplace trust and compatibility scoring
8. Cost optimization recommendations tied to real actions
9. Memory summarization by agent and behavior shift
10. Governance drift prediction

### Top 10 Trust / Visibility / Privacy / Security UX Improvements

1. Remove token-paste login.
2. Stop storing auth/session tokens in localStorage.
3. Make tenant context explicit and safer to change.
4. Fix broken route handoffs so success never feels like failure.
5. Route RBAC checks through the central auth client.
6. Keep “no fake evidence” behavior and apply it consistently.
7. Reconcile remediation/docs truth with runtime truth.
8. Replace silent wildcard fallbacks with truthful error or handoff states.
9. Contextualize observability instead of exposing it everywhere.
10. Fix shell-wide accessibility contrast debt.

## Appendix: Verification Notes

- Targeted Vitest suites passed: `62/62`
- Chromium login flow passed
- Chromium accessibility pass failed `20` tests and passed `13`
- Most a11y failures were repeated serious `color-contrast` issues across shared shell elements and selected/inactive button states
- Browser run also surfaced repeated backend proxy failures and shell-level SSE chatter when services were unavailable, which reinforced the need for more contextual degraded-state UX
