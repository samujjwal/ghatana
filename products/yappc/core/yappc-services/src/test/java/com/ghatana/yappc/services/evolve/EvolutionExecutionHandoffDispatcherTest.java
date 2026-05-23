package com.ghatana.yappc.services.evolve;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvolutionExecutionHandoffDispatcherTest extends EventloopTestBase {

    @Test
    void shouldDispatchQueuedHandoffsAndMarkDispatched() {
        DataCloudClient dataCloudClient = mock(DataCloudClient.class);
        EvolutionLifecycleExecutionDispatcher lifecycleDispatcher = mock(EvolutionLifecycleExecutionDispatcher.class);
        EvolutionExecutionHandoffDispatcher dispatcher =
                new EvolutionExecutionHandoffDispatcher(dataCloudClient, lifecycleDispatcher);

        Map<String, Object> queued = Map.ofEntries(
                Map.entry("id", "handoff-1"),
                Map.entry("handoffId", "handoff-1"),
                Map.entry("proposalId", "proposal-1"),
                Map.entry("tenantId", "tenant-a"),
                Map.entry("projectId", "project-a"),
                Map.entry("productUnitIntentRef", "intent-1"),
                Map.entry("requestedBy", "reviewer"),
                Map.entry("phases", List.of("validate", "generate", "run")),
                Map.entry("status", "QUEUED"),
                Map.entry("requestedAt", Instant.now().toString()),
                Map.entry("metadata", Map.of("decision", "APPROVED"))
        );

        when(dataCloudClient.query(eq("tenant-a"), eq("yappc_evolution_execution_handoffs"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of("handoff-1", "yappc_evolution_execution_handoffs", queued))));
        when(lifecycleDispatcher.dispatch(any(EvolutionLifecycleExecutionDispatcher.EvolutionLifecycleExecutionRequest.class)))
                .thenReturn(Promise.of("exec-123"));
        when(dataCloudClient.save(eq("tenant-a"), eq("yappc_evolution_execution_handoffs"), anyMap()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of("handoff-1", "yappc_evolution_execution_handoffs", queued)));

        EvolutionExecutionHandoffDispatcher.DispatchSummary summary =
                runPromise(() -> dispatcher.dispatchQueued("tenant-a", 10));

        assertEquals(1, summary.queued());
        assertEquals(1, summary.dispatched());
        assertEquals(0, summary.failed());
        verify(dataCloudClient).save(
                eq("tenant-a"),
                eq("yappc_evolution_execution_handoffs"),
                argThat(doc -> "DISPATCHED".equals(doc.get("status"))
                        && "exec-123".equals(doc.get("executionId"))
                        && doc.containsKey("dispatchedAt")));
    }

    @Test
    void shouldMarkFailedWhenLifecycleDispatchFails() {
        DataCloudClient dataCloudClient = mock(DataCloudClient.class);
        EvolutionLifecycleExecutionDispatcher lifecycleDispatcher = mock(EvolutionLifecycleExecutionDispatcher.class);
        EvolutionExecutionHandoffDispatcher dispatcher =
                new EvolutionExecutionHandoffDispatcher(dataCloudClient, lifecycleDispatcher);

        Map<String, Object> queued = Map.ofEntries(
                Map.entry("id", "handoff-2"),
                Map.entry("handoffId", "handoff-2"),
                Map.entry("proposalId", "proposal-2"),
                Map.entry("tenantId", "tenant-a"),
                Map.entry("projectId", "project-a"),
                Map.entry("productUnitIntentRef", "intent-2"),
                Map.entry("requestedBy", "reviewer"),
                Map.entry("phases", List.of("validate", "generate", "run")),
                Map.entry("status", "QUEUED"),
                Map.entry("requestedAt", Instant.now().toString()),
                Map.entry("metadata", Map.of("decision", "APPROVED"))
        );

        when(dataCloudClient.query(eq("tenant-a"), eq("yappc_evolution_execution_handoffs"), any(DataCloudClient.Query.class)))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of("handoff-2", "yappc_evolution_execution_handoffs", queued))));
        when(lifecycleDispatcher.dispatch(any(EvolutionLifecycleExecutionDispatcher.EvolutionLifecycleExecutionRequest.class)))
                .thenReturn(Promise.ofException(new RuntimeException("downstream unavailable")));
        when(dataCloudClient.save(eq("tenant-a"), eq("yappc_evolution_execution_handoffs"), anyMap()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of("handoff-2", "yappc_evolution_execution_handoffs", queued)));

        EvolutionExecutionHandoffDispatcher.DispatchSummary summary =
                runPromise(() -> dispatcher.dispatchQueued("tenant-a", 10));

        assertEquals(1, summary.queued());
        assertEquals(0, summary.dispatched());
        assertEquals(1, summary.failed());
        verify(dataCloudClient).save(
                eq("tenant-a"),
                eq("yappc_evolution_execution_handoffs"),
                argThat(doc -> "FAILED".equals(doc.get("status"))
                        && "downstream unavailable".equals(doc.get("failureReason"))
                        && doc.containsKey("failedAt")));
    }
}
