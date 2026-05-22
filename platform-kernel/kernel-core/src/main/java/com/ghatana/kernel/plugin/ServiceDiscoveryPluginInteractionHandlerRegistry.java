package com.ghatana.kernel.plugin;

import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.activej.service.ServiceGraph;

import java.util.*;

/**
 * Service discovery-based implementation of PluginInteractionHandlerRegistry.
 *
 * <p>This implementation discovers PluginInteractionHandler instances from the Kernel
 * ServiceRegistry, similar to how ServiceDiscoveryProductInteractionHandlerRegistry works
 * for product interactions. It uses a naming convention for service IDs to automatically
 * register handlers.</p>
 *
 * <p>Service ID pattern: {@code plugin-interaction-handler.{contractId}}</p>
 *
 * @doc.type class
 * @doc.purpose Service discovery-based registry for plugin interaction handlers
 * @doc.layer kernel
 * @doc.pattern ServiceDiscovery
 */
public final class ServiceDiscoveryPluginInteractionHandlerRegistry implements PluginInteractionHandlerRegistry {

    private final ServiceGraph serviceGraph;
    private final Map<String, PluginInteractionHandler<?, ?>> handlers;
    private final boolean allowManualRegistration;

    private ServiceDiscoveryPluginInteractionHandlerRegistry(
            ServiceGraph serviceGraph,
            Map<String, PluginInteractionHandler<?, ?>> handlers,
            boolean allowManualRegistration) {
        this.serviceGraph = Objects.requireNonNull(serviceGraph, "serviceGraph must not be null");
        this.handlers = new HashMap<>(Objects.requireNonNull(handlers, "handlers must not be null"));
        this.allowManualRegistration = allowManualRegistration;
    }

    /**
     * Creates a registry with service discovery and manual registration disabled.
     *
     * @param serviceGraph the service graph for handler discovery
     * @return the registry instance
     */
    public static ServiceDiscoveryPluginInteractionHandlerRegistry create(ServiceGraph serviceGraph) {
        return builder(serviceGraph).build();
    }

    /**
     * Creates a builder for the registry.
     *
     * @param serviceGraph the service graph for handler discovery
     * @return the builder
     */
    public static Builder builder(ServiceGraph serviceGraph) {
        return new Builder(serviceGraph);
    }

    @Override
    public Map<String, PluginInteractionHandler<?, ?>> allHandlers() {
        return Map.copyOf(handlers);
    }

    @Override
    public Set<String> registeredContractIds() {
        return handlers.keySet();
    }

    @Override
    public boolean hasHandler(String contractId) {
        Objects.requireNonNull(contractId, "contractId must not be null");
        return handlers.containsKey(contractId);
    }

    @Override
    public PluginInteractionHandler<?, ?> getHandler(String contractId) {
        Objects.requireNonNull(contractId, "contractId must not be null");
        return handlers.get(contractId);
    }

    /**
     * Manually registers a handler if manual registration is allowed.
     *
     * @param contractId the contract ID
     * @param handler the handler to register
     * @throws IllegalStateException if manual registration is not allowed
     */
    public void registerHandler(String contractId, PluginInteractionHandler<?, ?> handler) {
        if (!allowManualRegistration) {
            throw new IllegalStateException("Manual registration is not allowed");
        }
        Objects.requireNonNull(contractId, "contractId must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        handlers.put(contractId, handler);
    }

    /**
     * Builder for ServiceDiscoveryPluginInteractionHandlerRegistry.
     */
    public static final class Builder {
        private final ServiceGraph serviceGraph;
        private final Map<String, PluginInteractionHandler<?, ?>> handlers = new HashMap<>();
        private boolean allowManualRegistration = false;

        private Builder(ServiceGraph serviceGraph) {
            this.serviceGraph = Objects.requireNonNull(serviceGraph, "serviceGraph must not be null");
        }

        /**
         * Enables manual handler registration.
         *
         * @return this builder
         */
        public Builder allowManualRegistration() {
            this.allowManualRegistration = true;
            return this;
        }

        /**
         * Adds a manual handler registration.
         *
         * @param contractId the contract ID
         * @param handler the handler to register
         * @return this builder
         */
        public Builder withHandler(String contractId, PluginInteractionHandler<?, ?> handler) {
            this.allowManualRegistration = true;
            handlers.put(contractId, handler);
            return this;
        }

        /**
         * Builds the registry instance.
         *
         * @return the registry
         */
        public ServiceDiscoveryPluginInteractionHandlerRegistry build() {
            // Discover handlers from service graph
            discoverHandlers();
            return new ServiceDiscoveryPluginInteractionHandlerRegistry(
                serviceGraph,
                handlers,
                allowManualRegistration
            );
        }

        private void discoverHandlers() {
            // ActiveJ ServiceGraph no longer exposes service instances directly.
            // Keep existing manual registrations and defer automatic discovery until
            // an explicit service-index API is introduced.
        }
    }
}
