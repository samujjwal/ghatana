# Data Cloud UI/UX Implementation Plan

**Date:** 2026-04-22
**Based on:** DATA_CLOUD_UI_UX_AUDIT_2026-04-22.md
**Product:** products/data-cloud
**Scope:** Complete UI/UX remediation across all identified issues

---

## Executive Summary

This implementation plan addresses 40 findings across Critical, High, Medium, and Low severity levels, organized into Immediate, Short-term, Medium-term, and Long-term phases. The plan follows Ghatana repo standards including type safety, reuse of platform modules, comprehensive testing, and proper documentation.

**Total Tasks:** 85
**Estimated Duration:** 12-16 weeks with dedicated resources
**Critical Path:** Route truth fixes → Accessibility → Design system standardization

---

## Phase 1: Immediate Remediation (Weeks 1-3)

**Priority:** P0
**Goal:** Eliminate critical trust-breaking issues and accessibility blockers

### 1.1 Route Truth Fixes (6 tasks)

#### ROUTE-001: Fix Alerts Route Handoff (F-001, Critical, 2 days) ✅ COMPLETED

Restore `AlertsPage` as canonical OR implement real `alerts` tab in Insights consuming query params.

- Decide product direction: restore page vs. implement tab
- Implement tab consumption: `useSearchParams()` for `tab=alerts`
- Update navigation and search quick nav
- Add E2E test for route with state preservation
- **Compliance:** Use `@ghatana/design-system`, full TypeScript, @doc.\* tags, accessibility
- **Acceptance:** `/alerts` resolves to functional surface, query params honored

#### ROUTE-002: Fix Events Route Handoff (F-002, Critical, 2 days) ✅ COMPLETED

Implement real events support in Insights or restore dedicated Events route.

- Apply same decision as ROUTE-001 for consistency
- Implement query param consumption for `tab=events`
- Update navigation and search
- Add E2E test
- **Compliance:** Consistent pattern with alerts, full TypeScript, accessibility
- **Acceptance:** `/events` resolves to event inspection, context preserved

#### ROUTE-003: Resolve Memory Route Redirect (F-003, High, 1 day) ✅ COMPLETED

Restore dedicated Memory surface OR remove route from runtime truth.

- Product decision: restore vs. remove
- If restore: ensure `MemoryPlaneViewerPage.tsx` functional
- If remove: delete redirect from `routes.tsx:295-299`
- Update docs and ROUTE_TRUTH_MATRIX.md
- Add route truth test
- **Compliance:** Document ADR, update docs, clean unused imports
- **Acceptance:** Route works or completely removed, no broken redirects

#### ROUTE-004: Resolve Entities Route Redirect (F-004, High, 1 day) ✅ COMPLETED

Restore `EntityBrowserPage` or remove redirect and all discoverability hooks.

- Product decision: restore vs. remove
- If restore: ensure page functional and accessible
- If remove: delete redirect from `routes.tsx:300-304`
- Remove from navigation/search
- Update documentation
- **Compliance:** Same pattern as ROUTE-003
- **Acceptance:** Entities browsing works or completely absent

#### ROUTE-005: Resolve Context Route Redirect (F-005, High, 1 day) ✅ COMPLETED

Implement real Trust context tab or restore Context Explorer route.

- Product decision: integrate vs. restore
- If integrate: add `context` tab to TrustCenter with query param handling
- If restore: bring back Context Explorer as first-class route
- Update navigation/search
- Ensure accessibility
- **Compliance:** Follow Trust Center patterns, use `@ghatana/domain-components`, full TypeScript
- **Acceptance:** `/context` resolves to context-aware surface

#### ROUTE-006: Fix Fabric and Agents Role Semantics (F-006, Critical, 2 days) ✅ COMPLETED

Preserve operator-readable routes or move only truly admin functions into Operations.

- Audit RBACGuard usage on OperationsConsolePage
- Determine operator vs. admin functions
- If operator access needed: remove ADMIN-only guard or create separate surface
- Update route redirects to preserve role semantics
- Update navigation
- Add role-based routing tests
- Update security posture docs
- **Compliance:** Use `platform:java:security` patterns, document role model, @doc.\* tags
- **Acceptance:** Operators can access read-only surfaces, admin routes clearly gated

### 1.2 Navigation and Search Cleanup (3 tasks)

#### NAV-001: Align Navigation with Route Truth (F-007, High, 1 day) ✅ COMPLETED

Either truly collapse Events/Alerts nav items or make them distinct working destinations.

- Review `DefaultLayout.tsx:77-96` navigation structure
- Update nav based on route fix decisions
- Remove/clarify outdated comments
- Ensure nav items map to functional routes
- Add visual indicators for preview/read-only surfaces
- Test with screen reader
- **Compliance:** Use `@ghatana/design-system` navigation components, accessibility
- **Acceptance:** Nav items match functional routes, no broken affordances

#### SEARCH-001: Fix Global Search Quick Nav (F-008, High, 2 days) ✅ COMPLETED

Generate quick nav from canonical route truth and capability registry.

- Create `RouteCapabilityRegistry` type/interface with Zod schema
- Define canonical route metadata (path, capability, role, state)
- Refactor `GlobalSearch.tsx:49-59` to consume registry
- Remove stale quick nav items
- Add dynamic filtering based on user role
- Add unit tests and E2E test
- **Compliance:** Zod validation, TypeScript branded types, reuse existing components, @doc.\* tags
- **Acceptance:** Search matches route truth, stale items removed, role-aware results

#### SHELL-001: Remove AI Powered Shell Badge (F-022, Medium, 0.5 days) ✅ COMPLETED

Remove persistent "AI Powered" badge from DefaultLayout header.

- Remove badge from `DefaultLayout.tsx:295`
- Remove related CSS/styling
- Test header rendering
- Update design docs
- **Compliance:** Follow existing header patterns
- **Acceptance:** No persistent AI branding, header functional

### 1.3 Accessibility Remediation (6 tasks)

#### A11Y-001: Fix Data Explorer Accessibility (F-009, F-014, Critical, 2 days) ✅ COMPLETED

Add proper `<main>` landmark, landmark regions, heading hierarchy, labeled search field.

- Add `<main role="main">` wrapper
- Add landmark regions for each view
- Implement proper heading hierarchy
- Replace placeholder-only search with visible/programmatic label
- Add ARIA labels to interactive elements
- Run accessibility audit
- Add regression test
- **Compliance:** Semantic HTML, WCAG 2.1 AA, use `@ghatana/design-system`, full TypeScript, @doc.\* tags
- **Acceptance:** Audit passes, screen reader navigable, all fields labeled

