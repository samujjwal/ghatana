package com.ghatana.aep.integration.events;

import java.time.Instant;
import java.util.List;

/**
 * Adapter interface for EventLog operations.
 * 
 * <p>This abstraction allows the EventBusClient to work with different
 * EventLog implementations (real, in-memory for testing, etc.)</p>
 * 
 * @doc.type interface
 * @doc.purpose EventLog adapter abstraction
 * @doc.layer infrastructure
 * @doc.pattern Port
 */
public interface EventLogAdapter {
    
    /**
     * Appends an event record to the event log.
     * 
     * @param record The event record to append
     * @return The event ID assigned by the store
     */
    String append(EventRecord record);
    
    /**
     * Queries events by type, starting after the given cursor.
     * 
     * @param eventTypes The event types to query
     * @param afterEventId The event ID to start after (null for beginning)
     * @param limit Maximum number of events to return
     * @return List of matching event records
     */
    List<EventRecord> query(String[] eventTypes, String afterEventId, int limit);
    
    /**
     * Event record for storage and retrieval.
     */
    record EventRecord(
        String eventId,
        String tenantId,
        String eventType,
        String aggregateId,
        String payload,
        String eventClassName,
        Instant timestamp
    ) {}
}
