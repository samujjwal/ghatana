# Shared Modules Audit Report

## Executive Summary

Shared module health is mixed. The platform-level libraries are directionally organized, but the monorepo currently carries several contract-breaking issues in public shared surfaces, most notably:

- broken or stale package references in `@audio-video/client`, `@dcmaar/plugin-abstractions`, and the YAPPC shared UI/messaging stack
- duplicated ownership of the same shared responsibility across `@ghatana/utils` vs `@ghatana/platform-utils`, `@yappc/chat` vs `@yappc/messaging`, and `@yappc/crdt` vs `@yappc/collab`
- checked-in compiled artifacts and `package-lock.json` files inside a pnpm workspace, which obscures the source of truth and raises drift risk
- shallow or missing tests around high-reuse contracts and adapters

Overall assessment: the shared module layer is usable, but not yet stable enough to treat as a trusted canonical foundation across products without targeted consolidation and packaging cleanup.

## Scope Reviewed

Reviewed shared TypeScript workspace packages and shared product libraries under:

- `platform/typescript/*`
- `platform/typescript/foundation/*`
- `products/audio-video/libs/*`
- `products/dcmaar/libs/typescript/*`
- `products/dcmaar/libs/typescript/agent-core/*`
- `products/dcmaar/libs/guardian-dashboard-core`
- `products/tutorputor/contracts`
- `products/tutorputor/libs/*`
- `products/flashit/libs/ts/shared`
- `products/yappc/frontend/libs/*`
- `products/yappc/frontend/compat/*`
- `products/yappc/frontend/packages/*`

Audit method:

- manifest review for every shared package
- export surface review of entrypoints and representative public modules
- consumer scan across `products/` and `platform/`
- duplication and consolidation scan
- repository hygiene scan for generated artifacts and lockfiles

Not performed:

- exhaustive runtime verification of every package
- full monorepo test execution
- non-TypeScript shared modules outside the paths above

## Shared Module Inventory

### Platform

| Package | Path | Primary role |
| --- | --- | --- |
| `@ghatana/accessibility-audit` | `platform/typescript/accessibility-audit` | Accessibility audit UI/helpers |
| `@ghatana/api` | `platform/typescript/api` | Shared API client layer |
| `@ghatana/canvas` | `platform/typescript/canvas` | Canvas primitives |
| `@ghatana/charts` | `platform/typescript/charts` | Shared charting wrappers |
| `@ghatana/design-system` | `platform/typescript/design-system` | Canonical platform UI kit |
| `@ghatana/platform-utils` | `platform/typescript/foundation/platform-utils` | Canonical utility package |
| `@ghatana/i18n` | `platform/typescript/i18n` | Shared i18n helpers |
| `@ghatana/platform-shell` | `platform/typescript/platform-shell` | Shell integration helpers |
| `@ghatana/realtime` | `platform/typescript/realtime` | Realtime primitives |
| `@ghatana/sso-client` | `platform/typescript/sso-client` | SSO client utilities |
| `@ghatana/theme` | `platform/typescript/theme` | Theme system |
| `@ghatana/tokens` | `platform/typescript/tokens` | Design tokens |
| `@ghatana/ui-integration` | `platform/typescript/ui-integration` | UI integration helpers |
| `@ghatana/utils` | `platform/typescript/utils` | Utility facade over `@ghatana/platform-utils` |

### Audio-Video

| Package | Path | Primary role |
| --- | --- | --- |
| `@audio-video/client` | `products/audio-video/libs/audio-video-client` | Shared service client |
| `@audio-video/types` | `products/audio-video/libs/audio-video-types` | Shared contracts and DTOs |
| `@audio-video/ui` | `products/audio-video/libs/audio-video-ui` | Shared UI/hooks |

### DCMAAR

| Package | Path | Primary role |
| --- | --- | --- |
| `@dcmaar/dashboard-core` | `products/dcmaar/libs/guardian-dashboard-core` | Shared dashboard authorization/feature core |
| `@dcmaar/bridge-protocol` | `products/dcmaar/libs/typescript/agent-core/bridge-protocol` | Agent bridge contracts |
| `@dcmaar/agent-types` | `products/dcmaar/libs/typescript/agent-core/types` | Agent core types |
| `@dcmaar/agent-ui` | `products/dcmaar/libs/typescript/agent-core/ui` | Agent UI package |
| `@dcmaar/browser-extension-core` | `products/dcmaar/libs/typescript/browser-extension-core` | Browser extension runtime core |
| `@dcmaar/browser-extension-ui` | `products/dcmaar/libs/typescript/browser-extension-ui` | Browser extension UI |
| `@dcmaar/config-presets` | `products/dcmaar/libs/typescript/config-presets` | Shared config presets |
| `@dcmaar/connectors` | `products/dcmaar/libs/typescript/connectors` | Connector SDK |
| `@dcmaar/ui` | `products/dcmaar/libs/typescript/dcmaar-ui` | DCMAAR UI facade |
| `@dcmaar/plugin-abstractions` | `products/dcmaar/libs/typescript/plugin-abstractions` | Plugin interfaces and core managers |
| `@dcmaar/plugin-extension` | `products/dcmaar/libs/typescript/plugin-extension` | Plugin extension helpers |
| `@dcmaar/types` | `products/dcmaar/libs/typescript/types` | Shared product types |

### TutorPutor

| Package | Path | Primary role |
| --- | --- | --- |
| `@tutorputor/contracts` | `products/tutorputor/contracts` | Shared contracts |
| `@tutorputor/ai` | `products/tutorputor/libs/tutorputor-ai` | AI helpers |
| `@tutorputor/core` | `products/tutorputor/libs/tutorputor-core` | Core domain/platform library |
| `@tutorputor/simulation` | `products/tutorputor/libs/tutorputor-simulation` | Simulation runtime/UI |
| `@tutorputor/ui` | `products/tutorputor/libs/tutorputor-ui` | Shared UI helpers |

### Flashit

| Package | Path | Primary role |
| --- | --- | --- |
| `@flashit/shared` | `products/flashit/libs/ts/shared` | Shared client types, hooks, validation, API client |

### YAPPC

| Package | Path | Primary role |
| --- | --- | --- |
| `@yappc/api` | `products/yappc/frontend/libs/api` | Shared API layer |
| `@yappc/auth` | `products/yappc/frontend/libs/auth` | Shared auth helpers |
| `@yappc/chat` | `products/yappc/frontend/libs/chat` | Deprecated chat shim |
| `@yappc/code-editor` | `products/yappc/frontend/libs/code-editor` | Shared editor package |
| `@yappc/collab` | `products/yappc/frontend/libs/collab` | Canonical collaboration package |
| `@yappc/config` | `products/yappc/frontend/libs/config` | Shared config helpers |
| `@yappc/ide` | `products/yappc/frontend/libs/ide` | IDE helpers |
| `@yappc/shortcuts` | `products/yappc/frontend/libs/shortcuts` | Keyboard shortcut UI/hooks |
| `@yappc/testing` | `products/yappc/frontend/libs/testing` | Shared testing helpers |
| `@yappc/ai` | `products/yappc/frontend/libs/yappc-ai` | AI plus duplicate messaging surfaces |
| `@yappc/core` | `products/yappc/frontend/libs/yappc-core` | Core shared domain/frontend types |
| `@yappc/state` | `products/yappc/frontend/libs/yappc-state` | Shared state package |
| `@yappc/ui` | `products/yappc/frontend/libs/yappc-ui` | Shared UI package |
| `@yappc/base-ui` | `products/yappc/frontend/compat/base-ui` | Legacy compatibility package |
| `@yappc/config-hooks` | `products/yappc/frontend/compat/config-hooks` | Legacy compatibility package |
| `@yappc/crdt` | `products/yappc/frontend/compat/crdt` | Deprecated CRDT compatibility package |
| `@yappc/development-ui` | `products/yappc/frontend/compat/development-ui` | Legacy compatibility package |
| `@yappc/initialization-ui` | `products/yappc/frontend/compat/initialization-ui` | Legacy compatibility package |
| `@yappc/messaging` | `products/yappc/frontend/compat/messaging` | Messaging compatibility package |
| `@yappc/navigation-ui` | `products/yappc/frontend/compat/navigation-ui` | Legacy compatibility package |
| `@yappc/notifications` | `products/yappc/frontend/compat/notifications` | Deprecated notifications compatibility package |
| `@yappc/realtime` | `products/yappc/frontend/compat/realtime` | Realtime compatibility package |
| `@yappc/theme` | `products/yappc/frontend/compat/theme` | Theme compatibility package |
| `@yappc/types` | `products/yappc/frontend/compat/types` | Shared compatibility type package |
| `@yappc/utils` | `products/yappc/frontend/compat/utils` | Utility compatibility package |
| `@yappc/eslint-config-custom` | `products/yappc/frontend/packages/eslint-config-custom` | Shared lint config |
| `@yappc/tsconfig` | `products/yappc/frontend/packages/tsconfig` | Shared tsconfig package |
| `@yappc/vite-plugin-live-edit` | `products/yappc/frontend/packages/vite-plugin-live-edit` | Shared Vite plugin |

