package com.ghatana.datacloud.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RouteSensitivityMatrix.
 *
 * @doc.type class
 * @doc.purpose Route sensitivity matrix tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RouteSensitivityMatrix Tests")
class RouteSensitivityMatrixTest {

    @Test
    @DisplayName("Should return sensitivity for known routes")
    void shouldReturnSensitivityForKnownRoutes() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        
        var sensitivity = matrix.getSensitivity("/api/v1/entities", "GET");
        assertThat(sensitivity).isPresent();
        assertThat(sensitivity.get().sensitivity()).isEqualTo(RouteSensitivityMatrix.SensitivityLevel.MEDIUM);
    }

    @Test
    @DisplayName("Should return empty for unknown routes")
    void shouldReturnEmptyForUnknownRoutes() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        
        var sensitivity = matrix.getSensitivity("/api/v1/unknown", "GET");
        assertThat(sensitivity).isEmpty();
    }

    @Test
    @DisplayName("Should add custom route")
    void shouldAddCustomRoute() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        
        var customSensitivity = new RouteSensitivityMatrix.RouteSensitivity(
                "/api/v1/custom",
                "GET",
                RouteSensitivityMatrix.SensitivityLevel.MEDIUM,
                RouteSensitivityMatrix.AuthenticationRequirement.REQUIRED,
                RouteSensitivityMatrix.AuthorizationRequirement.RBAC,
                true,
                false,
                true,
                false,
                java.util.Set.of("custom-role"),
                java.util.Set.of("custom:read"),
                "INTERNAL"
        );
        
        matrix.addCustomRoute("/api/v1/custom", "GET", customSensitivity);
        
        var sensitivity = matrix.getSensitivity("/api/v1/custom", "GET");
        assertThat(sensitivity).isPresent();
        assertThat(sensitivity.get().sensitivity()).isEqualTo(RouteSensitivityMatrix.SensitivityLevel.MEDIUM);
    }

    @Test
    @DisplayName("Should enforce tenant isolation for sensitive routes")
    void shouldEnforceTenantIsolationForSensitiveRoutes() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        
        var sensitivity = matrix.getSensitivity("/api/v1/entities", "GET");
        assertThat(sensitivity).isPresent();
        assertThat(sensitivity.get().requiresTenantIsolation()).isTrue();
    }

    @Test
    @DisplayName("Should require audit logging for high sensitivity routes")
    void shouldRequireAuditLoggingForHighSensitivityRoutes() {
        RouteSensitivityMatrix matrix = new RouteSensitivityMatrix();
        
        var sensitivity = matrix.getSensitivity("/api/v1/entities", "GET");
        assertThat(sensitivity).isPresent();
        assertThat(sensitivity.get().requiresAuditLogging()).isTrue();
    }
}
