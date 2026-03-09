package com.ghatana.platform.plugin.test;

import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginCapability;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginInteractionBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class TestPluginContext implements PluginContext {

    @Override
    public <T> @Nullable T getConfig(@NotNull Class<T> configType) {
        return null;
    }

    @Override
    public <T extends Plugin> @NotNull Optional<T> findPlugin(@NotNull String pluginId) {
        return Optional.empty();
    }

    @Override
    public @NotNull List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability) {
        return List.of();
    }

    @Override
    public @NotNull PluginInteractionBus getInteractionBus() {
        return new PluginInteractionBus() {
            @Override
            public <Req, Res> @NotNull io.activej.promise.Promise<Res> request(@NotNull String targetPluginId, @NotNull Req request, @NotNull Class<Res> responseType, @NotNull java.time.Duration timeout) {
                return io.activej.promise.Promise.ofException(new UnsupportedOperationException("Not implemented"));
            }

            @Override
            public void publish(@NotNull String topic, @NotNull Object event) {}

            @Override
            public void subscribe(@NotNull String topic, @NotNull java.util.function.Consumer<Object> listener) {}
        };
    }
}
