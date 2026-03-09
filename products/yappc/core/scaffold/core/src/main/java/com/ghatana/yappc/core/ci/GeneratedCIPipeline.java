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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of generating a complete CI/CD pipeline configuration.
 *
 * <p>Week 8 Day 37: Generated CI/CD pipeline with all configuration files.
 *
 * @doc.type record
 * @doc.purpose Result of generating a complete CI/CD pipeline configuration.
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GeneratedCIPipeline(
        CIPipelineSpec spec,
        Map<String, String> pipelineFiles,
        List<String> generatedSecrets,
        Map<String, String> environmentConfigurations,
        List<String> requiredActions,
        Map<String, String> dockerfiles,
        List<String> securityConfigurations,
        Map<String, Object> metadata,
        Instant generatedAt,
        String generatorVersion) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CIPipelineSpec spec;
        private Map<String, String> pipelineFiles = Map.of();
        private List<String> generatedSecrets = List.of();
        private Map<String, String> environmentConfigurations = Map.of();
        private List<String> requiredActions = List.of();
        private Map<String, String> dockerfiles = Map.of();
        private List<String> securityConfigurations = List.of();
        private Map<String, Object> metadata = Map.of();
        private Instant generatedAt = Instant.now();
        private String generatorVersion = "1.0.0";

        public Builder spec(CIPipelineSpec spec) {
            this.spec = spec;
            return this;
        }

        public Builder pipelineFiles(Map<String, String> pipelineFiles) {
            this.pipelineFiles = pipelineFiles;
            return this;
        }

        public Builder generatedSecrets(List<String> generatedSecrets) {
            this.generatedSecrets = generatedSecrets;
            return this;
        }

        public Builder environmentConfigurations(Map<String, String> environmentConfigurations) {
            this.environmentConfigurations = environmentConfigurations;
            return this;
        }

        public Builder requiredActions(List<String> requiredActions) {
            this.requiredActions = requiredActions;
            return this;
        }

        public Builder dockerfiles(Map<String, String> dockerfiles) {
            this.dockerfiles = dockerfiles;
            return this;
        }

        public Builder securityConfigurations(List<String> securityConfigurations) {
            this.securityConfigurations = securityConfigurations;
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

        public GeneratedCIPipeline build() {
            return new GeneratedCIPipeline(
                    spec,
                    pipelineFiles,
                    generatedSecrets,
                    environmentConfigurations,
                    requiredActions,
                    dockerfiles,
                    securityConfigurations,
                    metadata,
                    generatedAt,
                    generatorVersion);
        }
    }
}
