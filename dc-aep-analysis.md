# Data Cloud + Shared Libraries Audit

**Repo:** `samujjwal/ghatana`
**Target commit:** `4b9fff1bf67dd3b75ca393eb6b40a1a7014f8a9b`
**Prompt basis:** root-cause production-readiness + cleanup audit template you provided. 

I verified the target commit is accessible as `refactor 42`.  I inspected the Data Cloud product, canonical docs, OpenAPI contract, launcher/security/runtime surfaces, representative handlers, tests, and cleanup artifacts. I did **not** execute Gradle, pnpm, Playwright, or CI gates, so build/test status is **Not verified from current execution**.

---

## 7.1 Production Readiness Verdict

**Production ready:** No
**Confidence:** Medium-high for architecture/code review; lower for runtime behavior because I did not run builds/tests.
**Highest-risk area:** the trust-critical mutation path: tenant resolution → auth/authz → policy → idempotency → transaction → audit → provenance → runtime truth.

Data Cloud has a strong canonical direction. The README defines Data Cloud as an AI-native operational data fabric, organized by planes, with AEP as the Action Plane implementation rather than a separate product.  The plane architecture and detailed architecture docs reinforce one product boundary, clear dependency rules, centralized contracts, Runtime Truth, and production fail-closed expectations.  

However, the implementation still has production blockers: optional/no-op/fallback runtime dependencies, prefix-based security instead of contract/action-derived authorization, incomplete idempotency/transaction/deletion hardening, runtime-truth terminology drift, OpenAPI/product-contract inconsistency, and documentation/audit artifact sprawl.

Shared libraries are directionally improving: Data Cloud has explicit shared-platform review rules and reuse checks.   But shared-library abstraction is not yet fully proven production-grade because dependency scanning, generated-client migration, and product/shared boundary enforcement are still incomplete or only partially evidenced.

---

## 7.2 Root Architectural Blockers

### P0-1 Trust-critical production dependencies are still too optional/fallback-driven

**Why it matters:** Production Data Cloud must not silently run with missing auth, audit, metrics, persistence, idempotency, transaction, AI, or trace exporters.

**Evidence:** `DataCloudHttpServer` centralizes many runtime dependencies and documents optional/fallback behavior: no-op metrics, heuristic AI fallback when `CompletionService` is missing, skipped audit when `AuditService` is null, in-memory settings/idempotency fallbacks, no transaction boundaries when `TransactionManager` is null, optional trace export, optional OpenSearch/export/report/model/feature services.  The detailed architecture explicitly says production profiles must fail closed for missing security, policy, audit, durability, redaction, required dependencies, and AI completion service. 

**Target pattern:** a single production profile validator that makes unsafe states impossible outside local/test. No audit-only or no-op trust dependency in production.

**Required fix:** hard-block startup for production/sovereign/enterprise when auth providers, policy engine, durable audit, durable idempotency, durable entity/event/settings stores, metrics, tracing, redaction policy, and required AI/runtime dependencies are missing.

**Required tests:** production startup matrix; missing-dependency fail-closed tests; local/test fallback allow-list tests.

---

### P0-2 Authorization is coarse and prefix-based, not route/action/contract-derived

**Why it matters:** Data Cloud spans data, events, governance, automation, voice, AI, connectors, exports, and plugins. Prefix-based roles cannot safely represent action-level permissions.

**Evidence:** `EndpointSensitivity` classifies routes by path prefixes and method classes, with hardcoded critical/sensitive path prefixes.  `DataCloudSecurityFilter` then maps access largely by method/path/sensitivity, with broad roles such as `ADMIN`, `API_CLIENT`, and `PROCESSOR` allowed broadly.  Tests prove useful filter-level behavior, but they do not establish every route/action’s authorization contract.  

**Target pattern:** generate a route/action permission registry from OpenAPI + route manifest + product roles. Enforce the same permission contract in UI, API, SDK, and backend handlers.

**Required fix:** replace `EndpointSensitivity` as the main source of authz truth with contract-backed action permissions. Keep sensitivity as secondary metadata only.

**Required tests:** full route/action permission matrix; tenant + role + action denial/allow tests; UI cannot enable actions without backend permission.

---

### P0-3 Mutating workflows lack complete idempotency, transaction, and deletion lifecycle guarantees

**Why it matters:** Entity/event/governance/automation mutations must be retry-safe, atomic, reversible/auditable, and tenant-isolated.

