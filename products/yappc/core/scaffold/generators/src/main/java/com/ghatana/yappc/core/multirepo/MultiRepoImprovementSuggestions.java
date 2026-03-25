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

package com.ghatana.yappc.core.multirepo;

import java.util.List;
import java.util.Map;

/**
 * AI-powered improvement suggestions for multi-repository workspace architecture.
 *
 * <p>Week 7 Day 35: Multi-repo optimization recommendations.
 *
 * @doc.type record
 * @doc.purpose AI-powered improvement suggestions for multi-repository workspace architecture.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record MultiRepoImprovementSuggestions(
        List<String> architectureOptimizations,
        List<String> communicationImprovements,
        List<String> buildSystemOptimizations,
        List<String> deploymentImprovements,
        Map<String, String> serviceRefactoringSuggestions,
        List<String> securityEnhancements,
        List<String> performanceOptimizations,
        double improvementScore) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> architectureOptimizations = List.of();
        private List<String> communicationImprovements = List.of();
        private List<String> buildSystemOptimizations = List.of();
        private List<String> deploymentImprovements = List.of();
        private Map<String, String> serviceRefactoringSuggestions = Map.of();
        private List<String> securityEnhancements = List.of();
        private List<String> performanceOptimizations = List.of();
        private double improvementScore = 0.0;

        public Builder architectureOptimizations(List<String> architectureOptimizations) {
            this.architectureOptimizations = architectureOptimizations;
            return this;
        }

        public Builder communicationImprovements(List<String> communicationImprovements) {
            this.communicationImprovements = communicationImprovements;
            return this;
        }

        public Builder buildSystemOptimizations(List<String> buildSystemOptimizations) {
            this.buildSystemOptimizations = buildSystemOptimizations;
            return this;
        }

        public Builder deploymentImprovements(List<String> deploymentImprovements) {
            this.deploymentImprovements = deploymentImprovements;
            return this;
        }

        public Builder serviceRefactoringSuggestions(
                Map<String, String> serviceRefactoringSuggestions) {
            this.serviceRefactoringSuggestions = serviceRefactoringSuggestions;
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

        public Builder improvementScore(double improvementScore) {
            this.improvementScore = improvementScore;
            return this;
        }

        public MultiRepoImprovementSuggestions build() {
            return new MultiRepoImprovementSuggestions(
                    architectureOptimizations,
                    communicationImprovements,
                    buildSystemOptimizations,
                    deploymentImprovements,
                    serviceRefactoringSuggestions,
                    securityEnhancements,
                    performanceOptimizations,
                    improvementScore);
        }
    }
}
