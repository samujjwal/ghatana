/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy;

import io.activej.promise.Promise;

/**
 * Base interface for queue consumer strategies.
 * Simplified for platform-connectors module.
 */
public interface QueueConsumerStrategy {

    /**
     * Start consuming messages from the queue.
     * @return Promise of completion
     */
    Promise<Void> start();

    /**
     * Stop consuming messages.
     * @return Promise of completion
     */
    Promise<Void> stop();

    /**
     * Check if the consumer is running.
     * @return true if running
     */
    boolean isRunning();
}
