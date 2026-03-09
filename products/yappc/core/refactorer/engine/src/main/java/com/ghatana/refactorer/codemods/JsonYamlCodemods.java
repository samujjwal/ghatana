package com.ghatana.refactorer.codemods;

import com.ghatana.refactorer.diagnostics.jsonyaml.NodeBridge;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.Logger;

/**
 * Handles JSON and YAML file normalization and schema validation.
 *
 * <p>This class provides methods to normalize JSON/YAML files and validate them against JSON Schema
 * using an embedded Node.js runtime.
 
 * @doc.type class
 * @doc.purpose Handles json yaml codemods operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class JsonYamlCodemods {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private static final String AJV_VALIDATE_SCRIPT = "/bridges/ajv/ajv-validate.js";

    private final PolyfixProjectContext context;
    private final Logger logger;
    private final NodeBridge nodeBridge;
    private final ExecutorService executor;

    public JsonYamlCodemods(PolyfixProjectContext context, NodeBridge nodeBridge) {
        this.context = Objects.requireNonNull(context, "context");
        this.logger = context.log();
        this.nodeBridge = Objects.requireNonNull(nodeBridge, "nodeBridge");
        this.executor = context.exec();
    }

    /**
     * Normalizes and validates JSON/YAML files against their schemas.
     *
     * @param files The files to process
     * @param schemaDir Directory containing JSON Schema files
     * @return List of diagnostics for any issues found
     */
    public Promise<List<UnifiedDiagnostic>> normalizeAndValidate(List<Path> files, Path schemaDir) {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(schemaDir, "schemaDir");

        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();
        if (files.isEmpty()) {
            return Promise.of(diagnostics);
        }

        logger.info("Processing {} JSON/YAML files with schemas from {}", files.size(), schemaDir);

        List<Promise<Void>> promises = new ArrayList<>();

        for (Path file : files) {
            if (!Files.exists(file)) {
                diagnostics.add(
                        UnifiedDiagnostic.builder()
                                .tool("jsonyaml")
                                .message("File not found: " + file)
                                .file(file)
                                .startLine(1)
                                .startColumn(1)
                                .build());
                continue;
            }

            // For each file, find the corresponding schema
            Path schemaFile = findSchemaForFile(file, schemaDir);
            if (schemaFile == null) {
                logger.debug("No schema found for {}, skipping validation", file);
                continue;
            }

            // Process each file asynchronously
            Promise<Void> promise =
                    Promise.ofBlocking(BLOCKING_EXECUTOR, 
                            () -> {
                                try {
                                    validateWithSchema(file, schemaFile, diagnostics);
                                } catch (Exception e) {
                                    String error =
                                            "Failed to validate " + file + ": " + e.getMessage();
                                    logger.error("{}: {}", error, e);
                                    diagnostics.add(
                                            UnifiedDiagnostic.builder()
                                                    .tool("jsonyaml")
                                                    .message(error)
                                                    .file(file)
                                                    .startLine(1)
                                                    .startColumn(1)
                                                    .endLine(1)
                                                    .endColumn(1)
                                                    .build());
                                }
                            });

            promises.add(promise);
        }

        if (promises.isEmpty()) {
            return Promise.of(diagnostics);
        }

        // Wait for all validations to complete
        return Promises.toList(promises)
                .map(ignored -> diagnostics)
                .map(result -> result, e -> {
                    logger.error("Error during JSON/YAML validation: {}", e.getMessage(), e);
                    return diagnostics;
                });
    }

    private Path findSchemaForFile(Path file, Path schemaDir) {
        String baseName = file.getFileName().toString();
        // Try exact match first (e.g., config.json -> config.schema.json)
        Path schemaFile =
                schemaDir.resolve(baseName.replaceAll("\\.(json|ya?ml)$", ".schema.json"));

        if (Files.exists(schemaFile)) {
            return schemaFile;
        }

        // Try with .yaml extension if .json not found
        if (schemaFile.toString().endsWith(".json")) {
            Path yamlSchema = Path.of(schemaFile.toString().replace(".json", ".yaml"));
            if (Files.exists(yamlSchema)) {
                return yamlSchema;
            }
        }

        return null;
    }

    private void validateWithSchema(
            Path file, Path schemaFile, List<UnifiedDiagnostic> diagnostics) {
        try {
            // Use NodeBridge to execute AJV validation
            NodeBridge.Result result =
                    nodeBridge.executeScript(
                            AJV_VALIDATE_SCRIPT, schemaFile.toString(), file.toString());

            if (result.exitCode() != 0) {
                String error = result.stderr().isEmpty() ? "Unknown error" : result.stderr();
                diagnostics.add(
                        UnifiedDiagnostic.builder()
                                .tool("jsonyaml")
                                .message("Schema validation failed: " + error)
                                .file(file)
                                .startLine(1)
                                .startColumn(1)
                                .endLine(1)
                                .endColumn(1)
                                .build());
            } else if (!result.stdout().trim().isEmpty()) {
                // Parse validation errors from stdout
                processValidationOutput(result.stdout(), file, diagnostics);
            }
        } catch (Exception e) {
            logger.error("Error validating {}: {}", file, e.getMessage(), e);
            diagnostics.add(
                    UnifiedDiagnostic.builder()
                            .tool("jsonyaml")
                            .message("Error processing file: " + file + " - " + e.getMessage())
                            .file(file)
                            .startLine(1)
                            .startColumn(1)
                            .endLine(1)
                            .endColumn(1)
                            .build());
        }
    }

    private void processValidationOutput(
            String output, Path file, List<UnifiedDiagnostic> diagnostics) {
        // In a real implementation, parse the JSON output from AJV
        // and convert to UnifiedDiagnostic objects
        // For now, just log the raw output
        logger.debug("Validation output for {}: {}", file, output);

        if (!output.isBlank()) {
            diagnostics.add(
                    UnifiedDiagnostic.builder()
                            .tool("jsonyaml")
                            .message("Schema validation warning: " + output)
                            .file(file)
                            .startLine(1)
                            .startColumn(1)
                            .build());
        }
    }
}
