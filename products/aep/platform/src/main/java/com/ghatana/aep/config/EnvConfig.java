/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Environment variable reader for AEP configuration.
 *
 * <p>Centralises all environment-based configuration access so that no
 * AEP class ever calls {@link System#getenv(String)} directly. Every
 * supported variable is documented here with its default value.
 *
 * <h2>Supported Variables</h2>
 * <table border="1">
 *   <tr><th>Variable</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>KAFKA_BOOTSTRAP_SERVERS</td><td>localhost:9092</td><td>Kafka broker list</td></tr>
 *   <tr><td>KAFKA_CONSUMER_GROUP</td><td>aep-consumer-group</td><td>Kafka consumer group id</td></tr>
 *   <tr><td>KAFKA_INPUT_TOPIC</td><td>events</td><td>Default input topic name</td></tr>
 *   <tr><td>KAFKA_OUTPUT_TOPIC</td><td>events-out</td><td>Default output topic name</td></tr>
 *   <tr><td>RABBITMQ_HOST</td><td>localhost</td><td>RabbitMQ broker hostname</td></tr>
 *   <tr><td>RABBITMQ_PORT</td><td>5672</td><td>RabbitMQ AMQP port</td></tr>
 *   <tr><td>RABBITMQ_QUEUE</td><td>aep-events</td><td>Default queue name</td></tr>
 *   <tr><td>REDIS_HOST</td><td>localhost</td><td>Redis hostname</td></tr>
 *   <tr><td>REDIS_PORT</td><td>6379</td><td>Redis port</td></tr>
 *   <tr><td>HTTP_INGRESS_ENDPOINT</td><td>http://localhost:8080/events</td><td>HTTP polling endpoint</td></tr>
 *   <tr><td>SQS_REGION</td><td>us-east-1</td><td>AWS region for SQS</td></tr>
 *   <tr><td>SQS_QUEUE_NAME</td><td>aep-events</td><td>SQS queue name</td></tr>
 *   <tr><td>SQS_QUEUE_URL</td><td>(derived)</td><td>Full SQS queue URL; derived if not set</td></tr>
 *   <tr><td>S3_REGION</td><td>us-east-1</td><td>AWS region for S3</td></tr>
 *   <tr><td>S3_BUCKET</td><td>aep-storage</td><td>S3 bucket name</td></tr>
 *   <tr><td>AEP_DB_URL</td><td>jdbc:postgresql://localhost:5432/aep</td><td>JDBC URL for the AEP PostgreSQL database</td></tr>
 *   <tr><td>AEP_DB_USERNAME</td><td>aep</td><td>AEP database username</td></tr>
 *   <tr><td>AEP_DB_PASSWORD</td><td>(required)</td><td>AEP database password</td></tr>
 *   <tr><td>AEP_DB_POOL_SIZE</td><td>10</td><td>HikariCP max pool size for the AEP database</td></tr>
 *   <tr><td>APP_ENV</td><td>production</td><td>deployment environment (development|production)</td></tr>
 * </table>
 *
 * @doc.type class
 * @doc.purpose Centralised environment variable configuration reader for AEP
 * @doc.layer product
 * @doc.pattern Configuration
 */
public final class EnvConfig {

    private static final Logger LOG = LoggerFactory.getLogger(EnvConfig.class);

    // ========================================================================
    //  Variable names (constants — use these everywhere instead of strings)
    // ========================================================================

    public static final String KAFKA_BOOTSTRAP_SERVERS = "KAFKA_BOOTSTRAP_SERVERS";
    public static final String KAFKA_CONSUMER_GROUP    = "KAFKA_CONSUMER_GROUP";
    public static final String KAFKA_INPUT_TOPIC       = "KAFKA_INPUT_TOPIC";
    public static final String KAFKA_OUTPUT_TOPIC      = "KAFKA_OUTPUT_TOPIC";
    public static final String RABBITMQ_HOST           = "RABBITMQ_HOST";
    public static final String RABBITMQ_PORT           = "RABBITMQ_PORT";
    public static final String RABBITMQ_QUEUE          = "RABBITMQ_QUEUE";
    public static final String REDIS_HOST              = "REDIS_HOST";
    public static final String REDIS_PORT              = "REDIS_PORT";
    public static final String HTTP_INGRESS_ENDPOINT   = "HTTP_INGRESS_ENDPOINT";
    public static final String SQS_REGION              = "SQS_REGION";
    public static final String SQS_QUEUE_NAME          = "SQS_QUEUE_NAME";
    public static final String SQS_QUEUE_URL           = "SQS_QUEUE_URL";
    public static final String S3_REGION               = "S3_REGION";
    public static final String S3_BUCKET               = "S3_BUCKET";
    public static final String APP_ENV                 = "APP_ENV";
    public static final String AEP_DB_URL              = "AEP_DB_URL";
    public static final String AEP_DB_USERNAME         = "AEP_DB_USERNAME";
    public static final String AEP_DB_PASSWORD         = "AEP_DB_PASSWORD";
    public static final String AEP_DB_POOL_SIZE        = "AEP_DB_POOL_SIZE";

    private final Map<String, String> env;

    /** Creates an instance reading from real system environment variables. */
    public static EnvConfig fromSystem() {
        return new EnvConfig(System.getenv());
    }

    /**
     * Creates an instance reading from the supplied map.
     * Primarily for testing without modifying system env.
     *
     * @param env environment map
     */
    public static EnvConfig fromMap(Map<String, String> env) {
        return new EnvConfig(Map.copyOf(env));
    }

    private EnvConfig(Map<String, String> env) {
        this.env = env;
    }

    /**
     * Returns the value of {@code key}, or {@code defaultValue} if absent or blank.
     *
     * @param key          environment variable name
     * @param defaultValue fallback value
     * @return resolved value
     */
    public String get(String key, String defaultValue) {
        String value = env.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * Returns the integer value of {@code key}, or {@code defaultValue} if absent.
     *
     * @param key          environment variable name
     * @param defaultValue fallback integer
     * @return resolved integer
     * @throws IllegalStateException if the variable is set but not a valid integer
     */
    public int getInt(String key, int defaultValue) {
        String value = env.get(key);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Environment variable '" + key + "' must be an integer, but got: " + value, e);
        }
    }

    /**
     * Returns the value of {@code key}, throwing if absent or blank.
     *
     * @param key environment variable name
     * @return value
     * @throws IllegalStateException if the variable is not set
     */
    public String require(String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Required environment variable '" + key + "' is not set.");
        }
        return value;
    }

    /** @return true if {@code APP_ENV} is {@code development} (case-insensitive) */
    public boolean isDevelopment() {
        return "development".equalsIgnoreCase(get(APP_ENV, "production"));
    }

    // ========================================================================
    //  Convenient typed accessors
    // ========================================================================

    public String kafkaBootstrapServers()    { return get(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092"); }
    public String kafkaConsumerGroup()       { return get(KAFKA_CONSUMER_GROUP, "aep-consumer-group"); }
    public String kafkaInputTopic()          { return get(KAFKA_INPUT_TOPIC, "events"); }
    public String kafkaOutputTopic()         { return get(KAFKA_OUTPUT_TOPIC, "events-out"); }
    public String rabbitMqHost()             { return get(RABBITMQ_HOST, "localhost"); }
    public int    rabbitMqPort()             { return getInt(RABBITMQ_PORT, 5672); }
    public String rabbitMqQueue()            { return get(RABBITMQ_QUEUE, "aep-events"); }
    public String redisHost()                { return get(REDIS_HOST, "localhost"); }
    public int    redisPort()                { return getInt(REDIS_PORT, 6379); }
    public String httpIngressEndpoint()      { return get(HTTP_INGRESS_ENDPOINT, "http://localhost:8080/events"); }
    public String sqsRegion()               { return get(SQS_REGION, "us-east-1"); }
    public String sqsQueueName()            { return get(SQS_QUEUE_NAME, "aep-events"); }
    public String sqsQueueUrl() {
        String explicit = env.get(SQS_QUEUE_URL);
        if (explicit != null && !explicit.isBlank()) return explicit;
        // Derive URL from region + queue name when not explicitly set
        String accountId = get("AWS_ACCOUNT_ID", "000000000000");
        return "https://sqs." + sqsRegion() + ".amazonaws.com/" + accountId + "/" + sqsQueueName();
    }
    public String s3Region()                { return get(S3_REGION, "us-east-1"); }
    public String s3Bucket()                { return get(S3_BUCKET, "aep-storage"); }

    // =====================================================================
    //  AEP Database
    // =====================================================================

    public String aepDbUrl()       { return get(AEP_DB_URL, "jdbc:postgresql://localhost:5432/aep"); }
    public String aepDbUsername()  { return get(AEP_DB_USERNAME, "aep"); }
    public String aepDbPassword()  { return require(AEP_DB_PASSWORD); }
    public int    aepDbPoolSize()  { return getInt(AEP_DB_POOL_SIZE, 10); }
}
