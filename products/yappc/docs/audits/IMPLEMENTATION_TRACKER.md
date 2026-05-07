# YAPPC End-to-End Product Correctness Tracker

Source audit: `products/yappc/docs/audits/end-to-end-product-correctness-audit.md`

Last updated: 2026-05-06

## Current Implementation Pass

This pass focuses on the production-blocking persistence and API drift items that can be safely changed in the mounted frontend without rewriting the entire YAPPC product surface in one change.

| ID | Status | Implementation evidence | Verification |
| --- | --- | --- | --- |
| P0-001 | Partially implemented | Page artifact HTTP persistence now sends tenant/workspace/project scope, credentials, `If-Match`, and distinct 401/403/409/422 errors. Conflict errors are not hidden by local fallback. Sensitive local drafts are blocked by policy. | `vitest run src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts` |
| P0-002 | Partially implemented | Mounted phase cockpit data now uses canonical `yappcApi` methods for project, activity, and next-phase preview instead of raw `fetch`. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts` |
| P0-003 | Partially implemented | Canonical `projects.list` now requires a `workspaceId` query parameter and returns the owned/included list contract. | Covered by type surface; call-site migration remains open where legacy raw workspace hooks are still used. |
| P0-004 | Partially implemented | Primary CTAs now respect adaptive locking and Generate/Run CTAs call typed backend operations instead of only emitting local feedback. Remaining phase CTAs still need backend-owned promote/reject/audit operations. | `vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx` |
| P0-005 | Partially implemented | Mounted cockpit uses `getAdaptivePhaseCockpitConfig` at render time for blocker, gate, tier, role, and feature-flag locking. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts` |
| P0-006 | Partially implemented | Generate phase starts `/api/v1/yappc/generate` through `yappcApi`, requests `/api/v1/yappc/generate/diff`, and surfaces the backend run id for review. Apply/reject/rollback endpoints remain open because no mounted backend contract was found in this pass. | `vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx` |
| P0-007 | Partially implemented | Run phase starts `/api/v1/workflows/yappc-run/start` with authenticated tenant scope, then checks `/api/v1/workflows/{runId}/status` and surfaces the backend run id/status. Readiness is respected from lifecycle preview. Rollback/promote orchestration remains open because no mounted backend contract was found in this pass. | `vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx` |
| P0-008 | Partially implemented | Source import no longer uses the hardcoded `imported-project`; source imports require active project, tenant, and workspace context. Browser source imports now require governed server orchestration through the canonical backend import endpoint with tenant/workspace/project headers, max source locator validation, and no local fallback when governed import is unavailable. Full resumable import-job UX remains open. | `vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/services/compiler/__tests__/ImportSourceWorkflow.test.ts` |
| P0-009 | Existing coverage retained | Imported multi-page artifacts continue to create additional page designer nodes instead of collapsing all pages into one root. | Existing PageDesignerNode behavior retained; deeper artifact graph persistence remains open. |
| P0-010 | Partially implemented | Page builder command audit hooks now emit scoped `/api/audit/events` records with actor, tenant, workspace, project, artifact, operation, outcome, changed node ids, document ids, and validation count. Audit persistence failures are surfaced in the builder instead of being fully silent. Broader telemetry/correlation coverage remains open. | `vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx` |
| P0-011 | Partially implemented | Local page artifact fallback now blocks `SENSITIVE`, `CREDENTIALS`, and `REGULATED` classifications unless policy is explicitly widened. | `vitest run src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts` |
| P0-012 | Partially implemented | Added active Playwright golden-path coverage under `frontend/web/e2e/end-to-end-product-correctness.spec.ts` for login, workspace selection, project selection, Generate backend action, and Run workflow action with deterministic API route mocks. Canvas/page-designer save/reload/preview persistence still needs a live backend-backed Playwright pass. | `playwright test e2e/end-to-end-product-correctness.spec.ts --list` |

## Remaining Backlog

P1, P2, and P3 items from the audit remain open unless explicitly mentioned below. They should be implemented as follow-up slices with focused acceptance tests.

