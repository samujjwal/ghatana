/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.kafka;

import com.ghatana.aep.connector.strategy.QueueConsumerStrategy;
import io.activej.promise.Promise;

/**
 * Kafka consumer strategy implementation.
 */
public class KafkaConsumerStrategy implements QueueConsumerStrategy {
    
    private final KafkaConsumerConfig config;
    private volatile boolean running = false;
    
    public KafkaConsumerStrategy(KafkaConsumerConfig config) {
        this.config = config;
    }
    
    @Override
    public Promise<Void> start() {
        running = true;
        // Kafka consumer logic would be implemented here
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
