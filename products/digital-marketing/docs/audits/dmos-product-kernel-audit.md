# DMOS Product + Kernel Capability Audit

**Repository:** `samujjwal/ghatana`  
**Target head audited:** `1577a8559f3ae6a973452465a2cedf8d52c0a0ef`  
**Execution spec:** pasted Ultra-Strict DMOS Product + Kernel Capability Audit Prompt  
**Audit date:** 2026-05-08  
**Output status:** local downloadable artifacts generated; not pushed to GitHub.

---

## 1. Executive Summary

DMOS has real product boundaries, validated Gradle modules, a product README, an ActiveJ API, React UI, PostgreSQL adapters, policy/domain packs, a kernel bridge, Google Ads adapter code, JWT-backed identity derivation, rate limiting, and several useful Testcontainers tests.

It is still **not production-ready** at this head. The highest-risk issues are production composition/wiring, Google Ads command durability, governed AI readiness, tenant isolation guarantees, canonical API contract consistency, privacy hardening, and real E2E validation.

| Gate | Status | Reason |
|---|---:|---|
| Production startup | ❌ Blocked | Production graph appears to omit durable dependencies such as `AiActionLogRepository` while services require them. |
| Google Ads connector | ❌ Blocked | Command registry is wired with in-memory connector/credential/link repositories and can fall back to an in-memory Google Ads API client. |
| AI-native strategy generation | ❌ Blocked | Production composition throws unless `GovernedAgentWorkflowService` is configured; non-prod can fall back to deterministic strategy generation. |
| Tenant/workspace isolation | ⚠️ High risk | Workspaces are tenant-scoped; campaigns and approval snapshots are workspace-scoped only. |
| Security/privacy | ⚠️ Partial | JWT/context hardening exists, but PII/token/DSAR/redaction are incomplete or inconsistently wired. |
| UI stable routes | ⚠️ MVP | Main pages exist; reporting pages intentionally show feature-unavailable placeholders. |
| Tests | ⚠️ Partial | Many unit/integration tests exist, but browser E2E uses mocked APIs and runtime composition tests are insufficient. |
| Kernel/platform boundary | ✅ Mostly sound | Product concepts generally remain in DMOS; platform backlog can stay generic. |

---

## 2. Scope and Method

The audit followed the pasted prompt and validated actual repo paths before judging quality. Inspected areas:

- `settings.gradle.kts`
- `products/digital-marketing/settings.gradle.kts`
- `products/digital-marketing/README.md`
- DMOS modules under `products/digital-marketing`
- Relevant `platform-kernel`, `platform-plugins`, `platform/contracts`, `platform/java`, and `shared-services` usage
- API, application, domain, persistence, connector, UI, and tests.

This audit generated local Markdown files. It did not modify or push to GitHub.

---

## 3. Validated Path and Module Inventory

| Area | Valid path/module | Notes |
|---|---|---|
| DMOS root | `products/digital-marketing` | Product root. |
| Product settings | `products/digital-marketing/settings.gradle.kts` | Standalone/product module registration. |
| Product README | `products/digital-marketing/README.md` | Declares product intent/status/API/UI routes. |
| Kernel core | `platform-kernel/*` | Correct path; do not use old `kernel/**`. |
| Platform plugins | `platform-plugins/*` | Audit, approval, compliance, consent, notification, risk, etc. |
| Platform contracts | `platform/contracts` | Generic contracts. |
| Platform Java | `platform/java/*` | Runtime, security, observability, workflow, governance, etc. |
| Shared services | `shared-services/*` | Cross-product services. |

### DMOS module inventory

