# Data Cloud UI/UX Audit and Remediation Blueprint

Date: 2026-04-22
Product audited: `products/data-cloud`
Audit scope: Data Cloud only
Audit basis: source code, runtime route configuration, page implementations, docs/specs, test suites, accessibility audit output, and prior product audit artifacts available in the repository

## Methodology and Evidence Base

This audit is evidence-based and grounded in the current implementation, not aspirational specs alone. Primary inputs reviewed include:

- Runtime shell and routing: `products/data-cloud/ui/src/routes.tsx`, `products/data-cloud/ui/src/layouts/DefaultLayout.tsx`
- Core pages: `ui/src/pages/IntelligentHub.tsx`, `DataExplorer.tsx`, `WorkflowsPage.tsx`, `WorkflowDesigner/index.tsx`, `SmartWorkflowBuilder.tsx`, `SqlWorkspacePage.tsx`, `TrustCenter.tsx`, `InsightsPage.tsx`, `SettingsPage.tsx`, `PluginsPage.tsx`, `PluginDetailsPage.tsx`, `OperationsConsolePage.tsx`, `AlertsPage.tsx`, `MemoryPlaneViewerPage.tsx`, `EntityBrowserPage.tsx`, `ContextExplorerPage.tsx`, `DataFabricPage.tsx`, `AgentPluginManagerPage.tsx`
- Shared UX infrastructure: `ui/src/components/common/GlobalSearch.tsx`, `ui/src/components/core/*`, `ui/src/components/common/unsupportedSurfaceRegistry.ts`
- Auth/session and API patterns: `ui/src/lib/auth/session.ts`, `ui/src/lib/auth/tokenStorage.ts`, `ui/src/api/client.ts`, `ui/src/lib/api/client.ts`
- Specs/docs: `products/data-cloud/ROUTE_TRUTH_MATRIX_2026-04-17.md`, `products/data-cloud/DATA_CLOUD_PRODUCT_AUDIT_2026-04-20.md`, `products/data-cloud/docs/BOUNDARY_ONLY_PAGES_ANALYSIS.md`, `products/data-cloud/docs/PRODUCT_TRUTH_RECONCILIATION_PLAN.md`, `products/data-cloud/docs/FRONTEND_AUTH_SESSION_POSTURE_PLAN.md`, and `products/data-cloud/ui/docs/web-page-specs/*`
- Test evidence: `ui/src/__tests__/accessibility/AccessibilityAudit.test.tsx`, `ui/src/__tests__/routes/routeTruthMatrix.test.ts`, `ui/src/__tests__/ShellRouting.test.tsx`, `ui/e2e/critical-path-smoke.spec.ts`, `ui/src/__tests__/layouts/DefaultLayout.test.tsx`

Assumptions are explicitly called out where implementation or docs are incomplete.

## 1. Executive Summary

Overall UI/UX health: `High risk`

The Data Cloud product shows clear effort toward simplification and reality-based product truth, but the current experience is still not production-grade from a UX integrity standpoint. The strongest surfaces are `Trust`, portions of `Data`, and parts of `Pipelines`, where the product is increasingly honest about what is live, what is preview-only, and what remains boundary-only. The weakest systemic issue is not visual polish alone. It is truth drift between navigation, routes, docs, page implementations, tests, and user expectations.

The product currently presents a cleaner top-level shell than earlier generations, but that simplification is only partial. Several routes still advertise or imply surfaces that the live runtime no longer actually supports as first-class destinations. Operator-oriented entry points such as `Events`, `Alerts`, `Memory`, `Entities`, `Context`, `Fabric`, and `Agents` are redirected into destinations that either ignore the incoming state, normalize it away, or gate it behind a different role model. This creates silent UX failure rather than explicit, safe degradation.

The biggest workflow weaknesses are:

- broken or misleading handoffs between navigation and destination state
- excessive boundary-only or partial surfaces that remain reachable
- duplicated mental models between “simple outcome-first UI” and “operator/control-plane UI”
- AI presented too explicitly and too inconsistently rather than embedded into core flows
- insufficient accessibility and landmark discipline on key pages
- fragmented shared abstractions across components, API clients, and test truth

The biggest systemic design issues are:

- route truth drift
- shell-role disclosure and route authorization misalignment
- design-system and shared-component fragmentation
- duplicated API client and error-handling patterns
- documentation that is more optimistic than the current runtime
- tests that overstate behavioral coverage in critical routing areas

The largest automation opportunities are in:

- query authoring
- workflow setup
- trust/governance review
- alert triage
- collection setup and schema mapping
- anomaly prioritization
- operator next-best-action routing

The largest trust/privacy/security/visibility gaps are:

- inconsistent capability signaling across surfaces
- role-based disclosure that is visually clear but not operationally coherent
- dead-end settings and operator routes
- weak user-facing explanation of what is real, preview, unavailable, or admin-only at the point of action
- stale security posture documentation that conflicts with implementation

Production-readiness of the product experience: `Not ready for broad external release without route-truth cleanup, accessibility fixes, and capability-surface reconciliation`

## 2. Full UX Scorecard

Severity scale in this scorecard:

- `Critical`: current state can mislead, block, or materially erode trust
- `High`: major friction, inconsistency, or product-quality issue
- `Medium`: notable weakness with bounded impact
- `Low`: acceptable but still improvable

| Area | Rating | Justification |
| --- | --- | --- |
| Information architecture | High | Top-level shell is simpler than before, but route aliases, redirected operator surfaces, and stale discoverability patterns create a fragmented architecture. |
| Navigation | Critical | Navigation exposes `Events` and `Alerts` as if first-class, but runtime redirects them into destinations that do not honor the tab state. |
| Workflow simplicity | High | Core tasks are moving toward simpler entry points, but flow handoffs, AI fallbacks, and partial admin/operator surfaces still create decision overhead. |
| Visual design | Medium | Some screens are clean and modern enough, but the product still mixes polished cards with placeholder/boundary-heavy layouts and uneven density. |
| Interaction quality | High | Predictability is undermined by broken redirects, placeholder-only inputs, inconsistent save/edit patterns, and several dead-end controls. |
| Accessibility | Critical | Accessibility audit failed 11 checks across important pages, including missing landmarks, unlabeled fields, and heading hierarchy issues. |
| AI/ML embedded experience | High | Useful opportunities exist and some heuristics are present, but AI is overly branded, inconsistently implemented, and not embedded deeply enough into core user tasks. |
| Observability/transparency UX | Medium | Trust Center is relatively strong, but cross-product operational visibility remains inconsistent, noisy in places, and misleading in others. |
| Privacy/security UX | High | Trust intent is visible, but settings, auth posture docs, admin/operator routing, and dangerous-action explanation remain inconsistent. |
| Consistency/reuse | Critical | Duplicate component layers, partial design-system adoption, dual API client patterns, and doc/runtime drift indicate systemic reuse failures. |
| Responsive behavior | Medium | Some specs claim responsive behavior, but evidence is uneven and several dense, operator-style layouts remain likely brittle on smaller screens. |
| Perceived performance | Medium | Suspense and query caching help, but boundary-heavy pages, duplicate fetch patterns, and incomplete async feedback reduce perceived reliability. |
| Cognitive load | Critical | Users must remember which routes are real, which are aliases, which are boundary-only, and which operator surfaces silently remap elsewhere. |
| Overall product usability | High | The product is directionally improving, but the current experience still requires too much interpretation and trust repair from the user. |

## 3. Complete Surface-by-Surface Audit

### 3.1 Shell, Routing, Navigation, and Search

Purpose:

- Provide product-wide wayfinding, role-aware disclosure, and deep-link stability.

Current quality:

- Partially simplified shell with clearer top-level priorities.
- Not reliable enough as a source of truth.

Issues found:

- `routes.tsx` redirects `/alerts` to `/insights?tab=alerts` and `/events` to `/insights?tab=events` (`ui/src/routes.tsx:277-293`).
- `InsightsPage.tsx` only defines tabs `overview`, `brain`, `analytics`, and `cost`, and initializes `activeTab` internally without consuming query params (`ui/src/pages/InsightsPage.tsx:1133-1205`).
- `routes.tsx` redirects `/memory` to `/data?view=memory` and `/entities` to `/data?view=entities` (`ui/src/routes.tsx:295-304`).
- `DataExplorer.tsx` only supports `table`, `lineage`, `quality`, and `schema` views (`ui/src/pages/DataExplorer.tsx:237-242`), so redirected `memory` and `entities` views are not honored.
- `routes.tsx` redirects `/context` to `/trust?tab=context` (`ui/src/routes.tsx:305-309`), but current `TrustCenter.tsx` does not expose query-param tab routing.
- `/fabric` and `/agents` redirect to `/operations` (`ui/src/routes.tsx:310-318`), but `OperationsConsolePage.tsx` is admin-gated. This changes the meaning of those routes.
- `DefaultLayout.tsx` comments say operator-only surfaces were “collapsed” into Insights, yet navigation still exposes `Events` and `Alerts` as separate items (`ui/src/layouts/DefaultLayout.tsx:77-96`).
- `GlobalSearch.tsx` still advertises `Events` and `Lineage Preview` as quick nav destinations and still treats `Settings` as a discoverable admin page (`ui/src/components/common/GlobalSearch.tsx:49-59`).
- `DefaultLayout` has an always-visible “AI Powered” badge in the header (`ui/src/layouts/DefaultLayout.tsx:295`), which adds brand noise rather than product value.

Friction points:

- Users can click into routes that look supported but land in semantically incorrect destinations.
- Search reinforces stale or misleading IA.
- Role disclosure tells users what they should see, but not whether the destination is actually actionable.

Hidden complexity:

- Users must mentally track route aliases, operator-only surfaces, admin-only surfaces, and boundary-only screens.
- The product silently absorbs unsupported deep links instead of surfacing explicit reconciliation.

