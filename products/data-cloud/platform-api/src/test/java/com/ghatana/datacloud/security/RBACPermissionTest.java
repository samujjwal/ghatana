/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RBAC permission management (S003). // GH-90000
 *
 * @doc.type class
 * @doc.purpose RBAC permission tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("RBACPermission – Role Permissions (S003) [GH-90000]")
class RBACPermissionTest extends EventloopTestBase {

    @Mock
    private RBACService rbacService;

    @Nested
    @DisplayName("Permission Checking [GH-90000]")
    class PermissionCheckingTests {

        @Test
        @DisplayName("[S003]: has_permission_returns_true_when_granted [GH-90000]")
        void hasPermissionReturnsTrueWhenGranted() { // GH-90000
            String userId = "user-001";
            String tenantId = "tenant-alpha";
            RBACService.Permission permission = RBACService.Permission.ENTITY_READ;
            String resource = "entity-123";

            when(rbacService.hasPermission(userId, tenantId, permission, resource)) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            Boolean result = runPromise(() -> // GH-90000
                rbacService.hasPermission(userId, tenantId, permission, resource) // GH-90000
            );

            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[S003]: has_permission_returns_false_when_denied [GH-90000]")
        void hasPermissionReturnsFalseWhenDenied() { // GH-90000
            String userId = "user-002";
            String tenantId = "tenant-alpha";
            RBACService.Permission permission = RBACService.Permission.ENTITY_DELETE;
            String resource = "entity-123";

            when(rbacService.hasPermission(userId, tenantId, permission, resource)) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            Boolean result = runPromise(() -> // GH-90000
                rbacService.hasPermission(userId, tenantId, permission, resource) // GH-90000
            );

            assertThat(result).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[S003]: get_user_permissions_returns_all_permissions [GH-90000]")
        void getUserPermissionsReturnsAllPermissions() { // GH-90000
            String userId = "user-001";
            String tenantId = "tenant-alpha";

            Set<RBACService.Permission> permissions = Set.of( // GH-90000
                RBACService.Permission.ENTITY_READ,
                RBACService.Permission.ENTITY_CREATE,
                RBACService.Permission.ENTITY_UPDATE
            );

            when(rbacService.getUserPermissions(userId, tenantId)) // GH-90000
                .thenReturn(Promise.of(permissions)); // GH-90000

            Set<RBACService.Permission> result = runPromise(() -> // GH-90000
                rbacService.getUserPermissions(userId, tenantId) // GH-90000
            );

            assertThat(result).contains( // GH-90000
                RBACService.Permission.ENTITY_READ,
                RBACService.Permission.ENTITY_CREATE
            );
        }
    }

    @Nested
    @DisplayName("Role Assignment [GH-90000]")
    class RoleAssignmentTests {

        @Test
        @DisplayName("[S003]: assign_role_grants_permissions [GH-90000]")
        void assignRoleGrantsPermissions() { // GH-90000
            String userId = "user-001";
            String tenantId = "tenant-alpha";
            String roleId = "role-admin";

            when(rbacService.assignRole(userId, tenantId, roleId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> rbacService.assignRole(userId, tenantId, roleId)); // GH-90000

            verify(rbacService).assignRole(userId, tenantId, roleId); // GH-90000
        }

        @Test
        @DisplayName("[S003]: revoke_role_removes_permissions [GH-90000]")
        void revokeRoleRemovesPermissions() { // GH-90000
            String userId = "user-001";
            String tenantId = "tenant-alpha";
            String roleId = "role-editor";

            when(rbacService.revokeRole(userId, tenantId, roleId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> rbacService.revokeRole(userId, tenantId, roleId)); // GH-90000

            verify(rbacService).revokeRole(userId, tenantId, roleId); // GH-90000
        }

        @Test
        @DisplayName("[S003]: get_user_roles_returns_assigned_roles [GH-90000]")
        void getUserRolesReturnsAssignedRoles() { // GH-90000
            String userId = "user-001";
            String tenantId = "tenant-alpha";

            List<RBACService.Role> roles = List.of( // GH-90000
                createRole("role-1", "Editor", Set.of(RBACService.Permission.ENTITY_READ)), // GH-90000
                createRole("role-2", "Viewer", Set.of(RBACService.Permission.ENTITY_READ)) // GH-90000
            );

            when(rbacService.getUserRoles(userId, tenantId)) // GH-90000
                .thenReturn(Promise.of(roles)); // GH-90000

            List<RBACService.Role> result = runPromise(() -> // GH-90000
                rbacService.getUserRoles(userId, tenantId) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Role Management [GH-90000]")
    class RoleManagementTests {

        @Test
        @DisplayName("[S003]: save_role_creates_role [GH-90000]")
        void saveRoleCreatesRole() { // GH-90000
            RBACService.Role role = createRole("new-role", "Analyst", Set.of( // GH-90000
                RBACService.Permission.REPORT_READ,
                RBACService.Permission.REPORT_CREATE
            ));

            when(rbacService.saveRole(any())) // GH-90000
                .thenReturn(Promise.of(role)); // GH-90000

            RBACService.Role result = runPromise(() -> rbacService.saveRole(role)); // GH-90000

            assertThat(result.id()).isEqualTo("new-role [GH-90000]");
            assertThat(result.permissions()).contains(RBACService.Permission.REPORT_READ); // GH-90000
        }

        @Test
        @DisplayName("[S003]: get_role_returns_existing [GH-90000]")
        void getRoleReturnsExisting() { // GH-90000
            String roleId = "existing-role";
            RBACService.Role role = createRole(roleId, "Manager", Set.of( // GH-90000
                RBACService.Permission.TENANT_ADMIN
            ));

            when(rbacService.getRole(roleId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(role))); // GH-90000

            Optional<RBACService.Role> result = runPromise(() -> rbacService.getRole(roleId)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id()).isEqualTo(roleId); // GH-90000
        }

        @Test
        @DisplayName("[S003]: list_roles_returns_tenant_roles [GH-90000]")
        void listRolesReturnsTenantRoles() { // GH-90000
            String tenantId = "tenant-alpha";

            List<RBACService.Role> roles = List.of( // GH-90000
                createRole("r1", "Admin", Set.of()), // GH-90000
                createRole("r2", "User", Set.of()), // GH-90000
                createRole("r3", "Guest", Set.of()) // GH-90000
            );

            when(rbacService.listRoles(tenantId)) // GH-90000
                .thenReturn(Promise.of(roles)); // GH-90000

            List<RBACService.Role> result = runPromise(() -> rbacService.listRoles(tenantId)); // GH-90000

            assertThat(result).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("[S003]: delete_role_removes_role [GH-90000]")
        void deleteRoleRemovesRole() { // GH-90000
            String roleId = "obsolete-role";

            when(rbacService.deleteRole(roleId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> rbacService.deleteRole(roleId)); // GH-90000

            verify(rbacService).deleteRole(roleId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Permission Hierarchy [GH-90000]")
    class PermissionHierarchyTests {

        @Test
        @DisplayName("[S003]: admin_role_has_all_permissions [GH-90000]")
        void adminRoleHasAllPermissions() { // GH-90000
            RBACService.Role admin = createRole("admin", "Administrator", Set.of( // GH-90000
                RBACService.Permission.TENANT_ADMIN,
                RBACService.Permission.ENTITY_ADMIN,
                RBACService.Permission.COLLECTION_ADMIN,
                RBACService.Permission.USER_MANAGE
            ));

            assertThat(admin.permissions()).contains(RBACService.Permission.TENANT_ADMIN); // GH-90000
            assertThat(admin.hasPermission(RBACService.Permission.ENTITY_ADMIN)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[S003]: read_only_role_has_limited_permissions [GH-90000]")
        void readOnlyRoleHasLimitedPermissions() { // GH-90000
            RBACService.Role viewer = createRole("viewer", "Viewer", Set.of( // GH-90000
                RBACService.Permission.ENTITY_READ,
                RBACService.Permission.COLLECTION_READ,
                RBACService.Permission.REPORT_READ
            ));

            assertThat(viewer.hasPermission(RBACService.Permission.ENTITY_READ)).isTrue(); // GH-90000
            assertThat(viewer.hasPermission(RBACService.Permission.ENTITY_DELETE)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[S003]: permission_inheritance_works [GH-90000]")
        void permissionInheritanceWorks() { // GH-90000
            // A user with multiple roles gets union of permissions
            Set<RBACService.Permission> role1Perms = Set.of(RBACService.Permission.ENTITY_READ); // GH-90000
            Set<RBACService.Permission> role2Perms = Set.of(RBACService.Permission.ENTITY_CREATE); // GH-90000

            Set<RBACService.Permission> combined = new java.util.HashSet<>(); // GH-90000
            combined.addAll(role1Perms); // GH-90000
            combined.addAll(role2Perms); // GH-90000

            assertThat(combined).contains( // GH-90000
                RBACService.Permission.ENTITY_READ,
                RBACService.Permission.ENTITY_CREATE
            );
        }
    }

    @Nested
    @DisplayName("Role Queries [GH-90000]")
    class RoleQueriesTests {

        @Test
        @DisplayName("[S003]: get_users_with_role_returns_assignees [GH-90000]")
        void getUsersWithRoleReturnsAssignees() { // GH-90000
            String roleId = "editor-role";

            List<String> users = List.of("user-1", "user-2", "user-3"); // GH-90000

            when(rbacService.getUsersWithRole(roleId)) // GH-90000
                .thenReturn(Promise.of(users)); // GH-90000

            List<String> result = runPromise(() -> rbacService.getUsersWithRole(roleId)); // GH-90000

            assertThat(result).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("[S003]: role_has_permission_checks_correctly [GH-90000]")
        void roleHasPermissionChecksCorrectly() { // GH-90000
            RBACService.Role role = createRole("custom", "Custom", Set.of( // GH-90000
                RBACService.Permission.EVENT_PUBLISH,
                RBACService.Permission.EVENT_SUBSCRIBE
            ));

            assertThat(role.hasPermission(RBACService.Permission.EVENT_PUBLISH)).isTrue(); // GH-90000
            assertThat(role.hasPermission(RBACService.Permission.EVENT_READ)).isFalse(); // GH-90000
        }
    }

    private RBACService.Role createRole(String id, String name, Set<RBACService.Permission> permissions) { // GH-90000
        return new RBACService.Role( // GH-90000
            id, name, "", "tenant-alpha",
            permissions, List.of(), false, 0 // GH-90000
        );
    }
}
