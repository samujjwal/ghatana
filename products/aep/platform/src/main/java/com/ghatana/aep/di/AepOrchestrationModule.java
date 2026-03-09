/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.orchestrator.cache.PipelineCache;
import com.ghatana.orchestrator.config.OrchestratorConfig;
import com.ghatana.orchestrator.core.Orchestrator;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import com.ghatana.orchestrator.loader.SpecFormatLoader;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.orchestrator.queue.impl.CheckpointAwareExecutionQueue;
import com.ghatana.orchestrator.store.CheckpointStore;
import com.ghatana.orchestrator.store.PipelineCheckpointRepository;
import com.ghatana.orchestrator.store.PostgresqlCheckpointStore;
import com.ghatana.orchestrator.store.StepCheckpointRepository;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

import java.time.Duration;

/**
 * ActiveJ DI module for AEP orchestration components.
 *
 * <p>Provides the orchestration layer — pipeline coordination, checkpointing,
 * execution queues, and agent step running:
 * <ul>
 *   <li>{@link Orchestrator} — central pipeline lifecycle manager</li>
 *   <li>{@link CheckpointStore} — execution state persistence</li>
 *   <li>{@link ExecutionQueue} — checkpoint-aware execution queue</li>
 *   <li>{@link OrchestratorConfig} — orchestrator configuration</li>
 *   <li>{@link PipelineCache} — in-memory pipeline cache with TTL</li>
 * </ul>
 *
 * <p><b>Dependencies:</b> Requires bindings from {@link AepCoreModule}
 * (for {@code Eventloop}), and from the observability layer
 * (for {@link MetricsCollector}).
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepCoreModule(),
 *     new AepObservabilityModule(),
 *     new AepOrchestrationModule()
 * );
 * Orchestrator orchestrator = injector.getInstance(Orchestrator.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for orchestration, checkpointing, and execution
 * @doc.layer product
 * @doc.pattern Module
 * @see Orchestrator
 * @see CheckpointStore
 * @see ExecutionQueue
 */
public class AepOrchestrationModule extends AbstractModule {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);

    /**
     * Provides the orchestrator configuration with production defaults.
     *
     * @return default orchestrator config
     */
    @Provides
    OrchestratorConfig orchestratorConfig() {
        return new OrchestratorConfig();
    }

    /**
     * Provides the pipeline cache with a configurable TTL.
     *
     * <p>The cache stores recently-loaded pipeline definitions to avoid
     * repeated repository lookups. TTL defaults to 10 minutes.
     *
     * @param metrics metrics collector for cache hit/miss tracking
     * @return pipeline cache instance
     */
    @Provides
    PipelineCache pipelineCache(MetricsCollector metrics) {
        return new PipelineCache(DEFAULT_CACHE_TTL, metrics);
    }

    /**
     * Provides the checkpoint store bound to its interface.
     *
     * <p>Uses {@link PostgresqlCheckpointStore} for durable execution state.
     * Requires JPA repository bindings for
     * {@link PipelineCheckpointRepository} and {@link StepCheckpointRepository},
     * which should be provided by a persistence module.
     *
     * @param pipelineRepository pipeline checkpoint JPA repository
     * @param stepRepository     step checkpoint JPA repository
     * @return checkpoint store
     */
    @Provides
    CheckpointStore checkpointStore(PipelineCheckpointRepository pipelineRepository,
                                    StepCheckpointRepository stepRepository) {
        return new PostgresqlCheckpointStore(pipelineRepository, stepRepository);
    }

    /**
     * Provides the execution queue bound to its interface.
     *
     * <p>Uses {@link CheckpointAwareExecutionQueue} which deduplicates
     * executions via the checkpoint store (idempotency guarantee).
     *
     * @param checkpointStore the checkpoint store for duplicate detection
     * @return execution queue
     */
    @Provides
    ExecutionQueue executionQueue(CheckpointStore checkpointStore) {
        return new CheckpointAwareExecutionQueue(checkpointStore);
    }

    /**
     * Provides the orchestrator — the central coordination component.
     *
     * <p>Wires together the pipeline cache, registry clients, config,
     * metrics, and spec loader. The orchestrator manages pipeline loading,
     * caching, refresh cycles, and execution dispatching.
     *
     * @param pipelineCache        cached pipeline store
     * @param agentRegistryClient  client for agent registry
     * @param pipelineRegistryClient client for pipeline registry
     * @param config               orchestrator configuration
     * @param metrics              metrics collector
     * @param specFormatLoader     pipeline spec format loader
     * @return orchestrator instance
     */
    @Provides
    Orchestrator orchestrator(
            PipelineCache pipelineCache,
            AgentRegistryClient agentRegistryClient,
            PipelineRegistryClient pipelineRegistryClient,
            OrchestratorConfig config,
            MetricsCollector metrics,
            SpecFormatLoader specFormatLoader) {
        return new Orchestrator(
                pipelineCache,
                agentRegistryClient,
                pipelineRegistryClient,
                config,
                metrics,
                specFormatLoader);
    }
}
