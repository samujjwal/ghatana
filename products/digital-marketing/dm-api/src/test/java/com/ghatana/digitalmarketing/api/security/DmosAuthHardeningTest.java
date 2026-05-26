package com.ghatana.digitalmarketing.api.security;

import io.activej.http.HttpRequest;
import io.activej.http.HttpHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0-005: Auth hardening tests to ensure backend enforcement is authoritative.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Unknown actions are rejected with clear errors</li>
 *   <li>Role hierarchy is enforced correctly</li>
 *   <li>Capability checks fail-closed</li>
 *   <li>Spoofed headers cannot grant access in production</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Auth hardening tests for backend enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("P0-005: Auth Hardening Tests")
class DmosAuthHardeningTest {

    @Test
    @DisplayName("Unknown action should throw IllegalArgumentException")
    void unknownAction_shouldThrowIllegalArgumentException() {
        Set<String> roles = Set.of("admin");
        String unknownAction = "unknown-action";

        assertThatThrownBy(() -> DmosActionPermissionRegistry.isActionAllowed(roles, unknownAction))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown DMOS action");
    }

    @Test
    @DisplayName("Viewer role should only allow viewer-level actions")
    void viewerRole_shouldOnlyAllowViewerActions() {
        Set<String> viewerRoles = Set.of("viewer");

        assertThat(DmosActionPermissionRegistry.isActionAllowed(viewerRoles, "view-dashboard")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(viewerRoles, "review-approval")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(viewerRoles, "view-audit-log")).isTrue();

        // Viewer should NOT be able to perform higher-level actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(viewerRoles, "create-campaign")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(viewerRoles, "launch-campaign")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(viewerRoles, "approve-strategy")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(viewerRoles, "approve-budget")).isFalse();
    }

    @Test
    @DisplayName("Brand manager role should allow brand-manager and viewer actions")
    void brandManagerRole_shouldAllowBrandManagerAndViewerActions() {
        Set<String> brandManagerRoles = Set.of("brand-manager");

        // Brand manager can do viewer actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(brandManagerRoles, "view-dashboard")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(brandManagerRoles, "review-approval")).isTrue();

        // Brand manager can do brand-manager actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(brandManagerRoles, "create-campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(brandManagerRoles, "launch-campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(brandManagerRoles, "generate-strategy")).isTrue();

        // Brand manager should NOT be able to do higher-level actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(brandManagerRoles, "approve-strategy")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(brandManagerRoles, "approve-budget")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(brandManagerRoles, "manage-agency")).isFalse();
    }

    @Test
    @DisplayName("Marketing director role should allow marketing-director and lower actions")
    void marketingDirectorRole_shouldAllowMarketingDirectorAndLowerActions() {
        Set<String> marketingDirectorRoles = Set.of("marketing-director");

        // Marketing director can do brand-manager and viewer actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(marketingDirectorRoles, "create-campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(marketingDirectorRoles, "launch-campaign")).isTrue();

        // Marketing director can do marketing-director actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(marketingDirectorRoles, "approve-strategy")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(marketingDirectorRoles, "generate-budget")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(marketingDirectorRoles, "manage-channels")).isTrue();

        // Marketing director should NOT be able to do exec-sponsor or admin actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(marketingDirectorRoles, "approve-budget")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(marketingDirectorRoles, "manage-agency")).isFalse();
    }

    @Test
    @DisplayName("Exec sponsor role should allow exec-sponsor and lower actions")
    void execSponsorRole_shouldAllowExecSponsorAndLowerActions() {
        Set<String> execSponsorRoles = Set.of("exec-sponsor");

        // Exec sponsor can do all lower-level actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(execSponsorRoles, "create-campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(execSponsorRoles, "approve-strategy")).isTrue();

        // Exec sponsor can do exec-sponsor actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(execSponsorRoles, "approve-budget")).isTrue();

        // Exec sponsor should NOT be able to do admin actions
        assertThat(DmosActionPermissionRegistry.isActionAllowed(execSponsorRoles, "manage-agency")).isFalse();
    }

