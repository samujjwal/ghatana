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

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns true for COMPLETED")
    void isTerminal_returnsTrueForCompleted() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
            "exec-1",
            "tenant-1",
            "workflow-1",
            "Test Workflow",
            "COMPLETED",
            100,
            "2026-04-28T10:00:00Z",
            "2026-04-28T10:05:00Z",
            300,
            List.of(),
            null,
            null
        );
        assertThat(snapshot.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns true for FAILED")
    void isTerminal_returnsTrueForFailed() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
            "exec-1",
            "tenant-1",
            "workflow-1",
            "Test Workflow",
            "FAILED",
            50,
            "2026-04-28T10:00:00Z",
            null,
            null,
            List.of(),
            null,
            "Error occurred"
        );
        assertThat(snapshot.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns true for CANCELLED")
    void isTerminal_returnsTrueForCancelled() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
            "exec-1",
            "tenant-1",
            "workflow-1",
            "Test Workflow",
            "CANCELLED",
            75,
            "2026-04-28T10:00:00Z",
            "2026-04-28T10:02:00Z",
            120,
            List.of(),
            null,
            null
        );
        assertThat(snapshot.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns false for RUNNING")
    void isTerminal_returnsFalseForRunning() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
            "exec-1",
            "tenant-1",
            "workflow-1",
            "Test Workflow",
            "RUNNING",
            25,
            "2026-04-28T10:00:00Z",
            null,
            null,
            List.of(),
            null,
            null
        );
        assertThat(snapshot.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("ExecutionSnapshot isTerminal returns false for PENDING")
    void isTerminal_returnsFalseForPending() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
            "exec-1",
            "tenant-1",
            "workflow-1",
            "Test Workflow",
            "PENDING",
            0,
            null,
            null,
            null,
            List.of(),
            null,
            null
        );
        assertThat(snapshot.isTerminal()).isFalse();
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
