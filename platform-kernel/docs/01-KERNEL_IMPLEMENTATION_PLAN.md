Below is a deeper 47-dimension production-grade plan for commit `ee6b28477b15fd5ab62bcd636867841f8e23ee32`.

The snapshot has moved in the right direction: Data Cloud now has a separate strict release workflow with environment validation, strict smoke E2E, strict backup drill, blocking dependency scan/SBOM generation, and release evidence artifacts.   Normal Data Cloud CI is now correctly marked advisory.  The root product scripts also show broad platform checks across interaction runtime truth, product interaction flows, artifact roundtrip, Studio workflow E2E, source acquisition, DS generator contrast, product readiness, observability, secrets, route entitlements, audited UI/E2E/performance workflows, and architecture gates. 

The plan below assumes the target is **production-grade first** and **world-class second**.

---

# Execution strategy

## Release maturity phases

```text id="bctg2r"
Phase 1 — Block production escape hatches
Goal: eliminate anything that can ship without runtime proof.

Phase 2 — Prove correctness end to end
Goal: atomic workflows, tenant isolation, security, audit, Runtime Truth, and cross-product interactions.

Phase 3 — Make product quality world-class
Goal: simple UX, consistent UI, implicit AI/ML, observability, i18n, a11y, performance, cost, and maintainability.

Phase 4 — Scale across all products
Goal: product-family release gates, product-specific maturity scorecards, and affected-product release orchestration.
```

## Hard release rule

A product cannot be considered production-grade until these are true:

```text id="zbbz8c"
No P0 blockers.
Every critical workflow has failure-injection tests.
Every protected route has server-side auth, tenant, policy, audit, and Runtime Truth metadata.
Release gate is strict and non-advisory.
Smoke, backup, security, SBOM, a11y, i18n, SLO, and DR evidence are retained as release artifacts.
```

## Implementation progress snapshot (2026-05-22)

Status update:

- Wave 1 item 1 completed: atomic workflow proof gate is enforced by `check:atomic-workflow-proof`, included in strict runtime profile checks, and retained as release evidence.
- Wave 1 item 2 completed: release scorecard artifact generation is enforced by `scripts/generate-release-maturity-summary.mjs` and uploaded as `data-cloud-release-summary`.
- Wave 1 item 3 completed: strict affected-product release orchestration is enforced in `product-release.yml` using resolver + strict profile checks.
- Wave 1 item 4 completed for Data Cloud strict release: protected environment policy is enforced by release environment validation and environment-scoped job gating.
- Wave 1 item 5 completed: typed contract schema lint is enforced through `check:openapi-release-quality` in strict release workflow.

New completeness/consistency/correctness enforcement added:

- `check:kernel-implementation-plan-coverage` verifies all 47 dimensions have mapped executable gates and writes machine-readable evidence.
- `check:product-release-readiness` now enforces explicit journey and release-area matrices, executes deduplicated strict checks, and writes structured pass/fail evidence.
- Product release workflow now blocks on strict readiness and uploads evidence artifacts per affected product.
- Product release workflow supports strict `dry_run` execution for controlled CI rehearsal without publishing release artifacts.
- Wave 2 product quality scorecard generation is now automated and retained with release summary artifacts.
- Wave 2 item 1 completed: cross-product a11y route matrix coverage is enforced for production-intended web products via `check:product-a11y-route-matrix` and included in strict phase/release gates.
- `check:affected-product-strict-release-profile` now enforces stricter affected-product release journey coverage (build/test plus release-core scripts) and validates strict workflow tokens.

Evidence artifacts:

- `.kernel/evidence/kernel-implementation-plan-progress.json`
- `.kernel/evidence/product-release-readiness.json`
- `.kernel/evidence/affected-product-release-profile.json`
- `.kernel/evidence/product-a11y-route-matrix.json`
- `.kernel/evidence/wave2-product-quality-scorecard.json`
- `.kernel/evidence/data-cloud-release-runtime-profile.json`
- `.kernel/evidence/atomic-workflow-posture.json`
- `release-evidence/wave2-product-quality-scorecard.md`

Operational command set:

