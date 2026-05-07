/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.store;

import com.ghatana.pipeline.registry.model.ConnectorBinding;
import com.ghatana.pipeline.registry.model.SchemaDefinition;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Durable storage interface for event design artifacts (schemas and bindings).
 *
 * <p>T-18: Replaces in-memory ConcurrentHashMap storage with durable backend
 * (Data Cloud or PostgreSQL). Implementations must provide tenant-isolated
 * persistence with async ActiveJ Promise-based APIs.</p>
 *
 * @doc.type interface
 * @doc.purpose Durable storage for event schemas and connector bindings
 * @doc.layer product
 * @doc.pattern Repository, Port
 * @since 2.0.0
 */
public interface EventDesignStore {

    // =========================================================================
    // Schema Operations
    // =========================================================================

    /**
     * Creates or updates a schema definition.
     *
     * @param schema the schema to save
     * @return Promise completing with the saved schema
     */
    Promise<SchemaDefinition> saveSchema(SchemaDefinition schema);

    /**
     * Retrieves a schema by ID.
     *
     * @param id the schema ID
     * @param tenantId the tenant scope
     * @return Promise completing with optional schema
     */
    Promise<Optional<SchemaDefinition>> findSchemaById(String id, TenantId tenantId);

    /**
     * Lists all schemas for a tenant.
     *
     * @param tenantId the tenant scope
     * @return Promise completing with collection of schemas
     */
    Promise<Collection<SchemaDefinition>> listSchemasByTenant(TenantId tenantId);

    /**
     * Lists schemas for a tenant with pagination.
     *
     * @param tenantId the tenant scope
     * @param cursor pagination cursor (null for first page)
     * @param pageSize maximum items to return
     * @return Promise completing with paginated schema list
     */
    Promise<SchemaListPage> listSchemasByTenant(TenantId tenantId, String cursor, int pageSize);

    /**
     * Updates a schema with the given mutation function.
     *
     * @param id the schema ID
     * @param tenantId the tenant scope
     * @param updater the mutation function
     * @return Promise completing with optional updated schema
     */
    Promise<Optional<SchemaDefinition>> updateSchema(String id, TenantId tenantId, Consumer<SchemaDefinition> updater);

    /**
     * Deletes a schema by ID.
     *
     * @param id the schema ID
     * @param tenantId the tenant scope
     * @return Promise completing with true if deleted, false if not found
     */
    Promise<Boolean> deleteSchema(String id, TenantId tenantId);

    // =========================================================================
    // Binding Operations
    // =========================================================================

    /**
     * Creates or updates a connector binding.
     *
     * @param binding the binding to save
     * @return Promise completing with the saved binding
     */
    Promise<ConnectorBinding> saveBinding(ConnectorBinding binding);

    /**
     * Retrieves a binding by ID.
     *
     * @param id the binding ID
     * @param tenantId the tenant scope
     * @return Promise completing with optional binding
     */
    Promise<Optional<ConnectorBinding>> findBindingById(String id, TenantId tenantId);

    /**
     * Lists all bindings for a tenant.
     *
     * @param tenantId the tenant scope
     * @return Promise completing with collection of bindings
     */
    Promise<Collection<ConnectorBinding>> listBindingsByTenant(TenantId tenantId);

    /**
     * Lists bindings for a tenant with pagination.
     *
     * @param tenantId the tenant scope
     * @param cursor pagination cursor (null for first page)
     * @param pageSize maximum items to return
     * @return Promise completing with paginated binding list
     */
    Promise<BindingListPage> listBindingsByTenant(TenantId tenantId, String cursor, int pageSize);

    /**
     * Updates a binding with the given mutation function.
     *
     * @param id the binding ID
     * @param tenantId the tenant scope
     * @param updater the mutation function
     * @return Promise completing with optional updated binding
     */
    Promise<Optional<ConnectorBinding>> updateBinding(String id, TenantId tenantId, Consumer<ConnectorBinding> updater);

    /**
     * Deletes a binding by ID.
     *
     * @param id the binding ID
     * @param tenantId the tenant scope
     * @return Promise completing with true if deleted, false if not found
     */
    Promise<Boolean> deleteBinding(String id, TenantId tenantId);

    /**
     * Finds bindings associated with a specific schema.
     *
     * @param schemaId the schema ID
     * @param tenantId the tenant scope
     * @return Promise completing with matching bindings
     */
    Promise<Collection<ConnectorBinding>> findBindingsBySchema(String schemaId, TenantId tenantId);

    // =========================================================================
    // Pagination Result Types
    // =========================================================================

    /**
     * Paginated schema list result.
     *
     * @param items the schema items
     * @param nextCursor cursor for next page (null if no more items)
     * @param totalCount total number of items (may be estimated)
     */
    record SchemaListPage(
        Collection<SchemaDefinition> items,
        String nextCursor,
        long totalCount
    ) {}

    /**
     * Paginated binding list result.
     *
     * @param items the binding items
     * @param nextCursor cursor for next page (null if no more items)
     * @param totalCount total number of items (may be estimated)
     */
    record BindingListPage(
        Collection<ConnectorBinding> items,
        String nextCursor,
        long totalCount
    ) {}
}
