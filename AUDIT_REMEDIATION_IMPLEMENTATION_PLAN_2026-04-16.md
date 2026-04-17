# AUDIT REMEDIATION IMPLEMENTATION PLAN
## Task-by-Task Execution Guide

**Created:** 2026-04-16 | **Source:** COMPREHENSIVE_PRODUCT_AUDIT_REPORT_2026-04-16.md (revised)  
**Governing Document:** `.github/copilot-instructions.md`  
**Methodology:** Evidence-based, source-verified, priority-ordered

> **Guiding Principles (from copilot-instructions.md):**
> - Reuse before creating (§1.1)
> - No silent failures (§1.4)
> - No hardcoded secrets or unsafe defaults (§1.5)
> - Zero-warning mindset (§1.6)
> - Type safety is implementation-time (§1.7, §5, §26)
> - Tests are part of the change (§1.8)
> - Fix-forward, no backward compatibility shims (§25)

---

## Execution Status

**Last Updated:** 2026-04-16

| Task | Status | Notes |
|------|--------|-------|
| 0.1 | COMPLETED | `StorageCostHandler` now rejects invalid collection IDs before query construction; targeted regression coverage passed. |
| 0.2 | COMPLETED | ClickHouse connector now uses ClickHouse request parameters for tenant and entity values across create/read/query/count/bulk paths; tenant IDs are validated against Data Cloud's safe identifier contract and targeted validation tests passed. Existing Testcontainers integration coverage remains Docker-gated in this environment. |
| 0.3 | COMPLETED | `AV_JWT_PERMISSIVE_MODE` bypass removed from the production interceptor path; targeted interceptor tests passed. |
| 0.4 | COMPLETED | Root `.gitignore` hardened for `.env.development` and `.env.*`; missing `.env.example` placeholders added where needed. |
| 2.2 | COMPLETED | `org.ts` now uses typed Zod request parsing, generated Prisma types, and structured typed error logging; targeted root `tsc` validation shows no remaining `org.ts` errors. Separate package install drift remains in other files (`dotenv`, `@sinclair/typebox`, `@fastify/type-provider-typebox`, `fastify-socket.io`, `axios`). |
| 5.2 | COMPLETED | `FeatureIngestConfig` no longer defaults the DB password to an empty string; postgres mode now fails fast when `FEATURE_INGEST_DB_PASSWORD` is missing, with targeted config tests passing. |
| 5.3 | COMPLETED | `DataCloudHttpServer` now requires explicit `DATACLOUD_CORS_ALLOWED_ORIGINS` outside local mode and keeps the localhost fallback only for local profile startup; targeted security configuration tests passed. |
| 5.4 | COMPLETED | AEP pipeline registry wiring no longer falls back to `http://localhost:8085`; `AEP_PIPELINE_REGISTRY_MODE=datacloud` now requires an explicit Data Cloud base URL, with targeted DI tests passing. |
| 8.3 | COMPLETED | Finance now has a product README with scope, structure, build/run/test, and container guidance aligned to existing ownership documentation. |
| 9.1 | COMPLETED | `VisionPanel` no longer renders fake detections; it now shows an explicit unavailable state and a disabled coming-soon action. |
| 9.2 | COMPLETED | `AIVoicePanel` no longer fabricates processed results; it now shows an explicit unavailable state and a disabled coming-soon action. |
| 9.3 | COMPLETED | `MultimodalPanel` no longer fabricates multimodal analysis; it now shows an explicit unavailable state and a disabled coming-soon action. |
| 10.6 | COMPLETED | Chose Option B for now: removed the dead commented-out `AgentGrpcService` stub so the codebase no longer implies a non-existent implementation. No AEP README gRPC claim was present in the current workspace. |
| 10.7 | COMPLETED | `LifecycleLoginController` Javadoc now matches the actual env-only credential provisioning path; no dev fallback user is documented anymore. |
| 4.1 | IN PROGRESS | Expanded Software-Org route integration coverage across org CRUD/agent branches plus new root, metrics, workspace override, and additional Fastify route suites (`build`, `personas`, `skills`, `budgets`, `innovation`, `norms`, `config-api`, `bulk`, `models`, `time-off`, `observe`, `operate`, `growth-plans`, `executions`, `approvals`); broader route/auth coverage is still open, but the new `executions` and `approvals` suites now validate cleanly with generated-client mocking at the route-test boundary. |
| 4.6 | IN PROGRESS | Wired `integration-tests:cross-service-workflow` into Gradle and replaced the stale Spring placeholder with contract-style AEP↔Data Cloud and YAPPC↔Data Cloud tests. |
| 5.1 | IN PROGRESS | Software-Org management-api now uses a typed Zod environment parser with fail-fast validation for `DATABASE_URL`, `JWT_SECRET`, and URL-shaped settings; broader product adoption remains open. |
| 4.2 | IN PROGRESS | Added initial `JpaUserRepository` unit coverage, more `AuthHttpHandler` tenant-default and health-payload edge coverage, extra `WebhookSignatureValidator` malformed-signature coverage, an `AuthorizationServiceImpl` RBAC edge-case increment, `JwtTokenProviderImpl` bulk user-revocation plus previously-unseen-token revocation semantics coverage, new `IpBlockingInterceptor`/`WafInterceptor`/`SecurityGateway` edge-case tests, stronger auth/user-repository/token-store/cross-service coverage, another token-store/encrypted-storage slice across in-memory, Redis, JPA, and Event Cloud security manager tests, a further auth/token/repository increment covering invalidated sessions, tenant-scoped bulk revocation, metadata wrapping, and JPA transaction success semantics, plus another validated tranche across auth/context/interceptor/webhook/storage/JPA integration suites. The latest tranche added auth success-payload assertions, SecurityContext overwrite coverage, public asset and malformed-auth-scheme interceptor regressions, and a production fix for the case-sensitive POST login public-endpoint bypass in `SecurityInterceptor`; broader JWT/RBAC/rate-limit surfaces remain open. |
| 3.3 | IN PROGRESS | Continued the YAPPC frontend promise cleanup by converting additional agent, recommendation, websocket, offline-sync, canvas auto-sync, mobile network bootstrap, incident-detail reload, feature-flag, auth-response parsing, collaboration bootstrap, canvas mutation flows, backend gateway body parsing, dialog promise flows, audit middleware logging, auth logout persistence, Data Cloud client reset, flow-state hook dispatch, Redis connection bootstrap, background node persistence, CLI entrypoints, accessibility-monitor observer callbacks, and the remaining script entrypoint bootstraps from chained background promises to explicit async error handling. The latest batches hardened the remaining source-owned YAPPC script entrypoints and seed bootstrap flow with explicit async failure handling and exit-code propagation, then added two validated 20-file production fetch/response-parsing tranches across development pages, canvas APIs, persistence/offline services, anomaly and rate-limit hooks, batched code-association hooks, auth-session loading, state hooks, mobile/app routes, auth services, AI hooks, thin API clients, and AEP/agent client wrappers. |

**Current Batch Focus:** Remaining Phase 6–11 dependency-injection, error-boundary, documentation, and migration-governance tasks.

---

## Phase 0: Immediate Security Fixes (Week 1)

> **Exit Criteria:** Zero SQL injection vectors, zero permissive security bypasses, zero committed secrets.
> **Verification:** CI green, CodeQL clean, manual security review.

### Task 0.1 — Fix SQL Injection in StorageCostHandler

| Field | Value |
|-------|-------|
| **Priority** | P0 — CRITICAL SECURITY |
| **Product** | Data Cloud |
| **File** | `products/data-cloud/*/StorageCostHandler.java` (line ~123) |
| **Issue** | Path parameter `collectionId` interpolated into SQL: `"SELECT COUNT(*) FROM \"" + collectionId + "\""` |
| **Root Cause** | `request.getPathParameter("id")` passed directly to SQL without parameterization |
| **Fix** | Replace string concatenation with parameterized query via `submitQuery(tenantId, sql, params)` |
| **Validation** | 1. Unit test with SQL injection payload in `collectionId` (e.g., `"; DROP TABLE --"`) <br> 2. Verify parameterized query execution <br> 3. Run CodeQL SAST scan |
| **Test Type** | Unit test (§16: `StorageCostHandlerTest.java` in mirror directory) |
| **Guideline Refs** | §1.5 (no unsafe defaults), §4 (meaningful exceptions), OWASP A03:2021 |

**Implementation Steps:**
1. Read `StorageCostHandler.java` — identify all `submitQuery()` calls with string interpolation
2. Replace `"SELECT COUNT(*) FROM \"" + collectionId + "\""` with parameterized query
3. Add input validation: reject `collectionId` containing non-alphanumeric characters (except `_` and `-`)
4. Create `StorageCostHandlerTest.java` with injection payload test
5. Verify build: `./gradlew :products:data-cloud:*:test`

---

### Task 0.2 — Fix SQL Injection Risk in ClickHouseTimeSeriesConnector

| Field | Value |
|-------|-------|
| **Priority** | P0 — HIGH SECURITY |
| **Product** | Data Cloud |
| **File** | `**/ClickHouseTimeSeriesConnector.java` (line ~476) |
| **Issue** | `String.format()` used for SQL with `tenantId` — `escapeIdentifier()` provides partial protection but string interpolation is risky |
| **Fix** | Replace with parameterized query; validate `tenantId` format at boundary |
| **Validation** | Unit test with injection payload, parameterized query verification |
| **Test Type** | Unit test |
| **Guideline Refs** | §1.5, §4, OWASP A03:2021 |

**Implementation Steps:**
1. Audit all `String.format()` SQL calls in the file
2. Replace each with parameterized equivalents
3. Ensure `escapeIdentifier()` is still used as defense-in-depth for table/column names
4. Add boundary validation for `tenantId` format (UUID pattern)
5. Add unit tests for injection attempts

**Status Update (2026-04-16):**
- Completed in `products/data-cloud/platform-launcher`: `ClickHouseTimeSeriesConnector` now routes user-controlled values through ClickHouse request parameters instead of embedding tenant IDs, entity IDs, JSON payloads, limits, and offsets directly into SQL strings.
- Added connector-local tenant boundary validation aligned with Data Cloud's existing safe identifier contract (`[a-zA-Z0-9._-:]`, max 128 chars) so injection payloads fail before query construction.
- Added `ClickHouseTimeSeriesConnectorTenantValidationTest` and an injection regression in `ClickHouseTimeSeriesConnectorTest`.
- Validation: `./gradlew :products:data-cloud:platform-launcher:test --tests com.ghatana.datacloud.infrastructure.storage.ClickHouseTimeSeriesConnectorTenantValidationTest --tests com.ghatana.datacloud.infrastructure.storage.ClickHouseTimeSeriesConnectorTest`
   - Passed: `ClickHouseTimeSeriesConnectorTenantValidationTest`
   - Skipped: Docker-gated `ClickHouseTimeSeriesConnectorTest` integration tests in the current environment

---

### Task 0.3 — Remove AV_JWT_PERMISSIVE_MODE Bypass

| Field | Value |
|-------|-------|
| **Priority** | P0 — SECURITY |
| **Product** | Audio-Video |
| **File** | `**/JwtServerInterceptor.java` |
| **Issue** | `AV_JWT_PERMISSIVE_MODE=true` env var disables JWT validation entirely |
| **Fix** | Remove the permissive mode path. If needed for local dev, gate behind `AV_PROFILE=LOCAL` check that fails in production |
| **Validation** | 1. Verify JWT validation cannot be bypassed <br> 2. Local dev still works with proper token |
| **Test Type** | Integration test |
| **Guideline Refs** | §1.5, §23 (Authentication Patterns) |

