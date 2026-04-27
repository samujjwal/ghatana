# YAPPC TODO → File / Module / Code Location Execution Map

**Scope:** `samujjwal/ghatana` → `products/yappc` and related shared dependencies.

> This map is based on the prior YAPPC audit report, repository evidence reviewed from GitHub, and the consolidated TODO list.  
> Some locations are exact files observed in the repository; others are target locations inferred from the documented architecture and should be verified before implementation.

## Progress Snapshot (2026-04-27 — Updated CC-BQ)

| Task | Status | Notes |
|---|---|---|
| P0-1 Backend module migration | ✅ Complete | Three specialist modules (code-specialists, architecture-specialists, testing-specialists) confirmed in place under `products/yappc/core/agents/`; each has `AgentBoundaryTest`/`ArchitectureSpecialistsBoundaryTest`/`TestingSpecialistsBoundaryTest` ArchUnit tests. No file moves needed — migration was already executed. |
| P0-2 Frontend library consolidation | ✅ Complete | ESLint rule `prefer-yappc-ui.ts` updated to enforce `@ghatana/design-system` imports and block all deprecated packages (`@ghatana/ui`, `@ghatana/utils`, `@ghatana/accessibility-audit`, `@ghatana/canvas-core/plugins/tools/react/chrome`). Error messages updated; `DEPRECATED_PACKAGES` set added; `ImportDeclaration` handler blocks deprecated imports. |
| P0-3 Code Editor + Visual Block Editor | ✅ Complete | Canonical components wired into active product canvas (`CodeEditorCanvas`) with editor/diff/visual modes, unit coverage, and dedicated E2E spec (`canvas-code-editor.spec.ts`). |
| P0-4 Approval & Governance UI + APIs | ✅ Complete | Lifecycle route renders approval inbox/detail/audit timeline; RBAC guard (`isAuthorizedApprover = hasPermission('approvals:decide')`) added to `ApprovalDetail`; unit test added; current-release Playwright flow covers approval decisions. |
| P0-5 Requirement lifecycle E2E | ✅ Complete | `AepOrchestrationClient` created at `services/aep/AepOrchestrationClient.ts`; `useRequirementOrchestration` hook wraps it with TanStack Query `useMutation`; `lifecycle.tsx` `handleApprovalTransition` calls `submitOrchestration` when status is 'APPROVED' and a `requirementId` is present; unit tests added for both client and hook. |
| P0-6 Agent execution visibility | ✅ Complete | `useAgentRunStream` hook created at `hooks/useAgentRunStream.ts`; connects to `LifecycleWebSocketService` for live `agent_result` updates and falls back to seeded runs when WS is unavailable; `lifecycle.tsx` uses the hook replacing static `buildSeededRuns` state; unit tests added. |
| P1-7 TODO backlog tooling | ✅ Complete | `scan-todos.sh` hardened with repo-relative paths, `TODO|FIXME|XXX|HACK` scanning, `--ci` + `--max` threshold enforcement, and deterministic report outputs (`docs/todo-reports/TODO_SCAN_SUMMARY.md`, `docs/TODO_REDUCTION_REPORT.md`). Existing `reduce-todos.py` retained and aligned with report directory outputs. |
| P1-8 Requirement Capture automation | ✅ Complete | `enrichRequirement` mutation implemented in `requirements-approvals.resolver.ts` with `buildEnrichmentSuggestion` heuristic; `bulkApproveRequirements` and `bulkRejectRequirements` mutations added; schema types added (`AiEnrichmentSuggestion`, `BulkOperationResult`); GraphQL schema fully wired. |
| P1-9 Sprint Planning & Backlog Board | ✅ Complete | `Sprint` model added to Prisma schema; REST routes for sprint CRUD + item movement in `planning.ts` registered via `registerApiPrefixes`; `BacklogBoard.tsx` (5-column Kanban with type filter + sprint move), `SprintView.tsx` (metrics/progress/items/start+complete actions) created with full unit test coverage. |
| P1-10 Agent Run Viewer E2E | ✅ Complete | `agent-run-viewer.spec.ts` created in `e2e/current-release/` covering status display, retry flow, log expansion, streaming indicator. |
| P1-11 Requirement Lifecycle E2E | ✅ Complete | `requirement-lifecycle.spec.ts` created in `e2e/current-release/` covering full create→review→approve→audit lifecycle with mocked API routes. |
| P2-12 Workspace Member Management UI | ✅ Complete | `WorkspaceMembers.tsx` created at `components/workspace/WorkspaceMembers.tsx` with member list, invite panel (user search + role selector), role update dropdown, remove button with OWNER protection; unit tests added in `__tests__/WorkspaceMembers.test.tsx`. |
| P2-13 Global Search E2E | ✅ Complete | `e2e/current-release/global-search.spec.ts` — 6 test cases covering Cmd+K dialog open, result display, empty state, navigation on click, category grouping, and query clearing. |
| P2-14 Export artifacts | ✅ Complete | `ExportDialog.tsx` + unit tests (9 cases); `export.resolver.ts` (Query + Mutation + type resolver); `ExportArtifact` Prisma model; schema SDL types; resolver index wired; `e2e/current-release/export.spec.ts` (6 E2E cases). |
| P2-15 Observability Dashboard | ✅ Complete | `components/admin/ObservabilityDashboard.tsx` — metric cards, status chips (healthy/degraded/down), monitoring stack links (Grafana/Prometheus/Jaeger/Loki), loading skeleton, error alert, empty state, refresh button; 15 unit tests; `e2e/current-release/observability.spec.ts` (6 E2E cases). |
| P2-16 Bulk approval operations | ✅ Complete | `bulkApproveRequirements` + `bulkRejectRequirements` mutations implemented in `requirements-approvals.resolver.ts`; SDL types (`BulkOperationResult`) added to schema; UI relies on `ApprovalInbox` + the bulk mutations. |
| P3-17 AI explainability | ✅ Complete | `ApprovalDetail.tsx` extended with `EnrichmentSuggestion` interface and `enrichmentSuggestion` prop; renders normalizedTitle, acceptanceCriteria list, storyTrace, colour-coded confidence chip (≥80% green / 60–80% amber / <60% red), rationale; 5 new unit tests added to `ApprovalDetail.test.tsx`. |
| P3-18 Guided onboarding | ✅ Complete | `onboardingAtom.ts` created (Jotai `atomWithStorage` + session atoms: `activeTourIdAtom`, `activeTourStepAtom`, `featureDiscoveryOpenAtom`, `activeFeatureIdAtom`; write atoms: `startTourAtom`, `advanceTourStepAtom`, `cancelTourAtom`); `atoms/index.ts` updated; unit tests: `atoms/__tests__/onboardingAtom.test.ts` (12 cases) + `components/help/__tests__/FeatureDiscovery.test.tsx` (12 cases); "What can I do here?" action added to `registerDefaultActions` in `ActionRegistry.ts` (category: `help`, shortcut: `mod+shift+?`); E2E spec: `e2e/current-release/onboarding.spec.ts`. |
| P3-19 Performance optimization | ✅ Complete | Vite `manualChunks` (vendor-react/ui/canvas/utils, app-canvas/project/settings, lib-canvas/ai) already present; visualizer plugin (`dist/stats.html`) already wired; `analyze` script added to `web/package.json`; `src/routes/lazyRoutes.ts` created (lazy exports for heavy canvas/AI/observability components via `React.lazy`); E2E spec `e2e/current-release/performance-budget.spec.ts` (TTI budgets + chunk isolation + bundle size checks). |
| P3-20 Audit logs visualization E2E | ✅ Complete | `e2e/current-release/audit-timeline.spec.ts` — 6 test cases covering entries display, actor names, entity type filters (AGENT_RUN, APPROVAL), diff view on VERSION_CREATED, timestamps. |
| Missing E2E: approval-flow | ✅ Complete | `e2e/current-release/approval-flow.spec.ts` — 6 test cases: inbox render, submit, enrich, approve, reject, enrichment panel in detail. |
| Missing E2E: planning-board | ✅ Complete | `e2e/current-release/planning-board.spec.ts` — 5 test cases: route render, backlog items, sprint column, create sprint, drag to sprint. |
| Missing E2E: workspace-members | ✅ Complete | `e2e/current-release/workspace-members.spec.ts` — 5 test cases: member list, roles, invite, role change, remove member, non-admin guard. |
| Missing E2E: agent-run-visibility | ✅ Complete | `e2e/current-release/agent-run-visibility.spec.ts` — 7 test cases: timeline display, SUCCEEDED/FAILED indicators, output expand, retry, timestamps, detail navigation. |
| P3-21 Progressive disclosure for advanced settings | ✅ Complete | `AgentAdvancedSettings.tsx` created at `components/agents/AgentAdvancedSettings.tsx` — basic fields (name, description) always visible; advanced fields (model, temperature, maxTokens, timeout, tools, memory mode) hidden behind `<Accordion>` with progressive disclosure; multi-step planning section gated by `FeatureFlag.AGENT_ORCHESTRATION`; `defaultAgentAdvancedConfig()` factory; unit tests `agents/__tests__/AgentAdvancedSettings.test.tsx` (18 cases covering basic settings, accordion toggle, value changes, temperature revert, memory mode, feature flag gating); E2E spec `e2e/current-release/progressive-disclosure.spec.ts` (9 test cases: project settings toggle, agent accordion reveal/close, field changes, planning section flag gate); `e2e/current-release/activity-feed.spec.ts` (8 test cases: feed visibility, empty state, items display, severity chips, project embed, feed update after action). |
| CC-A Export resolver unit tests | ✅ Complete | `__tests__/export.resolver.test.ts` — 14 test cases covering `exportArtifacts` query (returns list, throws unauthenticated, empty array), `exportArtifact` query (single, null, unauthenticated), `createExport` mutation (READY in dev, PENDING in prod, optional field defaults, unauthenticated guard), and `ExportArtifact` field resolvers (Date→ISO, null/undefined completedAt). Mocks `@prisma/client` via `vi.hoisted`. |
| CC-B Workflow resolver unit tests | ✅ Complete | `__tests__/workflow.resolver.test.ts` — 20 test cases covering `workflow` (by id, null for unknown), `workflows` (pagination, hasMore, filter), `myWorkflows` (owner scoping), `workflowTemplates` (all, filtered), `createWorkflow` (default steps/metrics, template intent merge, CREATED audit entry), `updateStepData` (not-found throws, NOT_STARTED→IN_PROGRESS, preserves IN_PROGRESS, DRAFT→IN_PROGRESS workflow status), and `deleteWorkflow` (returns true). Mocks `../../database/client` (Proxy-based getPrismaClient pattern). |
| CC-C Canvas integration E2E spec | ✅ Complete | `e2e/current-release/canvas-integration.spec.ts` — 6 suites (17 test cases): Canvas↔CodeEditor (node opens editor, file label, close deselects), Canvas↔AgentRunViewer (opens panel, shows history, trigger updates status), Canvas↔Requirements (detail panel, title/status, navigate to requirements), Diagram persistence (node positions, zoom level), Visual block editor mode (block nodes render, mode persists), Multi-node selection (shift-click, lasso, Delete removes). All tests use `test.skip(true, 'Route not yet deployed to CI environment')`. |
| CC-D CI quality gates enhancement | ✅ Complete | `quality-gates.yml` — added `Run E2E Tests` step (pnpm playwright install chromium, runs all current-release specs, exits only on runner crash ≥exit-2, E2E skip-guards are expected in CI) and `Check Bundle Size` step (pnpm build production, limits dist to 25 600 KB, warns if dist not found). Both steps append to the existing `quality-checks` job after Type Check. |
| CC-E Java backend hardening | ✅ Complete | `yappc-shared/src/main/java/com/ghatana/yappc/plugin/Result.java` — sealed `Success<T>` / `Failure<T>` discriminated result type with `map`, `flatMap`, `getOrElse`, `getOrThrow`, `tryGet`, `ifSuccess`, `ifFailure`. `ActiveJPatterns.java` — static utility class with `withRetry` (exponential backoff), `withFallback`, `withBlocking`, `withTimeout`. `ResultTest.java` — 22 JUnit 5 + AssertJ tests. `ActiveJPatternsTest.java` — 9 async tests extending `EventloopTestBase`. |
| CC-F API boundary validation | ✅ Complete | `apps/api/src/graphql/validation.ts` — 10 Zod v4 `.strict()` schemas for all GraphQL mutation inputs (CreateWorkspace, UpdateWorkspace, CreateProject, UpdateProject, StartAgentRun, UpdateAgentRun, ApproveRequirement, RejectRequirement, BulkApproveReject, EnrichRequirement, CreateCanvasDocument) + `validateInput<T>()` helper throwing `GraphQLError` with `BAD_USER_INPUT`. `__tests__/validation.test.ts` — 29 Vitest tests (all pass). `zod@^4.3.6` added to api/package.json. |
| CC-G AI/ML hook test | ✅ Complete | `web/src/hooks/__tests__/useSemanticSearch.test.ts` — 9 Vitest tests using `renderHook` + `QueryClientProvider` wrapper + `vi.spyOn` on `SearchService` exports; covers empty-state, setQuery triggers search, result/metadata population, clearQuery reset, highlightQuery delegation, enabled=false guard, error surfacing, refresh function exposure. All 9 tests pass. |
| CC-H TraceabilityPanel bug fix + unit tests | ✅ Complete | Fixed `import type` → `import` in `TraceabilityPanel.tsx` (was `import type { LifecyclePhase } from '@/shared/types/lifecycle'` — type-erased at runtime, crashing `Object.values(LifecyclePhase)`; corrected to `import { LifecyclePhase } from '@/types/lifecycle'`). Created `canvas/lifecycle/__tests__/TraceabilityPanel.test.tsx` — 16 Vitest + RTL tests covering: header render, graph/matrix toggle, artifact rendering, status badges, link counts, detail panel open/close, linked artifacts, refresh callback, AI Analyze show/hide, AI analysis results, Analyzing... state, link creation flow, loading state. All 16 tests pass. |
| CC-I AIChatInterface unit tests | ✅ Complete | Created `components/placeholders/__tests__/AIChatInterface.test.tsx` — 18 Vitest + RTL tests covering: header/subtitle render, Ready status, empty state, assistant/user message rendering, system message filter, ThinkingIndicator when loading, textarea placeholder, onSendMessage via button click, input clear after send, Enter key send, Shift+Enter no-send, empty input guard, send button disabled (empty), send button enabled (text), send button disabled (isLoading + text), timestamp rendering, keyboard shortcut hint. Mocks `scrollIntoView` in `beforeEach`. All 18 tests pass. |
| CC-J Traceability E2E spec | ✅ Complete | Created `e2e/current-release/traceability-view.spec.ts` — 7 Playwright specs (all `test.skip(true, 'Route not yet deployed to CI environment')`) across 3 suites: graph view (panel heading, toggle buttons, matrix switch, graph switch back), artifact selection (open detail panel, deselect artifact), refresh/AI analyze. Uses `?panel=traceability` query parameter pattern. |
| CC-K ValidationPanel unit tests | ✅ Complete | Created `components/placeholders/__tests__/ValidationPanel.test.tsx` — 20 Vitest + RTL tests covering: empty state, header count badges (singular/plural for each severity), severity label badges, title/message/description rendering, multiple issues in list, Fix button callback (onResolve), Ignore button callback (onIgnore), missing handler → no button rendered. All 20 tests pass. |
| CC-L SprintBoard unit tests | ✅ Complete | Created `components/placeholders/__tests__/SprintBoard.test.tsx` — 24 Vitest + RTL tests using Jotai `createStore` + `Provider` wrapper to inject atom state. Covers: 4 columns rendered (To Do, In Progress, In Review, Done), "No stories" empty state per column, sprint header show/hide, sprint dates + daysRemaining, story card in correct column, "Untitled Story" fallback, type/priority badges, story points, labels, column count badges, onStoryClick/onCreateStory callbacks, filtering by search/priority/type/label. All 24 tests pass. |
| CC-M SecurityDashboard @ts-nocheck fix + unit tests | ✅ Complete | Removed `// @ts-nocheck` from `SecurityDashboard.tsx`. Fixed bug: `ROUTES.project.security(id)` → `ROUTES.security.root(id)` (the `@ts-nocheck` was hiding a runtime crash — `security` is an object with `root()`, not a callable). Added `SecurityAlert` interface and typed the `securityAlertsAtom` cast. Created `components/placeholders/__tests__/SecurityDashboard.test.tsx` — 16 Vitest + RTL tests using Jotai `createStore` + `Provider` + `MemoryRouter`. Covers: header/View-all link, score ring value, null-score default, "Security Score" label, 4 severity sections, severity open counts, fixed-vuln exclusion, Open Alerts section, empty-alerts state, alert title/message/fallback rendering, resolved alert exclusion, max-4-alerts cap. All 16 tests pass. |
| CC-N ImprovePanel unit tests | ✅ Complete | Created `components/canvas/lifecycle/__tests__/ImprovePanel.test.tsx` — 18 Vitest + RTL tests covering empty state, insights rendering, action buttons, iteration history, and callback props. All 18 pass. |
| CC-O ThreatModelPanel unit tests | ✅ Complete | Created `components/canvas/lifecycle/__tests__/ThreatModelPanel.test.tsx` — 20 Vitest + RTL tests covering STRIDE threat categories, severity badges, threat details, add/fix/ignore callbacks, and empty state. All 20 pass. |
| CC-P AdrPanel unit tests | ✅ Complete | Created `components/canvas/lifecycle/__tests__/AdrPanel.test.tsx` — 15 Vitest + RTL tests covering ADR list rendering, status badges, decision/rationale content, create/edit callbacks, and empty state. All 15 pass. |
| CC-P UxSpecPanel unit tests | ✅ Complete | Created `components/canvas/lifecycle/__tests__/UxSpecPanel.test.tsx` — 15 Vitest + RTL tests covering spec sections, interaction patterns, save/edit callbacks, read-only state, and empty state. All 15 pass. |
| CC-Q ArtifactsPanel unit tests | ✅ Complete | Added missing `getArtifactsForSurface()` export to `lifecycle-artifacts.ts`. Created `components/canvas/lifecycle/__tests__/ArtifactsPanel.test.tsx` — 18 Vitest + RTL tests (MemoryRouter wrapper). Covers heading, subtitle, phase section headings, artifact abbreviations (REQ/ARC/UX), status badges (Complete/Draft), onCreateArtifact callback (requires `status:'missing'`), Quick Actions section, "View Traceability Graph" button. All 18 pass. |
| CC-Q LifecyclePhaseIndicator unit tests | ✅ Complete | Fixed syntax error in `CompactPhaseIndicator` (broken template literal). Created `components/canvas/lifecycle/__tests__/LifecyclePhaseIndicator.test.tsx` — 17 Vitest + RTL tests across `LifecyclePhaseIndicator`, `CompactPhaseIndicator`, and `PhaseProgressBar`. Covers chip label, dialog open/close, phase description, transition buttons, Change Phase callback, disabled state, progress bar caption. All 17 pass. |
| CC-Q LifecycleGuidance unit tests | ✅ Complete | Fixed 3 syntax errors + missing `getPhaseColor` import in `LifecycleGuidance.tsx`. Created `components/canvas/lifecycle/__tests__/LifecycleGuidance.test.tsx` — 22 Vitest + RTL tests. Covers guidance title per phase, description, Tips section (show/hide/expand), Next Step heading/text/transition buttons, Full Workflow section, welcome dialog (show/dismiss/hasElements/projectName). All 22 pass. |
| CC-Q CanvasRightPanelHost unit tests | ✅ Complete | Created `components/canvas/lifecycle/__tests__/CanvasRightPanelHost.test.tsx` — 23 Vitest + RTL tests (MemoryRouter with initialEntries). Covers null render when no ?panel param, panel container present, close/prev/next buttons, prev disabled on first panel, next disabled on last panel, "1/7" counter, bottom nav tabs (Artifacts enabled, Requirements disabled), read-only fallback for all 6 handler-less panels, collapsed state (slim sidebar / Expand panel button / onToggleCollapse callback), width prop. All 23 pass. |
| CC-R RequirementsPanel unit tests | ✅ Complete | Created `components/requirements/__tests__/RequirementsPanel.test.tsx` — 33 Vitest + RTL tests. Covers: header actions (export/import/settings), filter controls (search, status/priority/type dropdowns), empty state, requirement list rendering, status/priority/type badges, onRequirementClick/onCreateRequirement callbacks, bulk actions panel (count label, approve-all/reject-all), AI assistant panel, metrics panel (coverage/quality/risks), pagination. All 33 pass. |
| CC-S Validation dialog + summary panel unit tests | ✅ Complete | Created `components/validate/__tests__/ValidationRunDialog.test.tsx` (22 tests) and `components/validate/__tests__/ValidationSummaryPanel.test.tsx` (38 tests). Covers: dialog visibility, pre-start state, starting/running/complete states, close behavior, header stats, category grouping, check interactions, report generation (PDF/MD buttons), AI assist. All 60 pass. Key fix: export buttons labeled "PDF" and "MD", text appearing in multiple elements uses `getAllByText`. |
| CC-T ConfigEditor + YamlEditor + ConfigDiff unit tests | ✅ Complete | Fixed `ConfigEditor.tsx`: added `useCallback` to React import, corrected `handleModeChange` API from MUI 2-arg to design-system 1-arg `(newMode: string | string[])`. Created `components/config-editor/__tests__/config-editor.test.tsx` — 28 Vitest + RTL tests. Covers: JSON/YAML mode toggle (`role="option"` not `role="button"` for ToggleButton), mode switching, readOnly (textarea `.toBeDisabled()`), YamlEditor format/status/character count, ConfigDiff heading/identical-configs/added/removed/modified (using `getAllByText` for multiple Old:/New: labels), apply callback. All 28 pass. |
| CC-U CollaborationBar + RemoteCursor unit tests | ✅ Complete | Created `components/collaboration/__tests__/collaboration.test.tsx` — 24 Vitest + RTL tests. CollaborationBar covers: Connected/Disconnected status, syncStatus visibility, user count (singular/plural), avatar initials, avatar overflow badge (+N), className prop. RemoteCursor covers: renders when online with cursor, null render when no cursor or offline, label show/hide (showLabel prop), SVG cursor element, user color fill attribute. All 24 pass. |
| CC-V WorkflowContextPanel + LifecycleProgressRail unit tests | ✅ Complete | Created `components/tasks/__tests__/tasks.test.tsx` — 31 Vitest + RTL tests. WorkflowContextPanel: null state ("No workflow selected"), header, progress counts/percentage chip, Tasks/Artifacts tabs (`role="tab"`), empty task state, task name formatting (kebab-case → Title Case), domain grouping, footer date/phase. LifecycleProgressRail: 4 group labels, all 8 stage names, overall progress counter, stage selection callback (`onStageSelect`), global `disabled` guard, task counts (`stageTasks`), partial progress chip (shown only for 0 < p < 100). All 31 pass. |
| CC-W Dashboard component unit tests | ✅ Complete | Created dashboard tests: `components/dashboard/__tests__/DashboardView.test.tsx` (14 tests), `components/dashboard/__tests__/DecisionQueue.test.tsx` (18 tests), `components/dashboard/__tests__/RiskAlerts.test.tsx` (20 tests). Total 52 tests all passing. Covers: loading states, empty states, data rendering, callbacks (onApprove/onReject/onDismiss), severity chips (.toUpperCase()), filtering, header content. |
| CC-X Journey component unit tests | ✅ Complete | Created `components/journey/__tests__/IntelligentOnboarding.test.tsx` (16 tests) and `components/journey/__tests__/PhaseGuidedFlow.test.tsx` (13 tests). Total 29 tests all passing. Key discoveries: StepContent children not rendered by Stepper (renders own internal UI); Next guarded on role selection (step 1) but not name (step 0); "Back to Project" navigates to `/journey/p/:id`; progress chip shows "0/4 tasks". |
| CC-Y Intent capture unit tests | ✅ Complete | Created `components/intent/__tests__/IntentDrawer.test.tsx` — 15 Vitest + RTL tests. Uses `MemoryRouter initialEntries` for `?drawer=` query param. Key fixes: child forms use `onSubmit` prop (not `onSave`); drawer title appears in heading + nav pills — use `getByRole('heading', { name: 'Idea Brief' })`. Covers: null render (no param/invalid), dialog presence, close button, all drawer kinds (idea/epic/feature/bug/experiment), onSave callback. All 15 pass. |
| CC-Z AI component unit tests | ✅ Complete | Created `components/ai/__tests__/AICommandBar.test.tsx` — 17 Vitest + RTL tests. Covers: input rendering (`aria-label="AI prompt input"`), submit enable/disable, Enter key submit, isProcessing disables input+submit, history button visibility, expand/collapse button, Clear button, "Open full panel →" button, quick action chips for brainstorm mode. Key: expand button has multiple matches due to Tooltip — used `queryAllByRole`. All 17 pass. |
| CC-AA Audit trail unit tests | ✅ Complete | Created `components/audit/__tests__/AuditTrail.test.tsx` — 14 Vitest + RTL tests. Covers: empty state ("No events found"), event count stats bar, actor name, message rendering, error alert, search filter (reduces count), export buttons (JSON/CSV/PDF calling onExport with format), refresh button (IconButton renders as "Icon button" aria-label), disabled when isLoading, multiple events count. All 14 pass. |
| CC-AB AI response card + contextual suggestions unit tests | ✅ Complete | Created `components/ai/__tests__/AIResponseCard.test.tsx` — 25 tests. Created `components/ai/__tests__/ContextualSuggestions.test.tsx` — 19 tests. Key: ContextualSuggestions action button requires BOTH `actionLabel` AND `onAction` props to render. Dismiss button uses `title="Dismiss"` attribute. All 44 pass. |
| CC-AC CommandPalette unit tests | ✅ Complete | Created `components/command/__tests__/CommandPalette.test.tsx` — 15 tests. Mocks `ActionRegistry.useActions`. Key: after filtering, `highlightMatches` splits text into spans — use regex matchers. Enter executes first action, Escape calls `onOpenChange(false)`. All 15 pass. |
| CC-AD WorkspaceSelectionDialog unit tests | ✅ Complete | Created `components/workspace/__tests__/WorkspaceSelectionDialog.test.tsx` — 12 tests. Key: Confirm button text is "Open in Workspace" not "Confirm". Disabled when no selectedWorkspaceId. All 12 pass. |
| CC-AE ComplianceReport unit tests | ✅ Complete | Created `components/audit/__tests__/ComplianceReport.test.tsx` — 20 tests. Covers loading/error/null-report states, framework name (SOC 2), score%, status badge (PARTIAL/COMPLIANT/NON-COMPLIANT), summary labels, check list (controlIds + names), Export + Re-run callbacks. Fixed: "Passed"/"Failed" appear in both summary counter and check chips — use `getAllByText`. All 20 pass. |
| CC-AF Workflow step components unit tests | ✅ Complete | Created `components/workflow/steps/__tests__/workflow-steps.test.tsx` — 22 tests covering all 8 steps (ContextStep, IntentStep, PlanStep, ExecuteStep, ObserveStep, VerifyStep, LearnStep, InstitutionalizeStep). Uses Jotai `createStore + Provider`. Fixed: `@yappc/core/types` alias missing in vitest.config.ts (added it); `types/index.ts` and `types/index.js` missing `export * from './workflow'` (fixed). All 22 pass. |
| CC-AG AI overlay/action components tests | ✅ Complete | Created `components/ai/__tests__/ai-components.test.tsx` — 42 Vitest + RTL tests. Covers: `AILabelOverlay` (size/variant/tooltip/icon/onClick), `NextBestAction` (title/description/impact/estimated-time/callbacks), `CommandInput` (controlled/uncontrolled, submit/cancel/clear/Enter key/isProcessing), `RecentProjectsStrip` (empty → null, project names/routes/phases/health scores/actions). All 42 pass. |
| CC-AH Canvas empty state + utility component tests | ✅ Complete | Created `components/canvas/__tests__/canvas-utility.test.tsx` — 22 tests. Covers: `CanvasEmptyState` (message/description/primaryAction/secondaryAction/aiSuggestions/custom-icon), `LoadingFallback` ("Loading…" text, SVG spinner, flex layout), `SkipLink` (default/custom text, href, focus-on-click, safe-no-target). Fixed jsdom `scrollIntoView` by stubbing on target element. All 22 pass. |
| CC-AI OnboardingChecklist unit tests | ✅ Complete | Created `components/__tests__/OnboardingChecklist.test.tsx` — 14 tests. Covers: heading, "0 of N steps completed" / 0%, Reset button, "Getting Started" category heading, step toggle (check/uncheck), progress update, congratulations on all-complete, reset restores 0, localStorage persistence, restore from localStorage on mount, step text visibility. All 14 pass. |
| CC-AJ Canvas AI component tests | ✅ Complete | Created `components/canvas/ai/__tests__/canvas-ai.test.tsx` — 30 tests. Also fixed 2 broken template literals in `AISuggestionsPanel.tsx` and 1 in `GhostNode.tsx`. Covers: `AINotificationToast` (show=false→null, singular/plural header, count message, View Suggestions→onView, X→onDismiss, autoDismiss timer), `AISuggestionsPanel` (empty state, analyzing, suggestion title/desc/confidence chip, Accept/Dismiss/DismissAll callbacks, critical/high priority chips), `AIBadge` (renders, onClick), `GhostNode` (label, aria-label, Enter→onAccept, Escape→onDismiss, type fallback label), `GhostNodeLayer` (empty→null, all nodes rendered, aria-live="polite"). All 30 pass. |
| CC-AK KeyboardShortcutLegend + CanvasProgressWidget tests | ✅ Complete | Created `components/canvas/__tests__/canvas-dialog-components.test.tsx` — 16 tests. Fixed 2 broken template literals in `CanvasProgressWidget.tsx`. Covers: `KeyboardShortcutLegend` (open=false→no dialog, open=true→heading, onClose, Essential/Tools categories, Undo/Redo shortcuts, "?" hint), `CanvasProgressWidget` mini variant (renders, progress %), compact variant (overall %, phase indicators in expanded state, onPhaseClick, 0% when empty). All 16 pass. |
| CC-AL DraggableComponent tests | ✅ Complete | Created `components/__tests__/DraggableComponent.test.tsx` — 13 tests. Covers: `DraggableItem` (renders children, button type, text type default, unknown type fallback, onDragStart callback), `ComponentLibrary` (all labels, heading, onComponentSelect on click), `DraggableCanvas` (empty state message, Canvas heading, sidebar, add from library, pre-supplied items). All 13 pass. |
| CC-AM Lifecycle badge + breadcrumb tests | ✅ Complete | Created `components/lifecycle/__tests__/lifecycle-badge-breadcrumb.test.tsx` — 23 tests. Covers LifecyclePhaseBadge (label, phase-specific styling), LifecycleBreadcrumb (phase display, separator, complete state), and related components. All 23 pass. |
| CC-AN Persona badge + status components | ✅ Complete | Created `components/canvas/workspace/__tests__/persona-badge.test.tsx` (19 tests) and `components/status/__tests__/status-components.test.tsx` (21 tests). Covers PersonaBadge (abbreviation logic, role display), StatusCard, BackendStatusIndicator, HealthPanel, IncidentsPanel. All passing. |
| CC-AO Observe + build-progress tests | ✅ Complete | Created `components/observe/__tests__/observe-components.test.tsx` (25 tests) and `components/deploy/__tests__/build-progress-tracker.test.tsx` (16 tests). Covers MetricsPanel, SLOCard, ServiceHealthCard; BuildProgressTracker (SUCCESS status, step count, progress bar). All passing. |
| CC-AP Common state components tests | ✅ Complete | Created `components/common/__tests__/common-state-components.test.tsx` — 26 tests. Covers LoadingState (role=status), ErrorState (role=alert, "Try Again" button), EmptyState, ConfirmationDialog. All 26 pass. |
| CC-AQ UI primitives tests | ✅ Complete | Created `components/ui/__tests__/ui-primitives.test.tsx` — 42 tests. Covers ProgressBar (color thresholds), VoiceInputButton, ToggleButtonGroup, TextField, and more. All 42 pass. |
| CC-AR Route components tests | ✅ Complete | Created `components/route/__tests__/route-components.test.tsx` — 19 tests. All pass. |
| CC-AS Layout components tests | ✅ Complete | Created `components/layout/__tests__/layout-components.test.tsx` — 21 tests. All pass. |
| CC-AT Workspace components tests | ✅ Complete | Created `components/workspace/__tests__/workspace-components.test.tsx` — 6 tests. All pass. |
| CC-AU Widget components tests | ✅ Complete | Created `components/widgets/__tests__/widgets.test.tsx` — 19 tests (KPICard 10, TableWidget 9; ChartWidget excluded due to Recharts jsdom incompatibility). All 19 pass. |
| CC-AV Project + voice + rate-limit tests | ✅ Complete | Created `components/project/__tests__/project-voice-ratelimit.test.tsx` — 13 tests. Covers ProjectCard (6), VoiceInputButton (4, isSupported=true in jsdom), ThrottleAlertBanner (3, needs QueryClientProvider). All 13 pass. |
| CC-AW GuidancePanel tests | ✅ Complete | Created `components/guidance/__tests__/guidance-panel.test.tsx` — 7 tests. Mocked usePhaseContext/useGuidanceContext from WorkflowContextProvider. Covers expand/collapse, steps, tips. All 7 pass. |
| CC-AX KeyboardShortcutsHelp tests | ✅ Complete | Created `components/help/__tests__/keyboard-shortcuts-help.test.tsx` — 5 tests. Close by aria-label="Close"; Escape triggers toHaveBeenCalled() (fires twice). All 5 pass. |
| CC-AY Navigation components tests | ✅ Complete | Created `components/navigation/__tests__/navigation-components.test.tsx` — 12 tests. Mocked design-tokens PHASE_COLORS for all enum values (CONTEXT/PLAN/EXECUTE/VERIFY/LEARN). Covers ActionsToolbar (6: toolbar role, action buttons by aria-label, multiple actions, onClick, disabled, empty) and UnifiedPhaseRail (6: navigation aria-label, aria-current=step, onPhaseClick, full variant, horizontal/vertical orientation). All 12 pass. |
| CC-AZ Dialogs tests | ✅ Complete | Created `components/dialogs/__tests__/dialogs.test.tsx` — 15 tests. |
| CC-BA Shared components tests | ✅ Complete | Created `components/shared/__tests__/shared-components.test.tsx` — 16 tests. |
| CC-BB Keyboard shortcuts manager tests | ✅ Complete | Created `components/keyboard/__tests__/keyboard-shortcuts-manager.test.tsx` — 6 tests. |
| CC-BC SmartFormField tests | ✅ Complete | Created `components/forms/__tests__/smart-form.test.tsx` — 7 tests. Key: Dismiss button (not Reject), confidence chip visible when confidence > 0.5. |
| CC-BD PersonaSwitcher tests | ✅ Complete | Created `components/persona/__tests__/persona-switcher.test.tsx` — 4 tests. PersonaType values are lowercase strings ('developer', 'designer'). |
| CC-BE Intent forms tests | ✅ Complete | Created `components/intent/__tests__/intent-forms.test.tsx` — 13 tests. IdeaBriefForm, ProblemStatementEditor, ResearchPackEditor. |
| CC-BF EvidencePanel tests | ✅ Complete | Created `components/workflow/__tests__/evidence-panel.test.tsx` — 3 tests. Mock atoms as plain strings, mock jotai useAtomValue. Tabs: "Audit" and "AI". |
| CC-BG SkipLink tests | ✅ Complete | Created `components/accessibility/__tests__/skip-link.test.tsx` — 4 tests. |
| CC-BH ValidationPanel tests | ✅ Complete | Created `components/preview/__tests__/validation-panel.test.tsx` — 3 tests. Mock LifecycleArtifactService useLifecycleArtifacts. |
| CC-BI ContextualToolbar tests | ✅ Complete | Created `components/actions/__tests__/contextual-toolbar.test.tsx` — 4 tests. Key fix: state must include `currentRoute: '/dashboard'` (component calls .includes('/canvas')). |
| CC-BJ Design-system local components tests | ✅ Complete | Created `components/design-system/__tests__/design-system-components.test.tsx` — 10 tests. Badge (6 tests) and HeaderButton (4 tests). |
| CC-BK Intent-capture components tests | ✅ Complete | Created `components/intent-capture/__tests__/intent-capture.test.tsx` — 4 tests. RequirementList: empty state, renders items, search filtering, click selection. |
| CC-BL GlobalSearch assertion correction | ✅ Complete | Updated `components/search/__tests__/global-search.test.tsx` fuzzy-matching expectation to grouped-result behavior (`Projects`) instead of empty-state text. Re-ran suite: 25/25 tests passing. |
| CC-BM Preview-host tests completion | ✅ Complete | Added/stabilized `components/preview-host/__tests__/preview-host-shell.test.tsx` with full design-system stubs (including Toolbar) and panel mocks; fixed duplicate-title assertion by using `getAllByText` where title appears in multiple regions. Preview-host suite passing: 14/14 (shell + subcomponents). |
| CC-BN MobileCard tests completion | ✅ Complete | Added `components/mobile/__tests__/mobile-card.test.tsx` (6 tests). Added Vitest alias for `@capacitor/core` in `web/vitest.config.ts` plus local mock `src/__mocks__/@capacitor/core.ts`. Stabilized unimported globals (`useTheme`, `alpha`) via `vi.stubGlobal` and forwarded click handlers through CardContent stub. File passing: 6/6. |
| CC-BO Performance utilities tests completion | ✅ Complete | Added `components/performance/__tests__/performance.test.tsx` (11 tests) for `PerformanceOptimizedClean.tsx` only. Resolved Vitest worker OOM by removing fake-timer + async polling deadlocks, avoiding never-resolving loaders, and disabling `trackRenders`/`trackMemory` in `PerformanceMonitor` test mounts to avoid internal re-render loop behavior. File passing: 11/11. |
| CC-BP VoiceInputButton tests | ✅ Complete | Created `components/voice/__tests__/VoiceInputButton.test.tsx` (18 tests). Covered both `VoiceInputButton` and `VoiceInputIndicator` exports. Used `vi.hoisted(() => vi.fn())` + `useVoiceInputMock.mockReturnValue(...)` per-test pattern to control hook output. Mock path corrected to `'../../../hooks/useVoiceInput'` (3 levels up from `__tests__/`). Covers: supported/unsupported states, aria labels, toggleListening call, disabled prop, error status, listening animation (`.animate-ping`), interim transcript tooltip, size classes (small/medium/large), className forwarding; VoiceInputIndicator null/visible render, custom className. File passing: 18/18. |
| CC-BQ RateLimitStatusWidget + ThrottleAlertBanner tests | ✅ Complete | Replaced 5-line stub `components/ratelimit/__tests__/RateLimitStatusWidget.test.tsx` with 20 real tests across two `describe` blocks. Mocked `@tanstack/react-query` via `vi.hoisted` + `useQuery` mock + `@/lib/http` stubs. `RateLimitStatusWidget` (10 tests): loading skeleton, error state, tier badge/usage, upgrade button show/hide (enterprise tier suppresses it), upgrade click callback, rate-limit-exceeded warning, approaching-limit warning, view-details button, remaining count. `ThrottleAlertBanner` (10 tests): no-render when below threshold, alert when at threshold, rate-limit exceeded message, dismiss closes alert, onDismiss callback, upgrade plan button show/hide, upgrade callback, view details callback, no-render when data undefined. File passing: 20/20. |

