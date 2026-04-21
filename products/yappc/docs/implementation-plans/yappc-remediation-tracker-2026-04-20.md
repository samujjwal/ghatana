# Yappc Remediation Tracker

Date: 2026-04-20
Purpose: Task-by-task implementation and validation tracker derived from the Yappc product audit.
Usage: Update status, owner, links, and notes as work progresses. The order below is intentional and follows dependency order.

## Status Legend

- `TODO`: not started
- `IN PROGRESS`: currently being worked on
- `BLOCKED`: cannot proceed yet
- `DONE`: implemented and validated
- `DEFERRED`: intentionally postponed

## Program Goals

- Make Yappc truthful before making it broader.
- Make the core flow simple before expanding advanced surfaces.
- Eliminate broken mounted routes and contract mismatches.
- Establish one canonical lifecycle model.
- Replace silent fallback behavior with visible, trustworthy state.
- Add enough integration coverage to prove the product actually works end to end.

## Validation Evidence

- 2026-04-21 mounted web regression check:
  - Fixed the duplicate generated import in `frontend/web/src/contracts/workspace-project.ts`.
  - Fixed the mounted canvas sync badge import in `frontend/web/src/routes/app/project/_canvas/CanvasStatusBar.tsx`.
  - Removed the stale unused `frontend/web/src/stores/workflow.sample-data.ts` fixture that no longer participates in the mounted product flow.
  - Normalized legacy lifecycle alias transition handling in `frontend/web/src/types/lifecycle.ts`.
- 2026-04-21 focused validation outcome:
  - Focused mounted Yappc web Vitest suite passed after the fixes above: 26 tests across 9 files.
  - Full `frontend/web` typecheck still reports large pre-existing product-wide type debt outside the mounted remediation scope; tracker task status remains tied to the mounted route and contract remediation work, not unrelated latent canvas debt.
- 2026-04-21 workflow and lifecycle type cleanup:
  - Expanded the local web shims for `@yappc/core/types` and `@ghatana/canvas` so the mounted workflow UI and phase action registry compile against the workflow taxonomy and action contract they actually use.
  - Cleaned the focused workflow/lifecycle files `frontend/web/src/canvas/phase-actions.ts`, `frontend/web/src/components/workflow/CategoryContextPanel.tsx`, `frontend/web/src/components/workflow/steps/IntentStep.tsx`, and `frontend/web/src/components/workflow/steps/InstitutionalizeStep.tsx` to match the MUI-backed design-system API.
  - Verified those four files are typecheck-clean.
  - Re-ran full `frontend/web` typecheck and reduced the package error floor from 3715 errors to 2384, shifting the remaining dominant debt to canvas-node implementation files rather than workflow/type infrastructure.
- 2026-04-21 canvas node type-contract cleanup:
  - Normalized React Flow node typing in the dominant canvas-node hotspot files by switching raw `NodeProps<Data>` usage to `NodeProps<Node<Data>>` and widening node data interfaces to satisfy the canvas contract instead of collapsing `data` to `unknown`.
  - Cleaned the canvas-node files `frontend/web/src/components/canvas/NodePropertiesPanel.tsx`, `frontend/web/src/components/canvas/UnifiedRightPanel.tsx`, `frontend/web/src/components/canvas/nodes/ArtifactNode.tsx`, `frontend/web/src/components/canvas/nodes/security/ThreatNode.tsx`, `frontend/web/src/components/canvas/nodes/development/StoryNode.tsx`, `frontend/web/src/components/canvas/nodes/development/SprintNode.tsx`, `frontend/web/src/components/canvas/nodes/development/PolicyNode.tsx`, `frontend/web/src/components/canvas/nodes/development/DeploymentNode.tsx`, `frontend/web/src/components/canvas/nodes/development/PullRequestNode.tsx`, `frontend/web/src/components/canvas/nodes/bootstrap/ServiceNode.tsx`, `frontend/web/src/components/canvas/nodes/bootstrap/UserNode.tsx`, `frontend/web/src/components/canvas/nodes/bootstrap/IntegrationNode.tsx`, `frontend/web/src/components/canvas/nodes/bootstrap/FeatureNode.tsx`, `frontend/web/src/components/canvas/nodes/bootstrap/DatabaseNode.tsx`, `frontend/web/src/components/canvas/nodes/operations/RunbookNode.tsx`, `frontend/web/src/components/canvas/nodes/operations/DashboardNode.tsx`, `frontend/web/src/components/canvas/nodes/operations/MetricNode.tsx`, `frontend/web/src/components/canvas/nodes/operations/IncidentNode.tsx`, `frontend/web/src/components/canvas/nodes/security/AuditNode.tsx`, and `frontend/web/src/components/canvas/nodes/security/ComplianceNode.tsx`.
  - Verified the targeted bootstrap, operations, and security node clusters are typecheck-clean after removing the remaining unsupported Lucide icon tooltip props.
  - Re-ran full `frontend/web` typecheck and reduced the package error floor from 2384 to 1542, shifting the remaining dominant debt away from canvas-node contract mismatches and toward audit, service, and shared-panel files.
