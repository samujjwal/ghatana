# YAPPC Production Readiness Audit

Repo: `samujjwal/ghatana`
Target commit: `98a6097b8a163ba06abb000a1c9151d9c43ba6ee` — verified as commit `refactor 44` 

I inspected the current YAPPC code/docs at this commit. I did **not** run build, typecheck, tests, or CI, so runtime/test pass status is **not verified from execution**.

---

## 7.1 Production Readiness Verdict

**Production ready: No**

**Confidence level: High**

YAPPC is clearly improving structurally, but it is not production-ready. The repo itself states YAPPC is “In Active Buildout,” gives production readiness **2/10**, AI-native maturity **3/10**, feature completeness **4/10**, and says phases 3–7 are still under active implementation/hardening .

**Main blockers**

1. Frontend build/typecheck integrity is not established; checked-in error reports list many affected files, including canvas and preview code .
2. Route inventory, route manifest, OpenAPI, and actual mounted routes are not aligned.
3. Authorization is fail-closed at service auth level but not proven as full project/workspace/artifact-level enforcement.
4. Lifecycle phases exist as mounted surfaces, but several phase actions are still audit/surface-only or use hardcoded workflow defaults.
5. Preview, builder persistence, artifact compiler/import, and generated artifact review flows are partially integrated but not yet proven end to end.
6. Cleanup is still needed: stale docs, checked-in error reports, latent pages, compatibility modules, and partial migration artifacts remain.

**Highest-risk area:** release integrity plus route/API/security contract drift. The product cannot be trusted operationally until the shipped route tree, API contract, typed client, authorization model, and test gates are generated from the same source of truth and pass cleanly.

**Is it significantly improving?** Yes, but not enough for production. Strong positive signals include canonical model docs, explicit 8-phase routes, typed REST client, secure preview-session direction, page artifact persistence abstractions, release-readiness scripts, and route/API parity work. However, several of these are still partial or evidence-presence gates rather than proof of working production behavior.

---

## 7.2 Root Architectural Blockers

### P0-1 Release/build/typecheck integrity is not established

**Why it matters:** YAPPC cannot be production-ready if the frontend may not typecheck or build cleanly.

**Root cause:** Error evidence is checked into the repo, release gates are not yet hard proof of clean execution, and build governance contains partial/skipped checks.

**Evidence:** `error_summary.txt` and `error_report.txt` list many affected files, including mounted canvas, preview, canvas workspace, registry, page designer, command, and platform UI-builder files  . `LivePreviewPanel.tsx` itself appears in those reports and the fetched file content shows a risky trailing brace shape at the end of the component body; build execution was not run to confirm the exact compiler failure . `package.json` also contains suspicious dependency specs such as `workspace*` rather than `workspace:*` for some workspace packages .

**Affected surfaces:** web app, canvas, preview, page designer, UI-builder integration, release readiness.

**Target pattern:** clean `pnpm install`, build, typecheck, lint, unit tests, route tests, Playwright tests, and release readiness all pass with no checked-in error artifacts.

**Required fix:** fix all frontend errors, correct package specs, remove checked-in error reports, and make typecheck/build/test hard CI gates.

**Required tests:** `pnpm --filter @ghatana/yappc-web-app run typecheck`, `build`, `test`, `test:e2e:a11y`, `test:e2e:visual`, `test:e2e:performance-memory`, plus product-level Gradle checks.

**Cleanup implications:** delete `error_report.txt` and `error_summary.txt` only after the underlying errors are fixed and CI proves clean.

---

### P0-2 Route inventory, route manifest, OpenAPI, and mounted routes are divergent

**Why it matters:** Repeated audits will keep finding “missing” or “fake” capabilities if docs, route manifests, and code disagree.

**Root cause:** YAPPC has multiple route truth sources that are not generated from the mounted router/server definitions.

**Evidence:** Actual `routes.ts` mounts `/preview/builder`, eight first-class phase routes, legacy canvas/preview/deploy/lifecycle routes, and admin routes . The route inventory still describes an older 15-route tree, includes `/p/:projectId/canvas-workspace`, and does not reflect the current phase/admin route structure . The Java server mounts preview-session and page-artifact routes , while `route-manifest.yaml` omits those routes and also omits the generate apply/reject/rollback routes shown in the server/openapi surface  .

**Affected surfaces:** all navigation, API clients, OpenAPI contract tests, route tests, release gates, docs.

**Target pattern:** one generated route/API registry for frontend mounted routes and backend HTTP routes, with OpenAPI and typed clients generated or validated from that registry.

