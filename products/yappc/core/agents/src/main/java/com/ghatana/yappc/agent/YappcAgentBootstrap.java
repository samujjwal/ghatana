package com.ghatana.yappc.agent;

import com.ghatana.agent.framework.planner.PlannerAgentFactory;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstrap orchestrator for YAPPC agents using the AEP PlannerAgentFactory.
 *
 * <p>Loads agent definitions from YAML files, creates agents via {@link PlannerAgentFactory},
 * and registers them in an internal registry for lookup during execution.
 *
 * @doc.type class
 * @doc.purpose Bootstrap YAPPC agents from YAML definitions using AEP runtime
 * @doc.layer product
 * @doc.pattern Factory, Bootstrap
 */
public final class YappcAgentBootstrap {

    private static final Logger log = LoggerFactory.getLogger(YappcAgentBootstrap.class);
    private static final String DEFAULT_CONFIG_BASE_PATH = "config/agents";

    private final Eventloop eventloop;
    private final String configBasePath;
    private final PlannerAgentFactory factory;
    private final Map<String, Object> agentRegistry = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private YappcAgentBootstrap(
            @NotNull Eventloop eventloop,
            @NotNull String configBasePath) {
        this.eventloop = eventloop;
        this.configBasePath = configBasePath;
        this.factory = new PlannerAgentFactory();
    }

    /**
     * Creates a bootstrap instance using the default config base path.
     *
     * @param eventloop the ActiveJ eventloop for async operations
     * @return new bootstrap instance
     * @throws IllegalArgumentException if eventloop is null
     */
    @NotNull
    public static YappcAgentBootstrap create(@Nullable Eventloop eventloop) {
        if (eventloop == null) {
            throw new IllegalArgumentException("Eventloop cannot be null");
        }
        return new YappcAgentBootstrap(eventloop, DEFAULT_CONFIG_BASE_PATH);
    }

    /**
     * Creates a bootstrap instance with a custom config base path.
     *
     * @param eventloop      the ActiveJ eventloop for async operations
     * @param configBasePath base path from which to load agent YAML definitions
     * @return new bootstrap instance
     * @throws IllegalArgumentException if eventloop or configBasePath is null/empty
     */
    @NotNull
    public static YappcAgentBootstrap create(
            @Nullable Eventloop eventloop,
            @Nullable String configBasePath) {
        if (eventloop == null) {
            throw new IllegalArgumentException("Eventloop cannot be null");
        }
        if (configBasePath == null || configBasePath.isBlank()) {
            throw new IllegalArgumentException("Config base path cannot be null or empty");
        }
        return new YappcAgentBootstrap(eventloop, configBasePath);
    }

    /**
     * Returns the underlying {@link PlannerAgentFactory}.
     *
     * @return the factory instance
     */
    @NotNull
    public PlannerAgentFactory getFactory() {
        return factory;
    }

    /**
     * Returns the agent registry map (agent ID → agent).
     *
     * @return agent registry
     */
    @NotNull
    public Map<String, Object> getRegistry() {
        return agentRegistry;
    }

    /**
     * Initializes the bootstrap by scanning the config path for YAML definitions
     * and creating agents via the factory.
     *
     * @return Promise that completes on success, or fails if no agents could be created
     */
    @NotNull
    public Promise<Void> initialize() {
        Path definitionsDir = Path.of(configBasePath, "definitions");

        if (!Files.exists(definitionsDir)) {
            return Promise.ofException(
                new IOException("No agents loaded successfully: definitions directory not found: "
                    + definitionsDir));
        }

        List<String> loadedAgents = new ArrayList<>();

        try (var stream = Files.list(definitionsDir)) {
            stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                .forEach(yamlPath -> {
                    Object agent = factory.createAgent(yamlPath.toString());
                    if (agent != null) {
                        String agentId = yamlPath.getFileName().toString()
                            .replace(".yaml", "").replace(".yml", "");
                        agentRegistry.put(agentId, agent);
                        loadedAgents.add(agentId);
                        log.info("Loaded agent: {}", agentId);
                    } else {
                        log.warn("Failed to create agent from: {}", yamlPath);
                    }
                });
        } catch (IOException e) {
            return Promise.ofException(
                new IOException("No agents loaded successfully: " + e.getMessage(), e));
        }

        if (loadedAgents.isEmpty()) {
            return Promise.ofException(
                new IOException("No agents loaded successfully from " + definitionsDir));
        }

        initialized.set(true);
        log.info("Bootstrap initialized with {} agents", loadedAgents.size());
        return Promise.complete();
    }

    /**
     * Gets an agent by ID.
     *
     * @param agentId the agent identifier
     * @return the agent object
     * @throws IllegalStateException if bootstrap has not been initialized
     */
    @NotNull
    public Object getAgent(@NotNull String agentId) {
        if (!initialized.get()) {
            throw new IllegalStateException(
                "Bootstrap not initialized. Call initialize() first.");
        }
        Object agent = agentRegistry.get(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("No agent found with ID: " + agentId);
        }
        return agent;
    }
}
