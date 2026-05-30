package com.ghatana.datacloud.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RoutePolicyEnforcer.
 *
 * @doc.type class
 * @doc.purpose Route policy enforcer tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RoutePolicyEnforcer Tests")
class RoutePolicyEnforcerTest {

    @Test
    @DisplayName("Should allow request with valid authentication")
    void shouldAllowRequestWithValidAuthentication() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(matrix);
        
        var context = new RoutePolicyEnforcer.SecurityContext(
                "tenant-123",
                "user-456",
                Set.of("data-reader"),
                Set.of("data-cloud:entity-read"),
                "127.0.0.1",
                "test-agent",
                Map.of()
        );
        
        var result = enforcer.enforcePolicy("/api/v1/entities", "GET", context);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("Should deny request without authentication when required")
    void shouldDenyRequestWithoutAuthenticationWhenRequired() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(matrix);
        
        var context = new RoutePolicyEnforcer.SecurityContext(
                "tenant-123",
                null, // No user ID
                Set.of(),
                Set.of(),
                "127.0.0.1",
                "test-agent",
                Map.of()
        );
        
        var result = enforcer.enforcePolicy("/api/v1/entities", "GET", context);
        assertThat(result.allowed()).isFalse();
        assertThat(result.violationType()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("Should deny request without required role")
    void shouldDenyRequestWithoutRequiredRole() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(matrix);
        
        var context = new RoutePolicyEnforcer.SecurityContext(
                "tenant-123",
                "user-456",
                Set.of("other-role"), // Missing required role
                Set.of("data-cloud:entity-read"),
                "127.0.0.1",
                "test-agent",
                Map.of()
        );
        
        var result = enforcer.enforcePolicy("/api/v1/entities", "GET", context);
        assertThat(result.allowed()).isFalse();
        assertThat(result.violationType()).isEqualTo("ROLE_NOT_FOUND");
    }

    @Test
    @DisplayName("Should deny request without tenant ID when required")
    void shouldDenyRequestWithoutTenantIdWhenRequired() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(matrix);
        
        var context = new RoutePolicyEnforcer.SecurityContext(
                null, // No tenant ID
                "user-456",
                Set.of("data-reader"),
                Set.of("data-cloud:entity-read"),
                "127.0.0.1",
                "test-agent",
                Map.of()
        );
        
        var result = enforcer.enforcePolicy("/api/v1/entities", "GET", context);
        assertThat(result.allowed()).isFalse();
        assertThat(result.violationType()).isEqualTo("TENANT_ID_REQUIRED");
    }

    @Test
    @DisplayName("Should check rate limiting requirement")
    void shouldCheckRateLimitingRequirement() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(matrix);
        
        boolean requiresRateLimit = enforcer.requiresRateLimiting("/api/v1/entities", "GET");
        assertThat(requiresRateLimit).isTrue();
    }

    @Test
    @DisplayName("Should check audit logging requirement")
    void shouldCheckAuditLoggingRequirement() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(matrix);
        
        boolean requiresAudit = enforcer.requiresAuditLogging("/api/v1/entities", "GET");
        assertThat(requiresAudit).isTrue();
    }
}
