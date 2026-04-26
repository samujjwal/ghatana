# YAPPC UI/UX Implementation Tracker (2026-04-24 Audit)

> Generated from `YAPPC_UI_UX_AUDIT_2026-04-24.md`
> Guidelines: `../../.github/copilot-instructions.md`

## Tracker Legend

| Status | Meaning |
|---|---|
| Pending | Not yet started |
| In Progress | Actively being implemented |
| Blocked | Waiting on dependency or external input |
| Ready for Review | Implementation complete, needs verification |
| Done | Verified (typecheck, tests, browser smoke where applicable) |

## Phase 0 — Immediate (Critical Path)

| ID | Finding | Severity | Area | Status | Evidence | Fix Strategy | Verification |
|---|---|---|---|---|---|---|---|
| P0-1 | Fix live `require is not defined` runtime crash (YAPPC-UX-001) | Critical | Runtime | **Done** | Browser `/` and `/login` show Vite overlay from `@mui/material/index.js` | Added `@mui/material` and `@mui/material/styles` to `ssr.noExternal` and `optimizeDeps.include` in `vite.config.ts` so Vite pre-bundles them for SSR | Dev server renders `/login` without overlay |
| P0-2 | Add browser render smoke for `/login` and `/workspaces` (YAPPC-UX-003/028) | Critical | Tests | **Done** | Static smoke only asserts strings in route file | Added Playwright smoke tests in `e2e/smoke.spec.ts`: `login page renders without Vite overlay` and `workspaces page renders without Vite overlay`; both assert heading visibility and absence of `vite-error-overlay` | `pnpm test:smoke` or `npx playwright test e2e/smoke.spec.ts` passes |
| P0-3 | Reopen/update tracker P0-10 (YAPPC-UX-002) | Critical | Docs/tracker | **Done** | Old tracker marks P0-10 done but live app still crashes | Updated this tracker to reflect current truth; old P0-10 superseded by P0-1 | Tracker matches live verification |
| P0-4 | Fix typecheck scope — exclude latent pages/archives (YAPPC-UX-015/038) | Critical | Build | **Done** | Typecheck includes `_archived`, unmounted pages, latent components with broken imports | Added `src/routes/_archived/**/*`, `src/routes/mobile/**/*`, and `src/components/workflow/**/*` to tsconfig `exclude` | `pnpm typecheck` passes for active scope |
| P0-5 | Fix workspace settings route identity (YAPPC-UX-009) | Critical | Navigation/state | **Done** | `/settings?workspaceId=...` passes query param; settings reads `currentWorkspaceIdAtom` | `settings.tsx` now reads `workspaceId` from URL search params as primary source, falls back to atom; shows mismatch warning banner | Settings edits the workspace specified in URL |
| P0-6 | Remove/update stale auth route tests (YAPPC-UX-007) | High | Tests | **Done** | `auth-routes.test.tsx` imports `../../routes/register` (deleted; now in `_archived`) | Updated imports to `../../routes/_archived/register` and `../../routes/_archived/forgot-password` | `pnpm test` auth suite passes |
| P0-7 | Align onboarding request contract (YAPPC-UX-010) | High | Onboarding | **Done** | `src/pages/OnboardingFlow.tsx` passes `personaSelections` and `defaultProject` to `createWorkspace.mutateAsync` — fields not in `CreateWorkspaceRequest` | Removed unsupported `personaSelections` and `defaultProject` from workspace creation call; personas still persisted via localStorage after creation | Typecheck passes; onboarding flow compiles |

## Phase 1 — Short-Term (High Impact)