| ID | Status | Implementation evidence | Verification |
| --- | --- | --- | --- |
| P1-002 | Partially implemented | Added a canonical phase cockpit contract builder and exposed it from `usePhaseCockpitData`. The contract separates persisted project/activity, derived preview/evidence/governance/blockers, suggested actions, and review-required state without forcing a broad UI rewrite. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts` |
| P1-003 | Partially implemented | Replaced the two-item `nextActionHints` display path with a ranked next-best-action service that combines backed project guidance, lifecycle blockers, gate readiness, prediction confidence, project health, and user role into typed action details with source, risk, review-required, and safe-to-run metadata. A dedicated backend endpoint and one-click execution contract remain open. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts` |
| P1-004 | Partially implemented | Added a canonical mounted phase adapter for Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve and moved phase next-best-action labels/order through it while preserving compatibility for legacy lifecycle callers. Broad frontend/backend/API enum consolidation remains open. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts` |
| P1-005 | Partially implemented | Replaced reachable project-shell, lifecycle, canvas empty-state, command-palette, assistant modal, and canvas overlay labels from user-facing “AI” wording to outcome-oriented “Guided Assistant,” “Guided generation,” and “recommended insights” language while preserving internal API/hook names. Broader product-wide copy and OpenAPI wording remains open. | Narrowed `rg` confirms remaining matches in this touched surface are comments/doc tags only. |
| P1-006 | Partially implemented | Replaced the `LazyAIPanel`, `LazyAIExplainability`, and `LazyBulkOperationsDialog` `null` placeholders with an explicit `LazyFeatureUnavailable` state that explains feature-gated/unavailable surfaces instead of silently rendering nothing. Real feature implementations remain open. | `vitest run src/components/route/__tests__/route-components.test.tsx` |
| P1-007 | Partially implemented | Added bounded page artifact operation records and wired PageDesignerNode document edits, persistence success/failure/conflict, remote reload, overwrite, imported pages, and governance lineage through the artifact operation log. Full shared canvas-wide operation log and backend replay/export remain open. | `vitest run src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/pageArtifactDocument.test.ts` |
| P1-008 | Partially implemented | Replaced the conflict “Force Save” action with an explicit reload-or-overwrite review flow. Overwrite now requires a user-entered audit reason and records that reason in the page artifact operation log. Full local-vs-remote diff/merge UI remains open. | `vitest run src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/pageArtifactDocument.test.ts` |
| P1-009 | Partially implemented | PropertyForm now enforces component contract governance metadata by blocking apply when required accessibility props are missing and requiring explicit review acknowledgement before review-required prop changes can be applied. Broader inspector policy enforcement for variants, bindings, privacy, and telemetry consent remains open. | `vitest run src/components/canvas/page/__tests__/PropertyForm.test.tsx` |
| P1-010 | Partially implemented | Tokenized the page renderer’s unknown-component fallback and before/after/slot drop-zone styling, and fixed the renderer drag-event type mismatch. Broader dashboard/project-route raw controls and hardcoded utility token cleanup remains open. | Narrowed typecheck grep for `ComponentRenderer` is clean. |
| P1-011 | Partially implemented | Removed the legacy/demo `SimplePageDesigner` export from the production page-builder barrel so production imports resolve to `PageDesigner`. The standalone demo component still exists and should be moved to a demo/test-only folder or deleted in a follow-up. | Narrowed `rg` shows only the production boundary comment and standalone demo component remain. |
| P1-012 | Partially implemented | Page builder nodes now expose keyboard-accessible movement shortcuts (`Alt+ArrowUp`, `Alt+ArrowDown`, `Alt+ArrowLeft`) and invalid drop attempts surface an `aria-live` feedback message instead of silently returning. Full keyboard slot nesting/announced screen-reader workflow coverage remains open. | `vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx` |
| P1-013 | Partially implemented | Extended the inspector governance controls so telemetry-emitting component contracts require explicit telemetry consent acknowledgement before changes can be applied, alongside required a11y and review-required prop enforcement. Responsive variants, state variants, data/action bindings, and full privacy classification editing remain open. | `vitest run src/components/canvas/page/__tests__/PropertyForm.test.tsx` |
| P1-014 | Partially implemented | Expanded plugin loader runtime policy tests to prove guarded renderer execution exposes the constrained runtime environment, blocks disallowed global fetch calls, and restores global fetch/current environment after execution. Broader telemetry restriction and fallback rendering integration tests remain open. | `vitest run src/components/canvas/page/__tests__/pluginLoader.test.ts` |
| P1-015 | Partially implemented | Unknown components now render as reviewable residual islands with residual reason, source location, confidence, suggested registry contract, and explicit remediation guidance when metadata is available. Persisted residual review actions remain open. | `vitest run src/components/canvas/page/__tests__/rendererManifest.test.tsx` |
| P1-016 | Partially implemented | Live preview selectors now send typed `SET_VIEWPORT`, `SET_THEME`, and `SET_LOCALE` messages to the iframe runtime. The preview runtime applies the environment to viewport sizing, theme contrast styling, locale, and text direction so builder controls and preview rendering stay synchronized. Broader design-token theme packs and localized component fixtures remain open. | `vitest run src/routes/__tests__/preview-builder.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx` |
| P1-017 | Partially implemented | Preview runtime session validation is no longer bypassed in tests, `READY` is emitted only after a valid server-issued session, and host messages now validate document, sandbox, viewport, theme, and locale payloads before mutating runtime state. Invalid sessions, spoofed origins, and malformed mount documents are covered by policy tests. Server/static response-header rollout remains open for the frontend `/preview/builder` document itself. | `vitest run src/routes/__tests__/preview-builder.test.tsx src/routes/__tests__/preview-builder-security.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx` |
| P1-018 | Partially implemented | Preview hover now reaches the mounted page designer through a non-destructive `externalHoveredNodeId` channel, and page components render a dashed preview-hover outline without changing selection/inspector state. Designer hover changes are emitted for sync telemetry, and PageDesignerNode wires preview hover callbacks into the designer. Full outline/layers panel hover synchronization remains open. | `vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx` |
| P1-019 | Partially implemented | The mounted import panel now presents a guided workflow with explicit Semantic model and Governed source paths, source type selection, source locator input, review-step copy, and no need for users to memorize `source:<type>:<locator>` command syntax. Legacy command parsing remains for compatibility. Full confidence/residual diff review queues and apply/skip decisions remain open. | `vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx` |
| P1-020 | Partially implemented | Page artifact documents now carry a compact `artifactGraph` snapshot for imported/decompiled artifacts, including graph id, project/source scope, page/component/source/residual nodes, derived/contains/references/residual edges, source locations, compiler provenance, confidence, and residual island ids. Full backend graph ingestion/version persistence and graph-wide merge review remain open. | `vitest run src/components/canvas/page/__tests__/artifactCompilerBridge.test.ts src/services/compiler/__tests__/ImportSourceWorkflow.test.ts src/components/canvas/page/__tests__/pageArtifactDocument.test.ts` |
| P1-021 | Partially implemented | PageDesigner review decisions for pending automation changes now emit back to PageDesignerNode, update the matching `PageArtifactDocument.aiChangeRecords` lineage review state, mark the artifact dirty for persistence, and append a governance operation record with action id and decision metadata. Backend review-decision API/audit parity and rollback-on-reject remain open. | `vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/pageArtifactDocument.test.ts` |
| P1-022 | Partially implemented | Preview session issue/validation controller operations now support injected audit logging and emit scoped audit events with type, outcome, actor, tenant, project, artifact, timestamp, and operation metadata. Validation responses now use a null-safe response body so valid sessions do not fail when reason is null. Full audit/OTel parity across lifecycle, generation, run, import, registry, and all preview endpoints remains open. | `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.api.PreviewSessionApiControllerTest` |

Recommended next slices:

1. Add backend contracts and mounted UI for Generate apply, reject, and rollback review decisions.
2. Add backend contracts and mounted UI for Run rollback, promote, and post-run observation handoff.
3. Replace textarea source import with a governed backend import job and wizard.
4. Persist builder command audit and telemetry with actor, scope, artifact, operation, outcome, and correlation ID.
5. Extend the Playwright golden path to cover page designer preview, save, reload, validation, and generated artifact persistence against a live test backend.

## Verification Notes

Passing targeted checks:

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run \
  src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts \
  src/components/canvas/page/__tests__/PageDesigner.test.tsx \
  src/components/canvas/page/__tests__/artifactCompilerBridge.test.ts \
  src/components/canvas/page/__tests__/pageArtifactDocument.test.ts \
  src/components/canvas/page/__tests__/PropertyForm.test.tsx \
  src/components/canvas/page/__tests__/pluginLoader.test.ts \
  src/components/canvas/page/__tests__/rendererManifest.test.tsx \
  src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx \
  src/components/studio/__tests__/LivePreviewPanel.test.tsx \
  src/services/compiler/__tests__/ImportSourceWorkflow.test.ts \
  src/services/phase/__tests__/PhaseBuilders.test.ts \
  src/routes/__tests__/preview-builder.test.tsx \
  src/routes/__tests__/preview-builder-security.test.tsx \
  src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx \
  src/components/route/__tests__/route-components.test.tsx
```

