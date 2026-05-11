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

package com.ghatana.yappc.facades.datacloud;

import com.ghatana.yappc.facades.common.TenantScopedRequest;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YAPPC-facing facade for Data Cloud artifact storage operations.
 *
 * Provides a typed interface for YAPPC to interact with Data Cloud
 * artifact storage without direct dependencies on Data Cloud internals.
 *
 * @doc.type interface
 * @doc.purpose YAPPC facade for Data Cloud artifact storage
 * @doc.layer product
 * @doc.pattern Facade
 */
public interface DataCloudArtifactFacade {

    /**
     * Store an artifact in Data Cloud.
     *
     * @param request The artifact storage request
     * @return Promise containing the artifact ID
     */
    Promise<String> storeArtifact(ArtifactStorageRequest request);

    /**
     * Retrieve an artifact from Data Cloud.
     *
     * @param artifactId The artifact ID
     * @param tenantId The tenant ID
     * @return Promise containing the artifact content
     */
    Promise<Optional<ArtifactContent>> retrieveArtifact(String artifactId, String tenantId);

    /**
     * List artifacts for a project.
     *
     * @param projectId The project ID
     * @param tenantId The tenant ID
     * @return Promise containing list of artifact metadata
     */
    Promise<List<ArtifactMetadata>> listArtifacts(String projectId, String tenantId);

    /**
     * Delete an artifact from Data Cloud.
     *
     * @param artifactId The artifact ID
     * @param tenantId The tenant ID
     * @return Promise indicating completion
     */
    Promise<Void> deleteArtifact(String artifactId, String tenantId);

    /**
     * Check if an artifact exists.
     *
     * @param artifactId The artifact ID
     * @param tenantId The tenant ID
     * @return Promise containing existence status
     */
    Promise<Boolean> artifactExists(String artifactId, String tenantId);

    /**
     * Artifact storage request.
     */
    record ArtifactStorageRequest(
        String projectId,
        String tenantId,
        String artifactType,
        String content,
        Map<String, String> metadata,
        String version
    ) implements TenantScopedRequest {
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }

    /**
     * Artifact content.
     */
    record ArtifactContent(
        String artifactId,
        String content,
        String contentType,
        long size,
        Map<String, String> metadata
    ) {}

    /**
     * Artifact metadata.
     */
    record ArtifactMetadata(
        String artifactId,
        String projectId,
        String artifactType,
        String version,
        long size,
        long createdAt,
        Map<String, String> metadata
    ) {}
}
