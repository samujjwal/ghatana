package com.ghatana.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A simple implementation of MetricsCollectorFactory for the refactorer service.
 * This is a temporary solution until the actual implementation is available.
 
 * @doc.type class
 * @doc.purpose Handles metrics collector factory operations
 * @doc.layer core
 * @doc.pattern Factory
*/
public class MetricsCollectorFactory {
    private final MeterRegistry meterRegistry;
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();

    public MetricsCollectorFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer getOrCreateTimer(String name, String... tags) {
        String key = "timer:" + name + String.join(",", tags);
        return (Timer) metrics.computeIfAbsent(key, k -> 
            Timer.builder(name)
                .tags(tags)
                .publishPercentileHistogram()
                .register(meterRegistry)
        );
    }

    public Counter getOrCreateCounter(String name, String... tags) {
        String key = "counter:" + name + String.join(",", tags);
        return (Counter) metrics.computeIfAbsent(key, k -> 
            Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
        );
    }

    public <T extends Number> void registerGauge(String name, T number, String... tags) {
        String key = "gauge:" + name + String.join(",", tags);
        Gauge.builder(name, number::doubleValue)
            .tags(tags)
            .register(meterRegistry);
    }

    public void recordTimer(String name, long duration, TimeUnit unit, String... tags) {
        Timer timer = getOrCreateTimer(name, tags);
        timer.record(duration, unit);
    }

    public void incrementCounter(String name, String... tags) {
        getOrCreateCounter(name, tags).increment();
    }

    public void incrementCounter(String name, double amount, String... tags) {
        getOrCreateCounter(name, tags).increment(amount);
    }
}
