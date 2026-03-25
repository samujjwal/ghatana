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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of generating a feature pack, containing all generated artifacts.
 *
 * <p>Week 7 Day 34: Generated feature pack with cross-build-system support.
 *
 * @doc.type record
 * @doc.purpose Result of generating a feature pack, containing all generated artifacts.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GeneratedFeaturePack(
        FeaturePackSpec spec,
        List<String> generatedFiles,
        Map<String, String> buildSystemConfigurations,
        Map<String, String> sourceFiles,
        Map<String, String> testFiles,
        Map<String, String> configurationFiles,
        List<String> requiredDependencies,
        List<String> recommendedDependencies,
        Map<String, Object> metadata,
        Instant generatedAt,
        String generatorVersion) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private FeaturePackSpec spec;
        private List<String> generatedFiles = List.of();
        private Map<String, String> buildSystemConfigurations = Map.of();
        private Map<String, String> sourceFiles = Map.of();
        private Map<String, String> testFiles = Map.of();
        private Map<String, String> configurationFiles = Map.of();
        private List<String> requiredDependencies = List.of();
        private List<String> recommendedDependencies = List.of();
        private Map<String, Object> metadata = Map.of();
        private Instant generatedAt = Instant.now();
        private String generatorVersion = "1.0.0";

        public Builder spec(FeaturePackSpec spec) {
            this.spec = spec;
            return this;
        }

        public Builder generatedFiles(List<String> generatedFiles) {
            this.generatedFiles = generatedFiles;
            return this;
        }

        public Builder buildSystemConfigurations(Map<String, String> buildSystemConfigurations) {
            this.buildSystemConfigurations = buildSystemConfigurations;
            return this;
        }

        public Builder sourceFiles(Map<String, String> sourceFiles) {
            this.sourceFiles = sourceFiles;
            return this;
        }

        public Builder testFiles(Map<String, String> testFiles) {
            this.testFiles = testFiles;
            return this;
        }

        public Builder configurationFiles(Map<String, String> configurationFiles) {
            this.configurationFiles = configurationFiles;
            return this;
        }

        public Builder requiredDependencies(List<String> requiredDependencies) {
            this.requiredDependencies = requiredDependencies;
            return this;
        }

        public Builder recommendedDependencies(List<String> recommendedDependencies) {
            this.recommendedDependencies = recommendedDependencies;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder generatorVersion(String generatorVersion) {
            this.generatorVersion = generatorVersion;
            return this;
        }

        public GeneratedFeaturePack build() {
            return new GeneratedFeaturePack(
                    spec,
                    generatedFiles,
                    buildSystemConfigurations,
                    sourceFiles,
                    testFiles,
                    configurationFiles,
                    requiredDependencies,
                    recommendedDependencies,
                    metadata,
                    generatedAt,
                    generatorVersion);
        }
    }
}
