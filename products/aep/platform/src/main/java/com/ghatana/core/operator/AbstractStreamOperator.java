package com.ghatana.core.operator;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Abstract base class for stream operators implementing transformations and
 * filters.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides specialized base implementation for STREAM-type operators that
 * transform, filter, aggregate, or route event streams. AbstractStreamOperator
 * extends {@link AbstractOperator} with stream-specific capabilities while
 * maintaining the unified operator lifecycle and metrics collection.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * AbstractStreamOperator is the recommended base class for ALL stream
 * transformation operators (Filter, Map, FlatMap, Window, Join, Reduce, etc.).
 * It provides:
 * <ul>
 * <li><b>Stream Semantics</b>: Batch processing, stateful windowing
 * support</li>
 * <li><b>Transformation Patterns</b>: 1:1 (map), 1:N (flatMap), N:1 (reduce),
 * N:M (window)</li>
 * <li><b>Stateless/Stateful</b>: Support for both stateless (filter) and
 * stateful (window) operators</li>
 * <li><b>Chaining</b>: Compatible with OperatorChain for pipeline
 * composition</li>
 * <li><b>Performance</b>: Optimized batch processing with automatic
 * metrics</li>
 * </ul>
 *
 * <p>
 * <b>Stream Operator Types</b>
 * <ul>
 * <li><b>Filter</b>: 1:0-1 transformation (predicate-based event
 * filtering)</li>
 * <li><b>Map</b>: 1:1 transformation (event enrichment, field
 * transformation)</li>
 * <li><b>FlatMap</b>: 1:N transformation (event explosion,
 * denormalization)</li>
 * <li><b>Reduce</b>: N:1 aggregation (count, sum, avg, custom aggregation)</li>
 * <li><b>Window</b>: N:M aggregation (time/count windows,
 * tumbling/sliding)</li>
 * <li><b>Join</b>: 2:1 transformation (stream-stream join, enrichment)</li>
 * <li><b>Split</b>: 1:N routing (conditional routing, fan-out)</li>
 * </ul>
 *
 * <p>
 * <b>Extension Points</b>
 * <ul>
 * <li>{@link #process(Event)} - <b>REQUIRED</b>: Single event processing
 * logic</li>
 * <li>{@link #processBatch(List)} - Optional: Batch optimization (default
 * delegates to process())</li>
 * <li>{@link #doInitialize(OperatorConfig)} - Optional: Resource
 * allocation</li>
 * <li>{@link #doStop()} - Optional: Cleanup logic</li>
 * </ul>
 *
 * <p>
 * <b>Usage Examples</b>
 *
 * <p>
 * <b>Example 1: Simple stateless filter</b>
 * <pre>{@code
 * public class TypeFilterOperator extends AbstractStreamOperator {
 *     private final String targetType;
 *
 *     public TypeFilterOperator(OperatorId id, String targetType,
 *                               MetricsCollector metrics) {
 *         super(id, "Type Filter", "Filters events by type",
 *               List.of("event.filter", "event.type"), metrics);
 *         this.targetType = targetType;
 *     }
 *
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         if (event.getType().equals(targetType)) {
 *             return Promise.of(OperatorResult.of(event));  // Pass through
 *         } else {
 *             return Promise.of(OperatorResult.empty());     // Filter out
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Example 2: Stateless map transformation</b>
 * <pre>{@code
 * public class EnrichmentOperator extends AbstractStreamOperator {
 *     private final EnrichmentService enrichmentService;
 *
 *     public EnrichmentOperator(OperatorId id, EnrichmentService service,
 *                               MetricsCollector metrics) {
 *         super(id, "Enrichment", "Enriches events with external data",
 *               List.of("event.enrich", "event.transform"), metrics);
 *         this.enrichmentService = service;
 *     }
 *
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         return enrichmentService.enrich(event)
 *             .map(enriched -> OperatorResult.of(enriched));
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Example 3: Stateful window aggregation</b>
 * <pre>{@code
 * public class TumblingWindowOperator extends AbstractStreamOperator {
 *     private final Duration windowSize;
 *     private StateStore<String, List<Event>> windowState;
 *
 *     public TumblingWindowOperator(OperatorId id, Duration windowSize,
 *                                   MetricsCollector metrics) {
 *         super(id, "Tumbling Window", "Time-based tumbling window",
 *               List.of("event.window", "event.aggregate"), metrics);
 *         this.windowSize = windowSize;
 *     }
 *
 *     @Override
 *     protected Promise<Void> doInitialize(OperatorConfig config) {
 *         this.windowState = StateStoreFactory.createHybrid(config);
 *         return Promise.complete();
 *     }
 *
 *     @Override
 *     public Promise<OperatorResult> process(Event event) {
 *         String windowKey = calculateWindowKey(event);
 *
 *         return windowState.get(windowKey, List.class)
 *             .map(optional -> optional.orElse(new ArrayList<>()))
 *             .then(window -> {
 *                 window.add(event);
 *
 *                 if (isWindowComplete(window)) {
 *                     // Emit aggregated result and clear window
 *                     Event aggregated = aggregate(window);
 *                     return windowState.delete(windowKey)
 *                         .map(v -> OperatorResult.of(aggregated));
 *                 } else {
 *                     // Update window state
 *                     return windowState.put(windowKey, window,
 *                                          Optional.of(windowSize))
 *                         .map(v -> OperatorResult.empty());
 *                 }
 *             });
 *     }
 *
 *     @Override
 *     public boolean isStateful() {
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Example 4: Batch-optimized processing</b>
 * <pre>{@code
 * public class BatchEnrichmentOperator extends AbstractStreamOperator {
 *     private final EnrichmentService enrichmentService;
 *
 *     public BatchEnrichmentOperator(OperatorId id, EnrichmentService service,
 *                                    MetricsCollector metrics) {
 *         super(id, "Batch Enrichment", "Batch event enrichment",
 *               List.of("event.enrich", "event.batch"), metrics);
 *         this.enrichmentService = service;
 *     }
 *
 *     @Override
 *     public Promise<List<OperatorResult>> processBatch(List<Event> events) {
 *         // Batch optimization: single enrichment call for all events
 *         return enrichmentService.enrichBatch(events)
 *             .map(enrichedEvents -> enrichedEvents.stream()
 *                 .map(OperatorResult::of)
 *                 .toList());
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Best Practices</b>
 * <ul>
 * <li>Extend AbstractStreamOperator for all stream transformations</li>
 * <li>Use {@code isStateful() = false} for pure transformations (filter,
 * map)</li>
 * <li>Use {@code isStateful() = true} for windowing, aggregation, joins</li>
 * <li>Override {@code processBatch()} for batch optimization opportunities</li>
 * <li>Use StateStore for stateful operators (windowing, aggregation)</li>
 * <li>Return {@code OperatorResult.empty()} for filtering (0 output
 * events)</li>
 * <li>Return {@code OperatorResult.of(event)} for 1:1 transformations</li>
 * <li>Use {@code OperatorResult.batch(events)} for 1:N transformations</li>
 * </ul>
 *
 * <p>
 * <b>Anti-Patterns</b>
 * <ul>
 * <li>❌ DON'T use for pattern matching (use AbstractPatternOperator)</li>
 * <li>❌ DON'T use for ML inference (use AbstractLearningOperator)</li>
 * <li>❌ DON'T block in process() (use Promise.ofBlocking for IO)</li>
 * <li>❌ DON'T maintain state in fields without StateStore (lost on
 * restart)</li>
 * <li>❌ DON'T forget cleanup in doStop() (resource leaks)</li>
 * </ul>
 *
 * <p>
 * <b>Performance Characteristics</b>
 * <ul>
 * <li>Stateless operators: O(1) memory, <1ms p99 latency</li>
 *   <li>Statef
 * ul operators: O(window_size) memory, <10ms p99 latency</li> <li>Batch p
 * rocessing: Amortize overhead, 10-100x throughput improvement</li>
 * <li>GC pressure: Minimal (use object pooling for high-throughput)</li>
 * </ul>
 *
 * <p>
 * <b>Integration Points</b>
 * <ul>
 * <li>{@link AbstractOperator} - Extends for lifecycle and metrics</li>
 * <li>{@link OperatorType#STREAM} - Fixed operator type</li>
 * <li>{@link OperatorChain} - Chainable for pipeline composition</li>
 * <li>StateStore - For stateful operators (windowing, aggregation)</li>
 * <li>EventCloud - Consumes from and produces to EventCloud streams</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Inherits thread-safety guarantees from AbstractOperator. Stateful operators
 * MUST use thread-safe StateStore implementations. ActiveJ Eventloop affinity
 * ensures single-threaded execution per operator instance (recommended).
 *
 * @see AbstractOperator
 * @see AbstractPatternOperator
 * @see AbstractLearningOperator
 * @see UnifiedOperator
 * @see OperatorType#STREAM
 *
 * @doc.type class
 * @doc.purpose Abstract base class for stream transformation operators
 * @doc.layer core
 * @doc.pattern Template Method (process extension point)
 *
 * @since 2.0
 */
public abstract class AbstractStreamOperator extends AbstractOperator {

    /**
     * Creates a stream operator.
     *
     * @param id Operator identifier
     * @param name Operator name
     * @param description Operator description
     * @param capabilities Operator capabilities
     * @param metricsCollector Metrics collector
     */
    protected AbstractStreamOperator(
            OperatorId id,
            String name,
            String description,
            List<String> capabilities,
            MetricsCollector metricsCollector
    ) {
        super(
                Objects.requireNonNull(id, "Operator ID must not be null"),
                OperatorType.STREAM,
                Objects.requireNonNull(name, "Operator name must not be null"),
                Objects.requireNonNull(description, "Operator description must not be null"),
                capabilities != null ? capabilities : List.of(),
                metricsCollector
        );
    }

    /**
     * Process a single event (stream transformation logic).
     *
     * <p>
     * Subclasses MUST implement this method to define transformation behavior:
     * <ul>
     * <li><b>Filter</b>: Return empty() to filter out, of(event) to pass
     * through</li>
     * <li><b>Map</b>: Return of(transformedEvent) for 1:1 transformation</li>
     * <li><b>FlatMap</b>: Return batch(events) for 1:N transformation</li>
     * <li><b>Stateful</b>: Query/update StateStore, return result or
     * empty()</li>
     * </ul>
     *
     * @param event Input event
     * @return Promise of operator result (may contain 0, 1, or N events)
     */
    @Override
    public abstract Promise<OperatorResult> process(Event event);

    /**
     * Process a batch of events (batch optimization).
     *
     * <p>
     * Default implementation delegates to {@link #process(Event)} for each
     * event and returns aggregated result. Override this method for operators
     * that can optimize batch processing (e.g., batch database lookups, batch
     * API calls).
     *
     * <p>
     * <b>When to Override</b>
     * <ul>
     * <li>Batch enrichment with external services</li>
     * <li>Batch database queries</li>
     * <li>Vectorized transformations</li>
     * <li>Amortize connection/setup overhead</li>
     * </ul>
     *
     * @param events Batch of input events
     * @return Promise of operator result (batch result)
     */
    @Override
    public Promise<OperatorResult> processBatch(List<Event> events) {
        // Default: delegate to single-event process() and aggregate results
        return Promise.ofCallback(cb -> {
            List<Event> allResults = new java.util.ArrayList<>();

            Promise<Void> chain = Promise.complete();
            for (Event event : events) {
                chain = chain.then(() -> process(event)
                        .whenResult(result -> {
                            if (result.isSuccess()) {
                                allResults.addAll(result.getOutputEvents());
                            }
                        })
                        .toVoid()
                );
            }

            chain.whenComplete((v, ex) -> {
                if (ex == null) {
                    cb.set(OperatorResult.of(allResults));
                } else {
                    cb.setException(ex);
                }
            });
        });
    }

    /**
     * Returns false by default (stateless stream operators).
     *
     * <p>
     * Override to return true for stateful operators (windowing, aggregation,
     * joins).
     *
     * @return true if operator maintains state, false otherwise
     */
    @Override
    public boolean isStateful() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("StreamOperator[id=%s, name=%s, state=%s]",
                getId(), getName(), getState());
    }
}
