# YAPPC End-to-End Product Correctness Tracker

Source audit: `products/yappc/docs/audits/end-to-end-product-correctness-audit.md`

Last updated: 2026-05-07

## Current Implementation Pass

This pass focuses on the production-blocking persistence and API drift items that can be safely changed in the mounted frontend without rewriting the entire YAPPC product surface in one change.

| ID | Status | Implementation evidence | Verification |
| --- | --- | --- | --- |
| P0-001 | Partially implemented | Page artifact HTTP persistence now sends tenant/workspace/project scope, credentials, `If-Match`, and distinct 401/403/409/422 errors. Conflict errors are not hidden by local fallback. Sensitive local drafts are blocked by policy. | `vitest run src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts` |
| P0-002 | Partially implemented | Mounted phase cockpit data now uses canonical `yappcApi` methods for project, activity, and next-phase preview instead of raw `fetch`. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts` |
| P0-003 | Partially implemented | Canonical `projects.list` now requires a `workspaceId` query parameter and returns the owned/included list contract. | Covered by type surface; call-site migration remains open where legacy raw workspace hooks are still used. |
| P0-004 | Partially implemented | Primary CTAs now respect adaptive locking and Generate/Run CTAs call typed backend operations instead of only emitting local feedback. Remaining phase CTAs still need backend-owned promote/reject/audit operations. | `vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx` |
| P0-005 | Partially implemented | Mounted cockpit uses `getAdaptivePhaseCockpitConfig` at render time for blocker, gate, tier, role, and feature-flag locking. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts` |
| P0-006 | Implemented for mounted Generate review decisions | Generate phase starts `/api/v1/yappc/generate` through `yappcApi`, requests `/api/v1/yappc/generate/diff`, surfaces the backend run id for review, and now exposes typed apply/reject/rollback review decisions through `/api/v1/yappc/generate/runs/{runId}/apply`, `/reject`, and `/rollback`. Java generation review decisions require project and actor scope, return auditable decision metadata, record service audit/metrics, and are registered in both lifecycle and HTTP server routers. The mounted cockpit renders all three review actions and the phase action service posts actor-scoped review payloads through the canonical client. | `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.api.GenerationApiControllerTest`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts`; `pnpm --filter @ghatana/yappc-api-app exec vitest run src/__tests__/openapi-contract.test.ts` |
| P0-007 | Implemented for mounted Run post-actions | Run phase starts `/api/v1/workflows/yappc-run/start` with authenticated tenant scope, checks `/api/v1/workflows/{runId}/status`, and surfaces the backend run id/status. The mounted cockpit now exposes post-run rollback, promote, and Observe handoff controls after a run starts. Typed frontend Run client methods post deployment-scoped rollback/promote bodies to `/api/v1/yappc/run/rollback` and `/api/v1/yappc/run/promote`; Observe handoff navigates to the mounted Observe cockpit with the run context preserved in the result message. OpenAPI now matches the Java Run controller's deployment-scoped rollback/promote contract and explicit run-with-observation envelope. | `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.api.RunApiControllerTest`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts`; `pnpm --filter @ghatana/yappc-api-app exec vitest run src/__tests__/openapi-contract.test.ts` |
| P0-008 | Implemented for governed source import job and guided wizard | Source import no longer uses the hardcoded `imported-project`; source imports require active project, tenant, and workspace context. Browser source imports require governed server orchestration through `/api/v1/yappc/artifact/import-source` with tenant/workspace/project headers, max source locator validation, trusted HTTPS/artifact locator enforcement, review-required job metadata, and no local fallback when governed import is unavailable. The PageDesigner import textarea now accepts semantic JSON only; legacy `source:<type>:<locator>` command syntax is rejected with guidance to use the Governed source wizard. Full resumable multi-step import job progress/polling remains open. | `pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/source-imports.test.ts src/__tests__/openapi-contract.test.ts`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/services/compiler/__tests__/ImportSourceWorkflow.test.ts` |
| P0-009 | Existing coverage retained | Imported multi-page artifacts continue to create additional page designer nodes instead of collapsing all pages into one root. | Existing PageDesignerNode behavior retained; deeper artifact graph persistence remains open. |
| P0-010 | Partially implemented | Page builder command audit hooks now emit scoped `/api/audit/events` records with actor, tenant, workspace, project, artifact, operation, outcome, changed node ids, document ids, and validation count. Audit persistence failures are surfaced in the builder instead of being fully silent. Broader telemetry/correlation coverage remains open. | `vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx` |
| P0-011 | Partially implemented | Local page artifact fallback now blocks `SENSITIVE`, `CREDENTIALS`, and `REGULATED` classifications unless policy is explicitly widened. | `vitest run src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts` |
| P0-012 | Implemented for live page-designer save/preview/conflict/reload contract | Added active Playwright golden-path coverage under `frontend/web/e2e/end-to-end-product-correctness.spec.ts` for login, workspace selection, project selection, Generate backend action, and Run workflow action with deterministic API route mocks. Added `frontend/web/e2e/page-designer-live-backend.spec.ts` to mount the real canvas page designer with a dev-only, project-scoped seed hook and exercise live preview, semantic import, real adapter scope headers, save, 409 conflict surfacing, and reload through an in-page page-artifact backend mock at the adapter fetch boundary. The route-host publish/autosave handoff is now covered by the live Chromium run. | `playwright test e2e/end-to-end-product-correctness.spec.ts --list`; `pnpm --filter @ghatana/yappc-web-app exec playwright test e2e/page-designer-live-backend.spec.ts --list`; `E2E_BASE_URL=http://localhost:7003 pnpm --filter @ghatana/yappc-web-app exec playwright test e2e/page-designer-live-backend.spec.ts --project=chromium --timeout=90000` |

## Remaining Backlog

P1, P2, and P3 items from the audit remain open unless explicitly mentioned below. They should be implemented as follow-up slices with focused acceptance tests.

