# End-to-End Data Cloud + Shared Libraries Correctness Audit

**Repository:** `samujjwal/ghatana`  
**Reviewed commit/tree:** `0e609f7171965dbfc156fa4b43a77cd5e26b897b`  
**Target scope:** `products/data-cloud` plus directly used Data Cloud shared libraries and shared platform surfaces  
**Requested output target in repo:** `products/data-cloud/docs/audits/end-to-end-data-cloud-correctness-shared-libraries-audit.md`  
**Execution method:** source-evidence audit through the connected GitHub repository at the exact commit tree. I did **not** run Gradle, pnpm, Playwright, Docker, database migrations, Testcontainers, browsers, or live services. Runtime/build/test-only conclusions are marked as requiring execution proof.

---

## 1. Executive Summary

### 1.1 Readiness ratings

| Dimension | Rating | Evidence-backed rationale |
|---|---:|---|
| Overall correctness | **B-** | Several prior analytics correctness blockers appear fixed, but production profile safety, stale scripts/docs, runtime-gating proof, and durable-provider proof remain release blockers. |
| Overall completeness | **B-** | Broad product surfaces exist across docs, launcher, UI, contracts, planes, deployment, and tests, but Data Fabric, Runtime Truth consistency, compatibility-route gating, and durable restart journeys are not fully proven. |
| Production readiness | **No** | The production runbook requires explicit production profile, durable stores, auth, and readiness checks, but the container image does not set `DATACLOUD_PROFILE`, and the launcher defaults to `LOCAL` when absent. |
| Mock/stub/demo risk | **Medium** | Local stack disables MSW/mock data, and analytics placeholders appear reduced, but Data Fabric docs overclaim production readiness and production-stub scanning is incomplete. |
| Reuse/DRY/source-of-truth | **B-** | Plane architecture and shared-library rules are strong, but Data Cloud vs AEP terminology remains inconsistent across docs/CI/CODEOWNERS, and platform split rules still need enforcement. |
| Shared-library boundary | **B-** | Shared platform rules exist, but platform modules such as agent/workflow/messaging/AI/governance still need a concrete dependency audit and move/split decisions. |
| Test authenticity | **B-** | Analytics unit/metrics tests exist; production/durable/browser/contract/runtime-truth proof still needs execution and additional integration coverage. |
| Safe to release? | **No** | Internal demo is reasonable with explicit preview/degraded disclosures. Unrestricted production should be blocked until P0/P1 tasks are closed. |

### 1.2 Top production blockers

| ID | Severity | Area | Finding | Required fix |
|---|---|---|---|---|
| DC-P0-001 | P0 | Production profile safety | `DataCloudLauncherSettings.resolveProfile(...)` defaults to `LOCAL` when `DATACLOUD_PROFILE` is absent, while the production Dockerfile does not set `DATACLOUD_PROFILE`. This creates a credible risk of a container starting with local/in-memory semantics if deployment config omits the profile. | Make production/container startup fail closed unless an explicit non-local profile is set, or set/validate `DATACLOUD_PROFILE=production` in all production deployment assets. |
| DC-P1-001 | P1 | Local stack correctness | `run-local-stack.sh` resolves `UI_DIR="${PRODUCT_ROOT}/ui"`, but the active UI is under `products/data-cloud/delivery/ui`. The documented local stack command can fail before proving backend/UI integration. | Update the script to `delivery/ui`, add a smoke test, and ensure docs match. |
| DC-P1-002 | P1 | Documentation source of truth | `DEVELOPER_MANUAL.md` says Data Cloud does not own higher-level orchestration and AEP integrates externally, while current canonical docs say AEP is the Action Plane runtime inside Data Cloud. | Align all docs to the plane model and mark historical content as historical. |
| DC-P1-003 | P1 | Data Fabric preview/production boundary | Data Fabric feature docs say it is ready for production and mention “52 example tests,” but product docs/developer manual describe `/fabric` as preview-only. | Either implement all live backend endpoints/tests or mark it preview and disable production exposure by Runtime Truth. |
| DC-P1-004 | P1 | UI route gating | Compatibility routes in `routes.tsx` appear to mount pages directly, while canonical routes use role/runtime gates in places. Aliases may bypass capability/role gating. | Wrap all aliases in the same `RoleProtectedRoute` and `RuntimeCapabilityRouteGate` semantics as canonical routes. |
| DC-P1-005 | P1 | CI/build/test authenticity | AEP is still modeled as a product in CI despite being the Action Plane runtime, and one CI step can echo “No integration tests configured, skipping.” | Fold/rename action-plane CI into Data Cloud component CI and fail when required integration tests are missing. |

