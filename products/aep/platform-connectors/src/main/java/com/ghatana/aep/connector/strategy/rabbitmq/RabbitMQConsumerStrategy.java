/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.rabbitmq;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import io.activej.promise.Promise;

/**
 * RabbitMQ consumer strategy implementation.
 */
public class RabbitMQConsumerStrategy implements QueueConsumerStrategy {
    
    private final RabbitMQConfig config;
    private volatile boolean running = false;
    
    public RabbitMQConsumerStrategy(RabbitMQConfig config) {
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