## Dependency and Consumer Overview

High-use shared packages:

- `@ghatana/design-system`: consumed by Audio-Video desktop, Data Cloud UI, TutorPutor web/admin, Software Org web, and DCMAAR apps
- `@ghatana/theme`: consumed by platform packages plus Data Cloud, TutorPutor, and YAPPC UI/AI
- `@ghatana/utils` and `@ghatana/platform-utils`: both consumed, often simultaneously via aliases
- `@tutorputor/contracts` and `@tutorputor/core`: central backend and app contracts for TutorPutor apps/services/tools
- `@dcmaar/connectors`, `@dcmaar/browser-extension-core`, `@dcmaar/plugin-abstractions`: shared throughout DCMAAR extension/runtime code

Low- or no-code-consumer shared packages:

- several YAPPC compat packages are referenced mainly by lint rules, docs, or stale path aliases rather than active runtime imports
- `@dcmaar/ui` is used only by `device-health`; `parent-dashboard` already imports `@ghatana/design-system` directly
- `@ghatana/utils` is a facade package whose consumer base overlaps heavily with `@ghatana/platform-utils`

Dependency direction issues:

- YAPPC UI imports `@yappc/ui/theme` from within `@yappc/ui` source even though that subpath is not exported
- deprecated YAPPC shims re-export from `@yappc/ai/messaging/*`, so messaging ownership is split between compat and AI packages
- platform utilities expose two package names for one utility implementation, which forces apps to maintain duplicate alias maps

## Findings

### SM-001

- Severity: `critical`
- File path: `products/audio-video/libs/audio-video-client/src/index.ts:8`
- Module or package name: `@audio-video/client`
- Problem to resolve: the public client imports its shared contracts from `@ghatana/audio-video-product-types`, but the actual workspace package is `@audio-video/types`.
- Why it matters: this breaks the shared client’s build and guarantees consumer confusion because the published dependency graph says one thing and the source says another.
- Evidence: `products/audio-video/libs/audio-video-client/src/index.ts:26` imports `@ghatana/audio-video-product-types`; `products/audio-video/libs/audio-video-client/package.json` depends on `@audio-video/types`.
- Consumer impact: every Audio-Video app consuming `@audio-video/client`, especially `products/audio-video/apps/desktop`.
- Duplication type if applicable: `none`
- Consolidation recommendation if applicable: align the client to a single canonical contract package, `@audio-video/types`.
- Target location for consolidated code if applicable: keep types in `products/audio-video/libs/audio-video-types`.
- Migration notes: update the import, rebuild the package, then grep for `@ghatana/audio-video-product-types` across the repo and remove any remaining stale references.
- Exact fix recommendation: replace the import in `src/index.ts`, add an export smoke test that instantiates the client against `@audio-video/types`, and fail CI on unresolved workspace package names.
- Test gaps: no package-local tests currently verify the client’s public surface or packaging.
- Documentation gaps: package README or module docs should state that `@audio-video/types` is the sole contract package.

### SM-002

- Severity: `high`
- File path: `products/dcmaar/libs/typescript/plugin-abstractions/src/index.ts:6`
- Module or package name: `@dcmaar/plugin-abstractions`
- Problem to resolve: the package re-exports types from `@ghatana/dcmaar-types`, but the actual workspace package is `@dcmaar/types`.
- Why it matters: this is a broken public export at the shared package boundary and creates an avoidable mismatch between source, docs, and manifest dependencies.
- Evidence: `products/dcmaar/libs/typescript/plugin-abstractions/src/index.ts:7` exports from `@ghatana/dcmaar-types`; `products/dcmaar/libs/typescript/plugin-abstractions/package.json` depends on `@dcmaar/types`; `products/dcmaar/libs/typescript/types/package.json` defines `@dcmaar/types`.
- Consumer impact: all plugin-oriented DCMAAR consumers, including `browser-extension-core`, `device-health`, and `host`.
- Duplication type if applicable: `ownership`
- Consolidation recommendation if applicable: standardize all plugin and type imports on `@dcmaar/types`.
- Target location for consolidated code if applicable: `products/dcmaar/libs/typescript/types`
- Migration notes: update imports, add a package export test for `@dcmaar/plugin-abstractions`, and grep for `@ghatana/dcmaar-types` to prevent future drift.
- Exact fix recommendation: change the export source to `@dcmaar/types`, add lint rules or a dependency-policy check for deprecated package names.
- Test gaps: no package-level export smoke test currently catches this.
- Documentation gaps: docs and comments still mention the old package name.

### SM-003

- Severity: `critical`
- File path: `products/yappc/frontend/libs/yappc-ui/package.json:8`
- Module or package name: `@yappc/ui`
- Problem to resolve: the package source imports and re-exports `@yappc/ui/theme`, but `package.json` does not export a `./theme` subpath.
- Why it matters: this is a public API break and a circular packaging smell. Consumers and even the package’s own source depend on an unexported subpath.
- Evidence: `products/yappc/frontend/libs/yappc-ui/package.json:8-33` exports only `.`, `./components`, `./base`, `./hooks`, and `./styles`; `products/yappc/frontend/libs/yappc-ui/src/components/theme/ThemeProvider.tsx:1-9` re-exports from `@yappc/ui/theme`; many internal files and app consumers import `@yappc/ui/theme`.
- Consumer impact: YAPPC web, YAPPC state storybook utilities, canvas package, and any external consumer of `@yappc/ui`.
- Duplication type if applicable: `ownership`
- Consolidation recommendation if applicable: make one package canonical for theme ownership. Either:
  - move all theme code to `@yappc/theme` and import that directly, or
  - add a real `./theme` export in `@yappc/ui` and stop self-importing package subpaths from within package source.
- Target location for consolidated code if applicable: prefer `products/yappc/frontend/compat/theme` only if it remains the canonical package; otherwise move theme fully under `products/yappc/frontend/libs/yappc-ui`.
- Migration notes: pick one owner first, add explicit subpath exports, then migrate internal imports to relative imports inside the owning package.
- Exact fix recommendation: stop using package-name self-imports inside `@yappc/ui`, replace them with relative imports, and either add or remove the `./theme` public subpath consistently.
- Test gaps: no packaging test verifies exported subpaths resolve.
- Documentation gaps: the current public API is under-documented and contradicts actual usage.

### SM-004

- Severity: `high`
- File path: `products/yappc/frontend/tsconfig.base.json:40`
- Module or package name: `YAPPC compat/shared workspace configuration`
- Problem to resolve: YAPPC path aliases still point to removed `libs/*` directories for compat packages that now live under `compat/*`.
- Why it matters: build and editor resolution can silently diverge from actual package locations, especially for local dev and package-to-package source linking.
- Evidence: `products/yappc/frontend/tsconfig.base.json:40-51` maps `@yappc/base-ui`, `@yappc/config-hooks`, `@yappc/navigation-ui`, and `@yappc/theme` to `libs/...`; `products/yappc/frontend/web/vite.config.ts:188-196` repeats those stale `../libs/...` paths; `products/yappc/frontend/libs` does not contain `base-ui`, `config-hooks`, `navigation-ui`, or `theme`.
- Consumer impact: YAPPC web builds, editor IntelliSense, and any workspace-local consumer using source aliases rather than package resolution.
- Duplication type if applicable: `workflow`
- Consolidation recommendation if applicable: centralize alias ownership in one generated/shared config and point all compat aliases to `compat/*`.
- Target location for consolidated code if applicable: `products/yappc/frontend/tsconfig.base.json` plus a single shared alias source used by Vite/Vitest scripts.
- Migration notes: fix alias maps first, then run typecheck/build for YAPPC frontend packages to catch any remaining stale imports.
- Exact fix recommendation: replace stale `libs/*` alias targets with `compat/*`, remove dead alias entries for packages that should no longer be imported directly, and generate Vite aliases from the tsconfig map instead of hand-copying.
- Test gaps: no config consistency test checks that path aliases resolve to existing directories.
- Documentation gaps: migration docs exist, but the executable config has not been updated to match them.

### SM-005

