# Audit Remediation Implementation Plan

- **Date:** 2026-04-22
- **Source audit:** `docs/audit-report-2026-04-22.md`
- **Companion TODO list:** `docs/AUDIT_REMEDIATION_TODO_LIST_2026-04-22.md`
- **Governing repo rules:** `.github/copilot-instructions.md` §§29–33
- **Scope:** Audit remediation for `platform/`, `platform-kernel/`, `platform-plugins/`, `shared-services/`, `products/audio-video/`, `products/data-cloud/`, `products/aep/`, and `products/yappc/`

---

## 1. Purpose

This plan converts the audit findings in `docs/audit-report-2026-04-22.md` into an execution roadmap.

It is intended to:
- remove immediate build and wiring blockers,
- restore trust in the test suite,
- fix canonical naming and package-registry drift,
- harden the highest-risk modules,
- establish realistic sequencing, dependencies, and acceptance criteria,
- keep remediation aligned with repo rules instead of introducing one-off local patterns.

This plan is **implementation-focused**, not another audit. It assumes the audit findings are the current baseline.

---

## 2. Authoritative References

### Primary source
- `docs/audit-report-2026-04-22.md`

### Repo rules that govern implementation
- `.github/copilot-instructions.md`
  - **§29** Test authenticity / anti-test-theatre
  - **§30** Module wiring discipline
  - **§31** Stub adapters are not production code
  - **§32** Canonical TypeScript package registry
  - **§33** Platform-vs-product boundary enforcement

### Additional referenced repo contracts
- `gradle/doc-tag-check.gradle`
- `gradle/libs.versions.toml`
- `build-logic/conventions/`
- `platform/java/ACTIVEJ_PROMISE_PATTERNS.md`
- `platform/typescript/LIBRARY_GOVERNANCE.md` (must follow Section 32 of the repo instructions, not redefine it)

---

## 3. Scope and Non-Goals

### In scope
- Every remediation item explicitly identified in the audit ledger:
  - `W-1…W-4`
  - `T-001…T-053`
  - `D-1…D-17`
  - `P-1…P-15`
  - `AR-1…AR-5`
  - `A-1…A-5`
  - `KP-1…KP-5`
  - `AT-1…AT-11`
  - `DE-1…DE-4`
  - `DC-1…DC-3`
  - `AV-1…AV-4`
  - `YS-1…YS-3`
  - `YD-1…YD-3`
  - `AG-1…AG-4`
  - `HA-1…HA-3`
  - blockers `B-1…B-4`
- Supporting CI and guardrail changes required to keep regressions from returning.
- Minimal refactors needed to complete remediation safely.

### Not in scope
- Broad architectural rewrites not required to close audit findings.
- New product capabilities unrelated to audit remediation.
- Cosmetic documentation cleanup unrelated to traceability or broken instructions.
- Any backward-compatibility shims for deprecated package names; remediation is **fix-forward**.

---

## 4. Repo Constraints That Must Shape All Work

### 4.1 Test authenticity is mandatory
Per `.github/copilot-instructions.md` §29:
- no `expect(true).toBe(true)` placeholders,
- no tests that do not import production code,
- no committed disabled tests without a real ticket reference,
- no object-literal “pretend subject” tests.

### 4.2 Module wiring must be explicit
Per §30:
- every Java module must have its own `build.gradle.kts` and be included in the relevant settings,
- every TS package must have its own `package.json` and `tsconfig.json`,
- empty or orphan modules must be either implemented or removed.

### 4.3 No deprecated package names
Per §32 and §25:
- `@ghatana/ui` must be replaced with `@ghatana/design-system`,
- `@ghatana/accessibility-audit` must be replaced with `@ghatana/accessibility`,
- no alias shims should be introduced.

### 4.4 Platform/product boundaries must remain clean
Per §33:
- do not respond to product-local issues by stuffing more product logic into `platform/`,
- keep `kernel-bridge` and product-owned adapters under the owning product,
- converge duplicates only where the abstraction is truly product-agnostic.

### 4.5 Stub adapters are not acceptable
Per §31:
- no substring-based protocol parsing,
- no “TODO before production” integration paths,
- no fake production adapters disguised as real implementations.

---

## 5. Remediation Inventory and Traceability

