# YAPPC UI/UX Audit and Remediation Blueprint

Date: 2026-04-24  
Product: `products/yappc`  
Primary audited surface: `products/yappc/frontend/web`  
Audit target: current mounted YAPPC web product, surrounding latent UI surfaces, docs, tests, and UX-affecting implementation.

## Evidence Used

- Active route tree: `products/yappc/frontend/web/src/routes.ts`
- Current web app source under `products/yappc/frontend/web/src`
- Prior audit and tracker: `YAPPC_UI_UX_AUDIT_2026-04-22.md`, `YAPPC_UI_UX_TRACKER.md`
- Product docs: `docs/CRITICAL_JOURNEYS.md`, mounted route inventory, latent page inventory
- Live browser inspection at `http://localhost:7002/` and `/login`
- Targeted verification:
  - `pnpm --filter @yappc/web-app run test:smoke`
  - `pnpm --filter @yappc/web-app run typecheck`
  - targeted Vitest route tests for auth, project overview, deploy, lifecycle

Where docs and implementation conflict, the current runtime and active route tree are treated as product truth.

## 1. Executive Summary

YAPPC has a clearer product direction than its surrounding codebase suggests. The currently mounted product is intentionally smaller than the historical YAPPC surface: dashboard, login, onboarding, workspaces, projects, profile, workspace settings, project cockpit, canvas, preview, deploy, project settings, lifecycle, and not-found. That is the right direction. It is simpler, easier to reason about, and closer to a coherent product than the older operations/security/development/collaboration/admin sprawl.

However, the current YAPPC product experience is not production-ready. The live app fails before users can enter any meaningful flow. Browser inspection at `http://localhost:7002/` and `/login` produced a Vite error overlay with title `Error` and message `require is not defined`, originating from `@mui/material/index.js`. This directly contradicts `YAPPC_UI_UX_TRACKER.md`, which marks the previous `require is not defined` runtime issue as done.

The second systemic issue is product-truth fragmentation. `src/routes.ts` mounts 13 route calls, while `src/routes` contains 72 `.tsx` route files and `src/pages` contains 88 `.tsx` page files. Many of those pages represent operations, security, development, collaboration, bootstrapping, initialization, and admin surfaces that are not mounted in the active app. The docs acknowledge latent surfaces, but the inventory itself is now stale: it still lists `/p/:projectId/canvas-workspace` as mounted, while the active route config no longer mounts it.

The third systemic issue is verification weakness. The official `test:smoke` route check passes, but it is a one-test static file assertion. It does not start the app, render pages, or catch the live `@mui/material` SSR crash. Full web typecheck fails with a large error set across platform utility imports, YAPPC UI packages, theme bridge, lifecycle phases, auth/session typing, canvas types, generated package outputs, stale archived routes, and latent page exports. Targeted route tests also fail: `auth-routes.test.tsx` imports deleted/unmounted `routes/register` and `routes/forgot-password`, and the lifecycle route test expects `lifecycle-phase-summary-card` while the current UI renders `lifecycle-summary-status-card`.

Completeness is mixed. The mounted core is focused, but many flows are only operationally partial: onboarding completion is localStorage-backed, workspace settings are query-parameter based but read current workspace atom, project preview is truthfully scope-reduced, deploy planning is not a live deploy console, lifecycle automation can become one-click without enough visible governance, and canvas persistence/sync remains spread across API, localStorage, IndexedDB, and legacy utilities.

Simplicity is improved at the route level but undermined by implementation sprawl. Users see a relatively simple mounted product when it works, but contributors and QA must understand multiple route systems, latent pages, archived pages, test paths, storage keys, and UI package bridges. That directly affects user experience because stale surfaces and broken dependencies leak into runtime, typecheck, tests, and navigation confidence.

Correctness is currently high-risk. The live app does not render. Typecheck is red. Tests are stale. Auth state rejects `default-tenant` but also falls back through localStorage token models. Workspace settings links pass `workspaceId` in the URL, but the settings page fetches from `currentWorkspaceIdAtom`, so a user can click a specific workspace settings action and see/edit the wrong workspace if the atom is stale. Onboarding calls `createWorkspace.mutateAsync` with fields not accepted by the current type contract.

Consistency remains critical risk. The app mixes mounted React Router route modules, dormant route files, large unmounted `pages/**` surfaces, local route components, YAPPC UI packages, Ghatana design system shims, MUI dependencies, Tailwind classes, localStorage persistence, cookie-first auth comments, and legacy token keys. The product cannot feel unified while the engineering system remains this fragmented.

Overall production-readiness of the YAPPC experience: Critical risk. The product direction is good; the current deliverable is blocked by runtime failure, typecheck failure, stale tests, route/documentation drift, auth/storage fragmentation, and incomplete operational closure for several core flows.

## 2. Deep Audit Scorecard

| Area | Rating | Evidence and rationale |
|---|---:|---|
| Completeness | Critical | The mounted route tree is focused, but live rendering is blocked. Key flows are partial: onboarding persistence, workspace settings routing, deploy planning, canvas save/sync, preview, and lifecycle automation. |
| Simplicity | High | The mounted IA is simpler than historical YAPPC, but dormant pages, stale docs, and many storage/API patterns create contributor and product complexity that leaks into UX. |
| Correctness | Critical | Live browser fails with `require is not defined`; typecheck fails; tests import deleted auth routes; lifecycle tests expect stale test IDs; settings route can ignore clicked workspaceId. |
| Consistency | Critical | Active routes, docs, tests, latent pages, archived pages, storage keys, UI libraries, and auth models are inconsistent. |
| Information architecture | High | The small mounted IA is promising, but latent route/page sprawl and stale inventory docs make product scope ambiguous. |
| Navigation | High | Current mounted paths are clear, but route count/docs/tests disagree, project shell actions include feature-gated nonroutes, and workspace settings links do not match settings state. |
| Workflow simplicity | High | Dashboard/project cockpit are task-oriented. Builder/canvas, lifecycle, and deploy remain concept-heavy and need stronger guided workflows. |
| Visual design | Critical | Could not meaningfully evaluate live visual quality because the app crashes into Vite overlay on `/` and `/login`. Source uses modern classes but runtime blocks proof. |
| Interaction quality | High | Forms, dialogs, tabs, and cards exist. But browser reload retries, browser confirms, direct location changes, and one-click automation remain. |
| Accessibility | High | Skip links and tests exist, but runtime crash blocks browser audit. Some tests only warn on unnamed buttons instead of failing. Canvas still needs complete non-pointer workflows. |
| AI/ML embedded experience | Medium | AI suggestions exist in onboarding, lifecycle, insights, and canvas. They are not yet governed by a unified confidence, fallback, explainability, and audit model. |
| Observability/transparency UX | High | Lifecycle/deploy surfaces have readiness/gate concepts, but app-level live status, operation history, sync truth, and async closure are incomplete. |
| Privacy/security UX | High | Auth is fragmented across localStorage session, cookie comments, token lookup fallbacks, and legacy `auth_token` usage in latent pages. |
| Responsive/mobile behavior | High | Mobile route files exist but are not mounted by `src/routes.ts`; this is not a truthful responsive product strategy. |
| Perceived performance | Critical | The live app fails before rendering. Canvas is also very heavy and carries many persistence and telemetry systems. |
| Cognitive load | High | Users get a focused IA only after runtime works; teams must reconcile too much dormant scope and state drift. |
| Overall product usability | Critical | Not shippable until live render, typecheck, route/test truth, auth/storage, and high-risk flows are corrected. |

