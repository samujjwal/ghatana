/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.rabbitmq;

/**
 * Configuration for RabbitMQ connectors.
 */
public class RabbitMQConfig {
    private String host;
    private int port = 5672;
    private String username;
    private String password;
    private String virtualHost = "/";
    private String queueName;
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getVirtualHost() {
        return virtualHost;
    }
    
    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }
    
    public String getQueueName() {
        return queueName;
    }
    
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }
}
