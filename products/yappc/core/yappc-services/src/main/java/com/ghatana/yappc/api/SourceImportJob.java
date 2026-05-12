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
 * Source import job lifecycle schema.
 * Tracks the lifecycle of source import jobs from submission through validation,
 * decompilation, mapping, residual review, completion, or failure.
 *
 * Import jobs never execute untrusted code directly - they operate in a sandboxed
 * environment with strict validation before any code execution.
 *
 * @doc.type class
 * @doc.purpose Source import job lifecycle schema with sandboxed execution
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class SourceImportJob {

    private final String jobId;
    private final String projectId;
    private final String workspaceId;
    private final String tenantId;
    private final String sourceUrl;
    private final String sourceType;
    private final JobStatus status;
    private final JobProgress progress;
    private final List<ValidationResult> validationResults;
    private final List<DecompilationResult> decompilationResults;
    private final List<MappingResult> mappingResults;
    private final String residualReviewStatus;
    private final Instant submittedAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String submittedBy;
    private final String error;
    private final Map<String, String> metadata;

    public SourceImportJob(
            @NotNull String jobId,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull String tenantId,
            @NotNull String sourceUrl,
            @NotNull String sourceType,
            @NotNull JobStatus status,
            @NotNull JobProgress progress,
            @NotNull List<ValidationResult> validationResults,
            @NotNull List<DecompilationResult> decompilationResults,
            @NotNull List<MappingResult> mappingResults,
            @Nullable String residualReviewStatus,
            @NotNull Instant submittedAt,
            @Nullable Instant startedAt,
            @Nullable Instant completedAt,
            @NotNull String submittedBy,
            @Nullable String error,
            @NotNull Map<String, String> metadata
    ) {
        this.jobId = jobId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.sourceUrl = sourceUrl;
        this.sourceType = sourceType;
        this.status = status;
        this.progress = progress;
        this.validationResults = List.copyOf(validationResults);
        this.decompilationResults = List.copyOf(decompilationResults);
        this.mappingResults = List.copyOf(mappingResults);
        this.residualReviewStatus = residualReviewStatus;
        this.submittedAt = submittedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.submittedBy = submittedBy;
        this.error = error;
        this.metadata = Map.copyOf(metadata);
    }

    public String jobId() {
        return jobId;
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

    public String sourceUrl() {
        return sourceUrl;
    }

    public String sourceType() {
        return sourceType;
    }

    public JobStatus status() {
        return status;
    }

    public JobProgress progress() {
        return progress;
    }

    public List<ValidationResult> validationResults() {
        return validationResults;
    }

    public List<DecompilationResult> decompilationResults() {
        return decompilationResults;
    }

    public List<MappingResult> mappingResults() {
        return mappingResults;
    }

    public String residualReviewStatus() {
        return residualReviewStatus;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public String submittedBy() {
        return submittedBy;
    }

    public String error() {
        return error;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    /**
     * Job status enum representing the lifecycle states.
     */
    public enum JobStatus {
        SUBMITTED,
        VALIDATING,
        DECOMPILING,
        MAPPING,
        RESIDUAL_REVIEW_REQUIRED,
        COMPLETED,
        FAILED
    }

    /**
     * Job progress tracking.
     */
    public record JobProgress(
            int currentStep,
            int totalSteps,
            double percentage,
            String currentPhase
    ) {
        public JobProgress {
            if (currentStep < 0) {
                throw new IllegalArgumentException("currentStep must be >= 0");
            }
            if (totalSteps < 1) {
                throw new IllegalArgumentException("totalSteps must be >= 1");
            }
            if (currentStep > totalSteps) {
                throw new IllegalArgumentException("currentStep cannot exceed totalSteps");
            }
            if (percentage < 0 || percentage > 100) {
                throw new IllegalArgumentException("percentage must be between 0 and 100");
            }
        }
    }

    /**
     * Validation result for source import.
     */
    public record ValidationResult(
            @NotNull String validationId,
            @NotNull String rule,
            @NotNull boolean passed,
            @Nullable String message,
            @NotNull Severity severity
    ) {
        public ValidationResult {
            if (validationId == null || validationId.isBlank()) {
                throw new IllegalArgumentException("validationId is required");
            }
            if (rule == null || rule.isBlank()) {
                throw new IllegalArgumentException("rule is required");
            }
        }
    }

    /**
     * Severity level for validation results.
     */
    public enum Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    /**
     * Decompilation result.
     */
    public record DecompilationResult(
            @NotNull String componentId,
            @NotNull String componentName,
            @NotNull boolean success,
            @Nullable String decompiledCode,
            @Nullable String error,
            @NotNull Map<String, String> properties
    ) {
        public DecompilationResult {
            if (componentId == null || componentId.isBlank()) {
                throw new IllegalArgumentException("componentId is required");
            }
            if (componentName == null || componentName.isBlank()) {
                throw new IllegalArgumentException("componentName is required");
            }
        }
    }

    /**
     * Mapping result.
     */
    public record MappingResult(
            @NotNull String mappingId,
            @NotNull String sourceComponent,
            @NotNull String targetArtifact,
            @NotNull boolean success,
            @Nullable String error,
            @NotNull Map<String, String> mappingData
    ) {
        public MappingResult {
            if (mappingId == null || mappingId.isBlank()) {
                throw new IllegalArgumentException("mappingId is required");
            }
            if (sourceComponent == null || sourceComponent.isBlank()) {
                throw new IllegalArgumentException("sourceComponent is required");
            }
            if (targetArtifact == null || targetArtifact.isBlank()) {
                throw new IllegalArgumentException("targetArtifact is required");
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String jobId = java.util.UUID.randomUUID().toString();
        private String projectId;
        private String workspaceId;
        private String tenantId;
        private String sourceUrl;
        private String sourceType;
        private JobStatus status = JobStatus.SUBMITTED;
        private JobProgress progress = new JobProgress(0, 5, 0, "SUBMITTED");
        private final List<ValidationResult> validationResults = new java.util.ArrayList<>();
        private final List<DecompilationResult> decompilationResults = new java.util.ArrayList<>();
        private final List<MappingResult> mappingResults = new java.util.ArrayList<>();
        private String residualReviewStatus;
        private Instant submittedAt = Instant.now();
        private Instant startedAt;
        private Instant completedAt;
        private String submittedBy;
        private String error;
        private final Map<String, String> metadata = new java.util.HashMap<>();

        public Builder jobId(String jobId) {
            this.jobId = jobId;
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

        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder status(JobStatus status) {
            this.status = status;
            return this;
        }

        public Builder progress(JobProgress progress) {
            this.progress = progress;
            return this;
        }

        public Builder addValidationResult(ValidationResult result) {
            this.validationResults.add(result);
            return this;
        }

        public Builder validationResults(List<ValidationResult> validationResults) {
            this.validationResults.clear();
            this.validationResults.addAll(validationResults);
            return this;
        }

        public Builder addDecompilationResult(DecompilationResult result) {
            this.decompilationResults.add(result);
            return this;
        }

        public Builder decompilationResults(List<DecompilationResult> decompilationResults) {
            this.decompilationResults.clear();
            this.decompilationResults.addAll(decompilationResults);
            return this;
        }

        public Builder addMappingResult(MappingResult result) {
            this.mappingResults.add(result);
            return this;
        }

        public Builder mappingResults(List<MappingResult> mappingResults) {
            this.mappingResults.clear();
            this.mappingResults.addAll(mappingResults);
            return this;
        }

        public Builder residualReviewStatus(String residualReviewStatus) {
            this.residualReviewStatus = residualReviewStatus;
            return this;
        }

        public Builder submittedAt(Instant submittedAt) {
            this.submittedAt = submittedAt;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder submittedBy(String submittedBy) {
            this.submittedBy = submittedBy;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        public SourceImportJob build() {
            if (projectId == null || projectId.isBlank()) {
                throw new IllegalArgumentException("projectId is required");
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new IllegalArgumentException("workspaceId is required");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalArgumentException("sourceUrl is required");
            }
            if (sourceType == null || sourceType.isBlank()) {
                throw new IllegalArgumentException("sourceType is required");
            }
            if (submittedBy == null || submittedBy.isBlank()) {
                throw new IllegalArgumentException("submittedBy is required");
            }
            return new SourceImportJob(
                    jobId,
                    projectId,
                    workspaceId,
                    tenantId,
                    sourceUrl,
                    sourceType,
                    status,
                    progress,
                    validationResults,
                    decompilationResults,
                    mappingResults,
                    residualReviewStatus,
                    submittedAt,
                    startedAt,
                    completedAt,
                    submittedBy,
                    error,
                    metadata
            );
        }
    }
}
