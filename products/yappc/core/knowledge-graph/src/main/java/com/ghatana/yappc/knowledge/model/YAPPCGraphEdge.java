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
        OWNS, MANAGES, APPROVES, REVIEWS
    }
}