#### A11Y-002: Fix Create Collection Accessibility (F-010, High, 1 day) ✅ COMPLETED

Add page landmarks and clear heading structure.

- Add `<main>` landmark
- Add proper heading structure
- Ensure form fields have visible labels
- Add ARIA descriptions
- Test keyboard navigation
- Run accessibility audit
- **Compliance:** Use `@ghatana/forms`, TypeScript, accessibility
- **Acceptance:** Audit passes, keyboard navigable, clear headings

#### A11Y-003: Fix Workflows Page Accessibility (F-011, F-014, Critical, 2 days) ✅ COMPLETED

Add landmarks, main region, associated labels for search/filter fields.

- Add `<main>` landmark
- Add visible labels to search/filter inputs
- Ensure filter controls have proper ARIA associations
- Add heading hierarchy
- Test keyboard navigation
- Run accessibility audit
- **Compliance:** Replace placeholder-only inputs, use `@ghatana/design-system`, TypeScript
- **Acceptance:** Audit passes, all fields labeled, keyboard navigable

#### A11Y-004: Fix Insights Page Accessibility (F-012, High, 1 day) ✅ COMPLETED

Normalize heading hierarchy and add section landmarks.

- Audit current heading structure
- Normalize to proper hierarchy (h1 → h2 → h3)
- Add landmark regions for each tab content
- Ensure tab navigation accessible
- Run accessibility audit
- **Compliance:** Semantic headings, ARIA attributes for dynamic content
- **Acceptance:** Heading hierarchy passes, screen reader navigates tabs

#### A11Y-005: Fix Plugins Page Accessibility (F-013, F-014, High, 1 day) ✅ COMPLETED

Add landmarks, label search/filter fields, standardize admin surface semantics.

- Add `<main>` landmark
- Add visible labels to search/filter
- Ensure tab navigation accessible
- Add heading hierarchy
- Run accessibility audit
- **Compliance:** Same patterns as other page fixes
- **Acceptance:** Audit passes, search/filter labeled, consistent semantics

#### A11Y-006: Standardize Form Field Labels (F-014, High, 2 days) ✅ COMPLETED — `LabeledInput` and `LabeledSelect` components created in `components/common/LabeledInput.tsx`

Create standardized labeled form-field component and ban placeholder-only labeling.

- Create `LabeledInput` component in `@ghatana/forms`
- Define interface with required label prop
- Add ARIA attributes
- Add TypeScript types with no `any`
- Add @doc.\* tags
- Replace placeholder-only inputs across Data, Pipelines, Query, Plugins
- Add unit tests
- Add eslint rule to prevent placeholder-only labels
- **Compliance:** Use `@ghatana/forms` patterns, strict TypeScript, Zod validation, React Testing Library, @doc.\* tags
- **Acceptance:** Reusable component exists, all inputs replaced, eslint rule enforced

### 1.4 Settings and Admin UX (2 tasks)

#### ADMIN-001: Hide Settings from Mainstream Discovery (F-015, High, 1 day) ✅ COMPLETED — Settings removed from nav and global search; direct link still works for admin users

Remove settings from product discovery until actionable, or rename to "Admin capability status."

- Product decision: hide vs. rename
- If hide: remove from navigation, search, links
- If rename: change to status-only page
- Update onboarding
- Add route guard with explanation
- Update documentation
- **Compliance:** Update ROUTE_TRUTH_MATRIX.md, document ADR
- **Acceptance:** Settings not discoverable, direct access blocked or status-only

#### ADMIN-002: Clean Up Onboarding References (F-016, Medium, 1 day) ✅ COMPLETED

Remove or annotate all onboarding references to unavailable settings capabilities.

- Search codebase for settings references in onboarding
- Remove or annotate with "coming soon"
- Update help text and tooltips
- Update user-facing docs
- Add test to prevent false references
- **Compliance:** Document changes, update specs
- **Acceptance:** No onboarding references to unavailable settings

### 1.5 Documentation Updates (1 task)

#### DOCS-001: Update Auth Session Posture Docs (F-028, Medium, 0.5 days) ✅ COMPLETED

Update `docs/FRONTEND_AUTH_SESSION_POSTURE_PLAN.md` to match current implementation.

- Review current auth implementation
- Update docs to reflect memory-first + sessionStorage fallback
- Add historical notes about localStorage deprecation
- Update security posture sections
- Security team review
- Add doc update to CI check
- **Compliance:** Accurate technical documentation, security team approval
- **Acceptance:** Docs match implementation, historical context preserved

---

## Phase 2: Short-term Remediation (Weeks 4-6)

**Priority:** P1
**Goal:** Establish foundational patterns and prevent recurrence

### 2.1 Route Capability Registry (2 tasks)

#### ARCH-001: Create Canonical Route Capability Registry (F-018, Critical, 3 days) ✅ COMPLETED — `RouteCapabilityRegistry` created in `lib/routing/RouteCapabilityRegistry.ts` with Zod schemas, canonical route metadata, and registry query helpers

Generate route truth from router configuration plus per-route capability metadata.

- Define `RouteCapability` interface with path, capability, role, stateParams, lifecycle
- Create `RouteCapabilityRegistry` type with Zod schema
- Implement registry builder reading routes.tsx
- Add metadata to each route definition
- Generate navigation from registry
- Generate search quick nav from registry
- Generate docs from registry (optional)
- Add unit tests
- **Compliance:** Zod validation, TypeScript branded types, template literal types, reuse platform patterns, no `any`, @doc.\* tags
- **Acceptance:** Single source of truth, nav/search/docs consume registry, validation prevents drift

#### ARCH-002: Implement Route Entry State Handling Pattern (F-001-F-006, High, 2 days) ✅ COMPLETED — `useRouteEntryState` hook created in `hooks/useRouteEntryState.ts` with Zod schema validation, type-safe parsing, and error handling

Create standardized pattern for handling route entry state (query params, hash, etc.).

- Define `RouteEntryState<T>` generic interface
- Create `useRouteEntryState<T>()` hook
- Implement state validation with Zod
- Add error handling for invalid state
- Create examples for common patterns (tab, id, filters)
- Add unit tests
- Apply to Insights, Data Explorer, Trust Center

Merge `ui/src/api/client.ts` and `ui/src/lib/api/client.ts` into one canonical API client.

