package com.ghatana.platform.plugin.adapter;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.plugin.spi.StreamingPlugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Adapter allowing EventLogStore to be used as a StreamingPlugin.
 * Enables embeddable usage of event log storage within the plugin framework.
 *
 * @doc.type class
 * @doc.purpose Adapter for EventLogStore as StreamingPlugin
 * @doc.layer core
 * @doc.pattern Adapter
 */
public class EventCloudPluginAdapter implements StreamingPlugin<EventLogStore.EventEntry> {

    private final EventLogStore eventLogStore;
    private PluginState state = PluginState.UNLOADED;

    public EventCloudPluginAdapter(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
    }

    @Override
    public @NotNull PluginMetadata metadata() {
        return new PluginMetadata(
            "event-log-adapter",
            "EventLog Adapter",
            "1.0.0",
            "Native EventLog integration",
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
    public @NotNull Promise<Void> publish(@NotNull String topic, @NotNull EventLogStore.EventEntry message, @NotNull TenantId tenantId) {
        TenantContext tenant = TenantContext.of(tenantId.value());
        return eventLogStore.append(tenant, message)
            .map(offset -> null);
    }

    @Override
    public @NotNull Promise<Runnable> subscribe(@NotNull String topic, @NotNull TenantId tenantId, @NotNull Consumer<EventLogStore.EventEntry> listener) {
        TenantContext tenant = TenantContext.of(tenantId.value());
        return eventLogStore.tail(tenant, Offset.zero(), listener)
            .map(subscription -> subscription::cancel);
    }
}
