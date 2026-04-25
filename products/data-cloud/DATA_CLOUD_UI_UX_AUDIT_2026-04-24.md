# Data Cloud UI/UX Audit and Remediation Blueprint

Date: 2026-04-24
Product: Data Cloud
Scope: `products/data-cloud`, especially `products/data-cloud/ui`
Audit type: end-to-end UI/UX, product workflow, design system, accessibility, trust, correctness, completeness, and frontend implementation review

## Evidence Sources

This report is evidence-based and uses the following sources:

- Frontend routes, pages, components, API clients, tests, and runtime-boundary code under `products/data-cloud/ui`.
- Product/readiness docs under `products/data-cloud/docs`, `products/data-cloud/docs-generated`, `products/data-cloud/ROUTE_TRUTH_MATRIX_2026-04-17.md`, and prior audit material.
- Live browser inspection of the Vite app at `http://127.0.0.1:5173/`.
- Targeted test execution:
  - `pnpm --filter @data-cloud/ui exec vitest run src/__tests__/accessibility/AccessibilityAudit.test.tsx src/__tests__/routes/routeTruthMatrix.test.ts src/__tests__/ShellRouting.test.tsx src/__tests__/pages/SettingsPage.test.tsx src/__tests__/pages/WorkflowsPage.test.tsx src/__tests__/pages/SqlWorkspacePage.test.tsx`
  - Result: failed. 5 failed files, 1 passed file, 16 failed tests, 63 passed tests.
- Runtime console evidence:
  - MSW failed to register because `mockServiceWorker.js` was served with `text/html`; `/data` remained indefinitely on `Loading...` without backend/mock recovery.
- Important assumption:
  - The audit treats UI behavior as currently implemented and runnable. Where the code says a surface is intentionally boundary-limited, that boundary is treated as product truth unless contradicted by other source or runtime behavior.

## 1. Executive Summary

Overall UI/UX health is High risk. Data Cloud has a broad product shell and many useful primitives, but the experience is not yet production-ready as a coherent product experience because route truth, capability truth, runtime availability, and user-facing controls drift across pages. The largest issue is not visual styling alone; it is that the UI often looks more complete and more operational than it actually is.

Completeness is High risk. The product exposes many first-class surfaces, including Data, Pipelines, Query, Trust, Insights, Alerts, Operations, Events, Memory, Entities, Context, Fabric, Agents, Plugins, and Settings. Several are partial, preview, degraded, or boundary-limited, but are still discoverable or action-oriented. Major flows lack closure, pagination, reliable error states, deep-link correctness, recovery paths, and consistent background-operation visibility.

Simplicity is High risk. The shell currently increases cognitive load through over-broad navigation, AI-branded surfaces, dense cards, multiple entry points for similar concepts, static search shortcuts, and dashboard-style panels that compete for attention. The desired "near-zero cognitive load" state is not yet achieved. Users must infer which actions are real, which are previews, which are role-gated, which depend on unavailable capabilities, and which are local-only demonstrations.

Correctness is Critical risk. Several controls represent capabilities that are not actually wired, including Settings save/toggle/API-key actions, Workflows run/pause/menu actions, Data Explorer filter/sort parameters, static query trust badges, and Data Fabric live-metrics claims. The UI sometimes shows zeros or current client time where backend state is unavailable, which can mislead operators.

Consistency is High risk. There are multiple truth sources for routes and capabilities: `routes.tsx`, `DefaultLayout.tsx`, `RouteCapabilityRegistry.ts`, `GlobalSearch.tsx`, tests, docs, and unsupported-surface registries. These sources disagree. Components, loading states, empty states, forms, modal patterns, destructive actions, terminology, role exposure, and AI assistance patterns vary by page.

The biggest workflow weaknesses are first-run setup, collection creation and inspection, workflow execution, query assistance, alert triage, settings/admin operations, and preview/operator surfaces. The most important systemic fix is to make capability truth canonical and enforce it through navigation, search, routes, components, and docs.

Production-readiness assessment: not ready for a broad production release as an operator/admin-facing data product. It can support internal demos and targeted development validation, but the user experience needs a correctness and boundary-hardening pass before it should be trusted as a complete operational console.

## 2. Deep Audit Scorecard

Rating represents UX/product risk level.

| Area | Rating | Justification |
|---|---:|---|
| Completeness | High | Many surfaces are present, but core states and closures are missing: direct data detail resolution, durable settings, workflow action execution, query-derived trust state, pagination, backend failure recovery, role-specific closures, and operational histories. |
| Simplicity | High | The shell exposes too many concepts at once and asks users to distinguish canonical, legacy, preview, unavailable, AI-assisted, operator, and admin surfaces. Onboarding and home are especially heavy in narrow layouts. |
| Correctness | Critical | Multiple UI controls and summaries misrepresent operational reality. Examples include fake settings/API-key actions, inert workflow controls, ignored API filters/sort, static trust badges, local-only AI/guardrail logic, and fallback zeros in Operations. |
| Consistency | High | Route, nav, search, docs, tests, and capability registries disagree. Similar actions use different patterns. Error/loading/empty states are fragmented. |
| Information Architecture | High | Product concepts are split across Data, Entities, Context, Memory, Fabric, Insights, Trust, Alerts, and Operations without a simple progressive model. Several operator routes are direct-only or inconsistently discoverable. |
| Navigation | High | Static nav is used while registry-driven nav helpers exist. Global search exposes stale and unsupported route entries. Role disclosure is UI-only but can look like authorization. |
| Workflow Simplicity | High | Primary journeys require too much inference. Users encounter hidden capability boundaries, local-only actions, placeholder states, and repeated manual inputs. |
| Visual Design | Medium | Layout, spacing, and components are generally serviceable, but dashboard density, nested cards, cramped responsive states, emoji/icon drift, and mixed component treatments reduce polish. |
| Interaction Quality | High | Critical actions are sometimes inert, unsafe, unlabeled, or lack confirmation/undo/reason capture. Async feedback is inconsistent. |
| Accessibility | High | Test failures show crashes and heading hierarchy issues. Live onboarding/mobile layout showed cramped controls. Nested `<main>` landmarks appear in multiple pages. Several selects and icon buttons need better labels. |
| AI/ML Embedded Experience | High | AI is often visible as a branded feature rather than embedded as quiet prioritization, defaults, triage, summarization, or automation. Some AI behaviors are heuristic/local while the UI implies stronger intelligence. |
| Observability/Transparency UX | High | Operators cannot consistently see what is running, what failed, what retried, what changed, who acted, or why. Some dashboards show misleading empty/zero states when data is unavailable. |
| Privacy/Security UX | High | Trust signals are sometimes static or not data-derived. Sensitive operations lack consistent confirmations, reasons, approvals, audit links, or impact previews. Settings shows unavailable security features as if they work. |
| Responsive Behavior | High | Live inspection showed onboarding and home layouts collapse in a narrow viewport. Several surfaces rely on dense grids/tables and side panels without strong mobile alternatives. |
| Perceived Performance | Medium | Lazy routes and query states exist, but many loaders are generic and indefinite. Missing MSW/backend recovery makes routes feel broken rather than degraded. |
| Cognitive Load | High | Users must understand too many surfaces, states, role hints, capability boundaries, and manual options. The UI does not yet automate enough routine interpretation. |
| Overall Product Usability | High | Broad but not cohesive. Best viewed as a capable workbench with substantial correctness and completion debt before it becomes a simple, trustworthy product. |

## 3. Complete Surface-by-Surface Audit

### 3.1 Shell, Navigation, Roles, and Global Search

Purpose: provide the primary product map, role-based disclosure, route access, and cross-surface command/search entry.

Completeness:
- Routes exist for all major surfaces in `products/data-cloud/ui/src/routes.tsx`, including `/data`, `/pipelines`, `/query`, `/trust`, `/insights`, `/alerts`, `/operations`, `/events`, `/memory`, `/entities`, `/context`, `/fabric`, `/agents`, `/settings`, and `/plugins`.
- The shell has static nav sections in `DefaultLayout.tsx` while `RouteCapabilityRegistry.ts` also defines canonical discoverability and role metadata.
- Search has its own static `quickNavItems` in `GlobalSearch.tsx`.
- Docs and route truth matrix are stale relative to current routes.

Simplicity:
- The shell exposes too many advanced concepts without a progressive model. Data, Entities, Context, Memory, Fabric, Events, Alerts, Insights, Trust, Operations, Agents, and Plugins are all separate concepts.
- The role selector says disclosure only, but from a user perspective it still feels like switching access modes.

Correctness:
- `DefaultLayout.tsx` comments claim navigation is registry-backed, but actual navigation uses static `navSections`.
- `GlobalSearch.tsx` exposes static route entries including `nav-alerts` and legacy-like items, while route truth tests expect different behavior.
- `RouteCapabilityRegistry.ts` marks some preview or direct routes as active/discoverable in ways that conflict with docs and nav.
- Shell role is explicitly "UI disclosure only" in `src/lib/auth/session.ts`, but some pages use role gates such as `RBACGuard`. The distinction is correct internally but not simple enough for users.

Consistency:
- Route truth is spread across at least six sources: routes, layout, registry, global search, tests, docs, and unsupported-surface registries.
- Role labels, discoverability, and lifecycle labels are not consistently enforced.

Issues and recommendations:
- Make `RouteCapabilityRegistry.ts` the only source for route lifecycle, role, discoverability, aliases, legacy redirects, and boundary state.
- Generate shell nav, global search, breadcrumbs, route guards, and docs from the same metadata.
- Add visible but concise boundary states when a route is direct-only, preview, unavailable, or admin-gated.
- Replace broad nav exposure with a simpler primary workflow model: Home, Data, Workflows, Query, Trust, Operations. Move Memory, Entities, Context, Fabric, Events, Agents, and deep diagnostics behind contextual drill-ins or admin/operator views.

### 3.2 Onboarding and First-Run Setup

