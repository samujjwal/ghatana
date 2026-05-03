# DMOS End-to-End Product Review
**Date**: 2026-05-02  
**Reviewer**: AI-assisted architectural review  
**Scope**: Full DMOS product across all 8 modules — kernel bridge, application services, API layer, domain, UI, and integration tests  
**Template**: `new-product-prompt.md`

---

## 1. Executive Summary

DMOS (Digital Marketing Operating System) is architecturally sound in its core orchestration layer. The kernel bridge pattern is implemented correctly, all four platform kernel capabilities are properly wired, and the campaign/workspace domain is production-grade in design. However, **the product cannot be deployed today** because every persistence port (repository interfaces) and every external-connector port (Google Ads API clients) lack production adapter implementations — only in-memory test fakes exist. This is the single most critical gap to address before any live deployment.

| Severity | Count | Category |
|---|---|---|
| P0 — Blocks deployment | 2 | No production repository adapters; no external connector adapters |
| P1 — High risk | 3 | Auth tokens in localStorage (OWASP A2); no rate limiting at API layer; no Testcontainers integration tests |
| P2 — Important | 4 | No metrics/tracing (SLF4J only); no backend feature-flag kernel integration; dashboard widget shells incomplete; `RiskPlugin` bound in config only |
| P3 — Medium | 2 | Intentional editorial placeholder constants (acceptable domain behavior); `WorkflowStatusWidget` / `GrowthGoalWidget` are stub UIs |
| Kernel enhancements | 5 | Rate-limit plugin; notification plugin; metrics/telemetry plugin; circuit-breaker capability; backend feature-flag port |

---

## 2. Module Inventory

| Module | Files (main) | Status | Notes |
|---|---|---|---|
| `dm-core-contracts` | 7 Java | ✅ Production-grade | `DmOperationContext`, typed IDs, mapper — fully typed, immutable, tested |
| `dm-domain-packs` | 4 Java | ✅ Production-grade | Compliance rules, boundary policy, plugin bindings, Gradle validation tasks wired |
| `dm-kernel-bridge` | 2 Java (interface + impl) | ✅ Production-grade | Correct bridge pattern; all 4 kernel capabilities used |
| `dm-domain` | ~130 Java | ✅ Production-grade | Immutable `@Value`/`@Builder` records; campaign, workspace, content slices fully designed |
| `dm-application` | ~50 Java | ⚠️ Incomplete | Services correct; repository **ports exist but no production adapters**; Google Ads **clients are ports only** |
| `dm-api` | 18 Java servlets | ✅ Production-grade | Proper ActiveJ routing, tenant header enforcement, error mapping, idempotency key enforcement |
| `dm-integration-tests` | 4 Java ITs | ⚠️ In-memory only | No Testcontainers; all tests use in-memory fakes; no real-infra validation |
| `ui/src` | 34 TS/TSX files | ⚠️ Partial | Auth/approval/AI action pages exist; dashboard widgets are shells; auth token security gap |

---

## 3. Kernel Bridge Assessment

### 3.1 What Is Correctly Wired

`DigitalMarketingKernelAdapterImpl extends AbstractKernelBridge implements DigitalMarketingKernelAdapter`

All application services depend only on the product-local `DigitalMarketingKernelAdapter` interface — no direct imports of kernel ports appear in the application layer. This is the correct dependency direction.

| Kernel Capability | Interface Used | Where |
|---|---|---|
| Authorization | `BridgeAuthorizationService` | `isAuthorized()` in all services |
| Consent | `ConsentPlugin` | `verifyConsent()` used in email, landing-page |
| Human approval | `HumanApprovalPlugin` | `requestApproval()` used in campaign launch |
| Audit trail | `BridgeAuditEmitter` | `recordAudit()` called after every state-change |
| Health | `BridgeHealthIndicator` | `start()`/`stop()` lifecycle |
| Compliance | `CompliancePlugin` | `evaluate()` with `DM_CAMPAIGN_PREFLIGHT` rule set |
| Boundary policy | `BoundaryPolicyStore` | `DigitalMarketingBoundaryPolicyStore` |

`DmOperationContext.toBridgeContext()` correctly maps product context to `BridgeContext`. `buildAuditAttributes()` enriches every audit call with `tenantId`, `workspaceId`, `correlationId`, and `actor`.

