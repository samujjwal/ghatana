/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform.adapter;

import com.ghatana.yappc.api.PlatformExecution;
import com.ghatana.yappc.services.platform.PlatformExecutionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test fake platform execution client for testing purposes only.
 * This should only be used in test source sets, never in production code.
 *
 * @doc.type class
 * @doc.purpose Test fake platform execution client for testing
 * @doc.layer testing
 * @doc.pattern Test Double
 */
public final class FakePlatformExecutionClient implements PlatformExecutionClient {

    private static final Logger log = LoggerFactory.getLogger(FakePlatformExecutionClient.class);

    private final Map<String, PlatformExecution> executions = new HashMap<>();
    private boolean simulateFailure = false;
    private boolean simulateDegraded = false;

    public FakePlatformExecutionClient() {
        log.warn("FakePlatformExecutionClient is for testing only - never use in production");
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void setSimulateDegraded(boolean simulateDegraded) {
        this.simulateDegraded = simulateDegraded;
    }

    public void clearExecutions() {
        executions.clear();
    }

    public PlatformExecution getExecution(String executionId) {
        return executions.get(executionId);
    }

    @Override
    public PlatformExecution execute(PlatformExecution.ExecutionRequest request) {
        String executionId = java.util.UUID.randomUUID().toString();
        String projectId = "test-project";
        String workspaceId = "test-workspace";
        String tenantId = "test-tenant";
        
        if (simulateFailure) {
            return new PlatformExecution(
                executionId,
                projectId,
                workspaceId,
                tenantId,
                request,
                new PlatformExecution.ExecutionResponse(
                    "response-" + executionId,
                    request.executionType(),
                    Map.of(),
                    new PlatformExecution.ExecutionMetrics(0, 0, 0.0, Map.of()),
                    List.of(),
                    "Simulated failure for testing"
                ),
                new PlatformExecution.ExecutionMetadata(
                    "trace-" + executionId,
                    "session-" + executionId,
                    "test-user",
                    "TEST_PLATFORM",
                    Set.of("test"),
                    Map.of()
                ),
                new PlatformExecution.ExecutionStatus(
                    PlatformExecution.ExecutionStatus.ExecutionState.FAILED,
                    "Simulated failure for testing",
                    0,
                    java.time.Instant.now()
                ),
                java.time.Instant.now(),
                java.time.Instant.now()
            );
        }

        if (simulateDegraded) {
            return new PlatformExecution(
                executionId,
                projectId,
                workspaceId,
                tenantId,
                request,
                new PlatformExecution.ExecutionResponse(
                    "response-" + executionId,
                    request.executionType(),
                    Map.of(),
                    new PlatformExecution.ExecutionMetrics(0, 0, 0.0, Map.of()),
                    List.of(),
                    "Simulated degraded state for testing"
                ),
                new PlatformExecution.ExecutionMetadata(
                    "trace-" + executionId,
                    "session-" + executionId,
                    "test-user",
                    "TEST_PLATFORM",
                    Set.of("test"),
                    Map.of()
                ),
                new PlatformExecution.ExecutionStatus(
                    PlatformExecution.ExecutionStatus.ExecutionState.RUNNING,
                    "Simulated degraded state for testing",
                    50,
                    null
                ),
                java.time.Instant.now(),
                java.time.Instant.now()
            );
        }

        PlatformExecution execution = new PlatformExecution(
            executionId,
            projectId,
            workspaceId,
            tenantId,
            request,
            new PlatformExecution.ExecutionResponse(
                "response-" + executionId,
                request.executionType(),
                Map.of("result", "success"),
                new PlatformExecution.ExecutionMetrics(100, 1000, 0.01, Map.of()),
                List.of(),
                null
            ),
            new PlatformExecution.ExecutionMetadata(
                "trace-" + executionId,
                "session-" + executionId,
                "test-user",
                "TEST_PLATFORM",
                Set.of("test", "fake"),
                Map.of("source", "FAKE")
            ),
            new PlatformExecution.ExecutionStatus(
                PlatformExecution.ExecutionStatus.ExecutionState.COMPLETED,
                "Fake execution completed successfully",
                100,
                java.time.Instant.now()
            ),
            java.time.Instant.now(),
            java.time.Instant.now()
        );

        executions.put(executionId, execution);
        return execution;
    }

    @Override
    public PlatformExecution getExecutionStatus(String executionId) {
        if (simulateFailure) {
            return new PlatformExecution(
                executionId,
                "test-project",
                "test-workspace",
                "test-tenant",
                new PlatformExecution.ExecutionRequest(
                    "test-type",
                    "test-model",
                    "1.0",
                    Map.of(),
                    Map.of(),
                    "test-correlation"
                ),
                new PlatformExecution.ExecutionResponse(
                    "response-" + executionId,
                    "test-type",
                    Map.of(),
                    new PlatformExecution.ExecutionMetrics(0, 0, 0.0, Map.of()),
                    List.of(),
                    "Simulated failure for testing"
                ),
                new PlatformExecution.ExecutionMetadata(
                    "trace-" + executionId,
                    "session-" + executionId,
                    "test-user",
                    "TEST_PLATFORM",
                    Set.of("test"),
                    Map.of()
                ),
                new PlatformExecution.ExecutionStatus(
                    PlatformExecution.ExecutionStatus.ExecutionState.FAILED,
                    "Simulated failure for testing",
                    0,
                    java.time.Instant.now()
                ),
                java.time.Instant.now(),
                java.time.Instant.now()
            );
        }

        if (simulateDegraded) {
            return new PlatformExecution(
                executionId,
                "test-project",
                "test-workspace",
                "test-tenant",
                new PlatformExecution.ExecutionRequest(
                    "test-type",
                    "test-model",
                    "1.0",
                    Map.of(),
                    Map.of(),
                    "test-correlation"
                ),
                new PlatformExecution.ExecutionResponse(
                    "response-" + executionId,
                    "test-type",
                    Map.of(),
                    new PlatformExecution.ExecutionMetrics(0, 0, 0.0, Map.of()),
                    List.of(),
                    "Simulated degraded state for testing"
                ),
                new PlatformExecution.ExecutionMetadata(
                    "trace-" + executionId,
                    "session-" + executionId,
                    "test-user",
                    "TEST_PLATFORM",
                    Set.of("test"),
                    Map.of()
                ),
                new PlatformExecution.ExecutionStatus(
                    PlatformExecution.ExecutionStatus.ExecutionState.RUNNING,
                    "Simulated degraded state for testing",
                    50,
                    null
                ),
                java.time.Instant.now(),
                java.time.Instant.now()
            );
        }

        return executions.getOrDefault(executionId, new PlatformExecution(
            executionId,
            "test-project",
            "test-workspace",
            "test-tenant",
            new PlatformExecution.ExecutionRequest(
                "test-type",
                "test-model",
                "1.0",
                Map.of(),
                Map.of(),
                "test-correlation"
            ),
            new PlatformExecution.ExecutionResponse(
                "response-" + executionId,
                "test-type",
                Map.of(),
                new PlatformExecution.ExecutionMetrics(0, 0, 0.0, Map.of()),
                List.of(),
                "Execution not found"
            ),
            new PlatformExecution.ExecutionMetadata(
                "trace-" + executionId,
                "session-" + executionId,
                "test-user",
                "TEST_PLATFORM",
                Set.of("test"),
                Map.of()
            ),
            new PlatformExecution.ExecutionStatus(
                PlatformExecution.ExecutionStatus.ExecutionState.FAILED,
                "Execution not found",
                0,
                java.time.Instant.now()
            ),
            java.time.Instant.now(),
            java.time.Instant.now()
        ));
    }
}
