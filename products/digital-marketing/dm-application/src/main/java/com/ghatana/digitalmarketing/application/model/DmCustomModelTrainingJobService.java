package com.ghatana.digitalmarketing.application.model;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.model.DmCustomModelTrainingJob;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing custom AI model training jobs.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for custom model training management (DMOS-F5-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmCustomModelTrainingJobService {

    Promise<DmCustomModelTrainingJob> enqueue(DmOperationContext ctx, EnqueueTrainingJobCommand cmd);

    Promise<DmCustomModelTrainingJob> markTraining(DmOperationContext ctx, String jobId);

    Promise<DmCustomModelTrainingJob> markEvaluating(DmOperationContext ctx, String jobId);

    Promise<DmCustomModelTrainingJob> markComplete(DmOperationContext ctx, String jobId, double evalScore, String artifactRef);

    Promise<DmCustomModelTrainingJob> markFailed(DmOperationContext ctx, String jobId, String reason);

    Promise<DmCustomModelTrainingJob> cancel(DmOperationContext ctx, String jobId);

    Promise<Optional<DmCustomModelTrainingJob>> findById(DmOperationContext ctx, String jobId);

    Promise<List<DmCustomModelTrainingJob>> listByTenant(DmOperationContext ctx);

    record EnqueueTrainingJobCommand(
            String modelName,
            String baseModelId,
            String trainingDataRef,
            String workspaceId
    ) {
        public EnqueueTrainingJobCommand {
            if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName must not be blank");
            if (trainingDataRef == null || trainingDataRef.isBlank()) throw new IllegalArgumentException("trainingDataRef must not be blank");
        }
    }
}