Improvement recommendations:

- Make route truth canonical and executable from one source.
- Remove or hide any nav/search item that does not resolve to a first-class, state-preserving surface.
- If `Events`, `Alerts`, `Context`, `Memory`, or `Entities` are truly consolidated, destination pages must consume and render those incoming states.
- If not, restore distinct surfaces or remove the redirects.
- Replace the “AI Powered” shell badge with contextual assist signals only where they materially help.

Automation opportunities:

- Smart route reconciliation can suggest the correct destination when legacy links are used.
- Global search can rank current tasks, recent work, and role-appropriate destinations instead of static stale quick nav.

Trust/privacy/security considerations:

- Broken routing undermines trust more than a missing feature because it makes the product appear more complete than it is.
- Route and role truth must be explicit for operator/admin surfaces.

### 3.2 Intelligent Hub

Purpose:

- Outcome-first home surface for launching into core workflows.

Current quality:

- Better than a KPI wall.
- Still too noisy and too eager to foreground AI/operator framing.

Issues found:

- The page mixes primary-user task launchers with operator/admin posture and “AI” narrative.
- Outcome classification and suggestion logic appears heuristic-driven rather than confidence-managed orchestration.
- Operator follow-up paths depend on routes that are not always behaviorally coherent.

Friction points:

- The page asks users to interpret product capability rather than simply continue their work.
- “Next best action” value is constrained by downstream route-truth gaps.

Hidden complexity:

- A home page that looks decisive can route users into partial or boundary-heavy surfaces.

Improvement recommendations:

- Reduce the home surface to a short set of highly reliable “continue working” and “start new” actions.
- Move operator diagnostics into a compact contextual panel rather than co-equal launchers for non-operator roles.
- Only show assistive intelligence when it changes the next recommended action.

Automation opportunities:

- Resume last successful task or last interrupted task automatically.
- Pre-rank launchers by recent work, role, and operational urgency.
- Summarize anomalies into plain-language action cards instead of generic AI framing.

Trust/privacy/security considerations:

- A home page should never imply more certainty or automation than the downstream systems can deliver.

### 3.3 Data Explorer

Purpose:

- Browse collections and inspect table, quality, schema, and lineage views.

Current quality:

- One of the more coherent primary-user surfaces.
- Still incomplete in accessibility, view semantics, and metadata depth.

Issues found:

- Search input uses placeholder-only text and lacks explicit label (`ui/src/pages/DataExplorer.tsx:266-276`).
- Accessibility tests reported missing landmarks and missing main landmark.
- The subtitle still frames lineage as preview rather than integrated truth (`ui/src/pages/DataExplorer.tsx:260-262`).
- Redirected `memory` and `entities` states are dropped because only four view modes exist.
- Quality view is comparatively shallow and reads as async-computed placeholder behavior rather than a full operational workspace.

Friction points:

- Users cannot tell whether they are in a canonical detail view or a compatibility-hand-off state.
- Quality and lineage feel secondary rather than first-class explorations.

Hidden complexity:

- Data Explorer is now absorbing too many conceptual responsibilities: list, detail, quality, lineage preview, and legacy handoff target.

Improvement recommendations:

- Give the search field an accessible visible or programmatic label.
- Split canonical sub-views more clearly and only absorb handoff states that are fully supported.
- Either restore `memory` and `entities` as first-class destinations or remove those legacy redirects.
- Strengthen quality as an action-oriented diagnostics view with clear issue counts, trends, owners, and recommended actions.

Automation opportunities:

- Auto-summarize schema anomalies, freshness drops, lineage impact, and likely next actions.
- Prefill filters based on the last workflow, alert, or query context.

Trust/privacy/security considerations:

- Collection-level trust metadata, sensitivity, and access signals should appear inline but not dominate the whole page.

### 3.4 Create Collection

Purpose:

- Create a new collection with schema and metadata inputs.

Current quality:

- Structurally straightforward.

Issues found:

- Accessibility tests reported missing landmark regions and missing main landmark.
- The surrounding page shell is thin to the point of low confidence; users get minimal context about naming conventions, schema strategy, downstream implications, or automation support.

Friction points:

- Users must still reason manually about structure and intent.

Improvement recommendations:

- Add strong creation guidance, presets, and examples directly in the form.
- Improve page-level landmarks and hierarchy.
- Add post-create “next recommended step” routing.

Automation opportunities:

- Infer schema scaffolds from uploaded samples or selected template types.
- Recommend field names, types, retention class, and default quality checks.

Trust/privacy/security considerations:

- Sensitive data classification should be prompted contextually at creation time, not only later in Trust.

### 3.5 Edit Collection

Purpose:

- Update collection metadata and schema-related information.

Current quality:

- Functional wrapper around the shared form.

Issues found:

- Thin wrapper does not visibly distinguish safe edits from potentially disruptive edits.
- Generated fallback field IDs based on `Date.now()` suggest brittle client-side identity semantics.

Friction points:

- Users may not know which changes are cosmetic versus contract-affecting.

Improvement recommendations:

- Separate low-risk metadata edits from schema-impacting changes.
- Add explicit impact preview for schema modifications.

Automation opportunities:

- Predict downstream breakage risk before save.
- Suggest backward-compatible alternatives.

### 3.6 Collection Form

Purpose:

- Shared create/edit form for collections.

Current quality:

- Better design-system discipline than several other pages.

Issues found:

- Form quality is stronger than its surrounding page contexts, which makes the overall flow feel uneven.
- This is one example of partial rather than system-wide design-system adoption.

Improvement recommendations:

- Use the form as a design-system reference baseline and retrofit weaker pages to the same standard.

Automation opportunities:

- Prefill descriptions, tags, retention class, and quality checks from sample data or common presets.

### 3.7 Pipelines List / Workflows Page

Purpose:

- List, triage, create, and manage pipelines.

Current quality:

- Reasonably modern and clearer than many admin-style pages.

Issues found:

- Accessibility tests flagged missing landmarks and unlabeled form fields.
- Search and filter controls still depend too heavily on placeholders or adjacent context rather than clear labels.
- Page mixes “outcome-first” framing with advanced editor entry, review entry, and AI optimization hints, producing multiple mental models.

Friction points:

- Users decide between builder, designer, review, and optimizations before the page has clearly prioritized the dominant workflow.

Improvement recommendations:

- Make the primary action singular and explicit based on user type and workflow state.
- Reframe AI hints as inline assistance attached to each workflow row or draft, not as a separate conceptual pane.

Automation opportunities:

- Auto-group pipelines by health, owner attention required, and recency.
- Suggest next-best-action per workflow.
- Draft remediation actions for failed or risky pipelines.

Trust/privacy/security considerations:

- Pipeline pages should clearly surface execution risk, approval requirements, and policy implications.

### 3.8 Smart Workflow Builder

Purpose:

- Natural-language-assisted pipeline creation.

Current quality:

- Honest about unavailable AI generation in places.
- Still too boundary-driven and too dependent on manual continuation.

Issues found:

- The product explicitly acknowledges unavailable generation contracts and fallback modes.
- “Review required” behavior is correct in principle, but the surrounding experience still feels like a partially shipped feature.
- Placeholder configuration and fallback copy create expectation debt.

Friction points:

- Users are asked to engage an AI-led workflow that often hands them back into manual editor work.

Improvement recommendations:

- Recast this as “guided pipeline setup” until generation is reliable enough to own the promise.
- Only expose fully generated drafts when the system can provide traceable confidence and editable rationale.

Automation opportunities:

- Intent-to-draft generation, connector mapping, schema alignment, validation rule recommendation, and policy pre-checks.

Trust/privacy/security considerations:

- Generated workflows need provenance, confidence, human review gates, and explainable transformation steps.

### 3.9 Workflow Designer

Purpose:

- Advanced editing and review for pipeline structure.

Current quality:

- Clear wrapper and canvas entry.

Issues found:

- The surrounding page offers limited orientation and safety guidance for complex edits.
- The product lacks a strong progression between simple setup, advanced editing, execution review, and rollback.

Improvement recommendations:

- Add explicit mode framing: draft, review, active, unhealthy, deprecated.
- Surface impact summaries, recent failures, and pending approvals near the canvas.

Automation opportunities:

- Explain edge/node effects in plain language.
- Highlight likely breakpoints, invalid dependencies, and optimization opportunities.

### 3.10 Query / SQL Workspace

Purpose:

- Query data through SQL and AI/NLQ assistance.

Current quality:

- Useful hybrid experience with some honest fallback signaling.

Issues found:

- Still heavily manual.
- Uses explicit heuristic fallback chips and ambiguity clarification states that shift cognitive burden back to the user.
- Placeholder-driven inputs and suggested SQL templates imply assistance, but the user still authors much of the solution.

Friction points:

- Users must understand ambiguity resolution, scope, confidence, and SQL correctness at the same time.

Improvement recommendations:

- Make the dominant mode “ask a question” and progressively disclose raw SQL only after the system proposes a confident draft and rationale.
- Provide better result framing, saved query reuse, and inline explanation of data scope.

Automation opportunities:

- Intent classification, SQL draft generation, schema-aware autocomplete, ambiguity narrowing, result summarization, anomaly explanation, and follow-up suggestion generation.

Trust/privacy/security considerations:

- Sensitive-table access, row-level restrictions, and query cost/risk warnings should be visible before execution when relevant.

### 3.11 Trust Center

Purpose:

- Governance, compliance, retention, and redaction operations.

Current quality:

- Strongest current surface in the product.

Evidence:

- `TrustCenter.tsx` is wired to live governance actions and lifecycle states rather than fabricated controls (`ui/src/pages/TrustCenter.tsx:1-180` and related service usage).

Issues found:

