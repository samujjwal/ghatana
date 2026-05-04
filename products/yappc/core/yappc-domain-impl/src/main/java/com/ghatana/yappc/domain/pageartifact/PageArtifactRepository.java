/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.domain.pageartifact;

import io.activej.promise.Promise;

/**
 * Repository interface for page artifact persistence.
 * <p>
 * Handles CRUD operations for PageArtifactDocument with tenant/workspace/project scoping
 * and optimistic concurrency control.
 *
 * @doc.type interface
 * @doc.purpose Repository interface for page artifact persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface PageArtifactRepository {

    /**
     * Saves a page artifact document.
     * <p>
     * Uses optimistic concurrency via the documentId field as ETag.
     * Throws PageArtifactConflictException if the documentId doesn't match the current version.
     *
     * @param tenantId The tenant ID for scoping
     * @param workspaceId The workspace ID for scoping
     * @param projectId The project ID for scoping
     * @param document The document to save
     * @return Promise that completes with the persisted document, including the
     *         effective documentId/version written to storage
     * @throws PageArtifactConflictException if there's a version conflict
     */
    Promise<PageArtifactDocument> save(
            String tenantId,
            String workspaceId,
            String projectId,
            PageArtifactDocument document
    );

    /**
     * Loads a page artifact document by artifact ID.
     *
     * @param tenantId The tenant ID for scoping
     * @param workspaceId The workspace ID for scoping
     * @param projectId The project ID for scoping
     * @param artifactId The artifact ID to load
     * @return Promise that completes with the document, or null if not found
     */
    Promise<PageArtifactDocument> load(
            String tenantId,
            String workspaceId,
            String projectId,
            String artifactId
    );

    /**
     * Deletes a page artifact document.
     *
     * @param tenantId The tenant ID for scoping
     * @param workspaceId The workspace ID for scoping
     * @param projectId The project ID for scoping
     * @param artifactId The artifact ID to delete
     * @return Promise that completes when deleted
     */
    Promise<Void> delete(
            String tenantId,
            String workspaceId,
            String projectId,
            String artifactId
    );
}