**Required fix:** regenerate route inventory from `routes.ts`; regenerate route manifest from server/router declarations; make OpenAPI parity bidirectional, not just manifest → OpenAPI.

**Required tests:** fail CI when any mounted route lacks a route inventory entry, any server route lacks OpenAPI, any OpenAPI path lacks a mounted/server implementation, or any typed client calls a non-contract route.

**Cleanup implications:** archive stale route inventory docs after regeneration; remove dead references such as `/canvas-workspace`.

---

### P0-3 Authorization is fail-closed at authentication level but not proven end to end

**Why it matters:** YAPPC handles projects, generated artifacts, previews, source imports, and potentially code/deployment actions. Workspace/project/artifact isolation must be backend-enforced.

**Root cause:** Auth exists, but the audited evidence shows coarse principal/tenant mapping and frontend-derived scope in multiple flows. Full resource-level enforcement was not verified from current code/docs.

**Evidence:** `YappcApiAuthFilter` requires `YAPPC_API_KEYS` and tenant mappings and rejects missing/invalid credentials . But default roles can come from env with default `admin`, and the filter itself does not prove project/workspace/artifact permission checks. The project shell falls back to a `guest` user, uses `anonymous` for artifact saves, marks workspace entries as `isOwner: true`, and navigates to feature-flagged share/export actions from the frontend . Page artifact persistence sends tenant/workspace/project IDs from the client in headers, which must be validated server-side rather than trusted .

**Affected surfaces:** workspaces, projects, admin routes, phase actions, generation review, run, page artifacts, preview sessions, export.

**Target pattern:** backend derives actor and tenant from authenticated principal, resolves workspace/project/artifact access server-side, and rejects frontend-supplied scope mismatches.

**Required fix:** introduce a canonical scope resolver/permission guard used by every controller; remove guest/anonymous mutation paths from production; ensure admin routes are backend-gated, not only UI-gated.

**Required tests:** role matrix, workspace/project isolation, artifact isolation, admin route denial, export denial, preview-session denial, generation review denial, page-artifact scope mismatch tests.

**Cleanup implications:** remove frontend-only ownership assumptions and dead gated actions until backend authorization is proven.

---

### P0-4 Lifecycle phases are mounted but not yet semantically production-grade

**Why it matters:** The product promise is an 8-phase AI-native lifecycle, not just eight tabs.

**Root cause:** The project shell and cockpit are structurally unified, but several actions are still surface/audit-only or rely on hardcoded runtime defaults.

**Evidence:** The README states the 8-phase lifecycle but also says later phases are in active implementation and Run/Learn/Evolve are low maturity . The canonical model requires lifecycle packets from persisted project, activity, readiness preview, dashboard actions, and typed `yappcApi` methods . The phase cockpit is a generic route for all phases with blockers/evidence/governance panels , but action execution shows shape/observe/learn/evolve primarily emit audit events and open supporting surfaces; generate uses `diff: 'initial-review'`; run uses hardcoded `yappc-run`, frontend-provided tenant context, `previous-stable`, and `staging` defaults .

**Affected surfaces:** Validate, Generate, Run, Observe, Learn, Evolve, governance trace, run handoff.

**Target pattern:** each phase has backend-owned state transitions, explicit policies, review gates, generated artifacts, run IDs, evidence, rollback metadata, and audited outcomes.

**Required fix:** replace hardcoded phase action defaults with backend phase-action contracts; make every phase action either a real backend operation or an explicitly read-only/supporting surface.

**Required tests:** phase transition golden tests, blocked/ready state tests, generate review tests, run rollback/promote tests, audit trace tests.

**Cleanup implications:** remove phase CTAs that are not backed by durable operations or relabel them as review-only surfaces.

---

### P0-5 Prompt → plan → confirm → generate → preview → download is not yet proven end to end

**Why it matters:** This is the core YAPPC user journey.

**Root cause:** Pieces exist, but the audited evidence does not show one durable, governed path from user intent through generated artifact, preview, review, and export/download.

**Evidence:** The README lists code generation, scaffolding, knowledge graph, visual canvas, refactoring, observability, and agentic workflows as capabilities, while also marking implementation partial . The canonical artifact model defines lifecycle/generated/imported/page artifacts and requires provenance-bearing generated outputs and review decisions . The phase action service can run generation and review decisions, but the diff request uses a literal `initial-review` and missing run ID is handled as a weak accepted-but-unreviewable result .

