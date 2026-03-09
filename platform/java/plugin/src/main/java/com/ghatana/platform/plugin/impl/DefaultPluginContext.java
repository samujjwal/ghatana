package com.ghatana.platform.plugin.impl;

import com.ghatana.platform.plugin.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Default implementation of PluginContext.
 *
 * @doc.type class
 * @doc.purpose Default context implementation
 * @doc.layer core
 */
public class DefaultPluginContext implements PluginContext {

    private final PluginRegistry registry;
    private final Map<Class<?>, Object> configuration;
    private final PluginInteractionBus bus;

    public DefaultPluginContext(PluginRegistry registry, Map<Class<?>, Object> configuration) {
        this.registry = registry;
        this.configuration = new ConcurrentHashMap<>(configuration);
        this.bus = new DefaultPluginInteractionBus(registry);
    }

    @Override
    public <T> @Nullable T getConfig(@NotNull Class<T> configType) {
        return configType.cast(configuration.get(configType));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Plugin> @NotNull Optional<T> findPlugin(@NotNull String pluginId) {
        return registry.getPlugin(pluginId).map(p -> (T) p);
    }

    @Override
    public @NotNull List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability) {
        return registry.getAllPlugins().stream()
                .filter(p -> p.getCapability(capability).isPresent())
                .collect(Collectors.toList());
    }

    @Override
    public @NotNull PluginInteractionBus getInteractionBus() {
        return bus;
    }

    /**
     * Simple in-memory bus implementation.
     */
    private static class DefaultPluginInteractionBus implements PluginInteractionBus {
        private final PluginRegistry registry;
        // Simple topic subscription map
        private final Map<String, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();

        public DefaultPluginInteractionBus(PluginRegistry registry) {
            this.registry = registry;
        }

        @Override
        public <Req, Res> @NotNull Promise<Res> request(@NotNull String targetPluginId, @NotNull Req request, @NotNull Class<Res> responseType, @NotNull Duration timeout) {
            // This requires plugins to expose a request handler mechanism.
            // The current Plugin interface doesn't have a generic 'handleRequest'.
            // This would need to be an extension or part of the contract.
            // For now, we return error as it's not fully specified in the core interface yet.
            return Promise.ofException(new UnsupportedOperationException("Direct plugin request not yet implemented"));
        }

        @Override
        public void publish(@NotNull String topic, @NotNull Object event) {
            List<Consumer<Object>> listeners = subscribers.get(topic);
            if (listeners != null) {
                listeners.forEach(l -> l.accept(event));
            }
        }

        @Override
        public void subscribe(@NotNull String topic, @NotNull Consumer<Object> listener) {
            subscribers.computeIfAbsent(topic, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(listener);
        }
    }
}
