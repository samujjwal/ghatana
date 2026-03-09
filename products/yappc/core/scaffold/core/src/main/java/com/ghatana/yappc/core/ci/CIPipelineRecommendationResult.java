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

import java.util.List;
import java.util.Map;

/**
 * Result of analyzing a project to recommend appropriate CI/CD configuration.
 *
 * <p>Week 8 Day 37: AI-powered CI/CD recommendations based on project analysis.
 *
 * @doc.type record
 * @doc.purpose Result of analyzing a project to recommend appropriate CI/CD configuration.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record CIPipelineRecommendationResult(
        String projectType,
        List<String> detectedLanguages,
        List<String> detectedFrameworks,
        CIPipelineSpec.CIPlatform recommendedPlatform,
        List<CIPipelineRecommendation> recommendations,
        Map<String, Double> platformScores,
        List<String> analysisWarnings,
        double confidenceScore) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String projectType;
        private List<String> detectedLanguages = List.of();
        private List<String> detectedFrameworks = List.of();
        private CIPipelineSpec.CIPlatform recommendedPlatform;
        private List<CIPipelineRecommendation> recommendations = List.of();
        private Map<String, Double> platformScores = Map.of();
        private List<String> analysisWarnings = List.of();
        private double confidenceScore = 0.0;

        public Builder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder detectedLanguages(List<String> detectedLanguages) {
            this.detectedLanguages = detectedLanguages;
            return this;
        }

        public Builder detectedFrameworks(List<String> detectedFrameworks) {
            this.detectedFrameworks = detectedFrameworks;
            return this;
        }

        public Builder recommendedPlatform(CIPipelineSpec.CIPlatform recommendedPlatform) {
            this.recommendedPlatform = recommendedPlatform;
            return this;
        }

        public Builder recommendations(List<CIPipelineRecommendation> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public Builder platformScores(Map<String, Double> platformScores) {
            this.platformScores = platformScores;
            return this;
        }

        public Builder analysisWarnings(List<String> analysisWarnings) {
            this.analysisWarnings = analysisWarnings;
            return this;
        }

        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public CIPipelineRecommendationResult build() {
            return new CIPipelineRecommendationResult(
                    projectType,
                    detectedLanguages,
                    detectedFrameworks,
                    recommendedPlatform,
                    recommendations,
                    platformScores,
                    analysisWarnings,
                    confidenceScore);
        }
    }

    /**
 * Individual CI/CD pipeline recommendation. */
    public static record CIPipelineRecommendation(
            String name,
            String description,
            String rationale,
            double recommendationScore,
            List<String> benefits,
            List<String> considerations) {}
}
