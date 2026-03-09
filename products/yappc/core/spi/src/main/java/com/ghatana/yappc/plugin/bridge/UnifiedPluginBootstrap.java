package com.ghatana.yappc.plugin.bridge;

import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.yappc.plugin.YAPPCPlugin;
import com.ghatana.yappc.plugin.migration.PluginMigrationUtil;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Bootstraps all YAPPC plugins into the platform {@link PluginRegistry}.
 *
 * <p>Discovery order:
 * <ol>
 *   <li>Native {@link YAPPCPlugin} implementations via {@link ServiceLoader}</li>
 *   <li>Legacy {@code YappcPlugin} implementations via {@link PluginMigrationUtil}</li>
 * </ol>
 *
 * <p>All discovered plugins are wrapped in a {@link PlatformPluginBridge} and
 * registered with the platform-wide {@link PluginRegistry}.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PluginRegistry registry = new PluginRegistry();
 * UnifiedPluginBootstrap bootstrap = new UnifiedPluginBootstrap(registry);
 * Promise<Void> ready = bootstrap.discoverAndRegister();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Unified plugin discovery and registration bootstrap
 * @doc.layer product
 * @doc.pattern Bootstrap
 */
public final class UnifiedPluginBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedPluginBootstrap.class);

    private final PluginRegistry platformRegistry;

    /**
     * Creates a bootstrap bound to the given platform registry.
     *
     * @param platformRegistry the platform plugin registry
     */
    public UnifiedPluginBootstrap(@NotNull PluginRegistry platformRegistry) {
        this.platformRegistry = java.util.Objects.requireNonNull(platformRegistry,
                "platformRegistry must not be null");
    }

    /**
     * Discovers all YAPPC plugins (native + legacy) and registers them
     * with the platform registry.
     *
     * @return a Promise that completes when all plugins are registered
     */
    @NotNull
    public Promise<Void> discoverAndRegister() {
        logger.info("Starting unified YAPPC plugin discovery...");

        List<Plugin> bridgedPlugins = new ArrayList<>();

        // 1. Discover native YAPPCPlugin implementations
        int nativeCount = discoverNativePlugins(bridgedPlugins);

        // 2. Discover and wrap legacy framework plugins
        int legacyCount = discoverLegacyPlugins(bridgedPlugins);

        // 3. Register all with platform registry
        int registered = 0;
        int skipped = 0;
        for (Plugin plugin : bridgedPlugins) {
            try {
                if (!platformRegistry.isRegistered(plugin.metadata().id())) {
                    platformRegistry.register(plugin);
                    registered++;
                } else {
                    logger.warn("Plugin already registered, skipping: {}", plugin.metadata().id());
                    skipped++;
                }
            } catch (Exception e) {
                logger.error("Failed to register plugin: {}", plugin.metadata().id(), e);
                skipped++;
            }
        }

        logger.info("YAPPC plugin discovery complete: {} native, {} legacy, {} registered, {} skipped",
                nativeCount, legacyCount, registered, skipped);

        return Promise.complete();
    }

    /**
     * Discovers native {@link YAPPCPlugin} implementations via ServiceLoader
     * and wraps them in {@link PlatformPluginBridge}.
     */
    private int discoverNativePlugins(List<Plugin> target) {
        int count = 0;
        try {
            ServiceLoader<YAPPCPlugin> loader = ServiceLoader.load(YAPPCPlugin.class);
            for (YAPPCPlugin plugin : loader) {
                try {
                    target.add(new PlatformPluginBridge(plugin));
                    count++;
                    logger.debug("Discovered native YAPPC plugin: {}", plugin.getMetadata().getName());
                } catch (Exception e) {
                    logger.error("Failed to bridge native plugin: {}", plugin.getMetadata().getName(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to discover native YAPPC plugins", e);
        }
        logger.info("Discovered {} native YAPPC plugins", count);
        return count;
    }

    /**
     * Discovers legacy framework plugins via {@link PluginMigrationUtil}
     * and wraps the resulting unified plugins in {@link PlatformPluginBridge}.
     */
    private int discoverLegacyPlugins(List<Plugin> target) {
        int count = 0;
        try {
            List<YAPPCPlugin> legacyWrapped = PluginMigrationUtil.discoverAndWrapAllLegacyPlugins();
            for (YAPPCPlugin plugin : legacyWrapped) {
                try {
                    target.add(new PlatformPluginBridge(plugin));
                    count++;
                    logger.debug("Discovered legacy YAPPC plugin: {}", plugin.getMetadata().getName());
                } catch (Exception e) {
                    logger.error("Failed to bridge legacy plugin: {}", plugin.getMetadata().getName(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to discover legacy YAPPC plugins", e);
        }
        logger.info("Discovered {} legacy YAPPC plugins", count);
        return count;
    }

    /**
     * Initializes and starts all registered YAPPC plugins in the platform registry.
     *
     * @param context the platform plugin context to pass to plugins
     * @return a Promise that completes when all plugins are initialized and started
     */
    @NotNull
    public Promise<Void> initializeAll(@NotNull com.ghatana.platform.plugin.PluginContext context) {
        return platformRegistry.initializeAll(context)
                .then(v -> platformRegistry.startAll());
    }
}
