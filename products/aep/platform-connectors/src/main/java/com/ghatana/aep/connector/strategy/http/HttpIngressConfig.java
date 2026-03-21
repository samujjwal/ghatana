/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.http;

import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import io.activej.promise.Promise;

import java.util.function.Consumer;

/**
 * Configuration for HTTP ingress connectors.
 */
public class HttpIngressConfig {
    private String endpoint;
    private int port;
    private String path;
    private String method = "POST";
    private int timeoutMs = 30000;
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public int getTimeoutMs() {
        return timeoutMs;
    }
    
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
