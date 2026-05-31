/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.operations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pass 9: Tests for OperationRecord with traceId and requestId support.
 *
 * @doc.type test
 * @doc.purpose Validate OperationRecord creation, transitions, and response generation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("OperationRecord Tests")
class OperationRecordTest {

    @Test
    @DisplayName("Should create operation record with traceId and requestId")
    void shouldCreateWithTraceIdAndRequestId() {
        String traceId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        
        OperationRecord record = OperationRecord.create(
            "tenant-123",
            traceId,
            requestId,
            OperationKind.WORKFLOW,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-456",
            "execute",
            "Pipeline execution started",
            "user-789",
            "corr-abc",
            true,
            Map.of("pipelineExecutionId", "exec-xyz")
        );

        assertThat(record.operationId()).isNotNull().isNotEmpty();
        assertThat(record.traceId()).isEqualTo(traceId);
        assertThat(record.requestId()).isEqualTo(requestId);
        assertThat(record.tenantId()).isEqualTo("tenant-123");
        assertThat(record.kind()).isEqualTo(OperationKind.WORKFLOW);
        assertThat(record.status()).isEqualTo(OperationStatus.RUNNING);
        assertThat(record.action()).isEqualTo("execute");
        assertThat(record.cancellable()).isTrue();
        assertThat(record.metadata()).containsKey("pipelineExecutionId");
    }

    @Test
    @DisplayName("Should default traceId and requestId to operationId when not provided")
    void shouldDefaultTraceIdAndRequestId() {
        OperationRecord record = OperationRecord.create(
            "tenant-123",
            OperationKind.WORKFLOW,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-456",
            "execute",
            "Pipeline execution started",
            "user-789",
            "corr-abc",
            true,
            Map.of()
        );

        assertThat(record.traceId()).isEqualTo(record.operationId());
        assertThat(record.requestId()).isEqualTo(record.operationId());
    }

    @Test
    @DisplayName("Should validate required fields")
    void shouldValidateRequiredFields() {
        assertThatThrownBy(() -> OperationRecord.create(
            null,
            OperationKind.WORKFLOW,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-456",
            "execute",
            "summary",
            "user",
            "corr",
            true,
            Map.of()
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> OperationRecord.create(
            "",
            OperationKind.WORKFLOW,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-456",
            "execute",
            "summary",
            "user",
            "corr",
            true,
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should transition status and preserve traceId/requestId")
    void shouldTransitionStatusPreservingIds() {
        String traceId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        
        OperationRecord record = OperationRecord.create(
            "tenant-123",
            traceId,
            requestId,
            OperationKind.WORKFLOW,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-456",
            "execute",
            "Pipeline execution started",
            "user-789",
            "corr-abc",
            true,
            Map.of()
        );

        OperationRecord completed = record.transition(
            OperationStatus.COMPLETED,
            "Pipeline completed successfully",
            Map.of("result", "success")
        );

        assertThat(completed.operationId()).isEqualTo(record.operationId());
        assertThat(completed.traceId()).isEqualTo(traceId);
        assertThat(completed.requestId()).isEqualTo(requestId);
        assertThat(completed.status()).isEqualTo(OperationStatus.COMPLETED);
        assertThat(completed.detail()).isEqualTo("Pipeline completed successfully");
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.cancellable()).isFalse();
    }

    @Test
    @DisplayName("Should include traceId and requestId in response")
    void shouldIncludeTraceIdAndRequestIdInResponse() {
        String traceId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        
        OperationRecord record = OperationRecord.create(
            "tenant-123",
            traceId,
            requestId,
            OperationKind.MEDIA_PROCESSING,
            OperationStatus.ACCEPTED,
            "media-artifact",
            "artifact-789",
            "transcribe",
            "Transcription requested",
            "user-456",
            "corr-def",
            true,
            Map.of("mediaJobId", "job-ghi")
        );

        Map<String, Object> response = record.toResponse();

        assertThat(response).containsKey("operationId");
        assertThat(response).containsKey("traceId");
        assertThat(response).containsKey("requestId");
        assertThat(response.get("traceId")).isEqualTo(traceId);
        assertThat(response.get("requestId")).isEqualTo(requestId);
        assertThat(response.get("kind")).isEqualTo("MEDIA_PROCESSING");
        assertThat(response.get("status")).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("Should identify terminal statuses correctly")
    void shouldIdentifyTerminalStatuses() {
        assertThat(OperationRecord.terminal(OperationStatus.SUCCEEDED)).isTrue();
        assertThat(OperationRecord.terminal(OperationStatus.FAILED)).isTrue();
        assertThat(OperationRecord.terminal(OperationStatus.CANCELLED)).isTrue();
        assertThat(OperationRecord.terminal(OperationStatus.BLOCKED)).isTrue();
        assertThat(OperationRecord.terminal(OperationStatus.RUNNING)).isFalse();
        assertThat(OperationRecord.terminal(OperationStatus.INITIATED)).isFalse();
    }

    @Test
    @DisplayName("Should merge metadata on transition")
    void shouldMergeMetadataOnTransition() {
        OperationRecord record = OperationRecord.create(
            "tenant-123",
            OperationKind.WORKFLOW,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-456",
            "execute",
            "Pipeline execution started",
            "user-789",
            "corr-abc",
            true,
            Map.of("step1", "completed")
        );

        OperationRecord transitioned = record.transition(
            OperationStatus.COMPLETED,
            "All steps completed",
            Map.of("step2", "completed", "duration", "5000ms")
        );

        assertThat(transitioned.metadata()).containsEntry("step1", "completed");
        assertThat(transitioned.metadata()).containsEntry("step2", "completed");
        assertThat(transitioned.metadata()).containsEntry("duration", "5000ms");
    }

    @Test
    @DisplayName("Should handle null metadata gracefully")
    void shouldHandleNullMetadata() {
        OperationRecord record = OperationRecord.create(
            "tenant-123",
            OperationKind.WORKFLOW,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-456",
            "execute",
            "Pipeline execution started",
            "user-789",
            "corr-abc",
            true,
            null
        );

        assertThat(record.metadata()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Should copy metadata for immutability")
    void shouldCopyMetadataForImmutability() {
        Map<String, Object> originalMetadata = new java.util.HashMap<>();
        originalMetadata.put("key", "value");
        
        OperationRecord record = OperationRecord.create(
            "tenant-123",
            OperationKind.WORKFLOW,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-456",
            "execute",
            "Pipeline execution started",
            "user-789",
            "corr-abc",
            true,
            originalMetadata
        );

        originalMetadata.put("newKey", "newValue");

        assertThat(record.metadata()).doesNotContainKey("newKey");
    }
}
