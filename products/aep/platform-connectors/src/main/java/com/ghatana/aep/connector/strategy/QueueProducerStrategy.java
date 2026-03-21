/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Base interface for queue producer strategies.
 *
 * @doc.type interface
 * @doc.purpose Queue producer strategy for message publishing
 * @doc.layer core
 * @doc.pattern Strategy
 */
public interface QueueProducerStrategy {

    /**
     * Producer lifecycle status.
     */
    enum ProducerStatus {
        STARTING, RUNNING, STOPPED, ERROR
    }

    /**
     * Send a message to the queue.
     * @param message the message to send
     * @return true if sent successfully
     */
    boolean send(QueueMessage message);

    /**
     * Send a keyed payload to the queue asynchronously.
     * @param key message routing key
     * @param payload message body
     * @return Promise containing the message id
     */
    default Promise<String> send(String key, String payload) {
        QueueMessage msg = new QueueMessage(key, payload, Map.of());
        boolean result = send(msg);
        return Promise.of(result ? key : null);
    }

    /**
     * Start the producer.
     * @return Promise of completion
     */
    Promise<Void> start();

    /**
     * Stop the producer.
     * @return Promise of completion
     */
    Promise<Void> stop();

    /**
     * Flush any buffered messages.
     * @return Promise of completion
     */
    default Promise<Void> flush() {
        return Promise.complete();
    }

    /**
     * Check if the producer is running.
     * @return true if running
     */
    boolean isRunning();

    /**
     * Get the current producer status.
     * @return producer status
     */
    default ProducerStatus getStatus() {
        return isRunning() ? ProducerStatus.RUNNING : ProducerStatus.STOPPED;
    }
}
