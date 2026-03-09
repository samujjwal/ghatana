/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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

package com.ghatana.datacloud.reflex;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of reflex rule execution.
 *
 * <p>Captures the outcome of executing a reflex action, including
 * success/failure status, timing, and any outputs produced.
 *
 * @doc.type record
 * @doc.purpose Reflex execution outcome
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class ReflexOutcome {

    /**
     * Unique identifier for this outcome.
     */
    String outcomeId;

    /**
     * The rule that was executed.
     */
    String ruleId;

    /**
     * The trigger that caused execution.
     */
    String triggerId;

    /**
     * The action that was executed.
     */
    ReflexRule.ActionType actionType;

    /**
     * Execution status.
     */
    Status status;

    /**
     * When execution started.
     */
    Instant startTime;

    /**
     * When execution completed.
     */
    Instant endTime;

    /**
     * Execution duration.
     */
    Duration duration;

    /**
     * Output/result of the action.
     */
    @Builder.Default
    Map<String, Object> output = Map.of();

    /**
     * Error message if failed.
     */
    String errorMessage;

    /**
     * Exception class if failed.
     */
    String exceptionType;

    /**
     * Whether the action was retried.
     */
    @Builder.Default
    boolean retried = false;

    /**
     * Number of retry attempts.
     */
    @Builder.Default
    int retryCount = 0;

    /**
     * Whether the action can be rolled back.
     */
    @Builder.Default
    boolean reversible = true;

    /**
     * Whether a rollback was performed.
     */
    @Builder.Default
    boolean rolledBack = false;

    /**
     * Tenant ID.
     */
    String tenantId;

    /**
     * Additional metadata.
     */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    /**
     * Execution status values.
     */
    public enum Status {
        /**
         * Execution succeeded.
         */
        SUCCESS,

        /**
         * Execution failed.
         */
        FAILURE,

        /**
         * Execution timed out.
         */
        TIMEOUT,

        /**
         * Execution was skipped (cooldown, etc.).
         */
        SKIPPED,

        /**
         * Execution was suppressed by inhibition.
         */
        SUPPRESSED,

        /**
         * Execution is pending.
         */
        PENDING,

        /**
         * Execution was rolled back.
         */
        ROLLED_BACK
    }

    /**
     * Checks if execution was successful.
     *
     * @return true if success
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * Gets the execution time in milliseconds.
     *
     * @return execution time
     */
    public long getExecutionTimeMs() {
        return duration != null ? duration.toMillis() : 0;
    }

    /**
     * Creates a success outcome.
     *
     * @param ruleId the rule ID
     * @param triggerId the trigger ID
     * @param actionType the action type
     * @param startTime when execution started
     * @param output the execution output
     * @param tenantId the tenant ID
     * @return success outcome
     */
    public static ReflexOutcome success(
            String ruleId,
            String triggerId,
            ReflexRule.ActionType actionType,
            Instant startTime,
            Map<String, Object> output,
            String tenantId) {

        Instant endTime = Instant.now();
        return ReflexOutcome.builder()
                .outcomeId("out-" + System.nanoTime())
                .ruleId(ruleId)
                .triggerId(triggerId)
                .actionType(actionType)
                .status(Status.SUCCESS)
                .startTime(startTime)
                .endTime(endTime)
                .duration(Duration.between(startTime, endTime))
                .output(output)
                .tenantId(tenantId)
                .build();
    }

    /**
     * Creates a failure outcome.
     *
     * @param ruleId the rule ID
     * @param triggerId the trigger ID
     * @param actionType the action type
     * @param startTime when execution started
     * @param error the exception
     * @param tenantId the tenant ID
     * @return failure outcome
     */
    public static ReflexOutcome failure(
            String ruleId,
            String triggerId,
            ReflexRule.ActionType actionType,
            Instant startTime,
            Throwable error,
            String tenantId) {

        Instant endTime = Instant.now();
        return ReflexOutcome.builder()
                .outcomeId("out-" + System.nanoTime())
                .ruleId(ruleId)
                .triggerId(triggerId)
                .actionType(actionType)
                .status(Status.FAILURE)
                .startTime(startTime)
                .endTime(endTime)
                .duration(Duration.between(startTime, endTime))
                .errorMessage(error.getMessage())
                .exceptionType(error.getClass().getName())
                .tenantId(tenantId)
                .build();
    }

    /**
     * Creates a skipped outcome.
     *
     * @param ruleId the rule ID
     * @param triggerId the trigger ID
     * @param reason the skip reason
     * @param tenantId the tenant ID
     * @return skipped outcome
     */
    public static ReflexOutcome skipped(
            String ruleId,
            String triggerId,
            String reason,
            String tenantId) {

        return ReflexOutcome.builder()
                .outcomeId("out-" + System.nanoTime())
                .ruleId(ruleId)
                .triggerId(triggerId)
                .status(Status.SKIPPED)
                .errorMessage(reason)
                .tenantId(tenantId)
                .build();
    }
}
