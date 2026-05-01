# Navigation Components

Navigation-oriented components for headers, quick actions, menus, and contextual app movement.

## Canonical Navigation Tree

YAPPC uses a **single, coherent navigation tree**:


/ (root — _root.tsx)
+-- /app  (shell — _shell.tsx)
|     Header: UnifiedContextHeader
|       +-- NavigationBreadcrumb  (Workspace > Project > section)
|
+-- /p/:projectId  (project shell — project/_shell.tsx)
        Phase tab bar: BASE_PROJECT_TABS  <- single source of truth
        Intent . Shape . Validate . Generate . Run . Observe . Learn . Evolve
        NavLink -> /p/:id/intent  ...  /p/:id/evolve


**BASE_PROJECT_TABS in routes/app/project/_shell.tsx is the only place
phase navigation is defined.** Do not add a second phase navigator elsewhere.

## Deprecated — do not use or import

| File | Reason |
|------|--------|
| EightPhaseNavigation.tsx | Navigates via ?phase= query params — mismatches the route tree; not rendered anywhere |
| UnifiedPhaseRail.tsx | Uses an outdated 7-phase model (IMPROVE instead of LEARN/EVOLVE); not rendered anywhere |

Both files are kept for history but removed from index.ts.

## Scope
- Shared navigation shells and context controls.
- Product-local components that coordinate route and workspace movement.
- Reusable affordances for app-level navigation behavior.

## Key Areas
- Headers, action bars, and navigation widgets.
- Context-aware navigation helpers.
- Shared quick-action and location components.
