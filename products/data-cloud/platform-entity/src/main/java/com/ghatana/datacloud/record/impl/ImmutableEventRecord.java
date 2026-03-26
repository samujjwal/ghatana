/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.record.impl;

import com.ghatana.datacloud.record.*;
import com.ghatana.datacloud.record.Record.RecordType;
import com.ghatana.platform.domain.auth.TenantId;

import java.time.Instant;
import java.util.*;

/**
 * Immutable event record for event sourcing.
 *
 * <p>Implements the complete feature set for immutable event records:
 * <ul>
 *   <li>{@link DataRecord} - Event data payload</li>
 *   <li>{@link ImmutableRecord} - Cannot be modified after creation</li>
 *   <li>{@link Schematized} - Stream/schema binding</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>Events are immutable - once created, never changed</li>
 *   <li>Events have a sequence offset within their stream</li>
 *   <li>Events support correlation for distributed tracing</li>
 *   <li>Events have both occurred and ingested timestamps</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Builder pattern
 * ImmutableEventRecord event = ImmutableEventRecord.builder()
 *     .tenantId("acme")
 *     .streamName("orders")
 *     .data("orderId", "ORD-12345")
 *     .data("amount", 99.99)
 *     .correlationId("trace-abc-123")
 *     .build();
 *
 * // Attempting to modify throws exception
 * event.withData(newData); // UnsupportedOperationException
 * }</pre>
 *
 * @see SimpleRecord
 * @see FullEntityRecord
 * @doc.type record
 * @doc.purpose Immutable event implementation
 * @doc.layer core
 * @doc.pattern Value Object, Event Sourcing
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record ImmutableEventRecord(
        RecordId recordId,
        TenantId tenantIdValue,
        String collectionName,
        String streamName,
        long offset,
        Map<String, Object> data,
        Map<String, Object> headers,
        Instant occurredAt,
        Instant ingestedAt,
        String correlationId,
        String causationId,
        String schemaVersionValue
) implements DataRecord, ImmutableRecord, Schematized {

    /**
     * Compact constructor with validation and immutability.
     */
    public ImmutableEventRecord {
        Objects.requireNonNull(recordId, "recordId cannot be null");
        Objects.requireNonNull(tenantIdValue, "tenantId cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");
        Objects.requireNonNull(streamName, "streamName cannot be null");

        // Ensure immutable copies
        data = data != null ? Map.copyOf(data) : Map.of();
        headers = headers != null ? Map.copyOf(headers) : Map.of();

        // Set defaults
        if (occurredAt == null) occurredAt = Instant.now();
        if (ingestedAt == null) ingestedAt = Instant.now();
    }

    // ═══════════════════════════════════════════════════════════════
    // Record interface implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public UUID id() {
        return recordId.value();
    }

    @Override
    public String tenantId() {
        return tenantIdValue.value();
    }

    @Override
    public RecordType recordType() {
        return RecordType.EVENT;
    }

    // ═══════════════════════════════════════════════════════════════
    // DataRecord implementation (immutable - throws on mutation)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public DataRecord withData(Map<String, Object> data) {
        throw new UnsupportedOperationException(
                "Events are immutable - cannot modify data after creation");
    }

    // ═══════════════════════════════════════════════════════════════
    // Schematized implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Optional<String> schemaVersion() {
        return Optional.ofNullable(schemaVersionValue);
    }

    // ═══════════════════════════════════════════════════════════════
    // Event-specific methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the stream name this event belongs to.
     *
     * @return stream name
     */
    public String stream() {
        return streamName;
    }

    /**
     * Returns a specific header value.
     *
     * @param key the header key
     * @return the value, or null if not present
     */
    public Object getHeader(String key) {
        return headers.get(key);
    }

    /**
     * Returns true if this event has a correlation ID.
     *
     * @return true if correlated
     */
    public boolean hasCorrelationId() {
        return correlationId != null;
    }

    /**
     * Returns true if this event has a causation ID.
     *
     * @return true if has causation
     */
    public boolean hasCausationId() {
        return causationId != null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a new builder.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ImmutableEventRecord.
     */
    public static class Builder {
        private RecordId id = RecordId.generate();
        private TenantId tenantId;
        private String collectionName = "events";
        private String streamName;
        private long offset = 0;
        private Map<String, Object> data = new HashMap<>();
        private Map<String, Object> headers = new HashMap<>();
        private Instant occurredAt = Instant.now();
        private Instant ingestedAt = Instant.now();
        private String correlationId;
        private String causationId;
        private String schemaVersion;

        public Builder id(RecordId id) {
            this.id = id;
            return this;
        }

        public Builder id(String id) {
            this.id = RecordId.of(id);
            return this;
        }

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = TenantId.of(tenantId);
            return this;
        }

        public Builder collectionName(String name) {
            this.collectionName = name;
            return this;
        }

        public Builder streamName(String streamName) {
            this.streamName = streamName;
            return this;
        }

        public Builder offset(long offset) {
            this.offset = offset;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = new HashMap<>(data);
            return this;
        }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public Builder headers(Map<String, Object> headers) {
            this.headers = new HashMap<>(headers);
            return this;
        }

        public Builder header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder ingestedAt(Instant ingestedAt) {
            this.ingestedAt = ingestedAt;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder causationId(String causationId) {
            this.causationId = causationId;
            return this;
        }

        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        /**
         * Builds the ImmutableEventRecord.
         *
         * @return new ImmutableEventRecord
         * @throws NullPointerException if required fields are missing
         */
        public ImmutableEventRecord build() {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(streamName, "streamName is required");

            return new ImmutableEventRecord(
                    id, tenantId, collectionName, streamName, offset,
                    data, headers, occurredAt, ingestedAt,
                    correlationId, causationId, schemaVersion
            );
        }
    }
}
