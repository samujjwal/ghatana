## Deep + wide production-readiness maturity audit — commit `6a42f9b405c3cf7b0bb0b4370d43d3df070f24a0`

**Verdict: materially improved, still not production-ready.**

The commit is titled **`build good 1`** and appears to mostly refresh generated Digital Marketing lifecycle evidence timestamps rather than making major Data Cloud runtime changes. I audited the **full snapshot** at the commit, not just the diff. 

```text
Average maturity score: 3.39 / 5
Minimum dimension score: 2.5 / 5
Maturity band: Pre-production hardening
Production ready: No
World-class maturity: No
Can ship: No
P0 count: 2
P1 count: 7
Confidence: High for source-grounded audit; medium for runtime behavior because I did not execute tests/CI locally.
```

---

## Executive summary

Data Cloud and the surrounding Kernel/product platform are now much more mature than in the earlier snapshots. The strongest improvements are:

1. **Route/security/runtime-truth maturity is now strong.** `RouteSecurityRegistry` is generated from runtime routes, has a router checksum, covers Action Plane and broad Data Cloud route surfaces, and states production-like profiles fail closed when metadata is missing. 

2. **Production startup validation is stronger.** `DataCloudHttpServer` requires authentication, audit service, policy engine, durable idempotency, event store, durable entity/core event stores, metrics, trace export, tenant resolver, transaction manager, completion service, and durable ContextStore in production-like modes. 

3. **Context Plane wiring is much better.** The server exposes `withContextStore(ContextStore)` and validates that production-like deployment modes cannot run with `InMemoryContextStore`. 

4. **CI and root readiness gates are broad.** The repo now has checks for route/runtime truth, interaction runtime truth, product interaction broker tests, performance interaction checks, cross-product interaction flows, artifact roundtrip, Studio workflow E2E, canvas history, builder/canvas adapter, DS generator, product/provider readiness, observability, secrets, architecture boundaries, and world-class platform readiness. 

The remaining blockers are not superficial. They are hard production gaps:

* Atomic multi-step writes are still not proven.
* Runtime cross-product interaction proof is improving but still needs real deployment-style evidence.
* Data Cloud smoke and backup/restore gates are advisory in CI.
* Security scan, SBOM, and Sonar are still allowed to continue on error.
* Accessibility, i18n, DR, SLO/load, and cost maturity remain under-proven.
* Several gates prove presence/conformance but not necessarily full runtime behavior.

---

## P0 blockers

### P0-1 — Critical write atomicity is still not proven end to end

`DataCloudHttpServer` requires a `TransactionManager` in production-like modes, which is a strong improvement. 

However, the audited evidence still does not prove that every critical multi-step mutation is atomically wrapped across:

```text
business write
event append
audit emission
outbox persistence
idempotency record
trace/evidence record
```

The code previously documented this as a gap; this snapshot shows stronger production dependency validation, but I did not find enough evidence that every mutation handler has failure-injection tests proving no partial state survives.

**Required action**

```text
Enforce TransactionManager on every mutating handler.
Wrap entity/event/audit/outbox/idempotency in a single orchestration boundary.
Add failure-injection tests for every critical mutation family.
Fail production startup if any critical handler is not transaction-orchestrated.
Expose transaction orchestration posture in /api/v1/surfaces.
```

---

### P0-2 — Release gate still treats smoke E2E and backup/restore as advisory

The Data Cloud CI has a production readiness gate, but it explicitly treats `backup-drill` and `smoke-e2e` as advisory. The smoke job uses `continue-on-error: true` and runs `run-smoke-e2e.sh --warn-only`; the backup drill also uses `continue-on-error: true` and `--warn-only`. 

**Why this blocks production**

A production-readiness gate cannot mark a build “safe to merge” while live smoke and backup/restore evidence are advisory. These are core release requirements for a data platform.

**Required action**

```text
Create separate PR and release workflows.
Keep smoke/backup advisory for PR only.
Make smoke E2E, backup restore, and DR drill blocking in release/staging/prod workflows.
Require real DC_BASE_URL, tenant, and API token for release gate.
Block release if smoke or restore evidence is missing.
```

