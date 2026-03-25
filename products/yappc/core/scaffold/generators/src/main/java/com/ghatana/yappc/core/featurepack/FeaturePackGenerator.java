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

package com.ghatana.yappc.core.featurepack;

/**
 * Service interface for generating and applying feature packs across different build systems.
 * Provides cross-language abstractions for common development patterns.
 *
 * <p>Week 7 Day 34: Cross-build-system feature pack generation and application.
 *
 * @doc.type interface
 * @doc.purpose Service interface for generating and applying feature packs across different build systems.
 * @doc.layer platform
 * @doc.pattern Generator
 */
public interface FeaturePackGenerator {

    /**
     * Generates a feature pack that can be applied to projects across multiple build systems.
     *
     * @param spec The feature pack specification
     * @return The generated feature pack result
     */
    GeneratedFeaturePack generateFeaturePack(FeaturePackSpec spec);

    /**
     * Validates a feature pack specification for correctness and compatibility.
     *
     * @param spec The feature pack specification to validate
     * @return Validation result with any issues or suggestions
     */
    FeaturePackValidationResult validateFeaturePack(FeaturePackSpec spec);

    /**
     * Suggests improvements for a feature pack specification.
     *
     * @param spec The feature pack specification
     * @return Improvement suggestions powered by AI analysis
     */
    FeaturePackImprovementSuggestions suggestImprovements(FeaturePackSpec spec);

    /**
     * Analyzes existing project structure to recommend compatible feature packs.
     *
     * @param projectPath The path to the project
     * @return Analysis result with recommended feature packs
     */
    FeaturePackRecommendationResult recommendFeaturePacks(String projectPath);
}
