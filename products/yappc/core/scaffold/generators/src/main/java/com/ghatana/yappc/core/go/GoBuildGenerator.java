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

package com.ghatana.yappc.core.go;

import io.activej.promise.Promise;

/**
 * Service interface for Go module build generation. Provides intelligent Go project
 * configuration with best practices and optimizations.
 *
 * @doc.type interface
 * @doc.purpose Service interface for Go module build generation
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface GoBuildGenerator {

    /**
     * Generates a go.mod file based on the provided specification.
     *
     * @param spec The Go build specification containing project details
     * @return Promise containing the generated Go project configuration
     */
    Promise<GeneratedGoProject> generateGoMod(GoBuildSpec spec);

    /**
     * Validates a Go build specification for completeness and best practices.
     *
     * @param spec The Go build specification to validate
     * @return Promise containing validation results and recommendations
     */
    Promise<GoValidationResult> validateSpec(GoBuildSpec spec);

    /**
     * Suggests improvements for an existing Go build specification.
     * Uses analysis to recommend dependency updates, feature optimizations, and best practices.
     *
     * @param spec The current Go build specification
     * @return Promise containing improvement suggestions
     */
    Promise<GoImprovementSuggestions> suggestImprovements(GoBuildSpec spec);

    /**
     * Generates Go project scaffolding including main.go, internal/, pkg/, and tests.
     *
     * @param spec The Go build specification
     * @return Promise containing generated Go source files
     */
    Promise<GoProjectScaffold> generateProjectScaffold(GoBuildSpec spec);

    /**
     * Analyzes existing Go code and suggests module configuration optimizations.
     *
     * @param projectPath Path to the Go project directory
     * @return Promise containing analysis results and suggestions
     */
    Promise<GoAnalysisResult> analyzeProject(String projectPath);
}
