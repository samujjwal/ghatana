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

/**
 * Validation result for CI/CD pipeline specifications.
 *
 * <p>Week 8 Day 37: CI/CD validation with security and best practice checks.
 *
 * @doc.type record
 * @doc.purpose Validation result for CI/CD pipeline specifications.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record CIPipelineValidationResult(
        boolean isValid,
        List<String> errors,
        List<String> warnings,
        List<String> securityIssues,
        List<String> performanceIssues,
        List<String> bestPracticeViolations,
        double securityScore,
        double qualityScore) {

    public static Builder builder() {
        return new Builder();
    }

    public static CIPipelineValidationResult valid() {
        return builder().isValid(true).securityScore(1.0).qualityScore(1.0).build();
    }

    public static CIPipelineValidationResult invalid(List<String> errors) {
        return builder().isValid(false).errors(errors).securityScore(0.0).qualityScore(0.0).build();
    }

    public static class Builder {
        private boolean isValid = true;
        private List<String> errors = List.of();
        private List<String> warnings = List.of();
        private List<String> securityIssues = List.of();
        private List<String> performanceIssues = List.of();
        private List<String> bestPracticeViolations = List.of();
        private double securityScore = 1.0;
        private double qualityScore = 1.0;

        public Builder isValid(boolean isValid) {
            this.isValid = isValid;
            return this;
        }

        public Builder errors(List<String> errors) {
            this.errors = errors;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder securityIssues(List<String> securityIssues) {
            this.securityIssues = securityIssues;
            return this;
        }

        public Builder performanceIssues(List<String> performanceIssues) {
            this.performanceIssues = performanceIssues;
            return this;
        }

        public Builder bestPracticeViolations(List<String> bestPracticeViolations) {
            this.bestPracticeViolations = bestPracticeViolations;
            return this;
        }

        public Builder securityScore(double securityScore) {
            this.securityScore = securityScore;
            return this;
        }

        public Builder qualityScore(double qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }

        public CIPipelineValidationResult build() {
            return new CIPipelineValidationResult(
                    isValid,
                    errors,
                    warnings,
                    securityIssues,
                    performanceIssues,
                    bestPracticeViolations,
                    securityScore,
                    qualityScore);
        }
    }
}