- Search control still appears to rely on placeholder-only input style.
- Trust actions are stronger than many other surfaces, which makes product-wide maturity feel inconsistent.
- Access review remains read-only, which is honest but incomplete.
- Redirected `/context` handoff does not map into an actual Trust tab state.

Friction points:

- Governance truth is good locally, but not integrated enough into collection creation, query, pipeline, and settings experiences.

Improvement recommendations:

- Keep Trust as the reference model for “reality-based UX.”
- Pull trust signals upstream into collection, query, and pipeline flows.
- Either implement a real context tab or stop redirecting `/context` here.

Automation opportunities:

- Auto-classify retention, detect risky fields, summarize audit deltas, recommend redactions, and propose purge actions with safe review gates.

Trust/privacy/security considerations:

- Excellent candidate surface for role-appropriate visibility, dry-run-first actions, and explicit irreversible-action safeguards.

### 3.12 Insights

Purpose:

- Unified analytics, AI insights, and cost visibility.

Current quality:

- Ambitious, but overloaded and currently mismatched with operator route consolidation.

Issues found:

- Does not consume redirected tabs for `alerts` or `events`.
- Heading hierarchy failed accessibility audit.
- Tabs mix overview, “AI brain,” analytics, and cost into a single conceptual bucket that is broad but not necessarily cohesive.
- Subtitle and sidebar framing still overexpose AI as a branded product layer.

Friction points:

- Users arriving from redirected operator routes do not land in the intended context.
- The page requires users to infer the difference between diagnostics, analytics, cost, and AI status.

Improvement recommendations:

- Split operator incident/event review from analytics/cost unless the page can truly unify those tasks.
- If Insights remains the unified destination, it must support route-driven entry states such as `alerts`, `events`, `analytics`, and `cost`.
- Rename “AI Brain” to a user-outcome concept unless internal platform observability is truly the user’s goal.

Automation opportunities:

- Summarize top anomalies, top cost drivers, and recommended remediations in plain language.
- Rank insights by urgency and likely business impact.

Trust/privacy/security considerations:

- Observability surfaces should explain what is inferred, what is measured, and what remains heuristic.

### 3.13 Settings

Purpose:

- Admin-only settings and key-management surface.

Current quality:

- Explicitly boundary-only.

Issues found:

- All major settings sections are unsupported-boundary panels (`ui/src/pages/SettingsPage.tsx` plus `ui/src/components/common/unsupportedSurfaceRegistry.ts`).
- The page is still discoverable by direct link and global search.
- Presence of the route implies operational admin capability that does not exist.
- Onboarding and docs can easily drift into referencing settings that the product does not actually provide.

Friction points:

- Admins land in a page that confirms absence rather than enabling work.

Improvement recommendations:

- Remove from product discovery until real settings mutations exist.
- If retained, reframe as “Admin capabilities status” rather than “Settings.”
- Eliminate references to future settings locations from onboarding unless they exist.

Automation opportunities:

- None at the UI level until real settings models exist.

Trust/privacy/security considerations:

- API key and profile settings are security-sensitive and should not appear as faux capabilities.

### 3.14 Plugins

Purpose:

- View installed plugins, details, status, and some runtime actions.

Current quality:

- Partly real, partly boundary-driven.

Issues found:

- Accessibility tests flagged missing landmarks and unlabeled fields.
- Catalog and deployment capabilities remain boundary-only, but the information architecture still invites users into them.
- Search field labeling and operator framing need improvement.

Friction points:

- Product presents plugin control as broader than current implementation.

Improvement recommendations:

- Keep the installed-runtime view if it is materially useful.
- Hide catalog/deployment tabs until they are usable or clearly convert them into read-only documentation/status surfaces.

Automation opportunities:

- Health anomaly detection, version upgrade recommendations, risk summaries, and compatibility checks.

Trust/privacy/security considerations:

- Any plugin enable/disable control must surface blast radius, data access scope, and rollback behavior.

### 3.15 Plugin Details

Purpose:

- Inspect individual plugin health and metadata.

Current quality:

- Informative but not yet first-class.

Issues found:

- Dependency graph is explicitly unavailable, leaving a detail view that can feel sparse or generic.
- Narrative quality is less product-specific than it should be.

Improvement recommendations:

- Lead with operational facts: status, version delta, capabilities, incidents, permissions, recent failures.
- Remove generic boilerplate and emphasize what can be acted on.

### 3.16 Alerts

Purpose:

- Operator-facing alert triage, grouping, rule management, and suggestions.

Current quality:

- Underlying page and services appear more real than the current router admits.

Evidence:

- `unsupportedSurfaceRegistry.ts` explicitly says alerts are live and launcher-backed.
- Page spec `17_alerts_and_notifications_page.md` describes `/alerts` as a live operator triage surface.
- Contract tests include alert endpoints.
- Yet runtime route now redirects `/alerts` to `/insights?tab=alerts`.

Issues found:

- Live capability exists, but current routing collapses it into a destination that ignores the tab.
- This is one of the most damaging trust gaps in the product.

Improvement recommendations:

- Either restore `AlertsPage` as the canonical routed surface or fully implement alerts as a real Insights tab.

Automation opportunities:

- Alert grouping, deduplication, probable root cause, remediation suggestion, and escalation routing.

Trust/privacy/security considerations:

- Alerts must distinguish between automated action, suggested action, acknowledged action, and audited resolution.

### 3.17 Events

Purpose:

- Event inspection and operational history.

Current quality:

- No longer a stable first-class runtime surface.

Issues found:

- Navigation and docs still signal discoverability.
- Redirect currently lands in Insights overview rather than an events-focused context.

Improvement recommendations:

- Remove from shell/search if not first-class.
- If retained as part of Insights, implement real event tab support and query-state routing.

### 3.18 Memory Plane Viewer

Purpose:

- Surface memory/checkpoint context.

Current quality:

- Existing page appears more developed than current route exposure suggests.

Issues found:

- `/memory` redirects to `DataExplorer` with an unsupported `view=memory`, so runtime behavior no longer matches the page’s apparent existence.

Improvement recommendations:

- Restore as a route if needed, or archive the surface and remove false affordances.

### 3.19 Entity Browser

Purpose:

- Inspect entity-centric records and related operations.

Current quality:

- Backed by APIs and suggestions, but not routable in a coherent way.

Issues found:

- `/entities` redirect maps to a `DataExplorer` view that does not exist.

Improvement recommendations:

- Same decision as Memory: restore, absorb properly, or remove.

### 3.20 Context Explorer

Purpose:

- Inspect context layer and relationship depth.

Current quality:

- Valuable capability with broken runtime discoverability.

Issues found:

- `/context` redirect lands on a Trust destination that does not consume the intended tab state.

Improvement recommendations:

- Restore a dedicated route or implement true Trust-context integration.

### 3.21 Operations Console

Purpose:

- Admin/operator diagnostics and tools.

Current quality:

- Mixed.

Issues found:

- Admin-only guard conflicts with redirected operator-facing routes such as `/fabric` and `/agents`.
- Uses mock data and preview tools while still acting as a consolidation target.
- Links out to routes that themselves may be redirected or partially supported.

Friction points:

- Users cannot reliably predict whether Operations is a real console, a preview shell, or a diagnostic placeholder.

Improvement recommendations:

- Restrict this page to genuinely admin-only operational tasks.
- Do not redirect previously operator-facing route concepts here unless the page can fulfill them.

### 3.22 Data Fabric

Purpose:

- Fabric metrics and topology.

Current quality:

- Explicit preview.

Issues found:

- Docs and code acknowledge preview/demo metrics.
- Redirecting `/fabric` into admin-only Operations removes even that limited, honest preview affordance while changing route meaning.

Improvement recommendations:

- Either preserve a clearly labeled preview route or remove it entirely from route truth.

### 3.23 Agent Catalog / Agent Plugin Manager

Purpose:

- Inspect agent/plugin catalog and related runtime surfaces.

Current quality:

- Read-only and not first-class.

Issues found:

- Route truth says read-only operator-oriented surface, but runtime redirects to admin-only Operations.

Improvement recommendations:

- Same principle: restore coherent access semantics or remove route-level discoverability.

### 3.24 Empty, Loading, Success, Warning, and Error States

Current quality:

- Inconsistent.

Issues found:

- Some pages use honest unsupported boundaries.
- Others silently redirect or normalize unsupported states away.
- Loading and error experiences are not standardized enough across pages.

Improvement recommendations:

- Adopt one system-wide state model:
  - loading
  - empty
  - partial
  - live
  - preview
  - temporarily unavailable
  - not in deployment
  - error with recovery

### 3.25 Responsive and Mobile/Tablet Behavior

Current quality:

- Claimed in docs more than strongly evidenced in runtime verification.

Risks:

- Dense sidebar-plus-panel layouts, canvas surfaces, and operator consoles are likely weak on smaller screens.

Improvement recommendations:

- Explicitly audit narrow-width shells and convert secondary panels into drawers or stacked sections.

## 4. Complete End-to-End Flow Review

### 4.1 Opening the product and orienting

User goal:

- Understand where to start and resume meaningful work quickly.

Entry point:

- `/`, `/dashboard`, or `/hub`

Current steps:

1. Open Intelligent Hub.
2. Interpret launch cards, recent activity, and operator diagnostics.
3. Choose Data, Pipelines, Query, Trust, or diagnostics.

Decision points:

- Which top-level workflow applies
- Whether operator surfaces are relevant
- Whether AI-driven suggestions are reliable

Manual effort:

- Moderate

Failure points:

- Hub points toward surfaces whose downstream route behavior is inconsistent.

Visibility quality:

- Adequate for broad orientation, not adequate for capability certainty.

AI/ML opportunities:

- Personalized resume state
- urgency ranking
- next-best-action