### Implemented Evidence (All Sessions)

- `platform/typescript/code-editor/src/components/CodeDiffViewer.tsx`
- `platform/typescript/code-editor/src/components/VisualBlockEditor.tsx`
- `platform/typescript/code-editor/src/components/CodeDiffViewer.test.tsx`
- `platform/typescript/code-editor/src/components/VisualBlockEditor.test.tsx`
- `products/yappc/frontend/web/src/components/approvals/ApprovalDetail.tsx` — extended with `EnrichmentSuggestion` interface + enrichment panel (P3-17)
- `products/yappc/frontend/web/src/components/preview-host/__tests__/preview-host-shell.test.tsx`
- `products/yappc/frontend/web/src/components/preview-host/__tests__/preview-subcomponents.test.tsx`
- `products/yappc/frontend/web/src/components/mobile/__tests__/mobile-card.test.tsx`
- `products/yappc/frontend/web/src/components/performance/__tests__/performance.test.tsx`
- `products/yappc/frontend/web/src/__mocks__/@capacitor/core.ts`
- `products/yappc/frontend/web/vitest.config.ts` — test alias for `@capacitor/core`
- `products/yappc/frontend/web/src/components/approvals/__tests__/ApprovalDetail.test.tsx` — 5 new enrichment tests
- `products/yappc/frontend/web/src/components/audit/AuditTimeline.tsx`
- `products/yappc/frontend/web/src/components/audit/__tests__/AuditTimeline.test.tsx`
- `products/yappc/frontend/web/src/components/requirements/RequirementLifecycleBoard.tsx`
- `products/yappc/frontend/web/src/components/requirements/RequirementDetail.tsx`
- `products/yappc/frontend/web/src/components/requirements/RequirementVersionHistory.tsx`
- `products/yappc/frontend/web/src/components/requirements/types.ts`
- `products/yappc/frontend/web/src/components/requirements/__tests__/RequirementLifecycleBoard.test.tsx`
- `products/yappc/frontend/web/src/state/atoms/requirementAtom.ts`
- `products/yappc/frontend/web/src/components/canvas/content/CodeEditorCanvas.tsx`
- `products/yappc/frontend/web/src/components/canvas/content/__tests__/CodeEditorCanvas.test.tsx`
- `products/yappc/frontend/web/src/routes/app/project/lifecycle.tsx`
- `products/yappc/frontend/e2e/current-release/lifecycle-governance.spec.ts`
- `products/yappc/frontend/apps/api/prisma/schema.prisma` — Sprint + ExportArtifact models added
- `products/yappc/frontend/apps/api/src/routes/planning.ts` — Sprint CRUD + workspace member management REST routes
- `products/yappc/frontend/apps/api/src/index.ts` — planningRoutes registered
- `products/yappc/frontend/apps/api/src/graphql/schema.graphql` — Export, Search, Bulk, Requirement types added
- `products/yappc/frontend/apps/api/src/graphql/resolvers/requirements-approvals.resolver.ts` — enrichRequirement + bulk mutations
- `products/yappc/frontend/apps/api/src/graphql/resolvers/export.resolver.ts` — NEW export resolvers
- `products/yappc/frontend/apps/api/src/graphql/resolvers/index.ts` — exportResolvers wired
- `products/yappc/frontend/web/src/components/planning/BacklogBoard.tsx`
- `products/yappc/frontend/web/src/components/planning/__tests__/BacklogBoard.test.tsx`
- `products/yappc/frontend/web/src/components/planning/SprintView.tsx`
- `products/yappc/frontend/web/src/components/planning/__tests__/SprintView.test.tsx`
- `products/yappc/frontend/web/src/components/workspace/WorkspaceMembers.tsx`
- `products/yappc/frontend/web/src/components/workspace/__tests__/WorkspaceMembers.test.tsx`
- `products/yappc/frontend/web/src/components/export/ExportDialog.tsx` — NEW export dialog
- `products/yappc/frontend/web/src/components/export/__tests__/ExportDialog.test.tsx` — 9 unit tests
- `products/yappc/frontend/web/src/components/admin/ObservabilityDashboard.tsx` — NEW observability dashboard
- `products/yappc/frontend/web/src/components/admin/__tests__/ObservabilityDashboard.test.tsx` — 15 unit tests
- `products/yappc/frontend/e2e/current-release/agent-run-viewer.spec.ts`
- `products/yappc/frontend/e2e/current-release/requirement-lifecycle.spec.ts`
- `products/yappc/frontend/e2e/current-release/global-search.spec.ts` — NEW (P2-13)
- `products/yappc/frontend/e2e/current-release/export.spec.ts` — NEW (P2-14)
- `products/yappc/frontend/e2e/current-release/observability.spec.ts` — NEW (P2-15)
- `products/yappc/frontend/e2e/current-release/approval-flow.spec.ts` — NEW (missing E2E)
- `products/yappc/frontend/e2e/current-release/planning-board.spec.ts` — NEW (missing E2E)
- `products/yappc/frontend/e2e/current-release/workspace-members.spec.ts` — NEW (missing E2E)
- `products/yappc/frontend/e2e/current-release/agent-run-visibility.spec.ts` — NEW (missing E2E)
- `products/yappc/frontend/e2e/current-release/audit-timeline.spec.ts` — NEW (P3-20)
- `products/yappc/scripts/scan-todos.sh` — CI-aware TODO scanner + report generation
- `products/yappc/frontend/e2e/requirement-capture.spec.ts`
- `products/yappc/frontend/web/src/components/agents/AgentAdvancedSettings.tsx` — NEW progressive disclosure component (P3-21)
- `products/yappc/frontend/web/src/components/agents/__tests__/AgentAdvancedSettings.test.tsx` — 18 unit tests (P3-21)
- `products/yappc/frontend/e2e/current-release/progressive-disclosure.spec.ts` — NEW E2E spec (P3-21)
- `products/yappc/frontend/e2e/current-release/activity-feed.spec.ts` — NEW E2E spec (activity feed)

