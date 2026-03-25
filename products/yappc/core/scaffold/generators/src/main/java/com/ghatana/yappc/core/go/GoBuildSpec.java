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

package com.ghatana.yappc.core.go;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Specification for Go module/project build configuration.
 *
 * @doc.type record
 * @doc.purpose Go module build specification for go.mod generation
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GoBuildSpec(
        @JsonProperty("modulePath") String modulePath,
        @JsonProperty("goVersion") String goVersion,
        @JsonProperty("projectName") String projectName,
        @JsonProperty("description") String description,
        @JsonProperty("projectType") GoProjectType projectType,
        @JsonProperty("dependencies") List<GoDependency> dependencies,
        @JsonProperty("devDependencies") List<GoDependency> devDependencies,
        @JsonProperty("buildTags") List<String> buildTags,
        @JsonProperty("ldflags") Map<String, String> ldflags,
        @JsonProperty("targets") List<GoBuildTarget> targets,
        @JsonProperty("features") GoFeatures features) {

    /**
     * Go project type enumeration.
     */
    public enum GoProjectType {
        @JsonProperty("binary")
        BINARY,
        @JsonProperty("library")
        LIBRARY,
        @JsonProperty("service")
        SERVICE,
        @JsonProperty("cli")
        CLI,
        @JsonProperty("plugin")
        PLUGIN
    }

    /**
     * Go dependency specification.
     */
    public record GoDependency(
            @JsonProperty("path") String path,
            @JsonProperty("version") String version,
            @JsonProperty("indirect") boolean indirect) {
        
        @JsonCreator
        public GoDependency {}
        
        public GoDependency(String path, String version) {
            this(path, version, false);
        }
    }

    /**
     * Go build target for cross-compilation.
     */
    public record GoBuildTarget(
            @JsonProperty("os") String os,
            @JsonProperty("arch") String arch,
            @JsonProperty("outputName") String outputName) {
        
        @JsonCreator
        public GoBuildTarget {}
    }

    /**
     * Optional Go features configuration.
     */
    public record GoFeatures(
            @JsonProperty("enableCgo") boolean enableCgo,
            @JsonProperty("staticBuild") boolean staticBuild,
            @JsonProperty("raceDetector") boolean raceDetector,
            @JsonProperty("vendoring") boolean vendoring,
            @JsonProperty("workspaces") boolean workspaces) {
        
        @JsonCreator
        public GoFeatures {}
        
        public static GoFeatures defaults() {
            return new GoFeatures(false, false, false, false, false);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String modulePath;
        private String goVersion = "1.21";
        private String projectName;
        private String description;
        private GoProjectType projectType = GoProjectType.BINARY;
        private List<GoDependency> dependencies = List.of();
        private List<GoDependency> devDependencies = List.of();
        private List<String> buildTags = List.of();
        private Map<String, String> ldflags = Map.of();
        private List<GoBuildTarget> targets = List.of();
        private GoFeatures features = GoFeatures.defaults();

        public Builder modulePath(String modulePath) {
            this.modulePath = modulePath;
            return this;
        }

        public Builder goVersion(String goVersion) {
            this.goVersion = goVersion;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder projectType(GoProjectType projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder dependencies(List<GoDependency> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder devDependencies(List<GoDependency> devDependencies) {
            this.devDependencies = devDependencies;
            return this;
        }

        public Builder buildTags(List<String> buildTags) {
            this.buildTags = buildTags;
            return this;
        }

        public Builder ldflags(Map<String, String> ldflags) {
            this.ldflags = ldflags;
            return this;
        }

        public Builder targets(List<GoBuildTarget> targets) {
            this.targets = targets;
            return this;
        }

        public Builder features(GoFeatures features) {
            this.features = features;
            return this;
        }

        public GoBuildSpec build() {
            if (modulePath == null || modulePath.isBlank()) {
                throw new IllegalStateException("Module path is required");
            }
            return new GoBuildSpec(
                    modulePath,
                    goVersion,
                    projectName,
                    description,
                    projectType,
                    dependencies,
                    devDependencies,
                    buildTags,
                    ldflags,
                    targets,
                    features);
        }
    }
}
