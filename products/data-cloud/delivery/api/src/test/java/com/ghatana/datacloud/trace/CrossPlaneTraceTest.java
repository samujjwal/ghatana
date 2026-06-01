/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.trace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CrossPlaneTrace model (Pass 9 cross-plane observability).
 *
 * @doc.type class
 * @doc.purpose Validate CrossPlaneTrace model behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CrossPlaneTrace Tests")
class CrossPlaneTraceTest {

    @Test
    @DisplayName("Should create valid CrossPlaneTrace with required fields")
    void shouldCreateValidCrossPlaneTrace() {
        String traceId = "trc-abc123def456";
        String spanId = "spn-xyz789uvw012";
        String tenantId = "tenant-123";
        String sourcePlane = "DATA_CLOUD";
        String serviceName = "MediaArtifactController";
        String operationName = "POST /api/v1/media/artifacts";
        String status = "RUNNING";
        Instant startTime = Instant.now();

        CrossPlaneTrace trace = CrossPlaneTrace.builder()
            .traceId(traceId)
            .spanId(spanId)
            .tenantId(tenantId)
            .sourcePlane(CrossPlaneTrace.PlaneType.DATA_CLOUD)
            .serviceName(serviceName)
            .operationName(operationName)
            .status(CrossPlaneTrace.TraceStatus.RUNNING)
            .startTime(startTime)
            .build();

        assertThat(trace.traceId()).isEqualTo(traceId);
        assertThat(trace.spanId()).isEqualTo(spanId);
        assertThat(trace.tenantId()).isEqualTo(tenantId);
        assertThat(trace.sourcePlane()).isEqualTo(CrossPlaneTrace.PlaneType.DATA_CLOUD);
        assertThat(trace.serviceName()).isEqualTo(serviceName);
        assertThat(trace.operationName()).isEqualTo(operationName);
        assertThat(trace.status()).isEqualTo(CrossPlaneTrace.TraceStatus.RUNNING);
        assertThat(trace.startTime()).isEqualTo(startTime);
    }

    @Test
    @DisplayName("Should create CrossPlaneTrace with optional fields")
    void shouldCreateCrossPlaneTraceWithOptionalFields() {
        String traceId = "trc-abc123def456";
        String spanId = "spn-xyz789uvw012";
        String parentSpanId = "spn-parent123";
        String operationId = "op-456";
        String tenantId = "tenant-123";
        String sourcePlane = "ACTION_PLANE";
        String targetPlane = "AGENT_RUNTIME";
        String serviceName = "WorkflowExecutionHandler";
        String operationName = "POST /api/v1/action/pipelines/{id}/execute";
        String status = "COMPLETED";
        Instant startTime = Instant.now().minusSeconds(5);
        Instant endTime = Instant.now();
        long durationMs = 5000;
        Map<String, Object> attributes = Map.of("pipelineId", "pipeline-123", "inputSize", 1024);
        List<CrossPlaneTrace.TraceEvent> events = List.of(
            new CrossPlaneTrace.TraceEvent("LOG", "Pipeline execution started", Instant.now().minusSeconds(5), Map.of("level", "INFO")),
            new CrossPlaneTrace.TraceEvent("LOG", "Pipeline execution completed", Instant.now(), Map.of("level", "INFO"))
        );
        String correlationId = "corr-789";
        String userId = "user-456";
        String resourceType = "Pipeline";
        String resourceId = "pipeline-123";

        CrossPlaneTrace trace = CrossPlaneTrace.builder()
            .traceId(traceId)
            .spanId(spanId)
            .parentSpanId(parentSpanId)
            .operationId(operationId)
            .tenantId(tenantId)
            .sourcePlane(CrossPlaneTrace.PlaneType.ACTION_PLANE)
            .targetPlane(CrossPlaneTrace.PlaneType.AGENT_RUNTIME)
            .serviceName(serviceName)
            .operationName(operationName)
            .status(CrossPlaneTrace.TraceStatus.COMPLETED)
            .startTime(startTime)
            .endTime(endTime)
            .durationMs(durationMs)
            .attributes(attributes)
            .events(events)
            .correlationId(correlationId)
            .userId(userId)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .build();

        assertThat(trace.traceId()).isEqualTo(traceId);
        assertThat(trace.spanId()).isEqualTo(spanId);
        assertThat(trace.parentSpanId()).isEqualTo(parentSpanId);
        assertThat(trace.operationId()).isEqualTo(operationId);
        assertThat(trace.tenantId()).isEqualTo(tenantId);
        assertThat(trace.sourcePlane()).isEqualTo(CrossPlaneTrace.PlaneType.ACTION_PLANE);
        assertThat(trace.targetPlane()).isEqualTo(CrossPlaneTrace.PlaneType.AGENT_RUNTIME);
        assertThat(trace.serviceName()).isEqualTo(serviceName);
        assertThat(trace.operationName()).isEqualTo(operationName);
        assertThat(trace.status()).isEqualTo(CrossPlaneTrace.TraceStatus.COMPLETED);
        assertThat(trace.startTime()).isEqualTo(startTime);
        assertThat(trace.endTime()).isEqualTo(endTime);
        assertThat(trace.durationMs()).isEqualTo(durationMs);
        assertThat(trace.attributes()).isEqualTo(attributes);
        assertThat(trace.events()).hasSize(2);
        assertThat(trace.correlationId()).isEqualTo(correlationId);
        assertThat(trace.userId()).isEqualTo(userId);
        assertThat(trace.resourceType()).isEqualTo(resourceType);
        assertThat(trace.resourceId()).isEqualTo(resourceId);
    }

