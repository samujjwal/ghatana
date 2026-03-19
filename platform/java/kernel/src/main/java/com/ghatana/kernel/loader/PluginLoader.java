package com.ghatana.kernel.loader;

import com.ghatana.kernel.plugin.ProductPlugin;
import com.ghatana.kernel.registry.PluginRegistry;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * Plugin loader for dynamic plugin loading.
 * 
 * This loader can discover and load plugins from JAR files
 * without requiring kernel modifications.
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
            URLClassLoader classLoader = createPluginClassLoader(pluginJar);
            
            // Load plugin class using ServiceLoader
            ServiceLoader<ProductPlugin> loader = ServiceLoader.load(ProductPlugin.class, classLoader);
            
            for (ProductPlugin plugin : loader) {
                System.out.println("Found plugin: " + plugin.getProductId() + " v" + plugin.getProductVersion());
                
                // Validate and register plugin
                pluginRegistry.registerPlugin(plugin);
                
                System.out.println("Successfully registered plugin: " + plugin.getProductId());
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load plugin: " + pluginJar, e);
        }
    }

    /**
     * Unload plugin by ID.
     * 
     * @param productId the product identifier
     */
    public void unloadPlugin(String productId) {
        try {
            pluginRegistry.unregisterPlugin(productId);
            System.out.println("Successfully unloaded plugin: " + productId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to unload plugin: " + productId, e);
        }
    }

    /**
     * Reload plugin.
     * 
     * @param productId the product identifier
     */
    public void reloadPlugin(String productId) {
        // Unload first
        unloadPlugin(productId);
        
        // Find and reload the plugin JAR
        try {
            Path pluginPath = Path.of(pluginDirectory);
            Files.walk(pluginPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jar"))
                .filter(path -> path.getFileName().toString().contains(productId))
                .forEach(this::loadPlugin);
                
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload plugin: " + productId, e);
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
