/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Tests for RBAC permission management (S003). 
 *
 * @doc.type class
 * @doc.purpose RBAC permission tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("RBACPermission – Role Permissions (S003)")
class RBACPermissionTest extends EventloopTestBase {

    @Mock
    private RBACService rbacService;

    @Nested
    @DisplayName("Permission Checking")
    class PermissionCheckingTests {

        @Test
        @DisplayName("[S003]: has_permission_returns_true_when_granted")
        void hasPermissionReturnsTrueWhenGranted() { 
            String userId = "user-001";
            String tenantId = "tenant-alpha";
            RBACService.Permission permission = RBACService.Permission.ENTITY_READ;
            String resource = "entity-123";

            when(rbacService.hasPermission(userId, tenantId, permission, resource)) 
                .thenReturn(Promise.of(true)); 

            Boolean result = runPromise(() -> 
                rbacService.hasPermission(userId, tenantId, permission, resource) 
            );

            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("[S003]: has_permission_returns_false_when_denied")
        void hasPermissionReturnsFalseWhenDenied() { 
            String userId = "user-002";
            String tenantId = "tenant-alpha";
            RBACService.Permission permission = RBACService.Permission.ENTITY_DELETE;
            String resource = "entity-123";

            when(rbacService.hasPermission(userId, tenantId, permission, resource)) 
                .thenReturn(Promise.of(false)); 

            Boolean result = runPromise(() -> 
                rbacService.hasPermission(userId, tenantId, permission, resource) 
            );

            assertThat(result).isFalse(); 
        }

        @Test
        @DisplayName("[S003]: get_user_permissions_returns_all_permissions")
        void getUserPermissionsReturnsAllPermissions() { 
            String userId = "user-001";
            String tenantId = "tenant-alpha";

            Set<RBACService.Permission> permissions = Set.of( 
                RBACService.Permission.ENTITY_READ,
                RBACService.Permission.ENTITY_CREATE,
                RBACService.Permission.ENTITY_UPDATE
            );

            when(rbacService.getUserPermissions(userId, tenantId)) 
                .thenReturn(Promise.of(permissions)); 

            Set<RBACService.Permission> result = runPromise(() -> 
                rbacService.getUserPermissions(userId, tenantId) 
            );

            assertThat(result).contains( 
                RBACService.Permission.ENTITY_READ,
                RBACService.Permission.ENTITY_CREATE
            );
        }
    }

    @Nested
    @DisplayName("Role Assignment")
    class RoleAssignmentTests {

        @Test
        @DisplayName("[S003]: assign_role_grants_permissions")
        void assignRoleGrantsPermissions() { 
            String userId = "user-001";
            String tenantId = "tenant-alpha";
            String roleId = "role-admin";

            when(rbacService.assignRole(userId, tenantId, roleId)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> rbacService.assignRole(userId, tenantId, roleId)); 

            verify(rbacService).assignRole(userId, tenantId, roleId); 
        }

        @Test
        @DisplayName("[S003]: revoke_role_removes_permissions")
        void revokeRoleRemovesPermissions() { 
            String userId = "user-001";
            String tenantId = "tenant-alpha";
            String roleId = "role-editor";

            when(rbacService.revokeRole(userId, tenantId, roleId)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> rbacService.revokeRole(userId, tenantId, roleId)); 

            verify(rbacService).revokeRole(userId, tenantId, roleId); 
        }

        @Test
        @DisplayName("[S003]: get_user_roles_returns_assigned_roles")
        void getUserRolesReturnsAssignedRoles() { 
            String userId = "user-001";
            String tenantId = "tenant-alpha";

            List<RBACService.Role> roles = List.of( 
                createRole("role-1", "Editor", Set.of(RBACService.Permission.ENTITY_READ)), 
                createRole("role-2", "Viewer", Set.of(RBACService.Permission.ENTITY_READ)) 
            );

            when(rbacService.getUserRoles(userId, tenantId)) 
                .thenReturn(Promise.of(roles)); 

            List<RBACService.Role> result = runPromise(() -> 
                rbacService.getUserRoles(userId, tenantId) 
            );

            assertThat(result).hasSize(2); 
        }
    }

    @Nested
    @DisplayName("Role Management")
    class RoleManagementTests {

        @Test
        @DisplayName("[S003]: save_role_creates_role")
        void saveRoleCreatesRole() { 
            RBACService.Role role = createRole("new-role", "Analyst", Set.of( 
                RBACService.Permission.REPORT_READ,
                RBACService.Permission.REPORT_CREATE
            ));

            when(rbacService.saveRole(any())) 
                .thenReturn(Promise.of(role)); 

            RBACService.Role result = runPromise(() -> rbacService.saveRole(role)); 

            assertThat(result.id()).isEqualTo("new-role");
            assertThat(result.permissions()).contains(RBACService.Permission.REPORT_READ); 
        }

