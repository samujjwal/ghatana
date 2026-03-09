package com.ghatana.datacloud.entity.observability;

import io.activej.promise.Promise;
import java.util.Objects;

/**
 * Port interface for distributed trace sampling strategy.
 *
 * <p><b>Purpose</b><br>
 * Defines abstraction for sampling decisions (head-based, tail-based, rate-limiting).
 * Controls trace sampling to balance observability coverage vs. storage/cost.
 *
 * <p><b>Sampling Strategies</b><br>
 * - Head-based: Decision made at trace start (low overhead, limited context)
 * - Tail-based: Decision made after trace completion (full context, higher overhead)
 * - Rate-limiting: Enforce maximum traces/sec to prevent resource exhaustion
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SamplingStrategy strategy = headSamplingStrategy; // 10% sampling
 * Promise<Boolean> shouldSample = strategy.shouldSample(traceContext);
 * shouldSample.whenResult(sampled -> {
 *     if (sampled) {
 *         tracing.recordSpan(span);
 *     }
 * });
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe. Multiple threads may invoke shouldSample
 * concurrently for different tenants.
 *
 * <p><b>Performance Characteristics</b><br>
 * Head-based sampling: O(1) per decision
 * Tail-based sampling: O(n) where n = span count in trace
 * Rate-limiting: O(1) token bucket check
 *
 * @see com.ghatana.datacloud.entity.observability.TraceContext
 * @see com.ghatana.datacloud.entity.observability.Span
 * @doc.type interface
 * @doc.purpose Sampling decision contract for distributed traces
 * @doc.layer product
 * @doc.pattern Port
 */
public interface SamplingStrategy {

    /**
     * Determine if a trace should be sampled for recording.
     *
     * <p>Called for each trace start (head-based) or completion (tail-based).
     * Decision is persisted via W3C traceparent sampled flag.
     *
     * <p>If sampling rate changes mid-trace, the sampled flag is NOT updated
     * (the trace inherits its original decision).
     *
     * @param context trace context containing tenant ID and current sampled flag
     * @return Promise resolving to true if trace should be sampled, false otherwise
     * @throws NullPointerException if context is null
     *
     * <p><b>Async Guarantees</b><br>
     * Promise may be resolved immediately (head-based) or deferred (tail-based).
     * Exceptions in shouldSample are logged but not propagated to callers.
     */
    Promise<Boolean> shouldSample(TraceContext context);

    /**
     * Get strategy name for metrics/logging.
     *
     * @return strategy identifier (e.g., "head-10%", "tail-error", "ratelimit-1000")
     */
    String getName();

    /**
     * Get human-readable description of sampling strategy.
     *
     * @return strategy description including any parameters
     */
    String getDescription();

    /**
     * Check health of sampling strategy.
     *
     * @return Promise resolving to true if strategy is operational
     */
    Promise<Boolean> isHealthy();

    /**
     * Statistics for sampling decisions.
     *
     * <p><b>Purpose</b><br>
     * Enables monitoring of sampling effectiveness:
     * - Total decisions made
     * - Traces sampled vs. dropped
     * - Per-tenant sampling rates
     * - Rate-limiting rejections
     *
     * @return snapshot of sampling statistics
     */
    SamplingStatistics getStatistics();

    /**
     * Immutable statistics snapshot for sampling strategy.
     */
    final class SamplingStatistics {
        private final long totalDecisions;
        private final long sampledTraces;
        private final long droppedTraces;
        private final double sampleRate;
        private final String strategyName;

        /**
         * Create sampling statistics snapshot.
         *
         * @param totalDecisions total decisions made
         * @param sampledTraces traces approved for sampling
         * @param droppedTraces traces rejected by sampling
         * @param sampleRate current sampling rate (0.0-1.0)
         * @param strategyName strategy name
         * @throws IllegalArgumentException if sampleRate not in [0.0, 1.0]
         */
        public SamplingStatistics(
            long totalDecisions,
            long sampledTraces,
            long droppedTraces,
            double sampleRate,
            String strategyName
        ) {
            if (sampleRate < 0.0 || sampleRate > 1.0) {
                throw new IllegalArgumentException(
                    "sampleRate must be in [0.0, 1.0], got " + sampleRate
                );
            }
            this.totalDecisions = totalDecisions;
            this.sampledTraces = sampledTraces;
            this.droppedTraces = droppedTraces;
            this.sampleRate = sampleRate;
            this.strategyName = Objects.requireNonNull(strategyName, "strategyName required");
        }

        public long getTotalDecisions() {
            return totalDecisions;
        }

        public long getSampledTraces() {
            return sampledTraces;
        }

        public long getDroppedTraces() {
            return droppedTraces;
        }

        public double getSampleRate() {
            return sampleRate;
        }

        public String getStrategyName() {
            return strategyName;
        }

        @Override
        public String toString() {
            return "SamplingStatistics{" +
                "strategy='" + strategyName + '\'' +
                ", totalDecisions=" + totalDecisions +
                ", sampledTraces=" + sampledTraces +
                ", droppedTraces=" + droppedTraces +
                ", sampleRate=" + String.format("%.2f%%", sampleRate * 100) +
                '}';
        }
    }
}
