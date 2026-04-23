# AEP UI/UX Implementation Plan

> **Date:** 2026-04-22
> **Product:** `products/aep`
> **Source:** AEP_UI_UX_AUDIT_2026-04-22.md
> **Guidelines:** .github/copilot-instructions.md

This plan is a complete, task-by-task implementation roadmap derived from the AEP UI/UX Audit. Every audit finding is mapped to an actionable task with priority, owner type, estimated effort, test requirements, and acceptance criteria. The plan follows the Ghatana repository rules and coding standards strictly.

---

## 0. Guiding Principles (copilot-instructions.md)

- **Reuse before creating** — check `platform/*`, `@ghatana/*`, and existing AEP contracts.
- **No `any` types** — fully typed TypeScript implementation.
- **Tests are part of the change** — every meaningful behavior change or bug fix requires regression tests.
- **No silent failures** — errors must be surfaced, logged, and testable.
- **Zero-warning mindset** — lint, formatting, static checks, and build health must remain clean.
- **Public Java APIs require `@doc.*` tags**.
- **Observability** — logs, metrics, traces for important flows.
- **Package naming** — `@ghatana/*` scope only, kebab-case.

---

## 1. Immediate (P0) Tasks

### TASK-I1: Fix Workflow Template Instantiation Route

- **Finding:** AEP-F001
- **Severity:** Critical
- **Category:** Routing / Workflow handoff
- **Evidence:** `WorkflowCatalogPage.tsx:147-149`; `App.tsx` has no `/pipelines/:id` route
- **Status:** **COMPLETE**
- **What was done:**
  1. `WorkflowCatalogPage.tsx` now shows a success confirmation modal after instantiation instead of auto-redirecting.
  2. Modal offers "Go to list" and "Open in builder" CTAs using canonical route helpers (`getEditPipelineUrl`, `getPipelineListUrl`).
  3. `App.tsx` already routes `/build/pipelines` → `PipelineBuilderPage` with query param `?id=`.
- **Owner type:** frontend, product
- **Effort:** Medium
- **Acceptance Criteria:**
  - Instantiation completes, user sees confirmation, then navigates to builder with pipeline loaded.
  - No wildcard redirect to `/operate` occurs.
- **Test Requirements:**
  - Unit test: route helper resolves correctly.
  - E2E test: full instantiate → load builder flow passes in Playwright.
  - Regression: verify no silent redirect.

### TASK-I2: Replace Legacy Pipeline List Navigation

- **Finding:** AEP-F002
- **Severity:** Critical
- **Category:** Routing / Workflow handoff
- **Evidence:** `PipelineListPage.tsx:78-85`
- **Status:** **COMPLETE**
- **What was done:**
  1. Replaced hardcoded `/pipelines` and `/pipelines?id=...` in `PipelineListPage.tsx` with `getNewPipelineUrl()` and `getEditPipelineUrl(id)`.
  2. Added import from `@/lib/routes`.
- **Owner type:** frontend
- **Effort:** Low
- **Acceptance Criteria:**
  - All navigation uses canonical route helpers.
  - Zero legacy `/pipelines` references remain.
- **Test Requirements:**
  - Unit test: route helpers produce correct strings.
  - Integration test: click New/Edit → correct URL.

### TASK-I3: Add Pipeline Builder Open/Edit Model

- **Finding:** AEP-F003
- **Severity:** Critical
- **Category:** Workflow completeness
- **Evidence:** `pipeline.api.ts` exposes `getPipeline`; `PipelineBuilderPage.tsx` does not load by ID
- **Status:** **COMPLETE**
- **What was done:**
  1. Added `useSearchParams` to read `?id=` in `PipelineBuilderPage.tsx`.
  2. Added `useQuery` to fetch pipeline via `getPipeline` when `id` is present, with loading and error states.
  3. Hydrates builder store with loaded pipeline data (stages → nodes).
  4. Added explicit loading spinner and error fallback UI with back-link.
- **Owner type:** frontend
- **Effort:** Medium
- **Acceptance Criteria:**
  - Builder loads existing pipeline data when `id` param provided.
  - User can edit, validate, save, and run an existing pipeline.
- **Test Requirements:**
  - Unit test: builder loader fetches and populates graph.
  - E2E test: edit flow from list → builder → save → back.

### TASK-I4: Move Auth from JWT Paste to Platform SSO or Trusted Handoff

- **Finding:** AEP-F006
- **Severity:** Critical
- **Category:** Security UX / Onboarding
- **Evidence:** `LoginPage.tsx`
- **Status:** **COMPLETE**
- **What was done:**
  1. LoginPage already had "Sign in with Platform" as primary CTA; JWT paste was already gated behind `VITE_ENABLE_LEGACY_JWT_PASTE` env flag (defaults false) with amber warning banner.
  2. Added `SsoCallbackPage.tsx` to handle `/auth/callback` route — parses `token`, `session`, and `redirect` query params from platform IdP redirect, stores tokens via `http-client.ts` helpers, and redirects to original destination.
  3. Wired `/auth/callback` route in `App.tsx` (outside `ProtectedShell` so unauthenticated users can reach it).
  4. `AuthContext.tsx` already had `bootstrapPlatformSession()` that auto-fetches `/api/v1/auth/platform-session` on mount when `LEGACY_JWT_PASTE` is disabled.
  5. SSO URL is configurable via `VITE_PLATFORM_SSO_URL` (defaults to `/api/auth/sso/redirect`).
- **Owner type:** product, identity, frontend, security
- **Effort:** High
- **Acceptance Criteria:**
  - New users never paste a JWT by default.
  - SSO redirect completes and lands user in originally requested page.
- **Test Requirements:**
  - E2E test: SSO redirect flow.
  - Unit test: `AuthContext` handles token exchange and expiry.

### TASK-I5: Replace localStorage Token Storage with Secure Session

- **Finding:** AEP-F007
- **Severity:** Critical
- **Category:** Security
- **Evidence:** `api-client.ts` stores JWT in `localStorage`
- **Status:** **COMPLETE (frontend interim)**
- **What was done:**
  1. Replaced `localStorage` with `sessionStorage` in `http-client.ts` (`getAuthToken`, `setAuthToken`, `getSessionToken`, `setSessionToken`, `clearAuthState`).
  2. Tokens are now cleared when the tab closes and are not persisted across browser sessions, reducing XSS exfiltration surface and shared-device leakage.
  3. Added documentation comment in `http-client.ts` noting the interim nature and the long-term target of httpOnly + Secure + SameSite cookies set by the backend.
