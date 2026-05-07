/**
 * @fileoverview AI Anomaly Detection Service
 * ML-powered anomaly detection for DevSecOps monitoring
 * 
 * @doc.type service
 * @doc.purpose Detect anomalies in system metrics and security events
 * @doc.layer backend
 * @doc.pattern MachineLearning
 */

import io.activej.promise.Promise;
import java.util.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * AI Anomaly Detection Service
 * Uses statistical analysis and ML to detect unusual patterns
 */
public class AnomalyDetectionService {

    private final MetricsRepository metricsRepo;
    private final AlertRepository alertRepo;
    private final LLMService llmService;
    private final NotificationService notificationService;

    public AnomalyDetectionService(MetricsRepository metricsRepo,
                                   AlertRepository alertRepo,
                                   LLMService llmService,
                                   NotificationService notificationService) {
        this.metricsRepo = metricsRepo;
        this.alertRepo = alertRepo;
        this.llmService = llmService;
        this.notificationService = notificationService;
    }

    /**
     * Analyze metrics for anomalies
     * @doc.purpose Main anomaly detection entry point
     */
    public Promise<List<Anomaly>> analyzeMetrics(String componentId, 
                                                  MetricType metricType,
                                                  TimeRange timeRange) {
        // Fetch historical data
        return metricsRepo.getMetrics(componentId, metricType, timeRange)
            .then(metrics -> {
                if (metrics.size() < 10) {
                    return Promise.of(List.<Anomaly>of());
                }
                
                // Run multiple detection algorithms
                List<Anomaly> statisticalAnomalies = detectStatisticalAnomalies(metrics, componentId, metricType);
                List<Anomaly> trendAnomalies = detectTrendAnomalies(metrics, componentId, metricType);
                List<Anomaly> seasonalAnomalies = detectSeasonalAnomalies(metrics, componentId, metricType);
                
                // Combine and deduplicate
                List<Anomaly> allAnomalies = new ArrayList<>();
                allAnomalies.addAll(statisticalAnomalies);
                allAnomalies.addAll(trendAnomalies);
                allAnomalies.addAll(seasonalAnomalies);
                
                // Score and rank
                List<Anomaly> ranked = rankAnomalies(allAnomalies);
                
                // Generate AI insights for high-confidence anomalies
                return generateAIInsights(ranked.subList(0, Math.min(5, ranked.size())));
            });
    }

    /**
     * Statistical anomaly detection (Z-score method)
     * @doc.purpose Detect outliers using statistical methods
     */
    private List<Anomaly> detectStatisticalAnomalies(List<MetricPoint> metrics, 
                                                     String componentId,
                                                     MetricType metricType) {
        List<Anomaly> anomalies = new ArrayList<>();
        
        // Calculate mean and standard deviation
        double mean = metrics.stream()
            .mapToDouble(MetricPoint::value)
            .average()
            .orElse(0.0);
        
        double variance = metrics.stream()
            .mapToDouble(m -> Math.pow(m.value() - mean, 2))
            .average()
            .orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        
        // Detect outliers (Z-score > 3 or < -3)
        double threshold = 3.0;
        
        for (MetricPoint point : metrics) {
            double zScore = (point.value() - mean) / stdDev;
            
            if (Math.abs(zScore) > threshold) {
                AnomalySeverity severity = Math.abs(zScore) > 4 ? 
                    AnomalySeverity.CRITICAL : AnomalySeverity.HIGH;
                
                anomalies.add(new Anomaly(
                    UUID.randomUUID().toString(),
                    componentId,
                    metricType,
                    AnomalyType.STATISTICAL_OUTLIER,
                    severity,
                    point.timestamp(),
                    point.value(),
                    mean,
                    zScore,
                    "Value deviates " + String.format("%.2f", Math.abs(zScore)) + 
                    " standard deviations from mean",
                    Map.of("zScore", zScore, "mean", mean, "stdDev", stdDev),
                    false,
                    Instant.now()
                ));
            }
        }
        
        return anomalies;
    }

