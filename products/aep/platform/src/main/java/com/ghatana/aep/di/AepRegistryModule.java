/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.config.EnvConfig;
import com.ghatana.aep.integration.registry.DataCloudAgentRegistryClient;
import com.ghatana.aep.integration.registry.DataCloudPipelineRegistryClientImpl;
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
 * @see DataCloudPipelineRegistryClientImpl
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
     * Provides a {@link PipelineRegistryClient} backed by Data-Cloud's pipeline HTTP API.
     *
     * <p>The implementation is selected by the {@code AEP_PIPELINE_REGISTRY_MODE} environment
     * variable:
     * <ul>
     *   <li>{@code datacloud} (default) — {@link DataCloudPipelineRegistryClientImpl} calling
     *       {@code GET/POST /api/v1/pipelines} on the Data-Cloud service.</li>
     *   <li>{@code noop} — {@link NoOpPipelineRegistryClient} that returns empty collections;
     *       useful when running AEP without a Data-Cloud service.</li>
     * </ul>
     *
     * @return pipeline registry client backed by Data-Cloud, or a no-op stub
     *
     * @doc.type method
     * @doc.purpose Provides env-driven PipelineRegistryClient (datacloud vs. noop)
     * @doc.layer product
     * @doc.pattern Factory, Strategy
     */
    @Provides
    PipelineRegistryClient pipelineRegistryClient() {
        EnvConfig env = EnvConfig.fromSystem();
        String mode = System.getenv().getOrDefault("AEP_PIPELINE_REGISTRY_MODE", "datacloud");
        if ("noop".equalsIgnoreCase(mode)) {
            return new NoOpPipelineRegistryClient();
        }
        return new DataCloudPipelineRegistryClientImpl(env.aepDcBaseUrl());
    }
}