---

## 2. Scope and Method

### 2.1 Reviewed evidence

| Area | Evidence |
|---|---|
| Product boundary and planes | `products/data-cloud/README.md` defines Data Cloud as an AI-native operational data fabric, organized by planes, with AEP as the Action Plane runtime and not a separate customer-facing product boundary. fileciteturn5file0 |
| Plane architecture | `PLANE_ARCHITECTURE.md` defines planes, surfaces, modules, runtime truth, dependency rules, target layout, and shared-platform move/split rules. fileciteturn11file1 |
| Product design | `03_data_cloud_unified_high_level_design.md` requires one coherent product, runtime-truth-gated surfaces, evidence-first automation, no demo-visible production data, and tenant-safe/policy-aware actions. fileciteturn11file2 |
| Detailed architecture | The detailed architecture requires one product boundary, centralized contracts, runtime truth, fail-closed production profiles, and UI/SDK/docs/tests aligned with runtime truth. fileciteturn10file0 |
| Contracts build | `products/data-cloud/contracts/build.gradle.kts` validates Data Cloud/AEP/Action specs and checks OpenAPI sync against runtime/platform copies. fileciteturn11file0 |
| Launcher/profile safety | `DataCloudLauncher.java` validates non-embedded profiles before creating resources, while `DataCloudLauncherSettings` defaults to `LOCAL` when no profile is set. fileciteturn12file1 fileciteturn12file14 |
| Production runbook | `RUNBOOK.md` requires production profile, durable DB/Kafka, auth, readiness, migrations, and tenant isolation checks. fileciteturn12file10 fileciteturn12file12 |
| Docker runtime | `products/data-cloud/deploy/Dockerfile` sets ports/DB/Kafka defaults but does not set `DATACLOUD_PROFILE=production`. fileciteturn12file5 |
| Local stack | `run-local-stack.sh` starts backend and UI with mocks disabled but points `UI_DIR` at `${PRODUCT_ROOT}/ui`. fileciteturn12file17 |
| UI routes | `routes.tsx` shows role/runtime-gated surfaces plus direct compatibility aliases. fileciteturn16file10 |
| Data Fabric docs | Feature docs claim production readiness and example tests while developer docs say `/fabric` remains preview-only. fileciteturn16file5 fileciteturn16file2 |
| Analytics handler | `AnalyticsHandler.java` uses direct ActiveJ `request.loadBody().then(...)`, supports trace IDs, row-limit parameters, unsupported cancellation flag, and metrics. fileciteturn15file0 |
| Analytics tests | `AnalyticsHandlerTest.java`, `AnalyticsHandlerMetricsTest.java`, and `DataCloudHttpMetricsTest.java` provide focused unit/metrics coverage. fileciteturn15file1 fileciteturn14file10 fileciteturn14file13 |
| Production placeholder scan | `scan-production-placeholders.sh` scans Data Cloud and Action Plane production paths for comment-based placeholder markers. fileciteturn13file1 |
| CI/product boundaries | Product-isolated CI still exposes an `aep` product label for `products/data-cloud/planes/action/**`. fileciteturn17file8 |
| CODEOWNERS | CODEOWNERS has Data Cloud, Action Plane, and shared SPI ownership patterns whose order needs validation. fileciteturn17file9 |

### 2.2 Excluded

- Full clone with grep over every file.
- Build/test execution.
- Browser execution.
- Docker image build.
- Live DB/Kafka/H2/Trino/Ollama/OpenAI execution.
- Security/vulnerability/license scan execution.

### 2.3 Confidence terms

| Term | Meaning |
|---|---|
| Source-proven | Exact source evidence directly shows the behavior. |
| Source-partial | Source shows structure/intent, but runtime execution is needed. |
| Documented-only | Product docs claim it; implementation/test proof was not fully inspected. |
| Unknown | Insufficient evidence in inspected files. |

---

## 3. Product Behavior and Vision Map

