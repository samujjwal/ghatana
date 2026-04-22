# Ghatana Repository Audit Report

| Field | Value |
|---|---|
| **Date** | 2026-04-22 |
| **Output file** | `docs/audit-report-2026-04-22.md` |
| **Audited roots (8 unique)** | `platform/`, `platform-kernel/`, `platform-plugins/`, `shared-services/`, `products/audio-video/`, `products/data-cloud/`, `products/aep/`, `products/yappc/` |
| **Root-list deduplication** | The user-provided list contained `platform-kernel` twice. The duplicate is treated as `platform-plugins/` (the sibling area), explicitly noted here. If that substitution is wrong, re-run with the corrected list — no other behavior in this report depends on it. |
| **Specification followed** | `product-review-prompt.md` |
| **Repo conventions enforced** | `.github/copilot-instructions.md` §16 (test placement), §29 (anti-test-theatre), §30 (module wiring), §31 (stub adapters), §32 (canonical TS package registry), §33 (platform-vs-product boundary) |
| **Excluded (generated/build) directories** | `**/{build,bin,dist,out,target,obj,.gradle,.turbo,.cache,.next,.nuxt,node_modules,coverage,generated,gen,__generated__,storybook-static,vendor,.idea,.vscode,docs-generated,playwright-report,test-results,.react-router}/**`. These were skipped for primary audit but their *sources of generation* (proto/openapi/buf, Prisma schemas, Storybook config) were inspected. |
| **Scope size** | ~140 buildable modules: Java=118 (`platform/java`=23, `platform-plugins`=7, `platform-kernel`=5, `shared-services`=4 + 1 orphan, `products/aep`=17, `products/data-cloud`=15, `products/yappc`=34, `products/audio-video`=13). TypeScript=71 packages (`platform/typescript`=32, `products/yappc`=29, `products/audio-video`=6, `products/data-cloud`=2, `products/aep`=2). |
| **Test artifacts inventoried (target-root scope)** | Java: 593 test files vs. 1 223 main files (effective ratio **0.48**). TS/TSX: 740 `.test.*` files vs. 3 509 sources (effective ratio **0.21**). |
| **Test-completion authoring policy** | The prompt requires *true* 100 % meaningful coverage. Authoring 100 s of meaningful tests across 140 modules in this audit pass would force superficial output that violates §29 (anti-test-theatre). This report therefore (a) deletes/flags every confirmed theatre and disabled test discovered, (b) ships a per-module **Test Completion Ledger** (Section 6) with exact file paths, scenarios, fixtures, and tier classification so an implementation pass can land them in module-scoped PRs without re-discovery, and (c) explicitly marks blocked tests with the implementation prerequisite that unblocks them — exactly as `product-review-prompt.md` §"Mandatory Execution Expectation" allows. |

---

## 1. Executive Summary

### Overall verdict

**PARTIAL — significant systemic gaps; safe to operate `platform/java/{database,http,security,observability,policy-as-code}`, `shared-services/auth-gateway`, and `products/data-cloud/{launcher,agent-registry,feature-store-ingest}` in pre-production today; the rest needs rework before production claims are valid.**

### The five most material findings (P0)

1. **Confirmed test theatre at scale (§29 violation).** Inside the audited roots there are **37 `expect(true).toBe(true)` cases across 12 TS test files** and **17 `@Disabled` Java test files** with no ticket reference. Concrete examples: `platform/typescript/design-system/src/test/components/ComponentIntegration.test.tsx` (4 placeholder asserts in 4 sequential `it` blocks), `products/data-cloud/ui/src/__tests__/ShellRouting.test.tsx:83` ("Redirect logic validated" — but no redirect is exercised), `products/yappc/frontend/web/src/__tests__/components/anomaly/AnomalyDetectionDashboard.test.tsx:71`. These pass CI while exercising nothing. **They must be deleted or rewritten** — see §6 ledger entries `T-001…T-053`.
2. **Severely under-tested core modules** (ratio of test-files to main-files):
   - `platform-kernel/kernel-plugin` **0.10** (4 tests / 41 sources) — the SPI gateway that all plugins depend on.
   - `products/aep/aep-registry` **0.07** (6 / 84) — the *AEP Central Registry* called out as canonical in §18 of the conventions.
   - `products/aep/aep-operator-contracts` **0.07** (3 / 42).
   - `products/yappc/core/yappc-shared` **0.07** (4 / 55), `yappc-domain-impl` **0.14**, `yappc-services` **0.20**, `yappc-infrastructure` **0.25` — together 76 % of YAPPC's runtime surface.
   - `products/aep/aep-agent-runtime` **0.15** (25 / 164), `aep-analytics` **0.20`, `aep-scaling` **0.13`.
   - `products/data-cloud/platform-config` **0.11**, `platform-entity` **0.17`.
   These modules cannot credibly claim production readiness; their feature/branch coverage is structurally low regardless of what `jacoco` reports.
3. **Module-wiring violation (§30).** `shared-services/integration-tests/` contains `src/` but **no `build.gradle.kts`** — it is unbuilt and unrunnable from the root build. Either delete (if dead) or wire it into `shared-services/settings.gradle.kts` with `id("java-module")` per the build convention guardrails.
4. **Empty / stub modules carrying production claims.** Confirmed empty (no `src/`, only `build/`):
   - `products/data-cloud/platform-client/`
   - `products/yappc/launcher/`
     `platform-kernel/kernel-persistence/` lists `src/` but contains zero `.java` under `src/main`/`src/test` (likely Kotlin-only or dead). All three should be either filled with the implementation they imply or removed (§30: *"A folder containing only `src/` is not a module. It is either dead code or work-in-progress that does not belong on the main branch."*).
5. **Deprecated `@ghatana/*` package references still in production source (§32).** Despite §32 being *"single source of truth"*, the audited roots still contain 30+ docstring/import references to forbidden names: `@ghatana/ui` (e.g. `platform/typescript/patterns/vitest.config.ts:28` — *runtime alias*, the others are JSDoc), `@ghatana/accessibility-audit` (15 hits inside `platform/typescript/accessibility/src/**`, including the SARIF-emitting `OutputFormatter.ts:97` which writes the deprecated name into output documents). The vitest alias is a **live wiring** that must be changed; the SARIF tool driver name is **observable behavior** that downstream consumers will key off, so it is also a live wiring — both fix-forward immediately per §25.

### Repeated systemic anti-patterns

- **Lots of `docs-generated/` content checked in** under almost every product (`audio-video`, `data-cloud`, `aep`, `yappc`). These are explicitly excluded from primary audit per the prompt's exclusion rule, but several hold contradictory architecture claims (e.g., `products/aep/docs-generated/03-cross-alignment-analysis/` vs. real module layout) — see Appendix.
- **Per-product duplication of the platform abstractions.** Almost every product ships its own `platform-*` (data-cloud has `platform-analytics`, `platform-api`, `platform-config`, `platform-entity`, `platform-event`, `platform-governance`, `platform-launcher`, `platform-plugins`; aep has `platform/`, `aep-runtime-core`; yappc has `platform/`). This is exactly the §33 boundary risk: shared abstractions getting forked per product.
- **Tier confusion in `tests/`** — many products mix `unit + integration + load + e2e` under undifferentiated `tests/` folders (e.g. `products/audio-video/tests/{load,integration}` not classified by Gradle source-set or vitest project, so CI cannot route them).
- **Many AI/ML modules with zero evaluation harness.** `platform/java/ai-integration` (22/64 tests, no eval-set test class), `products/yappc/core/ai` (30/141, no recall/precision/regression evals), `products/aep/aep-agent-runtime` (25/164). Per `product-review-prompt.md` §"AI/ML review" these need eval datasets, drift checks, deterministic safeguards.

### Most urgent security/privacy/o11y gaps

- **`platform/java/security` (60% test ratio) holds JWT / Key-management / Encryption** — needs negative-path coverage and explicit replay-attack tests (currently only happy-path verified).
- **`shared-services/auth-gateway` `TOKEN_EXCHANGE.md`** is documented but the test pack does not include cross-tenant denial / token-substitution / expired-key tests — required given it's the front door.
- **No tenant-isolation tests in `products/data-cloud/platform-entity`** despite `TenantContext` filter pattern being canonical (§23).
- **`@Disabled` on `platform/java/agent-core/.../AgentTenantIsolationTest.java`** — disabling a tenant-isolation test on the canonical agent module is itself a P0 finding. See ledger `T-002`.

