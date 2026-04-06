/*
 * Copyright (c) 2026 Ghatana Inc.
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

/**
 * Phase 3 Expansion tests for Kernel-Persistence module.
 * Tests module registry, configuration resolution, and audit trail persistence at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for kernel persistence subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("KernelPersistence - Phase 3 Expansion")
class KernelPersistenceExpansionTest {

    private JdbcModuleRegistry registry;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:registry_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        registry = new JdbcModuleRegistry(dataSource);
        registry.ensureSchema();
    }

    // ============================================
    // MODULE REGISTRATION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Module Registration")
    class RegistrationTests {

        @Test
        @DisplayName("Register many modules")
        void registerManyModules() {
            for (int i = 0; i < 100; i++) {
                String moduleId = "platform:java:module-" + i;
                registry.registerModule(moduleId, "1.0.0", "REGISTERED");
            }

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules();
            assertThat(all).hasSize(100);
        }

        @Test
        @DisplayName("Register and update modules")
        void registerAndUpdate() {
            String moduleId = "platform:java:kernel";

            registry.registerModule(moduleId, "1.0.0", "REGISTERED");
            registry.registerModule(moduleId, "1.0.1", "STARTED");
            registry.registerModule(moduleId, "1.0.2", "HEALTHY");

            JdbcModuleRegistry.ModuleRegistration reg = registry.getModule(moduleId).orElseThrow();
            assertThat(reg.moduleVersion()).isEqualTo("1.0.2");
            assertThat(reg.moduleStatus()).isEqualTo("HEALTHY");
        }

        @Test
        @DisplayName("Register with various statuses")
        void variousStatuses() {
            String[] statuses = {"REGISTERED", "STARTED", "HEALTHY", "DEGRADED", "FAILED", "STOPPED"};

            for (int i = 0; i < 60; i++) {
                String moduleId = "module-" + i;
                String status = statuses[i % statuses.length];
                registry.registerModule(moduleId, "1.0", status);
            }

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules();
            assertThat(all).hasSize(60);
        }

        @Test
        @DisplayName("Register modules with semantic versioning")
        void semanticVersioning() {
            String moduleId = "platform:java:core";
            String[] versions = {"0.1.0", "0.2.0", "1.0.0", "1.1.0", "2.0.0"};

            for (String version : versions) {
                registry.registerModule(moduleId, version, "REGISTERED");
            }

            JdbcModuleRegistry.ModuleRegistration reg = registry.getModule(moduleId).orElseThrow();
            assertThat(reg.moduleVersion()).isEqualTo("2.0.0");
        }
    }

    // ============================================
    // MODULE RETRIEVAL AND QUERYING (3 tests)
    // ============================================

    @Nested
    @DisplayName("Module Retrieval")
    class RetrievalTests {

        @Test
        @DisplayName("Fetch modules by ID")
        void fetchById() {
            registry.registerModule("m1", "1.0", "REGISTERED");
            registry.registerModule("m2", "1.1", "STARTED");
            registry.registerModule("m3", "1.2", "HEALTHY");

            Optional<JdbcModuleRegistry.ModuleRegistration> m1 = registry.getModule("m1");
            Optional<JdbcModuleRegistry.ModuleRegistration> m2 = registry.getModule("m2");
            Optional<JdbcModuleRegistry.ModuleRegistration> m3 = registry.getModule("m3");

            assertThat(m1).isPresent();
            assertThat(m2).isPresent();
            assertThat(m3).isPresent();

            assertThat(m1.get().moduleStatus()).isEqualTo("REGISTERED");
            assertThat(m2.get().moduleStatus()).isEqualTo("STARTED");
            assertThat(m3.get().moduleStatus()).isEqualTo("HEALTHY");
        }

        @Test
        @DisplayName("List all modules efficiently")
        void listAll() {
            for (int i = 0; i < 500; i++) {
                final int idx = i;
                registry.registerModule("module-" + idx, "1.0.0", "REGISTERED");
            }

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules();
            assertThat(all).hasSize(500);
        }

        @Test
        @DisplayName("Non-existent module returns empty")
        void nonExistentModule() {
            registry.registerModule("m1", "1.0", "REGISTERED");

            Optional<JdbcModuleRegistry.ModuleRegistration> nonExistent = registry.getModule("does-not-exist");
            assertThat(nonExistent).isEmpty();
        }
    }

    // ============================================
    // MODULE REMOVAL (2 tests)
    // ============================================

    @Nested
    @DisplayName("Module Removal")
    class RemovalTests {

        @Test
        @DisplayName("Remove registered modules")
        void removeModules() {
            registry.registerModule("m1", "1.0", "REGISTERED");
            registry.registerModule("m2", "1.1", "STARTED");
            registry.registerModule("m3", "1.2", "HEALTHY");

            registry.removeModule("m2");

            Optional<JdbcModuleRegistry.ModuleRegistration> m2 = registry.getModule("m2");
            assertThat(m2).isEmpty();

            Optional<JdbcModuleRegistry.ModuleRegistration> m1 = registry.getModule("m1");
            assertThat(m1).isPresent();
        }

        @Test
        @DisplayName("Remove many modules")
        void removeManyModules() {
            List<String> moduleIds = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                String moduleId = "module-" + i;
                registry.registerModule(moduleId, "1.0", "REGISTERED");
                moduleIds.add(moduleId);
            }

            // Remove every other module
            for (int i = 0; i < moduleIds.size(); i += 2) {
                registry.removeModule(moduleIds.get(i));
            }

            List<JdbcModuleRegistry.ModuleRegistration> remaining = registry.listModules();
            assertThat(remaining).hasSize(50);
        }
    }

    // ============================================
    // CONCURRENT PERSISTENCE OPERATIONS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent module registration")
        void concurrentRegistration() throws Exception {
            int threadCount = 20;
            int modulesPerThread = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < modulesPerThread; i++) {
                                final int modIdx = i;
                                String moduleId = "module-" + threadIdx + "-" + modIdx;
                                registry.registerModule(moduleId, "1.0.0", "REGISTERED");
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

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules();
            assertThat(all).hasSize(threadCount * modulesPerThread);
        }

        @Test
        @DisplayName("Concurrent reads and writes")
        void concurrentReadsAndWrites() throws Exception {
            // Pre-populate
            for (int i = 0; i < 200; i++) {
                final int idx = i;
                registry.registerModule("module-" + idx, "1.0", "REGISTERED");
            }

            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger readCount = new AtomicInteger(0);
            AtomicInteger writeCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < 100; i++) {
                                final int idx = i;
                                if (threadIdx % 2 == 0) {
                                    // Write operation
                                    registry.registerModule(
                                        "module-w-" + threadIdx + "-" + idx,
                                        "1.0",
                                        "REGISTERED");
                                    writeCount.incrementAndGet();
                                } else {
                                    // Read operation
                                    Optional<JdbcModuleRegistry.ModuleRegistration> result =
                                        registry.getModule("module-" + (idx % 200));
                                    if (result.isPresent()) {
                                        readCount.incrementAndGet();
                                    }
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(readCount.get()).isGreaterThan(0);
            assertThat(writeCount.get()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Concurrent mixed operations (register, update, remove, list)")
        void concurrentMixedOperations() throws Exception {
            // Pre-populate
            for (int i = 0; i < 300; i++) {
                final int idx = i;
                registry.registerModule("module-" + idx, "1.0", "REGISTERED");
            }

            int threadCount = 30;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < 80; i++) {
                                final int idx = i;
                                int op = idx % 4;

                                if (op == 0) {
                                    // Register
                                    registry.registerModule(
                                        "module-t" + threadIdx + "-" + idx,
                                        "1.0",
                                        "REGISTERED");
                                } else if (op == 1) {
                                    // Update
                                    String moduleId = "module-" + (idx % 300);
                                    registry.registerModule(moduleId, "1.1", "STARTED");
                                } else if (op == 2) {
                                    // Remove
                                    String moduleId = "module-" + ((threadIdx * 80 + idx) % 300);
                                    registry.removeModule(moduleId);
                                } else {
                                    // List
                                    List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules();
                                    assertThat(all).isNotNull();
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(25, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }
        }
    }

    // ============================================
    // EDGE CASES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very long module IDs and versions")
        void veryLongNames() {
            String longModuleId = "platform:java:" + "a".repeat(200);
            String longVersion = "1." + "0".repeat(200);
            String longStatus = "STATUS_" + "X".repeat(200);

            registry.registerModule(longModuleId, longVersion, longStatus);

            Optional<JdbcModuleRegistry.ModuleRegistration> reg = registry.getModule(longModuleId);
            assertThat(reg).isPresent();
            assertThat(reg.get().moduleVersion()).isEqualTo(longVersion);
        }

        @Test
        @DisplayName("High volume module registry with many versions")
        void highVolumeVersionTracking() {
            String baseModuleId = "platform:java:kernel";

            for (int i = 0; i < 100; i++) {
                String version = "1." + i + ".0";
                registry.registerModule(baseModuleId, version, "REGISTERED");
            }

            JdbcModuleRegistry.ModuleRegistration latest = registry.getModule(baseModuleId).orElseThrow();
            assertThat(latest.moduleVersion()).isEqualTo("1.99.0");

            List<JdbcModuleRegistry.ModuleRegistration> all = registry.listModules();
            assertThat(all).hasSize(1);  // Only latest version stored per module
        }
    }
}
