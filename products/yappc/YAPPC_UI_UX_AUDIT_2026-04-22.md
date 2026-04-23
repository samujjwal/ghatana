# YAPPC UI/UX Audit

Date: 2026-04-22  
Product audited: YAPPC  
Primary audited surface: `products/yappc/frontend/web`  
Audit mode: Evidence-based repo audit with targeted test and browser verification

## Scope and assumptions

This audit is for the YAPPC product only.

The current mounted product truth is the React Router v7 app defined in `products/yappc/frontend/web/src/routes.ts`. That active route tree is materially smaller than the surrounding file inventory, historical route specs, legacy pages, DevSecOps route docs, mobile route files, and the older parallel router under `src/router/routes.tsx`.

This report therefore audits:

1. The currently mounted product experience.
2. User-facing surfaces that still exist in the repo but are unregistered, stale, or contradicted by the active router.
3. UX-affecting implementation details, testing gaps, route drift, and runtime failures that affect trust, adoption, and maintenance.

Where docs and implementation conflict, implementation is treated as the current runtime truth and the conflict is called out explicitly.

---

## 1. Executive Summary

YAPPC has a promising simplified product direction in the currently mounted web app: root dashboard, onboarding, workspaces, projects, a project cockpit, a unified canvas, lifecycle guidance, deploy planning, and scoped settings. Several of those mounted pages are materially better than the surrounding codebase suggests. The workspaces, projects, project overview, project settings, and deploy surfaces are generally cleaner and more truthful than the older YAPPC documentation.

However, the product is not currently trustworthy enough as a whole because the runtime, route map, docs, tests, and adjacent UX surfaces are badly out of sync.

The biggest systemic issue is not visual polish. It is product truth fragmentation:

- the live router exposes a small route set
- the repo contains many more user-facing routes and page specs that are not mounted
- tests still target old `/app/...` and `/app/w/:workspaceId/...` paths
- browser smoke verification currently fails on a live runtime overlay before the workspaces screen renders
- accessibility verification is partially blocked by broken shared test utilities

The result is a product that looks more complete on paper than it is in current runtime behavior. That creates high cognitive load for users, designers, engineers, and QA at the same time.

Overall UX health: `High Risk`  
Overall production readiness of the product experience: `Not production-ready as a coherent product surface`

The strongest current product assets are:

- a simplified route truth in `src/routes.ts`
- a good empty/guest/authenticated dashboard split
- a real onboarding flow
- a useful workspace and project management core
- a strong project overview cockpit
- a deploy screen that uses readiness and gate concepts instead of pure decoration
- a settings strategy that hides unsupported admin capabilities instead of faking them

The biggest weaknesses are:

- live runtime crash in browser smoke verification
- route taxonomy drift across runtime, specs, tests, and adjacent route files
- duplicate or dormant surfaces still present in the codebase
- weak consistency around auth, storage, API access, and global navigation/search
- excessive canvas implementation complexity
- poor confidence in accessibility and smoke coverage because the verification stack itself is partially broken

---

## 2. Full UX Scorecard

| Area | Rating | Justification |
|---|---|---|
| Information architecture | High | The active IA is simpler than historical YAPPC, but the repo still contains a much larger parallel IA that creates major truth drift. |
| Navigation | Critical | Runtime routes, tests, specs, and old router files disagree on core path structure. |
| Workflow simplicity | High | Several active flows are good, but users still face redundant selection layers and duplicate canvas entry points. |
| Visual design | Medium | Mounted surfaces generally use a modern tokenized style, but quality is inconsistent and some areas still feel transitional. |
| Interaction quality | Medium | Many active pages behave predictably, but dead buttons, hidden unsupported actions, and route mismatches reduce confidence. |
| Accessibility | High | Good intent exists, but accessibility verification is unreliable and the browser runtime currently fails before meaningful full-surface a11y checks can run. |
| AI/ML embedded experience | Medium | AI is present in onboarding, health, insights, and lifecycle guidance, but many high-value flows still surface AI as add-on hints rather than deeply embedded automation. |
| Observability / transparency UX | Medium | Lifecycle/deploy transparency is strong, but shell-level visibility and insight surfaces are not consistently contextual. |
| Privacy / security UX | High | Auth and storage patterns are fragmented, and disabled auth recovery routes still exist as first-class-looking pages. |
| Consistency / reuse | Critical | There are too many parallel route systems, dormant surfaces, duplicate navigation concepts, and stale design/test artifacts. |
| Responsive behavior | High | Mobile routes exist but are not mounted in the active router, so responsive strategy is not truthfully delivered. |
| Perceived performance | High | The live smoke path currently hits a runtime overlay, and the canvas surface is extremely heavy. |
| Cognitive load | High | Users and contributors both need to know which YAPPC is real: the mounted app, the old docs, the legacy pages, or the unregistered route files. |
| Overall product usability | High | The core mounted app has promise, but the surrounding fragmentation prevents a reliable, low-cognitive-load product experience. |

---

## 3. Complete Surface-by-Surface Audit

### 3.1 Route truth and shell

Purpose:
- define the real mounted product
- provide global shell, header, keyboard access, command palette, and insight visibility

Current quality:
- structurally cleaner than older YAPPC documentation
- strong root shell intent
- weak product truth governance

Evidence:
- active route truth: `products/yappc/frontend/web/src/routes.ts`
- parallel legacy router still present: `products/yappc/frontend/web/src/router/routes.tsx`
- shell: `products/yappc/frontend/web/src/routes/_shell.tsx`
- root layout: `products/yappc/frontend/web/src/routes/_root.tsx`

Issues:
- current mounted router exposes a narrow set of routes, but the repo still carries route files, specs, tests, and helper utilities for far more surfaces.
- `src/router/routes.tsx` still describes a much larger application model and is a second source of routing truth.
- `web-page-specs/INDEX.md` still documents a 42-route product with `/app`, `/devsecops`, `/workflows`, and `/mobile` areas that are not represented in the active route config.
- shell help opens `/docs` in a new tab via `window.open`, which is coarse and non-contextual.
- the shell exposes command palette and insight panel globally, but global search and action systems are not unified.
- the live shell currently cannot be trusted in browser verification because loading `/app/workspaces` triggers a runtime error overlay: `require is not defined`.