| Group | IDs | Description | Audit anchor |
|---|---|---|---|
| Wiring and module viability | `W-1…W-4` | Wire or delete orphan/empty modules | Audit §6.4, §9.1 |
| Test theatre cleanup | `T-001…T-053` | Rewrite or delete placeholder TS tests | Audit §6.2, §9.2 |
| Disabled Java tests | `D-1…D-17` | Re-enable or explicitly ticket blocked tests | Audit §6.3, §9.3 |
| Canonical package fixes | `P-1…P-15` | Fix-forward deprecated `@ghatana/*` names | Audit §6.5, §9.4 |
| AEP central registry hardening | `AR-1…AR-5` | Contract, integration, tenant, load coverage | Audit §6.6 |
| Agent-core hardening | `A-1…A-5` | Tenant isolation, cancellation, lifecycle | Audit §6.6 |
| Kernel plugin SPI hardening | `KP-1…KP-5` | Lifecycle, isolation, version mismatch, leaks | Audit §6.6 |
| AEP runtime hardening | `AT-1…AT-11` | Agent taxonomy, eval, resilience | Audit §6.6 |
| Data Cloud entity hardening | `DE-1…DE-4` | Tenant, concurrency, audit, contracts | Audit §6.6 |
| Data Cloud config hardening | `DC-1…DC-3` | Validation, precedence, reload | Audit §6.6 |
| Audio/video streaming hardening | `AV-1…AV-4` | Throughput, recovery, contract, ordering | Audit §6.6 |
| YAPPC shared hardening | `YS-1…YS-3` | DTO round-trip, util boundaries, arch rules | Audit §6.6 |
| YAPPC domain hardening | `YD-1…YD-3` | Invariants, ports, idempotency | Audit §6.6 |
| Auth gateway hardening | `AG-1…AG-4` | Replay, substitution, rotation, OIDC failure | Audit §6.6 |
| Human approval hardening | `HA-1…HA-3` | FSM, quorum, timeout escalation | Audit §6.6 |
| Blockers | `B-1…B-4` | Preconditions to unlock other work | Audit §1, §6 |

---

## 6. Phased Execution Roadmap

## Phase 0 — Remove blockers and decide dead-vs-live modules

### Objective
Close the structural issues that prevent valid builds, valid tests, or realistic remediation sequencing.

### In-scope items
- `W-1…W-4`
- `B-1…B-4`

### Work items
1. **`W-1` / `B-1`:** Resolve `shared-services/integration-tests/`
   - Decide whether it is a real module.
   - If real: add `build.gradle.kts`, register in the relevant settings, wire standard test dependencies.
   - If dead: remove it completely.
2. **`W-2`:** Resolve `products/data-cloud/platform-client/`
   - Either implement the missing source and tests or delete the shell.
3. **`W-3`:** Resolve `products/yappc/launcher/`
   - Either implement the module or delete it.
4. **`W-4` / `B-2`:** Resolve `platform-kernel/kernel-persistence/`
   - Verify whether it is Kotlin-based or truly empty.
   - If empty: either implement foundational persistence code or remove the module.
5. **`B-3`:** Prepare `aep-registry` integration-test prerequisites.
   - Add Testcontainers-backed database fixture support.
   - Ensure Gradle dependencies exist for integration tests.
6. **`B-4`:** Create evaluation-fixture strategy for AI/ML modules.
   - Define storage location for committed eval fixtures.
   - Decide fixture format and baseline ownership.

### Dependencies
- None. This is the first phase.

### Expected outputs
- No orphan or empty production-facing modules remain unresolved.
- `aep-registry` is testable with infrastructure-backed integration tests.
- AI/ML eval work has a committed fixture strategy.

### Acceptance criteria
- `W-1…W-4` each end in one of: **implemented and wired**, or **deleted**.
- `B-3` is closed with runnable integration-test plumbing.
- `B-4` is closed with committed fixture directory conventions.

### Key risks
- Teams may want to defer deletion decisions; do not leave ambiguous shells in place.
- `kernel-persistence` may reveal hidden consumers when touched.

### Rollback/containment
- If deletion is risky, first remove the module from build inclusion and document the dependency impact before hard delete.

---

## Phase 1 — Restore trust in the test suite

### Objective
Eliminate false confidence by removing placeholder tests and resolving disabled tests on critical paths.

### In-scope items
- `T-001…T-053`
- `D-1…D-17`

### Work items
1. Rewrite or delete all TS theatre tests.
2. Re-enable critical Java tests first:
   - `D-1` `AgentTenantIsolationTest`
   - `D-2` `KernelEndToEndTest`
   - `D-3` `KernelModuleIntegrationTest`
   - `D-4` `CircularDependencyDetectionTest`
   - `D-5` `RegulatoryComplianceTest`
   - `D-6` `PhrFinanceIntegrationTest`
   - `D-7` `BrokerConnectorIntegrationTest`
   - `D-8` `PlatformArchitectureTest`
