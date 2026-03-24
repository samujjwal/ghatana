package com.ghatana.yappc.storage;

import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory event publisher implementing {@link AepEventPublisher} for dev/testing.
 *
 * <p>Production deployments should use {@code HttpAepEventPublisher} instead.
 *
 * @doc.type class
 * @doc.purpose In-memory event publisher implementation for development/testing
 * @doc.layer infrastructure
 * @doc.pattern Publisher
 */
public class InMemoryEventPublisher implements AepEventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);
    
    private final Map<String, List<Map<String, Object>>> events = new ConcurrentHashMap<>();
    
    /**
     * Publishes an event to AEP (in-memory for dev/test).
     *
     * @param eventType Event type
     * @param tenantId  Tenant identifier
     * @param payload   Event payload
     * @return Promise of completion
     */
    @Override
    public Promise<Void> publish(String eventType, String tenantId, Map<String, Object> payload) {
        events.computeIfAbsent(eventType, k -> new ArrayList<>()).add(Map.copyOf(payload));
        
        log.info("Published event: {} tenant={} (total: {})", eventType, tenantId,
                events.get(eventType).size());
        log.debug("Event data: {}", payload);
        
        return Promise.complete();
    }
    
    /**
     * Gets all events of a specific type.
     * 
     * @param eventType Event type
     * @return List of events
     */
    public List<Map<String, Object>> getEvents(String eventType) {
        return events.getOrDefault(eventType, List.of());
    }
    
    /**
     * Gets all events.
     * 
     * @return Map of event type to events
     */
    public Map<String, List<Map<String, Object>>> getAllEvents() {
        return Map.copyOf(events);
    }
    
    /**
     * Clears all events (for testing).
     */
    public void clear() {
        events.clear();
        log.info("Cleared all events");
    }
    
    /**
     * Gets the total number of published events.
     * 
     * @return Event count
     */
    public int size() {
        return events.values().stream().mapToInt(List::size).sum();
    }
}
