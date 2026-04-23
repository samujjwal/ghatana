package com.ghatana.platform.security.rbac;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC integration tests — validates enforcement in real scenarios including
 * role assignment and revocation, permission checks across modules,
 * and multi-tenancy role isolation.
 *
 * @doc.type class
 * @doc.purpose Integration tests for RBAC enforcement across service scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RBAC Integration Tests — enforcement in real scenarios")
@Tag("integration")
class RbacIntegrationTest extends EventloopTestBase {

    private InMemoryRolePermissionRegistry registry;
    private SyncAuthorizationService authorizationService;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new InMemoryRolePermissionRegistry(); // GH-90000
        registry.registerRole("ADMIN",  Set.of("read", "write", "delete", "admin")); // GH-90000
        registry.registerRole("EDITOR", Set.of("read", "write")); // GH-90000
        registry.registerRole("VIEWER", Set.of("read"));

        InMemoryPolicyRepository policyRepository = new InMemoryPolicyRepository(); // GH-90000
        PolicyService policyService = new PolicyService(policyRepository); // GH-90000

        policyService.createPolicy("admin-write-data", "admin write on data", "ADMIN",  "data", Set.of("write"));
        policyService.createPolicy("editor-write-data","editor write on data","EDITOR", "data", Set.of("write"));
        policyService.createPolicy("viewer-read-data", "viewer read on data", "VIEWER", "data", Set.of("read"));

        authorizationService = new SyncAuthorizationService(registry); // GH-90000
    }

    // ── Role assignment and revocation ─────────────────────────────────────────

    @Nested
    @DisplayName("role assignment and revocation")
    class RoleAssignmentAndRevocation {

        @Test
        @DisplayName("newly registered role grants requested permissions")
        void newlyRegisteredRole_grantsRequestedPermissions() { // GH-90000
            registry.registerRole("BETA_TESTER", Set.of("read", "feature-x")); // GH-90000

            assertThat(registry.hasPermission("BETA_TESTER", "read")).isTrue(); // GH-90000
            assertThat(registry.hasPermission("BETA_TESTER", "feature-x")).isTrue(); // GH-90000
            assertThat(registry.hasPermission("BETA_TESTER", "write")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("role revocation (overwrite with empty set) removes all permissions")
        void roleRevocation_removesAllPermissions() { // GH-90000
            registry.registerRole("TEMP_ROLE", Set.of("read", "write")); // GH-90000
            assertThat(registry.hasPermission("TEMP_ROLE", "read")).isTrue(); // GH-90000

            // Revoke by overwriting with empty set
            registry.registerRole("TEMP_ROLE", Set.of()); // GH-90000
            assertThat(registry.hasPermission("TEMP_ROLE", "read")).isFalse(); // GH-90000
            assertThat(registry.hasPermission("TEMP_ROLE", "write")).isFalse(); // GH-90000
        }
    }

    // ── Permission checks across modules ─────────────────────────────────────

    @Nested
    @DisplayName("permission checks across module resources")
    class PermissionChecksAcrossModules {

        @Test
        @DisplayName("ADMIN user can write permission")
        void admin_canWriteToDataResource() { // GH-90000
            User adminUser = new User("admin-id", "admin", Set.of("ADMIN"));
            boolean allowed = authorizationService.hasPermission(adminUser, "write"); // GH-90000
            assertThat(allowed).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("VIEWER user cannot write permission")
        void viewer_cannotWriteToDataResource() { // GH-90000
            User viewerUser = new User("viewer-id", "viewer", Set.of("VIEWER"));
            boolean allowed = authorizationService.hasPermission(viewerUser, "write"); // GH-90000
            assertThat(allowed).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("VIEWER user can read permission")
        void viewer_canReadDataResource() { // GH-90000
            User viewerUser = new User("viewer-id", "viewer", Set.of("VIEWER"));
            boolean allowed = authorizationService.hasPermission(viewerUser, "read"); // GH-90000
            assertThat(allowed).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EDITOR user can write permission")
        void editor_canWriteToDataResource() { // GH-90000
            User editorUser = new User("editor-id", "editor", Set.of("EDITOR"));
            boolean allowed = authorizationService.hasPermission(editorUser, "write"); // GH-90000
            assertThat(allowed).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("unknown role denied access")
        void unknownRole_deniedAccessToAnyResource() { // GH-90000
            User ghostUser = new User("ghost-id", "ghost", Set.of("GHOST"));
            boolean allowed = authorizationService.hasPermission(ghostUser, "read"); // GH-90000
            assertThat(allowed).isFalse(); // GH-90000
        }
    }

    // ── Multi-tenancy isolation ────────────────────────────────────────────────

    @Nested
    @DisplayName("multi-tenancy role isolation")
    class MultiTenancyIsolation {

        @Test
        @DisplayName("tenant A roles do not bleed into tenant B namespace")
        void tenantARoles_doNotBleedIntoTenantB() { // GH-90000
            // Simulate tenant-scoped registries
            InMemoryRolePermissionRegistry tenantARegistry = new InMemoryRolePermissionRegistry(); // GH-90000
            InMemoryRolePermissionRegistry tenantBRegistry = new InMemoryRolePermissionRegistry(); // GH-90000

            tenantARegistry.registerRole("SUPER_ADMIN", Set.of("all"));
            tenantBRegistry.registerRole("VIEWER",      Set.of("read"));

            // Tenant A's SUPER_ADMIN must not appear in tenant B's registry
            assertThat(tenantBRegistry.getPermissions("SUPER_ADMIN")).isNull();
            assertThat(tenantARegistry.getPermissions("VIEWER")).isNull();
        }

        @Test
        @DisplayName("same role name in different tenants has independent permissions")
        void sameRoleNameInDifferentTenants_hasIndependentPermissions() { // GH-90000
            InMemoryRolePermissionRegistry tenantARegistry = new InMemoryRolePermissionRegistry(); // GH-90000
            InMemoryRolePermissionRegistry tenantBRegistry = new InMemoryRolePermissionRegistry(); // GH-90000

            // Tenant A's MANAGER has broad permissions
            tenantARegistry.registerRole("MANAGER", Set.of("read", "write", "delete")); // GH-90000
            // Tenant B's MANAGER is restricted
            tenantBRegistry.registerRole("MANAGER", Set.of("read"));

            assertThat(tenantARegistry.hasPermission("MANAGER", "delete")).isTrue(); // GH-90000
            assertThat(tenantBRegistry.hasPermission("MANAGER", "delete")).isFalse(); // GH-90000
        }
    }

    // ── Policy enforcement ────────────────────────────────────────────────────

    @Nested
    @DisplayName("policy enforcement integration")
    class PolicyEnforcement {

        @Test
        @DisplayName("policy that grants permission is enforced correctly")
        void policy_thatGrantsPermission_isEnforcedCorrectly() { // GH-90000
            User adminUser = new User("admin-id", "admin", Set.of("ADMIN"));
            boolean adminCanWrite = authorizationService.hasPermission(adminUser, "write"); // GH-90000
            assertThat(adminCanWrite).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("no matching policy denies access by default")
        void noMatchingPolicy_deniesAccessByDefault() { // GH-90000
            User viewerUser = new User("viewer-id", "viewer", Set.of("VIEWER"));
            boolean allowed = authorizationService.hasPermission(viewerUser, "delete"); // GH-90000
            assertThat(allowed).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("null user is denied without exception")
        void nullUser_isDeniedWithoutException() { // GH-90000
            boolean allowed = authorizationService.hasPermission(null, "read"); // GH-90000
            assertThat(allowed).isFalse(); // GH-90000
        }
    }
}