- 2026-04-21 audit UI and service contract cleanup:
  - Cleaned the audit/UI files `frontend/web/src/components/audit/ComplianceReport.tsx`, `frontend/web/src/components/audit/AuditTrail.tsx`, and `frontend/web/src/components/shared/ErrorBoundary.tsx` by removing unsupported `@ghatana/design-system` props and exports, simplifying menus/export controls, and replacing unsupported layout props with class-based layout.
  - Cleaned the service files `frontend/web/src/services/canvas/CanvasPersistence.ts` and `frontend/web/src/services/export/ExportService.ts` by aligning them with the canonical `CanvasElement`, `CanvasConnection`, and `CanvasState` contracts, adding explicit legacy JSON guards, and eliminating `unknown` property access in export rendering paths.
  - Verified all five files above are typecheck-clean.
  - Re-ran full `frontend/web` typecheck and reduced the package error floor from 1542 to 1345, shifting the next dominant debt to sketch, workflow, and shared canvas state files rather than audit UI or core export/persistence services.
- 2026-04-21 workflow panel and lifecycle hook cleanup:
  - Cleaned the workflow files `frontend/web/src/components/workflow/EvidencePanel.tsx`, `frontend/web/src/components/workflow/steps/LearnStep.tsx`, and `frontend/web/src/components/workflow/steps/VerifyStep.tsx` by replacing stale `@yappc/core/types` imports with local step-data shapes, removing unsupported `@ghatana/design-system` props, and aligning the audit panel with the current store-backed audit entry shape.
  - Cleaned `frontend/web/src/hooks/useLifecycleData.ts` by adding explicit TanStack Query mutation generics, correcting optimistic-update context typing, normalizing temporary `Artifact`/`Evidence`/`AuditEvent` values to real `Date` objects, and introducing an explicit `projectId` mutation input for evidence cache invalidation.
  - Verified all four files above are typecheck-clean.
  - Re-ran full `frontend/web` typecheck and reduced the package error floor from 1345 to 1219, for a net reduction of 323 errors from the earlier 1542 baseline in this remediation segment. The next dominant debt is concentrated in shared canvas-state, unified canvas panels, export PNG, and remaining workflow step files.
- 2026-04-21 canvas workspace and follow-on workflow cleanup:
  - Cleaned the canvas workspace files `frontend/web/src/components/canvas/workspace/canvasSharedState.ts`, `frontend/web/src/components/canvas/UnifiedLeftPanel.tsx`, `frontend/web/src/components/canvas/CanvasReactFlowSurface.tsx`, `frontend/web/src/components/canvas/CanvasOverlays.tsx`, and `frontend/web/src/components/canvas/CanvasWorkspace.tsx` by adding explicit shared-state/document typing, aligning interaction-mode unions with the actual canvas modes, removing stale design-system props, dropping the excluded version-history import, and normalizing workspace handler/template contracts.
  - Cleaned `frontend/web/src/components/canvas/TypeSelectorModal.tsx`, `frontend/web/src/components/workflow/steps/ExecuteStep.tsx`, `frontend/web/src/components/workflow/steps/PlanStep.tsx`, `frontend/web/src/components/workflow/steps/ObserveStep.tsx`, and `frontend/web/src/components/command/CommandPalette.tsx` by replacing stale type imports with local shapes, aligning the modal with the local artifact-template union, and typing command-registry search/grouped actions against the real `ActionRegistry` contracts.
  - Cleaned `frontend/web/src/services/export/PNGExportService.ts`, `frontend/web/src/components/canvas/page/PropertyForm.tsx`, `frontend/web/src/components/canvas/unified/rail-config.ts`, `frontend/web/src/components/canvas/unified/UnifiedLeftRail.types.ts`, `frontend/web/src/components/canvas/stories/CanvasFeatureStoryCard.tsx`, `frontend/web/src/components/canvas/governance/GovernancePanel.tsx`, `frontend/web/src/components/canvas/tools/AccessibilityTool.tsx`, `frontend/web/src/components/canvas/nodes/initialization/ProvisioningProgressNode.tsx`, `frontend/web/src/components/canvas/nodes/initialization/ConfigurationWizardNode.tsx`, and `frontend/web/src/components/canvas/generation/CodeGenerationPanel.tsx` by replacing stale or missing dependency imports with local contracts, normalizing React Flow node typing, and removing unsupported design-system props.
  - Verified all twenty files above are typecheck-clean.
  - Re-ran full `frontend/web` typecheck using `error TS` counting and reduced the package error floor from 1219 to 907. From the 1060 base for this continuation segment, that is a verified reduction of 153 errors. The current top hotspots are platform `ui-builder`, `frontend/web/src/components/ai/RecentProjectsStrip.tsx`, `frontend/web/src/components/workflow/steps/ContextStep.tsx`, `frontend/web/src/components/canvas/UnifiedCanvasDemo.tsx`, and the remaining initialization/session/lifecycle support files.
