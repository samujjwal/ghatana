package com.ghatana.platform.security.rbac;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for InMemoryRolePermissionRegistry role registration and permission lookup
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("InMemoryRolePermissionRegistry — role registration and permission lookup")
class InMemoryRolePermissionRegistryTest {

    private InMemoryRolePermissionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRolePermissionRegistry();
    }

    // ── getPermissions ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPermissions returns null for unknown role")
    void getPermissionsNullForUnknownRole() {
        assertThat(registry.getPermissions("UNKNOWN")).isNull();
    }

    @Test
    @DisplayName("getPermissions returns registered permissions for known role")
    void getPermissionsReturnsRegisteredPermissions() {
        registry.registerRole("ADMIN", Set.of("read", "write", "delete"));
        Set<String> permissions = registry.getPermissions("ADMIN");

        assertThat(permissions).containsExactlyInAnyOrder("read", "write", "delete");
    }

    // ── registerRole ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerRole null role throws IllegalArgumentException")
    void registerRoleNullRoleThrows() {
        assertThatThrownBy(() -> registry.registerRole(null, Set.of("read")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("registerRole empty role throws IllegalArgumentException")
    void registerRoleEmptyRoleThrows() {
        assertThatThrownBy(() -> registry.registerRole("", Set.of("read")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("registerRole null permissions throws IllegalArgumentException")
    void registerRoleNullPermissionsThrows() {
        assertThatThrownBy(() -> registry.registerRole("USER", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("registerRole stores an immutable copy of permissions")
    void registerRoleStoresImmutableCopy() {
        Set<String> mutable = new java.util.HashSet<>();
        mutable.add("read");
        registry.registerRole("USER", mutable);

        mutable.add("write"); // mutate original

        // Registry copy should not contain "write"
        assertThat(registry.getPermissions("USER")).containsOnly("read");
    }

    @Test
    @DisplayName("re-registering a role overwrites previous permissions")
    void reRegisteringRoleOverwritesPrevious() {
        registry.registerRole("EDITOR", Set.of("read"));
        registry.registerRole("EDITOR", Set.of("read", "publish"));

        assertThat(registry.getPermissions("EDITOR")).containsExactlyInAnyOrder("read", "publish");
    }

    // ── hasPermission ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasPermission returns true when role has the permission")
    void hasPermissionReturnsTrueWhenPresent() {
        registry.registerRole("MODERATOR", Set.of("read", "moderate"));
        assertThat(registry.hasPermission("MODERATOR", "moderate")).isTrue();
    }

    @Test
    @DisplayName("hasPermission returns false when role lacks the permission")
    void hasPermissionReturnsFalseWhenAbsent() {
        registry.registerRole("VIEWER", Set.of("read"));
        assertThat(registry.hasPermission("VIEWER", "delete")).isFalse();
    }

    @Test
    @DisplayName("hasPermission returns false for unknown role")
    void hasPermissionReturnsFalseForUnknownRole() {
        assertThat(registry.hasPermission("GHOST", "read")).isFalse();
    }

    // ── Multiple roles ────────────────────────────────────────────────────────

    @Test
    @DisplayName("multiple roles can coexist with independent permissions")
    void multipleRolesCoexistIndependently() {
        registry.registerRole("ADMIN", Set.of("read", "write", "delete"));
        registry.registerRole("USER", Set.of("read"));

        assertThat(registry.getPermissions("ADMIN")).contains("delete");
        assertThat(registry.getPermissions("USER")).doesNotContain("delete");
    }
}
