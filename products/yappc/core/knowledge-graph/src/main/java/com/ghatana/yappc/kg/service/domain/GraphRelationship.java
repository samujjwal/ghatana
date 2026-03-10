package com.ghatana.yappc.kg.service.domain;

/**
 * Graph relationship record for Knowledge Graph CLI.
 * Stub for compilation — bridges to YAPPC domain model.
 *
 * @deprecated Use {@link com.ghatana.yappc.knowledge.model.YAPPCGraphEdge} instead.
 *             The {@code com.ghatana.yappc.kg} package is deprecated as of 2.0.0
 *             and will be removed in a future release.
 * @doc.type record
 * @doc.purpose Immutable data carrier for graph relationship (deprecated)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public record GraphRelationship(
    String id,
    String sourceId,
    String targetId,
    String type,
    double confidence
) {}