    /**
     * Trend-based anomaly detection
     * @doc.purpose Detect sudden changes in trends
     */
    private List<Anomaly> detectTrendAnomalies(List<MetricPoint> metrics,
                                               String componentId,
                                               MetricType metricType) {
        List<Anomaly> anomalies = new ArrayList<>();
        
        if (metrics.size() < 20) return anomalies;
        
        // Calculate moving averages
        int windowSize = 10;
        List<Double> movingAverages = new ArrayList<>();
        
        for (int i = windowSize; i < metrics.size(); i++) {
            double avg = metrics.subList(i - windowSize, i).stream()
                .mapToDouble(MetricPoint::value)
                .average()
                .orElse(0.0);
            movingAverages.add(avg);
        }
        
        // Detect sudden trend changes
        for (int i = 1; i < movingAverages.size(); i++) {
            double prevAvg = movingAverages.get(i - 1);
            double currAvg = movingAverages.get(i);
            double changePercent = (currAvg - prevAvg) / prevAvg;
            
            // Alert on >50% change
            if (Math.abs(changePercent) > 0.5) {
                AnomalySeverity severity = Math.abs(changePercent) > 1.0 ?
                    AnomalySeverity.CRITICAL : AnomalySeverity.HIGH;
                
                int metricIndex = i + windowSize;
                anomalies.add(new Anomaly(
                    UUID.randomUUID().toString(),
                    componentId,
                    metricType,
                    AnomalyType.TREND_CHANGE,
                    severity,
                    metrics.get(metricIndex).timestamp(),
                    metrics.get(metricIndex).value(),
                    prevAvg,
                    changePercent,
                    "Trend changed by " + String.format("%.1f%%", changePercent * 100),
                    Map.of("changePercent", changePercent, "previousAvg", prevAvg),
                    false,
                    Instant.now()
                ));
            }
        }
        
        return anomalies;
    }

    /**
     * Seasonal pattern anomaly detection
     * @doc.purpose Detect deviations from expected seasonal patterns
     */
    private List<Anomaly> detectSeasonalAnomalies(List<MetricPoint> metrics,
                                                String componentId,
                                                MetricType metricType) {
        List<Anomaly> anomalies = new ArrayList<>();
        
        // Group by hour of day to find patterns
        Map<Integer, List<Double>> hourlyPatterns = new HashMap<>();
        
        for (MetricPoint point : metrics) {
            int hour = point.timestamp().getHour();
            hourlyPatterns.computeIfAbsent(hour, k -> new ArrayList<>()).add(point.value());
        }
        
        // Calculate expected values per hour
        Map<Integer, Double> hourlyMeans = new HashMap<>();
        Map<Integer, Double> hourlyStdDevs = new HashMap<>();
        
        hourlyPatterns.forEach((hour, values) -> {
            double mean = values.stream().mapToDouble(v -> v).average().orElse(0.0);
            double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0.0);
            
            hourlyMeans.put(hour, mean);
            hourlyStdDevs.put(hour, Math.sqrt(variance));
        });
        
        // Check recent metrics against patterns
        int recentCount = Math.min(24, metrics.size());
        List<MetricPoint> recentMetrics = metrics.subList(metrics.size() - recentCount, metrics.size());
        
        for (MetricPoint point : recentMetrics) {
            int hour = point.timestamp().getHour();
            double expectedMean = hourlyMeans.getOrDefault(hour, point.value());
            double expectedStdDev = hourlyStdDevs.getOrDefault(hour, 0.0);
            
            if (expectedStdDev > 0) {
                double zScore = (point.value() - expectedMean) / expectedStdDev;
                
                if (Math.abs(zScore) > 2.5) {
                    anomalies.add(new Anomaly(
                        UUID.randomUUID().toString(),
                        componentId,
                        metricType,
                        AnomalyType.SEASONAL_DEVIATION,
                        AnomalySeverity.MEDIUM,
                        point.timestamp(),
                        point.value(),
                        expectedMean,
                        zScore,
                        "Unusual for time of day (hour " + hour + ")",
                        Map.of("expectedForHour", expectedMean, "hour", hour),
                        false,
                        Instant.now()
                    ));
                }
            }
        }
        
