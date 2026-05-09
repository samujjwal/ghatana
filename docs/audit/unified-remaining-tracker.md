# Unified Remaining Tracker (Data Cloud + YAPPC)

Last updated: 2026-05-08
Source docs:
- docs/audit/todo.md
- products/yappc/docs/audits/yappc-todos.md

Purpose:
- Single execution tracker for all remaining (not complete) items across both product todo documents.
- Deduplicated where the same Data Cloud work appears both in progress notes and numbered backlog entries.

Status key:
- TODO: not started in current pass
- PARTIAL: started, remaining scope/tests still open

## Progress Updates (2026-05-08)

- YAPPC P0-003 advanced: added workspace-scoped project operations to canonical client (`yappcApi.projects.getScoped`, `yappcApi.projects.updateScoped`) so project reads/writes can be enforced against backend workspace context instead of route-only project id.
- Migrated project-critical routes/services to prefer scoped fetches when workspace context is available: `project/_shell`, `project/canvas`, `project/index`, `project/settings`, and phase cockpit project snapshot path (`usePhaseCockpitData` -> `fetchProjectSnapshot(projectId, workspaceId)`).
- Project settings save flow now uses canonical typed scoped updater (`projects.updateScoped`) and removes ad-hoc route-local PATCH transport.
- Validation evidence for this delta:
	- TypeScript compile diagnostics report no errors in all touched files (`src/lib/api/client.ts`, `src/routes/app/project/_shell.tsx`, `src/routes/app/project/canvas.tsx`, `src/routes/app/project/index.tsx`, `src/routes/app/project/settings.tsx`, `src/routes/app/project/_phaseCockpit.tsx`, `src/services/phase/PhaseCockpitDataService.ts`, `src/services/phase/usePhaseCockpitData.ts`).
	- Focused project route suite reruns now captured and passing: `src/routes/app/project/__tests__/settings.test.tsx` (`3/3`) and `src/routes/app/project/__tests__/index.test.tsx` + `src/routes/app/project/__tests__/shell.test.tsx` + `src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx` (`20/20`).

- YAPPC P0-003 advanced further: migrated scope-sensitive project inclusion flows in `useWorkspaceData` from raw REST transport to canonical typed client methods (`yappcApi.projects.include`, `yappcApi.projects.availableForInclusion`) and added these APIs to the canonical client surface.
- Validation evidence for this delta:
	- TypeScript compile diagnostics report no errors in touched files (`src/lib/api/client.ts`, `src/hooks/useWorkspaceData.ts`).
	- Focused hook regression passed: `src/hooks/__tests__/useWorkspaceData.test.tsx` (`5/5`).

- YAPPC P0-003 advanced again: migrated additional workspace/project flows in `useWorkspaceData` from raw fetch transport to canonical typed client methods (`workspaces.list/get/create/suggestName/refreshAiDetails`, `projects.create/suggestName/setupSuggestion/refreshAiDetails`) while preserving existing response normalization and mounted envelope compatibility.
- YAPPC P0-003 compatibility hardening: preserved existing UI-facing error semantics for workspace list failures (503 service unavailable and non-JSON HTML fallback) after transport migration to avoid regression in user-observable diagnostics.
- Validation evidence for this delta:
	- Focused regressions passed: `src/hooks/__tests__/useWorkspaceData.test.tsx` + `src/routes/app/project/__tests__/settings.test.tsx` (`8/8`).
	- Consolidated P0-003 regression bundle passed: `src/hooks/__tests__/useWorkspaceData.test.tsx` + `src/routes/app/project/__tests__/index.test.tsx` + `src/routes/app/project/__tests__/shell.test.tsx` + `src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx` + `src/routes/app/project/__tests__/settings.test.tsx` (`28/28`).
	- Raw workspace/project transport inventory in `web/src` shows no direct `fetch` usage outside approved boundaries (`src/lib/api/client.ts`, `src/services/canvas/api/CanvasAPIClient.ts`).

