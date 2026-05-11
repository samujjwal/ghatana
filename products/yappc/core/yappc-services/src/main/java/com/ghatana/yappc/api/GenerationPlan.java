/**
 * Generation Plan
 * 
 * Canonical schema for generation plans that track AI generation operations.
 * Defines the structure for generation plans including content, provenance, and status.
 * 
 * @doc.type class
 * @doc.purpose Generation plan schema
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical generation plan schema.
 */
public final class GenerationPlan {

    private final String id;
    private final String projectId;
    private final String workspaceId;
    private final String tenantId;
    private final String lifecyclePhase;
    private final GenerationMetadata metadata;
    private final List<GeneratedFile> generatedFiles;
    private final GenerationContent content;
    private final GenerationProvenance provenance;
    private final GenerationStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;
    private final Long revision;

    public GenerationPlan(
            @NotNull String id,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull String tenantId,
            @NotNull String lifecyclePhase,
            @NotNull GenerationMetadata metadata,
            @NotNull List<GeneratedFile> generatedFiles,
            @NotNull GenerationContent content,
            @NotNull GenerationProvenance provenance,
            @NotNull GenerationStatus status,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull String createdBy,
            @NotNull String updatedBy,
            @NotNull Long revision
    ) {
        this.id = id;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.lifecyclePhase = lifecyclePhase;
        this.metadata = metadata;
        this.generatedFiles = generatedFiles;
        this.content = content;
        this.provenance = provenance;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.revision = revision;
    }

    public String id() {
        return id;
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

    public String lifecyclePhase() {
        return lifecyclePhase;
    }

    public GenerationMetadata metadata() {
        return metadata;
    }

    public List<GeneratedFile> generatedFiles() {
        return generatedFiles;
    }

    public GenerationContent content() {
        return content;
    }

    public GenerationProvenance provenance() {
        return provenance;
    }

    public GenerationStatus status() {
        return status;
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

    public Long revision() {
        return revision;
    }

    /**
     * Generation metadata.
     */
    public record GenerationMetadata(
            String generationType,
            String modelId,
            String modelVersion,
            Map<String, String> parameters,
            Set<String> tags,
            String correlationId
    ) {}

    /**
     * Generated file record.
     */
    public record GeneratedFile(
            String fileId,
            String fileName,
            String filePath,
            String fileType,
            FileProvenance provenance,
            Instant generatedAt,
            long fileSize
    ) {}

    /**
     * File provenance.
     */
    public record FileProvenance(
            String sourceType,
            String sourceId,
            String sourceVersion,
            List<String> dependencyIds,
            String checksum,
            Instant lastModified
    ) {}

    /**
     * Generation content.
     */
    public record GenerationContent(
            String contentId,
            String contentType,
            String contentData,
            boolean isStored,
            String storageLocation,
            Instant storedAt
    ) {}

    /**
     * Generation provenance.
     */
    public record GenerationProvenance(
            String sessionId,
            String traceId,
            PlatformRunReference platformRun,
            List<String> evidenceIds,
            Map<String, String> additionalMetadata
    ) {}

    /**
     * Platform run reference.
     */
    public record PlatformRunReference(
            String runId,
            String platformType,
            String status,
            Instant startTime,
            Instant endTime
    ) {}

    /**
     * Generation status.
     */
    public record GenerationStatus(
            GenerationState state,
            boolean isDegraded,
            String degradationReason,
            boolean canAutoApply,
            String autoApplyBlockReason,
            Instant completedAt
    ) {
        public enum GenerationState {
            PENDING,
            IN_PROGRESS,
            COMPLETED,
            FAILED,
            ROLLED_BACK
        }
    }
}
