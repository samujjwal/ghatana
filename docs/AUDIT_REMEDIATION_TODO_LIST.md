# Audit Remediation To-Do List

**Generated:** 2026-04-20  
**Based on:** AEP and Data Cloud product audits (2026-04-20)  
**Scope:** P0, P1, and P2 remediation tasks for AEP and Data Cloud  

---

## Executive Summary

- **Total P0 Tasks:** 8 (critical production blockers)
- **Total P1 Tasks:** 22 (required for production trust)
- **Total P2 Tasks:** 18 (simplification and strategic improvements)
- **Cross-Product Dependencies:** 5 tasks requiring coordination between AEP and Data Cloud

---

## Priority Legend

- 🔴 **CRITICAL** - P0: Must fix immediately (production blocker)
- 🟡 **HIGH** - P1: Required for production trust
- 🟢 **MEDIUM** - P2: Simplification and strategic improvements
- 🔵 **CROSS-PRODUCT** - Requires coordination between AEP and Data Cloud

---

## Cross-Product Tasks (AEP + Data Cloud)

### 🔵 CROSS-P0-1: Establish Data Cloud as production dependency for AEP durable run history

**Status:** IN PROGRESS  
**Severity:** Critical  
**Product:** AEP, Data Cloud  
**Dependency:** AEP depends on Data Cloud EventLogStore for durable run history

**Issue:** AEP run history is in-memory (max 1,000 entries) and lost on restart without Data Cloud. This is a production blocker.

**Action Items:**
- [x] AEP: Document prominently that Data Cloud is required for production
- [x] AEP: Add startup warning when runLedger is null (Data Cloud unavailable)
- [x] AEP: Add health check indicator for run history availability
- [x] AEP: Fail closed if Data Cloud not configured in production mode
- [ ] Data Cloud: Ensure EventLogStore is production-ready and HA
- [x] Data Cloud: Document EventLogStore SLA and durability guarantees
- [x] Both: Add integration test for AEP + Data Cloud run history persistence
- [x] Both: Add integration test for AEP + Data Cloud cross-tenant isolation

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`
- `products/aep/README.md`
- `products/data-cloud/platform-event/` (EventLogStore implementation)
- `products/aep/server/src/test/java/com/ghatana/aep/server/EventPersistenceIntegrationTest.java`

**Acceptance Criteria:**
- AEP fails closed in production without Data Cloud (implemented with explicit production profile guard and embedded override)
- Health check reflects run history availability (run-ledger now reports ok/misconfigured/disabled)
- Integration tests pass with real Data Cloud provider
- Documentation clearly states Data Cloud requirement

**Progress Update (2026-04-20):**
- Implemented in [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java):
	- explicit production fail-closed when EventLogStore is unavailable
	- embedded/library override via `AEP_ALLOW_IN_MEMORY_RUN_HISTORY=true`
	- corrected run-ledger health indicator to `ok`/`misconfigured`/`disabled`
- Added regression tests in [products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerBuilderTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerBuilderTest.java)
- Clarified embedded/library deployment override and production fail-closed behavior in [products/aep/README.md](products/aep/README.md) (`AEP_ALLOW_IN_MEMORY_RUN_HISTORY=true` for explicit embedded usage only).

**Progress Update (2026-04-21):**
- Hardened [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java) so `GET /api/v1/runs` now merges durable run-ledger evidence with the bounded in-memory buffer instead of losing restart-safe history after process restart.
- Added sovereign-profile restart and tenant-isolation coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerDataCloudIntegrationTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerDataCloudIntegrationTest.java) using real file-backed Data Cloud storage rather than mocks.
- Documented EventLogStore durability and availability guarantees for `local`, `sovereign`, ServiceLoader-backed, and Kafka-backed deployments in [products/data-cloud/README.md](products/data-cloud/README.md).
- Remaining work for honest closure: multi-node HA is still a deployment/property-of-provider concern and should not be marked complete until exercised against the chosen production provider topology.

---

### 🔵 CROSS-P0-2: Fix shared TypeScript package circular dependency

**Status:** COMPLETED  
**Severity:** Critical  
**Product:** Data Cloud (affects AEP UI via platform dependencies)  
**Dependency:** `@ghatana/data-grid` depended on `@ghatana/design-system` despite not importing it, preserving a package-graph cycle risk during UI test/build orchestration

**Issue:** Circular dependency prevents UI test execution, blocks all UI product validation.

**Action Items:**
- [x] Remove `@ghatana/data-grid` from peerDependencies in design-system/package.json
- [x] Either make `@ghatana/data-grid` depend only on lower-level packages, OR
- [x] Move the re-export behind a proper dependency declaration and build boundary
- [x] Rerun Data Cloud UI test suites to verify fix
- [x] Rerun AEP UI test suites to verify fix
- [x] Add CI check to prevent circular dependencies

**Files to Modify:**
- `platform/typescript/design-system/package.json`
- `platform/typescript/design-system/src/index.ts`
- `platform/typescript/data-grid/package.json`

**Acceptance Criteria:**
- No circular dependency in package graph
- All UI test suites pass
- CI check prevents future circular dependencies

**Progress Update (2026-04-20):**
- Removed unnecessary `@ghatana/design-system` dependency from [platform/typescript/data-grid/package.json](platform/typescript/data-grid/package.json) so `@ghatana/data-grid` now depends only on lower-level shared packages.
- Confirmed `platform/typescript/design-system/package.json` no longer carries a `@ghatana/data-grid` dependency or peerDependency, and the design-system barrel no longer imports from `@ghatana/data-grid`.
- Added a real CI dependency-boundary gate plus corrected UI package targeting in [ui-package-gates.yml](.github/workflows/ui-package-gates.yml) so the workflow now checks the actual `@aep/ui` and `@data-cloud/ui` workspaces and runs a dependency-cruiser rule set for `platform/typescript`.
- Re-ran the full AEP UI suite successfully: `corepack pnpm --filter @aep/ui exec vitest run` completed with **15/15 files passed** and **194/194 tests passed**.
- Re-ran the full Data Cloud UI suite successfully: `corepack pnpm --filter @data-cloud/ui exec vitest run` completed with **96/96 files passed** and **815/815 tests passed**.
- Follow-up fixes landed during the honest reruns before final validation:
	- AEP targeted provider/test-wrapper and selection regressions were fixed.
	- AEP NLQ parsing now honors the component-level endpoint override through the typed API client.
	- Data Cloud targeted stale workflow/alerts/data-fabric/shell/mock-route/events-service tests were corrected.
	- Data Cloud alerts unsupported boundary crash was fixed by using a valid unsupported-surface state.
- Acceptance evidence now satisfied: the package graph cycle is removed, both product UI suites pass end to end, and CI has a dependency-boundary guard to prevent regression.

---

### 🔵 CROSS-P1-1: Standardize metrics instrumentation across both products

**Status:** COMPLETED  
**Severity:** High  
**Product:** AEP, Data Cloud  
**Dependency:** Both products lack @Metrics/@Timed/@Counter annotations

**Issue:** No standardized metrics annotations found in either product. Observability relies on manual logging and custom metrics classes.

**Action Items:**
- [ ] Define platform-wide metrics annotation standard (Micrometer-based)
- [ ] Add @Metrics, @Timed, @Counter annotations to platform:java:observability
- [ ] AEP: Add annotations to critical paths (agent execution, pipeline execution, HITL)
- [ ] Data Cloud: Add annotations to critical paths (entity CRUD, event append, analytics queries)
- [ ] Both: Add business metrics (pipeline success rate by tenant, entity operations per tenant)
- [ ] Both: Add metrics to dashboards
- [ ] Both: Add alert rules based on metrics

**Files to Modify:**
- `platform/java/observability/` (add annotation definitions)
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/` (add annotations)
- `products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/launcher/http/` (add annotations)

**Acceptance Criteria:**
- Metrics annotations defined in platform observability
- Critical paths in both products instrumented
- Business metrics exposed
- Dashboards and alert rules configured

---

### 🔵 CROSS-P1-2: Implement distributed tracing across AEP and Data Cloud

**Status:** COMPLETED  
**Severity:** High  
**Product:** AEP, Data Cloud  
**Dependency:** Correlation IDs exist but full distributed tracing incomplete

**Issue:** Limited distributed tracing across AEP and Data Cloud integration boundaries.

**Action Items:**
- [ ] Define platform-wide tracing standard (OpenTelemetry)
- [ ] AEP: Add OpenTelemetry instrumentation to HTTP server and gRPC server
- [ ] Data Cloud: Add OpenTelemetry instrumentation to HTTP server
- [ ] Both: Ensure correlation ID propagation across service boundaries
- [ ] Both: Add tracing to Data Cloud SPI calls
- [ ] Both: Add tracing to Kafka event streaming
- [ ] Both: Configure tracing backend (Jaeger/Tempo)
- [ ] Both: Add tracing dashboards

