/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.kafka;

import com.ghatana.platform.messaging.config.ConnectorConfig;

import java.util.Objects;

/**
 * Immutable configuration for Kafka consumer connectors.
 *
 * <p>Extends {@link ConnectorConfig} for shared TLS, retry and timeout settings.
 * Protocol-specific fields (bootstrap servers, topic, group ID) are provided here.
 *
 * @doc.type class
 * @doc.purpose Immutable Kafka consumer connector configuration
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public final class KafkaConsumerConfig extends ConnectorConfig {

    private final String bootstrapServers;
    private final String topic;
    private final String groupId;
    private final int pollTimeoutMs;

    private KafkaConsumerConfig(KafkaConsumerBuilder b) {
        super(b);
        this.bootstrapServers = Objects.requireNonNull(b.bootstrapServers, "bootstrapServers");
        this.topic            = Objects.requireNonNull(b.topic, "topic");
        this.groupId          = Objects.requireNonNull(b.groupId, "groupId");
        this.pollTimeoutMs    = b.pollTimeoutMs;
    }

    public static KafkaConsumerBuilder builder() { return new KafkaConsumerBuilder(); }

    public String bootstrapServers() { return bootstrapServers; }
    public String topic()            { return topic; }
    public String groupId()          { return groupId; }
    public int pollTimeoutMs()       { return pollTimeoutMs; }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class KafkaConsumerBuilder extends Builder<KafkaConsumerBuilder> {
        private String bootstrapServers;
        private String topic;
        private String groupId;
        private int pollTimeoutMs = 1000;

        @Override protected KafkaConsumerBuilder self() { return this; }

        public KafkaConsumerBuilder bootstrapServers(String s) { bootstrapServers = s; return this; }
        public KafkaConsumerBuilder topic(String t)            { topic = t; return this; }
        public KafkaConsumerBuilder groupId(String g)          { groupId = g; return this; }
        public KafkaConsumerBuilder pollTimeoutMs(int ms)      { pollTimeoutMs = ms; return this; }

        @Override public KafkaConsumerConfig build() { return new KafkaConsumerConfig(this); }
    }
}