- YAPPC P0-001 advanced: migrated rate-limit raw REST usage to typed client paths (`yappcApi.rateLimit.*`) across `useRateLimit` and `ThrottleAlertBanner`.
- Added canonical typed rate-limit API domain in `src/lib/api/client.ts` to prevent new ad-hoc fetch usage for this surface.
- Focused validation passed: `RateLimitStatusWidget.test.tsx` + `project-voice-ratelimit.test.tsx` (33/33).
- YAPPC P0-001 advanced further: migrated auth/profile raw REST paths to typed client methods (`yappcApi.auth.loginSession`, `yappcApi.auth.updateProfile`, `yappcApi.userProfile.get/update`) across `AuthService`, `pages/auth/ProfilePage`, and `pages/settings/ProfilePage`.
- Added typed client domains for billing/operations/collaboration/settings and migrated corresponding pages (`BillingPage`, `OnCallPage`, `ServiceMapPage`, `SSOCallbackPage`, `SettingsPage`, `ActivityFeedPage`, `TeamHubPage`, `MessagesPage`, `CalendarPage`).
- Added typed AI client methods (`ai.phaseGateReadiness`, `ai.suggestArtifacts`) and migrated `usePhaseGate` + `ArtifactSuggestionService` off raw fetch.
- Validation evidence for this delta:
	- `src/services/auth/__tests__/AuthService.test.ts` + `src/routes/app/admin/__tests__/billing-teams-gate.test.tsx` (`12/12`).
	- `src/routes/__tests__/auth-routes.test.tsx` + `src/routes/__tests__/settings.test.tsx` (`23/23`).
	- `src/hooks/__tests__/usePhaseGate.test.ts` + `src/components/ai/__tests__/ai-components.test.tsx` (`46/46`, with pre-existing React act warnings in hook test output).
- YAPPC P0-001 advanced again: migrated anomaly endpoint cluster in `useAnomalyDetection` (`/api/anomalies*`, baselines, threat-intelligence, risk-scores) to `yappcApi.anomalies.*` with tenant-header preservation.
- Added typed client namespaces for `canvas.save` and `errorReporting.report`; migrated `useAICommand` canvas persistence and `ErrorBoundary` production error-reporting calls off direct fetch.
- Validation evidence for this delta:
	- `src/__tests__/hooks/useAnomalyDetection.test.ts` + `src/__tests__/components/anomaly/AnomalyDetectionDashboard.test.tsx` + `src/__tests__/components/anomaly/AnomalyDetailModal.test.ts` (`52/52`).
	- `src/components/ai/__tests__/AICommandBar.test.tsx` + `src/components/ai/__tests__/ai-components.test.tsx` (`59/59`).
	- `src/components/canvas/__tests__/CanvasErrorBoundary.test.tsx` + `src/components/shared/__tests__/shared-components.test.tsx` (`18/18`).
