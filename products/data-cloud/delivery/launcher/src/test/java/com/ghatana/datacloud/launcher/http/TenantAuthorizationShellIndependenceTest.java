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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DC-SEC-001: Server-side tenant authorization is independent of UI shell roles.
 *
 * <p>These tests prove that:
 * <ol>
 *   <li>A request with valid API key but wrong tenant is denied at the backend
 *       regardless of any frontend "X-Role" or "X-UI-Role" header.</li>
 *   <li>A request with no valid server-side credential is denied regardless of
 *       any frontend privilege escalation headers.</li>
 *   <li>A request where the policy engine denies access is refused, even if the
 *       caller claims elevated roles via frontend headers.</li>
 *   <li>Only server-validated credentials and tenant context determine access.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Backend tenant authorization is not influenced by frontend shell role
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-SEC-001 — Backend authorization independent of UI shell role")
class TenantAuthorizationShellIndependenceTest extends EventloopTestBase {

    private static final String VALID_API_KEY   = "sk-sec001-valid";
    private static final String INVALID_API_KEY = "sk-sec001-bad";
    private static final String REGISTERED_TENANT = "registered-tenant";
    private static final String OTHER_TENANT      = "other-tenant";
    private static final String SENSITIVE_PATH    = "/api/v1/collections/sec001-check";

    private ApiKeyResolver  apiKeyResolver;
    private PolicyEngine    policyEngine;
    private AuditService    auditService;

    private static final AsyncServlet OK_DELEGATE =
            req -> Promise.of(HttpResponse.ok200().build());

    @BeforeEach
    void setUp() {
        apiKeyResolver = mock(ApiKeyResolver.class);
        policyEngine   = mock(PolicyEngine.class);
        auditService   = mock(AuditService.class);

        Principal principal = new Principal("svc-sec001", List.of("admin"), REGISTERED_TENANT);
        when(apiKeyResolver.resolve(VALID_API_KEY)).thenReturn(Optional.of(principal));
        when(apiKeyResolver.resolve(INVALID_API_KEY)).thenReturn(Optional.empty());

        when(auditService.record(any(AuditEvent.class))).thenReturn(Promise.of((Void) null));
        when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
    }

    private DataCloudSecurityFilter enforcing() {
        return DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .enforcing(true)
                .build();
    }

    private static HttpRequest requestWith(String apiKey, String tenantId,
                                           String uiRole, String xRoleHeader) {
        HttpRequest.Builder builder = HttpRequest.get("http://localhost" + SENSITIVE_PATH)
                .withHeader(HttpHeaders.HOST, "localhost")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId);
        if (apiKey != null) {
            builder.withHeader(HttpHeaders.of("X-API-Key"), apiKey);
        }
        if (uiRole != null) {
            // Simulated frontend shell role header — must NOT grant backend access
            builder.withHeader(HttpHeaders.of("X-UI-Role"), uiRole);
        }
        if (xRoleHeader != null) {
            builder.withHeader(HttpHeaders.of("X-Role"), xRoleHeader);
        }
        return builder.build();
    }

    @Nested
    @DisplayName("Mismatched tenant is always denied")
    class MismatchedTenant {

        @Test
        @DisplayName("valid API key but wrong tenant — denied even with admin UI role header")
        void validKeyWrongTenant_deniedRegardlessOfUiRole() {
            HttpRequest req = requestWith(VALID_API_KEY, OTHER_TENANT, "admin", "SUPER_ADMIN");

            HttpResponse response = runPromise(() ->
                enforcing().apply(OK_DELEGATE).serve(req));

            // Tenant mismatch: principal tenant is REGISTERED_TENANT, request tenant is OTHER_TENANT
            assertThat(response.getCode()).isIn(401, 403, 400);
        }

        @Test
        @DisplayName("valid API key, matching tenant — allowed (baseline sanity check)")
        void validKeyMatchingTenant_allowed() {
            HttpRequest req = requestWith(VALID_API_KEY, REGISTERED_TENANT, null, null);

            HttpResponse response = runPromise(() ->
                enforcing().apply(OK_DELEGATE).serve(req));

            assertThat(response.getCode()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Invalid server credential is always denied")
    class InvalidCredential {

        @Test
        @DisplayName("invalid API key is denied — frontend admin role header cannot override")
        void invalidApiKey_deniedEvenWithAdminShellRole() {
            HttpRequest req = requestWith(INVALID_API_KEY, REGISTERED_TENANT, "admin", "ADMIN");

            HttpResponse response = runPromise(() ->
                enforcing().apply(OK_DELEGATE).serve(req));

            assertThat(response.getCode()).isIn(401, 403);
        }

        @Test
        @DisplayName("missing API key is denied — claiming superuser via X-Role header is rejected")
        void missingApiKey_deniedEvenWithSuperuserRoleHeader() {
            HttpRequest req = requestWith(null, REGISTERED_TENANT, "superuser", "SUPERUSER");

            HttpResponse response = runPromise(() ->
                enforcing().apply(OK_DELEGATE).serve(req));

            assertThat(response.getCode()).isIn(401, 403);
        }
    }

    @Nested
    @DisplayName("Policy engine denial cannot be overridden by frontend headers")
    class PolicyEngineDenial {

        @Test
        @DisplayName("policy engine deny — caller cannot override by injecting X-UI-Role=admin")
        void policyDeny_notOverriddenByUiRole() {
            // Override default policy to deny
            when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.FALSE));

            // Use a CRITICAL path so the policy engine is actually invoked
            HttpRequest req = HttpRequest.post("http://localhost/api/v1/governance/retention/purge")
                    .withHeader(HttpHeaders.HOST, "localhost")
                    .withHeader(HttpHeaders.of("X-Tenant-ID"), REGISTERED_TENANT)
                    .withHeader(HttpHeaders.of("X-API-Key"), VALID_API_KEY)
                    .withHeader(HttpHeaders.of("X-UI-Role"), "admin")
                    .build();

            HttpResponse response = runPromise(() ->
                enforcing().apply(OK_DELEGATE).serve(req));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }
}