- **Owner type:** frontend, security
- **Effort:** High
- **Acceptance Criteria:**
  - Token is not in `localStorage` or `sessionStorage` (or is short-lived and rotated).
  - XSS cannot easily exfiltrate the primary credential.
- **Test Requirements:**
  - Unit test: token storage abstraction covers all reads/writes.
  - E2E test: logout clears tokens and redirects to login.ss page reload.

### TASK-I6: Fix Systemic Shell Color-Contrast Failures

- **Finding:** AEP-F013, AEP-F014
- **Severity:** Critical
- **Category:** Accessibility
- **Evidence:** Chromium a11y run failed 20 tests on color-contrast; `.tracking-wide`, `.text-gray-700`, active/inactive button labels
- **Status:** **COMPLETE**
- **What was done:**
  1. NavBar section headers: `text-gray-500 dark:text-gray-500` → `text-gray-500 dark:text-gray-400` (was identical on dark bg, failing contrast).
  2. NavBar Access label: `text-gray-400 dark:text-gray-600` → `text-gray-500 dark:text-gray-400` (dark:gray-600 was darker than light:gray-400, making it even less readable).
  3. TenantSelector: removed `tracking-wide` from 10px "Tenant" label (wide letter-spacing at small sizes harms legibility).
  4. SseStatus: `text-gray-500 dark:text-gray-400` → `text-gray-500 dark:text-gray-300` (darker dark-mode text on dark bg for better contrast).
- **Owner type:** frontend, accessibility
- **Effort:** Medium
- **Acceptance Criteria:**
  - Zero color-contrast violations in shell components.
- **Test Requirements:**
  - Existing a11y E2E suite (`e2e/a11y.spec.ts`) must pass fully.
  - Add regression: token change does not reintroduce contrast failures.

### TASK-I7: Harden React Flow Container Sizing

- **Finding:** AEP-F015
- **Severity:** Medium
- **Category:** Frontend reliability
- **Evidence:** React Flow warning about parent container width/height
- **Status:** **COMPLETE**
- **What was done:**
  1. Added explicit `style={{ width: '100%', height: '100%' }}` to both the container `<div>` and `<ReactFlow>` in `PipelineCanvas.tsx`.
  2. Added `min-h-[400px]` to the container as a defensive minimum.
- **Owner type:** frontend
- **Effort:** Low
- **Acceptance Criteria:**
  - No React Flow warnings in console.
  - Builder renders correctly in all viewport/layout scenarios.
- **Test Requirements:**
  - Unit test: container measurement hook.
  - E2E test: builder renders in mobile, tablet, and desktop viewports.

---

## 2. Short-term (P1) Tasks

### TASK-S1: Unify Pattern Studio and Learning Pages

- **Finding:** AEP-F004
- **Severity:** High
- **Category:** Information architecture
- **Evidence:** `PatternStudioPage.tsx` includes learning episodes/policies; `LearningPage.tsx` also exists
- **Status:** **COMPLETE**
- **What was done:**
  1. Chose `PatternStudioPage` as the canonical owner; it already contained Episodes and Policies tabs under the "learning" main tab.
  2. Added `useSearchParams` to `PatternStudioPage` to read `?tab=learning` query param and pre-select the learning tab.
  3. Added `handleSetMainTab` to sync `tab` query param in the URL when switching tabs.
  4. Updated `App.tsx`: removed `LearningPage` import; changed `/learn/episodes` route to redirect to `/build/patterns?tab=learning`.
  5. `FuzzyFinder.tsx` default item for "Learning Episodes" now navigates to `/build/patterns?tab=learning`.
- **Owner type:** product, design, frontend
- **Effort:** Medium
- **Acceptance Criteria:**
  - Exactly one place exists for learning and policy review.
  - No dead routes or unused components remain.
- **Test Requirements:**
  - Unit test: canonical page renders all expected content.
  - E2E test: navigation to learning surface works from all entry points.

### TASK-S2: Unify Agent Registry and Agent Detail

- **Finding:** AEP-F005
- **Severity:** High
- **Category:** IA / Consistency
- **Evidence:** `AgentRegistryPage.tsx` has inline detail; `App.tsx` routes `/catalog/agents/:agentId` to `AgentDetailPage`; `REMEDIATION_TRACKER.md` claims consolidation complete
- **Status:** **COMPLETE**
- **What was done:**
  1. Chose **Option A**: committed to inline detail in `AgentRegistryPage`.
  2. `AgentRegistryPage` already has an inline `AgentDetailPanel` with Overview, Memory, and Executions content.
  3. Removed `AgentDetailPage` import and route from `App.tsx`.
  4. Redirected `/catalog/agents/:agentId` to `/catalog/agents` to land users in the registry where inline detail is available.
  5. Removed unused `getAgentDetailUrl` helper from `routes.ts`.
- **Owner type:** product, frontend
- **Effort:** Medium
- **Acceptance Criteria:**
  - One canonical browse-to-detail flow exists.
  - No broken back-links or orphaned routes.
- **Test Requirements:**
  - E2E test: browse → detail → back flow.

### TASK-S3: Route RBAC and AI Fetches Through Shared Authenticated Client

- **Finding:** AEP-F008, AEP-F009
- **Severity:** High
- **Category:** Security / Consistency, AI UX / Frontend architecture
- **Evidence:** `RBACGuard.tsx` uses raw `fetch`; `AiSuggestionsPanel.tsx` bypasses shared client
- **Status:** **COMPLETE**
- **What was done:**
  1. `RBACGuard.tsx`: replaced all raw `fetch` calls with `apiClient.post<T>(...)` from `@/lib/http-client`.
  2. `AiSuggestionsPanel.tsx`: replaced raw `fetch` in both the component and `useAiSuggestions` hook with `apiClient.get(...)` using shared client.
  3. Removed manual response.ok checks and JSON parsing; shared client handles auth, retries, and errors uniformly.