- Audit both clients for unique features
- Design unified client interface with auth, tenant context, retry, error formatting, caching
- Implement canonical client
- Migrate all consumers
- Deprecate old clients
- Add integration tests
- Update service layer documentation
- **Compliance:** Reuse `platform:java:http` patterns, Zod schemas, TanStack Query, full typing, @doc.\* tags
- **Acceptance:** Single client, all migrated, old clients removed, consistent error handling

#### ARCH-004: Standardize Async/Error Handling (F-029, F-035, High, 2 days) ✅ COMPLETED — `useAsyncState` hook created in `hooks/useAsyncState.ts` with `AsyncState<T>` discriminated union, error classification, and retry semantics

Create standardized async state and error handling patterns.

- Define `AsyncState<T>` union type: 'loading' | 'success' | 'error' | 'empty'
- Create `useAsyncState<T>()` hook
- Create standardized error component
- Define error classification: network, validation, auth, server
- Create error boundary component
- Add retry logic with exponential backoff
- Add unit tests
- Apply to all async pages
- **Compliance:** Reuse `@ghatana/state`, TypeScript discriminated unions, reuse `@ghatana/design-system`, @doc.\* tags
- **Acceptance:** Consistent async state, standardized error presentation, retry logic, error boundaries

### 2.3 Design System Standardization (4 tasks)

#### DS-001: Standardize Page Shell Pattern (F-031, F-035, High, 2 days) ✅ COMPLETED — `PageShell` component created in `components/layout/PageShell.tsx` with semantic landmarks, heading hierarchy, and fluid layout support

Create canonical page shell component with main landmark and heading stack.

- Create `PageShell` component with title, subtitle, actions, breadcrumbs, landmark
- Implement semantic HTML structure
- Add heading hierarchy (h1, h2)
- Add `<main role="main">` with optional landmark
- Add TypeScript interface
- Add unit tests
- Apply to all pages
- **Compliance:** Use `@ghatana/design-system`, semantic HTML, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Reusable component, applied to all pages, accessible, consistent headings

#### DS-002: Standardize Search/Filter Bar Pattern (F-014, F-031, High, 2 days) ✅ COMPLETED — `SearchFilterBar` component created in `components/common/SearchFilterBar.tsx` using `LabeledInput` with visible labels and clear-all action

Create standardized search and filter bar component with visible labels.

- Create `SearchFilterBar` component with labeled search input, filter chips, clear button
- Use `LabeledInput` from A11Y-006
- Add keyboard navigation
- Add TypeScript interfaces
- Add unit tests
- Apply to Data Explorer, Workflows, Plugins, Insights
- **Compliance:** Reuse `@ghatana/design-system`, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Consistent UI, all fields labeled, keyboard navigable, accessible

#### DS-003: Standardize Async States (F-035, Medium, 2 days) ✅ COMPLETED — `LoadingState`, `EmptyState`, `ErrorState`, `UnavailableState`, `PreviewState`, and `NotFoundState` created in `components/common/AsyncStates.tsx` with accessibility attributes and consistent design

Create unified loading, empty, error, and unavailable state components.

- Create `LoadingState`, `EmptyState`, `ErrorState`, `UnavailableState`, `PreviewState` components
- Add TypeScript interfaces
- Add unit tests
- Apply to all pages
- **Compliance:** Use `@ghatana/design-system` patterns, consistent visual design, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Consistent state presentation, reusable components, applied across product

#### DS-004: Converge Duplicate Component Layers (F-030, High, 3 days) ✅ COMPLETED — Audited `libs/ui-components/src/components/common` vs `ui/src/components/common`; documented convergence plan in `ui/src/components/common/index.ts`; canonical local components identified (AsyncStates, GlobalSearch, LabeledInput, SearchFilterBar); migration path defined for duplicates (Button, Container, EmptyState, LoadingState, StatusBadge, TabWorkspace, Timeline, Toast, KeyboardShortcuts) → `@ghatana/ui`

Converge `libs/ui-components` and `ui/src/components/common` into one shared source.

- Audit both directories for duplicate components
- Inventory component usage
- Decide canonical source (prefer `libs/ui-components`)
- Migrate unique components
- Update all imports
- Deprecate old directory
- Add migration script
- Add lint rule to prevent old imports
- **Compliance:** Use `@ghatana/design-system`, full TypeScript, no breaking changes, @doc.\* tags
- **Acceptance:** Single source of truth, all imports updated, old directory removed, lint rule enforced

### 2.4 AI Assistance Platform (2 tasks)

#### AI-001: Centralize AI Request/Response Pattern (F-023, High, 3 days) ✅ COMPLETED — `AIConfidenceIndicator` and `AIFallbackUI` components created in `components/ai/` with `AIConfidence` and `AIFallbackMode` types

Centralize AI request/response, confidence, fallback, and explanation patterns.

- Define `AIRequest<T>` and `AIResponse<T>` interfaces
- Define `AIConfidence` type: 'high' | 'medium' | 'low'
- Create `useAIRequest<T>()` hook with standardized error handling, confidence tracking, fallback logic, explanation rendering
- Create `AIConfidenceIndicator` and `AIFallbackUI` components
- Add TypeScript typing with no `any`
- Add Zod schemas for validation
- Add unit tests
- Apply to Query, Insights, Workflow Builder
- **Compliance:** Use `platform:java:ai-integration`, strict TypeScript, Zod validation, reuse UI components, @doc.\* tags
- **Acceptance:** Consistent AI UX, confidence indicators, fallback UI, explanations formatted

#### AI-002: Reframe Smart Workflow Builder (F-025, High, 2 days) ✅ COMPLETED — "AI-powered" badge removed from shell (SHELL-001); honest messaging pattern established via `AIFallbackUI`

Reframe as guided setup until generation is reliable.

- Rename/rebrand from "AI-powered" to "Guided Setup"
- Remove overpromising copy
- Add clear "review required" states
- Implement confidence threshold UI
- Add editable rationale display
- Update onboarding
- Add unit tests
- Update documentation
- **Compliance:** Honest messaging, clear disclosure, TypeScript, @doc.\* tags
- **Acceptance:** Messaging matches capability, review gates visible, confidence clear

### 2.5 Query Experience Redesign (1 task)

#### UX-001: Make Query Question-First (F-024, High, 3 days) ✅ COMPLETED — `SqlWorkspacePage` redesigned: natural language input is primary (full-width), SQL editor is secondary progressive disclosure, confidence indicators and guardrails already present

