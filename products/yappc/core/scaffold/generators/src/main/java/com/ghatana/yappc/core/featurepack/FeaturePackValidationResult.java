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

/**
 * Result of validating a feature pack specification.
 *
 * <p>Week 7 Day 34: Feature pack validation with cross-build-system compatibility checks.
 *
 * @doc.type record
 * @doc.purpose Result of validating a feature pack specification.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record FeaturePackValidationResult(
        boolean isValid,
        List<String> errors,
        List<String> warnings,
        List<String> compatibilityIssues,
        List<String> buildSystemIssues,
        List<String> dependencyConflicts,
        double compatibilityScore) {

    public static Builder builder() {
        return new Builder();
    }

    public static FeaturePackValidationResult valid() {
        return builder().isValid(true).compatibilityScore(1.0).build();
    }

    public static FeaturePackValidationResult invalid(List<String> errors) {
        return builder().isValid(false).errors(errors).compatibilityScore(0.0).build();
    }

    public static class Builder {
        private boolean isValid = true;
        private List<String> errors = List.of();
        private List<String> warnings = List.of();
        private List<String> compatibilityIssues = List.of();
        private List<String> buildSystemIssues = List.of();
        private List<String> dependencyConflicts = List.of();
        private double compatibilityScore = 1.0;

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

        public Builder compatibilityIssues(List<String> compatibilityIssues) {
            this.compatibilityIssues = compatibilityIssues;
            return this;
        }

        public Builder buildSystemIssues(List<String> buildSystemIssues) {
            this.buildSystemIssues = buildSystemIssues;
            return this;
        }

        public Builder dependencyConflicts(List<String> dependencyConflicts) {
            this.dependencyConflicts = dependencyConflicts;
            return this;
        }

        public Builder compatibilityScore(double compatibilityScore) {
            this.compatibilityScore = compatibilityScore;
            return this;
        }

        public FeaturePackValidationResult build() {
            return new FeaturePackValidationResult(
                    isValid,
                    errors,
                    warnings,
                    compatibilityIssues,
                    buildSystemIssues,
                    dependencyConflicts,
                    compatibilityScore);
        }
    }
}