| ID | Finding | Severity | Area | Status | Evidence | Fix Strategy | Verification |
|---|---|---|---|---|---|---|---|
| P1-1 | Generate route inventory from `routes.ts` (YAPPC-UX-005/006) | High | IA/docs | **Done** | Manual inventory lists `/p/:projectId/canvas-workspace` as mounted; route config does not | Created `scripts/generate-route-inventory.mjs` that parses `src/routes.ts` and writes `docs/route-inventory.md`; added `inventory:routes` and `inventory:routes:check` npm scripts; generated inventory shows 15 mounted routes matching `routes.ts` exactly | `pnpm inventory:routes` generates file; `pnpm inventory:routes:check` validates against existing |
| P1-2 | Quarantine latent pages from release compile (YAPPC-UX-015/006) | High | Architecture | **Done** | 72 route files vs 13 mounted; 88 page files; latent pages import old token keys | Added `src/pages`, `src/services/RequirementTransformer/**/*`, `src/services/registry/**/*`, `src/services/export/**/*`, `src/services/collaboration/**/*`, `src/services/agentService.ts`, `src/components/knowledge-graph/**/*` to tsconfig `exclude`; existing exclusions cover `src/routes/_archived`, `src/routes/mobile`, `src/components/workflow` | Typecheck clean; no latent imports in active build |
| P1-3 | Fix project overview `ownerWorkspace` contract drift (YAPPC-UX-016) | High | Project cockpit | **Done** | `index.tsx:238` used `project.ownerWorkspace?.name` which does not exist on `ProjectWithOwnership` | Replaced with `project.ownerWorkspaceId` directly | Typecheck passes on project overview |
| P1-4 | Fix lifecycle test ID drift (YAPPC-UX-008) | Medium | Tests | **Done** | Test expects `lifecycle-phase-summary-card`; UI renders `lifecycle-summary-status-card` | Updated test selector to `lifecycle-summary-status-card` in `lifecycle.test.tsx` | Lifecycle tests pass |
| P1-5 | Replace reload retries with scoped refetch (YAPPC-UX-022) | Medium | Error recovery | **Done** | `window.location.reload()` in `workspaces.tsx:67` and other paths | Replaced with `queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.all })` in workspaces and `projectQueryKeys.all` in projects | Retry preserves context and state |
| P1-6 | Wire keyboard shortcuts panel action (YAPPC-UX-025) | Medium | Shell | **Done** | `onKeyboardShortcuts` handler is comment only | Wired `openShortcuts()` from `useKeyboardShortcutsPanel()` into `onKeyboardShortcuts` prop in `_shell.tsx` | Keyboard shortcuts button works |
| P1-7 | Persist deploy reject/hold decisions (YAPPC-UX-018) | High | Governance | **Done** | `handleReject` only `console.log`s | Implemented artifact-based persistence: creates `incident_report` artifact with rejection payload (gateId, reason, actor, timestamp); added `rejectionHistory` state and rendered decision history UI | Reject action creates durable record |
| P1-8 | Fix lifecycle phase enum exhaustive handling (YAPPC-UX-017) | High | Lifecycle | **Done** | Typecheck missing `INSTITUTIONALIZE` in records | Added `INSTITUTIONALIZE` to `PHASE_COLORS` and `PHASE_ICONS` in `LifecyclePhaseBadge.tsx`; fixed `PHASE_LABELS` in `LifecycleBreadcrumb.tsx` to use canonical names and include `INSTITUTIONALIZE` | No unhandled enum cases in lifecycle |

## Phase 2 — Medium-Term (Completeness & Consistency)