- YAPPC P0-001 raw `/api` fetch usage is now constrained to intentional transport boundaries only: canonical client internals (`lib/api/client.ts`), canvas transport adapter (`services/canvas/api/CanvasAPIClient.ts`), plus one commented legacy line in `useWorkflows`.
- YAPPC P0-001 lint guard implemented in `products/yappc/frontend/eslint.config.js`: `no-restricted-syntax` now blocks direct `fetch('/api...')` in `web/src/**/*`, with explicit allowlist only for canonical transport boundary files (`web/src/lib/api/client.ts`, `web/src/services/canvas/api/CanvasAPIClient.ts`).
- YAPPC P0-002 advanced: preview runtime now fails closed unless a valid server-issued `session` token is present, or explicit dev-only mode is requested (`mode=dev`) and enabled via `VITE_FEATURE_PREVIEW_DEV_MODE=true` in dev.
- YAPPC P0-002 supporting UX alignment: `LivePreviewPanel` no longer mounts tokenless `/preview/builder` by default; it now issues secure sessions via `previewContext`, supports explicit dev-mode URL only when enabled, and surfaces secure-session-required messaging otherwise.
- YAPPC P0-005 advanced: removed hardcoded phase cockpit tier/flag assumptions in `usePhaseCockpitData`; cockpit config now derives `tenantTier` and `enabledPhaseFlags` from backend project snapshots (with explicit fallback defaults only when backend values are absent).
- Added normalization support for backend variants (`tenantTier`/`tier`/`subscriptionTier`, `enabledPhaseFlags`/`featureFlags`/`enabledFlags`) in phase project snapshot parsing.
- YAPPC P0-008 advanced: introduced canonical typed `yappcApi.pageArtifacts` domain in `src/lib/api/client.ts` for page-artifact save/load and artifact-graph ingest APIs.
- Rewired `HttpPageArtifactPersistenceAdapter` to use canonical `yappcApi.pageArtifacts` by default (production path) while preserving custom transport override behavior for tests/backcompat and retaining existing conflict/authorization/validation/local-fallback semantics.
- YAPPC P0-003/P0-008 hardening: added explicit scope-consistency validation in page-artifact save path to reject artifact-graph payloads when `artifactGraph.projectId` does not match request scope headers (`X-Project-ID`), preventing silent scope drift at the client boundary.
- YAPPC P0-006 advanced: user-facing copy in key entry surfaces now uses outcome-oriented wording instead of direct "AI assistant" phrasing (updated auth/public layout messaging, workflow guidance/tips, next-best-action fallback description, and keyboard shortcut category label).
- YAPPC P0-006 advanced further: removed explicit AI-assistant phrasing from additional user-facing surfaces in empty states, contextual canvas help tips, and canvas onboarding copy while preserving internal model/provenance terminology.
- YAPPC P0-006 advanced again: onboarding journey and workflow evidence panel now use outcome-oriented “Guided Suggestions / Suggested Improvement” terminology in user-visible titles, descriptions, tab labels, and state messages.
- YAPPC P0-006 advanced further: keyboard shortcut guidance and lifecycle navigation copy now use outcome-oriented phrasing (e.g., “Accept suggested improvement”, “Quick fix (guided)”, and “Guided insights and recommendations”).
- YAPPC P0-006 advanced further: additional user-facing labels now use outcome-oriented wording in contextual suggestion and insight surfaces (`Suggested Improvements`, `Proactive insights`) and in placeholder assistant empty-state guidance (`Guided Assistant`).
- YAPPC P0-006 advanced further: canvas suggestion surfaces now use outcome-oriented wording across empty-state headers, notification toasts, ghost-node labels/aria text, and suggestion panel titles (`Suggested Improvements`, `Suggestion(s) Ready!`, `Suggested improvement`).
- YAPPC P0-006 advanced further: approval enrichment UI now uses outcome-oriented phrasing in detail surfaces (`Suggested rationale`, `Enrichment Details`, and `Enrichment suggestion` aria labels).
- YAPPC P0-006 advanced further: canvas toolbar/overlay entry labels now use outcome-oriented terminology (`Guided Assistant`, `Suggested improvements`, `Suggested Improvements`).
- YAPPC P0-006 advanced further: contextual help + execution/action surfaces now use updated assist wording and anchor matching (`Assist`, `Guided Assist`), and contextual-help tip anchoring now targets the new toolbar labels (`Suggested improvements` / `Guided Assistant`) instead of stale AI-title selectors.
- YAPPC P0-006 advanced further: lifecycle traceability panel guidance actions/results now use outcome-oriented wording (`Analyze Guidance`, `Guided Analysis`) instead of direct AI phrasing.
- YAPPC P0-006 advanced further: lifecycle authoring panels now use outcome-oriented action labels across assist/critique actions (`Guided Assist`, `Guided Critique`) in Improve, Threat Model, ADR, Requirements, and UX Spec flows.
- YAPPC P0-006 large-batch continuation completed: synchronized outcome-oriented copy updates across command input, quality dashboard, dashboard placeholder, inline code fixes, validation assist action, intent assist actions, phase helper guidance, lifecycle/context assistant tabs, shared suggestion headers, canvas empty-state assistant cards, and assistant modal suggestion headers.
- YAPPC P0-006 large-batch continuation scope: 20 files touched in one continuous slice (source + tests), including `AICommandBar`, `AIQualityDashboard`, `DashboardView`, `InlineCodePanel`, `ValidationSummaryPanel`, `IdeaBriefForm`, `ResearchPackEditor`, `PhaseGuidedFlow`, `ContextDrawer`, `CanvasUI`, `AIAssistantModal`, and `AISuggestionPanel` surfaces.
- YAPPC P0-006 large-batch continuation completed: synchronized additional outcome-oriented copy updates across unified canvas toolbar guided-assist labels, onboarding guidance language, keyboard shortcut descriptions, phase readiness dialog labels, project suggestion context wording, notification/agent status labels, and ghost-node suggestion CTA text.
- YAPPC P0-006 large-batch continuation scope: 15 files touched in one continuous slice (source + tests), including `UnifiedCanvasToolbar`, `IntelligentOnboarding`, `useKeyboardShortcuts`, `PhaseGateDialog`, `CreateProjectDialog`, `AgentActionPanel`, `GhostNodes`, `PersonaContext`, and onboarding route documentation surfaces.
- YAPPC P0-006 i18n readiness advanced: added a typed frontend i18n foundation (`I18nProvider`, keyed message catalog, interpolation support) and wired it at app root so migrated UI labels resolve via translation keys instead of hardcoded literals.
- i18n key migration completed for high-visibility user-facing labels in `useKeyboardShortcuts`, `UnifiedCanvasToolbar`, `PhaseGateDialog`, `IntelligentOnboarding`, and `CreateProjectDialog`, including button labels, tooltips, phase-gate copy, onboarding copy, and project-creation modal text.
- Validation evidence for this delta:
	- `src/components/studio/__tests__/LivePreviewPanel.test.tsx` + `src/routes/__tests__/preview-builder-security.test.tsx` (`17/17` passed; existing React act warnings remain in stderr).
	- Raw-fetch inventory check confirms only intentional boundaries remain: `src/lib/api/client.ts`, `src/services/canvas/api/CanvasAPIClient.ts`, and a commented legacy example in `src/hooks/useWorkflows.ts`.
	- `src/services/phase/__tests__/PhaseBuilders.test.ts` + `src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx` + `src/routes/app/project/__tests__/PhaseStatusPanels.test.tsx` (`34/34` passed).
	- `src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts` + `src/components/canvas/page/__tests__/pageArtifactDocument.test.ts` (`34/34` passed) and canonical transport regression assertion added (`default HTTP adapter path uses canonical typed client transport`).
	- `src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts` (`22/22` passed) after adding scope-mismatch rejection regression (`project scope mismatch` validation).
	- `src/hooks/__tests__/useNextBestAction.test.ts` + `src/routes/__tests__/dashboard.test.tsx` + `src/__tests__/routes.spec.ts` (`21/21` passed).
	- Copy hardening regressions passed: `src/components/onboarding/__tests__/EndToEndOnboarding.test.tsx` + `src/components/workflow/__tests__/evidence-panel.test.tsx` (`7/7`).
	- Additional copy hardening rerun passed after onboarding/evidence-panel label updates: `src/components/onboarding/__tests__/EndToEndOnboarding.test.tsx` + `src/components/workflow/__tests__/evidence-panel.test.tsx` (`7/7`).
	- Additional copy hardening regressions passed for keyboard/lifecycle surfaces: `src/components/keyboard/__tests__/keyboard-shortcuts-manager.test.tsx` + `src/components/lifecycle/__tests__/LifecycleNavigation.test.tsx` (`7/7`).
	- Additional copy hardening regressions passed for suggestions/insights/assistant placeholder surfaces: `src/components/ai/__tests__/ContextualSuggestions.test.tsx` + `src/components/ai/__tests__/InsightPanel.test.tsx` + `src/components/placeholders/__tests__/AIChatInterface.test.tsx` (`40/40`).
	- Additional copy hardening regressions passed for canvas suggestion surfaces: `src/components/canvas/__tests__/canvas-utility.test.tsx` + `src/components/canvas/ai/__tests__/canvas-ai.test.tsx` (`52/52`).
	- Additional copy hardening regression passed for approvals enrichment surface: `src/components/approvals/__tests__/ApprovalDetail.test.tsx` (`7/7`) after aligning a pre-existing amber/warning token-class assertion with current design-system class naming.
	- Additional copy hardening regression passed for toolbar/overlay access labels: `src/components/canvas/toolbar/__tests__/UnifiedCanvasToolbar.test.tsx` (`42/42`).
	- Additional copy hardening regressions passed for action/workflow step surfaces: `src/components/actions/__tests__/contextual-toolbar.test.tsx` + `src/components/workflow/steps/__tests__/workflow-steps.test.tsx` (`26/26`), with a pre-existing design-system select hydration warning still present in `workflow-steps.test.tsx` stderr.
	- Additional copy hardening regression passed for lifecycle traceability guidance surface: `src/components/canvas/lifecycle/__tests__/TraceabilityPanel.test.tsx` (`16/16`).
	- Additional copy hardening regression bundle passed for lifecycle authoring panels: `src/components/canvas/lifecycle/__tests__/ImprovePanel.test.tsx` + `src/components/canvas/lifecycle/__tests__/ThreatModelPanel.test.tsx` + `src/components/canvas/lifecycle/__tests__/AdrPanel.test.tsx` + `src/components/canvas/lifecycle/__tests__/RequirementsPanel.test.tsx` + `src/components/canvas/lifecycle/__tests__/UxSpecPanel.test.tsx` (`101/101`) after updating one stale RequirementsPanel test selector to match the new guided label.
	- Large-batch consolidated regression passed: `src/components/ai/__tests__/AICommandBar.test.tsx` + `src/components/ai/__tests__/AIQualityDashboard.test.tsx` + `src/components/dashboard/__tests__/DashboardView.test.tsx` + `src/components/canvas/__tests__/InlineCodePanel.test.tsx` + `src/components/validate/__tests__/ValidationSummaryPanel.test.tsx` + `src/components/intent/__tests__/intent-forms.test.tsx` + `src/components/journey/__tests__/PhaseGuidedFlow.test.tsx` (`109/109`), with pre-existing React DOM prop warnings in `PhaseGuidedFlow` stderr and no test failures.
	- Large-batch consolidated regression passed: `src/components/canvas/UnifiedCanvasToolbar.test.tsx` + `src/components/journey/__tests__/IntelligentOnboarding.test.tsx` + `src/hooks/__tests__/useKeyboardShortcuts.test.ts` + `src/components/dialogs/__tests__/PhaseGateDialog.test.tsx` + `src/components/workspace/__tests__/CreateProjectDialog.test.tsx` (`73/73`), with pre-existing React DOM prop warnings in `IntelligentOnboarding` stderr and no test failures.
	- i18n-readiness regression rerun passed for the migrated surfaces: `src/components/canvas/UnifiedCanvasToolbar.test.tsx` + `src/components/journey/__tests__/IntelligentOnboarding.test.tsx` + `src/hooks/__tests__/useKeyboardShortcuts.test.ts` + `src/components/dialogs/__tests__/PhaseGateDialog.test.tsx` + `src/components/workspace/__tests__/CreateProjectDialog.test.tsx` (`73/73`) after translation-key migration.
	- TypeScript/problem diagnostics on all 18 touched production/test files in this batch report no compile errors.