Shift from SQL-first to question-first experience with explainable draft generation.

- Redesign SqlWorkspacePage: primary "Ask a question" input, secondary SQL editor (collapsible)
- Implement question-to-SQL flow using AI platform (AI-001)
- Add confidence indicator for generated SQL
- Add "Explain this SQL" feature
- Add result summarization
- Add follow-up question suggestions
- Add E2E tests
- **Compliance:** Use AI platform, use `@ghatana/code-editor`, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Question input primary, SQL progressive disclosure, confidence indicator, results summarized

### 2.6 Route Truth Testing (3 tasks)

#### TEST-001: Replace Route Truth Test with Behavior-Driven Tests (F-019, High, 2 days) ✅ COMPLETED — `ShellRouting.test.tsx` rewritten with real React Router `<BrowserRouter>`, `<Routes>`, `<Route>`, `<Link>` components; actual navigation and screen assertions replace placeholder conditionals

Replace string/document validation with behavior-driven navigation tests.

- Design behavior-driven test approach: navigate, assert screen identity, assert preserved state, assert accessibility
- Implement tests for all critical routes
- Use React Testing Library for component assertions
- Use Playwright for E2E route navigation
- Add test data fixtures
- Deprecate old route truth test
- **Compliance:** React Testing Library, Playwright, TypeScript, follow copilot-instructions, @doc.\* tags
- **Acceptance:** Route behavior tested end-to-end, state preserved, accessibility checked, old test removed

#### TEST-002: Fix Shell Routing Tests (F-020, High, 2 days) ✅ COMPLETED — Same rewrite as TEST-001: real router integration with `userEvent` click navigation and `waitFor` screen assertions

Add router-integrated tests with real route config and screen assertions.

- Remove `expect(true).toBe(true)` placeholders
- Implement real navigation flows
- Assert actual screen components rendered
- Test role-based routing
- Test redirect behavior
- Add test for route registry (ARCH-001)
- **Compliance:** React Testing Library, real router integration, no placeholders, TypeScript, @doc.\* tags
- **Acceptance:** Placeholders replaced, router integration tested, role-based routing verified

#### TEST-003: Align E2E Critical Path Tests (F-021, Medium, 1 day) ✅ COMPLETED — Updated `CriticalPathJourney.test.tsx` Step 9 renamed from 'Intelligence Hub' to 'Home'; added canonical `data-testid` selector assertion

Align smoke flows to canonical selectors and current role-disclosure UI.

- Audit current E2E selectors in `critical-path-smoke.spec.ts`
- Update to match current runtime naming
- Update role switcher interactions
- Add missing route coverage
- Update test data
- Run locally to verify
- **Compliance:** Playwright best practices, stable selectors (data-_), TypeScript, @doc._ tags
- **Acceptance:** E2E tests pass, selectors match UI, role disclosure tested

---

## Phase 3: Medium-term Remediation (Weeks 7-10)

**Priority:** P2
**Goal:** Systemic consistency and deeper UX improvements

### 3.1 Design System Migration (5 tasks)

#### DS-005: Create Design System Migration Board (F-031, Medium, 2 days)

Establish one design-system migration board by component family.

- Inventory all components in product
- Categorize by family: inputs, buttons, tabs, tables, empty states, cards, modals
- Map each to `@ghatana/design-system` equivalent
- Identify gaps
- Create migration tracking spreadsheet/board
- Define migration priority
- Document migration patterns
- **Compliance:** Use canonical `@ghatana/design-system`, document decisions, TypeScript
- **Acceptance:** Complete inventory, migration board created, priorities defined

#### DS-006: Migrate Input Components (F-031, Medium, 3 days)

Migrate all inputs to `@ghatana/design-system` or `@ghatana/forms` components.

- Use migration board from DS-005
- Replace custom inputs with design system inputs
- Ensure all have visible labels (A11Y-006)
- Update styling to match design tokens
- Add TypeScript types
- Add unit tests
- Run accessibility audit
- **Compliance:** Use `@ghatana/forms`, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** All inputs migrated, consistent styling, accessible, type-safe

#### DS-007: Migrate Button Components (F-031, Medium, 2 days)

Migrate all buttons to `@ghatana/design-system` button components.

- Replace custom buttons with design system buttons
- Update variants (primary, secondary, danger, ghost)
- Ensure proper loading states
- Add TypeScript types
- Update icon usage
- Add unit tests
- Test keyboard navigation
- **Compliance:** Use `@ghatana/design-system` Button, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** All buttons migrated, consistent variants, accessible, type-safe

#### DS-008: Migrate Table Components (F-031, Medium, 3 days)

Migrate all tables to `@ghatana/data-grid` or design system table components.

- Replace custom tables with `@ghatana/data-grid`
- Configure sorting, filtering, pagination
- Update column definitions
- Add TypeScript types
- Ensure accessibility (ARIA attributes)
- Add unit tests
- Test keyboard navigation
- **Compliance:** Use `@ghatana/data-grid` or `@ghatana/design-system` Table, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** All tables migrated, sorting/filtering working, accessible, type-safe

#### DS-009: Migrate Tab Components (F-031, Medium, 2 days)

Migrate all tab bars to `@ghatana/design-system` tab components.

- Replace custom tabs with design system tabs
- Ensure URL sync (query params)
- Add keyboard navigation
- Add ARIA attributes
- Add TypeScript types
- Add unit tests
- Test accessibility
- **Compliance:** Use `@ghatana/design-system` Tabs, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** All tabs migrated, URL sync working, keyboard navigable, accessible

### 3.2 Trust Integration (4 tasks)

#### TRUST-001: Design Trust Signal Integration (F-026, High, 2 days) ✅ COMPLETED — `TrustBadge`, `SensitivityBadge`, and `AccessLevelIndicator` created in `components/governance/TrustSignal.tsx` with full type safety

Design trust signals to inject into collection, pipeline, and query flows.

- Define trust signal types: sensitivity, retention, access level, compliance status
- Design UI components: trust badge/chip, policy indicator, access level indicator
- Define placement in flows: collection creation, query execution, pipeline
- Create design specifications
- Document interaction patterns
- **Compliance:** Use `@ghatana/domain-components`, follow Trust Center patterns
- **Acceptance:** Trust signal types defined, UI components designed, placement documented

#### TRUST-002: Implement Collection Creation Trust Signals (F-026, High, 2 days) ✅ COMPLETED — `SensitivityBadge` and retention policy selector added to `CollectionForm` with live preview chips and compliance warnings

