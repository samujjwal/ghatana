package com.ghatana.yappc.plugin;

import java.util.Map;

/**
 * Adapter that bridges the platform {@link com.ghatana.platform.plugin.PluginContext}
 * to the YAPPC-specific {@link PluginContext}.
 *
 * @doc.type class
 * @doc.purpose Bridge platform plugin context to YAPPC plugin context
 * @doc.layer core
 * @doc.pattern Adapter
 */
final class PlatformPluginContextAdapter implements PluginContext {

    private final com.ghatana.platform.plugin.PluginContext platformContext;

    PlatformPluginContextAdapter(com.ghatana.platform.plugin.PluginContext platformContext) {
        this.platformContext = platformContext;
    }

    @Override
    public Map<String, Object> getConfiguration() {
        return platformContext.getConfigMap();
    }

    @Override
    public Object getConfigValue(String key) {
        return platformContext.getConfigMap().get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, T defaultValue) {
        Object value = platformContext.getConfigMap().get(key);
        return value != null ? (T) value : defaultValue;
    }

    @Override
    public String getYappcVersion() {
        return platformContext.getConfig("yappc.version", "2.4.0");
    }

    @Override
    public String getPluginDirectory() {
        return platformContext.getConfig("plugin.directory", "plugins");
    }
}