## Data Cloud Remaining Items

### Partial (in progress)

- [ ] DC-P1.8 (PARTIAL): Enforce collection-scoped EntityStore methods across all providers/integration suites and finish full integration validation.
- [ ] DC-P1.12 (PARTIAL): Complete Runtime Truth migration; remove compatibility aliases `/api/v1/capabilities*` after full consumer cutover and removal gate/date.
- [ ] DC-P1.16 (PARTIAL): Align all AI advisory/fail-closed semantics across docs, operator UX, and runtime boundary copy.
- [ ] DC-P1.18 (PARTIAL): Complete profile-posture parity checks and finalize non-local durability posture in docs/UI/runtime truth.
- [ ] DC-P1.22 (PARTIAL): Move remaining event-query filtering/pagination work to store-native paths (avoid in-memory merge for high-cardinality scenarios).
- [ ] DC-P1.23 (PARTIAL): Validate tail-from-latest parity in Docker-backed CI runs for Kafka/Warm-tier providers.
- [ ] DC-P1.24 (PARTIAL): Extend tail polling/config telemetry parity and add load assertions for subscriber limits/lag.

### TODO (not started / still open)

- [ ] DC-9: Harden H2 query field validation for JSON path usage (strict allowlist/regex + rejection tests).
- [ ] DC-10: Unify query model across `DataCloudClient.Query`, `EntityStore.QuerySpec`, OpenAPI, UI, and SDK.
- [ ] DC-11: Implement or explicitly reject projections/consistency/freshness/search in every entity provider (no silent ignore).
- [ ] DC-13: Enforce plane dependency boundaries in CI (ArchUnit/Gradle forbidden dependency direction checks).
- [ ] DC-14: Split Data Cloud-specific platform/kernel abstractions from shared modules with dependency inventory and migration.
- [ ] DC-15: Enforce Action Plane/AEP boundary at build time (no hidden AEP implementation dependency).
- [ ] DC-17: Introduce canonical `TenantWorkspaceContext` and propagate across API/storage/audit/events/AI/plugins.
- [ ] DC-19: Define explicit transactional or partial semantics for entity batch writes; enforce/test accordingly.
- [ ] DC-20: Promote core event envelope fields from opaque headers to first-class queryable storage fields.
- [ ] DC-25: Complete Context Plane implementation beyond placeholder boundary.
- [ ] DC-26: Implement canonical Data Quality + Trust backend contracts/services (not UI-only).
- [ ] DC-27: Complete Feature Store + Model Registry production lifecycle flow with tenant isolation and runtime truth states.
- [ ] DC-28: Make plugin/connector lifecycle production-grade (trust/signature/sandbox/audit/rollback).
- [ ] DC-29: Ensure UI consumes generated clients or typed adapters only; remove drift-prone hand-maintained shapes.
- [ ] DC-30: Add OpenAPI runtime route parity gate (runtime inventory vs contract, CI fail on drift).
- [ ] DC-31: Add `aep.yaml` vs `action-plane.yaml` equivalence gate until compatibility contract retirement.
- [ ] DC-32: Align runtime truth state enum/name parity across docs/OpenAPI/server/UI.
- [ ] DC-33: Add release gate rejecting stale audit/TODO target refs.
- [ ] DC-34: Run full Data Cloud Java suite on current head and convert failures into concrete remediation tasks.
- [ ] DC-35: Run full Data Cloud UI typecheck/test/e2e suite on current head and convert failures into concrete remediation tasks.
- [ ] DC-36: Build provider conformance matrix for all `EntityStore` and `EventLogStore` implementations.