Recommendations:
- make `src/routes.ts` the single documented route truth.
- archive or delete `src/router/routes.tsx` unless it is still actively mounted somewhere else.
- remove or clearly quarantine unmounted route files from the primary product tree.
- rewrite route specs and E2E suites to the active taxonomy before adding more features.
- replace generic help navigation with contextual task help, page help, and “what to do next” assistance.

Automation opportunities:
- auto-detect stale routes, docs, and tests in CI.
- auto-generate route truth docs from `src/routes.ts`.
- auto-flag when a route file exists but is not mounted.

Trust / visibility / privacy considerations:
- users cannot trust navigation when old route structures still appear in tests and docs.
- developers cannot trust coverage when smoke tests point to routes that no longer exist.

### 3.2 Root dashboard `/`

Purpose:
- guest landing
- authenticated resume point
- empty-state entry to create work

Current quality:
- one of the stronger current surfaces

Evidence:
- `products/yappc/frontend/web/src/routes/dashboard.tsx`

Strengths:
- clearly differentiates guest, empty, loading, and authenticated states.
- prioritizes resume/create/blocker review instead of flooding the user with every feature.
- recent projects section is appropriately task-oriented.

Issues:
- it still depends on the surrounding route system being coherent, which it is not.
- “review blockers” sends users to `/workspaces`, which adds another list screen rather than showing workspace blockers inline.
- dashboard intelligence is still largely static routing logic instead of true next-best-action orchestration.

Recommendations:
- show workspace issues inline on the dashboard and use `/workspaces` as a management surface, not the primary blocker review path.
- use AI/automation to rank the single most urgent project, single most urgent blocker, and single best next action.

### 3.3 Login `/login`

Purpose:
- user authentication

Current quality:
- visually clean and focused
- functionally incomplete around adjacent auth journeys

Evidence:
- `products/yappc/frontend/web/src/routes/login.tsx`
- `products/yappc/frontend/web/src/services/auth/AuthService.ts`

Strengths:
- simple form
- handles session-expired banner
- demo login is gated by env

Issues:
- login exists, but register and forgot-password pages still exist in code while the active router excludes them.
- `AuthService.register()` and `AuthService.forgotPassword()` are hard-disabled and return failure messages, but the route files still present them as real pages.
- auth state is spread across session bootstrap, `auth-session` localStorage, and other token lookups in adjacent services.

Recommendations:
- either fully wire register/reset flows with backed contracts or remove them from the main product tree and archive them.
- centralize auth token access behind one session source.
- replace generic auth disablement with truthful product copy inside login if self-service account management is unavailable.

Trust / privacy / security considerations:
- disabled but present account-recovery flows create false expectations and weaken trust.

### 3.4 Onboarding `/onboarding`

Purpose:
- first-run setup of workspace and starter project

Current quality:
- strong overall
- needs clearer governance and transparency

Evidence:
- `products/yappc/frontend/web/src/routes/onboarding.tsx`
- `products/yappc/frontend/web/src/components/workspace/OnboardingFlow.tsx`

Strengths:
- real flow exists
- progressive, short, and generally well scoped
- AI suggestions are embedded as name suggestions, not as a gimmick

Issues:
- onboarding state is controlled via `localStorage` key `onboarding_complete`, which is fragile for shared devices, resets, or multi-session behavior.
- there is no clear visibility into what defaults will be created, what can be changed later, and which parts are AI-suggested vs persisted truth.
- persona selection is useful, but the product does not clearly show how those choices affect the later experience.

Recommendations:
- persist onboarding completion and starter choices server-side.
- show an explicit review step of what will be created.
- connect selected personas to the actual shell, recommendations, and project defaults so the choice has visible value.

Automation opportunities:
- infer starter project type from uploaded docs or stated goal.
- prefill workspace/project naming based on repo or imported context.
- recommend whether to create one starter project or skip straight to workspace-only setup.

### 3.5 Workspaces `/workspaces`

Purpose:
- manage workspaces
- switch active workspace

Current quality:
- useful and reasonably clean
- over-automated in one important edge case

Evidence:
- `products/yappc/frontend/web/src/routes/app/workspaces.tsx`
- tests: `products/yappc/frontend/web/src/routes/__tests__/workspaces-route.test.tsx`

Strengths:
- clear cards and open action
- good unavailable-service fallback

Issues:
- on first empty entry, the page auto-creates a starter workspace and default project without an explicit confirmation step.
- that behavior reduces friction, but it also reduces user control and transparency.
- clicking the card and clicking “Open” do the same thing; interaction redundancy is minor but unnecessary.
- workspace settings action jumps to global `/settings`, not workspace-scoped settings, which is semantically confusing.

Recommendations:
- keep starter automation, but add an explicit “Create suggested workspace” confirmation with editable name before commit.
- relabel or route the settings action more truthfully.
- if there is only one workspace, consider skipping this page entirely after onboarding and route directly into the most relevant project or the project list.

### 3.6 Projects `/projects`

Purpose:
- cross-project overview and project creation

Current quality:
- good basic management surface
- still heavier than needed for single-workspace/single-team use

Evidence:
- `products/yappc/frontend/web/src/routes/app/projects.tsx`

Strengths:
- useful filters and search
- clear ownership distinction
- create dialog entry is prominent

Issues:
- the projects page is valuable for management, but in day-to-day use it likely becomes an extra step between dashboard and project work.
- the page assumes workspace context indirectly and does not strongly reinforce which workspace the user is operating in.
- “grid vs list” adds choice without major task-value on small datasets.

Recommendations:
- default daily users to “resume work” from dashboard, not project browsing.
- keep `/projects` as a management surface for power use.
- simplify project browsing when project count is low.

Automation opportunities:
- rank projects by urgency, recent activity, blocked status, and approval needs.
- deduplicate similar project names.
- recommend archival or cleanup candidates.

### 3.7 Project shell `/p/:projectId/*`

Purpose:
- top-level project workspace

Current quality:
- cleaner than older YAPPC tab structures
- still contains avoidable duplication

Evidence:
- `products/yappc/frontend/web/src/routes/app/project/_shell.tsx`

Strengths:
- tabs reduced to a more coherent set
- overview is the index route
- unsupported advanced capabilities are generally not faked in the mounted pages

Issues:
- both `Canvas` and `Workspace` appear as top-level tabs even though `canvas-workspace.tsx` simply re-exports the main canvas route.
- preview is feature-flagged into the tab model, which can shift navigation structure depending on environment.
- project shell fetches project data directly and also configures header actions locally; this is useful but increases duplication pressure with the overview and other route-level fetches.

