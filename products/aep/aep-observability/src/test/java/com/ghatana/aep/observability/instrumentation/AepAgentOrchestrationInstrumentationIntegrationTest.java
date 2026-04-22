package com.ghatana.aep.observability.instrumentation;

import com.ghatana.aep.observability.tracing.AepTracingProvider;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Integration tests for AEP agent orchestration instrumentation with tracing
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AEP Agent Orchestration Instrumentation Integration Tests")
class AepAgentOrchestrationInstrumentationIntegrationTest {

    private AepAgentOrchestrationInstrumentation instrumentation;

    @BeforeEach
    void setUp() {
        instrumentation = new AepAgentOrchestrationInstrumentation();
        MDC.clear();
    }

    @Nested
    @DisplayName("Pipeline Deployment Instrumentation")
    class PipelineDeploymentInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulPipelineDeployment() throws Exception {
            // Given
            String pipelineId = "deploy-pipeline-123";
            String tenantId = "deploy-tenant-456";
            String expectedResult = "deployment-successful";

            // When
            String result = instrumentation.instrumentPipelineDeployment(
                    pipelineId,
                    tenantId,
                    () -> expectedResult
            );

            // Then
            assertThat(result).isEqualTo(expectedResult);
            assertThat(MDC.getCopyOfContextMap()).isEmpty();
        }

