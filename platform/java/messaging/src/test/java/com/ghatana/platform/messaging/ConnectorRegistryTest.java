package com.ghatana.core.connectors;

import com.ghatana.platform.observability.MetricsRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ConnectorRegistry}.
 * Covers registration, lookup by name, lookup by type, duplicate registration,
 * removal, and batch lifecycle operations.
 *
 * @doc.type class
 * @doc.purpose Unit tests for ConnectorRegistry
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("ConnectorRegistry Tests [GH-90000]")
class ConnectorRegistryTest extends EventloopTestBase {

    private ConnectorRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        MetricsRegistry metricsRegistry = mock(MetricsRegistry.class); // GH-90000
        registry = ConnectorRegistry.initialize(metricsRegistry); // GH-90000
    }

    // --- Helper ---

    private Connector mockConnector(String name, String type) { // GH-90000
        Connector connector = mock(Connector.class); // GH-90000
        when(connector.getName()).thenReturn(name); // GH-90000
        when(connector.getType()).thenReturn(type); // GH-90000
        when(connector.stop()).thenReturn(Promise.complete()); // GH-90000
        when(connector.start()).thenReturn(Promise.complete()); // GH-90000
        when(connector.initialize(any())).thenReturn(Promise.complete()); // GH-90000
        return connector;
    }

    // --- Registration ---

    @Nested
    @DisplayName("Registration [GH-90000]")
    class Registration {

        @Test
        @DisplayName("should register a connector successfully [GH-90000]")
        void shouldRegisterConnector() { // GH-90000
            Connector connector = mockConnector("kafka-source", "kafka"); // GH-90000

            registry.register(connector); // GH-90000

            Optional<Connector> found = registry.getConnector("kafka-source [GH-90000]");
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().getName()).isEqualTo("kafka-source [GH-90000]");
        }

        @Test
        @DisplayName("should reject null connector [GH-90000]")
        void shouldRejectNullConnector() { // GH-90000
            assertThatThrownBy(() -> registry.register(null)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("null [GH-90000]");
        }

        @Test
        @DisplayName("should reject duplicate connector name [GH-90000]")
        void shouldRejectDuplicateName() { // GH-90000
            Connector first = mockConnector("dup-name", "kafka"); // GH-90000
            Connector second = mockConnector("dup-name", "file"); // GH-90000

            registry.register(first); // GH-90000

            assertThatThrownBy(() -> registry.register(second)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("dup-name [GH-90000]");
        }

        @Test
        @DisplayName("should allow multiple connectors with different names [GH-90000]")
        void shouldAllowMultipleConnectors() { // GH-90000
            Connector c1 = mockConnector("source-1", "kafka"); // GH-90000
            Connector c2 = mockConnector("source-2", "file"); // GH-90000
            Connector c3 = mockConnector("sink-1", "kafka"); // GH-90000

            registry.register(c1); // GH-90000
            registry.register(c2); // GH-90000
            registry.register(c3); // GH-90000

            assertThat(registry.getConnectors()).hasSize(3); // GH-90000
        }
    }

    // --- Lookup ---

    @Nested
    @DisplayName("Lookup [GH-90000]")
    class Lookup {

        @Test
        @DisplayName("should return empty optional for unknown name [GH-90000]")
        void shouldReturnEmptyForUnknownName() { // GH-90000
            assertThat(registry.getConnector("nonexistent [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("should find connector by name [GH-90000]")
        void shouldFindByName() { // GH-90000
            Connector c = mockConnector("my-conn", "kafka"); // GH-90000
            registry.register(c); // GH-90000

            Optional<Connector> found = registry.getConnector("my-conn [GH-90000]");
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get()).isSameAs(c); // GH-90000
        }

        @Test
        @DisplayName("should find connectors by type [GH-90000]")
        void shouldFindByType() { // GH-90000
            Connector k1 = mockConnector("kafka-1", "kafka"); // GH-90000
            Connector k2 = mockConnector("kafka-2", "kafka"); // GH-90000
            Connector f1 = mockConnector("file-1", "file"); // GH-90000

            registry.register(k1); // GH-90000
            registry.register(k2); // GH-90000
            registry.register(f1); // GH-90000

            Set<Connector> kafkaConnectors = registry.getConnectorsByType("kafka [GH-90000]");
            assertThat(kafkaConnectors).hasSize(2); // GH-90000
            assertThat(kafkaConnectors).extracting(Connector::getName) // GH-90000
                    .containsExactlyInAnyOrder("kafka-1", "kafka-2"); // GH-90000
        }

        @Test
        @DisplayName("should return empty set for unknown type [GH-90000]")
        void shouldReturnEmptySetForUnknownType() { // GH-90000
            assertThat(registry.getConnectorsByType("unknown [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("should return all registered connectors [GH-90000]")
        void shouldReturnAllConnectors() { // GH-90000
            Connector c1 = mockConnector("a", "kafka"); // GH-90000
            Connector c2 = mockConnector("b", "file"); // GH-90000

            registry.register(c1); // GH-90000
            registry.register(c2); // GH-90000

            Set<Connector> all = registry.getConnectors(); // GH-90000
            assertThat(all).hasSize(2); // GH-90000
        }
    }

    // --- Unregister ---

    @Nested
    @DisplayName("Unregister [GH-90000]")
    class Unregister {

        @Test
        @DisplayName("should unregister a connector and stop it [GH-90000]")
        void shouldUnregisterAndStop() { // GH-90000
            Connector c = mockConnector("to-remove", "kafka"); // GH-90000
            registry.register(c); // GH-90000

            runPromise(() -> registry.unregister("to-remove [GH-90000]"));

            assertThat(registry.getConnector("to-remove [GH-90000]")).isEmpty();
            verify(c).stop(); // GH-90000
        }

        @Test
        @DisplayName("should complete successfully when unregistering unknown name [GH-90000]")
        void shouldCompleteForUnknownName() { // GH-90000
            // Should not throw — just completes
            runPromise(() -> registry.unregister("nonexistent [GH-90000]"));
        }

        @Test
        @DisplayName("should not affect other connectors when one is unregistered [GH-90000]")
        void shouldNotAffectOtherConnectors() { // GH-90000
            Connector c1 = mockConnector("keep", "kafka"); // GH-90000
            Connector c2 = mockConnector("remove", "file"); // GH-90000

            registry.register(c1); // GH-90000
            registry.register(c2); // GH-90000

            runPromise(() -> registry.unregister("remove [GH-90000]"));

            assertThat(registry.getConnector("keep [GH-90000]")).isPresent();
            assertThat(registry.getConnector("remove [GH-90000]")).isEmpty();
            assertThat(registry.getConnectors()).hasSize(1); // GH-90000
        }
    }

    // --- Batch Lifecycle ---

    @Nested
    @DisplayName("Batch Lifecycle [GH-90000]")
    class BatchLifecycle {

        @Test
        @DisplayName("should start all registered connectors [GH-90000]")
        void shouldStartAll() { // GH-90000
            Connector c1 = mockConnector("a", "kafka"); // GH-90000
            Connector c2 = mockConnector("b", "file"); // GH-90000

            registry.register(c1); // GH-90000
            registry.register(c2); // GH-90000

            runPromise(registry::startAll); // GH-90000

            verify(c1).start(); // GH-90000
            verify(c2).start(); // GH-90000
        }

        @Test
        @DisplayName("should stop all registered connectors [GH-90000]")
        void shouldStopAll() { // GH-90000
            Connector c1 = mockConnector("a", "kafka"); // GH-90000
            Connector c2 = mockConnector("b", "file"); // GH-90000

            registry.register(c1); // GH-90000
            registry.register(c2); // GH-90000

            runPromise(registry::stopAll); // GH-90000

            verify(c1).stop(); // GH-90000
            verify(c2).stop(); // GH-90000
        }

        @Test
        @DisplayName("should complete start/stop on empty registry [GH-90000]")
        void shouldCompleteOnEmptyRegistry() { // GH-90000
            runPromise(registry::startAll); // GH-90000
            runPromise(registry::stopAll); // GH-90000
            // No exception means success
        }
    }

    // --- Singleton / getInstance ---

    @Nested
    @DisplayName("Singleton [GH-90000]")
    class Singleton {

        @Test
        @DisplayName("should return the initialized instance via getInstance() [GH-90000]")
        void shouldReturnInstance() { // GH-90000
            ConnectorRegistry instance = ConnectorRegistry.getInstance(); // GH-90000
            assertThat(instance).isSameAs(registry); // GH-90000
        }
    }

    // --- Metrics ---

    @Nested
    @DisplayName("Metrics [GH-90000]")
    class Metrics {

        @Test
        @DisplayName("should return metrics for all connectors [GH-90000]")
        void shouldReturnMetrics() { // GH-90000
            Connector c1 = mockConnector("a", "kafka"); // GH-90000
            Connector c2 = mockConnector("b", "file"); // GH-90000

            Connector.ConnectorMetrics m1 = mock(Connector.ConnectorMetrics.class); // GH-90000
            Connector.ConnectorMetrics m2 = mock(Connector.ConnectorMetrics.class); // GH-90000
            when(c1.getMetrics()).thenReturn(m1); // GH-90000
            when(c2.getMetrics()).thenReturn(m2); // GH-90000

            registry.register(c1); // GH-90000
            registry.register(c2); // GH-90000

            var metrics = registry.getMetrics(); // GH-90000
            assertThat(metrics).hasSize(2); // GH-90000
            assertThat(metrics).containsKeys("a", "b"); // GH-90000
        }
    }
}
