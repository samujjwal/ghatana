package com.ghatana.yappc.kg.service.domain;

import java.util.Set;

/**
 * Graph node record for Knowledge Graph CLI.
 * Stub for compilation — bridges to YAPPC domain model.
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for graph node
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public record GraphNode(
    String id,
    String label,
    Set<String> types,
    Set<String> tags
) {}
