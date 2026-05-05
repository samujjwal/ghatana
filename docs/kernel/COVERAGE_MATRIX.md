# Root-Level Coverage Matrix

> **Status**: Living document — update when modules are added or tests change  
> **Purpose**: Track test coverage by module, feature, flow, and test tier  
> **Owner**: Platform Engineering
> **Ownership source of truth**: [KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md](./KERNEL_PRODUCT_RESPONSIBILITY_MATRIX.md)

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Covered — tests exist and are wired to CI |
| ⚠️ | Partial — some cases covered, gaps remain |
| ❌ | Not covered — no tests or not in CI |
| N/A | Not applicable for this module/tier |

---

## Coverage by Module

### Platform Java

| Module | Unit (tier-0) | Contract (tier-0) | Integration (tier-1/2) | Infra (tier-3) | Notes |
|--------|:---:|:---:|:---:|:---:|-------|
| `platform/java/core` | ✅ | N/A | ⚠️ | N/A | |
| `platform/java/database` | ✅ | N/A | ✅ | ✅ | N+1 detection, retry-on-transient |
| `platform/java/http` | ✅ | ✅ | ✅ | N/A | TenantExtractor, FilterChain |
| `platform/java/observability` | ✅ | N/A | ✅ | N/A | Correlation ID propagation |
| `platform/java/security` | ✅ | ✅ | ✅ | N/A | Adversarial/tenant/policy |
| `platform/java/messaging` | ✅ | N/A | ✅ | ✅ | Throughput, ordering, backpressure |
| `platform/java/cache` | ✅ | N/A | ✅ | ⚠️ | Memory growth baseline |
| `platform/java/audit` | ✅ | N/A | ✅ | N/A | |
| `platform/java/agent-core` | ✅ | N/A | ⚠️ | N/A | |
| `platform/java/ai-integration` | ✅ | ✅ | ⚠️ | N/A | |
| `platform/contracts` | ✅ | ✅ | N/A | N/A | Auth, AEP, DC, YAPPC, AI contracts |

### Platform TypeScript

| Module | Unit (tier-0) | Browser (tier-4) | A11y (tier-4) | Perf (tier-4) | Notes |
|--------|:---:|:---:|:---:|:---:|-------|
| `platform/typescript/design-system` | ✅ | ⚠️ | ⚠️ | ❌ | Storybook smoke only |
| `platform/typescript/api-helpers` | ✅ | N/A | N/A | N/A | |
| `platform/typescript/tokens` | ✅ | N/A | N/A | N/A | |
| `platform/typescript/theme` | ✅ | N/A | N/A | N/A | |
| `platform/typescript/realtime` | ✅ | ⚠️ | N/A | N/A | |
| `platform/typescript/utils` | ✅ | N/A | N/A | N/A | |

### Platform Kernel

| Module | Unit (tier-0) | Integration (tier-1/2) | Notes |
|--------|:---:|:---:|-------|
| `kernel-core` | ✅ | ✅ | Concurrent registration, fanout, failure isolation |
| `kernel-persistence` | ✅ | ✅ | Migration, retention |
| `plugin-audit-trail` | ✅ | ✅ | |
| `plugin-ledger` | ✅ | ⚠️ | |
| `plugin-compliance` | ✅ | ⚠️ | |
| `plugin-consent` | ✅ | ✅ | Persistence migration + retention |

### Platform Plugins

| Plugin | Unit (tier-0) | Contract (tier-0) | Integration (tier-1/2) | Notes |
|--------|:---:|:---:|:---:|-------|
| `plugin-audit-trail` | ✅ | ✅ | ✅ | |
| `plugin-ledger` | ✅ | ⚠️ | ⚠️ | |
| `plugin-compliance` | ✅ | ⚠️ | ⚠️ | |
| `plugin-consent` | ✅ | ✅ | ✅ | |

### Shared Services

