package com.ghatana.core.connectors.impl;

import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.core.event.cloud.EventTypeRef;
import com.ghatana.core.event.cloud.Version;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.EventId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    private EventRecord createTestRecord(String eventType) {
        return EventRecord.builder()
                .tenantId(TenantId.random())
                .typeRef(EventTypeRef.of(eventType, 1, 0))
                .eventId(EventId.random())
                .occurrenceTime(Instant.now())
                .detectionTime(Instant.now())
                .headers(Map.of())
                .contentType(ContentType.JSON)
                .schemaUri("urn:test:schema")
                .payload(ByteBuffer.wrap("{\"test\":true}".getBytes()))
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

            EventRecord record = createTestRecord("user.created");
            runPromise(() -> sink.send(record));

            // Verify metrics were recorded
            verify(metricsCollector).incrementCounter("event.sink.logged", "type", "user.created");
        }

        @Test
        @DisplayName("should reject send when not started")
        void shouldRejectSendWhenNotStarted() {
            EventRecord record = createTestRecord("user.created");

            try {
                runPromise(() -> sink.send(record));
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

            EventRecord record = createTestRecord("user.created");

            try {
                runPromise(() -> sink.send(record));
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

            runPromise(() -> sink.send(createTestRecord("order.placed")));
            runPromise(() -> sink.send(createTestRecord("order.shipped")));
            runPromise(() -> sink.send(createTestRecord("order.placed")));

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
            try {
                new LoggingEventSink(null);
                org.junit.jupiter.api.Assertions.fail("Expected NullPointerException");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }
}
