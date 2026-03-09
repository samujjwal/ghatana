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
 * AI-powered improvement suggestions for CI/CD pipeline configurations.
 *
 * <p>Week 8 Day 37: CI/CD optimization recommendations.
 *
 * @doc.type record
 * @doc.purpose AI-powered improvement suggestions for CI/CD pipeline configurations.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record CIPipelineImprovementSuggestions(
        List<String> securityEnhancements,
        List<String> performanceOptimizations,
        List<String> reliabilityImprovements,
        List<String> costOptimizations,
        Map<String, String> actionUpgrades,
        List<String> bestPractices,
        List<String> complianceRecommendations,
        double improvementScore) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> securityEnhancements = List.of();
        private List<String> performanceOptimizations = List.of();
        private List<String> reliabilityImprovements = List.of();
        private List<String> costOptimizations = List.of();
        private Map<String, String> actionUpgrades = Map.of();
        private List<String> bestPractices = List.of();
        private List<String> complianceRecommendations = List.of();
        private double improvementScore = 0.0;

        public Builder securityEnhancements(List<String> securityEnhancements) {
            this.securityEnhancements = securityEnhancements;
            return this;
        }

        public Builder performanceOptimizations(List<String> performanceOptimizations) {
            this.performanceOptimizations = performanceOptimizations;
            return this;
        }

        public Builder reliabilityImprovements(List<String> reliabilityImprovements) {
            this.reliabilityImprovements = reliabilityImprovements;
            return this;
        }

        public Builder costOptimizations(List<String> costOptimizations) {
            this.costOptimizations = costOptimizations;
            return this;
        }

        public Builder actionUpgrades(Map<String, String> actionUpgrades) {
            this.actionUpgrades = actionUpgrades;
            return this;
        }

        public Builder bestPractices(List<String> bestPractices) {
            this.bestPractices = bestPractices;
            return this;
        }

        public Builder complianceRecommendations(List<String> complianceRecommendations) {
            this.complianceRecommendations = complianceRecommendations;
            return this;
        }

        public Builder improvementScore(double improvementScore) {
            this.improvementScore = improvementScore;
            return this;
        }

        public CIPipelineImprovementSuggestions build() {
            return new CIPipelineImprovementSuggestions(
                    securityEnhancements,
                    performanceOptimizations,
                    reliabilityImprovements,
                    costOptimizations,
                    actionUpgrades,
                    bestPractices,
                    complianceRecommendations,
                    improvementScore);
        }
    }
}
