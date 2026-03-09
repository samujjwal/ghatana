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
 * Full-featured entity record with all traits.
 *
 * <p>Implements the complete feature set for mutable entity records:
 * <ul>
 *   <li>{@link DataRecord} - Data payload</li>
 *   <li>{@link MetadataRecord} - Metadata storage</li>
 *   <li>{@link MutableRecord} - Allows updates</li>
 *   <li>{@link Versioned} - Optimistic concurrency</li>
 *   <li>{@link Timestamped} - Creation/modification timestamps</li>
 *   <li>{@link Auditable} - User tracking</li>
 *   <li>{@link Schematized} - Collection/schema binding</li>
 *   <li>{@link AIEnhanced} - AI metadata</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Builder pattern
 * FullEntityRecord record = FullEntityRecord.builder()
 *     .tenantId("acme")
 *     .collectionName("customers")
 *     .data("name", "John Doe")
 *     .data("email", "john@acme.com")
 *     .createdBy("admin")
 *     .build();
 *
 * // Update data (immutable)
 * FullEntityRecord updated = record.withData(Map.of("name", "Jane Doe"));
 *
 * // Increment version
 * FullEntityRecord versioned = record.incrementVersion();
 * }</pre>
 *
 * @see SimpleRecord
 * @see ImmutableEventRecord
 * @doc.type record
 * @doc.purpose Full-featured entity implementation
 * @doc.layer core
 * @doc.pattern Value Object, Builder
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record FullEntityRecord(
        RecordId recordId,
        TenantId tenantIdValue,
        String collectionName,
        Map<String, Object> data,
        Map<String, Object> metadata,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String modifiedBy,
        String schemaVersionValue,
        Map<String, Object> aiMetadata,
        Double aiConfidenceValue,
        String aiExplanationValue
) implements DataRecord, MetadataRecord, MutableRecord, Versioned<FullEntityRecord>, Timestamped<FullEntityRecord>,
        Auditable, Schematized, AIEnhanced {

    /**
     * Compact constructor with validation and immutability.
     */
    public FullEntityRecord {
        Objects.requireNonNull(recordId, "recordId cannot be null");
        Objects.requireNonNull(tenantIdValue, "tenantId cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");

        // Ensure immutable copies
        data = data != null ? Map.copyOf(data) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        aiMetadata = aiMetadata != null ? Map.copyOf(aiMetadata) : Map.of();

        // Set defaults
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
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
        return RecordType.ENTITY;
    }

    // ═══════════════════════════════════════════════════════════════
    // DataRecord/MetadataRecord implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public DataRecord withData(Map<String, Object> newData) {
        return new FullEntityRecord(
                recordId, tenantIdValue, collectionName, newData, metadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                schemaVersionValue, aiMetadata, aiConfidenceValue, aiExplanationValue
        );
    }

    @Override
    public MetadataRecord withMetadata(Map<String, Object> newMetadata) {
        return new FullEntityRecord(
                recordId, tenantIdValue, collectionName, data, newMetadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                schemaVersionValue, aiMetadata, aiConfidenceValue, aiExplanationValue
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Versioned implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public FullEntityRecord incrementVersion() {
        return new FullEntityRecord(
                recordId, tenantIdValue, collectionName, data, metadata,
                version + 1, createdAt, Instant.now(), createdBy, modifiedBy,
                schemaVersionValue, aiMetadata, aiConfidenceValue, aiExplanationValue
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Timestamped implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public FullEntityRecord touch() {
        return new FullEntityRecord(
                recordId, tenantIdValue, collectionName, data, metadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                schemaVersionValue, aiMetadata, aiConfidenceValue, aiExplanationValue
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Schematized implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Optional<String> schemaVersion() {
        return Optional.ofNullable(schemaVersionValue);
    }

    // ═══════════════════════════════════════════════════════════════
    // AIEnhanced implementation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Optional<Double> aiConfidence() {
        return Optional.ofNullable(aiConfidenceValue);
    }

    @Override
    public Optional<String> aiExplanation() {
        return Optional.ofNullable(aiExplanationValue);
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
     * Builder for FullEntityRecord.
     */
    public static class Builder {
        private RecordId id = RecordId.generate();
        private TenantId tenantId;
        private String collectionName;
        private Map<String, Object> data = new HashMap<>();
        private Map<String, Object> metadata = new HashMap<>();
        private long version = 0;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private String createdBy;
        private String modifiedBy;
        private String schemaVersion;
        private Map<String, Object> aiMetadata = new HashMap<>();
        private Double aiConfidence;
        private String aiExplanation;

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

        public Builder data(Map<String, Object> data) {
            this.data = new HashMap<>(data);
            return this;
        }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder modifiedBy(String modifiedBy) {
            this.modifiedBy = modifiedBy;
            return this;
        }

        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder aiMetadata(Map<String, Object> aiMetadata) {
            this.aiMetadata = new HashMap<>(aiMetadata);
            return this;
        }

        public Builder aiMetadata(String key, Object value) {
            this.aiMetadata.put(key, value);
            return this;
        }

        public Builder aiConfidence(Double confidence) {
            this.aiConfidence = confidence;
            return this;
        }

        public Builder aiExplanation(String explanation) {
            this.aiExplanation = explanation;
            return this;
        }

        /**
         * Builds the FullEntityRecord.
         *
         * @return new FullEntityRecord
         * @throws NullPointerException if required fields are missing
         */
        public FullEntityRecord build() {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(collectionName, "collectionName is required");

            return new FullEntityRecord(
                    id, tenantId, collectionName, data, metadata,
                    version, createdAt, updatedAt, createdBy, modifiedBy,
                    schemaVersion, aiMetadata, aiConfidence, aiExplanation
            );
        }
    }
}
