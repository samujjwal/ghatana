package com.ghatana.aep.connector.strategy.sqs;

import java.util.Objects;

/**
 * Configuration for AWS SQS consumer/producer strategy.
 * 
 * @doc.type class
 * @doc.purpose AWS SQS configuration
 * @doc.layer infrastructure
 * @doc.pattern Builder
 */
public class SqsConfig {
    
    private final String region;
    private final String queueName;
    private final String queueUrl;
    private final int batchSize;
    private final int waitTimeSeconds;
    private final int visibilityTimeout;
    
    private SqsConfig(Builder builder) {
        this.region = Objects.requireNonNull(builder.region, "region required");
        this.queueName = Objects.requireNonNull(builder.queueName, "queueName required");
        this.queueUrl = builder.queueUrl; // Can be null, will be derived
        this.batchSize = builder.batchSize > 0 ? Math.min(builder.batchSize, 10) : 10; // SQS max is 10
        this.waitTimeSeconds = builder.waitTimeSeconds >= 0 ? builder.waitTimeSeconds : 1; // 0-20 for long polling
        this.visibilityTimeout = builder.visibilityTimeout > 0 ? builder.visibilityTimeout : 30;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getRegion() { return region; }
    public String getQueueName() { return queueName; }
    
    public String getQueueUrl() { 
        // If explicit URL provided, use it; otherwise construct from name
        // In production, you'd typically get this from SQS API
        return queueUrl;
    }
    
    public int getBatchSize() { return batchSize; }
    public int getWaitTimeSeconds() { return waitTimeSeconds; }
    public int getVisibilityTimeout() { return visibilityTimeout; }
    
    public static class Builder {
        private String region;
        private String queueName;
        private String queueUrl;
        private int batchSize = 10;
        private int waitTimeSeconds = 1;
        private int visibilityTimeout = 30;
        
        public Builder region(String region) { this.region = region; return this; }
        public Builder queueName(String queueName) { this.queueName = queueName; return this; }
        public Builder queueUrl(String queueUrl) { this.queueUrl = queueUrl; return this; }
        public Builder batchSize(int batchSize) { this.batchSize = batchSize; return this; }
        public Builder waitTimeSeconds(int waitTimeSeconds) { this.waitTimeSeconds = waitTimeSeconds; return this; }
        public Builder visibilityTimeout(int visibilityTimeout) { this.visibilityTimeout = visibilityTimeout; return this; }
        
        public SqsConfig build() {
            return new SqsConfig(this);
        }
    }
}