| ID | Status | Implementation evidence | Verification |
| --- | --- | --- | --- |
| P1-001 | Implemented for mounted project navigation | The mounted project shell exposes only canonical phase navigation under `/p/:projectId/{intent,shape,validate,generate,run,observe,learn,evolve}` and no longer presents legacy Canvas/Preview/Deploy/Lifecycle tabs as first-class navigation. Legacy `/p/:projectId/canvas`, `/preview`, `/deploy`, and `/lifecycle` modules are retained for bookmark/deep-link compatibility and now render an explicit compatibility notice that points users to the corresponding canonical phase cockpit. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/preview.test.tsx src/routes/app/project/__tests__/deploy.test.tsx src/routes/app/project/__tests__/lifecycle.test.tsx src/routes/app/project/__tests__/shell.test.tsx` |
| P1-002 | Implemented for mounted cockpit contract visibility | Added a canonical phase cockpit contract builder and exposed it from `usePhaseCockpitData`. The contract separates persisted project/activity, derived preview/evidence/governance/blockers, suggested actions, and review-required state without forcing a broad UI rewrite. The mounted phase cockpit now renders those lanes explicitly in a canonical contract summary so persisted, derived, suggested, and review states are visible and testable in each phase route. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts` |
| P1-003 | Implemented for safe dashboard action execution | Replaced the two-item `nextActionHints` display path with a ranked next-best-action service that combines backed project guidance, lifecycle blockers, gate readiness, prediction confidence, project health, and user role into typed action details with source, risk, review-required, and safe-to-run metadata. The dashboard now has a dedicated backend-backed action summary endpoint and a one-click safe-action execution contract at `/api/projects/{projectId}/dashboard-actions/execute`; safe actions record a lifecycle audit event and return a canonical phase target, while review/blocker actions are explicitly rejected from one-click execution. Broader phase-cockpit one-click execution remains open for non-dashboard suggestions. | `vitest run src/services/phase/__tests__/PhaseBuilders.test.ts`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx`; `pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/projects.audit.test.ts src/__tests__/openapi-contract.test.ts` |
| P1-004 | Implemented for dashboard/API lifecycle boundaries | Added a canonical mounted phase adapter for Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve and moved phase next-best-action labels/order through it while preserving compatibility for legacy lifecycle callers. Dashboard resume actions now use the canonical adapter instead of a local enum guard, so legacy backend phases like `CONTEXT`, `PLAN`, `EXECUTE`, `VERIFY`, and `INSTITUTIONALIZE` route to mounted `shape`, `validate`, `generate`, `run`, and `evolve` cockpits instead of falling back to Intent. The project API route now has typed lifecycle compatibility aliases, maps `EVOLVE` to persisted `INSTITUTIONALIZE`, returns typed mounted dashboard route phases, and the OpenAPI `ProjectLifecyclePhase` schema explicitly documents mounted phases plus legacy aliases. Remaining lifecycle route internals outside the project/dashboard boundary can be consolidated opportunistically. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts`; `pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/projects.audit.test.ts src/__tests__/openapi-contract.test.ts` |
| P1-005 | Implemented for public/reachable wording | Replaced reachable project-shell, lifecycle, canvas empty-state, command-palette, assistant modal, canvas overlay, project layout, shortcut, command registry, generated client documentation, unified right panel, and assistant panel labels from user-facing “AI” wording to outcome-oriented “Guided Assistant,” “Guided generation,” “recommended insights,” and “recommendations” language while preserving internal API/hook/model provenance names. OpenAPI public tags, summaries, and descriptions now describe recommendations, guided enrichment, and automation cost instead of front-loading “AI suggestions.” Remaining matches are comments/internal client names, provenance fields, or domain/template examples such as AI/ML. | `pnpm --filter @ghatana/yappc-api-app exec vitest run src/__tests__/openapi-contract.test.ts`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/command/__tests__/CommandPalette.test.tsx`; narrowed wording `rg`; narrowed web/API typecheck greps |
| P1-006 | Partially implemented | Replaced the `LazyAIPanel`, `LazyAIExplainability`, and `LazyBulkOperationsDialog` `null` placeholders with an explicit `LazyFeatureUnavailable` state that explains feature-gated/unavailable surfaces instead of silently rendering nothing. The guided canvas panel lazy export now loads the real `AIPanel` implementation via the `LazyAIPanel`/`LazyGuidedPanel` compatibility exports instead of showing an unavailable placeholder. Real explainability and bulk-operation implementations remain open until their review/rollback contracts are available. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/route/__tests__/route-components.test.tsx`; narrowed lazy-route typecheck grep |
| P1-007 | Partially implemented | Added bounded page artifact operation records and wired PageDesignerNode document edits, persistence success/failure/conflict, remote reload, overwrite, imported pages, and governance lineage through the artifact operation log. Page artifact documents now expose a deterministic `createPageArtifactOperationLogExport` snapshot with schema version, replay cursor, sorted records, operation/status counts, and latest operation timestamp so canvas persistence/audit/replay work can consume one typed log contract. Backend replay/export endpoint and broader canvas undo/redo ingestion remain open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/pageArtifactDocument.test.ts src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx`; narrowed operation-log typecheck grep |
| P1-008 | Partially implemented | Replaced the conflict “Force Save” action with an explicit compare/reload/overwrite review flow. Conflict UI now loads and displays a remote comparison summary before users choose to reload or overwrite. Reload records discarded local and remote document ids plus local/remote node counts; overwrite still requires a user-entered audit reason and now records local/remote document ids and node counts in the page artifact operation log. Full node-level merge UI remains open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/pageArtifactDocument.test.ts`; narrowed conflict-flow typecheck grep |
| P1-009 | Partially implemented | PropertyForm now enforces component contract governance metadata by blocking apply when required accessibility props are missing, requiring explicit review acknowledgement before review-required prop changes can be applied, requiring telemetry consent for telemetry-emitting contracts, and requiring privacy-classification acknowledgement for privacy-sensitive contracts before any property update is submitted. Broader inspector policy enforcement for responsive/state variants and data/action bindings remains open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PropertyForm.test.tsx`; narrowed PropertyForm typecheck grep |
| P1-010 | Partially implemented | Tokenized the page renderer’s unknown-component fallback and before/after/slot drop-zone styling, and fixed the renderer drag-event type mismatch. The mounted dashboard's primary create CTA, section navigation controls, project/workspace cards, and backed action cards now use the design-system `Button` component instead of raw route-level `<button>` controls; `rg "<button"` is clean for `dashboard.tsx`. Broader project-route primitives and hardcoded utility token cleanup remain open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx`; narrowed dashboard typecheck grep; `rg "<button" products/yappc/frontend/web/src/routes/dashboard.tsx` |
| P1-011 | Implemented | Removed the legacy/demo `SimplePageDesigner` export from the production page-builder barrel and deleted the standalone demo component so production imports resolve to the canonical `PageDesigner` only. The frontend source tree no longer contains `SimplePageDesigner`. | `rg -n "SimplePageDesigner" products/yappc/frontend/web/src -S`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx`; narrowed page-builder typecheck grep |
| P1-012 | Partially implemented | Page builder nodes now expose keyboard-accessible movement shortcuts (`Alt+ArrowUp`, `Alt+ArrowDown`, `Alt+ArrowLeft`, `Alt+ArrowRight`) and invalid drop attempts surface an `aria-live` feedback message instead of silently returning. `Alt+ArrowRight` moves the focused component into the previous container's default slot when available, and announces a clear reason when no previous container target exists. Broader keyboard-only Playwright and screen-reader coverage remains open under P2 accessibility work. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx`; narrowed keyboard/drop typecheck grep |
| P1-013 | Partially implemented | Extended the inspector governance controls so telemetry-emitting component contracts require explicit telemetry consent acknowledgement before changes can be applied, alongside required a11y, review-required prop, and privacy-classification enforcement. The inspector now edits responsive variants, state variants, data bindings, and action bindings and routes them through PageBuilderCommands; matching binding updates replace existing same-target bindings instead of appending duplicates. Rich contract-specific binding pickers and broader privacy classification editing remain open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PropertyForm.test.tsx src/components/canvas/page/__tests__/PageDesigner.test.tsx src/services/canvas/commands/__tests__/PageBuilderCommands.test.ts`; narrowed inspector typecheck grep |
| P1-014 | Implemented for loader runtime guard coverage | Expanded plugin loader runtime policy tests to prove guarded renderer execution exposes the constrained runtime environment, blocks disallowed global fetch calls, and restores global fetch/current environment after execution. Loader-created plugin telemetry is now deny-by-default unless an allowlisted elevated package declares exact event names; declared events run with deterministic sampling and undeclared events remain blocked. Package unload now has fallback-renderer integration coverage so unloaded plugin contracts surface as reviewable residual islands. Broader end-to-end marketplace/package signing coverage remains open outside this loader boundary. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/pluginLoader.test.ts src/services/plugins/__tests__/PluginRuntimePolicy.test.ts src/components/canvas/page/__tests__/rendererManifest.test.tsx`; narrowed plugin-loader typecheck grep |
| P1-015 | Implemented for residual island review persistence | Unknown components now render as reviewable residual islands with residual reason, source location, confidence, suggested registry contract, and explicit remediation guidance when metadata is available. Imported residual islands now expose accept/reject actions in PageDesigner; each action posts a cookie-authenticated review decision to the artifact backend and only clears the island from the pending list after the server returns a valid audit response. Command-level residual review metadata/undo coverage remains in place. Broader graph-wide residual merge/retry workflows remain open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/page/__tests__/rendererManifest.test.tsx src/services/canvas/commands/__tests__/PageBuilderCommands.test.ts src/services/canvas/commands/__tests__/ResidualIslandReviewService.test.ts`; narrowed residual-review typecheck grep |
| P1-016 | Implemented for preview environment variants | Live preview selectors now send typed `SET_VIEWPORT`, `SET_THEME`, and `SET_LOCALE` messages to the iframe runtime. The preview runtime applies the environment to viewport sizing, validated token theme packs (`default`, `contrast`, `editorial`), locale, and text direction so builder controls and preview rendering stay synchronized. Host controls now expose RTL locale fixtures (`ar-SA`, `he-IL`) alongside English locales, and the runtime rejects unknown theme names instead of applying arbitrary strings. Broader localized component/content fixture libraries remain open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/preview-builder.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx`; narrowed preview environment typecheck grep |
| P1-017 | Implemented for preview runtime response headers | Preview runtime session validation is no longer bypassed in tests, `READY` is emitted only after a valid server-issued session, and host messages now validate document, sandbox, viewport, theme, and locale payloads before mutating runtime state. Invalid sessions, spoofed origins, malformed mount documents, and the `/preview/builder` document response-header contract are covered by policy tests. The route now exports strict same-origin preview headers: CSP without `unsafe-eval`, `frame-ancestors 'self'`, `X-Frame-Options: SAMEORIGIN`, `nosniff`, `no-referrer`, same-origin resource policy, and restrictive permissions policy. Broader deployment-edge header verification remains open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/preview-builder.test.tsx src/routes/__tests__/preview-builder-security.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx`; narrowed preview security typecheck grep |
| P1-018 | Implemented for preview hover/layers synchronization | Preview hover now reaches the mounted page designer through a non-destructive `externalHoveredNodeId` channel, and page components render a dashed preview-hover outline without changing selection/inspector state. Designer hover changes are emitted for sync telemetry, and PageDesignerNode wires preview hover callbacks into the designer. The shared preview sync service now maps preview IDs to canvas IDs by default when no explicit mapping is required, and the unified left rail/layers panel accepts externally hovered node ids so preview-hovered layers render a synced highlight. Broader cross-surface outline minimap polish remains open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/services/canvas/preview/__tests__/BidirectionalPreviewSync.test.ts src/components/canvas/unified/__tests__/UnifiedLeftRail.test.tsx src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx`; narrowed preview hover/layers typecheck grep |
| P1-019 | Implemented for mounted import review queue | The mounted import panel now presents a guided workflow with explicit Semantic model and Governed source paths, source type selection, source locator input, review-step copy, and no need for users to memorize `source:<type>:<locator>` command syntax. Imported round-trip fidelity loss points and residual islands now create an explicit review queue with visible progress and apply/skip decisions, while residual accept/reject decisions synchronize back into the queue. Legacy command parsing remains for compatibility. Backend persistence of the import review queue and richer side-by-side diff review remain open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/services/compiler/__tests__/ImportSourceWorkflow.test.ts src/components/canvas/page/__tests__/artifactCompilerBridge.test.ts`; narrowed import review typecheck grep |
| P1-020 | Implemented for page artifact graph ingest handoff | Page artifact documents now carry a compact `artifactGraph` snapshot for imported/decompiled artifacts, including graph id, project/source scope, page/component/source/residual nodes, derived/contains/references/residual edges, source locations, compiler provenance, confidence, and residual island ids. HTTP page artifact saves now transform that snapshot into the backend artifact graph ingest DTO and post it to `/api/v1/yappc/artifact/graph/ingest` with tenant/workspace/project headers after the page document save succeeds. Graph ingest failures are surfaced as non-fallback persistence errors so local draft fallback cannot mask missing graph/version persistence. Graph-wide merge review remains open. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts src/components/canvas/page/__tests__/pageArtifactDocument.test.ts src/components/canvas/page/__tests__/artifactCompilerBridge.test.ts`; narrowed artifact graph persistence typecheck grep |
| P1-021 | Implemented for persisted governance review decisions | PageDesigner review decisions for pending automation changes now emit back to PageDesignerNode, update the matching `PageArtifactDocument.aiChangeRecords` lineage review state, mark the artifact dirty for persistence, and append a governance operation record with action id and decision metadata. The page artifact backend now exposes `POST /api/v1/page-artifacts/:artifactId/review-decisions`, enforces edit authorization and tenant/workspace/project scope, updates the matching persisted governance lineage review state through the atomic save+audit path, emits metrics/logs, and returns actor, timestamp, evidence, confidence, changed nodes, and reversible metadata. Rollback-on-reject remains open. | `./gradlew :products:yappc:core:yappc-domain-impl:test --tests com.ghatana.yappc.domain.pageartifact.http.PageArtifactControllerTest`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/pageArtifactDocument.test.ts` |
| P1-022 | Partially implemented | Preview session issue/validation controller operations now support injected audit logging and emit scoped audit events with type, outcome, actor, tenant, project, artifact, timestamp, and operation metadata. Validation responses now use a null-safe response body so valid sessions do not fail when reason is null. Governed source imports now emit structured `YAPPC_SOURCE_IMPORT` audit events for review-required, rejected, and failed jobs with actor, role, tenant, workspace, project, source type, source locator, outcome/reason, status, job id, component name, and size metadata; import responses expose `job.auditRecorded` so audit-write failures are visible. Full audit/OTel parity across lifecycle, generation, run, registry, and all preview endpoints remains open. | `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.api.PreviewSessionApiControllerTest`; `pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/source-imports.test.ts src/__tests__/openapi-contract.test.ts`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/services/compiler/__tests__/ImportSourceWorkflow.test.ts`; narrowed source-import typecheck grep |
| P1-023 | Implemented for backend-scoped workspace/admin capability gates | Workspace and project list/detail responses now include server-derived role, ownership, read-only, and capability metadata. Frontend workspace state normalizes those fields without defaulting missing metadata to owner; shell workspace/project ownership, workflow capability gating, guidance role ranking, and admin `useCapabilityGate` now consume backend role/capability state instead of hard-coded ownership. Enabled admin capabilities now require the current workspace's backend-derived OWNER/ADMIN role plus update capability; a global auth role can no longer override an EDITOR/VIEWER workspace role. Full mutation-surface disablement for every included-project action continues in P1-024. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/hooks/__tests__/useCapabilityGate.test.ts src/services/workspace/__tests__/accessControl.test.ts src/routes/app/admin/__tests__/billing-teams-gate.test.tsx`; narrowed admin capability typecheck grep; `vitest run src/routes/__tests__/workspaces.audit.test.ts src/routes/__tests__/projects.audit.test.ts` |
| P1-024 | Implemented for included-project cockpit and dashboard execution guards | Mounted phase cockpit project snapshots now preserve backend project access fields and derive the adaptive phase role from `readOnly`/`capabilities.update`. Generate/Run primary actions are locked for included read-only projects before backend mutation calls can fire, while mutable project fixtures must explicitly carry server capabilities. Backend dashboard safe-action execution now also rejects included projects with `included_project_read_only` before lifecycle activity/audit records can be written, so server-side one-click actions cannot mutate read-only included project context. Broader canvas inspector/page-builder mutation controls for every included-project path are covered under P2-001 and remain subject to ongoing hardening as new surfaces are added. | `pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/projects.audit.test.ts src/__tests__/openapi-contract.test.ts`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/workspace/__tests__/accessControl.test.ts src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx`; narrowed dashboard execution typecheck grep |
| P1-025 | Implemented for backed dashboard action cards | Dashboard next actions no longer synthesize local recommendations from `NextBestActionService`. They now use server-provided project `aiNextActions` when present and otherwise derive a safe resume action from a normalized lifecycle phase; both paths navigate to mounted phase-specific project routes. Workspace resume also honors the current workspace's included projects instead of filtering only by owner workspace. Workspace/project API payloads normalize missing lifecycle, review action, health, type, status, and timestamp fields at the boundary. A dedicated `/api/projects/dashboard-actions` contract now returns backend-classified `blockedWork`, `reviewRequired`, and `safeToContinue` actions for the current workspace, the canonical web API client exposes it, and the dashboard renders separate backed cards that deep-link to the relevant phase cockpit. Safe continuation card clicks execute through the backend before navigation. Onboarding status rejects malformed server contracts and falls back to localStorage rather than silently marking users incomplete. Broader dashboard UX polish remains open under P3-003. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx`; `pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/projects.audit.test.ts src/__tests__/openapi-contract.test.ts` |
| P2-001 | Implemented for mounted/legacy canvas and embedded page builder | Mounted and legacy canvas routes now derive a backend-aware `CanvasAccessPolicy` from project role/capabilities plus normalized lifecycle phase. Design phases permit canvas/artifact mutations only with update capability; Validate/Generate/Run/Observe-style review phases default to read-only surfaces. Mounted canvas forwards included-project access metadata into the provider, left-panel create palettes are replaced by read-only guidance when locked, quick-create/context-menu edit actions are hidden when unsafe, inspector edits/blockers/comments respect capability gates, and mutation handlers/drag-drop/keyboard move/delete/paste/direct-drag paths announce and return before mutating when locked. Legacy unified canvas reuses the same policy for ReactFlow mutation filters, toolbar undo/redo/tools, left-rail inserts/updates/deletes, right-panel updates, context-menu duplicate/delete/layering, drawing, connection, and shortcut paste/delete/group/layer flows. Embedded page builder nodes now receive policy read-only metadata from both canvas routes; `PageDesigner` blocks palette insert, import/decompile, drops, keyboard moves, delete, nest, property apply, and governance review decisions when locked, while `PageDesignerNode` blocks document persistence, imported page node creation, governance record writes, conflict reload/overwrite, and wrapper document updates. | `vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/page/__tests__/PropertyForm.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/__tests__/canvasAccessPolicy.test.ts src/routes/app/project/canvas/__tests__/CanvasRoute.access.test.tsx src/routes/app/project/_canvas/__tests__/useCanvasKeyboardShortcuts.test.tsx` |
| P2-002 | Implemented for shared canvas sync status contract | Canvas sync status now flows through a shared `CanvasSyncStatus` alias backed by the generated `SaveSyncStatusContract`, with centralized labels, legacy status normalization (`synced` → `remote-saved`, `error` → `remote-failed`), and page artifact status mapping (`dirty/saving/synced/error/offline` into the same badge vocabulary). The mounted canvas status bar and `CanvasSyncService` consume the shared contract instead of maintaining divergent status unions/label maps, and status badge tests assert tokenized classes instead of raw color names. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/services/canvas/__tests__/canvasSyncStatus.test.ts src/services/canvas/__tests__/CanvasSyncService.test.ts src/routes/app/project/_canvas/__tests__/CanvasStatusBar.test.tsx src/components/status/__tests__/status-components.test.tsx src/routes/app/project/__tests__/canvas.integration.test.tsx`; narrowed canvas sync status typecheck grep |
| P2-003 | Implemented for validated and auditable mounted canvas JSON import | Mounted canvas JSON import now validates untrusted files through a typed Zod schema before mutating canvas state, migrates legacy wrapped/current export payloads into the canonical `1.0.0` import contract, rejects invalid JSON, duplicate node ids, malformed nodes, and dangling connections with user-facing error state/feedback, and records success/failure audit events through the existing `/api/audit/events` client when user/workspace/project context is available. PNG export failures now surface through the same feedback path instead of console-only logging. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/_canvas/__tests__/canvasImportContract.test.ts src/routes/app/project/_canvas/__tests__/useCanvasExport.test.tsx src/routes/app/project/__tests__/canvas.integration.test.tsx`; narrowed canvas import/export typecheck grep |
| P2-004 | Implemented for previewable and undoable legacy auto-layout | Legacy canvas auto-layout now computes a typed preview/diff before moving elements, shows the move summary in the dialog, disables Apply until a preview exists, applies from the reviewed preview, records preview/apply/undo/redo audit callbacks, and keeps local undo/redo snapshots for applied layout changes instead of irreversibly replacing positions. | `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/canvas/hooks/__tests__/useCanvasLayout.test.tsx`; narrowed auto-layout typecheck grep |
| P2-005 | Implemented for deterministic canvas/page-builder performance budgets | Added shared performance budgets for 500-node canvases and large page-builder documents, including visible-node, render-time, interaction-latency, validation-time, and estimated-memory checks. `useUnifiedCanvas` now uses the shared viewport-culling threshold instead of a hard-coded value, and the web app exposes a CI-friendly `test:performance:budgets` script that validates large-canvas and large-builder budget behavior without flaky wall-clock rendering assertions. Browser/Playwright memory profiling for full visual flows remains a follow-up under P3-005/P3-006. | `pnpm --filter @ghatana/yappc-web-app run test:performance:budgets`; `pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/canvas.integration.test.tsx src/services/performance/__tests__/canvasPerformanceBudgets.test.ts`; narrowed performance budget typecheck grep |

