package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.connector.googleads.GoogleAdsConnectorReadinessState;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Runtime readiness snapshot for a Google Ads connector.
 *
 * @doc.type record
 * @doc.purpose Captures persisted connector status and live Google Ads readiness truth
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DmGoogleAdsConnectorReadiness(
    String connectorId,
    GoogleAdsConnectorReadinessState readinessState,
    DmConnectorStatus connectorStatus,
    String reason,
    Instant checkedAt
) {
    public DmGoogleAdsConnectorReadiness {
        Objects.requireNonNull(connectorId, "connectorId must not be null");
        Objects.requireNonNull(readinessState, "readinessState must not be null");
        Objects.requireNonNull(connectorStatus, "connectorStatus must not be null");
        reason = reason == null ? "" : reason;
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }

    public boolean ready() {
        return readinessState == GoogleAdsConnectorReadinessState.READY
            && connectorStatus == DmConnectorStatus.ACTIVE;
    }
}
