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
@DisplayName("KernelDescriptor Tests [GH-90000]")
class KernelDescriptorTest {

    private static KernelDescriptor.Builder baseBuilder() { // GH-90000
        return new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test-module [GH-90000]")
            .withName("Test Module [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE); // GH-90000
    }

    @Test
    @DisplayName("Should create descriptor with required fields [GH-90000]")
    void shouldCreateDescriptorWithRequiredFields() { // GH-90000
        KernelDescriptor descriptor = baseBuilder().build(); // GH-90000

        assertNotNull(descriptor); // GH-90000
        assertEquals("test-module", descriptor.getDescriptorId()); // GH-90000
        assertEquals("Test Module", descriptor.getName()); // GH-90000
        assertEquals("1.0.0", descriptor.getVersion()); // GH-90000
        assertEquals(KernelDescriptor.DescriptorType.MODULE, descriptor.getType()); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception when descriptorId is null [GH-90000]")
    void shouldThrowExceptionWhenDescriptorIdIsNull() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId(null) // GH-90000
                .withName("Test [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @Test
    @DisplayName("Should throw exception when descriptorId is empty [GH-90000]")
    void shouldThrowExceptionWhenDescriptorIdIsEmpty() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("   [GH-90000]")
                .withName("Test [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @Test
    @DisplayName("Should throw exception when name is null [GH-90000]")
    void shouldThrowExceptionWhenNameIsNull() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("test [GH-90000]")
                .withName(null) // GH-90000
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build() // GH-90000
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid version format [GH-90000]")
    void shouldThrowExceptionForInvalidVersionFormat() { // GH-90000
        assertThrows(Exception.class, () -> // GH-90000
            new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("test [GH-90000]")
                .withName("Test [GH-90000]")
                .withVersion("invalid-version [GH-90000]")
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
    @DisplayName("Should validate semantic version format [GH-90000]")
    void shouldValidateSemanticVersionFormat(String version, boolean expectedValid) { // GH-90000
        if (expectedValid) { // GH-90000
            assertDoesNotThrow(() -> // GH-90000
                new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("test [GH-90000]")
                    .withName("Test [GH-90000]")
                    .withVersion(version) // GH-90000
                    .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                    .build() // GH-90000
            );
        } else {
            assertThrows(Exception.class, () -> // GH-90000
                new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("test [GH-90000]")
                    .withName("Test [GH-90000]")
                    .withVersion(version) // GH-90000
                    .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                    .build() // GH-90000
            );
        }
    }

    @Test
    @DisplayName("Should add and retrieve metadata [GH-90000]")
    void shouldAddAndRetrieveMetadata() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test [GH-90000]")
            .withName("Test [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withMetadata("key1", "value1") // GH-90000
            .withMetadata("key2", "value2") // GH-90000
            .build(); // GH-90000

        Map<String, String> metadata = descriptor.getMetadata(); // GH-90000
        assertEquals(2, metadata.size()); // GH-90000
        assertEquals("value1", metadata.get("key1 [GH-90000]"));
        assertEquals("value2", metadata.get("key2 [GH-90000]"));
    }

    @Test
    @DisplayName("Should add and retrieve capabilities [GH-90000]")
    void shouldAddAndRetrieveCapabilities() { // GH-90000
        KernelCapability cap1 = KernelCapability.Core.DATA_STORAGE;
        KernelCapability cap2 = KernelCapability.Core.USER_AUTHENTICATION;

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test [GH-90000]")
            .withName("Test [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
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
    @DisplayName("Should add and retrieve dependencies [GH-90000]")
    void shouldAddAndRetrieveDependencies() { // GH-90000
        KernelDependency dep1 = new KernelDependency("dep1", "1.0.0", // GH-90000
            KernelDependency.DependencyType.MODULE, false);
        KernelDependency dep2 = new KernelDependency("dep2", "1.0.0", // GH-90000
            KernelDependency.DependencyType.CAPABILITY, false);

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test [GH-90000]")
            .withName("Test [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withDependency(dep1) // GH-90000
            .withDependency(dep2) // GH-90000
            .build(); // GH-90000

        Set<KernelDependency> dependencies = descriptor.getDependencies(); // GH-90000
        assertEquals(2, dependencies.size()); // GH-90000
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("dep1 [GH-90000]")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("dep2 [GH-90000]")));
    }

    @Test
    @DisplayName("Should add and retrieve tags [GH-90000]")
    void shouldAddAndRetrieveTags() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test [GH-90000]")
            .withName("Test [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withTag("production [GH-90000]")
            .withTag("critical [GH-90000]")
            .build(); // GH-90000

        Set<String> tags = descriptor.getTags(); // GH-90000
        assertEquals(2, tags.size()); // GH-90000
        assertTrue(tags.contains("production [GH-90000]"));
        assertTrue(tags.contains("critical [GH-90000]"));
    }

    @Test
    @DisplayName("Should return empty collections when not set [GH-90000]")
    void shouldReturnEmptyCollectionsWhenNotSet() { // GH-90000
        KernelDescriptor descriptor = baseBuilder().build(); // GH-90000

        assertTrue(descriptor.getMetadata().isEmpty()); // GH-90000
        assertTrue(descriptor.getCapabilities().isEmpty()); // GH-90000
        assertTrue(descriptor.getDependencies().isEmpty()); // GH-90000
        assertTrue(descriptor.getTags().isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should check hasCapability correctly [GH-90000]")
    void shouldCheckHasCapabilityCorrectly() { // GH-90000
        KernelCapability cap = KernelCapability.Core.DATA_STORAGE;

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test [GH-90000]")
            .withName("Test [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withCapability(cap) // GH-90000
            .build(); // GH-90000

        assertTrue(descriptor.hasCapability(cap)); // GH-90000
        assertFalse(descriptor.hasCapability(KernelCapability.Core.AI_ML_FRAMEWORK)); // GH-90000
    }

    @Test
    @DisplayName("Should check getTags contains production [GH-90000]")
    void shouldCheckHasTagCorrectly() { // GH-90000
        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test [GH-90000]")
            .withName("Test [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .withTag("production [GH-90000]")
            .build(); // GH-90000

        assertTrue(descriptor.getTags().contains("production [GH-90000]"));
        assertFalse(descriptor.getTags().contains("development [GH-90000]"));
    }

    @Test
    @DisplayName("Should create descriptor with all descriptor types [GH-90000]")
    void shouldCreateDescriptorWithAllComponentTypes() { // GH-90000
        for (KernelDescriptor.DescriptorType type : KernelDescriptor.DescriptorType.values()) { // GH-90000
            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("test-" + type.name().toLowerCase()) // GH-90000
                .withName("Test " + type.name()) // GH-90000
                .withVersion("1.0.0 [GH-90000]")
                .withType(type) // GH-90000
                .build(); // GH-90000

            assertEquals(type, descriptor.getType()); // GH-90000
        }
    }

    @Test
    @DisplayName("Should return description when set [GH-90000]")
    void shouldReturnDescriptionWhenSet() { // GH-90000
        String description = "This is a test module for demonstration purposes";

        KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test [GH-90000]")
            .withName("Test [GH-90000]")
            .withDescription(description) // GH-90000
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .build(); // GH-90000

        assertEquals(description, descriptor.getDescription()); // GH-90000
    }

    @Test
    @DisplayName("Should return empty description when not set [GH-90000]")
    void shouldReturnEmptyDescriptionWhenNotSet() { // GH-90000
        KernelDescriptor descriptor = baseBuilder().build(); // GH-90000

        // description may be null or empty when not set
        String desc = descriptor.getDescription(); // GH-90000
        assertTrue(desc == null || desc.isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly [GH-90000]")
    void shouldImplementEqualsAndHashCodeCorrectly() { // GH-90000
        KernelDescriptor descriptor1 = baseBuilder().build(); // GH-90000

        KernelDescriptor descriptor2 = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("test-module [GH-90000]")
            .withName("Different Name [GH-90000]")
            .withVersion("2.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.PLUGIN) // GH-90000
            .build(); // GH-90000

        KernelDescriptor descriptor3 = new KernelDescriptor.Builder() // GH-90000
            .withDescriptorId("different [GH-90000]")
            .withName("Test Module [GH-90000]")
            .withVersion("1.0.0 [GH-90000]")
            .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
            .build(); // GH-90000

        assertEquals(descriptor1, descriptor2); // Same ID // GH-90000
        assertEquals(descriptor1.hashCode(), descriptor2.hashCode()); // GH-90000
        assertNotEquals(descriptor1, descriptor3); // Different ID // GH-90000
    }

    @Test
    @DisplayName("Should implement toString correctly [GH-90000]")
    void shouldImplementToStringCorrectly() { // GH-90000
        KernelDescriptor descriptor = baseBuilder().build(); // GH-90000

        String toString = descriptor.toString(); // GH-90000
        assertTrue(toString.contains("test-module [GH-90000]"));
        assertTrue(toString.contains("Test Module [GH-90000]"));
        assertTrue(toString.contains("1.0.0 [GH-90000]"));
        assertTrue(toString.contains("MODULE [GH-90000]"));
    }
}