Recommended next slices:

1. Proceed through the remaining P0/P1 audit backlog in priority order, keeping each slice covered by focused unit/contract tests plus a narrowed typecheck grep.
2. Continue opportunistic lifecycle cleanup only when touching remaining lifecycle route internals, using the mounted adapter/schema contract added under P1-004.

## Verification Notes

Passing targeted checks:

```text
pnpm --filter @ghatana/yappc-web-app run test:performance:budgets
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/canvas.integration.test.tsx src/services/performance/__tests__/canvasPerformanceBudgets.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "canvasPerformanceBudgets|useUnifiedCanvas|test:performance:budgets|PerformanceBudget" -C 2
```

Result: deterministic canvas/page-builder performance budget tests passed through the package script (1 file, 5 tests); mounted canvas integration plus budget tests passed (2 files, 28 tests); narrowed typecheck grep returned no matches for the performance budget files touched by this P2-005 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/canvas/hooks/__tests__/useCanvasLayout.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "useCanvasLayout|CanvasDialogs|CanvasPanels|AutoLayout" -C 2
```

Result: auto-layout preview/apply/undo/redo/audit hook tests passed (1 file, 2 tests); narrowed typecheck grep returned no matches for the auto-layout files touched by this P2-004 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/_canvas/__tests__/canvasImportContract.test.ts src/routes/app/project/_canvas/__tests__/useCanvasExport.test.tsx src/routes/app/project/__tests__/canvas.integration.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "canvasImportContract|useCanvasExport|project/canvas|CanvasImport" -C 2
```