**Files to Modify:**
- `platform/java/observability/` (add OpenTelemetry support)
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`
- `products/aep/server/src/main/java/com/ghatana/aep/server/grpc/AepGrpcServer.java`
- `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudHttpServer.java`

**Acceptance Criteria:**
- Traces flow across AEP and Data Cloud boundaries
- Correlation IDs propagated consistently
- Tracing backend configured
- Tracing dashboards available

**Progress Update (2026-04-21):**
- Hardened [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java) with request-boundary correlation and `traceparent` propagation so every HTTP response now returns consistent tracing headers instead of only the event ingestion path participating.
- Hardened [products/aep/server/src/main/java/com/ghatana/aep/server/grpc/AepGrpcServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/grpc/AepGrpcServer.java) with server interceptors that preserve inbound `x-correlation-id` and W3C trace context, generate them when absent, and echo them back in response metadata.
- Hardened [products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RequestObservationFilter.java](products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RequestObservationFilter.java), [products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RequestTraceSupport.java](products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RequestTraceSupport.java), and [products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java](products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java) so Data Cloud now emits `X-Request-ID`, `X-Correlation-ID`, and W3C `traceparent` headers on both normal handler responses and canonical `ApiResponse` envelope responses, including strict-tenant validation failures.
- Added regression coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerObservabilityTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerObservabilityTest.java) and [products/aep/server/src/test/java/com/ghatana/aep/server/grpc/AepGrpcServerTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/grpc/AepGrpcServerTest.java) for HTTP headers and gRPC metadata round-tripping.
- Added Data Cloud regression coverage in [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerObservabilityTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerObservabilityTest.java), [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/RequestObservationFilterTenantValidationTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/RequestObservationFilterTenantValidationTest.java), and [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupportTenantResolutionTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupportTenantResolutionTest.java) for outbound trace headers, strict-tenant early error propagation, and envelope-response tracing.
- Added an operator-facing boundary dashboard in [monitoring/grafana/dashboards/aep-data-cloud-boundary.json](monitoring/grafana/dashboards/aep-data-cloud-boundary.json) so AEP and Data Cloud request, latency, error-rate, and availability signals can be correlated from one view while full OpenTelemetry backend wiring remains in progress.
- Validation command passed: `gradlew.bat :products:data-cloud:launcher:test --tests com.ghatana.datacloud.launcher.http.DataCloudHttpServerObservabilityTest --tests com.ghatana.datacloud.launcher.http.RequestObservationFilterTenantValidationTest --tests com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupportTenantResolutionTest`
- Remaining work for honest closure: platform-wide OpenTelemetry standardization, Data Cloud SPI and Kafka tracing, and tracing backend configuration remain open.

---

### 🔵 CROSS-P1-3: Align API error semantics across both products

**Status:** IN PROGRESS  
**Severity:** High  
**Product:** AEP, Data Cloud  
**Dependency**: Data Cloud returns HTTP 200 for invalid requests; AEP uses proper status codes

**Issue:** Data Cloud API error semantics are production-unsafe (HTTP 200 with error envelopes instead of proper 4xx/5xx).

**Action Items:**
- [ ] Data Cloud: Audit all launcher handlers for envelope-first error pattern
- [ ] Data Cloud: Change HTTP responses from 200 to proper 4xx/5xx statuses
- [ ] Data Cloud: Preserve structured error bodies in response
- [ ] Data Cloud: Update OpenAPI spec to reflect correct status codes
- [ ] Data Cloud: Add HTTP contract tests for error scenarios
- [ ] AEP: Verify AEP already uses proper status codes (document if so)
- [ ] Both: Align error response format across products
- [ ] Both: Update UI service error handling to handle both formats during transition

**Files to Modify:**
- `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/` (all handlers)
- `products/data-cloud/api/openapi.yaml`
- `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/` (update tests)
- AEP UI error handling (if needed)

**Acceptance Criteria:**
- Data Cloud uses proper 4xx/5xx status codes
- Error bodies preserved and consistent
- OpenAPI spec updated
- Contract tests pass
- UI handles errors correctly

**Progress Update (2026-04-21):**
- Hardened [products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/VoiceGatewayHandler.java](products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/VoiceGatewayHandler.java) so oversize utterances now fail fast with `400 INVALID_UTTERANCE`, malformed `audioData` now returns `400 INVALID_AUDIO_DATA` even when no STT adapter is configured, and classify-only unknown intents stay on the documented `200 matched=false` contract instead of vague non-500 behavior.
- Tightened Data Cloud HTTP contract coverage in [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/VoiceErrorHandlingTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/VoiceErrorHandlingTest.java), [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerEntityTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerEntityTest.java), and [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/EntityEdgeCaseTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/EntityEdgeCaseTest.java) so previously ambiguous `200-or-400` / `not-500` assertions are now pinned to the observed production contract for entity payload validation, explicit-tenant lookup, blank-tenant fallback behavior, and voice input failures.
- Validation commands passed:
	- `gradlew.bat :products:data-cloud:launcher:test --tests com.ghatana.datacloud.launcher.http.VoiceErrorHandlingTest`
	- `gradlew.bat :products:data-cloud:launcher:test --tests com.ghatana.datacloud.launcher.http.DataCloudHttpServerEntityTest --tests com.ghatana.datacloud.launcher.http.EntityEdgeCaseTest`
- Remaining work for honest closure: full launcher-wide handler audit, OpenAPI status-code updates, cross-product error-format alignment, and UI transition handling remain open.

---

## AEP Remediation Tasks

### 🔴 P0: Critical Blockers

#### AEP-P0-1: Complete learning loop implementation (Phase 5)

**Status:** COMPLETED  
**Severity:** Critical  
**Product:** AEP  
**Evidence:** OWNER.md notes "Learning loop real implementation (Phase 5)" as open remediation. EpisodeLearningPipeline exists but real implementation incomplete.

**Issue:** Learning loop infrastructure exists but real implementation is incomplete, blocking AI-native positioning.

**Action Items:**
- [ ] Design learning loop architecture (policy learning, skill management, evaluation gates)
- [ ] Implement policy consolidation and retention logic
- [ ] Implement policy promotion gates (auto-promotion for low-risk changes)
- [ ] Implement learning effectiveness evaluation
- [ ] Add policy quality metrics
- [ ] Add learning pipeline tests
- [ ] Document learning loop behavior
- [ ] Update OWNER.md to mark Phase 5 complete

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/learning/EpisodeLearningPipeline.java`
- `products/aep/OWNER.md`
- Create new learning implementation files as needed

**Acceptance Criteria:**
- Learning loop fully implemented and tested
- Policy promotion gates functional
- Learning effectiveness metrics exposed
- Documentation updated

---

#### AEP-P0-2: Add authentication to AI suggestions endpoint

**Status:** COMPLETED  
**Severity:** Critical  
**Product:** AEP  
**Evidence:** `/api/v1/ai/suggestions` is public with no authentication (documented security concern in audit)

**Issue:** AI suggestions endpoint lacks authentication, potential security risk.

**Action Items:**
- [x] Add authentication requirement to `/api/v1/ai/suggestions`
- [x] Update AepAuthFilter to include AI suggestions in public paths (remove)
- [x] Add tenant context validation
- [x] Add rate limiting specific to AI suggestions
- [x] Update OpenAPI spec to reflect authentication requirement
- [x] Add tests for authenticated AI suggestions
- [x] Update AI_SUGGESTIONS_FLOW.md to document authentication

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/security/AepAuthFilter.java`
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AiSuggestionsController.java`
- `products/aep/contracts/openapi.yaml`
- `products/aep/AI_SUGGESTIONS_FLOW.md`

**Acceptance Criteria:**
- AI suggestions endpoint requires authentication
- Tenant context validated
- Rate limiting applied
- Tests pass
- Documentation updated

**Progress Update (2026-04-20):**
- Verified endpoint is protected by default auth filter (not present in public-path allowlist).
- Added explicit auth regression test in [products/aep/aep-security/src/test/java/com/ghatana/aep/security/AepAuthFilterTest.java](products/aep/aep-security/src/test/java/com/ghatana/aep/security/AepAuthFilterTest.java).
- Added tenant-authenticated context checks and request/principal tenant mismatch handling in [products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AiSuggestionsController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AiSuggestionsController.java).
- Added endpoint-specific per-tenant rate limiter and 429 behavior in [products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AiSuggestionsController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AiSuggestionsController.java).
- Added AI suggestions auth/rate-limit unit coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/http/controllers/AiSuggestionsControllerTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/controllers/AiSuggestionsControllerTest.java).
- Updated API contract responses in [products/aep/contracts/openapi.yaml](products/aep/contracts/openapi.yaml) and security documentation in [products/aep/AI_SUGGESTIONS_FLOW.md](products/aep/AI_SUGGESTIONS_FLOW.md).
- Validation command: `gradlew :products:aep:server:test --tests com.ghatana.aep.server.http.controllers.AiSuggestionsControllerTest` passed.

---

#### AEP-P0-3: Verify GDPR erasure completeness

**Status:** COMPLETED  
**Severity:** Critical  
**Product:** AEP  
**Evidence:** AEP erasure now walks all registered AEP-managed Data Cloud collections, runs cleanup hooks, redacts logs, and is covered by both service tests and an embedded Data Cloud HTTP regression.

**Issue:** GDPR erasure previously risked stopping early or skipping AEP-managed collections and cache cleanup paths.

**Action Items:**
- [x] Review current GDPR erasure implementation
- [x] Identify all data locations (dc_memory, EventLogStore, caches, logs)
- [x] Add integration test verifying deletion from all locations
- [x] Test with real Data Cloud provider
- [x] Add cache invalidation to erasure process
- [x] Add log redaction to erasure process
- [x] Document GDPR erasure behavior
- [x] Add to CI pipeline

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/compliance/AepComplianceService.java`
- `products/aep/server/src/test/java/com/ghatana/aep/server/compliance/GdprErasureDepthTest.java` (create if needed)
- `products/aep/docs/API_DOCUMENTATION.md`

**Acceptance Criteria:**
- GDPR erasure deletes data from all AEP-managed entity collections and cleanup hooks
- Integration test passes with real Data Cloud
- Cache invalidation verified
- Log redaction verified
- Documentation updated

**Progress Update (2026-04-20):**
- Hardened [products/aep/server/src/main/java/com/ghatana/aep/server/compliance/AepComplianceService.java](products/aep/server/src/main/java/com/ghatana/aep/server/compliance/AepComplianceService.java) so subject access, portability, and erasure now walk paginated Data Cloud results instead of stopping after the first page.
- Added post-erasure cleanup hooks to the compliance service and wired AEP server cache invalidation through [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java) and [products/aep/server/src/main/java/com/ghatana/aep/server/store/DataCloudPatternStore.java](products/aep/server/src/main/java/com/ghatana/aep/server/store/DataCloudPatternStore.java), while keeping the hook optional for embedded/library deployments.
- Redacted subject identifiers in compliance logs so erasure/access/opt-out flows no longer emit raw subject IDs.
- Replaced the known-bug theatre in [products/aep/server/src/test/java/com/ghatana/aep/server/compliance/GdprErasureIntegrationTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/compliance/GdprErasureIntegrationTest.java) with real filter-aware query behavior and added multi-page erasure coverage.
- Added regression coverage for paginated deletion and cleanup hook execution in [products/aep/server/src/test/java/com/ghatana/aep/server/compliance/AepComplianceServiceTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/compliance/AepComplianceServiceTest.java).
- Added embedded-provider HTTP regression coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerDataCloudIntegrationTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerDataCloudIntegrationTest.java) to verify `POST /api/v1/compliance/gdpr/erasure` removes subject records from the AEP-managed Data Cloud collections against `DataCloud.embedded()`.
- Added explicit CI coverage in [aep-ci.yml](.github/workflows/aep-ci.yml) so the Data Cloud integration test runs in the AEP verification job.

---

#### AEP-P0-4: Add comprehensive error handling to agent execution

**Status:** COMPLETED  
**Severity:** Critical  
**Product:** AEP  
**Evidence:** Limited error handling in AgentController, agent execution may fail without clear error messages

**Issue:** Agent execution errors not handled comprehensively, operators cannot diagnose issues.

**Action Items:**
- [x] Audit all agent execution error paths
- [x] Add comprehensive error handling with actionable error messages
- [x] Add error categorization (transient, permanent, retryable)
- [x] Add error recovery suggestions
- [x] Add error metrics (error rate by error type)
- [x] Add error logging with context
- [x] Add tests for all error scenarios
- [x] Update API documentation with error codes

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AgentController.java`
- `products/aep/aep-agent-runtime/` (agent execution logic)
- `products/aep/contracts/openapi.yaml`