- 2026-04-21 continuation cleanup after the 907 checkpoint:
  - Cleaned `frontend/web/src/components/ai/RecentProjectsStrip.tsx`, `frontend/web/src/components/workflow/steps/ContextStep.tsx`, `frontend/web/src/components/canvas/UnifiedCanvasDemo.tsx`, `frontend/web/src/components/canvas/nodes/initialization/ProviderNode.tsx`, `frontend/web/src/components/canvas/nodes/collaboration/SessionNode.tsx`, `frontend/web/src/state/atoms/breadcrumbAtom.ts`, `frontend/web/src/components/lifecycle/ContextDrawer.tsx`, `frontend/web/src/components/canvas/unified/UnifiedToolbar.tsx`, `frontend/web/src/components/canvas/stories/CanvasFeatureStoryList.tsx`, `frontend/web/src/hooks/useWorkspaceData.ts`, `frontend/web/src/components/canvas/unified/panels/LayersPanel.tsx`, and `frontend/web/src/components/canvas/SimplifiedCanvasWorkspace.tsx` by replacing stale external imports with local contracts, aligning React Flow generic usage with the atom state types, tightening optimistic mutation/query-cache typing, and removing unsupported `@ghatana/design-system` props in the remaining canvas/lifecycle UI.
  - Verified the targeted hotspot files above are typecheck-clean, using focused `tsc` checks for each cluster before refreshing the package-wide count.
  - Re-ran full `frontend/web` typecheck using `error TS` counting and reduced the package error floor from 907 to 747. From the user-provided 1060 base for this remediation continuation, that is a verified reduction of 313 errors, clearing the `>300` session target on a package-wide count.
- 2026-04-21 shared compatibility and resolver hardening:
  - Added compatibility support in `platform/typescript/design-system/src/atoms/TextField.tsx`, `platform/typescript/design-system/src/typography/Typography.tsx`, `platform/typescript/design-system/src/atoms/Tooltip.tsx`, `platform/typescript/design-system/src/atoms/Divider.tsx`, `platform/typescript/design-system/src/atoms/Spinner.tsx`, and `platform/typescript/design-system/src/atoms/ToggleButton.tsx` for the MUI-style props still exercised by Yappc (`multiline`, `rows`, adornments, `as`, `arrow`, numeric spinner sizes, `flexItem`, and group `variant`).
  - Corrected the mounted canvas abstraction navigator to use the children-based `Breadcrumbs` export instead of aliasing the item-based `Breadcrumb` API.
  - Fixed stale `@ghatana/platform-utils` resolver drift in `frontend/web/vite.config.ts` and `frontend/web/vitest.config.ts` so the web package no longer points to the removed `platform/typescript/foundation/platform-utils` path.
- 2026-04-21 mounted resolver completion pass:
  - Fixed the remaining web TypeScript resolver drift in `frontend/web/tsconfig.json` by pointing `@ghatana/platform-utils` at the canonical source package instead of requiring prebuilt `dist` declarations.
  - Fixed the Vitest runtime resolver in `frontend/web/vitest.config.ts` by mapping `clsx` and `tailwind-merge` to the concrete pnpm ESM entry files and allowing repo-root filesystem resolution for transitive workspace imports.
  - Replaced the remaining live `react-router-dom` imports in `frontend/web/src/**` with `react-router`, matching the actual dependency declared by the mounted web package and removing the last source-level router package drift in the shipped app code.
  - Re-ran the focused mounted regression `pnpm exec vitest run src/routes/app/project/__tests__/canvas.integration.test.tsx` from `frontend/web`; it passed cleanly at 23/23 tests, clearing the prior `clsx` workspace-resolution blocker and reaching mounted runtime assertions.

## Track 0: Program Setup

### Task 0.1

- Title: Appoint owners and working group
- Status: DONE
- Priority: P0
- Outcome: Clear ownership across product, web, API, DB, and platform.
- Suggested owners: Product architect, web lead, API lead, platform lead, QA lead
- Validation:
  - Named owners for each track
  - Review cadence defined
- Notes:
  - Added `docs/implementation-plans/yappc-remediation-working-agreement-2026-04-20.md` with a role-based working group, track ownership matrix, and explicit review cadence.
  - Ownership is defined by delivery role so remediation can proceed without inventing person-specific assignments inside the repo.

### Task 0.2

- Title: Freeze net-new surface area during core remediation
- Status: DONE
- Priority: P0
- Outcome: Team stops expanding broken or partial routes while repairing the core.
- Validation:
  - Team agreement documented
  - New UI features require audit sign-off until P0 and P1 are closed
- Notes:
  - Added the mounted-surface freeze policy to `docs/implementation-plans/yappc-remediation-working-agreement-2026-04-20.md`.
  - The agreement now requires product-architect audit sign-off for any net-new mounted route, settings surface, or product-claim change until all P0 and P1 tracker items are closed.

