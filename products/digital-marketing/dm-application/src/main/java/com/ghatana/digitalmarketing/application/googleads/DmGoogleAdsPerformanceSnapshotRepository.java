package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.domain.performance.DmCampaignPerformanceSnapshot;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Persistence port for Google Ads campaign performance snapshots.
 *
 * @doc.type class
 * @doc.purpose Stores synced Google Ads performance snapshots for campaigns (DMOS-F2-009)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmGoogleAdsPerformanceSnapshotRepository {

    Promise<DmCampaignPerformanceSnapshot> save(DmCampaignPerformanceSnapshot snapshot);

    Promise<Optional<DmCampaignPerformanceSnapshot>> findLatestByExternalCampaignId(String externalCampaignId);
}
