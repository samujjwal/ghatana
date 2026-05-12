/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.yappc.domain.intent.IntentInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable generation run model with provenance and review status.
 *
 * <p>This model represents the canonical workflow from user intent through
 * generated artifact, preview, review, and export. Each run has a unique ID,
 * links to a plan, generated artifacts, review decisions, and preview sessions.
 *
 * @doc.type class
 * @doc.purpose Durable generation run model with provenance and review status
 * @doc.layer api
 * @doc.pattern ValueObject
 */
public final class GenerationRun {

    private final String id;
    private final String planId;
    private final String projectId;
    private final String tenantId;
    private final String workspaceId;
    private final IntentInput intent;
    private final RunStatus status;
    private final List<String> artifactIds;
    private final ReviewStatus reviewStatus;
    private final String previewSessionId;
    private final Instant createdAt;
    private final Instant completedAt;
    private final Map<String, Object> provenance;
    private final Map<String, Object> metadata;

    private GenerationRun(Builder builder) {
        this.id = builder.id;
        this.planId = builder.planId;
        this.projectId = builder.projectId;
        this.tenantId = builder.tenantId;
        this.workspaceId = builder.workspaceId;
        this.intent = builder.intent;
        this.status = builder.status;
        this.artifactIds = List.copyOf(builder.artifactIds);
        this.reviewStatus = builder.reviewStatus;
        this.previewSessionId = builder.previewSessionId;
        this.createdAt = builder.createdAt;
        this.completedAt = builder.completedAt;
        this.provenance = Map.copyOf(builder.provenance);
        this.metadata = Map.copyOf(builder.metadata);
    }

    public String id() {
        return id;
    }

    public String planId() {
        return planId;
    }

    public String projectId() {
        return projectId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public IntentInput intent() {
        return intent;
    }

    public RunStatus status() {
        return status;
    }

    public List<String> artifactIds() {
        return artifactIds;
    }

    public ReviewStatus reviewStatus() {
        return reviewStatus;
    }

    public String previewSessionId() {
        return previewSessionId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Map<String, Object> provenance() {
        return provenance;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String planId;
        private String projectId;
        private String tenantId;
        private String workspaceId;
        private IntentInput intent;
        private RunStatus status = RunStatus.PENDING;
        private final java.util.List<String> artifactIds = new java.util.ArrayList<>();
        private ReviewStatus reviewStatus = ReviewStatus.REVIEW_PENDING;
        private String previewSessionId;
        private Instant createdAt = Instant.now();
        private Instant completedAt;
        private final Map<String, Object> provenance = new java.util.HashMap<>();
        private final Map<String, Object> metadata = new java.util.HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder planId(String planId) {
            this.planId = planId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
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

        public Builder intent(IntentInput intent) {
            this.intent = intent;
            return this;
        }

        public Builder status(RunStatus status) {
            this.status = status;
            return this;
        }

        public Builder addArtifactId(String artifactId) {
            this.artifactIds.add(artifactId);
            return this;
        }

        public Builder artifactIds(List<String> artifactIds) {
            this.artifactIds.clear();
            this.artifactIds.addAll(artifactIds);
            return this;
        }

        public Builder reviewStatus(ReviewStatus reviewStatus) {
            this.reviewStatus = reviewStatus;
            return this;
        }

        public Builder previewSessionId(String previewSessionId) {
            this.previewSessionId = previewSessionId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder addProvenance(String key, Object value) {
            this.provenance.put(key, value);
            return this;
        }

        public Builder provenance(Map<String, Object> provenance) {
            this.provenance.clear();
            this.provenance.putAll(provenance);
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        public GenerationRun build() {
            return new GenerationRun(this);
        }
    }

    /**
     * Status of the generation run.
     */
    public enum RunStatus {
        PENDING,
        GENERATING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Review status of the generated artifacts.
     * 
     * Idempotent state machine for generation review, apply, reject, and rollback.
     * States: GENERATING → GENERATED → REVIEW_PENDING → APPROVED/APPLIED/APPLY_FAILED → REJECTED → ROLLBACK_REQUESTED → ROLLED_BACK/ROLLBACK_FAILED → FAILED
     */
    public enum ReviewStatus {
        GENERATING,
        GENERATED,
        REVIEW_PENDING,
        APPROVED,
        APPLIED,
        APPLY_FAILED,
        REJECTED,
        ROLLBACK_REQUESTED,
        ROLLED_BACK,
        ROLLBACK_FAILED,
        FAILED
    }
}
