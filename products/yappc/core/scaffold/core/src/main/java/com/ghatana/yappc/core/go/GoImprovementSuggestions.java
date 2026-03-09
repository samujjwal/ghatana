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

/**
 * Improvement suggestions for Go module configuration.
 *
 * @doc.type record
 * @doc.purpose Improvement suggestions for Go project optimization
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record GoImprovementSuggestions(
        @JsonProperty("dependencyUpdates") List<DependencyUpdate> dependencyUpdates,
        @JsonProperty("securityIssues") List<SecurityIssue> securityIssues,
        @JsonProperty("performanceHints") List<String> performanceHints,
        @JsonProperty("bestPractices") List<String> bestPractices,
        @JsonProperty("deprecations") List<String> deprecations) {

    /**
     * Suggested dependency update.
     */
    public record DependencyUpdate(
            @JsonProperty("module") String module,
            @JsonProperty("currentVersion") String currentVersion,
            @JsonProperty("latestVersion") String latestVersion,
            @JsonProperty("breaking") boolean breaking,
            @JsonProperty("changeLog") String changeLog) {}

    /**
     * Security issue found in dependencies.
     */
    public record SecurityIssue(
            @JsonProperty("module") String module,
            @JsonProperty("severity") String severity,
            @JsonProperty("cve") String cve,
            @JsonProperty("description") String description,
            @JsonProperty("fixVersion") String fixVersion) {}
}