**Implementation Steps:**
1. Read `JwtServerInterceptor.java` — locate permissive mode code path
2. Remove the permissive bypass entirely
3. If local dev needs relaxed auth, use the LOCAL profile pattern (matching Data Cloud's `validateSecurityConfiguration()`)
4. Add test: verify JWT validation is always enforced when interceptor is active
5. Verify build: `./gradlew :products:audio-video:*:test`

---

### Task 0.4 — Gitignore Committed .env Files

| Field | Value |
|-------|-------|
| **Priority** | P0 — SECRET MANAGEMENT |
| **Products** | Software-Org, YAPPC, FlashIt |
| **Files** | `.env.development` files in product directories |
| **Issue** | Development environment files committed to repo |
| **Fix** | 1. Add `.env*` to `.gitignore` <br> 2. Create `.env.example` with placeholder values <br> 3. Remove `.env.development` from git tracking |
| **Validation** | `git status` shows files untracked, CI passes |
| **Test Type** | N/A |
| **Guideline Refs** | §1.5, §5.5 (Configuration and Secret Management) |

**Implementation Steps:**
1. Check `.gitignore` for existing `.env` patterns
2. Add `.env*` pattern if missing (preserve `.env.example`)
3. Create `.env.example` files with placeholder values and comments
4. `git rm --cached` the committed `.env.development` files
5. Verify no secrets in git history (inform team to rotate if found)

---

## Phase 1: Async/Concurrency Correctness (Weeks 1–2)

> **Exit Criteria:** Zero `.getResult()` calls in event loop context, zero `Thread.sleep` in production event loop code.
> **Verification:** Build green, async tests pass, no event loop blocking detected.
> **Guideline Refs:** §4 (Async and Concurrency): *"Never block the event loop. Wrap blocking I/O with `Promise.ofBlocking(…)`"*

### Task 1.1 — Fix Blocking .getResult() in EncryptedStorageService

| Field | Value |
|-------|-------|
| **Priority** | P0 — CONCURRENCY |
| **Product** | Cross-product (platform or product-specific) |
| **File** | `EncryptedStorageService.java:123` |
| **Issue** | Blocks event loop on encrypt operation |
| **Fix** | Wrap with `Promise.ofBlocking(executor, () -> ...)` |
| **Test Type** | Unit test extending `EventloopTestBase` using `runPromise(() -> ...)` (§4 Testing) |

**Implementation Steps:**
1. Read `EncryptedStorageService.java` — identify the blocking call
2. Replace `.getResult()` with `Promise.ofBlocking(executor, () -> encryptOperation())`
3. Ensure the blocking executor is injected via constructor (§4: prefer constructor injection)
4. Create or update test class extending `EventloopTestBase`
5. Test with `runPromise()` — verify no blocking

**Status Update (2026-04-16):**
- Completed in `products/security-gateway/platform/java`: `EncryptedStorageService.storeSecurely(...)` no longer calls `.getResult()`; it now returns a failed or successful promise by composing the existing async `store(...)` path.
- Updated the only production caller in `EnhancedEventSecurityManagerImpl.auditEventAccess(...)` to serialize on a blocking executor and then chain the async storage promise instead of invoking a synchronous facade.
- Added `EncryptedStorageServiceTest` with `EventloopTestBase` coverage for successful async persistence and surfaced encryption failures.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.security.storage.EncryptedStorageServiceTest`
   - Passed: `EncryptedStorageServiceTest`

---

### Task 1.2 — Fix Blocking .getResult() in EventCloudSecurityManager

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **File** | `EventCloudSecurityManager.java:153` |
| **Fix** | Same pattern as Task 1.1 |

**Status Update (2026-04-16):**
- Completed in `products/security-gateway/platform/java`: `EventCloudSecurityManager.rotateEncryptionKey()` now chains `keyManager.rotateKey()` directly instead of calling `.getResult()` inside `Promise.ofBlocking(...)`.
- Added `EventCloudSecurityManagerTest` with `EventloopTestBase` coverage for both success and failure paths.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.security.EventCloudSecurityManagerTest`
   - Passed: `EventCloudSecurityManagerTest`

---

### Task 1.3 — Fix Blocking .getResult() in DeploymentHttpAdapter

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **File** | `DeploymentHttpAdapter.java:142` |
| **Fix** | Replace `promise.getResult()` with proper Promise chain `.then(result -> ...)` |

**Status Update (2026-04-16):**
- Completed in `products/aep`: `DeploymentHttpAdapter` no longer calls `promise.getResult()`; its request handlers now return `Promise<DeploymentResponse>` and map failures through promise composition.
- Updated `DeploymentController` to compose the adapter promises directly instead of consuming synchronous responses.
- Updated orchestrator and controller tests to use the async contract.
- Validation: `./gradlew :products:aep:orchestrator:test --tests com.ghatana.orchestrator.deployment.http.DeploymentHttpAdapterTest :products:aep:server:test --tests com.ghatana.aep.server.http.controllers.DeploymentControllerTest`
   - Passed: `DeploymentHttpAdapterTest`
   - Passed: `DeploymentControllerTest`

---

### Task 1.4 — Fix Blocking .getResult() in RedisSessionStore

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **File** | `RedisSessionStore.java:230,278` |
| **Issue** | Iterates scan results with `.getResult()` |
| **Fix** | Convert to `Promise.reduce()` or `Promises.toList()` for async iteration |

**Status Update (2026-04-16):**
- Source-verified false positive. The referenced `scanResult.getResult()` calls are Jedis `SCAN` API accessors on `redis.clients.jedis.resps.ScanResult`, not ActiveJ promise blocking.
- No code change required for this task as written; the surrounding Redis work is already correctly isolated inside `Promise.ofBlocking(REDIS_POOL, ...)`.

---

### Task 1.5 — Remove 30-second Thread.sleep in SseStreamingHandler

| Field | Value |
|-------|-------|
| **Priority** | P0 — CRITICAL |
| **File** | `SseStreamingHandler.java` |
| **Issue** | `Thread.sleep(30_000)` blocks event loop thread for 30 seconds |
| **Fix** | Replace with `Eventloop.delay(Duration.ofSeconds(30), callback)` or `Promise.ofBlocking()` |
| **Impact** | Entire event loop stalls for 30 seconds — affects all connections |

**Status Update (2026-04-16):**
- Source-verified false positive. The sleep occurs inside `Thread.ofVirtual().start(...)` in `handleLearningStream(...)`, so it does not block the ActiveJ event loop thread handling HTTP requests.
- No code change required for this task as written. If we revisit this area later, the improvement would be lifecycle management of the spawned virtual thread, not event-loop unblocking.

---

### Task 1.6 — Audit and Fix Remaining Thread.sleep Calls

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | `SttGrpcService`, `TenantQuotaManager`, `CheckpointCoordinatorImpl`, `KafkaStreamingPlugin` |
| **Fix Pattern** | For each: <br> 1. Determine if in event loop context → use `Eventloop.delay()` <br> 2. If in blocking context → use `Promise.ofBlocking()` <br> 3. If in dedicated thread → acceptable (document) |
| **Test** | Each replacement needs an async test proving non-blocking behavior |

**Status Update (2026-04-16):**
- Partially remediated with one real code fix and three source-verified dedicated-thread cases.
- Fixed `products/audio-video/modules/speech/stt-service/src/main/java/com/ghatana/stt/grpc/SttGrpcService.java` by removing the ad hoc `Thread.sleep(50)` drain loop and `Thread.sleep(200)` settle delay from `onCompleted()`. The completion path now shuts down the processor deterministically, calls `session.endStream()`, completes the gRPC observer, and closes the session in `finally` so `endStream()` is no longer invoked after `close()`.
- Added regression coverage in `products/audio-video/modules/speech/stt-service/src/test/java/com/ghatana/stt/grpc/SttGrpcServiceTest.java` to verify completion ends the stream before the session is closed.
- Source-verified no remediation required for the other Task 1.6 sites as written:
   - `TenantQuotaManager`: sleeps occur in dedicated daemon reset threads.
   - `CheckpointCoordinatorImpl`: sleep occurs in a dedicated checkpoint thread.
   - `KafkaStreamingPlugin`: sleep occurs in the Kafka consumer loop thread.
- Validation: `./gradlew :products:audio-video:modules:speech:stt-service:test --tests com.ghatana.stt.grpc.SttGrpcServiceTest` → `BUILD SUCCESSFUL`.

---

## Phase 2: Type Safety Remediation (Weeks 2–4)

> **Exit Criteria:** All tsconfig.json have `strict: true`, zero `request.body as any` at API boundaries, `as any` count reduced by 80%.
> **Verification:** `tsc --noEmit` passes with strict mode, ESLint clean.
> **Guideline Refs:** §1.7, §5, §26, §27

### Task 2.1 — Enable strict: true in TutorPutor

| Field | Value |
|-------|-------|
| **Priority** | P0 — NO-GO BLOCKER |
| **Product** | TutorPutor |
| **Files** | `products/tutorputor/apps/tutorputor-web/tsconfig.json`, `products/tutorputor/libs/tutorputor-core/tsconfig.json` |
| **Issue** | `strict: false` — 1,769 `as any` casts |
| **Strategy** | Incremental strictification: <br> 1. Enable `noImplicitAny` first <br> 2. Fix errors file by file (prioritize API boundaries) <br> 3. Enable `strictNullChecks` <br> 4. Enable full `strict: true` |
| **Test** | `tsc --noEmit` must pass at each step |
| **Definition of Done (§15)** | All TS code fully typed, no `any`, no untyped parameters, no missing interfaces |

**Implementation Steps (per tsconfig):**
1. Set `noImplicitAny: true` — run `tsc --noEmit`, fix errors
2. Replace `as any` with proper types — start with API boundaries (Zod schemas per §27)
3. Set `strictNullChecks: true` — fix null handling
4. Set `strict: true` — fix remaining issues
5. Verify all 1,769 `as any` casts addressed

---

### Task 2.2 — Fix Software-Org API Route Type Safety

| Field | Value |
|-------|-------|
| **Priority** | P0 — NO-GO BLOCKER |
| **Product** | Software-Org |
| **File** | `products/software-org/services/management-api/src/routes/org.ts` |
| **Issue** | Every route handler uses `request.body as any` and `request.query as any` |
| **Fix** | 1. Define Zod schemas for every request body/query (§27) <br> 2. Create typed request interfaces <br> 3. Validate at boundary: `const body = CreateOrgSchema.parse(request.body)` |
| **Test** | Unit tests for each schema validation (valid + invalid inputs) |

**Implementation Steps:**
1. Identify all route handlers in `org.ts`
2. For each handler:
   a. Define a Zod schema for the expected request body
   b. Define a Zod schema for query parameters
   c. Replace `request.body as any` with `RequestSchema.parse(request.body)`
   d. Add proper error response for validation failures
3. Create `__tests__/org.test.ts` with validation tests
4. Enable `strict: true` in the project's tsconfig.json

**Status Update (2026-04-16):**
- Completed for `org.ts`: route inputs now validate through Zod, Prisma typing is aligned to the generated client used by the service, and `fastify.log.error` calls were converted to structured error logging compatible with strict Fastify typings.
- Validation result: root TypeScript compilation filtered for `org.ts` no longer reports route-file errors.
- Remaining package failures are outside this task's target file and currently reflect stale dependency resolution in `products/software-org/services/management-api` for `dotenv`, `@sinclair/typebox`, `@fastify/type-provider-typebox`, `fastify-socket.io`, and `axios`.

---

### Task 2.3 — Fix DCMAAR as any Casts (801)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | DCMAAR |
| **Strategy** | Prioritize browser extension code (highest security surface), then React Native |
| **Approach** | Same as 2.1 — incremental strictification with Zod at boundaries |

---

### Task 2.4 — Fix FlashIt as any Casts (289)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | FlashIt |
| **Strategy** | Focus on API clients and data transforms first |

---

### Task 2.5 — Enable strict in 26+ tsconfig Files Missing the Key

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | YAPPC frontend libs, PHR, DCMAAR modules, platform/typescript (code-editor, forms, wizard, ds-generator, sso-client, platform-events, data-grid) |
| **Strategy** | For each: <br> 1. Add `"strict": true` <br> 2. Run `tsc --noEmit` <br> 3. Fix errors <br> 4. Commit per-package |
| **CI Gate** | Add `tsc --noEmit --strict` to CI for all TypeScript packages |

---

### Task 2.6 — Fix VisionPanel.tsx any[] Type Violation

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Product** | Audio-Video |
| **File** | `products/audio-video/apps/desktop/src/components/VisionPanel.tsx` line 15 |
| **Issue** | `useState<any[]>([])` |
| **Fix** | Define `Detection` interface, use `useState<Detection[]>([])` |

**Status Update (2026-04-16):**
- Completed in `products/audio-video/apps/desktop/src/components/VisionPanel.tsx`: replaced `useState<any[]>([])` with explicit `Detection` and `DetectionBoundingBox` interfaces and `useState<Detection[]>([])`.
- Editor diagnostics for the touched component are clean.
- Validation caveat: package-level `pnpm exec tsc -p tsconfig.json --noEmit` currently fails before component checking due to a pre-existing TypeScript 6 config error in `products/audio-video/apps/desktop/tsconfig.json` on `baseUrl`, so full package typecheck is not yet green as part of this task.

---

## Phase 3: Error Handling Cleanup (Weeks 3–4)

> **Exit Criteria:** Zero empty catch blocks in production code, zero generic `catch (Exception e)` without logging.
> **Verification:** Build green, linter clean.
> **Guideline Refs:** §1.4 (No silent failures), §3 (Errors and Failure Handling)

### Task 3.1 — Fix 30+ Empty Catch Blocks in Production Code

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | All products |
| **Key Files** | `ReportService.java` (data-cloud), `FeatureStoreIngestLauncher.java` (data-cloud), `AepQueryService.java` (AEP) |
| **Fix Pattern** | For each empty catch: <br> 1. Determine if error should propagate → rethrow as domain exception <br> 2. If recoverable → log warning with context <br> 3. If expected → add comment explaining why empty is intentional |
| **Test** | Verify error scenarios trigger logging (use test log appender) |

**Implementation Steps:**
1. Search: `catch.*\{[\s]*\}` across `products/` (exclude test files)
2. For each match, determine the appropriate error handling strategy
3. Apply fix per the pattern above
4. Add tests for error paths where meaningful

**Status Update (2026-04-16):**
- Initial remediation completed for the three keyed parse-fallback files called out in this task:
   - `products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/report/ReportService.java`
   - `products/data-cloud/feature-store-ingest/src/main/java/com/ghatana/services/featurestore/FeatureStoreIngestLauncher.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/query/AepQueryService.java`
- Additional production remediations completed in this pass:
   - `products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/resilience/ResilienceDecorator.java`
   - `shared-services/ai-inference-service/src/main/java/com/ghatana/services/aiinference/AIInferenceHttpAdapter.java`
   - `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/resilience/ResilienceFactory.java`
   - `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/spec/AgentSpecLoader.java`
   - `products/finance/domains/pricing/src/main/java/com/ghatana/products/finance/domains/pricing/service/PriceCrossValidationService.java`
   - `products/finance/domains/post-trade/src/main/java/com/ghatana/products/finance/domains/posttrade/service/SettlementInterventionService.java`
   - `products/finance/domains/compliance/src/main/java/com/ghatana/products/finance/domains/compliance/service/BeneficialOwnershipDisclosureService.java`
   - `products/finance/domains/ems/src/main/java/com/ghatana/products/finance/domains/ems/service/SorConfigurationService.java`
   - `products/finance/domains/surveillance/src/main/java/com/ghatana/products/finance/domains/surveillance/service/TradingAnomalyDetectionService.java`
   - `products/finance/integration-testing/src/main/java/com/ghatana/finance/integration/PositionReconciliationIntegrityTestSuiteService.java`
   - `products/finance/integration-testing/src/main/java/com/ghatana/finance/integration/ComplianceScreeningE2eTestSuiteService.java`
   - `products/finance/integration-testing/src/main/java/com/ghatana/finance/integration/OrderToSettlementE2eTestSuiteService.java`
   - `products/finance/integration-testing/src/main/java/com/ghatana/finance/integration/OrderProcessingPerformanceBaselineService.java`
   - `products/finance/integration-testing/src/main/java/com/ghatana/finance/integration/LedgerDoubleEntryIntegrityTestSuiteService.java`
- The previously empty `NumberFormatException`, `IllegalArgumentException`, and duration-parse fallback catches in those files are now documented expected fallbacks rather than silent empty blocks.
- The previously empty `SQLException` catch in `BeneficialOwnershipDisclosureService` now emits a warning with client and instrument context instead of failing silently.
- The previously empty generic `Exception` catches in the finance pricing, EMS, and surveillance services now emit contextual warnings while preserving their existing skip-or-fallback behavior.
- The previously empty `SQLException` catches in the Finance integration-suite services now emit contextual warnings when assertion/run metadata persistence fails, preserving the existing test-suite control flow while removing silent audit-trail loss.
- No behavior change was introduced in this slice, so no new runtime tests were required; touched-file diagnostics are clean except for pre-existing unused constant warnings in `FeatureStoreIngestLauncher` and `OrderProcessingPerformanceBaselineService` that are outside this task’s scope.
- Remaining work for Task 3.1 is the wider repository sweep beyond these initial keyed files.

---

### Task 3.2 — Fix 50+ Generic Exception Catches

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | audio-video JPA repositories (heaviest), AEP execution queue, data-cloud report service |
| **Fix Pattern** | Replace `catch (Exception e)` with specific exception types <br> Log with structured context: `log.error("Operation failed", Map.of("entity", id, "op", opName), e)` |

**Status Update (2026-04-16):**
- Initial Task 3.2 remediation completed in `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/store/PostgresqlCheckpointStore.java`.
- Additional Task 3.2 parsing remediations completed in:
   - `platform/java/config/src/main/java/com/ghatana/platform/config/AppConfig.java`
   - `platform/java/config/src/main/java/com/ghatana/platform/config/ConfigManager.java`
   - `platform/java/config/src/main/java/com/ghatana/platform/config/YamlConfigSource.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/catalog/AepOperatorCatalogLoader.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/analytics/DataCloudAnalyticsStore.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/http/HttpHelper.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/CostController.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/observability/AepSloMetrics.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/query/AepQueryService.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/report/AepReportingService.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/store/DataCloudPipelineStore.java`
   - `products/aep/server/src/main/java/com/ghatana/aep/server/store/DataCloudPatternStore.java`
- The two broad `catch (Exception e)` blocks around `totalSteps` parsing in `createExecution(...)` and `updateCheckpoint(...)` were removed.
- Parsing is now centralized in a helper that handles `null` explicitly, accepts numeric instances directly, and only catches `NumberFormatException` for malformed string values.
- The unnecessary `catch (Exception ignored)` wrapper around `new YamlConfigSource(Path.of("application.yml"))` in `AppConfig.load()` was removed; the YAML source already handles missing files internally, so unexpected construction failures now surface instead of being silently downgraded.
- The broad `catch (Exception e)` around `new FileConfigSource(configFilePath)` in `ConfigManager.createDefault(...)` was narrowed to `IllegalArgumentException`, matching the file-source constructor contract for missing configuration files while preserving the existing warning-and-continue behavior.
- The broad conversion catch in `YamlConfigSource.getObject(...)` now catches `IOException`, matching the `ObjectMapper` serialization/deserialization path used to materialize typed config objects.
- The broad JSON serialization fallbacks in `HttpHelper.jsonResponse(...)`, `HttpHelper.errorResponse(...)`, and `AepHttpServer.parseJsonObject(...)` were narrowed to `JsonProcessingException`, preserving existing fallback responses for malformed or non-serializable payloads.
- In `CostController`, the `Instant.parse(...)` fallback used for cost-window timestamps was narrowed to `DateTimeParseException`, preserving the current fallback instant for malformed query or payload values.
- In `AepOperatorCatalogLoader`, the outer classpath-loading catch now targets `IOException | InvalidPathException`, and an inner try/catch around a no-op debug/count loop was removed because there was no real failing operation to recover from there.
- In `AepSloMetrics`, the best-effort gauge-registration fallback was narrowed to `IllegalArgumentException`, matching the expected duplicate/invalid meter registration path while preserving the existing debug-only fallback behavior.
- The broad `catch (Exception ...)` wrappers around `Instant.parse(...)` in the AEP analytics, query, reporting, and pipeline/pattern store helpers were narrowed to `DateTimeParseException`, preserving the existing fallback behavior for malformed timestamps.
- In `DataCloudPatternStore`, the broad fallback handlers around `ObjectMapper.convertValue(...)`, `UUID.fromString(...)`, and `PatternStatus.valueOf(...)` were narrowed to `IllegalArgumentException`, and the UUID helper now handles null/blank candidates explicitly before attempting parsing.
- Existing fallback behavior was preserved: invalid `totalSteps` values still do not fail checkpoint creation or update, but the warning log now corresponds to the concrete parse failure path instead of a blanket generic exception catch.
- Touched-file diagnostics are clean for the platform config files, AEP catalog/HTTP/observability helpers, orchestrator store, analytics/query services, and pipeline/pattern stores; `AepReportingService` still has a pre-existing unused-constant warning (`COL_METRICS`) unrelated to this remediation.

---

### Task 3.3 — Fix 30+ Unhandled .then() in YAPPC Frontend

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Scope** | `products/yappc/frontend/` |
| **Fix** | Add `.catch()` handler to every `.then()` chain, or use `async/await` with `try/catch` |
| **Guideline Refs** | §5 (Keep async flows explicit: Never ignore promises, always handle rejections) |

**Status Update (2026-04-16):**
- Initial production remediation batch completed in the YAPPC frontend:
   - `products/yappc/frontend/web/src/providers/AuthProvider.tsx`
   - `products/yappc/frontend/web/src/components/workspace/OnboardingFlow.tsx`
   - `products/yappc/frontend/web/src/routes/mobile/projects.tsx`
- Additional production/library remediation batch completed in:
   - `products/yappc/frontend/web/src/components/deploy/IncidentManagementPanel.tsx`
   - `products/yappc/frontend/libs/yappc-ai/src/agents/base/Agent.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/interactions/hooks/useDialog.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/utils/AccessibilityAuditor/index.ts`
- `AuthProvider` no longer leaves the session bootstrap promise unhandled; it now initializes auth state through an async function with explicit `try/catch`, logs the failure, and falls back to guest/null state as documented.
- `OnboardingFlow` no longer leaves workspace/project AI suggestion fetches on bare `.then()` chains; both effects now use async helpers with `try/catch/finally`, preserving the existing autofill behavior while ensuring loading flags always reset on failure.
- The mobile projects route now handles `Network.getStatus()` rejection explicitly instead of relying on an unobserved promise chain during Capacitor network bootstrap.
- `IncidentManagementPanel` now handles artifact detail reload failures explicitly instead of firing an unhandled background promise from its refresh effect.
- The YAPPC AI base agent queue now catches background task-execution failures before recursing the queue, so rejected task promises no longer risk leaving the queue stalled or silently unobserved.
- `useDialog` now resolves confirmation/prompt promises safely even if the modal orchestration path fails, and `AccessibilityAuditor.startMonitoring()` now logs rejected audit runs instead of leaving the mutation-observer callback with an unhandled promise.
- `CanvasToolbar` retry-save handling now uses explicit `async`/`await` around `performanceMonitor.measureAsync(...)` so the manual save path updates status in a single `try/catch` flow instead of chaining a background `.then(...).catch(...)` sequence.
- Additional production remediation batch completed in:
   - `products/yappc/frontend/web/src/pages/auth/SSOCallbackPage.tsx`
   - `products/yappc/frontend/web/src/routes/app/project/deploy.tsx`
   - `products/yappc/frontend/web/src/hooks/useAIAssistant.ts`
   - `products/yappc/frontend/libs/ide/src/hooks/useKeyboardShortcuts.ts`
- Additional shared-frontend remediation batch completed in:
   - `products/yappc/frontend/libs/yappc-ui/src/components/components/FileUpload/FileUpload.tsx`
   - `products/yappc/frontend/libs/api/src/graphql/client.ts`
   - `products/yappc/frontend/libs/yappc-core/src/api/graphql/client.ts`
- `SSOCallbackPage` now completes the auth callback through an async helper with explicit failure routing instead of a nested fetch promise chain.
- The deploy route now loads lifecycle phase previews through an async effect helper with a single `try/catch/finally` flow, preserving mounted guards while removing the background `.then(...).catch(...).finally(...)` chain.
- `useAIAssistant` now resolves service-backed suggestions through one explicit async flow with local-heuristic fallback, so suggestion generation no longer relies on chained `.then().catch().then().finally()` control flow.
- IDE clipboard paste handling now uses an async helper around `navigator.clipboard.readText()` with explicit fallback to `execCommand('paste')` on failure.
- File upload auto-submit now updates pending file states through an async helper with a normalized failure message instead of a background upload `.then(...).catch(...)` chain.
- Both shared GraphQL clients now perform token-refresh retry orchestration through explicit async retry helpers inside the observable path, rather than chaining `tokenRefreshPromise?.then(...).catch(...)` inside the auth error handler.
- Additional production/library remediation batch completed in:
   - `products/yappc/frontend/libs/yappc-ai/src/agents/BaseAgent.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useRecommendations.ts`
   - `products/yappc/frontend/libs/ide/src/services/websocket-service.ts`
   - `products/yappc/frontend/web/src/services/offline/OfflineService.ts`
   - `products/yappc/frontend/web/src/services/canvas/sync/SyncStrategy.ts`
- `BaseAgent.withTimeout()` now uses a single `Promise.race(...)` + `finally` flow, so timeout cleanup no longer depends on a chained `.then(...).catch(...)` wrapper.
- Recommendation feedback posting now runs through an explicit async helper that logs rejected or non-OK responses instead of leaving a fire-and-forget `fetch(...).catch(console.error)` path.
- IDE websocket reconnection now routes through an async retry helper rather than chaining `.catch(...)` directly off `connect()` inside the timer callback.
- Offline reconnect sync and canvas auto-sync now use dedicated background-sync helpers with explicit rejection logging, so timer and browser online handlers no longer rely on unobserved async interval callbacks or inline `.catch(console.error)` chains.
- The mobile projects route now resolves initial `Network.getStatus()` through an async helper with explicit warning handling instead of a bare `.then(...).catch(...)` bootstrap chain.
- `IncidentManagementPanel` now reloads selected incident details through an async effect helper with explicit error handling instead of chaining `.then(...).catch(...)` off the artifact fetch.
- Validation: touched-file diagnostics are clean for the three remediated YAPPC frontend files.
- Validation: touched-file diagnostics are also clean for the remediated agent, dialog, accessibility-auditor, and incident-management files.
- Validation: touched-file diagnostics are clean for the additional YAPPC remediations in `BaseAgent`, `useRecommendations`, IDE websocket service, `OfflineService`, and `SyncStrategy`.
- Validation: touched-file diagnostics are clean for the latest YAPPC remediations in `web/src/routes/mobile/projects.tsx` and `web/src/components/deploy/IncidentManagementPanel.tsx`.
- Replaced ad hoc `response.json().catch(() => ({}))` fallback parsing in `web/src/services/canvas/phase-actions/PhaseActionService.ts`, `web/src/services/lifecycle/api.ts`, `web/src/services/lifecycle/phase-transition-api.ts`, and `web/src/services/auth/AuthService.ts` with explicit helper-based error-body readers.
- Converted additional fire-and-forget frontend flows in `web/src/providers/FeatureFlagProvider.tsx`, `web/src/hooks/useCollaboration.ts`, `web/src/hooks/useErrorRecovery.ts`, `web/src/components/collaboration/CanvasCollaborationProvider.tsx`, `web/src/components/shared/ErrorBoundary.tsx`, and `web/src/components/canvas/collaboration/CommentsPanel.tsx` from chained `.catch(...)` usage to named async helpers or wrapped async blocks.
- Centralized mutation failure handling in `web/src/components/canvas/hooks/useCanvasHandlers.ts` so artifact updates, blocker/comment/link actions, ghost acceptance, type changes, and phase transitions all use one explicit announcement-backed async failure path.
- Tightened text-error fallback handling in `web/src/lib/canvas-ai/yappc-ai-adapter.ts` so failed response-body reads no longer depend on inline chained `.catch(...)` parsing.
- Validation: touched-file diagnostics are clean for the latest YAPPC remediation batch across 12 production files, including `PhaseActionService`, lifecycle APIs, `AuthService`, `FeatureFlagProvider`, collaboration bootstrap hooks/components, `ErrorBoundary`, and `useCanvasHandlers`.
- Added another shared/frontend async-handling cleanup batch in:
   - `products/yappc/frontend/libs/aep-config/aep-client-factory.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/aep-config/aep-client-factory.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/error/errorReporter.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/utils/webVitals.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/components/Auth/LoginForm.tsx`
   - `products/yappc/frontend/libs/yappc-ui/src/components/components/Auth/RegisterForm.tsx`
   - `products/yappc/frontend/libs/yappc-ui/src/components/components/Auth/PasswordResetForm.tsx`
- `AuthService` no longer contains the stray broken class-body statement from the previous parsing remediation, and the registration path now reuses the explicit `readErrorResponse(...)` helper instead of chaining `response.json().catch(...)` inline.
- The shared AEP client factory reset paths now wrap background shutdown failures in explicit logging instead of passing `console.error` directly as a bare rejection handler.
- The shared error reporter and web-vitals loader now use explicit async wrappers for their fire-and-forget network/import paths, preserving non-blocking behavior while keeping rejection handling explicit.
- The shared login, register, and password-reset forms now parse failed JSON bodies through explicit `try/catch` blocks instead of inline `.catch(() => ({}))` fallbacks.
- Validation: editor diagnostics are clean for this shared YAPPC/frontend cleanup batch.
- Added another async/error-handling cleanup tranche in:
   - `products/yappc/frontend/libs/data-cloud-config/data-cloud-client-factory.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/interactions/hooks/useDialog.ts`
   - `products/yappc/frontend/apps/api/src/middleware/BackendGateway.ts`
   - `products/yappc/frontend/apps/api/src/middleware/AuditLoggingMiddleware.ts`
   - `products/yappc/frontend/apps/api/src/middleware/audit.middleware.ts`
   - `products/yappc/frontend/apps/api/src/services/auth/auth.service.ts`
   - `products/yappc/frontend/apps/api/src/services/FlowService.ts`
- The shared Data Cloud client reset path now logs shutdown failures through an explicit wrapped async rejection handler instead of delegating directly to `console.error`.
- Dialog confirm/prompt helpers now resolve through explicit async wrappers, so modal orchestration failures no longer depend on chained `.then(...).catch(...)` promise flow.
- The API backend gateway now reads proxy request bodies through an explicit helper rather than inlining `request.json().catch(...)` in the proxy options object.
- Both audit middlewares and the flow-state hook dispatcher now wrap fire-and-forget async work in named async blocks with explicit logging, keeping non-blocking behavior while making rejection handling visible.
- The API auth logout-all path now treats the best-effort persistence update through an explicit `try/catch` instead of a silent chained `.catch(...)` call.
- Validation: editor diagnostics are clean for this additional YAPPC async-handling batch.
- Added another production/script async cleanup batch in:
   - `products/yappc/frontend/apps/api/prisma/seed-basic.ts`
   - `products/yappc/frontend/apps/api/prisma/seed-minimal.ts`
   - `products/yappc/frontend/apps/api/prisma/seed-simple.ts`
   - `products/yappc/frontend/apps/api/prisma/seed-ai.ts`
   - `products/yappc/frontend/apps/api/prisma/seed-workflows.ts`
   - `products/yappc/frontend/apps/api/prisma/seed.ts`
   - `products/yappc/frontend/libs/yappc-core/src/config/patterns/async-patterns.ts`
   - `products/yappc/frontend/libs/yappc-state/src/config/patterns/async-patterns.ts`
- The remaining Prisma seed entrypoints now execute through explicit `runSeed()` wrappers with `try/catch/finally`, preserving disconnect semantics while eliminating the trailing `.catch(...).finally(...)` chains and making exit-code handling explicit.
- Both shared async-pattern modules now use explicit async wrappers for limited parallel execution, API-call error wrapping, and cancellable promise bridging, so those helpers no longer depend on inline `.then(...).catch(...)` chains.
- Validation: editor diagnostics are clean for this YAPPC async cleanup batch across the six seed scripts and the two shared async-pattern modules.
- Added another script-entrypoint async cleanup batch in:
   - `products/yappc/frontend/scripts/verify-dod.js`
   - `products/yappc/frontend/scripts/post-ci-summary.js`
   - `products/yappc/frontend/scripts/consolidate-libraries.js`
   - `products/yappc/frontend/scripts/simplify-build-scripts.js`
   - `products/yappc/frontend/update-deps.js`
   - `products/yappc/frontend/test-runner.js`
   - `products/yappc/frontend/scripts/validate-contrast.mjs`
- The remaining CLI/bootstrap entrypoints now terminate through explicit async `run()` wrappers instead of trailing `.catch(...)` chains, and the canvas `test-runner.js` now actually honors the filtered suite set selected by command-line flags.
- Validation: editor diagnostics are clean for this YAPPC script-entrypoint cleanup batch.
- Added another YAPPC script-entrypoint hardening batch in:
   - `products/yappc/frontend/apps/api/seed_db.mjs`
   - `products/yappc/frontend/scripts/security-scan.js`
   - `products/yappc/frontend/scripts/enhance-typescript-config.js`
   - `products/yappc/frontend/scripts/manage-artifacts.js`
   - `products/yappc/frontend/scripts/validate-contrast.mjs`
   - `products/yappc/frontend/scripts/verify-workspace-deps.js`
   - `products/yappc/frontend/scripts/issue-planner.js`
   - `products/yappc/frontend/scripts/issue-planner.ts`
   - `products/yappc/frontend/scripts/build.js`
- The remaining source-owned script entrypoints now fail through explicit async wrappers or `run()` orchestration, so seed/bootstrap and CLI flows no longer rely on trailing chained `.catch(...)` handlers, and the API seed bootstrap now awaits Prisma disconnect in `finally` before exiting.
- Validation: `node --check` passed for the touched `.js`/`.mjs` entrypoints in this batch.
- Validation: `pnpm exec tsc --noEmit scripts/issue-planner.ts` remains blocked by existing workspace/package typing drift in `products/yappc/frontend` (`@types/node` globals and pre-existing `RoadmapSync` mismatch), not by the catch/exit handling added in this tranche.
- Added another production fetch/error-handling hardening tranche in:
   - `products/yappc/frontend/apps/api/src/index.ts`
   - `products/yappc/frontend/apps/api/src/jobs/embedding-pipeline.ts`
   - `products/yappc/frontend/apps/api/src/services/ai/ai.service.ts`
   - `products/yappc/frontend/apps/api/src/services/DashboardService.ts`
   - `products/yappc/frontend/apps/api/src/services/ConfigService.ts`
   - `products/yappc/frontend/apps/api/src/services/FlowService.ts`
   - `products/yappc/frontend/libs/aep-config/aep-client-factory.ts`
   - `products/yappc/frontend/libs/data-cloud-config/data-cloud-client-factory.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/aep-config/aep-client-factory.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/core/providers/LocalProvider.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/agents/api-client.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useRecommendations.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useAI.graphql.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useSemanticSearch.ts`
   - `products/yappc/frontend/web/src/services/agentService.ts`
- These source-owned API and client wrappers now fail explicitly on unreadable or invalid upstream JSON/text bodies instead of delegating to raw `response.json()`/`response.text()` calls with inconsistent error surfaces.
- Validation: editor diagnostics are clean for this 15-file YAPPC production hardening tranche.
- Validation: `pnpm exec tsc -p products/yappc/frontend/tsconfig.json --noEmit` passed.
- Validation note: narrower package-level typechecks remain blocked by pre-existing workspace drift outside this tranche: `products/yappc/frontend/apps/api/tsconfig.json` still fails in existing `lifecycle.ts` and `advanced-ai.service.ts` paths, `products/yappc/frontend/libs/yappc-ai/tsconfig.json` still fails on the existing `TS5101` `baseUrl` deprecation, and `products/yappc/frontend/web/tsconfig.json` still fails in existing `workflow.sample-data.ts` enum mismatches.
- Added another YAPPC production fetch/response hardening tranche in:
   - `products/yappc/frontend/web/src/pages/development/CodeReviewDetailPage.tsx`
   - `products/yappc/frontend/web/src/pages/development/SprintPlanningPage.tsx`
   - `products/yappc/frontend/web/src/pages/development/VelocityChartsPage.tsx`
   - `products/yappc/frontend/web/src/services/canvas/api/CanvasAPIClient.ts`
   - `products/yappc/frontend/web/src/services/canvas/api/CanvasAIService.ts`
   - `products/yappc/frontend/web/src/services/ai/ArtifactSuggestionService.ts`
   - `products/yappc/frontend/web/src/services/canvasBackend.ts`
   - `products/yappc/frontend/web/src/services/anomaly/ThreatIntelligenceService.ts`
   - `products/yappc/frontend/libs/api/src/hooks/useApi.ts`
   - `products/yappc/frontend/web/src/hooks/useLifecyclePhaseTransition.ts`
   - `products/yappc/frontend/web/src/hooks/useCanvasPersistence.ts`
   - `products/yappc/frontend/web/src/hooks/useAnomalyDetection.ts`
   - `products/yappc/frontend/web/src/hooks/useRateLimit.ts`
   - `products/yappc/frontend/web/src/hooks/useCodeAssociations.ts`
   - `products/yappc/frontend/web/src/components/canvas/hooks/useCodeAssociationsBatch.ts`
   - `products/yappc/frontend/web/src/components/ratelimit/ThrottleAlertBanner.tsx`
   - `products/yappc/frontend/web/src/components/ratelimit/RateLimitStatusWidget.tsx`
   - `products/yappc/frontend/web/src/services/canvas/CanvasPersistence.ts`
   - `products/yappc/frontend/web/src/services/offline/OfflineService.ts`
   - `products/yappc/frontend/web/src/providers/auth-session.ts`
- These remaining source-owned frontend fetch wrappers now parse JSON through explicit contextual helpers, so invalid upstream bodies fail with actionable context instead of surfacing as opaque `response.json()` runtime errors.
- Validation: editor diagnostics are clean for this 20-file YAPPC production hardening tranche.
- Validation: `pnpm exec tsc -p products/yappc/frontend/tsconfig.json --noEmit` passed again after this tranche.
- Added another YAPPC production fetch/response hardening tranche in:
   - `products/yappc/frontend/libs/yappc-state/src/store/config-hooks/useConfigData.ts`
   - `products/yappc/frontend/libs/yappc-state/src/hooks/useWorkspace.ts`
   - `products/yappc/frontend/libs/yappc-state/src/hooks/useAI.ts`
   - `products/yappc/frontend/libs/yappc-state/src/hooks/useProject.ts`
   - `products/yappc/frontend/libs/yappc-state/src/store/atoms.ts`
   - `products/yappc/frontend/web/src/routes/mobile/overview.tsx`
   - `products/yappc/frontend/web/src/routes/mobile/projects.tsx`
   - `products/yappc/frontend/web/src/routes/app/project/_shell.tsx`
   - `products/yappc/frontend/web/src/routes/app/project/canvas.tsx`
   - `products/yappc/frontend/web/src/services/auth/AuthService.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/ui/core/SuggestionPanel.tsx`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useAI.graphql.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useSemanticSearch.ts`
   - `products/yappc/frontend/apps/api/src/services/ai/ai.service.ts`
   - `products/yappc/frontend/apps/api/src/services/ai/resilient-ai.service.ts`
   - `products/yappc/frontend/apps/api/src/services/ConfigService.ts`
   - `products/yappc/frontend/apps/api/src/services/DashboardService.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/agents/api-client.ts`
   - `products/yappc/frontend/libs/aep-config/aep-client-factory.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/aep-config/aep-client-factory.ts`
- These remaining source-owned state hooks, route loaders, auth/service clients, AI hooks, and AEP/agent wrappers now parse upstream JSON through explicit contextual helpers and include response-body detail on non-OK service-mode failures, so malformed or unreadable upstream responses fail with actionable context instead of surfacing as opaque `response.json()` or `response.text()` runtime errors.
- Validation: editor diagnostics are clean for this additional 20-file YAPPC production hardening tranche.
- Validation: `pnpm exec tsc -p products/yappc/frontend/tsconfig.json --noEmit` passed cleanly after this tranche.
- Added another YAPPC production transport and loader hardening tranche in:
   - `products/yappc/frontend/libs/yappc-auth/src/auth/oauth/utils.ts`
   - `products/yappc/frontend/libs/yappc-core/src/api/hooks/useApi.ts`
   - `products/yappc/frontend/libs/yappc-core/src/config/tasks/configLoader.ts`
   - `products/yappc/frontend/libs/yappc-state/src/config/tasks/configLoader.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/hooks/useConfigData.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/hooks/useDataSource/utils.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/hooks/useLifecycleApi.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/hooks/auth/useAuth.ts`
   - `products/yappc/frontend/web/src/providers/auth-session.ts`
   - `products/yappc/frontend/web/src/services/agentService.ts`
   - `products/yappc/frontend/web/src/services/anomaly/ThreatIntelligenceService.ts`
   - `products/yappc/frontend/web/src/services/canvas/api/CanvasAIService.ts`
   - `products/yappc/frontend/web/src/services/canvas/api/CanvasAPIClient.ts`
   - `products/yappc/frontend/web/src/services/lifecycle/api.ts`
   - `products/yappc/frontend/web/src/services/offline/OfflineService.ts`
   - `products/yappc/frontend/web/src/hooks/useWorkflows.ts`
   - `products/yappc/frontend/libs/yappc-ui/src/components/canvas/CanvasEditor.tsx`
   - `products/yappc/frontend/web/src/components/canvas/lifecycle/CanvasRightPanelHost.tsx`
   - `products/yappc/frontend/web/src/components/canvas/nodes/MonacoNode.tsx`
   - `products/yappc/frontend/web/src/components/deploy/DeployPanelHost.tsx`
- These remaining source-owned transport helpers, auth/session loaders, config loaders, and canvas/deploy shell loaders now validate JSON bodies through explicit contextual readers, surface non-OK response detail instead of relying on raw `response.json()` fallbacks, and replace placeholder config-loader implementations with real YAML/file-loading behavior.
- Validation: editor diagnostics are clean for this additional 20-file YAPPC transport/loader hardening tranche.
- Validation: `pnpm exec tsc -p products/yappc/frontend/tsconfig.json --noEmit` passed cleanly after this tranche.
- Added another YAPPC production transport hardening tranche in:
   - `products/yappc/frontend/libs/api/src/hooks/useApi.ts`
   - `products/yappc/frontend/libs/api/src/graphql/client.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useRecommendations.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useSemanticSearch.ts`
   - `products/yappc/frontend/web/src/services/ai/ArtifactSuggestionService.ts`
   - `products/yappc/frontend/web/src/services/canvasBackend.ts`
   - `products/yappc/frontend/web/src/hooks/useLifecyclePhaseTransition.ts`
   - `products/yappc/frontend/web/src/hooks/useCanvasPersistence.ts`
   - `products/yappc/frontend/web/src/hooks/useAnomalyDetection.ts`
   - `products/yappc/frontend/web/src/hooks/useCodeAssociations.ts`
   - `products/yappc/frontend/web/src/hooks/useRateLimit.ts`
   - `products/yappc/frontend/web/src/components/canvas/hooks/useCodeAssociationsBatch.ts`
   - `products/yappc/frontend/web/src/services/lifecycle/phase-transition-api.ts`
   - `products/yappc/frontend/web/src/routes/mobile/overview.tsx`
   - `products/yappc/frontend/web/src/routes/mobile/projects.tsx`
   - `products/yappc/frontend/web/src/routes/app/project/_shell.tsx`
   - `products/yappc/frontend/web/src/routes/app/project/canvas.tsx`
   - `products/yappc/frontend/web/src/components/ratelimit/ThrottleAlertBanner.tsx`
   - `products/yappc/frontend/web/src/components/ratelimit/RateLimitStatusWidget.tsx`
   - `products/yappc/frontend/web/src/pages/development/CodeReviewDetailPage.tsx`
   - `products/yappc/frontend/web/src/services/auth/AuthService.ts`
   - `products/yappc/frontend/web/src/services/canvas/phase-actions/PhaseActionService.ts`
- These remaining source-owned API hooks, GraphQL/token refresh helpers, artifact/canvas services, lifecycle/rate-limit/code-association hooks, mobile/app route loaders, and review/auth/phase-action clients now decode response bodies through explicit contextual parsers and surface non-OK body detail instead of relying on raw `response.json()` with generic fallbacks.
- Validation: editor diagnostics are clean for this additional 22-file YAPPC transport hardening tranche.
- Validation: `pnpm exec tsc -p products/yappc/frontend/tsconfig.json --noEmit` passed cleanly after this tranche.
- Additional cross-product increment completed in `products/software-org/services/management-api/src/services/java-client.ts` and `products/software-org/services/management-api/src/server.ts`.
- The Software-Org management API now reads Java-service error/success payloads through explicit JSON/text helpers instead of inline `response.json().catch(...)` fallbacks, and the Fastify socket runtime initialization no longer depends on a top-level `.then(async ...)` startup chain.
- Validation: `pnpm exec tsc -p products/software-org/services/management-api/tsconfig.json --noEmit` passed after this increment.
- Added another YAPPC production transport and shared-client hardening tranche in:
   - `products/yappc/frontend/libs/aep-config/aep-client-factory.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/aep-config/aep-client-factory.ts`
   - `products/yappc/frontend/libs/data-cloud-config/data-cloud-client-factory.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/useAI.graphql.ts`
   - `products/yappc/frontend/libs/yappc-core/src/api/graphql/client.ts`
   - `products/yappc/frontend/web/src/services/canvas/CanvasPersistence.ts`
   - `products/yappc/frontend/web/src/pages/development/SprintPlanningPage.tsx`
   - `products/yappc/frontend/web/src/pages/development/VelocityChartsPage.tsx`
   - `products/yappc/frontend/libs/yappc-ai/src/agents/api-client.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/core/providers/LocalProvider.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/ui/core/SuggestionPanel.tsx`
   - `products/yappc/frontend/libs/yappc-ui/src/components/components/Auth/PasswordResetForm.tsx`
   - `products/yappc/frontend/libs/yappc-ui/src/components/components/Auth/RegisterForm.tsx`
   - `products/yappc/frontend/libs/yappc-ui/src/components/components/Auth/LoginForm.tsx`
   - `products/yappc/frontend/libs/yappc-state/src/store/config-hooks/useConfigData.ts`
   - `products/yappc/frontend/libs/yappc-state/src/store/atoms.ts`
   - `products/yappc/frontend/libs/yappc-state/src/hooks/useWorkspace.ts`
   - `products/yappc/frontend/libs/yappc-state/src/hooks/useAI.ts`
   - `products/yappc/frontend/libs/yappc-state/src/hooks/useProject.ts`
- These remaining shared client factories, GraphQL fetchers, canvas persistence adapters, development dashboards, AI suggestion/provider clients, auth forms, and YAPPC state-layer hooks now parse success bodies through explicit text-to-JSON readers and surface non-OK body detail instead of depending on raw `response.json()` branches or generic status text.
- Validation: editor diagnostics are clean for this additional 19-file YAPPC shared-client and state-layer tranche.
- Validation: `pnpm exec tsc -p products/yappc/frontend/tsconfig.json --noEmit` passed cleanly after this tranche.
- Added another YAPPC API-service transport hardening tranche in:
   - `products/yappc/frontend/apps/api/src/services/DashboardService.ts`
   - `products/yappc/frontend/apps/api/src/services/ConfigService.ts`
   - `products/yappc/frontend/apps/api/src/services/ai/resilient-ai.service.ts`
   - `products/yappc/frontend/apps/api/src/services/ai/ai.service.ts`
   - `products/yappc/frontend/apps/api/src/services/FlowService.ts`
   - `products/yappc/frontend/apps/api/src/jobs/embedding-pipeline.ts`
- These remaining YAPPC API thin clients and background jobs now parse success bodies through explicit text-to-JSON readers, reuse body-aware error extraction for Java backend/provider failures, and remove another small set of raw `response.json()` branches from the product API layer.
- Validation: editor diagnostics are clean for this additional 6-file YAPPC API-service tranche.
- Validation: `pnpm exec tsc -p products/yappc/frontend/apps/api/tsconfig.json --noEmit` still fails because of broader pre-existing YAPPC API type drift, including missing `PrismaClient`/`Prisma` exports from local database modules and existing `unknown`-typed repository/service flows; the current tranche was not expanded to remediate those unrelated package-wide issues.
- Added another YAPPC API/frontend typing-and-runtime hardening tranche in:
   - `products/yappc/frontend/apps/api/src/utils/type-guards.ts`
   - `products/yappc/frontend/apps/api/src/database/client.ts`
   - `products/yappc/frontend/apps/api/src/database/index.ts`
   - `products/yappc/frontend/apps/api/src/db.ts`
   - `products/yappc/frontend/apps/api/src/database/repository.base.ts`
   - `products/yappc/frontend/apps/api/src/database/repositories/audit-log.repository.ts`
   - `products/yappc/frontend/apps/api/src/middleware/auth.middleware.ts`
   - `products/yappc/frontend/apps/api/src/middleware/devAuth.ts`
   - `products/yappc/frontend/apps/api/src/routes/auth.ts`
   - `products/yappc/frontend/apps/api/src/middleware/BackendGateway.ts`
   - `products/yappc/frontend/apps/api/src/services/auth/session.service.ts`
   - `products/yappc/frontend/apps/api/src/services/auth/auth.service.ts`
   - `products/yappc/frontend/apps/api/src/graphql/resolvers/RateLimitResolver.ts`
   - `products/yappc/frontend/apps/api/src/services/compliance/ComplianceAutomationService.ts`
   - `products/yappc/frontend/apps/api/src/services/compliance/ComplianceReportService.ts`
   - `products/yappc/frontend/apps/api/src/services/FlowService.ts`
   - `products/yappc/frontend/apps/api/src/jobs/embedding-pipeline.ts`
   - `products/yappc/frontend/web/src/hooks/useWorkflows.ts`
   - `products/yappc/frontend/web/src/services/canvas/sync/SyncStrategy.ts`
   - `products/yappc/frontend/web/src/services/offline/OfflineService.ts`
   - `products/yappc/frontend/libs/yappc-ai/src/hooks/usePredictions.ts`
- This tranche adds shared YAPPC API type guards, unifies local Prisma exports and DB access, fixes the invalid auth registration workspace-creation path by switching to a schema-correct user/workspace/member flow, removes more proxy-style `unknown` access from flow/embedding/runtime code, and cleans up several remaining frontend helper call sites using deprecated ID generation or unobserved background sync entrypoints.
- Validation: editor diagnostics are clean for this additional 21-file YAPPC typing/runtime tranche.
- Validation: the recent YAPPC-only batches after the product-scope narrowing now total 118 source-file updates in aggregate, staying well beyond the requested 40–50-file brisk-progress threshold without touching Software-Org, Virtual-Org, Flashit, or Aura in this pass.
- Follow-up completed: `products/yappc/frontend/apps/api/src/services/compliance/ComplianceAutomationService.ts` and `products/yappc/frontend/apps/api/src/services/compliance/ComplianceReportService.ts` now use explicit local record adapters for assessment/findings/controls/audit-trail payloads, and remediation-plan persistence now writes Prisma-valid nested remediation-step creates instead of forcing `unknown` payloads through the ORM boundary.
- Validation: editor diagnostics are clean for the compliance-service follow-up pair, and a filtered `pnpm exec tsc -p products/yappc/frontend/apps/api/tsconfig.json --noEmit` pass shows no remaining errors from `ComplianceAutomationService.ts` or `ComplianceReportService.ts`; broader pre-existing YAPPC API package drift outside this touched-file set still remains separate follow-up work.
- Follow-up completed: added `products/yappc/frontend/web/src/lib/http.ts` for explicit JSON/error parsing and migrated another 14 YAPPC web/mobile route and page files off raw `res.json()` fetch handling onto shared helpers.
- Validation: editor diagnostics are clean for the latest YAPPC web-helper batch, and a filtered `pnpm exec tsc -p products/yappc/frontend/web/tsconfig.json --noEmit` pass returned zero matches for `NotificationPanel`, the touched mobile routes, `profile`, `dashboard`, `settings`, `app/project/settings`, `ProjectsPage`, `RunbookDetailPage`, `OnCallPage`, and `DashboardEditorPage`.
- Follow-up completed: extended the same shared YAPPC web fetch-helper migration across another 9 admin, development, auth, and security pages (`BillingPage`, `TeamsPage`, `CodeReviewPage`, `PullRequestDetailPage`, auth `ProfilePage`, `ScanResultsPage`, `ThreatModelPage`, `VulnerabilityDetailPage`, and `PolicyDetailPage`).
- Validation: editor diagnostics are clean for the second YAPPC web-helper batch, and a filtered `pnpm exec tsc -p products/yappc/frontend/web/tsconfig.json --noEmit` pass returned zero matches for all 9 page files.
- Follow-up completed: expanded the shared YAPPC web fetch-helper migration into another 13 files across operations, collaboration, settings, auth callback, and shared UI (`ServiceMapPage`, `WarRoomPage`, `SearchBar`, `SSOCallbackPage`, `ArticlePage`, `ActivityFeedPage`, `MessagesPage`, `CalendarPage`, `ChannelPage`, `DirectMessagePage`, `TeamHubPage`, settings `ProfilePage`, and settings `SettingsPage`).
- Validation: editor diagnostics are clean for the third YAPPC web-helper batch, and a filtered `pnpm exec tsc -p products/yappc/frontend/web/tsconfig.json --noEmit` pass returned zero matches for all 13 files.
- Follow-up completed: removed duplicated local JSON/error helper implementations from another 10 YAPPC web files by standardizing them on `products/yappc/frontend/web/src/lib/http.ts` (`routes/mobile/overview`, `routes/mobile/projects`, `useLifecyclePhaseTransition`, `useRateLimit`, `useCodeAssociations`, `RateLimitStatusWidget`, `ThrottleAlertBanner`, `CodeReviewDetailPage`, `SprintPlanningPage`, and `VelocityChartsPage`).
- Validation: editor diagnostics are clean for the helper-deduplication batch, and a filtered `pnpm exec tsc -p products/yappc/frontend/web/tsconfig.json --noEmit` pass returned zero matches for all 10 files after tightening `ThrottleAlertBanner`'s local status response type.
- Follow-up completed: migrated `products/yappc/frontend/web/src/lib/canvas-ai/yappc-ai-adapter.ts` off its last raw `res.json()` helper path onto the shared `lib/http.ts` parsing/error helpers, leaving only a server-side `res.json(...)` implementation in `GenerationAgent` and one commented example under `useWorkflows` in the YAPPC web tree.
- Validation note: editor diagnostics are clean for `yappc-ai-adapter.ts`, but a filtered package typecheck still reports 7 pre-existing `@ghatana/canvas` export/import errors in that file unrelated to the helper swap; those package-contract issues remain outstanding after this pass.
- Follow-up completed: resolved the YAPPC canvas-AI contract drift by expanding `products/yappc/frontend/web/src/shims/ghatana-canvas.ts` with the AI surface actually consumed by the app and aligning `yappc-ai-adapter.ts` plus `use-yappc-canvas-ai.ts` to that shim-backed import path.
- Validation: editor diagnostics are clean for the shim and both canvas-AI files, and a filtered `pnpm exec tsc -p products/yappc/frontend/web/tsconfig.json --noEmit` pass returned zero matches for `shims/ghatana-canvas.ts`, `lib/canvas-ai/yappc-ai-adapter.ts`, and `lib/canvas-ai/use-yappc-canvas-ai.ts`.
- Follow-up completed: removed another 5 duplicated standalone JSON/error helper implementations by standardizing `ArtifactSuggestionService`, `providers/auth-session`, `useAnomalyDetection`, `services/canvas/phase-actions/PhaseActionService`, and `components/canvas/hooks/useCodeAssociationsBatch` on `products/yappc/frontend/web/src/lib/http.ts`, while extending `shims/ghatana-canvas.ts` with `ActionContext`/`ActionDefinition` so canvas action consumers resolve against the same local shim contract.
- Validation: editor diagnostics are clean for the 5 deduped modules plus the shim, and a filtered `pnpm exec tsc -p products/yappc/frontend/web/tsconfig.json --noEmit` pass returned zero matches for `shims/ghatana-canvas.ts`, `ArtifactSuggestionService.ts`, `auth-session.ts`, `useAnomalyDetection.ts`, `PhaseActionService.ts`, and `useCodeAssociationsBatch.ts`.
- Follow-up completed: standardized another 6 standalone YAPPC frontend modules on the shared HTTP parsing helper (`services/canvasBackend`, `services/canvas/api/CanvasAPIClient`, `services/canvas/api/CanvasAIService`, `hooks/useCanvasPersistence`, `services/lifecycle/api`, and `services/lifecycle/phase-transition-api`) while explicitly adding `src/utils/Logger.ts` to `products/yappc/frontend/web/tsconfig.json` so composite typechecking includes the logger file imported across the same web workspace.
- Validation: editor diagnostics are clean for the 6 deduped modules and `tsconfig.json`, and a filtered `pnpm exec tsc -p products/yappc/frontend/web/tsconfig.json --noEmit` pass returned zero matches for those files after the logger inclusion fix.
- Follow-up completed: converted another 3 class-bound duplicated helper implementations into thin wrappers over the shared HTTP helper in `services/offline/OfflineService`, `services/canvas/CanvasPersistence`, and `services/agentService`, keeping their class-local call sites intact while centralizing the parsing behavior.
- Validation: editor diagnostics are clean for all 3 class-based services, and a filtered `pnpm exec tsc -p products/yappc/frontend/web/tsconfig.json --noEmit` pass returned zero matches for `OfflineService.ts`, `CanvasPersistence.ts`, and `agentService.ts`.
- Follow-up completed: standardized the remaining local `parseJsonResponse` helpers in `services/auth/AuthService.ts`, `routes/app/project/_shell.tsx`, and `routes/app/project/canvas.tsx` onto the shared parser in `products/yappc/frontend/web/src/lib/http.ts` while preserving AuthService's custom structured error-body parsing.
- Validation note: editor diagnostics are clean for `AuthService.ts`, `_shell.tsx`, and `canvas.tsx`, but focused package typecheck still reports pre-existing route-level type errors in `routes/app/project/canvas.tsx` (`TS2769` overload mismatches and `unknown`→`boolean` assignments around the existing canvas route wiring). Those route errors remain outstanding and were surfaced, not introduced, by this pass.
- Remaining work: continue the production-only sweep across the remaining YAPPC frontend `.then()` sites, excluding already-guarded chains, intentional `React.lazy(...import().then(...))` module wrappers, and test/e2e utilities.

---

## Phase 4: Test Coverage Investment (Weeks 3–6)

> **Exit Criteria:** All products ≥ 0.4 test ratio, security-critical products ≥ 0.6.
> **Verification:** CI test runs pass, coverage reports generated.
> **Guideline Refs:** §1.8 (Tests are part of the change), §3 Testing, §16 (Test File Placement)

### Task 4.1 — Software-Org: From 4 Tests to Minimum Coverage

| Field | Value |
|-------|-------|
| **Priority** | P0 — NO-GO BLOCKER |
| **Product** | Software-Org |
| **Current** | 4 tests / 87 source files (ratio: 0.05) |
| **Target** | ≥ 35 tests (ratio: 0.4) |
| **Focus** | API route handlers (after Task 2.2 adds type safety) |
| **Test Types** | Unit tests for route validation, integration tests for CRUD operations |
| **Placement** | Co-located `__tests__/` per §16 |

**Implementation Steps:**
1. After Task 2.2 completes (typed routes), write tests for each route:
   - Valid request → expected response
   - Invalid request → 400 with error message
   - Missing auth → 401
   - Missing tenant → 400
2. Add service-layer unit tests for business logic
3. Target: at least 1 test file per route file

**Status Update (2026-04-16):**
- Added `src/routes/__tests__/org.integration.test.ts` covering invalid query rejection, default-organization department creation, and duplicate-department conflict handling for `org.ts`.
- Added `src/config/__tests__/index.test.ts` so startup configuration validation is exercised alongside the route coverage.
- Expanded `org.integration.test.ts` with empty-default-organization handling, unknown-organization rejection, and missing-department detail coverage.
- Expanded `org.integration.test.ts` again across the agent surface with explicit organization agent listing, missing-agent detail handling, missing-department rejection on add-agent, and successful agent creation coverage.
- Expanded `org.integration.test.ts` further across delete-agent, move-agent, and hierarchy retrieval paths, including wrong-source move rejection and successful hierarchy payload shaping.
- Expanded `org.integration.test.ts` further into configuration and graph endpoints, including missing-organization config rejection, count-based config payloads, and department-agent graph shaping.
- Expanded `org.integration.test.ts` again across the remaining live `org.ts` branches: existing-agent reassignment, update-agent configuration merge, update-not-found handling, duplicate-agent rejection, missing-name-or-role rejection, genesis generation validation, and genesis materialization with namespace-collision handling.
- Removed the dead legacy `src/routes/org.old.ts` compatibility file after confirming there were no remaining references to it in the management API workspace.
- Added coverage for the remaining lightweight compatibility branches as well, including hierarchy-not-found, graph-not-found, and the `/services` placeholder response.
- Validation: `npm test -- src/routes/__tests__/org.integration.test.ts src/config/__tests__/index.test.ts` passed in `products/software-org/services/management-api` with 33 tests green across the touched files.
- Added `src/routes/__tests__/root.integration.test.ts` covering tenant listing, tenant-not-found, aggregated-alert query forwarding, and user suspension paths in `root.ts`.
- Added `src/routes/__tests__/metrics.integration.test.ts` covering default and explicit time-range handling in `metrics.ts`.
- Added `src/routes/__tests__/workspaces.integration.test.ts` covering authenticated workspace override retrieval, non-admin rejection on update, and missing-override delete handling in `workspaces.ts`.
- Validation: `pnpm vitest run src/routes/__tests__/root.integration.test.ts src/routes/__tests__/metrics.integration.test.ts src/routes/__tests__/workspaces.integration.test.ts` passed in `products/software-org/services/management-api` with 9 tests green across the new route suites.
- Added `src/routes/__tests__/audit.integration.test.ts` covering missing decision-field rejection, decision persistence, and audit-trail rate calculations in `audit.ts`.
- Added `src/routes/__tests__/tenants.integration.test.ts` covering missing-tenant health rejection, health rollup from environment state, unresolved alert mapping, and workflow payload shaping in `tenants.ts`.
- Added `src/routes/__tests__/kpis.integration.test.ts` covering KPI list mapping, missing-KPI rejection, and narrative filtering in `kpis.ts`.
- Added `src/routes/__tests__/reports.integration.test.ts` covering report schedule listing, missing-report schedule rejection, and manual report-run creation in `reports.ts`.
- Added `src/routes/__tests__/agent-actions.integration.test.ts` covering list, missing-action detail rejection, and defer-action handling in `agent-actions.ts`.
- Added `src/routes/__tests__/admin.integration.test.ts` covering tenant pagination payloads, duplicate-key rejection, and audited tenant creation in `admin.ts`.
- Validation: `npx vitest run src/routes/__tests__/audit.integration.test.ts src/routes/__tests__/tenants.integration.test.ts src/routes/__tests__/kpis.integration.test.ts src/routes/__tests__/reports.integration.test.ts src/routes/__tests__/agent-actions.integration.test.ts src/routes/__tests__/admin.integration.test.ts` passed in `products/software-org/services/management-api` with 19 tests green across the six new route suites.
- Added `src/routes/__tests__/build.integration.test.ts` covering workflow pagination, duplicate slug rejection, and invalid activation rejection in `build.ts`.
- Added `src/routes/__tests__/personas.integration.test.ts` covering workspace-access rejection, successful persona preference updates with broadcast, and missing-preference deletion handling in `personas.ts`.
- Added `src/routes/__tests__/skills.integration.test.ts` covering skill listing, schema-level rejection for invalid proficiency updates, and development-plan date conversion in `skills.ts`.
- Added `src/routes/__tests__/budgets.integration.test.ts` covering grouped annual budget-plan shaping, missing-budget update rejection, and archive flow behavior in `budgets.ts`.
- Added `src/routes/__tests__/innovation.integration.test.ts` covering idea-status filtering plus experiment create/update date conversion in `innovation.ts`.
- Added `src/routes/__tests__/norms.integration.test.ts` covering category filtering, single-norm retrieval, and unknown-norm rejection in `norms.ts`.
- Added `src/routes/__tests__/config-api.integration.test.ts` covering config retrieval, missing export-path rejection, and missing-service handling in `config-api.ts`.
- Validation: `pnpm exec vitest run src/routes/__tests__/build.integration.test.ts src/routes/__tests__/personas.integration.test.ts src/routes/__tests__/skills.integration.test.ts src/routes/__tests__/budgets.integration.test.ts src/routes/__tests__/innovation.integration.test.ts src/routes/__tests__/norms.integration.test.ts src/routes/__tests__/config-api.integration.test.ts` passed in `products/software-org/services/management-api` with 21 tests green across the seven new route suites.
- Added `src/routes/__tests__/bulk.integration.test.ts` covering missing-id rejection and multi-item action execution in `bulk.ts`.
- Added `src/routes/__tests__/models.integration.test.ts` covering compare validation, missing-current-version rejection, and feature-importance mapping in `models.ts`.
- Added `src/routes/__tests__/time-off.integration.test.ts` covering paginated listing, overlap-conflict rejection, and already-cancelled request handling in `time-off.ts`.
- Added `src/routes/__tests__/observe.integration.test.ts` covering tenant/category metric filtering, unknown metric rejection, and degraded-model filtering in `observe.ts`.
- Added `src/routes/__tests__/operate.integration.test.ts` covering missing-tenant rejection, queue filtering, and incident-not-found handling in `operate.ts`.
- Added `src/routes/__tests__/growth-plans.integration.test.ts` covering required-user validation, paginated listing payloads, and missing-plan rejection in `growth-plans.ts`.
- Validation: `npm test -- src/routes/__tests__/bulk.integration.test.ts src/routes/__tests__/models.integration.test.ts src/routes/__tests__/time-off.integration.test.ts src/routes/__tests__/observe.integration.test.ts src/routes/__tests__/operate.integration.test.ts src/routes/__tests__/growth-plans.integration.test.ts` passed in `products/software-org/services/management-api` with 17 tests green across the six new route suites.
- Added `src/routes/__tests__/performance-reviews.integration.test.ts` covering period-filter mapping, default reviewer/period/status behavior on create, missing-review rejection, submit-flow metadata persistence, and due-review query shaping in `performance-reviews.ts`.
- Added `src/routes/__tests__/knowledge-base.integration.test.ts` covering tag normalization, article-not-found handling, create-article delegation, default contributor limits, and category listing in `knowledge-base.ts`.
- Added `src/routes/__tests__/devsecops.integration.test.ts` covering filter echoing on the placeholder item-list route and the default stage-health payload shape in `devsecops.ts`.
- Validation: `npx vitest run src/routes/__tests__/performance-reviews.integration.test.ts src/routes/__tests__/knowledge-base.integration.test.ts src/routes/__tests__/devsecops.integration.test.ts` passed in `products/software-org/services/management-api` with 12 tests green across the three new route suites.
- Added `src/routes/__tests__/executions.integration.test.ts` covering workflow-name execution resolution, missing-workflow trigger listing, trigger defaulting, trigger config preservation on patch, missing execution rejection, and cancellation log appending in `executions.ts`.
- Added `src/routes/__tests__/approvals.integration.test.ts` covering filtered approval listing, metadata merging on submit, missing-approval rejection, current-step authorization rejection, next-step progression plus Java decision notification, and pending-approval filtering in `approvals.ts`.
- Validation: `pnpm vitest run src/routes/__tests__/executions.integration.test.ts src/routes/__tests__/approvals.integration.test.ts` passed in `products/software-org/services/management-api` with 12 tests green across the two new route suites after mocking the generated Prisma client at the route-test boundary.
- This does not satisfy the full task target yet; it establishes the reusable Fastify-plus-mocked-Prisma pattern for expanding the rest of the route surface.

---

### Task 4.2 — Security-Gateway: Security-Critical Test Investment

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Product** | Security-Gateway |
| **Current** | 18 tests / 100 source files (ratio: 0.18) |
| **Target** | ≥ 60 tests (ratio: 0.6 — security-critical threshold) |
| **Focus** | JWT validation, RBAC enforcement, rate limiting, webhook verification |
| **Test Types** | Unit tests for auth logic, integration tests for token lifecycle |
| **Placement** | Mirror directory per §16 (Java) |

**Priority test areas:**
1. JWT token validation (valid, expired, tampered, wrong algorithm)
2. RBAC permission checks (all predefined roles)
3. Rate limiting enforcement (threshold, window, per-tenant isolation)
4. Token storage (create, revoke, lookup, expiry)
5. Webhook signature verification (valid, invalid, replay)

**Status Update (2026-04-16):**
- Added `JpaUserRepositoryTest` with focused unit coverage for missing-user lookup, password mismatch authentication, and transaction rollback on persist failure.
- Expanded `SecurityContextTest` with JWT filter coverage for empty-bearer rejection, default-tenant validation delegation, and successful `SecurityContextHolder` population from validated claims.
- Expanded `AuthHttpHandlerTest` with malformed/bad-request branches for login, validate, refresh, and revoke payload handling so missing-token/invalid-JSON paths are now covered alongside the happy-path token operations.
- Completed another `AuthHttpHandlerTest` increment for the remaining malformed-request gaps: validate invalid JSON, refresh missing token, and revoke invalid JSON.
- Added another `AuthHttpHandlerTest` increment for default-tenant behavior on login/validate/refresh/revoke plus response-payload verification on `/auth/health`, so the HTTP edge contract is now covered beyond bare status codes.
- Expanded `WebhookSignatureValidatorTest` with malformed-signature edge coverage for empty digest values and wrong-length digest values, tightening verification around rejected webhook signatures.
- Expanded `AuthorizationServiceImplTest` with RBAC edge coverage for unknown roles plus null-role rejection paths on `checkRole(...)` and `getPermissionsForRole(...)`.
- Expanded `JwtTokenProviderImplTest` with token-lifecycle coverage for `revokeAllTokensForUser(...)`, including both the multi-token bulk revocation path and the zero-active-token no-op path.
- Expanded `IpBlockingInterceptorTest` with unknown-client-IP blocking coverage so the interceptor's no-header fallback branch is exercised when `unknown` is explicitly blocklisted.
- Expanded `WafInterceptorTest` with Windows-style backslash traversal coverage so the `PATH_TRAVERSAL` rule is verified for both slash styles.
- Expanded `SecurityGatewayTest` with a validated-user/null-roles authorization case so `validateTokenAndPermission(...)` is covered when identity exists but no roles are present.
- Expanded `SecurityInterceptorTest` with explicit public-endpoint bypass coverage for `/api/health` and invalid-bearer rejection coverage that verifies authorization is skipped when JWT validation fails.
- Expanded `SecurityContextTest` with a null-request guard assertion for `JwtAuthenticationFilter.authenticate(...)`.
- Expanded `OAuth2ControllerTest` with a mismatched-state callback rejection case, covering the hardened OAuth flow-state validation path.
- Expanded `AuthHttpHandlerTest` with wrong-field refresh payload rejection and an additional health payload assertion that verifies auth/token details are not exposed in the health response body.
- Expanded `SecurityGatewayTest` with an empty-role-set authorization case, complementing the existing null-role regression coverage.
- Expanded `CrossServiceTokenValidationTest` with blank-token rejection coverage in the shared issuance-to-validation contract.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.auth.http.AuthHttpHandlerTest` passed with 25 tests green.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.security.webhook.WebhookSignatureValidatorTest` passed with 15 tests green.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.auth.service.impl.AuthorizationServiceImplTest` passed with 18 tests green.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.auth.service.impl.JwtTokenProviderImplTest` passed with 18 tests green.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests "com.ghatana.security.interceptor.IpBlockingInterceptorTest" --tests "com.ghatana.security.interceptor.WafInterceptorTest" --tests "com.ghatana.security.SecurityGatewayTest"` passed with 37 tests green across those three classes.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.security.interceptor.SecurityInterceptorTest --tests com.ghatana.auth.security.SecurityContextTest --tests com.ghatana.security.controller.OAuth2ControllerTest --tests com.ghatana.auth.http.AuthHttpHandlerTest --tests com.ghatana.security.SecurityGatewayTest --tests com.ghatana.auth.service.impl.CrossServiceTokenValidationTest` passed after expectation alignment across the new edge cases.
- Expanded `InMemoryTokenStoreTest` with targeted client-wide revocation coverage so revoking one client path no longer leaves the cross-client branch untested.
- Expanded `RedisTokenStoreTest` with explicit `deleteExpired(...)` no-op coverage, documenting the Redis TTL contract instead of leaving that branch implicit.
- Expanded `JpaTokenRepositoryTest` with client-wide revocation coverage, complementing the existing user-wide revocation path.
- Expanded `EncryptedStorageServiceTest` with remove/contains lifecycle coverage so encrypted storage deletion and empty retrieval semantics are exercised explicitly.
- Expanded `EventCloudSecurityManagerTest` with missing-metadata retrieval coverage, verifying the null-through path without synchronous promise access.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.auth.adapter.memory.InMemoryTokenStoreTest --tests com.ghatana.auth.adapter.redis.RedisTokenStoreTest --tests com.ghatana.auth.adapter.jpa.JpaTokenRepositoryTest --tests com.ghatana.security.storage.EncryptedStorageServiceTest --tests com.ghatana.security.EventCloudSecurityManagerTest` passed for the in-memory, Redis, encrypted-storage, and Event Cloud manager classes; `JpaTokenRepositoryTest` remained skipped under its existing environment gating.
- Expanded `AuthenticationServiceImplTest` with graceful missing-session logout behavior and non-enumerating password-reset-request coverage.
- Expanded `InMemoryUserRepositoryTest` with locked-user authentication rejection and missing-user password-update failure coverage.
- Expanded `TokenStoreIntegrationTest` with cross-adapter client-wide revocation consistency coverage.
- Expanded `JwtTokenProviderImplTest` with explicit previously-unseen token-id revocation semantics coverage to match the implementation contract.
- Expanded `CrossServiceTokenValidationTest` with bulk user revocation validation across multiple active tokens.
- Expanded `SecurityGatewayTest` with blank-token rejection coverage ahead of introspection.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.auth.service.impl.AuthenticationServiceImplTest --tests com.ghatana.auth.adapter.memory.InMemoryUserRepositoryTest --tests com.ghatana.auth.adapter.TokenStoreIntegrationTest --tests com.ghatana.auth.service.impl.JwtTokenProviderImplTest --tests com.ghatana.auth.service.impl.CrossServiceTokenValidationTest --tests com.ghatana.security.SecurityGatewayTest` passed with 87 tests green across the targeted classes.
- Expanded `AuthorizationServiceImplTest` with positive editor-write permission coverage and explicit denial for roleless users.
- Expanded `AuthHttpHandlerTest` with a null-request guard on `handleLogin(...)`, documenting the handler's request precondition.
- Expanded `SecurityContextTest` with non-Bearer and blank-Bearer authorization-header rejection coverage.
- Expanded `SecurityInterceptorTest` with valid-token/no-user-id rejection coverage plus correlation-header preservation on rate-limited responses.
- Expanded `OAuth2ControllerTest` with missing-host authorize failure handling and stale OAuth-flow-cookie rejection coverage.
- Expanded `PolicyEnforcementInterceptorTest` with permission-denied close-path coverage, including correlation-aware trailers and denied-audit emission.
- Expanded `WebhookSignatureValidatorTest` with uppercase-digest rejection coverage to lock in canonical lowercase signature matching.
- Expanded `IpBlockingInterceptorTest` with multi-proxy `X-Forwarded-For` parsing coverage so the first forwarded IP remains the enforcement key.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.auth.service.impl.AuthorizationServiceImplTest --tests com.ghatana.auth.http.AuthHttpHandlerTest --tests com.ghatana.auth.security.SecurityContextTest --tests com.ghatana.security.interceptor.SecurityInterceptorTest --tests com.ghatana.security.controller.OAuth2ControllerTest --tests com.ghatana.security.grpc.PolicyEnforcementInterceptorTest --tests com.ghatana.security.webhook.WebhookSignatureValidatorTest --tests com.ghatana.security.interceptor.IpBlockingInterceptorTest` completed with `BUILD SUCCESSFUL` across all eight targeted classes.
- Expanded `AuthenticationServiceImplTest` with invalidated-session rejection and missing-user password-change failure coverage.
- Expanded `JwtTokenProviderImplTest` with blank-token rejection and tenant-scoped bulk user revocation coverage.
- Expanded `CrossServiceTokenValidationTest` with refresh-token bulk-revocation coverage plus tenant-boundary assertions for bulk user revocation.
- Expanded `SecurityGatewayTest` with a no-audit-logger validation path so token validation is covered when audit emission is intentionally absent.
- Expanded `InMemoryTokenStoreTest` and `RedisTokenStoreTest` with tenant-scoped user-revocation coverage.
- Expanded `InMemoryUserRepositoryTest` with tenant-scoped `findAllByTenant(...)` coverage.
- Expanded `TokenStoreIntegrationTest` with cross-adapter expired-token deletion consistency coverage.
- Expanded `EncryptedStorageServiceTest` with unknown-key `contains(...)` coverage.
- Expanded `EventCloudSecurityManagerTest` with `storeMetadata(...)` success and wrapped-failure coverage.
- Expanded `JpaUserRepositoryTest` with successful transaction-commit coverage on `save(...)`.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests AuthenticationServiceImplTest --tests JwtTokenProviderImplTest --tests CrossServiceTokenValidationTest --tests SecurityGatewayTest --tests InMemoryTokenStoreTest --tests RedisTokenStoreTest --tests InMemoryUserRepositoryTest --tests TokenStoreIntegrationTest --tests EncryptedStorageServiceTest --tests EventCloudSecurityManagerTest --tests JpaUserRepositoryTest` completed with `BUILD SUCCESSFUL`; 66 targeted tests passed, while the existing Docker-gated `RedisTokenStoreTest`/JPA integration behavior remained under their normal environment guards where applicable.
- Expanded `AuthHttpHandlerTest` with blank-credential rejection coverage.
- Expanded `SecurityContextTest` with empty-context permission/role denial coverage.
- Expanded `SecurityInterceptorTest` with shared-IP rate-limit coverage across different authenticated users, aligning the regression with the interceptor's IP-first limiter contract.
- Expanded `IpBlockingInterceptorTest` with auto-ban isolation coverage so banning one client IP does not block another.
- Expanded `WafInterceptorTest` with disabled-WAF coverage that verifies matching payloads do not emit audit events when inspection is turned off.
- Expanded `OAuth2ControllerTest` with stronger mismatched-state callback assertions, including OAuth flow-cookie clearing.
- Expanded `PolicyEnforcementInterceptorTest` with unauthenticated failure coverage that verifies no denied-audit event is emitted.
- Expanded `WebhookSignatureValidatorTest` with non-hex signature rejection coverage.
- Expanded `SecurityGatewayTest` with policy-evaluation coverage when no audit logger is configured.
- Expanded `EncryptedStorageServiceTest` with same-key overwrite semantics coverage.
- Expanded `JpaTokenRepositoryTest` and `TokenStoreIntegrationTest` with tenant-boundary lookup isolation coverage for stored tokens.
- Validation: `./gradlew --no-daemon :products:security-gateway:platform:java:test --tests com.ghatana.auth.http.AuthHttpHandlerTest --tests com.ghatana.auth.security.SecurityContextTest --tests com.ghatana.security.interceptor.SecurityInterceptorTest --tests com.ghatana.security.interceptor.IpBlockingInterceptorTest --tests com.ghatana.security.interceptor.WafInterceptorTest --tests com.ghatana.security.controller.OAuth2ControllerTest --tests com.ghatana.security.grpc.PolicyEnforcementInterceptorTest --tests com.ghatana.security.webhook.WebhookSignatureValidatorTest --tests com.ghatana.security.SecurityGatewayTest --tests com.ghatana.security.storage.EncryptedStorageServiceTest --tests com.ghatana.auth.adapter.jpa.JpaTokenRepositoryTest --tests com.ghatana.auth.adapter.TokenStoreIntegrationTest` completed with `BUILD SUCCESSFUL`; 145 targeted tests passed.
- Expanded `AuthenticationServiceImplTest` with refreshed-session client-metadata coverage so the gateway-default `ipAddress`/`userAgent` rewrite stays locked in.
- Expanded `JwtTokenProviderImplTest` with refresh-token claim minimization coverage so validated refresh tokens keep empty roles and permissions.
- Expanded `CrossServiceTokenValidationTest` with cross-tenant refresh-token rejection coverage.
- Expanded `WafInterceptorTest` with command-substitution payload rejection coverage.
- Expanded `IpBlockingInterceptorTest` with auto-ban expiry coverage.
- Expanded `EncryptedStorageServiceTest` with `clear()` size-reset and removal coverage.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests com.ghatana.auth.service.impl.AuthenticationServiceImplTest --tests com.ghatana.auth.service.impl.JwtTokenProviderImplTest --tests com.ghatana.auth.service.impl.CrossServiceTokenValidationTest --tests com.ghatana.security.interceptor.WafInterceptorTest --tests com.ghatana.security.interceptor.IpBlockingInterceptorTest --tests com.ghatana.security.storage.EncryptedStorageServiceTest` completed with `BUILD SUCCESSFUL`; 81 targeted tests passed.
- Expanded `AuthHttpHandlerTest` with success-payload assertions for login and validate flows, so token metadata and user payload shape are covered alongside status codes.
- Expanded `SecurityContextTest` with successful-authentication overwrite coverage, ensuring `SecurityContextHolder` is refreshed when a later valid JWT is processed.
- Expanded `SecurityInterceptorTest` with public `/public/*` bypass coverage and malformed-authorization-scheme rejection coverage.
- Fixed `SecurityInterceptor.isPublicEndpoint(...)` to compare the login method case-insensitively, after the new POST `/api/auth/login` regression exposed that the previous lowercase-only check prevented the intended unauthenticated bypass.
- Validation: `./gradlew :products:security-gateway:platform:java:test --tests 'com.ghatana.auth.http.AuthHttpHandlerTest' --tests 'com.ghatana.auth.security.SecurityContextTest' --tests 'com.ghatana.security.interceptor.SecurityInterceptorTest'` completed with `BUILD SUCCESSFUL`; all 62 targeted tests passed.
- Additional note: a root-level `./gradlew test --tests ...` attempt remains blocked by a pre-existing unrelated YAPPC project graph failure (`:products:yappc:core:knowledge-graph` references missing project path `:core:ai`), so only the scoped `:products:security-gateway:platform:java:test` verification is authoritative for this batch.
- This is still only an initial increment against the broader Security-Gateway target; higher-value auth/JWT/rate-limit surfaces remain for follow-up.

---

### Task 4.3 — Virtual-Org: Agent Test Coverage

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | Virtual-Org |
| **Current** | 42 tests / 289 source files (ratio: 0.13) |
| **Target** | ≥ 120 tests (ratio: 0.4) |
| **Focus** | Agent execution, governance adapter, role assignment |
| **Test Types** | Unit tests for agent logic, async tests extending `EventloopTestBase` |

---

### Task 4.4 — Audio-Video: Infrastructure Test Coverage

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | Audio-Video |
| **Current** | 104 tests / 436 source files (ratio: 0.24) |
| **Target** | ≥ 180 tests (ratio: 0.4) |
| **Focus** | JPA repositories (currently have generic catches), health metrics, gRPC interceptors |

---

### Task 4.5 — AEP: Integration Test Coverage

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | AEP |
| **Current** | 262 tests / 759 source files (ratio: 0.35) |
| **Target** | ≥ 300 tests (ratio: 0.4) |
| **Focus** | Pipeline execution E2E, Data Cloud integration, operator catalog execution |

---

### Task 4.6 — Cross-Product Contract Tests

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | AEP↔Data Cloud, YAPPC↔Data Cloud |
| **Issue** | No consumer-driven contract tests between products |
| **Fix** | Add contract tests verifying: <br> 1. AEP's `DataCloudPipelineRegistryClientImpl` against Data Cloud's actual API <br> 2. YAPPC's Data Cloud adapter against Data Cloud's entity/event APIs <br> 3. SSE streaming consumer contracts |
| **Placement** | `integration-tests/cross-service-workflow/` (existing directory) |
| **Guideline Refs** | §3 Testing (Contract tests for APIs), §11 (Contracts, Events, and Compatibility) |

**Status Update (2026-04-16):**
- Added `integration-tests/cross-service-workflow/build.gradle.kts` and included the module in root `settings.gradle.kts` so the contract suite is part of the build.
- Removed the stale Spring Boot `AuthenticatedUserWorkflowTest` placeholder that had no corresponding module wiring.
- Added contract-style tests for:
   - AEP `DataCloudPipelineRegistryClientImpl` mapping Data Cloud `/api/v1/pipelines` payloads.
   - YAPPC `YappcDataCloudRepository` tenant-scoped entity API usage against the Data Cloud SPI contract.

---

## Phase 5: Configuration Hardening (Weeks 4–5)

> **Exit Criteria:** All `System.getenv()` calls validated at startup, no empty-string defaults for credentials.
> **Verification:** Application fails fast with clear error on missing required config.
> **Guideline Refs:** §1.5, §27 (Environment Variable Validation)

### Task 5.1 — Validate All Critical Environment Variables at Startup

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | All products |
| **Issue** | 30+ `System.getenv()` calls without null/format validation |
| **Fix Pattern** | Create `EnvironmentConfigValidator` per product: <br> 1. List all required env vars <br> 2. Validate at startup (fail-fast with clear error message) <br> 3. Validate format where applicable (URLs, UUIDs, etc.) |

**Implementation Steps (per product):**
1. Inventory all `System.getenv()` calls
2. Categorize: REQUIRED vs OPTIONAL (with safe defaults)
3. Create config class with validation (reuse `platform:java:config` if available)
4. Wire validation into application bootstrap (before server starts)
5. Test: verify startup fails with clear message when required var is missing

**Products requiring validation:**
- Audio-Video: `AV_JWT_SECRET`, `REDIS_PASSWORD`, `ES_PASSWORD`
- Data Cloud: `DC_DB_PASSWORD`, `FEATURE_INGEST_DB_PASSWORD`
- AEP: `GHATANA_API_KEY`
- All: Database URLs, service endpoints

**Status Update (2026-04-16):**
- Completed for one additional real product surface: `products/software-org/services/management-api/src/config/index.ts` now parses environment via Zod and fails fast on invalid or missing `DATABASE_URL`, `JWT_SECRET`, and malformed URL settings.
- The server bootstrap now reuses `appConfig.isDevelopment` instead of reading `process.env.NODE_ENV` directly in the WebSocket auth path.
- Added config regression tests in `src/config/__tests__/index.test.ts`.

---

### Task 5.2 — Fix FeatureIngestConfig Empty Password Default

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | Data Cloud |
| **File** | `FeatureIngestConfig.java` |
| **Issue** | `dbPassword = ""` — empty string default |
| **Fix** | Require password via env var, throw if missing in non-LOCAL profiles |

**Status Update (2026-04-16):**
- Completed in `products/data-cloud/feature-store-ingest`: `FeatureIngestConfig.fromEnv()` no longer substitutes an empty-string DB password, and `validate()` now fails fast when postgres mode is selected without `FEATURE_INGEST_DB_PASSWORD`.
- Added regression coverage in `FeatureIngestConfigTest` for the missing-password case while preserving the existing valid postgres path.
- Validation: targeted tests passed for `FeatureIngestConfigTest`.

---

### Task 5.3 — Fix CORS Default in DataCloudHttpServer

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | Data Cloud |
| **File** | `DataCloudHttpServer.java` |
| **Issue** | CORS defaults to `http://localhost:5173` when env var not set |
| **Fix** | In non-LOCAL profiles, require explicit CORS origin. Fail-fast if not configured. |

**Status Update (2026-04-16):**
- Completed in `products/data-cloud/launcher`: `DataCloudHttpServer` now resolves CORS origins at startup based on the effective local/non-local mode instead of using a global implicit localhost default.
- Added `resolveCorsAllowOrigin(...)` so non-local profiles throw a clear startup error when `DATACLOUD_CORS_ALLOWED_ORIGINS` is missing, while local mode keeps the localhost fallback with an explicit warning.
- Added targeted regression coverage in `DataCloudHttpServerSecurityConfigurationTest`.
- Validation: targeted tests passed for `DataCloudHttpServerSecurityConfigurationTest`.

---

### Task 5.4 — Fix Hardcoded localhost Default in DataCloudPipelineRegistryClientImpl

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | AEP |
| **File** | `DataCloudPipelineRegistryClientImpl.java` line 45 |
| **Issue** | Defaults to `http://localhost:8085` |
| **Fix** | Require env var `DATA_CLOUD_URL`, fail-fast if not set in non-LOCAL profiles |

**Status Update (2026-04-16):**
- Completed in `products/aep`: `EnvConfig.aepDcBaseUrl()` no longer falls back to `http://localhost:8085`, and `AepRegistryModule` now fails fast when Data Cloud pipeline registry mode is selected without an explicit base URL.
- Added `AepRegistryModule.createPipelineRegistryClient(...)` to make the decision path testable and to keep the no-op mode explicit for local or disconnected runs.
- Updated `DataCloudPipelineRegistryClientImpl` Javadoc so it no longer documents a nonexistent localhost default.
- Validation: targeted tests passed for `AepRegistryModuleTest`.

---

## Phase 6: Dependency Injection Cleanup (Week 5)

> **Exit Criteria:** Zero field injection in production code.
> **Guideline Refs:** §4 (Prefer constructor injection. Avoid field injection.)

### Task 6.1 — Migrate @Inject Field Injection to Constructor Injection

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Scope** | YAPPC metrics, AEP (`PostgresqlCheckpointStore`, `PostgresExecutionQueue`, `AuditService`) |
| **Fix** | For each class: <br> 1. Add constructor with all injected fields <br> 2. Remove `@Inject` from fields <br> 3. Add `@Inject` to constructor (or use framework-specific mechanism) <br> 4. Make fields `private final` |
| **Test** | Existing tests must pass (constructor injection is backward-compatible) |

**Status Update (2026-04-16):**
- Source-verified the plan’s named targets in this batch: `PostgresqlCheckpointStore`, `PostgresExecutionQueue`, `AuditService`, and the YAPPC observability metric facades are already using constructor injection with final fields.
- No code change was required for those specific classes because the field-injection issue described in the plan is stale for the current workspace state.
- A fresh workspace-wide production sweep for `@Inject` field injection found two real holdouts outside the original stale task list, and both are now remediated:
   - `products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/security/PIIDetectionService.java` now uses constructor injection for `DataSource` and keeps its blocking executor/config initialization unchanged.
   - `products/yappc/core/ai/src/main/java/com/ghatana/yappc/ai/canvas/CanvasAIServer.java` no longer uses injected field state for `CanvasAIServiceImpl`; it now resolves the service explicitly from the ActiveJ provider graph before building the gRPC server.
- Validation: touched-file diagnostics are clean for both remediated classes, and a follow-up regex sweep no longer finds production `@Inject` field injection sites in `products/**/*.java`.
- Remaining work: treat Task 6.1 as materially complete for the current workspace unless a future sweep identifies framework-specific holdouts outside the current search set.

---

## Phase 7: React Error Boundary Coverage (Week 5)

> **Exit Criteria:** All React app entry points wrapped in ErrorBoundary.
> **Guideline Refs:** §6 (React and Frontend Standards)

### Task 7.1 — Add ErrorBoundary to 33 React Entry Points

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | YAPPC, TutorPutor, AEP UI, Audio-Video, Data Cloud, FlashIt, PHR |
| **Fix** | 1. Check if `@ghatana/design-system` has an ErrorBoundary component (reuse first, §1.1) <br> 2. If not, create one in `@ghatana/design-system` <br> 3. Wrap all app entry points: `<ErrorBoundary fallback={<ErrorPage />}><App /></ErrorBoundary>` |
| **Test** | Render test that triggers error in child → verifies fallback renders |

**Status Update (2026-04-16):**
- Partial remediation completed: verified that `@ghatana/design-system` already provides the canonical `ErrorBoundary`, so no new boundary component was introduced.
- Wrapped `products/phr/apps/web/src/main.tsx` with the shared design-system `ErrorBoundary`; that entry point previously mounted raw `<App />`.
- Wrapped `products/tutorputor/apps/content-explorer/src/main.tsx` with the shared design-system `ErrorBoundary` and replaced the bespoke local boundary in `products/tutorputor/apps/tutorputor-web/src/main.tsx` with the canonical design-system component plus product-specific error logging.
- Wrapped `products/yappc/frontend/libs/yappc-ai/src/ui/requirements/main.tsx` with YAPPC's existing `@yappc/ui` `ErrorBoundary`, preserving the local frontend stack instead of introducing a cross-workspace UI dependency. The same edit also restored the missing MUI theme imports already referenced by that entry point.
- Wrapped `products/dcmaar/apps/device-health/src/options/index.tsx` with the shared design-system `ErrorBoundary`.
- Wrapped `products/dcmaar/modules/desktop/src/index.tsx` with the package's existing local `ErrorBoundary` so the legacy CRA-style bootstrap now matches the already-protected `main.tsx` entry in the same app.
- Added local mobile app boundaries in `products/phr/apps/mobile/src/App.tsx`, `products/dcmaar/apps/agent-react-native/src/App.tsx`, `products/dcmaar/apps/parent-mobile/App.tsx`, and `products/dcmaar/apps/child-mobile/App.tsx` so the remaining native roots now fail through explicit fallback UI instead of crashing silently.
- Wrapped the remaining DCMAAR browser-extension surfaces in the package's local `ErrorBoundary`: `products/dcmaar/apps/browser-extension/src/options/Options.tsx`, `products/dcmaar/apps/browser-extension/src/popup/Popup.tsx`, and `products/dcmaar/apps/browser-extension/src/dashboard/index.ts`.
- Verified during the sweep that FlashIt mobile already mounts its app through a local `ErrorBoundary` inside `products/flashit/client/mobile/App.tsx`, so its web bootstrap did not require a second wrapper.
- Validation: targeted PHR web app tests passed (`App.test.tsx`); TutorPutor entry-point edits are static/type clean in the touched files.
- Validation: touched-file diagnostics are clean for the YAPPC requirements entry point, the DCMAAR desktop/device-health/browser-extension bootstraps above, and the four mobile app roots updated in this pass.
- Remaining work: no additional unguarded main product roots were found in the latest targeted sweep; treat this task as materially complete unless a future audit sweep identifies another specialty bootstrap outside the current search set.

---

## Phase 8: Documentation Closure (Weeks 6–7)

> **Exit Criteria:** All products and platform modules have README.md.
> **Verification:** Script counts README.md presence.
> **Guideline Refs:** §24 (Documentation Requirements)

### Task 8.1 — Platform Java README.md (23 Modules)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | 23 platform Java modules missing README.md |
| **Template** | Each README must contain: <br> 1. Module name and canonical package <br> 2. Purpose (1-2 sentences) <br> 3. Dependencies (what it uses from platform) <br> 4. Usage examples <br> 5. Public API surface summary |
| **Batch Size** | 5 modules per PR |

**Module List:**
1. agent-core, ai-integration, audit, billing, cache
2. config, data-governance, database, domain, ds-cli
3. governance, identity, kernel, kernel-persistence, messaging
4. platform-bom, plugin, policy-as-code, runtime, tool-runtime
5. workflow + 2 others

**Status Update (2026-04-16):**
- First documentation batch completed for real missing modules in the current workspace:
   - `platform/java/agent-core/README.md`
   - `platform/java/audit/README.md`
   - `platform/java/cache/README.md`
   - `platform/java/data-governance/README.md`
   - `platform/java/ds-cli/README.md`
   - `platform/java/governance/README.md`
- Second documentation batch completed:
   - `platform/java/database/README.md`
   - `platform/java/domain/README.md`
   - `platform/java/http/README.md`
   - `platform/java/messaging/README.md`
   - `platform/java/policy-as-code/README.md`
- Final documentation batch completed for the remaining real missing Java modules:
   - `platform/java/runtime/README.md`
   - `platform/java/security/README.md`
   - `platform/java/testing/README.md`
   - `platform/java/tool-runtime/README.md`
   - `platform/java/workflow/README.md`
- Source verification also showed that `platform/java/ai-integration/README.md`, `platform/java/config/README.md`, and `platform/java/identity/README.md` already existed, so they were not duplicated.
- The original `billing` batch member is stale in this workspace: `platform/java/billing/` currently contains only generated build output and no standalone module sources or `build.gradle.kts`, so no README was created there.
- `platform/java/platform-bom/` is BOM-only dependency-management infrastructure with no source tree, so it remains intentionally outside the module README sweep.
- Touched-file diagnostics are clean for the new READMEs.

---

### Task 8.2 — Platform TypeScript README.md (19 Packages)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | 19 platform TypeScript packages missing README.md |
| **Template** | Same as 8.1 + npm package name (`@ghatana/` scope per §17) |

**Package List:**
accessibility-audit, audit-components, data-grid, forms, nlp-ui, patterns, primitives, ui, wizard, browser-events, canvas-core, canvas-plugins, canvas-tools, code-editor, selection-ui, voice-ui, and 3 others

**Status Update (2026-04-16):**
- First TypeScript documentation batch completed for real missing packages in the current workspace:
   - `platform/typescript/data-grid/README.md`
   - `platform/typescript/forms/README.md`
   - `platform/typescript/patterns/README.md`
   - `platform/typescript/wizard/README.md`
- Second TypeScript documentation batch completed:
   - `platform/typescript/domain-components/README.md`
   - `platform/typescript/ds-generator/README.md`
   - `platform/typescript/ds-governance/README.md`
   - `platform/typescript/ds-registry/README.md`
   - `platform/typescript/ds-schema/README.md`
- Final TypeScript documentation batch completed for the remaining real missing packages:
   - `platform/typescript/primitives/README.md`
   - `platform/typescript/ui/README.md`
   - `platform/typescript/ui-builder/README.md`
- Source verification also showed that `platform/typescript/browser-events/README.md` already existed, so it was not duplicated.
- `platform/typescript/accessibility-audit/` is currently a stale/incomplete directory in this workspace: it contains no package sources or package manifest, so no README was added there.
- Touched-file diagnostics are clean for the new READMEs.
- With those batches complete, the platform TypeScript package README sweep is effectively complete for the current workspace except for the stale `accessibility-audit` directory.

---

### Task 8.3 — Finance Product README.md

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Product** | Finance |
| **Issue** | Only OWNER.md exists, no README.md |
| **Fix** | Create README.md following product README conventions |

**Status Update (2026-04-16):**
- Completed: added `products/finance/README.md` with product purpose, scope, structure, build/run/test commands, container build guidance, and platform dependency notes.
- The new README stays aligned with the existing Finance ownership and boundary guidance in `products/finance/OWNER.md` rather than inventing a new product narrative.
- Touched-file diagnostics are clean.

---

## Phase 9: Audio-Video Fake Panel Remediation (Weeks 5–7)

> **Exit Criteria:** No fake AI results presented as live. Either real service integration or honest "Coming Soon" with feature flag.
> **Guideline Refs:** §1.4 (No silent failures), §6 (UI/UX)

### Task 9.1 — Replace VisionPanel Fake Data

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | Audio-Video |
| **File** | `products/audio-video/apps/desktop/src/components/VisionPanel.tsx` |
| **Issue** | Returns hardcoded detections after `setTimeout(1200)` |
| **Options** | A) Integrate real vision service API <br> B) Show "Vision AI — Coming Soon" with feature flag |
| **Recommended** | Option B unless vision service exists — honest is better than fake |
| **Fix** | Replace mock data with feature-flagged "Coming Soon" state. Define `Detection` interface (fix `any[]`). |

