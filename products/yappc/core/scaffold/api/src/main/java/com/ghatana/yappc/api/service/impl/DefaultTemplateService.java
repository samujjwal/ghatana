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

package com.ghatana.yappc.api.service.impl;

import com.ghatana.yappc.api.YappcConfig;
import com.ghatana.yappc.api.model.RenderResult;
import com.ghatana.yappc.api.model.TemplateInfo;
import com.ghatana.yappc.api.service.PackService;
import com.ghatana.yappc.api.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of TemplateService.
 *
 * @doc.type class
 * @doc.purpose Template rendering implementation
 * @doc.layer platform
 * @doc.pattern Service
 */
public class DefaultTemplateService implements TemplateService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTemplateService.class);
    
    // Pattern for {{variableName}} or {{helper variableName}}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)(?:\\s+(\\w+))?\\s*}}");
    
    private final YappcConfig config;
    private final Map<String, TemplateHelper> helpers = new ConcurrentHashMap<>();

    public DefaultTemplateService(YappcConfig config) {
        this.config = config;
        registerBuiltinHelpers();
    }

    private void registerBuiltinHelpers() {
        helpers.put("lowercase", String::toLowerCase);
        helpers.put("uppercase", String::toUpperCase);
        helpers.put("capitalize", this::capitalize);
        helpers.put("camelCase", this::toCamelCase);
        helpers.put("pascalCase", this::toPascalCase);
        helpers.put("snakeCase", this::toSnakeCase);
        helpers.put("kebabCase", this::toKebabCase);
        helpers.put("uuid", input -> UUID.randomUUID().toString());
        helpers.put("now", input -> java.time.Instant.now().toString());
        helpers.put("date", input -> java.time.LocalDate.now().toString());
    }

    @Override
    public String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String firstPart = matcher.group(1);
            String secondPart = matcher.group(2);
            
            String replacement;
            
            if (secondPart != null) {
                // Helper syntax: {{helper variableName}}
                String helperName = firstPart;
                String variableName = secondPart;
                
                Object value = variables.get(variableName);
                String stringValue = value != null ? value.toString() : "";
                
                TemplateHelper helper = helpers.get(helperName);
                if (helper != null) {
                    replacement = helper.apply(stringValue);
                } else {
                    // Unknown helper, treat as literal
                    replacement = matcher.group(0);
                }
            } else {
                // Simple variable: {{variableName}}
                String variableName = firstPart;
                
                // Check if it's a helper with no argument
                if (helpers.containsKey(variableName) && 
                    (variableName.equals("uuid") || variableName.equals("now") || variableName.equals("date"))) {
                    replacement = helpers.get(variableName).apply("");
                } else {
                    Object value = variables.get(variableName);
                    replacement = value != null ? value.toString() : "";
                }
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    @Override
    public String renderFile(Path templatePath, Map<String, Object> variables) {
        try {
            String content = Files.readString(templatePath);
            return render(content, variables);
        } catch (IOException e) {
            LOG.error("Failed to read template file: {}", templatePath, e);
            throw new RuntimeException("Failed to read template: " + templatePath, e);
        }
    }

    @Override
    public RenderResult renderToFile(Path templatePath, Path outputPath, Map<String, Object> variables) {
        try {
            String content = renderFile(templatePath, variables);
            
            // Ensure parent directories exist
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            
            Files.writeString(outputPath, content);
            return RenderResult.success(outputPath, content);
        } catch (Exception e) {
            LOG.error("Failed to render template to file: {} -> {}", templatePath, outputPath, e);
            return RenderResult.failure(e.getMessage());
        }
    }

    @Override
    public List<TemplateInfo> listTemplates(String packName) {
        Path packPath = config.getPacksPath().resolve(packName);
        Path templatesPath = packPath.resolve("templates");
        
        if (!Files.exists(templatesPath)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(templatesPath)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> createTemplateInfo(templatesPath, path))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error("Failed to list templates for pack: {}", packName, e);
            return List.of();
        }
    }

    private TemplateInfo createTemplateInfo(Path templatesRoot, Path templatePath) {
        String relativePath = templatesRoot.relativize(templatePath).toString();
        List<String> variables = getRequiredVariables(templatePath);
        
        return TemplateInfo.of(
                templatePath.getFileName().toString(),
                templatePath,
                relativePath.replace(".tmpl", ""),
                variables
        );
    }

    @Override
    public List<String> getRequiredVariables(Path templatePath) {
        Set<String> variables = new HashSet<>();
        
        try {
            String content = Files.readString(templatePath);
            Matcher matcher = VARIABLE_PATTERN.matcher(content);
            
            while (matcher.find()) {
                String firstPart = matcher.group(1);
                String secondPart = matcher.group(2);
                
                if (secondPart != null) {
                    // Helper syntax - variable is second part
                    if (!isBuiltinNoArgHelper(secondPart)) {
                        variables.add(secondPart);
                    }
                } else {
                    // Simple variable
                    if (!isBuiltinNoArgHelper(firstPart)) {
                        variables.add(firstPart);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to read template for variable extraction: {}", templatePath, e);
        }
        
        return new ArrayList<>(variables);
    }

    private boolean isBuiltinNoArgHelper(String name) {
        return "uuid".equals(name) || "now".equals(name) || "date".equals(name);
    }

    @Override
    public List<String> getPackRequiredVariables(String packName) {
        Set<String> allVariables = new HashSet<>();
        
        for (TemplateInfo template : listTemplates(packName)) {
            allVariables.addAll(template.variables());
        }
        
        return new ArrayList<>(allVariables);
    }

    @Override
    public boolean validateSyntax(String templateContent) {
        try {
            // Try to parse all template expressions
            Matcher matcher = VARIABLE_PATTERN.matcher(templateContent);
            while (matcher.find()) {
                // Check for valid helper names
                String firstPart = matcher.group(1);
                String secondPart = matcher.group(2);
                
                if (secondPart != null && !helpers.containsKey(firstPart)) {
                    // Unknown helper
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> getAvailableHelpers() {
        return new ArrayList<>(helpers.keySet());
    }

    @Override
    public void registerHelper(String name, TemplateHelper helper) {
        helpers.put(name, helper);
        LOG.info("Registered custom template helper: {}", name);
    }

    // === Helper implementations ===

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    private String toCamelCase(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] parts = input.split("[-_\\s]+");
        StringBuilder result = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            result.append(capitalize(parts[i].toLowerCase()));
        }
        return result.toString();
    }

    private String toPascalCase(String input) {
        String camelCase = toCamelCase(input);
        return capitalize(camelCase);
    }

    private String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[-\\s]+", "_")
                .toLowerCase();
    }

    private String toKebabCase(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[_\\s]+", "-")
                .toLowerCase();
    }
}