### 3.2 Kernel Capabilities NOT Used (Kernel Enhancement Opportunities)

The following capabilities are absent from the product and should either be added to the kernel or adopted if they already exist:

| Missing Capability | Impact | Proposed Kernel Port |
|---|---|---|
| **Rate limiting** | API endpoints have no per-tenant throttling; budget endpoints and intake forms are vulnerable to abuse | `RateLimitPlugin` / `KernelRateLimiter` port in `DigitalMarketingKernelAdapter` |
| **Notifications / alerting** | Budget thresholds, approval expiry, and compliance violations have no user notification path; downstream consumers cannot receive events | `NotificationPlugin` — emit `DmEvent` to `KernelEventBus` for budget alerts, approval reminders |
| **Metrics / telemetry** | `DmosObservabilityBaseline` provides structured SLF4J logging and MDC, but zero counters, gauges, or histograms; no Prometheus `/metrics` endpoint | `MetricCollectorPort` in kernel bridge; DMOS should register business KPIs (campaign launches/min, approval latency, compliance violations) |
| **Circuit breaker** | Google Ads / external connectors have no circuit-break or timeout backstop at the kernel layer | `CircuitBreakerPlugin` / resilience capability in `AbstractKernelBridge`; connectors must declare their own timeout contracts |
| **Backend feature flags** | Frontend has 5 typed feature flags (`VITE_FEATURE_*`); backend has env-var booleans in `DmosProductConfig` but no dynamic, auditable flag capability | `FeatureFlagPlugin` — backend should read flags through kernel, not static `boolEnv()` constants, to allow runtime toggle without redeploy |

### 3.3 `RiskPlugin` Binding Gap

`docs/platform-alignment.md` explicitly notes: *"Risk plugin: Bound in manifest/config only — direct runtime integration pending."*  
`DigitalMarketingKernelAdapterImpl` does not invoke `RiskManagementPlugin` in any current flow. For campaign launch and budget-recommendation flows this is a P2 gap.

---

## 4. Critical Gaps (P0)

### 4.1 Missing Production Repository Adapters

**Every repository interface has zero production implementations.** Grep across all non-test Java source finds no class `implements CampaignRepository`, `implements WorkspaceRepository`, or any other port implementation outside of `dm-integration-tests`.

**Affected ports:**
- `CampaignRepository` (`dm-application/campaign/`)
- `WorkspaceRepository` (`dm-application/workspace/`)
- `DmRecommendationRepository` (`dm-application/recommendation/`)
- `DmCommandRepository` (`dm-application/command/`)
- Any additional repository interfaces in the ~50 application service packages

**Required action:** Implement database adapters in a new `dm-persistence` module (per `platform:java:database` conventions) using the established `TenantContext` pattern. Each adapter must be backed by a Testcontainers integration test.

### 4.2 Missing External Connector Adapters

`DmGoogleAdsOAuthClient`, `DmGoogleAdsCampaignApiClient`, and `DmGoogleAdsPerformanceApiClient` are interfaces (ports) only. No production HTTP adapter implementations exist in any module.

Per repo rule §31 (Stub Adapters Are Not Production Code), any port for an external system must have:
1. Typed request/response records with Jackson `@JsonProperty`
2. Explicit HTTP status handling
3. A Testcontainers (or WireMock) integration test
4. Declared timeouts and retries
5. At least one metric + one structured log per call

**Required action:** Implement `HttpDmGoogleAdsOAuthClientAdapter`, `HttpDmGoogleAdsCampaignApiClientAdapter`, and `HttpDmGoogleAdsPerformanceApiClientAdapter` in a `dm-connector-google-ads` module, conforming to §31.

---

## 5. High-Risk Gaps (P1)

### 5.1 Auth Token Storage in localStorage — OWASP A2 / A7

`ui/src/lib/http-client.ts` stores `dmos_auth_token`, `dmos_workspace_id`, and `dmos_tenant_id` in `localStorage`. This exposes all three to any XSS payload running in the same origin.

```ts
// CURRENT — vulnerable
export function setAuthToken(token: string): void {
  localStorage.setItem(AUTH_TOKEN_KEY, token);
}
```

**Required action:** Tokens must be stored in memory only (`useRef` or in-memory `useState`) and refreshed via a `httpOnly`/`Secure` cookie or a server-side session. If a cookie-based flow is not available in the MVP, the minimum acceptable mitigation is memory-only storage with no persistence across page reloads, combined with a server-side short-lived token pattern. `workspaceId` and `tenantId` are not secrets and may stay in `localStorage`.

