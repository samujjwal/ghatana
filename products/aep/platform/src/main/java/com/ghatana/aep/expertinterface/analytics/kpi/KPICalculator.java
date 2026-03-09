package com.ghatana.aep.expertinterface.analytics.kpi;

import com.ghatana.aep.expertinterface.analytics.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * KPI Calculator that calculates and tracks key performance indicators
 * for pattern discovery, performance, and business metrics with
 * caching, aggregation, and benchmarking capabilities.
 * 
 * @doc.type class
 * @doc.purpose KPI calculation and tracking
 * @doc.layer analytics
 * @doc.pattern KPI Calculator
 */
public class KPICalculator {
    
    private static final Logger log = LoggerFactory.getLogger(KPICalculator.class);
    
    private final Eventloop eventloop;
    private final MetricsCollector metricsCollector;
    private final KPIAggregator kpiAggregator;
    private final BenchmarkProvider benchmarkProvider;
    
    // KPI cache and metrics
    private final Map<String, KPIValue> kpiCache = new ConcurrentHashMap<>();
    private final AtomicLong totalCalculations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    
    public KPICalculator(Eventloop eventloop,
                         MetricsCollector metricsCollector,
                         KPIAggregator kpiAggregator,
                         BenchmarkProvider benchmarkProvider) {
        this.eventloop = eventloop;
        this.metricsCollector = metricsCollector;
        this.kpiAggregator = kpiAggregator;
        this.benchmarkProvider = benchmarkProvider;
        
        log.info("KPI Calculator initialized");
    }
    
    /**
     * Calculates pattern discovery KPIs.
     * 
     * @param timeRange Time range for calculation
     * @return Promise of KPI values
     */
    public Promise<Map<String, KPIValue>> calculateDiscoveryKPIs(TimeRange timeRange) {
        return Promise.ofBlocking(eventloop, () -> {
            totalCalculations.incrementAndGet();
            
            Map<String, KPIValue> kpis = new HashMap<>();
            
            // Pattern Discovery Rate
            List<MetricData> discoveryMetrics = metricsCollector.collectMetricsByName(
                "pattern.discovery.count", timeRange);
            double discoveryRate = discoveryMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .sum();
            kpis.put("pattern_discovery_rate", new KPIValue(
                "Pattern Discovery Rate",
                discoveryRate,
                "patterns/hour",
                calculateTrend(discoveryRate, 100.0)
            ));
            
            // Pattern Accuracy
            List<MetricData> accuracyMetrics = metricsCollector.collectMetricsByName(
                "pattern.accuracy", timeRange);
            double accuracy = accuracyMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .average()
                .orElse(0.0);
            kpis.put("pattern_accuracy", new KPIValue(
                "Pattern Accuracy",
                accuracy * 100,
                "%",
                calculateTrend(accuracy, 0.85)
            ));
            
            // Pattern Coverage
            List<MetricData> coverageMetrics = metricsCollector.collectMetricsByName(
                "pattern.coverage", timeRange);
            double coverage = coverageMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .average()
                .orElse(0.0);
            kpis.put("pattern_coverage", new KPIValue(
                "Pattern Coverage",
                coverage * 100,
                "%",
                calculateTrend(coverage, 0.75)
            ));
            
            // False Positive Rate
            List<MetricData> fpMetrics = metricsCollector.collectMetricsByName(
                "pattern.false_positive_rate", timeRange);
            double fpRate = fpMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .average()
                .orElse(0.0);
            kpis.put("false_positive_rate", new KPIValue(
                "False Positive Rate",
                fpRate * 100,
                "%",
                calculateTrend(fpRate, 0.05)
            ));
            
            log.debug("Calculated {} discovery KPIs", kpis.size());
            return kpis;
        });
    }
    
    /**
     * Calculates performance KPIs.
     * 
     * @param timeRange Time range for calculation
     * @return Promise of KPI values
     */
    public Promise<Map<String, KPIValue>> calculatePerformanceKPIs(TimeRange timeRange) {
        return Promise.ofBlocking(eventloop, () -> {
            totalCalculations.incrementAndGet();
            
            Map<String, KPIValue> kpis = new HashMap<>();
            
            // Event Processing Latency (p95)
            List<MetricData> latencyMetrics = metricsCollector.collectMetricsByName(
                "event.processing.latency", timeRange);
            double p95Latency = latencyMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .sorted()
                .skip((long)(latencyMetrics.size() * 0.95))
                .findFirst()
                .orElse(0.0);
            kpis.put("p95_latency", new KPIValue(
                "P95 Processing Latency",
                p95Latency,
                "ms",
                calculateTrend(p95Latency, 100.0)
            ));
            
            // Throughput
            List<MetricData> throughputMetrics = metricsCollector.collectMetricsByName(
                "event.processing.throughput", timeRange);
            double throughput = throughputMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .average()
                .orElse(0.0);
            kpis.put("throughput", new KPIValue(
                "Event Throughput",
                throughput,
                "events/sec",
                calculateTrend(throughput, 1000.0)
            ));
            
            // Error Rate
            List<MetricData> errorMetrics = metricsCollector.collectMetricsByName(
                "event.processing.errors", timeRange);
            double errorRate = errorMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .sum();
            kpis.put("error_rate", new KPIValue(
                "Error Rate",
                errorRate,
                "errors/hour",
                calculateTrend(errorRate, 10.0)
            ));
            
            // System Availability
            List<MetricData> uptimeMetrics = metricsCollector.collectMetricsByName(
                "system.uptime", timeRange);
            double availability = uptimeMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .average()
                .orElse(0.0);
            kpis.put("availability", new KPIValue(
                "System Availability",
                availability * 100,
                "%",
                calculateTrend(availability, 0.999)
            ));
            
            log.debug("Calculated {} performance KPIs", kpis.size());
            return kpis;
        });
    }
    
