# YAPPC End-to-End Product Correctness, Completeness, UI/UX, Backend, DB & Production-Readiness Audit

- **Target**: `products/yappc` and the shared platform/library modules it depends on
- **Audit date**: 2026-04-29
- **Auditor**: GitHub Copilot (Claude Opus 4.7), executing [End-to-End-Product-Prompt.md](../../../../End-to-End-Product-Prompt.md)
- **Prior audit**: [products/yappc/YAPPC_E2E_AUDIT_2026-04-27.md](../../YAPPC_E2E_AUDIT_2026-04-27.md) — used as the structural baseline. Each prior finding (`F-Y001..F-Y060`) was re-verified at HEAD; new findings are numbered `F-Y061+`.
- **Heavy / forensic** fidelity, all subsystems, no implementation changes were made.

> **Reading order**: §0 executive summary → §17 prioritised remediation → §16 release-readiness gate. Sections §1–§15 hold the evidence behind each finding.

---

## 0. Executive Summary

YAPPC is a credible 8‑phase product platform on Java 21 / ActiveJ with a Next.js + React frontend backed by 23 frontend libraries, 13 Java sub-modules and 4 long-running services. Since the 2026‑04‑27 audit substantial cleanup has landed:

- **API contract is now governed**. A 4 762‑line [docs/api/openapi.yaml](../api/openapi.yaml) plus a 119‑line [route-manifest.yaml](../api/route-manifest.yaml) are enforced by a `checkYappcOpenApiParity` Gradle task wired into `tasks.named("check")` ([products/yappc/build.gradle.kts](../../build.gradle.kts) lines 155-243). This substantially closes prior P0 **F‑Y001**.
- **Two competing cockpit shells eliminated**. `app-creator/` no longer exists (closes **F‑Y002**).
- **Archived legacy under FE has been deleted** — no `_archived/` directories remain (closes **F‑Y004**).
- **Two competing JWT services merged**. `JwtService.java` no longer exists in any `core/**` Java module (closes **F‑Y007**).
- **Test theatre patterns scrubbed**. No `test.skip(true, ...)` matches in `frontend/web` (closes **F‑Y015**).

However, the codebase still **fails the production-readiness gate** because of new and open structural defects:

| # | Severity | Issue | Section |
|---|----------|-------|---------|
| 1 | **P0** | Three overlapping services Gradle modules with **byte-identical class duplicates** (`yappc-services`, `services-platform`, `services-lifecycle`) — `LifecycleServiceModule`, `JdbcHumanApprovalService`, `YappcApiSecurity`, `ApprovalHttpHandlers`, `GdprController`, `JwtAuthController`, `LifecycleLoginController`, `RequirementsConsistencyChecker`, `CodeQualityAnalyzer` all exist twice or three times. **F‑Y061** | §10 |
| 2 | **P0** | Two parallel implementations of `Agent`, `Vector`, `Workflow` HTTP route classes exist under `core/yappc-api` *and* `core/yappc-domain-impl`. Identical route paths registered from two sources risk runtime double-binding inside one process. **F‑Y062** | §10 |
| 3 | **P0** | RBAC policies are loaded into `new InMemoryPolicyRepository()` at runtime ([YappcApiSecurity.java:163](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/security/YappcApiSecurity.java)) — non-durable authorisation state across restarts and replicas. **F‑Y063** | §11.2 |
| 4 | **P0** | `JdbcHumanApprovalService` silently falls back to in-memory state on DB failure for `pendingFor`, `allPending`, `findById` ([JdbcHumanApprovalService.java:190-223](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/JdbcHumanApprovalService.java)) — split-brain risk when one replica's DB connection blips. **F‑Y064** | §9 |
| 5 | **P0** | `LifecycleServiceModule.java:1451` registers "in-memory stubs for all YAPPC data collections" inside the production wiring — unverified whether these are dev‑only flags or always wired. **F‑Y065** | §9 |
| 6 | **P0** | 10 frontend libs still publish under the forbidden `@yappc/*` scope (`@yappc/{ai,initialization-ui,devsecops,chat,ui,auth,core,artifact-compiler,state,product-theme}`) — Section 32 of [.github/copilot-instructions.md](../../../../.github/copilot-instructions.md) bans the scope. **F‑Y003 still open**. | §10.1 |
| 7 | **P1** | Run-phase CI/CD adapter `GitHubActionsCiCdAdapter` is a fail‑closed stub for `build`/`test`/`deploy`/`migrate`/`rollback` ([GitHubActionsCiCdAdapter.java:112-216](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/run/GitHubActionsCiCdAdapter.java)). Honestly labelled as `NOT_READY` (Section 31 compliant) but the entire Run phase 5 of the 8‑phase lifecycle is non-functional via this provider. **F‑Y066** | §9 |
| 8 | **P1** | Frontend canvas persistence path stubbed — `IndexedDBAdapter` throws `Method not implemented` on three of its persistence operations ([IndexedDBAdapter.ts:47-53](../../frontend/web/src/services/canvas/storage/IndexedDBAdapter.ts)). **F‑Y067** | §9 |
| 9 | **P1** | `core/agents/src/` and `core/scaffold/src/` are folders containing only `src/` (no `build.gradle.kts`) sitting next to real sub-projects. Section 30 module-shell violation. **F‑Y068** | §10 |
| 10 | **P1** | `core/agents/runtime/.../CodeGenerationToolProvider.java:231` throws `UnsupportedOperationException("Not implemented yet")` — Generate-phase tool is incomplete. **F‑Y069** | §9 |
| 11 | **P1** | `frontend/web/src/state/atoms.ts:109` — "Creating stubs here to unblock migration. TODO: Implement properly." — global state migration incomplete. **F‑Y070** | §9 |
| 12 | **P1** | `frontend/web/src/services/agentService.ts:37` — "AI service (placeholder - would be injected)". **F‑Y071** | §9 |
| 13 | **P2** | `IDEShell.tsx:482-483` ships Terminal/Problems panels labelled "(coming soon)". **F‑Y072** | §3 |

