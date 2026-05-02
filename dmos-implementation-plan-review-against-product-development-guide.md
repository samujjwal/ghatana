# DMOS Implementation Plan Review Against Product Development Guide

**Reviewed artifact:** `digital-marketing-product-implementation-plan.md`  
**Review guideline:** `PRODUCT_DEVELOPMENT_GUIDE.md`  
**Product:** Digital Marketing Operating System (DMOS)  
**Rule prefix:** `DM-`  
**Review date:** 2026-05-01

---

## 1. Executive Verdict

The implementation plan is **substantially aligned** with the Product Development Guide and is a strong execution plan. It already includes the major required items:

- Domain Pack Model
- `DigitalMarketingBoundaryPolicyStore`
- `DigitalMarketingComplianceRulePack`
- `DM-` rule prefix
- default-deny boundary policy rule
- product-owned regulatory and marketing compliance packs
- Gradle validation tasks
- pack contract tests
- bridge/context propagation concepts
- no direct kernel implementation imports
- product-local implementation under `products/digital-marketing/`

However, the plan should be **amended before development starts** because several guide requirements are present only indirectly. The Product Development Guide makes plugin binding, kernel bridge adapter behavior, BridgeContext propagation, reference-consumer hygiene, and pack/bridge contract tests mandatory. The plan should make these explicit P0 tasks rather than leaving them distributed across other tasks.

**Recommendation:** keep the current plan as the base, but add the P0 amendments in Section 5 of this review before treating the plan as implementation-ready.

---

## 2. Guide Compliance Matrix

| Product Development Guide Requirement | Current Plan Coverage | Status | Required Action |
|---|---|---:|---|
| Products integrate through Domain Pack Model, Plugin Binding, and Kernel Bridge | Domain pack and bridge are covered; plugin binding is implied but not a standalone task | ⚠️ Partial | Add explicit `DMOS-R0-009: Product Plugin Binding Registry` |
| Product code must never import kernel implementation classes directly | Global DoD and R0 tasks mention this | ✅ Good | Strengthen with ArchUnit/static dependency tests for every product module |
| BoundaryPolicyStore must be product-owned | `DMOS-R0-004` covers `DigitalMarketingBoundaryPolicyStore` | ✅ Good | Add first-pass concrete rule table to the task |
| Boundary policy last rule must be default-deny over product scope | `DMOS-R0-004` explicitly covers this | ✅ Good | Keep as P0 blocker |
| Boundary rule IDs must use product prefix | `DMOS-R0-004` uses `DM-BP-` | ✅ Good | Keep validation and negative tests |
| Consent-sensitive reads must set `requiresConsent(true)` | `DMOS-R0-004` covers sensitive contact/lead reads | ✅ Good | Add explicit resource/action matrix for contacts, leads, audiences, exports |
| Audit-sensitive operations must set `requiresAudit(true)` | `DMOS-R0-004` covers sensitive writes | ✅ Good | Add audit assertions in policy tests |
| ComplianceRulePack must be product-owned and registered with `CompliancePlugin` | `DMOS-R0-005` covers product-owned rule packs | ✅ Good | Add explicit rule set IDs and startup registration test |
| Compliance rule IDs must use product prefix and unique rule set constants | `DMOS-R0-005` covers this | ✅ Good | Add cross-pack uniqueness test |
| Packs registered at product startup, not kernel boot | `DMOS-R0-005` mentions this | ⚠️ Needs stronger implementation detail | Add plugin binding registry startup task and startup integration test |
| Gradle validation tasks required | `DMOS-R0-006` covers `validateDomainPackManifest`, `validatePolicyPack`, `validateComplianceRulePack` | ✅ Good | Ensure validation tasks are wired to `check` in every relevant product module |
| Products supply SPI implementations for platform plugins | Not explicit enough | ❌ Gap | Add product plugin binding task for compliance, consent, human approval, risk/fraud where used |
| No product logic inside platform plugins | Global DoD covers plugin purity | ✅ Good | Add a static grep/ArchUnit gate for accidental `DMOS` terms in plugin code if plugins are touched |
| Kernel Bridge uses `AbstractKernelBridge` adapter pattern | Connector runtime references BridgeContext but no explicit DMOS kernel adapter task | ⚠️ Partial | Add `DMOS-R0-010: DigitalMarketingKernelBridgeAdapter` |
| Bridge adapter must call `requireStarted()` | Not explicit at adapter-method level | ⚠️ Partial | Add adapter acceptance criterion and tests |
| Bridge adapter must call `checkAuthorized()` before sensitive operations | Mentioned generally | ⚠️ Partial | Add bridge integration tests for authorized/denied paths |
| Bridge adapter must use `executeWithRetry()` for transient failures | Mentioned generally in connector task | ⚠️ Partial | Add adapter-specific retry tests |
| Bridge adapter must use `redact()` for sensitive metadata | Mentioned in connector task | ⚠️ Partial | Add log redaction tests at bridge level |
| Every bridge call must carry `BridgeContext` | `DMOS-R0-007`, `F1-002`, `F2-006` mention context | ✅ Mostly good | Add static/API tests disallowing bridge calls without `BridgeContext` |
| `BridgeContext.tenantId` required | Plan covers tenant context | ✅ Good | Add null/missing tenant test |
| `principalId`, `correlationId`, `idempotencyKey` propagation | Plan covers but distributed | ⚠️ Partial | Add end-to-end workflow test verifying all fields reach adapter and audit event |
| PHR/Finance are reference consumers, not defaults/templates | Not explicitly stated in plan | ❌ Gap | Add reference-consumer hygiene task and rename `Finance/Risk` owner wording |
| Pack contract tests required | `DMOS-R0-004`, `R0-006`, and final checklist cover these | ✅ Good | Add exact test class names and required assertions |
| Bridge integration tests required | Connector tests cover some; guide lists explicit cases | ⚠️ Partial | Add a dedicated bridge integration test task with all required cases |
| Product Pack Definition of Done | Mostly covered | ✅ Good | Add a final product-pack DoD checklist as a release gate |

