# App-Creator – Architecture Consistency & Sprawl Review

> Scope: `products/yappc/app-creator` (apps + libs) with focus on the web app, DevSecOps surfaces, shared state, registries, and docs.

### Principles & Guardrails (from `.github/copilot-instructions.md`)

- **Reuse-first**: Prefer `core/*` and `libs/*` (for example `@yappc/ui/state`, `libs/types`, `libs/canvas`) before adding product-local implementations.
- **Layered & modular**: Keep clear separation between UI, application, domain, and infrastructure; group code by feature with small, focused modules.
- **Single sources of truth**: Centralize state, domain types, and registry definitions; avoid redefining types or atoms in product modules.
- **No duplication**: When you find overlap, extract or extend shared modules instead of copying.
- **Type-safe & lint-clean**: All implementation tasks in this plan must keep type checks and linters passing without warnings.

## 1. High-Level Assessment

- **Overall alignment:**
  - The web app largely follows the documented direction in:
    - `apps/web/docs/DESIGN_ARCHITECTURE.md`
    - `apps/web/docs/guidelines/CODING.md` / `TESTING.md`
    - `docs/CURRENT_IMPL/DEVSECOPS_UX_DESIGN_SYSTEM.md`
  - State is mostly centralized via `@yappc/ui/state` (StateManager) with clear migration layers.
  - DevSecOps has a strong, detailed plan; many “Phase 1–2” items appear implemented or partially implemented.
- **Main issues:**
  - Some **duplication / sprawl** of registry and state types.
  - A few **planned docs and integration steps** are still missing or only partially wired.
  - Historical compatibility modules make the architecture look more complex than it needs to be for new code.

## 2. Docs & Plan Consistency

### 2.1 What Exists

- **App-level docs**
  - `apps/web/docs/README.md` – positions this as the main frontend and explains local docs layout.
  - `apps/web/docs/DESIGN_ARCHITECTURE.md` – high-level design & architecture, aligned with YAPPC roadmap.
- **Architecture index**
  - `apps/web/docs/architecture/INDEX.md` – describes intended sub-docs:
    - `FRONTEND_ARCHITECTURE.md`
    - `CANVAS_AND_STATE_ARCHITECTURE.md`
    - `DESIGN_SYSTEM_INTEGRATION.md`
    - `INTEGRATIONS_AND_DEPENDENCIES.md`
- **Guidelines**
  - `apps/web/docs/guidelines/CODING.md` – TypeScript, React/Vite, StateManager, `@ghatana/ui` usage.
  - `apps/web/docs/guidelines/TESTING.md` – unit/integration/E2E expectations.
- **DevSecOps master plan**
  - `docs/CURRENT_IMPL/DEVSECOPS_UX_DESIGN_SYSTEM.md` – very detailed plan + current status, including:
    - Canonical domain types in `libs/types/src/devsecops`.
    - Shared UI (`libs/ui/src/components/DevSecOps`).
    - Shared state & mock data.
    - API client + MSW-based mocking.
    - Routes, canvas, reports, settings, templates, desktop integration.

### 2.2 Gaps / Drift

- **Missing architecture sub-docs** (only INDEX exists):
  - `FRONTEND_ARCHITECTURE.md`
  - `CANVAS_AND_STATE_ARCHITECTURE.md`
  - `DESIGN_SYSTEM_INTEGRATION.md`
  - `INTEGRATIONS_AND_DEPENDENCIES.md`
- **DevSecOps doc vs. code**
  - The doc marks several Phase 1–2 tasks as “Completed” (fixtures, DevSecOpsClient refactor, MSW handlers, Jotai consolidation, route wiring). These look mostly consistent, but there is still:
    - Historical mention of “three DevSecOps stacks” (MSW+app state, libs/ui devsecops state, libs/api with inline mocks) – code should be periodically re-checked to ensure no old mocks remain.
- **Planning docs**
  - `docs/planning/epics.json` and `tasks.json` exist, but there is no human-readable summary tying them back to the architecture docs (this file can play that role at a high level).

