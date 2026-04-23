/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.aep.config;

import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Environment configuration for AEP components.
  * @doc.type class
 * @doc.purpose Provides env config functionality.
 * @doc.layer product
 * @doc.pattern Configuration
*/
public class EnvConfig {

    private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);

    // ── Well-known configuration key constants ──────────────────────────────
    public static final String RABBITMQ_PORT = "rabbitmq.port";
    public static final String REDIS_PORT = "redis.port";
    public static final String AEP_DB_POOL_SIZE = "db.pool.size";
    public static final String AEP_CONSOLIDATION_INTERVAL_HOURS = "consolidation.interval.hours";
    public static final String KAFKA_BOOTSTRAP_SERVERS = "KAFKA_BOOTSTRAP_SERVERS";

    /** Key for the application environment (development / production / staging). */
    public static final String APP_ENV = "APP_ENV";

    /** Key for the Redis host. */
    public static final String REDIS_HOST = "REDIS_HOST";

    /** Key for the Kafka consumer group. */
    public static final String KAFKA_CONSUMER_GROUP = "KAFKA_CONSUMER_GROUP";

    /** Key for the Kafka input topic. */
    public static final String KAFKA_INPUT_TOPIC = "KAFKA_INPUT_TOPIC";

    /** Key for the Kafka output topic. */
    public static final String KAFKA_OUTPUT_TOPIC = "KAFKA_OUTPUT_TOPIC";

    /** Key for the RabbitMQ host. */
    public static final String RABBITMQ_HOST = "RABBITMQ_HOST";

    /** Key for the RabbitMQ queue. */
    public static final String RABBITMQ_QUEUE = "RABBITMQ_QUEUE";

    /** Key for the AWS S3 region. */
    public static final String S3_REGION = "S3_REGION";

    /** Key for the AWS S3 bucket. */
    public static final String S3_BUCKET = "S3_BUCKET";

    private final Map<String, String> config;

    public EnvConfig() {
        this.config = new HashMap<>();
        loadDefaultValues();
    }

    public EnvConfig(Map<String, String> initialConfig) {
        this.config = new HashMap<>(initialConfig);
        loadDefaultValues();
    }

    private void loadDefaultValues() {
        // Load from system environment
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("AEP_")) {
                String configKey = key.substring(4).toLowerCase().replace('_', '.');
                config.put(configKey, value);
            }
        });
    }

    public String get(String key) {
        return config.get(key);
    }

    public String get(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Configuration key '" + key + "' has value '" + value
                    + "' which cannot be parsed as an integer", e);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            logger.warn("Invalid boolean config for {}='{}'; using default {}", key, value, defaultValue);
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public long getLong(String key, long defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid long config for {}='{}'; using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public void set(String key, String value) {
        config.put(key, value);
    }

    public boolean contains(String key) {
        return config.containsKey(key);
    }

    /**
     * Returns the configuration value for {@code key}, throwing
     * {@link IllegalStateException} if the key is absent or blank.
     *
     * <p>Use this method for mandatory configuration that must be present at startup.
     * Callers should invoke this during initialization so that missing config is
     * detected immediately rather than at first use.
     *
     * @param key configuration key (must not be {@code null})
     * @return the non-blank configuration value
     * @throws IllegalStateException if the key is absent or maps to a blank string
     */
    public String getRequired(String key) {
        String value = config.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Required configuration key '" + key + "' is not set. "
                    + "Set the corresponding environment variable or provide it at construction time.");
        }
        return value;
    }

    /**
     * Alias for {@link #getRequired(String)} for ergonomic use.
     *
     * @param key configuration key (must not be {@code null})
     * @return the non-blank configuration value
     * @throws IllegalStateException if the key is absent or maps to a blank string
     */
    public String require(String key) {
        return getRequired(key);
    }

    // ── Environment helpers ─────────────────────────────────────────────────

    /**
     * Returns {@code true} when the application is running in development mode.
     *
     * <p>Development mode is active when {@link #APP_ENV} is set to
     * {@code "development"} (case-insensitive).  In all other cases — including when
     * the key is absent — the method returns {@code false}, defaulting to the safer
     * production behaviour.
     *
     * @return {@code true} when APP_ENV equals "development"
     */
    public boolean isDevelopment() {
        return "development".equalsIgnoreCase(get(APP_ENV, "production"));
    }

    // ── Kafka typed accessors ───────────────────────────────────────────────

    /**
     * Returns the Kafka bootstrap servers string (default: {@code "localhost:9092"}).
     *
     * @return comma-separated list of broker {@code host:port} entries
     */
    public String kafkaBootstrapServers() {
        return get(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092");
    }

    /**
     * Returns the Kafka consumer group identifier (default: {@code "aep-consumer-group"}).
     *
     * @return consumer group identifier
     */
    public String kafkaConsumerGroup() {
        return get(KAFKA_CONSUMER_GROUP, "aep-consumer-group");
    }

    /**
     * Returns the Kafka input topic (default: {@code "events"}).
     *
     * @return input topic name
     */
    public String kafkaInputTopic() {
        return get(KAFKA_INPUT_TOPIC, "events");
    }

    /**
     * Returns the Kafka output topic (default: {@code "events-out"}).
     *
     * @return output topic name
     */
    public String kafkaOutputTopic() {
        return get(KAFKA_OUTPUT_TOPIC, "events-out");
    }

    // ── Redis typed accessors ───────────────────────────────────────────────

    /**
     * Returns the Redis server host (default: {@code "localhost"}).
     *
     * @return Redis host
     */
    public String redisHost() {
        return get(REDIS_HOST, "localhost");
    }

    /**
     * Returns the Redis server port (default: {@code 6379}).
     *
     * @return Redis port
     */
    public int redisPort() {
        return getInt(REDIS_PORT, 6379);
    }

    // ── RabbitMQ typed accessors ────────────────────────────────────────────

    /**
     * Returns the RabbitMQ host (default: {@code "localhost"}).
     *
     * @return RabbitMQ host
     */
    public String rabbitMqHost() {
        return get(RABBITMQ_HOST, "localhost");
    }

    /**
     * Returns the RabbitMQ port (default: {@code 5672}).
     *
     * @return RabbitMQ port
     */
    public int rabbitMqPort() {
        return getInt(RABBITMQ_PORT, 5672);
    }

    /**
     * Returns the RabbitMQ queue name (default: {@code "aep-events"}).
     *
     * @return RabbitMQ queue name
     */
    public String rabbitMqQueue() {
        return get(RABBITMQ_QUEUE, "aep-events");
    }

    // ── AWS / S3 typed accessors ────────────────────────────────────────────

    /**
     * Returns the AWS S3 region (default: {@code "us-east-1"}).
     *
     * @return S3 region identifier
     */
    public String s3Region() {
        return get(S3_REGION, "us-east-1");
    }

    /**
     * Returns the AWS S3 bucket name (default: {@code "aep-storage"}).
     *
     * @return S3 bucket name
     */
    public String s3Bucket() {
        return get(S3_BUCKET, "aep-storage");
    }

    public Map<String, String> getAll() {
        return new HashMap<>(config);
    }

    /**
     * Creates an EnvConfig instance populated from system environment variables.
     *
     * @return EnvConfig loaded from system environment
     */
    public static EnvConfig fromSystem() {
        return new EnvConfig();
    }

    /**
     * Creates an EnvConfig instance populated from the given map of key/value pairs.
     *
     * <p>The supplied map is copied; mutations after construction have no effect.
     * This factory is intended for testing and programmatic configuration assembly.
     *
     * @param config initial configuration entries (must not be {@code null})
     * @return EnvConfig backed by the given map
     */
    public static EnvConfig fromMap(Map<String, String> config) {
        return new EnvConfig(config);
    }

    /**
     * Gets the Data-Cloud base URL.
     *
     * @return Data-Cloud base URL, or {@code null} when not configured
     */
    public String aepDcBaseUrl() {
        return get("dc.base.url", System.getenv("AEP_DC_BASE_URL"));
    }

    @Override
    public String toString() {
        return "EnvConfig{" +
                "config=" + config +
                '}';
    }
}
