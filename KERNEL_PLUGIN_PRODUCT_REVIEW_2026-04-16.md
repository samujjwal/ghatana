# Kernel + Plugin Programming Model Review
## Strategic Audit: `platform-kernel`, `platform-plugins`, and Products `finance`, `phr`, `aura`, `flashit`

**Date:** 2026-04-16
**Scope:** Deep re-audit of the kernel+plugin foundation and its adoption by vertical products. Evaluates whether the kernel+plugin programming model is credible as the future blueprint for building products (PHR, Finance, Aura, Flashit, and beyond) on top of a shared substrate.
**Evidence basis:** Repository code (HEAD), build files, docs, prior audit `PRODUCT_REVIEW_FINDINGS_2026-04-16.md`, `OSS_LIBRARY_AUDIT_REPORT.md`, `platform-kernel/MIGRATION_STATUS.md`.

---

## 1. Executive Verdict

| Dimension | Rating | Notes |
|---|---|---|
| **Production readiness (kernel foundation)** | **Ready** | Core + plugin framework + persistence are mature; 42 kernel tests; real Postgres/Redis adapters; clean SPI boundaries. |
| **Production readiness (finance backend)** | **Partial** | 7 kernel services + all 6 plugins wired; solid service layer + tests; **no HTTP controller wiring; no frontend**. |
| **Production readiness (phr backend)** | **Partial** | 15 kernel services; FHIR engine; HIPAA-shaped security; **FHIR server endpoint missing; tenant isolation enforcement missing; retention jobs missing; frontend on mock data**. |
| **Production readiness (aura)** | **Critically Not Ready** | Design-phase only. Kernel dependency declared; no implementation. |
| **Production readiness (flashit)** | **Critically Not Ready** | Java domain stubbed; kernel dependency declared but unused; AI logic leaked into Node gateway. |
| **Feature completeness (claims vs. reality)** | **Partial / Misleadingly Complete** | PHR README marks 14/17 modules ✅ while FHIR server, tenant isolation, retention jobs and frontend wiring are open. Finance claims trade-processing depth without any public surface wired. |
| **Correctness confidence** | **Medium** | Core kernel: high. Finance services: high on unit logic, low end-to-end. PHR: medium on services, low on cross-cutting compliance. |
| **Hardening** | **Moderate** | Kernel: strong (hash-chain audit, ConcurrentHashMap, deprecation discipline). Products: weak end-to-end (no HTTP security filters wired, no tenant filter on PHR, frontend→backend seam is mock). |
| **UI/UX quality** | **Weak** | Finance has no UI. PHR web is a design-system shell rendering `mockData.ts`. |
| **Competitive position** | **Moderate** | The kernel+plugin differentiator is real (SOX/HIPAA/GDPR/PCI-DSS rules shipped, hash-chain audit, shared consent) but not yet provable end-to-end in any single product. |
| **Problem-solution fit** | **Strong (architecturally)** | The model genuinely reduces cross-product rewrite cost (fraud, consent, audit, billing, compliance, risk). Fit is structurally right. |
| **Innovation / disruption potential** | **Strong (latent)** | Kernel + shared plugin pack is a credible moat if adoption reaches AEP/Data-Cloud/YAPPC and if cross-product flows (PHR↔Finance billing, Aura↔PHR consent) are proven in runtime. |

### What improved since last audit
- Kernel migration is **done** (legacy `platform/java/kernel` etc. archived; composite builds for `platform-kernel` and `platform-plugins`).
- Six standard plugins have **real implementations** (not stubs): billing-ledger, fraud-detection, compliance, consent, audit-trail, risk-management.
- Finance and PHR `build.gradle.kts` now declare real kernel + plugin dependencies (not just in docs).
- Cross-product integration tests exist (`integration-tests/phr-finance-integration`).

### What is still open
- **Model adoption is uneven.** AEP, Data-Cloud, YAPPC, Audio-Video do **not** depend on `platform-kernel` / `platform-plugins` in any wired path — the "one kernel for all products" thesis is only proven on PHR and Finance.
- **Finance has no HTTP surface.** Contracts exist, services exist, no servlet/controller/route wires them.
- **PHR has critical compliance gaps** (tenant isolation, retention jobs, FHIR server endpoint) that invalidate production claims for a regulated health product.
- **Frontends are mock-backed.** PHR web renders `mockData.ts`; no API client integration visible.
- **Aura and Flashit** are not real adopters yet — Aura is pre-engineering, Flashit's Java domain is a stub.
- **Duplication debt:** `JsonUtils` exists in both `platform/java/core` and `platform-kernel/kernel-core`; dual JSON libraries (Jackson + Gson) still resident.
- **Plugins are in-memory only.** All six `StandardXxxPlugin` classes use `ConcurrentHashMap`. The kernel *has* `kernel-persistence` with Postgres/Redis, but the standard plugin impls do not yet bind to durable storage — so "billing ledger," "audit trail," "consent" are non-durable by default.

### What regressed
- Nothing clearly regressed vs. the prior report, but the **gap between kernel maturity and product adoption has widened**: kernel is ready, products aren't yet using it consistently.

### Top 15 critical findings (ordered by severity)

