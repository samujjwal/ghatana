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
@DisplayName("KernelCapability Tests")
class KernelCapabilityTest {

    private static KernelCapability make(String id, String name) { 
        return new KernelCapability(id, name, "", KernelCapability.CapabilityType.DATA_MANAGEMENT, Map.of()); 
    }

    @Test
    @DisplayName("Should create capability with all fields")
    void shouldCreateCapabilityWithAllFields() { 
        Map<String, Object> metadata = Map.of(
            "required_services", "auth-service,storage-service",
            "product", "domain-alpha",
            "priority", "high"
        );

        KernelCapability capability = new KernelCapability( 
            "custom.capability",
            "Custom Capability",
            "Test capability description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            metadata
        );

        assertEquals("custom.capability", capability.getCapabilityId()); 
        assertEquals("Custom Capability", capability.getName()); 
        assertEquals("Test capability description", capability.getDescription()); 
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, capability.getType()); 
        assertEquals(metadata, capability.getMetadata()); 
    }

    @Test
    @DisplayName("Should create capability with default type")
    void shouldCreateCapabilityWithDefaultType() { 
        KernelCapability capability = new KernelCapability( 
            "simple.capability",
            "Simple Capability",
            "",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of() 
        );

        assertEquals("simple.capability", capability.getCapabilityId()); 
        assertEquals("Simple Capability", capability.getName()); 
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, capability.getType()); 
        assertTrue(capability.getMetadata().isEmpty()); 
    }

    @Test
    @DisplayName("Should throw exception for null capabilityId")
    void shouldThrowExceptionForNullCapabilityId() { 
        assertThrows(Exception.class, () -> 
            new KernelCapability(null, "Name", "", KernelCapability.CapabilityType.DATA_MANAGEMENT, Map.of()) 
        );
    }

    @Test
    @DisplayName("Should throw exception for empty capabilityId")
    void shouldThrowExceptionForEmptyCapabilityId() { 
        assertThrows(Exception.class, () -> 
            new KernelCapability("  ", "Name", "", KernelCapability.CapabilityType.DATA_MANAGEMENT, Map.of()) 
        );
    }

    @Test
    @DisplayName("Should throw exception for null name")
    void shouldThrowExceptionForNullName() { 
        assertThrows(Exception.class, () -> 
            new KernelCapability("capability", null, "", KernelCapability.CapabilityType.DATA_MANAGEMENT, Map.of()) 
        );
    }

    @Test
    @DisplayName("Should get metadata value correctly")
    void shouldGetMetadataValueCorrectly() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("key", "value") 
        );

        assertEquals("value", capability.getMetadata("key", (String) null)); 
        assertNull(capability.getMetadata("nonexistent", (String) null)); 
    }

    @Test
    @DisplayName("Should check supportsProduct correctly")
    void shouldCheckSupportsProductCorrectly() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("supported_products", "phr,finance,flashit") 
        );

        assertTrue(capability.supportsProduct("phr"));
        assertTrue(capability.supportsProduct("finance"));
        assertFalse(capability.supportsProduct("unknown"));
    }

    @Test
    @DisplayName("Should return true for supportsProduct when no products specified")
    void shouldReturnTrueForSupportsProductWhenNoProductsSpecified() { 
        KernelCapability capability = make("universal.capability", "Universal"); 

        assertTrue(capability.supportsProduct("any"));
        assertTrue(capability.supportsProduct("domain-alpha"));
    }

    @Test
    @DisplayName("Should check requiresService correctly")
    void shouldCheckRequiresServiceCorrectly() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("required_services", "auth-service,storage-service") 
        );

        assertTrue(capability.requiresService("auth-service"));
        assertTrue(capability.requiresService("storage-service"));
        assertFalse(capability.requiresService("cache-service"));
    }

    @Test
    @DisplayName("Should return empty set for getRequiredServices when none specified")
    void shouldReturnEmptySetForGetRequiredServicesWhenNoneSpecified() { 
        KernelCapability capability = make("simple.capability", "Simple"); 

        assertTrue(capability.getRequiredServices().isEmpty()); 
    }

    @Test
    @DisplayName("Should parse required services correctly")
    void shouldParseRequiredServicesCorrectly() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("required_services", "service1,service2,service3") 
        );

        Set<String> services = capability.getRequiredServices(); 
        assertEquals(3, services.size()); 
        assertTrue(services.contains("service1"));
        assertTrue(services.contains("service2"));
        assertTrue(services.contains("service3"));
    }

    @Test
    @DisplayName("Should check hasOptionalDependencies correctly")
    void shouldCheckHasOptionalDependenciesCorrectly() { 
        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            Map.of("optional_dependencies", "cache-service,monitoring-service") 
        );

        assertFalse(capability.getOptionalDependencies().isEmpty()); 
    }

    @Test
    @DisplayName("Should return empty for getOptionalDependencies when none")
    void shouldReturnFalseForHasOptionalDependenciesWhenNone() { 
        KernelCapability capability = make("simple.capability", "Simple"); 

        assertTrue(capability.getOptionalDependencies().isEmpty()); 
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() { 
        KernelCapability cap1 = make("test.cap", "Test"); 
        KernelCapability cap2 = make("test.cap", "Different"); 
        KernelCapability cap3 = make("other.cap", "Test"); 

        assertEquals(cap1, cap2); // Same ID 
        assertEquals(cap1.hashCode(), cap2.hashCode()); 
        assertNotEquals(cap1, cap3); // Different ID 
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() { 
        KernelCapability capability = make("test.capability", "Test Capability"); 

        String toString = capability.toString(); 
        assertTrue(toString.contains("test.capability"));
        assertTrue(toString.contains("Test Capability"));
    }

    @Test
    @DisplayName("Should have all core capabilities defined")
    void shouldHaveAllCoreCapabilitiesDefined() { 
        assertNotNull(KernelCapability.Core.DATA_STORAGE); 
        assertNotNull(KernelCapability.Core.USER_AUTHENTICATION); 
        assertNotNull(KernelCapability.Core.API_FRAMEWORK); 
        assertNotNull(KernelCapability.Core.WORKFLOW_ENGINE); 
        assertNotNull(KernelCapability.Core.EVENT_PROCESSING); 
        assertNotNull(KernelCapability.Core.AI_ML_FRAMEWORK); 
        assertNotNull(KernelCapability.Core.OBSERVABILITY_FRAMEWORK); 
        assertNotNull(KernelCapability.Core.SECURITY_FRAMEWORK); 
        assertNotNull(KernelCapability.Core.CONFIG_MANAGEMENT); 
        assertNotNull(KernelCapability.Core.TENANT_ISOLATION); 
    }

    @Test
    @DisplayName("Core capabilities should have correct IDs")
    void coreCapabilitiesShouldHaveCorrectIds() { 
        assertEquals("data.storage", KernelCapability.Core.DATA_STORAGE.getCapabilityId()); 
        assertEquals("user.authentication", KernelCapability.Core.USER_AUTHENTICATION.getCapabilityId()); 
        assertEquals("api.framework", KernelCapability.Core.API_FRAMEWORK.getCapabilityId()); 
        assertEquals("workflow.engine", KernelCapability.Core.WORKFLOW_ENGINE.getCapabilityId()); 
        assertEquals("event.processing", KernelCapability.Core.EVENT_PROCESSING.getCapabilityId()); 
        assertEquals("ai.ml.framework", KernelCapability.Core.AI_ML_FRAMEWORK.getCapabilityId()); 
        assertEquals("observability.framework", KernelCapability.Core.OBSERVABILITY_FRAMEWORK.getCapabilityId()); 
        assertEquals("security.framework", KernelCapability.Core.SECURITY_FRAMEWORK.getCapabilityId()); 
        assertEquals("config.management", KernelCapability.Core.CONFIG_MANAGEMENT.getCapabilityId()); 
        assertEquals("tenant.isolation", KernelCapability.Core.TENANT_ISOLATION.getCapabilityId()); 
    }

    @Test
    @DisplayName("Core capabilities should be correct types")
    void coreCapabilitiesShouldBeCorrectTypes() { 
        assertEquals(KernelCapability.CapabilityType.DATA_MANAGEMENT, KernelCapability.Core.DATA_STORAGE.getType()); 
        assertEquals(KernelCapability.CapabilityType.SECURITY, KernelCapability.Core.USER_AUTHENTICATION.getType()); 
        assertEquals(KernelCapability.CapabilityType.EVENT_PROCESSING, KernelCapability.Core.EVENT_PROCESSING.getType()); 
    }

    @ParameterizedTest
    @CsvSource({ 
        "DATA_MANAGEMENT",
        "EVENT_PROCESSING",
        "SECURITY",
        "COMPLIANCE",
        "AI_ML",
        "WORKFLOW",
        "INTEGRATION",
        "MONITORING"
    })
    @DisplayName("Should have all capability types defined")
    void shouldHaveAllCapabilityTypesDefined(String typeName) { 
        KernelCapability.CapabilityType type = KernelCapability.CapabilityType.valueOf(typeName); 
        assertNotNull(type); 
    }

    @Test
    @DisplayName("Should create immutable metadata copy")
    void shouldCreateImmutableMetadataCopy() { 
        Map<String, Object> metadata = new HashMap<>(); 
        metadata.put("key", "value"); 

        KernelCapability capability = new KernelCapability( 
            "test.capability",
            "Test",
            "Description",
            KernelCapability.CapabilityType.DATA_MANAGEMENT,
            metadata
        );

        metadata.put("newKey", "newValue"); 

        assertFalse(capability.getMetadata().containsKey("newKey"));
        assertEquals("value", capability.getMetadata("key", (String) null)); 
    }
}