Result: 15 test files passed, 157 tests passed.

Additional narrowed typecheck check:

```text
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "ImportSourceWorkflow|PageDesigner.tsx|PhaseGovernanceTrace" -C 2
```

Result: no remaining typecheck matches in the governed import, page designer, or governance trace files touched by this pass.

```text
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "NextActionRankingService|PhaseSuggestionBuilder|usePhaseCockpitData|PhaseBuilders" -C 2
```

Result: no typecheck matches in the next-best-action service files touched by P1-003.

```text
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "CanonicalPhaseService|NextActionRankingService|PhaseSuggestionBuilder|PhaseBuilders" -C 2
```

Result: no typecheck matches in the canonical phase adapter files touched by P1-004.

```text
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "_shell.tsx|lifecycle.tsx|ImprovedEmptyState|CommandPalette|AIAssistantModal|CanvasAIOverlay" -C 2
```

Result: no typecheck matches in the user-facing wording files touched by P1-005.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/route/__tests__/route-components.test.tsx
```

Result: 1 test file passed, 20 tests passed.

```text
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "lazyRoutes|LazyFeatureUnavailable|route-components" -C 2
```

Result: no typecheck matches in the lazy placeholder files touched by P1-006.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run \
  src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx \
  src/components/canvas/page/__tests__/pageArtifactDocument.test.ts
```