**Affected surfaces:** Intent, Shape, Generate, Preview, Export, artifact review.

**Target pattern:** one canonical workflow where intent creates versioned plan, user confirms, backend generates artifacts with provenance, preview session renders exactly those artifacts, review applies/rejects/rolls back, and export/download uses the same reviewed artifact set.

**Required fix:** implement a durable generation run model with plan ID, artifact IDs, provenance, review status, preview session, and export bundle parity.

**Required tests:** full Playwright journey plus API integration tests verifying prompt → plan → generated artifact → preview → review → export parity.

**Cleanup implications:** remove or hide generation/demo surfaces that cannot participate in this canonical journey.

---

### P1-6 Canvas/page builder persistence is improved but still has recovery/authority ambiguity

**Why it matters:** Users must trust that canvas/page changes are durable, scoped, conflict-safe, and not silently replaced by stale local drafts.

**Root cause:** The persistence abstraction is strong, but fallback/local draft behavior needs stricter production semantics and visible sync state.

**Evidence:** The page artifact persistence layer has HTTP and LocalStorage adapters, tenant/workspace/project scope, classification-limited local drafts, TTL, conflict handling, graph ingest, and a resilient fallback adapter . The canonical builder model requires page documents to store governance metadata, preview trust, operation history, sync state, compatibility/migration, and operation logs .

**Affected surfaces:** canvas, page designer, autosave, import, reload, overwrite, conflict handling.

**Target pattern:** server document is authoritative; local drafts are clearly labeled recovery-only and never silently become production truth.

**Required fix:** add explicit recovered-draft UX, telemetry, sync banners, forced reconciliation before preview/export, and server-side validation for all scope headers.

**Required tests:** offline recovery, conflict, expired draft, forbidden classification, graph ingest failure, server authoritative reload.

**Cleanup implications:** remove older/local-only persistence paths after the resilient adapter is fully proven.

---

### P1-7 Preview runtime has the right direction but is not fully production-proven

**Why it matters:** Preview is a security and trust boundary, not just an iframe.

**Root cause:** Secure preview-session work exists, but it still needs full route/server/session/token/runtime proof and cleanup of frontend errors.

**Evidence:** `LivePreviewPanel` issues secure preview sessions, uses sandbox profiles, preview execution policy, CSP, typed preview host service, viewport/theme/locale messages, and blocks preview without context unless dev preview mode is explicitly enabled . The canonical preview model defines trust levels and rules for untrusted imports, privacy-sensitive data, runtime errors, and compiler health checks . The route exists as `/preview/builder` in `routes.ts` , and server preview-session endpoints exist . However, error reports list preview files, and route-manifest drift means the preview contract is not yet cleanly governed .

**Affected surfaces:** preview builder, LivePreviewPanel, preview sessions, sandbox/CSP, Observe phase.

**Target pattern:** preview session tokens are short-lived, backend-validated, artifact-scoped, policy-scoped, and fully covered by security/a11y/e2e tests.

**Required fix:** include preview routes in route manifest/OpenAPI parity, fix preview type/build issues, validate token expiry and artifact authorization, and surface runtime errors in Observe.

**Required tests:** preview token issue/validate/expiry, unauthorized artifact preview, sandbox/CSP headers, postMessage origin validation, validation-blocked preview, locale/theme/viewport sync.

**Cleanup implications:** remove dev preview mode from production bundles or hard-fail unless explicitly development-only.

---

### P1-8 Artifact compiler/decompiler/import path is not contract-clean end to end

**Why it matters:** Brownfield import, artifact graph, residual islands, and generated artifact round-trip are core to YAPPC’s power.

**Root cause:** Canonical docs, OpenAPI, server routes, and route manifest do not show one clean mounted/contracted import/decompiler pipeline.

**Evidence:** The canonical docs define `/api/v1/yappc/artifact/import-source`, governed source imports, residual islands, compiler runtime health checks, and preview blocking when runtime dependencies are unavailable . OpenAPI includes `artifact/import-source` and residual island review surfaces . The Java server evidence shows artifact graph ingest/analyze/merge/query/residual routes, but not the `import-source` route in the fetched server route list . The route manifest lists graph endpoints but not import-source or residual review endpoints .

**Affected surfaces:** import source, artifact graph, residual islands, compiler health, page artifact graph ingest.

**Target pattern:** one governed compiler/decompiler API with health, import job, residual review, registry promotion, graph persistence, preview trust, and round-trip tests.

**Required fix:** align OpenAPI/server/manifest/client for source import and residual review; make compiler runtime health a hard prerequisite; add round-trip golden tests.

