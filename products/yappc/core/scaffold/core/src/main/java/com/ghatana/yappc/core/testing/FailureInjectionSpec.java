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

package com.ghatana.yappc.core.testing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Day 30: E2E failure injection test specification. Defines how to inject specific failures for
 * testing system resilience.
 *
 * @doc.type class
 * @doc.purpose Day 30: E2E failure injection test specification. Defines how to inject specific failures for
 * @doc.layer platform
 * @doc.pattern Specification
 */
public class FailureInjectionSpec {

    @JsonProperty("testId")
    private final String testId;

    @JsonProperty("testName")
    private final String testName;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("targetProject")
    private final TargetProject targetProject;

    @JsonProperty("injectedFailures")
    private final List<InjectedFailure> injectedFailures;

    @JsonProperty("expectedBehavior")
    private final ExpectedBehavior expectedBehavior;

    @JsonProperty("timeout")
    private final long timeoutMs;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    public FailureInjectionSpec(
            String testId,
            String testName,
            String description,
            TargetProject targetProject,
            List<InjectedFailure> injectedFailures,
            ExpectedBehavior expectedBehavior,
            long timeoutMs,
            Map<String, Object> metadata) {
        this.testId = testId;
        this.testName = testName;
        this.description = description;
        this.targetProject = targetProject;
        this.injectedFailures = injectedFailures;
        this.expectedBehavior = expectedBehavior;
        this.timeoutMs = timeoutMs;
        this.metadata = metadata;
    }

    // Getters
    public String getTestId() {
        return testId;
    }

    public String getTestName() {
        return testName;
    }

    public String getDescription() {
        return description;
    }

    public TargetProject getTargetProject() {
        return targetProject;
    }

    public List<InjectedFailure> getInjectedFailures() {
        return injectedFailures;
    }

    public ExpectedBehavior getExpectedBehavior() {
        return expectedBehavior;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
 * Target project configuration for testing */
    public static class TargetProject {
        @JsonProperty("projectType")
        private final String projectType;

        @JsonProperty("buildTool")
        private final String buildTool;

        @JsonProperty("dependencies")
        private final List<String> dependencies;

        @JsonProperty("sourceFiles")
        private final List<SourceFile> sourceFiles;

        @JsonProperty("buildScript")
        private final String buildScript;

        public TargetProject(
                String projectType,
                String buildTool,
                List<String> dependencies,
                List<SourceFile> sourceFiles,
                String buildScript) {
            this.projectType = projectType;
            this.buildTool = buildTool;
            this.dependencies = dependencies;
            this.sourceFiles = sourceFiles;
            this.buildScript = buildScript;
        }

        public String getProjectType() {
            return projectType;
        }

        public String getBuildTool() {
            return buildTool;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public List<SourceFile> getSourceFiles() {
            return sourceFiles;
        }

        public String getBuildScript() {
            return buildScript;
        }

        public static class SourceFile {
            @JsonProperty("path")
            private final String path;

            @JsonProperty("content")
            private final String content;

            @JsonProperty("language")
            private final String language;

            public SourceFile(String path, String content, String language) {
                this.path = path;
                this.content = content;
                this.language = language;
            }

            public String getPath() {
                return path;
            }

            public String getContent() {
                return content;
            }

            public String getLanguage() {
                return language;
            }
        }
    }

    /**
 * Specification for a failure to inject */
    public static class InjectedFailure {
        @JsonProperty("failureType")
        private final FailureType failureType;

        @JsonProperty("trigger")
        private final String trigger; // When to trigger the failure

        @JsonProperty("parameters")
        private final Map<String, Object> parameters;

        @JsonProperty("duration")
        private final Long durationMs; // How long the failure should last

        public enum FailureType {
            COMPILATION_ERROR, // Inject syntax/compilation errors
            DEPENDENCY_MISSING, // Remove or corrupt dependencies
            TEST_FAILURE, // Inject failing test cases
            BUILD_TIMEOUT, // Simulate build timeouts
            NETWORK_ERROR, // Simulate network connectivity issues
            DISK_FULL, // Simulate disk space issues
            PERMISSION_DENIED, // Simulate permission problems
            MEMORY_EXHAUSTION, // Simulate out-of-memory conditions
            CONFIG_ERROR // Inject configuration errors
        }

        public InjectedFailure(
                FailureType failureType,
                String trigger,
                Map<String, Object> parameters,
                Long durationMs) {
            this.failureType = failureType;
            this.trigger = trigger;
            this.parameters = parameters;
            this.durationMs = durationMs;
        }

        public FailureType getFailureType() {
            return failureType;
        }

        public String getTrigger() {
            return trigger;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public Long getDurationMs() {
            return durationMs;
        }
    }

    /**
 * Expected behavior after failure injection */
    public static class ExpectedBehavior {
        @JsonProperty("shouldDetectFailure")
        private final boolean shouldDetectFailure;

        @JsonProperty("expectedRootCause")
        private final String expectedRootCause;

        @JsonProperty("expectedSuggestions")
        private final List<String> expectedSuggestions;

        @JsonProperty("shouldRecover")
        private final boolean shouldRecover;

        @JsonProperty("recoveryTimeMs")
        private final Long recoveryTimeMs;

        @JsonProperty("validationCriteria")
        private final List<ValidationCriterion> validationCriteria;

        public ExpectedBehavior(
                boolean shouldDetectFailure,
                String expectedRootCause,
                List<String> expectedSuggestions,
                boolean shouldRecover,
                Long recoveryTimeMs,
                List<ValidationCriterion> validationCriteria) {
            this.shouldDetectFailure = shouldDetectFailure;
            this.expectedRootCause = expectedRootCause;
            this.expectedSuggestions = expectedSuggestions;
            this.shouldRecover = shouldRecover;
            this.recoveryTimeMs = recoveryTimeMs;
            this.validationCriteria = validationCriteria;
        }

        public boolean isShouldDetectFailure() {
            return shouldDetectFailure;
        }

        public String getExpectedRootCause() {
            return expectedRootCause;
        }

        public List<String> getExpectedSuggestions() {
            return expectedSuggestions;
        }

        public boolean isShouldRecover() {
            return shouldRecover;
        }

        public Long getRecoveryTimeMs() {
            return recoveryTimeMs;
        }

        public List<ValidationCriterion> getValidationCriteria() {
            return validationCriteria;
        }

        public static class ValidationCriterion {
            @JsonProperty("type")
            private final String type;

            @JsonProperty("condition")
            private final String condition;

            @JsonProperty("expectedValue")
            private final Object expectedValue;

            public ValidationCriterion(String type, String condition, Object expectedValue) {
                this.type = type;
                this.condition = condition;
                this.expectedValue = expectedValue;
            }

            public String getType() {
                return type;
            }

            public String getCondition() {
                return condition;
            }

            public Object getExpectedValue() {
                return expectedValue;
            }
        }
    }
}
