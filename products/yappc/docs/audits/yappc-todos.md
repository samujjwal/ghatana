Here is the TODO list in plain Markdown.

# YAPPC Production Readiness TODO List

## P0 — Scope, Authorization, and Access

### TODO-001 — Make scoped project context mandatory in project shell

**Priority:** P0
**Area:** Authorization / Project Shell
**Details:** Remove unscoped project fetch fallback. Project routes must not render unless `tenantId`, `workspaceId`, `projectId`, authenticated `userId`, role, and capabilities are resolved.
**Acceptance criteria:** Direct project URL access fails safely when scope is missing or mismatched.

### TODO-002 — Scope every project-related data fetch

**Priority:** P0
**Area:** Authorization / Data Loading
**Details:** Project artifacts, sprint, backlog, runs, activity, dashboard actions, preview sessions, and exports must include workspace/project scope.
**Acceptance criteria:** No project-only API call remains for scoped resources.

### TODO-003 — Harden export authorization

**Priority:** P0
**Area:** Export / Security
**Details:** Export must be backend-authorized, scoped, audited, and user-visible on failure. Included/read-only users must be denied unless explicitly allowed.
**Acceptance criteria:** Export denial tests pass for viewer, included, and workspace-mismatch users.

### TODO-004 — Replace frontend-derived ownership with backend capability contract

**Priority:** P0
**Area:** Access Model
**Details:** Remove unsafe assumptions such as mapping every workspace as owner-owned. UI should render actions from backend-provided capability data.
**Acceptance criteria:** Owner/admin/editor/viewer UI differs only based on backend contract.

### TODO-005 — Make OpenAPI scope requirements consistent

**Priority:** P0
**Area:** API Contracts
**Details:** Make tenant/workspace/project scope mandatory for artifact review, registry promotion, import review, graph merge, preview, export, import, and generated artifact actions.
**Acceptance criteria:** OpenAPI, typed client, and server handlers agree.

### TODO-006 — Add complete access matrix tests

**Priority:** P0
**Area:** Testing
**Details:** Cover owner, admin, editor, viewer, included/read-only, support/delegated access, workspace mismatch, project mismatch, and artifact mismatch.
**Acceptance criteria:** Backend and UI tests prove all unauthorized paths are denied.

---

## P0 — Lifecycle Governance

### TODO-007 — Create canonical lifecycle policy service

**Priority:** P0
**Area:** Lifecycle
**Details:** Centralize phase readiness, blockers, transition rules, bypass rules, required evidence, approval state, and audit records.
**Acceptance criteria:** No phase transition is decided only in the frontend.

### TODO-008 — Enforce backend phase access for direct routes

**Priority:** P0
**Area:** Routing / Lifecycle
**Details:** Direct access to `/p/:projectId/intent`, `/shape`, `/validate`, `/generate`, `/run`, `/observe`, `/learn`, and `/evolve` must verify scope and phase capability.
**Acceptance criteria:** Direct URL access cannot bypass lifecycle gates.

### TODO-009 — Convert legacy project routes into redirect/deprecation routes

**Priority:** P0
**Area:** Routing Cleanup
**Details:** `canvas`, `preview`, `deploy`, and `lifecycle` routes should redirect to canonical phases with telemetry and removal dates.
**Acceptance criteria:** Legacy routes are not first-class navigation surfaces.

### TODO-010 — Add lifecycle transition golden tests

**Priority:** P0
**Area:** Testing
**Details:** Test valid transitions, blocked transitions, bypass approval, evidence requirements, rollback, retry, and audit trail behavior.
**Acceptance criteria:** Lifecycle state machine is deterministic and fully tested.

---

## P0 — Dashboard Truthfulness

### TODO-011 — Make backend dashboard actions authoritative

**Priority:** P0
**Area:** Dashboard
**Details:** Dashboard actions should come from backend action registry. Client-derived `aiNextActions` should be clearly marked as degraded/fallback.
**Acceptance criteria:** No frontend-generated action appears as authoritative.

### TODO-012 — Remove empty-action fallback that masks backend failure

**Priority:** P0
**Area:** Dashboard / Reliability
**Details:** Distinguish “loaded and empty” from “backend unavailable.” Do not show workspace as clear when action classification failed.
**Acceptance criteria:** Backend failure produces degraded dashboard state.