Result: canvas import contract, mounted import/export hook, and mounted canvas integration tests passed (3 files, 30 tests); narrowed typecheck grep returned no matches for the canvas import/export files touched by this P2-003 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/services/canvas/__tests__/canvasSyncStatus.test.ts src/services/canvas/__tests__/CanvasSyncService.test.ts src/routes/app/project/_canvas/__tests__/CanvasStatusBar.test.tsx src/components/status/__tests__/status-components.test.tsx src/routes/app/project/__tests__/canvas.integration.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "canvasSyncStatus|CanvasSyncService|CanvasStatusBar|_canvas/types|SaveSyncStatus" -C 2
```

Result: shared canvas sync status, sync service, mounted status bar, status badge, and mounted canvas integration tests passed (5 files, 60 tests); narrowed typecheck grep returned no matches for the sync status files touched by this P2-002 slice.

```text
pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/projects.audit.test.ts src/__tests__/openapi-contract.test.ts
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/workspace/__tests__/accessControl.test.ts src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx
pnpm --filter @ghatana/yappc-api-app typecheck 2>&1 | rg "projects.audit|projects.ts|dashboard-actions" -C 2
```

Result: project dashboard action API/OpenAPI tests passed (2 files, 14 tests); frontend phase cockpit, workspace access, dashboard, and workspace data tests passed (4 files, 22 tests); narrowed API typecheck grep returned no matches for the included-project dashboard execution files touched by this P1-024 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/hooks/__tests__/useCapabilityGate.test.ts src/services/workspace/__tests__/accessControl.test.ts src/routes/app/admin/__tests__/billing-teams-gate.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "useCapabilityGate|accessControl|billing-teams-gate" -C 2
```