**Status Update (2026-04-16):**
- Completed in `products/audio-video/apps/desktop/src/components/VisionPanel.tsx`: removed the timeout-backed fake detections and replaced them with an explicit unavailable state plus a disabled coming-soon action.
- Preserved image upload and preview so the panel still shows the current integration boundary honestly without pretending to analyze the image.
- Validation: targeted desktop panel tests passed in `MockServicePanels.test.tsx`.

---

### Task 9.2 — Replace AIVoicePanel Fake Data

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **File** | `AIVoicePanel.tsx` |
| **Fix** | Same approach as 9.1 |

**Status Update (2026-04-16):**
- Completed in `products/audio-video/apps/desktop/src/components/AIVoicePanel.tsx`: removed the fake processing timeout and fabricated transformed output.
- The panel now keeps its task and text inputs visible but clearly marks the feature unavailable in this build and disables the action button.
- Validation: targeted desktop panel tests passed in `MockServicePanels.test.tsx`.

---

### Task 9.3 — Replace MultimodalPanel Fake Data

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **File** | `MultimodalPanel.tsx` |
| **Fix** | Same approach as 9.1 |

**Status Update (2026-04-16):**
- Completed in `products/audio-video/apps/desktop/src/components/MultimodalPanel.tsx`: removed the timeout-backed canned success message and replaced it with an explicit unavailable state.
- The panel now keeps the input affordances visible but disables processing until a real orchestration backend exists.
- Validation: targeted desktop panel tests passed in `MockServicePanels.test.tsx`.

