package com.ghatana.datacloud.launcher.http.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for RouteSensitivityMatrix.
 *
 * @doc.type class
 * @doc.purpose Test route sensitivity matrix functionality
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RouteSensitivityMatrix Tests")
class RouteSensitivityMatrixTest {

    @Nested
    @DisplayName("Route Sensitivity Lookup")
    class RouteSensitivityLookup {

        @Test
        @DisplayName("Should return PUBLIC sensitivity for health endpoint")
        void shouldReturnPublicForHealthEndpoint() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/health");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.PUBLIC, sensitivity.getSensitivity());
            assertFalse(sensitivity.requiresAuthentication());
        }

        @Test
        @DisplayName("Should return AUTHENTICATED sensitivity for data read endpoint")
        void shouldReturnAuthenticatedForDataReadEndpoint() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/entities/123/get");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.AUTHENTICATED, sensitivity.getSensitivity());
            assertTrue(sensitivity.requiresAuthentication());
            assertTrue(sensitivity.requiresAuditLogging());
            assertTrue(sensitivity.requiresTenantIsolation());
        }

        @Test
        @DisplayName("Should return SENSITIVE sensitivity for data write endpoint")
        void shouldReturnSensitiveForDataWriteEndpoint() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/entities/123/create");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.SENSITIVE, sensitivity.getSensitivity());
            assertTrue(sensitivity.requiresAuthentication());
            assertTrue(sensitivity.requiresPolicyCheck());
            assertTrue(sensitivity.requiresAuditLogging());
            assertTrue(sensitivity.requiresTenantIsolation());
        }

        @Test
        @DisplayName("Should return CRITICAL sensitivity for governance policy endpoint")
        void shouldReturnCriticalForGovernancePolicyEndpoint() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/governance/policies/123/create");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.CRITICAL, sensitivity.getSensitivity());
            assertTrue(sensitivity.requiresAuthentication());
            assertTrue(sensitivity.requiresPolicyCheck());
            assertTrue(sensitivity.requiresAuditLogging());
            assertTrue(sensitivity.requiresTenantIsolation());
            assertTrue(sensitivity.allowsBreakGlass());
        }

        @Test
        @DisplayName("Should return ADMIN_ONLY sensitivity for admin config endpoint")
        void shouldReturnAdminOnlyForAdminConfigEndpoint() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/admin/config/123/update");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.ADMIN_ONLY, sensitivity.getSensitivity());
            assertTrue(sensitivity.requiresAuthentication());
            assertTrue(sensitivity.requiresPolicyCheck());
            assertTrue(sensitivity.requiresAuditLogging());
            assertTrue(sensitivity.requiresTenantIsolation());
            assertTrue(sensitivity.allowsBreakGlass());
        }

        @Test
        @DisplayName("Should return MEDIA sensitivity for media upload endpoint")
        void shouldReturnMediaForMediaUploadEndpoint() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/media/artifacts/123/upload");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.MEDIA, sensitivity.getSensitivity());
            assertTrue(sensitivity.requiresAuthentication());
            assertTrue(sensitivity.requiresPolicyCheck());
            assertTrue(sensitivity.requiresAuditLogging());
            assertTrue(sensitivity.requiresTenantIsolation());
        }

        @Test
        @DisplayName("Should return GOVERNANCE sensitivity for policy evaluation endpoint")
        void shouldReturnGovernanceForPolicyEvaluationEndpoint() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/governance/policies/123/evaluate");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.GOVERNANCE, sensitivity.getSensitivity());
            assertTrue(sensitivity.requiresAuthentication());
            assertTrue(sensitivity.requiresPolicyCheck());
            assertTrue(sensitivity.requiresAuditLogging());
            assertTrue(sensitivity.requiresTenantIsolation());
        }

        @Test
        @DisplayName("Should return default AUTHENTICATED for unknown routes")
        void shouldReturnDefaultAuthenticatedForUnknownRoutes() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/unknown/route");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.AUTHENTICATED, sensitivity.getSensitivity());
            assertTrue(sensitivity.requiresAuthentication());
        }

        @Test
        @DisplayName("Should match wildcard routes correctly")
        void shouldMatchWildcardRoutesCorrectly() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/status/health");
            
            assertEquals(RouteSensitivityMatrix.SensitivityLevel.PUBLIC, sensitivity.getSensitivity());
        }
    }

    @Nested
    @DisplayName("Static Helper Methods")
    class StaticHelperMethods {

        @Test
        @DisplayName("Should correctly identify routes requiring authentication")
        void shouldIdentifyRoutesRequiringAuthentication() {
            assertTrue(RouteSensitivityMatrix.requiresAuthentication("/api/v1/entities/123/get"));
            assertFalse(RouteSensitivityMatrix.requiresAuthentication("/health"));
        }

        @Test
        @DisplayName("Should correctly identify routes requiring policy check")
        void shouldIdentifyRoutesRequiringPolicyCheck() {
            assertTrue(RouteSensitivityMatrix.requiresPolicyCheck("/api/v1/entities/123/create"));
            assertFalse(RouteSensitivityMatrix.requiresPolicyCheck("/api/v1/entities/123/get"));
        }

        @Test
        @DisplayName("Should correctly identify routes requiring audit logging")
        void shouldIdentifyRoutesRequiringAuditLogging() {
            assertTrue(RouteSensitivityMatrix.requiresAuditLogging("/api/v1/entities/123/create"));
            assertFalse(RouteSensitivityMatrix.requiresAuditLogging("/health"));
        }

        @Test
        @DisplayName("Should correctly identify routes requiring tenant isolation")
        void shouldIdentifyRoutesRequiringTenantIsolation() {
            assertTrue(RouteSensitivityMatrix.requiresTenantIsolation("/api/v1/entities/123/get"));
            assertFalse(RouteSensitivityMatrix.requiresTenantIsolation("/health"));
        }

        @Test
        @DisplayName("Should return required roles for admin routes")
        void shouldReturnRequiredRolesForAdminRoutes() {
            var roles = RouteSensitivityMatrix.getRequiredRoles("/api/v1/admin/config/123/update");
            assertTrue(roles.contains("admin"));
        }

        @Test
        @DisplayName("Should return required permissions for write routes")
        void shouldReturnRequiredPermissionsForWriteRoutes() {
            var permissions = RouteSensitivityMatrix.getRequiredPermissions("/api/v1/entities/123/create");
            assertTrue(permissions.contains("entity:write"));
        }
    }

    @Nested
    @DisplayName("Route Sensitivity Properties")
    class RouteSensitivityProperties {

        @Test
        @DisplayName("Should have non-empty description")
        void shouldHaveNonEmptyDescription() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/entities/123/get");
            
            assertNotNull(sensitivity.getDescription());
            assertFalse(sensitivity.getDescription().isBlank());
        }

        @Test
        @DisplayName("Should have route pattern")
        void shouldHaveRoutePattern() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/entities/123/get");
            
            assertNotNull(sensitivity.getRoutePattern());
        }

        @Test
        @DisplayName("Should have required roles set")
        void shouldHaveRequiredRolesSet() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/governance/policies/123/create");
            
            assertNotNull(sensitivity.getRequiredRoles());
            assertFalse(sensitivity.getRequiredRoles().isEmpty());
        }

        @Test
        @DisplayName("Should have required permissions set")
        void shouldHaveRequiredPermissionsSet() {
            RouteSensitivityMatrix.RouteSensitivity sensitivity = 
                RouteSensitivityMatrix.getRouteSensitivity("/api/v1/entities/123/create");
            
            assertNotNull(sensitivity.getRequiredPermissions());
            assertFalse(sensitivity.getRequiredPermissions().isEmpty());
        }
    }
}