**Required tests:** source import job lifecycle, residual island preservation, registry candidate promotion, graph ingest/query, unsupported component fallback, preview trust block.

**Cleanup implications:** archive standalone implementation plans once canonical compiler docs and tests are current.

---

### P1-9 API client ownership is still too broad and mixed

**Why it matters:** Typed clients should reduce drift, not become a dumping ground for every latent or unrelated page surface.

**Root cause:** The client is canonical for REST, but it includes many page-level surfaces and still contains raw fetch/migration candidates.

**Evidence:** The API client states all new REST calls must use the client and GraphQL-owned domains must use the GraphQL client instead of this REST client . Yet phase Run calls `yappcApi.workflows.start/status` from the phase action service . The same client includes billing, operations, collaboration, anomaly, canvas, and other surfaces, some tied to latent pages or broad product scope .

**Affected surfaces:** frontend API calls, workflow calls, latent pages, GraphQL/REST split.

**Target pattern:** REST client only owns mounted REST surfaces; GraphQL client owns GraphQL domains; latent pages do not grow the production client.

**Required fix:** split client by mounted product domains, move latent/unmounted APIs behind explicit feature modules, and enforce client usage with lint/contract tests.

**Required tests:** no raw fetch outside API infrastructure, no GraphQL-owned calls in REST client, no client method without OpenAPI/GraphQL contract.

**Cleanup implications:** remove unused client sections for unmounted pages or mark them experimental behind feature flags.

---

### P1-10 Release gates are evidence-presence gates, not complete production gates

**Why it matters:** A release gate that checks files exist can pass while tests fail.

**Root cause:** Current release readiness script validates presence of tests, snapshots, scripts, and security header strings rather than executing or validating results.

**Evidence:** `verify-release-readiness.mjs` checks required evidence files exist, required scripts exist, checklist mentions the command, and preview header strings are present in route/test files . Gradle governance checks are basic, some checks skip when configuration cache is enabled, and `checkNoGetResultInTests` only scans a limited test set and warns rather than fails .

**Affected surfaces:** CI, release, test confidence, route/API parity, architecture governance.

**Target pattern:** release gate runs and verifies build/typecheck/lint/tests/e2e/security/a11y/performance/API parity outcomes.

**Required fix:** upgrade readiness gate from evidence-presence to execution/results validation; make skipped gates explicit non-release mode only.

**Required tests:** CI must publish test artifacts and fail if any required gate is skipped, missing, stale, or only presence-checked.

**Cleanup implications:** remove “fast evidence only” language from production release workflow or rename it as preflight.

---

### P2-11 i18n, accessibility, and low-cognitive-load UX are partial

**Why it matters:** YAPPC’s UI goal is no cognitive load with native i18n/a11y, especially for complex builder and lifecycle flows.

**Root cause:** Some i18n/a11y infrastructure exists, but many mounted components still use hardcoded English strings and evidence gates only check test file presence.

**Evidence:** `LivePreviewPanel` uses an i18n provider for some labels but also contains many hardcoded English UI strings and `toLocaleTimeString()` without explicit locale handling . The phase cockpit and project shell also contain substantial hardcoded user-facing text  . The release-readiness script checks accessibility evidence files exist, not that accessibility actually passes .

**Affected surfaces:** phase cockpit, project shell, preview, canvas, command palette, builder.

**Target pattern:** all mounted user-facing text is extracted; dates/numbers/currency use locale services; keyboard/screen-reader coverage is tested for phase navigation, canvas, modal/drawer, preview, and command surfaces.

**Required fix:** enforce i18n extraction and a11y tests for mounted routes; add keyboard alternatives for drag/drop/canvas operations.

**Required tests:** i18n grep gate, RTL/locale fixture tests, axe/Playwright keyboard navigation, focus management tests.

**Cleanup implications:** remove ad hoc English strings from production components.

---

### P1-12 Repository cleanup and documentation consolidation remain incomplete

**Why it matters:** Stale docs and latent code are causing repeated audit noise and false product-scope assumptions.

**Root cause:** YAPPC has canonical docs, but it still contains old audits/TODOs/plans, latent page surfaces, checked-in error reports, deprecated compatibility modules, and migration-in-progress architecture.

**Evidence:** Latent pages are explicitly documented as non-product until mounted/validated . The route inventory is stale relative to actual routes  . Core architecture identifies `spi` as a deprecated compatibility wrapper and `agents` as consolidation-in-progress toward `yappc-agents` . Search results also show audit/TODO and implementation-plan docs such as `docs/audits/yappc-todos.md` and `ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md` still present  .

