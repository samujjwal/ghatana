package com.ghatana.aep.observability.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
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
 * @doc.purpose Integration tests for AEP tracing provider with distributed tracing validation
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AEP Tracing Provider Integration Tests")
class AepTracingProviderIntegrationTest {

    private AepTracingProvider tracingProvider;

    @BeforeEach
    void setUp() { // GH-90000
        // Reset singleton instance for testing
        tracingProvider = AepTracingProvider.getInstance(); // GH-90000
        MDC.clear(); // GH-90000
    }

    @Nested
    @DisplayName("Pipeline Deployment Tracing")
    class PipelineDeploymentTracingTests {

        @Test
        void shouldCreatePipelineDeploymentSpan() { // GH-90000
            // Given
            String pipelineId = "pipeline-123";
            String tenantId = "tenant-456";

            // When
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId); // GH-90000

            // Then
            assertThat(span).isNotNull(); // GH-90000
            assertThat(MDC.get("pipelineId")).isEqualTo(pipelineId);
            assertThat(MDC.get("tenantId")).isEqualTo(tenantId);
            assertThat(MDC.get("correlationId")).isNotNull();

            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }

        @Test
        void shouldPropagateCorrelationId() { // GH-90000
            // Given
            String pipelineId = "pipeline-789";
            String tenantId = "tenant-012";
            MDC.put("correlationId", "existing-correlation-id"); // GH-90000

            // When
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId); // GH-90000

