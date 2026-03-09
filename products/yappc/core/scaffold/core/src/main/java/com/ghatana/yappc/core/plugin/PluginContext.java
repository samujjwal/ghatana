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

package com.ghatana.yappc.core.plugin;

import java.nio.file.Path;
import java.util.Map;

/**
 * Context provided to plugins during initialization.
 *
 * @doc.type record
 * @doc.purpose Plugin initialization context
 * @doc.layer platform
 * @doc.pattern Context Object
 */
public record PluginContext(
        Path workspacePath,
        Path packsPath,
        Map<String, String> config,
        PluginEventBus eventBus,
        PluginSandbox sandbox) {

    /**
     * Gets a configuration value.
     *
     * @param key configuration key
     * @return configuration value or null if not found
     */
    public String getConfig(String key) {
        return config.get(key);
    }

    /**
     * Gets a configuration value with a default.
     *
     * @param key          configuration key
     * @param defaultValue default value if key not found
     * @return configuration value or default
     */
    public String getConfig(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }

    /**
     * Checks if a configuration key exists.
     *
     * @param key configuration key
     * @return true if key exists
     */
    public boolean hasConfig(String key) {
        return config.containsKey(key);
    }
}