Result: backend-scoped workspace/admin capability tests passed (3 files, 29 tests); narrowed typecheck grep returned no matches for the admin capability gate files touched by this P1-023 slice.

```text
pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/source-imports.test.ts src/__tests__/openapi-contract.test.ts
pnpm --filter @ghatana/yappc-web-app exec vitest run src/services/compiler/__tests__/ImportSourceWorkflow.test.ts
pnpm --filter @ghatana/yappc-api-app typecheck 2>&1 | rg "source-imports|SourceImport" -C 2
```

Result: governed source import API/OpenAPI tests passed (2 files, 10 tests); web import workflow tests passed (1 file, 13 tests); narrowed API typecheck grep returned no matches for the source import audit files touched by this P1-022 slice.

```text
./gradlew :products:yappc:core:yappc-domain-impl:test --tests com.ghatana.yappc.domain.pageartifact.http.PageArtifactControllerTest
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/pageArtifactDocument.test.ts
```

Result: page artifact backend review-decision controller tests passed (16 tests); frontend PageDesigner/PageDesignerNode/page artifact governance tests passed (3 files, 66 tests).

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/pageArtifactPersistence.test.ts src/components/canvas/page/__tests__/pageArtifactDocument.test.ts src/components/canvas/page/__tests__/artifactCompilerBridge.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "pageArtifactPersistence|pageArtifactDocument|artifactCompilerBridge" -C 2
```

Result: page artifact graph snapshot, document, compiler bridge, and HTTP ingest persistence tests passed (3 files, 33 tests); narrowed typecheck grep returned no matches for the artifact graph persistence files touched by this P1-020 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/services/compiler/__tests__/ImportSourceWorkflow.test.ts src/components/canvas/page/__tests__/artifactCompilerBridge.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|PageDesigner.test|ImportSourceWorkflow|artifactCompilerBridge" -C 2
```

