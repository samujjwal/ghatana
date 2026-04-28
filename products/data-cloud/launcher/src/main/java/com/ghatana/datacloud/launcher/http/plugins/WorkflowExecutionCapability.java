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

    Promise<List<ExecutionLogEntry>> getExecutionLogs(String tenantId, String executionId);

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