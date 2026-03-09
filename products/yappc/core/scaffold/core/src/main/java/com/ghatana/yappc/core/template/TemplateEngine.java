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

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.yappc.core.error.TemplateException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Template engine interface for YAPPC scaffolding. Week 2, Day 6 deliverable -
 * Handlebars integration with custom helpers.
 *
 * @doc.type interface
 * @doc.purpose Template engine interface for YAPPC scaffolding. Week 2, Day 6
 * deliverable - Handlebars
 * @doc.layer platform
 * @doc.pattern Component
 */
public interface TemplateEngine {

    /**
     * Render a template with the given context.
     *
     * @param templateContent The template content to render
     * @param context The data context for template rendering
     * @return The rendered output
     * @throws TemplateException If rendering fails
     */
    String render(String templateContent, Map<String, Object> context) throws TemplateException;

    /**
     * Render a template file with the given context.
     *
     * @param templatePath Path to the template file
     * @param context The data context for template rendering
     * @return The rendered output
     * @throws TemplateException If rendering fails
     */
    String renderFile(Path templatePath, Map<String, Object> context) throws TemplateException;

    /**
     * Render a template with JSON context.
     *
     * @param templateContent The template content to render
     * @param jsonContext JSON node containing template context
     * @return The rendered output
     * @throws TemplateException If rendering fails
     */
    String render(String templateContent, JsonNode jsonContext) throws TemplateException;

    /**
     * Register a custom helper for template rendering.
     *
     * @param name The helper name
     * @param helper The helper implementation
     */
    void registerHelper(String name, TemplateHelper helper);

    /**
     * Create a template merger for handling 3-way merge operations.
     *
     * @return A new template merger instance
     */
    TemplateMerger createMerger();
}