3. For `D-9…D-17`, either:
   - re-enable and fix, or
   - annotate with a real ticket reference and explicit unblock condition.
4. Add CI protection to prevent theatre regressions.
   - Example: fail if new placeholder assertions or unticketed disabled tests are introduced.

### Dependencies
- Phase 0 should be complete first, especially if disabled tests rely on missing wiring.

### Expected outputs
- No placeholder TS tests remain in the audited scope.
- No unticketed disabled tests remain in the audited scope.
- CI guard prevents recurrence.

### Acceptance criteria
- `grep`-based theatre detection returns zero for audited roots.
- All disabled tests are either active or explicitly linked to an approved ticket.
- Critical trust-restoring tests (`D-1…D-8`) are not skipped.

### Key risks
- Some tests may have been disabled due to legitimate infra flakiness.
- Re-enabling architecture tests may expose long-hidden violations.

### Rollback/containment
- For unstable tests, convert them into isolated infra-backed jobs with a ticket, but do not silently disable them again.

---

## Phase 2 — Fix-forward canonical package and naming drift

### Objective
Remove forbidden package names and broken observable outputs.

### In-scope items
- `P-1…P-15`

### Work items
1. Replace the live alias in `platform/typescript/patterns/vitest.config.ts`.
2. Replace observable SARIF tool-driver naming in accessibility formatter output.
3. Rewrite broken docs and JSDoc references to canonical package names.
4. Add lint enforcement to block deprecated package names repo-wide.

### Dependencies
- None beyond normal review/testing, but Phase 1 is recommended first so signal quality improves before naming churn lands.

### Expected outputs
- No live use of forbidden `@ghatana/*` names remains in the audited roots.
- No generated output emits deprecated package identifiers.

### Acceptance criteria
- `grep` for forbidden package names returns zero in the audited roots, except historical docs intentionally outside active source.
- Accessibility formatter tests validate the new canonical emitted name.

### Key risks
- `P-2` changes an observable output contract; downstream tooling may expect the old SARIF name.

### Rollback/containment
- Coordinate the SARIF output rename with consumers in the same change window or document a one-release compatibility note outside the codebase.

---

## Phase 3 — Harden AEP Central Registry

### Objective
Raise confidence in the canonical agent-registry implementation.

### In-scope items
- `AR-1…AR-5`

### Work items
1. Add round-trip create/read integration coverage.
2. Add execute-path integration coverage for `/api/v1/agents/:agentId/execute`.
3. Add schema/contract verification against platform contracts.
4. Add tenant isolation unit coverage.
5. Add load testing for registry reads.

### Dependencies
- `B-3` must be closed.

### Expected outputs
- AEP registry behavior is proven by contract, integration, and load tests.
- Canonical routes are explicitly exercised in tests.

### Acceptance criteria
- `AR-1…AR-4` run in CI Tier 1.
- `AR-5` runs in nightly or dedicated perf tier.
- Registry routes and payloads are traceable back to canonical contracts.

### Key risks
- The audit found uncertainty around where `/api/v1/agents` is wired.
- Route composition may be indirect, making test setup harder.

### Rollback/containment
- If route ownership is ambiguous, first add an endpoint-discovery test or boot-level route assertion before deeper coverage.

---

## Phase 4 — Harden platform core runtime surfaces

### Objective
Close the most dangerous platform-level correctness gaps shared by multiple products.

### In-scope items
- `A-1…A-5`
- `KP-1…KP-5`

### Work items
1. Re-enable and repair `D-1`, then complete `A-1…A-5` in `platform/java/agent-core`.
2. Add the full kernel plugin SPI lifecycle and isolation test matrix.
3. Ensure tests follow ActiveJ async guidance (`EventloopTestBase`, `runPromise(...)`).

### Dependencies
- `A-1` depends on `D-1` being active.
- `KP-*` benefits from Phase 1 because disabled kernel tests may expose shared infra assumptions.

### Expected outputs
- Agent-core tenant safety is proven.
- Plugin SPI lifecycle and failure behavior are explicitly verified.

### Acceptance criteria
- `A-1…A-5` green in CI.
- `KP-1…KP-5` green in CI.
- No event-loop blocking or `.getResult()` misuse is introduced in new tests.

### Key risks
- Plugin lifecycle tests may uncover memory/classloader issues that require product-level fixes.

### Rollback/containment
- Keep SPI and agent-core remediation in narrowly scoped PRs to avoid cross-module merge conflicts.