## 3. State Management & Mocking

### 3.1 Strengths

- **Central StateManager**
  - `libs/ui/src/state/StateManager.ts` implements a robust, type-safe Jotai-based state manager (atoms, persistent atoms, derived atoms, async atoms, Yjs atoms, statistics).
  - `libs/ui/src/state/atoms.ts` defines a comprehensive set of global atoms (theme, user, UI, canvas, forms, navigation, search, performance, feature flags, legacy store compatibility, etc.).
- **Centralized imports for apps**
  - `apps/web/src/state/globalState.ts` and `apps/mobile-cap/src/state/globalState.ts` are thin re-export layers over `@yappc/ui/state` with clear **migration warnings**.
  - New code is encouraged to import directly from `@yappc/ui/state` rather than app-local state modules.
- **Legacy store bridge**
  - `libs/store/src/atoms.ts` is a compatibility layer that proxies old `@yappc/store` atoms onto StateManager-managed atoms.
- **Canvas state**
  - `libs/canvas/src/state/atoms.ts` uses StateManager for persistent & shared canvas state (`canvas:document`, `canvas:selection`, `canvas:viewport`, history, UI state, perf metrics) plus derived atoms and action atoms.

### 3.2 Sprawl / Duplication

- **Multiple layers for the same conceptual state**
  - `libs/ui/src/state/atoms.ts` defines canonical atoms for many concerns (theme, auth, navigation, etc.).
  - `libs/store/src/atoms.ts` re-exports many of these as legacy atoms (e.g., `authStateAtom`, `themeAtom`, `workspacesAtom`, `projectsAtom`, `tasksAtom`, etc.) with deprecation notices.
  - For newcomers, it’s easy to import from `@yappc/store` instead of `@yappc/ui/state`, reintroducing sprawl.
- **DevSecOps-specific state**
  - The DevSecOps doc explicitly notes historic fragmentation between:
    - `apps/web/src/state/devsecops.ts`
    - `libs/ui/src/state/devsecops/*`
    - `libs/api/src/devsecops/*` (client, integrations, websocket)
  - The plan is to consolidate onto `libs/ui` + `DevSecOpsClient` + MSW.
  - Code should be periodically audited to ensure `apps/web/src/state/devsecops.ts` has been fully reduced to a wrapper/composition layer and no route/canvas still uses ad-hoc local devsecops atoms.

### 3.3 Gaps / Enhancements

- **Enforce a “single import path” for new state**
  - New product code should import state exclusively from `@yappc/ui/state` (or feature-specific shared state modules) and avoid `libs/store/src/atoms.ts` except where strictly needed for backwards compatibility.
- **Automated guardrails**
  - Add ESLint rules or codemods to flag direct imports from deprecated modules (`apps/*/state/globalState`, `@yappc/store`) and suggest the modern alternative.
- **DevSecOps state audit**
  - Run a targeted pass across `apps/web/src/devsecops` and `libs/ui/src/state/devsecops` to:
    - Confirm they both use the same canonical types from `libs/types/src/devsecops`.
    - Ensure there is **exactly one** source of truth for filters, KPIs, phases, persona configs.

## 4. Registry & Type Sprawl

### 4.1 Current Setup

- `apps/web/src/services/registry/*` contains:
  - `SchemaRegistry.ts` – a registry for Zod schemas with migration hooks.
  - `UnifiedRegistry.ts` – a generalized, namespace-aware registry for components (with its own `RegistryEntry` and `RegistryFilter`/`RegistryComparator` types).
  - `types.ts` – also defines `ComponentDefinition`, `SchemaDefinition`, `RegistryEntry<T>`, `ValidationResult`, `MigrationHook`, etc.
  - `RegistryMigration.ts` – migration and validation utilities (including duplicate ID detection across namespaces).

### 4.2 Sprawl / Inconsistencies

