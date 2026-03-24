/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.hotreload;

import com.ghatana.yappc.framework.core.plugin.sandbox.IsolatingPluginSandbox;
import com.ghatana.yappc.framework.core.plugin.sandbox.PluginDescriptor;
import com.ghatana.yappc.framework.core.plugin.sandbox.PluginLoadException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugin registry with hot-reload support.
 *
 * <p>Plugins are registered via {@link #register(PluginDescriptor, Class)} and can be
 * reloaded at runtime via {@link #reload(String)} without a service restart. During a
 * reload, concurrent reads use the old instance until the new one is ready; a write lock
 * is held only for the swap operation.
 *
 * <p>Reload sequence (10.3.1):
 * <ol>
 *   <li>Acquire write lock.</li>
 *   <li>Close old {@link java.net.URLClassLoader} (allows GC to reclaim old class).</li>
 *   <li>Load fresh plugin instance via {@link IsolatingPluginSandbox}.</li>
 *   <li>Register fresh instance and release write lock.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Plugin registry with hot-reload support (no service restart needed)
 * @doc.layer product
 * @doc.pattern Registry
 */
public class HotReloadPluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(HotReloadPluginRegistry.class);

    private final IsolatingPluginSandbox sandbox;

    /** Stores active plugin entry per pluginId. */
    private final ConcurrentHashMap<String, PluginEntry<?>> registry = new ConcurrentHashMap<>();

    /** Guards reload operations to prevent concurrent half-swapped state. */
    private final ReentrantReadWriteLock reloadLock = new ReentrantReadWriteLock();

    /**
     * @param sandbox sandbox used to (re-)load plugins in isolated ClassLoaders
     */
    public HotReloadPluginRegistry(IsolatingPluginSandbox sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * Registers a plugin by loading it through {@link IsolatingPluginSandbox}.
     *
     * @param <T>        plugin contract type
     * @param descriptor plugin descriptor
     * @param contract   plugin interface
     * @return the loaded, permission-enforcing proxy
     * @throws PluginLoadException if loading fails
     */
    public <T> T register(PluginDescriptor descriptor, Class<T> contract) throws PluginLoadException {
        reloadLock.writeLock().lock();
        try {
            T instance = sandbox.loadPlugin(descriptor, contract);
            registry.put(descriptor.id(), new PluginEntry<>(descriptor, contract, instance));
            log.info("Registered plugin {}.", descriptor.logId());
            return instance;
        } finally {
            reloadLock.writeLock().unlock();
        }
    }

    /**
     * Hot-reloads an already-registered plugin without service restart (10.3.1).
     *
     * <p>The old ClassLoader is replaced with a fresh one after the new instance is
     * successfully loaded. If loading fails the existing instance remains active.
     *
     * @param pluginId id of the plugin to reload
     * @throws IllegalArgumentException if {@code pluginId} is not registered
     * @throws PluginLoadException      if reloading fails
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void reload(String pluginId) throws PluginLoadException {
        PluginEntry<?> existing;
        reloadLock.readLock().lock();
        try {
            existing = registry.get(pluginId);
        } finally {
            reloadLock.readLock().unlock();
        }

        if (existing == null) {
            throw new IllegalArgumentException("Plugin not registered: " + pluginId);
        }

        log.info("Hot-reloading plugin {}...", pluginId);
        // Load new instance outside the write lock to minimise contention
        Object newInstance = sandbox.loadPlugin(existing.descriptor, (Class) existing.contract);

        reloadLock.writeLock().lock();
        try {
            registry.put(pluginId, new PluginEntry(existing.descriptor, existing.contract, newInstance));
            log.info("Plugin {} hot-reloaded successfully.", existing.descriptor.logId());
        } finally {
            reloadLock.writeLock().unlock();
        }
    }

    /**
     * Returns the active plugin instance for the given id.
     *
     * @param <T>      plugin contract type
     * @param pluginId plugin id
     * @param contract expected contract class (for type safety)
     * @return active instance, or empty if not registered
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String pluginId, Class<T> contract) {
        reloadLock.readLock().lock();
        try {
            PluginEntry<?> entry = registry.get(pluginId);
            if (entry == null) {
                return Optional.empty();
            }
            return Optional.of(contract.cast(entry.instance));
        } finally {
            reloadLock.readLock().unlock();
        }
    }

    /** Returns {@code true} if a plugin with {@code pluginId} is currently registered. */
    public boolean isRegistered(String pluginId) {
        return registry.containsKey(pluginId);
    }

    /** Returns the number of currently registered plugins. */
    public int size() {
        return registry.size();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static final class PluginEntry<T> {
        final PluginDescriptor descriptor;
        final Class<T> contract;
        final T instance;

        PluginEntry(PluginDescriptor descriptor, Class<T> contract, T instance) {
            this.descriptor = descriptor;
            this.contract = contract;
            this.instance = instance;
        }
    }
}