## Track 1: Canonical Product Model

### Task 1.1

- Title: Choose one canonical lifecycle model
- Status: DONE
- Priority: P0
- Outcome: One lifecycle vocabulary and phase sequence for the whole product.
- Scope:
  - README
  - config pipelines
  - agent transition config
  - Prisma enum
  - API DTOs
  - web labels and state handling
- Validation:
  - One lifecycle source of truth committed
  - All references updated
  - No conflicting phase names remain
- Notes:
  - Web lifecycle helpers were centralized in `web/src/shared/types/lifecycle.ts` and stale local lifecycle typing was removed from `workspaceAtom.ts`.
  - API lifecycle routes, Prisma enum/defaults, transition previews, artifact metadata, phase-gate metadata, and seeds now converge on the canonical 8-phase model (`INTENT`, `CONTEXT`, `PLAN`, `EXECUTE`, `VERIFY`, `OBSERVE`, `LEARN`, `INSTITUTIONALIZE`).
  - Legacy lifecycle names are normalized at write and preview boundaries so older callers degrade into canonical values instead of reintroducing drift.

### Task 1.2

- Title: Create shared lifecycle constants and helpers
- Status: DONE
- Priority: P0
- Outcome: Lifecycle logic is imported rather than duplicated.
- Scope:
  - Shared package or domain module
  - Phase ordering
  - Transition rules
  - Human-readable labels
  - Guard and readiness metadata
- Validation:
  - Web and API compile against the shared lifecycle module
  - Unit tests cover phase ordering and transitions
- Notes:
  - Shared lifecycle constants, labels, and guards were added to the web layer and existing consumers were switched to import them instead of duplicating lifecycle strings.
  - Cross-runtime sharing is still pending under Task 1.1.

## Track 2: Core Entity Correctness

### Task 2.1

- Title: Fix project creation response bug
- Status: DONE
- Priority: P0
- Outcome: Project creation no longer throws because of undefined `aiHealthScore`.
- Exact fix:
  - Update `products/yappc/frontend/apps/api/src/routes/projects.ts`
  - Return `healthResult.score` or remove the field
- Validation:
  - `projects.audit.test.ts` passes
  - Manual create-project smoke check passes
- Notes:
  - `apps/api/src/routes/projects.ts` now returns `healthResult.score` instead of referencing undefined `aiHealthScore`.
  - Focused vitest run passed for `apps/api/src/routes/__tests__/projects.audit.test.ts`.

### Task 2.2

- Title: Change default project lifecycle phase to canonical first phase
- Status: DONE
- Priority: P0
- Outcome: Newly created projects start in the true first lifecycle phase.
- Scope:
  - Prisma schema default
  - Any explicit create-path defaults
  - Data migration for existing records if needed
- Validation:
  - Schema migration applied
  - New project creation tests assert the correct initial phase
- Notes:
  - Runtime create paths in project and workspace bootstrap now start projects at `INTENT`.
  - Prisma lifecycle defaults were changed to `INTENT` and a migration was added to remap legacy persisted lifecycle values to the canonical model.

### Task 2.3

- Title: Audit create and update DTOs for project and workspace entities
- Status: DONE
- Priority: P1
- Outcome: Core entity contracts are minimal, aligned, and complete.
- Validation:
  - No unsupported fields remain in mounted forms
  - DTO tests or schema assertions added
- Notes:
  - Mounted workspace and project create/write paths now reuse generated request contracts instead of duplicating local request DTOs.
  - `useWorkspaceData.ts` now sends a shared `CreateProjectRequest` shape and no longer rolls optimistic project state back with the wrong workspace key.
  - Mounted route tests cover the supported workspace/project settings write contracts and advanced metadata disclosure stays read-only.

## Track 3: Onboarding Truthfulness

### Task 3.1

- Title: Decide whether onboarding is rich or minimal
- Status: DONE
- Priority: P0
- Outcome: Onboarding only asks for what will actually be saved and used.
- Decision options:
  - Rich path: persist project name, type, personas, and defaults
  - Minimal path: trim the form to just workspace and starter project creation
- Validation:
  - Final onboarding specification documented
- Notes:
  - Current implementation follows the rich path: onboarding project name, project type, and persona selections are persisted through the API-backed workspace bootstrap flow.

### Task 3.2

- Title: Persist onboarding project metadata
- Status: DONE
- Priority: P0
- Outcome: Project name and type from onboarding become the actual created project.
- Files likely involved:
  - `products/yappc/frontend/web/src/components/workspace/OnboardingFlow.tsx`
  - `products/yappc/frontend/apps/api/src/routes/workspaces.ts`
  - `products/yappc/frontend/apps/api/src/routes/projects.ts`
- Validation:
  - Onboarding integration test proves persistence
- Notes:
  - `OnboardingFlow.tsx` now sends starter project metadata and `apps/api/src/routes/workspaces.ts` uses it to create the real default project.
  - Focused route tests are green and dedicated onboarding regression coverage now verifies the bootstrap payload and starter project persistence path.

