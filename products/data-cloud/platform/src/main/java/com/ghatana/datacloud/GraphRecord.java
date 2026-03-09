package com.ghatana.datacloud;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Graph record - represents nodes and edges in a property graph model.
 *
 * <p>
 * <b>Purpose</b><br>
 * Models interconnected data as a directed property graph. Each GraphRecord
 * can be a <b>node</b> (vertex) or an <b>edge</b> (relationship). Both carry
 * typed properties in {@code data} and structural metadata specific to graph
 * topology.
 *
 * <p>
 * <b>Features</b><br>
 * <ul>
 * <li><b>Node/Edge Duality</b> — A single record type covers both vertices
 *     and edges, distinguished by {@link GraphElementType}.</li>
 * <li><b>Direction</b> — Edges support directed, undirected, and bidirectional
 *     semantics via {@link EdgeDirection}.</li>
 * <li><b>Labels</b> — Nodes carry a label (e.g. "Person"), edges carry a
 *     relationship type (e.g. "KNOWS").</li>
 * <li><b>Weight</b> — Optional numeric weight for weighted graph algorithms.</li>
 * <li><b>Versioning &amp; Soft Delete</b> — Full CRUD with optimistic locking.</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Create a node
 * GraphRecord person = GraphRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("social-graph")
 *     .elementType(GraphElementType.NODE)
 *     .label("Person")
 *     .data(Map.of("name", "Alice", "age", 30))
 *     .build();
 *
 * // Create an edge
 * GraphRecord knows = GraphRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("social-graph")
 *     .elementType(GraphElementType.EDGE)
 *     .label("KNOWS")
 *     .sourceNodeId(person.getId().toString())
 *     .targetNodeId(bob.getId().toString())
 *     .direction(EdgeDirection.DIRECTED)
 *     .weight(0.85)
 *     .data(Map.of("since", "2023-01-15"))
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Database Table</b><br>
 * <pre>
 * CREATE TABLE graph_records (
 *     id UUID PRIMARY KEY,
 *     tenant_id VARCHAR(255) NOT NULL,
 *     collection_name VARCHAR(255) NOT NULL,
 *     record_type VARCHAR(50) NOT NULL,
 *     data JSONB,
 *     metadata JSONB,
 *     element_type VARCHAR(10) NOT NULL,
 *     label VARCHAR(255) NOT NULL,
 *     source_node_id VARCHAR(255),
 *     target_node_id VARCHAR(255),
 *     direction VARCHAR(20) DEFAULT 'DIRECTED',
 *     weight DOUBLE PRECISION DEFAULT 1.0,
 *     version INTEGER DEFAULT 1,
 *     active BOOLEAN DEFAULT TRUE,
 *     created_at TIMESTAMP,
 *     created_by VARCHAR(255),
 *     updated_at TIMESTAMP,
 *     updated_by VARCHAR(255)
 * );
 * </pre>
 *
 * @see RecordType#GRAPH
 * @see DataRecord
 * @doc.type class
 * @doc.purpose Graph node/edge record with traversal support
 * @doc.layer core
 * @doc.pattern Domain Model, Property Graph
 */