Purpose: guide first-time users through connection, tenant, collection creation, and optional AI assist.

Completeness:
- Wizard exists in `DataCloudOnboardingWizard.tsx`.
- The wizard captures setup intent, but it does not complete a real backend setup flow. It mainly stores completion in local storage.
- It references future configuration in `Settings -> AI`, but Settings has no AI section.

Simplicity:
- Four-step onboarding is understandable conceptually, but live rendering in a narrow viewport collapsed controls and presented the stepper as dense plain text.
- The first-run experience introduces too many product claims before the product has established basic operational readiness.

Correctness:
- Copy promises capabilities such as natural-language query and future AI configuration that are not consistently available.
- The "Create Your First Collection" copy points users to Entity Browser later, while primary navigation is Data.

Consistency:
- Onboarding language does not match current route names and admin boundaries.
- The UI styling does not hold up in responsive/narrow contexts.

Recommendations:
- Convert onboarding into a capability-aware checklist: Connect data, confirm tenant, create or inspect first collection, run first query, review trust posture.
- Hide AI setup unless the capability registry says AI is available.
- Replace local-only completion with setup state when backend endpoints exist, or explicitly label it as local setup guidance.
- Fix responsive modal layout and button spacing before release.

### 3.3 Home / Intelligent Hub

Purpose: orient users to current state, next actions, operational context, and recommended workflows.

Completeness:
- Home provides context panels, observed system state, assistant affordances, and entry points.
- It does not reliably collapse complexity into a small set of next-best actions.

Simplicity:
- Live inspection after onboarding showed a narrow layout with a context sidebar, large heading, multiple panels, and a floating assistant control competing for attention.
- The page is too dashboard-like for a first screen; it asks the user to interpret too many signals.

Correctness:
- Some outcome classification appears heuristic/local. That is acceptable for development, but the UI should not imply authoritative AI reasoning unless backed by actual capability state.

Consistency:
- AI and observability are presented differently here than in Query, Insights, Alerts, and Workflows.

Recommendations:
- Make Home a prioritized work queue:
  - "Needs attention"
  - "Recently changed"
  - "Recommended next action"
  - "System status"
- Move deeper context, o11y, learning, and assistant panels behind progressive disclosure.
- Use the same capability and trust signal components used across operator pages.

### 3.4 Data Explorer and Collection Detail

Purpose: browse, search, create, inspect, and govern collections.

Completeness:
- Data Explorer fetches collections and supports view modes for table, lineage, quality, and schema.
- It lacks a durable deep-link detail model: selecting a collection is local state; direct `/data/:id` does not clearly fetch/select that collection.
- Quality view is a placeholder: "Quality metrics are computed async. Use the Quality service endpoint for detailed metrics."
- Error and retry states are missing in the main loading path.

Simplicity:
- The view-mode model is understandable, but users must first select a collection. The page does not guide empty or no-selection states strongly enough.
- Quality and lineage are visible as tabs even when data may not be ready.

Correctness:
- `collectionsApi.list` accepts status, schema type, sort, and order parameters but only sends `limit`, `offset`, and `search`.
- Trust fields collected during creation are not fully sent through the create DTO.
- With MSW unavailable and no backend, `/data` stayed on an indefinite `Loading...` state.

Consistency:
- Collection state, badges, details, schema, quality, and lineage use page-specific patterns instead of a unified data asset detail shell.

Recommendations:
- Implement `/data/:id` as a true detail route that fetches by ID, handles not found, loading, forbidden, degraded, and stale states.
- Gate quality/lineage tabs by capability and collection availability.
- Wire filters and sort to API or remove them until supported.
- Standardize collection detail, schema, lineage, quality, trust, activity, and settings into one reusable asset-detail layout.

### 3.5 Collection Create/Edit Forms

Purpose: create and edit data collections with schema and governance metadata.

Completeness:
- The form captures name, description, schema type, active state, sensitivity, retention, and schema fields.
- The submit path drops several governance fields because the backend DTO does not yet support them.
- Accessibility tests crash due `SensitivityBadge` receiving undefined/invalid sensitivity.

Simplicity:
- Schema field creation is functional but manual. The system could infer schema from source data, pasted JSON, uploaded samples, or existing collection patterns.

Correctness:
- UI captures governance metadata but does not persist it. This is a severe false-completion issue.
- Badge rendering assumes valid inputs and can crash.

Consistency:
- The governance form does not align with Trust Center's policy model or boundary language.

Recommendations:
- Persist governance fields or clearly label them as not yet persisted.
- Add defensive rendering for trust badges.
- Add import/infer schema paths and validation summaries.
- Make successful creation land on a real collection detail page with visible persisted values.

### 3.6 Workflows / Pipelines

Purpose: list, monitor, create, run, pause, inspect, and troubleshoot workflows.

Completeness:
- Workflows page lists workflows, status, metrics, filters, and a detail modal.
- Primary actions are operationally incomplete: action menu items such as Run Now, Edit, View Logs, Delete are inert; modal Run Now and Pause buttons are inert.
- Only the first 12 workflows are displayed via `workflows.slice(0,12)` with no clear pagination or "show all".
- `workflowsApi.list` exposes search/sort/page parameters but implementation only sends limit/status.

Simplicity:
- The page is busy: summary cards, filters, workflow cards, AI/optimization hints, lifecycle status, and modals compete.
- The distinction between simple pipeline editor, advanced editor, workflow designer, and smart builder is not obvious.

Correctness:
- Visible controls imply execution, pause, logs, and delete support that do not happen.
- Search and sort controls can mislead users because API support is partial.

Consistency:
- Workflow lifecycle patterns differ from Alerts, Operations, and Event Explorer.

Recommendations:
- Remove or disable inert controls with explicit boundary copy.
- Wire run/pause/log/delete with loading, success, failure, undo/confirm, audit event, and retry states.
- Consolidate workflow creation into one progressive builder with advanced mode.
- Add a workflow execution detail surface with timeline, inputs, outputs, retries, logs, and related alerts.

### 3.7 Smart Workflow Builder and Workflow Designer

Purpose: create workflows with assisted drafting and visual/manual editing.

Completeness:
- Builder and validation utilities exist, including workflow validation logic and persistence helpers.
- AI assist has runtime boundary messages for unavailable/degraded states.
- The relationship between smart builder, manual editor, designer, and execution monitor is not fully unified.

Simplicity:
- Users should not have to choose among multiple creation modes up front. They should state an outcome, get a proposed workflow, then refine visually or manually if needed.

Correctness:
- AI-generated suggestions must be explicitly advisory when degraded/unavailable.
- Validation exists but must be tied to publish/run gating consistently.

Consistency:
- Workflow creation should share validation, error presentation, undo/history, and execution state with the Workflows page.

Recommendations:
- One "Create workflow" entry. Default to guided outcome capture, then expose visual/manual controls progressively.
- Require validation before run/publish.
- Show generated steps, confidence, missing credentials, risk, and required review as structured metadata.

### 3.8 Query / SQL Workspace

Purpose: write, assist, run, save, explain, and inspect SQL queries.

Completeness:
- SQL workspace includes query editor, AI assist, guardrail/rewrite suggestions, saved query patterns, and execution states.
- Some saved-query behavior uses mock queries.
- Trust badges are static and not query-derived.

Simplicity:
- The AI assist interaction is too prominent and mode-based. Tests expecting "AI Assist" no longer match current button names such as "Ask a Question" / "Hide Question", signaling UX drift.

Correctness:
- Query guardrails and rewrite suggestions are local heuristics; the UI must not imply authoritative backend governance.
- Static trust badges such as tenant access and internal sensitivity can misrepresent the actual query, selected collection, or result set.
- Tests for clarification prompts and AI input visibility fail, showing state/contract drift.

Consistency:
- Query assistance, insights assistance, workflow assistance, and global assistant patterns are not unified.

Recommendations:
- Make trust indicators derive from parsed query targets and backend policy evaluation.
- Treat AI as "suggested next action" and "explain query/result" rather than a separate branded mode.
- Standardize save, export, format, clear, run, cancel, retry, and explain states.
- Replace mock saved queries with durable saved-query API or a visible local-only boundary.

### 3.9 Trust Center and Governance

Purpose: show compliance posture, policies, audit logs, recommendations, and guarded trust operations.

Completeness:
- Trust Center is one of the more complete surfaces: it has policies, audit logs, compliance report, recommendations, lifecycle surfaces, classify/redact/purge operations, and purge dry-run/confirmation flow.
- Access review is still boundary-limited.
- Search and recommendations need stronger traceability to source policy and data assets.

Simplicity:
- The page is dense and operator-heavy. Quick actions, policy coverage, compliance, lifecycle, logs, recommendations, and modals need clearer prioritization.

Correctness:
- Some policy compliance status is derived from enabled/pending state rather than proven compliance.
- The purge flow is better than most destructive operations because it has dry-run and confirmation token; this pattern should be reused elsewhere.

Consistency:
- Trust Center has stronger operational safeguards than Alerts, Entity Browser, Settings, and Fabric. That inconsistency should be fixed by promoting its patterns into shared sensitive-action components.

Recommendations:
- Use Trust Center as the model for sensitive operation UX: impact preview, confirmation token, reason capture, audit link, and post-action result.
- Add role-specific views: data steward, operator, admin, auditor.
- Connect recommendations to explainable evidence and next actions.

### 3.10 Insights

Purpose: provide analytics, readiness, capability, cost, and diagnostic insight.

Completeness:
- Insights includes capability panels, bootstrap/session visibility, optional analytics SQL console, workflow/cost/collection data, and boundary messaging.
- Several insights depend on optional capabilities and should be visibly unavailable rather than mixed into normal content.

Simplicity:
- Insights reads partly like an internal diagnostic console and partly like an analytics dashboard. This mixed intent increases cognitive load.

Correctness:
- Docs show readiness limitations, while UI copy can look more confident than the underlying capability truth.
- Accessibility tests report heading hierarchy issues on Insights.