## 3. Complete Surface-by-Surface Audit

### 3.1 Runtime, Build, and Product Truth

Purpose: Ensure the web product can run, render, and be verified as the actual user experience.

Completeness assessment: Critical failure. The app does not currently render in the browser.

Simplicity assessment: The runtime stack is too complex: React Router dev SSR, Vite 8, MUI 9, YAPPC UI package aliases, Ghatana design-system shims, Tailwind, and monorepo package aliases interact in ways that break the live product.

Correctness assessment: Critical. The live browser title was `Error`; DOM snapshot showed `require is not defined` from `@mui/material/index.js`.

Consistency assessment: Critical. Tracker says `P0-10 Fix runtime require is not defined` is done. Current live behavior says it is not.

Evidence:
- Live `/` and `/login`: `require is not defined` overlay.
- `vite.config.ts` aliases `@mui/material` to a resolved pnpm path.
- `rg "@mui/material"` found active imports in YAPPC UI libs, product-theme bridge, and the web design-system shim.

Recommendations:
- Treat the MUI SSR/runtime failure as a release blocker.
- Remove MUI from SSR-loaded surfaces or configure SSR noExternal/optimizeDeps correctly for React Router dev.
- Add browser smoke test that starts dev server and asserts `/login` renders the login heading.
- Update tracker only after live browser verification passes.

### 3.2 Active Route Tree and Scope

Purpose: Define what YAPPC actually ships.

Completeness assessment: The mounted route tree is intentionally focused, but docs and files around it are stale.

Simplicity assessment: The route model is simple: root, auth/onboarding, app shell, project shell. This should be preserved.

Correctness assessment: High risk. Active route config mounts 13 route calls. Inventory docs claim 15 mounted handlers and list `/p/:projectId/canvas-workspace`, which is absent from current `routes.ts`.

Consistency assessment: Critical. Route files and page files far exceed active mounted scope.

Evidence:
- `src/routes.ts` lines 19-53 define the active route tree.
- Count: 13 `route(...)` calls in `src/routes.ts`.
- Count: 72 `.tsx` files under `src/routes`.
- Count: 88 `.tsx` files under `src/pages`.
- `YAPPC_MOUNTED_ROUTE_INVENTORY_2026-04-20.md` lists `/p/:projectId/canvas-workspace` as mounted, but current route config does not.

Recommendations:
- Auto-generate mounted route inventory from `src/routes.ts`.
- Move latent pages out of primary `src/pages` or mark them with a quarantine boundary that CI understands.
- Delete or archive stale tests that import unmounted routes.
- Keep the small IA as the product strategy until each latent area is intentionally reintroduced.

### 3.3 Root Dashboard `/`

Purpose: Provide the home/resume surface for guest, empty, and authenticated states.

Completeness assessment: Stronger than most surfaces, blocked by runtime crash. Source shows guest, empty, loading, authenticated, recent projects, workspace health, and project resume paths.

Simplicity assessment: Good. The dashboard focuses on resume, create, and workspace health instead of dumping every feature.

Correctness assessment: Medium risk. It depends on `useWorkspaceContext`, auth provider, and local last-opened project state. It can route to `/projects` broadly rather than a scoped blocker remediation path.

Consistency assessment: Medium. Dashboard uses current mounted routes and aligns with the simplified IA.

Recommendations:
- Preserve dashboard as the primary resume surface.
- Show exact blockers and next actions inline rather than sending users to broad `/projects` or `/workspaces`.
- Add live test coverage beyond static route-file checks.

### 3.4 Login `/login`

Purpose: Authenticate the user.

Completeness assessment: Source has a focused login form, session-expired state, and optional demo login. Runtime cannot render due global MUI issue.

Simplicity assessment: Good visible UI in source. Email/password login is straightforward.

Correctness assessment: High risk. Auth implementation stores sessions in `localStorage` as a backward-compatible fallback, while comments and docs reference cookie-first security. The app also has stale tests for removed register/forgot-password components.

Consistency assessment: High. `AuthProvider`, `auth-session.ts`, `AuthService.ts`, latent page files, and old token keys do not form one session model.

Evidence:
- `AuthService.ts` initializes from `localStorage.getItem('auth-session')`.
- `auth-session.ts` reads `auth-session` from localStorage and falls back to it after cookie lookup.
- `auth-routes.test.tsx` imports `../../routes/register` and `../../routes/forgot-password`, which no longer exist at those paths.

Recommendations:
- Define one auth/session source for the web app.
- Remove stale auth route tests or point them to archived components intentionally.
- Make cookie/session behavior real, not just comments.
- Add browser smoke for `/login`.

### 3.5 Onboarding `/onboarding`

Purpose: First-run setup for workspace/persona/starter project.

Completeness assessment: Partial. The flow exists and has AI name suggestions, persona selection, workspace creation, and local completion. But persistence and API contract alignment are incomplete.

Simplicity assessment: Good direction. It is progressive and role-aware.

Correctness assessment: High. Typecheck reports `personaSelections` does not exist in the current `CreateWorkspaceRequest` type. The project suggestion uses `temp-workspace`, not the eventual workspace context.

Consistency assessment: Medium-high. Completion and persona state are persisted through localStorage keys, while workspaces/projects are API-backed.

Evidence:
- `OnboardingFlow.tsx` passes `personaSelections` and `defaultProject` to `createWorkspace.mutateAsync`.
- Typecheck reports `personaSelections` is not in the request type.
- `onboarding_complete`, `yappc_active_personas`, and `yappc_primary_persona` are stored in localStorage.

Recommendations:
- Align onboarding request shape with API contract.
- Persist onboarding completion server-side.
- Show a review step with exactly what will be created.
- Make persona choices visibly affect later dashboard/canvas/deploy defaults.

### 3.6 Workspaces `/workspaces`

Purpose: Manage and switch workspaces.

Completeness assessment: Partial. Workspace list, create dialog, empty suggested workspace, loading, unavailable, error, and settings/open actions exist.

Simplicity assessment: Reasonable, but workspace switching and settings action need clearer correctness.

Correctness assessment: High. Workspace Settings action navigates to `/settings?workspaceId=...`, but `settings.tsx` fetches from `currentWorkspaceIdAtom`, not the query param. If the atom is stale or the user clicks settings without switching, the wrong workspace can be shown/edited.

Consistency assessment: Medium-high. Retry uses `window.location.reload()`, unlike query-scoped recovery.

Evidence:
- `workspaces.tsx` lines 201-204 navigate to `/settings?workspaceId=${workspace.id}`.
- `settings.tsx` reads `currentWorkspaceIdAtom`.
- `workspaces.tsx` line 67 uses `window.location.reload()`.

Recommendations:
- Either switch workspace before navigating to settings or make `/settings` read and validate the `workspaceId` query param.
- Replace full reload retry with query refetch.
- Confirm before starter workspace creation only after displaying exact created defaults.

### 3.7 Projects `/projects`

Purpose: Browse, search, filter, sort, and create projects.

Completeness assessment: Mostly present. Grid/list, search, filters, sort, create dialog, ownership filters, and loading/error states exist.

Simplicity assessment: Good for a management list. Some labels use emoji for "Mine" and "Shared", which is less consistent than icon+text.

