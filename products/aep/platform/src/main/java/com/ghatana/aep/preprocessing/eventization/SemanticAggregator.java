/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.eventization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates related raw signals into meaningful semantic events.
 * 
 * <p><b>Purpose</b><br>
 * Combines multiple low-level signals that represent the same business event.
 * For example, 100 HTTP requests to the same endpoint within 10 seconds become
 * one "HighLoadEvent".
 * 
 * <p><b>Aggregation Strategies</b><br>
 * <ul>
 *   <li>Temporal: Signals within time window</li>
 *   <li>Spatial: Signals from same source/location</li>
 *   <li>Semantic: Signals with same business meaning</li>
 *   <li>Statistical: Signals with similar patterns</li>
 * </ul>
 * 
 * <p><b>Example</b><br>
 * Input: 50 "http_request" signals to /api/users
 * Output: 1 "HighLoadEvent" with count=50, endpoint=/api/users
 * 
 * @doc.type class
 * @doc.purpose Signal aggregation and semantic extraction
 * @doc.layer product
 * @doc.pattern Service
 */
public class SemanticAggregator {
    private static final Logger logger = LoggerFactory.getLogger(SemanticAggregator.class);
    
    private final ComplexityReducer complexityReducer;

    public SemanticAggregator(ComplexityReducer complexityReducer) {
        this.complexityReducer = Objects.requireNonNull(complexityReducer, "complexityReducer required");
    }

    /**
     * Combines related signals into semantic events.
     * 
     * @param signals Raw signals to aggregate (same type, time window)
     * @return List of semantic events (typically 1, or split if too large)
     */
    public List<SemanticEvent> combineRelatedSignals(List<RawSignal> signals) {
        if (signals.isEmpty()) {
            return List.of();
        }

        // If only one signal, convert directly
        if (signals.size() == 1) {
            return List.of(convertSingleSignal(signals.get(0)));
        }

        // Group signals by domain/context
        Map<String, List<RawSignal>> byDomain = groupByDomain(signals);

        return byDomain.entrySet().stream()
                .map(entry -> aggregateSignalGroup(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Groups signals by domain (extracted from source or metadata).
     */
    private Map<String, List<RawSignal>> groupByDomain(List<RawSignal> signals) {
        Map<String, List<RawSignal>> grouped = new HashMap<>();
        
        for (RawSignal signal : signals) {
            String domain = extractDomain(signal);
            grouped.computeIfAbsent(domain, k -> new ArrayList<>()).add(signal);
        }
        
        return grouped;
    }

    /**
     * Extracts domain from signal source or metadata.
     */
    private String extractDomain(RawSignal signal) {
        // Try metadata first
        if (signal.metadata() != null && signal.metadata().containsKey("domain")) {
            return signal.metadata().get("domain");
        }

        // Extract from source (e.g., "http://api.example.com" → "api")
        String source = signal.source();
        if (source.contains("://")) {
            source = source.substring(source.indexOf("://") + 3);
        }
        if (source.contains(".")) {
            source = source.substring(0, source.indexOf("."));
        }
        if (source.contains(":")) {
            source = source.substring(0, source.indexOf(":"));
        }
        
        return source.isEmpty() ? "unknown" : source;
    }

    /**
     * Aggregates a group of signals from the same domain.
     */
    private SemanticEvent aggregateSignalGroup(String domain, List<RawSignal> signals) {
        RawSignal first = signals.get(0);
        
        // Use complexity reducer to map signal type to event type
        String eventType = complexityReducer.mapSignalToEventType(first.signalType());
        
        // Aggregate timestamps (use first as representative)
        Instant timestamp = signals.stream()
                .map(RawSignal::timestamp)
                .min(Instant::compareTo)
                .orElse(first.timestamp());

        // Merge attributes from all signals
        Map<String, Object> attributes = mergeAttributes(signals);
        
        // Build context
        Map<String, String> context = new HashMap<>();
        context.put("signalType", first.signalType());
        context.put("aggregationCount", String.valueOf(signals.size()));
        context.put("source", first.source());

        // Calculate confidence (higher for more signals)
        double confidence = Math.min(1.0, 0.5 + (signals.size() * 0.05));

        return SemanticEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(timestamp)
                .domain(domain)
                .attributes(attributes)
                .context(context)
                .confidence(confidence)
                .aggregatedSignalCount(signals.size())
                .build();
    }

    /**
     * Merges attributes from multiple signals.
     */
    private Map<String, Object> mergeAttributes(List<RawSignal> signals) {
        Map<String, Object> merged = new HashMap<>();

        // Count occurrences of each attribute value
        Map<String, Map<Object, Integer>> attributeCounts = new HashMap<>();
        
        for (RawSignal signal : signals) {
            if (signal.payload() != null) {
                for (Map.Entry<String, Object> entry : signal.payload().entrySet()) {
                    attributeCounts
                            .computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                            .merge(entry.getValue(), 1, Integer::sum);
                }
            }
        }

        // Use most frequent value for each attribute
        for (Map.Entry<String, Map<Object, Integer>> entry : attributeCounts.entrySet()) {
            String key = entry.getKey();
            Map<Object, Integer> counts = entry.getValue();
            
            Object mostFrequent = counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            
            if (mostFrequent != null) {
                merged.put(key, mostFrequent);
            }
        }

        // Add aggregation stats
        merged.put("totalSignals", signals.size());
        merged.put("uniqueSources", signals.stream()
                .map(RawSignal::source)
                .distinct()
                .count());

        return merged;
    }

    /**
     * Converts a single signal to a semantic event.
     */
    private SemanticEvent convertSingleSignal(RawSignal signal) {
        String eventType = complexityReducer.mapSignalToEventType(signal.signalType());
        String domain = extractDomain(signal);

        Map<String, String> context = new HashMap<>();
        context.put("signalType", signal.signalType());
        context.put("source", signal.source());
        if (signal.metadata() != null) {
            context.putAll(signal.metadata());
        }

        return SemanticEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(signal.timestamp())
                .domain(domain)
                .attributes(signal.payload() != null ? new HashMap<>(signal.payload()) : new HashMap<>())
                .context(context)
                .confidence(0.8) // Single signal has moderate confidence
                .aggregatedSignalCount(1)
                .build();
    }

    /**
     * Extracts meaningful event from signal patterns.
     */
    public SemanticEvent extractMeaningfulEvent(List<RawSignal> signalPattern) {
        logger.debug("Extracting meaningful event from {} signals", signalPattern.size());
        return combineRelatedSignals(signalPattern).get(0);
    }
}
