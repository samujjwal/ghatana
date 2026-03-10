package com.ghatana.yappc.plugin;

import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing YAPPC plugins with async lifecycle support.
 *
 * @doc.type class
 * @doc.purpose Manages plugin registration, lookup, and lifecycle
 * @doc.layer core
 * @doc.pattern Registry
 */
public class PluginRegistry {

    private final PluginContext context;
    private final Map<String, YAPPCPlugin> pluginsById = new ConcurrentHashMap<>();

    private PluginRegistry(PluginContext context) {
        this.context = context;
    }

    /**
     * Creates a new PluginRegistry with the given context.
     *
     * @param context the plugin context
     * @return a new PluginRegistry instance
     */
    public static PluginRegistry create(PluginContext context) {
        return new PluginRegistry(context);
    }

    /**
     * Initializes the registry.
     *
     * @return Promise that resolves when initialization is complete
     */
    public Promise<Void> initialize() {
        return Promise.complete();
    }

    /**
     * Shuts down all registered plugins and clears the registry.
     *
     * @return Promise that resolves when shutdown is complete
     */
    public Promise<Void> shutdown() {
        List<Promise<Void>> shutdowns = pluginsById.values().stream()
                .map(YAPPCPlugin::shutdown)
                .collect(Collectors.toList());
        pluginsById.clear();
        return Promise.complete();
    }

    /**
     * Registers a plugin.
     *
     * @param plugin the plugin to register
     * @return Promise that resolves when registration is complete
     */
    public Promise<Void> registerPlugin(YAPPCPlugin plugin) {
        String id = plugin.getMetadata().getId();
        pluginsById.put(id, plugin);
        return plugin.initialize(context);
    }

    /**
     * Returns whether a plugin with the given ID is registered.
     *
     * @param pluginId the plugin ID
     * @return true if registered
     */
    public boolean isRegistered(String pluginId) {
        return pluginsById.containsKey(pluginId);
    }

    /**
     * Returns the number of registered plugins.
     *
     * @return plugin count
     */
    public int getPluginCount() {
        return pluginsById.size();
    }

    /**
     * Gets a plugin by ID.
     *
     * @param pluginId the plugin ID
     * @return Optional containing the plugin if found
     */
    public Optional<YAPPCPlugin> getPlugin(String pluginId) {
        return Optional.ofNullable(pluginsById.get(pluginId));
    }

    /**
     * Returns all registered ValidatorPlugins.
     *
     * @return list of validator plugins
     */
    public List<ValidatorPlugin> getValidators() {
        return pluginsById.values().stream()
                .filter(p -> p instanceof ValidatorPlugin)
                .map(p -> (ValidatorPlugin) p)
                .collect(Collectors.toList());
    }

    /**
     * Returns all registered GeneratorPlugins.
     *
     * @return list of generator plugins
     */
    public List<GeneratorPlugin> getGenerators() {
        return pluginsById.values().stream()
                .filter(p -> p instanceof GeneratorPlugin)
                .map(p -> (GeneratorPlugin) p)
                .collect(Collectors.toList());
    }

    /**
     * Returns ValidatorPlugins filtered by category.
     *
     * @param category the category to filter by
     * @return list of matching validator plugins
     */
    public List<ValidatorPlugin> getValidatorsByCategory(String category) {
        return getValidators().stream()
                .filter(v -> category.equals(v.getValidatorCategory()))
                .collect(Collectors.toList());
    }

    /**
     * Returns GeneratorPlugins filtered by supported language.
     *
     * @param language the language to filter by
     * @return list of matching generator plugins
     */
    public List<GeneratorPlugin> getGeneratorsByLanguage(String language) {
        return getGenerators().stream()
                .filter(g -> g.getSupportedLanguages().contains(language))
                .collect(Collectors.toList());
    }

    /**
     * Returns AgentPlugins filtered by SDLC phase.
     *
     * @param phase the SDLC phase
     * @return list of matching agent plugins
     */
    public List<AgentPlugin> getAgentsByPhase(String phase) {
        return pluginsById.values().stream()
                .filter(p -> p instanceof AgentPlugin)
                .map(p -> (AgentPlugin) p)
                .filter(a -> phase.equals(a.getSdlcPhase()))
                .collect(Collectors.toList());
    }

    /**
     * Gets an AgentPlugin by phase and step name.
     *
     * @param phase    the SDLC phase
     * @param stepName the step name
     * @return Optional containing the agent plugin if found
     */
    public Optional<AgentPlugin> getAgent(String phase, String stepName) {
        return getAgentsByPhase(phase).stream()
                .filter(a -> stepName.equals(a.getStepName()))
                .findFirst();
    }

    /**
     * Checks the health of all registered plugins.
     *
     * @return Promise resolving to a map of plugin ID to health status
     */
    public Promise<Map<String, HealthStatus>> checkHealth() {
        Map<String, HealthStatus> result = new LinkedHashMap<>();
        List<Promise<Void>> checks = pluginsById.entrySet().stream()
                .map(entry -> entry.getValue().checkHealth()
                        .map(health -> {
                            result.put(entry.getKey(), health);
                            return (Void) null;
                        }))
                .collect(Collectors.toList());
        // Run health checks sequentially using promise chaining
        Promise<Void> chain = Promise.complete();
        for (Promise<Void> check : checks) {
            chain = chain.then(v -> check);
        }
        return chain.map(v -> result);
    }
}
