## Deterministic maturity audit — commit `f53ba36eb9987a75d14cf50e904106771c4435df`

## Verification note

The audit below is retained as a historical snapshot for commit `f53ba36eb9987a75d14cf50e904106771c4435df`.
In the current workspace, the cited launcher atomic-write, security, OpenAPI alignment, route-registry,
and cross-product runtime gaps are already implemented and verified by focused Gradle test runs.

Verified checks:

- `:products:data-cloud:delivery:launcher:test` for `EntityCrudHandlerDurabilityTest`, `DataCloudSecurityFilterTest`, `DataCloudSecurityFilterProductionProfileTest`, `OpenApiRouteAlignmentTest`, and `RouteSecurityRegistryInvariantTest`
- `:integration-tests:cross-service-workflow:runtimeInteractionTest`

Both runs completed successfully on 2026-05-21.

**Verdict: improved, but still not production-ready.**

This commit is titled **“build fix 5-21-1”** and adds cross-product interaction checks, a `productInteractionTest` source set, and focused PHR/DMOS product interaction contract tests. It also expands root `check:phase8` / `check:world-class-platform-readiness` to include kernel/plugin interaction, product interaction contracts, cross-product boundaries, interaction runtime truth, and cross-product interaction flows.  

I audited the snapshot statically from repository files. I did **not** execute Gradle, pnpm, Playwright, or CI jobs.

```text
Average maturity score: 3.18 / 5
Minimum dimension score: 2.5 / 5
Maturity band: Functional prototype
Production ready: No
World-class maturity: No
Can ship: No
Reason: hard blockers remain in atomicity, durable audit/evidence proof, real end-to-end execution proof, DR/release gates, a11y/i18n maturity, and some AI/ML/runtime operational hardening.
P0 count: 2
P1 count: 7
Confidence: High for source-grounded architecture audit; medium for runtime behavior because tests were not executed.
```

---

## What improved materially since `9d4a677...`

The route-security model is much stronger. `RouteSecurityRegistry` is now described as generated from `DataCloudRouterBuilder`, every live runtime route is expected to be represented, and production-like profiles fail closed when metadata is missing. The registry now includes many Data Cloud and Action Plane routes, not only the small subset seen previously. 

The security filter now looks up route metadata per request, rejects missing metadata in production-like profiles, derives sensitivity from metadata, uses metadata for access enforcement, and uses `requiresPolicy` / `requiresBlockingAudit` instead of only `EndpointSensitivity.CRITICAL`.  

The route manifest generator now reconciles `DataCloudRouterBuilder` runtime routes with `RouteSecurityRegistry`, fails on router-only or registry-only drift, and generates UI Runtime Truth with `requiresBlockingAudit`, `requiredAccess`, and `legacyStatus`. 

OpenAPI drift detection no longer rewrites `/api/v1/action/*` into `/api/v1/*`; it now separates Data Cloud, Action Plane, and compatibility-route ownership across `data-cloud.yaml`, `action-plane.yaml`, and `route-compatibility-registry.yaml`. 

The canonical Action Plane contract now states that canonical routes live under `/api/v1/action/*`, that legacy AEP/root paths are compatibility-only, and it includes route metadata extensions like sensitivity, required access, policy requirement, blocking audit, and legacy status. 

Context Plane durability improved. `DataCloudHttpServer` now has `withContextStore(ContextStore)`, validates context-store configuration, and wires the provided store into `ContextLayerHandler`; production mode rejects `InMemoryContextStore`.   

---

## P0 blockers

### P0-1 — Critical multi-step writes still lack proven atomic transaction boundaries

`DataCloudHttpServer.withAuditService()` still explicitly notes that transaction boundaries for entity write + event append + audit logging are not currently implemented and require atomic wrapping and failure-injection tests. 

**Why this blocks production:** a critical mutation can partially persist business data without corresponding event/audit evidence, or emit evidence without committed business state.

**Required actions:**

```text
Implement atomic entity/event/audit transaction orchestration.
Require TransactionManager for all critical multi-step writes.
Persist outbox entries transactionally.
Add failure-injection tests for write succeeds/event fails, event succeeds/audit fails, audit sink unavailable, retry, replay, and rollback.
Fail production startup if atomic mutation orchestration is unavailable.
```

---

### P0-2 — Cross-product interaction tests are handler-level, not production runtime proof

