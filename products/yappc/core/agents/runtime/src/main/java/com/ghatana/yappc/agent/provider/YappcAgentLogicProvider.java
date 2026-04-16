/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.agent.provider;

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
 * YAPPC agent logic provider — resolves {@code implementationRef}s
 * prefixed with {@code yappc-java:} to concrete YAPPC specialist agents.
 *
 * <p>This provider bridges the platform's {@link AgentLogicProvider} SPI
 * with YAPPC's in-memory agent creation. Products register agent factories
 * via {@link #registerFactory(String, Function)} at bootstrap time.
 *
 * <p><b>Supported refs</b></p>
 * <pre>
 *   yappc-java:agent.yappc.java-expert
 *   yappc-java:agent.yappc.code-reviewer
 *   yappc-java:agent.yappc.test-strategist
 * </pre>
 *
 * @doc.type class
 * @doc.purpose YAPPC-specific AgentLogicProvider implementation
 * @doc.layer product
 * @doc.pattern Service, Factory
 */
public class YappcAgentLogicProvider implements AgentLogicProvider {

    private static final Logger log = LoggerFactory.getLogger(YappcAgentLogicProvider.class);

    public static final String PROVIDER_ID = "yappc-java";

    private final Map<String, Function<AgentConfig, TypedAgent<?, ?>>> factories =
            new ConcurrentHashMap<>();

    public YappcAgentLogicProvider() {
        // Default refs — stub factories; must be replaced via registerFactory()
        factories.put("yappc-java:agent.yappc.java-expert", YappcAgentLogicProvider::stubFactory);
        factories.put("yappc-java:agent.yappc.code-reviewer", YappcAgentLogicProvider::stubFactory);
        factories.put("yappc-java:agent.yappc.test-strategist", YappcAgentLogicProvider::stubFactory);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getProviderName() {
        return "YAPPC Java Agent Provider";
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
        log.info("Creating YAPPC agent for ref '{}', agentId='{}'",
                implementationRef, config.getAgentId());
        return factory.apply(config);
    }

    /**
     * Registers a factory for a specific {@code implementationRef}.
     * Products call this at bootstrap to wire concrete agent creation.
     *
     * @param implementationRef the ref this factory handles
     * @param factory           function creating the agent from config
     */
    public void registerFactory(String implementationRef,
                                 Function<AgentConfig, TypedAgent<?, ?>> factory) {
        factories.put(implementationRef, factory);
        log.debug("Registered YAPPC agent factory for ref '{}'", implementationRef);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stub factory — replaced with real specialist factories at runtime
    // ═══════════════════════════════════════════════════════════════════════════

    private static TypedAgent<?, ?> stubFactory(AgentConfig config) {
        // Note: This stub factory is used when no specialist factory is registered.
        // Real specialist factories should be wired via registerFactory() during service initialization.
        throw new IllegalStateException(
                "No specialist factory registered for agent '" + config.getAgentId()
                        + "'. Register a factory via registerFactory() during service initialization.");
    }
}
