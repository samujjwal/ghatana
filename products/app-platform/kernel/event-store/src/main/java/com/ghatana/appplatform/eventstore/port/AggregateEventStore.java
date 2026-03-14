package com.ghatana.appplatform.eventstore.port;

import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.domain.ConflictError;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hexagonal port — aggregate-scoped event store operations.
 *
 * <p>Append events for a DDD aggregate and read back full or partial streams
 * for aggregate reconstruction or projection rebuilds. Adapters must guarantee:
 * <ul>
 *   <li>Append-only — no update or delete.</li>
 *   <li>Per-aggregate monotonic {@code sequenceNumber}.</li>
 *   <li>Optimistic concurrency: unique constraint on {@code (aggregateId, sequenceNumber)}.</li>
 * </ul>
 *
 * <p>All operations return ActiveJ {@link Promise} so they integrate naturally
 * with the non-blocking eventloop. Blocking JDBC calls must be wrapped in
 * {@link Promise#ofBlocking} by the adapter.
 *
 * @doc.type interface
 * @doc.purpose Hexagonal port for append-only DDD aggregate event store
 * @doc.layer product
 * @doc.pattern Port
 */
public interface AggregateEventStore {

    /**
     * Append a new event for an aggregate.
     *
     * <p>The adapter selects the next {@code sequenceNumber} for the aggregate
     * using the {@code UNIQUE (aggregate_id, sequence_number)} constraint as an
     * optimistic lock. On collision a {@link ConflictError} is returned inside
     * the promise.
     *
     * @param aggregateId   owning aggregate UUID
     * @param aggregateType aggregate type name (e.g. {@code "Order"})
     * @param eventType     event type name (e.g. {@code "OrderPlaced"})
     * @param data          event payload
     * @param metadata      envelope metadata (tenant_id, correlation_id, etc.)
     * @return promise of the persisted {@link AggregateEventRecord}
     */
    Promise<AggregateEventRecord> appendEvent(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            Map<String, Object> data,
            Map<String, Object> metadata);

    /**
     * Read the ordered event stream for one aggregate.
     *
     * @param aggregateId    target aggregate UUID
     * @param fromSequence   inclusive lower bound (0 = beginning)
     * @param toSequence     inclusive upper bound (null = no upper limit)
     * @return promise of events in ascending {@code sequenceNumber} order
     */
    Promise<List<AggregateEventRecord>> getEventsByAggregate(
            UUID aggregateId,
            long fromSequence,
            Long toSequence);
}
