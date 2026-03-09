package com.ghatana.validation.ai.anomaly;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.validation.ai.AIPatternDetectionService;
import com.ghatana.validation.ai.AIPatternDetectionService.AnomalyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of anomaly detector using multiple algorithms.
 * Consolidated from event-core anomaly detection capabilities.
 
 *
 * @doc.type class
 * @doc.purpose Default anomaly detector
 * @doc.layer core
 * @doc.pattern Component
*/
public class DefaultAnomalyDetector implements AnomalyDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultAnomalyDetector.class);
    
    private final Map<String, BaselineStats> baselineStats = new HashMap<>();
    private final Map<String, Object> modelMetrics = new HashMap<>();
    
    @Override
    public ValidationAnomalyDetectionResult detectAnomalies(List<Event> events, ValidationAnomalyDetectionConfig config) {
        long startTime = System.currentTimeMillis();
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        String algorithm = config.algorithm();
        double threshold = config.sensitivityThreshold();
        
        switch (algorithm.toLowerCase()) {
            case "statistical":
                anomalies.addAll(detectStatisticalAnomalies(events, config));
                break;
            case "frequency":
                anomalies.addAll(detectFrequencyAnomalies(events, config));
                break;
            case "contextual":
                anomalies.addAll(detectContextualAnomalies(events, config));
                break;
            case "collective":
                anomalies.addAll(detectCollectiveAnomalies(events, config));
                break;
            case "all":
            default:
                anomalies.addAll(detectStatisticalAnomalies(events, config));
                anomalies.addAll(detectFrequencyAnomalies(events, config));
                anomalies.addAll(detectContextualAnomalies(events, config));
                break;
        }
        
        // Remove duplicates and sort by severity
        anomalies = removeDuplicateAnomalies(anomalies);
        anomalies.sort((a, b) -> Double.compare(b.severityScore(), a.severityScore()));
        
        long evaluationTime = System.currentTimeMillis() - startTime;
        updateModelMetrics(anomalies, events.size());
        
        double overallScore = calculateOverallSeverity(anomalies);
        boolean hasAnomalies = !anomalies.isEmpty() && overallScore >= threshold;
        String status = hasAnomalies ? "ANOMALY_DETECTED" : "NORMAL";
        
        Map<String, Object> metadata = new HashMap<>(modelMetrics);
        metadata.put("algorithm", algorithm);
        metadata.put("threshold", threshold);
        metadata.put("anomalyCount", anomalies.size());
        metadata.put("evaluationTimeMs", evaluationTime);
        metadata.put("status", status);
        
        logger.info("Detected {} anomalies using {} algorithm", anomalies.size(), algorithm);
        return ValidationAnomalyDetectionResult.builder()
            .detectorId("default-detector")
            .anomalies(anomalies)
            .confidenceScore(overallScore)
            .status(status)
            .metadata(metadata)
            .build();
    }
    
    @Override
    public Map<String, Object> getModelMetrics() {
        return new HashMap<>(modelMetrics);
    }
    
    @Override
    public void updateBaseline(List<Event> events) {
        // Update baseline statistics for each event type
        Map<String, List<Event>> eventsByType = events.stream()
            .collect(Collectors.groupingBy(Event::getType));
        
        for (Map.Entry<String, List<Event>> entry : eventsByType.entrySet()) {
            String eventType = entry.getKey();
            List<Event> typeEvents = entry.getValue();
            
            BaselineStats stats = calculateBaselineStats(typeEvents);
            baselineStats.put(eventType, stats);
        }
        
        logger.info("Updated baseline statistics for {} event types", eventsByType.size());
    }
    
    @Override
    public List<String> getSupportedAlgorithms() {
        return Arrays.asList("statistical", "frequency", "contextual", "collective", "all");
    }
    
    private List<DetectedAnomaly> detectStatisticalAnomalies(List<Event> events, ValidationAnomalyDetectionConfig config) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        double threshold = config.sensitivityThreshold();
        
        // Group events by type for statistical analysis
        Map<String, List<Event>> eventsByType = events.stream()
            .collect(Collectors.groupingBy(Event::getType));
        
        for (Map.Entry<String, List<Event>> entry : eventsByType.entrySet()) {
            String eventType = entry.getKey();
            List<Event> typeEvents = entry.getValue();
            
            BaselineStats baseline = baselineStats.get(eventType);
            if (baseline == null) {
                // Calculate baseline from current events if not available
                baseline = calculateBaselineStats(typeEvents);
            }
            
            // Detect anomalies in numeric attributes
            for (Event event : typeEvents) {
                Map<String, Object> metadata = extractEventAttributes(event);
                if (metadata.isEmpty()) {
                    continue;
                }
                
                for (Map.Entry<String, Object> dataEntry : metadata.entrySet()) {
                    String attribute = dataEntry.getKey();
                    Object value = dataEntry.getValue();
                    
                    if (isNumeric(value)) {
                        double numericValue = parseNumericValue(value);
                        Double baselineMean = baseline.attributeMeans.get(attribute);
                        Double baselineStdDev = baseline.attributeStdDevs.get(attribute);
                        
                        if (baselineMean != null && baselineStdDev != null && baselineStdDev > 0) {
                            double zScore = Math.abs(numericValue - baselineMean) / baselineStdDev;
                            
                            if (zScore >= threshold) {
                                DetectedAnomaly anomaly = new DetectedAnomaly(
                                    event,
                                    AnomalyType.STATISTICAL,
                                    Math.min(1.0, zScore / 5.0), // Normalize severity
                                    String.format("Attribute '%s' value %.2f is %.2f standard deviations from baseline mean %.2f",
                                        attribute, numericValue, zScore, baselineMean),
                                    Map.of(
                                        "attribute", attribute,
                                        "value", numericValue,
                                        "baselineMean", baselineMean,
                                        "baselineStdDev", baselineStdDev,
                                        "zScore", zScore
                                    ),
                                    List.of(attribute)
                                );
                                anomalies.add(anomaly);
                            }
                        }
                    }
                }
            }
        }
        
        return anomalies;
    }
    
    private List<DetectedAnomaly> detectFrequencyAnomalies(List<Event> events, ValidationAnomalyDetectionConfig config) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        double threshold = config.sensitivityThreshold();
        
        // Calculate event frequencies in time windows
        Map<String, Map<Long, Integer>> eventTypeCounts = new HashMap<>();
        Map<String, List<Long>> eventTimestamps = new HashMap<>();
        
        // Group events by type and count occurrences in time windows
        for (Event event : events) {
            String eventType = event.getType();
            // Get the timestamp from the event's time interval (using start time)
            long window = event.getTime().getOccurrenceTime().start().toInstant().toEpochMilli() / 60000; // 1-minute windows
            
            eventTypeCounts.computeIfAbsent(eventType, k -> new HashMap<>())
                .merge(window, 1, Integer::sum);
            
            eventTimestamps.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(event.getTime().getOccurrenceTime().start().toInstant().toEpochMilli());
        }
        
        // Calculate baseline frequency statistics
        for (Map.Entry<String, Map<Long, Integer>> entry : eventTypeCounts.entrySet()) {
            String eventType = entry.getKey();
            Map<Long, Integer> counts = entry.getValue();
            
            double meanCount = counts.values().stream().mapToDouble(Integer::doubleValue).average().orElse(0.0);
            double stdDev = calculateStandardDeviation(counts.values().stream().map(Integer::doubleValue).collect(Collectors.toList()), meanCount);
            
            // Detect anomalous windows
            for (Map.Entry<Long, Integer> countEntry : counts.entrySet()) {
                Long window = countEntry.getKey();
                int count = countEntry.getValue();
                
                if (stdDev > 0) {
                    double zScore = Math.abs(count - meanCount) / stdDev;
                    
                    if (zScore >= threshold) {
                        // Create anomaly for each event in the anomalous window
                        for (Event event : events) {
                            if (event.getType().equals(eventType) && event.getTime().getOccurrenceTime().start().toInstant().toEpochMilli() / 60000 == window) {
                                DetectedAnomaly anomaly = new DetectedAnomaly(
                                    event,
                                    AnomalyType.COLLECTIVE,
                                    Math.min(1.0, zScore / 4.0),
                                    String.format("Event occurs in anomalous time window with %d events (%.2f standard deviations from mean %.2f)",
                                        count, zScore, meanCount),
                                    Map.of(
                                        "window", window,
                                        "count", count,
                                        "meanCount", meanCount,
                                        "zScore", zScore
                                    ),
                                    List.of("temporal_frequency")
                                );
                                anomalies.add(anomaly);
                            }
                        }
                    }
                }
            }
        }
        
        return anomalies;
    }
    
    private List<DetectedAnomaly> detectContextualAnomalies(List<Event> events, ValidationAnomalyDetectionConfig config) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // Group events by type
        Map<String, List<Event>> eventsByType = events.stream()
            .collect(Collectors.groupingBy(Event::getType));
        
        // For each event type, detect contextual anomalies
        for (Map.Entry<String, List<Event>> entry : eventsByType.entrySet()) {
            String eventType = entry.getKey();
            List<Event> typeEvents = entry.getValue();
            
            // Skip if not enough events for contextual analysis
            if (typeEvents.size() < 5) {
                continue;
            }
            
            // First pass: collect all numeric values
            for (Event event : typeEvents) {
                Map<String, Object> attributes = extractEventAttributes(event);
                if (attributes.isEmpty()) {
                    continue;
                }
                
                for (Map.Entry<String, Object> dataEntry : attributes.entrySet()) {
                    String attribute = dataEntry.getKey();
                    Object value = dataEntry.getValue();
                    
                    if (isNumeric(value)) {
                        double numericValue = parseNumericValue(value);
                        Double baselineMean = baselineStats.get(eventType).attributeMeans.get(attribute);
                        Double baselineStdDev = baselineStats.get(eventType).attributeStdDevs.get(attribute);
                        
                        if (baselineMean != null && baselineStdDev != null && baselineStdDev > 0) {
                            double zScore = Math.abs(numericValue - baselineMean) / baselineStdDev;
                            
                            if (zScore >= config.sensitivityThreshold()) {
                                DetectedAnomaly anomaly = new DetectedAnomaly(
                                    event,
                                    AnomalyType.STATISTICAL,
                                    Math.min(1.0, zScore / 5.0), // Normalize severity
                                    String.format("Attribute '%s' value %.2f is %.2f standard deviations from baseline mean %.2f",
                                        attribute, numericValue, zScore, baselineMean),
                                    Map.of(
                                        "attribute", attribute,
                                        "value", numericValue,
                                        "baselineMean", baselineMean,
                                        "baselineStdDev", baselineStdDev,
                                        "zScore", zScore
                                    ),
                                    List.of(attribute)
                                );
                                anomalies.add(anomaly);
                            }
                        }
                    }
                }
            }
        }
        
        return anomalies;
    }
    
    private List<DetectedAnomaly> detectCollectiveAnomalies(List<Event> events, ValidationAnomalyDetectionConfig config) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // Detect unusual patterns in event sequences
        // This is a simplified implementation - in practice, use more sophisticated sequence analysis
        
        Map<String, List<Event>> eventsByType = events.stream()
            .collect(Collectors.groupingBy(Event::getType));
        
        for (Map.Entry<String, List<Event>> entry : eventsByType.entrySet()) {
            String eventType = entry.getKey();
            List<Event> typeEvents = entry.getValue();
            
            if (typeEvents.size() < 5) continue; // Need minimum events for collective analysis
            
            // Sort by occurrence time
            typeEvents.sort(Comparator.comparing(e -> e.getTime().getOccurrenceTime().start().toInstant()));
            
            // Look for unusual bursts or gaps
            List<Long> intervals = new ArrayList<>();
            for (int i = 1; i < typeEvents.size(); i++) {
                long interval = typeEvents.get(i).getTime().getOccurrenceTime().start().toInstant().toEpochMilli() - 
                               typeEvents.get(i-1).getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
                intervals.add(interval);
            }
            
            if (!intervals.isEmpty()) {
                double meanInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
                double stdDev = calculateStandardDeviationLong(intervals, meanInterval);
                
                // Find unusual intervals
                for (int i = 0; i < intervals.size(); i++) {
                    long interval = intervals.get(i);
                    if (stdDev > 0) {
                        double zScore = Math.abs(interval - meanInterval) / stdDev;
                        
                        if (zScore >= config.threshold()) {
                            Event event = typeEvents.get(i + 1); // The event after the unusual interval
                            
                            DetectedAnomaly anomaly = new DetectedAnomaly(
                                event,
                                AnomalyType.COLLECTIVE,
                                Math.min(1.0, zScore / 4.0),
                                String.format("Event follows unusual time interval of %dms (%.2f standard deviations from mean %.2fms)",
                                    interval, zScore, meanInterval),
                                Map.of(
                                    "interval", interval,
                                    "meanInterval", meanInterval,
                                    "zScore", zScore,
                                    "eventType", eventType
                                ),
                                List.of("temporal_sequence")
                            );
                            anomalies.add(anomaly);
                        }
                    }
                }
            }
        }
        
        return anomalies;
    }
    
    private BaselineStats calculateBaselineStats(List<Event> events) {
        Map<String, List<Double>> numericAttributes = new HashMap<>();
        Map<String, List<Long>> timestampDeltas = new HashMap<>();
        
        // First pass: collect all numeric values
        for (Event event : events) {
            Map<String, Object> attributes = extractEventAttributes(event);
            if (attributes.isEmpty()) {
                continue;
            }
            
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String attribute = entry.getKey();
                Object value = entry.getValue();
                
                if (isNumeric(value)) {
                    double numValue = parseNumericValue(value);
                    numericAttributes.computeIfAbsent(attribute, k -> new ArrayList<>()).add(numValue);
                }
            }
        }
        
        // Second pass: calculate timestamp deltas for rate analysis
        events.sort(Comparator.comparing(e -> e.getTime().getOccurrenceTime().start().toInstant()));
        for (int i = 1; i < events.size(); i++) {
            long delta = events.get(i).getTime().getOccurrenceTime().start().toInstant().toEpochMilli() - 
                        events.get(i-1).getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
            timestampDeltas.computeIfAbsent("__timestamp_delta", k -> new ArrayList<>()).add(delta);
        }
        
        // Calculate statistics for numeric attributes
        Map<String, Double> attributeMeans = new HashMap<>();
        Map<String, Double> attributeStdDevs = new HashMap<>();
        
        for (Map.Entry<String, List<Double>> entry : numericAttributes.entrySet()) {
            String attribute = entry.getKey();
            List<Double> values = entry.getValue();
            
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double stdDev = calculateStandardDeviation(values, mean);
            
            attributeMeans.put(attribute, mean);
            attributeStdDevs.put(attribute, stdDev);
        }
        
        // Calculate rate statistics
        double meanRate = 0.0;
        double rateStdDev = 0.0;
        
        if (!timestampDeltas.isEmpty()) {
            List<Long> deltas = timestampDeltas.get("__timestamp_delta");
            double meanDelta = deltas.stream().mapToLong(Long::longValue).average().orElse(0.0);
            meanRate = meanDelta > 0 ? 1000.0 / meanDelta : 0.0; // events per second
            rateStdDev = calculateStandardDeviationLong(deltas, meanDelta);
        }
        
        BaselineStats stats = new BaselineStats();
        stats.attributeMeans.putAll(attributeMeans);
        stats.attributeStdDevs.putAll(attributeStdDevs);
        stats.meanRate = meanRate;
        stats.rateStdDev = rateStdDev;
        stats.eventCount = events.size();
        stats.lastUpdated = System.currentTimeMillis();
        return stats;
    }
    
    private Map<Long, List<Event>> groupEventsByTimeWindows(List<Event> events, long windowSize) {
        Map<Long, List<Event>> windows = new HashMap<>();
        
        for (Event event : events) {
            long timestamp = event.getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
            long windowStart = (timestamp / windowSize) * windowSize;
            
            windows.computeIfAbsent(windowStart, k -> new ArrayList<>()).add(event);
        }
        
        return windows;
    }
    
    private boolean isNumeric(Object value) {
        return value instanceof Number || 
               (value instanceof String && isNumericString((String) value));
    }
    
    private boolean isNumericString(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private double parseNumericValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            return Double.parseDouble((String) value);
        }
        throw new IllegalArgumentException("Cannot parse numeric value: " + value);
    }
    
    private double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.isEmpty()) return 0.0;
        
        double sumSquaredDiffs = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        
        return Math.sqrt(sumSquaredDiffs / values.size());
    }
    
    private double calculateStandardDeviationLong(List<Long> values, double mean) {
        if (values.isEmpty()) return 0.0;
        
        double sumSquaredDiffs = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        
        return Math.sqrt(sumSquaredDiffs / values.size());
    }
    
    private List<DetectedAnomaly> removeDuplicateAnomalies(List<DetectedAnomaly> anomalies) {
        // Simple deduplication based on event and anomaly type
        Map<String, DetectedAnomaly> uniqueAnomalies = new HashMap<>();
        
        for (DetectedAnomaly anomaly : anomalies) {
            String key = anomaly.event().getId() + "_" + anomaly.type();
            DetectedAnomaly existing = uniqueAnomalies.get(key);
            
            if (existing == null || anomaly.severityScore() > existing.severityScore()) {
                uniqueAnomalies.put(key, anomaly);
            }
        }
        
        return new ArrayList<>(uniqueAnomalies.values());
    }
    
    private double calculateOverallSeverity(List<DetectedAnomaly> anomalies) {
        if (anomalies.isEmpty()) {
            return 0.0;
        }
        return anomalies.stream()
            .mapToDouble(DetectedAnomaly::severityScore)
            .average()
            .orElse(0.0);
    }

    private void updateModelMetrics(List<DetectedAnomaly> anomalies, int totalEvents) {
        modelMetrics.put("totalAnomalies", anomalies.size());
        modelMetrics.put("totalEvents", totalEvents);
        modelMetrics.put("anomalyRate", totalEvents > 0 ? (double) anomalies.size() / totalEvents : 0.0);
        modelMetrics.put("lastAnalysisTime", System.currentTimeMillis());
        
        // Calculate severity distribution
        Map<AnomalyType, Long> typeDistribution = anomalies.stream()
            .collect(Collectors.groupingBy(DetectedAnomaly::type, Collectors.counting()));
        modelMetrics.put("anomalyTypeDistribution", typeDistribution);
        
        double averageSeverity = anomalies.stream()
            .mapToDouble(DetectedAnomaly::severityScore)
            .average().orElse(0.0);
        modelMetrics.put("averageSeverity", averageSeverity);
    }
    
    private Map<String, Object> extractEventAttributes(Event event) {
        Map<String, Object> attributes = new HashMap<>();
        
        // Add relevant headers
        for (String headerName : Arrays.asList("source", "severity", "environment", "category")) {
            String value = event.getHeader(headerName);
            if (value != null) {
                attributes.put(headerName, value);
            }
        }
        
        // Add relevant payload fields
        for (String fieldName : Arrays.asList("value", "count", "duration", "status")) {
            Object value = event.getPayload(fieldName);
            if (value != null) {
                attributes.put(fieldName, value);
            }
        }
        
        return attributes;
    }
    
    private static class BaselineStats {
        Map<String, Double> attributeMeans = new HashMap<>();
        Map<String, Double> attributeStdDevs = new HashMap<>();
        double meanRate = 0.0;
        double rateStdDev = 0.0;
        int eventCount = 0;
        long lastUpdated = 0;
    }
}