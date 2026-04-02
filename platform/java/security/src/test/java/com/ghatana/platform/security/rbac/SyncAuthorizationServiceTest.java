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
    void setUp() {
        registry = new InMemoryRolePermissionRegistry();
        registry.registerRole("ADMIN", Set.of("read", "write", "delete", "manage"));
        registry.registerRole("USER", Set.of("read", "write"));
        registry.registerRole("VIEWER", Set.of("read"));
        authService = new SyncAuthorizationService(registry);
    }

    // ── hasPermission ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("hasPermission returns true when user has the permission via a role")
    void hasPermissionTrueWhenUserHasRole() {
        User user = new User("u1", "admin", Set.of("ADMIN"));
        assertThat(authService.hasPermission(user, "delete")).isTrue();
    }

    @Test
    @DisplayName("hasPermission returns false when no role grants the permission")
    void hasPermissionFalseWhenNoRoleGrants() {
        User user = new User("u2", "viewer", Set.of("VIEWER"));
        assertThat(authService.hasPermission(user, "delete")).isFalse();
    }

    @Test
    @DisplayName("hasPermission returns false for null user")
    void hasPermissionFalseForNullUser() {
        assertThat(authService.hasPermission(null, "read")).isFalse();
    }

    @Test
    @DisplayName("hasPermission returns false for null permission")
    void hasPermissionFalseForNullPermission() {
        User user = new User("u3", "admin", Set.of("ADMIN"));
        assertThat(authService.hasPermission(user, null)).isFalse();
    }

    // ── hasAnyPermission ──────────────────────────────────────────────────────

    @Test
    @DisplayName("hasAnyPermission returns true when user has at least one of the permissions")
    void hasAnyPermissionTrueWhenOneMatches() {
        User user = new User("u4", "user", Set.of("USER"));
        assertThat(authService.hasAnyPermission(user, "delete", "write")).isTrue();
    }

    @Test
    @DisplayName("hasAnyPermission returns false when user has none of the permissions")
    void hasAnyPermissionFalseWhenNoneMatch() {
        User user = new User("u5", "viewer", Set.of("VIEWER"));
        assertThat(authService.hasAnyPermission(user, "delete", "manage")).isFalse();
    }

    @Test
    @DisplayName("hasAnyPermission returns false for null user")
    void hasAnyPermissionFalseForNullUser() {
        assertThat(authService.hasAnyPermission(null, "read")).isFalse();
    }

    @Test
    @DisplayName("hasAnyPermission returns false for empty permissions vararg")
    void hasAnyPermissionFalseForEmptyPermissions() {
        User user = new User("u6", "admin", Set.of("ADMIN"));
        assertThat(authService.hasAnyPermission(user)).isFalse();
    }

    // ── hasAllPermissions ─────────────────────────────────────────────────────

    @Test
    @DisplayName("hasAllPermissions returns true when user has all specified permissions")
    void hasAllPermissionsTrueWhenAllPresent() {
        User user = new User("u7", "admin", Set.of("ADMIN"));
        assertThat(authService.hasAllPermissions(user, "read", "write", "delete")).isTrue();
    }

    @Test
    @DisplayName("hasAllPermissions returns false when user lacks any permission")
    void hasAllPermissionsFalseWhenOneMissing() {
        User user = new User("u8", "user", Set.of("USER"));
        assertThat(authService.hasAllPermissions(user, "read", "delete")).isFalse();
    }

    @Test
    @DisplayName("hasAllPermissions returns false for null user")
    void hasAllPermissionsFalseForNullUser() {
        assertThat(authService.hasAllPermissions(null, "read")).isFalse();
    }

    // ── requirePermission ─────────────────────────────────────────────────────

    @Test
    @DisplayName("requirePermission does not throw when user has the permission")
    void requirePermissionNoThrowWhenAuthorized() {
        User user = new User("u9", "admin", Set.of("ADMIN"));
        assertThatCode(() -> authService.requirePermission(user, "manage")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requirePermission throws AccessDeniedException when user lacks permission")
    void requirePermissionThrowsWhenUnauthorized() {
        User user = new User("u10", "viewer", Set.of("VIEWER"));
        assertThatThrownBy(() -> authService.requirePermission(user, "delete"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── requireAnyPermission ──────────────────────────────────────────────────

    @Test
    @DisplayName("requireAnyPermission does not throw when user has at least one")
    void requireAnyPermissionNoThrowWhenOneMatches() {
        User user = new User("u11", "user", Set.of("USER"));
        assertThatCode(() -> authService.requireAnyPermission(user, "delete", "write"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireAnyPermission throws AccessDeniedException when none match")
    void requireAnyPermissionThrowsWhenNoneMatch() {
        User user = new User("u12", "viewer", Set.of("VIEWER"));
        assertThatThrownBy(() -> authService.requireAnyPermission(user, "delete", "manage"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── getAllPermissions ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllPermissions returns union of permissions from all user roles")
    void getAllPermissionsReturnsUnion() {
        User user = new User("u13", "power", Set.of("USER", "VIEWER"));
        Set<String> allPerms = authService.getAllPermissions(user);
        assertThat(allPerms).contains("read", "write");
    }

    @Test
    @DisplayName("getAllPermissions returns empty set for null user")
    void getAllPermissionsEmptyForNullUser() {
        assertThat(authService.getAllPermissions(null)).isEmpty();
    }

    // ── isAdmin ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isAdmin returns true when user has ADMIN role")
    void isAdminTrueForAdminRole() {
        User user = new User("u14", "admin", Set.of("ADMIN"));
        assertThat(authService.isAdmin(user)).isTrue();
    }

    @Test
    @DisplayName("isAdmin returns true when user has OWNER role")
    void isAdminTrueForOwnerRole() {
        User user = new User("u15", "owner", Set.of("OWNER"));
        assertThat(authService.isAdmin(user)).isTrue();
    }

    @Test
    @DisplayName("isAdmin returns false for regular user role")
    void isAdminFalseForUserRole() {
        User user = new User("u16", "regular", Set.of("USER"));
        assertThat(authService.isAdmin(user)).isFalse();
    }

    @Test
    @DisplayName("isAdmin returns false for null user")
    void isAdminFalseForNullUser() {
        assertThat(authService.isAdmin(null)).isFalse();
    }

    // ── Constructor guard ─────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws NullPointerException for null registry")
    void constructorThrowsForNullRegistry() {
        assertThatThrownBy(() -> new SyncAuthorizationService(null))
                .isInstanceOf(NullPointerException.class);
    }
}
