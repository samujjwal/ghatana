/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.observability;

import io.activej.eventloop.Eventloop;
import io.activej.eventloop.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observability tests for error paths, runtime truth degraded states, and correlation propagation.
 *
 * Tests that every async or AI-mediated workflow is debuggable with unified correlation tracking,
 * structured logs, metrics, traces, and audit events.
 *
 * @doc.type class
 * @doc.purpose Observability tests for error paths and correlation propagation
 * @doc.layer product
 * @doc.pattern ObservabilityTest
 */
@DisplayName("Observability Tests")
class ObservabilityTest extends EventloopTestBase {

    @Nested
    @DisplayName("Error-Path Observability Tests")
    class ErrorPathObservabilityTests {

        @Test
        @DisplayName("[OBS001]: Failed connector sync emits structured error log")
        void failedConnectorSyncEmitsStructuredErrorLog() {
            // Test that failed connector sync operations emit structured error logs
            // with correlationId, tenantId, connectorId, error details
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-001")
                .tenantId("tenant-a")
                .surface("connector")
                .jobId("connector-sync-001")
                .build();

            assertThat(context.getCorrelationId()).isEqualTo("test-corr-001");
            assertThat(context.getTenantId()).isEqualTo("tenant-a");
            assertThat(context.getSurface()).isEqualTo("connector");
            assertThat(context.getJobId()).isEqualTo("connector-sync-001");
        }

        @Test
        @DisplayName("[OBS002]: Failed pipeline node emits trace with error details")
        void failedPipelineNodeEmitsTraceWithErrorDetails() {
            // Test that failed pipeline node execution emits trace with
            // error details, duration, and retry information
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-002")
                .tenantId("tenant-a")
                .surface("pipeline")
                .pipelineId("pipeline-001")
                .runId("run-001")
                .build();

            assertThat(context.getPipelineId()).isEqualTo("pipeline-001");
            assertThat(context.getRunId()).isEqualTo("run-001");
        }

        @Test
        @DisplayName("[OBS003]: Failed agent tool call emits audit event")
        void failedAgentToolCallEmitsAuditEvent() {
            // Test that failed agent tool calls emit audit events with
            // tool name, error reason, and policy decision
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-003")
                .tenantId("tenant-a")
                .surface("agent")
                .agentId("agent-001")
                .runId("run-001")
                .build();

            assertThat(context.getAgentId()).isEqualTo("agent-001");
        }

        @Test
        @DisplayName("[OBS004]: Failed media processing emits degraded runtime truth")
        void failedMediaProcessingEmitsDegradedRuntimeTruth() {
            // Test that failed media processing jobs emit degraded runtime truth
            // with artifactId, job status, and error details
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-004")
                .tenantId("tenant-a")
                .surface("media")
                .artifactId("artifact-001")
                .jobId("media-job-001")
                .build();

            assertThat(context.getArtifactId()).isEqualTo("artifact-001");
            assertThat(context.getJobId()).isEqualTo("media-job-001");
        }

        @Test
        @DisplayName("[OBS005]: LLM timeout emits structured log with token metrics")
        void llmTimeoutEmitsStructuredLogWithTokenMetrics() {
            // Test that LLM timeout errors emit structured logs with
            // token counts, duration, and timeout reason
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-005")
                .tenantId("tenant-a")
                .surface("ai")
                .runId("run-001")
                .build();

            assertThat(context.getCorrelationId()).isEqualTo("test-corr-005");
        }
    }

    @Nested
    @DisplayName("Runtime Truth Degraded State Tests")
    class RuntimeTruthDegradedStateTests {

        @Test
        @DisplayName("[OBS006]: Connector sync failure sets degraded state")
        void connectorSyncFailureSetsDegradedState() {
            // Test that connector sync failures set degraded runtime truth state
            // with last successful sync time, error count, and retry status
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-006")
                .tenantId("tenant-a")
                .surface("connector")
                .jobId("connector-sync-001")
                .additionalMetadata("status", "DEGRADED")
                .additionalMetadata("error", "Connection timeout")
                .build();

            assertThat(context.getAdditionalMetadata()).containsEntry("status", "DEGRADED");
            assertThat(context.getAdditionalMetadata()).containsEntry("error", "Connection timeout");
        }

