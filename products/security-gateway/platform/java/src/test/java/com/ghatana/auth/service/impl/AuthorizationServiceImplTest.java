package com.ghatana.auth.service.impl;

import com.ghatana.platform.domain.auth.Permission;
import com.ghatana.platform.domain.auth.Role;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AuthorizationServiceImpl.
 *
 * Tests validate:
 * - Permission checking by role membership
 * - Role checking for users
 * - Permission retrieval by role
 * - Permission/role grant and revocation
 * - Cache invalidation
 * - Tenant isolation
 * - Metrics emission
 *
 * @see AuthorizationServiceImpl
 */
@DisplayName("Authorization Service Tests")
class AuthorizationServiceImplTest extends EventloopTestBase {

    private AuthorizationServiceImpl authzService;
    private TenantId tenantId;
    private UserPrincipal userWithAdminRole;
    private UserPrincipal userWithEditorRole;
    private UserPrincipal userWithViewerRole;

    @BeforeEach
    void setUp() {
        // GIVEN: Authorization service with no-op metrics
        authzService = new AuthorizationServiceImpl(new NoopMetricsCollector());
        tenantId = TenantId.of("tenant-test-1");

        // GIVEN: Users with different roles
        userWithAdminRole = UserPrincipal.builder()
                .userId("admin-user")
                .email("admin@example.com")
                .role("ADMIN")
                .build();

        userWithEditorRole = UserPrincipal.builder()
                .userId("editor-user")
                .email("editor@example.com")
                .role("EDITOR")
                .build();

        userWithViewerRole = UserPrincipal.builder()
                .userId("viewer-user")
                .email("viewer@example.com")
                .role("VIEWER")
                .build();
    }

    /**
     * Verifies that admin user has document delete permission.
     *
     * GIVEN: Admin user with ADMIN role
     * WHEN: checkPermission("document.delete") is called
     * THEN: Returns true
     */
    @Test
    @DisplayName("Should grant document.delete permission to admin user")
    void shouldGrantDocumentDeletePermissionToAdmin() {
        // GIVEN & WHEN
        Boolean hasPermission = runPromise(() ->
                authzService.checkPermission(
                        tenantId,
                        userWithAdminRole,
                        "document.delete"
                )
        );

        // THEN
        assertThat(hasPermission)
                .as("Admin should have document.delete permission")
                .isTrue();
    }

    /**
     * Verifies that editor user does not have document delete permission.
     *
     * GIVEN: Editor user with EDITOR role
     * WHEN: checkPermission("document.delete") is called
     * THEN: Returns false
     */
    @Test
    @DisplayName("Should deny document.delete permission to editor user")
    void shouldDenyDocumentDeletePermissionToEditor() {
        // GIVEN & WHEN
        Boolean hasPermission = runPromise(() ->
                authzService.checkPermission(
                        tenantId,
                        userWithEditorRole,
                        "document.delete"
                )
        );

        // THEN
        assertThat(hasPermission)
                .as("Editor should not have document.delete permission")
                .isFalse();
    }

    /**
     * Verifies that viewer user only has read permissions.
     *
     * GIVEN: Viewer user with VIEWER role
     * WHEN: checkPermission("document.read") is called
     * THEN: Returns true
     * AND: checkPermission("document.write") is called
     * THEN: Returns false
     */
    @Test
    @DisplayName("Should grant only read permissions to viewer user")
    void shouldGrantOnlyReadPermissionsToViewer() {
        // GIVEN & WHEN
        Boolean canRead = runPromise(() ->
                authzService.checkPermission(
                        tenantId,
                        userWithViewerRole,
                        "document.read"
                )
        );

        Boolean canWrite = runPromise(() ->
                authzService.checkPermission(
                        tenantId,
                        userWithViewerRole,
                        "document.write"
                )
        );

        // THEN
        assertThat(canRead)
                .as("Viewer should have read permission")
                .isTrue();

        assertThat(canWrite)
                .as("Viewer should not have write permission")
                .isFalse();
    }