- Severity: `high`
- File path: `products/yappc/frontend/libs/chat/src/index.ts:13`
- Module or package name: `YAPPC messaging/chat/notification stack`
- Problem to resolve: chat and notifications exist in parallel in at least three places: `@yappc/chat`, `@yappc/notifications`, and the AI package’s internal `messaging` surface, while compat `@yappc/messaging` also exists as another entrypoint.
- Why it matters: the same responsibility is split across multiple shared modules, making ownership unclear and making migrations easy to get partially wrong.
- Evidence: `products/yappc/frontend/libs/chat/src/index.ts:13-34` and `products/yappc/frontend/libs/yappc-ai/src/chat/index.ts:13-34` are the same compatibility pattern; `products/yappc/frontend/compat/notifications/src/index.ts:13-35` and `products/yappc/frontend/libs/yappc-ai/src/notifications/index.ts:13-35` duplicate the same notification shim; deprecated compat `@yappc/crdt` also overlaps with `@yappc/collab`.
- Consumer impact: YAPPC apps and future maintainers choosing between multiple valid-looking package names.
- Duplication type if applicable: `ownership`
- Consolidation recommendation if applicable: designate `@yappc/messaging` as the only messaging public API and `@yappc/collab` as the only collaboration public API.
- Target location for consolidated code if applicable: `products/yappc/frontend/compat/messaging` for short-term compatibility, or a canonical `products/yappc/frontend/libs/messaging` package if the team wants a permanent owner outside AI.
- Migration notes: add a deprecation map, migrate imports in apps first, then delete duplicate shim implementations after package-level smoke tests pass.
- Exact fix recommendation: collapse chat/notification code into one owner, make deprecated packages pure re-export shells only, and remove duplicate component/hook implementations from `@yappc/ai` once messaging is split cleanly.
- Test gaps: no shared acceptance test ensures `@yappc/chat`, `@yappc/notifications`, and `@yappc/messaging` resolve to the same behavior during migration.
- Documentation gaps: current docs describe the migration, but code ownership has not actually converged.

### SM-006

- Severity: `medium`
- File path: `platform/typescript/utils/src/index.ts:1`
- Module or package name: `@ghatana/utils` and `@ghatana/platform-utils`
- Problem to resolve: two package names expose the same utility implementation, and downstream apps compensate with duplicate alias entries.
- Why it matters: every consumer and every build config must remember both names, which is ongoing workflow duplication with no functional upside.
- Evidence: `platform/typescript/utils/src/index.ts:1` re-exports the entire `@ghatana/platform-utils` package; `platform/typescript/design-system/package.json:60-69` depends on `@ghatana/utils` while also peering on `@ghatana/platform-utils`; `products/data-cloud/ui/vite.config.ts:10-11` aliases both names to the same file.
- Consumer impact: all platform UI consumers and any app maintaining local alias maps.
- Duplication type if applicable: `ownership`
- Consolidation recommendation if applicable: pick one canonical package name and reduce the other to a time-boxed deprecated shim.
- Target location for consolidated code if applicable: keep implementation in `platform/typescript/foundation/platform-utils`; deprecate `@ghatana/utils`.
- Migration notes: publish/announce the canonical name, update workspace imports incrementally, then remove duplicate alias definitions.
- Exact fix recommendation: forbid new imports from the deprecated name, add codemod support, and simplify package manifests so platform packages depend on only one utility package.
- Test gaps: no policy test ensures only the canonical import path is used.
- Documentation gaps: docs still refer to both names as active choices.

### SM-007

- Severity: `medium`
- File path: `platform/typescript/design-system/src/hooks/useAccessibleId.ts:1`
- Module or package name: `@ghatana/design-system`
- Problem to resolve: the design system exposes duplicate hook concepts for the same concerns, including `useId` vs `useAccessibleId` and `useReducedMotion` vs `usePrefersReducedMotion`.
- Why it matters: redundant shared APIs increase discovery cost, create inconsistent usage patterns, and make future behavioral fixes harder to apply uniformly.
- Evidence: `platform/typescript/design-system/src/hooks/useAccessibleId.ts:27-28` is a thin wrapper over `useId`; `platform/typescript/design-system/src/hooks/useReducedMotion.ts:27-106` and `platform/typescript/design-system/src/hooks/usePrefersReducedMotion.ts:8-40` both expose reduced-motion preference behavior.
- Consumer impact: all design-system consumers and the design-system maintainers themselves.
- Duplication type if applicable: `code`
- Consolidation recommendation if applicable: keep one canonical ID hook and one canonical reduced-motion hook; keep aliases only if there is a compatibility need.
- Target location for consolidated code if applicable: `platform/typescript/design-system/src/hooks`
- Migration notes: keep deprecated wrappers temporarily, add deprecation docs, migrate call sites, then remove the redundant API.
- Exact fix recommendation: document one preferred hook per concern and move shared implementation into a single internal helper.
- Test gaps: no explicit tests ensure both duplicate hooks stay behaviorally equivalent.
- Documentation gaps: hook docs do not explain which API is preferred.

### SM-008

- Severity: `medium`
- File path: `.gitignore:42`
- Module or package name: `shared source tree hygiene`
- Problem to resolve: compiled JavaScript, declaration files, and source maps are checked into shared source directories across multiple packages, and root ignore rules only protect `platform/typescript/**/src`.
- Why it matters: maintainers can edit the wrong artifact, review noise increases, and source-of-truth becomes ambiguous. This is especially risky in contract packages.
- Evidence: `.gitignore:42-47` only blocks compiled source artifacts under `platform/typescript/**/src`; tracked compiled files exist in `products/tutorputor/contracts/v1/types.js`, `products/tutorputor/libs/tutorputor-simulation/src/physics/index.js`, `products/dcmaar/libs/typescript/types/src/index.js`, and many YAPPC `*.d.ts` files under `src/`.
- Consumer impact: every consumer relying on generated artifacts matching source, plus reviewers and release tooling.
- Duplication type if applicable: `workflow`
- Consolidation recommendation if applicable: keep generated output only in `dist/` or dedicated generated directories and treat source trees as TypeScript-only.
- Target location for consolidated code if applicable: `dist/` or explicit `generated/` directories per package.
- Migration notes: add ignore rules first, then delete tracked generated files in a controlled cleanup PR, then regenerate only package build output.
- Exact fix recommendation: extend ignore rules repo-wide, remove checked-in build output from `src/`, and add CI checks that fail when generated artifacts reappear outside approved directories.
- Test gaps: no repository hygiene check exists for generated artifacts in source trees.
- Documentation gaps: package docs do not state which directories are authoritative source versus generated output.

### SM-009

- Severity: `low`
- File path: `products/dcmaar/libs/guardian-dashboard-core/package-lock.json:1`
- Module or package name: `workspace dependency management`
- Problem to resolve: `package-lock.json` files are checked into a pnpm workspace.
- Why it matters: mixed lockfile strategies create churn, false diffs, and ambiguity about the actual dependency authority.
- Evidence: root workspace uses `pnpm@10.30.3` in `package.json`; tracked npm lockfiles exist in `platform/typescript/canvas/package-lock.json`, `platform/typescript/foundation/platform-utils/package-lock.json`, `platform/typescript/theme/package-lock.json`, `products/dcmaar/libs/guardian-dashboard-core/package-lock.json`, and others.
- Consumer impact: maintainers and CI environments, not runtime consumers directly.
- Duplication type if applicable: `workflow`
- Consolidation recommendation if applicable: use `pnpm-lock.yaml` as the only JS lockfile authority for workspace-managed packages.
- Target location for consolidated code if applicable: repository root lockfile strategy.
- Migration notes: verify no package still depends on npm-specific workflows before deleting the lockfiles.
- Exact fix recommendation: remove nested `package-lock.json` files from workspace packages and add a lint/check to prevent reintroduction.
- Test gaps: no dependency-policy check guards against nested npm lockfiles.
- Documentation gaps: contributor docs should state pnpm is the only supported JS package manager in workspace packages.

### SM-010

- Severity: `medium`
- File path: `products/audio-video/libs/audio-video-types/src/index.ts:141`
- Module or package name: `shared contract safety`
- Problem to resolve: several shared contract packages still expose `any` in public APIs.
- Why it matters: shared modules are supposed to reduce surprises and improve reuse safety; `any` in public contracts pushes risk out to consumers and weakens schema evolution.
- Evidence: `products/audio-video/libs/audio-video-types/src/index.ts:145`, `:173`, and many later fields use `any`; `products/tutorputor/contracts/v1/services.ts:106-114` includes `manifest: any` and `Promise<any>`; `products/flashit/libs/ts/shared/src/api/client.ts:220-221` exposes `search(params: any): Promise<any>`.
- Consumer impact: all downstream apps lose compile-time guarantees on the most reusable contracts.
- Duplication type if applicable: `logic`
- Consolidation recommendation if applicable: define explicit DTOs or generic constraints in the contract packages rather than leaving each consumer to infer shape.
- Target location for consolidated code if applicable: each owning contract package, especially `@audio-video/types`, `@tutorputor/contracts`, and `@flashit/shared`.
- Migration notes: add explicit interfaces first, introduce backward-compatible unions if needed, then migrate consumers package by package.
- Exact fix recommendation: replace `any` with discriminated unions, documented generic parameters, or `unknown` plus schema guards.
- Test gaps: contract tests currently focus more on type presence than consumer-safe shape guarantees.
- Documentation gaps: fields with intentionally open shape need usage notes and validation guidance.

