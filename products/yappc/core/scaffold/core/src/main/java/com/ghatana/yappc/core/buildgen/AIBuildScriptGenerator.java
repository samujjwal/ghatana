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

package com.ghatana.yappc.core.buildgen;

import com.ghatana.yappc.core.rca.RCAResult;
import java.util.List;
import io.activej.promise.Promise;

/**
 * Day 28: AI-powered build script generator interface. Generates optimized build scripts based on
 * project requirements and failure analysis.
 *
 * @doc.type interface
 * @doc.purpose Day 28: AI-powered build script generator interface. Generates optimized build scripts based on
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface AIBuildScriptGenerator {

    /**
     * Generate build script from specification
     *
     * @param spec Build script specification
     * @return Generated build script content
     */
    Promise<GeneratedBuildScript> generateBuildScript(BuildScriptSpec spec);

    /**
     * Generate build script with RCA-informed optimizations
     *
     * @param spec Build script specification
     * @param rcaResults Previous failure analysis results to avoid common issues
     * @return Optimized build script content
     */
    Promise<GeneratedBuildScript> generateBuildScript(
            BuildScriptSpec spec, List<RCAResult> rcaResults);

    /**
     * Suggest improvements for existing build script
     *
     * @param existingScript Current build script content
     * @param spec Target specification
     * @return Improvement suggestions and updated script
     */
    Promise<BuildScriptImprovement> suggestImprovements(
            String existingScript, BuildScriptSpec spec);

    /**
     * Validate generated build script for common issues
     *
     * @param script Generated build script content
     * @param spec Original specification
     * @return Validation results with warnings and errors
     */
    Promise<BuildScriptValidation> validateBuildScript(
            String script, BuildScriptSpec spec);

    /**
     * Check if the AI service is available
     *
     * @return true if service is operational
     */
    boolean isAvailable();

    /**
     * Get supported build tools
     *
     * @return List of supported build tools (gradle, maven, etc.)
     */
    List<String> getSupportedBuildTools();

    /**
     * Get supported project types
     *
     * @return List of supported project types (java, kotlin, scala, etc.)
     */
    List<String> getSupportedProjectTypes();
}
