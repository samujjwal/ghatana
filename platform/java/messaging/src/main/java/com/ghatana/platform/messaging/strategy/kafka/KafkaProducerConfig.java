/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.kafka;

import com.ghatana.aep.connector.config.ConnectorConfig;

import java.util.Objects;

/**
 * Immutable configuration for Kafka producer connectors.
 *
 * <p>Extends {@link ConnectorConfig} for shared TLS, retry and timeout settings.
 * Note: {@link #retryConfig()} controls connector-level retry; Kafka's own producer
 * retries ({@code batchSize}) remain as Kafka-specific fields.
 *
 * @doc.type class
 * @doc.purpose Immutable Kafka producer connector configuration
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public final class KafkaProducerConfig extends ConnectorConfig {

    private final String bootstrapServers;
    private final String topic;
    private final int batchSize;

    private KafkaProducerConfig(KafkaProducerBuilder b) {
        super(b);
        this.bootstrapServers = Objects.requireNonNull(b.bootstrapServers, "bootstrapServers");
        this.topic            = Objects.requireNonNull(b.topic, "topic");
        this.batchSize        = b.batchSize;
    }

    public static KafkaProducerBuilder builder() { return new KafkaProducerBuilder(); }

    public String bootstrapServers() { return bootstrapServers; }
    public String topic()            { return topic; }
    public int batchSize()           { return batchSize; }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class KafkaProducerBuilder extends Builder<KafkaProducerBuilder> {
        private String bootstrapServers;
        private String topic;
        private int batchSize = 16384;

        @Override protected KafkaProducerBuilder self() { return this; }

        public KafkaProducerBuilder bootstrapServers(String s) { bootstrapServers = s; return this; }
        public KafkaProducerBuilder topic(String t)            { topic = t; return this; }
        public KafkaProducerBuilder batchSize(int n)           { batchSize = n; return this; }

        @Override public KafkaProducerConfig build() { return new KafkaProducerConfig(this); }
    }
}
