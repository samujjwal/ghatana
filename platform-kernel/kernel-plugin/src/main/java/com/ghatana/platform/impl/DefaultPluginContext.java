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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Default implementation of PluginContext.
 *
 * @doc.type class
 * @doc.purpose Default context implementation
 * @doc.layer core
 * @doc.pattern Context
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
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map rawConfig = configuration;
        return (T) rawConfig.get(configType);
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
     *
     * <p>Plugins that wish to receive typed requests must register a handler via
     * {@link #registerHandler(String, java.util.function.Function)}.  Dispatching to a plugin
     * that has not registered a handler returns a {@link PluginCapabilityException} rather
     * than the generic {@link UnsupportedOperationException} so that callers can
     * distinguish "capability not available" from "method not yet coded".
     */
    static class DefaultPluginInteractionBus implements PluginInteractionBus {
        private final PluginRegistry registry;
        private final PluginInteractionPolicyEvaluator policyEvaluator =
                PluginInteractionPolicyEvaluator.defaultEvaluator();
        /** Typed request handlers keyed by plugin ID. */
        private final Map<String, java.util.function.Function<Object, Promise<Object>>> requestHandlers =
                new ConcurrentHashMap<>();
        /** Topic subscription map. */
        private final Map<String, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();
        /** Broker-local audit records for request/publish/delivery outcomes. */
        private final List<PluginInteractionAuditRecord> auditRecords = new CopyOnWriteArrayList<>();

        public DefaultPluginInteractionBus(PluginRegistry registry) {
            this.registry = registry;
        }

        /**
         * Registers a typed request handler for the given plugin ID.
         *
         * <p>Plugins that expose a request/response API should call this method
         * during their {@code initialize} lifecycle phase.
         *
         * @param pluginId the plugin ID to handle requests for
         * @param handler  the handler function; receives a raw request object and returns a Promise of the result
         */
        @SuppressWarnings("unchecked")
        @Override
        public <Req, Res> void registerHandler(String pluginId,
                java.util.function.Function<Req, Promise<Res>> handler) {
            requestHandlers.put(pluginId, (req) -> handler.apply((Req) req).map(res -> (Object) res));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <Req, Res> @NotNull Promise<Res> request(@NotNull String targetPluginId, @NotNull Req request,
                @NotNull Class<Res> responseType, @NotNull Duration timeout) {
            PluginInteractionEnvelope<Req> envelope = new PluginInteractionEnvelope<>(
                    "1.0.0",
                    java.util.UUID.randomUUID().toString(),
                    "plugin://legacy-request",
                    "unknown-plugin",
                    targetPluginId,
                    null,
                    null,
                    null,
                    null,
                    java.util.UUID.randomUUID().toString(),
                    java.time.Instant.now(),
                    request);
            return dispatchRequest(envelope, responseType);
        }

        @Override
        public <Req, Res> @NotNull Promise<Res> request(@NotNull PluginInteractionEnvelope<Req> envelope,
                @NotNull PluginContract<Req, Res> contract, @NotNull Duration timeout) {
            if (!contract.contractId().equals(envelope.contractId())) {
                recordAudit(envelope, null, "blocked", "plugin.contract_not_registered");
                return Promise.ofException(new PluginCapabilityException(
                        "plugin.contract_not_registered: Interaction envelope contractId does not match the typed contract"));
            }
            if (!contract.schemaVersion().equals(envelope.schemaVersion())) {
                recordAudit(envelope, null, "blocked", "plugin.contract_version_mismatch");
                return Promise.ofException(new PluginCapabilityException(
                        "plugin.contract_version_mismatch: Interaction envelope schemaVersion does not match the typed contract"));
            }
            if (timeout.isZero() || timeout.isNegative()) {
                recordAudit(envelope, null, "failed", "plugin.timeout");
                return Promise.ofException(new PluginCapabilityException(
                        "plugin.timeout: Interaction timeout must be positive"));
            }
            PluginInteractionPolicyDecision decision = policyEvaluator.evaluate(envelope, contract.policy());
            if (!decision.allowed()) {
                recordAudit(envelope, null, "denied", decision.reasonCode());
                return Promise.ofException(new PluginCapabilityException(
                        decision.reasonCode() + ": " + decision.message()));
            }
            return dispatchRequest(envelope, contract.responseType());
        }

        @SuppressWarnings("unchecked")
        private <Req, Res> @NotNull Promise<Res> dispatchRequest(
                @NotNull PluginInteractionEnvelope<Req> envelope,
                @NotNull Class<Res> responseType) {
            String targetPluginId = envelope.targetPluginId();
            if (targetPluginId == null || targetPluginId.isBlank()) {
                recordAudit(envelope, null, "blocked", "plugin.target_not_registered");
                return Promise.ofException(new PluginCapabilityException(
                        "plugin.target_not_registered: Interaction envelope missing targetPluginId"));
            }
            // Verify the plugin exists in the registry
            Optional<Plugin> plugin = registry.getPlugin(targetPluginId);
            if (plugin.isEmpty()) {
                recordAudit(envelope, null, "blocked", "plugin.target_not_registered");
                return Promise.ofException(new PluginCapabilityException(
                        "plugin.target_not_registered: Plugin '" + targetPluginId + "' is not registered in the plugin registry"));
            }
            // Dispatch to a registered handler if available
            java.util.function.Function<Object, Promise<Object>> handler = requestHandlers.get(targetPluginId);
            if (handler == null) {
                recordAudit(envelope, null, "blocked", "plugin.handler_missing");
                return Promise.ofException(new PluginCapabilityException(
                        "plugin.handler_missing: Plugin '" + targetPluginId + "' has not registered a request handler. "
                        + "Call DefaultPluginInteractionBus.registerHandler() during plugin initialization."));
            }
            recordAudit(envelope, null, "dispatched", "plugin.dispatched");
            return handler.apply(envelope.payload()).map(res -> {
                if (!responseType.isInstance(res)) {
                    recordAudit(envelope, null, "failed", "plugin.response_validation_failed");
                    throw new PluginCapabilityException(
                            "plugin.response_validation_failed: Handler response does not match contract responseType");
                }
                recordAudit(envelope, null, "succeeded", "plugin.succeeded");
                return responseType.cast(res);
            });
        }

        @Override
        public void publish(@NotNull String topic, @NotNull Object event) {
            PluginInteractionEnvelope<?> envelope = event instanceof PluginInteractionEnvelope<?>
                    ? (PluginInteractionEnvelope<?>) event
                    : null;
            if (envelope != null) {
                recordAudit(envelope, topic, "published", "plugin.published");
            }
            List<Consumer<Object>> listeners = subscribers.get(topic);
            if (listeners != null) {
                listeners.forEach(l -> {
                    l.accept(event);
                    if (envelope != null) {
                        recordAudit(envelope, topic, "delivered", "plugin.delivered");
                    }
                });
            }
        }

        @Override
        public void subscribe(@NotNull String topic, @NotNull Consumer<Object> listener) {
            subscribers.computeIfAbsent(topic, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(listener);
        }

        @Override
        public @NotNull List<PluginInteractionAuditRecord> auditRecords() {
            return List.copyOf(auditRecords);
        }

        private void recordAudit(
                @NotNull PluginInteractionEnvelope<?> envelope,
                String topic,
                String outcome,
                String reasonCode) {
            auditRecords.add(new PluginInteractionAuditRecord(
                    envelope.interactionId(),
                    envelope.contractId(),
                    envelope.schemaVersion(),
                    envelope.callerPluginId(),
                    envelope.targetPluginId(),
                    topic,
                    envelope.tenantId(),
                    envelope.workspaceId(),
                    envelope.correlationId(),
                    outcome,
                    reasonCode,
                    java.time.Instant.now()));
        }
    }
}
