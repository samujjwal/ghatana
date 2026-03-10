/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — DataCloud-backed Artifact Store
 */
package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DataCloud-backed implementation of {@link ArtifactStore}.
 *
 * <p>Stores each artifact as a DataCloud entity in the {@code yappc-artifacts} collection.
 * Artifact content is Base64-encoded and stored in the {@code content} field of the entity.
 * Metadata is stored in a separate entity with the {@code yappc-artifact-metadata} collection.
 *
 * <p>All operations resolve the current tenant ID from {@link TenantContext}, ensuring
 * strong multi-tenant isolation: no artifact crosses tenant boundaries.
 *
 * <p>Versioning: each {@code put()} generates a unique version UUID and stores it as
 * the entity's {@code version} field, enabling immutable artifact snapshots.
 *
 * @doc.type class
 * @doc.purpose Durable DataCloud-backed artifact storage replacing InMemoryArtifactStore
 * @doc.layer infrastructure
 * @doc.pattern Repository/Adapter
 */
public class DataCloudArtifactStore implements ArtifactStore {

    private static final Logger log = LoggerFactory.getLogger(DataCloudArtifactStore.class);

    private static final String ARTIFACTS_COLLECTION = "yappc-artifacts";
    private static final String METADATA_COLLECTION  = "yappc-artifact-metadata";

    private final EntityRepository entityRepository;
    private final ObjectMapper mapper;

    /**
     * Constructs a {@code DataCloudArtifactStore}.
     *
     * @param entityRepository DataCloud entity repository for persistence
     * @param mapper           Jackson ObjectMapper for JSON serialisation
     */
    public DataCloudArtifactStore(EntityRepository entityRepository, ObjectMapper mapper) {
        this.entityRepository = entityRepository;
        this.mapper           = mapper;
    }

    // =========================================================================
    // ArtifactStore contract
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Generates a unique version UUID and stores the Base64-encoded content as a
     * DataCloud entity. Returns the version UUID so callers can form the versioned path.
     */
    @Override
    public Promise<String> put(String path, byte[] content) {
        String tenantId = resolveTenantId();
        String version  = UUID.randomUUID().toString();

        Map<String, Object> entityData = new HashMap<>();
        entityData.put("path",      path);
        entityData.put("version",   version);
        entityData.put("content",   Base64.getEncoder().encodeToString(content));
        entityData.put("size",      content.length);
        entityData.put("tenant_id", tenantId);
        entityData.put("created_at", System.currentTimeMillis());

        UUID entityUuid = UUID.nameUUIDFromBytes((path.replace("/", "_") + "_" + version).getBytes(StandardCharsets.UTF_8));
        Entity entity   = Entity.builder()
                .id(entityUuid)
                .tenantId(tenantId)
                .collectionName(ARTIFACTS_COLLECTION)
                .data(entityData)
                .build();

        return entityRepository.save(tenantId, entity)
                .map(saved -> {
                    log.info("Stored artifact: path={} version={} size={} tenant={}",
                            path, version, content.length, tenantId);
                    return version;
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code path} must include the version segment (e.g., {@code products/p1/phases/intent/v-123}).
     */
    @Override
    public Promise<byte[]> get(String path) {
        String tenantId = resolveTenantId();

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return Promise.ofException(new IllegalArgumentException("Path must include version segment: " + path));
        }
        String basePath = path.substring(0, lastSlash);
        String version  = path.substring(lastSlash + 1);
        UUID entityUuid = UUID.nameUUIDFromBytes((basePath.replace("/", "_") + "_" + version).getBytes(StandardCharsets.UTF_8));

        return entityRepository.findById(tenantId, ARTIFACTS_COLLECTION, entityUuid)
                .map(entityOpt -> {
                    if (entityOpt.isEmpty() || entityOpt.get().getData() == null) {
                        throw new IllegalArgumentException("Artifact not found: " + path);
                    }
                    String encoded = (String) entityOpt.get().getData().get("content");
                    if (encoded == null) {
                        throw new IllegalStateException("Artifact entity missing content field: " + path);
                    }
                    return Base64.getDecoder().decode(encoded);
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Queries all artifacts whose {@code path} field starts with {@code prefix} and
     * returns their version identifiers.
     */
    @Override
    public Promise<List<String>> list(String prefix) {
        String tenantId = resolveTenantId();

        return entityRepository.findAll(
                tenantId, ARTIFACTS_COLLECTION, Map.of("path", prefix), null, 0, Integer.MAX_VALUE)
                .map(entities -> {
                    List<String> versions = new ArrayList<>();
                    for (Entity e : entities) {
                        Object v = e.getData() != null ? e.getData().get("version") : null;
                        if (v != null) {
                            versions.add(v.toString());
                        }
                    }
                    return versions;
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<Void> putMetadata(String path, Map<String, String> meta) {
        String tenantId = resolveTenantId();

        Map<String, Object> entityData = new HashMap<>(meta);
        entityData.put("path",      path);
        entityData.put("tenant_id", tenantId);

        UUID entityUuid = UUID.nameUUIDFromBytes(("meta_" + path.replace("/", "_")).getBytes(StandardCharsets.UTF_8));
        Entity entity   = Entity.builder()
                .id(entityUuid)
                .tenantId(tenantId)
                .collectionName(METADATA_COLLECTION)
                .data(entityData)
                .build();

        return entityRepository.save(tenantId, entity)
                .map(saved -> {
                    log.debug("Stored metadata for path={} tenant={}", path, tenantId);
                    return (Void) null;
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Promise<Map<String, String>> getMetadata(String path) {
        String tenantId = resolveTenantId();

        UUID entityUuid = UUID.nameUUIDFromBytes(("meta_" + path.replace("/", "_")).getBytes(StandardCharsets.UTF_8));

        return entityRepository.findById(tenantId, METADATA_COLLECTION, entityUuid)
                .map(entityOpt -> {
                    if (entityOpt.isEmpty() || entityOpt.get().getData() == null) {
                        return Map.<String, String>of();
                    }
                    Map<String, String> result = new HashMap<>();
                    for (Map.Entry<String, Object> entry : entityOpt.get().getData().entrySet()) {
                        if (!"path".equals(entry.getKey()) && !"tenant_id".equals(entry.getKey())) {
                            result.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                    }
                    return result;
                });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Resolves the current tenant ID from {@link TenantContext}.
     * Throws {@link SecurityException} if no tenant context is active.
     */
    private String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank() || "default-tenant".equals(tenantId)) {
            // Allow default-tenant in dev mode; strict services should reject this
            if ("default-tenant".equals(tenantId)) {
                log.debug("DataCloudArtifactStore operating under default-tenant (dev mode)");
            } else {
                throw new SecurityException("DataCloudArtifactStore requires a tenant context");
            }
        }
        return tenantId;
    }
}
