/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.platform.security.model.User;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Authorization matrix test covering OWNER, ADMIN, DEVELOPER, and VIEWER roles
 * across all resource scopes and permission combinations.
 *
 * <p>This test ensures that the authorization service correctly enforces permissions
 * based on role and resource scope, preventing privilege escalation and ensuring
 * least-privilege access.
 *
 * @doc.type test
 * @doc.purpose Comprehensive authorization matrix test for all role combinations
 * @doc.layer api
 * @doc.pattern Authorization Test
 */
@DisplayName("Authorization Matrix Tests")
class AuthorizationMatrixTest {

    private static final String TENANT_ID = "tenant-123";
    private static final String WORKSPACE_ID = "workspace-456";
    private static final String PROJECT_ID = "project-789";
    private static final String ARTIFACT_ID = "artifact-101";

    @Test
    @DisplayName("OWNER should have full workspace access")
    void ownerHasFullWorkspaceAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal owner = createPrincipal("owner-user", Set.of("OWNER"));

        assertDoesNotThrow(() -> service.authorizeWorkspaceAccess(owner, WORKSPACE_ID, Permission.WORKSPACE_READ));
        assertDoesNotThrow(() -> service.authorizeWorkspaceAccess(owner, WORKSPACE_ID, Permission.WORKSPACE_UPDATE));
        assertDoesNotThrow(() -> service.authorizeWorkspaceAccess(owner, WORKSPACE_ID, Permission.WORKSPACE_DELETE));
    }

    @Test
    @DisplayName("ADMIN should have workspace read and update access but not delete")
    void adminHasWorkspaceReadAndUpdateAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal admin = createPrincipal("admin-user", Set.of("ADMIN"));

        assertDoesNotThrow(() -> service.authorizeWorkspaceAccess(admin, WORKSPACE_ID, Permission.WORKSPACE_READ));
        assertDoesNotThrow(() -> service.authorizeWorkspaceAccess(admin, WORKSPACE_ID, Permission.WORKSPACE_UPDATE));
        
        AccessDeniedException exception = assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeWorkspaceAccess(admin, WORKSPACE_ID, Permission.WORKSPACE_DELETE)
        );
        assertTrue(exception.getMessage().contains("does not have permission"));
    }

    @Test
    @DisplayName("DEVELOPER should have workspace read access only")
    void developerHasWorkspaceReadAccessOnly() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal developer = createPrincipal("developer-user", Set.of("DEVELOPER"));

        assertDoesNotThrow(() -> service.authorizeWorkspaceAccess(developer, WORKSPACE_ID, Permission.WORKSPACE_READ));
        
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeWorkspaceAccess(developer, WORKSPACE_ID, Permission.WORKSPACE_UPDATE)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeWorkspaceAccess(developer, WORKSPACE_ID, Permission.WORKSPACE_DELETE)
        );
    }

    @Test
    @DisplayName("VIEWER should have workspace read access only")
    void viewerHasWorkspaceReadAccessOnly() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal viewer = createPrincipal("viewer-user", Set.of("VIEWER"));

        assertDoesNotThrow(() -> service.authorizeWorkspaceAccess(viewer, WORKSPACE_ID, Permission.WORKSPACE_READ));
        
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeWorkspaceAccess(viewer, WORKSPACE_ID, Permission.WORKSPACE_UPDATE)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeWorkspaceAccess(viewer, WORKSPACE_ID, Permission.WORKSPACE_DELETE)
        );
    }

    @Test
    @DisplayName("OWNER should have full project access")
    void ownerHasFullProjectAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal owner = createPrincipal("owner-user", Set.of("OWNER"));

        assertDoesNotThrow(() -> service.authorizeProjectAccess(owner, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_READ));
        assertDoesNotThrow(() -> service.authorizeProjectAccess(owner, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_UPDATE));
        assertDoesNotThrow(() -> service.authorizeProjectAccess(owner, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_DELETE));
    }

    @Test
    @DisplayName("ADMIN should have full project access")
    void adminHasFullProjectAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal admin = createPrincipal("admin-user", Set.of("ADMIN"));

        assertDoesNotThrow(() -> service.authorizeProjectAccess(admin, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_READ));
        assertDoesNotThrow(() -> service.authorizeProjectAccess(admin, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_UPDATE));
        assertDoesNotThrow(() -> service.authorizeProjectAccess(admin, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_DELETE));
    }

    @Test
    @DisplayName("DEVELOPER should have project read and update access but not delete")
    void developerHasProjectReadAndUpdateAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal developer = createPrincipal("developer-user", Set.of("DEVELOPER"));

        assertDoesNotThrow(() -> service.authorizeProjectAccess(developer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_READ));
        assertDoesNotThrow(() -> service.authorizeProjectAccess(developer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_UPDATE));
        
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeProjectAccess(developer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_DELETE)
        );
    }

    @Test
    @DisplayName("VIEWER should have project read access only")
    void viewerHasProjectReadAccessOnly() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal viewer = createPrincipal("viewer-user", Set.of("VIEWER"));

        assertDoesNotThrow(() -> service.authorizeProjectAccess(viewer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_READ));
        
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeProjectAccess(viewer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_UPDATE)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeProjectAccess(viewer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_DELETE)
        );
    }

    @Test
    @DisplayName("OWNER should have full artifact access")
    void ownerHasFullArtifactAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal owner = createPrincipal("owner-user", Set.of("OWNER"));

        assertDoesNotThrow(() -> service.authorizeArtifactAccess(owner, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_READ));
        assertDoesNotThrow(() -> service.authorizeArtifactAccess(owner, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_UPDATE));
        assertDoesNotThrow(() -> service.authorizeArtifactAccess(owner, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_DELETE));
    }

    @Test
    @DisplayName("ADMIN should have full artifact access")
    void adminHasFullArtifactAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal admin = createPrincipal("admin-user", Set.of("ADMIN"));

        assertDoesNotThrow(() -> service.authorizeArtifactAccess(admin, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_READ));
        assertDoesNotThrow(() -> service.authorizeArtifactAccess(admin, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_UPDATE));
        assertDoesNotThrow(() -> service.authorizeArtifactAccess(admin, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_DELETE));
    }

    @Test
    @DisplayName("DEVELOPER should have artifact read and update access but not delete")
    void developerHasArtifactReadAndUpdateAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal developer = createPrincipal("developer-user", Set.of("DEVELOPER"));

        assertDoesNotThrow(() -> service.authorizeArtifactAccess(developer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_READ));
        assertDoesNotThrow(() -> service.authorizeArtifactAccess(developer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_UPDATE));
        
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeArtifactAccess(developer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_DELETE)
        );
    }

    @Test
    @DisplayName("VIEWER should have artifact read access only")
    void viewerHasArtifactReadAccessOnly() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal viewer = createPrincipal("viewer-user", Set.of("VIEWER"));

        assertDoesNotThrow(() -> service.authorizeArtifactAccess(viewer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_READ));
        
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeArtifactAccess(viewer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_UPDATE)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeArtifactAccess(viewer, TENANT_ID, WORKSPACE_ID, PROJECT_ID, ARTIFACT_ID, Permission.REQUIREMENT_DELETE)
        );
    }

    @Test
    @DisplayName("Tenant scope mismatch should deny access for all roles")
    void tenantScopeMismatchDeniesAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal owner = createPrincipal("owner-user", Set.of("OWNER"), "different-tenant");

        AccessDeniedException exception = assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeProjectAccess(owner, TENANT_ID, WORKSPACE_ID, PROJECT_ID, Permission.PROJECT_READ)
        );
        assertTrue(exception.getMessage().contains("tenant"));
    }

    @Test
    @DisplayName("OWNER should have admin system access")
    void ownerHasAdminSystemAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal owner = createPrincipal("owner-user", Set.of("OWNER"));

        assertDoesNotThrow(() -> service.authorizeAdminAccess(owner));
    }

    @Test
    @DisplayName("ADMIN should have admin system access")
    void adminHasAdminSystemAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal admin = createPrincipal("admin-user", Set.of("ADMIN"));

        assertDoesNotThrow(() -> service.authorizeAdminAccess(admin));
    }

    @Test
    @DisplayName("DEVELOPER should not have admin system access")
    void developerDoesNotHaveAdminSystemAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal developer = createPrincipal("developer-user", Set.of("DEVELOPER"));

        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeAdminAccess(developer)
        );
    }

    @Test
    @DisplayName("VIEWER should not have admin system access")
    void viewerDoesNotHaveAdminSystemAccess() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal viewer = createPrincipal("viewer-user", Set.of("VIEWER"));

        assertThrows(
            AccessDeniedException.class,
            () -> service.authorizeAdminAccess(viewer)
        );
    }

    @Test
    @DisplayName("hasPermission should return correct results for OWNER")
    void hasPermissionForOwner() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal owner = createPrincipal("owner-user", Set.of("OWNER"));

        assertTrue(service.hasPermission(owner, Permission.WORKSPACE_READ));
        assertTrue(service.hasPermission(owner, Permission.WORKSPACE_UPDATE));
        assertTrue(service.hasPermission(owner, Permission.WORKSPACE_DELETE));
        assertTrue(service.hasPermission(owner, Permission.PROJECT_READ));
        assertTrue(service.hasPermission(owner, Permission.PROJECT_UPDATE));
        assertTrue(service.hasPermission(owner, Permission.PROJECT_DELETE));
        assertTrue(service.hasPermission(owner, Permission.ADMIN_SYSTEM));
    }

    @Test
    @DisplayName("hasPermission should return correct results for ADMIN")
    void hasPermissionForAdmin() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal admin = createPrincipal("admin-user", Set.of("ADMIN"));

        assertTrue(service.hasPermission(admin, Permission.WORKSPACE_READ));
        assertTrue(service.hasPermission(admin, Permission.WORKSPACE_UPDATE));
        assertFalse(service.hasPermission(admin, Permission.WORKSPACE_DELETE));
        assertTrue(service.hasPermission(admin, Permission.PROJECT_READ));
        assertTrue(service.hasPermission(admin, Permission.PROJECT_UPDATE));
        assertTrue(service.hasPermission(admin, Permission.PROJECT_DELETE));
        assertTrue(service.hasPermission(admin, Permission.ADMIN_SYSTEM));
    }

    @Test
    @DisplayName("hasPermission should return correct results for DEVELOPER")
    void hasPermissionForDeveloper() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal developer = createPrincipal("developer-user", Set.of("DEVELOPER"));

        assertTrue(service.hasPermission(developer, Permission.WORKSPACE_READ));
        assertFalse(service.hasPermission(developer, Permission.WORKSPACE_UPDATE));
        assertFalse(service.hasPermission(developer, Permission.WORKSPACE_DELETE));
        assertTrue(service.hasPermission(developer, Permission.PROJECT_READ));
        assertTrue(service.hasPermission(developer, Permission.PROJECT_UPDATE));
        assertFalse(service.hasPermission(developer, Permission.PROJECT_DELETE));
        assertFalse(service.hasPermission(developer, Permission.ADMIN_SYSTEM));
    }

    @Test
    @DisplayName("hasPermission should return correct results for VIEWER")
    void hasPermissionForViewer() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal viewer = createPrincipal("viewer-user", Set.of("VIEWER"));

        assertTrue(service.hasPermission(viewer, Permission.WORKSPACE_READ));
        assertFalse(service.hasPermission(viewer, Permission.WORKSPACE_UPDATE));
        assertFalse(service.hasPermission(viewer, Permission.WORKSPACE_DELETE));
        assertTrue(service.hasPermission(viewer, Permission.PROJECT_READ));
        assertFalse(service.hasPermission(viewer, Permission.PROJECT_UPDATE));
        assertFalse(service.hasPermission(viewer, Permission.PROJECT_DELETE));
        assertFalse(service.hasPermission(viewer, Permission.ADMIN_SYSTEM));
    }

    // Helper methods

    private YappcAuthorizationService createAuthorizationService() {
        SyncAuthorizationService mockAuth = mock(SyncAuthorizationService.class);
        
        // Configure mock to allow OWNER for all permissions
        when(mockAuth.hasPermission(any(User.class), eq(Permission.WORKSPACE_READ))).thenReturn(true);
        when(mockAuth.hasPermission(any(User.class), eq(Permission.WORKSPACE_UPDATE))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return hasRole(user, "OWNER") || hasRole(user, "ADMIN");
        });
        when(mockAuth.hasPermission(any(User.class), eq(Permission.WORKSPACE_DELETE))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return hasRole(user, "OWNER");
        });
        when(mockAuth.hasPermission(any(User.class), eq(Permission.PROJECT_READ))).thenReturn(true);
        when(mockAuth.hasPermission(any(User.class), eq(Permission.PROJECT_UPDATE))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return hasRole(user, "OWNER") || hasRole(user, "ADMIN") || hasRole(user, "DEVELOPER");
        });
        when(mockAuth.hasPermission(any(User.class), eq(Permission.PROJECT_DELETE))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return hasRole(user, "OWNER") || hasRole(user, "ADMIN");
        });
        when(mockAuth.hasPermission(any(User.class), eq(Permission.REQUIREMENT_READ))).thenReturn(true);
        when(mockAuth.hasPermission(any(User.class), eq(Permission.REQUIREMENT_UPDATE))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return hasRole(user, "OWNER") || hasRole(user, "ADMIN") || hasRole(user, "DEVELOPER");
        });
        when(mockAuth.hasPermission(any(User.class), eq(Permission.REQUIREMENT_DELETE))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return hasRole(user, "OWNER") || hasRole(user, "ADMIN");
        });
        when(mockAuth.hasPermission(any(User.class), eq(Permission.ADMIN_SYSTEM))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            return hasRole(user, "OWNER") || hasRole(user, "ADMIN");
        });

        return new YappcAuthorizationService(mockAuth);
    }

    private Principal createPrincipal(String name, Set<String> roles) {
        return createPrincipal(name, roles, TENANT_ID);
    }

    private Principal createPrincipal(String name, Set<String> roles, String tenantId) {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(name);
        when(principal.getTenantId()).thenReturn(tenantId);
        when(principal.getRoles()).thenReturn(new ArrayList<>(roles));
        return principal;
    }

    private boolean hasRole(User user, String role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }
}