Simplification recommendations:

- Make “resume last task,” “start new,” and “review attention items” the only top-level decisions.

Ideal future-state journey:

- User lands on a calm home surface showing one primary resume action, one creation action, and a short queue of issues needing attention.

### 4.2 Creating a collection

User goal:

- Define a new collection with minimal setup burden.

Current steps:

1. Go to `/data/new`.
2. Fill out collection form.
3. Save.
4. Decide what to do next manually.

Manual effort:

- High relative to expected modern product standard.

Failure points:

- Weak guidance
- limited schema automation
- no strong trust or downstream-usage preview

AI/ML opportunities:

- schema inference
- field type suggestions
- quality rule defaults
- sensitivity classification

Simplification recommendations:

- Convert from blank-form-first to guided setup with presets and sample-driven inference.

Ideal future-state:

- User provides a name or sample; system drafts schema, tags, retention, and default checks, then asks for confirmation only where uncertainty is meaningful.

### 4.3 Exploring data and diagnosing issues

User goal:

- Find a collection and inspect quality, schema, and lineage.

Current steps:

1. Open `/data`.
2. Search/select collection.
3. Switch between table, quality, schema, lineage.

Manual effort:

- Moderate

Failure points:

- search labeling
- shallow quality context
- unsupported legacy redirects for memory/entities

AI/ML opportunities:

- issue summarization
- anomaly detection
- suggested filters
- explain-this-schema or explain-this-lineage

Simplification recommendations:

- Make issue-driven exploration the default when users arrive from alerts, pipelines, or query anomalies.

### 4.4 Creating a pipeline from scratch

User goal:

- Build and launch a useful workflow quickly.

Current steps:

1. Open `/pipelines`.
2. Choose create path.
3. Enter guided or advanced builder.
4. Review and edit.

Manual effort:

- High

Decision points:

- guided builder vs advanced editor
- confidence/review path vs manual authoring

Failure points:

- partial AI availability
- unclear ownership of builder vs designer

AI/ML opportunities:

- intent capture
- draft generation
- connector mapping
- validation
- optimization hints

Simplification recommendations:

- Collapse into one entry flow with progressive disclosure into advanced editing only when needed.

### 4.5 Monitoring and managing pipelines

User goal:

- Know which workflows are healthy, failing, risky, or require attention.

Current steps:

1. Open `/pipelines`.
2. Search/filter.
3. Open workflow.
4. Investigate.

Manual effort:

- Moderate to high

Failure points:

- page asks user to interpret multiple conceptual panels
- limited execution-first review framing

AI/ML opportunities:

- ranked triage
- probable failure cause
- suggested fixes
- execution summaries

Simplification recommendations:

- Default list sort by “attention required,” not just raw recency.

### 4.6 Writing and running a query

User goal:

- Get an answer with minimal SQL expertise.

Current steps:

1. Open `/query`.
2. Ask a question or start writing SQL.
3. Review ambiguity chips and inferred scope.
4. Run or refine query.

Manual effort:

- High for less technical users

Failure points:

- ambiguity management remains manual
- fallback chips expose internal reasoning burden

AI/ML opportunities:

- ask-to-answer flow
- generated SQL draft
- explanation of result
- follow-up prompt suggestions

Simplification recommendations:

- Move SQL into an advanced reveal; keep question-answer as primary mode.

### 4.7 Governance and sensitive-data action

User goal:

- Review policies, redaction, retention, and compliance status.

Current steps:

1. Open `/trust`.
2. Search or review policy/retention surfaces.
3. Trigger dry run or action.

Manual effort:

- Moderate

Strengths:

- live operations
- lifecycle truth
- dry-run posture

Weaknesses:

- not integrated enough upstream into creation/query/pipeline flows

AI/ML opportunities:

- policy suggestion
- risk highlighting
- audit summarization

Ideal future-state:

- Trust is not a separate specialist island; its signals appear naturally where users are about to do risky things.

### 4.8 Reviewing alerts and events

User goal:

- Triage operator issues and understand what needs attention.

Current steps:

1. Click Events or Alerts in nav or search.
2. Get redirected into Insights.
3. Land on a page that does not honor alert/event tab state.

Manual effort:

- High and frustrating

Failure points:

- route failure
- context loss
- trust erosion

AI/ML opportunities:

- deduplication
- likely root cause
- action recommendation

Simplification recommendations:

- Fix routing first. No higher-level UX optimization matters until arrival context is preserved.

### 4.9 Managing plugins

User goal:

- Inspect or manage plugin runtime state.

Current steps:

1. Open `/plugins`.
2. Review installed/catalog/deployment tabs.
3. Land on partial or boundary-only content depending on tab.

Manual effort:

- Moderate

Failure points:

- partial discoverability
- unclear actionability

AI/ML opportunities:

- compatibility and risk summaries

### 4.10 Admin settings and key management

User goal:

- Manage admin-level preferences and credentials.

Current steps:

1. Reach `/settings`.
2. Browse profile/preferences/notifications/api sections.
3. Discover that all are boundary-only.

Manual effort:

- Low, but only because nothing substantive can be done.

Failure points:

- trust loss
- false capability implication

Simplification recommendations:

- remove or recast as capability-status page.

## 5. Comprehensive Findings Catalog

Severity legend:

- `Critical`
- `High`
- `Medium`
- `Low`

### F-001

- Title: Redirected Alerts route does not resolve to an alert-capable destination
- Severity: Critical
- Category: Routing / Navigation / Trust
- Affected surface: `/alerts`, nav, search, operator workflows
- Evidence: `ui/src/routes.tsx:277-280` redirects `/alerts` to `/insights?tab=alerts`; `ui/src/pages/InsightsPage.tsx:1133-1205` defines no `alerts` tab and does not consume query params.
- Why it matters: Users are sent to the wrong task context while believing they reached Alerts.
- User impact: Broken triage flow and high trust erosion.
- Business/operational impact: Delayed response to incidents and misleading product capability.
- Likely root cause: Consolidation work was applied at route level without destination-state support.
- Recommended fix: Restore `AlertsPage` as canonical or implement real `alerts` tab support in Insights.
- Expected benefit: Accurate operator entry points and improved trust.
- Related findings: F-002, F-003, F-004, F-008

### F-002

- Title: Redirected Events route loses context and lands in generic Insights overview
- Severity: Critical
- Category: Routing / Navigation
- Affected surface: `/events`, nav, search
- Evidence: `ui/src/routes.tsx:290-293`; `ui/src/pages/InsightsPage.tsx:1133-1205`
- Why it matters: Event investigation is no longer first-class or context-preserving.
- User impact: Extra navigation and interpretation burden.
- Business/operational impact: Slower operator debugging and lower confidence in route stability.
- Likely root cause: Partial consolidation of operator surfaces.
- Recommended fix: Implement real events support in Insights or restore dedicated Events route.
- Expected benefit: Reliable operational history flow.
- Related findings: F-001, F-008

### F-003

- Title: Memory route redirects into unsupported Data Explorer state
- Severity: High
- Category: Routing / IA
- Affected surface: `/memory`
- Evidence: `ui/src/routes.tsx:295-299`; `ui/src/pages/DataExplorer.tsx:237-242`
- Why it matters: Product suggests a memory viewer concept that does not actually resolve.
- User impact: Confusion and lost context.
- Business/operational impact: Weakens confidence in advanced data/AI capabilities.
- Likely root cause: Legacy route consolidation without equivalent destination support.
- Recommended fix: Restore dedicated Memory surface or remove route from runtime truth.
- Expected benefit: Cleaner product semantics.
- Related findings: F-004, F-005

### F-004

- Title: Entities route redirects into unsupported Data Explorer view
- Severity: High
- Category: Routing / IA
- Affected surface: `/entities`
- Evidence: `ui/src/routes.tsx:300-304`; `ui/src/pages/DataExplorer.tsx:237-242`
- Why it matters: Entity browsing capability appears present but is not reachable coherently.
- User impact: Users cannot trust links or search results.
- Business/operational impact: Reduced discoverability of entity-oriented workflows.
- Likely root cause: Same as F-003.
- Recommended fix: Restore `EntityBrowserPage` or remove redirect and all discoverability hooks.
- Expected benefit: Reduced false affordance.
- Related findings: F-003, F-006

### F-005

- Title: Context route redirects to Trust without a context-aware destination state
- Severity: High
- Category: Routing / Information Architecture
- Affected surface: `/context`
- Evidence: `ui/src/routes.tsx:305-309`; `TrustCenter.tsx` lacks tab query handling.
- Why it matters: Context exploration concept is not preserved across handoff.
- User impact: Context users land on the wrong surface.
- Business/operational impact: Weakens product cohesion between context layer and governance.
- Likely root cause: Unfinished absorption strategy.
- Recommended fix: Implement real Trust context tab or restore Context Explorer route.
- Expected benefit: Clearer conceptual mapping.
- Related findings: F-003, F-004

### F-006

- Title: Fabric and Agents routes now redirect into an admin-only console
- Severity: Critical
- Category: Role Model / Routing
- Affected surface: `/fabric`, `/agents`, operator workflows
- Evidence: `ui/src/routes.tsx:310-318`; `OperationsConsolePage.tsx` guarded by `RBACGuard requiredRole="ADMIN"`.
- Why it matters: Operator-facing or read-only surfaces become inaccessible or semantically changed.
- User impact: Operators may be denied access to routes still documented as operator-facing/read-only.
- Business/operational impact: RBAC confusion and operational delay.
- Likely root cause: Consolidation ignored role semantics.
- Recommended fix: Preserve operator-readable routes or move only truly admin functions into Operations.
- Expected benefit: Coherent authorization and disclosure.
- Related findings: F-007, F-011

### F-007

