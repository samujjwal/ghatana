/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.sqs;

/**
 * Configuration for SQS connectors.
 */
public class SqsConfig {
    private String queueUrl;
    private String region;
    private String accessKey;
    private String secretKey;
    private int maxMessages = 10;
    private int waitTimeSeconds = 20;
    
    public String getQueueUrl() {
        return queueUrl;
    }
    
    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public String getAccessKey() {
        return accessKey;
    }
    
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }
    
    public String getSecretKey() {
        return secretKey;
    }
    
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
    
    public int getMaxMessages() {
        return maxMessages;
    }
    
    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }
    
    public int getWaitTimeSeconds() {
        return waitTimeSeconds;
    }
    
    public void setWaitTimeSeconds(int waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }
}
