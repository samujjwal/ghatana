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
 * Covers lifecycle (start/stop), event sending, metrics tracking,
 * and state validation (reject when not started).
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
    void setUp() {
        metricsCollector = spy(NoopMetricsCollector.getInstance());
        sink = new LoggingEventSink(metricsCollector);
    }

    // --- Helper ---

    private EventLogStore.EventEntry createTestEntry(String eventType) {
        return EventLogStore.EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .eventVersion("1.0.0")
                .timestamp(Instant.now())
                .payload(ByteBuffer.wrap("{\"test\":true}".getBytes()))
                .contentType("application/json")
                .headers(Map.of())
                .build();
    }

    // --- Lifecycle ---

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should start successfully")
        void shouldStart() {
            runPromise(() -> sink.start());
            // No exception implies success
        }

        @Test
        @DisplayName("should stop successfully after start")
        void shouldStopAfterStart() {
            runPromise(() -> sink.start());
            runPromise(() -> sink.stop());
            // No exception implies success
        }
    }

    // --- Send ---

    @Nested
    @DisplayName("Send")
    class Send {

        @Test
        @DisplayName("should send event successfully when started")
        void shouldSendEvent() {
            runPromise(() -> sink.start());

            EventLogStore.EventEntry entry = createTestEntry("user.created");
            TenantContext tenant = TenantContext.of("test-tenant");
            runPromise(() -> sink.send(tenant, entry));

            verify(metricsCollector).incrementCounter("event.sink.logged", "type", "user.created");
        }

        @Test
        @DisplayName("should reject send when not started")
        void shouldRejectSendWhenNotStarted() {
            EventLogStore.EventEntry entry = createTestEntry("user.created");
            TenantContext tenant = TenantContext.of("test-tenant");

            try {
                runPromise(() -> sink.send(tenant, entry));
                org.junit.jupiter.api.Assertions.fail("Expected exception for send() on non-started sink");
            } catch (Exception e) {
                assertThat(e)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("sink not started");
            }
        }

        @Test
        @DisplayName("should reject send after stop")
        void shouldRejectSendAfterStop() {
            runPromise(() -> sink.start());
            runPromise(() -> sink.stop());

            EventLogStore.EventEntry entry = createTestEntry("user.created");
            TenantContext tenant = TenantContext.of("test-tenant");

            try {
                runPromise(() -> sink.send(tenant, entry));
                org.junit.jupiter.api.Assertions.fail("Expected exception for send() on stopped sink");
            } catch (Exception e) {
                assertThat(e)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("sink not started");
            }
        }

        @Test
        @DisplayName("should send multiple events and track metrics for each")
        void shouldSendMultipleEvents() {
            runPromise(() -> sink.start());

            TenantContext tenant = TenantContext.of("test-tenant");
            runPromise(() -> sink.send(tenant, createTestEntry("order.placed")));
            runPromise(() -> sink.send(tenant, createTestEntry("order.shipped")));
            runPromise(() -> sink.send(tenant, createTestEntry("order.placed")));

            verify(metricsCollector, times(2))
                    .incrementCounter("event.sink.logged", "type", "order.placed");
            verify(metricsCollector, times(1))
                    .incrementCounter("event.sink.logged", "type", "order.shipped");
        }
    }

    // --- Flush ---

    @Nested
    @DisplayName("Flush")
    class Flush {

        @Test
        @DisplayName("flush should complete successfully (default no-op)")
        void shouldFlush() {
            runPromise(() -> sink.start());
            runPromise(() -> sink.flush());
            // No exception implies success
        }
    }

    // --- Constructor validation ---

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null metrics collector")
        void shouldRejectNullMetrics() {
            assertThatThrownBy(() -> new LoggingEventSink(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
