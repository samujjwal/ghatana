/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.kernel.descriptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 Expansion tests for Kernel module.
 * Tests descriptor composition, validation, versioning, and concurrency.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for kernel descriptor and module system
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Kernel - Phase 3 Expansion [GH-90000]")
class KernelExpansionTest {

    // ============================================
    // DESCRIPTOR BUILDER VALIDATION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Descriptor Builder Validation [GH-90000]")
    class DescriptorValidationTests {

        @Test
        @DisplayName("Build valid descriptor with all fields [GH-90000]")
        void buildValidDescriptor() { // GH-90000
            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("module-1 [GH-90000]")
                .withName("Module One [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .withDescription("Test module [GH-90000]")
                .withOwner("Test Author [GH-90000]")
                .build(); // GH-90000

            assertThat(descriptor.getDescriptorId()).isEqualTo("module-1 [GH-90000]");
            assertThat(descriptor.getName()).isEqualTo("Module One [GH-90000]");
            assertThat(descriptor.getVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.MODULE); // GH-90000
        }

        @Test
        @DisplayName("Rejects null descriptor ID [GH-90000]")
        void rejectsNullDescriptorId() { // GH-90000
            assertThatThrownBy(() -> new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId(null) // GH-90000
                .withName("Module [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("Rejects blank descriptor ID [GH-90000]")
        void rejectsBlankDescriptorId() { // GH-90000
            assertThatThrownBy(() -> new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("    [GH-90000]")
                .withName("Module [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("Validates semantic versioning [GH-90000]")
        void semanticVersioning() { // GH-90000
            KernelDescriptor desc1 = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("m1 [GH-90000]")
                .withName("M1 [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build(); // GH-90000

            KernelDescriptor desc2 = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("m1 [GH-90000]")
                .withName("M1 [GH-90000]")
                .withVersion("1.1.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build(); // GH-90000

            assertThat(desc1.getVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(desc2.getVersion()).isEqualTo("1.1.0 [GH-90000]");
        }
    }

    // ============================================
    // DESCRIPTOR TYPES (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Descriptor Types [GH-90000]")
    class DescriptorTypeTests {

        @Test
        @DisplayName("Create MODULE descriptor [GH-90000]")
        void moduleDescriptor() { // GH-90000
            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("module-1 [GH-90000]")
                .withName("My Module [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build(); // GH-90000

            assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.MODULE); // GH-90000
        }

        @Test
        @DisplayName("Create SERVICE descriptor [GH-90000]")
        void serviceDescriptor() { // GH-90000
            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("service-1 [GH-90000]")
                .withName("My Service [GH-90000]")
                .withVersion("2.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.SERVICE) // GH-90000
                .build(); // GH-90000

            assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.SERVICE); // GH-90000
        }

        @Test
        @DisplayName("Create PLUGIN descriptor [GH-90000]")
        void pluginDescriptor() { // GH-90000
            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("plugin-1 [GH-90000]")
                .withName("My Plugin [GH-90000]")
                .withVersion("1.5.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.PLUGIN) // GH-90000
                .build(); // GH-90000

            assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.PLUGIN); // GH-90000
        }

        @Test
        @DisplayName("Many descriptors of various types [GH-90000]")
        void variousDescriptorTypes() { // GH-90000
            List<KernelDescriptor> descriptors = new ArrayList<>(); // GH-90000

            KernelDescriptor.DescriptorType[] types = KernelDescriptor.DescriptorType.values(); // GH-90000

            for (int i = 0; i < types.length * 5; i++) { // GH-90000
                final int idx = i;
                KernelDescriptor.DescriptorType type = types[idx % types.length];

                KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("desc-" + idx) // GH-90000
                    .withName("Descriptor " + idx) // GH-90000
                    .withVersion("1.0.0 [GH-90000]")
                    .withType(type) // GH-90000
                    .build(); // GH-90000

                descriptors.add(descriptor); // GH-90000
            }

            assertThat(descriptors).hasSize(types.length * 5); // GH-90000
        }
    }

    // ============================================
    // DEPENDENCIES AND METADATA (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Dependencies and Metadata [GH-90000]")
    class DependenciesTests {

        @Test
        @DisplayName("Module with dependencies [GH-90000]")
        void withDependencies() { // GH-90000
            Set<KernelDependency> dependencies = new HashSet<>(); // GH-90000
            dependencies.add(new KernelDependency("core-module", "1.0.0", KernelDependency.DependencyType.MODULE, false)); // GH-90000
            dependencies.add(new KernelDependency("util-module", "1.0.0", KernelDependency.DependencyType.MODULE, false)); // GH-90000
            dependencies.add(new KernelDependency("security-module", "1.0.0", KernelDependency.DependencyType.MODULE, false)); // GH-90000

            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("app-module [GH-90000]")
                .withName("Application [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .withDependencies(dependencies) // GH-90000
                .build(); // GH-90000

            assertThat(descriptor.getDependencies()).hasSize(3); // GH-90000
            assertThat(descriptor.getDependencies()) // GH-90000
                .extracting(KernelDependency::getDependencyId) // GH-90000
                .contains("core-module", "util-module", "security-module"); // GH-90000
        }

        @Test
        @DisplayName("Module with custom metadata [GH-90000]")
        void withCustomMetadata() { // GH-90000
            Map<String, String> metadata = new HashMap<>(); // GH-90000
            metadata.put("author", "John Doe"); // GH-90000
            metadata.put("license", "Apache 2.0"); // GH-90000
            metadata.put("classification", "internal"); // GH-90000
            metadata.put("build_number", "12345"); // GH-90000

            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("meta-module [GH-90000]")
                .withName("Metadata Module [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .withMetadata(metadata) // GH-90000
                .build(); // GH-90000

            assertThat(descriptor.getMetadata()).hasSize(4); // GH-90000
            assertThat(descriptor.getMetadata()).containsEntry("license", "Apache 2.0"); // GH-90000
        }

        @Test
        @DisplayName("Complex dependency graph [GH-90000]")
        void complexDependencyGraph() { // GH-90000
            List<KernelDescriptor> modules = new ArrayList<>(); // GH-90000

            // Create base modules
            for (int i = 0; i < 10; i++) { // GH-90000
                final int idx = i;
                Set<KernelDependency> deps = new HashSet<>(); // GH-90000
                if (idx > 0) { // GH-90000
                    deps.add(new KernelDependency("module-" + (idx - 1), "1.0.0", KernelDependency.DependencyType.MODULE, false)); // GH-90000
                }

                KernelDescriptor desc = new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("module-" + idx) // GH-90000
                    .withName("Module " + idx) // GH-90000
                    .withVersion("1.0.0 [GH-90000]")
                    .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                    .withDependencies(deps) // GH-90000
                    .build(); // GH-90000

                modules.add(desc); // GH-90000
            }

            assertThat(modules).hasSize(10); // GH-90000
            // Last module has dependency on previous
            assertThat(modules.get(9).getDependencies()) // GH-90000
                .extracting(KernelDependency::getDependencyId) // GH-90000
                .contains("module-8 [GH-90000]");
        }

        @Test
        @DisplayName("Many metadata fields [GH-90000]")
        void manyMetadataFields() { // GH-90000
            Map<String, String> metadata = new HashMap<>(); // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                metadata.put("field-" + idx, "value-" + idx); // GH-90000
            }

            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("large-meta [GH-90000]")
                .withName("Large Metadata [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .withMetadata(metadata) // GH-90000
                .build(); // GH-90000

            assertThat(descriptor.getMetadata()).hasSize(50); // GH-90000
        }
    }

    // ============================================
    // VERSIONING (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Versioning and Compatibility [GH-90000]")
    class VersioningTests {

        @Test
        @DisplayName("Various semantic versions [GH-90000]")
        void semanticVersions() { // GH-90000
            String[] versions = {
                "1.0.0", "1.0.1", "1.1.0", "2.0.0",
                "0.0.1", "10.5.3", "1.0.0-alpha", "1.0.0-beta.1"
            };

            List<KernelDescriptor> descriptors = new ArrayList<>(); // GH-90000
            for (int i = 0; i < versions.length; i++) { // GH-90000
                final int idx = i;
                KernelDescriptor desc = new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("versioned-" + idx) // GH-90000
                    .withName("Versioned Module " + idx) // GH-90000
                    .withVersion(versions[idx]) // GH-90000
                    .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                    .build(); // GH-90000
                descriptors.add(desc); // GH-90000
            }

            assertThat(descriptors).hasSize(versions.length); // GH-90000
            assertThat(descriptors.get(0).getVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(descriptors.get(7).getVersion()).isEqualTo("1.0.0-beta.1 [GH-90000]");
        }

        @Test
        @DisplayName("Version evolution tracking [GH-90000]")
        void versionEvolution() { // GH-90000
            List<KernelDescriptor> versions = new ArrayList<>(); // GH-90000

            String[] versionSequence = {"1.0.0", "1.1.0", "1.2.0", "2.0.0"};
            for (int i = 0; i < versionSequence.length; i++) { // GH-90000
                final int idx = i;
                KernelDescriptor desc = new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("myapp [GH-90000]")
                    .withName("My Application [GH-90000]")
                    .withVersion(versionSequence[idx]) // GH-90000
                    .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                    .build(); // GH-90000
                versions.add(desc); // GH-90000
            }

            assertThat(versions).hasSize(4); // GH-90000
            assertThat(versions.get(0).getVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(versions.get(3).getVersion()).isEqualTo("2.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("Compatibility metadata [GH-90000]")
        void compatibilityMetadata() { // GH-90000
            Map<String, String> compat = new HashMap<>(); // GH-90000
            compat.put("min_kernel_version", "2.0.0"); // GH-90000
            compat.put("max_kernel_version", "3.0.0"); // GH-90000
            compat.put("java_version", "11+"); // GH-90000
            compat.put("deprecated_apis", "api-1,api-2"); // GH-90000

            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("compat-module [GH-90000]")
                .withName("Compatible Module [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .withMetadata(compat) // GH-90000
                .build(); // GH-90000

            assertThat(descriptor.getMetadata()).containsEntry("min_kernel_version", "2.0.0"); // GH-90000
        }

        @Test
        @DisplayName("Breaking change tracking [GH-90000]")
        void breakingChanges() { // GH-90000
            KernelDescriptor v1 = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("myapp [GH-90000]")
                .withName("App [GH-90000]")
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build(); // GH-90000

            Map<String, String> breakingMeta = new HashMap<>(); // GH-90000
            breakingMeta.put( // GH-90000
                "breaking_changes",
                "Removed config format support;API endpoint changed;Database schema migration required"
            );

            KernelDescriptor v2 = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId("myapp [GH-90000]")
                .withName("App [GH-90000]")
                .withVersion("2.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .withMetadata(breakingMeta) // GH-90000
                .build(); // GH-90000

            assertThat(v1.getVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(v2.getVersion()).isEqualTo("2.0.0 [GH-90000]");
        }
    }

    // ============================================
    // CONCURRENT DESCRIPTOR OPERATIONS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many descriptors built concurrently [GH-90000]")
        void concurrentDescriptorBuilding() throws Exception { // GH-90000
            int threadCount = 30;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            List<KernelDescriptor> descriptors = java.util.Collections.synchronizedList(new ArrayList<>()); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            Set<KernelDependency> deps = new HashSet<>(); // GH-90000
                            deps.add(new KernelDependency("base-module", "1.0.0", KernelDependency.DependencyType.MODULE, false)); // GH-90000

                            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                                .withDescriptorId("descriptor-" + threadIdx) // GH-90000
                                .withName("Descriptor " + threadIdx) // GH-90000
                                .withVersion("1.0.0 [GH-90000]")
                                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                                .withDependencies(deps) // GH-90000
                                .build(); // GH-90000

                            descriptors.add(descriptor); // GH-90000
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(descriptors).hasSize(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Large descriptor registry operations [GH-90000]")
        void largeRegistryOperations() throws Exception { // GH-90000
            // Build large set of descriptors
            List<KernelDescriptor> registry = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                final int idx = i;
                KernelDescriptor desc = new KernelDescriptor.Builder() // GH-90000
                    .withDescriptorId("module-" + idx) // GH-90000
                    .withName("Module " + idx) // GH-90000
                    .withVersion("1.0.0 [GH-90000]")
                    .withType(KernelDescriptor.DescriptorType.values()[idx % 3]) // GH-90000
                    .build(); // GH-90000
                registry.add(desc); // GH-90000
            }

            // Concurrent queries
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger queryCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 500; i++) { // GH-90000
                                KernelDescriptor desc = registry.get(i % registry.size()); // GH-90000
                                if (desc.getDescriptorId() != null) { // GH-90000
                                    queryCount.incrementAndGet(); // GH-90000
                                }
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(queryCount.get()).isEqualTo(threadCount * 500); // GH-90000
        }
    }

    // ============================================
    // EDGE CASES (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Descriptor with extremely long names and descriptions [GH-90000]")
        void veryLongNames() { // GH-90000
            String longId = "descriptor-" + "a".repeat(200); // GH-90000
            String longName = "Module-" + "B".repeat(500); // GH-90000
            String longDesc = "Description: " + "x".repeat(1000); // GH-90000

            KernelDescriptor descriptor = new KernelDescriptor.Builder() // GH-90000
                .withDescriptorId(longId) // GH-90000
                .withName(longName) // GH-90000
                .withDescription(longDesc) // GH-90000
                .withVersion("1.0.0 [GH-90000]")
                .withType(KernelDescriptor.DescriptorType.MODULE) // GH-90000
                .build(); // GH-90000

            assertThat(descriptor.getDescriptorId()).hasSize(longId.length()); // GH-90000
            assertThat(descriptor.getName()).hasSize(longName.length()); // GH-90000
        }
    }
}
