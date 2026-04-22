package com.ghatana.kernel.descriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link KernelCapability}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for KernelCapability core capabilities and metadata
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("KernelCapability Tests [GH-90000]")
class KernelCapabilityTest {

    private static KernelCapability make(String id, String name) { // GH-90000
        return new KernelCapability(id, name, "", KernelCapability.CapabilityType.DATA_MANAGEMENT, Map.of()); // GH-90000
    }

    @Test
    @DisplayName("Should create capability with all fields [GH-90000]")
    void shouldCreateCapabilityWithAllFields() { // GH-90000
        Map<String, Object> metadata = Map.of( // GH-90000
            "required_services", "auth-service,storage-service",
            "product", "phr",
            "priority", "high"
        );

        KernelCapability capability = new KernelCapability( // GH-90000
            "custom.capability",
            "Custom Capability",
            "Test capability description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            metadata
        );

        assertEquals("custom.capability", capability.getCapabilityId()); // GH-90000
        assertEquals("Custom Capability", capability.getName()); // GH-90000
        assertEquals("Test capability description", capability.getDescription()); // GH-90000
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, capability.getType()); // GH-90000
        assertEquals(metadata, capability.getMetadata()); // GH-90000
    }

    @Test
    @DisplayName("Should create capability with default type [GH-90000]")
    void shouldCreateCapabilityWithDefaultType() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "simple.capability",
            "Simple Capability",
            "",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of() // GH-90000
        );

        assertEquals("simple.capability", capability.getCapabilityId()); // GH-90000
        assertEquals("Simple Capability", capability.getName()); // GH-90000
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, capability.getType()); // GH-90000
        assertTrue(capability.getMetadata().isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception for null capabilityId [GH-90000]")
    void shouldThrowExceptionForNullCapabilityId() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelCapability(null, "Name", "", KernelCapability.CapabilityType.DATA_MANAGEMENT, Map.of()) // GH-90000
        );
    }

    @Test
    @DisplayName("Should throw exception for empty capabilityId [GH-90000]")
    void shouldThrowExceptionForEmptyCapabilityId() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelCapability("  ", "Name", "", KernelCapability.CapabilityType.DATA_MANAGEMENT, Map.of()) // GH-90000
        );
    }

    @Test
    @DisplayName("Should throw exception for null name [GH-90000]")
    void shouldThrowExceptionForNullName() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelCapability("capability", null, "", KernelCapability.CapabilityType.DATA_MANAGEMENT, Map.of()) // GH-90000
        );
    }

    @Test
    @DisplayName("Should get metadata value correctly [GH-90000]")
    void shouldGetMetadataValueCorrectly() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("key", "value") // GH-90000
        );

        assertEquals("value", capability.getMetadata("key", (String) null)); // GH-90000
        assertNull(capability.getMetadata("nonexistent", (String) null)); // GH-90000
    }

    @Test
    @DisplayName("Should check supportsProduct correctly [GH-90000]")
    void shouldCheckSupportsProductCorrectly() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("supported_products", "phr,finance,flashit") // GH-90000
        );

        assertTrue(capability.supportsProduct("phr [GH-90000]"));
        assertTrue(capability.supportsProduct("finance [GH-90000]"));
        assertFalse(capability.supportsProduct("unknown [GH-90000]"));
    }

    @Test
    @DisplayName("Should return true for supportsProduct when no products specified [GH-90000]")
    void shouldReturnTrueForSupportsProductWhenNoProductsSpecified() { // GH-90000
        KernelCapability capability = make("universal.capability", "Universal"); // GH-90000

        assertTrue(capability.supportsProduct("any [GH-90000]"));
        assertTrue(capability.supportsProduct("phr [GH-90000]"));
    }

    @Test
    @DisplayName("Should check requiresService correctly [GH-90000]")
    void shouldCheckRequiresServiceCorrectly() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("required_services", "auth-service,storage-service") // GH-90000
        );

        assertTrue(capability.requiresService("auth-service [GH-90000]"));
        assertTrue(capability.requiresService("storage-service [GH-90000]"));
        assertFalse(capability.requiresService("cache-service [GH-90000]"));
    }

    @Test
    @DisplayName("Should return empty set for getRequiredServices when none specified [GH-90000]")
    void shouldReturnEmptySetForGetRequiredServicesWhenNoneSpecified() { // GH-90000
        KernelCapability capability = make("simple.capability", "Simple"); // GH-90000

        assertTrue(capability.getRequiredServices().isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should parse required services correctly [GH-90000]")
    void shouldParseRequiredServicesCorrectly() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("required_services", "service1,service2,service3") // GH-90000
        );

        Set<String> services = capability.getRequiredServices(); // GH-90000
        assertEquals(3, services.size()); // GH-90000
        assertTrue(services.contains("service1 [GH-90000]"));
        assertTrue(services.contains("service2 [GH-90000]"));
        assertTrue(services.contains("service3 [GH-90000]"));
    }

    @Test
    @DisplayName("Should check hasOptionalDependencies correctly [GH-90000]")
    void shouldCheckHasOptionalDependenciesCorrectly() { // GH-90000
        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("optional_dependencies", "cache-service,monitoring-service") // GH-90000
        );

        assertFalse(capability.getOptionalDependencies().isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should return empty for getOptionalDependencies when none [GH-90000]")
    void shouldReturnFalseForHasOptionalDependenciesWhenNone() { // GH-90000
        KernelCapability capability = make("simple.capability", "Simple"); // GH-90000

        assertTrue(capability.getOptionalDependencies().isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly [GH-90000]")
    void shouldImplementEqualsAndHashCodeCorrectly() { // GH-90000
        KernelCapability cap1 = make("test.cap", "Test"); // GH-90000
        KernelCapability cap2 = make("test.cap", "Different"); // GH-90000
        KernelCapability cap3 = make("other.cap", "Test"); // GH-90000

        assertEquals(cap1, cap2); // Same ID // GH-90000
        assertEquals(cap1.hashCode(), cap2.hashCode()); // GH-90000
        assertNotEquals(cap1, cap3); // Different ID // GH-90000
    }

    @Test
    @DisplayName("Should implement toString correctly [GH-90000]")
    void shouldImplementToStringCorrectly() { // GH-90000
        KernelCapability capability = make("test.capability", "Test Capability"); // GH-90000

        String toString = capability.toString(); // GH-90000
        assertTrue(toString.contains("test.capability [GH-90000]"));
        assertTrue(toString.contains("Test Capability [GH-90000]"));
    }

    @Test
    @DisplayName("Should have all core capabilities defined [GH-90000]")
    void shouldHaveAllCoreCapabilitiesDefined() { // GH-90000
        assertNotNull(KernelCapability.Core.DATA_STORAGE); // GH-90000
        assertNotNull(KernelCapability.Core.USER_AUTHENTICATION); // GH-90000
        assertNotNull(KernelCapability.Core.API_FRAMEWORK); // GH-90000
        assertNotNull(KernelCapability.Core.WORKFLOW_ENGINE); // GH-90000
        assertNotNull(KernelCapability.Core.EVENT_PROCESSING); // GH-90000
        assertNotNull(KernelCapability.Core.AI_ML_FRAMEWORK); // GH-90000
        assertNotNull(KernelCapability.Core.OBSERVABILITY_FRAMEWORK); // GH-90000
        assertNotNull(KernelCapability.Core.SECURITY_FRAMEWORK); // GH-90000
        assertNotNull(KernelCapability.Core.CONFIG_MANAGEMENT); // GH-90000
        assertNotNull(KernelCapability.Core.TENANT_ISOLATION); // GH-90000
    }

    @Test
    @DisplayName("Core capabilities should have correct IDs [GH-90000]")
    void coreCapabilitiesShouldHaveCorrectIds() { // GH-90000
        assertEquals("data.storage", KernelCapability.Core.DATA_STORAGE.getCapabilityId()); // GH-90000
        assertEquals("user.authentication", KernelCapability.Core.USER_AUTHENTICATION.getCapabilityId()); // GH-90000
        assertEquals("api.framework", KernelCapability.Core.API_FRAMEWORK.getCapabilityId()); // GH-90000
        assertEquals("workflow.engine", KernelCapability.Core.WORKFLOW_ENGINE.getCapabilityId()); // GH-90000
        assertEquals("event.processing", KernelCapability.Core.EVENT_PROCESSING.getCapabilityId()); // GH-90000
        assertEquals("ai.ml.framework", KernelCapability.Core.AI_ML_FRAMEWORK.getCapabilityId()); // GH-90000
        assertEquals("observability.framework", KernelCapability.Core.OBSERVABILITY_FRAMEWORK.getCapabilityId()); // GH-90000
        assertEquals("security.framework", KernelCapability.Core.SECURITY_FRAMEWORK.getCapabilityId()); // GH-90000
        assertEquals("config.management", KernelCapability.Core.CONFIG_MANAGEMENT.getCapabilityId()); // GH-90000
        assertEquals("tenant.isolation", KernelCapability.Core.TENANT_ISOLATION.getCapabilityId()); // GH-90000
    }

    @Test
    @DisplayName("Core capabilities should be correct types [GH-90000]")
    void coreCapabilitiesShouldBeCorrectTypes() { // GH-90000
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, KernelCapability.Core.DATA_STORAGE.getType()); // GH-90000
        assertEquals(KernelCapability.CapabilityType.SECURITY, KernelCapability.Core.USER_AUTHENTICATION.getType()); // GH-90000
        assertEquals(KernelCapability.CapabilityType.EVENT_PROCESSING, KernelCapability.Core.EVENT_PROCESSING.getType()); // GH-90000
    }

    @ParameterizedTest
    @CsvSource({ // GH-90000
        "DATA_MANAGEMENT",
        "EVENT_PROCESSING",
        "SECURITY",
        "COMPLIANCE",
        "AI_ML",
        "WORKFLOW",
        "INTEGRATION",
        "MONITORING"
    })
    @DisplayName("Should have all capability types defined [GH-90000]")
    void shouldHaveAllCapabilityTypesDefined(String typeName) { // GH-90000
        KernelCapability.CapabilityType type = KernelCapability.CapabilityType.valueOf(typeName); // GH-90000
        assertNotNull(type); // GH-90000
    }

    @Test
    @DisplayName("Should create immutable metadata copy [GH-90000]")
    void shouldCreateImmutableMetadataCopy() { // GH-90000
        Map<String, Object> metadata = new HashMap<>(); // GH-90000
        metadata.put("key", "value"); // GH-90000

        KernelCapability capability = new KernelCapability( // GH-90000
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            metadata
        );

        metadata.put("newKey", "newValue"); // GH-90000

        assertFalse(capability.getMetadata().containsKey("newKey [GH-90000]"));
        assertEquals("value", capability.getMetadata("key", (String) null)); // GH-90000
    }
}
