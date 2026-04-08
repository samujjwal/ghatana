# Test Remediation Sprint Plan

**Status:** Proposed Execution Plan  
**Owner:** Engineering Leadership + Product Engineering Leads  
**Scope:** Repo-wide test quality, coverage enforcement, traceability, and critical-path hardening  
**Planning Basis:** Audit findings from April 8, 2026  
**Sprint Cadence:** 2 weeks per sprint  
**Execution Mode:** Parallel workstreams with strict release gates

## 1. Purpose

This plan converts the audit findings into a sprint-by-sprint execution program with:

- concrete tasks
- owners by workstream
- dependency order
- acceptance criteria
- release gates
- no orphaned audit findings

This plan is intentionally grounded in current code, tests, and checked-in coverage artifacts. Where older documentation conflicts with the codebase, this plan treats executable evidence as the source of truth and schedules documentation reconciliation as explicit work.

## 2. Non-Negotiable Quality Rules

1. Tests must validate business outcomes, not implementation trivia.
2. Critical-path tests may not be skipped in release-gated suites.
3. Coverage artifacts must exist for every release-gated module.
4. Structural coverage is necessary but not sufficient; requirement and use-case coverage are also mandatory.
5. Stale documentation is a defect and must be reconciled.
6. No release may proceed with unresolved P0 or P1 items in this plan.

## 3. Workstreams

| Workstream | Prefix | Scope | Primary Owner |
|---|---|---|---|
| Quality Gates | `QG` | CI gates, coverage thresholds, artifact enforcement | Build/DevEx |
| Test Quality | `TQ` | weak assertions, skipped tests, standards | QA + Product Eng |
| Kernel | `KRN` | dependency safety, lifecycle rollback, plugin lifecycle | Platform Kernel |
| YAPPC | `YAP` | canvas/editor critical journeys, frontend release gate | YAPPC Frontend |
| Data-Cloud | `DC` | requirement closure, plugin/security/perf gaps | Data-Cloud Team |
| PHR | `PHR` | regulated workflows, FHIR, emergency access | PHR Team |
| Finance | `FIN` | approval races, idempotency, autonomy, load | Finance Team |
| Traceability | `TRC` | requirement-use-case-test mapping, reporting | Architecture + QA |
| Documentation | `DOC` | stale-doc reconciliation and governance | Architecture + Tech Writing |

## 4. Master Task Register

Every task below is assigned to a sprint later in this document.