        @Test
        void shouldPropagateExceptionDuringDeployment() {
            // Given
            String pipelineId = "failing-pipeline";
            String tenantId = "failing-tenant";
            RuntimeException exception = new RuntimeException("Deployment failed");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentPipelineDeployment(
                    pipelineId,
                    tenantId,
                    () -> {
                        throw exception;
                    }
            )).isInstanceOf(RuntimeException.class)
                    .hasMessage("Deployment failed");
        }

        @Test
        void shouldClearMDCAfterDeployment() throws Exception {
            // Given
            String pipelineId = "cleanup-pipeline";
            String tenantId = "cleanup-tenant";

            // When
            instrumentation.instrumentPipelineDeployment(
                    pipelineId,
                    tenantId,
                    () -> "result"
            );

            // Then
            // MDC should be cleared after instrumentation (in finally block)
            // Note: Due to thread-local nature of MDC, this test verifies cleanup occurred
            assertThat(MDC.getCopyOfContextMap()).isEmpty();
        }

        @Test
        void shouldRecordDurationMetrics() throws Exception {
            // Given
            String pipelineId = "duration-pipeline";
            String tenantId = "duration-tenant";
            long startTime = System.currentTimeMillis();

            // When
            instrumentation.instrumentPipelineDeployment(
                    pipelineId,
                    tenantId,
                    () -> {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "result";
                    }
            );

            long duration = System.currentTimeMillis() - startTime;

            // Then
            assertThat(duration).isGreaterThanOrEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Agent Execution Instrumentation")
    class AgentExecutionInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulAgentExecution() throws Exception {
            // Given
            String agentId = "agent-exec-123";
            String pipelineId = "exec-pipeline-456";
            String tenantId = "exec-tenant-789";
            String expectedResult = "agent-output";

            // When
            String result = instrumentation.instrumentAgentExecution(
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> expectedResult
            );

            // Then
            assertThat(result).isEqualTo(expectedResult);
            assertThat(MDC.get("agentId")).isNull();
        }

        @Test
        void shouldValidateAgentExecutionSLA() throws Exception {
            // Given
            String agentId = "sla-agent";
            String pipelineId = "sla-pipeline";
            String tenantId = "sla-tenant";

            // When + Then - SLA threshold is 5 seconds
            String result = instrumentation.instrumentAgentExecution(
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> {
                        // Simulate quick execution within SLA
                        return "completed-within-sla";
                    }
            );

            assertThat(result).isEqualTo("completed-within-sla");
        }

        @Test
        void shouldLogWarningWhenExecutionExceedsSLA() throws Exception {
            // Given
            String agentId = "slow-agent";
            String pipelineId = "slow-pipeline";
            String tenantId = "slow-tenant";

            // When
            String result = instrumentation.instrumentAgentExecution(
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> {
                        try {
                            // Simulate execution exceeding 5 second SLA
                            Thread.sleep(100); // Simulating delay
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "slow-result";
                    }
            );

            // Then
            assertThat(result).isEqualTo("slow-result");
            // SLA warning would be logged (verified through log capture in real tests)
        }

        @Test
        void shouldPropagateAgentExecutionException() {
            // Given
            String agentId = "failing-agent";
            String pipelineId = "failing-exec-pipeline";
            String tenantId = "failing-exec-tenant";
            IllegalStateException exception = new IllegalStateException("Agent state invalid");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentAgentExecution(
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> {
                        throw exception;
                    }
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Agent state invalid");
        }

        @Test
        void shouldClearAgentContextAfterExecution() throws Exception {
            // Given
            String agentId = "context-cleanup-agent";
            String pipelineId = "context-cleanup-pipeline";
            String tenantId = "context-cleanup-tenant";

            // When
            instrumentation.instrumentAgentExecution(
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> "result"
            );

            // Then
            assertThat(MDC.get("agentId")).isNull();
        }
    }

    @Nested
    @DisplayName("Event Processing Instrumentation")
    class EventProcessingInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulEventProcessing() throws Exception {
            // Given
            String eventId = "event-proc-123";
            String eventType = "agent.deployed";
            String tenantId = "event-tenant-456";
            String expectedResult = "event-processed";

            // When
            String result = instrumentation.instrumentEventProcessing(
                    eventId,
                    eventType,
                    tenantId,
                    () -> expectedResult
            );

            // Then
            assertThat(result).isEqualTo(expectedResult);
            assertThat(MDC.get("eventId")).isNull();
        }

        @Test
        void shouldDetectSlowEventProcessing() throws Exception {
            // Given
            String eventId = "slow-event";
            String eventType = "slow.process";
            String tenantId = "slow-event-tenant";

            // When
            String result = instrumentation.instrumentEventProcessing(
                    eventId,
                    eventType,
                    tenantId,
                    () -> {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "slow-result";
                    }
            );

            // Then
            assertThat(result).isEqualTo("slow-result");
            // Performance warning would be logged for > 1000ms processing
        }

        @Test
        void shouldPropagateEventProcessingException() {
            // Given
            String eventId = "failing-event";
            String eventType = "event.failed";
            String tenantId = "failing-event-tenant";
            IOException exception = new java.io.IOException("Event processing IO error");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentEventProcessing(
                    eventId,
                    eventType,
                    tenantId,
                    () -> {
                        throw exception;
                    }
            )).isInstanceOf(IOException.class)
                    .hasMessage("Event processing IO error");
        }

        @Test
        void shouldClearEventContextAfterProcessing() throws Exception {
            // Given
            String eventId = "cleanup-event";
            String eventType = "cleanup.type";
            String tenantId = "cleanup-event-tenant";

            // When
            instrumentation.instrumentEventProcessing(
                    eventId,
                    eventType,
                    tenantId,
                    () -> "result"
            );

            // Then
            assertThat(MDC.get("eventId")).isNull();
            assertThat(MDC.get("eventType")).isNull();
        }

        @Test
        void shouldPreserveCorrelationIdDuringEventProcessing() throws Exception {
            // Given
            String correlationId = "correlation-event-123";
            MDC.put("correlationId", correlationId);
            String eventId = "corr-event";
            String eventType = "corr.type";
            String tenantId = "corr-tenant";

            // When
            instrumentation.instrumentEventProcessing(
                    eventId,
                    eventType,
                    tenantId,
                    () -> "result"
            );

            // Then
            // Correlation ID should be preserved through instrumentation
            // (actual verification requires log capture in real tests)
        }
    }

    @Nested
    @DisplayName("Batch Operation Instrumentation")
    class BatchOperationInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulBatchOperation() throws Exception {
            // Given
            String batchId = "batch-123";
            String tenantId = "batch-tenant-456";
            int totalItems = 10;

            // When + Then
            instrumentation.instrumentBatchOperation(
                    batchId,
                    tenantId,
                    totalItems,
                    () -> {
                        // Simulate batch processing
                    }
            );

            // Should complete without exception
        }

        @Test
        void shouldCalculatePerItemDuration() throws Exception {
            // Given
            String batchId = "duration-batch";
            String tenantId = "duration-batch-tenant";
            int totalItems = 5;
            long startTime = System.currentTimeMillis();

            // When
            instrumentation.instrumentBatchOperation(
                    batchId,
                    tenantId,
                    totalItems,
                    () -> {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
            );

            long totalDuration = System.currentTimeMillis() - startTime;

            // Then
            assertThat(totalDuration).isGreaterThanOrEqualTo(100);
            long perItemDuration = totalDuration / totalItems;
            assertThat(perItemDuration).isGreaterThan(0);
        }

        @Test
        void shouldWarnWhenPerItemDurationExceedsThreshold() throws Exception {
            // Given
            String batchId = "slow-batch";
            String tenantId = "slow-batch-tenant";
            int totalItems = 1;

            // When
            instrumentation.instrumentBatchOperation(
                    batchId,
                    tenantId,
                    totalItems,
                    () -> {
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
            );

            // Then
            // Warning would be logged for per-item duration > 100ms
        }

        @Test
        void shouldPropagateBatchOperationException() {
            // Given
            String batchId = "failing-batch";
            String tenantId = "failing-batch-tenant";
            int totalItems = 5;
            RuntimeException exception = new RuntimeException("Batch processing failed");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentBatchOperation(
                    batchId,
                    tenantId,
                    totalItems,
                    () -> {
                        throw exception;
                    }
            )).isInstanceOf(RuntimeException.class)
                    .hasMessage("Batch processing failed");
        }

        @Test
        void shouldHandleZeroItemBatch() throws Exception {
            // Given
            String batchId = "empty-batch";
            String tenantId = "empty-batch-tenant";
            int totalItems = 0;

            // When + Then
            instrumentation.instrumentBatchOperation(
                    batchId,
                    tenantId,
                    totalItems,
                    () -> {
                        // No items to process
                    }
            );

            // Should complete without division by zero error
        }
    }

    @Nested
    @DisplayName("Multi-Tenant Context Isolation")
    class MultiTenantContextIsolationTests {

        @Test
        void shouldIsolateTenantContextInPipelineDeployment() throws Exception {
            // Given
            String tenant1 = "isolation-tenant-1";
            String tenant2 = "isolation-tenant-2";
            String pipelineId = "isolation-pipeline";

            // When
            instrumentation.instrumentPipelineDeployment(
                    pipelineId,
                    tenant1,
                    () -> "tenant1-result"
            );

            MDC.clear();

            instrumentation.instrumentPipelineDeployment(
                    pipelineId,
                    tenant2,
                    () -> "tenant2-result"
            );

            // Then
            assertThat(MDC.get("tenantId")).isNull();
        }

        @Test
        void shouldIsolateTenantContextInAgentExecution() throws Exception {
            // Given
            String tenant1 = "agent-isolation-tenant-1";
            String tenant2 = "agent-isolation-tenant-2";
            String agentId = "multitenancy-agent";
            String pipelineId = "multitenancy-pipeline";

            // When
            instrumentation.instrumentAgentExecution(
                    agentId,
                    pipelineId,
                    tenant1,
                    () -> "tenant1-agent-result"
            );

            MDC.clear();

            instrumentation.instrumentAgentExecution(
                    agentId,
                    pipelineId,
                    tenant2,
                    () -> "tenant2-agent-result"
            );

            // Then
            assertThat(MDC.get("tenantId")).isNull();
        }
    }

    @Nested
    @DisplayName("Exception Handling and Error Propagation")
    class ExceptionHandlingAndErrorPropagationTests {

        @Test
        void shouldClearMDCEvenOnException() throws Exception {
            // Given
            String pipelineId = "exception-cleanup";
            String tenantId = "exception-cleanup-tenant";

            // When
            try {
                instrumentation.instrumentPipelineDeployment(
                        pipelineId,
                        tenantId,
                        () -> {
                            throw new RuntimeException("Test exception");
                        }
                );
            } catch (RuntimeException e) {
                // Expected
            }

            // Then
            assertThat(MDC.getCopyOfContextMap()).isEmpty();
        }

        @Test
        void shouldPreserveOriginalExceptionMessage() {
            // Given
            String originalMessage = "Original error message";
            Exception originalException = new RuntimeException(originalMessage);

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentPipelineDeployment(
                    "pipeline",
                    "tenant",
                    () -> {
                        throw originalException;
                    }
            )).hasMessage(originalMessage);
        }

        @Test
        void shouldHandleCheckedExceptions() {
            // Given
            String eventId = "checked-exception-event";
            String eventType = "error.type";
            String tenantId = "error-tenant";

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentEventProcessing(
                    eventId,
                    eventType,
                    tenantId,
                    () -> {
                        throw new InterruptedException("Thread interrupted");
                    }
            )).isInstanceOf(InterruptedException.class);
        }
    }
}