- **Duplicate type names**
  - `RegistryEntry` is defined in both `apps/web/src/services/registry/types.ts` and `UnifiedRegistry.ts`.
  - Additional `RegistryFilter` and `RegistryComparator` types are defined in multiple places.
- **Documentation vs. modularization**
  - The registry system is powerful but not yet clearly surfaced in the architecture docs; it’s easy for feature teams to bypass it and roll their own registries or ad-hoc maps.

### 4.3 Cleanup / Enhancements

- **Single registry types module**
  - Extract a single `RegistryTypes` module (likely under `libs/canvas` or a new `libs/registry`) that defines:
    - `RegistryEntry<T>`
    - `RegistryFilter<T>`
    - `RegistryComparator<T>`
    - Shared `MigrationHook` and `ValidationResult` types
  - Make `SchemaRegistry`, `UnifiedRegistry`, `ComponentRegistry` all import from this shared module.
- **Document registry usage**
  - Add a short `REGISTRY_ARCHITECTURE.md` under `apps/web/docs/architecture/` describing:
    - When to use `SchemaRegistry` vs. `UnifiedRegistry`.
    - How to register new components/schemas in a way that avoids ID duplication.

## 5. DevSecOps Integration & Mocking

### 5.1 Alignment with Plan

From `docs/CURRENT_IMPL/DEVSECOPS_UX_DESIGN_SYSTEM.md`:

- **Canonical domain & API shapes** are defined under `libs/types/src/devsecops` and `libs/api/src/devsecops/*`.
- **Single mocking path** target is clearly articulated:
  - `DevSecOpsClient` → `/api/devsecops/...` → MSW handlers → shared fixtures.
- **Jotai store consolidation** under `libs/ui/src/state/devsecops` is planned (and marked largely “Completed”).

### 5.2 Remaining Gaps (Conceptual)

- **Triple-stack legacy remnants**
  - Code should be inspected (outside this summary) to ensure:
    - `DevSecOpsClient` has no inline mocks left.
    - MSW handlers use shared fixtures and canonical types exclusively.
    - App-local devsecops state is just a wrapper around shared hooks from `libs/ui`.
- **Route ↔ Canvas ↔ APIs integration**
  - The doc calls out dual surfaces (routes vs. canvas) and aims for shared state across both.
  - Any route or canvas code that still uses local arrays/objects instead of shared atoms/clients is technical debt.

### 5.3 Suggested Next Steps

1. **DevSecOps state/code audit** (single, focused sweep):
   - Confirm that all DevSecOps views (dashboard, phase views, persona dashboards, reports, settings, templates, canvas) use:
     - Canonical domain types from `libs/types/src/devsecops`.
     - Shared state hooks from `libs/ui/src/state/devsecops`.
     - `DevSecOpsClient` for data access.
2. **DevSecOps documentation alignment**:
   - When changes are made (e.g., new routes wired, new personas, new reports), update Section 2–6 in `DEVSECOPS_UX_DESIGN_SYSTEM.md` in the same PR.

## 6. Documentation & Testing Gaps

### 6.1 Architecture Docs

- **Missing concrete architecture docs** for the web app:
  - `FRONTEND_ARCHITECTURE.md` – should describe routing, layouts, shell components, major feature modules.
  - `CANVAS_AND_STATE_ARCHITECTURE.md` – should explain the relationship between `libs/canvas`, StateManager, and app-level usage.
  - `DESIGN_SYSTEM_INTEGRATION.md` – should document how `@ghatana/ui` and tokens are consumed in app-creator.
  - `INTEGRATIONS_AND_DEPENDENCIES.md` – should show how the web app talks to `libs/api`, MSW, and backend services.

### 6.2 Testing

- Guidelines exist, and there are `jest.canvas.config` and `playwright` configs, but coverage vs. guidelines is not summarized.
- **Potential improvements:**
  - Add a short `TEST_COVERAGE_OVERVIEW.md` (or expand `apps/web/docs/guidelines/TESTING.md`) summarizing:
    - Critical flows with E2E coverage.
    - Areas intentionally mocked vs. hitting real APIs.
    - Known testing gaps (e.g., DevSecOps WebSocket behavior, canvas undo/redo stress tests).