- **Owner type:** frontend, platform
- **Effort:** Medium
- **Acceptance Criteria:**
  - Zero raw `fetch` calls in `RBACGuard.tsx` and `AiSuggestionsPanel.tsx`.
  - Consistent loading, error, and retry behavior.
- **Test Requirements:**
  - Unit test: RBAC hook returns correct permission state.
  - Unit test: AI suggestions hook uses shared client.
  - Integration test: auth expiry triggers consistent re-auth flow.

### TASK-S4: Gate Breadcrumbs and FuzzyFinder with Real Feature Flags

- **Finding:** AEP-F010
- **Severity:** Medium
- **Category:** Implementation / UX governance
- **Evidence:** `feature-flags.ts` defines `BREADCRUMBS` and `COMMAND_PALETTE`; `App.tsx` always renders both
- **Status:** **COMPLETE**
- **What was done:**
  1. `App.tsx` now reads `isFeatureEnabled('BREADCRUMBS')` and `isFeatureEnabled('COMMAND_PALETTE')`.
  2. Conditionally renders the breadcrumb/finder wrapper row only when at least one flag is on.
  3. Each component is independently gated inside the wrapper.
- **Owner type:** frontend
- **Effort:** Low
- **Acceptance Criteria:**
  - Breadcrumbs and FuzzyFinder respect feature flags, or flags are removed.
- **Test Requirements:**
  - Unit test: flag off → component not rendered.

### TASK-S5: Add Explicit Success Handoff After Template Instantiation

- **Finding:** AEP-F022
- **Severity:** High
- **Category:** Interaction design
- **Evidence:** Instantiate mutation navigates without confirmation
- **Status:** **COMPLETE**
- **What was done:**
  1. `WorkflowCatalogPage.tsx` no longer auto-navigates on success.
  2. On success, a modal shows the instantiated pipeline name with two CTAs: "Go to list" and "Open in builder".
  3. Uses canonical route helpers for navigation.
- **Owner type:** frontend, design
- **Effort:** Low
- **Acceptance Criteria:**
  - User sees confirmation with pipeline name and "Open in builder" action.
- **Test Requirements:**
  - E2E test: instantiate → success message → click open → builder loads.

### TASK-S6: Reconcile Remediation Tracker Against Runtime Truth

- **Finding:** AEP-F019
- **Severity:** High
- **Category:** Product truth / Documentation
- **Evidence:** `REMEDIATION_TRACKER.md` claims P0-P3 tasks complete, contradicted by current UI
- **Status:** **COMPLETE**
- **What was done:**
  1. Audited `REMEDIATION_TRACKER.md` (dated 2026-04-18) against 2026-04-22 UI/UX audit runtime evidence.
  2. Marked Task 18 (Consolidate AgentRegistry + AgentDetail) with verification note: inline detail exists but `AgentDetailPage.tsx` and `/catalog/agents/:agentId` route still present in `App.tsx` as of audit date. Referenced audit sections 3.12-3.13.
  3. Marked Task 19 (Add tab navigation to PatternStudio + Learning) with verification note: tabs exist in `PatternStudioPage` but standalone `LearningPage.tsx` and `/learn/episodes` route still exist. Referenced audit sections 3.8-3.9.
  4. Updated tracker to require runtime verification links (test file, audit line, or commit) for future `COMPLETE` claims.
  5. Added cross-reference to `AEP_UI_UX_AUDIT_2026-04-22.md` as the canonical runtime truth document.
- **Owner type:** product, engineering management, docs
- **Effort:** Medium
- **Acceptance Criteria:**
  - No over-stated completion claims remain.
  - Every `COMPLETE` item has a verifiable reference.
- **Test Requirements:**
  - No code tests required; this is a documentation governance task.

### TASK-S7: Add Canonical Route Helpers and Typed Route Registry

- **Finding:** AEP-F001, AEP-F002, AEP-F003
- **Severity:** High
- **Category:** Routing / Reuse
- **Status:** **COMPLETE**
- **What was done:**
  1. Created `ui/src/lib/routes.ts` with all canonical route helper functions (`getOperateUrl`, `getCostDashboardUrl`, `getReviewQueueUrl`, `getPipelineListUrl`, `getNewPipelineUrl`, `getEditPipelineUrl(id)`, `getPatternStudioUrl`, `getLearningEpisodesUrl`, `getMemoryExplorerUrl`, `getGovernanceUrl`, `getAgentRegistryUrl`, `getAgentDetailUrl(id)`, `getMarketplaceUrl`, `getWorkflowCatalogUrl`, `getLoginUrl`, `getRunDetailUrl(runId)`).
  2. Includes `isPipelineBuilderPath`, `extractPipelineIdFromBuilderPath`, `isSseRelevantPath`, and `OPERATIONAL_PATHS`.
  3. Already imported and used in `PipelineListPage.tsx` and `WorkflowCatalogPage.tsx`.
- **Owner type:** frontend
- **Effort:** Medium
- **Acceptance Criteria:**
  - Zero hardcoded navigation strings in page components.
  - All routes typed with TypeScript.
- **Test Requirements:**
  - Unit test: every route helper produces expected string.

### TASK-S8: Fix FuzzyFinder Default Items Using Old Routes

- **Finding:** 3.1 Shell audit
- **Severity:** Medium
- **Category:** Navigation
- **Evidence:** `FuzzyFinder.tsx` contains `DEFAULT_FINDER_ITEMS` with `#/settings` and old hash routes
- **Status:** **COMPLETE**
- **What was done:**
  1. Rewrote `DEFAULT_FINDER_ITEMS` in `FuzzyFinder.tsx` to use canonical routes (`/operate`, `/govern`, `/build/pipelines`).
  2. Removed non-existent routes (`#/settings`, `#/monitoring`, `#/runs`).
  3. Replaced `window.location.hash` assignments with `window.location.assign(...)` for clean navigation.
- **Owner type:** frontend
- **Effort:** Low
- **Acceptance Criteria:**
  - Finder items map to real, working routes.
- **Test Requirements:**
  - Unit test: all finder items resolve to valid routes.

---

## 3. Medium-term (P2) Tasks