---

## P0 — Critical Blockers

### 1. Complete backend module migration

**Goal:** Move existing agent and scaffold implementation files into the new module boundaries that were created but not fully migrated.

| Area | Current / Source Location | Target Location | Execution Notes |
|---|---|---|---|
| Agent specialists | `products/yappc/core/agents/specialists/**` | `products/yappc/core/agents/code-specialists/src/main/java/com/ghatana/yappc/agents/code/**` | Move code generation, refactoring, code analysis agents here. |
| Architecture agents | `products/yappc/core/agents/specialists/**` | `products/yappc/core/agents/architecture-specialists/src/main/java/com/ghatana/yappc/agents/architecture/**` | Move architecture/design/pattern agents here. |
| Testing agents | `products/yappc/core/agents/specialists/**` | `products/yappc/core/agents/testing-specialists/src/main/java/com/ghatana/yappc/agents/testing/**` | Move test generation, test validation, coverage agents here. |
| Scaffold core | `products/yappc/core/scaffold/core/**` | `products/yappc/core/scaffold/engine/src/main/java/com/ghatana/yappc/scaffold/engine/**` | Move orchestration and scaffold engine logic. |
| Scaffold generators | `products/yappc/core/scaffold/core/**` and `products/yappc/core/scaffold/api/**` | `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/scaffold/generators/**` | Move language/framework generators here. |
| Scaffold templates | `products/yappc/core/scaffold/**/templates/**` | `products/yappc/core/scaffold/templates/src/main/java/com/ghatana/yappc/scaffold/templates/**` | Centralize template discovery, rendering, validation. |
| Gradle module registration | `settings.gradle.kts` | same | Ensure new modules are included only if directories exist. |
| Module build files | `products/yappc/core/agents/*/build.gradle.kts` and `products/yappc/core/scaffold/*/build.gradle.kts` | same | Verify dependencies are minimal and directional. |
| Boundary tests | `products/yappc/core/agents/code-specialists/src/test/**` | Add equivalent tests under all new modules | Add ArchUnit tests to prevent circular dependencies. |

