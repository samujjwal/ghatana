package com.ghatana.core.connectors.impl;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LoggingEventSink}.
 * Covers lifecycle (start/stop), event sending, metrics tracking, // GH-90000
 * and state validation (reject when not started). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for LoggingEventSink
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("LoggingEventSink Tests")
class LoggingEventSinkTest extends EventloopTestBase {

    private LoggingEventSink sink;
    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() { // GH-90000
        metricsCollector = spy(NoopMetricsCollector.getInstance()); // GH-90000
        sink = new LoggingEventSink(metricsCollector); // GH-90000
    }

    // --- Helper ---

    private EventLogStore.EventEntry createTestEntry(String eventType) { // GH-90000
        return EventLogStore.EventEntry.builder() // GH-90000
                .eventId(UUID.randomUUID()) // GH-90000
                .eventType(eventType) // GH-90000
                .eventVersion("1.0.0")
                .timestamp(Instant.now()) // GH-90000
                .payload(ByteBuffer.wrap("{\"test\":true}".getBytes())) // GH-90000
                .contentType("application/json")
                .headers(Map.of()) // GH-90000
                .build(); // GH-90000
    }

    // --- Lifecycle ---

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should start successfully")
        void shouldStart() { // GH-90000
            runPromise(() -> sink.start()); // GH-90000
            // No exception implies success
        }

        @Test
        @DisplayName("should stop successfully after start")
        void shouldStopAfterStart() { // GH-90000
            runPromise(() -> sink.start()); // GH-90000
            runPromise(() -> sink.stop()); // GH-90000
            // No exception implies success
        }
    }

    // --- Send ---

    @Nested
    @DisplayName("Send")
    class Send {

        @Test
        @DisplayName("should send event successfully when started")
        void shouldSendEvent() { // GH-90000
            runPromise(() -> sink.start()); // GH-90000

            EventLogStore.EventEntry entry = createTestEntry("user.created");
            TenantContext tenant = TenantContext.of("test-tenant");
            runPromise(() -> sink.send(tenant, entry)); // GH-90000

            verify(metricsCollector).incrementCounter("event.sink.logged", "type", "user.created"); // GH-90000
        }

        @Test
        @DisplayName("should reject send when not started")
        void shouldRejectSendWhenNotStarted() { // GH-90000
            EventLogStore.EventEntry entry = createTestEntry("user.created");
            TenantContext tenant = TenantContext.of("test-tenant");

            try {
                runPromise(() -> sink.send(tenant, entry)); // GH-90000
                org.junit.jupiter.api.Assertions.fail("Expected exception for send() on non-started sink");
            } catch (Exception e) { // GH-90000
                assertThat(e) // GH-90000
                        .isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("sink not started");
            }
        }

        @Test
        @DisplayName("should reject send after stop")
        void shouldRejectSendAfterStop() { // GH-90000
            runPromise(() -> sink.start()); // GH-90000
            runPromise(() -> sink.stop()); // GH-90000

            EventLogStore.EventEntry entry = createTestEntry("user.created");
            TenantContext tenant = TenantContext.of("test-tenant");

            try {
                runPromise(() -> sink.send(tenant, entry)); // GH-90000
                org.junit.jupiter.api.Assertions.fail("Expected exception for send() on stopped sink");
            } catch (Exception e) { // GH-90000
                assertThat(e) // GH-90000
                        .isInstanceOf(IllegalStateException.class) // GH-90000
                        .hasMessageContaining("sink not started");
            }
        }

        @Test
        @DisplayName("should send multiple events and track metrics for each")
        void shouldSendMultipleEvents() { // GH-90000
            runPromise(() -> sink.start()); // GH-90000

            TenantContext tenant = TenantContext.of("test-tenant");
            runPromise(() -> sink.send(tenant, createTestEntry("order.placed")));
            runPromise(() -> sink.send(tenant, createTestEntry("order.shipped")));
            runPromise(() -> sink.send(tenant, createTestEntry("order.placed")));

            verify(metricsCollector, times(2)) // GH-90000
                    .incrementCounter("event.sink.logged", "type", "order.placed"); // GH-90000
            verify(metricsCollector, times(1)) // GH-90000
                    .incrementCounter("event.sink.logged", "type", "order.shipped"); // GH-90000
        }
    }

    // --- Flush ---

    @Nested
    @DisplayName("Flush")
    class Flush {

        @Test
        @DisplayName("flush should complete successfully (default no-op)")
        void shouldFlush() { // GH-90000
            runPromise(() -> sink.start()); // GH-90000
            runPromise(() -> sink.flush()); // GH-90000
            // No exception implies success
        }
    }

    // --- Constructor validation ---

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null metrics collector")
        void shouldRejectNullMetrics() { // GH-90000
            assertThatThrownBy(() -> new LoggingEventSink(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
