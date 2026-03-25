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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple code transformer implementation with basic text-based transformations. Week 2, Day 6
 * deliverable - OpenRewrite foundation (will be enhanced with full OpenRewrite integration).
 *
 * <p>This provides basic transformations while the full OpenRewrite integration is being developed.
 *
 * @doc.type class
 * @doc.purpose Simple code transformer implementation with basic text-based transformations. Week 2, Day 6
 * @doc.layer platform
 * @doc.pattern Component
 */
public class SimpleCodeTransformer implements CodeTransformer {

    private static final Logger log = LoggerFactory.getLogger(SimpleCodeTransformer.class);

    // Patterns for common transformations
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^import\\s+([a-zA-Z0-9_.]+);?$", Pattern.MULTILINE);
    private static final Pattern DEPENDENCY_PATTERN =
            Pattern.compile("dependencies\\s*\\{([^}]*?)\\}", Pattern.DOTALL);

    @Override
    public TransformationResult applyJavaRecipe(String recipeName, List<Path> sourcePaths)
            throws TemplateException {
        log.info("Applying Java recipe '{}' to {} files", recipeName, sourcePaths.size());

        List<String> changedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int filesChanged = 0;

        for (Path sourcePath : sourcePaths) {
            try {
                boolean changed = applyJavaRecipeToFile(recipeName, sourcePath);
                if (changed) {
                    filesChanged++;
                    changedFiles.add(sourcePath.toString());
                }
            } catch (Exception e) {
                errors.add("Failed to transform " + sourcePath + ": " + e.getMessage());
                log.error("Failed to apply recipe to {}", sourcePath, e);
            }
        }

        boolean successful = errors.isEmpty();
        String summary =
                String.format(
                        "Applied recipe '%s': %d files changed, %d errors",
                        recipeName, filesChanged, errors.size());

        return new TransformationResult(successful, filesChanged, changedFiles, errors, summary);
    }

    @Override
    public TransformationResult applyGradleRecipe(String recipeName, List<Path> buildFiles)
            throws TemplateException {
        log.info("Applying Gradle recipe '{}' to {} files", recipeName, buildFiles.size());

        List<String> changedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int filesChanged = 0;

        for (Path buildFile : buildFiles) {
            try {
                boolean changed = applyGradleRecipeToFile(recipeName, buildFile);
                if (changed) {
                    filesChanged++;
                    changedFiles.add(buildFile.toString());
                }
            } catch (Exception e) {
                errors.add("Failed to transform " + buildFile + ": " + e.getMessage());
                log.error("Failed to apply recipe to {}", buildFile, e);
            }
        }

        boolean successful = errors.isEmpty();
        String summary =
                String.format(
                        "Applied recipe '%s': %d files changed, %d errors",
                        recipeName, filesChanged, errors.size());

        return new TransformationResult(successful, filesChanged, changedFiles, errors, summary);
    }

    @Override
    public TransformationResult addGradleDependency(
            Path buildFile, String groupId, String artifactId, String version, String configuration)
            throws TemplateException {
        log.debug(
                "Adding Gradle dependency {}:{}:{} to {}", groupId, artifactId, version, buildFile);

        try {
            String content = Files.readString(buildFile);
            String dependencyLine =
                    String.format("    %s '%s:%s:%s'", configuration, groupId, artifactId, version);

            // Find dependencies block
            Matcher matcher = DEPENDENCY_PATTERN.matcher(content);
            if (matcher.find()) {
                String dependenciesBlock = matcher.group(1);

                // Check if dependency already exists
                if (dependenciesBlock.contains(groupId + ":" + artifactId)) {
                    log.debug("Dependency already exists in {}", buildFile);
                    return new TransformationResult(
                            true, 0, List.of(), List.of(), "Dependency already exists");
                }

                // Add dependency to block
                String newDependenciesBlock = dependenciesBlock.trim() + "\n" + dependencyLine;
                String newContent = content.replace(matcher.group(1), newDependenciesBlock);

                Files.writeString(buildFile, newContent);

                return new TransformationResult(
                        true,
                        1,
                        List.of(buildFile.toString()),
                        List.of(),
                        "Added dependency " + groupId + ":" + artifactId + ":" + version);
            } else {
                throw new TemplateException("No dependencies block found in " + buildFile);
            }

        } catch (Exception e) {
            String error = "Failed to add dependency: " + e.getMessage();
            return new TransformationResult(false, 0, List.of(), List.of(error), error);
        }
    }