## YAPPC Remaining Items

### P0 (must fix before end-to-end production claim)

- [ ] YAPPC-P0-001 (PARTIAL): Replace all remaining raw REST fetch usage with canonical typed `yappcApi`/domain typed clients + lint guard.
- [ ] YAPPC-P0-002: Fix live preview session issuance/usage so `/preview/builder` always has valid server token or explicit dev-only mode.
- [ ] YAPPC-P0-003: Make workspace/project/artifact/page-builder state backend-authoritative with strict scope/auth enforcement.
- [ ] YAPPC-P0-004: Convert generic phase cockpit behavior into phase-specific contracts/actions/blockers/evidence/review gates.
- [ ] YAPPC-P0-005: Replace hardcoded phase flags/tier with backend entitlement/capability signals.
- [ ] YAPPC-P0-006: Finish user-facing AI wording migration to outcome-oriented terminology.
- [ ] YAPPC-P0-007: Remove mixed local/ad-hoc primitives and hardcoded styles; enforce design-system tokens/components.
- [ ] YAPPC-P0-008: Move page artifact save/load/graph ingest to canonical typed API services with atomic persistence semantics.
- [ ] YAPPC-P0-009: Harden preview trust boundary (CSP/sandbox/session/origin/message validation).
- [ ] YAPPC-P0-010: Add real full-loop Playwright E2E for YAPPC product journey.