### 5.2 No Rate Limiting at API Layer

All 18 servlets (`DmosCampaignServlet`, `DmosIntakeQuestionnaireServlet`, `DmosProposalServlet`, etc.) perform no per-tenant request-rate enforcement. The intake questionnaire and public-intake servlets in particular accept unauthenticated or lightly authenticated requests.

**Required action:** Apply a rate-limit guard at the `RoutingServlet` entry point, delegating to either the proposed `RateLimitPlugin` (kernel enhancement) or a product-local `DmRateLimiter` backed by `platform:java:http` middleware until the kernel capability is available. Target: max 60 requests/minute per tenant per endpoint class.

### 5.3 No Real-Infrastructure Integration Tests

All four integration tests in `dm-integration-tests` use in-memory fakes (`InMemoryCampaignRepository`, `AllowAllKernelAdapter`, `AlwaysCompliantPlugin`). `CampaignLoadPerfIT` creates 100 campaigns against an in-memory map — this validates application logic but cannot surface persistence performance, network failures, or schema drift.

Per repo rule §31, adapters for external systems must include a Testcontainers integration test.

**Required action:** Once P0 adapters are implemented, add:
- `CampaignRepositoryIT` using Testcontainers Postgres/ClickHouse (matching production store)
- `DmGoogleAdsConnectorIT` using WireMock to simulate Google Ads API responses
- A kernel-bridge integration test that validates `ConsentPlugin` and `HumanApprovalPlugin` are properly resolved at startup

---

## 6. Important Gaps (P2)

### 6.1 No Metrics / Business KPIs

`DmosObservabilityBaseline` provides structured MDC logging only. There is no Prometheus metric registration, no latency histogram, no counter for campaign launches, no gauge for pending approvals. The monitoring stack (Grafana/Prometheus per §19) cannot display any DMOS business signal.

**Kernel enhancement:** Add `MetricCollectorPort` to `DigitalMarketingKernelAdapter` and register these DMOS business KPIs at startup:
- `dmos_campaign_launched_total{tenant, workspace}` — counter
- `dmos_approval_pending_gauge{tenant, workspace}` — gauge  
- `dmos_compliance_violation_total{rule_set}` — counter
- `dmos_api_request_duration_seconds{servlet, method, status}` — histogram

### 6.2 No Backend Feature-Flag Kernel Integration

`DmosProductConfig` reads flags via `boolEnv(...)` — a static JVM constant evaluated at startup. Runtime toggling (e.g., disabling AI features for a specific tenant without restart) is not possible.

**Kernel enhancement:** Expose a `FeatureFlagPlugin` in `AbstractKernelBridge`. DMOS should request `AI_FEATURES_ENABLED`, `GOOGLE_ADS_CONNECTOR_ENABLED`, and `KILL_SWITCH_ENABLED` through this port, allowing operations to toggle flags without a rolling restart.

### 6.3 `RiskManagementPlugin` Not Integrated

Documented in `docs/platform-alignment.md` as "pending execution-phase work." The campaign launch flow and budget recommendation flow both benefit from risk scoring before commitment. Until integrated, the compliance gate is the only backstop.

**Required action:** Add `evaluateRisk(ctx, entityId, attributes)` to `DigitalMarketingKernelAdapter`, implement in `DigitalMarketingKernelAdapterImpl` using `RiskManagementPlugin`, and invoke in `CampaignServiceImpl.launchCampaign()` and `BudgetRecommendationServiceImpl`.

### 6.4 Dashboard Widget Shells

`GrowthGoalWidget` is flagged `EXPERIMENTAL` and shows "Metrics loading…" with no actual data. `WorkflowStatusWidget` hardcodes "No active workflows". `RiskComplianceWidget` hardcodes "No active violations" (no live query).

These are acceptable behind feature flags but must not be shipped to production without real data bindings or an explicit `NOT_READY` status. Current state is borderline §35.3 (stub implementations in production-critical paths).

---

## 7. End-to-End Use Case Tracing

### 7.1 Campaign Creation → Launch → Pause (Happy Path)