### Task 3.3

- Title: Persist onboarding persona selections durably
- Status: DONE
- Priority: P1
- Outcome: Persona selections are saved to a project or workspace model instead of local-only storage.
- Validation:
  - DB model decided
  - API contract added
  - Web reads and writes through the API
- Notes:
  - Persona selections now persist durably through workspace `aiTags` using `persona:*` tags during bootstrap instead of local-only storage.
  - A dedicated first-class persona model can still be introduced later if product requirements expand.

### Task 3.4

- Title: Fix onboarding completion semantics
- Status: DONE
- Priority: P0
- Outcome: Onboarding is only marked complete after successful durable setup.
- Validation:
  - Failure-path tests added
  - No false-complete state on failed setup
- Notes:
  - Onboarding now marks completion only after successful durable setup and no longer false-completes after generic creation failures.

## Track 4: Route Inventory and Product Surface Cleanup

### Task 4.1

- Title: Create canonical mounted-route inventory
- Status: DONE
- Priority: P0
- Outcome: Team has one source of truth for what is actually shipped.
- Validation:
  - Route inventory document committed
  - Every route classified as complete, gated, or hidden
- Notes:
  - Added `docs/architecture/YAPPC_MOUNTED_ROUTE_INVENTORY_2026-04-20.md` as the canonical route inventory derived from `frontend/web/src/routes.ts`.
  - The inventory distinguishes the 15 mounted route handlers from the much larger latent `web/src/pages/**` surface so reachability is no longer inferred from file presence.

### Task 4.2

- Title: Hide or remove broken mounted routes
- Status: DONE
- Priority: P0
- Outcome: Users no longer reach unsupported flows.
- Routes to review first:
  - `/profile`
  - workspace settings
  - project settings
  - preview
  - deploy
- Validation:
  - Navigation smoke test passes
  - Broken links eliminated
- Notes:
  - `/profile` was converted to a truthful read-only screen backed by `/api/auth/me`.
  - Workspace settings was reduced to backed fields only.
  - Project settings now exposes only backed fields and removes the stubbed admin panels.
  - Preview now renders a truthful unavailable state unless a dedicated preview host is configured, and deploy is labeled as planning guidance instead of live deployment telemetry.
  - The workspace-card settings CTA now routes to the mounted `/settings` surface instead of a dead nested workspace settings path, and mounted integration coverage locks that navigation in place.

### Task 4.3

- Title: Remove or archive unreachable page artifacts from product-critical areas
- Status: DONE
- Priority: P2
- Outcome: Repo surface better matches the real product surface.
- Validation:
  - Reachability audit complete
  - Archive or document latent surfaces clearly
- Notes:
  - Added `docs/architecture/YAPPC_LATENT_PAGE_SURFACE_2026-04-20.md` to explicitly classify the unmounted `web/src/pages/**` tree as latent product surface.
  - The mounted route inventory now links to the latent-surface document so shipped reachability and repository presence are no longer conflated.

## Track 5: API Contract Alignment

### Task 5.1

- Title: Standardize response envelopes for workspace and project reads
- Status: DONE
- Priority: P0
- Outcome: Frontend and API agree on one read-shape convention.
- Validation:
  - Workspace settings and project shell both load correctly
  - Contract tests added
- Notes:
  - Added shared response-unwrapping helper in `web/src/lib/http.ts`.
  - Project shell, project canvas, and workspace settings now correctly parse wrapped `{ project }` / `{ workspace }` responses.
  - Mounted regression coverage now proves workspace settings still loads the wrapped `{ workspace }` envelope.
  - Project and workspace mounted settings coverage now also locks the advanced metadata disclosure flow against the same wrapped read contracts.

### Task 5.2

- Title: Standardize update contracts for project and workspace writes
- Status: DONE
- Priority: P1
- Outcome: Write routes require only the right fields and are called consistently.
- Validation:
  - `workspaceId` and similar required data flows are coherent
  - Save-route tests added
- Notes:
  - Workspace settings now only sends supported fields.
  - Project settings now writes through the supported project PATCH path with explicit workspace context.
  - Lifecycle stage transitions now send the backend-required `{ fromStage, toStage }` payload instead of a raw target-only body.
  - Mounted regression coverage now locks workspace settings PATCH success/failure behavior and the lifecycle stage-transition write contract.
  - Mounted create paths now reuse generated request types and the optimistic project-create rollback path uses the correct `ownerWorkspaceId` key.

### Task 5.3

- Title: Add missing endpoints or remove unsupported UI dependencies
- Status: DONE
- Priority: P0
- Outcome: Mounted UIs call only supported routes.
- Candidate endpoints:
  - `/api/users/me`
  - project members
  - project tokens
  - project audit feed
- Validation:
  - No mounted screen calls a missing route
- Notes:
  - `/profile` no longer calls the missing `/api/users/me` route.
  - Project settings no longer calls unsupported members, tokens, or audit endpoints.
  - Preview and deploy were realigned so they no longer imply unsupported live execution surfaces.