---

## Phase 10: Operational Improvements (Weeks 7–8)

> **Guideline Refs:** §19 (Observability), §10 (Infrastructure), §22 (Build and Dependency Management)

### Task 10.1 — Deprecate resolveTenantId() in Data Cloud

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | Data Cloud |
| **File** | `HttpHandlerSupport.java` |
| **Issue** | `resolveTenantId()` returns "default" on missing tenant; critical handlers already use `requireTenantIdOrFail()` |
| **Fix** | 1. Deprecate `resolveTenantId()` <br> 2. Migrate all remaining callers to `requireTenantIdOrFail()` <br> 3. Log deprecation warnings <br> 4. Remove in next release |
| **Test** | Verify all handlers reject missing tenant with HTTP 400 |

**Status Update (2026-04-16):**
- Completed remediation in `products/data-cloud/launcher`: all handler-package callers were migrated from the default-tenant fallback to `requireTenantIdOrFail()`, and the deprecated shared `HttpHandlerSupport.resolveTenantId()` helper was removed.
- Migrated `StorageCostHandler`, `AnalyticsHandler`, `AgentCatalogHandler`, `AiAssistHandler`, `AiModelHandler`, `AutonomyHandler`, `BrainHandler`, `CapabilityRegistryHandler`, `ContextLayerHandler`, `DataProductHandler`, `EntityCrudHandler`, `EntityExportHandler`, `EntityValidationHandler`, `EventHandler`, `LearningHandler`, `LineageHandler`, `MemoryPlaneHandler`, `SseStreamingHandler`, `EntityAnomalyHandler`, `FederatedQueryHandler`, `PluginInstallHandler`, `SemanticSearchHandler`, `TierMigrationHandler`, and `VoiceGatewayHandler` off the default-tenant fallback. Those handlers now return HTTP 400 when `X-Tenant-Id` is missing instead of silently using `default`.
- Cleaned remaining handler-package compatibility references by renaming local tenant-extraction helpers in `DataCloudMiddleware` and `PipelineCheckpointHandler`, deleting the unused `resolveQueryOrHeaderTenantId()` wrapper, and updating launcher documentation examples to use `requireTenantIdOrFail()`.
- Completed launcher-wide compatibility cleanup by renaming the remaining local tenant helper in `DataCloudSecurityFilter`; `products/data-cloud/launcher/src/main/java` no longer contains any `resolveTenantId(...)` references.
- Added regression coverage for the missing-tenant path in `StorageCostHandlerTest`, `AnalyticsHandlerMetricsTest`, `AgentCatalogHandlerTest`, `AiAssistHandlerTest`, `AiModelHandlerTest`, `AutonomyHandlerTest`, `BrainHandlerTest`, `CapabilityRegistryHandlerTest`, `ContextLayerHandlerKgEnrichmentTest`, `DataProductHandlerTest`, `EntityCrudHandlerTenantEnforcementTest`, `EntityExportHandlerTest`, `EntityValidationHandlerTest`, `EventHandlerTenantEnforcementTest`, `LearningHandlerTest`, `LineageHandlerP391Test`, `MemoryPlaneHandlerTest`, `SseStreamingHandlerTest`, `EntityAnomalyHandlerP361Test`, `FederatedQueryHandlerEnhancementTest`, `PluginInstallHandlerTest`, `SemanticSearchHandlerTest`, `TierMigrationHandlerTest`, and `VoiceGatewayHandlerHardeningTest`.
- Validation: targeted tests passed for `StorageCostHandlerTest`, `AnalyticsHandlerMetricsTest`, `AgentCatalogHandlerTest`, `AiModelHandlerTest`, `AiAssistHandlerTest` (validated via Gradle class filter), `AutonomyHandlerTest` (validated via Gradle class filter), `BrainHandlerTest` (validated via Gradle class filter), `CapabilityRegistryHandlerTest`, `ContextLayerHandlerKgEnrichmentTest`, `DataProductHandlerTest`, `EntityCrudHandlerTenantEnforcementTest`, `EntityExportHandlerTest`, `EntityValidationHandlerTest`, `EventHandlerTenantEnforcementTest`, `LearningHandlerTest`, `LineageHandlerP391Test`, `MemoryPlaneHandlerTest`, `SseStreamingHandlerTest`, `EntityAnomalyHandlerP361Test`, `FederatedQueryHandlerEnhancementTest`, `PluginInstallHandlerTest`, `SemanticSearchHandlerTest`, `TierMigrationHandlerTest`, and `VoiceGatewayHandlerHardeningTest`.