**Affected surfaces:** docs, route scope, audits, architecture, migration planning, frontend pages.

**Target pattern:** minimal canonical docs, stale docs archived, latent pages either mounted/validated or deleted/archived, compatibility modules removed after migration.

**Required fix:** run a cleanup pass after functional remediation, not before; classify each file as keep/merge/archive/delete.

**Required tests:** dead route detection, unused export detection, route inventory check, doc link check, module boundary checks.

**Cleanup implications:** see cleanup plan below.

---

## 7.3 Migration / Completeness Matrix

| Surface                    | Route Registry | Scope / Auth | Typed API | Canonical Model | Builder / Compiler / Preview | Persistence | i18n/a11y | Tests | Cleanup | Status                          |
| -------------------------- | -------------: | -----------: | --------: | --------------: | ---------------------------: | ----------: | --------: | ----: | ------: | ------------------------------- |
| Product entry/dashboard    |             🟡 |           🟡 |        🟡 |              🟡 |                            ⚫ |          🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                      |
| Workspace/project setup    |             🟡 |           🟡 |        🟡 |               ✅ |                            ⚫ |          🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                      |
| Project shell / phase tabs |              ✅ |           🟡 |        🟡 |               ✅ |                            ⚫ |          🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                      |
| Intent phase               |              ✅ |           🟡 |        🟡 |               ✅ |                            ⚫ |          🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                      |
| Shape / canvas phase       |              ✅ |           🟡 |        🟡 |               ✅ |                           🟡 |          🟡 |        🟡 |    🟡 |      🔴 | 🟡 Partial                      |
| Validate phase             |              ✅ |           🟡 |        🟡 |               ✅ |                            ⚫ |          🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                      |
| Generate phase             |              ✅ |           🟡 |        🟡 |               ✅ |                           🟡 |          🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                      |
| Run phase                  |              ✅ |           🔴 |        🟡 |              🟡 |                            ⚫ |          🟡 |        🟡 |    🟡 |      🟡 | 🔴 Missing production semantics |
| Observe / preview          |              ✅ |           🟡 |        🟡 |               ✅ |                           🟡 |          🟡 |        🟡 |    🟡 |      🔴 | 🟡 Partial                      |
| Learn / Evolve             |              ✅ |           🟡 |        🟡 |              🟡 |                            ⚫ |          🟡 |        🟡 |    🟡 |      🟡 | 🔴 Low maturity                 |
| Page designer / builder    |             🟡 |           🟡 |        🟡 |               ✅ |                           🟡 |          🟡 |        🟡 |    🟡 |      🔴 | 🟡 Partial                      |
| Page artifact persistence  |             🟡 |           🟡 |        🟡 |               ✅ |                           🟡 |          🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                      |
| Artifact compiler/import   |             🔴 |           🟡 |        🟡 |               ✅ |                           🟡 |          🟡 |        🟡 |    🟡 |      🟡 | 🔴 Contract drift               |
| Admin prompt/AB/flags      |              ✅ |           🔴 |        🟡 |              🟡 |                            ⚫ |          🟡 |        🟡 |    🟡 |      🟡 | 🔴 Needs backend gating proof   |
| API contracts              |             🔴 |           🟡 |        🟡 |              🟡 |                           🟡 |          🟡 |         ⚫ |    🟡 |      🔴 | 🔴 Drift                        |
| Release gates              |              ⚫ |            ⚫ |         ⚫ |              🟡 |                           🟡 |          🟡 |        🟡 |    🟡 |      🔴 | 🟡 Presence-gated               |
| Repository cleanup         |             🔴 |            ⚫ |         ⚫ |              🟡 |                           🟡 |           ⚫ |         ⚫ |     ⚫ |      🔴 | 🔴 Required                     |

---

## 7.4 File-Level Gaps

