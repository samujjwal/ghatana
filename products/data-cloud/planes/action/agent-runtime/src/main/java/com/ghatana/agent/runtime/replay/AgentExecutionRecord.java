package com.ghatana.agent.runtime.replay;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable replay record for a single agent execution.
 *
 * @doc.type record
 * @doc.purpose Captures model, prompt, retrieval, tool, and output snapshots for replay-safe agent execution
 * @doc.layer agent-runtime
 * @doc.pattern AuditRecord
 */
public record AgentExecutionRecord(
        String executionId,
        String agentRef,
        String tenantId,
        String correlationId,
        String operatorId,
        String modelRef,
        AgentPromptSnapshot promptSnapshot,
        AgentRetrievalSnapshot retrievalSnapshot,
        List<AgentToolCallRecord> toolCalls,
        AgentOutputRecord outputRecord,
        AgentReplayPolicy replayPolicy,
        Instant startedAt,
        Instant completedAt,
        Map<String, String> metadata
) {

    public AgentExecutionRecord {
        requireNonBlank(executionId, "executionId");
        requireNonBlank(agentRef, "agentRef");
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(correlationId, "correlationId");
        requireNonBlank(operatorId, "operatorId");
        requireNonBlank(modelRef, "modelRef");
        Objects.requireNonNull(promptSnapshot, "promptSnapshot");
        Objects.requireNonNull(retrievalSnapshot, "retrievalSnapshot");
        Objects.requireNonNull(outputRecord, "outputRecord");
        Objects.requireNonNull(replayPolicy, "replayPolicy");
        Objects.requireNonNull(startedAt, "startedAt");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean hasRecordedOutput() {
        return replayPolicy.mode() == AgentReplayMode.RECORDED_OUTPUT && outputRecord != null;
    }

    private static void requireNonBlank(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
