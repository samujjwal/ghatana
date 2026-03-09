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

package com.ghatana.yappc.core.framework;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Template metadata from YAML configuration.
 * 
 * @doc.type record
 * @doc.purpose Store template metadata
 * @doc.layer platform
 * @doc.pattern Value Object
 * 
 * @param description Template description
 * @param author Template author
 * @param version Template version
 * @param tags Template tags for categorization
 * @param variables Variable definitions
 * @param dependencies Template dependencies
 * @param examples Usage examples
 */
public record TemplateMetadata(
    @JsonProperty("description") String description,
    @JsonProperty("author") String author,
    @JsonProperty("version") String version,
    @JsonProperty("tags") List<String> tags,
    @JsonProperty("variables") Map<String, TemplateVariable> variables,
    @JsonProperty("dependencies") List<String> dependencies,
    @JsonProperty("examples") List<TemplateExample> examples
) {
    /**
     * Template usage example.
     */
    public record TemplateExample(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("variables") Map<String, Object> variables
    ) {}
}