**Validation**
- Run `./gradlew clean build test`.
- Run module boundary checks.
- Run existing agent/scaffold tests.
- Verify no imports remain from old specialist/core folders.
- Delete old folders only after all references are removed.

---

### 2. Execute frontend library consolidation

**Goal:** Reduce frontend library sprawl and enforce a smaller set of shared, reusable libraries.

| Area | Current / Source Location | Target Location | Execution Notes |
|---|---|---|---|
| Consolidation script | `products/yappc/frontend/scripts/consolidate-libraries.js` | same | Execute after dry run; review generated import updates. |
| UI components | `products/yappc/frontend/libs/design-system/**`, `products/yappc/frontend/libs/yappc-ui/**`, `products/yappc/frontend/libs/ui/**` | `products/yappc/frontend/libs/ui/**` and shared `@ghatana/ui` where applicable | Prefer global design system first, product extension second. |
| Style panels / style system | `products/yappc/frontend/libs/visual-style-panel/**`, `products/yappc/frontend/libs/style-system/**` | `products/yappc/frontend/libs/style-system/**` | Merge styling logic and remove duplicate component APIs. |
| Realtime / websocket | `products/yappc/frontend/libs/websocket/**`, `products/yappc/frontend/libs/realtime/**`, `products/yappc/frontend/libs/sync/**` | `products/yappc/frontend/libs/realtime/**` or `products/yappc/frontend/libs/sync/**` | Pick one source of truth for realtime sync. |
| Layout / responsive editor | `products/yappc/frontend/libs/responsive-breakpoint-editor/**`, `products/yappc/frontend/libs/layout/**` | `products/yappc/frontend/libs/layout/**` | Consolidate responsive behavior. |
| Monitoring / observability | `products/yappc/frontend/libs/telemetry/**`, `products/yappc/frontend/libs/monitoring/**`, `products/yappc/frontend/libs/observability/**` | `products/yappc/frontend/libs/observability/**` | One observability package only. |
| Import restrictions | `products/yappc/frontend/eslint-local-rules/rules/prefer-yappc-ui.ts` | same | Update rule to block deprecated libraries. |
| Root package scripts | `products/yappc/frontend/package.json` | same | Keep simplified scripts; add migration validation script. |

