package com.ghatana.core.connectors.impl.source;

import com.ghatana.core.connectors.EventSource;
import com.ghatana.core.connectors.IngestEvent;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Production-grade Kafka event source adapter for consuming events from Kafka topics.
 *
 * <p><b>Purpose</b><br>
 * Provides EventSource abstraction over Kafka consumer, enabling event ingestion
 * from Kafka topics without exposing Kafka client dependencies to core connectors.
 * Delegates to KafkaConsumerAdapter for Kafka-specific operations.
 *
 * <p><b>Architecture Role</b><br>
 * Event source adapter in core/connectors/impl/source for Kafka integration.
 * Used by:
 * - Event Ingestion Pipelines - Consume events from Kafka
 * - Multi-Tenant Systems - Topic-per-tenant consumption
 * - Event Replay - Replay historical events from Kafka
 * - Stream Processing - Kafka as event stream source
 * - Integration Patterns - External system event ingestion
 *
 * <p><b>Source Features</b><br>
 * - <b>Kafka Consumer Abstraction</b>: Hides Kafka client details
 * - <b>Lifecycle Management</b>: start()/stop() control
 * - <b>Promise-Based API</b>: ActiveJ Promise for async integration
 * - <b>State Validation</b>: Rejects polling when not started
 * - <b>Auto-Cleanup</b>: Closes consumer on stop()
 * - <b>Delegation Pattern</b>: KafkaConsumerAdapter handles Kafka specifics
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic Kafka event consumption
 * KafkaConsumerAdapter kafkaAdapter = new KafkaConsumerAdapter(
 *     "localhost:9092",
 *     "event-consumer-group",
 *     List.of("events-topic")
 * );
 * 
 * KafkaEventSource source = new KafkaEventSource(kafkaAdapter);
 * 
 * // Start source
 * source.start().whenComplete((v, e) -> {
 *     if (e == null) {
 *         logger.info("Kafka source started");
 *     }
 * });
 * 
 * // Poll for events
 * source.next().whenComplete((event, e) -> {
 *     if (e == null) {
 *         logger.info("Received event: {}", event);
 *         processEvent(event);
 *     } else {
 *         logger.error("Failed to poll event", e);
 *     }
 * });
 * 
 * // Stop source (closes Kafka consumer)
 * source.stop();
 *
 * // 2. Continuous event polling loop
 * public class KafkaEventPoller {
 *     private final KafkaEventSource source;
 *     private volatile boolean running;
 *     
 *     public Promise<Void> startPolling() {
 *         return source.start().then(() -> {
 *             running = true;
 *             return pollLoop();
 *         });
 *     }
 *     
 *     private Promise<Void> pollLoop() {
 *         if (!running) {
 *             return Promise.complete();
 *         }
 *         
 *         return source.next()
 *             .then(event -> processEvent(event))
 *             .then(() -> pollLoop())
 *             .whenException(e -> {
 *                 if (e instanceof IllegalStateException) {
 *                     // No events available, retry after delay
 *                     return Promises.delay(Duration.ofMillis(100))
 *                         .then(() -> pollLoop());
 *                 }
 *                 logger.error("Polling error", e);
 *                 running = false;
 *             });
 *     }
 *     
 *     public Promise<Void> stopPolling() {
 *         running = false;
 *         return source.stop();
 *     }
 * }
 *
 * // 3. Multi-topic consumption
 * KafkaConsumerAdapter multiTopicAdapter = new KafkaConsumerAdapter(
 *     "localhost:9092",
 *     "multi-topic-consumer",
 *     List.of("events-topic", "notifications-topic", "metrics-topic")
 * );
 * 
 * KafkaEventSource multiSource = new KafkaEventSource(multiTopicAdapter);
 * multiSource.start();
 * 
 * // Polls from all subscribed topics
 * multiSource.next().whenComplete((event, e) -> {
 *     // Event from any of the 3 topics
 *     logger.info("Event from topic: {}", event.metadata().get("topic"));
 * });
 *
 * // 4. Integration with event pipeline
 * public class EventIngestionPipeline {
 *     private final KafkaEventSource source;
 *     private final EventSink sink;
 *     
 *     public EventIngestionPipeline(
 *             KafkaConsumerAdapter kafkaAdapter,
 *             EventSink sink) {
 *         this.source = new KafkaEventSource(kafkaAdapter);
 *         this.sink = sink;
 *     }
 *     
 *     public Promise<Void> start() {
 *         return Promise.all(source.start(), sink.start())
 *             .then(() -> ingestLoop());
 *     }
 *     
 *     private Promise<Void> ingestLoop() {
 *         return source.next()
 *             .then(ingestEvent -> convertToEventRecord(ingestEvent))
 *             .then(eventRecord -> sink.send(eventRecord))
 *             .then(() -> ingestLoop())
 *             .whenException(e -> logger.error("Ingestion error", e));
 *     }
 * }
 *
 * // 5. Multi-tenant Kafka consumption
 * public class TenantKafkaSourceFactory {
 *     private final String bootstrapServers;
 *     
 *     public KafkaEventSource createTenantSource(String tenantId) {
 *         String consumerGroup = "tenant-" + tenantId + "-consumer";
 *         String topic = "tenant-" + tenantId + "-events";
 *         
 *         KafkaConsumerAdapter adapter = new KafkaConsumerAdapter(
 *             bootstrapServers,
 *             consumerGroup,
 *             List.of(topic)
 *         );
 *         
 *         return new KafkaEventSource(adapter);
 *     }
 * }
 *
 * // 6. Error handling with retry
 * public class ResilientKafkaPoller {
 *     private final KafkaEventSource source;
 *     private final int maxRetries = 3;
 *     
 *     public Promise<IngestEvent> pollWithRetry() {
 *         return pollWithRetry(0);
 *     }
 *     
 *     private Promise<IngestEvent> pollWithRetry(int attempt) {
 *         return source.next()
 *             .whenException(e -> {
 *                 if (attempt < maxRetries) {
 *                     logger.warn("Poll failed (attempt {}/{}), retrying...", 
 *                         attempt + 1, maxRetries);
 *                     return Promises.delay(Duration.ofSeconds(1))
 *                         .then(() -> pollWithRetry(attempt + 1));
 *                 } else {
 *                     logger.error("Poll failed after {} attempts", maxRetries);
 *                     throw e;
 *                 }
 *             });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Kafka Consumer Delegation</b><br>
 * KafkaConsumerAdapter handles:
 * - Kafka client creation and configuration
 * - Topic subscription
 * - Record polling and deserialization
 * - Offset management
 * - Consumer group coordination
 * - Connection management
 *
 * <p><b>Polling Behavior</b><br>
 * - Returns Promise<IngestEvent> for single event
 * - If no events available, returns failed Promise with IllegalStateException
 * - Caller responsible for retry logic and polling loop
 * - No automatic backpressure (caller controls polling rate)
 *
 * <p><b>Lifecycle States</b><br>
 * - <b>Created</b>: Source created, not started (rejects polling)
 * - <b>Started</b>: Source started, accepts polling
 * - <b>Stopped</b>: Source stopped, consumer closed (rejects polling)
 *
 * <p><b>Error Handling</b><br>
 * - Throws IllegalStateException if next() called before start()
 * - Throws IllegalStateException if no events available
 * - Propagates Kafka consumer exceptions from adapter
 * - Caller must implement retry and error recovery logic
 *
 * <p><b>Consumer Group Management</b><br>
 * - Consumer group ID specified in KafkaConsumerAdapter
 * - Multiple consumers in same group load-balance partitions
 * - Single consumer in group processes all partitions
 * - Kafka handles partition rebalancing
 *
 * <p><b>Offset Management</b><br>
 * Controlled by KafkaConsumerAdapter configuration:
 * - Auto-commit (default): Offsets committed periodically
 * - Manual commit: Application controls offset commits
 * - See KafkaConsumerAdapter for details
 *
 * <p><b>Best Practices</b><br>
 * - Implement polling loop with error handling and retry
 * - Use separate consumer group per logical consumer
 * - Monitor lag metrics for consumer health
 * - Handle rebalancing gracefully (stateless processing)
 * - Use manual offset commit for at-least-once semantics
 * - Close source properly to commit final offsets
 *
 * <p><b>Performance Considerations</b><br>
 * - Polling blocks until events available or timeout
 * - Batch size controlled in KafkaConsumerAdapter
 * - Network latency affects polling throughput
 * - Partition count limits parallel consumption
 * - Consumer group rebalancing causes temporary pauses
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - volatile started field ensures visibility.
 * KafkaConsumerAdapter must be thread-safe or single-threaded.
 * Kafka consumers are NOT thread-safe - do not share across threads.
 *
 * @see EventSource
 * @see KafkaConsumerAdapter
 * @see IngestEvent
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Kafka event source adapter for consuming events from Kafka topics
 * @doc.layer core
 * @doc.pattern Adapter
 */
public final class KafkaEventSource implements EventSource {

    private final KafkaConsumerAdapter consumer;
    private volatile boolean started = false;

    public KafkaEventSource(KafkaConsumerAdapter consumer) {
        this.consumer = Objects.requireNonNull(consumer);
    }

    @Override
    public Promise<Void> start() {
        started = true;
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started = false;
        consumer.close();
        return Promise.complete();
    }

    @Override
    public Promise<IngestEvent> next() {
        if (!started) {
            return Promise.ofException(new IllegalStateException("source not started"));
        }
        return consumer.poll().then(list -> {
            if (list == null || list.isEmpty()) {
                return Promise.ofException(new IllegalStateException("no events available"));
            }
            return Promise.of(list.get(0));
        });
    }
}

