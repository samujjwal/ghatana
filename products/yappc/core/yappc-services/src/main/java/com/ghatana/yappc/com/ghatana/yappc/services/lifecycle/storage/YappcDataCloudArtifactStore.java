package com.ghatana.yappc.services.lifecycle.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.entity.ArtifactContentEntity;
import com.ghatana.yappc.infrastructure.datacloud.entity.ArtifactMetadataEntity;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.yappc.storage.ArtifactStore;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Repository-backed YAPPC artifact store using the canonical Data Cloud adapter seam
 * @doc.layer product
 * @doc.pattern Repository/Adapter
 */
public final class YappcDataCloudArtifactStore implements ArtifactStore {

    private static final Logger LOG = LoggerFactory.getLogger(YappcDataCloudArtifactStore.class);
    private static final String ARTIFACTS_COLLECTION = "yappc-artifacts";
    private static final String METADATA_COLLECTION = "yappc-artifact-metadata";

    private final YappcDataCloudRepository<ArtifactContentEntity> artifactRepository;
    private final YappcDataCloudRepository<ArtifactMetadataEntity> metadataRepository;

    public YappcDataCloudArtifactStore(@NotNull DataCloudClient client, @NotNull ObjectMapper mapper) {
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

    @Override
    public Promise<String> put(String path, byte[] content) {
        String tenantId = requireTenantId();
        String normalizedPath = normalizeArtifactPath(path);
        String version = UUID.randomUUID().toString();
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
                LOG.info("Stored artifact via repository seam: path={} version={} tenant={}",
                    normalizedPath, saved.version(), tenantId);
                return saved.version();
            });
    }

    @Override
    public Promise<byte[]> get(String path) {
        requireTenantId();
        VersionedArtifactPath versionedPath = parseVersionedArtifactPath(path);
        UUID artifactId = buildArtifactId(versionedPath.basePath(), versionedPath.version());

        return artifactRepository.findById(artifactId)
            .map(entityOpt -> entityOpt
                .map(entity -> Base64.getDecoder().decode(entity.content()))
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + path)));
    }

    @Override
    public Promise<List<String>> list(String prefix) {
        requireTenantId();
        String normalizedPath = normalizeArtifactPath(prefix);
        return artifactRepository.findByField("path", normalizedPath)
            .map(entities -> entities.stream()
                .map(ArtifactContentEntity::version)
                .toList());
    }

    @Override
    public Promise<Void> putMetadata(String path, Map<String, String> meta) {
        String tenantId = requireTenantId();
        String normalizedPath = normalizeMetadataPath(path);
        ArtifactMetadataEntity entity = new ArtifactMetadataEntity(
            buildMetadataId(normalizedPath),
            normalizedPath,
            Map.copyOf(meta),
            tenantId,
            System.currentTimeMillis()
        );

        return metadataRepository.save(entity).map(saved -> null);
    }

    @Override
    public Promise<Map<String, String>> getMetadata(String path) {
        requireTenantId();
        String normalizedPath = normalizeMetadataPath(path);
        return metadataRepository.findById(buildMetadataId(normalizedPath))
            .map(entityOpt -> entityOpt.map(ArtifactMetadataEntity::metadata).orElseGet(Map::of));
    }

    @Override
    public Promise<Void> delete(String path) {
        requireTenantId();
        if (path.endsWith("/metadata")) {
            return deleteMetadataEntity(normalizeMetadataPath(path));
        }

        VersionedArtifactPath versionedPath = parseVersionedArtifactPath(path);
        UUID artifactId = buildArtifactId(versionedPath.basePath(), versionedPath.version());
        String metadataPath = buildMetadataPath(versionedPath.basePath(), versionedPath.version());

        return artifactRepository.deleteById(artifactId)
            .then(unused -> deleteMetadataEntity(metadataPath), error -> {
                LOG.debug("Artifact entity not found during delete, continuing with metadata cleanup: {}", path);
                return deleteMetadataEntity(metadataPath);
            });
    }

    private Promise<Void> deleteMetadataEntity(String metadataPath) {
        return metadataRepository.deleteById(buildMetadataId(metadataPath))
            .then(unused -> Promise.complete(), error -> {
                LOG.debug("Artifact metadata not found during delete, treating as no-op: {}", metadataPath);
                return Promise.complete();
            });
    }

    private String requireTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException(
                "YappcDataCloudArtifactStore requires an active tenant context. "
                    + "Ensure ApiKeyAuthFilter or TenantExtractionFilter is applied.");
        }
        if ("default-tenant".equals(tenantId)) {
            throw new SecurityException(
                "YappcDataCloudArtifactStore does not allow default-tenant. "
                    + "A valid tenant ID must be configured in YAPPC_API_KEY_TENANT_MAP.");
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

    private record VersionedArtifactPath(String basePath, String version) {
    }
}