1. **[CRITICAL] PHR tenant isolation enforcement not implemented** — cross-tenant exposure risk, blocks HIPAA/Nepal Privacy Act 2075 claims. (`products/phr/docs/01_governance/phr_qa_delivery_plan.md#L139`)
2. **[CRITICAL] PHR retention cleanup, export expiry, sync-replay jobs not implemented** — directly violates 25-year retention + right-to-erasure promises in README. (`products/phr/docs/01_governance/phr_qa_delivery_plan.md#L142`)
3. **[CRITICAL] PHR FHIR server endpoint not implemented** — FHIR transformation engine exists, but no HTTP surface, so no clinician/HIE can interoperate. (`products/phr/docs/03-PHR_IMPLEMENTATION_PLAN.md#L140`)
4. **[CRITICAL] Finance has no REST/HTTP controller layer wired** — `TransactionService`, `LedgerManagementService`, `ComplianceService`, `RiskManagementService` exist but have no public surface; any "trade processing" claim is not reachable.
5. **[CRITICAL] All six platform plugins are in-memory only** — `StandardBillingLedgerPlugin`, `StandardAuditTrailPlugin`, `StandardConsentPlugin`, `StandardCompliancePlugin`, `StandardFraudDetectionPlugin`, `StandardRiskManagementPlugin` all use `ConcurrentHashMap` as storage. This contradicts "tamper-evident, durable, regulatory-grade" plugin narratives.
6. **[HIGH] PHR web app renders mock data only** — `apps/web/src/pages/DashboardPage.tsx` reads `mockData.ts`; no API client integration is visible end-to-end.
7. **[HIGH] Kernel+plugin model not adopted by AEP, Data-Cloud, YAPPC, Audio-Video** — the primary "platform products" still operate pre-kernel. The thesis "build any product on kernel+plugin" is unproven beyond PHR/Finance.
8. **[HIGH] Flashit `backend/agent` is an empty stub** — `build.gradle.kts` exists, kernel dependency is declared at root, but there is no `src/`; AI processing sits in the Node gateway (`whisper-service.ts`) against stated architecture.
9. **[HIGH] Aura is pre-implementation** — specs and scaffolding only, no runtime code. Kernel adoption is a promise, not an artifact.
10. **[HIGH] Duplicate `JsonUtils`** across `platform/java/core` and `platform-kernel/kernel-core` — drift risk and violates §25 forward-fix policy. (`OSS_LIBRARY_AUDIT_REPORT.md`)
11. **[HIGH] Dual JSON stacks (Jackson + Gson)** still resident — 1,538 Jackson usages + 628 Gson usages; boundary code can silently diverge on serialization.
12. **[MEDIUM] Plugin test coverage is thin** — exactly 1 unit test file per plugin; no contract tests across product↔plugin boundaries; no failure-mode / concurrency / fuzz tests on hash-chain or idempotency.
13. **[MEDIUM] PHR emergency-access and caregiver services are service-level only** — break-glass workflow UI, post-access mandatory-audit UI, and caregiver delegation UI are missing. The security-critical human loop is not provable.
14. **[MEDIUM] Finance fraud ML path has a deterministic fallback** — `DefaultFraudModelInferenceService` returns a deterministic fallback score when remote inference fails. This is correct for resilience but must be logged/metered as a degradation; no metric wiring found.
15. **[MEDIUM] `KernelExtension` vs. `KernelModule` vs. plugin `Plugin` SPI** — three closely related contracts with overlapping lifecycles. Product devs have to choose between extending via `KernelExtension`, owning a `KernelModule`, or shipping a `Plugin`; there is no concise programming-model doc disambiguating the three. Onboarding friction.

---

## 2. Problem / Use-Case Validation Matrix

### 2.1 Kernel + Plugin Programming Model (the library itself is a "product" here)

| Item | Finding |
|---|---|
| **User / persona** | Internal product teams (Finance, PHR, Aura, Flashit, eventually AEP/Data-Cloud/YAPPC). |
| **Problem solved** | Stop rebuilding billing, fraud detection, consent, audit, compliance, risk per product. Provide lifecycle + DI + registry + hot-reload + tenant context. |
| **Why it matters** | Healthcare and finance share ~70% of compliance plumbing (audit trail, consent, regulation engine). Without a kernel, each product re-invents it and drifts. |
| **Expected workflow** | Product declares `KernelModule`, discovers capabilities via `KernelContext`, pulls shared `Plugin` impls (or ships product-specific ones), registers via `KernelRegistry`. |
| **Implementation evidence** | `platform-kernel/kernel-core` (KernelModule, KernelRegistry, KernelContext, KernelExtension); `platform-kernel/kernel-plugin` (Plugin, PluginRegistry, HotReloadPluginManager, JarPluginLoader); `platform-kernel/kernel-persistence` (Postgres + Redis adapters). |
| **Missing pieces** | (a) No concise programmer guide that disambiguates `KernelModule` / `KernelExtension` / `Plugin`. (b) Durable default plugin backends. (c) Adoption by AEP/Data-Cloud/YAPPC. |
| **Correctness status** | Contracts are correct and cleanly typed. Lifecycle is Promise-based (non-blocking). D4 decision makes `KernelRegistry` the only public root — good. |
| **Production credibility** | High as a **library**; medium as a **product model** because only 2 of 13 products adopt it. |
| **Verdict** | **Supported but not yet proven at scale.** Right problem, right shape, low adoption. |

### 2.2 Finance — "Process trades, manage risk, stay compliant, reconcile"