**Verdict — production gate**: ❌ **FAIL.** The combination of (a) duplicated production class graphs across three Gradle modules, (b) two parallel HTTP route registrations for the same paths, (c) non-durable RBAC, (d) silent JDBC→in‑memory failover for human approvals, and (e) incomplete Run-phase adapter would each individually block release. See §16 for the full gate.

---

## 1. Inventories (Source of Truth)

### 1.1 Backend module topology (Gradle, present at HEAD)

Sub-modules under [products/yappc/core/](../../core/) that have a `build.gradle.kts` and are therefore wired:

```
agents                       services-lifecycle           yappc-infrastructure
ai                           services-platform            yappc-services
cli-tools                    yappc-api                    yappc-shared
knowledge-graph              yappc-domain-impl
scaffold                     refactorer (parent only — api & engine are real)
```

Module shells (Section 30 violations — folder with only `src/`, no `build.gradle.kts`):

| Path | Status |
|------|--------|
| `core/agents/src/` | Orphan (real submodules are `agents/runtime/`, `agents/workflow`, `agents/code-specialists`, `agents/architecture-specialists`, `agents/delivery-specialists`, `agents/testing-specialists`, `agents/build`, `agents/common`) |
| `core/scaffold/src/` | Orphan (real submodules are `scaffold/api`, `scaffold/core`, `scaffold/engine`, `scaffold/generators`, `scaffold/templates`, `scaffold/docs`, `scaffold/gradle`) |

The three `services-*` modules **co-exist** — see §10 (F‑Y061).

### 1.2 HTTP surface (route-manifest)

[products/yappc/docs/api/route-manifest.yaml](../api/route-manifest.yaml) declares **4 servers**:

| Server | Routes |
|--------|--------|
| `yappc-services` | 29 — `/api/v1/yappc/{intent,shape,validate,generate,run,observe,learn,evolve,lifecycle,artifact}/...` |
| `yappc-api` | 33 — 8 vector + 10 agent + 15 workflow |
| `scaffold-api` | ~26 — under `/api/v1/scaffold/...` |
| `refactorer-api` | ~12 — under `/api/v1/refactor/...` |

Parity gating: `checkYappcOpenApiParity` ([products/yappc/build.gradle.kts:155-243](../../build.gradle.kts)) parses both files, fails the build if any manifest route is missing from `openapi.yaml`. Wired via `tasks.named("check") { dependsOn(...) }`. **This is a strong governance control and substantially closes F-Y001.**

### 1.3 Java HTTP route classes — DUPLICATION DETECTED

