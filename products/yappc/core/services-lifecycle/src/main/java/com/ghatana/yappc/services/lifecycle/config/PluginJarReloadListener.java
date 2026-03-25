/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Services Lifecycle Module
 */
package com.ghatana.yappc.services.lifecycle.config;

import com.ghatana.yappc.framework.core.plugin.hotreload.HotReloadPluginRegistry;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ConfigReloadListener} that reacts to JAR file changes in the
 * {@code plugins/} directory by requesting a hot-reload from
 * {@link HotReloadPluginRegistry}.
 *
 * <p>Wired into {@link ConfigWatchService} during service startup (10.3.2).
 * When a plugin JAR is detected as modified or replaced, the registry
 * attempts to reload that plugin without a service restart.
 *
 * @doc.type class
 * @doc.purpose Listens for plugin JAR changes and triggers hot-reload (10.3.2)
 * @doc.layer product
 * @doc.pattern Listener
 */
public class PluginJarReloadListener implements ConfigReloadListener {

    private static final Logger log = LoggerFactory.getLogger(PluginJarReloadListener.class);

    private final HotReloadPluginRegistry registry;
    private final Consumer<ConfigChangeEvent> onUnknownPlugin;

    /**
     * @param registry        registry to trigger reload on
     * @param onUnknownPlugin called when a JAR change references a plugin not in the registry
     */
    public PluginJarReloadListener(
            HotReloadPluginRegistry registry,
            Consumer<ConfigChangeEvent> onUnknownPlugin) {
        this.registry = registry;
        this.onUnknownPlugin = onUnknownPlugin;
    }

    /**
     * Accepts change events for files matching {@code plugins/**\/*.jar}.
     *
     * @param relativePath path relative to the watched config root
     * @return {@code true} for JAR files under {@code plugins/}
     */
    @Override
    public boolean accepts(String relativePath) {
        return relativePath.startsWith("plugins/") && relativePath.endsWith(".jar");
    }

    /**
     * Triggers hot-reload when a plugin JAR is modified.
     *
     * <p>The plugin id is derived as the JAR file name without the {@code .jar} extension.
     * For example, {@code plugins/intent-agent-plugin-2.0.jar} maps to plugin id
     * {@code intent-agent-plugin-2.0}.
     *
     * @param event describes the file system change
     */
    @Override
    public void onConfigChanged(ConfigChangeEvent event) {
        String relativePath = event.relativePath();
        // Derive plugin id from filename (strip .jar extension)
        String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        String pluginId = fileName.endsWith(".jar") ? fileName.substring(0, fileName.length() - 4) : fileName;

        if (!registry.isRegistered(pluginId)) {
            log.warn("Plugin JAR changed ({}) but no plugin '{}' is registered — skipping hot-reload.",
                    relativePath, pluginId);
            onUnknownPlugin.accept(event);
            return;
        }

        log.info("Plugin JAR changed ({}), triggering hot-reload for plugin '{}'.", relativePath, pluginId);
        try {
            registry.reload(pluginId);
        } catch (Exception e) {
            log.error("Hot-reload failed for plugin '{}': {}", pluginId, e.getMessage(), e);
        }
    }
}
