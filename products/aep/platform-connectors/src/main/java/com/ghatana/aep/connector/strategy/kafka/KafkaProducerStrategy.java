/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.kafka;

import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;

/**
 * Kafka producer strategy implementation.
 */
public class KafkaProducerStrategy implements QueueProducerStrategy {
    
    private final KafkaProducerConfig config;
    private volatile boolean running = false;
    
    public KafkaProducerStrategy(KafkaProducerConfig config) {
        this.config = config;
    }
    
    @Override
    public boolean send(QueueMessage message) {
        // Kafka producer logic would be implemented here
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