| Gap                                             | Where                                                                       | Root blocker | Required fix                                                                                                | Tests / validation                 |
| ----------------------------------------------- | --------------------------------------------------------------------------- | ------------ | ----------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| Checked-in frontend error inventories           | `products/yappc/frontend/web/error_report.txt`, `error_summary.txt`         | P0-1         | Fix underlying errors, then delete these files                                                              | typecheck/build/lint/test clean    |
| Suspicious workspace dependency specs           | `products/yappc/frontend/web/package.json`                                  | P0-1         | Change `workspace*` to valid workspace protocol, validate install                                           | pnpm install + lockfile validation |
| Route inventory stale                           | `YAPPC_MOUNTED_ROUTE_INVENTORY_2026-04-20.md`                               | P0-2         | Regenerate from `routes.ts`; include phase/admin/preview-builder routes                                     | `inventory:routes:check`           |
| Route manifest incomplete                       | `docs/api/route-manifest.yaml`                                              | P0-2         | Add preview sessions, page artifacts, generation review, import/review routes or generate from server       | OpenAPI parity both directions     |
| Backend route contract drift                    | `YappcHttpServer.java`, `openapi.yaml`, `route-manifest.yaml`               | P0-2         | Make route declarations, OpenAPI, and typed client one contract chain                                       | route/API contract tests           |
| Guest/anonymous mutation fallback               | `_shell.tsx`                                                                | P0-3         | Block mutating actions when unauthenticated; backend-auth required                                          | unauthenticated mutation denial    |
| Workspace ownership hardcoded                   | `_shell.tsx`                                                                | P0-3         | Use backend capability model, not `isOwner: true`                                                           | role/capability tests              |
| Dead feature route                              | `_shell.tsx` share action navigates to `/share`, not mounted in `routes.ts` | P0-2/P1-12   | Hide until route exists or mount with contract                                                              | route traversal test               |
| Hardcoded lifecycle action defaults             | `PhaseCockpitActionService.ts`                                              | P0-4         | Backend-owned action contracts; remove `yappc-run`, `initial-review`, `staging`, `previous-stable` literals | phase action contract tests        |
| Local fallback authority ambiguity              | `pageArtifactPersistence.ts`                                                | P1-6         | Recovery-only UX and forced reconciliation before preview/export                                            | offline/conflict/recovery tests    |
| Preview runtime build/security not fully proven | `LivePreviewPanel.tsx`, `preview-builder.tsx`                               | P1-7         | Fix type/build issues, enforce session scope, validate headers/tokens                                       | preview security/e2e tests         |
| Release gate checks presence, not execution     | `verify-release-readiness.mjs`                                              | P1-10        | Execute/consume real test results; fail on skipped gates                                                    | CI release gate                    |
| Gradle checks are partial/skipped               | `products/yappc/build.gradle.kts`                                           | P1-10        | Make governance checks hard in release profile; remove limited scans                                        | Gradle check with no skipped gates |
| Stale audit/planning docs                       | `docs/audits/yappc-todos.md`, `ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md`    | P1-12        | Merge into canonical tracker or archive                                                                     | doc inventory/link check           |

---

## 7.5 Prioritized Implementation Sequence

### 1. Release integrity first

**Goal:** make the current codebase buildable and testable.
**Main files:** `package.json`, error reports, canvas/preview files listed in reports, build scripts.
**Outcome:** no checked-in error artifacts; build/typecheck/lint/test pass.
**Acceptance:** CI fails on any type/build/lint error.

### 2. Canonical route/API contract

**Goal:** eliminate route/API drift.
**Main files:** `routes.ts`, route inventory docs, `YappcHttpServer.java`, `route-manifest.yaml`, `openapi.yaml`, `client.ts`.
**Outcome:** generated/validated route and API registry.
**Acceptance:** every mounted route and server route is documented, tested, and typed.

### 3. Scope and authorization hardening

**Goal:** backend-enforced workspace/project/artifact authorization.
**Main files:** auth filter, controllers, project/page artifact/preview/generation endpoints, frontend shell.
**Outcome:** frontend no longer supplies trusted scope; backend derives and validates scope.
**Acceptance:** role/scope denial tests pass for all sensitive surfaces.

### 4. Lifecycle phase action hardening

**Goal:** make all eight phase CTAs semantically real or explicitly read-only.
**Main files:** `PhaseCockpitActionService.ts`, backend lifecycle/run/generate controllers.
**Outcome:** no hardcoded run/generation defaults; all operations are auditable and durable.
**Acceptance:** phase transition/generation/run golden tests pass.

### 5. Prompt → plan → generate → preview → review → export flow

**Goal:** one coherent product creation journey.
**Main files:** intent/shape/generate/preview/export routes and APIs.
**Outcome:** generated artifacts have provenance and review status; preview/export use same artifact set.
**Acceptance:** Playwright full journey and API parity tests pass.

### 6. Builder/page artifact persistence

