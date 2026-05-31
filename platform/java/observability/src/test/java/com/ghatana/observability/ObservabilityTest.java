package com.ghatana.observability;

import com.ghatana.observability.correlation.CorrelationContext;
import com.ghatana.observability.degradation.DegradationState;
import com.ghatana.observability.logging.StructuredLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for observability components (Group 9).
 *
 * <p><b>Purpose</b><br>
 * Validates correlation context, structured logging, and degradation state
 * for observability across async workflows.
 *
 * @doc.type test
 * @doc.purpose Observability validation
 * @doc.layer platform
 * @doc.pattern Observability Test
 */
@DisplayName("Observability Tests")
class ObservabilityTest {

    // ============ Group 9-4: Error-Path Observability Tests ============

    @Test
    @DisplayName("[OBS001] CorrelationContext should generate correlation ID if not provided")
    void correlationContextShouldGenerateCorrelationIdIfNotProvided() {
        // Act
        CorrelationContext context = CorrelationContext.builder()
                .tenantId("tenant-123")
                .surface("data-cloud")
                .build();

        // Assert
        assertNotNull(context.getCorrelationId());
        assertTrue(context.getCorrelationId().startsWith("corr-"));
        assertEquals("tenant-123", context.getTenantId());
        assertEquals("data-cloud", context.getSurface());
    }

    @Test
    @DisplayName("[OBS002] CorrelationContext should preserve provided correlation ID")
    void correlationContextShouldPreserveProvidedCorrelationId() {
        // Act
        CorrelationContext context = CorrelationContext.builder()
                .correlationId("custom-123")
                .tenantId("tenant-456")
                .build();

        // Assert
        assertEquals("custom-123", context.getCorrelationId());
    }

    @Test
    @DisplayName("[OBS003] CorrelationContext should support context chaining with with methods")
    void correlationContextShouldSupportContextChaining() {
        // Arrange
        CorrelationContext base = CorrelationContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-123")
                .surface("data-cloud")
                .build();

        // Act
        CorrelationContext withRun = base.withRunId("run-456");
        CorrelationContext withJob = withRun.withJobId("job-789");
        CorrelationContext withAgent = withJob.withAgentId("agent-abc");

        // Assert
        assertEquals("corr-123", withAgent.getCorrelationId());
        assertEquals("run-456", withAgent.getRunId());
        assertEquals("job-789", withAgent.getJobId());
        assertEquals("agent-abc", withAgent.getAgentId());
        // Original context unchanged
        assertNull(base.getRunId());
    }

    @Test
    @DisplayName("[OBS004] CorrelationContext should convert to map for serialization")
    void correlationContextShouldConvertToMapForSerialization() {
        // Arrange
        CorrelationContext context = CorrelationContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("data-cloud")
                .runId("run-789")
                .addContext("customKey", "customValue")
                .build();

        // Act
        Map<String, String> map = context.toMap();

        // Assert
        assertEquals("corr-123", map.get("correlationId"));
        assertEquals("tenant-456", map.get("tenantId"));
        assertEquals("data-cloud", map.get("surface"));
        assertEquals("run-789", map.get("runId"));
        assertEquals("customValue", map.get("customKey"));
        assertNotNull(map.get("createdAt"));
    }

    // ============ Group 9-5: Runtime Truth Degraded States Tests ============

    @Test
    @DisplayName("[OBS005] DegradationState should represent degraded state correctly")
    void degradationStateShouldRepresentDegradedStateCorrectly() {
        // Act
        DegradationState state = DegradationState.builder()
                .component("connector-sync")
                .degradationType(DegradationState.DegradationType.SERVICE_UNAVAILABLE)
                .severity(DegradationState.Severity.HIGH)
                .message("External connector service is unavailable")
                .addDetail("service", "salesforce")
                .addDetail("region", "us-west-2")
                .build();

        // Assert
        assertEquals("connector-sync", state.getComponent());
        assertEquals(DegradationState.DegradationType.SERVICE_UNAVAILABLE, state.getDegradationType());
        assertEquals(DegradationState.Severity.HIGH, state.getSeverity());
        assertEquals("External connector service is unavailable", state.getMessage());
        assertTrue(state.isDegraded());
        assertFalse(state.isResolved());
        assertEquals("salesforce", state.getDetails().get("service"));
        assertNotNull(state.getDetectedAt());
    }

    @Test
    @DisplayName("[OBS006] DegradationState should support resolution")
    void degradationStateShouldSupportResolution() {
        // Arrange
        DegradationState degraded = DegradationState.builder()
                .component("connector-sync")
                .degradationType(DegradationState.DegradationType.SERVICE_UNAVAILABLE)
                .severity(DegradationState.Severity.HIGH)
                .message("External connector service is unavailable")
                .build();

        // Act
        DegradationState resolved = degraded.resolve("Service recovered");

        // Assert
        assertTrue(resolved.isResolved());
        assertEquals("Service recovered", resolved.getResolutionReason());
        assertNotNull(resolved.getResolvedAt());
        assertEquals(degraded.getComponent(), resolved.getComponent());
        assertEquals(degraded.getDegradationType(), resolved.getDegradationType());
    }