### P1 (hardening + architecture cleanup)

- [ ] YAPPC-P1-001: Add runtime schema validation for API responses and persisted documents.
- [ ] YAPPC-P1-002: Enforce command-only builder mutation discipline.
- [ ] YAPPC-P1-003: Persist audit events for undo/redo (not telemetry-only).
- [ ] YAPPC-P1-004: Decompose `PageDesigner.tsx` into focused modules.
- [ ] YAPPC-P1-005: Replace JSON text-area heavy inspector controls with registry-driven typed controls.
- [ ] YAPPC-P1-006: Enforce privacy/telemetry/review/a11y policies at command/API/runtime layers.
- [ ] YAPPC-P1-007: Improve compiler/decompiler fidelity (tree, slots, props, bindings, residuals, provenance).
- [ ] YAPPC-P1-008: Promote residual islands into first-class review/promotion workflow.
- [ ] YAPPC-P1-009: Harden plugin runtime guard against async/network/global escape paths.
- [ ] YAPPC-P1-010: Complete reliable bidirectional preview sync (selection/hover/viewport/theme/locale/validation).
- [ ] YAPPC-P1-011: Upgrade conflict resolution to schema-validated semantic merge operations.
- [ ] YAPPC-P1-012: Move next-best-action derivation to backend typed contract.
- [ ] YAPPC-P1-013: Connect Generate cockpit to real diff/apply/reject/rollback with provenance.
- [ ] YAPPC-P1-014: Make Run -> Observe handoff fully backend-backed.
- [ ] YAPPC-P1-015: Make multi-page import/generation product-level graph model first-class.
- [ ] YAPPC-P1-016: Formalize versioned import/export schema + migrations.
- [ ] YAPPC-P1-017: Standardize product terminology and propagate through types/routes/docs/tests.

