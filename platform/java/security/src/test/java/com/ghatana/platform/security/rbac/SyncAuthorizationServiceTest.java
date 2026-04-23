package com.ghatana.platform.security.rbac;

import com.ghatana.platform.security.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for SyncAuthorizationService permission checking and access control
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("SyncAuthorizationService — synchronous role-based permission checking")
class SyncAuthorizationServiceTest {

    private InMemoryRolePermissionRegistry registry;
    private SyncAuthorizationService authService;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new InMemoryRolePermissionRegistry(); // GH-90000
        registry.registerRole("ADMIN", Set.of("read", "write", "delete", "manage")); // GH-90000
        registry.registerRole("USER", Set.of("read", "write")); // GH-90000
        registry.registerRole("VIEWER", Set.of("read"));
        authService = new SyncAuthorizationService(registry); // GH-90000
    }

    // ── hasPermission ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasPermission returns true when user has the permission via a role")
    void hasPermissionTrueWhenUserHasRole() { // GH-90000
        User user = new User("u1", "admin", Set.of("ADMIN"));
        assertThat(authService.hasPermission(user, "delete")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("hasPermission returns false when no role grants the permission")
    void hasPermissionFalseWhenNoRoleGrants() { // GH-90000
        User user = new User("u2", "viewer", Set.of("VIEWER"));
        assertThat(authService.hasPermission(user, "delete")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("hasPermission returns false for null user")
    void hasPermissionFalseForNullUser() { // GH-90000
        assertThat(authService.hasPermission(null, "read")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("hasPermission returns false for null permission")
    void hasPermissionFalseForNullPermission() { // GH-90000
        User user = new User("u3", "admin", Set.of("ADMIN"));
        assertThat(authService.hasPermission(user, null)).isFalse(); // GH-90000
    }

    // ── hasAnyPermission ──────────────────────────────────────────────────────

    @Test
    @DisplayName("hasAnyPermission returns true when user has at least one of the permissions")
    void hasAnyPermissionTrueWhenOneMatches() { // GH-90000
        User user = new User("u4", "user", Set.of("USER"));
        assertThat(authService.hasAnyPermission(user, "delete", "write")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("hasAnyPermission returns false when user has none of the permissions")
    void hasAnyPermissionFalseWhenNoneMatch() { // GH-90000
        User user = new User("u5", "viewer", Set.of("VIEWER"));
        assertThat(authService.hasAnyPermission(user, "delete", "manage")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("hasAnyPermission returns false for null user")
    void hasAnyPermissionFalseForNullUser() { // GH-90000
        assertThat(authService.hasAnyPermission(null, "read")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("hasAnyPermission returns false for empty permissions vararg")
    void hasAnyPermissionFalseForEmptyPermissions() { // GH-90000
        User user = new User("u6", "admin", Set.of("ADMIN"));
        assertThat(authService.hasAnyPermission(user)).isFalse(); // GH-90000
    }

    // ── hasAllPermissions ─────────────────────────────────────────────────────

    @Test
    @DisplayName("hasAllPermissions returns true when user has all specified permissions")
    void hasAllPermissionsTrueWhenAllPresent() { // GH-90000
        User user = new User("u7", "admin", Set.of("ADMIN"));
        assertThat(authService.hasAllPermissions(user, "read", "write", "delete")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("hasAllPermissions returns false when user lacks any permission")
    void hasAllPermissionsFalseWhenOneMissing() { // GH-90000
        User user = new User("u8", "user", Set.of("USER"));
        assertThat(authService.hasAllPermissions(user, "read", "delete")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("hasAllPermissions returns false for null user")
    void hasAllPermissionsFalseForNullUser() { // GH-90000
        assertThat(authService.hasAllPermissions(null, "read")).isFalse(); // GH-90000
    }

    // ── requirePermission ─────────────────────────────────────────────────────

    @Test
    @DisplayName("requirePermission does not throw when user has the permission")
    void requirePermissionNoThrowWhenAuthorized() { // GH-90000
        User user = new User("u9", "admin", Set.of("ADMIN"));
        assertThatCode(() -> authService.requirePermission(user, "manage")).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("requirePermission throws AccessDeniedException when user lacks permission")
    void requirePermissionThrowsWhenUnauthorized() { // GH-90000
        User user = new User("u10", "viewer", Set.of("VIEWER"));
        assertThatThrownBy(() -> authService.requirePermission(user, "delete")) // GH-90000
                .isInstanceOf(AccessDeniedException.class); // GH-90000
    }

    // ── requireAnyPermission ──────────────────────────────────────────────────

    @Test
    @DisplayName("requireAnyPermission does not throw when user has at least one")
    void requireAnyPermissionNoThrowWhenOneMatches() { // GH-90000
        User user = new User("u11", "user", Set.of("USER"));
        assertThatCode(() -> authService.requireAnyPermission(user, "delete", "write")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("requireAnyPermission throws AccessDeniedException when none match")
    void requireAnyPermissionThrowsWhenNoneMatch() { // GH-90000
        User user = new User("u12", "viewer", Set.of("VIEWER"));
        assertThatThrownBy(() -> authService.requireAnyPermission(user, "delete", "manage")) // GH-90000
                .isInstanceOf(AccessDeniedException.class); // GH-90000
    }

    // ── getAllPermissions ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllPermissions returns union of permissions from all user roles")
    void getAllPermissionsReturnsUnion() { // GH-90000
        User user = new User("u13", "power", Set.of("USER", "VIEWER")); // GH-90000
        Set<String> allPerms = authService.getAllPermissions(user); // GH-90000
        assertThat(allPerms).contains("read", "write"); // GH-90000
    }

    @Test
    @DisplayName("getAllPermissions returns empty set for null user")
    void getAllPermissionsEmptyForNullUser() { // GH-90000
        assertThat(authService.getAllPermissions(null)).isEmpty(); // GH-90000
    }

    // ── isAdmin ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isAdmin returns true when user has ADMIN role")
    void isAdminTrueForAdminRole() { // GH-90000
        User user = new User("u14", "admin", Set.of("ADMIN"));
        assertThat(authService.isAdmin(user)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isAdmin returns true when user has OWNER role")
    void isAdminTrueForOwnerRole() { // GH-90000
        User user = new User("u15", "owner", Set.of("OWNER"));
        assertThat(authService.isAdmin(user)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isAdmin returns false for regular user role")
    void isAdminFalseForUserRole() { // GH-90000
        User user = new User("u16", "regular", Set.of("USER"));
        assertThat(authService.isAdmin(user)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("isAdmin returns false for null user")
    void isAdminFalseForNullUser() { // GH-90000
        assertThat(authService.isAdmin(null)).isFalse(); // GH-90000
    }

    // ── Constructor guard ─────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws NullPointerException for null registry")
    void constructorThrowsForNullRegistry() { // GH-90000
        assertThatThrownBy(() -> new SyncAuthorizationService(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
