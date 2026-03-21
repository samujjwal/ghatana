/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ai;

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
 * Finance agent logic provider — resolves {@code finance:} prefixed
 * {@code implementationRef}s to concrete financial domain agents.
 *
 * <p>Maps implementation refs to agent factories:
 * <pre>
 *   finance:risk-assessment  → RiskAssessmentAgent
 *   finance:fraud-detection  → FraudDetectionAgent
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Finance-specific AgentLogicProvider implementation
 * @doc.layer product
 * @doc.pattern Service, Factory
 */
public class FinanceAgentLogicProvider implements AgentLogicProvider {

    private static final Logger log = LoggerFactory.getLogger(FinanceAgentLogicProvider.class);

    public static final String PROVIDER_ID = "finance";

    private final Map<String, Function<AgentConfig, TypedAgent<?, ?>>> factories =
            new ConcurrentHashMap<>();

    public FinanceAgentLogicProvider() {
        factories.put("finance:risk-assessment", FinanceAgentLogicProvider::stubFactory);
        factories.put("finance:fraud-detection", FinanceAgentLogicProvider::stubFactory);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getProviderName() {
        return "Finance Agent Provider";
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
        log.info("Creating Finance agent for ref '{}', agentId='{}'",
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
        log.debug("Registered Finance agent factory for ref '{}'", implementationRef);
    }

    private static TypedAgent<?, ?> stubFactory(AgentConfig config) {
        throw new UnsupportedOperationException(
                "Stub factory for '" + config.getAgentId()
                        + "'. Wire real factories via registerFactory().");
    }
}
