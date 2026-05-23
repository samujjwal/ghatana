package com.ghatana.yappc.services.evolve;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataCloudEvolutionExecutionHandoffServiceTest extends EventloopTestBase {

    @Test
    void shouldPersistQueuedHandoffRecord() {
        DataCloudClient dataCloudClient = mock(DataCloudClient.class);
        DataCloudEvolutionExecutionHandoffService service = new DataCloudEvolutionExecutionHandoffService(dataCloudClient);

        when(dataCloudClient.save(eq("tenant-alpha"), eq("yappc_evolution_execution_handoffs"), anyMap()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of(
                        "handoff-1",
                        "yappc_evolution_execution_handoffs",
                        Map.of("status", "QUEUED")
                )));

        EvolutionExecutionHandoffService.EvolutionExecutionRequest request =
                new EvolutionExecutionHandoffService.EvolutionExecutionRequest(
                        "handoff-1",
                        "proposal-123",
                        "tenant-alpha",
                        "project-99",
                        "intent-7",
                        "reviewer-a",
                        List.of("validate", "generate", "run"),
                        Instant.now(),
                        Map.of("decision", "APPROVED")
                );

        EvolutionExecutionHandoffService.EvolutionExecutionHandoff handoff =
                runPromise(() -> service.handoff(request));

        assertEquals("handoff-1", handoff.handoffId());
        assertEquals("QUEUED", handoff.status());
        assertNotNull(handoff.acceptedAt());
        verify(dataCloudClient).save(
                eq("tenant-alpha"),
                eq("yappc_evolution_execution_handoffs"),
                argThat(doc -> "handoff-1".equals(doc.get("handoffId"))
                        && "proposal-123".equals(doc.get("proposalId"))
                        && "QUEUED".equals(doc.get("status"))
                        && "intent-7".equals(doc.get("productUnitIntentRef"))));
    }
}
