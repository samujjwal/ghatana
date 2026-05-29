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
import io.activej.http.HttpHeaders;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
        auditService = mock(AuditService.class);

        lenient().when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
        lenient().when(auditService.record(any())).thenReturn(Promise.of((Void) null));
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

        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .deploymentProfile("production")
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/v1/unknown-route")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
            .withHeader(HttpHeaders.HOST, "localhost")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> Promise.of(HttpResponse.ok200().build())).serve(request));

        // Unknown routes must be rejected in production (fail-closed = 500)
        assertThat(response.getCode()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("DC-SEC-001: Route without metadata is rejected in staging profile")
    void routeWithoutMetadataRejectedInStaging() {
        System.setProperty("DATACLOUD_PROFILE", "staging");

        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .deploymentProfile("staging")
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/v1/unknown-route")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
            .withHeader(HttpHeaders.HOST, "localhost")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> Promise.of(HttpResponse.ok200().build())).serve(request));

        // Unknown routes must be rejected in staging (fail-closed = 500)
        assertThat(response.getCode()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("DC-SEC-001: Route without metadata is rejected in sovereign profile")
    void routeWithoutMetadataRejectedInSovereign() {
        System.setProperty("DATACLOUD_PROFILE", "sovereign");

        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .deploymentProfile("sovereign")
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/v1/unknown-route")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
            .withHeader(HttpHeaders.HOST, "localhost")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> Promise.of(HttpResponse.ok200().build())).serve(request));

        // Unknown routes must be rejected in sovereign (fail-closed = 500)
        assertThat(response.getCode()).isNotEqualTo(200);
    }

    @Test
    @DisplayName("DC-SEC-001: Known route with metadata is allowed in production profile")
    void knownRouteWithMetadataAllowedInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        when(policyEngine.evaluate(anyString(), any())).thenReturn(Promise.of(Boolean.TRUE));
        when(auditService.record(any())).thenReturn(Promise.of((Void) null));

        DataCloudSecurityFilter filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .deploymentProfile("production")
            .enforcing(true)
            .build();

        // Use a known route from the registry with valid API key auth
        HttpRequest request = HttpRequest.get("http://localhost:8080/api/v1/entities/test-collection")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.HOST, "localhost")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> Promise.of(HttpResponse.ok200().build())).serve(request));

        // Should not be blocked by fail-closed (route exists in registry)
        assertThat(response.getCode()).isEqualTo(200);
    }
}