    @Test
    @DisplayName("Admin role should allow all actions")
    void adminRole_shouldAllowAllActions() {
        Set<String> adminRoles = Set.of("admin");

        assertThat(DmosActionPermissionRegistry.isActionAllowed(adminRoles, "view-dashboard")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(adminRoles, "create-campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(adminRoles, "approve-strategy")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(adminRoles, "approve-budget")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(adminRoles, "manage-agency")).isTrue();
    }

    @Test
    @DisplayName("Null roles should fail-closed")
    void nullRoles_shouldFailClosed() {
        Set<String> nullRoles = null;

        assertThat(DmosActionPermissionRegistry.isActionAllowed(nullRoles, "view-dashboard")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(nullRoles, "create-campaign")).isFalse();
    }

    @Test
    @DisplayName("Empty roles should fail-closed")
    void emptyRoles_shouldFailClosed() {
        Set<String> emptyRoles = Set.of();

        assertThat(DmosActionPermissionRegistry.isActionAllowed(emptyRoles, "view-dashboard")).isFalse();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(emptyRoles, "create-campaign")).isFalse();
    }

    @Test
    @DisplayName("Role normalization should handle underscores and spaces")
    void roleNormalization_shouldHandleUnderscoresAndSpaces() {
        Set<String> rolesWithUnderscores = Set.of("brand_manager");
        Set<String> rolesWithSpaces = Set.of("brand manager");

        // Both should be normalized to brand-manager
        assertThat(DmosActionPermissionRegistry.isActionAllowed(rolesWithUnderscores, "create-campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(rolesWithSpaces, "create-campaign")).isTrue();
    }

    @Test
    @DisplayName("Action normalization should be case-insensitive")
    void actionNormalization_shouldBeCaseInsensitive() {
        Set<String> roles = Set.of("brand-manager");

        assertThat(DmosActionPermissionRegistry.isActionAllowed(roles, "CREATE-CAMPAIGN")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(roles, "create_campaign")).isTrue();
        assertThat(DmosActionPermissionRegistry.isActionAllowed(roles, "Create Campaign")).isTrue();
    }

    @Test
    @DisplayName("Production mode should require Authorization header")
    void productionMode_shouldRequireAuthorizationHeader() {
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, new TestIdentityProvider());
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/workspace-123/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .build();

        assertThatThrownBy(() -> factory.buildContext(request, "workspace-123", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Required header missing: Authorization");
    }

    @Test
    @DisplayName("Production mode should reject invalid tokens")
    void productionMode_shouldRejectInvalidTokens() {
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, new TestIdentityProvider());
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/workspace-123/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer invalid-token")
            .build();

        assertThatThrownBy(() -> factory.buildContext(request, "workspace-123", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or expired authentication token");
    }

    @Test
    @DisplayName("Production mode should enforce X-Idempotency-Key for writes")
    void productionMode_shouldEnforceIdempotencyKeyForWrites() {
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, new TestIdentityProvider());
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/workspace-123/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer valid-token")
            .build();

        assertThatThrownBy(() -> factory.buildContext(request, "workspace-123", true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("X-Idempotency-Key header is required for write operations");
    }

    @Test
    @DisplayName("Production mode should derive identity server-side")
    void productionMode_shouldDeriveIdentityServerSide() {
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, new TestIdentityProvider());
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/workspace-123/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer test-token")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "spoofed-principal")  // Should be ignored
            .withHeader(HttpHeaders.of("X-Roles"), "admin")  // Should be ignored
            .withHeader(HttpHeaders.of("X-Permissions"), "*")  // Should be ignored
            .build();

        var context = factory.buildContext(request, "workspace-123", false);

        // Identity should come from the token, not the spoofed headers
        assertThat(context.getActor().getPrincipalId()).isEqualTo("user-from-token");
    }

    @Test
    @DisplayName("Route capability registry should resolve canonical manifest routes")
    void routeCapabilityRegistry_shouldResolveCanonicalManifestRoutes() {
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/workspace-123/campaigns/abc/transition"))
            .isEqualTo("dmos.campaigns");
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/workspace-123/strategy/strategy-1/approve"))
            .isEqualTo("dmos.strategy");
        assertThat(DmosRouteCapabilityRegistry.capabilityForPath("/v1/workspaces/workspace-123/approvals/request-1"))
            .isNull();
    }

    @Test
    @DisplayName("Non-production mode should use client headers as fallback")
    void nonProductionMode_shouldUseClientHeadersAsFallback() {
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/workspace-123/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "test-principal")
            .withHeader(HttpHeaders.of("X-Session-ID"), "test-session")
            .withHeader(HttpHeaders.of("X-Roles"), "brand-manager")
            .build();

        var context = factory.buildContext(request, "workspace-123", false);

        // In non-production, client headers are used as fallback
        assertThat(context.getActor().getPrincipalId()).isEqualTo("test-principal");
    }

    // Test identity provider for production mode tests
    private static class TestIdentityProvider implements DmosHttpContextFactory.IdentityProvider {
        @Override
        public IdentityResult deriveIdentity(String token, String tenantId) {
            if ("valid-token".equals(token) || "test-token".equals(token)) {
                return new IdentityResult(
                    "user-from-token",
                    "session-from-token",
                    Set.of("brand-manager"),
                    Set.of("dmos.campaigns"),
                    true
                );
            }
            return new IdentityResult(null, null, Set.of(), Set.of(), false);
        }
    }
}
