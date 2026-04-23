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
    void setUp() { // GH-90000
        registry = new InMemoryRolePermissionRegistry(); // GH-90000
    }

    // ── getPermissions ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPermissions returns null for unknown role")
    void getPermissionsNullForUnknownRole() { // GH-90000
        assertThat(registry.getPermissions("UNKNOWN")).isNull();
    }

    @Test
    @DisplayName("getPermissions returns registered permissions for known role")
    void getPermissionsReturnsRegisteredPermissions() { // GH-90000
        registry.registerRole("ADMIN", Set.of("read", "write", "delete")); // GH-90000
        Set<String> permissions = registry.getPermissions("ADMIN");

        assertThat(permissions).containsExactlyInAnyOrder("read", "write", "delete"); // GH-90000
    }

    // ── registerRole ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerRole null role throws IllegalArgumentException")
    void registerRoleNullRoleThrows() { // GH-90000
        assertThatThrownBy(() -> registry.registerRole(null, Set.of("read")))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("registerRole empty role throws IllegalArgumentException")
    void registerRoleEmptyRoleThrows() { // GH-90000
        assertThatThrownBy(() -> registry.registerRole("", Set.of("read")))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("registerRole null permissions throws IllegalArgumentException")
    void registerRoleNullPermissionsThrows() { // GH-90000
        assertThatThrownBy(() -> registry.registerRole("USER", null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("registerRole stores an immutable copy of permissions")
    void registerRoleStoresImmutableCopy() { // GH-90000
        Set<String> mutable = new java.util.HashSet<>(); // GH-90000
        mutable.add("read");
        registry.registerRole("USER", mutable); // GH-90000

        mutable.add("write"); // mutate original

        // Registry copy should not contain "write"
        assertThat(registry.getPermissions("USER")).containsOnly("read");
    }

    @Test
    @DisplayName("re-registering a role overwrites previous permissions")
    void reRegisteringRoleOverwritesPrevious() { // GH-90000
        registry.registerRole("EDITOR", Set.of("read"));
        registry.registerRole("EDITOR", Set.of("read", "publish")); // GH-90000

        assertThat(registry.getPermissions("EDITOR")).containsExactlyInAnyOrder("read", "publish");
    }

    // ── hasPermission ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasPermission returns true when role has the permission")
    void hasPermissionReturnsTrueWhenPresent() { // GH-90000
        registry.registerRole("MODERATOR", Set.of("read", "moderate")); // GH-90000
        assertThat(registry.hasPermission("MODERATOR", "moderate")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("hasPermission returns false when role lacks the permission")
    void hasPermissionReturnsFalseWhenAbsent() { // GH-90000
        registry.registerRole("VIEWER", Set.of("read"));
        assertThat(registry.hasPermission("VIEWER", "delete")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("hasPermission returns false for unknown role")
    void hasPermissionReturnsFalseForUnknownRole() { // GH-90000
        assertThat(registry.hasPermission("GHOST", "read")).isFalse(); // GH-90000
    }

    // ── Multiple roles ────────────────────────────────────────────────────────

    @Test
    @DisplayName("multiple roles can coexist with independent permissions")
    void multipleRolesCoexistIndependently() { // GH-90000
        registry.registerRole("ADMIN", Set.of("read", "write", "delete")); // GH-90000
        registry.registerRole("USER", Set.of("read"));

        assertThat(registry.getPermissions("ADMIN")).contains("delete");
        assertThat(registry.getPermissions("USER")).doesNotContain("delete");
    }
}
