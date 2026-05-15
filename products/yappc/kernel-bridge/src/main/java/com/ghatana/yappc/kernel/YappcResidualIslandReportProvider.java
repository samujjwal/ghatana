package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Narrow YAPPC port for residual island reports.
 *
 * @doc.type interface
 * @doc.purpose Expose residual island reports as stable evidence contracts
 * @doc.layer adapter
 * @doc.pattern Port
 */
public interface YappcResidualIslandReportProvider {
    Promise<Map<String, Object>> residualIslandReport(String productUnitId, Map<String, Object> request);
}
