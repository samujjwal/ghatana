package com.ghatana.datacloud.spi;

import java.util.List;
import java.util.Map;

/**
 * Domain port for backpressure management (Hexagonal Architecture).
 *
 * <p>Adapters in the infrastructure layer must implement this port so that
 * application-layer code depends on the abstraction, not on a concrete class.
 *
 * <p>DC3-C2: Extracted from the monolithic {@code BackpressureManager} infrastructure
 * class to enforce the dependency-inversion principle. Only observable, type-safe
 * methods are part of this port; richer queue operations (enqueue/drain) remain on
 * the concrete {@code WatermarkBackpressureManager} until their inner types
 * ({@code FlowControl}, {@code EnqueueResult}) are promoted to the domain layer.
 *
 * @doc.type interface
 * @doc.purpose Domain port for ingest backpressure decisions
 * @doc.layer product
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface BackpressurePort {

    /**
     * Returns the number of items currently buffered in the ingest queue.
     *
     * @return queue depth (0 = empty)
     */
    int getQueueDepth();

    /**
     * Returns the queue utilization as a fraction between 0.0 and 1.0.
     *
     * @return utilization ratio; 1.0 means queue is full
     */
    double getQueueUtilization();

    /**
     * Returns an observable snapshot of backpressure statistics.
     *
     * <p>Keys include at minimum: {@code queueSize}, {@code maxQueueSize},
     * {@code utilization}, {@code totalProcessed}, {@code totalRejected},
     * {@code totalThrottled}.
     *
     * @return immutable statistics map
     */
    Map<String, Object> getStats();

    /**
     * Drains up to {@code maxItems} buffered items for downstream processing.
     *
     * @param maxItems maximum number of items to drain
     * @return list of drained items (may be empty)
     */
    List<Object> drain(int maxItems);
}
