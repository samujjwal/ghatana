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
@DisplayName("SyncAuthorizationService - Phase 3 Expansion [GH-90000]")
class SyncAuthorizationServiceExpansionTest {

    private InMemoryRolePermissionRegistry registry;
    private SyncAuthorizationService authService;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new InMemoryRolePermissionRegistry(); // GH-90000
        // Standard roles
        registry.registerRole("ADMIN", Set.of("read", "write", "delete", "manage")); // GH-90000
        registry.registerRole("EDITOR", Set.of("read", "write", "publish")); // GH-90000
        registry.registerRole("USER", Set.of("read", "write")); // GH-90000
        registry.registerRole("VIEWER", Set.of("read [GH-90000]"));

        authService = new SyncAuthorizationService(registry); // GH-90000
    }

    // ============================================
    // MULTI-ROLE PERMISSION UNION (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Role Permission Union [GH-90000]")
    class MultiRoleTests {

        @Test
        @DisplayName("User with multiple roles gets union of all role permissions [GH-90000]")
        void multiRoleUnion() { // GH-90000
            // User has both VIEWER and EDITOR roles
            User user = new User("u1", "multi-role", Set.of("VIEWER", "EDITOR")); // GH-90000

            // Should have permissions from both roles
            assertThat(authService.hasPermission(user, "read")).isTrue();      // from VIEWER // GH-90000
            assertThat(authService.hasPermission(user, "write")).isTrue();     // from EDITOR // GH-90000
            assertThat(authService.hasPermission(user, "publish")).isTrue();   // from EDITOR // GH-90000
            assertThat(authService.hasPermission(user, "delete")).isFalse();   // in neither role // GH-90000
        }

        @Test
        @DisplayName("Order of roles doesn't matter for permission evaluation [GH-90000]")
        void roleOrderIndependent() { // GH-90000
            User user1 = new User("u2", "role-order-1", Set.of("ADMIN", "VIEWER")); // GH-90000
            User user2 = new User("u3", "role-order-2", Set.of("VIEWER", "ADMIN")); // GH-90000

            // Both users should have the same permissions regardless of role order
            assertThat(authService.hasPermission(user1, "delete")).isTrue(); // GH-90000
            assertThat(authService.hasPermission(user2, "delete")).isTrue(); // GH-90000

            assertThat(authService.hasPermission(user1, "read")).isTrue(); // GH-90000
            assertThat(authService.hasPermission(user2, "read")).isTrue(); // GH-90000
        }
    }

    // ============================================
    // EMPTY/NULL ROLE HANDLING (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Empty/Null Role Handling [GH-90000]")
    class EmptyRoleTests {

        @Test
        @DisplayName("User with empty role set has no permissions [GH-90000]")
        void emptyRoleSetNoPermissions() { // GH-90000
            User user = new User("u4", "no-roles", Set.of()); // GH-90000

            assertThat(authService.hasPermission(user, "read")).isFalse(); // GH-90000
            assertThat(authService.hasPermission(user, "write")).isFalse(); // GH-90000
            // When querying with at least one permission on user without roles
            assertThat(authService.hasAnyPermission(user, "read", "write")).isFalse(); // GH-90000
        }
    }

    // ============================================
    // PERMISSION VARARGS COMBINATIONS (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Permission Varargs Combinations [GH-90000]")
    class VarargTests {

        @Test
        @DisplayName("User with mixed permissions satisfies complex queries [GH-90000]")
        void mixedPermissionQueries() { // GH-90000
            // ADMIN has: read, write, delete, manage
            // VIEWER has: read
            User user = new User("u5", "admin-viewer", Set.of("ADMIN", "VIEWER")); // GH-90000

            // All permissions present
            assertThat(authService.hasAllPermissions(user, "read", "write", "delete")) // GH-90000
                .isTrue(); // GH-90000

            // Any of these satisfied
            assertThat(authService.hasAnyPermission(user, "publish", "delete")) // GH-90000
                .isTrue(); // GH-90000

            // Single permission
            assertThat(authService.hasPermission(user, "manage")).isTrue(); // GH-90000

            // Missing permission
            assertThat(authService.hasPermission(user, "publish")).isFalse(); // GH-90000
        }
    }

    // ============================================
    // DYNAMIC ROLE MANAGEMENT (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Dynamic Role Management [GH-90000]")
    class DynamicRoleTests {

        @Test
        @DisplayName("Adding new roles updates permission evaluation [GH-90000]")
        void dynamicRoleAddition() { // GH-90000
            // Create a new role after service initialization
            registry.registerRole("MODERATOR", Set.of("read", "write", "delete")); // GH-90000

            User user = new User("u6", "moderator", Set.of("MODERATOR [GH-90000]"));

            // Should immediately have permissions from new role
            assertThat(authService.hasPermission(user, "read")).isTrue(); // GH-90000
            assertThat(authService.hasPermission(user, "delete")).isTrue(); // GH-90000
            assertThat(authService.hasPermission(user, "manage")).isFalse(); // GH-90000
        }
    }

    // ============================================
    // PERMISSION PRECEDENCE (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Permission Precedence [GH-90000]")
    class PrecedenceTests {

        @Test
        @DisplayName("Permissions are correctly accumulated across multiple roles [GH-90000]")
        void permissionAccumulation() { // GH-90000
            // Create overlapping roles
            registry.registerRole("DATA_READER", Set.of("read:data", "read:logs")); // GH-90000
            registry.registerRole("DATA_WRITER", Set.of("write:data [GH-90000]"));
            registry.registerRole("AUDITOR", Set.of("read:logs", "read:audit")); // GH-90000

            User auditor = new User("u7", "auditor", Set.of("AUDITOR", "DATA_READER")); // GH-90000

            // Should have union of all permissions
            assertThat(authService.hasPermission(auditor, "read:data")).isTrue();      // from DATA_READER // GH-90000
            assertThat(authService.hasPermission(auditor, "read:logs")).isTrue();      // from both // GH-90000
            assertThat(authService.hasPermission(auditor, "read:audit")).isTrue();     // from AUDITOR // GH-90000
            assertThat(authService.hasPermission(auditor, "write:data")).isFalse();    // not in any role // GH-90000
        }
    }
}
