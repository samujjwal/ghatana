package com.ghatana.kernel.loader;

import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.registry.PluginRegistry;

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
                System.out.println("Plugin directory does not exist: " + pluginDirectory);
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
            System.out.println("Loading plugin from: " + pluginJar);

            // Load plugin JAR
            try (URLClassLoader classLoader = createPluginClassLoader(pluginJar)) {
                // Load plugin class using ServiceLoader
                ServiceLoader<KernelPlugin> loader = ServiceLoader.load(KernelPlugin.class, classLoader);

                for (KernelPlugin plugin : loader) {
                    System.out.println("Found plugin: " + plugin.getModuleId() + " v" + plugin.getVersion());

                    // Validate and register plugin
                    pluginRegistry.registerPlugin(plugin);

                    System.out.println("Successfully registered plugin: " + plugin.getModuleId());
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
            System.out.println("Successfully unloaded plugin: " + pluginId);
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
}