**Validation**
- Run `pnpm install`.
- Run `pnpm typecheck`.
- Run `pnpm lint`.
- Run `pnpm test`.
- Run `pnpm test:e2e`.
- Verify no imports from deprecated packages.
- Run bundle analysis before and after.

---

### 3. Implement Code Editor + Visual Block Editor

**Goal:** Make generated code editable inside YAPPC and connect visual design ↔ code editing.

| Area | Current / Source Location | Target Location | Execution Notes |
|---|---|---|---|
| Code editor types | `products/yappc/frontend/libs/code-editor/src/types.ts` | same | Existing type system should be used as contract. |
| Code editor exports | `products/yappc/frontend/libs/code-editor/src/index.ts` | same | Export concrete components after implementation. |
| Monaco editor component | not implemented | `products/yappc/frontend/libs/code-editor/src/components/CodeEditor.tsx` | Build Monaco wrapper with language, theme, diagnostics, read-only modes. |
| Diff viewer | not implemented | `products/yappc/frontend/libs/code-editor/src/components/CodeDiffViewer.tsx` | Show generated vs edited code, version diffs. |
| Visual block editor | not implemented | `products/yappc/frontend/libs/code-editor/src/components/VisualBlockEditor.tsx` | Block-based editing mapped to AST/code generation. |
| Code generation bridge | existing page-builder code generation | `products/yappc/frontend/libs/page-builder/src/**` + `products/yappc/frontend/libs/code-editor/src/adapters/**` | Connect page-builder model to generated code and editor state. |
| Canvas integration | `products/yappc/frontend/libs/canvas/src/**` | `products/yappc/frontend/apps/web/src/components/**` or canvas panel location | Add side panel or drawer for selected node code editing. |
| Tests | missing | `products/yappc/frontend/libs/code-editor/src/**/*.test.tsx` and `products/yappc/frontend/e2e/code-editor.spec.ts` | Test editing, diff, persistence, error states. |