Recommendations:
- merge `Canvas` and `Workspace` into one route with mode toggles or panels.
- stabilize the project tab model across environments where possible.
- centralize project shell data loading and share it with child routes.

### 3.8 Project overview `/p/:projectId`

Purpose:
- cockpit view for phase, health, readiness, recent activity, and next action framing

Current quality:
- one of the best current product surfaces

Evidence:
- `products/yappc/frontend/web/src/routes/app/project/index.tsx`

Strengths:
- strong status framing
- useful promotion gate summary
- good activity and readiness emphasis
- largely low-noise and decision-oriented

Issues:
- health score is labeled as rule-based completeness, but explanation depth is still shallow.
- the page exposes strong status concepts but does not always turn them into one-click next steps.
- project health, readiness, and activity could be more tightly tied to downstream route actions.

Recommendations:
- add contextual next actions like “open lifecycle blockers,” “review deploy plan,” or “resume canvas changes.”
- expose confidence and evidence sources behind readiness in a compact expandable pattern.

### 3.9 Unified canvas `/p/:projectId/canvas`

Purpose:
- central creation and implementation surface

Current quality:
- strategically important
- functionally ambitious
- systemically too complex

Evidence:
- `products/yappc/frontend/web/src/routes/app/project/canvas.tsx` (`926` lines)
- `products/yappc/frontend/web/src/components/canvas/CanvasWorkspace.tsx` (`721` lines)

Strengths:
- rich canvas capability
- collaboration, AI, role/phase info, export, lifecycle zones, and multiple panels are all present

Issues:
- the route orchestrator is too large and couples too many responsibilities.
- the canvas is still a convergence point for many legacy and experimental concepts.
- multiple overlapping modes and panels increase cognitive load.
- there is a mismatch between the product promise of “simple, AI-first, low-friction” and the actual breadth of tools exposed here.
- accessibility claims around canvas are present, but the verification story is not currently trustworthy enough.

Recommendations:
- split canvas into a smaller default mode and role/task-specific advanced panels.
- keep the canvas as the deep workspace, but stop making it the container for every conceptual surface.
- move rarely used tooling behind explicit advanced or context-triggered entry points.

Automation opportunities:
- context-aware panel opening
- smart node insertion
- deduplication of duplicated artifacts
- auto-layout
- AI-assisted cleanup and explanation of graph changes
- exception triage instead of constant user supervision

### 3.10 Canvas workspace `/p/:projectId/canvas-workspace`

Purpose:
- currently presented as a distinct tab

Current quality:
- not distinct enough to justify existence

Evidence:
- `products/yappc/frontend/web/src/routes/app/project/canvas-workspace.tsx`

Issue:
- this route is just a wrapper around the main canvas route and creates duplicate navigation with unclear user value.

Recommendation:
- remove it or turn it into a meaningful mode within the canvas route.

### 3.11 Preview `/p/:projectId/preview`

Purpose:
- preview via external host

Current quality:
- truthful but thin

Evidence:
- `products/yappc/frontend/web/src/routes/app/project/preview.tsx`

Strengths:
- does not pretend to run a local preview when it cannot
- clearly exposes unconfigured state

Issues:
- once configured, the surface is mostly an iframe wrapper with device toggles.
- there is limited visibility into preview freshness, build source, environment, or validation status.
- there is no clear handoff from preview findings back into build or canvas corrections.

Recommendations:
- show build source, commit/version, environment, last refresh time, and snapshot status.
- embed quick issue reporting and “return to fix” actions.

### 3.12 Lifecycle `/p/:projectId/lifecycle`

Purpose:
- lifecycle explorer, readiness anomalies, recommendations, and guided automation

Current quality:
- conceptually strong
- still information-dense

Evidence:
- `products/yappc/frontend/web/src/routes/app/project/lifecycle.tsx`

Strengths:
- strong decision-support framing
- useful anomaly and recommendation logic
- good human-review vs guided-apply distinction

Issues:
- there is still a lot of concept density for one screen.
- readiness, anomalies, stage labels, recommendations, insights, next task, and automation plan all compete for attention.
- the page is closer to an operator cockpit than a low-cognitive-load lifecycle navigator.

Recommendations:
- collapse this into three visible layers:
  - current stage and readiness
  - blockers and next action
  - deeper evidence on demand

### 3.13 Deploy `/p/:projectId/deploy`

Purpose:
- deployment planning, promotion readiness, operator notes, and incident actions

Current quality:
- one of the stronger advanced surfaces

Evidence:
- `products/yappc/frontend/web/src/routes/app/project/deploy.tsx`

Strengths:
- uses lifecycle readiness instead of showing a fake deployment dashboard
- clearly distinguishes blocked, approval-needed, and planning-ready states
- keeps human review in the loop where risk is high

Issues:
- the page is still dense and combines several responsibilities.
- incident creation and operator controls could be more contextual.
- deploy is strong as an operator page, but it lacks a slimmed-down “safe summary” for less technical users.

Recommendations:
- keep the current model, but simplify the top of page into a single “can I deploy now?” frame with progressively disclosed details.

### 3.14 Project settings `/p/:projectId/settings`

Purpose:
- edit supported project fields

Current quality:
- strong and appropriately restrained

Evidence:
- `products/yappc/frontend/web/src/routes/app/project/settings.tsx`

Strengths:
- only backed fields are editable
- unsupported advanced capabilities are explicitly hidden
- advanced metadata is disclosure-based

Issues:
- this restraint is good, but the page styling and language still imply a fuller admin/settings area than currently exists.

Recommendation:
- rename unsupported areas as “not available in this deployment” consistently across project and workspace settings.

### 3.15 Profile `/profile`

Purpose:
- current user profile view

Current quality:
- acceptable but misleadingly interactive

Evidence:
- `products/yappc/frontend/web/src/routes/profile.tsx`

Issues:
- save/cancel framing remains even though editing is disabled.
- the page should either be a read-only profile summary or a real editable form, not a hybrid.

Recommendation:
- simplify to read-only account summary until profile editing is genuinely backed.

### 3.16 Workspace settings `/settings`

Purpose:
- manage supported workspace settings

Current quality:
- generally good

Evidence:
- `products/yappc/frontend/web/src/routes/settings.tsx`

Strengths:
- bounded scope
- advanced metadata hidden by default
- save state visibility exists

Issues:
- the “Danger Zone” pattern is fine, but full workspace management remains a little isolated from the rest of the product journey.

