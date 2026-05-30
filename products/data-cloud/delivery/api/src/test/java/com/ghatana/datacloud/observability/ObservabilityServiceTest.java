package com.ghatana.datacloud.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused unit tests for ObservabilityService.
 *
 * <p>These tests cover the core functionality without integrating with
 * external systems or running release-readiness flows.</p>
 *
 * @doc.type class
 * @doc.purpose Focused unit tests for ObservabilityService
 * @doc.layer test
 * @doc.pattern UnitTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ObservabilityService Tests")
class ObservabilityServiceTest {

    private ObservabilityService observabilityService;

    @BeforeEach
    void setUp() {
        observabilityService = ObservabilityService.builder()
            .serviceName("test-service")
            .build();
    }

    @Test
    @DisplayName("Should create context with required parameters")
    void shouldCreateContextWithRequiredParameters() {
        String correlationId = "test-correlation-123";
        String tenantId = "tenant-456";
        String surface = "api";

        try (ObservabilityService.ObservabilityContext context = 
             observabilityService.createContext(correlationId, tenantId, surface)) {
            
            assertThat(context.getCorrelationId()).isEqualTo(correlationId);
            assertThat(context.getTenantId()).isEqualTo(tenantId);
            assertThat(context.getSurface()).isEqualTo(surface);
            assertThat(context.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("Should create context with optional parameters")
    void shouldCreateContextWithOptionalParameters() {
        String correlationId = "test-correlation-123";
        String tenantId = "tenant-456";
        String surface = "api";
        String runId = "run-789";
        String jobId = "job-101";

        try (ObservabilityService.ObservabilityContext context = 
             observabilityService.createContext(correlationId, tenantId, surface, runId, jobId)) {
            
            assertThat(context.getCorrelationId()).isEqualTo(correlationId);
            assertThat(context.getTenantId()).isEqualTo(tenantId);
            assertThat(context.getSurface()).isEqualTo(surface);
            assertThat(context.getRunId()).isEqualTo(runId);
            assertThat(context.getJobId()).isEqualTo(jobId);
            assertThat(context.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("Should reject invalid correlation ID")
    void shouldRejectInvalidCorrelationId() {
        assertThatThrownBy(() -> 
            observabilityService.createContext("", "tenant-456", "api"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("correlationId");
    }

    @Test
    @DisplayName("Should reject null correlation ID")
    void shouldRejectNullCorrelationId() {
        assertThatThrownBy(() -> 
            observabilityService.createContext(null, "tenant-456", "api"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("correlationId");
    }

    @Test
    @DisplayName("Should get current context when active")
    void shouldGetCurrentContextWhenActive() {
        String correlationId = "test-correlation-123";
        String tenantId = "tenant-456";
        String surface = "api";

        try (ObservabilityService.ObservabilityContext context = 
             observabilityService.createContext(correlationId, tenantId, surface)) {
            
            Optional<ObservabilityService.ObservabilityContext> current = 
                observabilityService.getCurrentContext();
            
            assertThat(current).isPresent();
            assertThat(current.get().getCorrelationId()).isEqualTo(correlationId);
        }
    }

    @Test
    @DisplayName("Should return empty when no active context")
    void shouldReturnEmptyWhenNoActiveContext() {
        Optional<ObservabilityService.ObservabilityContext> current = 
            observabilityService.getCurrentContext();
        
        assertThat(current).isEmpty();
    }

    @Test
    @DisplayName("Should record and retrieve metrics")
    void shouldRecordAndRetrieveMetrics() {
        String correlationId = "test-correlation-123";
        String tenantId = "tenant-456";
        String surface = "api";

        try (ObservabilityService.ObservabilityContext context = 
             observabilityService.createContext(correlationId, tenantId, surface)) {
            
            context.recordMetric("test_metric", 42.0);
            
            Map<String, Object> metrics = context.getAllMetrics();
            assertThat(metrics).containsEntry("test_metric", 42.0);
        }
    }

    @Test
    @DisplayName("Should record and retrieve events")
    void shouldRecordAndRetrieveEvents() {
        String correlationId = "test-correlation-123";
        String tenantId = "tenant-456";
        String surface = "api";

        try (ObservabilityService.ObservabilityContext context = 
             observabilityService.createContext(correlationId, tenantId, surface)) {
            
            Map<String, Object> eventData = Map.of("key", "value");
            context.recordEvent("test_event", eventData);
            
            Map<String, Object> events = context.getAllEvents();
            assertThat(events).containsKey("test_event");
            assertThat(events.get("test_event")).isEqualTo(eventData);
        }
    }

    @Test
    @DisplayName("Should track context statistics")
    void shouldTrackContextStatistics() {
        String correlationId = "test-correlation-123";
        String tenantId = "tenant-456";
        String surface = "api";

        int initialCreated = observabilityService.getStats().getTotalContextsCreated();

        try (ObservabilityService.ObservabilityContext context = 
             observabilityService.createContext(correlationId, tenantId, surface)) {
            // Context is active
            assertThat(observabilityService.getStats().getActiveContexts()).isEqualTo(1);
            assertThat(observabilityService.getStats().getTotalContextsCreated())
                .isEqualTo(initialCreated + 1);
        }

        // Context should be closed
        assertThat(observabilityService.getStats().getActiveContexts()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle nested contexts properly")
    void shouldHandleNestedContextsProperly() {
        String correlationId1 = "test-correlation-123";
        String correlationId2 = "test-correlation-456";

        try (ObservabilityService.ObservabilityContext context1 = 
             observabilityService.createContext(correlationId1, "tenant-456", "api")) {
            
            assertThat(observabilityService.getCurrentContext())
                .map(ObservabilityService.ObservabilityContext::getCorrelationId)
                .contains(correlationId1);

            try (ObservabilityService.ObservabilityContext context2 = 
                 observabilityService.createContext(correlationId2, "tenant-456", "api")) {
                
                assertThat(observabilityService.getCurrentContext())
                    .map(ObservabilityService.ObservabilityContext::getCorrelationId)
                    .contains(correlationId2);
            }

            // Should revert to context1
            assertThat(observabilityService.getCurrentContext())
                .map(ObservabilityService.ObservabilityContext::getCorrelationId)
                .contains(correlationId1);
        }
    }

    @Test
    @DisplayName("Should build service with custom configuration")
    void shouldBuildServiceWithCustomConfiguration() {
        ObservabilityService customService = ObservabilityService.builder()
            .serviceName("custom-service")
            .enableMdc(true)
            .build();

        assertThat(customService.getStats().getServiceName()).isEqualTo("custom-service");
    }

    @Test
    @DisplayName("Should handle context closure gracefully")
    void shouldHandleContextClosureGracefully() {
        String correlationId = "test-correlation-123";
        String tenantId = "tenant-456";
        String surface = "api";

        ObservabilityService.ObservabilityContext context = 
            observabilityService.createContext(correlationId, tenantId, surface);

        assertThat(context.isActive()).isTrue();

        context.close();

        assertThat(context.isActive()).isFalse();
        assertThat(observabilityService.getCurrentContext()).isEmpty();
    }
}
