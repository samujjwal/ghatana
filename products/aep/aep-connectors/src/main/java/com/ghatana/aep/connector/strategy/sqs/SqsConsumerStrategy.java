/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.sqs;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import io.activej.promise.Promise;

/**
 * SQS consumer strategy implementation.
 */
public class SqsConsumerStrategy implements QueueConsumerStrategy {
    
    private final SqsConfig config;
    private volatile boolean running = false;
    
    public SqsConsumerStrategy(SqsConfig config) {
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