## Module-by-Module Review

### Platform

- `@ghatana/accessibility-audit` at `platform/typescript/accessibility-audit`
  Purpose: accessibility audit viewer/helpers.
  Main exports and responsibilities: audit/report UI.
  Main consumers: Software Org web.
  Key dependencies: React.
  Review status: acceptable with low confidence due no tests observed.
  Findings found in that unit: none material.
  Duplicates or overlaps found: overlaps conceptually with design-system accessibility utils but not enough evidence of harmful duplication.
  Consolidation opportunities: add README and contract tests before broader reuse.
  Test gaps: package manifest has test script but no package-local tests found.
  Documentation gaps: package-level purpose and public API docs are thin.
  Naming concerns: none.
  Maintainability concerns: medium due low validation coverage.

- `@ghatana/api` at `platform/typescript/api`
  Purpose: shared API layer.
  Main exports and responsibilities: API helpers/adapters.
  Main consumers: Software Org web and architecture docs.
  Key dependencies: fetch/client utilities.
  Review status: needs follow-up.
  Findings found in that unit: contributes to broad missing-test gap.
  Duplicates or overlaps found: possible overlap with product-local API clients.
  Consolidation opportunities: centralize product HTTP wrappers here only if interfaces are stable.
  Test gaps: no package-local tests found.
  Documentation gaps: package-level documentation insufficient.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@ghatana/canvas` at `platform/typescript/canvas`
  Purpose: canvas primitives.
  Main exports and responsibilities: core canvas and topology/flow integrations.
  Main consumers: Data Cloud UI.
  Key dependencies: design system.
  Review status: acceptable.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: ensure `flow-canvas` relationship is documented.
  Test gaps: no package-local tests found.
  Documentation gaps: public API docs could be tighter around subpaths.
  Naming concerns: none.
  Maintainability concerns: moderate due multiple related packages.

- `@ghatana/charts` at `platform/typescript/charts`
  Purpose: chart wrappers.
  Main exports and responsibilities: chart components around platform theme.
  Main consumers: `@dcmaar/ui`.
  Key dependencies: `@ghatana/theme`.
  Review status: acceptable.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: document consumer expectations and supported chart primitives.
  Test gaps: no package-local tests found.
  Documentation gaps: package docs missing.
  Naming concerns: none.
  Maintainability concerns: moderate.

- `@ghatana/design-system` at `platform/typescript/design-system`
  Purpose: canonical UI library.
  Main exports and responsibilities: atoms, molecules, organisms, hooks, layout, accessibility helpers.
  Main consumers: platform products across the repo.
  Key dependencies: `@ghatana/utils`, `@ghatana/theme`, `@ghatana/tokens`, `@ghatana/platform-utils`.
  Review status: healthy directionally, but overloaded.
  Findings found in that unit: `SM-006`, `SM-007`.
  Duplicates or overlaps found: duplicate hooks and compatibility re-exports.
  Consolidation opportunities: reduce redundant hooks and remove dual utility dependency names.
  Test gaps: no package-local `tests/` dir; only scattered source tests.
  Documentation gaps: preferred hook guidance is missing.
  Naming concerns: some hooks and compatibility exports are ambiguous.
  Maintainability concerns: high because the package is the central dependency for many apps.

- `@ghatana/platform-utils` at `platform/typescript/foundation/platform-utils`
  Purpose: canonical utility implementation.
  Main exports and responsibilities: formatting, classnames, platform detection, responsive and accessibility helpers.
  Main consumers: Data Cloud, YAPPC, platform UI.
  Key dependencies: none significant.
  Review status: healthy implementation, unclear canonicality.
  Findings found in that unit: `SM-006`.
  Duplicates or overlaps found: `@ghatana/utils`.
  Consolidation opportunities: make this the only canonical owner.
  Test gaps: no package-local tests found.
  Documentation gaps: migration notes exist, but canonical package naming is still unclear.
  Naming concerns: none on the package itself.
  Maintainability concerns: medium until alias duplication is removed.

- `@ghatana/i18n` at `platform/typescript/i18n`
  Purpose: i18n helpers.
  Main exports and responsibilities: localization primitives.
  Main consumers: unclear from active import scan.
  Key dependencies: none significant.
  Review status: no material issue found, but low usage visibility.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: document intended consumers.
  Test gaps: no tests found.
  Documentation gaps: thin.
  Naming concerns: none.
  Maintainability concerns: low.

- `@ghatana/platform-shell` at `platform/typescript/platform-shell`
  Purpose: platform shell integration.
  Main exports and responsibilities: shell abstractions.
  Main consumers: not prominent in current import scan.
  Key dependencies: none major.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: clarify role versus app-shell code.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: low.

- `@ghatana/realtime` at `platform/typescript/realtime`
  Purpose: realtime primitives.
  Main exports and responsibilities: realtime data hooks/adapters.
  Main consumers: Data Cloud, TutorPutor web, Software Org web.
  Key dependencies: none major.
  Review status: acceptable.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with YAPPC compat realtime.
  Consolidation opportunities: expose product-agnostic realtime API and retire product-specific shims.
  Test gaps: no tests found.
  Documentation gaps: needs contract docs.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@ghatana/sso-client` at `platform/typescript/sso-client`
  Purpose: SSO client utilities.
  Main exports and responsibilities: auth/SSO helpers.
  Main consumers: not prominent in current import scan.
  Key dependencies: browser/auth libs.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with product auth packages.
  Consolidation opportunities: document when to use versus product-local auth modules.
  Test gaps: no tests found.
  Documentation gaps: needs package docs.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@ghatana/theme` at `platform/typescript/theme`
  Purpose: canonical theme system.
  Main exports and responsibilities: providers, hooks, theme creation, validation, presets.
  Main consumers: design-system, Data Cloud, TutorPutor, YAPPC deps.
  Key dependencies: `@ghatana/tokens`.
  Review status: healthy, but central.
  Findings found in that unit: indirectly affected by `SM-006`.
  Duplicates or overlaps found: overlaps conceptually with YAPPC theme compat.
  Consolidation opportunities: prefer platform theme for products that do not need isolated branding stacks.
  Test gaps: no tests found.
  Documentation gaps: needs explicit product adoption guidance.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@ghatana/tokens` at `platform/typescript/tokens`
  Purpose: design tokens.
  Main exports and responsibilities: spacing, colors, typography, validation, CSS generation.
  Main consumers: theme, design-system, TutorPutor, Data Cloud.
  Key dependencies: none major.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: product-local token stacks still exist in YAPPC theme.
  Consolidation opportunities: long-term token convergence with YAPPC theme package.
  Test gaps: token validation test exists; broader consumer tests would help.
  Documentation gaps: strong relative to many other packages.
  Naming concerns: none.
  Maintainability concerns: low.

- `@ghatana/ui-integration` at `platform/typescript/ui-integration`
  Purpose: UI integration helpers.
  Main exports and responsibilities: integration utilities around design-system/theme/tokens.
  Main consumers: unclear from active imports.
  Key dependencies: design-system, theme, tokens, platform-utils.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with product-specific adapter libraries.
  Consolidation opportunities: document narrow intended use.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: broad name.
  Maintainability concerns: medium.

- `@ghatana/utils` at `platform/typescript/utils`
  Purpose: legacy/facade utility package.
  Main exports and responsibilities: full re-export of `@ghatana/platform-utils`.
  Main consumers: design-system and multiple apps.
  Key dependencies: `@ghatana/platform-utils`.
  Review status: should be deprecated.
  Findings found in that unit: `SM-006`.
  Duplicates or overlaps found: total overlap with `@ghatana/platform-utils`.
  Consolidation opportunities: remove as long-term package owner.
  Test gaps: no tests found.
  Documentation gaps: deprecation and migration docs missing.
  Naming concerns: misleading because it appears canonical.
  Maintainability concerns: medium.

### Audio-Video

- `@audio-video/client` at `products/audio-video/libs/audio-video-client`
  Purpose: shared service client.
  Main exports and responsibilities: STT/TTS/AI-voice/vision/multimodal client facade.
  Main consumers: Audio-Video desktop.
  Key dependencies: `@audio-video/types`.
  Review status: blocked by broken import.
  Findings found in that unit: `SM-001`.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: none beyond fixing package reference.
  Test gaps: no tests found.
  Documentation gaps: must document HTTP vs gRPC assumptions.
  Naming concerns: none.
  Maintainability concerns: high until fixed.

