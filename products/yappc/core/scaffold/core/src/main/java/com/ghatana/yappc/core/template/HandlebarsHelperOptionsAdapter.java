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
 * Adapter that wraps Handlebars Options to provide HelperOptions interface. Week 2, Day 6
 * deliverable.
 *
 * @doc.type class
 * @doc.purpose Adapter that wraps Handlebars Options to provide HelperOptions interface. Week 2, Day 6
 * @doc.layer platform
 * @doc.pattern Component
 */
public class HandlebarsHelperOptionsAdapter implements HelperOptions {

    // For now, we'll create a simple implementation that doesn't depend on Handlebars
    // This will be updated once Handlebars dependency is resolved
    private final Map<String, Object> hash;
    private final Object[] params;
    private final Object context;

    public HandlebarsHelperOptionsAdapter(Object context) {
        this.context = context;
        this.hash = Map.of();
        this.params = new Object[0];
    }

    public HandlebarsHelperOptionsAdapter(
            Object context, Map<String, Object> hash, Object... params) {
        this.context = context;
        this.hash = hash != null ? hash : Map.of();
        this.params = params != null ? params : new Object[0];
    }

    @Override
    public Object param(int index) {
        if (index < 0 || index >= params.length) {
            return null;
        }
        return params[index];
    }

    @Override
    public <T> T param(int index, T defaultValue) {
        Object value = param(index);
        if (value == null) {
            return defaultValue;
        }
        try {
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public Object hash(String name) {
        return hash.get(name);
    }

    @Override
    public <T> T hash(String name, T defaultValue) {
        Object value = hash.get(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public Map<String, Object> hash() {
        return Map.copyOf(hash);
    }

    @Override
    public Object context() {
        return context;
    }
}
