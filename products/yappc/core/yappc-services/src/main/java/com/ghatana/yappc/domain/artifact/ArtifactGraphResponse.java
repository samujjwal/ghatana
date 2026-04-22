package com.ghatana.yappc.domain.artifact;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Generic response envelope for artifact graph operations
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record ArtifactGraphResponse(
    boolean success,
    String operation,
    Map<String, Object> result,
    String message
) {
}
