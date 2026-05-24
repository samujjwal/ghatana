package com.ghatana.platform.plugin;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Bus for inter-plugin communication.
 *
 * @doc.type interface
 * @doc.purpose Plugin interaction
 * @doc.layer core
 * @doc.pattern Mediator
 */
public interface PluginInteractionBus {

    /**
     * Registers a request handler for a plugin target when the implementation supports in-process dispatch.
     */
    default <Req, Res> void registerHandler(
            @NotNull String pluginId,
            @NotNull Function<Req, Promise<Res>> handler) {
        throw new UnsupportedOperationException("plugin.handler_registration_unsupported");
    }

    /**
     * Sends a request to a specific plugin.
     */
    @NotNull
    <Req, Res> Promise<Res> request(
        @NotNull String targetPluginId,
        @NotNull Req request,
        @NotNull Class<Res> responseType,
        @NotNull Duration timeout
    );

    /**
     * Sends a request using a typed contract.
     */
    @NotNull
    default <Req, Res> Promise<Res> request(
        @NotNull String targetPluginId,
        @NotNull String callerPluginId,
        @NotNull PluginContract<Req, Res> contract,
        @NotNull Req request,
        @NotNull Duration timeout
    ) {
        PluginInteractionEnvelope<Req> envelope = new PluginInteractionEnvelope<>(
                contract.schemaVersion(),
                java.util.UUID.randomUUID().toString(),
                contract.contractId(),
                callerPluginId,
                targetPluginId,
                null,
                null,
                null,
                null,
                java.util.UUID.randomUUID().toString(),
                java.time.Instant.now(),
                request);
        return request(envelope, contract, timeout);
    }

    @NotNull
    default <Req, Res> Promise<Res> request(
        @NotNull String targetPluginId,
        @NotNull PluginContract<Req, Res> contract,
        @NotNull Req request,
        @NotNull Duration timeout
    ) {
        return request(targetPluginId, "unknown-plugin", contract, request, timeout);
    }

    /**
     * Sends a request using a typed contract and broker envelope.
     */
    @NotNull
    default <Req, Res> Promise<Res> request(
        @NotNull PluginInteractionEnvelope<Req> envelope,
        @NotNull PluginContract<Req, Res> contract,
        @NotNull Duration timeout
    ) {
        if (envelope.targetPluginId() == null) {
            return Promise.ofException(new PluginCapabilityException(
                    "plugin.target_not_registered: Interaction envelope missing targetPluginId"));
        }
        return request(envelope.targetPluginId(), envelope.payload(), contract.responseType(), timeout);
    }

    /**
     * Publishes a typed event envelope to all subscribers for a topic contract.
     */
    default <Event> void publish(
        @NotNull PluginTopicContract<Event> contract,
        @NotNull PluginInteractionEnvelope<Event> envelope
    ) {
        publish(contract.topic(), envelope);
    }

    /**
     * Publishes an event to all subscribers.
     */
    void publish(@NotNull String topic, @NotNull Object event);

    /**
     * Subscribes to events on a topic.
     */
    void subscribe(@NotNull String topic, @NotNull Consumer<Object> listener);

    /**
     * Returns broker-local audit records when the implementation supports it.
     */
    @NotNull
    default List<PluginInteractionAuditRecord> auditRecords() {
        return Collections.unmodifiableList(new ArrayList<>());
    }

    /**
     * Returns broker-local metrics when the implementation supports it.
     */
    @NotNull
    default PluginInteractionBrokerMetrics metrics() {
        return new PluginInteractionBrokerMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