**Evidence:** `DataCloudHttpServer` documents optional generic idempotency, optional entity idempotency, and optional `TransactionManager`; if absent, multi-step writes execute without transaction boundaries.  `DataLifecycleHandler` checks idempotency only when a store and idempotency key exist.  Current implementation progress artifacts also state idempotency route migration, transaction boundaries, and deletion lifecycle remain incomplete; these are cleanup/progress artifacts, not canonical source-of-truth, but they align with the code evidence.  

**Target pattern:** every mutating route declares idempotency policy, transaction boundary, deletion behavior, audit event, and retry semantics.

**Required fix:** make durable idempotency mandatory for mutating production routes; integrate transaction manager into entity, event, governance, pipeline, connector, report/export, and automation mutations; implement soft delete/archive/retention purge consistently.

**Required tests:** duplicate-submit tests, retry-after-timeout tests, crash/restart idempotency tests, rollback/failure-injection tests, deletion lifecycle tests.

---

### P1-4 Runtime Truth is canonical in docs but still has capability-era drift

**Why it matters:** UI, SDK, docs, and operators need one authoritative source of live/degraded/disabled/preview/unavailable state.

**Evidence:** README says `/api/v1/surfaces` is canonical and `/api/v1/capabilities` was removed.  The architecture scorecard still checks for a `RouteCapabilityRegistry.ts`, showing capability naming remains in active quality checks.  The OpenAPI contract still includes a `capabilities` tag. 

**Target pattern:** Runtime Truth/Surface Registry everywhere: server, OpenAPI, generated clients, UI route gating, docs, scripts, scorecards, and tests.

**Required fix:** rename compatibility code and scripts, remove capability tags/registry naming, and enforce `/api/v1/surfaces` schema parity.

**Required tests:** route/surface inventory test, disabled/degraded UI tests, runtime posture contract tests.

---

### P1-5 OpenAPI contract is not fully aligned with canonical product architecture

**Why it matters:** Contracts must be the source of truth for SDK, UI, API validation, and consumer expectations.

**Evidence:** Canonical docs say AEP is the Action Plane runtime implementation inside Data Cloud.  The OpenAPI description still says “AEP still owns broader agentic orchestration,” which conflicts with the unified product boundary.  The detailed architecture requires product-level contracts, OpenAPI validation, runtime route alignment, SDK generation, and drift prevention. 

**Target pattern:** OpenAPI describes Data Cloud planes, Action Plane, Runtime Truth, trust posture, generated clients, and fail-closed semantics without legacy AEP/product-boundary ambiguity.

**Required fix:** rewrite OpenAPI descriptions/tags/security text; align `data-cloud.yaml`, `action-plane.yaml`, and `aep.yaml`; make SDK generation and route parity mandatory CI gates.

**Required tests:** OpenAPI validation, runtime route inventory parity, generated client freshness, action-plane/aep equivalence until retirement.

---

### P1-6 AI/automation fallback behavior can overclaim trust

**Why it matters:** Canonical design requires evidence-first automation, confidence, policy, review, override, and audit.  Production AI fallback must not appear as authoritative intelligence.

**Evidence:** `DataCloudHttpServer` documents heuristic AI fallback when no `CompletionService` is configured.  OpenAPI describes “gracefully-degrading embedded AI/ML hints.”  The detailed architecture says unavailable AI completion service in production should return HTTP 503 instead of heuristic fallback. 

**Target pattern:** local/test may use heuristic fallbacks; production must either fail closed, mark surface degraded, or return explicitly non-authoritative results with confidence/evidence/fallback metadata.

**Required tests:** production AI dependency missing → 503/degraded; fallback metadata; no silent automation; human override and audit verification.

---

### P1-7 Provenance, lineage, and trust are not proven end to end

**Why it matters:** Data Cloud’s differentiator is trusted data → context → automation → audit. Every derived insight or action needs evidence.

**Evidence:** Canonical docs require provenance, freshness, lineage, memory/RAG, trust states, and evidence-first automation.   The server has handlers/plugins for lineage, context, semantic search, memory, and agents, but end-to-end provenance from source record to derived result/action is **Not verified from current code/docs** in the inspected files. 

**Target pattern:** canonical provenance envelope shared by ingestion, entity CRUD, event append, retrieval, reports, AI, and Action Plane runs.

**Required tests:** source-row/entity/event → index/retrieval/report/action lineage tests; stale-data trust-state tests; evidence rendering tests.

