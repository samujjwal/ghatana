package com.ghatana.yappc.services.phase;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Learn-phase workflow service for durable signal lifecycle.
 *
 * @doc.type class
 * @doc.purpose Resolves and updates durable learning signals with approval state
 * @doc.layer service
 * @doc.pattern Service
 */
public final class LearningWorkflowService {

    private final LearningSignalRepository repository;

    public LearningWorkflowService(@NotNull LearningSignalRepository repository) {
        this.repository = repository;
    }

    public static LearningWorkflowService noop() {
        return new LearningWorkflowService(LearningSignalRepository.noop());
    }

    public Promise<LearningWorkflowState> resolveLatest(
            String tenantId,
            String workspaceId,
            String projectId,
            String fallbackSourceEvent,
            double fallbackConfidence,
            String fallbackApprovalState,
            List<String> fallbackEvidenceIds
    ) {
        return repository.findLatest(tenantId, workspaceId, projectId)
                .map(signalOpt -> signalOpt
                        .map(LearningWorkflowService::toState)
                        .orElseGet(() -> LearningWorkflowState.fallback(
                                fallbackSourceEvent,
                                fallbackConfidence,
                                fallbackApprovalState,
                                fallbackEvidenceIds
                        )));
    }

    public Promise<Void> approveSignal(
            String tenantId,
            String workspaceId,
            String projectId,
            LearningWorkflowState state
    ) {
        return repository.save(toSignal(tenantId, workspaceId, projectId, state, "APPROVED"));
    }

    public Promise<Void> rejectSignal(
            String tenantId,
            String workspaceId,
            String projectId,
            LearningWorkflowState state
    ) {
        return repository.save(toSignal(tenantId, workspaceId, projectId, state, "REJECTED"));
    }

    private static LearningWorkflowState toState(LearningSignalRepository.LearningSignal signal) {
        return new LearningWorkflowState(
                signal.signal(),
                signal.sourceEvent(),
                signal.confidence(),
                signal.recommendation(),
                signal.approvalState(),
                signal.rollbackPath(),
                signal.evidenceIds()
        );
    }

    private static LearningSignalRepository.LearningSignal toSignal(
            String tenantId,
            String workspaceId,
            String projectId,
            LearningWorkflowState state,
            String approvalState
    ) {
        return new LearningSignalRepository.LearningSignal(
                "learn-signal-" + UUID.randomUUID(),
                tenantId,
                workspaceId,
                projectId,
                state.learnedSignal(),
                state.sourceEvent(),
                state.confidence(),
                state.recommendation(),
                approvalState,
                state.rollbackPath(),
                state.evidenceIds(),
                Instant.now()
        );
    }
}
