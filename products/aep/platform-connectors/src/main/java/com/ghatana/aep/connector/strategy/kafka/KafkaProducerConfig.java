/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.kafka;

/**
 * Configuration for Kafka producer connectors.
 */
public class KafkaProducerConfig {
    private String bootstrapServers;
    private String topic;
    private int retries = 3;
    private int batchSize = 16384;
    
    public String getBootstrapServers() {
        return bootstrapServers;
    }
    
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }
    
    public String getTopic() {
        return topic;
    }
    
    public void setTopic(String topic) {
        this.topic = topic;
    }
    
    public int getRetries() {
        return retries;
    }
    
    public void setRetries(int retries) {
        this.retries = retries;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