| Service | Unit (tier-0) | Contract (tier-0) | Integration (tier-1/2) | Load (tier-4) | Notes |
|---------|:---:|:---:|:---:|:---:|-------|
| `auth-gateway` | ✅ | ✅ | ✅ | ✅ | k6: OAuth flows + auth-gateway endpoints |
| `ai-inference-service` | ✅ | ✅ | ⚠️ | ❌ | |
| `incident-service` | ✅ | N/A | ⚠️ | ❌ | |

### Products

| Product | Unit (tier-0) | Contract (tier-0) | Integration (tier-1/2) | E2E (tier-4) | Load (tier-4) | Notes |
|---------|:---:|:---:|:---:|:---:|:---:|-------|
| `products/aep` | ✅ | ✅ | ✅ | ⚠️ | ❌ | State machine, queue, idempotency |
| `products/data-cloud` | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | Query semantics ⚠️ |
| `products/yappc` | ✅ | ✅ | ✅ | ⚠️ | ❌ | AI eval tier-4 partial |
| `products/audio-video` | ✅ | ⚠️ | ✅ | ❌ | ❌ | Media privacy/security |
| `products/flashit` | ✅ | ⚠️ | ⚠️ | ❌ | ❌ | |
| `products/tutorputor` | ✅ | ⚠️ | ⚠️ | ❌ | ❌ | |
| `products/dcmaar` | ✅ | ⚠️ | ⚠️ | ❌ | ❌ | |
| `products/phr` | ✅ | ⚠️ | ⚠️ | ❌ | ❌ | |

---

## Coverage by Feature Area

| Feature Area | Tested | Test Type | Location |
|---|:---:|---|---|
| Tenant isolation / extraction | ✅ | Unit + Contract + Load | `auth-gateway`, `platform/java/http` |
| JWT issuance + validation | ✅ | Unit + Contract + Load | `auth-gateway/k6-tests` |
| Token refresh + blocklist | ✅ | Unit + Integration + Load | `auth-gateway` |
| Cross-product token exchange | ✅ | Unit + Contract | `auth-gateway` |
| Rate limiting | ✅ | Unit + Load | `auth-gateway` |
| Audit logging | ✅ | Unit + Integration | `auth-gateway/audit` |
| Correlation ID propagation | ✅ | Integration | `platform/java/observability` |
| Event envelope contract | ✅ | Contract | `platform/contracts` |
| OpenAPI contract: auth-gateway | ✅ | Contract | `platform/contracts` |
| OpenAPI contract: AEP | ✅ | Contract | `platform/contracts` |
| OpenAPI contract: data-cloud | ✅ | Contract | `platform/contracts` |
| OpenAPI contract: YAPPC | ✅ | Contract | `platform/contracts` |
| OpenAPI contract: ai-inference | ✅ | Contract | `platform/contracts` |
| OpenAPI contract: ai-registry | ✅ | Contract | `platform/contracts` |
| DB migration + retention | ✅ | Integration | `platform-plugins`, `platform/java/database` |
| N+1 query detection | ✅ | Integration | `platform/java/database` |
| Cache memory growth | ✅ | Integration | `platform/java/cache` |
| Messaging throughput / ordering | ✅ | Integration | `platform/java/messaging` |
| Plugin contract isolation | ✅ | Contract | `platform-plugins` |
| AEP orchestration state machine | ✅ | Integration | `products/aep` |
| AEP queue concurrency + idempotency | ✅ | Integration | `products/aep` |
| Data-cloud query semantics | ⚠️ | Unit (partial) | `products/data-cloud` |
| Audio-video media privacy | ✅ | Unit + Integration | `products/audio-video` |
| GDPR / CCPA compliance flows | ✅ | Integration | `products/aep`, `products/data-cloud` |
| Browser E2E (YAPPC frontend) | ⚠️ | E2E (Playwright) | CI tier-4 partial |
| Accessibility (a11y) | ⚠️ | Lighthouse / axe | CI tier-4 partial |
| Performance budgets | ⚠️ | Lighthouse | CI tier-4 partial |
| AI eval / model governance | ⚠️ | Eval suite | CI tier-4 partial |
| Security SAST | ✅ | SpotBugs | CI tier-4 |

