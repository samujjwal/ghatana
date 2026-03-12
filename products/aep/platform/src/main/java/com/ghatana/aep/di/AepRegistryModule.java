/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.config.EnvConfig;
import com.ghatana.aep.integration.registry.DataCloudAgentRegistryClient;
import com.ghatana.aep.integration.registry.NoOpPipelineRegistryClient;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * ActiveJ DI module for AEP registry client bindings (AEP-P5).
 *
 * <p>Provides the registry client implementations required by
 * {@link com.ghatana.orchestrator.core.Orchestrator}:
 * <ul>
 *   <li>{@link AgentRegistryClient} — {@link DataCloudAgentRegistryClient} backed by the
 *       Data-Cloud HTTP agent registry ({@code /api/v1/agents}).</li>
 *   <li>{@link PipelineRegistryClient} — {@link NoOpPipelineRegistryClient} stub until
 *       pipelines are stored in Data-Cloud.</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>The Data-Cloud base URL is read from the {@code AEP_DC_BASE_URL} environment variable
 * (default: {@code http://localhost:8085}).
 *
 * <h2>Installation</h2>
 * <p>Install this module together with {@link AepOrchestrationModule}:
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepCoreModule(),
 *     new AepOrchestrationModule(),
 *     new AepRegistryModule()   // ← provides AgentRegistryClient + PipelineRegistryClient
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for DataCloud-backed agent/pipeline registry clients
 * @doc.layer product
 * @doc.pattern Module
 * @see DataCloudAgentRegistryClient
 * @see NoOpPipelineRegistryClient
 * @see AepOrchestrationModule
 * @since 1.0.0
 */
public class AepRegistryModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bindings are provided via @Provides methods below.
    }

    /**
     * Provides an {@link AgentRegistryClient} backed by Data-Cloud's agent HTTP API.
     *
     * <p>The Data-Cloud base URL is resolved from {@code AEP_DC_BASE_URL}
     * (default: {@code http://localhost:8085}).
     *
     * @return Data-Cloud-backed agent registry client
     *
     * @doc.type method
     * @doc.purpose Provides DataCloudAgentRegistryClient
     * @doc.layer product
     * @doc.pattern Factory
     */
    @Provides
    AgentRegistryClient agentRegistryClient() {
        EnvConfig env = EnvConfig.fromSystem();
        String dcBaseUrl = env.aepDcBaseUrl();
        return new DataCloudAgentRegistryClient(dcBaseUrl);
    }

    /**
     * Provides a stub {@link PipelineRegistryClient} until pipelines are stored in Data-Cloud.
     *
     * <p>Replaced by a DataCloud-backed implementation in a future iteration when the
     * {@code dc_pipelines} collection is ready.
     *
     * @return no-op pipeline registry client stub
     *
     * @doc.type method
     * @doc.purpose Provides stub PipelineRegistryClient (Null Object pattern)
     * @doc.layer product
     * @doc.pattern Factory, Null Object
     */
    @Provides
    PipelineRegistryClient pipelineRegistryClient() {
        return new NoOpPipelineRegistryClient();
    }
}
