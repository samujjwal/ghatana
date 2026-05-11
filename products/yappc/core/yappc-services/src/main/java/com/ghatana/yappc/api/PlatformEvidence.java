/**
 * Platform Evidence/Search DTOs
 * 
 * Canonical schema for Data Cloud+AEP platform evidence and search.
 * Defines the structure for evidence records and search queries.
 * 
 * @doc.type class
 * @doc.purpose Platform evidence schema
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
 * Canonical platform evidence schema.
 */
public final class PlatformEvidence {

    private final String evidenceId;
    private final String executionId;
    private final String projectId;
    private final String workspaceId;
    private final String tenantId;
    private final EvidenceRecord record;
    private final EvidenceMetadata metadata;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PlatformEvidence(
            @NotNull String evidenceId,
            @NotNull String executionId,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull String tenantId,
            @NotNull EvidenceRecord record,
            @NotNull EvidenceMetadata metadata,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt
    ) {
        this.evidenceId = evidenceId;
        this.executionId = executionId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.record = record;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String evidenceId() {
        return evidenceId;
    }

    public String executionId() {
        return executionId;
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

    public EvidenceRecord record() {
        return record;
    }

    public EvidenceMetadata metadata() {
        return metadata;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Evidence record.
     */
    public record EvidenceRecord(
            String evidenceType,
            String contentType,
            String content,
            EvidenceSource source,
            List<EvidenceReference> references,
            Map<String, Object> attributes
    ) {}

    /**
     * Evidence source.
     */
    public record EvidenceSource(
            String sourceType,
            String sourceId,
            String sourceVersion,
            String traceId,
            Instant timestamp
    ) {}

    /**
     * Evidence reference.
     */
    public record EvidenceReference(
            String referenceId,
            String referenceType,
            String referenceLocation,
            String checksum
    ) {}

    /**
     * Evidence metadata.
     */
    public record EvidenceMetadata(
            String sessionId,
            String userId,
            String lifecyclePhase,
            Set<String> tags,
            Map<String, String> customMetadata
    ) {}

    /**
     * Search query.
     */
    public record SearchQuery(
            String query,
            String projectId,
            String workspaceId,
            List<String> evidenceTypes,
            Instant startDate,
            Instant endDate,
            Map<String, String> filters
    ) {}

    /**
     * Search result.
     */
    public record SearchResult(
            String evidenceId,
            String evidenceType,
            String contentPreview,
            double relevanceScore,
            Instant timestamp,
            Map<String, String> metadata
    ) {}
}