- Title: Navigation comments claim Events and Alerts are collapsed, but nav still exposes them as separate items
- Severity: High
- Category: Navigation / Consistency
- Affected surface: sidebar navigation
- Evidence: `ui/src/layouts/DefaultLayout.tsx:77-96`
- Why it matters: Code comments, nav structure, and route behavior disagree.
- User impact: Users see multiple entry points that do not behave distinctly.
- Business/operational impact: Maintenance drift and poor UX clarity.
- Likely root cause: Incomplete implementation after IA simplification.
- Recommended fix: Either truly collapse these items or make them distinct working destinations.
- Expected benefit: More honest and simpler navigation.
- Related findings: F-001, F-002

### F-008

- Title: Global search advertises stale or semantically broken destinations
- Severity: High
- Category: Search / Navigation
- Affected surface: global command/search
- Evidence: `ui/src/components/common/GlobalSearch.tsx:49-59`
- Why it matters: Search reinforces outdated IA and broken routes.
- User impact: Wrong destination and lost confidence in command palette.
- Business/operational impact: Lower discoverability and increased support burden.
- Likely root cause: Search quick nav was not updated alongside route consolidation.
- Recommended fix: Generate quick nav from canonical route truth and capability registry, not static literals.
- Expected benefit: Search becomes reliable.
- Related findings: F-001 through F-007

### F-009

- Title: Accessibility audit found missing landmarks on Data Explorer
- Severity: Critical
- Category: Accessibility
- Affected surface: Data Explorer
- Evidence: `vitest` run of `AccessibilityAudit.test.tsx` reported missing landmark regions and main landmark for `DataExplorer`.
- Why it matters: Screen-reader and keyboard users lose structural orientation.
- User impact: Exclusionary navigation experience.
- Business/operational impact: Accessibility non-compliance risk.
- Likely root cause: Page shells are visually structured but semantically weak.
- Recommended fix: Add proper `<main>`, landmark regions, and heading hierarchy.
- Expected benefit: Better assistive-tech navigation.
- Related findings: F-010, F-011, F-012, F-013

### F-010

- Title: Accessibility audit found missing landmarks on Create Collection
- Severity: High
- Category: Accessibility
- Affected surface: Create Collection
- Evidence: same test run as above
- Why it matters: New-object creation flow lacks semantic structure.
- User impact: Harder navigation for assistive technology users.
- Business/operational impact: Accessibility compliance risk.
- Likely root cause: Thin wrapper pages omit semantic shell.
- Recommended fix: Add page landmarks and clear heading structure.
- Expected benefit: More inclusive form flow.
- Related findings: F-009

### F-011

- Title: Accessibility audit found missing landmarks and label issues on Workflows page
- Severity: Critical
- Category: Accessibility / Forms
- Affected surface: Pipelines / Workflows
- Evidence: `AccessibilityAudit.test.tsx` failures for landmarks, main region, and associated labels.
- Why it matters: Pipeline triage surface is operationally important and currently excludes or burdens keyboard/screen-reader users.
- User impact: Harder filtering and navigation.
- Business/operational impact: Accessibility risk on a core workflow.
- Likely root cause: Placeholder-led form patterns and insufficient semantic markup.
- Recommended fix: Add visible/programmatic labels and proper landmark structure.
- Expected benefit: Faster, more inclusive workflow management.
- Related findings: F-009, F-012

### F-012

- Title: Accessibility audit found heading hierarchy issues on Insights
- Severity: High
- Category: Accessibility / Content hierarchy
- Affected surface: Insights
- Evidence: `AccessibilityAudit.test.tsx` failure for heading hierarchy.
- Why it matters: Page is already cognitively dense; poor heading order worsens it.
- User impact: Harder scanability and assistive-tech navigation.
- Business/operational impact: Accessibility and maintainability risk.
- Likely root cause: Visual composition evolved faster than semantic structure.
- Recommended fix: Normalize heading hierarchy and section landmarks.
- Expected benefit: Better comprehension.
- Related findings: F-014

### F-013

- Title: Accessibility audit found landmark and label issues on Plugins page
- Severity: High
- Category: Accessibility
- Affected surface: Plugins
- Evidence: `AccessibilityAudit.test.tsx` failures for landmarks and form labels.
- Why it matters: Operator/admin control surfaces cannot be second-class in accessibility.
- User impact: Reduced usability for assistive-tech users.
- Business/operational impact: Compliance and ops-quality risk.
- Likely root cause: Similar placeholder/control pattern drift.
- Recommended fix: Add landmarks, label search/filter fields, and standardize admin surface semantics.
- Expected benefit: Better accessibility and consistency.

### F-014

- Title: Placeholder-only inputs remain common across key pages
- Severity: High
- Category: Accessibility / Interaction design / Content
- Affected surface: Data, Pipelines, Query, Plugins, search patterns
- Evidence: direct code review of `DataExplorer.tsx`, `GlobalSearch.tsx`, and related pages; supported by accessibility failures.
- Why it matters: Placeholder text is not a durable label and disappears during use.
- User impact: Lower clarity, higher memory burden, poorer accessibility.
- Business/operational impact: Lower task completion reliability.
- Likely root cause: Fast visual implementation without form semantics standard.
- Recommended fix: Standardize labeled form-field components and ban placeholder-only labeling.
- Expected benefit: Lower cognitive load and better accessibility.

### F-015

- Title: Settings page is discoverable despite being entirely boundary-only
- Severity: High
- Category: IA / Trust / Admin UX
- Affected surface: `/settings`, search, onboarding references
- Evidence: `ui/src/pages/SettingsPage.tsx`; `unsupportedSurfaceRegistry.ts`
- Why it matters: Users are sent to “Settings” expecting control and receive only absence.
- User impact: Trust erosion and admin frustration.
- Business/operational impact: Support burden and poor perceived maturity.
- Likely root cause: Transparent boundary strategy retained too much discovery.
- Recommended fix: Hide from discovery until actionable, or rename to “Admin capability status.”
- Expected benefit: More honest admin UX.

### F-016

- Title: Onboarding and settings references are likely to drift into nonexistent destinations
- Severity: Medium
- Category: Flow coherence / Documentation
- Affected surface: onboarding, docs, help text
- Evidence: boundary-only Settings plus spec/docs ecosystem mentioning future settings patterns.
- Why it matters: Setup guidance becomes misleading.
- User impact: Users hunt for controls that do not exist.
- Business/operational impact: Longer time to value.
- Likely root cause: Docs evolved ahead of implementation.
- Recommended fix: remove or annotate all onboarding references to unavailable settings capabilities.
- Expected benefit: Faster setup and fewer dead ends.

### F-017

- Title: Shell role disclosure is clearer than route capability truth
- Severity: High
- Category: Role model / UX governance
- Affected surface: whole shell
- Evidence: `session.ts` docs clarify shell role is UI-only; route behavior still changes perceived capability in misleading ways.
- Why it matters: UI-disclosure clarity is undermined when destination capability is inconsistent.
- User impact: Role confusion.
- Business/operational impact: Risky assumptions about permission vs feature availability.
- Likely root cause: Role model improved faster than route/system integration.
- Recommended fix: unify shell disclosure, RBAC guard semantics, and destination capability truth.
- Expected benefit: More predictable operator/admin experience.

### F-018

- Title: Route truth documentation is materially stale relative to runtime
- Severity: Critical
- Category: Product truth / Documentation / QA
- Affected surface: docs, tests, development process
- Evidence: `ROUTE_TRUTH_MATRIX_2026-04-17.md` and route-truth tests still describe routes such as `/alerts`, `/fabric`, `/agents`, `/memory`, `/entities`, `/context` in ways the runtime no longer fulfills.
- Why it matters: Engineers, QA, docs, and product stakeholders are working from conflicting truth.
- User impact: Bugs ship because internal truth is unstable.
- Business/operational impact: Rework and slower product convergence.
- Likely root cause: Docs are manually maintained rather than generated from runtime contracts.
- Recommended fix: generate route truth from router configuration plus per-route capability metadata.
- Expected benefit: Faster alignment and fewer regressions.

### F-019

- Title: Route truth test validates strings, not actual runtime behavior
- Severity: High
- Category: QA / Reliability
- Affected surface: route testing
- Evidence: `ui/src/__tests__/routes/routeTruthMatrix.test.ts`
- Why it matters: Tests can pass while users still land in incorrect destinations.
- User impact: Broken behaviors escape.
- Business/operational impact: False confidence in regression coverage.
- Likely root cause: Documentation reconciliation treated as a proxy for runtime truth.
- Recommended fix: replace with behavior-driven navigation tests that assert end-state screen identity and preserved state.
- Expected benefit: More meaningful route regression protection.

### F-020

- Title: Shell routing tests are placeholder-heavy and can mask real routing gaps
- Severity: High
- Category: QA / Reliability
- Affected surface: route behavior verification
- Evidence: `ui/src/__tests__/ShellRouting.test.tsx` contains placeholder patterns such as `expect(true).toBe(true)` and mocked navigation flows.
- Why it matters: Critical UX paths are not being verified with sufficient realism.
- User impact: Broken route handoffs persist.
- Business/operational impact: QA effort does not translate into product safety.
- Likely root cause: tests optimized for structural existence rather than behavior.
- Recommended fix: add router-integrated tests with real route config and screen assertions.
- Expected benefit: Better user-facing reliability.

### F-021

- Title: E2E critical path assumptions are likely stale relative to current shell
- Severity: Medium
- Category: QA / Drift
- Affected surface: e2e coverage
- Evidence: `ui/e2e/critical-path-smoke.spec.ts` expects patterns such as role switcher affordances that do not match current runtime naming and selectors.
- Why it matters: Smoke tests may be brittle or misleading.
- User impact: Regressions can be missed or tests can fail for the wrong reasons.
- Business/operational impact: reduced trust in CI signal.
- Likely root cause: shell evolution outpaced test updates.
- Recommended fix: align smoke flows to canonical selectors and current role-disclosure UI.
- Expected benefit: Higher-signal CI.