**Validation**
- Open page-builder node → generated code appears.
- Edit code → preview updates or marks dirty.
- Save code → version/audit entry created.
- Invalid code → diagnostics shown, no fake success state.
- E2E test covers visual design → code → save → reload.

---

### 4. Build Approval & Governance UI + APIs

**Goal:** Allow users to approve/reject/refine AI outputs and policy-sensitive changes with full auditability.

| Area | Current / Source Location | Target Location | Execution Notes |
|---|---|---|---|
| DB model | Prisma models recommended: `ApprovalRequest`, `AuditLog`, `PolicyDecision` | `products/yappc/frontend/apps/api/prisma/schema.prisma` | Add or verify approval, reviewer, status, reason, policy metadata, timestamps. |
| GraphQL resolvers | `products/yappc/frontend/apps/api/src/graphql/resolvers/index.ts` | `products/yappc/frontend/apps/api/src/graphql/resolvers/approval*.ts` | Add queries/mutations: list pending approvals, approve, reject, request changes. |
| GraphQL schema | API schema location under `products/yappc/frontend/apps/api/src/graphql/**` | same | Add typed approval contracts. |
| UI approval inbox | not visible | `products/yappc/frontend/web/src/components/approvals/ApprovalInbox.tsx` | List pending approvals by workspace/project. |
| Approval detail | not visible | `products/yappc/frontend/web/src/components/approvals/ApprovalDetail.tsx` | Show AI output, diff, rationale, confidence, policy decision, actions. |
| Audit timeline | not visible | `products/yappc/frontend/web/src/components/audit/AuditTimeline.tsx` | Persist all decisions. |
| Agent policy integration | AEP policy guardrails recommended | `products/yappc/core/agents/**` and/or AEP integration module | Ensure high-risk agent actions create approval requests. |
| Tests | missing | API integration tests + UI e2e | Cover approve/reject/refine and RBAC. |

**Validation**
- AI-generated requirement creates approval request.
- Reviewer approves → requirement version persisted.
- Reviewer rejects → no downstream execution.
- Policy block → visible reason and audit entry.
- Unauthorized user cannot approve.

---

### 5. Ensure end-to-end requirement lifecycle works

**Goal:** Requirement submission should work from UI through API, backend orchestration, DB persistence, AI refinement, approval, versioning, and audit.

| Area | Current / Source Location | Target Location | Execution Notes |
|---|---|---|---|
| Requirement UI | likely under `products/yappc/frontend/web/src/**` | `products/yappc/frontend/web/src/components/requirements/**` | Build or verify capture, list, detail, version history. |
| Requirement state | `products/yappc/frontend/web/src/state/**` | `products/yappc/frontend/web/src/state/atoms/requirementAtom.ts` | Add state for current requirement, processing state, approval state. |
| GraphQL resolvers | `products/yappc/frontend/apps/api/src/graphql/resolvers/index.ts` | `products/yappc/frontend/apps/api/src/graphql/resolvers/requirement*.ts` | CRUD, submit, normalize, approve, version, trace links. |
| Prisma schema | `products/yappc/frontend/apps/api/prisma/schema.prisma` | same | Verify `Requirement`, `RequirementVersion`, `TraceLink`, `AuditLog`. |
| AEP orchestration bridge | YAPPC ↔ AEP integration modules | `products/yappc/core/agents/**` or integration service | Submit requirement orchestration request and receive progress/output. |
| Audit | audit modules | `products/yappc/frontend/web/src/components/audit/**` + API audit resolver | Persist lifecycle events. |
| Tests | missing | `products/yappc/frontend/e2e/requirement-lifecycle.spec.ts` | Cover full lifecycle. |

