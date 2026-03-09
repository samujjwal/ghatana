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
package com.ghatana.yappc.core.rca;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.yappc.core.common.EstimatedEffort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Day 27: AI-powered Root Cause Analysis (RCA) result. Contains AI-generated
 * explanations and fix suggestions for build failures.
 *
 * @doc.type class
 * @doc.purpose Day 27: AI-powered Root Cause Analysis (RCA) result. Contains
 * AI-generated explanations and fix
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class RCAResult {

    @JsonProperty("analysisId")
    private String analysisId;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("buildLog")
    private NormalizedBuildLog buildLog;

    @JsonProperty("rootCause")
    private RootCause rootCause;

    @JsonProperty("explanation")
    private String explanation;

    @JsonProperty("fixSuggestions")
    private List<FixSuggestion> fixSuggestions;

    @JsonProperty("confidence")
    private double confidence;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    // Constructors
    public RCAResult() {
    }

    /**
     * Simple constructor for basic RCA results.
     */
    public RCAResult(String category, String rootCause, List<String> recommendations) {
        this.analysisId = java.util.UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.buildLog = null;
        this.rootCause = mapCategoryToRootCause(category);
        this.explanation = rootCause;
        this.fixSuggestions = recommendations.stream()
            .map(rec -> {
                FixSuggestion suggestion = new FixSuggestion();
                suggestion.setTitle("Recommendation");
                suggestion.setDescription(rec);
                suggestion.setPriority(FixSuggestion.Priority.MEDIUM);
                suggestion.setCategory(FixSuggestion.Category.CODE_FIX);
                return suggestion;
            })
            .toList();
        this.confidence = 0.7;
        this.metadata = Map.of("category", category);
    }

    public RCAResult(
            String analysisId,
            Instant timestamp,
            NormalizedBuildLog buildLog,
            RootCause rootCause,
            String explanation,
            List<FixSuggestion> fixSuggestions,
            double confidence,
            Map<String, Object> metadata) {
        this.analysisId = analysisId;
        this.timestamp = timestamp;
        this.buildLog = buildLog;
        this.rootCause = rootCause;
        this.explanation = explanation;
        this.fixSuggestions = fixSuggestions;
        this.confidence = confidence;
        this.metadata = metadata;
    }
    
    /**
     * Map category string to RootCause enum.
     */
    private static RootCause mapCategoryToRootCause(String category) {
        return switch (category.toLowerCase()) {
            case "compilation error" -> RootCause.COMPILATION_ERROR;
            case "null pointer" -> RootCause.COMPILATION_ERROR;
            case "class not found" -> RootCause.DEPENDENCY_ISSUE;
            case "port conflict" -> RootCause.ENVIRONMENT_ISSUE;
            case "memory error" -> RootCause.RESOURCE_EXHAUSTION;
            case "permission denied" -> RootCause.PERMISSION_DENIED;
            default -> RootCause.UNKNOWN;
        };
    }

    // Getters and setters
    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public NormalizedBuildLog getBuildLog() {
        return buildLog;
    }

    public void setBuildLog(NormalizedBuildLog buildLog) {
        this.buildLog = buildLog;
    }

    public RootCause getRootCause() {
        return rootCause;
    }

    public void setRootCause(RootCause rootCause) {
        this.rootCause = rootCause;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<FixSuggestion> getFixSuggestions() {
        return fixSuggestions;
    }

    public void setFixSuggestions(List<FixSuggestion> fixSuggestions) {
        this.fixSuggestions = fixSuggestions;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Root cause category classification
     */
    public enum RootCause {
        COMPILATION_ERROR("Compilation Error", "Code compilation failures"),
        DEPENDENCY_ISSUE("Dependency Issue", "Missing or conflicting dependencies"),
        CONFIGURATION_ERROR("Configuration Error", "Build configuration problems"),
        TEST_FAILURE("Test Failure", "Unit or integration test failures"),
        NETWORK_ERROR("Network Error", "Network connectivity issues"),
        RESOURCE_EXHAUSTION("Resource Exhaustion", "Memory, disk, or CPU limitations"),
        PERMISSION_DENIED("Permission Denied", "File system or security permissions"),
        TOOL_VERSION_MISMATCH("Tool Version Mismatch", "Incompatible tool versions"),
        ENVIRONMENT_ISSUE("Environment Issue", "Environment setup problems"),
        UNKNOWN("Unknown", "Unable to determine root cause");

        private final String displayName;
        private final String description;

        RootCause(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * AI-generated fix suggestion
     */
    public static class FixSuggestion {

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("priority")
        private Priority priority;

        @JsonProperty("category")
        private Category category;

        @JsonProperty("commands")
        private List<String> commands;

        @JsonProperty("fileChanges")
        private List<FileChange> fileChanges;

        @JsonProperty("estimatedEffort")
        private EstimatedEffort estimatedEffort;

        public FixSuggestion() {
        }

        public FixSuggestion(
                String title,
                String description,
                Priority priority,
                Category category,
                List<String> commands,
                List<FileChange> fileChanges,
                EstimatedEffort estimatedEffort) {
            this.title = title;
            this.description = description;
            this.priority = priority;
            this.category = category;
            this.commands = commands;
            this.fileChanges = fileChanges;
            this.estimatedEffort = estimatedEffort;
        }

        // Getters and setters
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Priority getPriority() {
            return priority;
        }

        public void setPriority(Priority priority) {
            this.priority = priority;
        }

        public Category getCategory() {
            return category;
        }

        public void setCategory(Category category) {
            this.category = category;
        }

        public List<String> getCommands() {
            return commands;
        }

        public void setCommands(List<String> commands) {
            this.commands = commands;
        }

        public List<FileChange> getFileChanges() {
            return fileChanges;
        }

        public void setFileChanges(List<FileChange> fileChanges) {
            this.fileChanges = fileChanges;
        }

        public EstimatedEffort getEstimatedEffort() {
            return estimatedEffort;
        }

        public void setEstimatedEffort(EstimatedEffort estimatedEffort) {
            this.estimatedEffort = estimatedEffort;
        }

        public enum Priority {
            LOW, // Nice to have, no urgent impact
            MEDIUM, // Affects functionality, should fix soon
            HIGH, // Critical fix, impacts core functionality
            URGENT    // Blocker, requires immediate attention
        }

        public enum EstimatedEffort {
            TRIVIAL, // < 5 minutes
            SMALL, // 5-15 minutes
            MEDIUM, // 15-60 minutes
            LARGE, // 1-4 hours
            VERY_LARGE  // > 4 hours
        }

        public enum Category {
            DEPENDENCY_MANAGEMENT,
            CODE_FIX,
            CONFIGURATION_CHANGE,
            ENVIRONMENT_SETUP,
            TEST_UPDATE,
            TOOL_UPGRADE
        }
    }

    /**
     * Suggested file change
     */
    public static class FileChange {

        @JsonProperty("file")
        private String file;

        @JsonProperty("operation")
        private FileOperation operation;

        @JsonProperty("content")
        private String content;

        @JsonProperty("lineNumber")
        private Integer lineNumber;

        @JsonProperty("reasoning")
        private String reasoning;

        public FileChange() {
        }

        public FileChange(
                String file,
                FileOperation operation,
                String content,
                Integer lineNumber,
                String reasoning) {
            this.file = file;
            this.operation = operation;
            this.content = content;
            this.lineNumber = lineNumber;
            this.reasoning = reasoning;
        }

        // Getters and setters
        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public FileOperation getOperation() {
            return operation;
        }

        public void setOperation(FileOperation operation) {
            this.operation = operation;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }

        public enum FileOperation {
            CREATE,
            UPDATE,
            DELETE,
            APPEND
        }
    }
}