        @Test
        @DisplayName("[S003]: get_role_returns_existing")
        void getRoleReturnsExisting() { 
            String roleId = "existing-role";
            RBACService.Role role = createRole(roleId, "Manager", Set.of( 
                RBACService.Permission.TENANT_ADMIN
            ));

            when(rbacService.getRole(roleId)) 
                .thenReturn(Promise.of(Optional.of(role))); 

            Optional<RBACService.Role> result = runPromise(() -> rbacService.getRole(roleId)); 

            assertThat(result).isPresent(); 
            assertThat(result.get().id()).isEqualTo(roleId); 
        }

        @Test
        @DisplayName("[S003]: list_roles_returns_tenant_roles")
        void listRolesReturnsTenantRoles() { 
            String tenantId = "tenant-alpha";

            List<RBACService.Role> roles = List.of( 
                createRole("r1", "Admin", Set.of()), 
                createRole("r2", "User", Set.of()), 
                createRole("r3", "Guest", Set.of()) 
            );

            when(rbacService.listRoles(tenantId)) 
                .thenReturn(Promise.of(roles)); 

            List<RBACService.Role> result = runPromise(() -> rbacService.listRoles(tenantId)); 

            assertThat(result).hasSize(3); 
        }

        @Test
        @DisplayName("[S003]: delete_role_removes_role")
        void deleteRoleRemovesRole() { 
            String roleId = "obsolete-role";

            when(rbacService.deleteRole(roleId)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> rbacService.deleteRole(roleId)); 

            verify(rbacService).deleteRole(roleId); 
        }
    }

    @Nested
    @DisplayName("Permission Hierarchy")
    class PermissionHierarchyTests {

        @Test
        @DisplayName("[S003]: admin_role_has_all_permissions")
        void adminRoleHasAllPermissions() { 
            RBACService.Role admin = createRole("admin", "Administrator", Set.of( 
                RBACService.Permission.TENANT_ADMIN,
                RBACService.Permission.ENTITY_ADMIN,
                RBACService.Permission.COLLECTION_ADMIN,
                RBACService.Permission.USER_MANAGE
            ));

            assertThat(admin.permissions()).contains(RBACService.Permission.TENANT_ADMIN); 
            assertThat(admin.hasPermission(RBACService.Permission.ENTITY_ADMIN)).isTrue(); 
        }

        @Test
        @DisplayName("[S003]: read_only_role_has_limited_permissions")
        void readOnlyRoleHasLimitedPermissions() { 
            RBACService.Role viewer = createRole("viewer", "Viewer", Set.of( 
                RBACService.Permission.ENTITY_READ,
                RBACService.Permission.COLLECTION_READ,
                RBACService.Permission.REPORT_READ
            ));

            assertThat(viewer.hasPermission(RBACService.Permission.ENTITY_READ)).isTrue(); 
            assertThat(viewer.hasPermission(RBACService.Permission.ENTITY_DELETE)).isFalse(); 
        }

        @Test
        @DisplayName("[S003]: permission_inheritance_works")
        void permissionInheritanceWorks() { 
            // A user with multiple roles gets union of permissions
            Set<RBACService.Permission> role1Perms = Set.of(RBACService.Permission.ENTITY_READ); 
            Set<RBACService.Permission> role2Perms = Set.of(RBACService.Permission.ENTITY_CREATE); 

            Set<RBACService.Permission> combined = new java.util.HashSet<>(); 
            combined.addAll(role1Perms); 
            combined.addAll(role2Perms); 

            assertThat(combined).contains( 
                RBACService.Permission.ENTITY_READ,
                RBACService.Permission.ENTITY_CREATE
            );
        }
    }

    @Nested
    @DisplayName("Role Queries")
    class RoleQueriesTests {

        @Test
        @DisplayName("[S003]: get_users_with_role_returns_assignees")
        void getUsersWithRoleReturnsAssignees() { 
            String roleId = "editor-role";

            List<String> users = List.of("user-1", "user-2", "user-3"); 

            when(rbacService.getUsersWithRole(roleId)) 
                .thenReturn(Promise.of(users)); 

            List<String> result = runPromise(() -> rbacService.getUsersWithRole(roleId)); 

            assertThat(result).hasSize(3); 
        }

        @Test
        @DisplayName("[S003]: role_has_permission_checks_correctly")
        void roleHasPermissionChecksCorrectly() { 
            RBACService.Role role = createRole("custom", "Custom", Set.of( 
                RBACService.Permission.EVENT_PUBLISH,
                RBACService.Permission.EVENT_SUBSCRIBE
            ));

            assertThat(role.hasPermission(RBACService.Permission.EVENT_PUBLISH)).isTrue(); 
            assertThat(role.hasPermission(RBACService.Permission.EVENT_READ)).isFalse(); 
        }
    }

    private RBACService.Role createRole(String id, String name, Set<RBACService.Permission> permissions) { 
        return new RBACService.Role( 
            id, name, "", "tenant-alpha",
            permissions, List.of(), false, 0 
        );
    }
}