@Entity
@Table(name = "graph_records", indexes = {
    @Index(name = "idx_graph_tenant", columnList = "tenant_id"),
    @Index(name = "idx_graph_collection", columnList = "tenant_id, collection_name"),
    @Index(name = "idx_graph_label", columnList = "tenant_id, collection_name, label"),
    @Index(name = "idx_graph_source", columnList = "tenant_id, source_node_id"),
    @Index(name = "idx_graph_target", columnList = "tenant_id, target_node_id"),
    @Index(name = "idx_graph_active", columnList = "tenant_id, collection_name, active")
})
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class GraphRecord extends DataRecord {

    // ═══════════════════════════════════════════════════════════════
    // Enumerations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Distinguishes graph nodes from edges.
     */
    public enum GraphElementType {
        /** A vertex in the graph — holds properties, connects via edges. */
        NODE,
        /** A relationship between two nodes — carries properties and direction. */
        EDGE
    }

    /**
     * Directionality of an edge.
     */
    public enum EdgeDirection {
        /** Source → Target only. */
        DIRECTED,
        /** Equivalent in both directions. */
        UNDIRECTED,
        /** Explicitly traversable in both directions (two logical edges). */
        BIDIRECTIONAL
    }

    // ═══════════════════════════════════════════════════════════════
    // Graph-specific Fields
    // ═══════════════════════════════════════════════════════════════

    /**
     * Whether this record represents a node or an edge.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false, length = 10)
    @Builder.Default
    private GraphElementType elementType = GraphElementType.NODE;

    /**
     * Label for the node or edge.
     * <p>
     * For nodes: typically a type such as "Person", "Product".
     * For edges: a relationship type such as "KNOWS", "PURCHASED".
     */
    @Column(name = "label", nullable = false, length = 255)
    private String label;

    /**
     * ID of the source (outgoing) node for edges.
     * <p>
     * Null for node records.
     */
    @Column(name = "source_node_id", length = 255)
    private String sourceNodeId;

    /**
     * ID of the target (incoming) node for edges.
     * <p>
     * Null for node records.
     */
    @Column(name = "target_node_id", length = 255)
    private String targetNodeId;

    /**
     * Edge direction semantics.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20)
    @Builder.Default
    private EdgeDirection direction = EdgeDirection.DIRECTED;

    /**
     * Optional numeric weight for weighted graph algorithms (PageRank, shortest path).
     */
    @Column(name = "weight")
    @Builder.Default
    private Double weight = 1.0;

    // ═══════════════════════════════════════════════════════════════
    // Entity Lifecycle (same as EntityRecord — mutable, versioned)
    // ═══════════════════════════════════════════════════════════════

    @Version
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    // ═══════════════════════════════════════════════════════════════
    // RecordType
    // ═══════════════════════════════════════════════════════════════

    @Override
    public RecordType getRecordType() {
        return RecordType.GRAPH;
    }

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (recordType == null) recordType = RecordType.GRAPH;
        if (elementType == null) elementType = GraphElementType.NODE;
        if (direction == null) direction = EdgeDirection.DIRECTED;
        if (weight == null) weight = 1.0;
        if (version == null) version = 1;
        if (active == null) active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.Instant.now();
    }

    // ═══════════════════════════════════════════════════════════════
    // Graph query helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Is this record a node?
     */
    public boolean isNode() {
        return elementType == GraphElementType.NODE;
    }

    /**
     * Is this record an edge?
     */
    public boolean isEdge() {
        return elementType == GraphElementType.EDGE;
    }

    /**
     * Whether this edge connects the given node (as source or target).
     *
     * @param nodeId node ID to check
     * @return true if this edge references the node
     */
    public boolean connectsNode(String nodeId) {
        if (!isEdge() || nodeId == null) return false;
        return nodeId.equals(sourceNodeId) || nodeId.equals(targetNodeId);
    }

    /**
     * Soft-delete this graph record.
     *
     * @param deletedBy user performing the delete
     */
    public void softDelete(String deletedBy) {
        this.active = false;
        this.updatedBy = deletedBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Restore a soft-deleted graph record.
     *
     * @param restoredBy user performing the restore
     */
    public void restore(String restoredBy) {
        this.active = true;
        this.updatedBy = restoredBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if this record is soft-deleted.
     *
     * @return true if inactive
     */
    public boolean isDeleted() {
        return !Boolean.TRUE.equals(active);
    }

    @Override
    public String toString() {
        return "GraphRecord{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", collectionName='" + collectionName + '\''
                + ", elementType=" + elementType
                + ", label='" + label + '\''
                + (isEdge() ? ", source='" + sourceNodeId + "', target='" + targetNodeId + "'" : "")
                + ", version=" + version
                + ", active=" + active
                + '}';
    }
}