| Capability / use case | Expected behavior | Current evidence | Status | Gaps |
|---|---|---|---|---|
| Operational data fabric | Unified trusted data, events, governed context, intelligence, policy, and action in one product. | README and plane architecture are explicit. fileciteturn5file0 fileciteturn11file1 | Source-proven vision | Must ensure docs/CI no longer imply AEP peer product. |
| Runtime Truth | `/api/v1/surfaces` reports live/degraded/disabled/preview/unavailable state; `/api/v1/capabilities` is temporary compatibility. | README defines target and compatibility endpoint. fileciteturn5file0 | Source-partial | Need API/UI/SDK E2E proof and universal UI gating. |
| Entity storage/query | Entity CRUD, schema/history, tenant isolation, durable provider outside local. | Developer manual and runbook identify entity storage and provider expectations. fileciteturn16file7 fileciteturn12file10 | Source-partial | Need executed DB/provider/restart/tenant tests. |
| Event append/replay/stream | Durable event log, replay/streaming, tenant isolation. | Plane docs and runbook define it. fileciteturn11file1 fileciteturn12file10 | Source-partial | Need executed Kafka/EventLog durability tests. |
| Analytics/query | Query, result retrieval, aggregate/explain/plan, bounded rows, trace IDs, no sensitive error leaks. | Handler and unit/metrics tests exist. fileciteturn15file0 fileciteturn15file1 | Improved from prior audit; still partial | Submit path must defensively enforce row cap; timeout constant must be enforced or removed. |
| Action Plane | Pipelines, agents, reviews, learning powered by AEP runtime under Data Cloud. | README/plane docs define boundary. fileciteturn5file0 fileciteturn11file1 | Source-proven architecture | CI/docs still use AEP as product label. |
| Data Fabric | Admin/fabric topology, connectors, metrics. | Developer manual says `/fabric` preview-only; feature docs say ready for production. fileciteturn16file2 fileciteturn16file5 | Inconsistent | Must either implement live production backend or disable/preview-gate. |
| Governance/trust | Policy, classification, audit, retention, redaction, tenant isolation. | Runbook defines tenant isolation verification and production checks. fileciteturn12file12 | Source-partial | Need destructive action and audit-event E2E proof. |
| Local developer loop | Backend + UI together with mock data disabled. | Script disables MSW/mock data but references wrong UI directory. fileciteturn12file17 | Incorrect | Fix path and add smoke test. |

---

## 4. Plane Architecture Audit

| Plane | Path(s) / owner | Assessment | Severity | Required fix |
|---|---|---|---|---|
| Experience | `delivery/ui`, `delivery/sdk`, docs/scripts | UI and docs exist; route gating must be normalized across compatibility aliases; local-stack script points to stale UI path. | P1 | Fix script path and enforce role/runtime gates on all routes and aliases. |
| Contract | `contracts/openapi/*`, SDK generation | Contracts are centralized and validated; AEP/action/platform spec copies are sync-checked. | P2 | Keep compatibility copies temporary, define retirement date, and prove Data Cloud SDK generation consumes `data-cloud.yaml`. |
| Runtime Truth | `/api/v1/surfaces`, `/api/v1/capabilities` | Strong target in docs, but universal UI/API/SDK consumption not fully proven. | P1 | Add SurfaceRegistryCompositionTest, UI gating tests, and generated route/action gate drift checks. |
| Data | `planes/data/entity` | Owned by Data Cloud; durable provider and tenant proof documented. | P1 | Execute provider/restart/tenant tests and persist artifacts. |
| Event | `planes/event/core`, `planes/event/store` | Durable event plane documented. | P1 | Execute Kafka/EventLog append/replay/stream durability and tenant tests. |
| Context | `planes/context` | README calls it placeholder and ownership boundary. | P2 | Define minimal v1 context/provenance/memory scope and contracts. |
| Intelligence | `planes/intelligence/analytics`, `feature-ingest` | Analytics handler/test evidence exists; AI/ML-native features are partly documented. | P1 | Enforce analytics timeout/row limits; add contract/UI tests; define feature/model metadata E2E. |
| Governance | `planes/governance/core` | Runbook has strong tenant/auth guardrails. | P1 | Add destructive governance action tests, audit event assertions, and policy/retention E2E. |
| Action | `planes/action/*` | Correctly positioned as runtime implementation behind Action Plane. | P1/P2 | Rename CI/product labels from AEP to Data Cloud Action Plane; verify no core → action implementation dependencies. |
| Operations | `planes/operations`, runbook, deploy | Runbook is strong; Docker profile default is unsafe if deployment forgets profile. | P0 | Fail closed unless explicit production profile/durable deps are configured. |

