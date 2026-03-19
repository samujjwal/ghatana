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

    @Test
    @DisplayName("Should create descriptor with required fields")
    void shouldCreateDescriptorWithRequiredFields() {
        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test-module")
            .name("Test Module")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .build();

        assertNotNull(descriptor);
        assertEquals("test-module", descriptor.getDescriptorId());
        assertEquals("Test Module", descriptor.getName());
        assertEquals("1.0.0", descriptor.getVersion());
        assertEquals(KernelDescriptor.ComponentType.MODULE, descriptor.getType());
    }

    @Test
    @DisplayName("Should throw exception when descriptorId is null")
    void shouldThrowExceptionWhenDescriptorIdIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            KernelDescriptor.builder()
                .descriptorId(null)
                .name("Test")
                .version("1.0.0")
                .type(KernelDescriptor.ComponentType.MODULE)
                .build()
        );
        assertTrue(exception.getMessage().contains("descriptorId"));
    }

    @Test
    @DisplayName("Should throw exception when descriptorId is empty")
    void shouldThrowExceptionWhenDescriptorIdIsEmpty() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            KernelDescriptor.builder()
                .descriptorId("  ")
                .name("Test")
                .version("1.0.0")
                .type(KernelDescriptor.ComponentType.MODULE)
                .build()
        );
        assertTrue(exception.getMessage().contains("descriptorId"));
    }

    @Test
    @DisplayName("Should throw exception when name is null")
    void shouldThrowExceptionWhenNameIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            KernelDescriptor.builder()
                .descriptorId("test")
                .name(null)
                .version("1.0.0")
                .type(KernelDescriptor.ComponentType.MODULE)
                .build()
        );
        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    @DisplayName("Should throw exception for invalid version format")
    void shouldThrowExceptionForInvalidVersionFormat() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            KernelDescriptor.builder()
                .descriptorId("test")
                .name("Test")
                .version("invalid-version")
                .type(KernelDescriptor.ComponentType.MODULE)
                .build()
        );
        assertTrue(exception.getMessage().contains("version"));
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
                KernelDescriptor.builder()
                    .descriptorId("test")
                    .name("Test")
                    .version(version)
                    .type(KernelDescriptor.ComponentType.MODULE)
                    .build()
            );
        } else {
            assertThrows(IllegalArgumentException.class, () ->
                KernelDescriptor.builder()
                    .descriptorId("test")
                    .name("Test")
                    .version(version)
                    .type(KernelDescriptor.ComponentType.MODULE)
                    .build()
            );
        }
    }

    @Test
    @DisplayName("Should add and retrieve metadata")
    void shouldAddAndRetrieveMetadata() {
        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .metadata("key1", "value1")
            .metadata("key2", "value2")
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

        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .capability(cap1)
            .capability(cap2)
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

        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .dependency(dep1)
            .dependency(dep2)
            .build();

        Set<KernelDependency> dependencies = descriptor.getDependencies();
        assertEquals(2, dependencies.size());
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("dep1")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("dep2")));
    }

    @Test
    @DisplayName("Should add and retrieve tags")
    void shouldAddAndRetrieveTags() {
        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .tag("production")
            .tag("critical")
            .build();

        Set<String> tags = descriptor.getTags();
        assertEquals(2, tags.size());
        assertTrue(tags.contains("production"));
        assertTrue(tags.contains("critical"));
    }

    @Test
    @DisplayName("Should return empty collections when not set")
    void shouldReturnEmptyCollectionsWhenNotSet() {
        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .build();

        assertTrue(descriptor.getMetadata().isEmpty());
        assertTrue(descriptor.getCapabilities().isEmpty());
        assertTrue(descriptor.getDependencies().isEmpty());
        assertTrue(descriptor.getTags().isEmpty());
    }

    @Test
    @DisplayName("Should check hasCapability correctly")
    void shouldCheckHasCapabilityCorrectly() {
        KernelCapability cap = KernelCapability.Core.DATA_STORAGE;

        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .capability(cap)
            .build();

        assertTrue(descriptor.hasCapability(cap));
        assertFalse(descriptor.hasCapability(KernelCapability.Core.AI_ML_FRAMEWORK));
    }

    @Test
    @DisplayName("Should check hasTag correctly")
    void shouldCheckHasTagCorrectly() {
        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .tag("production")
            .build();

        assertTrue(descriptor.hasTag("production"));
        assertFalse(descriptor.hasTag("development"));
    }

    @Test
    @DisplayName("Should create descriptor with all component types")
    void shouldCreateDescriptorWithAllComponentTypes() {
        for (KernelDescriptor.ComponentType type : KernelDescriptor.ComponentType.values()) {
            KernelDescriptor descriptor = KernelDescriptor.builder()
                .descriptorId("test-" + type.name().toLowerCase())
                .name("Test " + type.name())
                .version("1.0.0")
                .type(type)
                .build();

            assertEquals(type, descriptor.getType());
        }
    }

    @Test
    @DisplayName("Should return description when set")
    void shouldReturnDescriptionWhenSet() {
        String description = "This is a test module for demonstration purposes";

        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .description(description)
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .build();

        assertEquals(description, descriptor.getDescription());
    }

    @Test
    @DisplayName("Should return empty description when not set")
    void shouldReturnEmptyDescriptionWhenNotSet() {
        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .build();

        assertEquals("", descriptor.getDescription());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        KernelDescriptor descriptor1 = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .build();

        KernelDescriptor descriptor2 = KernelDescriptor.builder()
            .descriptorId("test")
            .name("Different Name")
            .version("2.0.0")
            .type(KernelDescriptor.ComponentType.PLUGIN)
            .build();

        KernelDescriptor descriptor3 = KernelDescriptor.builder()
            .descriptorId("different")
            .name("Test")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .build();

        assertEquals(descriptor1, descriptor2); // Same ID
        assertEquals(descriptor1.hashCode(), descriptor2.hashCode());
        assertNotEquals(descriptor1, descriptor3); // Different ID
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
        KernelDescriptor descriptor = KernelDescriptor.builder()
            .descriptorId("test-module")
            .name("Test Module")
            .version("1.0.0")
            .type(KernelDescriptor.ComponentType.MODULE)
            .build();

        String toString = descriptor.toString();
        assertTrue(toString.contains("test-module"));
        assertTrue(toString.contains("Test Module"));
        assertTrue(toString.contains("1.0.0"));
        assertTrue(toString.contains("MODULE"));
    }
}
