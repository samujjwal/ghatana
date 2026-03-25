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

package com.ghatana.yappc.core.template;

import com.ghatana.yappc.core.error.TemplateException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template inheritance resolver for YAPPC scaffolding.
 * 
 * Resolves template inheritance chains with override strategies:
 * - REPLACE: Child template replaces parent completely
 * - MERGE: Child template merges with parent
 * - APPEND: Child template appends to parent
 * 
 * Template resolution order: global → language → framework → project
 * 
 * @doc.type class
 * @doc.purpose Template inheritance resolution with override strategies
 * @doc.layer platform
 * @doc.pattern Resolver/Strategy
 */
public class TemplateInheritanceResolver {

    private static final Logger log = LoggerFactory.getLogger(TemplateInheritanceResolver.class);

    private final Map<String, Path> templatePaths;
    private final OverrideStrategy defaultStrategy;

    public TemplateInheritanceResolver() {
        this(OverrideStrategy.REPLACE);
    }

    public TemplateInheritanceResolver(OverrideStrategy defaultStrategy) {
        this.templatePaths = new LinkedHashMap<>();
        this.defaultStrategy = defaultStrategy;
    }

    /**
     * Register a template search path with priority.
     * 
     * @param level template level (global, language, framework, project)
     * @param path path to template directory
     */
    public void registerTemplatePath(TemplateLevel level, Path path) {
        templatePaths.put(level.name(), path);
        log.debug("Registered template path for {}: {}", level, path);
    }

    /**
     * Resolve template inheritance chain and return final template content.
     * 
     * @param templateName template name to resolve
     * @param strategy override strategy to use
     * @return resolved template content
     * @throws TemplateException if resolution fails
     */
    public String resolveTemplate(String templateName, OverrideStrategy strategy) 
            throws TemplateException {
        
        log.debug("Resolving template: {} with strategy: {}", templateName, strategy);

        List<TemplateSource> sources = findTemplateSources(templateName);
        
        if (sources.isEmpty()) {
            throw new TemplateException("Template not found: " + templateName);
        }

        if (sources.size() == 1) {
            return sources.get(0).content();
        }

        return applyInheritanceStrategy(sources, strategy);
    }

    /**
     * Resolve template with default strategy.
     */
    public String resolveTemplate(String templateName) throws TemplateException {
        return resolveTemplate(templateName, defaultStrategy);
    }

    /**
     * Find all template sources in inheritance chain.
     */
    private List<TemplateSource> findTemplateSources(String templateName) {
        List<TemplateSource> sources = new ArrayList<>();

        // Search in order: global → language → framework → project
        for (TemplateLevel level : TemplateLevel.values()) {
            Path basePath = templatePaths.get(level.name());
            if (basePath == null) continue;

            Path templatePath = basePath.resolve(templateName);
            if (Files.exists(templatePath) && Files.isRegularFile(templatePath)) {
                try {
                    String content = Files.readString(templatePath);
                    sources.add(new TemplateSource(level, templatePath, content));
                    log.debug("Found template at {}: {}", level, templatePath);
                } catch (IOException e) {
                    log.warn("Failed to read template at {}: {}", templatePath, e.getMessage());
                }
            }
        }

        return sources;
    }

    /**
     * Apply inheritance strategy to merge template sources.
     */
    private String applyInheritanceStrategy(
            List<TemplateSource> sources, 
            OverrideStrategy strategy) {
        
        switch (strategy) {
            case REPLACE:
                // Use the most specific (last) template
                return sources.get(sources.size() - 1).content();
                
            case MERGE:
                // Merge templates with block replacement
                return mergeTemplates(sources);
                
            case APPEND:
                // Append all templates
                return appendTemplates(sources);
                
            default:
                return sources.get(sources.size() - 1).content();
        }
    }

    /**
     * Merge templates using block replacement.
     * 
     * Blocks are defined as: {{#block blockName}}...{{/block}}
     * Child templates can override parent blocks.
     */
    private String mergeTemplates(List<TemplateSource> sources) {
        Map<String, String> blocks = new HashMap<>();
        String baseTemplate = sources.get(0).content();

        // Extract blocks from all templates
        for (TemplateSource source : sources) {
            extractBlocks(source.content(), blocks);
        }

        // Replace blocks in base template
        String result = baseTemplate;
        for (Map.Entry<String, String> block : blocks.entrySet()) {
            String blockPattern = "\\{\\{#block " + block.getKey() + "\\}\\}.*?\\{\\{/block\\}\\}";
            result = result.replaceAll(blockPattern, block.getValue());
        }

        return result;
    }

    /**
     * Extract blocks from template content.
     */
    private void extractBlocks(String content, Map<String, String> blocks) {
        // Simple block extraction - can be enhanced with proper parser
        String[] lines = content.split("\n");
        String currentBlock = null;
        StringBuilder blockContent = new StringBuilder();

        for (String line : lines) {
            if (line.contains("{{#block ")) {
                currentBlock = extractBlockName(line);
                blockContent = new StringBuilder();
            } else if (line.contains("{{/block}}") && currentBlock != null) {
                blocks.put(currentBlock, blockContent.toString());
                currentBlock = null;
            } else if (currentBlock != null) {
                blockContent.append(line).append("\n");
            }
        }
    }

    /**
     * Extract block name from block declaration.
     */
    private String extractBlockName(String line) {
        int start = line.indexOf("{{#block ") + 9;
        int end = line.indexOf("}}", start);
        return line.substring(start, end).trim();
    }

    /**
     * Append all templates in order.
     */
    private String appendTemplates(List<TemplateSource> sources) {
        StringBuilder result = new StringBuilder();
        for (TemplateSource source : sources) {
            result.append(source.content()).append("\n");
        }
        return result.toString();
    }

    /**
     * Template level enumeration.
     */
    public enum TemplateLevel {
        GLOBAL,
        LANGUAGE,
        FRAMEWORK,
        PROJECT
    }

    /**
     * Override strategy enumeration.
     */
    public enum OverrideStrategy {
        REPLACE,
        MERGE,
        APPEND
    }

    /**
     * Template source record.
     */
    private record TemplateSource(
        TemplateLevel level,
        Path path,
        String content
    ) {}
}
