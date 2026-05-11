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
 * YAPPC-facing facade for Data Cloud project operations.
 *
 * Provides a typed interface for YAPPC to interact with Data Cloud
 * project management without direct dependencies on Data Cloud internals.
 *
 * @doc.type interface
 * @doc.purpose YAPPC facade for Data Cloud project operations
 * @doc.layer product
 * @doc.pattern Facade
 */
public interface DataCloudProjectFacade {

    /**
     * Create a project in Data Cloud.
     *
     * @param request The project creation request
     * @return Promise containing the project ID
     */
    Promise<String> createProject(ProjectCreationRequest request);

    /**
     * Retrieve a project from Data Cloud.
     *
     * @param projectId The project ID
     * @param tenantId The tenant ID
     * @return Promise containing the project details
     */
    Promise<Optional<ProjectDetails>> retrieveProject(String projectId, String tenantId);

    /**
     * List projects for a tenant.
     *
     * @param tenantId The tenant ID
     * @param limit Optional limit on number of results
     * @return Promise containing list of project metadata
     */
    Promise<List<ProjectMetadata>> listProjects(String tenantId, Optional<Integer> limit);

    /**
     * Update a project in Data Cloud.
     *
     * @param request The project update request
     * @return Promise indicating completion
     */
    Promise<Void> updateProject(ProjectUpdateRequest request);

    /**
     * Delete a project from Data Cloud.
     *
     * @param projectId The project ID
     * @param tenantId The tenant ID
     * @return Promise indicating completion
     */
    Promise<Void> deleteProject(String projectId, String tenantId);

    /**
     * Project creation request.
     */
    record ProjectCreationRequest(
        String name,
        String description,
        String tenantId,
        Map<String, String> metadata,
        Optional<String> workspaceId
    ) implements TenantScopedRequest {
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }

    /**
     * Project update request.
     */
    record ProjectUpdateRequest(
        String projectId,
        String tenantId,
        Optional<String> name,
        Optional<String> description,
        Map<String, String> metadata
    ) implements TenantScopedRequest {
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }

    /**
     * Project details.
     */
    record ProjectDetails(
        String projectId,
        String name,
        String description,
        String tenantId,
        Optional<String> workspaceId,
        long createdAt,
        long updatedAt,
        Map<String, String> metadata
    ) {}

    /**
     * Project metadata.
     */
    record ProjectMetadata(
        String projectId,
        String name,
        String description,
        long createdAt,
        long updatedAt
    ) {}
}
