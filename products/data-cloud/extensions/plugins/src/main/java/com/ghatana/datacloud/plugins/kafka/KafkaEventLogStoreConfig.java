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
    /** DC3-L2: Regex for valid Kafka bootstrap.servers entries (host:port pairs). */
    private static final java.util.regex.Pattern BOOTSTRAP_SERVERS_PATTERN =
            java.util.regex.Pattern.compile(
                    "^([a-zA-Z0-9.-]+:\\d{1,5})(,[a-zA-Z0-9.-]+:\\d{1,5})*$");

    public KafkaEventLogStoreConfig {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("bootstrapServers must not be blank");
        }
        // DC3-L2: Validate format at config-load time, not at first produce/consume
        if (!BOOTSTRAP_SERVERS_PATTERN.matcher(bootstrapServers.trim()).matches()) {
            throw new IllegalArgumentException(
                "bootstrapServers has invalid format '" + bootstrapServers +
                "'; expected 'host:port' or 'host1:port1,host2:port2'");
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