### TODO-013 — Add dashboard degraded-state UX

**Priority:** P0
**Area:** UX / Reliability
**Details:** Show retry, reason, and correlation ID when workspace, project, or action APIs fail.
**Acceptance criteria:** User sees actionable error instead of silent fallback.

### TODO-014 — Test dashboard safe-to-run action execution

**Priority:** P0
**Area:** Testing
**Details:** Safe actions must execute only through backend. Review-required actions must route to review. Unsafe actions must never run directly.
**Acceptance criteria:** Dashboard action tests cover blocker, review, and safe continuation paths.

---

## P0 — Prompt → Generate → Preview → Export Flow

### TODO-015 — Define canonical generation run state machine

**Priority:** P0
**Area:** Product Flow
**Details:** Model the full flow: Intent → Plan → Confirm → GenerateRun → ReviewDiff → PreviewSession → Apply/Reject/Rollback → Export/Deploy.
**Acceptance criteria:** Every generated artifact belongs to a tracked generation run.

### TODO-016 — Trace generated artifacts to intent and plan

**Priority:** P0
**Area:** Provenance
**Details:** Generated files must include requirement, phase, canvas node, source artifact, confidence, review actor, and approval time.
**Acceptance criteria:** Exported artifacts include review/provenance metadata.

### TODO-017 — Block export/apply before review is complete

**Priority:** P0
**Area:** Governance
**Details:** Export/apply/deploy must check generation run status, review decision, artifact sync status, and authorization.
**Acceptance criteria:** Incomplete or unreviewed generation cannot be exported.

### TODO-018 — Add full prompt-to-download E2E test

**Priority:** P0
**Area:** Testing
**Details:** Cover prompt capture, plan generation, confirm, generate, review, preview, export/download, rollback, and failure recovery.
**Acceptance criteria:** One Playwright test validates the complete happy path and at least one failure path.

---

## P0 — Builder, Canvas, Compiler, and Decompiler

### TODO-019 — Split `PageDesigner.tsx` into focused modules

**Priority:** P0
**Area:** UI Architecture
**Details:** Extract builder shell, import wizard, residual review, graph merge review, registry candidate panel, AI lineage panel, and audit boundary.
**Acceptance criteria:** `PageDesigner` becomes a thin orchestrator, not the owner of every workflow.

### TODO-020 — Move import/decompile orchestration into services

**Priority:** P0
**Area:** Compiler / Decompiler
**Details:** Semantic import, source import, runtime health checks, residual review, and graph merge should be service-backed and independently testable.
**Acceptance criteria:** Import workflows have non-React unit/integration tests.

### TODO-021 — Govern semantic JSON import path

**Priority:** P0
**Area:** Import Security
**Details:** Pasted semantic model import must either become a governed backend workflow or be explicitly dev-only.
**Acceptance criteria:** Production cannot import untrusted semantic JSON without validation, review, and audit.

### TODO-022 — Harden residual island review workflow

**Priority:** P0
**Area:** Artifact Review
**Details:** Residual islands must require accept, reject, or promote decisions with persisted audit evidence and clear downstream impact.
**Acceptance criteria:** No residual content is silently dropped or promoted.

### TODO-023 — Add compiler/decompiler round-trip golden tests

**Priority:** P0
**Area:** Testing
**Details:** Verify source import, residual preservation, loss points, graph merge conflicts, generated builder document, and re-export fidelity.
**Acceptance criteria:** Round-trip tests fail on silent data loss.

---

## P0 — Persistence and Sync

### TODO-024 — Make local artifact drafts visibly non-authoritative

**Priority:** P0
**Area:** Persistence
**Details:** Local storage fallback should show local-only/recovery state and must never appear as synced production truth.
**Acceptance criteria:** UI clearly labels local-only artifact state.

### TODO-025 — Block production actions from unsynced local-only artifacts

**Priority:** P0
**Area:** Persistence / Governance
**Details:** Preview, export, apply, and deploy must require successful server sync unless explicitly in dev/offline recovery mode.
**Acceptance criteria:** Unsynced local-only artifacts cannot be exported.

### TODO-026 — Add artifact conflict resolution UX and tests

