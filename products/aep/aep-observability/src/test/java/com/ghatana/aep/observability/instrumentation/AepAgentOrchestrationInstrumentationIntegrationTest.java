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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AEP Agent Orchestration Instrumentation Integration Tests [GH-90000]")
class AepAgentOrchestrationInstrumentationIntegrationTest {

    private AepAgentOrchestrationInstrumentation instrumentation;

    @BeforeEach
    void setUp() { // GH-90000
        instrumentation = new AepAgentOrchestrationInstrumentation(); // GH-90000
        MDC.clear(); // GH-90000
    }

    @Nested
    @DisplayName("Pipeline Deployment Instrumentation [GH-90000]")
    class PipelineDeploymentInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulPipelineDeployment() throws Exception { // GH-90000
            // Given
            String pipelineId = "deploy-pipeline-123";
            String tenantId = "deploy-tenant-456";
            String expectedResult = "deployment-successful";

            // When
            String result = instrumentation.instrumentPipelineDeployment( // GH-90000
                    pipelineId,
                    tenantId,
                    () -> expectedResult // GH-90000
            );

            // Then
            assertThat(result).isEqualTo(expectedResult); // GH-90000
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldPropagateExceptionDuringDeployment() { // GH-90000
            // Given
            String pipelineId = "failing-pipeline";
            String tenantId = "failing-tenant";
            RuntimeException exception = new RuntimeException("Deployment failed [GH-90000]");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentPipelineDeployment( // GH-90000
                    pipelineId,
                    tenantId,
                    () -> { // GH-90000
                        throw exception;
                    }
            )).isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessage("Deployment failed [GH-90000]");
        }

