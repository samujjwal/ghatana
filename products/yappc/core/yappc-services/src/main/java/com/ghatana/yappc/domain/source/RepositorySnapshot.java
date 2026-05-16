package com.ghatana.yappc.domain.source;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Immutable snapshot of a repository at a specific commit or content hash.
 *              Contains metadata about the snapshot and its files for inventory scanning.
 * @doc.layer domain
 * @doc.pattern ValueObject
 *
 * P0: Immutable snapshot DTO with snapshotId, provider, repoId, commitSha/contentHash,
 *      materialized root, file count, checksum, diagnostics.
 */
public final class RepositorySnapshot {

    private final String snapshotId;
    private final String provider;
    private final String repoId;
    private final String commitSha;
    private final String contentHash;
    private final String materializedRoot;
    private final List<SnapshotFile> files;
    private final int fileCount;
    private final String checksum;
    private final List<SnapshotDiagnostic> diagnostics;
    private final Instant createdAt;
    private final String tenantId;
    private final String workspaceId;
    private final String projectId;

    private RepositorySnapshot(Builder builder) {
        this.snapshotId = Objects.requireNonNull(builder.snapshotId, "snapshotId must not be null");
        this.provider = Objects.requireNonNull(builder.provider, "provider must not be null");
        this.repoId = Objects.requireNonNull(builder.repoId, "repoId must not be null");
        this.commitSha = builder.commitSha;
        this.contentHash = builder.contentHash;
        this.materializedRoot = Objects.requireNonNull(builder.materializedRoot, "materializedRoot must not be null");
        this.files = builder.files != null ? List.copyOf(builder.files) : List.of();
        this.fileCount = this.files.size();
        this.checksum = Objects.requireNonNull(builder.checksum, "checksum must not be null");
        this.diagnostics = builder.diagnostics != null ? List.copyOf(builder.diagnostics) : List.of();
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.projectId = Objects.requireNonNull(builder.projectId, "projectId must not be null");
    }

    /**
     * Unique identifier for this snapshot.
     */
    public String snapshotId() {
        return snapshotId;
    }

    /**
     * The source provider type.
     */
    public String provider() {
        return provider;
    }

    /**
     * Repository identifier.
     */
    public String repoId() {
        return repoId;
    }

    /**
     * Commit SHA if available (Git-based providers).
     */
    public Optional<String> commitSha() {
        return Optional.ofNullable(commitSha);
    }

    /**
     * Content hash for verification (non-Git providers).
     */
    public Optional<String> contentHash() {
        return Optional.ofNullable(contentHash);
    }

    /**
     * Absolute path where files are materialized.
     */
    public String materializedRoot() {
        return materializedRoot;
    }

    /**
     * List of files in the snapshot.
     */
    public List<SnapshotFile> files() {
        return files;
    }

    /**
     * Total file count.
     */
    public int fileCount() {
        return fileCount;
    }

    /**
     * Checksum of the entire snapshot for integrity verification.
     */
    public String checksum() {
        return checksum;
    }

    /**
     * Diagnostics (warnings, errors) from snapshot creation.
     */
    public List<SnapshotDiagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * When the snapshot was created.
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Tenant ID for scope isolation.
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * Workspace ID for scope isolation.
     */
    public String workspaceId() {
        return workspaceId;
    }

    /**
     * Project ID for scope isolation.
     */
    public String projectId() {
        return projectId;
    }

    /**
     * Creates a new builder for RepositorySnapshot.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RepositorySnapshot)) return false;
        RepositorySnapshot that = (RepositorySnapshot) o;
        return Objects.equals(snapshotId, that.snapshotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId);
    }

    @Override
    public String toString() {
        return "RepositorySnapshot{" +
                "snapshotId='" + snapshotId + '\'' +
                ", provider='" + provider + '\'' +
                ", repoId='" + repoId + '\'' +
                ", commitSha='" + commitSha + '\'' +
                ", fileCount=" + fileCount +
                ", tenantId='" + tenantId + '\'' +
                ", workspaceId='" + workspaceId + '\'' +
                ", projectId='" + projectId + '\'' +
                '}';
    }

    /**
     * Builder for RepositorySnapshot.
     */
    public static class Builder {
        private String snapshotId;
        private String provider;
        private String repoId;
        private String commitSha;
        private String contentHash;
        private String materializedRoot;
        private List<SnapshotFile> files;
        private String checksum;
        private List<SnapshotDiagnostic> diagnostics;
        private Instant createdAt;
        private String tenantId;
        private String workspaceId;
        private String projectId;

        private Builder() {}

        public Builder snapshotId(String snapshotId) {
            this.snapshotId = snapshotId;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder repoId(String repoId) {
            this.repoId = repoId;
            return this;
        }

        public Builder commitSha(String commitSha) {
            this.commitSha = commitSha;
            return this;
        }

        public Builder contentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public Builder materializedRoot(String materializedRoot) {
            this.materializedRoot = materializedRoot;
            return this;
        }

        public Builder files(List<SnapshotFile> files) {
            this.files = files != null ? List.copyOf(files) : null;
            return this;
        }

        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder diagnostics(List<SnapshotDiagnostic> diagnostics) {
            this.diagnostics = diagnostics != null ? List.copyOf(diagnostics) : null;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public RepositorySnapshot build() {
            return new RepositorySnapshot(this);
        }
    }

    /**
     * Represents a file within a repository snapshot.
     */
    public record SnapshotFile(
            String relativePath,
            String absolutePath,
            long sizeBytes,
            Instant lastModified,
            String contentChecksum
    ) {
        public SnapshotFile {
            Objects.requireNonNull(relativePath, "relativePath must not be null");
            Objects.requireNonNull(absolutePath, "absolutePath must not be null");
        }
    }

    /**
     * Diagnostic event from snapshot creation.
     */
    public record SnapshotDiagnostic(
            DiagnosticLevel level,
            String code,
            String message,
            String resourcePath,
            Instant timestamp
    ) {
        public SnapshotDiagnostic {
            Objects.requireNonNull(level, "level must not be null");
            Objects.requireNonNull(message, "message must not be null");
            if (timestamp == null) {
                timestamp = Instant.now();
            }
        }
    }

    /**
     * Diagnostic severity levels.
     */
    public enum DiagnosticLevel {
        INFO,
        WARNING,
        ERROR
    }
}
