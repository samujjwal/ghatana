package com.ghatana.eventlog.ports;

import com.ghatana.contracts.event.v1.GetEventRequestProto;
import com.ghatana.contracts.event.v1.GetEventResponseProto;
import com.ghatana.contracts.event.v1.IngestBatchRequestProto;
import com.ghatana.contracts.event.v1.IngestBatchResponseProto;
import com.ghatana.contracts.event.v1.IngestRequestProto;
import com.ghatana.contracts.event.v1.IngestResponseProto;
import com.ghatana.contracts.event.v1.QueryEventsRequestProto;
import com.ghatana.contracts.event.v1.QueryEventsResponseProto;

/**
 * Port for event storage using Protocol Buffer contracts.
 * Implementations live in adapters.* packages.
 * 
 * <p>This interface defines the contract for storing and retrieving events
 * in the event log using Proto messages. Implementations must be thread-safe
 * and handle concurrent access appropriately.</p>
 * 
 * <p>For the core domain port, see {@link com.ghatana.eventcore.ports.EventStore}.</p>
 */
public interface EventStorePort {
    /**
     * Appends a single event to the store.
     *
     * @param request The ingest request containing the event to append
     * @return The response with the ingested event
     */
    IngestResponseProto append(IngestRequestProto request);
    
    /**
     * Appends a batch of events to the store.
     *
     * @param request The batch ingest request containing events to append
     * @return The response with the results of the batch operation
     */
    IngestBatchResponseProto appendBatch(IngestBatchRequestProto request);
    
    /**
     * Retrieves a single event by its ID.
     *
     * @param request The request containing the event ID to retrieve
     * @return The response containing the requested event
     */
    GetEventResponseProto get(GetEventRequestProto request);
    
    /**
     * Queries events based on the provided criteria.
     *
     * @param request The query request containing filter criteria
     * @return The response containing matching events
     */
    QueryEventsResponseProto query(QueryEventsRequestProto request);
}