**Goal:** make page artifacts durable, scoped, conflict-safe, and recoverable.
**Main files:** `pageArtifactPersistence.ts`, page artifact controller, canvas/page designer.
**Outcome:** server authoritative state with explicit local recovery.
**Acceptance:** conflict/offline/recovery/forbidden-classification tests pass.

### 7. Artifact compiler/decompiler/import integration

**Goal:** governed source import and round-trip artifact graph.
**Main files:** compiler services, import workflow, artifact graph controller, OpenAPI.
**Outcome:** import-source, graph, residual island, registry promotion, and preview trust are contract-clean.
**Acceptance:** round-trip golden tests and residual island tests pass.

### 8. Preview/runtime security

**Goal:** secure, scoped, testable preview runtime.
**Main files:** `LivePreviewPanel.tsx`, `preview-builder.tsx`, preview session API/controller.
**Outcome:** session-scoped preview with CSP/sandbox/postMessage validation.
**Acceptance:** token expiry, unauthorized artifact, CSP, sandbox, and origin tests pass.

### 9. i18n/a11y/UX hardening

**Goal:** no cognitive load, accessible builder/lifecycle UX.
**Main files:** phase cockpit, project shell, preview, canvas, command palette, i18n provider.
**Outcome:** all mounted text extracted; keyboard/screen-reader flows covered.
**Acceptance:** i18n grep and Playwright/axe tests pass.

### 10. Cleanup and consolidation

**Goal:** remove audit noise and stale code.
**Main files:** latent pages, stale docs, deprecated `spi`, agent migration docs, error reports.
**Outcome:** minimal canonical doc/code surface.
**Acceptance:** route inventory, doc inventory, dead-code checks, and module-boundary tests pass.

---

## 7.6 Regression and Release Gates

Minimum gates before production readiness:

1. Clean install/build/typecheck/lint/test for YAPPC frontend.
2. Product-level Gradle check with no skipped release-critical checks.
3. Mounted route traversal from `/`, `/projects`, `/p/:projectId/intent` through all eight phase routes.
4. Route inventory generated from `routes.ts`.
5. Backend route manifest generated/validated against `YappcHttpServer` and other YAPPC servers.
6. OpenAPI parity both directions.
7. Typed client route coverage; no route-local raw fetch except inside API infrastructure.
8. Workspace/project/artifact role matrix tests.
9. Admin route backend-denial tests.
10. Preview session issue/validate/expiry/unauthorized tests.
11. Page artifact save/load/conflict/offline/recovery tests.
12. Prompt → plan → confirm → generate → preview → review → export E2E.
13. Generate apply/reject/rollback tests with provenance.
14. Run rollback/promote/observe handoff tests.
15. Artifact import/decompiler residual island tests.
16. Privacy redaction tests for preview/export/logs.
17. i18n extraction and locale formatting tests.
18. Accessibility tests for phase tabs, drawers, modals, canvas alternatives, command palette, and preview.
19. No checked-in error report files.
20. No latent page treated as shipped product unless mounted, contracted, and tested.

---

# Repository Cleanup Plan

| Priority | Classification                   | Path                                                                           | Reason                                                                                 | Evidence                                                                       | Safe Fix                                                | Tests/Validation        |
| -------- | -------------------------------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------- | ----------------------- |
| P0       | Delete after fix                 | `products/yappc/frontend/web/error_report.txt`                                 | Checked-in error artifact creates audit noise and implies unresolved build/type issues | Listed many files/errors                                                       | Fix errors, delete file                                 | typecheck/build pass    |
| P0       | Delete after fix                 | `products/yappc/frontend/web/error_summary.txt`                                | Same as above                                                                          | Lists critical canvas/preview/platform files                                   | Fix errors, delete file                                 | typecheck/build pass    |
| P0       | Replace                          | `products/yappc/docs/architecture/YAPPC_MOUNTED_ROUTE_INVENTORY_2026-04-20.md` | Stale route inventory                                                                  | Conflicts with actual `routes.ts`                                              | Regenerate from router                                  | route inventory check   |
| P0       | Replace                          | `products/yappc/docs/api/route-manifest.yaml`                                  | Missing mounted server routes                                                          | Server mounts more routes than manifest lists                                  | Generate from server/router                             | OpenAPI parity          |
| P0       | Replace                          | `products/yappc/frontend/web/package.json` dependency specs                    | Possible invalid workspace specs                                                       | `workspace*` entries present                                                   | Normalize workspace protocol                            | pnpm install            |
| P1       | Merge/archive                    | `products/yappc/docs/audits/yappc-todos.md`                                    | Historical audit/TODO doc should not be canonical                                      | File exists under audits                                                       | Merge active items into canonical tracker, archive rest | doc inventory           |
| P1       | Merge/archive                    | `products/yappc/ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md`                      | Implementation plan should not compete with canonical compiler docs                    | File exists at root                                                            | Merge current truth into architecture/design docs       | doc link check          |
| P1       | Deprecate/remove after migration | `products/yappc/core/spi`                                                      | Deprecated compatibility wrapper                                                       | Core architecture identifies it as deprecated                                  | Move consumers to `yappc-shared`; remove wrapper        | Gradle + ArchUnit       |
| P1       | Merge                            | `core/agents` → `yappc-agents`                                                 | Consolidation still in progress                                                        | Core architecture says migration target is `yappc-agents`                      | Complete migration and enforce boundaries               | module boundary tests   |
| P1       | Delete/archive                   | obsolete latent pages under `frontend/web/src/pages/**`                        | Large unmounted surface causes false scope assumptions                                 | Latent page doc says pages are not product capability until mounted/validated  | Classify keep/archive/delete                            | dead route/import check |
| P2       | Replace                          | `verify-release-readiness.mjs` production semantics                            | Presence-only gate is insufficient                                                     | Script checks file/script/header presence                                      | Split preflight vs release gate                         | CI release proof        |
| P2       | Replace                          | Gradle governance checks                                                       | Basic/skipped/limited scans                                                            | Build script documents skipped/limited checks                                  | Hard-fail in release profile                            | Gradle release check    |