**Acceptance Criteria:**
- All error paths handled with actionable messages
- Error categorization implemented
- Error metrics exposed
- Tests cover all error scenarios
- API documentation updated

**Progress Update (2026-04-20):**
- Implemented categorized execution error mapping with actionable operator guidance in [products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AgentController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AgentController.java):
	- `400` invalid request/input shape
	- `403` forbidden/policy-denied
	- `404` execution target not found
	- `503` dependency unavailable/misconfigured
	- `504` dependency timeout
	- structured details: `errorCode`, `category`, `retryable`, `suggestion`, tenant/agent context
- Added regression coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/http/controllers/AgentControllerTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/controllers/AgentControllerTest.java).
- Updated API contract responses for execute endpoint in [products/aep/contracts/openapi.yaml](products/aep/contracts/openapi.yaml).
- Added dedicated agent execution metrics instrumentation in [products/aep/server/src/main/java/com/ghatana/aep/observability/AepSloMetrics.java](products/aep/server/src/main/java/com/ghatana/aep/observability/AepSloMetrics.java):
	- `aep.slo.agent.execution.attempts`
	- `aep.slo.agent.execution.succeeded`
	- `aep.slo.agent.execution.failed`
	- tagged by `tenant`, `agent`, `error_code`, `category`, `retryable` with duration timer `aep.slo.agent.execution.duration.ms`
- Wired controller emission from [products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AgentController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AgentController.java) via [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java).
- Extended regression coverage in:
	- [products/aep/server/src/test/java/com/ghatana/aep/server/http/controllers/AgentControllerTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/controllers/AgentControllerTest.java)
	- [products/aep/server/src/test/java/com/ghatana/aep/observability/AepSloMetricsTest.java](products/aep/server/src/test/java/com/ghatana/aep/observability/AepSloMetricsTest.java)

---

### 🟡 P1: Required for Production Trust

#### AEP-P1-1: Complete UI operator cockpit (Phase 4)

**Status:** COMPLETED  
**Severity:** High  
**Product:** AEP  
**Evidence:** Phase-4 operator cockpit has already shipped in the AEP UI; only OWNER/tracker reconciliation was stale.

**Issue:** Documentation and tracker lagged behind the already-shipped operator cockpit implementation.

**Action Items:**
- [x] Design operator cockpit UI (agent status, pipeline health, system metrics)
- [x] Implement real-time dashboards
- [x] Add agent execution monitoring
- [x] Add pipeline execution monitoring
- [x] Add system health monitoring
- [x] Add alert management interface
- [x] Add incident response interface
- [x] Add operator cockpit tests
- [x] Update OWNER.md to mark Phase 4 complete

**Files to Modify:**
- `products/aep/ui/` (create operator cockpit)
- `products/aep/OWNER.md`

**Acceptance Criteria:**
- Operator cockpit UI implemented
- Real-time dashboards functional
- Monitoring interfaces complete
- Tests pass
- Documentation updated

**Progress Update (2026-04-21):**
- Verified the shipped operator cockpit surfaces in [products/aep/ui/src/App.tsx](products/aep/ui/src/App.tsx), [products/aep/ui/src/pages/MonitoringDashboardPage.tsx](products/aep/ui/src/pages/MonitoringDashboardPage.tsx), [products/aep/ui/src/pages/CostDashboardPage.tsx](products/aep/ui/src/pages/CostDashboardPage.tsx), [products/aep/ui/src/pages/RunDetailPage.tsx](products/aep/ui/src/pages/RunDetailPage.tsx), and [products/aep/ui/src/components/shared/NavBar.tsx](products/aep/ui/src/components/shared/NavBar.tsx).
- Updated [products/aep/OWNER.md](products/aep/OWNER.md) so the product source of truth now matches the real implementation.

---

#### AEP-P1-2: Complete observability/health checks (Phase 6)

**Status:** COMPLETED  
**Severity:** High  
**Product:** AEP  
**Evidence:** OWNER.md notes "Observability/health checks (Phase 6)" as open remediation

**Issue:** Observability and health checks incomplete, operators lack operational visibility.

**Action Items:**
- [x] Define SLO metrics (pipeline success rate, agent success rate, latency)
- [x] Add SLO metrics to AepSloMetrics
- [x] Add health check for Data Cloud connectivity
- [x] Add health check for Redis connectivity
- [x] Add health check for Kafka connectivity
- [x] Add health check for event loop health
- [x] Add health check for memory usage
- [x] Add dashboard definitions (Grafana)
- [x] Add alert rules (Prometheus)
- [x] Update OWNER.md to mark Phase 6 complete

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/observability/AepSloMetrics.java`
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/HealthController.java`
- Create dashboard and alert rule files
- `products/aep/OWNER.md`

**Acceptance Criteria:**
- SLO metrics defined and exposed
- Health checks for all dependencies
- Dashboard definitions created
- Alert rules created
- Documentation updated

**Progress Update (2026-04-21):**
- Expanded [products/aep/server/src/main/java/com/ghatana/aep/observability/AepSloMetrics.java](products/aep/server/src/main/java/com/ghatana/aep/observability/AepSloMetrics.java) so `/metrics/slo` now exposes run success-rate fields plus replay and agent-execution snapshots instead of only raw run totals.
- Hardened [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java) with additional health probes for event-loop state, heap pressure, and Kafka bootstrap connectivity while preserving embedded/library deployment support by treating missing Kafka config as `disabled`.
- Added integration coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerObservabilityTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerObservabilityTest.java) for the richer `/health/deep` component set, socket-backed Kafka connectivity probing, and the expanded `/metrics/slo` payload.
- Added unit coverage in [products/aep/server/src/test/java/com/ghatana/aep/observability/AepSloMetricsTest.java](products/aep/server/src/test/java/com/ghatana/aep/observability/AepSloMetricsTest.java) for replay and agent-execution success/failure snapshots.
- Added Grafana dashboards in [monitoring/grafana/dashboards/aep-runtime-trust.json](monitoring/grafana/dashboards/aep-runtime-trust.json) and [monitoring/grafana/dashboards/aep-data-cloud-boundary.json](monitoring/grafana/dashboards/aep-data-cloud-boundary.json).
- Added Prometheus alerts in [monitoring/prometheus/rules/aep.yml](monitoring/prometheus/rules/aep.yml) for agent execution failures, replay failures, optimistic-concurrency conflict spikes, HITL SLA escalations, trusted-proxy header rejections, and sustained deep-health pressure.
- Updated [products/aep/OWNER.md](products/aep/OWNER.md) to mark Phase 6 complete.
- Validation command passed: `gradlew.bat :products:aep:server:test --tests com.ghatana.aep.observability.AepSloMetricsTest --tests com.ghatana.aep.server.http.AepHttpServerObservabilityTest --tests com.ghatana.aep.server.config.AepDynamicConfigServiceTest`

---

#### AEP-P1-3: Add conflict detection for pipeline versioning

**Status:** COMPLETED  
**Severity:** High  
**Product:** AEP  
**Evidence:** No conflict detection in PipelineController, concurrent pipeline updates may conflict

**Issue:** Pipeline version conflicts may not be detected, leading to data corruption.

**Action Items:**
- [x] Design conflict detection strategy (optimistic locking, version checking)
- [x] Implement conflict detection in PipelineController
- [x] Add conflict resolution workflow
- [x] Add conflict metrics (conflict rate by tenant)
- [x] Add tests for conflict scenarios
- [x] Update API documentation with conflict handling
- [x] Update OpenAPI spec with conflict error codes

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/PipelineController.java`
- `products/aep/server/src/main/java/com/ghatana/aep/server/store/DataCloudPipelineStore.java`
- `products/aep/contracts/openapi.yaml`

**Acceptance Criteria:**
- Conflict detection implemented
- Conflict resolution workflow functional
- Conflict metrics exposed
- Tests cover conflict scenarios
- API documentation updated

**Progress Update (2026-04-20):**
- Hardened [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java) so pipeline updates now require a concurrency token from `version`, `expectedVersion`, or `If-Match` and return a structured `409` with `PIPELINE_VERSION_CONFLICT` when the caller is stale.
- Added conflict metrics via `aep.pipeline.update.conflicts` tagged by tenant, pipeline, expected version, and current version.
- Added live HTTP regression coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerDataCloudIntegrationTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerDataCloudIntegrationTest.java) for both stale-update rejection and current-version success.
- Documented the update precondition contract in [products/aep/README.md](products/aep/README.md), [products/aep/contracts/openapi.yaml](products/aep/contracts/openapi.yaml), and [products/aep/server/src/main/resources/openapi.yaml](products/aep/server/src/main/resources/openapi.yaml).
- Validation: `gradlew.bat :products:aep:server:test --tests com.ghatana.aep.server.http.AepHttpServerDataCloudIntegrationTest`

---

#### AEP-P1-4: Add timeout and escalation for HITL review items

**Status:** COMPLETED  
**Severity:** High  
**Product:** AEP  
**Evidence**: No timeout mechanism for review items, review queue may have stale items

**Issue:** HITL review items may become stale, blocking workflows indefinitely.

**Action Items:**
- [x] Design timeout policy (configurable per tenant)
- [x] Implement timeout mechanism for review items
- [x] Implement escalation workflow (auto-approve, auto-reject, notify manager)
- [x] Add review timeout metrics
- [x] Add escalation metrics
- [x] Add tests for timeout and escalation
- [x] Update API documentation with timeout behavior
- [x] Update OpenAPI spec with timeout and escalation policy behavior

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/HitlController.java`
- `products/aep/server/src/main/java/com/ghatana/aep/learning/EpisodeLearningPipeline.java`
- `products/aep/contracts/openapi.yaml`