| Module | Path | Purpose | Claimed Status | Actual Status | Issues |
|---|---|---|---|---|---|
| `dm-core-contracts` | `products/digital-marketing/dm-core-contracts` | Typed IDs, operation context, security mapping | Stable | Mostly valid | Add conversion/context contract coverage. |
| `dm-domain-packs` | `products/digital-marketing/dm-domain-packs` | Boundary/compliance packs | Stable | Partial risk | Default-deny validation appears incomplete. |
| `dm-kernel-bridge` | `products/digital-marketing/dm-kernel-bridge` | DMOS adapter over Kernel/plugin ports | Stable | Mostly valid | Needs stronger startup proof for durable ports. |
| `dm-domain` | `products/digital-marketing/dm-domain` | Aggregates, invariants, approval types, AI action model | Stable scaffold | MVP | Tenant-bearing invariants should be stronger. |
| `dm-application` | `products/digital-marketing/dm-application` | Services, orchestration, AI, command handling | MVP scaffold | Not production complete | Deterministic strategy fallback; connector command wiring concerns. |
| `dm-infra` | `products/digital-marketing/dm-infra` | In-memory dev/test adapters | Dev/test only | Acceptable if blocked in prod | Runtime guard must be proven. |
| `dm-persistence` | `products/digital-marketing/dm-persistence` | Flyway/PostgreSQL adapters | Production-ready | Partial | Missing/unused durable repos in composition; tenant scoping gaps. |
| `dm-connector-google-ads` | `products/digital-marketing/dm-connector-google-ads` | Google Ads HTTP adapter | Adapter complete | Partial | Durable credential/link/outbox wiring incomplete. |
| `dm-api` | `products/digital-marketing/dm-api` | ActiveJ servlets and composition root | MVP complete | Not production complete | Missing repo wiring, inconsistent envelopes, approval GET idempotency bug. |
| `dm-integration-tests` | `products/digital-marketing/dm-integration-tests` | Cross-module tests | Partial | Useful but incomplete | Not full API/UI/production composition proof. |
| `ui` | `products/digital-marketing/ui` | React console | MVP complete; E2E pending | Partial | E2E mocks APIs; boundary pages placeholders. |

---

## 4. Product Capability Audit

| Capability | Expected | Actual | Severity | Required Fix |
|---|---|---|---:|---|
| Workspace context | Tenant/workspace/principal/correlation/idempotency propagated | `DmosHttpContextFactory` builds context from token/headers | P1 | Add full production-context servlet tests. |
| Tenant isolation | Query-level and auth-level isolation | Workspaces query by tenant; campaigns/snapshots by workspace | P0 | Add tenant_id to critical tables or enforce tenant-workspace ownership everywhere. |
| Campaign lifecycle | CRUD/lifecycle with durable audit | API/service/repo exist | P1 | Add real servlet + Postgres + auth + idempotency integration tests. |
| Approval workflow | Submit, queue, snapshot, decide | Exists; pending GET uses write context and errors are non-canonical | P1 | Fix context flag and error helper. |
| AI action transparency | Durable AI action log | Service exists; production repo wiring gap | P0 | Wire Postgres AI action repo and test startup/roundtrip. |
| Strategy generation | Governed AI or clear disabled state | Production blocked unless workflow configured; deterministic fallback exists | P0 | Wire governed workflow or feature-disable production. |
| Google Ads launch | Durable connector/credential/link/outbox and real API client | HTTP adapter exists; command registry uses in-memory deps/fallback | P0 | Durable production wiring and fail-closed config. |
| Reporting/analytics | Real data or unavailable | Boundary pages show feature unavailable | P2 | Acceptable; keep disabled until real backend exists. |
| Security/privacy | Authz, rate limiting, PII controls, DSAR | Auth/rate limiting exist; PII/DSAR/redaction pending | P1 | Complete privacy module. |
| Observability | Logs/metrics/traces/audit/health | Telemetry exists; health no-op and redaction gaps | P1 | Real readiness + redaction policy. |

---

## 5. Frontend/UI Inventory and Audit

