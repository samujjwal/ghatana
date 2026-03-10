/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

/**
 * Listener notified by {@link ConfigWatchService} when a config file changes.
 *
 * <p>Implementations perform the actual hot-reload work (e.g., re-parsing YAML,
 * updating registries, swapping cached objects). The {@link #accepts(String)} method
 * is used to route change events to the correct listener without coupling
 * {@link ConfigWatchService} to individual loader implementations.
 *
 * <h2>Example — policy reload</h2>
 * <pre>{@code
 * public class PolicyReloadListener implements ConfigReloadListener {
 *     @Override public boolean accepts(String relativePath) {
 *         return relativePath.startsWith("policies/") && relativePath.endsWith(".yaml");
 *     }
 *     @Override public void onConfigChanged(ConfigChangeEvent event) {
 *         policyStore.reload(event.absolutePath());
 *         log.info("Policies reloaded from {}", event.relativePath());
 *     }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose SPI for reacting to hot-config-file changes detected by ConfigWatchService
 * @doc.layer product
 * @doc.pattern SPI, Observer
 */
public interface ConfigReloadListener {

    /**
     * Returns {@code true} if this listener is interested in changes to the given relative path.
     *
     * <p>Called by {@link ConfigWatchService} before {@link #onConfigChanged(ConfigChangeEvent)}
     * to avoid unnecessary listener invocations.
     *
     * @param relativePath file path relative to the config root, forward-slash separated
     *                     (e.g., {@code "policies/security.yaml"})
     * @return {@code true} if this listener should process the change
     */
    boolean accepts(String relativePath);

    /**
     * Performs the hot-reload action for the changed config file.
     *
     * <p>Called on the {@link ConfigWatchService} watch thread. Implementations MUST be
     * thread-safe. Exceptions thrown here are caught and logged — they do NOT propagate to
     * other listeners.
     *
     * @param event the change event (never {@code null})
     */
    void onConfigChanged(ConfigChangeEvent event);
}