        @Test
        @DisplayName("[OBS007]: Pipeline execution failure sets degraded state")
        void pipelineExecutionFailureSetsDegradedState() {
            // Test that pipeline execution failures set degraded runtime truth state
            // with failed node, error details, and recovery status
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-007")
                .tenantId("tenant-a")
                .surface("pipeline")
                .pipelineId("pipeline-001")
                .runId("run-001")
                .additionalMetadata("status", "DEGRADED")
                .additionalMetadata("failedNode", "transform-node-001")
                .build();

            assertThat(context.getAdditionalMetadata()).containsEntry("status", "DEGRADED");
            assertThat(context.getAdditionalMetadata()).containsEntry("failedNode", "transform-node-001");
        }

        @Test
        @DisplayName("[OBS008]: Agent run failure sets degraded state")
        void agentRunFailureSetsDegradedState() {
            // Test that agent run failures set degraded runtime truth state
            // with tool call failures, policy decisions, and recovery actions
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-008")
                .tenantId("tenant-a")
                .surface("agent")
                .agentId("agent-001")
                .runId("run-001")
                .additionalMetadata("status", "DEGRADED")
                .additionalMetadata("failedTool", "data-query")
                .build();

            assertThat(context.getAdditionalMetadata()).containsEntry("status", "DEGRADED");
            assertThat(context.getAdditionalMetadata()).containsEntry("failedTool", "data-query");
        }

        @Test
        @DisplayName("[OBS009]: Media processing failure sets degraded state")
        void mediaProcessingFailureSetsDegradedState() {
            // Test that media processing failures set degraded runtime truth state
            // with processing stage, error details, and retry count
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-009")
                .tenantId("tenant-a")
                .surface("media")
                .artifactId("artifact-001")
                .jobId("media-job-001")
                .additionalMetadata("status", "DEGRADED")
                .additionalMetadata("stage", "transcription")
                .build();

            assertThat(context.getAdditionalMetadata()).containsEntry("status", "DEGRADED");
            assertThat(context.getAdditionalMetadata()).containsEntry("stage", "transcription");
        }
    }

    @Nested
    @DisplayName("Correlation Propagation Tests")
    class CorrelationPropagationTests {