---

## 3. Strengths in the Current Implementation Plan

### 3.1 Domain Pack Model Is Correctly Prioritized

The plan places domain pack manifest, boundary policy store, compliance rule pack, and validation tasks in R0 before feature implementation. This is exactly the right sequence because policy and compliance behavior must exist before campaign, contact, connector, and workflow features are built.

### 3.2 Boundary Policy Is Treated as a Product Concern

`DigitalMarketingBoundaryPolicyStore` is correctly product-owned, uses `DM-BP-` IDs, includes default-deny, and covers sensitive resources such as contact, lead, campaign launch, budget updates, connector writes, approvals, exports, and audit-sensitive operations.

### 3.3 Compliance Rules Are Correctly Product-Owned

The implementation plan keeps DMOS-specific marketing and regulatory checks in product-owned compliance rule packs rather than inside generic plugins. This preserves plugin purity and allows the same generic compliance engine to serve many products.

### 3.4 Validation and CI Gates Are Included Early

The plan correctly includes `validateDomainPackManifest`, `validatePolicyPack`, `validateComplianceRulePack`, architecture tests, pack contract tests, and CI wiring. This avoids drifting from kernel onboarding requirements.

### 3.5 Context, Idempotency, and Audit Are Treated as Cross-Cutting

The plan correctly treats tenant ID, workspace ID, principal ID, correlation ID, causation ID, and idempotency key as foundational product contracts rather than feature-specific details.

---

## 4. Key Gaps and Corrections

### Gap 1 — Plugin Binding Is Implied, Not Explicit

The guide requires products to supply SPI implementations for platform plugins. The current plan covers compliance rule packs, consent foundation, approval workflow, risk/budget guardrails, and audit usage, but it does not add a standalone task to wire product-specific bindings into plugin SPIs at product startup.

**Correction:** add `DMOS-R0-009: Implement Product Plugin Binding Registry`.

This task should bind:

- `DigitalMarketingComplianceRulePack` to `CompliancePlugin`
- consent policy/configuration provider to `ConsentPlugin`
- approval workflow/risk routing provider to `HumanApprovalPlugin`
- optional budget/fraud/risk providers to `RiskPlugin` or `FraudDetectionPlugin` only when actually used
- audit event mapping to `AuditTrailPlugin`

### Gap 2 — Kernel Bridge Adapter Is Not a Dedicated Task

