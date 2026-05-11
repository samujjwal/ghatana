package com.ghatana.yappc.services.lifecycle.storage;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.facades.datacloud.DataCloudArtifactFacade;
import com.ghatana.yappc.storage.ArtifactStore;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * YAPPC artifact store using Data Cloud facade.
 *
 * Migration Status: Migrated to use DataCloudArtifactFacade (task 5.F.2)
 * Previous implementation used direct DataCloudClient and YappcDataCloudRepository.
 * Route ownership: scaffold-api
 *
 * @doc.type class
 * @doc.purpose YAPPC artifact store using Data Cloud facade (task 5.F.2)
 * @doc.layer product
 * @doc.pattern Facade Adapter
 */
public final class YappcDataCloudArtifactStore implements ArtifactStore {

    private static final Logger LOG = LoggerFactory.getLogger(YappcDataCloudArtifactStore.class);

    private final DataCloudArtifactFacade artifactFacade;

    public YappcDataCloudArtifactStore(@NotNull DataCloudArtifactFacade artifactFacade) {
        this.artifactFacade = artifactFacade;
    }

    @Override
    public Promise<String> put(String path, byte[] content) {
        String tenantId = requireTenantId();
        String normalizedPath = normalizeArtifactPath(path);
        String version = UUID.randomUUID().toString();
        String projectId = extractProjectId(path);

        return artifactFacade.storeArtifact(
            new DataCloudArtifactFacade.ArtifactStorageRequest(
                projectId,
                tenantId,
                "yappc-artifact",
                Base64.getEncoder().encodeToString(content),
                Map.of("path", normalizedPath),
                version
            )
        ).map(artifactId -> {
            LOG.info("Stored artifact via facade: path={} version={} tenant={}",
                normalizedPath, version, tenantId);
            return version;
        });
    }

    @Override
    public Promise<byte[]> get(String path) {
        String tenantId = requireTenantId();
        VersionedArtifactPath versionedPath = parseVersionedArtifactPath(path);
        String artifactId = buildArtifactId(versionedPath.basePath(), versionedPath.version());

        return artifactFacade.retrieveArtifact(artifactId, tenantId)
            .map(contentOpt -> contentOpt
                .map(content -> Base64.getDecoder().decode(content.content()))
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + path)));
    }

    @Override
    public Promise<List<String>> list(String prefix) {
        String tenantId = requireTenantId();
        String projectId = extractProjectId(prefix);
        
        return artifactFacade.listArtifacts(projectId, tenantId)
            .map(metadataList -> metadataList.stream()
                .map(DataCloudArtifactFacade.ArtifactMetadata::version)
                .toList());
    }

    @Override
    public Promise<Void> putMetadata(String path, Map<String, String> meta) {
        String tenantId = requireTenantId();
        String normalizedPath = normalizeMetadataPath(path);
        String projectId = extractProjectId(path);
        String version = extractVersionFromMetadataPath(normalizedPath);

        return artifactFacade.storeArtifact(
            new DataCloudArtifactFacade.ArtifactStorageRequest(
                projectId,
                tenantId,
                "yappc-metadata",
                normalizedPath,
                meta,
                version
            )
        ).map(artifactId -> null);
    }

    @Override
    public Promise<Map<String, String>> getMetadata(String path) {
        String tenantId = requireTenantId();
        String normalizedPath = normalizeMetadataPath(path);
        String artifactId = buildMetadataId(normalizedPath);

        return artifactFacade.retrieveArtifact(artifactId, tenantId)
            .map(contentOpt -> contentOpt
                .map(DataCloudArtifactFacade.ArtifactContent::metadata)
                .orElseGet(Map::of));
    }

    @Override
    public Promise<Void> delete(String path) {
        String tenantId = requireTenantId();
        VersionedArtifactPath versionedPath = parseVersionedArtifactPath(path);
        String artifactId = buildArtifactId(versionedPath.basePath(), versionedPath.version());

        return artifactFacade.deleteArtifact(artifactId, tenantId);
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

    private String buildArtifactId(String path, String version) {
        return UUID.nameUUIDFromBytes((path + "::" + version).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String buildMetadataId(String metadataPath) {
        return UUID.nameUUIDFromBytes(("metadata::" + metadataPath).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String buildMetadataPath(String path, String version) {
        return path + "/" + version + "/metadata";
    }

    private String extractProjectId(String path) {
        String normalized = normalizeArtifactPath(path);
        int firstSlash = normalized.indexOf('/');
        return firstSlash > 0 ? normalized.substring(0, firstSlash) : "default-project";
    }

    private String extractVersionFromMetadataPath(String metadataPath) {
        String normalized = metadataPath.strip();
        if (!normalized.endsWith("/metadata")) {
            return "latest";
        }
        String withoutMetadata = normalized.substring(0, normalized.length() - "/metadata".length());
        int lastSlash = withoutMetadata.lastIndexOf('/');
        return lastSlash > 0 ? withoutMetadata.substring(lastSlash + 1) : "latest";
    }

    private record VersionedArtifactPath(String basePath, String version) {
    }
}