package com.ghatana.core.connectors.impl.sink;

import com.ghatana.core.event.cloud.EventRecord;
import io.activej.promise.Promise;

/**
 * Production-grade adapter interface abstracting Kafka producer implementations from core connectors.
 *
 * <p><b>Purpose</b><br>
 * Decouples core/connectors module from Kafka client dependencies, allowing
 * runtime-provided Kafka implementations while keeping core module lightweight.
 * Enables testing with mock implementations and supports multiple Kafka client versions.
 *
 * <p><b>Architecture Role</b><br>
 * Adapter port in core/connectors/impl/sink for Kafka client abstraction.
 * Used by:
 * - KafkaEventSink - Publish events via adapter interface
 * - Runtime Modules - Provide actual Kafka client implementations
 * - Testing - Mock Kafka producer without real Kafka dependency
 * - Multi-Version Support - Support different Kafka client versions
 * - Dependency Isolation - Keep core module free of Kafka dependencies
 *
 * <p><b>Adapter Features</b><br>
 * - <b>Kafka Client Abstraction</b>: Hides Kafka-specific APIs
 * - <b>Promise-Based Publishing</b>: ActiveJ Promise for async integration
 * - <b>Event Serialization</b>: Convert EventRecord to Kafka format
 * - <b>Resource Management</b>: close() for cleanup
 * - <b>Implementation Agnostic</b>: Works with any Kafka client version
 * - <b>Testability</b>: Easy to mock for testing
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Production implementation with Kafka client
 * public class ProductionKafkaProducer implements KafkaProducerAdapter {
 *     private final KafkaProducer<String, byte[]> producer;
 *     private final String topic;
 *     private final EventSerializer serializer;
 *     
 *     public ProductionKafkaProducer(
 *             String bootstrapServers,
 *             String topic) {
 *         Properties props = new Properties();
 *         props.put("bootstrap.servers", bootstrapServers);
 *         props.put("key.serializer", StringSerializer.class.getName());
 *         props.put("value.serializer", ByteArraySerializer.class.getName());
 *         props.put("acks", "all");
 *         props.put("retries", 3);
 *         props.put("linger.ms", 10);
 *         props.put("batch.size", 16384);
 *         props.put("compression.type", "snappy");
 *         props.put("enable.idempotence", "true");
 *         
 *         this.producer = new KafkaProducer<>(props);
 *         this.topic = topic;
 *         this.serializer = new EventSerializer();
 *     }
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord record) {
 *         return Promise.ofFuture(executor, () -> {
 *             String key = record.id().value();
 *             byte[] value = serializer.serialize(record);
 *             
 *             ProducerRecord<String, byte[]> kafkaRecord = 
 *                 new ProducerRecord<>(topic, key, value);
 *             
 *             RecordMetadata metadata = producer.send(kafkaRecord).get();
 *             
 *             logger.debug("Event sent to partition {} offset {}",
 *                 metadata.partition(), metadata.offset());
 *             
 *             return null;
 *         });
 *     }
 *     
 *     @Override
 *     public void close() {
 *         producer.flush();
 *         producer.close();
 *     }
 * }
 *
 * // 2. Test implementation with mock
 * public class MockKafkaProducer implements KafkaProducerAdapter {
 *     private final List<EventRecord> sentEvents = new ArrayList<>();
 *     private volatile boolean closed = false;
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord record) {
 *         if (closed) {
 *             return Promise.ofException(
 *                 new IllegalStateException("Producer closed")
 *             );
 *         }
 *         sentEvents.add(record);
 *         return Promise.complete();
 *     }
 *     
 *     @Override
 *     public void close() {
 *         closed = true;
 *     }
 *     
 *     public List<EventRecord> getSentEvents() {
 *         return new ArrayList<>(sentEvents);
 *     }
 * }
 *
 * // 3. In-memory adapter for testing
 * class InMemoryKafkaProducerTest {
 *     @Test
 *     void shouldSendEvent() {
 *         MockKafkaProducer adapter = new MockKafkaProducer();
 *         
 *         EventRecord event = createTestEvent("user.created");
 *         Promise<Void> result = adapter.send(event);
 *         
 *         assertThat(result).succeedsWithin(Duration.ofSeconds(1));
 *         assertThat(adapter.getSentEvents()).hasSize(1);
 *         assertThat(adapter.getSentEvents().get(0)).isEqualTo(event);
 *     }
 * }
 *
 * // 4. Partitioning adapter with custom logic
 * public class PartitioningKafkaProducer implements KafkaProducerAdapter {
 *     private final KafkaProducerAdapter delegate;
 *     private final Function<EventRecord, Integer> partitioner;
 *     
 *     public PartitioningKafkaProducer(
 *             KafkaProducerAdapter delegate,
 *             Function<EventRecord, Integer> partitioner) {
 *         this.delegate = delegate;
 *         this.partitioner = partitioner;
 *     }
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord record) {
 *         // Apply custom partitioning logic
 *         Integer partition = partitioner.apply(record);
 *         
 *         // Add partition metadata
 *         EventRecord partitionedRecord = record.withMetadata(
 *             "partition", String.valueOf(partition)
 *         );
 *         
 *         return delegate.send(partitionedRecord);
 *     }
 *     
 *     @Override
 *     public void close() {
 *         delegate.close();
 *     }
 * }
 *
 * // 5. Metrics-collecting adapter
 * public class MetricsKafkaProducer implements KafkaProducerAdapter {
 *     private final KafkaProducerAdapter delegate;
 *     private final MetricsCollector metrics;
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord record) {
 *         long start = System.nanoTime();
 *         
 *         return delegate.send(record).whenComplete((v, e) -> {
 *             long durationMs = (System.nanoTime() - start) / 1_000_000;
 *             
 *             if (e == null) {
 *                 metrics.recordTimer("kafka.send.duration", durationMs);
 *                 metrics.incrementCounter("kafka.events.sent",
 *                     "type", record.typeRef().name());
 *             } else {
 *                 metrics.incrementCounter("kafka.send.errors",
 *                     "error", e.getClass().getSimpleName(),
 *                     "type", record.typeRef().name());
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
 * // 6. Compression adapter
 * public class CompressingKafkaProducer implements KafkaProducerAdapter {
 *     private final KafkaProducerAdapter delegate;
 *     private final Compressor compressor;
 *     
 *     @Override
 *     public Promise<Void> send(EventRecord record) {
 *         // Compress event payload before sending
 *         byte[] payload = serialize(record);
 *         byte[] compressed = compressor.compress(payload);
 *         
 *         EventRecord compressedRecord = record.withMetadata(
 *             "compression", compressor.algorithm(),
 *             "original_size", String.valueOf(payload.length),
 *             "compressed_size", String.valueOf(compressed.length)
 *         );
 *         
 *         return delegate.send(compressedRecord);
 *     }
 *     
 *     @Override
 *     public void close() {
 *         delegate.close();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Send Contract</b><br>
 * - Returns Promise<Void> that completes when send acknowledged
 * - Acknowledgment level determined by implementation (acks=0/1/all)
 * - Promise success: Event successfully sent and acknowledged
 * - Promise failure: Send failed (network, serialization, etc.)
 * - Blocking: Implementation may block during send operation
 *
 * <p><b>Event Serialization</b><br>
 * Implementation responsibilities:
 * - Serialize EventRecord to Kafka format (key + value)
 * - Extract partition key from event ID or metadata
 * - Handle serialization errors gracefully
 * - Include metadata in Kafka headers (optional)
 *
 * <p><b>Acknowledgment Levels</b><br>
 * Implementations should support:
 * - <b>acks=0</b>: Fire-and-forget (no wait for acknowledgment)
 * - <b>acks=1</b>: Leader acknowledgment (default, balanced)
 * - <b>acks=all</b>: All replicas acknowledgment (most durable)
 *
 * <p><b>Error Handling</b><br>
 * Implementations should:
 * - Return failed Promise for transient errors (network, timeout)
 * - Throw exception for fatal errors (serialization, partition unavailable)
 * - Retry internally for transient errors (configurable retries)
 * - Log errors with sufficient context for debugging
 *
 * <p><b>Resource Cleanup</b><br>
 * close() must:
 * - Flush pending sends (wait for acknowledgments)
 * - Close Kafka producer connection
 * - Release network resources
 * - Clean up internal buffers
 * - Be idempotent (safe to call multiple times)
 *
 * <p><b>Best Practices</b><br>
 * - Use Promise.ofFuture() for Kafka send Future
 * - Configure acks=all for critical events (durability)
 * - Enable idempotence to avoid duplicates
 * - Use compression (snappy/lz4) for large payloads
 * - Configure batching for throughput (linger.ms, batch.size)
 * - Include event metadata for traceability
 * - Implement metrics collection for observability
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe.
 * Kafka producers are thread-safe - can be shared across threads.
 *
 * @see KafkaEventSink
 * @see EventRecord
 * @since 1.0.0
 * @doc.type interface
 * @doc.purpose Adapter interface abstracting Kafka producer implementations
 * @doc.layer core
 * @doc.pattern Port
 */
public interface KafkaProducerAdapter {
    Promise<Void> send(EventRecord record);
    void close();
}