### F-022

- Title: AI is too visibly branded at shell level
- Severity: Medium
- Category: Content / Product strategy
- Affected surface: header and multiple pages
- Evidence: `DefaultLayout.tsx` “AI Powered” badge; multiple pages use overt AI framing.
- Why it matters: AI appears as product theater instead of embedded utility.
- User impact: Noise, skepticism, and higher cognitive load.
- Business/operational impact: lower perceived seriousness and trustworthiness.
- Likely root cause: assistance is being promoted as a feature rather than absorbed into workflows.
- Recommended fix: remove persistent AI branding and keep assistance contextual.
- Expected benefit: calmer, more credible product experience.

### F-023

- Title: AI implementation is fragmented across direct fetches, service hooks, heuristics, and separate affordances
- Severity: High
- Category: Frontend architecture / UX consistency
- Affected surface: AI assistant, query, insights, command bar
- Evidence: `AiAssistant.tsx` uses raw `fetch('/api/v1/...')`; other surfaces use service clients or heuristics.
- Why it matters: Users experience inconsistent loading, errors, confidence handling, and outputs.
- User impact: Unpredictable assistance quality.
- Business/operational impact: harder observability, governance, and reliability.
- Likely root cause: AI features grew opportunistically instead of through one assistance platform.
- Recommended fix: centralize AI request/response, confidence, fallback, and explanation patterns.
- Expected benefit: consistent and governable AI UX.

### F-024

- Title: Query assistance still leaves too much interpretation burden on the user
- Severity: High
- Category: AI/ML UX / Workflow simplification
- Affected surface: SQL Workspace
- Evidence: fallback chips, ambiguity clarification, manual SQL continuation in `SqlWorkspacePage.tsx`
- Why it matters: Product promise suggests assistance, but actual experience remains expert-heavy.
- User impact: lower adoption by less technical users.
- Business/operational impact: less leverage from embedded intelligence.
- Likely root cause: assistive layer does not yet own the end-to-end question-to-answer flow.
- Recommended fix: shift from SQL-first to question-first experience with explainable draft generation.
- Expected benefit: lower cognitive load and faster answers.

### F-025

- Title: Smart Workflow Builder promise exceeds current reliable automation level
- Severity: High
- Category: AI/ML UX / Product truth
- Affected surface: pipeline creation
- Evidence: unsupported boundaries, review-required behavior, placeholder configuration in builder flow
- Why it matters: Users enter a generator flow that often reverts to manual editing.
- User impact: disappointment and extra work.
- Business/operational impact: lower trust in AI-assisted creation.
- Likely root cause: incomplete backend contract and overexposed future-state UI.
- Recommended fix: reframe as guided setup until generation is reliable.
- Expected benefit: better expectation management and lower abandonment.

### F-026

- Title: Trust model is locally strong but not embedded across the product
- Severity: High
- Category: Privacy / Security UX
- Affected surface: collection creation, query, pipelines, plugins
- Evidence: `TrustCenter.tsx` has real lifecycle/actions, but upstream surfaces rarely surface those same truths contextually.
- Why it matters: users encounter trust considerations too late.
- User impact: more context switching and hidden risk.
- Business/operational impact: weaker governance adoption and more preventable mistakes.
- Likely root cause: trust work implemented as separate hub rather than pervasive system quality.
- Recommended fix: inject trust signals and actions into surrounding workflows contextually.
- Expected benefit: safer, lower-friction operations.

### F-027

- Title: Settings and API key UX currently create security-perception debt
- Severity: High
- Category: Security UX / Trust
- Affected surface: admin settings
- Evidence: unsupported boundary copy in settings registry and page.
- Why it matters: security-sensitive controls should never feel fake or placeholder.
- User impact: reduced confidence in secret handling.
- Business/operational impact: reputational risk.
- Likely root cause: transparency principle retained controls that should have been withheld.
- Recommended fix: hide secret-management surfaces until fully real and auditable.
- Expected benefit: stronger trust posture.

### F-028

- Title: Auth/session posture documentation is stale and conflicts with implementation
- Severity: Medium
- Category: Documentation / Security UX
- Affected surface: auth posture, internal development, support
- Evidence: `docs/FRONTEND_AUTH_SESSION_POSTURE_PLAN.md` says tokens are in `localStorage`; current implementation uses memory-first with `sessionStorage` fallback.
- Why it matters: internal trust and decision-making degrade when security docs are out of date.
- User impact: indirect, but real via slower or wrong remediation.
- Business/operational impact: security planning drift.
- Likely root cause: implementation advanced without doc update.
- Recommended fix: update posture docs immediately and mark historical notes explicitly.
- Expected benefit: better cross-team alignment.

### F-029

- Title: Duplicate API client layers create inconsistent UX behavior
- Severity: High
- Category: Frontend architecture / UX reliability
- Affected surface: all data-fetching flows
- Evidence: `ui/src/api/client.ts` and `ui/src/lib/api/client.ts` coexist; some components bypass both.
- Why it matters: auth, tenant context, retries, error formatting, and caching can diverge across surfaces.
- User impact: inconsistent errors, stale data, or broken auth behavior.
- Business/operational impact: higher maintenance and harder incident diagnosis.
- Likely root cause: migration or layering without final convergence.
- Recommended fix: consolidate onto one canonical API client contract.
- Expected benefit: more predictable behavior and simpler instrumentation.

### F-030

- Title: Duplicate component layers risk design and behavior drift
- Severity: High
- Category: Design system / Reuse
- Affected surface: shared components
- Evidence: duplicated common components under `libs/ui-components` and `ui/src/components/common`
- Why it matters: similar UI elements may look or behave differently over time.
- User impact: subtle inconsistency and lower perceived quality.
- Business/operational impact: slower standardization.
- Likely root cause: local overrides became parallel systems.
- Recommended fix: converge on one shared component source and deprecate duplicates.
- Expected benefit: better consistency and faster UI fixes.

### F-031

- Title: Partial design-system adoption creates uneven quality across forms and admin surfaces
- Severity: Medium
- Category: Design system / Consistency
- Affected surface: whole product
- Evidence: Collection form uses `@ghatana/design-system`; other surfaces still rely on ad hoc inputs/buttons/layouts.
- Why it matters: users experience different levels of polish and accessibility across the same product.
- User impact: inconsistent predictability and usability.
- Business/operational impact: slower convergence on accessibility and theming improvements.
- Likely root cause: incremental migration without enforcement.
- Recommended fix: define a migration matrix for all inputs, buttons, tabs, tables, and empty states.
- Expected benefit: more coherent product.

### F-032

- Title: Unsupported boundary surfaces are honest individually but excessive collectively
- Severity: High
- Category: IA / Product strategy / Cognitive load
- Affected surface: settings, plugins, fabric, builder, admin surfaces
- Evidence: multiple `UnsupportedSurfaceBoundary` uses and supporting registry.
- Why it matters: transparency becomes clutter when too many reachable pages primarily explain absence.
- User impact: repeated disappointment and mode-switching.
- Business/operational impact: lower perceived completeness.
- Likely root cause: boundary truth favored visibility over simplification.
- Recommended fix: keep boundaries for diagnostic/internal use, but remove from mainstream discovery unless actionably useful.
- Expected benefit: lower cognitive load and cleaner product.

### F-033

- Title: Operator and primary-user experiences are not separated cleanly enough
- Severity: High
- Category: IA / Role experience
- Affected surface: shell, home, insights, operations
- Evidence: home surface mixes multiple personas; nav/route differences are inconsistent.
- Why it matters: primary users see too much implied operator complexity; operators do not get coherent first-class workflows.
- User impact: increased confusion for both groups.
- Business/operational impact: role training burden.
- Likely root cause: progressive disclosure strategy is incomplete.
- Recommended fix: define explicit persona-specific route clusters and contextual handoffs.
- Expected benefit: simpler mental models.

### F-034

- Title: Observability is split between useful transparency and ambient noise
- Severity: Medium
- Category: Observability UX
- Affected surface: ambient intelligence bar, insights, operator summaries
- Evidence: `AmbientIntelligenceBar` and AI/insight sidebars surface broad telemetry and unsupported states persistently.
- Why it matters: visibility becomes clutter when not tightly contextual.
- User impact: attention fragmentation.
- Business/operational impact: important signals can be ignored.
- Likely root cause: desire for always-on intelligence visibility.
- Recommended fix: show observability only at task-relevant moments or in explicitly opened surfaces.
- Expected benefit: higher signal-to-noise ratio.

### F-035

- Title: Loading, partial, and unavailable states are not unified across the product
- Severity: Medium
- Category: Interaction quality / Design consistency
- Affected surface: all async pages
- Evidence: mixed use of spinners, fallback copy, unsupported boundaries, and silent redirects.
- Why it matters: users cannot form a stable expectation of state behavior.
- User impact: more interpretation required.
- Business/operational impact: harder QA and design governance.
- Likely root cause: state handling evolved page by page.
- Recommended fix: define one asynchronous experience standard.
- Expected benefit: smoother and more trustworthy interactions.

### F-036

- Title: Insights page is conceptually overloaded
- Severity: High
- Category: Information architecture
- Affected surface: Insights
- Evidence: overview, AI Brain, analytics, and cost tabs all coexist while also serving as redirect sink for alerts/events.
- Why it matters: one page is carrying too many disparate responsibilities.
- User impact: harder orientation and scanability.
- Business/operational impact: feature growth will worsen page sprawl.
- Likely root cause: consolidation without clear domain boundaries.
- Recommended fix: split or strongly hierarchy the page into operator diagnostics vs analytics/cost.
- Expected benefit: improved clarity and extensibility.