- `@audio-video/types` at `products/audio-video/libs/audio-video-types`
  Purpose: shared contracts.
  Main exports and responsibilities: service DTOs, status types, UI state types.
  Main consumers: `@audio-video/client`, Audio-Video desktop.
  Key dependencies: none.
  Review status: usable but weakly typed in places.
  Findings found in that unit: `SM-010`.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: replace open-ended `any` with explicit DTOs.
  Test gaps: no tests found.
  Documentation gaps: field-level contracts need examples.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@audio-video/ui` at `products/audio-video/libs/audio-video-ui`
  Purpose: shared UI hooks/components.
  Main exports and responsibilities: speech hooks and UI primitives.
  Main consumers: Audio-Video desktop, Data Cloud voice UI.
  Key dependencies: React/browser speech APIs.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with design-system or AI voice libs.
  Consolidation opportunities: document which hooks are product-specific.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

### DCMAAR

- `@dcmaar/dashboard-core` at `products/dcmaar/libs/guardian-dashboard-core`
  Purpose: shared role/section/dashboard logic.
  Main exports and responsibilities: role config, guards, features.
  Main consumers: child-mobile, parent-mobile, parent-dashboard.
  Key dependencies: React.
  Review status: acceptable.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: remove nested `package-lock.json`.
  Test gaps: local source tests exist; package-level API coverage could improve.
  Documentation gaps: package-level README absent.
  Naming concerns: package-lock uses `@guardian/dashboard-core`, inconsistent with package name.
  Maintainability concerns: medium.

- `@dcmaar/bridge-protocol` at `products/dcmaar/libs/typescript/agent-core/bridge-protocol`
  Purpose: bridge contracts.
  Main exports and responsibilities: agent bridge protocol types/utilities.
  Main consumers: agent-core ecosystem.
  Key dependencies: none major.
  Review status: acceptable but source contains compiled JS.
  Findings found in that unit: `SM-008`.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: keep generated output out of `src/`.
  Test gaps: no tests directory found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@dcmaar/agent-types` at `products/dcmaar/libs/typescript/agent-core/types`
  Purpose: agent type package.
  Main exports and responsibilities: agent contracts.
  Main consumers: agent-core ecosystem.
  Key dependencies: none major.
  Review status: acceptable.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with `@dcmaar/types`.
  Consolidation opportunities: clarify boundary between product-wide and agent-only types.
  Test gaps: no tests directory found.
  Documentation gaps: needs explicit boundary docs.
  Naming concerns: close to `@dcmaar/types`.
  Maintainability concerns: medium.

- `@dcmaar/agent-ui` at `products/dcmaar/libs/typescript/agent-core/ui`
  Purpose: agent UI package.
  Main exports and responsibilities: agent UI components.
  Main consumers: agent ecosystem.
  Key dependencies: React/UI libs.
  Review status: acceptable.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with `@dcmaar/ui`.
  Consolidation opportunities: clarify intended consumer split.
  Test gaps: no tests directory found.
  Documentation gaps: package docs needed.
  Naming concerns: overlap with `@dcmaar/ui`.
  Maintainability concerns: medium.

- `@dcmaar/browser-extension-core` at `products/dcmaar/libs/typescript/browser-extension-core`
  Purpose: extension runtime core.
  Main exports and responsibilities: plugin host, connector bridge, storage adapters, event primitives.
  Main consumers: browser-extension app, device-health app.
  Key dependencies: `@dcmaar/connectors`, `@dcmaar/plugin-abstractions`, `@dcmaar/types`.
  Review status: healthy.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: none urgent.
  Test gaps: has tests; extend package export smoke tests.
  Documentation gaps: package docs can improve.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@dcmaar/browser-extension-ui` at `products/dcmaar/libs/typescript/browser-extension-ui`
  Purpose: extension UI package.
  Main exports and responsibilities: browser extension UI components.
  Main consumers: extension apps.
  Key dependencies: React/UI libs.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with `@dcmaar/ui`.
  Consolidation opportunities: align with product-wide UI strategy.
  Test gaps: no tests found.
  Documentation gaps: package docs thin.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@dcmaar/config-presets` at `products/dcmaar/libs/typescript/config-presets`
  Purpose: shared config presets.
  Main exports and responsibilities: config presets/build settings.
  Main consumers: DCMAAR apps/libs.
  Key dependencies: TypeScript/build tooling.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: document preset ownership.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: low.

- `@dcmaar/connectors` at `products/dcmaar/libs/typescript/connectors`
  Purpose: connector SDK.
  Main exports and responsibilities: connectors, resilience, observability, adapters.
  Main consumers: extension runtime, AI platform adapters, device-health.
  Key dependencies: Zod, Ajv.
  Review status: strategically important and relatively mature.
  Findings found in that unit: none material in code sampled.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: package is a good candidate for stricter export tests and script cleanup.
  Test gaps: tests exist; package manifest scripts still deserve validation.
  Documentation gaps: strong relative to peers.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@dcmaar/ui` at `products/dcmaar/libs/typescript/dcmaar-ui`
  Purpose: DCMAAR UI facade.
  Main exports and responsibilities: product aliases for design-system/charts plus small local types.
  Main consumers: device-health.
  Key dependencies: `@ghatana/design-system`, `@ghatana/charts`.
  Review status: functional but thin.
  Findings found in that unit: contributes to duplication and ownership sprawl.
  Duplicates or overlaps found: heavy overlap with `@ghatana/design-system`.
  Consolidation opportunities: either keep only product-specific additions here or migrate device-health directly to platform UI.
  Test gaps: no tests.
  Documentation gaps: should define what belongs here versus platform UI.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@dcmaar/plugin-abstractions` at `products/dcmaar/libs/typescript/plugin-abstractions`
  Purpose: plugin abstractions/core managers.
  Main exports and responsibilities: interfaces, registry, loader, plugin manager support.
  Main consumers: browser-extension-core, plugin-extension, apps.
  Key dependencies: `@dcmaar/types`.
  Review status: blocked by stale package name.
  Findings found in that unit: `SM-002`.
  Duplicates or overlaps found: some plugin ownership spread into `plugin-extension`.
  Consolidation opportunities: standardize types/imports.
  Test gaps: no tests found.
  Documentation gaps: stale comments reference old package names.
  Naming concerns: none.
  Maintainability concerns: high until fixed.

- `@dcmaar/plugin-extension` at `products/dcmaar/libs/typescript/plugin-extension`
  Purpose: plugin extension helpers/implementations.
  Main exports and responsibilities: base monitors and plugin helpers.
  Main consumers: device-health and host apps.
  Key dependencies: `@dcmaar/plugin-abstractions`, `@dcmaar/types`.
  Review status: acceptable.
  Findings found in that unit: none material directly.
  Duplicates or overlaps found: role split with `plugin-abstractions` should be documented.
  Consolidation opportunities: keep abstractions vs implementations boundary crisp.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: close to `plugin-abstractions`.
  Maintainability concerns: medium.

- `@dcmaar/types` at `products/dcmaar/libs/typescript/types`
  Purpose: shared product types.
  Main exports and responsibilities: common/event/config/plugin/error types.
  Main consumers: all major DCMAAR shared packages.
  Key dependencies: none major.
  Review status: central, but source tree contains compiled JS and d.ts.
  Findings found in that unit: `SM-008`.
  Duplicates or overlaps found: boundary overlap with `@dcmaar/agent-types`.
  Consolidation opportunities: keep `src/` TypeScript-only and document boundary with agent-specific types.
  Test gaps: no tests found.
  Documentation gaps: package-level docs needed.
  Naming concerns: overlap with `@dcmaar/agent-types`.
  Maintainability concerns: medium.

### TutorPutor

- `@tutorputor/contracts` at `products/tutorputor/contracts`
  Purpose: shared domain and service contracts.
  Main exports and responsibilities: v1 contracts for types, services, telemetry, simulation, curriculum, assessments.
  Main consumers: TutorPutor apps, services, tools.
  Key dependencies: TypeScript only.
  Review status: central and useful, but source-of-truth is muddy.
  Findings found in that unit: `SM-008`, `SM-010`.
  Duplicates or overlaps found: checked-in `v1/*.js` and `dist/` outputs duplicate source intent.
  Consolidation opportunities: keep authored source in `v1/*.ts`, build output in `dist/` only.
  Test gaps: tests exist, but more consumer-shape tests needed.
  Documentation gaps: package lacks clear canonical API docs.
  Naming concerns: root export vs explicit `v1/*` subpaths need clearer guidance.
  Maintainability concerns: high because many services depend on it.

