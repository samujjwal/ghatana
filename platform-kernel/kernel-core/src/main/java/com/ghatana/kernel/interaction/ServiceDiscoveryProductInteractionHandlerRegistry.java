package com.ghatana.kernel.interaction;

import com.ghatana.kernel.registry.ServiceRegistry;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service-discovery-backed implementation of ProductInteractionHandlerRegistry.
 *
 * <p>This registry automatically discovers ProductInteractionHandler instances from the
 * Kernel ServiceRegistry, enabling automatic handler registration without manual builder
 * configuration. It supports both service-discovered handlers and manually registered handlers
 * for testing and development scenarios.</p>
 *
 * <p>Handlers are discovered by looking up services registered with the service ID pattern
 * "product-interaction-handler.{contractId}" in the ServiceRegistry. This allows products
 * to register their handlers with the Kernel and have them automatically available for
 * product-to-product interactions.</p>
 *
 * @doc.type class
 * @doc.purpose Service-discovery-backed handler registry for automatic handler discovery
 * @doc.layer kernel
 * @doc.pattern Registry
 */
public final class ServiceDiscoveryProductInteractionHandlerRegistry implements ProductInteractionHandlerRegistry {

    private final Map<String, ProductInteractionHandler<?, ?>> handlers;
    private final ServiceRegistry serviceRegistry;

    private ServiceDiscoveryProductInteractionHandlerRegistry(Builder builder) {
        this.serviceRegistry = Objects.requireNonNull(builder.serviceRegistry, "serviceRegistry must not be null");
        this.handlers = Collections.unmodifiableMap(new ConcurrentHashMap<>(builder.handlers));
        
        // Discover handlers from service registry
        discoverHandlers();
    }

    @Override
    public Map<String, ProductInteractionHandler<?, ?>> allHandlers() {
        return handlers;
    }

    @Override
    public Set<String> registeredContractIds() {
        return handlers.keySet();
    }

    @Override
    public boolean hasHandler(String contractId) {
        return handlers.containsKey(contractId);
    }

    @Override
    public ProductInteractionHandler<?, ?> getHandler(String contractId) {
        return handlers.get(contractId);
    }

    /**
     * Returns the number of registered handlers.
     *
     * @return handler count
     */
    public int size() {
        return handlers.size();
    }

    /**
     * Checks if the registry is empty.
     *
     * @return true if no handlers are registered
     */
    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    /**
     * Discovers and registers ProductInteractionHandler instances from the ServiceRegistry.
     *
     * <p>This method scans the ServiceRegistry for services registered with the service ID
     * pattern "product-interaction-handler.{contractId}" and registers them as handlers.
     * Handlers are type-checked to ensure they implement ProductInteractionHandler.</p>
     */
    private void discoverHandlers() {
        if (serviceRegistry == null) {
            return;
        }

        Set<String> serviceIds = serviceRegistry.getServiceIds();
        for (String serviceId : serviceIds) {
            if (serviceId.startsWith("product-interaction-handler.")) {
                String contractId = serviceId.substring("product-interaction-handler.".length());
                
                serviceRegistry.getService(serviceId, ProductInteractionHandler.class)
                    .ifPresent(handler -> {
                        // Validate contract ID matches
                        if (!handler.contractId().equals(contractId)) {
                            throw new IllegalStateException(
                                String.format("Handler contract ID mismatch: service ID '%s' indicates contract '%s' " +
                                    "but handler reports contract '%s'", 
                                    serviceId, contractId, handler.contractId()));
                        }
                        
                        // Register the discovered handler
                        @SuppressWarnings("unchecked")
                        ProductInteractionHandler<?, ?> typedHandler = 
                            (ProductInteractionHandler<?, ?>) handler;
                        
                        if (handlers.containsKey(contractId)) {
                            throw new IllegalStateException(
                                String.format("Handler already registered for contractId %s", contractId));
                        }
                        
                        handlers.put(contractId, typedHandler);
                    });
            }
        }
    }

    /**
     * Creates a new builder for constructing a ServiceDiscoveryProductInteractionHandlerRegistry.
     *
     * @param serviceRegistry the service registry for handler discovery
     * @return a new builder
     */
    public static Builder builder(ServiceRegistry serviceRegistry) {
        return new Builder(serviceRegistry);
    }

    /**
     * Builder for ServiceDiscoveryProductInteractionHandlerRegistry.
     */
    public static final class Builder {
        private final ServiceRegistry serviceRegistry;
        private final Map<String, ProductInteractionHandler<?, ?>> handlers = new ConcurrentHashMap<>();

        private Builder(ServiceRegistry serviceRegistry) {
            this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "serviceRegistry must not be null");
        }

        /**
         * Manually registers a handler with its contract ID.
         *
         * <p>This is useful for testing scenarios or for handlers that cannot be
         * registered in the ServiceRegistry. Manually registered handlers take
         * precedence over service-discovered handlers.</p>
         *
         * @param handler the handler to register
         * @return this builder
         * @throws IllegalArgumentException if a handler is already registered for the contract ID
         */
        public Builder register(ProductInteractionHandler<?, ?> handler) {
            Objects.requireNonNull(handler, "handler must not be null");
            String contractId = handler.contractId();
            if (contractId == null || contractId.isBlank()) {
                throw new IllegalArgumentException("handler contractId must not be blank");
            }
            ProductInteractionHandler<?, ?> previous = handlers.putIfAbsent(contractId, handler);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "handler already registered for contractId " + contractId);
            }
            return this;
        }

        /**
         * Registers all handlers from another registry.
         *
         * @param registry the registry to copy handlers from
         * @return this builder
         */
        public Builder registerAll(ProductInteractionHandlerRegistry registry) {
            Objects.requireNonNull(registry, "registry must not be null");
            for (Map.Entry<String, ProductInteractionHandler<?, ?>> entry : registry.allHandlers().entrySet()) {
                register(entry.getValue());
            }
            return this;
        }

        /**
         * Registers multiple handlers.
         *
         * @param handlers the handlers to register
         * @return this builder
         */
        @SafeVarargs
        public final Builder registerAll(ProductInteractionHandler<?, ?>... handlers) {
            Objects.requireNonNull(handlers, "handlers must not be null");
            for (ProductInteractionHandler<?, ?> handler : handlers) {
                register(handler);
            }
            return this;
        }

        /**
         * Builds the registry and performs service discovery.
         *
         * @return a new ServiceDiscoveryProductInteractionHandlerRegistry
         */
        public ServiceDiscoveryProductInteractionHandlerRegistry build() {
            return new ServiceDiscoveryProductInteractionHandlerRegistry(this);
        }
    }
}