Result: 2 test files passed, 27 tests passed.

```text
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesignerNode|pageArtifactDocument" -C 2
```

Result: no typecheck matches in the operation-log files touched by P1-007.

P1-008 uses the same PageDesignerNode/page artifact document verification above. The conflict overwrite test now asserts the overwrite action is disabled until an audit reason is entered.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PropertyForm.test.tsx
```

Result: 1 test file passed, 2 tests passed.

```text
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PropertyForm" -C 2
```

Result: no typecheck matches in the property governance files touched by P1-009.

```text
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "ComponentRenderer" -C 2
```

Result: no typecheck matches in the renderer design-system slice touched by P1-010.

```text
rg "SimplePageDesigner" products/yappc/frontend/web/src -n
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "SimplePageDesigner|components/canvas/page/index" -C 2
```

Result: `SimplePageDesigner` is no longer exported from the production page-builder barrel; no narrowed typecheck matches for the quarantine slice touched by P1-011.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|ComponentRenderer" -C 2
```

Result: 1 test file passed, 23 tests passed; no narrowed typecheck matches in the keyboard/drop-feedback files touched by P1-012.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PropertyForm.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PropertyForm" -C 2
```

Result: 1 test file passed, 2 tests passed; no narrowed typecheck matches in the telemetry consent inspector files touched by P1-013.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/pluginLoader.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "pluginLoader" -C 2
```

Result: 1 test file passed, 19 tests passed; no narrowed typecheck matches in the plugin runtime policy files touched by P1-014.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/rendererManifest.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "rendererManifest" -C 2
```

Result: 1 test file passed, 1 test passed; no narrowed typecheck matches in the residual fallback renderer files touched by P1-015.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/preview-builder.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "LivePreviewPanel|preview-builder|preview-builder.test|LivePreviewPanel.test" -C 2
```

Result: 2 test files passed, 14 tests passed; no narrowed typecheck matches in the live preview runtime/panel files touched by P1-016.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/preview-builder.test.tsx src/routes/__tests__/preview-builder-security.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "preview-builder|LivePreviewPanel|PreviewSessionApi" -C 2
```

Result: 3 test files passed, 18 tests passed; no narrowed typecheck matches in the preview security files touched by P1-017.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|ComponentRenderer|PageDesignerNode|LivePreviewPanel|PageDesigner.test" -C 2
```

Result: 3 test files passed, 47 tests passed; no narrowed typecheck matches in the preview selection/hover sync files touched by P1-018.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|PageDesigner.test" -C 2
```

Result: 1 test file passed, 26 tests passed; no narrowed typecheck matches in the guided import workflow files touched by P1-019.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/artifactCompilerBridge.test.ts src/services/compiler/__tests__/ImportSourceWorkflow.test.ts src/components/canvas/page/__tests__/pageArtifactDocument.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "artifactCompilerBridge|pageArtifactDocument|ImportSourceWorkflow|artifactCompilerBridge.test" -C 2
```

Result: 3 test files passed, 30 tests passed; no narrowed typecheck matches in the artifact graph snapshot files touched by P1-020.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/pageArtifactDocument.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|PageDesignerNode|pageArtifactDocument|PageDesignerNode.test|PageDesigner.test" -C 2
```

Result: 3 test files passed, 54 tests passed; no narrowed typecheck matches in the persisted governance review files touched by P1-021.

```text
./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.api.PreviewSessionApiControllerTest
```

Result: 1 Java test class passed, 2 tests passed. An initial `./gradlew -p products/yappc/core/yappc-services ...` invocation failed because the monorepo has duplicate project-directory aliases for that module; the root project-path invocation above is the working verification command.

Known broader gate failure:

```text
pnpm --filter @ghatana/yappc-web-app typecheck
```

Result: fails on pre-existing broad workspace errors outside this implementation slice, including `libs/yappc-core` API typings, dashboard/design-system prop mismatches, inspector/plugin service typing, and missing `jspdf` / `html2canvas` type resolution.
