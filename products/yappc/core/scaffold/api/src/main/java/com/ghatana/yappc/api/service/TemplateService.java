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

package com.ghatana.yappc.api.service;

import com.ghatana.yappc.api.model.TemplateInfo;
import com.ghatana.yappc.api.model.RenderResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Service for template rendering and management.
 *
 * @doc.type interface
 * @doc.purpose Template rendering operations
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface TemplateService {

    /**
     * Render a template string with variables.
     *
     * @param template The template string
     * @param variables The variables to substitute
     * @return The rendered string
     */
    String render(String template, Map<String, Object> variables);

    /**
     * Render a template file with variables.
     *
     * @param templatePath Path to the template file
     * @param variables The variables to substitute
     * @return The rendered content
     */
    String renderFile(Path templatePath, Map<String, Object> variables);

    /**
     * Render a template to a file.
     *
     * @param templatePath Path to the template file
     * @param outputPath Path for the output file
     * @param variables The variables to substitute
     * @return The render result
     */
    RenderResult renderToFile(Path templatePath, Path outputPath, Map<String, Object> variables);

    /**
     * List all templates in a pack.
     *
     * @param packName The pack name
     * @return List of template info
     */
    List<TemplateInfo> listTemplates(String packName);

    /**
     * Get required variables for a template.
     *
     * @param templatePath Path to the template
     * @return List of variable names
     */
    List<String> getRequiredVariables(Path templatePath);

    /**
     * Get required variables for a pack.
     *
     * @param packName The pack name
     * @return List of variable names
     */
    List<String> getPackRequiredVariables(String packName);

    /**
     * Validate template syntax.
     *
     * @param templateContent The template content
     * @return true if syntax is valid
     */
    boolean validateSyntax(String templateContent);

    /**
     * Get all available template helpers.
     *
     * @return List of helper names
     */
    List<String> getAvailableHelpers();

    /**
     * Register a custom helper function.
     *
     * @param name The helper name
     * @param helper The helper function
     */
    void registerHelper(String name, TemplateHelper helper);

    /**
     * Functional interface for custom template helpers.
     */
    @FunctionalInterface
    interface TemplateHelper {
        /**
         * Process the input value.
         *
         * @param input The input value
         * @return The processed value
         */
        String apply(String input);
    }
}