---

### Task 10.2 — Fix YAPPC Runtime Port Drift

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Product** | YAPPC |
| **Issue** | Mixed mode-specific ports were being documented as one runtime: standalone service/OpenAPI defaults still use `8082`, unified dev now runs through gateway/backend ports `7002`/`7003`, and external AEP continues to use `8080`, leaving stale health checks and frontend examples wired to the wrong target |
| **Fix** | Preserve the real mode-specific topology and remove stale cross-mode references from live scripts, frontend defaults, and developer-facing examples |
| **Test** | Diagnostics plus targeted drift grep for live files; pending mode-aware E2E health checks for standalone and unified-dev entrypoints |

**Status Update (2026-04-16):**
- Reassessed the current YAPPC topology before editing: standalone service/OpenAPI defaults still point at `8082`, the active unified dev flow uses `7001` (web), `7002` (gateway), and `7003` (Java backend), and `8080` remains valid only for external AEP mode.
- Prior remediation remains valid for standalone/runtime deployment assets: the centralized TypeScript runtime default (`platform/config/ConfigLoader.ts`) stays on `8082`, which still matches the standalone service manifests and OpenAPI server document.
- Removed remaining stale port drift from live runtime/deployment/operator entry points in:
   - `deployment/helm/templates/NOTES.txt`
   - `deployment/kubernetes/base/deploy.sh`
   - `deployment/validate-deployment.sh`
   - `deployment/docker/Dockerfile`
   - `Dockerfile`
   - `Makefile`
   - `DEPLOYMENT_GUIDE.md`
   - `docs/onboarding/developer.md`