---

## 5. Inventory Summary

### 5.1 Documentation inventory

| Document | Status | Issue |
|---|---|---|
| `products/data-cloud/README.md` | Canonical and aligned with plane model. | Keep as top-level product source. |
| `docs/architecture/PLANE_ARCHITECTURE.md` | Canonical target architecture. | Enforce with CI/ArchUnit/import checks. |
| `docs/product/02_data_cloud_unified_detailed_architecture.md` | Strong detailed architecture. | Use as acceptance basis for runtime truth and production fail-closed gates. |
| `docs/product/03_data_cloud_unified_high_level_design.md` | Strong HLD, UX/API/test goals. | Ensure implementation matches HLD routes/gates. |
| `DEVELOPER_MANUAL.md` | Useful but partially stale. | Align AEP/Action Plane ownership language and local stack paths. |
| `docs/operations/RUNBOOK.md` | Strong production guardrails. | Make Docker/Helm/K8s enforce these defaults. |
| `delivery/ui/src/features/data-fabric/*` | Overclaims production readiness. | Mark preview or implement backend+tests. |
| Historical AEP migration docs | Mostly historical. | Keep clearly historical; do not use as canonical. |

### 5.2 Contract inventory

| Contract | Status | Issue |
|---|---|---|
| `contracts/openapi/data-cloud.yaml` | Canonical Data Cloud REST contract per README. | Need prove route drift checks and SDK consume only canonical path. |
| `contracts/openapi/action-plane.yaml` | Canonical Action Plane contract. | Keep distinct from runtime implementation internals. |
| `contracts/openapi/aep.yaml` | Compatibility contract. | Keep sync only temporarily and add retirement criteria. |
| Platform AEP copy | Sync-checked in contracts build. | Avoid letting platform copy become source of truth. |

### 5.3 UI/action inventory highlights

| UI / action surface | Status | Issue |
|---|---|---|
| Home/Data/Query/Pipelines/Trust/Operations | Broadly present by docs/routes. | Need full E2E against live backend. |
| Compatibility routes | Present. | Must not bypass route/capability/role gates. |
| Data Fabric `/fabric` | Preview per developer manual. | Feature docs claim production readiness. |
| Alerts | Launcher-backed per developer manual. | Need triage/ack/resolve/stream E2E proof. |
| Query analytics | Handler and tests exist. | Need contract/UI E2E; enforce row cap and timeout. |

### 5.4 Backend/data/event/storage inventory highlights

| Backend/data item | Status | Issue |
|---|---|---|
| Launcher profile validation | Non-embedded profiles validate before resource creation. | Missing profile defaults to local. |
| HTTP/gRPC transport startup | Explicit transport required. | Good; keep tests. |
| Entity/event stores | Local/durable modes documented. | Need executed durable/restart tests. |
| Analytics handler | Improved; no visible prior async P0. | Timeout constant and submit row cap need enforcement. |
| Runtime truth/capabilities | Documented and partially scripted. | Need universal UI/SDK consumption proof. |

### 5.5 Shared-library inventory highlights

| Shared library / dependency | Use | Boundary assessment |
|---|---|---|
| `platform:java:observability` | Launcher metrics/traces. | Valid shared infra; keep generic. |
| `platform:java:config/http/security/audit/governance` | Launcher and production guardrails. | Valid shared infra; ensure Data Cloud-specific policy stays in Data Cloud. |
| `platform:java:ai-integration` | AI/model/feature integration. | Plane architecture explicitly says move query assist/schema inference/action suggestions into Data Cloud Intelligence if product-specific. |
| `platform:java:workflow`, `agent-*`, `messaging`, `data-governance` | Shared action/event/governance primitives. | Must be audited for Data Cloud/Action semantics that should move into `products/data-cloud`. |
| `@ghatana/theme`, design system packages | UI theme/shell. | Valid shared UI infra; route/page-specific logic should remain in Data Cloud UI. |

---

## 6. Requirement-to-Implementation Traceability Matrix