Consistency:
- Capability and boundary visibility should use the same components as Operations and route lifecycle surfaces.

Recommendations:
- Split "Business insights" from "Deployment diagnostics".
- Keep optional analytics behind capability-aware cards.
- Fix heading hierarchy and make empty/error states consistent.

### 3.11 Alerts

Purpose: triage, group, acknowledge, resolve, and understand operational alerts.

Completeness:
- Alerts page supports grouping, filters, list view, severity/status, AI grouped view, acknowledge, resolve, and auto-resolve affordances.
- It lacks consistent reason capture, confirmation, undo, bulk operation review, SLA/ownership assignment, and audit-link closure.

Simplicity:
- "AI Grouped" is too visible as a mode. Users need grouped root causes and prioritized next actions, not AI branding.

Correctness:
- Acknowledge/Resolve buttons mutate directly without a consistent confirmation/reason model.
- Auto-resolve should have confidence thresholds, governance triggers, and rollback/override.

Consistency:
- Alert actions do not match Trust Center's guarded action pattern.
- Severity/status filters use raw selects that need labels and consistent filter components.

Recommendations:
- Default to "Grouped by probable root cause" with confidence and affected assets.
- Require reason or suggested reason for resolve.
- Add undo where safe, audit trail link, assignment, escalation, and related events/logs.

### 3.12 Operations Console

Purpose: give operators a reliable view of system health, alerts, capabilities, and diagnostics.

Completeness:
- Operations Console uses `RBACGuard requiredRole="ADMIN"` and shows system health, capability registry, alert summary, and actions.
- It lacks real health endpoint integration in some areas.

Simplicity:
- The surface should be a concise operational command center, but it mixes route capability status, alert counts, health placeholders, and broad diagnostics.

Correctness:
- Code comments identify some health data as placeholder.
- It can show alert counts as zero when `alertSummary` is missing.
- It uses current client time as a last-check timestamp, which can imply a successful backend check.

Consistency:
- Operational status differs from Insights, Events, and Trust Center.

Recommendations:
- Never show "0" for unavailable counts. Use "Unavailable", "Unknown", or "Last successful sync".
- Add real health checks, last successful fetch, degraded reasons, retry state, and incident links.
- Use one status grammar across Operations, Insights, Alerts, Events, and Trust.

### 3.13 Events

Purpose: inspect event streams, live mode, event details, timeline/list views, and operational traceability.

Completeness:
- Event Explorer supports list/timeline, live mode, filters, detail drawer, stats, and refetch.
- It needs stronger connection state, backfill, dropped-event, retry, and live-mode failure visibility.

Simplicity:
- Events should be entered contextually from Alerts, Workflows, Collections, and Operations rather than requiring users to know it as a separate top-level concept.

Correctness:
- Live mode must explicitly distinguish connected, reconnecting, stale, paused, and unavailable states.

Consistency:
- Timeline and detail patterns should align with workflow execution timelines and audit logs.

Recommendations:
- Make event exploration a shared timeline component reused in workflow, alert, data asset, and operations detail pages.
- Add saved filters and deep links from related surfaces.

### 3.14 Memory, Entities, and Context

Purpose: expose advanced internal data/agent/context stores and related exploration tools.

Completeness:
- Memory has list, type filters, search, agent filter, delete, and consolidation data.
- Entity Browser has namespaces, lists, detail, delete, bulk delete, suggestions, and audit logging.
- Context Explorer has collection context and lineage.

Simplicity:
- These are advanced operator/developer concepts and should not be prominent for normal users.
- Entity, Context, and Data Explorer overlap conceptually.

Correctness:
- Entity delete and bulk delete are sensitive operations. They need the same confirmation, impact preview, reason, and audit closure pattern used in Trust Center.
- AI suggestions in Entity Browser must be capability-gated and explainable.

Consistency:
- Data asset detail, Entity detail, Context detail, and Memory detail are separate patterns today.

Recommendations:
- Merge normal asset exploration into Data. Keep Entities/Memory/Context as advanced diagnostics or contextual tabs.
- Standardize destructive operations through a shared guarded-action component.

### 3.15 Data Fabric

Purpose: show storage-tier topology, tier metrics, placement, and migration controls.

Completeness:
- Data Fabric page fetches metrics and exposes tier migration.
- Unsupported-surface boundary says metrics preview only/no live metrics route, while page copy and comments claim live topology and production consumer behavior.

Simplicity:
- A preview topology plus manual migration controls is confusing. Users cannot tell what is safe and real.

Correctness:
- Manual tier migration lacks dry-run, impact analysis, approval, rollback, and audit closure.
- "No tier data available" can hide unavailable metrics.

Consistency:
- Fabric migration is less guarded than Trust purge despite similar operational risk.

Recommendations:
- Treat Fabric as preview until real metrics and migration workflows exist.
- Require placement recommendation, impact preview, dry-run, approval, progress, rollback, and audit trail for migration.

### 3.16 Agents and Plugins

Purpose: manage plugins, agent capabilities, installed state, and integration boundaries.

Completeness:
- Plugins page fetches installed plugins, supports enable/disable mutations, tabs for installed/catalog/delivery, filters, empty/error states, and detail pages.
- Catalog and delivery are boundary-limited by runtime messages.
- Agent Plugin Manager fetches agent registry and exposes retry/error states.

Simplicity:
- Plugins, Agents, Catalog, Delivery, and Integration boundaries are advanced admin/developer concepts. They should be hidden unless needed.

Correctness:
- Enable/disable should include dependency, capability, permission, restart/reload, and rollback implications.
- Plugin detail includes code-like snippets and operational material that may be useful for developers but too much for normal admins.

Consistency:
- Plugin lifecycle states should use the same status model as workflows and operations.

Recommendations:
- Make Plugins an admin-only capability manager.
- Add impact previews for enable/disable, dependency checks, audit events, and post-action health checks.
- Keep catalog/delivery clearly disabled until supported.

### 3.17 Settings

Purpose: manage profile, preferences, notifications, API keys, and admin-like personal settings.

Completeness:
- Settings renders profile, preference, notification, and API-key sections.
- `unsupportedSurfaceRegistry.ts` explicitly says these endpoints are not available and no save/generate/revoke action should be shown until API exists.

Simplicity:
- Settings should be a trustworthy low-complexity area. Current boundary plus fake controls creates uncertainty.

Correctness:
- The page contradicts its own boundary: it shows writable fields, toggles, Save Profile, notification toggles, and local fake API key creation/revocation.
- It generates pseudo-secrets locally in UI state.

Consistency:
- Settings violates the boundary pattern used elsewhere by rendering operational-looking controls after declaring them unavailable.

Recommendations:
- Remove fake saves/toggles/API key actions.
- Replace with read-only account/deployment details and "Unavailable in this deployment" panels.
- Only show API key generation when backed by real API, audit trail, one-time secret display, scoping, expiration, revocation, and confirmation.

### 3.18 Loading, Empty, Error, Success, and Async States

Purpose: communicate state truthfully and support recovery.

Completeness:
- Some pages have good error states and retry buttons, especially Plugins and Events.
- Several pages have indefinite generic loading, fallback zeros, placeholder comments, or boundary panels mixed with live controls.

Simplicity:
- Users should never need to infer whether a blank, zero, spinner, or preview means "empty", "loading", "failed", "unavailable", or "not configured".

Correctness:
- MSW failure showed a real route can hang on `Loading...` with no recovery.
- Operations can show zero alerts when summary data is unavailable.

Consistency:
- Loading/error state components exist but are not uniformly adopted.

Recommendations:
- Standardize query state handling: loading, empty, no permission, unavailable capability, degraded, stale, error, retrying, success, partial success.
- Require every route to render a bounded error/retry state when dependencies fail.

### 3.19 Accessibility and Responsive Behavior

Purpose: ensure keyboard, screen reader, focus, contrast, semantics, and responsive usability.

Completeness:
- Accessibility tests exist, which is a strength.
- Current failures include page crashes and heading hierarchy issues.

Simplicity:
- Responsive simplification should reduce surfaces on small screens, not merely compress the desktop dashboard.

Correctness:
- Nested `<main>` landmarks appear because `DefaultLayout.tsx` renders `<main>` and pages such as `DataExplorer.tsx` also render `<main>`.
- Create Collection accessibility tests crash due undefined sensitivity badge state.
- Several selects and icon-only buttons need explicit accessible names.

Consistency:
- Focus, modal, filter, table, card, and button semantics vary by page.

Recommendations:
- Fix test failures before expanding surface area.
- Add route-level a11y acceptance tests for nav, onboarding, data, workflows, query, trust, alerts, settings.
- Define a responsive shell pattern: mobile top bar, collapsible sections, single-column task queue, sticky primary action, no persistent side context by default.

### 3.20 Frontend Architecture and UX-Affecting Implementation

Purpose: assess implementation choices that directly affect UX quality.

Completeness:
- There are useful shared primitives: route registry, unsupported boundaries, error states, query client, validation services, auth/session helpers, trust components.
- They are not consistently used as product-level enforcement mechanisms.

Simplicity:
- The codebase has multiple local implementations of similar ideas, increasing UX drift.

Correctness:
- API clients expose parameters that are ignored.
- Cache invalidation is narrow and may not invalidate parent list queries after detail mutations.
- MSW is enabled in development but worker file is missing/mis-served.

Consistency:
- Design tokens and shared components exist, but pages still use raw controls and one-off patterns.

Recommendations:
- Treat UX state as architecture: canonical route metadata, capability metadata, action metadata, query-state wrappers, and guarded-action components should be shared and enforced.

## 4. Complete End-to-End Flow Review

