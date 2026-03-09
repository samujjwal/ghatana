package com.ghatana.aep.connector.strategy;

import io.activej.promise.Promise;
import java.util.List;

/**
 * Strategy interface for queue consumer implementations.
 * 
 * <p>Implementations provide queue-specific consumer logic for different
 * message queue systems (Kafka, RabbitMQ, SQS).</p>
 * 
 * @doc.type interface
 * @doc.purpose Queue consumer abstraction
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public interface QueueConsumerStrategy {
    
    /**
     * Starts the consumer (subscribes to queue/topic).
     * 
     * @return Promise that completes when consumer is ready
     */
    Promise<Void> start();
    
    /**
     * Polls for messages from the queue.
     * 
     * @return Promise containing list of messages
     */
    Promise<List<QueueMessage>> poll();
    
    /**
     * Acknowledges successful processing of a message.
     * 
     * @param messageId The ID of the message to acknowledge
     * @return Promise that completes when acknowledged
     */
    Promise<Void> acknowledge(String messageId);
    
    /**
     * Negative acknowledgement (requeue message for retry).
     * 
     * @param messageId The ID of the message to nack
     * @return Promise that completes when nacked
     */
    Promise<Void> nack(String messageId);
    
    /**
     * Stops the consumer gracefully.
     * 
     * @return Promise that completes when stopped
     */
    Promise<Void> stop();
    
    /**
     * Returns the consumer status.
     */
    ConsumerStatus getStatus();
    
    /**
     * Consumer status enum.
     */
    enum ConsumerStatus {
        NOT_STARTED,
        STARTING,
        RUNNING,
        PAUSED,
        STOPPING,
        STOPPED,
        ERROR
    }
}
