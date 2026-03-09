package com.ghatana.aep.connector.strategy.kafka;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for Kafka consumer strategy.
 * 
 * @doc.type class
 * @doc.purpose Kafka consumer configuration
 * @doc.layer infrastructure
 * @doc.pattern Builder
 */
public class KafkaConsumerConfig {
    
    private final String bootstrapServers;
    private final String groupId;
    private final List<String> topics;
    private final int batchSize;
    private final int fetchMinBytes;
    private final int fetchMaxWaitMs;
    private final String autoOffsetReset;
    private final int sessionTimeoutMs;
    private final int pollTimeoutMs;
    private final Map<String, Object> customProperties;
    
    private KafkaConsumerConfig(Builder builder) {
        this.bootstrapServers = Objects.requireNonNull(builder.bootstrapServers, "bootstrapServers required");
        this.groupId = Objects.requireNonNull(builder.groupId, "groupId required");
        this.topics = Objects.requireNonNull(builder.topics, "topics required");
        this.batchSize = builder.batchSize > 0 ? builder.batchSize : 100;
        this.fetchMinBytes = builder.fetchMinBytes > 0 ? builder.fetchMinBytes : 1024;
        this.fetchMaxWaitMs = builder.fetchMaxWaitMs > 0 ? builder.fetchMaxWaitMs : 500;
        this.autoOffsetReset = builder.autoOffsetReset != null ? builder.autoOffsetReset : "latest";
        this.sessionTimeoutMs = builder.sessionTimeoutMs > 0 ? builder.sessionTimeoutMs : 30000;
        this.pollTimeoutMs = builder.pollTimeoutMs > 0 ? builder.pollTimeoutMs : 100;
        this.customProperties = builder.customProperties;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getBootstrapServers() { return bootstrapServers; }
    public String getGroupId() { return groupId; }
    public List<String> getTopics() { return topics; }
    public int getBatchSize() { return batchSize; }
    public int getFetchMinBytes() { return fetchMinBytes; }
    public int getFetchMaxWaitMs() { return fetchMaxWaitMs; }
    public String getAutoOffsetReset() { return autoOffsetReset; }
    public int getSessionTimeoutMs() { return sessionTimeoutMs; }
    public int getPollTimeoutMs() { return pollTimeoutMs; }
    public Map<String, Object> getCustomProperties() { return customProperties; }
    
    public static class Builder {
        private String bootstrapServers;
        private String groupId;
        private List<String> topics;
        private int batchSize = 100;
        private int fetchMinBytes = 1024;
        private int fetchMaxWaitMs = 500;
        private String autoOffsetReset = "latest";
        private int sessionTimeoutMs = 30000;
        private int pollTimeoutMs = 100;
        private Map<String, Object> customProperties;
        
        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }
        
        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }
        
        public Builder topics(List<String> topics) {
            this.topics = topics;
            return this;
        }
        
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }
        
        public Builder fetchMinBytes(int fetchMinBytes) {
            this.fetchMinBytes = fetchMinBytes;
            return this;
        }
        
        public Builder fetchMaxWaitMs(int fetchMaxWaitMs) {
            this.fetchMaxWaitMs = fetchMaxWaitMs;
            return this;
        }
        
        public Builder autoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
            return this;
        }
        
        public Builder sessionTimeoutMs(int sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
            return this;
        }
        
        public Builder pollTimeoutMs(int pollTimeoutMs) {
            this.pollTimeoutMs = pollTimeoutMs;
            return this;
        }
        
        public Builder customProperties(Map<String, Object> customProperties) {
            this.customProperties = customProperties;
            return this;
        }
        
        public KafkaConsumerConfig build() {
            return new KafkaConsumerConfig(this);
        }
    }
}
