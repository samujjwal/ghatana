package com.ghatana.appplatform.eventstore.replay;

import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;

/**
 * Contract for event-sourced projections that support full or partial rebuilds.
 *
 * <p>A projection implementing this interface may be registered with
 * {@link ProjectionRebuildEngine} so that callers can trigger replay-based rebuilds
 * without knowing the projection's internal storage details.
 *
 * @doc.type interface
 * @doc.purpose Projection rebuild contract for event-sourced read models (STORY-K05-022)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface RebuildableProjection {

    /**
     * Returns a stable, unique name for this projection (e.g. {@code "order-summary"}).
     * Used as the rebuild progress tracking key.
     */
    String projectionName();

    /**
     * Clears all state in this projection so the engine can replay from scratch.
     * Must be idempotent and complete before the replay starts.
     */
    void clear();

    /**
     * Handles a single replayed event. Called in sequence-ascending order during rebuild.
     * Must be idempotent — the same event may be delivered more than once if the build
     * is interrupted and resumed.
     *
     * @param event replayed aggregate event
     */
    void applyEvent(AggregateEventRecord event);
}
