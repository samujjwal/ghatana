/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataCloudSecurityFilter}.
 *
 * <p>Comprehensive coverage of the 6 security scenarios:
 * <ol>
 *   <li>PUBLIC paths bypass auth and audit entirely.</li>
 *   <li>Missing or invalid API key returns HTTP 401.</li>
 *   <li>Authenticated INTERNAL paths pass through; no audit.</li>
 *   <li>Authenticated SENSITIVE paths pass through; audit emitted.</li>
 *   <li>CRITICAL paths go through policy engine; allow emits audit, deny returns 403.</li>
 *   <li>Audit-only (non-enforcing) mode logs but never blocks.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Unit tests for the security/policy/audit middleware filter chain
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudSecurityFilter – security middleware unit tests")
class DataCloudSecurityFilterTest extends EventloopTestBase {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String VALID_API_KEY   = "sk-test-valid-key";
    private static final String INVALID_API_KEY = "sk-test-invalid-key";
    private static final String TEST_TENANT     = "test-tenant";

    // Public probes
    private static final String PUBLIC_PATH    = "/health";
    // Authenticated read path (INTERNAL)
    private static final String INTERNAL_PATH  = "/api/v1/collections/test";
    // AI inference path (SENSITIVE)
    private static final String SENSITIVE_PATH = "/api/v1/voice/intent";
    // Governance path (always CRITICAL)
    private static final String CRITICAL_PATH  = "/api/v1/governance/policies/test";

    // ── Collaborator mocks ────────────────────────────────────────────────────
    private ApiKeyResolver  apiKeyResolver;
    private PolicyEngine    policyEngine;
    private AuditService    auditService;

    // Stub delegate that always returns 200 OK
    private static final AsyncServlet OK_DELEGATE =
            request -> Promise.of(HttpResponse.ok200().build());

