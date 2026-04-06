package com.ghatana.kernel.descriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

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

    private static KernelDescriptor.Builder baseBuilder() {
        return new KernelDescriptor.Builder()
            .withDescriptorId("test-module")
            .withName("Test Module")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE);
    }

    @Test
    @DisplayName("Should create descriptor with required fields")
    void shouldCreateDescriptorWithRequiredFields() {
        KernelDescriptor descriptor = baseBuilder().build();

        assertNotNull(descriptor);
        assertEquals("test-module", descriptor.getDescriptorId());
        assertEquals("Test Module", descriptor.getName());
        assertEquals("1.0.0", descriptor.getVersion());
        assertEquals(KernelDescriptor.DescriptorType.MODULE, descriptor.getType());
    }

    @Test
    @DisplayName("Should throw exception when descriptorId is null")
    void shouldThrowExceptionWhenDescriptorIdIsNull() {
        assertThrows(Exception.class, () ->
            new KernelDescriptor.Builder()
                .withDescriptorId(null)
                .withName("Test")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE)
                .build()
        );
    }

    @Test
    @DisplayName("Should throw exception when descriptorId is empty")
    void shouldThrowExceptionWhenDescriptorIdIsEmpty() {
        assertThrows(Exception.class, () ->
            new KernelDescriptor.Builder()
                .withDescriptorId("  ")
                .withName("Test")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE)
                .build()
        );
    }

    @Test
    @DisplayName("Should throw exception when name is null")
    void shouldThrowExceptionWhenNameIsNull() {
        assertThrows(Exception.class, () ->
            new KernelDescriptor.Builder()
                .withDescriptorId("test")
                .withName(null)
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE)
                .build()
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid version format")
    void shouldThrowExceptionForInvalidVersionFormat() {
        assertThrows(Exception.class, () ->
            new KernelDescriptor.Builder()
                .withDescriptorId("test")
                .withName("Test")
                .withVersion("invalid-version")
                .withType(KernelDescriptor.DescriptorType.MODULE)
                .build()
        );
    }

    @ParameterizedTest
    @CsvSource({
        "1.0.0, true",
        "0.0.1, true",
        "10.99.100, true",
        "1.0, false",
        "1.0.0.0, false",
        "v1.0.0, false",
        "1.0-SNAPSHOT, false"
    })
    @DisplayName("Should validate semantic version format")
    void shouldValidateSemanticVersionFormat(String version, boolean expectedValid) {
        if (expectedValid) {
            assertDoesNotThrow(() ->
                new KernelDescriptor.Builder()
                    .withDescriptorId("test")
                    .withName("Test")
                    .withVersion(version)
                    .withType(KernelDescriptor.DescriptorType.MODULE)
                    .build()
            );
        } else {
            assertThrows(Exception.class, () ->
                new KernelDescriptor.Builder()
                    .withDescriptorId("test")
                    .withName("Test")
                    .withVersion(version)
                    .withType(KernelDescriptor.DescriptorType.MODULE)
                    .build()
            );
        }
    }

    @Test
    @DisplayName("Should add and retrieve metadata")
    void shouldAddAndRetrieveMetadata() {
        KernelDescriptor descriptor = new KernelDescriptor.Builder()
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .withMetadata("key1", "value1")
            .withMetadata("key2", "value2")
            .build();

        Map<String, String> metadata = descriptor.getMetadata();
        assertEquals(2, metadata.size());
        assertEquals("value1", metadata.get("key1"));
        assertEquals("value2", metadata.get("key2"));
    }

    @Test
    @DisplayName("Should add and retrieve capabilities")
    void shouldAddAndRetrieveCapabilities() {
        KernelCapability cap1 = KernelCapability.Core.DATA_STORAGE;
        KernelCapability cap2 = KernelCapability.Core.USER_AUTHENTICATION;

        KernelDescriptor descriptor = new KernelDescriptor.Builder()
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .withCapability(cap1)
            .withCapability(cap2)
            .build();

        Set<KernelCapability> capabilities = descriptor.getCapabilities();
        assertEquals(2, capabilities.size());
        assertTrue(capabilities.contains(cap1));
        assertTrue(capabilities.contains(cap2));
    }

    @Test
    @DisplayName("Should add and retrieve dependencies")
    void shouldAddAndRetrieveDependencies() {
        KernelDependency dep1 = new KernelDependency("dep1", "1.0.0",
            KernelDependency.DependencyType.MODULE, false);
        KernelDependency dep2 = new KernelDependency("dep2", "1.0.0",
            KernelDependency.DependencyType.CAPABILITY, false);

        KernelDescriptor descriptor = new KernelDescriptor.Builder()
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .withDependency(dep1)
            .withDependency(dep2)
            .build();

        Set<KernelDependency> dependencies = descriptor.getDependencies();
        assertEquals(2, dependencies.size());
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("dep1")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("dep2")));
    }

    @Test
    @DisplayName("Should add and retrieve tags")
    void shouldAddAndRetrieveTags() {
        KernelDescriptor descriptor = new KernelDescriptor.Builder()
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .withTag("production")
            .withTag("critical")
            .build();

        Set<String> tags = descriptor.getTags();
        assertEquals(2, tags.size());
        assertTrue(tags.contains("production"));
        assertTrue(tags.contains("critical"));
    }

    @Test
    @DisplayName("Should return empty collections when not set")
    void shouldReturnEmptyCollectionsWhenNotSet() {
        KernelDescriptor descriptor = baseBuilder().build();

        assertTrue(descriptor.getMetadata().isEmpty());
        assertTrue(descriptor.getCapabilities().isEmpty());
        assertTrue(descriptor.getDependencies().isEmpty());
        assertTrue(descriptor.getTags().isEmpty());
    }

    @Test
    @DisplayName("Should check hasCapability correctly")
    void shouldCheckHasCapabilityCorrectly() {
        KernelCapability cap = KernelCapability.Core.DATA_STORAGE;

        KernelDescriptor descriptor = new KernelDescriptor.Builder()
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .withCapability(cap)
            .build();

        assertTrue(descriptor.hasCapability(cap));
        assertFalse(descriptor.hasCapability(KernelCapability.Core.AI_ML_FRAMEWORK));
    }

    @Test
    @DisplayName("Should check getTags contains production")
    void shouldCheckHasTagCorrectly() {
        KernelDescriptor descriptor = new KernelDescriptor.Builder()
            .withDescriptorId("test")
            .withName("Test")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .withTag("production")
            .build();

        assertTrue(descriptor.getTags().contains("production"));
        assertFalse(descriptor.getTags().contains("development"));
    }

    @Test
    @DisplayName("Should create descriptor with all descriptor types")
    void shouldCreateDescriptorWithAllComponentTypes() {
        for (KernelDescriptor.DescriptorType type : KernelDescriptor.DescriptorType.values()) {
            KernelDescriptor descriptor = new KernelDescriptor.Builder()
                .withDescriptorId("test-" + type.name().toLowerCase())
                .withName("Test " + type.name())
                .withVersion("1.0.0")
                .withType(type)
                .build();

            assertEquals(type, descriptor.getType());
        }
    }

    @Test
    @DisplayName("Should return description when set")
    void shouldReturnDescriptionWhenSet() {
        String description = "This is a test module for demonstration purposes";

        KernelDescriptor descriptor = new KernelDescriptor.Builder()
            .withDescriptorId("test")
            .withName("Test")
            .withDescription(description)
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .build();

        assertEquals(description, descriptor.getDescription());
    }

    @Test
    @DisplayName("Should return empty description when not set")
    void shouldReturnEmptyDescriptionWhenNotSet() {
        KernelDescriptor descriptor = baseBuilder().build();

        // description may be null or empty when not set
        String desc = descriptor.getDescription();
        assertTrue(desc == null || desc.isEmpty());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        KernelDescriptor descriptor1 = baseBuilder().build();

        KernelDescriptor descriptor2 = new KernelDescriptor.Builder()
            .withDescriptorId("test-module")
            .withName("Different Name")
            .withVersion("2.0.0")
            .withType(KernelDescriptor.DescriptorType.PLUGIN)
            .build();

        KernelDescriptor descriptor3 = new KernelDescriptor.Builder()
            .withDescriptorId("different")
            .withName("Test Module")
            .withVersion("1.0.0")
            .withType(KernelDescriptor.DescriptorType.MODULE)
            .build();

        assertEquals(descriptor1, descriptor2); // Same ID
        assertEquals(descriptor1.hashCode(), descriptor2.hashCode());
        assertNotEquals(descriptor1, descriptor3); // Different ID
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
        KernelDescriptor descriptor = baseBuilder().build();

        String toString = descriptor.toString();
        assertTrue(toString.contains("test-module"));
        assertTrue(toString.contains("Test Module"));
        assertTrue(toString.contains("1.0.0"));
        assertTrue(toString.contains("MODULE"));
    }
}
