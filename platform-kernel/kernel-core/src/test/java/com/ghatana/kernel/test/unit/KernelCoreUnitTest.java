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
 * <p>IMPORTANT — ActiveJ mandate: use runPromise(() -> ...) from EventloopTestBase. // GH-90000
 * CompletableFuture is BANNED in kernel code. Do NOT use .getResult() on Promise.</p> // GH-90000
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
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
        configResolver = new HierarchicalKernelConfigResolver(); // GH-90000
        testContext = createTestTenantContext(); // GH-90000
    }

    // ==================== KernelDescriptor Tests ====================

    @Test
    void kernelDescriptorBuilderCreatesValidDescriptor() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test-module [GH-90000]")
            .withName("Test Module [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .build(); // GH-90000

        assertEquals("test-module", descriptor.getDescriptorId()); // GH-90000
        assertEquals("Test Module", descriptor.getName()); // GH-90000
        assertEquals("1.0.0", descriptor.getVersion()); // GH-90000
        assertEquals(KernelDescriptor.DescriptorType.MODULE, descriptor.getType()); // GH-90000
    }

    @Test
    void kernelDescriptorValidationRejectsInvalidId() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("Invalid_ID_With_CAPS [GH-90000]") // Invalid: uppercase
                .withName("Test [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @Test
    void kernelDescriptorValidationRejectsInvalidVersion() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("test-module [GH-90000]")
                .withName("Test [GH-90000]")
                .withVersion("invalid-version [GH-90000]") // Invalid: not semver
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @Test
    void kernelDescriptorHasCapabilityCheck() { // GH-90000
        KernelCapability capability = KernelCapability.Core.DATA_STORAGE;

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test-module [GH-90000]")
            .withName("Test Module [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withCapability(capability) // GH-90000
            .build(); // GH-90000

        assertTrue(descriptor.hasCapability(capability)); // GH-90000
        assertTrue(descriptor.hasCapability("data.storage [GH-90000]"));
        assertFalse(descriptor.hasCapability("nonexistent [GH-90000]"));
    }

    @Test
    void kernelDescriptorSupportsTenantCheck() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test-module [GH-90000]")
            .withName("Test [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withSupportedTenant("tenant-1 [GH-90000]")
            .withSupportedTenant("tenant-2 [GH-90000]")
            .build(); // GH-90000

        assertTrue(descriptor.supportsTenant("tenant-1 [GH-90000]"));
        assertTrue(descriptor.supportsTenant("tenant-2 [GH-90000]"));
        assertFalse(descriptor.supportsTenant("tenant-3 [GH-90000]"));
    }

    @Test
    void kernelDescriptorEmptySupportedTenantsMeansAllTenants() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test-module [GH-90000]")
            .withName("Test [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .build(); // GH-90000

        assertTrue(descriptor.supportsTenant("any-tenant [GH-90000]"));
    }

    // ==================== KernelCapability Tests ====================

    @Test
    void kernelCapabilityCreation() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test Capability",
            "Test description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("key", "value") // GH-90000
        );

        assertEquals("test.capability", capability.getCapabilityId()); // GH-90000
        assertEquals("Test Capability", capability.getName()); // GH-90000
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, capability.getType()); // GH-90000
    }

    @Test
    void kernelCapabilityRequiresServiceCheck() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Test",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("required_services", "service1,service2") // GH-90000
        );

        assertTrue(capability.requiresService("service1 [GH-90000]"));
        assertTrue(capability.requiresService("service2 [GH-90000]"));
        assertFalse(capability.requiresService("service3 [GH-90000]"));
    }

    @Test
    void kernelCapabilitySupportsProductCheck() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Test",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("supported_products", "phr,finance") // GH-90000
        );

        assertTrue(capability.supportsProduct("phr [GH-90000]"));
        assertTrue(capability.supportsProduct("finance [GH-90000]"));
        assertFalse(capability.supportsProduct("flashit [GH-90000]"));
    }

    @Test
    void kernelCapabilityEmptySupportedProductsMeansAllProducts() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Test",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of() // GH-90000
        );

        assertTrue(capability.supportsProduct("any-product [GH-90000]"));
    }

    @Test
    void kernelCapabilityValidationRejectsInvalidId() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            new KernelCapability( // GH-90000
                "Invalid.Capability.ID", // Invalid: uppercase
                "Test",
                "Test",
                KernelCapability.CapabilityType.DATA_MANAGEMENT,
                Map.of() // GH-90000
            )
        );
    }

    // ==================== HierarchicalKernelConfigResolver Tests ====================

    @Test
    void configResolverUsesKernelDefault() { // GH-90000
        configResolver.setKernelDefault("test.key", "default-value"); // GH-90000

        String value = configResolver.resolve("test.key", String.class, testContext); // GH-90000
        assertEquals("default-value", value); // GH-90000
    }

    @Test
    void configResolverResolveWithDefault() { // GH-90000
        String value = configResolver.resolveWithDefault( // GH-90000
            "missing.key", String.class, "fallback", testContext);
        assertEquals("fallback", value); // GH-90000
    }

    @Test
    void configResolverThrowsOnMissingRequiredConfig() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            configResolver.resolve("missing.required.key", String.class, testContext) // GH-90000
        );
    }

    @Test
    void configResolverReturnsOptional() { // GH-90000
        Optional<String> value = configResolver.resolveOptional( // GH-90000
            "missing.key", String.class, testContext);
        assertTrue(value.isEmpty()); // GH-90000

        configResolver.setKernelDefault("existing.key", "value"); // GH-90000
        Optional<String> existing = configResolver.resolveOptional( // GH-90000
            "existing.key", String.class, testContext);
        assertTrue(existing.isPresent()); // GH-90000
        assertEquals("value", existing.get()); // GH-90000
    }

    // ==================== Helper Methods ====================

    private KernelTenantContext createTestTenantContext() { // GH-90000
        return new KernelTenantContext( // GH-90000
            "test-tenant",
            KernelTenantContext.TenantType.STANDARD,
            Map.of(), // GH-90000
            Set.of("test-feature [GH-90000]"),
            null,
            null
        );
    }
}
