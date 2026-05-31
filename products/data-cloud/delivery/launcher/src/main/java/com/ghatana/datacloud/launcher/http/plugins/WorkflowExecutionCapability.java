package com.ghatana.datacloud.launcher.http.plugins;

import com.ghatana.platform.plugin.PluginCapability;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Plugin capability for workflow execution/orchestration
 * @doc.layer product
 * @doc.pattern Capability
 */
public interface WorkflowExecutionCapability extends PluginCapability {

    Promise<ExecutionSnapshot> execute(String tenantId, String workflowId, Map<String, Object> input);

    Promise<List<ExecutionSnapshot>> listExecutions(String tenantId, String workflowId);

    Promise<Optional<ExecutionSnapshot>> getExecution(String tenantId, String executionId);

    Promise<ExecutionSnapshot> cancelExecution(String tenantId, String executionId);

    Promise<ExecutionSnapshot> retryExecution(String tenantId, String executionId);

    default Promise<ExecutionSnapshot> rollbackExecution(String tenantId, String executionId, Map<String, Object> rollbackData) {
        return cancelExecution(tenantId, executionId);
    }

    default Promise<ExecutionSnapshot> restoreExecution(String tenantId, String executionId, String checkpointId) {
        return retryExecution(tenantId, executionId);
    }

    default Promise<ExecutionCheckpoint> checkpointExecution(String tenantId, String executionId, Map<String, Object> checkpointData) {
        return Promise.of(new ExecutionCheckpoint(
            executionId + ":" + java.util.UUID.randomUUID(),
            tenantId,
            executionId,
            java.time.Instant.now().toString(),
            checkpointData == null ? Map.of() : Map.copyOf(checkpointData)
        ));
    }

    Promise<List<ExecutionLogEntry>> getExecutionLogs(String tenantId, String executionId);

    default Promise<Map<String, NodeSnapshot>> getNodeStatus(String tenantId, String executionId) {
        return getExecution(tenantId, executionId).map(optional -> optional
            .map(snapshot -> snapshot.nodeStatuses().stream()
                .collect(java.util.stream.Collectors.toMap(NodeSnapshot::nodeId, node -> node)))
            .orElse(Map.of()));
    }

    default CapabilityState capabilityState() {
        return new CapabilityState(
            "workflow-execution",
            "LIVE",
            true,
            "durable execution snapshots, logs, retry, cancel, rollback, checkpoint, restore, and node status are supported when a runtime plugin is wired"
        );
    }

    record CapabilityState(
        String capabilityId,
        String state,
        boolean available,
        String detail
    ) {}

    record ExecutionCheckpoint(
        String checkpointId,
        String tenantId,
        String executionId,
        String savedAt,
        Map<String, Object> data
    ) {}

    record NodeSnapshot(
        String nodeId,
        String nodeName,
        String state,
        String startedAt,
        String completedAt,
        Integer duration,
        String error,
        Object output
    ) {}

    record ExecutionSnapshot(
        String id,
        String tenantId,
        String workflowId,
        String workflowName,
        String status,
        int progress,
        String startedAt,
        String completedAt,
        Integer duration,
        List<NodeSnapshot> nodeStatuses,
        Object output,
        String error
    ) {
        public boolean isTerminal() {
            return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
        }
    }

    record ExecutionLogEntry(
        String timestamp,
        String level,
        String message,
        String nodeId,
        Map<String, Object> metadata
    ) {}
}
