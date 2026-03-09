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

package com.ghatana.yappc.core.python;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Specification for Python project build configuration.
 *
 * @doc.type record
 * @doc.purpose Python project build specification for pyproject.toml generation
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record PythonBuildSpec(
        @JsonProperty("projectName") String projectName,
        @JsonProperty("version") String version,
        @JsonProperty("description") String description,
        @JsonProperty("authors") List<String> authors,
        @JsonProperty("pythonVersion") String pythonVersion,
        @JsonProperty("projectType") PythonProjectType projectType,
        @JsonProperty("buildTool") PythonBuildTool buildTool,
        @JsonProperty("dependencies") List<PythonDependency> dependencies,
        @JsonProperty("devDependencies") List<PythonDependency> devDependencies,
        @JsonProperty("scripts") Map<String, String> scripts,
        @JsonProperty("entryPoints") Map<String, String> entryPoints,
        @JsonProperty("features") PythonFeatures features) {

    /**
     * Python project type enumeration.
     */
    public enum PythonProjectType {
        @JsonProperty("application")
        APPLICATION,
        @JsonProperty("library")
        LIBRARY,
        @JsonProperty("service")
        SERVICE,
        @JsonProperty("cli")
        CLI,
        @JsonProperty("package")
        PACKAGE
    }

    /**
     * Python build tool enumeration.
     */
    public enum PythonBuildTool {
        @JsonProperty("uv")
        UV,
        @JsonProperty("poetry")
        POETRY,
        @JsonProperty("pip")
        PIP,
        @JsonProperty("setuptools")
        SETUPTOOLS
    }

    /**
     * Python dependency specification.
     */
    public record PythonDependency(
            @JsonProperty("name") String name,
            @JsonProperty("version") String version,
            @JsonProperty("extras") List<String> extras,
            @JsonProperty("optional") boolean optional) {

        @JsonCreator
        public PythonDependency {
        }

        public PythonDependency(String name, String version) {
            this(name, version, List.of(), false);
        }

        public String toSpecifier() {
            StringBuilder spec = new StringBuilder(name);
            if (!extras.isEmpty()) {
                spec.append("[").append(String.join(",", extras)).append("]");
            }
            if (version != null && !version.isEmpty()) {
                spec.append(version);
            }
            return spec.toString();
        }
    }

    /**
     * Optional Python features configuration.
     */
    public record PythonFeatures(
            @JsonProperty("enableTypeChecking") boolean enableTypeChecking,
            @JsonProperty("enableLinting") boolean enableLinting,
            @JsonProperty("enableFormatting") boolean enableFormatting,
            @JsonProperty("enableTesting") boolean enableTesting,
            @JsonProperty("testFramework") String testFramework,
            @JsonProperty("linter") String linter,
            @JsonProperty("formatter") String formatter) {

        @JsonCreator
        public PythonFeatures {
        }

        public static PythonFeatures defaults() {
            return new PythonFeatures(
                    true, // type checking
                    true, // linting
                    true, // formatting
                    true, // testing
                    "pytest",
                    "ruff",
                    "ruff");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String projectName;
        private String version = "0.1.0";
        private String description;
        private List<String> authors = List.of();
        private String pythonVersion = ">=3.11";
        private PythonProjectType projectType = PythonProjectType.APPLICATION;
        private PythonBuildTool buildTool = PythonBuildTool.UV;
        private List<PythonDependency> dependencies = List.of();
        private List<PythonDependency> devDependencies = List.of();
        private Map<String, String> scripts = Map.of();
        private Map<String, String> entryPoints = Map.of();
        private PythonFeatures features = PythonFeatures.defaults();

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

        public Builder authors(List<String> authors) {
            this.authors = authors;
            return this;
        }

        public Builder pythonVersion(String pythonVersion) {
            this.pythonVersion = pythonVersion;
            return this;
        }

        public Builder projectType(PythonProjectType projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder buildTool(PythonBuildTool buildTool) {
            this.buildTool = buildTool;
            return this;
        }

        public Builder dependencies(List<PythonDependency> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder devDependencies(List<PythonDependency> devDependencies) {
            this.devDependencies = devDependencies;
            return this;
        }

        public Builder scripts(Map<String, String> scripts) {
            this.scripts = scripts;
            return this;
        }

        public Builder entryPoints(Map<String, String> entryPoints) {
            this.entryPoints = entryPoints;
            return this;
        }

        public Builder features(PythonFeatures features) {
            this.features = features;
            return this;
        }

        public PythonBuildSpec build() {
            if (projectName == null || projectName.isBlank()) {
                throw new IllegalStateException("Project name is required");
            }
            return new PythonBuildSpec(
                    projectName,
                    version,
                    description,
                    authors,
                    pythonVersion,
                    projectType,
                    buildTool,
                    dependencies,
                    devDependencies,
                    scripts,
                    entryPoints,
                    features);
        }
    }
}
