package com.ghatana.validation.ai.detectors;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.validation.ai.AIPatternDetectionService.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects frequency-based patterns in event streams.
 * Consolidated from event-core frequency analysis capabilities.
 
 *
 * @doc.type class
 * @doc.purpose Frequency pattern detector
 * @doc.layer core
 * @doc.pattern Component
*/
public class FrequencyPatternDetector implements PatternDetector {
    
    @Override
    public List<DetectedPattern> detect(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Detect event type frequency patterns
        patterns.addAll(detectEventTypeFrequencyPatterns(events, config));
        
        // Detect temporal frequency patterns
        patterns.addAll(detectTemporalFrequencyPatterns(events, config));
        
        // Detect attribute value frequency patterns
        patterns.addAll(detectAttributeFrequencyPatterns(events, config));
        
        return patterns;
    }
    
    @Override
    public PatternType getPatternType() {
        return PatternType.FREQUENCY;
    }
    
    @Override
    public Map<String, String> getConfigurationRequirements() {
        Map<String, String> requirements = new HashMap<>();
        requirements.put("minFrequency", "Minimum frequency threshold (default: 5)");
        requirements.put("timeWindowMs", "Time window for frequency analysis in milliseconds (default: 3600000)");
        requirements.put("frequencyThreshold", "Frequency deviation threshold for anomaly detection (default: 2.0)");
        requirements.put("topAttributesCount", "Number of top attributes to analyze (default: 10)");
        return requirements;
    }
    
    private List<DetectedPattern> detectEventTypeFrequencyPatterns(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        int minFrequency = getConfigValue(config, "minFrequency", 5);
        
        // Count event type frequencies
        Map<String, Long> typeFrequencies = events.stream()
            .collect(Collectors.groupingBy(Event::getType, Collectors.counting()));
        
        // Calculate statistics
        double meanFrequency = typeFrequencies.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(typeFrequencies.values(), meanFrequency);
        
        // Identify frequent event types
        for (Map.Entry<String, Long> entry : typeFrequencies.entrySet()) {
            String eventType = entry.getKey();
            Long frequency = entry.getValue();
            
            if (frequency >= minFrequency) {
                double zScore = (frequency - meanFrequency) / Math.max(stdDev, 1.0);
                
                if (Math.abs(zScore) >= 1.5) { // Significant deviation
                    List<Event> matchingEvents = events.stream()
                        .filter(e -> e.getType().equals(eventType))
                        .collect(Collectors.toList());
                    
                    DetectedPattern pattern = new DetectedPattern(
                        UUID.randomUUID().toString(),
                        "High Frequency Event Type",
                        String.format("Event type '%s' occurs %d times (%.2f standard deviations from mean)", 
                            eventType, frequency, zScore),
                        Math.min(1.0, Math.abs(zScore) / 3.0), // Normalize confidence
                        PatternType.FREQUENCY,
                        Map.of(
                            "eventType", eventType,
                            "frequency", frequency,
                            "zScore", zScore,
                            "meanFrequency", meanFrequency,
                            "standardDeviation", stdDev
                        ),
                        matchingEvents,
                        System.currentTimeMillis()
                    );
                    
                    if (pattern.confidence() >= config.confidenceThreshold()) {
                        patterns.add(pattern);
                    }
                }
            }
        }
        
        return patterns;
    }
    
    private List<DetectedPattern> detectTemporalFrequencyPatterns(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        long timeWindowMs = getConfigValue(config, "timeWindowMs", 3600000L); // 1 hour default
        
        // Sort events by occurrence time
        List<Event> sortedEvents = events.stream()
            .sorted(Comparator.comparing(e -> e.getTime().getOccurrenceTime().start().toInstant()))
            .collect(Collectors.toList());
        
        if (sortedEvents.isEmpty()) return patterns;
        
        // Create time windows and count events
        long startTime = sortedEvents.get(0).getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
        long endTime = sortedEvents.get(sortedEvents.size() - 1).getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
        
        Map<Long, Long> windowCounts = new HashMap<>();
        
        for (long windowStart = startTime; windowStart < endTime; windowStart += timeWindowMs) {
            long windowEnd = windowStart + timeWindowMs;
            long finalWindowStart = windowStart;
            long count = sortedEvents.stream()
                .filter(e -> {
                    long timestamp = e.getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
                    return timestamp >= finalWindowStart && timestamp < windowEnd;
                })
                .count();
            
            windowCounts.put(windowStart, count);
        }
        
        // Analyze frequency patterns in time windows
        double meanCount = windowCounts.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(windowCounts.values(), meanCount);
        
        double threshold = getConfigValue(config, "frequencyThreshold", 2.0);
        
        for (Map.Entry<Long, Long> entry : windowCounts.entrySet()) {
            long windowStart = entry.getKey();
            long count = entry.getValue();
            double zScore = (count - meanCount) / Math.max(stdDev, 1.0);
            
            if (Math.abs(zScore) >= threshold) {
                List<Event> windowEvents = findEventsInWindow(sortedEvents, windowStart, windowStart + timeWindowMs);
                
                DetectedPattern pattern = new DetectedPattern(
                    UUID.randomUUID().toString(),
                    zScore > 0 ? "High Frequency Time Window" : "Low Frequency Time Window",
                    String.format("Time window starting at %s has %d events (%.2f standard deviations from mean)", 
                        Instant.ofEpochMilli(windowStart), count, zScore),
                    Math.min(1.0, Math.abs(zScore) / 4.0),
                    PatternType.FREQUENCY,
                    Map.of(
                        "windowStart", windowStart,
                        "windowEnd", windowStart + timeWindowMs,
                        "eventCount", count,
                        "zScore", zScore,
                        "meanCount", meanCount,
                        "isSpike", zScore > 0
                    ),
                    windowEvents,
                    System.currentTimeMillis()
                );
                
                if (pattern.confidence() >= config.confidenceThreshold()) {
                    patterns.add(pattern);
                }
            }
        }
        
        return patterns;
    }
    
