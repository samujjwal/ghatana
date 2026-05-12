/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.content;

import com.ghatana.yappc.storage.GeneratedArtifactContentRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Service for resolving artifact content references.
 * Provides safe content retrieval with redaction rules for restricted artifacts.
 *
 * @doc.type interface
 * @doc.purpose Service for resolving artifact content references with redaction
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ArtifactContentResolver {

    /**
     * Resolves artifact content by content reference.
     * The content reference can be an artifactId, contentHash, or other identifier.
     *
     * @param contentRef The content reference to resolve
     * @return Optional containing the content if found
     */
    Optional<ResolvedArtifactContent> resolveContent(@NotNull String contentRef);

    /**
     * Resolves artifact content by artifact ID.
     *
     * @param artifactId The artifact ID
     * @return Optional containing the content if found
     */
    Optional<ResolvedArtifactContent> resolveByArtifactId(@NotNull String artifactId);

    /**
     * Resolved artifact content with redaction applied for restricted artifacts.
     */
    record ResolvedArtifactContent(
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
            @NotNull String content,
            boolean isRedacted,
            @Nullable String redactionReason
    ) {
        public ResolvedArtifactContent {
            if (artifactId == null || artifactId.isBlank()) {
                throw new IllegalArgumentException("artifactId is required");
            }
            if (contentHash == null || contentHash.isBlank()) {
                throw new IllegalArgumentException("contentHash is required");
            }
            if (content == null) {
                throw new IllegalArgumentException("content is required");
            }
        }
    }

    /**
     * Content redaction rules for restricted artifacts.
     */
    interface ContentRedactionRules {

        /**
         * Checks if content should be redacted based on data classification.
         *
         * @param dataClassification The data classification
         * @return true if content should be redacted
         */
        boolean shouldRedact(@Nullable String dataClassification);

        /**
         * Applies redaction to content.
         *
         * @param content The original content
         * @param dataClassification The data classification
         * @return Redacted content
         */
        @Nullable String applyRedaction(@NotNull String content, @Nullable String dataClassification);
    }
}