| Item | Finding |
|---|---|
| **User / persona** | Trading desks, risk officers, compliance teams, operators, regulators. |
| **Problem solved** | End-to-end trade lifecycle with real-time risk + regulatory reporting. |
| **Expected workflow** | Order → validation → fraud check → risk assessment → settlement → ledger post → regulatory report. |
| **Implementation evidence** | 18 domain modules; `TransactionService` (idempotent, rate-limited); `LedgerManagementService` (double-entry dataset); `RiskManagementService`, `ComplianceService`, `MarketDataService`, `PortfolioManagementService`, `OrderManagementService`; `DefaultFraudModelInferenceService` with circuit-breaker + deterministic fallback; `FinanceKernelModule`; kernel + all 6 plugins wired. 50 tests. |
| **Missing pieces** | **No HTTP endpoint wiring.** No REST controllers / servlets that call `TransactionService`. Regulator portal SPA is a directory, not an app. Consumer/retail surfaces explicitly listed as "not implemented". No Flyway/Liquibase migrations (schemas live inside `initializeDatasets()`). |
| **Correctness status** | Unit-level: high. End-to-end: unprovable (no public entry point). |
| **Production credibility** | **Backend-ready, surface-less.** |
| **Verdict** | **Supported but fragile.** Core is right; the product cannot be exercised by a real customer. |

### 2.3 PHR — "Patient-owned health record with consent, FHIR, emergency access"

| Item | Finding |
|---|---|
| **User / persona** | Patients, providers, clinicians, caregivers, emergency responders. |
| **Problem solved** | Patient-centric 25-year record with granular consent, break-glass, FHIR interop, Nepal Directive 2081 compliance. |
| **Expected workflow** | Patient logs in → views records → grants/revokes consent → provider requests access → emergency override with mandatory audit → FHIR export to HIE. |
| **Implementation evidence** | 15 kernel services (`PatientRecordService`, `ConsentManagementService`, `LabResultService`, `MedicationService`, `ImagingService`, `ImmunizationService`, `ClinicalNoteService`, `DocumentService`, `AppointmentService`, `TelemedicineService`, `ReferralService`, `CaregiverService`, `EmergencyAccessLogService`, `BillingService`, `ClinicalDecisionSupportService`); `FhirR4TransformationEngine` + `FhirValidator`; `PHRSecurityManagerImpl` + `PHRPrivacyManagerImpl` + `PhiFieldEncryptionService`; hash-chained audit; 60 tests; Prisma schema with 50+ FHIR-aligned models. |
| **Missing pieces** | (1) **FHIR server endpoint** — transformation exists, server doesn't. No HIE interop is possible. (2) **Tenant isolation enforcement** — explicitly called out as missing in `phr_qa_delivery_plan.md#L139`. (3) **Retention cleanup, export expiry, sync-replay jobs** — called out as missing. (4) **Mobile app** — not started. (5) **Nepal HIE** — stub. (6) **Web UI is mock-wired** — no real API calls from React pages. (7) **Break-glass audit UI + caregiver delegation UI** absent. |
| **Correctness status** | Service layer correctness is medium-high; compliance correctness is **low** because tenant isolation and retention workflows are unimplemented. |
| **Production credibility** | **Not credible for a regulated patient-facing launch.** The README marks most modules ✅ Complete; the delivery plan contradicts that. This is misleading completeness. |
| **Verdict** | **Partially supported, compliance-blocking gaps.** |

### 2.4 Aura — "Personal AI intelligence platform"

| Item | Finding |
|---|---|
| **Problem solved** | Personal intelligence agent surface built on GAA framework + kernel. |
| **Implementation evidence** | Module trees exist (`agents/`, `domain/`, `foundation/`, `integration/aep/`, `platform/`). Kernel dependency declared in `domain/recommendation/build.gradle.kts#L14` and `integration/aep/build.gradle.kts#L18`. `AURA_END_TO_END_PRODUCTION_AUDIT.md`: **"PRE-PRODUCTION — IMPLEMENTATION NOT STARTED"**. |
| **Missing pieces** | Everything runtime. |
| **Verdict** | **Claimed but not implemented.** Kernel fit is *correct on paper* and positions Aura to be the cleanest adopter once engineering starts. |

### 2.5 Flashit — "Personal context capture (audio/text/vision → reflections)"

| Item | Finding |
|---|---|
| **Problem solved** | Continuous personal context capture + summarization/reflection pipeline. |
| **Implementation evidence** | Clients (Expo RN + web) scaffolded. Node gateway (Fastify, port 2900) is running AI logic (`whisper-service.ts`). Kernel dependency declared at root `build.gradle.kts#L11`. |
| **Missing pieces** | `backend/agent/build.gradle.kts` is empty; no `src/`. Java domain is absent. Reflection loop / System-2 thinking / event sourcing (required for GAA) not present. Mixed AWS SDK v2+v3. |
| **Verdict** | **Implemented but solving the wrong problem in the wrong layer.** AI + domain logic should live in the Java kernel-backed module; today it sits in Node. Kernel dependency is vestigial. |

---

## 3. Previous Audit Closure Matrix (kernel/plugin-relevant items)

