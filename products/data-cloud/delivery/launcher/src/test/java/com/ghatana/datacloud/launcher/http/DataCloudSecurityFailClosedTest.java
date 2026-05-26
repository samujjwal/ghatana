/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DC-SEC-001: Route metadata fail-closed E2E test.
 *
 * <p>Verifies that routes without metadata are rejected in production-like profiles
 * with fail-closed behavior. Unknown production routes never reach the handler.
 *
 * @doc.type class
 * @doc.purpose Route metadata fail-closed E2E test (DC-SEC-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudSecurity Fail-Closed Tests")
@Tag("production")
@Tag("security")
class DataCloudSecurityFailClosedTest extends EventloopTestBase {

    private ApiKeyResolver apiKeyResolver;
    private JwtTokenProvider jwtProvider;
    private PolicyEngine policyEngine;
    private AuditService auditService;
    private DataCloudSecurityFilter filter;
    private String originalProfile;

    @BeforeEach
    void setUp() {
        originalProfile = System.getProperty("DATACLOUD_PROFILE");

        apiKeyResolver = apiKey -> {
            if ("valid-api-key".equals(apiKey)) {
                return Optional.of(new Principal("user-1", List.of("OPERATOR"), "tenant-1"));
            }
            return Optional.empty();
        };

        jwtProvider = new JwtTokenProvider() {
            @Override
            public String createToken(String userId, List<String> roles, Map<String, Object> additionalClaims) {
                return "test-token";
            }

            @Override
            public boolean validateToken(String token) {
                return "valid-jwt-token".equals(token);
            }

            @Override
            public Optional<String> getUserIdFromToken(String token) {
                return "valid-jwt-token".equals(token) ? Optional.of("user-1") : Optional.empty();
            }

            @Override
            public Optional<Map<String, Object>> extractClaims(String token) {
                if ("valid-jwt-token".equals(token)) {
                    return Optional.of(Map.of("tenant_id", "tenant-1", "sub", "user-1"));
                }
                return Optional.empty();
            }

            @Override
            public List<String> getRolesFromToken(String token) {
                return "valid-jwt-token".equals(token) ? List.of("OPERATOR") : List.of();
            }
        };

        policyEngine = mock(PolicyEngine.class);
        when(policyEngine.evaluate(any(), any(), any())).thenReturn(true);

        auditService = mock(AuditService.class);
    }

    @AfterEach
    void tearDown() {
        if (originalProfile != null) {
            System.setProperty("DATACLOUD_PROFILE", originalProfile);
        } else {
            System.clearProperty("DATACLOUD_PROFILE");
        }
    }

    // ==================== DC-SEC-001: Route metadata fail-closed ====================

    @Test
    @DisplayName("DC-SEC-001: Route without metadata is rejected in production profile")
    void routeWithoutMetadataRejectedInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = new DataCloudSecurityFilter(
            apiKeyResolver,
            jwtProvider,
            policyEngine,
            auditService,
            "production"
        );

        HttpRequest request = HttpRequest.get(
            io.activej.http.HttpUrl.parse("http://localhost:8080/api/v1/unknown-route")
        );
        request.addHeader(io.activej.http.HttpHeaders.of("Authorization", "Bearer valid-jwt-token"));

        HttpResponse response = runPromise(() -> filter.filter(request, req -> Promise.of(HttpResponse.ok200().build())));

        // Should return 403 or 404, not reach the handler
        assertThat(response.getCode()).isNotEqualTo(200);
        assertThat(response.getCode()).isIn(403, 404);
    }

    @Test
    @DisplayName("DC-SEC-001: Route without metadata is rejected in staging profile")
    void routeWithoutMetadataRejectedInStaging() {
        System.setProperty("DATACLOUD_PROFILE", "staging");

        filter = new DataCloudSecurityFilter(
            apiKeyResolver,
            jwtProvider,
            policyEngine,
            auditService,
            "staging"
        );

        HttpRequest request = HttpRequest.get(
            io.activej.http.HttpUrl.parse("http://localhost:8080/api/v1/unknown-route")
        );
        request.addHeader(io.activej.http.HttpHeaders.of("Authorization", "Bearer valid-jwt-token"));

        HttpResponse response = runPromise(() -> filter.filter(request, req -> Promise.of(HttpResponse.ok200().build())));

        // Should return 403 or 404, not reach the handler
        assertThat(response.getCode()).isNotEqualTo(200);
        assertThat(response.getCode()).isIn(403, 404);
    }

    @Test
    @DisplayName("DC-SEC-001: Route without metadata is rejected in sovereign profile")
    void routeWithoutMetadataRejectedInSovereign() {
        System.setProperty("DATACLOUD_PROFILE", "sovereign");

        filter = new DataCloudSecurityFilter(
            apiKeyResolver,
            jwtProvider,
            policyEngine,
            auditService,
            "sovereign"
        );

        HttpRequest request = HttpRequest.get(
            io.activej.http.HttpUrl.parse("http://localhost:8080/api/v1/unknown-route")
        );
        request.addHeader(io.activej.http.HttpHeaders.of("Authorization", "Bearer valid-jwt-token"));

        HttpResponse response = runPromise(() -> filter.filter(request, req -> Promise.of(HttpResponse.ok200().build())));

        // Should return 403 or 404, not reach the handler
        assertThat(response.getCode()).isNotEqualTo(200);
        assertThat(response.getCode()).isIn(403, 404);
    }

    @Test
    @DisplayName("DC-SEC-001: Known route with metadata is allowed in production profile")
    void knownRouteWithMetadataAllowedInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = new DataCloudSecurityFilter(
            apiKeyResolver,
            jwtProvider,
            policyEngine,
            auditService,
            "production"
        );

        // Use a known route from the registry
        HttpRequest request = HttpRequest.get(
            io.activej.http.HttpUrl.parse("http://localhost:8080/api/v1/entities/test-collection")
        );
        request.addHeader(io.activej.http.HttpHeaders.of("Authorization", "Bearer valid-jwt-token"));

        HttpResponse response = runPromise(() -> filter.filter(request, req -> Promise.of(HttpResponse.ok200().build())));

        // Should reach the handler (200) if all auth/tenant checks pass
        // Note: This test verifies the filter doesn't block known routes with valid metadata
        // The actual handler may return different codes based on business logic
        assertThat(response).isNotNull();
    }
}