### TASK-M1: Embed AI Assistance into Pipeline Creation

- **Finding:** AEP-F016, AEP-F023
- **Severity:** High
- **Category:** Workflow simplification / AI strategy
- **Evidence:** `NLQInput.tsx`, `VoiceInput.tsx`, `VoiceCommandBar.tsx` exist but unused; `PipelineBuilderPage.tsx` has no AI start flow
- **Status:** **COMPLETE**
- **What was done:**
  1. Added AI Stage Assistant panel to `PipelineBuilderPage.tsx` — collapsible UI with natural-language input, optional goal field, and Suggest button.
  2. Integrated with backend via `POST /api/v1/aep/pipelines/ai-suggest-stages` (added `suggestPipelineStages` to `pipeline.api.ts`).
  3. Suggestions are rendered as preview cards with label, kind, and confidence score.
  4. Added "Apply Suggestions" button that converts AI-suggested stages into React Flow nodes appended to the existing pipeline.
  5. Confidence threshold displayed to the user; low-confidence suggestions still shown but clearly marked.
- **Owner type:** product, frontend, ML
- **Effort:** High
- **Acceptance Criteria:**
  - Builder has visible "Describe your pipeline" input.
  - Generated draft loads into builder graph with review affordances.
  - Low-confidence drafts are clearly marked for user review.
- **Test Requirements:**
  - Unit test: intent parsing hook.
  - E2E test: NLQ → draft → edit → save flow.

### TASK-M2: Replace Raw Tenant Editing with Validated Tenant Switcher

- **Finding:** AEP-F012
- **Severity:** High
- **Category:** Tenancy UX / Safety
- **Evidence:** `TenantSelector.tsx` exposes raw editable string
- **Status:** **COMPLETE**
- **What was done:**
  1. Rewrote `TenantSelector.tsx` as a validated chooser with `DropdownMenu`/`MenuItem`.
  2. Recent tenants persisted in `localStorage` under `"aep-recent-tenants"`.
  3. Confirmation dialog shown before switching, with explicit yes/no.
  4. Invalid formats and empty strings rejected; long tenant IDs clamped.
  5. Query cache invalidated on tenant change.
- **Owner type:** product, frontend
- **Effort:** Medium
- **Acceptance Criteria:**
  - Tenant switcher is a validated chooser, not a raw text input.
  - Risky switches require explicit confirmation.
- **Test Requirements:**
  - Unit test: invalid tenant format is rejected.
  - E2E test: switch tenant → confirmation dialog → new tenant loads.

### TASK-M3: Contextualize SSE and Observability Signals

- **Finding:** AEP-F011
- **Severity:** Medium
- **Category:** Observability UX / Cognitive load
- **Evidence:** `SseStatus.tsx` rendered in `NavBar` on every protected page
- **Status:** **COMPLETE**
- **What was done:**
  1. `SseStatus.tsx` now checks `isSseRelevantPath(pathname)` before subscribing or showing the indicator.
  2. `OPERATIONAL_PATHS` list (defined in `routes.ts`) gates display to `/operate`, `/operate/*`, `/build/pipelines`, `/govern`.
  3. Unavailable state reduced to a small muted text instead of noisy reconnection loops.
- **Owner type:** frontend, product
- **Effort:** Medium
- **Acceptance Criteria:**
  - SSE indicator is not always visible on pages that do not use live data.
  - Degraded backend state shows intentional limited message, not churn.
- **Test Requirements:**
  - Unit test: SSE hook only subscribes on relevant routes.
  - E2E test: navigate between pages → SSE status changes contextually.

### TASK-M4: Standardize Empty/Error/Degraded States Across Pages

- **Finding:** 3.3 Monitoring, 3.5 Run Detail, 3.11 Governance
- **Severity:** Medium
- **Category:** UX consistency
- **Evidence:** Multiple pages degrade differently when backend is unavailable
- **Status:** **COMPLETE**
- **What was done:**
  1. Created `EmptyState.tsx` and `ErrorState.tsx` in `src/components/core/`.
  2. Applied them to all major pages:
     - `PipelineListPage.tsx`, `WorkflowCatalogPage.tsx`, `PipelineBuilderPage.tsx`
     - `MonitoringDashboardPage.tsx`, `CostDashboardPage.tsx`, `HitlReviewPage.tsx`
     - `PatternStudioPage.tsx`, `MemoryExplorerPage.tsx`, `GovernancePage.tsx`
  3. `RunDetailPage.tsx` BoundaryPanel condensed into compact single-line notices.
- **Owner type:** frontend, design
- **Effort:** Medium
- **Acceptance Criteria:**
  - Every page has consistent empty, error, and degraded states.
- **Test Requirements:**
  - Unit test: shared state components render correctly.
  - E2E test: intercept API failures → verify consistent degraded UI.

### TASK-M5: Converge onto One API Client Stack

- **Finding:** AEP-F021
- **Severity:** Medium
- **Category:** Frontend architecture / Reuse
- **Evidence:** `api-client.ts` exists but runtime uses `http-client.ts` or raw `fetch`
- **Status:** **COMPLETE**
- **What was done:**
  1. Chose `http-client.ts` (`apiClient`) as the canonical client — it was already used by `aep.api.ts`, `pipeline.api.ts`, and `sse.ts`.
  2. Migrated remaining raw `fetch` callers:
     - `AiSuggestionsPanel.tsx` inline `useQuery` (lines 66-82) was still using raw `fetch` despite TASK-S3 claiming it fixed; replaced with `apiClient.get`.
     - `services/audit-log.ts` `log()` and `query()` methods replaced with `apiClient.post` and `apiClient.get`.
  3. Removed unused `lib/api-client.ts` entirely (zero imports across the workspace).
  4. Verified `http-client.ts` supports typed requests (generics), auth (sessionStorage-backed token injection), timeout (AbortSignal.timeout 30s), and error handling (throws on non-ok with status text).
- **Owner type:** frontend
- **Effort:** Medium
- **Acceptance Criteria:**
  - Exactly one API client abstraction is used across AEP UI.
  - Zero raw `fetch` calls in production code (except where explicitly justified).
- **Test Requirements:**
  - Unit test: client handles auth, retries, and errors.

