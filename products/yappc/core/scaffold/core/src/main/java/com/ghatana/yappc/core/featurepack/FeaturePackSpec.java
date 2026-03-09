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
 * Specification for a feature pack that can be applied across multiple build systems. Feature packs
 * provide cross-language, cross-build-system abstractions for common patterns like database access,
 * API frameworks, observability, etc.
 *
 * <p>Week 7 Day 34: Feature packs for database and API integration across build systems.
 *
 * @doc.type record
 * @doc.purpose Specification for a feature pack that can be applied across multiple build systems. Feature packs
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record FeaturePackSpec(
        String name,
        String version,
        String description,
        FeaturePackType type,
        List<String> supportedBuildSystems,
        List<String> supportedLanguages,
        Map<String, String> dependencies,
        Map<String, String> devDependencies,
        List<String> requiredFeatures,
        List<String> optionalFeatures,
        Map<String, Object> configuration,
        List<String> templateFiles,
        List<String> configFiles,
        Map<String, String> environment) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String version;
        private String description;
        private FeaturePackType type;
        private List<String> supportedBuildSystems = List.of();
        private List<String> supportedLanguages = List.of();
        private Map<String, String> dependencies = Map.of();
        private Map<String, String> devDependencies = Map.of();
        private List<String> requiredFeatures = List.of();
        private List<String> optionalFeatures = List.of();
        private Map<String, Object> configuration = Map.of();
        private List<String> templateFiles = List.of();
        private List<String> configFiles = List.of();
        private Map<String, String> environment = Map.of();

        public Builder name(String name) {
            this.name = name;
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

        public Builder type(FeaturePackType type) {
            this.type = type;
            return this;
        }

        public Builder supportedBuildSystems(List<String> supportedBuildSystems) {
            this.supportedBuildSystems = supportedBuildSystems;
            return this;
        }

        public Builder supportedLanguages(List<String> supportedLanguages) {
            this.supportedLanguages = supportedLanguages;
            return this;
        }

        public Builder dependencies(Map<String, String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder devDependencies(Map<String, String> devDependencies) {
            this.devDependencies = devDependencies;
            return this;
        }

        public Builder requiredFeatures(List<String> requiredFeatures) {
            this.requiredFeatures = requiredFeatures;
            return this;
        }

        public Builder optionalFeatures(List<String> optionalFeatures) {
            this.optionalFeatures = optionalFeatures;
            return this;
        }

        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder templateFiles(List<String> templateFiles) {
            this.templateFiles = templateFiles;
            return this;
        }

        public Builder configFiles(List<String> configFiles) {
            this.configFiles = configFiles;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public FeaturePackSpec build() {
            return new FeaturePackSpec(
                    name,
                    version,
                    description,
                    type,
                    supportedBuildSystems,
                    supportedLanguages,
                    dependencies,
                    devDependencies,
                    requiredFeatures,
                    optionalFeatures,
                    configuration,
                    templateFiles,
                    configFiles,
                    environment);
        }
    }
}
