package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataCloudPlatformRunStatusWriter}.
 *
 * @doc.type class
 * @doc.purpose Verifies platform run lifecycle events persist Data Cloud runtime truth
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudPlatformRunStatusWriter")
class DataCloudPlatformRunStatusWriterTest extends EventloopTestBase {

    @Mock private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("ingests AEP run event with trace and evidence IDs")
    void ingestEvent_persistsRunStatusWithTraceAndEvidence() {
        when(dataCloudClient.save(eq("tenant-1"), eq("yappc_platform_runs"), any()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of("entity-1", "yappc_platform_runs", Map.of())));
        DataCloudPlatformRunStatusWriter writer = new DataCloudPlatformRunStatusWriter(dataCloudClient);
        Instant timestamp = Instant.parse("2026-05-26T12:00:00Z");
        DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("aep.platform.run.completed")
                .source("aep")
                .subjectId("run-1")
                .correlationId("corr-1")
                .traceContext("trace-1")
                .timestamp(timestamp)
                .payload(Map.of(
                        "workspaceId", "workspace-1",
                        "projectId", "project-1",
                        "phase", "run",
                        "evidenceIds", List.of("evidence-1", "evidence-2")
                ))
                .build();

        runPromise(() -> writer.ingestEvent("tenant-1", event));

        ArgumentCaptor<Map<String, Object>> dataCaptor = dataCaptor();
        verify(dataCloudClient).save(eq("tenant-1"), eq("yappc_platform_runs"), dataCaptor.capture());
        Map<String, Object> data = dataCaptor.getValue();
        assertThat(data)
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("projectId", "project-1")
                .containsEntry("phase", "RUN")
                .containsEntry("runId", "run-1")
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("platform", "aep")
                .containsEntry("traceId", "trace-1")
                .containsEntry("correlationId", "corr-1")
                .containsEntry("completedAt", timestamp.toString());
        assertThat(data.get("evidenceIds")).isEqualTo(List.of("evidence-1", "evidence-2"));
    }

    @Test
    @DisplayName("completed Kernel event updates the same run ID with terminal status")
    void ingestEvent_updatesRunStatusForKernelTerminalEvent() {
        when(dataCloudClient.save(eq("tenant-1"), eq("yappc_platform_runs"), any()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of("entity-1", "yappc_platform_runs", Map.of())));
        DataCloudPlatformRunStatusWriter writer = new DataCloudPlatformRunStatusWriter(dataCloudClient);
        Instant startedAt = Instant.parse("2026-05-26T12:00:00Z");
        Instant completedAt = Instant.parse("2026-05-26T12:05:00Z");
        DataCloudClient.Event event = DataCloudClient.Event.builder()
                .type("kernel.product-unit.run.failed")
                .source("kernel")
                .subjectId("kernel-run-1")
                .correlationId("corr-kernel")
                .timestamp(completedAt)
                .payload(Map.of(
                        "workspace_id", "workspace-1",
                        "project_id", "project-1",
                        "phase", "RUN",
                        "startedAt", startedAt.toString(),
                        "evidenceId", "kernel-evidence-1"
                ))
                .build();

        runPromise(() -> writer.ingestEvent("tenant-1", event));

        ArgumentCaptor<Map<String, Object>> dataCaptor = dataCaptor();
        verify(dataCloudClient).save(eq("tenant-1"), eq("yappc_platform_runs"), dataCaptor.capture());
        Map<String, Object> data = dataCaptor.getValue();
        assertThat(data)
                .containsEntry("runId", "kernel-run-1")
                .containsEntry("status", "FAILED")
                .containsEntry("platform", "kernel")
                .containsEntry("startedAt", startedAt.toString())
                .containsEntry("completedAt", completedAt.toString())
                .containsEntry("sourceEventType", "kernel.product-unit.run.failed");
        assertThat(data.get("evidenceIds")).isEqualTo(List.of("kernel-evidence-1"));
    }

    @Test
    @DisplayName("record rejects missing tenant instead of writing ambiguous runtime truth")
    void record_rejectsMissingTenant() {
        DataCloudPlatformRunStatusWriter writer = new DataCloudPlatformRunStatusWriter(dataCloudClient);
        PlatformRunStatusWriter.PlatformRunStatusRecord record =
                new PlatformRunStatusWriter.PlatformRunStatusRecord(
                        "",
                        "workspace-1",
                        "project-1",
                        "RUN",
                        "run-1",
                        "RUNNING",
                        "aep",
                        Instant.now(),
                        null,
                        "trace-1",
                        List.of("evidence-1"),
                        "aep.platform.run.started",
                        "corr-1");

        assertThatThrownBy(() -> runPromise(() -> writer.record(record)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId is required");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> dataCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
