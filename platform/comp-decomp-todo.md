## Snapshot used

I reviewed commit `367ce916067282d257852cc29649281e80cd56a1`. The commit itself only updates `products/yappc/CHANGELOG.md` and is marked `[skip ci]`, so the TODOs below are based on the **full repository snapshot at that commit**, not the diff alone. 

The snapshot is much stronger than earlier audits: root `package.json` now includes behavioral gates for atomic workflow failure injection, runtime dependency failure injection, AI governance behavioral proof, i18n behavioral proof, a11y behavioral proof, enhanced OpenAPI quality, product SLO/cost budgets, product domain invariants, aggregate gate integrity, and split phase8 fast/integration/e2e/release gates.  

Below is the **correct remaining TODO list**.

---

# P0 — Fix before calling this production-grade

## 1. Make atomic workflow failure-injection fail on warnings and product coverage gaps

**Where**

```text
scripts/check-atomic-workflow-failure-injection.mjs
.kernel/evidence/atomic-workflow-failure-injection/atomic-workflow-failure-injection-latest.json
products/data-cloud/delivery/launcher/src/test/java/...
products/data-cloud/delivery/sdk/...
products/finance/...
products/phr/...
```

**Current problem**

The atomic failure-injection evidence passes with `0` violations, but it still has `11` warnings: Data Cloud SDK has no atomic workflow failure-injection tests; Finance Gateway and PHR Gateway paths are not found; only **1 product** actually executed real atomic workflow failure-injection tests. 

The script itself also falls back to posture checks when test execution fails or tests are missing.   

**TODO**

```text
Change check-atomic-workflow-failure-injection.mjs so CI/release mode fails on warnings.

Replace hardcoded product paths with canonical product registry resolution.

Remove or strictly gate fallback posture checks. In release mode, fallback should fail unless the product is explicitly marked not-applicable with a waiver.

Add atomic workflow failure-injection tests for:
- Data Cloud SDK, or mark SDK as non-mutating/non-runtime with explicit waiver.
- Finance product actual gateway/backend path.
- PHR product actual gateway/backend path.
- Any other active product exposing critical mutations.
```

**Acceptance criteria**

```text
atomic-workflow-failure-injection-latest.json has:
- totalViolations = 0
- totalWarnings = 0
- executedTestProductCount equals all applicable production products
- no “Product path not found”
- no “falling back to posture checks”
```

---

## 2. Make atomic workflow tests verify side-effect rollback, not just scenario existence

**Where**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java
products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/
scripts/check-atomic-workflow-failure-injection.mjs
```

**Current problem**

`EntityCrudHandler` has transaction-aware save logic for entity save + event append + audit/outbox/idempotency handling. It validates production requirements for durable idempotency, `TransactionManager`, `AuditService`, and `EntityWriteOutboxProcessor`. 

The save path runs transactional logic when `transactionManager != null`, but still supports a non-transactional path outside production-like profiles. It stores idempotency after building the response, and audit payload is placed into outbox payload rather than independently proving durable audit write behavior in the same test path. 

**TODO**

```text
Add explicit assertions in Data Cloud Launcher atomic tests proving:

1. business write succeeds, event append fails:
   - no entity committed
   - no idempotency cache committed
   - no outbox entry committed
   - no success response returned

2. event append succeeds, audit/outbox fails:
   - transaction rolls back or request fails closed
   - event does not remain orphaned
   - no success response returned

3. idempotency write fails:
   - response behavior is deterministic
   - retry does not duplicate entity/event/audit/outbox

4. retry after partial failure:
   - no duplicate entity
   - no duplicate event
   - no duplicate audit
   - same idempotency key returns consistent result

5. replay after crash:
   - outbox/replay recovers once
   - replay is idempotent
```

**Acceptance criteria**

```text
Atomic tests inspect actual store state before and after failure, not only response codes or string tokens.
```

---

# P1 — High-priority production hardening

## 3. Expand runtime dependency failure-injection beyond Data Cloud Launcher and Digital Marketing bridge

**Where**

```text
scripts/check-runtime-dependency-failure-injection.mjs
.kernel/evidence/runtime-dependency-failure-injection/runtime-dependency-failure-injection-latest.json
products/finance/...
products/phr/...
products/flashit/...
products/yappc/...
```

**Current problem**

Runtime dependency failure evidence is clean for **Data Cloud Launcher** and **Digital Marketing Kernel Bridge** only. It covers Postgres, ClickHouse, OpenSearch, S3, audit sink, policy engine, AI completion, network timeout, queue saturation, retry, and backoff for those two products. 

The script’s product list only checks Data Cloud Launcher and Digital Marketing Kernel Bridge. 

**TODO**

```text
Resolve applicable products from canonical product registry instead of hardcoded product list.

