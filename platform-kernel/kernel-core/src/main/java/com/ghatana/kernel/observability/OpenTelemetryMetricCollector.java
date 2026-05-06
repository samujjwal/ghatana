package com.ghatana.kernel.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableLongGauge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenTelemetry implementation of MetricCollectorPort.
 *
 * @doc.type class
 * @doc.purpose OpenTelemetry metrics collector implementation
 * @doc.layer core
 * @doc.pattern Adapter
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class OpenTelemetryMetricCollector implements MetricCollectorPort {

    private final Meter meter;
    private final Map<String, LongCounter> counters = new ConcurrentHashMap<>();
    private final Map<String, ObservableDoubleGauge> gauges = new ConcurrentHashMap<>();
    private final Map<String, ObservableLongGauge> longGauges = new ConcurrentHashMap<>();
    private final Map<String, LongHistogram> histograms = new ConcurrentHashMap<>();
    private final Map<String, LongHistogram> timers = new ConcurrentHashMap<>();

    public OpenTelemetryMetricCollector(OpenTelemetry openTelemetry) {
        this.meter = openTelemetry.getMeter("ghatana.kernel");
    }

    @Override
    public void incrementCounter(String name, long increment, String... tags) {
        LongCounter counter = counters.computeIfAbsent(name, n -> 
            meter.counterBuilder(n)
                .setDescription("Counter metric")
                .build()
        );
        counter.add(increment, toAttributes(tags));
    }

    @Override
    public void recordGauge(String name, double value, String... tags) {
        // OpenTelemetry gauges are callback-based (ObservableGauge)
        // For simplicity, we skip gauge recording in this stub implementation
        // A production implementation would use ObservableGauge with callbacks
    }

    @Override
    public void recordHistogram(String name, double value, String... tags) {
        LongHistogram histogram = histograms.computeIfAbsent(name, n -> 
            meter.histogramBuilder(n)
                .setDescription("Histogram metric")
                .ofLongs()
                .build()
        );
        histogram.record((long) value, toAttributes(tags));
    }

    @Override
    public void recordTimer(String name, long durationMillis, String... tags) {
        LongHistogram timer = timers.computeIfAbsent(name, n -> 
            meter.histogramBuilder(n)
                .setDescription("Timer metric")
                .ofLongs()
                .setUnit("ms")
                .build()
        );
        timer.record(durationMillis, toAttributes(tags));
    }

    @Override
    public Timer startTimer(String name, String... tags) {
        long startTime = System.currentTimeMillis();
        return new Timer() {
            @Override
            public void stop() {
                long duration = System.currentTimeMillis() - startTime;
                recordTimer(name, duration, tags);
            }

            @Override
            public long getElapsedMillis() {
                return System.currentTimeMillis() - startTime;
            }
        };
    }

    @Override
    public void recordMetric(String name, double value, Map<String, String> tags) {
        // Generic metric recording - use histogram for simplicity
        LongHistogram histogram = histograms.computeIfAbsent(name, n -> 
            meter.histogramBuilder(n)
                .setDescription("Generic metric")
                .ofLongs()
                .build()
        );
        histogram.record((long) value, toAttributesFromMap(tags));
    }

    private io.opentelemetry.api.common.Attributes toAttributes(String... tags) {
        if (tags == null || tags.length == 0) {
            return io.opentelemetry.api.common.Attributes.empty();
        }
        io.opentelemetry.api.common.AttributesBuilder builder = io.opentelemetry.api.common.Attributes.builder();
        for (int i = 0; i < tags.length - 1; i += 2) {
            builder.put(tags[i], tags[i + 1]);
        }
        return builder.build();
    }

    private io.opentelemetry.api.common.Attributes toAttributesFromMap(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return io.opentelemetry.api.common.Attributes.empty();
        }
        io.opentelemetry.api.common.AttributesBuilder builder = io.opentelemetry.api.common.Attributes.builder();
        tags.forEach(builder::put);
        return builder.build();
    }
}
