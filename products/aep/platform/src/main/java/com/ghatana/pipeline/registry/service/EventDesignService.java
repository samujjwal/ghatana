package com.ghatana.pipeline.registry.service;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.ConnectorBinding;
import com.ghatana.pipeline.registry.model.SchemaDefinition;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Service for managing event schemas and connector bindings.
 *
 * <p>Purpose: Provides business logic for schema and binding lifecycle
 * operations including CRUD operations with tenant isolation. Currently
 * uses in-memory storage with ConcurrentHashMap for thread safety.</p>
 *
 * @doc.type class
 * @doc.purpose Business logic for event schema and binding management
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public class EventDesignService {

    private final Map<String, SchemaDefinition> schemas = new ConcurrentHashMap<>();
    private final Map<String, ConnectorBinding> bindings = new ConcurrentHashMap<>();

    public SchemaDefinition createSchema(SchemaDefinition schema) {
        schemas.put(schema.getId(), schema);
        return schema;
    }

    public Optional<SchemaDefinition> getSchema(String id) {
        return Optional.ofNullable(schemas.get(id));
    }

    public Collection<SchemaDefinition> listSchemasByTenant(TenantId tenantId) {
        return schemas.values().stream()
                .filter(schema -> tenantId.equals(schema.getTenantId()))
                .toList();
    }

    public Optional<SchemaDefinition> updateSchema(String id, Consumer<SchemaDefinition> updater) {
        SchemaDefinition existing = schemas.get(id);
        if (existing == null) {
            return Optional.empty();
        }
        updater.accept(existing);
        return Optional.of(existing);
    }

    public boolean deleteSchema(String id) {
        return schemas.remove(id) != null;
    }

    public ConnectorBinding createBinding(ConnectorBinding binding) {
        bindings.put(binding.getId(), binding);
        return binding;
    }

    public Optional<ConnectorBinding> getBinding(String id) {
        return Optional.ofNullable(bindings.get(id));
    }

    public Collection<ConnectorBinding> listBindingsByTenant(TenantId tenantId) {
        return bindings.values().stream()
                .filter(binding -> tenantId.equals(binding.getTenantId()))
                .toList();
    }

    public Optional<ConnectorBinding> updateBinding(String id, Consumer<ConnectorBinding> updater) {
        ConnectorBinding existing = bindings.get(id);
        if (existing == null) {
            return Optional.empty();
        }
        updater.accept(existing);
        return Optional.of(existing);
    }

    public boolean deleteBinding(String id) {
        return bindings.remove(id) != null;
    }
}