---

### P1-8 Connector and plugin security needs full production proof

**Why it matters:** Connectors carry credentials, residency policies, source ownership, and data ingestion risk.

**Evidence:** OpenAPI defines connector registration with `credentials`, `residencyPolicy`, schedule, and target collection.  Runtime has DataSourceRegistry and plugin install handlers, but credential redaction, sandboxing, residency enforcement, and connector provenance are **Not verified from current code/docs** in the inspected implementation.  

**Target pattern:** encrypted secret storage, redacted GET/list responses, connector sandbox policy, residency validation, source evidence, tenant-scoped sync jobs, and audited credential access.

**Required tests:** no-secret-response tests, logs/client-bundle secret scans, connector sandbox tests, cross-tenant connector denial, residency policy tests.

---

### P1-9 UI is not yet fully generated-client and runtime-truth driven

**Why it matters:** The canonical rule says UI must use generated clients and frontend adapters, not backend internals. 

**Evidence:** Current progress artifacts state API type generation infrastructure exists but migration from ad hoc types remains pending and some action-plane/aep refs need fixing; this is not canonical source-of-truth, but it is a cleanup/progress signal.  The HLD requires runtime-truth-gated UI and no fake live data. 

**Target pattern:** OpenAPI-generated types/client are mandatory; UI route manifest derives availability/actions from `/api/v1/surfaces`; no fake KPIs, placeholder charts, or dead buttons.

**Required tests:** generated-client freshness, Playwright disabled/degraded surfaces, no-placeholder-data tests, route contract tests.

---

### P1-10 Shared-library boundaries are directionally governed but not fully release-gated

**Why it matters:** Shared libraries should remain reusable cross-product primitives, not Data Cloud-specific product behavior.

**Evidence:** Plane architecture gives explicit rules for what stays in platform versus what moves/splits into Data Cloud, including candidates such as agent-core, workflow, messaging, AI integration, data governance, and contracts.  A reuse scorecard script exists for HTTP helpers, validation, serialization, policy, observability, rate limiting, and audit. 

**Target pattern:** enforce dependency direction and semantic boundaries in CI for Java, TypeScript, UI component packages, contracts, and scripts.

**Required tests:** ArchUnit/dependency graph checks; package import boundary checks; unused dependency scan; shared-library semantic scan.

---

### P2-11 Observability exists but production operational trust is incomplete

**Why it matters:** Operators need durable, actionable telemetry for ingestion, retrieval, automation, governance, and degraded state.

**Evidence:** The code has request trace, trace-span, metrics, health, alerting, and audit surfaces.   But `DataCloudHttpServer` allows no-op metrics and optional trace export.  `DataCloudSecurityFilter` emits audit fire-and-forget and skips audit if no audit service exists. 

**Target pattern:** production metrics/tracing/audit must be configured or startup fails; every sensitive operation must produce actionable trace + audit + runtime truth evidence.

**Required tests:** telemetry presence tests, audit failure behavior tests, runtime truth degradation on telemetry dependency failures.

---

### P2-12 Repository cleanup debt is actively causing audit confusion

**Why it matters:** Repeated audits will keep rediscovering historical reports, implementation plans, stale backup files, and migration residue.

**Evidence:** Search found prior audit/TODO outputs under `docs/audit`, implementation plans/progress summaries under `docs/implementation`, and a backup Gradle file under Data Cloud SDK.    These were **not used as source-of-truth** for functional findings, but they are cleanup evidence.

**Target pattern:** small canonical docs set, one current backlog, archived historical audits, no `.backup` files, no duplicate migration/task docs.

---

## 7.3 Migration / Completeness Matrix

