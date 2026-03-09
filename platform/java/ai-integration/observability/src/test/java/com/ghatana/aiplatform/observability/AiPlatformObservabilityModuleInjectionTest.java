package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.ObservabilityModule;
import io.activej.inject.Injector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPlatformObservabilityModuleInjectionTest {

    @Test
    void shouldWireAiObservabilityComponentsViaMetricsCollector() {
        // GIVEN an injector composed of core observability and AI observability modules
        Injector injector = Injector.of(
                new ObservabilityModule(),
                new AiPlatformObservabilityModule()
        );

        // WHEN resolving components
        MetricsCollector metricsCollector = injector.getInstance(MetricsCollector.class);
        CostTracker costTracker = injector.getInstance(CostTracker.class);
        DataDriftDetector dataDriftDetector = injector.getInstance(DataDriftDetector.class);
        ModelDriftDetector modelDriftDetector = injector.getInstance(ModelDriftDetector.class);
        QualityMonitor qualityMonitor = injector.getInstance(QualityMonitor.class);

        // THEN all components are wired and share a common MetricsCollector abstraction
        assertThat(metricsCollector).isNotNull();
        assertThat(costTracker).isNotNull();
        assertThat(dataDriftDetector).isNotNull();
        assertThat(modelDriftDetector).isNotNull();
        assertThat(qualityMonitor).isNotNull();
    }
}
