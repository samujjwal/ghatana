package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
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
 * <p>Provides CRUD operations for YAPPC domain objects backed by
 * data-cloud entity storage.
 * 
 * @param <T> Entity type
 * @doc.type class
 * @doc.purpose Generic data-cloud repository adapter
 * @doc.layer infrastructure
 * @doc.pattern Repository/Adapter
 */
public class YappcDataCloudRepository<T> {
    
    private static final Logger LOG = LoggerFactory.getLogger(YappcDataCloudRepository.class);
    private static final String DEFAULT_TENANT = "default";
    
    private final EntityRepository entityRepository;
    private final YappcEntityMapper mapper;
    private final String collectionName;
    private final Class<T> entityClass;
    
    public YappcDataCloudRepository(
        @NotNull EntityRepository entityRepository,
        @NotNull YappcEntityMapper mapper,
        @NotNull String collectionName,
        @NotNull Class<T> entityClass
    ) {
        this.entityRepository = entityRepository;
        this.mapper = mapper;
        this.collectionName = collectionName;
        this.entityClass = entityClass;
    }
    
    /**
     * Saves an entity to data-cloud.
     */
    @NotNull
    public Promise<T> save(@NotNull T domainEntity) {
        Entity entity = mapper.toEntity(domainEntity, collectionName, DEFAULT_TENANT);
        
        return entityRepository.save(DEFAULT_TENANT, entity)
            .map(saved -> mapper.fromEntity(saved, entityClass));
    }
    
    /**
     * Finds an entity by ID.
     */
    @NotNull
    public Promise<Optional<T>> findById(@NotNull UUID id) {
        return entityRepository.findById(DEFAULT_TENANT, collectionName, id)
            .map(opt -> opt.map(entity -> mapper.fromEntity(entity, entityClass)));
    }
    
    /**
     * Finds all entities in the collection.
     */
    @NotNull
    public Promise<List<T>> findAll() {
        return entityRepository.findAll(DEFAULT_TENANT, collectionName, Map.of(), null, 0, 1000)
            .map(entities -> entities.stream()
                .map(entity -> mapper.fromEntity(entity, entityClass))
                .collect(Collectors.toList()));
    }
    
    /**
     * Deletes an entity by ID.
     */
    @NotNull
    public Promise<Void> deleteById(@NotNull UUID id) {
        return entityRepository.delete(DEFAULT_TENANT, collectionName, id);
    }
    
    /**
     * Finds entities by filter criteria.
     * 
     * @param filter the filter criteria (field name -> value)
     * @param sort the sort expression (optional, can be null)
     * @param limit maximum results to return
     * @param offset offset for pagination
     * @return Promise of list of matching entities
     */
    @NotNull
    public Promise<List<T>> findByFilter(
            @NotNull Map<String, Object> filter,
            String sort,
            int limit,
            int offset) {
        return entityRepository.findAll(DEFAULT_TENANT, collectionName, filter, sort, offset, limit)
            .map(entities -> entities.stream()
                .map(entity -> mapper.fromEntity(entity, entityClass))
                .collect(Collectors.toList()));
    }
    
    /**
     * Finds entities by a single field value.
     * 
     * @param fieldName the field name to filter by
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