- `pnpm check:kernel-implementation-plan-coverage`
- `pnpm check:product-release-readiness`
- `pnpm check:product-a11y-route-matrix`
- `pnpm generate:wave2-product-quality-scorecard`
- `pnpm check:release-gate`

---

# 47-dimension production-grade plan

## 1. Vision alignment

**Current diagnosis:** Strong direction. The product aims for Data Cloud as an AI-native operational data layer with Action Plane, governance, Runtime Truth, and product-family platform checks. The gap is execution proof.

**Target maturity:** 4.5

**Plan:**

```text id="9h8vkf"
Define canonical product vision per product.
Map every feature to product outcome, user journey, and runtime surface.
Create a product capability matrix: promised, implemented, tested, release-gated.
Reject features that are only documented but not runtime-backed.
```

**Tests/gates:**

```text id="8a1tuz"
check:product-shape-capability-matrix
check:product-doc-claims-evidence
check:current-state-claims
```

**Acceptance criteria:** Every claimed capability links to code, contract, tests, Runtime Truth, and release gate.

---

## 2. Product coherence

**Current diagnosis:** Improved, but product boundaries still need discipline across Data Cloud, Action Plane, Kernel, Studio, and other products.

**Target maturity:** 4.5

**Plan:**

```text id="6brwwy"
Define product ownership map.
Define which product owns each UI route, API route, data model, workflow, and release gate.
Keep AEP as Action Plane runtime, not separate customer product.
Prevent product-specific logic from leaking into shared platform libraries.
```

**Tests/gates:**

```text id="nsbxdv"
check:product-registry
check:product-registry-drift
check:platform-product-boundaries
check:cross-product-interaction-boundaries
```

**Acceptance criteria:** Each route/module/workflow has one product owner and no ambiguous ownership.

---

## 3. Feature completeness

**Current diagnosis:** Many surfaces exist, but completeness varies by product and feature.

**Target maturity:** 4.0

**Plan:**

```text id="1a71tp"
Create feature inventory per product.
Mark each feature as Complete, Partial, Missing, Broken, Overbuilt, or Unknown.
For each Partial feature, list missing UI, API, storage, policy, audit, tests, docs, and release gates.
Hide incomplete features behind Runtime Truth instead of exposing broken UI.
```

**Tests/gates:**

```text id="w0qj06"
check:product-ui-contracts
check:data-cloud-platform-provider-readiness
check:yappc-platform-provider-readiness
check:finance-lifecycle-readiness
check:phr-lifecycle-readiness
```

**Acceptance criteria:** No customer-visible feature is UI-only, mock-backed, or missing runtime support.

---

## 4. End-to-end workflow completeness

**Current diagnosis:** Improving, but not all workflows are proven from UI/API to storage/audit/release evidence.

**Target maturity:** 4.5

**Plan:**

```text id="4xw5d4"
For each product, define top 10 critical workflows.
Trace each workflow: UI → API → service → storage → event → audit → observability → tests → release gate.
Add golden path, negative path, unauthorized path, degraded path, and retry path tests.
```

**Tests/gates:**

```text id="3x6xhf"
check:audited-e2e-workflow
check:studio-artifact-workflow-e2e
check:cross-product-interaction-flows
Data Cloud strict release smoke E2E
```

**Acceptance criteria:** Every critical workflow has executable end-to-end proof and release evidence.

---

## 5. Runtime correctness

**Current diagnosis:** Stronger production dependency validation exists, but atomic runtime behavior remains under-proven. The server requires key production dependencies such as auth, audit, policy, durable stores, metrics, trace export, idempotency, transaction manager, and completion service in production-like modes. 

**Target maturity:** 4.5

**Plan:**

```text id="rsk83y"
Create runtime correctness matrix for every handler.
Require transaction orchestration for critical mutations.
Require durable stores for production profiles.
Fail startup when critical runtime dependencies are missing.
Expose runtime dependency posture through /api/v1/surfaces.
```

**Tests/gates:**

```text id="rkfggp"
Runtime startup tests for local/staging/production/sovereign
Failure-injection tests
Release runtime profile gate
```

**Acceptance criteria:** Production mode cannot start with unsafe runtime dependencies or incomplete critical mutation orchestration.

---

## 6. Domain correctness

**Current diagnosis:** Domain coverage exists, but domain invariants need deeper golden tests per product.

