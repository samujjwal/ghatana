/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.softwareorg.domain.agent;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.spi.AgentLogicProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Software-Org agent logic provider — resolves {@code software-org:} prefixed
 * {@code implementationRef}s to concrete persona-based agents.
 *
 * <p>Bridges the existing {@link SoftwareAgentFactory} with the platform
 * {@link AgentLogicProvider} SPI. Products register concrete factories via
 * {@link #registerFactory(String, Function)} at bootstrap time.
 *
 * @doc.type class
 * @doc.purpose Software-Org AgentLogicProvider implementation
 * @doc.layer product
 * @doc.pattern Service, Factory, Adapter
 */
public class SoftwareOrgAgentLogicProvider implements AgentLogicProvider {

    private static final Logger log = LoggerFactory.getLogger(SoftwareOrgAgentLogicProvider.class);

    public static final String PROVIDER_ID = "software-org";

    private final Map<String, Function<AgentConfig, TypedAgent<?, ?>>> factories =
            new ConcurrentHashMap<>();

    public SoftwareOrgAgentLogicProvider() {
        // Default refs matching the 8 SoftwareAgentFactory templates
        factories.put("software-org:CodeReviewer", SoftwareOrgAgentLogicProvider::stubFactory);
        factories.put("software-org:ReleaseManager", SoftwareOrgAgentLogicProvider::stubFactory);
        factories.put("software-org:TechLead", SoftwareOrgAgentLogicProvider::stubFactory);
        factories.put("software-org:Architect", SoftwareOrgAgentLogicProvider::stubFactory);
        factories.put("software-org:QAEngineer", SoftwareOrgAgentLogicProvider::stubFactory);
        factories.put("software-org:DevOpsEngineer", SoftwareOrgAgentLogicProvider::stubFactory);
        factories.put("software-org:FrontendDeveloper", SoftwareOrgAgentLogicProvider::stubFactory);
        factories.put("software-org:BackendDeveloper", SoftwareOrgAgentLogicProvider::stubFactory);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getProviderName() {
        return "Software-Org Agent Provider";
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
        log.info("Creating Software-Org agent for ref '{}', agentId='{}'",
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
        log.debug("Registered Software-Org agent factory for ref '{}'", implementationRef);
    }

    private static TypedAgent<?, ?> stubFactory(AgentConfig config) {
        throw new UnsupportedOperationException(
                "Stub factory for '" + config.getAgentId()
                        + "'. Wire real factories via registerFactory().");
    }
}
