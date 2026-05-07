package com.ghatana.kernel.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RegionRouter}.
 *
 * @doc.type class
 * @doc.purpose Tests RegionRouter generic multi-region routing (KERNEL-P2)
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("RegionRouter Tests")
class RegionRouterTest {

    @Test
    @DisplayName("Should build valid router with primary region")
    void shouldBuildValidRouterWithPrimaryRegion() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .addRegion("eu-west-1", URI.create("https://api-eu-west-1.example.com"))
            .build();

        assertEquals("us-east-1", router.getPrimaryRegion());
        assertTrue(router.isRegionRegistered("us-east-1"));
        assertTrue(router.isRegionRegistered("eu-west-1"));
    }

    @Test
    @DisplayName("Should fail to build router without primary region")
    void shouldFailToBuildRouterWithoutPrimaryRegion() {
        assertThrows(IllegalArgumentException.class, () ->
            RegionRouter.builder()
                .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to build router with non-HTTPS endpoint")
    void shouldFailToBuildRouterWithNonHttpsEndpoint() {
        assertThrows(IllegalArgumentException.class, () ->
            RegionRouter.builder()
                .primaryRegion("us-east-1")
                .addRegion("us-east-1", URI.create("http://api-us-east-1.example.com"))
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to build router if primary not in endpoints")
    void shouldFailToBuildRouterIfPrimaryNotInEndpoints() {
        assertThrows(IllegalArgumentException.class, () ->
            RegionRouter.builder()
                .primaryRegion("us-east-1")
                .addRegion("eu-west-1", URI.create("https://api-eu-west-1.example.com"))
                .build()
        );
    }

    @Test
    @DisplayName("Should resolve endpoint for registered region")
    void shouldResolveEndpointForRegisteredRegion() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .addRegion("eu-west-1", URI.create("https://api-eu-west-1.example.com"))
            .build();

        URI endpoint = router.resolveEndpoint("tenant-1", "workspace-1", "eu-west-1").getResult();

        assertEquals(URI.create("https://api-eu-west-1.example.com"), endpoint);
    }

    @Test
    @DisplayName("Should fall back to primary region for null hint")
    void shouldFallbackToPrimaryRegionForNullHint() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .addRegion("eu-west-1", URI.create("https://api-eu-west-1.example.com"))
            .build();

        URI endpoint = router.resolveEndpoint("tenant-1", "workspace-1", null).getResult();

        assertEquals(URI.create("https://api-us-east-1.example.com"), endpoint);
    }

    @Test
    @DisplayName("Should fall back to primary region for blank hint")
    void shouldFallbackToPrimaryRegionForBlankHint() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .addRegion("eu-west-1", URI.create("https://api-eu-west-1.example.com"))
            .build();

        URI endpoint = router.resolveEndpoint("tenant-1", "workspace-1", "").getResult();

        assertEquals(URI.create("https://api-us-east-1.example.com"), endpoint);
    }

    @Test
    @DisplayName("Should fall back to primary region for unregistered hint")
    void shouldFallbackToPrimaryRegionForUnregisteredHint() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .addRegion("eu-west-1", URI.create("https://api-eu-west-1.example.com"))
            .build();

        URI endpoint = router.resolveEndpoint("tenant-1", "workspace-1", "ap-southeast-1").getResult();

        assertEquals(URI.create("https://api-us-east-1.example.com"), endpoint);
    }

    @Test
    @DisplayName("Should return all registered regions alphabetically")
    void shouldReturnAllRegisteredRegionsAlphabetically() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("eu-west-1", URI.create("https://api-eu-west-1.example.com"))
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .addRegion("ap-southeast-1", URI.create("https://api-ap-se-1.example.com"))
            .build();

        var regions = router.registeredRegions();

        assertEquals(3, regions.size());
        assertEquals("ap-southeast-1", regions.get(0));
        assertEquals("eu-west-1", regions.get(1));
        assertEquals("us-east-1", regions.get(2));
    }

    @Test
    @DisplayName("Should check region registration correctly")
    void shouldCheckRegionRegistrationCorrectly() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .addRegion("eu-west-1", URI.create("https://api-eu-west-1.example.com"))
            .build();

        assertTrue(router.isRegionRegistered("us-east-1"));
        assertTrue(router.isRegionRegistered("eu-west-1"));
        assertFalse(router.isRegionRegistered("ap-southeast-1"));
    }

    @Test
    @DisplayName("Should fail to resolve with null tenantId")
    void shouldFailToResolveWithNullTenantId() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .build();

        assertThrows(NullPointerException.class, () ->
            router.resolveEndpoint(null, "workspace-1", "eu-west-1")
        );
    }

    @Test
    @DisplayName("Should fail to resolve with null workspaceId")
    void shouldFailToResolveWithNullWorkspaceId() {
        RegionRouter router = RegionRouter.builder()
            .primaryRegion("us-east-1")
            .addRegion("us-east-1", URI.create("https://api-us-east-1.example.com"))
            .build();

        assertThrows(NullPointerException.class, () ->
            router.resolveEndpoint("tenant-1", null, "eu-west-1")
        );
    }
}