Result: import panel, source workflow, and artifact compiler bridge tests passed (3 files, 52 tests); narrowed typecheck grep returned no matches for the import review queue files touched by this P1-019 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/services/canvas/preview/__tests__/BidirectionalPreviewSync.test.ts src/components/canvas/unified/__tests__/UnifiedLeftRail.test.tsx src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "BidirectionalPreviewSync|UnifiedLeftRail|LayersPanel|useUnifiedCanvas|unifiedCanvasAtom|canvas.tsx|PageDesignerNode|LivePreviewPanel" -C 2
```

Result: preview sync, unified layers, PageDesigner, PageDesignerNode, and LivePreviewPanel tests passed (5 files, 65 tests); narrowed typecheck grep returned no matches for the preview hover/layers files touched by this P1-018 slice after synchronizing the canvas state shape.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/preview-builder.test.tsx src/routes/__tests__/preview-builder-security.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "preview-builder|preview-builder-security|LivePreviewPanel|ContentSecurityPolicy" -C 2
```

Result: preview runtime/security/host panel tests passed (3 files, 21 tests); narrowed typecheck grep returned no matches for the preview response-header files touched by this P1-017 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/preview-builder.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "preview-builder|LivePreviewPanel" -C 2
```

Result: preview runtime and host environment controls passed (2 files, 16 tests); narrowed typecheck grep returned no matches for the preview theme-pack and RTL locale files touched by this P1-016 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/page/__tests__/rendererManifest.test.tsx src/services/canvas/commands/__tests__/PageBuilderCommands.test.ts src/services/canvas/commands/__tests__/ResidualIslandReviewService.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|PageDesigner.test|ResidualIslandReviewService|rendererManifest|PageBuilderCommands" -C 2
```

Result: residual island renderer, PageDesigner review action, command metadata, and persistence service tests passed (4 files, 51 tests); narrowed typecheck grep returned no matches for the residual-review files touched by this P1-015 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/pluginLoader.test.ts src/services/plugins/__tests__/PluginRuntimePolicy.test.ts src/components/canvas/page/__tests__/rendererManifest.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "pluginLoader|components/canvas/page/__tests__/pluginLoader|rendererManifest" -C 2
```

Result: plugin loader/runtime policy/fallback renderer tests passed (3 files, 33 tests); narrowed typecheck grep returned no matches for the plugin-loader telemetry and fallback files touched by this P1-014 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PropertyForm.test.tsx src/components/canvas/page/__tests__/PageDesigner.test.tsx src/services/canvas/commands/__tests__/PageBuilderCommands.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PropertyForm|PageDesigner.tsx|PageBuilderCommands|PropertyForm.test|PageDesigner.test" -C 2
```

Result: PropertyForm/PageDesigner/PageBuilderCommands inspector variant and binding tests passed (3 files, 51 tests); narrowed typecheck grep returned no matches for the inspector integration files touched by this P1-013 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|ComponentRenderer|PageDesigner.test|PageDesignerNode" -C 2
```

Result: PageDesigner/PageDesignerNode keyboard/drop tests passed (2 files, 50 tests); narrowed typecheck grep returned no matches for the keyboard nesting files touched by this P1-012 slice.

```text
rg -n "SimplePageDesigner" products/yappc/frontend/web/src -S
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "SimplePageDesigner|components/canvas/page/index|PageDesigner.tsx|PageDesignerNode" -C 2
```

Result: `SimplePageDesigner` is absent from frontend source; canonical PageDesigner/PageDesignerNode tests passed (2 files, 48 tests); narrowed typecheck grep returned no matches for the duplicate page-designer removal.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "routes/dashboard|dashboard.test|useWorkspaceData" -C 2
rg -n "<button" products/yappc/frontend/web/src/routes/dashboard.tsx
```

