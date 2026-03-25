/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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

package com.ghatana.yappc.core.plugin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Plugin metadata containing identification, capabilities, and requirements.
 *
 * @doc.type record
 * @doc.purpose Plugin metadata for discovery and validation
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record PluginMetadata(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("version") String version,
        @JsonProperty("description") String description,
        @JsonProperty("author") String author,
        @JsonProperty("capabilities") List<PluginCapability> capabilities,
        @JsonProperty("supportedLanguages") List<String> supportedLanguages,
        @JsonProperty("supportedBuildSystems") List<String> supportedBuildSystems,
        @JsonProperty("requiredConfig") Map<String, String> requiredConfig,
        @JsonProperty("optionalConfig") Map<String, String> optionalConfig,
        @JsonProperty("stability") StabilityLevel stability,
        @JsonProperty("dependencies") List<String> dependencies) {

    @JsonCreator
    public PluginMetadata {
    }

    /**
     * Plugin stability level.
     */
    public enum StabilityLevel {
        @JsonProperty("experimental")
        EXPERIMENTAL,
        @JsonProperty("beta")
        BETA,
        @JsonProperty("stable")
        STABLE,
        @JsonProperty("deprecated")
        DEPRECATED
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String version;
        private String description;
        private String author;
        private List<PluginCapability> capabilities = List.of();
        private List<String> supportedLanguages = List.of();
        private List<String> supportedBuildSystems = List.of();
        private Map<String, String> requiredConfig = Map.of();
        private Map<String, String> optionalConfig = Map.of();
        private StabilityLevel stability = StabilityLevel.STABLE;
        private List<String> dependencies = List.of();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

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

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder capabilities(List<PluginCapability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder supportedLanguages(List<String> supportedLanguages) {
            this.supportedLanguages = supportedLanguages;
            return this;
        }

        public Builder supportedBuildSystems(List<String> supportedBuildSystems) {
            this.supportedBuildSystems = supportedBuildSystems;
            return this;
        }

        public Builder requiredConfig(Map<String, String> requiredConfig) {
            this.requiredConfig = requiredConfig;
            return this;
        }

        public Builder optionalConfig(Map<String, String> optionalConfig) {
            this.optionalConfig = optionalConfig;
            return this;
        }

        public Builder stability(StabilityLevel stability) {
            this.stability = stability;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public PluginMetadata build() {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Plugin id is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Plugin name is required");
            }
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("Plugin version is required");
            }
            return new PluginMetadata(
                    id,
                    name,
                    version,
                    description,
                    author,
                    capabilities,
                    supportedLanguages,
                    supportedBuildSystems,
                    requiredConfig,
                    optionalConfig,
                    stability,
                    dependencies);
        }
    }
}
