package com.ghatana.pipeline.registry.service;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.ConnectorInstance;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Service for managing connector instances.
 *
 * <p>Purpose: Provides business logic for connector instance lifecycle
 * operations including CRUD operations with tenant isolation. Currently
 * uses in-memory storage with ConcurrentHashMap for thread safety.</p>
 *
 * @doc.type class
 * @doc.purpose Business logic for connector instance management
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public class ConnectorAdminService {

    private final Map<String, ConnectorInstance> connectors = new ConcurrentHashMap<>();

    public ConnectorInstance create(ConnectorInstance connector) {
        connectors.put(connector.getId(), connector);
        return connector;
    }

    public Optional<ConnectorInstance> get(String id) {
        return Optional.ofNullable(connectors.get(id));
    }

    public Collection<ConnectorInstance> listByTenant(TenantId tenantId) {
        return connectors.values().stream()
                .filter(connector -> tenantId.equals(connector.getTenantId()))
                .toList();
    }

    public Optional<ConnectorInstance> update(String id, Consumer<ConnectorInstance> updater) {
        ConnectorInstance existing = connectors.get(id);
        if (existing == null) {
            return Optional.empty();
        }
        updater.accept(existing);
        return Optional.of(existing);
    }

    public boolean delete(String id) {
        return connectors.remove(id) != null;
    }
}