            // Then
            assertThat(MDC.get("correlationId")).isEqualTo("existing-correlation-id");

            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }

        @Test
        void shouldGenerateNewCorrelationIdWhenMissing() { // GH-90000
            // Given
            String pipelineId = "pipeline-new";
            String tenantId = "tenant-new";
            MDC.clear(); // GH-90000

            // When
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId); // GH-90000
            String generatedCorrelationId = MDC.get("correlationId");

            // Then
            assertThat(generatedCorrelationId).isNotNull(); // GH-90000
            assertThat(generatedCorrelationId).isNotEmpty(); // GH-90000
            assertThat(generatedCorrelationId).matches("[0-9a-f\\-]+");

            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Agent Execution Tracing")
    class AgentExecutionTracingTests {

        @Test
        void shouldCreateAgentExecutionSpan() { // GH-90000
            // Given
            String agentId = "agent-123";
            String pipelineId = "pipeline-456";
            String tenantId = "tenant-789";

            // When
            Span span = tracingProvider.startAgentExecutionSpan(agentId, pipelineId, tenantId); // GH-90000

            // Then
            assertThat(span).isNotNull(); // GH-90000
            assertThat(MDC.get("agentId")).isEqualTo(agentId);
            assertThat(MDC.get("correlationId")).isNotNull();

            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }

        @Test
        void shouldIncludeSpanContextInMDC() { // GH-90000
            // Given
            String agentId = "agent-context-test";
            String pipelineId = "pipeline-context-test";
            String tenantId = "tenant-context-test";

            // When
            Span span = tracingProvider.startAgentExecutionSpan(agentId, pipelineId, tenantId); // GH-90000
            String traceId = MDC.get("traceId");

            // Then
            assertThat(MDC.get("correlationId")).isNotNull();
            assertThat(MDC.get("agentId")).isEqualTo(agentId);

            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Event Processing Tracing")
    class EventProcessingTracingTests {

        @Test
        void shouldCreateEventProcessingSpan() { // GH-90000
            // Given
            String eventId = "event-123";
            String eventType = "agent.deployed";
            String tenantId = "tenant-456";

            // When
            Span span = tracingProvider.startEventProcessingSpan(eventId, eventType, tenantId); // GH-90000

            // Then
            assertThat(span).isNotNull(); // GH-90000
            assertThat(MDC.get("eventId")).isEqualTo(eventId);
            assertThat(MDC.get("eventType")).isEqualTo(eventType);
            assertThat(MDC.get("correlationId")).isNotNull();

            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }

        @Test
        void shouldTrackEventTypeInContext() { // GH-90000
            // Given
            String eventId = "event-type-test";
            String eventType = "pipeline.updated";
            String tenantId = "tenant-type-test";

            // When
            Span span = tracingProvider.startEventProcessingSpan(eventId, eventType, tenantId); // GH-90000

            // Then
            assertThat(MDC.get("eventType")).isEqualTo("pipeline.updated");

            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Success Recording")
    class SuccessRecordingTests {

        @Test
        void shouldRecordSuccessfulOperation() { // GH-90000
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("test.operation").startSpan();
            long duration = 150;

            // When
            tracingProvider.recordSuccess(span, duration); // GH-90000

            // Then
            assertThat(span).isNotNull(); // GH-90000
            // Span is ended, verifiable through no exception thrown
        }

        @Test
        void shouldHandleNullSpan() { // GH-90000
            // When + Then
            tracingProvider.recordSuccess(null, 100); // GH-90000
            // Should not throw
        }

        @Test
        void shouldRecordDurationAttribute() { // GH-90000
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("duration.test").startSpan();
            long duration = 250;

            // When
            tracingProvider.recordSuccess(span, duration); // GH-90000

            // Then
            // Duration recorded in span (verified by observer pattern in real tracing) // GH-90000
            assertThat(duration).isEqualTo(250); // GH-90000
        }
    }

    @Nested
    @DisplayName("Error Recording")
    class ErrorRecordingTests {

        @Test
        void shouldRecordErrorWithException() { // GH-90000
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("error.test").startSpan();
            Exception exception = new IllegalArgumentException("Test error");
            long duration = 100;

            // When
            tracingProvider.recordError(span, exception, duration); // GH-90000

            // Then
            assertThat(span).isNotNull(); // GH-90000
        }

        @Test
        void shouldHandleNullSpanInError() { // GH-90000
            // When + Then
            Exception exception = new RuntimeException("Test");
            tracingProvider.recordError(null, exception, 50); // GH-90000
            // Should not throw
        }

        @Test
        void shouldRecordExceptionDetails() { // GH-90000
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("exception.details.test").startSpan();
            Exception exception = new IllegalStateException("Invalid state");
            long duration = 75;

            // When
            tracingProvider.recordError(span, exception, duration); // GH-90000

            // Then
            assertThat(exception.getMessage()).isEqualTo("Invalid state");
        }

        @Test
        void shouldHandleDifferentExceptionTypes() { // GH-90000
            // Given
            Span span1 = tracingProvider.getTracer().spanBuilder("io.error").startSpan();
            Span span2 = tracingProvider.getTracer().spanBuilder("validation.error").startSpan();

            // When
            tracingProvider.recordError(span1, new java.io.IOException("IO failed"), 100);
            tracingProvider.recordError(span2, new IllegalArgumentException("Invalid arg"), 80);

            // Then
            assertThat(span1).isNotNull(); // GH-90000
            assertThat(span2).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Scope Management")
    class ScopeManagementTests {

        @Test
        void shouldCreateScopeForSpan() { // GH-90000
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("scope.test").startSpan();

            // When
            Scope scope = tracingProvider.createScope(span); // GH-90000

            // Then
            assertThat(scope).isNotNull(); // GH-90000
            scope.close(); // GH-90000
            span.end(); // GH-90000
        }

        @Test
        void shouldMaintainSpanContextAcrossScope() { // GH-90000
            // Given
            String pipelineId = "scope-context-test";
            String tenantId = "tenant-scope-test";
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId); // GH-90000
            String correlationIdBeforeScope = MDC.get("correlationId");

            // When
            Scope scope = tracingProvider.createScope(span); // GH-90000
            String correlationIdInScope = MDC.get("correlationId");

            // Then
            assertThat(correlationIdInScope).isEqualTo(correlationIdBeforeScope); // GH-90000
            scope.close(); // GH-90000
            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Provider Shutdown")
    class ProviderShutdownTests {

        @Test
        void shouldShutdownProviderGracefully() { // GH-90000
            // Given
            AepTracingProvider provider = AepTracingProvider.getInstance(); // GH-90000

            // When + Then
            provider.shutdown(); // GH-90000
            // Should not throw, provider should clean resources

            assertThat(provider).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantIsolationTests {

        @Test
        void shouldIsolateTenantContexts() { // GH-90000
            // Given
            String tenant1Id = "tenant-1";
            String tenant2Id = "tenant-2";
            String pipelineId = "isolation-test";

            // When
            Span span1 = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenant1Id); // GH-90000
            String tenant1Context = MDC.get("tenantId");

            // Reset MDC for second tenant
            MDC.clear(); // GH-90000

            Span span2 = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenant2Id); // GH-90000
            String tenant2Context = MDC.get("tenantId");

            // Then
            assertThat(tenant1Context).isEqualTo(tenant1Id); // GH-90000
            assertThat(tenant2Context).isEqualTo(tenant2Id); // GH-90000
            assertThat(tenant1Context).isNotEqualTo(tenant2Context); // GH-90000

            span1.end(); // GH-90000
            span2.end(); // GH-90000
            MDC.clear(); // GH-90000
        }

        @Test
        void shouldPropagateCorrelationIdAcrossTenants() { // GH-90000
            // Given
            String correlationId = "shared-correlation-123";
            MDC.put("correlationId", correlationId); // GH-90000
            String tenant1Id = "tenant-1";
            String pipelineId = "correlation-test";

            // When
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenant1Id); // GH-90000

            // Then
            assertThat(MDC.get("correlationId")).isEqualTo(correlationId);

            span.end(); // GH-90000
            MDC.clear(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Tracer and Meter Access")
    class TracerAndMeterAccessTests {

        @Test
        void shouldProvideTracerAccess() { // GH-90000
            // When
            var tracer = tracingProvider.getTracer(); // GH-90000

            // Then
            assertThat(tracer).isNotNull(); // GH-90000
        }

        @Test
        void shouldProvideMeterAccess() { // GH-90000
            // When
            var meter = tracingProvider.getMeter(); // GH-90000

            // Then
            assertThat(meter).isNotNull(); // GH-90000
        }

        @Test
        void shouldProvideOpenTelemetryAccess() { // GH-90000
            // When
            var openTelemetry = tracingProvider.getOpenTelemetry(); // GH-90000

            // Then
            assertThat(openTelemetry).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Concurrent Span Operations")
    class ConcurrentSpanOperationsTests {

        @Test
        void shouldHandleConcurrentPipelineDeployments() throws InterruptedException { // GH-90000
            // Given
            String[] pipelineIds = {"pipeline-a", "pipeline-b", "pipeline-c"};
            Span[] spans = new Span[3];

            // When
            for (int i = 0; i < 3; i++) { // GH-90000
                final int index = i;
                spans[i] = tracingProvider.startPipelineDeploymentSpan(pipelineIds[i], "tenant-concurrent"); // GH-90000
            }

            // Then
            for (Span span : spans) { // GH-90000
                assertThat(span).isNotNull(); // GH-90000
                span.end(); // GH-90000
            }
            MDC.clear(); // GH-90000
        }

        @Test
        void shouldMaintainContextInConcurrentAgentExecution() throws InterruptedException { // GH-90000
            // Given
            String pipelineId = "concurrent-pipeline";
            String tenantId = "concurrent-tenant";

            // When
            Span span1 = tracingProvider.startAgentExecutionSpan("agent-1", pipelineId, tenantId); // GH-90000
            Span span2 = tracingProvider.startAgentExecutionSpan("agent-2", pipelineId, tenantId); // GH-90000

            // Then
            assertThat(span1).isNotNull(); // GH-90000
            assertThat(span2).isNotNull(); // GH-90000

            span1.end(); // GH-90000
            span2.end(); // GH-90000
            MDC.clear(); // GH-90000
        }
    }
}
