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
import com.ghatana.platform.domain.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Day 28: Build script validation results
 * @doc.type class
 * @doc.purpose Day 28: Build script validation results
 * @doc.layer platform
 * @doc.pattern Component
 */
public class BuildScriptValidation {

    @JsonProperty("validationId")
    private final String validationId;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("isValid")
    private final boolean isValid;

    @JsonProperty("errors")
    private final List<ValidationIssue> errors;

    @JsonProperty("warnings")
    private final List<ValidationIssue> warnings;

    @JsonProperty("suggestions")
    private final List<ValidationIssue> suggestions;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    public BuildScriptValidation(
            String validationId,
            Instant timestamp,
            boolean isValid,
            List<ValidationIssue> errors,
            List<ValidationIssue> warnings,
            List<ValidationIssue> suggestions,
            Map<String, Object> metadata) {
        this.validationId = validationId;
        this.timestamp = timestamp;
        this.isValid = isValid;
        this.errors = errors;
        this.warnings = warnings;
        this.suggestions = suggestions;
        this.metadata = metadata;
    }

    // Getters
    public String getValidationId() {
        return validationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isValid() {
        return isValid;
    }

    public List<ValidationIssue> getErrors() {
        return errors;
    }

    public List<ValidationIssue> getWarnings() {
        return warnings;
    }

    public List<ValidationIssue> getSuggestions() {
        return suggestions;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
 * Validation issue (error, warning, or suggestion) */
    public static class ValidationIssue {
        @JsonProperty("type")
        private final String type;

        @JsonProperty("message")
        private final String message;

        @JsonProperty("line")
        private final Integer line;

        @JsonProperty("column")
        private final Integer column;

        @JsonProperty("severity")
        private final Severity severity;

        @JsonProperty("rule")
        private final String rule;

        public ValidationIssue(
                String type,
                String message,
                Integer line,
                Integer column,
                Severity severity,
                String rule) {
            this.type = type;
            this.message = message;
            this.line = line;
            this.column = column;
            this.severity = severity;
            this.rule = rule;
        }

        // Getters
        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public Integer getLine() {
            return line;
        }

        public Integer getColumn() {
            return column;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getRule() {
            return rule;
        }
    }
}
