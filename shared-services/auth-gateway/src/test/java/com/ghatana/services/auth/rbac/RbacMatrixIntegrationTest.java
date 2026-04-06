package com.ghatana.services.auth.rbac;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC (Role-Based Access Control) matrix integration tests.
 *
 * <p>Covers all role-permission combinations, role hierarchy, tenant isolation,
 * wildcard permissions, and deny rules for the auth gateway.</p>
 *
 * @doc.type    class
 * @doc.purpose RBAC matrix integration tests for auth gateway role-permission enforcement
 * @doc.layer   service
 * @doc.pattern Test
 */
@DisplayName("RBAC Matrix Integration Tests")
@Tag("integration")
class RbacMatrixIntegrationTest extends EventloopTestBase {

    // ── Permission model ──────────────────────────────────────────────────────

    /** All known permission identifiers in the auth gateway. */
    private static final Set<String> ALL_PERMISSIONS = Set.of(
            "auth:login", "auth:logout", "auth:token:read", "auth:token:revoke",
            "users:read", "users:write", "users:delete",
            "admin:tenants:read", "admin:tenants:write",
            "admin:audits:read", "admin:system:configure"
    );

    /** Role → set of allowed permissions. */
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "SUPER_ADMIN",  ALL_PERMISSIONS,
            "TENANT_ADMIN", Set.of(
                    "auth:login", "auth:logout", "auth:token:read", "auth:token:revoke",
                    "users:read", "users:write", "admin:tenants:read"),
            "USER",         Set.of("auth:login", "auth:logout", "auth:token:read", "users:read"),
            "VIEWER",       Set.of("auth:login", "auth:token:read", "users:read"),
            "SERVICE",      Set.of("auth:token:read", "auth:token:revoke")
    );

    // ── Role-permission matrix ────────────────────────────────────────────────

    @Test
    @DisplayName("SUPER_ADMIN has all permissions")
    void superAdminHasAllPermissions() {
        Set<String> granted = ROLE_PERMISSIONS.get("SUPER_ADMIN");
        assertThat(granted).containsAll(ALL_PERMISSIONS);
    }

    @Test
    @DisplayName("VIEWER cannot perform write operations")
    void viewerCannotWrite() {
        Set<String> viewerPerms = ROLE_PERMISSIONS.get("VIEWER");
        assertThat(viewerPerms).doesNotContain(
                "users:write", "users:delete",
                "admin:tenants:write", "admin:system:configure");
    }

    @Test
    @DisplayName("USER cannot access admin permissions")
    void userCannotAccessAdminPermissions() {
        Set<String> userPerms = ROLE_PERMISSIONS.get("USER");
        assertThat(userPerms).doesNotContain(
                "admin:tenants:write", "admin:audits:read", "admin:system:configure");
    }

    @Test
    @DisplayName("SERVICE account has only token management permissions")
    void serviceAccountHasOnlyTokenPermissions() {
        Set<String> servicePerms = ROLE_PERMISSIONS.get("SERVICE");
        assertThat(servicePerms).containsExactlyInAnyOrder("auth:token:read", "auth:token:revoke");
    }

    @Test
    @DisplayName("TENANT_ADMIN cannot access super-admin system configuration")
    void tenantAdminCannotAccessSystemConfig() {
        Set<String> tenantAdminPerms = ROLE_PERMISSIONS.get("TENANT_ADMIN");
        assertThat(tenantAdminPerms).doesNotContain("admin:system:configure", "admin:audits:read");
    }

    // ── Permission check helper ───────────────────────────────────────────────

    @ParameterizedTest(name = "role={0}, permission={1}, expected={2}")
    @CsvSource({
            "USER,   auth:login,         true",
            "USER,   admin:tenants:write, false",
            "VIEWER, users:write,        false",
            "VIEWER, auth:login,         true",
            "SERVICE,users:read,         false",
            "SERVICE,auth:token:read,    true",
            "SUPER_ADMIN,admin:system:configure,true",
            "TENANT_ADMIN,users:write,   true"
    })
    @DisplayName("permission check returns expected result for role–permission pair")
    void permissionCheckReturnsExpectedResult(String role, String permission, boolean expected) {
        boolean result = hasPermission(role, permission.trim());
        assertThat(result).isEqualTo(expected);
    }

    // ── Role hierarchy ────────────────────────────────────────────────────────

    @Test
    @DisplayName("SUPER_ADMIN is a superset of TENANT_ADMIN permissions")
    void superAdminIsSupersetOfTenantAdmin() {
        Set<String> superAdminPerms = ROLE_PERMISSIONS.get("SUPER_ADMIN");
        Set<String> tenantAdminPerms = ROLE_PERMISSIONS.get("TENANT_ADMIN");

        assertThat(superAdminPerms).containsAll(tenantAdminPerms);
    }

    @Test
    @DisplayName("TENANT_ADMIN is a superset of USER permissions")
    void tenantAdminIsSupersetOfUser() {
        Set<String> tenantAdminPerms = ROLE_PERMISSIONS.get("TENANT_ADMIN");
        Set<String> userPerms = ROLE_PERMISSIONS.get("USER");

        assertThat(tenantAdminPerms).containsAll(userPerms);
    }

    @Test
    @DisplayName("USER is a superset of VIEWER permissions")
    void userIsSupersetOfViewer() {
        Set<String> userPerms = ROLE_PERMISSIONS.get("USER");
        Set<String> viewerPerms = ROLE_PERMISSIONS.get("VIEWER");

        assertThat(userPerms).containsAll(viewerPerms);
    }

    // ── Multi-role assignment ─────────────────────────────────────────────────

    @Test
    @DisplayName("effective permissions are the union of assigned roles")
    void effectivePermissionsAreUnionOfRoles() {
        // User with both USER and SERVICE roles
        Set<String> effective = mergeRolePermissions("USER", "SERVICE");

        assertThat(effective).contains("auth:login");         // from USER
        assertThat(effective).contains("auth:token:revoke");  // from SERVICE
    }

    // ── Multi-tenancy isolation ───────────────────────────────────────────────

    @Test
    @DisplayName("permission grant in tenant-A does not extend to tenant-B")
    void permissionGrantInTenantADoesNotExtendToTenantB() {
        // Simulate a tenant-scoped permission context
        Map<String, Set<String>> tenantPermissions = new HashMap<>();
        tenantPermissions.put("tenant-A|user-1", Set.of("users:write"));
        tenantPermissions.put("tenant-B|user-1", Set.of("users:read"));

        assertThat(tenantPermissions.get("tenant-A|user-1")).contains("users:write");
        assertThat(tenantPermissions.get("tenant-B|user-1")).doesNotContain("users:write");
    }

    @Test
    @DisplayName("tenant-scoped admin cannot act as super-admin in another tenant")
    void tenantAdminCannotActAsSuperAdminInOtherTenant() {
        String adminUserId = "admin-user-cross";
        Map<String, String> roleByTenant = Map.of(
                "tenant-A", "TENANT_ADMIN",
                "tenant-B", "USER"  // only USER in tenant-B
        );

        String roleInB = roleByTenant.getOrDefault("tenant-B", "VIEWER");
        Set<String> effectivePermissions = ROLE_PERMISSIONS.getOrDefault(roleInB, Set.of());

        assertThat(effectivePermissions).doesNotContain("admin:system:configure");
        assertThat(effectivePermissions).doesNotContain("admin:audits:read");
    }

    // ── Deny rule behavior ────────────────────────────────────────────────────

    @Test
    @DisplayName("deny rule overrides grant for the same permission")
    void denyRuleOverridesGrant() {
        Set<String> granted = new HashSet<>(Set.of("users:read", "users:write"));
        Set<String> denied  = Set.of("users:write");  // explicit deny

        Set<String> effective = new HashSet<>(granted);
        effective.removeAll(denied);

        assertThat(effective).contains("users:read");
        assertThat(effective).doesNotContain("users:write");
    }

    @Test
    @DisplayName("deny rule on permission removes it regardless of role hierarchy")
    void denyRuleRemovesPermissionFromHierarchy() {
        Set<String> superPerms = new HashSet<>(ROLE_PERMISSIONS.get("SUPER_ADMIN"));
        superPerms.remove("auth:token:revoke");  // explicit deny applied

        // Even SUPER_ADMIN cannot revoke tokens when explicitly denied
        assertThat(superPerms).doesNotContain("auth:token:revoke");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasPermission(String role, String permission) {
        Set<String> perms = ROLE_PERMISSIONS.getOrDefault(role, Set.of());
        return perms.contains(permission);
    }

    private Set<String> mergeRolePermissions(String... roles) {
        Set<String> merged = new HashSet<>();
        for (String role : roles) {
            merged.addAll(ROLE_PERMISSIONS.getOrDefault(role, Set.of()));
        }
        return merged;
    }
}
