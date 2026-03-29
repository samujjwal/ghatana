/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application.facade;

import com.ghatana.datacloud.application.CollectionService;
import com.ghatana.datacloud.application.EntityService;
import com.ghatana.datacloud.application.EntitySuggestionService;
import com.ghatana.datacloud.application.EntityValidationService;
import com.ghatana.datacloud.application.SchemaDiffService;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.MetaCollection;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain facade aggregating entity-related application services.
 *
 * <p>Reduces handler constructor injection complexity by providing a single
 * dependency entry point for all entity domain operations: CRUD, validation,
 * suggestions, schema diffing, and collection management.
 *
 * <p>Handlers that previously needed to inject {@code EntityService},
 * {@code EntityValidationService}, {@code EntitySuggestionService},
 * {@code SchemaDiffService}, and {@code CollectionService} separately now
 * inject one {@code EntityDomainService} instead.
 *
 * @doc.type class
 * @doc.purpose Domain facade for entity-related application services
 * @doc.layer application
 * @doc.pattern Facade, Service
 */
public final class EntityDomainService {

    private static final Logger log = LoggerFactory.getLogger(EntityDomainService.class);

    private final EntityService entityService;
    private final EntityValidationService validationService;
    private final EntitySuggestionService suggestionService;
    private final SchemaDiffService schemaDiffService;
    private final CollectionService collectionService;

    public EntityDomainService(
            EntityService entityService,
            EntityValidationService validationService,
            EntitySuggestionService suggestionService,
            SchemaDiffService schemaDiffService,
            CollectionService collectionService) {
        this.entityService     = Objects.requireNonNull(entityService, "entityService");
        this.validationService = Objects.requireNonNull(validationService, "validationService");
        this.suggestionService = Objects.requireNonNull(suggestionService, "suggestionService");
        this.schemaDiffService = Objects.requireNonNull(schemaDiffService, "schemaDiffService");
        this.collectionService = Objects.requireNonNull(collectionService, "collectionService");
    }

    // ── Entity CRUD ───────────────────────────────────────────────────────────

    public Promise<Entity> createEntity(String tenantId, String collection,
                                        Map<String, Object> data, String userId) {
        return entityService.createEntity(tenantId, collection, data, userId);
    }

    public Promise<Entity> updateEntity(String tenantId, String collection,
                                        UUID entityId, Map<String, Object> data, String userId) {
        return entityService.updateEntity(tenantId, collection, entityId, data, userId);
    }

    public Promise<Entity> getEntity(String tenantId, String collection, UUID entityId) {
        return entityService.getEntity(tenantId, collection, entityId);
    }

    public Promise<Void> deleteEntity(String tenantId, String collection,
                                      UUID entityId, String userId) {
        return entityService.deleteEntity(tenantId, collection, entityId, userId);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    public EntityValidationService validation() {
        return validationService;
    }

    // ── Suggestions ───────────────────────────────────────────────────────────

    public EntitySuggestionService suggestions() {
        return suggestionService;
    }

    // ── Schema diff ───────────────────────────────────────────────────────────

    public SchemaDiffService schemaDiff() {
        return schemaDiffService;
    }

    // ── Collection management ─────────────────────────────────────────────────

    public Promise<MetaCollection> createCollection(String tenantId,
                                                     MetaCollection collection, String userId) {
        return collectionService.createCollection(tenantId, collection, userId);
    }

    public Promise<List<MetaCollection>> listCollections(String tenantId) {
        return collectionService.listCollections(tenantId);
    }

    public CollectionService collections() {
        return collectionService;
    }
}