The guide requires adapters to extend `AbstractKernelBridge` and use `BridgeAuthorizationService`, `BridgeAuditEmitter`, `BridgeHealthIndicator`, `requireStarted()`, `checkAuthorized()`, `executeWithRetry()`, and `redact()`. The plan covers connector runtime, but it should explicitly define a DMOS bridge adapter layer for Data Cloud/AEP/kernel-facing operations.

**Correction:** add `DMOS-R0-010: Implement DigitalMarketingKernelBridgeAdapter`.

This is separate from external ad/CMS/email connectors. External connectors call Google/WordPress/etc.; kernel bridge adapters connect DMOS to platform/kernel capabilities.

### Gap 3 — Bridge Integration Tests Should Be P0, Not Later

The guide says bridge integration tests must cover successful operation, denied operation, transient failure with retry, and timeout path. The plan has some connector tests, but the dedicated bridge test cases need to be required before execution-phase integrations are accepted.

**Correction:** add `DMOS-R0-012: Create Product Pack and Bridge Contract Test Harness`, and make it a dependency for `F2-006` connector runtime.

### Gap 4 — Reference Consumer Hygiene Is Missing

The updated guide clarifies that PHR and Finance are reference consumers, not platform defaults. DMOS should not copy `PHR-`, `FIN-`, `PhrBoundaryPolicyStore`, `FinanceBoundaryPolicyStore`, product-specific healthcare/finance resources, or their regulatory pack structure as templates.

**Correction:** add `DMOS-R0-011: Enforce Reference Consumer Hygiene and Domain Neutrality`.

Also rename the owner string `Product Engineering + Finance/Risk` in `DMOS-F1-014` to avoid confusing the Finance product with budget/commercial risk ownership. Use `Product Engineering + Budget/Risk` or `Product Engineering + Commercial Risk`.

### Gap 5 — BridgeContext Propagation Needs a Whole-Flow Test

The plan mentions `BridgeContext`, but acceptance criteria should require propagation across a full workflow:

`API request → security context → command → workflow execution → adapter call → audit event`.

**Correction:** add an E2E test that asserts `tenantId`, `principalId`, `correlationId`, and `idempotencyKey` are present at every boundary.

### Gap 6 — Boundary Policy Needs a First-Pass Rule Table

The plan says to define rules, but implementers will benefit from a starter table to reduce ambiguity.

**Correction:** add a DMOS boundary policy starter matrix:

| Rule ID | Resource | Actions | Effect | Flags |
|---|---|---|---|---|
| `DM-BP-001` | `workspaces/**` | `read` | `ALLOW` | `requiresAudit=false` unless sensitive |
| `DM-BP-002` | `contacts/**` | `read` | `ALLOW` | `requiresConsent=true`, `requiresAudit=true` |
| `DM-BP-003` | `contacts/**` | `write`, `delete`, `export` | `REQUIRE_APPROVAL` or `DENY` depending action | `requiresAudit=true` |
| `DM-BP-004` | `audiences/**` | `export`, `sync` | `REQUIRE_APPROVAL` | `requiresConsent=true`, `requiresAudit=true` |
| `DM-BP-005` | `campaigns/**` | `launch`, `pause`, `resume` | `REQUIRE_APPROVAL` | `requiresAudit=true` |
| `DM-BP-006` | `budgets/**` | `write`, `increase` | `REQUIRE_APPROVAL` | `requiresAudit=true` |
| `DM-BP-007` | `content/**` | `publish` | `REQUIRE_APPROVAL` | `requiresAudit=true` |
| `DM-BP-008` | `connectors/**` | `write`, `execute` | `REQUIRE_APPROVAL` | `requiresAudit=true` |
| `DM-BP-999` | `**` / `digital-marketing.*` | `*` | `DENY` | default-deny |

### Gap 7 — Compliance Rule Packs Need Named Rule Sets

The plan names categories but should specify initial rule set constants so implementations do not invent inconsistent names.

**Correction:** add initial constants:

- `DM_MARKETING_INTEGRITY`
- `DM_CONSENT_LIFECYCLE`
- `DM_AUDIT_TRACEABILITY`
- `DM_CAMPAIGN_PREFLIGHT`
- `DM_CLAIMS_DISCLOSURES`
- `DM_EMAIL_COMPLIANCE`
- `DM_CONNECTOR_EXECUTION_SAFETY`

### Gap 8 — Plugin Startup Registration Needs Tests

The plan says packs are registered at product startup, but it should require tests proving plugin registration does not happen at kernel boot and does happen when DMOS starts.