## 7. Concrete Cleanup & Integration Plan

### 7.1 Short-Term (1–2 Iterations)

1. **Document the current web architecture**
   - Create `apps/web/docs/architecture/FRONTEND_ARCHITECTURE.md`:
     - Routing structure, layout shell, feature modules.
     - How StateManager and `@yappc/ui/state` are wired into the app.
2. **Registry type consolidation**
   - Introduce a shared `RegistryTypes` module and update `SchemaRegistry`, `UnifiedRegistry`, and related files to consume it.
3. **State import guardrails**
   - Add ESLint rule(s) or lint patterns:
     - Disallow new imports from `@yappc/store` in `apps/*` (only allowed in legacy code).
     - Prefer `@yappc/ui/state` and `libs/ui/src/state/*` for all new state.

### 7.2 Medium-Term (3–5 Iterations)

4. **DevSecOps state & mock consolidation verification**
   - Run a targeted audit to confirm triple-stack fragmentation has been fully retired, updating `DEVSECOPS_UX_DESIGN_SYSTEM.md` statuses.
5. **Complete DevSecOps routing integration**
   - Ensure `/devsecops/reports`, `/devsecops/settings`, `/devsecops/templates`, `/devsecops/canvas` are fully wired, with shared state/hooks.
6. **Add architecture docs for canvas & design system**
   - `CANVAS_AND_STATE_ARCHITECTURE.md`
   - `DESIGN_SYSTEM_INTEGRATION.md`

### 7.3 Long-Term

7. **Retire legacy store compatibility**
   - Gradually remove `libs/store/src/atoms.ts` once all consumers are migrated to `@yappc/ui/state` directly.
8. **Desktop shell alignment**
   - Ensure `apps/desktop` uses exactly the same DevSecOps flows, state, and components as `apps/web` (per the DevSecOps doc Phase 5).
9. **Periodic drift & sprawl checks**
   - Use `dependency-cruiser` + `jscpd` reports already present in the repo to:
     - Flag new duplicate state/registry/DevSecOps implementations.
     - Keep `docs/CURRENT_IMPL/DEVSECOPS_UX_DESIGN_SYSTEM.md` as the single source of truth.

## 8. Day-by-Day Implementation Plan

"Day" below means a focused working day for this initiative; adjust sequencing or scope as needed. Use the checkboxes to track progress.

### Day 1 – Kickoff & State/Docs Inventory

- [ ] **Confirm scope & owners** for this initiative (frontend, DevSecOps, shared libs/state, registry, docs).
- [ ] **Inventory state imports**:
  - [ ] Run a search for imports from `@yappc/store` and `apps/*/state/globalState`.
  - [ ] Capture a short list of top consumers to prioritize for migration.
- [ ] **Docs baseline**:
  - [ ] Open `apps/web/docs/architecture/INDEX.md`, `DESIGN_ARCHITECTURE.md`, and this file side-by-side.
  - [ ] Create an issue/epic linking this plan to `docs/planning/epics.json` / `tasks.json`.

### Day 2 – FRONTEND_ARCHITECTURE.md (Section 7.1.1)

- [ ] **Create `FRONTEND_ARCHITECTURE.md` skeleton** under `apps/web/docs/architecture/`:
  - [ ] Routing overview (top-level routes, devsecops routes, canvas routes, settings, templates, etc.).
  - [ ] Layout/shell components and how they compose.
  - [ ] High-level feature/module boundaries.
- [ ] **Document StateManager wiring**:
  - [ ] How `@yappc/ui/state` is wired into `apps/web`.
  - [ ] Where providers are created and how feature modules access atoms/hooks.
- [ ] **Open PR #1**: FRONTEND_ARCHITECTURE.md (even if partial) for early review.

### Day 3 – RegistryTypes Module (Section 7.1.2)