### 3.17 Not found `/404`

Purpose:
- recovery from bad routes

Current quality:
- visually fine

Issues:
- “Contact support” links back to `/`, which is misleading.

Recommendation:
- link to actual help/support or relabel as “Return home”.

### 3.18 Dormant and unregistered surfaces

Purpose:
- historical or not-currently-mounted route and page inventory

Evidence:
- unregistered mobile route files: `src/routes/mobile/*`
- unregistered auth recovery routes: `src/routes/register.tsx`, `src/routes/forgot-password.tsx`
- orphaned IDE route: `src/routes/ide.tsx`
- large page inventory: `src/pages` contains `98` files
- route file inventory under `src/routes`: `47` tsx files
- active route declarations in `src/routes.ts`: only `14`

Issues:
- users do not see these directly, but they heavily affect UX quality by corrupting docs, tests, design assumptions, and product planning.
- specs and generated analyses still describe products that are not the active mounted app.

Recommendations:
- create an explicit `active`, `experimental`, and `archived` separation for route/page surfaces.
- move unmounted surfaces out of the primary path tree or clearly namespace them.

---

## 4. Complete End-to-End Flow Review

### 4.1 Discover and sign in

Goal:
- understand YAPPC and access the product

Current steps:
1. open `/`
2. see guest landing
3. go to `/login`
4. authenticate
5. land in workspace flow

Friction:
- login is real, but surrounding account recovery and registration affordances are not honestly available in the active mounted app.
- product truth is undermined by disabled-but-present adjacent auth pages.

Ideal future state:
- one clear authentication path
- explicit support for self-serve or invite-only access
- no dead-end auth pages in code or documentation

### 4.2 First-run onboarding

Goal:
- get a new user into a working workspace and starter project quickly

Current steps:
1. shell checks `onboarding_complete` in localStorage
2. user enters onboarding
3. chooses workspace name and personas
4. chooses starter project type
5. completes setup and lands in workspace/project flow

Friction:
- local-only completion flag
- unclear review of what exactly will be created

Ideal future state:
- server-backed onboarding state
- explicit “you are about to create” review
- immediate jump to the right next screen without extra list navigation

### 4.3 Empty user to first workspace

Goal:
- avoid dead empty management screens

Current steps:
1. user reaches `/workspaces`
2. if no workspaces exist, the page auto-creates a suggested starter workspace and default project
3. user is redirected to `/projects`

Friction:
- high automation with weak transparency
- the system acts before explicit user confirmation

Ideal future state:
- suggested workspace creation remains one-click, but user explicitly approves it first

### 4.4 Resume existing work

Goal:
- get back to the highest-value project fast

Current steps:
1. open `/`
2. pick recent project or review blockers
3. possibly visit `/workspaces`
4. possibly visit `/projects`
5. open `/p/:projectId`

Friction:
- too many possible list surfaces between entry and real work
- blocker review is routed away from the dashboard instead of being embedded

Ideal future state:
- root dashboard recommends one project and one next action
- `/projects` becomes optional management, not a required step

### 4.5 Create a project

Goal:
- create a new project and enter the cockpit

Current steps:
1. create from dashboard or projects page
2. use dialog and API-backed creation
3. navigate to project routes

Friction:
- project creation exists in multiple entry points
- workspace context is not always made explicit enough in the create moment

Ideal future state:
- one canonical create flow reused everywhere
- contextual defaults from current workspace

### 4.6 Understand current project state

Goal:
- know where the project is and what to do next

Current steps:
1. open `/p/:projectId`
2. review phase, health, readiness, activity
3. decide between canvas, lifecycle, preview, deploy, settings

Friction:
- good page, but next-action conversion still depends on user interpretation

Ideal future state:
- overview provides one primary CTA and one secondary CTA derived from readiness and blockers

### 4.7 Work in canvas

Goal:
- create or modify the product through the unified canvas

Current steps:
1. open `/p/:projectId/canvas`
2. interact with heavy multi-panel environment
3. possibly use collaboration, AI, export, lifecycle tools, and studio helpers

Friction:
- too much exposed at once
- duplicated canvas entry via `canvas-workspace`
- large implementation surface likely reduces robustness

Ideal future state:
- a calmer default canvas with context-triggered advanced panels

### 4.8 Review lifecycle readiness

Goal:
- understand promotion/blocker state and apply automation safely

Current steps:
1. open `/p/:projectId/lifecycle`
2. review anomalies, insights, recommendations, automation plan
3. possibly trigger guided automation

Friction:
- high information density

Ideal future state:
- summary first
- evidence second
- automation third
- all in one clear progression

### 4.9 Preview and validate

Goal:
- see the built experience before deployment

Current steps:
1. open `/preview`
2. if configured, use external preview host iframe
3. otherwise see unavailable state

Friction:
- little integration with build state, issue capture, or feedback loop

Ideal future state:
- preview linked to build source, validation status, and fix loop

### 4.10 Deploy or promote

Goal:
- advance lifecycle safely

Current steps:
1. open `/deploy`
2. inspect readiness and blockers
3. enter notes or incident actions
4. advance phase if allowed

Friction:
- page density
- operator-centric language may be heavier than needed for non-operators

Ideal future state:
- deploy summary first
- operator controls disclosed beneath

### 4.11 Profile, workspace settings, and recovery

Goal:
- manage account and environment

Current state:
- profile is largely read-only
- settings pages are bounded and mostly good
- register and forgot-password pages exist but are not active product routes

Ideal future state:
- one truthful account surface
- no dormant auth flows in the main app tree

---

## 5. Comprehensive Findings Catalog