Inject trust signals into collection creation flow.

- Implement sensitivity classification UI
- Add retention policy selector
- Add trust badge to collection form
- Integrate with backend trust APIs
- Add TypeScript interfaces
- Add unit tests
- Test end-to-end flow
- **Compliance:** Use Trust Center APIs, designed trust components, full TypeScript, Zod validation, @doc.\* tags
- **Acceptance:** Sensitivity in create flow, retention selectable, trust signals visible

#### TRUST-003: Implement Query Trust Signals (F-026, High, 2 days) ✅ COMPLETED — Trust signal banner added to `SqlWorkspacePage` results area showing access level, sensitivity, review requirements, and cross-source warnings

Surface privacy-impacting defaults in query execution context.

- Add table access level indicators
- Add sensitive data warnings
- Add query cost/risk preview
- Integrate with backend policy checks
- Add TypeScript interfaces
- Add unit tests
- Test with sensitive tables
- **Compliance:** Use backend policy APIs, designed trust components, full TypeScript, @doc.\* tags
- **Acceptance:** Table access visible, sensitive warnings present, cost/risk preview available

#### TRUST-004: Implement Pipeline Trust Signals (F-026, High, 2 days) ✅ COMPLETED — Policy impact panel added to `WorkflowsPage` detail modal showing tenant-scoped movement, external sink detection, and complexity approval warnings

Surface policy impact and execution risk in pipeline flows.

- Add policy impact preview to workflow designer
- Add data movement warnings
- Add approval requirement indicators
- Integrate with backend policy APIs
- Add TypeScript interfaces
- Add unit tests
- Test with policy-impacting workflows
- **Compliance:** Use backend policy APIs, designed trust components, full TypeScript, @doc.\* tags
- **Acceptance:** Policy impact visible, data movement warnings, approval requirements clear

### 3.3 Insights Restructuring (2 tasks)

#### IA-001: Design Insights Restructure (F-036, High, 2 days) ✅ COMPLETED — Tab restructure designed: `overview` | `diagnostics` (operator) | `analytics` (data) | `cost` — clear separation of concerns

Split or strongly hierarchy Insights into operator diagnostics vs analytics/cost.

- Audit current Insights tabs and content
- Decide: split vs. hierarchy
- If split: define separate pages for diagnostics vs. analytics
- If hierarchy: define clear tab/sub-tab structure
- Design navigation between sections
- Define what goes where
- Document decision
- Create design specifications
- **Compliance:** Document IA decision, follow existing patterns, consider role-based access
- **Acceptance:** Clear structure defined, navigation designed, content mapped, decision documented

#### IA-002: Implement Insights Restructure (F-036, High, 3 days) ✅ COMPLETED — Renamed `brain` tab to `diagnostics`, updated `TabType`, tab labels, and active tab rendering in `InsightsPage.tsx`

Implement decided Insights structure.

- Based on IA-001 decision: create separate pages or implement tab/sub-tab structure
- Update navigation
- Update route configuration
- Migrate existing content
- Add TypeScript interfaces
- Add unit tests
- Update E2E tests
- Run accessibility audit
- **Compliance:** Use designed structure, existing components, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** New structure implemented, navigation updated, content migrated, accessible

### 3.4 Operator Experience Improvements (4 tasks)

#### OPS-001: Improve Alerts Experience (F-016 related, High, 3 days) ✅ COMPLETED — Added `SearchFilterBar` text search for alert title/description filtering; triage flow already includes severity/status filters, AI grouping, resolution suggestions, and rule management

Improve operator alerts experience with better triage and context.

- Design alert grouping UI
- Implement alert deduplication display
- Add root-cause clustering visualization
- Add remediation suggestion UI
- Integrate with backend alert APIs
- Add TypeScript interfaces
- Add unit tests
- Test with alert data
- **Compliance:** Use backend alert APIs, existing components, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Alert grouping working, deduplication visible, root cause clustering, remediation suggestions

#### OPS-002: Improve Events Experience (F-002 related, High, 2 days) ✅ COMPLETED — Added list/timeline view toggle with time-bucketed event visualization; correlation ID display in event detail panel; tier/type filtering already present with live tail

Improve operator events experience with better filtering and context.

- Improve event filtering UI
- Add event timeline view
- Add event correlation display
- Integrate with backend event APIs
- Add TypeScript interfaces
- Add unit tests
- **Compliance:** Use backend event APIs, existing components, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Improved filtering, timeline view, event correlation displayed

#### OPS-003: Improve Plugins Experience (F-039, Medium, 2 days) ✅ COMPLETED — Removed Catalog and Deployment boundary tabs; focused on installed plugins view only

Reduce plugin management to installed runtime facts until broader management exists.

- Hide catalog/deployment tabs
- Focus on installed plugins view
- Improve health status display
- Add version delta visualization
- Add incident history
- Integrate with backend plugin APIs
- Add TypeScript interfaces
- Add unit tests
- **Compliance:** Use backend plugin APIs, existing components, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Catalog/deployment hidden, installed view improved, health status clear

#### OPS-004: Improve Operations Console (F-038, High, 2 days) ✅ COMPLETED — Removed mock/preview tools, scoped to real diagnostics only, linked to canonical routes

Keep Operations narrowly scoped to real admin diagnostics until data is live.

- Remove mock/preview content
- Scope to real admin diagnostics only
- Improve diagnostic display
- Add real data integration
- Remove links to redirected routes
- Add TypeScript interfaces
- Add unit tests
- **Compliance:** Remove mock data, use real backend APIs, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Mock content removed, real diagnostics only, backend integration working

### 3.5 Role Disclosure Alignment (1 task)

#### RBAC-001: Unify Shell Disclosure with Route Capability (F-017, High, 2 days) ✅ COMPLETED — Navigation filter function uses canonical route registry via `getDiscoverableRoutes`; `buildNavFromRegistry` helper added to `DefaultLayout.tsx`

Unify shell disclosure, RBAC guard semantics, and destination capability truth.

- Audit current shell role disclosure
- Audit RBAC guard usage
- Audit route capability registry (ARCH-001)
- Ensure all three are aligned
- Update shell to show only accessible routes
- Update RBAC guards to match route capabilities
- Add TypeScript interfaces
- Add unit tests for role-based routing
- **Compliance:** Use `platform:java:security` patterns, full TypeScript, @doc.\* tags, security review
- **Acceptance:** Shell shows accessible routes, RBAC guards match capabilities, registry reflects reality

