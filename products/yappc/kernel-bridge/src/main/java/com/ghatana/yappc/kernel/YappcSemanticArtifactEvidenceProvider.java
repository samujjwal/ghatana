package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Narrow YAPPC port for semantic artifact evidence.
 *
 * @doc.type interface
 * @doc.purpose Expose stable semantic artifact evidence without exposing YAPPC scanners
 * @doc.layer adapter
 * @doc.pattern Port
 */
public interface YappcSemanticArtifactEvidenceProvider {
    Promise<Map<String, Object>> semanticArtifactEvidence(String artifactId, Map<String, Object> request);
}
