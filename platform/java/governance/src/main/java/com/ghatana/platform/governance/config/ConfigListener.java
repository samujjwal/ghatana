package com.ghatana.platform.governance.config;

/**
 * Listener for configuration changes.
 *
 * @doc.type interface
 * @doc.purpose Callback interface for configuration reload events
 * @doc.layer core
 * @doc.pattern Observer
 */
public interface ConfigListener<T> {
    /** Called when configuration reloads successfully. */
    void onConfigReload(T newConfig);

    /** Called when a configuration reload fails. Default no-op. */
    default void onConfigReloadError(Exception e) {
        // no-op by default
    }
}