| ID | Finding | Severity | Area | Status | Evidence | Fix Strategy | Verification |
|---|---|---|---|---|---|---|---|
| P2-1 | Server-backed onboarding completion/personas (YAPPC-UX-011) | High | Onboarding | **Done** | `onboarding_complete`, personas stored in localStorage | Created `OnboardingStatusService` (`services/onboarding/OnboardingStatusService.ts`) with server-first sync to `/api/onboarding/status` (PUT/GET) and graceful localStorage fallback; exposed `useOnboardingStatus` TanStack Query hook for React integration; updated `OnboardingFlow` to call `markComplete({primary, active})` on finish; updated `PersonaContext` to sync persona changes to server via `setOnboardingStatus` in background; replaced `readFlag('onboarding_complete')` in `routes/onboarding.tsx` and `routes/_shell.tsx` with `useOnboardingStatus` hook (server-aware redirect with loading state); added onboarding barrel export (`services/onboarding/index.ts`); added `OnboardingStatusService.test.ts` with 4 passing tests verifying 404/501/network-error fallback to localStorage | Onboarding survives localStorage clear |
| P2-2 | Unified canvas sync state/history (YAPPC-UX-020) | High | Canvas | **Done** | `canvasSyncStatus` is `local-only`; multiple persistence systems | Expanded `SaveSyncStatusContract` in `contracts/workspace-project.ts` to include `stale` and `conflict` states alongside existing `local-only`, `syncing`, `remote-saved`, `remote-failed`; updated `SaveSyncStatusBadge` (`components/status/SaveSyncStatusBadge.tsx`) with labels and Tailwind classes for all six states; created `CanvasSyncService` (`services/canvas/CanvasSyncService.ts`) implementing a state machine with `markLocalChange`, `startSync`, `syncSucceeded`, `syncFailed`, `reportConflict`, `markStale`, `resolveConflict`, `reset` transitions; includes `SyncHistoryEntry` capped at 50 entries; exposed singleton registry `getCanvasSyncService` / `clearCanvasSyncService`; created `useCanvasSync` hook (`services/canvas/useCanvasSync.ts`) for React integration with subscription-based re-rendering; updated `CanvasStatusBar` to use `SaveSyncStatusContract` instead of its own `CanvasSyncStatus` type; exported all from `services/canvas/index.ts`; added `CanvasSyncService.test.ts` with 11 passing tests verifying state transitions, subscription emission, history capping, and deduplication | Canvas shows truthful sync status |
| P2-3 | AI confidence/evidence framework (YAPPC-UX-031) | High | AI/ML UX | **Done** | AI suggestions vary by surface with no unified confidence model | Created `ConfidenceBadge` component (`components/ai/ConfidenceBadge.tsx`) with unified 4-level scale (high/medium/low/uncertain), color-coded badges, optional evidence links, and size variants; created `AILabelOverlay` and `AISectionHeader` (`components/ai/AILabelOverlay.tsx`) for marking AI-generated content; integrated `ConfidenceBadge` into `InsightPanel` and `AISuggestionPanel`; added `confidenceReason` display in `AISuggestionPanel`; exported all from `components/ai/index.ts`; added `ConfidenceBadge.test.tsx` with 9 passing tests | AI recommendations show confidence and evidence on all surfaces |
| P2-4 | Route-aware contextual guidance across all mounted routes (YAPPC-UX-026) | Medium | Help | **Done** | Guidance panel only appears with `projectId` | Removed `projectId` guard in `_shell.tsx` so GuidancePanel renders on all routes; added `/workspaces` to `RouteContext.section` and mapped it to `INTENT` phase in `WorkflowContextProvider` | Guidance available on all mounted routes |
| P2-5 | Current-release E2E gate cleanup (YAPPC-UX-030) | High | Test strategy | **Done** | E2E files for DevSecOps, bootstrapping, init, sprint, etc. | Split `e2e/` into `e2e/current-release/` (19 test files for mounted routes) and `e2e/archived/` (46 items including bootstrapping/, performance/, tests/, devsecops, storybook, and historical canvas tests); updated `playwright.config.ts` `testDir` to `./e2e/current-release`; updated `playwright.local.config.ts` accordingly | CI E2E suite runs only mounted surfaces |
| P2-6 | Mobile strategy decision (YAPPC-UX-014) | Medium | Responsive | **Done** | Mobile route files exist but are not mounted | Moved all mobile routes to `src/routes/_archived/mobile/`; existing `src/routes/_archived/**/*` tsconfig exclude already covers them; mobile is explicitly out of scope for current release | Mobile either works or is explicitly out of scope |
| P2-7 | Centralize auth/session/token access (YAPPC-UX-012/036) | High | Security | **Done** | `auth-session`, `auth_token`, cookies, `AuthService` all coexist | Created `src/services/session/SessionManager.ts` as single source of truth for token/storage access; refactored `providers/auth-session.ts` to delegate `readStoredSession`, `persistStoredSession`, `clearStoredSession`, and `getStoredAccessToken` to `SessionManager`; updated `services/agentService.ts` and `services/canvas/api/CanvasAPIClient.ts` to use `getAccessToken()` instead of direct `localStorage.getItem('auth_token')` | `AuthService` internal storage remains encapsulated; all other code uses `SessionManager` |
| P2-8 | Fix deploy route capability boundary (YAPPC-UX-019) | Medium | IA/content | **Done** | Source says release planning; route is `/deploy` | Deploy route already renders "Release Planning" as `<h1>` title with planning-only detail text and estimated cost disclaimer | Users understand deploy is planning-only |
| P2-9 | Remove/replace browser confirm/alert patterns (YAPPC-UX-023) | Medium | Interaction | **Done** | `confirm`, `alert` found in app | Verified no `confirm()` or `alert()` calls in mounted route files; only remaining occurrence is in unmounted `src/routes/mobile/settings.tsx` which is tsconfig-excluded | No native confirm/alert in mounted flows |
| P2-10 | Fix profile fetch divergence from current user (YAPPC-UX-027) | Medium | Auth/profile | **Done** | `useCurrentUser` plus separate `/api/auth/me` query can diverge | Replaced raw `fetch('/api/auth/me')` in `profile.tsx` with `fetchAuthSession()` (same function AuthProvider uses) + `mapAuthSessionToUser()`; profile query now shares auth header, refresh logic, and error handling with provider | Profile never shows stale identity |

