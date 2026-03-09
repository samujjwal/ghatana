package com.ghatana.aep.connector.strategy;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for queue producer implementations.
 * 
 * <p>Implementations provide queue-specific producer logic for different
 * message queue systems (Kafka, RabbitMQ, SQS).</p>
 * 
 * @doc.type interface
 * @doc.purpose Queue producer abstraction
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public interface QueueProducerStrategy {
    
    /**
     * Starts the producer (establishes connection).
     * 
     * @return Promise that completes when producer is ready
     */
    Promise<Void> start();
    
    /**
     * Sends a single message.
     * 
     * @param key The message key (for partitioning)
     * @param value The message payload
     * @return Promise containing the message ID
     */
    Promise<String> send(String key, String value);
    
    /**
     * Sends a message with headers.
     * 
     * @param key The message key
     * @param value The message payload
     * @param headers Additional message headers
     * @return Promise containing the message ID
     */
    Promise<String> send(String key, String value, Map<String, String> headers);
    
    /**
     * Sends a batch of messages.
     * 
     * @param messages List of messages
     * @return Promise containing list of message IDs
     */
    Promise<List<String>> sendBatch(List<QueueMessage> messages);
    
    /**
     * Flushes any buffered messages.
     * 
     * @return Promise that completes when flushed
     */
    Promise<Void> flush();
    
    /**
     * Stops the producer gracefully.
     * 
     * @return Promise that completes when stopped
     */
    Promise<Void> stop();
    
    /**
     * Returns the producer status.
     */
    ProducerStatus getStatus();
    
    /**
     * Producer status enum.
     */
    enum ProducerStatus {
        NOT_STARTED,
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
        ERROR
    }
}
