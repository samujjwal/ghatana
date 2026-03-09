package com.ghatana.virtualorg.framework.agent;

import com.ghatana.virtualorg.framework.config.VirtualOrgAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Registry for managing AgentFactories and creating agents.
 *
 * <p><b>Purpose</b><br>
 * The AgentRegistry is the central hub for agent creation. It manages
 * registered factories and delegates agent creation based on templates.
 * Supports SPI-based auto-discovery of factories.
 *
 * <p><b>Key Features</b><br>
 * - Manual factory registration
 * - SPI-based auto-discovery via ServiceLoader
 * - Priority-based factory selection
 * - Fallback to generic agent if no factory found
 * - Agent caching (optional)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AgentRegistry registry = new AgentRegistry();
 *
 * // Manual registration
 * registry.register(new SoftwareAgentFactory());
 *
 * // Or auto-discover via SPI
 * registry.discoverFactories();
 *
 * // Create agent from template
 * Agent agent = registry.create("CodeReviewer", agentConfig);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Registry for managing AgentFactories and creating agents
 * @doc.layer platform
 * @doc.pattern Registry, SPI
 */
public class AgentRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AgentRegistry.class);

    private final List<AgentFactory> factories = new CopyOnWriteArrayList<>();
    private final Map<String, Agent> agentCache = new ConcurrentHashMap<>();
    private boolean cachingEnabled = false;

    /**
     * Creates an empty registry.
     */
    public AgentRegistry() {
    }

    /**
     * Creates a registry with auto-discovered factories.
     */
    public static AgentRegistry withDiscovery() {
        AgentRegistry registry = new AgentRegistry();
        registry.discoverFactories();
        return registry;
    }

    /**
     * Discovers and registers factories via ServiceLoader (SPI).
     *
     * @return number of factories discovered
     */
    public int discoverFactories() {
        ServiceLoader<AgentFactory> loader = ServiceLoader.load(AgentFactory.class);
        int count = 0;

        for (AgentFactory factory : loader) {
            register(factory);
            count++;
            LOG.info("Discovered AgentFactory: {} (domain: {}, templates: {})",
                    factory.getClass().getSimpleName(),
                    factory.getDomain(),
                    factory.getSupportedTemplates());
        }

        LOG.info("Discovered {} agent factories via SPI", count);
        return count;
    }

    /**
     * Registers a new AgentFactory.
     *
     * @param factory The factory to register.
     */
    public void register(AgentFactory factory) {
        factories.add(factory);
        // Re-sort by priority (descending)
        factories.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        LOG.debug("Registered factory: {} with priority {}",
                factory.getClass().getSimpleName(), factory.getPriority());
    }

    /**
     * Unregisters a factory.
     *
     * @param factory the factory to remove
     * @return true if removed
     */
    public boolean unregister(AgentFactory factory) {
        return factories.remove(factory);
    }

    /**
     * Creates an agent using the registered factories.
     *
     * @param template The template name.
     * @param config   The agent configuration.
     * @return The created Agent.
     * @throws IllegalArgumentException if no factory can create the agent.
     */
    public Agent create(String template, VirtualOrgAgentConfig config) {
        String cacheKey = template + ":" + config.getName();

        if (cachingEnabled) {
            Agent cached = agentCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        for (AgentFactory factory : factories) {
            if (factory.supports(template)) {
                Optional<Agent> agent = factory.createAgent(template, config);
                if (agent.isPresent()) {
                    LOG.debug("Created agent '{}' from template '{}' using factory '{}'",
                            config.getName(), template, factory.getClass().getSimpleName());

                    if (cachingEnabled) {
                        agentCache.put(cacheKey, agent.get());
                    }
                    return agent.get();
                }
            }
        }

        throw new IllegalArgumentException("No registered factory found for agent template: " + template);
    }

    /**
     * Creates an agent, falling back to generic if no template found.
     *
     * @param template The template name (may be null).
     * @param config   The agent configuration.
     * @return The created Agent.
     */
    public Agent createOrDefault(String template, VirtualOrgAgentConfig config) {
        if (template == null || template.isBlank()) {
            return createGenericAgent(config);
        }

        try {
            return create(template, config);
        } catch (IllegalArgumentException e) {
            LOG.warn("No factory for template '{}', creating generic agent", template);
            return createGenericAgent(config);
        }
    }

    /**
     * Gets all supported templates across all factories.
     */
    public Set<String> getSupportedTemplates() {
        return factories.stream()
                .flatMap(f -> f.getSupportedTemplates().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Gets factories by domain.
     */
    public List<AgentFactory> getFactoriesByDomain(String domain) {
        return factories.stream()
                .filter(f -> f.getDomain().equals(domain))
                .collect(Collectors.toList());
    }

    /**
     * Gets the number of registered factories.
     */
    public int getFactoryCount() {
        return factories.size();
    }

    /**
     * Enables or disables agent caching.
     */
    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
        if (!enabled) {
            agentCache.clear();
        }
    }

    /**
     * Clears the agent cache.
     */
    public void clearCache() {
        agentCache.clear();
    }

    /**
     * Creates a generic agent from config.
     */
    private Agent createGenericAgent(VirtualOrgAgentConfig config) {
        Agent.Builder builder = Agent.builder()
                .id(config.getName())
                .name(config.getDisplayName() != null ? config.getDisplayName() : config.getName());

        if (!config.getPrimaryCapabilities().isEmpty()) {
            builder.capabilities(config.getPrimaryCapabilities().toArray(String[]::new));
        }

        return builder.build();
    }
}