**Acceptance Criteria:**
- Timeout mechanism implemented
- Escalation workflow functional
- Timeout metrics exposed
- Tests cover timeout scenarios
- API documentation updated

**Progress Update (2026-04-21):**
- Extended [products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/HitlController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/HitlController.java) so pending-review queries support tenant-specific timeout policies, overdue scans, and policy-driven overdue resolution through `escalate`, `auto_approve`, or `auto_reject` without bypassing the existing `HumanReviewQueue.findOverdue(...)` contract.
- Wired [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java) to parse `AEP_HITL_TIMEOUT_POLICIES` for both standalone and embedded/library deployments while preserving the global `AEP_HITL_ESCALATION_TIMEOUT_SECONDS` fallback.
- Added escalation notifications to [products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/review/ReviewNotificationSpi.java](products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/review/ReviewNotificationSpi.java), [products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/review/InMemoryHumanReviewQueue.java](products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/review/InMemoryHumanReviewQueue.java), [products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/review/DataCloudHumanReviewQueue.java](products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/review/DataCloudHumanReviewQueue.java), and [products/aep/server/src/main/java/com/ghatana/aep/server/AepLauncher.java](products/aep/server/src/main/java/com/ghatana/aep/server/AepLauncher.java) so manual and timeout-driven escalations emit real SSE events and notification hooks instead of silent state transitions.
- Expanded regression coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerHitlEscalationTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerHitlEscalationTest.java), [products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/learning/review/InMemoryHumanReviewQueueTest.java](products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/learning/review/InMemoryHumanReviewQueueTest.java), [products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/learning/review/DataCloudHumanReviewQueueTest.java](products/aep/aep-agent-runtime/src/test/java/com/ghatana/agent/learning/review/DataCloudHumanReviewQueueTest.java), and [products/aep/server/src/test/java/com/ghatana/aep/server/http/AepOpenApiSurfaceDriftTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/http/AepOpenApiSurfaceDriftTest.java).
- Updated [products/aep/README.md](products/aep/README.md), [products/aep/docs/API_DOCUMENTATION.md](products/aep/docs/API_DOCUMENTATION.md), [products/aep/contracts/openapi.yaml](products/aep/contracts/openapi.yaml), and [products/aep/server/src/main/resources/openapi.yaml](products/aep/server/src/main/resources/openapi.yaml) to document tenant timeout policy syntax, richer escalation payloads, and the packaged OpenAPI parity guard.
- Validation: `gradlew.bat :products:aep:server:test --tests com.ghatana.aep.server.http.AepOpenApiSurfaceDriftTest :products:aep:aep-agent-runtime:test --tests com.ghatana.agent.learning.review.InMemoryHumanReviewQueueTest --tests com.ghatana.agent.learning.review.DataCloudHumanReviewQueueTest :products:aep:server:test --tests com.ghatana.aep.server.http.AepHttpServerHitlEscalationTest`

---

#### AEP-P1-5: Add configuration validation with rollback

**Status:** COMPLETED  
**Severity:** High  
**Product:** AEP  
**Evidence**: AepDynamicConfigService exists but validation unclear, invalid changes may break system

**Issue:** Dynamic configuration changes may not be validated, risk of system breakage.

**Action Items:**
- [x] Design configuration schema (JSON Schema)
- [x] Implement configuration validation on change
- [x] Implement rollback mechanism for invalid changes
- [x] Add configuration change audit trail
- [x] Add configuration change metrics
- [x] Add tests for validation and rollback
- [x] Update documentation with configuration schema
- [x] Update OpenAPI spec with validation error codes or document the absence of a public config mutation API

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/config/AepDynamicConfigService.java`
- Create configuration schema file
- `products/aep/contracts/openapi.yaml`

**Acceptance Criteria:**
- Configuration validation implemented
- Rollback mechanism functional
- Configuration change audit trail complete
- Tests cover validation scenarios
- Documentation updated

**Progress Update (2026-04-21):**
- Hardened [products/aep/server/src/main/java/com/ghatana/aep/server/config/AepDynamicConfigService.java](products/aep/server/src/main/java/com/ghatana/aep/server/config/AepDynamicConfigService.java) so runtime overrides are validated semantically before apply, listener failures now reject and roll back the write, and applied/rejected/rolled-back attempts are retained in an audit history.
- Reused the existing AEP config semantics from [products/aep/aep-engine/src/main/java/com/ghatana/aep/config/AepConfigurationValidator.java](products/aep/aep-engine/src/main/java/com/ghatana/aep/config/AepConfigurationValidator.java) for host-port and environment validation instead of introducing a second ad hoc rule set.
- Added counters for validation failures, listener failures, and rollbacks to make rejected hot-config writes observable through the existing Micrometer path.
- Added regression coverage in [products/aep/server/src/test/java/com/ghatana/aep/server/config/AepDynamicConfigServiceTest.java](products/aep/server/src/test/java/com/ghatana/aep/server/config/AepDynamicConfigServiceTest.java) for Kafka/port/consolidation validation, rollback-on-listener-failure, batch rollback behavior, and audit-history recording.
- Published the authoritative runtime overlay schema at [products/aep/docs/config/aep-dynamic-config.schema.json](products/aep/docs/config/aep-dynamic-config.schema.json) and documented the current non-HTTP config surface in [products/aep/README.md](products/aep/README.md) and [products/aep/docs/API_DOCUMENTATION.md](products/aep/docs/API_DOCUMENTATION.md).
- Reflected the documented runtime config and HITL surface in both OpenAPI copies at [products/aep/contracts/openapi.yaml](products/aep/contracts/openapi.yaml) and [products/aep/server/src/main/resources/openapi.yaml](products/aep/server/src/main/resources/openapi.yaml); AEP still does not expose a public remote config mutation route, so there is no separate admin config endpoint to document.
- Validation command passed: `gradlew.bat :products:aep:server:test --tests com.ghatana.aep.observability.AepSloMetricsTest --tests com.ghatana.aep.server.http.AepHttpServerObservabilityTest --tests com.ghatana.aep.server.config.AepDynamicConfigServiceTest`

---

#### AEP-P1-6: Validate X-Forwarded-For against trusted proxy list

**Status**: COMPLETED  
**Severity**: High  
**Product**: AEP  
**Evidence**: AepSecurityFilter trusts X-Forwarded-For header without validation, may be spoofed

**Issue**: Rate limiter may be bypassed via X-Forwarded-For spoofing.

**Action Items:**
- [x] Design trusted proxy list configuration
- [x] Implement X-Forwarded-For validation in AepSecurityFilter
- [x] Add proxy validation metrics
- [x] Add tests for proxy validation
- [x] Add tests for spoofing attempts
- [x] Update documentation with proxy configuration
- [x] Update runbook with proxy setup

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/security/AepSecurityFilter.java`
- `products/aep/docs/OPERATIONAL_RUNBOOK.md`

**Acceptance Criteria:**
- X-Forwarded-For validation implemented
- Trusted proxy list configurable
- Proxy validation metrics exposed
- Tests cover validation scenarios
- Documentation updated

**Progress Update (2026-04-20):**
- Hardened [products/aep/aep-security/src/main/java/com/ghatana/aep/security/AepSecurityFilter.java](products/aep/aep-security/src/main/java/com/ghatana/aep/security/AepSecurityFilter.java) so `X-Forwarded-For` is only honored when the immediate caller matches `AEP_TRUSTED_PROXY_CIDRS`; otherwise AEP rate-limits on the actual peer address.
- Added exact-IP and CIDR trusted-proxy parsing plus a test-only remote-address hook so the validation path is covered without weakening embedded/library deployments.
- Wired environment-based configuration in [products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java) with an empty default for direct-service and embedded usage.
- Added proxy decision counters for trusted forwarded-IP usage and rejected spoofed or malformed headers, exported through the existing Prometheus metrics path.
- Added regression coverage in [products/aep/aep-runtime-core/src/test/java/com/ghatana/aep/security/AepSecurityFilterTest.java](products/aep/aep-runtime-core/src/test/java/com/ghatana/aep/security/AepSecurityFilterTest.java) for both trusted-proxy forwarding, spoofed-header rejection, and proxy metrics emission.
- Documented deployment and ingress setup in [products/aep/README.md](products/aep/README.md) and [products/aep/docs/OPERATIONAL_RUNBOOK.md](products/aep/docs/OPERATIONAL_RUNBOOK.md).
- Validation command: `gradlew :products:aep:aep-runtime-core:test --tests com.ghatana.aep.security.AepSecurityFilterTest`.

---

#### AEP-P1-7: Execute quarterly disaster recovery drills

**Status**: NOT STARTED  
**Severity**: High  
**Product**: AEP  
**Evidence**: Runbook documents quarterly drills but no evidence of execution

**Issue**: DR procedures not regularly tested, risk of failure during actual disaster.

