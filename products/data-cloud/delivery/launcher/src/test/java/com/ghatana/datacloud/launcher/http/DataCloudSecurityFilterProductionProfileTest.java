/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQuery;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Production-grade tests for DataCloudSecurityFilter in strict production profiles.
 *
 * <p>Verifies that missing tenant/auth is rejected in production/staging profiles
 * with fail-closed behavior. Tests enforce strict tenant resolution and authentication
 * requirements that are critical for production security.
 *
 * @doc.type class
 * @doc.purpose Production profile security tests for tenant/auth enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudSecurityFilter Production Profile Tests")
@Tag("production")
@Tag("security")
class DataCloudSecurityFilterProductionProfileTest extends EventloopTestBase {

    private ApiKeyResolver apiKeyResolver;
    private JwtTokenProvider jwtProvider;
    private PolicyEngine policyEngine;
    private AuditService auditService;
    private DataCloudSecurityFilter filter;
    private String originalProfile;

    @BeforeEach
    void setUp() {
        originalProfile = System.getProperty("DATACLOUD_PROFILE");

        // API key resolver: valid-api-key → Principal with tenant-1
        apiKeyResolver = apiKey -> {
            if ("valid-api-key".equals(apiKey)) {
                return Optional.of(new Principal("user-1", List.of("OPERATOR"), "tenant-1"));
            }
            return Optional.empty();
        };

        // JWT provider: valid-jwt-token → user-1 / tenant-1
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

        // Policy engine: allow all
        policyEngine = new PolicyEngine() {
            @Override
            public Promise<Boolean> evaluate(String policyName, Map<String, Object> context) {
                return Promise.of(true);
            }

            @Override
            public Promise<Boolean> policyExists(String policyName) {
                return Promise.of(true);
            }
        };

        // Audit service: no-op
        auditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.complete();
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (originalProfile != null) {
            System.setProperty("DATACLOUD_PROFILE", originalProfile);
        } else {
            System.clearProperty("DATACLOUD_PROFILE");
        }
    }

