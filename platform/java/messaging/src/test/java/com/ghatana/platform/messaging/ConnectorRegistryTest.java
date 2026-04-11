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
@DisplayName("ConnectorRegistry Tests")
class ConnectorRegistryTest extends EventloopTestBase {

    private ConnectorRegistry registry;

    @BeforeEach
    void setUp() {
        MetricsRegistry metricsRegistry = mock(MetricsRegistry.class);
        registry = ConnectorRegistry.initialize(metricsRegistry);
    }

    // --- Helper ---

    private Connector mockConnector(String name, String type) {
        Connector connector = mock(Connector.class);
        when(connector.getName()).thenReturn(name);
        when(connector.getType()).thenReturn(type);
        when(connector.stop()).thenReturn(Promise.complete());
        when(connector.start()).thenReturn(Promise.complete());
        when(connector.initialize(any())).thenReturn(Promise.complete());
        return connector;
    }

    // --- Registration ---

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("should register a connector successfully")
        void shouldRegisterConnector() {
            Connector connector = mockConnector("kafka-source", "kafka");

            registry.register(connector);

            Optional<Connector> found = registry.getConnector("kafka-source");
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("kafka-source");
        }

        @Test
        @DisplayName("should reject null connector")
        void shouldRejectNullConnector() {
            assertThatThrownBy(() -> registry.register(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should reject duplicate connector name")
        void shouldRejectDuplicateName() {
            Connector first = mockConnector("dup-name", "kafka");
            Connector second = mockConnector("dup-name", "file");

            registry.register(first);

            assertThatThrownBy(() -> registry.register(second))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dup-name");
        }

        @Test
        @DisplayName("should allow multiple connectors with different names")
        void shouldAllowMultipleConnectors() {
            Connector c1 = mockConnector("source-1", "kafka");
            Connector c2 = mockConnector("source-2", "file");
            Connector c3 = mockConnector("sink-1", "kafka");

            registry.register(c1);
            registry.register(c2);
            registry.register(c3);

            assertThat(registry.getConnectors()).hasSize(3);
        }
    }

    // --- Lookup ---

    @Nested
    @DisplayName("Lookup")
    class Lookup {

        @Test
        @DisplayName("should return empty optional for unknown name")
        void shouldReturnEmptyForUnknownName() {
            assertThat(registry.getConnector("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("should find connector by name")
        void shouldFindByName() {
            Connector c = mockConnector("my-conn", "kafka");
            registry.register(c);

            Optional<Connector> found = registry.getConnector("my-conn");
            assertThat(found).isPresent();
            assertThat(found.get()).isSameAs(c);
        }

        @Test
        @DisplayName("should find connectors by type")
        void shouldFindByType() {
            Connector k1 = mockConnector("kafka-1", "kafka");
            Connector k2 = mockConnector("kafka-2", "kafka");
            Connector f1 = mockConnector("file-1", "file");

            registry.register(k1);
            registry.register(k2);
            registry.register(f1);

            Set<Connector> kafkaConnectors = registry.getConnectorsByType("kafka");
            assertThat(kafkaConnectors).hasSize(2);
            assertThat(kafkaConnectors).extracting(Connector::getName)
                    .containsExactlyInAnyOrder("kafka-1", "kafka-2");
        }

        @Test
        @DisplayName("should return empty set for unknown type")
        void shouldReturnEmptySetForUnknownType() {
            assertThat(registry.getConnectorsByType("unknown")).isEmpty();
        }

        @Test
        @DisplayName("should return all registered connectors")
        void shouldReturnAllConnectors() {
            Connector c1 = mockConnector("a", "kafka");
            Connector c2 = mockConnector("b", "file");

            registry.register(c1);
            registry.register(c2);

            Set<Connector> all = registry.getConnectors();
            assertThat(all).hasSize(2);
        }
    }

    // --- Unregister ---

    @Nested
    @DisplayName("Unregister")
    class Unregister {

        @Test
        @DisplayName("should unregister a connector and stop it")
        void shouldUnregisterAndStop() {
            Connector c = mockConnector("to-remove", "kafka");
            registry.register(c);

            runPromise(() -> registry.unregister("to-remove"));

            assertThat(registry.getConnector("to-remove")).isEmpty();
            verify(c).stop();
        }

        @Test
        @DisplayName("should complete successfully when unregistering unknown name")
        void shouldCompleteForUnknownName() {
            // Should not throw — just completes
            runPromise(() -> registry.unregister("nonexistent"));
        }

        @Test
        @DisplayName("should not affect other connectors when one is unregistered")
        void shouldNotAffectOtherConnectors() {
            Connector c1 = mockConnector("keep", "kafka");
            Connector c2 = mockConnector("remove", "file");

            registry.register(c1);
            registry.register(c2);

            runPromise(() -> registry.unregister("remove"));

            assertThat(registry.getConnector("keep")).isPresent();
            assertThat(registry.getConnector("remove")).isEmpty();
            assertThat(registry.getConnectors()).hasSize(1);
        }
    }

    // --- Batch Lifecycle ---

    @Nested
    @DisplayName("Batch Lifecycle")
    class BatchLifecycle {

        @Test
        @DisplayName("should start all registered connectors")
        void shouldStartAll() {
            Connector c1 = mockConnector("a", "kafka");
            Connector c2 = mockConnector("b", "file");

            registry.register(c1);
            registry.register(c2);

            runPromise(registry::startAll);

            verify(c1).start();
            verify(c2).start();
        }

        @Test
        @DisplayName("should stop all registered connectors")
        void shouldStopAll() {
            Connector c1 = mockConnector("a", "kafka");
            Connector c2 = mockConnector("b", "file");

            registry.register(c1);
            registry.register(c2);

            runPromise(registry::stopAll);

            verify(c1).stop();
            verify(c2).stop();
        }

        @Test
        @DisplayName("should complete start/stop on empty registry")
        void shouldCompleteOnEmptyRegistry() {
            runPromise(registry::startAll);
            runPromise(registry::stopAll);
            // No exception means success
        }
    }

    // --- Singleton / getInstance ---

    @Nested
    @DisplayName("Singleton")
    class Singleton {

        @Test
        @DisplayName("should return the initialized instance via getInstance()")
        void shouldReturnInstance() {
            ConnectorRegistry instance = ConnectorRegistry.getInstance();
            assertThat(instance).isSameAs(registry);
        }
    }

    // --- Metrics ---

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should return metrics for all connectors")
        void shouldReturnMetrics() {
            Connector c1 = mockConnector("a", "kafka");
            Connector c2 = mockConnector("b", "file");

            Connector.ConnectorMetrics m1 = mock(Connector.ConnectorMetrics.class);
            Connector.ConnectorMetrics m2 = mock(Connector.ConnectorMetrics.class);
            when(c1.getMetrics()).thenReturn(m1);
            when(c2.getMetrics()).thenReturn(m2);

            registry.register(c1);
            registry.register(c2);

            var metrics = registry.getMetrics();
            assertThat(metrics).hasSize(2);
            assertThat(metrics).containsKeys("a", "b");
        }
    }
}