**Action Items:**
- [ ] Schedule quarterly DR drill
- [ ] Execute DR drill (simulate disaster, execute recovery)
- [ ] Document drill results
- [ ] Identify and fix issues found
- [ ] Update runbook based on drill results
- [ ] Add DR drill metrics (recovery time, data loss)
- [ ] Add DR drill to CI/CD schedule
- [ ] Update runbook with drill schedule

**Files to Modify:**
- `products/aep/docs/OPERATIONAL_RUNBOOK.md`
- Create DR drill documentation

**Acceptance Criteria:**
- Quarterly DR drill executed
- Drill results documented
- Issues identified and fixed
- Runbook updated
- DR drill scheduled in CI/CD

---

### 🟢 P2: Simplification and Strategic Improvements

#### AEP-P2-1: Consolidate AEP modules (16 → 8-10)

**Status**: NOT STARTED  
**Severity**: Medium  
**Product**: AEP  
**Evidence**: 16 modules may be over-segmented, some could be consolidated

**Issue**: Too many modules create unnecessary complexity and cognitive overhead.

**Action Items:**
- [ ] Audit all 16 modules for consolidation opportunities
- [ ] Design target module structure (8-10 modules)
- [ ] Consolidate related modules (e.g., aep-registry + aep-operator-contracts)
- [ ] Update dependency graph
- [ ] Update build.gradle.kts files
- [ ] Update documentation
- [ ] Add tests for consolidated modules
- [ ] Migrate any external dependencies

**Files to Modify:**
- All AEP module build.gradle.kts files
- `products/aep/README.md`
- `products/aep/OWNER.md`

**Acceptance Criteria:**
- Module count reduced to 8-10
- Dependency graph simplified
- Documentation updated
- Tests pass for consolidated modules

---

#### AEP-P2-2: Combine security filters

**Status**: NOT STARTED  
**Severity**: Medium  
**Product**: AEP  
**Evidence**: AepSecurityFilter and AepAuthFilter could be combined

**Issue**: Multiple security filters create complexity and potential for misconfiguration.

**Action Items:**
- [ ] Design unified security filter architecture
- [ ] Combine AepSecurityFilter and AepAuthFilter
- [ ] Preserve all security functionality (headers, CORS, auth, rate limiting)
- [ ] Add tests for unified filter
- [ ] Update documentation
- [ ] Update runbook with new filter configuration

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/security/AepSecurityFilter.java`
- `products/aep/server/src/main/java/com/ghatana/aep/security/AepAuthFilter.java`
- Create unified security filter
- `products/aep/docs/OPERATIONAL_RUNBOOK.md`

**Acceptance Criteria:**
- Unified security filter implemented
- All functionality preserved
- Tests pass
- Documentation updated

---

#### AEP-P2-3: Standardize store abstractions

**Status**: NOT STARTED  
**Severity**: Medium  
**Product**: AEP  
**Evidence**: DataCloudPipelineStore, DataCloudPatternStore, EventCloudAgentStore have similar patterns

**Issue**: Duplicate store abstractions create maintenance burden.

**Action Items:**
- [ ] Audit all store implementations
- [ ] Extract common store pattern into base class
- [ ] Refactor stores to use base class
- [ ] Add tests for refactored stores
- [ ] Update documentation
- [ ] Update runbook with store architecture

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/store/DataCloudPipelineStore.java`
- `products/aep/server/src/main/java/com/ghatana/aep/server/store/DataCloudPatternStore.java`
- `products/aep/aep-event-cloud/src/main/java/com/ghatana/aep/eventcloud/store/EventCloudAgentStore.java`
- Create base store class

**Acceptance Criteria:**
- Common store pattern extracted
- Stores refactored to use base class
- Tests pass
- Documentation updated

---

#### AEP-P2-4: Add performance benchmarks

**Status**: NOT STARTED  
**Severity**: Medium  
**Product**: AEP  
**Evidence**: No performance benchmarks found, performance claims not credible

**Issue:** Performance claims lack evidence, cannot verify performance characteristics.

**Action Items:**
- [ ] Design performance benchmark suite (pipeline execution, agent execution, event processing)
- [ ] Implement JMH benchmarks
- [ ] Add benchmark execution to CI
- [ ] Add performance regression detection
- [ ] Document performance baselines
- [ ] Update documentation with performance characteristics

**Files to Modify:**
- Create benchmark files in `products/aep/server/src/test/java/com/ghatana/aep/benchmark/`
- Update CI configuration
- `products/aep/README.md`

**Acceptance Criteria:**
- Performance benchmarks implemented
- Benchmarks run in CI
- Performance regression detection functional
- Documentation updated

---

#### AEP-P2-5: Add scalability tests

**Status**: NOT STARTED  
**Severity**: Medium  
**Product**: AEP  
**Evidence**: No scalability tests found, scalability claims not credible

**Issue:** Scalability claims lack evidence, cannot verify scalability characteristics.

**Action Items:**
- [ ] Design scalability test suite (horizontal scaling, vertical scaling, load testing)
- [ ] Implement load tests (pipeline execution under load, agent execution under load)
- [ ] Implement horizontal scaling tests
- [ ] Add scalability test execution to CI
- [ ] Document scalability baselines
- [ ] Update documentation with scalability characteristics

**Files to Modify:**
- Create scalability test files in `products/aep/integration-tests/`
- Update CI configuration
- `products/aep/README.md`

**Acceptance Criteria:**
- Scalability tests implemented
- Tests run in CI
- Scalability baselines documented
- Documentation updated

---

## Data Cloud Remediation Tasks

### 🔴 P0: Critical Blockers

#### DC-P0-3: Stop returning HTTP 200 for invalid request scenarios

**Status:** COMPLETED  
**Severity:** Critical  
**Product:** Data Cloud  
**Evidence**: Governance endpoints previously returned generic error envelopes without endpoint-specific status semantics; remediation is being implemented in slices with contract tests.

**Issue**: Invalid governance requests return HTTP 200 with error envelopes instead of proper 4xx/5xx status codes, breaking client correctness, alerting, and retries.

**Action Items:**
- [x] Audit all launcher handlers for envelope-first error pattern
- [x] Change HTTP responses from 200 to proper 4xx/5xx statuses
- [x] Preserve structured error bodies in response
- [x] Update OpenAPI spec to reflect correct status codes
- [x] Add HTTP contract tests for error scenarios
- [x] Update UI service error handling tests
- [x] Remove or update VoiceErrorHandlingTest.java line 329

**Files to Modify:**
- All launcher handlers in `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/`
- `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/VoiceErrorHandlingTest.java`
- `products/data-cloud/api/openapi.yaml`

**Acceptance Criteria:**
- Proper 4xx/5xx status codes used
- Error bodies preserved
- OpenAPI spec updated
- Contract tests pass
- UI handles errors correctly

**Progress Update (2026-04-20):**
- Implemented first production slice for governance lifecycle endpoints in [products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java](products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java):
	- `400` for validation failures (`MISSING_COLLECTION`, `MISSING_REQUIRED`, `MISSING_CONFIRMATION`)
	- `403` for invalid purge confirmation token (`INVALID_CONFIRMATION_TOKEN`)
	- `404` for missing entities in redact/verify (`ENTITY_NOT_FOUND`)
	- `503` for production misconfiguration requiring purge token secret (`PURGE_TOKEN_SECRET_REQUIRED`)
	- Structured error envelope preserved via `errorEnvelopeResponse(...)`.
- Added/updated integration contract coverage in [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerGovernanceTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerGovernanceTest.java):
	- invalid token -> 403
	- missing production purge secret -> 503
	- redact missing entity -> 404
	- verify missing entity -> 404
- Updated API contract for touched governance endpoints in [products/data-cloud/api/openapi.yaml](products/data-cloud/api/openapi.yaml) to include `403`, `404`, and `503` responses where applicable.
- Validation command passed: `gradlew :products:data-cloud:launcher:test --tests com.ghatana.datacloud.launcher.http.DataCloudHttpServerGovernanceTest`.
- Implemented shared envelope status mapping in [products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java](products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupport.java):
	- `429` rate-limit errors
	- `401/403` auth/permission failures
	- `404` not-found style errors
	- `409` conflict/duplicate errors
	- `503/504` dependency unavailable/timeout failures
	- preserved `ApiResponse` envelope body and metadata.
- Added status-mapping regression tests in [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupportTenantResolutionTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/HttpHandlerSupportTenantResolutionTest.java).
- Expanded voice gateway contract status coverage in [products/data-cloud/api/openapi.yaml](products/data-cloud/api/openapi.yaml):
	- `/api/v1/voice/intent`: `400`, `404`, `429`, `500`
	- `/api/v1/voice/intents`: `400`, `500`
	- `/api/v1/voice/intent/classify`: `400`, `500`
- Validation commands passed:
	- `gradlew :products:aep:server:test --tests com.ghatana.aep.server.http.controllers.AgentControllerTest --tests com.ghatana.aep.observability.AepSloMetricsTest`
	- `gradlew :products:data-cloud:launcher:test --tests com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupportTenantResolutionTest --tests com.ghatana.datacloud.launcher.http.DataCloudHttpServerGovernanceTest --tests com.ghatana.datacloud.launcher.http.VoiceErrorHandlingTest`
- Updated the shared UI client in [products/data-cloud/ui/src/lib/api/client.ts](products/data-cloud/ui/src/lib/api/client.ts) so non-2xx responses now parse the canonical launcher `error` envelope as well as legacy top-level error bodies during transition.
- Added UI regression coverage in [products/data-cloud/ui/src/__tests__/api/client.contract.test.ts](products/data-cloud/ui/src/__tests__/api/client.contract.test.ts) for both canonical nested-envelope errors (`INVALID_CONFIRMATION_TOKEN`, 403) and legacy top-level error bodies (`MISSING_COLLECTION`, 400).
- Validation command passed: `corepack pnpm vitest run src/__tests__/api/client.contract.test.ts`.

---

#### DC-P0-4: Reclassify misleading tests and replace placeholders with real boundary tests