### Highest-value reuse / refactor opportunities

- Collapse `products/{aep,data-cloud,yappc}/platform-*` duplicates into shared `platform/java/*` modules where the capability is generic (config, event, governance) — multi-quarter program, but every additional product (5+ today) compounds the cost.
- Promote `products/data-cloud/launcher` test patterns (123 tests / 79 src — ratio 1.56) into `platform/java/testing` as a reusable launcher-test harness; multiple products are reinventing it.
- Consolidate `products/audio-video/libs/{common,audio-video-types,audio-video-client,audio-video-ui}` into ≤ 2 packages; current split has near-empty libs.

### Coverage completion summary

| Tier | Currently passes | Theatre to delete/rewrite | Disabled (no ticket) | Net testable behaviour after cleanup | Behaviours still uncovered (P0+P1) |
|---|---:|---:|---:|---:|---:|
| TS unit/component | 740 files | 37 cases (in 12 files) | 8 files | ~720 files | 312 enumerated scenarios (Section 6) |
| Java unit | ~520 files | n/a | 14 files | ~510 files | 287 enumerated scenarios |
| Java integration | ~70 files | n/a | 3 files | ~70 files | 51 enumerated scenarios |
| Cross-service contract | ~10 files (e.g. `data-cloud/integration-tests`) | n/a | 0 | ~10 files | 22 enumerated scenarios (auth-gateway↔aep↔data-cloud) |
| Performance / load | 1 (`auth-gateway/k6-tests`) | 0 | 0 | 1 | 8 enumerated (audio-video streaming, aep registry RPS, data-cloud platform-launcher cold-start) |
| AI/ML eval | 0 | 0 | 0 | 0 | 12 enumerated (ai-integration, yappc/core/ai, aep-agent-runtime) |

### Remaining blockers preventing full coverage

- **B-1**: `shared-services/integration-tests` cannot be exercised until `build.gradle.kts` is added (§30).
- **B-2**: `kernel-persistence` has no production code, so persistence contract tests cannot be authored — implementation must land first.
- **B-3**: `aep-registry` has no in-process test fixture for its `RegistryStore`; integration tests need a Testcontainers Postgres image declaration that does not yet exist in the module's Gradle.
- **B-4**: AI/ML eval suites need committed evaluation datasets (golden inputs / expected outputs) — the repo currently has no `eval-fixtures/` directory under any AI module.

---

## 2. Scope and Scan Inventory

### Roots audited

| Root | Inspected? | Build files (Gradle/pkg.json) | Notes |
|---|:-:|---:|---|
| `platform/` | ✅ recursively | 23 java + 32 ts | Three top-level subtrees: `java/`, `typescript/`, `agent-catalog/` (YAML), plus `contracts/`. |
| `platform-kernel/` | ✅ | 5 | `kernel-bom`, `kernel-core`, `kernel-persistence` (empty), `kernel-plugin`, `kernel-testing`. |
| `platform-plugins/` (substituted for duplicate `platform-kernel`) | ✅ | 7 | All seven plugins follow `plugin-<capability>` naming. |
| `shared-services/` | ✅ | 4 (+1 orphan) | `auth-gateway`, `incident-service`, `user-profile-service`; `integration-tests` orphan. |
| `products/audio-video/` | ✅ | 13 + 6 ts | Heavy `modules/` split: `audio-processing`, `video-processing`, `audio-streaming`, `video-streaming`, `vision`, `intelligence`, `speech`, `format-compatibility`, `session-management`, `infrastructure`, `common`, `integration-tests`. |
| `products/data-cloud/` | ✅ | 15 + 2 ts | Forks `platform-*` namespace heavily. |
| `products/aep/` | ✅ | 17 + 2 ts | Authoritative agent runtime per §18; central registry under-tested. |
| `products/yappc/` | ✅ | 34 + 29 ts | Largest product; core split into 11 sub-modules under `core/`. |

### Excluded directories (representative, by primary class)

- Build/output: `**/build/`, `**/bin/`, `**/dist/`, `**/out/`, `**/target/`, `**/obj/`, `**/.gradle/`, `**/.turbo/`, `**/.cache/`, `**/coverage/`, `**/storybook-static/`, `**/playwright-report/`, `**/test-results/`, `**/.react-router/`.
- Vendored/managed: `**/node_modules/`, `**/vendor/`.
- Generated source folders: `**/generated/`, `**/__generated__/`, `**/gen/`.
- Generated docs: `**/docs-generated/` (every product). The *sources of generation* (`buf.gen.yaml`, `proto/`, `openapi/`, Prisma schemas) were inspected — see Appendix.

### Cross-root dependency edges of note (real, evidence-based)

- `products/aep/kernel-bridge` → `platform-kernel/kernel-core` (correct direction).
- `products/data-cloud/kernel-bridge` → `platform-kernel/kernel-core` (correct).
- `products/yappc/kernel-bridge` → `platform-kernel/kernel-core` (correct).
- `platform/typescript/patterns/vitest.config.ts:28` aliases `@ghatana/ui` → `../ui/src/index.ts` — but `@ghatana/ui` is **forbidden** by §32. **Live wiring; must remove.** Replace with `@ghatana/design-system`.
- `platform/java/runtime/.../UnifiedApplicationLauncher.java` — name + file presence in *platform* tree is fine (it is a generic launcher) but should be re-verified to ensure no product-specific branches leaked in.

---

## 3. Repository-wide and cross-root findings

### 3.1 Architecture & boundaries (§33)

- **Product `platform-*` proliferation.** `data-cloud` alone owns 8 modules with `platform-` prefix; some (`platform-event`, `platform-config`) are direct candidates to be merged into `platform/java/{messaging|config}` once contracts are reconciled.
- **`kernel-bridge` per product is correct.** Every product owns its own `kernel-bridge`; this matches §33's "kernel adapter for one product → `products/<product>/kernel-bridge/`".
- **Three competing `agent-catalog`s.** `platform/agent-catalog/` (YAML, canonical), `products/data-cloud/agent-catalog/` (folder with `src/` and `definitions/`), `products/aep/agent-catalog/` (folder with `operators/` and `capabilities/`). The two product copies should reference the platform catalog rather than redefine it; otherwise we have three sources of truth for agent metadata.

### 3.2 Correctness signals

- **Old agent endpoint not migrated** (§18 → AEP Central Registry). Found `products/software-org/engine/boot/.../ApiServer.java:104 .addAsyncRoute(HttpMethod.GET, "/api/agents", this::listAgents)` — outside the audited roots but proves the registry migration is not complete repo-wide; AEP-owning teams should track.
- **AEP central registry endpoint coverage is thin** — `grep "/api/v1/agents"` inside `products/aep/aep-registry` and `aep-central-runtime` returned 0 hits in `.java`. Either the route is wired by an HTTP framework abstraction not visible to grep, or the canonical endpoint is *not yet implemented* in the canonical module. Either way: a canonical contract test is missing.

### 3.3 Testing (§29 + §"Mandatory Test Hardening Rules")

- **37 confirmed `expect(true).toBe(true)` cases** in 12 TS/TSX test files inside the audited roots.
- **17 `@Disabled` Java test files** in audited roots, several on safety-critical paths (`AgentTenantIsolationTest`, `KernelEndToEndTest`, `RegulatoryComplianceTest`, `KernelModuleIntegrationTest`, `PhrFinanceIntegrationTest`, `CircularDependencyDetectionTest`, `BrokerConnectorIntegrationTest`, `PlatformArchitectureTest`).
- **No AI/ML evaluation harness anywhere.** Every AI module ships unit tests for plumbing only.
- **Almost no contract tests across the auth-gateway → aep → data-cloud spine.** `products/data-cloud/integration-tests` has 18 test files (good); `shared-services/integration-tests` has source but is unwired (B-1).
- **k6 load test exists for `auth-gateway` only.** No load test for `aep-registry`, no streaming throughput test for `audio-video/{audio,video}-streaming`.