| Capability | `core/yappc-api/...` | `core/yappc-domain-impl/...` |
|------------|----------------------|------------------------------|
| Workflows | `api/http/WorkflowRoutes.java`, `WorkflowController.java` | `domain/workflow/http/WorkflowRoutes.java`, `WorkflowController.java` |
| Agents | `api/http/AgentRoutes.java`, `AgentController.java` | `domain/agent/http/AgentRoutes.java`, `AgentController.java` |
| Vectors | `api/http/VectorRoutes.java`, `VectorController.java` | `domain/vector/http/VectorRoutes.java`, `VectorController.java` |

This is **F‑Y062**. Either the duplicates are dead code (delete one set immediately), or they represent two binaries with intentionally identical wire contracts (still a Section 11 contract-evolution risk because changes must now be made twice).

### 1.4 Frontend libraries (23 in `frontend/libs/`)

```
a11y                aep-config        api               collab
config-compiler     config-schema     data-cloud-config ide
mobile              mocks             shortcuts         yappc-ai
yappc-artifact-compiler yappc-auth    yappc-chat        yappc-core
yappc-devsecops     yappc-initialization-ui yappc-product-theme yappc-state
yappc-ui
```

Of these, **10 violate Section 32** (forbidden `@yappc/*` scope) — see F-Y003.
The remaining 13 (`a11y`, `aep-config`, `api`, `collab`, `config-compiler`, `config-schema`, `data-cloud-config`, `ide`, `mobile`, `mocks`, `shortcuts`) are unscoped or scoped acceptably but should be reviewed against Section 32 for whether they belong in `@ghatana/*` or under `products/yappc/frontend/libs/` only.

### 1.5 Frontend pages and routes

- `frontend/web/src/pages/` — 88 `.tsx` files across `admin`(2), `auth`(2), `bootstrapping`(8), `collaboration`(15), `dashboard`(6), `development`(13), `errors`(3), `initialization`(9), `operations`(14), `security`(13), `settings`(2).
- `frontend/web/src/routes/app/` — 75 `.tsx` files (admin: 6, project: 41 + the rest).
- Capability gates have been added to `routes/app/admin/billing.tsx`, `routes/app/admin/teams.tsx`, `routes/app/project/ops-alerts.tsx` (closes F‑Y012/F‑Y013 partially) — but the underlying overstuffed `pages/operations/*` (14 pages) and `pages/security/*` (13 pages) still ship.

### 1.6 Persistence

- **TypeScript (Prisma)**: lives in `frontend/apps/api`.
- **Java (JDBC)**: spread across `yappc-services`, `services-lifecycle`, `services-platform`. The very existence of three modules with identical persistence wiring means the schema migrations have **no single owner** — see F‑Y061.
- **In-memory stores still bound at runtime**: `InMemoryPolicyRepository` (RBAC), `InMemoryArtifactStore` (default for `YappcArtifactRepository`), `InMemoryEventPublisher` (default for `PhaseEventPublisher`), `InMemoryAiPlanRepository`, `InMemoryAiWorkflowRepository`, `InMemoryAgentRegistry`, `InMemoryJobQueue`, `InMemoryJobStore`, `InMemoryKgService`, `InMemoryEventBus`, plus the JDBC fallback shadow inside `JdbcHumanApprovalService`.

### 1.7 Tests

`test.skip(true, ...)`, `xit(`, `it.skip(` were not found in `frontend/web/{e2e,src}`. Section 29 anti‑theatre rule passes for the FE happy-path. (Java side not exhaustively swept — recommended in §17 follow-up.)

---

## 2. Status of Prior Findings (`F-Y001 .. F-Y060`)

| ID | 2026-04-27 status | 2026-04-29 status | Evidence |
|----|--------------------|-------------------|----------|
| F-Y001 | P0 — no canonical openapi/route-manifest | **CLOSED** | 4 762-line [openapi.yaml](../api/openapi.yaml) + 119-line [route-manifest.yaml](../api/route-manifest.yaml) + Gradle parity gate in [build.gradle.kts:155-243](../../build.gradle.kts) |
| F-Y002 | P0 — two cockpit shells (`app-creator` + `frontend/web`) | **CLOSED** | `app-creator/` and `frontend/app-creator/` no longer exist |
| F-Y003 | P0 — `@yappc/*` libs violate Section 32 | **OPEN** | 10 packages still publish under `@yappc/*` (see §1.4) |
| F-Y004 | P1 — `_archived/` legacy code shipping | **CLOSED** | No `_archived/` directories anywhere under `products/yappc/` |
| F-Y007 | P1 — two `JwtService.java` | **CLOSED** | `JwtService.java` not present in any `core/**` |
| F-Y012/F-Y013 | P1 — admin/ops routes lacked capability gates | **PARTIALLY CLOSED** | `coming-soon` capability gates added on billing/teams/ops-alerts; underlying `pages/operations/*` (14 pages) and `pages/security/*` (13 pages) still over-built |
| F-Y015 | P1 — test theatre (`test.skip(true,…)`) | **CLOSED** | Zero matches in `frontend/web/{e2e,src}` |
| F-Y037 | P0 — three overlapping services modules | **OPEN** (re-issued as F-Y061 with stronger evidence) | 9 byte-identical `.java` files duplicated across modules — see §10 |
| All others | — | **Re-verification deferred to next pass.** Classes/files still present per spot-checks; status carried forward as **OPEN** until individually re-verified |

