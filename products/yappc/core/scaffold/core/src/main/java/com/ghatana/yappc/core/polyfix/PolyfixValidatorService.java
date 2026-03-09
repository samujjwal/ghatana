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

package com.ghatana.yappc.core.polyfix;

import java.util.List;
import io.activej.promise.Promise;

/**
 * Day 29: Polyfix codemod validator service interface. Validates generated build scripts and code
 * using Polyfix codemods.
 *
 * @doc.type interface
 * @doc.purpose Day 29: Polyfix codemod validator service interface. Validates generated build scripts and code
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface PolyfixValidatorService {

    /**
     * Validate generated code using appropriate Polyfix codemods
     *
     * @param targetPath Path to the project directory to validate
     * @param projectType Type of project (java, kotlin, typescript, etc.)
     * @return Validation results with applied codemods and issues found
     */
    Promise<PolyfixValidationResult> validateProject(
            String targetPath, String projectType);

    /**
     * Validate specific files using targeted codemods
     *
     * @param filePaths List of file paths to validate
     * @param projectType Type of project for appropriate codemod selection
     * @return Validation results for the specified files
     */
    Promise<PolyfixValidationResult> validateFiles(
            List<String> filePaths, String projectType);

    /**
     * Get available codemods for a specific project type
     *
     * @param projectType Type of project
     * @return List of available codemod names and descriptions
     */
    List<CodemodInfo> getAvailableCodemods(String projectType);

    /**
     * Run specific codemods on a project
     *
     * @param targetPath Path to the project directory
     * @param codemodNames List of specific codemod names to run
     * @return Validation results from running the specified codemods
     */
    Promise<PolyfixValidationResult> runSpecificCodemods(
            String targetPath, List<String> codemodNames);

    /**
     * Check if Polyfix is available and configured
     *
     * @return true if Polyfix can be used for validation
     */
    boolean isAvailable();

    /**
     * Get supported project types
     *
     * @return List of project types that can be validated
     */
    List<String> getSupportedProjectTypes();

    /**
 * Information about an available codemod */
    class CodemodInfo {
        private final String name;
        private final String version;
        private final String description;
        private final List<String> supportedFileTypes;
        private final String category;

        public CodemodInfo(
                String name,
                String version,
                String description,
                List<String> supportedFileTypes,
                String category) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.supportedFileTypes = supportedFileTypes;
            this.category = category;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getSupportedFileTypes() {
            return supportedFileTypes;
        }

        public String getCategory() {
            return category;
        }
    }
}
