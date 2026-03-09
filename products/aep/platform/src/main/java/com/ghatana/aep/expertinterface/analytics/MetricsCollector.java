package com.ghatana.aep.expertinterface.analytics;

import static com.ghatana.aep.expertinterface.analytics.MetricData.MetricType;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.DistributionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Metrics collector for gathering and aggregating system metrics.
 * 
 * @doc.type class
 * @doc.purpose Metrics collection and aggregation
 * @doc.layer analytics
 */
public class MetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);
    
    private final MeterRegistry meterRegistry;
    
    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry is required");
    }
    
    /**
     * Collects all metrics within the specified time range.
     * 
     * @param timeRange time range for metric collection
     * @return list of collected metrics
     */
    public List<MetricData> collectMetrics(TimeRange timeRange) {
        Objects.requireNonNull(timeRange, "TimeRange is required");
        
        log.debug("Collecting metrics for time range: {} to {}", 
            timeRange.getStart(), timeRange.getEnd());
        
        List<MetricData> metrics = new ArrayList<>();
        
        // Collect counter metrics
        metrics.addAll(collectCounterMetrics(timeRange));
        
        // Collect timer metrics
        metrics.addAll(collectTimerMetrics(timeRange));
        
        // Collect gauge metrics
        metrics.addAll(collectGaugeMetrics(timeRange));
        
        // Collect distribution summary metrics
        metrics.addAll(collectDistributionMetrics(timeRange));
        
        log.info("Collected {} metrics for time range", metrics.size());
        return metrics;
    }
    
    /**
     * Collects metrics for a specific metric name.
     */
    public List<MetricData> collectMetricsByName(String metricName, TimeRange timeRange) {
        Objects.requireNonNull(metricName, "Metric name is required");
        Objects.requireNonNull(timeRange, "TimeRange is required");
        
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().equals(metricName))
            .map(meter -> convertMeterToMetricData(meter, timeRange))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Collects metrics by tag.
     */
    public List<MetricData> collectMetricsByTag(String tagKey, String tagValue, TimeRange timeRange) {
        Objects.requireNonNull(tagKey, "Tag key is required");
        Objects.requireNonNull(tagValue, "Tag value is required");
        Objects.requireNonNull(timeRange, "TimeRange is required");
        
        return meterRegistry.getMeters().stream()
            .filter(meter -> hasTag(meter, tagKey, tagValue))
            .map(meter -> convertMeterToMetricData(meter, timeRange))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets aggregated metrics summary.
     */
    public MetricsSummary getMetricsSummary(TimeRange timeRange) {
        List<MetricData> allMetrics = collectMetrics(timeRange);
        
        long totalMetrics = allMetrics.size();
        long counterMetrics = allMetrics.stream()
            .filter(m -> m.getType() == MetricType.COUNTER)
            .count();
        long timerMetrics = allMetrics.stream()
            .filter(m -> m.getType() == MetricType.TIMER)
            .count();
        long gaugeMetrics = allMetrics.stream()
            .filter(m -> m.getType() == MetricType.GAUGE)
            .count();
        
        double totalValue = allMetrics.stream()
            .mapToDouble(MetricData::getValue)
            .sum();
        
        return new MetricsSummary(
            totalMetrics,
            counterMetrics,
            timerMetrics,
            gaugeMetrics,
            totalValue,
            timeRange
        );
    }
    
    private List<MetricData> collectCounterMetrics(TimeRange timeRange) {
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter instanceof Counter)
            .map(meter -> {
                Counter counter = (Counter) meter;
                return new MetricData(
                    counter.getId().getName(),
                    MetricType.COUNTER,
                    counter.count(),
                    extractTags(counter),
                    Instant.now(),
                    "count"
                );
            })
            .collect(Collectors.toList());
    }
    
    private List<MetricData> collectTimerMetrics(TimeRange timeRange) {
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter instanceof Timer)
            .flatMap(meter -> {
                Timer timer = (Timer) meter;
                List<MetricData> timerMetrics = new ArrayList<>();
                
                // Count
                timerMetrics.add(new MetricData(
                    timer.getId().getName() + ".count",
                    MetricType.TIMER,
                    timer.count(),
                    extractTags(timer),
                    Instant.now(),
                    "count"
                ));
                
                // Total time
                timerMetrics.add(new MetricData(
                    timer.getId().getName() + ".total",
                    MetricType.TIMER,
                    timer.totalTime(TimeUnit.MILLISECONDS),
                    extractTags(timer),
                    Instant.now(),
                    "ms"
                ));
                
                // Mean
                timerMetrics.add(new MetricData(
                    timer.getId().getName() + ".mean",
                    MetricType.TIMER,
                    timer.mean(TimeUnit.MILLISECONDS),
                    extractTags(timer),
                    Instant.now(),
                    "ms"
                ));
                
                // Max
                timerMetrics.add(new MetricData(
                    timer.getId().getName() + ".max",
                    MetricType.TIMER,
                    timer.max(TimeUnit.MILLISECONDS),
                    extractTags(timer),
                    Instant.now(),
                    "ms"
                ));
                
                return timerMetrics.stream();
            })
            .collect(Collectors.toList());
    }
    
    private List<MetricData> collectGaugeMetrics(TimeRange timeRange) {
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter instanceof Gauge)
            .map(meter -> {
                Gauge gauge = (Gauge) meter;
                return new MetricData(
                    gauge.getId().getName(),
                    MetricType.GAUGE,
                    gauge.value(),
                    extractTags(gauge),
                    Instant.now(),
                    "value"
                );
            })
            .collect(Collectors.toList());
    }
    
    private List<MetricData> collectDistributionMetrics(TimeRange timeRange) {
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter instanceof DistributionSummary)
            .flatMap(meter -> {
                DistributionSummary summary = (DistributionSummary) meter;
                List<MetricData> summaryMetrics = new ArrayList<>();
                
                // Count
                summaryMetrics.add(new MetricData(
                    summary.getId().getName() + ".count",
                    MetricType.DISTRIBUTION_SUMMARY,
                    summary.count(),
                    extractTags(summary),
                    Instant.now(),
                    "count"
                ));
                
                // Total
                summaryMetrics.add(new MetricData(
                    summary.getId().getName() + ".total",
                    MetricType.DISTRIBUTION_SUMMARY,
                    summary.totalAmount(),
                    extractTags(summary),
                    Instant.now(),
                    "amount"
                ));
                
                // Mean
                summaryMetrics.add(new MetricData(
                    summary.getId().getName() + ".mean",
                    MetricType.DISTRIBUTION_SUMMARY,
                    summary.mean(),
                    extractTags(summary),
                    Instant.now(),
                    "amount"
                ));
                
                // Max
                summaryMetrics.add(new MetricData(
                    summary.getId().getName() + ".max",
                    MetricType.DISTRIBUTION_SUMMARY,
                    summary.max(),
                    extractTags(summary),
                    Instant.now(),
                    "amount"
                ));
                
                return summaryMetrics.stream();
            })
            .collect(Collectors.toList());
    }
    
    private MetricData convertMeterToMetricData(Meter meter, TimeRange timeRange) {
        if (meter instanceof Counter) {
            Counter counter = (Counter) meter;
            return new MetricData(
                counter.getId().getName(),
                MetricType.COUNTER,
                counter.count(),
                extractTags(counter),
                Instant.now(),
                "count"
            );
        } else if (meter instanceof Timer) {
            Timer timer = (Timer) meter;
            return new MetricData(
                timer.getId().getName(),
                MetricType.TIMER,
                timer.mean(TimeUnit.MILLISECONDS),
                extractTags(timer),
                Instant.now(),
                "ms"
            );
        } else if (meter instanceof Gauge) {
            Gauge gauge = (Gauge) meter;
            return new MetricData(
                gauge.getId().getName(),
                MetricType.GAUGE,
                gauge.value(),
                extractTags(gauge),
                Instant.now(),
                "value"
            );
        } else if (meter instanceof DistributionSummary) {
            DistributionSummary summary = (DistributionSummary) meter;
            return new MetricData(
                summary.getId().getName(),
                MetricType.DISTRIBUTION_SUMMARY,
                summary.mean(),
                extractTags(summary),
                Instant.now(),
                "amount"
            );
        }
        return null;
    }
    
    private Map<String, String> extractTags(Meter meter) {
        Map<String, String> tags = new HashMap<>();
        meter.getId().getTags().forEach(tag -> 
            tags.put(tag.getKey(), tag.getValue())
        );
        return tags;
    }
    
    private boolean hasTag(Meter meter, String tagKey, String tagValue) {
        return meter.getId().getTags().stream()
            .anyMatch(tag -> tag.getKey().equals(tagKey) && tag.getValue().equals(tagValue));
    }
    
    /**
     * Metrics summary.
     */
    public static class MetricsSummary {
        private final long totalMetrics;
        private final long counterMetrics;
        private final long timerMetrics;
        private final long gaugeMetrics;
        private final double totalValue;
        private final TimeRange timeRange;
        
        public MetricsSummary(long totalMetrics, long counterMetrics, long timerMetrics,
                             long gaugeMetrics, double totalValue, TimeRange timeRange) {
            this.totalMetrics = totalMetrics;
            this.counterMetrics = counterMetrics;
            this.timerMetrics = timerMetrics;
            this.gaugeMetrics = gaugeMetrics;
            this.totalValue = totalValue;
            this.timeRange = timeRange;
        }
        
        public long getTotalMetrics() { return totalMetrics; }
        public long getCounterMetrics() { return counterMetrics; }
        public long getTimerMetrics() { return timerMetrics; }
        public long getGaugeMetrics() { return gaugeMetrics; }
        public double getTotalValue() { return totalValue; }
        public TimeRange getTimeRange() { return timeRange; }
    }
}