**Target maturity:** 4.0

**Plan:**

```text id="7zzmr8"
Define domain invariants per product.
Add golden datasets for normal, edge, invalid, and compliance-sensitive cases.
Separate manually entered facts from computed facts.
Prove computations and state transitions with deterministic tests.
```

**Tests/gates:**

```text id="jzsicr"
Golden domain tests
Workflow lifecycle tests
Product-specific invariant tests
```

**Acceptance criteria:** Domain outputs are reproducible, explainable, and tested against authoritative examples.

---

## 7. Data model correctness

**Current diagnosis:** Needs deeper proof of durability, lifecycle, history, retention, ownership, tenant boundaries, and computed/manual data separation.

**Target maturity:** 4.0

**Plan:**

```text id="0bib0c"
Document data ownership and lifecycle per model.
Mark source-of-truth fields vs computed fields.
Add migration and backward-compatibility tests.
Add retention, redaction, provenance, and audit fields where required.
```

**Tests/gates:**

```text id="b1fxgw"
Schema migration tests
Data lifecycle tests
Tenant isolation storage tests
```

**Acceptance criteria:** Every persisted model has owner, lifecycle, retention, migration strategy, and tenant/privacy rules.

---

## 8. Contract correctness

**Current diagnosis:** Route ownership improved. The Action Plane release contract now uses canonical `/api/v1/action/*` and metadata extensions.  Remaining gap is schema specificity.

**Target maturity:** 4.5

**Plan:**

```text id="k6wu7j"
Reject generic object/additionalProperties responses unless explicitly waived.
Require typed request and response schemas.
Require standard error envelope.
Require idempotency headers for mutating routes.
Generate SDK tests from OpenAPI.
```

**Tests/gates:**

```text id="4wr8ec"
check-openapi-drift.sh
SDK generation check
Schema specificity lint
Backward compatibility contract tests
```

**Acceptance criteria:** Public contracts are typed, versioned, generated, and executable.

---

## 9. Route/API correctness

**Current diagnosis:** Strong. Route security registry is generated from runtime routes and has router checksum. 

**Target maturity:** 4.5

**Plan:**

```text id="7mbw27"
Keep RouteSecurityRegistry generated only.
Fail CI if router checksum is stale.
Fail CI if any runtime route lacks metadata.
Generate route docs and UI posture from same manifest.
```

**Tests/gates:**

```text id="qjo02r"
generate-route-security-metadata --check
generate-route-manifest --check
check-openapi-drift.sh
Route metadata parameterized tests
```

**Acceptance criteria:** Router, OpenAPI, security metadata, Runtime Truth, SDK, and UI posture cannot drift.

---

## 10. UI/API/runtime coherence

**Current diagnosis:** Improving, especially through Runtime Truth and UI tests.

**Target maturity:** 4.0

**Plan:**

```text id="da8pqe"
All UI actions must map to API routes.
All API routes must map to Runtime Truth posture.
All unavailable capabilities must be hidden or shown as unavailable.
No UI-only authorization.
No dead buttons or fake actions.
```

**Tests/gates:**

```text id="rpqwhf"
Product UI contract tests
Route entitlement contracts
Playwright user flow tests
```

**Acceptance criteria:** Every visible UI action is backed by a real authorized runtime capability.

---

## 11. Runtime Truth maturity

**Current diagnosis:** Strong for Data Cloud, needs product-family expansion.

**Target maturity:** 4.5

**Plan:**

```text id="xwhqi6"
Runtime Truth must include feature status, route posture, dependency status, storage mode, AI mode, audit mode, tenant mode, and release readiness.
UI must consume Runtime Truth for gating.
Release evidence must include Runtime Truth snapshot.
```

**Tests/gates:**

```text id="gy4hw3"
check:interaction-runtime-truth
Data Cloud release runtime profile
Runtime Truth schema tests
```

**Acceptance criteria:** Runtime Truth is the single customer-facing truth of availability and safety.

---

## 12. Security

**Current diagnosis:** Stronger. Release workflow now has strict security/SBOM gates for Data Cloud. 

**Target maturity:** 4.5

**Plan:**

