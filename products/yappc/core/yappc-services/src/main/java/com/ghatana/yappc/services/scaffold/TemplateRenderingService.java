/**
 * Template Rendering Service
 * 
 * Renders templates with variable substitution.
 * Handles template rendering for scaffold generation.
 * 
 * @doc.type interface
 * @doc.purpose Template rendering
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.scaffold;

import com.ghatana.yappc.api.PackMetadata;

import java.util.Map;

/**
 * Service interface for rendering templates.
 */
public interface TemplateRenderingService {

    /**
     * Renders a template with variable substitution.
     * 
     * @param template The template content to render
     * @param variables The variables to substitute
     * @param templateVariables The template variable definitions
     * @return TemplateRenderingResult containing the rendered content and any errors
     */
    TemplateRenderingResult renderTemplate(String template, Map<String, Object> variables, 
            java.util.List<PackMetadata.TemplateVariable> templateVariables);

    /**
     * Renders all templates in a pack.
     * 
     * @param packMetadata The pack metadata
     * @param variables The variables to substitute
     * @return PackRenderingResult containing rendered files and any errors
     */
    PackRenderingResult renderPack(PackMetadata packMetadata, Map<String, Object> variables);
}

/**
 * Template rendering result.
 */
record TemplateRenderingResult(
    boolean success,
    String renderedContent,
    java.util.List<String> errors,
    java.util.List<String> warnings
) {
    public TemplateRenderingResult {
        if (errors == null) {
            errors = java.util.List.of();
        }
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}

/**
 * Pack rendering result.
 */
record PackRenderingResult(
    boolean success,
    java.util.List<RenderedFile> renderedFiles,
    java.util.List<String> errors,
    java.util.List<String> warnings
) {
    public PackRenderingResult {
        if (renderedFiles == null) {
            renderedFiles = java.util.List.of();
        }
        if (errors == null) {
            errors = java.util.List.of();
        }
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}

/**
 * Rendered file.
 */
record RenderedFile(
    String filePath,
    String renderedContent,
    boolean isTemplate
) {}
