/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.http;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import io.activej.promise.Promise;

/**
 * HTTP polling ingress strategy for consuming data via HTTP endpoints.
 */
public class HttpPollingIngressStrategy implements QueueConsumerStrategy {
    
    private final HttpIngressConfig config;
    private volatile boolean running = false;
    
    public HttpPollingIngressStrategy(HttpIngressConfig config) {
        this.config = config;
    }
    
    @Override
    public Promise<Void> start() {
        running = true;
        return Promise.complete();
    }
    
    @Override
    public Promise<Void> stop() {
        running = false;
        return Promise.complete();
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
}