| UI Item | Files | Purpose | Data/API Dependency | Permission Behavior | Completeness | Issues |
|---|---|---|---|---|---|---|
| Login | `ui/src/pages/LoginPage.tsx` | Dev login or production OAuth PKCE | Auth/session config | Production provider required | Mostly complete | Docs must clarify production OAuth. |
| App shell/routes | `ui/src/App.tsx`, `ui/src/routeManifest.tsx` | Guarded product routes | Auth/capability manifests | Role + capability gating | Good | Backend must remain source of truth. |
| Dashboard | `DashboardPage` | Workspace status, approvals, AI actions | Workspace/approval/AI APIs | Authenticated | Partial | Needs non-mocked E2E. |
| Campaigns | `CampaignsPage` | Campaign create/list/lifecycle | Campaign APIs | Route capability | MVP | Backend-integrated E2E missing. |
| Approvals | `ApprovalQueuePage`, `ApprovalDetailPage` | Approval queue/decision | Approval APIs | Backend capability | MVP | API bug affects read queue. |
| AI Actions | `AiActionLogPage` | AI action log view | AI action API | Backend capability | MVP | Durable repo not wired in production. |
| Strategy | `StrategyPage` | Generate/review/approve | Strategy APIs | `dmos.strategy` | Partial | Governed AI not production wired. |
| Budget | `BudgetPage` | Generate/review/approve | Budget + strategy APIs | `dmos.budget` | Partial | Needs approval-link proof. |
| Reporting boundary | `FunnelAnalyticsPage`, `AttributionPage`, `RoiRoasPage` | Feature unavailable views | None | `dmos.reporting` | Acceptable boundary | Keep no fake metrics. |

---

## 6. Backend/API/Application Audit

| Backend Flow | Files | Expected | Actual | Severity | Required Fix | Tests |
|---|---|---|---|---:|---|---|
| Production composition | `DmosApiServer.java` | Wire every production dependency | Missing durable repo registrations appear likely | P0 | Register all production repos/services | Production boot IT. |
| Google Ads command registry | `DmosApiServer`, `DmCommandHandlerRegistry` | Durable repos + real/fail-closed client | In-memory repos/client fallback | P0 | Wire durable connector dependencies | Command integration tests. |
| Strategy generation | `DmosApiServer`, `StrategyGeneratorServiceImpl` | Governed AI workflow | Production throws if not configured; deterministic fallback otherwise | P0 | Real workflow or disabled mode | Boot + strategy API tests. |
| HTTP context | `DmosHttpContextFactory` | Production identity provider required | Null provider can fall through to dev fallback | P1 | Constructor/build fail closed | Unit and servlet tests. |
| Approval pending GET | `DmosApprovalServlet` | Read endpoints do not need idempotency key | Uses write context | P1 | `buildContext(..., false)` | Servlet tests. |
| API errors | `DmosApprovalServlet` | Canonical error envelope | `{code,message}` | P1 | Shared error envelope helper | API contract tests. |
| Rate limiting | `DmosApiRateLimiter` | Tenant-keyed and no env bypass | Implemented/tested | P2 | Platformize later | Cross-servlet tests. |
| Bootstrap validation | `ProductionBootstrapValidator` | Real default-deny validation | Skip/not implemented log | P0 | Validate rule pack contents | Bootstrap tests. |

---

## 7. Domain Model Audit

| Domain Area | Expected Rule | Actual | Severity | Required Fix |
|---|---|---|---:|---|
| Campaign | State transitions and tenant/workspace isolation | Lifecycle exists; tenant not embedded in campaign persistence | P1 | Add tenant-aware invariant or ownership precondition. |
| Approval snapshot | Immutable/auditable snapshot | Upsert updates fields/version | P1 | Choose insert-only or explicit revision history. |
| Strategy | AI result with provenance | Deterministic and AI variants exist | P1 | Label deterministic/fallback outputs. |
| Budget | Recommendation linked to approved strategy | UI attempts to use approved strategy | P1 | Enforce in backend/domain. |
| AI action log | Every AI action persisted | Service exists; production wiring missing | P0 | Durable repo + required writes. |
| Google Ads command | External side effects idempotent/audited | Handlers exist; wiring incomplete | P0 | Durable outbox + rollback tests. |

---

## 8. Persistence/Database Audit

