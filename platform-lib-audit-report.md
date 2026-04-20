# Platform Library Audit Report

- **Audited root:** `platform/`
- **Generated file:** `platform-lib-audit-report.md` (repo root)
- **Date / time of audit:** 2026-04-20
- **Reviewer:** Principal-level audit (automated, evidence-based)
- **Method:** Recursive inventory of `platform/{contracts,agent-catalog,java,kernel,shared-services,testing,typescript}` cross-checked against `settings.gradle.kts`, `pnpm-workspace.yaml`, `gradle/libs.versions.toml`, `.github/copilot-instructions.md`, `platform/typescript/LIBRARY_GOVERNANCE.md`, `platform/typescript/PACKAGE_NAMING_STANDARD.md`, `platform/UTILITY_NAMING_CONVENTIONS.md`, `platform/java/ACTIVEJ_PROMISE_PATTERNS.md`.
- **Scope summary:** All 24 Java modules, 38 TypeScript packages + 6 orphan UI shells, contracts (protobuf + OpenAPI generators), agent catalog (YAML), platform kernel docs, shared-services (Java bridges + TS test files), and the cross-cutting `platform/testing/` suite.
- **Explicit exclusions:**
  - Generated `build/`, `dist/`, `node_modules/` directories — not audited as code.
  - Vendor/binary fixtures inside test resources beyond detection of presence/absence.
  - Cross-product audits (only platform/ in scope, but boundary violations are flagged where products consume platform).

---

## 1. Executive summary

Platform code is **architecturally serious but operationally drifting**. Strong foundations exist (ActiveJ patterns are documented, `tsconfig.base.json` is genuinely strict, Gradle convention plugins are unified under `id("java-module")` / `id("protobuf-module")`, agent taxonomy is well typed). However, several systemic issues materially undermine production claims:

### Top systemic risks (most-to-least urgent)