---

## P1 blockers

### P1-1 — Security scan, SBOM, and Sonar are not hard gates

`dependencyCheckAnalyze`, `cyclonedxBom`, and SonarQube analysis use `continue-on-error: true`. 

**Required action:** make release workflows fail on dependency vulnerability thresholds, missing SBOM, and configured static-analysis quality gates.

---

### P1-2 — Context Plane rejects in-memory production mode, but durable implementation wiring still needs deployment proof

The server defaults `contextStore` to `InMemoryContextStore`, exposes `withContextStore`, and rejects in-memory store in production-like deployment modes.  

This is acceptable for code-level safety, but production maturity needs deployment composition proof showing a durable `ContextStore` is actually wired in staging/prod manifests.

**Required action:** add deployment tests proving staging/prod set a durable ContextStore and fail if not.

---

### P1-3 — Route/security model is strong, but must remain generated-only

The registry includes a generated router checksum and large generated route metadata surface. 

**Required action:** fail CI if checksum is stale, if manual edits bypass the generator, or if any runtime route lacks metadata.

---

### P1-4 — AI/ML is product-native but production posture is not fully proven

The server requires `CompletionService` in production-like mode. 

But AI/ML maturity also requires model routing, fallback/degraded-mode behavior, quality feedback, privacy boundaries, cost controls, HITL escalation, and observability of automated decisions.

**Required action:** add AI/ML production posture gates covering model availability, no heuristic fallback in prod, privacy redaction, cost budget, model telemetry, feedback loop, HITL takeover, and audit evidence.

---

### P1-5 — A11y and i18n maturity remain under-proven

I found broad UI/workflow/design-system checks in the root scripts, but not enough evidence of WCAG AA browser automation, keyboard navigation, screen-reader assertions, pseudo-locale tests, locale formatting, or translation coverage gates across Data Cloud. 

**Required action:** add blocking a11y and i18n gates for Data Cloud UI surfaces.

---

### P1-6 — Runtime interaction checks are improving but still need deployment-style proof

Root scripts now include product interaction contracts, product interaction broker tests, interaction runtime truth, interaction performance, and cross-product interaction flows. 

This is a strong maturity jump. The remaining gap is proving these flows through realistic runtime composition, not only handler/unit-style tests.

**Required action:** add staging-style cross-product tests with tenant/workspace propagation, policy, audit, retries, timeout, unsupported version, wrong tenant, and evidence persistence.

---

### P1-7 — Performance and cost gates are present but not yet sufficient for world-class maturity

There are performance-related root checks, including audited performance workflows and interaction performance. 

But production readiness needs product-level SLO thresholds, load profiles, backpressure tests, heap/memory budgets, query latency budgets, storage/cost estimates, and regression baselines.

**Required action:** define and enforce product-level p95/p99 latency, throughput, memory, cost, and backpressure gates.

---

## Complete maturity scorecard