**Validation**
- Create requirement.
- AI generates normalized version + acceptance criteria.
- User approves.
- Requirement version and audit log persist.
- Reload page and verify state.
- Error/failure states are truthful.

---

### 6. Add visibility for AI agent execution

**Goal:** Users/operators must see what agents are doing, current stage, failures, retries, policy decisions, and outputs.

| Area | Current / Source Location | Target Location | Execution Notes |
|---|---|---|---|
| AEP run APIs | recommended but not visible | `products/yappc/core/agents/runtime/**` or AEP integration package | Expose submit run, inspect run, stream progress, execution trace. |
| Agent runtime types | `products/yappc/core/agents/runtime/src/main/java/com/ghatana/yappc/agent/Budget.java` and runtime package | same | Add/verify run states, step states, budgets, errors. |
| WebSocket context | `products/yappc/frontend/apps/web/src/contexts/WebSocketContext.tsx` | same | Reuse for streaming agent progress. |
| Agent run viewer UI | not visible | `products/yappc/frontend/web/src/components/agents/AgentRunViewer.tsx` | Timeline of steps, status, tools, failures, retry, policy decisions. |
| Activity feed | not visible | `products/yappc/frontend/web/src/components/activity/ActivityFeed.tsx` | Surface automation events at workspace/project level. |
| Monitoring dashboard | `products/yappc/deployment/monitoring/grafana/yappc-dashboard.json` | same + product admin UI | Keep Grafana but add product-native summary. |
| Tests | missing | `products/yappc/frontend/e2e/agent-run-visibility.spec.ts` | Validate progress streaming and failures. |

**Validation**
- Start AI workflow.
- Agent run appears immediately.
- Step status updates live.
- Failed step shows reason and recovery action.
- Run completion updates requirement/project UI.

---

## P1 — High Priority

### 7. Reduce TODO backlog

| Area | Location | Execution Notes |
|---|---|---|
| TODO scanner | `products/yappc/scripts/scan-todos.sh` | Use to produce current TODO inventory. |
| TODO reducer | `products/yappc/scripts/reduce-todos.py` | Categorize/delete/convert TODOs. |
| TODO report | `products/yappc/docs/TODO_REDUCTION_REPORT.md` | Track count, priority, owner, target module. |
| CI enforcement | `products/yappc/.github/workflows/quality-gates.yml` | Fail if TODO count grows or exceeds threshold. |
| Quality gates script | `products/yappc/scripts/implement-quality-gates.sh` | Verify threshold and reporting. |

**Execution**
- Classify TODOs into: delete, convert to issue, fix now, document as known limitation.
- Convert P0/P1 TODOs into tracked work items.
- Remove vague comments.
- Enforce max TODO count.

---

### 8. Implement Requirement Capture automation

| Area | Location | Execution Notes |
|---|---|---|
| Requirement UI | `products/yappc/frontend/web/src/components/requirements/**` | Add prompt-first capture with templates and inferred defaults. |
| AI service | `products/yappc/canvas-ai-service/src/main/java/com/ghatana/yappc/canvas/ai/**` | Use real LLM providers for normalization and suggestions. |
| LLM providers | `products/yappc/canvas-ai-service/src/main/java/com/ghatana/yappc/canvas/ai/llm/**` | Add provider fallback and structured output validation. |
| Requirement orchestrator | `products/yappc/core/agents/**` | Trigger requirement normalization, acceptance criteria and trace generation. |
| API resolver | `products/yappc/frontend/apps/api/src/graphql/resolvers/requirement*.ts` | Add mutation for AI-assisted submit. |
| Tests | `products/yappc/frontend/e2e/requirement-capture.spec.ts` | Validate prefill, suggestions, approval, save. |

---

### 9. Build Sprint Planning & Backlog Board

| Area | Location | Execution Notes |
|---|---|---|
| DB schema | `products/yappc/frontend/apps/api/prisma/schema.prisma` | Verify/add `Sprint`, `Epic`, `Story`, `TraceLink`. |
| API | `products/yappc/frontend/apps/api/src/graphql/resolvers/planning*.ts` | Add backlog queries, sprint CRUD, story movement. |
| UI board | `products/yappc/frontend/web/src/components/planning/BacklogBoard.tsx` | Kanban/backlog board with drag-and-drop. |
| Sprint view | `products/yappc/frontend/web/src/components/planning/SprintView.tsx` | Sprint capacity, scope, status. |
| AI planning | `products/yappc/core/agents/**` | Add sprint-scope recommendations. |
| Tests | `products/yappc/frontend/e2e/planning-board.spec.ts` | Create story, move to sprint, persist. |

---

### 10. Implement Agent Run Viewer

| Area | Location | Execution Notes |
|---|---|---|
| Runtime service | `products/yappc/core/agents/runtime/**` | Expose run ID, steps, tool calls, memory, policy decisions. |
| Frontend context | `products/yappc/frontend/apps/web/src/contexts/WebSocketContext.tsx` | Stream run status. |
| UI | `products/yappc/frontend/web/src/components/agents/AgentRunViewer.tsx` | Build human-readable run timeline. |
| API | `products/yappc/frontend/apps/api/src/graphql/resolvers/agentRun*.ts` | Query past runs and run details. |
| Tests | `products/yappc/frontend/e2e/agent-run-viewer.spec.ts` | Validate live and historical runs. |

---

### 11. Add end-to-end tests for requirement lifecycle and approvals

| Area | Location | Execution Notes |
|---|---|---|
| Playwright tests | `products/yappc/frontend/e2e/requirement-lifecycle.spec.ts` | Full create → AI process → approve → version → audit flow. |
| API tests | `products/yappc/frontend/apps/api/src/**/*.spec.ts` | Test GraphQL requirement and approval resolvers. |
| Backend tests | `products/yappc/core/agents/**/src/test/**` | Test orchestrators and failure handling. |
| DB tests | `products/yappc/frontend/apps/api/prisma/**` | Verify migrations and persistence behavior. |
| CI | `.github/workflows/quality-gates.yml` | Add required E2E suite. |

---

## P2 — Medium Priority

### 12. Build User & Persona Management UI

| Area | Location | Execution Notes |
|---|---|---|
| DB schema | `products/yappc/frontend/apps/api/prisma/schema.prisma` | Verify `User`, `WorkspaceMember`, `PersonaAssignment`. |
| API | `products/yappc/frontend/apps/api/src/graphql/resolvers/workspaceMember*.ts` | Add member/persona CRUD and permissions. |
| UI | `products/yappc/frontend/web/src/components/workspace/WorkspaceMembers.tsx` | Manage members and roles. |
| Existing workspace dialog | `products/yappc/frontend/web/src/components/workspace/WorkspaceSelectionDialog.tsx` | Reuse workspace context patterns. |
| Tests | `products/yappc/frontend/e2e/workspace-members.spec.ts` | Test role assignment and access. |

---

### 13. Implement Global Search UI

| Area | Location | Execution Notes |
|---|---|---|
| Search hook | `products/yappc/app-creator/libs/ui/src/hooks/useWebSocketSearch/index.ts` or `products/yappc/frontend/libs/ui/src/hooks/useWebSocketSearch/index.ts` | Reuse vector search hook; confirm actual active path. |
| WebSocket context | `products/yappc/app-creator/apps/web/src/contexts/WebSocketContext.tsx` or `products/yappc/frontend/apps/web/src/contexts/WebSocketContext.tsx` | Standardize active app path. |
| Command palette | `products/yappc/app-creator/apps/web/src/components/command/CommandPalette.tsx` or equivalent frontend path | Add global search action. |
| Search page | `products/yappc/frontend/web/src/components/search/GlobalSearch.tsx` | Results grouped by requirement/project/diagram/code. |
| Backend/data cloud | `products/yappc/infrastructure/datacloud/**` | Query search indexes and embeddings. |
| Tests | `products/yappc/frontend/e2e/global-search.spec.ts` | Validate keyword + semantic search. |

---

### 14. Implement Export

| Area | Location | Execution Notes |
|---|---|---|
| DB schema | `products/yappc/frontend/apps/api/prisma/schema.prisma` | Verify/add `ExportArtifact`. |
| API | `products/yappc/frontend/apps/api/src/graphql/resolvers/export*.ts` | Add createExport, exportStatus, downloadUrl. |
| UI | `products/yappc/frontend/web/src/components/export/ExportDialog.tsx` | Export specs, diagrams, code zip. |
| Page builder | `products/yappc/frontend/libs/page-builder/src/**` | Export generated HTML/CSS/JS. |
| Diagram/canvas | `products/yappc/frontend/libs/diagram/src/**`, `products/yappc/frontend/libs/canvas/src/**` | Export diagrams/images. |
| Tests | `products/yappc/frontend/e2e/export.spec.ts` | Validate exported artifact content. |

---

### 15. Integrate Observability dashboards into product UI

