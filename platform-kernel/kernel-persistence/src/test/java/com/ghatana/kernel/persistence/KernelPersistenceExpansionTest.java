/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.kernel.persistence;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 Expansion tests for Kernel-Persistence module.
 * Tests module registry, configuration resolution, and audit trail persistence at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for kernel persistence subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("KernelPersistence - Phase 3 Expansion [GH-90000]")
class KernelPersistenceExpansionTest {

    private JdbcModuleRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        JdbcDataSource dataSource = new JdbcDataSource(); // GH-90000
        dataSource.setURL("jdbc:h2:mem:registry_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"); // GH-90000
        dataSource.setUser("sa [GH-90000]");
        dataSource.setPassword("sa [GH-90000]");

        registry = new JdbcModuleRegistry(dataSource); // GH-90000
        registry.ensureSchema(); // GH-90000
    }

    // ============================================
    // MODULE REGISTRATION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Module Registration [GH-90000]")
    class RegistrationTests {

        @Test
        @DisplayName("Register many modules [GH-90000]")
        void registerManyModules() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                String moduleId = "platform:java:module-" + i;
                registry.registerModule(moduleId, "1.0.0", "REGISTERED"); // GH-90000
            }

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules(); // GH-90000
            assertThat(all).hasSize(100); // GH-90000
        }

        @Test
        @DisplayName("Register and update modules [GH-90000]")
        void registerAndUpdate() { // GH-90000
            String moduleId = "platform:java:kernel";

            registry.registerModule(moduleId, "1.0.0", "REGISTERED"); // GH-90000
            registry.registerModule(moduleId, "1.0.1", "STARTED"); // GH-90000
            registry.registerModule(moduleId, "1.0.2", "HEALTHY"); // GH-90000

            JdbcModuleRegistry.ModuleRegistration reg = registry.getModule(moduleId).orElseThrow(); // GH-90000
            assertThat(reg.moduleVersion()).isEqualTo("1.0.2 [GH-90000]");
            assertThat(reg.moduleStatus()).isEqualTo("HEALTHY [GH-90000]");
        }

        @Test
        @DisplayName("Register with various statuses [GH-90000]")
        void variousStatuses() { // GH-90000
            String[] statuses = {"REGISTERED", "STARTED", "HEALTHY", "DEGRADED", "FAILED", "STOPPED"};

            for (int i = 0; i < 60; i++) { // GH-90000
                String moduleId = "module-" + i;
                String status = statuses[i % statuses.length];
                registry.registerModule(moduleId, "1.0", status); // GH-90000
            }

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules(); // GH-90000
            assertThat(all).hasSize(60); // GH-90000
        }

        @Test
        @DisplayName("Register modules with semantic versioning [GH-90000]")
        void semanticVersioning() { // GH-90000
            String moduleId = "platform:java:core";
            String[] versions = {"0.1.0", "0.2.0", "1.0.0", "1.1.0", "2.0.0"};

            for (String version : versions) { // GH-90000
                registry.registerModule(moduleId, version, "REGISTERED"); // GH-90000
            }

            JdbcModuleRegistry.ModuleRegistration reg = registry.getModule(moduleId).orElseThrow(); // GH-90000
            assertThat(reg.moduleVersion()).isEqualTo("2.0.0 [GH-90000]");
        }
    }

    // ============================================
    // MODULE RETRIEVAL AND QUERYING (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Module Retrieval [GH-90000]")
    class RetrievalTests {

        @Test
        @DisplayName("Fetch modules by ID [GH-90000]")
        void fetchById() { // GH-90000
            registry.registerModule("m1", "1.0", "REGISTERED"); // GH-90000
            registry.registerModule("m2", "1.1", "STARTED"); // GH-90000
            registry.registerModule("m3", "1.2", "HEALTHY"); // GH-90000

            Optional<JdbcModuleRegistry.ModuleRegistration> m1 = registry.getModule("m1 [GH-90000]");
            Optional<JdbcModuleRegistry.ModuleRegistration> m2 = registry.getModule("m2 [GH-90000]");
            Optional<JdbcModuleRegistry.ModuleRegistration> m3 = registry.getModule("m3 [GH-90000]");

            assertThat(m1).isPresent(); // GH-90000
            assertThat(m2).isPresent(); // GH-90000
            assertThat(m3).isPresent(); // GH-90000

            assertThat(m1.get().moduleStatus()).isEqualTo("REGISTERED [GH-90000]");
            assertThat(m2.get().moduleStatus()).isEqualTo("STARTED [GH-90000]");
            assertThat(m3.get().moduleStatus()).isEqualTo("HEALTHY [GH-90000]");
        }

        @Test
        @DisplayName("List all modules efficiently [GH-90000]")
        void listAll() { // GH-90000
            for (int i = 0; i < 500; i++) { // GH-90000
                final int idx = i;
                registry.registerModule("module-" + idx, "1.0.0", "REGISTERED"); // GH-90000
            }

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules(); // GH-90000
            assertThat(all).hasSize(500); // GH-90000
        }

        @Test
        @DisplayName("Non-existent module returns empty [GH-90000]")
        void nonExistentModule() { // GH-90000
            registry.registerModule("m1", "1.0", "REGISTERED"); // GH-90000

            Optional<JdbcModuleRegistry.ModuleRegistration> nonExistent = registry.getModule("does-not-exist [GH-90000]");
            assertThat(nonExistent).isEmpty(); // GH-90000
        }
    }

    // ============================================
    // MODULE REMOVAL (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Module Removal [GH-90000]")
    class RemovalTests {

        @Test
        @DisplayName("Remove registered modules [GH-90000]")
        void removeModules() { // GH-90000
            registry.registerModule("m1", "1.0", "REGISTERED"); // GH-90000
            registry.registerModule("m2", "1.1", "STARTED"); // GH-90000
            registry.registerModule("m3", "1.2", "HEALTHY"); // GH-90000

            registry.removeModule("m2 [GH-90000]");

            Optional<JdbcModuleRegistry.ModuleRegistration> m2 = registry.getModule("m2 [GH-90000]");
            assertThat(m2).isEmpty(); // GH-90000

            Optional<JdbcModuleRegistry.ModuleRegistration> m1 = registry.getModule("m1 [GH-90000]");
            assertThat(m1).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("Remove many modules [GH-90000]")
        void removeManyModules() { // GH-90000
            List<String> moduleIds = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                String moduleId = "module-" + i;
                registry.registerModule(moduleId, "1.0", "REGISTERED"); // GH-90000
                moduleIds.add(moduleId); // GH-90000
            }

            // Remove every other module
            for (int i = 0; i < moduleIds.size(); i += 2) { // GH-90000
                registry.removeModule(moduleIds.get(i)); // GH-90000
            }

            List<JdbcModuleRegistry.ModuleRegistration> remaining = registry.listModules(); // GH-90000
            assertThat(remaining).hasSize(50); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT PERSISTENCE OPERATIONS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent module registration [GH-90000]")
        void concurrentRegistration() throws Exception { // GH-90000
            int threadCount = 20;
            int modulesPerThread = 50;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < modulesPerThread; i++) { // GH-90000
                                final int modIdx = i;
                                String moduleId = "module-" + threadIdx + "-" + modIdx;
                                registry.registerModule(moduleId, "1.0.0", "REGISTERED"); // GH-90000
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

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules(); // GH-90000
            assertThat(all).hasSize(threadCount * modulesPerThread); // GH-90000
        }

        @Test
        @DisplayName("Concurrent reads and writes [GH-90000]")
        void concurrentReadsAndWrites() throws Exception { // GH-90000
            // Pre-populate
            for (int i = 0; i < 200; i++) { // GH-90000
                final int idx = i;
                registry.registerModule("module-" + idx, "1.0", "REGISTERED"); // GH-90000
            }

            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger readCount = new AtomicInteger(0); // GH-90000
            AtomicInteger writeCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 100; i++) { // GH-90000
                                final int idx = i;
                                if (threadIdx % 2 == 0) { // GH-90000
                                    // Write operation
                                    registry.registerModule( // GH-90000
                                        "module-w-" + threadIdx + "-" + idx,
                                        "1.0",
                                        "REGISTERED");
                                    writeCount.incrementAndGet(); // GH-90000
                                } else {
                                    // Read operation
                                    Optional<JdbcModuleRegistry.ModuleRegistration> result =
                                        registry.getModule("module-" + (idx % 200)); // GH-90000
                                    if (result.isPresent()) { // GH-90000
                                        readCount.incrementAndGet(); // GH-90000
                                    }
                                }
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(readCount.get()).isGreaterThan(0); // GH-90000
            assertThat(writeCount.get()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("Concurrent mixed operations (register, update, remove, list) [GH-90000]")
        void concurrentMixedOperations() throws Exception { // GH-90000
            // Pre-populate
            for (int i = 0; i < 300; i++) { // GH-90000
                final int idx = i;
                registry.registerModule("module-" + idx, "1.0", "REGISTERED"); // GH-90000
            }

            int threadCount = 30;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 80; i++) { // GH-90000
                                final int idx = i;
                                int op = idx % 4;

                                if (op == 0) { // GH-90000
                                    // Register
                                    registry.registerModule( // GH-90000
                                        "module-t" + threadIdx + "-" + idx,
                                        "1.0",
                                        "REGISTERED");
                                } else if (op == 1) { // GH-90000
                                    // Update
                                    String moduleId = "module-" + (idx % 300); // GH-90000
                                    registry.registerModule(moduleId, "1.1", "STARTED"); // GH-90000
                                } else if (op == 2) { // GH-90000
                                    // Remove
                                    String moduleId = "module-" + ((threadIdx * 80 + idx) % 300); // GH-90000
                                    registry.removeModule(moduleId); // GH-90000
                                } else {
                                    // List
                                    List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules(); // GH-90000
                                    assertThat(all).isNotNull(); // GH-90000
                                }
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(25, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }
        }
    }

    // ============================================
    // EDGE CASES (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very long module IDs and versions [GH-90000]")
        void veryLongNames() { // GH-90000
            String longModuleId = "platform:java:" + "a".repeat(200); // GH-90000
            String longVersion = "1." + "0".repeat(200); // GH-90000
            String longStatus = "STATUS_" + "X".repeat(200); // GH-90000

            assertThatThrownBy(() -> registry.registerModule(longModuleId, longVersion, longStatus)) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        @DisplayName("High volume module registry with many versions [GH-90000]")
        void highVolumeVersionTracking() { // GH-90000
            String baseModuleId = "platform:java:kernel";

            for (int i = 0; i < 100; i++) { // GH-90000
                String version = "1." + i + ".0";
                registry.registerModule(baseModuleId, version, "REGISTERED"); // GH-90000
            }

            JdbcModuleRegistry.ModuleRegistration latest = registry.getModule(baseModuleId).orElseThrow(); // GH-90000
            assertThat(latest.moduleVersion()).isEqualTo("1.99.0 [GH-90000]");

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules(); // GH-90000
            assertThat(all).hasSize(1);  // Only latest version stored per module // GH-90000
        }
    }
}
