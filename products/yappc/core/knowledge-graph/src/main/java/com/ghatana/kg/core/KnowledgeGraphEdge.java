package com.ghatana.kg.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Knowledge Graph edge model for CLI creation.
 *
 * @deprecated This is a stub for compilation that bridges to YAPPC domain model.
 *             Use {@link com.ghatana.yappc.knowledge.model.YAPPCGraphEdge} instead.
 *             This stub is retained for compilation compatibility only and will be removed in a future release.
 *             Set system property "kg.core.stub.enabled=false" to disable stub usage at runtime.
 *
 * @doc.type class
 * @doc.purpose Handles knowledge graph edge operations
 * @doc.layer core
 * @doc.pattern ValueObject
 */
@Deprecated(since = "2026-03-27", forRemoval = true)
public class KnowledgeGraphEdge {

    private static final boolean STUB_ENABLED = Boolean.parseBoolean(
        System.getProperty("kg.core.stub.enabled", "true")
    );

    private final String sourceNodeId;
    private final String targetNodeId;
    private final String relationshipType;
    private final double weight;
    private final Map<String, String> properties;

    private KnowledgeGraphEdge(Builder builder) {
        if (!STUB_ENABLED) {
            throw new IllegalStateException(
                "KnowledgeGraphEdge stub is disabled. " +
                "Set kg.core.stub.enabled=true or use com.ghatana.yappc.knowledge.model.YAPPCGraphEdge instead."
            );
        }
        this.sourceNodeId = builder.sourceNodeId;
        this.targetNodeId = builder.targetNodeId;
        this.relationshipType = builder.relationshipType;
        this.weight = builder.weight;
        this.properties = builder.properties;
    }

    public String getSourceNodeId() { return sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public String getRelationshipType() { return relationshipType; }
    public double getWeight() { return weight; }
    public Map<String, String> getProperties() { return properties; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourceNodeId;
        private String targetNodeId;
        private String relationshipType;
        private double weight = 1.0;
        private Map<String, String> properties = new HashMap<>();

        public Builder sourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; return this; }
        public Builder targetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; return this; }
        public Builder relationshipType(String relationshipType) { this.relationshipType = relationshipType; return this; }
        public Builder weight(double weight) { this.weight = weight; return this; }
        public Builder property(String key, String value) { this.properties.put(key, value); return this; }

        public KnowledgeGraphEdge build() {
            return new KnowledgeGraphEdge(this);
        }
    }
}