| ID | Task | Outcome |
|---|---|---|
| `DOC-001` | Create canonical audit baseline and supersede stale claims | One trusted source of truth |
| `DOC-002` | Reconcile root traceability docs with current code/tests | No contradictory governance docs |
| `TRC-001` | Build repo-wide requirement -> use case -> test matrix | Executable traceability |
| `TRC-002` | Add machine-readable manifests for requirements and test mapping | CI-readable traceability |
| `TRC-003` | Publish per-run requirement/use-case coverage reports | Behavioral visibility |
| `QG-001` | Remove zero-threshold JaCoCo pass-through in shared Java build | Real Java coverage gate |
| `QG-002` | Normalize Java thresholds to repo standard | Consistent enforcement |
| `QG-003` | Normalize TS/Vitest thresholds to repo standard | Consistent enforcement |
| `QG-004` | Fail CI when coverage artifacts are missing | No false-green coverage |
| `QG-005` | Fail CI on skipped tests in release-gated suites | No silent holes |
| `QG-006` | Require direct test coverage for changed critical-path files | Change-level protection |
| `QG-007` | Add release-gated suite manifest per product | Explicit gate scope |
| `TQ-001` | Build weak-assertion detector for critical-path tests | Outcome-first assertions |
| `TQ-002` | Rewrite shallow critical-path tests to assert truth and outcomes | Better failure signal |
| `TQ-003` | Ban new weak assertions in gated suites | Quality stays improved |
| `KRN-001` | Make kernel topological sort fail fast on cycles | Cycle-safe startup |
| `KRN-002` | Replace disabled cycle suite with active tests against current contract | Live cycle protection |
| `KRN-003` | Add start/rollback failure-path tests for kernel lifecycle | Verified rollback semantics |
| `KRN-004` | Add plugin lifecycle install/uninstall/reinstall coverage | Plugin lifecycle confidence |
| `KRN-005` | Add nightly concurrency stress runs for registry operations | Race-condition guardrail |
| `YAP-001` | Reactivate canvas persistence/reload E2E coverage | Saved work is protected |
| `YAP-002` | Reactivate keyboard shortcut and multi-select coverage | Editor interaction confidence |
| `YAP-003` | Reactivate `useCanvasScene` behavior/integration suites | State-sync confidence |
| `YAP-004` | Make core YAPPC journeys release-gated | Real release confidence |
| `YAP-005` | Burn down skipped YAPPC tests outside release gate | Reduce latent risk |
| `DC-001` | Build requirement-to-test map for all partial Data-Cloud requirements | No unknown coverage holes |
| `DC-002` | Add plugin lifecycle/isolation/update/communication suites | Close plugin-system gap |
| `DC-003` | Add tenant-isolation and security integration suites | Security confidence |
| `DC-004` | Add performance and load validation for critical targets | Performance evidence |
| `DC-005` | Close AI/ML partial requirement coverage gaps | End-to-end ML confidence |
| `PHR-001` | Reconcile PHR strategy with actual tests and modules | Accurate regulated backlog |
| `PHR-002` | Add missing FHIR endpoint/search/validation/security suites | FHIR contract confidence |
| `PHR-003` | Add emergency break-glass, audit, expiry, revocation, load suites | Safety-critical coverage |
| `PHR-004` | Add Nepal HIE failure-path integration suites | External integration resilience |
| `PHR-005` | Add HIPAA/privacy/breach workflow coverage closure | Compliance confidence |
| `FIN-001` | Build finance requirement/use-case matrix | Explicit high-risk scope |
| `FIN-002` | Add model approval race-condition and stale-approval tests | Governance confidence |
| `FIN-003` | Add transaction autonomy and idempotency failure-path suites | Financial safety |
| `FIN-004` | Add inference outage/retry/rollback suites | Resilience confidence |
| `FIN-005` | Add staged load and latency validation for finance critical flows | Performance evidence |

## 5. Sprint Plan

## Sprint 1: Truth And Gates

**Goal:** establish a correct baseline and stop false-green builds.

**Tasks**

| ID | Task | Dependencies | Deliverable | Acceptance Criteria |
|---|---|---|---|---|
| `DOC-001` | Create canonical audit baseline doc | None | Audit baseline | Lists trusted evidence sources, stale-doc exceptions, and current facts |
| `DOC-002` | Reconcile root traceability/risk docs with current code/tests | `DOC-001` | Updated docs or supersession notes | No known contradictions on kernel cycle/concurrency or PHR test counts |
| `TRC-001` | Create first-pass repo traceability matrix | `DOC-001` | Traceability matrix | Kernel, YAPPC, Data-Cloud, PHR, Finance all represented |
| `QG-001` | Remove zero-threshold Java gate in shared build | None | Build change | Shared Java build fails when coverage is absent or below target |
| `QG-002` | Set Java thresholds to standard | `QG-001` | Threshold config | Default Java gate is at least 85% line / 80% branch |
| `QG-003` | Set TS thresholds to standard | None | Threshold config | Default TS gate is at least 85% line / 80% branch |
| `QG-004` | Fail CI on missing coverage artifacts | `QG-001`, `QG-003` | CI rule | Missing coverage output fails the workflow |
| `QG-007` | Add release-gated suite manifest template per product | None | Manifest skeletons | Kernel, YAPPC, Data-Cloud, PHR, Finance each have a manifest |

**Sprint 1 exit criteria**

- shared Java no longer permits `0.00` thresholds
- TS release workflows no longer rely on optional strict mode
- missing coverage artifacts fail CI
- traceability matrix exists in first-pass form
- stale root docs are explicitly marked or corrected

## Sprint 2: Test Quality Foundation

**Goal:** improve the signal quality of tests before adding more of them.

**Tasks**