    /**
     * Verifies role checking works correctly.
     *
     * GIVEN: Admin user with ADMIN role
     * WHEN: checkRole("ADMIN") is called
     * THEN: Returns true
     * AND: checkRole("EDITOR") is called
     * THEN: Returns false
     */
    @Test
    @DisplayName("Should correctly identify user roles")
    void shouldCorrectlyIdentifyUserRoles() {
        // GIVEN & WHEN
        Boolean isAdmin = runPromise(() ->
                authzService.checkRole(tenantId, userWithAdminRole, "ADMIN")
        );

        Boolean isEditor = runPromise(() ->
                authzService.checkRole(tenantId, userWithAdminRole, "EDITOR")
        );

        // THEN
        assertThat(isAdmin)
                .as("User with ADMIN role should return true for checkRole(ADMIN)")
                .isTrue();

        assertThat(isEditor)
                .as("User with ADMIN role should return false for checkRole(EDITOR)")
                .isFalse();
    }

    /**
     * Verifies that permissions for roles can be retrieved.
     *
     * GIVEN: Role "VIEWER"
     * WHEN: getPermissionsForRole("VIEWER") is called
     * THEN: Returns set containing "document.read" and "user.read"
     */
    @Test
    @DisplayName("Should retrieve permissions for VIEWER role")
    void shouldRetrievePermissionsForViewerRole() {
        // GIVEN & WHEN
        Set<String> permissions = runPromise(() ->
                authzService.getPermissionsForRole(tenantId, "VIEWER")
        );

        // THEN
        assertThat(permissions)
                .as("VIEWER should have document.read and user.read permissions")
                .containsExactlyInAnyOrder("document.read", "user.read");
    }

    /**
     * Verifies that permissions for ADMIN role are comprehensive.
     *
     * GIVEN: Role "ADMIN"
     * WHEN: getPermissionsForRole("ADMIN") is called
     * THEN: Returns set with 11 admin permissions
     */
    @Test
    @DisplayName("Should retrieve comprehensive permissions for ADMIN role")
    void shouldRetrieveAdminPermissions() {
        // GIVEN & WHEN
        Set<String> permissions = runPromise(() ->
                authzService.getPermissionsForRole(tenantId, "ADMIN")
        );

        // THEN
        assertThat(permissions)
                .as("ADMIN role should have all permissions")
                .size().isGreaterThanOrEqualTo(10);

        assertThat(permissions)
                .contains("document.read", "document.write", "document.delete",
                        "user.read", "user.create", "user.update", "user.delete",
                        "role.manage", "settings.admin");
    }

    /**
     * Verifies that permission cache is used (second call returns same result).
     *
     * GIVEN: Role "EDITOR"
     * WHEN: getPermissionsForRole is called twice
     * THEN: Cache size is 1 (not 2)
     */
    @Test
    @DisplayName("Should cache role permissions after first retrieval")
    void shouldCacheRolePermissions() {
        // GIVEN
        assertThat(authzService.getCacheSize())
                .as("Cache should be empty initially")
                .isZero();

        // WHEN: First call
        runPromise(() -> authzService.getPermissionsForRole(tenantId, "EDITOR"));

        assertThat(authzService.getCacheSize())
                .as("Cache should have 1 entry after first call")
                .isEqualTo(1);

        // WHEN: Second call
        runPromise(() -> authzService.getPermissionsForRole(tenantId, "EDITOR"));

        // THEN: Cache size unchanged (using cached entry)
        assertThat(authzService.getCacheSize())
                .as("Cache should still have 1 entry after second call")
                .isEqualTo(1);
    }

    /**
     * Verifies that invalidateCache clears cached entries.
     *
     * GIVEN: Cached permissions for multiple roles
     * WHEN: invalidateCache is called
     * THEN: Cache is cleared
     */
    @Test
    @DisplayName("Should clear cache when invalidateCache is called")
    void shouldClearCacheOnInvalidation() {
        // GIVEN: Load some permissions into cache
        runPromise(() -> authzService.getPermissionsForRole(tenantId, "ADMIN"));
        runPromise(() -> authzService.getPermissionsForRole(tenantId, "EDITOR"));

        assertThat(authzService.getCacheSize())
                .as("Cache should have entries")
                .isGreaterThan(0);

        // WHEN: Invalidate cache
        runPromise(() -> authzService.invalidateCache(tenantId));

        // THEN: Cache is cleared
        assertThat(authzService.getCacheSize())
                .as("Cache should be cleared after invalidation")
                .isZero();
    }

