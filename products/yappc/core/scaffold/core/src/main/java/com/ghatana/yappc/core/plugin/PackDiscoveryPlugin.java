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

import com.ghatana.yappc.core.pack.PackMetadata;
import java.nio.file.Path;
import java.util.List;

/**
 * Plugin interface for discovering packs from various sources.
 *
 * @doc.type interface
 * @doc.purpose Pack discovery plugin
 * @doc.layer platform
 * @doc.pattern Plugin SPI
 */
public interface PackDiscoveryPlugin extends YappcPlugin {

    /**
     * Discovers packs from the configured source.
     *
     * @return list of discovered pack metadata
     * @throws PluginException if discovery fails
     */
    List<PackMetadata> discoverPacks() throws PluginException;

    /**
     * Discovers packs from a specific path.
     *
     * @param path path to search
     * @return list of discovered pack metadata
     * @throws PluginException if discovery fails
     */
    List<PackMetadata> discoverPacks(Path path) throws PluginException;

    /**
     * Checks if this plugin can handle the given source.
     *
     * @param source source identifier (filesystem path, URL, etc.)
     * @return true if this plugin can handle the source
     */
    boolean canHandle(String source);
}
