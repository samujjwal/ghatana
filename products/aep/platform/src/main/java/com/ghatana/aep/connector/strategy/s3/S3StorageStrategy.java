package com.ghatana.aep.connector.strategy.s3;

import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.promise.Promise;

import java.util.List;
import java.util.function.Consumer;

/**
 * Strategy interface for S3-based event storage and retrieval.
 * 
 * @doc.type interface
 * @doc.purpose S3 storage strategy
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public interface S3StorageStrategy {
    
    /**
     * Start the S3 strategy (initialize client, start polling if applicable).
     */
    Promise<Void> start();
    
    /**
     * Stop the S3 strategy.
     */
    Promise<Void> stop();
    
    /**
     * Write a message to S3.
     */
    Promise<WriteResult> write(QueueMessage message);
    
    /**
     * Write a batch of messages to S3.
     */
    Promise<List<WriteResult>> writeBatch(List<QueueMessage> messages);
    
    /**
     * Read messages from S3 (one-time poll).
     */
    Promise<List<QueueMessage>> read(int maxMessages);
    
    /**
     * Register a handler for continuous polling mode.
     */
    void onMessage(Consumer<QueueMessage> handler);
    
    /**
     * Start continuous polling for messages.
     */
    Promise<Void> startPolling();
    
    /**
     * Stop continuous polling.
     */
    Promise<Void> stopPolling();
    
    /**
     * Delete a message/object from S3.
     */
    Promise<Void> delete(String key);
    
    /**
     * Get current status.
     */
    StorageStatus getStatus();
    
    /**
     * Storage status.
     */
    enum StorageStatus {
        CREATED,
        STARTING,
        RUNNING,
        POLLING,
        STOPPING,
        STOPPED,
        ERROR
    }
    
    /**
     * Result of a write operation.
     */
    record WriteResult(
        String messageId,
        String s3Key,
        boolean success,
        String errorMessage
    ) {}
}
