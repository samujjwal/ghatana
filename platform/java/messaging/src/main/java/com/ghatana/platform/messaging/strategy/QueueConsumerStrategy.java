/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy;

import io.activej.promise.Promise;

import java.util.function.Consumer;

/**
 * Base interface for queue consumer strategies.
 * Simplified for platform-connectors module.
  * @doc.type interface
 * @doc.purpose Provides queue consumer strategy functionality.
 * @doc.layer platform
 * @doc.pattern Interface
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
     * Register a raw message body handler for strategies that support callback-driven consumption.
     * Implementations that do not support this may keep the default no-op behavior.
     */
    default void setMessageHandler(Consumer<String> handler) {
        // no-op by default to preserve backward compatibility
    }

    /**
     * Check if the consumer is running.
     * @return true if running
     */
    boolean isRunning();
}
