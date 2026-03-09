package com.ghatana.validation.ai.detectors;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.validation.ai.AIPatternDetectionService.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects correlation patterns between event attributes.
 * Consolidated from event-core correlation analysis capabilities.
 
 *
 * @doc.type class
 * @doc.purpose Correlation pattern detector
 * @doc.layer core
 * @doc.pattern Component
*/
public class CorrelationPatternDetector implements PatternDetector {
    
    @Override
    public List<DetectedPattern> detect(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Detect attribute correlations
        patterns.addAll(detectAttributeCorrelations(events, config));
        
        // Detect event type correlations
        patterns.addAll(detectEventTypeCorrelations(events, config));
        
        return patterns;
    }
    
    @Override
    public PatternType getPatternType() {
        return PatternType.CORRELATION;
    }
    
    @Override
    public Map<String, String> getConfigurationRequirements() {
        Map<String, String> requirements = new HashMap<>();
        requirements.put("minCorrelation", "Minimum correlation coefficient to consider significant (default: 0.7)");
        requirements.put("minSampleSize", "Minimum number of events required for correlation analysis (default: 20)");
        requirements.put("maxAttributes", "Maximum number of attributes to analyze for correlations (default: 50)");
        requirements.put("correlationWindow", "Time window for correlation analysis in milliseconds (default: 300000)");
        return requirements;
    }
    
    private List<DetectedPattern> detectAttributeCorrelations(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        double minCorrelation = getConfigValue(config, "minCorrelation", 0.7);
        int minSampleSize = getConfigValue(config, "minSampleSize", 20);
        int maxAttributes = getConfigValue(config, "maxAttributes", 50);
        
        if (events.size() < minSampleSize) {
            return patterns;
        }
        
        // Extract numeric attributes
        Map<String, List<Double>> numericAttributes = extractNumericAttributes(events, maxAttributes);
        
        // Calculate correlations between all pairs of numeric attributes
        List<String> attributeNames = new ArrayList<>(numericAttributes.keySet());
        
        for (int i = 0; i < attributeNames.size(); i++) {
            for (int j = i + 1; j < attributeNames.size(); j++) {
                String attr1 = attributeNames.get(i);
                String attr2 = attributeNames.get(j);
                
                List<Double> values1 = numericAttributes.get(attr1);
                List<Double> values2 = numericAttributes.get(attr2);
                
                if (values1.size() >= minSampleSize && values2.size() >= minSampleSize) {
                    double correlation = calculatePearsonCorrelation(values1, values2);
                    
                    if (Math.abs(correlation) >= minCorrelation) {
                        List<Event> correlatedEvents = findCorrelatedEvents(events, attr1, attr2);
                        
                        DetectedPattern pattern = new DetectedPattern(
                            UUID.randomUUID().toString(),
                            correlation > 0 ? "Positive Attribute Correlation" : "Negative Attribute Correlation",
                            String.format("Strong %s correlation (%.3f) between attributes '%s' and '%s'",
                                correlation > 0 ? "positive" : "negative", correlation, attr1, attr2),
                            Math.min(1.0, Math.abs(correlation)),
                            PatternType.CORRELATION,
                            Map.of(
                                "attribute1", attr1,
                                "attribute2", attr2,
                                "correlationCoefficient", correlation,
                                "sampleSize", Math.min(values1.size(), values2.size()),
                                "correlationType", correlation > 0 ? "positive" : "negative"
                            ),
                            correlatedEvents,
                            System.currentTimeMillis()
                        );
                        
                        if (pattern.confidence() >= config.confidenceThreshold()) {
                            patterns.add(pattern);
                        }
                    }
                }
            }
        }
        
        return patterns;
    }
    
