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

package com.ghatana.yappc.core.plugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Plugin interface for build system providers.
 *
 * @doc.type interface
 * @doc.purpose Build system plugin
 * @doc.layer platform
 * @doc.pattern Plugin SPI
 */
public interface BuildSystemPlugin extends YappcPlugin {

    /**
     * Returns the build system identifier.
     *
     * @return build system ID (e.g., "gradle", "cargo", "pnpm")
     */
    String getBuildSystemId();

    /**
     * Returns the supported languages for this build system.
     *
     * @return list of supported language identifiers
     */
    List<String> getSupportedLanguages();

    /**
     * Generates build configuration for a project.
     *
     * @param projectPath path to project
     * @param spec        build specification
     * @return generated build file content
     * @throws PluginException if generation fails
     */
    String generateBuildConfig(Path projectPath, Object spec) throws PluginException;

    /**
     * Validates a build specification.
     *
     * @param spec build specification
     * @return validation result
     * @throws PluginException if validation fails
     */
    ValidationResult validateSpec(Object spec) throws PluginException;

    /**
     * Analyzes an existing project and suggests improvements.
     *
     * @param projectPath path to project
     * @return analysis result with suggestions
     * @throws PluginException if analysis fails
     */
    AnalysisResult analyzeProject(Path projectPath) throws PluginException;

    /**
     * Validation result.
     */
    record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, List.of());
        }
    }

    /**
     * Analysis result.
     */
    record AnalysisResult(
            List<String> suggestions,
            List<String> warnings,
            List<DependencyUpdate> updates) {
    }

    /**
     * Dependency update suggestion.
     */
    record DependencyUpdate(
            String name,
            String currentVersion,
            String suggestedVersion,
            boolean breaking) {
    }
}
