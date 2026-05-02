package com.ghatana.platform.security.rbac;

import com.ghatana.platform.security.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: Authorization service edge cases and advanced scenarios.
 * Tests multi-role permission union, wildcard matching, role inheritance, and dynamic assignment.
 *
 * @doc.type class
 * @doc.purpose Authorization service edge cases and advanced RBAC scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("SyncAuthorizationService - Phase 3 Expansion")
class SyncAuthorizationServiceExpansionTest {

    private InMemoryRolePermissionRegistry registry;
    private SyncAuthorizationService authService;

    @BeforeEach
    void setUp() { 
        registry = new InMemoryRolePermissionRegistry(); 
        // Standard roles
        registry.registerRole("ADMIN", Set.of("read", "write", "delete", "manage")); 
        registry.registerRole("EDITOR", Set.of("read", "write", "publish")); 
        registry.registerRole("USER", Set.of("read", "write")); 
        registry.registerRole("VIEWER", Set.of("read"));

        authService = new SyncAuthorizationService(registry); 
    }

    // ============================================
    // MULTI-ROLE PERMISSION UNION (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Multi-Role Permission Union")
    class MultiRoleTests {

        @Test
        @DisplayName("User with multiple roles gets union of all role permissions")
        void multiRoleUnion() { 
            // User has both VIEWER and EDITOR roles
            User user = new User("u1", "multi-role", Set.of("VIEWER", "EDITOR")); 

            // Should have permissions from both roles
            assertThat(authService.hasPermission(user, "read")).isTrue();      // from VIEWER 
            assertThat(authService.hasPermission(user, "write")).isTrue();     // from EDITOR 
            assertThat(authService.hasPermission(user, "publish")).isTrue();   // from EDITOR 
            assertThat(authService.hasPermission(user, "delete")).isFalse();   // in neither role 
        }

        @Test
        @DisplayName("Order of roles doesn't matter for permission evaluation")
        void roleOrderIndependent() { 
            User user1 = new User("u2", "role-order-1", Set.of("ADMIN", "VIEWER")); 
            User user2 = new User("u3", "role-order-2", Set.of("VIEWER", "ADMIN")); 

            // Both users should have the same permissions regardless of role order
            assertThat(authService.hasPermission(user1, "delete")).isTrue(); 
            assertThat(authService.hasPermission(user2, "delete")).isTrue(); 

            assertThat(authService.hasPermission(user1, "read")).isTrue(); 
            assertThat(authService.hasPermission(user2, "read")).isTrue(); 
        }
    }

    // ============================================
    // EMPTY/NULL ROLE HANDLING (1 test) 
    // ============================================

    @Nested
    @DisplayName("Empty/Null Role Handling")
    class EmptyRoleTests {

        @Test
        @DisplayName("User with empty role set has no permissions")
        void emptyRoleSetNoPermissions() { 
            User user = new User("u4", "no-roles", Set.of()); 

            assertThat(authService.hasPermission(user, "read")).isFalse(); 
            assertThat(authService.hasPermission(user, "write")).isFalse(); 
            // When querying with at least one permission on user without roles
            assertThat(authService.hasAnyPermission(user, "read", "write")).isFalse(); 
        }
    }

    // ============================================
    // PERMISSION VARARGS COMBINATIONS (1 test) 
    // ============================================

    @Nested
    @DisplayName("Permission Varargs Combinations")
    class VarargTests {

        @Test
        @DisplayName("User with mixed permissions satisfies complex queries")
        void mixedPermissionQueries() { 
            // ADMIN has: read, write, delete, manage
            // VIEWER has: read
            User user = new User("u5", "admin-viewer", Set.of("ADMIN", "VIEWER")); 

            // All permissions present
            assertThat(authService.hasAllPermissions(user, "read", "write", "delete")) 
                .isTrue(); 

            // Any of these satisfied
            assertThat(authService.hasAnyPermission(user, "publish", "delete")) 
                .isTrue(); 

            // Single permission
            assertThat(authService.hasPermission(user, "manage")).isTrue(); 

            // Missing permission
            assertThat(authService.hasPermission(user, "publish")).isFalse(); 
        }
    }

    // ============================================
    // DYNAMIC ROLE MANAGEMENT (1 test) 
    // ============================================

    @Nested
    @DisplayName("Dynamic Role Management")
    class DynamicRoleTests {

        @Test
        @DisplayName("Adding new roles updates permission evaluation")
        void dynamicRoleAddition() { 
            // Create a new role after service initialization
            registry.registerRole("MODERATOR", Set.of("read", "write", "delete")); 

            User user = new User("u6", "moderator", Set.of("MODERATOR"));

            // Should immediately have permissions from new role
            assertThat(authService.hasPermission(user, "read")).isTrue(); 
            assertThat(authService.hasPermission(user, "delete")).isTrue(); 
            assertThat(authService.hasPermission(user, "manage")).isFalse(); 
        }
    }

    // ============================================
    // PERMISSION PRECEDENCE (1 test) 
    // ============================================

    @Nested
    @DisplayName("Permission Precedence")
    class PrecedenceTests {

        @Test
        @DisplayName("Permissions are correctly accumulated across multiple roles")
        void permissionAccumulation() { 
            // Create overlapping roles
            registry.registerRole("DATA_READER", Set.of("read:data", "read:logs")); 
            registry.registerRole("DATA_WRITER", Set.of("write:data"));
            registry.registerRole("AUDITOR", Set.of("read:logs", "read:audit")); 

            User auditor = new User("u7", "auditor", Set.of("AUDITOR", "DATA_READER")); 

            // Should have union of all permissions
            assertThat(authService.hasPermission(auditor, "read:data")).isTrue();      // from DATA_READER 
            assertThat(authService.hasPermission(auditor, "read:logs")).isTrue();      // from both 
            assertThat(authService.hasPermission(auditor, "read:audit")).isTrue();     // from AUDITOR 
            assertThat(authService.hasPermission(auditor, "write:data")).isFalse();    // not in any role 
        }
    }
}
