package com.ghatana.digitalmarketing.infra.connector;

import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link DmConnectorRepository} for local and test profiles.
 *
 * @doc.type class
 * @doc.purpose In-memory connector repository for local development and testing
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DmConnectorInMemoryRepository implements DmConnectorRepository {

    private final ConcurrentHashMap<String, DmConnectorConfig> store = new ConcurrentHashMap<>();

    @Override
    public Promise<DmConnectorConfig> save(DmConnectorConfig connector) {
        store.put(connector.getId(), connector);
        return Promise.of(connector);
    }

    @Override
    public Promise<Optional<DmConnectorConfig>> findById(String id) {
        return Promise.of(Optional.ofNullable(store.get(id)));
    }

    @Override
    public Promise<List<DmConnectorConfig>> findByType(String tenantId, DmConnectorType type, int limit) {
        List<DmConnectorConfig> result = store.values().stream()
            .filter(c -> tenantId.equals(c.getTenantId()) && type == c.getConnectorType())
            .limit(limit)
            .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    public Promise<List<DmConnectorConfig>> findByStatus(String tenantId, DmConnectorStatus status, int limit) {
        List<DmConnectorConfig> result = store.values().stream()
            .filter(c -> tenantId.equals(c.getTenantId()) && status == c.getStatus())
            .limit(limit)
            .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    public Promise<DmConnectorConfig> update(DmConnectorConfig connector) {
        store.put(connector.getId(), connector);
        return Promise.of(connector);
    }

    @Override
    public Promise<Long> countByStatus(String tenantId, DmConnectorStatus status) {
        long count = store.values().stream()
            .filter(c -> tenantId.equals(c.getTenantId()) && status == c.getStatus())
            .count();
        return Promise.of(count);
    }
}

