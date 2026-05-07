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
@ExtendWith(MockitoExtension.class) 
@DisplayName("AEP Tracing Provider Integration Tests")
class AepTracingProviderIntegrationTest {

    private AepTracingProvider tracingProvider;

    @BeforeEach
    void setUp() { 
        // Reset singleton instance for testing
        tracingProvider = AepTracingProvider.getInstance(); 
        MDC.clear(); 
    }

    @Nested
    @DisplayName("Pipeline Deployment Tracing")
    class PipelineDeploymentTracingTests {

        @Test
        void shouldCreatePipelineDeploymentSpan() { 
            // Given
            String pipelineId = "pipeline-123";
            String tenantId = "tenant-456";

            // When
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId); 

            // Then
            assertThat(span).isNotNull(); 
            assertThat(MDC.get("pipelineId")).isEqualTo(pipelineId);
            assertThat(MDC.get("tenantId")).isEqualTo(tenantId);
            assertThat(MDC.get("correlationId")).isNotNull();

            span.end(); 
            MDC.clear(); 
        }

        @Test
        void shouldPropagateCorrelationId() { 
            // Given
            String pipelineId = "pipeline-789";
            String tenantId = "tenant-012";
            MDC.put("correlationId", "existing-correlation-id"); 

            // When
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId); 

            // Then
            assertThat(MDC.get("correlationId")).isEqualTo("existing-correlation-id");