        return anomalies;
    }

    /**
     * Generate AI-powered insights for anomalies
     * @doc.purpose Enhance anomalies with LLM-generated analysis
     */
    private Promise<List<Anomaly>> generateAIInsights(List<Anomaly> anomalies) {
        if (anomalies.isEmpty()) {
            return Promise.of(List.of());
        }
        
        // Build context for LLM
        StringBuilder context = new StringBuilder("Analyze these system anomalies:\n\n");
        
        for (Anomaly anomaly : anomalies) {
            context.append(String.format(
                "- %s anomaly in %s: %s at %s (severity: %s)\n",
                anomaly.type(),
                anomaly.metricType(),
                anomaly.description(),
                anomaly.timestamp(),
                anomaly.severity()
            ));
        }
        
        return llmService.generateAnomalyAnalysis(context.toString())
            .then(analysis -> {
                // Enhance anomalies with AI insights
                List<Anomaly> enhanced = new ArrayList<>();
                
                for (int i = 0; i < anomalies.size(); i++) {
                    Anomaly anomaly = anomalies.get(i);
                    String aiInsight = extractInsightForAnomaly(analysis, i);
                    
                    enhanced.add(new Anomaly(
                        anomaly.id(),
                        anomaly.componentId(),
                        anomaly.metricType(),
                        anomaly.type(),
                        anomaly.severity(),
                        anomaly.timestamp(),
                        anomaly.actualValue(),
                        anomaly.expectedValue(),
                        anomaly.score(),
                        anomaly.description(),
                        anomaly.metadata(),
                        anomaly.acknowledged(),
                        anomaly.detectedAt(),
                        aiInsight,
                        analysis
                    ));
                }
                
                return Promise.of(enhanced);
            })
            .whenException(e -> {
                // Return original anomalies if AI fails
                return Promise.of(anomalies);
            });
    }

    /**
     * Rank anomalies by severity and confidence
     * @doc.purpose Prioritize most important anomalies
     */
    private List<Anomaly> rankAnomalies(List<Anomaly> anomalies) {
        // Deduplicate by timestamp (within 5 minutes)
        Map<String, Anomaly> deduplicated = new LinkedHashMap<>();
        
        for (Anomaly anomaly : anomalies) {
            String key = anomaly.componentId() + ":" + anomaly.metricType() + ":" + 
                        anomaly.timestamp().truncatedTo(ChronoUnit.MINUTES).toString();
            
            if (!deduplicated.containsKey(key) || 
                anomaly.severity().ordinal() > deduplicated.get(key).severity().ordinal()) {
                deduplicated.put(key, anomaly);
            }
        }
        
        // Sort by severity and score
        List<Anomaly> ranked = new ArrayList<>(deduplicated.values());
        ranked.sort((a, b) -> {
            int severityCompare = b.severity().ordinal() - a.severity().ordinal();
            if (severityCompare != 0) return severityCompare;
            return Double.compare(b.score(), a.score());
        });
        
        return ranked;
    }

    /**
     * Create alerts for high-severity anomalies
     * @doc.purpose Convert anomalies to actionable alerts
     */
    public Promise<Void> createAlerts(List<Anomaly> anomalies) {
        List<Promise<Void>> alertPromises = new ArrayList<>();
        
        for (Anomaly anomaly : anomalies) {
            if (anomaly.severity().ordinal() >= AnomalySeverity.HIGH.ordinal()) {
                Alert alert = new Alert(
                    UUID.randomUUID().toString(),
                    anomaly.componentId(),
                    mapSeverityToAlertLevel(anomaly.severity()),
                    "Anomaly detected: " + anomaly.description(),
                    buildAlertDescription(anomaly),
                    anomaly.detectedAt(),
                    false,
                    anomaly.id()
                );
                
                alertPromises.add(alertRepo.save(alert)
                    .then(saved -> {
                        // Send notification for critical alerts
                        if (anomaly.severity() == AnomalySeverity.CRITICAL) {
                            notificationService.sendAlertNotification(saved);
                        }
                        return Promise.complete();
                    }));
            }
        }
        
        return Promise.all(alertPromises).toVoid();
    }

    /**
     * Get anomaly trends over time
     * @doc.purpose Track anomaly frequency patterns
     */
    public Promise<AnomalyTrend> getAnomalyTrend(String componentId, TimeRange timeRange) {
        return metricsRepo.getAnomalies(componentId, timeRange)
            .map(anomalies -> {
                // Group by day
                Map<String, Long> dailyCounts = new HashMap<>();
                Map<AnomalyType, Long> typeCounts = new HashMap<>();
                
                for (Anomaly anomaly : anomalies) {
                    String day = anomaly.detectedAt().toString().substring(0, 10);
                    dailyCounts.merge(day, 1L, Long::sum);
                    typeCounts.merge(anomaly.type(), 1L, Long::sum);
                }
                
                return new AnomalyTrend(
                    componentId,
                    timeRange,
                    anomalies.size(),
                    dailyCounts,
                    typeCounts,
                    calculateTrendDirection(dailyCounts)
                );
            });
    }

    // Helper methods
    private String extractInsightForAnomaly(String analysis, int index) {
        // Parse LLM response to extract per-anomaly insights
        String[] lines = analysis.split("\n");
        if (index < lines.length) {
            return lines[index].trim();
        }
        return "No specific insight available";
    }

    private String buildAlertDescription(Anomaly anomaly) {
        return String.format(
            "Anomaly detected in %s for component %s\n" +
            "Type: %s\n" +
            "Severity: %s\n" +
            "Actual value: %.2f (expected: %.2f)\n" +
            "Score: %.2f\n" +
            "Detected at: %s\n" +
            "%s",
            anomaly.metricType(),
            anomaly.componentId(),
            anomaly.type(),
            anomaly.severity(),
            anomaly.actualValue(),
            anomaly.expectedValue(),
            anomaly.score(),
            anomaly.detectedAt(),
            anomaly.aiInsight() != null ? "AI Insight: " + anomaly.aiInsight() : ""
        );
    }

    private AlertLevel mapSeverityToAlertLevel(AnomalySeverity severity) {
        return switch (severity) {
            case CRITICAL -> AlertLevel.CRITICAL;
            case HIGH -> AlertLevel.HIGH;
            case MEDIUM -> AlertLevel.MEDIUM;
            case LOW -> AlertLevel.LOW;
        };
    }

    private TrendDirection calculateTrendDirection(Map<String, Long> dailyCounts) {
        if (dailyCounts.size() < 2) return TrendDirection.STABLE;
        
        List<Long> counts = new ArrayList<>(dailyCounts.values());
        long first = counts.get(0);
        long last = counts.get(counts.size() - 1);
        
        if (last > first * 1.5) return TrendDirection.INCREASING;
        if (last < first * 0.5) return TrendDirection.DECREASING;
        return TrendDirection.STABLE;
    }

    // Record classes
    public record MetricPoint(Instant timestamp, double value) {}
    
    public record Anomaly(String id, String componentId, MetricType metricType,
                       AnomalyType type, AnomalySeverity severity,
                       Instant timestamp, double actualValue, double expectedValue,
                       double score, String description, Map<String, Object> metadata,
                       boolean acknowledged, Instant detectedAt,
                       String aiInsight, String fullAnalysis) {
        
        public Anomaly(String id, String componentId, MetricType metricType,
                      AnomalyType type, AnomalySeverity severity,
                      Instant timestamp, double actualValue, double expectedValue,
                      double score, String description, Map<String, Object> metadata,
                      boolean acknowledged, Instant detectedAt) {
            this(id, componentId, metricType, type, severity, timestamp, actualValue,
                 expectedValue, score, description, metadata, acknowledged, detectedAt,
                 null, null);
        }
    }
    
    public record AnomalyTrend(String componentId, TimeRange timeRange,
                              int totalAnomalies, Map<String, Long> dailyCounts,
                              Map<AnomalyType, Long> typeCounts, TrendDirection direction) {}
    
    public record Alert(String id, String componentId, AlertLevel level,
                       String title, String description, Instant createdAt,
                       boolean acknowledged, String anomalyId) {}

    // Enums
    public enum MetricType {
        CPU, MEMORY, LATENCY, ERROR_RATE, THROUGHPUT, AVAILABILITY
    }
    
    public enum AnomalyType {
        STATISTICAL_OUTLIER, TREND_CHANGE, SEASONAL_DEVIATION, PATTERN_BREAK
    }
    
    public enum AnomalySeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum AlertLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum TrendDirection {
        INCREASING, STABLE, DECREASING
    }
    
    public record TimeRange(Instant start, Instant end) {}
}