| ID | Title | Severity | Category | Affected surface | Evidence | Why it matters | Recommended fix |
|---|---|---|---|---|---|---|---|
| YAPPC-F001 | Live browser smoke path crashes with `require is not defined` overlay | Critical | Runtime / UX | Shell, workspaces, overall entry | Playwright smoke screenshot and `error-context.md` under `frontend/e2e/test-results/e2e-local/smoke-basic-navigation-works-chromium/` | Users cannot reach the product reliably; all downstream UX quality is blocked by runtime failure | Fix the `@ghatana/design-system` / `@mui/material` runtime import path so the mounted shell renders in Vite without overlay failure |
| YAPPC-F002 | Runtime route truth conflicts with docs, tests, and historical path taxonomy | Critical | IA / Navigation | Entire product | `src/routes.ts`, `web-page-specs/INDEX.md`, `e2e/smoke.spec.ts`, `e2e/accessibility-navigation.spec.ts` | Users and teams cannot trust where the product lives or how to navigate it | Publish one route source of truth, then rewrite tests and specs to match it |
| YAPPC-F003 | Parallel router definitions create multiple product truths | High | Consistency / Architecture | Routing, navigation | `src/routes.ts` and `src/router/routes.tsx` | Product decisions, QA, and docs can drift indefinitely when multiple router models remain in-tree | Remove or archive the legacy router if it is not the mounted product |
| YAPPC-F004 | Route smoke coverage is broken from the standard frontend workspace root | High | Testing / Reliability | Verification stack | `web/src/__tests__/routes.spec.ts` fails because it looks for `frontend/src/routes.ts` | The team can get false negatives and lose confidence in route validation | Make route tests resolve the actual `web/src/routes.ts` path from the workspace root or move them into the `web` package contract |
| YAPPC-F005 | Shared design-system test utility contains a syntax error that blocks workspaces and accessibility suites | High | Testing / Accessibility | Shared UI verification | `platform/typescript/design-system/src/utils/testing.ts` begins with stray `l/**` | Accessibility and route confidence are reduced because suites fail before validating UX behavior | Fix the shared testing utility and rerun affected suites |
| YAPPC-F006 | Unregistered auth recovery surfaces remain in the product tree while the active app excludes them | High | Trust / Auth UX | Register, forgot password | `src/routes/register.tsx`, `src/routes/forgot-password.tsx`, `src/routes.ts`, `AuthService.ts` | Users can be promised flows the product does not actually support | Either wire these flows fully or archive them outside the active route tree |
| YAPPC-F007 | Auth service disables register and forgot-password logic but leaves surrounding UX artifacts intact | High | Security / Trust | Auth | `AuthService.register()`, `AuthService.forgotPassword()` | The product appears more self-serve than it is | Centralize truthful account capability messaging in login and remove unsupported affordances |
| YAPPC-F008 | Mobile route files exist but are not part of the active route config | High | Responsive UX | Mobile | `src/routes/mobile/*`, absent from `src/routes.ts` | Mobile support looks more complete than it is, which distorts roadmap, QA, and product expectations | Move mobile work behind an experimental namespace or mount it fully |
| YAPPC-F009 | Legacy IDE route is still present as an orphaned placeholder | Medium | Cleanup / Consistency | IDE | `src/routes/ide.tsx` | Retained placeholder surfaces increase noise and route uncertainty | Archive the route or remove it after migration is complete |
| YAPPC-F010 | Auto-creating a starter workspace on empty entry is too implicit | Medium | Workflow / Automation | Workspaces | `src/routes/app/workspaces.tsx` | Helpful automation can become surprising or governance-hostile without explicit confirmation | Keep the suggestion, but ask for one-click confirmation before creation |
| YAPPC-F011 | Workspace settings action from workspace cards routes to global settings, not workspace-scoped settings | Medium | Navigation | Workspaces / Settings | `src/routes/app/workspaces.tsx` | Users can lose context and misinterpret scope | Route to a workspace-scoped settings view or relabel the action |
| YAPPC-F012 | Dashboard sends blocker review to a list page instead of showing blockers inline | Medium | Workflow simplicity | Dashboard | `src/routes/dashboard.tsx` | Adds extra navigation and weakens the dashboard as a resume cockpit | Embed top blockers and reserve `/workspaces` for management |
| YAPPC-F013 | Projects page adds management choice where daily resume could be automatic | Medium | Cognitive load | Projects | `src/routes/app/projects.tsx`, `src/routes/dashboard.tsx` | Too many list surfaces slow task resumption | Use dashboard as the primary resume surface and keep `/projects` as secondary management |
| YAPPC-F014 | Project shell exposes duplicate `Canvas` and `Workspace` tabs without distinct value | High | Navigation / Simplicity | Project shell | `src/routes/app/project/_shell.tsx`, `canvas-workspace.tsx` | Duplicate top-level choices create needless cognitive load | Merge into one canvas route with submodes or role panels |
| YAPPC-F015 | `canvas-workspace` is only a wrapper around the main canvas route | High | Reuse / IA | Project shell | `src/routes/app/project/canvas-workspace.tsx` | Duplicate routing with no differentiated experience wastes attention and maintenance | Remove route or implement a truly distinct mode |
| YAPPC-F016 | Unified canvas route is too large and responsibility-heavy | High | Interaction / Maintainability | Canvas | `src/routes/app/project/canvas.tsx` (`926` lines) | A critical surface becomes fragile, harder to reason about, and harder to simplify | Split default canvas, advanced tooling, and lifecycle/operator concerns into smaller compositions |
| YAPPC-F017 | Preview route is truthful but too thin to support a real validate-before-deploy loop | Medium | Workflow / Visibility | Preview | `src/routes/app/project/preview.tsx` | Users see an iframe, not a review surface with context, freshness, or issue capture | Add preview provenance, build state, validation, and fix handoff |
| YAPPC-F018 | Lifecycle route is conceptually valuable but too dense for low-cognitive-load navigation | Medium | IA / Workflow | Lifecycle | `src/routes/app/project/lifecycle.tsx` | Users must synthesize many status concepts at once | Reframe into summary, blockers, and evidence layers |
| YAPPC-F019 | Profile page presents a quasi-edit form even though editing is disabled | Low | Content / Interaction | Profile | `src/routes/profile.tsx` | Disabled save/cancel affordances waste attention | Convert to read-only account summary until editing is truly backed |
| YAPPC-F020 | Not-found page promises support but links home | Low | Content / Trust | 404 | `src/routes/not-found.tsx` | Small but trust-eroding content mismatch | Link to real support or relabel the action |
| YAPPC-F021 | API access is fragmented across raw `fetch`, custom parsers, and multiple auth/storage paths | High | Consistency / Frontend architecture | Entire product | `lib/http.ts`, `useWorkspaceData.ts`, many route files, `AuthService.ts`, `CanvasAPIClient.ts` | Inconsistent error handling and auth behavior directly affect UX reliability | Centralize browser API access and token/session handling |
| YAPPC-F022 | Auth and operational state use too many localStorage/sessionStorage keys | High | Privacy / Security UX | Auth, onboarding, canvas | `AuthService.ts`, `_shell.tsx`, `onboarding.tsx`, canvas services | Inconsistent persistence weakens trust, recovery, and security posture | Consolidate into one product session and one governed local cache policy |
| YAPPC-F023 | Global search remains mock-based and points to dead route patterns | Medium | Search / Discoverability | Search | `src/components/search/GlobalSearch.tsx` | A search surface that returns dead routes erodes trust immediately if ever activated | Remove mock global search from production path or connect it to real mounted routes |
| YAPPC-F024 | Command palette and global search are parallel discovery models instead of one truth | Medium | Navigation / Reuse | Shell | `CommandPalette.tsx`, `GlobalSearch.tsx` | Users and teams get two search/action paradigms | Consolidate search and actions into one command surface |
| YAPPC-F025 | Generated cross-alignment documentation is materially unreliable relative to current code | High | Documentation / Trust | Docs, planning, onboarding | `docs-generated/02-inventory-analysis/05-cross-alignment-analysis.md` claims no onboarding and no mobile implementation despite current code | Product strategy and audit work can be misled by stale generated conclusions | Regenerate or retire misleading generated analyses and clearly timestamp route truth |
| YAPPC-F026 | Verification scripts still point at old `apps/web` structure | Medium | Tooling / Consistency | Routing validation | `frontend/scripts/validate-routes.ts` | Tooling reinforces the wrong code structure and slows safe change | Update scripts to target `frontend/web` or remove them |
| YAPPC-F027 | E2E navigation and accessibility suites target old `/app/...` routes and broken helper exports | High | Testing / Navigation | Browser coverage | `e2e/smoke.spec.ts`, `e2e/accessibility-navigation.spec.ts`, `e2e/helpers/test-isolation.ts` | Browser verification no longer protects the current app truth | Rewrite E2E suites to current routes and fix helper exports |
| YAPPC-F028 | Help and documentation handoff is generic and not task-contextual | Medium | Guidance | Shell | `_shell.tsx` help action opens `/docs` | Users need in-context guidance, not a broad docs escape hatch | Add contextual help, empty-state guidance, and task-specific troubleshooting links |

