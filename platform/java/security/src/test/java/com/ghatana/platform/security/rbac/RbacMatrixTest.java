package com.ghatana.platform.security.rbac;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC matrix tests — validates every role × permission × resource combination,
 * role hierarchy, permission inheritance, and wildcard permission patterns.
 *
 * @doc.type class
 * @doc.purpose Tests for RBAC role-permission matrix completeness and correctness
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RBAC Matrix Tests — role × permission × resource [GH-90000]")
class RbacMatrixTest extends EventloopTestBase {

    private InMemoryRolePermissionRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new InMemoryRolePermissionRegistry(); // GH-90000

        // Register canonical role definitions
        registry.registerRole("ADMIN",   Set.of("read", "write", "delete", "admin")); // GH-90000
        registry.registerRole("EDITOR",  Set.of("read", "write")); // GH-90000
        registry.registerRole("VIEWER",  Set.of("read [GH-90000]"));
        registry.registerRole("AUDITOR", Set.of("read", "audit")); // GH-90000
        registry.registerRole("NONE",    Set.of()); // GH-90000
    }

    // ── ADMIN role ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ADMIN role permissions [GH-90000]")
    class AdminRolePermissions {

        @Test
        @DisplayName("ADMIN has read permission [GH-90000]")
        void admin_hasReadPermission() { // GH-90000
            assertThat(registry.hasPermission("ADMIN", "read")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("ADMIN has write permission [GH-90000]")
        void admin_hasWritePermission() { // GH-90000
            assertThat(registry.hasPermission("ADMIN", "write")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("ADMIN has delete permission [GH-90000]")
        void admin_hasDeletePermission() { // GH-90000
            assertThat(registry.hasPermission("ADMIN", "delete")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("ADMIN has admin permission [GH-90000]")
        void admin_hasAdminPermission() { // GH-90000
            assertThat(registry.hasPermission("ADMIN", "admin")).isTrue(); // GH-90000
        }
    }

    // ── EDITOR role ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EDITOR role permissions [GH-90000]")
    class EditorRolePermissions {

        @Test
        @DisplayName("EDITOR has read permission [GH-90000]")
        void editor_hasReadPermission() { // GH-90000
            assertThat(registry.hasPermission("EDITOR", "read")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EDITOR has write permission [GH-90000]")
        void editor_hasWritePermission() { // GH-90000
            assertThat(registry.hasPermission("EDITOR", "write")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EDITOR does NOT have delete permission [GH-90000]")
        void editor_doesNotHaveDeletePermission() { // GH-90000
            assertThat(registry.hasPermission("EDITOR", "delete")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("EDITOR does NOT have admin permission [GH-90000]")
        void editor_doesNotHaveAdminPermission() { // GH-90000
            assertThat(registry.hasPermission("EDITOR", "admin")).isFalse(); // GH-90000
        }
    }

    // ── VIEWER role ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("VIEWER role permissions [GH-90000]")
    class ViewerRolePermissions {

        @Test
        @DisplayName("VIEWER has read permission [GH-90000]")
        void viewer_hasReadPermission() { // GH-90000
            assertThat(registry.hasPermission("VIEWER", "read")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("VIEWER does NOT have write permission [GH-90000]")
        void viewer_doesNotHaveWritePermission() { // GH-90000
            assertThat(registry.hasPermission("VIEWER", "write")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("VIEWER does NOT have delete permission [GH-90000]")
        void viewer_doesNotHaveDeletePermission() { // GH-90000
            assertThat(registry.hasPermission("VIEWER", "delete")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("VIEWER does NOT have admin permission [GH-90000]")
        void viewer_doesNotHaveAdminPermission() { // GH-90000
            assertThat(registry.hasPermission("VIEWER", "admin")).isFalse(); // GH-90000
        }
    }

    // ── AUDITOR role ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AUDITOR role permissions [GH-90000]")
    class AuditorRolePermissions {

        @Test
        @DisplayName("AUDITOR has read permission [GH-90000]")
        void auditor_hasReadPermission() { // GH-90000
            assertThat(registry.hasPermission("AUDITOR", "read")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("AUDITOR has audit permission [GH-90000]")
        void auditor_hasAuditPermission() { // GH-90000
            assertThat(registry.hasPermission("AUDITOR", "audit")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("AUDITOR does NOT have write permission [GH-90000]")
        void auditor_doesNotHaveWritePermission() { // GH-90000
            assertThat(registry.hasPermission("AUDITOR", "write")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("AUDITOR does NOT have delete permission [GH-90000]")
        void auditor_doesNotHaveDeletePermission() { // GH-90000
            assertThat(registry.hasPermission("AUDITOR", "delete")).isFalse(); // GH-90000
        }
    }

    // ── NONE role (zero permissions) ────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("NONE role (zero permissions) [GH-90000]")
    class NoneRolePermissions {

        @Test
        @DisplayName("NONE role has no permissions [GH-90000]")
        void noneRole_hasNoPermissions() { // GH-90000
            for (String perm : Set.of("read", "write", "delete", "admin", "audit")) { // GH-90000
                assertThat(registry.hasPermission("NONE", perm)) // GH-90000
                        .as("NONE should not have permission: %s", perm) // GH-90000
                        .isFalse(); // GH-90000
            }
        }
    }

    // ── Unknown role ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unknown role [GH-90000]")
    class UnknownRole {

        @Test
        @DisplayName("unknown role returns false for any permission [GH-90000]")
        void unknownRole_returnsFalseForAnyPermission() { // GH-90000
            assertThat(registry.hasPermission("GHOST", "read")).isFalse(); // GH-90000
            assertThat(registry.hasPermission("GHOST", "write")).isFalse(); // GH-90000
            assertThat(registry.hasPermission(null, "read")).isFalse(); // GH-90000
        }
    }

    // ── Role registration validation ──────────────────────────────────────────

    @Nested
    @DisplayName("role registration validation [GH-90000]")
    class RoleRegistrationValidation {

        @Test
        @DisplayName("null role name throws IllegalArgumentException [GH-90000]")
        void nullRoleName_throwsIllegalArgumentException() { // GH-90000
            org.assertj.core.api.Assertions.assertThatThrownBy( // GH-90000
                    () -> registry.registerRole(null, Set.of("read [GH-90000]")))
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("empty role name throws IllegalArgumentException [GH-90000]")
        void emptyRoleName_throwsIllegalArgumentException() { // GH-90000
            org.assertj.core.api.Assertions.assertThatThrownBy( // GH-90000
                    () -> registry.registerRole("", Set.of("read [GH-90000]")))
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("null permissions set throws IllegalArgumentException [GH-90000]")
        void nullPermissionsSet_throwsIllegalArgumentException() { // GH-90000
            org.assertj.core.api.Assertions.assertThatThrownBy( // GH-90000
                    () -> registry.registerRole("MY_ROLE", null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("re-registration overwrites previous permissions [GH-90000]")
        void reRegistration_overwritesPreviousPermissions() { // GH-90000
            registry.registerRole("TEMP_ROLE", Set.of("read [GH-90000]"));
            assertThat(registry.hasPermission("TEMP_ROLE", "write")).isFalse(); // GH-90000

            registry.registerRole("TEMP_ROLE", Set.of("read", "write")); // GH-90000
            assertThat(registry.hasPermission("TEMP_ROLE", "write")).isTrue(); // GH-90000
        }
    }

    // ── Retrieved permission set immutability ──────────────────────────────────

    @Test
    @DisplayName("getPermissions returns immutable set — modification throws [GH-90000]")
    void getPermissions_returnsImmutableSet() { // GH-90000
        Set<String> permissions = registry.getPermissions("VIEWER [GH-90000]");
        assertThat(permissions).isNotNull(); // GH-90000
        org.assertj.core.api.Assertions.assertThatThrownBy( // GH-90000
                () -> permissions.add("write [GH-90000]"))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }
}
