package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.domain.connector.DmYouTubeCtvConnector;
import com.ghatana.digitalmarketing.domain.connector.DmYouTubeCtvConnectorStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for YouTube/CTV connector persistence.
 *
 * @doc.type interface
 * @doc.purpose Defines YouTube/CTV connector persistence contract (P3-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmYouTubeCtvConnectorRepository {

    Promise<DmYouTubeCtvConnector> save(DmYouTubeCtvConnector connector);

    Promise<DmYouTubeCtvConnector> update(DmYouTubeCtvConnector connector);

    Promise<Optional<DmYouTubeCtvConnector>> findById(String id);

    Promise<List<DmYouTubeCtvConnector>> findByStatus(String tenantId, DmYouTubeCtvConnectorStatus status, int limit);

    Promise<List<DmYouTubeCtvConnector>> findByTenantId(String tenantId, int limit);

    Promise<Void> delete(String id);
}
