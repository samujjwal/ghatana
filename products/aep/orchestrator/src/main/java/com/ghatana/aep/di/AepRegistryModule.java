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
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

/**
 * ActiveJ DI module for AEP registry bindings (v2.5+).
 *
 * <p>Provides HTTP client bindings for legacy access to agent and pipeline registries.
 * Agent discovery is now centralized through AepAgentRegistryController.
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for AEP registry clients
 * @doc.layer product
 * @doc.pattern Module
 * @since 1.0.0
 * @revised 2.5.0 — agent registry unified at AEP level
 */
public class AepRegistryModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(AepRegistryModule.class);

    @Override
    protected void configure() {
        log.info("Configuring AEP registry module (v2.5)");
    }

    /**
     * Provides an {@link AgentRegistryClient} backed by Data-Cloud's HTTP API.
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
        var dcClient = new DataCloudAgentRegistryClient(dcBaseUrl);
        // Adapt the DataCloud client to the orchestrator.client.AgentRegistryClient expected by Orchestrator
        return new AgentRegistryClient() {
            @Override
            public Promise<Optional<AgentRegistryClient.AgentInfo>> getAgent(String agentRef) {
                return dcClient.getAgent(agentRef)
                        .map(opt -> opt.map(info -> new AgentRegistryClient.AgentInfo(
                                info.getAgentId(), info.getName(), info.getStatus())));
            }

            @Override
            public Promise<Boolean> isHealthy() {
                return dcClient.isHealthy();
            }
        };
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
