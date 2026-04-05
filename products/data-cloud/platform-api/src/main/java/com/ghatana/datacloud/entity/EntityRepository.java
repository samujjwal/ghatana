/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Entity persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Data access layer for entities
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public interface EntityRepository {
    
    Promise<Entity> save(Entity entity);
    
    Promise<Entity> findById(String tenantId, String collectionName, UUID entityId);
    
    Promise<List<Entity>> findByCollection(String tenantId, String collectionName);
    
    Promise<Void> delete(String tenantId, String collectionName, UUID entityId);
    
    Promise<Long> count(String tenantId, String collectionName);
}