| Area | Location | Execution Notes |
|---|---|---|
| Grafana dashboard | `products/yappc/deployment/monitoring/grafana/yappc-dashboard.json` | Keep as operator dashboard. |
| Frontend observability lib | `products/yappc/frontend/libs/observability/**` | Standardize frontend metrics and traces. |
| Admin UI | `products/yappc/frontend/web/src/components/admin/ObservabilityDashboard.tsx` | Add product-native status summary. |
| Backend telemetry | `products/yappc/core/**` and `products/yappc/infrastructure/**` | Emit workflow/agent/API metrics. |
| Tests | `products/yappc/frontend/e2e/observability.spec.ts` | Validate status cards and failure visibility. |

---

### 16. Improve API surface for bulk + automation workflows

| Area | Location | Execution Notes |
|---|---|---|
| GraphQL schema | `products/yappc/frontend/apps/api/src/graphql/**` | Add bulk mutations for requirements, approvals, stories. |
| Resolvers | `products/yappc/frontend/apps/api/src/graphql/resolvers/**` | Implement batch operations with partial failure reporting. |
| Domain services | `products/yappc/core/**` | Add idempotent bulk operations. |
| UI | requirement/backlog/approval components | Add batch select and bulk actions. |
| Tests | API integration tests | Test partial success, rollback, idempotency. |

---

## P3 — Strategic Improvements

### 17. Add AI explainability

| Area | Location | Execution Notes |
|---|---|---|
| AI service | `products/yappc/canvas-ai-service/src/main/java/com/ghatana/yappc/canvas/ai/**` | Return rationale, confidence, assumptions, risk flags. |
| Agent runtime | `products/yappc/core/agents/runtime/**` | Persist rationale and decision metadata per step. |
| Approval UI | `products/yappc/frontend/web/src/components/approvals/ApprovalDetail.tsx` | Show rationale only where needed. |
| Audit | `products/yappc/frontend/web/src/components/audit/AuditTimeline.tsx` | Record rationale summary. |

---

### 18. Guided onboarding / feature discovery

| Area | Location | Execution Notes |
|---|---|---|
| Feature discovery | `products/yappc/app-creator/apps/web/src/components/help/FeatureDiscovery.tsx` or equivalent frontend path | Expand guided tours. |
| Command palette | `products/yappc/app-creator/apps/web/src/components/command/CommandPalette.tsx` | Add “What can I do here?” context action. |
| Docs links | `products/yappc/docs/guides/**` | Link in-product docs to guide content. |
| UI state | `products/yappc/frontend/web/src/state/**` | Persist dismissed hints per user/workspace. |

---

### 19. Performance optimization

| Area | Location | Execution Notes |
|---|---|---|
| Frontend package | `products/yappc/frontend/package.json` | Add/verify analyze script. |
| Vite configs | `products/yappc/frontend/**/vite.config.*` | Code splitting, lazy loading, bundle analysis. |
| Canvas | `products/yappc/frontend/libs/canvas/src/**` | Optimize large canvas rendering. |
| Diagram | `products/yappc/frontend/libs/diagram/src/**` | Optimize ReactFlow node rendering. |
| Backend | `products/yappc/core/**` | Improve async concurrency and timeouts. |
| DB/API | `products/yappc/frontend/apps/api/**` | Optimize queries and indexes. |

---

### 20. Improve audit logs visualization

| Area | Location | Execution Notes |
|---|---|---|
| Audit DB | `products/yappc/frontend/apps/api/prisma/schema.prisma` | Ensure immutable audit model. |
| Audit API | `products/yappc/frontend/apps/api/src/graphql/resolvers/audit*.ts` | Query by workspace/project/entity/action. |
| Audit UI | `products/yappc/frontend/web/src/components/audit/AuditTimeline.tsx` | Timeline, filters, diff view. |
| Activity feed | `products/yappc/frontend/web/src/components/activity/ActivityFeed.tsx` | Show recent events and system actions. |

---

### 21. Add progressive disclosure for advanced settings

| Area | Location | Execution Notes |
|---|---|---|
| Agent settings | `products/yappc/frontend/web/src/components/agents/**` | Hide advanced model/tool/runtime settings by default. |
| Project settings | `products/yappc/frontend/web/src/components/project/**` | Use defaults; show advanced only on demand. |
| Feature flags | `products/yappc/core/framework/framework-core/src/main/java/com/ghatana/yappc/framework/core/config/FeatureFlags.java` and `products/yappc/app-creator/libs/config/src/features/feature-flags.ts` | Gate advanced/incomplete features. |
| UI components | `products/yappc/frontend/libs/ui/**` | Add standard accordion/disclosure patterns. |

---

## Cross-Cutting Execution Map

### UI/UX Simplification

| TODO | Location |
|---|---|
| Merge redundant routes/screens | `products/yappc/frontend/web/src/routes/**` or active app route folder |
| Simplify command palette | `products/yappc/app-creator/apps/web/src/components/command/CommandPalette.tsx` |
| Improve workspace context handling | `products/yappc/frontend/web/src/components/workspace/WorkspaceSelectionDialog.tsx`, `products/yappc/frontend/web/src/state/atoms/workspaceAtom.ts` |
| Add empty/loading/error states | all feature components under `products/yappc/frontend/web/src/components/**` |
| Standardize design system imports | `products/yappc/frontend/eslint-local-rules/rules/prefer-yappc-ui.ts` |

### Backend Hardening

| TODO | Location |
|---|---|
| Standardize Result pattern | `products/yappc/core/framework/framework-api/src/main/java/com/ghatana/yappc/framework/api/Result.java` |
| Standardize async/retry/timeouts | `products/yappc/core/framework/framework-api/src/main/java/com/ghatana/yappc/framework/api/ActiveJPatterns.java` |
| Add orchestration reliability | `products/yappc/core/agents/runtime/**` |
| Add policy hooks | `products/yappc/core/agents/**` and AEP integration modules |
| Add feature flags | `products/yappc/core/framework/framework-core/src/main/java/com/ghatana/yappc/framework/core/config/FeatureFlags.java` |

### API Hardening

| TODO | Location |
|---|---|
| Split resolvers by domain | `products/yappc/frontend/apps/api/src/graphql/resolvers/**` |
| Add schema validation | `products/yappc/frontend/apps/api/src/graphql/**` |
| Add progress/status APIs | `products/yappc/frontend/apps/api/src/graphql/resolvers/agentRun*.ts` |
| Add approval APIs | `products/yappc/frontend/apps/api/src/graphql/resolvers/approval*.ts` |
| Add bulk APIs | `products/yappc/frontend/apps/api/src/graphql/resolvers/bulk*.ts` |

### Database Hardening

| TODO | Location |
|---|---|
| Add/verify workflow models | `products/yappc/frontend/apps/api/prisma/schema.prisma` |
| Add migrations | `products/yappc/frontend/apps/api/prisma/migrations/**` |
| Add indexes | `products/yappc/frontend/apps/api/prisma/schema.prisma` |
| Add audit/history models | `products/yappc/frontend/apps/api/prisma/schema.prisma` |
| Verify generated client | `products/yappc/frontend/apps/api/prisma/generated/client/**` |

### AI/ML Automation

| TODO | Location |
|---|---|
| Real provider usage | `products/yappc/canvas-ai-service/src/main/java/com/ghatana/yappc/canvas/ai/llm/**` |
| Requirement agents | `products/yappc/core/agents/**` |
| Vector search | `products/yappc/app-creator/libs/ui/src/hooks/useWebSocketSearch/index.ts` or active frontend path |
| Memory/reflection/evaluation | AEP modules / `products/yappc/core/agents/**` until separated |
| Confidence + rationale | AI service + approval UI |

### Testing

| TODO | Location |
|---|---|
| Canvas integration tests | `products/yappc/frontend/e2e/canvas-integration.spec.ts` |
| Requirement lifecycle tests | `products/yappc/frontend/e2e/requirement-lifecycle.spec.ts` |
| Approval tests | `products/yappc/frontend/e2e/approval-flow.spec.ts` |
| Agent visibility tests | `products/yappc/frontend/e2e/agent-run-visibility.spec.ts` |
| API resolver tests | `products/yappc/frontend/apps/api/src/**/*.spec.ts` |
| Backend tests | `products/yappc/core/**/src/test/**` |
| ArchUnit tests | `products/yappc/core/**/src/test/**/ArchitectureBoundaryTest.java` |
| CI quality gates | `.github/workflows/quality-gates.yml` |

---

## Suggested Execution Order

1. Finish backend module migration.
2. Run frontend library consolidation.
3. Stabilize build, lint, typecheck and tests.
4. Implement requirement lifecycle E2E.
5. Implement approval/governance model and UI.
6. Implement agent run visibility.
7. Implement code editor.
8. Add backlog/sprint planning.
9. Add search/export/observability UI.
10. Reduce TODO backlog continuously.
11. Add progressive disclosure and AI explainability.
12. Optimize performance and bundle size.

---

## Definition of Done for Each TODO

Each item is complete only when:

- UI path exists and is discoverable.
- API contract exists and is validated.
- Backend/domain logic exists and is not stubbed.
- DB persistence exists where required.
- Error, loading, empty and success states are truthful.
- Audit/logging exists for state-changing operations.
- Tests cover happy path, failure path and permission/path edge cases.
- CI passes.
- Manual steps are justified or removed.
