/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import com.ghatana.yappc.api.PlatformExecution;

/**
 * Typed client for platform execution operations.
 * Handles communication with Data Cloud+AEP execution services.
 *
 * @doc.type interface
 * @doc.purpose Typed client for platform execution operations
 * @doc.layer product
 * @doc.pattern Client
 */
public interface PlatformExecutionClient {

    /**
     * Executes a platform operation (agent/intelligence execution).
     *
     * @param request The execution request
     * @return PlatformExecution containing the execution result
     */
    PlatformExecution execute(PlatformExecution.ExecutionRequest request);

    /**
     * Gets execution status.
     *
     * @param executionId The execution ID
     * @return PlatformExecution containing the current status
     */
    PlatformExecution getExecutionStatus(String executionId);
}