- `@tutorputor/ai` at `products/tutorputor/libs/tutorputor-ai`
  Purpose: AI helpers.
  Main exports and responsibilities: AI integration helpers on top of core/contracts.
  Main consumers: TutorPutor services/apps.
  Key dependencies: `@tutorputor/contracts`, `@tutorputor/core`.
  Review status: no material issue found in sampled entrypoints.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with service-layer AI implementations.
  Consolidation opportunities: document where orchestration should live.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@tutorputor/core` at `products/tutorputor/libs/tutorputor-core`
  Purpose: core domain/platform layer.
  Main exports and responsibilities: DB, kernel, analytics, plugins, validation.
  Main consumers: API gateway, web/admin/mobile, platform service, tools.
  Key dependencies: `@tutorputor/contracts`.
  Review status: strategically important.
  Findings found in that unit: impacted by generated output hygiene and broad missing-test coverage.
  Duplicates or overlaps found: some boundary overlap with platform service modules.
  Consolidation opportunities: clearer split between core domain logic and service orchestration.
  Test gaps: scattered tests exist, but no obvious package-wide API contract suite.
  Documentation gaps: core package needs an architectural README.
  Naming concerns: none.
  Maintainability concerns: medium-high.

- `@tutorputor/simulation` at `products/tutorputor/libs/tutorputor-simulation`
  Purpose: simulation runtime/UI.
  Main exports and responsibilities: physics engine, rendering, collaboration, serialization.
  Main consumers: TutorPutor web/admin/platform.
  Key dependencies: contracts, core, platform theme/tokens.
  Review status: feature-rich but hygiene issue present.
  Findings found in that unit: `SM-008`.
  Duplicates or overlaps found: checked-in JS and d.ts under `src/`.
  Consolidation opportunities: generated output cleanup and clearer boundary between runtime and UI.
  Test gaps: no package tests found.
  Documentation gaps: package-level docs needed.
  Naming concerns: none.
  Maintainability concerns: high due size plus generated artifacts.

- `@tutorputor/ui` at `products/tutorputor/libs/tutorputor-ui`
  Purpose: shared TutorPutor UI helpers.
  Main exports and responsibilities: UI utilities/themes/components.
  Main consumers: TutorPutor web/admin.
  Key dependencies: platform theme/tokens.
  Review status: no material issue found in sampled entrypoints.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with platform design-system.
  Consolidation opportunities: document what is TutorPutor-specific versus platform-generic.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

### Flashit

- `@flashit/shared` at `products/flashit/libs/ts/shared`
  Purpose: shared API client, validation, hooks, atoms, and types.
  Main exports and responsibilities: client-side shared surface for Flashit apps.
  Main consumers: Flashit mobile and web.
  Key dependencies: React/Jotai/Zod.
  Review status: useful, but broad.
  Findings found in that unit: `SM-010` for open-ended public API methods.
  Duplicates or overlaps found: possible overlap between hooks and app-local platform abstractions.
  Consolidation opportunities: split API client from hooks if the surface keeps growing.
  Test gaps: some tests exist; coverage should expand around hooks and search contract typing.
  Documentation gaps: package-level docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

### YAPPC

- `@yappc/api` at `products/yappc/frontend/libs/api`
  Purpose: shared API layer.
  Main exports and responsibilities: auth, AI, GraphQL, hook exports, DevSecOps client helpers.
  Main consumers: YAPPC web and other YAPPC libs.
  Key dependencies: TypeScript only in manifest, runtime deps inferred from source.
  Review status: acceptable but under-specified.
  Findings found in that unit: none material directly.
  Duplicates or overlaps found: overlap with config-hooks and product-local client wrappers.
  Consolidation opportunities: move config/data hooks here only if compat package is retired.
  Test gaps: no tests found.
  Documentation gaps: needs API ownership docs.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/auth` at `products/yappc/frontend/libs/auth`
  Purpose: auth helpers.
  Main exports and responsibilities: RBAC and OAuth helpers.
  Main consumers: YAPPC frontend.
  Key dependencies: none major.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with platform SSO helpers.
  Consolidation opportunities: document when to use platform SSO versus local auth helpers.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/chat` at `products/yappc/frontend/libs/chat`
  Purpose: deprecated chat shim.
  Main exports and responsibilities: compatibility re-export plus local component exports.
  Main consumers: intended migration layer.
  Key dependencies: messaging ownership in AI stack.
  Review status: should be retired.
  Findings found in that unit: `SM-005`.
  Duplicates or overlaps found: overlaps with `@yappc/messaging` and AI messaging.
  Consolidation opportunities: collapse into one canonical messaging owner.
  Test gaps: no tests found.
  Documentation gaps: deprecation exists but execution is incomplete.
  Naming concerns: still looks canonical.
  Maintainability concerns: medium.

- `@yappc/code-editor` at `products/yappc/frontend/libs/code-editor`
  Purpose: shared editor package.
  Main exports and responsibilities: editor primitives.
  Main consumers: IDE and frontend apps.
  Key dependencies: editor ecosystem.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: none urgent.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/collab` at `products/yappc/frontend/libs/collab`
  Purpose: canonical collaboration package.
  Main exports and responsibilities: CRDT, websocket, presence, collaboration UI.
  Main consumers: YAPPC frontend packages.
  Key dependencies: collaboration internals.
  Review status: strategically correct destination package.
  Findings found in that unit: indirectly affected by `SM-005`.
  Duplicates or overlaps found: `@yappc/crdt`.
  Consolidation opportunities: finish migration of all CRDT ownership here.
  Test gaps: no tests found.
  Documentation gaps: migration docs exist, package docs still needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/config` at `products/yappc/frontend/libs/config`
  Purpose: config helpers.
  Main exports and responsibilities: config loading, async patterns, feature flags, result helpers.
  Main consumers: YAPPC frontend.
  Key dependencies: none major.
  Review status: acceptable.
  Findings found in that unit: none material.
  Duplicates or overlaps found: overlap with `@yappc/config-hooks`.
  Consolidation opportunities: retire compat package and keep config ownership here.
  Test gaps: no tests found.
  Documentation gaps: needs ownership docs.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/ide` at `products/yappc/frontend/libs/ide`
  Purpose: IDE library.
  Main exports and responsibilities: IDE-specific shared logic.
  Main consumers: YAPPC frontend/web.
  Key dependencies: editor/collab packages.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: none urgent.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/shortcuts` at `products/yappc/frontend/libs/shortcuts`
  Purpose: shortcut UI/hooks.
  Main exports and responsibilities: command palette and shortcut helpers.
  Main consumers: YAPPC frontend apps.
  Key dependencies: React.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: possible overlap with `@yappc/ui` command palette.
  Consolidation opportunities: centralize shortcut primitives if overlap grows.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/testing` at `products/yappc/frontend/libs/testing`
  Purpose: shared testing helpers.
  Main exports and responsibilities: test utilities.
  Main consumers: YAPPC packages.
  Key dependencies: test toolchain.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: use as central owner for package smoke tests.
  Test gaps: package should include self-tests.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: low.

- `@yappc/ai` at `products/yappc/frontend/libs/yappc-ai`
  Purpose: AI library plus overlapping messaging surface.
  Main exports and responsibilities: AI providers, agents, hooks, UI, duplicate chat/notifications/messaging.
  Main consumers: YAPPC frontend.
  Key dependencies: `@yappc/core`, `@yappc/ui`, `@yappc/theme`.
  Review status: over-scoped.
  Findings found in that unit: `SM-005`.
  Duplicates or overlaps found: messaging/chat/notification ownership overlaps heavily with compat and deprecated chat package.
  Consolidation opportunities: keep AI in this package, move messaging to a dedicated shared owner.
  Test gaps: some tests exist in `src/ml`; package-wide API coverage is still weak.
  Documentation gaps: package purpose is too broad.
  Naming concerns: none.
  Maintainability concerns: high.

- `@yappc/core` at `products/yappc/frontend/libs/yappc-core`
  Purpose: shared core/types.
  Main exports and responsibilities: core types and shared frontend/domain utilities.
  Main consumers: YAPPC UI and AI packages.
  Key dependencies: none major.
  Review status: acceptable.
  Findings found in that unit: none material directly.
  Duplicates or overlaps found: overlap with `@yappc/types`.
  Consolidation opportunities: clarify whether compat types package remains necessary.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: close to `@yappc/types`.
  Maintainability concerns: medium.

