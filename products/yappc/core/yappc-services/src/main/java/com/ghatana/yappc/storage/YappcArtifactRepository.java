package com.ghatana.yappc.storage;

import com.ghatana.yappc.domain.PhaseType;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Manages versioned artifact storage in data-cloud
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class YappcArtifactRepository {

    private static final Logger log = LoggerFactory.getLogger(YappcArtifactRepository.class);

    private final ArtifactStore store;

    /**
     * Constructor with artifact store.
     *
     * @param store Artifact store implementation (DataCloudArtifactStore for production)
     */
    public YappcArtifactRepository(ArtifactStore store) {
        this.store = Objects.requireNonNull(store, "ArtifactStore must not be null");
    }

    /**
     * Stores an artifact for a specific product and phase.
     *
     * @param productId Product identifier
     * @param phase Phase type
     * @param content Artifact content
     * @return Promise of version identifier
     */
    public Promise<String> storeArtifact(String productId, PhaseType phase, byte[] content) {
        String path = String.format("products/%s/phases/%s",
                productId, phase.name().toLowerCase());

        log.info("Storing artifact: {}", path);

        return store.put(path, content);
    }

    /**
     * Retrieves an artifact by product, phase, and version.
     *
     * @param productId Product identifier
     * @param phase Phase type
     * @param version Version identifier
     * @return Promise of artifact content
     */
    public Promise<byte[]> getArtifact(String productId, PhaseType phase, String version) {
        String path = String.format("products/%s/phases/%s/%s",
                productId, phase.name().toLowerCase(), version);

        log.info("Retrieving artifact: {}", path);

        return store.get(path);
    }

    /**
     * Lists all versions of an artifact.
     *
     * @param productId Product identifier
     * @param phase Phase type
     * @return Promise of version list
     */
    public Promise<java.util.List<String>> listVersions(String productId, PhaseType phase) {
        String prefix = String.format("products/%s/phases/%s/",
                productId, phase.name().toLowerCase());

        log.info("Listing versions for: {}", prefix);

        return store.list(prefix);
    }

    /**
     * Lists canonical completed artifact metadata for a product phase.
     *
     * <p>Only versions with explicit artifact identity, type, actor, and timestamp metadata
     * are returned. This keeps lifecycle gates from treating storage versions as completed
     * artifacts when the canonical artifact record is missing or malformed.</p>
     *
     * @param productId Product identifier
     * @param phase Phase type
     * @return Promise of canonical artifact metadata records
     */
    public Promise<List<ArtifactMetadata>> listCompletedArtifactMetadata(String productId, PhaseType phase) {
        return listVersions(productId, phase)
                .then(versions -> Promises.toList(versions.stream()
                        .map(version -> getMetadata(productId, phase, version)
                                .map(metadata -> ArtifactMetadata.from(productId, phase, version, metadata)))
                        .toList()))
                .map(records -> records.stream()
                        .flatMap(Optional::stream)
                        .toList());
    }

    /**
     * Lists all artifact paths matching the given path prefix.
     * Used by {@code AdvancePhaseUseCase} to verify that required artifact IDs are present.
     *
     * @param prefix path prefix to match
     * @return Promise of matching paths (empty list when none found)
     */
    public Promise<java.util.List<String>> list(String prefix) {
        return store.list(prefix);
    }

    /**
     * Stores artifact metadata.
     *
     * @param productId Product identifier
     * @param phase Phase type
     * @param version Version identifier
     * @param metadata Metadata to store
     * @return Promise of completion
     */
    public Promise<Void> storeMetadata(String productId, PhaseType phase, String version,
                                       Map<String, String> metadata) {
        String path = String.format("products/%s/phases/%s/%s/metadata",
                productId, phase.name().toLowerCase(), version);

        log.info("Storing metadata: {}", path);

        return store.putMetadata(path, metadata);
    }

    /**
     * Retrieves metadata for a specific artifact version.
     *
     * @param productId project identifier
     * @param phase     lifecycle phase
     * @param version   artifact version identifier
     * @return promise of the metadata map (empty map if none stored)
     */
    public Promise<Map<String, String>> getMetadata(String productId, PhaseType phase, String version) {
        String path = String.format("products/%s/phases/%s/%s/metadata",
                productId, phase.name().toLowerCase(), version);
        return store.getMetadata(path);
    }

    /**
     * Deletes a specific artifact version and its associated metadata.
     *
     * @param productId project identifier
     * @param phase     lifecycle phase
     * @param version   artifact version identifier to delete
     * @return promise that completes when deletion is done
     */
    public Promise<Void> deleteArtifactVersion(String productId, PhaseType phase, String version) {
        String contentPath  = String.format("products/%s/phases/%s/%s",
                productId, phase.name().toLowerCase(), version);
        String metadataPath = contentPath + "/metadata";

        log.info("YappcArtifactRepository.deleteArtifactVersion: {}", contentPath);

        return store.delete(contentPath)
                .then(unused -> store.delete(metadataPath));
    }

    /**
     * Canonical artifact metadata used by lifecycle phase readiness.
     *
     * @param artifactId stable artifact identifier
     * @param artifactType canonical artifact type
     * @param version storage version identifier
     * @param title display title
     * @param completedAt canonical completion timestamp
     * @param completedBy actor that completed or approved the artifact
     * @param evidenceId evidence/provenance identifier for completion
     */
    public record ArtifactMetadata(
            String artifactId,
            String artifactType,
            String version,
            String title,
            Instant completedAt,
            String completedBy,
            String evidenceId
    ) {
        private static Optional<ArtifactMetadata> from(
                String productId,
                PhaseType phase,
                String version,
                Map<String, String> metadata
        ) {
            if (metadata == null || metadata.isEmpty()) {
                return Optional.empty();
            }
            String artifactId = firstNonBlank(metadata, "artifactId", "artifact_id", "id");
            String artifactType = firstNonBlank(metadata, "artifactType", "artifact_type", "type");
            String completedAtValue = firstNonBlank(metadata, "completedAt", "completed_at", "createdAt", "created_at");
            String completedBy = firstNonBlank(metadata, "completedBy", "completed_by", "actor", "createdBy", "created_by");
            if (isBlank(artifactId) || isBlank(artifactType) || isBlank(completedAtValue) || isBlank(completedBy)) {
                log.warn(
                        "Skipping malformed artifact metadata: productId={}, phase={}, version={}, artifactId={}, artifactType={}",
                        productId,
                        phase,
                        version,
                        artifactId,
                        artifactType
                );
                return Optional.empty();
            }
            try {
                return Optional.of(new ArtifactMetadata(
                        artifactId,
                        artifactType,
                        version,
                        firstNonBlank(metadata, "title", "name", "displayName", "display_name", "artifactId"),
                        Instant.parse(completedAtValue),
                        completedBy,
                        firstNonBlank(metadata, "evidenceId", "evidence_id", "traceId", "trace_id")
                ));
            } catch (DateTimeParseException e) {
                log.warn(
                        "Skipping artifact metadata with invalid completion timestamp: productId={}, phase={}, version={}",
                        productId,
                        phase,
                        version,
                        e
                );
                return Optional.empty();
            }
        }

        private static String firstNonBlank(Map<String, String> metadata, String... keys) {
            for (String key : keys) {
                String value = metadata.get(key);
                if (!isBlank(value)) {
                    return value;
                }
            }
            return null;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