```
POST /v1/workspaces/{wsId}/campaigns
  → DmosCampaignServlet.handleCreate()
    → DmOperationContext built (X-Tenant-ID, X-Principal-ID, X-Correlation-ID required)
    → CampaignServiceImpl.createCampaign()
      → kernelAdapter.isAuthorized(ctx, "campaigns/*", "write") ✅
      → CampaignRepository.save(new DRAFT campaign) ← PORT ONLY, NO ADAPTER ❌
      → kernelAdapter.recordAudit(...) ✅
      → HTTP 201 + Campaign JSON

POST /v1/workspaces/{wsId}/campaigns/{id}/launch
  → DmosCampaignServlet.handleLaunch()
    → CampaignServiceImpl.launchCampaign()
      → kernelAdapter.isAuthorized(ctx, "campaigns/*", "write") ✅
      → CompliancePlugin.evaluate("DM_CAMPAIGN_PREFLIGHT", ...) ✅
      → kernelAdapter.requestApproval(...) ✅
      → CampaignRepository.save(LAUNCHED) ← PORT ONLY ❌
      → kernelAdapter.recordAudit(...) ✅
```

**Gap**: Step `CampaignRepository.save()` has no real implementation — the flow is architecturally correct but not deployable.

### 7.2 Email Draft Generation (Consent-Aware)

```
POST /v1/workspaces/{wsId}/email-follow-up
  → DmosEmailFollowUpServlet.handleGenerateDraft()
    → EmailFollowUpDraftServiceImpl.generateEmailDraft()
      → kernelAdapter.isAuthorized(ctx, "content", "write") ✅
      → kernelAdapter.verifyConsent(ctx, contactId, "marketing-email") ✅
      → suppressionService.isSuppressed(ctx, contactId) ✅
      → ContentItemService.createVersion(ctx, cmd) ← no persistence adapter ❌
      → kernelAdapter.recordAudit(...) ✅
```

Consent check + suppression check before draft creation is correct. CAN-SPAM `UNSUBSCRIBE_PLACEHOLDER` is injected as a mandatory editorial marker — this is domain-correct behavior.

### 7.3 Approval Decision Flow (UI)

```
UI: ApprovalQueuePage → useApprovalQueue(workspaceId, tenantId)
  → api/approvals.ts listPendingApprovals()
    → GET /v1/workspaces/{wsId}/approvals/pending/{subjectId}
      → DmosApprovalServlet → ApprovalService ← no persistence adapter ❌

ApprovalDetailPage → decideApproval()
  → POST /v1/workspaces/{wsId}/approvals/{id}/decide
    → DmosApprovalServlet → ApprovalService.decide() ← no persistence adapter ❌
```

---

## 8. Security Posture

| Check | Status | Notes |
|---|---|---|
| Tenant header enforced | ✅ | `X-Tenant-ID` required, 400 on missing |
| Idempotency key enforced for mutations | ✅ | `X-Idempotency-Key` required |
| Correlation ID propagated | ✅ | `X-Correlation-ID` through MDC |
| Authorization checked before every state change | ✅ | `kernelAdapter.isAuthorized()` first in every service |
| Consent checked before contact data use | ✅ | Email, landing page, lead scoring all check consent |
| Suppression list checked before email generation | ✅ | `SuppressionService.isSuppressed()` |
| Compliance preflight before campaign launch | ✅ | `DM_CAMPAIGN_PREFLIGHT` rule set via `CompliancePlugin` |
| Default-deny boundary policy | ✅ | `DM-BP-999` catch-all deny rule registered |
| Reference consumer hygiene (no PHR-/FIN- tokens) | ✅ | Gradle validation task `validateReferenceConsumerHygiene` |
| Auth token storage — XSS exposure | ❌ P1 | `localStorage` for JWT — must move to memory-only or httpOnly cookie |
| Rate limiting | ❌ P1 | No rate limiting on any endpoint |
| Input validation at API layer | ✅ | Null/blank checks in all servlet handlers |
| SQL injection / parameterized queries | N/A | No production DB adapter yet |
| Secrets in env vars | ✅ | `DmosProductConfig` uses `System.getenv()` with defaults; no hardcoded secrets found |

---

## 9. Test Coverage Assessment