| Flow | User Goal | Current Steps | Friction / Failure Points | Completeness Gaps | Correctness / Consistency Concerns | AI/Automation Opportunity | Ideal Future-State Journey |
|---|---|---|---|---|---|---|---|
| First-run setup | Get Data Cloud ready and understand next action | Wizard -> connection -> tenant -> collection -> AI assist -> home | Responsive modal collapse, local-only completion, unsupported AI/settings references | No real setup verification or backend closure | Copy promises unsupported settings/capabilities | Detect deployment capabilities, prefill tenant, skip unavailable steps | User sees a 3-step readiness checklist with verified state and one next action. |
| Explore data asset | Find and inspect a collection | Data -> search/filter -> select collection -> switch view tabs | Indefinite loading if backend/MSW unavailable; direct detail weak | Missing not found/error/forbidden/stale states | Filters/sort ignored by API | Rank likely collections, summarize schema/quality/lineage | User searches once, opens durable asset detail with tabs and truthful state. |
| Create collection | Register a new collection with governance metadata | Data -> create -> form -> submit | Manual schema entry, validation crash risk | Governance fields not persisted | Form implies trust metadata saved when it is not | Infer schema and sensitivity from sample/source | User connects or pastes source; system infers schema/trust; user confirms; persisted detail shows results. |
| Inspect quality/lineage | Understand data health and dependencies | Data detail -> quality/lineage tabs | Quality placeholder; lineage availability unclear | Missing async job status and detailed quality view | Placeholder points to service endpoint instead of UI closure | Explain anomalies, prioritize quality issues | User sees quality status, lineage graph, stale indicators, and remediation actions. |
| Author query | Ask data question and run SQL | Query -> write SQL or ask question -> run | AI assist mode drift, static trust badges | Missing query-derived policy visibility | Heuristics imply real governance | Generate SQL, validate policy, explain results | User asks a question; system proposes SQL, shows trust/risk, runs, explains, saves. |
| Save/reuse query | Keep useful query for later | Query -> saved query UI | Mock saved queries exist | Durable saved-query contract unclear | Local/mock content may look real | Suggest names/tags/schedules | User saves to real workspace with owner, scope, history, and sharing controls. |
| Create/run workflow | Build and execute pipeline | Pipelines -> create/builder/editor -> run | Multiple modes; inert run/pause/menu actions | Missing execution closure and logs path | Actions look operational but are not wired | Convert goal to workflow, validate, suggest fixes | User describes outcome, reviews generated steps, validates, runs, monitors timeline. |
| Monitor workflow failure | Understand and recover failed pipeline | Workflows -> modal/logs maybe | Logs action inert; only summary visible | Missing retry, root cause, related alerts/events | Failure state lacks structured recovery | Summarize root cause and next action | User opens failure, sees timeline, failed step, suggested retry/fix, and audit trail. |
| Alert triage | Group, acknowledge, resolve incidents | Alerts -> AI grouped/list -> acknowledge/resolve | No consistent reason, undo, assignment, escalation | Missing audit closure and review workflow | Auto-resolve/risky actions not sufficiently governed | Root-cause grouping, dedupe, recommended owner | User sees grouped incidents, confirms suggested action, records reason, tracks closure. |
| Trust operation | Classify, redact, purge, audit | Trust -> quick action -> modal -> execute | Dense page but guarded purge flow is strong | Access review limited | Compliance status derivation needs care | Recommend policy gaps and safe actions | User reviews evidence, runs dry-run, approves with token, gets result and audit link. |
| Fabric migration | Move collection/storage tier | Fabric -> enter collection -> target tier -> migrate | Preview/live contradiction; no impact preview | Missing dry-run, approval, rollback, audit | Risky operation less guarded than purge | Recommend placement and estimate impact | User sees recommendation, impact, dry-run, approval, progress, rollback. |
| Admin settings | Manage account/preferences/API keys | Settings -> tabs -> save/toggle/generate | Fake local actions contradict boundary | Missing real persistence and key lifecycle | Severe trust issue | N/A until backend exists | User sees read-only unavailable state or real secure key lifecycle. |
| Plugin management | Enable/disable integrations | Plugins -> installed -> enable/disable | Missing impact/dependency preview | Missing rollback and post-action health check | Lifecycle states differ from operations | Recommend plugins based on missing capability | Admin reviews impact, enables, sees health validation and audit event. |
| Operator diagnostics | Understand deployment health | Operations/Insights/Events | Mixed diagnostics and business insights | Missing reliable health and background jobs | Zeros/client time can mislead | Summarize degraded services and root cause | Operator sees compact status, degraded reasons, linked incidents, and retries. |

## 5. Comprehensive Findings Catalog

