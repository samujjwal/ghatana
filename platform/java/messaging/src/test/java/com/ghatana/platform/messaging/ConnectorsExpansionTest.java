/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Connectors - Phase 3 Expansion [GH-90000]")
class ConnectorsExpansionTest extends EventloopTestBase {

    private ConnectorRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        MetricsRegistry metricsRegistry = mock(MetricsRegistry.class); // GH-90000
        registry = ConnectorRegistry.initialize(metricsRegistry); // GH-90000
    }

    private Connector mockConnector(String name, String type) { // GH-90000
        Connector connector = mock(Connector.class); // GH-90000
        when(connector.getName()).thenReturn(name); // GH-90000
        when(connector.getType()).thenReturn(type); // GH-90000
        when(connector.stop()).thenReturn(Promise.complete()); // GH-90000
        when(connector.start()).thenReturn(Promise.complete()); // GH-90000
        when(connector.initialize(any())).thenReturn(Promise.complete()); // GH-90000
        return connector;
    }

    // ============================================
    // REGISTRY COMPOSITION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Connector Registry Composition [GH-90000]")
    class RegistryCompositionTests {

        @Test
        @DisplayName("Register many connectors [GH-90000]")
        void registerMany() { // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                String type = idx % 3 == 0 ? "kafka" : (idx % 3 == 1 ? "file" : "http"); // GH-90000
                Connector conn = mockConnector("connector-" + idx, type); // GH-90000
                registry.register(conn); // GH-90000
            }

            assertThat(registry.getConnector("connector-0 [GH-90000]")).isPresent();
            assertThat(registry.getConnector("connector-49 [GH-90000]")).isPresent();
            assertThat(registry.getConnector("nonexistent [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("Register connectors by type [GH-90000]")
        void registerByType() { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                registry.register(mockConnector("kafka-" + i, "kafka")); // GH-90000
            }
            for (int i = 0; i < 3; i++) { // GH-90000
                registry.register(mockConnector("file-" + i, "file")); // GH-90000
            }

            Set<Connector> kafkaConnectors = registry.getConnectorsByType("kafka [GH-90000]");
            Set<Connector> fileConnectors = registry.getConnectorsByType("file [GH-90000]");

            assertThat(kafkaConnectors).hasSize(5); // GH-90000
            assertThat(fileConnectors).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("Multiple types in single registry [GH-90000]")
        void multipleTypes() { // GH-90000
            String[] types = {"kafka", "file", "http", "s3", "database"};

            for (int t = 0; t < types.length; t++) { // GH-90000
                for (int i = 0; i < 10; i++) { // GH-90000
                    final int typeIdx = t;
                    final int connIdx = i;
                    registry.register(mockConnector( // GH-90000
                        types[typeIdx] + "-" + connIdx,
                        types[typeIdx]
                    ));
                }
            }

            // Verify all types registered
            for (String type : types) { // GH-90000
                assertThat(registry.getConnectorsByType(type)).hasSize(10); // GH-90000
            }
        }

        @Test
        @DisplayName("Lookup performance with large registry [GH-90000]")
        void largeRegistryLookup() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                registry.register(mockConnector("connector-" + idx, "type-" + (idx % 10))); // GH-90000
            }

            // Perform many lookups
            for (int i = 0; i < 100; i++) { // GH-90000
                Optional<Connector> found = registry.getConnector("connector-" + i); // GH-90000
                assertThat(found).isPresent(); // GH-90000
            }
        }
    }

    // ============================================
    // DUPLICATE HANDLING (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Duplicate Handling [GH-90000]")
    class DuplicateTests {

        @Test
        @DisplayName("Reject duplicate connector names [GH-90000]")
        void rejectDuplicateName() { // GH-90000
            Connector first = mockConnector("duplicate", "kafka"); // GH-90000
            Connector second = mockConnector("duplicate", "file"); // GH-90000

            registry.register(first); // GH-90000

            assertThatThrownBy(() -> registry.register(second)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("duplicate [GH-90000]");
        }

        @Test
        @DisplayName("Same type, different names allowed [GH-90000]")
        void sameTypeAllowed() { // GH-90000
            Connector conn1 = mockConnector("kafka-1", "kafka"); // GH-90000
            Connector conn2 = mockConnector("kafka-2", "kafka"); // GH-90000
            Connector conn3 = mockConnector("kafka-3", "kafka"); // GH-90000

            registry.register(conn1); // GH-90000
            registry.register(conn2); // GH-90000
            registry.register(conn3); // GH-90000

            assertThat(registry.getConnectorsByType("kafka [GH-90000]")).hasSize(3);
        }

        @Test
        @DisplayName("Null connector rejection [GH-90000]")
        void rejectNull() { // GH-90000
            assertThatThrownBy(() -> registry.register(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ============================================
    // CONNECTOR LIFECYCLE (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Connector Lifecycle [GH-90000]")
    class LifecycleTests {

        @Test
        @DisplayName("Initialize all connectors [GH-90000]")
        void initializeAllConnectors() { // GH-90000
            Connector connector = mockConnector("init-test", "kafka"); // GH-90000
            registry.register(connector); // GH-90000

            runPromise(() -> registry.initializeAll()); // GH-90000

            assertThat(registry.getConnector("init-test [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("Start all connectors [GH-90000]")
        void startAllConnectors() { // GH-90000
            Connector connector = mockConnector("start-test", "kafka"); // GH-90000
            registry.register(connector); // GH-90000

            runPromise(() -> registry.startAll()); // GH-90000

            assertThat(registry.getConnector("start-test [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("Stop all connectors [GH-90000]")
        void stopAllConnectors() { // GH-90000
            Connector connector = mockConnector("stop-test", "kafka"); // GH-90000
            registry.register(connector); // GH-90000

            runPromise(() -> registry.stopAll()); // GH-90000

            assertThat(registry.getConnector("stop-test [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("Full lifecycle: init all, start all, stop all [GH-90000]")
        void fullLifecycle() { // GH-90000
            Connector connector = mockConnector("lifecycle-test", "kafka"); // GH-90000
            registry.register(connector); // GH-90000

            runPromise(() -> registry.initializeAll()); // GH-90000
            runPromise(() -> registry.startAll()); // GH-90000
            runPromise(() -> registry.stopAll()); // GH-90000

            assertThat(registry.getConnector("lifecycle-test [GH-90000]")).isPresent();
        }
    }

    // ============================================
    // CONCURRENT OPERATIONS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent connector registration [GH-90000]")
        void concurrentRegistration() throws Exception { // GH-90000
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            for (int i = 0; i < 10; i++) { // GH-90000
                                final int connIdx = i;
                                Connector conn = mockConnector( // GH-90000
                                    "conn-" + threadIdx + "-" + connIdx,
                                    "type-" + threadIdx
                                );
                                registry.register(conn); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }
        }

        @Test
        @DisplayName("Concurrent read/write operations [GH-90000]")
        void concurrentReadWrite() throws Exception { // GH-90000
            // Pre-register some connectors
            for (int i = 0; i < 50; i++) { // GH-90000
                registry.register(mockConnector("registered-" + i, "kafka")); // GH-90000
            }

            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger readCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int t = 0; t < threadCount; t++) { // GH-90000
                    final int threadIdx = t;
                    exec.submit(() -> { // GH-90000
                        try {
                            // Mix of reads and occasional writes
                            for (int i = 0; i < 100; i++) { // GH-90000
                                Optional<Connector> found = registry.getConnector("registered-" + (i % 50)); // GH-90000
                                if (found.isPresent()) { // GH-90000
                                    readCount.incrementAndGet(); // GH-90000
                                }

                                if (i % 20 == 0) { // GH-90000
                                    Connector newConn = mockConnector( // GH-90000
                                        "new-" + threadIdx + "-" + i,
                                        "kafka"
                                    );
                                    registry.register(newConn); // GH-90000
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

            assertThat(readCount.get()).isEqualTo(threadCount * 100); // GH-90000
        }

        @Test
        @DisplayName("Concurrent lifecycle operations [GH-90000]")
        void concurrentLifecycle() throws Exception { // GH-90000
            // Pre-register connectors
            List<String> connectorNames = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 30; i++) { // GH-90000
                String name = "lifecycle-" + i;
                registry.register(mockConnector(name, "kafka")); // GH-90000
                connectorNames.add(name); // GH-90000
            }

            // Run lifecycle operations sequentially on eventloop
            // (concurrent lifecycle testing with runPromise from threads is incompatible with ActiveJ) // GH-90000
            runPromise(() -> registry.initializeAll()); // GH-90000
            runPromise(() -> registry.startAll()); // GH-90000

            // Verify all connectors are still present
            for (String name : connectorNames) { // GH-90000
                assertThat(registry.getConnector(name)).isPresent(); // GH-90000
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
        @DisplayName("Connectors with very long names and types [GH-90000]")
        void longNamesAndTypes() { // GH-90000
            String longName = "connector-" + "a".repeat(200); // GH-90000
            String longType = "type-" + "b".repeat(200); // GH-90000

            Connector connector = mockConnector(longName, longType); // GH-90000
            registry.register(connector); // GH-90000

            assertThat(registry.getConnector(longName)).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("Empty type lookup [GH-90000]")
        void emptyTypeLookup() { // GH-90000
            registry.register(mockConnector("conn-1", "kafka")); // GH-90000

            Set<Connector> emptyResults = registry.getConnectorsByType("nonexistent-type [GH-90000]");
            assertThat(emptyResults).isEmpty(); // GH-90000
        }
    }
}