    @Test
    @DisplayName("Production profile rejects missing authentication credentials")
    void productionProfileRejectsMissingAuth() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Staging profile rejects missing authentication credentials")
    void stagingProfileRejectsMissingAuth() {
        System.setProperty("DATACLOUD_PROFILE", "staging");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Production profile rejects invalid API key")
    void productionProfileRejectsInvalidApiKey() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-API-Key"), "invalid-api-key")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Production profile rejects invalid JWT token")
    void productionProfileRejectsInvalidJwt() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt-token")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Production profile rejects JWT without tenant claim")
    void productionProfileRejectsJwtWithoutTenantClaim() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        JwtTokenProvider jwtWithoutTenant = new JwtTokenProvider() {
            @Override
            public String createToken(String userId, List<String> roles, Map<String, Object> additionalClaims) {
                return "test-token";
            }

            @Override
            public boolean validateToken(String token) {
                return "valid-jwt-no-tenant".equals(token);
            }

            @Override
            public Optional<String> getUserIdFromToken(String token) {
                return "valid-jwt-no-tenant".equals(token) ? Optional.of("user-1") : Optional.empty();
            }

            @Override
            public Optional<Map<String, Object>> extractClaims(String token) {
                if ("valid-jwt-no-tenant".equals(token)) {
                    return Optional.of(Map.of("sub", "user-1")); // no tenant_id claim
                }
                return Optional.empty();
            }

            @Override
            public List<String> getRolesFromToken(String token) {
                return "valid-jwt-no-tenant".equals(token) ? List.of("OPERATOR") : List.of();
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtWithoutTenant)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-no-tenant")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Production profile rejects tenant mismatch between JWT and header")
    void productionProfileRejectsTenantMismatch() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "different-tenant")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Production profile rejects principal without tenant")
    void productionProfileRejectsPrincipalWithoutTenant() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        ApiKeyResolver apiKeyWithoutTenant = apiKey -> {
            if ("valid-api-key-no-tenant".equals(apiKey)) {
                // Principal intentionally has blank tenant to test the missing-tenant rejection path
                return Optional.of(new Principal("user-1", List.of("OPERATOR"), ""));
            }
            return Optional.empty();
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyWithoutTenant)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key-no-tenant")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Response may be 400 or 401 depending on filter logic for missing tenant
        assertThat(response.getCode()).isBetween(400, 401);
    }

    @Test
    @DisplayName("Production profile allows valid API key with tenant")
    void productionProfileAllowsValidApiKeyWithTenant() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Production profile allows valid JWT with matching tenant")
    void productionProfileAllowsValidJwtWithMatchingTenant() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt-token")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Local profile rejects requests without authentication")
    void localProfileRejectsRequestsWithoutAuth() {
        System.setProperty("DATACLOUD_PROFILE", "local");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // In local profile, auth is still required by the filter
        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Audit-only mode allows requests despite invalid API key")
    void auditOnlyModeAllowsRequestsWithInvalidKey() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(false) // Audit-only mode
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-API-Key"), "invalid-api-key")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Audit-only: enforcing=false means requests pass through despite invalid key
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Production profile rejects critical routes without policy engine")
    void productionProfileRejectsCriticalRoutesWithoutPolicyEngine() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(null) // No policy engine
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/governance/policies")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Critical route without policy engine should be denied
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Production profile allows critical routes when policy engine permits")
    void productionProfileAllowsCriticalRoutesWhenPolicyAllows() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        // Governance POST requires ADMIN role — use an admin API key resolver
        ApiKeyResolver adminApiKeyResolver = apiKey -> {
            if ("valid-api-key".equals(apiKey)) {
                return Optional.of(new Principal("admin-user", List.of("ADMIN"), "tenant-1"));
            }
            return Optional.empty();
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(adminApiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/governance/policies")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Health endpoints bypass authentication in all profiles")
    void healthEndpointsBypassAuthenticationInAllProfiles() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/health")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Metrics endpoints bypass authentication in all profiles")
    void metricsEndpointsBypassAuthenticationInAllProfiles() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("DC-P0-01: Production profile rejects tenantId query parameter spoofing")
    void productionProfileRejectsTenantIdQuerySpoofing() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        // Try to spoof tenant via query parameter while using valid API key for tenant-1
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections?tenantId=spoofed-tenant")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Should reject due to tenant mismatch (API key tenant-1 vs query spoofed-tenant)
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("DC-P0-01: Production profile rejects API key with different tenant than authenticated principal")
    void productionProfileRejectsApiKeyTenantMismatch() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        // API key resolver that returns principal for tenant-2
        ApiKeyResolver apiKeyResolverTenant2 = apiKey -> {
            if ("api-key-tenant-2".equals(apiKey)) {
                return Optional.of(new Principal("user-2", List.of("OPERATOR"), "tenant-2"));
            }
            return Optional.empty();
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolverTenant2)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .build();

        // Try to use API key for tenant-2 but request with X-Tenant-Id header for tenant-1
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-API-Key"), "api-key-tenant-2")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Should reject due to tenant mismatch
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("DC-P0-01: Production profile startup validation fails without strict tenant resolution")
    void productionProfileStartupValidationFailsWithoutStrictTenantResolution() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(false) // NOT strict - should fail validation
            .deploymentProfile("production")
            .build();

        IllegalStateException exception = new IllegalStateException();
        try {
            filter.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P0-01");
        assertThat(exception.getMessage()).contains("strictTenantResolution must be true");
    }

    @Test
    @DisplayName("DC-P0-05: Production profile startup validation fails without authentication mechanism")
    void productionProfileStartupValidationFailsWithoutAuthMechanism() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        // DC-P0-05: Use assertThatThrownBy to catch NullPointerException from builder
        assertThatThrownBy(() -> {
            DataCloudSecurityFilter.builder()
                .apiKeyResolver(null)
                .jwtProvider(null) // No auth mechanism - this will throw NullPointerException in constructor
                .policyEngine(policyEngine)
                .auditService(auditService)
                .enforcing(true)
                .strictTenantResolution(true)
                .deploymentProfile("production")
                .build();
        })
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Either apiKeyResolver or jwtProvider must be configured");
    }

    @Test
    @DisplayName("DC-P0-01: Production profile startup validation fails without audit service when enforcing")
    void productionProfileStartupValidationFailsWithoutAuditService() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(null) // No audit service
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        IllegalStateException exception = new IllegalStateException();
        try {
            filter.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P0-01");
        assertThat(exception.getMessage()).contains("AuditService is required");
    }

    @Test
    @DisplayName("DC-P0-01: Production profile startup validation fails without policy engine when enforcing")
    void productionProfileStartupValidationFailsWithoutPolicyEngine() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(null) // No policy engine
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        IllegalStateException exception = new IllegalStateException();
        try {
            filter.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P0-01");
        assertThat(exception.getMessage()).contains("PolicyEngine is required");
    }

    @Test
    @DisplayName("DC-P0-01: Production profile startup validation succeeds with all required components")
    void productionProfileStartupValidationSucceedsWithAllComponents() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        // Should not throw any exception
        filter.validateProductionRequirements();
    }

    @Test
    @DisplayName("DC-P0-01: Local profile skips production validation")
    void localProfileSkipsProductionValidation() {
        System.setProperty("DATACLOUD_PROFILE", "local");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(null) // Missing policy engine - OK for local
            .auditService(null) // Missing audit service - OK for local
            .enforcing(true)
            .strictTenantResolution(false) // Not strict - OK for local
            .deploymentProfile("local")
            .build();

        // Should not throw any exception
        filter.validateProductionRequirements();
    }

    @Test
    @DisplayName("DC-P0-01: Sovereign profile enforces same strictness as production")
    void sovereignProfileEnforcesSameStrictnessAsProduction() {
        System.setProperty("DATACLOUD_PROFILE", "sovereign");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(false) // NOT strict - should fail
            .deploymentProfile("sovereign")
            .build();

        IllegalStateException exception = new IllegalStateException();
        try {
            filter.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P0-01");
        assertThat(exception.getMessage()).contains("strictTenantResolution must be true");
    }

    @Test
    @DisplayName("DC-P1-09: Production profile startup fails if audit sink is not ready")
    void productionProfileStartupFailsIfAuditSinkNotReady() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        // Audit service that fails to record (simulating unavailable sink)
        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit sink unavailable"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        IllegalStateException exception = new IllegalStateException();
        try {
            filter.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P1-09");
        assertThat(exception.getMessage()).contains("Audit sink readiness check failed");
    }

    @Test
    @DisplayName("DC-P1-09: Production profile startup succeeds when audit sink is ready")
    void productionProfileStartupSucceedsWhenAuditSinkReady() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        // Should not throw any exception
        filter.validateProductionRequirements();
    }

    // ==================== DC-SEC-001: Route Metadata Fail-Closed Tests ====================

    @Test
    @DisplayName("DC-SEC-001: Production profile rejects route without metadata")
    void dcSec001ProductionProfileRejectsRouteWithoutMetadata() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        // Request to a route that doesn't exist in RouteSecurityRegistry
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/unknown-route")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Should reject with 500 (ROUTE_METADATA_REQUIRED) in production-like profiles
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("DC-SEC-001: Staging profile rejects route without metadata")
    void dcSec001StagingProfileRejectsRouteWithoutMetadata() {
        System.setProperty("DATACLOUD_PROFILE", "staging");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("staging")
            .build();

        // Request to a route that doesn't exist in RouteSecurityRegistry
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/unknown-route")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Should reject with 500 (ROUTE_METADATA_REQUIRED) in production-like profiles
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("DC-SEC-001: Sovereign profile rejects route without metadata")
    void dcSec001SovereignProfileRejectsRouteWithoutMetadata() {
        System.setProperty("DATACLOUD_PROFILE", "sovereign");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("sovereign")
            .build();

        // Request to a route that doesn't exist in RouteSecurityRegistry
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/unknown-route")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Should reject with 500 (ROUTE_METADATA_REQUIRED) in production-like profiles
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("DC-SEC-001: Local profile allows route without metadata")
    void dcSec001LocalProfileAllowsRouteWithoutMetadata() {
        System.setProperty("DATACLOUD_PROFILE", "local");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(false)
            .deploymentProfile("local")
            .build();

        // Request to a route that doesn't exist in RouteSecurityRegistry
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/unknown-route")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Local profile may allow (200) or reject (404) - both are acceptable
        assertThat(response.getCode()).isIn(200, 404);
    }

    // ==================== DC-SEC-002: Blocking Audit Failure Injection Tests ====================

    @Test
    @DisplayName("DC-SEC-002: Critical route blocks when audit service fails in production")
    void dcSec002CriticalRouteBlocksWhenAuditFailsInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        ApiKeyResolver adminApiKeyResolver = apiKey -> Optional.of(new Principal("admin-1", List.of("ADMIN"), "tenant-1"));

        // Audit service that fails to record
        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit persistence failed"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(adminApiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        // Request to a CRITICAL route with requiresBlockingAudit=true (checkpoints delete)
        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/checkpoints/{checkpointId}")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        // The filter should throw an IllegalStateException when audit fails on CRITICAL route with blocking audit
        Throwable thrown = catchThrowable(() ->
            runPromise(() -> filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request)));
        
        assertThat(thrown)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-06");
    }

    @Test
    @DisplayName("DC-SEC-002: Sensitive route allows when audit service fails in production")
    void dcSec002SensitiveRouteAllowsWhenAuditFailsInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        // Audit service that fails to record
        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit persistence failed"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        // Request to a SENSITIVE route (query)
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/entities/{collection}")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // SENSITIVE routes may still succeed (200) despite audit failure (audit is fire-and-forget)
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("DC-SEC-002: Staging profile blocks critical route when audit fails")
    void dcSec002StagingProfileBlocksCriticalRouteWhenAuditFails() {
        System.setProperty("DATACLOUD_PROFILE", "staging");

        ApiKeyResolver adminApiKeyResolver = apiKey -> Optional.of(new Principal("admin-1", List.of("ADMIN"), "tenant-1"));

        // Audit service that fails to record
        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit persistence failed"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(adminApiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("staging")
            .build();

        // Request to a CRITICAL route with requiresBlockingAudit=true (checkpoints delete)
        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/checkpoints/{checkpointId}")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        // The filter should throw an IllegalStateException when audit fails on CRITICAL route with blocking audit
        Throwable thrown = catchThrowable(() ->
            runPromise(() -> filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request)));
        
        assertThat(thrown)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-06");
    }

    @Test
    @DisplayName("DC-P1-06: Audit write failure on redaction route blocks request in production")
    void auditWriteFailureOnRedactionRouteBlocksRequestInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        ApiKeyResolver adminApiKeyResolver = apiKey -> Optional.of(new Principal("admin-1", List.of("ADMIN"), "tenant-1"));

        // Audit service that fails to record
        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit persistence failed"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(adminApiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/governance/privacy/redact")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        // DC-P1-06: Redaction route should block on audit write failure in production
        assertThatThrownBy(() -> runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Audit persistence failed");
    }

    @Test
    @DisplayName("DC-P1-06: Audit write failure on retention purge route blocks request in production")
    void auditWriteFailureOnRetentionPurgeRouteBlocksRequestInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        ApiKeyResolver adminApiKeyResolver = apiKey -> Optional.of(new Principal("admin-1", List.of("ADMIN"), "tenant-1"));

        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit persistence failed"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(adminApiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/api/v1/governance/retention/purge")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        assertThatThrownBy(() -> runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Audit persistence failed");
    }

    @Test
    @DisplayName("DC-P1-06: Audit write failure on policy update route blocks request in production")
    void auditWriteFailureOnPolicyUpdateRouteBlocksRequestInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        ApiKeyResolver adminApiKeyResolver = apiKey -> Optional.of(new Principal("admin-1", List.of("ADMIN"), "tenant-1"));

        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit write failed"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(adminApiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.PUT, "http://localhost/api/v1/governance/policies/policy-1")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        assertThatThrownBy(() -> runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Audit persistence failed");
    }

    @Test
    @DisplayName("DC-P1-06: Audit write failure on delete entity route blocks request in production")
    void auditWriteFailureOnDeleteEntityRouteBlocksRequestInProduction() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        ApiKeyResolver adminApiKeyResolver = apiKey -> Optional.of(new Principal("admin-1", List.of("ADMIN"), "tenant-1"));

        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit write failed"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(adminApiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.DELETE, "http://localhost/api/v1/entities/test-collection/entity-123")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        assertThatThrownBy(() -> runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Audit persistence failed");
    }

    @Test
    @DisplayName("DC-P1-06: Audit write failure on non-critical route does not block request")
    void auditWriteFailureOnNonCriticalRouteDoesNotBlockRequest() {
        System.setProperty("DATACLOUD_PROFILE", "production");

        // Audit service that fails to record
        AuditService failingAuditService = new AuditService() {
            @Override
            public Promise<Void> record(AuditEvent event) {
                return Promise.ofException(new RuntimeException("Audit write failed"));
            }

            @Override
            public Promise<List<AuditEvent>> query(AuditQuery query) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }

            @Override
            public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
                return Promise.of(List.of());
            }
        };

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(failingAuditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("production")
            .build();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/collections")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-api-key")
            .build();

        HttpResponse response = runPromise(() ->
            filter.apply(req -> HttpResponse.ok200().toPromise()).serve(request));

        // Non-critical routes should succeed despite audit write failure (fire-and-forget)
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("DC-P1-09: Local profile skips audit sink readiness check")
    void localProfileSkipsAuditSinkReadinessCheck() {
        System.setProperty("DATACLOUD_PROFILE", "local");

        filter = DataCloudSecurityFilter.builder()
            .apiKeyResolver(apiKeyResolver)
            .jwtProvider(jwtProvider)
            .policyEngine(policyEngine)
            .auditService(auditService)
            .enforcing(true)
            .strictTenantResolution(true)
            .deploymentProfile("local")
            .build();

        // Should not throw any exception even if audit service is configured
        filter.validateProductionRequirements();
    }
        // =========================================================================
        //  DC-P1-011: Audit-only mode (enforcing=false) must fail-fast in production
        // =========================================================================

        @Test
        @DisplayName("DC-P1-011: enforcing=false in production profile throws IllegalStateException")
        void dc_p1_011_auditOnlyModeInProductionProfileThrows() {
            System.setProperty("DATACLOUD_PROFILE", "production");

            DataCloudSecurityFilter auditOnlyFilter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .jwtProvider(jwtProvider)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .enforcing(false)  // audit-only: must be rejected in production
                .strictTenantResolution(true)
                .deploymentProfile("production")
                .build();

            assertThatThrownBy(auditOnlyFilter::validateProductionRequirements)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DC-P1-011")
                .hasMessageContaining("enforcing=false");
        }

        @Test
        @DisplayName("DC-P1-011: enforcing=false in staging profile also throws IllegalStateException")
        void dc_p1_011_auditOnlyModeInStagingProfileThrows() {
            System.setProperty("DATACLOUD_PROFILE", "staging");

            DataCloudSecurityFilter auditOnlyFilter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .jwtProvider(jwtProvider)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .enforcing(false)  // audit-only: must also be rejected in staging
                .strictTenantResolution(true)
                .deploymentProfile("staging")
                .build();

            assertThatThrownBy(auditOnlyFilter::validateProductionRequirements)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DC-P1-011");
        }

        @Test
        @DisplayName("DC-P1-011: enforcing=false is permitted in local profile (not production-like)")
        void dc_p1_011_auditOnlyModeInLocalProfileDoesNotThrow() {
            System.setProperty("DATACLOUD_PROFILE", "local");

            DataCloudSecurityFilter auditOnlyFilter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .jwtProvider(jwtProvider)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .enforcing(false)  // audit-only: permitted in local/dev profiles
                .deploymentProfile("local")
                .build();

            // Must not throw — local profiles are explicitly exempted from this rule
            auditOnlyFilter.validateProductionRequirements();
        }
}