Correctness assessment: Medium. Sorting by `lastActivityAt` can degrade if fields are absent. Health color maps to optional scores without explanation. Error handling treats some backend errors as unavailable and otherwise silently continues into the page.

Consistency assessment: Medium. Uses local buttons/selects and raw Tailwind classes rather than a single shared table/list pattern.

Recommendations:
- Add explicit unavailable state for all errors, not only selected message substrings.
- Explain health score source or hide it until meaningful.
- Use shared filter/search/sort/list components.

### 3.8 Project Shell `/p/:projectId/*`

Purpose: Provide project-level navigation, context actions, phase metadata, and tabs.

Completeness assessment: Good core, partial action model. Overview/canvas/lifecycle/deploy/settings tabs are mounted; preview is feature-gated but still mounted in routes.

Simplicity assessment: Good tab reduction. Hiding project tabs on canvas reduces duplication.

Correctness assessment: Medium-high. Shell action handlers include feature-gated share/export paths; share navigates to `${basePath}/share` when enabled, but no share route is mounted.

Consistency assessment: Medium. Global header and project shell both manage context, breadcrumbs, phases, actions, and project state.

Recommendations:
- Route-contract test every project shell action.
- Do not add feature-gated actions unless their routes or dialogs are mounted and tested.
- Keep canvas header and project shell navigation in one source of truth.

### 3.9 Project Overview `/p/:projectId`

Purpose: Summarize project status, lifecycle phase, health, readiness, blockers, activity, and next action.

Completeness assessment: Strong conceptually. It includes project data, activity, phase preview, promotion status, delivery posture, blockers, and automation guidance.

Simplicity assessment: Good summary-first direction.

Correctness assessment: High risk. Typecheck reports `ownerWorkspace` does not exist on the project contract. Runtime would likely break or display fallback incorrectly depending on data shape.

Consistency assessment: Medium. It uses direct fetch paths and parse helpers, while other surfaces use workspace hooks.

Recommendations:
- Align project contract fields before release.
- Add explicit unavailable states for phase preview and activity separately.
- Tie "automation guidance" to a real, auditable operation record.

### 3.10 Canvas `/p/:projectId/canvas`

Purpose: Unified product canvas for planning, design, code, deploy, collaboration, and lifecycle artifacts.

Completeness assessment: Broad but not simple. The canvas has many features: React Flow, lifecycle zones, left rail, right panel, toolbar, telemetry, commands, AI status, keyboard shortcuts, local-only sync status, outline, export/import, context menus, drawing, minimap, calm mode, and collaboration banner.

Simplicity assessment: High cognitive load. The canvas is framed as "complete" and "Epic 1-10", but users need a clear primary job: what should I do next to move the project forward?

Correctness assessment: High. Source declares `canvasSyncStatus: 'local-only'`. Multiple persistence systems still exist, including backend-first/localStorage fallback, IndexedDB migration, node-position localStorage, and legacy canvas state migration.

Consistency assessment: High. Canvas uses `@ghatana/canvas`, `@ghatana/design-system`, local components, React Flow, MUI-derived shims, and many custom hooks.

Recommendations:
- Make canvas state truth explicit: local-only, syncing, saved, failed, stale, conflict.
- Add operation history for save/import/export/apply AI/generate/deploy.
- Prioritize guided lifecycle tasks over freeform canvas controls.
- Ensure keyboard-first create/edit/connect/delete workflows are complete and tested.

### 3.11 Preview `/p/:projectId/preview`

Purpose: Preview generated or deployed application output.

Completeness assessment: Truthfully reduced according to older docs, but still needs closure. Preview must say whether it is live, generated, unavailable, or external.

Simplicity assessment: Should remain simple: one clear preview state and next action.

Correctness assessment: Needs contract validation. If preview depends on external runtime, users need explicit status and reasons.

Recommendations:
- Provide exact status: no preview generated, preview building, preview available, external service unavailable, or permissions needed.
- Link to the generating operation and deployment artifact.

### 3.12 Deploy `/p/:projectId/deploy`

Purpose: Release planning and lifecycle promotion.

Completeness assessment: Partial. It models readiness, release planning status, deployment plan, capacity recommendations, operator notes, incidents, phase advance, and panel host. It is not a live deploy console.

Simplicity assessment: Good if positioned as planning; risky if users expect actual deployment.

Correctness assessment: High. `handleReject` only logs to console and says no action is needed. Approve/advance can submit with optional note, but rejection does not create durable evidence.

Consistency assessment: Medium. It mixes lifecycle artifacts, phase gates, deployment planning, incidents, and capacity in one route.

Recommendations:
- Rename or subtitle as "Release planning" wherever needed.
- Make reject/hold decisions durable, with reason, actor, timestamp, and next review.
- Add clear boundary between planning recommendations and actual deployment execution.
- Require explicit confirmation before lifecycle promotion.

### 3.13 Lifecycle `/p/:projectId/lifecycle`

Purpose: Explore lifecycle phases, recommendations, insights, anomalies, next tasks, and automation plans.

Completeness assessment: Conceptually strong, but test drift and automation governance gaps remain.

Simplicity assessment: Summary-first improvements are visible in source, but the page still exposes recommendations, insights, anomalies, next task, automation plan, and phase explorer at once.

Correctness assessment: High. Targeted test expected `lifecycle-phase-summary-card`, current UI renders `lifecycle-summary-status-card`. This is not a user-facing bug by itself, but it proves verification drift.

Consistency assessment: Medium-high. Uses `React.createElement` style for a large route, unlike most other route modules.

Recommendations:
- Keep lifecycle summary-first.
- Add a single "recommended next action" with evidence and confidence, then progressively disclose the rest.
- Standardize AI/automation action governance before one-click approval.
- Update tests to current UI contracts.

### 3.14 Workspace Settings `/settings`

Purpose: Manage current workspace fields and destructive actions.

Completeness assessment: Partial. General and danger tabs exist. Advanced metadata is progressive. Delete exists.

Simplicity assessment: Good scope reduction: unsupported admin capabilities are hidden rather than faked.

Correctness assessment: Critical risk due query/atom mismatch. `/settings?workspaceId=...` may not mean the page edits that workspace.

Consistency assessment: Medium. Save status pattern is good and should be reused; destructive action model must be shared.

Recommendations:
- Make workspace identity in settings unambiguous.
- Require typed confirmation for delete and show project/member impact.
- Use route param or query param as the source of truth, not hidden atom state.

### 3.15 Profile `/profile`

Purpose: Show current account summary.

Completeness assessment: Reasonable and truthful. Editing is explicitly unavailable.

Simplicity assessment: Good. Read-only scope is clear.

Correctness assessment: Medium. It fetches `/api/auth/me` without adding Authorization header, relying on browser cookies or backend session. This needs to match the auth model.

Consistency assessment: Medium. Profile uses current user fallback plus query data, which can diverge.

Recommendations:
- Clarify auth transport and ensure profile query uses the same session mechanism.
- Show unavailable/error state if `/api/auth/me` fails rather than potentially displaying stale current user data.

### 3.16 Not Found `*`

Purpose: Recover from unknown routes.

Completeness assessment: Basic and useful.

Simplicity assessment: Good.

Correctness assessment: Medium. "Go Back" can return to an unavailable error route or external history; dashboard link is safer.

Consistency assessment: Low-medium. Uses direct `window.history.back()`.

Recommendations:
- Provide a route-aware safe back target and contextual suggestions.