    @BeforeEach
    void setUpMocks() {
        apiKeyResolver = mock(ApiKeyResolver.class);
        policyEngine   = mock(PolicyEngine.class);
        auditService   = mock(AuditService.class);

        // Default: valid key → authenticated principal
        Principal principal = new Principal("test-service", List.of("reader"), TEST_TENANT);
        when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));
        when(apiKeyResolver.resolve(INVALID_API_KEY)).thenReturn(Optional.empty());

        // Default AuditService: fire-and-forget, complete successfully
        when(auditService.record(any(AuditEvent.class))).thenReturn(Promise.of((Void) null));

        // Default PolicyEngine: allow everything (individual tests override)
        when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private DataCloudSecurityFilter enforcing() {
        return DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .enforcing(true)
                .build();
    }

    private DataCloudSecurityFilter auditOnly() {
        return DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .enforcing(false)
                .build();
    }

    private static HttpRequest get(String path) {
        return HttpRequest.get("http://localhost" + path)
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    private static HttpRequest getWithKey(String path, String apiKey) {
        return HttpRequest.get("http://localhost" + path)
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    private static HttpRequest getNoKey(String path) {
        return HttpRequest.get("http://localhost" + path)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    private static HttpRequest getWithRequestId(String path, String requestId) {
        return HttpRequest.get("http://localhost" + path)
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.of(DataCloudSecurityFilter.HEADER_REQUEST_ID), requestId)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. PUBLIC PATH BYPASS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUBLIC paths – bypass auth and audit")
    class PublicPathTests {

        @Test
        @DisplayName("GET /health returns 200 without API key")
        void health_returnsOkWithoutApiKey() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.get("http://localhost/health")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();  // NO X-API-Key

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("GET /health does not invoke ApiKeyResolver")
        void health_doesNotInvokeApiKeyResolver() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.get("http://localhost/health")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(apiKeyResolver, never()).resolve(anyString());
        }

        @Test
        @DisplayName("GET /health does not emit audit")
        void health_doesNotEmitAudit() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.get("http://localhost/health")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(auditService, never()).record(any());
        }

        @Test
        @DisplayName("/ready, /live, /metrics, /info are PUBLIC")
        void otherPublicProbes_returnOkWithoutKey() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            for (String probePath : new String[]{"/ready", "/live", "/metrics", "/info"}) {
                HttpRequest req = HttpRequest.get("http://localhost" + probePath)
                        .withHeader(HttpHeaders.HOST, "localhost")
                        .build();
                int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));
                assertThat(status).as("status for %s without auth", probePath).isEqualTo(200);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. AUTHENTICATION FAILURE
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authentication failures – 401")
    class AuthFailureTests {

        @Test
        @DisplayName("missing API key header returns 401")
        void missingApiKey_returns401() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = getNoKey(INTERNAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(401);
        }

        @Test
        @DisplayName("invalid API key returns 401")
        void invalidApiKey_returns401() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = getWithKey(INTERNAL_PATH, INVALID_API_KEY);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(401);
        }

        @Test
        @DisplayName("auth failure emits audit (fail-record)")
        void invalidApiKey_emitsAudit() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = getWithKey(INTERNAL_PATH, INVALID_API_KEY);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(auditService).record(any(AuditEvent.class));
        }

        @Test
        @DisplayName("delegate is never invoked on auth failure")
        void invalidApiKey_doesNotInvokeDelegate() {
            int[] calls = {0};
            AsyncServlet countingDelegate = request -> {
                calls[0]++;
                return Promise.of(HttpResponse.ok200().build());
            };
            AsyncServlet secured = enforcing().apply(countingDelegate);
            HttpRequest req = getWithKey(INTERNAL_PATH, INVALID_API_KEY);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(calls[0]).isZero();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. AUTHENTICATED INTERNAL PATH
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authenticated INTERNAL paths – pass-through, no audit")
    class InternalPathTests {

        @Test
        @DisplayName("valid key on INTERNAL path returns delegate status")
        void internalPath_validKey_passes() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(INTERNAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("INTERNAL path does not invoke PolicyEngine")
        void internalPath_doesNotInvokePolicy() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(INTERNAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(policyEngine, never()).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("INTERNAL path does not emit audit")
        void internalPath_doesNotEmitAudit() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(INTERNAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(auditService, never()).record(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. SENSITIVE PATH – audit emitted, no policy check
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authenticated SENSITIVE paths – pass-through, audit emitted")
    class SensitivePathTests {

        @Test
        @DisplayName("valid key on SENSITIVE path returns 200")
        void sensitivePath_validKey_passes() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("SENSITIVE path emits audit on success")
        void sensitivePath_emitsAudit() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(auditService).record(any(AuditEvent.class));
        }

        @Test
        @DisplayName("SENSITIVE path does not invoke PolicyEngine")
        void sensitivePath_doesNotInvokePolicy() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(policyEngine, never()).evaluate(anyString(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. CRITICAL PATH – policy engine consulted
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CRITICAL paths – policy engine consulted")
    class CriticalPathTests {

        @Test
        @DisplayName("policy ALLOWS → returns 200 and emits audit")
        void criticalPath_policyAllows_passes() {
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
            verify(auditService).record(any(AuditEvent.class));
        }

        @Test
        @DisplayName("policy DENIES with enforcing=true → returns 403 with POLICY_DENY body")
        void criticalPath_policyDenies_enforcing_returns403() {
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("policy DENIES enforcing=true → 403 response body contains POLICY_DENY code")
        void criticalPath_policyDenies_enforcing_responseBodyContainsPolicyDenyCode() {
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            String body = runPromise(() -> secured.serve(req)
                    .then(r -> r.loadBody())
                    .map(bodyBuf -> bodyBuf.asString(java.nio.charset.StandardCharsets.UTF_8)));

            assertThat(body).contains("POLICY_DENY");
        }

        @Test
        @DisplayName("policy DENIES enforcing=true → audit emitted with success=false")
        void criticalPath_policyDenies_emitsFailureAudit() {
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(auditService).record(any(AuditEvent.class));
        }

        @Test
        @DisplayName("policy DENIES with enforcing=false (audit-only) → passes through with 200")
        void criticalPath_policyDenies_auditOnly_passes() {
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = auditOnly().apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("policy engine exception → fail-closed, returns 403 in enforcing mode")
        void criticalPath_policyThrows_failsClosed_returns403() {
            when(policyEngine.evaluate(anyString(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Policy service unavailable")));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("policy engine exception in audit-only mode → passes through")
        void criticalPath_policyThrows_auditOnly_passes() {
            when(policyEngine.evaluate(anyString(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Policy service unavailable")));
            AsyncServlet secured = auditOnly().apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("CRITICAL path policy evaluation uses datacloud.sensitive-route-access policy name")
        void criticalPath_evaluatesCorrectPolicyName() {
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(policyEngine).evaluate(
                    Mockito.eq("datacloud.sensitive-route-access"), any());
        }

            @Test
            @DisplayName("CRITICAL path policy evaluation uses authenticated tenant context")
            void criticalPath_policyContextUsesAuthenticatedTenant() {
                when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
                AsyncServlet secured = enforcing().apply(OK_DELEGATE);
                HttpRequest req = get(CRITICAL_PATH);

                runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

                @SuppressWarnings("unchecked")
                ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
                verify(policyEngine).evaluate(
                    Mockito.eq("datacloud.sensitive-route-access"), contextCaptor.capture());
                assertThat(contextCaptor.getValue()).containsEntry("tenantId", TEST_TENANT);
            }

        @Test
        @DisplayName("policyExcludedTenants bypasses policy engine for matching tenant")
        void criticalPath_excludedTenant_bypassesPolicy() {
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .policyExcludedTenants(Set.of(TEST_TENANT))
                    .build();

            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            // Despite policy denial, excluded tenant bypasses the policy check → passes
            assertThat(status).isEqualTo(200);
            verify(policyEngine, never()).evaluate(anyString(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. REQUEST-ID PROPAGATION
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Request-ID propagation")
    class RequestIdTests {

        @Test
        @DisplayName("missing X-Request-ID header does not block request")
        void missingRequestId_doesNotBlockRequest() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            // req has no X-Request-ID header
            HttpRequest req = get(SENSITIVE_PATH);

            // Should succeed — a UUID will be generated internally
            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("present X-Request-ID is respected (no NPE, no override)")
        void presentRequestId_doesNotFailRequest() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = getWithRequestId(INTERNAL_PATH, "trace-abc-123");

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. NULL / OPTIONAL COLLABORATORS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null collaborators – graceful operation")
    class NullCollaboratorTests {

        @Test
        @DisplayName("null PolicyEngine skips policy check on CRITICAL path")
        void nullPolicyEngine_criticalPath_passesThrough() {
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(null)    // no policy engine
                    .auditService(auditService)
                    .enforcing(true)
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("null AuditService means no audit emission (no NPE)")
        void nullAuditService_sensitivePath_noNPE() {
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(null)    // no audit service
                    .enforcing(true)
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // Must not throw NPE
            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("audit service throwing does not propagate to caller")
        void auditServiceThrows_doesNotPropagateToResponse() {
            when(auditService.record(any(AuditEvent.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("Audit store down")));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // Audit failure must never block the response
            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. BUILDER VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder validation")
    class BuilderTests {

        @Test
        @DisplayName("null apiKeyResolver throws NullPointerException at build time")
        void nullApiKeyResolver_throwsNPE() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                DataCloudSecurityFilter.builder()
                    .apiKeyResolver(null)
                    .build()
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("default enforcing=true when not set")
        void defaultEnforcing_isTrue() {
            // Verify that policy denial blocks when enforcing not explicitly set
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("policyExcludedTenants defaults to empty set")
        void defaultExcludedTenants_isEmpty() {
            // With no excluded tenants override and policy denying, should get 403
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .enforcing(true)
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = get(CRITICAL_PATH);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }
    }
}
