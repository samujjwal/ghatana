/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.framework.core.plugin;

import com.ghatana.yappc.framework.api.plugin.PluginContext;
import com.ghatana.yappc.framework.api.plugin.YappcPlugin;
import com.ghatana.yappc.framework.api.services.*;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**

 * Default implementation of PluginContextFactory.

 *

 * <p>Strategic restructuring - Plugin Architecture Implementation Creates plugin contexts with

 * controlled access to framework services.

 *

 * @doc.type class

 * @doc.purpose Produce scoped PluginContext instances with access to telemetry and configuration.

 * @doc.layer product

 * @doc.pattern Factory

 */

public class DefaultPluginContextFactory implements PluginContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPluginContextFactory.class);

    private final TelemetryCollector telemetryCollector;
    private final Properties frameworkConfiguration;

    public DefaultPluginContextFactory(
            TelemetryCollector telemetryCollector, Properties frameworkConfiguration) {
        this.telemetryCollector = telemetryCollector;
        this.frameworkConfiguration = new Properties(frameworkConfiguration);
    }

    @Override
    public PluginContext createContext(YappcPlugin plugin) {
        logger.debug("Creating context for plugin: {}", plugin.getName());
        return new DefaultPluginContext(plugin, telemetryCollector, frameworkConfiguration);
    }

    /**
 * Default implementation of PluginContext. */
    private static class DefaultPluginContext implements PluginContext {

        private final YappcPlugin plugin;
        private final TelemetryCollector telemetryCollector;
        private final Properties configuration;
        private final Logger logger;

        public DefaultPluginContext(
                YappcPlugin plugin,
                TelemetryCollector telemetryCollector,
                Properties configuration) {
            this.plugin = plugin;
            this.telemetryCollector = telemetryCollector;
            this.configuration = new Properties(configuration);
            this.logger = LoggerFactory.getLogger("plugin." + plugin.getName());
        }

        @Override
        public com.ghatana.yappc.framework.api.domain.ProjectDescriptor getCurrentProject() {
            // NOTE: Project context is not yet wired — returns null until ProjectService DI is added.
            logger.debug("getCurrentProject() — project context not yet wired for plugin '{}'", plugin.getName());
            return null;
        }

        @Override
        public java.util.Map<String, Object> getPluginConfiguration() {
            java.util.Map<String, Object> config = new java.util.HashMap<>();
            String prefix = "plugin." + plugin.getName() + ".";

            for (String key : configuration.stringPropertyNames()) {
                if (key.startsWith(prefix)) {
                    String configKey = key.substring(prefix.length());
                    config.put(configKey, configuration.getProperty(key));
                }
            }

            return config;
        }

        @Override
        public <T> T getConfigurationValue(String key, Class<T> type, T defaultValue) {
            String pluginKey = "plugin." + plugin.getName() + "." + key;
            String value = configuration.getProperty(pluginKey, configuration.getProperty(key));

            if (value == null) {
                return defaultValue;
            }

            try {
                if (type == String.class) {
                    return type.cast(value);
                } else if (type == Boolean.class || type == boolean.class) {
                    return type.cast(Boolean.parseBoolean(value));
                } else if (type == Integer.class || type == int.class) {
                    return type.cast(Integer.parseInt(value));
                } else if (type == Long.class || type == long.class) {
                    return type.cast(Long.parseLong(value));
                } else if (type == Double.class || type == double.class) {
                    return type.cast(Double.parseDouble(value));
                }
            } catch (Exception e) {
                logger.warn(
                        "Failed to convert configuration value '{}' to type {}, using default",
                        value,
                        type.getSimpleName());
            }

            return defaultValue;
        }

        @Override
        public String getFrameworkVersion() {
            return configuration.getProperty("yappc.framework.version", "1.0.0");
        }

        @Override
        public <T> T getService(Class<T> serviceType) {
            // NOTE: Service registry supports TelemetryCollector; additional services added via DI.
            if (serviceType == TelemetryCollector.class) {
                return serviceType.cast(telemetryCollector);
            }
            return null;
        }

        @Override
        public boolean hasService(Class<?> serviceType) {
            return serviceType == TelemetryCollector.class;
        }

        @Override
        public void log(String level, String message, Object... args) {
            switch (level.toUpperCase()) {
                case "ERROR":
                    logger.error(message, args);
                    break;
                case "WARN":
                    logger.warn(message, args);
                    break;
                case "INFO":
                    logger.info(message, args);
                    break;
                case "DEBUG":
                    logger.debug(message, args);
                    break;
                case "TRACE":
                    logger.trace(message, args);
                    break;
                default:
                    logger.info(message, args);
                    break;
            }
        }
    }
}
