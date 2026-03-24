package com.ghatana.yappc.plugin.bridge;

import com.ghatana.platform.plugin.HealthStatus;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginCapability;
import com.ghatana.platform.plugin.PluginCompatibility;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.yappc.plugin.YAPPCPlugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridge that adapts a YAPPC unified plugin ({@link YAPPCPlugin}) to the
 * platform canonical {@link Plugin} interface.
 *
 * <p>This enables YAPPC-specific plugins to be registered and managed by
 * the platform-wide {@link com.ghatana.platform.plugin.PluginRegistry}
 * without modification to either the plugin or the registry.</p>
 *
 * <p>Unlike the legacy {@code YappcPluginAdapter} (which bridges the deprecated
 * {@code com.ghatana.yappc.framework.api.plugin.YappcPlugin}), this bridge
 * operates on the current {@code com.ghatana.yappc.plugin.YAPPCPlugin} SPI.</p>
 *
 * <h3>Thread Safety</h3>
 * Thread-safe. State transitions are atomic via {@link AtomicReference}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * YAPPCPlugin yappcPlugin = new MyValidatorPlugin();
 * Plugin platformPlugin = new PlatformPluginBridge(yappcPlugin);
 * platformRegistry.register(platformPlugin);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bridge YAPPCPlugin (unified SPI) to platform Plugin interface
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class PlatformPluginBridge implements Plugin {

    private static final Logger logger = LoggerFactory.getLogger(PlatformPluginBridge.class);

    private final YAPPCPlugin delegate;
    private final PluginMetadata platformMetadata;
    private final AtomicReference<PluginState> state;

    /**
     * Creates a bridge for the given YAPPC plugin.
     *
     * @param delegate the YAPPC plugin to bridge
     */
    public PlatformPluginBridge(@NotNull YAPPCPlugin delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate must not be null");
        this.platformMetadata = convertMetadata(delegate.getMetadata());
        this.state = new AtomicReference<>(PluginState.DISCOVERED);
    }

    private static PluginMetadata convertMetadata(com.ghatana.yappc.plugin.PluginMetadata yappcMeta) {
        PluginType type = mapCategory(yappcMeta.getCategory());
        return PluginMetadata.builder()
                .id("yappc." + yappcMeta.getId())
                .name(yappcMeta.getName())
                .version(yappcMeta.getVersion())
                .description(yappcMeta.getDescription())
                .type(type)
                .author(yappcMeta.getAuthor().isEmpty() ? "Ghatana YAPPC" : yappcMeta.getAuthor())
                .license("Apache-2.0")
                .tags(Set.of("yappc", type.name().toLowerCase()))
                .capabilities(mapCapabilities(yappcMeta))
                .build();
    }

    private static PluginType mapCategory(String category) {
        if (category == null) {
            return PluginType.INTEGRATION;
        }
        return switch (category.toLowerCase()) {
            case "ai", "agent" -> PluginType.AI;
            case "observability", "telemetry" -> PluginType.OBSERVABILITY;
            case "governance", "validation" -> PluginType.GOVERNANCE;
            case "storage", "persistence" -> PluginType.STORAGE;
            case "processing" -> PluginType.PROCESSING;
            case "streaming" -> PluginType.STREAMING;
            default -> PluginType.INTEGRATION;
        };
    }

    private static Set<String> mapCapabilities(com.ghatana.yappc.plugin.PluginMetadata yappcMeta) {
        // The category is the primary capability descriptor
        return yappcMeta.getCategory() != null
                ? Set.of(yappcMeta.getCategory())
                : Set.of();
    }

    @Override
    @NotNull
    public PluginMetadata metadata() {
        return platformMetadata;
    }

    @Override
    @NotNull
    public PluginState getState() {
        return state.get();
    }

    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull com.ghatana.platform.plugin.PluginContext context) {
        logger.info("Initializing YAPPC plugin via bridge: {}", delegate.getMetadata().getName());

        // Create a YAPPC PluginContext from the platform PluginContext
        com.ghatana.yappc.plugin.PluginContext yappcContext = new YappcPluginContextBridge(context);

        return delegate.initialize(yappcContext)
                .map(v -> {
                    state.set(PluginState.INITIALIZED);
                    logger.info("YAPPC plugin initialized: {}", delegate.getMetadata().getName());
                    return v;
                })
                .mapException(e -> {
                    state.set(PluginState.FAILED);
                    logger.error("Failed to initialize YAPPC plugin: {}", delegate.getMetadata().getName(), e);
                    return e;
                });
    }

    @Override
    @NotNull
    public Promise<Void> start() {
        state.set(PluginState.STARTING);
        return delegate.start()
                .map(v -> {
                    state.set(PluginState.RUNNING);
                    logger.info("YAPPC plugin started: {}", delegate.getMetadata().getName());
                    return v;
                })
                .mapException(e -> {
                    state.set(PluginState.FAILED);
                    logger.error("Failed to start YAPPC plugin: {}", delegate.getMetadata().getName(), e);
                    return e;
                });
    }

    @Override
    @NotNull
    public Promise<Void> stop() {
        state.set(PluginState.STOPPING);
        return delegate.stop()
                .map(v -> {
                    state.set(PluginState.STOPPED);
                    logger.info("YAPPC plugin stopped: {}", delegate.getMetadata().getName());
                    return v;
                })
                .mapException(e -> {
                    state.set(PluginState.FAILED);
                    logger.error("Error stopping YAPPC plugin: {}", delegate.getMetadata().getName(), e);
                    return e;
                });
    }

    @Override
    @NotNull
    public Promise<Void> shutdown() {
        return delegate.shutdown()
                .map(v -> {
                    state.set(PluginState.UNLOADED);
                    return v;
                });
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck() {
        return delegate.checkHealth()
                .map(yappcHealth -> yappcHealth.isHealthy()
                        ? HealthStatus.ok(yappcHealth.getMessage())
                        : HealthStatus.unhealthy(yappcHealth.getMessage()))
                .mapException(e -> {
                    logger.warn("Health check failed for {}: {}", delegate.getMetadata().getName(), e.getMessage());
                    return e;
                });
    }

    @Override
    @NotNull
    public Set<PluginCapability> getCapabilities() {
        return Set.of();
    }

    @Override
    @NotNull
    public <T extends PluginCapability> Optional<T> getCapability(@NotNull Class<T> capabilityType) {
        return Optional.empty();
    }

    /**
     * Returns the underlying YAPPC plugin.
     *
     * @return the delegate YAPPC plugin
     */
    @NotNull
    public YAPPCPlugin getDelegate() {
        return delegate;
    }

    /**
     * Bridge that adapts platform PluginContext to YAPPC PluginContext.
     */
    private static final class YappcPluginContextBridge implements com.ghatana.yappc.plugin.PluginContext {

        private final com.ghatana.platform.plugin.PluginContext platformContext;

        YappcPluginContextBridge(com.ghatana.platform.plugin.PluginContext platformContext) {
            this.platformContext = platformContext;
        }

        @Override
        public Map<String, Object> getConfiguration() {
            return platformContext.getConfigMap();
        }

        @Override
        public Object getConfigValue(String key) {
            return platformContext.getConfigMap().get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getConfigValue(String key, T defaultValue) {
            Object value = platformContext.getConfigMap().get(key);
            if (value != null) {
                try {
                    return (T) value;
                } catch (ClassCastException e) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }

        @Override
        public String getYappcVersion() {
            return "2.0.0";
        }

        @Override
        public String getPluginDirectory() {
            return platformContext.getConfig("pluginDirectory", "plugins");
        }
    }
}
