package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnector;
import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnectorStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for Meta Ads connector persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for Meta Ads connector storage (DMOS-F4-001)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmMetaAdsConnectorRepository {

    Promise<DmMetaAdsConnector> save(DmMetaAdsConnector connector);

    Promise<DmMetaAdsConnector> update(DmMetaAdsConnector connector);

    Promise<Optional<DmMetaAdsConnector>> findById(String id);

    Promise<List<DmMetaAdsConnector>> listByTenant(String tenantId);

    Promise<List<DmMetaAdsConnector>> listByStatus(String tenantId, DmMetaAdsConnectorStatus status);
}
