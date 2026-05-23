package com.ghatana.yappc.services.evolve;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Outbound port used by evolve handoff workers to trigger lifecycle execution.
 *
 * @doc.type interface
 * @doc.purpose Dispatch validate/generate/run execution requests from approved evolve proposals
 * @doc.layer service
 * @doc.pattern Port
 */
public interface EvolutionLifecycleExecutionDispatcher {

    /**
     * Dispatches one lifecycle execution request.
     *
     * @param request normalized dispatch request
     * @return execution identifier from downstream lifecycle runtime
     */
    Promise<String> dispatch(@NotNull EvolutionLifecycleExecutionRequest request);

    /**
     * Immutable dispatch request payload.
     */
    record EvolutionLifecycleExecutionRequest(
            @NotNull String handoffId,
            @NotNull String proposalId,
            @NotNull String tenantId,
            @NotNull String projectId,
            @NotNull String productUnitIntentRef,
            @NotNull String requestedBy,
            @NotNull List<String> phases,
            @NotNull Map<String, Object> metadata
    ) {
    }
}
