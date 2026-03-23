/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.http;

import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;

/**
 * HTTP webhook egress strategy for sending data via HTTP webhooks.
 */
public class HttpWebhookEgressStrategy implements QueueProducerStrategy {
    
    private final HttpIngressConfig config;
    private volatile boolean running = false;
    
    public HttpWebhookEgressStrategy(HttpIngressConfig config) {
        this.config = config;
    }
    
    @Override
    public boolean send(QueueMessage message) {
        // Webhook sending logic would be implemented here
        return true;
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
