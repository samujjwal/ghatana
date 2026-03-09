package com.ghatana.core.connectors.impl.source;

import com.ghatana.core.connectors.IngestEvent;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Production-grade adapter interface abstracting Kafka consumer implementations from core connectors.
 *
 * <p><b>Purpose</b><br>
 * Decouples core/connectors module from Kafka client dependencies, allowing
 * runtime-provided Kafka implementations while keeping core module lightweight.
 * Enables testing with mock implementations and supports multiple Kafka client versions.
 *
 * <p><b>Architecture Role</b><br>
 * Adapter port in core/connectors/impl/source for Kafka client abstraction.
 * Used by:
 * - KafkaEventSource - Consume events via adapter interface
 * - Runtime Modules - Provide actual Kafka client implementations
 * - Testing - Mock Kafka consumer without real Kafka dependency
 * - Multi-Version Support - Support different Kafka client versions
 * - Dependency Isolation - Keep core module free of Kafka dependencies
 *
 * <p><b>Adapter Features</b><br>
 * - <b>Kafka Client Abstraction</b>: Hides Kafka-specific APIs
 * - <b>Promise-Based Polling</b>: ActiveJ Promise for async integration
 * - <b>Batch Processing</b>: Returns list of events per poll
 * - <b>Resource Management</b>: close() for cleanup
 * - <b>Implementation Agnostic</b>: Works with any Kafka client version
 * - <b>Testability</b>: Easy to mock for testing
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Production implementation with Kafka client
 * public class ProductionKafkaAdapter implements KafkaConsumerAdapter {
 *     private final KafkaConsumer<String, byte[]> consumer;
 *     private final EventDeserializer deserializer;
 *     
 *     public ProductionKafkaAdapter(
 *             String bootstrapServers,
 *             String groupId,
 *             List<String> topics) {
 *         Properties props = new Properties();
 *         props.put("bootstrap.servers", bootstrapServers);
 *         props.put("group.id", groupId);
 *         props.put("key.deserializer", StringDeserializer.class.getName());
 *         props.put("value.deserializer", ByteArrayDeserializer.class.getName());
 *         props.put("enable.auto.commit", "true");
 *         props.put("auto.offset.reset", "earliest");
 *         
 *         this.consumer = new KafkaConsumer<>(props);
 *         this.consumer.subscribe(topics);
 *         this.deserializer = new EventDeserializer();
 *     }
 *     
 *     @Override
 *     public Promise<List<IngestEvent>> poll() {
 *         return Promise.ofBlocking(() -> {
 *             ConsumerRecords<String, byte[]> records = 
 *                 consumer.poll(Duration.ofMillis(100));
 *             
 *             List<IngestEvent> events = new ArrayList<>();
 *             for (ConsumerRecord<String, byte[]> record : records) {
 *                 IngestEvent event = deserializer.deserialize(
 *                     record.key(),
 *                     record.value(),
 *                     Map.of(
 *                         "topic", record.topic(),
 *                         "partition", String.valueOf(record.partition()),
 *                         "offset", String.valueOf(record.offset())
 *                     )
 *                 );
 *                 events.add(event);
 *             }
 *             
 *             return events;
 *         });
 *     }
 *     
 *     @Override
 *     public void close() {
 *         consumer.close();
 *     }
 * }
 *
 * // 2. Test implementation with mock data
 * public class MockKafkaAdapter implements KafkaConsumerAdapter {
 *     private final Queue<IngestEvent> events = new ConcurrentLinkedQueue<>();
 *     private volatile boolean closed = false;
 *     
 *     public void addEvent(IngestEvent event) {
 *         events.add(event);
 *     }
 *     
 *     @Override
 *     public Promise<List<IngestEvent>> poll() {
 *         if (closed) {
 *             return Promise.ofException(new IllegalStateException("Consumer closed"));
 *         }
 *         
 *         List<IngestEvent> batch = new ArrayList<>();
 *         IngestEvent event;
 *         while ((event = events.poll()) != null && batch.size() < 10) {
 *             batch.add(event);
 *         }
 *         
 *         return Promise.of(batch);
 *     }
 *     
 *     @Override
 *     public void close() {
 *         closed = true;
 *         events.clear();
 *     }
 * }
 *
 * // 3. In-memory adapter for testing
 * class InMemoryKafkaAdapterTest {
 *     @Test
 *     void shouldPollEvents() {
 *         MockKafkaAdapter adapter = new MockKafkaAdapter();
 *         
 *         // Add test events
 *         adapter.addEvent(createTestEvent("event1"));
 *         adapter.addEvent(createTestEvent("event2"));
 *         
 *         // Poll events
 *         Promise<List<IngestEvent>> result = adapter.poll();
 *         List<IngestEvent> events = result.getResult();
 *         
 *         assertThat(events).hasSize(2);
 *         assertThat(events.get(0).eventId()).isEqualTo("event1");
 *     }
 * }
 *
 * // 4. Batching adapter wrapper
 * public class BatchingKafkaAdapter implements KafkaConsumerAdapter {
 *     private final KafkaConsumerAdapter delegate;
 *     private final int batchSize;
 *     private final Queue<IngestEvent> buffer = new ArrayDeque<>();
 *     
 *     public BatchingKafkaAdapter(KafkaConsumerAdapter delegate, int batchSize) {
 *         this.delegate = delegate;
 *         this.batchSize = batchSize;
 *     }
 *     
 *     @Override
 *     public Promise<List<IngestEvent>> poll() {
 *         return delegate.poll().then(events -> {
 *             buffer.addAll(events);
 *             
 *             List<IngestEvent> batch = new ArrayList<>();
 *             while (!buffer.isEmpty() && batch.size() < batchSize) {
 *                 batch.add(buffer.poll());
 *             }
 *             
 *             return Promise.of(batch);
 *         });
 *     }
 *     
 *     @Override
 *     public void close() {
 *         delegate.close();
 *         buffer.clear();
 *     }
 * }
 *
 * // 5. Metrics-collecting adapter
 * public class MetricsKafkaAdapter implements KafkaConsumerAdapter {
 *     private final KafkaConsumerAdapter delegate;
 *     private final MetricsCollector metrics;
 *     
 *     @Override
 *     public Promise<List<IngestEvent>> poll() {
 *         long start = System.nanoTime();
 *         
 *         return delegate.poll().whenComplete((events, e) -> {
 *             long durationMs = (System.nanoTime() - start) / 1_000_000;
 *             
 *             if (e == null) {
 *                 metrics.recordTimer("kafka.poll.duration", durationMs);
 *                 metrics.incrementCounter("kafka.events.polled", 
 *                     "count", String.valueOf(events.size()));
 *             } else {
 *                 metrics.incrementCounter("kafka.poll.errors", 
 *                     "error", e.getClass().getSimpleName());
 *             }
 *         });
 *     }
 *     
 *     @Override
 *     public void close() {
 *         delegate.close();
 *     }
 * }
 *
 * // 6. Multi-topic adapter with routing
 * public class MultiTopicKafkaAdapter implements KafkaConsumerAdapter {
 *     private final Map<String, KafkaConsumerAdapter> topicAdapters;
 *     
 *     public MultiTopicKafkaAdapter(Map<String, KafkaConsumerAdapter> topicAdapters) {
 *         this.topicAdapters = topicAdapters;
 *     }
 *     
 *     @Override
 *     public Promise<List<IngestEvent>> poll() {
 *         // Poll from all topics, merge results
 *         List<Promise<List<IngestEvent>>> polls = topicAdapters.values().stream()
 *             .map(KafkaConsumerAdapter::poll)
 *             .collect(Collectors.toList());
 *         
 *         return Promise.all(polls).then(results -> {
 *             List<IngestEvent> merged = results.stream()
 *                 .flatMap(List::stream)
 *                 .collect(Collectors.toList());
 *             return Promise.of(merged);
 *         });
 *     }
 *     
 *     @Override
 *     public void close() {
 *         topicAdapters.values().forEach(KafkaConsumerAdapter::close);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Poll Contract</b><br>
 * - Returns Promise<List<IngestEvent>>
 * - Empty list: No events currently available (not an error)
 * - Null list: Should not be returned (use empty list instead)
 * - Promise failure: Error during polling (network, deserialization, etc.)
 * - Blocking: Implementation may block during poll operation
 *
 * <p><b>Event Metadata</b><br>
 * Implementations should include Kafka metadata in IngestEvent:
 * <pre>
 * Map<String, String> metadata = Map.of(
 *     "topic", record.topic(),
 *     "partition", String.valueOf(record.partition()),
 *     "offset", String.valueOf(record.offset()),
 *     "timestamp", String.valueOf(record.timestamp()),
 *     "key", record.key()
 * );
 * </pre>
 *
 * <p><b>Offset Management</b><br>
 * Implementation responsibilities:
 * - Auto-commit: Kafka automatically commits offsets (default)
 * - Manual commit: Application controls when to commit offsets
 * - Commit on close: Ensure final offsets committed in close()
 * - Commit frequency: Balance between performance and at-least-once semantics
 *
 * <p><b>Error Handling</b><br>
 * Implementations should:
 * - Return failed Promise for transient errors (network, timeout)
 * - Throw exception for fatal errors (authentication, authorization)
 * - Handle deserialization errors gracefully (skip or dead-letter)
 * - Log errors with sufficient context for debugging
 *
 * <p><b>Resource Cleanup</b><br>
 * close() must:
 * - Commit any pending offsets
 * - Close Kafka consumer connection
 * - Release network resources
 * - Clean up internal buffers
 * - Be idempotent (safe to call multiple times)
 *
 * <p><b>Best Practices</b><br>
 * - Use Promise.ofBlocking() for Kafka poll (blocking operation)
 * - Configure poll timeout appropriately (100-500ms recommended)
 * - Handle rebalancing gracefully (Kafka handles automatically)
 * - Include metadata for traceability (topic, partition, offset)
 * - Implement metrics collection for observability
 * - Use manual offset commit for at-least-once semantics
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe OR clearly document single-threaded usage.
 * Kafka consumers are NOT thread-safe - serialize access or use per-thread instances.
 *
 * @see KafkaEventSource
 * @see IngestEvent
 * @since 1.0.0
 * @doc.type interface
 * @doc.purpose Adapter interface abstracting Kafka consumer implementations
 * @doc.layer core
 * @doc.pattern Port
 */
public interface KafkaConsumerAdapter {
    /**
     * Poll for next batch of events. Implementations should return a Promise that
     * completes with a list of IngestEvent; empty list means no events currently available.
     */
    Promise<List<IngestEvent>> poll();

    /**
     * Close the underlying consumer.
     */
    void close();
}

