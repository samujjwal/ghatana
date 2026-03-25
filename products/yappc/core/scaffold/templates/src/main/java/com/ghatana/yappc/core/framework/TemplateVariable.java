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

package com.ghatana.yappc.core.framework;

/**
 * Template variable definition.
 * 
 * @doc.type record
 * @doc.purpose Define template variable metadata
 * @doc.layer platform
 * @doc.pattern Value Object
 * 
 * @param name Variable name
 * @param type Variable type (string, boolean, number, array, object)
 * @param description Variable description
 * @param required Whether variable is required
 * @param defaultValue Default value if not provided
 */
public record TemplateVariable(
    String name,
    String type,
    String description,
    boolean required,
    Object defaultValue
) {
    /**
     * Create required variable.
     */
    public static TemplateVariable required(String name, String type, String description) {
        return new TemplateVariable(name, type, description, true, null);
    }

    /**
     * Create optional variable with default.
     */
    public static TemplateVariable optional(String name, String type, String description, Object defaultValue) {
        return new TemplateVariable(name, type, description, false, defaultValue);
    }
}
