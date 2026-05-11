/**
 * Structured Diff with Region Provenance
 * 
 * Canonical schema for structured diffs with region-level provenance tracking.
 * Defines the structure for diffs that include region-specific provenance information.
 * 
 * @doc.type class
 * @doc.purpose Structured diff schema
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
 * Canonical structured diff schema with region-level provenance.
 */
public final class StructuredDiff {

    private final String diffId;
    private final String generationPlanId;
    private final String projectId;
    private final DiffMetadata metadata;
    private final List<DiffRegion> regions;
    private final DiffSummary summary;
    private final Instant createdAt;

    public StructuredDiff(
            @NotNull String diffId,
            @NotNull String generationPlanId,
            @NotNull String projectId,
            @NotNull DiffMetadata metadata,
            @NotNull List<DiffRegion> regions,
            @NotNull DiffSummary summary,
            @NotNull Instant createdAt
    ) {
        this.diffId = diffId;
        this.generationPlanId = generationPlanId;
        this.projectId = projectId;
        this.metadata = metadata;
        this.regions = regions;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public String diffId() {
        return diffId;
    }

    public String generationPlanId() {
        return generationPlanId;
    }

    public String projectId() {
        return projectId;
    }

    public DiffMetadata metadata() {
        return metadata;
    }

    public List<DiffRegion> regions() {
        return regions;
    }

    public DiffSummary summary() {
        return summary;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Diff metadata.
     */
    public record DiffMetadata(
            String sourceVersion,
            String targetVersion,
            String diffAlgorithm,
            Map<String, String> parameters,
            String correlationId
    ) {}

    /**
     * Diff region with provenance.
     */
    public record DiffRegion(
            String regionId,
            String filePath,
            RegionType type,
            RegionStatus status,
            DiffRegionProvenance provenance,
            List<DiffHunk> hunks,
            Map<String, String> metadata
    ) {
        public enum RegionType {
            ADDED,
            MODIFIED,
            DELETED,
            MOVED
        }

        public enum RegionStatus {
            PENDING,
            APPROVED,
            REJECTED,
            AUTO_APPLIED
        }
    }

    /**
     * Diff region provenance.
     */
    public record DiffRegionProvenance(
            String sourceType,
            String sourceId,
            String sourceVersion,
            String aiModelId,
            String aiModelVersion,
            String sessionId,
            String traceId,
            List<String> evidenceIds,
            Map<String, String> additionalMetadata
    ) {}

    /**
     * Diff hunk.
     */
    public record DiffHunk(
            String hunkId,
            int oldStart,
            int oldLines,
            int newStart,
            int newLines,
            String oldContent,
            String newContent,
            String diffHeader
    ) {}

    /**
     * Diff summary.
     */
    public record DiffSummary(
            int totalRegions,
            int addedRegions,
            int modifiedRegions,
            int deletedRegions,
            int movedRegions,
            int totalLinesAdded,
            int totalLinesDeleted,
            Map<String, Integer> regionsByFile
    ) {}
}