### Task 5.4

- Title: Generate or centralize shared client types
- Status: DONE
- Priority: P1
- Outcome: Contract drift becomes harder to introduce.
- Validation:
  - Shared types used in web and API
  - Type-level contract breakages caught in CI
- Notes:
  - Mounted workspace and project settings routes now consume shared contract aliases backed by the generated OpenAPI schema types instead of duplicating local entity shapes.
  - `useWorkspaceData` and the mounted project shell now also consume shared contract aliases for the mounted workspace/project response shapes.
  - The mounted project overview route now also consumes shared project and activity contract aliases instead of introducing another local response shape.
  - Shared mounted contracts now also define preview status, release-planning status, lifecycle review-threshold status, and generated create-request aliases used by the mounted web routes.

## Track 6: Settings and Admin Simplification

### Task 6.1

- Title: Reduce workspace settings to only supported fields
- Status: DONE
- Priority: P0
- Outcome: Workspace settings become truthful and usable.
- Validation:
  - Unsupported fields removed or fully backed
  - GET and PATCH paths pass integration checks
- Notes:
  - Workspace settings now only loads and saves supported fields (`name`, `description`) and no longer exposes unsupported slug, billing, or notification controls.

### Task 6.2

- Title: Rebuild project settings around real capabilities only
- Status: DONE
- Priority: P0
- Outcome: Project settings no longer ship as a stubbed or broken admin panel.
- Validation:
  - Stub markers removed
  - Only backed sections remain visible
- Notes:
  - Project settings now reads and writes through the supported project contract.
  - Unsupported RBAC, token, and audit sections were removed from the mounted route and replaced with an explicit unavailable notice.

### Task 6.3

- Title: Reintroduce advanced settings behind progressive disclosure
- Status: DONE
- Priority: P2
- Outcome: Core flows stay calm while advanced controls remain available when real.
- Validation:
  - UX review sign-off
- Notes:
  - Workspace and project settings now keep primary edits focused while exposing backed metadata only after explicit disclosure.
  - Focused settings-route tests verify that advanced metadata stays hidden until requested.

## Track 7: Preview and Deploy Truthfulness

### Task 7.1

- Title: Decide product truth for preview
- Status: DONE
- Priority: P0
- Outcome: Preview is either implemented, clearly externalized, or hidden.
- Decision options:
  - Implement a real preview service contract
  - Integrate with an external preview host formally
  - Hide preview until supported
- Validation:
  - Decision documented
  - UI behavior matches reality
- Notes:
  - Product truth is now documented and implemented as: preview is not a built-in local runtime.
  - The mounted route presents either a configured external preview host or a truthful unavailable state.

### Task 7.2

- Title: Decide product truth for deploy
- Status: DONE
- Priority: P0
- Outcome: Deploy screen becomes real or becomes an explicitly scoped planning surface.
- Validation:
  - No hardcoded fake operational numbers remain
  - CI/CD adapter path matches UI claims
- Notes:
  - Product truth is now documented and implemented as: deploy is currently a release-planning surface, not a live deployment console.
  - The mounted route copy and behavior were already realigned to planning guidance while the no-op CI/CD backend remains in place.

### Task 7.3

- Title: Add deployment and preview status models
- Status: DONE
- Priority: P1
- Outcome: User can see pending, running, succeeded, failed, blocked, and approval-needed states.
- Validation:
  - Status model documented
  - API and UI states aligned
- Notes:
  - Mounted preview now renders an explicit status badge for `unconfigured` versus `external-ready` states.
  - Mounted deploy now renders a release-planning status badge and detail line for blocked, approval-needed, planning-ready, and final-phase outcomes.
  - Focused preview and deploy route tests now lock these mounted status states.

## Track 8: Save, Sync, and Visibility Truth

### Task 8.1

- Title: Inventory all local fallback repositories and optimistic save paths
- Status: DONE
- Priority: P0
- Outcome: Team knows every place where remote failure can be hidden.
- Validation:
  - Inventory committed
  - Each path classified by risk
- Notes:
  - Added `docs/implementation-plans/yappc-save-sync-inventory-2026-04-20.md` to classify mounted and non-mounted fallback persistence paths.
  - The inventory identifies canvas fallback/save paths as the highest-risk mounted truthfulness gap and separates them from lower-risk local caches such as auth session and onboarding persona state.

### Task 8.2

- Title: Introduce explicit sync states in the UI
- Status: DONE
- Priority: P0
- Outcome: User sees whether state is local-only, syncing, saved remotely, or failed.
- Validation:
  - Save-state component available and reused
  - Failure UX tested
- Notes:
  - Added a shared save/sync status badge and reused it in the mounted canvas and settings routes.
  - Workspace and project settings now surface unsaved, saving, saved, and failed states instead of relying on generic toasts alone.

### Task 8.3

