package com.ghatana.core.connectors.impl;

import com.ghatana.core.connectors.EventSink;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Production-grade logging event sink writing events to application logs with metrics tracking.
 *
 * <p><b>Purpose</b><br>
 * Provides simple event persistence via logging for debugging, development, and
 * low-volume production scenarios. Records metrics for observability and supports
 * lifecycle management (start/stop).
 *
 * <p><b>Architecture Role</b><br>
 * Event sink implementation in core/connectors/impl for log-based output.
 * Used by:
 * - Development Environments - Debug event flows
 * - Testing - Verify event publishing without external dependencies
 * - Low-Volume Production - Simple event auditing
 * - Observability - Track event counts by type
 * - Debugging - Troubleshoot event processing issues
 *
 * <p><b>Sink Features</b><br>
 * - <b>Log-Based Output</b>: Writes events to SLF4J logger
 * - <b>Metrics Collection</b>: Tracks event.sink.logged counter by type
 * - <b>Lifecycle Management</b>: start()/stop() control
 * - <b>State Validation</b>: Rejects events when not started
 * - <b>Promise-Based API</b>: ActiveJ Promise for async integration
 * - <b>Type Tracking</b>: Metrics tagged by event type
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic usage
 * MetricsCollector metrics = MetricsCollectorFactory.create(registry);
 * LoggingEventSink sink = new LoggingEventSink(metrics);
 * 
 * // Start sink
 * sink.start().whenComplete((v, e) -> {
 *     if (e == null) {
 *         logger.info("Logging sink started");
 *     }
 * });
 * 
 * // Send event
 * EventRecord event = EventRecord.create(
 *     EventId.random(),
 *     EventType.of("user.created"),
 *     Map.of("email", "john@example.com")
 * );
 * 
 * sink.send(event).whenComplete((v, e) -> {
 *     if (e == null) {
 *         logger.info("Event logged");
 *     }
 * });
 * 
 * // Stop sink
 * sink.stop();
 *
 * // 2. Integration with event pipeline
 * public class EventPipeline {
 *     private final EventSink sink;
 *     
 *     public EventPipeline(MetricsCollector metrics) {
 *         this.sink = new LoggingEventSink(metrics);
 *     }
 *     
 *     public Promise<Void> start() {
 *         return sink.start();
 *     }
 *     
 *     public Promise<Void> publish(EventRecord event) {
 *         return sink.send(event);
 *     }
 *     
 *     public Promise<Void> shutdown() {
 *         return sink.stop();
 *     }
 * }
 *
 * // 3. Development debugging
 * @Configuration
 * public class DevConfig {
 *     @Bean
 *     public EventSink eventSink(MetricsCollector metrics) {
 *         // Use logging sink in development
 *         LoggingEventSink sink = new LoggingEventSink(metrics);
 *         sink.start();  // Auto-start
 *         return sink;
 *     }
 * }
 *
 * // 4. Testing without external dependencies
 * class EventProcessorTest {
 *     private LoggingEventSink sink;
 *     
 *     @BeforeEach
 *     void setup() {
 *         MetricsCollector metrics = NoopMetricsCollector.getInstance();
 *         sink = new LoggingEventSink(metrics);
 *         sink.start();
 *     }
 *     
 *     @Test
 *     void shouldPublishEvent() {
 *         EventRecord event = createTestEvent();
 *         
 *         // No external dependencies - just logs
 *         Promise<Void> result = sink.send(event);
 *         
 *         assertThat(result).succeedsWithin(Duration.ofSeconds(1));
 *     }
 * }
 *
 * // 5. Multi-sink configuration
 * public class ProductionEventPublisher {
 *     private final List<EventSink> sinks;
 *     
 *     public ProductionEventPublisher(
 *             KafkaEventSink kafkaSink,
 *             LoggingEventSink loggingSink) {
 *         this.sinks = List.of(kafkaSink, loggingSink);
 *     }
 *     
 *     public Promise<Void> publish(EventRecord event) {
 *         // Send to Kafka + log for debugging
 *         return Promise.all(sinks.stream()
 *             .map(sink -> sink.send(event))
 *             .collect(Collectors.toList()));
 *     }
 * }
 *
 * // 6. Conditional logging based on event type
 * public class FilteringLoggingSink implements EventSink {
 *     private final LoggingEventSink delegate;
 *     private final Set<String> loggedTypes;
 *     
 *     public FilteringLoggingSink(
 *             MetricsCollector metrics,
 *             Set<String> loggedTypes) {
 *         this.delegate = new LoggingEventSink(metrics);
 *         this.loggedTypes = loggedTypes;
 *     }
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord event) {
 *         // Only log specific event types
 *         if (loggedTypes.contains(event.typeRef().name())) {
 *             return delegate.send(event);
 *         }
 *         return Promise.complete();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Log Output Format</b><br>
 * <pre>
 * [LoggingEventSink] event: EventRecord{
 *   id=evt_123,
 *   type=user.created,
 *   timestamp=2025-11-06T10:30:00Z,
 *   payload={email=john@example.com, userId=123}
 * }
 * </pre>
 *
 * <p><b>Metrics Emitted</b><br>
 * - <b>event.sink.logged</b>: Counter with tag type={eventType}
 * - Example: event.sink.logged{type=user.created} = 42
 *
 * <p><b>Lifecycle States</b><br>
 * - <b>Created</b>: Sink created, not started (rejects events)
 * - <b>Started</b>: Sink started, accepts events
 * - <b>Stopped</b>: Sink stopped, rejects events
 *
 * <p><b>Error Handling</b><br>
 * - Throws IllegalStateException if send() called before start()
 * - Returns failed Promise with exception
 * - Does not retry (fire-and-forget logging)
 *
 * <p><b>Use Cases</b><br>
 * - <b>Development</b>: Debug event flows without Kafka/database
 * - <b>Testing</b>: Unit tests without external dependencies
 * - <b>Auditing</b>: Simple event audit trail in logs
 * - <b>Debugging</b>: Troubleshoot event processing issues
 * - <b>Low-Volume</b>: Small-scale production event logging
 *
 * <p><b>Limitations</b><br>
 * - Not suitable for high-volume production (log bloat)
 * - No persistence guarantee (logs may rotate/be lost)
 * - No replay capability
 * - No ordering guarantees across restarts
 * - Performance limited by logging framework
 *
 * <p><b>Best Practices</b><br>
 * - Use for development/testing, not high-volume production
 * - Configure log level appropriately (INFO for events)
 * - Use structured logging (JSON) for log aggregation
 * - Monitor metrics for event volume trends
 * - Consider multi-sink pattern (Kafka + logging)
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - volatile started field ensures visibility.
 * Metrics collector must be thread-safe (standard implementation is).
 *
 * @see EventSink
 * @see MetricsCollector
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Logging event sink with metrics tracking for debugging and development
 * @doc.layer core
 * @doc.pattern Adapter
 */
public final class LoggingEventSink implements EventSink {
    private static final Logger logger = LoggerFactory.getLogger(LoggingEventSink.class);

    private final MetricsCollector metrics;
    private volatile boolean started = false;

    public LoggingEventSink(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public Promise<Void> start() {
        started = true;
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started = false;
        return Promise.complete();
    }

    @Override
    public Promise<Void> send(EventRecord record) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("sink not started"));
        }
        logger.info("[LoggingEventSink] event: {}", record);
        metrics.incrementCounter("event.sink.logged", "type", record.typeRef().name());
        return Promise.complete();
    }
}
