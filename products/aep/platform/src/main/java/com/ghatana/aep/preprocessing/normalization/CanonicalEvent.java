/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.normalization;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical event format after normalization.
 * 
 * <p><b>Purpose</b><br>
 * Unified representation of events from all sources. Ensures consistent
 * schema regardless of origin (HTTP, DB, file system, IoT, etc).
 * 
 * <p><b>Normalization Benefits</b><br>
 * <ul>
 *   <li>Single schema for all downstream processing</li>
 *   <li>Source-agnostic pattern detection</li>
 *   <li>Simplified operator development</li>
 *   <li>Cross-source correlation</li>
 * </ul>
 * 
 * <p><b>Canonical Fields</b><br>
 * <ul>
 *   <li>eventId: Unique identifier</li>
 *   <li>eventType: Normalized type (camelCase)</li>
 *   <li>timestamp: ISO-8601 instant</li>
 *   <li>source: Original source identifier</li>
 *   <li>tenantId: Multi-tenant isolation</li>
 *   <li>attributes: Normalized key-value pairs</li>
 *   <li>metadata: Source-specific context</li>
 * </ul>
 * 
 * @doc.type record
 * @doc.purpose Unified event representation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanonicalEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    String source,
    String tenantId,
    String domain,
    Map<String, Object> attributes,
    Map<String, String> metadata,
    double quality
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
        private String source;
        private String tenantId;
        private String domain;
        private Map<String, Object> attributes;
        private Map<String, String> metadata;
        private double quality = 1.0;

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

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
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

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder quality(double quality) {
            this.quality = quality;
            return this;
        }

        public CanonicalEvent build() {
            return new CanonicalEvent(
                eventId, eventType, timestamp, source, tenantId,
                domain, attributes, metadata, quality
            );
        }
    }
}