| ID | Title | Severity | Category / Dimension | Affected Surface | Evidence | Why It Matters / Impact | Likely Root Cause | Recommended Fix / Expected Benefit | Related |
|---|---|---:|---|---|---|---|---|---|---|
| DC-UX-001 | Development/mock runtime can leave core routes indefinitely loading | Critical | Correctness, completeness | App runtime, Data Explorer | Live browser: MSW worker failed due `mockServiceWorker.js` MIME `text/html`; `/data` stayed on `Loading...`; no worker file found under UI root. | Users and developers cannot distinguish backend unavailable from loading. Blocks all route validation and creates false perceived instability. | MSW worker asset missing/mis-served and route query states lack bounded fallback. | Add worker asset or disable MSW by default when unavailable; add route-level timeout/error/retry/degraded state. Benefit: truthful local/dev and demo behavior. | DC-UX-038 |
| DC-UX-002 | Route truth is split across multiple conflicting sources | Critical | Correctness, consistency | Routes, nav, search, docs, tests | `routes.tsx`, `DefaultLayout.tsx`, `RouteCapabilityRegistry.ts`, `GlobalSearch.tsx`, route truth matrix, unsupported registry, tests disagree. | Users see stale routes, unsupported routes, or missing routes. Teams cannot reason about product availability. | No single canonical route/capability source enforced at build/test time. | Make registry canonical and generate nav/search/breadcrumbs/docs/tests from it. | DC-UX-003, DC-UX-004 |
| DC-UX-003 | Global search exposes stale or inconsistent navigation shortcuts | High | Correctness, consistency | Global Search | `GlobalSearch.tsx` has static `quickNavItems`; route truth test fails on `nav-alerts`. | Search can send users to unsupported or unexpected surfaces. | Static search list not derived from capability registry. | Generate search items from route registry and lifecycle metadata. | DC-UX-002 |
| DC-UX-004 | Shell navigation comments claim registry-backed nav, but static nav is used | High | Correctness, consistency | DefaultLayout | `buildNavFromRegistry` exists, but role nav uses static `navSections`. | Future maintainers and users inherit drift between product truth and shell. | Partial migration to registry model. | Remove static nav or make it generated. Add regression tests. | DC-UX-002 |
| DC-UX-005 | Nested main landmarks create accessibility and semantic drift | Medium | Accessibility, consistency | Layout and pages | `DefaultLayout.tsx` renders `<main>` while pages such as `DataExplorer.tsx` also render `<main>`. | Screen reader landmark navigation becomes confusing. | Pages own full document semantics despite shell wrapper. | Shell owns `<main>`; pages render sections/articles. | DC-UX-043 |
| DC-UX-006 | Onboarding modal fails responsive polish | High | Simplicity, accessibility | Onboarding | Live narrow viewport showed `Skip SetupNext` collapsed and step list cramped. | First impression is fragile; users may abandon setup. | Modal/stepper responsive constraints insufficient. | Rework wizard layout with mobile-first stepper, stable footer, and single primary action. | DC-UX-043 |
| DC-UX-007 | Onboarding promises unsupported or mismatched setup paths | High | Correctness, completeness | Onboarding, Settings | Wizard says configure AI later in `Settings -> AI`; Settings has no AI section. Copy also points collection management to Entity Browser rather than Data. | Creates false expectations and makes the product feel unreliable. | Onboarding copy not capability-aware. | Derive onboarding steps/copy from capability registry and real settings sections. | DC-UX-025 |
| DC-UX-008 | Home screen has high cognitive load and poor narrow-layout focus | High | Simplicity | Intelligent Hub | Live narrow view showed context sidebar, large title, panels, and assistant competing in constrained space. | Users do not get one obvious next action. | Dashboard-first composition instead of task-first home. | Convert Home to prioritized work queue with progressive diagnostics. | DC-UX-040 |
| DC-UX-009 | Data Explorer lacks bounded error and recovery states | Critical | Completeness, correctness | Data Explorer | `DataExplorer.tsx` uses loading state but no main error path; live `/data` stayed on spinner. | Users cannot recover or understand dependency failure. | Query state not standardized. | Add error, retry, unavailable capability, stale, and partial data states. | DC-UX-001 |
| DC-UX-010 | Collection detail deep links are not durable enough | High | Completeness, correctness | Data Explorer, routes | Selecting collection sets local state and navigates `/data/:id`; direct route does not clearly fetch/select by ID. | Shared links and refreshes fail to show intended asset detail. | Detail route implemented as selected state instead of resource route. | Implement true collection detail loader/query by ID with not-found/forbidden states. | DC-UX-011 |
| DC-UX-011 | Quality view is visibly present but operationally incomplete | High | Completeness | Data Explorer | Quality tab says metrics computed async and sends users to service endpoint. | Users cannot complete quality assessment in UI. | UI tab created before product workflow. | Add quality job state, results, history, issue list, and remediation actions or hide tab by capability. | DC-UX-010 |
| DC-UX-012 | Collection filters and sorting are accepted by client API but ignored | High | Correctness | Collections API, Data Explorer | `collectionsApi.list` accepts status/schema/sort but only sends limit/offset/search. | UI can show controls that do not affect results correctly. | API adapter contract drift. | Wire params to backend or remove controls. Add adapter contract tests. | DC-UX-018 |
| DC-UX-013 | Collection governance fields are collected but not persisted | Critical | Correctness, trust | Collection form/create | Form has sensitivity, retention, active; create sends only name/description/schema type/schema. | False compliance: user thinks trust metadata is saved. | Backend DTO and UI form out of sync. | Persist fields or mark them unavailable/read-only. | DC-UX-045 |
| DC-UX-014 | Sensitivity badge can crash when value is undefined/invalid | Critical | Accessibility, correctness | Create Collection, TrustSignal | Accessibility test crashed on `SensitivityBadge` undefined `badgeClass`. | Form becomes unusable for assistive testing and possibly users. | Component assumes valid enum value. | Add defensive fallback and form default normalization. | DC-UX-039 |
| DC-UX-015 | Workflow action menu contains inert primary actions | Critical | Correctness, completeness | Workflows | Run Now, Edit, View Logs, Delete buttons render without operational handlers. | Users cannot run, edit, inspect logs, or delete despite controls saying they can. | UI scaffold outran backend/action wiring. | Wire actions or disable with boundary copy. | DC-UX-016 |
| DC-UX-016 | Workflow detail modal Run/Pause controls are inert | Critical | Correctness | Workflows | Modal buttons `Run Now` and `Pause` have no operational effect. | Severe false affordance in primary workflow. | Missing mutation integration. | Connect to workflow execution/deactivate APIs with feedback and audit. | DC-UX-015 |
| DC-UX-017 | Workflows page silently truncates list to 12 items | High | Completeness | Workflows | Renders `workflows.slice(0,12)` without pagination. | Users may miss workflows and assume list is complete. | Card grid performance shortcut. | Add pagination, virtualized list, or clear "showing first 12" with load more. | DC-UX-018 |
| DC-UX-018 | Workflow list search/sort/page parameters are ignored | High | Correctness | Workflow API, Workflows | `workflowsApi.list` exposes search/sort/page but sends only limit/status. | Filtering and paging semantics are unreliable. | API adapter drift. | Wire params, remove controls, or use client-only controls with explicit scope. | DC-UX-012 |
| DC-UX-019 | Workflow execution visibility is insufficient | High | Completeness, observability | Workflows, execution monitor | Workflow cards/modal lack complete run timeline, inputs, outputs, retries, log path, and recovery closure. | Operators cannot troubleshoot or trust automation. | Execution data model not surfaced end to end. | Add execution detail route/timeline and related events/alerts. | DC-UX-020 |
| DC-UX-020 | Workflow creation modes are fragmented | Medium | Simplicity, consistency | Pipelines, Smart Builder, Designer | Multiple creation/edit entry points exist with different mental models. | Users must choose a mode before knowing what they need. | Feature accretion. | One "Create workflow" flow with progressive visual/manual/advanced modes. | DC-UX-019 |
| DC-UX-021 | Query AI assist state and test contract have drifted | High | Consistency, correctness | SQL Workspace | Tests expect "AI Assist"; UI exposes "Ask a Question"/"Hide Question"; clarification test fails. | Users and tests see inconsistent assistance behavior. | AI assist pattern changed locally without shared standard. | Standardize assistant trigger, open/closed state, and clarification behavior. | DC-UX-040 |
| DC-UX-022 | Query trust badges are static, not query-derived | Critical | Correctness, trust | SQL Workspace | Query page renders fixed tenant/internal trust indicators. | Can falsely reassure users about access/sensitivity. | Trust UI not connected to query analysis/policy backend. | Derive sensitivity/access from parsed query targets and backend policy evaluation. | DC-UX-045 |
| DC-UX-023 | Query guardrails are local heuristics but appear authoritative | High | Correctness | SQL Workspace | Rewrite/guardrail suggestions are heuristic/local code. | Users may trust incomplete safety guidance. | Prototype logic presented in operational surface. | Label as advisory or connect to policy/optimizer service. | DC-UX-021 |
| DC-UX-024 | Saved query experience contains mock data | Medium | Correctness, completeness | Query saved queries | `components/sql/SavedQueries.tsx` defines `mockQueries`. | Users may mistake examples for durable workspace content. | Mock component remains in product path. | Replace with durable API or clear sample/local state boundary. | DC-UX-025 |
| DC-UX-025 | Settings contradicts its own unsupported boundary | Critical | Correctness, trust | Settings | Boundary says no save/preference/API-key actions; page renders writable controls and save/toggle actions. | Users think security/profile changes took effect when they did not. | Boundary message and UI controls maintained separately. | Remove fake actions; show read-only unavailable states until backend exists. | DC-UX-026 |
| DC-UX-026 | API key management is fake/local but looks security-critical | Critical | Security UX, correctness | Settings API Keys | UI creates pseudo-secret locally and supports local revoke. | Severe trust and security risk; users may rely on non-real credentials. | Demo behavior left in admin surface. | Hide until real API exists with one-time reveal, scopes, expiration, audit, revoke. | DC-UX-025 |
| DC-UX-027 | Operations can show zero counts when data is unavailable | Critical | Correctness, observability | Operations Console | Alert summary defaults to zeros when missing. | Operators can miss incidents or believe system is healthy. | Null unavailable state collapsed into numeric zero. | Use Unknown/Unavailable/Stale with last successful fetch. | DC-UX-028 |
| DC-UX-028 | Operations "last check" can be client time, not backend health time | High | Correctness | Operations Console | Health placeholder maps last check to current client time. | Misrepresents monitoring truth. | Placeholder health model. | Use real health endpoint timestamps or label local render time separately. | DC-UX-027 |
| DC-UX-029 | Data Fabric claims live topology while boundary says preview | High | Correctness, trust | Data Fabric | Page comments/copy claim live metrics; boundary says preview/no live metrics route. | Users cannot know whether migration and metrics are trustworthy. | Documentation and surface maturity drift. | Align copy and lifecycle; hide live claims until backend route is available. | DC-UX-030 |
| DC-UX-030 | Data Fabric migration lacks safeguards | Critical | Trust, correctness | Data Fabric | Manual migration input/select/start flow lacks dry-run, impact, approval, rollback. | Risky operational action can be launched without enough context. | Sensitive action pattern not reused. | Reuse Trust Center guarded-action pattern. | DC-UX-029 |
| DC-UX-031 | Alerts overexpose AI as a mode instead of embedded triage | Medium | Simplicity, AI/ML | Alerts | View mode labeled "AI Grouped". | AI branding adds cognitive load and weakens trust if confidence is unclear. | AI presented as feature surface. | Rename to "Grouped by root cause"; expose confidence/evidence only when needed. | DC-UX-040 |
| DC-UX-032 | Alert filters lack consistent accessible labeling | Medium | Accessibility, consistency | Alerts | Severity/status raw selects appear without robust label pattern. | Keyboard/screen reader users lose context. | One-off filter controls. | Use shared labeled filter components. | DC-UX-039 |
| DC-UX-033 | Alert acknowledge/resolve lacks reason, undo, and audit closure | High | Completeness, trust | Alerts | Direct Acknowledge/Resolve mutation buttons. | Incident handling lacks accountability and recovery. | Lightweight actions not upgraded for ops. | Add reason capture, suggested reason, undo where safe, audit link, assignment. | DC-UX-031 |
| DC-UX-034 | Route truth matrix is stale relative to implementation | High | Consistency | Docs/routes | `ROUTE_TRUTH_MATRIX_2026-04-17.md` conflicts with current route config. | Documentation cannot be trusted for product planning. | Manual route documentation. | Generate route truth report from canonical registry in CI. | DC-UX-002 |
| DC-UX-035 | Lifecycle metadata marks some partial surfaces as active | High | Correctness, completeness | RouteCapabilityRegistry | `/events`, `/alerts`, `/fabric`, `/agents` states conflict with boundaries/docs. | Users see partial features as production-ready. | Lifecycle values not tied to runtime capability. | Add lifecycle states: active, preview, boundary, unavailable, legacy; enforce in UI. | DC-UX-029 |
| DC-UX-036 | Role disclosure can be confused with authorization | High | Trust/security UX | Shell, routes | Session code says shell role is UI disclosure only; pages also use role guards. | Users/admins can misunderstand security guarantees. | Mixed disclosure and permission concepts. | Rename to "View mode" or "Surface visibility"; show backend permission separately. | DC-UX-002 |
| DC-UX-037 | Browser title still says "CES UI" | Low | Consistency, polish | App shell | Live browser title: "CES UI". | Reduces product coherence and trust. | Legacy naming not updated. | Rename document title and metadata to Data Cloud. | DC-UX-042 |
| DC-UX-038 | Generic indefinite loading is overused | High | Completeness, perceived performance | Routes/pages | Page loader says generic `Loading...`; `/data` showed indefinite load. | Users cannot tell if product is slow, failed, or unavailable. | Missing query-state standard. | Use bounded loaders with skeleton, capability dependency, retry, and timeout. | DC-UX-001 |
| DC-UX-039 | Empty/error/success states are inconsistent across pages | High | Consistency, completeness | All pages | Plugins has retry/empty, Data lacks main error, Operations defaults zeros, Settings fake success. | Product feels patched together and state truth varies. | No shared state contract. | Create route/query state components and lint/test adoption. | DC-UX-014 |
| DC-UX-040 | AI is too often user-facing branding rather than embedded help | High | Simplicity, AI/ML | Home, Query, Alerts, Workflows, Onboarding | "AI Assist", "AI Grouped", AI setup, assistant panels, heuristic suggestions. | Adds complexity and trust burden. | AI surfaced as feature instead of product behavior. | Embed AI into defaults, ranking, summaries, suggestions; disclose confidence contextually. | DC-UX-021 |
| DC-UX-041 | Test suite drift reveals UX contract instability | High | Correctness, consistency | Tests | Route truth, shell routing, settings, query, and a11y tests fail. | Automated assurance no longer protects user experience. | Implementation changed without updating product contracts. | Rebaseline tests against canonical truth and add critical journey tests. | DC-UX-002 |
| DC-UX-042 | Design system reuse is incomplete | Medium | Consistency | Components/pages | Raw buttons/selects, one-off cards, emoji-like settings icons, repeated state patterns. | Visual and interaction drift accumulates. | Shared components not mandatory. | Centralize buttons, filters, cards, tabs, modals, states, badges, guarded actions. | DC-UX-039 |
| DC-UX-043 | Responsive shell compresses complexity instead of simplifying | High | Simplicity, accessibility | Home, onboarding, tables/grids | Live narrow views showed cramped layout and competing panels. | Mobile/tablet users face unusable density. | Desktop-first layouts. | Define responsive information hierarchy and mobile-specific workflows. | DC-UX-006 |
| DC-UX-044 | Audit/history visibility is incomplete for sensitive operations | High | Trust, observability | Alerts, Entities, Fabric, Settings, Plugins | Some mutations lack post-action audit links, reason capture, impact previews. | Reduces accountability and compliance confidence. | Sensitive action pattern not centralized. | Shared guarded-action framework with reason, preview, confirmation, audit result. | DC-UX-030 |
| DC-UX-045 | Trust/privacy/security signals are not consistently data-derived | Critical | Correctness, trust | Query, Data, Settings, Trust | Static trust badges, unpersisted governance fields, fake API keys. | False trust is worse than missing trust. | Trust UI decoupled from backend policy/data. | Bind trust signals to source data, policy evaluation, and audited operations. | DC-UX-013 |
| DC-UX-046 | Background jobs and async operations lack unified visibility | High | Completeness, observability | Workflows, Data quality, Fabric, Alerts | Async operations do not consistently show queued/running/retry/partial/success/failure. | Users cannot know what happened or what happens next. | No shared operation model. | Add operation timeline/state component reused across product. | DC-UX-019 |
| DC-UX-047 | Unsupported surfaces remain discoverable/actionable | High | Correctness, completeness | Settings, Fabric, Plugins catalog, access review | Boundary text appears alongside normal controls or nav entries. | Users waste time and misinterpret maturity. | Boundary registry not enforced as behavior. | Boundary state should disable/hide actions and route discoverability. | DC-UX-025 |
| DC-UX-048 | Static command/search undermines canonical IA | Medium | Consistency, simplicity | Global Search | Quick nav list manually maintained. | Search becomes another route truth source. | Search not generated from registry. | Use registry plus aliases and capability state. | DC-UX-003 |
| DC-UX-049 | Readiness docs conflict with UI maturity claims | High | Correctness, trust | Docs and product copy | Readiness scorecard marks security/isolation and performance validation red while UI surfaces imply mature operations. | Stakeholders may overestimate readiness. | Marketing/product copy not tied to readiness status. | Add readiness-aware copy and release gating. | DC-UX-034 |
| DC-UX-050 | Dev console dependency warning indicates asset/integration drift | Low | Perceived performance, implementation | Dev server | Vite warned failed dependency resolve for `@ghatana/canvas`. | Can signal broken optional visualization path and slow debugging. | Stale optimizeDeps/include or missing package. | Remove stale dependency or install/wire optional package. | DC-UX-001 |

