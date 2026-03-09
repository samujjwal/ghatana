/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.statistics;

import com.ghatana.aep.preprocessing.normalization.CanonicalEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Collects real-time statistics from event streams.
 * 
 * <p><b>Purpose</b><br>
 * Maintains sliding window statistics for event streams. Tracks frequencies,
 * co-occurrences, inter-arrival times, and attribute distributions.
 * 
 * <p><b>Window Management</b><br>
 * Uses configurable time windows (default: 5 minutes). Older events are
 * automatically expired from statistics to maintain memory efficiency.
 * 
 * <p><b>Thread Safety</b><br>
 * All collections use concurrent data structures for safe multi-threaded access.
 * Statistics are eventually consistent within the window period.
 * 
 * <p><b>Memory Management</b><br>
 * <ul>
 *   <li>Bounded window size (time-based)</li>
 *   <li>Automatic cleanup of expired data</li>
 *   <li>Top-K value tracking for high-cardinality attributes</li>
 *   <li>Periodic flushing to learning system</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Real-time event stream statistics
 * @doc.layer product
 * @doc.pattern Service
 */
public class StatisticsCollector {
    
    private final Duration windowSize;
    private final int maxCardinalityTracking;
    
    // Event type tracking
    private final Map<String, Long> eventTypeFrequency = new ConcurrentHashMap<>();
    private final Map<String, List<Instant>> eventTimestamps = new ConcurrentHashMap<>();
    
    // Co-occurrence tracking (which events happen together)
    private final Map<String, Map<String, Long>> coOccurrence = new ConcurrentHashMap<>();
    
    // Attribute statistics
    private final Map<String, Map<String, Map<String, Long>>> attributeValueCounts = new ConcurrentHashMap<>();
    
    // Inter-arrival time tracking
    private final Map<String, List<Long>> interArrivalTimes = new ConcurrentHashMap<>();

    public StatisticsCollector(Duration windowSize, int maxCardinalityTracking) {
        this.windowSize = windowSize;
        this.maxCardinalityTracking = maxCardinalityTracking;
    }

    /**
     * Records a canonical event for statistics.
     */
    public void record(CanonicalEvent event) {
        String eventType = event.eventType();
        Instant timestamp = event.timestamp();
        
        // Update frequency
        eventTypeFrequency.merge(eventType, 1L, Long::sum);
        
        // Record timestamp for inter-arrival calculation
        eventTimestamps.computeIfAbsent(eventType, k -> new ArrayList<>()).add(timestamp);
        
        // Track attribute values
        if (event.attributes() != null) {
            for (Map.Entry<String, Object> attr : event.attributes().entrySet()) {
                String attrName = attr.getKey();
                String attrValue = String.valueOf(attr.getValue());
                
                attributeValueCounts
                        .computeIfAbsent(eventType, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(attrName, k -> new ConcurrentHashMap<>())
                        .merge(attrValue, 1L, Long::sum);
            }
        }
        
        // Cleanup old data periodically
        if (System.currentTimeMillis() % 100 == 0) {
            cleanup();
        }
    }

    /**
     * Records co-occurrence between two event types.
     */
    public void recordCoOccurrence(String eventType1, String eventType2) {
        coOccurrence
                .computeIfAbsent(eventType1, k -> new ConcurrentHashMap<>())
                .merge(eventType2, 1L, Long::sum);
        
        // Record bidirectional
        coOccurrence
                .computeIfAbsent(eventType2, k -> new ConcurrentHashMap<>())
                .merge(eventType1, 1L, Long::sum);
    }

    /**
     * Computes current statistics for an event type.
     */
    public EventStatistics computeStatistics(String eventType) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(windowSize);
        
        // Frequency histogram
        Map<String, Long> frequencyHist = new HashMap<>();
        frequencyHist.put(eventType, eventTypeFrequency.getOrDefault(eventType, 0L));
        
        // Co-occurrence matrix
        Map<String, Map<String, Long>> coOccMatrix = new HashMap<>();
        if (coOccurrence.containsKey(eventType)) {
            coOccMatrix.put(eventType, new HashMap<>(coOccurrence.get(eventType)));
        }
        
        // Inter-arrival statistics
        Map<String, Double> interArrivalStats = computeInterArrivalStats(eventType);
        
        // Attribute statistics
        Map<String, EventStatistics.AttributeStatistics> attrStats = computeAttributeStats(eventType);
        
        return EventStatistics.builder()
                .eventType(eventType)
                .windowStart(windowStart)
                .windowEnd(now)
                .eventCount(eventTypeFrequency.getOrDefault(eventType, 0L))
                .frequencyHistogram(frequencyHist)
                .coOccurrenceMatrix(coOccMatrix)
                .interArrivalStats(interArrivalStats)
                .attributeStats(attrStats)
                .metadata(Map.of(
                        "windowSizeSeconds", String.valueOf(windowSize.getSeconds()),
                        "maxCardinality", String.valueOf(maxCardinalityTracking)
                ))
                .build();
    }