| Surface                            | Boundary | Contracts | Tenant/Auth | Runtime Truth | Provenance | AI/Automation | Observability | Tests | Cleanup | Status     |
| ---------------------------------- | -------: | --------: | ----------: | ------------: | ---------: | ------------: | ------------: | ----: | ------: | ---------- |
| Product boundary / planes          |       🟡 |         ✅ |           ⚫ |            🟡 |         🟡 |            🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| Runtime Truth `/api/v1/surfaces`   |       🟡 |        🟡 |          🟡 |            🟡 |         🟡 |            🟡 |            🟡 |    🟡 |      🔴 | 🟡 Partial |
| Entity CRUD                        |        ✅ |        🟡 |          🟡 |            🟡 |         🟡 |             ⚫ |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| Event append/query/stream          |        ✅ |        🟡 |          🟡 |            🟡 |         🟡 |            🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| Connectors / data sources          |       🟡 |        🟡 |          🟡 |            🟡 |         🔴 |             ⚫ |            🟡 |    🔴 |      🟡 | 🟡 Partial |
| Pipeline/workflow execution        |       🟡 |        🟡 |          🟡 |            🟡 |         🟡 |            🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| Governance / retention / redaction |        ✅ |        🟡 |          🟡 |            🟡 |         🟡 |             ⚫ |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| Search / semantic retrieval / RAG  |       🟡 |        🟡 |          🟡 |            🟡 |         🔴 |            🟡 |            🟡 |    🔴 |      🟡 | 🟡 Partial |
| AI assist / voice / autonomy       |       🟡 |        🟡 |          🟡 |            🟡 |         🟡 |            🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| Reports / exports                  |       🟡 |        🟡 |          🟡 |            🟡 |         🟡 |             ⚫ |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| UI app shell / navigation          |        ✅ |        🟡 |          🟡 |            🟡 |         🟡 |            🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| Generated SDK/client               |        ✅ |        🟡 |           ⚫ |            🟡 |          ⚫ |             ⚫ |             ⚫ |    🟡 |      🟡 | 🟡 Partial |
| Shared UI components               |       🟡 |         ⚫ |           ⚫ |             ⚫ |          ⚫ |             ⚫ |             ⚫ |    🟡 |      🟡 | 🟡 Partial |
| Shared platform libraries          |       🟡 |        🟡 |          🟡 |            🟡 |         🟡 |            🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial |
| Repo docs/scripts cleanup          |       🟡 |        🟡 |           ⚫ |            🟡 |          ⚫ |             ⚫ |            🟡 |    🟡 |      🔴 | 🔴 Missing |

---

## 7.4 File-Level Gaps

| Path                                                                               | Root issue | Gap                                                                                                                                  | Required fix                                                                                                      | Tests                                 |
| ---------------------------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| `products/data-cloud/delivery/launcher/src/main/java/.../DataCloudHttpServer.java` | P0-1       | Too many optional/no-op/fallback production dependencies in one composition root.                                                    | Split runtime composition, production validator, route registration, plugin boot; fail closed outside local/test. | production profile matrix             |
| `DataCloudSecurityFilter.java`                                                     | P0-2       | Audit-only mode can pass auth failures; audit service nullable; policy-excluded tenants bypass policy; coarse roles.                 | Disallow audit-only in production; require durable audit; replace broad role logic with action registry.          | authz matrix, audit failure tests     |
| `EndpointSensitivity.java`                                                         | P0-2       | Prefix/method classifier is not sufficient as route/action source of truth.                                                          | Generate route sensitivity/action permissions from OpenAPI/manifest.                                              | route contract tests                  |
| `HttpHandlerSupport.java`                                                          | P1/P2      | Product-local helper mixes response envelopes, CORS, tenant fallback, executor creation; returns a new cached thread pool per call.  | Split helpers; use shared executor; canonical envelope; remove fallback tenant outside local/test.                | helper unit tests, resource-leak test |
| `contracts/openapi/data-cloud.yaml`                                                | P1-5       | Contract text conflicts with unified Action Plane positioning and still exposes capability-era terminology.                          | Rewrite descriptions/tags/security; regenerate clients; add drift checks.                                         | OpenAPI + SDK freshness               |
| `DataLifecycleHandler.java`                                                        | P0-3       | Critical purge idempotency is conditional on optional store/key.                                                                     | Require idempotency for critical mutations in production.                                                         | purge retry/crash tests               |
| `scripts/generate-data-cloud-architecture-scorecard.mjs`                           | P1-4       | Scorecard still checks `RouteCapabilityRegistry.ts`.                                                                                 | Rename to Runtime Truth / Surface Registry checks.                                                                | script snapshot test                  |
| `products/data-cloud/delivery/sdk/build.gradle.kts.backup`                         | P2-12      | Backup file inside product tree.                                                                                                     | Compare to active build file, then delete.                                                                        | Gradle build                          |
| `docs/audit/*20260509*`                                                            | P2-12      | Historical audit/TODO outputs pollute discovery.                                                                                     | Archive or delete; keep only current commit-scoped audit if needed.                                               | docs index check                      |
| `docs/implementation/*`                                                            | P2-12      | Progress/task docs overlap canonical docs and backlog.                                                                               | Merge into one canonical backlog or archive.                                                                      | no stale-doc references               |

