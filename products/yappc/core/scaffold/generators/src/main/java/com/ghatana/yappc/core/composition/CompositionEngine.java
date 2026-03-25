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

package com.ghatana.yappc.core.composition;

import com.ghatana.yappc.core.pack.*;
import com.ghatana.yappc.core.template.TemplateEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composition engine for generating multi-module projects.
 * 
 * Handles module dependency resolution, integration generation, and lifecycle hooks.
 * 
 * @doc.type class
 * @doc.purpose Composition engine for universal multi-module project generation
 * @doc.layer platform
 * @doc.pattern Engine/Orchestrator
 */
public class CompositionEngine {

    private static final Logger log = LoggerFactory.getLogger(CompositionEngine.class);

    private final PackEngine packEngine;
    private final TemplateEngine templateEngine;

    public CompositionEngine(PackEngine packEngine, TemplateEngine templateEngine) {
        this.packEngine = packEngine;
        this.templateEngine = templateEngine;
    }

    /**
     * Generate a multi-module project from a composition definition.
     *
     * @param composition composition definition
     * @param outputPath target directory for generated project
     * @param variables global variables for generation
     * @return composition generation result
     * @throws CompositionException if generation fails
     * 
     * @doc.purpose Generate multi-module project from composition
     * @doc.layer platform
     */
    public CompositionResult generateComposition(
            CompositionDefinition composition,
            Path outputPath,
            Map<String, Object> variables) throws CompositionException {
        
        log.info("Generating composition '{}' to: {}", composition.metadata().name(), outputPath);

        try {
            Files.createDirectories(outputPath);

            // Resolve module dependencies
            List<PackMetadata.ModuleDefinition> orderedModules = 
                resolveModuleDependencies(composition.modules());

            // Execute pre-generation hooks
            executeHooks(composition.lifecycle().preGeneration(), outputPath, variables);

            // Generate each module
            List<ModuleResult> moduleResults = new ArrayList<>();
            Map<String, Map<String, String>> moduleOutputs = new HashMap<>();

            for (PackMetadata.ModuleDefinition module : orderedModules) {
                if (!isModuleEnabled(module, variables)) {
                    log.debug("Skipping disabled module: {}", module.id());
                    continue;
                }

                ModuleResult result = generateModule(module, outputPath, variables);
                moduleResults.add(result);
                moduleOutputs.put(module.id(), module.outputs());
            }

            // Generate integrations
            List<IntegrationResult> integrationResults = new ArrayList<>();
            if (composition.integrations() != null) {
                for (PackMetadata.IntegrationDefinition integration : composition.integrations()) {
                    if (shouldGenerateIntegration(integration, moduleOutputs, variables)) {
                        IntegrationResult result = 
                            generateIntegration(integration, outputPath, moduleOutputs, variables);
                        integrationResults.add(result);
                    }
                }
            }

            // Execute post-generation hooks
            executeHooks(composition.lifecycle().postGeneration(), outputPath, variables);

            return new CompositionResult(
                true,
                composition.metadata().name(),
                moduleResults,
                integrationResults,
                calculateTotalFiles(moduleResults, integrationResults),
                new ArrayList<>(),
                new ArrayList<>()
            );

        } catch (Exception e) {
            log.error("Composition generation failed", e);
            throw new CompositionException("Failed to generate composition: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve module dependencies and return topologically sorted list.
     */
    private List<PackMetadata.ModuleDefinition> resolveModuleDependencies(
            List<PackMetadata.ModuleDefinition> modules) throws CompositionException {
        
        Map<String, PackMetadata.ModuleDefinition> moduleMap = new HashMap<>();
        for (PackMetadata.ModuleDefinition module : modules) {
            moduleMap.put(module.id(), module);
        }

        // Topological sort using DFS
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        List<PackMetadata.ModuleDefinition> sorted = new ArrayList<>();

        for (PackMetadata.ModuleDefinition module : modules) {
            if (!visited.contains(module.id())) {
                topologicalSort(module, moduleMap, visited, visiting, sorted);
            }
        }

        return sorted;
    }

    private void topologicalSort(
            PackMetadata.ModuleDefinition module,
            Map<String, PackMetadata.ModuleDefinition> moduleMap,
            Set<String> visited,
            Set<String> visiting,
            List<PackMetadata.ModuleDefinition> sorted) throws CompositionException {
        
        if (visiting.contains(module.id())) {
            throw new CompositionException("Circular dependency detected involving module: " + module.id());
        }

        if (visited.contains(module.id())) {
            return;
        }

        visiting.add(module.id());

        if (module.dependencies() != null) {
            for (String depId : module.dependencies()) {
                PackMetadata.ModuleDefinition dep = moduleMap.get(depId);
                if (dep == null) {
                    throw new CompositionException("Module dependency not found: " + depId);
                }
                topologicalSort(dep, moduleMap, visited, visiting, sorted);
            }
        }

        visiting.remove(module.id());
        visited.add(module.id());
        sorted.add(module);
    }

    /**
     * Check if module should be generated based on enabled flag and condition.
     */
    private boolean isModuleEnabled(
            PackMetadata.ModuleDefinition module,
            Map<String, Object> variables) {
        
        if (module.enabled() != null && !module.enabled()) {
            return false;
        }

        if (module.condition() != null && !module.condition().isEmpty()) {
            return evaluateCondition(module.condition(), variables);
        }

        return true;
    }

    /**
     * Generate a single module.
     */
    private ModuleResult generateModule(
            PackMetadata.ModuleDefinition module,
            Path outputPath,
            Map<String, Object> globalVariables) throws PackException {
        
        log.info("Generating module '{}' using pack '{}'", module.id(), module.pack());

        // Load pack
        Path packPath = resolvePackPath(module.pack());
        Pack pack = packEngine.loadPack(packPath);

        // Merge module variables with global variables
        Map<String, Object> mergedVariables = new HashMap<>(globalVariables);
        if (module.variables() != null) {
            mergedVariables.putAll(module.variables());
        }

        // Generate module
        Path modulePath = outputPath.resolve(module.path());
        PackEngine.GenerationResult result = 
            packEngine.generateFromPack(pack, modulePath, mergedVariables);

        return new ModuleResult(
            module.id(),
            module.name(),
            result.successful(),
            result.filesGenerated(),
            result.generatedFiles(),
            result.errors()
        );
    }

    /**
     * Check if integration should be generated.
     */
    private boolean shouldGenerateIntegration(
            PackMetadata.IntegrationDefinition integration,
            Map<String, Map<String, String>> moduleOutputs,
            Map<String, Object> variables) {
        
        // Check if both modules exist
        if (!moduleOutputs.containsKey(integration.from()) || 
            !moduleOutputs.containsKey(integration.to())) {
            return false;
        }

        // Check condition
        if (integration.condition() != null && !integration.condition().isEmpty()) {
            return evaluateCondition(integration.condition(), variables);
        }

        return true;
    }

    /**
     * Generate integration between modules.
     */
    private IntegrationResult generateIntegration(
            PackMetadata.IntegrationDefinition integration,
            Path outputPath,
            Map<String, Map<String, String>> moduleOutputs,
            Map<String, Object> variables) {
        
        log.info("Generating integration '{}' from '{}' to '{}'", 
            integration.id(), integration.from(), integration.to());

        List<String> generatedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            // Merge integration variables with outputs
            Map<String, Object> integrationVars = new HashMap<>(variables);
            if (integration.variables() != null) {
                integrationVars.putAll(integration.variables());
            }
            integrationVars.put("from", moduleOutputs.get(integration.from()));
            integrationVars.put("to", moduleOutputs.get(integration.to()));

            // Generate integration templates
            if (integration.templates() != null) {
                for (String templateName : integration.templates()) {
                    try {
                        String rendered = templateEngine.render(
                            loadIntegrationTemplate(templateName),
                            integrationVars
                        );
                        Path targetPath = outputPath.resolve(
                            resolveIntegrationTargetPath(integration, templateName)
                        );
                        Files.createDirectories(targetPath.getParent());
                        Files.writeString(targetPath, rendered);
                        generatedFiles.add(targetPath.toString());
                    } catch (Exception e) {
                        errors.add("Failed to generate template " + templateName + ": " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            errors.add("Integration generation failed: " + e.getMessage());
        }

        return new IntegrationResult(
            integration.id(),
            integration.name(),
            errors.isEmpty(),
            generatedFiles.size(),
            generatedFiles,
            errors
        );
    }

    /**
     * Execute lifecycle hooks.
     */
    private void executeHooks(
            List<String> hooks,
            Path workingDir,
            Map<String, Object> variables) {
        
        if (hooks == null || hooks.isEmpty()) {
            return;
        }

        for (String hook : hooks) {
            try {
                log.debug("Executing hook: {}", hook);
                // Hook execution would be implemented here
                // For now, just log
            } catch (Exception e) {
                log.warn("Hook execution failed: {}", hook, e);
            }
        }
    }

    /**
     * Evaluate a condition expression.
     * Supports simple variable checks and boolean expressions.
     * 
     * @param condition condition expression (e.g., "enabled", "!disabled", "env == 'prod'")
     * @param variables variable context
     * @return true if condition evaluates to true
     */
    private boolean evaluateCondition(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        
        String trimmed = condition.trim();
        
        // Handle negation
        if (trimmed.startsWith("!")) {
            return !evaluateCondition(trimmed.substring(1), variables);
        }
        
        // Handle equality checks
        if (trimmed.contains("==")) {
            String[] parts = trimmed.split("==");
            if (parts.length == 2) {
                String left = resolveValue(parts[0].trim(), variables);
                String right = parts[1].trim().replace("'", "").replace("\"", "");
                return left.equals(right);
            }
        }
        
        // Handle inequality checks
        if (trimmed.contains("!=")) {
            String[] parts = trimmed.split("!=");
            if (parts.length == 2) {
                String left = resolveValue(parts[0].trim(), variables);
                String right = parts[1].trim().replace("'", "").replace("\"", "");
                return !left.equals(right);
            }
        }
        
        // Simple variable check
        Object value = variables.get(trimmed);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return !((String) value).isEmpty() && !"false".equalsIgnoreCase((String) value);
        }
        return value != null;
    }
    
    /**
     * Resolve a variable value from the context.
     */
    private String resolveValue(String expr, Map<String, Object> variables) {
        Object value = variables.get(expr);
        return value != null ? value.toString() : "";
    }

    /**
     * Resolve pack path from pack name.
     * Searches in standard pack locations.
     * 
     * @param packName name of the pack
     * @return resolved pack path
     */
    private Path resolvePackPath(String packName) {
        // Try multiple standard locations
        List<Path> searchPaths = List.of(
            Path.of("packs", packName),
            Path.of("templates", "packs", packName),
            Path.of(".yappc", "packs", packName),
            Path.of(System.getProperty("user.home"), ".yappc", "packs", packName)
        );
        
        for (Path path : searchPaths) {
            if (Files.exists(path) && Files.isDirectory(path)) {
                log.debug("Resolved pack '{}' to: {}", packName, path);
                return path;
            }
        }
        
        // Default to packs directory
        Path defaultPath = Path.of("packs", packName);
        log.warn("Pack '{}' not found in standard locations, using default: {}", packName, defaultPath);
        return defaultPath;
    }

    /**
     * Load integration template from templates directory.
     * 
     * @param templateName name of the template file
     * @return template content
     */
    private String loadIntegrationTemplate(String templateName) {
        try {
            // Try multiple template locations
            List<Path> templatePaths = List.of(
                Path.of("templates", "integrations", templateName),
                Path.of("core", "src", "main", "resources", "templates", "integrations", templateName),
                Path.of(".yappc", "templates", "integrations", templateName)
            );
            
            for (Path path : templatePaths) {
                if (Files.exists(path)) {
                    log.debug("Loading integration template from: {}", path);
                    return Files.readString(path);
                }
            }
            
            log.warn("Integration template not found: {}", templateName);
            return "";
        } catch (IOException e) {
            log.error("Failed to load integration template: {}", templateName, e);
            return "";
        }
    }

    /**
     * Resolve target path for integration template based on integration type.
     * 
     * @param integration integration definition
     * @param templateName template file name
     * @return resolved target path
     */
    private String resolveIntegrationTargetPath(
            PackMetadata.IntegrationDefinition integration,
            String templateName) {
        
        String basePath = "integrations/" + integration.id();
        
        // Customize path based on integration type
        return switch (integration.type()) {
            case API_CLIENT -> basePath + "/api/" + templateName;
            case DATASOURCE -> basePath + "/db/" + templateName;
            case EVENT_STREAM -> basePath + "/events/" + templateName;
            case SHARED_TYPES -> "shared/types/" + integration.id() + "/" + templateName;
            case SERVICE_MESH -> basePath + "/mesh/" + templateName;
        };
    }

    /**
     * Calculate total files generated.
     */
    private int calculateTotalFiles(
            List<ModuleResult> moduleResults,
            List<IntegrationResult> integrationResults) {
        
        int total = 0;
        for (ModuleResult result : moduleResults) {
            total += result.filesGenerated();
        }
        for (IntegrationResult result : integrationResults) {
            total += result.filesGenerated();
        }
        return total;
    }

    /**
     * Composition generation result.
     */
    public record CompositionResult(
        boolean successful,
        String compositionName,
        List<ModuleResult> moduleResults,
        List<IntegrationResult> integrationResults,
        int totalFilesGenerated,
        List<String> errors,
        List<String> warnings
    ) {}

    /**
     * Module generation result.
     */
    public record ModuleResult(
        String moduleId,
        String moduleName,
        boolean successful,
        int filesGenerated,
        List<String> generatedFiles,
        List<String> errors
    ) {}

    /**
     * Integration generation result.
     */
    public record IntegrationResult(
        String integrationId,
        String integrationName,
        boolean successful,
        int filesGenerated,
        List<String> generatedFiles,
        List<String> errors
    ) {}
}
