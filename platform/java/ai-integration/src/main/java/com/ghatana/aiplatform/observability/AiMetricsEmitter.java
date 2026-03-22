package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.time.Duration;
import java.util.Objects;

/**
 * AI-specific metrics emitter for model and feature observability.
 *
 * @doc.type class
 * @doc.purpose AI metrics collection
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public class AiMetricsEmitter {

    private final MetricsCollector metrics;

    public AiMetricsEmitter(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    /**
     * Records model inference metrics.
     *
     * @param modelName the model name
     * @param version the model version
     * @param duration inference duration
     * @param success whether inference succeeded
     */
    public void recordInference(String modelName, String version, Duration duration, boolean success) {
        metrics.incrementCounter("ai.model.inference.count",
                "model", modelName,
                "version", version,
                "success", String.valueOf(success));

        metrics.recordTimer("ai.model.inference.duration", duration.toMillis(),
                "model", modelName,
                "version", version);

        if (!success) {
            metrics.incrementCounter("ai.model.inference.errors",
                    "model", modelName,
                    "version", version);
        }
    }

    /**
     * Records prediction quality metrics.
     *
     * @param modelName the model name
     * @param version the model version
     * @param qualityScore quality score (0.0 - 1.0)
     */
    public void recordPredictionQuality(String modelName, String version, double qualityScore) {
        Tags tags = Tags.of("model", modelName, "version", version);
        metrics.getMeterRegistry().gauge("ai.model.quality.score", tags, qualityScore);
    }

    /**
     * Records data drift detection.
     *
     * @param modelName the model name
     * @param version the model version
     * @param driftMagnitude magnitude of drift detected
     */
    public void recordDrift(String modelName, String version, double driftMagnitude) {
        metrics.incrementCounter("ai.model.drift.count",
                "model", modelName,
                "version", version);

        Tags tags = Tags.of("model", modelName, "version", version);
        metrics.getMeterRegistry().gauge("ai.model.drift.magnitude", tags, driftMagnitude);
    }
}