---

## 6. Comprehensive Simplification Plan

### Remove

- remove `canvas-workspace` as a separate top-level project route unless it becomes materially distinct
- remove or archive `src/router/routes.tsx` if it is no longer mounted
- remove or archive `src/routes/ide.tsx`
- remove inactive auth recovery routes from the active route tree until backend support exists
- remove stale route assumptions from smoke and accessibility suites
- remove mock global search from the product path until it is truthful

### Merge

- merge `Canvas` and `Workspace` into one project workspace route
- merge command palette and global search into one discovery surface
- merge blocker review into the root dashboard rather than forcing navigation to `/workspaces`
- merge duplicated route, spec, and validation truth around `src/routes.ts`

### Hide by default

- advanced metadata in settings should stay disclosure-based
- advanced operator controls in deploy should sit behind a “show details” layer
- lifecycle evidence detail should stay secondary to readiness and blockers
- canvas advanced tooling should open only in relevant task contexts

### Automate

- resume the most relevant project from the dashboard
- prefill workspace and project names from user context, docs, or imported repositories
- rank projects by urgency and blocked status
- suggest the correct project next action on the overview page
- recommend deploy strategy and approval mode from readiness and anomaly history
- detect and report route/docs/test drift in CI

### Prefill or contextualize

- workspace creation should present suggested values before commit
- project creation should inherit workspace context automatically
- preview should show provenance from the latest successful build/deploy source
- settings should expose only fields backed in the current deployment

### Move to advanced or admin mode

- dormant or experimental mobile/IDE/devsecops/workflows surfaces
- export/share actions that are not always relevant
- deep lifecycle evidence and anomaly internals
- advanced canvas rails and operators-only tools

### Standardize as reusable patterns

- unsupported capability banners
- save-sync badges
- unavailable-service fallbacks
- readiness status blocks
- advanced metadata disclosure blocks

---

## 7. Comprehensive AI/ML Embedding Plan

| Surface | Opportunity | User value | Automation vs assist | Confidence / governance model | Privacy / security notes | Priority |
|---|---|---|---|---|---|---|
| Root dashboard | Recommend one project and one next step | Faster resume, less hunting | Assist | Show top recommendation plus reason and confidence | Use existing project metadata only | Immediate |
| Onboarding | Infer starter workspace/project setup | Faster setup | Assist with explicit confirmation | User reviews before creation | Avoid hidden persistence of imported sensitive docs | Immediate |
| Workspaces | Detect workspace health and setup blockers | Better admin visibility | Assist | Summarize blockers; do not auto-fix destructive issues | Restrict by workspace membership | Short-term |
| Projects list | Rank by urgency, blocked status, review need | Lower cognitive load | Assist | Re-rank, never auto-open without user setting | Use project metadata, not secret content | Short-term |
| Project overview | Next-best-action orchestration | Faster decision making | Assist | One primary CTA; explain reason | Keep explanations concise | Immediate |
| Canvas | Smart node suggestions and auto-layout | Faster modeling | Assist by default | Auto-apply only reversible low-risk actions | Do not exfiltrate code/artifacts without policy guardrails | Medium-term |
| Canvas | Duplicate artifact / node detection | Reduce clutter | Assist | Suggest merge or archive, do not auto-delete | Show affected items clearly | Medium-term |
| Lifecycle | Readiness explanation and blocker summarization | Better promotion confidence | Assist | Human review required below threshold or when anomalies are critical | Keep audit trail of recommendations | Immediate |
| Lifecycle | Guided apply for high-confidence low-risk transitions | Reduce manual work | Automate only above explicit threshold | Keep 85%+ confidence and zero critical anomalies policy | Log operator, evidence, and override reason | Medium-term |
| Preview | UI diff and smoke summary | Faster validate loop | Assist | Show notable regressions and uncertainty | Strip sensitive test data from summaries | Medium-term |
| Deploy | Rollout and capacity recommendation | Safer releases | Assist | Human approval remains default for risky rollouts | Record rationale and decision history | Immediate |
| Deploy | Incident triage recommendation | Faster response | Assist | Never close incidents automatically without policy | Protect sensitive operational details by role | Medium-term |
| Settings | Unsafe configuration detection | Reduce misconfiguration | Assist | Warn, do not auto-change | Explain impact in plain language | Short-term |
| Shell insights | Notification ranking and digesting | Lower interruption load | Assist | Only show high-signal items by default | Respect workspace/project scope | Short-term |
| Docs / route governance | Drift detection across routes, specs, and tests | Lower maintenance chaos | Automate | Auto-open a corrective task when drift appears | No user data sensitivity issue | Immediate |