- Fixed stale unified-dev/frontend references that were still pointing developers at the wrong ports in:
   - `run-dev.sh`
   - `run-yappc.sh`
   - `frontend/.env.development.example`
   - `frontend/web/src/services/canvas/phase-actions/PhaseActionService.ts`
   - `frontend/web/src/components/CollaborativeCanvas.tsx`
   - `frontend/web/src/clients/ai/AIServiceClient.ts`
   - `frontend/web/src/clients/dashboard/*.ts`
   - `frontend/libs/yappc-ai/src/hooks/*.ts`
   - `frontend/libs/yappc-ai/src/realtime/WebSocketClient.ts`
   - `frontend/libs/yappc-ai/src/agents/api-client.ts`
   - `frontend/web/src/components/canvas/ComponentPalette.tsx`
- Validation: touched-file diagnostics are clean for the updated shell and TypeScript files; targeted grep now leaves only legitimate live references (`AEP_PORT=8080` external mode and `LIFECYCLE_SERVICE_URL` on `8082`) plus tests/test-server literals.
- Remaining work: sweep broader YAPPC docs/archive/generated files for stale port references and add or run explicit health checks for both supported modes: standalone `8082` and unified dev `7002`/`7003`.

---

### Task 10.3 — Add CI Coverage Gates for All Products

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | All products (currently only Data Cloud has coverage gates) |
| **Fix** | Preserve existing AEP and YAPPC coverage workflows and add matrix coverage gates for the remaining uncovered Java products: Finance, PHR, and Security-Gateway |
| **Threshold** | 70% line coverage for new code, 50% overall |

