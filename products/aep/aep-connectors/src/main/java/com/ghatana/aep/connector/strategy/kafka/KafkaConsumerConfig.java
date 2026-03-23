/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.kafka;

/**
 * Configuration for Kafka connectors.
 */
public class KafkaConsumerConfig {
    private String bootstrapServers;
    private String topic;
    private String groupId;
    private int pollTimeoutMs = 1000;
    
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
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public int getPollTimeoutMs() {
        return pollTimeoutMs;
    }
    
    public void setPollTimeoutMs(int pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }
}