### P2 (UX/perf/extensibility/maintainability)

- [ ] YAPPC-P2-001: Reduce cockpit cognitive load with consistent information hierarchy.
- [ ] YAPPC-P2-002: Complete empty/loading/error/recovery state coverage.
- [ ] YAPPC-P2-003: Make canvas toolset phase-aware via backend policy + `PhaseCanvasConfig`.
- [ ] YAPPC-P2-004: Replace stringly typed DnD payloads with validated typed payloads.
- [ ] YAPPC-P2-005: Complete keyboard-only builder operations.
- [ ] YAPPC-P2-006: Add design-system lint rules for raw controls/tokens/styles.
- [ ] YAPPC-P2-007: Add large-canvas/page-builder performance budgets and tests.
- [ ] YAPPC-P2-008: Add frontend observability spans/events for major flows.
- [ ] YAPPC-P2-009: Add deterministic multi-user collaboration/locking/review flows.
- [ ] YAPPC-P2-010: Publish canonical YAPPC architecture/developer implementation docs.

## Cross-Product Execution Order (single queue)

1. P0 security/correctness gates first: DC-P1.12/DC-P1.16/DC-P1.18 + YAPPC-P0-001/P0-002/P0-008/P0-009/P0-010.
2. Contract and boundary governance next: DC-10/13/15/30/31/32 + YAPPC-P0-003/P0-005/P1-001/P1-012.
3. Runtime and persistence hardening: DC-P1.22/P1.23/P1.24/DC-19/DC-20 + YAPPC-P1-002/P1-003/P1-011/P1-013/P1-014.
4. Platform capability completion: DC-25/26/27/28 + YAPPC-P1-004/P1-005/P1-006/P1-007/P1-008/P1-015/P1-016.
5. UX/performance/docs closure: DC-29/DC-33/DC-34/DC-35/DC-36 + YAPPC-P0-006/P0-007/P2-*.
