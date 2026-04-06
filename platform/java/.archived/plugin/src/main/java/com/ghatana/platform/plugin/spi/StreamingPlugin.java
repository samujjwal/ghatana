package com.ghatana.platform.plugin.spi;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * SPI for Streaming Plugins (e.g., Kafka, Pulsar).
 *
 * @param <T> The type of message
 *
 * @doc.type interface
 * @doc.purpose Streaming abstraction
 * @doc.layer core
 */
public interface StreamingPlugin<T> extends Plugin {

    /**
     * Publishes a message to a topic.
     *
     * @param topic The topic name
     * @param message The message to publish
     * @param tenantId The tenant ID
     * @return A Promise resolving when published
     */
    @NotNull
    Promise<Void> publish(@NotNull String topic, @NotNull T message, @NotNull TenantId tenantId);

    /**
     * Subscribes to a topic.
     *
     * @param topic The topic name
     * @param tenantId The tenant ID
     * @param listener The consumer for incoming messages
     * @return A Promise resolving to a subscription handle (Runnable to unsubscribe)
     */
    @NotNull
    Promise<Runnable> subscribe(@NotNull String topic, @NotNull TenantId tenantId, @NotNull Consumer<T> listener);
}
