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
import java.util.List;
import java.util.Map;

/**
 * Analysis result for existing Go project.
 *
 * @doc.type record
 * @doc.purpose Analysis result for Go project structure and configuration
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GoAnalysisResult(
        @JsonProperty("modulePath") String modulePath,
        @JsonProperty("goVersion") String goVersion,
        @JsonProperty("directDependencies") List<String> directDependencies,
        @JsonProperty("indirectDependencies") List<String> indirectDependencies,
        @JsonProperty("unusedDependencies") List<String> unusedDependencies,
        @JsonProperty("missingDependencies") List<String> missingDependencies,
        @JsonProperty("packageCount") int packageCount,
        @JsonProperty("testCoverage") double testCoverage,
        @JsonProperty("issues") List<AnalysisIssue> issues,
        @JsonProperty("metrics") Map<String, Object> metrics) {

    /**
     * Analysis issue found in Go project.
     */
    public record AnalysisIssue(
            @JsonProperty("type") String type,
            @JsonProperty("severity") String severity,
            @JsonProperty("message") String message,
            @JsonProperty("location") String location,
            @JsonProperty("suggestion") String suggestion) {}
}
