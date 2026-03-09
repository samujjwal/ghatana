/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
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

package com.ghatana.yappc.core.ci;

/**
 * Service interface for generating CI/CD pipeline configurations. Supports multiple platforms with
 * intelligent defaults and best practices.
 *
 * <p>Week 8 Day 37: CI/CD pipeline generation with security and quality gates.
 *
 * @doc.type interface
 * @doc.purpose Service interface for generating CI/CD pipeline configurations. Supports multiple platforms with
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface CIPipelineGenerator {

    /**
     * Generates a complete CI/CD pipeline configuration from specification.
     *
     * @param spec The CI/CD pipeline specification
     * @return Generated pipeline configuration with all required files
     */
    GeneratedCIPipeline generatePipeline(CIPipelineSpec spec);

    /**
     * Validates a CI/CD pipeline specification for correctness and security.
     *
     * @param spec The pipeline specification to validate
     * @return Validation result with security and best practice checks
     */
    CIPipelineValidationResult validatePipeline(CIPipelineSpec spec);

    /**
     * Suggests improvements for CI/CD pipeline configuration.
     *
     * @param spec The pipeline specification
     * @return AI-powered improvement suggestions for security and performance
     */
    CIPipelineImprovementSuggestions suggestImprovements(CIPipelineSpec spec);

    /**
     * Analyzes existing project to recommend appropriate CI/CD configuration.
     *
     * @param projectPath The path to the project
     * @return Analysis result with recommended CI/CD configuration
     */
    CIPipelineRecommendationResult recommendPipeline(String projectPath);
}