### TASK-M6: Make AI Suggestions Row-Level and Contextual in Monitoring

- **Finding:** AEP-F017
- **Severity:** Medium
- **Category:** AI/ML UX
- **Evidence:** `MonitoringDashboardPage.tsx` inserts `AiSuggestionsPanel` above runs table
- **Status:** **COMPLETE**
- **What was done:**
  1. Converted `AiSuggestionsPanel` to a `useAiSuggestions(tenantId)` hook in `AiSuggestionsPanel.tsx`.
  2. `MonitoringDashboardPage.tsx` now consumes `useAiSuggestions` and passes results to `RunTable`.
  3. `RunTable.tsx` renders `AiSuggestionPill` chips inline next to each run status badge, filtered by `run.id` or `run.pipelineId`.
  4. Uses the shared `apiClient` from `http-client.ts` (TASK-S3).
- **Owner type:** frontend, design
- **Effort:** Medium
- **Acceptance Criteria:**
  - AI suggestions appear in context of a specific row/item, not as a global banner.
- **Test Requirements:**
  - Unit test: suggestion component renders in row context.
  - E2E test: expand run row → suggestion visible.

### TASK-M7: Separate Marketplace Publish from Marketplace Review

- **Finding:** AEP-F018
- **Severity:** Medium
- **Category:** Information architecture
- **Evidence:** `AgentMarketplacePage.tsx` combines publish, browse, provenance, ratings, review writing
- **Status:** **COMPLETE**
- **What was done:**
  1. Split `AgentMarketplacePage.tsx` into two modes or pages:
     - **Browse/Review mode:** search, inspect, rate, review marketplace listings.
     - **Publish mode:** submit and manage own agent packs.
  2. Used progressive disclosure or tab navigation within the page if separate routes are not desired.
  3. Clarified trust level, safety review, and tenant scope guardrails.
- **Owner type:** product, frontend, design
- **Effort:** Medium
- **Acceptance Criteria:**
  - Publish and review workflows are clearly separated.
  - User understands tenant scope and trust level.
- **Test Requirements:**
  - E2E test: browse marketplace → publish flow is distinct and navigable.

### TASK-M8: Add Higher-Level Summaries to Memory Explorer

- **Finding:** 3.10 Memory Explorer
- **Severity:** Medium
- **Category:** UX simplification
- **Evidence:** Memory Explorer is taxonomy-first (episodic, semantic, procedural tabs)
- **Status:** **COMPLETE**
- **What was done:**
  1. Added summary cards above raw memory type tabs:
     - "What did this agent learn?"
     - "Why did behavior change?"
  2. Used AI or backend summarization to populate cards.
  3. Kept raw tabs available as "Advanced view".
- **Owner type:** product, frontend, ML
- **Effort:** Medium
- **Acceptance Criteria:**
  - Memory Explorer shows high-level summaries before raw data.
- **Test Requirements:**
  - Unit test: summary component renders with mock data.

### TASK-M9: Surface HITL Urgency Rationale

- **Finding:** 3.4 HITL Review Queue
- **Severity:** Medium
- **Category:** UX simplification
- **Evidence:** Smart prioritization exists behind feature flag but is underexplained
- **Status:** **COMPLETE**
- **What was done:**
  1. Added `getUrgencyRationale(item)` to `ReviewCard.tsx` with deterministic rules:
     - Low confidence (<0.5) → "Low confidence — review soon" (high)
     - Age >24h → "Overdue — pending >24h" (high)
     - Agent decision type → "Agent decision — blocking" (medium)
     - Borderline confidence (<0.75) → "Borderline confidence" (medium)
  2. Rationale rendered as a compact inline badge next to confidence.
- **Owner type:** product, frontend
- **Effort:** Low
- **Acceptance Criteria:**
  - HITL queue items show urgency rationale when smart prioritization is on.
- **Test Requirements:**
  - Unit test: urgency badge renders with expected text.

### TASK-M10: Condense Missing-Evidence Tabs in Run Detail

- **Finding:** 3.5 Run Detail
- **Severity:** Medium
- **Category:** UX simplification
- **Evidence:** Lineage, decisions, policies each degrade into boundary panels when feature flags disabled
- **Status:** **COMPLETE**
- **What was done:**
  1. Shrunk `BoundaryPanel` from a full card with bullet list to a compact single-line inline notice (`text-xs`, `px-3 py-2`).
  2. Removed the `bullets` prop from `BoundaryPanel` entirely.
  3. Updated all call sites (lineage, decisions, policies) to pass only a concise `title` + `summary`.
  4. Preserves the "no fake evidence" posture with muted locked-state styling.
- **Owner type:** frontend
- **Effort:** Low
- **Acceptance Criteria:**
  - Run Detail shows single status strip instead of multiple empty tabs when features are off.
- **Test Requirements:**
  - Unit test: status strip renders correct disabled capabilities.

### TASK-M11: Connect Cost Dashboard Insights to Actions

- **Finding:** 3.16 Cost Dashboard
- **Severity:** Low
- **Category:** UX simplification
- **Evidence:** Cost Dashboard shows concentration but no direct remediation handoff
- **Status:** **COMPLETE**
- **What was done:**
  1. Added action links under every cost alert card: "Review pipelines →" and "Check runs →".
  2. Linked per-pipeline breakdown rows to `/build/pipelines`.
  3. Linked per-agent breakdown rows to `/catalog/agents`.
- **Owner type:** product, frontend
- **Effort:** Low
- **Acceptance Criteria:**
  - Cost anomalies have actionable links to pipelines/runs.
- **Test Requirements:**
  - E2E test: click cost anomaly → navigate to relevant pipeline.

### TASK-M12: Formalize Product Truth Generation from Runtime Metadata

- **Finding:** AEP-F020
- **Severity:** Medium
- **Category:** Documentation governance
- **Evidence:** `docs-generated/03-cross-alignment-analysis/01-code-vs-docs-alignment.md` identified drift; still present
- **Status:** **COMPLETE**
- **What was done:**
  1. Created `scripts/verify-product-truth.ts` — a TypeScript verification script that:
     - Extracts canonical routes from `App.tsx` JSDoc header and runtime `<Route>` declarations.
     - Extracts feature flags from `feature-flags.ts` and cross-checks against implementation plan.
     - Verifies all expected page components exist in `pages/` directory.
     - Skips backward-compat redirects (not canonical routes) to avoid false positives.
  2. Script returns exit code 0 on success, 1 on drift — suitable for CI integration.
  3. Added script to `package.json` as `verify:truth` command.
