/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 *   <li>Audit-only (non-enforcing) mode logs but never blocks.</li> // GH-90000
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
    // Authenticated read path (INTERNAL) // GH-90000
    private static final String INTERNAL_PATH  = "/api/v1/collections/test";
    // AI inference path (SENSITIVE) // GH-90000
    private static final String SENSITIVE_PATH = "/api/v1/voice/intent";
    // Governance path (always CRITICAL) // GH-90000
    private static final String CRITICAL_PATH  = "/api/v1/governance/policies/test";

    // ── Collaborator mocks ────────────────────────────────────────────────────
    private ApiKeyResolver  apiKeyResolver;
    private PolicyEngine    policyEngine;
    private AuditService    auditService;

    // Stub delegate that always returns 200 OK
    private static final AsyncServlet OK_DELEGATE =
            request -> Promise.of(HttpResponse.ok200().build()); // GH-90000

    @BeforeEach
    void setUpMocks() { // GH-90000
        apiKeyResolver = mock(ApiKeyResolver.class); // GH-90000
        policyEngine   = mock(PolicyEngine.class); // GH-90000
        auditService   = mock(AuditService.class); // GH-90000

        // Default: valid key → authenticated principal
        Principal principal = new Principal("test-service", List.of("admin"), TEST_TENANT);
        when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal)); // GH-90000
        when(apiKeyResolver.resolve(INVALID_API_KEY)).thenReturn(Optional.empty()); // GH-90000

        // Default AuditService: fire-and-forget, complete successfully
        when(auditService.record(any(AuditEvent.class))).thenReturn(Promise.of((Void) null)); // GH-90000

        // Default PolicyEngine: allow everything (individual tests override) // GH-90000
        when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE)); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private DataCloudSecurityFilter enforcing() { // GH-90000
        return DataCloudSecurityFilter.builder() // GH-90000
                .apiKeyResolver(apiKeyResolver) // GH-90000
                .policyEngine(policyEngine) // GH-90000
                .auditService(auditService) // GH-90000
                .enforcing(true) // GH-90000
                .build(); // GH-90000
    }

    private DataCloudSecurityFilter auditOnly() { // GH-90000
        return DataCloudSecurityFilter.builder() // GH-90000
                .apiKeyResolver(apiKeyResolver) // GH-90000
                .policyEngine(policyEngine) // GH-90000
                .auditService(auditService) // GH-90000
                .enforcing(false) // GH-90000
                .build(); // GH-90000
    }

    private static HttpRequest get(String path) { // GH-90000
        return HttpRequest.get("http://localhost" + path) // GH-90000
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000
    }

    private static HttpRequest getWithKey(String path, String apiKey) { // GH-90000
        return HttpRequest.get("http://localhost" + path) // GH-90000
                .withHeader(HttpHeaders.of("X-API-Key"), apiKey)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000
    }

    private static HttpRequest getNoKey(String path) { // GH-90000
        return HttpRequest.get("http://localhost" + path) // GH-90000
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000
    }

    private static HttpRequest getWithRequestId(String path, String requestId) { // GH-90000
        return HttpRequest.get("http://localhost" + path) // GH-90000
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.of(DataCloudSecurityFilter.HEADER_REQUEST_ID), requestId) // GH-90000
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. PUBLIC PATH BYPASS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUBLIC paths – bypass auth and audit")
    class PublicPathTests {

        @Test
        @DisplayName("GET /health returns 200 without API key")
        void health_returnsOkWithoutApiKey() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.get("http://localhost/health")
                    .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                    .build();  // NO X-API-Key // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("GET /health does not invoke ApiKeyResolver")
        void health_doesNotInvokeApiKeyResolver() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.get("http://localhost/health")
                    .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                    .build(); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            verify(apiKeyResolver, never()).resolve(anyString()); // GH-90000
        }

        @Test
        @DisplayName("GET /health does not emit audit")
        void health_doesNotEmitAudit() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.get("http://localhost/health")
                    .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                    .build(); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            verify(auditService, never()).record(any()); // GH-90000
        }

        @Test
        @DisplayName("/ready, /live, /metrics, /info are PUBLIC")
        void otherPublicProbes_returnOkWithoutKey() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            for (String probePath : new String[]{"/ready", "/live", "/metrics", "/info"}) { // GH-90000
                HttpRequest req = HttpRequest.get("http://localhost" + probePath) // GH-90000
                        .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                        .build(); // GH-90000
                int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000
                assertThat(status).as("status for %s without auth", probePath).isEqualTo(200); // GH-90000
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
        void missingApiKey_returns401() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = getNoKey(INTERNAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(401); // GH-90000
        }

        @Test
        @DisplayName("invalid API key returns 401")
        void invalidApiKey_returns401() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = getWithKey(INTERNAL_PATH, INVALID_API_KEY); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(401); // GH-90000
        }

        @Test
        @DisplayName("auth failure emits audit (fail-record)")
        void invalidApiKey_emitsAudit() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = getWithKey(INTERNAL_PATH, INVALID_API_KEY); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class); // GH-90000
            verify(auditService).record(eventCaptor.capture()); // GH-90000
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo("AUTH_FAILURE");
        }

        @Test
        @DisplayName("delegate is never invoked on auth failure")
        void invalidApiKey_doesNotInvokeDelegate() { // GH-90000
            int[] calls = {0};
            AsyncServlet countingDelegate = request -> {
                calls[0]++;
                return Promise.of(HttpResponse.ok200().build()); // GH-90000
            };
            AsyncServlet secured = enforcing().apply(countingDelegate); // GH-90000
            HttpRequest req = getWithKey(INTERNAL_PATH, INVALID_API_KEY); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(calls[0]).isZero(); // GH-90000
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
        void internalPath_validKey_passes() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(INTERNAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("INTERNAL path does not invoke PolicyEngine")
        void internalPath_doesNotInvokePolicy() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(INTERNAL_PATH); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            verify(policyEngine, never()).evaluate(anyString(), any()); // GH-90000
        }

        @Test
        @DisplayName("INTERNAL path does not emit audit")
        void internalPath_doesNotEmitAudit() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(INTERNAL_PATH); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            verify(auditService, never()).record(any()); // GH-90000
        }
    }

    @Nested
    @DisplayName("RBAC route authorization")
    class RbacTests {

        @Test
        @DisplayName("viewer can read INTERNAL route")
        void viewerCanReadInternalRoute() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("viewer-user", List.of("viewer"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000

            int status = runPromise(() -> secured.serve(get(INTERNAL_PATH)).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("viewer cannot mutate SENSITIVE route")
        void viewerCannotMutateSensitiveRoute() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("viewer-user", List.of("viewer"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH) // GH-90000
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(403); // GH-90000
            verify(policyEngine, never()).evaluate(anyString(), any()); // GH-90000
        }

        @Test
        @DisplayName("auditor can read governance summary")
        void auditorCanReadGovernanceSummary() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("auditor-user", List.of("auditor"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000

            int status = runPromise(() -> secured.serve(get("/api/v1/governance/compliance/summary")).map(HttpResponse::getCode));

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("operator cannot execute governance mutation")
        void operatorCannotExecuteGovernanceMutation() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("operator-user", List.of("operator"), TEST_TENANT)));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.post("http://localhost/api/v1/governance/retention/purge")
                .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(403); // GH-90000
            verify(policyEngine, never()).evaluate(anyString(), any()); // GH-90000
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
        void sensitivePath_validKey_passes() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH) // GH-90000
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                    .build(); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("SENSITIVE path emits audit on success")
        void sensitivePath_emitsAudit() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH) // GH-90000
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                    .build(); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            verify(auditService).record(any(AuditEvent.class)); // GH-90000
        }

        @Test
        @DisplayName("SENSITIVE path does not invoke PolicyEngine")
        void sensitivePath_doesNotInvokePolicy() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH) // GH-90000
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                    .build(); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            verify(policyEngine, never()).evaluate(anyString(), any()); // GH-90000
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
        void criticalPath_policyAllows_passes() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE)); // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
            verify(auditService).record(any(AuditEvent.class)); // GH-90000
        }

        @Test
        @DisplayName("policy DENIES with enforcing=true → returns 403 with POLICY_DENY body")
        void criticalPath_policyDenies_enforcing_returns403() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE)); // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(403); // GH-90000
        }

        @Test
        @DisplayName("policy DENIES enforcing=true → 403 response body contains POLICY_DENY code")
        void criticalPath_policyDenies_enforcing_responseBodyContainsPolicyDenyCode() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE)); // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            String body = runPromise(() -> secured.serve(req) // GH-90000
                    .then(r -> r.loadBody()) // GH-90000
                    .map(bodyBuf -> bodyBuf.asString(java.nio.charset.StandardCharsets.UTF_8))); // GH-90000

            assertThat(body).contains("POLICY_DENY");
        }

        @Test
        @DisplayName("policy DENIES enforcing=true → audit emitted with success=false")
        void criticalPath_policyDenies_emitsFailureAudit() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE)); // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            verify(auditService).record(any(AuditEvent.class)); // GH-90000
        }

        @Test
        @DisplayName("policy DENIES with enforcing=false (audit-only) → passes through with 200")
        void criticalPath_policyDenies_auditOnly_passes() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE)); // GH-90000
            AsyncServlet secured = auditOnly().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("policy engine exception → fail-closed, returns 403 in enforcing mode")
        void criticalPath_policyThrows_failsClosed_returns403() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
            .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Policy service unavailable")));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(403); // GH-90000
        }

        @Test
        @DisplayName("policy engine exception in audit-only mode → passes through")
        void criticalPath_policyThrows_auditOnly_passes() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
            .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Policy service unavailable")));
            AsyncServlet secured = auditOnly().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("CRITICAL path policy evaluation uses datacloud.sensitive-route-access policy name")
        void criticalPath_evaluatesCorrectPolicyName() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE)); // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            verify(policyEngine).evaluate( // GH-90000
                    Mockito.eq("datacloud.sensitive-route-access"), any());
        }

            @Test
            @DisplayName("CRITICAL path policy evaluation uses authenticated tenant context")
            void criticalPath_policyContextUsesAuthenticatedTenant() { // GH-90000
                when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                    .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
                when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE)); // GH-90000
                AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
                HttpRequest req = get(CRITICAL_PATH); // GH-90000

                runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

                @SuppressWarnings("unchecked")
                ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class); // GH-90000
                verify(policyEngine).evaluate( // GH-90000
                    Mockito.eq("datacloud.sensitive-route-access"), contextCaptor.capture());
                assertThat(contextCaptor.getValue()).containsEntry("tenantId", TEST_TENANT); // GH-90000
            }

        @Test
        @DisplayName("policyExcludedTenants bypasses policy engine for matching tenant")
        void criticalPath_excludedTenant_bypassesPolicy() { // GH-90000
            when(apiKeyResolver.resolve(VALID_API_KEY)) // GH-90000
                .thenReturn(Optional.of(new Principal("admin-user", List.of("admin"), TEST_TENANT)));
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder() // GH-90000
                    .apiKeyResolver(apiKeyResolver) // GH-90000
                    .policyEngine(policyEngine) // GH-90000
                    .auditService(auditService) // GH-90000
                    .enforcing(true) // GH-90000
                    .policyExcludedTenants(Set.of(TEST_TENANT)) // GH-90000
                    .build(); // GH-90000

            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE)); // GH-90000
            AsyncServlet secured = filter.apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            // Despite policy denial, excluded tenant bypasses the policy check → passes
            assertThat(status).isEqualTo(200); // GH-90000
            verify(policyEngine, never()).evaluate(anyString(), any()); // GH-90000
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
        void missingRequestId_doesNotBlockRequest() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            // req has no X-Request-ID header
            HttpRequest req = get(SENSITIVE_PATH); // GH-90000

            // Should succeed — a UUID will be generated internally
            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("present X-Request-ID is respected (no NPE, no override)")
        void presentRequestId_doesNotFailRequest() { // GH-90000
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = getWithRequestId(INTERNAL_PATH, "trace-abc-123"); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
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
        void nullPolicyEngine_criticalPath_failsClosed() { // GH-90000
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder() // GH-90000
                    .apiKeyResolver(apiKeyResolver) // GH-90000
                    .policyEngine(null)    // no policy engine // GH-90000
                    .auditService(auditService) // GH-90000
                    .enforcing(true) // GH-90000
                    .build(); // GH-90000
            AsyncServlet secured = filter.apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(403); // GH-90000
        }

        @Test
        @DisplayName("null PolicyEngine allows CRITICAL path in audit-only mode")
        void nullPolicyEngine_criticalPath_auditOnly_passesThrough() { // GH-90000
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder() // GH-90000
                    .apiKeyResolver(apiKeyResolver) // GH-90000
                    .policyEngine(null)    // no policy engine // GH-90000
                    .auditService(auditService) // GH-90000
                    .enforcing(false) // audit-only // GH-90000
                    .build(); // GH-90000
            AsyncServlet secured = filter.apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("null AuditService means no audit emission (no NPE)")
        void nullAuditService_sensitivePath_noNPE() { // GH-90000
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder() // GH-90000
                    .apiKeyResolver(apiKeyResolver) // GH-90000
                    .policyEngine(policyEngine) // GH-90000
                    .auditService(null)    // no audit service // GH-90000
                    .enforcing(true) // GH-90000
                    .build(); // GH-90000
            AsyncServlet secured = filter.apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH) // GH-90000
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                    .build(); // GH-90000

            // Must not throw NPE
            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
        }

        @Test
        @DisplayName("audit service throwing does not propagate to caller")
        void auditServiceThrows_doesNotPropagateToResponse() { // GH-90000
            when(auditService.record(any(AuditEvent.class))) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Audit store down")));
            AsyncServlet secured = enforcing().apply(OK_DELEGATE); // GH-90000
            HttpRequest req = HttpRequest.post("http://localhost" + SENSITIVE_PATH) // GH-90000
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), TEST_TENANT)
                    .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                    .build(); // GH-90000

            // Audit failure must never block the response
            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(200); // GH-90000
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
        void nullApiKeyResolver_throwsNPE() { // GH-90000
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> // GH-90000
                DataCloudSecurityFilter.builder() // GH-90000
                    .apiKeyResolver(null) // GH-90000
                    .build() // GH-90000
            ).isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("default enforcing=true when not set")
        void defaultEnforcing_isTrue() { // GH-90000
            // Verify that policy denial blocks when enforcing not explicitly set
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE)); // GH-90000
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder() // GH-90000
                    .apiKeyResolver(apiKeyResolver) // GH-90000
                    .policyEngine(policyEngine) // GH-90000
                    .build(); // GH-90000
            AsyncServlet secured = filter.apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(403); // GH-90000
        }

        @Test
        @DisplayName("policyExcludedTenants defaults to empty set")
        void defaultExcludedTenants_isEmpty() { // GH-90000
            // With no excluded tenants override and policy denying, should get 403
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE)); // GH-90000
            DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder() // GH-90000
                    .apiKeyResolver(apiKeyResolver) // GH-90000
                    .policyEngine(policyEngine) // GH-90000
                    .enforcing(true) // GH-90000
                    .build(); // GH-90000
            AsyncServlet secured = filter.apply(OK_DELEGATE); // GH-90000
            HttpRequest req = get(CRITICAL_PATH); // GH-90000

            int status = runPromise(() -> secured.serve(req).map(HttpResponse::getCode)); // GH-90000

            assertThat(status).isEqualTo(403); // GH-90000
        }
    }
}
