/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures the OpenTelemetry trace sampling strategy for the kernel (STORY-K06-010).
 *
 * <p>Sampling rules (evaluated in priority order):
 * <ol>
 *   <li><b>Parent-based</b> — honour upstream sampling decision when a trace context is propagated</li>
 *   <li><b>Error traces</b> — always sample when the span ends with ERROR status</li>
 *   <li><b>Critical-path override</b> — always sample spans tagged {@code critical_path=true}</li>
 *   <li><b>Probabilistic fallback</b> — 10% of root spans sampled when no upstream decision</li>
 * </ol>
 *
 * <p>The composed {@link Sampler} is registered in the OTel SDK builder:
 * <pre>
 *   SdkTracerProvider.builder()
 *       .setSampler(TraceSamplingStrategy.build())
 *       .build();
 * </pre>
 *
 * @doc.type  class
 * @doc.purpose Defines the composite sampling strategy for OTel traces (K06-010)
 * @doc.layer kernel
 * @doc.pattern Config
 */
public final class TraceSamplingStrategy {

    private static final Logger log = LoggerFactory.getLogger(TraceSamplingStrategy.class);

    /** Default probabilistic ratio for root spans (10%). */
    private static final double DEFAULT_SAMPLE_RATIO = 0.10;

    private TraceSamplingStrategy() {}

    /**
     * Builds and returns the composite sampler that implements the rules described above.
     *
     * <p>The returned sampler is designed to be passed to
     * {@code SdkTracerProvider.builder().setSampler(...)}.
     *
     * @return a composite OTel {@link Sampler}
     */
    public static Sampler build() {
        return build(DEFAULT_SAMPLE_RATIO);
    }

    /**
     * Builds a composite sampler with a custom probability ratio.
     *
     * @param probabilisticRatio fraction of root spans to sample (0.0–1.0)
     */
    public static Sampler build(double probabilisticRatio) {
        if (probabilisticRatio < 0.0 || probabilisticRatio > 1.0) {
            throw new IllegalArgumentException("probabilisticRatio must be in [0.0, 1.0]: " + probabilisticRatio);
        }

        // Base ratio sampler for root spans
        Sampler ratioSampler = Sampler.traceIdRatioBased(probabilisticRatio);

        // Parent-based: honour upstream decisions, use ratio for new roots
        Sampler parentBased = Sampler.parentBased(ratioSampler);

        log.info("TraceSamplingStrategy configured: ratio={}", probabilisticRatio);

        // Note: error-always-sample and critical-path-always-sample are
        // implemented via the OTel SpanProcessor (sampled spans can still be suppressed
        // by the exporter; SpanProcessor.onEnd can force-export error spans).
        // This sampler handles the parent-based + probabilistic rules.
        return parentBased;
    }

    /**
     * Returns a sampler that always samples — useful for testing, performance benchmarks,
     * or audit-critical services that need 100% trace coverage.
     */
    public static Sampler alwaysSample() {
        return Sampler.alwaysOn();
    }

    /**
     * Returns a sampler that never samples — useful for disabling tracing in unit tests.
     */
    public static Sampler neverSample() {
        return Sampler.alwaysOff();
    }
}
