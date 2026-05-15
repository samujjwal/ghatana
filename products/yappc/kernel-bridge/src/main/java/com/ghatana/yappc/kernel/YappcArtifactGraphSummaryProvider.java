package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Narrow YAPPC port for artifact graph summaries.
 *
 * @doc.type interface
 * @doc.purpose Expose stable artifact graph summaries without leaking graph internals
 * @doc.layer adapter
 * @doc.pattern Port
 */
public interface YappcArtifactGraphSummaryProvider {
    Promise<Map<String, Object>> artifactGraphSummary(String productUnitId, Map<String, Object> request);
}
