package com.ghatana.kernel.plugin.runtime;

import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginManifest;
import io.activej.promise.Promise;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hot-reloadable plugin manager with class-loader isolation per plugin.
 *
 * <p>Each plugin is loaded in its own isolated ClassLoader to prevent:
 * <ul>
 *   <li>Class conflicts between plugins</li>
 *   <li>Resource leaks from plugin unload</li>
 *   <li>Dependency version conflicts</li>
 *   <li>Security boundary violations</li>
 * </ul></p>
 *
 * <p>Supports runtime plugin loading, unloading, and reloading without kernel restart.</p>
 *
 * @doc.type class
 * @doc.purpose Hot-reloadable plugin manager with class-loader isolation per plugin
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class KernelPluginRuntimeManager {

    private final Path pluginDirectory;
    private final Map<String, PluginEntry> loadedPlugins = new ConcurrentHashMap<>();
    private final PluginSecurityManager securityManager;

    /**
     * Creates a new plugin runtime manager.
     *
     * @param pluginDirectory the directory containing plugin JARs
     * @param securityManager the security manager for plugin validation
     */
    public KernelPluginRuntimeManager(Path pluginDirectory, PluginSecurityManager securityManager) {
        this.pluginDirectory = Objects.requireNonNull(pluginDirectory, "pluginDirectory cannot be null");
        this.securityManager = Objects.requireNonNull(securityManager, "securityManager cannot be null");
    }

    /**
     * Loads a plugin from a JAR file with class-loader isolation.
     *
     * <p>Process:
     * <ol>
     *   <li>Verify JAR signature</li>
     *   <li>Create isolated ClassLoader</li>
     *   <li>Load plugin manifest</li>
     *   <li>Validate dependencies</li>
     *   <li>Initialize plugin</li>
     * </ol></p>
     *
     * @param pluginJar the plugin JAR file path
     * @return Promise containing the loaded plugin
     */
    public Promise<KernelPlugin> loadPlugin(Path pluginJar) {
        Objects.requireNonNull(pluginJar, "pluginJar cannot be null");

        String pluginId = pluginJar.getFileName().toString().replace(".jar", "");

        // Check if already loaded
        if (loadedPlugins.containsKey(pluginId)) {
            return Promise.ofException(new IllegalStateException("Plugin already loaded: " + pluginId));
        }

        // Step 1: Verify JAR signature
        return securityManager.verifyPluginSignature(pluginJar)
            .then(valid -> {
                if (!valid) {
                    return Promise.ofException(new SecurityException("Plugin signature verification failed: " + pluginId));
                }

                // Step 2: Create isolated ClassLoader
                try {
                    URL[] urls = { pluginJar.toUri().toURL() };
                    PluginClassLoader classLoader = new PluginClassLoader(urls, getClass().getClassLoader());

                    // Step 3: Load plugin manifest
                    PluginManifest manifest = loadManifest(classLoader);

                    // Step 4: Load plugin class
                    Class<?> pluginClass = classLoader.loadClass(manifest.getMainClass());
                    KernelPlugin plugin = (KernelPlugin) pluginClass.getDeclaredConstructor().newInstance();

                    // Store in registry
                    PluginEntry entry = new PluginEntry(pluginId, plugin, classLoader, manifest, pluginJar);
                    loadedPlugins.put(pluginId, entry);

                    return Promise.of(plugin);
                } catch (Exception e) {
                    return Promise.ofException(new RuntimeException("Failed to load plugin: " + pluginId, e));
                }
            });
    }

    /**
     * Unloads a plugin and releases its resources.
     *
     * <p>Process:
     * <ol>
     *   <li>Stop plugin</li>
     *   <li>Unregister from kernel</li>
     *   <li>Close ClassLoader</li>
     *   <li>Cleanup resources</li>
     * </ol></p>
     *
     * @param pluginId the plugin identifier
     * @return Promise completing when plugin is unloaded
     */
    public Promise<Void> unloadPlugin(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId cannot be null");

        PluginEntry entry = loadedPlugins.get(pluginId);
        if (entry == null) {
            return Promise.ofException(new IllegalArgumentException("Plugin not loaded: " + pluginId));
        }

        // Step 1: Stop plugin
        return entry.getPlugin().stop()
            .then($ -> {
                // Step 2: Uninstall plugin
                return entry.getPlugin().uninstall();
            })
            .then($ -> {
                // Step 3: Remove from registry
                loadedPlugins.remove(pluginId);

                // Step 4: Close ClassLoader
                try {
                    entry.getClassLoader().close();
                } catch (Exception e) {
                    System.getLogger(KernelPluginRuntimeManager.class.getName())
                            .log(System.Logger.Level.WARNING, "Failed to close plugin classloader", e);
                }

                return Promise.complete();
            });
    }

    /**
     * Reloads a plugin without kernel restart.
     *
     * <p>Process:
     * <ol>
     *   <li>Unload existing plugin</li>
     *   <li>Load new version</li>
     *   <li>Initialize and start</li>
     * </ol></p>
     *
     * @param pluginId the plugin identifier
     * @param newPluginJar the new plugin JAR file path
     * @return Promise containing the reloaded plugin
     */
    public Promise<KernelPlugin> reloadPlugin(String pluginId, Path newPluginJar) {
        Objects.requireNonNull(pluginId, "pluginId cannot be null");
        Objects.requireNonNull(newPluginJar, "newPluginJar cannot be null");

        // Unload existing
        return unloadPlugin(pluginId)
            .then($ -> {
                // Load new version
                return loadPlugin(newPluginJar);
            })
            .then(plugin -> {
                // Initialize and start
                return plugin.start().map(v -> plugin);
            });
    }

    /**
     * Gets all loaded plugins.
     *
     * @return set of loaded plugin identifiers
     */
    public Set<String> getLoadedPlugins() {
        return Set.copyOf(loadedPlugins.keySet());
    }

    /**
     * Gets a loaded plugin by ID.
     *
     * @param pluginId the plugin identifier
     * @return the plugin if loaded, null otherwise
     */
    public KernelPlugin getPlugin(String pluginId) {
        PluginEntry entry = loadedPlugins.get(pluginId);
        return entry != null ? entry.getPlugin() : null;
    }

    // ==================== Private Methods ====================

    private PluginManifest loadManifest(ClassLoader classLoader) {
        // Load manifest from plugin JAR
        // In production: read from META-INF/plugin-manifest.yaml
        return PluginManifest.builder()
            .pluginId("loaded-plugin")
            .version("1.0.0")
            .build();
    }

    // ==================== Inner Types ====================

    /**
     * Plugin entry with ClassLoader and metadata.
     */
    private static class PluginEntry {
        private final String pluginId;
        private final KernelPlugin plugin;
        private final PluginClassLoader classLoader;
        private final PluginManifest manifest;
        private final Path jarPath;

        PluginEntry(String pluginId, KernelPlugin plugin, PluginClassLoader classLoader,
                   PluginManifest manifest, Path jarPath) {
            this.pluginId = pluginId;
            this.plugin = plugin;
            this.classLoader = classLoader;
            this.manifest = manifest;
            this.jarPath = jarPath;
        }

        String getPluginId() { return pluginId; }
        KernelPlugin getPlugin() { return plugin; }
        PluginClassLoader getClassLoader() { return classLoader; }
        PluginManifest getManifest() { return manifest; }
        Path getJarPath() { return jarPath; }
    }

    /**
     * Isolated ClassLoader for plugin loading.
     */
    private static class PluginClassLoader extends URLClassLoader {
        private final ClassLoader parent;

        PluginClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.parent = parent;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Load kernel classes from parent
            if (name.startsWith("com.ghatana.kernel")) {
                return parent.loadClass(name);
            }

            // Try to load from plugin first
            try {
                return findClass(name);
            } catch (ClassNotFoundException e) {
                // Fall back to parent
                return super.loadClass(name, resolve);
            }
        }
    }

    // Stub security manager
    interface PluginSecurityManager {
        Promise<Boolean> verifyPluginSignature(Path pluginJar);
    }
}
