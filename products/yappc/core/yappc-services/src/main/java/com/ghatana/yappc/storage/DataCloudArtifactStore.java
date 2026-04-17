/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — DataCloud-backed Artifact Store
 */
package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.entity.ArtifactContentEntity;
import com.ghatana.yappc.infrastructure.datacloud.entity.ArtifactMetadataEntity;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    private final YappcDataCloudRepository<ArtifactContentEntity> artifactRepository;
    private final YappcDataCloudRepository<ArtifactMetadataEntity> metadataRepository;

    /**
     * Constructs a {@code DataCloudArtifactStore}.
     *
     * @param client DataCloud SPI client for persistence
     * @param mapper           Jackson ObjectMapper for JSON serialisation
     */
    public DataCloudArtifactStore(DataCloudClient client, ObjectMapper mapper) {
        YappcEntityMapper entityMapper = new YappcEntityMapper(mapper);
        this.artifactRepository = new YappcDataCloudRepository<>(
            client,
            entityMapper,
            ARTIFACTS_COLLECTION,
            ArtifactContentEntity.class
        );
        this.metadataRepository = new YappcDataCloudRepository<>(
            client,
            entityMapper,
            METADATA_COLLECTION,
            ArtifactMetadataEntity.class
        );
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
        String normalizedPath = normalizeArtifactPath(path);
        String version  = UUID.randomUUID().toString();
        ArtifactContentEntity entity = new ArtifactContentEntity(
            buildArtifactId(normalizedPath, version),
            normalizedPath,
            version,
            Base64.getEncoder().encodeToString(content),
            content.length,
            tenantId,
            System.currentTimeMillis()
        );

        return artifactRepository.save(entity)
            .map(saved -> {
                log.info("Stored artifact via repository seam: path={} version={} size={} tenant={}",
                    normalizedPath, saved.version(), content.length, tenantId);
                return saved.version();
            });
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code path} must include the version segment (e.g., {@code products/p1/phases/intent/v-123}).
     */
    @Override
    public Promise<byte[]> get(String path) {
        resolveTenantId();
        VersionedArtifactPath versionedPath = parseVersionedArtifactPath(path);
        return artifactRepository.findById(buildArtifactId(versionedPath.basePath(), versionedPath.version()))
            .map(entityOpt -> entityOpt
                .map(entity -> Base64.getDecoder().decode(entity.content()))
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + path)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Queries all artifacts whose {@code path} field starts with {@code prefix} and
     * returns their version identifiers.
     */
    @Override
    public Promise<List<String>> list(String prefix) {
        resolveTenantId();
        String normalizedPrefix = normalizeArtifactPath(prefix);
        return artifactRepository.findByField("path", normalizedPrefix)
            .map(entities -> entities.stream()
                .map(ArtifactContentEntity::version)
                .toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<Void> putMetadata(String path, Map<String, String> meta) {
        String tenantId = resolveTenantId();
        String normalizedPath = normalizeMetadataPath(path);
        ArtifactMetadataEntity entity = new ArtifactMetadataEntity(
            buildMetadataId(normalizedPath),
            normalizedPath,
            Map.copyOf(meta),
            tenantId,
            System.currentTimeMillis()
        );

        return metadataRepository.save(entity)
            .map(saved -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Promise<Map<String, String>> getMetadata(String path) {
        resolveTenantId();
        String normalizedPath = normalizeMetadataPath(path);
        return metadataRepository.findById(buildMetadataId(normalizedPath))
            .map(entityOpt -> entityOpt
                .map(ArtifactMetadataEntity::metadata)
                .orElseGet(Map::of));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Resolves the current tenant ID from {@link TenantContext}.
     * Throws {@link SecurityException} if no tenant context is active or if tenantId is default-tenant.
     */
    private String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException(
                    "DataCloudArtifactStore requires an active tenant context. " +
                            "Ensure ApiKeyAuthFilter or TenantExtractionFilter is applied.");
        }
        if ("default-tenant".equals(tenantId)) {
            throw new SecurityException(
                    "DataCloudArtifactStore does not allow default-tenant. " +
                            "A valid tenant ID must be configured in YAPPC_API_KEY_TENANT_MAP.");
        }
        return tenantId;
    }

    private String normalizeArtifactPath(String path) {
        String normalized = path.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeMetadataPath(String path) {
        String normalized = path.strip();
        if (!normalized.endsWith("/metadata")) {
            throw new IllegalArgumentException("Metadata path must end with /metadata: " + path);
        }
        return normalized;
    }

    private VersionedArtifactPath parseVersionedArtifactPath(String path) {
        String normalizedPath = normalizeArtifactPath(path);
        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash < 0) {
            throw new IllegalArgumentException("Path must include version segment: " + path);
        }
        return new VersionedArtifactPath(
            normalizedPath.substring(0, lastSlash),
            normalizedPath.substring(lastSlash + 1)
        );
    }

    private UUID buildArtifactId(String path, String version) {
        return UUID.nameUUIDFromBytes((path + "::" + version).getBytes(StandardCharsets.UTF_8));
    }

    private UUID buildMetadataId(String metadataPath) {
        return UUID.nameUUIDFromBytes(("metadata::" + metadataPath).getBytes(StandardCharsets.UTF_8));
    }

    private String buildMetadataPath(String path, String version) {
        return path + "/" + version + "/metadata";
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
        resolveTenantId();
        if (path.endsWith("/metadata")) {
            return deleteMetadataEntity(normalizeMetadataPath(path));
        }

        VersionedArtifactPath versionedPath = parseVersionedArtifactPath(path);
        UUID artifactId = buildArtifactId(versionedPath.basePath(), versionedPath.version());
        String metadataPath = buildMetadataPath(versionedPath.basePath(), versionedPath.version());

        return artifactRepository.deleteById(artifactId)
            .then(unused -> deleteMetadataEntity(metadataPath), ex -> {
                log.debug("Artifact entity not found during delete, continuing with metadata cleanup: {}", path);
                return deleteMetadataEntity(metadataPath);
            });
    }

    private Promise<Void> deleteMetadataEntity(String metadataPath) {
        return metadataRepository.deleteById(buildMetadataId(metadataPath))
            .then(unused -> Promise.complete(), ex -> {
                log.debug("Artifact metadata not found during delete, treating as no-op: {}", metadataPath);
                return Promise.complete();
            });
    }

    private record VersionedArtifactPath(String basePath, String version) {
    }
}
