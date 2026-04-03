/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.config.EnvConfig;
import com.ghatana.aep.engine.controller.AepAgentRegistryController;
import com.ghatana.aep.engine.controller.HttpHandlerUtils;
import com.ghatana.aep.engine.registry.AepCentralRegistryService;
import com.ghatana.aep.engine.registry.AgentExecutionService;
import com.ghatana.aep.integration.registry.CatalogRegistryContractAdapter;
import com.ghatana.aep.integration.registry.DataCloudAgentRegistryClient;
import com.ghatana.aep.integration.registry.DataCloudPipelineRegistryClientImpl;
import com.ghatana.aep.integration.registry.NoOpPipelineRegistryClient;
import com.ghatana.aep.registry.AgentRegistryContracts;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.orchestrator.client.AgentRegistryClient;
import com.ghatana.orchestrator.client.PipelineRegistryClient;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.promise.Promise;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * Provides {@link AgentRegistryContracts} backed by the ServiceLoader-discovered
     * {@link CatalogRegistry}. This adapter is the bridge between the catalog's
     * in-memory {@code allDefinitions/findById/findByCapability} surface and the
     * contract interface expected by {@link AepCentralRegistryService}.
     *
     * @param catalogRegistry catalog registry (provided by AepOrchestrationModule)
     * @return catalog-backed contract adapter
     *
     * @doc.type method
     * @doc.purpose Bridges ServiceLoader CatalogRegistry to AgentRegistryContracts
     * @doc.layer product
     * @doc.pattern Adapter, Factory
     */
    @Provides
    AgentRegistryContracts agentRegistryContracts(CatalogRegistry catalogRegistry) {
        log.info("Wiring AgentRegistryContracts via CatalogRegistryContractAdapter");
        return new CatalogRegistryContractAdapter(catalogRegistry);
    }

    /**
     * Provides the orchestrator-level {@link AepCentralRegistryService} wired to
     * the catalog-backed {@link AgentRegistryContracts} backend.
     *
     * <p>In production this service merges the ServiceLoader-discovered catalog
     * (via the adapter) with any in-process registrations. Local registrations
     * take priority over catalog entries on conflict.
     *
     * @param backendRegistry catalog-backed registry contracts (never null)
     * @return wired registry service
     *
     * @doc.type method
     * @doc.purpose Provides the orchestrator unified registry service bound to catalog backend
     * @doc.layer product
     * @doc.pattern Factory
     */
    @Provides
    AepCentralRegistryService aepCentralRegistryService(AgentRegistryContracts backendRegistry) {
        log.info("Creating AepCentralRegistryService with catalog-backend delegation");
        return new AepCentralRegistryService(backendRegistry);
    }

    /**
     * Provides the {@link AgentExecutionService} used by {@link AepAgentRegistryController}.
     *
     * @return default agent execution service
     *
     * @doc.type method
     * @doc.purpose Provides AgentExecutionService for the registry controller
     * @doc.layer product
     * @doc.pattern Factory
     */
    @Provides
    AgentExecutionService agentExecutionService() {
        return new AgentExecutionService();
    }

    /**
     * Provides the unified {@link AepAgentRegistryController} that handles the
     * {@code /api/v1/agents} endpoint family.
     *
     * @param registryService the central registry service
     * @param executionService the agent execution service
     * @return wired registry controller
     *
     * @doc.type method
     * @doc.purpose Provides the unified registryController for agent discovery and execution
     * @doc.layer product
     * @doc.pattern Factory
     */
    @Provides
    AepAgentRegistryController aepAgentRegistryController(
            AepCentralRegistryService registryService, AgentExecutionService executionService) {
        log.info("Creating AepAgentRegistryController (unified catalog-backed registry)");
        return new AepAgentRegistryController(registryService, executionService, new HttpHandlerUtils());
    }
}
