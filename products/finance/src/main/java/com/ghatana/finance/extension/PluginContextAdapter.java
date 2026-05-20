package com.ghatana.finance.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginCapability;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginInteractionBus;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Adapter class to bridge KernelContext to PluginContext.
 *
 * <p>Allows product extensions to use the shared plugin implementations
 * while maintaining compatibility with the existing kernel infrastructure.</p>
 *
 * @doc.type class
 * @doc.purpose Adapter between KernelContext and PluginContext
 * @doc.layer product
 * @doc.pattern Adapter
 * @since 1.0.0
 */
class PluginContextAdapter implements PluginContext {

    private static final List<Plugin> NO_PLUGINS = List.of();

    private final KernelContext kernelContext;

    PluginContextAdapter(KernelContext kernelContext) {
        this.kernelContext = kernelContext;
    }

    @Override
    public <T> T getConfig(Class<T> configType) {
        return null;
    }

    @Override
    public String getTenantId() {
        return kernelContext.getTenantContext() != null
            ? kernelContext.getTenantContext().getTenantId()
            : null;
    }

    @Override
    public <T extends Plugin> Optional<T> findPlugin(String pluginId) {
        return Optional.empty();
    }

    @Override
    public List<Plugin> findPluginsByCapability(Class<? extends PluginCapability> capability) {
        return NO_PLUGINS;
    }

    @Override
    public PluginInteractionBus getInteractionBus() {
        return new PluginInteractionBus() {
            @Override
            public <Req, Res> Promise<Res> request(String targetPluginId, Req request, Class<Res> responseType, Duration timeout) {
                return Promise.ofException(new UnsupportedOperationException("request unavailable"));
            }

            @Override
            public void publish(String topic, Object event) {
                // No-op adapter
            }

            @Override
            public void subscribe(String topic, java.util.function.Consumer<Object> listener) {
                // No-op adapter
            }
        };
    }
}