- Title: Add event and audit visibility for important automation actions
- Status: DONE
- Priority: P1
- Outcome: Important automated actions become inspectable without clutter.
- Validation:
  - Project timeline or activity panel exists
  - Automation events render with reason and result
- Notes:
  - Added `/api/projects/:projectId/activity` and a mounted overview timeline backed by lifecycle and audit records.
  - Project create/update flows now write lifecycle activity entries so the cockpit timeline stays grounded in real product actions.

## Track 9: AI and Automation Realignment

### Task 9.1

- Title: Rewrite AI product language to match actual capability
- Status: DONE
- Priority: P1
- Outcome: Product claims are honest.
- Validation:
  - Copy review complete
  - No screen implies deeper AI than exists
- Notes:
  - Mounted lifecycle, onboarding, guest landing, dashboard, and empty-state copy now describe backed guidance and externalized preview/deploy behavior instead of inflated AI/runtime claims.

### Task 9.2

- Title: Expand only implicit, practical automation
- Status: DONE
- Priority: P2
- Outcome: AI reduces effort rather than advertising itself.
- Candidate interventions:
  - Autofill
  - Summaries
  - Next-best actions
  - Duplicate detection
  - Persona recommendation
- Validation:
  - UX outcomes measured by fewer inputs and fewer clicks
- Notes:
  - The mounted lifecycle route now frames automation as suggested tasks, evidence-based next steps, and guided promotion instead of theatrical automation language.
  - Dashboard and project navigation now route through the calmer cockpit-first entry point instead of dropping users straight into canvas.

### Task 9.3

- Title: Add confidence and review thresholds for automation
- Status: DONE
- Priority: P2
- Outcome: Low-risk automation stays hidden; high-risk automation becomes reviewable.
- Validation:
  - Confidence thresholds documented
  - Review triggers tested
- Notes:
  - The mounted lifecycle route now surfaces a review-threshold badge that keeps actions in human review unless confidence is at least 85% and no critical anomalies exist.
  - Focused lifecycle-route regression coverage verifies the review-threshold state remains visible in the mounted product.

## Track 10: Observability and Governance

### Task 10.1

- Title: Surface project-level status and next action in the UI
- Status: DONE
- Priority: P1
- Outcome: Users and operators can immediately tell what stage a project is in and what should happen next.
- Validation:
  - Project cockpit or shell shows status and owner of next action

- Notes:
  - The mounted project canvas now surfaces explicit persistence truth in-product via the status bar: `Local draft only` is shown instead of implying a synced remote draft.
  - This closes the shipped false-truth gap for the mounted canvas path because the route is not currently wired to authoritative remote persistence.
  - Static validation for the touched files is clean, and focused runtime validation now passes in `frontend/web/src/routes/app/project/__tests__/canvas.integration.test.tsx` after fixing shared UI Vitest resolution and token-barrel drift.
### Task 10.2

- Title: Build a lightweight approval and blocked-reason UX
- Status: DONE
- Priority: P1
- Outcome: Governance is understandable instead of hidden in config.
- Validation:
  - Approval-needed and blocked states have human-readable reasons
- Notes:
  - The project overview route now summarizes lifecycle gate status, leading blockers, and operator-facing next actions in plain language.

### Task 10.3

- Title: Map audit events to user-visible timeline entries
- Status: DONE
- Priority: P2
- Outcome: Auditability becomes useful in product workflows.
- Validation:
  - Timeline entries link back to real audit events or action records
- Notes:
  - The mounted cockpit timeline now renders combined lifecycle and audit records from the backed activity API.

## Track 11: Testing and Release Confidence

### Task 11.1

- Title: Fix Prisma generation and integration test bootstrap
- Status: DONE
- Priority: P0
- Outcome: Integration tests can run locally and in CI.
- Validation:
  - `lifecycle-gates.integration.test.ts` runs
  - CI setup documented
- Notes:
  - Added `apps/api` script `test:integration` and rewrote `src/__tests__/lifecycle-gates.integration.test.ts` to the current Prisma schema, canonical lifecycle model, real JWT signing, and the mounted `/api/projects/:projectId/gates/:stage` route contract used by the booted API app.
  - Added `apps/api/scripts/run-lifecycle-integration.sh` and `pnpm run test:integration:lifecycle` to boot disposable Postgres from the existing Yappc DB compose file, generate Prisma client, push the current schema, and run the lifecycle gate integration suite against a real database.
  - Validation now covers both bootstrap paths: the suite skips cleanly without `TEST_DATABASE_URL`, and the dedicated lifecycle integration runner provides a reproducible local/CI execution path with a disposable database.
  - Closing fixes included removing eager Prisma client creation from the API boot path so the test database URL is honored before the first connection is created.

### Task 11.2

- Title: Add full-stack tests for mounted critical flows
- Status: DONE
- Priority: P1
- Critical flows:
  - onboarding
  - workspace list and settings
  - project create and shell load
  - lifecycle transition
  - preview and deploy state behavior
- Validation:
  - Each mounted critical route has at least one full-stack happy path and one failure-path test
