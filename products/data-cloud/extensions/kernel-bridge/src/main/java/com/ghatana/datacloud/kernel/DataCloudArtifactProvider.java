package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Cloud-backed artifact manifest provider.
 *
 * @doc.type class
 * @doc.purpose Persist kernel artifact manifests in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudArtifactProvider extends DataCloudKernelProviderSupport {

    /**
     * Typed record for artifact manifest persistence.
     *
     * @doc.type record
     * @doc.purpose Encapsulate artifact reference persistence with lifecycle provenance
     * @doc.layer adapter
     * @doc.pattern Request
     */
    public record ArtifactManifestPersistRequest(
        String artifactId,
        String productUnitId,
        String surfaceId,
        String phase,
        String sourceRef,
        String digest,
        Map<String, Object> manifest,
        Instant producedAt,
        String correlationId
    ) {}

    /**
     * Typed record for artifact manifest persistence responses.
     *
     * @doc.type record
     * @doc.purpose Encapsulate artifact persistence success with provider timestamp
     * @doc.layer adapter
     * @doc.pattern Response
     */
    public record ArtifactManifestPersistResponse(
        boolean success,
        String artifactId,
        String persistedAt
    ) {}

    public DataCloudArtifactProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.artifacts." + context.getTenantId(), "artifacts");
    }

    public Promise<Void> persistArtifactManifest(String artifactId, Map<String, Object> manifest) {
        return persistRecord(artifactId, manifest);
    }

    public Promise<ArtifactManifestPersistResponse> persistArtifactManifestTyped(
        ArtifactManifestPersistRequest request
    ) {
        DataCloudProviderException validationError = validateArtifactReference(request);
        if (validationError != null) {
            return Promise.ofException(validationError);
        }

        String persistedAt = Instant.now().toString();
        Map<String, Object> artifactMap = new HashMap<>();
        artifactMap.put("artifactId", request.artifactId());
        artifactMap.put("productUnitId", request.productUnitId());
        artifactMap.put("surfaceId", request.surfaceId());
        artifactMap.put("phase", request.phase());
        artifactMap.put("sourceRef", request.sourceRef());
        artifactMap.put("digest", request.digest());
        artifactMap.put("manifest", request.manifest() != null ? request.manifest() : Map.of());
        artifactMap.put("producedAt", request.producedAt().toString());
        artifactMap.put("correlationId", request.correlationId());
        artifactMap.put("tenantId", context().getTenantId());
        artifactMap.put("workspaceId", context().getWorkspaceId());
        artifactMap.put("projectId", context().getProjectId());
        artifactMap.put("persistedAt", persistedAt);
        return persistRecord(request.artifactId(), artifactMap)
            .map($ -> new ArtifactManifestPersistResponse(true, request.artifactId(), persistedAt));
    }

    private DataCloudProviderException validateArtifactReference(ArtifactManifestPersistRequest request) {
        if (isBlank(request.artifactId())) {
            return invalidArtifactReference("artifactId is required");
        }
        if (isBlank(request.productUnitId())) {
            return invalidArtifactReference("productUnitId is required");
        }
        if (isBlank(request.surfaceId())) {
            return invalidArtifactReference("surfaceId is required");
        }
        if (isBlank(request.phase())) {
            return invalidArtifactReference("phase is required");
        }
        if (isBlank(request.sourceRef())) {
            return invalidArtifactReference("sourceRef is required");
        }
        if (isBlank(request.digest())) {
            return invalidArtifactReference("digest is required");
        }
        if (request.producedAt() == null) {
            return invalidArtifactReference("producedAt is required");
        }
        if (isBlank(request.correlationId())) {
            return invalidArtifactReference("correlationId is required");
        }
        return null;
    }

    private DataCloudProviderException invalidArtifactReference(String message) {
        return new DataCloudProviderException(
            "artifacts",
            "persist-artifact-manifest",
            message,
            DataCloudProviderException.ReasonCode.SCHEMA
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
