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

import java.util.Map;
import java.util.function.Function;

/**
 * Plugin interface for registering custom template helpers.
 *
 * @doc.type interface
 * @doc.purpose Template helper plugin
 * @doc.layer platform
 * @doc.pattern Plugin SPI
 */
public interface TemplateHelperPlugin extends YappcPlugin {

    /**
     * Returns the template helpers provided by this plugin.
     *
     * @return map of helper name to helper function
     */
    Map<String, Function<String, String>> getHelpers();

    /**
     * Returns the description for each helper.
     *
     * @return map of helper name to description
     */
    Map<String, String> getHelperDescriptions();
}
