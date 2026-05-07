package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.domain.connector.DmCmsConnector;
import com.ghatana.digitalmarketing.domain.connector.DmCmsConnectorStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CMS connector persistence.
 *
 * @doc.type interface
 * @doc.purpose Defines CMS connector persistence contract (P3-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmCmsConnectorRepository {

    Promise<DmCmsConnector> save(DmCmsConnector connector);

    Promise<DmCmsConnector> update(DmCmsConnector connector);

    Promise<Optional<DmCmsConnector>> findById(String id);

    Promise<List<DmCmsConnector>> findByStatus(String tenantId, DmCmsConnectorStatus status, int limit);

    Promise<List<DmCmsConnector>> findByTenantId(String tenantId, int limit);

    Promise<Void> delete(String id);
}
