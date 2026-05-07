package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.resilience.DeadLetterQueue;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Operator that routes permanently failed events to a Dead Letter Queue (DLQ).
 *
 * <p><b>Purpose</b><br>
 * Wraps a delegate operator and, when that operator fails, stores the failed event
 * in a {@link DeadLetterQueue} instead of silently dropping it. This ensures that
 * no business-critical event is permanently lost after exhausting all retry attempts.
 *
 * <p><b>Architecture Role</b><br>
 * Sits at the end of a resilience chain, typically after a {@link RetryOperator}:
 * <pre>
 *   [Event] → [RetryOperator] → [DeadLetterQueueOperator] → [downstream]
 *                                      ↓ (on failure)
 *                              [DeadLetterQueue storage]
 * </pre>
 * On success the delegate result passes through transparently. On failure the event
 * is persisted in the DLQ for later inspection, replay, or manual resolution, and
 * an empty {@link OperatorResult} is returned to halt downstream processing.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DeadLetterQueue dlq = DeadLetterQueue.builder()
 *     .maxSize(10_000)
 *     .ttl(Duration.ofDays(7))
 *     .enableReplay(true)
 *     .build();
 *
 * DeadLetterQueueOperator dlqOp = DeadLetterQueueOperator.builder()
 *     .operator(myOperator)
 *     .deadLetterQueue(dlq)
 *     .build();
 *
 * // Failed events are automatically stored in DLQ, never silently dropped
 * dlqOp.process(event);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. The delegate and DLQ must themselves be thread-safe (both are).
 *
 * @see RetryOperator
 * @see DeadLetterQueue
 * @doc.type class
 * @doc.purpose Routes permanently failed events to a dead letter queue for recovery
 * @doc.layer core
 * @doc.pattern Decorator
 */
public final class DeadLetterQueueOperator extends AbstractOperator {

    private static final Logger logger = LoggerFactory.getLogger(DeadLetterQueueOperator.class);

    /** Metric name for events routed to the DLQ. */
    public static final String METRIC_DLQ_STORED = "aep.dlq.events.stored";
    /** Metric name for the current number of events in the DLQ (gauge). */
    public static final String METRIC_DLQ_PENDING = "aep.dlq.events.pending";
    /** Tag key for the error type dimension. */
    public static final String TAG_ERROR_TYPE = "error_type";

    private final UnifiedOperator delegate;
    private final DeadLetterQueue deadLetterQueue;
    /** Counter for DLQ store events; null when no registry is provided. */
    private final Counter dlqStoredCounter;

    private DeadLetterQueueOperator(Builder builder) {
        super(
            OperatorId.of("ghatana", "error-handling", "dead-letter-queue", "1.0.0"),
            OperatorType.STREAM,
            "Dead Letter Queue Operator",
            "Routes permanently failed events to a dead letter queue",
            List.of("dlq", "resilience", "error-handling", "recovery"),
            null
        );
        this.delegate = Objects.requireNonNull(builder.operator, "operator required");
        this.deadLetterQueue = Objects.requireNonNull(builder.deadLetterQueue, "deadLetterQueue required");
        // Register a static counter; per-error-type dimensions are handled at call-site
        // via the global registry so that we can emit tagged increments without
        // holding a separate Counter instance per error type.
        if (builder.meterRegistry != null) {
            this.dlqStoredCounter = Counter.builder(METRIC_DLQ_STORED)
                .description("Total number of events routed to the Dead Letter Queue")
                .register(builder.meterRegistry);
            Gauge.builder(METRIC_DLQ_PENDING, deadLetterQueue, dlq -> (double) dlq.size())
                .description("Current number of events in the Dead Letter Queue")
                .register(builder.meterRegistry);
        } else {
            // Fall back to the global composite registry (no-op if none configured)
            this.dlqStoredCounter = Counter.builder(METRIC_DLQ_STORED)
                .description("Total number of events routed to the Dead Letter Queue")
                .register(Metrics.globalRegistry);
            Gauge.builder(METRIC_DLQ_PENDING, deadLetterQueue, dlq -> (double) dlq.size())
                .description("Current number of events in the Dead Letter Queue")
                .register(Metrics.globalRegistry);
        }
    }

    /**
     * Process the event through the delegate operator.
     *
     * <p>On success, the result passes through transparently. On failure, the event
     * is stored in the DLQ and an empty (filter-drop) result is returned to halt
     * further downstream processing of this failed event.
     *
     * @param event the event to process
     * @return promise of processing result (empty on delegate failure)
     */
    @Override
    public Promise<OperatorResult> process(Event event) {
        return delegate.process(event)
            .then(result -> Promise.of(result),
                error -> {
                    String reason = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                    String errorType = error.getClass().getSimpleName();
                    String dlqId = deadLetterQueue.store(event, error, reason);
                    logger.warn("Event routed to DLQ (dlqId={}, errorType={}, reason={}): {}",
                        dlqId, errorType, reason, event);
                    // Increment metric — use the global registry for per-error-type tag
                    Metrics.globalRegistry.counter(METRIC_DLQ_STORED,
                        TAG_ERROR_TYPE, errorType).increment();
                    dlqStoredCounter.increment();
                    // Return empty result — downstream operators will not see this event
                    return Promise.of(OperatorResult.empty());
                });
    }

    /**
     * Returns the underlying dead letter queue for monitoring or replay.
     *
     * @return the configured DLQ
     */
    public DeadLetterQueue getDeadLetterQueue() {
        return deadLetterQueue;
    }

    @Override
    public com.ghatana.platform.domain.event.Event toEvent() {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("type", "operator.dead-letter-queue");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());
        payload.put("capabilities", java.util.List.of("dlq", "resilience", "error-handling", "recovery"));

        var headers = new java.util.HashMap<String, String>();
        headers.put("operatorId", getId().toString());
        headers.put("tenantId", getId().getNamespace());

        return com.ghatana.platform.domain.event.GEvent.builder()
                .type("operator.registered")
                .headers(headers)
                .payload(payload)
                .time(com.ghatana.platform.domain.event.EventTime.now())
                .build();
    }

    // ==================== Builder ====================

    /**
     * Create a new builder for {@link DeadLetterQueueOperator}.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DeadLetterQueueOperator}.
     */
    public static final class Builder {

        private UnifiedOperator operator;
        private DeadLetterQueue deadLetterQueue;
        private MeterRegistry meterRegistry;

        private Builder() {}

        /**
         * Set the delegate operator whose failures will be routed to the DLQ.
         *
         * @param operator delegate operator (required)
         * @return this builder
         */
        public Builder operator(UnifiedOperator operator) {
            this.operator = operator;
            return this;
        }

        /**
         * Set the dead letter queue to route failed events to.
         *
         * @param deadLetterQueue DLQ instance (required)
         * @return this builder
         */
        public Builder deadLetterQueue(DeadLetterQueue deadLetterQueue) {
            this.deadLetterQueue = deadLetterQueue;
            return this;
        }

        /**
         * Set the Micrometer {@link MeterRegistry} used to record DLQ metrics.
         *
         * <p>When omitted the global {@link Metrics#globalRegistry} is used, which
         * is a no-op registry unless a registry has been bound globally.
         *
         * @param meterRegistry Micrometer registry (may be {@code null})
         * @return this builder
         */
        public Builder meterRegistry(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return this;
        }

        /**
         * Build the {@link DeadLetterQueueOperator}.
         *
         * @return configured operator
         * @throws NullPointerException if operator or deadLetterQueue is null
         */
        public DeadLetterQueueOperator build() {
            return new DeadLetterQueueOperator(this);
        }
    }
}