| Prior finding | Severity | Current evidence | Status | Root-cause fixed? |
|---|---|---|---|---|
| Billing duplicated across products | HIGH | `StandardBillingLedgerPlugin` centralized; Finance + PHR both depend on `platform-plugins:plugin-billing-ledger` | **Resolved (structurally)** | Yes (structure); **No (durability)** — still in-memory |
| Consent logic duplicated between PHR and others | HIGH | `StandardConsentPlugin`; PHR `HealthcareConsentKernelExtension` wraps it | **Resolved (structurally)** | Yes (structure); No (persistence + multi-tenant) |
| Audit trail scattered | HIGH | `StandardAuditTrailPlugin` with SHA-256 hash chain; `PostgresAuditTrailPersistence` exists in `kernel-persistence` | **Partially resolved** | No — the standard plugin doesn't bind to the Postgres implementation by default |
| Compliance rules repeated per product | HIGH | `StandardCompliancePlugin` ships SOX / HIPAA / GDPR / PCI-DSS built-ins | **Resolved (structurally)** | Yes (shape); No (rules are not yet versioned/governed) |
| Duplicate `JsonUtils` | MEDIUM | Still present in both `platform/java/core` and `platform-kernel/kernel-core` | **Open** | No |
| Dual JSON libraries (Jackson + Gson) | MEDIUM | Both resident; no consolidation PR | **Open** | No |
| YAPPC @ts-nocheck, duplicate web apps | P0 | Out-of-scope here, but relevant because YAPPC should eventually be kernel-backed | **Unrelated to kernel, still open** | N/A |
| AEP `PlaceholderAgent` throws `UnsupportedOperationException` | P0 | AEP has no kernel dependency; no migration plan found | **Open + not tied into kernel** | No |
| Data-Cloud auth disabled by default, tenant fallback | P0 | Out of scope for this review but **the kernel has `TenantContext` + security primitives that would fix it** | **Open; kernel is the right target** | No |
| Plugin contracts marked "concrete implementations" in `MIGRATION_STATUS.md` | — | Confirmed: real logic, not stubs | **True** | Yes |

---

## 4. Product Claim vs. Reality Matrix

| Capability | Claimed where | Implementation evidence | Missing | Correctness | Hardening | Test evidence | Verdict |
|---|---|---|---|---|---|---|---|
| **Kernel lifecycle (init/start/stop on `Promise<Void>`)** | kernel-core | `KernelModule.java`, `AbstractKernelModule`, `DefaultKernelContext` | — | High | Strong | `KernelLifecycleIntegrationTest`, `KernelModuleIntegrationTest` | **Complete & credible** |
| **Public root registry (D4)** | kernel-core | `KernelRegistry.java`; internal registries marked `@KernelInternal` | — | High | Strong | `KernelRegistryTest` | **Complete & credible** |
| **Hot-reload plugins** | kernel-plugin | `HotReloadPluginManager`, `EnhancedPluginManager`, `JarPluginLoader` | No end-to-end reload test found; `DefaultPluginContext.java#L77` "Direct plugin request not yet implemented" | Medium | Moderate | 1 test file (`InMemoryStoragePluginTest`) | **Partial** |
| **Durable audit trail (hash chain)** | plugin-audit-trail | `StandardAuditTrailPlugin` computes SHA-256 chain; `PostgresAuditTrailPersistence` exists separately | Standard plugin stores in `ConcurrentHashMap`, not Postgres. Merkle/anchoring claimed in PHR README not located in code. | Low (end-to-end) | Weak | 1 unit test | **Misleadingly complete** |
| **Durable billing ledger (idempotent, reversible)** | plugin-billing-ledger | `StandardBillingLedgerPlugin` with transaction dedupe + reversal set | In-memory only | Medium | Weak | 1 unit test | **Partial** |
| **Multi-regulation compliance (SOX/HIPAA/GDPR/PCI-DSS)** | plugin-compliance | Pre-registered `ComplianceRule` sets with severity + categories | No rule-version migration, no tenant-specific overlays, no evidence generation | Medium | Moderate | 1 unit test | **Partial** |
| **Universal consent (record/verify/revoke/history)** | plugin-consent | `StandardConsentPlugin` with expiration + revocation | In-memory; no cross-node replication (PHR wrote its own `DistributedCachePort`) | Medium | Weak | 1 unit test | **Partial** |
| **Fraud detection (rule-based + ML)** | plugin-fraud-detection | SPI + `trainModel`, `detectPatterns`, rule registration | `trainModel` persists nothing durable | Medium | Moderate | 1 unit test | **Partial** |
| **Multi-type risk (market/credit/operational/liquidity)** | plugin-risk-management | `calculate`, `registerModel`, `getExposures`, `setThreshold` | In-memory; no stress-test harness | Medium | Moderate | 1 unit test | **Partial** |
| **Finance trade processing** | domain-pack-manifest | `TransactionService`, 18 domain modules | No HTTP entry, no regulator UI, no consumer/retail UI | Medium | Weak | `TransactionServiceTest` + 49 others | **Misleadingly complete** |
| **Finance double-entry ledger** | FinanceCapabilities | `LedgerManagementService` dataset (debit/credit/amount/currency/postedAt) | No migration, no Postgres binding on default path, no end-to-end reconciliation test | Medium | Weak | Integration tests use Testcontainers | **Partial** |
| **PHR patient records (25-yr retention)** | README | `PatientRecordService` + repository + Prisma schema | Retention cleanup job **not implemented** | Low (retention) | Weak | Persistence tests | **Misleadingly complete** |
| **PHR consent (Nepal 2081)** | README | `ConsentManagementService` with rate limits + distributed cache port + immutable audit integration | Tenant-scoping enforcement missing | Medium | Moderate | `ConsentManagementServiceTest` | **Partial** |
| **PHR FHIR R4 interop** | README | `FhirR4TransformationEngine`, `FhirValidator`, `FhirTransformer` | **FHIR server endpoint not implemented** | N/A (no server) | N/A | — | **Claimed but not implemented** |
| **PHR emergency break-glass** | README | `EmergencyAccessLogService` + consent plugin emergency window | No UI; no mandatory post-access audit flow wired to frontend | Medium | Weak | — | **Partial** |
| **PHR HIPAA-compliant security** | README + `HIPAA_VALIDATION_EVIDENCE_2026-04-07.md` | `PHRSecurityManagerImpl`, `PHRPrivacyManagerImpl`, `PhiFieldEncryptionService` | Tenant isolation enforcement missing (critical HIPAA gap) | Low (cross-tenant) | Weak | `PHRSecurityIntegrationTest` | **Misleadingly complete** |
| **PHR web app** | README ("React 19 + Tailwind") | 10 pages under `apps/web/src/pages/` | Renders `mockData.ts`; no API client integration | Low | Weak | Snapshot/rendering tests only | **Scaffolded, not wired** |
| **PHR mobile** | README | `apps/mobile/` scaffold | Not started | — | — | — | **Claimed but not implemented** |
| **Aura runtime** | PRD / architecture docs | Directory scaffolds, kernel deps declared | All runtime code | — | — | — | **Claimed but not implemented** |
| **Flashit Java domain on kernel** | root `build.gradle.kts#L11` | Dependency declared | `backend/agent/src/` absent; AI logic in Node gateway | — | — | — | **Implemented but solving wrong problem** |