**Correction:** add startup tests:

- kernel boot without DMOS does not register DMOS packs
- DMOS startup registers DMOS packs once
- duplicate startup does not duplicate rule registrations
- missing plugin fails gracefully with clear startup error or feature-disabled mode

---

## 5. Required P0 Amendments to the Implementation Plan

The following task cards should be added after `DMOS-R0-008` and before `DMOS-F1-001`.

---

### DMOS-R0-009: Implement Product Plugin Binding Registry

**Phase:** R0 — Readiness  
**Priority:** P0  
**Primary owner:** Product Engineering + Platform Engineering  
**Depends on:** `DMOS-R0-003`, `DMOS-R0-004`, `DMOS-R0-005`, `DMOS-R0-006`

#### What

Create a product-owned plugin binding registry that wires DMOS rule packs, policy providers, approval providers, consent providers, risk providers, and audit mappings to platform plugin SPIs at DMOS product startup.

#### Why

The Product Development Guide requires products to supply SPI implementations for platform plugins. Generic plugins must remain product-agnostic; DMOS-specific behavior must be registered from the product layer, not embedded in plugin or kernel modules.

#### How

Create `DigitalMarketingPluginBindings` or equivalent in the product module. Register:

- `DigitalMarketingComplianceRulePack` with `CompliancePlugin`
- DMOS consent policy/configuration provider with `ConsentPlugin`
- DMOS approval routing provider with `HumanApprovalPlugin`
- DMOS risk/budget provider with `RiskPlugin` if used
- DMOS audit event classification/mapping with `AuditTrailPlugin`

Registration must occur at product startup, never kernel boot. Keep all regulatory and marketing-specific rule logic in product packs.

#### Acceptance Criteria

- Product startup registers DMOS compliance rule packs with `CompliancePlugin`.
- Product startup binds consent, approval, risk, and audit SPI implementations only when the corresponding plugin is enabled.
- Kernel boot does not register DMOS-specific packs.
- Plugin modules contain no DMOS-specific production logic.
- Registration is idempotent and does not duplicate rule packs on repeated startup.
- Missing optional plugins fail with clear error or feature-disabled state, not silent partial behavior.

#### Required Test Cases

- Startup integration test: DMOS startup registers `DM_MARKETING_INTEGRITY` and other DM rule sets.
- Kernel-only boot test: no DMOS rule sets are registered.
- Duplicate startup test: rule packs are registered once.
- Disabled plugin test: DMOS reports missing plugin clearly and disables dependent features.
- Purity test: no `DMOS`, `DigitalMarketing`, `DM-`, or marketing-domain rule logic appears in platform plugin main sources.

---

### DMOS-R0-010: Implement DigitalMarketingKernelBridgeAdapter

**Phase:** R0 — Readiness  
**Priority:** P0  
**Primary owner:** Product Engineering + Platform Engineering  
**Depends on:** `DMOS-R0-001`, `DMOS-R0-007`

#### What

Create a product-owned adapter layer for DMOS interactions with kernel/platform bridge capabilities using `AbstractKernelBridge` and public bridge ports.

#### Why

The Product Development Guide requires kernel-connected adapters to use the bridge pattern. DMOS will need bridge-backed access to AEP/Data Cloud/kernel capabilities, but must not directly import kernel implementation classes.

#### How

Create `DigitalMarketingKernelAdapter` public contract and `DigitalMarketingKernelAdapterImpl extends AbstractKernelBridge`. Use:

- `BridgeAuthorizationService`
- `BridgeAuditEmitter`
- `BridgeHealthIndicator`
- `BridgeContext`
- `requireStarted()`
- `checkAuthorized()`
- `executeWithRetry()`
- `redact()`

This adapter should wrap platform-facing operations only. External Google Ads/CMS/email connectors remain separate product connector implementations but must carry the same context fields.

#### Acceptance Criteria

- Adapter uses only public kernel bridge interfaces and ports.
- Every adapter method accepts `BridgeContext`.
- Every adapter method calls `requireStarted()` before operation logic.
- Sensitive operations call `checkAuthorized()` before execution.
- Transient operations use bounded retry via `executeWithRetry()`.
- Logs redact sensitive metadata.
- Adapter emits audit and health signals through bridge ports.

#### Required Test Cases

