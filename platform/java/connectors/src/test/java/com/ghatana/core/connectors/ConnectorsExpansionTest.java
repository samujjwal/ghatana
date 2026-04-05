/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.connectors;

import com.ghatana.platform.observability.MetricsRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 3 Expansion tests for Connectors module.
 * Tests registry operations, connector lifecycle, and concurrent access.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for connector registration and lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Connectors - Phase 3 Expansion")
class ConnectorsExpansionTest extends EventloopTestBase {

    private ConnectorRegistry registry;

    @BeforeEach
    void setUp() {
        MetricsRegistry metricsRegistry = mock(MetricsRegistry.class);
        registry = ConnectorRegistry.initialize(metricsRegistry);
    }

    private Connector mockConnector(String name, String type) {
        Connector connector = mock(Connector.class);
        when(connector.getName()).thenReturn(name);
        when(connector.getType()).thenReturn(type);
        when(connector.stop()).thenReturn(Promise.complete());
        when(connector.start()).thenReturn(Promise.complete());
        when(connector.initialize(any())).thenReturn(Promise.complete());
        return connector;
    }

    // ============================================
    // REGISTRY COMPOSITION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Connector Registry Composition")
    class RegistryCompositionTests {

        @Test
        @DisplayName("Register many connectors")
        void registerMany() {
            for (int i = 0; i < 50; i++) {
                final int idx = i;
                String type = idx % 3 == 0 ? "kafka" : (idx % 3 == 1 ? "file" : "http");
                Connector conn = mockConnector("connector-" + idx, type);
                registry.register(conn);
            }

            assertThat(registry.getConnector("connector-0")).isPresent();
            assertThat(registry.getConnector("connector-49")).isPresent();
            assertThat(registry.getConnector("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("Register connectors by type")
        void registerByType() {
            for (int i = 0; i < 5; i++) {
                registry.register(mockConnector("kafka-" + i, "kafka"));
            }
            for (int i = 0; i < 3; i++) {
                registry.register(mockConnector("file-" + i, "file"));
            }

            Set<Connector> kafkaConnectors = registry.getConnectorsByType("kafka");
            Set<Connector> fileConnectors = registry.getConnectorsByType("file");

            assertThat(kafkaConnectors).hasSize(5);
            assertThat(fileConnectors).hasSize(3);
        }

        @Test
        @DisplayName("Multiple types in single registry")
        void multipleTypes() {
            String[] types = {"kafka", "file", "http", "s3", "database"};
            
            for (int t = 0; t < types.length; t++) {
                for (int i = 0; i < 10; i++) {
                    final int typeIdx = t;
                    final int connIdx = i;
                    registry.register(mockConnector(
                        types[typeIdx] + "-" + connIdx,
                        types[typeIdx]
                    ));
                }
            }

            // Verify all types registered
            for (String type : types) {
                assertThat(registry.getConnectorsByType(type)).hasSize(10);
            }
        }

        @Test
        @DisplayName("Lookup performance with large registry")
        void largeRegistryLookup() {
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                registry.register(mockConnector("connector-" + idx, "type-" + (idx % 10)));
            }

            // Perform many lookups
            for (int i = 0; i < 100; i++) {
                Optional<Connector> found = registry.getConnector("connector-" + i);
                assertThat(found).isPresent();
            }
        }
    }

    // ============================================
    // DUPLICATE HANDLING (3 tests)
    // ============================================

    @Nested
    @DisplayName("Duplicate Handling")
    class DuplicateTests {

        @Test
        @DisplayName("Reject duplicate connector names")
        void rejectDuplicateName() {
            Connector first = mockConnector("duplicate", "kafka");
            Connector second = mockConnector("duplicate", "file");

            registry.register(first);

            assertThatThrownBy(() -> registry.register(second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        }

        @Test
        @DisplayName("Same type, different names allowed")
        void sameTypeAllowed() {
            Connector conn1 = mockConnector("kafka-1", "kafka");
            Connector conn2 = mockConnector("kafka-2", "kafka");
            Connector conn3 = mockConnector("kafka-3", "kafka");

            registry.register(conn1);
            registry.register(conn2);
            registry.register(conn3);

            assertThat(registry.getConnectorsByType("kafka")).hasSize(3);
        }

        @Test
        @DisplayName("Null connector rejection")
        void rejectNull() {
            assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================================
    // CONNECTOR LIFECYCLE (4 tests)
    // ============================================

    @Nested
    @DisplayName("Connector Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Initialize connector successful")
        void initializeConnector() {
            Connector connector = mockConnector("init-test", "kafka");
            registry.register(connector);

            runPromise(() -> registry.initializeConnector("init-test"));

            assertThat(registry.getConnector("init-test")).isPresent();
        }

        @Test
        @DisplayName("Start connector lifecycle")
        void startConnector() {
            Connector connector = mockConnector("start-test", "kafka");
            registry.register(connector);

            runPromise(() -> registry.startConnector("start-test"));

            assertThat(registry.getConnector("start-test")).isPresent();
        }

        @Test
        @DisplayName("Stop connector lifecycle")
        void stopConnector() {
            Connector connector = mockConnector("stop-test", "kafka");
            registry.register(connector);

            runPromise(() -> registry.stopConnector("stop-test"));

            assertThat(registry.getConnector("stop-test")).isPresent();
        }

        @Test
        @DisplayName("Full lifecycle: init, start, stop")
        void fullLifecycle() {
            Connector connector = mockConnector("lifecycle-test", "kafka");
            registry.register(connector);

            runPromise(() -> registry.initializeConnector("lifecycle-test"));
            runPromise(() -> registry.startConnector("lifecycle-test"));
            runPromise(() -> registry.stopConnector("lifecycle-test"));

            assertThat(registry.getConnector("lifecycle-test")).isPresent();
        }
    }

    // ============================================
    // CONCURRENT OPERATIONS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent connector registration")
        void concurrentRegistration() throws Exception {
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < 10; i++) {
                                final int connIdx = i;
                                Connector conn = mockConnector(
                                    "conn-" + threadIdx + "-" + connIdx,
                                    "type-" + threadIdx
                                );
                                registry.register(conn);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }
        }

        @Test
        @DisplayName("Concurrent read/write operations")
        void concurrentReadWrite() throws Exception {
            // Pre-register some connectors
            for (int i = 0; i < 50; i++) {
                registry.register(mockConnector("registered-" + i, "kafka"));
            }

            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger readCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            // Mix of reads and occasional writes
                            for (int i = 0; i < 100; i++) {
                                Optional<Connector> found = registry.getConnector("registered-" + (i % 50));
                                if (found.isPresent()) {
                                    readCount.incrementAndGet();
                                }
                                
                                if (i % 20 == 0) {
                                    Connector newConn = mockConnector(
                                        "new-" + threadIdx + "-" + i,
                                        "kafka"
                                    );
                                    registry.register(newConn);
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

            assertThat(readCount.get()).isEqualTo(threadCount * 100);
        }

        @Test
        @DisplayName("Concurrent lifecycle operations")
        void concurrentLifecycle() throws Exception {
            // Pre-register connectors
            List<String> connectorNames = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                String name = "lifecycle-" + i;
                registry.register(mockConnector(name, "kafka"));
                connectorNames.add(name);
            }

            int threadCount = 15;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            for (int i = 0; i < connectorNames.size(); i++) {
                                String connName = connectorNames.get(i);
                                runPromise(() -> registry.initializeConnector(connName));
                                runPromise(() -> registry.startConnector(connName));
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
        }
    }

    // ============================================
    // EDGE CASES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Connectors with very long names and types")
        void longNamesAndTypes() {
            String longName = "connector-" + "a".repeat(200);
            String longType = "type-" + "b".repeat(200);

            Connector connector = mockConnector(longName, longType);
            registry.register(connector);

            assertThat(registry.getConnector(longName)).isPresent();
        }

        @Test
        @DisplayName("Empty type lookup")
        void emptyTypeLookup() {
            registry.register(mockConnector("conn-1", "kafka"));
            
            Set<Connector> emptyResults = registry.getConnectorsByType("nonexistent-type");
            assertThat(emptyResults).isEmpty();
        }
    }
}