| Layer | Coverage Mechanism | Status |
|---|---|---|
| Domain objects | JUnit unit tests in `dm-domain` | ✅ JaCoCo gates pass (≥0.93 lines, ≥0.85 branches) |
| Application services | JUnit + Mockito in `dm-application` | ✅ ~50 test classes match ~50 service classes |
| API servlets | JUnit in `dm-api` | ✅ 18 servlet tests (one per servlet) |
| Contracts | JUnit in `dm-core-contracts` | ✅ 7 tests |
| Domain packs | JUnit in `dm-domain-packs` | ✅ 4 tests + Gradle validation tasks |
| Kernel bridge | JUnit in `dm-kernel-bridge` | ✅ 1 test (DigitalMarketingKernelAdapterImplTest) |
| Integration — in-memory | EventloopTestBase ITs | ⚠️ 4 tests, in-memory only |
| Integration — real infra | Testcontainers | ❌ None |
| UI unit tests | Vitest + Testing Library | ✅ 4 page tests + route-contracts test |
| E2E browser | Playwright | ❌ None |

**JaCoCo gate fix (prior session):** `dm-domain` lines gate raised from 0.92→0.93, branches from 0.84→0.85 by adding targeted tests to `DmPublicApiKeyTest`, `DmPreflightChecklistTest`, `DmEmailFollowUpTest`, `DmKillSwitchTest`.

---

## 10. Intentional Placeholder Constants (Acceptable Domain Behavior)

The following constants appear in production source but are intentionally domain-correct editorial markers injected into AI-generated content drafts. They require human completion before publication — they are NOT production shortcuts:

| Constant | Class | Purpose |
|---|---|---|
| `PROOF_WARNING` | `LandingPageGeneratorServiceImpl` | Flags AI-generated claims that lack evidence; prevents hallucinated testimonials |
| `DISCLOSURE_PLACEHOLDER` | `LandingPageGeneratorServiceImpl` | Mandatory legal disclosure slot requiring human input before publishing |
| `[FORM PLACEHOLDER]` | `LandingPageGeneratorServiceImpl` (inline) | Form tracking configuration required before publishing |
| `UNSUBSCRIBE_PLACEHOLDER` | `EmailFollowUpDraftServiceImpl` | CAN-SPAM / CASL mandatory unsubscribe link slot |
| `UNVERIFIED_CLAIM_WARNING` | `EmailFollowUpDraftServiceImpl` | Claim review required before email activation |

These are classified **P3 / Medium** with no action required beyond ensuring content validation (approval gate) correctly rejects drafts that still contain these markers before activation.

---

## 11. Kernel Enhancement Proposals

The following capabilities should be raised as kernel enhancement requests or tracked as `platform:java:kernel` additions:

### KE-01: `RateLimitPlugin`
**Rationale:** No product should implement its own rate-limit logic. A shared `RateLimitPlugin` in the kernel bridge gives consistent per-tenant throttling across all products.  
**Suggested interface addition to `DigitalMarketingKernelAdapter`:**
```java
Promise<Boolean> checkRateLimit(DmOperationContext ctx, String operation, int maxPerMinute);
```

### KE-02: `NotificationPlugin`
**Rationale:** Budget threshold alerts, approval expiry reminders, and compliance violation notifications have no delivery channel today.  
**Suggested approach:** Kernel exposes `NotificationPlugin.notify(ctx, recipientId, template, attributes)` backed by the `KernelEventBus`.

### KE-03: `MetricCollectorPort` in `AbstractKernelBridge`
**Rationale:** Each product needs to register business KPIs. Centralizing metric registration in the bridge ensures consistent labeling (tenant, workspace, product) and prevents duplicate metric names across products.  
**Suggested interface:**
```java
void registerCounter(String name, String description, String... labelNames);
void incrementCounter(String name, Map<String, String> labels);
```

### KE-04: `CircuitBreakerCapability` for External Adapters
**Rationale:** Google Ads and other third-party connectors should be wrapped in a circuit breaker. The kernel bridge is the right place to declare this contract so all products benefit.  
**Suggested approach:** `AbstractKernelBridge` exposes `executeWithCircuitBreaker(Supplier<Promise<T>>, String circuitName)`.

### KE-05: `FeatureFlagPlugin`
**Rationale:** Static `boolEnv()` constants in `DmosProductConfig` are evaluated once at JVM startup. Runtime toggling per tenant or per region requires a dynamic flag store.  
**Suggested interface addition to `DigitalMarketingKernelAdapter`:**
```java
Promise<Boolean> isFeatureEnabled(DmOperationContext ctx, String flagKey);
```

