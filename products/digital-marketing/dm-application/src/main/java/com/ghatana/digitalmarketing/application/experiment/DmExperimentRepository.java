package com.ghatana.digitalmarketing.application.experiment;

import com.ghatana.digitalmarketing.domain.experiment.DmExperiment;
import com.ghatana.digitalmarketing.domain.experiment.DmExperimentStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for experiment persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for A/B experiment storage (DMOS-F3-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmExperimentRepository {

    Promise<DmExperiment> save(DmExperiment experiment);

    Promise<DmExperiment> update(DmExperiment experiment);

    Promise<Optional<DmExperiment>> findById(String id);

    Promise<List<DmExperiment>> listByTenant(String tenantId);

    Promise<List<DmExperiment>> listByStatus(String tenantId, DmExperimentStatus status);
}
