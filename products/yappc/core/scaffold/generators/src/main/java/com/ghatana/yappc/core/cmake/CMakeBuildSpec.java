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

package com.ghatana.yappc.core.cmake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Specification for CMake project build configuration.
 *
 * @doc.type record
 * @doc.purpose CMake project build specification
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record CMakeBuildSpec(
        @JsonProperty("projectName") String projectName,
        @JsonProperty("version") String version,
        @JsonProperty("description") String description,
        @JsonProperty("cmakeVersion") String cmakeVersion,
        @JsonProperty("cxxStandard") String cxxStandard,
        @JsonProperty("projectType") CMakeProjectType projectType,
        @JsonProperty("targets") List<CMakeTarget> targets,
        @JsonProperty("dependencies") List<String> dependencies,
        @JsonProperty("options") Map<String, String> options,
        @JsonProperty("features") CMakeFeatures features) {

    public enum CMakeProjectType {
        @JsonProperty("executable")
        EXECUTABLE,
        @JsonProperty("library")
        LIBRARY,
        @JsonProperty("header_only")
        HEADER_ONLY
    }

    public record CMakeTarget(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("sources") List<String> sources,
            @JsonProperty("headers") List<String> headers,
            @JsonProperty("linkLibraries") List<String> linkLibraries) {

        @JsonCreator
        public CMakeTarget {
        }
    }

    public record CMakeFeatures(
            @JsonProperty("enableTesting") boolean enableTesting,
            @JsonProperty("enableDocs") boolean enableDocs,
            @JsonProperty("enableWarnings") boolean enableWarnings,
            @JsonProperty("warningsAsErrors") boolean warningsAsErrors,
            @JsonProperty("enableSanitizers") boolean enableSanitizers) {

        @JsonCreator
        public CMakeFeatures {
        }

        public static CMakeFeatures defaults() {
            return new CMakeFeatures(true, false, true, false, false);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String projectName;
        private String version = "0.1.0";
        private String description;
        private String cmakeVersion = "3.20";
        private String cxxStandard = "17";
        private CMakeProjectType projectType = CMakeProjectType.EXECUTABLE;
        private List<CMakeTarget> targets = List.of();
        private List<String> dependencies = List.of();
        private Map<String, String> options = Map.of();
        private CMakeFeatures features = CMakeFeatures.defaults();

        public Builder projectName(String projectName) {
            this.projectName = projectName;
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

        public Builder cmakeVersion(String cmakeVersion) {
            this.cmakeVersion = cmakeVersion;
            return this;
        }

        public Builder cxxStandard(String cxxStandard) {
            this.cxxStandard = cxxStandard;
            return this;
        }

        public Builder projectType(CMakeProjectType projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder targets(List<CMakeTarget> targets) {
            this.targets = targets;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder options(Map<String, String> options) {
            this.options = options;
            return this;
        }

        public Builder features(CMakeFeatures features) {
            this.features = features;
            return this;
        }

        public CMakeBuildSpec build() {
            if (projectName == null || projectName.isBlank()) {
                throw new IllegalStateException("Project name is required");
            }
            return new CMakeBuildSpec(
                    projectName,
                    version,
                    description,
                    cmakeVersion,
                    cxxStandard,
                    projectType,
                    targets,
                    dependencies,
                    options,
                    features);
        }
    }
}