| Capability | UI | API/backend | Data/store | Tests | Observability | Status |
|---|---|---|---|---|---|---|
| Runtime Truth | Status banner/gates expected | `/api/v1/surfaces`, compatibility `/capabilities` | dependency probes | Needed | Runtime state/audit | Partially implemented/proven |
| Entity CRUD | `/data`, `/entities` | entity handlers | EntityStore | Needed DB/provider tests | metrics/logs | Source-partial |
| Event append/query | `/events` | event handlers/SSE | EventLogStore | Needed durable tests | streams/metrics | Source-partial |
| Analytics query | `/query` | `AnalyticsHandler` | analytics engine/result store | Unit/metrics tests exist | metrics/trace ID | Partially complete |
| Analytics cancellation | query cancel action if exposed | `handleAnalyticsCancelQuery` | engine cancellation if supported | Unit tests exist | trace/error | Capability alignment needed |
| Data Fabric | `/fabric`, `/connectors` | fabric/connector APIs claimed | connector/fabric store | Docs say examples | unknown | Preview/inconsistent |
| Pipelines/workflows | `/pipelines` | workflow capability | execution snapshots/logs | Need provider/restart tests | logs/metrics | Partially proven |
| Governance/trust | `/trust` | governance handlers | audit/governance store | Needed | audit events | Partial |
| Production startup | Docker/Helm/K8s | launcher validation | durable stores/settings | Some docs/tests claimed | readiness/metrics | P0 gap due profile default |
| Shared library boundaries | n/a | Gradle/import graph | n/a | Needed ArchUnit/scripts | CI checks | Partial |

---

## 7. End-to-End Journey Audit

| Journey | Expected outcome | Actual / evidence | Correct? | Complete? | Severity | Required tests |
|---|---|---|---:|---:|---|---|
| Production container startup | Fail closed unless production profile, durable DB/Kafka/settings, and auth are configured. | Runbook requires this, but Dockerfile omits profile and launcher defaults absent profile to LOCAL. | No | No | P0 | container startup tests, Helm/K8s config tests, launcher profile tests |
| Local backend + UI stack | `run-local-stack.sh` starts backend and `delivery/ui`, disables mocks, and verifies both. | Mocks disabled, but script points to `${PRODUCT_ROOT}/ui`. | No | No | P1 | shellcheck + smoke E2E |
| Open Data Cloud UI | Runtime-truth-gated shell and core routes. | Routes exist; compatibility aliases may mount pages directly. | Partial | Partial | P1 | route matrix with role/capability gates |
| Run analytics query | Tenant-required query, row bounded, trace/correlation, stable errors. | Handler supports tenant, trace, unit/metrics tests; submit path trusts engine for row limit and timeout not proven. | Partial | Partial | P1 | API contract + large-result + timeout tests |
| Cancel analytics query | Only shown when supported; backend and capability registry agree. | Handler defaults unsupported; tests cover 501 and supported mode; UI/OpenAPI proof missing. | Partial | Partial | P1/P2 | UI/API/capability tests |
| Create/update entity | Persist tenant-isolated data. | Docs/manual/runbook support; runtime not executed. | Unknown | Unknown | P1/P2 | DB integration + Playwright journey |
| Append/replay event | Durable event survives restart and remains tenant-scoped. | Docs/manual/runbook support; runtime not executed. | Unknown | Unknown | P1 | Kafka/EventLog integration |
| Data Fabric | Either real fabric metrics or clear preview boundary. | Docs conflict: preview-only vs production-ready. | No | No | P1 | production-off feature gate + docs tests |
| Governance destructive action | Confirmed, authorized, audited, tenant-safe. | Runbook has tenant isolation; exact destructive flow proof not inspected. | Partial | Partial | P1/P2 | API E2E + audit-event assertions |
| Action Plane runtime | Native Data Cloud surface, not peer AEP product. | Docs align; CI/product labels still use AEP as product. | Partial | Partial | P1/P2 | boundary CI + docs/CI rename checks |

---

## 8. Findings and Remediation Plan

