package com.ghatana.kg.core;

import java.time.Instant;

/**
 * Knowledge Graph model for CLI operations.
 * Stub for compilation — bridges to YAPPC domain model.
 
 * @doc.type class
 * @doc.purpose Handles knowledge graph operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class KnowledgeGraph {

    private final String id;
    private final String name;
    private final String description;
    private final int nodeCount;
    private final int edgeCount;
    private final Instant createdAt;
    private final Instant updatedAt;

    public KnowledgeGraph(String id, String name, String description, int nodeCount, int edgeCount) {
        this(id, name, description, nodeCount, edgeCount, Instant.now(), Instant.now());
    }

    public KnowledgeGraph(String id, String name, String description, int nodeCount, int edgeCount,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getNodeCount() { return nodeCount; }
    public int getEdgeCount() { return edgeCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean hasPath(String sourceId, String targetId) { return false; }
}