- [ ] **Design shared registry types location** (e.g., new `libs/registry/src/types.ts` or similar).
- [ ] **Introduce `RegistryTypes` module** with:
  - [ ] `RegistryEntry<T>`
  - [ ] `RegistryFilter<T>`
  - [ ] `RegistryComparator<T>`
  - [ ] Shared `MigrationHook` and `ValidationResult` types, if appropriate.
- [ ] **Wire Schema/Unified registries to shared types** in a feature branch (no deletions yet).
- [ ] **Add minimal unit tests** (or extend existing tests) to validate type usage and basic registry behavior.

### Day 4 – Registry Cleanup & Documentation (Sections 4 & 7.1.2)

- [ ] **Remove duplicate type definitions** from `SchemaRegistry.ts`, `UnifiedRegistry.ts`, and `types.ts`, now that `RegistryTypes` exists.
- [ ] **Refactor imports** across `apps/web/src/services/registry/*` to use the shared types.
- [ ] **Add `REGISTRY_ARCHITECTURE.md`** under `apps/web/docs/architecture/`:
  - [ ] When to use `SchemaRegistry` vs. `UnifiedRegistry`.
  - [ ] How to avoid ID duplication and validate registry integrity.
- [ ] **Open PR #2**: RegistryTypes + REGISTRY_ARCHITECTURE.md.

### Day 5 – State Import Guardrails (Section 7.1.3)

- [ ] **Define linting strategy**:
  - [ ] Decide between custom ESLint rule(s), path-restriction config, or simple forbidden-import pattern.
- [ ] **Implement rules** to:
  - [ ] Disallow new imports from `@yappc/store` in `apps/*` (except whitelisted legacy files).
  - [ ] Flag imports from `apps/*/state/globalState` and suggest `@yappc/ui/state` instead.
- [ ] **Update docs**:
  - [ ] Add a short section in `apps/web/docs/guidelines/CODING.md` explaining the expected import paths for state.
- [ ] **Open PR #3**: ESLint rules + docs.

### Day 6 – DevSecOps State & Mock Audit (Sections 3.3 & 5.3.1)

- [ ] **Audit DevSecOps code**:
  - [ ] Review `libs/api/src/devsecops/*` to ensure `DevSecOpsClient` has no inline mocks.
  - [ ] Review `libs/ui/src/state/devsecops/*` and `apps/web/src/state/devsecops.ts` for duplication or divergence.
  - [ ] Ensure all views (dashboard, phase views, persona dashboards, reports, settings, templates, canvas) use canonical domain types from `libs/types/src/devsecops`.
- [ ] **Audit MSW handlers & fixtures** for DevSecOps:
  - [ ] Confirm MSW handlers use shared fixtures and canonical types.
- [ ] **Create/Update tracking issues** for any remaining triple-stack remnants.
- [ ] **Open PR #4**: Focused DevSecOps cleanup (if small), or at least a draft with the first batch of fixes.

### Day 7 – DevSecOps Routing & Integration (Section 7.2.5)

- [ ] **Verify all DevSecOps routes are wired**:
  - [ ] `/devsecops/reports`
  - [ ] `/devsecops/settings`
  - [ ] `/devsecops/templates`
  - [ ] `/devsecops/canvas`
- [ ] **Ensure shared state/hooks usage** across these routes.
- [ ] **Add or update tests**:
  - [ ] Integration/E2E tests for at least one end-to-end DevSecOps flow (e.g., dashboard → details → report).
- [ ] **Update `DEVSECOPS_UX_DESIGN_SYSTEM.md`** statuses for any completed items.
- [ ] **Open PR #5**: Routing and integration fixes + doc updates.

### Day 8 – Canvas & State Architecture Docs (Sections 3 & 6.1)

- [ ] **Create `CANVAS_AND_STATE_ARCHITECTURE.md`** under `apps/web/docs/architecture/`:
  - [ ] How `libs/canvas` atoms integrate with `StateManager`.
  - [ ] How canvas state flows into UI components and DevSecOps experiences.
  - [ ] Any Yjs/real-time collaboration considerations, if applicable.
