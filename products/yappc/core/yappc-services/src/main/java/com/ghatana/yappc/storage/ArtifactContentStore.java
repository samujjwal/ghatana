/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.storage;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Contract for storing generated content with full provenance and authorization.
 *
 * <p>This interface extends the basic ArtifactStore to include rich content metadata
 * such as content hash, size, MIME type, language, provenance, source evidence IDs,
 * generation run ID, and content retrieval authorization.
 *
 * <p>Implementations must ensure that content is stored with all required metadata
 * and that retrieval is authorized based on the content's provenance and access controls.
 *
 * @doc.type interface
 * @doc.purpose Generated content storage with full provenance and authorization
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public interface ArtifactContentStore {

    /**
     * Content metadata record with full provenance information.
     */
    record ContentMetadata(
        String contentHash,
        long size,
        String mimeType,
        String language,
        Map<String, String> provenance,
        String generatedByRunId,
        String authorizationToken
    ) {}

    /**
     * Stores artifact content with full metadata.
     *
     * @param artifactId The artifact ID
     * @param content The raw content bytes
     * @param metadata The content metadata including hash, size, MIME, provenance, etc.
     * @return Promise completing when content is stored
     */
    Promise<Void> putContent(String artifactId, byte[] content, ContentMetadata metadata);

    /**
     * Retrieves artifact content by ID with authorization check.
     *
     * @param artifactId The artifact ID
     * @param authorizationToken The authorization token for access
     * @return Promise of raw content bytes
     */
    Promise<byte[]> getContent(String artifactId, String authorizationToken);

    /**
     * Retrieves content metadata without loading the full content.
     *
     * @param artifactId The artifact ID
     * @param authorizationToken The authorization token for access
     * @return Promise of content metadata
     */
    Promise<ContentMetadata> getContentMetadata(String artifactId, String authorizationToken);

    /**
     * Deletes artifact content by ID.
     *
     * @param artifactId The artifact ID
     * @param authorizationToken The authorization token for deletion
     * @return Promise completing when content is deleted
     */
    Promise<Void> deleteContent(String artifactId, String authorizationToken);

    /**
     * Checks if content exists for the given artifact ID.
     *
     * @param artifactId The artifact ID
     * @return Promise of existence status
     */
    Promise<Boolean> contentExists(String artifactId);
}