|  # | Dimension                         | Score | Status                     | Biggest gap                               | Blocking |
| -: | --------------------------------- | ----: | -------------------------- | ----------------------------------------- | -------- |
|  1 | Vision alignment                  |   4.0 | Production-ready direction | Execution proof still trails ambition     | No       |
|  2 | Product coherence                 |   3.5 | Pre-production             | Cross-product runtime proof incomplete    | No       |
|  3 | Feature completeness              |   3.0 | Functional                 | Some surfaces remain partial/degraded     | No       |
|  4 | End-to-end workflow completeness  |   3.5 | Pre-production             | Runtime/deployment proof incomplete       | Yes      |
|  5 | Runtime correctness               |   3.5 | Pre-production             | Atomic mutation proof incomplete          | Yes      |
|  6 | Domain correctness                |   3.0 | Functional                 | Full invariants under-tested              | No       |
|  7 | Data model correctness            |   3.0 | Functional                 | Durable/evidence model proof incomplete   | No       |
|  8 | Contract correctness              |   3.5 | Pre-production             | Need schema-depth and method parity proof | No       |
|  9 | Route/API correctness             |   4.0 | Production-ready direction | Needs executed CI evidence                | No       |
| 10 | UI/API/runtime coherence          |   3.5 | Pre-production             | Full browser proof incomplete             | No       |
| 11 | Runtime Truth maturity            |   4.0 | Production-ready direction | Needs deployment-level posture proof      | No       |
| 12 | Security                          |   3.5 | Pre-production             | Security scan not fully hard-gated        | Yes      |
| 13 | Privacy                           |   3.0 | Functional                 | Data-subject/privacy proof incomplete     | No       |
| 14 | Tenant isolation                  |   3.5 | Pre-production             | Runtime cross-product proof incomplete    | Yes      |
| 15 | Authorization/RBAC/ABAC/scope     |   3.5 | Pre-production             | Need full role-route matrix E2E           | No       |
| 16 | Governance/policy/compliance      |   3.5 | Pre-production             | Policy runtime evidence incomplete        | No       |
| 17 | Audit durability/evidence         |   3.0 | Functional                 | Atomic audit/evidence proof missing       | Yes      |
| 18 | Event correctness                 |   3.5 | Pre-production             | Replay/bridge failure proof incomplete    | No       |
| 19 | Action Plane / automation         |   3.5 | Pre-production             | Runtime execution proof incomplete        | No       |
| 20 | Implicit AI/ML maturity           |   3.0 | Functional                 | Production AI posture incomplete          | No       |
| 21 | HITL / override control           |   3.0 | Functional                 | Full takeover/delegation evidence missing | No       |
| 22 | Observability                     |   3.5 | Pre-production             | SLO dashboards/gates incomplete           | No       |
| 23 | Reliability/resilience            |   3.0 | Functional                 | Failure-injection coverage incomplete     | Yes      |
| 24 | Error/degraded mode               |   3.0 | Functional                 | Degraded-mode UX/runtime proof incomplete | No       |
| 25 | Idempotency/retry/replay/rollback |   3.0 | Functional                 | Atomic retry/replay proof incomplete      | Yes      |
| 26 | Performance                       |   3.0 | Functional                 | Load/SLO gates incomplete                 | No       |
| 27 | Scalability                       |   3.0 | Functional                 | Scale-out/backpressure proof incomplete   | No       |
| 28 | Extensibility/plugin model        |   3.5 | Pre-production             | Runtime plugin proof incomplete           | No       |
| 29 | Shared-library reuse              |   3.5 | Pre-production             | Needs continued boundary enforcement      | No       |
| 30 | Dependency hygiene                |   3.5 | Pre-production             | Some advisory checks remain               | No       |
| 31 | Architecture boundaries           |   3.5 | Pre-production             | Runtime boundary proof incomplete         | No       |
| 32 | Simplicity/maintainability        |   3.0 | Functional                 | Large route/security surface complexity   | No       |
| 33 | UI/UX simplicity/consistency      |   3.0 | Functional                 | Full surface review incomplete            | No       |
| 34 | Accessibility                     |   2.5 | Partial                    | WCAG browser evidence missing             | No       |
| 35 | i18n/l10n readiness               |   2.5 | Partial                    | Locale/pseudo-locale gates missing        | No       |
| 36 | Testing depth                     |   3.5 | Pre-production             | Runtime/system tests incomplete           | No       |
| 37 | Test quality / no test theater    |   3.0 | Functional                 | Some gates likely static/presence-based   | Yes      |
| 38 | CI gate strength                  |   3.5 | Pre-production             | Advisory smoke/backup/security gates      | Yes      |
| 39 | Release readiness                 |   3.0 | Functional                 | Release gate not strict enough            | Yes      |
| 40 | Deployment/ops readiness          |   3.0 | Functional                 | Production deployment proof incomplete    | No       |
| 41 | Backup/restore/DR                 |   2.5 | Partial                    | Advisory backup drill                     | Yes      |
| 42 | Config/secrets management         |   3.0 | Functional                 | Runtime secrets proof incomplete          | No       |
| 43 | Documentation truthfulness        |   3.5 | Pre-production             | Must track generated truth continuously   | No       |
| 44 | Migration/deprecation hygiene     |   3.0 | Functional                 | Compatibility routes remain active        | No       |
| 45 | Cost/operational efficiency       |   2.5 | Partial                    | Cost budgets under-proven                 | No       |
| 46 | Overall production readiness      |   3.0 | Functional                 | P0 blockers remain                        | Yes      |
| 47 | Overall world-class maturity      |   2.5 | Partial                    | Not yet fully proven or low-burden        | Yes      |