---

## 5. Gaps We Must Fill

### 5.1 Product / use-case
- **Finance HTTP surface:** `TransactionService` → POST `/api/v1/transactions`, GET `/api/v1/transactions/{id}`, plus ledger query and reconciliation endpoints. Without this, Finance is a library, not a product.
- **PHR FHIR server endpoint:** wrap `FhirR4TransformationEngine` behind `FhirController` (already exists as scaffold) and expose `/fhir/{ResourceType}` with FHIR R4 conformance.
- **PHR frontend wiring:** replace `mockData.ts` imports with a typed API client (respect §27 Zod boundary validation).
- **PHR retention + export-expiry + sync-replay jobs** (scheduled): this is non-optional for Nepal Privacy Act 2075 and for the "25-year retention" claim.
- **PHR tenant isolation filter** analogous to Data-Cloud's `TenantContextFilter` pattern, sourced from kernel `TenantContext`.
- **Aura:** start engineering Phase 1 with kernel + consent + audit + fraud-signal plugins wired on day 1.
- **Flashit:** re-home the AI and domain logic into `backend/agent/` as a real Java `KernelModule` that consumes kernel-plugin storage/audit/consent; shrink the Node gateway to transport.

### 5.2 Correctness
- **Default plugin durability:** provide `Postgres*Plugin` variants (or repository-backed decorators) for all six plugins. The current `Standard*Plugin` classes should document "in-memory — for test / bootstrap" and not be mounted in production products.
- **Audit hash-chain verification test:** `StandardAuditTrailPlugin` has `verify(...)` but no test that deliberately mutates a historical entry and asserts verification fails.
- **Idempotency contract tests:** `StandardBillingLedgerPlugin.postTransaction` idempotency should be covered by a contract test that Finance + PHR both run.
- **Cross-tenant leakage test:** add a kernel-level contract test that every plugin refuses to return another tenant's data given only the caller's `TenantContext`.

### 5.3 Architecture
- **Disambiguate `KernelModule` vs. `KernelExtension` vs. `Plugin`.** Ship a one-page "When do I use which?" doc with a decision tree. This is the #1 onboarding obstacle for product teams considering the model.
- **Publish the product-on-kernel template** (likely under `templates/`) so Aura, Flashit v2, and future products scaffold correctly.
- **Canonical durable plugin tier:** promote `kernel-persistence` adapters into optional default bindings when a datasource is present.
- **Migrate AEP, Data-Cloud, YAPPC onto the kernel** — this is the single largest architectural win available. Every P0 from the prior audit (PlaceholderAgent, tenant fallback, governance simulation) maps cleanly to an existing kernel/plugin primitive.

### 5.4 UX
- **PHR:** real login → dashboard → records view → consent grant/revoke → break-glass flow with mandatory audit confirmation UI. Replace all `<Input placeholder=... />` ghosts.
- **Finance:** at minimum a regulator-facing view that reads ledger + compliance evidence; a trading/ops console can follow.

### 5.5 Hardening
- Wire rate limiting + policy enforcement points consistently at HTTP boundaries (PHR has `PolicyEnforcementPoint` inside controllers — good — but no HTTP filter chain yet).
- Consolidate secret management — no kernel-level helper for secret retrieval was found; each product currently rolls its own.
- Retire `Gson`; consolidate on Jackson. Delete duplicate `JsonUtils`.

### 5.6 Data / integrations
- **Schema migrations:** Finance dataset declarations live in `initializeDatasets()`. Add Flyway/Liquibase (or Prisma migrations for PHR) and wire them through `kernel-persistence`.
- **Event bus:** kernel has `event/` + `communication/` packages, but cross-product event flows (PHR `billing.posted` → Finance ledger, PHR `consent.revoked` → broadcast) are not demonstrated in integration tests.
- **Plugin dependency resolution:** `PluginDependencyResolver` exists but no product composes multiple plugins declaratively via a manifest.

### 5.7 Testing / proof
- Add **contract tests** per plugin SPI that every product runs (PHR + Finance + future Aura).
- Add **soak / concurrency tests** for audit-trail hash chain and billing-ledger idempotency.
- Add **failure-mode tests** for `DefaultFraudModelInferenceService` fallback — assert metric emission on degradation.
- Replace the **mock-backed PHR page tests** with contract-verified tests against the typed API client.

### 5.8 Operations
- Neither Finance nor PHR has a deployable `Dockerfile` / Helm chart shown in this scope; `monitoring/` exists at repo root but no product-level runbook links it.
- Add `/health`, `/ready`, `/metrics` on whatever HTTP servers get added.

### 5.9 Strategy / positioning
- The single strongest narrative Ghatana can tell is: *"Ship a regulated product in a quarter on the kernel."* PHR+Finance cross-product billing is the proof point. We are ~60% there but nothing end-to-end is demonstrable today. Prioritize an end-to-end thin slice over breadth.

