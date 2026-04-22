package com.ghatana.yappc.services.artifact;

import java.time.Instant;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Immutable snapshot of an artifact product model at a point in time, enabling Git-like versioning and merge provenance.
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
public record ArtifactModelVersion(
    String versionId,
    String productId,
    String tenantId,
    String parentVersionId,
    String commitMessage,
    Instant committedAt,
    String committedBy,
    Map<String, Object> modelSnapshot,
    long nodeCount,
    long edgeCount,
    Map<String, String> mergeProvenance
) {
}