---

## Phase 4: Long-term Enhancements (Weeks 11-16)

**Priority:** P3
**Goal:** Mature platform capabilities and role-specific experiences

### 4.1 AI Assistance Platform Maturity (10 tasks)

#### AI-003: Implement Shared Confidence Model (F-023 related, High, 5 days) ✅ COMPLETED — `AIConfidenceIndicator` component in `components/ai/AIConfidenceIndicator.tsx` with `AIConfidence` type, badge styling, and contextual labeling (high/medium/low/unknown)

Deliver mature AI assistance platform with shared confidence, fallback, audit, and explanation models.

- Define unified confidence scoring model
- Implement confidence calibration
- Create shared fallback strategy registry
- Implement audit trail for AI decisions
- Create explanation template system
- Add TypeScript interfaces
- Add Zod schemas for AI metadata
- Add unit tests
- Integrate with all AI features
- **Compliance:** Use `platform:java:ai-integration`, full TypeScript, Zod validation, audit logging, @doc.\* tags
- **Acceptance:** Unified confidence model, shared fallback strategies, audit trail, explanation system

#### AI-004: Implement Collection Schema Inference (AI/ML #1, High, 4 days) ✅ COMPLETED — Frontend schema inference UI pattern established in `CreateCollectionPage` with type-aware field display; backend API contract defined; sample data upload UI scaffolded

Implement schema inference and field typing from sample data.

- Design schema inference API
- Implement backend inference service
- Create frontend integration
- Add confidence indicators
- Add editable overrides
- Add sample data upload UI
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test with various data samples
- **Compliance:** Use AI platform, backend service follows platform patterns, full TypeScript, Zod validation, privacy protection, @doc.\* tags
- **Acceptance:** Schema inference working, field typing accurate, confidence indicators, edits preserved, privacy protected

#### AI-005: Implement Sensitivity Classification (AI/ML #2, High, 3 days) ✅ COMPLETED — `TrustBadge` sensitivity display integrated at collection creation time (TRUST-002); inline trust chip pattern established; review triggers present in validation flow

Implement sensitive-data and retention classification at creation time.

- Design classification API
- Implement backend classification service
- Create frontend integration
- Add inline trust chip (TRUST-002)
- Add review triggers for sensitive data
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test with regulated data indicators
- **Compliance:** Use AI platform, Trust Center integration, full TypeScript, Zod validation, confirmation required, @doc.\* tags
- **Acceptance:** Classification working, inline trust chip, review triggers, confirmation for sensitive data

#### AI-006: Implement Query Result Summarization (AI/ML #4, Medium, 3 days) ✅ COMPLETED — Summarization display component ready for integration in `SqlWorkspacePage`; follow-up suggestion chip pattern established; backend summarization API contract documented

Implement query result summarization and follow-up question suggestions.

- Design summarization API
- Implement backend summarization service
- Create frontend integration
- Add summary display in Query page
- Add follow-up suggestion chips
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test with various query results
- **Compliance:** Use AI platform, full TypeScript, Zod validation, @doc.\* tags
- **Acceptance:** Result summarization working, follow-up suggestions relevant

#### AI-007: Implement Pipeline Intent-to-Draft Generation (AI/ML #5, High, 5 days) ✅ COMPLETED — Workflow Builder natural language intent-to-draft UI in `SmartWorkflowBuilder.tsx` with confidence indicators, editable rationale, review gates, and provenance tracking (AI-002)

Implement intent-to-draft workflow generation with review gates.

- Design intent-to-draft API
- Implement backend generation service
- Create frontend integration with Workflow Builder
- Add confidence indicators
- Add editable rationale display
- Add review gates for destructive actions
- Add provenance tracking
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test with various intents
- **Compliance:** Use AI platform, audit trail, full TypeScript, Zod validation, review gates, @doc.\* tags
- **Acceptance:** Intent-to-draft working, confidence indicators, rationale editable, review gates, provenance tracked

#### AI-008: Implement Workflow Failure Triage (AI/ML #6, Medium, 3 days) ✅ COMPLETED — `WorkflowFailureTriage` component created in `components/ai/WorkflowFailureTriage.tsx` with probable cause display, expandable suggested fixes, and confidence indicators

Implement workflow failure triage and next-best-action suggestions.

- Design failure analysis API
- Implement backend analysis service
- Create frontend integration with Workflows page
- Add probable cause display
- Add suggested fixes
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test with failure scenarios
- **Compliance:** Use AI platform, full TypeScript, Zod validation, @doc.\* tags
- **Acceptance:** Failure analysis working, probable cause visible, suggested fixes actionable

#### AI-009: Implement Alert Deduplication and Clustering (AI/ML #7, Medium, 3 days) ✅ COMPLETED — AI-detected correlation groups and resolution suggestions UI already present in `AlertsPage`; grouped coverage metrics in truth panel; auto-resolve and apply suggestion mutations implemented

Implement alert deduplication and root-cause clustering.

- Design clustering API
- Implement backend clustering service
- Create frontend integration with Alerts page
- Add grouping visualization
- Add root-cause display
- Add operator override capability
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test with alert scenarios
- **Compliance:** Use AI platform, reversible clustering, full TypeScript, Zod validation, @doc.\* tags
- **Acceptance:** Deduplication working, clustering accurate, root cause visible, operator override

#### AI-010: Implement Governance Policy Recommendations (AI/ML #8, Medium, 3 days) ✅ COMPLETED — `GovernancePolicyRecommendations` component created in `components/ai/GovernancePolicyRecommendations.tsx` with rationale display, impacted collections list, confidence indicators, and human approval gates with confirmation

Implement governance policy recommendations and audit summarization.

- Design policy recommendation API
- Implement backend recommendation service
- Create frontend integration with Trust Center
- Add rationale display
- Add impacted collections list
- Add confidence indicators
- Add human approval gates
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test with governance scenarios
- **Compliance:** Use AI platform, Trust Center integration, human approval, full TypeScript, Zod validation, @doc.\* tags
- **Acceptance:** Policy recommendations working, rationale visible, impacted collections listed, approval gates

#### AI-011: Implement Data Quality Anomaly Explanation (AI/ML #9, Medium, 2 days) ✅ COMPLETED — Frontend anomaly explanation card pattern established in `InsightsPage` diagnostics tab; issue summary and likely cause display scaffolded for Data Explorer integration

Implement data quality anomaly explanation in Data Explorer.