```text id="31106m"
Make security scan blocking for all release workflows.
Add route-level auth tests.
Add secret scanning.
Add dependency severity thresholds.
Add SBOM generation and artifact retention.
```

**Tests/gates:**

```text id="z5xcyb"
security-scan-strict
check:secret-default-credentials
check:route-entitlement-contracts
```

**Acceptance criteria:** No release can proceed with missing SBOM, critical dependency vulnerabilities, hardcoded secrets, or route auth gaps.

---

## 13. Privacy

**Current diagnosis:** Privacy principles are visible, but privacy workflow proof needs expansion.

**Target maturity:** 4.0

**Plan:**

```text id="tcdxlc"
Classify data by sensitivity.
Add PII discovery, masking, redaction, export, delete, and retention workflows.
Ensure AI/ML calls receive redacted or policy-approved data.
Add privacy audit trail for data access and automation.
```

**Tests/gates:**

```text id="v7trzu"
Privacy policy tests
PII redaction tests
AI redaction tests
Data retention tests
```

**Acceptance criteria:** Sensitive data access, processing, AI usage, export, and deletion are governed and auditable.

---

## 14. Tenant isolation

**Current diagnosis:** Stronger but needs product-family runtime proof.

**Target maturity:** 4.5

**Plan:**

```text id="t4xph6"
Every request resolves tenant from authenticated identity.
Header/query tenant hints must match principal tenant.
Cross-product interactions must carry tenant/workspace scope.
Storage queries must always include tenant filter.
```

**Tests/gates:**

```text id="16v3u4"
audit-tenant-isolation.sh
Cross-product wrong-tenant tests
Storage tenant isolation tests
```

**Acceptance criteria:** No route, workflow, report, export, AI action, or cross-product interaction can cross tenant boundaries.

---

## 15. Authorization / RBAC / ABAC / scope enforcement

**Current diagnosis:** Route metadata includes required access. Needs full matrix.

**Target maturity:** 4.5

**Plan:**

```text id="f5rq7l"
Generate role-route matrix from RouteSecurityRegistry.
Test each role against each route category.
Add ABAC/scope conditions for workspace, module, tenant, client, and product.
```

**Tests/gates:**

```text id="rcrlhp"
Route entitlement contract tests
Security filter parameterized tests
Role matrix E2E tests
```

**Acceptance criteria:** Authorization is server-side, route-specific, scope-aware, and test-proven.

---

## 16. Governance, policy, compliance

**Current diagnosis:** Strong policy direction, needs runtime evidence.

**Target maturity:** 4.0

**Plan:**

```text id="ct6crh"
Define policy decision points per route/workflow.
Require policy engine for critical routes.
Record policy decision evidence.
Add simulation/dry-run for policy changes.
```

**Tests/gates:**

```text id="ugke7k"
Policy allow/deny tests
Governance route E2E
Policy simulation tests
```

**Acceptance criteria:** Critical behavior cannot execute without policy decision evidence.

---

## 17. Audit durability and evidence quality

**Current diagnosis:** Release evidence is improving, but atomic durable audit proof remains incomplete.

**Target maturity:** 4.5

**Plan:**

```text id="15n7zz"
Block critical mutations if audit cannot persist.
Store audit events durably.
Attach requestId/correlationId/tenant/principal/route/action/outcome.
Retain release evidence artifacts.
```

**Tests/gates:**

```text id="vk6j7m"
Audit sink failure tests
Blocking audit tests
Release evidence artifact checks
```

**Acceptance criteria:** Critical actions are never allowed without durable audit evidence.

---

## 18. Event correctness

**Current diagnosis:** Improved, but replay/bridge/rollback needs deeper proof.

**Target maturity:** 4.0

**Plan:**

```text id="5wczx4"
Define canonical event envelope.
Preserve envelope fields through append, query, tail, replay, and Action Plane bridge.
Add versioning and schema compatibility.
```

**Tests/gates:**

```text id="pm7d7s"
Event envelope golden tests
Replay tests
Bridge tests
Schema compatibility tests
```

**Acceptance criteria:** No event metadata is lost across storage, replay, automation, or audit.

---

## 19. Action Plane / automation correctness

**Current diagnosis:** Canonical route model improved. Automation lifecycle still needs end-to-end proof.

**Target maturity:** 4.0