- Success path: authorized bridge call returns healthy result and emits success audit.
- Denied path: unauthorized call fails and emits denied audit.
- Transient failure path: retry occurs within configured bound; health degrades appropriately.
- Timeout path: timeout is handled and health/audit reflect failure.
- Not-started path: method call before `markStarted()` fails safely.
- Redaction test: secrets/tokens/PII do not appear in logs.

---

### DMOS-R0-011: Enforce Reference Consumer Hygiene and Domain Neutrality

**Phase:** R0 — Readiness  
**Priority:** P0  
**Primary owner:** Architecture + Build Engineering  
**Depends on:** `DMOS-R0-002`

#### What

Add a static hygiene check to ensure DMOS does not copy PHR/Finance reference consumer identifiers, rule prefixes, product-specific resource names, or domain terms into DMOS product packs or generic kernel/plugin code.

#### Why

PHR and Finance are reference consumers, not platform defaults or templates. DMOS must follow the same pattern with its own `DM-` prefix and marketing-specific resources.

#### How

Add a validation script or ArchUnit/static test that scans DMOS product packs and product docs for accidental copied identifiers such as:

- `PHR-`, `FIN-`
- `PhrBoundaryPolicyStore`, `FinanceBoundaryPolicyStore`
- `PHR_`, `FIN_`
- `patient.records`, `trade.records`
- product-specific healthcare/finance example rules unless explicitly in a “reference-only” documentation section

Also rename ambiguous implementation-plan owner labels like `Finance/Risk` to `Budget/Risk` or `Commercial Risk`.

#### Acceptance Criteria

- DMOS packs use only `DM-` rule IDs and `DM_` rule set constants.
- DMOS policy resources are marketing-domain resources, not copied PHR/Finance resources.
- Product docs clearly state that PHR/Finance are reference consumers only.
- Generic plugins and kernel code contain no DMOS-specific terms.
- Ambiguous `Finance/Risk` owner wording is replaced.

#### Required Test Cases

- Static scan fails if `PHR-` or `FIN-` appears in DMOS pack code.
- Static scan fails if `PhrBoundaryPolicyStore` or `FinanceBoundaryPolicyStore` appears outside reference-only docs.
- Static scan passes with `DM-BP-*` and `DM_*` constants.
- Docs check confirms DMOS guide references PHR/Finance only as examples, not dependencies.

---

### DMOS-R0-012: Create Product Pack and Bridge Contract Test Harness

**Phase:** R0 — Readiness  
**Priority:** P0  
**Primary owner:** Test Engineering + Product Engineering  
**Depends on:** `DMOS-R0-004`, `DMOS-R0-005`, `DMOS-R0-010`

#### What

Create a reusable test harness for product pack contract tests and bridge integration tests.

#### Why

The guide requires `*PackContractTest` for every product and bridge integration tests for successful, denied, retry, and timeout paths. These should not be optional or deferred because DMOS relies on policy, compliance, and bridge behavior from the beginning.

#### How

Create:

- `DigitalMarketingPackContractTest`
- `DigitalMarketingBoundaryPolicyStoreContractTest`
- `DigitalMarketingComplianceRulePackContractTest`
- `DigitalMarketingKernelBridgeIntegrationTest`
- test fixtures for valid and invalid domain pack manifests
- reusable assertions for default-deny, rule ID prefix, rule set uniqueness, consent/audit flags, and bridge context propagation

#### Acceptance Criteria

- Pack contract tests run under product `check`.
- Bridge integration tests run in the appropriate integration test suite.
- Negative fixtures prove validation failures are caught.
- Tests assert no kernel implementation classes are extended directly.
- Tests assert all bridge calls carry full `BridgeContext`.

#### Required Test Cases

- Boundary policy non-empty and well-formed.
- Last boundary policy rule is default-deny.
- Sensitive contact read requires consent and audit.
- Campaign launch requires approval and audit.
- Compliance rule packs are non-empty and all rule IDs start with `DM-`.
- Rule set IDs are unique across DMOS packs.
- Store superclass is `Object` and uses only public interfaces.
- Bridge success path works with full `BridgeContext`.
- Bridge denied path emits denied audit event.
- Bridge transient failure retries and updates health.
- Bridge timeout path is deterministic and observable.

---

## 6. Required Edits to Existing Tasks