Result: dashboard/workspace-data tests passed (2 files, 11 tests); narrowed typecheck grep returned no matches for the dashboard design-system card cleanup; `rg "<button"` returned no matches in `dashboard.tsx`.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "routes/dashboard|dashboard.test|useWorkspaceData" -C 2
```

Result: dashboard/workspace-data tests passed (2 files, 11 tests); narrowed typecheck grep returned no matches for the dashboard design-system cleanup files touched by this P1-010 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PropertyForm.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PropertyForm" -C 2
```

Result: PropertyForm governance tests passed (1 file, 3 tests); narrowed typecheck grep returned no matches for the inspector privacy-enforcement files touched by this P1-009 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/pageArtifactDocument.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesignerNode|pageArtifactDocument" -C 2
```

Result: PageDesignerNode/page artifact document conflict tests passed (2 files, 32 tests); narrowed typecheck grep returned no matches for the conflict compare/reload/overwrite files touched by this P1-008 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/pageArtifactDocument.test.ts src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "pageArtifactDocument|PageDesignerNode" -C 2
```

Result: page artifact document/PageDesignerNode operation-log tests passed (2 files, 31 tests); narrowed typecheck grep returned no matches for the operation-log export files touched by this P1-007 slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/route/__tests__/route-components.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "lazyRoutes|LazyAIPanel|LazyGuidedPanel|route-components|AIPanel" -C 2
```

Result: route component/lazy feature tests passed (1 file, 21 tests); narrowed typecheck grep returned no matches for the lazy route implementation files touched by this P1-006 slice.

```text
pnpm --filter @ghatana/yappc-api-app exec vitest run src/__tests__/openapi-contract.test.ts
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/command/__tests__/CommandPalette.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "ActionRegistry|CommandPalette|constants/shortcuts|ProjectLayout|generated/openapi|AICommandBar|ModeLevelAdapter|UnifiedRightPanel|AIPanel" -C 2
pnpm --filter @ghatana/yappc-api-app exec tsc --noEmit 2>&1 | rg "openapi-contract" -C 2
rg -n "Ask AI|Open AI assistant|Open AI Chat|Toggle AI Assistant|AI Chat|AI-powered project scaffolding|Generate AI lifecycle suggestions|AI-analyse captured intent|AI suggestions$|AI suggestion endpoints|Select elements to get AI suggestions" products/yappc/frontend/web/src products/yappc/docs/api/openapi.yaml products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts -S
```

Result: OpenAPI contract tests passed (1 file, 7 tests); command palette tests passed (1 file, 15 tests); narrowed web and API typecheck greps returned no matches for the public wording files touched by the P1-005 completion slice. The narrowed wording grep now only returns comments/internal client documentation plus the intentional `AI Chat Application` template/domain example.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts
pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/projects.audit.test.ts src/__tests__/openapi-contract.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "routes/dashboard|dashboard.test|CanonicalPhaseService|PhaseBuilders" -C 2
pnpm --filter @ghatana/yappc-api-app exec tsc --noEmit 2>&1 | rg "routes/projects|projects.audit|openapi-contract" -C 2
```

Result: dashboard/phase adapter tests passed (2 files, 16 tests); API project route/OpenAPI tests passed (2 files, 12 tests); narrowed web and API typecheck greps returned no matches for the lifecycle boundary files touched by the P1-004 completion slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx src/services/onboarding/__tests__/OnboardingStatusService.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "src/(routes/dashboard|routes/__tests__/dashboard|hooks/useWorkspaceData|hooks/__tests__/useWorkspaceData|services/onboarding/OnboardingStatusService|services/onboarding/__tests__/OnboardingStatusService)" -C 2
```

Result: dashboard/workspace/onboarding contract-hardening tests passed (3 files, 16 tests); narrowed typecheck grep returned no matches for the dashboard, workspace-data, or onboarding files touched by this P1-025 slice. Full web typecheck still reports unrelated pre-existing `yappc-core`, design-system, inspector, plugin, and optional dependency issues outside this slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/hooks/__tests__/useWorkspaceData.test.tsx
pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/projects.audit.test.ts src/__tests__/openapi-contract.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "routes/dashboard|dashboard.test|useWorkspaceData|query-keys|lib/api/client|lib/api/index" -C 2
pnpm --filter @ghatana/yappc-api-app exec tsc --noEmit 2>&1 | rg "routes/projects|projects.audit|openapi-contract" -C 2
```

Result: dashboard/workspace-data tests passed (2 files, 10 tests); API project route/OpenAPI tests passed (2 files, 10 tests); narrowed web and API typecheck greps returned no matches for the dashboard action-summary and safe-action execution files touched by the P1-003/P1-025 completion slice.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/preview.test.tsx src/routes/app/project/__tests__/deploy.test.tsx src/routes/app/project/__tests__/lifecycle.test.tsx src/routes/app/project/__tests__/shell.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "LegacyRouteCompatibilityNotice|routes/app/project/(preview|deploy|lifecycle|canvas|_shell)|project/__tests__/(preview|deploy|lifecycle|shell)" -C 2
```

Result: project route compatibility tests passed (4 files, 17 tests); narrowed typecheck grep returned no matches for the legacy route compatibility files touched by P1-001.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "_phaseCockpit|phase-cockpit-routes|PhaseCockpitContractBuilder|usePhaseCockpitData|PhaseBuilders" -C 2
```

Result: phase cockpit route/contract tests passed (2 files, 16 tests); narrowed typecheck grep returned no matches for the mounted phase contract visibility files touched by P1-002.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/studio/__tests__/LivePreviewPanel.test.tsx
pnpm --filter @ghatana/yappc-web-app exec playwright test e2e/page-designer-live-backend.spec.ts --list
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesignerNode|PageDesigner.tsx|routes/app/project/canvas|page-designer-live-backend|ghatana-design-system|vite.config" -C 2
E2E_BASE_URL=http://localhost:7003 pnpm --filter @ghatana/yappc-web-app exec playwright test e2e/page-designer-live-backend.spec.ts --project=chromium --timeout=90000
```

Result: focused PageDesigner/PageDesignerNode/LivePreviewPanel tests passed (3 files, 54 tests); the Playwright spec is discoverable; live Chromium now passes against `E2E_BASE_URL=http://localhost:7003` (1 test passed). The spec exercises semantic import, live preview rendering, real adapter tenant/workspace/project headers, save, 409 conflict surfacing, and reload through an in-page page-artifact backend mock. Narrowed typecheck grep returned no matches for the page-designer live-backend slice; full web typecheck still has unrelated pre-existing issues outside this slice. Implementation changes from this slice include the dev-only project-scoped canvas seed hook, page-designer route data bridge for `useUnifiedCanvas`, command-time `PageDesigner` document notifications, idempotent document-change guarding, stale governance-record protection in route mode, explicit import-to-host document notification coverage, design-system `TextArea` shim compatibility, page-designer node sizing, and Vite browser define for the artifact compiler `process.env` read.