### 3.17 Mobile Routes

Purpose: Mobile overview/projects/backlog/canvas/deploy/settings/notifications.

Completeness assessment: Not product-complete. Mobile route files exist but are not mounted by active `src/routes.ts`.

Simplicity assessment: Ambiguous. File presence implies a mobile product, but runtime truth does not.

Correctness assessment: High. Some mobile files use localStorage keys such as `current-workspace`, which differs from mounted app keys.

Consistency assessment: Critical. Mobile is a latent surface masquerading as reachable code.

Recommendations:
- Decide whether mobile is a supported YAPPC web surface.
- If yes, mount and test it. If no, quarantine/archive mobile routes.

### 3.18 Latent Pages: Operations, Security, Development, Collaboration, Bootstrapping, Initialization, Admin

Purpose: Historical or future product modules.

Completeness assessment: Not shipped according to active router. Many are large and operationally tempting, but they are not mounted.

Simplicity assessment: Major cognitive burden. These files make the product look much larger than it is.

Correctness assessment: High risk. Typecheck and grep show many latent pages still import old token keys, missing modules, unavailable exports, direct `localStorage.getItem('auth_token')`, and broad placeholder/mock services.

Consistency assessment: Critical.

Recommendations:
- Move latent pages out of app compile scope or make them compile-clean behind explicit package boundaries.
- Do not let latent surfaces break active web typecheck.
- Keep only mounted, supported product paths in the release gate.

## 4. End-to-End Flow Review

### 4.1 Visit App and Sign In

User goal: Load YAPPC, understand the entry state, and sign in.

Current flow:
1. User opens `/` or `/login`.
2. Runtime crashes into Vite overlay.

Completeness gaps:
- The flow cannot begin.
- Browser smoke does not catch this.

Correctness concerns:
- Tracker says the runtime crash is fixed, but live browser says otherwise.

Ideal future flow:
User opens `/`, sees guest landing or dashboard, signs in, receives a durable session, and lands on intended route with a clear workspace context.

### 4.2 First Run Onboarding

User goal: Create workspace, select persona, create starter project, and start work.

Current source flow:
1. User enters onboarding.
2. System suggests workspace name.
3. User chooses personas and project type.
4. System suggests project name using `temp-workspace`.
5. Create workspace request includes default project and persona selections.
6. LocalStorage marks completion.

Failure points:
- Request shape is not type-aligned.
- Completion is localStorage-only.
- Persona choices are not clearly tied to future UX.

Ideal future flow:
System shows a review step, creates backed workspace/project/persona preferences atomically, and records setup state server-side.

### 4.3 Manage Workspaces

User goal: See workspaces, create one, open one, or edit its settings.

Current source flow:
1. Load workspace list.
2. Select workspace or click settings.
3. Settings link includes query param but settings page reads atom.

Failure points:
- Wrong workspace settings risk.
- Retry reloads whole page.

Ideal future flow:
Workspace selection and settings use one source of truth. Settings deep links are stable and validated.

### 4.4 Create and Resume Projects

User goal: Create a project and resume work from dashboard or list.

Current source flow:
1. Dashboard and projects page list recent/all projects.
2. Create dialog can create project and navigate to `/p/:id`.
3. Last-opened project stored locally.

Failure points:
- Last-opened is local only.
- Project health/phase can show partial data with limited provenance.

Ideal future flow:
Dashboard ranks one best next project/action from server-backed activity, blockers, ownership, and lifecycle state.

### 4.5 Project Cockpit

User goal: Understand project health, phase, blockers, recent activity, and next action.

Current source flow:
1. Fetch project.
2. Fetch activity.
3. Fetch phase preview.
4. Render cockpit cards.

Failure points:
- Type contract mismatch on `ownerWorkspace`.
- Activity/phase preview error states need independent handling.

Ideal future flow:
Cockpit starts with "what changed", "what is blocked", and "what to do next", with all cards backed by explicit evidence.

### 4.6 Canvas Authoring

User goal: Design/plan/build project artifacts in one canvas.

Current source flow:
1. Open canvas.
2. Interact with rails, nodes, canvas mode, toolbar, outline, minimap, context menu, AI, and lifecycle zones.
3. State is local-only/sync-dependent.

Failure points:
- Too many modes and panels.
- Sync truth is not complete enough.
- Persistence is fragmented.
- Keyboard and screen reader paths must be proven, not assumed.

Ideal future flow:
Canvas opens on the next lifecycle task, hides nonessential controls, shows save/sync truth, and offers keyboard-equivalent authoring.

### 4.7 Lifecycle Guidance

User goal: Move project through lifecycle phases with confidence.

Current source flow:
1. View phase explorer and summary.
2. Read recommendations, insights, anomalies.
3. Execute next task or apply automation plan.

Failure points:
- Verification drift around expected UI.
- One-click automation needs stronger governance and evidence.
- Too many panels compete.

Ideal future flow:
Lifecycle gives one recommended next action, shows evidence/confidence, and keeps automation human-reviewed unless confidence and risk thresholds are met.

### 4.8 Release Planning

User goal: Decide whether a project is ready to release and plan deployment.

Current source flow:
1. Fetch lifecycle preview.
2. Derive planning status, deployment strategy, capacity recommendation.
3. Approve/advance phase or create incident.

Failure points:
- It is not a deploy console despite route name.
- Reject logs only to console.
- Capacity numbers appear simulated/derived and need source labeling.

Ideal future flow:
Release planning shows readiness, risk, approval status, deployment plan, and whether an actual deployment backend is connected.

### 4.9 Workspace and Project Settings

User goal: Update supported fields safely; delete only with full awareness.

Current source flow:
1. Edit fields.
2. Save via PATCH.
3. Delete workspace from danger tab.

Failure points:
- Workspace route source mismatch.
- Delete needs impact preview and typed confirmation.
- Unsupported admin capabilities are hidden, which is good, but should include a roadmap or contact path when relevant.

Ideal future flow:
Settings are scoped by route identity, save status is clear, unsupported capabilities are transparently marked, and destructive actions are governed.

## 5. Comprehensive Findings Catalog

