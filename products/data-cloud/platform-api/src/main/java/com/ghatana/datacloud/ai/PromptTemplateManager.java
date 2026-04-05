/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manager for prompt templates.
 *
 * @doc.type interface
 * @doc.purpose Prompt template management
 * @doc.layer product
 * @doc.pattern Template Manager
 */
public interface PromptTemplateManager {

    /**
     * Register a prompt template.
     *
     * @param template template definition
     * @return promise of registered template
     */
    Promise<PromptTemplate> registerTemplate(PromptTemplate template);

    /**
     * Get template by ID.
     *
     * @param templateId template identifier
     * @return promise of template if found
     */
    Promise<Optional<PromptTemplate>> getTemplate(String templateId);

    /**
     * List templates for tenant.
     *
     * @param tenantId tenant identifier
     * @param category optional category filter
     * @return promise of template list
     */
    Promise<List<PromptTemplate>> listTemplates(String tenantId, String category);

    /**
     * Update template.
     *
     * @param templateId template identifier
     * @param template updated template
     * @return promise of updated template
     */
    Promise<PromptTemplate> updateTemplate(String templateId, PromptTemplate template);

    /**
     * Delete template.
     *
     * @param templateId template identifier
     * @return promise completing when deleted
     */
    Promise<Void> deleteTemplate(String templateId);

    /**
     * Render template with variables.
     *
     * @param templateId template identifier
     * @param variables variable values
     * @return promise of rendered prompt
     */
    Promise<String> render(String templateId, Map<String, Object> variables);

    /**
     * Validate template syntax.
     *
     * @param template template to validate
     * @return promise of validation result
     */
    Promise<ValidationResult> validate(PromptTemplate template);

    /**
     * Get template categories.
     *
     * @param tenantId tenant identifier
     * @return promise of categories
     */
    Promise<List<String>> getCategories(String tenantId);

    /**
     * Clone template.
     *
     * @param sourceTemplateId source template
     * @param newTemplateId new template ID
     * @return promise of cloned template
     */
    Promise<PromptTemplate> cloneTemplate(String sourceTemplateId, String newTemplateId);

    /**
     * Prompt template.
     */
    record PromptTemplate(
        String id,
        String name,
        String description,
        String tenantId,
        String category,
        String content,
        List<String> variables,
        String systemPrompt,
        Map<String, Object> defaultParameters,
        int version,
        boolean active
    ) {
        /**
         * Check if template has variable.
         */
        public boolean hasVariable(String variable) {
            return variables != null && variables.contains(variable);
        }
    }

    /**
     * Validation result.
     */
    record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        List<String> undefinedVariables,
        List<String> unusedVariables
    ) {}
}
