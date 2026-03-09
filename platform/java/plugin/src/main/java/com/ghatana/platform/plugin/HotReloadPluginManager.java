package com.ghatana.platform.plugin;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Hot-reloadable plugin manager that watches a plugin directory for JAR changes
 * and automatically loads/unloads/reloads plugins without application restart.
 *
 * <p>Uses {@link WatchService} for file-system events and isolated {@link URLClassLoader}
 * instances for each plugin to enable clean unload/reload cycles.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * HotReloadPluginManager manager = new HotReloadPluginManager(Path.of("plugins"));
 * manager.start();
 * // Plugins dropped into plugins/ are auto-loaded
 * // Updated JARs are auto-reloaded
 * // Deleted JARs are auto-unloaded
 * manager.stop();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Hot-reloadable plugin lifecycle manager
 * @doc.layer platform
 * @doc.pattern Observer, Factory
 */
public class HotReloadPluginManager {
    private static final Logger log = LoggerFactory.getLogger(HotReloadPluginManager.class);

    private final Path pluginDir;
    private final ConcurrentHashMap<String, LoadedPlugin> plugins = new ConcurrentHashMap<>();
    private final List<PluginLifecycleListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private Thread watchThread;

    /**
     * Creates a new manager watching the given directory.
     *
     * @param pluginDir Directory to watch for plugin JARs
     */
    public HotReloadPluginManager(@NotNull Path pluginDir) {
        this.pluginDir = Objects.requireNonNull(pluginDir);
    }

    /**
     * Starts the plugin manager: loads existing plugins and begins watching for changes.
     */
    public void start() throws IOException {
        if (running) return;
        running = true;

        // Ensure directory exists
        Files.createDirectories(pluginDir);

        // Load existing plugins
        try (var stream = Files.list(pluginDir)) {
            stream.filter(p -> p.toString().endsWith(".jar"))
                  .forEach(this::loadPlugin);
        }

        // Start file watcher in background
        watchThread = Thread.startVirtualThread(this::watchLoop);
        log.info("HotReloadPluginManager started, watching: {}", pluginDir);
    }

    /**
     * Stops the manager and unloads all plugins.
     */
    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        plugins.values().forEach(this::unloadPlugin);
        plugins.clear();
        log.info("HotReloadPluginManager stopped");
    }

    /**
     * Registers a lifecycle listener.
     */
    public void addListener(@NotNull PluginLifecycleListener listener) {
        listeners.add(listener);
    }

    /**
     * Returns an unmodifiable view of loaded plugin IDs.
     */
    public Set<String> getLoadedPluginIds() {
        return Collections.unmodifiableSet(plugins.keySet());
    }

    /**
     * Gets a loaded plugin by ID.
     */
    @Nullable
    public LoadedPlugin getPlugin(@NotNull String pluginId) {
        return plugins.get(pluginId);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private void loadPlugin(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        try {
            log.info("Loading plugin: {}", fileName);

            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarUrl},
                getClass().getClassLoader()
            );

            ServiceLoader<PluginDescriptor> descriptors = ServiceLoader.load(
                PluginDescriptor.class, classLoader);

            PluginDescriptor descriptor = descriptors.findFirst().orElse(null);
            String pluginId = descriptor != null ? descriptor.id() : fileName;

            LoadedPlugin loaded = new LoadedPlugin(pluginId, jarPath, classLoader, descriptor);
            LoadedPlugin previous = plugins.put(pluginId, loaded);

            if (previous != null) {
                unloadPlugin(previous);
                log.info("Reloaded plugin: {}", pluginId);
                listeners.forEach(l -> l.onReloaded(pluginId));
            } else {
                log.info("Loaded plugin: {}", pluginId);
                listeners.forEach(l -> l.onLoaded(pluginId));
            }
        } catch (Exception e) {
            log.error("Failed to load plugin: {}", fileName, e);
        }
    }

    private void unloadPlugin(LoadedPlugin plugin) {
        try {
            log.info("Unloading plugin: {}", plugin.id());
            plugin.classLoader().close();
            listeners.forEach(l -> l.onUnloaded(plugin.id()));
        } catch (IOException e) {
            log.error("Error unloading plugin: {}", plugin.id(), e);
        }
    }

    private void removePlugin(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        plugins.entrySet().removeIf(entry -> {
            if (entry.getValue().jarPath().equals(jarPath)) {
                unloadPlugin(entry.getValue());
                return true;
            }
            return false;
        });
    }

    private void watchLoop() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            pluginDir.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

            while (running) {
                WatchKey key = watcher.poll(500, TimeUnit.MILLISECONDS);
                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = pluginDir.resolve((Path) event.context());
                    if (!changed.toString().endsWith(".jar")) continue;

                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                        kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        // Brief delay to let file writing finish
                        Thread.sleep(200);
                        loadPlugin(changed);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        removePlugin(changed);
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("Plugin watch service failed", e);
        }
    }

    // ── Records / Interfaces ────────────────────────────────────────────

    /**
     * A loaded plugin with its classloader for lifecycle management.
     */
    public record LoadedPlugin(
        String id,
        Path jarPath,
        URLClassLoader classLoader,
        @Nullable PluginDescriptor descriptor
    ) {}

    /**
     * SPI interface for plugin self-description.
     */
    public interface PluginDescriptor {
        String id();
        String version();
        String description();
    }

    /**
     * Listener for plugin lifecycle events.
     */
    public interface PluginLifecycleListener {
        default void onLoaded(String pluginId) {}
        default void onUnloaded(String pluginId) {}
        default void onReloaded(String pluginId) {}
    }
}
