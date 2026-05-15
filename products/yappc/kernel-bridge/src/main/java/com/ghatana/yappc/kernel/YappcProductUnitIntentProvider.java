package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Narrow YAPPC port for exporting ProductUnitIntent candidates.
 *
 * @doc.type interface
 * @doc.purpose Expose YAPPC ProductUnitIntent candidates without leaking plugin internals
 * @doc.layer adapter
 * @doc.pattern Port
 */
public interface YappcProductUnitIntentProvider {
    Promise<Map<String, Object>> exportProductUnitIntent(String candidateId, Map<String, Object> request);
}