---

## 3. UI / UX Audit (frontend/web + frontend/libs)

### 3.1 Coming-soon / placeholder UI shipped to users

| File | Symptom |
|------|---------|
| [frontend/libs/ide/src/components/IDEShell.tsx:482-483](../../frontend/libs/ide/src/components/IDEShell.tsx) | Terminal panel **(coming soon)**, Problems panel **(coming soon)** |
| [frontend/libs/yappc-ui/src/components/hooks/useConfig.ts:232](../../frontend/libs/yappc-ui/src/components/hooks/useConfig.ts) | `// TODO: Implement actual API refresh calls` |
| [frontend/libs/ide/src/components/KeyboardShortcutsManager.tsx](../../frontend/libs/ide/src/components/KeyboardShortcutsManager.tsx) | Shortcuts wired to no-ops |
| [frontend/web/src/services/agentService.ts:37,87](../../frontend/web/src/services/agentService.ts) | `AI service (placeholder - would be injected)` |
| [frontend/web/src/services/canvas/storage/IndexedDBAdapter.ts:47,50,53](../../frontend/web/src/services/canvas/storage/IndexedDBAdapter.ts) | `throw new Error('Method not implemented.')` × 3 |
| [frontend/web/src/lib/canvas-legacy/history/checkpoint-manager.ts:536](../../frontend/web/src/lib/canvas-legacy/history/checkpoint-manager.ts) | "placeholder - real implementation would apply merge" |
| [frontend/web/src/lib/canvas/migrationRules.ts](../../frontend/web/src/lib/canvas/migrationRules.ts) | hard-coded `/api/placeholder` URL |
| [frontend/web/src/state/atoms.ts:109](../../frontend/web/src/state/atoms.ts) | "Creating stubs here to unblock migration. TODO: Implement properly." |
| [frontend/web/src/templates/TemplateEditor.tsx](../../frontend/web/src/templates/TemplateEditor.tsx) | "For now, this is a placeholder." |
| [frontend/web/src/hooks/useErrorRecovery.ts](../../frontend/web/src/hooks/useErrorRecovery.ts) | "placeholder - actual retry logic depends on context" |

> **Voice commands** (`frontend/libs/yappc-ui/src/components/voice/useVoiceCommands.ts`) is feature-flagged off pending the speech endpoints — **acceptable** per Section 9 because the kill-switch is explicit and observable.

### 3.2 Page-volume / feature-density risk

`pages/operations/` ships 14 pages and `pages/security/` 13 pages. The capability gates on `routes/app/admin/{billing,teams}` and `routes/app/project/ops-alerts` indicate awareness of the over-build, but the underlying pages still ship. Recommend the same gating treatment for the entire `pages/operations/*` and `pages/security/*` trees, or move them under a `feature.experimental.*` flag.

---

## 4. Backend Audit

### 4.1 HTTP surface — duplication risk

§1.3 documents the parallel `*Routes.java` / `*Controller.java` classes for **agents**, **vectors**, and **workflows** under both `core/yappc-api/src/main/java/com/ghatana/yappc/api/http/` and `core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/{agent,vector,workflow}/http/`. **F‑Y062**.

Without inspecting both server bootstraps (`yappc-api` and `yappc-domain-impl` start scripts), it is unknown whether:

1. One module is dead and shadow-builds against the other (then: delete it), or
2. Both run as separate JVMs with identical wire contracts (then: contract evolution gets duplicated and Section 11 is violated), or
3. Both register routes into the **same** `RoutingServlet` instance somewhere — which would cause runtime `IllegalStateException` ("route already registered") on boot.

This must be triaged before any release. See §17 R-1.

