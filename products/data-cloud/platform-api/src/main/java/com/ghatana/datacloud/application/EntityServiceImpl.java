/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of EntityService for CRUD operations on entities.
 *
 * @doc.type class
 * @doc.purpose Concrete implementation of entity operations with metrics
 * @doc.layer application
 * @doc.pattern Service Implementation
 */
public class EntityServiceImpl implements EntityService {

    private static final Logger log = LoggerFactory.getLogger(EntityServiceImpl.class);

    private final EntityRepository repository;
    private final MetricsCollector metrics;

    public EntityServiceImpl(EntityRepository repository, MetricsCollector metrics) {
        this.repository = Objects.requireNonNull(repository, "Repository required");
        this.metrics = Objects.requireNonNull(metrics, "Metrics required");
    }

    @Override
    public Promise<Entity> createEntity(String tenantId, String collectionName, 
                                        Map<String, Object> data, String userId) {
        validateInputs(tenantId, collectionName, data, userId);

        Entity entity = Entity.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .collectionName(collectionName)
            .data(data)
            .createdBy(userId)
            .updatedBy(userId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(1)
            .build();

        return repository.save(entity)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("entity.create.success",
                        "tenant", tenantId, "collection", collectionName);
                    log.info("Entity created: id={}, tenant={}, collection={}",
                        result.getId(), tenantId, collectionName);
                } else {
                    metrics.incrementCounter("entity.create.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    @Override
    public Promise<Entity> updateEntity(String tenantId, String collectionName, 
                                        UUID entityId, Map<String, Object> data, String userId) {
        validateInputs(tenantId, collectionName, userId);
        Objects.requireNonNull(entityId, "Entity ID required");

        return repository.findById(tenantId, collectionName, entityId)
            .then(existing -> {
                if (existing == null) {
                    return Promise.ofException(
                        new IllegalArgumentException("Entity not found: " + entityId));
                }

                Entity updated = Entity.builder()
                    .id(existing.getId())
                    .tenantId(existing.getTenantId())
                    .collectionName(existing.getCollectionName())
                    .data(data)
                    .createdBy(existing.getCreatedBy())
                    .createdAt(existing.getCreatedAt())
                    .updatedBy(userId)
                    .updatedAt(Instant.now())
                    .version(existing.getVersion() + 1)
                    .build();

                return repository.save(updated);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("entity.update.success",
                        "tenant", tenantId, "collection", collectionName);
                } else {
                    metrics.incrementCounter("entity.update.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    @Override
    public Promise<Entity> getEntity(String tenantId, String collectionName, UUID entityId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name required");
        Objects.requireNonNull(entityId, "Entity ID required");

        return repository.findById(tenantId, collectionName, entityId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    if (result != null) {
                        metrics.incrementCounter("entity.get.success",
                            "tenant", tenantId, "collection", collectionName);
                    } else {
                        metrics.incrementCounter("entity.get.not_found",
                            "tenant", tenantId, "collection", collectionName);
                    }
                } else {
                    metrics.incrementCounter("entity.get.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    @Override
    public Promise<Void> deleteEntity(String tenantId, String collectionName, 
                                       UUID entityId, String userId) {
        validateInputs(tenantId, collectionName, userId);
        Objects.requireNonNull(entityId, "Entity ID required");

        return repository.delete(tenantId, collectionName, entityId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("entity.delete.success",
                        "tenant", tenantId, "collection", collectionName);
                } else {
                    metrics.incrementCounter("entity.delete.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    private void validateInputs(String tenantId, String collectionName, 
                                 Map<String, Object> data, String userId) {
        validateInputs(tenantId, collectionName, userId);
        Objects.requireNonNull(data, "Data required");
    }

    private void validateInputs(String tenantId, String collectionName, String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name required");
        Objects.requireNonNull(userId, "User ID required");
    }

    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID required");
        }
    }
}
