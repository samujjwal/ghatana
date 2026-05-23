package com.ghatana.yappc.services.evolve;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Port for handing approved evolution proposals to downstream lifecycle execution.
 *
 * @doc.type interface
 * @doc.purpose Trigger durable validate/generate/run handoff from approved evolve proposals
 * @doc.layer service
 * @doc.pattern Port
 */
public interface EvolutionExecutionHandoffService {

    /**
     * Records/dispatches one lifecycle execution handoff request.
     *
     * @param request handoff payload
     * @return persisted/accepted handoff descriptor
     */
    Promise<EvolutionExecutionHandoff> handoff(@NotNull EvolutionExecutionRequest request);

    /**
     * No-op handoff implementation for tests and isolated runs.
     *
     * @return no-op implementation
     */
    static EvolutionExecutionHandoffService noop() {
        return request -> Promise.of(new EvolutionExecutionHandoff(
                "handoff-disabled",
                "DISABLED",
                Instant.now(),
                Map.of("reason", "No execution handoff service configured")
        ));
    }

    /**
     * Immutable handoff request payload.
     */
    record EvolutionExecutionRequest(
            @NotNull String handoffId,
            @NotNull String proposalId,
            @NotNull String tenantId,
            @NotNull String projectId,
            @NotNull String productUnitIntentRef,
            @NotNull String requestedBy,
            @NotNull List<String> phases,
            @NotNull Instant requestedAt,
            @NotNull Map<String, Object> metadata
    ) {
    }

    /**
     * Immutable handoff acceptance descriptor.
     */
    record EvolutionExecutionHandoff(
            @NotNull String handoffId,
            @NotNull String status,
            @NotNull Instant acceptedAt,
            @NotNull Map<String, Object> metadata
    ) {
    }
}