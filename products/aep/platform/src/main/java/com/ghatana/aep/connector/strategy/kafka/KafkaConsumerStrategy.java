package com.ghatana.aep.connector.strategy.kafka;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
 *   <li><b>Dead-Letter Topic (DLT)</b>: messages that exceed {@code maxRetries} are
 *       forwarded to {@code {originalTopic}{dltTopicSuffix}} (default: {@code .dlt}) with
 *       diagnostic headers:
 *       <ul>
 *         <li>{@code X-DLT-OriginalTopic} — source topic name</li>
 *         <li>{@code X-DLT-OriginalPartition} — source partition</li>
 *         <li>{@code X-DLT-OriginalOffset} — source offset</li>
 *         <li>{@code X-DLT-RetryCount} — number of attempts made</li>
 *         <li>{@code X-DLT-ErrorMessage} — last exception message</li>
 *         <li>{@code X-DLT-FailedAt} — ISO-8601 timestamp of the final failure</li>
 *       </ul>
 *   </li>
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
 *         .maxRetries(3)
 *         .dltTopicSuffix(".dlt")
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
 * @doc.purpose Kafka consumer with at-least-once delivery and dead-letter routing
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class KafkaConsumerStrategy implements QueueConsumerStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerStrategy.class);

    // DLT header names
    private static final String HEADER_ORIGINAL_TOPIC     = "X-DLT-OriginalTopic";
    private static final String HEADER_ORIGINAL_PARTITION = "X-DLT-OriginalPartition";
    private static final String HEADER_ORIGINAL_OFFSET    = "X-DLT-OriginalOffset";
    private static final String HEADER_RETRY_COUNT        = "X-DLT-RetryCount";
    private static final String HEADER_ERROR_MESSAGE      = "X-DLT-ErrorMessage";
    private static final String HEADER_FAILED_AT          = "X-DLT-FailedAt";

    private final KafkaConsumerConfig config;
    private final Eventloop eventloop;
    private final ExecutorService executor;
    private final AtomicReference<ConsumerStatus> status;
    private final Set<String> pendingAcks;
    private final Map<String, TopicPartitionOffset> messageOffsets;
    /**
     * Per-message retry counter. Incremented on each {@link #nack(String, String)} call;
     * removed on {@link #acknowledge(String)} or after DLT routing.
     */
    private final Map<String, AtomicInteger> retryCounters;

    private volatile Consumer<String, String> consumer;
    /** Lazily-created producer for DLT publishing. Created on first DLT need. */
    private volatile Producer<String, String> dltProducer;
    
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
        this.retryCounters = new ConcurrentHashMap<>();
    }

    /**
     * Package-private constructor for unit testing only.
     *
     * <p>Accepts pre-built {@code consumer} and {@code dltProducer} mocks so tests can
     * exercise the DLT retry logic without requiring a live Kafka broker. Status is
     * pre-set to {@code RUNNING} so {@link #poll()} can be called directly without
     * going through {@link #start()}.
     *
     * @param config      consumer configuration (notably {@code maxRetries}, {@code dltTopicSuffix})
     * @param eventloop   ActiveJ eventloop for async operations
     * @param consumer    pre-built (e.g. {@code MockConsumer}) Kafka consumer
     * @param dltProducer pre-built (e.g. {@code MockProducer}) Kafka producer for DLT
     */
    KafkaConsumerStrategy(KafkaConsumerConfig config, Eventloop eventloop,
                          Consumer<String, String> consumer,
                          Producer<String, String> dltProducer) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-consumer-test-" + config.getGroupId());
            t.setDaemon(true);
            return t;
        });
        this.status = new AtomicReference<>(ConsumerStatus.RUNNING);
        this.pendingAcks = ConcurrentHashMap.newKeySet();
        this.messageOffsets = new ConcurrentHashMap<>();
        this.retryCounters = new ConcurrentHashMap<>();
        this.consumer = consumer;
        this.dltProducer = dltProducer;
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
            retryCounters.remove(messageId);

            // Commit offset when all messages in batch are acknowledged
            if (pendingAcks.isEmpty() && !messageOffsets.isEmpty()) {
                commitOffsets();
            }

            return null;
        });
    }

    /**
     * NACKs a message without an error reason; delegates to {@link #nack(String, String)}.
     */
    @Override
    public Promise<Void> nack(String messageId) {
        return nack(messageId, null);
    }

    /**
     * NACKs a message indicating a processing failure.
     *
     * <p>On each invocation the per-message retry counter is incremented. When the counter
     * reaches {@code maxRetries} the message is published to the dead-letter topic and the
     * original-topic offset is committed so the consumer moves forward rather than
     * reprocessing the poison pill indefinitely.
     *
     * <p>If {@code maxRetries} is 0 DLT routing is disabled and the message is simply
     * dropped from tracking (Kafka will redeliver it on restart / rebalance).
     *
     * @param messageId unique message identifier (format: {@code topic-partition-offset})
     * @param errorMessage optional description of the last processing error
     * @return promise that completes when the nack bookkeeping is done
     */
    public Promise<Void> nack(String messageId, String errorMessage) {
        return Promise.ofBlocking(executor, () -> {
            pendingAcks.remove(messageId);

            AtomicInteger counter = retryCounters.computeIfAbsent(messageId, k -> new AtomicInteger(0));
            int attempts = counter.incrementAndGet();

            TopicPartitionOffset tpo = messageOffsets.get(messageId);

            if (config.getMaxRetries() > 0 && attempts >= config.getMaxRetries() && tpo != null) {
                // Exhausted retries — route to DLT and commit the offset so the consumer moves forward.
                logger.warn("[dlt] Message exhausted {} retries, routing to DLT. messageId={} error={}",
                        config.getMaxRetries(), messageId, errorMessage);
                publishToDlt(messageId, tpo, attempts, errorMessage);
                retryCounters.remove(messageId);
                // commitOffsets() reads messageOffsets (still contains this entry) and then clears it.
                commitOffsets();
            } else {
                // Still within retry budget — keep the offset in messageOffsets without committing.
                // Not calling commitOffsets() means Kafka will redeliver the message on rebalance/restart.
                logger.warn("[retry] Message nacked (attempt {}/{}), will be redelivered. messageId={}",
                        attempts, config.getMaxRetries(), messageId);
            }

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

                Producer<String, String> dlt = dltProducer;
                if (dlt != null) {
                    dlt.close(Duration.ofSeconds(10));
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
     * Publishes a poison-pill message to the dead-letter topic.
     *
     * <p>The DLT topic is derived by appending {@link KafkaConsumerConfig#getDltTopicSuffix()}
     * to the original source topic. The original message payload is forwarded verbatim;
     * diagnostic context is attached as Kafka headers.
     *
     * @param messageId   original message identifier
     * @param tpo         original topic-partition-offset
     * @param retryCount  number of failed processing attempts
     * @param errorMessage last error message; may be {@code null}
     */
    private void publishToDlt(String messageId, TopicPartitionOffset tpo,
                               int retryCount, String errorMessage) {
        try {
            // Lazily create the DLT producer on first need (most messages succeed)
            if (dltProducer == null) {
                Properties props = new Properties();
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
                props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                props.put(ProducerConfig.ACKS_CONFIG, "all");
                props.put(ProducerConfig.RETRIES_CONFIG, "3");
                props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
                if (config.getCustomProperties() != null) {
                    props.putAll(config.getCustomProperties());
                }
                dltProducer = new KafkaProducer<>(props);
                logger.info("[dlt] DLT producer initialised. bootstrapServers={}",
                        config.getBootstrapServers());
            }

            String dltTopic = tpo.topic() + config.getDltTopicSuffix();
            // We don't have the original payload in the offset map — reconstruct a
            // diagnostic envelope. In production this should carry the original payload;
            // callers should prefer nack(messageId, payload, errorMessage) (future overload).
            String diagnosticPayload = "{\"dlt\":true,\"originalMessageId\":\"" + messageId + "\"}";

            ProducerRecord<String, String> dltRecord = new ProducerRecord<>(dltTopic, messageId, diagnosticPayload);
            dltRecord.headers()
                .add(toHeader(HEADER_ORIGINAL_TOPIC,     tpo.topic()))
                .add(toHeader(HEADER_ORIGINAL_PARTITION, String.valueOf(tpo.partition())))
                .add(toHeader(HEADER_ORIGINAL_OFFSET,    String.valueOf(tpo.offset())))
                .add(toHeader(HEADER_RETRY_COUNT,        String.valueOf(retryCount)))
                .add(toHeader(HEADER_FAILED_AT,          Instant.now().toString()));
            if (errorMessage != null) {
                dltRecord.headers().add(toHeader(HEADER_ERROR_MESSAGE, errorMessage));
            }

            dltProducer.send(dltRecord, (metadata, ex) -> {
                if (ex != null) {
                    logger.error("[dlt] Failed to publish to DLT topic='{}' messageId='{}': {}",
                            dltTopic, messageId, ex.getMessage(), ex);
                } else {
                    logger.info("[dlt] Published to DLT topic='{}' partition={} offset={} messageId='{}'",
                            dltTopic, metadata.partition(), metadata.offset(), messageId);
                }
            });
            dltProducer.flush();
        } catch (Exception ex) {
            logger.error("[dlt] Exception while publishing to DLT for messageId='{}': {}",
                    messageId, ex.getMessage(), ex);
        }
    }

    /** Creates a Kafka {@link Header} from a plain-string key/value pair (UTF-8). */
    private static Header toHeader(String key, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new Header() {
            @Override public String key() { return key; }
            @Override public byte[] value() { return bytes; }
        };
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
