/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.sqs;

import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;

/**
 * SQS producer strategy implementation.
 */
public class SqsProducerStrategy implements QueueProducerStrategy {
    
    private final SqsConfig config;
    private volatile boolean running = false;
    
    public SqsProducerStrategy(SqsConfig config) {
        this.config = config;
    }
    
    @Override
    public boolean send(QueueMessage message) {
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
