package com.ghatana.yappc.knowledge.model;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * YAPPC-specific graph node model.
 * 
 * @doc.type record
 * @doc.purpose YAPPC graph node representation
 * @doc.layer domain
 */
@Builder
/**
 * @doc.type record
 * @doc.purpose Immutable data carrier for yappc graph node
 * @doc.layer core
 * @doc.pattern Enum
 */
public record YAPPCGraphNode(
    String id,
    YAPPCNodeType type,
    String name,
    String description,
    Map<String, Object> properties,
    Set<String> tags,
    YAPPCGraphMetadata metadata
) {
    public enum YAPPCNodeType {
        CLASS, INTERFACE, SERVICE, COMPONENT,
        CONFIG, TEST, DOCUMENT, WORKSPACE,
        USER, TEAM, PROJECT, API, DATABASE
    }
}