---

## 7.5 Prioritized Implementation Sequence

1. **Production trust-profile hardening**
   Make auth, policy, durable audit, durable idempotency, durable stores, metrics, tracing, redaction, and required AI/runtime dependencies fail closed outside local/test.

2. **Route/action authorization registry**
   Generate route/action permissions from OpenAPI + route manifest. Replace broad prefix/role logic with explicit action gates.

3. **Mutation correctness foundation**
   Finish idempotency, transaction boundaries, delete lifecycle, retry semantics, and audit events for all mutating routes.

4. **Contract source-of-truth closure**
   Align OpenAPI with canonical Data Cloud/Action Plane model, fix capability terminology, verify route parity, regenerate clients, and gate CI.

5. **Runtime Truth convergence**
   Rename capability-era registries/scripts, enforce `/api/v1/surfaces`, and make UI/SDK/docs consume surface state consistently.

6. **Connector/security/provenance hardening**
   Add credential redaction, sandboxing, residency, sync evidence, source-row lineage, and cross-tenant connector tests.

7. **AI/automation trust controls**
   Production AI fallback must degrade/503 or expose non-authoritative fallback metadata. Add confidence, evidence, HITL, override, and audit tests.

8. **UI generated-client and design-system migration**
   Replace ad hoc API types, use generated clients, enforce runtime-truth-gated disabled states, and remove local duplicate primitives.

9. **Shared-library boundary enforcement**
   Add Java/TypeScript/package dependency scans, semantic boundary tests, and unused dependency checks.

10. **Repository cleanup and canonical docs consolidation**
    Remove backup files, archive stale audits, merge implementation progress docs, and keep one canonical documentation set.

---

## 7.6 Regression and Release Gates

Minimum release gates before production readiness:

* Exact commit reproducibility gate.
* Full Gradle build for Data Cloud planes, delivery, contracts, SDK, and integration tests.
* UI lint/type/test/build/Playwright gates.
* OpenAPI validation + route inventory parity.
* Generated client freshness gate.
* `/api/v1/surfaces` Runtime Truth contract test.
* Tenant isolation matrix across every route group.
* Role/action permission matrix across every route/action.
* Mutating-route idempotency and retry tests.
* Transaction rollback/failure-injection tests.
* Delete lifecycle tests.
* Connector credential redaction and sandbox tests.
* Source → entity/event → retrieval/report/action provenance tests.
* AI fallback/degraded-state tests.
* Audit, metrics, traces, and runtime-posture tests.
* No fake/stub/demo production data scan.
* Shared-library boundary and unused dependency scans.
* Repository cleanup validation: no `.backup`, stale audit, duplicate docs, dead routes, or duplicate runtime truth systems.

---

# Repository Cleanup Plan

