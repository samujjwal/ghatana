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

package com.ghatana.yappc.core.composition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.yappc.core.pack.PackMetadata;
import java.util.List;

/**
 * Composition definition for multi-module projects.
 * 
 * @doc.type record
 * @doc.purpose Composition definition for universal multi-module projects
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompositionDefinition(
        @JsonProperty("version")
        String version,
        @JsonProperty("type")
        CompositionType type,
        @JsonProperty("metadata")
        CompositionMetadata metadata,
        @JsonProperty("modules")
        List<PackMetadata.ModuleDefinition> modules,
        @JsonProperty("integrations")
        List<PackMetadata.IntegrationDefinition> integrations,
        @JsonProperty("lifecycle")
        LifecycleHooks lifecycle) {

    public enum CompositionType {
        @JsonProperty("custom")
        CUSTOM,
        @JsonProperty("template")
        TEMPLATE,
        @JsonProperty("preset")
        PRESET
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CompositionMetadata(
            @JsonProperty("name")
            String name,
            @JsonProperty("description")
            String description,
            @JsonProperty("tags")
            List<String> tags,
            @JsonProperty("author")
            String author,
            @JsonProperty("license")
            String license) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LifecycleHooks(
            @JsonProperty("pre-generation")
            List<String> preGeneration,
            @JsonProperty("post-generation")
            List<String> postGeneration,
            @JsonProperty("pre-build")
            List<String> preBuild,
            @JsonProperty("post-build")
            List<String> postBuild) {
    }
}
