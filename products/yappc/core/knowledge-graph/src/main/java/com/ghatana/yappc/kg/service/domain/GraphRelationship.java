package com.ghatana.yappc.kg.service.domain;

/**
 * Graph relationship record for Knowledge Graph CLI.
 * Stub for compilation — bridges to YAPPC domain model.
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for graph relationship
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public record GraphRelationship(
    String id,
    String sourceId,
    String targetId,
    String type,
    double confidence
) {}
