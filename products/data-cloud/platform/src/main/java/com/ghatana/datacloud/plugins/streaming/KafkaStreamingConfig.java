package com.ghatana.datacloud.plugins.kafka;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration for Apache Kafka Streaming Plugin.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides comprehensive Kafka configuration supporting:
 * <ul>
 * <li>Cluster connection (bootstrap servers)</li>
 * <li>Authentication (SASL/SCRAM, SSL/TLS)</li>
 * <li>Consumer/Producer tuning</li>
 * <li>Exactly-once semantics</li>
 * <li>Topic naming conventions</li>
 * </ul>
 *
 * <p>
 * <b>Security Configuration</b><br>
 * <pre>{@code
 * KafkaStreamingConfig config = KafkaStreamingConfig.builder()
 *     .bootstrapServers("kafka-1:9092,kafka-2:9092")
 *     .securityProtocol(SecurityProtocol.SASL_SSL)
 *     .saslMechanism("SCRAM-SHA-512")
 *     .saslUsername("eventcloud")
 *     .saslPassword("secret")
 *     .sslTruststoreLocation("/path/to/truststore.jks")
 *     .sslTruststorePassword("changeit")
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Performance Tuning</b><br>
 * <pre>{@code
 * KafkaStreamingConfig config = KafkaStreamingConfig.builder()
 *     .bootstrapServers("kafka:9092")
 *     .producerBatchSize(65536)           // 64KB batches
 *     .producerLingerMs(5)                // 5ms linger
 *     .producerBufferMemory(67108864)     // 64MB buffer
 *     .producerCompressionType("lz4")     // LZ4 compression
 *     .consumerMaxPollRecords(1000)       // 1000 records per poll
 *     .consumerFetchMinBytes(1048576)     // 1MB minimum fetch
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Configuration for Kafka streaming plugin
 * @doc.layer plugin
 * @doc.pattern Configuration, Builder
 */
@Value
@Builder(toBuilder = true)
public class KafkaStreamingConfig {

    // ==================== Connection ====================
    /**
     * Comma-separated list of Kafka bootstrap servers. Example:
     * "kafka-1:9092,kafka-2:9092,kafka-3:9092"
     */
    @Builder.Default
    String bootstrapServers = "localhost:9092";

    /**
     * Client ID prefix for identification in Kafka.
     */
    @Builder.Default
    String clientIdPrefix = "eventcloud";

    // ==================== Security ====================
    /**
     * Security protocol: PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL
     */
    @Builder.Default
    SecurityProtocol securityProtocol = SecurityProtocol.PLAINTEXT;

    /**
     * SASL mechanism: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI
     */
    @Builder.Default
    String saslMechanism = "PLAIN";

    /**
     * SASL username for authentication.
     */
    String saslUsername;

    /**
     * SASL password for authentication.
     */
    String saslPassword;

    /**
     * Path to SSL truststore.
     */
    String sslTruststoreLocation;

    /**
     * Password for SSL truststore.
     */
    String sslTruststorePassword;

    /**
     * Path to SSL keystore (for mTLS).
     */
    String sslKeystoreLocation;

    /**
     * Password for SSL keystore.
     */
    String sslKeystorePassword;

    // ==================== Topic Configuration ====================
    /**
     * Topic prefix for EventCloud topics. Full topic name:
     * {topicPrefix}.{tenantId}.{streamName}
     */
    @Builder.Default
    String topicPrefix = "eventcloud";

    /**
     * Default number of partitions for auto-created topics.
     */
    @Builder.Default
    int defaultPartitions = 12;

    /**
     * Default replication factor for topics.
     */
    @Builder.Default
    short defaultReplicationFactor = 3;

    /**
     * Retention period for topics.
     */
    @Builder.Default
    Duration topicRetention = Duration.ofDays(7);

    // ==================== Producer Configuration ====================
    /**
     * Producer acks: 0, 1, all
     */
    @Builder.Default
    String producerAcks = "all";

    /**
     * Producer retries on transient failures.
     */
    @Builder.Default
    int producerRetries = 3;

    /**
     * Producer batch size in bytes.
     */
    @Builder.Default
    int producerBatchSize = 65536; // 64KB

    /**
     * Producer linger time in milliseconds.
     */
    @Builder.Default
    int producerLingerMs = 5;

    /**
     * Producer buffer memory in bytes.
     */
    @Builder.Default
    long producerBufferMemory = 67108864L; // 64MB

    /**
     * Producer compression type: none, gzip, snappy, lz4, zstd
     */
    @Builder.Default
    String producerCompressionType = "lz4";

    /**
     * Enable idempotent producer for exactly-once semantics.
     */
    @Builder.Default
    boolean producerIdempotence = true;

    // ==================== Consumer Configuration ====================
    /**
     * Consumer group ID prefix.
     */
    @Builder.Default
    String consumerGroupPrefix = "eventcloud-consumer";

    /**
     * Maximum records per poll.
     */
    @Builder.Default
    int consumerMaxPollRecords = 500;

    /**
     * Minimum fetch size in bytes.
     */
    @Builder.Default
    int consumerFetchMinBytes = 1048576; // 1MB

    /**
     * Maximum fetch wait time.
     */
    @Builder.Default
    Duration consumerFetchMaxWait = Duration.ofMillis(500);

    /**
     * Session timeout for consumer group coordination.
     */
    @Builder.Default
    Duration consumerSessionTimeout = Duration.ofSeconds(30);

    /**
     * Heartbeat interval (should be < session timeout / 3).
     */
    @Builder.Default
    Duration consumerHeartbeatInterval = Duration.ofSeconds(10);

    /**
     * Maximum poll interval before consumer is considered dead.
     */
    @Builder.Default
    Duration consumerMaxPollInterval = Duration.ofMinutes(5);

    /**
     * Auto offset reset: earliest, latest, none
     */
    @Builder.Default
    String consumerAutoOffsetReset = "latest";

    /**
     * Enable auto commit (false for exactly-once).
     */
    @Builder.Default
    boolean consumerEnableAutoCommit = false;

    // ==================== Exactly-Once Configuration ====================
    /**
     * Enable exactly-once semantics with transactions.
     */
    @Builder.Default
    boolean exactlyOnceEnabled = true;

    /**
     * Transaction timeout for exactly-once.
     */
    @Builder.Default
    Duration transactionTimeout = Duration.ofSeconds(60);

    /**
     * Isolation level: read_committed, read_uncommitted
     */
    @Builder.Default
    String isolationLevel = "read_committed";

    // ==================== Methods ====================
    /**
     * Creates default configuration for development.
     */
    public static KafkaStreamingConfig defaults() {
        return KafkaStreamingConfig.builder().build();
    }

    /**
     * Creates production configuration with security enabled.
     */
    public static KafkaStreamingConfig production(String bootstrapServers,
            String username,
            String password) {
        return KafkaStreamingConfig.builder()
                .bootstrapServers(bootstrapServers)
                .securityProtocol(SecurityProtocol.SASL_SSL)
                .saslMechanism("SCRAM-SHA-512")
                .saslUsername(username)
                .saslPassword(password)
                .producerAcks("all")
                .producerIdempotence(true)
                .exactlyOnceEnabled(true)
                .build();
    }

    /**
     * Builds topic name from tenant and stream.
     */
    public String buildTopicName(String tenantId, String streamName) {
        return String.format("%s.%s.%s", topicPrefix, tenantId, streamName);
    }

    /**
     * Builds consumer group ID.
     */
    public String buildConsumerGroupId(String tenantId, String groupName) {
        return String.format("%s-%s-%s", consumerGroupPrefix, tenantId, groupName);
    }

    /**
     * Converts to Kafka producer properties.
     */
    public Properties toProducerProperties() {
        Properties props = new Properties();

        // Connection
        props.put("bootstrap.servers", bootstrapServers);
        props.put("client.id", clientIdPrefix + "-producer");

        // Security
        applySecurityProperties(props);

        // Producer settings
        props.put("acks", producerAcks);
        props.put("retries", producerRetries);
        props.put("batch.size", producerBatchSize);
        props.put("linger.ms", producerLingerMs);
        props.put("buffer.memory", producerBufferMemory);
        props.put("compression.type", producerCompressionType);
        props.put("enable.idempotence", producerIdempotence);

        // Serializers
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

        // Exactly-once
        if (exactlyOnceEnabled) {
            props.put("transactional.id", clientIdPrefix + "-tx");
            props.put("transaction.timeout.ms", (int) transactionTimeout.toMillis());
        }

        return props;
    }

    /**
     * Converts to Kafka consumer properties.
     */
    public Properties toConsumerProperties(String groupId) {
        Properties props = new Properties();

        // Connection
        props.put("bootstrap.servers", bootstrapServers);
        props.put("client.id", clientIdPrefix + "-consumer");
        props.put("group.id", groupId);

        // Security
        applySecurityProperties(props);

        // Consumer settings
        props.put("max.poll.records", consumerMaxPollRecords);
        props.put("fetch.min.bytes", consumerFetchMinBytes);
        props.put("fetch.max.wait.ms", (int) consumerFetchMaxWait.toMillis());
        props.put("session.timeout.ms", (int) consumerSessionTimeout.toMillis());
        props.put("heartbeat.interval.ms", (int) consumerHeartbeatInterval.toMillis());
        props.put("max.poll.interval.ms", (int) consumerMaxPollInterval.toMillis());
        props.put("auto.offset.reset", consumerAutoOffsetReset);
        props.put("enable.auto.commit", consumerEnableAutoCommit);

        // Deserializers
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        // Exactly-once
        if (exactlyOnceEnabled) {
            props.put("isolation.level", isolationLevel);
        }

        return props;
    }

    /**
     * Applies security properties.
     */
    private void applySecurityProperties(Properties props) {
        props.put("security.protocol", securityProtocol.name());

        if (securityProtocol == SecurityProtocol.SASL_PLAINTEXT
                || securityProtocol == SecurityProtocol.SASL_SSL) {
            props.put("sasl.mechanism", saslMechanism);
            if (saslUsername != null && saslPassword != null) {
                String jaasConfig = String.format(
                        "org.apache.kafka.common.security.scram.ScramLoginModule required "
                        + "username=\"%s\" password=\"%s\";",
                        saslUsername, saslPassword);
                props.put("sasl.jaas.config", jaasConfig);
            }
        }

        if (securityProtocol == SecurityProtocol.SSL
                || securityProtocol == SecurityProtocol.SASL_SSL) {
            if (sslTruststoreLocation != null) {
                props.put("ssl.truststore.location", sslTruststoreLocation);
                props.put("ssl.truststore.password", sslTruststorePassword);
            }
            if (sslKeystoreLocation != null) {
                props.put("ssl.keystore.location", sslKeystoreLocation);
                props.put("ssl.keystore.password", sslKeystorePassword);
            }
        }
    }

    /**
     * Validates configuration.
     */
    public void validate() {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("bootstrapServers is required");
        }

        if (securityProtocol == SecurityProtocol.SASL_SSL
                || securityProtocol == SecurityProtocol.SASL_PLAINTEXT) {
            if (saslUsername == null || saslPassword == null) {
                throw new IllegalArgumentException("SASL credentials required for " + securityProtocol);
            }
        }

        if (consumerHeartbeatInterval.toMillis() >= consumerSessionTimeout.toMillis() / 3) {
            throw new IllegalArgumentException(
                    "heartbeatInterval should be less than sessionTimeout/3");
        }
    }

    /**
     * Security protocol enum.
     */
    public enum SecurityProtocol {
        PLAINTEXT,
        SSL,
        SASL_PLAINTEXT,
        SASL_SSL
    }
}