The new `productInteractionTest` source set and PHR/DMOS tests are valuable, but the tests instantiate handlers directly and assert success/blocking behavior using in-process request objects. They do not prove service discovery, runtime routing, policy engine integration, audit persistence, tenant propagation across real product boundaries, or failure behavior across network/runtime boundaries.  

**Why this blocks production maturity:** it proves contract semantics at handler level, not real cross-product production interaction readiness.

**Required actions:**

```text
Add integration tests that execute through the Kernel interaction runtime.
Prove product-to-product routing, tenant/workspace propagation, policy evaluation, audit evidence, observability correlation, and failure handling.
Add negative tests for wrong tenant, missing workspace, unsupported contract version, policy denial, handler unavailable, timeout, and retry/idempotency.
Make the interaction runtime truth gate verify real executable wiring, not only static contract presence.
```

---

## P1 blockers

1. **Context Plane has fail-closed validation, but no durable implementation was found in the inspected snapshot.** `withContextStore()` exists and production rejects `InMemoryContextStore`, but production readiness requires a concrete durable `JdbcContextStore` or equivalent wired in deployment composition. 

2. **AI/ML is pervasive but still mixed between real-service and degraded/fallback modes.** The server requires a completion service in production, but other AI/voice/semantic capabilities still expose `NOT_CONFIGURED` health modes and need clearer production enablement matrices. 

3. **Audit blocking is stronger, but audit durability still depends on `AuditService.record()` behavior and sink readiness.** The filter blocks for `requiresBlockingAudit`, but production maturity needs durable sink guarantees and end-to-end evidence package tests. 

4. **OpenAPI correctness improved, but schema depth remains generic.** The Action Plane contract has canonical paths and metadata, but many responses are still generic `type: object` with `additionalProperties: true`; that is not world-class contract specificity. 

5. **Release gates are broad but still need stronger runtime proof.** Root `check:phase8` is extensive, but static scripts and handler tests must be complemented by full runtime deployment smoke, backup/restore, security, a11y, i18n, performance, and disaster-recovery gates. 

6. **A11y and i18n are still immature relative to production ambitions.** I found CI and product gates, but the inspected evidence does not prove WCAG coverage, keyboard flows, screen-reader behavior, translation extraction, locale formatting, or pseudo-locale tests across Data Cloud surfaces.

7. **Operational cost and performance maturity remain under-proven.** There are performance-related gates in root scripts, but the inspected Data Cloud evidence does not prove load envelopes, p95/p99 SLOs, memory profiles, backpressure, scale-out behavior, or cost budgets as hard release gates.

---

## Complete maturity scorecard

