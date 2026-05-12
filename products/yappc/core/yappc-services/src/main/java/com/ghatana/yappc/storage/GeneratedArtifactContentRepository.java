/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Repository for storing and retrieving generated artifact content.
 * Stores actual content with artifactId, contentHash, path, MIME/language, size,
 * source prompt hash, generator version, AI/degraded mode, and provenance metadata.
 *
 * @doc.type interface
 * @doc.purpose Repository for storing and retrieving generated artifact content
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface GeneratedArtifactContentRepository {

    /**
     * Stores generated artifact content.
     *
     * @param content The artifact content to store
     * @return true if successful, false otherwise
     */
    boolean store(@NotNull GeneratedArtifactContent content);

    /**
     * Retrieves artifact content by artifact ID.
     *
     * @param artifactId The artifact ID
     * @return Optional containing the content if found
     */
    Optional<GeneratedArtifactContent> retrieveByArtifactId(@NotNull String artifactId);

    /**
     * Retrieves artifact content by content hash.
     *
     * @param contentHash The content hash
     * @return Optional containing the content if found
     */
    Optional<GeneratedArtifactContent> retrieveByContentHash(@NotNull String contentHash);

    /**
     * Deletes artifact content by artifact ID.
     *
     * @param artifactId The artifact ID
     * @return true if successful, false otherwise
     */
    boolean deleteByArtifactId(@NotNull String artifactId);

    /**
     * Checks if content exists for a given artifact ID.
     *
     * @param artifactId The artifact ID
     * @return true if content exists, false otherwise
     */
    boolean existsByArtifactId(@NotNull String artifactId);

    /**
     * Generated artifact content model.
     */
    record GeneratedArtifactContent(
            @NotNull String artifactId,
            @NotNull String contentHash,
            @NotNull String path,
            @NotNull String mimeType,
            @NotNull String language,
            long size,
            @Nullable String sourcePromptHash,
            @Nullable String generatorVersion,
            boolean isAiMode,
            boolean isDegraded,
            @NotNull ProvenanceMetadata provenance,
            @NotNull String content
    ) {
        public GeneratedArtifactContent {
            if (artifactId == null || artifactId.isBlank()) {
                throw new IllegalArgumentException("artifactId is required");
            }
            if (contentHash == null || contentHash.isBlank()) {
                throw new IllegalArgumentException("contentHash is required");
            }
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path is required");
            }
            if (mimeType == null || mimeType.isBlank()) {
                throw new IllegalArgumentException("mimeType is required");
            }
            if (language == null || language.isBlank()) {
                throw new IllegalArgumentException("language is required");
            }
            if (size < 0) {
                throw new IllegalArgumentException("size must be >= 0");
            }
            if (content == null) {
                throw new IllegalArgumentException("content is required");
            }
        }
    }

    /**
     * Provenance metadata for generated artifact content.
     */
    record ProvenanceMetadata(
            @NotNull String generationRunId,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull String tenantId,
            @Nullable String actorId,
            @NotNull String correlationId,
            @NotNull java.time.Instant generatedAt
    ) {
        public ProvenanceMetadata {
            if (generationRunId == null || generationRunId.isBlank()) {
                throw new IllegalArgumentException("generationRunId is required");
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
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlationId is required");
            }
            if (generatedAt == null) {
                throw new IllegalArgumentException("generatedAt is required");
            }
        }
    }
}