---

## Phase 5 — Close high-risk module gaps

### Objective
Raise the twelve high-risk modules and adjacent critical modules out of high-risk status.

### In-scope items
- `AT-1…AT-11`
- `DE-1…DE-4`
- `DC-1…DC-3`
- `AV-1…AV-4`
- `YS-1…YS-3`
- `YD-1…YD-3`
- `AG-1…AG-4`
- `HA-1…HA-3`

### Work items by stream

#### AEP runtime
- Complete taxonomy coverage across all canonical agent types.
- Add timeout/retry/circuit-break tests around LLM or remote runtime dependencies.

#### Data Cloud entity/config
- Add tenant isolation, optimistic concurrency, audit-preserving delete, config precedence, and reload tests.

#### Audio/video streaming
- Add throughput, resilience, wire-contract, and frame-ordering tests.

#### YAPPC core
- Add shared DTO round-trip, architecture rules, domain invariants, and idempotency checks.

#### Shared services auth / approval plugins
- Add replay/substitution/rotation/OIDC failure tests.
- Add approval FSM, quorum, and timeout-escalation coverage.

### Dependencies
- `AT-10` depends on `B-4`.
- Some `AV-*` work may require dedicated perf environment or fixtures.

### Expected outputs
- Each currently high-risk area has clear behavior coverage rather than file-count improvement only.

### Acceptance criteria
- Each listed ID is implemented and mapped back to the audit ledger.
- High-risk modules are reassessed and reduced to at least **PARTIAL**, ideally **PASS WITH MINOR GAPS**.

### Key risks
- Performance and streaming tests may be expensive or flaky without dedicated isolation.
- Domain invariants may expose production-code bugs rather than only test gaps.

### Rollback/containment
- Keep perf/load tests in Tier 2 if they are too expensive for PR checks, but do not omit them.

---

## Phase 6 — AI/ML evaluation and performance tiers

### Objective
Turn currently unverified AI/ML and performance claims into measurable, repeatable checks.

### In-scope items
- `AT-10`
- AI/ML eval items implied by audit §6.8
- perf/load items implied by audit §6.7

### Work items
1. Add committed eval fixture sets for:
   - `platform/java/ai-integration`
   - `products/yappc/core/ai`
   - `products/aep/aep-agent-runtime`
2. Define acceptance thresholds per suite.
3. Add or expand performance tiers for:
   - `aep-registry`
   - `data-cloud/platform-launcher`
   - `audio-video/{audio,video}-streaming`
   - `yappc/core/yappc-services`

### Dependencies
- `B-4` must be resolved.
- Load/perf infrastructure should be stable enough to avoid false positives.

### Expected outputs
- AI/ML behavior is regression-tested against golden sets.
- Performance claims have repeatable benchmarks or load tests.

### Acceptance criteria
- Eval datasets are committed and versioned.
- Threshold failures are visible in CI/nightly reporting.
- No AI/ML-critical module remains with zero evaluation coverage.

### Key risks
- Teams may resist locking acceptance thresholds if current behavior is unstable.

### Rollback/containment
- Start with warning-mode reporting only if thresholds need one baseline run, but convert to gating quickly.

---

## Phase 7 — Refactor and standardization follow-through

### Objective
Address the architectural issues the audit identified after immediate correctness and trust problems are fixed.

### In-scope items
- Audit §7 refactor plan

### Workstreams
1. Consolidate platform duplication across product-local `platform-*` modules where abstraction is truly shared.
2. Promote reusable launcher-test harness patterns into `platform/java/testing`.
3. Consolidate `agent-catalog` ownership around the canonical platform catalog.
4. Centralize agent registry usage behind AEP where appropriate.
5. Ensure doc-tag enforcement is wired into standard checks.
6. Extend lint/build checks so deprecated package names and theatre tests cannot regress.

### Dependencies
- Earlier remediation phases should be complete first.

### Expected outputs
- Fewer duplicate abstractions.
- Stronger shared testing and governance patterns.
- Better long-term maintainability.

### Acceptance criteria
- Each refactor has clear ownership and a dependency-impact review.
- No product-specific logic leaks into `platform/` during convergence.

### Key risks
- Large-scope refactors can destabilize already-fixed modules.

### Rollback/containment
- Run refactors as separate change streams after correctness and trust work is stable.

---

## 7. Cross-Workstream Dependencies

