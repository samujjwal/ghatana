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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of generating a complete multi-repository workspace. Contains all generated services,
 * configurations, and cross-service integrations.
 *
 * <p>Week 7 Day 35: Generated multi-repo workspace with cross-language coordination.
 *
 * @doc.type record
 * @doc.purpose Result of generating a complete multi-repository workspace. Contains all generated services,
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GeneratedMultiRepoWorkspace(
        MultiRepoWorkspaceSpec spec,
        List<String> generatedRepositories,
        Map<String, String> serviceConfigurations,
        Map<String, String> buildConfigurations,
        Map<String, String> communicationConfigurations,
        List<String> crossServiceContracts,
        Map<String, String> dockerConfigurations,
        Map<String, String> ciCdConfigurations,
        Map<String, Object> orchestrationMetadata,
        Instant generatedAt,
        String generatorVersion) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MultiRepoWorkspaceSpec spec;
        private List<String> generatedRepositories = List.of();
        private Map<String, String> serviceConfigurations = Map.of();
        private Map<String, String> buildConfigurations = Map.of();
        private Map<String, String> communicationConfigurations = Map.of();
        private List<String> crossServiceContracts = List.of();
        private Map<String, String> dockerConfigurations = Map.of();
        private Map<String, String> ciCdConfigurations = Map.of();
        private Map<String, Object> orchestrationMetadata = Map.of();
        private Instant generatedAt = Instant.now();
        private String generatorVersion = "1.0.0";

        public Builder spec(MultiRepoWorkspaceSpec spec) {
            this.spec = spec;
            return this;
        }

        public Builder generatedRepositories(List<String> generatedRepositories) {
            this.generatedRepositories = generatedRepositories;
            return this;
        }

        public Builder serviceConfigurations(Map<String, String> serviceConfigurations) {
            this.serviceConfigurations = serviceConfigurations;
            return this;
        }

        public Builder buildConfigurations(Map<String, String> buildConfigurations) {
            this.buildConfigurations = buildConfigurations;
            return this;
        }

        public Builder communicationConfigurations(
                Map<String, String> communicationConfigurations) {
            this.communicationConfigurations = communicationConfigurations;
            return this;
        }

        public Builder crossServiceContracts(List<String> crossServiceContracts) {
            this.crossServiceContracts = crossServiceContracts;
            return this;
        }

        public Builder dockerConfigurations(Map<String, String> dockerConfigurations) {
            this.dockerConfigurations = dockerConfigurations;
            return this;
        }

        public Builder ciCdConfigurations(Map<String, String> ciCdConfigurations) {
            this.ciCdConfigurations = ciCdConfigurations;
            return this;
        }

        public Builder orchestrationMetadata(Map<String, Object> orchestrationMetadata) {
            this.orchestrationMetadata = orchestrationMetadata;
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

        public GeneratedMultiRepoWorkspace build() {
            return new GeneratedMultiRepoWorkspace(
                    spec,
                    generatedRepositories,
                    serviceConfigurations,
                    buildConfigurations,
                    communicationConfigurations,
                    crossServiceContracts,
                    dockerConfigurations,
                    ciCdConfigurations,
                    orchestrationMetadata,
                    generatedAt,
                    generatorVersion);
        }
    }
}
