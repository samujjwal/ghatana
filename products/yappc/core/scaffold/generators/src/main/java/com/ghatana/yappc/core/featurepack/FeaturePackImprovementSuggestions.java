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
 * AI-powered improvement suggestions for feature pack specifications.
 *
 * <p>Week 7 Day 34: Intelligent feature pack optimization recommendations.
 *
 * @doc.type record
 * @doc.purpose AI-powered improvement suggestions for feature pack specifications.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record FeaturePackImprovementSuggestions(
        List<String> dependencyOptimizations,
        List<String> configurationImprovements,
        List<String> securityEnhancements,
        List<String> performanceOptimizations,
        List<String> buildSystemOptimizations,
        Map<String, String> alternativeFeaturePacks,
        List<String> deprecationWarnings,
        double improvementScore) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> dependencyOptimizations = List.of();
        private List<String> configurationImprovements = List.of();
        private List<String> securityEnhancements = List.of();
        private List<String> performanceOptimizations = List.of();
        private List<String> buildSystemOptimizations = List.of();
        private Map<String, String> alternativeFeaturePacks = Map.of();
        private List<String> deprecationWarnings = List.of();
        private double improvementScore = 0.0;

        public Builder dependencyOptimizations(List<String> dependencyOptimizations) {
            this.dependencyOptimizations = dependencyOptimizations;
            return this;
        }

        public Builder configurationImprovements(List<String> configurationImprovements) {
            this.configurationImprovements = configurationImprovements;
            return this;
        }

        public Builder securityEnhancements(List<String> securityEnhancements) {
            this.securityEnhancements = securityEnhancements;
            return this;
        }

        public Builder performanceOptimizations(List<String> performanceOptimizations) {
            this.performanceOptimizations = performanceOptimizations;
            return this;
        }

        public Builder buildSystemOptimizations(List<String> buildSystemOptimizations) {
            this.buildSystemOptimizations = buildSystemOptimizations;
            return this;
        }

        public Builder alternativeFeaturePacks(Map<String, String> alternativeFeaturePacks) {
            this.alternativeFeaturePacks = alternativeFeaturePacks;
            return this;
        }

        public Builder deprecationWarnings(List<String> deprecationWarnings) {
            this.deprecationWarnings = deprecationWarnings;
            return this;
        }

        public Builder improvementScore(double improvementScore) {
            this.improvementScore = improvementScore;
            return this;
        }

        public FeaturePackImprovementSuggestions build() {
            return new FeaturePackImprovementSuggestions(
                    dependencyOptimizations,
                    configurationImprovements,
                    securityEnhancements,
                    performanceOptimizations,
                    buildSystemOptimizations,
                    alternativeFeaturePacks,
                    deprecationWarnings,
                    improvementScore);
        }
    }
}
