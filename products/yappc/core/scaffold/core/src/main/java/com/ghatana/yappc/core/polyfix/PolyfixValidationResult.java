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
package com.ghatana.yappc.core.polyfix;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Day 29: Polyfix codemod validation result. Captures the results of validating
 * generated code using Polyfix codemods.
 *
 * @doc.type class
 * @doc.purpose Day 29: Polyfix codemod validation result. Captures the results
 * of validating generated code
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class PolyfixValidationResult {

    @JsonProperty("validationId")
    private final String validationId;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("targetPath")
    private final String targetPath;

    @JsonProperty("validationStatus")
    private final ValidationStatus validationStatus;

    @JsonProperty("appliedCodemods")
    private final List<AppliedCodemod> appliedCodemods;

    @JsonProperty("validationIssues")
    private final List<ValidationIssue> validationIssues;

    @JsonProperty("suggestions")
    private final List<CodemodSuggestion> suggestions;

    @JsonProperty("metrics")
    private final ValidationMetrics metrics;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    public enum ValidationStatus {
        PASSED, // All codemods applied successfully, no issues
        PASSED_WITH_WARNINGS, // Codemods applied but with warnings
        FAILED, // Critical issues found, codemods couldn't be applied
        SKIPPED // Validation was skipped (no applicable codemods)
    }

    /**
     * Priority levels for codemod suggestions.
     */
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Severity levels for validation issues.
     */
    public enum Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    public PolyfixValidationResult(
            String validationId,
            Instant timestamp,
            String targetPath,
            ValidationStatus validationStatus,
            List<AppliedCodemod> appliedCodemods,
            List<ValidationIssue> validationIssues,
            List<CodemodSuggestion> suggestions,
            ValidationMetrics metrics,
            Map<String, Object> metadata) {
        this.validationId = validationId;
        this.timestamp = timestamp;
        this.targetPath = targetPath;
        this.validationStatus = validationStatus;
        this.appliedCodemods = appliedCodemods;
        this.validationIssues = validationIssues;
        this.suggestions = suggestions;
        this.metrics = metrics;
        this.metadata = metadata;
    }

    // Getters
    public String getValidationId() {
        return validationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public List<AppliedCodemod> getAppliedCodemods() {
        return appliedCodemods;
    }

    public List<ValidationIssue> getValidationIssues() {
        return validationIssues;
    }

    public List<CodemodSuggestion> getSuggestions() {
        return suggestions;
    }

    public ValidationMetrics getMetrics() {
        return metrics;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Information about a codemod that was applied
     */
    public static class AppliedCodemod {

        @JsonProperty("name")
        private final String name;

        @JsonProperty("version")
        private final String version;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("filesModified")
        private final int filesModified;

        @JsonProperty("changesApplied")
        private final int changesApplied;

        @JsonProperty("executionTimeMs")
        private final long executionTimeMs;

        @JsonProperty("status")
        private final CodemodStatus status;

        @JsonProperty("errors")
        private final List<String> errors;

        public enum CodemodStatus {
            SUCCESS,
            PARTIAL,
            FAILED,
            SKIPPED
        }

        public AppliedCodemod(
                String name,
                String version,
                String description,
                int filesModified,
                int changesApplied,
                long executionTimeMs,
                CodemodStatus status,
                List<String> errors) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.filesModified = filesModified;
            this.changesApplied = changesApplied;
            this.executionTimeMs = executionTimeMs;
            this.status = status;
            this.errors = errors;
        }

        // Getters
        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }

        public int getFilesModified() {
            return filesModified;
        }

        public int getChangesApplied() {
            return changesApplied;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public CodemodStatus getStatus() {
            return status;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Validation issue found during codemod execution
     */
    public static class ValidationIssue {

        @JsonProperty("type")
        private final String type;

        @JsonProperty("severity")
        private final Severity severity;

        @JsonProperty("message")
        private final String message;

        @JsonProperty("filePath")
        private final String filePath;

        @JsonProperty("lineNumber")
        private final Integer lineNumber;

        @JsonProperty("columnNumber")
        private final Integer columnNumber;

        @JsonProperty("ruleId")
        private final String ruleId;

        public ValidationIssue(
                String type,
                Severity severity,
                String message,
                String filePath,
                Integer lineNumber,
                Integer columnNumber,
                String ruleId) {
            this.type = type;
            this.severity = severity;
            this.message = message;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.ruleId = ruleId;
        }

        // Getters
        public String getType() {
            return type;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        public String getFilePath() {
            return filePath;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        public Integer getColumnNumber() {
            return columnNumber;
        }

        public String getRuleId() {
            return ruleId;
        }
    }

    /**
     * Suggestion for additional codemods or improvements
     */
    public static class CodemodSuggestion {

        @JsonProperty("codemodName")
        private final String codemodName;

        @JsonProperty("priority")
        private final Priority priority;

        @JsonProperty("description")
        private final String description;

        @JsonProperty("reasoning")
        private final String reasoning;

        @JsonProperty("estimatedBenefit")
        private final String estimatedBenefit;

        public CodemodSuggestion(
                String codemodName,
                Priority priority,
                String description,
                String reasoning,
                String estimatedBenefit) {
            this.codemodName = codemodName;
            this.priority = priority;
            this.description = description;
            this.reasoning = reasoning;
            this.estimatedBenefit = estimatedBenefit;
        }

        // Getters
        public String getCodemodName() {
            return codemodName;
        }

        public Priority getPriority() {
            return priority;
        }

        public String getDescription() {
            return description;
        }

        public String getReasoning() {
            return reasoning;
        }

        public String getEstimatedBenefit() {
            return estimatedBenefit;
        }
    }

    /**
     * Metrics about the validation process
     */
    public static class ValidationMetrics {

        @JsonProperty("totalFilesAnalyzed")
        private final int totalFilesAnalyzed;

        @JsonProperty("totalFilesModified")
        private final int totalFilesModified;

        @JsonProperty("totalChangesApplied")
        private final int totalChangesApplied;

        @JsonProperty("executionTimeMs")
        private final long executionTimeMs;

        @JsonProperty("codeQualityScore")
        private final double codeQualityScore; // 0.0 to 1.0

        @JsonProperty("coveragePercentage")
        private final double coveragePercentage;

        public ValidationMetrics(
                int totalFilesAnalyzed,
                int totalFilesModified,
                int totalChangesApplied,
                long executionTimeMs,
                double codeQualityScore,
                double coveragePercentage) {
            this.totalFilesAnalyzed = totalFilesAnalyzed;
            this.totalFilesModified = totalFilesModified;
            this.totalChangesApplied = totalChangesApplied;
            this.executionTimeMs = executionTimeMs;
            this.codeQualityScore = codeQualityScore;
            this.coveragePercentage = coveragePercentage;
        }

        // Getters
        public int getTotalFilesAnalyzed() {
            return totalFilesAnalyzed;
        }

        public int getTotalFilesModified() {
            return totalFilesModified;
        }

        public int getTotalChangesApplied() {
            return totalChangesApplied;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public double getCodeQualityScore() {
            return codeQualityScore;
        }

        public double getCoveragePercentage() {
            return coveragePercentage;
        }
    }
}
