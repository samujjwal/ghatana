package com.ghatana.aep.connector.strategy.kafka;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kafka consumer strategy using ActiveJ Promise integration.
 * 
 * <p>This implementation:
 * <ul>
 *   <li>Uses manual offset commit for at-least-once delivery</li>
 *   <li>Supports batched message polling</li>
 *   <li>Integrates with ActiveJ Eventloop for async operations</li>
 *   <li>Handles consumer group coordination</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * KafkaConsumerStrategy consumer = new KafkaConsumerStrategy(
 *     KafkaConsumerConfig.builder()
 *         .bootstrapServers("localhost:9092")
 *         .groupId("aep-consumer-group")
 *         .topics(List.of("events"))
 *         .batchSize(100)
 *         .build(),
 *     eventloop
 * );
 * 
 * consumer.start()
 *     .then(() -> consumer.poll())
 *     .whenResult(messages -> processMessages(messages));
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Kafka consumer implementation
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class KafkaConsumerStrategy implements QueueConsumerStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerStrategy.class);
    
    private final KafkaConsumerConfig config;
    private final Eventloop eventloop;
    private final ExecutorService executor;
    private final AtomicReference<ConsumerStatus> status;
    private final Set<String> pendingAcks;
    private final Map<String, TopicPartitionOffset> messageOffsets;
    
    private volatile KafkaConsumer<String, String> consumer;
    
    /**
     * Creates a new KafkaConsumerStrategy.
     * 
     * @param config Consumer configuration
     * @param eventloop ActiveJ eventloop for async operations
     */
    public KafkaConsumerStrategy(KafkaConsumerConfig config, Eventloop eventloop) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-consumer-" + config.getGroupId());
            t.setDaemon(true);
            return t;
        });
        this.status = new AtomicReference<>(ConsumerStatus.NOT_STARTED);
        this.pendingAcks = ConcurrentHashMap.newKeySet();
        this.messageOffsets = new ConcurrentHashMap<>();
    }
    
    @Override
    public Promise<Void> start() {
        if (!status.compareAndSet(ConsumerStatus.NOT_STARTED, ConsumerStatus.STARTING)) {
            return Promise.ofException(
                new IllegalStateException("Consumer already started or starting")
            );
        }
        
        return Promise.ofBlocking(executor, () -> {
            logger.info("Starting Kafka consumer. group={}, topics={}", 
                config.getGroupId(), config.getTopics());
            
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getGroupId());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // Manual commit
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(config.getBatchSize()));
            props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, String.valueOf(config.getFetchMinBytes()));
            props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, String.valueOf(config.getFetchMaxWaitMs()));
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.getAutoOffsetReset());
            props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(config.getSessionTimeoutMs()));
            
            // Add custom properties
            if (config.getCustomProperties() != null) {
                props.putAll(config.getCustomProperties());
            }
            
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(config.getTopics());
            
            status.set(ConsumerStatus.RUNNING);
            logger.info("Kafka consumer started successfully");
            
            return null;
        });
    }
    
    @Override
    public Promise<List<QueueMessage>> poll() {
        if (status.get() != ConsumerStatus.RUNNING) {
            return Promise.ofException(
                new IllegalStateException("Consumer not running. Status: " + status.get())
            );
        }
        
        return Promise.ofBlocking(executor, () -> {
            ConsumerRecords<String, String> records = consumer.poll(
                Duration.ofMillis(config.getPollTimeoutMs())
            );
            
            List<QueueMessage> messages = new ArrayList<>();
            
            for (ConsumerRecord<String, String> record : records) {
                String messageId = formatMessageId(record);
                
                // Store offset for later commit
                messageOffsets.put(messageId, new TopicPartitionOffset(
                    record.topic(),
                    record.partition(),
                    record.offset()
                ));
                
                pendingAcks.add(messageId);
                
                Map<String, String> metadata = new HashMap<>();
                metadata.put("topic", record.topic());
                metadata.put("partition", String.valueOf(record.partition()));
                metadata.put("offset", String.valueOf(record.offset()));
                metadata.put("timestamp", String.valueOf(record.timestamp()));
                metadata.put("timestampType", record.timestampType().name());
                if (record.key() != null) {
                    metadata.put("key", record.key());
                }
                
                // Add headers as metadata
                record.headers().forEach(header -> 
                    metadata.put("header." + header.key(), new String(header.value()))
                );
                
                messages.add(new QueueMessage(messageId, record.value(), metadata));
            }
            
            if (!messages.isEmpty()) {
                logger.debug("Polled {} messages from Kafka", messages.size());
            }
            
            return messages;
        });
    }
    
    @Override
    public Promise<Void> acknowledge(String messageId) {
        return Promise.ofBlocking(executor, () -> {
            pendingAcks.remove(messageId);
            
            // Commit offset when all messages in batch are acknowledged
            if (pendingAcks.isEmpty() && !messageOffsets.isEmpty()) {
                commitOffsets();
            }
            
            return null;
        });
    }
    
    @Override
    public Promise<Void> nack(String messageId) {
        return Promise.ofBlocking(executor, () -> {
            pendingAcks.remove(messageId);
            messageOffsets.remove(messageId);
            
            // Kafka doesn't have explicit NACK - message will be redelivered
            // when consumer restarts or rebalances without committing offset
            logger.warn("Message nacked, will be redelivered: {}", messageId);
            
            return null;
        });
    }
    
    @Override
    public Promise<Void> stop() {
        ConsumerStatus currentStatus = status.get();
        if (currentStatus == ConsumerStatus.STOPPED || currentStatus == ConsumerStatus.STOPPING) {
            return Promise.complete();
        }
        
        status.set(ConsumerStatus.STOPPING);
        
        return Promise.ofBlocking(executor, () -> {
            logger.info("Stopping Kafka consumer");
            
            try {
                // Commit any remaining offsets
                if (!messageOffsets.isEmpty()) {
                    commitOffsets();
                }
                
                if (consumer != null) {
                    consumer.close(Duration.ofSeconds(10));
                }
                
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } finally {
                status.set(ConsumerStatus.STOPPED);
                logger.info("Kafka consumer stopped");
            }
            
            return null;
        });
    }
    
    @Override
    public ConsumerStatus getStatus() {
        return status.get();
    }
    
    /**
     * Commits offsets synchronously.
     */
    private void commitOffsets() {
        Map<org.apache.kafka.common.TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        
        for (TopicPartitionOffset tpo : messageOffsets.values()) {
            org.apache.kafka.common.TopicPartition tp = 
                new org.apache.kafka.common.TopicPartition(tpo.topic, tpo.partition);
            
            // Commit offset + 1 (next message to read)
            OffsetAndMetadata offsetMeta = offsets.get(tp);
            if (offsetMeta == null || tpo.offset + 1 > offsetMeta.offset()) {
                offsets.put(tp, new OffsetAndMetadata(tpo.offset + 1));
            }
        }
        
        if (!offsets.isEmpty()) {
            consumer.commitSync(offsets);
            logger.debug("Committed {} partition offsets", offsets.size());
            messageOffsets.clear();
        }
    }
    
    /**
     * Formats a unique message ID from a Kafka record.
     */
    private String formatMessageId(ConsumerRecord<String, String> record) {
        return String.format("%s-%d-%d", record.topic(), record.partition(), record.offset());
    }
    
    /**
     * Internal class to track topic-partition-offset tuples.
     */
    private record TopicPartitionOffset(String topic, int partition, long offset) {}
}