### F-037

- Title: Data Explorer is absorbing legacy routes without full product semantics
- Severity: High
- Category: IA / Flow coherence
- Affected surface: Data
- Evidence: redirects from `/memory` and `/entities`; Data Explorer only supports table, lineage, quality, schema.
- Why it matters: centralization is incomplete and silently wrong.
- User impact: destination mismatch.
- Business/operational impact: harder future refactor.
- Likely root cause: canonicalization strategy chosen before feature absorption was complete.
- Recommended fix: absorb fully or stop absorbing.
- Expected benefit: cleaner route model.

### F-038

- Title: Operations Console uses mock/preview content while acting as a consolidation target
- Severity: High
- Category: Product truth / Trust
- Affected surface: Operations
- Evidence: page comments and content indicate production would fetch real data; preview tools remain unavailable.
- Why it matters: consolidation into a mock-heavy page multiplies truth debt.
- User impact: uncertain reliability.
- Business/operational impact: operator confidence risk.
- Likely root cause: consolidation favored fewer routes over truthful routing.
- Recommended fix: keep Operations narrowly scoped to real admin diagnostics until data is live.
- Expected benefit: more trustworthy operator surface.

### F-039

- Title: Plugin management surface exposes more conceptual breadth than current actionability supports
- Severity: Medium
- Category: Product strategy / IA
- Affected surface: Plugins
- Evidence: installed, catalog boundary, deployment tabs and unavailable dependency graph
- Why it matters: broad structure suggests maturity beyond implementation.
- User impact: more exploratory dead ends.
- Business/operational impact: perceived incompleteness.
- Recommended fix: reduce to installed runtime facts until broader management exists.
- Expected benefit: tighter scope and lower clutter.

### F-040

- Title: Home and pipeline surfaces still expose AI as a concept users must think about
- Severity: Medium
- Category: Content / AI strategy
- Affected surface: Intelligent Hub, Workflows, Insights
- Evidence: repeated AI-specific cards, sidebars, pills, and naming
- Why it matters: the stated product principle is embedded, mostly invisible AI.
- User impact: more cognitive load and skepticism.
- Business/operational impact: diluted product value proposition.
- Recommended fix: rename or remove explicit AI framing unless required for governance or confidence explanation.
- Expected benefit: more modern and calmer product feel.

## 6. Comprehensive Simplification Plan

### Remove

- Remove `Events` and `Alerts` as separate nav items until they resolve correctly or become real tabs.
- Remove stale quick-nav items from `GlobalSearch`.
- Remove persistent “AI Powered” shell badge.
- Remove direct-link discoverability for fully boundary-only admin settings.
- Remove any page or tab from primary discovery whose main purpose is explaining absence.

### Merge

- Merge route truth, nav truth, and search truth into one canonical capability registry.
- Merge duplicate API clients into one.
- Merge duplicate shared component layers into one source of truth.
- Merge overlapping AI assistance behaviors into one assistance platform with common confidence/fallback UX.

### Hide by default

- Hide operator/admin surfaces from primary users more aggressively.
- Hide preview-only or read-only operational pages from mainstream navigation.
- Hide advanced workflow and raw SQL complexity until the user needs it.

### Automate

- Auto-suggest home actions based on recency and urgency.
- Auto-classify collection sensitivity and retention defaults.
- Auto-rank workflows by attention required.
- Auto-summarize query results and ambiguity.
- Auto-group alerts and propose remediation actions.
- Auto-summarize governance deltas and audit changes.

### Prefill / preconfigure

- Collection schema, tags, retention class, quality defaults
- Workflow connector mappings and validation rules
- Query scope and table suggestions
- Plugin health explanations and upgrade suggestions

### Make contextual

- Trust signals at creation, query, and execution time
- Cost signals at query and workflow optimization moments
- AI explanations only when confidence is low or review is required
- Operational visibility only when a user’s task depends on it

### Move to advanced/admin mode

- Raw operational metrics
- deep plugin topology and deployment controls
- advanced workflow canvas edits
- raw SQL and explain plans
- low-level fabric metrics

### Standardize as reusable patterns

- page shell with `main` landmark and heading stack
- search/filter bar with visible labels
- async states
- unsupported/preview/live lifecycle banners
- confirmation and destructive-action flows
- audit trail presentation
- AI confidence and fallback presentation

## 7. Comprehensive AI/ML Embedding Plan

### Collections

- Opportunity: schema inference and field typing
- User value: faster setup, fewer errors
- Mode: assist by default, automate low-risk defaults
- Confidence threshold: automate above high confidence; require review for ambiguous fields
- Fallback: plain preset templates
- Review triggers: new sensitive fields, low-confidence type inference
- Override model: editable before save
- Visibility model: concise “suggested from sample data” note
- Privacy/security: never expose sample values unnecessarily
- Priority: Immediate

- Opportunity: sensitivity and retention classification
- User value: safer defaults
- Mode: assist with recommended policy class
- Confidence threshold: require confirmation for anything sensitive
- Fallback: manual policy selection
- Review triggers: regulated data indicators
- Visibility model: inline trust chip on form
- Priority: Immediate

### Data Explorer

- Opportunity: anomaly summarization
- User value: quicker diagnosis
- Mode: assist
- Confidence threshold: medium confidence acceptable if explanation included
- Fallback: raw metrics only
- Review triggers: destructive remediation proposals
- Visibility model: one-card issue summary
- Priority: Short-term

- Opportunity: lineage impact explanation
- User value: faster understanding of breakage risk
- Mode: assist
- Fallback: static lineage graph
- Priority: Short-term

### Pipelines

- Opportunity: intent-to-draft workflow generation
- User value: faster pipeline creation
- Mode: assist first, automate only with review
- Confidence threshold: high structure confidence required before creating a runnable draft
- Fallback: guided manual builder
- Review triggers: external side effects, data movement, policy impact
- Override model: editable nodes/steps with provenance
- Visibility model: concise draft rationale and confidence
- Privacy/security: audit generated transformations and approvals
- Priority: Short-term

- Opportunity: failure triage and next-best-action
- User value: less manual diagnosis
- Mode: assist
- Fallback: static failure summaries
- Priority: Immediate

### Query

- Opportunity: NLQ to SQL draft
- User value: lower barrier to data access
- Mode: assist as primary, SQL as advanced
- Confidence threshold: high enough to propose, not necessarily auto-run
- Fallback: templates and schema search
- Review triggers: large-scan query, sensitive tables, ambiguous join paths
- Override model: editable SQL before run
- Visibility model: “Generated from your question; review before run”
- Privacy/security: enforce policy-aware table/column access
- Priority: Immediate

- Opportunity: result summarization and follow-up suggestions
- User value: faster interpretation
- Mode: assist
- Fallback: raw result table
- Priority: Short-term

### Alerts and Events

- Opportunity: deduplication and root-cause clustering
- User value: lower noise
- Mode: automate clustering, assist remediation
- Confidence threshold: moderate acceptable if reversible
- Fallback: raw grouped alerts
- Review triggers: auto-resolution, escalation, suppression
- Override model: operator can split/merge or reject grouping
- Visibility model: clear “system grouped these related alerts”
- Privacy/security: keep incident data role-scoped
- Priority: Immediate once routing is fixed

### Trust

- Opportunity: policy recommendation and risk highlighting
- User value: faster governance action
- Mode: assist
- Confidence threshold: high for recommended policy changes; human approval required for enforcement
- Fallback: manual review queue
- Review triggers: purge, redaction, access changes
- Visibility model: explicit rationale, impacted collections, confidence
- Priority: Immediate

### Plugins

- Opportunity: health anomaly explanation and upgrade risk scoring
- User value: easier runtime management
- Mode: assist
- Fallback: health/status facts only
- Priority: Medium-term

### Cross-product governance rules for AI

- Use one confidence model and explanation pattern.
- Never use AI branding as a substitute for product clarity.
- Keep AI mostly invisible until it reduces a real task.
- Require human review for destructive, compliance-sensitive, externally visible, or high-cost actions.

## 8. Comprehensive Trust / Transparency / Privacy / Security UX Review

### Current gaps

- Capability truth is not consistently surfaced at the action point.
- Role-based disclosure is clearer than route-based capability truth.
- Settings expose security-sensitive concepts without real controls.
- Security posture docs are stale in at least one important area.
- Alert/event/operator flows do not provide trustworthy context-preserving handoffs.

### Future-state trust model

- Every surface should declare only what is real, preview, read-only, or unavailable.
- Dangerous or sensitive actions should use:
  - impact preview
  - reason requirement
  - dry run when possible
  - audit trail after completion
- Role-specific transparency:
  - primary users see only the relevant safe summary
  - operators see actionable diagnostics
  - admins see privileged controls and blast-radius context

### Operational visibility

- Show what happened, what is pending, and whether attention is required.
- Do not keep ambient system bars visible if they do not influence the current task.
- Standardize timeline/history patterns across pipelines, alerts, plugins, and trust actions.

### Auditability

- Trust Center already points in the right direction with lifecycle and audit concepts.
- Extend the same audit pattern to:
  - generated workflow drafts
  - alert resolutions
  - plugin state changes
  - query execution against sensitive resources

### Permission clarity

- Every admin/operator surface should make role requirements explicit before navigation if possible.
- Shell role should never visually imply backend permission.
- Redirected routes must preserve permission semantics rather than rewrite them.

### Sensitive action handling

- Purge, revoke, enable/disable runtime components, and policy enforcement changes need explicit impact previews.
- Success states should confirm what changed and where it is auditable.

### Privacy controls and disclosures

- Surface privacy-impacting defaults in collection setup and query execution contexts.
- Do not relegate all privacy meaning to Trust alone.

### Safe defaults