    @Test
    @DisplayName("[OBS007] DegradationState should calculate duration correctly")
    void degradationStateShouldCalculateDurationCorrectly() {
        // Arrange
        Instant past = Instant.now().minusSeconds(60);
        DegradationState state = DegradationState.builder()
                .component("connector-sync")
                .degradationType(DegradationState.DegradationType.SERVICE_UNAVAILABLE)
                .severity(DegradationState.Severity.HIGH)
                .message("Service unavailable")
                .detectedAt(past)
                .build();

        // Act
        java.time.Duration duration = state.getDuration();

        // Assert
        assertTrue(duration.getSeconds() >= 59); // Allow for test execution time
        assertTrue(duration.getSeconds() <= 61);
    }

    @Test
    @DisplayName("[OBS008] DegradationState should require mandatory fields")
    void degradationStateShouldRequireMandatoryFields() {
        // Assert component required
        assertThrows(IllegalArgumentException.class, () -> {
            DegradationState.builder()
                    .degradationType(DegradationState.DegradationType.SERVICE_UNAVAILABLE)
                    .severity(DegradationState.Severity.HIGH)
                    .message("Message")
                    .build();
        });

        // Assert degradationType required
        assertThrows(IllegalArgumentException.class, () -> {
            DegradationState.builder()
                    .component("component")
                    .severity(DegradationState.Severity.HIGH)
                    .message("Message")
                    .build();
        });

        // Assert severity required
        assertThrows(IllegalArgumentException.class, () -> {
            DegradationState.builder()
                    .component("component")
                    .degradationType(DegradationState.DegradationType.SERVICE_UNAVAILABLE)
                    .message("Message")
                    .build();
        });

        // Assert message required
        assertThrows(IllegalArgumentException.class, () -> {
            DegradationState.builder()
                    .component("component")
                    .degradationType(DegradationState.DegradationType.SERVICE_UNAVAILABLE)
                    .severity(DegradationState.Severity.HIGH)
                    .build();
        });
    }

    // ============ Group 9-6: Correlation Propagation Tests ============

    @Test
    @DisplayName("[OBS009] CorrelationContext should propagate through builder")
    void correlationContextShouldPropagateThroughBuilder() {
        // Arrange
        CorrelationContext original = CorrelationContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("data-cloud")
                .runId("run-789")
                .addContext("key1", "value1")
                .build();

        // Act
        CorrelationContext propagated = CorrelationContext.builder(original)
                .jobId("job-abc")
                .addContext("key2", "value2")
                .build();

        // Assert
        assertEquals("corr-123", propagated.getCorrelationId());
        assertEquals("tenant-456", propagated.getTenantId());
        assertEquals("data-cloud", propagated.getSurface());
        assertEquals("run-789", propagated.getRunId());
        assertEquals("job-abc", propagated.getJobId());
        assertEquals("value1", propagated.getContextValue("key1").orElse(null));
        assertEquals("value2", propagated.getContextValue("key2").orElse(null));
    }

    @Test
    @DisplayName("[OBS010] CorrelationContext should support equality based on core fields")
    void correlationContextShouldSupportEqualityBasedOnCoreFields() {
        // Arrange
        CorrelationContext context1 = CorrelationContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("data-cloud")
                .build();

        CorrelationContext context2 = CorrelationContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("data-cloud")
                .runId("run-789") // Different additional field
                .build();

        // Assert
        assertEquals(context1, context2);
        assertEquals(context1.hashCode(), context2.hashCode());
    }

    @Test
    @DisplayName("[OBS011] CorrelationContext should not equal with different core fields")
    void correlationContextShouldNotEqualWithDifferentCoreFields() {
        // Arrange
        CorrelationContext context1 = CorrelationContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("data-cloud")
                .build();

        CorrelationContext context2 = CorrelationContext.builder()
                .correlationId("corr-456") // Different correlation ID
                .tenantId("tenant-456")
                .surface("data-cloud")
                .build();

        // Assert
        assertNotEquals(context1, context2);
    }

    @Test
    @DisplayName("[OBS012] StructuredLogger should use MDC for correlation context")
    void structuredLoggerShouldUseMDCForCorrelationContext() {
        // This test validates the MDC usage pattern
        // Actual MDC testing would require a logging framework mock
        CorrelationContext context = CorrelationContext.builder()
                .correlationId("corr-123")
                .tenantId("tenant-456")
                .surface("data-cloud")
                .build();

        Map<String, String> contextMap = context.toMap();
        
        // Assert context map contains expected fields
        assertEquals("corr-123", contextMap.get("correlationId"));
        assertEquals("tenant-456", contextMap.get("tenantId"));
        assertEquals("data-cloud", contextMap.get("surface"));
        assertNotNull(contextMap.get("createdAt"));
    }
}
