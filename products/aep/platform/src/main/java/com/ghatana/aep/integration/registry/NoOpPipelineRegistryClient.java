/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.integration.registry;

import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * In-memory (no-op) {@link PipelineRegistryClient} used until pipelines are stored in Data-Cloud.
 *
 * <p>Pipeline definitions are currently managed locally within AEP via the operator catalog
 * and YAML-based pipeline specs. A future migration will store pipeline definitions in
 * Data-Cloud's {@code dc_pipelines} collection, at which point this stub will be replaced
 * by a {@code DataCloudPipelineRegistryClientImpl} that calls the DC HTTP API.
 *
 * <p><b>AEP-P5: </b>This stub satisfies the ActiveJ DI injection requirement for
 * {@link PipelineRegistryClient} in {@link com.ghatana.orchestrator.core.Orchestrator}.
 *
 * @doc.type class
 * @doc.purpose Stub PipelineRegistryClient — returns empty collections until DC pipeline storage is ready
 * @doc.layer product
 * @doc.pattern Stub, Null Object
 * @since 1.0.0
 */
public final class NoOpPipelineRegistryClient implements PipelineRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpPipelineRegistryClient.class);

    /**
     * {@inheritDoc}
     *
     * <p>Always returns an empty list. Pipeline definitions are resolved locally
     * via the operator catalog until DC pipeline storage is connected.
     */
    @Override
    public Promise<List<OrchestratorPipelineEntity>> listAllPipelines() {
        log.debug("NoOpPipelineRegistryClient.listAllPipelines() — returning empty list (stub)");
        return Promise.of(Collections.emptyList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Always returns {@code Optional.empty()} (stub).
     */
    @Override
    public Promise<Optional<OrchestratorPipelineEntity>> getPipeline(String pipelineId) {
        log.debug("NoOpPipelineRegistryClient.getPipeline({}) — returning empty (stub)", pipelineId);
        return Promise.of(Optional.empty());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Always returns an empty list (stub).
     */
    @Override
    public Promise<List<OrchestratorPipelineEntity>> listPipelinesForTenant(String tenantId) {
        log.debug("NoOpPipelineRegistryClient.listPipelinesForTenant({}) — returning empty (stub)", tenantId);
        return Promise.of(Collections.emptyList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} — the stub is always "healthy" since it never fails.
     */
    @Override
    public Promise<Boolean> isHealthy() {
        return Promise.of(Boolean.TRUE);
    }
}
