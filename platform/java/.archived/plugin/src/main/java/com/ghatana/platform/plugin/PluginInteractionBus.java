package com.ghatana.platform.plugin;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Bus for inter-plugin communication.
 *
 * @doc.type interface
 * @doc.purpose Plugin interaction
 * @doc.layer core
 */
public interface PluginInteractionBus {

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
        @NotNull PluginContract<Req, Res> contract,
        @NotNull Req request,
        @NotNull Duration timeout
    ) {
        return request(targetPluginId, request, contract.responseType(), timeout);
    }

    /**
     * Publishes an event to all subscribers.
     */
    void publish(@NotNull String topic, @NotNull Object event);

    /**
     * Subscribes to events on a topic.
     */
    void subscribe(@NotNull String topic, @NotNull Consumer<Object> listener);
}