| ID | Task | Dependencies | Deliverable | Acceptance Criteria |
|---|---|---|---|---|
| `TQ-001` | Build weak-assertion detector | `QG-007` | Script + CI integration | Flags `toBeDefined`, `toBeTruthy`, status-only checks in gated suites |
| `TQ-002` | Rewrite shallow critical-path tests in representative modules | `TQ-001` | Test rewrites | At least kernel, YAPPC canvas, DC pages/services, DCMAAR example suites upgraded |
| `TQ-003` | Ban new weak assertions in gated suites | `TQ-001` | CI rule | New weak assertions fail PRs touching gated suites |
| `QG-005` | Fail CI on skipped tests in release-gated suites | `QG-007` | CI rule | Any `skip` in gated suites fails |
| `QG-006` | Require direct tests for changed critical-path files | `QG-007` | CI rule | Changes to gated paths require mapped tests |
| `TRC-002` | Add machine-readable manifests | `TRC-001` | JSON/TOML manifests | Requirements and test maps can be parsed in CI |

**Sprint 2 exit criteria**

- weak assertion detector runs in CI
- gated suites cannot introduce new shallow assertions
- skipped tests are blocked in gated suites
- traceability manifests are machine-readable

## Sprint 3: Kernel Hardening

**Goal:** eliminate kernel lifecycle ambiguity and make dependency failures explicit.

**Tasks**

| ID | Task | Dependencies | Deliverable | Acceptance Criteria |
|---|---|---|---|---|
| `KRN-001` | Make `topologicalSort()` fail fast on cycles | `QG-001` | Kernel code change | Cycle causes explicit failure, not partial order |
| `KRN-002` | Replace disabled cycle suite with active tests against current contract | `KRN-001` | Active cycle tests | Disabled archived assumptions removed from release confidence path |
| `KRN-003` | Add `startAllModules()` rollback and partial-failure tests | `KRN-001` | New tests | Failed startup proves reverse-order rollback and error propagation |
| `KRN-004` | Add plugin lifecycle tests | `KRN-001` | New tests | install/uninstall/reinstall and capability cleanup covered |
| `KRN-005` | Add nightly concurrency registry stress suite | `KRN-001` | Nightly CI job | concurrent register/unregister/resolve/start exercises run regularly |
| `TRC-003` | Publish requirement/use-case coverage for kernel | `TRC-002` | Coverage report | Kernel requirement matrix points to active tests and artifacts |

**Sprint 3 exit criteria**

- kernel cycle handling is explicit and tested
- rollback semantics are proven
- plugin lifecycle is covered
- concurrency stress is automated nightly

## Sprint 4: YAPPC Critical Journey Recovery

**Goal:** make YAPPC release confidence come from actual editor journeys, not non-gated broad test volume.

**Tasks**

| ID | Task | Dependencies | Deliverable | Acceptance Criteria |
|---|---|---|---|---|
| `YAP-004` | Define exact YAPPC release-gated journeys | `QG-007`, `TRC-001` | Gated journey manifest | Includes auth, AI intent-to-generation, tenant isolation, startup health, canvas edit/save/reload |
| `YAP-001` | Reactivate canvas persistence/reload tests | `QG-005` | Active E2E tests | Save and reload preserve expected canvas state |
| `YAP-002` | Reactivate keyboard shortcut and multi-select tests | `QG-005` | Active E2E tests | Select all, copy/paste, duplicate, multi-select assert exact outcomes |
| `YAP-003` | Reactivate `useCanvasScene` behavior/integration suites | `QG-005` | Active unit/integration tests | No-op changes do not persist; real changes do |
| `YAP-005` | Burn down skipped YAPPC tests outside release gate, tranche 1 | `YAP-004` | Skip reduction | Total skipped YAPPC tests reduced by at least 50% |

**Sprint 4 exit criteria**

- all release-gated YAPPC tests are active
- canvas persistence and core editor interactions are covered
- skipped YAPPC test count is materially reduced

## Sprint 5: Data-Cloud Requirement Closure

**Goal:** close the largest documented gap between implemented requirements and tested requirements.

**Tasks**