```text
pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/source-imports.test.ts src/__tests__/openapi-contract.test.ts
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/services/compiler/__tests__/ImportSourceWorkflow.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|PageDesigner.test|ImportSourceWorkflow|lib/api/client" -C 2
pnpm --filter @ghatana/yappc-api-app exec tsc --noEmit 2>&1 | rg "source-imports|openapi-contract" -C 2
```

Result: API source import/OpenAPI tests passed (2 files, 8 tests); PageDesigner/import workflow tests passed (2 files, 41 tests); narrowed typecheck greps returned no matches for governed import files.

```text
./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.api.RunApiControllerTest
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts
pnpm --filter @ghatana/yappc-api-app exec vitest run src/__tests__/openapi-contract.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "src/(routes/app/project/_phaseCockpit|routes/app/project/__tests__/phase-cockpit-routes|services/phase/PhaseCockpitActionService|services/phase/__tests__/PhaseBuilders|lib/api/client)|_phaseCockpit|PhaseCockpitActionService|PhaseBuilders|lib/api/client" -C 2
```

Result: backend Run controller tests passed (4 tests); mounted Run cockpit/phase service tests passed (2 files, 16 tests); OpenAPI contract passed (5 tests); narrowed typecheck grep returned no matches for Run post-action files.

```text
./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.api.GenerationApiControllerTest
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/phase/__tests__/PhaseBuilders.test.ts
pnpm --filter @ghatana/yappc-api-app exec vitest run src/__tests__/openapi-contract.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "frontend/web/src/(routes/app/project/_phaseCockpit|services/phase/PhaseCockpitActionService|services/phase/__tests__/PhaseBuilders|lib/api/client)|src/routes/app/project/_phaseCockpit|src/services/phase/PhaseCockpitActionService|src/services/phase/__tests__/PhaseBuilders|src/lib/api/client" -C 2
```

Result: backend generation controller tests passed (4 tests); mounted Generate cockpit/phase service tests passed (2 files, 15 tests); OpenAPI contract passed (5 tests); narrowed typecheck grep returned no matches for Generate review files. Full web typecheck still reports unrelated pre-existing `yappc-core` GraphQL/AI type issues outside this slice.

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

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/services/workspace/__tests__/accessControl.test.ts src/hooks/__tests__/useCapabilityGate.test.ts
pnpm --filter @ghatana/yappc-api-app exec vitest run src/routes/__tests__/workspaces.audit.test.ts src/routes/__tests__/projects.audit.test.ts
pnpm --filter @ghatana/yappc-api-app exec tsc --noEmit 2>&1 | rg "routes/workspaces|routes/projects|workspaces.audit|projects.audit" -C 2
```

Result: frontend role/capability tests passed (2 files, 20 tests); API route tests passed (2 files, 4 tests); API touched-file typecheck grep returned no matches for P1-023 route/test files.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx src/services/workspace/__tests__/accessControl.test.ts
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "usePhaseCockpitData|services/phase/types|lib/api/client|phase-cockpit-routes|accessControl" -C 2
```

Result: 2 frontend test files passed, 10 tests passed; narrowed typecheck grep returned no matches for the phase cockpit read-only gating files touched by P1-024.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/routes/__tests__/dashboard.test.tsx src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "routes/dashboard|dashboard.test" -C 2
```

Result: 2 frontend test files passed, 8 tests passed; narrowed typecheck grep returned no matches for the dashboard source-of-truth route files touched by P1-025.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/__tests__/canvasAccessPolicy.test.ts src/routes/app/project/canvas/__tests__/CanvasRoute.access.test.tsx src/routes/app/project/_canvas/__tests__/useCanvasKeyboardShortcuts.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "canvasAccessPolicy|canvas.tsx|useCanvasKeyboardShortcuts|CanvasNodeContextMenu|CanvasWorkspace.tsx|CanvasRoute.access|useCanvasKeyboardShortcuts.test" -C 2
```

Result: 3 frontend test files passed, 8 tests passed; narrowed typecheck grep returned no matches for the mounted and legacy canvas policy, route, provider, overlay, panel, drag/drop, handler, context-menu, and shortcut files touched by P2-001.

```text
pnpm --filter @ghatana/yappc-web-app exec vitest run src/components/canvas/page/__tests__/PageDesigner.test.tsx src/components/canvas/page/__tests__/PropertyForm.test.tsx src/components/canvas/nodes/__tests__/PageDesignerNode.test.tsx src/components/canvas/__tests__/canvasAccessPolicy.test.ts src/routes/app/project/canvas/__tests__/CanvasRoute.access.test.tsx src/routes/app/project/_canvas/__tests__/useCanvasKeyboardShortcuts.test.tsx
pnpm --filter @ghatana/yappc-web-app typecheck 2>&1 | rg "PageDesigner.tsx|PageDesigner.test|PropertyForm.tsx|PropertyForm.test|PageDesignerNode|canvasAccessPolicy|CanvasWorkspace.tsx|canvas.tsx" -C 2
```

Result: 6 frontend test files passed, 55 tests passed; narrowed typecheck grep returned no matches for the embedded page-builder policy propagation, mutation guards, wrapper persistence guards, and related canvas policy files touched by the P2-001 completion pass.

Known broader gate failure:

```text
pnpm --filter @ghatana/yappc-web-app typecheck
```

Result: fails on pre-existing broad workspace errors outside this implementation slice, including `libs/yappc-core` API typings, dashboard/design-system prop mismatches, inspector/plugin service typing, and missing `jspdf` / `html2canvas` type resolution.