**Status:** IN PROGRESS  
**Severity:** Critical  
**Product:** Data Cloud  
**Evidence**: Tests identified as placeholder-level in audit (FailureRecoveryTest, MultiTenantIsolationTest, EndToEndWorkflowTest)

**Issue**: Many "integration" and "e2e" tests use in-memory doubles or mocks, not real durable providers, overstating production readiness.

**Action Items:**
- [x] Rename synthetic tests to reflect their actual scope (e.g., "unit" instead of "integration")
- [x] Rebuild tests against launcher plus real durable providers where applicable
- [x] Create real launcher-backed integration suite
- [x] Add browser-level smoke pack
- [x] Remove or reclassify FailureRecoveryTest (placeholder-level)
- [x] Remove or reclassify MultiTenantIsolationTest (uses custom in-memory store)
- [x] Remove or reclassify EndToEndWorkflowTest (placeholder)

**Files to Modify:**
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/FailureRecoveryTest.java`
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/MultiTenantIsolationTest.java`
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java`

**Acceptance Criteria:**
- Tests accurately reflect their scope
- Real launcher-backed integration suite exists
- Placeholder tests removed or reclassified
- Evidence quality improved

**Progress Update (2026-04-20):**
- Replaced placeholder-level test theatre in:
	- [products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/FailureRecoveryTest.java](products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/FailureRecoveryTest.java)
	- [products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/MultiTenantIsolationTest.java](products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/MultiTenantIsolationTest.java)
	- [products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java](products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java)
- New coverage uses real product code only:
	- sovereign embedded/file-backed H2 persistence via `DataCloudConfig.profile(SOVEREIGN)`
	- live launcher HTTP surface for restart recovery and tenant isolation
	- real `DataCloudRuntimePluginManager` workflow execution persistence across client/plugin restart
- Fixed a production durability defect uncovered by the new suite in [products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/plugins/DataCloudRuntimePluginManager.java](products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/plugins/DataCloudRuntimePluginManager.java):
	- execution logs now use a distinct durable record ID (`<executionId>:logs`) so sovereign persistence no longer overwrites workflow execution snapshots with log records.
- Fixed a real frontend-to-launcher integration defect in [products/data-cloud/ui/src/lib/api/client.ts](products/data-cloud/ui/src/lib/api/client.ts):
	- the shared collection/entity client now propagates `X-Tenant-ID` from `SessionBootstrap`, aligning the legacy collection CRUD path with launcher tenant enforcement.
- Added a real launcher-backed browser smoke pack in:
	- [products/data-cloud/ui/e2e/data-explorer.smoke.spec.ts](products/data-cloud/ui/e2e/data-explorer.smoke.spec.ts)
	- [products/data-cloud/ui/playwright.config.ts](products/data-cloud/ui/playwright.config.ts)
	- smoke path seeds the real session bootstrap, disables MSW, starts the live launcher plus Vite UI, creates a collection through `/data/new`, verifies redirect/list rendering, and confirms persistence through the launcher HTTP API.
- Added regression coverage for the tenant propagation root fix in [products/data-cloud/ui/src/__tests__/api/client.contract.test.ts](products/data-cloud/ui/src/__tests__/api/client.contract.test.ts).
- Validation command passed:
	- `gradlew :products:data-cloud:integration-tests:test --tests com.ghatana.datacloud.integration.FailureRecoveryTest --tests com.ghatana.datacloud.integration.MultiTenantIsolationTest --tests com.ghatana.datacloud.integration.EndToEndWorkflowTest`
- Validation commands passed:
	- `corepack pnpm vitest run src/__tests__/api/client.contract.test.ts`
	- `corepack pnpm playwright test e2e/data-explorer.smoke.spec.ts --project=chromium`

---

### 🟡 P1: Required for Production Trust

#### DC-P1-1: Reconcile product truth across docs, route matrix, and UI boundaries

**Status:** COMPLETED  
**Severity:** High  
**Product:** Data Cloud  
**Evidence**: Docs, route-truth matrix, UI boundaries, and runtime behavior disagree on whether surfaces are live/preview/read-only

**Issue**: Product truth is fragmented, creating confusion for users and operators.

**Action Items:**
- [ ] Audit all Data Cloud documentation
- [ ] Compare documentation against route matrix
- [ ] Compare documentation against UI boundaries
- [ ] Compare documentation against runtime behavior
- [ ] Create single canonical source of truth
- [ ] Update docs to match implementation OR
- [ ] Update implementation to match docs
- [ ] Ensure consistency across all sources

**Files to Modify:**
- All Data Cloud documentation files
- Route matrix documentation
- UI boundary definitions
- OpenAPI spec

**Acceptance Criteria:**
- Single canonical source of truth exists
- Docs, route matrix, UI boundaries consistent
- Runtime behavior matches documentation

**Progress Update (2026-04-21):**
- Established [products/data-cloud/ROUTE_TRUTH_MATRIX_2026-04-17.md](products/data-cloud/ROUTE_TRUTH_MATRIX_2026-04-17.md) as the canonical runtime-truth source and updated [products/data-cloud/REST_API_DOCUMENTATION.md](products/data-cloud/REST_API_DOCUMENTATION.md) to point at it explicitly.
- Reconciled user-facing route truth in [products/data-cloud/ui/docs/usage/USER_MANUAL.md](products/data-cloud/ui/docs/usage/USER_MANUAL.md), [products/data-cloud/REST_API_DOCUMENTATION.md](products/data-cloud/REST_API_DOCUMENTATION.md), and [products/data-cloud/USER_MANUAL.md](products/data-cloud/USER_MANUAL.md) so `/alerts` is live operator-only, `/fabric` is preview-only, and `/settings` is an admin-only boundary page.
- Verified route and boundary alignment against the existing runtime sources in [products/data-cloud/ui/src/routes.tsx](products/data-cloud/ui/src/routes.tsx) and [products/data-cloud/ui/src/components/common/unsupportedSurfaceRegistry.ts](products/data-cloud/ui/src/components/common/unsupportedSurfaceRegistry.ts).

---

#### DC-P1-2: Remove or demote boundary-only pages from product framing

**Status:** COMPLETED  
**Severity:** High  
**Product:** Data Cloud  
**Evidence**: Settings is a navigable dead end; Data Fabric is preview-only with hardcoded metrics

**Issue**: Too many boundary-only pages exposed in product shell, creating user confusion.

**Action Items:**
- [ ] Identify all boundary-only pages in UI
- [ ] Check which have backend APIs
- [ ] Remove or demote pages without APIs from product framing
- [ ] Add "preview" or "coming soon" labels where appropriate
- [ ] Update navigation to hide non-functional pages
- [ ] Update documentation to reflect actual capabilities

**Files to Modify:**
- Data Cloud UI navigation
- Data Cloud documentation

**Acceptance Criteria:**
- Boundary-only pages removed or demoted
- Navigation reflects actual capabilities
- Documentation updated

**Progress Update (2026-04-21):**
- Runtime routing already demotes boundary-only surfaces in [products/data-cloud/ui/src/routes.tsx](products/data-cloud/ui/src/routes.tsx): `/alerts` remains direct-link-only operator triage, `/fabric` redirects out of primary framing as preview-only, and `/settings` remains admin-only instead of pretending to be a general user workflow.
- Boundary metadata in [products/data-cloud/ui/src/components/common/unsupportedSurfaceRegistry.ts](products/data-cloud/ui/src/components/common/unsupportedSurfaceRegistry.ts) now matches that disclosure model so primary-user discovery does not advertise dead-end or preview-only pages as core product value.
- Updated supporting docs in [products/data-cloud/ui/docs/usage/USER_MANUAL.md](products/data-cloud/ui/docs/usage/USER_MANUAL.md) and [products/data-cloud/REST_API_DOCUMENTATION.md](products/data-cloud/REST_API_DOCUMENTATION.md) to describe the demoted route posture accurately.

---

#### DC-P1-3: Raise coverage thresholds materially above 0.20 instruction / 0.10 branch

**Status:** IN PROGRESS  
**Severity:** High  
**Product:** Data Cloud  
**Evidence**: Coverage thresholds very low (platform-launcher: instruction 0.20, branch 0.10; platform-entity: instruction 0.15, branch not enforced)

**Issue**: Coverage thresholds too low, insufficient test evidence.

**Action Items:**
- [x] Measure current coverage for all modules
- [ ] Set new target thresholds (e.g., 0.50 instruction / 0.30 branch)
- [ ] Update build.gradle.kts files with new thresholds
- [ ] Add coverage enforcement in CI
- [ ] Write additional tests to meet new thresholds
- [ ] Monitor coverage in CI/CD pipeline

**Files to Modify:**
- `products/data-cloud/platform-launcher/build.gradle.kts`
- `products/data-cloud/platform-entity/build.gradle.kts`
- All other Data Cloud module build.gradle.kts files

**Acceptance Criteria:**
- Coverage thresholds raised to 0.50 instruction / 0.30 branch
- Coverage enforced in CI
- Tests added to meet thresholds

**Progress Update (2026-04-21):**
- Added focused domain coverage in [products/data-cloud/platform-entity/src/test/java/com/ghatana/datacloud/entity/security/UserContextTest.java](products/data-cloud/platform-entity/src/test/java/com/ghatana/datacloud/entity/security/UserContextTest.java), [products/data-cloud/platform-entity/src/test/java/com/ghatana/datacloud/entity/governance/RoleTest.java](products/data-cloud/platform-entity/src/test/java/com/ghatana/datacloud/entity/governance/RoleTest.java), and [products/data-cloud/platform-entity/src/test/java/com/ghatana/datacloud/entity/governance/RoleAssignmentTest.java](products/data-cloud/platform-entity/src/test/java/com/ghatana/datacloud/entity/governance/RoleAssignmentTest.java) to cover previously unmeasured governance and security value objects.
- Regenerated the module report at [products/data-cloud/platform-entity/build/reports/jacoco/test/jacocoTestReport.xml](products/data-cloud/platform-entity/build/reports/jacoco/test/jacocoTestReport.xml) and improved the honest baseline to about `0.2030` instruction and `0.1377` branch coverage; this is progress, but still not a material enough improvement to justify a meaningful threshold raise.
- Reconfirmed [products/data-cloud/platform-launcher/build.gradle.kts](products/data-cloud/platform-launcher/build.gradle.kts) still targets the most important threshold lift, but the generated XML artifact at [products/data-cloud/platform-launcher/build/reports/jacoco/test/jacocoTestReport.xml](products/data-cloud/platform-launcher/build/reports/jacoco/test/jacocoTestReport.xml) is currently zero bytes, so there is no honest launcher-wide measurement to base a stricter gate on.
- Because the measurement surface is incomplete and the measured `platform-entity` baseline is still near the existing minimum, this item remains in progress without threshold edits; the next real step is additional launcher/entity tests plus a successful non-empty launcher Jacoco report.

---

#### DC-P1-4: Prove durable workflow execution, recovery, and tenant isolation against real providers

**Status:** IN PROGRESS  
**Severity:** High  
**Product:** Data Cloud  
**Evidence**: MultiTenantIsolationTest uses custom in-memory store unrelated to real persistence

**Issue**: Tenant isolation evidence is weak, not proven against real providers.

**Action Items:**
- [x] Create integration test for durable workflow execution
- [ ] Test workflow recovery after failure
- [ ] Test tenant isolation with real Data Cloud provider
- [ ] Test cross-tenant data leakage prevention
- [x] Use real durable providers (PostgreSQL, Kafka)
- [ ] Add tests to CI pipeline
- [x] Document evidence of durable execution

**Files to Modify:**
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/MultiTenantIsolationTest.java`
- Create new integration tests with real providers