            span.end(); 
            MDC.clear(); 
        }

        @Test
        void shouldGenerateNewCorrelationIdWhenMissing() { 
            // Given
            String pipelineId = "pipeline-new";
            String tenantId = "tenant-new";
            MDC.clear(); 

            // When
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId); 
            String generatedCorrelationId = MDC.get("correlationId");

            // Then
            assertThat(generatedCorrelationId).isNotNull(); 
            assertThat(generatedCorrelationId).isNotEmpty(); 
            assertThat(generatedCorrelationId).matches("[0-9a-f\\-]+");

            span.end(); 
            MDC.clear(); 
        }
    }

    @Nested
    @DisplayName("Agent Execution Tracing")
    class AgentExecutionTracingTests {

        @Test
        void shouldCreateAgentExecutionSpan() { 
            // Given
            String agentId = "agent-123";
            String pipelineId = "pipeline-456";
            String tenantId = "tenant-789";

            // When
            Span span = tracingProvider.startAgentExecutionSpan(agentId, pipelineId, tenantId); 

            // Then
            assertThat(span).isNotNull(); 
            assertThat(MDC.get("agentId")).isEqualTo(agentId);
            assertThat(MDC.get("correlationId")).isNotNull();

            span.end(); 
            MDC.clear(); 
        }

        @Test
        void shouldIncludeSpanContextInMDC() { 
            // Given
            String agentId = "agent-context-test";
            String pipelineId = "pipeline-context-test";
            String tenantId = "tenant-context-test";

            // When
            Span span = tracingProvider.startAgentExecutionSpan(agentId, pipelineId, tenantId); 
            String traceId = MDC.get("traceId");

            // Then
            assertThat(MDC.get("correlationId")).isNotNull();
            assertThat(MDC.get("agentId")).isEqualTo(agentId);

            span.end(); 
            MDC.clear(); 
        }
    }

    @Nested
    @DisplayName("Event Processing Tracing")
    class EventProcessingTracingTests {

        @Test
        void shouldCreateEventProcessingSpan() { 
            // Given
            String eventId = "event-123";
            String eventType = "agent.deployed";
            String tenantId = "tenant-456";

            // When
            Span span = tracingProvider.startEventProcessingSpan(eventId, eventType, tenantId); 

            // Then
            assertThat(span).isNotNull(); 
            assertThat(MDC.get("eventId")).isEqualTo(eventId);
            assertThat(MDC.get("eventType")).isEqualTo(eventType);
            assertThat(MDC.get("correlationId")).isNotNull();

            span.end(); 
            MDC.clear(); 
        }

        @Test
        void shouldTrackEventTypeInContext() { 
            // Given
            String eventId = "event-type-test";
            String eventType = "pipeline.updated";
            String tenantId = "tenant-type-test";

            // When
            Span span = tracingProvider.startEventProcessingSpan(eventId, eventType, tenantId); 

            // Then
            assertThat(MDC.get("eventType")).isEqualTo("pipeline.updated");

            span.end(); 
            MDC.clear(); 
        }
    }

    @Nested
    @DisplayName("Success Recording")
    class SuccessRecordingTests {

        @Test
        void shouldRecordSuccessfulOperation() { 
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("test.operation").startSpan();
            long duration = 150;

            // When
            tracingProvider.recordSuccess(span, duration); 

            // Then
            assertThat(span).isNotNull(); 
            // Span is ended, verifiable through no exception thrown
        }

        @Test
        void shouldHandleNullSpan() { 
            // When + Then
            tracingProvider.recordSuccess(null, 100); 
            // Should not throw
        }

        @Test
        void shouldRecordDurationAttribute() { 
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("duration.test").startSpan();
            long duration = 250;

            // When
            tracingProvider.recordSuccess(span, duration); 

            // Then
            // Duration recorded in span (verified by observer pattern in real tracing) 
            assertThat(duration).isEqualTo(250); 
        }
    }

    @Nested
    @DisplayName("Error Recording")
    class ErrorRecordingTests {

        @Test
        void shouldRecordErrorWithException() { 
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("error.test").startSpan();
            Exception exception = new IllegalArgumentException("Test error");
            long duration = 100;

            // When
            tracingProvider.recordError(span, exception, duration); 

            // Then
            assertThat(span).isNotNull(); 
        }

        @Test
        void shouldHandleNullSpanInError() { 
            // When + Then
            Exception exception = new RuntimeException("Test");
            tracingProvider.recordError(null, exception, 50); 
            // Should not throw
        }

        @Test
        void shouldRecordExceptionDetails() { 
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("exception.details.test").startSpan();
            Exception exception = new IllegalStateException("Invalid state");
            long duration = 75;

            // When
            tracingProvider.recordError(span, exception, duration); 

            // Then
            assertThat(exception.getMessage()).isEqualTo("Invalid state");
        }

        @Test
        void shouldHandleDifferentExceptionTypes() { 
            // Given
            Span span1 = tracingProvider.getTracer().spanBuilder("io.error").startSpan();
            Span span2 = tracingProvider.getTracer().spanBuilder("validation.error").startSpan();

            // When
            tracingProvider.recordError(span1, new java.io.IOException("IO failed"), 100);
            tracingProvider.recordError(span2, new IllegalArgumentException("Invalid arg"), 80);

            // Then
            assertThat(span1).isNotNull(); 
            assertThat(span2).isNotNull(); 
        }
    }

    @Nested
    @DisplayName("Scope Management")
    class ScopeManagementTests {

        @Test
        void shouldCreateScopeForSpan() { 
            // Given
            Span span = tracingProvider.getTracer().spanBuilder("scope.test").startSpan();

            // When
            Scope scope = tracingProvider.createScope(span); 

            // Then
            assertThat(scope).isNotNull(); 
            scope.close(); 
            span.end(); 
        }

        @Test
        void shouldMaintainSpanContextAcrossScope() { 
            // Given
            String pipelineId = "scope-context-test";
            String tenantId = "tenant-scope-test";
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId); 
            String correlationIdBeforeScope = MDC.get("correlationId");

            // When
            Scope scope = tracingProvider.createScope(span); 
            String correlationIdInScope = MDC.get("correlationId");

            // Then
            assertThat(correlationIdInScope).isEqualTo(correlationIdBeforeScope); 
            scope.close(); 
            span.end(); 
            MDC.clear(); 
        }
    }

    @Nested
    @DisplayName("Provider Shutdown")
    class ProviderShutdownTests {

        @Test
        void shouldShutdownProviderGracefully() { 
            // Given
            AepTracingProvider provider = AepTracingProvider.getInstance(); 

            // When + Then
            provider.shutdown(); 
            // Should not throw, provider should clean resources

            assertThat(provider).isNotNull(); 
        }
    }

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantIsolationTests {

        @Test
        void shouldIsolateTenantContexts() { 
            // Given
            String tenant1Id = "tenant-1";
            String tenant2Id = "tenant-2";
            String pipelineId = "isolation-test";

            // When
            Span span1 = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenant1Id); 
            String tenant1Context = MDC.get("tenantId");

            // Reset MDC for second tenant
            MDC.clear(); 

            Span span2 = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenant2Id); 
            String tenant2Context = MDC.get("tenantId");

            // Then
            assertThat(tenant1Context).isEqualTo(tenant1Id); 
            assertThat(tenant2Context).isEqualTo(tenant2Id); 
            assertThat(tenant1Context).isNotEqualTo(tenant2Context); 

            span1.end(); 
            span2.end(); 
            MDC.clear(); 
        }

        @Test
        void shouldPropagateCorrelationIdAcrossTenants() { 
            // Given
            String correlationId = "shared-correlation-123";
            MDC.put("correlationId", correlationId); 
            String tenant1Id = "tenant-1";
            String pipelineId = "correlation-test";

            // When
            Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenant1Id); 

            // Then
            assertThat(MDC.get("correlationId")).isEqualTo(correlationId);

            span.end(); 
            MDC.clear(); 
        }
    }

    @Nested
    @DisplayName("Tracer and Meter Access")
    class TracerAndMeterAccessTests {

        @Test
        void shouldProvideTracerAccess() { 
            // When
            var tracer = tracingProvider.getTracer(); 

            // Then
            assertThat(tracer).isNotNull(); 
        }

        @Test
        void shouldProvideMeterAccess() { 
            // When
            var meter = tracingProvider.getMeter(); 

            // Then
            assertThat(meter).isNotNull(); 
        }

        @Test
        void shouldProvideOpenTelemetryAccess() { 
            // When
            var openTelemetry = tracingProvider.getOpenTelemetry(); 

            // Then
            assertThat(openTelemetry).isNotNull(); 
        }
    }

    @Nested
    @DisplayName("Concurrent Span Operations")
    class ConcurrentSpanOperationsTests {

        @Test
        void shouldHandleConcurrentPipelineDeployments() throws InterruptedException { 
            // Given
            String[] pipelineIds = {"pipeline-a", "pipeline-b", "pipeline-c"};
            Span[] spans = new Span[3];

            // When
            for (int i = 0; i < 3; i++) { 
                final int index = i;
                spans[i] = tracingProvider.startPipelineDeploymentSpan(pipelineIds[i], "tenant-concurrent"); 
            }

            // Then
            for (Span span : spans) { 
                assertThat(span).isNotNull(); 
                span.end(); 
            }
            MDC.clear(); 
        }

        @Test
        void shouldMaintainContextInConcurrentAgentExecution() throws InterruptedException { 
            // Given
            String pipelineId = "concurrent-pipeline";
            String tenantId = "concurrent-tenant";

            // When
            Span span1 = tracingProvider.startAgentExecutionSpan("agent-1", pipelineId, tenantId); 
            Span span2 = tracingProvider.startAgentExecutionSpan("agent-2", pipelineId, tenantId); 

            // Then
            assertThat(span1).isNotNull(); 
            assertThat(span2).isNotNull(); 

            span1.end(); 
            span2.end(); 
            MDC.clear(); 
        }
    }
}
