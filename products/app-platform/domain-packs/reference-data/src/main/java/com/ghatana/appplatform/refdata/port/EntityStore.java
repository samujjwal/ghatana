package com.ghatana.appplatform.refdata.port;

import com.ghatana.appplatform.refdata.domain.EntityRelationship;
import com.ghatana.appplatform.refdata.domain.MarketEntity;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @doc.type       Port (Secondary)
 * @doc.purpose    Persistence port for the entity master and relationship graph.
 * @doc.layer      Application / Port
 * @doc.pattern    Repository Port (Hexagonal)
 */
public interface EntityStore {

    Promise<Void> saveEntity(MarketEntity entity);

    Promise<Optional<MarketEntity>> findEntityById(UUID id);

    Promise<List<MarketEntity>> listEntities(String statusFilter);

    Promise<Void> saveRelationship(EntityRelationship relationship);

    /** Return all active relationships where parentEntityId or childEntityId = entityId. */
    Promise<List<EntityRelationship>> findRelationships(UUID entityId);

    /** Recursive graph traversal: all descendants via subsidiary chain. */
    Promise<List<UUID>> findAllDescendantIds(UUID rootEntityId);
}
