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
 * Full-featured document record with all traits.
 *
 * <p>Trait-based (immutable java record) counterpart of the JPA
 * {@link com.ghatana.datacloud.DocumentRecord}. Implements the complete
 * feature set for schema-free documents:
 * <ul>
 *   <li>{@link DataRecord} — Document body payload</li>
 *   <li>{@link MetadataRecord} — Metadata storage</li>
 *   <li>{@link MutableRecord} — Allows updates</li>
 *   <li>{@link Versioned} — Optimistic concurrency</li>
 *   <li>{@link Timestamped} — Created/modified timestamps</li>
 *   <li>{@link Auditable} — User tracking</li>
 *   <li>{@link Schematized} — Schema version binding</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FullDocumentRecord doc = FullDocumentRecord.builder()
 *     .tenantId("acme")
 *     .collectionName("articles")
 *     .title("Getting Started")
 *     .slug("getting-started")
 *     .contentType("text/markdown")
 *     .tags(List.of("guide", "onboarding"))
 *     .data("body", "# Getting Started\n…")
 *     .build();
 *
 * // Immutable update
 * FullDocumentRecord v2 = (FullDocumentRecord) doc.withData(
 *     Map.of("body", "# Updated Guide\n…"));
 * }</pre>
 *
 * @see com.ghatana.datacloud.DocumentRecord
 * @doc.type record
 * @doc.purpose Trait-based document record
 * @doc.layer core
 * @doc.pattern Value Object, Document Store
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record FullDocumentRecord(
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
        // Document-specific
        String title,
        String slug,
        String contentType,
        List<String> tags,
        String language
) implements DataRecord, MetadataRecord, MutableRecord, Versioned<FullDocumentRecord>,
        Timestamped<FullDocumentRecord>, Auditable, Schematized {

    // ═══════════════════════════════════════════════════════════════
    // Compact Constructor
    // ═══════════════════════════════════════════════════════════════

    public FullDocumentRecord {
        Objects.requireNonNull(recordId, "recordId cannot be null");
        Objects.requireNonNull(tenantIdValue, "tenantId cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");

        data = data != null ? Map.copyOf(data) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        tags = tags != null ? List.copyOf(tags) : List.of();
        if (contentType == null) contentType = "application/json";
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    // ═══════════════════════════════════════════════════════════════
    // Record interface
    // ═══════════════════════════════════════════════════════════════

    @Override public UUID id() { return recordId.value(); }
    @Override public String tenantId() { return tenantIdValue.value(); }
    @Override public RecordType recordType() { return RecordType.DOCUMENT; }

    // ═══════════════════════════════════════════════════════════════
    // DataRecord / MetadataRecord
    // ═══════════════════════════════════════════════════════════════

    @Override
    public DataRecord withData(Map<String, Object> newData) {
        return new FullDocumentRecord(
                recordId, tenantIdValue, collectionName, newData, metadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                schemaVersionValue, title, slug, contentType, tags, language
        );
    }

    @Override
    public MetadataRecord withMetadata(Map<String, Object> newMetadata) {
        return new FullDocumentRecord(
                recordId, tenantIdValue, collectionName, data, newMetadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                schemaVersionValue, title, slug, contentType, tags, language
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Versioned / Timestamped
    // ═══════════════════════════════════════════════════════════════

    @Override
    public FullDocumentRecord incrementVersion() {
        return new FullDocumentRecord(
                recordId, tenantIdValue, collectionName, data, metadata,
                version + 1, createdAt, Instant.now(), createdBy, modifiedBy,
                schemaVersionValue, title, slug, contentType, tags, language
        );
    }

    @Override
    public FullDocumentRecord touch() {
        return new FullDocumentRecord(
                recordId, tenantIdValue, collectionName, data, metadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                schemaVersionValue, title, slug, contentType, tags, language
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Schematized
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Optional<String> schemaVersion() {
        return Optional.ofNullable(schemaVersionValue);
    }

    // ═══════════════════════════════════════════════════════════════
    // Document helpers
    // ═══════════════════════════════════════════════════════════════

    /** Whether content type starts with {@code application/json}. */
    public boolean isJson() { return contentType != null && contentType.startsWith("application/json"); }

    /** Whether content type starts with {@code text/}. */
    public boolean isText() { return contentType != null && contentType.startsWith("text/"); }

    /** Whether this document carries the given tag (case-insensitive). */
    public boolean hasTag(String tag) {
        return tag != null && tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag));
    }

    // ═══════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private RecordId id = RecordId.generate();
        private TenantId tenantId;
        private String collectionName;
        private final Map<String, Object> data = new HashMap<>();
        private final Map<String, Object> metadata = new HashMap<>();
        private long version = 0;
        private Instant createdAt;
        private String createdBy;
        private String modifiedBy;
        private String schemaVersion;
        private String title;
        private String slug;
        private String contentType = "application/json";
        private final List<String> tags = new ArrayList<>();
        private String language;

        public Builder id(RecordId id) { this.id = id; return this; }
        public Builder id(String id) { this.id = RecordId.of(id); return this; }
        public Builder tenantId(String t) { this.tenantId = TenantId.of(t); return this; }
        public Builder tenantId(TenantId t) { this.tenantId = t; return this; }
        public Builder collectionName(String c) { this.collectionName = c; return this; }
        public Builder title(String t) { this.title = t; return this; }
        public Builder slug(String s) { this.slug = s; return this; }
        public Builder contentType(String c) { this.contentType = c; return this; }
        public Builder language(String l) { this.language = l; return this; }
        public Builder createdBy(String c) { this.createdBy = c; return this; }
        public Builder version(long v) { this.version = v; return this; }
        public Builder schemaVersion(String s) { this.schemaVersion = s; return this; }

        public Builder tag(String tag) { this.tags.add(tag); return this; }
        public Builder tags(List<String> tags) { this.tags.addAll(tags); return this; }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public Builder data(Map<String, Object> d) {
            this.data.putAll(d);
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public FullDocumentRecord build() {
            return new FullDocumentRecord(
                    id, tenantId, collectionName, data, metadata,
                    version, createdAt, null, createdBy, modifiedBy,
                    schemaVersion, title, slug, contentType, tags, language
            );
        }
    }
}
