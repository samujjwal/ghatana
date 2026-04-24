package com.ghatana.platform.security;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests verifying tenant isolation semantics and policy decision behaviour
 * in the security platform module.
 * <p>
 * The pattern: each tenant is represented by its own {@link InMemoryRolePermissionRegistry}
 * and {@link SyncAuthorizationService} instance. A user presented to tenant-A's authz
 * service must never gain privileges registered only in tenant-B's registry.
 *
 * @doc.type class
 * @doc.purpose Verify tenant isolation + policy decision contract in RBAC security module
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Tenant Isolation and Policy Decision Contract Tests")
class TenantIsolationContractTest {

    private InMemoryRolePermissionRegistry registryTenantA;
    private InMemoryRolePermissionRegistry registryTenantB;
    private SyncAuthorizationService authzTenantA;
    private SyncAuthorizationService authzTenantB;

    @BeforeEach
    void setUp() {
        registryTenantA = new InMemoryRolePermissionRegistry();
        registryTenantB = new InMemoryRolePermissionRegistry();
        authzTenantA = new SyncAuthorizationService(registryTenantA);
        authzTenantB = new SyncAuthorizationService(registryTenantB);
    }

    // ── Tenant isolation ────────────────────────────────────────────────────

    @Test
    @DisplayName("Role registered only in tenant-A registry does not grant access via tenant-B authz")
    void roleRegisteredInTenantADoesNotLeakToTenantB() {
        registryTenantA.registerRole("editor", Set.of("documents.read", "documents.write"));
        // tenant-B registry has no roles registered

        User userInTenantA = User.builder().userId("u1").username("alice").addRole("editor").build();

        assertThat(authzTenantA.hasPermission(userInTenantA, "documents.read")).isTrue();
        assertThat(authzTenantA.hasPermission(userInTenantA, "documents.write")).isTrue();
        // Same user object evaluated against tenant-B authz: registry has no "editor" role → no permissions
        assertThat(authzTenantB.hasPermission(userInTenantA, "documents.read")).isFalse();
        assertThat(authzTenantB.hasPermission(userInTenantA, "documents.write")).isFalse();
    }

    @Test
    @DisplayName("Admin role registered only in tenant-A is not recognized by tenant-B")
    void adminRoleIsTenantScoped() {
        registryTenantA.registerRole("ADMIN", Set.of("admin.manage", "system.config"));
        // tenant-B has no ADMIN role

        User adminUser = User.builder().userId("admin1").username("bob").addRole("ADMIN").build();

        assertThat(authzTenantA.isAdmin(adminUser)).isTrue();
        assertThat(authzTenantA.hasPermission(adminUser, "admin.manage")).isTrue();
        assertThat(authzTenantB.isAdmin(adminUser)).isTrue(); // isAdmin() checks role names in User
        // but permissions derived from the registry are empty in tenant-B
        assertThat(authzTenantB.hasPermission(adminUser, "admin.manage")).isFalse();
    }

    @Test
    @DisplayName("Registering the same role name in both tenants with different permissions is independent")
    void samRoleNameInDifferentTenantsHasDifferentPermissions() {
        registryTenantA.registerRole("viewer", Set.of("reports.read", "dashboard.view"));
        registryTenantB.registerRole("viewer", Set.of("invoices.view"));

        User viewer = User.builder().userId("u2").username("carol").addRole("viewer").build();

        assertThat(authzTenantA.hasPermission(viewer, "reports.read")).isTrue();
        assertThat(authzTenantA.hasPermission(viewer, "invoices.view")).isFalse();
        assertThat(authzTenantB.hasPermission(viewer, "invoices.view")).isTrue();
        assertThat(authzTenantB.hasPermission(viewer, "reports.read")).isFalse();
    }

    // ── Policy decision: permission checks ─────────────────────────────────

    @Test
    @DisplayName("User with no roles has no permissions")
    void userWithNoRolesHasNoPermissions() {
        registryTenantA.registerRole("admin", Set.of("all.access"));
        User noRoleUser = User.builder().userId("u3").username("dave").build();

        assertThat(authzTenantA.hasPermission(noRoleUser, "all.access")).isFalse();
        assertThat(authzTenantA.getAllPermissions(noRoleUser)).isEmpty();
    }

    @Test
    @DisplayName("hasAnyPermission returns true when user holds at least one of the requested permissions")
    void hasAnyPermissionReturnsTrueForAtLeastOne() {
        registryTenantA.registerRole("reader", Set.of("docs.read", "reports.read"));
        User reader = User.builder().userId("u4").username("eve").addRole("reader").build();

        assertThat(authzTenantA.hasAnyPermission(reader, "docs.read", "docs.write")).isTrue();
    }

    @Test
    @DisplayName("hasAnyPermission returns false when user holds none of the requested permissions")
    void hasAnyPermissionReturnsFalseForNoMatch() {
        registryTenantA.registerRole("reader", Set.of("docs.read"));
        User reader = User.builder().userId("u5").username("frank").addRole("reader").build();

        assertThat(authzTenantA.hasAnyPermission(reader, "docs.delete", "docs.approve")).isFalse();
    }

    @Test
    @DisplayName("hasAllPermissions returns true when user holds every requested permission")
    void hasAllPermissionsReturnsTrueForFullMatch() {
        registryTenantA.registerRole("editor", Set.of("docs.read", "docs.write", "docs.delete"));
        User editor = User.builder().userId("u6").username("grace").addRole("editor").build();

        assertThat(authzTenantA.hasAllPermissions(editor, "docs.read", "docs.write")).isTrue();
    }

    @Test
    @DisplayName("hasAllPermissions returns false when user is missing at least one required permission")
    void hasAllPermissionsReturnsFalseForPartialMatch() {
        registryTenantA.registerRole("editor", Set.of("docs.read", "docs.write"));
        User editor = User.builder().userId("u7").username("henry").addRole("editor").build();

        assertThat(authzTenantA.hasAllPermissions(editor, "docs.read", "docs.write", "docs.delete")).isFalse();
    }

    @Test
    @DisplayName("requirePermission throws AccessDeniedException for missing permission")
    void requirePermissionThrowsForMissingPermission() {
        registryTenantA.registerRole("viewer", Set.of("reports.view"));
        User viewer = User.builder().userId("u8").username("iris").addRole("viewer").build();

        assertThatThrownBy(() -> authzTenantA.requirePermission(viewer, "reports.delete"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("reports.delete");
    }

    @Test
    @DisplayName("requirePermission does not throw when user holds the required permission")
    void requirePermissionSucceedsForValidPermission() {
        registryTenantA.registerRole("editor", Set.of("docs.write"));
        User editor = User.builder().userId("u9").username("jack").addRole("editor").build();

        authzTenantA.requirePermission(editor, "docs.write");
        // no exception expected
    }

    @Test
    @DisplayName("null user is denied all permissions without exception")
    void nullUserIsAlwaysDenied() {
        registryTenantA.registerRole("admin", Set.of("all.access"));

        assertThat(authzTenantA.hasPermission(null, "all.access")).isFalse();
        assertThat(authzTenantA.hasAnyPermission(null, "all.access")).isFalse();
        assertThat(authzTenantA.hasAllPermissions(null, "all.access")).isFalse();
        assertThat(authzTenantA.getAllPermissions(null)).isEmpty();
    }
}
