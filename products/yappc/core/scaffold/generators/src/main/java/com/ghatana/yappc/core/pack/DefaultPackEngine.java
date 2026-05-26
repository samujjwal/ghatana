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

package com.ghatana.yappc.core.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.core.template.TemplateEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of PackEngine. Week 2, Day 7 deliverable - Pack generation engine.
 * @doc.type class
 * @doc.purpose Default implementation of PackEngine. Week 2, Day 7 deliverable - Pack generation engine.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DefaultPackEngine implements PackEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultPackEngine.class);
    private static final String PACK_METADATA_FILE = "pack.json";

    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final Set<Path> packLocations;

    public DefaultPackEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        this.objectMapper = JsonUtils.getDefaultMapper();
        this.packLocations = new HashSet<>();
    }

    @Override
    public Pack loadPack(Path packPath) throws PackException {
        log.debug("Loading pack from: {}", packPath);

        if (!Files.isDirectory(packPath)) {
            throw new PackException("Pack path is not a directory: " + packPath);
        }

        Path metadataFile = packPath.resolve(PACK_METADATA_FILE);
        if (!Files.exists(metadataFile)) {
            throw new PackException("Pack metadata file not found: " + metadataFile);
        }

        try {
            // Load pack metadata
            String metadataContent = Files.readString(metadataFile);
            PackMetadata metadata = objectMapper.readValue(metadataContent, PackMetadata.class);

            // Load template files
            Map<String, String> templateContents = loadTemplateContents(packPath, metadata);

            log.info("Loaded pack: {} v{}", metadata.name(), metadata.version());
            return new Pack(metadata, packPath, templateContents);

        } catch (IOException e) {
            throw new PackException("Failed to load pack metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public GenerationResult generateFromPack(
            Pack pack, Path outputPath, Map<String, Object> variables) throws PackException {
        log.info("Generating from pack '{}' to: {}", pack.getName(), outputPath);

        List<String> generatedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> createdFiles = new ArrayList<>();
        List<String> updatedFiles = new ArrayList<>();
        List<String> unchangedFiles = new ArrayList<>();
        List<String> backupFiles = new ArrayList<>();
        int filesGenerated = 0;

        try {
            Files.createDirectories(outputPath);
            Path outputRoot = outputPath.toAbsolutePath().normalize();

            PackMetadata metadata = pack.getMetadata();
            Map<String, PackMetadata.TemplateFile> templates = metadata.templates();

            if (templates != null) {
                for (Map.Entry<String, PackMetadata.TemplateFile> entry : templates.entrySet()) {
                    String templateName = entry.getKey();
                    PackMetadata.TemplateFile templateSpec = entry.getValue();

                    try {
                        TemplateGenerationOutcome outcome =
                                generateTemplateFile(
                                        pack, templateName, templateSpec, outputRoot, variables);
                        if (outcome.written()) {
                            filesGenerated++;
                        }
                        generatedFiles.add(outcome.relativePath());
                        createdFiles.addAll(outcome.createdFiles());
                        updatedFiles.addAll(outcome.updatedFiles());
                        unchangedFiles.addAll(outcome.unchangedFiles());
                        backupFiles.addAll(outcome.backupFiles());
                        warnings.addAll(outcome.warnings());
                    } catch (Exception e) {
                        String error =
                                String.format(
                                        "Failed to generate %s: %s", templateName, e.getMessage());
                        errors.add(error);
                        log.error("Template generation failed", e);
                    }
                }
            }

            // Execute post-generation hooks if any
            executeHooks(pack.getMetadata().hooks(), "post-generation", outputPath, variables);

            boolean successful = errors.isEmpty();
            String summary =
                    String.format(
                            "Generated %d files from pack '%s'", filesGenerated, pack.getName());

            return new GenerationResult(
                    successful,
                    filesGenerated,
                    generatedFiles,
                    errors,
                    warnings,
                    summary,
                    Map.of(
                            "pack",
                            pack.getName(),
                            "createdFiles",
                            createdFiles,
                            "updatedFiles",
                            updatedFiles,
                            "unchangedFiles",
                            unchangedFiles,
                            "backupFiles",
                            backupFiles));

        } catch (IOException e) {
            throw new PackException("Failed to generate from pack: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PackMetadata> listAvailablePacks() {
        List<PackMetadata> packs = new ArrayList<>();

        for (Path location : packLocations) {
            if (Files.isDirectory(location)) {
                try (Stream<Path> paths = Files.list(location)) {
                    paths.filter(Files::isDirectory)
                            .forEach(
                                    packDir -> {
                                        try {
                                            Pack pack = loadPack(packDir);
                                            packs.add(pack.getMetadata());
                                        } catch (PackException e) {
                                            log.debug(
                                                    "Skipping invalid pack at {}: {}",
                                                    packDir,
                                                    e.getMessage());
                                        }
                                    });
                } catch (IOException e) {
                    log.warn("Failed to list packs in {}: {}", location, e.getMessage());
                }
            }
        }

        return packs;
    }

    @Override
    public ValidationResult validatePack(Path packPath) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            Pack pack = loadPack(packPath);

            // Validate metadata
            PackMetadata metadata = pack.getMetadata();
            if (metadata.name() == null || metadata.name().trim().isEmpty()) {
                errors.add("Pack name is required");
            }
            if (metadata.version() == null || metadata.version().trim().isEmpty()) {
                errors.add("Pack version is required");
            }
            if (metadata.type() == null) {
                warnings.add("Pack type not specified");
            }

            // Validate template files exist
            if (metadata.templates() != null) {
                for (Map.Entry<String, PackMetadata.TemplateFile> entry :
                        metadata.templates().entrySet()) {
                    String templateName = entry.getKey();
                    PackMetadata.TemplateFile spec = entry.getValue();

                    Path templateFile = packPath.resolve(spec.source());
                    if (!Files.exists(templateFile)) {
                        errors.add(String.format("Template file not found: %s", spec.source()));
                    }
                }
            }

        } catch (PackException e) {
            errors.add("Failed to load pack: " + e.getMessage());
        }

        boolean valid = errors.isEmpty();
        String summary =
                valid
                        ? "Pack validation passed"
                        : String.format("Pack validation failed with %d errors", errors.size());

        return new ValidationResult(valid, errors, warnings, summary);
    }

    @Override
    public void registerPackLocation(Path packRegistryPath) {
        if (Files.isDirectory(packRegistryPath)) {
            packLocations.add(packRegistryPath);
            log.info("Registered pack location: {}", packRegistryPath);
        } else {
            log.warn("Pack location does not exist or is not a directory: {}", packRegistryPath);
        }
    }

    /**
 * Load template file contents from pack directory. */
    private Map<String, String> loadTemplateContents(Path packPath, PackMetadata metadata)
            throws IOException {
        Map<String, String> contents = new HashMap<>();

        if (metadata.templates() != null) {
            for (Map.Entry<String, PackMetadata.TemplateFile> entry :
                    metadata.templates().entrySet()) {
                String templateName = entry.getKey();
                PackMetadata.TemplateFile spec = entry.getValue();

                Path templateFile = packPath.resolve(spec.source());
                if (Files.exists(templateFile)) {
                    String content = Files.readString(templateFile);
                    contents.put(templateName, content);
                    log.debug("Loaded template: {} from {}", templateName, spec.source());
                } else {
                    log.warn("Template file not found: {}", templateFile);
                }
            }
        }

        return contents;
    }

    /**
 * Generate a single template file. */
    private TemplateGenerationOutcome generateTemplateFile(
            Pack pack,
            String templateName,
            PackMetadata.TemplateFile spec,
            Path outputRoot,
            Map<String, Object> variables)
            throws Exception {

        // Check condition if specified
        if (spec.condition() != null && !evaluateCondition(spec.condition(), variables)) {
            log.debug("Skipping template {} due to condition: {}", templateName, spec.condition());
            return TemplateGenerationOutcome.skipped(spec.target());
        }

        String templateContent = pack.getTemplateContent(templateName);
        if (templateContent == null) {
            throw new PackException("Template content not found: " + templateName);
        }

        // Render template
        String rendered = templateEngine.render(templateContent, variables);

        // Determine output file path
        Path outputFile = outputRoot.resolve(spec.target()).normalize();
        if (!outputFile.startsWith(outputRoot)) {
            throw new PackException("Template target escapes output directory: " + spec.target());
        }
        String relativePath = formatRelativePath(outputRoot.relativize(outputFile));

        // Handle merge strategy
        PackMetadata.TemplateFile.MergeStrategy merge = spec.merge();
        if (merge == PackMetadata.TemplateFile.MergeStrategy.SKIP_IF_EXISTS
                && Files.exists(outputFile)) {
            log.debug("Skipping existing file: {}", outputFile);
            return TemplateGenerationOutcome.unchanged(relativePath);
        }

        // Create parent directories
        Files.createDirectories(outputFile.getParent());

        // Write file
        if (merge == PackMetadata.TemplateFile.MergeStrategy.APPEND && Files.exists(outputFile)) {
            String existing = Files.readString(outputFile);
            if (existing.contains(rendered)) {
                return TemplateGenerationOutcome.unchanged(
                        relativePath,
                        "Skipped append for unchanged generated content: " + relativePath);
            }
            Path backup = backupExistingFile(outputRoot, outputFile);
            Files.writeString(outputFile, "\n" + rendered, StandardOpenOption.APPEND);
            return TemplateGenerationOutcome.updated(relativePath, formatRelativePath(outputRoot.relativize(backup)));
        } else if (Files.exists(outputFile)) {
            String existing = Files.readString(outputFile);
            if (existing.equals(rendered)) {
                return TemplateGenerationOutcome.unchanged(relativePath);
            }
            Path backup = backupExistingFile(outputRoot, outputFile);
            Files.writeString(outputFile, rendered);
            setExecutableIfRequested(spec, outputFile);
            log.debug("Updated file with backup: {}", outputFile);
            return TemplateGenerationOutcome.updated(relativePath, formatRelativePath(outputRoot.relativize(backup)));
        } else {
            Files.writeString(outputFile, rendered);
        }

        setExecutableIfRequested(spec, outputFile);

        log.debug("Generated file: {}", outputFile);
        return TemplateGenerationOutcome.created(relativePath);
    }

    private void setExecutableIfRequested(PackMetadata.TemplateFile spec, Path outputFile) {
        if (Boolean.TRUE.equals(spec.executable())) {
            outputFile.toFile().setExecutable(true);
        }
    }

    private Path backupExistingFile(Path outputRoot, Path outputFile) throws IOException {
        String timestamp = Instant.now().toString().replace(":", "-").replace(".", "-");
        Path relativePath = outputRoot.relativize(outputFile);
        Path backupFile = outputRoot.resolve(".yappc").resolve("backups").resolve(timestamp).resolve(relativePath);
        Files.createDirectories(backupFile.getParent());
        Files.copy(outputFile, backupFile, StandardCopyOption.COPY_ATTRIBUTES);
        return backupFile;
    }

    private String formatRelativePath(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    /**
 * Evaluate a condition expression (simple implementation). */
    private boolean evaluateCondition(String condition, Map<String, Object> variables) {
        // Simple condition evaluation - can be enhanced with expression parser
        if (condition.startsWith("has:")) {
            String variableName = condition.substring(4);
            return variables.containsKey(variableName) && variables.get(variableName) != null;
        }

        return true; // Default to true for unknown conditions
    }

    /**
 * Execute pack hooks. */
    private void executeHooks(
            PackMetadata.PackHooks hooks,
            String hookType,
            Path outputPath,
            Map<String, Object> variables) {
        if (hooks == null) return;

        List<String> hookCommands =
                switch (hookType) {
                    case "pre-generation" -> hooks.preGeneration();
                    case "post-generation" -> hooks.postGeneration();
                    case "pre-build" -> hooks.preBuild();
                    case "post-build" -> hooks.postBuild();
                    default -> null;
                };

        if (hookCommands != null) {
            for (String command : hookCommands) {
                log.info("Executing {} hook: {}", hookType, command);
                // Hook execution implementation would go here
            }
        }
    }

    private record TemplateGenerationOutcome(
            String relativePath,
            boolean written,
            List<String> createdFiles,
            List<String> updatedFiles,
            List<String> unchangedFiles,
            List<String> backupFiles,
            List<String> warnings) {

        private static TemplateGenerationOutcome created(String relativePath) {
            return new TemplateGenerationOutcome(
                    relativePath, true, List.of(relativePath), List.of(), List.of(), List.of(), List.of());
        }

        private static TemplateGenerationOutcome updated(String relativePath, String backupPath) {
            return new TemplateGenerationOutcome(
                    relativePath, true, List.of(), List.of(relativePath), List.of(), List.of(backupPath), List.of());
        }

        private static TemplateGenerationOutcome unchanged(String relativePath) {
            return unchanged(relativePath, null);
        }

        private static TemplateGenerationOutcome unchanged(String relativePath, String warning) {
            return new TemplateGenerationOutcome(
                    relativePath,
                    false,
                    List.of(),
                    List.of(),
                    List.of(relativePath),
                    List.of(),
                    warning == null ? List.of() : List.of(warning));
        }

        private static TemplateGenerationOutcome skipped(String relativePath) {
            return new TemplateGenerationOutcome(
                    relativePath, false, List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }
}