| Priority | Classification   | Path                                                                 | Reason                                                                        | Evidence | Safe Fix                                                                              | Tests/Validation       |
| -------- | ---------------- | -------------------------------------------------------------------- | ----------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------- | ---------------------- |
| P0       | Delete / Archive | `docs/audit/data_cloud_audit_report_20260509_160436.md`              | Historical audit output can pollute future audits.                            |          | Move to `docs/archive/audits/` or delete after current audit is saved.                | docs index + grep      |
| P0       | Delete / Archive | `docs/audit/data_cloud_todo_list_20260509_160436.md`                 | Old TODO list from a prior unresolved commit workflow.                        |          | Merge live items into one canonical backlog, then archive/delete.                     | backlog link check     |
| P0       | Merge / Archive  | `docs/implementation/task-progress-summary.md`                       | Progress doc overlaps backlog and canonical docs.                             |          | Convert into canonical backlog entries or archive.                                    | docs index check       |
| P0       | Merge / Archive  | `docs/implementation/documented_requirements_implementation_plan.md` | Large implementation plan duplicates TODO/backlog content.                    |          | Merge current items into canonical implementation backlog.                            | no stale references    |
| P0       | Delete           | `products/data-cloud/delivery/sdk/build.gradle.kts.backup`           | Backup file in active source tree.                                            |          | Compare with active Gradle file, then delete.                                         | Gradle build           |
| P1       | Replace          | `contracts/openapi/data-cloud.yaml`                                  | Contract contains legacy product-boundary wording and capability terminology. |          | Rewrite and regenerate SDK.                                                           | OpenAPI + route parity |
| P1       | Replace          | `RouteCapabilityRegistry.ts` and scorecard checks                    | Capability naming conflicts with Runtime Truth naming.                        |          | Rename to Surface/RuntimeTruth registry.                                              | UI route tests         |
| P1       | Split / Replace  | `HttpHandlerSupport.java`                                            | Over-broad helper; ad hoc response shapes; per-call executor creation.        |          | Split response/tenant/executor utilities; reuse platform helpers where generic.       | unit + resource tests  |
| P1       | Replace          | `DataCloudSecurityFilter.policyExcludedTenants`                      | Tenant policy bypass needs explicit break-glass model.                        |          | Replace with delegated support/break-glass policy + audit.                            | security tests         |
| P2       | Split            | `DataCloudHttpServer.java`                                           | Composition root is too broad and hard to reason about.                       |          | Extract route registry, production validator, plugin composition, and feature wiring. | startup + route tests  |
| P2       | Keep, but rename | `scripts/check-reuse-scorecard.sh`                                   | Useful reuse gate, but ensure categories remain current.                      |          | Keep as CI gate after updating rules.                                                 | script CI              |
| P2       | Keep, but update | `scripts/generate-data-cloud-architecture-scorecard.mjs`             | Useful architecture scorecard but has stale capability naming.                |          | Update Runtime Truth terminology and checks.                                          | scorecard CI           |

---

## Canonical Docs Matrix

| Doc                                                                       | Keep | Merge | Archive | Delete | Notes                                                     |
| ------------------------------------------------------------------------- | ---: | ----: | ------: | -----: | --------------------------------------------------------- |
| `products/data-cloud/README.md`                                           |    ✅ |       |         |        | Keep as product entry/index.                              |
| `docs/architecture/PLANE_ARCHITECTURE.md`                                 |    ✅ |       |         |        | Canonical architecture source.                            |
| `docs/product/01_data_cloud_unified_vision_market_positioning.md`         |    ✅ |       |         |        | Keep; periodically refresh market content.                |
| `docs/product/02_data_cloud_unified_detailed_architecture.md`             |    ✅ |    🟡 |         |        | Keep; clean any stale migration contradictions.           |
| `docs/product/03_data_cloud_unified_high_level_design.md`                 |    ✅ |    🟡 |         |        | Keep; align route/runtime-truth wording.                  |
| `contracts/README.md`                                                     |    ✅ |       |         |        | Keep as Contract Plane ownership doc.                     |
| `docs/api/REST_API_DOCUMENTATION.md`                                      |      |     ✅ |         |        | Generate from OpenAPI or keep as thin reference.          |
| `docs/operations/RUNBOOK.md`                                              |    ✅ |       |         |        | Keep after script/deploy validation.                      |
| `delivery/ui/ARCHITECTURE.md` + `delivery/ui/docs/DESIGN_ARCHITECTURE.md` |      |     ✅ |         |        | Merge into one UI architecture doc.                       |
| `docs/implementation/*`                                                   |      |     ✅ |       ✅ |        | Merge current items into canonical backlog; archive rest. |
| `docs/audit/*`                                                            |      |       |       ✅ |     🟡 | Keep only latest commit-scoped audit if needed.           |
| `docs/DATA_FABRIC_PROMOTION_PLAN.md`                                      |      |    🟡 |       ✅ |        | Retain only if it is current roadmap; otherwise archive.  |

---

## Final Cleanup Checklist

* [ ] Legacy audit/TODO docs archived or deleted.
* [ ] Implementation progress docs merged into one canonical backlog.
* [ ] `.backup` files removed.
* [ ] Capability terminology replaced by Runtime Truth / Surface terminology.
* [ ] OpenAPI descriptions aligned with Data Cloud + Action Plane product boundary.
* [ ] UI generated-client migration completed.
* [ ] Mutating-route idempotency coverage completed.
* [ ] Transaction boundaries implemented and tested.
* [ ] Delete lifecycle implemented and tested.
* [ ] Shared-library boundary checks enforced in CI.
* [ ] No product-specific semantics remain in generic platform libraries unless explicitly justified.
* [ ] No production heuristic/stub/fallback path can appear as real capability.
* [ ] Build/lint/typecheck/test/contract/Playwright gates pass.
