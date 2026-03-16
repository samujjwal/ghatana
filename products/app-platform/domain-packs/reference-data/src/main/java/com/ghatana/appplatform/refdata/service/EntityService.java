package com.ghatana.appplatform.refdata.service;

import com.ghatana.appplatform.refdata.domain.EntityRelationship;
import com.ghatana.appplatform.refdata.domain.MarketEntity;
import com.ghatana.appplatform.refdata.domain.RelationshipType;
import com.ghatana.appplatform.refdata.port.EntityStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type       Application Service
 * @doc.purpose    Entity master CRUD (D11-004) and relationship graph management
 *                 (D11-005).  Entities represent legal and market participants:
 *                 issuers, brokers, custodians, exchanges, regulators, and banks.
 *                 Relationships express structural links (subsidiary, custodian-for,
 *                 issuer-of) with temporal validity.
 * @doc.layer      Application Service
 * @doc.pattern    Hexagonal Application Service
 */
public class EntityService {

    private static final Logger log = LoggerFactory.getLogger(EntityService.class);

    private final EntityStore store;
    private final Executor executor;

    public EntityService(EntityStore store, Executor executor) {
        this.store = store;
        this.executor = executor;
    }

    /** Persist a new or updated entity version. */
    public Promise<Void> saveEntity(MarketEntity entity) {
        return Promise.ofBlocking(executor, () -> {
            store.saveEntity(entity).get();
            log.info("entity.saved id={} type={} name={}", entity.id(), entity.entityType(), entity.name());
        });
    }

    public Promise<Optional<MarketEntity>> findEntityById(UUID id) {
        return Promise.ofBlocking(executor, () -> store.findEntityById(id).get());
    }

    public Promise<List<MarketEntity>> listEntities(String statusFilter) {
        return Promise.ofBlocking(executor, () -> store.listEntities(statusFilter).get());
    }

    /**
     * Register a directed relationship between two entities.
     * Validates that both parent and child entities exist before saving.
     */
    public Promise<Void> addRelationship(UUID parentEntityId, UUID childEntityId,
                                         RelationshipType type,
                                         LocalDate effectiveFrom) {
        return Promise.ofBlocking(executor, () -> {
            store.findEntityById(parentEntityId).get()
                    .orElseThrow(() -> new EntityNotFoundException(parentEntityId));
            store.findEntityById(childEntityId).get()
                    .orElseThrow(() -> new EntityNotFoundException(childEntityId));

            EntityRelationship rel = new EntityRelationship(
                    UUID.randomUUID(), parentEntityId, childEntityId, type,
                    effectiveFrom, null);
            store.saveRelationship(rel).get();
            log.info("entity.relationship.added parent={} child={} type={}", parentEntityId, childEntityId, type);
        });
    }

    /** Return all active relationships for the given entity (either side). */
    public Promise<List<EntityRelationship>> findRelationships(UUID entityId) {
        return Promise.ofBlocking(executor, () -> store.findRelationships(entityId).get());
    }

    /** Recursive graph traversal: all descendants via subsidiary chain. */
    public Promise<List<UUID>> findAllDescendants(UUID rootEntityId) {
        return Promise.ofBlocking(executor, () -> store.findAllDescendantIds(rootEntityId).get());
    }

    public static final class EntityNotFoundException extends RuntimeException {
        public EntityNotFoundException(UUID id) {
            super("Entity not found: " + id);
        }
    }
}
