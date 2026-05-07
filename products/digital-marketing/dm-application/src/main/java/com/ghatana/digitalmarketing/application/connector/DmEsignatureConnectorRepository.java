package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.domain.connector.DmEsignatureConnector;
import com.ghatana.digitalmarketing.domain.connector.DmEsignatureConnectorStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for e-signature connector persistence.
 *
 * @doc.type interface
 * @doc.purpose Defines e-signature connector persistence contract (P3-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmEsignatureConnectorRepository {

    Promise<DmEsignatureConnector> save(DmEsignatureConnector connector);

    Promise<DmEsignatureConnector> update(DmEsignatureConnector connector);

    Promise<Optional<DmEsignatureConnector>> findById(String id);

    Promise<List<DmEsignatureConnector>> findByStatus(String tenantId, DmEsignatureConnectorStatus status, int limit);

    Promise<List<DmEsignatureConnector>> findByTenantId(String tenantId, int limit);

    Promise<Void> delete(String id);
}