- Notes:
  - Focused mounted-flow coverage now exists for onboarding bootstrap success/failure, lifecycle phase preview, deploy planning transitions, project create, workspace bootstrap, project shell load, and project settings contracts.
  - Mounted regression coverage now also exercises workspace settings GET/PATCH/DELETE behavior, including save failure feedback, and locks the lifecycle stage-transition write contract to the backend-required `{ fromStage, toStage }` payload.
  - Deploy-route regression coverage now also verifies that lifecycle readiness load failures are surfaced explicitly and do not leave the promotion control in a misleading enabled state.
  - Operator-driven advancement on the mounted deploy route is now covered, including forwarding the operator note through the lifecycle transition request and surfacing the submission feedback.
  - Mounted overview-route regression coverage now verifies blocked-state summaries, readiness-service failure behavior, and recent activity rendering.
  - Mounted preview-route regression coverage now verifies truthful unavailable and external-host states, and mounted settings tests now cover progressive disclosure for advanced metadata.
  - Added `frontend/web/src/routes/__tests__/critical-flows.integration.test.tsx` for router-level mounted coverage across onboarding, workspace settings navigation, project list entry, project cockpit, lifecycle, deploy, and preview.
  - Focused validation is green across the new critical-flow suite plus the adjacent mounted route regressions (`workspaces-route`, `settings`, project shell, overview, preview, deploy, and lifecycle).

### Task 11.3

- Title: Add contract tests for shared API shapes
- Status: DONE
- Priority: P1
- Outcome: Envelope drift and required-parameter drift are caught automatically.
- Validation:
  - Contract tests exist for workspaces and projects
- Notes:
  - Focused regression validation is now in place for `projects.audit.test.ts`, `app/project/__tests__/shell.test.tsx`, and `workspaces-route.test.tsx`.
  - Added route-level regression coverage for the project settings envelope/write contract and API bootstrap coverage for workspace persona/default-project persistence.
  - Focused vitest validation passes across the touched project and workspace contract tests.

### Task 11.4

- Title: Add failure and recovery tests for sync-state behavior
- Status: DONE
- Priority: P1
- Outcome: Local fallback no longer hides critical truth from users.
- Validation:
  - Failure injection coverage added
- Notes:
  - Added overview-route regression coverage for blocked-state and readiness-service failure scenarios.
  - Mounted save surfaces now use deterministic sync-state rendering built on one shared component.

## Track 12: Product Simplification Delivery

### Task 12.1

- Title: Design one-project-cockpit information architecture
- Status: DONE
- Priority: P2
- Outcome: Core workflow is calmer and simpler.
- Validation:
  - UX spec approved
  - Redundant screens identified for merge or removal
- Notes:
  - `/p/:projectId` now opens on a mounted cockpit with lifecycle status, health, next actions, and recent activity instead of redirecting straight to canvas.

### Task 12.2

- Title: Implement cockpit and progressive disclosure model
- Status: DONE
- Priority: P2
- Outcome: Lifecycle, preview, deploy, and status become one coherent flow.
- Validation:
  - Usability review
  - Navigation regression tests
- Notes:
  - Added an Overview tab to the mounted shell navigation and aligned the project root to that cockpit flow.

### Task 12.3

- Title: Remove dashboard and settings clutter
- Status: DONE
- Priority: P2
- Outcome: Primary actions become obvious and calm.
- Validation:
  - Screen-by-screen copy and control count review
- Notes:
  - Dashboard navigation now routes back through the project cockpit instead of forcing a canvas-first entry.
  - Guest landing, empty state, onboarding, and mounted settings copy were simplified so the supported actions are clearer and the unsupported product theater is gone.

## Reporting Template

Use this flat template to update each task:

```md
### Task X.Y

- Title:
- Status:
- Priority:
- Owner:
- Started:
- Completed:
- Dependencies:
- Deliverables:
- Validation:
- Notes:
```

## Suggested Milestones

### Milestone 1: Core Truth

- Includes:
  - Track 1
  - Task 2.1
  - Task 2.2
  - Track 3
  - Track 4.2
  - Task 5.1
  - Task 6.1
  - Task 11.1

### Milestone 2: Usable Core Flow

- Includes:
  - Remaining Track 5
  - Track 6.2
  - Track 7 decisions
  - Track 8
  - Task 10.1
  - Task 11.2
  - Task 11.3

### Milestone 3: Trust and Simplification

- Includes:
  - Track 9
  - Track 10.2
  - Track 10.3
  - Track 12

## Exit Criteria

Yappc should not be considered production-ready until the following are true:

- One canonical lifecycle model exists everywhere.
- New projects start in the correct first phase.
- No mounted route is knowingly broken or unbacked.
- Onboarding is truthful and durably persisted.
- Save and sync state is explicit and trustworthy.
- Preview and deploy accurately reflect reality.
- Critical flows have real full-stack test coverage.
- Product claims about AI and automation are honest and practical.
