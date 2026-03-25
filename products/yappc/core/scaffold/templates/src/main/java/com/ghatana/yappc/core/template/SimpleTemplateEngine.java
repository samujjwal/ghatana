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

import com.ghatana.yappc.core.error.TemplateException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple template engine implementation with basic variable substitution. Week 2, Day 6 deliverable
 * - Template engine foundation (will be enhanced with Handlebars).
 *
 * <p>This is a simplified implementation that will be upgraded to full Handlebars integration once
 * external dependencies are properly resolved.
 *
 * @doc.type class
 * @doc.purpose Simple template engine implementation with basic variable substitution. Week 2, Day 6 deliverable
 * @doc.layer platform
 * @doc.pattern Component
 */
public class SimpleTemplateEngine implements TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(SimpleTemplateEngine.class);

    // Pattern to match {{variable}} and {{helper arg1 arg2}}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    private final ObjectMapper objectMapper;
    private final Map<String, TemplateHelper> customHelpers;

    public SimpleTemplateEngine() {
        this.objectMapper = JsonUtils.getDefaultMapper();
        this.customHelpers = new HashMap<>();

        // Register default helpers
        registerDefaultHelpers();
    }

    @Override
    public String render(String templateContent, Map<String, Object> context)
            throws TemplateException {
        try {
            String result = templateContent;
            Matcher matcher = VARIABLE_PATTERN.matcher(templateContent);

            while (matcher.find()) {
                String expression = matcher.group(1).trim();
                String replacement = evaluateExpression(expression, context);
                result = result.replace("{{" + matcher.group(1) + "}}", replacement);
            }

            return result;
        } catch (Exception e) {
            throw new TemplateException("Failed to render template", e);
        }
    }

    @Override
    public String renderFile(Path templatePath, Map<String, Object> context)
            throws TemplateException {
        try {
            String templateContent = Files.readString(templatePath);
            return render(templateContent, context);
        } catch (IOException e) {
            throw new TemplateException("Failed to read template file: " + templatePath, e);
        }
    }

    @Override
    public String render(String templateContent, JsonNode jsonContext) throws TemplateException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = objectMapper.convertValue(jsonContext, Map.class);
            return render(templateContent, context);
        } catch (IllegalArgumentException e) {
            throw new TemplateException("Failed to convert JSON context", e);
        }
    }

    @Override
    public void registerHelper(String name, TemplateHelper helper) {
        customHelpers.put(name, helper);
        log.debug("Registered template helper: {}", name);
    }

    @Override
    public TemplateMerger createMerger() {
        return new DefaultTemplateMerger();
    }

    /**
 * Evaluate a template expression like "variable" or "helper arg1 arg2". */
    private String evaluateExpression(String expression, Map<String, Object> context) {
        String[] parts = expression.split("\\s+");
        String name = parts[0];

        // Check if it's a helper call
        if (customHelpers.containsKey(name)) {
            try {
                TemplateHelper helper = customHelpers.get(name);
                Object contextValue = parts.length > 1 ? parts[1] : null;
                HelperOptions options = new HandlebarsHelperOptionsAdapter(contextValue);
                return helper.apply(contextValue, options).toString();
            } catch (IOException e) {
                log.error("Helper '{}' failed", name, e);
                return "[ERROR: " + e.getMessage() + "]";
            }
        }

        // Simple variable substitution
        Object value = context.get(name);
        return value != null ? value.toString() : "";
    }

    /**
 * Register default YAPPC helpers. */
    private void registerDefaultHelpers() {
        // packagePath helper - converts package name to path
        registerHelper(
                "packagePath",
                (context, options) -> {
                    if (context == null) return "";
                    String packageName = context.toString();
                    return packageName.replace('.', '/');
                });

        // safeImport helper - ensures safe Java import statement
        registerHelper(
                "safeImport",
                (context, options) -> {
                    if (context == null) return "";
                    String importName = context.toString();
                    // Validate import name
                    if (importName.matches("^[a-zA-Z][a-zA-Z0-9_.]*[a-zA-Z0-9]$")) {
                        return "import " + importName + ";";
                    }
                    return "// INVALID IMPORT: " + importName;
                });

        // otelEndpoint helper - formats OpenTelemetry endpoint
        registerHelper(
                "otelEndpoint",
                (context, options) -> {
                    String baseUrl = options.hash("baseUrl", "http://localhost:4317");
                    String service = options.hash("service", "yappc-service");
                    return baseUrl + "/v1/traces?service.name=" + service;
                });

        // lowercase helper
        registerHelper(
                "lowercase",
                (context, options) -> {
                    if (context == null) return "";
                    return context.toString().toLowerCase();
                });

        // uppercase helper
        registerHelper(
                "uppercase",
                (context, options) -> {
                    if (context == null) return "";
                    return context.toString().toUpperCase();
                });

        // capitalize helper - first letter uppercase
        registerHelper(
                "capitalize",
                (context, options) -> {
                    if (context == null) return "";
                    String str = context.toString();
                    if (str.isEmpty()) return str;
                    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
                });

        // pascalCase helper - converts to PascalCase
        registerHelper(
                "pascalCase",
                (context, options) -> {
                    if (context == null) return "";
                    return toPascalCase(context.toString());
                });

        // camelCase helper - converts to camelCase
        registerHelper(
                "camelCase",
                (context, options) -> {
                    if (context == null) return "";
                    String pascal = toPascalCase(context.toString());
                    if (pascal.isEmpty()) return pascal;
                    return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
                });

        // snakeCase helper - converts to snake_case
        registerHelper(
                "snakeCase",
                (context, options) -> {
                    if (context == null) return "";
                    return toSnakeCase(context.toString());
                });

        // kebabCase helper - converts to kebab-case
        registerHelper(
                "kebabCase",
                (context, options) -> {
                    if (context == null) return "";
                    return toKebabCase(context.toString());
                });

        // equals helper - compare two values
        registerHelper(
                "eq",
                (context, options) -> {
                    if (context == null) return "false";
                    String compare = options.hash("to", "");
                    return String.valueOf(context.toString().equals(compare));
                });

        // year helper - current year
        registerHelper(
                "year",
                (context, options) -> String.valueOf(java.time.Year.now().getValue()));

        // date helper - current date
        registerHelper(
                "date",
                (context, options) -> {
                    String format = options.hash("format", "yyyy-MM-dd");
                    return java.time.LocalDate.now().format(
                            java.time.format.DateTimeFormatter.ofPattern(format));
                });

        // uuid helper - generate UUID
        registerHelper(
                "uuid",
                (context, options) -> java.util.UUID.randomUUID().toString());

        log.debug("Registered {} default template helpers", customHelpers.size());
    }

    private String toPascalCase(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_' || c == '.' || c == ' ') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '-' || c == ' ' || c == '.') {
                sb.append('_');
            } else if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String toKebabCase(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == ' ' || c == '.') {
                sb.append('-');
            } else if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
