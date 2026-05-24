package com.ghatana.kernel.loader;

import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.registry.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Plugin loader for dynamic plugin loading.
 *
 * <p>Discovers and loads {@link com.ghatana.kernel.plugin.KernelPlugin} implementations
 * from JAR files in a configurable plugin directory using {@link java.util.ServiceLoader},
 * enabling hot-extension of the kernel without modifying the core modules.</p>
 *
 * @doc.type class
 * @doc.purpose Dynamic JAR-based plugin discovery and lifecycle management
 * @doc.layer kernel
 * @doc.pattern Service
 */
public class PluginLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginLoader.class);

    private final PluginRegistry pluginRegistry;
    private final String pluginDirectory;

    public PluginLoader(PluginRegistry pluginRegistry, String pluginDirectory) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry);
        this.pluginDirectory = Objects.requireNonNull(pluginDirectory);
    }

    /**
     * Load plugins from directory.
     *
     * Scans the plugin directory for plugin JARs and loads them.
     */
    public void loadPlugins() {
        try {
            Path pluginPath = Path.of(pluginDirectory);
            if (!Files.exists(pluginPath)) {
                LOGGER.warn("Plugin directory does not exist: {}", pluginDirectory);
                return;
            }

            Files.walk(pluginPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jar"))
                .forEach(this::loadPlugin);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load plugins from directory: " + pluginDirectory, e);
        }
    }

    /**
     * Load individual plugin from JAR file.
     *
     * @param pluginJar path to plugin JAR
     */
    public void loadPlugin(Path pluginJar) {
        try {
            LOGGER.info("Loading plugin from: {}", pluginJar);

            // Load plugin JAR
            try (URLClassLoader classLoader = createPluginClassLoader(pluginJar)) {
                // Load plugin class using ServiceLoader
                ServiceLoader<KernelPlugin> loader = ServiceLoader.load(KernelPlugin.class, classLoader);

                for (KernelPlugin plugin : loader) {
                    LOGGER.info("Found plugin: {} v{}", plugin.getModuleId(), plugin.getVersion());
                    loadDiscoveredPlugin(plugin);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load plugin: " + pluginJar, e);
        }
    }

    /**
     * Unload plugin by ID.
     *
     * @param pluginId the plugin identifier
     */
    public void unloadPlugin(String pluginId) {
        try {
            pluginRegistry.unregisterPlugin(pluginId);
            LOGGER.info("Successfully unloaded plugin: {}", pluginId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to unload plugin: " + pluginId, e);
        }
    }

    /**
     * Reload plugin.
     *
     * @param pluginId the plugin identifier
     */
    public void reloadPlugin(String pluginId) {
        unloadPlugin(pluginId);
        try {
            Path pluginPath = Path.of(pluginDirectory);
            Files.walk(pluginPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jar"))
                .filter(path -> path.getFileName().toString().contains(pluginId))
                .forEach(this::loadPlugin);
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload plugin: " + pluginId, e);
        }
    }

    /**
     * Create plugin class loader.
     *
     * @param pluginPath path to plugin JAR
     * @return URLClassLoader for the plugin
     */
    private URLClassLoader createPluginClassLoader(Path pluginPath) throws IOException {
        URL[] urls = {pluginPath.toUri().toURL()};
        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    /**
     * Register a plugin found by the discovery path after enforcing loader policies.
     *
     * @param plugin discovered plugin candidate
     * @return true when the plugin was registered, false when it was policy-blocked
     */
    protected boolean loadDiscoveredPlugin(KernelPlugin plugin) {
        Objects.requireNonNull(plugin);

        if (isProductionEnvironment() && plugin.getManifest().isEphemeral()) {
            LOGGER.warn("Ephemeral plugin blocked in production: {}", plugin.getModuleId());
            return false;
        }

        pluginRegistry.registerPlugin(plugin);
        LOGGER.info("Successfully registered plugin: {}", plugin.getModuleId());
        return true;
    }

    /**
     * Get plugin directory.
     *
     * @return plugin directory path
     */
    public String getPluginDirectory() {
        return pluginDirectory;
    }

    /**
     * Check if plugin directory exists.
     *
     * @return true if directory exists
     */
    public boolean pluginDirectoryExists() {
        return Files.exists(Path.of(pluginDirectory));
    }

    /**
     * Create plugin directory if it doesn't exist.
     *
     * @return true if directory was created
     */
    public boolean createPluginDirectory() {
        try {
            Path pluginPath = Path.of(pluginDirectory);
            if (!Files.exists(pluginPath)) {
                Files.createDirectories(pluginPath);
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create plugin directory: " + pluginDirectory, e);
        }
    }

    /**
     * Check if running in production environment.
     *
     * @return true if production environment
     */
    protected boolean isProductionEnvironment() {
        String env = System.getenv("ENVIRONMENT");
        if (env == null) {
            env = System.getenv("NODE_ENV");
        }
        if (env == null) {
            env = System.getProperty("environment");
        }
        return "prod".equalsIgnoreCase(env) || "production".equalsIgnoreCase(env);
    }
}