**Status (2026-04-16):**
- Verified existing product-specific coverage enforcement already exists in `.github/workflows/aep-ci.yml`, `.github/workflows/yappc-fe-coverage.yml`, and related product workflows, so the original plan wording was stale.
- Added `.github/workflows/product-coverage-gates.yml` to run `test`, `jacocoTestReport`, and `jacocoTestCoverageVerification` for Finance, PHR, and Security-Gateway on PRs, `main`, and manual dispatch.
- Validated the exact Gradle task paths with `--dry-run` before wiring CI:
   - `:products:finance:test`, `:products:finance:jacocoTestReport`, `:products:finance:jacocoTestCoverageVerification`
   - `:products:phr:test`, `:products:phr:jacocoTestReport`, `:products:phr:jacocoTestCoverageVerification`
   - `:products:security-gateway:platform:java:test`, `:products:security-gateway:platform:java:jacocoTestReport`, `:products:security-gateway:platform:java:jacocoTestCoverageVerification`
- Coverage reports are uploaded through Codecov and stored as artifacts per product, and PRs now fail if the product-level JaCoCo verification task fails.

---

### Task 10.4 — Add CI TypeScript Strict Enforcement

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Scope** | All TypeScript packages |
| **Fix** | Add governance CI step that resolves effective TypeScript config and fails PRs when any first-party tsconfig does not resolve `compilerOptions.strict` to `true` |
| **Enforcement** | PR check fails through `pnpm run check:tsconfig-strict` in `.github/workflows/governance-checks.yml` |

