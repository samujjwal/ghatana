package com.ghatana.appplatform.eventstore.domain;

import java.util.UUID;

/**
 * Thrown when an append attempt violates the per-aggregate sequence uniqueness
 * constraint (i.e., optimistic concurrency conflict).
 *
 * <p>Callers should retry with the latest sequence number fetched from the store.
 *
 * @doc.type class
 * @doc.purpose Exception signalling an optimistic concurrency conflict in the event store
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class ConflictError extends RuntimeException {

    private final UUID aggregateId;
    private final long attemptedSequence;

    public ConflictError(UUID aggregateId, long attemptedSequence) {
        super(String.format(
            "Sequence conflict for aggregate %s at sequence %d — another writer already used this sequence.",
            aggregateId, attemptedSequence));
        this.aggregateId       = aggregateId;
        this.attemptedSequence = attemptedSequence;
    }

    public UUID aggregateId()       { return aggregateId; }
    public long attemptedSequence() { return attemptedSequence; }
}
