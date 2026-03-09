package com.ghatana.aep.integration.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory implementation of EventLogAdapter for testing.
 * 
 * <p>This implementation stores all events in memory and provides
 * full query capabilities without requiring external infrastructure.</p>
 * 
 * @doc.type class
 * @doc.purpose In-memory EventLog for testing
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class InMemoryEventLogAdapter implements EventLogAdapter {
    
    private final Map<String, EventRecord> events;
    private final List<String> eventOrder;
    private final AtomicLong sequence;
    
    public InMemoryEventLogAdapter() {
        this.events = new ConcurrentHashMap<>();
        this.eventOrder = new ArrayList<>();
        this.sequence = new AtomicLong(0);
    }
    
    @Override
    public synchronized String append(EventRecord record) {
        // Ensure we have a valid event ID with ordering
        String eventId = record.eventId();
        if (eventId == null) {
            eventId = String.format("event-%06d", sequence.incrementAndGet());
        }
        
        // Create new record with the event ID
        EventRecord stored = new EventRecord(
            eventId,
            record.tenantId(),
            record.eventType(),
            record.aggregateId(),
            record.payload(),
            record.eventClassName(),
            record.timestamp() != null ? record.timestamp() : Instant.now()
        );
        
        events.put(eventId, stored);
        eventOrder.add(eventId);
        
        return eventId;
    }
    
    @Override
    public synchronized List<EventRecord> query(String[] eventTypes, String afterEventId, int limit) {
        List<String> typeList = Arrays.asList(eventTypes);
        
        // Find starting position
        int startIndex = 0;
        if (afterEventId != null) {
            startIndex = eventOrder.indexOf(afterEventId);
            if (startIndex >= 0) {
                startIndex++; // Start after the specified event
            } else {
                startIndex = 0; // Event not found, start from beginning
            }
        }
        
        // Collect matching events
        return eventOrder.stream()
            .skip(startIndex)
            .map(events::get)
            .filter(record -> record != null && typeList.contains(record.eventType()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Returns all events (for testing).
     */
    public List<EventRecord> getAllEvents() {
        return eventOrder.stream()
            .map(events::get)
            .collect(Collectors.toList());
    }
    
    /**
     * Returns all events of a specific type (for testing).
     */
    public List<EventRecord> getEventsByType(String eventType) {
        return events.values().stream()
            .filter(e -> e.eventType().equals(eventType))
            .collect(Collectors.toList());
    }
    
    /**
     * Returns the count of events (for testing).
     */
    public int getEventCount() {
        return events.size();
    }
    
    /**
     * Clears all events (for testing).
     */
    public void clear() {
        events.clear();
        eventOrder.clear();
        sequence.set(0);
    }
}
