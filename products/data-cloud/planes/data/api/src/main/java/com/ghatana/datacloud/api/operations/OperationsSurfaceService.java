/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.operations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for surfacing operation details including retries, failures, dead-letter/recovery, policy decisions, and runtime dependencies.
 * 
 * P10.3: Surface retries, failures, dead-letter/recovery, policy decisions, runtime dependencies.
 * Provides a unified interface for querying and displaying operation observability data.
 * 
 * @doc.type interface
 * @doc.purpose Surface operation observability data
 * @doc.layer product
 * @doc.pattern Service
 */
public interface OperationsSurfaceService {

    /**
     * Gets operation details with retries and failures.
     *
     * @param operationId the operation ID
     * @return the operation details
     */
    Optional<OperationDetails> getOperationDetails(String operationId);

    /**
     * Gets dead-letter queue items for a tenant.
     *
     * @param tenantId the tenant ID
     * @param limit maximum number of items to return
     * @return list of dead-letter items
     */
    List<DeadLetterItem> getDeadLetterItems(String tenantId, int limit);

    /**
     * Gets recovery attempts for a dead-letter item.
     *
     * @param itemId the dead-letter item ID
     * @return list of recovery attempts
     */
    List<RecoveryAttempt> getRecoveryAttempts(String itemId);

    /**
     * Gets policy decisions for a tenant.
     *
     * @param tenantId the tenant ID
     * @param filters optional filters
     * @return list of policy decisions
     */
    List<PolicyDecision> getPolicyDecisions(String tenantId, PolicyDecisionFilters filters);

    /**
     * Gets runtime dependency status.
     *
     * @param tenantId the tenant ID
     * @return list of runtime dependencies
     */
    List<RuntimeDependencyStatus> getRuntimeDependencies(String tenantId);

    /**
     * Gets operation summary for a tenant.
     *
     * @param tenantId the tenant ID
     * @param timeRange time range for the summary
     * @return the operation summary
     */
    OperationSummary getOperationSummary(String tenantId, TimeRange timeRange);

    /**
     * Operation details with retries and failures.
     *
     * @param operation the operation
     * @param retries retry attempts
     * @param failures failure information
     * @param runtimeDependencies runtime dependency status
     */
    record OperationDetails(
            Operation operation,
            List<RetryAttempt> retries,
            List<FailureInfo> failures,
            Map<String, RuntimeDependencyStatus> runtimeDependencies) {}

    /**
     * Retry attempt.
     *
     * @param attemptNumber the attempt number
     * @param timestamp when the retry occurred
     * @param reason the reason for retry
     * @param success whether the retry succeeded
     */
    record RetryAttempt(
            int attemptNumber,
            Instant timestamp,
            String reason,
            boolean success) {}

    /**
     * Failure information.
     *
     * @param timestamp when the failure occurred
     * @param errorType the error type
     * @param errorMessage the error message
     * @param recoverable whether the error is recoverable
     * @param recoveryAction the recovery action taken (if any)
     */
    record FailureInfo(
            Instant timestamp,
            String errorType,
            String errorMessage,
            boolean recoverable,
            String recoveryAction) {}

    /**
     * Dead-letter item.
     *
     * @param id the item ID
     * @param operationId the original operation ID
     * @param tenantId the tenant ID
     * @param operationType the operation type
     * @param reason the reason for dead-letter
     * @param payload the original payload
     * @param createdAt when the item was created
     * @param expiresAt when the item expires (null if no expiration)
     * @param recoveryStatus the recovery status
     */
    record DeadLetterItem(
            String id,
            String operationId,
            String tenantId,
            OperationType operationType,
            String reason,
            Map<String, Object> payload,
            Instant createdAt,
            Instant expiresAt,
            RecoveryStatus recoveryStatus) {

        public enum RecoveryStatus {
            PENDING,
            IN_PROGRESS,
            RECOVERED,
            FAILED,
            EXPIRED
        }
    }

    /**
     * Recovery attempt.
     *
     * @param attemptId the attempt ID
     * @param itemId the dead-letter item ID
     * @param timestamp when the attempt was made
     * @param strategy the recovery strategy used
     * @param success whether the recovery succeeded
     * @param errorMessage error message (if failed)
     */
    record RecoveryAttempt(
            String attemptId,
            String itemId,
            Instant timestamp,
            RecoveryStrategy strategy,
            boolean success,
            String errorMessage) {

        public enum RecoveryStrategy {
            RETRY,
            MANUAL_INTERVENTION,
            ALTERNATE_PATH,
            COMPENSATION,
            IGNORE
        }
    }

    /**
     * Policy decision.
     *
     * @param decisionId the decision ID
     * @param policyId the policy ID
     * @param tenantId the tenant ID
     * @param decision the decision made
     * @param reason the reason for the decision
     * @param context the decision context
     * @param timestamp when the decision was made
     * @param reviewedBy who reviewed the decision (if applicable)
     */
    record PolicyDecision(
            String decisionId,
            String policyId,
            String tenantId,
            Decision decision,
            String reason,
            Map<String, Object> context,
            Instant timestamp,
            String reviewedBy) {

        public enum Decision {
            APPROVE,
            REJECT,
            ESCALATE,
            DEFER
        }
    }

    /**
     * Policy decision filters.
     *
     * @param policyId optional policy ID filter
     * @param decision optional decision filter
     * @param startDate optional start date filter
     * @param endDate optional end date filter
     */
    record PolicyDecisionFilters(
            String policyId,
            Decision decision,
            Instant startDate,
            Instant endDate) {}

    /**
     * Runtime dependency status.
     *
     * @param name the dependency name
     * @param type the dependency type
     * @param status the current status
     * @param lastChecked when the dependency was last checked
     * @param lastHealthy when the dependency was last healthy
     * @param uptimePercentage uptime percentage
     * @param metadata additional metadata
     */
    record RuntimeDependencyStatus(
            String name,
            DependencyType type,
            DependencyStatus status,
            Instant lastChecked,
            Instant lastHealthy,
            double uptimePercentage,
            Map<String, String> metadata) {

        public enum DependencyType {
            DATABASE,
            CACHE,
            MESSAGE_QUEUE,
            EXTERNAL_API,
            STORAGE,
            SEARCH,
            STREAM
        }

        public enum DependencyStatus {
            HEALTHY,
            DEGRADED,
            UNAVAILABLE,
            MISCONFIGURED
        }
    }

    /**
     * Operation summary.
     *
     * @param totalOperations total number of operations
     * @param successfulOperations number of successful operations
     * @param failedOperations number of failed operations
     * @param retriedOperations number of retried operations
     * @param deadLetterCount number of dead-letter items
     * @param averageDurationMs average operation duration in milliseconds
     * @param successRate success rate percentage
     */
    record OperationSummary(
            int totalOperations,
            int successfulOperations,
            int failedOperations,
            int retriedOperations,
            int deadLetterCount,
            long averageDurationMs,
            double successRate) {}

    /**
     * Time range for queries.
     *
     * @param start start time
     * @param end end time
     */
    record TimeRange(Instant start, Instant end) {}
}
