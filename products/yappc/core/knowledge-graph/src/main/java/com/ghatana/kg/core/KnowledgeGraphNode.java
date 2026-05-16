package com.ghatana.kg.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Knowledge Graph node model for CLI creation.
 *
 * @deprecated This is a stub for compilation that bridges to YAPPC domain model.
 *             Use {@link com.ghatana.yappc.knowledge.model.YAPPCGraphNode} instead.
 *             This stub is retained for compilation compatibility only and will be removed in a future release.
 *             Set system property "kg.core.stub.enabled=false" to disable stub usage at runtime.
 *
 * @doc.type class
 * @doc.purpose Handles knowledge graph node operations
 * @doc.layer core
 * @doc.pattern ValueObject
 */
@Deprecated(since = "2026-03-27", forRemoval = true)
public class KnowledgeGraphNode {

    private static final boolean STUB_ENABLED = Boolean.parseBoolean(
        System.getProperty("kg.core.stub.enabled", "true")
    );

    private final String label;
    private final String nodeType;
    private final String description;
    private final String sourceUri;
    private final Set<String> tags;
    private final Map<String, String> properties;

    private KnowledgeGraphNode(Builder builder) {
        if (!STUB_ENABLED) {
            throw new IllegalStateException(
                "KnowledgeGraphNode stub is disabled. " +
                "Set kg.core.stub.enabled=true or use com.ghatana.yappc.knowledge.model.YAPPCGraphNode instead."
            );
        }
        this.label = builder.label;
        this.nodeType = builder.nodeType;
        this.description = builder.description;
        this.sourceUri = builder.sourceUri;
        this.tags = builder.tags;
        this.properties = builder.properties;
    }

    public String getLabel() { return label; }
    public String getNodeType() { return nodeType; }
    public String getDescription() { return description; }
    public String getSourceUri() { return sourceUri; }
    public Set<String> getTags() { return tags; }
    public Map<String, String> getProperties() { return properties; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String label;
        private String nodeType;
        private String description;
        private String sourceUri;
        private Set<String> tags = new HashSet<>();
        private Map<String, String> properties = new HashMap<>();

        public Builder label(String label) { this.label = label; return this; }
        public Builder nodeType(String nodeType) { this.nodeType = nodeType; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder sourceUri(String sourceUri) { this.sourceUri = sourceUri; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags; return this; }
        public Builder property(String key, String value) { this.properties.put(key, value); return this; }

        public KnowledgeGraphNode build() {
            return new KnowledgeGraphNode(this);
        }
    }
}
