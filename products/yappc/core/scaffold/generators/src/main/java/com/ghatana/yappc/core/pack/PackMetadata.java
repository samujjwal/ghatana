/*
 * Copyright (c) 2024 Ghatana, Inc.
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
package com.ghatana.yappc.core.pack;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Pack metadata schema - pack.json structure. Week 2, Day 7 deliverable - Base
 * pack structure definition.
 * 
 * Enhanced with universal composition system support for multi-module projects.
 * 
 * @doc.type record
 * @doc.purpose Pack metadata schema with universal composition support
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PackMetadata(
        @JsonProperty("name")
        String name,
        @JsonProperty("version")
        String version,
        @JsonProperty("description")
        String description,
        @JsonProperty("author")
        String author,
        @JsonProperty("license")
        String license,
        @JsonProperty("tags")
        List<String> tags,
        @JsonProperty("type")
        PackType type,
        @JsonProperty("language")
        String language,
        @JsonProperty("framework")
        String framework,
        @JsonProperty("buildSystem")
        String buildSystem,
        @JsonProperty("platform")
        String platform,
        @JsonProperty("archetype")
        String archetype,
        @JsonProperty("category")
        String category,
        @JsonProperty("supportedPacks")
        List<String> supportedPacks,
        @JsonProperty("dependencies")
        PackDependencies dependencies,
        @JsonProperty("templates")
        Map<String, TemplateFile> templates,
        @JsonProperty("hooks")
        PackHooks hooks,
        @JsonProperty("variables")
        Map<String, VariableSpec> variables,
        @JsonProperty("requirements")
        PackRequirements requirements,
        @JsonProperty("composition")
        PackComposition composition,
        @JsonProperty("modules")
        List<ModuleDefinition> modules,
        @JsonProperty("integrations")
        List<IntegrationDefinition> integrations) {

    /**
     * Pack type enumeration.
     *
     * @doc.type enum
     * @doc.purpose Pack metadata schema enumeration for pack types
     * @doc.layer platform
     * @doc.pattern Enumeration
     */
    public enum PackType {
        @JsonProperty("base")
        BASE,
        @JsonProperty("service")
        SERVICE,
        @JsonProperty("library")
        LIBRARY,
        @JsonProperty("application")
        APPLICATION,
        @JsonProperty("feature")
        FEATURE,
        @JsonProperty("fullstack")
        FULLSTACK,
        @JsonProperty("middleware")
        MIDDLEWARE
    }

    /**
     * Pack composition for fullstack/multi-module packs.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PackComposition(
            @JsonProperty("frontend") ComposedModule frontend,
            @JsonProperty("backend") ComposedModule backend,
            @JsonProperty("contracts") ComposedModule contracts,
            @JsonProperty("middleware") ComposedModule middleware) {

        public record ComposedModule(
                @JsonProperty("pack") String pack,
                @JsonProperty("path") String path) {}
    }

    /**
     * Pack dependencies specification.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PackDependencies(
            @JsonProperty("runtime") List<String> runtime,
            @JsonProperty("build") List<String> build,
            @JsonProperty("test") List<String> test,
            @JsonProperty("devDependencies") List<String> devDependencies,
            @JsonProperty("plugins") List<String> plugins) {
    }

    /**
     * Template file specification.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TemplateFile(
            @JsonProperty("source") String source,
            @JsonProperty("target") String target,
            @JsonProperty("condition") String condition,
            @JsonProperty("executable") Boolean executable,
            @JsonProperty("merge") MergeStrategy merge) {

        

    public enum MergeStrategy {
        @JsonProperty("replace")
        REPLACE,
        @JsonProperty("merge")
        MERGE,
        @JsonProperty("append")
        APPEND,
        @JsonProperty("skip-if-exists")
        SKIP_IF_EXISTS
    }
}

/**
 * Pack hooks specification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PackHooks(
        @JsonProperty("pre-generation")
        List<String> preGeneration,
        @JsonProperty("post-generation")
        List<String> postGeneration,
        @JsonProperty("pre-build")
        List<String> preBuild,
        @JsonProperty("post-build")
        List<String> postBuild) {

}

/**
 * Variable specification for pack templates.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VariableSpec(
        @JsonProperty("type")
        VariableType type,
        @JsonProperty("description")
        String description,
        @JsonProperty("default")
        Object defaultValue,
        @JsonProperty("required")
        Boolean required,
        @JsonProperty("options")
        List<String> options,
        @JsonProperty("validation")
        String validation) {

    public enum VariableType {
        @JsonProperty("string")
        STRING,
        @JsonProperty("number")
        NUMBER,
        @JsonProperty("boolean")
        BOOLEAN,
        @JsonProperty("array")
        ARRAY,
        @JsonProperty("object")
        OBJECT
    }
}

/**
 * Pack requirements specification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PackRequirements(
        @JsonProperty("java-version")
        String javaVersion,
        @JsonProperty("gradle-version")
        String gradleVersion,
        @JsonProperty("node-version")
        String nodeVersion,
        @JsonProperty("docker")
        Boolean docker,
        @JsonProperty("pnpm")
        Boolean pnpm,
        @JsonProperty("tools")
        List<String> tools) {

}

/**
 * Module definition for multi-module compositions.
 * 
 * @doc.type record
 * @doc.purpose Module definition for universal composition system
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModuleDefinition(
        @JsonProperty("id")
        String id,
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        ModuleType type,
        @JsonProperty("pack")
        String pack,
        @JsonProperty("path")
        String path,
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("condition")
        String condition,
        @JsonProperty("variables")
        Map<String, Object> variables,
        @JsonProperty("dependencies")
        List<String> dependencies,
        @JsonProperty("outputs")
        Map<String, String> outputs) {

    public enum ModuleType {
        @JsonProperty("application")
        APPLICATION,
        @JsonProperty("library")
        LIBRARY,
        @JsonProperty("service")
        SERVICE,
        @JsonProperty("infrastructure")
        INFRASTRUCTURE,
        @JsonProperty("tool")
        TOOL
    }
}

/**
 * Integration definition for cross-module integrations.
 * 
 * @doc.type record
 * @doc.purpose Integration definition for module communication
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntegrationDefinition(
        @JsonProperty("id")
        String id,
        @JsonProperty("name")
        String name,
        @JsonProperty("type")
        IntegrationType type,
        @JsonProperty("from")
        String from,
        @JsonProperty("to")
        String to,
        @JsonProperty("templates")
        List<String> templates,
        @JsonProperty("variables")
        Map<String, Object> variables,
        @JsonProperty("condition")
        String condition) {

    public enum IntegrationType {
        @JsonProperty("api-client")
        API_CLIENT,
        @JsonProperty("datasource")
        DATASOURCE,
        @JsonProperty("event-stream")
        EVENT_STREAM,
        @JsonProperty("shared-types")
        SHARED_TYPES,
        @JsonProperty("service-mesh")
        SERVICE_MESH
    }
}
}