**Priority:** P0
**Area:** Persistence / Collaboration
**Details:** Support reload, compare, reapply, discard, and retry when remote artifact version conflicts occur.
**Acceptance criteria:** Conflict behavior is deterministic and tested.

---

## P1 — Preview Runtime

### TODO-027 — Require scoped preview session for production preview

**Priority:** P1
**Area:** Preview Security
**Details:** Preview session must be bound to tenant, workspace, project, artifact, user, and expiration.
**Acceptance criteria:** Cross-workspace preview token reuse fails.

### TODO-028 — Lock dev preview mode out of production builds

**Priority:** P1
**Area:** Security
**Details:** `mode=dev` should require dev build and explicit feature flag. Production builds must reject it.
**Acceptance criteria:** Production security test proves dev preview cannot be enabled.

### TODO-029 — Execute preview security tests in CI

**Priority:** P1
**Area:** Testing / Security
**Details:** Test token validation, origin checks, postMessage source checks, CSP headers, iframe sandbox, expired token, and spoofing denial.
**Acceptance criteria:** Preview security suite is release-blocking.

---

## P1 — API and Contracts

### TODO-030 — Generate REST client from OpenAPI

**Priority:** P1
**Area:** API Contracts
**Details:** Replace or shrink manually maintained REST client by generating it from canonical OpenAPI.
**Acceptance criteria:** No manually drifted endpoint definitions remain for REST-owned surfaces.

### TODO-031 — Verify API client, OpenAPI, and server route parity

**Priority:** P1
**Area:** Contract Testing
**Details:** Every client method must map to a documented and implemented server route. Every route must have auth, validation, and tests.
**Acceptance criteria:** CI fails on client/spec/server drift.

### TODO-032 — Resolve Node API `DashboardService` ownership

**Priority:** P1
**Area:** Backend Architecture
**Details:** Either make the Node-to-Java dashboard proxy the canonical gateway with tests, or delete/migrate it to the canonical API surface.
**Acceptance criteria:** No unclear proxy/gateway path remains.

### TODO-033 — Split REST and GraphQL-owned domains clearly

**Priority:** P1
**Area:** API Architecture
**Details:** Document and enforce which surfaces are REST-owned vs GraphQL-owned. Prevent mixed ownership and duplicate clients.
**Acceptance criteria:** Lint/contract tests block wrong client usage.

---

## P1 — Quality Gates and Architecture Enforcement

### TODO-034 — Make release readiness execution mode mandatory

**Priority:** P1
**Area:** CI / Release
**Details:** Production release gate must execute tests and verify results, not only check evidence files.
**Acceptance criteria:** Evidence-presence mode cannot pass a production release.

### TODO-035 — Execute visual and accessibility tests in CI

**Priority:** P1
**Area:** CI / UX Quality
**Details:** Remove evidence-only visual/a11y checks. Run Playwright visual regression and accessibility contracts on release branches.
**Acceptance criteria:** Visual/a11y failures block release.

### TODO-036 — Promote architecture governance warnings to errors

**Priority:** P1
**Area:** Lint / Governance
**Details:** Import cycles, restricted imports, legacy packages, design-system violations, a11y issues, and boundary violations should fail CI.
**Acceptance criteria:** Production code cannot merge with warning-mode architecture violations.

### TODO-037 — Add ArchUnit or equivalent boundary tests for core modules

**Priority:** P1
**Area:** Backend / Core Architecture
**Details:** Enforce no forbidden imports between agents, scaffold, refactorer, lifecycle, infrastructure, and AEP adapter modules.
**Acceptance criteria:** Core module dependency violations fail CI.

### TODO-038 — Expand design-system enforcement to all production UI

**Priority:** P1
**Area:** Design System
**Details:** Apply no-raw-buttons, no-raw-inputs, no-hardcoded-color, and no-ad-hoc-card rules across production UI.
**Acceptance criteria:** UI consistency rules apply beyond selected files.

---

## P1 — UX Integrity

### TODO-039 — Remove random fallback workspace/project suggestions

**Priority:** P1
**Area:** UX / AI Assist
**Details:** If backend AI suggestion fails, show degraded state and retry. Do not generate random names that look authoritative.
**Acceptance criteria:** No random fallback names in production UX.

### TODO-040 — Replace console-only failures with user-visible errors

