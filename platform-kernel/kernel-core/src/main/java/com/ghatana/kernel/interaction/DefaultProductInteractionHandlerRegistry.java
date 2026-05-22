package com.ghatana.kernel.interaction;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default in-memory implementation of ProductInteractionHandlerRegistry.
 *
 * <p>This registry supports manual handler registration and is suitable for testing,
 * development, and simple production scenarios. For production use with service discovery,
 * implement the ProductInteractionHandlerRegistry interface with a discovery-backed
 * implementation.</p>
 *
 * @doc.type class
 * @doc.purpose Default in-memory handler registry for product interactions
 * @doc.layer kernel
 * @doc.pattern Registry
 */
public final class DefaultProductInteractionHandlerRegistry implements ProductInteractionHandlerRegistry {

    private final Map<String, ProductInteractionHandler<?, ?>> handlers;

    private DefaultProductInteractionHandlerRegistry(Builder builder) {
        this.handlers = Collections.unmodifiableMap(new ConcurrentHashMap<>(builder.handlers));
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
     * Creates a new builder for constructing a DefaultProductInteractionHandlerRegistry.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an empty registry.
     *
     * @return an empty registry
     */
    public static ProductInteractionHandlerRegistry empty() {
        return builder().build();
    }

    /**
     * Builder for DefaultProductInteractionHandlerRegistry.
     */
    public static final class Builder {
        private final Map<String, ProductInteractionHandler<?, ?>> handlers = new ConcurrentHashMap<>();

        private Builder() {}

        /**
         * Registers a handler with its contract ID.
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
         * Builds the registry.
         *
         * @return a new DefaultProductInteractionHandlerRegistry
         */
        public DefaultProductInteractionHandlerRegistry build() {
            return new DefaultProductInteractionHandlerRegistry(this);
        }
    }
}
