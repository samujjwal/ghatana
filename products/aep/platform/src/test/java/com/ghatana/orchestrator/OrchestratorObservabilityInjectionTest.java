package com.ghatana.orchestrator;

import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsRegistry;
import com.ghatana.platform.observability.ObservabilityModule;
import com.ghatana.platform.observability.TracingManager;
import io.activej.inject.Injector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorObservabilityInjectionTest {

    @Test
    void observabilityModuleShouldProvideMetricsAndTracingForOrchestrator() {
        Injector injector = Injector.of(new ObservabilityModule());

        MetricsRegistry metricsRegistry = injector.getInstance(MetricsRegistry.class);
        MetricsCollector metricsCollector = injector.getInstance(MetricsCollector.class);
        TracingManager tracingManager = injector.getInstance(TracingManager.class);
        Metrics metrics = injector.getInstance(Metrics.class);

        assertThat(metricsRegistry).isNotNull();
        assertThat(metricsCollector).isNotNull();
        assertThat(tracingManager).isNotNull();
        assertThat(metrics).isNotNull();
        assertThat(metricsCollector.getMeterRegistry())
                .isSameAs(metricsRegistry.getMeterRegistry());
    }
}