    private List<DetectedPattern> detectAttributeFrequencyPatterns(List<Event> events, PatternAnalysisConfig config) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        int topAttributesCount = getConfigValue(config, "topAttributesCount", 10);
        int minFrequency = getConfigValue(config, "minFrequency", 5);
        
        // Collect all attribute values and their frequencies
        Map<String, Map<Object, Long>> attributeValueFrequencies = new HashMap<>();
        
        // First pass: collect all payload attribute values and their counts
        for (Event event : events) {
            // Get all payload field names (implementation dependent)
            // For now, we'll use a predefined set of known payload fields
            // In a real implementation, you might need to use reflection or another mechanism
            // to get all payload field names
            List<String> payloadFields = List.of("type", "severity", "message"); // Example fields
            
            for (String field : payloadFields) {
                Object value = event.getPayload(field);
                if (value != null) {
                    attributeValueFrequencies
                        .computeIfAbsent(field, k -> new HashMap<>())
                        .merge(value, 1L, Long::sum);
                }
            }
            
            // Also check headers if needed
            String[] headerFields = {"correlationId", "causationId", "stream"};
            for (String field : headerFields) {
                String value = event.getHeader(field);
                if (value != null) {
                    attributeValueFrequencies
                        .computeIfAbsent("header." + field, k -> new HashMap<>())
                        .merge(value, 1L, Long::sum);
                }
            }
        }
        
        // Analyze frequency for each attribute
        for (Map.Entry<String, Map<Object, Long>> attrEntry : attributeValueFrequencies.entrySet()) {
            String attribute = attrEntry.getKey();
            Map<Object, Long> valueCounts = attrEntry.getValue();
            
            // Get top N most frequent values for this attribute
            valueCounts.entrySet().stream()
                .sorted(Map.Entry.<Object, Long>comparingByValue().reversed())
                .limit(topAttributesCount)
                .forEach(entry -> {
                    Object value = entry.getKey();
                    Long count = entry.getValue();
                    double frequency = (double) count / events.size();
                    
                    if (count >= minFrequency) {
                        List<Event> matchingEvents = events.stream()
                            .filter(e -> {
                                // Check if this is a header or payload field
                                if (attribute.startsWith("header.")) {
                                    String headerName = attribute.substring("header.".length());
                                    return value.equals(e.getHeader(headerName));
                                } else {
                                    return value.equals(e.getPayload(attribute));
                                }
                            })
                            .collect(Collectors.toList());
                            
                        DetectedPattern pattern = new DetectedPattern(
                            UUID.randomUUID().toString(),
                            "Frequent Attribute Value",
                            String.format("Value '%s' for attribute '%s' appears in %d events (%.1f%%)", 
                                value, attribute, count, frequency * 100),
                            Math.min(1.0, frequency * 2), // Higher frequency = higher confidence
                            PatternType.FREQUENCY,
                            Map.of(
                                "attribute", attribute,
                                "value", String.valueOf(value),
                                "count", count,
                                "frequency", frequency
                            ),
                            matchingEvents,
                            System.currentTimeMillis()
                        );
                        
                        if (pattern.confidence() >= config.confidenceThreshold()) {
                            patterns.add(pattern);
                        }
                    }
                });
        }
        
        return patterns;
    }
    
    private double calculateStandardDeviation(Collection<Long> values, double mean) {
        if (values.isEmpty()) return 0.0;
        
        double sumSquaredDiffs = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        
        return Math.sqrt(sumSquaredDiffs / values.size());
    }
    
    private List<Event> findEventsInWindow(List<Event> events, long windowStart, long windowEnd) {
        return events.stream()
            .filter(e -> {
                long timestamp = e.getTime().getOccurrenceTime().start().toInstant().toEpochMilli();
                return timestamp >= windowStart && timestamp < windowEnd;
            })
            .collect(Collectors.toList());
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getConfigValue(PatternAnalysisConfig config, String key, T defaultValue) {
        return (T) config.algorithmParameters().getOrDefault(key, defaultValue);
    }
}