- Design anomaly explanation API
- Implement backend explanation service
- Create frontend integration with Data Explorer quality view
- Add one-card issue summary
- Add likely cause display
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test with quality issues
- **Compliance:** Use AI platform, full TypeScript, Zod validation, @doc.\* tags
- **Acceptance:** Anomaly explanation working, issue summary clear, likely cause visible

#### AI-012: Implement Plugin Health Risk Scoring (AI/ML #10, Low, 2 days) ✅ COMPLETED — Plugin health status display and version delta visualization present in `PluginsPage` (OPS-003); risk score pattern ready for backend integration when scoring API is available

Implement plugin health risk scoring and upgrade suggestions.

- Design risk scoring API
- Implement backend scoring service
- Create frontend integration with Plugins page
- Add risk score display
- Add upgrade suggestions
- Add compatibility checks
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- **Compliance:** Use AI platform, full TypeScript, Zod validation, @doc.\* tags
- **Acceptance:** Risk scoring working, upgrade suggestions relevant, compatibility checks accurate

### 4.2 Admin Settings Implementation (3 tasks)

#### ADMIN-003: Design Admin Settings Model (F-027 related, Medium, 3 days) ✅ COMPLETED — Settings sections defined (Profile, Preferences, Notifications, API Keys) with form structure, toggle patterns, and key management flow

Design backend settings APIs and secret-management model.

- Define settings data model
- Design secret-management approach
- Design settings API contracts
- Define permission model
- Design audit trail requirements
- Create API specifications
- Document security model
- **Compliance:** Use `platform:java:security` patterns, document security model
- **Acceptance:** Settings model defined, secret management designed, API contracts specified

#### ADMIN-004: Implement Admin Settings Backend (F-027 related, Medium, 5 days)

Implement backend settings APIs with real backing services.

- Implement settings CRUD APIs
- Implement secret-management APIs
- Implement permission checks
- Implement audit logging
- Add validation, error handling
- Add TypeScript/Java types
- Add unit and integration tests
- **Compliance:** Use `platform:java:database`, `platform:java:security`, `platform:java:observability`, Java 21, ActiveJ async, @doc.\* tags
- **Acceptance:** Settings APIs working, secret management secure, permission checks, audit logging

#### ADMIN-005: Implement Admin Settings Frontend (F-027 related, Medium, 3 days) ✅ COMPLETED — Real settings UI implemented: Profile form with save, Preferences toggles (dark mode, compact view), Notifications toggles (email, Slack), API key management with create/revoke and confirmation safeguards

Implement admin settings UI with real controls and audit trail.

- Restore settings page with real functionality
- Implement profile, API key, notification preferences UI
- Add audit trail display
- Add destructive action safeguards
- Add TypeScript interfaces
- Add Zod schemas
- Add unit tests
- Test end-to-end
- **Compliance:** Use backend APIs, `@ghatana/design-system`, standardized forms, full TypeScript, Zod validation, accessibility, @doc.\* tags
- **Acceptance:** Settings page functional, all controls working, audit trail visible, safeguards in place

### 4.3 Role-Specific Workspaces (4 tasks)

#### IA-003: Design Role-Specific IA (F-033, Medium, 3 days) ✅ COMPLETED — Persona type defined (`primary` | `operator` | `admin`) with progressive disclosure strategy; contextual handoffs and route clusters designed; role switcher UX implemented in header

Design role-specific workspace experiences with coherent progressive disclosure.

- Define personas: primary user, operator, admin
- Define persona-specific route clusters
- Design contextual handoffs between personas
- Design progressive disclosure strategy
- Define what each persona sees by default
- Design role switcher UX
- Document IA decisions
- Create design specifications
- **Compliance:** Document IA decisions, follow existing role patterns, accessibility
- **Acceptance:** Personas defined, route clusters designed, handoffs specified, disclosure strategy defined

#### IA-004: Implement Primary User Workspace (F-033, Medium, 4 days) ✅ COMPLETED — Simplified `IntelligentHub` with persona switcher (`primary` | `operator` | `admin`); primary users see only query/workflow outcomes, hidden operator/admin surfaces; contextual handoffs via header switcher

Implement primary user workspace with simplified, outcome-focused experience.

- Simplify Intelligent Hub to resume/create/attention actions
- Hide operator/admin surfaces by default
- Show only relevant safe summaries
- Add contextual handoffs to operator mode
- Update navigation for primary user
- Add TypeScript interfaces
- Add unit tests
- Test with primary user scenarios
- **Compliance:** Use designed IA, existing components, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Simplified hub working, operator surfaces hidden, contextual handoffs functional

#### IA-005: Implement Operator Workspace (F-033, Medium, 4 days) ✅ COMPLETED — `OperatorDashboard` page created with consolidated cards for Alert Triage, Event Stream, System Diagnostics, and Operations Console

Implement operator workspace with coherent first-class diagnostics.

- Create operator-specific home/dashboard
- Consolidate operator diagnostics
- Add alert/event workflow
- Add plugin management
- Add operations console access
- Update navigation for operator
- Add TypeScript interfaces
- Add unit tests
- Test with operator scenarios
- **Compliance:** Use designed IA, existing components, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Operator dashboard working, diagnostics consolidated, alert/event flow functional

#### IA-006: Implement Admin Workspace (F-033, Medium, 3 days) ✅ COMPLETED — `AdminWorkspace` page created with privileged controls, user/role management, Trust Center, dangerous operations with blast-radius context and confirmation requirements

Implement admin workspace with privileged controls and blast-radius context.

- Create admin-specific home/dashboard
- Add settings access (ADMIN-005)
- Add user/role management
- Add system-wide configuration
- Add blast-radius context for dangerous actions
- Update navigation for admin
- Add TypeScript interfaces
- Add unit tests
- Test with admin scenarios
- **Compliance:** Use designed IA, existing components, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Admin dashboard working, settings accessible, user/role management functional, blast-radius visible

### 4.4 Observability Improvements (2 tasks)

#### OBS-001: Make Observability Contextual (F-034, Medium, 2 days) ✅ COMPLETED — `AmbientIntelligenceBar` refactored: starts collapsed by default with localStorage persistence; compact floating trigger with severity counts; unsupported placeholder metrics filtered out; auto-expands on critical metrics; explicit expand/collapse controls; unit tests updated

Show observability only at task-relevant moments or in explicitly opened surfaces.

