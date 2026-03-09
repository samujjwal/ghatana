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

package com.ghatana.yappc.core.buildgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Day 28: Generated build script result with metadata.
 * @doc.type class
 * @doc.purpose Day 28: Generated build script result with metadata.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class GeneratedBuildScript {

    @JsonProperty("generationId")
    private final String generationId;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("buildTool")
    private final String buildTool;

    @JsonProperty("projectType")
    private final String projectType;

    @JsonProperty("content")
    private final String content;

    @JsonProperty("additionalFiles")
    private final Map<String, String> additionalFiles; // filename -> content

    @JsonProperty("optimizations")
    private final List<Optimization> optimizations;

    @JsonProperty("warnings")
    private final List<String> warnings;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    public GeneratedBuildScript(
            String generationId,
            Instant timestamp,
            String buildTool,
            String projectType,
            String content,
            Map<String, String> additionalFiles,
            List<Optimization> optimizations,
            List<String> warnings,
            Map<String, Object> metadata) {
        this.generationId = generationId;
        this.timestamp = timestamp;
        this.buildTool = buildTool;
        this.projectType = projectType;
        this.content = content;
        this.additionalFiles = additionalFiles;
        this.optimizations = optimizations;
        this.warnings = warnings;
        this.metadata = metadata;
    }

    // Getters
    public String getGenerationId() {
        return generationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getBuildTool() {
        return buildTool;
    }

    public String getProjectType() {
        return projectType;
    }

    public String getContent() {
        return content;
    }

    public Map<String, String> getAdditionalFiles() {
        return additionalFiles;
    }

    public List<Optimization> getOptimizations() {
        return optimizations;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
 * Build script optimization applied during generation */
    public static class Optimization {
        @JsonProperty("type")
        private final String type;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("impact")
        private final String impact; // "performance", "maintainability", "reliability"

        @JsonProperty("details")
        private final String details;

        public Optimization(String type, String description, String impact, String details) {
            this.type = type;
            this.description = description;
            this.impact = impact;
            this.details = details;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public String getImpact() {
            return impact;
        }

        public String getDetails() {
            return details;
        }
    }
}