    /**
     * Computes all statistics across all event types.
     */
    public List<EventStatistics> computeAllStatistics() {
        return eventTypeFrequency.keySet().stream()
                .map(this::computeStatistics)
                .collect(Collectors.toList());
    }

    /**
     * Computes inter-arrival time statistics.
     */
    private Map<String, Double> computeInterArrivalStats(String eventType) {
        Map<String, Double> stats = new HashMap<>();
        
        List<Instant> timestamps = eventTimestamps.get(eventType);
        if (timestamps == null || timestamps.size() < 2) {
            return stats;
        }
        
        // Sort timestamps
        List<Instant> sorted = new ArrayList<>(timestamps);
        sorted.sort(Instant::compareTo);
        
        // Calculate inter-arrival times
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            long gapMs = Duration.between(sorted.get(i - 1), sorted.get(i)).toMillis();
            gaps.add(gapMs);
        }
        
        if (!gaps.isEmpty()) {
            double mean = gaps.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double min = gaps.stream().mapToLong(Long::longValue).min().orElse(0);
            double max = gaps.stream().mapToLong(Long::longValue).max().orElse(0);
            
            stats.put("meanInterArrivalMs", mean);
            stats.put("minInterArrivalMs", min);
            stats.put("maxInterArrivalMs", max);
        }
        
        return stats;
    }

    /**
     * Computes attribute statistics.
     */
    private Map<String, EventStatistics.AttributeStatistics> computeAttributeStats(String eventType) {
        Map<String, EventStatistics.AttributeStatistics> stats = new HashMap<>();
        
        Map<String, Map<String, Long>> typeAttrs = attributeValueCounts.get(eventType);
        if (typeAttrs == null) {
            return stats;
        }
        
        for (Map.Entry<String, Map<String, Long>> attrEntry : typeAttrs.entrySet()) {
            String attrName = attrEntry.getKey();
            Map<String, Long> valueCounts = attrEntry.getValue();
            
            // Find most frequent value
            String mostFrequent = valueCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            
            long cardinality = valueCounts.size();
            long nullCount = valueCounts.getOrDefault("null", 0L);
            
            // Limit value distribution to top K
            Map<String, Long> topKValues = valueCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(maxCardinalityTracking)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            
            EventStatistics.AttributeStatistics attrStats = EventStatistics.AttributeStatistics.builder()
                    .attributeName(attrName)
                    .cardinality(cardinality)
                    .valueDistribution(topKValues)
                    .mostFrequentValue(mostFrequent)
                    .nullCount(nullCount)
                    .build();
            
            stats.put(attrName, attrStats);
        }
        
        return stats;
    }

    /**
     * Cleans up expired data outside the window.
     */
    private void cleanup() {
        Instant cutoff = Instant.now().minus(windowSize);
        
        // Clean up timestamps
        for (List<Instant> timestamps : eventTimestamps.values()) {
            timestamps.removeIf(ts -> ts.isBefore(cutoff));
        }
        
        // Remove empty entries
        eventTimestamps.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Resets all statistics.
     */
    public void reset() {
        eventTypeFrequency.clear();
        eventTimestamps.clear();
        coOccurrence.clear();
        attributeValueCounts.clear();
        interArrivalTimes.clear();
    }
}