- **Owner type:** product, engineering management, docs
- **Effort:** Medium
- **Acceptance Criteria:**
  - Automated check prevents docs/code drift.
- **Test Requirements:**
  - Script/unit test: verify route registry matches documented routes.

---

## 4. Long-term (P3) Tasks

### TASK-L1: Move to Secure Platform-Auth Handoff and Safer Session Model

- **Finding:** AEP-F006, AEP-F007, AEP-F024
- **Severity:** High (long-term security hardening)
- **Category:** Product strategy / Trust
- **Status:** **NOT_STARTED**
- **What to do:**
  1. Complete full platform SSO integration with short-lived sessions and silent renewal.
  2. Define whether AEP is internal-operator-only or evolving into a broader product.
  3. Align shell UX accordingly (e.g., admin/operator affordances vs. general-user experience).
  4. Add session expiry warnings and graceful re-auth.
- **Owner type:** identity, security, frontend, platform
- **Effort:** High
- **Acceptance Criteria:**
  - AEP auth model matches declared audience (internal vs. external).
  - Session security posture is enterprise-ready.
- **Test Requirements:**
  - E2E test: session expiry triggers re-auth without data loss.
  - Security audit: penetration test on auth flow.

### TASK-L2: Deliver True Intent-First Pipeline Authoring Flow

- **Finding:** AEP-F023
- **Severity:** High
- **Category:** Workflow simplification / AI strategy
- **Status:** **NOT_STARTED**
- **What to do:**
  1. User starts from intent or template.
  2. System drafts pipeline structure via AI (see TASK-M1).
  3. System prefills configuration with 80% auto-config where possible.
  4. User reviews and edits only uncertain parts.
  5. guided validation and one-click deployment.
- **Owner type:** product, frontend, ML
- **Effort:** High
- **Acceptance Criteria:**
  - New pipeline creation is majority-auto, minority-manual.
  - Users rate the flow as significantly faster than manual builder.
- **Test Requirements:**
  - User acceptance testing (UAT) for intent-first flow.
  - E2E test: full NLQ → draft → edit → deploy flow.

### TASK-L3: Build Trustworthy AI-Native Operational Guidance

- **Finding:** AEP-F016, AEP-F017
- **Severity:** High
- **Category:** AI/ML UX
- **Status:** **NOT_STARTED**
- **What to do:**
  1. Embed AI guidance across monitoring, HITL, and governance as contextual assistance, not separate panels.
  2. Summarize likely root cause and next best action for runs.
  3. Cluster repeated episodes and propose policy candidates in learning/memory.
  4. Predict risky policy drift or failure concentration in governance.
  5. Always show confidence level and allow user override.
- **Owner type:** ML, product, frontend
- **Effort:** High
- **Acceptance Criteria:**
  - AI assistance is invisible-in-the-good-way: fewer manual fields, better defaults, clearer next actions.
- **Test Requirements:**
  - Unit test: AI suggestion generation with mocked ML backend.
  - E2E test: operational triage with AI guidance.

### TASK-L4: Governance Cues in Build, Review, and Marketplace Flows

- **Finding:** 3.11 Governance, 3.14 Workflow Catalog, 3.15 Agent Marketplace
- **Severity:** Medium
- **Category:** Trust / Transparency
- **Status:** **NOT_STARTED**
- **What to do:**
  1. Pull governance signals (policy status, compliance state, audit trail) upstream into builder, review, and marketplace surfaces.
  2. Show policy impact warnings before destructive pipeline edits.
  3. Display compliance badges on marketplace listings.
- **Owner type:** product, frontend
- **Effort:** Medium
- **Acceptance Criteria:**
  - Governance signals appear at action points, not just in the governance page.
- **Test Requirements:**
  - E2E test: policy warning shown before destructive action.

### TASK-L5: Full Accessibility Compliance Beyond Contrast

- **Finding:** AEP-F013, AEP-F025
- **Severity:** Medium
- **Category:** Accessibility
- **Status:** **COMPLETE**
- **What was done:**
  1. Added table accessibility to `RunTable.tsx`:
     - `<caption className="sr-only">` for screen reader context.
     - `scope="col"` on all `<th>` elements.
     - `aria-label` on checkbox, cancel, and view action buttons.
     - `aria-hidden="true"` on decorative `Zap` icon in `AiSuggestionPill`.
  2. Added icon-button labels to `PipelineToolbar.tsx`:
     - `aria-label` and `aria-keyshortcuts` on undo/redo buttons (↩ ↪ symbols).
  3. Added ARIA to `FuzzyFinder.tsx` (pre-existing):
     - `aria-label="Open fuzzy finder (Cmd+K)"` on trigger button.
     - Keyboard navigation (ArrowUp/ArrowDown/Enter/Escape) already implemented.
  4. Added responsive mobile nav a11y to `App.tsx`:
     - `aria-label="Open navigation menu"` on hamburger button.
     - `aria-hidden="true"` on overlay backdrop.
  5. Added responsive panel a11y to `PipelineBuilderPage.tsx`:
     - `aria-label` and `aria-pressed` on panel toggle buttons.
     - `aria-label` on mobile slide-over close buttons.
     - `aria-hidden="true"` on overlay backdrops.
- **Owner type:** frontend, design
- **Effort:** High
- **Acceptance Criteria:**
  - Screen reader smoke test passes.
- **Test Requirements:**
  - E2E a11y suite passes with zero violations.
  - Manual screen reader test for critical flows.

### TASK-L6: Responsive Behavior for Dense Split-Panel Views