## 6. Completeness Gap Inventory

Missing screens or route closures:
- True collection detail route with by-ID fetch, not found, forbidden, stale, and degraded states.
- Durable saved-query management.
- Workflow execution detail page with timeline, logs, retries, inputs, outputs, related alerts, and audit events.
- Unified operation/job center for background work.
- Real Settings pages for profile, preferences, notifications, API keys, and AI configuration, or clear absence of those settings.
- Fabric migration detail/progress/rollback page.
- Alert assignment/escalation/reason/audit view.
- Plugin enable/disable impact and health-check flow.

Missing steps:
- First-run verification of backend connection, tenant, capabilities, and first successful query.
- Data quality job initiation, progress, results, and remediation.
- Collection governance persistence confirmation.
- Workflow publish validation and run gating.
- Query policy evaluation before execution for sensitive sources.
- Alert resolve reason and post-resolution review.
- Sensitive action dry-run/impact preview outside Trust Center.

Missing transitions:
- Loading -> unavailable -> retry -> recovered.
- Preview -> active route lifecycle.
- Local draft -> persisted object.
- Async job queued -> running -> retrying -> partial failure -> complete.
- Suggested AI action -> user review -> accepted/rejected -> audited outcome.

Missing states:
- Backend unavailable.
- Capability unavailable/degraded.
- Permission denied vs UI role hidden.
- Empty because no data vs empty because filters exclude data.
- Stale data.
- Partial result.
- Retry exhausted.
- Conflict/race update.
- In-flight mutation with cancel/undo where safe.

Missing validations:
- Collection governance fields against backend DTO.
- Schema import/field validation and duplicate field handling.
- Workflow execution prerequisites and credentials.
- Query target sensitivity/access policy.
- Plugin dependency/compatibility validation.
- Fabric migration impact and eligibility.
- Alert resolve reason/ownership requirements.

Missing guidance:
- What is canonical vs preview.
- What actions are local-only.
- What data is stale/unavailable.
- Why AI suggested something.
- Which role or permission is required.
- What happens after a long-running operation starts.

Missing error/recovery handling:
- Data Explorer backend/mock failure.
- Quality/lineage unavailable.
- Workflow action failure.
- Alert mutation failure with rollback.
- Plugin enable/disable failure and health-check failure.
- Fabric migration failure/rollback.
- Settings persistence unavailable.

Missing supporting workflows:
- Audit trail drilldown from actions.
- Notification center for jobs/incidents.
- Approval/review queue for risky automation.
- Help/contextual docs tied to capability boundaries.
- Admin capability management.
- Role-specific onboarding and home.

Missing operational visibility:
- Background job list.
- Last successful sync/check timestamps.
- Retry counts and next retry.
- Partial failure details.
- Source of displayed metrics.
- Confidence/evidence for AI-assisted grouping or recommendations.

Missing role-specific handling:
- Clear distinction between disclosure mode and backend authorization.
- Data steward vs operator vs admin vs auditor views.
- Permission-denied copy with request-access path.

Missing accessibility behavior:
- No nested main landmarks.
- Stable heading hierarchy.
- Labeled selects and icon controls.
- Modal focus trapping/focus restore verification.
- Responsive first-run/onboarding layout.
- Screen-reader status updates for async actions.

Missing trust/privacy/security surfaces:
- Secure API key lifecycle.
- Sensitive action confirmation standards.
- Policy-derived trust indicators.
- Audit links after mutations.
- Data handling/privacy explanations where needed.
- Consent/disclosure only where actually relevant.

Missing automation:
- Schema inference.
- Sensitivity classification.
- Query policy evaluation.
- Workflow draft and validation suggestions.
- Alert deduplication/root-cause grouping.
- Incident owner recommendation.
- Fabric placement recommendation with impact.
- Settings/configuration capability detection.

## 7. Comprehensive Simplification Plan

Remove:
- Fake Settings save/toggle/API-key controls until real APIs exist.
- Inert workflow menu/modal actions until wired.
- Static search shortcuts that do not come from canonical route metadata.
- Top-level exposure for direct-only advanced surfaces where not needed.
- AI branding where the user value is grouping, summarization, ranking, or recommendation.
- Placeholder quality/fabric/live metrics claims unless backed by UI closure.

Merge:
- Data, Entity, Context, Schema, Lineage, Quality, and Activity into one asset-detail model.
- Workflow builder, designer, smart builder, and advanced editor into one progressive workflow creation journey.
- Events, audit logs, workflow execution logs, and alert timeline into a shared timeline pattern.
- Operations and deployment diagnostics into a concise operator command center, with Insights focused on business/product analytics.

Hide by default:
- Memory, Context, Entities, Fabric, Agents, plugin delivery, and raw diagnostics for standard users.
- AI confidence and detailed explanations until a recommendation is inspected or risk is high.
- Advanced query/workflow controls until the simple path is exhausted.

Automate:
- Tenant/capability detection during onboarding.
- Schema inference from source/sample.
- Sensitivity and retention suggestions.
- Query generation and validation.
- Workflow draft and missing credential detection.
- Alert grouping, deduplication, and suggested owner/reason.
- Fabric placement recommendations.

Prefill:
- Tenant ID from session/deployment.
- Collection schema from source metadata or pasted sample.
- Workflow names/descriptions from intent.
- Alert resolution reason from root-cause analysis.
- Plugin configuration defaults from environment.

Make contextual:
- Trust/privacy/security explanations.
- AI reasoning.
- Capability boundaries.
- Logs/events/audit links.
- Advanced settings.

Move to advanced/admin mode:
- Memory store inspection.
- Raw entity namespace browser.
- Fabric migration.
- Agent registry.
- Plugin catalog/delivery.
- Route/capability diagnostics.

Make reusable shared patterns:
- Guarded action.
- Query state.
- Capability boundary.
- Filter bar.
- Resource detail layout.
- Operation timeline.
- Empty/error/success state.
- AI recommendation card.
- Trust signal.
- Role/permission message.

## 8. Correctness Review Register

Misleading states:
- Loading forever when backend/MSW unavailable.
- Operations zeros when alert summary is unavailable.
- Fabric "live" claims while boundary says preview.
- Settings success/local actions while endpoints are unavailable.
- Static Query trust badges.

Incorrect labels:
- `Settings -> AI` referenced by onboarding but absent.
- AI/grouped labels overstate AI as a user-facing mode.
- Role switch can look like authorization despite being disclosure only.
- Browser title says "CES UI".

Inaccurate summaries/counts/statuses:
- Operations alert counts default to 0.
- Client time used as health last-check surrogate.
- Compliance/policy status may be derived from enabled state rather than measured compliance.

Incorrect workflow logic:
- Workflow Run/Pause/Edit/Logs/Delete controls do not execute.
- Workflows list truncates without pagination.
- Collection detail route does not function as a robust resource route.
- Fabric migration lacks required impact/approval flow.

Incorrect system feedback:
- Settings toasts/local state imply persistence.
- Generic loader hides unavailable dependencies.
- Placeholder quality state sends user to service endpoint instead of UI.

Incorrect validation behavior:
- Sensitivity badge crash on invalid/undefined value.
- Collection governance fields not validated against persisted backend contract.
- Workflow validation not consistently tied to publish/run gating.

