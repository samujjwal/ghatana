/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins.kafka;

import java.util.Map;

/**
 * Configuration record for {@link KafkaEventLogStore}.
 *
 * @doc.type record
 * @doc.purpose Immutable configuration for the Kafka-backed EventLogStore
 * @doc.layer product
 * @doc.pattern ValueObject, Configuration
 */
public record KafkaEventLogStoreConfig(
        String bootstrapServers,
        int partitions,
        short replicationFactor,
        long readTimeoutMs,
        Map<String, Object> additionalProducerProps,
        Map<String, Object> additionalConsumerProps
) {
    public KafkaEventLogStoreConfig {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("bootstrapServers must not be blank");
        }
        if (partitions < 1) throw new IllegalArgumentException("partitions must be >= 1");
        if (replicationFactor < 1) throw new IllegalArgumentException("replicationFactor must be >= 1");
        if (readTimeoutMs < 0) throw new IllegalArgumentException("readTimeoutMs must be >= 0");
        additionalProducerProps = additionalProducerProps != null ? Map.copyOf(additionalProducerProps) : Map.of();
        additionalConsumerProps = additionalConsumerProps != null ? Map.copyOf(additionalConsumerProps) : Map.of();
    }

    /** Default configuration suitable for local/dev environments. */
    public static KafkaEventLogStoreConfig defaults() {
        return new KafkaEventLogStoreConfig(
                "localhost:9092",
                1,
                (short) 1,
                5_000L,
                Map.of(),
                Map.of()
        );
    }

    /** Fluent builder. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String bootstrapServers = "localhost:9092";
        private int partitions = 1;
        private short replicationFactor = 1;
        private long readTimeoutMs = 5_000L;
        private Map<String, Object> additionalProducerProps = Map.of();
        private Map<String, Object> additionalConsumerProps = Map.of();

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder partitions(int partitions) {
            this.partitions = partitions;
            return this;
        }

        public Builder replicationFactor(short replicationFactor) {
            this.replicationFactor = replicationFactor;
            return this;
        }

        public Builder readTimeoutMs(long readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        public Builder additionalProducerProps(Map<String, Object> props) {
            this.additionalProducerProps = props;
            return this;
        }

        public Builder additionalConsumerProps(Map<String, Object> props) {
            this.additionalConsumerProps = props;
            return this;
        }

        public KafkaEventLogStoreConfig build() {
            return new KafkaEventLogStoreConfig(
                    bootstrapServers, partitions, replicationFactor,
                    readTimeoutMs, additionalProducerProps, additionalConsumerProps);
        }
    }
}