|  # | Dimension                                 | Score | Status                     | Biggest gap                                | Blocking |
| -: | ----------------------------------------- | ----: | -------------------------- | ------------------------------------------ | -------- |
|  1 | Vision alignment                          |   4.0 | Production-ready direction | Execution still trails vision              | No       |
|  2 | Product coherence                         |   3.5 | Pre-production             | Cross-product/runtime proof incomplete     | No       |
|  3 | Feature completeness                      |   3.0 | Functional                 | Some surfaces remain partial/degraded      | No       |
|  4 | End-to-end workflow completeness          |   3.0 | Functional                 | Handler-level proof over runtime proof     | Yes      |
|  5 | Runtime correctness                       |   3.5 | Pre-production             | Atomic write/runtime failure proof         | Yes      |
|  6 | Domain correctness                        |   3.0 | Functional                 | Domain invariants not fully proven         | No       |
|  7 | Data model correctness                    |   3.0 | Functional                 | Durable context/evidence models incomplete | No       |
|  8 | Contract correctness                      |   3.5 | Pre-production             | Generic schemas remain                     | No       |
|  9 | Route/API correctness                     |   4.0 | Production-ready direction | Needs executed drift proof                 | No       |
| 10 | UI/API/runtime coherence                  |   3.5 | Pre-production             | Needs full UI gate proof                   | No       |
| 11 | Runtime Truth maturity                    |   3.5 | Pre-production             | Needs complete deployment posture          | No       |
| 12 | Security                                  |   3.5 | Pre-production             | Runtime/e2e proof still needed             | No       |
| 13 | Privacy                                   |   3.0 | Functional                 | Data subject/privacy flows under-proven    | No       |
| 14 | Tenant isolation                          |   3.5 | Pre-production             | Cross-product tenant proof incomplete      | Yes      |
| 15 | Authorization/RBAC/ABAC/scope enforcement |   3.5 | Pre-production             | Needs route-matrix e2e proof               | No       |
| 16 | Governance, policy, compliance            |   3.5 | Pre-production             | Policy runtime proof incomplete            | No       |
| 17 | Audit durability/evidence quality         |   3.0 | Functional                 | Atomic durable evidence missing            | Yes      |
| 18 | Event correctness                         |   3.5 | Pre-production             | Replay/bridge proof still needed           | No       |
| 19 | Action Plane / automation correctness     |   3.0 | Functional                 | Agent/action lifecycle proof incomplete    | No       |
| 20 | Implicit AI/ML maturity                   |   3.0 | Functional                 | Production AI/ML posture incomplete        | No       |
| 21 | Human-in-the-loop / override control      |   3.0 | Functional                 | HITL e2e and override evidence incomplete  | No       |
| 22 | Observability: logs, metrics, traces      |   3.5 | Pre-production             | SLO/load proof incomplete                  | No       |
| 23 | Reliability and resilience                |   3.0 | Functional                 | Failure-injection coverage incomplete      | Yes      |
| 24 | Error handling / degraded mode            |   3.0 | Functional                 | Degraded mode needs route-level proof      | No       |
| 25 | Idempotency, retries, replay, rollback    |   3.0 | Functional                 | Atomic retry/replay proof incomplete       | Yes      |
| 26 | Performance                               |   3.0 | Functional                 | SLO/load gates under-proven                | No       |
| 27 | Scalability                               |   3.0 | Functional                 | Scale-out/backpressure proof incomplete    | No       |
| 28 | Extensibility/plugin model                |   3.5 | Pre-production             | Plugin lifecycle proof incomplete          | No       |
| 29 | Shared-library reuse                      |   3.5 | Pre-production             | Cross-product boundaries improving         | No       |
| 30 | Dependency hygiene                        |   3.5 | Pre-production             | Needs executed gate evidence               | No       |
| 31 | Architecture boundaries                   |   3.5 | Pre-production             | More runtime-boundary tests needed         | No       |
| 32 | Simplicity/maintainability                |   3.0 | Functional                 | Route/security complexity remains high     | No       |
| 33 | UI/UX simplicity/consistency              |   3.0 | Functional                 | Full surface review not proven             | No       |
| 34 | Accessibility                             |   2.5 | Partial                    | WCAG/browser evidence missing              | No       |
| 35 | i18n/l10n readiness                       |   2.5 | Partial                    | Locale/pseudo-locale gates missing         | No       |
| 36 | Testing depth                             |   3.5 | Pre-production             | Runtime/system tests still thin            | No       |
| 37 | Test quality / no test theater            |   3.0 | Functional                 | Some handler/static tests overclaim        | Yes      |
| 38 | CI gate strength                          |   3.5 | Pre-production             | Release runtime gates need hardening       | No       |
| 39 | Release readiness                         |   3.0 | Functional                 | Ship/no-ship gates incomplete              | Yes      |
| 40 | Deployment/operations readiness           |   3.0 | Functional                 | Production deployment proof incomplete     | No       |
| 41 | Backup/restore/DR                         |   2.5 | Partial                    | DR drill hard gate not proven              | No       |
| 42 | Config/secrets management                 |   3.0 | Functional                 | Secret/runtime profile proof incomplete    | No       |
| 43 | Documentation truthfulness                |   3.5 | Pre-production             | Must stay aligned with new runtime truth   | No       |
| 44 | Migration/deprecation hygiene             |   3.0 | Functional                 | Compatibility route lifecycle still active | No       |
| 45 | Cost/operational efficiency               |   2.5 | Partial                    | Cost budgets under-proven                  | No       |
| 46 | Overall production readiness              |   3.0 | Functional                 | P0 blockers remain                         | Yes      |
| 47 | Overall world-class maturity              |   2.5 | Partial                    | Not yet low-risk, simple, fully proven     | Yes      |

---

## Maturity band calculation

```text
Average score: 3.18
Minimum score: 2.5
0 scores: 0
1 scores: 0
2 scores: 0
2.5 scores: 4
3 scores: 22
3.5 scores: 18
4 scores: 3
5 scores: 0
Unknown dimensions: 0
Maturity band: Functional prototype
```

Despite improvements, hard blocking rules still prevent shipment because critical multi-step write atomicity and real runtime interaction proof remain incomplete.