| ID | Priority | Area | Finding | Evidence | Required fix | Acceptance criteria | Tests required |
|---|---|---|---|---|---|---|---|
| DC-P0-001 | P0 | Production startup | Production/container startup can silently default to local profile if `DATACLOUD_PROFILE` is absent. | Launcher defaults missing profile to LOCAL; Dockerfile omits `DATACLOUD_PROFILE`; runbook requires production profile. fileciteturn12file14 fileciteturn12file5 fileciteturn12file10 | Require explicit profile for container/non-dev deployments; set `DATACLOUD_PROFILE=production` in production Helm/K8s/Terraform; fail closed if profile missing outside local dev. | Production image cannot start with local/in-memory semantics unless explicitly running local dev image/profile. | launcher unit tests; container smoke; Helm/K8s config tests |
| DC-P1-001 | P1 | Scripts/local dev | `run-local-stack.sh` points to `${PRODUCT_ROOT}/ui` instead of `delivery/ui`. | Script evidence. fileciteturn12file17 | Change UI dir to `products/data-cloud/delivery/ui`; update docs; add smoke check. | `bash products/data-cloud/scripts/run-local-stack.sh` starts backend and UI from clean checkout. | shellcheck; local stack smoke |
| DC-P1-002 | P1 | Docs/source of truth | Developer manual contradicts canonical Data Cloud/Action Plane boundary. | Developer manual says Data Cloud does not own higher-level agent orchestration; README/architecture says AEP is Action Plane runtime. fileciteturn16file7 fileciteturn5file0 fileciteturn11file1 | Rewrite manual to match plane architecture; move historical wording to historical migration docs. | No active docs describe AEP as peer product or external product boundary. | doc truth scan |
| DC-P1-003 | P1 | Data Fabric | Data Fabric feature docs overclaim production readiness while active manual marks `/fabric` preview-only. | Feature docs and developer manual conflict. fileciteturn16file5 fileciteturn16file2 | Either implement live APIs/tests or mark preview and production-disable through Runtime Truth. | No demo/preview feature is presented as production-live. | UI feature-gate tests; doc truth tests |
| DC-P1-004 | P1 | UI authorization/gating | Compatibility aliases may bypass role/runtime gates. | Route snippet shows direct alias elements. fileciteturn16file10 | Ensure every compatibility alias delegates through same gate stack as canonical route. | Route matrix proves canonical and alias paths have identical auth/capability behavior. | route unit tests; Playwright role/capability tests |
| DC-P1-005 | P1 | Analytics performance/correctness | Analytics submit path passes `_rowLimit` to engine but does not defensively cap `rows` before response; timeout constant appears unused. | Handler evidence. fileciteturn15file0 | Enforce local response cap and timeout/cancellation behavior, or remove unused timeout claim. | Responses cannot exceed configured max even if engine misbehaves; long-running queries fail/cancel predictably. | large-result tests; timeout tests |
| DC-P1-006 | P1 | Analytics contract/UI parity | Analytics unit tests exist, but contract/UI/generated-client parity still needs proof. | Handler/tests evidence. fileciteturn15file0 fileciteturn15file1 | Add OpenAPI route alignment and UI mapping tests for analytics responses and cancellation support. | UI, OpenAPI, handler, and SDK agree on query/result/plan/cancel behavior. | API contract tests; UI mapping tests |
| DC-P1-007 | P1 | CI/product boundary | CI still has separate `aep` product label for `products/data-cloud/planes/action/**`. | Product isolated CI evidence. fileciteturn17file8 | Rename to Data Cloud Action Plane component or fold into Data Cloud product matrix. | CI terminology matches one product boundary with internal components. | CI config tests |
| DC-P1-008 | P1 | CI authenticity | AEP/action CI can skip missing integration tests with echo instead of failing. | Gitea CI evidence. fileciteturn17file17 | Remove `|| echo` skip for required gates; explicitly mark optional tests by feature flag and fail required missing suites. | Required integration tests cannot be silently skipped. | CI dry-run / workflow lint |
| DC-P1-009 | P1 | CODEOWNERS/governance | CODEOWNERS ordering likely makes Action Plane ownership ambiguous because general Data Cloud owner can override earlier action rule. | CODEOWNERS evidence. fileciteturn17file9 | Place specific `planes/action`, `contracts`, and `shared-spi` rules after general Data Cloud rule. | GitHub owner resolution shows correct owners for action/contracts/shared-spi paths. | CODEOWNERS resolver test |
| DC-P1-010 | P1 | Runtime Truth | Runtime Truth is canonical in docs but universal UI/API/SDK consumption is not proven. | README/HLD evidence. fileciteturn5file0 fileciteturn11file2 | Add runtime truth registry composition and route/action gate generation tests. | Every gated route/action reads Runtime Truth or generated gate metadata. | SurfaceRegistryCompositionTest; UI gate tests |
| DC-P1-011 | P1 | Durable proof | Entity/event/workflow durability and restart behavior are documented but need executed proof. | Runbook/manual evidence. fileciteturn12file10 fileciteturn16file7 | Run/add Testcontainers-backed durability and restart tests. | Entity/event/workflow data survives restart in durable profiles and remains tenant-isolated. | DB/Kafka integration; restart tests |
| DC-P1-012 | P1 | Browser E2E portability | Playwright config uses Windows-specific Gradle command/cwd. | Playwright config evidence. fileciteturn12file8 | Make commands cross-platform or script-backed. | E2E runs on Linux CI, macOS, and Windows. | Playwright CI on Linux |
| DC-P1-013 | P1 | Production mock/stub scan | Placeholder scanner only catches comment markers and a narrow set of words. | Scanner evidence. fileciteturn13file1 | Extend scanner to production `return []/null/true`, static JSON live data, console logging, hardcoded demo data, mock imports, fixture imports, and UI mock flags. | Production placeholder scan catches all rubric-prohibited patterns with narrow allowlist. | scanner unit tests; CI gate |
| DC-P2-001 | P2 | Test coverage threshold | Launcher coverage gate allows 50% instruction coverage. | Build file evidence. fileciteturn13file14 | Raise thresholds for critical modules and require branch coverage for changed code. | Critical launcher/handlers/guards meet release coverage target. | Jacoco verification |
| DC-P2-002 | P2 | Contract compatibility debt | `aep.yaml`, `action-plane.yaml`, runtime, and platform spec copies are sync-checked but still duplicated. | Contracts build evidence. fileciteturn11file0 | Add retirement plan for compatibility specs and keep platform copy non-canonical. | Single canonical contract remains after callers migrate. | OpenAPI sync and migration tests |
| DC-P2-003 | P2 | Shared platform split | Plane architecture lists platform modules to move/split, but concrete import inventory was not proven. | Shared platform review rules. fileciteturn11file1 | Inventory Data Cloud imports from agent/workflow/messaging/AI/governance/platform-contracts and split product semantics into Data Cloud. | Generic platform libs contain only reusable infrastructure; Data Cloud semantics live in Data Cloud planes/extensions. | dependency graph + ArchUnit |
| DC-P2-004 | P2 | Root workspace scripts | Root package scripts use product-level UI globs that may miss nested Data Cloud `delivery/ui` unless package filters compensate. | Root scripts evidence. fileciteturn15file2 | Audit root `build`, `dev`, `test:ui`, `typecheck` filters for nested Data Cloud UI. | Data Cloud UI is always included in root UI gates. | script tests |
| DC-P2-005 | P2 | Historical docs | Some general build/kernel docs still mention AEP as standalone product or old paths. | Build guide evidence. fileciteturn17file2 | Mark historical or update to Data Cloud Action Plane. | Active docs use one product boundary. | doc truth check |

