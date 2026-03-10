package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generic repository adapter for YAPPC entities using data-cloud.
 *
 * <p>Provides CRUD operations for YAPPC domain objects backed by data-cloud entity storage.
 * All operations resolve the tenant ID from {@link TenantContext} at call-time to ensure proper
 * multi-tenant data isolation. No hardcoded tenant identifier is used.
 *
 * @param <T> Entity type
 * @doc.type class
 * @doc.purpose Generic data-cloud repository adapter with proper tenant isolation
 * @doc.layer infrastructure
 * @doc.pattern Repository/Adapter
 */
public class YappcDataCloudRepository<T> {

    private static final Logger LOG = LoggerFactory.getLogger(YappcDataCloudRepository.class);

    private final EntityRepository entityRepository;
    private final YappcEntityMapper mapper;
    private final String collectionName;
    private final Class<T> entityClass;

    public YappcDataCloudRepository(
            @NotNull EntityRepository entityRepository,
            @NotNull YappcEntityMapper mapper,
            @NotNull String collectionName,
            @NotNull Class<T> entityClass) {
        this.entityRepository = entityRepository;
        this.mapper = mapper;
        this.collectionName = collectionName;
        this.entityClass = entityClass;
    }

    /**
     * Resolves the current tenant ID from {@link TenantContext}. Throws {@link
     * SecurityException} if no tenant context is active, preventing cross-tenant data access.
     *
     * @return current tenant ID, never null or blank
     * @throws SecurityException if no tenant context is set
     */
    private String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException(
                    "YappcDataCloudRepository requires an active tenant context. "
                            + "Ensure ApiKeyAuthFilter or TenantExtractionFilter is applied.");
        }
        return tenantId;
    }

    /**
     * Saves an entity to data-cloud under the current tenant.
     */
    @NotNull
    public Promise<T> save(@NotNull T domainEntity) {
        String tenantId = resolveTenantId();
        Entity entity = mapper.toEntity(domainEntity, collectionName, tenantId);

        return entityRepository.save(tenantId, entity)
                .map(saved -> mapper.fromEntity(saved, entityClass));
    }

    /**
     * Finds an entity by ID, scoped to the current tenant.
     */
    @NotNull
    public Promise<Optional<T>> findById(@NotNull UUID id) {
        String tenantId = resolveTenantId();
        return entityRepository.findById(tenantId, collectionName, id)
                .map(opt -> opt.map(entity -> mapper.fromEntity(entity, entityClass)));
    }

    /**
     * Finds all entities in the collection, scoped to the current tenant.
     */
    @NotNull
    public Promise<List<T>> findAll() {
        String tenantId = resolveTenantId();
        return entityRepository.findAll(tenantId, collectionName, Map.of(), null, 0, 1000)
                .map(entities -> entities.stream()
                        .map(entity -> mapper.fromEntity(entity, entityClass))
                        .collect(Collectors.toList()));
    }

    /**
     * Deletes an entity by ID, scoped to the current tenant.
     */
    @NotNull
    public Promise<Void> deleteById(@NotNull UUID id) {
        String tenantId = resolveTenantId();
        return entityRepository.delete(tenantId, collectionName, id);
    }

    /**
     * Finds entities by filter criteria, scoped to the current tenant.
     *
     * @param filter the filter criteria (field name -&gt; value)
     * @param sort   the sort expression (optional, can be null)
     * @param limit  maximum results to return
     * @param offset offset for pagination
     * @return Promise of list of matching entities
     */
    @NotNull
    public Promise<List<T>> findByFilter(
            @NotNull Map<String, Object> filter,
            String sort,
            int limit,
            int offset) {
        String tenantId = resolveTenantId();
        return entityRepository.findAll(tenantId, collectionName, filter, sort, offset, limit)
                .map(entities -> entities.stream()
                        .map(entity -> mapper.fromEntity(entity, entityClass))
                        .collect(Collectors.toList()));
    }

    /**
     * Finds entities by a single field value, scoped to the current tenant.
     *
     * @param fieldName  the field name to filter by
     * @param fieldValue the field value to match
     * @return Promise of list of matching entities
     */
    @NotNull
    public Promise<List<T>> findByField(
            @NotNull String fieldName,
            @NotNull Object fieldValue) {
        Map<String, Object> filter = new HashMap<>();
        filter.put(fieldName, fieldValue);
        return findByFilter(filter, null, 1000, 0);
    }
}