---

## File-by-file implementation plan

### `DataCloudHttpServer.java`

**Current maturity problem:** Multi-step writes still lack atomic transaction boundaries; context store validation is improved but durable implementation wiring must be proven.

**Target score:** 4.0+

**Required actions:**

```text
Wire TransactionManager into all critical mutation handlers.
Fail production startup when critical mutation orchestration is absent.
Require durable ContextStore in production deployment composition.
Expose contextStore mode and transaction orchestration mode in /api/v1/surfaces.
Add startup tests for production, staging, sovereign, local, and test profiles.
```

Evidence: the server still documents missing atomic transaction boundaries in the audit-service builder note. 

---

### `RouteSecurityRegistry.java`

**Current maturity problem:** Much improved, but must remain generated and exhaustive.

**Target score:** 4.5

**Required actions:**

```text
Keep registry generated only.
Block manual edits unless generated marker verifies source checksum.
Add route count parity assertions to CI summary.
Add tests for every CRITICAL route proving policy + blocking audit.
```

Evidence: the registry now declares itself runtime-route metadata and expects every live runtime route to be represented. 

---

### `DataCloudSecurityFilter.java`

**Current maturity problem:** Enforcement is now metadata-driven, but needs exhaustive tests.

**Target score:** 4.5

**Required actions:**

```text
Add parameterized tests for requiresPolicy.
Add parameterized tests for requiresBlockingAudit.
Add tests for missing metadata fail-closed in production.
Add tests for compatibility-only routes.
Add tests for API_CLIENT / PROCESSOR not bypassing admin routes.
```

Evidence: the filter now looks up metadata, rejects missing metadata in production, and passes metadata into access, policy, and audit decisions.  

---

### `check-openapi-drift.sh`

**Current maturity problem:** Greatly improved; now needs method-level and schema-level parity.

**Target score:** 4.5

**Required actions:**

```text
Compare methods, not just paths.
Compare x-ghatana route metadata.
Compare request/response schema presence.
Fail if any canonical route uses generic response schema without explicit waiver.
Separate Data, Action, and compatibility ownership in the report.
```

Evidence: the script now separates Data, Action, and compatibility route ownership and no longer normalizes `/api/v1/action/*` into `/api/v1/*`. 

---

### `action-plane.yaml`

**Current maturity problem:** Canonical route ownership is fixed, but schemas are still too generic.

**Target score:** 4.0+

**Required actions:**

```text
Replace generic additionalProperties responses with typed schemas.
Add explicit error envelopes.
Add idempotency headers for mutating routes.
Add audit/policy metadata to all critical mutations.
Add compatibility/deprecation notes only in aep.yaml or compatibility registry.
```

Evidence: the contract now uses canonical `/api/v1/action/*` routes and route metadata extensions. 

---

### `integration-tests/cross-service-workflow/build.gradle.kts`

**Current maturity problem:** Adds useful product interaction test source set, but needs runtime-level integration.

**Target score:** 4.0

**Required actions:**

```text
Keep productInteractionTest.
Add runtimeInteractionTest using Kernel interaction runtime.
Add testcontainers or in-process service composition where appropriate.
Wire productInteractionTest into CI release gate, not only root script.
```

Evidence: the new `productInteractionTest` source set and task are defined with PHR, DMOS, kernel, and platform testing dependencies. 

---

### `PhrDmosProductInteractionContractTest.java`

**Current maturity problem:** Good contract handler tests, insufficient runtime proof.

**Target score:** 4.0

**Required actions:**

```text
Add tests through Kernel interaction dispatcher.
Assert tenant/workspace/correlation propagation.
Assert audit evidence refs are durable, not just returned strings.
Add negative tests for wrong tenant, stale contract version, unsupported purpose, timeout, and policy denial.
```

Evidence: the tests instantiate handlers directly and verify PHR consent, DMOS notification preferences, and missing tenant fail-closed behavior. 

---

## Final ship decision

```text
Can this commit ship? No.
```

This commit is a **clear maturity improvement** over the prior state. The most important previous blockers around route truth, Action Plane OpenAPI ownership, route metadata, UI Runtime Truth, and context-store startup validation are either fixed or materially improved.

The remaining blockers are now deeper production-readiness issues: atomic writes, durable evidence, runtime-level cross-product interaction proof, DR/release hard gates, i18n/a11y maturity, and full operational SLO proof.