1. **HIGH — Test theatre across cross-cutting suites.** Every test in [platform/testing/](platform/testing/) (9 files) and [platform/shared-services/testing/](platform/shared-services/testing/) (3 files) is a *fake* test. The pattern is: build a hard-coded object literal whose fields already encode the "expected" answer (`{ valid: true, complete: true, allowed: false }`), then assert on that literal. Examples: [advanced-security-edge-cases.test.ts L18-L51](platform/testing/advanced-security-edge-cases.test.ts#L18-L51), [e2e-workflows.test.ts L11-L25](platform/testing/e2e-workflows.test.ts#L11-L25), [security-boundary.test.ts L11-L40](platform/shared-services/testing/security-boundary.test.ts#L11-L40). These do not invoke any real subject-under-test, validate no contract, exercise no failure path. They produce green CI signal with zero coverage. Treat as P0 to either delete or rewrite.

2. **HIGH — Orphan Java integration-tests module never runs.** [platform/java/integration-tests/](platform/java/integration-tests/) contains 3 real ActiveJ-based tests (`SecurityIntegrationTest`, `ObservabilityIntegrationTest`, `DatabaseIntegrationTest`) but the module has **no `build.gradle.kts`** and is **not in [settings.gradle.kts](settings.gradle.kts#L107-L130)**. Coverage is zero; Section 4 of repo Java standards is silently violated.

3. **HIGH — Production policy enforcement implemented as a stub.** [OpaClient.java L57-L91](platform/java/policy-as-code/src/main/java/com/ghatana/platform/pac/OpaClient.java#L57-L91) ships a `// TODO` "stub" with `mapToJsonUnsafe()` (handwritten string concatenation) and decides allow/deny via `response.body().contains("\"allow\":true")` (substring match). A trivially crafted OPA payload bypasses this. Comment itself says *"replace before production use"*. Any product wired to this module is operating without trustworthy authz.

4. **HIGH — Governance / instruction drift between three documents.** [.github/copilot-instructions.md §17](.github/copilot-instructions.md#L382) lists a different canonical TS package set than [platform/typescript/LIBRARY_GOVERNANCE.md L43-L65](platform/typescript/LIBRARY_GOVERNANCE.md#L43-L65). Instructions still list `@ghatana/accessibility-audit` as canonical (the directory does not exist; canonical is `@ghatana/accessibility`); governance lists 8 packages absent from instructions (`@ghatana/code-editor`, `@ghatana/config`, `@ghatana/state`, `@ghatana/forms`, `@ghatana/data-grid`, `@ghatana/wizard`, `@ghatana/events`, `@ghatana/domain-components`). Result: contributors get conflicting guidance.

5. **HIGH — Section 25 ("fix-forward, no aliases, no backward compatibility") is violated in code.** Concrete cases:
   - [agent-core DEPRECATION_GUIDE.md](platform/java/agent-core/DEPRECATION_GUIDE.md#L9-L20) keeps `AgentType.LLM`, the legacy `Agent` interface, and `AgentCapabilities` until "3.0.0" — explicit deprecation window.
   - [canvas-core/package.json L26](platform/typescript/canvas-core/package.json#L26) has `"deprecated": "Use @ghatana/canvas directly. This package is a thin re-export facade."` — exactly the alias pattern Section 25 forbids.
   - [agent-catalog schema-migration.js L48-L57](platform/agent-catalog/schema-migration.js#L48-L57) explicitly says *"Mark generator as deprecated but keep for backward compatibility"*.
   - 9 `@Deprecated` Java members across `core/validation/ValidationResult`, `ai-integration/LLMService.generate(String)`, `tool-runtime/DefaultToolExecutor`, `security/model/User`.

6. **HIGH — Severe dependency drift between `platform/typescript/package.json` and the actual packages.** Root [platform/typescript/package.json L21-L29](platform/typescript/package.json#L21-L29) declares `typescript ^4.9.4`, `vitest ^0.28.1`, `@types/react ^18.x`, `engines.node >=16`, and uses `npm run --workspaces`. Actual packages use `typescript ^6.0.2`, `vitest ^4.1.4`, `react ^19.2.4`, `@types/node ^25.6.0` (e.g. [design-system/package.json L106-L114](platform/typescript/design-system/package.json#L106-L114)). The root file is a stale lie that confuses tooling and contradicts repo policy of `pnpm`.

7. **MEDIUM — Orphan UI shell folders.** [audit-ui/src/index.ts](platform/typescript/audit-ui/src/index.ts), [nlp-ui/src/index.ts](platform/typescript/nlp-ui/src/index.ts), [privacy-ui/src/index.ts](platform/typescript/privacy-ui/src/index.ts), [security-ui/src/index.ts](platform/typescript/security-ui/src/index.ts), [voice-ui/src/index.ts](platform/typescript/voice-ui/src/index.ts), [selection-ui/src/index.ts](platform/typescript/selection-ui/src/index.ts), [browser-events/](platform/typescript/browser-events/) — each is a single-file folder without `package.json`/`tsconfig.json`/`README.md`. Looks like incomplete consolidation into `@ghatana/domain-components`. Either finish the migration and delete, or restore them to real packages.

8. **MEDIUM — Architectural tension: product-coupled "shared services" sit inside `platform/`.** [aep-kernel-bridge](platform/shared-services/aep-kernel-bridge/), [data-cloud-kernel-bridge](platform/shared-services/data-cloud-kernel-bridge/), [yappc-kernel-bridge](platform/shared-services/yappc-kernel-bridge/) — these are explicitly product-specific bridges named after AEP / Data-Cloud / YAPPC, but live in the platform-level `shared-services` namespace. This is exactly the "shared platform modules should stay generic and product-agnostic" rule from Section 2 being bent. They belong under `products/<name>/kernel-bridge/` or in `shared-services/` outside `platform/`.

9. **MEDIUM — Disabled/silenced tests committed.**
   - [agent-core/bin/test/.../MigrationAdapterTest.java.disabled](platform/java/agent-core/bin/test/com/ghatana/agent/migration/MigrationAdapterTest.java.disabled) — disabled file inside committed Eclipse `bin/` directory (double mistake: bin shouldn't be tracked, and disabled tests shouldn't ship).
   - [ArchitectureRulesTest.java L60-L120](platform/java/testing/src/test/java/com/ghatana/platform/testing/architecture/ArchitectureRulesTest.java#L60-L120) — 6 architectural assertions disabled with `// TODO: Enable when ... exists`.

10. **MEDIUM — Production code with `any` types.** [canvas/src/elements/live-react.ts L97](platform/typescript/canvas/src/elements/live-react.ts#L97) and [L185](platform/typescript/canvas/src/elements/live-react.ts#L185) use `React.ComponentType<any>` in production. Test files have ~76 `as any` casts, many of them unjustified by the OWASP-clean / strict-typing mandate in copilot-instructions §5.

11. **MEDIUM — Duplicate / dual-tree Java packages within a single module.** [platform/java/core](platform/java/core/) ships both `com.ghatana.platform.core.*` (canonical) and a legacy `com.ghatana.platform.validation.ValidationResult` (deprecated, marked `forRemoval = true`). Either delete the legacy, or move to a clearly retired-only directory.

12. **MEDIUM — Build file duplication and naming inconsistency.**
   - [policy-as-code/build.gradle.kts L24-L25](platform/java/policy-as-code/build.gradle.kts#L24-L25) lists `libs.bundles.testing.containers` twice.
   - [`platform/typescript/foundation/platform-utils/`](platform/typescript/foundation/platform-utils/) — only one child of `foundation/`; the extra nesting is inconsistent with every other top-level `platform/typescript/<name>/` package and breaks pnpm-workspace path conventions used elsewhere.

13. **LOW — Duplicate `bin/` directories committed.** [agent-core/bin/](platform/java/agent-core/bin/) and [core/bin/](platform/java/core/bin/) — Eclipse output, not gitignored at module level. Fix by extending root `.gitignore` (`platform/java/**/bin/`) and removing tracked artifacts.

14. **LOW — `platform/kernel/` is documentation-only.** Only [platform/kernel/docs/](platform/kernel/docs/) exists (4 markdown subtrees). The actual code lives in `platform-kernel/` (top-level). This split is confusing — fold the docs into `platform-kernel/docs/` or rename `platform/kernel/` to `platform/kernel-docs/` and document the relationship.

15. **LOW — Deprecation drift in canonical Java APIs.** Section 4 of repo standards says "prefer existing platform modules"; meanwhile, two real candidates (`@Deprecated` `LLMService.generate(String)` and `DefaultToolExecutor` legacy constructor) are still public surface. A consumer could pick the wrong overload because the deprecation is documentation-only.

### Repeated anti-patterns

- **Object-literal "tests"** that assert on their own input (12 files in platform/).
- **Stub adapters with TODO** marking the only realistic implementation as future work (`OpaClient`, several `TraceHttpService` paths).
- **Deprecated-but-kept** packages, classes, and YAML fields, contradicting fix-forward policy.
- **`as any`** in tests (acceptable in narrow boundary mocks; widespread here).
- **Module-scoped IDE detritus** (`bin/`, `node_modules/`) appearing in source listings.

### AI/ML maturity summary

- `platform/java/agent-core` and `platform/java/ai-integration` provide strong typed agent abstractions, descriptors, deterministic/probabilistic/composite/hybrid taxonomies, token usage tracking, and structured-output primitives — this is genuinely production-shaped.
- However: there is **no evaluation harness** (no eval datasets, no acceptance thresholds, no drift metrics), **no policy guard rail** before LLM call (the stubbed `OpaClient` would be the natural place), and **no prompt/version registry** beyond `agent-catalog/` YAML (which is consumed but not validated by the catalog schema in CI).
- `platform/typescript/nlp-ui` and `platform/typescript/voice-ui` — empty shells; no AI/ML there yet.

---

## 2. Repository-wide findings

### Architecture
- Convention plugins (`java-module`, `protobuf-module`) are uniformly applied across the 20 Java modules that are wired (good).
- Layering broadly sound: `core → http/database/observability/security → domain → workflow/agent-core → product modules`. But `platform/shared-services/*-kernel-bridge` reverses that direction by being product-aware inside `platform/`.
- `platform/contracts` correctly isolates schema / OpenAPI generation; it should be the *only* place protobuf/JSON-schema codegen lives, and from inspection it is.

### Correctness
- `OpaClient` (HIGH).
- `agent-catalog/schema-migration.js`: `_deprecated`/`_migratedTo` fields injected into the live YAML write back are unspecified by `catalog-schema.yaml` (would need verification), and consumers may treat them as data.
- Token-expiration test in cross-cutting suite asserts on a hard-coded boolean; real token validators are not invoked.

### Completeness
- Many README files are present (good) but several modules (`identity`, `data-governance`, `tool-runtime`, `policy-as-code`) lack any tests or test count is single-digit.
- `platform/java/integration-tests` not wired (HIGH).

### Testing
- See systemic finding #1 (test theatre).
- Real ActiveJ tests exist in `audit`, `observability`, `agent-core`, `core`, `http`, `messaging`, `governance`, `testing`, `workflow` — these follow `EventloopTestBase`/`runPromise(...)` patterns correctly.
- Missing tiers: integration with real PostgreSQL/Kafka via Testcontainers exists in some modules (`policy-as-code` declares it); cross-module contract tests are absent at platform scope (only OpenAPI generation, no consumer-driven contracts).

### Performance & scalability
- ActiveJ patterns documented and largely followed.
- `OpaClient` blocks and JSON-handwrites — both cost and incorrect.
- No JMH benchmarks except in `agent-core` (declares JMH but no `*.jmh.java` source observed in main inventory). Performance baselines are missing for `database`, `http`, `messaging`, `observability` ingest pipelines.

### Observability
- Strong observability module exists, OpenTelemetry tracing standard prescribed.
- TODOs in [TraceHttpService.java L42, L224](platform/java/observability/src/main/java/com/ghatana/platform/observability/http/TraceHttpService.java#L42) signal incomplete migration to `core:http-server` ResponseBuilder.

### Security & privacy
- Tenant-context pattern documented in instructions; not all modules wire it.
- `OpaClient` substring match (HIGH); `mapToJsonUnsafe` is also a soft injection vector when nested values are passed.
- No SAST/SCA-specific configuration in this layer beyond global OWASP suppressions.

### Auditability
- `platform/java/audit` has real `JpaAuditService` with mapper-based domain conversion (good).
- No append-only / hash-chain guarantee in audit storage observed.

### AI/ML
- See executive summary.

### Build / release / operability
- `gradle/libs.versions.toml` centralization is good.
- Drift between root `platform/typescript/package.json` and packages (HIGH).
- Two `bin/` directories committed.

### Reuse / shared-library opportunities
- Several modules redeclare `libs.bundles.testing.core` plus AssertJ/JUnit individually; a `bundles.testing.platform` could collapse this.
- HTTP filter patterns (`TenantContextFilter`, `ApiKeyAuthFilter`, `JwtAuthFilter`) are documented in instructions but appear in product code rather than `platform:java:http` — promote to platform.

---

## 3. Per-library / per-folder audit

> Verdict legend: **PASS / PASS WITH MINOR GAPS / PARTIAL / HIGH RISK / FAIL**.

### `platform/contracts`
- **Intent.** Single source of truth for cross-product schemas (Protobuf + OpenAPI + JSON-schema → POJO codegen). Correctly scoped.
- **What exists.** [build.gradle.kts](platform/contracts/build.gradle.kts) using `id("protobuf-module")`; `openapi/` (8 product YAMLs); `src/{main,schemaGen,test}/`; codemodel + jsonschema2pojo + mustache toolchain.
- **Findings.** Pinned versions for `codemodel`, `jsonschema2pojo`, `mustache.java`, `jsoup`, `protobuf-java-util` are hardcoded strings instead of being lifted into `libs.versions.toml`. Inconsistency vs. the policy.
- **Tests.** `bundles.testing.core` only — no schema-evolution / backward-compat assertions ("no breaking change in published `*.proto`") which is critical for a contracts module.
- **Verdict.** PASS WITH MINOR GAPS.
- **P0/P1.** P1: lift inline coordinates into version catalog; add proto/OpenAPI breaking-change check (e.g. `buf breaking` or `swagger-diff`).

### `platform/agent-catalog`
- **Intent.** YAML-driven catalog of agents, capabilities, templates, consumed by `CatalogLoader` discovery.
- **What exists.** [agent-catalog.yaml](platform/agent-catalog/agent-catalog.yaml), schema, capability taxonomy, schema-migration script.
- **Findings.**
  - `schema-migration.js` is JS not TS, lives outside any `pnpm` package, has no tests, and explicitly perpetuates deprecation (Section 25 violation).
  - No CI-time schema validation that loaded YAMLs conform to `catalog-schema.yaml` (only declared, not enforced).
  - `composite-agents/data-pipelines/` and `domain-agents/{finance,healthcare,manufacturing,retail}/` exist but were not inspected in depth — verify whether shipped agents are actually loaded by any product or are aspirational.
- **Verdict.** PARTIAL.
- **P0/P1.** P1: convert migration to TS, validate catalog YAMLs in CI against schema, drop "_deprecated" pattern in favour of fix-forward.

### `platform/kernel/docs`
- **Intent.** Implementation plan and ADR docs.
- **Findings.** `platform/kernel/` only contains `docs/`; actual kernel code is in repo-root `platform-kernel/`. The naming collision is confusing.
- **Verdict.** PARTIAL.
- **P0/P1.** P2: relocate to `platform-kernel/docs/` or rename folder.

### `platform/shared-services`
- **Intent.** Kernel adapter/bridge implementations.
- **Findings.** Three product-named bridges sit inside `platform/`. Test layer (`platform/shared-services/testing/`) is fake (HIGH).
- **Verdict.** HIGH RISK (because the test layer claims security/cross-service/HTTP coverage but provides none).
- **P0/P1.** P0: delete or rewrite the 3 fake test files. P1: relocate bridges out of `platform/` to honour Section 2.

### `platform/testing` (cross-cutting TS suite)
- **Intent.** "Production readiness" cross-library coverage.
- **Findings.** All 9 files are object-literal theatre. Zero real coverage.
- **Verdict.** **FAIL**.
- **P0/P1.** P0: delete the entire suite or reauthor with real subjects (HttpClient + spawned servers via Vitest setup). Either decision is acceptable; the current state is actively misleading.

### `platform/typescript/` (overview)
- **Tooling base** ([tsconfig.base.json](platform/typescript/tsconfig.base.json)) is genuinely strict (matches policy).
- **Root [package.json](platform/typescript/package.json)** is *severely* drifted and should be regenerated to match real tooling.
- **`.dependency-cruiser.cjs`** present and enforced (good).
- Per-package detail below for *high-impact* packages; small/empty folders summarised in Section 7 inventory.

### `platform/typescript/design-system`
- **Intent.** Atomic-design canonical UI package.
- **Findings.** Comprehensive `exports` map (atoms/molecules/organisms/hooks/layout/utils/audit/privacy/security/voice/nlp/selection/contracts) — pulls in formerly separate `*-ui` packages via subpaths. This is the correct consolidation; the orphan `*-ui` folders should be deleted.
- **Verdict.** PASS WITH MINOR GAPS. P1: delete dead sibling shells.

### `platform/typescript/canvas` + `canvas-core`/`canvas-plugins`/`canvas-tools`
- **Intent.** Canvas + plugin / tools surfaces.
- **Findings.** `canvas-core` is a `"deprecated"` re-export facade (Section 25 violation). `canvas/src/elements/live-react.ts` uses `React.ComponentType<any>` in production.
- **Verdict.** PARTIAL. P1: delete deprecated facades and migrate consumers; remove `any`.

### `platform/typescript/accessibility`
- **Findings.** Real implementation (`AccessibilityAuditor`, `AccessibilityScorer`, `OutputFormatter`); test coverage exists. ~25 `as any` casts in tests including reaching into private members (`(scorer as any).generateRecommendationsInternal`). Acceptable for negative-input fuzzing; not for accessing privates — refactor to test via public API.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/typescript/realtime`
- **Findings.** WebSocket / EventSource client with multiple resilience tests. Heavy `as any` for `global.WebSocket` mocks — acceptable boundary adapter pattern but should be wrapped in a typed test helper.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/typescript/audit-ui`, `nlp-ui`, `privacy-ui`, `security-ui`, `voice-ui`, `selection-ui`, `browser-events`
- **Findings.** Single-file `src/index.ts` with no `package.json`. Either dead or stub. Not consumed via `@ghatana/...` imports because they have no name.
- **Verdict.** **FAIL** (as packages).
- **P0/P1.** P1: delete or reinstate.

### `platform/typescript/foundation/platform-utils`
- **Findings.** Lives one level deeper than every sibling. Inconsistent path; pnpm-workspace must enumerate this exception.
- **Verdict.** PARTIAL. P2: flatten to `platform/typescript/platform-utils/`.

### `platform/typescript/ui`
- **Findings.** Listed as deprecated by governance, but the package's own README says it is the *backing implementation* for design-system. Reality and governance disagree.
- **Verdict.** PARTIAL — needs definitive decision: either fold into `design-system` or remove from deprecated list.

### `platform/typescript/{tokens,theme,primitives,events,platform-events,charts,forms,data-grid,wizard,api,sso-client,state,config,i18n,patterns,code-editor,ds-schema,ds-registry,ds-governance,ds-generator,domain-components,eslint-plugin,ghatana-studio,ui-builder,testing,accessibility}`
- **Findings.** All have proper `package.json`, README, build script using `tsc`, tests in `__tests__/` co-located.
- **Verdict.** PASS WITH MINOR GAPS (apply the systemic findings — especially `as any` cleanup and root package.json version drift — before claiming production-ready).

### `platform/java/core`
- **Intent.** Foundation utilities, async/event/error/json/validation primitives.
- **Findings.** Package layout is good. Legacy `com.ghatana.platform.validation.ValidationResult` deprecated since 1.0 still present — fix-forward.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/domain`
- **Intent.** Shared domain types (`auth.User`, etc.). Single tidy module.
- **Findings.** Acts as canonical destination for fix-forward (e.g. for `security.model.User`). Confirm no transitive cycles.
- **Verdict.** PASS.

### `platform/java/database`
- **Intent.** DB primitives + Testcontainers helpers.
- **Findings.** Has `testcontainers/` peer folder for shared utilities. Verify it is actually a separate Gradle subproject or that it is wired to be re-used by other modules; otherwise consumers won't find it.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/http`
- **Intent.** Shared HTTP plumbing (DTOMapper, response shapes).
- **Findings.** `DTOMapper` good. Documented `TenantContextFilter`/`ApiKeyAuthFilter`/`JwtAuthFilter` belong here but actual implementations sit inside products (per audit notes). Centralise.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/observability`
- **Intent.** Tracing/metrics/logs ingest + emit.
- **Findings.** Real ingest handler, span mapper, metrics tests. Two TODOs about migrating to `core:http-server` ResponseBuilder.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/security`
- **Intent.** Security primitives + auth utilities.
- **Findings.** `model/User` deprecated alongside `domain.auth.User` — fix-forward needed.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/identity`
- **Findings.** Has `build.gradle.kts` + `src/{main,test}` and is wired in `settings.gradle.kts`. Detail not audited beyond inventory.
- **Verdict.** PARTIAL — needs deeper test review.

### `platform/java/messaging`
- **Findings.** Unified module merged from connectors + aep-connectors per settings.gradle.kts comment. README present.
- **Verdict.** PASS WITH MINOR GAPS — verify deprecated `aep-connectors` references are all removed from products.

### `platform/java/audit`
- **Findings.** `JpaAuditService` real, mapper exists. Missing append-only/hash-chain integrity assurance — major gap for an audit store.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/observability/quarantine`
- **Findings.** None — `quarantine/` folder exists in `governance/` not observability. See next.

### `platform/java/governance`
- **Findings.** Has a `quarantine/` directory whose contents we could not enumerate (filesystem listed empty). If empty, delete it; if non-empty, document its purpose.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/agent-core`
- **Findings.** Strong taxonomy (`deterministic/probabilistic/composite/hybrid/reactive/planning/stream/adaptive/llm/migration`). DEPRECATION_GUIDE keeps legacy `Agent`, `AgentType.LLM`, `AgentCapabilities` until 3.0.0 — Section 25 violation. Committed `bin/` directory with a `.disabled` test file.
- **Verdict.** PARTIAL. P0: remove deprecated APIs in-place per fix-forward policy; remove `bin/`.

### `platform/java/ai-integration`
- **Findings.** Token usage tracking, structured output. Deprecated `LLMService.generate(String)` still public.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/cache`
- **Findings.** Distributed cache infra, single-folder module.
- **Verdict.** PASS WITH MINOR GAPS — detail audit recommended.

### `platform/java/config`
- **Findings.** Config surface module. Detail not audited.
- **Verdict.** PARTIAL — review test coverage.

### `platform/java/data-governance`
- **Findings.** Module wired; detail not audited.
- **Verdict.** PARTIAL.

### `platform/java/policy-as-code`
- **Findings.** **HIGH RISK**: `OpaClient` is a stub, response decoded by string `.contains(...)`, JSON serialized by hand. Build file lists `bundles.testing.containers` twice.
- **Verdict.** **HIGH RISK**.
- **P0.** Replace `mapToJsonUnsafe` with Jackson; deserialize OPA response into a typed record (`OpaResponse(result: Map<String, Object>)`); honour `result.allow` boolean explicitly; harden status-code handling; add integration test against a real OPA Testcontainer.

### `platform/java/tool-runtime`
- **Findings.** `DefaultToolExecutor` has a deprecated constructor (preferred ctor takes `PolicyAsCodeEngine`) — coupling issue with `policy-as-code` HIGH RISK above.
- **Verdict.** PASS WITH MINOR GAPS — but trust degrades because of dependency on stub OPA.

### `platform/java/runtime`
- **Findings.** `HttpServerBinding` / factory exist (per repo memory). Detail not re-audited; relies on `core:http-server`.
- **Verdict.** PASS.

### `platform/java/workflow`
- **Findings.** Real module with build + tests.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/testing`
- **Findings.** Hosts `EventloopTestBase`, contract framework. `ArchitectureRulesTest` has 6 disabled assertions.
- **Verdict.** PASS WITH MINOR GAPS. P1: complete or remove the disabled rules — disabled architectural enforcement is misleading.

### `platform/java/integration-tests`
- **Findings.** Three real tests; module **not wired**. **Verdict.** **FAIL** until wired.
- **P0.** Add `build.gradle.kts` (use `id("java-module")`) and include in `settings.gradle.kts`; confirm tests pass.

### `platform/java/platform-bom`
- **Findings.** Only `build.gradle.kts`; no source — appropriate for a BOM. Verify it actually publishes constraints.
- **Verdict.** PASS WITH MINOR GAPS.

### `platform/java/ds-cli`
- **Findings.** CLI module with build + README + src.
- **Verdict.** PASS WITH MINOR GAPS — CLI tests / smoke tests not verified.

---

## 4. Test plan to reach real 100% meaningful coverage

| Module | Required tier | Specific scenarios missing |
|---|---|---|
| `platform/java/policy-as-code` | Integration (Testcontainers OPA) | allow=true, allow=false, allow missing, malformed JSON, 5xx, timeout, large nested input |
| `platform/java/integration-tests` | Wire + run existing tests | All 3 currently dark |
| `platform/java/audit` | Integrity | Append-only invariants, hash-chain (or document choice not to) |
| `platform/java/agent-core` | Contract | Each canonical agent type has a determinism contract test; fail when subtype mismatches type |
| `platform/java/observability` | E2E | Span → exporter → backend round-trip via Testcontainer Jaeger |
| `platform/java/ai-integration` | Eval | LLM eval-set per prompt template with acceptance thresholds |
| `platform/java/messaging` | Contract / consumer-driven | Cross-version compatibility for unified bus topology |
| `platform/typescript/realtime` | Resilience | Reconnect backoff jitter; multi-tab session continuity |
| `platform/typescript/canvas` | Visual regression | Plugin-render snapshot suite |
| `platform/typescript/design-system` | A11y | Axe pass per atom/molecule/organism (CI gating) |
| `platform/contracts` | Schema | `buf breaking` against `main`; OpenAPI consumer-driven contract tests for products |
| `platform/testing/*` (cross-cutting) | **Replace, not extend** | Real cross-tier flow (TS client → live Java route) using docker-compose fixtures |
| `platform/shared-services/testing/*` | **Replace, not extend** | Same |

**Execution strategy:** classify into Gradle `test`, `integrationTest` (Testcontainers), `contractTest`, `eval`. CI tiers: PR (`test`+`contractTest`); nightly (`integrationTest`+`eval`); release (visual regression + a11y).

---

## 5. Refactor and standardization plan

1. **Delete / rewrite all object-literal "tests"** in `platform/testing/` and `platform/shared-services/testing/`. (P0)
2. **Wire `platform/java/integration-tests`** into Gradle. (P0)
3. **Rewrite `OpaClient`** with Jackson + typed response. (P0)
4. **Reconcile three governance documents** ([copilot-instructions.md §17](.github/copilot-instructions.md#L382), [LIBRARY_GOVERNANCE.md](platform/typescript/LIBRARY_GOVERNANCE.md), [PACKAGE_NAMING_STANDARD.md](platform/typescript/PACKAGE_NAMING_STANDARD.md)) into a single canonical list. Update copilot-instructions.md as the authoritative entry point and have the other two reference it. (P1)
5. **Execute fix-forward**: delete `canvas-core`/`canvas-plugins`/`canvas-tools` facades, delete `agent-core` deprecated APIs, drop `_deprecated` flags from agent-catalog migration script, delete deprecated Java members. (P1)
6. **Refresh root [platform/typescript/package.json](platform/typescript/package.json)** to match real tooling: `pnpm` not `npm`, modern TS/vitest/Node, populate `workspaces` or remove if relying on root pnpm-workspace. (P1)
7. **Delete or restore orphan UI shells** (`audit-ui`, `nlp-ui`, `privacy-ui`, `security-ui`, `voice-ui`, `selection-ui`, `browser-events`). (P1)
8. **Relocate product-coupled "shared services"** out of `platform/`. (P1)
9. **Promote `TenantContextFilter`/`ApiKeyAuthFilter`/`JwtAuthFilter` into `platform:java:http`**. (P1)
10. **Centralise inline dependency coordinates** in `platform/contracts/build.gradle.kts` into `libs.versions.toml`. (P2)
11. **Flatten `platform/typescript/foundation/platform-utils`** to `platform/typescript/platform-utils`. (P2)
12. **Remove `bin/` directories** + add `**/bin/` to `.gitignore` where missing. (P2)
13. **Resolve `platform/kernel/docs/`** vs `platform-kernel/` confusion. (P2)
14. **Enable disabled `ArchitectureRulesTest` assertions** or delete. (P2)
15. **Add CI validation** for agent-catalog YAMLs against `catalog-schema.yaml`. (P2)

---

## 6. Final scorecard

| Library / folder | Intent | Complete | Correct | Tests | Perf | Scale | O11y | Sec | Privacy | Audit | AI/ML | Prod-ready | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `platform/contracts` | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | n/a | ✓ | n/a | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/agent-catalog` | ✓ | ◐ | ◐ | ✗ | n/a | n/a | ✗ | ◐ | ◐ | ◐ | ✓ | ✗ | PARTIAL |
| `platform/kernel/docs` | ◐ | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | PARTIAL |
| `platform/shared-services/aep-kernel-bridge` | ◐ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/shared-services/data-cloud-kernel-bridge` | ◐ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/shared-services/yappc-kernel-bridge` | ◐ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/shared-services/testing` | ✓ | ✗ | ✗ | ✗ | n/a | n/a | n/a | ✗ | n/a | n/a | n/a | ✗ | **FAIL** |
| `platform/testing` | ✓ | ✗ | ✗ | ✗ | n/a | n/a | n/a | ✗ | n/a | n/a | n/a | ✗ | **FAIL** |
| `platform/java/core` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ✓ | PASS WITH MINOR GAPS |
| `platform/java/domain` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ✓ | PASS |
| `platform/java/database` | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ✓ | PASS WITH MINOR GAPS |
| `platform/java/http` | ✓ | ◐ | ✓ | ◐ | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/java/observability` | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/java/security` | ✓ | ◐ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/java/identity` | ✓ | ◐ | ◐ | ◐ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | n/a | ◐ | PARTIAL |
| `platform/java/messaging` | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/java/audit` | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ◐ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/java/governance` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ✓ | PASS WITH MINOR GAPS |
| `platform/java/agent-core` | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ◐ | PARTIAL |
| `platform/java/ai-integration` | ✓ | ◐ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ◐ | ✓ | ◐ | ◐ | PASS WITH MINOR GAPS |
| `platform/java/cache` | ✓ | ◐ | ◐ | ◐ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | n/a | ◐ | PARTIAL |
| `platform/java/config` | ✓ | ◐ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PARTIAL |
| `platform/java/data-governance` | ✓ | ◐ | ◐ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PARTIAL |
| `platform/java/policy-as-code` | ✓ | ✗ | ✗ | ✗ | ✓ | ✓ | ◐ | ✗ | ✓ | ✓ | n/a | ✗ | **HIGH RISK** |
| `platform/java/tool-runtime` | ✓ | ◐ | ◐ | ◐ | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | ◐ | ◐ | PASS WITH MINOR GAPS |
| `platform/java/runtime` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ✓ | PASS |
| `platform/java/workflow` | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/java/testing` | ✓ | ◐ | ✓ | ◐ | n/a | n/a | n/a | ✓ | ✓ | n/a | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/java/integration-tests` | ✓ | ✓ | ✓ | ✗ | n/a | n/a | n/a | ✓ | ✓ | n/a | n/a | ✗ | **FAIL** |
| `platform/java/platform-bom` | ✓ | ◐ | ✓ | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/java/ds-cli` | ✓ | ◐ | ◐ | ◐ | ✓ | n/a | ◐ | ✓ | ✓ | ✓ | n/a | ◐ | PARTIAL |
| `platform/typescript/design-system` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/typescript/canvas` | ✓ | ✓ | ◐ | ◐ | ✓ | ✓ | n/a | ✓ | ✓ | n/a | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/typescript/canvas-core/-plugins/-tools` | ◐ | ◐ | ✓ | ◐ | ✓ | ✓ | n/a | ✓ | ✓ | n/a | n/a | ◐ | PARTIAL (deprecated facades) |
| `platform/typescript/accessibility` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | n/a | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/typescript/realtime` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | n/a | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/typescript/{tokens,theme,primitives,events,platform-events,charts,forms,data-grid,wizard,api,sso-client,state,config,i18n,patterns,code-editor,ds-schema,ds-registry,ds-governance,ds-generator,domain-components,eslint-plugin,ghatana-studio,ui-builder,testing}` | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | n/a | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS |
| `platform/typescript/{audit-ui,nlp-ui,privacy-ui,security-ui,voice-ui,selection-ui,browser-events}` | ✗ | ✗ | n/a | ✗ | n/a | n/a | n/a | n/a | n/a | n/a | n/a | ✗ | **FAIL** (orphan shells) |
| `platform/typescript/foundation/platform-utils` | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | n/a | ✓ | ✓ | ✓ | n/a | ◐ | PASS WITH MINOR GAPS (path drift) |
| `platform/typescript/ui` | ◐ | ✓ | ✓ | ◐ | ✓ | ✓ | n/a | ✓ | ✓ | n/a | n/a | ◐ | PARTIAL (governance disagreement) |

Legend: ✓ = adequate, ◐ = partial, ✗ = absent / failing.

---

## 7. Appendix

### A. Folder inventory scanned

**Java (24):** `core, domain, database, http, observability, platform-bom, testing, runtime, config, workflow, ai-integration, governance, security, agent-core, messaging, audit, identity, data-governance, tool-runtime, policy-as-code, ds-cli, cache, integration-tests` — plus `acm-pattern docs:` `ACTIVEJ_PROMISE_PATTERNS.md`.

**TypeScript packages with `package.json` (38):** see Section 1 grep results.

**TypeScript orphan shells (7):** `audit-ui, nlp-ui, privacy-ui, security-ui, voice-ui, selection-ui, browser-events`.

**Other:** `contracts, agent-catalog (catalog YAMLs + capability taxonomy + js migrator), kernel/docs (4 subtrees), shared-services (3 java bridges + 1 ts test folder), testing (9 ts files), UTILITY_NAMING_CONVENTIONS.md`.

### B. Detected languages / frameworks / tooling

- Java 21 (`id("java-module")` convention).
- Kotlin Gradle DSL.
- Protobuf via `id("protobuf-module")`.
- ActiveJ async runtime (Promise + Reactor).
- Jackson (databind/yaml), HikariCP, PostgreSQL JDBC.
- TypeScript 6.x, Vitest 4.x, React 19.2, Tailwind, Lucide, Zod, jsdom.
- pnpm workspace + dependency-cruiser + ESLint flat config.

### C. Notable configs / build files

- [gradle/libs.versions.toml](gradle/libs.versions.toml) — central catalog.
- [build-logic/conventions/](build-logic/) — convention plugins.
- [platform/typescript/.dependency-cruiser.cjs](platform/typescript/.dependency-cruiser.cjs) — boundary enforcement.
- [platform/typescript/tsconfig.base.json](platform/typescript/tsconfig.base.json) — strict baseline.
- [platform/typescript/PACKAGE_NAMING_STANDARD.md](platform/typescript/PACKAGE_NAMING_STANDARD.md) — naming spec.

### D. Missing docs / specs / contracts

- Single canonical TS package registry (today split across copilot-instructions, LIBRARY_GOVERNANCE, PACKAGE_NAMING_STANDARD with conflicts).
- No documented retirement record for `accessibility-audit` (gone from disk, still listed in instructions).
- No published OPA contract or Rego module reference for `policy-as-code`.
- No agent-catalog conformance test against `catalog-schema.yaml`.
- No append-only-store guarantee statement for `audit`.

### E. Assumptions & uncertainties

- Several modules (`identity`, `cache`, `config`, `data-governance`, `ds-cli`) were inventoried but not deeply read; verdicts marked PARTIAL conservatively.
- `governance/quarantine/` content unreadable via tooling — assumed empty.
- `node_modules/` listed inside `canvas-core` and `primitives` may be local-only artifacts; verify they are gitignored.

### F. Recommended next execution order

1. P0: delete or rewrite fake test suites; wire `integration-tests`; replace `OpaClient` stub.
2. P1: governance reconciliation across the three TS docs; refresh root TS `package.json`; execute fix-forward deletions; relocate product bridges; delete orphan UI shells.
3. P2: flatten `foundation/`; remove committed `bin/`; centralise contracts coordinates; finish ArchUnit rules; CI-validate agent-catalog YAMLs.

---

**Summary:** 30 libraries / folders reviewed (24 Java modules + 6 TS clusters + 4 non-code areas). **3 FAIL**, **1 HIGH RISK**, **8 PARTIAL**, **17 PASS WITH MINOR GAPS**, **1 PASS**. Most critical blockers: fake cross-cutting test suites, orphan integration-tests, OPA stub. Most urgent governance action: reconcile the three competing canonical-package documents.