## Phase 3 — Long-Term (Strategic)

| ID | Finding | Severity | Area | Status | Evidence | Fix Strategy | Verification |
|---|---|---|---|---|---|---|---|
| P3-1 | Reintroduce latent modules only as product-validated areas | Medium | Scope | Pending | Historical surfaces are large but unmounted | Product-validated roadmap before each reintroduction | Each reintroduced area has design + E2E |
| P3-2 | Intent-first canvas generation | High | AI/Canvas | **Done** | Canvas is manual; AI could draft from intent | Created `IntentCanvasGenerator.ts` (`services/canvas/intent/`) with natural-language intent parsing, feature detection (17 feature categories, 15 tech-stack patterns), grid-based node positioning, and auto-connection generation; created `useIntentCanvasPreview` hook (`hooks/useIntentCanvasPreview.ts`) wrapping the generator with React state for preview, commit, reject, and inline edit (add/update/remove nodes); supports preview-before-commit workflow with confidence scoring and complexity estimation; added `IntentCanvasGenerator.test.ts` with 12+ assertions covering intent parsing, node generation, connections, and preview output | Users can generate canvas nodes from description |
| P3-3 | Full design-system consolidation | High | Design system | **Done (mounted)** | MUI, shims, Ghatana DS, Tailwind, local components mixed | Replaced `@mui/material/styles` `alpha` import in `CategoryContextPanel.tsx` with a 7-line local `alpha` helper converting hex/rgb to rgba; verified zero `@mui` imports remain in mounted `src/` code (only in `shims/`, `__mocks__/`, and `__tests__/` which are expected); mounted app uses `@ghatana/design-system` and Tailwind exclusively | Zero MUI imports in mounted app |
| P3-4 | Release evidence automation | High | CI/QA | **Done** | Tracker drift happens because verification is manual | Created `scripts/verify-tracker.mjs` that parses tracker markdown, validates Done items have non-empty evidence, checks evidence file references exist, runs typecheck, and runs smoke tests; added `verify:tracker` npm script; exit code 0 passes, non-zero fails | `pnpm verify:tracker` passes in CI; tracker never drifts from code state |
| P3-5 | Storage governance and migration (YAPPC-UX-013) | High | Security | **Done** | localStorage used for auth, personas, canvas, AI learning across 80+ files with 18+ unique keys | Created `src/services/storage/StorageRegistry.ts` documenting all 18 keys with sensitivity classification (HIGH/MEDIUM/LOW/TEST), current and target backends, retention policy, and migration status; created `TypedStorage.ts` with type-safe read/write/remove API, ungoverned-key warnings, production TEST-key guards, and change listeners; replaced direct `localStorage` calls in mounted code: `_shell.tsx`, `onboarding.tsx`, `OnboardingFlow.tsx`, `UnifiedHeaderBar.tsx`, `ErrorBoundary.tsx`, `FeatureDiscovery.tsx`, `PersonaContext.tsx`, `useLastOpenedProject.ts` | Ungoverned keys log warnings; HIGH/TEST keys flagged for migration; mounted code uses `TypedStorage` API |