### Edit `DMOS-R0-003: Define Product Domain Pack Manifest`

Add acceptance criteria:

- Manifest includes plugin binding declarations or references `DigitalMarketingPluginBindings`.
- Manifest explicitly states PHR and Finance are reference consumers only and not inherited defaults.
- Manifest validation fails for `PHR-` or `FIN-` rule prefixes in DMOS pack declarations.

### Edit `DMOS-R0-004: Implement DigitalMarketingBoundaryPolicyStore`

Add acceptance criteria:

- Initial rule table includes concrete resources and effects for contacts, audiences, campaigns, budgets, content publishing, connectors, exports, and default-deny.
- Pack contract tests verify `.requiresConsent(true)` for sensitive contact/audience reads and `.requiresAudit(true)` for sensitive reads/writes.
- Unknown action on known resource is denied.

### Edit `DMOS-R0-005: Implement DigitalMarketingComplianceRulePack`

Add initial rule set constants:

- `DM_MARKETING_INTEGRITY`
- `DM_CONSENT_LIFECYCLE`
- `DM_AUDIT_TRACEABILITY`
- `DM_CAMPAIGN_PREFLIGHT`
- `DM_CLAIMS_DISCLOSURES`
- `DM_EMAIL_COMPLIANCE`
- `DM_CONNECTOR_EXECUTION_SAFETY`

Add tests:

- Product startup registers rule sets.
- Kernel-only startup does not register rule sets.
- Missing compliance plugin behavior is deterministic.

### Edit `DMOS-R0-006: Wire Product Validation and CI Gates`

Add CI gates:

- reference consumer hygiene scan
- plugin binding startup test
- bridge contract integration test
- no bridge method without `BridgeContext`
- product docs do not instruct copying PHR/Finance packs

### Edit `DMOS-R0-007: Define Canonical IDs, Context, and Correlation Standards`

Add full-flow test:

- `API request → security context → command → workflow execution → bridge adapter → audit event` preserves `tenantId`, `principalId`, `correlationId`, and `idempotencyKey`.

### Edit `DMOS-F1-014: Implement Budget Recommendation and Guardrail Model`

Rename owner:

- From: `Product Engineering + Finance/Risk`
- To: `Product Engineering + Budget/Risk` or `Product Engineering + Commercial Risk`

Reason: avoid confusion with the Finance reference consumer product.

### Edit `DMOS-F2-006: Implement Connector Runtime Base`

Clarify scope:

- This is a **product-owned external connector runtime**, not a platform plugin.
- It must use context propagated from product workflows and must call platform bridge adapters only through `DigitalMarketingKernelBridgeAdapter` or verified public bridge ports.
- It must not directly import kernel implementation classes.

Add tests:

- No connector write can execute without `tenantId`, `principalId`, `correlationId`, and write `idempotencyKey`.
- Secrets/tokens are redacted through both connector and bridge logs.

---

## 7. Final Readiness Decision

| Area | Decision |
|---|---|---:|
| Product scope and MVP sequence | Strong | ✅ Ready |
| Domain pack model | Strong | ✅ Ready with minor concrete rule-table addition |
| Compliance rule pack model | Strong | ✅ Ready with named rule set constants |
| Gradle validation tasks | Strong | ✅ Ready |
| Plugin binding | Needs explicit task | ⚠️ Amend before implementation |
| Kernel bridge adapter | Needs explicit task | ⚠️ Amend before implementation |
| Bridge integration tests | Needs dedicated harness | ⚠️ Amend before implementation |
| Reference consumer hygiene | Missing | ⚠️ Amend before implementation |
| Context propagation | Good but needs full-flow test | ⚠️ Amend before implementation |
| CI/quality gates | Strong | ✅ Ready after adding the above gates |

## 8. Bottom Line

The implementation plan is **architecturally sound and mostly guide-compliant**, but it should not be treated as final until the P0 amendments above are added. The most important corrections are:

1. Make product plugin binding a first-class R0 task.
2. Make the DMOS kernel bridge adapter a first-class R0 task.
3. Move bridge integration tests into the early readiness gate.
4. Add reference-consumer hygiene so PHR/Finance remain examples, not defaults.
5. Add full-flow `BridgeContext` propagation tests.

Once these are added, the plan will align cleanly with the Product Development Guide and will be ready for implementation sequencing.