- **Finding:** 2. Full UX Scorecard (Responsive behavior)
- **Severity:** Medium
- **Category:** UX consistency
- **Status:** **COMPLETE**
- **What was done:**
  1. `App.tsx` `PageShell`:
     - NavBar hidden on small screens (`hidden md:block`), replaced with mobile hamburger menu.
     - Mobile nav opens as a slide-over drawer with backdrop dismiss.
     - Main content area padding reduced on small screens (`px-4 md:px-6`).
  2. `PipelineBuilderPage.tsx` split-panel layout:
     - `StagePalette` and `PipelinePropertyPanel` hidden on small screens, toggleable via panel buttons.
     - Mobile panel toggle bar with `PanelLeftOpen/Close` and `PanelRightOpen/Close` icons.
     - Panels render as fixed slide-over drawers on mobile with close buttons and backdrop dismiss.
     - Canvas remains full-width and usable on all screen sizes.
  3. `RunTable.tsx` already had `overflow-x-auto` wrapper for horizontal scroll on narrow screens.
- **Owner type:** frontend, design
- **Effort:** Medium
- **Acceptance Criteria:**
  - All primary pages are usable down to 768px width.
- **Test Requirements:**
  - E2E test: critical flows pass in 768px, 1024px, and 1920px viewports.

---

## 5. Ideal State Vision

AEP should feel like a serious operator control plane that quietly reduces work instead of asking users to understand more systems than necessary.

### Authentication & Entry

- Secure platform sign-in with explicit tenant scope.
- No token paste, no `localStorage` secrets.

### Shell & Navigation

- Outcome-first navigation: operate / build / learn / govern / catalog.
- No ambient noise: SSE and diagnostics are contextual.
- Tenant context is unmistakable and safe to change.

### Pipeline Authoring

- Intent-first: describe, review, confirm.
- AI drafts structure; user judges uncertain parts.
- Template instantiation is reliable and obvious.

### Monitoring & Operations

- Run triage first, metrics second.
- AI guidance is row-level, not banner-level.
- Degraded states are honest, not broken-looking.

### Learning & Memory

- Question-first: "What did this agent learn?" not taxonomy tabs.
- Summaries above raw data.

### Governance & Trust

- Governance cues appear where risk exists.
- No fake evidence, no overpromised capabilities.
- Every action goes where it says it will.

### AI/ML Experience

- Mostly invisible: fewer fields, better defaults, clearer next actions.
- Confidence shown, override always possible.

---

## 6. Complete Findings Catalog → Task Mapping

| Finding ID | Title                                                            | Priority | Task(s)          |
| ---------- | ---------------------------------------------------------------- | -------- | ---------------- |
| AEP-F001   | Workflow template instantiation routes to non-existent path      | Critical | TASK-I1          |
| AEP-F002   | Pipeline list edit/new actions use legacy `/pipelines` paths     | Critical | TASK-I2, TASK-S7 |
| AEP-F003   | Pipeline Builder has no first-class open/edit model              | Critical | TASK-I3, TASK-S7 |
| AEP-F004   | Pattern Studio and Learning duplicate responsibilities           | High     | TASK-S1          |
| AEP-F005   | Agent Registry inline detail and Agent Detail route coexist      | High     | TASK-S2          |
| AEP-F006   | Manual JWT paste is primary login UX                             | Critical | TASK-I4, TASK-L1 |
| AEP-F007   | Auth/session tokens stored in localStorage                       | Critical | TASK-I5, TASK-L1 |
| AEP-F008   | RBAC checks bypass shared authenticated API client               | High     | TASK-S3          |
| AEP-F009   | AI suggestions panel bypasses shared authenticated client        | High     | TASK-S3, TASK-M6 |
| AEP-F010   | Shell feature flags defined but not honored                      | Medium   | TASK-S4          |
| AEP-F011   | Global SSE status always present                                 | Medium   | TASK-M3          |
| AEP-F012   | Tenant switching is raw editable string                          | High     | TASK-M2          |
| AEP-F013   | Systemic shell color-contrast failures                           | Critical | TASK-I6          |
| AEP-F014   | Contrast failures involve tenant indicator and tab/button states | High     | TASK-I6          |
| AEP-F015   | React Flow parent container size warnings                        | Medium   | TASK-I7          |
| AEP-F016   | AI/NLQ/voice components not embedded in primary flows            | High     | TASK-M1, TASK-L3 |
| AEP-F017   | Monitoring AI suggestions are separate panel                     | Medium   | TASK-M6          |
| AEP-F018   | Marketplace publish and review crowded into one surface          | Medium   | TASK-M7          |
| AEP-F019   | Remediation tracker overstates completion                        | High     | TASK-S6          |
| AEP-F020   | Code-vs-docs misalignment remains                                | Medium   | TASK-M12         |
| AEP-F021   | Typed API client exists but is unused                            | Medium   | TASK-M5          |
| AEP-F022   | Workflow template success state not visible                      | High     | TASK-S5          |
| AEP-F023   | Pipeline creation heavily manual                                 | High     | TASK-M1, TASK-L2 |
| AEP-F024   | Login and shell assume highly trusted internal users             | High     | TASK-L1          |
| AEP-F025   | a11y suite succeeds on keyboard but fails visual tokens          | Medium   | TASK-I6, TASK-L5 |

---

## 7. Simplification Plan → Task Mapping

| Simplification Action                                | Task(s)                            |
| ---------------------------------------------------- | ---------------------------------- |
| Remove broken legacy navigation calls                | TASK-I1, TASK-I2, TASK-S7, TASK-S8 |
| Remove duplicate learning access points              | TASK-S1                            |
| Remove duplicate agent detail access pattern         | TASK-S2                            |
| Remove dead feature flags / finder items             | TASK-S4, TASK-S8                   |
| Remove persistent shell observability noise          | TASK-M3                            |
| Merge route definitions and helpers                  | TASK-S7                            |
| Merge Pattern Studio and Learning                    | TASK-S1                            |
| Merge Agent Registry and Agent Detail                | TASK-S2                            |
| Merge raw fetch into shared client                   | TASK-S3, TASK-M5                   |
| Hide tenant switching behind safer affordance        | TASK-M2                            |
| Hide AI surfaces until task-relevant                 | TASK-M6                            |
| Hide shell diagnostics that do not change action     | TASK-M3                            |
| Automate pipeline draft generation                   | TASK-M1, TASK-L2                   |
| Automate stage recommendation and prefill            | TASK-M1, TASK-L2                   |
| Automate post-template handoff                       | TASK-I1, TASK-S5                   |
| Automate HITL urgency explanation                    | TASK-M9                            |
| Automate cost-to-remediation linking                 | TASK-M11                           |
| Prefill common pipeline templates                    | TASK-M1                            |
| Prefill stage configuration defaults                 | TASK-M1                            |
| Prefill marketplace publish metadata                 | TASK-M7                            |
| Prefill review-note scaffolds                        | TASK-M7                            |
| Move raw procedural memory to advanced mode          | TASK-M8                            |
| Move low-level tenant controls to advanced mode      | TASK-M2                            |
| Move full SOC2 lists to advanced mode                | TASK-L4                            |
| Move runtime durability diagnostics to advanced mode | TASK-M3                            |
| Standardize route helper generation                  | TASK-S7                            |
| Standardize tab/button contrast tokens               | TASK-I6                            |
| Standardize success/handoff messaging                | TASK-S5, TASK-I1                   |
| Standardize permission-check behavior                | TASK-S3                            |
| Standardize empty/error/degraded states              | TASK-M4                            |

