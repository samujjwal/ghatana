package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.http.HttpRequest;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RoutePolicyEnforcer security enforcement.
 *
 * <p><b>Purpose</b><br>
 * Validates that backend policy enforcement works correctly for all
 * sensitivity levels and route groups.
 *
 * @doc.type test
 * @doc.purpose Security enforcement validation
 * @doc.layer product
 * @doc.pattern Security Test
 */
@DisplayName("Route Policy Enforcer Tests")
class RoutePolicyEnforcerTest extends EventloopTestBase {

    // ============ Group 8-3: Unauthorized/Forbidden Tests ============

    @Test
    @DisplayName("[SEC001] PUBLIC routes should not require authentication")
    void publicRoutesShouldNotRequireAuthentication() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = request("/health");
        when(permissionValidator.hasPermissions(any(), any(), anySet()))
                .thenReturn(Promise.of(true));

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/health", "GET"));

        // Assert
        assertTrue(result.isApproved(), "PUBLIC routes should be approved without authentication");
        assertEquals(200, result.getStatusCode());
    }

    @Test
    @DisplayName("[SEC002] AUTHENTICATED routes should reject unauthenticated requests")
    void authenticatedRoutesShouldRejectUnauthenticatedRequests() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = request("/api/v1/entities"); // No Authorization header

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/entities", "GET"));

        // Assert
        assertFalse(result.isApproved(), "Unauthenticated requests should be rejected");
        assertEquals(401, result.getStatusCode());
        assertEquals("Authentication required", result.getReason());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("UNAUTHENTICATED_ACCESS"), any());
    }

    @Test
    @DisplayName("[SEC003] SENSITIVE routes should reject requests without required permissions")
    void sensitiveRoutesShouldRejectRequestsWithoutRequiredPermissions() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess(any(), any())).thenReturn(true);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions(any(), any(), anySet()))
                .thenReturn(Promise.of(false));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/entities", "tenant-123", "user-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/entities", "POST"));

        // Assert
        assertFalse(result.isApproved(), "Requests without permissions should be rejected");
        assertEquals(403, result.getStatusCode());
        assertEquals("Insufficient permissions", result.getReason());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("INSUFFICIENT_PERMISSIONS"), any());
    }

    @Test
    @DisplayName("[SEC004] CRITICAL routes should reject requests without approval")
    void criticalRoutesShouldRejectRequestsWithoutApproval() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess(any(), any())).thenReturn(true);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions(any(), any(), anySet()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        when(approvalValidator.hasRequiredApproval(any(), any(), any()))
                .thenReturn(Promise.of(false));

        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/collections/123/purge", "tenant-123", "user-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/collections/123/purge", "POST"));

        // Assert
        assertFalse(result.isApproved(), "Requests without approval should be rejected");
        assertEquals(403, result.getStatusCode());
        assertEquals("Approval required", result.getReason());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("MISSING_APPROVAL"), any());
    }

    @Test
    @DisplayName("[SEC005] GOVERNANCE routes should reject policy violations")
    void governanceRoutesShouldRejectPolicyViolations() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess(any(), any())).thenReturn(true);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions(any(), any(), anySet()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(false));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/governance/policies", "tenant-123", "user-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/governance/policies", "POST"));

        // Assert
        assertFalse(result.isApproved(), "Policy violations should be rejected");
        assertEquals(403, result.getStatusCode());
        assertEquals("Policy violation", result.getReason());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("POLICY_VIOLATION"), any());
    }

    // ============ Group 8-4: Tenant Mismatch Tests ============

    @Test
    @DisplayName("[SEC006] Routes requiring tenant isolation should reject requests without tenant ID")
    void tenantIsolationRoutesShouldRejectRequestsWithoutTenantId() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
                .withHeader(HttpHeaders.AUTHORIZATION, HttpHeaderValue.of("Bearer token"))
                .withHeader(HttpHeaders.of("X-User-ID"), HttpHeaderValue.of("user-123"))
                .build();
        // Missing X-Tenant-ID

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/entities", "GET"));

        // Assert
        assertFalse(result.isApproved(), "Requests without tenant ID should be rejected");
        assertEquals(403, result.getStatusCode());
        assertEquals("Tenant ID required", result.getReason());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("MISSING_TENANT_ID"), any());
    }

    @Test
    @DisplayName("[SEC007] Routes should reject tenant mismatch")
    void routesShouldRejectTenantMismatch() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess("tenant-456", "user-123")).thenReturn(false);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions(any(), any(), anySet()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/entities", "tenant-456", "user-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/entities", "GET"));

        // Assert
        assertFalse(result.isApproved(), "Tenant mismatch should be rejected");
        assertEquals(403, result.getStatusCode());
        assertEquals("Tenant access denied", result.getReason());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("TENANT_MISMATCH"), any());
    }

    // ============ Group 8-5: Admin-Only Governance Tests ============

    @Test
    @DisplayName("[SEC008] ADMIN_ONLY routes should reject non-admin users")
    void adminOnlyRoutesShouldRejectNonAdminUsers() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess(any(), any())).thenReturn(true);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions("user-123", "tenant-123", Set.of("data-cloud:policy-manage")))
                .thenReturn(Promise.of(false));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/admin/config", "tenant-123", "user-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/admin/config", "GET"));

        // Assert
        assertFalse(result.isApproved(), "Non-admin users should be rejected from admin routes");
        assertEquals(403, result.getStatusCode());
        assertEquals("Insufficient permissions", result.getReason());
    }

    @Test
    @DisplayName("[SEC009] ADMIN_ONLY routes should allow admin users with correct permissions")
    void adminOnlyRoutesShouldAllowAdminUsersWithCorrectPermissions() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess(any(), any())).thenReturn(true);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions("admin-123", "tenant-123", Set.of("data-cloud:policy-manage")))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/admin/config", "tenant-123", "admin-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/admin/config", "GET"));

        // Assert
        assertTrue(result.isApproved(), "Admin users with correct permissions should be allowed");
        assertEquals(200, result.getStatusCode());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("POLICY_ENFORCEMENT_SUCCESS"), any());
    }

    // ============ Group 8-6: AI/Autonomy Approval-Required Tests ============

    @Test
    @DisplayName("[SEC010] AI_AUTONOMY routes should reject requests without approval")
    void aiAutonomyRoutesShouldRejectRequestsWithoutApproval() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess(any(), any())).thenReturn(true);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions(any(), any(), anySet()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        when(approvalValidator.hasRequiredApproval(any(), any(), any()))
                .thenReturn(Promise.of(false));

        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/ai/alerts/123/remediate", "tenant-123", "user-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/ai/alerts/123/remediate", "POST"));

        // Assert
        assertFalse(result.isApproved(), "AI autonomy routes without approval should be rejected");
        assertEquals(403, result.getStatusCode());
        assertEquals("Approval required", result.getReason());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("MISSING_APPROVAL"), any());
    }

    @Test
    @DisplayName("[SEC011] AI_AUTONOMY routes should allow requests with approval")
    void aiAutonomyRoutesShouldAllowRequestsWithApproval() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess(any(), any())).thenReturn(true);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions(any(), any(), anySet()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        when(approvalValidator.hasRequiredApproval(any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/ai/alerts/123/remediate", "tenant-123", "user-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/ai/alerts/123/remediate", "POST"));

        // Assert
        assertTrue(result.isApproved(), "AI autonomy routes with approval should be allowed");
        assertEquals(200, result.getStatusCode());
        verify(auditLogger).logSecurityEvent(any(), any(), any(), eq("POLICY_ENFORCEMENT_SUCCESS"), any());
    }

    @Test
    @DisplayName("[SEC012] MEDIA routes should enforce privacy consent checks")
    void mediaRoutesShouldEnforcePrivacyConsentChecks() {
        // Arrange
        RoutePolicyEnforcer.TenantValidator tenantValidator = mock(RoutePolicyEnforcer.TenantValidator.class);
        when(tenantValidator.validateTenantAccess(any(), any())).thenReturn(true);

        RoutePolicyEnforcer.PermissionValidator permissionValidator = mock(RoutePolicyEnforcer.PermissionValidator.class);
        when(permissionValidator.hasPermissions(any(), any(), anySet()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.PolicyValidator policyValidator = mock(RoutePolicyEnforcer.PolicyValidator.class);
        when(policyValidator.validatePolicyCompliance(any(), any(), any(), any()))
                .thenReturn(Promise.of(true));

        RoutePolicyEnforcer.ApprovalValidator approvalValidator = mock(RoutePolicyEnforcer.ApprovalValidator.class);
        RoutePolicyEnforcer.AuditLogger auditLogger = mock(RoutePolicyEnforcer.AuditLogger.class);

        RoutePolicyEnforcer enforcer = new RoutePolicyEnforcer(
                tenantValidator, permissionValidator, policyValidator, approvalValidator, auditLogger);

        HttpRequest request = authenticatedRequest("/api/v1/media/artifacts", "tenant-123", "user-123");

        // Act
        RoutePolicyEnforcer.PolicyEnforcementResult result =
                runPromise(() -> enforcer.enforcePolicy(request, "/api/v1/media/artifacts", "POST"));

        // Assert
        assertTrue(result.isApproved(), "Media routes with valid policy compliance should be allowed");
        assertEquals(200, result.getStatusCode());
        verify(policyValidator).validatePolicyCompliance(eq("tenant-123"), eq("user-123"), eq("/api/v1/media/artifacts"),
                eq(RouteSensitivityMatrix.SensitivityLevel.MEDIA));
    }

    private static HttpRequest request(String path) {
        return HttpRequest.get("http://localhost" + path).build();
    }

    private static HttpRequest authenticatedRequest(String path, String tenantId, String userId) {
        return HttpRequest.get("http://localhost" + path)
                .withHeader(HttpHeaders.AUTHORIZATION, HttpHeaderValue.of("Bearer token"))
                .withHeader(HttpHeaders.of("X-Tenant-ID"), HttpHeaderValue.of(tenantId))
                .withHeader(HttpHeaders.of("X-User-ID"), HttpHeaderValue.of(userId))
                .build();
    }
}
