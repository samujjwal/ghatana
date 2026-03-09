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

package com.ghatana.yappc.core.integration;

import com.ghatana.yappc.core.error.TemplateException;
import com.ghatana.yappc.core.pack.PackMetadata;
import com.ghatana.yappc.core.template.TemplateEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration template engine for generating cross-module integration code.
 * 
 * Supports integration types:
 * - API_CLIENT: Generate API client code for frontend-backend communication
 * - DATASOURCE: Generate database connection and repository code
 * - EVENT_STREAM: Generate event streaming integration
 * - SHARED_TYPES: Generate shared type definitions
 * - SERVICE_MESH: Generate service mesh configuration
 * 
 * @doc.type class
 * @doc.purpose Integration template engine for cross-module code generation
 * @doc.layer platform
 * @doc.pattern Engine/Generator
 */
public class IntegrationTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(IntegrationTemplateEngine.class);

    private final TemplateEngine templateEngine;
    private final Map<PackMetadata.IntegrationDefinition.IntegrationType, IntegrationTemplateSet> templateSets;
    private final Path integrationTemplatesPath;

    public IntegrationTemplateEngine(TemplateEngine templateEngine, Path integrationTemplatesPath) {
        this.templateEngine = templateEngine;
        this.integrationTemplatesPath = integrationTemplatesPath;
        this.templateSets = new HashMap<>();
        
        // Load integration template sets
        loadIntegrationTemplateSets();
    }

    /**
     * Generate integration code between modules.
     * 
     * @param integration integration definition
     * @param fromModule source module metadata
     * @param toModule target module metadata
     * @param variables integration variables
     * @return integration generation result
     * @throws IntegrationException if generation fails
     */
    public IntegrationResult generateIntegration(
            PackMetadata.IntegrationDefinition integration,
            ModuleMetadata fromModule,
            ModuleMetadata toModule,
            Map<String, Object> variables) throws IntegrationException {
        
        log.info("Generating integration '{}' from '{}' to '{}'", 
            integration.id(), integration.from(), integration.to());

        try {
            IntegrationTemplateSet templateSet = templateSets.get(integration.type());
            if (templateSet == null) {
                throw new IntegrationException("No template set found for integration type: " + integration.type());
            }

            // Prepare integration context
            Map<String, Object> context = prepareIntegrationContext(
                integration, fromModule, toModule, variables);

            // Generate integration files
            List<GeneratedFile> generatedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (IntegrationTemplate template : templateSet.templates()) {
                try {
                    GeneratedFile file = generateIntegrationFile(template, context);
                    generatedFiles.add(file);
                } catch (Exception e) {
                    String error = "Failed to generate " + template.name() + ": " + e.getMessage();
                    errors.add(error);
                    log.error(error, e);
                }
            }

            return new IntegrationResult(
                integration.id(),
                integration.type(),
                errors.isEmpty(),
                generatedFiles,
                errors
            );

        } catch (Exception e) {
            throw new IntegrationException("Integration generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Prepare integration context with module metadata and variables.
     */
    private Map<String, Object> prepareIntegrationContext(
            PackMetadata.IntegrationDefinition integration,
            ModuleMetadata fromModule,
            ModuleMetadata toModule,
            Map<String, Object> variables) {
        
        Map<String, Object> context = new HashMap<>(variables);
        
        // Add integration metadata
        context.put("integration", Map.of(
            "id", integration.id(),
            "name", integration.name(),
            "type", integration.type().name()
        ));
        
        // Add source module metadata
        context.put("from", Map.of(
            "id", fromModule.id(),
            "name", fromModule.name(),
            "type", fromModule.type(),
            "language", fromModule.language(),
            "framework", fromModule.framework(),
            "outputs", fromModule.outputs()
        ));
        
        // Add target module metadata
        context.put("to", Map.of(
            "id", toModule.id(),
            "name", toModule.name(),
            "type", toModule.type(),
            "language", toModule.language(),
            "framework", toModule.framework(),
            "outputs", toModule.outputs()
        ));
        
        // Add integration-specific variables
        if (integration.variables() != null) {
            context.putAll(integration.variables());
        }
        
        return context;
    }

    /**
     * Generate a single integration file from template.
     */
    private GeneratedFile generateIntegrationFile(
            IntegrationTemplate template,
            Map<String, Object> context) throws TemplateException, IOException {
        
        // Load template content
        Path templatePath = integrationTemplatesPath.resolve(template.templatePath());
        String templateContent = Files.readString(templatePath);
        
        // Render template
        String content = templateEngine.render(templateContent, context);
        
        // Resolve target path
        String targetPath = templateEngine.render(template.targetPath(), context);
        
        return new GeneratedFile(
            template.name(),
            targetPath,
            content,
            template.executable()
        );
    }

    /**
     * Load integration template sets from configuration.
     */
    private void loadIntegrationTemplateSets() {
        // API Client integration templates
        templateSets.put(PackMetadata.IntegrationDefinition.IntegrationType.API_CLIENT, new IntegrationTemplateSet(
            PackMetadata.IntegrationDefinition.IntegrationType.API_CLIENT,
            List.of(
                new IntegrationTemplate(
                    "api-client",
                    "api-client/client.hbs",
                    "{{from.path}}/src/api/{{to.id}}-client.{{from.extension}}",
                    false
                ),
                new IntegrationTemplate(
                    "type-definitions",
                    "api-client/types.hbs",
                    "{{from.path}}/src/types/{{to.id}}.{{from.extension}}",
                    false
                ),
                new IntegrationTemplate(
                    "environment-config",
                    "api-client/env.hbs",
                    "{{from.path}}/.env.example",
                    false
                )
            )
        ));

        // Datasource integration templates
        templateSets.put(PackMetadata.IntegrationDefinition.IntegrationType.DATASOURCE, new IntegrationTemplateSet(
            PackMetadata.IntegrationDefinition.IntegrationType.DATASOURCE,
            List.of(
                new IntegrationTemplate(
                    "datasource-config",
                    "datasource/config.hbs",
                    "{{from.path}}/config/database.{{from.extension}}",
                    false
                ),
                new IntegrationTemplate(
                    "repository-interfaces",
                    "datasource/repositories.hbs",
                    "{{from.path}}/internal/repository/interfaces.{{from.extension}}",
                    false
                ),
                new IntegrationTemplate(
                    "migration-scripts",
                    "datasource/migrations.hbs",
                    "{{to.path}}/migrations/{{timestamp}}_init.sql",
                    false
                )
            )
        ));

        // Event Stream integration templates
        templateSets.put(PackMetadata.IntegrationDefinition.IntegrationType.EVENT_STREAM, new IntegrationTemplateSet(
            PackMetadata.IntegrationDefinition.IntegrationType.EVENT_STREAM,
            List.of(
                new IntegrationTemplate(
                    "event-producer",
                    "event-stream/producer.hbs",
                    "{{from.path}}/events/producer.{{from.extension}}",
                    false
                ),
                new IntegrationTemplate(
                    "event-consumer",
                    "event-stream/consumer.hbs",
                    "{{to.path}}/events/consumer.{{to.extension}}",
                    false
                ),
                new IntegrationTemplate(
                    "event-schemas",
                    "event-stream/schemas.hbs",
                    "{{from.path}}/events/schemas.{{from.extension}}",
                    false
                )
            )
        ));

        // Shared Types integration templates
        templateSets.put(PackMetadata.IntegrationDefinition.IntegrationType.SHARED_TYPES, new IntegrationTemplateSet(
            PackMetadata.IntegrationDefinition.IntegrationType.SHARED_TYPES,
            List.of(
                new IntegrationTemplate(
                    "shared-types",
                    "shared-types/types.hbs",
                    "shared/types/{{integration.id}}.{{from.extension}}",
                    false
                )
            )
        ));

        // Service Mesh integration templates
        templateSets.put(PackMetadata.IntegrationDefinition.IntegrationType.SERVICE_MESH, new IntegrationTemplateSet(
            PackMetadata.IntegrationDefinition.IntegrationType.SERVICE_MESH,
            List.of(
                new IntegrationTemplate(
                    "service-discovery",
                    "service-mesh/discovery.hbs",
                    "{{from.path}}/config/discovery.{{from.extension}}",
                    false
                ),
                new IntegrationTemplate(
                    "mesh-config",
                    "service-mesh/mesh.hbs",
                    "infra/mesh/{{integration.id}}.yaml",
                    false
                )
            )
        ));

        log.info("Loaded {} integration template sets", templateSets.size());
    }

    /**
     * Integration template set.
     */
    private record IntegrationTemplateSet(
        PackMetadata.IntegrationDefinition.IntegrationType type,
        List<IntegrationTemplate> templates
    ) {}

    /**
     * Integration template definition.
     */
    private record IntegrationTemplate(
        String name,
        String templatePath,
        String targetPath,
        boolean executable
    ) {}

    /**
     * Generated file result.
     */
    public record GeneratedFile(
        String name,
        String targetPath,
        String content,
        boolean executable
    ) {}

    /**
     * Integration generation result.
     */
    public record IntegrationResult(
        String integrationId,
        PackMetadata.IntegrationDefinition.IntegrationType type,
        boolean successful,
        List<GeneratedFile> generatedFiles,
        List<String> errors
    ) {}

    /**
     * Module metadata for integration generation.
     */
    public record ModuleMetadata(
        String id,
        String name,
        String type,
        String language,
        String framework,
        Map<String, String> outputs
    ) {}
}