- default to non-destructive actions
- default to dry-run and review for risky actions
- default to least surprise in routing and discoverability

## 9. Design System / Consistency / Reuse Review

### Inconsistent components

- Duplicate shared components in `libs/ui-components` and `ui/src/components/common`
- Partial use of `@ghatana/design-system` instead of universal adoption
- Different page shells and search/filter treatments across primary and admin/operator pages

### Behavior drift

- Nav says one thing, router does another, docs say another
- Search quick nav lags behind route changes
- Settings remains discoverable despite lack of actionability

### Naming drift

- Home vs Dashboard vs Hub aliases
- Insights as analytics page, operator page, cost page, and redirect sink
- “AI Brain” naming vs embedded-assistance product principle

### Repeated UI patterns that should be centralized

- search bars
- tab bars
- empty states
- unsupported/preview boundaries
- audit timelines
- labeled filters
- section headers

### Missing shared abstractions

- canonical route capability registry
- route-entry-state handling pattern
- one async-state contract
- one AI assistance contract
- one page-shell semantic layout pattern

### Token/style inconsistency

- Uneven spacing, density, and semantic structure across modernized vs older/admin pages

### Standardization plan

- Establish one design-system migration board by component family.
- Require all new page work to use semantic page shell, labeled fields, and shared lifecycle-state components.
- Generate navigation/search from canonical route metadata rather than handwritten duplicates.

## 10. Prioritized Remediation Roadmap

### Immediate

1. Fix all broken route handoffs for `alerts`, `events`, `memory`, `entities`, `context`, `fabric`, and `agents`
- Priority: P0
- Effort: Medium
- Impact: Very high
- Dependencies: route/capability decision per surface
- Owner type: frontend, product
- Rationale: Current behavior materially breaks trust and discoverability.

2. Resolve accessibility failures on Data, Create Collection, Workflows, Insights, and Plugins
- Priority: P0
- Effort: Medium
- Impact: Very high
- Dependencies: shared page-shell and form-label patterns
- Owner type: frontend, design
- Rationale: Core compliance and usability issue.

3. Remove stale nav/search items until destinations are truthful
- Priority: P0
- Effort: Low
- Impact: High
- Dependencies: route decisions
- Owner type: frontend, product
- Rationale: Fastest way to reduce user confusion.

4. Reclassify Settings out of mainstream discovery
- Priority: P0
- Effort: Low
- Impact: High
- Dependencies: none
- Owner type: product, frontend
- Rationale: Eliminates admin false affordance immediately.

5. Update stale auth/session posture documentation
- Priority: P1
- Effort: Low
- Impact: Medium
- Dependencies: none
- Owner type: frontend, security, docs
- Rationale: Internal truth must match implementation.

### Short-term

1. Create a canonical route capability registry that drives router, nav, search, and docs
- Priority: P1
- Effort: Medium
- Impact: Very high
- Dependencies: product decisions on retained/removed surfaces
- Owner type: frontend, product, platform
- Rationale: Prevents recurrence of truth drift.

2. Consolidate API client layers and standardize async/error handling
- Priority: P1
- Effort: Medium
- Impact: High
- Dependencies: service migration plan
- Owner type: frontend, platform
- Rationale: Improves reliability and consistency.

3. Standardize page shell, labels, empty/loading/error states
- Priority: P1
- Effort: Medium
- Impact: High
- Dependencies: design-system agreement
- Owner type: design, frontend
- Rationale: Reduces both accessibility and consistency debt.

4. Reframe Smart Workflow Builder as guided setup unless generation is truly ready
- Priority: P1
- Effort: Medium
- Impact: High
- Dependencies: product messaging and AI capability review
- Owner type: product, design, frontend, ML
- Rationale: Aligns promise with reality.

5. Make Query question-first with SQL as progressive disclosure
- Priority: P1
- Effort: Medium
- Impact: High
- Dependencies: AI assistance platform work
- Owner type: product, frontend, ML
- Rationale: Major simplification lever.

### Medium-term

1. Converge duplicate component layers and accelerate design-system migration
- Priority: P2
- Effort: High
- Impact: High
- Dependencies: component inventory
- Owner type: frontend, design-system/platform
- Rationale: Systemic consistency and maintainability.

2. Embed trust signals into collection, pipeline, and query flows
- Priority: P2
- Effort: Medium
- Impact: High
- Dependencies: trust model design
- Owner type: product, design, frontend, backend
- Rationale: Trust should be pervasive, not isolated.

3. Re-scope Insights into a coherent diagnostic/analytics structure
- Priority: P2
- Effort: Medium
- Impact: High
- Dependencies: operator IA decisions
- Owner type: product, design, frontend
- Rationale: Prevents future sprawl.

4. Improve operator experiences for alerts, events, plugins, and operations
- Priority: P2
- Effort: Medium to High
- Impact: High
- Dependencies: route truth and live backend capability boundaries
- Owner type: product, frontend, backend
- Rationale: Operators currently lack a fully coherent experience.

### Long-term

1. Deliver mature AI assistance platform with shared confidence, fallback, audit, and explanation models
- Priority: P3
- Effort: High
- Impact: Very high
- Dependencies: platform and ML investment
- Owner type: ML, frontend, platform, product
- Rationale: Enables durable embedded-intelligence UX.

2. Expand trustworthy admin settings only when real backing services exist
- Priority: P3
- Effort: High
- Impact: Medium
- Dependencies: backend settings APIs and secret-management model
- Owner type: backend, security, frontend, product
- Rationale: Avoid repeating false-affordance pattern.

3. Build role-specific workspace experiences with coherent progressive disclosure
- Priority: P3
- Effort: High
- Impact: High
- Dependencies: IA redesign
- Owner type: product, design, frontend
- Rationale: Reduces cognitive load across personas.

## 11. Final Ideal UX Vision

After remediation, Data Cloud should feel calm, fast, and trustworthy.

The user opens the product and immediately sees the next action that matters, not a wall of systems thinking. Primary users move through collection setup, querying, and workflow creation with minimal choices and strong defaults. Operators have a coherent, first-class set of diagnostics that preserve context when they navigate. Admins only see controls that are real, safe, and auditable.

The system quietly automates the mechanical work:

- it suggests schemas and policies during setup
- drafts query logic from questions
- ranks pipeline issues by urgency
- groups related alerts
- summarizes anomalies and likely causes
- recommends safe next actions

AI/ML remains embedded and mostly invisible. Users experience it as less work, clearer prioritization, and better defaults, not as a constant branded layer they have to manage.

Trust remains pervasive but non-intrusive. Sensitive actions surface the right amount of context at the right time. Users can see what happened, what is pending, and why action is needed, but the product does not flood them with ambient telemetry. Privacy, security, and observability feel built in because the product behaves consistently, explains itself clearly, and never promises a capability it does not actually deliver.

## 12. Executive Summary Lists

### Top 10 Critical Issues

1. `/alerts` redirects into an Insights page that does not honor alert context.
2. `/events` redirects into an Insights page that does not honor event context.
3. `/fabric` and `/agents` now route into an admin-only console, changing role semantics.
4. `/memory`, `/entities`, and `/context` redirect into destinations that do not support the intended state.
5. Accessibility audit failures exist on core pages, including landmark and label issues.
6. Route truth docs/tests are stale and do not protect real runtime behavior.
7. Navigation and search still expose stale or misleading destinations.
8. Settings remains discoverable despite being entirely boundary-only.
9. Duplicate API/client/component layers are causing systemic consistency drift.
10. AI is overexposed and fragmented rather than embedded and governable.

### Top 10 Simplification Opportunities

1. Collapse route truth, nav truth, and search truth into one registry.
2. Remove boundary-only or broken surfaces from mainstream discovery.
3. Turn Query into question-first, SQL-second.
4. Turn workflow creation into one guided flow with advanced mode as disclosure.
5. Reduce Intelligent Hub to resume, create, and attention-needed actions.
6. Remove persistent shell-level AI branding.
7. Standardize page shells, filters, and async states.
8. Push trust signals upstream into creation, query, and workflow moments.
9. Reduce Insights scope or split it by domain.
10. Remove legacy redirects that are not fully supported.

### Top 10 AI/ML Opportunities

1. Collection schema inference from sample data
2. Sensitive-data and retention classification at creation time
3. Query question-to-SQL draft generation
4. Query result summarization and follow-up question suggestions
5. Pipeline intent-to-draft generation with review gates
6. Workflow failure triage and next-best-action suggestions
7. Alert deduplication and root-cause clustering
8. Governance policy recommendations and audit summarization
9. Data quality anomaly explanation in Data Explorer
10. Plugin health risk scoring and upgrade suggestions

### Top 10 Trust / Visibility / Privacy / Security UX Improvements

1. Fix all misleading redirects before adding new features.
2. Surface capability truth at the point of entry and action.
3. Hide faux settings and secret-management surfaces until real.
4. Standardize destructive-action flows with dry run and audit trail.
5. Unify role disclosure with route and capability semantics.
6. Embed trust cues into collection, query, and pipeline workflows.
7. Standardize operational visibility to be contextual, not ambient by default.
8. Update stale auth/security posture docs immediately.
9. Add clear lifecycle labeling for live, preview, read-only, and unavailable surfaces.
10. Ensure all operator/admin controls meet the same accessibility quality as primary-user flows.

## Appendix: Verification Notes

- Route truth test run: `ui/src/__tests__/routes/routeTruthMatrix.test.ts` passed, but it validates string/document alignment more than live runtime behavior.
- Accessibility audit run: `ui/src/__tests__/accessibility/AccessibilityAudit.test.tsx` failed 11 checks across 52 tests.
- Conclusion: the product has meaningful truth drift between documentation, tests, and current runtime behavior, and the accessibility issues are concrete rather than speculative.