    /**
     * Calculates business impact KPIs.
     * 
     * @param timeRange Time range for calculation
     * @return Promise of KPI values
     */
    public Promise<Map<String, KPIValue>> calculateBusinessKPIs(TimeRange timeRange) {
        return Promise.ofBlocking(eventloop, () -> {
            totalCalculations.incrementAndGet();
            
            Map<String, KPIValue> kpis = new HashMap<>();
            
            // Cost Savings from Automation
            List<MetricData> automationMetrics = metricsCollector.collectMetricsByName(
                "automation.cost_savings", timeRange);
            double costSavings = automationMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .sum();
            kpis.put("cost_savings", new KPIValue(
                "Cost Savings",
                costSavings,
                "USD",
                calculateTrend(costSavings, 10000.0)
            ));
            
            // Time to Detection
            List<MetricData> detectionTimeMetrics = metricsCollector.collectMetricsByName(
                "detection.time_to_detect", timeRange);
            double avgDetectionTime = detectionTimeMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .average()
                .orElse(0.0);
            kpis.put("time_to_detection", new KPIValue(
                "Avg Time to Detection",
                avgDetectionTime,
                "seconds",
                calculateTrend(avgDetectionTime, 60.0)
            ));
            
            // Incident Prevention Rate
            List<MetricData> preventionMetrics = metricsCollector.collectMetricsByName(
                "incident.prevention_rate", timeRange);
            double preventionRate = preventionMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .average()
                .orElse(0.0);
            kpis.put("incident_prevention", new KPIValue(
                "Incident Prevention Rate",
                preventionRate * 100,
                "%",
                calculateTrend(preventionRate, 0.80)
            ));
            
            // User Satisfaction Score
            List<MetricData> satisfactionMetrics = metricsCollector.collectMetricsByName(
                "user.satisfaction_score", timeRange);
            double satisfaction = satisfactionMetrics.stream()
                .mapToDouble(MetricData::getValue)
                .average()
                .orElse(0.0);
            kpis.put("user_satisfaction", new KPIValue(
                "User Satisfaction",
                satisfaction,
                "score",
                calculateTrend(satisfaction, 4.0)
            ));
            
            log.debug("Calculated {} business KPIs", kpis.size());
            return kpis;
        });
    }
    
    /**
     * Generates KPI trends over time.
     * 
     * @param kpiName KPI name
     * @param timeRange Time range for trend analysis
     * @return Promise of KPI trend data
     */
    public Promise<KPITrend> generateKPITrend(String kpiName, TimeRange timeRange) {
        return Promise.ofBlocking(eventloop, () -> {
            totalCalculations.incrementAndGet();
            
            // Collect historical data points
            List<MetricData> historicalData = metricsCollector.collectMetricsByName(
                kpiName, timeRange);
            
            if (historicalData.isEmpty()) {
                log.warn("No historical data found for KPI: {}", kpiName);
                return new KPITrend(kpiName, new ArrayList<>(), 0.0, "STABLE");
            }
            
            // Create trend data points
            List<KPITrendPoint> trendPoints = historicalData.stream()
                .map(metric -> new KPITrendPoint(
                    metric.getTimestamp(),
                    metric.getValue()
                ))
                .sorted(Comparator.comparing(KPITrendPoint::getTimestamp))
                .collect(Collectors.toList());
            
            // Calculate trend direction
            double trendSlope = calculateTrendSlope(trendPoints);
            String trendDirection = determineTrendDirection(trendSlope);
            
            log.debug("Generated trend for KPI: {} with {} points, direction: {}",
                kpiName, trendPoints.size(), trendDirection);
            
            return new KPITrend(kpiName, trendPoints, trendSlope, trendDirection);
        });
    }
    
    private String calculateTrend(double current, double target) {
        if (current >= target * 0.95) return "GOOD";
        if (current >= target * 0.80) return "WARNING";
        return "CRITICAL";
    }
    
    private double calculateTrendSlope(List<KPITrendPoint> points) {
        if (points.size() < 2) return 0.0;
        
        // Simple linear regression
        int n = points.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = points.get(i).getValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }
    
    private String determineTrendDirection(double slope) {
        if (slope > 0.1) return "INCREASING";
        if (slope < -0.1) return "DECREASING";
        return "STABLE";
    }
    
    /**
     * Gets KPI calculator metrics.
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        long total = totalCalculations.get();
        long hits = cacheHits.get();
        
        metrics.put("totalCalculations", total);
        metrics.put("cacheHits", hits);
        metrics.put("cacheHitRate", total > 0 ? (double) hits / total : 0.0);
        metrics.put("cacheSize", kpiCache.size());
        
        return metrics;
    }
    
    /**
     * Clears KPI cache and resets metrics.
     */
    public void reset() {
        kpiCache.clear();
        totalCalculations.set(0);
        cacheHits.set(0);
        log.info("KPI Calculator reset completed");
    }
}
