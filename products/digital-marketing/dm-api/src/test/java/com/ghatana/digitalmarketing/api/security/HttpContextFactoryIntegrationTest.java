package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("HTTP Context Factory Integration Tests")
class HttpContextFactoryIntegrationTest {

    @Test
    @DisplayName("production mode derives identity from provider")
    void productionModeDerivesIdentityFromProvider() {
        DmosHttpContextFactory.IdentityProvider identityProvider = (token, tenantId) ->
            new DmosHttpContextFactory.IdentityProvider.IdentityResult(
                "user123",
                "session456",
                Set.of("admin"),
                Set.of("dmos.ai_optimization"),
                true
            );

        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, identityProvider);
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/next-best-action-recommendations")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer valid-token")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .build();

        DmOperationContext context = factory.buildContext(request, "ws-1", false);

        assertEquals("user123", context.getActor().getPrincipalId());
        assertEquals("tenant-123", context.getTenantId().getValue());
        assertEquals("ws-1", context.getWorkspaceId().getValue());
    }

    @Test
    @DisplayName("write operations require idempotency key")
    void writeOperationsRequireIdempotencyKey() {
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer dev-token")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .withHeader(HttpHeaders.of("X-Permissions"), "dmos.campaigns")
            .build();

        assertThrows(IllegalArgumentException.class, () -> factory.buildContext(request, "ws-1", true));
    }

    @Test
    @DisplayName("campaign routes reject principals without campaign capability")
    void campaignRoutesRejectWithoutCapability() {
        DmosHttpContextFactory.IdentityProvider identityProvider = (token, tenantId) ->
            new DmosHttpContextFactory.IdentityProvider.IdentityResult(
                "user123",
                "session456",
                Set.of("operator"),
                Set.of("dmos.strategy"),
                true
            );

        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, identityProvider);
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer valid-token")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .build();

        assertThrows(SecurityException.class, () -> factory.buildContext(request, "ws-1", false));
    }

    @Test
    @DisplayName("optimization routes reject principals without optimization capability")
    void optimizationRoutesRejectWithoutCapability() {
        DmosHttpContextFactory.IdentityProvider identityProvider = (token, tenantId) ->
            new DmosHttpContextFactory.IdentityProvider.IdentityResult(
                "user123",
                "session456",
                Set.of("operator"),
                Set.of("dmos.campaigns"),
                true
            );

        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, identityProvider);
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/next-best-action-recommendations")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer valid-token")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .build();

        assertThrows(SecurityException.class, () -> factory.buildContext(request, "ws-1", false));
    }
}