**Plan:**

```text id="f6gkna"
Require identity, policy, audit, idempotency, and Runtime Truth for every action.
Add action dry-run, approval, execute, rollback, cancel, retry, and replay flows.
Expose action status and evidence.
```

**Tests/gates:**

```text id="g8s5gu"
Action lifecycle E2E
Automation rollback tests
HITL approval tests
```

**Acceptance criteria:** Automation is governed, reversible where applicable, observable, and auditable.

---

## 20. Implicit AI/ML maturity

**Current diagnosis:** AI/ML is embedded, but production governance needs stronger proof.

**Target maturity:** 4.0

**Plan:**

```text id="s9o6jl"
Make AI capability posture explicit.
Prevent heuristic fallback in production.
Add model availability checks.
Add prompt/input/output provenance.
Add cost and quality telemetry.
```

**Tests/gates:**

```text id="n56rte"
AI posture gate
AI privacy/redaction tests
AI cost budget gate
AI feedback loop tests
```

**Acceptance criteria:** AI/ML is useful, governed, implicit where appropriate, and never unsafe or silently degraded.

---

## 21. Human-in-the-loop and override control

**Current diagnosis:** Conceptually present, but takeover/delegation needs proof.

**Target maturity:** 4.0

**Plan:**

```text id="op7w1h"
Define automation autonomy levels.
Add manual takeover.
Add approval queue.
Add delegation/resume.
Add break-glass with reason, role, audit, and expiry.
```

**Tests/gates:**

```text id="oy1zw5"
HITL approval E2E
Break-glass tests
Takeover/resume tests
```

**Acceptance criteria:** Humans can interrupt automation, take over, and delegate again safely.

---

## 22. Observability: logs, metrics, traces

**Current diagnosis:** Stronger runtime validation requires metrics and trace export in production. 

**Target maturity:** 4.5

**Plan:**

```text id="uqaiez"
Require structured logs.
Propagate requestId/correlationId/traceId.
Expose metrics for latency, errors, retries, queue depth, model usage, cost, audit writes.
Add dashboards and alerts.
```

**Tests/gates:**

```text id="n9gmv5"
Observability conformance
Trace export readiness
SLO regression tests
```

**Acceptance criteria:** Operators can diagnose every failed workflow from UI action to storage/audit event.

---

## 23. Reliability and resilience

**Current diagnosis:** Needs more failure-injection coverage.

**Target maturity:** 4.0

**Plan:**

```text id="myjsph"
Add timeout, retry, circuit breaker, backpressure, and fallback policies.
Make degraded mode visible.
Fail closed for security/governance failures.
```

**Tests/gates:**

```text id="0lo36m"
Failure injection suite
Chaos-style dependency failure tests
Retry/backoff tests
```

**Acceptance criteria:** Known dependency failures produce safe, observable, tested behavior.

---

## 24. Error handling and degraded mode

**Current diagnosis:** Some graceful degradation exists, but needs route-level proof.

**Target maturity:** 4.0

**Plan:**

```text id="0zm85v"
Standardize error envelope.
Expose degraded capability status through Runtime Truth.
Ensure UI has error, empty, loading, unauthorized, unavailable states.
```

**Tests/gates:**

```text id="09ft4q"
Error envelope contract tests
UI degraded-state tests
Runtime Truth degraded-state tests
```

**Acceptance criteria:** Users and operators understand what failed, why, and what to do next.

---

## 25. Idempotency, retries, replay, rollback

**Current diagnosis:** Production dependency validation requires idempotency and transaction manager, but workflow proof remains incomplete. 

**Target maturity:** 4.5

**Plan:**

```text id="ws5hcx"
Require idempotency keys for mutating APIs.
Persist idempotency results durably.
Add retry-safe handlers.
Add replay and rollback semantics for critical flows.
```

**Tests/gates:**

```text id="rg01s5"
Duplicate request tests
Retry after partial failure tests
Replay tests
Rollback tests
```

**Acceptance criteria:** Retrying a request never duplicates side effects or corrupts state.

---

## 26. Performance

**Current diagnosis:** Checks exist, but product SLOs need hard ownership.

**Target maturity:** 4.0

**Plan:**

