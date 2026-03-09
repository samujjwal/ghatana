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

package com.ghatana.yappc.core.dotnet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Specification for .NET project build configuration.
 *
 * @doc.type record
 * @doc.purpose .NET project build specification for .csproj generation
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record DotnetBuildSpec(
        @JsonProperty("projectName") String projectName,
        @JsonProperty("version") String version,
        @JsonProperty("description") String description,
        @JsonProperty("authors") List<String> authors,
        @JsonProperty("targetFramework") String targetFramework,
        @JsonProperty("projectType") DotnetProjectType projectType,
        @JsonProperty("outputType") DotnetOutputType outputType,
        @JsonProperty("dependencies") List<DotnetDependency> dependencies,
        @JsonProperty("projectReferences") List<String> projectReferences,
        @JsonProperty("properties") Map<String, String> properties,
        @JsonProperty("features") DotnetFeatures features) {

    /**
     * .NET project type enumeration.
     */
    public enum DotnetProjectType {
        @JsonProperty("console")
        CONSOLE,
        @JsonProperty("web")
        WEB,
        @JsonProperty("api")
        API,
        @JsonProperty("library")
        LIBRARY,
        @JsonProperty("worker")
        WORKER,
        @JsonProperty("test")
        TEST
    }

    /**
     * .NET output type enumeration.
     */
    public enum DotnetOutputType {
        @JsonProperty("exe")
        EXE,
        @JsonProperty("library")
        LIBRARY,
        @JsonProperty("winexe")
        WINEXE
    }

    /**
     * .NET dependency specification.
     */
    public record DotnetDependency(
            @JsonProperty("packageId") String packageId,
            @JsonProperty("version") String version,
            @JsonProperty("includeAssets") String includeAssets,
            @JsonProperty("excludeAssets") String excludeAssets) {

        @JsonCreator
        public DotnetDependency {
        }

        public DotnetDependency(String packageId, String version) {
            this(packageId, version, null, null);
        }
    }

    /**
     * Optional .NET features configuration.
     */
    public record DotnetFeatures(
            @JsonProperty("enableNullable") boolean enableNullable,
            @JsonProperty("enableImplicitUsings") boolean enableImplicitUsings,
            @JsonProperty("treatWarningsAsErrors") boolean treatWarningsAsErrors,
            @JsonProperty("generateDocumentation") boolean generateDocumentation,
            @JsonProperty("enableAnalyzers") boolean enableAnalyzers,
            @JsonProperty("publishTrimmed") boolean publishTrimmed,
            @JsonProperty("publishSingleFile") boolean publishSingleFile) {

        @JsonCreator
        public DotnetFeatures {
        }

        public static DotnetFeatures defaults() {
            return new DotnetFeatures(
                    true, // nullable
                    true, // implicit usings
                    false, // warnings as errors
                    true, // documentation
                    true, // analyzers
                    false, // trimmed
                    false); // single file
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String projectName;
        private String version = "1.0.0";
        private String description;
        private List<String> authors = List.of();
        private String targetFramework = "net8.0";
        private DotnetProjectType projectType = DotnetProjectType.CONSOLE;
        private DotnetOutputType outputType = DotnetOutputType.EXE;
        private List<DotnetDependency> dependencies = List.of();
        private List<String> projectReferences = List.of();
        private Map<String, String> properties = Map.of();
        private DotnetFeatures features = DotnetFeatures.defaults();

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

        public Builder targetFramework(String targetFramework) {
            this.targetFramework = targetFramework;
            return this;
        }

        public Builder projectType(DotnetProjectType projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder outputType(DotnetOutputType outputType) {
            this.outputType = outputType;
            return this;
        }

        public Builder dependencies(List<DotnetDependency> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder projectReferences(List<String> projectReferences) {
            this.projectReferences = projectReferences;
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        public Builder features(DotnetFeatures features) {
            this.features = features;
            return this;
        }

        public DotnetBuildSpec build() {
            if (projectName == null || projectName.isBlank()) {
                throw new IllegalStateException("Project name is required");
            }
            return new DotnetBuildSpec(
                    projectName,
                    version,
                    description,
                    authors,
                    targetFramework,
                    projectType,
                    outputType,
                    dependencies,
                    projectReferences,
                    properties,
                    features);
        }
    }
}
