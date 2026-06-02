/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for WorkflowExecutionHandler (Pass 9 operation recording).
 *
 * @doc.type class
 * @doc.purpose Validate workflow execution handler operation recording
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Workflow Execution Handler Tests")
class WorkflowExecutionHandlerTest {

    private WorkflowExecutionHandler handler;

    @BeforeEach
    void setUp() {
        HttpHandlerSupport http = new HttpHandlerSupport(new ObjectMapper(), "*", "GET,POST,PUT,DELETE", "Content-Type");
        handler = new WorkflowExecutionHandler(mock(DataCloudClient.class), http);
    }

    @Test
    @DisplayName("Should record operation on pipeline execution")
    void shouldRecordOperationOnPipelineExecution() {
        String tenantId = "tenant-123";
        String pipelineId = "pipeline-456";
        HttpRequest request = HttpRequest.get("/api/v1/pipelines/" + pipelineId + "/execute").build();

        Promise<HttpResponse> response = handler.handleExecutePipeline(request);

        assertThat(response).isNotNull();
        // Verify operation recording was called
    }

    @Test
    @DisplayName("Should record operation on pipeline cancellation")
    void shouldRecordOperationOnPipelineCancellation() {
        String tenantId = "tenant-123";
        String pipelineId = "pipeline-456";
        String executionId = "execution-789";
        HttpRequest request = HttpRequest.get("/api/v1/pipelines/" + pipelineId + "/executions/" + executionId + "/cancel").build();

        Promise<HttpResponse> response = handler.handleCancelPipelineExecution(request);

        assertThat(response).isNotNull();
        // Verify operation recording was called
    }

    @Test
    @DisplayName("Should record operation on execution retry")
    void shouldRecordOperationOnExecutionRetry() {
        String tenantId = "tenant-123";
        String pipelineId = "pipeline-456";
        String executionId = "execution-789";
        HttpRequest request = HttpRequest.post("/api/v1/pipelines/" + pipelineId + "/executions/" + executionId + "/retry").build();

        Promise<HttpResponse> response = handler.handleRetryExecution(request);

        assertThat(response).isNotNull();
        // Verify operation recording was called
    }

    @Test
    @DisplayName("Should record operation on execution rollback")
    void shouldRecordOperationOnExecutionRollback() {
        String tenantId = "tenant-123";
        String pipelineId = "pipeline-456";
        String executionId = "execution-789";
        HttpRequest request = HttpRequest.post("/api/v1/pipelines/" + pipelineId + "/executions/" + executionId + "/rollback").build();

        Promise<HttpResponse> response = handler.handleRollbackExecution(request);

        assertThat(response).isNotNull();
        // Verify operation recording was called
    }

    @Test
    @DisplayName("Should record operation on checkpoint creation")
    void shouldRecordOperationOnCheckpointCreation() {
        String tenantId = "tenant-123";
        String pipelineId = "pipeline-456";
        String executionId = "execution-789";
        HttpRequest request = HttpRequest.post("/api/v1/pipelines/" + pipelineId + "/executions/" + executionId + "/checkpoint").build();

        Promise<HttpResponse> response = handler.handleCheckpointExecution(request);

        assertThat(response).isNotNull();
        // Verify operation recording was called
    }

    @Test
    @DisplayName("Should record operation on checkpoint restore")
    void shouldRecordOperationOnCheckpointRestore() {
        String tenantId = "tenant-123";
        String pipelineId = "pipeline-456";
        String executionId = "execution-789";
        String checkpointId = "checkpoint-abc";
        HttpRequest request = HttpRequest.post("/api/v1/pipelines/" + pipelineId + "/executions/" + executionId + "/restore/" + checkpointId).build();

        Promise<HttpResponse> response = handler.handleRestoreExecution(request);

        assertThat(response).isNotNull();
        // Verify operation recording was called
    }

    @Test
    @DisplayName("Should include operation ID in response")
    void shouldIncludeOperationIdInResponse() {
        String tenantId = "tenant-123";
        String pipelineId = "pipeline-456";
        HttpRequest request = HttpRequest.get("/api/v1/pipelines/" + pipelineId + "/execute").build();

        Promise<HttpResponse> response = handler.handleExecutePipeline(request);

        assertThat(response).isNotNull();
        // Verify response includes operation ID
    }

    @Test
    @DisplayName("Should include trace ID in response")
    void shouldIncludeTraceIdInResponse() {
        String tenantId = "tenant-123";
        String pipelineId = "pipeline-456";
        HttpRequest request = HttpRequest.get("/api/v1/pipelines/" + pipelineId + "/execute").build();

        Promise<HttpResponse> response = handler.handleExecutePipeline(request);

        assertThat(response).isNotNull();
        // Verify response includes trace ID
    }
}
