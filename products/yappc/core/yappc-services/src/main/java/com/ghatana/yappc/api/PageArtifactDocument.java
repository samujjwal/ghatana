/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canonical Page Artifact Document contract.
 * Defines the schema for page artifacts that are persisted and shared across the YAPPC platform.
 * Includes builder document, operation log, sync state, preview trust, and data classification.
 *
 * @doc.type class
 * @doc.purpose Canonical page artifact document contract with optimistic concurrency and operation logging
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class PageArtifactDocument {

    private final String artifactId;
    private final String projectId;
    private final String workspaceId;
    private final String tenantId;
    private final String builderDocument;
    private final String registryVersion;
    private final List<OperationLogEntry> operationLog;
    private final SyncState syncState;
    private final PreviewTrust previewTrust;
    private final String dataClassification;
    private final Long documentVersion;
    private final String etag;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;

    public PageArtifactDocument(
            @NotNull String artifactId,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull String tenantId,
            @NotNull String builderDocument,
            @NotNull String registryVersion,
            @NotNull List<OperationLogEntry> operationLog,
            @NotNull SyncState syncState,
            @NotNull PreviewTrust previewTrust,
            @NotNull String dataClassification,
            @NotNull Long documentVersion,
            @NotNull String etag,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull String createdBy,
            @NotNull String updatedBy
    ) {
        this.artifactId = artifactId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.builderDocument = builderDocument;
        this.registryVersion = registryVersion;
        this.operationLog = List.copyOf(operationLog);
        this.syncState = syncState;
        this.previewTrust = previewTrust;
        this.dataClassification = dataClassification;
        this.documentVersion = documentVersion;
        this.etag = etag;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    public String artifactId() {
        return artifactId;
    }

    public String projectId() {
        return projectId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String builderDocument() {
        return builderDocument;
    }

    public String registryVersion() {
        return registryVersion;
    }

    public List<OperationLogEntry> operationLog() {
        return operationLog;
    }

    public SyncState syncState() {
        return syncState;
    }

    public PreviewTrust previewTrust() {
        return previewTrust;
    }

    public String dataClassification() {
        return dataClassification;
    }

    public Long documentVersion() {
        return documentVersion;
    }

    public String etag() {
        return etag;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public String updatedBy() {
        return updatedBy;
    }

    /**
     * Operation log entry tracking mutations to the page artifact.
     */
    public record OperationLogEntry(
            @NotNull String operationId,
            @NotNull OperationType operationType,
            @NotNull String actorId,
            @NotNull Instant timestamp,
            @Nullable String previousVersion,
            @Nullable String newVersion,
            @Nullable Map<String, String> metadata,
            @Nullable String reason
    ) {
        public OperationLogEntry {
            if (operationId == null || operationId.isBlank()) {
                throw new IllegalArgumentException("operationId is required");
            }
            if (actorId == null || actorId.isBlank()) {
                throw new IllegalArgumentException("actorId is required");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp is required");
            }
        }
    }

    /**
     * Type of operation performed on the page artifact.
     */
    public enum OperationType {
        SAVE,
        IMPORT,
        RELOAD,
        OVERWRITE,
        MIGRATION,
        GOVERNANCE_DECISION
    }

    /**
     * Sync state of the page artifact.
     */
    public enum SyncState {
        SYNCED,
        PENDING_SYNC,
        SYNC_FAILED,
        CONFLICT
    }

    /**
     * Preview trust status for the page artifact.
     */
    public enum PreviewTrust {
        TRUSTED,
        UNTRUSTED,
        REQUIRES_REVIEW,
        BLOCKED
    }

    /**
     * Data classification level.
     */
    public enum DataClassification {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        RESTRICTED
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String artifactId;
        private String projectId;
        private String workspaceId;
        private String tenantId;
        private String builderDocument;
        private String registryVersion;
        private final List<OperationLogEntry> operationLog = new java.util.ArrayList<>();
        private SyncState syncState = SyncState.SYNCED;
        private PreviewTrust previewTrust = PreviewTrust.REQUIRES_REVIEW;
        private String dataClassification = DataClassification.INTERNAL.name();
        private Long documentVersion = 1L;
        private String etag;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private String createdBy;
        private String updatedBy;

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder builderDocument(String builderDocument) {
            this.builderDocument = builderDocument;
            return this;
        }

        public Builder registryVersion(String registryVersion) {
            this.registryVersion = registryVersion;
            return this;
        }

        public Builder addOperationLogEntry(OperationLogEntry entry) {
            this.operationLog.add(entry);
            return this;
        }

        public Builder operationLog(List<OperationLogEntry> operationLog) {
            this.operationLog.clear();
            this.operationLog.addAll(operationLog);
            return this;
        }

        public Builder syncState(SyncState syncState) {
            this.syncState = syncState;
            return this;
        }

        public Builder previewTrust(PreviewTrust previewTrust) {
            this.previewTrust = previewTrust;
            return this;
        }

        public Builder dataClassification(String dataClassification) {
            this.dataClassification = dataClassification;
            return this;
        }

        public Builder dataClassification(DataClassification dataClassification) {
            this.dataClassification = dataClassification.name();
            return this;
        }

        public Builder documentVersion(Long documentVersion) {
            this.documentVersion = documentVersion;
            return this;
        }

        public Builder etag(String etag) {
            this.etag = etag;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public PageArtifactDocument build() {
            if (artifactId == null || artifactId.isBlank()) {
                throw new IllegalArgumentException("artifactId is required");
            }
            if (projectId == null || projectId.isBlank()) {
                throw new IllegalArgumentException("projectId is required");
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new IllegalArgumentException("workspaceId is required");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (builderDocument == null || builderDocument.isBlank()) {
                throw new IllegalArgumentException("builderDocument is required");
            }
            if (registryVersion == null || registryVersion.isBlank()) {
                throw new IllegalArgumentException("registryVersion is required");
            }
            if (etag == null) {
                etag = generateEtag();
            }
            return new PageArtifactDocument(
                    artifactId,
                    projectId,
                    workspaceId,
                    tenantId,
                    builderDocument,
                    registryVersion,
                    operationLog,
                    syncState,
                    previewTrust,
                    dataClassification,
                    documentVersion,
                    etag,
                    createdAt,
                    updatedAt,
                    createdBy,
                    updatedBy
            );
        }

        private String generateEtag() {
            return artifactId + "-" + documentVersion + "-" + updatedAt;
        }
    }
}