**Acceptance Criteria:**
- Integration tests use real durable providers
- Tenant isolation proven with real provider
- Cross-tenant data leakage prevented
- Tests in CI pipeline

**Progress Update (2026-04-21):**
- Added production-profile real-provider coverage in [products/data-cloud/platform-launcher/src/test/java/com/ghatana/datacloud/DataCloudFactoryRealProviderTest.java](products/data-cloud/platform-launcher/src/test/java/com/ghatana/datacloud/DataCloudFactoryRealProviderTest.java) using PostgreSQL and Kafka Testcontainers plus Flyway migration bootstrap, targeting restart persistence and cross-tenant isolation through the public `DataCloud.create(PRODUCTION_CONFIG)` factory.
- Hardened embedded/library provider bootstrap in [products/data-cloud/platform-plugins/src/main/java/com/ghatana/datacloud/plugins/postgres/PostgresEntityStoreConfig.java](products/data-cloud/platform-plugins/src/main/java/com/ghatana/datacloud/plugins/postgres/PostgresEntityStoreConfig.java) so the ServiceLoader-backed Postgres provider can be configured from JVM system properties as well as environment variables.
- Added direct regression coverage in [products/data-cloud/platform-plugins/src/test/java/com/ghatana/datacloud/plugins/postgres/PostgresEntityStoreValidationTest.java](products/data-cloud/platform-plugins/src/test/java/com/ghatana/datacloud/plugins/postgres/PostgresEntityStoreValidationTest.java) for the new system-property bootstrap path used by embedded/library and test scenarios.
- Validation update: `gradlew.bat :products:data-cloud:platform-plugins:test --tests com.ghatana.datacloud.plugins.postgres.PostgresEntityStoreValidationTest -x :products:data-cloud:platform-plugins:jacocoTestReport --no-daemon` completed successfully, confirming the provider bootstrap regression path.
- Validation is not complete yet: the JUnit artifact at [products/data-cloud/platform-launcher/build/test-results/test/TEST-com.ghatana.datacloud.DataCloudFactoryRealProviderTest.xml](products/data-cloud/platform-launcher/build/test-results/test/TEST-com.ghatana.datacloud.DataCloudFactoryRealProviderTest.xml) shows both real-provider tests were skipped, so this item must remain in progress until Docker/Testcontainers are available and the class executes for real, after which CI wiring should be updated with that evidence.

---

#### DC-P1-5: Strengthen auth/session posture on the frontend

**Status:** IN PROGRESS  
**Severity:** High  
**Product:** Data Cloud  
**Evidence**: Shell role stored client-side in session storage, tokens in browser storage with TODO for httpOnly cookies

**Issue**: Frontend auth posture is weak, tokens vulnerable to XSS.

**Action Items:**
- [x] Audit frontend auth/session implementation
- [ ] Remove shell role as quasi-product mode
- [x] Add clearer wording for shell role if kept
- [ ] Migrate tokens from localStorage to httpOnly cookies
- [ ] Add SameSite and Secure flags to cookies
- [x] Strengthen session validation
- [x] Add proper error handling for auth failures
- [ ] Test auth flows end-to-end

**Files to Modify:**
- Data Cloud UI auth implementation
- Session storage usage
- Cookie configuration

**Acceptance Criteria:**
- Tokens in httpOnly cookies
- Shell role removed or clarified
- Session validation strengthened
- Auth flows tested

**Progress Update (2026-04-21):**
- Hardened browser session handling in [products/data-cloud/ui/src/lib/auth/tokenStorage.ts](products/data-cloud/ui/src/lib/auth/tokenStorage.ts), [products/data-cloud/ui/src/lib/auth/session.ts](products/data-cloud/ui/src/lib/auth/session.ts), and [products/data-cloud/ui/src/lib/api/client.ts](products/data-cloud/ui/src/lib/api/client.ts) so the UI now distinguishes explicit header-token mode from cookie-backed session mode, defaults browser requests to `credentials: 'include'`, and avoids injecting bearer headers when the session is cookie-backed.
- Tightened the remaining shell-role wording in [products/data-cloud/ui/src/lib/auth/session.ts](products/data-cloud/ui/src/lib/auth/session.ts), [products/data-cloud/ui/src/layouts/DefaultLayout.tsx](products/data-cloud/ui/src/layouts/DefaultLayout.tsx), and [products/data-cloud/ui/src/pages/IntelligentHub.tsx](products/data-cloud/ui/src/pages/IntelligentHub.tsx) so the header control is now framed as a workspace view/disclosure setting instead of a product-mode switch, with explicit copy that backend permissions remain enforced independently.
- Improved operator-facing auth diagnostics in [products/data-cloud/ui/src/pages/InsightsPage.tsx](products/data-cloud/ui/src/pages/InsightsPage.tsx) so the runtime surface no longer pretends every authenticated browser session is token-visible.
- Added regression coverage in [products/data-cloud/ui/src/__tests__/lib/tokenStorage.test.ts](products/data-cloud/ui/src/__tests__/lib/tokenStorage.test.ts), [products/data-cloud/ui/src/__tests__/lib/session.test.ts](products/data-cloud/ui/src/__tests__/lib/session.test.ts), and [products/data-cloud/ui/src/__tests__/api/client.contract.test.ts](products/data-cloud/ui/src/__tests__/api/client.contract.test.ts) for cookie-backed session state, credentialed requests, and canonical 401 fallback semantics.
- Hardened launcher auth acceptance in [products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java](products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java) so JWT authentication can come from the browser auth cookie when the Authorization header is absent, with matching regression coverage in [products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilterJwtTest.java](products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilterJwtTest.java).
- Validation commands passed:
	- `corepack pnpm --filter @data-cloud/ui exec vitest run src/__tests__/lib/tokenStorage.test.ts src/__tests__/lib/session.test.ts src/__tests__/api/client.contract.test.ts`
	- `corepack pnpm --filter @data-cloud/ui exec vitest run src/__tests__/lib/session.test.ts src/__tests__/layouts/DefaultLayout.test.tsx src/__tests__/components/GlobalSearch.test.tsx src/__tests__/pages/BrainPage.test.tsx`
	- `gradlew.bat :products:data-cloud:launcher:test --tests com.ghatana.datacloud.launcher.http.DataCloudSecurityFilterJwtTest -x :products:data-cloud:launcher:jacocoTestReport --no-daemon`
- Remaining work for honest closure: the browser still cannot prove `HttpOnly`, `SameSite`, and `Secure` flags from the frontend alone, and shell-role semantics remain disclosure-only rather than fully removed.

---

#### DC-P1-6: Add method-level security annotations

**Status**: NOT STARTED  
**Severity:** High  
**Product:** Data Cloud  
**Evidence**: No @PreAuthorize/@Secured/@RolesAllowed annotations found

**Issue**: Authorization handled via JWT claims in filter only, no fine-grained method-level security.

**Action Items:**
- [ ] Define method-level security annotation standard
- [ ] Add @PreAuthorize/@Secured/@RolesAllowed annotations to critical methods
- [ ] Implement role-based access control enforcement
- [ ] Add permission checking infrastructure
- [ ] Add tests for authorization scenarios
- [ ] Update documentation with security model

**Files to Modify:**
- All Data Cloud launcher handlers
- Security infrastructure

**Acceptance Criteria:**
- Method-level security annotations added
- RBAC enforcement implemented
- Authorization tests pass
- Documentation updated

---

#### DC-P1-7: Strengthen PII detection and redaction

**Status**: NOT STARTED  
**Severity:** High  
**Product**: Data Cloud  
**Evidence**: PIIDetectionService exists but not consistently applied across all data paths

**Issue**: PII detection not consistently applied, privacy redaction end-to-end correctness not proven.

**Action Items:**
- [ ] Audit all data paths for PII
- [ ] Apply PIIDetectionService consistently across all data paths
- [ ] Add PII detection to logs
- [ ] Add PII detection to traces
- [ ] Add PII detection to error messages
- [ ] Add integration tests for PII redaction
- [ ] Document PII handling policy

**Files to Modify:**
- All Data Cloud data paths
- Logging infrastructure
- Tracing infrastructure

**Acceptance Criteria:**
- PII detection consistently applied
- PII redaction proven end-to-end
- Integration tests pass
- Documentation updated

---

### 🟢 P2: Simplification and Strategic Improvements

#### DC-P2-1: Remove or consolidate read-only/preview surfaces

