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

package com.ghatana.yappc.core.cmake;

import io.activej.promise.Promise;

/**
 * Service interface for CMake project build generation.
 *
 * @doc.type interface
 * @doc.purpose Service interface for CMakeLists.txt generation
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface CMakeBuildGenerator {

    /**
     * Generates CMakeLists.txt based on the provided specification.
     *
     * @param spec The CMake build specification
     * @return Promise containing the generated CMake project
     */
    Promise<GeneratedCMakeProject> generateCMakeLists(CMakeBuildSpec spec);

    /**
     * Validates a CMake build specification.
     *
     * @param spec The CMake build specification to validate
     * @return Promise containing validation results
     */
    Promise<CMakeValidationResult> validateSpec(CMakeBuildSpec spec);

    /**
     * Suggests improvements for an existing CMake build specification.
     *
     * @param spec The current CMake build specification
     * @return Promise containing improvement suggestions
     */
    Promise<CMakeImprovementSuggestions> suggestImprovements(CMakeBuildSpec spec);

    /**
     * Generates CMake project scaffolding.
     *
     * @param spec The CMake build specification
     * @return Promise containing generated C/C++ source files
     */
    Promise<CMakeProjectScaffold> generateProjectScaffold(CMakeBuildSpec spec);

    /**
     * Analyzes existing CMake code and suggests configuration optimizations.
     *
     * @param projectPath Path to the CMake project directory
     * @return Promise containing analysis results
     */
    Promise<CMakeAnalysisResult> analyzeProject(String projectPath);
}
