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
import com.ghatana.yappc.core.common.EstimatedEffort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Day 28: Build script improvement suggestions
 */
@Data
@Builder
/**
 * BuildScriptImprovement component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose BuildScriptImprovement component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class BuildScriptImprovement {

    @JsonProperty("improvementId")
    private final String improvementId;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("originalScript")
    private final String originalScript;

    @JsonProperty("improvedScript")
    private final String improvedScript;

    @JsonProperty("suggestions")
    private final List<ImprovementSuggestion> suggestions;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    public BuildScriptImprovement(
            String improvementId,
            Instant timestamp,
            String originalScript,
            String improvedScript,
            List<ImprovementSuggestion> suggestions,
            Map<String, Object> metadata) {
        this.improvementId = improvementId;
        this.timestamp = timestamp;
        this.originalScript = originalScript;
        this.improvedScript = improvedScript;
        this.suggestions = suggestions;
        this.metadata = metadata;
    }

    // Getters
    public String getImprovementId() {
        return improvementId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getOriginalScript() {
        return originalScript;
    }

    public String getImprovedScript() {
        return improvedScript;
    }

    public List<ImprovementSuggestion> getSuggestions() {
        return suggestions;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Individual improvement suggestion
     */
    public static class ImprovementSuggestion {

        @JsonProperty("category")
        private final String category;

        @JsonProperty("title")
        private final String title;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("priority")
        private final Priority priority;

        @JsonProperty("impact")
        private final String impact;

        @JsonProperty("effort")
        private final EstimatedEffort effort;

        public ImprovementSuggestion(
                String category,
                String title,
                String description,
                Priority priority,
                String impact,
                EstimatedEffort effort) {
            this.category = category;
            this.title = title;
            this.description = description;
            this.priority = priority;
            this.impact = impact;
            this.effort = effort;
        }

        // Getters
        public String getCategory() {
            return category;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public Priority getPriority() {
            return priority;
        }

        public String getImpact() {
            return impact;
        }

        public EstimatedEffort getEffort() {
            return effort;
        }
    }

    /**
     * Priority level for improvement suggestions
     */
    public enum Priority {
        LOW, // Nice to have, no urgent impact
        MEDIUM, // Should be addressed, affects quality
        HIGH, // Important, affects functionality
        CRITICAL  // Must fix, blocks development
    }

    /**
     * Estimated effort to implement the improvement
     */
    public enum EstimatedEffort {
        TRIVIAL, // < 5 minutes
        SMALL, // 5-15 minutes
        MEDIUM, // 15-60 minutes
        LARGE, // 1-4 hours
        VERY_LARGE  // > 4 hours
    }
}
