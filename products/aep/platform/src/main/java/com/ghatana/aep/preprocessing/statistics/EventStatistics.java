/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.statistics;

import java.time.Instant;
import java.util.Map;

/**
 * Statistical profile of event stream.
 * 
 * <p><b>Purpose</b><br>
 * Captures key metrics about event patterns, frequencies, and distributions.
 * Used by Learning System for pattern mining and anomaly detection.
 * 
 * <p><b>Statistical Metrics</b><br>
 * <ul>
 *   <li>Frequency: Event occurrences over time windows</li>
 *   <li>Co-occurrence: Which events happen together</li>
 *   <li>Inter-arrival: Time gaps between events</li>
 *   <li>Attributes: Value distributions and cardinality</li>
 * </ul>
 * 
 * <p><b>Learning System Integration</b><br>
 * These statistics feed pattern mining algorithms to discover:
 * <ul>
 *   <li>Frequent sequences (e.g., Login → View → Purchase)</li>
 *   <li>Temporal patterns (e.g., high load every Friday 5pm)</li>
 *   <li>Correlation patterns (e.g., error A precedes error B)</li>
 *   <li>Baseline behaviors for anomaly detection</li>
 * </ul>
 * 
 * @doc.type record
 * @doc.purpose Event stream statistical profile
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EventStatistics(
    String eventType,
    Instant windowStart,
    Instant windowEnd,
    long eventCount,
    Map<String, Long> frequencyHistogram,
    Map<String, Map<String, Long>> coOccurrenceMatrix,
    Map<String, Double> interArrivalStats,
    Map<String, AttributeStatistics> attributeStats,
    Map<String, String> metadata
) {
    /**
     * Builder for convenient construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType;
        private Instant windowStart;
        private Instant windowEnd;
        private long eventCount;
        private Map<String, Long> frequencyHistogram;
        private Map<String, Map<String, Long>> coOccurrenceMatrix;
        private Map<String, Double> interArrivalStats;
        private Map<String, AttributeStatistics> attributeStats;
        private Map<String, String> metadata;

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder windowStart(Instant windowStart) {
            this.windowStart = windowStart;
            return this;
        }

        public Builder windowEnd(Instant windowEnd) {
            this.windowEnd = windowEnd;
            return this;
        }

        public Builder eventCount(long eventCount) {
            this.eventCount = eventCount;
            return this;
        }

        public Builder frequencyHistogram(Map<String, Long> frequencyHistogram) {
            this.frequencyHistogram = frequencyHistogram;
            return this;
        }

        public Builder coOccurrenceMatrix(Map<String, Map<String, Long>> coOccurrenceMatrix) {
            this.coOccurrenceMatrix = coOccurrenceMatrix;
            return this;
        }

        public Builder interArrivalStats(Map<String, Double> interArrivalStats) {
            this.interArrivalStats = interArrivalStats;
            return this;
        }

        public Builder attributeStats(Map<String, AttributeStatistics> attributeStats) {
            this.attributeStats = attributeStats;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public EventStatistics build() {
            return new EventStatistics(
                eventType, windowStart, windowEnd, eventCount,
                frequencyHistogram, coOccurrenceMatrix, interArrivalStats,
                attributeStats, metadata
            );
        }
    }

    /**
     * Statistics for a single attribute.
     */
    public record AttributeStatistics(
        String attributeName,
        long cardinality,
        Map<String, Long> valueDistribution,
        String mostFrequentValue,
        long nullCount
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String attributeName;
            private long cardinality;
            private Map<String, Long> valueDistribution;
            private String mostFrequentValue;
            private long nullCount;

            public Builder attributeName(String attributeName) {
                this.attributeName = attributeName;
                return this;
            }

            public Builder cardinality(long cardinality) {
                this.cardinality = cardinality;
                return this;
            }

            public Builder valueDistribution(Map<String, Long> valueDistribution) {
                this.valueDistribution = valueDistribution;
                return this;
            }

            public Builder mostFrequentValue(String mostFrequentValue) {
                this.mostFrequentValue = mostFrequentValue;
                return this;
            }

            public Builder nullCount(long nullCount) {
                this.nullCount = nullCount;
                return this;
            }

            public AttributeStatistics build() {
                return new AttributeStatistics(
                    attributeName, cardinality, valueDistribution,
                    mostFrequentValue, nullCount
                );
            }
        }
    }
}