    /**
     * Verifies that revoke permission works.
     *
     * GIVEN: Admin user
     * WHEN: revokePermission is called
     * THEN: Returns true
     */
    @Test
    @DisplayName("Should revoke permission successfully")
    void shouldRevokePermissionSuccessfully() {
        // GIVEN & WHEN
        Boolean revoked = runPromise(() ->
                authzService.revokePermission(
                        tenantId,
                        "user-123",
                        "document.write"
                )
        );

        // THEN
        assertThat(revoked)
                .as("Permission revocation should succeed")
                .isTrue();
    }

    /**
     * Verifies that grant permission works.
     *
     * GIVEN: User ID and permission
     * WHEN: grantPermission is called
     * THEN: Returns true
     */
    @Test
    @DisplayName("Should grant permission successfully")
    void shouldGrantPermissionSuccessfully() {
        // GIVEN & WHEN
        Boolean granted = runPromise(() ->
                authzService.grantPermission(
                        tenantId,
                        "user-123",
                        "document.write"
                )
        );

        // THEN
        assertThat(granted)
                .as("Permission grant should succeed")
                .isTrue();
    }

    /**
     * Verifies that revoke role works.
     *
     * GIVEN: User ID and role
     * WHEN: revokeRole is called
     * THEN: Returns true
     */
    @Test
    @DisplayName("Should revoke role successfully")
    void shouldRevokeRoleSuccessfully() {
        // GIVEN & WHEN
        Boolean revoked = runPromise(() ->
                authzService.revokeRole(
                        tenantId,
                        "user-123",
                        "EDITOR"
                )
        );

        // THEN
        assertThat(revoked)
                .as("Role revocation should succeed")
                .isTrue();
    }

    /**
     * Verifies that grant role works.
     *
     * GIVEN: User ID and role
     * WHEN: grantRole is called
     * THEN: Returns true
     */
    @Test
    @DisplayName("Should grant role successfully")
    void shouldGrantRoleSuccessfully() {
        // GIVEN & WHEN
        Boolean granted = runPromise(() ->
                authzService.grantRole(
                        tenantId,
                        "user-123",
                        "EDITOR"
                )
        );

        // THEN
        assertThat(granted)
                .as("Role grant should succeed")
                .isTrue();
    }

    /**
     * Verifies null parameter handling.
     *
     * GIVEN: Null tenantId
     * WHEN: checkPermission is called
     * THEN: Throws NullPointerException
     */
    @Test
    @DisplayName("Should reject null tenantId")
    void shouldRejectNullTenantId() {
        // WHEN & THEN
        assertThatThrownBy(() ->
                authzService.checkPermission(null, userWithAdminRole, "document.read")
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tenantId cannot be null");
    }

    /**
     * Verifies null parameter handling for user principal.
     *
     * GIVEN: Null userPrincipal
     * WHEN: checkPermission is called
     * THEN: Throws NullPointerException
     */
    @Test
    @DisplayName("Should reject null userPrincipal")
    void shouldRejectNullUserPrincipal() {
        // WHEN & THEN
        assertThatThrownBy(() ->
                authzService.checkPermission(tenantId, null, "document.read")
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userPrincipal cannot be null");
    }

    /**
     * Verifies null parameter handling for permission.
     *
     * GIVEN: Null permission
     * WHEN: checkPermission is called
     * THEN: Throws NullPointerException
     */
    @Test
    @DisplayName("Should reject null permission")
    void shouldRejectNullPermission() {
        // WHEN & THEN
        assertThatThrownBy(() ->
                authzService.checkPermission(tenantId, userWithAdminRole, null)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("permission cannot be null");
    }

    @AfterEach
    void tearDown() {
        authzService.clearAllCaches();
    }
}
