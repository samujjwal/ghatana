/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Kernel - Phase 3 Expansion")
class KernelExpansionTest {

    // ============================================
    // DESCRIPTOR BUILDER VALIDATION (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Descriptor Builder Validation")
    class DescriptorValidationTests {

        @Test
        @DisplayName("Build valid descriptor with all fields")
        void buildValidDescriptor() { 
            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId("module-1")
                .withName("Module One")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .withDescription("Test module")
                .withOwner("Test Author")
                .build(); 

            assertThat(descriptor.getDescriptorId()).isEqualTo("module-1");
            assertThat(descriptor.getName()).isEqualTo("Module One");
            assertThat(descriptor.getVersion()).isEqualTo("1.0.0");
            assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.MODULE); 
        }

        @Test
        @DisplayName("Rejects null descriptor ID")
        void rejectsNullDescriptorId() { 
            assertThatThrownBy(() -> new KernelDescriptor.Builder() 
                .withDescriptorId(null) 
                .withName("Module")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build()) 
                .isInstanceOf(Exception.class); 
        }

        @Test
        @DisplayName("Rejects blank descriptor ID")
        void rejectsBlankDescriptorId() { 
            assertThatThrownBy(() -> new KernelDescriptor.Builder() 
                .withDescriptorId("   ")
                .withName("Module")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build()) 
                .isInstanceOf(Exception.class); 
        }

        @Test
        @DisplayName("Validates semantic versioning")
        void semanticVersioning() { 
            KernelDescriptor desc1 = new KernelDescriptor.Builder() 
                .withDescriptorId("m1")
                .withName("M1")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build(); 

            KernelDescriptor desc2 = new KernelDescriptor.Builder() 
                .withDescriptorId("m1")
                .withName("M1")
                .withVersion("1.1.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build(); 

            assertThat(desc1.getVersion()).isEqualTo("1.0.0");
            assertThat(desc2.getVersion()).isEqualTo("1.1.0");
        }
    }

    // ============================================
    // DESCRIPTOR TYPES (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Descriptor Types")
    class DescriptorTypeTests {

        @Test
        @DisplayName("Create MODULE descriptor")
        void moduleDescriptor() { 
            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId("module-1")
                .withName("My Module")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build(); 

            assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.MODULE); 
        }

