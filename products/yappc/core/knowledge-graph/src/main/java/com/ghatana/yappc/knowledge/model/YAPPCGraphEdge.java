package com.ghatana.yappc.knowledge.model;

import lombok.Builder;

import java.util.Map;

@Builder
/**
 * @doc.type record
 * @doc.purpose Immutable data carrier for yappc graph edge
 * @doc.layer core
 * @doc.pattern Enum
 */
public record YAPPCGraphEdge(
    String id,
    String sourceNodeId,
    String targetNodeId,
    YAPPCRelationshipType relationshipType,
    Map<String, Object> properties,
    YAPPCGraphMetadata metadata
) {
    public enum YAPPCRelationshipType {
        DEPENDS_ON, IMPLEMENTS, EXTENDS, IMPORTS,
        CALLS, USES, CONFIGURES, TESTS,
        OWNS, MANAGES, APPROVES, REVIEWS,
        // Artifact Compiler Relationships
        RENDERS_IN, CONTAINS_FIELD, REFERENCES_TABLE,
        TRIGGERS_MIGRATION, IMPORTS_MODULE, EXPORTS_SYMBOL,
        DEFINED_IN, ACCESSES_STATE, READS_FROM, WRITES_TO,
        INVOKES_API, STYLED_BY, DOCUMENTED_BY
    }
}
