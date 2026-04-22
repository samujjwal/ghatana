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
    public <T> @Nullable T getConfig(@NotNull Class<T> configType) { // GH-90000
        return null;
    }

    @Override
    public <T extends Plugin> @NotNull Optional<T> findPlugin(@NotNull String pluginId) { // GH-90000
        return Optional.empty(); // GH-90000
    }

    @Override
    public @NotNull List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability) { // GH-90000
        return List.of(); // GH-90000
    }

    @Override
    public @NotNull PluginInteractionBus getInteractionBus() { // GH-90000
        return new PluginInteractionBus() { // GH-90000
            @Override
            public <Req, Res> @NotNull io.activej.promise.Promise<Res> request(@NotNull String targetPluginId, @NotNull Req request, @NotNull Class<Res> responseType, @NotNull java.time.Duration timeout) { // GH-90000
                return io.activej.promise.Promise.ofException(new UnsupportedOperationException("Not implemented [GH-90000]"));
            }

            @Override
            public void publish(@NotNull String topic, @NotNull Object event) {} // GH-90000

            @Override
            public void subscribe(@NotNull String topic, @NotNull java.util.function.Consumer<Object> listener) {} // GH-90000
        };
    }
}
