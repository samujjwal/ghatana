/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Base configuration for all AEP connector types.
 *
 * <p>Provides cross-cutting concerns shared by every connector: TLS, retry policy, and
 * request timeouts. Protocol-specific connection details (e.g. Kafka bootstrap servers,
 * SQS queue URL, S3 bucket) are added by each concrete subclass via its own typed builder.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class KafkaConsumerConfig extends ConnectorConfig {
 *     private final String bootstrapServers;
 *     private final String topic;
 *
 *     private KafkaConsumerConfig(KafkaBuilder b) {
 *         super(b);
 *         this.bootstrapServers = b.bootstrapServers;
 *         this.topic = b.topic;
 *     }
 *
 *     public static KafkaBuilder builder() { return new KafkaBuilder(); }
 *
 *     public static final class KafkaBuilder extends Builder<KafkaBuilder> {
 *         private String bootstrapServers, topic;
 *         protected KafkaBuilder self() { return this; }
 *         public KafkaBuilder bootstrapServers(String s) { bootstrapServers = s; return this; }
 *         public KafkaBuilder topic(String t) { topic = t; return this; }
 *         public KafkaConsumerConfig build() { return new KafkaConsumerConfig(this); }
 *     }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Base configuration providing shared TLS, retry, and timeout settings for all AEP connectors
 * @doc.layer infrastructure
 * @doc.pattern Template Method
 */
public abstract class ConnectorConfig {

    protected final TlsConfig tlsConfig;
    protected final RetryConfig retryConfig;
    protected final Duration connectionTimeout;
    protected final Duration readTimeout;

    protected ConnectorConfig(Builder<?> builder) {
        this.tlsConfig = builder.tlsConfig;
        this.retryConfig = builder.retryConfig;
        this.connectionTimeout = builder.connectionTimeout;
        this.readTimeout = builder.readTimeout;
    }

    public TlsConfig tlsConfig()        { return tlsConfig; }
    public RetryConfig retryConfig()    { return retryConfig; }
    public Duration connectionTimeout() { return connectionTimeout; }
    public Duration readTimeout()       { return readTimeout; }

    /** True if this connector configuration has TLS enabled. */
    public boolean isTlsEnabled() {
        return tlsConfig.enabled();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
               "{tls=" + tlsConfig.enabled() +
               ", retry=" + retryConfig.maxAttempts() + '}';
    }

    // ── Generic builder base ────────────────────────────────────────────────

    /**
     * Generic self-typed builder for connector configuration.
     *
     * @param <T> the concrete builder subtype (for fluent chaining)
     */
    public abstract static class Builder<T extends Builder<T>> {
        private TlsConfig tlsConfig = TlsConfig.DISABLED;
        private RetryConfig retryConfig = RetryConfig.DEFAULT;
        private Duration connectionTimeout = Duration.ofSeconds(30);
        private Duration readTimeout = Duration.ofSeconds(30);

        /** Returns the concrete builder for fluent chaining. */
        protected abstract T self();

        public T tlsConfig(TlsConfig tlsConfig) {
            this.tlsConfig = Objects.requireNonNull(tlsConfig);
            return self();
        }

        public T retryConfig(RetryConfig retryConfig) {
            this.retryConfig = Objects.requireNonNull(retryConfig);
            return self();
        }

        public T connectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = Objects.requireNonNull(connectionTimeout);
            return self();
        }

        public T readTimeout(Duration readTimeout) {
            this.readTimeout = Objects.requireNonNull(readTimeout);
            return self();
        }

        public abstract ConnectorConfig build();
    }
}
