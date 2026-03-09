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
 * Specification for multi-language, multi-repository workspace orchestration. Supports coordinated
 * development across Rust, Java, TypeScript, and C++ projects.
 *
 * <p>Week 7 Day 35: E2E multi-repo orchestration with Rust+Java+TS integration.
 *
 * @doc.type record
 * @doc.purpose Specification for multi-language, multi-repository workspace orchestration. Supports coordinated
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record MultiRepoWorkspaceSpec(
        String workspaceName,
        String version,
        String description,
        List<MultiRepoServiceSpec> services,
        Map<String, String> sharedDependencies,
        List<String> communicationPatterns,
        Map<String, Object> orchestrationConfig,
        List<String> crossServiceContracts,
        Map<String, String> environmentVariables,
        boolean monorepoMode) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String workspaceName;
        private String version = "1.0.0";
        private String description;
        private List<MultiRepoServiceSpec> services = List.of();
        private Map<String, String> sharedDependencies = Map.of();
        private List<String> communicationPatterns = List.of();
        private Map<String, Object> orchestrationConfig = Map.of();
        private List<String> crossServiceContracts = List.of();
        private Map<String, String> environmentVariables = Map.of();
        private boolean monorepoMode = false;

        public Builder workspaceName(String workspaceName) {
            this.workspaceName = workspaceName;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder services(List<MultiRepoServiceSpec> services) {
            this.services = services;
            return this;
        }

        public Builder sharedDependencies(Map<String, String> sharedDependencies) {
            this.sharedDependencies = sharedDependencies;
            return this;
        }

        public Builder communicationPatterns(List<String> communicationPatterns) {
            this.communicationPatterns = communicationPatterns;
            return this;
        }

        public Builder orchestrationConfig(Map<String, Object> orchestrationConfig) {
            this.orchestrationConfig = orchestrationConfig;
            return this;
        }

        public Builder crossServiceContracts(List<String> crossServiceContracts) {
            this.crossServiceContracts = crossServiceContracts;
            return this;
        }

        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables;
            return this;
        }

        public Builder monorepoMode(boolean monorepoMode) {
            this.monorepoMode = monorepoMode;
            return this;
        }

        public MultiRepoWorkspaceSpec build() {
            return new MultiRepoWorkspaceSpec(
                    workspaceName,
                    version,
                    description,
                    services,
                    sharedDependencies,
                    communicationPatterns,
                    orchestrationConfig,
                    crossServiceContracts,
                    environmentVariables,
                    monorepoMode);
        }
    }

    /**
 * Individual service specification within the multi-repo workspace. */
    public static record MultiRepoServiceSpec(
            String serviceName,
            String language,
            String buildSystem,
            String serviceType,
            List<String> dependencies,
            List<String> crossServiceDependencies,
            Map<String, String> configuration,
            List<String> apiContracts,
            int port) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String serviceName;
            private String language;
            private String buildSystem;
            private String serviceType;
            private List<String> dependencies = List.of();
            private List<String> crossServiceDependencies = List.of();
            private Map<String, String> configuration = Map.of();
            private List<String> apiContracts = List.of();
            private int port = 8080;

            public Builder serviceName(String serviceName) {
                this.serviceName = serviceName;
                return this;
            }

            public Builder language(String language) {
                this.language = language;
                return this;
            }

            public Builder buildSystem(String buildSystem) {
                this.buildSystem = buildSystem;
                return this;
            }

            public Builder serviceType(String serviceType) {
                this.serviceType = serviceType;
                return this;
            }

            public Builder dependencies(List<String> dependencies) {
                this.dependencies = dependencies;
                return this;
            }

            public Builder crossServiceDependencies(List<String> crossServiceDependencies) {
                this.crossServiceDependencies = crossServiceDependencies;
                return this;
            }

            public Builder configuration(Map<String, String> configuration) {
                this.configuration = configuration;
                return this;
            }

            public Builder apiContracts(List<String> apiContracts) {
                this.apiContracts = apiContracts;
                return this;
            }

            public Builder port(int port) {
                this.port = port;
                return this;
            }

            public MultiRepoServiceSpec build() {
                return new MultiRepoServiceSpec(
                        serviceName,
                        language,
                        buildSystem,
                        serviceType,
                        dependencies,
                        crossServiceDependencies,
                        configuration,
                        apiContracts,
                        port);
            }
        }
    }
}
