package com.ghatana.core.connectors.impl.sink;

import com.ghatana.core.connectors.EventSink;
import com.ghatana.core.event.cloud.EventRecord;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Production-grade Kafka event sink adapter for publishing events to Kafka topics.
 *
 * <p><b>Purpose</b><br>
 * Provides EventSink abstraction over Kafka producer, enabling event publishing
 * to Kafka topics without exposing Kafka client dependencies to core connectors.
 * Delegates to KafkaProducerAdapter for Kafka-specific operations.
 *
 * <p><b>Architecture Role</b><br>
 * Event sink adapter in core/connectors/impl/sink for Kafka integration.
 * Used by:
 * - Event Publishing Pipelines - Publish events to Kafka
 * - Multi-Tenant Systems - Topic-per-tenant publishing
 * - Event Streaming - Kafka as event stream output
 * - Integration Patterns - External system event publishing
 * - Event Replay - Republish events to Kafka topics
 *
 * <p><b>Sink Features</b><br>
 * - <b>Kafka Producer Abstraction</b>: Hides Kafka client details
 * - <b>Lifecycle Management</b>: start()/stop() control
 * - <b>Promise-Based API</b>: ActiveJ Promise for async integration
 * - <b>State Validation</b>: Rejects sends when not started
 * - <b>Auto-Cleanup</b>: Closes producer on stop()
 * - <b>Delegation Pattern</b>: KafkaProducerAdapter handles Kafka specifics
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic Kafka event publishing
 * KafkaProducerAdapter kafkaAdapter = new KafkaProducerAdapter(
 *     "localhost:9092",
 *     "events-topic"
 * );
 * 
 * KafkaEventSink sink = new KafkaEventSink(kafkaAdapter);
 * 
 * // Start sink
 * sink.start().whenComplete((v, e) -> {
 *     if (e == null) {
 *         logger.info("Kafka sink started");
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
 *         logger.info("Event published to Kafka");
 *     } else {
 *         logger.error("Failed to publish event", e);
 *     }
 * });
 * 
 * // Stop sink (flushes and closes producer)
 * sink.stop();
 *
 * // 2. Integration with event pipeline
 * public class EventPublishingPipeline {
 *     private final EventSink sink;
 *     
 *     public EventPublishingPipeline(KafkaProducerAdapter kafkaAdapter) {
 *         this.sink = new KafkaEventSink(kafkaAdapter);
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
 * // 3. Multi-topic publishing by event type
 * public class TopicRoutingKafkaSink implements EventSink {
 *     private final Map<String, KafkaEventSink> topicSinks;
 *     
 *     public TopicRoutingKafkaSink(
 *             String bootstrapServers,
 *             Map<String, String> eventTypeToTopic) {
 *         this.topicSinks = eventTypeToTopic.entrySet().stream()
 *             .collect(Collectors.toMap(
 *                 Map.Entry::getKey,
 *                 e -> new KafkaEventSink(
 *                     new KafkaProducerAdapter(bootstrapServers, e.getValue())
 *                 )
 *             ));
 *     }
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord event) {
 *         String eventType = event.typeRef().name();
 *         KafkaEventSink sink = topicSinks.get(eventType);
 *         
 *         if (sink == null) {
 *             return Promise.ofException(
 *                 new IllegalArgumentException("No topic for event type: " + eventType)
 *             );
 *         }
 *         
 *         return sink.send(event);
 *     }
 * }
 *
 * // 4. Batch publishing with buffering
 * public class BufferedKafkaSink implements EventSink {
 *     private final KafkaEventSink delegate;
 *     private final Queue<EventRecord> buffer = new ConcurrentLinkedQueue<>();
 *     private final int batchSize;
 *     
 *     public BufferedKafkaSink(KafkaProducerAdapter adapter, int batchSize) {
 *         this.delegate = new KafkaEventSink(adapter);
 *         this.batchSize = batchSize;
 *     }
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord event) {
 *         buffer.add(event);
 *         
 *         if (buffer.size() >= batchSize) {
 *             return flush();
 *         }
 *         
 *         return Promise.complete();
 *     }
 *     
 *     public Promise<Void> flush() {
 *         List<Promise<Void>> sends = new ArrayList<>();
 *         EventRecord event;
 *         while ((event = buffer.poll()) != null) {
 *             sends.add(delegate.send(event));
 *         }
 *         return Promise.all(sends);
 *     }
 * }
 *
 * // 5. Multi-tenant Kafka publishing
 * public class TenantKafkaSinkFactory {
 *     private final String bootstrapServers;
 *     
 *     public KafkaEventSink createTenantSink(String tenantId) {
 *         String topic = "tenant-" + tenantId + "-events";
 *         
 *         KafkaProducerAdapter adapter = new KafkaProducerAdapter(
 *             bootstrapServers,
 *             topic
 *         );
 *         
 *         return new KafkaEventSink(adapter);
 *     }
 * }
 *
 * // 6. Resilient publishing with retry
 * public class ResilientKafkaSink implements EventSink {
 *     private final KafkaEventSink delegate;
 *     private final int maxRetries = 3;
 *     
 *     public ResilientKafkaSink(KafkaProducerAdapter adapter) {
 *         this.delegate = new KafkaEventSink(adapter);
 *     }
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord event) {
 *         return sendWithRetry(event, 0);
 *     }
 *     
 *     private Promise<Void> sendWithRetry(EventRecord event, int attempt) {
 *         return delegate.send(event).whenException(e -> {
 *             if (attempt < maxRetries) {
 *                 logger.warn("Send failed (attempt {}/{}), retrying...", 
 *                     attempt + 1, maxRetries);
 *                 return Promises.delay(Duration.ofSeconds(1))
 *                     .then(() -> sendWithRetry(event, attempt + 1));
 *             } else {
 *                 logger.error("Send failed after {} attempts", maxRetries);
 *                 throw e;
 *             }
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Kafka Producer Delegation</b><br>
 * KafkaProducerAdapter handles:
 * - Kafka client creation and configuration
 * - Topic selection and partitioning
 * - Record serialization
 * - Acknowledgment handling
 * - Batching and compression
 * - Connection management
 *
 * <p><b>Publishing Behavior</b><br>
 * - Returns Promise<Void> for async send confirmation
 * - Promise completes when Kafka acknowledges receipt
 * - Acknowledgment level controlled in KafkaProducerAdapter
 * - No automatic retry (caller controls retry logic)
 *
 * <p><b>Lifecycle States</b><br>
 * - <b>Created</b>: Sink created, not started (rejects sends)
 * - <b>Started</b>: Sink started, accepts sends
 * - <b>Stopped</b>: Sink stopped, producer closed (rejects sends)
 *
 * <p><b>Error Handling</b><br>
 * - Throws IllegalStateException if send() called before start()
 * - Propagates Kafka producer exceptions from adapter
 * - Caller must implement retry and error recovery logic
 * - Stop ensures pending sends are flushed before closing
 *
 * <p><b>Acknowledgment Levels</b><br>
 * Controlled by KafkaProducerAdapter configuration:
 * - acks=0: Fire-and-forget (no acknowledgment)
 * - acks=1: Leader acknowledges (default)
 * - acks=all: All replicas acknowledge (most durable)
 *
 * <p><b>Partitioning Strategy</b><br>
 * Controlled by KafkaProducerAdapter:
 * - Key-based: Events with same key go to same partition
 * - Round-robin: Events distributed evenly across partitions
 * - Custom: Application-defined partitioner
 *
 * <p><b>Best Practices</b><br>
 * - Use acks=all for critical events (durability)
 * - Use acks=1 for high-throughput scenarios (performance)
 * - Implement retry logic for transient failures
 * - Monitor producer metrics (lag, errors, throughput)
 * - Use idempotent producer to avoid duplicates
 * - Close sink properly to flush pending sends
 * - Consider batching for high-volume publishing
 *
 * <p><b>Performance Considerations</b><br>
 * - Batching reduces network overhead
 * - Compression reduces bandwidth usage
 * - Asynchronous sends improve throughput
 * - Partition count affects parallelism
 * - Network latency affects send latency
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - volatile started field ensures visibility.
 * KafkaProducerAdapter must be thread-safe (Kafka producers are thread-safe).
 *
 * @see EventSink
 * @see KafkaProducerAdapter
 * @see EventRecord
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Kafka event sink adapter for publishing events to Kafka topics
 * @doc.layer core
 * @doc.pattern Adapter
 */
public final class KafkaEventSink implements EventSink {

    private final KafkaProducerAdapter producer;
    private volatile boolean started = false;

    public KafkaEventSink(KafkaProducerAdapter producer) {
        this.producer = Objects.requireNonNull(producer);
    }

    @Override
    public Promise<Void> start() {
        started = true;
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started = false;
        producer.close();
        return Promise.complete();
    }

    @Override
    public Promise<Void> send(EventRecord record) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("sink not started"));
        }
        return producer.send(record);
    }
}
