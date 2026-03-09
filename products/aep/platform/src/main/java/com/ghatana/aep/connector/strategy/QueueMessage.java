package com.ghatana.aep.connector.strategy;

import java.util.Map;

/**
 * Represents a message received from a message queue.
 * 
 * @doc.type record
 * @doc.purpose Queue message data transfer object
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public record QueueMessage(
    String messageId,
    String payload,
    Map<String, String> metadata
) {
    /**
     * Returns true if the message has metadata.
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
    
    /**
     * Gets a metadata value by key.
     */
    public String getMetadataValue(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * Returns the payload size in bytes.
     */
    public int getPayloadSize() {
        return payload != null ? payload.length() : 0;
    }
}