- `@yappc/state` at `products/yappc/frontend/libs/yappc-state`
  Purpose: shared state package.
  Main exports and responsibilities: atoms, providers, sync helpers.
  Main consumers: YAPPC web and Storybook helpers.
  Key dependencies: Jotai.
  Review status: under-specified manifest.
  Findings found in that unit: none material directly.
  Duplicates or overlaps found: possible overlap with `@yappc/realtime`.
  Consolidation opportunities: document boundary between state and realtime compatibility layer.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/ui` at `products/yappc/frontend/libs/yappc-ui`
  Purpose: shared UI package.
  Main exports and responsibilities: components, hooks, theme-adjacent exports, styles.
  Main consumers: YAPPC web, AI, state, canvas.
  Key dependencies: `@yappc/core`, `@yappc/theme`.
  Review status: currently unsafe at package boundary.
  Findings found in that unit: `SM-003`.
  Duplicates or overlaps found: theme ownership overlaps with `@yappc/theme`.
  Consolidation opportunities: choose one canonical owner for theme exports.
  Test gaps: no packaging tests.
  Documentation gaps: public subpaths are not documented consistently.
  Naming concerns: `@yappc/ui` appears to own theme but package exports do not.
  Maintainability concerns: high.

- `@yappc/base-ui` at `products/yappc/frontend/compat/base-ui`
  Purpose: base UI compatibility package.
  Main exports and responsibilities: small wrapper package.
  Main consumers: mostly legacy config/lint references.
  Key dependencies: Base UI libs.
  Review status: legacy.
  Findings found in that unit: part of `SM-004`.
  Duplicates or overlaps found: overlaps with `@yappc/ui`.
  Consolidation opportunities: retire after alias cleanup.
  Test gaps: no tests found.
  Documentation gaps: deprecation/retirement plan should be package-local.
  Naming concerns: still looks first-class.
  Maintainability concerns: medium.

- `@yappc/config-hooks` at `products/yappc/frontend/compat/config-hooks`
  Purpose: config compatibility package.
  Main exports and responsibilities: config hook exports.
  Main consumers: mostly via stale alias/config.
  Key dependencies: React Query.
  Review status: legacy.
  Findings found in that unit: part of `SM-004`.
  Duplicates or overlaps found: overlaps with `@yappc/api` and `@yappc/config`.
  Consolidation opportunities: retire and route users to canonical owners.
  Test gaps: no tests.
  Documentation gaps: package-local migration notes missing.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/crdt` at `products/yappc/frontend/compat/crdt`
  Purpose: deprecated CRDT compatibility package.
  Main exports and responsibilities: CRDT compatibility surface.
  Main consumers: mostly docs and migration artifacts.
  Key dependencies: `@yappc/collab`.
  Review status: legacy.
  Findings found in that unit: part of `SM-005`.
  Duplicates or overlaps found: `@yappc/collab`.
  Consolidation opportunities: complete migration and delete.
  Test gaps: no tests found.
  Documentation gaps: deprecation should include deletion criteria.
  Naming concerns: still publishable-looking.
  Maintainability concerns: medium.

- `@yappc/development-ui` at `products/yappc/frontend/compat/development-ui`
  Purpose: development UI compatibility package.
  Main exports and responsibilities: chart/card UI.
  Main consumers: mostly legacy references.
  Key dependencies: React.
  Review status: legacy.
  Findings found in that unit: part of `SM-004`.
  Duplicates or overlaps found: overlaps with core/UI package destinations described in migration docs.
  Consolidation opportunities: retire after alias cleanup.
  Test gaps: no tests found.
  Documentation gaps: package-local migration notes needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/initialization-ui` at `products/yappc/frontend/compat/initialization-ui`
  Purpose: initialization UI compatibility package.
  Main exports and responsibilities: preset/resource UI.
  Main consumers: mostly legacy references.
  Key dependencies: React.
  Review status: legacy.
  Findings found in that unit: part of `SM-004`.
  Duplicates or overlaps found: overlaps with core/UI destinations.
  Consolidation opportunities: retire after alias cleanup.
  Test gaps: no tests found.
  Documentation gaps: migration notes needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/messaging` at `products/yappc/frontend/compat/messaging`
  Purpose: messaging compatibility package.
  Main exports and responsibilities: canonical short-term messaging entrypoint.
  Main consumers: intended migration target.
  Key dependencies: React/Jotai/date-fns.
  Review status: candidate canonical owner, but ecosystem is not aligned yet.
  Findings found in that unit: `SM-005`.
  Duplicates or overlaps found: `@yappc/chat`, `@yappc/notifications`, `@yappc/ai` messaging.
  Consolidation opportunities: make this the only import path or replace with a dedicated non-compat package.
  Test gaps: no tests found.
  Documentation gaps: package-level ownership statement needed.
  Naming concerns: package lives under `compat/` even though it is the preferred target.
  Maintainability concerns: high until ownership is settled.

