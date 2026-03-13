package com.ghatana.pipeline.registry.connector;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HttpIngressConnector}.
 *
 * Covers:
 * - Full lifecycle: initialize → connect → ingest → disconnect → close
 * - Authentication enforcement (NONE, BEARER, API_KEY)
 * - Event type validation
 * - State machine guards (ingest rejected when not CONNECTED)
 * - Metrics recording
 * - Null event rejection
 */
@ExtendWith(MockitoExtension.class)
class HttpIngressConnectorTest extends EventloopTestBase {

    @Mock
    private MetricsCollector metricsCollector;

    private ConnectorSpec spec;

    @BeforeEach
    void setUp() {
        spec = ConnectorSpec.builder()
                .id("test-connector")
                .endpoint("/events/test")
                .tenantId("tenant-1")
                .build();
    }

    private Event testEvent() {
        return GEvent.builder()
                .typeTenantVersion("tenant-1", "test.event", "v1")
                .addPayload("key", "value")
                .build();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("initialize() transitions CREATED → INITIALIZED")
        void initialize_transitionsState() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            Promise<Void> result = connector.initialize();
            assertThat(result.getException()).isNull();
        }

        @Test
        @DisplayName("initialize() twice returns error on second call")
        void initialize_twice_returnsError() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize();
            Promise<Void> second = connector.initialize();
            assertThat(second.getException()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("connect() after initialize() sets accepting=true")
        void connect_afterInitialize_acceptsEvents() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize();
            connector.connect();
            Promise<Boolean> healthy = connector.isHealthy();
            assertThat(runPromise(() -> healthy)).isTrue();
        }

        @Test
        @DisplayName("disconnect() pauses ingestion but keeps connector alive")
        void disconnect_pausesIngestion() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize();
            connector.connect();
            connector.disconnect();
            Promise<Boolean> healthy = connector.isHealthy();
            assertThat(runPromise(() -> healthy)).isFalse();
        }

        @Test
        @DisplayName("close() on closed connector is idempotent")
        void close_idempotent() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize();
            connector.connect();
            connector.close();
            Promise<Void> second = connector.close();
            assertThat(second.getException()).isNull();
        }
    }

    // ─── Ingest ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Ingest")
    class IngestTests {

        @Test
        @DisplayName("ingest() succeeds when connected and auth=NONE")
        void ingest_connected_noAuth_succeeds() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize();
            connector.connect();

            Promise<Void> result = connector.ingest(testEvent(), null);
            assertThat(result.getException()).isNull();
            verify(metricsCollector).incrementCounter(
                    eq("aep.connector.http_ingress.events.received"),
                    anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("ingest() rejected when not connected")
        void ingest_notConnected_rejected() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize(); // INITIALIZED but not CONNECTED

            Promise<Void> result = connector.ingest(testEvent(), null);
            assertThat(result.getException()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ingest(null event) throws immediately")
        void ingest_nullEvent_throws() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize();
            connector.connect();

            Promise<Void> result = connector.ingest(null, null);
            assertThat(result.getException()).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("ingest() rejected after close()")
        void ingest_afterClose_rejected() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize();
            connector.connect();
            connector.close();

            Promise<Void> result = connector.ingest(testEvent(), null);
            assertThat(result.getException()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ingest() rejects event with wrong type when expected_event_type set")
        void ingest_wrongEventType_rejected() {
            ConnectorSpec typedSpec = ConnectorSpec.builder()
                    .id("typed-connector")
                    .endpoint("/events/typed")
                    .tenantId("tenant-1")
                    .properties(Map.of("expected_event_type", "order.created"))
                    .build();

            HttpIngressConnector connector = HttpIngressConnector.of(typedSpec, metricsCollector);
            connector.initialize();
            connector.connect();

            Event wrongType = GEvent.builder()
                    .typeTenantVersion("tenant-1", "user.registered", "v1")
                    .addPayload("key", "value")
                    .build();

            Promise<Void> result = connector.ingest(wrongType, null);
            assertThat(result.getException()).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("order.created");
        }
    }

    // ─── Authentication ───────────────────────────────────────────────

    @Nested
    @DisplayName("Authentication")
    class AuthTests {

        @Test
        @DisplayName("BEARER auth: valid token accepted")
        void bearer_validToken_accepted() {
            ConnectorSpec authSpec = ConnectorSpec.builder()
                    .id("bearer-connector")
                    .endpoint("/events/secure")
                    .tenantId("tenant-1")
                    .properties(Map.of("auth.type", "BEARER", "auth.token", "secret-token-123"))
                    .build();

            HttpIngressConnector connector = HttpIngressConnector.of(authSpec, metricsCollector);
            connector.initialize();
            connector.connect();

            Promise<Void> result = connector.ingest(testEvent(), "Bearer secret-token-123");
            assertThat(result.getException()).isNull();
        }

        @Test
        @DisplayName("BEARER auth: invalid token rejected with auth_failures metric")
        void bearer_invalidToken_rejected() {
            ConnectorSpec authSpec = ConnectorSpec.builder()
                    .id("bearer-connector")
                    .endpoint("/events/secure")
                    .tenantId("tenant-1")
                    .properties(Map.of("auth.type", "BEARER", "auth.token", "secret-token-123"))
                    .build();

            HttpIngressConnector connector = HttpIngressConnector.of(authSpec, metricsCollector);
            connector.initialize();
            connector.connect();

            Promise<Void> result = connector.ingest(testEvent(), "Bearer wrong-token");
            assertThat(result.getException()).isInstanceOf(IllegalAccessException.class)
                    .hasMessageContaining("Unauthorized");
            verify(metricsCollector).incrementCounter(
                    eq("aep.connector.http_ingress.auth_failures"),
                    anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("BEARER auth: missing Authorization header rejected")
        void bearer_missingHeader_rejected() {
            ConnectorSpec authSpec = ConnectorSpec.builder()
                    .id("bearer-connector")
                    .endpoint("/events/secure")
                    .tenantId("tenant-1")
                    .properties(Map.of("auth.type", "BEARER", "auth.token", "secret"))
                    .build();

            HttpIngressConnector connector = HttpIngressConnector.of(authSpec, metricsCollector);
            connector.initialize();
            connector.connect();

            Promise<Void> result = connector.ingest(testEvent(), null);
            assertThat(result.getException()).isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("API_KEY auth: valid key accepted")
        void apiKey_validKey_accepted() {
            ConnectorSpec authSpec = ConnectorSpec.builder()
                    .id("apikey-connector")
                    .endpoint("/events/apikey")
                    .tenantId("tenant-1")
                    .properties(Map.of("auth.type", "API_KEY", "auth.token", "my-api-key"))
                    .build();

            HttpIngressConnector connector = HttpIngressConnector.of(authSpec, metricsCollector);
            connector.initialize();
            connector.connect();

            Promise<Void> result = connector.ingest(testEvent(), "my-api-key");
            assertThat(result.getException()).isNull();
        }

        @Test
        @DisplayName("NONE auth: null token accepted")
        void noAuth_nullToken_accepted() {
            HttpIngressConnector connector = HttpIngressConnector.of(spec, metricsCollector);
            connector.initialize();
            connector.connect();

            Promise<Void> result = connector.ingest(testEvent(), null);
            assertThat(result.getException()).isNull();
        }
    }
}