---

## Maturity band

```text
Average score: 3.39
Minimum dimension score: 2.5
Maturity band: Pre-production hardening
0 scores: 0
1 scores: 0
2 scores: 0
2.5 scores: 4
3.0 scores: 20
3.5 scores: 19
4.0 scores: 4
5.0 scores: 0
Unknown: 0
```

This is a meaningful improvement from the prior “functional prototype” state, but hard blockers still prevent shipment.

---

## File-by-file / area-by-area implementation plan

### `.github/workflows/data-cloud-ci.yml`

**Problem:** Smoke E2E and backup/restore are advisory; security scan, SBOM, and Sonar are not hard gates.

**Required changes:**

```text
Split PR and release workflows.
Make smoke-e2e blocking for release.
Make backup-drill blocking for release.
Make dependencyCheckAnalyze blocking above severity threshold.
Make cyclonedxBom blocking.
Make Sonar blocking when configured.
Require real staging/prod environment variables for release gates.
```

Evidence: smoke and backup jobs are `continue-on-error` and `--warn-only`; release gate logs them as advisory. 

---

### `DataCloudHttpServer.java`

**Problem:** Production dependency validation is strong, but atomic mutation proof is incomplete.

**Required changes:**

```text
Make transaction orchestration explicit per handler.
Expose transaction posture in Runtime Truth.
Add production startup test that rejects any critical handler without transaction orchestration.
Add failure-injection tests for entity/event/audit/outbox/idempotency partial failures.
```

Evidence: production dependency validation requires auth, audit, policy, idempotency, durable stores, metrics, trace export, tenant resolver, transaction manager, completion service, and durable context store. 

---

### `RouteSecurityRegistry.java`

**Problem:** Strong generated registry; must remain exhaustive and generated-only.

**Required changes:**

```text
Keep GENERATED_ROUTER_CHECKSUM mandatory.
Fail CI if checksum mismatches router.
Fail CI if any runtime route lacks metadata.
Add generated route coverage summary to CI.
Add CRITICAL-route policy/audit parameterized tests.
```

Evidence: registry declares itself generated from `DataCloudRouterBuilder`, includes generated checksum, and states production-like profiles fail closed when metadata is missing. 

---

### `package.json` root readiness scripts

**Problem:** Excellent breadth, but many gates are static or scripted; runtime evidence must be elevated.

**Required changes:**

```text
Keep phase8/world-class platform readiness.
Add release-only runtime profile that executes real services.
Convert key static contract gates into runtime smoke where appropriate.
Add a11y, i18n, DR, and cost-budget gates into phase8 or release phase.
```

Evidence: root scripts include interaction runtime truth, product interaction broker, interaction performance, cross-product interaction flows, artifact roundtrip, Studio workflow E2E, canvas history, builder checks, DS generator golden, production readiness, architecture boundaries, and audited performance workflows. 

---

## Final decision

```text
Can ship to production: No
Can continue as pre-production hardening branch: Yes
Maturity trajectory: Strongly positive
```

This commit is not a major code-change commit for Data Cloud, but the snapshot is noticeably stronger than the previous audit state. The system is now in **pre-production hardening**, not early prototype. The next step should be to convert the remaining advisory/runtime gaps into hard, executable release gates and prove atomicity, DR, security, a11y, i18n, performance, and cross-product runtime behavior end to end.