---

## Coverage by Flow

| Flow | Covered | Tiers | Notes |
|---|:---:|---|---|
| User login → JWT → tenant extraction | ✅ | 0,1,4 | End-to-end via release-gate smoke |
| Token refresh → blocklist check | ✅ | 0,1,4 | Load test validates blocklist |
| Cross-product token exchange | ✅ | 0,1 | Contract + unit |
| Event ingestion → pattern detection | ✅ | 0,1,2 | AEP unit + integration |
| Pipeline create → execute → status | ✅ | 0,1,2 | Data-cloud integration |
| GDPR access request flow | ✅ | 0,1 | AEP + data-cloud |
| GDPR erasure request flow | ✅ | 0,1 | AEP + data-cloud |
| Agent register → execute → memory | ✅ | 0,1 | AEP integration |
| HITL review → approve/reject | ✅ | 0,1 | AEP integration |
| Design create → generate → artifact | ⚠️ | 0 | YAPPC unit only |
| Audio transcription → storage → retention | ✅ | 0,1 | audio-video |
| Platform plugin lifecycle | ✅ | 0,1 | platform-plugins |
| Release smoke (YAPPC + auth) | ✅ | 4 | release-gate.yml |

---

## Coverage by Test Tier

| Tier | Description | Status | Workflow |
|---|---|:---:|---|
| tier-0 | Unit + pure contract | ✅ Wired | `test-tier-classification.yml` |
| tier-1 | Module integration (in-memory) | ✅ Wired | `test-tier-classification.yml` |
| tier-2 | Network integration (localhost) | ✅ Wired | `test-tier-classification.yml` |
| tier-3 | Testcontainers / infra-backed | ✅ Wired | `test-tier-classification.yml` |
| tier-4 | Browser / perf / load / security / AI eval | ⚠️ Partial | `test-tier-classification.yml`, `release-gate.yml` |

---

## Required Negative / Failure / Security / Privacy / Observability Tests

| Test Type | Required Coverage | Status | Location |
|---|---|:---:|---|
| Invalid JWT rejected (401) | All products | ✅ | `auth-gateway` unit + k6 |
| Expired JWT rejected (401) | All products | ✅ | `auth-gateway` unit |
| Missing tenant header → 403 (strict) | Platform HTTP | ✅ | `TenantExtractionFilter` test |
| Cross-tenant data leakage = 0 | Auth gateway | ✅ | `load-test-tenant-boundary.js` |
| Rate-limit burst → 429 | Auth gateway | ✅ | `load-test-auth-gateway.js` |
| Revoked token rejected on refresh | Auth gateway | ✅ | Unit + k6 blocklist test |
| PII not logged in plaintext | Platform observability | ✅ | `platform/java/observability` |
| GDPR erasure removes all data | Data-cloud, AEP | ✅ | Integration tests |
| Audit event emitted on login | Auth gateway | ✅ | `AuditLoggerPersistenceTest` |
| Correlation ID propagated cross-service | Platform observability | ✅ | Integration tests |
| Plugin isolation: crash doesn't leak | Platform-plugins | ✅ | Contract tests |
| Media retention policy enforced | Audio-video | ✅ | Unit + integration |
| Consent revocation stops processing | Audio-video | ✅ | Integration |
| Agent HITL escalation path | AEP | ✅ | Integration |
| DB transient failure retried | Platform database | ✅ | Integration |
| Messaging backpressure handled | Platform messaging | ✅ | Integration |
| Browser a11y WCAG 2.1 AA | YAPPC frontend | ⚠️ | tier-4 partial |
| Performance budget: P95 < SLA | Auth gateway | ✅ | k6 thresholds |
| AI model hallucination eval | YAPPC | ⚠️ | AI eval suite partial |