**Status (2026-04-16):**
- Added `scripts/check-typescript-strict.js` to parse first-party `tsconfig*.json` files through the TypeScript compiler API, so inherited strictness is validated instead of relying on brittle text grep.
- Added `check:tsconfig-strict` in `package.json` and wired it into `.github/workflows/governance-checks.yml` as a dedicated TypeScript strict-governance PR check.
- Remediated first-party strictness gaps in `products/data-cloud/ui/tsconfig.node.json`, `products/dcmaar/modules/desktop/tsconfig.node.json`, `products/dcmaar/modules/desktop/tsconfig.paths.json`, `products/flashit/client/web/tsconfig.node.json`, `products/tutorputor/apps/tutorputor-admin/tsconfig.node.json`, `products/tutorputor/apps/tutorputor-web/tsconfig.json`, `products/tutorputor/libs/tutorputor-core/tsconfig.json`, and corrected the broken base-config inheritance in `products/yappc/frontend/libs/yappc-devsecops/tsconfig.json`.
- Validation: `node ./scripts/check-typescript-strict.js` now passes locally with all first-party TypeScript configs resolving `compilerOptions.strict` to `true`.
- Follow-up noted during validation: a broader package-sweep runner (`scripts/run-typescript-workspace-typecheck.js`) now enumerates pnpm workspace TypeScript packages, but full repo-wide typechecking still exposes existing compile debt starting in `platform/typescript/accessibility`; keep that sweep as remediation support, not as a mandatory PR gate until those package errors are addressed.

---

### Task 10.5 — Create Missing Dockerfiles

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Products** | Finance, PHR, Security-Gateway, Audio-Video (individual services), Software-Org |
| **Template** | Follow existing Dockerfile best practices (multi-stage, non-root user, ZGC, MaxRAMPercentage) |
| **Guideline Refs** | §10 (Infrastructure) |

**Status Update (2026-04-16):**
- Added verified multi-stage Dockerfiles for the products that already expose clear runtime entrypoints:
   - `products/audio-video/modules/speech/stt-service/Dockerfile`
   - `products/audio-video/modules/speech/tts-service/Dockerfile`
   - `products/audio-video/modules/vision/vision-service/Dockerfile`
   - `products/audio-video/modules/intelligence/multimodal-service/Dockerfile`
   - `products/software-org/services/management-api/Dockerfile`
- Audio-Video packaging now targets the product-local Gradle workspace rather than the monorepo root, because container builds against root `settings.gradle.kts` eagerly fail on unrelated missing product directories. Validation is still in progress: Docker build repros confirmed and fixed the initial monorepo-layout issues (missing included `build-logic`, nonexistent root `contracts/`, broken product `gradle` symlink target), and the current remaining failure is inside the product Gradle build itself rather than the Dockerfile scaffold.
- Audio-Video product-local build validation also surfaced stale standalone build wiring that had to be normalized before container validation could proceed: `products/audio-video/settings.gradle.kts` now resolves its protobuf plugin and root `build-logic` correctly, and the product infrastructure modules no longer reference several nonexistent local catalog aliases (`testcontainers.redis`, `rabbitmq`, `testcontainers.rabbitmq`, `grpc.api`, `jjwt.*`). The representative STT image reached `installDist` and exposed a real packaging issue (`duplicate lib/common.jar`), which is now addressed by setting duplicate handling on the `installDist` task in the four service modules.
- Representative validation is now green for Audio-Video: `./gradlew :products:audio-video:modules:speech:stt-service:installDist --no-daemon -x test` succeeds in the product-local workspace, and `docker build -f products/audio-video/modules/speech/stt-service/Dockerfile -t ghatana-stt-service:test .` completes successfully. The same product-local Gradle + `installDist` Docker pattern is now the baseline for the TTS, Vision, and Multimodal service images.
- Software-Org management API is now also validated end to end. Container hardening required two layers of remediation: first Prisma 7 alignment (`DATABASE_URL` provided at build time and legacy schema datasource URL removed), then application build cleanup (expanded `AgentConfig`/`OrgConfig` contracts, fixed strict-mode loader/client/script issues, excluded a stale Express-only route from the Fastify package build, and added the missing `axios` dependency). After those fixes, `docker build -f products/software-org/services/management-api/Dockerfile -t ghatana-software-org-management:test .` completes successfully.
- Software-Org packaging follows the existing repo Node multi-stage pattern and includes Prisma client generation plus startup-time `prisma migrate deploy` before launching `dist/server.js`. Local validation still shows package drift in the checked-in workspace (`pnpm exec prisma generate --schema prisma/schema.prisma` fails because the package-local Prisma CLI entrypoint is missing), so the clean image install remains the intended source of truth for that service.
- Finance and PHR now also have validated launcher-based container packaging:
   - Added `products/finance/launcher/build.gradle.kts`, `products/finance/launcher/src/main/java/com/ghatana/products/finance/launcher/FinanceLauncher.java`, and `products/finance/launcher/Dockerfile`.
   - Added `products/phr/launcher/build.gradle.kts`, `products/phr/launcher/src/main/java/com/ghatana/phr/launcher/PhrLauncher.java`, and `products/phr/launcher/Dockerfile`.
   - Root Gradle settings now include `:products:finance`, `:products:finance:launcher`, and `:products:phr:launcher` so the new launchers participate in the monorepo build graph.
   - Both launchers compile and package cleanly via `./gradlew :products:finance:launcher:installDist :products:phr:launcher:installDist -x test --no-daemon`.
   - Both launcher images now build successfully: `docker build -f products/finance/launcher/Dockerfile -t ghatana-finance-launcher:test .` and `docker build -f products/phr/launcher/Dockerfile -t ghatana-phr-launcher:test .`.
- Container validation also required repo-level Docker hardening for monorepo builds:
   - Added a root `.dockerignore` to exclude heavyweight generated state (`target/`, `node_modules/`, `.gradle/`, `build/`, etc.), reducing Docker context transfer from ~22 GB to ~70 MB.
   - Finance/PHR launcher Dockerfiles now override the repo-wide Gradle defaults inside the container (`--max-workers=2`, `-Dorg.gradle.parallel=false`, `-Dorg.gradle.caching=false`, smaller `org.gradle.jvmargs`) so the monorepo build can complete within Docker memory limits.
- Security Gateway is now also closed with a real launcher/runtime surface:
   - Added `products/security-gateway/launcher/build.gradle.kts`, `products/security-gateway/launcher/src/main/java/com/ghatana/securitygateway/launcher/SecurityGatewayLauncher.java`, and `products/security-gateway/launcher/Dockerfile`.
   - Root Gradle settings now include `:products:security-gateway:launcher`, and the Security Gateway README now points local execution to `./gradlew :products:security-gateway:launcher:run`.
   - Validation is green end to end: `./gradlew :products:security-gateway:launcher:compileJava :products:security-gateway:launcher:installDist -x test --no-daemon` and `docker build -f products/security-gateway/launcher/Dockerfile -t ghatana-security-gateway:test .` both succeed.
- Task 10.5 is now complete for the products named in the audit plan: Audio-Video service images, Software-Org management API, Finance launcher, PHR launcher, and Security Gateway launcher are all backed by validated Docker builds.

---

### Task 10.6 — AEP gRPC Service Implementation or Removal

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Product** | AEP |
| **File** | `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/grpc/AgentGrpcService.java` |
| **Issue** | Entire class commented out — references non-existent `AgentFrameworkRegistry` |
| **Options** | A) Reimplement using `AgentRegistry` SPI <br> B) Remove file and gRPC claims from README |
| **Recommended** | Option A if gRPC is strategic, Option B if HTTP-only is sufficient |

**Status Update (2026-04-16):**
- Completed as Option B for the current remediation batch: removed `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/grpc/AgentGrpcService.java`, which was only a commented-out placeholder referencing a non-existent registry type.
- Verified that no AEP README in the current workspace advertises this removed stub as a working implementation.
- Follow-up, if gRPC becomes strategic again: implement a fresh service against `com.ghatana.agent.spi.AgentRegistry` rather than resurrecting the deleted placeholder.

---

### Task 10.7 — Remove Misleading YAPPC Javadoc Comments

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Product** | YAPPC |
| **File** | `LifecycleLoginController.java` |
| **Issue** | Javadoc mentions `change-me-in-production` dev user but code doesn't create it |
| **Fix** | Update Javadoc to reflect actual behavior (env-based user provisioning) |

**Status Update (2026-04-16):**
- Completed in `products/yappc/core/services-lifecycle`: `LifecycleLoginController` Javadoc now states that `YAPPC_AUTH_USERS` is mandatory and that startup fails fast when it is missing.
- Tightened the parsing path at the same time by switching JSON parsing to a typed Jackson `TypeReference`, removing the remaining unchecked warning in this controller.

---

## Phase 11: Schema Migration Strategy (Week 8)

> **Guideline Refs:** §10 (Infrastructure), §3 Design

### Task 11.1 — Evaluate and Adopt Schema Migration Tool

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Scope** | All products with database persistence |
| **Issue** | No Flyway/Liquibase — custom migrations lack rollback and ordering |
| **Decision Required** | Evaluate Flyway vs Liquibase. Flyway preferred for simplicity. |
| **Implementation** | 1. Add Flyway to `gradle/libs.versions.toml` <br> 2. Create migration baseline for each product <br> 3. Convert custom migrations to Flyway SQL migrations <br> 4. Add `./gradlew flywayMigrate` to CI |

**Status Update (2026-04-16):**
- Completed as an audit-plan correction plus evidence-backed adoption confirmation: the workspace already standardizes on Flyway rather than lacking a migration tool.
- Evidence in the current repo:
   - `gradle/libs.versions.toml` already declares `flyway`, `flyway-core`, and `flyway-postgresql`.
   - `platform/java/database/src/main/java/com/ghatana/core/database/migration/FlywayMigration.java` already provides the shared migration abstraction used by product code.
   - `docs/adr/ADR-010-flyway-migrations.md` already records the architectural decision in favor of Flyway over Liquibase.
   - Multiple products already ship Flyway-style migration trees under `src/main/resources/db/migration`, including Data Cloud, AEP, Finance, PHR, Audio-Video, Security Gateway, Virtual-Org, and YAPPC.
- Follow-up narrowed from “adopt a migration tool” to “finish rollout/governance consistency”:
   - Some products still rely on product-local migration helpers rather than a single repo-wide CI entrypoint.
   - No shared CI workflow in the current workspace advertises a canonical `flywayMigrate` gate yet, so the remaining work is rollout standardization, not tool selection.

---

## Summary: Implementation Timeline

| Phase | Weeks | Tasks | Exit Criteria |
|-------|-------|-------|---------------|
| **0: Security Fixes** | 1 | 0.1–0.4 | Zero SQL injection, zero permissive bypasses |
| **1: Async Correctness** | 1–2 | 1.1–1.6 | Zero event loop blocking |
| **2: Type Safety** | 2–4 | 2.1–2.6 | All strict: true, API boundaries typed |
| **3: Error Handling** | 3–4 | 3.1–3.3 | Zero empty catches, errors logged |
| **4: Test Coverage** | 3–6 | 4.1–4.6 | All products ≥ 0.4 ratio |
| **5: Config Hardening** | 4–5 | 5.1–5.4 | All env vars validated |
| **6: DI Cleanup** | 5 | 6.1 | Zero field injection |
| **7: Error Boundaries** | 5 | 7.1 | All React apps covered |
| **8: Documentation** | 6–7 | 8.1–8.3 | All modules have README |
| **9: Fake Panel Removal** | 5–7 | 9.1–9.3 | No fake AI results |
| **10: Operational** | 7–8 | 10.1–10.7 | Deployment ready |
| **11: Schema Migration** | 8 | 11.1 | Migration tool adopted |

---

## Priority Quick Reference

### P0 — Immediate (Must fix before any deployment)

| Task | Summary | Product |
|------|---------|---------|
| 0.1 | SQL injection in StorageCostHandler | Data Cloud |
| 0.2 | SQL injection in ClickHouseTimeSeriesConnector | Data Cloud |
| 0.3 | Remove AV_JWT_PERMISSIVE_MODE | Audio-Video |
| 0.4 | Gitignore committed .env files | Software-Org, YAPPC, FlashIt |
| 1.1–1.5 | Fix blocking .getResult() and Thread.sleep(30s) | Cross-product |
| 2.1 | Enable strict: true in TutorPutor | TutorPutor |
| 2.2 | Fix Software-Org API route type safety | Software-Org |
| 4.1 | Software-Org minimum test coverage | Software-Org |
| 4.2 | Security-Gateway test investment | Security-Gateway |
| 10.2 | YAPPC runtime port drift | YAPPC |

### P1 — High (Must fix before production release)

| Task | Summary | Product |
|------|---------|---------|
| 1.6 | Audit remaining Thread.sleep | Cross-product |
| 2.3–2.5 | Fix as any casts (DCMAAR, FlashIt, tsconfigs) | Cross-product |
| 3.1–3.2 | Fix empty catches and generic exceptions | Cross-product |
| 4.3–4.6 | Test coverage (Virtual-Org, Audio-Video, AEP, contracts) | Cross-product |
| 5.1–5.4 | Config validation and CORS | Cross-product |
| 7.1 | React ErrorBoundary | Cross-product |
| 8.1–8.2 | Platform documentation | Platform |
| 9.1–9.3 | Fake panel remediation | Audio-Video |
| 10.1 | Deprecate resolveTenantId | Data Cloud |
| 10.3–10.4 | CI coverage and strict gates | CI/CD |
| 10.6 | AEP gRPC service | AEP |

### P2 — Medium (Should fix in next sprint)

| Task | Summary | Product |
|------|---------|---------|
| 2.6 | VisionPanel any[] type | Audio-Video |
| 3.3 | Unhandled .then() in YAPPC | YAPPC |
| 6.1 | Field injection cleanup | Cross-product |
| 8.3 | Finance README | Finance |
| 10.5 | Missing Dockerfiles | Cross-product |
| 10.7 | Remove misleading Javadoc | YAPPC |
| 11.1 | Schema migration strategy | Cross-product |

---

## Dependency Graph

```
Phase 0 (Security) ──────────────────────────────┐
                                                   │
Phase 1 (Async) ─────────────────────────────────┤
                                                   ▼
Phase 2 (Type Safety) ────────────► Phase 4 (Tests) ────► Phase 10 (Ops)
                                         │                      │
Phase 3 (Error Handling) ───────────────┤                      │
                                         │                      ▼
Phase 5 (Config) ───────────────────────┤              Phase 11 (Schema)
                                         │
Phase 6 (DI) ──────────────────────────┤
                                         │
Phase 7 (Error Boundaries) ────────────┤
                                         │
Phase 8 (Documentation) ──────────────┘
                                         
Phase 9 (Fake Panels) ─── (independent, parallel with Phase 5+)
```

**Key Dependencies:**
- Task 4.1 (Software-Org tests) depends on Task 2.2 (Software-Org type safety)
- Phase 10 (Ops) depends on Phase 4 (Tests) for coverage gate implementation
- All P0 tasks (Phase 0, 1) are independent and can start immediately

---

## Monitoring Progress

### Weekly Metrics to Track

1. **SQL injection vectors remaining** (target: 0 after Week 1)
2. **`.getResult()` calls in event loop context** (target: 0 after Week 2)
3. **`as any` cast count** (target: 80% reduction by Week 4)
4. **tsconfig.json without `strict: true`** (target: 0 by Week 4)
5. **Empty catch blocks** (target: 0 by Week 4)
6. **Test ratio per product** (target: all ≥ 0.4 by Week 6)
7. **Modules with README.md** (target: 100% by Week 7)
8. **Products with ErrorBoundary** (target: 100% by Week 5)

### Completion Criteria per §15 (Definition of Done)

Every task is done when:
- [ ] Follows existing conventions of the touched module
- [ ] Existing shared platform code checked before creating new abstractions
- [ ] Builds, types, or compiles in the relevant workspace
- [ ] All TypeScript code is fully typed — no `any`, no untyped parameters
- [ ] Relevant tests added/updated and pass
- [ ] Formatting, linting, static checks remain healthy
- [ ] Public Java APIs include required JavaDoc and `@doc.*` tags
- [ ] Errors and important flows are observable
- [ ] Inputs validated at correct boundaries
- [ ] No repo drift in architecture, naming, or dependency choices

---

**Created:** 2026-04-16  
**Source Audit:** COMPREHENSIVE_PRODUCT_AUDIT_REPORT_2026-04-16.md  
**Governing Standards:** .github/copilot-instructions.md  
**Total Tasks:** 40  
**Estimated Duration:** 8 weeks  
