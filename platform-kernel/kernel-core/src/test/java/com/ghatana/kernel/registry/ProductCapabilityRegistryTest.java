package com.ghatana.kernel.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProductCapabilityRegistry} and {@link InMemoryProductCapabilityRegistry}.
 *
 * @doc.type class
 * @doc.purpose Tests product capability registry (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("ProductCapabilityRegistry Tests")
class ProductCapabilityRegistryTest {

    @Test
    @DisplayName("Should build registry with capabilities")
    void shouldBuildRegistryWithCapabilities() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .registerCapability("dmos.strategy", "Strategy generation")
            .build();

        assertTrue(registry.isCapabilityRegistered("dmos.campaigns"));
        assertTrue(registry.isCapabilityRegistered("dmos.strategy"));
        assertFalse(registry.isCapabilityRegistered("dmos.reporting"));
    }

    @Test
    @DisplayName("Should fail to register blank capability key")
    void shouldFailToRegisterBlankCapabilityKey() {
        assertThrows(IllegalArgumentException.class, () ->
            ProductCapabilityRegistry.builder()
                .registerCapability("", "Description")
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to register null capability key")
    void shouldFailToRegisterNullCapabilityKey() {
        assertThrows(NullPointerException.class, () ->
            ProductCapabilityRegistry.builder()
                .registerCapability(null, "Description")
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to register null description")
    void shouldFailToRegisterNullDescription() {
        assertThrows(NullPointerException.class, () ->
            ProductCapabilityRegistry.builder()
                .registerCapability("dmos.campaigns", null)
                .build()
        );
    }

    @Test
    @DisplayName("Should enable and check workspace capability")
    void shouldEnableAndCheckWorkspaceCapability() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .build();

        registry.enableCapability("tenant-1", "workspace-1", "dmos.campaigns");

        assertTrue(registry.isCapabilityEnabled("tenant-1", "workspace-1", "dmos.campaigns"));
        assertFalse(registry.isCapabilityEnabled("tenant-1", "workspace-2", "dmos.campaigns"));
    }

    @Test
    @DisplayName("Should disable workspace capability")
    void shouldDisableWorkspaceCapability() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .build();

        registry.enableCapability("tenant-1", "workspace-1", "dmos.campaigns");
        registry.disableCapability("tenant-1", "workspace-1", "dmos.campaigns");

        assertFalse(registry.isCapabilityEnabled("tenant-1", "workspace-1", "dmos.campaigns"));
    }

    @Test
    @DisplayName("Should fail to enable unregistered capability")
    void shouldFailToEnableUnregisteredCapability() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            registry.enableCapability("tenant-1", "workspace-1", "dmos.reporting")
        );
    }

    @Test
    @DisplayName("Should return empty for unregistered capability description")
    void shouldReturnEmptyForUnregisteredCapabilityDescription() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .build();

        assertEquals("Campaign management", registry.getCapabilityDescription("dmos.campaigns"));
        assertEquals("", registry.getCapabilityDescription("dmos.reporting"));
    }

    @Test
    @DisplayName("Should return all registered capabilities")
    void shouldReturnAllRegisteredCapabilities() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .registerCapability("dmos.strategy", "Strategy generation")
            .registerCapability("dmos.reporting", "Analytics reporting")
            .build();

        var capabilities = registry.getRegisteredCapabilities();

        assertEquals(3, capabilities.size());
        assertTrue(capabilities.contains("dmos.campaigns"));
        assertTrue(capabilities.contains("dmos.strategy"));
        assertTrue(capabilities.contains("dmos.reporting"));
    }

    @Test
    @DisplayName("Should return enabled capabilities for workspace")
    void shouldReturnEnabledCapabilitiesForWorkspace() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .registerCapability("dmos.strategy", "Strategy generation")
            .registerCapability("dmos.reporting", "Analytics reporting")
            .build();

        registry.enableCapability("tenant-1", "workspace-1", "dmos.campaigns");
        registry.enableCapability("tenant-1", "workspace-1", "dmos.strategy");

        var enabled = registry.getEnabledCapabilities("tenant-1", "workspace-1");

        assertEquals(2, enabled.size());
        assertTrue(enabled.contains("dmos.campaigns"));
        assertTrue(enabled.contains("dmos.strategy"));
        assertFalse(enabled.contains("dmos.reporting"));
    }

    @Test
    @DisplayName("Should fail to check capability with null tenantId")
    void shouldFailToCheckCapabilityWithNullTenantId() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .build();

        assertThrows(NullPointerException.class, () ->
            registry.isCapabilityEnabled(null, "workspace-1", "dmos.campaigns")
        );
    }

    @Test
    @DisplayName("Should fail to check capability with null workspaceId")
    void shouldFailToCheckCapabilityWithNullWorkspaceId() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .build();

        assertThrows(NullPointerException.class, () ->
            registry.isCapabilityEnabled("tenant-1", null, "dmos.campaigns")
        );
    }

    @Test
    @DisplayName("Should fail to check capability with null capabilityKey")
    void shouldFailToCheckCapabilityWithNullCapabilityKey() {
        ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
            .registerCapability("dmos.campaigns", "Campaign management")
            .build();

        assertThrows(NullPointerException.class, () ->
            registry.isCapabilityEnabled("tenant-1", "workspace-1", null)
        );
    }

    @Test
    @DisplayName("Should enable tenant-level capability")
    void shouldEnableTenantLevelCapability() {
        InMemoryProductCapabilityRegistry registry = new InMemoryProductCapabilityRegistry();
        registry.registerCapability("dmos.campaigns", "Campaign management");

        registry.enableTenantCapability("tenant-1", "dmos.campaigns");

        assertTrue(registry.isCapabilityEnabled("tenant-1", "workspace-1", "dmos.campaigns"));
        assertTrue(registry.isCapabilityEnabled("tenant-1", "workspace-2", "dmos.campaigns"));
    }

    @Test
    @DisplayName("Should disable tenant-level capability")
    void shouldDisableTenantLevelCapability() {
        InMemoryProductCapabilityRegistry registry = new InMemoryProductCapabilityRegistry();
        registry.registerCapability("dmos.campaigns", "Campaign management");

        registry.enableTenantCapability("tenant-1", "dmos.campaigns");
        registry.disableTenantCapability("tenant-1", "dmos.campaigns");

        assertFalse(registry.isCapabilityEnabled("tenant-1", "workspace-1", "dmos.campaigns"));
    }

    @Test
    @DisplayName("Workspace capability should override tenant capability")
    void workspaceCapabilityShouldOverrideTenantCapability() {
        InMemoryProductCapabilityRegistry registry = new InMemoryProductCapabilityRegistry();
        registry.registerCapability("dmos.campaigns", "Campaign management");

        registry.enableTenantCapability("tenant-1", "dmos.campaigns");
        registry.disableCapability("tenant-1", "workspace-1", "dmos.campaigns");

        assertFalse(registry.isCapabilityEnabled("tenant-1", "workspace-1", "dmos.campaigns"));
        assertTrue(registry.isCapabilityEnabled("tenant-1", "workspace-2", "dmos.campaigns"));
    }
}