---

## 9. Production Mock/Stub/Shortcut Audit

| Area | Status | Required action |
|---|---|---|
| Analytics placeholders | Prior `Promise.ofBlocking` and `FIXME` issues appear resolved in inspected source; direct ActiveJ chaining and tests exist. | Keep static scan and contract tests. |
| Analytics cancellation | Unsupported by default and unit-tested as capability-consistent; UI/OpenAPI proof still needed. | Do not show cancel unless Runtime Truth says supported. |
| Local in-memory profile | Valid for local dev only; dangerous if production container defaults there. | P0 fail-closed profile fix. |
| Data Fabric | Feature docs can create false production confidence. | Preview-gate or implement production endpoints/tests. |
| Production placeholder scan | Existing script is good start but too narrow. | Extend scan patterns and make release gate mandatory. |

---

## 10. Security, Privacy, Governance, and Tenant Isolation

| Area | Assessment | Required fix |
|---|---|---|
| Auth/profile guardrails | Strong runbook and launcher validation for non-embedded profiles; unsafe default profile remains. | P0 explicit profile requirement. |
| Tenant isolation | Runbook documents strict tenant isolation verification and tests. | Execute and publish provider/HTTP-level test evidence for this commit. |
| Insecure/local mode | Local/embedded exceptions are documented. | Ensure container/production cannot accidentally use local/insecure defaults. |
| Secrets | Dockerfile avoids default DB credentials. | Verify Helm/K8s/Terraform secrets references. |
| Governance/destructive actions | Documented but not fully inspected. | Add purge/redaction/retention API E2E with audit assertions. |

