/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config.interpolation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves variables in configuration strings.
 * Supports environment variables, system properties, and file references.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>{@code ${VAR_NAME}} - Environment variable (required)</li>
 *   <li>{@code ${VAR_NAME:default}} - Environment variable with default value</li>
 *   <li>{@code ${sys:prop.name}} - System property</li>
 *   <li>{@code ${sys:prop.name:default}} - System property with default</li>
 *   <li>{@code ${ref:path/to/file}} - File reference (reads file content)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * VariableResolver resolver = new VariableResolver(basePath);
 * resolver.addVariable("APP_NAME", "ghatana");
 * String resolved = resolver.resolve("app.name=${APP_NAME}");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Resolve variable interpolations in configuration strings
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public class VariableResolver {

    private static final Logger log = LoggerFactory.getLogger(VariableResolver.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
    private static final Pattern SYS_PROP_PATTERN = Pattern.compile("\\$\\{sys:([^:}]+)(?::([^}]*))?\\}");
    private static final Pattern REF_PATTERN = Pattern.compile("\\$\\{ref:([^}]+)\\}");
    private static final Executor DEFAULT_EXECUTOR = Executors.newCachedThreadPool();

    private final Map<String, String> customVariables;
    private final Path baseDir;
    private final Executor executor;

    /**
     * Creates a new variable resolver with a base directory for file references.
     *
     * @param baseDir base directory for resolving file references
     */
    public VariableResolver(@NotNull Path baseDir) {
        this(baseDir, DEFAULT_EXECUTOR);
    }

    /**
     * Creates a new variable resolver with custom executor.
     *
     * @param baseDir  base directory for resolving file references
     * @param executor executor for async operations
     */
    public VariableResolver(@NotNull Path baseDir, @NotNull Executor executor) {
        this.baseDir = baseDir;
        this.executor = executor;
        this.customVariables = new HashMap<>();
    }

    /**
     * Creates a variable resolver with default base directory (current directory).
     */
    public VariableResolver() {
        this(Path.of("."), DEFAULT_EXECUTOR);
    }

    /**
     * Adds a custom variable that can be referenced in configurations.
     *
     * @param name  variable name
     * @param value variable value
     * @return this resolver for chaining
     */
    @NotNull
    public VariableResolver addVariable(@NotNull String name, @NotNull String value) {
        customVariables.put(name, value);
        return this;
    }

    /**
     * Adds multiple custom variables.
     *
     * @param variables map of variable names to values
     * @return this resolver for chaining
     */
    @NotNull
    public VariableResolver addVariables(@NotNull Map<String, String> variables) {
        customVariables.putAll(variables);
        return this;
    }

    /**
     * Resolves all variables in the given content synchronously.
     *
     * @param content content with variable references
     * @return content with resolved variables
     * @throws VariableResolutionException if a required variable is not found
     */
    @NotNull
    public String resolve(@NotNull String content) {
        if (content.isEmpty()) {
            return content;
        }

        String result = content;
        result = resolveFileReferences(result);
        result = resolveSystemProperties(result);
        result = resolveEnvironmentVariables(result);
        result = resolveCustomVariables(result);
        return result;
    }

    /**
     * Resolves all variables in the given content asynchronously.
     *
     * @param content content with variable references
     * @return Promise of content with resolved variables
     */
    @NotNull
    public Promise<String> resolveAsync(@NotNull String content) {
        return Promise.ofBlocking(executor, () -> resolve(content));
    }

    private String resolveEnvironmentVariables(String content) {
        Matcher matcher = ENV_VAR_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);

            if (varName.startsWith("sys:") || varName.startsWith("ref:")) {
                continue;
            }

            String value = System.getenv(varName);
            if (value == null) {
                value = customVariables.get(varName);
            }

            if (value == null) {
                if (defaultValue != null) {
                    value = defaultValue;
                } else {
                    throw new VariableResolutionException(
                            "Required environment variable not found: " + varName);
                }
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveSystemProperties(String content) {
        Matcher matcher = SYS_PROP_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String propName = matcher.group(1);
            String defaultValue = matcher.group(2);

            String value = System.getProperty(propName);
            if (value == null && defaultValue != null) {
                value = defaultValue;
            }

            if (value != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            } else {
                throw new VariableResolutionException(
                        "Required system property not found: " + propName);
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveFileReferences(String content) {
        Matcher matcher = REF_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String filePath = matcher.group(1);
            Path absolutePath = baseDir.resolve(filePath);

            try {
                if (!Files.exists(absolutePath)) {
                    throw new VariableResolutionException(
                            "Referenced file not found: " + absolutePath);
                }

                String fileContent = Files.readString(absolutePath);
                matcher.appendReplacement(result, Matcher.quoteReplacement(fileContent.trim()));
            } catch (IOException e) {
                throw new VariableResolutionException(
                        "Failed to read referenced file: " + absolutePath, e);
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveCustomVariables(String content) {
        String result = content;
        for (Map.Entry<String, String> entry : customVariables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }

    /**
     * Gets the base directory for file references.
     *
     * @return base directory
     */
    @NotNull
    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Gets all custom variables.
     *
     * @return map of custom variables
     */
    @NotNull
    public Map<String, String> getCustomVariables() {
        return new HashMap<>(customVariables);
    }
}
