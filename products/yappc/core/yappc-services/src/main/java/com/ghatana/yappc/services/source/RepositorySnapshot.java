package com.ghatana.yappc.services.source;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Durable repository snapshot manifest used by compiler jobs
 * @doc.layer service
 * @doc.pattern DataTransferObject
 */
public record RepositorySnapshot(
    String snapshotId,
    String provider,
    String repoId,
    String commitSha,
    String branch,
    String localRootPath,
    String contentChecksum,
    Instant snapshotAt,
    List<SnapshotFile> files,
    List<Map<String, Object>> diagnostics
) {

    public RepositorySnapshot {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId must not be blank");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (repoId == null || repoId.isBlank()) {
            throw new IllegalArgumentException("repoId must not be blank");
        }
        if (localRootPath == null || localRootPath.isBlank()) {
            throw new IllegalArgumentException("localRootPath must not be blank");
        }
        files = files == null ? List.of() : List.copyOf(files);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /**
     * @doc.type record
     * @doc.purpose Snapshot file metadata used for deterministic inventory and replay
     * @doc.layer service
     * @doc.pattern DataTransferObject
     */
    public record SnapshotFile(
        String relativePath,
        String absolutePath,
        long sizeBytes,
        Instant lastModifiedAt,
        boolean materialized
    ) {}
}
