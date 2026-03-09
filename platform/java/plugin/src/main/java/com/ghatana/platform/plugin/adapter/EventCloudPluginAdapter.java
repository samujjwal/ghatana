package com.ghatana.platform.plugin.adapter;

import com.ghatana.core.event.cloud.*;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.plugin.spi.StreamingPlugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Adapter allowing EventCloud to be used as a StreamingPlugin.
 * Enables embeddable usage of EventCloud within the plugin framework.
 *
 * @doc.type class
 * @doc.purpose Adapter for EventCloud as StreamingPlugin
 * @doc.layer core
 */
public class EventCloudPluginAdapter implements StreamingPlugin<EventRecord> {

    private final EventCloud eventCloud;
    private PluginState state = PluginState.UNLOADED;

    public EventCloudPluginAdapter(EventCloud eventCloud) {
        this.eventCloud = eventCloud;
    }

    @Override
    public @NotNull PluginMetadata metadata() {
        return new PluginMetadata(
            "event-cloud-adapter",
            "EventCloud Adapter",
            "1.0.0",
            "Native EventCloud integration",
            PluginType.STREAMING,
            "Ghatana",
            "Proprietary",
            Set.of(),
            Map.of(),
            Set.of(),
            null
        );
    }

    @Override
    public @NotNull PluginState getState() {
        return state;
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
        state = PluginState.INITIALIZED;
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> start() {
        state = PluginState.RUNNING;
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> stop() {
        state = PluginState.STOPPED;
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> publish(@NotNull String topic, @NotNull EventRecord message, @NotNull TenantId tenantId) {
        EventCloud.AppendRequest request = new EventCloud.AppendRequest(
            message, 
            EventCloud.AppendOptions.defaults()
        );
        
        return eventCloud.append(request)
            .map(result -> null);
    }

    @Override
    public @NotNull Promise<Runnable> subscribe(@NotNull String topic, @NotNull TenantId tenantId, @NotNull Consumer<EventRecord> listener) {
        EventCloud.Selection selection = EventCloud.Selection.byTypes(topic);
        EventCloud.StartingPositions start = new EventCloud.StartAtLatest();

        EventStream stream = eventCloud.subscribe(tenantId, selection, start);
        
        stream.onEvent(chunk -> {
            for (EventCloud.EventEnvelope envelope : chunk.events()) {
                listener.accept(envelope.record());
            }
            // Simple backpressure strategy: request more as we consume
            int count = chunk.events().size();
            if (count > 0) {
                stream.request(count);
            } else {
                stream.request(1);
            }
        });
        
        // Initial request
        stream.request(10);
        
        return Promise.of(stream::close);
    }
}