| Area | Evidence | Expected | Actual | Risk | Severity | Fix |
|---|---|---|---|---|---:|---|
| Workspaces | `V2__create_dmos_workspaces.sql`, `PostgresWorkspaceRepository` | Tenant scoped | Has `tenant_id` and tenant query filters | Low | P2 | Add composite constraints if workspace ids become tenant-local. |
| Campaigns | `V1__create_dmos_campaigns.sql`, `PostgresCampaignRepository` | Tenant + workspace scoped | `workspace_id` only | Cross-tenant leak if auth gap | P0 | Add `tenant_id` or ownership enforcement. |
| Campaign FKs/indexes | `V16__add_foreign_key_constraints.sql` | FK/indexed | FK and workspace indexes exist | Tenant index missing | P1 | Add composite tenant/workspace indexes. |
| Approval snapshots | `PostgresApprovalSnapshotRepository` | Immutable/auditable | Upsert with version increments | Audit ambiguity | P1 | Insert-only or revision table. |
| Google Ads credentials | `PostgresDmGoogleAdsCredentialRepository` | Encrypted, pooled, wired | Encrypts but uses single `Connection`; not wired | Runtime/concurrency risk | P0 | DataSource-backed + production wiring. |
| AI action log | Production composition | Durable repo wired | Missing from server registration | Startup/runtime failure | P0 | Register repo. |
| Migration tests | `FlywayMigrationValidationTest` | Migration proof | Exists | Good but CI unknown | P1 | Ensure Docker suite runs. |
| Static proof | `ProductionPersistenceWiringProofTest` | Runtime proof | File/class checks | False confidence | P1 | Add real boot test. |

---

## 9. Integration/Connector Audit

| Flow | Files | Expected | Actual | Ready? | Severity | Fix |
|---|---|---|---|---:|---:|---|
| Google Ads create | `HttpDmGoogleAdsCampaignApiClientAdapter` | Real HTTP create | OkHttp adapter exists | Partial | P1 | Add retry/backoff and structured errors. |
| Google Ads pause/rollback | Adapter + command handlers | Idempotent rollback | Adapter supports pause | Partial | P1 | Add rollback state tests. |
| Credentials | Credential repo + server | Encrypted durable token storage | Repo exists but not wired | No | P0 | Wire durable credential repo. |
| Connector enablement | Connector repo | Durable enable/disable | In-memory in registry | No | P0 | Durable connector config repo. |
| External failure | Adapter tests | Normalize 429/5xx/timeouts | Basic tests only | Partial | P1 | Expand failure matrix. |

---

## 10. AI/Agent/Automation Audit

| Flow | Files | Real/Stub/Deterministic | Human Review | Audit Trail | Severity | Fix |
|---|---|---|---|---|---:|---|
| Strategy generation | `StrategyGeneratorServiceImpl` | Governed AI if available, deterministic fallback otherwise | Submit/approve exists | Audit on deterministic save | P0 | Production workflow or disabled mode. |
| AI parse fallback | `StrategyGeneratorServiceImpl` | Silent deterministic fallback on parse failure | Not explicit | Debug log only | P1 | Persist fallback reason and show provenance. |
| AI action log | `AiActionLogServiceImpl`, repo wiring | Service but production repo missing | UI exists | Intended | P0 | Durable repo and mandatory writes. |
| Budget recommendation | Budget service/page | Likely deterministic/business logic | Submit/approve | Needs proof | P1 | Provenance + approval-link tests. |
| Website/content generation | API routes | Not fully proven | Should be reviewable | Needs proof | P1 | Inspect/test each path before release. |

---

## 11. Security, Privacy, and Governance Audit

| Area | Risk | Actual | Severity | Fix |
|---|---|---|---:|---|
| JWT identity | Tenant spoofing | Token is validated and tenant mismatch rejected if claim exists | P2 | Require tenant claim in production or document policy. |
| HTTP context | Accidental dev fallback | Production null provider can fall back | P1 | Fail closed. |
| Capability enforcement | UI/API drift | Backend capability resolver exists | P1 | Route-capability contract tests. |
| Tenant isolation | Cross-tenant workspace guessing | Campaign/snapshot rows lack tenant | P0 | Tenant-scoped DB/service enforcement. |
| PII/token privacy | Raw PII/token leakage | PII/DSAR/redaction pending | P1 | Complete privacy hardening. |
| Observability privacy | Sensitive IDs in spans | Principal/idempotency attributes present | P1 | Redaction/hash policy. |
| Governance | Fake default-deny validation | Validation skip log | P0 | Real policy validation. |

---

## 12. Observability and Operability Audit

