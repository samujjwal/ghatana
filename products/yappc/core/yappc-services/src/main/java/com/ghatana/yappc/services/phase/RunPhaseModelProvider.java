package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Run phase model provider.
 *
 * @doc.type class
 * @doc.purpose Build Run phase models
 * @doc.layer product
 * @doc.pattern PhaseModelProvider
 */
public final class RunPhaseModelProvider {

    public RunPhaseModel build(PhasePanelInput input) {
        String runId = input.platformRunStatus() == null ? "" : input.platformRunStatus().runId();
        String status = input.platformRunStatus() == null
                ? input.healthSignals().runtime().status()
                : input.platformRunStatus().status();
        return new RunPhaseModel(
                runId,
                status,
                input.platformRunStatus() == null ? "" : input.platformRunStatus().promoteTarget(),
                input.platformRunStatus() == null ? "" : input.platformRunStatus().rollbackTarget(),
                input.platformRunStatus() != null && input.platformRunStatus().rollbackSupported(),
                input.platformRunStatus() != null && !input.platformRunStatus().promoteTarget().isBlank(),
                input.platformRunStatus() == null || input.platformRunStatus().remediationHint().isBlank()
                        ? List.of()
                        : List.of(input.platformRunStatus().remediationHint()),
                input.activityFeed().stream().map(entry -> entry.id() + ":" + entry.outcome()).toList()
        );
    }
}
