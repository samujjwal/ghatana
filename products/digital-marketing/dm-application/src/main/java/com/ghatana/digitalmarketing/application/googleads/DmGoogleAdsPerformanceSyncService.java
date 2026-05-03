package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.performance.DmCampaignPerformanceSnapshot;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for Google Ads performance synchronization.
 *
 * @doc.type class
 * @doc.purpose Syncs Google Ads performance metrics into DMOS snapshots (DMOS-F2-009)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmGoogleAdsPerformanceSyncService {

    Promise<DmCampaignPerformanceSnapshot> syncCampaignPerformance(
        DmOperationContext ctx,
        SyncCampaignPerformanceRequest request
    );

    Promise<Optional<DmCampaignPerformanceSnapshot>> findLatestSnapshot(
        DmOperationContext ctx,
        String internalCampaignId
    );

    /**
     * Request for syncing campaign performance from Google Ads.
     */
    record SyncCampaignPerformanceRequest(
        String connectorId,
        String internalCampaignId,
        Instant periodStart,
        Instant periodEnd
    ) {
        public SyncCampaignPerformanceRequest {
            Objects.requireNonNull(connectorId, "connectorId must not be null");
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(periodStart, "periodStart must not be null");
            Objects.requireNonNull(periodEnd, "periodEnd must not be null");
            if (connectorId.isBlank()) {
                throw new IllegalArgumentException("connectorId must not be blank");
            }
            if (internalCampaignId.isBlank()) {
                throw new IllegalArgumentException("internalCampaignId must not be blank");
            }
            if (periodEnd.isBefore(periodStart)) {
                throw new IllegalArgumentException("periodEnd must be >= periodStart");
            }
        }
    }
}
