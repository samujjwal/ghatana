/*
 * Copyright (c) 2024 Ghatana, Inc.
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

package com.ghatana.yappc.core.template;

import java.util.Map;

/**
 * Helper options for template helper functions. Week 2, Day 6 deliverable - wrapper around
 * Handlebars Options.
 *
 * @doc.type interface
 * @doc.purpose Helper options for template helper functions. Week 2, Day 6 deliverable - wrapper around
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface HelperOptions {

    /**
     * Get a parameter by index.
     *
     * @param index Parameter index
     * @return Parameter value or null if not found
     */
    Object param(int index);

    /**
     * Get a parameter by index with default value.
     *
     * @param index Parameter index
     * @param defaultValue Default value if parameter not found
     * @param <T> Parameter type
     * @return Parameter value or default value
     */
    <T> T param(int index, T defaultValue);

    /**
     * Get a hash parameter by name.
     *
     * @param name Parameter name
     * @return Parameter value or null if not found
     */
    Object hash(String name);

    /**
     * Get a hash parameter by name with default value.
     *
     * @param name Parameter name
     * @param defaultValue Default value if parameter not found
     * @param <T> Parameter type
     * @return Parameter value or default value
     */
    <T> T hash(String name, T defaultValue);

    /**
     * Get all hash parameters.
     *
     * @return Map of all hash parameters
     */
    Map<String, Object> hash();

    /**
     * Get the template context.
     *
     * @return Current context object
     */
    Object context();
}
