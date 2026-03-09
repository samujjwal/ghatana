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

/**

 * Factory for creating plugin contexts.

 *

 * <p>Strategic restructuring - Plugin Architecture Implementation Provides controlled access to

 * framework services for plugins.

 *

 * @doc.type interface

 * @doc.purpose Allow the framework to supply plugins with controlled access to shared services.

 * @doc.layer product

 * @doc.pattern Factory

 */

public interface PluginContextFactory {

    /**
     * Create a plugin context for the specified plugin. The context provides controlled access to
     * framework services.
     *
     * @param plugin The plugin for which to create a context
     * @return A plugin context instance
     */
    PluginContext createContext(YappcPlugin plugin);
}
