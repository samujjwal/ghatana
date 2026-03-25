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

import java.io.IOException;

/**
 * Interface for custom Handlebars template helpers. Week 2, Day 6 deliverable - custom helpers for
 * YAPPC templates.
 */
@FunctionalInterface
/**
 * TemplateHelper component within the YAPPC platform.
 *
 * @doc.type interface
 * @doc.purpose TemplateHelper component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface TemplateHelper {

    /**
     * Apply the template helper logic.
     *
     * @param context The current template context
     * @param options Helper options (will be mapped from Handlebars Options)
     * @return The helper output
     * @throws IOException If helper processing fails
     */
    CharSequence apply(Object context, HelperOptions options) throws IOException;
}