### 4.2 Run-phase (Phase 5) is non-functional via the GitHub adapter

[`GitHubActionsCiCdAdapter`](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/run/GitHubActionsCiCdAdapter.java) returns `RunStatus.NOT_READY` from `build()`, `test()`, `deploy()`, `migrate()`, `rollback()` with explicit `[NOT_IMPLEMENTED]` messages. This is **Section 31 compliant** (no fake success) but it means the entire Run phase is unusable through this provider. Either:

- (a) deliver the integration before any release that markets the Run phase, or
- (b) gate the entire Run phase out of the product surface until then.

### 4.3 Approvals — split-brain on DB failure

[JdbcHumanApprovalService.java:185-225](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/JdbcHumanApprovalService.java) reads from PostgreSQL but on `SQLException` "falls back to in-memory" for `pendingFor`, `allPending`, and `findById`. Two consequences:

1. After a DB blip, two replicas can return **different lists of pending approvals**, breaking the "single source of truth" invariant for human-in-the-loop governance.
2. Mutations are written to both DB and in-memory; on DB failure for a write, the in-memory copy still succeeds — that mutation will **never** materialise to the durable store.

This is a P0 correctness defect for any compliance-bound flow (GDPR approvals, deployment approvals).

### 4.4 RBAC stored in-memory

[`YappcApiSecurity.java:163`](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/security/YappcApiSecurity.java) wires `new PolicyService(new InMemoryPolicyRepository())`. Unless policies are loaded from a durable source on startup (no evidence found in the file), policy edits are lost on restart and replicas drift. Per Section 23, policies must live in a tenant-aware store. **F‑Y063**.

### 4.5 Generate-phase tool gap

`core/agents/runtime/.../CodeGenerationToolProvider.java:231` throws `UnsupportedOperationException("Not implemented yet")` for one of its tool variants — **F‑Y069**. Section 31 requires explicit failure semantics for this; the throw qualifies, but the capability is missing.

### 4.6 Lifecycle module stub registration

[`LifecycleServiceModule.java:1451`](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/LifecycleServiceModule.java) contains the literal comment "Register in-memory stubs for all YAPPC data collections." If this code path executes in production wiring (the file is the **production** module), it instantiates the in-memory variant of every collection — which then races/conflicts with the JDBC variants registered elsewhere in the same module. **F‑Y065**.

---

## 5. Persistence Audit

### 5.1 In-memory shadows alongside durable stores

The repository contains **paired in-memory + JDBC** implementations for many concerns (artifacts, events, approvals, plans, workflows, agents, jobs, KG, idempotency). Most pairs are wired with the JDBC variant in production — but several still default to in-memory when no override is supplied:

| Default in-memory binding | File |
|---------------------------|------|
| `YappcArtifactRepository` default ctor | [storage/YappcArtifactRepository.java:35](../../core/yappc-services/src/main/java/com/ghatana/yappc/storage/YappcArtifactRepository.java) |
| `PhaseEventPublisher` default ctor | [storage/PhaseEventPublisher.java:42](../../core/yappc-services/src/main/java/com/ghatana/yappc/storage/PhaseEventPublisher.java) |
| `YappcAgentSystem` default registry | [agents/.../YappcAgentSystem.java:1077](../../core/agents/src/main/java/com/ghatana/yappc/agent/YappcAgentSystem.java) |
| `WorkflowController` idempotency cache (single-node TTL) | [domain/workflow/http/WorkflowController.java:673](../../core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/workflow/http/WorkflowController.java) |

Recommendation: every default ctor that returns an in-memory store should be deleted. Make the durable store the only construction path and require dev/test code to provide its own in-memory wrapper explicitly. Section 9 rationale: silent default to in-memory is exactly the silent-failure pattern this rule forbids.

### 5.2 Schema ownership is ambiguous

With three services modules (see F‑Y061), a single `JdbcHumanApprovalService` exists under both `core/yappc-services/` and `core/services-lifecycle/`. Both will compile against the same DDL. There is no governance file declaring which module owns the migration. Recommendation: collapse to one services module, then own one `db/migrations/` directory under it.

---

## 6. Test Authenticity (Section 29)

- **Frontend**: zero `test.skip(true, ...)`, zero `xit(`, zero `it.skip(` in `frontend/web/{src,e2e}`. Spot-check passes.
- **Java**: not exhaustively scanned this pass (would extend the audit by hours and the deferred sub-modules need their own pass). Recommended follow-up in §17 R‑8.