Recommended automation thresholds:

- fully automate only reversible, low-risk actions
- require explicit human review for destructive, security-sensitive, or externally visible actions
- keep rollout and promotion in assist mode unless policy, confidence, and anomaly thresholds are satisfied

---

## 8. Comprehensive Trust / Transparency / Privacy / Security UX Review

### Current gaps

- auth capability is not truthfully represented across login, register, and forgot-password artifacts
- session, onboarding, and token persistence are fragmented across multiple local/browser stores
- preview does not show enough provenance
- runtime/test/doc drift makes the product itself feel less trustworthy
- unsupported capabilities are sometimes hidden well and sometimes left as dormant route files or docs

### What good future-state behavior looks like

Operational visibility:
- overview shows current phase, blockers, readiness, and last update
- deploy shows promotion posture, operator notes, and gated recommendations
- preview shows source build and environment
- shell insights stay contextual, concise, and scoped

Auditability:
- lifecycle guided-apply and deploy actions record actor, reason, evidence, and outcome
- AI recommendations tied to deploy or lifecycle decisions keep lightweight explanation history

Permission clarity:
- workspace vs project vs account scope must be obvious on settings and management pages
- read-only/included projects should retain clear labels wherever navigation exposes them

Sensitive action handling:
- starter workspace creation should be reviewable
- deploy and phase advancement should remain explicit operator actions
- destructive settings actions should use clear irreversible language

Privacy and data boundaries:
- centralize token/session storage
- remove ad hoc `auth_token`, `api_key`, and cross-service localStorage fallback patterns where possible
- make any persisted local canvas/state cache discoverable and governable

Status and reasoning visibility:
- explain why a project is blocked
- explain why a route or capability is unavailable
- explain what AI did, only when the user needs the explanation

Safe defaults:
- start users on the smallest truthful route surface
- hide unsupported admin features
- keep risky automation in assist mode

---

## 9. Design System / Consistency / Reuse Review

### Major inconsistencies

- `src/routes.ts` is the active route truth, but route docs, E2E tests, and `src/router/routes.tsx` still describe a different product
- active mounted app is simplified, while specs still describe much larger `/app`, `/devsecops`, `/workflows`, and `/mobile` structures
- command palette is active, but global search remains mock-based and disconnected
- settings pages use a strong bounded pattern, while profile still presents disabled edit scaffolding
- some route tests correctly know register/forgot-password are excluded, while route/page inventory still treats them as real surfaces

### Missing shared abstractions

- one canonical product capability registry indicating active, dormant, experimental, archived
- one canonical browser API client
- one canonical session/token accessor
- one shared “unsupported capability” pattern across auth, profile, settings, preview, and shell

### Repeated or drifting patterns

- multiple route taxonomies
- multiple search/action metaphors
- multiple persistence strategies
- repeated raw `fetch` plus local parsing logic
- large clusters of historical pages and route specs that no longer reflect the mounted app

### Standardization actions

- standardize on `src/routes.ts`
- standardize on one API layer
- standardize one route-aware search/action surface
- standardize one save-state indicator pattern
- standardize one “API unavailable” pattern

---

## 10. Prioritized Remediation Roadmap

### Immediate

| Priority | Item | Effort | Impact | Owner | Dependencies | Rationale |
|---|---|---|---|---|---|---|
| P0 | Fix live runtime overlay `require is not defined` in mounted shell | M | Critical | Frontend / Platform | Design-system import fix | Product is not reliably reachable without this |
| P0 | Freeze route truth to `src/routes.ts` and publish current route matrix | S | Critical | Frontend / Product | None | Teams need one reliable navigation truth |
| P0 | Rewrite smoke and accessibility E2E tests to current route taxonomy | M | High | Frontend / QA | Route truth freeze | Current smoke tests target old routes and do not protect the mounted app |
| P0 | Fix broken shared testing utility in `platform/typescript/design-system/src/utils/testing.ts` | S | High | Platform / Frontend | None | Accessibility and route suites are blocked by infrastructure failure |
| P0 | Remove or quarantine dead route surfaces from active product docs | S | High | Product / Frontend | Route truth freeze | Trust and onboarding are degraded by stale specs |

### Short-term

| Priority | Item | Effort | Impact | Owner | Dependencies | Rationale |
|---|---|---|---|---|---|---|
| P1 | Merge `Canvas` and `Workspace` into one route or one mode system | M | High | Product / Frontend | Route truth freeze | Reduces cognitive load and duplicate maintenance |
| P1 | Simplify dashboard to include blockers inline and better next-action ranking | M | High | Product / Frontend / ML | None | Makes root screen a true cockpit |
| P1 | Centralize auth/session/token access and local persistence policy | M | High | Frontend / Platform / Security | Auth review | Improves trust, security, and failure handling |
| P1 | Remove or archive `src/router/routes.tsx`, `src/routes/ide.tsx`, and inactive auth route files | M | Medium | Frontend | Route truth freeze | Reduces product noise |
| P1 | Rework workspaces auto-create flow to explicit confirmable automation | S | Medium | Product / Frontend | None | Preserves speed while improving transparency |

### Medium-term

| Priority | Item | Effort | Impact | Owner | Dependencies | Rationale |
|---|---|---|---|---|---|---|
| P2 | Break canvas orchestration into smaller task-specific compositions | L | High | Frontend | Canvas information architecture plan | Improves maintainability and usability |
| P2 | Build one unified command/search surface backed by real route/action truth | M | Medium | Frontend / Product / ML | Route truth freeze | Improves discoverability and consistency |
| P2 | Upgrade preview into a true validation surface with provenance and issue handoff | M | Medium | Frontend / Backend / Product | Build metadata integration | Makes preview part of a real workflow |
| P2 | Reframe lifecycle page into summary-first information architecture | M | Medium | Product / Frontend | Overview/deploy coordination | Reduces cognitive load |
| P2 | Convert profile to truthful read-only or fully backed editable mode | S | Medium | Frontend / Backend | Profile API decision | Removes hybrid confusion |

### Long-term

