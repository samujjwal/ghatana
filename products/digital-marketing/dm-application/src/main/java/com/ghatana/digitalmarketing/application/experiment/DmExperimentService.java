package com.ghatana.digitalmarketing.application.experiment;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.experiment.DmExperiment;
import com.ghatana.digitalmarketing.domain.experiment.DmExperiment.DmExperimentVariant;
import com.ghatana.digitalmarketing.domain.experiment.DmExperimentStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for A/B experiment lifecycle management.
 *
 * @doc.type interface
 * @doc.purpose Create, run, conclude, and query marketing experiments (DMOS-F3-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmExperimentService {

    Promise<DmExperiment> create(DmOperationContext ctx, CreateExperimentCommand command);

    Promise<DmExperiment> start(DmOperationContext ctx, String experimentId);

    Promise<DmExperiment> conclude(DmOperationContext ctx, String experimentId, String winnerVariantId);

    Promise<DmExperiment> cancel(DmOperationContext ctx, String experimentId);

    Promise<Optional<DmExperiment>> findById(DmOperationContext ctx, String experimentId);

    Promise<List<DmExperiment>> listByTenant(DmOperationContext ctx);

    Promise<List<DmExperiment>> listByStatus(DmOperationContext ctx, DmExperimentStatus status);

    /**
     * Command to create an A/B experiment.
     */
    record CreateExperimentCommand(
        String name,
        String hypothesis,
        List<DmExperimentVariant> variants
    ) {
        public CreateExperimentCommand {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(variants, "variants must not be null");
            if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        }
    }
}
