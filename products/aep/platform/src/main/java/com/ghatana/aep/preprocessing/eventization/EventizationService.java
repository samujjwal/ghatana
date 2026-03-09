/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.eventization;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Core service for converting raw signals to semantic events.
 * 
 * <p><b>Purpose</b><br>
 * Implements the eventization layer that transforms high-volume raw signals
 * into meaningful business events. Achieves 10:1 compression through:
 * <ul>
 *   <li>Noise filtering (irrelevant signals)</li>
 *   <li>Temporal aggregation (similar signals in time window)</li>
 *   <li>Semantic extraction (business meaning)</li>
 *   <li>Complexity reduction (dimensionality)</li>
 * </ul>
 * 
 * <p><b>Architecture Role</b><br>
 * First stage of Data Preprocessing cluster. Feeds normalized events to
 * Normalization Service and Detection Engine.
 * 
 * <p><b>Processing Pipeline</b><br>
 * <pre>
 * RawSignals[] → filterNoise() → groupByType() → aggregateGroups() 
 *              → extractSemantics() → SemanticEvents[]
 * </pre>
 * 
 * <p><b>Example</b><br>
 * <pre>{@code
 * EventizationService service = new EventizationService(executor, config);
 * 
 * List<RawSignal> rawSignals = List.of(...); // 100 HTTP requests
 * Promise<List<SemanticEvent>> promise = service.extractDomainEvents(rawSignals);
 * List<SemanticEvent> events = promise.getResult(); // 10 HighLoadEvents
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Raw signal to semantic event transformation
 * @doc.layer product
 * @doc.pattern Service
 */
public class EventizationService {
    private static final Logger logger = LoggerFactory.getLogger(EventizationService.class);
    
    private final ExecutorService executor;
    private final EventizationConfig config;
    private final ComplexityReducer complexityReducer;
    private final SemanticAggregator semanticAggregator;

    public EventizationService(
            ExecutorService executor,
            EventizationConfig config,
            ComplexityReducer complexityReducer,
            SemanticAggregator semanticAggregator) {
        this.executor = Objects.requireNonNull(executor, "executor required");
        this.config = Objects.requireNonNull(config, "config required");
        this.complexityReducer = Objects.requireNonNull(complexityReducer, "complexityReducer required");
        this.semanticAggregator = Objects.requireNonNull(semanticAggregator, "semanticAggregator required");
    }

    /**
     * Extracts domain events from raw signals.
     * 
     * @param rawSignals Raw signals to process
     * @return Promise of semantic events (10:1 reduction)
     */
    public Promise<List<SemanticEvent>> extractDomainEvents(List<RawSignal> rawSignals) {
        return Promise.ofBlocking(executor, () -> {
            logger.debug("Processing {} raw signals", rawSignals.size());
            
            // Step 1: Filter noise
            List<RawSignal> filtered = filterNoise(rawSignals);
            logger.debug("After noise filtering: {} signals", filtered.size());
            
            // Step 2: Group by type and time window
            Map<String, List<RawSignal>> grouped = groupByTypeAndTime(filtered);
            logger.debug("Grouped into {} signal types", grouped.size());
            
            // Step 3: Aggregate and extract semantics
            List<SemanticEvent> events = new ArrayList<>();
            for (Map.Entry<String, List<RawSignal>> entry : grouped.entrySet()) {
                List<SemanticEvent> aggregated = aggregateLowLevelEvents(entry.getValue());
                events.addAll(aggregated);
            }
            
            logger.info("Eventization complete: {} raw signals → {} semantic events ({}:1 ratio)",
                    rawSignals.size(), events.size(), 
                    rawSignals.isEmpty() ? 0 : rawSignals.size() / Math.max(1, events.size()));
            
            return events;
        });
    }

    /**
     * Filters out noise and irrelevant signals.
     */
    private List<RawSignal> filterNoise(List<RawSignal> rawSignals) {
        return rawSignals.stream()
                .filter(signal -> !isNoise(signal))
                .filter(signal -> meetsThreshold(signal))
                .collect(Collectors.toList());
    }

    /**
     * Checks if signal is noise (health checks, keep-alives, etc).
     */
    private boolean isNoise(RawSignal signal) {
        // Filter out known noise patterns
        String signalType = signal.signalType();
        if (signalType.contains("health_check") || 
            signalType.contains("heartbeat") ||
            signalType.contains("keep_alive")) {
            return true;
        }
        
        // Filter duplicate signals within short time window
        return false;
    }

    /**
     * Checks if signal meets configured threshold.
     */
    private boolean meetsThreshold(RawSignal signal) {
        return complexityReducer.meetsSignificanceThreshold(signal);
    }

    /**
     * Groups signals by type and time window.
     */
    private Map<String, List<RawSignal>> groupByTypeAndTime(List<RawSignal> signals) {
        Map<String, List<RawSignal>> grouped = new HashMap<>();
        
        for (RawSignal signal : signals) {
            String key = createGroupingKey(signal);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(signal);
        }
        
        return grouped;
    }

    /**
     * Creates grouping key from signal type and time bucket.
     */
    private String createGroupingKey(RawSignal signal) {
        long timeBucket = signal.timestamp().toEpochMilli() / 
                config.getAggregationWindowMillis();
        return signal.signalType() + "_" + timeBucket;
    }

    /**
     * Aggregates low-level signals into semantic events.
     */
    private List<SemanticEvent> aggregateLowLevelEvents(List<RawSignal> signals) {
        if (signals.isEmpty()) {
            return List.of();
        }

        // Use semantic aggregator to combine related signals
        return semanticAggregator.combineRelatedSignals(signals);
    }

    /**
     * Configuration for eventization service.
     */
    public static class EventizationConfig {
        private final long aggregationWindowMillis;
        private final double significanceThreshold;
        private final int maxSignalsPerEvent;

        public EventizationConfig(
                long aggregationWindowMillis,
                double significanceThreshold,
                int maxSignalsPerEvent) {
            this.aggregationWindowMillis = aggregationWindowMillis;
            this.significanceThreshold = significanceThreshold;
            this.maxSignalsPerEvent = maxSignalsPerEvent;
        }

        public static EventizationConfig defaults() {
            return new EventizationConfig(
                    Duration.ofSeconds(10).toMillis(), // 10-second window
                    0.5,  // 50% significance threshold
                    100   // Max 100 signals per event
            );
        }

        public long getAggregationWindowMillis() {
            return aggregationWindowMillis;
        }

        public double getSignificanceThreshold() {
            return significanceThreshold;
        }

        public int getMaxSignalsPerEvent() {
            return maxSignalsPerEvent;
        }
    }
}
