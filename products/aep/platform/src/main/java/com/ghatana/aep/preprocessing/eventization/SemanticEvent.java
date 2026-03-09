/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.eventization;

import java.time.Instant;
import java.util.Map;

/**
 * Semantic event extracted from raw signals.
 * 
 * <p><b>Purpose</b><br>
 * Represents a meaningful business event after eventization processing.
 * One semantic event may aggregate multiple raw signals (10:1 reduction ratio).
 * 
 * <p><b>Key Differences from RawSignal</b><br>
 * <ul>
 *   <li>Business semantics: "UserLogin" vs "HTTP POST /auth"</li>
 *   <li>Noise filtered: Only meaningful changes</li>
 *   <li>Aggregated: Related signals combined</li>
 *   <li>Enriched: Additional context added</li>
 * </ul>
 * 
 * <p><b>Example Transformations</b><br>
 * <ul>
 *   <li>100 HTTP requests → 10 "HighLoadEvent"</li>
 *   <li>50 DB updates → 1 "OrderCompleteEvent"</li>
 *   <li>1000 sensor readings → 5 "TemperatureAnomalyEvent"</li>
 * </ul>
 * 
 * @doc.type record
 * @doc.purpose Meaningful business event
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SemanticEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    String domain,
    Map<String, Object> attributes,
    Map<String, String> context,
    double confidence,
    int aggregatedSignalCount
) {
    /**
     * Builder for convenient construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventId;
        private String eventType;
        private Instant timestamp;
        private String domain;
        private Map<String, Object> attributes;
        private Map<String, String> context;
        private double confidence = 1.0;
        private int aggregatedSignalCount = 1;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder context(Map<String, String> context) {
            this.context = context;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder aggregatedSignalCount(int aggregatedSignalCount) {
            this.aggregatedSignalCount = aggregatedSignalCount;
            return this;
        }

        public SemanticEvent build() {
            return new SemanticEvent(
                eventId, eventType, timestamp, domain, 
                attributes, context, confidence, aggregatedSignalCount
            );
        }
    }
}
