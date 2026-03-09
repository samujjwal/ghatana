package com.ghatana.platform.workflow.operator;

import com.ghatana.platform.types.identity.OperatorId;

import com.ghatana.platform.domain.domain.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of processing an event through an operator in the Unified Operator Model.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates the complete outcome of operator processing: output events produced,
 * success/failure status, error details, and performance metrics. Supports complex
 * operator behaviors including filtering (0 outputs), transformation (1 output),
 * fan-out (N outputs), and failure handling.
 *
 * <p><b>Architecture Role</b><br>
 * Returned from {@link UnifiedOperator#process(Event)} and {@link UnifiedOperator#processBatch(List)}
 * as the canonical representation of processing outcomes. OperatorResult enables:
 * <ul>
 *   <li>Pipeline composition: Chain operators by feeding outputs to downstream operators</li>
 *   <li>Backpressure handling: Control flow based on output event count</li>
 *   <li>Error propagation: Capture and propagate failures through pipeline</li>
 *   <li>Performance tracking: Record processing time for latency monitoring</li>
 *   <li>Dead-letter queue: Route failed events for retry or inspection</li>
 *   <li>Batch optimization: Merge results from batch processing</li>
 * </ul>
 *
 * <p><b>Result Semantics</b>
 * <ul>
 *   <li><b>Success with 0 outputs</b>: Filter dropped event (no match)</li>
 *   <li><b>Success with 1 output</b>: Transform produced modified event</li>
 *   <li><b>Success with N outputs</b>: Fan-out (e.g., FlatMap, pattern match)</li>
 *   <li><b>Failure</b>: Processing error, errorMessage set, outputs ignored</li>
 * </ul>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: Filter operator (0 outputs)</b>
 * <pre>{@code
 * // Filter drops events that don't match predicate
 * Promise<OperatorResult> filterProcess(Event event) {
 *     if (predicate.test(event)) {
 *         // Pass through (1 output)
 *         return Promise.of(OperatorResult.of(event));
 *     } else {
 *         // Drop (0 outputs)
 *         return Promise.of(OperatorResult.empty());
 *     }
 * }
 * 
 * OperatorResult result = filterProcess(event).getResult();
 * assert result.isSuccess();
 * assert result.getOutputEvents().isEmpty(); // Filtered out
 * }</pre>
 *
 * <p><b>Example 2: Map operator (1 output)</b>
 * <pre>{@code
 * // Map transforms event and returns 1 output
 * Promise<OperatorResult> mapProcess(Event event) {
 *     Event transformed = Event.builder()
 *         .type(event.getType())
 *         .payload(transformFunction.apply(event.getPayload()))
 *         .build();
 *     return Promise.of(OperatorResult.of(transformed));
 * }
 * 
 * OperatorResult result = mapProcess(event).getResult();
 * assert result.isSuccess();
 * assert result.getOutputEvents().size() == 1;
 * Event output = result.getOutputEvents().get(0);
 * }</pre>
 *
 * <p><b>Example 3: FlatMap operator (N outputs)</b>
 * <pre>{@code
 * // FlatMap produces multiple output events from one input
 * Promise<OperatorResult> flatMapProcess(Event event) {
 *     List<Event> outputs = new ArrayList<>();
 *     for (String item : event.getPayload().getArray("items")) {
 *         outputs.add(Event.builder()
 *             .type("item.extracted")
 *             .addPayload("item", item)
 *             .build());
 *     }
 *     return Promise.of(OperatorResult.of(outputs));
 * }
 * 
 * OperatorResult result = flatMapProcess(event).getResult();
 * assert result.isSuccess();
 * assert result.getOutputEvents().size() == 3; // Fan-out
 * }</pre>
 *
 * <p><b>Example 4: Error handling</b>
 * <pre>{@code
 * // Operator fails due to invalid input
 * Promise<OperatorResult> process(Event event) {
 *     try {
 *         validateEvent(event);
 *         Event output = transformEvent(event);
 *         return Promise.of(OperatorResult.of(output));
 *     } catch (Exception e) {
 *         return Promise.of(OperatorResult.failed(
 *             "Validation failed: " + e.getMessage()
 *         ));
 *     }
 * }
 * 
 * OperatorResult result = process(invalidEvent).getResult();
 * assert !result.isSuccess();
 * assert result.getErrorMessage().contains("Validation failed");
 * assert result.getOutputEvents().isEmpty(); // No outputs on failure
 * }</pre>
 *
 * <p><b>Example 5: Performance tracking</b>
 * <pre>{@code
 * // Record processing time in result
 * Promise<OperatorResult> processWithTiming(Event event) {
 *     long startNanos = System.nanoTime();
 *     return process(event)
 *         .map(result -> OperatorResult.builder()
 *             .success()
 *             .addEvents(result.getOutputEvents())
 *             .processingTime(System.nanoTime() - startNanos)
 *             .build()
 *         );
 * }
 * 
 * OperatorResult result = processWithTiming(event).getResult();
 * long latencyMs = result.getProcessingTimeNanos() / 1_000_000;
 * System.out.println("Processing latency: " + latencyMs + "ms");
 * }</pre>
 *
 * <p><b>Example 6: Batch processing with mergeWith()</b>
 * <pre>{@code
 * // Process batch of events and merge results
 * Promise<OperatorResult> processBatch(List<Event> events) {
 *     List<Promise<OperatorResult>> promises = events.stream()
 *         .map(this::process)
 *         .toList();
 *     
 *     return Promises.toList(promises).map(results -> {
 *         OperatorResult.Builder builder = OperatorResult.builder().success();
 *         results.forEach(builder::mergeWith);
 *         return builder.build();
 *     });
 * }
 * 
 * OperatorResult batchResult = processBatch(events).getResult();
 * assert batchResult.getOutputEvents().size() == 50; // Merged outputs
 * long totalTime = batchResult.getProcessingTimeNanos(); // Cumulative time
 * }</pre>
 *
 * <p><b>Example 7: Dead-letter queue routing</b>
 * <pre>{@code
 * // Route failed events to dead-letter queue
 * OperatorResult result = operator.process(event).getResult();
 * 
 * if (!result.isSuccess()) {
 *     // Send to DLQ with error context
 *     deadLetterQueue.send(
 *         event,
 *         result.getErrorMessage(),
 *         operator.getId()
 *     );
 * } else {
 *     // Forward successful outputs to next operator
 *     result.getOutputEvents().forEach(nextOperator::process);
 * }
 * }</pre>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Use {@code OperatorResult.empty()} for filters that drop events</li>
 *   <li>Use {@code OperatorResult.of(event)} for 1:1 transformations</li>
 *   <li>Use {@code OperatorResult.of(List)} for fan-out operators</li>
 *   <li>Always set descriptive errorMessage on failure</li>
 *   <li>Track processingTime for latency monitoring (use Timer.record())</li>
 *   <li>Use builder().mergeWith() for batch result aggregation</li>
 *   <li>Check isSuccess() before accessing outputEvents</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T return null (use OperatorResult.empty() instead)</li>
 *   <li>❌ DON'T include output events in failed results (ignored anyway)</li>
 *   <li>❌ DON'T throw exceptions from process() (return failed result instead)</li>
 *   <li>❌ DON'T mutate result after build() (immutable)</li>
 *   <li>❌ DON'T ignore processingTime (critical for performance monitoring)</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li>Construction: O(1) for empty/single event, O(n) for n events (defensive copy)</li>
 *   <li>getOutputEvents(): O(1) cached unmodifiable list</li>
 *   <li>Builder.mergeWith(): O(n+m) for n+m total events</li>
 *   <li>Memory: ~50 bytes overhead + n * event size</li>
 *   <li>GC pressure: Minimal (immutable, no defensive copies on read)</li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link UnifiedOperator#process(Event)} - Returns OperatorResult</li>
 *   <li>{@link UnifiedOperator#processBatch(List)} - Returns merged OperatorResult</li>
 *   <li>PipelineBuilder - Chains operators by feeding result outputs to next operator</li>
 *   <li>DeadLetterQueue - Routes failed results for retry/inspection</li>
 *   <li>Metrics - Tracks operator.process.errors counter from failed results</li>
 *   <li>Observability - Logs error messages with traceId for debugging</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable and thread-safe after {@code build()}. All fields are final and defensive
 * copies are made during construction. Safe for concurrent access from multiple threads.
 *
 * <p><b>Comparison with Other Result Types</b>
 * <ul>
 *   <li>vs {@code Optional<Event>}: Cannot represent fan-out (N outputs) or errors</li>
 *   <li>vs {@code Either<Error, List<Event>>}: No performance metrics, less ergonomic</li>
 *   <li>vs {@code List<Event>}: Cannot represent failures or processing time</li>
 *   <li>vs {@code Promise<List<Event>>}: Promise wraps OperatorResult (not mutually exclusive)</li>
 * </ul>
 *
 * @see UnifiedOperator
 * @see OperatorException
 * @see Event
 * 
 * @doc.type class
 * @doc.purpose Result of processing an event through an operator (outputs, success, errors, metrics)
 * @doc.layer core
 * @doc.pattern Value Object
 * 
 * @author Ghatana Platform Team
 * @version 2.0.0
 * @since 2025-10-25
 */
public final class OperatorResult {
    
    private final List<Event> outputEvents;
    private final boolean success;
    private final String errorMessage;
    private final long processingTimeNanos;

    private OperatorResult(Builder builder) {
        this.outputEvents = List.copyOf(builder.outputEvents);
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.processingTimeNanos = builder.processingTimeNanos;
    }

    /**
     * Get output events produced by operator.
     * 
     * @return unmodifiable list of output events (may be empty)
     */
    public List<Event> getOutputEvents() {
        return outputEvents;
    }

    /**
     * Check if processing was successful.
     * 
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get error message (if processing failed).
     * 
     * @return error message or empty string if successful
     */
    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : "";
    }

    /**
     * Get processing time in nanoseconds.
     * 
     * @return processing time (nanos)
     */
    public long getProcessingTimeNanos() {
        return processingTimeNanos;
    }

    /**
     * Create successful result with no output events (filter dropped event).
     * 
     * @return empty result
     */
    public static OperatorResult empty() {
        return builder().success().build();
    }

    /**
     * Create successful result with single output event.
     * 
     * @param event output event
     * @return result with single event
     */
    public static OperatorResult of(Event event) {
        return builder().success().addEvent(event).build();
    }

    /**
     * Create successful result with multiple output events.
     * 
     * @param events output events
     * @return result with multiple events
     */
    public static OperatorResult of(List<Event> events) {
        Builder builder = builder().success();
        events.forEach(builder::addEvent);
        return builder.build();
    }

    /**
     * Create failed result with error message.
     * 
     * @param errorMessage error description
     * @return failed result
     */
    public static OperatorResult failed(String errorMessage) {
        return builder().failed(errorMessage).build();
    }

    /**
     * Create result builder.
     * 
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * OperatorResult Builder.
     */
    public static final class Builder {
        
        private final List<Event> outputEvents = new ArrayList<>();
        private boolean success = true;
        private String errorMessage;
        private long processingTimeNanos = 0;

        private Builder() {}

        /**
         * Mark result as successful.
         * 
         * @return this builder
         */
        public Builder success() {
            this.success = true;
            this.errorMessage = null;
            return this;
        }

        /**
         * Mark result as failed with error message.
         * 
         * @param errorMessage error description
         * @return this builder
         */
        public Builder failed(String errorMessage) {
            this.success = false;
            this.errorMessage = Objects.requireNonNull(errorMessage, "errorMessage cannot be null");
            return this;
        }

        /**
         * Add output event.
         * 
         * @param event output event
         * @return this builder
         */
        public Builder addEvent(Event event) {
            this.outputEvents.add(Objects.requireNonNull(event, "event cannot be null"));
            return this;
        }

        /**
         * Add multiple output events.
         * 
         * @param events output events
         * @return this builder
         */
        public Builder addEvents(List<Event> events) {
            Objects.requireNonNull(events, "events cannot be null");
            events.forEach(this::addEvent);
            return this;
        }

        /**
         * Set processing time.
         * 
         * @param processingTimeNanos processing time in nanoseconds
         * @return this builder
         * @throws IllegalArgumentException if processing time is negative
         */
        public Builder processingTime(long processingTimeNanos) {
            if (processingTimeNanos < 0) {
                throw new IllegalArgumentException(
                    "Processing time must be non-negative, got: " + processingTimeNanos
                );
            }
            this.processingTimeNanos = processingTimeNanos;
            return this;
        }

        /**
         * Merge with another result (for batch processing).
         * 
         * @param other other result to merge
         * @return this builder
         */
        public Builder mergeWith(OperatorResult other) {
            this.outputEvents.addAll(other.outputEvents);
            if (!other.success) {
                this.success = false;
                // Concatenate error messages
                if (this.errorMessage != null && other.errorMessage != null) {
                    this.errorMessage = this.errorMessage + "; " + other.errorMessage;
                } else if (other.errorMessage != null) {
                    this.errorMessage = other.errorMessage;
                }
            }
            this.processingTimeNanos += other.processingTimeNanos;
            return this;
        }

        /**
         * Build immutable OperatorResult.
         * 
         * @return operator result
         */
        public OperatorResult build() {
            return new OperatorResult(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "OperatorResult{success=%s, outputs=%d, time=%dns, error=%s}",
            success, outputEvents.size(), processingTimeNanos, errorMessage
        );
    }
}
