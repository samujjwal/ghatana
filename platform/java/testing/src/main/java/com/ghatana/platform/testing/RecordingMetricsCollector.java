package com.ghatana.platform.testing;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test double for MetricsCollector that records all metrics for test assertions.
 *
 * @doc.type class
 * @doc.purpose Test double for MetricsCollector to record metrics for assertions
 * @doc.layer testing
 * @doc.pattern Test Double
 */
public class RecordingMetricsCollector implements MetricsCollector {

    private final Map<String, List<MetricRecord>> timers = new ConcurrentHashMap<>();
    private final Map<String, List<MetricRecord>> counters = new ConcurrentHashMap<>();
    private final Map<String, List<MetricRecord>> gauges = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Override
    public void increment(String metricName, double amount, Map<String, String> tags) {
        counters.computeIfAbsent(metricName, key -> new ArrayList<>())
            .add(new MetricRecord(metricName, amount, tags));
    }

    @Override
    public void recordError(String metricName, Exception e, Map<String, String> tags) {
        Map<String, String> effectiveTags = new ConcurrentHashMap<>(tags);
        effectiveTags.putIfAbsent("error_type", e.getClass().getSimpleName());
        counters.computeIfAbsent(metricName, key -> new ArrayList<>())
            .add(new MetricRecord(metricName, e, effectiveTags));
    }

    @Override
    public void incrementCounter(String metricName, String... keyValues) {
        counters.computeIfAbsent(metricName, key -> new ArrayList<>())
            .add(new MetricRecord(metricName, 1, toTagMap(keyValues)));
    }

    @Override
    public void recordTimer(String name, long durationMs, Map<String, String> tags) {
        timers.computeIfAbsent(name, k -> new ArrayList<>())
              .add(new MetricRecord(name, durationMs, tags));
    }

    public void setGauge(String name, double value, Map<String, String> tags) {
        gauges.computeIfAbsent(name, k -> new ArrayList<>())
              .add(new MetricRecord(name, value, tags));
    }

    @Override
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public List<MetricRecord> getTimers(String name) {
        return new ArrayList<>(timers.getOrDefault(name, List.of()));
    }

    public List<MetricRecord> getCounters(String name) {
        return new ArrayList<>(counters.getOrDefault(name, List.of()));
    }

    public List<MetricRecord> getGauges(String name) {
        return new ArrayList<>(gauges.getOrDefault(name, List.of()));
    }

    public List<MetricRecord> getRecords() {
        List<MetricRecord> all = new ArrayList<>();
        timers.values().forEach(all::addAll);
        counters.values().forEach(all::addAll);
        gauges.values().forEach(all::addAll);
        return all;
    }

    public void clear() {
        timers.clear();
        counters.clear();
        gauges.clear();
    }

    public int getTimerCount() {
        return timers.values().stream().mapToInt(List::size).sum();
    }

    public int getCounterCount() {
        return counters.values().stream().mapToInt(List::size).sum();
    }

    private Map<String, String> toTagMap(String... keyValues) {
        if (keyValues.length == 0) {
            return Map.of();
        }

        Map<String, String> tags = new ConcurrentHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            tags.put(keyValues[index], keyValues[index + 1]);
        }
        return tags;
    }

    public record MetricRecord(String name, Object value, Map<String, String> tags) {}
}
