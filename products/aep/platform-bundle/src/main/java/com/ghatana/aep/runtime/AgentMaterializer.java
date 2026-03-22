/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.runtime;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.config.AgentConfigMaterializer;
import com.ghatana.agent.spi.AgentLogicProvider;
import com.ghatana.agent.spi.AgentLogicProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * AEP agent materializer — the central factory that converts YAML agent
 * definitions into live {@link TypedAgent} instances by resolving
 * {@code implementationRef} via {@link AgentLogicProvider} SPI.
 *
 * <h2>Materialization Pipeline</h2>
 * <pre>
 *   YAML definition
 *     → AgentConfigMaterializer (YAML → AgentConfig with implementationRef)
 *     → AgentLogicProviderRegistry.resolve(implementationRef)
 *     → AgentLogicProvider.createAgent(ref, config)
 *     → TypedAgent instance
 * </pre>
 *
 * <h2>Provider Resolution</h2>
 * The {@code implementationRef} format is
 * {@code <provider-id>:<qualified-agent-id>}. The registry first tries
 * a fast O(1) prefix lookup, then falls back to querying all providers.
 *
 * @doc.type class
 * @doc.purpose YAML-to-agent materialization via provider SPI
 * @doc.layer product
 * @doc.pattern Factory
 */
public class AgentMaterializer {

    private static final Logger log = LoggerFactory.getLogger(AgentMaterializer.class);

    private final AgentConfigMaterializer configMaterializer;
    private final AgentLogicProviderRegistry providerRegistry;

    /**
     * Creates a materializer with the given config materializer and
     * provider registry.
     *
     * @param configMaterializer YAML → AgentConfig transformer
     * @param providerRegistry   registry of logic providers
     */
    public AgentMaterializer(
            AgentConfigMaterializer configMaterializer,
            AgentLogicProviderRegistry providerRegistry) {
        this.configMaterializer = Objects.requireNonNull(configMaterializer);
        this.providerRegistry = Objects.requireNonNull(providerRegistry);
    }

    /**
     * Materializes an agent from a YAML definition file.
     *
     * <p>Parses the YAML into an {@link AgentConfig}, extracts the
     * {@code implementationRef}, resolves the provider, and creates
     * the agent.
     *
     * @param agentDefinitionYaml path to the YAML file
     * @return a fully initialized agent
     * @throws IOException              if the YAML file cannot be read
     * @throws IllegalArgumentException if no provider supports the ref
     * @throws IllegalStateException    if implementationRef is missing
     */
    public TypedAgent<?, ?> materialize(Path agentDefinitionYaml) throws IOException {
        com.ghatana.agent.AgentConfig frameworkConfig =
                configMaterializer.materialize(agentDefinitionYaml);
        com.ghatana.agent.AgentConfig apiConfig = adaptConfig(frameworkConfig);
        String ref = resolveRef(apiConfig);

        log.info("Materializing agent '{}' via ref '{}' from {}",
                apiConfig.getAgentId(), ref, agentDefinitionYaml);

        return providerRegistry.createAgent(ref, apiConfig);
    }

    /**
     * Materializes an agent from an already-parsed API {@link com.ghatana.agent.AgentConfig}.
     *
     * @param config agent configuration (must contain implementationRef)
     * @return a fully initialized agent
     * @throws IllegalArgumentException if no provider supports the ref
     * @throws IllegalStateException    if implementationRef is missing
     */
    public TypedAgent<?, ?> materialize(com.ghatana.agent.AgentConfig config) {
        String ref = resolveRef(config);

        log.info("Materializing agent '{}' via ref '{}'",
                config.getAgentId(), ref);

        return providerRegistry.createAgent(ref, config);
    }

    /**
     * Materializes an agent from an explicit ref and config (bypasses
     * the config's own implementationRef field).
     *
     * @param implementationRef explicit ref
     * @param config            agent configuration
     * @return a fully initialized agent
     * @throws IllegalArgumentException if no provider supports the ref
     */
    public TypedAgent<?, ?> materialize(String implementationRef,
                                         com.ghatana.agent.AgentConfig config) {
        Objects.requireNonNull(implementationRef, "implementationRef must not be null");
        log.info("Materializing agent '{}' via explicit ref '{}'",
                config.getAgentId(), implementationRef);
        return providerRegistry.createAgent(implementationRef, config);
    }

    /**
     * Checks whether the given ref can be resolved by any registered
     * provider.
     *
     * @param implementationRef the ref to check
     * @return true if a provider exists for this ref
     */
    public boolean canMaterialize(String implementationRef) {
        return providerRegistry.resolve(implementationRef).isPresent();
    }

    /**
     * Returns the underlying provider registry.
     */
    public AgentLogicProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════════════════════════════════════════

    private String resolveRef(com.ghatana.agent.AgentConfig config) {
        String ref = config.getImplementationRef();

        // Fallback: check properties map (for YAML parsed via @JsonAnySetter)
        if (ref == null || ref.isBlank()) {
            Object propRef = config.getProperties().get("implementationRef");
            if (propRef instanceof String s && !s.isBlank()) {
                ref = s;
            }
        }

        if (ref == null || ref.isBlank()) {
            throw new IllegalStateException(
                    "Agent '" + config.getAgentId()
                            + "' has no implementationRef. Cannot resolve provider.");
        }
        return ref;
    }

    /**
     * Adapts the framework's AgentConfig to the API module's AgentConfig.
     */
    private com.ghatana.agent.AgentConfig adaptConfig(
            com.ghatana.agent.AgentConfig fw) {
        return com.ghatana.agent.AgentConfig.builder()
                .agentId(fw.getAgentId())
                .type(AgentType.valueOf(fw.getType().name()))
                .version(fw.getVersion())
                .implementationRef(fw.getImplementationRef())
                .timeout(fw.getTimeout())
                .confidenceThreshold(fw.getConfidenceThreshold())
                .maxRetries(fw.getMaxRetries())
                .retryBackoff(fw.getRetryBackoff())
                .maxRetryBackoff(fw.getMaxRetryBackoff())
                .failureMode(FailureMode.valueOf(fw.getFailureMode().name()))
                .circuitBreakerThreshold(fw.getCircuitBreakerThreshold())
                .circuitBreakerReset(fw.getCircuitBreakerReset())
                .metricsEnabled(fw.isMetricsEnabled())
                .tracingEnabled(fw.isTracingEnabled())
                .tracingSampleRate(fw.getTracingSampleRate())
                .properties(fw.getProperties())
                .labels(fw.getLabels())
                .requiredCapabilities(fw.getRequiredCapabilities())
                .build();
    }
}
