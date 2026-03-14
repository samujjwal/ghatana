package com.ghatana.appplatform.eventstore.kafka;

import java.util.List;

/**
 * Port for tracking which events have been published to Kafka.
 *
 * <p>The cursor maintains a per-aggregate-type high-water mark so the relay
 * knows which events still need to be published. Adapters persist the cursor
 * to PostgreSQL (so the relay survives restarts) and use {@code SKIP LOCKED}
 * to allow concurrent relay instances without duplication.
 *
 * @doc.type interface
 * @doc.purpose Port for outbox relay cursor — tracks publish progress (K05-009)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface KafkaOutboxCursor {

    /**
     * Returns up to {@code limit} unpublished outbox candidates ordered by
     * {@code created_at_utc ASC} (oldest first).
     */
    List<KafkaEventOutboxRelay.OutboxCandidate> nextBatch(int limit);

    /** Marks a candidate as successfully published. */
    void markPublished(String cursorId);

    /** Increments the attempt counter and returns the new count. */
    int incrementAttempt(String cursorId);

    /** Marks a candidate as routed to the DLQ (terminal state). */
    void markDlqRouted(String cursorId);
}