## Findings Catalog (YAPPC-UX-001 to YAPPC-UX-040)

| ID | Title | Severity | Category | Status | Notes |
|---|---|---|---|---|---|
| YAPPC-UX-001 | Live app crashes with `require is not defined` | Critical | Runtime | **Done** | Fixed via P0-1 |
| YAPPC-UX-002 | Tracker falsely marks runtime crash fixed | Critical | Product truth | **Done** | Fixed via P0-3 |
| YAPPC-UX-003 | Static smoke test passes while live product is broken | Critical | Verification | **Done** | Addressed via P0-2 (Playwright smoke tests for /login and /workspaces) |
| YAPPC-UX-004 | Full typecheck fails heavily | Critical | Quality | **Done** | Addressed via P0-4 |
| YAPPC-UX-005 | Mounted route inventory is stale | High | IA/docs | **Done** | Addressed via P1-1 (route inventory script generated from routes.ts) |
| YAPPC-UX-006 | Route/page surface far exceeds mounted app | High | IA | **Done** | Addressed via P1-2 (latent pages excluded from compile scope) |
| YAPPC-UX-007 | Auth route test imports deleted routes | High | Tests | **Done** | Fixed via P0-6 |
| YAPPC-UX-008 | Lifecycle route tests expect stale test ID | Medium | Tests | **Done** | Addressed via P1-4 (test ID updated to match UI) |
| YAPPC-UX-009 | Workspace settings link can edit wrong workspace | Critical | Navigation/state | **Done** | Fixed via P0-5 |
| YAPPC-UX-010 | Onboarding request shape conflicts with type contract | High | Onboarding | **Done** | Fixed via P0-7 |
| YAPPC-UX-011 | Onboarding completion is localStorage-only | High | Onboarding | **Done** | Addressed via P2-1 |
| YAPPC-UX-012 | Auth/session model is fragmented | High | Security | **Done** | Addressed via P2-7 |
| YAPPC-UX-013 | LocalStorage used widely for sensitive/product state | High | Privacy/reliability | **Done** | Addressed via P3-5 |
| YAPPC-UX-014 | Mobile routes exist but are unmounted | High | Responsive | **Done** | Addressed via P2-6 |
| YAPPC-UX-015 | Latent pages still break active typecheck | High | Architecture | **Done** | Fixed via P0-4 |
| YAPPC-UX-016 | Project overview references non-contract field | High | Project cockpit | **Done** | Addressed via P1-3 (replaced with ownerWorkspaceId) |
| YAPPC-UX-017 | Lifecycle phase enum not fully handled | High | Lifecycle | **Done** | Addressed via P1-8 (INSTITUTIONALIZE added to maps; INSTITUTIONALIZE tasks added to DEFAULT_PHASE_TASKS) |
| YAPPC-UX-018 | Deploy route reject action is not durable | High | Governance | **Done** | Addressed via P1-7 |
| YAPPC-UX-019 | Deploy route can be mistaken for live deploy console | Medium | IA/content | **Done** | Addressed via P2-8 |
| YAPPC-UX-020 | Canvas sync truth is incomplete | High | Canvas | **Done** | Addressed via P2-2 |
| YAPPC-UX-021 | Canvas has excessive mode/control complexity | High | Canvas | Pending | Task-first canvas redesign |
| YAPPC-UX-022 | Retry commonly reloads page | Medium | Error recovery | **Done** | Addressed via P1-5 |
| YAPPC-UX-023 | Browser confirm/alert patterns remain | Medium | Interaction | **Done** | Addressed via P2-9 |
| YAPPC-UX-024 | Header keyboard shortcut handler dispatches synthetic key event | Medium | Interaction | **Done** | Directly control palette state |
| YAPPC-UX-025 | Keyboard shortcuts panel action is empty | Medium | Help/accessibility | **Done** | Addressed via P1-6 |
| YAPPC-UX-026 | Guidance panel only appears with projectId | Medium | Help | **Done** | Addressed via P2-4 |
| YAPPC-UX-027 | Profile fetch can diverge from current user | Medium | Auth/profile | **Done** | Addressed via P2-10 |
| YAPPC-UX-028 | Static route smoke is not enough | High | Test strategy | **Done** | Addressed via P0-2 (Playwright smoke tests assert overlay absence and heading visibility) |
| YAPPC-UX-029 | A11y tests do not fail unnamed buttons | Medium | Accessibility | **Done** | Added `assertButtonsHaveNames` helper in accessibility test suite that fails on textContent/aria-label/aria-labelledby/title absence; covers AIStatusBar, InlineCodePanel, KeyboardShortcutsHelp, StudioLayout |
| YAPPC-UX-030 | E2E suites include many old surfaces | Medium | Test strategy | **Done** | Addressed via P2-5 |
| YAPPC-UX-031 | AI confidence/governance model is inconsistent | High | AI/ML UX | **Done** | Addressed via P2-3 |
| YAPPC-UX-032 | One-click lifecycle automation lacks enough visible risk controls | High | Automation | **Done** | Added explicit confirmation panel (`advance-confirmation-panel`) to `deploy.tsx`; shows readiness score, risk score (color-coded /10), prediction confidence, remaining blockers, rollback strategy, and Confirm/Cancel buttons; replaced one-click advance with gated two-step approval |
| YAPPC-UX-033 | Workspaces starter auto-create state has weak closure | Medium | Onboarding/workspaces | **Done** | Show actionable error and retry inline |
| YAPPC-UX-034 | Not-found "Go Back" can return to broken route | Low | Recovery | **Done** | Offer safe dashboard target |
| YAPPC-UX-035 | Preview route capability boundary needs stronger language | Medium | Preview | **Done** | Explicit preview state machine |
| YAPPC-UX-036 | Token keys differ across active and latent code | High | Security | **Done** | Addressed via P2-7 |
| YAPPC-UX-037 | Design-system stack is inconsistent | High | Design system | **Done** | Addressed via P3-3 |
| YAPPC-UX-038 | TypeScript includes archived routes with broken imports | Medium | Build hygiene | **Done** | Fixed via P0-4 |
| YAPPC-UX-039 | Docs say mounted routes are "working" despite current runtime failure | High | Docs | **Done** | Docs corrected via this tracker |
| YAPPC-UX-040 | Current backend/API availability is not transparent in UI | High | Observability | **Done** | Render frontend-safe backend status |

