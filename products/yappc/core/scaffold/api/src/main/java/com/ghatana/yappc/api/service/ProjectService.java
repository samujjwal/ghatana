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

package com.ghatana.yappc.api.service;

import com.ghatana.yappc.api.model.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Service for project creation, management, and updates.
 *
 * @doc.type interface
 * @doc.purpose Project lifecycle operations
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ProjectService {

    /**
     * Create a new project from a pack.
     *
     * @param request The creation request
     * @return The creation result
     */
    CreateResult create(CreateRequest request);

    /**
     * Add a feature to an existing project.
     *
     * @param request The add feature request
     * @return The add result
     */
    AddResult addFeature(AddFeatureRequest request);

    /**
     * Update a project with pack changes.
     *
     * @param request The update request
     * @return The update result
     */
    UpdateResult update(UpdateRequest request);

    /**
     * Get project information.
     *
     * @param projectPath Path to the project
     * @return Project info if found
     */
    Optional<ProjectInfo> getInfo(Path projectPath);

    /**
     * Check if a path contains a YAPPC-managed project.
     *
     * @param projectPath Path to check
     * @return true if it's a YAPPC project
     */
    boolean isYappcProject(Path projectPath);

    /**
     * Get the project state.
     *
     * @param projectPath Path to the project
     * @return Project state if found
     */
    Optional<ProjectState> getState(Path projectPath);

    /**
     * List features available for a project.
     *
     * @param projectPath Path to the project
     * @return List of available features
     */
    List<FeatureInfo> getAvailableFeatures(Path projectPath);

    /**
     * List features already added to a project.
     *
     * @param projectPath Path to the project
     * @return List of added features
     */
    List<FeatureInfo> getAddedFeatures(Path projectPath);

    /**
     * Validate a project structure.
     *
     * @param projectPath Path to the project
     * @return Validation result
     */
    ProjectValidationResult validate(Path projectPath);

    /**
     * Check for available updates.
     *
     * @param projectPath Path to the project
     * @return Update availability info
     */
    UpdateAvailability checkUpdates(Path projectPath);

    /**
     * Preview update changes without applying them.
     *
     * @param request The update request
     * @return Preview of changes
     */
    UpdatePreview previewUpdate(UpdateRequest request);

    /**
     * Export project state for backup/migration.
     *
     * @param projectPath Path to the project
     * @return Exported state as JSON
     */
    String exportState(Path projectPath);

    /**
     * Import project state from backup.
     *
     * @param projectPath Path to the project
     * @param stateJson The state JSON to import
     */
    void importState(Path projectPath, String stateJson);
}