    @Test
    @DisplayName("Should validate plane enum values")
    void shouldValidatePlaneEnumValues() {
        String[] validPlanes = {
            "DATA_CLOUD", "ACTION_PLANE", "AGENT_RUNTIME", 
            "MEDIA_PROCESSING", "CONNECTOR", "WORKFLOW", "EXTERNAL"
        };

        for (String plane : validPlanes) {
            CrossPlaneTrace.PlaneType planeType = CrossPlaneTrace.PlaneType.valueOf(plane);
            CrossPlaneTrace trace = CrossPlaneTrace.builder()
                .traceId("trc-test")
                .spanId("spn-test")
                .tenantId("tenant-1")
                .sourcePlane(planeType)
                .serviceName("TestService")
                .operationName("testOperation")
                .status(CrossPlaneTrace.TraceStatus.RUNNING)
                .build();
            assertThat(trace.sourcePlane()).isEqualTo(planeType);
        }
    }

    @Test
    @DisplayName("Should validate status enum values")
    void shouldValidateStatusEnumValues() {
        String[] validStatuses = {
            "PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED", "TIMEOUT"
        };

        for (String status : validStatuses) {
            CrossPlaneTrace.TraceStatus statusType = CrossPlaneTrace.TraceStatus.valueOf(status);
            CrossPlaneTrace trace = CrossPlaneTrace.builder()
                .traceId("trc-test")
                .spanId("spn-test")
                .tenantId("tenant-1")
                .sourcePlane(CrossPlaneTrace.PlaneType.DATA_CLOUD)
                .serviceName("TestService")
                .operationName("testOperation")
                .status(statusType)
                .build();
            assertThat(trace.status()).isEqualTo(statusType);
        }
    }

    @Test
    @DisplayName("Should create TraceEvent with required fields")
    void shouldCreateTraceEvent() {
        String eventType = "ERROR";
        String description = "Pipeline execution failed";
        Instant timestamp = Instant.now();
        Map<String, Object> metadata = Map.of("errorCode", "500", "errorMessage", "Internal error");

        CrossPlaneTrace.TraceEvent event = new CrossPlaneTrace.TraceEvent(eventType, description, timestamp, metadata);

        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.description()).isEqualTo(description);
        assertThat(event.timestamp()).isEqualTo(timestamp);
        assertThat(event.metadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("Should create TraceEvent with minimal fields")
    void shouldCreateTraceEventWithMinimalFields() {
        String eventType = "LOG";
        String description = "Operation started";
        Instant timestamp = Instant.now();

        CrossPlaneTrace.TraceEvent event = new CrossPlaneTrace.TraceEvent(eventType, description, timestamp, null);

        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.description()).isEqualTo(description);
        assertThat(event.timestamp()).isEqualTo(timestamp);
        assertThat(event.metadata()).isEqualTo(Map.of());
    }
}