Frontend/backend mismatches:
- Collections API accepts filter/sort params but ignores several.
- Workflows API accepts search/sort/page but ignores several.
- Create collection form captures fields not in create DTO.
- Mock saved queries can appear as product data.

Incorrect automation placement:
- Query guardrails are local heuristics but appear authoritative.
- AI grouping and auto-resolve need confidence, fallback, and governance.
- Onboarding AI setup should only show when capability is available.

Incorrect edge/error/retry handling:
- No bounded fallback for MSW/backend failure.
- Alert and workflow mutations need rollback/retry states.
- Plugin enable/disable needs impact and post-action health state.

## 9. Consistency Review Register

Design drift:
- Mixed raw buttons, shared button styles, card styles, icon treatments, and tab patterns.
- Settings uses more casual iconography than operator pages.
- Some pages use dense cards; others use tables; similar data types lack shared layouts.

Component drift:
- Different loading, empty, error, retry, and boundary components by page.
- Different destructive action patterns.
- Different status badges and severity badges.
- Different filter bar structures.

Terminology drift:
- Pipelines vs workflows.
- Data vs entities vs context vs collections.
- AI Assist vs Ask a Question vs AI Grouped vs assistant.
- Operator/admin/disclosure/role/permission.

State handling drift:
- Plugins has retry error state; Data spins.
- Operations turns unavailable into zero; Trust uses richer operation feedback.
- Settings local-only success contradicts unavailable boundary.

Workflow pattern drift:
- Trust purge has dry-run/confirmation; Fabric migration does not.
- Alert resolve has direct action; Trust actions have modals.
- Workflow actions are visible but inert.

Formatting drift:
- Dates, statuses, and counts vary by page.
- Empty state and boundary messages have inconsistent tone.

CTA drift:
- Primary actions appear in cards, menus, modal footers, and headers with inconsistent placement.
- Some primary CTAs are disabled, some inert, some real, and some local-only.

Modal/drawer/form drift:
- Onboarding modal responsive failure.
- Trust quick-action modal is robust.
- Workflow modal contains inert controls.
- Entity detail and event detail use page-specific drawers.

Trust/privacy/security messaging drift:
- Trust Center is relatively explicit.
- Query trust is static.
- Settings is misleading.
- Alerts and Fabric lack equivalent safety language.

Role-based inconsistency:
- Shell role reveals surfaces.
- Operations has `RBACGuard`.
- Settings and Plugins admin-like controls are not consistently guarded by real permission language.

Accessibility inconsistency:
- Heading hierarchy failure in Insights.
- Nested landmarks in pages.
- Raw selects/buttons need shared accessible components.
- Modal behavior varies.

## 10. Comprehensive AI/ML Embedding Plan

| Opportunity | Function | User Value | Mode | Confidence / Fallback | Review / Governance Trigger | Override Model | Visibility Model | Privacy/Security | Priority |
|---|---|---|---|---|---|---|---|---|---|
| Capability-aware onboarding | Detect available services and skip irrelevant steps | Faster setup, fewer false promises | Automation | If detection fails, show manual checklist | Admin setup only | User can manually mark step skipped | Show detected/unavailable status | No secrets exposed | Immediate |
| Schema inference | Infer schema from source/sample | Less manual entry | Assist then confirm | Require user confirmation for low confidence | New collection creation | Edit fields before save | Show inferred fields and source | Avoid storing samples unnecessarily | Short-term |
| Sensitivity classification | Suggest sensitivity/retention | Better trust posture | Assist with review | Low confidence -> require steward review | Sensitive/regulated fields | Override with reason | Show evidence fields | Audit classification changes | Short-term |
| Query generation | NL question to SQL | Faster analysis | Assist | Low confidence -> ask clarifying question | Sensitive datasets | User edits SQL | Show targets and assumptions | Policy evaluate before run | Short-term |
| Query risk evaluation | Detect policy/sensitivity/cost before run | Prevent unsafe queries | Automation with block/review | Block high-risk; warn medium | Cross-tenant/sensitive/large scan | Request exception or adjust query | Show concise reason | Backend policy required | Immediate |
| Workflow draft generation | Convert goal to workflow | Faster pipeline creation | Assist | Low confidence -> manual builder | External calls/destructive steps | Edit/disable steps | Show generated plan/confidence | Do not expose secrets | Medium |
| Workflow validation and repair | Detect missing credentials/cycles/failures | Fewer failed runs | Automation plus assist | Suggest fixes; do not auto-publish risky changes | Publish/run | Accept individual fixes | Inline validation summary | Audit publish | Short-term |
| Alert grouping/deduplication | Root-cause clusters | Less alert noise | Automation | Low confidence -> list view fallback | Auto-resolve only high confidence | Split/merge groups | Show evidence and confidence | Avoid leaking sensitive event data | Short-term |
| Alert owner/reason suggestion | Suggest assignee/resolution | Faster incident closure | Assist | Require confirmation | Resolve/escalate | Edit reason/owner | Suggested fields visible | Audit reason source | Medium |
| Data quality anomaly detection | Identify quality regressions | Better data reliability | Automation | Unknown -> show "insufficient history" | Severe regression | Dismiss/snooze with reason | Trend + explanation | Respect data sensitivity | Medium |
| Fabric placement recommendation | Recommend tier/migration | Cost/perf optimization | Assist with review | No auto-migration except safe policy | Large migration/cost risk | Override target with reason | Impact estimate and confidence | Audit migration | Medium |
| Plugin recommendation | Suggest plugins for missing capability | Easier admin setup | Assist | Show only compatible plugins | Security-sensitive plugin | Dismiss/install | Explain dependency/value | Permission and data access preview | Long-term |
| Support/troubleshooting summary | Summarize failed jobs/events/logs | Faster recovery | Assist | Link to raw evidence | Incident review | User accepts/rejects summary | Summary with citations | Redact secrets | Medium |

Principles:
- Default AI output should be embedded into existing workflows as suggestions, ranking, prefills, warnings, or summaries.
- Avoid top-level AI modes unless the user explicitly asks for assistance.
- Every AI-assisted action needs confidence, evidence, fallback, override, auditability, and privacy guardrails proportional to risk.

## 11. Comprehensive Trust / Transparency / Privacy / Security UX Review

Operational visibility:
- Add a global operation center for jobs, imports, workflow runs, quality scans, fabric migrations, plugin changes, alert actions, and policy operations.
- Every operation should show status, owner, start time, last update, source, retry count, result, and next step.
- Dashboards must distinguish zero, unknown, unavailable, stale, and loading.

Auditability:
- Sensitive actions must return an audit event link.
- Alerts, entity deletes, fabric migrations, plugin enable/disable, API-key changes, collection governance changes, and workflow publish/run actions need audit closure.
- Keep Trust Center purge dry-run/confirmation pattern and reuse it.

Permission clarity:
- Rename shell role if it is not authorization, or pair it with backend permission state.
- Show permission-denied states with why, required permission, and request path.
- Avoid exposing admin actions when permission/capability is not available.

Sensitive action handling:
- Standardize impact preview, reason capture, confirmation, typed/token confirmation for destructive actions, progress, rollback/undo where safe, and audit result.
- Fabric migration, entity delete, bulk delete, alert auto-resolve, plugin disable, and API key revoke should use this pattern.

Privacy controls:
- Only expose privacy controls that exist and persist.
- For AI/ML features, disclose data use at the point of configuration or sensitive action, not as generic clutter.
- Redact secrets in logs, plugin snippets, query output, and troubleshooting summaries.

Status and reasoning visibility:
- Show AI reasoning only when it affects a recommendation, warning, automation, or user decision.
- Use concise "Why this?" details with evidence links.

Safe defaults:
- Default to manual review for destructive, cross-tenant, sensitive, or high-cost operations.
- Default to hiding advanced diagnostics from standard users.
- Default to disabled controls rather than fake local success when backend is unavailable.

Exception handling:
- Provide retry, contact/support path, raw error disclosure for developers/admins, and safe user-facing summary for standard users.
- Preserve failed-operation context so users do not have to repeat inputs.

## 12. Design System / Reuse Review

Inconsistent components:
- Buttons, icon buttons, selects, tab controls, cards, empty states, modals, drawers, status badges, and trust badges vary by page.

Behavior drift:
- Loading/error/retry behavior differs across Data, Plugins, Operations, Settings, and Trust.
- Destructive action treatment differs across Trust, Alerts, Fabric, and Entity Browser.
- AI assistance appears as modal, panel, toggle, grouping mode, and onboarding step.

Naming drift:
- Workflows/Pipelines, Data/Entities/Collections, AI Assist/Ask a Question/AI Grouped, role/disclosure/permission.

Repeated UI patterns:
- Filter bars.
- Resource cards.
- Detail drawers.
- Health/status cards.
- Boundary panels.
- Quick action cards.
- Audit/event timelines.
- Confirmation modals.

Missing shared abstractions:
- `CapabilityBoundary` that hides/disables/gates controls.
- `GuardedAction` for sensitive operations.
- `ResourceDetailShell` for collection/entity/context/plugin/workflow detail.
- `OperationTimeline`.
- `QueryStateBoundary`.
- `FilterBar`.
- `AIAssistSuggestion`.
- `TrustSignalGroup`.
- `RolePermissionNotice`.

Token/style inconsistency:
- Some surfaces use shared `buttonStyles`, some use raw class strings.
- Radius, spacing, card density, and disabled styles vary.
- Operator/admin pages should use restrained, dense, scannable layouts; onboarding/home should simplify aggressively.

Messaging/state handling:
- Boundary language exists but is not structurally enforced.
- Success and error copy should be centrally defined for common operations.

Centralization opportunities:
- Generate route nav/search/docs/tests from registry.
- Use design-system components for all filters, buttons, badges, modals, drawers, and states.
- Add lint or test coverage for forbidden fake actions on unsupported surfaces.

## 13. Prioritized Remediation Roadmap

### Immediate