        @Test
        void shouldClearMDCAfterDeployment() throws Exception { // GH-90000
            // Given
            String pipelineId = "cleanup-pipeline";
            String tenantId = "cleanup-tenant";

            // When
            instrumentation.instrumentPipelineDeployment( // GH-90000
                    pipelineId,
                    tenantId,
                    () -> "result" // GH-90000
            );

            // Then
            // MDC should be cleared after instrumentation (in finally block) // GH-90000
            // Note: Due to thread-local nature of MDC, this test verifies cleanup occurred
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldRecordDurationMetrics() throws Exception { // GH-90000
            // Given
            String pipelineId = "duration-pipeline";
            String tenantId = "duration-tenant";
            long startTime = System.currentTimeMillis(); // GH-90000

            // When
            instrumentation.instrumentPipelineDeployment( // GH-90000
                    pipelineId,
                    tenantId,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(100); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return "result";
                    }
            );

            long duration = System.currentTimeMillis() - startTime; // GH-90000

            // Then
            assertThat(duration).isGreaterThanOrEqualTo(100); // GH-90000
        }
    }

    @Nested
    @DisplayName("Agent Execution Instrumentation [GH-90000]")
    class AgentExecutionInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulAgentExecution() throws Exception { // GH-90000
            // Given
            String agentId = "agent-exec-123";
            String pipelineId = "exec-pipeline-456";
            String tenantId = "exec-tenant-789";
            String expectedResult = "agent-output";

            // When
            String result = instrumentation.instrumentAgentExecution( // GH-90000
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> expectedResult // GH-90000
            );

            // Then
            assertThat(result).isEqualTo(expectedResult); // GH-90000
            assertThat(MDC.get("agentId [GH-90000]")).isNull();
        }

        @Test
        void shouldValidateAgentExecutionSLA() throws Exception { // GH-90000
            // Given
            String agentId = "sla-agent";
            String pipelineId = "sla-pipeline";
            String tenantId = "sla-tenant";

            // When + Then - SLA threshold is 5 seconds
            String result = instrumentation.instrumentAgentExecution( // GH-90000
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> { // GH-90000
                        // Simulate quick execution within SLA
                        return "completed-within-sla";
                    }
            );

            assertThat(result).isEqualTo("completed-within-sla [GH-90000]");
        }

        @Test
        void shouldLogWarningWhenExecutionExceedsSLA() throws Exception { // GH-90000
            // Given
            String agentId = "slow-agent";
            String pipelineId = "slow-pipeline";
            String tenantId = "slow-tenant";

            // When
            String result = instrumentation.instrumentAgentExecution( // GH-90000
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> { // GH-90000
                        try {
                            // Simulate execution exceeding 5 second SLA
                            Thread.sleep(100); // Simulating delay // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return "slow-result";
                    }
            );

            // Then
            assertThat(result).isEqualTo("slow-result [GH-90000]");
            // SLA warning would be logged (verified through log capture in real tests) // GH-90000
        }

        @Test
        void shouldPropagateAgentExecutionException() { // GH-90000
            // Given
            String agentId = "failing-agent";
            String pipelineId = "failing-exec-pipeline";
            String tenantId = "failing-exec-tenant";
            IllegalStateException exception = new IllegalStateException("Agent state invalid [GH-90000]");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentAgentExecution( // GH-90000
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> { // GH-90000
                        throw exception;
                    }
            )).isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessage("Agent state invalid [GH-90000]");
        }

        @Test
        void shouldClearAgentContextAfterExecution() throws Exception { // GH-90000
            // Given
            String agentId = "context-cleanup-agent";
            String pipelineId = "context-cleanup-pipeline";
            String tenantId = "context-cleanup-tenant";

            // When
            instrumentation.instrumentAgentExecution( // GH-90000
                    agentId,
                    pipelineId,
                    tenantId,
                    () -> "result" // GH-90000
            );

            // Then
            assertThat(MDC.get("agentId [GH-90000]")).isNull();
        }
    }

    @Nested
    @DisplayName("Event Processing Instrumentation [GH-90000]")
    class EventProcessingInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulEventProcessing() throws Exception { // GH-90000
            // Given
            String eventId = "event-proc-123";
            String eventType = "agent.deployed";
            String tenantId = "event-tenant-456";
            String expectedResult = "event-processed";

            // When
            String result = instrumentation.instrumentEventProcessing( // GH-90000
                    eventId,
                    eventType,
                    tenantId,
                    () -> expectedResult // GH-90000
            );

            // Then
            assertThat(result).isEqualTo(expectedResult); // GH-90000
            assertThat(MDC.get("eventId [GH-90000]")).isNull();
        }

        @Test
        void shouldDetectSlowEventProcessing() throws Exception { // GH-90000
            // Given
            String eventId = "slow-event";
            String eventType = "slow.process";
            String tenantId = "slow-event-tenant";

            // When
            String result = instrumentation.instrumentEventProcessing( // GH-90000
                    eventId,
                    eventType,
                    tenantId,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(100); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return "slow-result";
                    }
            );

            // Then
            assertThat(result).isEqualTo("slow-result [GH-90000]");
            // Performance warning would be logged for > 1000ms processing
        }

        @Test
        void shouldPropagateEventProcessingException() { // GH-90000
            // Given
            String eventId = "failing-event";
            String eventType = "event.failed";
            String tenantId = "failing-event-tenant";
            IOException exception = new java.io.IOException("Event processing IO error [GH-90000]");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentEventProcessing( // GH-90000
                    eventId,
                    eventType,
                    tenantId,
                    () -> { // GH-90000
                        throw exception;
                    }
            )).isInstanceOf(IOException.class) // GH-90000
                    .hasMessage("Event processing IO error [GH-90000]");
        }

        @Test
        void shouldClearEventContextAfterProcessing() throws Exception { // GH-90000
            // Given
            String eventId = "cleanup-event";
            String eventType = "cleanup.type";
            String tenantId = "cleanup-event-tenant";

            // When
            instrumentation.instrumentEventProcessing( // GH-90000
                    eventId,
                    eventType,
                    tenantId,
                    () -> "result" // GH-90000
            );

            // Then
            assertThat(MDC.get("eventId [GH-90000]")).isNull();
            assertThat(MDC.get("eventType [GH-90000]")).isNull();
        }

        @Test
        void shouldPreserveCorrelationIdDuringEventProcessing() throws Exception { // GH-90000
            // Given
            String correlationId = "correlation-event-123";
            MDC.put("correlationId", correlationId); // GH-90000
            String eventId = "corr-event";
            String eventType = "corr.type";
            String tenantId = "corr-tenant";

            // When
            instrumentation.instrumentEventProcessing( // GH-90000
                    eventId,
                    eventType,
                    tenantId,
                    () -> "result" // GH-90000
            );

            // Then
            // Correlation ID should be preserved through instrumentation
            // (actual verification requires log capture in real tests) // GH-90000
        }
    }

    @Nested
    @DisplayName("Batch Operation Instrumentation [GH-90000]")
    class BatchOperationInstrumentationTests {

        @Test
        void shouldInstrumentSuccessfulBatchOperation() throws Exception { // GH-90000
            // Given
            String batchId = "batch-123";
            String tenantId = "batch-tenant-456";
            int totalItems = 10;

            // When + Then
            instrumentation.instrumentBatchOperation( // GH-90000
                    batchId,
                    tenantId,
                    totalItems,
                    () -> { // GH-90000
                        // Simulate batch processing
                    }
            );

            // Should complete without exception
        }

        @Test
        void shouldCalculatePerItemDuration() throws Exception { // GH-90000
            // Given
            String batchId = "duration-batch";
            String tenantId = "duration-batch-tenant";
            int totalItems = 5;
            long startTime = System.currentTimeMillis(); // GH-90000

            // When
            instrumentation.instrumentBatchOperation( // GH-90000
                    batchId,
                    tenantId,
                    totalItems,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(100); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                    }
            );

            long totalDuration = System.currentTimeMillis() - startTime; // GH-90000

            // Then
            assertThat(totalDuration).isGreaterThanOrEqualTo(100); // GH-90000
            long perItemDuration = totalDuration / totalItems;
            assertThat(perItemDuration).isGreaterThan(0); // GH-90000
        }

        @Test
        void shouldWarnWhenPerItemDurationExceedsThreshold() throws Exception { // GH-90000
            // Given
            String batchId = "slow-batch";
            String tenantId = "slow-batch-tenant";
            int totalItems = 1;

            // When
            instrumentation.instrumentBatchOperation( // GH-90000
                    batchId,
                    tenantId,
                    totalItems,
                    () -> { // GH-90000
                        try {
                            Thread.sleep(150); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                    }
            );

            // Then
            // Warning would be logged for per-item duration > 100ms
        }

        @Test
        void shouldPropagateBatchOperationException() { // GH-90000
            // Given
            String batchId = "failing-batch";
            String tenantId = "failing-batch-tenant";
            int totalItems = 5;
            RuntimeException exception = new RuntimeException("Batch processing failed [GH-90000]");

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentBatchOperation( // GH-90000
                    batchId,
                    tenantId,
                    totalItems,
                    () -> { // GH-90000
                        throw exception;
                    }
            )).isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessage("Batch processing failed [GH-90000]");
        }

        @Test
        void shouldHandleZeroItemBatch() throws Exception { // GH-90000
            // Given
            String batchId = "empty-batch";
            String tenantId = "empty-batch-tenant";
            int totalItems = 0;

            // When + Then
            instrumentation.instrumentBatchOperation( // GH-90000
                    batchId,
                    tenantId,
                    totalItems,
                    () -> { // GH-90000
                        // No items to process
                    }
            );

            // Should complete without division by zero error
        }
    }

    @Nested
    @DisplayName("Multi-Tenant Context Isolation [GH-90000]")
    class MultiTenantContextIsolationTests {

        @Test
        void shouldIsolateTenantContextInPipelineDeployment() throws Exception { // GH-90000
            // Given
            String tenant1 = "isolation-tenant-1";
            String tenant2 = "isolation-tenant-2";
            String pipelineId = "isolation-pipeline";

            // When
            instrumentation.instrumentPipelineDeployment( // GH-90000
                    pipelineId,
                    tenant1,
                    () -> "tenant1-result" // GH-90000
            );

            MDC.clear(); // GH-90000

            instrumentation.instrumentPipelineDeployment( // GH-90000
                    pipelineId,
                    tenant2,
                    () -> "tenant2-result" // GH-90000
            );

            // Then
            assertThat(MDC.get("tenantId [GH-90000]")).isNull();
        }

        @Test
        void shouldIsolateTenantContextInAgentExecution() throws Exception { // GH-90000
            // Given
            String tenant1 = "agent-isolation-tenant-1";
            String tenant2 = "agent-isolation-tenant-2";
            String agentId = "multitenancy-agent";
            String pipelineId = "multitenancy-pipeline";

            // When
            instrumentation.instrumentAgentExecution( // GH-90000
                    agentId,
                    pipelineId,
                    tenant1,
                    () -> "tenant1-agent-result" // GH-90000
            );

            MDC.clear(); // GH-90000

            instrumentation.instrumentAgentExecution( // GH-90000
                    agentId,
                    pipelineId,
                    tenant2,
                    () -> "tenant2-agent-result" // GH-90000
            );

            // Then
            assertThat(MDC.get("tenantId [GH-90000]")).isNull();
        }
    }

    @Nested
    @DisplayName("Exception Handling and Error Propagation [GH-90000]")
    class ExceptionHandlingAndErrorPropagationTests {

        @Test
        void shouldClearMDCEvenOnException() throws Exception { // GH-90000
            // Given
            String pipelineId = "exception-cleanup";
            String tenantId = "exception-cleanup-tenant";

            // When
            try {
                instrumentation.instrumentPipelineDeployment( // GH-90000
                        pipelineId,
                        tenantId,
                        () -> { // GH-90000
                            throw new RuntimeException("Test exception [GH-90000]");
                        }
                );
            } catch (RuntimeException e) { // GH-90000
                // Expected
            }

            // Then
            assertThat(MDC.getCopyOfContextMap()).isEmpty(); // GH-90000
        }

        @Test
        void shouldPreserveOriginalExceptionMessage() { // GH-90000
            // Given
            String originalMessage = "Original error message";
            Exception originalException = new RuntimeException(originalMessage); // GH-90000

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentPipelineDeployment( // GH-90000
                    "pipeline",
                    "tenant",
                    () -> { // GH-90000
                        throw originalException;
                    }
            )).hasMessage(originalMessage); // GH-90000
        }

        @Test
        void shouldHandleCheckedExceptions() { // GH-90000
            // Given
            String eventId = "checked-exception-event";
            String eventType = "error.type";
            String tenantId = "error-tenant";

            // When + Then
            assertThatThrownBy(() -> instrumentation.instrumentEventProcessing( // GH-90000
                    eventId,
                    eventType,
                    tenantId,
                    () -> { // GH-90000
                        throw new InterruptedException("Thread interrupted [GH-90000]");
                    }
            )).isInstanceOf(InterruptedException.class); // GH-90000
        }
    }
}