| Flow | Logs | Metrics | Traces | Audit | Gaps | Fix |
|---|---|---|---|---|---|---|
| API requests | Some | Rate limiter duration | `DmosTelemetry` | Mutations | Approval errors lack correlation | Shared error/obs wrapper. |
| Campaign lifecycle | Some | Business metrics | Some | Kernel audit | Need full trace through command/connector | E2E trace tests. |
| Approvals | Some | Metrics injected | Some | Approval plugin | Envelope/context bugs | Shared approval timeline. |
| AI generation | Some | TBD | Some | AI log intended | Durable AI log missing | Generic AI audit envelope. |
| Google Ads | Adapter errors | TBD | Not fully proven | Command audit | Retry/rate/credential visibility | Connector observability. |
| Health/readiness | No-op | Unknown | Unknown | N/A | `BridgeHealthIndicator.noOp()` | Real readiness checks. |

---

## 13. Performance and Scalability Audit

| Area | Risk | Evidence | Severity | Fix |
|---|---|---|---:|---|
| JDBC blocking | Event-loop blocking | `Promise.ofBlocking` used | P2 | Ensure bounded executor sizing. |
| Google Ads HTTP | Retry storms/thread saturation | Fixed blocking pool | P1 | Rate limit/backoff/circuit breaker. |
| Campaign list | Pagination | Large-data test exists | P2 | Run in CI/schedule; add EXPLAIN checks. |
| UI bundle | Eager route imports | Route manifest imports pages | P2 | Lazy-load heavy/boundary pages. |
| Approval queue | Large queues | Pending list APIs | P2 | Enforce pagination/filtering. |
| AI action log | Large logs | Durable query unproven | P1 | Add indexes/pagination/load tests. |

---

## 14. Test Quality Audit

| Capability | Existing Tests | Weakness | Required Test Type |
|---|---|---|---|
| Campaign API | Servlet tests, repository IT, lifecycle IT | Many servlet tests use dev constructor/headers | Production-mode servlet + API/DB IT |
| Approval API | Servlet tests | Miss idempotency/read bug and canonical envelope | Contract + production-mode tests |
| Rate limiting | `DmosApiRateLimiterTest` | Good but not all servlets | Cross-servlet integration |
| Bootstrap | `ProductionBootstrapValidatorTest` | Name-based and incomplete pack validation | Real policy/bootstrap IT |
| Persistence | Testcontainers repos/migrations | Tenant isolation not fully proven | Cross-tenant negative tests |
| Google Ads adapter | MockWebServer tests | No retry/rate/timeout matrix | Expanded connector tests |
| Command registry | Handler map test | No real command execution | Durable command IT |
| UI E2E | Playwright fixtures | Mocked APIs | Non-mocked backend E2E |
| Production wiring | Static proof test | File existence only | Runtime production graph boot |

---

## 15. Production Mock/Stub/Shortcut Audit

| File | Evidence | Production Reachable? | Critical? | Severity | Action |
|---|---|---:|---:|---:|---|
| `DmosApiServer.java` | In-memory connector/credential/link repos in command registry | Yes | Yes | P0 | Wire durable repos. |
| `DmosApiServer.java` | In-memory Google Ads API client fallback | Yes unless blocked | Yes | P0 | Fail closed when enabled/misconfigured. |
| `DmosApiServer.java` | `BridgeHealthIndicator.noOp()` | Yes | Ops | P1 | Real health indicator. |
| `StrategyGeneratorServiceImpl.java` | Deterministic fallback | Yes in non-prod; prod blocked | AI | P1 | Provenance and feature gate. |
| `ProductionBootstrapValidator.java` | Default-deny validation skipped | Yes | Governance | P0 | Implement validation. |
| `ui/e2e/fixtures.ts` | All APIs mocked | Test only | QA | P1 | Add real E2E lane. |
| `DmosApprovalServlet.java` | Non-canonical error body | Yes | API | P1 | Shared envelope. |

---

## 16. Duplicate / Source-of-Truth Audit