---

## 6. Fake Completeness / Shallow Fix Findings

| # | Location | Evidence | Why unacceptable | Required replacement |
|---|---|---|---|---|
| F1 | `platform-plugins/plugin-audit-trail/.../StandardAuditTrailPlugin.java` | In-memory `Map<String,List<AuditEntry>>` behind a "tamper-evident" claim | A hash chain kept in RAM is erased at process restart; not tamper-evident at all in operations | Bind default impl to `PostgresAuditTrailPersistence` when datasource is present |
| F2 | `platform-plugins/plugin-billing-ledger/.../StandardBillingLedgerPlugin.java` | In-memory ledger | Ledgers must be durable by definition | Postgres-backed impl + migrations + integration test |
| F3 | `platform-plugins/plugin-consent/.../StandardConsentPlugin.java` | In-memory consents | PHR built its own `DistributedCachePort` *around* this to work around the gap | Durable + distributed default |
| F4 | `products/phr/apps/web/src/pages/*Page.tsx` | All pages read `mockData.ts` | Product README claims capability matrices complete | Real typed API client (Zod schemas per §27) |
| F5 | `products/phr/README.md` capability matrix | Marks ✅ Complete for modules the delivery plan marks ❌ Unimplemented | Misleading to any reader | Reconcile README with `phr_qa_delivery_plan.md` |
| F6 | `products/finance` public surface | Controllers / endpoints absent; yet docs describe "API v1/transactions" with rate limits | Claim without surface | Implement HTTP handlers wired to `TransactionService` |
| F7 | `products/flashit/backend/agent/build.gradle.kts` | Module declared, kernel dependency declared; no `src/` | Gives false impression of kernel adoption | Either implement or delete the stub |
| F8 | `platform-kernel/kernel-plugin/.../DefaultPluginContext.java#L77` | "Direct plugin request not yet implemented" throws `UnsupportedOperationException` | Acceptable as an SPI hole only if explicitly marked `@Incubating` | Mark incubating and guard against accidental call in production paths |
| F9 | `products/phr/src/main/java/com/ghatana/phr/kernel/events/PhrEventProcessor.java#L240` (“Operator Placeholders”) and `PhrPatientDataService.java#L167` (“Placeholder Classes”) | Placeholder markers inside production-path files | Either complete or extract behind a feature flag |
| F10 | `platform-kernel/kernel-core/.../ContractValidationGate.java#L65` | `TODO`: "Re-enable validators after fixing schema/metadata compatibility issues" | Contract validation being silently off is a correctness hole at the center of the kernel | Fix schema compatibility; re-enable; add regression |

---

## 7. End-to-End Correctness Findings

| Workflow | Expected | Actual | Layers affected | Proof status | Severity | Correction |
|---|---|---|---|---|---|---|
| Finance: submit transaction → ledger post → audit record | Client POST → controller → `TransactionService` → `BillingLedgerPlugin` → `AuditTrailPlugin` | No controller; service is callable only from tests | HTTP, service, plugin | None (integration tests exist at service layer only) | HIGH | Add HTTP layer; extend `phr-finance-integration` tests to cover the full chain |
| PHR: patient fetches records | React page → API client → `PatientController` → `PatientService` → repository | React page reads `mockData.ts`; controller and service exist but are not wired to frontend | UI, BFF | None | HIGH | Implement typed API client + wire pages |
| PHR: consent revoke propagates cross-product | `ConsentPlugin.revoke` → kernel event → PHR + Finance subscribe | Plugin stores revocation; no kernel event emission found | Plugin, event bus | None | HIGH | Emit `consent.revoked` on kernel event bus; add subscriber test |
| PHR: emergency break-glass leaves mandatory audit | UI confirm → service → `EmergencyAccessLogService` → `AuditTrailPlugin` with non-repudiable chain | Service exists; no UI; hash chain in-memory | UI, service, plugin | None E2E | CRITICAL | Durable audit binding + UI flow |
| PHR: tenant A cannot see tenant B data | Every repository query filtered by `TenantContext` | Tenant isolation explicitly marked not-implemented | Service, repository | None | CRITICAL | Add `TenantContextFilter` (mirror Data-Cloud pattern) + repository-layer enforcement tests |
| PHR: FHIR R4 export | Client GET `/fhir/Patient/{id}` → transformation → FHIR JSON | No endpoint | HTTP | None | CRITICAL | Wire `FhirController` → `FhirR4TransformationEngine` |
| Kernel: plugin hot-reload preserves state | Old plugin stops, new plugin starts, state migrates via `EnhancedPluginManager` | Component exists, no end-to-end test observed | Plugin framework | Weak | MEDIUM | Add integration test |

---

## 8. Hardening Findings

| Location | Issue | Failure mode | Severity | Fix |
|---|---|---|---|---|
| Standard plugins (all six) | No durable default backend | Data loss on restart | CRITICAL | Default to kernel-persistence when DS available |
| PHR | No tenant filter on HTTP boundary | Cross-tenant exposure | CRITICAL | Implement at BFF edge |
| PHR/Finance | No `/health`, `/ready`, `/metrics` endpoints shown | Ops blind spot | HIGH | Add per product |
| Kernel `ContractValidationGate` TODO | Validation disabled | Silent contract drift | HIGH | Re-enable |
| `DefaultPluginContext.findPluginsByCapability` returns "not yet implemented" for string route | Silent discovery hole | Plugins invisible to capability-driven consumers | MEDIUM | Implement |
| Frontend Zod usage | Not enforced at API seam (because seam is mock) | Unsafe once real API arrives | MEDIUM | Introduce Zod at boundary per §27 before wiring |
| JSON library duality | Jackson ↔ Gson divergence at boundaries | Silent serialization drift | MEDIUM | Consolidate |
| Secret handling | No kernel secret provider observed | Per-product re-implementation drift | MEDIUM | Add `SecretProvider` SPI in kernel |

