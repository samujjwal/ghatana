package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

/**
 * Service contract for Google Ads connector runtime readiness.
 *
 * @doc.type interface
 * @doc.purpose Defines live Google Ads readiness checks backed by persisted connector status
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmGoogleAdsConnectorReadinessService {

    Promise<DmGoogleAdsConnectorReadiness> checkReadiness(DmOperationContext ctx, String connectorId);
}