**Priority:** P1
**Area:** Error Handling
**Details:** Export, AI assist, save, import, preview, and sync failures need visible feedback, retry path, and correlation/audit ID.
**Acceptance criteria:** No critical user action fails only through `console.error`.

### TODO-041 — Remove anonymous production artifact saves

**Priority:** P1
**Area:** Auth / Artifact Governance
**Details:** Artifact create/update must require authenticated user context. Anonymous fallback must be dev-only or removed.
**Acceptance criteria:** Production artifact mutation without authenticated user is impossible.

---

## P2 — i18n and Accessibility

### TODO-042 — Extract all production UI strings

**Priority:** P2
**Area:** i18n
**Details:** Dashboard, project shell, builder, preview, errors, forms, and status text should use i18n keys instead of hardcoded English.
**Acceptance criteria:** CI detects unextracted user-facing strings.

### TODO-043 — Add i18n coverage gate

**Priority:** P2
**Area:** i18n / CI
**Details:** Fail CI when production UI introduces unextracted strings or locale-unsafe formatting.
**Acceptance criteria:** Dates, numbers, currencies, and generated text are locale-aware.

### TODO-044 — Add keyboard-only canvas and builder tests

**Priority:** P2
**Area:** Accessibility
**Details:** Cover selection, insertion, move/reorder, delete, undo/redo, drawer/modal focus, and screen-reader announcements.
**Acceptance criteria:** Builder can be operated without mouse-only interactions.

### TODO-045 — Verify preview locale and RTL rendering

**Priority:** P2
**Area:** Preview / i18n
**Details:** Test locale fixtures, direction, date/currency examples, theme switching, and viewport changes in preview runtime.
**Acceptance criteria:** Preview renders correctly for RTL and non-default locales.

---

## Repository Cleanup

### TODO-046 — Move `DOMAIN_MODEL_REGISTRY.md` into canonical docs tree

**Priority:** P1
**Area:** Documentation
**Details:** Move from `products/yappc/DOMAIN_MODEL_REGISTRY.md` to `products/yappc/docs/DOMAIN_MODEL_REGISTRY.md` and update references.
**Acceptance criteria:** Documentation index links the canonical location.

### TODO-047 — Merge artifact compiler implementation docs

**Priority:** P1
**Area:** Documentation
**Details:** Merge artifact compiler implementation plan/review docs into canonical compiler/decompiler architecture or `docs/implementation-plans/`.
**Acceptance criteria:** No duplicate artifact compiler planning docs remain.

### TODO-048 — Archive or remove stale TODO and audit docs

**Priority:** P1
**Area:** Documentation Cleanup
**Details:** Convert live tasks into issues or canonical migration matrix. Mark archived audits as historical and non-canonical.
**Acceptance criteria:** Future audits do not rediscover stale TODO docs as current truth.

### TODO-049 — Review and remove stub-generation scripts

**Priority:** P2
**Area:** Repo Cleanup
**Details:** Delete unused stub-generation scripts or move them to explicitly dev-only tooling with no production references.
**Acceptance criteria:** No production path references stub-generation outputs.

### TODO-050 — Consolidate VS Code extension docs

**Priority:** P2
**Area:** Documentation
**Details:** Move `tools/vscode-extension` docs into `products/yappc/docs/guides/vscode-extension/` and update documentation index.
**Acceptance criteria:** VS Code extension docs are discoverable from canonical docs index.

### TODO-051 — Add documentation index validation

**Priority:** P2
**Area:** Documentation CI
**Details:** CI should verify canonical docs are linked, archive docs are excluded as source of truth, and scattered docs are not added.
**Acceptance criteria:** Scattered product-wide docs fail validation.

### TODO-052 — Remove deprecated compatibility modules after migration

**Priority:** P2
**Area:** Architecture Cleanup
**Details:** Track and remove deprecated `spi` compatibility wrapper and agents consolidation leftovers once consumers are migrated.
**Acceptance criteria:** No deprecated compatibility path remains without explicit ADR.

### TODO-053 — Validate no duplicate runtime systems remain

**Priority:** P1
**Area:** Architecture Cleanup
**Details:** Check for duplicate API clients, preview runtimes, builder models, compiler/decompiler pipelines, local fallback paths, and duplicate route models.
**Acceptance criteria:** One canonical implementation exists for each runtime concern.