---

## 9. UI / UX Findings

- PHR web pages are design-system shells over `mockData.ts`; no empty/error/loading states wired to real data flows.
- Form fields use placeholders as labels (`<Input placeholder="National ID" />`), which is both an accessibility issue (no `aria-label` / `<label>`) and a wiring smell.
- Finance has no UI at all — regulator portal, trading ops console, and reconciliation views are directories, not apps.
- Break-glass emergency access has no confirmation/audit screen despite being the most security-sensitive flow.
- No keyboard-first flows or focus-management patterns observable in the reviewed pages.

---

## 10. Competitor / Market Analysis (kernel-as-product lens)

| Category | Representative solutions | Where they are stronger | Where they are weaker | Ghatana kernel+plugin angle |
|---|---|---|---|---|
| **Health data platforms** | Redox, 1up Health, Particle Health | Battle-tested FHIR ingress/egress, payer integrations, clearinghouse partnerships | Rigid verticalization; hard to embed custom consent / break-glass semantics; no shared substrate with finance | Shared consent + audit + billing plugin across health and non-health = cross-pollination |
| **Financial core engines** | Thought Machine, 10x Banking, Mambu | Core-banking robustness, regulatory certification | Closed stacks; weak AI integration; weak cross-domain (no PHR-compatible consent semantics) | Kernel+plugin gives one consent/audit/fraud spine across verticals |
| **App/agent frameworks** | LangGraph, CrewAI, AutoGen, Salesforce Agentforce | Rich LLM ergonomics, growing agent patterns | Weak regulated-industry posture (audit, consent, tenancy) | Plugins already include audit, consent, fraud, risk — the regulated default the AI frameworks lack |
| **Open-source plugin platforms** | Backstage, Nx, Dapr, Quarkus/OSGi | Plugin loading + lifecycle maturity | Not opinionated for regulated verticals | Ghatana's SOX/HIPAA/GDPR/PCI-DSS defaults and hash-chain audit are unusually specific differentiators |
| **Incumbent/status quo** | In-house rewrites | Fully bespoke fit | Expensive, drift-prone, uncertifiable | The kernel's central bet |

**Whitespace:** a *regulated-vertical-ready* kernel (audit + consent + billing + compliance rule sets + fraud/risk + tenant + hot-reload) that credibly spans health and finance. If PHR↔Finance cross-plugin works end-to-end, that is a genuinely rare combination.

**Commodity zones we shouldn't romanticize:** generic DI, generic event bus, generic plugin loader. The moat is in the *regulated plugin pack + cross-product flows*, not in the kernel abstractions themselves.

---

## 11. Innovation / Disruption Positioning

Proposed differentiators, ranked by defensibility:

1. **Cross-product regulated flows as a product.** "PHR bills through Finance via `plugin-billing-ledger`, revokes consent through `plugin-consent`, and both share one audit chain." No incumbent spans healthcare + finance under one consent+audit substrate. This is the moat.
2. **Durable, verifiable audit-by-default.** Hash-chain + Postgres anchor + Merkle roots (when wired) + plugin-standardized audit schema. Sellable as "every action across every product is cryptographically attested."
3. **Regulated rule libraries shipped with the kernel.** SOX/HIPAA/GDPR/PCI-DSS + Nepal Directive 2081 + Nepal Privacy Act 2075 bundled; customers only add overlays. Shipping rules is rare; most competitors ship hooks.
4. **Tenant + consent + PHI encryption as a composable triangle.** Many platforms ship one; very few ship all three as plugins with the same SPI.
5. **Human-in-loop autonomy for regulated actions.** Finance's `AutonomyManager` (>$100k approval) + PHR's break-glass audit are the same pattern — promote to a kernel capability (`HumanApprovalPlugin`).
6. **Hot-reload + dependency-aware plugin graph** is present but nobody has marketed it. Offer a "swap fraud model at runtime, verify audit integrity afterwards" demo as the innovation headline.

Commodity improvements (do not lead the narrative with these): generic async DI, capability registry, generic plugin manager.

Gimmicks to avoid: AI-wrapping for its own sake, "marketplace" narratives before the above flows are proven.

---

## 12. Testing / Proof Gaps

| Capability | Expected proof | Current | Missing | Confidence |
|---|---|---|---|---|
| Kernel lifecycle | Integration test with failing dependency | Present | Chaos test | High |
| Audit-trail tamper resistance | Mutation test of stored entry → `verify()` returns false | Absent | Add | Low |
| Ledger idempotency | Contract test shared by Finance + PHR | Absent (each has unit tests) | Add | Medium |
| Cross-product: PHR bills via Finance | Integration test exercising full chain | `integration-tests/phr-finance-integration` exists but scope unclear | Expand | Medium |
| FHIR conformance | Public FHIR test suite against server | No server | Stand up server + run HL7 test kit | None |
| PHR tenant isolation | Repository contract refuses cross-tenant queries | Absent | Add per repository | None |
| PHR retention | Scheduled job test that removes expired records | Absent (jobs unimplemented) | Implement + test | None |
| Consent revocation propagation | Event subscriber test across products | Absent | Add | None |
| Plugin hot-reload | Integration test swapping implementations | Absent | Add | None |
| Frontend contract | Zod-validated API responses at the seam | Absent (seam is mock) | Stand up seam | None |