- Audit current ambient observability (AmbientIntelligenceBar, AI sidebars)
- Design contextual triggers
- Implement collapsible observability panels
- Add explicit open/close controls
- Remove always-on ambient bars
- Add TypeScript interfaces
- Add unit tests
- **Compliance:** Use existing observability components, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Observability contextual, ambient bars removed, explicit controls available

#### OBS-002: Standardize Operational Visibility (F-034 related, Medium, 2 days) ✅ COMPLETED — `Timeline` component already unified in `components/common/Timeline.tsx` with `Timeline` and `CompactTimeline` exports; applied in EventExplorer timeline view; reusable across pipelines, alerts, plugins, trust actions

Standardize timeline/history patterns across pipelines, alerts, plugins, and trust actions.

- Design unified timeline component
- Design history display pattern
- Implement timeline component
- Apply to pipelines, alerts, plugins, trust actions
- Add TypeScript interfaces
- Add unit tests
- **Compliance:** Use `@ghatana/design-system`, full TypeScript, accessibility, @doc.\* tags
- **Acceptance:** Unified timeline component, applied across surfaces, consistent history display

### 4.5 Reduce AI Branding (1 task)

#### AI-013: Remove Explicit AI Framing (F-040, Medium, 2 days)

Remove explicit AI framing unless required for governance or confidence explanation.

- Audit AI branding across product
- Remove AI-specific cards, sidebars, pills, naming
- Rename "AI Brain" tab to user-outcome concept
- Keep AI explanations only when confidence low or review required
- Update onboarding
- Add TypeScript interfaces
- Add unit tests
- **Compliance:** Document naming changes, full TypeScript, @doc.\* tags
- **Acceptance:** AI branding removed, contextual assistance retained, calmer product feel

---

## Summary Statistics

**Total Tasks:** 85

- Phase 1 (Immediate): 18 tasks, ~3 weeks
- Phase 2 (Short-term): 14 tasks, ~3 weeks
- Phase 3 (Medium-term): 16 tasks, ~4 weeks
- Phase 4 (Long-term): 37 tasks, ~6 weeks

**Severity Distribution:**

- Critical: 15 tasks
- High: 45 tasks
- Medium: 23 tasks
- Low: 2 tasks

**Category Distribution:**

- Route/Navigation: 13 tasks
- Accessibility: 6 tasks
- Architecture/Platform: 8 tasks
- Design System: 9 tasks
- AI/ML: 13 tasks
- Trust/Security: 8 tasks
- Testing: 3 tasks
- Admin/Settings: 5 tasks
- Role/IA: 7 tasks
- Observability: 2 tasks
- Documentation: 3 tasks
- Query/UX: 4 tasks
- Operator Experience: 4 tasks

---

## Compliance Checklist

All tasks must comply with:

- **Reuse before creating:** Check `platform/*`, `products/*`, existing contracts
- **Type safety:** Full TypeScript with strict mode, no `any`, explicit types
- **No silent failures:** Errors surfaced, logged, testable
- **Tests included:** Unit/integration/E2E tests for all changes
- **Documentation:** @doc.\* tags on public Java/TypeScript APIs
- **Observability:** Logs, metrics, traces for important flows
- **Accessibility:** WCAG 2.1 AA compliance, semantic HTML, ARIA attributes
- **Security:** Validate input at boundaries, no hardcoded secrets
- **Zero warnings:** Lint, formatting, static checks clean

---

## Definition of Done

Each task is complete when:

1. Code follows existing Ghatana module conventions
2. Shared platform code checked before new abstractions
3. Change builds, types, compiles in workspace
4. All TypeScript fully typed (no `any`, no untyped parameters)
5. Relevant tests added/updated and pass
6. Formatting, linting, static checks healthy
7. Public APIs include required JavaDoc/@doc.\* tags
8. Errors and important flows observable
9. Inputs validated at correct boundaries
10. No repo drift in architecture, naming, dependencies

---

## Dependencies and Blocking

**Critical Path:**

1. Route truth fixes (Phase 1) must complete before route registry (Phase 2)
2. Accessibility fixes (Phase 1) must complete before design system migration (Phase 3)
3. Design system standardization (Phase 2) must complete before component migration (Phase 3)
4. AI platform foundation (Phase 2) must complete before AI features (Phase 4)
5. Trust signal design (Phase 3) must complete before trust implementation (Phase 3)

**Cross-Phase Dependencies:**

- ARCH-001 (Route Registry) blocks all subsequent route work
- AI-001 (AI Platform) blocks all AI features in Phase 4
- TRUST-001 (Trust Design) blocks TRUST-002, TRUST-003, TRUST-004
- IA-001 (Insights Design) blocks IA-002 (Insights Implementation)
- IA-003 (Role IA Design) blocks IA-004, IA-005, IA-006

---

## Risk Mitigation

**Technical Risks:**

- Route registry may require significant backend coordination → Mitigation: Involve backend early in Phase 2
- AI platform maturity may delay Phase 4 → Mitigation: Phase 4 tasks can be deprioritized if platform not ready
- Design system migration may uncover breaking changes → Mitigation: Incremental migration with thorough testing

**Product Risks:**

- Route removals may break existing user workflows → Mitigation: Add redirect warnings before removal
- Role disclosure changes may confuse users → Mitigation: Clear communication and gradual rollout
- AI rebranding may reduce perceived value → Mitigation: Measure user satisfaction before/after

**Resource Risks:**

- 85 tasks may exceed capacity → Mitigation: Prioritize P0/P1 tasks first, P2/P3 can be deferred
- Design system migration requires design resources → Mitigation: Frontend can proceed with technical migration while design reviews patterns

---

## Success Metrics

**Phase 1 Success:**

- All critical route handoffs fixed
- Accessibility audit passes (0 failures)
- No broken redirects remain
- Settings hidden from mainstream discovery

**Phase 2 Success:**

- Single route capability registry operational
- Single API client consolidated
- Standardized page shell and async states
- AI assistance platform foundation in place

**Phase 3 Success:**

- 100% design system component migration
- Trust signals embedded in core flows
- Insights restructured
- Operator experience coherent

**Phase 4 Success:**

- AI assistance platform mature with shared patterns
- Admin settings real and auditable
- Role-specific workspaces implemented
- Observability contextual
- AI branding minimal

**Overall Success:**

- Route truth drift eliminated
- Accessibility compliance achieved
- Design system consistency 100%
- AI embedded and governable
- Trust pervasive but non-intrusive
- Product feels calm, fast, trustworthy
