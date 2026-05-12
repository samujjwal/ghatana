/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform.adapter;

import com.ghatana.yappc.api.PlatformExecution;
import com.ghatana.yappc.services.platform.PlatformExecutionClient;
import com.ghatana.yappc.services.platform.PlatformResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

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
        if (simulateFailure) {
            return new PlatformExecution(
                request.executionId(),
                "FAILED",
                "Simulated failure for testing",
                PlatformResponseMetadata.failure("Simulated failure"),
                request.requestData(),
                Map.of(),
                request.requestedAt(),
                java.time.Instant.now()
            );
        }

        if (simulateDegraded) {
            return new PlatformExecution(
                request.executionId(),
                "DEGRADED",
                "Simulated degraded state for testing",
                PlatformResponseMetadata.degraded("Simulated degraded"),
                request.requestData(),
                Map.of(),
                request.requestedAt(),
                null
            );
        }

        PlatformExecution execution = new PlatformExecution(
            request.executionId(),
            "COMPLETED",
            "Fake execution completed successfully",
            PlatformResponseMetadata.success(),
            request.requestData(),
            Map.of("source", "FAKE"),
            request.requestedAt(),
            java.time.Instant.now()
        );

        executions.put(request.executionId(), execution);
        return execution;
    }

    @Override
    public PlatformExecution getExecutionStatus(String executionId) {
        if (simulateFailure) {
            return new PlatformExecution(
                executionId,
                "FAILED",
                "Simulated failure for testing",
                PlatformResponseMetadata.failure("Simulated failure"),
                Map.of(),
                Map.of(),
                java.time.Instant.now(),
                java.time.Instant.now()
            );
        }

        if (simulateDegraded) {
            return new PlatformExecution(
                executionId,
                "UNKNOWN",
                "Simulated degraded state for testing",
                PlatformResponseMetadata.degraded("Simulated degraded"),
                Map.of(),
                Map.of(),
                java.time.Instant.now(),
                null
            );
        }

        return executions.getOrDefault(executionId, new PlatformExecution(
            executionId,
            "NOT_FOUND",
            "Execution not found",
            PlatformResponseMetadata.failure("Execution not found"),
            Map.of(),
            Map.of(),
            java.time.Instant.now(),
            null
        ));
    }
}
