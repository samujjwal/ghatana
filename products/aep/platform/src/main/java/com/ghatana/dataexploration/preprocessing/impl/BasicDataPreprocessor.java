package com.ghatana.dataexploration.preprocessing.impl;

import com.ghatana.dataexploration.model.CorrelatedEventGroup;
import com.ghatana.dataexploration.model.EventStreamStatistics;
import com.ghatana.dataexploration.model.NormalizedEvent;
import com.ghatana.dataexploration.model.PreprocessedEventBatch;
import com.ghatana.dataexploration.model.PreprocessingConfig;
import com.ghatana.dataexploration.model.ExplorationEvent;
import com.ghatana.dataexploration.model.TemporalFeatures;
import com.ghatana.dataexploration.preprocessing.DataPreprocessor;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Basic implementation of DataPreprocessor for pattern exploration.
 * 
 * Day 28 Implementation: In-memory preprocessing with temporal feature extraction and correlation analysis.
 */
public class BasicDataPreprocessor implements DataPreprocessor {
    
    @Override
    public Promise<PreprocessedEventBatch> preprocessEvents(List<ExplorationEvent> events, PreprocessingConfig config) {
        try {
            // Normalize events
            List<NormalizedEvent> normalizedEvents = normalizeEventsSync(events);
            
            // Extract temporal features if configured
            Map<String, TemporalFeatures> temporalFeatures = Map.of();
            if (config.isExtractTemporalFeatures()) {
                temporalFeatures = extractTemporalFeaturesSync(events, config.getTimeWindow());
            }
            
            // Calculate statistics
            EventStreamStatistics statistics = calculateStreamStatisticsSync(events, config.getTimeWindow());
            
            // Create batch
            PreprocessedEventBatch batch = new PreprocessedEventBatch(
                    normalizedEvents,
                    temporalFeatures,
                    statistics,
                    Instant.now(),
                    UUID.randomUUID().toString()
            );
            
            return Promise.of(batch);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Map<String, TemporalFeatures>> extractTemporalFeatures(List<ExplorationEvent> events, Duration timeWindow) {
        try {
            Map<String, TemporalFeatures> features = extractTemporalFeaturesSync(events, timeWindow);
            return Promise.of(features);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<Set<CorrelatedEventGroup>> findCorrelatedEventTypes(List<ExplorationEvent> events, double minConfidence) {
        try {
            Set<CorrelatedEventGroup> correlatedGroups = findCorrelatedEventTypesSync(events, minConfidence);
            return Promise.of(correlatedGroups);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<List<NormalizedEvent>> normalizeEvents(List<ExplorationEvent> events) {
        try {
            List<NormalizedEvent> normalized = normalizeEventsSync(events);
            return Promise.of(normalized);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    @Override
    public Promise<EventStreamStatistics> calculateStreamStatistics(List<ExplorationEvent> events, Duration analysisWindow) {
        try {
            EventStreamStatistics statistics = calculateStreamStatisticsSync(events, analysisWindow);
            return Promise.of(statistics);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    // Synchronous helper methods
    
    private List<NormalizedEvent> normalizeEventsSync(List<ExplorationEvent> events) {
        return events.stream()
                .map(event -> new NormalizedEvent(
                        event.getId(),
                        event.getType(),
                        event.getTimestamp(),
                        normalizeProperties(event.getProperties()),
                        event.getTenantId(),
                        1.0
                ))
                .collect(Collectors.toList());
    }
    
    private Map<String, Object> normalizeProperties(Map<String, Object> properties) {
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            Object value = entry.getValue();
            
            // Simple type normalization
            if (value instanceof String) {
                normalized.put(key, ((String) value).toLowerCase().trim());
            } else if (value instanceof Number) {
                normalized.put(key, ((Number) value).doubleValue());
            } else {
                normalized.put(key, value != null ? value.toString() : null);
            }
        }
        return normalized;
    }
    
    private Map<String, TemporalFeatures> extractTemporalFeaturesSync(List<ExplorationEvent> events, Duration timeWindow) {
        Map<String, List<ExplorationEvent>> eventsByType = events.stream()
                .collect(Collectors.groupingBy(ExplorationEvent::getType));
        
        Map<String, TemporalFeatures> features = new HashMap<>();
        
        for (Map.Entry<String, List<ExplorationEvent>> entry : eventsByType.entrySet()) {
            String eventType = entry.getKey();
            List<ExplorationEvent> typeEvents = entry.getValue();
            
            if (typeEvents.size() < 2) {
                continue; // Need at least 2 events to calculate intervals
            }
            
            // Sort by timestamp
            typeEvents.sort(Comparator.comparing(ExplorationEvent::getTimestamp));
            
            // Calculate intervals
            List<Duration> intervals = new ArrayList<>();
            for (int i = 1; i < typeEvents.size(); i++) {
                Duration interval = Duration.between(
                        typeEvents.get(i - 1).getTimestamp(),
                        typeEvents.get(i).getTimestamp()
                );
                intervals.add(interval);
            }
            
            // Calculate statistics
            Duration averageInterval = calculateAverageDuration(intervals);
            Duration medianInterval = calculateMedianDuration(intervals);
            Instant firstOccurrence = typeEvents.get(0).getTimestamp();
            Instant lastOccurrence = typeEvents.get(typeEvents.size() - 1).getTimestamp();
            Duration totalSpan = Duration.between(firstOccurrence, lastOccurrence);
            double frequency = totalSpan.toMinutes() > 0 ? typeEvents.size() / (double) totalSpan.toMinutes() : 0;
            double burstiness = calculateBurstiness(intervals);
            
            TemporalFeatures temporalFeatures = new TemporalFeatures(
                    eventType,
                    averageInterval,
                    medianInterval,
                    typeEvents.size(),
                    firstOccurrence,
                    lastOccurrence,
                    frequency,
                    Map.of(), // Periodicity scores - simplified for now
                    burstiness
            );
            
            features.put(eventType, temporalFeatures);
        }
        
        return features;
    }
    
    private Duration calculateAverageDuration(List<Duration> durations) {
        if (durations.isEmpty()) {
            return Duration.ZERO;
        }
        long totalMillis = durations.stream().mapToLong(Duration::toMillis).sum();
        return Duration.ofMillis(totalMillis / durations.size());
    }
    
    private Duration calculateMedianDuration(List<Duration> durations) {
        if (durations.isEmpty()) {
            return Duration.ZERO;
        }
        List<Duration> sorted = durations.stream().sorted().collect(Collectors.toList());
        int middle = sorted.size() / 2;
        return sorted.get(middle);
    }
    
    private double calculateBurstiness(List<Duration> intervals) {
        if (intervals.size() < 2) {
            return 0.0;
        }
        
        double mean = intervals.stream().mapToLong(Duration::toMillis).average().orElse(0.0);
        double variance = intervals.stream()
                .mapToDouble(interval -> Math.pow(interval.toMillis() - mean, 2))
                .average().orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        return mean > 0 ? stdDev / mean : 0.0; // Coefficient of variation
    }
    
    private Set<CorrelatedEventGroup> findCorrelatedEventTypesSync(List<ExplorationEvent> events, double minConfidence) {
        // Simple correlation analysis based on co-occurrence in time windows
        Set<CorrelatedEventGroup> correlatedGroups = new HashSet<>();
        
        Map<String, List<ExplorationEvent>> eventsByType = events.stream()
                .collect(Collectors.groupingBy(ExplorationEvent::getType));
        
        List<String> eventTypes = new ArrayList<>(eventsByType.keySet());
        
        // Simple pairwise correlation analysis
        for (int i = 0; i < eventTypes.size(); i++) {
            for (int j = i + 1; j < eventTypes.size(); j++) {
                String type1 = eventTypes.get(i);
                String type2 = eventTypes.get(j);
                
                double correlation = calculatePairwiseCorrelation(
                        eventsByType.get(type1), 
                        eventsByType.get(type2), 
                        Duration.ofMinutes(5)
                );
                
                if (correlation >= minConfidence) {
                    CorrelatedEventGroup group = new CorrelatedEventGroup(
                            Set.of(type1, type2),
                            correlation,
                            0.1, // Simplified support calculation
                            Map.of(type1 + "-" + type2, correlation),
                            List.of(new CorrelatedEventGroup.CorrelationRule(type1, type2, correlation, 1.0)),
                            UUID.randomUUID().toString()
                    );
                    correlatedGroups.add(group);
                }
            }
        }
        
        return correlatedGroups;
    }
    
    private double calculatePairwiseCorrelation(List<ExplorationEvent> events1, 
                                              List<ExplorationEvent> events2, 
                                              Duration window) {
        if (events1.isEmpty() || events2.isEmpty()) {
            return 0.0;
        }
        
        // Count co-occurrences within time window
        int coOccurrences = 0;
        for (ExplorationEvent event1 : events1) {
            for (ExplorationEvent event2 : events2) {
                Duration gap = Duration.between(event1.getTimestamp(), event2.getTimestamp()).abs();
                if (gap.compareTo(window) <= 0) {
                    coOccurrences++;
                    break; // Count each event1 only once
                }
            }
        }
        
        // Simple correlation: co-occurrences / min(events1.size(), events2.size())
        return (double) coOccurrences / Math.min(events1.size(), events2.size());
    }
    
    private EventStreamStatistics calculateStreamStatisticsSync(List<ExplorationEvent> events, Duration analysisWindow) {
        if (events.isEmpty()) {
            return EventStreamStatistics.builder()
                    .totalEvents(0)
                    .uniqueEventTypes(0)
                    .timeSpan(Duration.ZERO)
                    .averageFrequency(0.0)
                    .eventTypeCounts(Map.of())
                    .eventTypeFrequencies(Map.of())
                    .windowStart(Instant.now())
                    .windowEnd(Instant.now())
                    .entropy(0.0)
                    .build();
        }
        
        // Sort events by timestamp
        List<ExplorationEvent> sortedEvents = events.stream()
                .sorted(Comparator.comparing(ExplorationEvent::getTimestamp))
                .collect(Collectors.toList());
        
        Instant windowStart = sortedEvents.get(0).getTimestamp();
        Instant windowEnd = sortedEvents.get(sortedEvents.size() - 1).getTimestamp();
        Duration timeSpan = Duration.between(windowStart, windowEnd);
        
        // Count event types
        Map<String, Long> eventTypeCounts = events.stream()
                .collect(Collectors.groupingBy(ExplorationEvent::getType, Collectors.counting()));
        
        // Calculate frequencies
        double totalMinutes = Math.max(1.0, timeSpan.toMinutes());
        Map<String, Double> eventTypeFrequencies = eventTypeCounts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() / totalMinutes
                ));
        
        // Calculate entropy
        double entropy = calculateEntropy(eventTypeCounts, events.size());
        
        return EventStreamStatistics.builder()
                .totalEvents(events.size())
                .uniqueEventTypes(eventTypeCounts.size())
                .timeSpan(timeSpan)
                .averageFrequency(events.size() / totalMinutes)
                .eventTypeCounts(eventTypeCounts)
                .eventTypeFrequencies(eventTypeFrequencies)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .entropy(entropy)
                .build();
    }
    
    private double calculateEntropy(Map<String, Long> eventTypeCounts, int totalEvents) {
        if (totalEvents == 0) {
            return 0.0;
        }
        
        double entropy = 0.0;
        for (long count : eventTypeCounts.values()) {
            double probability = (double) count / totalEvents;
            if (probability > 0) {
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }
        return entropy;
    }
}