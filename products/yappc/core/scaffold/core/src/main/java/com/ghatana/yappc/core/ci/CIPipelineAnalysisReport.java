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
 * Comprehensive analysis report for CI/CD pipeline cross-platform evaluation. Contains detailed
 * comparison and recommendations across multiple CI/CD platforms.
 *
 * <p>Week 8 Day 37: Multi-platform CI/CD analysis and comparison report.
 *
 * @doc.type record
 * @doc.purpose Comprehensive analysis report for CI/CD pipeline cross-platform evaluation. Contains detailed
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record CIPipelineAnalysisReport(
        CIPipelineSpec baseSpec,
        Map<CIPipelineSpec.CIPlatform, GeneratedCIPipeline> generatedPipelines,
        Map<CIPipelineSpec.CIPlatform, CIPipelineValidationResult> validationResults,
        Map<CIPipelineSpec.CIPlatform, CIPipelineImprovementSuggestions> improvementSuggestions,
        Map<String, Object> aggregateMetrics) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CIPipelineSpec baseSpec;
        private Map<CIPipelineSpec.CIPlatform, GeneratedCIPipeline> generatedPipelines;
        private Map<CIPipelineSpec.CIPlatform, CIPipelineValidationResult> validationResults;
        private Map<CIPipelineSpec.CIPlatform, CIPipelineImprovementSuggestions>
                improvementSuggestions;
        private Map<String, Object> aggregateMetrics;

        public Builder baseSpec(CIPipelineSpec baseSpec) {
            this.baseSpec = baseSpec;
            return this;
        }

        public Builder generatedPipelines(
                Map<CIPipelineSpec.CIPlatform, GeneratedCIPipeline> generatedPipelines) {
            this.generatedPipelines = generatedPipelines;
            return this;
        }

        public Builder validationResults(
                Map<CIPipelineSpec.CIPlatform, CIPipelineValidationResult> validationResults) {
            this.validationResults = validationResults;
            return this;
        }

        public Builder improvementSuggestions(
                Map<CIPipelineSpec.CIPlatform, CIPipelineImprovementSuggestions>
                        improvementSuggestions) {
            this.improvementSuggestions = improvementSuggestions;
            return this;
        }

        public Builder aggregateMetrics(Map<String, Object> aggregateMetrics) {
            this.aggregateMetrics = aggregateMetrics;
            return this;
        }

        public CIPipelineAnalysisReport build() {
            return new CIPipelineAnalysisReport(
                    baseSpec,
                    generatedPipelines,
                    validationResults,
                    improvementSuggestions,
                    aggregateMetrics);
        }
    }
}
