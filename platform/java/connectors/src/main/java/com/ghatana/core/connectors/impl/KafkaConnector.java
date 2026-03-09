package com.ghatana.core.connectors.impl;

import com.ghatana.core.connectors.BaseConnector;
import com.ghatana.core.connectors.Connector;
import com.ghatana.platform.observability.util.PromisesCompat;
import io.activej.promise.Promise;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kafka connector implementation supporting both source and sink operations.
 * Produces to and consumes from Kafka topics with metrics, health checks, and standardized configuration.
 *
 * @doc.type class
 * @doc.purpose Kafka-based connector for pub/sub messaging and observability data streaming
 * @doc.layer observability
 * @doc.pattern Adapter, Implementation
 *
 * <p>Provides Apache Kafka integration with producer (sink) and consumer (source)
 * capabilities. Supports configurable Kafka client properties, automatic topic
 * subscription, and graceful shutdown with connection cleanup.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Source mode: KafkaConsumer subscribes to topic with configurable offset reset</li>
 *   <li>Sink mode: KafkaProducer publishes to topic with acks, retries, compression</li>
 *   <li>Health checks verify topic existence and client connectivity</li>
 *   <li>Configurable producer/consumer properties (batching, compression, timeouts)</li>
 *   <li>Automatic client ID assignment for tracking</li>
 *   <li>Graceful shutdown with 5-second timeout</li>
 * </ul>
 *
 * <h2>Configuration:</h2>
 * Required properties:
 * - {@code bootstrap.servers}: Kafka broker addresses (default: localhost:9092)
 * - {@code topic}: Kafka topic name (default: default-topic)
 *
 * Optional properties:
 * - {@code source.enabled}: Enable consumer (reading) mode
 * - {@code sink.enabled}: Enable producer (writing) mode
 * - {@code producer.acks}: Producer acks (all, 1, 0) - default: all
 * - {@code producer.retries}: Producer retry count - default: 3
 * - {@code producer.compression.type}: Compression (none, gzip, snappy, lz4) - default: none
 * - {@code consumer.group.id}: Consumer group - default: connector-{name}
 * - {@code consumer.auto.offset.reset}: Offset reset (earliest, latest) - default: earliest
 * - {@code consumer.enable.auto.commit}: Auto-commit - default: false
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create producer connector
 * KafkaConnector producer = new KafkaConnector("events-producer");
 * ConnectorConfig config = KafkaConnectorConfig.builder()
 *     .name("events-producer")
 *     .bootstrapServers("localhost:9092")
 *     .topic("events")
 *     .sinkEnabled(true)
 *     .build();
 * 
 * producer.initialize(config)
 *     .then(() -> producer.start())
 *     .whenResult(() -> {
 *         // Send events
 *         KafkaProducer<String, String> prod = producer.getProducer();
 *         prod.send(new ProducerRecord<>("events", "key", "value"));
 *     });
 * 
 * // Create consumer connector
 * KafkaConnector consumer = new KafkaConnector("events-consumer");
 * ConnectorConfig consumerConfig = KafkaConnectorConfig.builder()
 *     .name("events-consumer")
 *     .bootstrapServers("localhost:9092")
 *     .topic("events")
 *     .sourceEnabled(true)
 *     .build();
 * 
 * consumer.initialize(consumerConfig)
 *     .then(() -> consumer.start())
 *     .whenResult(() -> {
 *         // Poll events
 *         KafkaConsumer<String, String> cons = consumer.getConsumer();
 *         cons.poll(Duration.ofMillis(100));
 *     });
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. KafkaProducer is thread-safe; KafkaConsumer should be accessed
 * from single thread (caller's responsibility).
 *
 * <h2>Performance Characteristics:</h2>
 * - Producer: Batched, async sends with configurable acks
 * - Consumer: Polling-based with configurable batch size
 * - Health check: O(1) - checks topic existence via metadata
 *
 * <h2>Best Practices:</h2>
 * - Use compression (gzip/snappy) for high-throughput scenarios
 * - Set {@code acks=all} for durability (default)
 * - Disable {@code enable.auto.commit} and commit manually
 * - Monitor lag via ConnectorMetrics
 *
 * @since 1.0.0
 */
public class KafkaConnector extends BaseConnector {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaConnector.class);
    
    private final AtomicReference<KafkaProducer<String, String>> producer = new AtomicReference<>();
    private final AtomicReference<KafkaConsumer<String, String>> consumer = new AtomicReference<>();
    private final AtomicReference<String> topic = new AtomicReference<>();
    
    public KafkaConnector(String name) {
        super(name, "kafka");
    }
    
    @Override
    protected Promise<Void> doInitialize(ConnectorConfig config) {
        return PromisesCompat.runBlocking(() -> {
            try {
                topic.set(config.getProperty("topic", String.class, "default-topic"));
                
                // Initialize producer if sink-capable
                if (isSinkCapable()) {
                    Properties producerProps = createProducerProperties(config);
                    producer.set(new KafkaProducer<>(producerProps));
                    logger.info("Kafka producer initialized for topic: {}", topic.get());
                }
                
                // Initialize consumer if source-capable
                if (isSourceCapable()) {
                    Properties consumerProps = createConsumerProperties(config);
                    consumer.set(new KafkaConsumer<>(consumerProps));
                    logger.info("Kafka consumer initialized for topic: {}", topic.get());
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to initialize Kafka connector", e);
                throw e;
            }
        });
    }
    
    @Override
    protected Promise<Void> doStart() {
        return PromisesCompat.runBlocking(() -> {
            try {
                // Subscribe consumer if source-capable
                if (isSourceCapable() && consumer.get() != null) {
                    consumer.get().subscribe(Collections.singletonList(topic.get()));
                    logger.info("Kafka consumer subscribed to topic: {}", topic.get());
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to start Kafka connector", e);
                throw e;
            }
        });
    }
    
    @Override
    protected Promise<Void> doStop() {
        return PromisesCompat.runBlocking(() -> {
            try {
                // Close producer if initialized
                if (producer.get() != null) {
                    producer.get().close(Duration.ofSeconds(5));
                    producer.set(null);
                    logger.info("Kafka producer closed");
                }
                
                // Close consumer if initialized
                if (consumer.get() != null) {
                    consumer.get().close(Duration.ofSeconds(5));
                    consumer.set(null);
                    logger.info("Kafka consumer closed");
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to stop Kafka connector", e);
                throw e;
            }
        });
    }
    
    @Override
    protected Promise<HealthCheckResult> doHealthCheck() {
        return PromisesCompat.runBlocking(() -> {
            try {
                boolean producerHealthy = !isSinkCapable() || producer.get() != null;
                boolean consumerHealthy = !isSourceCapable() || (consumer.get() != null && consumer.get().listTopics().containsKey(topic.get()));
                
                if (producerHealthy && consumerHealthy) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("topic", topic.get());
                    details.put("producerInitialized", producer.get() != null);
                    details.put("consumerInitialized", consumer.get() != null);
                    details.put("messagesProcessed", getMetrics().getMessagesProcessed());
                    
                    return HealthCheckResult.healthy("Kafka connector is healthy", details, Duration.ZERO);
                } else {
                    Map<String, Object> details = new HashMap<>();
                    details.put("topic", topic.get());
                    details.put("producerHealthy", producerHealthy);
                    details.put("consumerHealthy", consumerHealthy);
                    
                    return HealthCheckResult.unhealthy("Kafka connector is unhealthy", details, Duration.ZERO, null);
                }
            } catch (Exception e) {
                return HealthCheckResult.unhealthy("Kafka health check failed", e);
            }
        });
    }
    
    @Override
    public boolean isSourceCapable() {
        ConnectorConfig cfg = getConfig();
        return cfg != null && cfg.getProperty("source.enabled", Boolean.class, false);
    }
    
    @Override
    public boolean isSinkCapable() {
        ConnectorConfig cfg = getConfig();
        return cfg != null && cfg.getProperty("sink.enabled", Boolean.class, false);
    }
    
    /**
     * Create Kafka producer properties from connector configuration.
     */
    private Properties createProducerProperties(ConnectorConfig config) {
        Properties props = new Properties();
        
        // Required properties
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                 config.getProperty("bootstrap.servers", String.class, "localhost:9092"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Optional properties with defaults
        props.put(ProducerConfig.ACKS_CONFIG, 
                 config.getProperty("producer.acks", String.class, "all"));
        props.put(ProducerConfig.RETRIES_CONFIG, 
                 config.getProperty("producer.retries", Integer.class, 3));
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 
                 config.getProperty("producer.batch.size", Integer.class, 16384));
        props.put(ProducerConfig.LINGER_MS_CONFIG, 
                 config.getProperty("producer.linger.ms", Integer.class, 1));
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 
                 config.getProperty("producer.buffer.memory", Integer.class, 33554432));
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, 
                 config.getProperty("producer.compression.type", String.class, "none"));
        
        // Add client ID for tracking
        props.put(ProducerConfig.CLIENT_ID_CONFIG, 
                 "connector-" + getName() + "-producer");
        
        return props;
    }
    
    /**
     * Create Kafka consumer properties from connector configuration.
     */
    private Properties createConsumerProperties(ConnectorConfig config) {
        Properties props = new Properties();
        
        // Required properties
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                 config.getProperty("bootstrap.servers", String.class, "localhost:9092"));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        
        // Optional properties with defaults
        props.put(ConsumerConfig.GROUP_ID_CONFIG, 
                 config.getProperty("consumer.group.id", String.class, "connector-" + getName()));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, 
                 config.getProperty("consumer.auto.offset.reset", String.class, "earliest"));
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, 
                 config.getProperty("consumer.enable.auto.commit", Boolean.class, false));
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 
                 config.getProperty("consumer.max.poll.records", Integer.class, 500));
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 
                 config.getProperty("consumer.max.poll.interval.ms", Integer.class, 300000));
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 
                 config.getProperty("consumer.session.timeout.ms", Integer.class, 10000));
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 
                 config.getProperty("consumer.heartbeat.interval.ms", Integer.class, 3000));
        
        // Add client ID for tracking
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, 
                 "connector-" + getName() + "-consumer");
        
        return props;
    }
    
    /**
     * Get the Kafka producer instance.
     */
    public KafkaProducer<String, String> getProducer() {
        return producer.get();
    }
    
    /**
     * Get the Kafka consumer instance.
     */
    public KafkaConsumer<String, String> getConsumer() {
        return consumer.get();
    }
    
    /**
     * Get the configured topic.
     */
    public String getTopic() {
        return topic.get();
    }
    
    /**
     * Create a default configuration for the Kafka connector.
     */
    public static class KafkaConnectorConfig implements Connector.ConnectorConfig {
        private final String name;
        private final Map<String, Object> properties;
        private final Duration timeout;
        private final int retryAttempts;
        private final Duration retryDelay;
        private final boolean enabled;
        
        public KafkaConnectorConfig(String name, Map<String, Object> properties, 
                                  Duration timeout, int retryAttempts, 
                                  Duration retryDelay, boolean enabled) {
            this.name = name;
            this.properties = new HashMap<>(properties);
            this.timeout = timeout;
            this.retryAttempts = retryAttempts;
            this.retryDelay = retryDelay;
            this.enabled = enabled;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public String getType() {
            return "kafka";
        }
        
        @Override
        public Map<String, Object> getProperties() {
            return new HashMap<>(properties);
        }
        
        @Override
        public <T> T getProperty(String key, Class<T> type) {
            Object value = properties.get(key);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            throw new IllegalArgumentException("Property " + key + " is not of type " + type.getName());
        }
        
        @Override
        public <T> T getProperty(String key, Class<T> type, T defaultValue) {
            T value = getProperty(key, type);
            return value != null ? value : defaultValue;
        }
        
        @Override
        public Duration getTimeout() {
            return timeout;
        }
        
        @Override
        public int getRetryAttempts() {
            return retryAttempts;
        }
        
        @Override
        public Duration getRetryDelay() {
            return retryDelay;
        }
        
        @Override
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * Builder for KafkaConnectorConfig.
         */
        public static class Builder {
            private String name;
            private final Map<String, Object> properties = new HashMap<>();
            private Duration timeout = Duration.ofSeconds(30);
            private int retryAttempts = 3;
            private Duration retryDelay = Duration.ofSeconds(1);
            private boolean enabled = true;
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder property(String key, Object value) {
                properties.put(key, value);
                return this;
            }
            
            public Builder properties(Map<String, Object> properties) {
                this.properties.putAll(properties);
                return this;
            }
            
            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }
            
            public Builder retryAttempts(int retryAttempts) {
                this.retryAttempts = retryAttempts;
                return this;
            }
            
            public Builder retryDelay(Duration retryDelay) {
                this.retryDelay = retryDelay;
                return this;
            }
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder bootstrapServers(String bootstrapServers) {
                properties.put("bootstrap.servers", bootstrapServers);
                return this;
            }
            
            public Builder topic(String topic) {
                properties.put("topic", topic);
                return this;
            }
            
            public Builder sourceEnabled(boolean enabled) {
                properties.put("source.enabled", enabled);
                return this;
            }
            
            public Builder sinkEnabled(boolean enabled) {
                properties.put("sink.enabled", enabled);
                return this;
            }
            
            public KafkaConnectorConfig build() {
                if (name == null || name.isEmpty()) {
                    throw new IllegalArgumentException("Connector name is required");
                }
                return new KafkaConnectorConfig(name, properties, timeout, retryAttempts, retryDelay, enabled);
            }
        }
        
        public static Builder builder() {
            return new Builder();
        }
    }
}
