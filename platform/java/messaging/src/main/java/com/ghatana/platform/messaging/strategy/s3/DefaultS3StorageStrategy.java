/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.s3;

import com.ghatana.platform.messaging.strategy.QueueMessage;
import com.ghatana.platform.messaging.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;

/**
 * S3 storage strategy implementation for egress to S3.
 */
public class DefaultS3StorageStrategy implements QueueProducerStrategy {

    private final S3Config config;
    private volatile boolean running = false;

    public DefaultS3StorageStrategy(S3Config config) {
        this.config = config;
    }

    @Override
    public boolean send(QueueMessage message) {
        // S3 upload logic would be implemented here
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
