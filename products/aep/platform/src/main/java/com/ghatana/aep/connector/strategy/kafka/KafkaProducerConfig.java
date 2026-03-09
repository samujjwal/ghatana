package com.ghatana.aep.connector.strategy.kafka;

import java.util.Map;
import java.util.Objects;

/**
 * Configuration for Kafka producer strategy.
 * 
 * @doc.type class
 * @doc.purpose Kafka producer configuration
 * @doc.layer infrastructure
 * @doc.pattern Builder
 */
public class KafkaProducerConfig {
    
    private final String bootstrapServers;
    private final String topic;
    private final String acks;
    private final int retries;
    private final int batchSize;
    private final int lingerMs;
    private final String compressionType;
    private final int maxInFlightRequests;
    private final boolean enableIdempotence;
    private final Map<String, Object> customProperties;
    
    private KafkaProducerConfig(Builder builder) {
        this.bootstrapServers = Objects.requireNonNull(builder.bootstrapServers, "bootstrapServers required");
        this.topic = Objects.requireNonNull(builder.topic, "topic required");
        this.acks = builder.acks != null ? builder.acks : "all";
        this.retries = builder.retries > 0 ? builder.retries : 3;
        this.batchSize = builder.batchSize > 0 ? builder.batchSize : 16384;
        this.lingerMs = builder.lingerMs > 0 ? builder.lingerMs : 10;
        this.compressionType = builder.compressionType != null ? builder.compressionType : "snappy";
        this.maxInFlightRequests = builder.maxInFlightRequests > 0 ? builder.maxInFlightRequests : 5;
        this.enableIdempotence = builder.enableIdempotence;
        this.customProperties = builder.customProperties;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getBootstrapServers() { return bootstrapServers; }
    public String getTopic() { return topic; }
    public String getAcks() { return acks; }
    public int getRetries() { return retries; }
    public int getBatchSize() { return batchSize; }
    public int getLingerMs() { return lingerMs; }
    public String getCompressionType() { return compressionType; }
    public int getMaxInFlightRequests() { return maxInFlightRequests; }
    public boolean isEnableIdempotence() { return enableIdempotence; }
    public Map<String, Object> getCustomProperties() { return customProperties; }
    
    public static class Builder {
        private String bootstrapServers;
        private String topic;
        private String acks = "all";
        private int retries = 3;
        private int batchSize = 16384;
        private int lingerMs = 10;
        private String compressionType = "snappy";
        private int maxInFlightRequests = 5;
        private boolean enableIdempotence = false;
        private Map<String, Object> customProperties;
        
        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }
        
        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }
        
        public Builder acks(String acks) {
            this.acks = acks;
            return this;
        }
        
        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }
        
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }
        
        public Builder lingerMs(int lingerMs) {
            this.lingerMs = lingerMs;
            return this;
        }
        
        public Builder compressionType(String compressionType) {
            this.compressionType = compressionType;
            return this;
        }
        
        public Builder maxInFlightRequests(int maxInFlightRequests) {
            this.maxInFlightRequests = maxInFlightRequests;
            return this;
        }
        
        public Builder enableIdempotence(boolean enableIdempotence) {
            this.enableIdempotence = enableIdempotence;
            return this;
        }
        
        public Builder customProperties(Map<String, Object> customProperties) {
            this.customProperties = customProperties;
            return this;
        }
        
        public KafkaProducerConfig build() {
            return new KafkaProducerConfig(this);
        }
    }
}