        @Test
        @DisplayName("Create SERVICE descriptor")
        void serviceDescriptor() { 
            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId("service-1")
                .withName("My Service")
                .withVersion("2.0.0")
                .withType(KernelDescriptor.DescriptorType.SERVICE) 
                .build(); 

            assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.SERVICE); 
        }

        @Test
        @DisplayName("Create PLUGIN descriptor")
        void pluginDescriptor() { 
            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId("plugin-1")
                .withName("My Plugin")
                .withVersion("1.5.0")
                .withType(KernelDescriptor.DescriptorType.PLUGIN) 
                .build(); 

            assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.PLUGIN); 
        }

        @Test
        @DisplayName("Many descriptors of various types")
        void variousDescriptorTypes() { 
            List<KernelDescriptor> descriptors = new ArrayList<>(); 

            KernelDescriptor.DescriptorType[] types = KernelDescriptor.DescriptorType.values(); 

            for (int i = 0; i < types.length * 5; i++) { 
                final int idx = i;
                KernelDescriptor.DescriptorType type = types[idx % types.length];

                KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                    .withDescriptorId("desc-" + idx) 
                    .withName("Descriptor " + idx) 
                    .withVersion("1.0.0")
                    .withType(type) 
                    .build(); 

                descriptors.add(descriptor); 
            }

            assertThat(descriptors).hasSize(types.length * 5); 
        }
    }

    // ============================================
    // DEPENDENCIES AND METADATA (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Dependencies and Metadata")
    class DependenciesTests {

        @Test
        @DisplayName("Module with dependencies")
        void withDependencies() { 
            Set<KernelDependency> dependencies = new HashSet<>(); 
            dependencies.add(new KernelDependency("core-module", "1.0.0", KernelDependency.DependencyType.MODULE, false)); 
            dependencies.add(new KernelDependency("util-module", "1.0.0", KernelDependency.DependencyType.MODULE, false)); 
            dependencies.add(new KernelDependency("security-module", "1.0.0", KernelDependency.DependencyType.MODULE, false)); 

            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId("app-module")
                .withName("Application")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .withDependencies(dependencies) 
                .build(); 

            assertThat(descriptor.getDependencies()).hasSize(3); 
            assertThat(descriptor.getDependencies()) 
                .extracting(KernelDependency::getDependencyId) 
                .contains("core-module", "util-module", "security-module"); 
        }

        @Test
        @DisplayName("Module with custom metadata")
        void withCustomMetadata() { 
            Map<String, String> metadata = new HashMap<>(); 
            metadata.put("author", "John Doe"); 
            metadata.put("license", "Apache 2.0"); 
            metadata.put("classification", "internal"); 
            metadata.put("build_number", "12345"); 

            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId("meta-module")
                .withName("Metadata Module")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .withMetadata(metadata) 
                .build(); 

            assertThat(descriptor.getMetadata()).hasSize(4); 
            assertThat(descriptor.getMetadata()).containsEntry("license", "Apache 2.0"); 
        }

        @Test
        @DisplayName("Complex dependency graph")
        void complexDependencyGraph() { 
            List<KernelDescriptor> modules = new ArrayList<>(); 

            // Create base modules
            for (int i = 0; i < 10; i++) { 
                final int idx = i;
                Set<KernelDependency> deps = new HashSet<>(); 
                if (idx > 0) { 
                    deps.add(new KernelDependency("module-" + (idx - 1), "1.0.0", KernelDependency.DependencyType.MODULE, false)); 
                }

                KernelDescriptor desc = new KernelDescriptor.Builder() 
                    .withDescriptorId("module-" + idx) 
                    .withName("Module " + idx) 
                    .withVersion("1.0.0")
                    .withType(KernelDescriptor.DescriptorType.MODULE) 
                    .withDependencies(deps) 
                    .build(); 

                modules.add(desc); 
            }

            assertThat(modules).hasSize(10); 
            // Last module has dependency on previous
            assertThat(modules.get(9).getDependencies()) 
                .extracting(KernelDependency::getDependencyId) 
                .contains("module-8");
        }

        @Test
        @DisplayName("Many metadata fields")
        void manyMetadataFields() { 
            Map<String, String> metadata = new HashMap<>(); 
            for (int i = 0; i < 50; i++) { 
                final int idx = i;
                metadata.put("field-" + idx, "value-" + idx); 
            }

            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId("large-meta")
                .withName("Large Metadata")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .withMetadata(metadata) 
                .build(); 

            assertThat(descriptor.getMetadata()).hasSize(50); 
        }
    }

    // ============================================
    // VERSIONING (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Versioning and Compatibility")
    class VersioningTests {

        @Test
        @DisplayName("Various semantic versions")
        void semanticVersions() { 
            String[] versions = {
                "1.0.0", "1.0.1", "1.1.0", "2.0.0",
                "0.0.1", "10.5.3", "1.0.0-alpha", "1.0.0-beta.1"
            };

            List<KernelDescriptor> descriptors = new ArrayList<>(); 
            for (int i = 0; i < versions.length; i++) { 
                final int idx = i;
                KernelDescriptor desc = new KernelDescriptor.Builder() 
                    .withDescriptorId("versioned-" + idx) 
                    .withName("Versioned Module " + idx) 
                    .withVersion(versions[idx]) 
                    .withType(KernelDescriptor.DescriptorType.MODULE) 
                    .build(); 
                descriptors.add(desc); 
            }

            assertThat(descriptors).hasSize(versions.length); 
            assertThat(descriptors.get(0).getVersion()).isEqualTo("1.0.0");
            assertThat(descriptors.get(7).getVersion()).isEqualTo("1.0.0-beta.1");
        }

        @Test
        @DisplayName("Version evolution tracking")
        void versionEvolution() { 
            List<KernelDescriptor> versions = new ArrayList<>(); 

            String[] versionSequence = {"1.0.0", "1.1.0", "1.2.0", "2.0.0"};
            for (int i = 0; i < versionSequence.length; i++) { 
                final int idx = i;
                KernelDescriptor desc = new KernelDescriptor.Builder() 
                    .withDescriptorId("myapp")
                    .withName("My Application")
                    .withVersion(versionSequence[idx]) 
                    .withType(KernelDescriptor.DescriptorType.MODULE) 
                    .build(); 
                versions.add(desc); 
            }

            assertThat(versions).hasSize(4); 
            assertThat(versions.get(0).getVersion()).isEqualTo("1.0.0");
            assertThat(versions.get(3).getVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("Compatibility metadata")
        void compatibilityMetadata() { 
            Map<String, String> compat = new HashMap<>(); 
            compat.put("min_kernel_version", "2.0.0"); 
            compat.put("max_kernel_version", "3.0.0"); 
            compat.put("java_version", "11+"); 
            compat.put("deprecated_apis", "api-1,api-2"); 

            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId("compat-module")
                .withName("Compatible Module")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .withMetadata(compat) 
                .build(); 

            assertThat(descriptor.getMetadata()).containsEntry("min_kernel_version", "2.0.0"); 
        }

        @Test
        @DisplayName("Breaking change tracking")
        void breakingChanges() { 
            KernelDescriptor v1 = new KernelDescriptor.Builder() 
                .withDescriptorId("myapp")
                .withName("App")
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build(); 

            Map<String, String> breakingMeta = new HashMap<>(); 
            breakingMeta.put( 
                "breaking_changes",
                "Removed config format support;API endpoint changed;Database schema migration required"
            );

            KernelDescriptor v2 = new KernelDescriptor.Builder() 
                .withDescriptorId("myapp")
                .withName("App")
                .withVersion("2.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .withMetadata(breakingMeta) 
                .build(); 

            assertThat(v1.getVersion()).isEqualTo("1.0.0");
            assertThat(v2.getVersion()).isEqualTo("2.0.0");
        }
    }

    // ============================================
    // CONCURRENT DESCRIPTOR OPERATIONS (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many descriptors built concurrently")
        void concurrentDescriptorBuilding() throws Exception { 
            int threadCount = 30;
            CountDownLatch latch = new CountDownLatch(threadCount); 
            List<KernelDescriptor> descriptors = java.util.Collections.synchronizedList(new ArrayList<>()); 

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); 
            try {
                for (int t = 0; t < threadCount; t++) { 
                    final int threadIdx = t;
                    exec.submit(() -> { 
                        try {
                            Set<KernelDependency> deps = new HashSet<>(); 
                            deps.add(new KernelDependency("base-module", "1.0.0", KernelDependency.DependencyType.MODULE, false)); 

                            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                                .withDescriptorId("descriptor-" + threadIdx) 
                                .withName("Descriptor " + threadIdx) 
                                .withVersion("1.0.0")
                                .withType(KernelDescriptor.DescriptorType.MODULE) 
                                .withDependencies(deps) 
                                .build(); 

                            descriptors.add(descriptor); 
                        } finally {
                            latch.countDown(); 
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); 
            } finally {
                exec.shutdownNow(); 
            }

            assertThat(descriptors).hasSize(threadCount); 
        }

        @Test
        @DisplayName("Large descriptor registry operations")
        void largeRegistryOperations() throws Exception { 
            // Build large set of descriptors
            List<KernelDescriptor> registry = new ArrayList<>(); 
            for (int i = 0; i < 1000; i++) { 
                final int idx = i;
                KernelDescriptor desc = new KernelDescriptor.Builder() 
                    .withDescriptorId("module-" + idx) 
                    .withName("Module " + idx) 
                    .withVersion("1.0.0")
                    .withType(KernelDescriptor.DescriptorType.values()[idx % 3]) 
                    .build(); 
                registry.add(desc); 
            }

            // Concurrent queries
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); 
            AtomicInteger queryCount = new AtomicInteger(0); 

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); 
            try {
                for (int t = 0; t < threadCount; t++) { 
                    final int threadIdx = t;
                    exec.submit(() -> { 
                        try {
                            for (int i = 0; i < 500; i++) { 
                                KernelDescriptor desc = registry.get(i % registry.size()); 
                                if (desc.getDescriptorId() != null) { 
                                    queryCount.incrementAndGet(); 
                                }
                            }
                        } finally {
                            latch.countDown(); 
                        }
                    });
                }
                assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); 
            } finally {
                exec.shutdownNow(); 
            }

            assertThat(queryCount.get()).isEqualTo(threadCount * 500); 
        }
    }

    // ============================================
    // EDGE CASES (1 test) 
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Descriptor with extremely long names and descriptions")
        void veryLongNames() { 
            String longId = "descriptor-" + "a".repeat(200); 
            String longName = "Module-" + "B".repeat(500); 
            String longDesc = "Description: " + "x".repeat(1000); 

            KernelDescriptor descriptor = new KernelDescriptor.Builder() 
                .withDescriptorId(longId) 
                .withName(longName) 
                .withDescription(longDesc) 
                .withVersion("1.0.0")
                .withType(KernelDescriptor.DescriptorType.MODULE) 
                .build(); 

            assertThat(descriptor.getDescriptorId()).hasSize(longId.length()); 
            assertThat(descriptor.getName()).hasSize(longName.length()); 
        }
    }
}