| ID | Title | Severity | Category | Affected area | Dimension | Evidence | User impact | Likely root cause | Recommended fix |
|---|---|---:|---|---|---|---|---|---|---|
| YAPPC-UX-001 | Live app crashes with `require is not defined` | Critical | Runtime | Entire web app | Correctness, completeness | Browser `/` and `/login` show Vite overlay from `@mui/material/index.js` | User cannot use product | MUI/SSR/Vite alias incompatibility | Fix SSR dependency handling or remove MUI from SSR-loaded path |
| YAPPC-UX-002 | Tracker falsely marks runtime crash fixed | Critical | Product truth | Docs/tracker | Correctness | Tracker P0-10 says done; live app still crashes | Release confidence is false | Tracker not validated with browser smoke | Require live verification before marking fixed |
| YAPPC-UX-003 | Static smoke test passes while live product is broken | Critical | Verification | `test:smoke` | Correctness | One static route-file test passed | CI can greenlight unusable app | Smoke scope too narrow | Add dev-server render smoke |
| YAPPC-UX-004 | Full typecheck fails heavily | Critical | Quality | Web app | Correctness | `pnpm --filter @yappc/web-app run typecheck` exits 2 with many TS errors | Product cannot be trusted for release | Latent code, aliases, generated outputs, type drift | Make mounted app and included sources type-clean |
| YAPPC-UX-005 | Mounted route inventory is stale | High | IA/docs | Route docs | Consistency | Docs list canvas-workspace as mounted; route config does not | Teams misunderstand scope | Manual inventory drift | Generate inventory from route config |
| YAPPC-UX-006 | Route/page surface far exceeds mounted app | High | IA | `src/routes`, `src/pages` | Simplicity, consistency | 13 route calls vs 72 route files and 88 page files | Scope appears larger than product truth | Dormant surfaces remain in app tree | Quarantine/archive latent pages |
| YAPPC-UX-007 | Auth route test imports deleted routes | High | Tests | Auth tests | Correctness | `auth-routes.test.tsx` imports `routes/register` and `routes/forgot-password` | Tests fail and enforce stale UX | Routes moved/archived but tests not updated | Delete or intentionally point to archived components |
| YAPPC-UX-008 | Lifecycle route tests expect stale test ID | Medium | Tests | Lifecycle | Consistency | Test expects `lifecycle-phase-summary-card`; UI renders `lifecycle-summary-status-card` | Verification no longer tracks UI | Test contract drift | Update test to current semantic contract |
| YAPPC-UX-009 | Workspace settings link can edit wrong workspace | Critical | Navigation/state | Workspaces/settings | Correctness | Link passes `workspaceId` query; settings reads `currentWorkspaceIdAtom` | User may edit/delete wrong workspace | Route identity and atom identity diverge | Make settings route source explicit and validated |
| YAPPC-UX-010 | Onboarding request shape conflicts with type contract | High | Onboarding | Onboarding | Correctness | Typecheck: `personaSelections` not allowed | First-run setup can fail or silently drop intent | UI ahead of API contract | Align API contract or split supported fields |
| YAPPC-UX-011 | Onboarding completion is localStorage-only | High | Onboarding | Onboarding | Completeness | `onboarding_complete` stored in localStorage | Shared devices/resets can misstate setup | Client-only setup state | Persist onboarding status server-side |
| YAPPC-UX-012 | Auth/session model is fragmented | High | Security | Auth | Trust, consistency | Cookies, `auth-session`, `auth_token`, AuthService, auth-session provider coexist | Session behavior is confusing and less secure | Migration not completed | Centralize auth/session service |
| YAPPC-UX-013 | LocalStorage is used widely for sensitive/product state | High | Privacy/reliability | App/canvas/auth | Trust | `rg localStorage` finds many paths | Data leakage, stale state, cross-device gaps | Convenience persistence | Govern storage policy and migrate important state |
| YAPPC-UX-014 | Mobile routes exist but are unmounted | High | Responsive | Mobile | Completeness | `src/routes/mobile/*` exists; active route tree omits it | Mobile product implied but unavailable | Latent mobile code | Mount/test or quarantine |
| YAPPC-UX-015 | Latent pages still break active typecheck | High | Architecture | `src/pages/**` | Correctness | Typecheck includes many latent page/module errors | Unsupported surfaces block release | Compile boundary too broad | Exclude/quarantine or make type-clean |
| YAPPC-UX-016 | Project overview references non-contract field | High | Project cockpit | `/p/:id` | Correctness | Typecheck: `ownerWorkspace` does not exist; source uses it | Cockpit can display wrong/failed ownership | Contract drift | Align contract and UI |
| YAPPC-UX-017 | Lifecycle phase enum not fully handled | High | Lifecycle/canvas | Multiple | Correctness | Typecheck missing `INSTITUTIONALIZE` in records | New phase can break guidance/tasks | Enum expansion not propagated | Exhaustive phase handling tests |
| YAPPC-UX-018 | Deploy route reject action is not durable | High | Governance | Deploy | Completeness, trust | `handleReject` console.logs only | Reject decisions leave no audit/history | Placeholder implementation | Persist rejection reason and status |
| YAPPC-UX-019 | Deploy route can be mistaken for live deploy console | Medium | IA/content | Deploy | Correctness | Source says release planning; route is `/deploy` | User may overestimate capability | Label/route mismatch | Label as release planning or wire deploy execution |
| YAPPC-UX-020 | Canvas sync truth is incomplete | High | Canvas | Canvas | Correctness | `canvasSyncStatus` is `local-only`; multiple persistence systems | Users cannot trust saved state | Persistence migration incomplete | Unified sync state and operation history |
| YAPPC-UX-021 | Canvas has excessive mode/control complexity | High | Canvas | Canvas | Simplicity | Many panels/modes/hooks/statuses | High cognitive load | Feature accumulation | Task-first canvas and progressive disclosure |
| YAPPC-UX-022 | Retry commonly reloads page | Medium | Error recovery | Workspaces/pages | Simplicity, correctness | `window.location.reload()` in key paths | Users lose context | Missing shared retry model | Use query refetch/specific recovery |
| YAPPC-UX-023 | Browser confirm/alert patterns remain | Medium | Interaction | Canvas/settings/latent pages | Consistency, accessibility | `confirm`, `alert` found in app | Poor accessibility and weak governance | One-off interactions | Shared accessible dialogs |
| YAPPC-UX-024 | Header keyboard shortcut handler dispatches synthetic key event | Medium | Interaction | Shell search | Correctness | `onSearch` dispatches `KeyboardEvent` for mod+k | Fragile command opening | Palette API not centralized | Directly control command palette state |
| YAPPC-UX-025 | Keyboard shortcuts panel action is empty | Medium | Help/accessibility | Shell | Completeness | `onKeyboardShortcuts` handler is comment only | Users cannot discover shortcuts via header | Incomplete wiring | Open shortcuts panel from handler |
| YAPPC-UX-026 | Guidance panel only appears with projectId | Medium | Help | Shell | Completeness | `showGuidance && projectId` condition | Workspaces/projects lack contextual help | Project-only help model | Provide route-level guidance outside projects |
| YAPPC-UX-027 | Profile fetch can diverge from current user | Medium | Auth/profile | Profile | Correctness | `useCurrentUser` plus separate `/api/auth/me` query | Stale/mismatched identity | Dual user sources | Single session/user source |
| YAPPC-UX-028 | Static route smoke is not enough | High | Test strategy | Tests | Completeness | Only asserts strings in route file | No user journey confidence | Minimal patch after old audit | Add journey-based release gates |
| YAPPC-UX-029 | A11y tests do not fail unnamed buttons | Medium | Accessibility | E2E | Correctness | Test logs warning rather than assertion | Accessibility debt can pass | Weak assertion design | Fail on missing names |
| YAPPC-UX-030 | E2E suites include many old surfaces | Medium | Test strategy | E2E | Consistency | E2E files for DevSecOps, bootstrapping, init, sprint, code review, etc. | QA signal is noisy | Historical scope retained | Split current release gate vs archival suites |
| YAPPC-UX-031 | AI confidence/governance model is inconsistent | High | AI/ML UX | Lifecycle/canvas/onboarding | Trust | AI suggestions, insights, automation plans vary by surface | Users cannot know when to trust automation | No AI UX contract | Define confidence, fallback, override, audit |
| YAPPC-UX-032 | One-click lifecycle automation lacks enough visible risk controls | High | Automation | Lifecycle | Trust | One-click approve path exists | Automation can hide governance risk | Speed prioritized over review | Require evidence, confidence, approval, rollback |
| YAPPC-UX-033 | Workspaces starter auto-create state has weak closure | Medium | Onboarding/workspaces | Completeness | Suggested workspace path catches errors to console | User may not recover from failed creation | Missing inline recovery | Show actionable error and retry |
| YAPPC-UX-034 | Not-found "Go Back" can return to broken route | Low | Recovery | 404 | Simplicity | Uses `window.history.back()` | May loop user into bad state | Generic browser history | Offer safe recent/dashboard targets |
| YAPPC-UX-035 | Preview route capability boundary needs stronger language | Medium | Preview | Project preview | Correctness | Preview is mounted but likely unavailable/external | Users may expect live preview | Scope-reduction wording incomplete | Explicit preview state machine |
| YAPPC-UX-036 | Token keys differ across active and latent code | High | Security | Auth/API | Consistency | `auth-session`, `auth_token`, cookies, API key fallbacks | Auth bugs and leakage risk | Multiple migrations | Central token accessor, ban direct reads |
| YAPPC-UX-037 | Design-system stack is inconsistent | High | Design system | App/libs | Consistency | MUI, shims, Ghatana DS, Tailwind, local components | Runtime and visual drift | Partial migration | Standardize DS boundary |
| YAPPC-UX-038 | TypeScript includes archived routes with broken imports | Medium | Build hygiene | Archived routes | Correctness | Typecheck errors from `_archived` paths | Archives still affect release | Archive still compiled | Exclude archives from tsconfig |
| YAPPC-UX-039 | Docs say mounted routes are "working" despite current runtime failure | High | Docs | Architecture docs | Correctness | Inventory labels routes working | Misleading readiness | Docs not revalidated | Add dated status and verification results |
| YAPPC-UX-040 | Current backend/API availability is not transparent in UI | High | Observability | App | Completeness | API unavailable fallback exists, but live app crashes before it | Users cannot tell if backend or frontend is at fault | Runtime failure before product states | Render frontend-safe backend status |

