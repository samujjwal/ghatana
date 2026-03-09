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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registry for framework-specific templates.
 * Discovers, loads, and manages templates for various frameworks.
 * 
 * @doc.type class
 * @doc.purpose Manage framework-specific templates
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class FrameworkTemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(FrameworkTemplateRegistry.class);
    
    private final Map<String, FrameworkTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, List<FrameworkTemplate>> frameworkIndex = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final List<Path> searchPaths;

    /**
     * Create registry with default search paths.
     */
    public FrameworkTemplateRegistry() {
        this(getDefaultSearchPaths());
    }

    /**
     * Create registry with custom search paths.
     *
     * @param searchPaths paths to search for templates
     */
    public FrameworkTemplateRegistry(List<Path> searchPaths) {
        this.searchPaths = searchPaths;
        discoverTemplates();
    }

    /**
     * Register a framework template.
     *
     * @param template template to register
     */
    public void registerTemplate(FrameworkTemplate template) {
        log.debug("Registering template: {}", template.id());
        
        templates.put(template.id(), template);
        
        frameworkIndex
            .computeIfAbsent(template.framework(), k -> new ArrayList<>())
            .add(template);
    }

    /**
     * Find templates for a specific framework.
     *
     * @param framework framework name
     * @param version framework version (optional, use "*" for any)
     * @return list of matching templates
     */
    public List<FrameworkTemplate> findTemplates(String framework, String version) {
        List<FrameworkTemplate> frameworkTemplates = frameworkIndex.get(framework);
        
        if (frameworkTemplates == null) {
            return List.of();
        }
        
        if ("*".equals(version)) {
            return new ArrayList<>(frameworkTemplates);
        }
        
        return frameworkTemplates.stream()
            .filter(t -> version.equals(t.version()) || "*".equals(t.version()))
            .collect(Collectors.toList());
    }

    /**
     * Find templates by category.
     *
     * @param framework framework name
     * @param category template category
     * @return list of matching templates
     */
    public List<FrameworkTemplate> findByCategory(String framework, String category) {
        return findTemplates(framework, "*").stream()
            .filter(t -> category.equals(t.category()))
            .collect(Collectors.toList());
    }

    /**
     * Get template by ID.
     *
     * @param templateId template identifier
     * @return optional containing template if found
     */
    public Optional<FrameworkTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    /**
     * Get all registered frameworks.
     *
     * @return set of framework names
     */
    public Set<String> getFrameworks() {
        return new HashSet<>(frameworkIndex.keySet());
    }

    /**
     * Get all templates for a framework.
     *
     * @param framework framework name
     * @return list of templates
     */
    public List<FrameworkTemplate> getAllTemplates(String framework) {
        return findTemplates(framework, "*");
    }

    /**
     * Validate template metadata.
     *
     * @param template template to validate
     * @return validation result
     */
    public ValidationResult validateTemplate(FrameworkTemplate template) {
        List<String> errors = new ArrayList<>();
        
        if (template.id() == null || template.id().isBlank()) {
            errors.add("Template ID is required");
        }
        
        if (template.framework() == null || template.framework().isBlank()) {
            errors.add("Framework is required");
        }
        
        if (template.name() == null || template.name().isBlank()) {
            errors.add("Template name is required");
        }
        
        if (template.templatePath() == null || !Files.exists(template.templatePath())) {
            errors.add("Template file does not exist: " + template.templatePath());
        }
        
        if (template.metadata() != null) {
            if (template.metadata().author() == null || template.metadata().author().isBlank()) {
                errors.add("Template author is required");
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Discover templates from search paths.
     */
    private void discoverTemplates() {
        log.info("Discovering templates from {} search paths", searchPaths.size());
        
        for (Path searchPath : searchPaths) {
            if (!Files.exists(searchPath)) {
                log.debug("Search path does not exist: {}", searchPath);
                continue;
            }
            
            try {
                discoverTemplatesInPath(searchPath);
            } catch (IOException e) {
                log.error("Failed to discover templates in: {}", searchPath, e);
            }
        }
        
        log.info("Discovered {} templates for {} frameworks", 
            templates.size(), frameworkIndex.size());
    }

    /**
     * Discover templates in a specific path.
     */
    private void discoverTemplatesInPath(Path basePath) throws IOException {
        try (Stream<Path> paths = Files.walk(basePath)) {
            paths.filter(p -> p.toString().endsWith(".hbs"))
                .forEach(this::loadTemplate);
        }
    }

    /**
     * Load template from file.
     */
    private void loadTemplate(Path templatePath) {
        try {
            // Extract metadata from path structure
            // Expected: frameworks/{framework}/{category}/{template-name}.hbs
            Path relativePath = getRelativePath(templatePath);
            String[] parts = relativePath.toString().split("/");
            
            if (parts.length < 3) {
                log.warn("Invalid template path structure: {}", templatePath);
                return;
            }
            
            String framework = parts[0];
            String category = parts[1];
            String fileName = parts[parts.length - 1];
            String templateName = fileName.replace(".hbs", "");
            
            // Try to load metadata file
            Path metadataPath = templatePath.getParent().resolve(templateName + ".meta.yaml");
            TemplateMetadata metadata = loadMetadata(metadataPath);
            
            // Create template
            FrameworkTemplate template = new FrameworkTemplate(
                generateTemplateId(framework, category, templateName),
                framework,
                "*", // Version wildcard by default
                category,
                templateName,
                metadata != null ? metadata.description() : "Template for " + templateName,
                templatePath,
                metadata != null ? metadata.variables() : Map.of(),
                metadata != null ? metadata.dependencies() : List.of(),
                metadata
            );
            
            registerTemplate(template);
            log.debug("Loaded template: {}", template.id());
            
        } catch (Exception e) {
            log.error("Failed to load template: {}", templatePath, e);
        }
    }

    /**
     * Load template metadata from YAML file.
     */
    private TemplateMetadata loadMetadata(Path metadataPath) {
        if (!Files.exists(metadataPath)) {
            return null;
        }
        
        try {
            return yamlMapper.readValue(metadataPath.toFile(), TemplateMetadata.class);
        } catch (IOException e) {
            log.warn("Failed to load metadata: {}", metadataPath, e);
            return null;
        }
    }

    /**
     * Get relative path from template base.
     */
    private Path getRelativePath(Path templatePath) {
        for (Path searchPath : searchPaths) {
            if (templatePath.startsWith(searchPath)) {
                return searchPath.relativize(templatePath);
            }
        }
        return templatePath;
    }

    /**
     * Generate unique template ID.
     */
    private String generateTemplateId(String framework, String category, String name) {
        return String.format("%s:%s:%s", framework, category, name);
    }

    /**
     * Get default search paths for templates.
     */
    private static List<Path> getDefaultSearchPaths() {
        return List.of(
            Path.of("templates/frameworks"),
            Path.of("core/src/main/resources/templates/frameworks"),
            Path.of(".yappc/templates/frameworks"),
            Path.of(System.getProperty("user.home"), ".yappc/templates/frameworks")
        );
    }

    /**
     * Validation result record.
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors
    ) {
        public String getErrorMessage() {
            return String.join(", ", errors);
        }
    }
}
