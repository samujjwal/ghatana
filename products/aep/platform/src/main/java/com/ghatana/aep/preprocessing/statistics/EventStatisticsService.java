/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.statistics;

import com.ghatana.aep.preprocessing.normalization.CanonicalEvent;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Main service for collecting event stream statistics.
 * 
 * <p><b>Purpose</b><br>
 * Third and final stage of Data Preprocessing cluster. Collects statistical
 * profiles of canonical event streams for Learning System pattern mining.
 * 
 * <p><b>Architecture Role</b><br>
 * Bridges Data Preprocessing → Learning System. Provides the statistical
 * foundation for:
 * <ul>
 *   <li>Frequent pattern mining (FP-Growth, Apriori)</li>
 *   <li>Sequence discovery (PrefixSpan, GSP)</li>
 *   <li>Anomaly detection (baseline behaviors)</li>
 *   <li>Correlation analysis (event relationships)</li>
 * </ul>
 * 
 * <p><b>Pipeline Position</b><br>
 * <pre>
 * RawSignals → Eventization → Normalization → Statistics → Learning System
 * </pre>
 * 
 * <p><b>Configuration</b><br>
 * <ul>
 *   <li>Window Size: Default 5 minutes (configurable)</li>
 *   <li>Max Cardinality Tracking: Top 100 values per attribute</li>
 *   <li>Flush Interval: Send statistics to learning system every 1 minute</li>
 * </ul>
 * 
 * <p><b>Example</b><br>
 * <pre>{@code
 * EventStatisticsService service = new EventStatisticsService(executor);
 * 
 * List<CanonicalEvent> events = List.of(...);
 * Promise<List<EventStatistics>> promise = service.collectStatistics(events);
 * List<EventStatistics> stats = promise.getResult();
 * 
 * // Stats now contain: frequencies, co-occurrences, inter-arrival times, etc.
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Event stream statistical profiling
 * @doc.layer product
 * @doc.pattern Service
 */
public class EventStatisticsService {
    private static final Logger logger = LoggerFactory.getLogger(EventStatisticsService.class);
    
    private final ExecutorService executor;
    private final StatisticsCollector collector;
    private final StatisticsConfig config;

    public EventStatisticsService(ExecutorService executor) {
        this(executor, StatisticsConfig.defaults());
    }

    public EventStatisticsService(ExecutorService executor, StatisticsConfig config) {
        this.executor = Objects.requireNonNull(executor, "executor required");
        this.config = Objects.requireNonNull(config, "config required");
        this.collector = new StatisticsCollector(
                config.windowSize(),
                config.maxCardinalityTracking()
        );
    }

    /**
     * Collects statistics from canonical events.
     * 
     * @param canonicalEvents Events from normalization service
     * @return Promise of event statistics
     */
    public Promise<List<EventStatistics>> collectStatistics(List<CanonicalEvent> canonicalEvents) {
        return Promise.ofBlocking(executor, () -> {
            logger.debug("Collecting statistics from {} canonical events", canonicalEvents.size());
            
            // Record all events
            for (CanonicalEvent event : canonicalEvents) {
                collector.record(event);
            }
            
            // Detect co-occurrences in this batch
            detectCoOccurrences(canonicalEvents);
            
            // Compute statistics
            List<EventStatistics> statistics = collector.computeAllStatistics();
            
            logger.info("Collected statistics for {} event types from {} events",
                    statistics.size(), canonicalEvents.size());
            
            return statistics;
        });
    }

    /**
     * Gets current statistics without adding new events.
     */
    public Promise<List<EventStatistics>> getCurrentStatistics() {
        return Promise.ofBlocking(executor, () -> {
            logger.debug("Retrieving current statistics");
            return collector.computeAllStatistics();
        });
    }

    /**
     * Gets statistics for a specific event type.
     */
    public Promise<EventStatistics> getStatistics(String eventType) {
        return Promise.ofBlocking(executor, () -> {
            logger.debug("Retrieving statistics for event type: {}", eventType);
            return collector.computeStatistics(eventType);
        });
    }

    /**
     * Resets all collected statistics.
     */
    public void resetStatistics() {
        logger.info("Resetting all statistics");
        collector.reset();
    }

    /**
     * Detects co-occurring events within time window.
     */
    private void detectCoOccurrences(List<CanonicalEvent> events) {
        Duration coOccurrenceWindow = Duration.ofSeconds(10);
        
        for (int i = 0; i < events.size(); i++) {
            CanonicalEvent event1 = events.get(i);
            
            for (int j = i + 1; j < events.size(); j++) {
                CanonicalEvent event2 = events.get(j);
                
                // Check if within co-occurrence window
                long gapMs = Math.abs(
                        Duration.between(event1.timestamp(), event2.timestamp()).toMillis()
                );
                
                if (gapMs <= coOccurrenceWindow.toMillis()) {
                    collector.recordCoOccurrence(event1.eventType(), event2.eventType());
                }
            }
        }
    }

    /**
     * Configuration for statistics collection.
     */
    public record StatisticsConfig(
        Duration windowSize,
        int maxCardinalityTracking
    ) {
        public static StatisticsConfig defaults() {
            return new StatisticsConfig(
                    Duration.ofMinutes(5),  // 5-minute sliding window
                    100                      // Track top 100 values per attribute
            );
        }

        public static StatisticsConfig withWindow(Duration windowSize) {
            return new StatisticsConfig(windowSize, 100);
        }
    }
}