## 6. Completeness Gap Inventory

Missing screens or truthful surfaces:
- Runtime-safe login and dashboard.
- Generated current route inventory.
- Server-backed onboarding completion/profile/persona preferences.
- Routeable workspace settings by workspace ID.
- Unified canvas save/sync history.
- Release planning operation/audit history.
- Mobile product decision: mounted and tested or quarantined.
- Current release gate dashboard showing browser, typecheck, route, auth, and API status.

Missing steps:
- Verify live app before marking tracker tasks complete.
- Review onboarding creation payload before submit.
- Confirm workspace delete with impact preview.
- Persist deploy reject/hold decisions.
- Confirm lifecycle promotion with evidence and rollback path.
- Show explicit preview generation/deployment state.

Missing states:
- Live app normal render state due runtime crash.
- Auth unavailable vs unauthenticated vs expired vs tenant invalid.
- Workspace settings wrong-scope detection.
- Canvas conflict/stale/sync failed/saved states.
- Deploy planning unavailable vs blocked vs planning-ready vs live-deploy-unavailable.
- AI low-confidence and governance-required states.

Missing validations:
- Workspace settings route identity matches edited workspace.
- Onboarding request matches API contract.
- Project overview contract fields exist.
- Lifecycle enum exhaustive handling.
- Active route tests do not import deleted routes.
- MUI/shim SSR compatibility.

Missing guidance:
- What YAPPC currently supports vs latent historical modules.
- Why a user should use lifecycle vs canvas vs deploy.
- What AI recommendations can do automatically and what needs review.
- Which state is local-only and which is safely persisted.

Missing recovery:
- Query-specific retry instead of full reload.
- Runtime crash recovery cannot be represented inside product UI.
- Failed starter workspace creation recovery.
- Failed lifecycle automation recovery.
- Failed canvas save conflict handling.

Missing accessibility:
- Live rendered a11y audit because runtime is blocked.
- Failing assertions for unnamed buttons.
- Complete keyboard-first canvas authoring.
- Dialog focus trap/focus restore across custom dialogs.

Missing trust/privacy/security:
- One auth/session model.
- Storage governance for localStorage/sessionStorage/IndexedDB.
- Sensitive action confirmations with audit.
- Role-aware mounted navigation and actions.
- Evidence links for lifecycle/deploy/AI claims.

## 7. Comprehensive Simplification Plan

Remove:
- Dormant pages from active compile/release scope.
- Stale route tests for deleted register/forgot-password routes.
- Manual mounted route inventory maintenance.
- Broad latent E2E suites from current release gate.
- Browser `alert`, `confirm`, and full reload retry from mounted flows.
- MUI from SSR-loaded paths unless the integration is proven stable.

Merge:
- Command palette and global search into one state-controlled command surface.
- Auth/session/token access into one service.
- Workspace identity routing and atom state into one source.
- Canvas persistence paths into one sync service.
- Lifecycle/deploy automation evidence into one operation record model.
- Page state handling into shared loading/empty/error/degraded components.

Hide by default:
- Advanced metadata until requested.
- Raw canvas and lifecycle internals unless diagnosing.
- AI recommendation mechanics unless confidence/evidence matters.
- Latent product areas from docs and navigation.
- Local-only storage details unless state is at risk.

Automate:
- Generate route inventory from `src/routes.ts`.
- Detect unmounted route/page files in CI.
- Detect direct token/localStorage reads outside approved services.
- Generate release evidence from browser render, typecheck, and active journey tests.
- Suggest next project action from lifecycle, activity, and blockers.

Prefill:
- Onboarding workspace/project names from real context.
- Project type from imported docs/repo metadata.
- Deploy operator note from blocker summary, editable before submit.
- Settings forms from verified route-scoped entity.

Make contextual:
- Guidance panel should work on workspaces/projects/settings, not only project pages.
- AI should appear inside next-action cards, forms, and blockers, not as broad ambient panels.
- Help should explain the current route and user job.

Move to advanced/admin mode:
- Raw IDs, phase internals, audit payloads, canvas debug panels, storage migrations.
- Historical DevSecOps/operations/security/admin/collaboration pages until remounted.

Reusable shared patterns needed:
- `PageState`
- `EntityScopedSettingsRoute`
- `SensitiveActionDialog`
- `OperationRecordPanel`
- `AIConfidenceEvidence`
- `RouteContractTest`
- `StoragePolicy`
- `CanvasSyncStatus`
- `ReleaseDecisionDialog`

## 8. Correctness Review Register

| Correctness issue | Severity | Evidence | Required correction |
|---|---:|---|---|
| Live app crashes | Critical | Browser overlay `require is not defined` | Fix MUI/SSR path and add browser smoke |
| Typecheck fails | Critical | `tsc` exits 2 with many errors | Make active compile scope clean |
| Tracker contradicts live app | Critical | P0-10 done vs live crash | Reopen tracker item |
| Route inventory stale | High | Canvas-workspace listed mounted but absent | Generate route inventory |
| Workspace settings wrong-scope risk | Critical | Query param ignored by settings route | Use route/query source or switch before navigate |
| Onboarding request invalid | High | `personaSelections` type error | Align contract |
| Auth test imports deleted routes | High | Vitest import failure | Remove/update tests |
| Lifecycle test ID drift | Medium | Test expects stale test ID | Update semantic test contract |
| Project overview contract drift | High | `ownerWorkspace` type error | Align with API |
| Lifecycle enum incomplete | High | Missing `INSTITUTIONALIZE` errors | Exhaustive phase map |
| Auth storage fragmented | High | localStorage, cookie, auth_token | Central auth service |
| Deploy reject not persisted | High | Console log only | Persist reject decision |
| Canvas sync local-only | High | Hard-coded sync state | Real sync state machine |
| E2E current gate unclear | Medium | Many historical suites | Separate current vs archival gates |

