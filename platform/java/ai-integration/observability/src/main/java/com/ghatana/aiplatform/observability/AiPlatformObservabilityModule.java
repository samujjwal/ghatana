package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * ActiveJ DI module wiring AI observability components.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link CostTracker}</li>
 *   <li>{@link DataDriftDetector}</li>
 *   <li>{@link ModelDriftDetector}</li>
 *   <li>{@link QualityMonitor}</li>
 * </ul>
 * backed by the shared {@link MetricsCollector} abstraction.</p>
 
 *
 * @doc.type class
 * @doc.purpose Ai platform observability module
 * @doc.layer core
 * @doc.pattern Component
*/
public class AiPlatformObservabilityModule extends AbstractModule {

    private static final long DEFAULT_MODEL_DRIFT_WINDOW_MS = 3_600_000L; // 1 hour

    @Provides
    CostTracker costTracker(MetricsCollector metricsCollector) {
        return new CostTracker(metricsCollector);
    }

    @Provides
    DataDriftDetector dataDriftDetector(MetricsCollector metricsCollector) {
        return new DataDriftDetector(metricsCollector);
    }

    @Provides
    ModelDriftDetector modelDriftDetector(MetricsCollector metricsCollector) {
        return new ModelDriftDetector(metricsCollector, DEFAULT_MODEL_DRIFT_WINDOW_MS);
    }

    @Provides
    QualityMonitor qualityMonitor(MetricsCollector metricsCollector, DataDriftDetector dataDriftDetector) {
        return new QualityMonitor(metricsCollector, dataDriftDetector);
    }
}
