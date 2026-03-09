package com.ghatana.eventlog.adapters;

import com.ghatana.contracts.event.v1.QueryEventsRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsResponseProto;
import com.ghatana.platform.domain.domain.event.Event;

import java.time.Instant;
import java.util.List;

/**
 * Repository interface for event log storage operations.
 * Implementations should provide thread-safe access to the underlying storage.
 */
public interface EventLogRepository {
    
    /**
     * Queries events based on the provided criteria.
     *
     * @param request The query request containing filter criteria
     * @return The response containing matching events
     */
    QueryEventsResponseProto query(QueryEventsRequestProto request);
    
    /**
     * Retrieves events that were created before the specified cutoff time.
     *
     * @param cutoff The cutoff time (exclusive)
     * @return List of events created before the cutoff time
     */
    List<Event> getEventsBefore(Instant cutoff);
    
    /**
     * Purges all events that were created before the specified cutoff time.
     *
     * @param cutoff The cutoff time (exclusive)
     */
    void purgeEventsBefore(Instant cutoff);
}
