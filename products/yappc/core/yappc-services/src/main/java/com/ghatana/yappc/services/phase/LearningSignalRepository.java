package com.ghatana.yappc.services.phase;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository contract for durable learning signals.
 *
 * @doc.type interface
 * @doc.purpose Provides durable read/write operations for Learn workflow state
 * @doc.layer service
 * @doc.pattern Repository Port
 */
public interface LearningSignalRepository {

    Promise<Optional<LearningSignal>> findLatest(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId
    );

    Promise<Void> save(@NotNull LearningSignal signal);

    static LearningSignalRepository noop() {
        return new LearningSignalRepository() {
            @Override
            public Promise<Optional<LearningSignal>> findLatest(
                    @NotNull String tenantId,
                    @NotNull String workspaceId,
                    @NotNull String projectId
            ) {
                return Promise.of(Optional.empty());
            }

            @Override
            public Promise<Void> save(@NotNull LearningSignal signal) {
                return Promise.complete();
            }
        };
    }

    record LearningSignal(
            String signalId,
            String tenantId,
            String workspaceId,
            String projectId,
            String signal,
            String sourceEvent,
            double confidence,
            String recommendation,
            String approvalState,
            String rollbackPath,
            List<String> evidenceIds,
            Instant createdAt
    ) {
    }
}
