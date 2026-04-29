/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy;

import io.activej.promise.Promise;

import java.util.List;
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
     * Send multiple messages to the queue.
     *
     * <p>The default implementation preserves backward compatibility by sending
     * messages one by one through {@link #send(QueueMessage)}. Strategies with
     * native broker batching can override this for higher throughput.
     *
     * @param messages messages to send
     * @return true if every message was accepted successfully
     */
    default boolean sendBatch(List<QueueMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return true;
        }
        return messages.stream().allMatch(this::send);
    }

    /**
     * Send a keyed payload to the queue asynchronously.
     * @param key message routing key
     * @param payload message body
     * @return Promise containing the message id
     */
    default Promise<String> send(String key, String payload) {
        return send(key, payload, Map.of());
    }

    /**
     * Send a keyed payload with explicit headers to the queue asynchronously.
     * Use this overload to propagate correlation IDs and other trace headers.
     *
     * @param key     message routing key
     * @param payload message body
     * @param headers message headers (e.g. X-Correlation-ID)
     * @return Promise containing the message id
     */
    default Promise<String> send(String key, String payload, Map<String, String> headers) {
        QueueMessage msg = new QueueMessage(key, payload, headers);
        ConnectorSendResult result = sendWithResult(msg);
        if (result.isSuccess()) {
            return Promise.of(result.messageId());
        }
        return Promise.ofException(new ConnectorSendException(result.failure()));
    }

    /**
     * Send a message and return a typed success/failure contract.
     *
     * <p>This default implementation wraps the legacy boolean-returning
     * {@link #send(QueueMessage)} method and enriches failures with retry
     * classification so callers can make explicit retry decisions.
     *
     * @param message message to send
     * @return typed send result with message id or classified failure
     */
    default ConnectorSendResult sendWithResult(QueueMessage message) {
        try {
            boolean accepted = send(message);
            if (accepted) {
                return ConnectorSendResult.success(message.getId());
            }
            return ConnectorSendResult.failure(
                ConnectorSendFailure.retryable(
                    "Connector rejected message without exception"
                )
            );
        } catch (RuntimeException e) {
            return ConnectorSendResult.failure(
                ConnectorSendFailure.of(classifyFailure(e), e.getMessage(), e)
            );
        }
    }

    /**
     * Classify connector failure into retryability semantics.
     *
     * <p>Default rules:
     * <ul>
     *   <li>{@link IllegalArgumentException}, {@link IllegalStateException},
     *       {@link UnsupportedOperationException}, {@link SecurityException} are non-retryable</li>
     *   <li>All other runtime failures are retryable</li>
     * </ul>
     *
     * Implementations can override for connector-specific behavior.
     *
     * @param throwable failure to classify
     * @return retryability classification
     */
    default ConnectorFailureClassification classifyFailure(Throwable throwable) {
        if (throwable instanceof IllegalArgumentException
            || throwable instanceof IllegalStateException
            || throwable instanceof UnsupportedOperationException
            || throwable instanceof SecurityException) {
            return ConnectorFailureClassification.NON_RETRYABLE;
        }
        return ConnectorFailureClassification.RETRYABLE;
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
