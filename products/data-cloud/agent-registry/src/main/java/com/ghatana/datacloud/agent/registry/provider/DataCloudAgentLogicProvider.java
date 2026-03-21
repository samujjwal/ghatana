/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry.provider;

import com.ghatana.agent.api.AgentConfig;
import com.ghatana.agent.api.TypedAgent;
import com.ghatana.agent.spi.AgentLogicProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Data Cloud agent logic provider — resolves {@code implementationRef}s
 * prefixed with {@code data-cloud:} to concrete Data Cloud agent
 * implementations.
 *
 * <p>Bridges the platform's {@link AgentLogicProvider} SPI with Data Cloud's
 * data-processing and analytics agents.
 *
 * <h3>Supported refs</h3>
 * <pre>
 *   data-cloud:agent.data-cloud.schema-validator
 *   data-cloud:agent.data-cloud.data-sync
 *   data-cloud:agent.data-cloud.anomaly-detector
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Data Cloud AgentLogicProvider implementation
 * @doc.layer product
 * @doc.pattern Service, Factory
 */
public class DataCloudAgentLogicProvider implements AgentLogicProvider {

    private static final Logger log = LoggerFactory.getLogger(DataCloudAgentLogicProvider.class);

    public static final String PROVIDER_ID = "data-cloud";

    private final Map<String, Function<AgentConfig, TypedAgent<?, ?>>> factories =
            new ConcurrentHashMap<>();

    public DataCloudAgentLogicProvider() {
        registerFactory("data-cloud:agent.data-cloud.schema-validator", this::createStubAgent);
        registerFactory("data-cloud:agent.data-cloud.data-sync", this::createStubAgent);
        registerFactory("data-cloud:agent.data-cloud.anomaly-detector", this::createStubAgent);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getProviderName() {
        return "Data Cloud Agent Provider";
    }

    @Override
    public Set<String> getSupportedRefs() {
        return Set.copyOf(factories.keySet());
    }

    @Override
    public TypedAgent<?, ?> createAgent(String implementationRef, AgentConfig config) {
        Function<AgentConfig, TypedAgent<?, ?>> factory = factories.get(implementationRef);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unsupported implementationRef: " + implementationRef
                            + ". Supported: " + factories.keySet());
        }
        log.info("Creating Data Cloud agent for ref '{}', agentId='{}'",
                implementationRef, config.getAgentId());
        return factory.apply(config);
    }

    /**
     * Registers a factory for a specific {@code implementationRef}.
     *
     * @param implementationRef the ref this factory handles
     * @param factory           function creating the agent from config
     */
    public void registerFactory(String implementationRef,
                                 Function<AgentConfig, TypedAgent<?, ?>> factory) {
        factories.put(implementationRef, factory);
        log.debug("Registered Data Cloud agent factory for ref '{}'", implementationRef);
    }

    private TypedAgent<?, ?> createStubAgent(AgentConfig config) {
        throw new UnsupportedOperationException(
                "Stub factory for '" + config.getAgentId()
                        + "'. Wire real agent factories via registerFactory().");
    }
}
