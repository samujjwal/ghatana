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

import com.ghatana.yappc.api.model.DependencyInfo;
import com.ghatana.yappc.api.model.DependencyAnalysis;
import com.ghatana.yappc.api.model.ConflictInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * Service for dependency analysis and conflict detection.
 *
 * @doc.type interface
 * @doc.purpose Dependency management operations
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface DependencyService {

    /**
     * Analyze dependencies for a pack.
     *
     * @param packName The pack name
     * @return Dependency analysis results
     */
    DependencyAnalysis analyzePack(String packName);

    /**
     * Analyze dependencies for a project.
     *
     * @param projectPath Path to the project
     * @return Dependency analysis results
     */
    DependencyAnalysis analyzeProject(Path projectPath);

    /**
     * Get dependencies for a pack.
     *
     * @param packName The pack name
     * @return List of dependencies
     */
    List<DependencyInfo> getPackDependencies(String packName);

    /**
     * Get dependencies for a project.
     *
     * @param projectPath Path to the project
     * @return List of dependencies
     */
    List<DependencyInfo> getProjectDependencies(Path projectPath);

    /**
     * Check for dependency conflicts between packs.
     *
     * @param packNames List of pack names to check
     * @return List of conflicts
     */
    List<ConflictInfo> checkConflicts(List<String> packNames);

    /**
     * Check if adding a pack would cause conflicts.
     *
     * @param projectPath Path to the project
     * @param packName The pack to add
     * @return List of potential conflicts
     */
    List<ConflictInfo> checkAddConflicts(Path projectPath, String packName);

    /**
     * Get transitive dependencies for a pack.
     *
     * @param packName The pack name
     * @return Full dependency tree
     */
    List<DependencyInfo> getTransitiveDependencies(String packName);

    /**
     * Check for outdated dependencies in a project.
     *
     * @param projectPath Path to the project
     * @return List of outdated dependencies
     */
    List<DependencyInfo> findOutdated(Path projectPath);

    /**
     * Suggest dependency upgrades for a project.
     *
     * @param projectPath Path to the project
     * @return List of suggested upgrades
     */
    List<DependencyUpgrade> suggestUpgrades(Path projectPath);

    /**
     * Represents a suggested dependency upgrade.
     */
    record DependencyUpgrade(
            String name,
            String currentVersion,
            String suggestedVersion,
            String reason,
            boolean breaking
    ) {}
}
