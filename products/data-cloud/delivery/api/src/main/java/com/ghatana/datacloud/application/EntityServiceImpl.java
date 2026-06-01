/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.entity.EntityLineage;
import com.ghatana.datacloud.entity.EntityLineageRepository;
import com.ghatana.datacloud.entity.policy.PolicyDecision;
import com.ghatana.datacloud.entity.policy.PolicyEngine;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.datacloud.spi.TransactionManager;
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
    private final EntitySchemaValidator schemaValidator;
    private final PolicyEngine policyEngine;
    private final TransactionManager transactionManager;
    private final EntityLineageRepository lineageRepository;
    private final EventLogStore eventLogStore;

    /**
     * Creates an entity service with full lifecycle support.
     *
     * WS5: Entity write lifecycle: validate schema → enforce policy → idempotency → transaction → save → append event → lineage/audit
     */
    public EntityServiceImpl(
            EntityRepository repository,
            MetricsCollector metrics,
            EntitySchemaValidator schemaValidator,
            PolicyEngine policyEngine,
            TransactionManager transactionManager,
            EntityLineageRepository lineageRepository,
            EventLogStore eventLogStore) {
        this.repository = Objects.requireNonNull(repository, "Repository required");
        this.metrics = Objects.requireNonNull(metrics, "Metrics required");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "Schema validator required");
        this.policyEngine = Objects.requireNonNull(policyEngine, "Policy engine required");
        this.transactionManager = Objects.requireNonNull(transactionManager, "Transaction manager required");
        this.lineageRepository = Objects.requireNonNull(lineageRepository, "Lineage repository required");
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "Event log store required");
    }

    @Override
    public Promise<Entity> createEntity(String tenantId, String collectionName,
                                        Map<String, Object> data, String userId) {
        validateInputs(tenantId, collectionName, data, userId);

        // WS5: Full entity write lifecycle
        return executeWriteLifecycle(tenantId, collectionName, data, userId, "create", null);
    }

    @Override
    public Promise<Entity> updateEntity(String tenantId, String collectionName,
                                        UUID entityId, Map<String, Object> data, String userId) {
        validateInputs(tenantId, collectionName, userId);
        Objects.requireNonNull(entityId, "Entity ID required");

        // WS5: Full entity write lifecycle
        return executeWriteLifecycle(tenantId, collectionName, data, userId, "update", entityId);
    }

    @Override
    public Promise<Entity> getEntity(String tenantId, String collectionName, UUID entityId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(collectionName, "Collection name required");
        Objects.requireNonNull(entityId, "Entity ID required");

        return repository.findById(tenantId, collectionName, entityId)
            .map(optional -> optional.orElse(null))
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

    /**
     * WS5: Executes the full entity write lifecycle.
     *
     * Lifecycle: validate schema → enforce policy → idempotency → transaction → save → append event → lineage/audit
     */
    private Promise<Entity> executeWriteLifecycle(String tenantId, String collectionName,
                                                   Map<String, Object> data, String userId,
                                                   String operation, UUID existingEntityId) {
        // Step 1: Validate schema
        ValidationResult schemaResult = schemaValidator.validate(tenantId, collectionName, data);
        if (!schemaResult.valid()) {
            metrics.incrementCounter("entity.write.schema_error",
                "tenant", tenantId, "collection", collectionName, "operation", operation);
            return Promise.ofException(new IllegalArgumentException(
                "Schema validation failed: " + String.join(", ", schemaResult.violations())));
        }

        // Step 2: Enforce policy
        Map<String, Object> policyInput = Map.of(
            "tenantId", tenantId,
            "collection", collectionName,
            "operation", operation,
            "userId", userId,
            "data", data
        );
        return policyEngine.evaluate("entity_write", policyInput)
            .then(decision -> {
                if (!decision.isAllowed()) {
                    metrics.incrementCounter("entity.write.policy_denied",
                        "tenant", tenantId, "collection", collectionName, "operation", operation);
                    return Promise.ofException(new SecurityException(
                        "Policy denied: " + decision.reason()));
                }

                // Step 3-7: Execute within transaction
                return transactionManager.executeInTransaction(tenantId, () ->
                    executeTransactionalWrite(tenantId, collectionName, data, userId, operation, existingEntityId)
                );
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("entity.write.success",
                        "tenant", tenantId, "collection", collectionName, "operation", operation);
                } else {
                    metrics.incrementCounter("entity.write.error",
                        "tenant", tenantId, "collection", collectionName, "operation", operation,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * WS5: Executes the transactional portion of the write lifecycle.
     *
     * Steps: idempotency → save → append event → lineage/audit
     */
    private Promise<Entity> executeTransactionalWrite(String tenantId, String collectionName,
                                                       Map<String, Object> data, String userId,
                                                       String operation, UUID existingEntityId) {
        String idempotencyKey = generateIdempotencyKey(tenantId, collectionName, data, operation);

        // For update, fetch existing entity first
        if ("update".equals(operation) && existingEntityId != null) {
            return repository.findById(tenantId, collectionName, existingEntityId)
                .then(existingOpt -> {
                    if (existingOpt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Entity not found: " + existingEntityId));
                    }
                    Entity existing = existingOpt.get();
                    Entity updated = buildUpdatedEntity(existing, data, userId);
                    
                    // Step 3: Idempotency check + save
                    return repository.saveWithIdempotency(tenantId, updated, idempotencyKey)
                        .then(savedEntity -> completeWriteLifecycle(tenantId, collectionName, savedEntity, operation));
                });
        }

        // For create, build new entity directly
        Entity newEntity = buildEntity(tenantId, collectionName, data, userId, operation, existingEntityId);

        // Step 3: Idempotency check + save
        return repository.saveWithIdempotency(tenantId, newEntity, idempotencyKey)
            .then(savedEntity -> completeWriteLifecycle(tenantId, collectionName, savedEntity, operation));
    }

    /**
     * WS5: Completes the write lifecycle after save: append event → lineage/audit
     */
    private Promise<Entity> completeWriteLifecycle(String tenantId, String collectionName,
                                                   Entity savedEntity, String operation) {
        // Step 5: Append CDC event
        return appendCdcEvent(tenantId, collectionName, savedEntity, operation)
            .then(eventOffset -> {
                // Step 6: Record lineage
                return recordLineage(tenantId, collectionName, savedEntity, operation)
                    .then(lineage -> {
                        // Step 7: Audit logging (handled by metrics and logging)
                        log.info("Entity write completed: tenant={}, collection={}, id={}, operation={}",
                            tenantId, collectionName, savedEntity.getId(), operation);
                        return Promise.of(savedEntity);
                    });
            });
    }

    /**
     * WS5: Builds an entity for create or update operations.
     */
    private Entity buildEntity(String tenantId, String collectionName, Map<String, Object> data,
                              String userId, String operation, UUID existingEntityId) {
        if ("create".equals(operation)) {
            return Entity.builder()
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
        } else {
            // For update, we need to fetch the existing entity first
            // This is handled in the transactional write path
            throw new IllegalStateException("Update requires existing entity fetch in transaction");
        }
    }

    /**
     * WS5: Builds an updated entity from an existing entity.
     */
    private Entity buildUpdatedEntity(Entity existing, Map<String, Object> data, String userId) {
        return Entity.builder()
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
    }

    /**
     * WS5: Generates an idempotency key for the operation.
     */
    private String generateIdempotencyKey(String tenantId, String collectionName,
                                          Map<String, Object> data, String operation) {
        // Simple hash-based key for idempotency
        return operation + ":" + collectionName + ":" + data.hashCode();
    }

    /**
     * WS5: Appends a CDC event for the entity write.
     */
    private Promise<com.ghatana.platform.types.identity.Offset> appendCdcEvent(String tenantId, String collectionName,
                                                                                   Entity entity, String operation) {
        com.ghatana.platform.domain.eventstore.TenantContext tenant = com.ghatana.platform.domain.eventstore.TenantContext.of(tenantId);
        
        Map<String, String> headers = Map.of(
            "operation", operation,
            "collection", collectionName,
            "entityId", entity.getId().toString(),
            "userId", entity.getUpdatedBy()
        );

        com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry eventEntry =
            com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType("entity." + operation)
                .eventVersion("1.0.0")
                .timestamp(Instant.now())
                .payload(java.nio.ByteBuffer.wrap(entity.getData().toString().getBytes()))
                .contentType("application/json")
                .headers(headers)
                .build();

        return eventLogStore.append(tenant, eventEntry);
    }

    /**
     * WS5: Records lineage for the entity write.
     */
    private Promise<Void> recordLineage(String tenantId, String collectionName,
                                         Entity entity, String operation) {
        EntityLineage lineage = EntityLineage.builder()
            .entityId(entity.getId())
            .tenantId(tenantId)
            .collectionName(collectionName)
            .sourceType("api")
            .sourceId(operation)
            .createdBy(entity.getUpdatedBy())
            .build();

        return lineageRepository.save(lineage).map(saved -> null);
    }
}
