package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.domain.connector.DmSeoToolConnector;
import com.ghatana.digitalmarketing.domain.connector.DmSeoToolConnectorStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for SEO tool connector persistence.
 *
 * @doc.type interface
 * @doc.purpose Defines SEO tool connector persistence contract (P3-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmSeoToolConnectorRepository {

    Promise<DmSeoToolConnector> save(DmSeoToolConnector connector);

    Promise<DmSeoToolConnector> update(DmSeoToolConnector connector);

    Promise<Optional<DmSeoToolConnector>> findById(String id);

    Promise<List<DmSeoToolConnector>> findByStatus(String tenantId, DmSeoToolConnectorStatus status, int limit);

    Promise<List<DmSeoToolConnector>> findByTenantId(String tenantId, int limit);

    Promise<Void> delete(String id);
}