---

## 12. Prioritized Action Plan

### Phase 1 — Deployment Blocker (P0) — Weeks 1–3

| Task | Owner | Module |
|---|---|---|
| Implement `PostgresCampaignRepository` (Testcontainers IT) | Backend | new `dm-persistence` |
| Implement `PostgresWorkspaceRepository` (Testcontainers IT) | Backend | `dm-persistence` |
| Implement remaining repository adapters (Recommendation, Command, etc.) | Backend | `dm-persistence` |
| Implement `HttpDmGoogleAdsOAuthClientAdapter` + WireMock IT | Backend | new `dm-connector-google-ads` |
| Implement `HttpDmGoogleAdsCampaignApiClientAdapter` + WireMock IT | Backend | `dm-connector-google-ads` |
| Implement `HttpDmGoogleAdsPerformanceApiClientAdapter` + WireMock IT | Backend | `dm-connector-google-ads` |

### Phase 2 — Security (P1) — Week 4

| Task | Owner | Module |
|---|---|---|
| Move auth token from `localStorage` to memory-only + `httpOnly` cookie | Frontend | `ui/src/lib/http-client.ts`, `AuthContext.tsx` |
| Add rate-limit guard at servlet routing level (product-local until KE-01) | Backend | `dm-api` |
| Add Testcontainers-backed kernel bridge startup wiring test | Backend | `dm-integration-tests` |

### Phase 3 — Observability and Completeness (P2) — Weeks 5–6

| Task | Owner | Module |
|---|---|---|
| Register DMOS business KPIs via `MetricCollectorPort` (propose KE-03 to kernel team) | Backend | `dm-application` |
| Integrate `RiskManagementPlugin` in campaign launch and budget flows | Backend | `dm-application`, `dm-kernel-bridge` |
| Implement real data binding for `WorkflowStatusWidget` and `RiskComplianceWidget` | Frontend | `ui/src/components/dashboard/` |
| Migrate `DmosProductConfig` flags to `FeatureFlagPlugin` when KE-05 is available | Backend | `dm-application` |

### Phase 4 — Kernel Enhancements (KE-01–05) — Weeks 6–10

Raise kernel enhancement tickets for KE-01 through KE-05. Work with platform kernel team to land:
- `RateLimitPlugin` (KE-01) — affects DM + all other products
- `NotificationPlugin` (KE-02)  
- `MetricCollectorPort` (KE-03)
- `CircuitBreakerCapability` (KE-04)
- `FeatureFlagPlugin` (KE-05)

---

## 13. Strengths to Preserve

1. **Kernel bridge pattern is exemplary.** Correct bridge inheritance, full lifecycle management, `requireStarted()` guards, audit enrichment, and `toBridgeContext()` mapping — this is a reference implementation for other products.
2. **Domain model is well-structured.** ~130 immutable value objects across a rich domain, no anemic domain model anti-pattern.
3. **API layer is thorough.** 18 servlets each with proper tenant header enforcement, idempotency, error mapping, and matching unit tests.
4. **Compliance-first design.** `DM_CAMPAIGN_PREFLIGHT` rule pack, default-deny boundary policy (`DM-BP-999`), reference consumer hygiene Gradle gate, consent verification before every contact-touching operation.
5. **No production shortcuts in content generation.** Intentional editorial placeholder constants correctly prevent AI-hallucinated testimonials and missing legal disclosures from reaching activation.
6. **Observability baseline is documented and adopted.** `DmosObservabilityBaseline` with MDC correlation IDs is used consistently across service classes.
7. **TypeScript type safety throughout UI.** No `any` types found; all props are typed; context uses typed generics; API clients are fully typed.

---

## 14. Conclusion

DMOS has the architectural bones of a production system. The kernel bridge integration is the most well-executed aspect of the product and should serve as a reference for all other products in the monorepo. The compliance-first and consent-first design choices are correct and valuable.

The product cannot be deployed in its current state because all persistence and external-system ports lack production adapter implementations. These P0 gaps must be addressed before any other work. Once resolved, the P1 security gap (localStorage tokens) and the absence of real-infra integration tests are the next mandatory fixes.

The five kernel enhancement opportunities (KE-01 through KE-05) represent genuine platform-level gaps that the DMOS product has surfaced first — resolving them in the kernel will benefit every product in the monorepo.