```text id="8omky7"
Define p50/p95/p99 latency per route family.
Define throughput and concurrency targets.
Define query, import, export, automation, and AI latency budgets.
```

**Tests/gates:**

```text id="9fp1fa"
check:interaction-performance
check:audited-performance-workflows
Data Cloud load suite
```

**Acceptance criteria:** Performance regressions fail release.

---

## 27. Scalability

**Current diagnosis:** Under-proven.

**Target maturity:** 4.0

**Plan:**

```text id="gs8ehx"
Test multi-tenant load.
Test queue growth/backpressure.
Test horizontal scaling assumptions.
Test storage tier behavior.
```

**Tests/gates:**

```text id="f1wd1q"
Load tests
Backpressure tests
Multi-tenant scale tests
Storage tier tests
```

**Acceptance criteria:** Product scales under expected tenant, data, workflow, and automation volume.

---

## 28. Extensibility and plugin model

**Current diagnosis:** Strong direction with plugin checks, but runtime plugin lifecycle proof needed.

**Target maturity:** 4.0

**Plan:**

```text id="gbg721"
Define plugin contract.
Sandbox plugins.
Validate plugin security, dependencies, upgrades, rollback, and conformance.
Expose plugin Runtime Truth.
```

**Tests/gates:**

```text id="c6xj5d"
check:kernel-plugin-interactions
check:plugin-interaction-broker
Plugin security tests
Plugin dependency resolver tests
```

**Acceptance criteria:** Plugins can be installed, validated, enabled, disabled, upgraded, and rolled back safely.

---

## 29. Shared-library reuse

**Current diagnosis:** Broad shared platform exists; must prevent over-sharing and product leakage.

**Target maturity:** 4.0

**Plan:**

```text id="wwzgra"
Define keep/move/split rules.
Keep only generic multi-product logic in shared libraries.
Move product semantics back to product modules.
Enforce dependency direction.
```

**Tests/gates:**

```text id="k7pyki"
check:architecture-boundaries
check:kernel-boundaries
check:platform-product-boundaries
Reuse scorecard
```

**Acceptance criteria:** Shared libraries are composable, generic, stable, and not product dumping grounds.

---

## 30. Dependency hygiene

**Current diagnosis:** Improving through release SBOM and dependency checks.

**Target maturity:** 4.5

**Plan:**

```text id="7fa59n"
Generate SBOM for release.
Fail on critical/high vulnerabilities.
Remove unused dependencies.
Pin versions.
Audit license compatibility.
```

**Tests/gates:**

```text id="xna17g"
security-scan-strict
SBOM artifact gate
Dependency hygiene lint
```

**Acceptance criteria:** Release artifacts include clean dependency evidence.

---

## 31. Architecture boundaries

**Current diagnosis:** Strong scripts exist; runtime boundary proof still needed.

**Target maturity:** 4.0

**Plan:**

```text id="49srh8"
Enforce module boundaries with ArchUnit and scripts.
Block product-to-product direct imports.
Require Kernel/contracts for product interactions.
```

**Tests/gates:**

```text id="o5yqeg"
check:architecture-boundaries
check:cross-product-interaction-boundaries
DataCloudPlaneBoundaryTest
```

**Acceptance criteria:** Architecture rules are executable and fail CI on drift.

---

## 32. Simplicity and maintainability

**Current diagnosis:** Product is powerful but complex.

**Target maturity:** 4.0

**Plan:**

```text id="3rl5ap"
Reduce duplicate patterns.
Prefer generated registries over manual maps.
Make Runtime Truth the main operating model.
Keep product business logic separate from platform setup.
```

**Tests/gates:**

```text id="pg6ye7"
Duplicate detection
Orphan module check
Deprecated import/package checks
```

**Acceptance criteria:** New features follow one obvious pattern and require minimal boilerplate.

---

## 33. UI/UX simplicity and consistency

**Current diagnosis:** Needs full expert route-by-route review.

**Target maturity:** 4.0

**Plan:**

```text id="nql0sk"
Use shared shell/layout primitives.
Enforce design tokens.
Ensure no dead space, no cognitive overload, no inconsistent tables/cards.
Make action discoverability consistent.
```

**Tests/gates:**

