package com.ghatana.yappc.api;

import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.security.rbac.RolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import io.activej.http.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies generated route manifest entries are present in the backend authorization registry
 * @doc.layer test
 * @doc.pattern ContractTest
 */
@DisplayName("Route authorization registry parity")
class RouteAuthorizationRegistryParityTest {

    private final RouteAuthorizationRegistry registry = new RouteAuthorizationRegistry(
            new YappcAuthorizationService(new SyncAuthorizationService(new RolePermissionRegistry() {
                @Override
                public Set<String> getPermissions(String role) {
                    return Set.of(
                            Permission.ADMIN_SYSTEM,
                            Permission.WORKSPACE_READ,
                            Permission.PROJECT_READ,
                            Permission.PROJECT_UPDATE
                    );
                }

                @Override
                public void registerRole(String role, Set<String> permissions) {
                }
            }))
    );

    @Test
    @DisplayName("phase packet routes match manifest authorization contract")
    void phasePacketRoutesMatchManifestAuthorizationContract() {
        assertRoute(
                HttpMethod.GET,
                "/api/v1/phase/packet",
                "getPhasePacket",
                Permission.PROJECT_READ,
                RouteAuthorizationRegistry.ResourceScope.PROJECT,
                RouteAuthorizationRegistry.PrivacyClassification.CONFIDENTIAL
        );
        assertRoute(
                HttpMethod.POST,
                "/api/v1/phase/packet",
                "requestPhasePacket",
                Permission.PROJECT_READ,
                RouteAuthorizationRegistry.ResourceScope.PROJECT,
                RouteAuthorizationRegistry.PrivacyClassification.CONFIDENTIAL
        );
    }

    @Test
    @DisplayName("dashboard action routes match manifest authorization contract")
    void dashboardActionRoutesMatchManifestAuthorizationContract() {
        assertRoute(
                HttpMethod.GET,
                "/api/v1/dashboard/actions",
                "getDashboardActions",
                Permission.WORKSPACE_READ,
                RouteAuthorizationRegistry.ResourceScope.WORKSPACE,
                RouteAuthorizationRegistry.PrivacyClassification.CONFIDENTIAL
        );
        assertRoute(
                HttpMethod.POST,
                "/api/v1/dashboard/actions",
                "requestDashboardActions",
                Permission.WORKSPACE_READ,
                RouteAuthorizationRegistry.ResourceScope.WORKSPACE,
                RouteAuthorizationRegistry.PrivacyClassification.CONFIDENTIAL
        );
    }

    private void assertRoute(
            HttpMethod method,
            String path,
            String action,
            String permission,
            RouteAuthorizationRegistry.ResourceScope resourceScope,
            RouteAuthorizationRegistry.PrivacyClassification privacy
    ) {
        RouteAuthorizationRegistry.RouteDefinition definition = registry.getRouteDefinition(method, path);
        assertThat(definition).as("%s %s", method, path).isNotNull();
        assertThat(definition.action()).isEqualTo(action);
        assertThat(definition.requiredPermission()).isEqualTo(permission);
        assertThat(definition.resourceScope()).isEqualTo(resourceScope);
        assertThat(definition.privacyClassification()).isEqualTo(privacy);
    }
}
