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

import java.util.List;
import java.util.Map;

/**
 * Result of analyzing a project to recommend compatible feature packs.
 *
 * <p>Week 7 Day 34: AI-powered feature pack recommendations based on project analysis.
 *
 * @doc.type record
 * @doc.purpose Result of analyzing a project to recommend compatible feature packs.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record FeaturePackRecommendationResult(
        String projectType,
        List<String> detectedLanguages,
        List<String> detectedBuildSystems,
        List<FeaturePackRecommendation> recommendations,
        Map<String, Double> compatibilityScores,
        List<String> analysisWarnings,
        double confidenceScore) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String projectType;
        private List<String> detectedLanguages = List.of();
        private List<String> detectedBuildSystems = List.of();
        private List<FeaturePackRecommendation> recommendations = List.of();
        private Map<String, Double> compatibilityScores = Map.of();
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

        public Builder detectedBuildSystems(List<String> detectedBuildSystems) {
            this.detectedBuildSystems = detectedBuildSystems;
            return this;
        }

        public Builder recommendations(List<FeaturePackRecommendation> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public Builder compatibilityScores(Map<String, Double> compatibilityScores) {
            this.compatibilityScores = compatibilityScores;
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

        public FeaturePackRecommendationResult build() {
            return new FeaturePackRecommendationResult(
                    projectType,
                    detectedLanguages,
                    detectedBuildSystems,
                    recommendations,
                    compatibilityScores,
                    analysisWarnings,
                    confidenceScore);
        }
    }

    /**
 * Individual feature pack recommendation with rationale. */
    public static record FeaturePackRecommendation(
            String name,
            FeaturePackType type,
            String version,
            String rationale,
            double recommendationScore,
            List<String> benefits,
            List<String> considerations) {}
}
