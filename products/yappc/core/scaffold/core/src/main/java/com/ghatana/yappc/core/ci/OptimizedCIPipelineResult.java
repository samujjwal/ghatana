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

import java.util.Map;

/**
 * Result of AI-powered CI/CD pipeline optimization process. Contains original and optimized
 * pipelines with improvement metrics.
 *
 * <p>Week 8 Day 37: AI-optimized CI/CD pipeline generation result.
 *
 * @doc.type record
 * @doc.purpose Result of AI-powered CI/CD pipeline optimization process. Contains original and optimized
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record OptimizedCIPipelineResult(
        CIPipelineSpec originalSpec,
        CIPipelineSpec optimizedSpec,
        GeneratedCIPipeline originalPipeline,
        GeneratedCIPipeline optimizedPipeline,
        CIPipelineValidationResult validationResult,
        CIPipelineImprovementSuggestions improvementSuggestions,
        Map<String, Double> improvementMetrics,
        double optimizationScore) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CIPipelineSpec originalSpec;
        private CIPipelineSpec optimizedSpec;
        private GeneratedCIPipeline originalPipeline;
        private GeneratedCIPipeline optimizedPipeline;
        private CIPipelineValidationResult validationResult;
        private CIPipelineImprovementSuggestions improvementSuggestions;
        private Map<String, Double> improvementMetrics;
        private double optimizationScore;

        public Builder originalSpec(CIPipelineSpec originalSpec) {
            this.originalSpec = originalSpec;
            return this;
        }

        public Builder optimizedSpec(CIPipelineSpec optimizedSpec) {
            this.optimizedSpec = optimizedSpec;
            return this;
        }

        public Builder originalPipeline(GeneratedCIPipeline originalPipeline) {
            this.originalPipeline = originalPipeline;
            return this;
        }

        public Builder optimizedPipeline(GeneratedCIPipeline optimizedPipeline) {
            this.optimizedPipeline = optimizedPipeline;
            return this;
        }

        public Builder validationResult(CIPipelineValidationResult validationResult) {
            this.validationResult = validationResult;
            return this;
        }

        public Builder improvementSuggestions(
                CIPipelineImprovementSuggestions improvementSuggestions) {
            this.improvementSuggestions = improvementSuggestions;
            return this;
        }

        public Builder improvementMetrics(Map<String, Double> improvementMetrics) {
            this.improvementMetrics = improvementMetrics;
            return this;
        }

        public Builder optimizationScore(double optimizationScore) {
            this.optimizationScore = optimizationScore;
            return this;
        }

        public OptimizedCIPipelineResult build() {
            return new OptimizedCIPipelineResult(
                    originalSpec,
                    optimizedSpec,
                    originalPipeline,
                    optimizedPipeline,
                    validationResult,
                    improvementSuggestions,
                    improvementMetrics,
                    optimizationScore);
        }
    }
}
