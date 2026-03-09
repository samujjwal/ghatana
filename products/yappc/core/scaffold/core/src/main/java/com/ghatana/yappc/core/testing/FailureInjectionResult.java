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
import com.ghatana.platform.testing.TestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Day 30: Result of a failure injection test execution
 * @doc.type class
 * @doc.purpose Day 30: Result of a failure injection test execution
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class FailureInjectionResult {

    @JsonProperty("testId")
    private final String testId;

    @JsonProperty("executionId")
    private final String executionId;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("status")
    private final TestStatus status;

    @JsonProperty("executionTimeMs")
    private final long executionTimeMs;

    @JsonProperty("injectedFailures")
    private final List<InjectionExecution> injectedFailures;

    @JsonProperty("systemResponses")
    private final List<SystemResponse> systemResponses;

    @JsonProperty("validationResults")
    private final List<ValidationResult> validationResults;

    @JsonProperty("logs")
    private final List<String> logs;

    @JsonProperty("metrics")
    private final TestMetrics metrics;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    public FailureInjectionResult(
            String testId,
            String executionId,
            Instant timestamp,
            TestStatus status,
            long executionTimeMs,
            List<InjectionExecution> injectedFailures,
            List<SystemResponse> systemResponses,
            List<ValidationResult> validationResults,
            List<String> logs,
            TestMetrics metrics,
            Map<String, Object> metadata) {
        this.testId = testId;
        this.executionId = executionId;
        this.timestamp = timestamp;
        this.status = status;
        this.executionTimeMs = executionTimeMs;
        this.injectedFailures = injectedFailures;
        this.systemResponses = systemResponses;
        this.validationResults = validationResults;
        this.logs = logs;
        this.metrics = metrics;
        this.metadata = metadata;
    }

    // Getters
    public String getTestId() {
        return testId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public TestStatus getStatus() {
        return status;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public List<InjectionExecution> getInjectedFailures() {
        return injectedFailures;
    }

    public List<SystemResponse> getSystemResponses() {
        return systemResponses;
    }

    public List<ValidationResult> getValidationResults() {
        return validationResults;
    }

    public List<String> getLogs() {
        return logs;
    }

    public TestMetrics getMetrics() {
        return metrics;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
 * Execution details for a specific injected failure */
    public static class InjectionExecution {
        @JsonProperty("failureType")
        private final FailureInjectionSpec.InjectedFailure.FailureType failureType;

        @JsonProperty("injectionTime")
        private final Instant injectionTime;

        @JsonProperty("success")
        private final boolean success;

        @JsonProperty("actualDurationMs")
        private final long actualDurationMs;

        @JsonProperty("error")
        private final String error;

        public InjectionExecution(
                FailureInjectionSpec.InjectedFailure.FailureType failureType,
                Instant injectionTime,
                boolean success,
                long actualDurationMs,
                String error) {
            this.failureType = failureType;
            this.injectionTime = injectionTime;
            this.success = success;
            this.actualDurationMs = actualDurationMs;
            this.error = error;
        }

        public FailureInjectionSpec.InjectedFailure.FailureType getFailureType() {
            return failureType;
        }

        public Instant getInjectionTime() {
            return injectionTime;
        }

        public boolean isSuccess() {
            return success;
        }

        public long getActualDurationMs() {
            return actualDurationMs;
        }

        public String getError() {
            return error;
        }
    }

    /**
 * System response to injected failures */
    public static class SystemResponse {
        @JsonProperty("component")
        private final String component; // RCA, build generator, etc.

        @JsonProperty("responseTime")
        private final Instant responseTime;

        @JsonProperty("action")
        private final String action;

        @JsonProperty("success")
        private final boolean success;

        @JsonProperty("details")
        private final Map<String, Object> details;

        public SystemResponse(
                String component,
                Instant responseTime,
                String action,
                boolean success,
                Map<String, Object> details) {
            this.component = component;
            this.responseTime = responseTime;
            this.action = action;
            this.success = success;
            this.details = details;
        }

        public String getComponent() {
            return component;
        }

        public Instant getResponseTime() {
            return responseTime;
        }

        public String getAction() {
            return action;
        }

        public boolean isSuccess() {
            return success;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }

    /**
 * Validation result for a specific criterion */
    public static class ValidationResult {
        @JsonProperty("criterionType")
        private final String criterionType;

        @JsonProperty("expected")
        private final Object expected;

        @JsonProperty("actual")
        private final Object actual;

        @JsonProperty("passed")
        private final boolean passed;

        @JsonProperty("message")
        private final String message;

        public ValidationResult(
                String criterionType,
                Object expected,
                Object actual,
                boolean passed,
                String message) {
            this.criterionType = criterionType;
            this.expected = expected;
            this.actual = actual;
            this.passed = passed;
            this.message = message;
        }

        public String getCriterionType() {
            return criterionType;
        }

        public Object getExpected() {
            return expected;
        }

        public Object getActual() {
            return actual;
        }

        public boolean isPassed() {
            return passed;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
 * Test execution metrics */
    public static class TestMetrics {
        @JsonProperty("setupTimeMs")
        private final long setupTimeMs;

        @JsonProperty("injectionTimeMs")
        private final long injectionTimeMs;

        @JsonProperty("responseTimeMs")
        private final long responseTimeMs;

        @JsonProperty("cleanupTimeMs")
        private final long cleanupTimeMs;

        @JsonProperty("totalSystemCalls")
        private final int totalSystemCalls;

        @JsonProperty("successfulRecoveries")
        private final int successfulRecoveries;

        public TestMetrics(
                long setupTimeMs,
                long injectionTimeMs,
                long responseTimeMs,
                long cleanupTimeMs,
                int totalSystemCalls,
                int successfulRecoveries) {
            this.setupTimeMs = setupTimeMs;
            this.injectionTimeMs = injectionTimeMs;
            this.responseTimeMs = responseTimeMs;
            this.cleanupTimeMs = cleanupTimeMs;
            this.totalSystemCalls = totalSystemCalls;
            this.successfulRecoveries = successfulRecoveries;
        }

        public long getSetupTimeMs() {
            return setupTimeMs;
        }

        public long getInjectionTimeMs() {
            return injectionTimeMs;
        }

        public long getResponseTimeMs() {
            return responseTimeMs;
        }

        public long getCleanupTimeMs() {
            return cleanupTimeMs;
        }

        public int getTotalSystemCalls() {
            return totalSystemCalls;
        }

        public int getSuccessfulRecoveries() {
            return successfulRecoveries;
        }
    }
}