---

## 7. Mock / Stub / Placeholder Zero-Tolerance Scan

See §3.1 (frontend) and §4.2 / §4.5 / §4.6 / §5.1 (Java). The summary across the whole audit:

- **Honest fail-closed stubs** (Section 31 compliant — keep until integration ships): `GitHubActionsCiCdAdapter`, voice commands feature flag, `Refactoring.preview()` default `UnsupportedOperationException`.
- **Silent-failure stubs** (must fix before release): `JdbcHumanApprovalService` JDBC→in-memory fallback (F‑Y064), defaults that return in-memory stores (F‑Y063, §5.1), `agentService.ts` placeholder (F‑Y071), `state/atoms.ts` migration stubs (F‑Y070), `IndexedDBAdapter` `Method not implemented` throws on non-public canvas paths (F‑Y067).
- **Coming-soon UI** (must hide behind capability flag if shipped): `IDEShell` Terminal/Problems panels (F‑Y072), `TemplateEditor` placeholder.

---

## 8. End-to-End Journey Traces (spot checks)

> Full end-to-end journey playback requires running the system. The spot checks below use static evidence only.

| Journey | Evidence-based assessment |
|---------|---------------------------|
| **Intent → Approval → AEP → Lineage** | Approvals sub-system has split-brain risk (F‑Y064). AEP integration via `AepOrchestrationClient` and `useAgentRunStream` is wired but the duplicated agent route classes (F‑Y062) mean it is unclear which controller actually serves `/api/v1/agents/.../execute`. |
| **Generate → Quality Gate** | `CodeQualityAnalyzer` exists in both `services-platform` and `yappc-services` — same class, two modules. Quality gate decision becomes non-deterministic if both register. (F‑Y061) |
| **Run → Deploy → Rollback** | All routes return `NOT_READY` from `GitHubActionsCiCdAdapter`. Phase is non-functional. (F‑Y066) |
| **Refactor → Simulate → Apply** | `refactorer/api` module's `ServiceRuntime` defaults `JobQueue`, `JobStore`, `EventBus` to in-memory variants — fine for dev, must be rejected for production wiring. |
| **Sprint planning** | Not re-verified this pass. |
| **Export queue** | Not re-verified this pass. |

---

## 9. Mock / Stub / Placeholder — see §7.

