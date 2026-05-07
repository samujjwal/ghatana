package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.domain.connector.DmTikTokAdsConnector;
import com.ghatana.digitalmarketing.domain.connector.DmTikTokAdsConnectorStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for TikTok Ads connector persistence.
 *
 * @doc.type interface
 * @doc.purpose Defines TikTok Ads connector persistence contract (P3-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmTikTokAdsConnectorRepository {

    Promise<DmTikTokAdsConnector> save(DmTikTokAdsConnector connector);

    Promise<DmTikTokAdsConnector> update(DmTikTokAdsConnector connector);

    Promise<Optional<DmTikTokAdsConnector>> findById(String id);

    Promise<List<DmTikTokAdsConnector>> findByStatus(String tenantId, DmTikTokAdsConnectorStatus status, int limit);

    Promise<List<DmTikTokAdsConnector>> findByTenantId(String tenantId, int limit);

    Promise<Void> delete(String id);
}