- `@yappc/navigation-ui` at `products/yappc/frontend/compat/navigation-ui`
  Purpose: navigation compatibility package.
  Main exports and responsibilities: tabs/breadcrumb/navigation components.
  Main consumers: mostly legacy references.
  Key dependencies: Base UI and design-system.
  Review status: legacy.
  Findings found in that unit: part of `SM-004`.
  Duplicates or overlaps found: overlaps with `@yappc/ui`.
  Consolidation opportunities: retire or fold into `@yappc/ui/navigation`.
  Test gaps: no tests found.
  Documentation gaps: migration notes needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/notifications` at `products/yappc/frontend/compat/notifications`
  Purpose: deprecated notifications compatibility package.
  Main exports and responsibilities: re-export plus local component/hook compatibility.
  Main consumers: intended migration layer.
  Key dependencies: `@yappc/ai`.
  Review status: should be retired.
  Findings found in that unit: `SM-005`.
  Duplicates or overlaps found: `@yappc/messaging` and AI notifications.
  Consolidation opportunities: make it a pure alias or delete after migration.
  Test gaps: no tests found.
  Documentation gaps: deprecation exists, but deletion plan is missing.
  Naming concerns: still looks valid.
  Maintainability concerns: medium.

- `@yappc/realtime` at `products/yappc/frontend/compat/realtime`
  Purpose: realtime compatibility package.
  Main exports and responsibilities: websocket client helpers.
  Main consumers: mostly legacy references.
  Key dependencies: Jotai.
  Review status: legacy.
  Findings found in that unit: overlaps noted but no direct defect confirmed.
  Duplicates or overlaps found: overlaps with `@yappc/state` and platform realtime patterns.
  Consolidation opportunities: clarify whether state package owns realtime client state.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: medium.

- `@yappc/theme` at `products/yappc/frontend/compat/theme`
  Purpose: theme compatibility package.
  Main exports and responsibilities: theme providers, tokens, enhanced theme helpers.
  Main consumers: `@yappc/ui` dependency and stale aliases.
  Key dependencies: MUI.
  Review status: central to YAPPC theme confusion.
  Findings found in that unit: `SM-003`, `SM-004`.
  Duplicates or overlaps found: overlaps with `@yappc/ui` theme exports.
  Consolidation opportunities: either own theme here explicitly or move theme fully into `@yappc/ui`.
  Test gaps: no tests found.
  Documentation gaps: canonical owner is unclear.
  Naming concerns: none.
  Maintainability concerns: high.

- `@yappc/types` at `products/yappc/frontend/compat/types`
  Purpose: shared compatibility type package.
  Main exports and responsibilities: registry, tasks, devsecops, workflow types.
  Main consumers: migration target for some historical store/type users.
  Key dependencies: Zod.
  Review status: acceptable but overlaps with `@yappc/core`.
  Findings found in that unit: none material directly.
  Duplicates or overlaps found: type ownership overlap with `@yappc/core`.
  Consolidation opportunities: decide whether `core` or `types` is the long-term home.
  Test gaps: no tests found.
  Documentation gaps: package docs and intended consumer guidance needed.
  Naming concerns: overlap with `@yappc/core`.
  Maintainability concerns: medium.

- `@yappc/utils` at `products/yappc/frontend/compat/utils`
  Purpose: utility compatibility package.
  Main exports and responsibilities: feature flags/performance helpers.
  Main consumers: mostly legacy/migration context.
  Key dependencies: none major.
  Review status: legacy.
  Findings found in that unit: part of broader YAPPC compat sprawl.
  Duplicates or overlaps found: overlaps with `@yappc/core` and `@ghatana/platform-utils`.
  Consolidation opportunities: retire or move surviving helpers into one canonical owner.
  Test gaps: no tests found.
  Documentation gaps: migration notes needed.
  Naming concerns: utility ownership unclear.
  Maintainability concerns: medium.

- `@yappc/eslint-config-custom` at `products/yappc/frontend/packages/eslint-config-custom`
  Purpose: shared lint config.
  Main exports and responsibilities: ESLint configuration package.
  Main consumers: YAPPC frontend packages.
  Key dependencies: ESLint ecosystem.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: none urgent.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: low.

- `@yappc/tsconfig` at `products/yappc/frontend/packages/tsconfig`
  Purpose: shared tsconfig package.
  Main exports and responsibilities: config-only package.
  Main consumers: YAPPC frontend packages.
  Key dependencies: none.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: ensure app configs actually extend it consistently.
  Test gaps: config validation could be added.
  Documentation gaps: package docs thin.
  Naming concerns: none.
  Maintainability concerns: low.

- `@yappc/vite-plugin-live-edit` at `products/yappc/frontend/packages/vite-plugin-live-edit`
  Purpose: shared Vite plugin.
  Main exports and responsibilities: live-edit Vite plugin.
  Main consumers: YAPPC frontend build pipeline.
  Key dependencies: Vite.
  Review status: no material issue found.
  Findings found in that unit: none material.
  Duplicates or overlaps found: none confirmed.
  Consolidation opportunities: none urgent.
  Test gaps: no tests found.
  Documentation gaps: package docs needed.
  Naming concerns: none.
  Maintainability concerns: low.

## Contract and API Risks

- `@audio-video/client` and `@dcmaar/plugin-abstractions` each contain a broken package import at the shared API boundary.
- `@yappc/ui` exposes a package surface that its own source depends on but its manifest does not export.
- `@audio-video/types`, `@tutorputor/contracts`, and `@flashit/shared` still leak `any` into reusable public contracts.
- `@ghatana/design-system` exposes multiple hooks for the same concern without a documented preferred API.

## Duplicate Code and Logic

- `@yappc/chat` and `@yappc/ai/src/chat` duplicate the same deprecated compatibility barrel structure.
- `@yappc/notifications` and `@yappc/ai/src/notifications` duplicate the same deprecated compatibility barrel structure.
- `@ghatana/utils` duplicates `@ghatana/platform-utils` at the package boundary.
- `useAccessibleId` duplicates `useId`, and `usePrefersReducedMotion` duplicates the preference portion of `useReducedMotion`.

## Duplicate Effort and Overlapping Responsibilities

- YAPPC messaging ownership is split between compat and AI libraries.
- YAPPC theme ownership is split between `@yappc/theme` and `@yappc/ui`.
- DCMAAR UI ownership is split between `@dcmaar/ui` and direct `@ghatana/design-system` consumption.
- Type ownership is split in several ecosystems: `@dcmaar/types` vs `@dcmaar/agent-types`, `@yappc/types` vs `@yappc/core`.

## Sprawled Modules and Fragmented Ownership

- `@yappc/ai` is too broad; it owns AI, agents, hooks, UI, plus messaging shims.
- `@tutorputor/simulation` mixes engine, rendering, collaboration, serialization, and checked-in generated output.
- `@ghatana/design-system` mixes current APIs, compatibility exports, and duplicated hooks in one very wide surface.

## Consolidation Opportunities

1. Collapse utility ownership onto `@ghatana/platform-utils` and deprecate `@ghatana/utils`.
2. Collapse YAPPC messaging onto one canonical package, ideally `@yappc/messaging` or a new dedicated `libs/messaging` package.
3. Finish the YAPPC CRDT migration so `@yappc/collab` is the only collaboration owner.
4. Decide whether YAPPC theme lives in `@yappc/theme` or `@yappc/ui`, then make package exports match that choice.
5. Keep generated output only in `dist/` or explicit `generated/` directories, never mixed into `src/`.
6. Reduce `@dcmaar/ui` to product-specific additions only, or migrate consumers directly to `@ghatana/design-system`.

## Recommended Simplifications

- Remove deprecated package-name references from source comments and export barrels.
- Generate Vite aliases from `tsconfig` rather than maintaining parallel hand-written maps.
- Add package export smoke tests for every published or reusable shared package.
- Add a repo hygiene check for `package-lock.json` and generated artifacts in `src/`.

## Naming and Documentation Issues

- `@ghatana/utils` appears canonical even though it is only a facade.
- `@yappc/chat` and `@yappc/notifications` still look like real packages despite deprecation.
- `@yappc/ui` and `@yappc/theme` do not clearly communicate which package owns theme exports.
- Several packages have no README or package-level usage guidance despite wide reuse.

## Dead Code and Redundant Abstractions

- YAPPC compat packages with little or no active runtime consumption should be treated as removal candidates after migration validation.
- `@dcmaar/ui` is currently a thin adapter layer; if product-specific types do not grow, this may be a redundant abstraction.
- Duplicate deprecated barrels in YAPPC messaging are maintenance-only code until the migration completes.

## Performance Concerns

- No severe algorithmic hot-path issue was confirmed in sampled shared code.
- The bigger performance risk is organizational: duplicate packages force repeated resolution config, repeated bundling decisions, and repeated maintenance work.
- Large packages like `@yappc/ai` and `@ghatana/design-system` should eventually be split by stable ownership boundaries if build times or bundle size become problematic.

## Missing Test Coverage

Highest-priority gaps:

- package export smoke tests for `@audio-video/client`, `@dcmaar/plugin-abstractions`, `@yappc/ui`, `@yappc/messaging`, and `@tutorputor/contracts`
- consumer-contract tests for `@audio-video/types`, `@flashit/shared`, and `@tutorputor/contracts`
- repository hygiene tests for generated files and nested lockfiles
- alias/config consistency tests in YAPPC frontend

Broad gap:

- many shared packages declare no package-local tests despite being reused across apps

## Full Remediation Plan

### Phase 1: Stop the known breakages

1. Fix `SM-001`, `SM-002`, `SM-003`, and `SM-004`.
2. Add smoke tests that import every exported subpath from the affected packages.
3. Add CI checks for unresolved workspace package names and missing export targets.

### Phase 2: Remove ambiguous ownership

1. Resolve the canonical owner for Ghatana utilities.
2. Resolve the canonical owner for YAPPC messaging.
3. Resolve the canonical owner for YAPPC theme.
4. Decide whether `@dcmaar/ui` remains a real product abstraction or becomes a migration-only shell.

### Phase 3: Clean source-of-truth hygiene

1. Remove generated JS/d.ts from shared `src/` trees.
2. Remove nested `package-lock.json` files from workspace packages.
3. Add repo guards so generated/source pollution does not return.

### Phase 4: Strengthen contracts

1. Replace public `any` in shared contracts with explicit DTOs or `unknown` plus validation.
2. Add consumer-driven tests for the most reused contract packages.
3. Add README-level usage docs for all high-reuse shared packages.

## All Unresolved Findings By Severity

### Critical

- `SM-001` `@audio-video/client` imports a non-existent contract package.
- `SM-003` `@yappc/ui` depends on a non-exported `./theme` subpath.

### High

- `SM-002` `@dcmaar/plugin-abstractions` exports from a stale package name.
- `SM-004` YAPPC alias maps still point to removed `libs/*` compat locations.
- `SM-005` YAPPC messaging/notification ownership is split across multiple shared packages.

### Medium

- `SM-006` `@ghatana/utils` and `@ghatana/platform-utils` duplicate utility ownership.
- `SM-007` `@ghatana/design-system` exposes redundant hooks for the same concerns.
- `SM-008` generated artifacts are checked into shared source trees.
- `SM-010` public shared contracts still expose `any`.

### Low

- `SM-009` nested `package-lock.json` files are checked into a pnpm workspace.

## All Unresolved Findings By Module

- `@audio-video/client`: `SM-001`
- `@audio-video/types`: `SM-010`
- `@dcmaar/plugin-abstractions`: `SM-002`
- `@ghatana/design-system`: `SM-007`
- `@ghatana/platform-utils`: `SM-006`
- `@ghatana/utils`: `SM-006`
- `@tutorputor/contracts`: `SM-008`, `SM-010`
- `@tutorputor/simulation`: `SM-008`
- `@flashit/shared`: `SM-010`
- `@yappc/ui`: `SM-003`
- `YAPPC compat/shared workspace configuration`: `SM-004`
- `@yappc/chat`: `SM-005`
- `@yappc/messaging`: `SM-005`
- `@yappc/notifications`: `SM-005`
- `@yappc/ai`: `SM-005`
- `@yappc/crdt`: `SM-005`
- `shared source tree hygiene`: `SM-008`
- `workspace dependency management`: `SM-009`

## Assumptions and Limitations

- This was a static audit of the current workspace on March 25, 2026.
- I did not execute the full monorepo test suite or package builds.
- The worktree is heavily dirty, so the audit intentionally avoided treating unrelated in-flight changes as defects unless they affected the reviewed shared module boundaries directly.
- Consumer mapping used manifest review plus import scanning; packages referenced only through runtime plugin loading or undocumented codegen may be undercounted.
- Modules marked “no material issue found” should still be considered lower-risk, not proven-correct.

Overall shared module health: functional but not yet cleanly canonical. The fastest path to materially better reuse safety is to fix the package-boundary breakages first, then collapse duplicated ownership in YAPPC and the platform utility layer, then clean generated artifacts and lockfile drift from the source tree.
