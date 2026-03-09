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

package com.ghatana.yappc.core.python;

import io.activej.promise.Promise;

/**
 * Service interface for Python project build generation.
 *
 * @doc.type interface
 * @doc.purpose Service interface for Python pyproject.toml generation
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface PythonBuildGenerator {

    /**
     * Generates a pyproject.toml file based on the provided specification.
     *
     * @param spec The Python build specification
     * @return Promise containing the generated Python project
     *         configuration
     */
    Promise<GeneratedPythonProject> generatePyprojectToml(PythonBuildSpec spec);

    /**
     * Validates a Python build specification.
     *
     * @param spec The Python build specification to validate
     * @return Promise containing validation results
     */
    Promise<PythonValidationResult> validateSpec(PythonBuildSpec spec);

    /**
     * Suggests improvements for an existing Python build specification.
     *
     * @param spec The current Python build specification
     * @return Promise containing improvement suggestions
     */
    Promise<PythonImprovementSuggestions> suggestImprovements(PythonBuildSpec spec);

    /**
     * Generates Python project scaffolding.
     *
     * @param spec The Python build specification
     * @return Promise containing generated Python source files
     */
    Promise<PythonProjectScaffold> generateProjectScaffold(PythonBuildSpec spec);

    /**
     * Analyzes existing Python code and suggests configuration optimizations.
     *
     * @param projectPath Path to the Python project directory
     * @return Promise containing analysis results
     */
    Promise<PythonAnalysisResult> analyzeProject(String projectPath);
}
