package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Narrow YAPPC port for risk hotspot reports.
 *
 * @doc.type interface
 * @doc.purpose Expose risk hotspot reports as stable evidence contracts
 * @doc.layer adapter
 * @doc.pattern Port
 */
public interface YappcRiskHotspotReportProvider {
    Promise<Map<String, Object>> riskHotspotReport(String productUnitId, Map<String, Object> request);
}
