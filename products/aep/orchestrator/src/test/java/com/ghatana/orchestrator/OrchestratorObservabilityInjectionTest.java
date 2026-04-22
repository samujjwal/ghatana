package com.ghatana.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsRegistry;
import com.ghatana.platform.observability.ObservabilityModule;
import com.ghatana.platform.observability.TracingManager;
import io.activej.inject.Injector;
import org.junit.jupiter.api.Test;

class OrchestratorObservabilityInjectionTest {

    @Test
    void observabilityModuleShouldProvideMetricsAndTracingForOrchestrator() { // GH-90000
        Injector injector = Injector.of(new ObservabilityModule()); // GH-90000

        MetricsRegistry metricsRegistry = injector.getInstance(MetricsRegistry.class); // GH-90000
        MetricsCollector metricsCollector = injector.getInstance(MetricsCollector.class); // GH-90000
        TracingManager tracingManager = injector.getInstance(TracingManager.class); // GH-90000
        Metrics metrics = injector.getInstance(Metrics.class); // GH-90000

        assertThat(metricsRegistry).isNotNull(); // GH-90000
        assertThat(metricsCollector).isNotNull(); // GH-90000
        assertThat(tracingManager).isNotNull(); // GH-90000
        assertThat(metrics).isNotNull(); // GH-90000
        assertThat(metricsCollector.getMeterRegistry()).isSameAs(metricsRegistry.getMeterRegistry()); // GH-90000
    }
}
