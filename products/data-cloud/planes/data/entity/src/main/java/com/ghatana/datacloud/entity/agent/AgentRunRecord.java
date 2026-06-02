/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.agent;

import java.time.Instant;
import java.util.*;

/**
 * Immutable record representing an agent execution run with governance tracking.
 *
 * <p>This record provides a clean, immutable view of agent execution state
 * that can be safely used across the agent runtime system. It maps to/from
 * the mutable JPA {@link AgentRun} entity via adapter methods.
 *
 * @param runId               unique run identifier
 * @param agentId             agent being executed
 * @param tenantId            tenant scope
 * @param sessionId           session identifier (optional)
 * @param correlationId       trace correlation ID (optional)
 * @param status              current run status
 * @param input               initial input provided to the agent
 * @param output              final output produced by the agent
 * @param toolCalls           all tool calls made during execution
 * @param memoryWrites        all memory writes performed
 * @param approvalRequests    all approval requests generated
 * @param policyDecisions     policy decisions made during execution
 * @param runTrace            detailed execution trace
 * @param metrics             runtime metrics and telemetry
 * @param errorInfo           error information if failed
 * @param governanceMetadata  governance metadata including compliance checks
 * @param createdAt           when the run was created
 * @param updatedAt           when the run was last updated
 * @param startedAt           when execution started
 * @param completedAt        when execution completed
 * @param createdBy           user who created the run
 * @param completedBy        user who completed the run
 * @param sideEffectDeclaration side-effect declaration from planning phase (stored as Map)
 * @param grantedApprovals    approvals granted during execution
 * @param compensationExecuted whether compensation was executed
 * @param compensationResult  result of compensation execution
 * @param traceId             distributed trace ID
 * @param policyDecision      final policy decision (stored as Map)
 *
 * @doc.type record
 * @doc.purpose Immutable record for agent execution runs
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AgentRunRecord(
        String runId,
        String agentId,
        String tenantId,
        String sessionId,
        String correlationId,
        RunStatus status,
        Map<String, Object> input,
        Map<String, Object> output,
        List<ToolCall> toolCalls,
        List<MemoryWrite> memoryWrites,
        List<ApprovalRequest> approvalRequests,
        List<com.ghatana.datacloud.entity.policy.PolicyDecision> policyDecisions,
        RunTrace runTrace,
        Map<String, Object> metrics,
        ErrorInfo errorInfo,
        Map<String, Object> governanceMetadata,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt,
        String createdBy,
        String completedBy,
        Map<String, Object> sideEffectDeclaration,
        Set<String> grantedApprovals,
        boolean compensationExecuted,
        String compensationResult,
        String traceId,
        Map<String, Object> policyDecision) {

    public AgentRunRecord {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        if (agentId.isBlank()) throw new IllegalArgumentException("agentId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");

        input = input != null ? Map.copyOf(input) : Map.of();
        output = output != null ? Map.copyOf(output) : Map.of();
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        memoryWrites = memoryWrites != null ? List.copyOf(memoryWrites) : List.of();
        approvalRequests = approvalRequests != null ? List.copyOf(approvalRequests) : List.of();
        policyDecisions = policyDecisions != null ? List.copyOf(policyDecisions) : List.of();
        metrics = metrics != null ? Map.copyOf(metrics) : Map.of();
        governanceMetadata = governanceMetadata != null ? Map.copyOf(governanceMetadata) : Map.of();
        grantedApprovals = grantedApprovals != null ? Set.copyOf(grantedApprovals) : Set.of();
    }

    /**
     * Run status enumeration.
     */
    public enum RunStatus {
        INITIALIZED,     // Run created but not started
        RUNNING,         // Run is actively executing
        WAITING_APPROVAL, // Run paused waiting for human approval
        COMPLETED,       // Run completed successfully
        FAILED,          // Run failed with error
        CANCELLED,       // Run was cancelled
        TIMEOUT,         // Run timed out
        SUSPENDED        // Run temporarily suspended
    }

    /**
     * Error information for failed runs.
     */
    public record ErrorInfo(
        String errorCode,
        String errorMessage,
        String errorType,
        Map<String, Object> context,
        Instant occurredAt,
        String stackTrace
    ) {}

    /**
     * Creates an AgentRunRecord from a JPA AgentRun entity.
     */
    public static AgentRunRecord fromEntity(AgentRun entity) {
        if (entity == null) {
            return null;
        }

        return new AgentRunRecord(
            entity.getId() != null ? entity.getId().toString() : null,
            entity.getAgentId(),
            entity.getTenantId(),
            entity.getSessionId(),
            entity.getCorrelationId(),
            toRunStatus(entity.getStatus()),
            entity.getInput(),
            entity.getOutput(),
            entity.getToolCalls(),
            entity.getMemoryWrites(),
            entity.getApprovalRequests(),
            entity.getPolicyDecisions(),
            entity.getRunTrace(),
            entity.getMetrics(),
            toErrorInfo(entity.getErrorInfo()),
            entity.getGovernanceMetadata(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getCreatedBy(),
            entity.getCompletedBy(),
            null, // sideEffectDeclaration not stored in entity
            Set.of(), // grantedApprovals not stored as separate field
            false, // compensationExecuted not stored
            null, // compensationResult not stored
            null, // traceId not stored
            null // policyDecision not stored
        );
    }

    /**
     * Converts this record to a JPA AgentRun entity.
     */
    public AgentRun toEntity() {
        AgentRun entity = new AgentRun();
        
        if (runId != null) {
            entity.setId(UUID.fromString(runId));
        }
        entity.setAgentId(agentId);
        entity.setTenantId(tenantId);
        entity.setSessionId(sessionId);
        entity.setCorrelationId(correlationId);
        entity.setStatus(toEntityStatus(status));
        entity.setInput(new HashMap<>(input));
        entity.setOutput(new HashMap<>(output));
        entity.setToolCalls(new ArrayList<>(toolCalls));
        entity.setMemoryWrites(new ArrayList<>(memoryWrites));
        entity.setApprovalRequests(new ArrayList<>(approvalRequests));
        entity.setPolicyDecisions(new ArrayList<>(policyDecisions));
        entity.setRunTrace(runTrace);
        entity.setMetrics(new HashMap<>(metrics));
        entity.setErrorInfo(toEntityErrorInfo(errorInfo));
        entity.setGovernanceMetadata(new HashMap<>(governanceMetadata));
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setStartedAt(startedAt);
        entity.setCompletedAt(completedAt);
        entity.setCreatedBy(createdBy);
        entity.setCompletedBy(completedBy);
        
        return entity;
    }

    private static RunStatus toRunStatus(AgentRun.RunStatus entityStatus) {
        if (entityStatus == null) {
            return RunStatus.INITIALIZED;
        }
        return switch (entityStatus) {
            case INITIALIZED -> RunStatus.INITIALIZED;
            case RUNNING -> RunStatus.RUNNING;
            case WAITING_APPROVAL -> RunStatus.WAITING_APPROVAL;
            case COMPLETED -> RunStatus.COMPLETED;
            case FAILED -> RunStatus.FAILED;
            case CANCELLED -> RunStatus.CANCELLED;
            case TIMEOUT -> RunStatus.TIMEOUT;
            case SUSPENDED -> RunStatus.SUSPENDED;
        };
    }

    private static AgentRun.RunStatus toEntityStatus(RunStatus recordStatus) {
        if (recordStatus == null) {
            return AgentRun.RunStatus.INITIALIZED;
        }
        return switch (recordStatus) {
            case INITIALIZED -> AgentRun.RunStatus.INITIALIZED;
            case RUNNING -> AgentRun.RunStatus.RUNNING;
            case WAITING_APPROVAL -> AgentRun.RunStatus.WAITING_APPROVAL;
            case COMPLETED -> AgentRun.RunStatus.COMPLETED;
            case FAILED -> AgentRun.RunStatus.FAILED;
            case CANCELLED -> AgentRun.RunStatus.CANCELLED;
            case TIMEOUT -> AgentRun.RunStatus.TIMEOUT;
            case SUSPENDED -> AgentRun.RunStatus.SUSPENDED;
        };
    }

    private static ErrorInfo toErrorInfo(AgentRun.ErrorInfo entityErrorInfo) {
        if (entityErrorInfo == null) {
            return null;
        }
        return new ErrorInfo(
            entityErrorInfo.errorCode(),
            entityErrorInfo.errorMessage(),
            entityErrorInfo.errorType(),
            entityErrorInfo.context(),
            entityErrorInfo.occurredAt(),
            entityErrorInfo.stackTrace()
        );
    }

    private static AgentRun.ErrorInfo toEntityErrorInfo(ErrorInfo recordErrorInfo) {
        if (recordErrorInfo == null) {
            return null;
        }
        return new AgentRun.ErrorInfo(
            recordErrorInfo.errorCode(),
            recordErrorInfo.errorMessage(),
            recordErrorInfo.errorType(),
            recordErrorInfo.context(),
            recordErrorInfo.occurredAt(),
            recordErrorInfo.stackTrace()
        );
    }

    /**
     * Creates a new record with updated fields using a builder-like pattern.
     */
    public AgentRunRecord withStatus(RunStatus newStatus) {
        return new AgentRunRecord(
            runId, agentId, tenantId, sessionId, correlationId,
            newStatus, input, output, toolCalls, memoryWrites,
            approvalRequests, policyDecisions, runTrace, metrics,
            errorInfo, governanceMetadata, createdAt, updatedAt,
            startedAt, completedAt, createdBy, completedBy,
            sideEffectDeclaration, grantedApprovals, compensationExecuted,
            compensationResult, traceId, policyDecision
        );
    }

    public AgentRunRecord withOutput(Map<String, Object> newOutput) {
        return new AgentRunRecord(
            runId, agentId, tenantId, sessionId, correlationId,
            status, input, newOutput, toolCalls, memoryWrites,
            approvalRequests, policyDecisions, runTrace, metrics,
            errorInfo, governanceMetadata, createdAt, updatedAt,
            startedAt, completedAt, createdBy, completedBy,
            sideEffectDeclaration, grantedApprovals, compensationExecuted,
            compensationResult, traceId, policyDecision
        );
    }

    public AgentRunRecord withCompletedAt(Instant newCompletedAt) {
        return new AgentRunRecord(
            runId, agentId, tenantId, sessionId, correlationId,
            status, input, output, toolCalls, memoryWrites,
            approvalRequests, policyDecisions, runTrace, metrics,
            errorInfo, governanceMetadata, createdAt, updatedAt,
            startedAt, newCompletedAt, createdBy, completedBy,
            sideEffectDeclaration, grantedApprovals, compensationExecuted,
            compensationResult, traceId, policyDecision
        );
    }

    public AgentRunRecord withSideEffectDeclaration(Map<String, Object> newDeclaration) {
        return new AgentRunRecord(
            runId, agentId, tenantId, sessionId, correlationId,
            status, input, output, toolCalls, memoryWrites,
            approvalRequests, policyDecisions, runTrace, metrics,
            errorInfo, governanceMetadata, createdAt, updatedAt,
            startedAt, completedAt, createdBy, completedBy,
            newDeclaration, grantedApprovals, compensationExecuted,
            compensationResult, traceId, policyDecision
        );
    }

    public AgentRunRecord withGrantedApprovals(Set<String> newApprovals) {
        return new AgentRunRecord(
            runId, agentId, tenantId, sessionId, correlationId,
            status, input, output, toolCalls, memoryWrites,
            approvalRequests, policyDecisions, runTrace, metrics,
            errorInfo, governanceMetadata, createdAt, updatedAt,
            startedAt, completedAt, createdBy, completedBy,
            sideEffectDeclaration, newApprovals, compensationExecuted,
            compensationResult, traceId, policyDecision
        );
    }

    public AgentRunRecord withCompensationExecuted(boolean newCompensationExecuted) {
        return new AgentRunRecord(
            runId, agentId, tenantId, sessionId, correlationId,
            status, input, output, toolCalls, memoryWrites,
            approvalRequests, policyDecisions, runTrace, metrics,
            errorInfo, governanceMetadata, createdAt, updatedAt,
            startedAt, completedAt, createdBy, completedBy,
            sideEffectDeclaration, grantedApprovals, newCompensationExecuted,
            compensationResult, traceId, policyDecision
        );
    }

    public AgentRunRecord withCompensationResult(String newCompensationResult) {
        return new AgentRunRecord(
            runId, agentId, tenantId, sessionId, correlationId,
            status, input, output, toolCalls, memoryWrites,
            approvalRequests, policyDecisions, runTrace, metrics,
            errorInfo, governanceMetadata, createdAt, updatedAt,
            startedAt, completedAt, createdBy, completedBy,
            sideEffectDeclaration, grantedApprovals, compensationExecuted,
            newCompensationResult, traceId, policyDecision
        );
    }
}
