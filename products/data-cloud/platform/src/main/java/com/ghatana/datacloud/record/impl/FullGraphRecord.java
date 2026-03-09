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
 * Full-featured graph record with all traits.
 *
 * <p>Trait-based (immutable java record) counterpart of the JPA
 * {@link com.ghatana.datacloud.GraphRecord}. Implements the complete feature
 * set for graph records:
 * <ul>
 *   <li>{@link DataRecord} — Data payload</li>
 *   <li>{@link MetadataRecord} — Metadata storage</li>
 *   <li>{@link MutableRecord} — Allows updates</li>
 *   <li>{@link Versioned} — Optimistic concurrency</li>
 *   <li>{@link Timestamped} — Created/modified timestamps</li>
 *   <li>{@link Auditable} — User tracking</li>
 * </ul>
 *
 * <h2>Graph Element Model</h2>
 * <p>A single record can represent either a <b>node</b> (vertex) or an
 * <b>edge</b> (relationship). Edges carry source/target node references,
 * direction semantics, and an optional numeric weight.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create a node
 * FullGraphRecord person = FullGraphRecord.nodeBuilder()
 *     .tenantId("acme")
 *     .collectionName("social")
 *     .label("Person")
 *     .data("name", "Alice")
 *     .build();
 *
 * // Create an edge
 * FullGraphRecord knows = FullGraphRecord.edgeBuilder()
 *     .tenantId("acme")
 *     .collectionName("social")
 *     .label("KNOWS")
 *     .sourceNodeId(person.id().toString())
 *     .targetNodeId(bobId)
 *     .weight(0.85)
 *     .build();
 * }</pre>
 *
 * @see com.ghatana.datacloud.GraphRecord
 * @doc.type record
 * @doc.purpose Trait-based graph record (node or edge)
 * @doc.layer core
 * @doc.pattern Value Object, Property Graph
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record FullGraphRecord(
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
        // Graph-specific
        GraphElement elementType,
        String label,
        String sourceNodeId,
        String targetNodeId,
        EdgeDirection direction,
        double weight
) implements DataRecord, MetadataRecord, MutableRecord, Versioned<FullGraphRecord>,
        Timestamped<FullGraphRecord>, Auditable {

    // ═══════════════════════════════════════════════════════════════
    // Enums (mirror JPA GraphRecord enums for portability)
    // ═══════════════════════════════════════════════════════════════

    /** Graph element kind. */
    public enum GraphElement { NODE, EDGE }

    /** Edge direction semantics. */
    public enum EdgeDirection { DIRECTED, UNDIRECTED, BIDIRECTIONAL }

    // ═══════════════════════════════════════════════════════════════
    // Compact Constructor
    // ═══════════════════════════════════════════════════════════════

    public FullGraphRecord {
        Objects.requireNonNull(recordId, "recordId cannot be null");
        Objects.requireNonNull(tenantIdValue, "tenantId cannot be null");
        Objects.requireNonNull(collectionName, "collectionName cannot be null");
        Objects.requireNonNull(elementType, "elementType cannot be null");
        Objects.requireNonNull(label, "label cannot be null");

        data = data != null ? Map.copyOf(data) : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (direction == null) direction = EdgeDirection.DIRECTED;
    }

    // ═══════════════════════════════════════════════════════════════
    // Record interface
    // ═══════════════════════════════════════════════════════════════

    @Override public UUID id() { return recordId.value(); }
    @Override public String tenantId() { return tenantIdValue.value(); }
    @Override public RecordType recordType() { return RecordType.GRAPH; }

    // ═══════════════════════════════════════════════════════════════
    // DataRecord / MetadataRecord
    // ═══════════════════════════════════════════════════════════════

    @Override
    public DataRecord withData(Map<String, Object> newData) {
        return new FullGraphRecord(
                recordId, tenantIdValue, collectionName, newData, metadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                elementType, label, sourceNodeId, targetNodeId, direction, weight
        );
    }

    @Override
    public MetadataRecord withMetadata(Map<String, Object> newMetadata) {
        return new FullGraphRecord(
                recordId, tenantIdValue, collectionName, data, newMetadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                elementType, label, sourceNodeId, targetNodeId, direction, weight
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Versioned / Timestamped
    // ═══════════════════════════════════════════════════════════════

    @Override
    public FullGraphRecord incrementVersion() {
        return new FullGraphRecord(
                recordId, tenantIdValue, collectionName, data, metadata,
                version + 1, createdAt, Instant.now(), createdBy, modifiedBy,
                elementType, label, sourceNodeId, targetNodeId, direction, weight
        );
    }

    @Override
    public FullGraphRecord touch() {
        return new FullGraphRecord(
                recordId, tenantIdValue, collectionName, data, metadata,
                version, createdAt, Instant.now(), createdBy, modifiedBy,
                elementType, label, sourceNodeId, targetNodeId, direction, weight
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Graph helpers
    // ═══════════════════════════════════════════════════════════════

    /** True if this is a node. */
    public boolean isNode() { return elementType == GraphElement.NODE; }

    /** True if this is an edge. */
    public boolean isEdge() { return elementType == GraphElement.EDGE; }

    /** Whether this edge connects the given node (source or target). */
    public boolean connectsNode(String nodeId) {
        return isEdge() && nodeId != null
                && (nodeId.equals(sourceNodeId) || nodeId.equals(targetNodeId));
    }

    // ═══════════════════════════════════════════════════════════════
    // Builders
    // ═══════════════════════════════════════════════════════════════

    /** Builder pre-configured for NODE. */
    public static Builder nodeBuilder() { return new Builder(GraphElement.NODE); }

    /** Builder pre-configured for EDGE. */
    public static Builder edgeBuilder() { return new Builder(GraphElement.EDGE); }

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
        private final GraphElement elementType;
        private String label;
        private String sourceNodeId;
        private String targetNodeId;
        private EdgeDirection direction = EdgeDirection.DIRECTED;
        private double weight = 1.0;

        Builder(GraphElement type) { this.elementType = type; }

        public Builder id(RecordId id) { this.id = id; return this; }
        public Builder id(String id) { this.id = RecordId.of(id); return this; }
        public Builder tenantId(String t) { this.tenantId = TenantId.of(t); return this; }
        public Builder tenantId(TenantId t) { this.tenantId = t; return this; }
        public Builder collectionName(String c) { this.collectionName = c; return this; }
        public Builder label(String l) { this.label = l; return this; }
        public Builder sourceNodeId(String s) { this.sourceNodeId = s; return this; }
        public Builder targetNodeId(String t) { this.targetNodeId = t; return this; }
        public Builder direction(EdgeDirection d) { this.direction = d; return this; }
        public Builder weight(double w) { this.weight = w; return this; }
        public Builder createdBy(String c) { this.createdBy = c; return this; }
        public Builder version(long v) { this.version = v; return this; }

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

        public FullGraphRecord build() {
            return new FullGraphRecord(
                    id, tenantId, collectionName, data, metadata,
                    version, createdAt, null, createdBy, modifiedBy,
                    elementType, label, sourceNodeId, targetNodeId, direction, weight
            );
        }
    }
}
