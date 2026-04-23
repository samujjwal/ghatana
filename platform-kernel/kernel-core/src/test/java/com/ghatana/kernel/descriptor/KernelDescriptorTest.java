package com.ghatana.kernel.descriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link KernelDescriptor}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for KernelDescriptor validation and builder
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("KernelDescriptor Tests")
class KernelDescriptorTest {

    private static KernelDescriptor.Builder baseBuilder() { // GH-90000
        return new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test-module")
            .withName("Test Module")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE); // GH-90000
    }

    @Test
    @DisplayName("Should create descriptor with required fields")
    void shouldCreateDescriptorWithRequiredFields() { // GH-90000
        KernelDescriptor descriptor = baseBuilder().build(); // GH-90000

        assertNotNull(descriptor); // GH-90000
        assertEquals("test-module", descriptor.getDescriptorId()); // GH-90000
        assertEquals("Test Module", descriptor.getName()); // GH-90000
        assertEquals("1.0.0", descriptor.getVersion()); // GH-90000
        assertEquals(KernelDescriptor.DescriptorType.MODULE, descriptor.getType()); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception when descriptorId is null")
    void shouldThrowExceptionWhenDescriptorIdIsNull() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId(null) // GH-90000
                .withName("Test")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @Test
    @DisplayName("Should throw exception when descriptorId is empty")
    void shouldThrowExceptionWhenDescriptorIdIsEmpty() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("  ")
                .withName("Test")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @Test
    @DisplayName("Should throw exception when name is null")
    void shouldThrowExceptionWhenNameIsNull() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("test")
                .withName(null) // GH-90000
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid version format")
    void shouldThrowExceptionForInvalidVersionFormat() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("test")
                .withName("Test")
                .withVersion("invalid-version")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @ParameterizedTest
    @CsvSource({ // GH-90000
        "1.0.0, true",
        "0.0.1, true",
        "10.99.100, true",
        "1.0, false",
        "1.0.0.0, false",
        "v1.0.0, false",
        "1.0-SNAPSHOT, false"
    })
    @DisplayName("Should validate semantic version format")
    void shouldValidateSemanticVersionFormat(String version, boolean expectedValid) { // GH-90000
        if (expectedValid) { // GH-90000
            assertDoesNotThrow(() -> // GH-90000
                new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("test")
                    .withName("Test")
                    .withVersion(version) // GH-90000
                    .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                    .build() // GH-90000
            );
        } else {
            assertThrows(Exception.class, () -> // GH-90000
                new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("test")
                    .withName("Test")
                    .withVersion(version) // GH-90000
                    .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                    .build() // GH-90000
            );
        }
    }

    @Test
    @DisplayName("Should add and retrieve metadata")
    void shouldAddAndRetrieveMetadata() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withMetadata("key1", "value1") // GH-90000
            .withMetadata("key2", "value2") // GH-90000
            .build(); // GH-90000

        Map<String, String> metadata = descriptor.getMetadata(); // GH-90000
        assertEquals(2, metadata.size()); // GH-90000
        assertEquals("value1", metadata.get("key1"));
        assertEquals("value2", metadata.get("key2"));
    }

    @Test
    @DisplayName("Should add and retrieve capabilities")
    void shouldAddAndRetrieveCapabilities() { // GH-90000
        KernelCapability cap1 = KernelCapability.Core.DATA_STORAGE;
        KernelCapability cap2 = KernelCapability.Core.USER_AUTHENTICATION;

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withCapability(cap1) // GH-90000
            .withCapability(cap2) // GH-90000
            .build(); // GH-90000

        Set<KernelCapability> capabilities = descriptor.getCapabilities(); // GH-90000
        assertEquals(2, capabilities.size()); // GH-90000
        assertTrue(capabilities.contains(cap1)); // GH-90000
        assertTrue(capabilities.contains(cap2)); // GH-90000
    }

    @Test
    @DisplayName("Should add and retrieve dependencies")
    void shouldAddAndRetrieveDependencies() { // GH-90000
        KernelDependency dep1 = new KernelDependency("dep1", "1.0.0", // GH-90000
            KernelDependency.DependencyType.MODULE, false);
        KernelDependency dep2 = new KernelDependency("dep2", "1.0.0", // GH-90000
            KernelDependency.DependencyType.CAPABILITY, false);

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withDependency(dep1) // GH-90000
            .withDependency(dep2) // GH-90000
            .build(); // GH-90000

        Set<KernelDependency> dependencies = descriptor.getDependencies(); // GH-90000
        assertEquals(2, dependencies.size()); // GH-90000
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("dep1")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("dep2")));
    }

    @Test
    @DisplayName("Should add and retrieve tags")
    void shouldAddAndRetrieveTags() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withTag("production")
            .withTag("critical")
            .build(); // GH-90000

        Set<String> tags = descriptor.getTags(); // GH-90000
        assertEquals(2, tags.size()); // GH-90000
        assertTrue(tags.contains("production"));
        assertTrue(tags.contains("critical"));
    }

    @Test
    @DisplayName("Should return empty collections when not set")
    void shouldReturnEmptyCollectionsWhenNotSet() { // GH-90000
        KernelDescriptor descriptor = baseBuilder().build(); // GH-90000

        assertTrue(descriptor.getMetadata().isEmpty()); // GH-90000
        assertTrue(descriptor.getCapabilities().isEmpty()); // GH-90000
        assertTrue(descriptor.getDependencies().isEmpty()); // GH-90000
        assertTrue(descriptor.getTags().isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should check hasCapability correctly")
    void shouldCheckHasCapabilityCorrectly() { // GH-90000
        KernelCapability cap = KernelCapability.Core.DATA_STORAGE;

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withCapability(cap) // GH-90000
            .build(); // GH-90000

        assertTrue(descriptor.hasCapability(cap)); // GH-90000
        assertFalse(descriptor.hasCapability(KernelCapability.Core.AI_ML_FRAMEWORK)); // GH-90000
    }

    @Test
    @DisplayName("Should check getTags contains production")
    void shouldCheckHasTagCorrectly() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withTag("production")
            .build(); // GH-90000

        assertTrue(descriptor.getTags().contains("production"));
        assertFalse(descriptor.getTags().contains("development"));
    }

    @Test
    @DisplayName("Should create descriptor with all descriptor types")
    void shouldCreateDescriptorWithAllComponentTypes() { // GH-90000
        for (KernelDescriptor.DescriptorType type : KernelDescriptor.DescriptorType.values()) { // GH-90000
            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("test-" + type.name().toLowerCase()) // GH-90000
                .withName("Test " + type.name()) // GH-90000
                .withVersion("1.0.0")
                .withType(type) // GH-90000
                .build(); // GH-90000

            assertEquals(type, descriptor.getType()); // GH-90000
        }
    }

    @Test
    @DisplayName("Should return description when set")
    void shouldReturnDescriptionWhenSet() { // GH-90000
        String description = "This is a test module for demonstration purposes";

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test")
            .withName("Test")
            .withDescription(description) // GH-90000
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .build(); // GH-90000

        assertEquals(description, descriptor.getDescription()); // GH-90000
    }

    @Test
    @DisplayName("Should return empty description when not set")
    void shouldReturnEmptyDescriptionWhenNotSet() { // GH-90000
        KernelDescriptor descriptor = baseBuilder().build(); // GH-90000

        // description may be null or empty when not set
        String desc = descriptor.getDescription(); // GH-90000
        assertTrue(desc == null || desc.isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() { // GH-90000
        KernelDescriptor descriptor1 = baseBuilder().build(); // GH-90000

        KernelDescriptor descriptor2 = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test-module")
            .withName("Different Name")
            .withVersion("2.0.0")
            .withType(KernelDescriptor.DescriptorType.PLUGIN) // GH-90000
            .build(); // GH-90000

        KernelDescriptor descriptor3 = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("different")
            .withName("Test Module")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .build(); // GH-90000

        assertEquals(descriptor1, descriptor2); // Same ID // GH-90000
        assertEquals(descriptor1.hashCode(), descriptor2.hashCode()); // GH-90000
        assertNotEquals(descriptor1, descriptor3); // Different ID // GH-90000
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() { // GH-90000
        KernelDescriptor descriptor = baseBuilder().build(); // GH-90000

        String toString = descriptor.toString(); // GH-90000
        assertTrue(toString.contains("test-module"));
        assertTrue(toString.contains("Test Module"));
        assertTrue(toString.contains("1.0.0"));
        assertTrue(toString.contains("MODULE"));
    }
}