    private List<DetectedPattern> detectEventTypeCorrelations(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        long correlationWindow = getConfigValue(config, "correlationWindow", 300000L); // 5 minutes
        double minCorrelation = getConfigValue(config, "minCorrelation", 0.7);
        int minSampleSize = getConfigValue(config, "minSampleSize", 20);
        
        // Group events by time windows
        Map<Long, List<Event>> timeWindows = groupEventsByTimeWindows(events, correlationWindow);
        
        if (timeWindows.size() < minSampleSize) {
            return patterns;
        }
        
        // Get unique event types
        Set<String> eventTypes = events.stream().map(Event::getType).collect(Collectors.toSet());
        List<String> typeList = new ArrayList<>(eventTypes);
        
        // Calculate event type co-occurrence correlations
        for (int i = 0; i < typeList.size(); i++) {
            for (int j = i + 1; j < typeList.size(); j++) {
                String type1 = typeList.get(i);
                String type2 = typeList.get(j);
                
                List<Double> type1Occurrences = new ArrayList<>();
                List<Double> type2Occurrences = new ArrayList<>();
                
                for (List<Event> windowEvents : timeWindows.values()) {
                    long type1Count = windowEvents.stream().filter(e -> e.getType().equals(type1)).count();
                    long type2Count = windowEvents.stream().filter(e -> e.getType().equals(type2)).count();
                    
                    type1Occurrences.add((double) type1Count);
                    type2Occurrences.add((double) type2Count);
                }
                
                if (type1Occurrences.size() >= minSampleSize) {
                    double correlation = calculatePearsonCorrelation(type1Occurrences, type2Occurrences);
                    
                    if (Math.abs(correlation) >= minCorrelation) {
                        List<Event> correlatedEvents = findCorrelatedEvents(events, type1, type2);
                        
                        DetectedPattern pattern = new DetectedPattern(
                            UUID.randomUUID().toString(),
                            "Event Type Correlation",
                            String.format("Event types '%s' and '%s' show %s correlation (%.3f) in occurrence patterns",
                                type1, type2, correlation > 0 ? "positive" : "negative", correlation),
                            Math.min(1.0, Math.abs(correlation)),
                            PatternType.CORRELATION,
                            Map.of(
                                "eventType1", type1,
                                "eventType2", type2,
                                "correlationCoefficient", correlation,
                                "windowSize", correlationWindow,
                                "sampleWindows", type1Occurrences.size(),
                                "correlationType", correlation > 0 ? "co-occurrence" : "mutual-exclusion"
                            ),
                            correlatedEvents,
                            System.currentTimeMillis()
                        );
                        
                        if (pattern.confidence() >= config.confidenceThreshold()) {
                            patterns.add(pattern);
                        }
                    }
                }
            }
        }
        
        return patterns;
    }
    
    private Map<String, List<Double>> extractNumericAttributes(List<Event> events, int maxAttributes) {
        Map<String, List<Double>> numericAttributes = new HashMap<>();
        
        // Use a predefined set of numeric attributes that we expect in the payload
        // In a real implementation, you might want to make this configurable
        List<String> attributesToAnalyze = List.of("amount", "count", "duration", "size", "value");
        
        // Filter to only include attributes that exist in the payload
        List<String> validAttributes = attributesToAnalyze.stream()
            .filter(attr -> events.stream().anyMatch(e -> e.getPayload(attr) != null))
            .limit(maxAttributes)
            .collect(Collectors.toList());
        
        for (String attributeName : validAttributes) {
            List<Double> values = new ArrayList<>();
            
            for (Event event : events) {
                Object value = event.getPayload(attributeName);
                if (value != null) {
                    try {
                        double numericValue = parseNumericValue(value);
                        values.add(numericValue);
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }
            
            if (values.size() >= 10) { // Minimum threshold for meaningful correlation
                numericAttributes.put(attributeName, values);
            }
        }
        
        return numericAttributes;
    }
    
    private double parseNumericValue(Object value) throws NumberFormatException {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            return Double.parseDouble((String) value);
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? 1.0 : 0.0;
        } else {
            throw new NumberFormatException("Cannot convert to numeric value: " + value.getClass());
        }
    }
    
    private double calculatePearsonCorrelation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.isEmpty()) {
            return 0.0;
        }
        
        int n = x.size();
        
        // Calculate means
        double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // Calculate correlation coefficient
        double numerator = 0.0;
        double sumXSquared = 0.0;
        double sumYSquared = 0.0;
        
        for (int i = 0; i < n; i++) {
            double xDiff = x.get(i) - meanX;
            double yDiff = y.get(i) - meanY;
            
            numerator += xDiff * yDiff;
            sumXSquared += xDiff * xDiff;
            sumYSquared += yDiff * yDiff;
        }
        
        // Calculate the denominator
        double denominator = Math.sqrt(sumXSquared * sumYSquared);
        
        // Avoid division by zero
        if (denominator == 0.0) {
            return 0.0;
        }
        
        return numerator / denominator;
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getConfigValue(PatternAnalysisConfig config, String key, T defaultValue) {
        return (T) config.algorithmParameters().getOrDefault(key, defaultValue);
    }
    
    /**
     * Finds events that have both specified attributes with non-null values.
     */
    private List<Event> findCorrelatedEvents(List<Event> events, String attr1, String attr2) {
        return events.stream()
            .filter(event -> {
                Object val1 = event.getPayload(attr1);
                Object val2 = event.getPayload(attr2);
                return val1 != null && val2 != null;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Groups events into time windows of the specified size in milliseconds.
     */
    private Map<Long, List<Event>> groupEventsByTimeWindows(List<Event> events, long windowSizeMs) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // Find the minimum timestamp to align windows
        long minTime = events.stream()
            .mapToLong(e -> e.getTime().getOccurrenceTime().start().toInstant().toEpochMilli())
            .min()
            .orElse(0L);
            
        // Group events into time windows
        return events.stream()
            .collect(Collectors.groupingBy(
                e -> {
                    long timestamp = e.getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
                    return (timestamp - minTime) / windowSizeMs;
                },
                Collectors.toList()
            ));
    }
}