Add per-product dependency failure suites for:
- Finance
- PHR
- Flashit
- YAPPC
- Any active lifecycle-enabled backend product

For each product, classify applicable dependencies:
- database
- event store
- object storage
- search/index
- audit
- policy
- AI/LLM
- queue/broker
- network timeout
- backpressure/queue saturation
```

**Acceptance criteria**

```text
runtime-dependency-failure-injection-latest.json includes all applicable active production products, not just Data Cloud Launcher and Digital Marketing Kernel Bridge.
```

---

## 4. Make runtime dependency failure-injection fail on fallback posture checks

**Where**

```text
scripts/check-runtime-dependency-failure-injection.mjs
```

**Current problem**

The script executes tests when found, but can fall back to scanning for failure-injection infrastructure and scenario tokens if tests do not execute.  

**TODO**

```text
In release mode:
- fail if no executable dependency failure test is found
- fail if test execution fails
- fail if scenario coverage comes only from source scanning
- allow not-applicable only with explicit product/dependency waiver
```

**Acceptance criteria**

```text
Runtime failure-injection release evidence proves execution, not source-token presence.
```

---

## 5. Fix product path resolution for Finance and PHR

**Where**

```text
scripts/check-atomic-workflow-failure-injection.mjs
scripts/check-runtime-dependency-failure-injection.mjs
scripts/resolve-affected-products.mjs
config/product-registry*
```

**Current problem**

Atomic workflow evidence reports:

```text
Finance Gateway: Product path not found at products/finance/gateway
PHR Gateway: Product path not found at products/phr/gateway
```



**TODO**

```text
Stop hardcoding product paths in release proof scripts.

Load product surfaces from canonical product registry.

For each product, resolve:
- backend-api surface path
- gateway surface path
- web surface path
- lifecycle build/test command
- release profile
- applicable proof gates

Update Finance and PHR surface paths to match actual repository layout.
```

**Acceptance criteria**

```text
No release evidence contains “Product path not found.”
```

---

## 6. Add product-specific release readiness evidence

**Where**

```text
scripts/check-product-release-readiness.mjs
.github/workflows/product-release.yml
.kernel/evidence/product-release-readiness.json
```

**Current problem**

`product-release-readiness.json` passes all journey groups and scripts, but it is still an aggregate evidence file. It proves broad release gates executed, not a product-by-product maturity verdict. 

**TODO**

```text
Generate one evidence file per affected product:

.kernel/evidence/product-release-readiness.<productId>.json

Each file must include:
- product ID
- product kind
- affected surfaces
- release profile
- applicable gates
- skipped gates with reason
- workflow evidence
- a11y evidence
- i18n evidence
- AI governance evidence
- security evidence
- performance/SLO evidence
- final verdict
```

**Acceptance criteria**

```text
product-release.yml uploads product-specific release evidence for every affected product.
```

---

## 7. Tighten Data Cloud release evidence validation

**Where**

```text
.github/workflows/data-cloud-release.yml
scripts/validate-release-evidence.mjs
scripts/generate-release-maturity-summary.mjs
release-evidence/
.kernel/evidence/
```

**Current state**

The release workflow now validates runtime profile, implementation-plan coverage, UI a11y, product a11y matrix, i18n, AI governance, SLO budgets, cost budgets, domain invariants, OpenAPI release quality, and OpenAPI breaking changes. It uploads corresponding evidence artifacts and validates release evidence content/freshness. 

**Remaining TODO**

```text
Extend validate-release-evidence.mjs to fail if:
- any evidence file has warnings
- any evidence file has fallback posture checks
- any evidence file is generated-on-demand without commit/release identity
- any product expected by affected-products.json has no product-specific evidence
- atomic workflow evidence executedTestProductCount < expected applicable products
- runtime dependency evidence executedTestProductCount < expected applicable products
```

**Acceptance criteria**

```text
Release gate validates evidence quality, not only evidence existence.
```

---

## 8. Improve `check-atomic-workflow-failure-injection.mjs` command execution portability

**Where**

```text
scripts/check-atomic-workflow-failure-injection.mjs
scripts/run-gradle-wrapper.mjs
```

**Current problem**

Root package scripts were updated to use `node ./scripts/run-gradle-wrapper.mjs` for Gradle portability, but `check-atomic-workflow-failure-injection.mjs` still builds shell command strings using `./gradlew` / `gradlew.bat`.  

**TODO**

```text
Replace shell-string Gradle execution with execFileSync(process.execPath, ['./scripts/run-gradle-wrapper.mjs', ...]).