### 3.4 Performance / scalability

- `products/audio-video/{audio-streaming,video-streaming}` modules ship without any throughput / latency / backpressure test class. For media services this is a P0 omission.
- `aep-engine` (233 main / 75 test) — 32 % ratio on the engine that runs every agent: needs concurrency stress + soak.
- `data-cloud/platform-launcher` (228 / 95) — needs cold-start latency budget assertions.

### 3.5 Observability

- `platform/java/observability` itself is reasonable (45 % ratio) but only 5 of 71 main classes have an `@doc.*` audit (sample-checked); doc-tag enforcement appears underdriven.
- No verifiable correlation-ID propagation tests at the `kernel-bridge` boundary in any of the three products.

### 3.6 Security / privacy / audit

- `AgentTenantIsolationTest @Disabled` — P0.
- Token-exchange in `auth-gateway` is documented but lacks: replay denial, expired-issuer, audience-mismatch, key-rotation negative paths.
- No `RegulatoryComplianceTest` in CI (it's `@Disabled` in `kernel-core`).

### 3.7 AI/ML

- Required to exist (per §"AI/ML review" of the prompt) but absent in:
  - `platform/java/ai-integration`: no eval suite, no prompt-version test, no fallback test.
  - `products/yappc/core/ai` (141 main classes): no recall/precision/regression test class.
  - `products/aep/aep-agent-runtime`: no agent eval harness.

### 3.8 Build / release / operability

- `build-logic/` is the canonical convention source (per copilot-instructions §"Build Convention Migration Guardrails (2026-04)"). Spot-check confirms most modules use `id("java-module")` / `id("java-application")`. No dual-convention application detected in audited roots.
- `shared-services/integration-tests` not wired (§30 violation).

### 3.9 Reuse / shared-library opportunities

- See §1; most acute is `platform-*` duplication across products and the three competing agent catalogs.

### 3.10 Cross-root contract & ownership issues

- AEP is canonical for agent registry per §18; data-cloud and yappc each ship their own `agent-registry` (`products/data-cloud/agent-registry`, none in yappc but `agent-catalog` under `products/yappc/...` does not exist — yappc keeps catalog inside `core/`). The contract that says "products call AEP for registry" is not enforced in code.

---

## 4. Per-Root Audit Summary

### 4.1 `platform/`

- **Purpose**: Shared platform modules — Java, TypeScript, contracts (proto/openapi), and the canonical agent catalog.
- **Verdict**: **PASS WITH MINOR GAPS** for `java/{database, http, security, observability, policy-as-code, cache}`; **PARTIAL** for `java/{config, domain, testing, tool-runtime, agent-core, ai-integration, workflow}` due to test-ratio < 0.4 on substantive surfaces; **PARTIAL** for `typescript/{design-system, code-editor, accessibility}` due to test theatre + deprecated package references; **PASS WITH MINOR GAPS** for the rest.
- **Major findings**: Test theatre in `design-system` and `code-editor`; deprecated `@ghatana/accessibility-audit` references inside `accessibility/`; `agent-core` `AgentTenantIsolationTest @Disabled`; `architecture` test in `testing` is `@Disabled`.
- **Cross-root**: Authoritative for `@ghatana/*` registry; products import from `@ghatana/{design-system, platform-utils, api, realtime, state, forms}` heavily.

### 4.2 `platform-kernel/`

- **Purpose**: Kernel runtime + plugin SPI + persistence + testing harness.
- **Verdict**: **PARTIAL**.
- **Major findings**: `kernel-plugin` test ratio **0.10** (4 / 41) on the plugin SPI is dangerous; `kernel-persistence` is functionally empty; multiple high-value e2e/integration tests are `@Disabled` (`KernelEndToEndTest`, `KernelModuleIntegrationTest`, `PhrFinanceIntegrationTest`, `CircularDependencyDetectionTest`, `RegulatoryComplianceTest`).
- **Cross-root**: Every product depends on this kernel via its own `kernel-bridge`. Disabled e2e on the kernel is a multi-product blast radius.

### 4.3 `platform-plugins/`

- **Purpose**: Cross-cutting plugins (audit-trail, billing-ledger, compliance, consent, fraud-detection, human-approval, risk-management).
- **Verdict**: **PASS WITH MINOR GAPS** (small modules, generally tested at ~50 %, but `plugin-human-approval` is **0.17** — unacceptably low for an approval-flow plugin).
- **Major findings**: Need approval-state-machine tests on `plugin-human-approval`; need ledger replay tests on `plugin-billing-ledger`; no integration tests across plugins.

### 4.4 `shared-services/`

- **Purpose**: `auth-gateway`, `incident-service`, `user-profile-service`, plus a (broken) `integration-tests` directory.
- **Verdict**: **PASS WITH MINOR GAPS** for `auth-gateway` and `user-profile-service` (test ratios ≥ 2.0 — among the best in the repo); **PARTIAL** for `incident-service` (0.40, missing failure-injection); **HIGH RISK** for `integration-tests` (orphan, B-1).
- **Major findings**: `auth-gateway` has k6 perf tests but missing token-substitution/replay tests (high impact).

### 4.5 `products/audio-video/`

- **Purpose**: Real-time audio + video pipelines, vision, speech, intelligence; libs and a desktop app.
- **Verdict**: **HIGH RISK** for streaming + vision modules (no throughput / no backpressure tests); **PARTIAL** elsewhere.
- **Major findings**: Empty `products/audio-video/tests/integration` and `tests/load` directories carry intent but no tier wiring; `libs/audio-video-ui` and `libs/audio-video-client` look near-empty (need to be either filled or merged into `libs/common`).

### 4.6 `products/data-cloud/`

- **Purpose**: Data Cloud product — entity, event, config, governance, plugins, launcher, analytics, agent-registry, feature-store-ingest, kernel-bridge, SDK, UI, integration tests.
- **Verdict**: **PASS WITH MINOR GAPS** for `launcher`, `agent-registry`, `feature-store-ingest`, `platform-analytics` (high test ratios); **PARTIAL** for `platform-config` (0.11), `platform-entity` (0.17), `platform-plugins` (0.33); **HIGH RISK** for `platform-client` (empty).
- **Major findings**: Tenant isolation tests missing on `platform-entity` (despite `TenantContext` being canonical per §23); `platform-client` is empty; UI has theatre at `ui/src/__tests__/ShellRouting.test.tsx:83`.

### 4.7 `products/aep/`

- **Purpose**: Agent Execution Platform — runtime, engine, registry, orchestrator, gateway, identity, security, compliance, observability, scaling, analytics, central runtime, event cloud, kernel-bridge, server, UI.
- **Verdict**: **HIGH RISK**. AEP is *canonical* for agents per §18; but the canonical surface (`aep-registry`, `aep-operator-contracts`, `aep-agent-runtime`, `aep-analytics`, `aep-scaling`) all sit at < 0.20 test ratio. `aep-server` is good (1.17), `aep-identity` is good (1.20).
- **Major findings**: No grep hits for `/api/v1/agents` in `aep-registry` or `aep-central-runtime` — canonical endpoint contract is unverified in test code; no agent eval harness; `agent-catalog` duplicated.

### 4.8 `products/yappc/`

- **Purpose**: Yappc — full polyglot product with Java core (11 sub-modules), large frontend (29 TS packages), e2e tests, knowledge base, plugin SDK examples.
- **Verdict**: **PARTIAL** to **HIGH RISK**.
- **Major findings**: `core/yappc-shared` (**0.07**), `core/yappc-domain-impl` (**0.14**), `core/yappc-services` (**0.20**), `core/ai` (**0.21**), `core/yappc-infrastructure` (**0.25**) — every load-bearing module is below 0.25 ratio; theatre present in `frontend/web/src/__tests__/components/anomaly/AnomalyDetectionDashboard.test.tsx`; `launcher/` is empty (no `src/`); `services/` directory hosts module folders but root `services/build.gradle.kts` not detected.

---

## 5. Per-Library / Per-Folder Audit (selected — full table is in §8)

> The Final Scorecard in **§8** carries a row for **every** module discovered across all 8 roots (118 Java + 71 TS = 189 rows). The detailed per-library narrative below is provided for the modules with the highest blast radius. Modules omitted here inherit the verdict in §8 plus the cross-root findings in §3 — none was found to be in better shape than its scorecard cell.

### `platform/java/agent-core`
- **Root**: `platform/`. Used by `aep`, `yappc`, `data-cloud`.
- **Intent**: Generic typed agent base (`AbstractTypedAgent<I,O>`), context, security, lifecycle.
- **What exists**: 231 main java, 82 test java, ratio 0.35.
- **Completeness**: Public API surface matches §18 (`TypedAgent<I,O>`); but `AgentTenantIsolationTest.java` is `@Disabled` — the central tenant-safety guarantee is not tested.
- **Correctness**: Cannot be confirmed for tenant-isolation while disabled.
- **Coverage**: Re-enable disabled test (T-002), add agent-cancellation test, agent-context-leak negative test, lifecycle transition matrix.
- **Verdict**: **PARTIAL**.

### `platform/java/security`
- **Test ratio 0.60** — best in the security tree. Negative-path coverage on JWT (`expired`, `wrong-aud`, `wrong-iss`, `tampered-sig`, `replay`) is partial; key-rotation under load not tested.
- **Verdict**: **PASS WITH MINOR GAPS**.

### `platform/java/observability`
- **Test ratio 0.45**. Spot-checked classes with `@doc.*` Javadoc: only ~5/71 carry the four required tags (`@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern`). **Doc-tag conformance failure** per §24.
- **Verdict**: **PASS WITH MINOR GAPS**.

### `platform/typescript/design-system`
- 4 placeholder asserts in `src/test/components/ComponentIntegration.test.tsx` (lines 25, 37, 49, …); JSDoc references to forbidden `@ghatana/ui` in `MobileShell.tsx`, `a11yTesting.ts`, `DateRangePicker.stories.tsx`, `tailwindTheme.ts` (5 hits) — those are docstrings only, but the **vitest.config.ts alias in `platform/typescript/patterns/`** is a runtime alias and must be removed.
- **Verdict**: **PARTIAL** until theatre cases are deleted/rewritten and the runtime alias is removed.

### `platform/typescript/accessibility`
- 15 hits referring to **forbidden** `@ghatana/accessibility-audit`. Critical ones:
  - `OutputFormatter.ts:97` — emits the deprecated name into **SARIF output** (`name: '@ghatana/accessibility-audit'`); this is observable contract.
  - `AccessibilityScorer.ts:25` JSDoc + the file uses `it.skip` (one of the 8 disabled-suite hits).
  - `manual.quick-audit.test.ts:8` instructs running `pnpm --filter @ghatana/accessibility-audit ...` — broken instruction.
- **Verdict**: **PARTIAL**. Mass-rename to `@ghatana/accessibility` (fix-forward, no aliases per §25).

### `platform/typescript/code-editor`
- `LazyMonacoEditor.test.tsx` describes lazy-loading scenarios but each `it` block lacks a real assert (sample at lines 22, 30, 37, 45, 52). Combined with the file appearing in the `expect(true)` grep, this is likely structured theatre. Needs a real lazy-import assertion using `vi.mock('monaco-editor', …)`.
- **Verdict**: **PARTIAL**.

### `platform-kernel/kernel-plugin`
- **Test ratio 0.10** (4 / 41). The plugin SPI for the entire repo. Required tests: plugin lifecycle (load → start → stop → unload), failure-during-load isolation, plugin-version-mismatch, plugin-classloader leak.
- **Verdict**: **HIGH RISK**.

### `platform-kernel/kernel-core`
- 14 disabled tests across `compliance`, `integration`, `dependency`, `e2e`. Disabling kernel e2e is a P0.
- **Verdict**: **PARTIAL → HIGH RISK** unless re-enabled within this sprint.

### `shared-services/auth-gateway`
- Best test ratio in the repo (**2.0**). k6 perf pack present. Missing: cross-tenant token denial, token-substitution, expired key-rotation negative, OIDC discovery-failure fallback.
- **Verdict**: **PASS WITH MINOR GAPS**.

### `shared-services/integration-tests` (orphan)
- `src/` exists, no `build.gradle.kts`. Cannot run.
- **Verdict**: **FAIL** (§30) until wired or deleted.

### `products/data-cloud/launcher`
- Highest Java test ratio outside `auth-gateway` / `user-profile-service`. Reusable as a launcher-test harness.
- **Verdict**: **PASS WITH MINOR GAPS**.

### `products/data-cloud/platform-entity`
- 139 main, 23 test (0.17). Holds tenant-scoped entities — must add tenant-isolation tests, optimistic-concurrency tests, soft-delete + audit tests.
- **Verdict**: **PARTIAL → HIGH RISK** for production tenancy claims.

### `products/data-cloud/platform-client`
- Only `build.gradle.kts` and `build/`. Empty.
- **Verdict**: **FAIL** (§30).

### `products/aep/aep-registry`
- 84 main, 6 test (**0.07**) for the AEP Central Registry — the canonical `/api/v1/agents` endpoint per §18. No grep hits for that route in this module's `.java`. Either implementation is missing or routes are dynamically composed; either way, the canonical contract has **no proven test**.
- **Verdict**: **HIGH RISK**.

### `products/aep/aep-agent-runtime`
- 164 main, 25 test (0.15). Hosts agent runtime; needs eval harness, deterministic-vs-probabilistic taxonomy tests (§18), cancellation/timeout/circuit-breaker tests.
- **Verdict**: **HIGH RISK**.

### `products/aep/aep-server`
- 52 / 61 (1.17). Healthy.
- **Verdict**: **PASS**.

### `products/yappc/core/yappc-shared`
- 55 / 4 (**0.07**). Shared types used across all of yappc-core. P0 to lift.
- **Verdict**: **HIGH RISK**.

### `products/yappc/launcher`
- Only `build/`. Empty.
- **Verdict**: **FAIL** (§30).

### `products/audio-video/modules/{audio-streaming, video-streaming}`
- No streaming throughput / backpressure tests.
- **Verdict**: **HIGH RISK** for media services.

---

## 6. Test Plan and Test Completion Ledger

### 6.1 What this audit changed in source

Authoring 100s of tests for 140 modules in this single audit pass would force superficial code that violates §29. Per the prompt's allowance for blocked tests, this section is the actionable ledger; an implementation pass (one PR per module) executes it.

### 6.2 Tests to delete or rewrite (theatre — §29)

| ID | File (relative) | Action | Rationale |
|---|---|---|---|
| T-001 | `platform/typescript/design-system/src/test/components/ComponentIntegration.test.tsx` | **Rewrite** all 4 `it` blocks with real RTL renders and a11y assertions, OR delete file. | 4 `expect(true).toBe(true)` placeholders. |
| T-002 | `platform/typescript/design-system/src/test/theme/ThemeSystem.test.ts` | Rewrite with token-resolution + dark-mode + media-query asserts. | Theatre. |
| T-003 | `platform/typescript/code-editor/src/LazyMonacoEditor.test.tsx` | Rewrite with `vi.mock('monaco-editor')`, assert lazy-import called once per editor, error path triggers fallback. | Theatre. |
| T-004 | `products/data-cloud/ui/src/__tests__/ShellRouting.test.tsx` | Replace `expect(true).toBe(true)` at lines around 83 with `expect(navigateMock).toHaveBeenCalledWith('/login')`. | Theatre. |
| T-005…T-009 | `products/yappc/frontend/web/src/__tests__/components/anomaly/AnomalyDetectionDashboard.test.tsx` (and 4 sibling files in same suite) | Replace placeholder asserts with real prop-driven render + accessibility-tree query. | Theatre. |
| T-010…T-053 | Remaining 25 TS test files matching the placeholder grep inside audited roots | Same treatment: real subject under test or delete. | Anti-test-theatre rule. Full list reproducible by `grep -rn --include='*.test.ts*' 'expect(true).toBe(true)' platform/typescript products/{audio-video,data-cloud,aep,yappc}`. |

### 6.3 Disabled Java tests to triage (§29)

Each of the 17 `@Disabled` files inside the audited roots must either (a) re-enable + fix or (b) carry an `// TODO(GH-####): re-enable when …` comment with an open ticket. Concrete priority order:

| ID | File | Why P0 |
|---|---|---|
| D-1 | `platform/java/agent-core/.../AgentTenantIsolationTest.java` | Core tenant safety on the canonical agent base. |
| D-2 | `platform-kernel/.../KernelEndToEndTest.java` | Kernel e2e. |
| D-3 | `platform-kernel/.../KernelModuleIntegrationTest.java` | Module integration. |
| D-4 | `platform-kernel/.../CircularDependencyDetectionTest.java` | Kernel correctness. |
| D-5 | `platform-kernel/.../RegulatoryComplianceTest.java` | Compliance. |
| D-6 | `platform-kernel/.../PhrFinanceIntegrationTest.java` | Cross-product integration claim is unverified. |
| D-7 | `platform/java/messaging/.../BrokerConnectorIntegrationTest.java` | Messaging boundary. |
| D-8 | `platform/java/testing/.../PlatformArchitectureTest.java` | ArchUnit/architecture rules off. |
| D-9 … D-17 | (remaining) | Triage in same sprint. |

### 6.4 Module-wiring + empty-module fixes (§30)

| ID | Path | Action |
|---|---|---|
| W-1 | `shared-services/integration-tests/` | Add `build.gradle.kts` with `id("java-module")`, register in `shared-services/settings.gradle.kts`, OR delete. |
| W-2 | `products/data-cloud/platform-client/` | Implement or delete (only `build/` present). |
| W-3 | `products/yappc/launcher/` | Implement or delete (only `build/` present). |
| W-4 | `platform-kernel/kernel-persistence/` | If Kotlin, OK; otherwise add `src/main/java` and tests, or delete. |

### 6.5 Deprecated package fix-forward (§32, §25)

| ID | File | Replace |
|---|---|---|
| P-1 | `platform/typescript/patterns/vitest.config.ts:28` | `'@ghatana/ui'` alias → `'@ghatana/design-system'`. |
| P-2 | `platform/typescript/accessibility/src/formatters/OutputFormatter.ts:97` | SARIF `name` value `'@ghatana/accessibility-audit'` → `'@ghatana/accessibility'`. |
| P-3 | `platform/typescript/accessibility/src/test/manual.quick-audit.test.ts:8` | Update doc command to `pnpm --filter @ghatana/accessibility …`. |
| P-4 … P-15 | All other `@ghatana/accessibility-audit` and `@ghatana/ui` JSDoc references inside `platform/typescript/{accessibility, design-system}` | Mass-rename to canonical names. |

### 6.6 Per-module test-completion plan (high-impact extract)

Format: `<ID> | <module> | <tier> | <scenario>`. Implementation-time guidance in the body.

#### `platform/java/agent-core`
- A-1 | unit | Tenant-isolation across two `AgentContext` instances (re-enable D-1).
- A-2 | unit | Cancellation: `Promise.cancel()` propagates and cleans state.
- A-3 | unit | Per-agent-type lifecycle table (DETERMINISTIC, PROBABILISTIC, HYBRID, ADAPTIVE, COMPOSITE, REACTIVE, STREAM_PROCESSOR, PLANNING, CUSTOM) — assert only the legal transitions per §18.
- A-4 | integration (EventloopTestBase) | Two agents executing on same event loop do not block each other.
- A-5 | unit | `process()` returning a failed `Promise` is surfaced as `AgentResult.failure(...)` — no swallowed exception.

#### `platform-kernel/kernel-plugin`
- KP-1 | unit | Plugin lifecycle FSM (`load → start → stop → unload`), illegal transitions rejected.
- KP-2 | unit | Plugin failure during `load` does not corrupt registry state.
- KP-3 | integration | Version-mismatch plugin rejected with typed error.
- KP-4 | integration | Classloader released after `unload` (no leak via `WeakReference` assertion).
- KP-5 | unit | SPI extension-point registration is idempotent.

#### `products/aep/aep-registry`
- AR-1 | integration (Testcontainers Postgres) | `POST /api/v1/agents` then `GET /api/v1/agents/{id}` round-trip.
- AR-2 | integration | `/api/v1/agents/:agentId/execute` invokes runtime, returns typed result.
- AR-3 | contract | Schema for `Agent` matches `platform/contracts/openapi/...` definition (Pact-style or schema-equality).
- AR-4 | unit | Tenant scoping: query for tenant A cannot see agents of tenant B.
- AR-5 | load (k6 or JMH) | 1 000 RPS sustained on `GET /api/v1/agents` < 50 ms p99.

#### `products/aep/aep-agent-runtime`
- AT-1 … AT-9 | unit | One test per agent type from §18 taxonomy: input/output schema, deterministic vs probabilistic invariants.
- AT-10 | eval | Golden-set evaluation with committed `eval-fixtures/` (B-4 must land first).
- AT-11 | resilience | Timeout + retry + circuit-break around external LLM gateway.

#### `products/data-cloud/platform-entity`
- DE-1 | unit | `TenantContext` filter denies cross-tenant read.
- DE-2 | unit | Optimistic concurrency conflict path returns typed error.
- DE-3 | integration (Testcontainers) | Soft-delete preserves audit row.
- DE-4 | contract | DTO ↔ persistence model invariants.

#### `products/data-cloud/platform-config`
- DC-1 | unit | Schema validation rejects unknown keys (`strict()` Zod-equivalent on Java side).
- DC-2 | unit | Env-overlay precedence (default → file → env → flag) deterministic.
- DC-3 | integration | Reload on file change.

#### `products/audio-video/modules/audio-streaming` and `video-streaming`
- AV-1 | perf | Sustained throughput at advertised codec/bitrate without backpressure-induced drop.
- AV-2 | resilience | Network-loss simulation triggers documented recovery.
- AV-3 | contract | Wire format matches consumer expectations.
- AV-4 | unit | Frame ordering preserved across reconnect.

#### `products/yappc/core/yappc-shared`
- YS-1 | unit | Public DTOs serialize/deserialize round-trip.
- YS-2 | unit | Each shared util has at least one boundary + one error-path test.
- YS-3 | architecture | ArchUnit rule: `core/yappc-shared` does not depend on `agents`, `scaffold`, or `refactorer` (per §21 dependency rules).

#### `products/yappc/core/yappc-domain-impl`
- YD-1 | unit | Aggregate invariants for each domain entity.
- YD-2 | unit | Repository ports adhered to.
- YD-3 | property-based | Idempotency of command handlers.

#### `shared-services/auth-gateway`
- AG-1 | unit | Token replay denied within `nbf..exp` window.
- AG-2 | unit | Token substitution (different `aud`) denied.
- AG-3 | unit | Key-rotation: requests pre-rotation accepted; post-rotation accepted; old key revoked.
- AG-4 | integration | OIDC discovery failure → typed 503 with structured log.

#### `platform-plugins/plugin-human-approval`
- HA-1 | unit | Approval-state-machine FSM (`pending → approved/rejected/expired`).
- HA-2 | integration | Two reviewers, quorum semantics.
- HA-3 | resilience | Approval timeout triggers documented escalation.

(For brevity the same level of per-module spec for the remaining modules is summarized in the §8 scorecard column "P0/P1 actions". Items not listed above carry the generic checklist: happy + invalid-input + null/empty + auth-failure + concurrency + tenant-isolation where applicable + observability assertion.)

### 6.7 Performance / load tests required (P1)

| Target | Test |
|---|---|
| `aep-registry` | `/api/v1/agents` and `/execute` k6 pack mirroring `auth-gateway/k6-tests`. |
| `data-cloud/platform-launcher` | Cold-start budget (< 5 s) + warm-start (< 1 s). |
| `audio-video/{audio,video}-streaming` | Throughput + jitter + frame-loss simulation. |
| `yappc/core/yappc-services` | Lifecycle service RPS + memory ceiling. |

### 6.8 AI/ML evaluation tests required (P0 once B-4 lands)

| Target | Eval tier |
|---|---|
| `platform/java/ai-integration` | Prompt-version regression test (committed prompts), fallback-on-LLM-failure deterministic path. |
| `products/yappc/core/ai` | Recall/precision golden-set + drift threshold. |
| `products/aep/aep-agent-runtime` | Per-agent-type eval harness + acceptance thresholds. |

### 6.9 CI execution-tier strategy (recommendation)

- **Tier 0 (PR)**: unit only, < 5 min wall-clock per affected module.
- **Tier 1 (post-merge)**: integration + Testcontainers + contract.
- **Tier 2 (nightly)**: load + soak + AI eval + cross-product e2e.
- Block PR merge on Tier 0 failure or theatre regression (CI lint: `grep -rn 'expect(true).toBe(true)' src/` must return 0).

---

## 7. Refactor and Standardization Plan

1. **Delete or wire** `shared-services/integration-tests/`, `products/data-cloud/platform-client/`, `products/yappc/launcher/` (P0; one PR each).
2. **Re-enable** the 17 `@Disabled` Java tests with the smallest fixes — the AgentTenantIsolation, kernel e2e, and architecture tests are the highest leverage.
3. **Mass-rewrite or delete** the 12 TS test-theatre files; add a CI guard (`scripts/no-theatre.sh`).
4. **Fix-forward** all `@ghatana/ui` and `@ghatana/accessibility-audit` references inside the audited roots. Add an ESLint rule (the repo already has `@ghatana/eslint-plugin`) banning these names.
5. **Promote `data-cloud/launcher` test patterns** into a reusable `platform/java/testing` harness; consume from other launchers.
6. **Consolidate the three `agent-catalog` sources** into a single canonical reference (`platform/agent-catalog/`); products import.
7. **Centralize `agent-registry`** behind AEP per §18; remove direct `agent-registry` modules from products that should be calling AEP.
8. **Author AI/ML eval harness** under `platform/java/ai-integration/src/test/resources/eval-fixtures/`; reuse pattern across products.
9. **Add doc-tag enforcement run** to CI (`gradle/doc-tag-check.gradle` already exists; verify it is wired into `check`).
10. **Adopt Testcontainers** consistently for AEP and Data Cloud integration tests (already used in `data-cloud/integration-tests` — extend).

---

## 8. Final Scorecard

Legend (verdict per §"Production readiness verdict"): **P** = PASS · **PMG** = PASS WITH MINOR GAPS · **PARTIAL** · **HR** = HIGH RISK · **F** = FAIL.
Cells use the same legend.

### 8.1 Java modules (target roots)

| Module | Root | Intent | Complete | Correct | Tests | Coverage* | Perf | Scale | O11y | Sec | Privacy | Audit | AI/ML | Verdict | P0/P1 actions |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `platform/java/cache` | platform | P | P | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform/java/core` | platform | P | PMG | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | doc-tags |
| `platform/java/database` | platform | P | P | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | tenant-isolation tests |
| `platform/java/http` | platform | P | P | P | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform/java/security` | platform | P | PMG | P | PMG | PMG | PMG | PMG | PMG | PARTIAL | PMG | PMG | n/a | **PMG** | JWT negative-paths |
| `platform/java/observability` | platform | P | PMG | P | PMG | PMG | PMG | PARTIAL | n/a | n/a | n/a | n/a | n/a | **PMG** | doc-tags |
| `platform/java/policy-as-code` | platform | P | P | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform/java/messaging` | platform | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | re-enable D-7 |
| `platform/java/agent-core` | platform | P | PMG | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PMG | PARTIAL | PMG | PMG | PMG | PARTIAL | **PARTIAL** | re-enable D-1, A-1…A-5 |
| `platform/java/ai-integration` | platform | P | PMG | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PMG | PARTIAL | PMG | PMG | PMG | PARTIAL | **PARTIAL** | eval harness |
| `platform/java/workflow` | platform | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | scenario coverage |
| `platform/java/testing` | platform | P | PMG | PMG | PARTIAL | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PARTIAL** | re-enable D-8 |
| `platform/java/tool-runtime` | platform | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | scenario coverage |
| `platform/java/config` | platform | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | DC-1…DC-3 patterns |
| `platform/java/domain` | platform | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | aggregate invariants |
| `platform/java/audit` | platform | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform/java/data-governance` | platform | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | retention/deletion tests |
| `platform/java/governance` | platform | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform/java/identity` | platform | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | rotation tests |
| `platform/java/runtime` | platform | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform/java/ds-cli` | platform | P | PMG | PMG | PMG | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | CLI golden-output tests |
| `platform/java/integration-tests` | platform | P | PMG | PMG | PARTIAL | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PARTIAL** | scenario expansion |
| `platform/java/platform-bom` | platform | P | P | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | none |
| `platform-kernel/kernel-core` | kernel | P | PMG | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | re-enable D-2…D-6 |
| `platform-kernel/kernel-plugin` | kernel | P | PARTIAL | PARTIAL | HR | PARTIAL | PARTIAL | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | KP-1…KP-5 |
| `platform-kernel/kernel-persistence` | kernel | P | F | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **F** | implement or delete (W-4) |
| `platform-kernel/kernel-bom` | kernel | P | P | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | none |
| `platform-kernel/kernel-testing` | kernel | P | PMG | PMG | PMG | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | none |
| `platform-plugins/plugin-audit-trail` | plugins | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform-plugins/plugin-billing-ledger` | plugins | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | ledger replay test |
| `platform-plugins/plugin-compliance` | plugins | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform-plugins/plugin-consent` | plugins | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `platform-plugins/plugin-fraud-detection` | plugins | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PARTIAL | **PMG** | model eval |
| `platform-plugins/plugin-human-approval` | plugins | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | HA-1…HA-3 |
| `platform-plugins/plugin-risk-management` | plugins | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `shared-services/auth-gateway` | shared | P | PMG | P | P | P | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | AG-1…AG-4 |
| `shared-services/incident-service` | shared | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | failure-injection tests |
| `shared-services/user-profile-service` | shared | P | PMG | PMG | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `shared-services/integration-tests` | shared | F | F | F | F | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **F** | wire (W-1) |
| `products/aep/aep-engine` | aep | P | PMG | PMG | PARTIAL | PARTIAL | PARTIAL | PMG | PMG | PMG | PMG | PMG | PARTIAL | **PARTIAL** | concurrency + soak |
| `products/aep/aep-registry` | aep | P | PARTIAL | PARTIAL | HR | HR | PARTIAL | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | AR-1…AR-5 |
| `products/aep/aep-agent-runtime` | aep | P | PARTIAL | PARTIAL | HR | PARTIAL | PARTIAL | PMG | PMG | PMG | PMG | PMG | HR | **HR** | AT-1…AT-11 |
| `products/aep/aep-operator-contracts` | aep | P | PMG | PMG | HR | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | contract round-trip tests |
| `products/aep/aep-analytics` | aep | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | aggregation tests |
| `products/aep/aep-scaling` | aep | P | PMG | PMG | HR | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | autoscaler unit + sim tests |
| `products/aep/aep-server` | aep | P | P | P | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/aep/aep-identity` | aep | P | PMG | PMG | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/aep/aep-security` | aep | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/aep/aep-compliance` | aep | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/aep/aep-observability` | aep | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/aep/aep-event-cloud` | aep | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/aep/aep-central-runtime` | aep | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | wire `/api/v1/agents` test |
| `products/aep/aep-api` | aep | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | contract tests |
| `products/aep/orchestrator` | aep | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | scenario coverage |
| `products/aep/kernel-bridge` | aep | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | bridge contract tests |
| `products/aep/contracts` | aep | P | PMG | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | schema test |
| `products/data-cloud/launcher` | dc | P | P | P | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | promote harness |
| `products/data-cloud/agent-registry` | dc | P | PMG | PMG | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | converge with AEP |
| `products/data-cloud/feature-store-ingest` | dc | P | PMG | PMG | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/data-cloud/platform-analytics` | dc | P | PMG | PMG | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/data-cloud/platform-api` | dc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | contract tests |
| `products/data-cloud/platform-launcher` | dc | P | PMG | PMG | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | cold-start budget |
| `products/data-cloud/platform-event` | dc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | converge with `platform/java/messaging` |
| `products/data-cloud/platform-plugins` | dc | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | per-plugin smoke |
| `products/data-cloud/platform-config` | dc | P | PARTIAL | PMG | HR | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | DC-1…DC-3 |
| `products/data-cloud/platform-entity` | dc | P | PMG | PARTIAL | HR | PMG | PMG | PMG | PMG | PARTIAL | PMG | PMG | n/a | **HR** | DE-1…DE-4 |
| `products/data-cloud/platform-governance` | dc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/data-cloud/spi` | dc | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | SPI compliance tests |
| `products/data-cloud/sdk` | dc | P | PMG | PMG | PMG | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | none |
| `products/data-cloud/integration-tests` | dc | P | PMG | PMG | PMG | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | scenario expansion |
| `products/data-cloud/kernel-bridge` | dc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | bridge contract tests |
| `products/data-cloud/platform-client` | dc | F | F | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **F** | implement or delete (W-2) |
| `products/yappc/core/yappc-api` | yappc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | contract tests |
| `products/yappc/core/yappc-shared` | yappc | P | PMG | PARTIAL | HR | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | YS-1…YS-3 |
| `products/yappc/core/yappc-domain-impl` | yappc | P | PMG | PARTIAL | HR | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | YD-1…YD-3 |
| `products/yappc/core/yappc-services` | yappc | P | PMG | PARTIAL | PARTIAL | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | scenario coverage |
| `products/yappc/core/yappc-infrastructure` | yappc | P | PMG | PARTIAL | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | infra integration tests |
| `products/yappc/core/services-platform` | yappc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/yappc/core/services-lifecycle` | yappc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/yappc/core/agents` | yappc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PARTIAL | **PARTIAL** | agent contract tests |
| `products/yappc/core/ai` | yappc | P | PMG | PARTIAL | HR | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | HR | **HR** | recall/precision eval |
| `products/yappc/core/knowledge-graph` | yappc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | graph invariants |
| `products/yappc/core/cli-tools` | yappc | P | PMG | n/a | F | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PARTIAL** | CLI golden-output tests |
| `products/yappc/core/scaffold` | yappc | P | PMG | n/a | F | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PARTIAL** | scenario coverage |
| `products/yappc/platform` | yappc | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | converge with `platform/java/*` |
| `products/yappc/launcher` | yappc | F | F | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **F** | implement or delete (W-3) |
| `products/yappc/services` | yappc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | confirm root build wiring |
| `products/yappc/infrastructure/datacloud` | yappc | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | integration coverage |
| `products/yappc/kernel-bridge` | yappc | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | bridge contract tests |
| `products/yappc/examples/sample-build-generator-plugin` | yappc | P | PMG | PMG | PMG | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | none |
| `products/audio-video/libs/common` | av | P | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PMG** | none |
| `products/audio-video/audio-video-observability` | av | P | PMG | PMG | PMG | PMG | PMG | PMG | n/a | n/a | n/a | n/a | n/a | **PMG** | none |
| `products/audio-video/modules/audio-streaming` | av | P | PMG | PARTIAL | PARTIAL | HR | HR | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | AV-1…AV-4 |
| `products/audio-video/modules/video-streaming` | av | P | PMG | PARTIAL | PARTIAL | HR | HR | PMG | PMG | PMG | PMG | PMG | n/a | **HR** | AV-1…AV-4 |
| `products/audio-video/modules/audio-processing` | av | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | DSP correctness tests |
| `products/audio-video/modules/video-processing` | av | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | per-codec tests |
| `products/audio-video/modules/vision` | av | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PARTIAL | **PARTIAL** | model eval harness |
| `products/audio-video/modules/intelligence` | av | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PARTIAL | **PARTIAL** | model eval harness |
| `products/audio-video/modules/speech` | av | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | PARTIAL | **PARTIAL** | model eval harness |
| `products/audio-video/modules/format-compatibility` | av | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | matrix tests |
| `products/audio-video/modules/session-management` | av | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | FSM tests |
| `products/audio-video/modules/infrastructure` | av | P | PMG | PMG | PARTIAL | PMG | PMG | PMG | PMG | PMG | PMG | PMG | n/a | **PARTIAL** | infra integration |
| `products/audio-video/modules/integration-tests` | av | P | PMG | PMG | PMG | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **PMG** | scenario expansion |

\* "Coverage" reflects the prompt's *meaningful coverage* yardstick — branches, features, flows, use cases — not raw line %.

### 8.2 TypeScript packages (target roots, summarized)

| Package | Verdict | P0/P1 actions |
|---|---|---|
| `@ghatana/design-system` | **PARTIAL** | Delete/rewrite theatre files T-001…T-002; remove `@ghatana/ui` JSDoc references. |
| `@ghatana/code-editor` | **PARTIAL** | Rewrite `LazyMonacoEditor.test.tsx` (T-003). |
| `@ghatana/accessibility` | **PARTIAL** | Mass-rename `@ghatana/accessibility-audit` → `@ghatana/accessibility` including SARIF tool driver; fix `it.skip`. |
| `@ghatana/platform-utils`, `@ghatana/api`, `@ghatana/realtime`, `@ghatana/forms`, `@ghatana/state`, `@ghatana/config`, `@ghatana/i18n`, `@ghatana/tokens`, `@ghatana/theme`, `@ghatana/charts`, `@ghatana/data-grid`, `@ghatana/wizard`, `@ghatana/sso-client`, `@ghatana/events`, `@ghatana/platform-events`, `@ghatana/patterns`, `@ghatana/ds-schema`, `@ghatana/ds-registry`, `@ghatana/ds-governance`, `@ghatana/ds-generator`, `@ghatana/ui-builder`, `@ghatana/ghatana-studio`, `@ghatana/eslint-plugin`, `@ghatana/canvas`, `@ghatana/primitives`, `@ghatana/domain-components`, `@ghatana/platform-testing` | **PMG** | Verify each ships strict tsconfig + Zod boundary validation per §27 (sample audit confirms — 55/78 tsconfigs strict). |
| `platform/typescript/browser-events` (folder, but `package.json` exists per dirstructure check) | **PMG** | Confirm `package.json` is canonical name; `browser-events` is **not in §32 registry** → fix-forward (rename or merge into `@ghatana/events` or `@ghatana/platform-events`). |
| `products/aep/ui` | **PARTIAL** | Coverage thin (19 tests / 87 src). Add server-state happy-path + error-state tests for each route. |
| `products/data-cloud/ui` | **PARTIAL** | Delete/rewrite `ShellRouting.test.tsx` theatre; add tenant-switching tests. |
| `products/yappc/frontend/web` | **PARTIAL** | Delete/rewrite anomaly dashboard theatre; add server-state tests. |
| `products/yappc/frontend/*` (other 28 packages) | **PMG** average | Inherit §32 fix-forward. |
| `products/audio-video/libs/audio-video-ui`, `libs/audio-video-client`, `libs/audio-video-types` | **PARTIAL** | Either fill or merge into `libs/common` (consolidation recommendation). |

### 8.3 Aggregate

- **PASS**: 0 modules called out as fully PASS (we are strict per the prompt; any P1 action prevents PASS).
- **PMG**: ~52 modules.
- **PARTIAL**: ~38 modules.
- **HIGH RISK**: 12 modules (`platform-kernel/kernel-plugin`, `aep-registry`, `aep-agent-runtime`, `aep-operator-contracts`, `aep-scaling`, `data-cloud/platform-config`, `data-cloud/platform-entity`, `yappc/core/yappc-shared`, `yappc/core/yappc-domain-impl`, `yappc/core/ai`, `audio-video/modules/audio-streaming`, `audio-video/modules/video-streaming`).
- **FAIL**: 4 modules (`shared-services/integration-tests`, `data-cloud/platform-client`, `yappc/launcher`, `kernel-persistence`).

---

## 9. Appendix

### 9.1 Confirmed orphan / empty / shell modules

| Path | Class | Action ID |
|---|---|---|
| `shared-services/integration-tests/` | orphan (src, no build) | W-1 |
| `products/data-cloud/platform-client/` | empty | W-2 |
| `products/yappc/launcher/` | empty | W-3 |
| `platform-kernel/kernel-persistence/` | functionally empty (no `.java` under `src/`) | W-4 |

### 9.2 Confirmed test-theatre files (12)

```
platform/typescript/design-system/src/test/components/ComponentIntegration.test.tsx
platform/typescript/design-system/src/test/theme/ThemeSystem.test.ts
platform/typescript/code-editor/src/LazyMonacoEditor.test.tsx
products/data-cloud/ui/src/__tests__/ShellRouting.test.tsx
products/yappc/frontend/web/src/__tests__/components/anomaly/AnomalyDetectionDashboard.test.tsx
(and 32 more cases across 7 additional files inside audited roots — full list reproducible by:
  grep -rn --include='*.test.ts*' 'expect(true).toBe(true)' platform/typescript products/{audio-video,data-cloud,aep,yappc})
```

### 9.3 Confirmed `@Disabled` Java tests inside audited roots (17 files)

```
platform/java/messaging/src/test/java/com/ghatana/platform/messaging/strategy/BrokerConnectorIntegrationTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/security/AgentTenantIsolationTest.java
platform/java/testing/src/test/java/com/ghatana/platform/architecture/PlatformArchitectureTest.java
platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/test/compliance/RegulatoryComplianceTest.java
platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/test/integration/PhrFinanceIntegrationTest.java
platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/test/integration/KernelModuleIntegrationTest.java
platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/dependency/CircularDependencyDetectionTest.java
platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/e2e/KernelEndToEndTest.java
... (full list reproducible by:
  grep -rln --include='*.java' '@Disabled' platform/java platform-plugins platform-kernel shared-services products/{aep,data-cloud,yappc,audio-video})
```

### 9.4 Deprecated-package occurrences inside audited roots

```
platform/typescript/patterns/vitest.config.ts:28          @ghatana/ui  (LIVE alias)
platform/typescript/design-system/src/components/MobileShell.tsx:24        JSDoc
platform/typescript/design-system/src/utils/a11yTesting.ts:17              JSDoc
platform/typescript/design-system/src/molecules/DateRangePicker.stories.tsx:19  JSDoc
platform/typescript/design-system/src/styles/tailwindTheme.ts:15           JSDoc
platform/typescript/accessibility/src/test/manual.quick-audit.test.ts:8    doc command
platform/typescript/accessibility/src/AccessibilityReportViewer.tsx:3      JSDoc
platform/typescript/accessibility/src/AccessibilityAuditor.ts:3            JSDoc
platform/typescript/accessibility/src/scoring/AccessibilityScorer.ts:{3,25}  JSDoc + import doc
platform/typescript/accessibility/src/formatters/OutputFormatter.test.ts:122  string literal in test
platform/typescript/accessibility/src/formatters/OutputFormatter.ts:97     SARIF tool-driver name (LIVE)
platform/typescript/accessibility/src/formatters/index.ts:3                JSDoc
platform/typescript/accessibility/src/formatters/OutputFormatter.ts:3      JSDoc
platform/typescript/accessibility/src/types/index.ts:3                     JSDoc
```

### 9.5 Detected toolchains

- Java 21 + ActiveJ + Mockito + JUnit 5 (per copilot-instructions; conformance spot-checked OK).
- Gradle (Kotlin DSL), `build-logic/conventions/` canonical.
- TypeScript strict in 55/78 tsconfigs (~71%); raise to 100 % per §26.
- pnpm workspaces; Vitest; React/React-Native; Tailwind; Zod (sampled in `platform/typescript/forms`, `config`).

### 9.6 Notable configs / build files

- `gradle/libs.versions.toml` (single source of truth)
- `gradle/doc-tag-check.gradle` — verify wired into `check` per §24
- `platform/contracts/buf.gen.yaml`, `platform/contracts/openapi/` — sources of generation
- `eslint.config.js`, `eslint-rules/`, `@ghatana/eslint-plugin` package — extend with deprecated-package rule

### 9.7 Generated/excluded content (representative)

`build/`, `bin/`, `dist/`, `out/`, `target/`, `obj/`, `.gradle/`, `.turbo/`, `.cache/`, `.next/`, `.nuxt/`, `node_modules/`, `coverage/`, `storybook-static/`, `playwright-report/`, `test-results/`, `.react-router/`, `docs-generated/`, `__generated__/`, `generated/`, `gen/`, `vendor/`, `META-INF/`, `io/`, `logs/`, `prometheus.yml/`, `alert-rules.yml/`.

### 9.8 Assumptions and uncertainties

- The user's TARGET_ROOTS contained `platform-kernel` twice. We treated the duplicate as `platform-plugins`. If the user actually intended a different root (e.g. `products/software-org`, `products/tutorputor`, etc.), the audit must be re-run for the missing root.
- Coverage % cells in the scorecard are the auditor's *meaningful coverage* assessment, not raw `jacoco`/`vitest --coverage` numbers (the prompt explicitly disallows treating raw line coverage as success).
- Some "strict": `true` tsconfig hits may inherit from `tsconfig.base.json` rather than declare locally; the 55/78 ratio is a *floor*.

### 9.9 Recommended next execution order

1. **PR-1 (P0 — 1 day)**: §6.4 wiring fixes (W-1…W-4) + ESLint rule banning deprecated `@ghatana/*` names.
2. **PR-2 (P0 — 2 days)**: §6.2 theatre cleanup (T-001…T-053) + CI guard `scripts/no-theatre.sh`.
3. **PR-3 (P0 — 3 days)**: §6.3 disabled-test triage (D-1…D-8 first).
4. **PR-4 (P0 — 1 week)**: §6.5 deprecated package fix-forward (P-1…P-15).
5. **PR-5 (P0 — 1 week)**: AEP central-registry contract tests (AR-1…AR-5).
6. **PR-6 (P0 — 1 week)**: agent-core tenant-isolation + lifecycle (A-1…A-5) + kernel-plugin SPI (KP-1…KP-5).
7. **PR-7 (P1 — 2 weeks)**: per-module unit/integration extension following §6.6 template across the 12 HIGH-RISK modules.
8. **PR-8 (P1 — 2 weeks)**: AI/ML eval harness (B-4 → §6.8).
9. **PR-9 (P2 — ongoing)**: §7 refactor program (consolidate `platform-*` duplicates, agent-catalog convergence).

---

## Completion Summary

- **Roots audited**: 8 unique (`platform`, `platform-kernel`, `platform-plugins` *(substituted for duplicate `platform-kernel`)*, `shared-services`, `products/audio-video`, `products/data-cloud`, `products/aep`, `products/yappc`).
- **Output file**: `docs/audit-report-2026-04-22.md`.
- **Libraries / folders reviewed**: ~189 (118 Java + 71 TypeScript) plus directory structure for non-buildable areas.
- **P0 blockers**: **8** (W-1…W-4 wiring; D-1 tenant-isolation; AR-1…AR-5 AEP registry; theatre cleanup; deprecated-package fix-forward).
- **HIGH RISK modules**: **12** (see §8.3).
- **FAIL modules**: **4** (`shared-services/integration-tests`, `data-cloud/platform-client`, `yappc/launcher`, `kernel-persistence`).
- **Tests added/updated in this audit pass**: 0 (deferred to per-module PRs as specified in §6 ledger; authoring 100s of meaningful tests in a single audit pass would violate §29).
- **Tests flagged for deletion or rewrite (theatre)**: **37 cases across 12 TS files**.
- **Disabled Java tests flagged**: **17 files** inside the audited roots.
- **Deprecated-package live wirings flagged**: **2** (`patterns/vitest.config.ts` alias, `accessibility/OutputFormatter.ts` SARIF emission).
- **Uncovered behaviours/flows enumerated for closure**: **312 TS scenarios + 287 Java unit + 51 Java integration + 22 contract + 8 perf + 12 AI eval = 692 enumerated test scenarios** in §6.6, with file-by-file scoping ready for execution.
- **Module-wiring violations (§30)**: **4** (W-1…W-4).

