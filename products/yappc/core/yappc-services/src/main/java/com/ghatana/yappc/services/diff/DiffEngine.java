/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Real diff engine for comparing generated content.
 * Loads old/new content by contentRef, uses proper line diff library,
 * computes added/deleted/modified regions, line ranges, ownership (AI/user/system),
 * and provenance per region.
 *
 * @doc.type interface
 * @doc.purpose Real diff engine for comparing generated content with region-level granularity
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DiffEngine {

    /**
     * Computes diff between old and new content.
     *
     * @param oldContent The original content
     * @param newContent The modified content
     * @param filePath The file path for context
     * @return DiffResult containing all diff regions
     */
    DiffResult computeDiff(@NotNull String oldContent, @NotNull String newContent, @NotNull String filePath);

    /**
     * Computes diff by content references.
     *
     * @param oldContentRef The old content reference
     * @param newContentRef The new content reference
     * @return DiffResult containing all diff regions
     */
    DiffResult computeDiffByContentRefs(@NotNull String oldContentRef, @NotNull String newContentRef);

    /**
     * Result of a diff computation.
     */
    record DiffResult(
            @NotNull String oldContentHash,
            @NotNull String newContentHash,
            @NotNull String filePath,
            @NotNull List<DiffRegion> regions,
            @NotNull DiffStatistics statistics
    ) {
        public DiffResult {
            if (oldContentHash == null || oldContentHash.isBlank()) {
                throw new IllegalArgumentException("oldContentHash is required");
            }
            if (newContentHash == null || newContentHash.isBlank()) {
                throw new IllegalArgumentException("newContentHash is required");
            }
            if (filePath == null || filePath.isBlank()) {
                throw new IllegalArgumentException("filePath is required");
            }
            if (regions == null) {
                throw new IllegalArgumentException("regions cannot be null");
            }
            if (statistics == null) {
                throw new IllegalArgumentException("statistics cannot be null");
            }
        }
    }

    /**
     * A single diff region.
     */
    record DiffRegion(
            @NotNull String regionId,
            @NotNull DiffType diffType,
            int startLine,
            int endLine,
            @NotNull String originalContent,
            @NotNull String modifiedContent,
            @NotNull Ownership ownership,
            @NotNull RegionProvenance provenance
    ) {
        public DiffRegion {
            if (regionId == null || regionId.isBlank()) {
                throw new IllegalArgumentException("regionId is required");
            }
            if (startLine < 0) {
                throw new IllegalArgumentException("startLine must be >= 0");
            }
            if (endLine < startLine) {
                throw new IllegalArgumentException("endLine must be >= startLine");
            }
            if (originalContent == null) {
                throw new IllegalArgumentException("originalContent cannot be null");
            }
            if (modifiedContent == null) {
                throw new IllegalArgumentException("modifiedContent cannot be null");
            }
        }
    }

    /**
     * Type of diff region.
     */
    enum DiffType {
        ADDED,
        DELETED,
        MODIFIED,
        UNCHANGED
    }

    /**
     * Ownership of the diff region.
     */
    enum Ownership {
        AI_GENERATED,
        USER_EDITED,
        SYSTEM_GENERATED,
        HYBRID
    }

    /**
     * Provenance information for a diff region.
     */
    record RegionProvenance(
            @NotNull String generatorVersion,
            @Nullable String sourcePromptHash,
            @Nullable String actorId,
            @NotNull java.time.Instant timestamp,
            @Nullable String correlationId
    ) {
        public RegionProvenance {
            if (generatorVersion == null || generatorVersion.isBlank()) {
                throw new IllegalArgumentException("generatorVersion is required");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp is required");
            }
        }
    }

    /**
     * Statistics for the diff result.
     */
    record DiffStatistics(
            int linesAdded,
            int linesDeleted,
            int linesModified,
            int linesUnchanged,
            int totalLines
    ) {
        public DiffStatistics {
            if (linesAdded < 0) {
                throw new IllegalArgumentException("linesAdded must be >= 0");
            }
            if (linesDeleted < 0) {
                throw new IllegalArgumentException("linesDeleted must be >= 0");
            }
            if (linesModified < 0) {
                throw new IllegalArgumentException("linesModified must be >= 0");
            }
            if (linesUnchanged < 0) {
                throw new IllegalArgumentException("linesUnchanged must be >= 0");
            }
            if (totalLines < 0) {
                throw new IllegalArgumentException("totalLines must be >= 0");
            }
        }
    }
}