```text id="46uvbd"
check:shared-product-shells
check:shared-layout-primitives
check:design-system-conformance
Audited UI workflows
```

**Acceptance criteria:** Users can understand status, take action, and recover from errors without training.

---

## 34. Accessibility

**Current diagnosis:** Data Cloud release runs UI a11y checks, which is a good start. 

**Target maturity:** 4.0

**Plan:**

```text id="1y1j6w"
Add WCAG AA route matrix.
Run axe checks on every key route.
Add keyboard-only flows.
Add focus management tests.
Add screen-reader semantic assertions.
```

**Tests/gates:**

```text id="tyxwmt"
check:data-cloud-ui-a11y
Playwright a11y tests
Design system accessibility tests
```

**Acceptance criteria:** Core workflows are keyboard-accessible, screen-reader-friendly, and WCAG AA compliant.

---

## 35. Internationalization and localization readiness

**Current diagnosis:** Still weak relative to production-grade expectations.

**Target maturity:** 4.0

**Plan:**

```text id="kbksmr"
Add translation extraction.
Fail on missing keys.
Add pseudo-locale tests.
Add date/number/currency/timezone formatting tests.
Add RTL readiness where relevant.
```

**Tests/gates:**

```text id="ytmtk0"
check:i18n-conformance
Pseudo-locale Playwright tests
Locale formatting unit tests
```

**Acceptance criteria:** UI is locale-ready and does not hardcode user-facing strings.

---

## 36. Testing depth

**Current diagnosis:** Broad test surface, but runtime and failure tests need expansion.

**Target maturity:** 4.0

**Plan:**

```text id="hdr7xw"
Classify tests as unit, integration, browser, release, smoke, performance, failure-injection.
Ensure each critical path has all relevant tiers.
Remove duplicate/test-theater checks.
```

**Tests/gates:**

```text id="7ssujz"
Product test architecture matrix
Coverage by workflow
Release evidence tests
```

**Acceptance criteria:** Every critical claim is proven by the right test tier.

---

## 37. Test quality / no test theater

**Current diagnosis:** Improving but still must guard against presence-only tests.

**Target maturity:** 4.0

**Plan:**

```text id="ykk8bz"
Reject tests that only instantiate objects.
Require behavior assertions.
Require negative and failure assertions.
Require real runtime wiring for release tests.
```

**Tests/gates:**

```text id="a7f1su"
Test quality lint
Mutation/failure-injection tests
Runtime integration tests
```

**Acceptance criteria:** Tests prove behavior, not just structure.

---

## 38. CI gate strength

**Current diagnosis:** Strong improvement. Advisory CI and strict release gate are now separated.  

**Target maturity:** 4.5

**Plan:**

```text id="3ood42"
Keep CI fast and advisory.
Keep release strict and evidence-backed.
Add affected-product release orchestration.
Ensure required branch protection references correct gates.
```

**Acceptance criteria:** Developers get fast feedback; releases require hard evidence.

---

## 39. Release readiness

**Current diagnosis:** Data Cloud release readiness improved significantly.

**Target maturity:** 4.5

**Plan:**

```text id="0fp4j4"
Tie release workflow to protected environments.
Require approvals.
Require evidence artifacts.
Require release scorecard.
Require rollback plan.
```

**Tests/gates:** `data-cloud-release.yml`

**Acceptance criteria:** No release without environment validation, smoke, backup, security, SBOM, runtime, and evidence artifacts.

---

## 40. Deployment and operations readiness

**Current diagnosis:** Improved for Data Cloud; product-family coverage needs expansion.

**Target maturity:** 4.0

**Plan:**

```text id="15vmfo"
Validate Helm/k8s manifests.
Validate runtime profile.
Validate secrets/config.
Validate health/readiness/deep health.
Validate observability exporters.
```

**Tests/gates:** Helm/k8s release checks, runtime profile release checks.

**Acceptance criteria:** Operators can deploy, verify, observe, and roll back safely.

---

## 41. Backup, restore, and disaster recovery

**Current diagnosis:** Data Cloud release now has strict backup drill. 

**Target maturity:** 4.5

**Plan:**

```text id="gygg1o"
Run strict backup/restore in release.
Validate restore point.
Validate RPO/RTO.
Upload DR evidence.
Test Postgres, ClickHouse, OpenSearch, and object storage.
```

