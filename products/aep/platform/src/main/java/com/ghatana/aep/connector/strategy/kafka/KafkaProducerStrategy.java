package com.ghatana.aep.connector.strategy.kafka;

import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.promise.Promises;

/**
 * Kafka producer strategy using ActiveJ Promise integration.
 * 
 * <p>This implementation:
 * <ul>
 *   <li>Supports batched message sending for efficiency</li>
 *   <li>Configurable acknowledgment levels (acks=all for durability)</li>
 *   <li>Compression support (snappy by default)</li>
 *   <li>Async send with callback-based Promise resolution</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Kafka producer implementation
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class KafkaProducerStrategy implements QueueProducerStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerStrategy.class);
    
    private final KafkaProducerConfig config;
    private final Eventloop eventloop;
    private final ExecutorService executor;
    private final AtomicReference<ProducerStatus> status;
    
    private volatile KafkaProducer<String, String> producer;
    
    /**
     * Creates a new KafkaProducerStrategy.
     * 
     * @param config Producer configuration
     * @param eventloop ActiveJ eventloop for async operations
     */
    public KafkaProducerStrategy(KafkaProducerConfig config, Eventloop eventloop) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-producer-" + config.getTopic());
            t.setDaemon(true);
            return t;
        });
        this.status = new AtomicReference<>(ProducerStatus.NOT_STARTED);
    }
    
    @Override
    public Promise<Void> start() {
        if (!status.compareAndSet(ProducerStatus.NOT_STARTED, ProducerStatus.STARTING)) {
            return Promise.ofException(
                new IllegalStateException("Producer already started or starting")
            );
        }
        
        return Promise.ofBlocking(executor, () -> {
            logger.info("Starting Kafka producer. topic={}", config.getTopic());
            
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, config.getAcks());
            props.put(ProducerConfig.RETRIES_CONFIG, config.getRetries());
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, String.valueOf(config.getBatchSize()));
            props.put(ProducerConfig.LINGER_MS_CONFIG, String.valueOf(config.getLingerMs()));
            props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.getCompressionType());
            props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 
                String.valueOf(config.getMaxInFlightRequests()));
            
            // Idempotence for exactly-once semantics
            if (config.isEnableIdempotence()) {
                props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
            }
            
            // Add custom properties
            if (config.getCustomProperties() != null) {
                props.putAll(config.getCustomProperties());
            }
            
            producer = new KafkaProducer<>(props);
            
            status.set(ProducerStatus.RUNNING);
            logger.info("Kafka producer started successfully");
            
            return null;
        });
    }
    
    @Override
    public Promise<String> send(String key, String value) {
        return send(key, value, null);
    }
    
    @Override
    public Promise<String> send(String key, String value, Map<String, String> headers) {
        if (status.get() != ProducerStatus.RUNNING) {
            return Promise.ofException(
                new IllegalStateException("Producer not running. Status: " + status.get())
            );
        }
        
        return Promise.ofCallback(callback -> {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                config.getTopic(),
                null, // partition (let Kafka decide)
                key,
                value
            );
            
            // Add headers if present
            if (headers != null) {
                headers.forEach((k, v) -> 
                    record.headers().add(k, v.getBytes())
                );
            }
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send message to Kafka: topic={}, key={}", 
                        config.getTopic(), key, exception);
                    eventloop.execute(() -> callback.setException(exception));
                } else {
                    String messageId = formatMessageId(metadata);
                    logger.debug("Sent message to Kafka: {}", messageId);
                    eventloop.execute(() -> callback.set(messageId));
                }
            });
        });
    }
    
    @Override
    public Promise<List<String>> sendBatch(List<QueueMessage> messages) {
        if (status.get() != ProducerStatus.RUNNING) {
            return Promise.ofException(
                new IllegalStateException("Producer not running. Status: " + status.get())
            );
        }
        
        List<Promise<String>> sendPromises = new ArrayList<>();
        
        for (QueueMessage msg : messages) {
            sendPromises.add(send(msg.messageId(), msg.payload()));
        }
        
        return Promises.toList(sendPromises);
    }
    
    @Override
    public Promise<Void> flush() {
        return Promise.ofBlocking(executor, () -> {
            if (producer != null) {
                producer.flush();
            }
            return null;
        });
    }
    
    @Override
    public Promise<Void> stop() {
        ProducerStatus currentStatus = status.get();
        if (currentStatus == ProducerStatus.STOPPED || currentStatus == ProducerStatus.STOPPING) {
            return Promise.complete();
        }
        
        status.set(ProducerStatus.STOPPING);
        
        return Promise.ofBlocking(executor, () -> {
            logger.info("Stopping Kafka producer");
            
            try {
                if (producer != null) {
                    producer.flush();
                    producer.close(Duration.ofSeconds(10));
                }
                
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } finally {
                status.set(ProducerStatus.STOPPED);
                logger.info("Kafka producer stopped");
            }
            
            return null;
        });
    }
    
    @Override
    public ProducerStatus getStatus() {
        return status.get();
    }
    
    /**
     * Formats a unique message ID from Kafka record metadata.
     */
    private String formatMessageId(RecordMetadata metadata) {
        return String.format("%s-%d-%d", metadata.topic(), metadata.partition(), metadata.offset());
    }
}