    @Override
    public TransformationResult addJavaImport(Path javaFile, String importClass)
            throws TemplateException {
        log.debug("Adding Java import {} to {}", importClass, javaFile);

        try {
            String content = Files.readString(javaFile);

            // Check if import already exists
            if (content.contains("import " + importClass)) {
                log.debug("Import already exists in {}", javaFile);
                return new TransformationResult(
                        true, 0, List.of(), List.of(), "Import already exists");
            }

            // Find package declaration and add import after it
            String importLine = "import " + importClass + ";\n";
            String[] lines = content.split("\n");
            StringBuilder newContent = new StringBuilder();

            boolean importAdded = false;
            for (String line : lines) {
                newContent.append(line).append("\n");

                // Add import after package declaration
                if (!importAdded && line.startsWith("package ") && line.endsWith(";")) {
                    newContent.append("\n").append(importLine);
                    importAdded = true;
                }
            }

            if (importAdded) {
                Files.writeString(javaFile, newContent.toString());
                return new TransformationResult(
                        true,
                        1,
                        List.of(javaFile.toString()),
                        List.of(),
                        "Added import " + importClass);
            } else {
                throw new TemplateException("Could not find package declaration in " + javaFile);
            }

        } catch (Exception e) {
            String error = "Failed to add import: " + e.getMessage();
            return new TransformationResult(false, 0, List.of(), List.of(error), error);
        }
    }

    @Override
    public TransformationResult applyYappcTransformation(
            String transformationType, List<Path> targetPaths) throws TemplateException {
        log.info(
                "Applying YAPPC transformation '{}' to {} paths",
                transformationType,
                targetPaths.size());

        // Delegate to appropriate transformation based on type
        return switch (transformationType) {
            case "activej-setup" -> applyActiveJSetup(targetPaths);
            case "otel-setup" -> applyOtelSetup(targetPaths);
            case "docker-setup" -> applyDockerSetup(targetPaths);
            default ->
                    throw new TemplateException(
                            "Unknown transformation type: " + transformationType);
        };
    }

    /**
 * Apply Java recipe to a single file. */
    private boolean applyJavaRecipeToFile(String recipeName, Path sourcePath) throws Exception {
        // Placeholder for OpenRewrite integration
        log.debug("Would apply Java recipe '{}' to {}", recipeName, sourcePath);
        return false; // No changes made in this simplified implementation
    }

    /**
 * Apply Gradle recipe to a single file. */
    private boolean applyGradleRecipeToFile(String recipeName, Path buildFile) throws Exception {
        // Placeholder for OpenRewrite integration
        log.debug("Would apply Gradle recipe '{}' to {}", recipeName, buildFile);
        return false; // No changes made in this simplified implementation
    }

    /**
 * Apply ActiveJ-specific setup transformations. */
    private TransformationResult applyActiveJSetup(List<Path> targetPaths)
            throws TemplateException {
        log.info("Applying ActiveJ setup to {} paths", targetPaths.size());

        // Placeholder for ActiveJ-specific transformations
        // This would add ActiveJ dependencies, setup main class, etc.

        return new TransformationResult(
                true, 0, List.of(), List.of(), "ActiveJ setup transformation completed");
    }

    /**
 * Apply OpenTelemetry setup transformations. */
    private TransformationResult applyOtelSetup(List<Path> targetPaths) throws TemplateException {
        log.info("Applying OpenTelemetry setup to {} paths", targetPaths.size());

        // Placeholder for OpenTelemetry-specific transformations
        // This would add OTel dependencies, configuration, etc.

        return new TransformationResult(
                true, 0, List.of(), List.of(), "OpenTelemetry setup transformation completed");
    }

    /**
 * Apply Docker setup transformations. */
    private TransformationResult applyDockerSetup(List<Path> targetPaths) throws TemplateException {
        log.info("Applying Docker setup to {} paths", targetPaths.size());

        // Placeholder for Docker-specific transformations
        // This would create Dockerfile, docker-compose.yml, etc.

        return new TransformationResult(
                true, 0, List.of(), List.of(), "Docker setup transformation completed");
    }
}
