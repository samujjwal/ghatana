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

package com.ghatana.yappc.core.dotnet;

import io.activej.promise.Promise;

/**
 * Service interface for .NET project build generation.
 *
 * @doc.type interface
 * @doc.purpose Service interface for .NET .csproj generation
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface DotnetBuildGenerator {

    /**
     * Generates a .csproj file based on the provided specification.
     *
     * @param spec The .NET build specification
     * @return Promise containing the generated .NET project configuration
     */
    Promise<GeneratedDotnetProject> generateCsproj(DotnetBuildSpec spec);

    /**
     * Validates a .NET build specification.
     *
     * @param spec The .NET build specification to validate
     * @return Promise containing validation results
     */
    Promise<DotnetValidationResult> validateSpec(DotnetBuildSpec spec);

    /**
     * Suggests improvements for an existing .NET build specification.
     *
     * @param spec The current .NET build specification
     * @return Promise containing improvement suggestions
     */
    Promise<DotnetImprovementSuggestions> suggestImprovements(DotnetBuildSpec spec);

    /**
     * Generates .NET project scaffolding.
     *
     * @param spec The .NET build specification
     * @return Promise containing generated .NET source files
     */
    Promise<DotnetProjectScaffold> generateProjectScaffold(DotnetBuildSpec spec);

    /**
     * Analyzes existing .NET code and suggests configuration optimizations.
     *
     * @param projectPath Path to the .NET project directory
     * @return Promise containing analysis results
     */
    Promise<DotnetAnalysisResult> analyzeProject(String projectPath);
}