| Dependency | Reason |
|---|---|
| `W-1` resolves `B-1` | `shared-services/integration-tests` cannot run until wired or removed. |
| `W-4` resolves `B-2` | `kernel-persistence` must exist meaningfully before persistence tests can be authored. |
| `AR-1…AR-5` depend on `B-3` | AEP registry integration tests need infrastructure fixtures and dependencies. |
| AI/ML eval items depend on `B-4` | Golden-set fixtures do not exist yet. |
| `A-1` depends on `D-1` | Tenant isolation cannot be proven while the canonical test is disabled. |
| Phase 1 depends partially on Phase 0 | Orphan/empty modules distort test execution and CI trust. |
| Phase 5 depends partially on Phases 1–4 | High-risk coverage should build on trusted test and route foundations. |

---

## 8. Delivery Structure and Recommended PR Waves

| Wave | Scope | Target outcome |
|---|---|---|
| PR-1 | `W-1…W-4`, `B-1…B-4` | No unresolved shells/orphans; unblock tests and eval strategy |
| PR-2 | `T-001…T-053` + CI theatre guard | No placeholder tests remain |
| PR-3 | `D-1…D-17` | Critical disabled tests re-enabled or ticketed explicitly |
| PR-4 | `P-1…P-15` + lint rule | No deprecated package names in active source |
| PR-5 | `AR-1…AR-5` | AEP registry contract and integration confidence |
| PR-6 | `A-1…A-5`, `KP-1…KP-5` | Platform runtime hardening complete |
| PR-7 | `AT-*`, `DE-*`, `DC-*`, `AV-*`, `YS-*`, `YD-*`, `AG-*`, `HA-*` | High-risk module coverage uplift |
| PR-8 | AI/ML eval + perf/load tiers | Measurable quality/performance gates |
| PR-9 | Audit §7 refactors | Long-term standardization and deduplication |

---

## 9. Test Strategy

### Tier 0 — PR gating
- unit tests,
- no-theatre guard,
- no unticketed disabled tests,
- fast static validation.

### Tier 1 — Post-merge / required integration
- Testcontainers-based integration tests,
- contract tests,
- boot-level integration flows,
- architecture tests.

### Tier 2 — Nightly / scheduled
- load tests,
- soak tests,
- AI/ML evaluation suites,
- cross-product end-to-end flows.

### Test-design rules
- Prefer real subject execution over over-mocking.
- Validate observable behavior, not implementation trivia.
- Keep Java async tests aligned with ActiveJ rules.
- Keep TypeScript tests fully typed and co-located per repo guidance.

---

## 10. Risk Register

| Risk | Affected items | Mitigation |
|---|---|---|
| Hidden consumers of empty/orphan modules | `W-1…W-4` | Search references before delete; remove from build first if needed |
| Disabled tests are flaky because infra is missing | `D-*` | Move to correct tier, add fixture support, do not silently skip |
| Observable output compatibility break | `P-2` | Coordinate SARIF consumer update and validate output tests |
| Route ownership ambiguity in AEP registry | `AR-*` | Add route-discovery or boot tests first |
| Missing AI/ML datasets | `B-4`, `AT-10` | Define committed fixture policy before eval authoring |
| Performance tests too noisy in PR CI | `AR-5`, `AV-*`, perf items | Run in Tier 2 with stable infra and thresholds |
| Refactor work reintroduces boundary drift | Phase 7 | Enforce §33 during design review |

---

## 11. Definition of Done

This remediation plan is complete only when all of the following are true:

- Every audit remediation ID in this plan is either completed or explicitly closed as not applicable with justification.
- No placeholder/assertion-theatre tests remain in the audited roots.
- No unticketed `@Disabled`, `it.skip`, or equivalent disabled tests remain on critical paths.
- No forbidden `@ghatana/*` package names remain in active source or live emitted outputs.
- Every previously orphaned or empty module is either implemented and wired or removed.
- AEP registry routes are proven by contract/integration coverage.
- Agent-core tenant isolation and kernel plugin lifecycle are explicitly tested.
- AI/ML-critical modules have committed evaluation fixtures and measurable checks.
- High-risk modules identified in the audit are re-evaluated and no longer sit in `HIGH RISK` due solely to missing core coverage.
- CI contains guardrails to prevent regressions in module wiring, package naming, and test authenticity.

---

## 12. Recommended Execution Notes

- Keep each PR tightly scoped to a single wave or single module group.
- Preserve audit IDs in PR descriptions and commit messages for traceability.
- Update `docs/audit-report-2026-04-22.md` only if a remediation materially changes an audit conclusion.
- If any audit assumption proves false during implementation, record the correction in the PR and amend this plan rather than silently deviating.

