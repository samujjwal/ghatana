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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Framework template definition.
 * 
 * @doc.type record
 * @doc.purpose Define framework-specific template
 * @doc.layer platform
 * @doc.pattern Value Object
 * 
 * @param id Unique template identifier
 * @param framework Framework name (e.g., "react", "spring-boot", "express")
 * @param version Framework version (use "*" for any version)
 * @param category Template category (e.g., "components", "services", "controllers")
 * @param name Template name
 * @param description Template description
 * @param templatePath Path to template file
 * @param variables Template variable definitions
 * @param dependencies Template dependencies
 * @param metadata Additional template metadata
 */
public record FrameworkTemplate(
    String id,
    String framework,
    String version,
    String category,
    String name,
    String description,
    Path templatePath,
    Map<String, TemplateVariable> variables,
    List<String> dependencies,
    TemplateMetadata metadata
) {
    /**
     * Check if template matches framework and version.
     */
    public boolean matches(String framework, String version) {
        if (!this.framework.equals(framework)) {
            return false;
        }
        
        if ("*".equals(this.version) || "*".equals(version)) {
            return true;
        }
        
        return this.version.equals(version);
    }

    /**
     * Get template content.
     */
    public String getContent() throws java.io.IOException {
        return java.nio.file.Files.readString(templatePath);
    }
}