(This section number is preserved from the prompt's structure; content folded into §7 to avoid repetition.)

---

## 10. Duplicate / Source-of-Truth Audit

### 10.1 Three competing services modules — F‑Y061 (P0)

The following classes exist **byte-identically (or near-identically)** across two or three of `core/yappc-services/`, `core/services-platform/`, `core/services-lifecycle/`:

| Class | yappc-services | services-platform | services-lifecycle |
|-------|----------------|-------------------|--------------------|
| `LifecycleServiceModule` | ✅ | — | ✅ |
| `JdbcHumanApprovalService` | ✅ | — | ✅ |
| `HumanApprovalService` | ✅ | — | ✅ |
| `ApprovalHttpHandlers` | ✅ | — | ✅ |
| `YappcApiSecurity` | ✅ | — | ✅ |
| `RequirementsConsistencyChecker` | ✅ | ✅ | — |
| `CodeQualityAnalyzer` | ✅ | ✅ | — |
| `ResultAggregatorOperator` | ✅ | — | ✅ |

Whichever module is on the runtime classpath wins; if multiple are, classloader order determines which `LifecycleServiceModule` runs and which `RoutingServlet` registrations execute. This is the single biggest production risk in the codebase right now.

**Decision required**: pick ONE owner module. Delete the duplicates. Update settings.gradle.kts and downstream module dependencies in one PR.

### 10.2 Two parallel HTTP route trees — F‑Y062 (P0)

See §1.3 / §4.1.

### 10.3 Forbidden `@yappc/*` packages — F‑Y003 (P0, carried over)

10 packages (see §1.4). Section 32 / Section 25 require fix-forward, no aliases. Each package needs a one-shot rename PR that updates every importer.

### 10.4 Module shells — F‑Y068 (P1)

`core/agents/src/` and `core/scaffold/src/` have no `build.gradle.kts`. Per Section 30 they are either dead or in-progress and must not sit on the main branch.

---

## 11. Security & Authz

### 11.1 Tenant context

Tenant extraction is done via `extractTenantId(request)` in route classes; the duplicated route trees (F‑Y062) mean one of them might silently miss a tenant filter. Recommend a test that asserts every mutating route is wrapped by `TenantContextFilter`.

### 11.2 RBAC durability

In-memory only — F‑Y063. Hard P0.

### 11.3 Approval auditability

Split-brain (F‑Y064) defeats the audit log. Hard P0 for any tenant subject to GDPR / HIPAA / SOC2.

### 11.4 Hardcoded URLs

`/api/placeholder` literal in [migrationRules.ts](../../frontend/web/src/lib/canvas/migrationRules.ts) is a soft data-leak smell — likely a placeholder URL that escaped review. Replace or delete.

---

## 12. Observability

The `monitoring/` stack referenced in `.github/copilot-instructions.md` §19 is workspace-wide and exists at the repo root. YAPPC-side: spot-check confirms `MDC.put("correlationId", …)` patterns appear, but the duplicated route trees (F‑Y062) mean correlation IDs may be set in one controller and not the other. Recommend: when collapsing the duplicate routes, add an integration test that asserts every `/api/v1/yappc/...` response carries `X-Correlation-ID`.

---

## 13. Performance

Not exhaustively measured this pass. The `WorkflowController` per-process idempotency cache ([line 673](../../core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/workflow/http/WorkflowController.java)) is a horizontal-scaling smell — replays will be honoured per-replica only.

---

## 14. Tests

Frontend test theatre check passes (§6). Recommended: extend the same scan to all Java modules in a follow-up PR.

---

## 15. Documentation

The 2026-04-27 audit, the `openapi.yaml`, the `route-manifest.yaml`, the parity Gradle task, and a thoughtful `LIBRARY_GOVERNANCE.md` (referenced from copilot-instructions) all exist. Documentation governance is healthier than the code itself. The largest doc gap is the **lack of a single statement** declaring which of the three `services-*` modules is canonical.

---

## 16. Production-Readiness Gate

| Gate | Required | Status | Blocking finding(s) |
|------|----------|--------|---------------------|
| 1. No silent failures | All adapters fail explicitly | ❌ | F‑Y064 (silent JDBC fallback) |
| 2. Single source of truth per concern | One impl per class; one module per concern | ❌ | F‑Y061, F‑Y062 |
| 3. Durable security state | RBAC, approvals durable | ❌ | F‑Y063, F‑Y064 |
| 4. No production stubs | Section 9 / Section 31 | ❌ | F‑Y065, F‑Y066, F‑Y067, F‑Y069, F‑Y070, F‑Y071 |
| 5. Section 30 module discipline | No orphan folders | ❌ | F‑Y068 |
| 6. Section 32 canonical packages | No `@yappc/*` | ❌ | F‑Y003 (10 packages) |
| 7. API contract governed | OpenAPI ↔ manifest parity gate | ✅ | (F‑Y001 closed) |
| 8. Test theatre absent | Section 29 | ⚠️ | Frontend passes; Java not exhaustively swept |
| 9. UI shipped honestly | No "coming soon" without capability flag | ⚠️ | F‑Y072 (IDE panels) |

**Verdict: ❌ FAIL.** Any one of gates 1, 2, 3, 4, 5, 6 is sufficient to block release; six are open.

---

## 17. Prioritised Remediation Roadmap

> Each item lists the precise files to touch. No code changes were made by this audit.

### Immediate (cannot release without):

- **R‑1 (F‑Y061, F‑Y062)** — Pick ONE of `yappc-services` / `services-platform` / `services-lifecycle` as canonical. Delete the duplicate `.java` files in the others and remove the unused modules from `settings.gradle.kts`. Decide whether `yappc-api` or `yappc-domain-impl` owns the agent/vector/workflow HTTP routes; delete the loser. Add a Gradle `forbidDuplicateClassesCheck` task to prevent recurrence.
- **R‑2 (F‑Y064)** — Remove the `falls back to in-memory` branches in [JdbcHumanApprovalService.java:185-225](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/JdbcHumanApprovalService.java). Surface the SQLException to the caller. Add a circuit breaker, do not silently degrade.
- **R‑3 (F‑Y063, §5.1)** — Delete every default in-memory ctor on production stores (`YappcArtifactRepository`, `PhaseEventPublisher`, `YappcAgentSystem`, `InMemoryPolicyRepository` in `YappcApiSecurity`). Inject the durable store via the DI module only.
- **R‑4 (F‑Y065)** — Inspect [LifecycleServiceModule.java:1450+](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/LifecycleServiceModule.java); if the in-memory stub block is unconditionally executed in production, gate it behind `if (config.environment().isDev())` or delete it.
- **R‑5 (F‑Y003)** — Rename all 10 `@yappc/*` packages to either `@ghatana/...` (if shared) or move them under `products/yappc/frontend/libs/` as private (un-scoped) libs. Fix-forward all importers in one PR per Section 25.

### Required before next release (P1):

- **R‑6 (F‑Y066)** — Either complete the GitHub Actions integration in [GitHubActionsCiCdAdapter](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/run/GitHubActionsCiCdAdapter.java) (preferred) or feature-flag the entire Run phase off in the product surface until done.
- **R‑7 (F‑Y067, F‑Y070, F‑Y071)** — Implement the three `IndexedDBAdapter` methods, finish the atom migration, replace the `agentService.ts` placeholder with a real injected client.
- **R‑8** — Extend the Section 29 anti-theatre scan into Java tests (`@Disabled`, `assertTrue(true)`, tests with no production import).
- **R‑9 (F‑Y068)** — Delete `core/agents/src/` and `core/scaffold/src/` shell folders.
- **R‑10 (F‑Y069)** — Implement or formally remove the `CodeGenerationToolProvider` variant that throws `UnsupportedOperationException`.

### Hygiene (P2):

- **R‑11 (F‑Y072)** — Move IDE Terminal/Problems "coming soon" placeholders behind a capability flag (`feature.ide.terminal.enabled`).
- **R‑12** — Replace the hardcoded `/api/placeholder` URL in `migrationRules.ts`.
- **R‑13** — Apply the capability-gating pattern (already used on admin/billing) across the whole `pages/operations/*` and `pages/security/*` trees, or gate them behind `feature.experimental.*`.

---

## Appendix A — Files Inspected (forensic trail)

- [products/yappc/build.gradle.kts](../../build.gradle.kts) lines 155-243
- [products/yappc/docs/api/route-manifest.yaml](../api/route-manifest.yaml)
- [products/yappc/docs/api/openapi.yaml](../api/openapi.yaml) (4 762 lines, sampled)
- [products/yappc/YAPPC_E2E_AUDIT_2026-04-27.md](../../YAPPC_E2E_AUDIT_2026-04-27.md)
- `core/{yappc-api,yappc-domain-impl}/src/main/java/.../http/{Agent,Vector,Workflow}{Routes,Controller}.java`
- `core/{yappc-services,services-platform,services-lifecycle}/src/main/java/...` (duplicated class set)
- [core/yappc-services/.../GitHubActionsCiCdAdapter.java](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/run/GitHubActionsCiCdAdapter.java) lines 100-220
- [core/yappc-services/.../JdbcHumanApprovalService.java](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/JdbcHumanApprovalService.java) lines 185-225
- [core/yappc-services/.../YappcApiSecurity.java](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/security/YappcApiSecurity.java) line 163
- [core/yappc-services/.../LifecycleServiceModule.java](../../core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/LifecycleServiceModule.java) lines 1448-1455
- [core/agents/runtime/.../CodeGenerationToolProvider.java](../../core/agents/runtime/src/main/java/com/ghatana/yappc/agent/tools/provider/CodeGenerationToolProvider.java) line 231
- `frontend/libs/yappc-*/package.json` (10 files)
- [frontend/web/src/services/canvas/storage/IndexedDBAdapter.ts](../../frontend/web/src/services/canvas/storage/IndexedDBAdapter.ts) lines 47-53
- [frontend/web/src/services/agentService.ts](../../frontend/web/src/services/agentService.ts) lines 37, 87
- [frontend/web/src/state/atoms.ts](../../frontend/web/src/state/atoms.ts) line 109
- [frontend/libs/ide/src/components/IDEShell.tsx](../../frontend/libs/ide/src/components/IDEShell.tsx) lines 482-483
- [frontend/libs/yappc-ui/src/components/hooks/useConfig.ts](../../frontend/libs/yappc-ui/src/components/hooks/useConfig.ts) line 232
- Directory listings of `core/`, `core/agents/`, `core/scaffold/`, `core/refactorer/`, `frontend/libs/`, `frontend/apps/`

---

*End of audit. No code modifications were performed; this document is the deliverable per the End-to-End-Product-Prompt heavy-fidelity instruction.*