---

## 11. Observability and Operability

| Flow | Logs | Metrics | Traces/correlation | Gaps |
|---|---|---|---|---|
| Analytics query | Server logs sanitized errors. | Metrics tests exist. | Trace ID included in response. | Add API contract tests proving trace/correlation shape. |
| Startup/readiness | Runbook defines health/ready/live/metrics checks. | Metrics endpoint documented. | Runtime truth expected. | Container/startup profile P0; execute readiness tests. |
| Runtime Truth | Target docs are strong. | Unknown. | Unknown. | Surface registry composition and UI/SDK gating proof. |
| Durable recovery | Runbook includes recovery notes. | Load suite artifacts documented. | Unknown. | Execute load/restart/recovery suites for exact commit. |

---

## 12. Performance and Scalability

| Area | Risk | Required fix |
|---|---|---|
| Analytics large results | Submit path depends on engine honoring `_rowLimit`; response does not cap defensively. | Sublist/cap rows before response and assert `truncated`. |
| Analytics timeout | `QUERY_TIMEOUT_MS` constant appears defined but not proven enforced. | Enforce timeout or remove claim; add timeout tests. |
| Local/dev stack | Broken UI path blocks fast feedback loop. | Fix script and add smoke test. |
| Durable load | Runbook describes load suite. | Execute suite and store results as CI artifact. |
| UI E2E | Windows-only Playwright backend command can block Linux CI. | Make cross-platform. |

---

## 13. Test Correctness and Coverage

| Test area | Existing evidence | Gap |
|---|---|---|
| Analytics unit tests | `AnalyticsHandlerTest.java` covers engine absent, valid/invalid JSON, missing/blank query, invalid limit, engine error, tenant, caps, cancellation. fileciteturn15file1 | Add route-level API/OpenAPI/UI client tests. |
| Analytics metrics tests | Metrics tests exist. fileciteturn14file10 | Add trace/correlation and failure-envelope contract tests. |
| Production startup tests | Runbook claims tests; exact execution not performed. | Execute under exact commit and add container/Helm tests. |
| UI route tests | Some route specs/docs exist. | Add canonical-vs-alias gate parity matrix. |
| Data Fabric tests | Feature docs say examples, not necessarily integrated tests. | Replace example-only claims with real integrated tests or preview flag. |
| Durable DB/Kafka tests | Runbook references provider/load tests. | Execute and publish artifacts for release. |
| Coverage gates | Launcher threshold 50%. | Raise for critical paths and changed code. |

---

## 14. Production Readiness Gate

| Gate | Verdict |
|---|---|
| Ready for production | **No** |
| Ready for internal demo | **Yes, if preview/degraded surfaces are clearly disclosed** |
| Ready behind feature flags | **Partially** |
| Critical blockers | DC-P0-001 production/container profile default risk |
| Minimum release fixes | Close all P0/P1 items, run build/test/E2E/durable suites, commit new audit and TODO files, enforce no-stub scan and runtime-truth gates |
| Shared-library refactors required before release | At minimum, inventory platform/shared imports and move/split product-specific Data Cloud/Action semantics |
| Contract migrations required before release | Keep AEP compatibility specs sync-checked; add retirement plan and prove Data Cloud SDK canonical generation |

---

## 15. Final Checklist

- [ ] Data Cloud product boundary uses planes/surfaces/modules/runtime truth everywhere.
- [ ] AEP is described only as Action Plane runtime implementation, not peer product.
- [ ] Production image cannot start with local/in-memory defaults accidentally.
- [ ] Runtime Truth gates all UI routes/actions, including compatibility aliases.
- [ ] Data Fabric is preview-gated or fully implemented/tested.
- [ ] Analytics enforces response caps, timeout behavior, trace/error contract, and UI/OpenAPI parity.
- [ ] Durable entity/event/workflow restart and tenant isolation tests pass.
- [ ] Production placeholder/mock/stub scanner covers all rubric patterns.
- [ ] CODEOWNERS correctly resolves Data Cloud, Action Plane, contracts, and shared SPI owners.
- [ ] CI no longer treats AEP as a separate product boundary.
- [ ] Local-stack and Playwright commands work cross-platform.
- [ ] New exact-commit audit and TODO files are committed under `products/data-cloud/docs/audits/`.
