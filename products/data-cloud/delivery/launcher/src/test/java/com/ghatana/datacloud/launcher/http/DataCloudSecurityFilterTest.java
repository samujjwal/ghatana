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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        Principal principal = new Principal("test-service", List.of("admin"), TEST_TENANT);
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
        return getWithTenant(path, apiKey, TEST_TENANT);
    }

    private static HttpRequest getWithTenant(String path, String apiKey, String tenantId) {
        return HttpRequest.get("http://localhost" + path)
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
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

    private static HttpRequest criticalRequest() {
        return HttpRequest.put("http://localhost" + CRITICAL_PATH)
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();
    }

    private static HttpRequest request(String method, String path, String apiKey, String tenantId) {
        HttpRequest.Builder builder;
        switch (method) {
            case "POST" -> builder = HttpRequest.post("http://localhost" + path);
            case "PUT" -> builder = HttpRequest.put("http://localhost" + path);
            case "DELETE" -> builder = HttpRequest.builder(io.activej.http.HttpMethod.DELETE, "http://localhost" + path);
            case "PATCH" -> builder = HttpRequest.builder(io.activej.http.HttpMethod.PATCH, "http://localhost" + path);
            default -> builder = HttpRequest.get("http://localhost" + path);
        }
        return builder
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
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

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo("AUTH_FAILURE");
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

    @Nested
    @DisplayName("RBAC route authorization")
    class RbacTests {

        @Test
        @DisplayName("viewer can read INTERNAL route")
        void viewerCanReadInternalRoute() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("viewer-user", List.of("viewer"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);

            int status = runPromise(() -> secured.serve(get(INTERNAL_PATH)).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("viewer cannot mutate SENSITIVE route")
        void viewerCannotMutateSensitiveRoute() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("viewer-user", List.of("viewer"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
            verify(policyEngine, never()).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("auditor can read governance summary")
        void auditorCanReadGovernanceSummary() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("auditor-user", List.of("auditor"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);

            int status = runPromise(() -> secured.serve(get("/api/v1/governance/compliance/summary")).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("operator cannot execute governance mutation")
        void operatorCannotExecuteGovernanceMutation() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("operator-user", List.of("operator"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost/api/v1/governance/retention/purge")
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
            verify(policyEngine, never()).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("auditor cannot execute governance mutation")
        void auditorCannotExecuteGovernanceMutation() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("auditor-user", List.of("auditor"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost/api/v1/governance/retention/purge")
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
            verify(policyEngine, never()).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("auditor cannot execute governance retention classify mutation")
        void auditorCannotExecuteGovernanceRetentionClassifyMutation() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("auditor-user", List.of("auditor"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost/api/v1/governance/retention/classify")
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
            verify(policyEngine, never()).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("admin can execute governance mutation")
        void adminCanExecuteGovernanceMutation() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost/api/v1/governance/retention/purge")
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
            verify(policyEngine).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("admin can execute governance privacy redact mutation")
        void adminCanExecuteGovernancePrivacyRedactMutation() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.post("http://localhost/api/v1/governance/privacy/redact")
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
            verify(policyEngine).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("mismatched tenant header is denied for API-key principal in enforcing mode")
        void mismatchedTenantHeaderIsDeniedWhenEnforcing() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);

            int status = runPromise(() -> secured
                .serve(getWithTenant("/api/v1/governance/compliance/summary", VALID_API_KEY, "other-tenant"))
                .map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
            verify(policyEngine, never()).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("mismatched tenant header is audit-only warning when enforcing is disabled")
        void mismatchedTenantHeaderPassesWhenAuditOnly() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            AsyncServlet secured = auditOnly().apply(OK_DELEGATE);

            int status = runPromise(() -> secured
                .serve(getWithTenant("/api/v1/governance/compliance/summary", VALID_API_KEY, "other-tenant"))
                .map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @ParameterizedTest(name = "role={0} {2} {1} -> {3}")
        @MethodSource("roleAccessMatrix")
        @DisplayName("role access matrix enforces route-level access without bypass")
        void roleAccessMatrixEnforcesRouteLevelAccess(
                String role,
                String path,
                String method,
                int expectedStatus,
                boolean policyEvaluated) {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                    .thenReturn(Optional.of(new Principal("matrix-user", List.of(role), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);

            int status = runPromise(() -> secured
                    .serve(request(method, path, VALID_API_KEY, TEST_TENANT))
                    .map(HttpResponse::getCode));

            assertThat(status).isEqualTo(expectedStatus);
            if (policyEvaluated) {
                verify(policyEngine).evaluate(anyString(), any());
            } else {
                verify(policyEngine, never()).evaluate(anyString(), any());
            }
        }

        private static Stream<Arguments> roleAccessMatrix() {
            return Stream.of(
                    // P0-04: PROCESSOR and API_CLIENT must not bypass ADMIN operations.
                    Arguments.of("processor", "/api/v1/governance/retention/purge", "POST", 403, false),
                    Arguments.of("api_client", "/api/v1/governance/retention/purge", "POST", 403, false),
                    // PROCESSOR can execute OPERATOR routes, API_CLIENT cannot without explicit OPERATOR role.
                    Arguments.of("processor", SENSITIVE_PATH, "POST", 200, false),
                    Arguments.of("api_client", SENSITIVE_PATH, "POST", 403, false),
                    // PROCESSOR can read viewer routes.
                    Arguments.of("processor", INTERNAL_PATH, "GET", 200, false)
            );
        }

        private static HttpRequest request(String method, String path, String apiKey, String tenantId) {
            HttpRequest.Builder builder;
            switch (method) {
                case "POST" -> builder = HttpRequest.post("http://localhost" + path);
                case "PUT" -> builder = HttpRequest.put("http://localhost" + path);
                case "DELETE" -> builder = HttpRequest.builder(io.activej.http.HttpMethod.DELETE, "http://localhost" + path);
                case "PATCH" -> builder = HttpRequest.builder(io.activej.http.HttpMethod.PATCH, "http://localhost" + path);
                default -> builder = HttpRequest.get("http://localhost" + path);
            }
            return builder
                    .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();
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
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
            verify(auditService).record(any(AuditEvent.class));
        }

        @Test
        @DisplayName("policy DENIES with enforcing=true → returns 403 with POLICY_DENY body")
        void criticalPath_policyDenies_enforcing_returns403() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("policy DENIES enforcing=true → 403 response body contains POLICY_DENY code")
        void criticalPath_policyDenies_enforcing_responseBodyContainsPolicyDenyCode() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            String body = runPromise(() -> secured.serve(req)
                    .then(r -> r.loadBody())
                    .map(bodyBuf -> bodyBuf.asString(java.nio.charset.StandardCharsets.UTF_8)));

            assertThat(body).contains("POLICY_DENY");
        }

        @Test
        @DisplayName("policy DENIES enforcing=true → audit emitted with success=false")
        void criticalPath_policyDenies_emitsFailureAudit() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(auditService).record(any(AuditEvent.class));
        }

        @Test
        @DisplayName("policy DENIES with enforcing=false (audit-only) → passes through with 200")
        void criticalPath_policyDenies_auditOnly_passes() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = auditOnly().apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("policy engine exception → fail-closed, returns 403 in enforcing mode")
        void criticalPath_policyThrows_failsClosed_returns403() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
            .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Policy service unavailable")));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("policy engine exception in audit-only mode → passes through")
        void criticalPath_policyThrows_auditOnly_passes() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
            .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Policy service unavailable")));
            AsyncServlet secured = auditOnly().apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("CRITICAL path policy evaluation uses datacloud.sensitive-route-access policy name")
        void criticalPath_evaluatesCorrectPolicyName() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            verify(policyEngine).evaluate(
                    Mockito.eq("datacloud.sensitive-route-access"), any());
        }

            @Test
            @DisplayName("CRITICAL path policy evaluation uses authenticated tenant context")
            void criticalPath_policyContextUsesAuthenticatedTenant() {
                when(apiKeyResolver.resolve(VALID_API_KEY))
                    .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
                when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
                AsyncServlet secured = enforcing().apply(OK_DELEGATE);
                HttpRequest req = criticalRequest();

                runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

                @SuppressWarnings("unchecked")
                ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
                verify(policyEngine).evaluate(
                    Mockito.eq("datacloud.sensitive-route-access"), contextCaptor.capture());
                assertThat(contextCaptor.getValue()).containsEntry("tenantId", TEST_TENANT);
            }

        @Test
        @DisplayName("breakGlassTenants requires explicit break-glass reason header")
        void criticalPath_breakGlassTenant_requiresReasonHeader() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .breakGlassTenants(Set.of(TEST_TENANT))
                    .build();

            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
            verify(policyEngine, never()).evaluate(anyString(), any());
        }

        @Test
        @DisplayName("breakGlassTenants bypasses policy engine when ADMIN provides reason")
        void criticalPath_breakGlassTenant_allowsWithReasonHeader() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .breakGlassTenants(Set.of(TEST_TENANT))
                    .build();

            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = HttpRequest.put("http://localhost" + CRITICAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.of(DataCloudSecurityFilter.HEADER_BREAK_GLASS_REASON), "Emergency restore")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
            verify(policyEngine, never()).evaluate(anyString(), any());
        }

        @ParameterizedTest(name = "{index}: {1} {0} -> policyEvaluated={2}")
        @MethodSource("requiresPolicyMatrix")
        @DisplayName("requiresPolicy metadata controls policy evaluation")
        void requiresPolicyMetadataControlsPolicyEvaluation(String path, String method, boolean policyEvaluated) {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .deploymentProfile("production")
                    .build();

            int status = runPromise(() -> filter.apply(OK_DELEGATE)
                    .serve(request(method, path, VALID_API_KEY, TEST_TENANT))
                    .map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
            if (policyEvaluated) {
                verify(policyEngine).evaluate(anyString(), any());
            } else {
                verify(policyEngine, never()).evaluate(anyString(), any());
            }
        }

        @ParameterizedTest(name = "{index}: {1} {0} -> blocksOnAuditFailure={2}")
        @MethodSource("requiresBlockingAuditMatrix")
        @DisplayName("requiresBlockingAudit metadata controls fail-closed audit behavior")
        void requiresBlockingAuditControlsAuditFailureBehavior(String path, String method, boolean blocksOnAuditFailure) {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
            when(auditService.record(any(AuditEvent.class)))
                .thenReturn(Promise.ofException(new RuntimeException("audit sink unavailable")));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .deploymentProfile("production")
                    .build();

            if (blocksOnAuditFailure) {
                assertThatThrownBy(() -> runPromise(() -> filter.apply(OK_DELEGATE)
                        .serve(request(method, path, VALID_API_KEY, TEST_TENANT))
                        .map(HttpResponse::getCode)))
                        .isInstanceOf(RuntimeException.class);
            } else {
                assertThatCode(() -> runPromise(() -> filter.apply(OK_DELEGATE)
                        .serve(request(method, path, VALID_API_KEY, TEST_TENANT))
                        .map(HttpResponse::getCode)))
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("missing metadata fails closed in production-like profile")
        void missingMetadataFailsClosedInProductionProfile() {
            when(apiKeyResolver.resolve(VALID_API_KEY))
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .deploymentProfile("production")
                    .build();

            HttpResponse response = runPromise(() -> filter.apply(OK_DELEGATE)
                    .serve(request("GET", "/api/v1/unregistered/route", VALID_API_KEY, TEST_TENANT)));

            assertThat(response.getCode()).isEqualTo(500);
        }

        private static Stream<Arguments> requiresPolicyMatrix() {
            return Stream.of(
                    Arguments.of("/api/v1/action/autonomy/feedback-policy", "POST", true),
                    Arguments.of("/api/v1/action/learning/trigger", "POST", false),
                    Arguments.of("/api/v1/action/pipelines/pipeline-1/execute", "POST", true),
                    Arguments.of("/api/v1/collections/collection-1/migrate", "POST", true),
                    Arguments.of("/api/v1/entities/orders/export", "POST", true),
                    Arguments.of("/mcp/v1/tools", "POST", true),
                    Arguments.of("/api/v1/plugins/plugin-1/upgrade", "POST", true),
                    // Compatibility alias route should still honor requiresPolicy metadata.
                    Arguments.of("/api/v1/executions/exec-1/cancel", "POST", true),
                    Arguments.of("/api/v1/plugins/plugin-1/disable", "POST", true),
                    Arguments.of("/api/v1/executions/exec-1/checkpoint", "POST", false)
            );
        }

        private static Stream<Arguments> requiresBlockingAuditMatrix() {
            return Stream.of(
                    Arguments.of("/api/v1/action/autonomy/feedback-policy", "POST", true),
                    Arguments.of("/api/v1/action/learning/trigger", "POST", false),
                    // Compatibility alias route should still honor blocking-audit metadata.
                    Arguments.of("/api/v1/executions/exec-1/cancel", "POST", true),
                    Arguments.of("/api/v1/executions/exec-1/checkpoint", "POST", false)
            );
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
        @DisplayName("null PolicyEngine fails-closed on CRITICAL path when enforcing")
        void nullPolicyEngine_criticalPath_failsClosed() {
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(null)    // no policy engine
                    .auditService(auditService)
                    .enforcing(true)
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("null PolicyEngine allows CRITICAL path in audit-only mode")
        void nullPolicyEngine_criticalPath_auditOnly_passesThrough() {
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(null)    // no policy engine
                    .auditService(auditService)
                    .enforcing(false) // audit-only
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

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
    @DisplayName("Global tenant enforcement")
    class GlobalTenantEnforcementTests {

        @Test
        @DisplayName("principal without tenant returns 400 when enforcing=true")
        void missingTenantPrincipal_returnsBadRequest_whenEnforcing() {
            // A principal whose tenantId is blank (e.g., empty-string claim in JWT)
            // must be rejected at the filter boundary so handlers never see a request
            // with no tenant context.
            Principal noTenantPrincipal = new Principal("svc-admin", List.of("admin"), "");
            ApiKeyResolver noTenantResolver = mock(ApiKeyResolver.class);
            when(noTenantResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(noTenantPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(noTenantResolver)
                    .enforcing(true)
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = getWithKey(INTERNAL_PATH, VALID_API_KEY);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(400);
        }

        @Test
        @DisplayName("principal without tenant is allowed through when not enforcing (audit-only)")
        void missingTenantPrincipal_allowed_whenNotEnforcing() {
            Principal noTenantPrincipal = new Principal("svc-admin", List.of("admin"), "");
            ApiKeyResolver noTenantResolver = mock(ApiKeyResolver.class);
            when(noTenantResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(noTenantPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(noTenantResolver)
                    .enforcing(false)
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = getWithKey(INTERNAL_PATH, VALID_API_KEY);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("principal with valid tenant passes through the global tenant check")
        void principalWithTenant_passesGlobalCheck_whenEnforcing() {
            AsyncServlet secured = enforcing().apply(OK_DELEGATE);
            HttpRequest req = getWithKey(INTERNAL_PATH, VALID_API_KEY);

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200);
        }
    }

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
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("breakGlassTenants defaults to empty set")
        void defaultBreakGlassTenants_isEmpty() {
            // With no excluded tenants override and policy denying, should get 403
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .enforcing(true)
                    .build();
            AsyncServlet secured = filter.apply(OK_DELEGATE);
            HttpRequest req = criticalRequest();

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(403);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. PRODUCTION PROFILE TENANT ENFORCEMENT (DC-P0-04)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Production profile tenant enforcement")
    class ProductionProfileTenantTests {

        @Test
        @DisplayName("production profile requires strictTenantResolution=true")
        void productionProfile_requiresStrictTenantResolution() {
            // DC-P0-04: In production profile, strict tenant resolution must be enabled
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(false)  // Violation: should be true in production
                    .build();

            // validateProductionRequirements should throw IllegalStateException
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                filter.validateProductionRequirements("production")
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("strictTenantResolution must be true");
        }

        @Test
        @DisplayName("production profile rejects tenant from query parameter spoofing")
        void productionProfile_rejectsTenantQuerySpoofing() {
            // DC-P0-04: Tenant must come from authenticated identity, not query parameter
            Principal principal = new Principal("test-service", List.of("admin"), TEST_TENANT);
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            // Try to spoof tenant via query parameter
            HttpRequest req = HttpRequest.get("http://localhost" + INTERNAL_PATH + "?tenantId=spoofed-tenant")
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // In strict mode, tenant hints that do not match authenticated identity are rejected.
            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("production profile rejects tenant from header when mismatched with principal")
        void productionProfile_rejectsMismatchedTenantHeader() {
            // DC-P0-04: X-Tenant-ID header must match authenticated tenant in production
            Principal principal = new Principal("test-service", List.of("admin"), TEST_TENANT);
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            // Send mismatched tenant header
            HttpRequest req = HttpRequest.get("http://localhost" + INTERNAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), "different-tenant")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("production profile allows matching tenant header as compatibility hint")
        void productionProfile_allowsMatchingTenantHeader() {
            // DC-P0-04: X-Tenant-ID header is allowed as compatibility hint if it matches
            Principal principal = new Principal("test-service", List.of("admin"), TEST_TENANT);
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            // Send matching tenant header
            HttpRequest req = HttpRequest.get("http://localhost" + INTERNAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("production profile requires audit service")
        void productionProfile_requiresAuditService() {
            // DC-P0-04: Audit service must be configured in production
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(null)  // Violation: audit service required in production
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                filter.validateProductionRequirements("production")
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("AuditService");
        }

        @Test
        @DisplayName("production profile requires policy engine")
        void productionProfile_requiresPolicyEngine() {
            // DC-P0-04: Policy engine must be configured in production
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(null)  // Violation: policy engine required in production
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                filter.validateProductionRequirements("production")
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("PolicyEngine");
        }

        @Test
        @DisplayName("API key principal tenant mismatch is rejected in production")
        void productionProfile_rejectsApiKeyTenantMismatch() {
            // DC-P0-04: API key tenant must match request context
            Principal principal = new Principal("test-service", List.of("admin"), "api-key-tenant");
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            // Send request with different tenant in header
            HttpRequest req = HttpRequest.get("http://localhost" + INTERNAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), "header-tenant")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(403);
        }

        @Test
        @DisplayName("delegated access scenario - support tenant can access with proper claims")
        void productionProfile_allowsDelegatedAccessWithClaims() {
            // DC-P0-04: Delegated access (e.g., support team) requires proper claims
            // Simulate a support principal with delegated access claim
            Principal supportPrincipal = new Principal("support-user", List.of("support", "delegated_access", "operator"), TEST_TENANT);
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(supportPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

                // Use the actual SENSITIVE route signature (POST /api/v1/voice/intent).
                HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(200);
            verify(auditService).record(any(AuditEvent.class));  // Audit should be emitted for delegated access
        }

        @Test
        @DisplayName("missing tenant claim in JWT returns 400 in production")
        void productionProfile_missingTenantClaimReturns400() {
            // DC-P0-04: JWT without tenant_id claim should be rejected
            Principal noTenantPrincipal = new Principal("svc-user", List.of("service"), "");
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(noTenantPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            HttpRequest req = getWithKey(INTERNAL_PATH, VALID_API_KEY);

            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(400);
        }

        @Test
        @DisplayName("staging profile enforces same tenant rules as production")
        void stagingProfile_enforcesSameTenantRules() {
            // DC-P0-04: Staging profile should enforce same rules as production
            Principal principal = new Principal("test-service", List.of("admin"), TEST_TENANT);
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            // Staging should also require strict tenant resolution
            org.assertj.core.api.Assertions.assertThatCode(() ->
                filter.validateProductionRequirements("staging")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sovereign profile enforces same tenant rules as production")
        void sovereignProfile_enforcesSameTenantRules() {
            // DC-P0-04: Sovereign profile should enforce same rules as production
            Principal principal = new Principal("test-service", List.of("admin"), TEST_TENANT);
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .policyEngine(policyEngine)
                    .auditService(auditService)
                    .enforcing(true)
                    .strictTenantResolution(true)
                    .build();

            // Sovereign should also require strict tenant resolution
            org.assertj.core.api.Assertions.assertThatCode(() ->
                filter.validateProductionRequirements("sovereign")
            ).doesNotThrowAnyException();
        }
    }

    // ==================== Canonical Permission Tests (P2-02) ====================

    @Nested
    @DisplayName("P2-02: Canonical Permission Enforcement")
    class CanonicalPermissionTests {

        @Test
        @DisplayName("ADMIN role derives all canonical permissions")
        void adminRoleDerivesAllPermissions() {
            // Given: Principal with ADMIN role (has all permissions)
            Principal adminPrincipal = new Principal(
                "admin-user",
                List.of("ADMIN"),
                TEST_TENANT,
                Set.of("datacloud:admin", "media:artifact:create", "action:pipeline:execute")
            );
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(adminPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .auditService(auditService)
                    .enforcing(true)
                    .build();

            // When: Request to CRITICAL path
            HttpRequest req = HttpRequest.post("http://localhost" + CRITICAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // Then: Access granted (ADMIN has all permissions)
            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("VIEWER role denied access to critical paths")
        void viewerRoleDeniedCriticalAccess() {
            // Given: Principal with VIEWER role (limited permissions)
            Principal viewerPrincipal = new Principal(
                "viewer-user",
                List.of("VIEWER"),
                TEST_TENANT,
                Set.of("datacloud:read", "surface:read")
            );
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(viewerPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .auditService(auditService)
                    .enforcing(true)
                    .build();

            // When: Request to CRITICAL path
            HttpRequest req = HttpRequest.post("http://localhost" + CRITICAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // Then: Access denied (VIEWER lacks required permissions)
            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(403);

            // Verify permission denied audit was emitted
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assertThat(auditEvent.eventType()).isEqualTo("PERMISSION_DENIED");
        }

        @Test
        @DisplayName("OPERATOR role has execute permissions")
        void operatorRoleHasExecutePermissions() {
            // Given: Principal with OPERATOR role
            Principal operatorPrincipal = new Principal(
                "operator-user",
                List.of("OPERATOR"),
                TEST_TENANT,
                Set.of("datacloud:read", "datacloud:write", "action:pipeline:execute", "media:artifact:process")
            );
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(operatorPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .auditService(auditService)
                    .enforcing(true)
                    .build();

            // When: Request to SENSITIVE path (requires OPERATOR level)
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // Then: Access granted
            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("Permissions derived from role mapping, not request headers")
        void permissionsDerivedFromRolesNotHeaders() {
            // Given: Principal with VIEWER role (limited permissions)
            Principal viewerPrincipal = new Principal(
                "viewer-user",
                List.of("VIEWER"),
                TEST_TENANT,
                Set.of("datacloud:read")
            );
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(viewerPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .auditService(auditService)
                    .enforcing(true)
                    .build();

            // When: Request attempts to spoof admin permission via header
            HttpRequest req = HttpRequest.get("http://localhost" + INTERNAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Permissions"), "datacloud:admin,connector:sync")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // Then: Access granted to INTERNAL path (VIEWER has read), but spoofed header ignored
            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("Permission denied response includes missing permission details")
        void permissionDeniedResponseIncludesDetails() {
            // Given: Principal with VIEWER role
            Principal viewerPrincipal = new Principal(
                "viewer-user",
                List.of("VIEWER"),
                TEST_TENANT,
                Set.of("datacloud:read")
            );
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(viewerPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .auditService(auditService)
                    .enforcing(true)
                    .build();

            // When: Request to CRITICAL path (requires admin permission)
            HttpRequest req = HttpRequest.post("http://localhost" + CRITICAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // Then: 403 with permission denied details
            HttpResponse response = runPromise(() -> filter.apply(OK_DELEGATE).serve(req));
            assertThat(response.getCode()).isEqualTo(403);

            // Verify audit includes permission context
            ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditService).record(auditCaptor.capture());
            AuditEvent auditEvent = auditCaptor.getValue();
            assertThat(auditEvent.eventType()).isEqualTo("PERMISSION_DENIED");
            assertThat(auditEvent.principal()).isEqualTo("viewer-user");
        }

        @Test
        @DisplayName("AUDITOR role has audit read permissions only")
        void auditorRoleHasAuditReadPermissions() {
            // Given: Principal with AUDITOR role
            Principal auditorPrincipal = new Principal(
                "auditor-user",
                List.of("AUDITOR"),
                TEST_TENANT,
                Set.of("datacloud:read", "datacloud:audit", "governance:read", "governance:compliance:read")
            );
            when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(auditorPrincipal));

            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
                    .apiKeyResolver(apiKeyResolver)
                    .auditService(auditService)
                    .enforcing(true)
                    .build();

            // When: Request to INTERNAL path (read access)
            HttpRequest req = HttpRequest.get("http://localhost" + INTERNAL_PATH)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .build();

            // Then: Access granted (AUDITOR has datacloud:read)
            int status = runPromise(() -> filter.apply(OK_DELEGATE).serve(req).map(HttpResponse::getCode));
            assertThat(status).isEqualTo(200);
        }
    }
}
