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

/**
 * Python build improvement suggestions.
 *
 * @doc.type record
 * @doc.purpose Improvement suggestions for Python projects
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record PythonImprovementSuggestions(
        @JsonProperty("dependencyUpdates") List<DependencyUpdate> dependencyUpdates,
        @JsonProperty("securityIssues") List<SecurityIssue> securityIssues,
        @JsonProperty("performanceHints") List<String> performanceHints,
        @JsonProperty("bestPractices") List<String> bestPractices,
        @JsonProperty("deprecations") List<String> deprecations,
        @JsonProperty("confidenceScore") double confidenceScore) {

    @JsonCreator
    public PythonImprovementSuggestions {
    }

    public record DependencyUpdate(
            @JsonProperty("name") String name,
            @JsonProperty("currentVersion") String currentVersion,
            @JsonProperty("suggestedVersion") String suggestedVersion,
            @JsonProperty("breaking") boolean breaking,
            @JsonProperty("reason") String reason) {

        @JsonCreator
        public DependencyUpdate {
        }
    }

    public record SecurityIssue(
            @JsonProperty("dependency") String dependency,
            @JsonProperty("severity") String severity,
            @JsonProperty("description") String description,
            @JsonProperty("fixedIn") String fixedIn) {

        @JsonCreator
        public SecurityIssue {
        }
    }
}
