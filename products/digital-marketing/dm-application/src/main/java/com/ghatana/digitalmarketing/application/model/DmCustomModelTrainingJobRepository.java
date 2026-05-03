package com.ghatana.digitalmarketing.application.model;

import com.ghatana.digitalmarketing.domain.model.DmCustomModelTrainingJob;
import com.ghatana.digitalmarketing.domain.model.DmCustomModelTrainingStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for custom model training job persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for custom model training job storage (DMOS-F5-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmCustomModelTrainingJobRepository {

    Promise<DmCustomModelTrainingJob> save(DmCustomModelTrainingJob job);

    Promise<DmCustomModelTrainingJob> update(DmCustomModelTrainingJob job);

    Promise<Optional<DmCustomModelTrainingJob>> findById(String id);

    Promise<List<DmCustomModelTrainingJob>> listByTenant(String tenantId);

    Promise<List<DmCustomModelTrainingJob>> listByTenantAndStatus(String tenantId, DmCustomModelTrainingStatus status);
}