| Duplicate Area | Risk | Canonical Owner | Decision | Tests |
|---|---|---|---|---|
| Error envelope helpers | Client/API drift | Shared DMOS helper now; platform later | Merge | Contract tests |
| Context builders/test constructors | Tests bypass production auth | `DmosHttpContextFactory` | Centralize | Production-mode tests |
| Feature flags | Generated Java/TS drift | Manifest | Keep/codegen | CI drift check |
| Connector wiring | In-memory vs durable setup split | Composition root + connector module | Fix | Startup + command IT |
| Persistence proof | Static vs runtime proof | Runtime graph boot | Replace/add | Production boot IT |
| Route/capability mapping | UI/backend drift | Shared manifest/codegen | Platform candidate | Contract test |

---

## 17. Kernel/Platform Usage Audit

| Capability | Location | Used By | Correct? | Missing? | Leakage? | Issue |
|---|---|---|---:|---:|---:|---|
| Kernel bridge ports | `platform-kernel/*`, `dm-kernel-bridge` | Auth/audit/health | Mostly | Startup verifier | No | Health no-op/runtime proof gap. |
| Human approval | `platform-plugins/plugin-human-approval` | Approval workflow | Mostly | Timeline/history | No | DTO/error consistency gaps. |
| Audit trail | `platform-plugins/plugin-audit-trail` | Mutations/AI | Partial | AI audit envelope | No | Ensure all AI/connector actions logged. |
| Compliance | `platform-plugins/plugin-compliance` | Policy checks | Partial | Policy validation harness | No | Default-deny validation incomplete. |
| Observability | `platform/java/observability` | Metrics/traces | Partial | Redaction policy | No | Sensitive attribute risk. |
| Security | `platform/java/security` | JWT/context | Partial | Trusted context helper | No | DMOS route capability resolver can drift. |
| Testing | `platform/java/testing` | ActiveJ/Testcontainers | Good partial | Composition proof harness | No | Static tests insufficient. |
| Connector/runtime | Not generic enough | Google Ads commands | Weak | Connector framework | No | Product-specific wiring too fragile. |

---

## 18. Kernel/Platform Enhancement Backlog

See `dmos-kernel-platform-enhancement-todos.md`. Valid generic candidates:

1. Production composition verifier.
2. Generic connector runtime framework.
3. Token vault / OAuth credential abstraction.
4. Generic AI action audit envelope.
5. Standard API error envelope middleware.
6. Policy pack validation harness.
7. PII-safe observability redaction policy.
8. Multi-tenant repository/API test harness.
9. Generic idempotent command/outbox runtime.
10. Route/capability contract generator.
11. Generic health/readiness model.
12. Feature flag manifest/codegen utility.

---

## 19. Product Remediation Backlog Summary

See `dmos-product-todos.md`. Highest-priority product work:

1. Fix production repository wiring.
2. Replace Google Ads in-memory command dependencies.
3. Wire or disable governed AI strategy workflow.
4. Implement real default-deny policy validation.
5. Close tenant/workspace isolation gaps.
6. Normalize API error envelopes.
7. Add non-mocked E2E and runtime production boot tests.

---

## 20. Production Readiness Gate

Do not release until:

- Production boots with PostgreSQL and all durable repos registered.
- No in-memory/fake/deterministic adapter is reachable in critical production flows.
- Google Ads connector is durable, observable, retry-safe, and fail-closed.
- AI flows either use governed AI with action logs or are explicitly disabled/labeled deterministic.
- Tenant isolation is proven with cross-tenant negative tests.
- PII/token/DSAR/redaction controls are implemented.
- All servlets return canonical errors with correlation ID.
- Playwright includes at least one non-mocked backend+DB critical journey.
- CI runs backend, migration, UI, E2E smoke, and production composition proof.

---

## 21. Final Release Checklist

| Checklist Item | Status |
|---|---:|
| Production boot with `DMOS_ENV=production`, `DMOS_PERSISTENCE_TYPE=postgresql` | ❌ |
| All durable repositories registered | ❌ |
| No in-memory connector/client in production path | ❌ |
| Governed AI workflow configured or disabled | ❌ |
| Tenant isolation tests pass | ❌ |
| PII/token/DSAR/redaction implemented | ❌ |
| Canonical API error envelope across all servlets | ❌ |
| Real Google Ads failure/retry/rate-limit tests | ❌ |
| Non-mocked Playwright E2E critical journey | ❌ |
| Kernel/platform enhancements classified and scoped | ✅ |
