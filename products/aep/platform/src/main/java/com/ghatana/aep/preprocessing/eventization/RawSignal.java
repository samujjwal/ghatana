/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.eventization;

import java.time.Instant;
import java.util.Map;

/**
 * Raw signal from any data source before eventization.
 * 
 * <p><b>Purpose</b><br>
 * Represents unprocessed data from sources like HTTP requests, database changes,
 * file system events, or IoT sensors. Contains minimal structure - just timestamp,
 * source identifier, and raw payload.
 * 
 * <p><b>Lifecycle</b><br>
 * RawSignal → EventizationService → SemanticEvent (10:1 reduction)
 * 
 * <p><b>Example Raw Signals</b><br>
 * <ul>
 *   <li>HTTP: Every request with headers, body, IP</li>
 *   <li>DB: Every row change with old/new values</li>
 *   <li>File: Every file system operation</li>
 *   <li>IoT: Every sensor reading</li>
 * </ul>
 * 
 * @doc.type record
 * @doc.purpose Raw data container before processing
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RawSignal(
    String signalId,
    String source,
    Instant timestamp,
    String signalType,
    Map<String, Object> payload,
    Map<String, String> metadata
) {
    /**
     * Builder for convenient construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String signalId;
        private String source;
        private Instant timestamp;
        private String signalType;
        private Map<String, Object> payload;
        private Map<String, String> metadata;

        public Builder signalId(String signalId) {
            this.signalId = signalId;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder signalType(String signalType) {
            this.signalType = signalType;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public RawSignal build() {
            return new RawSignal(signalId, source, timestamp, signalType, payload, metadata);
        }
    }
}
