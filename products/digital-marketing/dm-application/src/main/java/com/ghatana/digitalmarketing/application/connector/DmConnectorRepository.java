package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link DmConnectorConfig}.
 *
 * @doc.type class
 * @doc.purpose Repository contract for connector configuration storage (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmConnectorRepository {

    Promise<DmConnectorConfig> save(DmConnectorConfig connector);

    Promise<Optional<DmConnectorConfig>> findById(String id);

    Promise<List<DmConnectorConfig>> findByType(String tenantId, DmConnectorType type, int limit);

    Promise<List<DmConnectorConfig>> findByStatus(String tenantId, DmConnectorStatus status, int limit);

    Promise<DmConnectorConfig> update(DmConnectorConfig connector);

    Promise<Long> countByStatus(String tenantId, DmConnectorStatus status);
}