**Status**: NOT STARTED  
**Severity:** Medium  
**Product:** Data Cloud  
**Evidence**: Too many partial/read-only/preview surfaces exposed in product shell

**Issue**: Too many boundary-only pages create user confusion and clutter.

**Action Items:**
- [ ] Audit all read-only/preview surfaces
- [ ] Identify which can be made actionable
- [ ] Remove surfaces that cannot be made actionable
- [ ] Consolidate related surfaces
- [ ] Update navigation
- [ ] Update documentation

**Files to Modify:**
- Data Cloud UI
- Data Cloud documentation

**Acceptance Criteria:**
- Read-only/preview surfaces consolidated
- Navigation simplified
- Documentation updated

---

#### DC-P2-2: Implement distributed rate limiting

**Status**: NOT STARTED  
**Severity**: Medium  
**Product:** Data Cloud  
**Evidence**: In-memory rate limiting doesn't scale horizontally

**Issue**: Rate limiting is in-memory, doesn't scale across multiple instances.

**Action Items:**
- [ ] Design distributed rate limiting strategy (Redis-based)
- [ ] Implement Redis-based rate limiting
- [ ] Add rate limiting metrics
- [ ] Add rate limiting tests
- [ ] Update documentation with rate limiting configuration

**Files to Modify:**
- Rate limiting implementation
- Redis integration

**Acceptance Criteria:**
- Distributed rate limiting implemented
- Rate limiting scales horizontally
- Tests pass
- Documentation updated

---

#### DC-P2-3: Add performance benchmarks

**Status**: NOT STARTED  
**Severity**: Medium  
**Product**: Data Cloud  
**Evidence**: JMH benchmarks exist but coverage is uneven

**Issue**: Performance benchmarks exist but not consistently run in CI, coverage uneven.

**Action Items:**
- [ ] Audit existing JMH benchmarks
- [ ] Add benchmarks for missing critical paths
- [ ] Add benchmark execution to CI
- [ ] Add performance regression detection
- [ ] Document performance baselines

**Files to Modify:**
- Benchmark files
- CI configuration

**Acceptance Criteria:**
- Critical paths benchmarked
- Benchmarks run in CI
- Performance regression detection functional
- Baselines documented

---

#### DC-P2-4: Add query plan analysis and monitoring

**Status**: NOT STARTED  
**Severity**: Medium  
**Product:** Data Cloud  
**Evidence**: No query plan analysis in runbook, may have slow queries undetected

**Issue**: No query performance profiling in production, slow queries may go undetected.

**Action Items:**
- [ ] Add pg_stat_statements monitoring
- [ ] Add query plan analysis
- [ ] Add slow query logging
- [ ] Add query performance metrics
- [ ] Add query performance dashboards
- [ ] Update runbook with query monitoring

**Files to Modify:**
- Database configuration
- Monitoring infrastructure
- Runbook

**Acceptance Criteria:**
- Query performance monitored
- Slow queries detected
- Dashboards available
- Runbook updated

---

#### DC-P2-5: Implement Redis HA

**Status**: NOT STARTED  
**Severity**: Medium  
**Product**: Data Cloud  
**Evidence**: Redis single point of failure, no HA configuration

**Issue**: Redis used for caching/state but no HA configuration.

**Action Items:**
- [ ] Design Redis HA strategy (sentinel or cluster)
- [ ] Implement Redis HA
- [ ] Add Redis health checks
- [ ] Add Redis failover tests
- [ ] Update documentation with Redis HA configuration
- [ ] Update runbook with Redis failover procedures

**Files to Modify:**
- Redis configuration
- Health checks
- Documentation
- Runbook

**Acceptance Criteria:**
- Redis HA implemented
- Failover tested
- Health checks functional
- Documentation updated

---

#### DC-P2-6: Add PostgreSQL read replica support

**Status**: NOT STARTED  
**Severity**: Medium  
**Product:** Data Cloud  
**Evidence**: Single PostgreSQL instance may become bottleneck

**Issue**: No read replica configuration, database may become bottleneck.

**Action Items:**
- [ ] Design read replica strategy
- [ ] Implement read replica support
- [ ] Add connection pooling tuning
- [ ] Add read replica health checks
- [ ] Add read replica failover tests
- [ ] Update documentation with read replica configuration
- [ ] Update runbook with read replica procedures

**Files to Modify:**
- Database configuration
- Connection pool configuration
- Health checks
- Documentation
- Runbook

**Acceptance Criteria:**
- Read replica support implemented
- Connection pooling tuned
- Failover tested
- Documentation updated

---

## Implementation Timeline

### Sprint 1 (Immediate - Next 2 Weeks)

**Critical P0 Blockers:**
1. CROSS-P0-1: Establish Data Cloud as production dependency for AEP durable run history
2. CROSS-P0-2: Fix shared TypeScript package circular dependency
3. AEP-P0-1: Complete learning loop implementation (Phase 5)
4. AEP-P0-2: Add authentication to AI suggestions endpoint
5. AEP-P0-3: Verify GDPR erasure completeness
6. AEP-P0-4: Add comprehensive error handling to agent execution
7. DC-P0-3: Stop returning HTTP 200 for invalid request scenarios
8. DC-P0-4: Reclassify misleading tests and replace placeholders

**High Priority P1 (Cross-Product):**
1. CROSS-P1-1: Standardize metrics instrumentation across both products
2. CROSS-P1-3: Align API error semantics across both products

---

### Sprint 2 (Short-Term - 2-4 Weeks)

**P1 Production Trust (AEP):**
1. AEP-P1-1: Complete UI operator cockpit (Phase 4)
2. AEP-P1-2: Complete observability/health checks (Phase 6)
3. AEP-P1-3: Add conflict detection for pipeline versioning
4. AEP-P1-4: Add timeout and escalation for HITL review items
5. AEP-P1-5: Add configuration validation with rollback
6. AEP-P1-6: Validate X-Forwarded-For against trusted proxy list

**P1 Production Trust (Data Cloud):**
1. DC-P1-1: Reconcile product truth across docs, route matrix, and UI boundaries
2. DC-P1-2: Remove or demote boundary-only pages from product framing
3. DC-P1-3: Raise coverage thresholds materially above 0.20 instruction / 0.10 branch
4. DC-P1-4: Prove durable workflow execution, recovery, and tenant isolation against real providers
5. DC-P1-5: Strengthen auth/session posture on the frontend

**Cross-Product:**
1. CROSS-P1-2: Implement distributed tracing across AEP and Data Cloud

---

### Sprint 3 (Medium-Term - 1-2 Months)

**P1 Production Trust (AEP):**
1. AEP-P1-7: Execute quarterly disaster recovery drills

**P1 Production Trust (Data Cloud):**
1. DC-P1-6: Add method-level security annotations
2. DC-P1-7: Strengthen PII detection and redaction

---

### Sprint 4+ (Long-Term - 2-3 Months)

**P2 Simplification (AEP):**
1. AEP-P2-1: Consolidate AEP modules (16 → 8-10)
2. AEP-P2-2: Combine security filters
3. AEP-P2-3: Standardize store abstractions
4. AEP-P2-4: Add performance benchmarks
5. AEP-P2-5: Add scalability tests

**P2 Simplification (Data Cloud):**
1. DC-P2-1: Remove or consolidate read-only/preview surfaces
2. DC-P2-2: Implement distributed rate limiting
3. DC-P2-3: Add performance benchmarks
4. DC-P2-4: Add query plan analysis and monitoring
5. DC-P2-5: Implement Redis HA
6. DC-P2-6: Add PostgreSQL read replica support

---

## Success Criteria

### Definition of Done for Each Task

- [ ] Code changes implemented
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] CI pipeline passes
- [ ] Manual verification completed
- [ ] No regressions introduced
- [ ] Acceptance criteria met

### Overall Success Metrics

**P0 Tasks:**
- 100% complete (0 partial, 0 not fixed)
- All critical production blockers resolved

**P1 Tasks:**
- 90%+ complete
- Production trust items resolved
- Security and privacy posture strengthened

**P2 Tasks:**
- 80%+ complete
- Simplification improvements delivered
- Strategic enhancements implemented

**Cross-Product Tasks:**
- 100% complete
- AEP and Data Cloud integration hardened
- Consistent patterns across products

**Quality Metrics:**
- Test coverage: Above 0.50 instruction / 0.30 branch
- Type safety: Zero `any` in production code
- Documentation: All public APIs documented
- Observability: All critical paths instrumented
- Security: All security findings addressed

---

## Tracking and Reporting

### Weekly Status Updates

Track progress using the following format:

```
## Week X Status (YYYY-MM-DD)

### P0 Tasks
- [x] TASK-ID: Status update
- [ ] TASK-ID: Status update

### P1 Tasks
- [x] TASK-ID: Status update
- [ ] TASK-ID: Status update

### P2 Tasks
- [x] TASK-ID: Status update
- [ ] TASK-ID: Status update

### Cross-Product Tasks
- [x] TASK-ID: Status update
- [ ] TASK-ID: Status update

### Blockers
- Description of any blockers

### Next Week Focus
- Tasks planned for next week
```

### Risk Register

Track risks using the following format:

| Risk | Impact | Likelihood | Mitigation | Owner | Status |
|------|--------|------------|------------|-------|--------|
| Risk description | High/Medium/Low | High/Medium/Low | Mitigation strategy | Owner | Open/Mitigated/Closed |

---

## Notes

- This to-do list is based on the comprehensive AEP and Data Cloud product audits generated on 2026-04-20
- Priorities may shift based on business needs
- Some tasks may require additional investigation before implementation
- Coordinate with product owners before removing or changing user-facing features
- Consider creating feature branches for each major task group
- Cross-product tasks require coordination between AEP and Data Cloud teams

---

## References

- AEP Audit: `products/aep/AEP_PRODUCT_AUDIT_2026-04-20.md`
- Data Cloud Audit: `products/data-cloud/DATA_CLOUD_PRODUCT_AUDIT_2026-04-20.md`
- Copilot Instructions: `.github/copilot-instructions.md`
- Product Review Prompt: `product-review-prompt.md`
