package com.ghatana.appplatform.config.notification;

/**
 * Callback interface for config changes delivered via PostgreSQL LISTEN/NOTIFY.
 *
 * <p>Implementations are registered with a {@link ConfigChangeNotifier} and invoked
 * on the notifier's polling thread. Implementations must be thread-safe and should
 * return quickly — heavy reloading work should be dispatched to a separate executor.
 *
 * @doc.type interface
 * @doc.purpose Callback for hot-reload config change notifications
 * @doc.layer product
 * @doc.pattern Port
 */
@FunctionalInterface
public interface ConfigChangeListener {

    /**
     * Called when a config entry has been inserted or updated.
     *
     * @param namespace  config namespace that changed
     * @param key        config key within that namespace
     * @param level      hierarchy level that changed (e.g. "TENANT")
     * @param levelId    scope identifier for the level
     */
    void onConfigChange(String namespace, String key, String level, String levelId);
}
