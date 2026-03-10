/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Core SPI — Plugin Adapter
 */
package com.ghatana.yappc.plugin;

import com.ghatana.platform.plugin.HealthStatus;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginCapability;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

/**
 * Adapts a {@link YAPPCPlugin} to the platform {@link Plugin} interface.
 *
 * <p>Use this when registering YAPPC plugins with the platform's plugin infrastructure.
 *
 * @doc.type class
 * @doc.purpose Bridges YAPPC plugin SPI to platform Plugin contract
 * @doc.layer core
 * @doc.pattern Adapter
 */
public final class YappcPluginToPlatformAdapter implements Plugin {

    private final YAPPCPlugin delegate;

    public YappcPluginToPlatformAdapter(@NotNull YAPPCPlugin delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @NotNull
    public PluginMetadata metadata() {
        com.ghatana.yappc.plugin.PluginMetadata yappcMeta = delegate.getMetadata();
        return PluginMetadata.builder()
            .id(yappcMeta.getId())
            .name(yappcMeta.getName())
            .version(yappcMeta.getVersion())
            .description(yappcMeta.getDescription() != null ? yappcMeta.getDescription() : "")
            .build();
    }

    @Override
    @NotNull
    public PluginState getState() {
        return PluginState.RUNNING;
    }

    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull com.ghatana.platform.plugin.PluginContext context) {
        PluginContext yappcContext = new PlatformPluginContextAdapter(context);
        return delegate.initialize(yappcContext);
    }

    @Override
    @NotNull
    public Promise<Void> start() {
        return delegate.start();
    }

    @Override
    @NotNull
    public Promise<Void> stop() {
        return delegate.stop();
    }

    @Override
    @NotNull
    public Promise<Void> shutdown() {
        return delegate.shutdown();
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck() {
        return delegate.checkHealth()
            .map(h -> HealthStatus.ok());
    }

    @Override
    @NotNull
    public Set<PluginCapability> getCapabilities() {
        return Set.of();
    }

    /**
     * Returns the wrapped YAPPC plugin.
     */
    @NotNull
    public YAPPCPlugin getDelegate() {
        return delegate;
    }
}