- [ ] **Cross-link** this doc from:
  - [ ] `FRONTEND_ARCHITECTURE.md`
  - [ ] `DEVSECOPS_UX_DESIGN_SYSTEM.md` (if canvas is used there).
- [ ] **Open PR #6**: Canvas & state architecture docs.

### Day 9 – Design System Integration & Testing Overview (Sections 6.1 & 6.2)

- [ ] **Create `DESIGN_SYSTEM_INTEGRATION.md`** under `apps/web/docs/architecture/`:
  - [ ] How `@ghatana/ui` components and tokens are used in app-creator.
  - [ ] Guidelines for extending or composing new UI components.
- [ ] **Testing overview**:
  - [ ] Either add `TEST_COVERAGE_OVERVIEW.md` or extend `apps/web/docs/guidelines/TESTING.md` to summarize:
    - [ ] Critical flows with E2E coverage.
    - [ ] Areas intentionally mocked vs. hitting real APIs.
    - [ ] Known gaps (e.g., DevSecOps WebSocket behavior, canvas undo/redo stress tests).
- [ ] **Open PR #7**: Design system integration + testing overview.

### Day 10 – Long-Term Migration & Tooling (Sections 7.3 & 9)

- [ ] **Plan retirement of legacy store compatibility**:
  - [ ] From the Day 1 inventory, group `@yappc/store` consumers by feature.
  - [ ] Create a migration checklist or RFC describing how each group will move to `@yappc/ui/state`.
- [ ] **Desktop shell alignment**:
  - [ ] Identify where `apps/desktop` diverges from `apps/web` for DevSecOps flows.
  - [ ] File follow-up tasks/epics to reuse the same components/state where feasible.
- [ ] **Periodic drift & sprawl checks**:
  - [ ] Ensure `dependency-cruiser` and `jscpd` are part of CI or at least a regular pre-release check.
  - [ ] Document a lightweight process (e.g., monthly review) to scan for new duplicate state/registry/DevSecOps implementations.
- [ ] **Open PR #8 (or RFC)**: Long-term migration & tooling plan, referencing this section.

### PR Checklist for This Plan

Copy-paste this checklist into PR descriptions that implement items from this document:

- [ ] **Scope linked**: PR description references the relevant day(s)/item(s) from sections 7 and 8.
- [ ] **Reuse-first**: Checked `core/*` and `libs/*` (e.g. `@yappc/ui/state`, `libs/types`, `libs/canvas`, shared DevSecOps modules) before adding new product-local code.
- [ ] **Single source of truth**: No new domain types, atoms, or registry types duplicate existing shared definitions; where overlap was found, code was consolidated into shared modules.
- [ ] **Layered & modular**: Changes respect UI → application → domain → infrastructure boundaries and keep modules small, feature-focused, and colocated.
- [ ] **State & mocking alignment**: Any state or API changes reuse `@yappc/ui/state` atoms/hooks, canonical clients (e.g. DevSecOpsClient), and MSW fixtures instead of introducing ad-hoc stores or inline mocks.
- [ ] **No duplication introduced**: `dependency-cruiser`/`jscpd` (or equivalent checks) show no new duplicate state/registry/DevSecOps implementations.
- [ ] **Type-safe & lint-clean**: TypeScript/Java compilation and all linters pass with zero new warnings; no `any`/type-escape hatches added without justification.
- [ ] **Tests updated**: Relevant unit/integration/E2E tests were added or updated (including DevSecOps and canvas flows where applicable).
- [ ] **Docs updated**: Any affected architecture docs (e.g. `FRONTEND_ARCHITECTURE.md`, `CANVAS_AND_STATE_ARCHITECTURE.md`, `DESIGN_SYSTEM_INTEGRATION.md`, `DEVSECOPS_UX_DESIGN_SYSTEM.md`, or this review file) have been updated in the same PR.