## 9. Consistency Review Register

| Drift type | Severity | Evidence | Standard needed |
|---|---:|---|---|
| Runtime/docs drift | Critical | Tracker says fixed; browser fails | Verification-backed tracker |
| Route drift | High | Route config, inventory, route files disagree | Generated route truth |
| Test drift | High | Auth/lifecycle tests stale | Active-route-only release tests |
| Storage drift | High | Many localStorage keys and token names | Storage policy and token accessor |
| UI library drift | Critical | MUI, shims, Ghatana DS, Tailwind | One DS boundary |
| State drift | High | Query param vs atom in settings | Route-scoped entity contract |
| Error drift | Medium | Reload vs fallback vs console | Shared error/retry model |
| Automation drift | High | AI suggestions, lifecycle apply, deploy advice differ | AI governance pattern |
| Mobile drift | High | Mobile files unmounted | Supported or quarantined mobile |
| Latent scope drift | High | 88 page files outside active routes | Latent module boundary |

## 10. Comprehensive AI/ML Embedding Plan

| Opportunity | Function | User value | Mode | Confidence/fallback | Governance/override | Priority |
|---|---|---|---|---|---|---:|
| Dashboard next-best action | Rank project/blocker/task | Reduces decision load | Assist | Show source evidence; fallback recent projects | User can dismiss/reorder | High |
| Onboarding inference | Infer workspace/project/persona defaults | Faster setup | Assist | Ask when confidence low | User reviews before create | High |
| Project cockpit summary | Summarize health, blockers, recent changes | Faster orientation | Assist | Link to activity/evidence | User controls actions | High |
| Canvas task guidance | Surface next lifecycle task in canvas | Reduces mode overload | Assist | Fallback manual canvas | User chooses mode | High |
| Canvas generation | Draft artifacts/nodes from intent | Less manual canvas work | Assist/automate low risk | Preview before commit | Audit accepted generation | Medium |
| Lifecycle promotion support | Recommend promotion/hold | Safer lifecycle moves | Assist | Confidence threshold 85%+ | Human approval required unless low risk | High |
| Deploy planning | Recommend strategy/capacity/risk | Clear release plan | Assist | Label derived vs observed data | Operator confirms | High |
| Anomaly grouping | Cluster readiness anomalies | Less noise | Assist | Show raw signals | Human resolves/acknowledges | Medium |
| Storage/sync conflict resolution | Suggest conflict merge | Prevent data loss | Assist | No auto-merge on low confidence | User accepts merge | Medium |
| Documentation/product truth agent | Detect stale route/docs/tests | Prevent scope drift | Automate | CI fallback manual review | Owners approve docs changes | High |

AI/ML must remain embedded. Do not add a generic "AI panel" as the primary answer. The product should feel smarter because setup, triage, artifact creation, and release decisions require less manual interpretation.

## 11. Trust / Transparency / Privacy / Security UX Review

Operational visibility:
- Add a frontend health gate visible in development and release evidence: app render, API reachability, auth session, route table, typecheck.
- Add operation records for onboarding create, project create, canvas save, lifecycle apply, deploy planning, delete, and settings save.
- Make local-only/syncing/saved/failed/conflict states explicit.

Auditability:
- Persist deploy reject/hold/advance decisions with actor, reason, project, phase, before/after, and timestamp.
- Persist workspace delete and project destructive actions with impact preview.
- Track AI-generated or AI-applied changes as audit events.

Permission clarity:
- Role-aware nav and action states should derive from one user/session source.
- Profile/settings should use the same auth source as API calls.
- Tenant/workspace identity must be displayed and validated before edits/deletes.

Privacy:
- Reduce localStorage use for auth, personas, onboarding, canvas state, AI learning, and last-opened projects.
- Classify each persisted key as preference, cache, user data, sensitive, or migration-only.
- Do not store access tokens under legacy `auth_token` keys.

Security:
- Complete cookie/httpOnly migration or document supported fallback clearly.
- Ban direct token reads in latent pages.
- Separate unsupported latent pages from active release build.

Safe defaults:
- Do not auto-complete onboarding locally unless backend setup succeeded.
- Do not advance lifecycle without explicit approval and visible evidence.
- Do not delete workspaces without typed confirmation and impact preview.

## 12. Design System / Reuse Review

Current state:
- The app uses Tailwind-like utility classes, local components, `@ghatana/design-system` shim, `@ghatana/theme`, `@ghatana/canvas`, YAPPC UI libraries, and MUI.
- The MUI dependency currently breaks live SSR/dev rendering.

Inconsistent components:
- Buttons, inputs, dialogs, cards, panels, page states, toasts, badges, and error states vary across route modules and latent pages.
- Some mounted routes use raw buttons/selects/inputs; others use design-system components.

Behavior drift:
- Retries use reload in some places and query invalidation/refetch in others.
- Destructive actions use typed confirmation in some places, browser confirm in others, and direct mutation elsewhere.
- Search/command palette is bridged by synthetic keyboard event.

Missing abstractions:
- Route inventory generator.
- Storage policy utility.
- Token accessor.
- Entity-scoped settings route helper.
- Operation history panel.
- AI confidence/evidence component.
- Shared route-safe error state.

Recommendations:
1. Resolve MUI/SSR or remove MUI from active render path.
2. Make one active design-system boundary for mounted app.
3. Exclude latent pages from the active app compile until migrated.
4. Standardize buttons/forms/dialogs/page states in mounted routes first.
5. Add lint checks for direct localStorage token reads and browser confirm/alert in mounted paths.

## 13. Prioritized Remediation Roadmap

### Immediate

| Item | Priority | Effort | Impact | Dependencies | Owner type | Rationale |
|---|---:|---:|---:|---|---|---|
| Fix live `require is not defined` runtime crash | Critical | Medium | Critical | Vite/MUI/SSR config | Frontend/platform | Product cannot render |
| Add browser render smoke for `/login` and `/workspaces` | Critical | Medium | Critical | Dev server | QA/frontend | Static smoke missed blocker |
| Reopen/update tracker P0-10 | Critical | Small | High | Browser evidence | Product/QA | Prevent false readiness |
| Fix typecheck scope or active errors | Critical | High | Critical | tsconfig/package boundaries | Frontend/platform | Build health is red |
| Fix workspace settings route identity | Critical | Small-medium | High | Settings route | Frontend/product | Prevent wrong workspace edits |
| Remove/update stale auth route tests | High | Small | Medium | Test cleanup | Frontend/QA | Current tests fail on deleted routes |
| Align onboarding request contract | High | Medium | High | API contract | Frontend/backend | First-run flow correctness |

### Short-Term

| Item | Priority | Effort | Impact | Dependencies | Owner type | Rationale |
|---|---:|---:|---:|---|---|---|
| Generate route inventory from `routes.ts` | High | Medium | High | Script/CI | Frontend/QA | Stops route-doc drift |
| Quarantine latent pages from release compile | High | Medium | High | tsconfig/package boundary | Frontend/platform | Stops unsupported code breaking release |
| Centralize auth/session/token access | High | High | High | Auth API | Security/frontend | Reduces privacy/security drift |
| Replace reload retries with scoped refetch | Medium | Medium | Medium | PageState component | Frontend | Preserves context |
| Wire keyboard shortcuts panel action | Medium | Small | Medium | Shell state | Frontend | Completes help surface |
| Persist deploy reject/hold decisions | High | Medium | High | Lifecycle/deploy API | Frontend/backend | Governance completeness |

