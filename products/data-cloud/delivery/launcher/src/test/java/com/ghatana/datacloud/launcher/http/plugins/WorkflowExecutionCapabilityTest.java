/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.plugins;

import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability.ExecutionSnapshot;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability.NodeSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for WorkflowExecutionCapability records
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("WorkflowExecutionCapability")
class WorkflowExecutionCapabilityTest {

    private static ExecutionSnapshot snapshot(String status, String startedAt, String completedAt, Integer duration, String error) {
        return new ExecutionSnapshot(
            "exec-1",
            "tenant-1",
            "workflow-1",
            "Test Workflow",
            status,
            50,
            startedAt,
            completedAt,
            duration,
            List.<NodeSnapshot>of(),
            null,
            error,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns true for COMPLETED")
    void isTerminal_returnsTrueForCompleted() {
        ExecutionSnapshot value = snapshot("COMPLETED", "2026-04-28T10:00:00Z", "2026-04-28T10:05:00Z", 300, null);
        assertThat(value.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns true for FAILED")
    void isTerminal_returnsTrueForFailed() {
        ExecutionSnapshot value = snapshot("FAILED", "2026-04-28T10:00:00Z", null, null, "Error occurred");
        assertThat(value.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns true for CANCELLED")
    void isTerminal_returnsTrueForCancelled() {
        ExecutionSnapshot value = snapshot("CANCELLED", "2026-04-28T10:00:00Z", "2026-04-28T10:02:00Z", 120, null);
        assertThat(value.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns false for RUNNING")
    void isTerminal_returnsFalseForRunning() {
        ExecutionSnapshot value = snapshot("RUNNING", "2026-04-28T10:00:00Z", null, null, null);
        assertThat(value.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns false for PENDING")
    void isTerminal_returnsFalseForPending() {
        ExecutionSnapshot value = snapshot("PENDING", null, null, null, null);
        assertThat(value.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("NodeSnapshot record constructor works")
    void nodeSnapshot_constructor() {
        NodeSnapshot snapshot = new NodeSnapshot(
            "node-1",
            "Test Node",
            "COMPLETED",
            "2026-04-28T10:01:00Z",
            "2026-04-28T10:02:00Z",
            60,
            null,
            "result"
        );
        assertThat(snapshot.nodeId()).isEqualTo("node-1");
        assertThat(snapshot.nodeName()).isEqualTo("Test Node");
        assertThat(snapshot.state()).isEqualTo("COMPLETED");
        assertThat(snapshot.output()).isEqualTo("result");
    }
}
