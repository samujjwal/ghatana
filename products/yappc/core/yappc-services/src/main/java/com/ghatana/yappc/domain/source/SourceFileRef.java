package com.ghatana.yappc.domain.source;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Canonical durable file identity for source files across repository snapshots
 * @doc.layer domain
 * @doc.pattern ValueObject
 * 
 * P0: Added SourceFileRef to provide canonical durable file identity across repository snapshots.
 * Enables tracking file identity independent of snapshot-specific node IDs, supporting
 * drift detection, incremental updates, and cross-snapshot file correlation.
 */
public record SourceFileRef(
    /**
     * Canonical file identifier derived from repository, path, and content hash.
     * Stable across snapshots for the same file content.
     */
    String fileRefId,
    
    /**
     * Repository identifier (e.g., GitHub repo URL, local folder path, archive path)
     */
    String repositoryId,
    
    /**
     * Relative path of the file within the repository
     */
    String relativePath,
    
    /**
     * SHA-256 checksum of file content
     */
    String contentHash,
    
    /**
     * File size in bytes
     */
    long sizeBytes,
    
    /**
     * File extension or type (e.g., ".java", ".ts", ".yaml")
     */
    String fileType,
    
    /**
     * Language or classification (e.g., "java", "typescript", "yaml")
     */
    String language,
    
    /**
     * Last modification timestamp from source
     */
    Instant lastModifiedAt,
    
    /**
     * Snapshot ID where this file was first observed
     */
    String firstSeenSnapshotId,
    
    /**
     * Snapshot ID where this file was last observed
     */
    String lastSeenSnapshotId,
    
    /**
     * Additional metadata about the file
     */
    Map<String, String> metadata,
    
    /**
     * Tenant identifier for scope isolation
     */
    String tenantId,
    
    /**
     * Workspace identifier for scope isolation
     */
    String workspaceId,
    
    /**
     * Project identifier for scope isolation
     */
    String projectId
) {
    public SourceFileRef {
        Objects.requireNonNull(fileRefId, "fileRefId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
    }
    
    /**
     * Create a canonical file reference ID from repository, path, and content hash.
     * 
     * @param repositoryId Repository identifier
     * @param relativePath Relative path within repository
     * @param contentHash SHA-256 content hash
     * @return Canonical file reference ID
     */
    public static String computeFileRefId(String repositoryId, String relativePath, String contentHash) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(repositoryId.getBytes());
            digest.update(relativePath.getBytes());
            digest.update(contentHash.getBytes());
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
    
    /**
     * Builder for SourceFileRef with automatic fileRefId computation.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for SourceFileRef.
     */
    public static class Builder {
        private String repositoryId;
        private String relativePath;
        private String contentHash;
        private long sizeBytes;
        private String fileType;
        private String language;
        private Instant lastModifiedAt;
        private String firstSeenSnapshotId;
        private String lastSeenSnapshotId;
        private Map<String, String> metadata = Map.of();
        private String tenantId;
        private String workspaceId;
        private String projectId;
        
        public Builder repositoryId(String repositoryId) {
            this.repositoryId = repositoryId;
            return this;
        }
        
        public Builder relativePath(String relativePath) {
            this.relativePath = relativePath;
            return this;
        }
        
        public Builder contentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }
        
        public Builder sizeBytes(long sizeBytes) {
            this.sizeBytes = sizeBytes;
            return this;
        }
        
        public Builder fileType(String fileType) {
            this.fileType = fileType;
            return this;
        }
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder lastModifiedAt(Instant lastModifiedAt) {
            this.lastModifiedAt = lastModifiedAt;
            return this;
        }
        
        public Builder firstSeenSnapshotId(String firstSeenSnapshotId) {
            this.firstSeenSnapshotId = firstSeenSnapshotId;
            return this;
        }
        
        public Builder lastSeenSnapshotId(String lastSeenSnapshotId) {
            this.lastSeenSnapshotId = lastSeenSnapshotId;
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
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
        
        public SourceFileRef build() {
            String fileRefId = computeFileRefId(
                Objects.requireNonNull(repositoryId, "repositoryId is required"),
                Objects.requireNonNull(relativePath, "relativePath is required"),
                Objects.requireNonNull(contentHash, "contentHash is required")
            );
            
            return new SourceFileRef(
                fileRefId,
                repositoryId,
                relativePath,
                contentHash,
                sizeBytes,
                fileType,
                language,
                lastModifiedAt != null ? lastModifiedAt : Instant.now(),
                firstSeenSnapshotId,
                lastSeenSnapshotId,
                metadata != null ? metadata : Map.of(),
                Objects.requireNonNull(tenantId, "tenantId is required"),
                Objects.requireNonNull(workspaceId, "workspaceId is required"),
                Objects.requireNonNull(projectId, "projectId is required")
            );
        }
    }
}
