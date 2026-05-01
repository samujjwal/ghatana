package com.ghatana.kernel.test.unit;

import com.ghatana.kernel.config.HierarchicalKernelConfigResolver;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for kernel core components.
 *
 * <p>IMPORTANT — ActiveJ mandate: use runPromise(() -> ...) from EventloopTestBase. 
 * CompletableFuture is BANNED in kernel code. Do NOT use .getResult() on Promise.</p> 
 *
 * @doc.type test
 * @doc.purpose Unit tests for kernel registry, config resolver, and descriptors
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
class KernelCoreUnitTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private HierarchicalKernelConfigResolver configResolver;
    private KernelTenantContext testContext;

    @BeforeEach
    void setUp() { 
        registry = new KernelRegistryImpl(); 
        configResolver = new HierarchicalKernelConfigResolver(); 
        testContext = createTestTenantContext(); 
    }

    // ==================== KernelDescriptor Tests ====================

    @Test
    void kernelDescriptorBuilderCreatesValidDescriptor() { 
        KernelDescriptor descriptor = new KernelDescriptor.Builder() 
            .withDescriptorId("test-module")
            .withName("Test Module")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) 
            .build(); 

        assertEquals("test-module", descriptor.getDescriptorId()); 
        assertEquals("Test Module", descriptor.getName()); 
        assertEquals("1.0.0", descriptor.getVersion()); 
        assertEquals(KernelDescriptor.DescriptorType.MODULE, descriptor.getType()); 
    }

    @Test
    void kernelDescriptorValidationRejectsInvalidId() { 
        assertThrows(IllegalArgumentException.class, () -> 
            new KernelDescriptor.Builder() 
                .withDescriptorId("Invalid_ID_With_CAPS") // Invalid: uppercase
                .withName("Test")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build() 
        );
    }

    @Test
    void kernelDescriptorValidationRejectsInvalidVersion() { 
        assertThrows(IllegalArgumentException.class, () -> 
            new KernelDescriptor.Builder() 
                .withDescriptorId("test-module")
                .withName("Test")
                .withVersion("invalid-version") // Invalid: not semver
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build() 
        );
    }

    @Test
    void kernelDescriptorHasCapabilityCheck() { 
        KernelCapability capability = KernelCapability.Core.DATA_STORAGE;

        KernelDescriptor descriptor = new KernelDescriptor.Builder() 
            .withDescriptorId("test-module")
            .withName("Test Module")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) 
            .withCapability(capability) 
            .build(); 

        assertTrue(descriptor.hasCapability(capability)); 
        assertTrue(descriptor.hasCapability("data.storage"));
        assertFalse(descriptor.hasCapability("nonexistent"));
    }

    @Test
    void kernelDescriptorSupportsTenantCheck() { 
        KernelDescriptor descriptor = new KernelDescriptor.Builder() 
            .withDescriptorId("test-module")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) 
            .withSupportedTenant("tenant-1")
            .withSupportedTenant("tenant-2")
            .build(); 

        assertTrue(descriptor.supportsTenant("tenant-1"));
        assertTrue(descriptor.supportsTenant("tenant-2"));
        assertFalse(descriptor.supportsTenant("tenant-3"));
    }

    @Test
    void kernelDescriptorEmptySupportedTenantsMeansAllTenants() { 
        KernelDescriptor descriptor = new KernelDescriptor.Builder() 
            .withDescriptorId("test-module")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) 
            .build(); 

        assertTrue(descriptor.supportsTenant("any-tenant"));
    }

    // ==================== KernelCapability Tests ====================

    @Test
    void kernelCapabilityCreation() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test Capability",
            "Test description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("key", "value") 
        );

        assertEquals("test.capability", capability.getCapabilityId()); 
        assertEquals("Test Capability", capability.getName()); 
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, capability.getType()); 
    }

    @Test
    void kernelCapabilityRequiresServiceCheck() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Test",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("required_services", "service1,service2") 
        );

        assertTrue(capability.requiresService("service1"));
        assertTrue(capability.requiresService("service2"));
        assertFalse(capability.requiresService("service3"));
    }

    @Test
    void kernelCapabilitySupportsProductCheck() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Test",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("supported_products", "phr,finance") 
        );

        assertTrue(capability.supportsProduct("phr"));
        assertTrue(capability.supportsProduct("finance"));
        assertFalse(capability.supportsProduct("flashit"));
    }

    @Test
    void kernelCapabilityEmptySupportedProductsMeansAllProducts() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Test",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of() 
        );

        assertTrue(capability.supportsProduct("any-product"));
    }

    @Test
    void kernelCapabilityValidationRejectsInvalidId() { 
        assertThrows(IllegalArgumentException.class, () -> 
            new KernelCapability( 
                "Invalid.Capability.ID", // Invalid: uppercase
                "Test",
                "Test",
                KernelCapability.CapabilityType.DATA_MANAGEMENT,
                Map.of() 
            )
        );
    }

    // ==================== HierarchicalKernelConfigResolver Tests ====================

    @Test
    void configResolverUsesKernelDefault() { 
        configResolver.setKernelDefault("test.key", "default-value"); 

        String value = configResolver.resolve("test.key", String.class, testContext); 
        assertEquals("default-value", value); 
    }

    @Test
    void configResolverResolveWithDefault() { 
        String value = configResolver.resolveWithDefault( 
            "missing.key", String.class, "fallback", testContext);
        assertEquals("fallback", value); 
    }

    @Test
    void configResolverThrowsOnMissingRequiredConfig() { 
        assertThrows(IllegalArgumentException.class, () -> 
            configResolver.resolve("missing.required.key", String.class, testContext) 
        );
    }

    @Test
    void configResolverReturnsOptional() { 
        Optional<String> value = configResolver.resolveOptional( 
            "missing.key", String.class, testContext);
        assertTrue(value.isEmpty()); 

        configResolver.setKernelDefault("existing.key", "value"); 
        Optional<String> existing = configResolver.resolveOptional( 
            "existing.key", String.class, testContext);
        assertTrue(existing.isPresent()); 
        assertEquals("value", existing.get()); 
    }

    // ==================== Helper Methods ====================

    private KernelTenantContext createTestTenantContext() { 
        return new KernelTenantContext( 
            "test-tenant",
            KernelTenantContext.TenantType.STANDARD,
            Map.of(), 
            Set.of("test-feature"),
            null,
            null
        );
    }
}
