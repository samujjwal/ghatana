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

package com.ghatana.yappc.core.language;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Language definition with all metadata.
 * 
 * @doc.type record
 * @doc.purpose Complete language definition with versions, frameworks, and tools
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LanguageDefinition(
        @JsonProperty("name")
        String name,
        @JsonProperty("versions")
        List<String> versions,
        @JsonProperty("packageManagement")
        PackageManagementDefinition packageManagement,
        @JsonProperty("buildSystems")
        Map<String, BuildSystemDefinition> buildSystems,
        @JsonProperty("frameworks")
        Map<String, FrameworkDefinition> frameworks,
        @JsonProperty("testing")
        TestingDefinition testing,
        @JsonProperty("linting")
        LintingDefinition linting,
        @JsonProperty("conventions")
        ConventionsDefinition conventions) {
}

/**
 * Package management definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record PackageManagementDefinition(
        @JsonProperty("primary")
        String primary,
        @JsonProperty("options")
        List<String> options,
        @JsonProperty("files")
        List<String> files,
        @JsonProperty("commands")
        Map<String, String> commands) {
}

/**
 * Build system definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record BuildSystemDefinition(
        @JsonProperty("files")
        List<String> files,
        @JsonProperty("commands")
        Map<String, String> commands,
        @JsonProperty("outputs")
        List<String> outputs,
        @JsonProperty("targets")
        List<String> targets) {
}

/**
 * Framework definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record FrameworkDefinition(
        @JsonProperty("templates")
        List<String> templates,
        @JsonProperty("conventions")
        List<String> conventions,
        @JsonProperty("testing")
        List<String> testing,
        @JsonProperty("async")
        Boolean async,
        @JsonProperty("orm")
        Boolean orm,
        @JsonProperty("config")
        List<String> config) {
}

/**
 * Testing definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TestingDefinition(
        @JsonProperty("frameworks")
        List<String> frameworks,
        @JsonProperty("conventions")
        List<String> conventions,
        @JsonProperty("coverage")
        List<String> coverage) {
}

/**
 * Linting definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record LintingDefinition(
        @JsonProperty("tools")
        List<String> tools,
        @JsonProperty("config")
        List<String> config) {
}

/**
 * Conventions definition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ConventionsDefinition(
        @JsonProperty("directory")
        List<String> directory,
        @JsonProperty("naming")
        List<String> naming,
        @JsonProperty("imports")
        List<String> imports) {
}