| ID | Task | Dependencies | Deliverable | Acceptance Criteria |
|---|---|---|---|---|
| `DC-001` | Map every partial Data-Cloud requirement to concrete suites | `TRC-001` | Requirement closure matrix | Every `Partially Tested` item has a backlog entry and owner |
| `DC-002` | Add plugin lifecycle/isolation/update/communication suites | `DC-001` | New unit/integration/API suites | Plugin system coverage no longer materially trails core areas |
| `DC-003` | Add tenant-isolation and security integration suites | `DC-001` | Security suites | Cross-tenant access, authz, privacy controls are asserted at outcome level |
| `DC-005` | Add AI/ML partial coverage closure suites | `DC-001` | AI/ML suites | experiment tracking, feature engineering, model monitoring, A/B cases covered |
| `DC-004` | Add performance/load validation for defined targets | `DC-001` | Benchmarks + load tests | API, query, event throughput, and concurrency targets have measured evidence |

**Sprint 5 exit criteria**

- every partial Data-Cloud requirement is either closed or explicitly deferred with owner and risk
- plugin/security/AI gaps are substantially reduced
- performance evidence exists for critical targets

## Sprint 6: PHR Regulated Workflow Closure

**Goal:** close gaps in regulated healthcare flows where business and compliance failure costs are highest.

**Tasks**

| ID | Task | Dependencies | Deliverable | Acceptance Criteria |
|---|---|---|---|---|
| `PHR-001` | Reconcile strategy with actual code/tests and regulated scope | `DOC-002`, `TRC-001` | PHR truth matrix | Existing tests and missing suites are accurately enumerated |
| `PHR-002` | Add missing FHIR endpoint/search/validation/security suites | `PHR-001` | FHIR suites | CRUD, bundle/search, auth, consent, validation covered |
| `PHR-003` | Add emergency access workflow suites | `PHR-001` | Emergency suites | dual authorization, expiry, revocation, audit, load, failure modes covered |
| `PHR-004` | Add Nepal HIE failure-path integration suites | `PHR-001` | Integration suites | timeout, malformed payload, retry, partial failure covered |
| `PHR-005` | Add HIPAA/privacy/breach workflow closure suites | `PHR-001` | Compliance suites | audit trail, PHI handling, breach workflow outcomes asserted |

**Sprint 6 exit criteria**

- regulated FHIR and emergency flows are mapped and tested
- safety-critical failure paths are covered
- PHR strategy doc aligns with reality

## Sprint 7: Finance High-Risk Flow Closure

**Goal:** harden financial autonomy, governance, idempotency, and latency-sensitive flows.

**Tasks**

| ID | Task | Dependencies | Deliverable | Acceptance Criteria |
|---|---|---|---|---|
| `FIN-001` | Build finance requirement/use-case matrix | `TRC-001` | Finance truth matrix | transaction, approval, governance, inference, audit flows mapped |
| `FIN-002` | Add model approval race and stale-approval suites | `FIN-001` | Governance suites | competing approvals and invalid transitions fail correctly |
| `FIN-003` | Add transaction autonomy and idempotency failure-path suites | `FIN-001` | Transaction suites | duplicate submit, retry, stale state, blocked autonomy covered |
| `FIN-004` | Add inference outage/retry/rollback suites | `FIN-001` | Resilience suites | model outage and recovery preserve correct financial behavior |
| `FIN-005` | Add staged load and latency validation | `FIN-001` | Performance evidence | critical paths have measured latency and throughput evidence |

**Sprint 7 exit criteria**

- high-risk finance flows have explicit outcome-based coverage
- approval and idempotency races are tested
- performance evidence exists for critical financial paths

## Sprint 8: Closure, Reporting, And Release Standardization

**Goal:** lock in the improvements so coverage, traceability, and release quality do not regress.

**Tasks**

| ID | Task | Dependencies | Deliverable | Acceptance Criteria |
|---|---|---|---|---|
| `TRC-003` | Publish per-run behavioral coverage reports in CI | `TRC-002`, product matrices | CI reports | Requirement and use-case coverage visible on every gated run |
| `QG-004` | Extend artifact verification to all release modules | Prior sprint coverage work | CI rule | All gated modules publish and validate coverage output |
| `YAP-005` | Burn down skipped YAPPC tests, tranche 2 | `Sprint 4` | Skip reduction | No skipped tests remain in critical YAPPC suites |
| `DOC-002` | Final documentation reconciliation pass | All product truth matrices | Updated docs | Traceability and risk docs reflect current codebase and suites |
| `Release Readiness Review` | Cross-team signoff on standards | All prior sprints | Signoff package | P0/P1 items closed or formally deferred with approval |

