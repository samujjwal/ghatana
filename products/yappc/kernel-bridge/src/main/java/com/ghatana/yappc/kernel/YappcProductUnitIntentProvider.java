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

    /**
     * Exports a typed ProductUnitIntent contract for Kernel handoff.
     *
     * <p>The default implementation preserves compatibility with older providers
     * while new providers can override it to avoid map-shaped handoff internally.</p>
     */
    default Promise<ProductUnitIntentContract> exportTypedProductUnitIntent(
            String candidateId,
            Map<String, Object> request) {
        return exportProductUnitIntent(candidateId, request)
                .map(intent -> ProductUnitIntentContract.fromRequest(candidateId, intent, "yappc-product-unit-intent-provider"));
    }
}