### Medium-Term

| Item | Priority | Effort | Impact | Dependencies | Owner type | Rationale |
|---|---:|---:|---:|---|---|---|
| Server-backed onboarding completion/personas | High | Medium-high | High | Workspace API | Product/backend/frontend | Correct first-run truth |
| Unified canvas sync state/history | High | High | High | Canvas persistence API | Frontend/backend | Prevent data loss/confusion |
| AI confidence/evidence framework | High | Medium | High | ML/product standards | ML/frontend/product | Safe embedded AI |
| Route-aware contextual guidance across all mounted routes | Medium | Medium | Medium | Guidance model | Design/frontend | Reduces cognitive load |
| Current-release E2E gate cleanup | High | Medium | High | E2E ownership | QA/frontend | Makes CI signal meaningful |
| Mobile strategy decision | Medium | Medium | Medium | Product scope | Product/design/frontend | Avoids false mobile completeness |

### Long-Term

| Item | Priority | Effort | Impact | Dependencies | Owner type | Rationale |
|---|---:|---:|---:|---|---|---|
| Reintroduce latent modules only as product-validated areas | Medium | High | High | Contracts, route plan | Product/frontend/backend | Prevents scope sprawl |
| Intent-first canvas generation | High | High | High | AI/canvas APIs | ML/frontend/product | Major simplification |
| Full design-system consolidation | High | High | High | DS migration | Design system/frontend | Durable consistency |
| Release evidence automation | High | Medium | High | CI | Platform/QA | Prevents tracker drift |
| Storage governance and migration | High | High | High | Security/platform | Security/frontend | Privacy and reliability |

## 14. Final Ideal UX Vision

YAPPC should feel like a focused product-building cockpit, not a museum of every historical surface. A user opens the app, signs in, lands on a dashboard that tells them the one or two most useful next actions, and can move from workspace to project to lifecycle/canvas/deploy without wondering which route tree is real.

The system should quietly manage setup, context, and state. Onboarding should create backed workspace and project records, remember persona preferences server-side, and explain what was created. The dashboard should rank work based on real activity, blockers, and lifecycle state. The project cockpit should summarize health, blockers, and next action before exposing deeper details.

Canvas should be powerful but calm. The default should be the next lifecycle task, not a buffet of modes. Users should see whether work is local-only, syncing, saved, failed, stale, or conflicted. AI should draft artifacts, recommend next actions, and explain evidence only where it reduces work.

Deploy should feel like release planning unless and until it is wired to live deployment execution. It should show readiness, risk, approvals, incidents, and the exact boundary between recommendation and action. Every approval, rejection, hold, and phase advance should have durable evidence.

Correctness should be maintained through generated route truth, browser render smoke, typecheck-clean active scope, active-route E2E gates, one auth/session source, and one storage policy. Docs and trackers should never say a runtime blocker is fixed unless the browser proves it.

Trust should be quiet but pervasive: clear workspace scope, role-aware actions, safe destructive flows, storage minimization, operation history, and evidence-linked AI recommendations. The user should feel guided, not buried in implementation detail.

## 15. Executive Summary Lists

### Top 10 Critical Issues
1. Live app crashes with `require is not defined`.
2. Tracker marks the runtime crash fixed despite current failure.
3. Static smoke test passes while live app is unusable.
4. Full web typecheck fails with many errors.
5. Workspace settings can target the wrong workspace.
6. Auth/session/token model is fragmented.
7. Active product compile includes latent/broken surfaces.
8. Canvas sync truth is incomplete and local-only.
9. Onboarding request shape does not match type contract.
10. Route/docs/tests disagree on product truth.

### Top 10 Completeness Gaps
1. Runtime-safe login/dashboard render.
2. Generated route inventory.
3. Server-backed onboarding completion.
4. Workspace settings scoped by route identity.
5. Canvas sync and operation history.
6. Durable deploy reject/hold decisions.
7. Live preview state machine.
8. Mobile support decision.
9. Active-route release E2E gate.
10. Storage governance.

### Top 10 Simplification Opportunities
1. Keep mounted IA small and archive latent sprawl.
2. Generate docs from route truth.
3. Merge search and command palette.
4. Replace local-only onboarding flags with server setup state.
5. Make dashboard next-action driven.
6. Make canvas task-first.
7. Hide raw IDs/advanced metadata by default.
8. Move latent modules out of active build.
9. Replace reloads/confirms with shared patterns.
10. Standardize auth/token access.

### Top 10 Correctness Issues
1. Browser runtime crash.
2. Typecheck failure.
3. Stale auth route imports.
4. Stale lifecycle test ID.
5. Stale mounted route inventory.
6. Workspace settings query/atom mismatch.
7. Onboarding API contract mismatch.
8. Project overview contract mismatch.
9. Missing lifecycle enum cases.
10. Deploy rejection not persisted.

### Top 10 Consistency Issues
1. Mounted routes vs route files vs pages.
2. Docs vs current route config.
3. Tracker vs live browser.
4. Static tests vs runtime behavior.
5. MUI vs Ghatana DS vs shims vs Tailwind.
6. Cookie vs localStorage auth.
7. `auth-session` vs `auth_token`.
8. Reload vs refetch recovery.
9. Browser confirm vs custom dialogs.
10. Mobile route files vs mounted product.

### Top 10 AI/ML Opportunities
1. Dashboard next-best action.
2. Onboarding inference.
3. Project health/blocker summary.
4. Canvas task guidance.
5. Intent-to-canvas artifact generation.
6. Lifecycle promotion support.
7. Deploy risk and capacity planning.
8. Readiness anomaly grouping.
9. Canvas sync conflict resolution.
10. Product-truth drift detection.

### Top 10 Trust / Visibility / Privacy / Security Improvements
1. Fix live render and show frontend/backend status clearly.
2. Centralize session/token handling.
3. Remove direct localStorage token reads.
4. Add storage policy and migration plan.
5. Make workspace scope explicit before edits/deletes.
6. Add operation history for important actions.
7. Persist deploy/lifecycle decisions with reason.
8. Add evidence links for AI recommendations.
9. Use safe dialogs for destructive actions.
10. Keep unsupported latent surfaces out of release scope.

## Appendix: Verification Notes

| Check | Outcome |
|---|---|
| Live browser `http://localhost:7002/` | Failed with Vite overlay: `require is not defined` from `@mui/material/index.js`; title `Error`. |
| Live browser `http://localhost:7002/login` | Same runtime failure. |
| `pnpm --filter @yappc/web-app run test:smoke` | Passed 1 file / 1 test, but only statically reads `src/routes.ts`. |
| `pnpm --filter @yappc/web-app run typecheck` | Failed, exit 2, with large TypeScript error set across platform utils, YAPPC UI, theme bridge, lifecycle, canvas, archived routes, generated outputs, and latent pages. |
| Targeted Vitest route tests | 2 files passed, 2 failed; auth routes failed importing deleted routes; lifecycle test failed stale test ID. |
| Route/file count | 13 active route calls, 72 route `.tsx` files, 88 `src/pages` `.tsx` files. |
