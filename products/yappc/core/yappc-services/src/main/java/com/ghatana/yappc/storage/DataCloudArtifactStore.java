/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — DataCloud-backed Artifact Store
 */
package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
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

    private final DataCloudClient client;
    private final ObjectMapper mapper;

    /**
     * Constructs a {@code DataCloudArtifactStore}.
     *
     * @param client DataCloud SPI client for persistence
     * @param mapper           Jackson ObjectMapper for JSON serialisation
     */
    public DataCloudArtifactStore(DataCloudClient client, ObjectMapper mapper) {
        this.client  = client;
        this.mapper  = mapper;
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
        entityData.put("id", entityUuid.toString());

        return client.save(tenantId, ARTIFACTS_COLLECTION, entityData)
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

        return client.findById(tenantId, ARTIFACTS_COLLECTION, entityUuid.toString())
                .map(entityOpt -> {
                    if (entityOpt.isEmpty() || entityOpt.get().data() == null) {
                        throw new IllegalArgumentException("Artifact not found: " + path);
                    }
                    String encoded = (String) entityOpt.get().data().get("content");
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

        return client.query(
                tenantId, ARTIFACTS_COLLECTION,
                DataCloudClient.Query.builder()
                        .filter(DataCloudClient.Filter.eq("path", prefix))
                        .limit(Integer.MAX_VALUE)
                        .build())
                .map(entities -> {
                    List<String> versions = new ArrayList<>();
                    for (DataCloudClient.Entity e : entities) {
                        Object v = e.data() != null ? e.data().get("version") : null;
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
        entityData.put("id", entityUuid.toString());

        return client.save(tenantId, METADATA_COLLECTION, entityData)
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

        return client.findById(tenantId, METADATA_COLLECTION, entityUuid.toString())
                .map(entityOpt -> {
                    if (entityOpt.isEmpty() || entityOpt.get().data() == null) {
                        return Map.<String, String>of();
                    }
                    Map<String, String> result = new HashMap<>();
                    for (Map.Entry<String, Object> entry : entityOpt.get().data().entrySet()) {
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

    /**
     * Deletes the artifact (and associated metadata) stored at {@code path}.
     *
     * <p>The implementation issues two DataCloud deletes — one for the artifact entity
     * in {@code yappc-artifacts} and one for the metadata entity in
     * {@code yappc-artifact-metadata}. Both deletions are best-effort; a missing
     * entity is not treated as an error.
     *
     * @param path versioned artifact path
     * @return promise resolving when both deletes complete
     */
    @Override
    public Promise<Void> delete(String path) {
        String tenantId = resolveTenantId();
        log.info("DataCloudArtifactStore.delete: path={} tenant={}", path, tenantId);
        return client.delete(tenantId, "yappc-artifacts", path)
                .then(unused -> client.delete(tenantId, "yappc-artifact-metadata", path),
                        ex -> {
                            log.debug("Artifact entity not found during delete — treating as no-op: {}", path);
                            return client.delete(tenantId, "yappc-artifact-metadata", path);
                        })
                .then(unused -> Promise.complete(),
                        ex -> {
                            log.debug("Metadata entity not found during delete — treating as no-op: {}", path);
                            return Promise.complete();
                        });
    }
}
