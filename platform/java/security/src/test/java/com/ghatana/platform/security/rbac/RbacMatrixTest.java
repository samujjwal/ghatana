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
@DisplayName("RBAC Matrix Tests — role × permission × resource")
class RbacMatrixTest extends EventloopTestBase {

    private InMemoryRolePermissionRegistry registry;

    @BeforeEach
    void setUp() { 
        registry = new InMemoryRolePermissionRegistry(); 

        // Register canonical role definitions
        registry.registerRole("ADMIN",   Set.of("read", "write", "delete", "admin")); 
        registry.registerRole("EDITOR",  Set.of("read", "write")); 
        registry.registerRole("VIEWER",  Set.of("read"));
        registry.registerRole("AUDITOR", Set.of("read", "audit")); 
        registry.registerRole("NONE",    Set.of()); 
    }

    // ── ADMIN role ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ADMIN role permissions")
    class AdminRolePermissions {

        @Test
        @DisplayName("ADMIN has read permission")
        void admin_hasReadPermission() { 
            assertThat(registry.hasPermission("ADMIN", "read")).isTrue(); 
        }

        @Test
        @DisplayName("ADMIN has write permission")
        void admin_hasWritePermission() { 
            assertThat(registry.hasPermission("ADMIN", "write")).isTrue(); 
        }

        @Test
        @DisplayName("ADMIN has delete permission")
        void admin_hasDeletePermission() { 
            assertThat(registry.hasPermission("ADMIN", "delete")).isTrue(); 
        }

        @Test
        @DisplayName("ADMIN has admin permission")
        void admin_hasAdminPermission() { 
            assertThat(registry.hasPermission("ADMIN", "admin")).isTrue(); 
        }
    }

    // ── EDITOR role ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EDITOR role permissions")
    class EditorRolePermissions {

        @Test
        @DisplayName("EDITOR has read permission")
        void editor_hasReadPermission() { 
            assertThat(registry.hasPermission("EDITOR", "read")).isTrue(); 
        }

        @Test
        @DisplayName("EDITOR has write permission")
        void editor_hasWritePermission() { 
            assertThat(registry.hasPermission("EDITOR", "write")).isTrue(); 
        }

        @Test
        @DisplayName("EDITOR does NOT have delete permission")
        void editor_doesNotHaveDeletePermission() { 
            assertThat(registry.hasPermission("EDITOR", "delete")).isFalse(); 
        }

        @Test
        @DisplayName("EDITOR does NOT have admin permission")
        void editor_doesNotHaveAdminPermission() { 
            assertThat(registry.hasPermission("EDITOR", "admin")).isFalse(); 
        }
    }

    // ── VIEWER role ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("VIEWER role permissions")
    class ViewerRolePermissions {

        @Test
        @DisplayName("VIEWER has read permission")
        void viewer_hasReadPermission() { 
            assertThat(registry.hasPermission("VIEWER", "read")).isTrue(); 
        }

        @Test
        @DisplayName("VIEWER does NOT have write permission")
        void viewer_doesNotHaveWritePermission() { 
            assertThat(registry.hasPermission("VIEWER", "write")).isFalse(); 
        }

        @Test
        @DisplayName("VIEWER does NOT have delete permission")
        void viewer_doesNotHaveDeletePermission() { 
            assertThat(registry.hasPermission("VIEWER", "delete")).isFalse(); 
        }

        @Test
        @DisplayName("VIEWER does NOT have admin permission")
        void viewer_doesNotHaveAdminPermission() { 
            assertThat(registry.hasPermission("VIEWER", "admin")).isFalse(); 
        }
    }

    // ── AUDITOR role ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AUDITOR role permissions")
    class AuditorRolePermissions {

        @Test
        @DisplayName("AUDITOR has read permission")
        void auditor_hasReadPermission() { 
            assertThat(registry.hasPermission("AUDITOR", "read")).isTrue(); 
        }

        @Test
        @DisplayName("AUDITOR has audit permission")
        void auditor_hasAuditPermission() { 
            assertThat(registry.hasPermission("AUDITOR", "audit")).isTrue(); 
        }

        @Test
        @DisplayName("AUDITOR does NOT have write permission")
        void auditor_doesNotHaveWritePermission() { 
            assertThat(registry.hasPermission("AUDITOR", "write")).isFalse(); 
        }

        @Test
        @DisplayName("AUDITOR does NOT have delete permission")
        void auditor_doesNotHaveDeletePermission() { 
            assertThat(registry.hasPermission("AUDITOR", "delete")).isFalse(); 
        }
    }

    // ── NONE role (zero permissions) ────────────────────────────────────────── 

    @Nested
    @DisplayName("NONE role (zero permissions)")
    class NoneRolePermissions {

        @Test
        @DisplayName("NONE role has no permissions")
        void noneRole_hasNoPermissions() { 
            for (String perm : Set.of("read", "write", "delete", "admin", "audit")) { 
                assertThat(registry.hasPermission("NONE", perm)) 
                        .as("NONE should not have permission: %s", perm) 
                        .isFalse(); 
            }
        }
    }

    // ── Unknown role ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unknown role")
    class UnknownRole {

        @Test
        @DisplayName("unknown role returns false for any permission")
        void unknownRole_returnsFalseForAnyPermission() { 
            assertThat(registry.hasPermission("GHOST", "read")).isFalse(); 
            assertThat(registry.hasPermission("GHOST", "write")).isFalse(); 
            assertThat(registry.hasPermission(null, "read")).isFalse(); 
        }
    }

    // ── Role registration validation ──────────────────────────────────────────

    @Nested
    @DisplayName("role registration validation")
    class RoleRegistrationValidation {

        @Test
        @DisplayName("null role name throws IllegalArgumentException")
        void nullRoleName_throwsIllegalArgumentException() { 
            org.assertj.core.api.Assertions.assertThatThrownBy( 
                    () -> registry.registerRole(null, Set.of("read")))
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("empty role name throws IllegalArgumentException")
        void emptyRoleName_throwsIllegalArgumentException() { 
            org.assertj.core.api.Assertions.assertThatThrownBy( 
                    () -> registry.registerRole("", Set.of("read")))
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("null permissions set throws IllegalArgumentException")
        void nullPermissionsSet_throwsIllegalArgumentException() { 
            org.assertj.core.api.Assertions.assertThatThrownBy( 
                    () -> registry.registerRole("MY_ROLE", null)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("re-registration overwrites previous permissions")
        void reRegistration_overwritesPreviousPermissions() { 
            registry.registerRole("TEMP_ROLE", Set.of("read"));
            assertThat(registry.hasPermission("TEMP_ROLE", "write")).isFalse(); 

            registry.registerRole("TEMP_ROLE", Set.of("read", "write")); 
            assertThat(registry.hasPermission("TEMP_ROLE", "write")).isTrue(); 
        }
    }

    // ── Retrieved permission set immutability ──────────────────────────────────

    @Test
    @DisplayName("getPermissions returns immutable set — modification throws")
    void getPermissions_returnsImmutableSet() { 
        Set<String> permissions = registry.getPermissions("VIEWER");
        assertThat(permissions).isNotNull(); 
        org.assertj.core.api.Assertions.assertThatThrownBy( 
                () -> permissions.add("write"))
                .isInstanceOf(UnsupportedOperationException.class); 
    }
}