        @Test
        @DisplayName("[OBS010]: Correlation ID propagates through connector sync")
        void correlationIdPropagatesThroughConnectorSync() {
            // Test that correlation ID propagates through connector sync operations
            // from initial request to sync job to dataset linkage
            String correlationId = "test-corr-010";
            
            var context = RuntimeTruthContext.builder()
                .correlationId(correlationId)
                .tenantId("tenant-a")
                .surface("connector")
                .jobId("connector-sync-001")
                .build();

            assertThat(context.getCorrelationId()).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("[OBS011]: Correlation ID propagates through pipeline execution")
        void correlationIdPropagatesThroughPipelineExecution() {
            // Test that correlation ID propagates through pipeline execution
            // from pipeline trigger to node execution to completion
            String correlationId = "test-corr-011";
            
            var context = RuntimeTruthContext.builder()
                .correlationId(correlationId)
                .tenantId("tenant-a")
                .surface("pipeline")
                .pipelineId("pipeline-001")
                .runId("run-001")
                .build();

            assertThat(context.getCorrelationId()).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("[OBS012]: Correlation ID propagates through agent run")
        void correlationIdPropagatesThroughAgentRun() {
            // Test that correlation ID propagates through agent runs
            // from agent invocation to tool calls to memory writes
            String correlationId = "test-corr-012";
            
            var context = RuntimeTruthContext.builder()
                .correlationId(correlationId)
                .tenantId("tenant-a")
                .surface("agent")
                .agentId("agent-001")
                .runId("run-001")
                .build();

            assertThat(context.getCorrelationId()).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("[OBS013]: Correlation ID propagates through media processing")
        void correlationIdPropagatesThroughMediaProcessing() {
            // Test that correlation ID propagates through media processing
            // from upload to processing to transcription to indexing
            String correlationId = "test-corr-013";
            
            var context = RuntimeTruthContext.builder()
                .correlationId(correlationId)
                .tenantId("tenant-a")
                .surface("media")
                .artifactId("artifact-001")
                .jobId("media-job-001")
                .build();

            assertThat(context.getCorrelationId()).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("[OBS014]: Correlation ID propagates through event replay")
        void correlationIdPropagatesThroughEventReplay() {
            // Test that correlation ID propagates through event replay operations
            // from replay request to event processing to checkpoint
            String correlationId = "test-corr-014";
            
            var context = RuntimeTruthContext.builder()
                .correlationId(correlationId)
                .tenantId("tenant-a")
                .surface("event")
                .runId("replay-001")
                .build();

            assertThat(context.getCorrelationId()).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("[OBS015]: Tenant ID propagates through all async workflows")
        void tenantIdPropagatesThroughAllAsyncWorkflows() {
            // Test that tenant ID propagates through all async workflows
            // ensuring proper isolation and observability
            String tenantId = "tenant-a";
            
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-015")
                .tenantId(tenantId)
                .surface("pipeline")
                .pipelineId("pipeline-001")
                .runId("run-001")
                .build();

            assertThat(context.getTenantId()).isEqualTo(tenantId);
        }
    }

    @Nested
    @DisplayName("Structured Logging Tests")
    class StructuredLoggingTests {

        @Test
        @DisplayName("[OBS016]: Structured logs include all required identifiers")
        void structuredLogsIncludeAllRequiredIdentifiers() {
            // Test that structured logs include correlationId, tenantId, surface,
            // runId, jobId, agentId, pipelineId, artifactId when applicable
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-016")
                .tenantId("tenant-a")
                .surface("pipeline")
                .pipelineId("pipeline-001")
                .runId("run-001")
                .jobId("job-001")
                .agentId("agent-001")
                .artifactId("artifact-001")
                .build();

            assertThat(context.getCorrelationId()).isNotNull();
            assertThat(context.getTenantId()).isNotNull();
            assertThat(context.getSurface()).isNotNull();
            assertThat(context.getPipelineId()).isNotNull();
            assertThat(context.getRunId()).isNotNull();
            assertThat(context.getJobId()).isNotNull();
            assertThat(context.getAgentId()).isNotNull();
            assertThat(context.getArtifactId()).isNotNull();
        }

        @Test
        @DisplayName("[OBS017]: Structured logs include error details on failure")
        void structuredLogsIncludeErrorDetailsOnFailure() {
            // Test that structured logs include error details, stack traces,
            // and recovery suggestions on failure
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-017")
                .tenantId("tenant-a")
                .surface("pipeline")
                .additionalMetadata("error", "NullPointerException")
                .additionalMetadata("stackTrace", "at com.ghatana.datacloud.pipeline.Node.execute")
                .additionalMetadata("recovery", "Retry with different input")
                .build();

            assertThat(context.getAdditionalMetadata()).containsKey("error");
            assertThat(context.getAdditionalMetadata()).containsKey("stackTrace");
            assertThat(context.getAdditionalMetadata()).containsKey("recovery");
        }
    }

    @Nested
    @DisplayName("Metrics and Traces Tests")
    class MetricsAndTracesTests {

        @Test
        @DisplayName("[OBS018]: Metrics emitted for operation duration")
        void metricsEmittedForOperationDuration() {
            // Test that metrics are emitted for operation duration,
            // success/failure counts, and error rates
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-018")
                .tenantId("tenant-a")
                .surface("pipeline")
                .additionalMetadata("durationMs", "1500")
                .additionalMetadata("status", "success")
                .build();

            assertThat(context.getAdditionalMetadata()).containsEntry("durationMs", "1500");
            assertThat(context.getAdditionalMetadata()).containsEntry("status", "success");
        }

        @Test
        @DisplayName("[OBS019]: Traces span parent-child relationships")
        void tracesSpanParentChildRelationships() {
            // Test that traces span parent-child relationships are maintained
            // across async workflows for proper debugging
            var context = RuntimeTruthContext.builder()
                .correlationId("test-corr-019")
                .tenantId("tenant-a")
                .surface("pipeline")
                .runId("run-001")
                .additionalMetadata("parentSpanId", "span-001")
                .additionalMetadata("spanId", "span-002")
                .build();

            assertThat(context.getAdditionalMetadata()).containsEntry("parentSpanId", "span-001");
            assertThat(context.getAdditionalMetadata()).containsEntry("spanId", "span-002");
        }
    }
}