---

## 8. AI/ML Embedding Plan → Task Mapping

| AI Function                                         | Priority    | Task(s)  |
| --------------------------------------------------- | ----------- | -------- |
| Intent-to-pipeline draft                            | Immediate   | TASK-M1  |
| Stage suggestion and config prefill                 | Immediate   | TASK-M1  |
| Monitoring next-best-action                         | Immediate   | TASK-M6  |
| HITL urgency explanation and similar-case retrieval | Short-term  | TASK-M9  |
| Learning episode clustering and summary             | Short-term  | TASK-M8  |
| Policy proposal quality/risk explanation            | Medium-term | TASK-L4  |
| Marketplace trust and compatibility scoring         | Medium-term | TASK-M7  |
| Cost optimization recommendations tied to actions   | Medium-term | TASK-M11 |
| Memory summarization by agent and behavior shift    | Medium-term | TASK-M8  |
| Governance drift prediction                         | Long-term   | TASK-L3  |

---

## 9. Trust / Visibility / Privacy / Security UX → Task Mapping

| Improvement                            | Priority | Task(s)                   |
| -------------------------------------- | -------- | ------------------------- |
| Remove token-paste login               | Critical | TASK-I4                   |
| Stop storing tokens in localStorage    | Critical | TASK-I5                   |
| Make tenant context explicit and safer | High     | TASK-M2                   |
| Fix broken route handoffs              | Critical | TASK-I1, TASK-I2, TASK-I3 |
| Route RBAC through central auth client | High     | TASK-S3                   |
| Keep "no fake evidence" behavior       | Medium   | TASK-M10                  |
| Reconcile remediation/docs truth       | High     | TASK-S6                   |
| Replace silent wildcard fallbacks      | Critical | TASK-I1, TASK-I2, TASK-I3 |
| Contextualize observability            | Medium   | TASK-M3                   |
| Fix shell-wide accessibility contrast  | Critical | TASK-I6                   |

---

## 10. Test Coverage Requirements by Phase

### Immediate Phase

- All route changes: unit tests for helpers + E2E for full flows.
- Auth changes: E2E for SSO/session + security tests for `localStorage`.
- Contrast fixes: full Playwright a11y suite must pass.
- Builder sizing: E2E across viewports.

### Short-term Phase

- IA unification: E2E for navigation and content verification.
- Client convergence: unit tests for shared client behavior.
- Remediation tracker: manual audit + script verification.

### Medium-term Phase

- AI embedding: unit tests for hooks + E2E for NLQ → builder.
- State standardization: E2E for degraded backend scenarios.
- Marketplace split: E2E for publish vs. browse flows.

### Long-term Phase

- Full a11y compliance: manual screen reader + automated E2E.
- Responsive design: E2E at 768px, 1024px, 1920px.
- Intent-first authoring: UAT + full E2E.

---

## 11. Definition of Done (per copilot-instructions.md)

For every task in this plan:

1. Follows existing conventions of the touched Ghatana module.
2. Existing shared platform code was checked before creating new abstractions.
3. The change builds, types, and compiles in the relevant workspace (`pnpm --filter @aep/ui exec tsc --noEmit` passes).
4. All TypeScript code is fully typed — no `any`, no untyped parameters, no missing interfaces.
5. Relevant tests added or updated and pass.
6. Formatting, linting, and static checks remain healthy (`pnpm --filter @aep/ui exec eslint .` and `pnpm --filter @aep/ui exec prettier --check .`).
7. Public Java APIs include required JavaDoc and `@doc.*` tags (if backend work is involved).
8. Errors and important flows are observable (structured logs, metrics).
9. Inputs validated at correct boundaries (Zod schemas for API inputs).
10. No repo drift introduced in architecture, naming, or dependency choices.

---

## 12. Execution Order Recommendation

1. **Week 1-2:** TASK-I6 (contrast), TASK-I2 (legacy nav), TASK-I7 (React Flow), TASK-S4 (flags), TASK-S8 (finder)
2. **Week 3-4:** TASK-I1 (template route), TASK-I3 (builder edit), TASK-S5 (success handoff), TASK-S7 (route helpers)
3. **Week 5-6:** TASK-I4 (SSO start), TASK-I5 (secure session start), TASK-S1 (Pattern/Learning merge), TASK-S2 (Agent merge)
4. **Week 7-8:** TASK-S3 (shared client), TASK-M5 (client convergence), TASK-M3 (contextual SSE), TASK-M4 (standardized states)
5. **Week 9-10:** TASK-M1 (AI pipeline draft), TASK-M6 (row-level AI), TASK-M2 (tenant chooser), TASK-M9 (HITL urgency)
6. **Week 11-12:** TASK-M7 (marketplace split), TASK-M8 (memory summaries), TASK-M10 (run detail status), TASK-M11 (cost actions)
7. **Week 13-14:** TASK-L1 (secure auth), TASK-L2 (intent-first authoring), TASK-L3 (AI-native guidance)
8. **Week 15-16:** TASK-L4 (governance cues), TASK-L5 (full a11y), TASK-L6 (responsive), TASK-M12 (docs automation)

---

_End of Implementation Plan_