**Acceptance criteria:** Backup/restore failures block release.

---

## 42. Configuration and secrets management

**Current diagnosis:** Release workflow validates required environment variables and secrets and rejects localhost release endpoint. 

**Target maturity:** 4.5

**Plan:**

```text id="k24t1m"
Validate all required vars/secrets.
Reject defaults in production.
Reject localhost release endpoints.
Use environment-protected secrets.
Add secret rotation readiness.
```

**Tests/gates:**

```text id="du34b4"
validate-release-config
check:secret-default-credentials
```

**Acceptance criteria:** Production cannot run with missing, default, localhost, or unsafe config.

---

## 43. Documentation truthfulness

**Current diagnosis:** Needs continuous enforcement against generated truth.

**Target maturity:** 4.0

**Plan:**

```text id="mrm1ie"
Link docs to generated route/runtime truth.
Fail stale current-state claims.
Fail unsupported production claims.
Add evidence links to release docs.
```

**Tests/gates:**

```text id="vqz9n9"
check:doc-claims-evidence
check:current-state-claims
check:doc-truth
```

**Acceptance criteria:** Docs describe only what code/tests/release gates prove.

---

## 44. Migration and deprecation hygiene

**Current diagnosis:** Compatibility routing exists and needs lifecycle control.

**Target maturity:** 4.0

**Plan:**

```text id="jpixvy"
Define compatibility registry.
Mark deprecated routes.
Add removal dates.
Expose legacyStatus in Runtime Truth.
Block new legacy routes.
```

**Tests/gates:** route compatibility registry checks.

**Acceptance criteria:** Migration paths are explicit, tested, and eventually removed.

---

## 45. Cost and operational efficiency

**Current diagnosis:** Underdeveloped.

**Target maturity:** 4.0

**Plan:**

```text id="c7tn2n"
Add cost budgets for AI, storage, query, export, stream, and automation.
Expose usage/cost metrics.
Fail release on severe cost regression.
Add capacity forecast tests.
```

**Tests/gates:**

```text id="vl3dxl"
Cost budget gate
AI cost telemetry tests
Storage/query cost tests
```

**Acceptance criteria:** Product can be operated predictably without runaway cost.

---

## 46. Overall production readiness

**Current diagnosis:** Close but not ready.

**Target maturity:** 4.5

**Plan:**

```text id="9ztdui"
Close P0 atomic workflow proof.
Expand strict release gates to all production-intended products.
Add release scorecard artifact.
Add product-family affected release orchestration.
```

**Acceptance criteria:** All hard blocking rules pass.

---

## 47. Overall world-class maturity

**Current diagnosis:** Not yet. Strong direction, not enough proof and simplicity.

**Target maturity:** 5.0 long-term

**Plan:**

```text id="6uor2t"
Make the platform easy enough that product teams only write business logic.
Automate setup, runtime truth, tests, releases, docs, observability, and governance.
Make AI/ML implicit but interruptible.
Make every product secure, private, accessible, internationalized, observable, and low-cognitive-load by default.
```

**Acceptance criteria:** New product/features inherit production-grade foundations automatically.

---

# Final prioritized implementation waves

## Wave 1 — Production blocking

```text id="q4bdqx"
1. Add atomic workflow proof gate.
2. Add release scorecard artifact.
3. Add strict affected-product release orchestration.
4. Add production deployment trigger/protected environment policy.
5. Add typed contract schema lint.
```

## Wave 2 — Product quality

```text id="t7fqrf"
1. Add a11y route matrix.
2. Add i18n conformance.
3. Add AI governance/cost/HITL tests.
4. Add SLO/performance release gates.
5. Add product-family Runtime Truth posture.
```

## Wave 3 — World-class maturity

```text id="qj8mlc"
1. Simplify product development around Kernel lifecycle.
2. Standardize product release profiles.
3. Standardize generated docs/evidence.
4. Standardize plugin/action/automation lifecycle.
5. Standardize customer-facing UX patterns.
```

The immediate next step is **Wave 2, item 2: i18n conformance expansion across all production-intended products** while preserving strict Wave 1 and Wave 2 item 1 release blockers.