Use same Gradle wrapper abstraction everywhere.
```

**Acceptance criteria**

```text
Atomic workflow failure-injection tests run consistently on Linux/macOS/Windows and in CI.
```

---

## 9. Fix AI governance behavioral proof to use correct product tasks

**Where**

```text
scripts/check-ai-governance-behavioral-proof.mjs
products/data-cloud/...
products/aep/...
products/*/...
```

**Current problem**

The AI behavioral proof script claims to validate model availability, fallback prevention, redaction, provenance, cost, eval thresholds, HITL approval, and audit evidence. But the test execution path is hardcoded to `:products:aep:test`, which may not be the correct task for Data Cloud Action Plane or other AI-enabled products. 

**TODO**

```text
Resolve AI-enabled products from product registry.

For each AI-enabled product, run its actual test command.

Replace hardcoded :products:aep:test with product-specific lifecycle test task.

Require evidence per AI-enabled product:
- model availability
- fallback disabled in production
- privacy redaction before model call
- prompt/input/output provenance
- cost budget
- quality threshold
- HITL approval for risky action
- audit evidence
```

**Acceptance criteria**

```text
AI governance behavioral evidence is product-scoped and executable, not AEP-task hardcoded.
```

---

## 10. Promote warnings to release failures across all behavioral proof scripts

**Where**

```text
scripts/check-atomic-workflow-failure-injection.mjs
scripts/check-runtime-dependency-failure-injection.mjs
scripts/check-ai-governance-behavioral-proof.mjs
scripts/check-i18n-behavioral-proof.mjs
scripts/check-a11y-behavioral-proof.mjs
```

**TODO**

```text
Add a shared helper:
scripts/lib/release-evidence-policy.mjs

Rules:
- local mode may warn
- CI mode may warn only for non-release checks
- release mode fails on warnings
- not-applicable requires explicit waiver
- every waiver must include owner, reason, expiry, and linked TODO
```

**Acceptance criteria**

```text
No release proof passes with warnings unless explicitly waived.
```

---

# P2 — Product quality and completeness

## 11. Make product completeness gate feature-level, not script-level

**Where**

```text
scripts/check-product-feature-completeness.mjs
scripts/check-product-shape-capability-matrix.mjs
config/product-registry*
```

**TODO**

```text
For each active product, generate a feature matrix:

feature
owner
route/API
UI surface
storage
audit
policy
tenant isolation
Runtime Truth
tests
release gate
status: Complete/Partial/Missing/Broken/Overbuilt
```

**Acceptance criteria**

```text
No product passes production readiness if customer-visible features are Partial, Missing, or Broken.
```

---

## 12. Add domain-specific invariant tests per product

**Where**

```text
scripts/check-product-domain-invariants.mjs
products/finance/...
products/phr/...
products/digital-marketing/...
products/data-cloud/...
products/yappc/...
```

**TODO**

```text
Define product domain invariants:

Finance:
- transaction correctness
- portfolio/report calculations
- tenant/client visibility
- audit for financial mutations

PHR:
- consent
- patient data privacy
- care plan lifecycle
- secure sharing

Digital Marketing:
- campaign lifecycle
- consent-based activation
- notification preferences
- segmentation privacy

Data Cloud:
- event envelope
- route security metadata
- tenant isolation
- governance policy enforcement

YAPPC:
- artifact authoring lifecycle
- canvas/builder roundtrip
- source acquisition
- workflow persistence
```

**Acceptance criteria**

```text
Each active product has deterministic domain invariant tests and release evidence.
```

---

## 13. Strengthen SLO and cost budgets

**Where**

```text
scripts/check-product-slo-budgets.mjs
scripts/check-product-cost-budgets.mjs
config/product-slo-budgets.json
config/product-cost-budgets.json
```

**TODO**

```text
Move from presence checks to measured thresholds.

For each product define:
- p95 latency
- p99 latency
- max memory
- max bundle size
- max job duration
- max AI cost per workflow
- max query/export cost
- max storage growth
```

**Acceptance criteria**

```text
Performance or cost regression fails release.
```

---

## 14. Expand a11y behavioral proof beyond route matrix

**Where**

```text
scripts/check-a11y-behavioral-proof.mjs
scripts/check-product-a11y-route-matrix.mjs
.github/workflows/accessibility.yml
products/*/e2e/*a11y*
```

**TODO**

```text
Require tests for:
- keyboard-only navigation
- focus management
- dialog focus trap
- table/grid roles
- chart descriptions
- form error announcements
- toast/live-region behavior
- skip links / landmarks
```

**Acceptance criteria**

```text
A11y behavioral proof validates actual user interaction, not only spec/workflow presence.
```

---

## 15. Expand i18n behavioral proof beyond token checks

**Where**

```text
scripts/check-i18n-behavioral-proof.mjs
scripts/check-i18n-conformance.mjs
products/*/src/i18n/
products/*/ui/
```

**TODO**

```text
Add:
- missing translation key extraction
- hardcoded user-facing string scan
- pseudo-locale Playwright route execution
- locale date/number/currency/timezone assertions
- validation/error/empty/loading-state localization checks
```

**Acceptance criteria**

```text
Every active web product can run in pseudo-locale without missing keys or layout-breaking text.
```

---

## 16. Improve release evidence storage policy

**Where**

```text
.kernel/evidence/
release-evidence/
scripts/validate-release-evidence.mjs
```

**Current issue**

The target commit itself is `[skip ci]` and changelog-only; generated evidence files can create noisy commits if checked in frequently. 

**TODO**

```text
Separate:
- canonical evidence schemas committed to repo
- generated release evidence uploaded as CI artifacts
- intentionally snapshotted release evidence committed only for release tags

Add lint:
- block timestamp-only evidence commits unless commit is marked release-evidence-refresh
```

**Acceptance criteria**

```text
Repository history clearly separates implementation changes from generated evidence refreshes.
```

---

# P3 — Cleanup and maintainability

## 17. Standardize release proof script structure

**Where**

```text
scripts/check-*-behavioral-proof.mjs
scripts/check-*-failure-injection.mjs
scripts/lib/
```

**TODO**

```text
Extract shared helpers for:
- product registry loading
- product/surface resolution
- executing product-specific checks
- evidence writing
- warning/waiver policy
- release-mode strictness
```

**Acceptance criteria**

```text
Behavioral proof scripts are small, deterministic, and reuse the same validation framework.
```

---

## 18. Remove hardcoded product lists from release proof scripts

**Where**

```text
scripts/check-atomic-workflow-failure-injection.mjs
scripts/check-runtime-dependency-failure-injection.mjs
scripts/check-ai-governance-behavioral-proof.mjs
```

**TODO**

```text
Use product registry to discover:
- active products
- lifecycle-enabled products
- backend products
- web products
- AI-enabled products
- products with critical mutations
```

**Acceptance criteria**

```text
Adding a new production product automatically brings it into the correct release proof gates.
```

---

## 19. Add waiver registry for non-applicable gates

**Where**

```text
config/release-proof-waivers.json
scripts/lib/release-evidence-policy.mjs
```

**TODO**

```text
Create waiver format:

productId
dimension
gate
reason
owner
expiryDate
replacementTodo
```

**Acceptance criteria**

```text
No product silently skips a production-readiness dimension.
```

---

## 20. Make release summary score all 47 dimensions per product

**Where**

```text
scripts/generate-release-maturity-summary.mjs
release-evidence/release-summary.json
release-evidence/release-summary.md
```

**TODO**

```text
For every affected product, output:

dimension
score
evidence file
gate result
warnings
waivers
blocking status
required next action
```

**Acceptance criteria**

```text
The release summary becomes a deterministic ship/no-ship artifact.
```

---

# Recommended next implementation sequence

```text
1. Fix product path resolution and remove hardcoded product lists.
2. Make release-mode warnings fail.
3. Add product-specific release readiness evidence.
4. Make atomic failure-injection cover all applicable products.
5. Make runtime dependency failure-injection cover all applicable products.
6. Fix AI governance behavioral proof to use product-specific tests.
7. Add waiver registry for legitimate not-applicable gates.
8. Strengthen release evidence validation to parse semantics, warnings, waivers, and product coverage.
9. Add feature-level completeness matrix per product.
10. Add per-product domain invariant tests.
```

## Best next commit title

```text
harden(release): make behavioral proof product-scoped and warning-fatal
```

This is the correct next step because the platform now has many strong gates, but the remaining risk is that some gates still pass with warnings, hardcoded product paths, product coverage gaps, or fallback posture checks.