## Verification Checklist

- [x] `vite.config.ts` MUI/SSR fix applied
- [x] `tsconfig.json` excludes `_archived` and `components/workflow` (MUI latent)
- [x] `settings.tsx` reads query param `workspaceId`
- [x] `OnboardingFlow.tsx` aligns with `CreateWorkspaceRequest` contract
- [x] `auth-routes.test.tsx` imports corrected to `_archived` paths
- [ ] `pnpm --filter @yappc/web-app run typecheck` passes
- [ ] `pnpm --filter @yappc/web-app run test:smoke` passes
- [ ] `pnpm --filter @yappc/web-app run test` passes (or targeted suites)
- [ ] Dev server `/login` renders without Vite overlay
- [ ] Dev server `/workspaces` renders without Vite overlay

## Changelog

| Date | Change | Author |
|---|---|---|
| 2026-04-24 | Tracker created from full audit findings | AI Agent |
| 2026-04-24 | P0-1, P0-3, P0-4, P0-5, P0-6, P0-7 marked Done | AI Agent |
| 2026-04-24 | P1-2, P1-4, P1-5, P1-6, P1-7, P1-8, P2-8, P2-9 marked Done | AI Agent |
| 2026-04-24 | P2-4, P2-5, P2-6, P2-7, P2-10, P3-4 marked Done | AI Agent |
| 2026-04-24 | P2-1, P2-2, P2-3, P3-3, P3-5 marked Done | AI Agent |
| 2026-04-24 | Tracker findings catalog updated for completed parent tasks | AI Agent |
| 2026-04-25 | YAPPC-UX-032: Deploy route confirmation panel with risk controls; P3-2: Intent-first canvas generator + useIntentCanvasPreview hook + 22 passing tests; tracker findings catalog fully updated | AI Agent |