---

## Canonical Docs Matrix

| Doc / doc family                         | Keep | Merge | Archive | Delete | Notes                                                                  |
| ---------------------------------------- | ---: | ----: | ------: | -----: | ---------------------------------------------------------------------- |
| `products/yappc/README.md`               |    ✅ |    🟡 |         |        | Keep, but update maturity/status after every release gate              |
| `docs/CORE_ARCHITECTURE.md`              |    ✅ |    🟡 |         |        | Keep as module boundary reference; update after `spi`/agents migration |
| `architecture/YAPPC_CANONICAL_MODELS.md` |    ✅ |       |         |        | Strong canonical model; keep current                                   |
| Mounted route inventory                  |      |     ✅ |      🟡 |        | Regenerate from `routes.ts`; archive stale dated copy                  |
| Latent page surface doc                  |    ✅ |    🟡 |         |        | Keep until latent pages are deleted/archived                           |
| `docs/api/openapi.yaml`                  |    ✅ |    🟡 |         |        | Keep but generate/validate against server and client                   |
| `docs/api/route-manifest.yaml`           |      |     ✅ |         |        | Replace with generated server route manifest                           |
| Release readiness checklist              |    ✅ |    🟡 |         |        | Distinguish preflight from production release gate                     |
| Audit/TODO docs                          |      |     ✅ |       ✅ |        | Merge active work into tracker; archive historical audits              |
| Standalone implementation plans          |      |     ✅ |       ✅ |        | Merge into canonical architecture/design or archive                    |
| Security/privacy docs                    |    ✅ |    🟡 |         |        | Keep, but align with backend enforcement tests                         |
| i18n/a11y/testing docs                   |    ✅ |    🟡 |         |        | Keep only if tied to executable gates                                  |

---

## Final Cleanup Checklist

* [ ] Frontend error reports removed after errors are fixed.
* [ ] `pnpm install`, build, typecheck, lint, unit tests, and E2E tests pass.
* [ ] Route inventory regenerated from actual `routes.ts`.
* [ ] Backend route manifest regenerated from server/router code.
* [ ] OpenAPI, typed client, and server routes are contract-aligned.
* [ ] Dead or stale latent pages archived/deleted or explicitly mounted with tests.
* [ ] Guest/anonymous mutation paths removed from production.
* [ ] Frontend-only ownership assumptions removed.
* [ ] Share/export routes hidden unless backend/mounted/authorized.
* [ ] Local page-artifact drafts marked recovery-only.
* [ ] Preview session routes included in route/API parity.
* [ ] Artifact import/compiler/decompiler contracts consolidated.
* [ ] Deprecated `spi` compatibility path removed after migration.
* [ ] `core/agents` consolidation completed or explicitly tracked.
* [ ] Old audit/TODO/implementation-plan docs merged or archived.
* [ ] Release-readiness script upgraded from evidence presence to actual gate results.
* [ ] No hidden fallback runtime paths remain.
* [ ] No production stubs/demo/no-op paths are enabled without explicit feature flags and release documentation.