**Sprint 8 exit criteria**

- CI publishes structural and behavioral coverage
- release-gated modules all emit fresh artifacts
- critical YAPPC skips are zero
- traceability docs are current
- leadership signoff package exists

## 6. Cross-Sprint Dependencies

| Dependency | Why It Matters |
|---|---|
| `QG-001` before most product hardening | otherwise weak gates hide regressions |
| `QG-005` before YAPPC reactivation | otherwise skips can remain silently |
| `TRC-001` before product closure sprints | otherwise requirement closure cannot be proven |
| `KRN-001` before kernel rollout signoff | cycle ambiguity undermines platform safety |
| `PHR-001` and `FIN-001` before deep suite expansion | prevents adding tests against stale assumptions |

## 7. Release Gates By End State

These are the standards the repo must meet at the end of this program.

### Structural gates

- Java default gate: `>= 85%` line, `>= 80%` branch
- TypeScript default gate: `>= 85%` line, `>= 80%` branch
- Critical-path modules: `>= 90%` line, `>= 90%` branch
- Missing coverage artifacts fail

### Behavioral gates

- 100% of release-gated journeys covered
- 100% of regulated and security-critical flows covered
- 100% of rollback, idempotency, authorization, and failure-path scenarios covered for critical flows
- every implemented critical requirement mapped to executable tests

### Test quality gates

- no skipped tests in release-gated suites
- no weak assertions in release-gated suites
- no status-code-only API tests for critical workflows

## 8. Definition Of Done

A workstream is done only when all of the following are true:

1. implementation is merged
2. tests are active and passing
3. coverage artifacts are produced in CI
4. traceability mapping is updated
5. stale documentation is reconciled
6. release-gated suites remain skip-free

## 9. No-Gap Verification Checklist

Use this checklist at each sprint review.

- `DOC-001` assigned
- `DOC-002` assigned
- `TRC-001` assigned
- `TRC-002` assigned
- `TRC-003` assigned
- `QG-001` assigned
- `QG-002` assigned
- `QG-003` assigned
- `QG-004` assigned
- `QG-005` assigned
- `QG-006` assigned
- `QG-007` assigned
- `TQ-001` assigned
- `TQ-002` assigned
- `TQ-003` assigned
- `KRN-001` assigned
- `KRN-002` assigned
- `KRN-003` assigned
- `KRN-004` assigned
- `KRN-005` assigned
- `YAP-001` assigned
- `YAP-002` assigned
- `YAP-003` assigned
- `YAP-004` assigned
- `YAP-005` assigned
- `DC-001` assigned
- `DC-002` assigned
- `DC-003` assigned
- `DC-004` assigned
- `DC-005` assigned
- `PHR-001` assigned
- `PHR-002` assigned
- `PHR-003` assigned
- `PHR-004` assigned
- `PHR-005` assigned
- `FIN-001` assigned
- `FIN-002` assigned
- `FIN-003` assigned
- `FIN-004` assigned
- `FIN-005` assigned

If any item above is not actively assigned, tracked, or explicitly deferred with owner approval, the program is incomplete.

## 10. Recommended Owners And Ceremony

### Weekly

- engineering leads review task status
- QA reviews flaky, skipped, and weak-assertion trends
- architecture reviews traceability drift

### End of each sprint

- publish sprint scorecard
- show coverage deltas
- show skipped test deltas
- show requirement closure deltas
- decide release-blocker status

### Monthly

- leadership review of remaining P0/P1 items
- update release readiness outlook

## 11. Immediate Start Sequence

Start in this order without waiting for later product work:

1. Sprint 1 tasks `DOC-001`, `QG-001`, `QG-004`, `QG-007`
2. Sprint 2 tasks `TQ-001`, `QG-005`, `TRC-002`
3. Sprint 3 task `KRN-001`
4. Sprint 4 task `YAP-004`
5. Sprint 5 task `DC-001`
6. Sprint 6 task `PHR-001`
7. Sprint 7 task `FIN-001`

This sequence produces a correct baseline, real gates, and visible progress fastest.
