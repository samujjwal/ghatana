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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Generated Go project containing go.mod and related configuration files.
 *
 * @doc.type record
 * @doc.purpose Container for generated Go project files
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GeneratedGoProject(
        @JsonProperty("goModContent") String goModContent,
        @JsonProperty("makefileContent") String makefileContent,
        @JsonProperty("dockerfileContent") String dockerfileContent,
        @JsonProperty("gitignoreContent") String gitignoreContent,
        @JsonProperty("sourceFiles") Map<String, String> sourceFiles,
        @JsonProperty("testFiles") Map<String, String> testFiles,
        @JsonProperty("configFiles") Map<String, String> configFiles,
        @JsonProperty("metadata") GoProjectMetadata metadata) {

    /**
     * Metadata about the generated Go project.
     */
    public record GoProjectMetadata(
            @JsonProperty("modulePath") String modulePath,
            @JsonProperty("goVersion") String goVersion,
            @JsonProperty("projectType") String projectType,
            @JsonProperty("dependencyCount") int dependencyCount,
            @JsonProperty("generatedAt") String generatedAt) {}
}