| Item | Priority | Effort | Impact | Dependencies | Owner Type | Rationale |
|---|---:|---:|---:|---|---|---|
| Make capability/route registry canonical for nav/search/docs/tests | P0 | Medium | Very high | Existing registry | Frontend, product | Stops systemic route truth drift. |
| Remove or disable fake Settings actions/API-key generation | P0 | Low | Very high | Unsupported registry | Frontend, security, product | Prevents false security and trust claims. |
| Wire or disable inert workflow Run/Pause/menu actions | P0 | Medium | Very high | Workflow API | Frontend, backend | Removes severe false affordances. |
| Fix MSW/dev fallback and route-level backend-unavailable states | P0 | Low/Medium | High | Dev server/MSW asset | Frontend/platform | Makes local/demo behavior truthful. |
| Fix Create Collection crash and governance persistence mismatch | P0 | Medium | Very high | Collection DTO decision | Frontend, backend, product | Prevents crash and false governance. |
| Replace Operations zero/client-time placeholders with Unknown/Unavailable | P0 | Low | High | None | Frontend | Avoids misleading operators. |
| Stop static query trust badges | P0 | Medium | Very high | Policy/query target source | Frontend, backend, trust | Prevents false trust signals. |
| Fix onboarding copy and responsive layout | P1 | Medium | High | Capability registry | Design, frontend | First-run credibility. |
| Rebaseline failing route/settings/query/a11y tests | P1 | Medium | High | Canonical truth decisions | Frontend, QA | Restores UX contract protection. |

### Short-Term

| Item | Priority | Effort | Impact | Dependencies | Owner Type | Rationale |
|---|---:|---:|---:|---|---|---|
| Implement true collection detail route | P1 | Medium | High | Collection by-ID API | Frontend, backend | Makes Data usable and shareable. |
| Standardize loading/empty/error/degraded/stale states | P1 | Medium | High | Design system | Design, frontend | Reduces confusion across product. |
| Add workflow execution detail and timeline | P1 | High | Very high | Execution API/logs | Frontend, backend, platform | Completes primary workflow monitoring. |
| Add guarded-action shared component | P1 | Medium | Very high | Audit API ideally | Design, frontend, security | Standardizes destructive/sensitive actions. |
| Simplify Home into prioritized work queue | P1 | Medium | High | Capability/alert data | Product, design, frontend | Reduces cognitive load. |
| Wire collection/workflow filters or remove controls | P1 | Medium | High | API contract | Frontend, backend | Restores filter/search correctness. |
| Split Insights analytics from diagnostics | P2 | Medium | Medium | IA decision | Product, design | Reduces mixed intent. |
| Add alert reason/assignment/audit closure | P1 | Medium | High | Alert API | Frontend, backend, ops | Makes incident UX accountable. |

### Medium-Term

| Item | Priority | Effort | Impact | Dependencies | Owner Type | Rationale |
|---|---:|---:|---:|---|---|---|
| Consolidate Data/Entities/Context into asset detail model | P2 | High | High | IA and API mapping | Product, design, frontend | Simplifies core data exploration. |
| Consolidate workflow builders into one progressive flow | P2 | High | High | Workflow service | Product, design, frontend, ML | Reduces mode choice and creation friction. |
| Add global operation/job center | P2 | High | Very high | Operation event model | Backend, frontend, platform | Provides background-work visibility. |
| Implement query policy/risk evaluation | P1 | High | Very high | Policy backend | Backend, frontend, security | Makes query trust truthful. |
| Add schema/sensitivity inference | P2 | High | High | ML/data profiling | ML, backend, frontend | Reduces manual setup. |
| Add plugin impact/dependency preview | P2 | Medium | Medium | Plugin metadata | Backend, frontend, platform | Safer admin operations. |
| Add mobile/tablet responsive workflow patterns | P2 | Medium | High | Design system | Design, frontend | Prevents cramped dashboard compression. |

### Long-Term

| Item | Priority | Effort | Impact | Dependencies | Owner Type | Rationale |
|---|---:|---:|---:|---|---|---|
| AI-assisted operations across alerts/workflows/quality/fabric | P3 | High | High | Event/telemetry/ML platform | ML, platform, product | Enables quiet automation with governance. |
| Full secure settings and API-key lifecycle | P2 | High | High | Identity/security backend | Security, backend, frontend | Completes admin trust surface. |
| Fabric placement recommendation and governed migration | P3 | High | Medium/High | Fabric backend | Backend, platform, frontend | Makes Fabric operationally safe. |
| Generated product truth documentation | P2 | Medium | High | Registry and docs pipeline | Frontend, product, docs | Prevents future docs/implementation drift. |
| Role-specific product modes for steward/operator/admin/auditor | P3 | High | High | Permission model | Product, security, frontend | Makes product simpler for each audience. |

## 14. Final Ideal UX Vision

After remediation, Data Cloud should feel like a calm operational workspace rather than a collection of powerful but uneven tools. A user opens the product and sees only what needs attention, what changed recently, and the next best action. The product knows the deployment's capabilities, the user's permission context, and the current operational state; it does not ask the user to interpret preview routes, local-only controls, unsupported settings, stale docs, or placeholder metrics.

Core work should be organized around a few durable journeys:

- Understand data assets.
- Ask and answer data questions.
- Build and monitor workflows.
- Govern trust and risk.
- Resolve operational exceptions.
- Administer capabilities only when appropriate.

The system should handle routine work automatically: detect capabilities, infer schemas, classify sensitivity, validate queries, group alerts, draft workflows, summarize failures, recommend owners, and surface only the exceptions that need human judgment. AI/ML should mostly feel like good defaults, helpful summaries, fewer repeated fields, clearer priorities, and safer recommendations. It should not feel like a separate feature users must learn.

Completeness should come from every route and action having closure: loading, empty, unavailable, degraded, success, partial failure, retry, audit, undo/rollback where possible, and next step. No action should look real unless it is real, and no preview should be discoverable as production.

Correctness should be enforced structurally. Route truth, capability truth, action truth, trust truth, and state truth should be generated from canonical metadata and backend contracts. The UI should never show zeros for unknown data, static trust signals for dynamic queries, or success for local-only settings.

Consistency should be maintained through a small set of shared patterns: resource detail, operation timeline, query state, guarded action, capability boundary, filter bar, trust signal, AI suggestion, and role/permission notice. Similar operations should look and behave the same everywhere.

Trust should be pervasive but quiet. Users should see privacy, security, and observability details exactly when they affect a decision: before a sensitive action, when a recommendation is made, when automation acts, when a policy blocks something, or when an incident is being resolved. Everything else should stay out of the way.

## 15. Executive Summary Lists

### Top 10 Critical Issues

1. Fake Settings/API-key actions create false security and persistence.
2. Workflow Run/Pause/Edit/Logs/Delete controls are visible but inert.
3. Query trust badges are static rather than query-derived.
4. Collection governance fields are captured but not persisted.
5. Data route can indefinitely load when backend/MSW is unavailable.
6. Route/capability truth is split across conflicting sources.
7. Operations can show zero counts or current-time checks for unavailable data.
8. Fabric migration lacks dry-run, approval, rollback, and audit safeguards.
9. Create Collection can crash accessibility flow via sensitivity badge.
10. Unsupported/preview surfaces remain discoverable and actionable.

### Top 10 Completeness Gaps

1. True collection detail route.
2. Workflow execution timeline/logs/retry/recovery.
3. Durable Settings and API key lifecycle.
4. Background operation center.
5. Alert reason/assignment/audit closure.
6. Data quality UI with progress/results/remediation.
7. Fabric migration lifecycle.
8. Saved query persistence.
9. Role-specific onboarding/home.
10. Permission-denied/request-access paths.

### Top 10 Simplification Opportunities

1. Generate nav/search from one route registry.
2. Collapse Home into a prioritized work queue.
3. Merge Data/Entity/Context into one asset detail model.
4. Merge workflow creation modes into one progressive journey.
5. Hide advanced diagnostics by default.
6. Remove fake and inert controls.
7. Replace AI-branded modes with embedded suggestions.
8. Standardize all query states.
9. Move Fabric/Agents/Memory behind admin/operator context.
10. Use one guarded-action pattern for risky operations.

### Top 10 Correctness Issues

1. Settings boundary contradicted by writable controls.
2. Static query trust indicators.
3. Ignored collection/workflow API params.
4. Inert workflow controls.
5. Operations fallback zeros.
6. Fabric live/preview contradiction.
7. Onboarding references non-existent `Settings -> AI`.
8. Collection governance metadata dropped on submit.
9. Mock saved queries in product path.
10. MSW failure without route-level recovery.

### Top 10 Consistency Issues

1. Route truth split across code/docs/tests.
2. Static nav vs registry nav.
3. Static search vs route registry.
4. Workflows/Pipelines terminology drift.
5. Data/Entities/Context overlap.
6. AI Assist/Ask/Grouped terminology drift.
7. Destructive action safeguards vary by page.
8. Loading/error/empty states vary by page.
9. Modal/drawer/form behavior varies by page.
10. Role/disclosure/permission language varies.

### Top 10 AI/ML Opportunities

1. Capability-aware onboarding.
2. Schema inference.
3. Sensitivity and retention classification.
4. Query generation with policy validation.
5. Query risk/cost evaluation.
6. Workflow draft and validation repair.
7. Alert grouping/deduplication.
8. Alert owner/reason suggestion.
9. Data quality anomaly detection.
10. Fabric placement recommendation.

### Top 10 Trust / Visibility / Privacy / Security UX Improvements

1. Remove fake API key lifecycle.
2. Add guarded-action framework.
3. Add audit links after sensitive actions.
4. Distinguish unknown/unavailable/stale from zero.
5. Derive trust signals from backend policy and data.
6. Add reason capture for incident/destructive actions.
7. Add permission-denied/request-access states.
8. Add operation/job visibility center.
9. Add confidence/evidence for AI-assisted decisions.
10. Redact secrets and sensitive data in logs/summaries/snippets.