| Priority | Item | Effort | Impact | Owner | Dependencies | Rationale |
|---|---|---|---|---|---|---|
| P3 | Decide product status of mobile, workflows, and DevSecOps surfaces and either mount or archive them | L | High | Product / Frontend / Platform | Strategy decision | Prevents perpetual parallel-product drift |
| P3 | Add AI-driven route/spec/test drift governance in CI | M | Medium | Platform / ML / Frontend | Route truth freeze | Keeps the product coherent over time |
| P3 | Mature lifecycle/deploy guided automation with explanation and audit trails | L | High | Product / Frontend / Backend / ML | Session and audit groundwork | Enables credible low-burden operation |

---

## 11. Final Ideal UX Vision

After remediation, YAPPC should feel like one product again.

The user signs in, lands on a truthful cockpit, sees one recommended next action, and can move from idea to implementation to validation to deployment without wondering which route structure, document, or hidden surface is the real one.

The system handles routine setup, ranking, summarization, and low-risk recommendations automatically. It only asks for user intervention when approval, ambiguity, security, or risk genuinely require it.

Visibility remains contextual:

- the dashboard explains what is blocked
- overview explains where the project stands
- lifecycle explains why promotion is or is not ready
- deploy explains what will happen next
- preview explains what users are looking at

AI remains embedded and mostly invisible:

- it pre-fills
- ranks
- summarizes
- recommends
- deduplicates
- explains when needed

It does not create noisy parallel surfaces or novelty UI.

Privacy, security, accessibility, and observability feel built in because the product is governed by one route truth, one session model, one API model, and one consistent system of progressive disclosure.

---

## 12. Executive Summary Lists

### Top 10 critical issues

1. Live browser smoke path currently hits a runtime overlay before workspaces render.
2. Runtime routes, tests, and docs disagree on the product’s core taxonomy.
3. A second parallel router definition still exists.
4. Shared test infrastructure blocks workspaces and accessibility suites.
5. E2E smoke and accessibility tests still target old `/app/...` route structures.
6. Disabled register and forgot-password logic still live as first-class route files.
7. Mobile surfaces exist in code but are not mounted.
8. `Canvas` and `Workspace` are duplicate top-level project tabs.
9. Canvas orchestration is too large for a critical core workflow.
10. Generated alignment docs are materially misleading relative to current code truth.

### Top 10 simplification opportunities

1. Collapse `Canvas` and `Workspace` into one route.
2. Use the dashboard as the real “resume work” cockpit.
3. Embed blockers on the dashboard instead of routing to `/workspaces`.
4. Remove unmounted route files from the active product path.
5. Replace global search plus command palette with one canonical discovery surface.
6. Turn profile into a read-only summary until editing exists.
7. Keep preview slim but richer in provenance rather than feature-sprawled.
8. Collapse lifecycle into summary, blockers, and evidence layers.
9. Confirm suggested starter workspace creation before commit.
10. Remove old route taxonomies from specs and tests.

### Top 10 AI/ML opportunities

1. Recommend one next action on the root dashboard.
2. Rank projects by urgency and blockage.
3. Prefill onboarding setup from user intent or imported context.
4. Explain lifecycle readiness with concise evidence summaries.
5. Recommend deploy strategy and approval path from readiness and anomaly data.
6. Suggest canvas cleanup, dedupe, and auto-layout.
7. Generate preview validation summaries tied to fixes.
8. Detect route/spec/test drift automatically in CI.
9. Summarize workspace issues and setup blockers.
10. Turn insight streams into high-signal contextual digests.

### Top 10 trust / visibility / privacy / security UX improvements

1. Fix runtime overlay failure on mounted routes.
2. Publish one route source of truth.
3. Centralize session/token storage policy.
4. Remove unsupported auth surfaces from the active product tree.
5. Show clear provenance on preview.
6. Keep audit trails for lifecycle and deploy actions.
7. Make automation thresholds explicit where risk is involved.
8. Keep unsupported admin features hidden and truthfully labeled.
9. Explain blockers and unavailable states in plain language.
10. Make local persistence and offline cache behavior discoverable and governable.

---

## Appendix: Verification Notes

Verification commands run:

1. Targeted Vitest pass/fail check from `products/yappc/frontend`:

```bash
pnpm exec vitest run \
  web/src/__tests__/routes.spec.ts \
  web/src/routes/__tests__/login-route.test.tsx \
  web/src/routes/__tests__/workspaces-route.test.tsx \
  web/src/routes/app/project/__tests__/index.test.tsx \
  web/src/routes/app/project/__tests__/shell.test.tsx \
  web/src/routes/app/project/__tests__/preview.test.tsx \
  web/src/routes/app/project/__tests__/deploy.test.tsx \
  web/src/routes/app/project/__tests__/settings.test.tsx \
  web/src/__tests__/accessibility/ui-components.accessibility.test.tsx
```

Observed result:
- `6` test files passed
- `3` failed
- project overview, shell, preview, deploy, settings, and login route tests passed
- `web/src/__tests__/routes.spec.ts` failed because it looked for `frontend/src/routes.ts` instead of `frontend/web/src/routes.ts`
- `web/src/routes/__tests__/workspaces-route.test.tsx` and `web/src/__tests__/accessibility/ui-components.accessibility.test.tsx` failed because `platform/typescript/design-system/src/utils/testing.ts` contains a syntax error (`l/**`)

2. Browser smoke verification:

```bash
pnpm --filter @yappc/web-app dev --host 127.0.0.1 --port 5173
pnpm exec playwright test e2e/smoke.spec.ts --config e2e/playwright.local.config.ts --project=chromium
```

Observed result:
- the dev server started after sandbox escalation
- Playwright smoke failed
- the page did not render workspaces; it showed a Vite error overlay with `require is not defined`
- screenshot evidence:
  - `products/yappc/frontend/e2e/test-results/e2e-local/smoke-basic-navigation-works-chromium/test-failed-1.png`
  - `products/yappc/frontend/e2e/test-results/e2e-local/smoke-basic-navigation-works-chromium/error-context.md`

3. Accessibility-navigation E2E attempt:

```bash
pnpm exec playwright test e2e/accessibility-navigation.spec.ts --config e2e/playwright.local.config.ts --project=chromium
```

Observed result:
- suite could not run because `e2e/helpers/test-isolation.ts` does not export `setupTest`, even though the spec imports it

Key evidence summary:
- mounted app route truth is simpler and better than older YAPPC docs suggest
- the dominant current risk is product truth fragmentation, not merely isolated UI polish issues
- the runtime and verification stack both need repair before broader UX claims can be trusted