---

## 13. Current Release Blockers

**Kernel / plugin framework:** none for the *library*; the framework itself could be released as 1.0 with known caveat "standard plugin impls are in-memory reference impls."

**Finance (as a product):**
- B1. No HTTP surface.
- B2. No deployable runbook.
- B3. Durable persistence bindings for ledger/audit absent on the default code path.

**PHR (as a product):**
- B4. Tenant isolation enforcement missing.
- B5. Retention cleanup / export expiry / sync replay jobs missing.
- B6. FHIR server endpoint missing.
- B7. Frontend is mock-backed.
- B8. Durable persistence bindings for audit/consent absent on default path.

**Aura:** entire runtime.

**Flashit:** Java domain.

---

## 14. Prioritized Remediation & Advancement Plan

### Phase 0 — Blockers & fake completeness
- P0.1 Bind `StandardAuditTrailPlugin`, `StandardBillingLedgerPlugin`, `StandardConsentPlugin` to `kernel-persistence` as default when a datasource exists. Rename in-memory versions `InMemory*Plugin` and mark for test only.
- P0.2 Implement PHR tenant isolation filter + repository-level enforcement tests.
- P0.3 Implement PHR retention/export/sync-replay scheduled jobs.
- P0.4 Stand up FHIR server endpoint in PHR wrapping the existing engine.
- P0.5 Wire Finance HTTP controllers → `TransactionService`, `LedgerManagementService`, `ComplianceService`.
- P0.6 Reconcile PHR README capability matrix with `phr_qa_delivery_plan.md`; stop claiming complete where the plan says not.
- P0.7 Re-enable `ContractValidationGate` validators (`kernel-core`).
- P0.8 Delete or implement Flashit `backend/agent` stub; don't leave kernel deps declared with no module.

### Phase 1 — Hardening & proof
- P1.1 Add cross-tenant contract tests to every plugin SPI.
- P1.2 Add audit-chain mutation test proving `verify()` fails on tamper.
- P1.3 Add shared idempotency contract test for `BillingLedgerPlugin` executed by PHR + Finance test suites.
- P1.4 Expand `integration-tests/phr-finance-integration` to exercise: PHR creates encounter → bills → Finance ledger posts → cross-chain audit verifies.
- P1.5 Replace PHR web `mockData.ts` with a Zod-validated typed API client per §27.
- P1.6 Consolidate `JsonUtils`; retire Gson in favor of Jackson (§12 governance).
- P1.7 Publish a one-page "KernelModule vs KernelExtension vs Plugin" decision doc (high-leverage onboarding fix).

### Phase 2 — Use-case completion & UX
- P2.1 Ship a minimal PHR patient journey end-to-end (login → dashboard → consent grant → record view → emergency access with audited break-glass).
- P2.2 Ship a minimal Finance regulator/ops view over real ledger + compliance evidence.
- P2.3 Start Aura engineering on the canonical kernel+plugin template.
- P2.4 Re-home Flashit's AI + domain logic to a real Java `KernelModule`; shrink Node gateway to transport.

### Phase 3 — Innovation & market positioning
- P3.1 Promote the human-in-loop autonomy pattern to a first-class `HumanApprovalPlugin` (Finance >$100k + PHR break-glass become one SPI).
- P3.2 Ship "swap fraud model at runtime" hot-reload demo backed by durable audit — this is a defensible, showable differentiator.
- P3.3 Migrate AEP, Data-Cloud, YAPPC onto kernel + plugins. This closes prior P0s (PlaceholderAgent, auth disabled, tenant fallback, governance simulation) by construction.
- P3.4 Package the regulated rule library (SOX / HIPAA / GDPR / PCI-DSS / Nepal 2081 / Nepal 2075) as an explicit, versioned artifact with override/overlay semantics.
- P3.5 Publish the PHR↔Finance↔shared-audit thin slice as the flagship demo; this is the market story others cannot easily reproduce.

---

## 15. Final Recommendation on the Kernel+Plugin Product Model

**Is kernel+plugin the right model to build products like PHR, Finance, Aura, Flashit on top of?** **Yes — structurally.** The SPIs are clean, the lifecycle is Promise-based and non-blocking, the registry decision (D4) is sound, the plugin framework supports hot-reload/dependency resolution/tier enforcement, and the six shared plugins map directly to the parts every regulated vertical repeatedly rebuilds.

**Is it proven in practice today?** **Not yet.** The model has two credible adopters (Finance, PHR) and neither is end-to-end provable: Finance lacks a surface, PHR has critical compliance gaps and a mock-wired UI. Aura hasn't started; Flashit has declared the dependency but not used it; AEP/Data-Cloud/YAPPC/Audio-Video haven't adopted it at all.

**What would move it from "right idea" to "frontrunner capability"?** Three things, in order:

1. **Make the six standard plugins durable by default** (kernel-persistence bindings). In-memory reference impls as production defaults are the single biggest credibility issue.
2. **Ship one end-to-end thin slice that exercises the model's unique claim:** PHR patient record → consent → billing → Finance ledger → shared tamper-evident audit, with real UI, real tenant isolation, real persistence. One slice, fully proved.
3. **Migrate AEP, Data-Cloud, YAPPC onto the kernel.** Every critical P0 in the prior audit maps onto a kernel/plugin primitive. Doing this turns the kernel from "a framework two products use" into "the spine of the company."

Until those three are done, the kernel+plugin model is a correct bet that hasn't been cashed in.
